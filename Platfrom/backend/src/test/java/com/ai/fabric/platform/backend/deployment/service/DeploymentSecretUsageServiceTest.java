package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.config.PlatformDeliveryProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentDraftEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSecretUsageSummary;
import com.ai.fabric.platform.backend.secret.model.PlatformSecretSummary;
import com.ai.fabric.platform.backend.secret.service.DeploymentProviderSecretResolutionService;
import com.ai.fabric.platform.backend.secret.service.DeploymentSecretPurposeCatalog;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DeploymentSecretUsageServiceTest {

    @Test
    void requiredTokenBindingDoesNotReportGlobalCredentialsAsReady() {
        var secrets = mock(PlatformSecretService.class);
        when(secrets.listSecrets()).thenReturn(List.of(new PlatformSecretSummary(
            "MILVUS_TOKEN", "Milvus token", "Fake global credential", false, true, "DATABASE", null,
            "GLOBAL", "PLATFORM", "MILVUS_TOKEN", null, false, "KEEP")));
        var resolutions = mock(DeploymentProviderSecretResolutionService.class);
        var service = new DeploymentSecretUsageService(secrets, resolutions, new DeploymentSecretPurposeCatalog(),
            new PlatformDeliveryProperties("https://platform.example", false, Duration.ofDays(1)), new ObjectMapper(),
            mock(DeploymentExistingVectorBindingService.class));
        var draft = new DeploymentDraftEntity();
        draft.setProviderConfigJson("""
            {"vectorStrategy":"milvus","milvusAuthenticationType":"TOKEN","milvusExternalBindingRequired":true,
             "vectorProvisioningMode":"EXTERNAL_EXISTING","vectorStoragePosture":"SHARED"}
            """);
        var result = service.build("dep-fake", draft);
        assertThat(result.secrets()).filteredOn(item -> item.secretPurpose() != null && item.secretPurpose().startsWith("MILVUS"))
            .singleElement().satisfies(item -> {
                assertThat(item.secretPurpose()).isEqualTo("MILVUS_TOKEN");
                assertThat(item.required()).isTrue();
                assertThat(item.present()).isFalse();
                assertThat(item.status()).isEqualTo("MISSING");
                assertThat(item.source()).isEqualTo("DEPLOYMENT_MANAGED");
            });
        org.mockito.Mockito.verify(resolutions, org.mockito.Mockito.never()).summarize("dep-fake", "MILVUS_TOKEN");
        draft.setProviderConfigJson("""
            {"vectorStrategy":"milvus","milvusAuthenticationType":"TOKEN","milvusExternalBindingRequired":true,
             "vectorProvisioningMode":"EXTERNAL_EXISTING","vectorStoragePosture":"SHARED",
             "milvusRuntimeTokenSecretName":"MANAGED_EXTERNAL_VECTOR_TOKEN_FAKE_TARGET"}
            """);
        when(secrets.isSecretPresent("MANAGED_EXTERNAL_VECTOR_TOKEN_FAKE_TARGET")).thenReturn(true);
        assertThat(service.build("dep-fake", draft).secrets()).filteredOn(item -> "MILVUS_TOKEN".equals(item.secretPurpose()))
            .singleElement().satisfies(item -> assertThat(item.present()).isFalse());
        org.mockito.Mockito.verify(secrets, org.mockito.Mockito.never()).resolveSecret(org.mockito.ArgumentMatchers.anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"TOKEN", "USERNAME_PASSWORD"})
    void unchangedDraftUsesLocalBindingForExactPublishedVersionAndTarget(String authType) throws Exception {
        var fixture = new BindingFixture(authType);
        String unchangedDraft = fixture.mapper.writeValueAsString(fixture.draft);
        var summary = fixture.scopedSummary();

        assertBinding(summary, authType, true);
        assertThat(summary.missingRequiredCount()).isZero();
        assertThat(summary.secrets()).filteredOn(item -> item.secretName().equals(fixture.purpose))
            .singleElement().satisfies(item -> {
                assertThat(item.summaryMessage()).contains("target target-staging", "published version ver-fake", "Provider health has not been checked");
                assertThat(item.effectiveResolution()).isNull();
            });
        assertThat(fixture.mapper.writeValueAsString(fixture.draft)).isEqualTo(unchangedDraft);
        assertThat(fixture.draft.getProviderConfigJson()).doesNotContain("RuntimeTokenSecretName", "RuntimeUsernameSecretName", "RuntimePasswordSecretName");
        assertThat(fixture.version.getSourceDraftId()).isNotEqualTo(fixture.draft.getId());
        verify(fixture.bindings).resolveLocal(fixture.deployment, fixture.version,
            fixture.mapper.readTree(fixture.version.getProviderConfigJson()), "target-staging");
        fixture.verifyNoLiveOrFallbackReads();
    }

    @ParameterizedTest
    @ValueSource(strings = {"TOKEN", "USERNAME_PASSWORD"})
    void unscopedSummaryCannotInferBindingFromDraftReferencesOrGlobalCredentials(String authType) {
        var fixture = new BindingFixture(authType);
        var summary = fixture.service.build(fixture.deployment.getId(), fixture.draft);
        assertBinding(summary, authType, false);
        assertThat(summary.secrets()).filteredOn(item -> item.secretName().equals(fixture.purpose))
            .singleElement().satisfies(item -> assertThat(item.summaryMessage()).contains("explicit published version and target"));
        verifyNoInteractions(fixture.bindings);
        fixture.verifyNoLiveOrFallbackReads();
    }

    @ParameterizedTest
    @ValueSource(strings = {"TOKEN", "USERNAME_PASSWORD"})
    void explicitProductionTargetUsesItsOwnBindingAndDoesNotClaimBootstrapOrProviderHealth(String authType) {
        var fixture = new BindingFixture(authType);
        when(fixture.bindings.resolveLocal(eq(fixture.deployment), eq(fixture.version), any(), eq("target-production")))
            .thenReturn(fixture.effectiveConfig);
        var summary = fixture.service.build(fixture.deployment, fixture.draft, fixture.version, "target-production");
        assertBinding(summary, authType, true);
        assertThat(summary.secrets()).filteredOn(item -> item.secretName().equals(fixture.purpose))
            .singleElement().satisfies(item -> assertThat(item.summaryMessage()).contains("target target-production", "Provider health has not been checked")
                .doesNotContain("target-staging"));
        verify(fixture.bindings).resolveLocal(eq(fixture.deployment), eq(fixture.version), any(), eq("target-production"));
        verify(fixture.bindings, never()).resolveLocal(eq(fixture.deployment), eq(fixture.version), any(), eq("target-staging"));
    }

    @ParameterizedTest
    @CsvSource({
        "TOKEN,EXTERNAL_VECTOR_BINDING_REQUIRED", "USERNAME_PASSWORD,EXTERNAL_VECTOR_BINDING_REQUIRED",
        "TOKEN,EXTERNAL_VECTOR_BINDING_CHANGED", "USERNAME_PASSWORD,EXTERNAL_VECTOR_BINDING_CHANGED",
        "TOKEN,EXTERNAL_VECTOR_BINDING_SCOPE_CHANGED", "USERNAME_PASSWORD,EXTERNAL_VECTOR_BINDING_SCOPE_CHANGED",
        "TOKEN,EXTERNAL_VECTOR_RUNTIME_CREDENTIALS_CHANGED", "USERNAME_PASSWORD,EXTERNAL_VECTOR_RUNTIME_CREDENTIALS_CHANGED"
    })
    void expectedLocalBindingDenialsAreMissingAndNeverExposeFailureDetails(String authType, String diagnostic) {
        var fixture = new BindingFixture(authType);
        when(fixture.bindings.resolveLocal(eq(fixture.deployment), eq(fixture.version), any(), eq("target-staging")))
            .thenThrow(new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, diagnostic + ": synthetic-private-detail"));
        var summary = fixture.scopedSummary();
        assertBinding(summary, authType, false);
        assertThat(summary.missingRequiredCount()).isEqualTo(1);
        assertThat(summary.toString()).doesNotContain("synthetic-private-detail", diagnostic);
        fixture.verifyNoLiveOrFallbackReads();
    }

    @ParameterizedTest
    @ValueSource(strings = {"TOKEN", "USERNAME_PASSWORD"})
    void wrongTargetCannotReuseWorkingStagingBinding(String authType) {
        var fixture = new BindingFixture(authType);
        when(fixture.bindings.resolveLocal(eq(fixture.deployment), eq(fixture.version), any(), eq("target-other")))
            .thenThrow(new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "EXTERNAL_VECTOR_BINDING_REQUIRED"));
        assertBinding(fixture.service.build(fixture.deployment, fixture.draft, fixture.version, "target-other"), authType, false);
        verify(fixture.bindings, never()).resolveLocal(eq(fixture.deployment), eq(fixture.version), any(), eq("target-staging"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"TOKEN", "USERNAME_PASSWORD"})
    void changedDraftOrWrongVersionScopeCannotReuseLocalEvidence(String authType) {
        var fixture = new BindingFixture(authType);
        fixture.draft.setEntityConfigJson("{\"ai-entities\":{\"changed\":{}}}");
        assertBinding(fixture.scopedSummary(), authType, false);
        fixture.draft.setEntityConfigJson(fixture.version.getEntityConfigJson());
        fixture.version.setDeploymentId("dep-other");
        assertBinding(fixture.scopedSummary(), authType, false);
        verifyNoInteractions(fixture.bindings);
    }

    @Test
    void programmingFailuresAndUnexpectedHttpFailuresAreNotHiddenAsMissingCredentials() {
        var fixture = new BindingFixture("TOKEN");
        when(fixture.bindings.resolveLocal(eq(fixture.deployment), eq(fixture.version), any(), eq("target-staging")))
            .thenThrow(new IllegalStateException("synthetic-programming-failure"));
        assertThatThrownBy(fixture::scopedSummary).isInstanceOf(IllegalStateException.class).hasMessage("synthetic-programming-failure");
        org.mockito.Mockito.doThrow(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "synthetic-unexpected-failure"))
            .when(fixture.bindings).resolveLocal(eq(fixture.deployment), eq(fixture.version), any(), eq("target-staging"));
        assertThatThrownBy(fixture::scopedSummary).isInstanceOf(ResponseStatusException.class).hasMessageContaining("synthetic-unexpected-failure");
    }

    @Test
    void reportsReferencedManagedIntegrationSecretAsPresent() {
        PlatformSecretService secretService = mock(PlatformSecretService.class);
        when(secretService.listSecrets()).thenReturn(List.of());
        when(secretService.isManagedSecretName("MANAGED_CUSTOMER_BACKEND_API_KEY")).thenReturn(true);
        when(secretService.describeSecret("MANAGED_CUSTOMER_BACKEND_API_KEY")).thenReturn(new PlatformSecretSummary(
            "MANAGED_CUSTOMER_BACKEND_API_KEY",
            "Customer backend API key",
            "Deployment-managed integration credential.",
            false,
            true,
            "DATABASE",
            "2026-09-30T00:00:00Z",
            "DEPLOYMENT_MANAGED",
            "PLATFORM_MANAGED",
            null,
            "dep-test",
            true,
            "DELETE_WITH_DEPLOYMENT"
        ));

        DeploymentSecretUsageService service = new DeploymentSecretUsageService(
            secretService,
            mock(DeploymentProviderSecretResolutionService.class),
            mock(DeploymentSecretPurposeCatalog.class),
            new PlatformDeliveryProperties("https://platform.example", false, Duration.ofDays(1)),
            new ObjectMapper(),
            mock(DeploymentExistingVectorBindingService.class)
        );
        DeploymentDraftEntity draft = new DeploymentDraftEntity();
        draft.setProviderConfigJson("{}");
        draft.setSecurityConfigJson("{\"adminApiKeyEnabled\":false,\"connectorApiKeyEnabled\":true}");
        draft.setMarketplaceDatasetConfigJson(DeploymentDraftEntity.DEFAULT_MARKETPLACE_DATASET_CONFIG_JSON);
        draft.setRoutingConfigJson("""
            {
              "connector": {
                "inbound-auth": {
                  "api-key": {"value": "${CONNECTOR_API_KEY}"}
                },
                "upstream": {
                  "auth": {"value": "${MANAGED_CUSTOMER_BACKEND_API_KEY}"}
                }
              }
            }
            """);

        var result = service.build("dep-test", draft);

        assertThat(result.literalRiskCount()).isZero();
        assertThat(result.secrets())
            .filteredOn(item -> item.secretName().equals("MANAGED_CUSTOMER_BACKEND_API_KEY"))
            .singleElement()
            .satisfies(item -> {
                assertThat(item.required()).isTrue();
                assertThat(item.present()).isTrue();
                assertThat(item.status()).isEqualTo("READY");
                assertThat(item.source()).isEqualTo("DATABASE");
            });
    }

    private void assertBinding(DeploymentSecretUsageSummary summary, String authType, boolean present) {
        String purpose = "TOKEN".equals(authType) ? "MILVUS_TOKEN" : "MILVUS_RUNTIME_CREDENTIALS";
        assertThat(summary.secrets()).filteredOn(item -> purpose.equals(item.secretPurpose())).singleElement().satisfies(item -> {
            assertThat(item.required()).isTrue();
            assertThat(item.present()).isEqualTo(present);
            assertThat(item.status()).isEqualTo(present ? "READY" : "MISSING");
            assertThat(item.source()).isEqualTo("DEPLOYMENT_MANAGED");
        });
    }

    private static final class BindingFixture {
        private final ObjectMapper mapper = new ObjectMapper();
        private final PlatformSecretService secrets = mock(PlatformSecretService.class);
        private final DeploymentProviderSecretResolutionService resolutions = mock(DeploymentProviderSecretResolutionService.class);
        private final DeploymentExistingVectorBindingService bindings = mock(DeploymentExistingVectorBindingService.class);
        private final DeploymentEntity deployment = new DeploymentEntity();
        private final DeploymentDraftEntity draft = new DeploymentDraftEntity();
        private final DeploymentVersionEntity version = new DeploymentVersionEntity();
        private final ObjectNode effectiveConfig;
        private final String purpose;
        private final DeploymentSecretUsageService service;

        private BindingFixture(String authType) {
            purpose = "TOKEN".equals(authType) ? "MILVUS_TOKEN" : "MILVUS_RUNTIME_CREDENTIALS";
            deployment.setId("dep-fake"); deployment.setCustomerId("customer-fake"); deployment.setTenantId("tenant-fake");
            draft.setId("draft-current"); draft.setDeploymentId(deployment.getId());
            draft.setProviderConfigJson(mapper.createObjectNode().put("vectorStrategy", "milvus")
                .put("vectorProvisioningMode", "EXTERNAL_EXISTING").put("vectorStoragePosture", "SHARED")
                .put("milvusExternalBindingRequired", true).put("milvusAuthenticationType", authType).toString());
            draft.setSecurityConfigJson("{\"adminApiKeyEnabled\":false}");
            version.setId("ver-fake"); version.setDeploymentId(deployment.getId()); version.setSourceDraftId("draft-published");
            version.setStatus("PUBLISHED"); version.setConfigHash("hash-fake");
            version.setProviderConfigJson(draft.getProviderConfigJson()); version.setSecurityConfigJson(draft.getSecurityConfigJson());
            effectiveConfig = mapper.createObjectNode().put("vectorStrategy", "milvus")
                .put("vectorProvisioningMode", "EXTERNAL_EXISTING").put("vectorStoragePosture", "SHARED")
                .put("milvusExternalBindingRequired", true).put("milvusAuthenticationType", authType);
            if ("TOKEN".equals(authType)) effectiveConfig.put("milvusRuntimeTokenSecretName", "MANAGED_EXTERNAL_VECTOR_TOKEN_FAKE_SCOPE");
            else effectiveConfig.put("milvusRuntimeUsernameSecretName", "MANAGED_EXTERNAL_VECTOR_USERNAME_FAKE_SCOPE")
                .put("milvusRuntimePasswordSecretName", "MANAGED_EXTERNAL_VECTOR_PASSWORD_FAKE_SCOPE");
            when(bindings.resolveLocal(eq(deployment), eq(version), any(), eq("target-staging"))).thenReturn(effectiveConfig);
            when(secrets.listSecrets()).thenReturn(List.of(
                new PlatformSecretSummary("OPENAI_API_KEY", "OpenAI", "Synthetic metadata", true, true, "DATABASE", null,
                    "GLOBAL_PLATFORM", "PLATFORM", "OPENAI_API_KEY", null, false, "KEEP"),
                new PlatformSecretSummary(purpose, "Milvus", "Synthetic global metadata", false, true, "DATABASE", null,
                    "GLOBAL_PLATFORM", "PLATFORM", purpose, null, false, "KEEP")));
            service = new DeploymentSecretUsageService(secrets, resolutions, new DeploymentSecretPurposeCatalog(),
                new PlatformDeliveryProperties("https://platform.example", false, Duration.ofDays(1)), mapper, bindings);
        }

        private DeploymentSecretUsageSummary scopedSummary() {
            return service.build(deployment, draft, version, "target-staging");
        }

        private void verifyNoLiveOrFallbackReads() {
            verify(bindings, never()).resolve(any(), any(), any(), any());
            verify(resolutions, never()).summarize(deployment.getId(), purpose);
            verify(secrets, never()).resolveSecret(any());
            verify(secrets, never()).isSecretPresent(any());
        }
    }
}
