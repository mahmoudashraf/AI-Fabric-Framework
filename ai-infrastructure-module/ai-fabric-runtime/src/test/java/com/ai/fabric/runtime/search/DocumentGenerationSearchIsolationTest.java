package com.ai.fabric.runtime.search;

import ai.fabric.core.AISearchService;
import ai.fabric.dto.AISearchRequest;
import ai.fabric.dto.AISearchResponse;
import ai.fabric.dto.RAGRequest;
import ai.fabric.indexing.document.model.DocumentMetadataKeys;
import ai.fabric.rag.VectorDatabaseService;
import ai.fabric.rag.source.ResolvedKnowledgeSource;
import ai.fabric.rag.source.SearchSource;
import com.ai.fabric.runtime.documents.DocumentActiveVersionFilter;
import com.ai.fabric.runtime.documents.DocumentRuntimeIdentity;
import com.ai.fabric.runtime.documents.entity.DocumentSourceEntity;
import com.ai.fabric.runtime.documents.repository.DocumentSourceRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentGenerationSearchIsolationTest {

    @Test
    void privateAndSharedIndexAdaptersEnforceGenerationOwnershipInEverySearchMode() {
        var repository = mock(DocumentSourceRepository.class);
        var identity = new DocumentRuntimeIdentity("tenant", "customer", "deployment", "staging", "release-b");
        var owned = new DocumentSourceEntity();
        owned.setActiveSourceVersion(1L);
        owned.setStatus(DocumentSourceEntity.Status.ACTIVE);
        when(repository.findByIdAndTenantIdAndCustomerIdAndDeploymentIdAndTargetProfileIdAndReleaseId(
            "doc-current", "tenant", "customer", "deployment", "staging", "release-b")).thenReturn(Optional.of(owned));
        var filter = new DocumentActiveVersionFilter(repository, identity, "document");
        var search = mock(AISearchService.class);
        var vectors = mock(VectorDatabaseService.class);
        List<Map<String, Object>> hits = new ArrayList<>();
        hits.add(hit("doc-old", "staging", "release-a"));
        hits.add(hit("doc-production", "production", "release-b"));
        hits.add(hit("doc-old-with-forged-metadata", "staging", "release-b"));
        hits.add(hit("doc-current", "staging", "release-b"));
        Map<String, Object> unowned = new LinkedHashMap<>(identity.requireConfiguredBoundary().metadata());
        unowned.put(DocumentMetadataKeys.SOURCE_ID, "doc-legacy");
        unowned.put(DocumentMetadataKeys.SOURCE_VERSION, 1L);
        unowned.remove("releaseId");
        hits.add(Map.of("id", "legacy", "score", 0.99d, "metadata", unowned));
        hits.add(Map.of("id", "missing-document-id", "score", 0.99d, "metadata", identity.requireConfiguredBoundary().metadata()));
        var response = AISearchResponse.builder().results(hits).totalResults(hits.size()).query("delivery").build();
        when(search.search(anyList(), any(AISearchRequest.class))).thenReturn(response);
        when(vectors.search(anyList(), any(AISearchRequest.class))).thenReturn(response);
        when(vectors.hybridSearch(anyList(), anyString(), any(AISearchRequest.class))).thenReturn(response);

        var source = ResolvedKnowledgeSource.builder().id("documents").type("deployment-private-vector")
            .adapterType("deployment-private-vector").entityType("document").enabled(true)
            .filters(Map.of("tenantId", "tenant", "customerId", "customer", "deploymentId", "deployment")).build();
        List<SearchSource> adapters = List.of(new DeploymentPrivateVectorSearchSource(source, search, vectors, filter),
            new SharedIndexSearchSource(source, search, vectors, filter));
        for (SearchSource adapter : adapters) {
            for (RAGRequest mode : List.of(
                RAGRequest.builder().query("delivery").build(),
                RAGRequest.builder().query("delivery").enableHybridSearch(true).build(),
                RAGRequest.builder().query("delivery").enableContextualSearch(true).build())) {
                var result = adapter.search(List.of(0.1d), mode,
                    AISearchRequest.builder().query("delivery").entityType("document").limit(5).build());
                assertThat(result.getResults()).extracting(row -> row.get("id")).containsExactly("doc-current");
                assertThat(result.getResults().getFirst().get("score")).isEqualTo(0.9d);
            }
        }
    }

    private Map<String, Object> hit(String sourceId, String target, String release) {
        var identity = new DocumentRuntimeIdentity("tenant", "customer", "deployment", target, release);
        var metadata = new LinkedHashMap<>(identity.requireConfiguredBoundary().metadata());
        metadata.put(DocumentMetadataKeys.SOURCE_ID, sourceId);
        metadata.put(DocumentMetadataKeys.SOURCE_VERSION, 1L);
        return Map.of("id", sourceId, "score", 0.9d, "metadata", metadata);
    }
}
