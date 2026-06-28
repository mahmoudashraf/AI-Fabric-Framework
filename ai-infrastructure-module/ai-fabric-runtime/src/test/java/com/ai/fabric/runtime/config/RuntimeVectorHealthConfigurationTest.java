package com.ai.fabric.runtime.config;

import ai.fabric.service.VectorManagementService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static java.util.Map.entry;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RuntimeVectorHealthConfigurationTest {

    @Test
    void reportsUpWithWarningForMilvusScanBackedEntityTypeCount() {
        VectorManagementService vectorManagementService = mock(VectorManagementService.class);
        when(vectorManagementService.getProviderDiagnostics()).thenReturn(Map.ofEntries(
            entry("diagnosticsAvailable", true),
            entry("provider", "milvus"),
            entry("supportsVectorScan", true),
            entry("supportsSearchMetadataFiltering", true),
            entry("supportsScanMetadataFiltering", true),
            entry("supportsExactFetchById", true),
            entry("supportsClearByEntityType", true),
            entry("supportsEfficientEntityTypeCount", false),
            entry("countMode", "milvus-visible-row-scan"),
            entry("clearMode", "milvus-drop-collection"),
            entry("readiness", Map.of(
                "status", "NOT_READY",
                "operational", false,
                "productionReady", false,
                "reasons", List.of("Vector provider does not advertise efficient entity-type count."),
                "warnings", List.of()
            ))
        ));

        Health health = new RuntimeVectorHealthConfiguration.RuntimeVectorProviderHealthIndicator(vectorManagementService)
            .health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails())
            .containsEntry("readinessStatus", "WARN")
            .containsEntry("productionReady", false)
            .containsEntry("reasons", List.of());
        assertThat((List<String>) health.getDetails().get("warnings"))
            .contains("Vector provider uses scan-backed entity-type counts; this is operational but not efficient.");
    }

    @Test
    void stillReportsDownWhenRequiredFilteringCapabilityIsMissing() {
        VectorManagementService vectorManagementService = mock(VectorManagementService.class);
        when(vectorManagementService.getProviderDiagnostics()).thenReturn(Map.ofEntries(
            entry("diagnosticsAvailable", true),
            entry("provider", "custom"),
            entry("supportsVectorScan", true),
            entry("supportsSearchMetadataFiltering", true),
            entry("supportsScanMetadataFiltering", false),
            entry("supportsExactFetchById", true),
            entry("supportsClearByEntityType", true),
            entry("supportsEfficientEntityTypeCount", false),
            entry("countMode", "scan-count"),
            entry("readiness", Map.of(
                "status", "NOT_READY",
                "operational", false,
                "productionReady", false,
                "reasons", List.of(
                    "Vector provider does not advertise metadata-filtered vector scan.",
                    "Vector provider does not advertise efficient entity-type count."
                ),
                "warnings", List.of()
            ))
        ));

        Health health = new RuntimeVectorHealthConfiguration.RuntimeVectorProviderHealthIndicator(vectorManagementService)
            .health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails())
            .containsEntry("readinessStatus", "NOT_READY");
        assertThat((List<String>) health.getDetails().get("reasons"))
            .contains("Vector provider does not advertise metadata-filtered vector scan.");
    }
}
