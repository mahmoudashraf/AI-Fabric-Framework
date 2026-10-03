package com.loomai.demo.dealership.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.loomai.demo.dealership.config.DealershipDemoProperties;
import com.loomai.demo.dealership.runtime.RuntimeConnectorOperationsClient;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ProviderSimulatorService {

    static final String CONTROL_HEADER = "X-Simulator-Control-Key";
    private static final String PROFILE = "autotrader";

    private final DealershipDemoProperties.ProviderSimulator properties;
    private final RestClient restClient;
    private final RuntimeConnectorOperationsClient connectorOperations;

    public ProviderSimulatorService(DealershipDemoProperties configuration,
                                    RestClient.Builder restClientBuilder,
                                    RuntimeConnectorOperationsClient connectorOperations) {
        this.properties = configuration.getProviderSimulator();
        this.connectorOperations = connectorOperations;
        this.restClient = StringUtils.hasText(properties.getBaseUrl())
            ? restClientBuilder.baseUrl(properties.getBaseUrl().trim().replaceAll("/+$", "")).build()
            : restClientBuilder.build();
    }

    public SimulatorStatus status() {
        if (!properties.isEnabled()) {
            return new SimulatorStatus(false, "AUTOTRADER_PUBLIC_CONTRACT", List.of(), null);
        }
        JsonNode upstream = requestStatus();
        return new SimulatorStatus(
            true,
            "AUTOTRADER_PUBLIC_CONTRACT",
            Scenario.codes(),
            upstream.path("fixture").path("resetAt").asText(null)
        );
    }

    public ScenarioReceipt run(String requestedScenario) {
        requireEnabled();
        Scenario scenario = Scenario.parse(requestedScenario);
        JsonNode mutation = scenario.delete
            ? delete(scenario.stockId)
            : upsert(scenario.vehicle);
        JsonNode notification = emitNotification(scenario.stockId);
        String eventId = notification.path("eventId").asText("");
        RuntimeConnectorOperationsClient.WebhookEventEvidence evidence;
        try {
            evidence = connectorOperations.awaitWebhookEvent(eventId, Duration.ofSeconds(10));
        } catch (Exception ex) {
            evidence = new RuntimeConnectorOperationsClient.WebhookEventEvidence(
                "EVIDENCE_UNAVAILABLE",
                0,
                "CONNECTOR_EVIDENCE_UNAVAILABLE"
            );
        }
        return new ScenarioReceipt(
            scenario.code,
            scenario.stockId,
            mutation.path("operation").asText("UNKNOWN"),
            eventId,
            notification.path("sequence").asLong(0),
            lastDeliveryStatus(notification),
            evidence.status(),
            evidence.attemptCount(),
            evidence.errorClass()
        );
    }

    private JsonNode requestStatus() {
        try {
            return restClient.get()
                .uri("/internal/control/status")
                .header(CONTROL_HEADER, properties.getControlApiKey())
                .retrieve()
                .body(JsonNode.class);
        } catch (RestClientException ex) {
            throw new IllegalStateException("The provider simulator status could not be loaded.", ex);
        }
    }

    private JsonNode upsert(VehicleInput vehicle) {
        try {
            return restClient.put()
                .uri(controlVehiclePath(vehicle.id))
                .header(CONTROL_HEADER, properties.getControlApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(vehicle)
                .retrieve()
                .body(JsonNode.class);
        } catch (RestClientException ex) {
            throw new IllegalStateException("The provider simulator stock mutation failed.", ex);
        }
    }

    private JsonNode delete(String stockId) {
        try {
            return restClient.delete()
                .uri(controlVehiclePath(stockId) + "?purge=false")
                .header(CONTROL_HEADER, properties.getControlApiKey())
                .retrieve()
                .body(JsonNode.class);
        } catch (RestClientException ex) {
            throw new IllegalStateException("The provider simulator stock deletion failed.", ex);
        }
    }

    private JsonNode emitNotification(String stockId) {
        Map<String, Object> body = Map.of(
            "variant", "VALID",
            "targetUrl", properties.getWebhookTargetUrl(),
            "vehicleId", stockId
        );
        try {
            return restClient.post()
                .uri("/internal/control/accounts/{profile}/{accountId}/events", PROFILE, properties.getAdvertiserId())
                .header(CONTROL_HEADER, properties.getControlApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        } catch (RestClientException ex) {
            throw new IllegalStateException("The contract-compatible stock notification could not be delivered.", ex);
        }
    }

    private String controlVehiclePath(String stockId) {
        return "/internal/control/accounts/" + PROFILE + "/" + encoded(properties.getAdvertiserId())
            + "/vehicles/" + encoded(stockId);
    }

    private String encoded(String value) {
        return UriUtils.encodePathSegment(value, StandardCharsets.UTF_8);
    }

    private int lastDeliveryStatus(JsonNode notification) {
        JsonNode attempts = notification.path("attempts");
        return attempts.isArray() && !attempts.isEmpty()
            ? attempts.get(attempts.size() - 1).path("status").asInt(0)
            : 0;
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("The provider simulator controls are disabled.");
        }
    }

    enum Scenario {
        ADD_STOCK("add-stock", "DEMO-1099", false,
            new VehicleInput("DEMO-1099", "Meridian", "E7", "Family Long Range", 2025, 36_450_00L,
                "GBP", "Electric", "SUV", "Automatic", 850, "active")),
        UPDATE_PRICE("update-price", "DEMO-1001", false,
            new VehicleInput("DEMO-1001", "Aster", "E1", "Motion Long Range", 2025, 30_950_00L,
                "GBP", "Electric", "SUV", "Automatic", 4850, "active")),
        MARK_SOLD("mark-sold", "DEMO-1004", false,
            new VehicleInput("DEMO-1004", "Caldera", "X6", "Adventure AWD", 2023, 29_800_00L,
                "GBP", "Petrol", "SUV", "Automatic", 17420, "sold")),
        DELETE_STOCK("delete-stock", "DEMO-1006", true, null);

        private final String code;
        private final String stockId;
        private final boolean delete;
        private final VehicleInput vehicle;

        Scenario(String code, String stockId, boolean delete, VehicleInput vehicle) {
            this.code = code;
            this.stockId = stockId;
            this.delete = delete;
            this.vehicle = vehicle;
        }

        static Scenario parse(String value) {
            String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            for (Scenario scenario : values()) {
                if (scenario.code.equals(normalized)) {
                    return scenario;
                }
            }
            throw new IllegalArgumentException("Unknown provider simulator scenario.");
        }

        static List<String> codes() {
            return java.util.Arrays.stream(values()).map(item -> item.code).toList();
        }
    }

    record VehicleInput(String id, String make, String model, String derivative, int year,
                        long priceMinor, String currency, String fuelType, String bodyStyle,
                        String transmission, int mileage, String state) {
    }

    public record SimulatorStatus(boolean enabled, String providerContract,
                                  List<String> availableScenarios, String fixtureResetAt) {
    }

    public record ScenarioReceipt(String scenario, String stockId, String mutationOperation,
                                  String eventId, long eventSequence, int deliveryStatus,
                                  String reconciliationStatus, int reconciliationAttempts,
                                  String reconciliationErrorClass) {
    }
}
