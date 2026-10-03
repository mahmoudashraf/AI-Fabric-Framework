package com.loomai.verification.vehicleprovider.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.loomai.verification.vehicleprovider.config.SimulatorProperties;
import com.loomai.verification.vehicleprovider.model.SimulatorContracts.AccountState;
import com.loomai.verification.vehicleprovider.model.SimulatorContracts.ActiveFault;
import com.loomai.verification.vehicleprovider.model.SimulatorContracts.ControlStatus;
import com.loomai.verification.vehicleprovider.model.SimulatorContracts.FaultMode;
import com.loomai.verification.vehicleprovider.model.SimulatorContracts.FixtureRun;
import com.loomai.verification.vehicleprovider.model.SimulatorContracts.MutationReceipt;
import com.loomai.verification.vehicleprovider.model.SimulatorContracts.Profile;
import com.loomai.verification.vehicleprovider.model.SimulatorContracts.VehicleInput;
import com.loomai.verification.vehicleprovider.model.SimulatorContracts.VehicleRecord;
import com.loomai.verification.vehicleprovider.persistence.SimulatorRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class SimulatorService {

    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();

    private final SimulatorProperties properties;
    private final SimulatorRepository repository;
    private final ObjectMapper objectMapper;
    private final String publicBaseUrl;

    public SimulatorService(SimulatorProperties properties,
                            SimulatorRepository repository,
                            ObjectMapper objectMapper) {
        this.properties = properties;
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.publicBaseUrl = validatePublicBaseUrl(properties.publicBaseUrl());
    }

    @PostConstruct
    void initializeFixture() {
        Optional<FixtureRun> existing = repository.currentRun();
        if (existing.isEmpty() || !properties.fixtureVersion().equals(existing.get().fixtureVersion())) {
            reset();
        }
    }

    public FixtureRun reset() {
        return repository.reset(
            properties.fixtureVersion(),
            properties.profileA().accountId(),
            properties.profileB().accountId(),
            properties.autoTrader().advertiserId(),
            profileAVehicles(),
            profileBVehicles(),
            autoTraderVehicles()
        );
    }

    public ControlStatus status() {
        FixtureRun run = repository.currentRun()
            .orElseThrow(() -> new IllegalStateException("Simulator fixture is not initialized."));
        return new ControlStatus(
            "external-vehicle-provider-simulator",
            "hosted-generic-substrate-verification",
            run,
            repository.accountStates()
        );
    }

    public ObjectNode authenticateProfileA(String key, String secret) {
        if (!constantTime(properties.profileA().key(), key)
            || !constantTime(properties.profileA().secret(), secret)) {
            throw new ProviderApiException(401, "INVALID_CREDENTIALS", "Provider credentials were rejected.");
        }
        Instant expiresAt = Instant.now().plusSeconds(properties.tokenTtlSeconds());
        String token = issueToken(Profile.PROFILE_A, properties.profileA().accountId(), expiresAt);
        return objectMapper.createObjectNode()
            .put("access_token", token)
            .put("token_type", "Bearer")
            .put("expires_at", expiresAt.toString())
            .put("expires_in", properties.tokenTtlSeconds())
            .put("account_id", properties.profileA().accountId())
            .put("fixture_version", properties.fixtureVersion());
    }

    public ObjectNode authenticateAutoTrader(String key, String secret) {
        if (!constantTime(properties.autoTrader().apiKey(), key)
            || !constantTime(properties.autoTrader().apiSecret(), secret)) {
            throw new ProviderApiException(401, "INVALID_CREDENTIALS", "Provider credentials were rejected.");
        }
        Instant expiresAt = Instant.now().plusSeconds(properties.autoTrader().tokenTtlSeconds());
        String token = issueToken(Profile.AUTOTRADER, properties.autoTrader().advertiserId(), expiresAt);
        return objectMapper.createObjectNode()
            .put("access_token", token)
            .put("expires_at", expiresAt.toString());
    }

    public String requireProfileAToken(String authorization) {
        return requireToken(Profile.PROFILE_A, properties.profileA().accountId(), authorization);
    }

    public String requireAutoTraderToken(String authorization) {
        return requireToken(Profile.AUTOTRADER, properties.autoTrader().advertiserId(), authorization);
    }

    private String requireToken(Profile expectedProfile, String expectedAccountId, String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ProviderApiException(401, "AUTHENTICATION_REQUIRED", "A bearer token is required.");
        }
        String token = authorization.substring("Bearer ".length()).trim();
        String[] parts = token.split("\\.", -1);
        if (parts.length != 2 || !constantTime(sign(parts[0]), parts[1])) {
            throw new ProviderApiException(401, "TOKEN_INVALID", "The bearer token is invalid.");
        }
        try {
            JsonNode payload = objectMapper.readTree(BASE64_URL_DECODER.decode(parts[0]));
            String profile = payload.path("profile").asText();
            String accountId = payload.path("accountId").asText();
            long expiresAt = payload.path("expiresAt").asLong(0);
            if (!expectedProfile.value().equals(profile)
                || !expectedAccountId.equals(accountId)
                || expiresAt <= Instant.now().getEpochSecond()) {
                throw new ProviderApiException(401, "TOKEN_INVALID", "The bearer token is invalid or expired.");
            }
            return accountId;
        } catch (ProviderApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ProviderApiException(401, "TOKEN_INVALID", "The bearer token is invalid.");
        }
    }

    public String requireProfileBApiKey(String apiKey) {
        if (!constantTime(properties.profileB().apiKey(), apiKey)) {
            throw new ProviderApiException(401, "API_KEY_INVALID", "The API key is invalid.");
        }
        return properties.profileB().accountId();
    }

    public void requireAccount(Profile profile, String authenticatedAccount, String requestedAccount) {
        String configured = accountId(profile);
        if (!configured.equals(authenticatedAccount) || !configured.equals(requestedAccount)) {
            throw new ProviderApiException(403, "RESOURCE_ACCESS_DENIED", "The credential cannot access this account.");
        }
    }

    public List<VehicleRecord> vehicles(Profile profile, String accountId) {
        requireKnownAccount(profile, accountId);
        return repository.vehicles(profile, accountId);
    }

    public VehicleRecord vehicle(Profile profile, String accountId, String vehicleId) {
        requireKnownAccount(profile, accountId);
        return repository.vehicle(profile, accountId, vehicleId)
            .orElseThrow(() -> new ProviderApiException(404, "VEHICLE_NOT_FOUND", "The vehicle was not found."));
    }

    public Optional<VehicleRecord> findVehicle(Profile profile, String accountId, String vehicleId) {
        requireKnownAccount(profile, accountId);
        return repository.vehicle(profile, accountId, vehicleId);
    }

    public long sourceVersion(Profile profile, String accountId) {
        requireKnownAccount(profile, accountId);
        return repository.sourceVersion(profile, accountId);
    }

    public MutationReceipt upsert(Profile profile, String accountId, VehicleInput input) {
        requireKnownAccount(profile, accountId);
        long version = repository.upsertVehicle(profile, accountId, input);
        return new MutationReceipt(profile.value(), accountId, input.id(), "UPSERT", version);
    }

    public MutationReceipt delete(Profile profile, String accountId, String vehicleId, boolean purge) {
        requireKnownAccount(profile, accountId);
        long version = repository.deleteVehicle(profile, accountId, vehicleId, purge);
        return new MutationReceipt(profile.value(), accountId, vehicleId, purge ? "PURGE" : "TOMBSTONE", version);
    }

    public void configureFault(Profile profile, String accountId, FaultMode mode, int remaining, int delayMs) {
        requireKnownAccount(profile, accountId);
        repository.setFault(profile, accountId, mode, remaining, delayMs);
    }

    public Optional<ActiveFault> consumeFault(Profile profile, String accountId) {
        requireKnownAccount(profile, accountId);
        return repository.consumeFault(profile, accountId);
    }

    public long nextEventSequence(Profile profile, String accountId) {
        requireKnownAccount(profile, accountId);
        return repository.nextEventSequence(profile, accountId);
    }

    public String accountId(Profile profile) {
        return switch (profile) {
            case PROFILE_A -> properties.profileA().accountId();
            case PROFILE_B -> properties.profileB().accountId();
            case AUTOTRADER -> properties.autoTrader().advertiserId();
        };
    }

    public String webhookSecret(Profile profile) {
        return switch (profile) {
            case PROFILE_A -> properties.profileA().webhookSecret();
            case PROFILE_B -> properties.profileB().webhookSecret();
            case AUTOTRADER -> properties.autoTrader().notificationSecret();
        };
    }

    public String autoTraderIntegrationId() {
        return properties.autoTrader().integrationId();
    }

    public String publicBaseUrl() {
        return publicBaseUrl;
    }

    public String newRequestId() {
        return "sim-" + UUID.randomUUID();
    }

    private String issueToken(Profile profile, String accountId, Instant expiresAt) {
        ObjectNode payload = objectMapper.createObjectNode()
            .put("profile", profile.value())
            .put("accountId", accountId)
            .put("expiresAt", expiresAt.getEpochSecond())
            .put("nonce", UUID.randomUUID().toString());
        try {
            String encoded = BASE64_URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(payload));
            return encoded + "." + sign(encoded);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to issue simulator token.", ex);
        }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                properties.tokenSigningSecret().getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
            ));
            return BASE64_URL_ENCODER.encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to sign simulator token.", ex);
        }
    }

    private void requireKnownAccount(Profile profile, String accountId) {
        if (!accountId(profile).equals(accountId)) {
            throw new IllegalArgumentException("Unknown simulator account.");
        }
    }

    private static boolean constantTime(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8),
            actual.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String validatePublicBaseUrl(String configured) {
        URI uri;
        try {
            uri = URI.create(configured.trim());
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("simulator.public-base-url must be a valid absolute URL.", ex);
        }
        boolean loopbackHttp = "http".equalsIgnoreCase(uri.getScheme())
            && ("localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost()));
        boolean validPath = uri.getPath() == null || uri.getPath().isBlank() || "/".equals(uri.getPath());
        if (!("https".equalsIgnoreCase(uri.getScheme()) || loopbackHttp)
            || uri.getHost() == null
            || uri.getUserInfo() != null
            || uri.getQuery() != null
            || uri.getFragment() != null
            || !validPath) {
            throw new IllegalArgumentException(
                "simulator.public-base-url must be an HTTPS origin; loopback HTTP is allowed only for local use."
            );
        }
        return configured.trim().replaceAll("/+$", "");
    }

    private static List<VehicleInput> profileAVehicles() {
        return List.of(
            new VehicleInput("veh-a-1001", "Northstar", "Atlas", "Long Range", 2025, 3899500, "GBP", "electric", "suv", "automatic", 4100, "active"),
            new VehicleInput("veh-a-1002", "Northstar", "Comet", "Urban", 2024, 2475000, "GBP", "electric", "hatchback", "automatic", 9200, "active"),
            new VehicleInput("veh-a-1003", "Caldera", "Touring", "Estate Plus", 2023, 2199000, "GBP", "hybrid", "estate", "automatic", 18400, "reserved"),
            new VehicleInput("veh-a-1004", "Arden", "Trail", "AWD", 2022, 1945000, "GBP", "petrol", "suv", "automatic", 26700, "active")
        );
    }

    private static List<VehicleInput> profileBVehicles() {
        return List.of(
            new VehicleInput("veh-b-2001", "Aster", "Pulse", "Performance", 2025, 4210000, "GBP", "electric", "saloon", "automatic", 2300, "active"),
            new VehicleInput("veh-b-2002", "Aster", "Pulse", "Touring", 2024, 3650000, "GBP", "electric", "estate", "automatic", 7100, "active"),
            new VehicleInput("veh-b-2003", "Morrow", "City", "Comfort", 2023, 1695000, "GBP", "hybrid", "hatchback", "automatic", 14100, "active"),
            new VehicleInput("veh-b-2004", "Morrow", "Venture", "Seven Seat", 2022, 2325000, "GBP", "diesel", "suv", "automatic", 29800, "sold")
        );
    }

    private static List<VehicleInput> autoTraderVehicles() {
        return List.of(
            new VehicleInput("DEMO-1001", "Aster", "E1", "Motion Long Range", 2025, 31_950_00L, "GBP", "Electric", "SUV", "Automatic", 4850, "active"),
            new VehicleInput("DEMO-1002", "Northstar", "S4", "Touring Hybrid", 2024, 27_400_00L, "GBP", "Hybrid", "Estate", "Automatic", 8920, "active"),
            new VehicleInput("DEMO-1003", "Morrow", "C2", "City Electric", 2025, 22_750_00L, "GBP", "Electric", "Hatchback", "Automatic", 1980, "active"),
            new VehicleInput("DEMO-1004", "Caldera", "X6", "Adventure AWD", 2023, 29_800_00L, "GBP", "Petrol", "SUV", "Automatic", 17420, "active"),
            new VehicleInput("DEMO-1005", "Arden", "V3", "Executive Plug-in Hybrid", 2024, 34_600_00L, "GBP", "Plug-in hybrid", "Saloon", "Automatic", 6310, "reserved"),
            new VehicleInput("DEMO-1006", "Aster", "E2", "Sport Dual Motor", 2025, 39_250_00L, "GBP", "Electric", "Crossover", "Automatic", 3760, "active")
        );
    }
}
