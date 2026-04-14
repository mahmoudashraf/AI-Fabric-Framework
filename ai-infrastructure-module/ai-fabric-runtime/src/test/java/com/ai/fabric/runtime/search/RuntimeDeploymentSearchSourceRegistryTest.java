package com.ai.fabric.runtime.search;

import com.ai.fabric.runtime.config.RuntimeDeploymentKnowledgeSourceConfigService;
import com.ai.infrastructure.core.AISearchService;
import com.ai.infrastructure.dto.AIAccessSubjectContext;
import com.ai.infrastructure.dto.RAGRequest;
import com.ai.infrastructure.rag.VectorDatabaseService;
import com.ai.infrastructure.rag.source.ResolvedKnowledgeSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeDeploymentSearchSourceRegistryTest {

    @Mock
    private RuntimeDeploymentKnowledgeSourceConfigService knowledgeSourceConfigService;
    @Mock
    private AISearchService searchService;
    @Mock
    private VectorDatabaseService vectorDatabaseService;

    @Test
    void registryLoadsDefaultPrivateAndEligibleSharedSources() {
        when(knowledgeSourceConfigService.currentSources()).thenReturn(List.of(
            ResolvedKnowledgeSource.builder()
                .id("shared-catalog")
                .type("shared-vector")
                .adapterType("shared-index")
                .attributionLabel("Shared catalog")
                .entityType("product")
                .handleRef("marketplace/catalog")
                .authModes(List.of("PUBLIC_RUNTIME_AUTHENTICATED"))
                .enabled(true)
                .build()
        ));
        when(vectorDatabaseService.adminDiagnostics()).thenReturn(Map.of("sharedStorage", true));

        RuntimeDeploymentSearchSourceRegistry registry = new RuntimeDeploymentSearchSourceRegistry(
            knowledgeSourceConfigService,
            searchService,
            vectorDatabaseService
        );
        registry.validateAndLoad();

        assertThat(registry.supportedAdapterTypes()).containsExactly("deployment-private-vector", "shared-index");
        assertThat(registry.resolveSearchSources(RAGRequest.builder()
                .query("tell me about Alienware")
                .entityType("product")
                .authContext(AIAccessSubjectContext.builder().authMode("PUBLIC_RUNTIME_AUTHENTICATED").build())
                .build()))
            .extracting(source -> source.source().getId())
            .containsExactly("deployment-private-vector", "shared-catalog");
    }

    @Test
    void registryReturnsConfiguredSharedSourceEvenWhenCurrentRequestIsNotEligible() {
        when(knowledgeSourceConfigService.currentSources()).thenReturn(List.of(
            ResolvedKnowledgeSource.builder()
                .id("shared-catalog")
                .type("shared-vector")
                .adapterType("shared-index")
                .attributionLabel("Shared catalog")
                .entityType("product")
                .handleRef("marketplace/catalog")
                .authModes(List.of("PUBLIC_RUNTIME_AUTHENTICATED"))
                .enabled(true)
                .build()
        ));
        when(vectorDatabaseService.adminDiagnostics()).thenReturn(Map.of("sharedStorage", true));

        RuntimeDeploymentSearchSourceRegistry registry = new RuntimeDeploymentSearchSourceRegistry(
            knowledgeSourceConfigService,
            searchService,
            vectorDatabaseService
        );
        registry.validateAndLoad();

        assertThat(registry.resolveSearchSources(RAGRequest.builder()
                .query("tell me about Alienware")
                .entityType("product")
                .authContext(AIAccessSubjectContext.builder().authMode("PUBLIC_RUNTIME_ANONYMOUS").build())
                .build()))
            .extracting(source -> source.source().getId())
            .containsExactly("deployment-private-vector", "shared-catalog");
    }

    @Test
    void registryTracksSearchHealthDiagnostics() {
        when(knowledgeSourceConfigService.currentSources()).thenReturn(List.of(
            ResolvedKnowledgeSource.builder()
                .id("shared-catalog")
                .type("shared-vector")
                .adapterType("shared-index")
                .attributionLabel("Shared catalog")
                .entityType("product")
                .handleRef("marketplace/catalog")
                .authModes(List.of("PUBLIC_RUNTIME_AUTHENTICATED"))
                .enabled(true)
                .build()
        ));
        when(vectorDatabaseService.adminDiagnostics()).thenReturn(Map.of("sharedStorage", true));

        RuntimeDeploymentSearchSourceRegistry registry = new RuntimeDeploymentSearchSourceRegistry(
            knowledgeSourceConfigService,
            searchService,
            vectorDatabaseService
        );
        registry.validateAndLoad();
        registry.recordSearchExecution(List.of(
            Map.of(
                "sourceId", "deployment-private-vector",
                "sourceType", "deployment-private-vector",
                "adapterType", "deployment-private-vector",
                "status", "SUCCEEDED",
                "processingTimeMs", 18L
            ),
            Map.of(
                "sourceId", "shared-catalog",
                "sourceType", "shared-vector",
                "adapterType", "shared-index",
                "status", "FAILED",
                "reason", "search_error",
                "errorMessage", "catalog unavailable",
                "processingTimeMs", 42L
            )
        ), true);

        Map<String, Object> diagnostics = registry.adminDiagnostics();

        assertThat(diagnostics).containsEntry("contractVersion", "SEARCH_SOURCE_DIAGNOSTICS_V1");
        assertThat(diagnostics).containsEntry("degradedSearchSupported", true);
        assertThat(diagnostics).containsEntry("configuredSourcesCount", 2);
        assertThat(diagnostics).containsEntry("recordedSearchExecutions", 1L);
        assertThat(diagnostics).containsEntry("degradedSearchExecutions", 1L);
        assertThat(diagnostics).containsEntry("degradedSourcesCount", 1L);
        assertThat(diagnostics).containsEntry("disabledSourcesCount", 0L);
        assertThat(diagnostics.get("lastRecordedSearchAt")).isInstanceOf(String.class);
        assertThat(diagnostics.get("sources")).isInstanceOf(List.class);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sources = (List<Map<String, Object>>) diagnostics.get("sources");
        assertThat(sources)
            .anySatisfy(entry -> assertThat(entry)
                .containsEntry("sourceId", "deployment-private-vector")
                .containsEntry("healthStatus", "READY")
                .containsEntry("successCount", 1L)
                .containsEntry("failureCount", 0L))
            .anySatisfy(entry -> assertThat(entry)
                .containsEntry("sourceId", "shared-catalog")
                .containsEntry("healthStatus", "DEGRADED")
                .containsEntry("failureCount", 1L)
                .containsEntry("lastReason", "search_error")
                .containsEntry("lastFailureMessage", "catalog unavailable"));
    }

    @Test
    void registryFailsClosedForSharedIndexWithoutHandleRef() {
        when(knowledgeSourceConfigService.currentSources()).thenReturn(List.of(
            ResolvedKnowledgeSource.builder()
                .id("shared-catalog")
                .type("shared-vector")
                .adapterType("shared-index")
                .enabled(true)
                .build()
        ));
        when(vectorDatabaseService.adminDiagnostics()).thenReturn(Map.of("sharedStorage", true));

        RuntimeDeploymentSearchSourceRegistry registry = new RuntimeDeploymentSearchSourceRegistry(
            knowledgeSourceConfigService,
            searchService,
            vectorDatabaseService
        );

        assertThatThrownBy(registry::validateAndLoad)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("requires handleRef");
    }
}
