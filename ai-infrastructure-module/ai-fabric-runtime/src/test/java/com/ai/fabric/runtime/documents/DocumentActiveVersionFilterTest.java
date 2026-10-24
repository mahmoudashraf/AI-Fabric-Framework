package com.ai.fabric.runtime.documents;

import ai.fabric.indexing.document.model.DocumentMetadataKeys;
import com.ai.fabric.runtime.documents.entity.DocumentSourceEntity;
import com.ai.fabric.runtime.documents.repository.DocumentSourceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
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
    private final DocumentKnowledgeProperties properties = properties();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DocumentActiveVersionFilter filter = new DocumentActiveVersionFilter(repository, runtimeIdentity, properties, objectMapper);

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
    void rejectsMissingOrForgedOriginReleaseAndForeignTargets() {
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
    void coldReleaseAcceptsThePriorActiveVersionWithoutRewritingItsProvenance() {
        stubOwnedSource(activeSource());
        var cold = new DocumentActiveVersionFilter(repository,
            new DocumentRuntimeIdentity("tenant-a", "customer-a", "dep-a", "staging", "release-b"), properties, objectMapper);

        assertThat(cold.accepts("document", metadata(2L, "tenant-a", "customer-a", "dep-a"))).isTrue();
        Map<String, Object> forged = new LinkedHashMap<>(metadata(2L, "tenant-a", "customer-a", "dep-a"));
        forged.put("releaseId", "release-b");
        assertThat(cold.accepts("document", forged)).isFalse();
    }

    @Test
    void missingOrChangedPhysicalScopeAndNonIntegralVersionsAreRejected() {
        var source = activeSource();
        stubOwnedSource(source);
        var metadata = metadata(2L, "tenant-a", "customer-a", "dep-a");
        source.setConnectorScopeDigest(null);
        assertThat(filter.accepts(metadata)).isFalse();
        source.setConnectorScopeDigest(DocumentSourceScope.storageDigest(properties, objectMapper));
        properties.getConnector().getS3().setEndpoint("https://other-storage.example.test");
        assertThat(filter.accepts(metadata)).isFalse();
        properties.getConnector().getS3().setEndpoint("https://storage.example.test");
        Map<String, Object> fractionalVersion = new LinkedHashMap<>(metadata);
        fractionalVersion.put(DocumentMetadataKeys.SOURCE_VERSION, 2.5d);
        assertThat(filter.accepts(fractionalVersion)).isFalse();
    }

    @Test
    void providerNumbersAcceptOnlyExactPositiveIntegralLongVersionsWhileStringsRemainStrict() {
        var source = activeSource();
        source.setActiveSourceVersion(1L);
        stubOwnedSource(source);
        Map<String, Object> metadata = new LinkedHashMap<>(metadata(1L, "tenant-a", "customer-a", "dep-a"));
        metadata.put(DocumentMetadataKeys.SOURCE_VERSION, 1.0d);
        assertThat(filter.accepts(metadata)).isTrue();
        for (Object invalid : new Object[]{1.5d, 2.5d, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
            0.0d, -1.0d, new BigDecimal("9223372036854775808"), 9.223372036854776E18d, "1.0", " 1 "}) {
            metadata.put(DocumentMetadataKeys.SOURCE_VERSION, invalid);
            assertThat(filter.accepts(metadata)).as("source version %s", invalid).isFalse();
        }
        metadata.put(DocumentMetadataKeys.SOURCE_VERSION, "1");
        assertThat(filter.accepts(metadata)).isTrue();
        source.setActiveSourceVersion(Long.MAX_VALUE);
        metadata.put(DocumentMetadataKeys.SOURCE_VERSION, new BigDecimal("9223372036854775807"));
        assertThat(filter.accepts(metadata)).isTrue();
        for (Number overflow : new Number[]{new BigDecimal("9223372036854775808"), 9.223372036854776E18d,
            Double.POSITIVE_INFINITY}) {
            metadata.put(DocumentMetadataKeys.SOURCE_VERSION, overflow);
            assertThat(filter.accepts(metadata)).as("overflow source version %s", overflow).isFalse();
        }
    }

    @Test
    void changedOrMissingBindingConnectorTypeAndDatasetFailClosed() {
        DocumentSourceEntity source = activeSource();
        stubOwnedSource(source);
        var metadata = metadata(2L, "tenant-a", "customer-a", "dep-a");
        source.setConnectorBindingRef("other-binding");
        assertThat(filter.accepts(metadata)).isFalse();
        source.setConnectorBindingRef(null);
        assertThat(filter.accepts(metadata)).isFalse();
        source.setConnectorBindingRef("binding-a");
        source.setConnectorType("MOUNTED_FOLDER");
        assertThat(filter.accepts(metadata)).isFalse();
        source.setConnectorType("S3_COMPATIBLE_OBJECT_STORAGE");
        source.setDatasetId("foreign-dataset");
        assertThat(filter.accepts(metadata)).isFalse();
        source.setDatasetId("document-knowledge");
        Map<String, Object> forgedDataset = new LinkedHashMap<>(metadata);
        forgedDataset.put("datasetId", "foreign-dataset");
        assertThat(filter.accepts(forgedDataset)).isFalse();
        Map<String, Object> forgedHandle = new LinkedHashMap<>(metadata);
        forgedHandle.put("knowledgeSourceHandleRef", "foreign-handle");
        assertThat(filter.accepts(forgedHandle)).isFalse();
        forgedHandle.remove("knowledgeSourceHandleRef");
        assertThat(filter.accepts(forgedHandle)).isFalse();
        Map<String, Object> forgedId = new LinkedHashMap<>(metadata);
        forgedId.put(DocumentMetadataKeys.SOURCE_ID, " doc-1 ");
        assertThat(filter.accepts(forgedId)).isFalse();
        properties.getConnector().setBindingRef(null);
        assertThat(filter.accepts(metadata)).isFalse();
    }

    @Test
    void unconfiguredRuntimeRejectsDocumentsWithoutARepositoryLookup() {
        var unconfigured = new DocumentActiveVersionFilter(repository,
            new DocumentRuntimeIdentity("tenant-a", "customer-a", "dep-a", "", ""), properties, objectMapper);
        assertThat(unconfigured.accepts(metadata(2L, "tenant-a", "customer-a", "dep-a"))).isFalse();
        assertThat(unconfigured.accepts(Map.of("entityType", "product"))).isTrue();
        verifyNoInteractions(repository);
    }

    private void stubOwnedSource(DocumentSourceEntity source) {
        when(repository.findByIdAndTenantIdAndCustomerIdAndDeploymentIdAndTargetProfileId(
            "doc-1", "tenant-a", "customer-a", "dep-a", "staging")).thenReturn(Optional.of(source));
    }

    private Map<String, Object> metadata(long version, String tenant, String customer, String deployment) {
        return Map.of(
            DocumentMetadataKeys.SOURCE_ID, "doc-1",
            DocumentMetadataKeys.SOURCE_VERSION, version,
            "tenantId", tenant,
            "customerId", customer,
            "deploymentId", deployment,
            "targetProfileId", "staging",
            "releaseId", "release-a",
            "datasetId", "document-knowledge",
            "knowledgeSourceHandleRef", "handle-a"
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
        source.setConnectorType("S3_COMPATIBLE_OBJECT_STORAGE");
        source.setConnectorBindingRef("binding-a");
        source.setConnectorScopeDigest(DocumentSourceScope.storageDigest(properties, objectMapper));
        source.setDatasetId("document-knowledge");
        source.setActiveSourceVersion(2L);
        source.setStatus(DocumentSourceEntity.Status.ACTIVE);
        return source;
    }

    private DocumentKnowledgeProperties properties() {
        var properties = new DocumentKnowledgeProperties();
        properties.getConnector().setType(DocumentKnowledgeProperties.ConnectorType.S3_COMPATIBLE_OBJECT_STORAGE);
        properties.getConnector().setBindingRef("binding-a");
        properties.getConnector().getS3().setEndpoint("https://storage.example.test");
        properties.getConnector().getS3().setBucket("customer-bucket");
        properties.setDatasetHandleRefsJson("{\"document-knowledge\":\"handle-a\"}");
        return properties;
    }
}
