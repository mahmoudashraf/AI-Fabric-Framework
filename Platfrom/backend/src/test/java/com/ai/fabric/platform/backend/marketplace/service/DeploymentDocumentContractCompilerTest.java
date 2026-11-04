package com.ai.fabric.platform.backend.marketplace.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

class DeploymentDocumentContractCompilerTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void compilationPreservesNeutralOwnershipAndResolvesTheBindingWithoutMutatingTheManifest() throws Exception {
        var compiler = mock(DeploymentMarketplaceDraftCompilerService.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(compiler, "objectMapper", mapper);
        ObjectNode connector = (ObjectNode) mapper.readTree("""
            {"connectorType":"S3_COMPATIBLE_OBJECT_STORAGE","storageOwnership":"BOUND_CONNECTION",
             "bindingRefField":"documentStorageBindingRef","allowedOperations":["LIST","READ","HEAD"],
             "deleteSourceOnRemoval":false}
            """);
        var dataset = new MarketplaceManifestService.ParsedMarketplaceDatasetDefinition(
            "documents", "document", "CUSTOMER_SCOPED", "DEPLOYMENT_ONLY", "EXTERNAL_DOCUMENT_STORAGE",
            "VERSIONED_REPLACE", null, null, null, "S3_COMPATIBLE_OBJECT_STORAGE", "documentStorageBindingRef",
            null, null, connector, null, null
        );

        ObjectNode compiled = ReflectionTestUtils.invokeMethod(compiler, "resolveSourceConnector", dataset,
            mapper.createObjectNode().put("documentStorageBindingRef", "slot:document-storage"), mapper.createObjectNode());

        assertThat(compiled.path("storageOwnership").asText()).isEqualTo("BOUND_CONNECTION");
        assertThat(compiled.path("bindingRef").asText()).isEqualTo("slot:document-storage");
        assertThat(compiled.path("allowedOperations")).isEqualTo(connector.path("allowedOperations"));
        assertThat(compiled.path("deleteSourceOnRemoval").asBoolean()).isFalse();
        assertThat(connector.has("bindingRef")).isFalse();
    }
}
