package com.ai.fabric.runtime.documents;

import ai.fabric.indexing.document.model.DocumentMetadataKeys;
import com.ai.fabric.runtime.documents.entity.DocumentSourceEntity;
import com.ai.fabric.runtime.documents.repository.DocumentSourceRepository;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DocumentActiveVersionFilterTest {

    private final DocumentSourceRepository repository = mock(DocumentSourceRepository.class);
    private final DocumentRuntimeIdentity runtimeIdentity = new DocumentRuntimeIdentity(
        "tenant-a", "customer-a", "dep-a", "staging", "release-a");
    private final DocumentActiveVersionFilter filter = new DocumentActiveVersionFilter(repository, runtimeIdentity, "document");

    @Test
    void leavesNonDocumentSearchResultsUntouched() {
        assertThat(filter.accepts(Map.of("entityType", "product"))).isTrue();
        assertThat(filter.accepts("product", Map.of())).isTrue();
        assertThat(filter.accepts("document", Map.of())).isFalse();
        assertThat(filter.accepts("document", null)).isFalse();
    }

    @Test
    void acceptsOnlyTheActiveVersionInsideTheTrustedBoundary() {
        DocumentSourceEntity source = activeSource();
        stubOwnedSource(source);

        assertThat(filter.accepts(metadata(2L, "tenant-a", "customer-a", "dep-a"))).isTrue();
        assertThat(filter.accepts(metadata(1L, "tenant-a", "customer-a", "dep-a"))).isFalse();
        assertThat(filter.accepts(metadata(2L, "tenant-b", "customer-a", "dep-a"))).isFalse();
        assertThat(filter.accepts(metadata(2L, "tenant-a", "customer-b", "dep-a"))).isFalse();
        assertThat(filter.accepts(metadata(2L, "tenant-a", "customer-a", "dep-b"))).isFalse();
    }

    @Test
    void rejectsDocumentEvidenceWithMissingBoundaryOrDeletedRegistration() {
        DocumentSourceEntity source = activeSource();
        stubOwnedSource(source);

        assertThat(filter.accepts(Map.of(
            DocumentMetadataKeys.SOURCE_ID, "doc-1",
            DocumentMetadataKeys.SOURCE_VERSION, 2L
        ))).isFalse();

        source.setStatus(DocumentSourceEntity.Status.DELETED);
        assertThat(filter.accepts(metadata(2L, "tenant-a", "customer-a", "dep-a"))).isFalse();
    }

    @Test
    void rejectsMissingAndForeignGenerationsEvenWhenTheirMetadataMatchesTheirOwnSource() {
        stubOwnedSource(activeSource());
        Map<String, Object> metadata = new LinkedHashMap<>(metadata(2L, "tenant-a", "customer-a", "dep-a"));
        metadata.remove("releaseId");
        assertThat(filter.accepts(metadata)).isFalse();
        metadata.put("releaseId", "release-b");
        assertThat(filter.accepts(metadata)).isFalse();
        metadata.put("releaseId", "release-a");
        metadata.put("targetProfileId", "production");
        assertThat(filter.accepts(metadata)).isFalse();
    }

    @Test
    void unconfiguredRuntimeRejectsDocumentsWithoutARepositoryLookup() {
        var unconfigured = new DocumentActiveVersionFilter(repository,
            new DocumentRuntimeIdentity("tenant-a", "customer-a", "dep-a", "", ""), "document");
        assertThat(unconfigured.accepts(metadata(2L, "tenant-a", "customer-a", "dep-a"))).isFalse();
        assertThat(unconfigured.accepts(Map.of("entityType", "product"))).isTrue();
        verifyNoInteractions(repository);
    }

    private void stubOwnedSource(DocumentSourceEntity source) {
        when(repository.findByIdAndTenantIdAndCustomerIdAndDeploymentIdAndTargetProfileIdAndReleaseId(
            "doc-1", "tenant-a", "customer-a", "dep-a", "staging", "release-a")).thenReturn(Optional.of(source));
    }

    private Map<String, Object> metadata(long version, String tenant, String customer, String deployment) {
        return Map.of(
            DocumentMetadataKeys.SOURCE_ID, "doc-1",
            DocumentMetadataKeys.SOURCE_VERSION, version,
            "tenantId", tenant,
            "customerId", customer,
            "deploymentId", deployment,
            "targetProfileId", "staging",
            "releaseId", "release-a"
        );
    }

    private DocumentSourceEntity activeSource() {
        DocumentSourceEntity source = new DocumentSourceEntity();
        source.setId("doc-1");
        source.setTenantId("tenant-a");
        source.setCustomerId("customer-a");
        source.setDeploymentId("dep-a");
        source.setTargetProfileId("staging");
        source.setReleaseId("release-a");
        source.setActiveSourceVersion(2L);
        source.setStatus(DocumentSourceEntity.Status.ACTIVE);
        return source;
    }
}
