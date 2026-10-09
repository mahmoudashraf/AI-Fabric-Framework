package com.loomai.demo.dealership.runtime;

import com.loomai.demo.dealership.config.DealershipDemoProperties;
import com.loomai.demo.dealership.inventory.VehicleRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class RuntimeIntegrationController {

    private final DealershipDemoProperties properties;
    private final VehicleRepository vehicles;
    private final RuntimeConnectorOperationsClient connectorOperations;
    private final String appVersion;
    private final String buildCommit;
    private final String buildTime;

    public RuntimeIntegrationController(DealershipDemoProperties properties,
                                        VehicleRepository vehicles,
                                        RuntimeConnectorOperationsClient connectorOperations,
                                        @Value("${info.app.version:unknown}") String appVersion,
                                        @Value("${info.app.commit:unknown}") String buildCommit,
                                        @Value("${info.app.build-time:unknown}") String buildTime) {
        this.properties = properties;
        this.vehicles = vehicles;
        this.connectorOperations = connectorOperations;
        this.appVersion = appVersion;
        this.buildCommit = buildCommit;
        this.buildTime = buildTime;
    }

    @GetMapping("/api/public/status")
    public Map<String, Object> publicStatus() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("status", "UP");
        response.put("service", "loomai-dealership-demo-backend");
        response.put("version", appVersion);
        response.put("commit", buildCommit);
        response.put("buildTime", buildTime);
        response.put("dealershipId", properties.getId());
        response.put("inventoryCount", vehicles.count());
        response.put("inventoryRefreshedAt", vehicles.latestSourceUpdate().orElse(Instant.EPOCH));
        response.put("runtimeConfigured", properties.getRuntime().isEnabled());
        response.put("sourceMode", StringUtils.hasText(properties.getRuntime().getIntegrationSourceId())
            ? "PROVIDER_MANAGED_DEPLOYMENT_INDEX"
            : "NOT_CONFIGURED");
        return response;
    }

    @GetMapping("/api/staff/integration/status")
    public Map<String, Object> staffStatus() {
        Map<String, Object> integration = new LinkedHashMap<>();
        integration.put("runtimeConfigured", properties.getRuntime().isEnabled());
        integration.put("inventoryVectorSpace", properties.getRuntime().getInventoryVectorSpace());
        integration.put("retrievalVectorSpaces", properties.getRuntime().getRetrievalVectorSpaces());
        integration.put("sourceId", properties.getRuntime().getIntegrationSourceId());
        try {
            JsonNode source = connectorOperations.sourceStatus();
            integration.put("available", true);
            integration.put("source", source);
        } catch (Exception ex) {
            integration.put("available", false);
            integration.put("errorCode", "CONNECTOR_SOURCE_UNAVAILABLE");
            integration.put("message", safeMessage(ex));
        }
        return Map.of("success", true, "integration", integration);
    }

    @PostMapping("/api/staff/integration/reconcile")
    public Map<String, Object> reconcile() {
        return Map.of("success", true, "source", connectorOperations.reconcileSource());
    }

    private String safeMessage(Exception ex) {
        String message = ex.getMessage();
        if (!StringUtils.hasText(message)) {
            return ex.getClass().getSimpleName();
        }
        return message.substring(0, Math.min(message.length(), 300));
    }
}
