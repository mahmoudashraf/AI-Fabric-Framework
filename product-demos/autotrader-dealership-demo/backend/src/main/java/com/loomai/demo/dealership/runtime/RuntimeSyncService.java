package com.loomai.demo.dealership.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.loomai.demo.dealership.config.DealershipDemoProperties;
import com.loomai.demo.dealership.inventory.Vehicle;
import com.loomai.demo.dealership.inventory.VehicleRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RuntimeSyncService {

    private final VehicleRepository vehicles;
    private final SyncRepository syncRepository;
    private final RuntimeIngestionClient runtime;
    private final DealershipDemoProperties properties;

    public RuntimeSyncService(VehicleRepository vehicles,
                              SyncRepository syncRepository,
                              RuntimeIngestionClient runtime,
                              DealershipDemoProperties properties) {
        this.vehicles = vehicles;
        this.syncRepository = syncRepository;
        this.runtime = runtime;
        this.properties = properties;
    }

    @Transactional
    public SyncRepository.RunSummary synchronize() {
        List<Vehicle> inventory = vehicles.findAll();
        String runId = UUID.randomUUID().toString();
        String requestId = "dealership-inventory-" + UUID.randomUUID();
        syncRepository.createRun(runId, requestId, inventory.size());
        try {
            JsonNode response = runtime.batch(Map.of(
                "trace", Map.of("requestId", requestId),
                "operations", inventory.stream().map(this::operation).toList()
            ));
            List<JsonNode> results = new ArrayList<>();
            response.path("results").forEach(results::add);
            for (int index = 0; index < inventory.size(); index++) {
                Vehicle vehicle = inventory.get(index);
                JsonNode result = index < results.size() ? results.get(index) : null;
                boolean success = result != null && result.path("success").asBoolean(false);
                String workId = result == null ? null : text(result.path("metadata"), "indexingWorkId");
                String indexingStatus = result == null ? null : text(result.path("metadata"), "indexingStatus");
                String status = success ? normalizeStatus(indexingStatus) : "FAILED";
                syncRepository.addItem(
                    UUID.randomUUID().toString(), runId, vehicle.id(), workId, status,
                    result == null ? "MISSING_OPERATION_RESULT" : text(result, "errorCode"),
                    result == null ? "Runtime did not return an operation result." : text(result, "message")
                );
            }
            int succeeded = response.path("succeededOperations").asInt(0);
            int failed = response.path("failedOperations").asInt(Math.max(0, inventory.size() - succeeded));
            boolean pending = results.stream().anyMatch(result -> {
                String status = text(result.path("metadata"), "indexingStatus");
                return status != null && !List.of("COMPLETED", "FAILED_PERMANENT").contains(status);
            });
            String status = failed == 0 ? (pending ? "INDEXING" : "COMPLETED")
                : succeeded == 0 ? "FAILED" : "PARTIAL";
            syncRepository.finishSubmission(
                runId, status, succeeded, failed, text(response, "providerRequestId"),
                text(response, "errorCode"), text(response, "message")
            );
        } catch (Exception ex) {
            syncRepository.finishSubmission(runId, "FAILED", 0, inventory.size(), null,
                "RUNTIME_SYNC_FAILED", safeMessage(ex));
        }
        return syncRepository.latestRun().orElseThrow();
    }

    public Map<String, Object> status() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("runtimeConfigured", properties.getRuntime().isEnabled());
        response.put("vectorSpace", properties.getRuntime().getVectorSpace());
        response.put("latestRun", syncRepository.latestRun().orElse(null));
        if (properties.getRuntime().isEnabled()) {
            try {
                response.put("runtimeReadiness", runtime.readiness());
            } catch (Exception ex) {
                response.put("runtimeReadiness", Map.of(
                    "success", false,
                    "status", "UNAVAILABLE",
                    "message", safeMessage(ex)
                ));
            }
        }
        return response;
    }

    @Scheduled(fixedDelayString = "${DEALERSHIP_SYNC_RECONCILE_INTERVAL_MS:15000}")
    public void reconcile() {
        if (!properties.getRuntime().isEnabled()) {
            return;
        }
        for (SyncRepository.PendingWork pending : syncRepository.pendingWork(50)) {
            try {
                JsonNode response = runtime.work(pending.workId());
                String status = normalizeStatus(text(response, "status"));
                syncRepository.updateWork(pending.id(), status, text(response, "errorCode"), null);
                syncRepository.refreshRun(pending.runId());
            } catch (Exception ignored) {
                // A transient status lookup must not overwrite the accepted work evidence.
            }
        }
    }

    Map<String, Object> operation(Vehicle vehicle) {
        String content = searchableContent(vehicle);
        Map<String, Object> identity = Map.of(
            "sourceRecordId", vehicle.stockId(),
            "sourceRecordVersion", String.valueOf(vehicle.sourceVersion()),
            "chunkId", "vehicle-profile",
            "chunkCount", 1,
            "contentFingerprint", sha256(content)
        );
        if (!vehicle.isActive()) {
            return Map.of(
                "type", "DELETE",
                "vectorSpace", properties.getRuntime().getVectorSpace(),
                "id", vehicle.id(),
                "identity", identity
            );
        }

        Map<String, Object> entity = new LinkedHashMap<>();
        entity.put("id", vehicle.id());
        entity.put("stockId", vehicle.stockId());
        entity.put("slug", vehicle.slug());
        entity.put("make", vehicle.make());
        entity.put("model", vehicle.model());
        entity.put("derivative", vehicle.derivative());
        entity.put("year", vehicle.registrationYear());
        entity.put("priceGbp", priceGbp(vehicle));
        entity.put("currency", vehicle.currency());
        entity.put("mileage", vehicle.mileage());
        entity.put("fuelType", vehicle.fuelType());
        entity.put("transmission", vehicle.transmission());
        entity.put("bodyType", vehicle.bodyType());
        entity.put("colour", vehicle.exteriorColour());
        entity.put("location", vehicle.location());
        entity.put("availability", vehicle.lifecycleState());
        entity.put("features", vehicle.features());
        entity.put("content", content);
        if (vehicle.electricRangeMiles() != null) {
            entity.put("electricRangeMiles", vehicle.electricRangeMiles());
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        DealershipDemoProperties.PrivateAccess access = properties.getRuntime().getPrivateAccess();
        metadata.put("tenantId", requiredScopeValue(access.getTenantId(), "LOOMAI_RUNTIME_TENANT_ID"));
        metadata.put("deploymentId", requiredScopeValue(access.getDeploymentId(), "LOOMAI_RUNTIME_DEPLOYMENT_ID"));
        metadata.put("dealershipId", properties.getId());
        metadata.put("sourceClass", "DEMONSTRATION_INVENTORY");
        metadata.put("sourceLabel", vehicle.sourceLabel());
        metadata.put("sourceUpdatedAt", vehicle.sourceUpdatedAt().toString());
        metadata.put("stockId", vehicle.stockId());
        metadata.put("make", vehicle.make());
        metadata.put("fuelType", vehicle.fuelType());
        metadata.put("bodyType", vehicle.bodyType());
        metadata.put("lifecycleState", vehicle.lifecycleState());

        return Map.of(
            "type", "UPSERT",
            "vectorSpace", properties.getRuntime().getVectorSpace(),
            "id", vehicle.id(),
            "content", content,
            "entity", entity,
            "metadata", metadata,
            "identity", identity
        );
    }

    private String requiredScopeValue(String value, String variable) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(variable + " is required for runtime inventory synchronization.");
        }
        return value.trim();
    }

    private String searchableContent(Vehicle vehicle) {
        String range = vehicle.electricRangeMiles() == null ? "" : " Electric range " + vehicle.electricRangeMiles() + " miles.";
        return vehicle.displayName() + " " + vehicle.derivative() + ". " + vehicle.summary()
            + " Price: GBP " + priceGbp(vehicle).toPlainString() + "."
            + " Fuel: " + vehicle.fuelType() + ". Transmission: " + vehicle.transmission()
            + ". Body: " + vehicle.bodyType() + ". Features: " + String.join(", ", vehicle.features()) + "." + range;
    }

    private BigDecimal priceGbp(Vehicle vehicle) {
        return BigDecimal.valueOf(vehicle.priceMinor(), 2);
    }

    private String normalizeStatus(String value) {
        if (!StringUtils.hasText(value)) {
            return "QUEUED";
        }
        return switch (value.trim().toUpperCase()) {
            case "COMPLETED", "SUCCEEDED" -> "COMPLETED";
            case "FAILED", "FAILED_PERMANENT" -> "FAILED";
            case "DEAD_LETTER" -> "DEAD_LETTER";
            case "FAILED_RETRYABLE", "RETRYING" -> "RETRYING";
            case "PROCESSING", "IN_PROGRESS" -> "PROCESSING";
            default -> "QUEUED";
        };
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.path(field);
        return value == null || value.isMissingNode() || value.isNull() || !StringUtils.hasText(value.asText())
            ? null : value.asText().trim();
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not fingerprint inventory content.", ex);
        }
    }

    private String safeMessage(Exception ex) {
        String message = ex.getMessage();
        return StringUtils.hasText(message) ? message.substring(0, Math.min(message.length(), 300)) : ex.getClass().getSimpleName();
    }
}
