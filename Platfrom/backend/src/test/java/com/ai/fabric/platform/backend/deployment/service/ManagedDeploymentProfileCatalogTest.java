package com.ai.fabric.platform.backend.deployment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManagedDeploymentProfileCatalogTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void milvusDefaultsToUsernamePasswordAndTokenRequiresOnlyTokenPurpose() throws Exception {
        var config = objectMapper.readTree("{\"vectorStrategy\":\"milvus\"}");
        assertThat(ManagedDeploymentProfileCatalog.resolveMilvusAuthenticationType(config)).isEqualTo("USERNAME_PASSWORD");
        assertThat(ManagedDeploymentProfileCatalog.optionalVectorSecretNames(config)).containsExactlyInAnyOrder("MILVUS_USERNAME", "MILVUS_PASSWORD");
        ((com.fasterxml.jackson.databind.node.ObjectNode) config).put("milvusAuthenticationType", "TOKEN");
        assertThat(ManagedDeploymentProfileCatalog.requiredVectorSecretName(config)).isEqualTo("MILVUS_TOKEN");
        assertThat(ManagedDeploymentProfileCatalog.optionalVectorSecretNames(config)).isEmpty();
        ((com.fasterxml.jackson.databind.node.ObjectNode) config).put("vectorProvisioningMode", "EXTERNAL_EXISTING")
            .put("vectorStoragePosture", "SHARED").put("milvusExternalBindingRequired", true);
        ManagedDeploymentProfileCatalog.validateMilvusAuthentication(config, false);
        assertThatThrownBy(() -> ManagedDeploymentProfileCatalog.validateMilvusAuthentication(config, true))
            .hasMessage("MILVUS_TOKEN_SECRET_REFERENCE_REQUIRED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"token\"", "\"BASIC\"", "\"\"", "null", "1", "{}"})
    void milvusRejectsInvalidExplicitAuthenticationTypes(String value) throws Exception {
        var config = objectMapper.readTree("{\"vectorStrategy\":\"milvus\",\"milvusAuthenticationType\":" + value + "}");
        assertThatThrownBy(() -> ManagedDeploymentProfileCatalog.validateMilvusAuthentication(config, false))
            .hasMessage("MILVUS_AUTHENTICATION_TYPE_INVALID");
    }

    @Test
    void milvusRejectsMixedModesAndKeepsManagedProvisionerOnUsernamePassword() throws Exception {
        var config = objectMapper.createObjectNode().put("vectorStrategy", "milvus")
            .put("milvusAuthenticationType", "TOKEN").put("milvusRuntimeUsernameSecretName", "FAKE_USERNAME")
            .put("vectorProvisioningMode", "EXTERNAL_EXISTING").put("vectorStoragePosture", "SHARED")
            .put("milvusExternalBindingRequired", true);
        assertThatThrownBy(() -> ManagedDeploymentProfileCatalog.validateMilvusAuthentication(config, false))
            .hasMessage("MILVUS_AUTHENTICATION_MIXED_MODE");
        config.remove("milvusRuntimeUsernameSecretName");
        config.put("vectorProvisioningMode", "PLATFORM_MANAGED");
        assertThatThrownBy(() -> ManagedDeploymentProfileCatalog.validateMilvusAuthentication(config, false))
            .hasMessage("MILVUS_TOKEN_EXTERNAL_BINDING_REQUIRED");
        config.remove("milvusAuthenticationType");
        config.remove("milvusExternalBindingRequired");
        assertThat(ManagedDeploymentProfileCatalog.requiredVectorSecretName(config)).isEqualTo("ZILLIZ_CLOUD_API_KEY");
        ManagedDeploymentProfileCatalog.validateMilvusAuthentication(config, true);
        config.put("milvusRuntimeTokenSecretName", "FAKE_TOKEN_REF");
        assertThatThrownBy(() -> ManagedDeploymentProfileCatalog.validateMilvusAuthentication(config, false))
            .hasMessage("MILVUS_AUTHENTICATION_MIXED_MODE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ZILLIZ_CLOUD_API_KEY", "MANAGED_EXTERNAL_VECTOR_TOKEN_OTHER_CUSTOMER"})
    void tokenCannotSelectArbitraryCredentialsWithoutFormalBinding(String ref) {
        var config = objectMapper.createObjectNode().put("vectorStrategy", "milvus")
            .put("milvusAuthenticationType", "TOKEN").put("milvusRuntimeTokenSecretName", ref)
            .put("vectorProvisioningMode", "EXTERNAL_EXISTING").put("vectorStoragePosture", "SHARED");
        assertThatThrownBy(() -> ManagedDeploymentProfileCatalog.validateMilvusAuthentication(config, false))
            .hasMessage("MILVUS_TOKEN_EXTERNAL_BINDING_REQUIRED");
        config.put("milvusExternalBindingRequired", false);
        assertThatThrownBy(() -> ManagedDeploymentProfileCatalog.validateMilvusAuthentication(config, true))
            .hasMessage("MILVUS_TOKEN_EXTERNAL_BINDING_REQUIRED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "[]", "false", "123", "null"})
    void rejectsNonTextualLiteralCredentialFields(String value) throws Exception {
        for (String field : java.util.List.of("milvusToken", "milvusUsername", "milvusPassword")) {
            var config = objectMapper.readTree("{\"vectorStrategy\":\"milvus\",\"" + field + "\":" + value + "}");
            assertThatThrownBy(() -> ManagedDeploymentProfileCatalog.validateMilvusAuthentication(config, false))
                .hasMessage("MILVUS_LITERAL_CREDENTIAL_UNSUPPORTED");
        }
    }

    @Test
    void defaultPrivateRuntimeAcceptedIssuersCoverCurrentFirstPartyRuntimeCallers() throws Exception {
        String issuers = ManagedDeploymentProfileCatalog.effectivePrivateRuntimeAcceptedIssuers(
            objectMapper.readTree("{}")
        );

        assertThat(issuers).isEqualTo(
            "platform-runtime:SESSION,platform-runtime:API_KEY,platform-poc:SESSION,platform-poc:API_KEY,platform-poc:SYSTEM,platform-release-verification,platform-document-operations,platform-marketplace-dataset-sync,platform-vectorization-runner,platform-vectorization-verification,platform-runtime-coverage"
        );
    }

    @Test
    void configuredPrivateRuntimeAcceptedIssuersExtendPlatformDefaults() throws Exception {
        String issuers = ManagedDeploymentProfileCatalog.effectivePrivateRuntimeAcceptedIssuers(
            objectMapper.readTree("""
                {
                  "privateRuntimeAcceptedIssuers": "trusted-backend-app,shopify-app"
                }
                """)
        );

        assertThat(issuers)
            .startsWith("platform-runtime:SESSION,platform-runtime:API_KEY")
            .contains("platform-release-verification")
            .contains("platform-marketplace-dataset-sync")
            .endsWith("trusted-backend-app,shopify-app");
    }

    @Test
    void recommendedOrchestrationModelUsesLatencyOptimizedOpenAiDefault() {
        assertThat(ManagedDeploymentProfileCatalog.recommendedOrchestrationModel("openai"))
            .isEqualTo("gpt-5.4-nano");
        assertThat(ManagedDeploymentProfileCatalog.recommendedOrchestrationModel("anthropic"))
            .isEqualTo("claude-3-haiku-20240307");
    }

    @Test
    void providerFallbackIsDisabledByDefaultForManagedRuntimeMemoryLimits() throws Exception {
        assertThat(ManagedDeploymentProfileCatalog.providerEnableFallback(objectMapper.readTree("{}"))).isFalse();
        assertThat(ManagedDeploymentProfileCatalog.providerEnableFallback(objectMapper.readTree("""
            {"enableFallback":true}
            """))).isTrue();
    }
}
