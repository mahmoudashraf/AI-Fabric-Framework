package com.ai.fabric.runtime.search;

import ai.fabric.core.AISearchService;
import ai.fabric.dto.AISearchRequest;
import ai.fabric.dto.AISearchResponse;
import ai.fabric.dto.RAGRequest;
import ai.fabric.rag.RetrievalProfile;
import ai.fabric.rag.VectorDatabaseService;
import ai.fabric.rag.source.ResolvedKnowledgeSource;
import ai.fabric.rag.source.SearchSource;
import com.ai.fabric.runtime.documents.DocumentActiveVersionFilter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProfiledSearchSourceSecurityTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void nativeJsonIntegerVersionMatchesTrustedLongWithoutAdmittingRetiredOrStringVersions(boolean privateSource) {
        String versionKey = ai.fabric.indexing.document.model.DocumentMetadataKeys.SOURCE_VERSION;
        var definition = ResolvedKnowledgeSource.builder().id("approved-document").entityType("document")
            .filters(Map.of(versionKey, 1L)).build();
        var search = mock(AISearchService.class); var database = mock(VectorDatabaseService.class);
        SearchSource source = privateSource ? new DeploymentPrivateVectorSearchSource(definition, search, database)
            : new SharedIndexSearchSource(definition, search, database);
        var request = RAGRequest.builder().query("Returns?").metadata(Map.of(versionKey, 1))
            .retrievalProfile(new RetrievalProfile(RetrievalProfile.Mode.DENSE, 20, 20, 40, 6, 60, RetrievalProfile.FailurePolicy.FAIL)).build();
        assertThat(source.isEligible(request)).isTrue();
        var rows = List.of(Map.<String, Object>of("id", "active", "content", "Approved", "metadata", Map.of(versionKey, 1)),
            Map.<String, Object>of("id", "retired", "content", "Retired", "metadata", Map.of(versionKey, 2)),
            Map.<String, Object>of("id", "string", "content", "Untyped", "metadata", Map.of(versionKey, "1")));
        when(database.keywordSearch(anyString(), any())).thenReturn(AISearchResponse.builder().results(rows).build());
        var proof = source.keywordSearch(request, AISearchRequest.builder().entityType("document").limit(5).build());
        assertThat(proof.getResults()).extracting(row -> row.get("id")).containsExactly("active");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void selectedDocumentCannotBeReplacedByAnotherConfiguredSourceBeforeExternalRanking(boolean privateSource) {
        var definition = ResolvedKnowledgeSource.builder().id("another-approved-source").entityType("document")
            .filters(Map.of(ai.fabric.indexing.document.model.DocumentMetadataKeys.SOURCE_ID, "other-document")).build();
        var search = mock(AISearchService.class); var database = mock(VectorDatabaseService.class);
        SearchSource source = privateSource ? new DeploymentPrivateVectorSearchSource(definition, search, database)
            : new SharedIndexSearchSource(definition, search, database);
        var request = RAGRequest.builder().query("Returns?").metadata(Map.of(
            ai.fabric.indexing.document.model.DocumentMetadataKeys.SOURCE_ID, "selected-document"))
            .retrievalProfile(new RetrievalProfile(RetrievalProfile.Mode.DENSE, 20, 20, 40, 6, 60, RetrievalProfile.FailurePolicy.FAIL)).build();
        assertThat(source.isEligible(request)).isFalse();
        verifyNoInteractions(search, database);
        assertThat(SearchSourceResultSupport.matchesRequestedDocumentSelection(definition,
            request.toBuilder().metadata(Map.of(ai.fabric.indexing.document.model.DocumentMetadataKeys.SOURCE_ID, "other-document")).build())).isTrue();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void bothLegsRejectForeignOwnerWrongSourceAndRetiredVersionBeforeFusion(boolean privateSource) {
        var search = mock(AISearchService.class);
        var database = mock(VectorDatabaseService.class);
        var active = mock(DocumentActiveVersionFilter.class);
        var definition = ResolvedKnowledgeSource.builder().id("approved-documents").entityType("document")
            .filters(Map.of("datasetId", "approved")).build();
        SearchSource source = privateSource
            ? new DeploymentPrivateVectorSearchSource(definition, search, database, active)
            : new SharedIndexSearchSource(definition, search, database, active);
        var response = AISearchResponse.builder().results(List.of(
            row("owned", "owner", "approved", 2), row("foreign", "other", "approved", 2),
            row("wrong-source", "owner", "other", 2), row("retired", "owner", "approved", 1))).build();
        when(search.search(any(), any())).thenReturn(response);
        when(database.keywordSearch(any(), any())).thenReturn(response);
        when(active.accepts(eq("document"), anyMap())).thenAnswer(invocation ->
            Integer.valueOf(2).equals(((Map<?, ?>) invocation.getArgument(1)).get("version")));
        var profile = new RetrievalProfile(RetrievalProfile.Mode.HYBRID, 20, 20, 40, 6, 60,
            RetrievalProfile.FailurePolicy.FAIL);
        var request = RAGRequest.builder().query("20 MiB").retrievalProfile(profile).enableHybridSearch(false).build();
        var base = AISearchRequest.builder().entityType("document").limit(5).metadata(Map.of("customerId", "owner")).build();
        var dense = source.search(List.of(1.0), request, base);
        var lexical = source.keywordSearch(request, base);
        for (var result : List.of(dense, lexical)) {
            assertThat(result.getResults()).extracting(row -> row.get("id")).containsExactly("owned");
            var metadata = (Map<?, ?>) result.getResults().getFirst().get("metadata");
            assertThat(metadata.get("knowledgeSourceId")).isEqualTo("approved-documents");
        }
        verify(search).search(any(), argThat(r -> r.getLimit() == 5
            && "owner".equals(r.getMetadata().get("customerId")) && "approved".equals(r.getMetadata().get("datasetId"))));
        verify(database).keywordSearch(eq("20 MiB"), argThat(r -> r.getLimit() == 5
            && "owner".equals(r.getMetadata().get("customerId")) && "approved".equals(r.getMetadata().get("datasetId"))));
    }

    private static Map<String, Object> row(String id, String owner, String dataset, int version) {
        return Map.of("id", id, "content", id, "score", .9,
            "metadata", Map.of("customerId", owner, "datasetId", dataset, "version", version));
    }
}
