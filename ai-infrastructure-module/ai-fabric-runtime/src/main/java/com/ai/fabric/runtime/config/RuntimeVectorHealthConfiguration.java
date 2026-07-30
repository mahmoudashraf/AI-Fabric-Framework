package com.ai.fabric.runtime.config;

import ai.fabric.service.VectorManagementService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "management.health.ai-fabric.vector", name = "enabled", havingValue = "true")
public class RuntimeVectorHealthConfiguration {

    private static final String VECTOR_COUNT_REASON =
        "Vector provider does not advertise efficient entity-type count.";

    @Bean(name = "vectorProviderHealthIndicator")
    @ConditionalOnBean(VectorManagementService.class)
    @ConditionalOnMissingBean(name = "vectorProviderHealthIndicator")
    public HealthIndicator vectorProviderHealthIndicator(VectorManagementService vectorManagementService) {
        return new RuntimeVectorProviderHealthIndicator(vectorManagementService);
    }

    static final class RuntimeVectorProviderHealthIndicator implements HealthIndicator {

        private final VectorManagementService vectorManagementService;

        RuntimeVectorProviderHealthIndicator(VectorManagementService vectorManagementService) {
            this.vectorManagementService = vectorManagementService;
        }

        @Override
        public Health health() {
            Map<String, Object> diagnostics = vectorManagementService.getProviderDiagnostics();
            Map<String, Object> readiness = readiness(diagnostics);
            Map<String, Object> adjustedReadiness = tolerateScanBackedCountMode(diagnostics, readiness);
            boolean operational = Boolean.TRUE.equals(adjustedReadiness.get("operational"));

            Health.Builder builder = operational ? Health.up() : Health.down();
            builder.withDetail("readinessStatus", adjustedReadiness.getOrDefault("status", "NOT_READY"));
            builder.withDetail("productionReady", Boolean.TRUE.equals(adjustedReadiness.get("productionReady")));
            builder.withDetail("reasons", adjustedReadiness.getOrDefault("reasons", List.of()));
            builder.withDetail("warnings", adjustedReadiness.getOrDefault("warnings", List.of()));
            builder.withDetail("vectorDatabase", diagnostics == null ? Map.of() : diagnostics);
            return builder.build();
        }

        @SuppressWarnings("unchecked")
        private Map<String, Object> readiness(Map<String, Object> diagnostics) {
            Object readiness = diagnostics != null ? diagnostics.get("readiness") : null;
            if (readiness instanceof Map<?, ?> map) {
                Map<String, Object> typed = new LinkedHashMap<>();
                map.forEach((key, value) -> typed.put(String.valueOf(key), value));
                return typed;
            }
            return Map.of(
                "status", "NOT_READY",
                "operational", false,
                "productionReady", false,
                "reasons", List.of("Vector provider readiness verdict is missing."),
                "warnings", List.of()
            );
        }

        private Map<String, Object> tolerateScanBackedCountMode(Map<String, Object> diagnostics,
                                                               Map<String, Object> readiness) {
            if (!isOnlyMissingEfficientCount(diagnostics, readiness)) {
                return readiness;
            }
            Map<String, Object> adjusted = new LinkedHashMap<>(readiness);
            List<String> warnings = new ArrayList<>(strings(readiness.get("warnings")));
            warnings.add("Vector provider uses scan-backed entity-type counts; this is operational but not efficient.");
            adjusted.put("status", "WARN");
            adjusted.put("operational", true);
            adjusted.put("productionReady", false);
            adjusted.put("reasons", List.of());
            adjusted.put("warnings", List.copyOf(warnings));
            return adjusted;
        }

        private boolean isOnlyMissingEfficientCount(Map<String, Object> diagnostics,
                                                   Map<String, Object> readiness) {
            if (diagnostics == null || diagnostics.isEmpty()) {
                return false;
            }
            if (!Boolean.TRUE.equals(asBoolean(diagnostics.get("diagnosticsAvailable"), true))) {
                return false;
            }
            List<String> reasons = strings(readiness.get("reasons"));
            if (reasons.isEmpty() || reasons.stream().anyMatch(reason -> !VECTOR_COUNT_REASON.equals(reason))) {
                return false;
            }
            return capability(diagnostics, "supportsVectorScan")
                && capability(diagnostics, "supportsSearchMetadataFiltering")
                && capability(diagnostics, "supportsScanMetadataFiltering")
                && capability(diagnostics, "supportsExactFetchById")
                && capability(diagnostics, "supportsClearByEntityType")
                && !capability(diagnostics, "supportsEfficientEntityTypeCount")
                && hasText(modeValue(diagnostics, "entityTypeCountMode", "countMode"));
        }

        private boolean capability(Map<String, Object> diagnostics, String key) {
            return Boolean.TRUE.equals(asBoolean(value(diagnostics, key), false));
        }

        private Object modeValue(Map<String, Object> diagnostics, String capabilityKey, String flatKey) {
            Object value = value(diagnostics, capabilityKey);
            return hasText(value) ? value : diagnostics.get(flatKey);
        }

        private Object value(Map<String, Object> diagnostics, String key) {
            Object capabilities = diagnostics.get("capabilities");
            if (capabilities instanceof Map<?, ?> map && map.containsKey(key)) {
                return map.get(key);
            }
            return diagnostics.get(key);
        }

        private Boolean asBoolean(Object value, boolean defaultValue) {
            if (value instanceof Boolean bool) {
                return bool;
            }
            if (value instanceof String text && !text.isBlank()) {
                return Boolean.parseBoolean(text.trim());
            }
            return defaultValue;
        }

        private boolean hasText(Object value) {
            return value != null && !String.valueOf(value).trim().isEmpty();
        }

        private List<String> strings(Object value) {
            if (value instanceof List<?> list) {
                return list.stream()
                    .filter(item -> item != null && !String.valueOf(item).isBlank())
                    .map(String::valueOf)
                    .toList();
            }
            if (value == null || String.valueOf(value).isBlank()) {
                return List.of();
            }
            return List.of(String.valueOf(value));
        }
    }
}
