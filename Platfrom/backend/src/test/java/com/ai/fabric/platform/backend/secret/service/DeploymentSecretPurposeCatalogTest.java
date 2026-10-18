package com.ai.fabric.platform.backend.secret.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeploymentSecretPurposeCatalogTest {

    @Test
    void nativeMilvusTokenHasSingleSecretPurposeAndScopedMetadataInference() {
        var catalog = new DeploymentSecretPurposeCatalog();
        var definition = catalog.requireDefinition("MILVUS_TOKEN");
        assertThat(definition.paired()).isFalse();
        assertThat(definition.primaryGlobalSecretName()).isEqualTo("MILVUS_TOKEN");
        assertThat(definition.secondaryGlobalSecretName()).isNull();
        assertThat(catalog.inferPurposeForSecretName("MANAGED_EXTERNAL_VECTOR_TOKEN_FAKE_TARGET")).isEqualTo("MILVUS_TOKEN");
        assertThat(catalog.requireDefinition("MILVUS_RUNTIME_CREDENTIALS").paired()).isTrue();
    }
}
