package com.ai.fabric.runtime.documents;

import ai.fabric.indexing.document.model.DocumentMetadataKeys;
import com.ai.fabric.runtime.documents.entity.DocumentSourceEntity;
import com.ai.fabric.runtime.documents.repository.DocumentSourceRepository;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentActiveVersionFilterTest {

    private final DocumentSourceRepository repository = mock(DocumentSourceRepository.class);
    private final DocumentActiveVersionFilter filter = new DocumentActiveVersionFilter(repository);

    @Test
    void leavesNonDocumentSearchResultsUntouched() {
        assertThat(filter.accepts(Map.of("entityType", "product"))).isTrue();
    }

    @Test
    void acceptsOnlyTheActiveVersionInsideTheTrustedBoundary() {
        DocumentSourceEntity source = activeSource();
        when(repository.findById("doc-1")).thenReturn(Optional.of(source));

        assertThat(filter.accepts(metadata(2L, "tenant-a", "customer-a", "dep-a"))).isTrue();
        assertThat(filter.accepts(metadata(1L, "tenant-a", "customer-a", "dep-a"))).isFalse();
        assertThat(filter.accepts(metadata(2L, "tenant-b", "customer-a", "dep-a"))).isFalse();
        assertThat(filter.accepts(metadata(2L, "tenant-a", "customer-b", "dep-a"))).isFalse();
        assertThat(filter.accepts(metadata(2L, "tenant-a", "customer-a", "dep-b"))).isFalse();
    }

    @Test
    void rejectsDocumentEvidenceWithMissingBoundaryOrDeletedRegistration() {
        DocumentSourceEntity source = activeSource();
        when(repository.findById("doc-1")).thenReturn(Optional.of(source));

        assertThat(filter.accepts(Map.of(
            DocumentMetadataKeys.SOURCE_ID, "doc-1",
            DocumentMetadataKeys.SOURCE_VERSION, 2L
        ))).isFalse();

        source.setStatus(DocumentSourceEntity.Status.DELETED);
        assertThat(filter.accepts(metadata(2L, "tenant-a", "customer-a", "dep-a"))).isFalse();
    }

    private Map<String, Object> metadata(long version, String tenant, String customer, String deployment) {
        return Map.of(
            DocumentMetadataKeys.SOURCE_ID, "doc-1",
            DocumentMetadataKeys.SOURCE_VERSION, version,
            "tenantId", tenant,
            "customerId", customer,
            "deploymentId", deployment
        );
    }

    private DocumentSourceEntity activeSource() {
        DocumentSourceEntity source = new DocumentSourceEntity();
        source.setId("doc-1");
        source.setTenantId("tenant-a");
        source.setCustomerId("customer-a");
        source.setDeploymentId("dep-a");
        source.setActiveSourceVersion(2L);
        source.setStatus(DocumentSourceEntity.Status.ACTIVE);
        return source;
    }
}
