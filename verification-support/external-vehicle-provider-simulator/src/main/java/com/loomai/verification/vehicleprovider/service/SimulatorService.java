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

    public SimulatorService(SimulatorProperties properties,
                            SimulatorRepository repository,
                            ObjectMapper objectMapper) {
        this.properties = properties;
        this.repository = repository;
        this.objectMapper = objectMapper;
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
            profileAVehicles(),
            profileBVehicles()
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
        String token = issueToken(properties.profileA().accountId(), expiresAt);
        return objectMapper.createObjectNode()
            .put("access_token", token)
            .put("token_type", "Bearer")
            .put("expires_at", expiresAt.toString())
            .put("expires_in", properties.tokenTtlSeconds())
            .put("account_id", properties.profileA().accountId())
            .put("fixture_version", properties.fixtureVersion());
    }

    public String requireProfileAToken(String authorization) {
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
            if (!Profile.PROFILE_A.value().equals(profile)
                || !properties.profileA().accountId().equals(accountId)
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
        return profile == Profile.PROFILE_A
            ? properties.profileA().accountId()
            : properties.profileB().accountId();
    }

    public String webhookSecret(Profile profile) {
        return profile == Profile.PROFILE_A
            ? properties.profileA().webhookSecret()
            : properties.profileB().webhookSecret();
    }

    public String newRequestId() {
        return "sim-" + UUID.randomUUID();
    }

    private String issueToken(String accountId, Instant expiresAt) {
        ObjectNode payload = objectMapper.createObjectNode()
            .put("profile", Profile.PROFILE_A.value())
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
}
