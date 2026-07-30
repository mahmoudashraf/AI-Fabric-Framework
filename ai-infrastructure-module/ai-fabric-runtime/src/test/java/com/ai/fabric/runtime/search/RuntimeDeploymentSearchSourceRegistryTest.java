package com.ai.fabric.runtime.search;

import ai.fabric.core.AISearchService;
import ai.fabric.dto.AIAccessSubjectContext;
import ai.fabric.dto.AISearchRequest;
import ai.fabric.dto.AISearchResponse;
import ai.fabric.dto.RAGRequest;
import ai.fabric.rag.VectorDatabaseService;
import ai.fabric.rag.source.ResolvedKnowledgeSource;
import com.ai.fabric.runtime.config.RuntimeDeploymentKnowledgeSourceConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeDeploymentSearchSourceRegistryTest {

    private static final String DEPLOYMENT_KNOWLEDGE_SPECIALIST_SCOPE =
        "specialist:deployment-knowledge-specialist@1";

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
    void deploymentKnowledgeSpecialistUsesOnlyTrustedDeploymentPrivateSources() {
        when(knowledgeSourceConfigService.currentSources()).thenReturn(List.of(
            ResolvedKnowledgeSource.builder()
                .id("deployment-documents")
                .type("deployment-private-vector")
                .adapterType("deployment-private-vector")
                .attributionLabel("Deployment documents")
                .entityType("document")
                .enabled(true)
                .build(),
            ResolvedKnowledgeSource.builder()
                .id("shared-documents")
                .type("shared-vector")
                .adapterType("shared-index")
                .attributionLabel("Shared documents")
                .entityType("document")
                .handleRef("marketplace/shared-documents")
                .enabled(true)
                .build()
        ));
        when(vectorDatabaseService.adminDiagnostics()).thenReturn(Map.of("sharedStorage", true));
        when(vectorDatabaseService.supportsSearchMetadataFiltering()).thenReturn(true);

        RuntimeDeploymentSearchSourceRegistry registry = new RuntimeDeploymentSearchSourceRegistry(
            knowledgeSourceConfigService,
            searchService,
            vectorDatabaseService
        );
        registry.validateAndLoad();

        assertThat(registry.resolveSearchSources(deploymentKnowledgeRequest("tenant-a", "dep-a")))
            .singleElement()
            .satisfies(source -> {
                assertThat(source.source().getId()).isEqualTo("deployment-documents");
                assertThat(source.source().getFilters())
                    .containsEntry("tenantId", "tenant-a")
                    .containsEntry("deploymentId", "dep-a");
            });
    }

    @Test
    void deploymentKnowledgeSpecialistRejectsMissingTrustedBoundary() {
        when(knowledgeSourceConfigService.currentSources()).thenReturn(List.of());
        when(vectorDatabaseService.adminDiagnostics()).thenReturn(Map.of("sharedStorage", false));

        RuntimeDeploymentSearchSourceRegistry registry = new RuntimeDeploymentSearchSourceRegistry(
            knowledgeSourceConfigService,
            searchService,
            vectorDatabaseService
        );
        registry.validateAndLoad();

        assertThatThrownBy(() -> registry.resolveSearchSources(
            deploymentKnowledgeRequest(null, "dep-a")
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("trusted tenant and deployment");
    }

    @Test
    void deploymentKnowledgeSpecialistRejectsConflictingSourceBoundary() {
        when(knowledgeSourceConfigService.currentSources()).thenReturn(List.of(
            ResolvedKnowledgeSource.builder()
                .id("deployment-documents")
                .type("deployment-private-vector")
                .adapterType("deployment-private-vector")
                .attributionLabel("Deployment documents")
                .entityType("document")
                .filters(Map.of("tenantId", "tenant-b"))
                .enabled(true)
                .build()
        ));
        when(vectorDatabaseService.adminDiagnostics()).thenReturn(Map.of("sharedStorage", false));
        when(vectorDatabaseService.supportsSearchMetadataFiltering()).thenReturn(true);

        RuntimeDeploymentSearchSourceRegistry registry = new RuntimeDeploymentSearchSourceRegistry(
            knowledgeSourceConfigService,
            searchService,
            vectorDatabaseService
        );
        registry.validateAndLoad();

        assertThatThrownBy(() -> registry.resolveSearchSources(
            deploymentKnowledgeRequest("tenant-a", "dep-a")
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("conflicts with trusted tenantId");
    }

    @Test
    void deploymentKnowledgeSpecialistRequiresProviderMetadataFiltering() {
        when(knowledgeSourceConfigService.currentSources()).thenReturn(List.of());
        when(vectorDatabaseService.adminDiagnostics()).thenReturn(Map.of("sharedStorage", false));
        when(vectorDatabaseService.supportsSearchMetadataFiltering()).thenReturn(false);

        RuntimeDeploymentSearchSourceRegistry registry = new RuntimeDeploymentSearchSourceRegistry(
            knowledgeSourceConfigService,
            searchService,
            vectorDatabaseService
        );
        registry.validateAndLoad();

        assertThatThrownBy(() -> registry.resolveSearchSources(
            deploymentKnowledgeRequest("tenant-a", "dep-a")
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("metadata-filtered vector search");
    }

    @Test
    void deploymentKnowledgeSearchFiltersAtProviderAndPostVerifiesHits() {
        when(knowledgeSourceConfigService.currentSources()).thenReturn(List.of());
        when(vectorDatabaseService.adminDiagnostics()).thenReturn(Map.of("sharedStorage", false));
        when(vectorDatabaseService.supportsSearchMetadataFiltering()).thenReturn(true);
        when(searchService.search(anyList(), any(AISearchRequest.class)))
            .thenReturn(AISearchResponse.builder()
                .results(List.of(
                    Map.of(
                        "id", "tenant-a-document",
                        "score", 0.95,
                        "metadata", Map.of(
                            "tenantId", "tenant-a",
                            "deploymentId", "dep-a"
                        )
                    ),
                    Map.of(
                        "id", "tenant-b-document",
                        "score", 0.94,
                        "metadata", Map.of(
                            "tenantId", "tenant-b",
                            "deploymentId", "dep-b"
                        )
                    )
                ))
                .totalResults(2)
                .maxScore(0.95)
                .query("deployment release")
                .build());

        RuntimeDeploymentSearchSourceRegistry registry = new RuntimeDeploymentSearchSourceRegistry(
            knowledgeSourceConfigService,
            searchService,
            vectorDatabaseService
        );
        registry.validateAndLoad();
        RAGRequest request = deploymentKnowledgeRequest("tenant-a", "dep-a");
        var source = registry.resolveSearchSources(request).getFirst();

        AISearchResponse response = source.search(
            List.of(0.1, 0.2),
            request,
            AISearchRequest.builder()
                .query("deployment release")
                .entityType("document")
                .limit(5)
                .build()
        );

        ArgumentCaptor<AISearchRequest> searchRequest =
            ArgumentCaptor.forClass(AISearchRequest.class);
        verify(searchService).search(anyList(), searchRequest.capture());
        assertThat(searchRequest.getValue().getMetadata())
            .containsEntry("tenantId", "tenant-a")
            .containsEntry("deploymentId", "dep-a");
        assertThat(response.getResults())
            .extracting(result -> result.get("id"))
            .containsExactly("tenant-a-document");
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
                "processingTimeMs", 18L,
                "resultsCount", 2L
            ),
            Map.of(
                "sourceId", "shared-catalog",
                "sourceType", "shared-vector",
                "adapterType", "shared-index",
                "status", "FAILED",
                "reason", "search_error",
                "errorMessage", "catalog unavailable",
                "processingTimeMs", 42L,
                "resultsCount", 0L
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
                .containsEntry("failureCount", 0L)
                .containsEntry("lastResultsCount", 2L))
            .anySatisfy(entry -> assertThat(entry)
                .containsEntry("sourceId", "shared-catalog")
                .containsEntry("healthStatus", "DEGRADED")
                .containsEntry("failureCount", 1L)
                .containsEntry("lastReason", "search_error")
                .containsEntry("lastFailureMessage", "catalog unavailable")
                .containsEntry("lastResultsCount", 0L));
    }

    @Test
    void registrySupportsPrivateAndPlatformAuthModesWhenConfigured() {
        when(knowledgeSourceConfigService.currentSources()).thenReturn(List.of(
            ResolvedKnowledgeSource.builder()
                .id("shared-catalog")
                .type("shared-vector")
                .adapterType("shared-index")
                .attributionLabel("Shared catalog")
                .entityType("product")
                .handleRef("marketplace/catalog")
                .authModes(List.of("PRIVATE_RUNTIME_BACKEND_MEDIATED", "PLATFORM_PROXY_SESSION"))
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

        RAGRequest privateRequest = RAGRequest.builder()
            .query("tell me about Alienware")
            .entityType("product")
            .authContext(AIAccessSubjectContext.builder().authMode("PRIVATE_RUNTIME_BACKEND_MEDIATED").build())
            .build();
        RAGRequest platformRequest = RAGRequest.builder()
            .query("tell me about Alienware")
            .entityType("product")
            .authContext(AIAccessSubjectContext.builder().authMode("PLATFORM_PROXY_SESSION").build())
            .build();
        RAGRequest anonymousRequest = RAGRequest.builder()
            .query("tell me about Alienware")
            .entityType("product")
            .authContext(AIAccessSubjectContext.builder().authMode("PUBLIC_RUNTIME_ANONYMOUS").build())
            .build();

        assertThat(registry.resolveSearchSources(privateRequest))
            .extracting(source -> source.isEligible(privateRequest))
            .containsExactly(true, true);
        assertThat(registry.resolveSearchSources(platformRequest))
            .extracting(source -> source.isEligible(platformRequest))
            .containsExactly(true, true);
        assertThat(registry.resolveSearchSources(anonymousRequest))
            .extracting(source -> source.isEligible(anonymousRequest))
            .containsExactly(true, false);
    }

    @Test
    void registryKeepsDisabledSourcesVisibleAndFailSafe() {
        when(knowledgeSourceConfigService.currentSources()).thenReturn(List.of(
            ResolvedKnowledgeSource.builder()
                .id("shared-catalog")
                .type("shared-vector")
                .adapterType("shared-index")
                .attributionLabel("Shared catalog")
                .entityType("product")
                .handleRef("marketplace/catalog")
                .enabled(false)
                .build()
        ));
        when(vectorDatabaseService.adminDiagnostics()).thenReturn(Map.of("sharedStorage", true));

        RuntimeDeploymentSearchSourceRegistry registry = new RuntimeDeploymentSearchSourceRegistry(
            knowledgeSourceConfigService,
            searchService,
            vectorDatabaseService
        );
        registry.validateAndLoad();

        RAGRequest request = RAGRequest.builder()
            .query("show catalog")
            .entityType("product")
            .build();

        assertThat(registry.resolveSearchSources(request))
            .extracting(source -> source.source().getId())
            .containsExactly("deployment-private-vector", "shared-catalog");
        assertThat(registry.resolveSearchSources(request))
            .extracting(source -> source.isEligible(request))
            .containsExactly(true, false);

        registry.recordSearchExecution(List.of(
            Map.of(
                "sourceId", "shared-catalog",
                "sourceType", "shared-vector",
                "adapterType", "shared-index",
                "status", "SKIPPED",
                "reason", "disabled"
            )
        ), false);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sources = (List<Map<String, Object>>) registry.adminDiagnostics().get("sources");
        assertThat(sources)
            .anySatisfy(entry -> assertThat(entry)
                .containsEntry("sourceId", "shared-catalog")
                .containsEntry("enabled", false)
                .containsEntry("healthStatus", "DISABLED")
                .containsEntry("lastStatus", "SKIPPED")
                .containsEntry("skippedCount", 1L));
    }

    @Test
    void registrySupportsMultipleHandleScopedPrivateSourcesForDedicatedStorage() {
        when(knowledgeSourceConfigService.currentSources()).thenReturn(List.of(
            ResolvedKnowledgeSource.builder()
                .id("produs-safe-service-category")
                .type("deployment-private-vector")
                .adapterType("deployment-private-vector")
                .attributionLabel("Service categories")
                .entityType("service-category")
                .handleRef("plugin/produs/service-category")
                .filters(Map.of("knowledgeSourceHandleRef", "plugin/produs/service-category"))
                .enabled(true)
                .build(),
            ResolvedKnowledgeSource.builder()
                .id("produs-safe-service-module")
                .type("deployment-private-vector")
                .adapterType("deployment-private-vector")
                .attributionLabel("Service modules")
                .entityType("service-module")
                .handleRef("plugin/produs/service-module")
                .filters(Map.of("knowledgeSourceHandleRef", "plugin/produs/service-module"))
                .enabled(true)
                .build()
        ));
        when(vectorDatabaseService.adminDiagnostics()).thenReturn(Map.of("sharedStorage", false));

        RuntimeDeploymentSearchSourceRegistry registry = new RuntimeDeploymentSearchSourceRegistry(
            knowledgeSourceConfigService,
            searchService,
            vectorDatabaseService
        );
        registry.validateAndLoad();

        assertThat(registry.resolveSearchSources(RAGRequest.builder()
                .query("API security review")
                .entityType("service-module")
                .build()))
            .extracting(source -> source.source().getId())
            .containsExactly(
                "deployment-private-vector",
                "produs-safe-service-category",
                "produs-safe-service-module"
            );
    }

    @Test
    void handleScopedPrivateSourceCanReadOnlyItsOwnHandleDocuments() {
        ResolvedKnowledgeSource source = ResolvedKnowledgeSource.builder()
            .id("produs-safe-service-module")
            .type("deployment-private-vector")
            .adapterType("deployment-private-vector")
            .attributionLabel("Service modules")
            .entityType("service-module")
            .handleRef("plugin/produs/service-module")
            .filters(Map.of("knowledgeSourceHandleRef", "plugin/produs/service-module"))
            .enabled(true)
            .build();
        AISearchResponse response = AISearchResponse.builder()
            .results(List.of(
                Map.of(
                    "id", "api-security-review",
                    "score", 0.92,
                    "metadata", Map.of("knowledgeSourceHandleRef", "plugin/produs/service-module")
                ),
                Map.of(
                    "id", "security-hardening",
                    "score", 0.91,
                    "metadata", Map.of("knowledgeSourceHandleRef", "plugin/produs/package-template")
                )
            ))
            .totalResults(2)
            .maxScore(0.92)
            .query("API security review")
            .build();

        AISearchResponse filtered = SearchSourceResultSupport.filterAndDecorate(response, source, source.getFilters());

        assertThat(filtered.getResults())
            .hasSize(1)
            .first()
            .satisfies(result -> {
                assertThat(result).containsEntry("id", "api-security-review");
                assertThat(result.get("metadata")).isInstanceOf(Map.class);
                @SuppressWarnings("unchecked")
                Map<String, Object> metadata = (Map<String, Object>) result.get("metadata");
                assertThat(metadata)
                    .containsEntry("knowledgeSourceId", "produs-safe-service-module")
                    .containsEntry("knowledgeSourceAdapterType", "deployment-private-vector")
                    .containsEntry("knowledgeSourceHandleRef", "plugin/produs/service-module");
            });
    }

    @Test
    void sourceFiltersAcceptLuceneSerializedMetadataWithoutWeakeningScopeChecks() {
        ResolvedKnowledgeSource source = ResolvedKnowledgeSource.builder()
            .id("gate-a-tenant-a-documents")
            .type("document")
            .adapterType("deployment-private-vector")
            .attributionLabel("Gate A tenant A evidence")
            .entityType("document")
            .filters(Map.of(
                "tenantId", "ten-gate-a",
                "deploymentId", "dep-gate-a-040"
            ))
            .enabled(true)
            .build();
        AISearchResponse response = AISearchResponse.builder()
            .results(List.of(
                Map.of(
                    "id", "gate-a-aurora",
                    "score", 0.94,
                    "metadata", """
                        {"tenantId":"ten-gate-a","deploymentId":"dep-gate-a-040","source":"gate-a"}
                        """
                ),
                Map.of(
                    "id", "gate-b-borealis",
                    "score", 0.93,
                    "metadata", """
                        {"tenantId":"ten-gate-b","deploymentId":"dep-gate-a-040","source":"gate-b"}
                        """
                ),
                Map.of(
                    "id", "malformed",
                    "score", 0.92,
                    "metadata", "{not-json"
                )
            ))
            .totalResults(3)
            .maxScore(0.94)
            .query("Project Aurora release checks")
            .build();

        AISearchResponse filtered = SearchSourceResultSupport.filterAndDecorate(
            response,
            source,
            source.getFilters()
        );

        assertThat(filtered.getResults())
            .hasSize(1)
            .first()
            .satisfies(result -> {
                assertThat(result).containsEntry("id", "gate-a-aurora");
                assertThat(result.get("metadata")).isInstanceOf(Map.class);
                @SuppressWarnings("unchecked")
                Map<String, Object> metadata = (Map<String, Object>) result.get("metadata");
                assertThat(metadata)
                    .containsEntry("tenantId", "ten-gate-a")
                    .containsEntry("deploymentId", "dep-gate-a-040")
                    .containsEntry("knowledgeSourceId", "gate-a-tenant-a-documents");
            });
    }

    @Test
    void defaultPrivateSourceStillExcludesHandleScopedMarketplaceDocuments() {
        ResolvedKnowledgeSource source = ResolvedKnowledgeSource.builder()
            .id("deployment-private-vector")
            .type("deployment-private-vector")
            .adapterType("deployment-private-vector")
            .attributionLabel("Deployment knowledge")
            .enabled(true)
            .build();
        AISearchResponse response = AISearchResponse.builder()
            .results(List.of(
                Map.of(
                    "id", "private-note",
                    "score", 0.88,
                    "metadata", Map.of("scope", "private")
                ),
                Map.of(
                    "id", "marketplace-doc",
                    "score", 0.91,
                    "metadata", Map.of("knowledgeSourceHandleRef", "plugin/produs/service-module")
                )
            ))
            .totalResults(2)
            .maxScore(0.91)
            .query("deployment notes")
            .build();

        AISearchResponse filtered = SearchSourceResultSupport.filterAndDecorate(response, source, source.getFilters());

        assertThat(filtered.getResults())
            .extracting(result -> result.get("id"))
            .containsExactly("private-note");
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

    private RAGRequest deploymentKnowledgeRequest(
        String tenantId,
        String deploymentId
    ) {
        return RAGRequest.builder()
            .query("What changed in this deployment?")
            .entityType("document")
            .authContext(
                AIAccessSubjectContext.builder()
                    .subjectId(deploymentId)
                    .subjectType("deployment")
                    .authMode("TRUSTED_APPLICATION")
                    .callerType("SERVICE")
                    .tenantId(tenantId)
                    .deploymentId(deploymentId)
                    .grantedScopes(List.of(
                        DEPLOYMENT_KNOWLEDGE_SPECIALIST_SCOPE,
                        "vector:document"
                    ))
                    .build()
            )
            .build();
    }
}
