package com.ai.fabric.platform.backend.secret.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.secret.entity.PlatformSecretEntity;
import com.ai.fabric.platform.backend.secret.entity.PlatformSecretScopeType;
import com.ai.fabric.platform.backend.secret.entity.PlatformSecretOwnerType;
import com.ai.fabric.platform.backend.secret.model.PlatformSecretSummary;
import com.ai.fabric.platform.backend.secret.repository.PlatformSecretRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class PlatformSecretServiceTest {

    @Test
    void targetManagedSecretPersistsExactOwnerAndNeverReportsTheValue() {
        var repository = mock(PlatformSecretRepository.class);
        var audit = mock(PlatformAuditService.class);
        var service = new PlatformSecretService(repository, audit, new MockEnvironment());
        var summary = service.upsertTargetManagedSecret("MANAGED_SUPPORT_DIAGNOSTICS_STAGE", "test-only-support-key",
            "dep-support", "target-stage", Map.of("source", "test"));
        var saved = org.mockito.ArgumentCaptor.forClass(PlatformSecretEntity.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getDeploymentId()).isEqualTo("dep-support");
        assertThat(saved.getValue().getTargetProfileId()).isEqualTo("target-stage");
        assertThat(saved.getValue().getScopeType()).isEqualTo(PlatformSecretScopeType.DEPLOYMENT_MANAGED);
        assertThat(summary.targetProfileId()).isEqualTo("target-stage");
        assertThat(summary.toString()).doesNotContain("test-only-support-key");
        verify(audit).record("MANAGED_SECRET_UPDATED", "PLATFORM_SECRET", "MANAGED_SUPPORT_DIAGNOSTICS_STAGE",
            Map.of("source", "test", "deploymentId", "dep-support", "targetProfileId", "target-stage"));
    }

    @Test
    void targetManagedSecretDeniesForeignDeploymentWrongOrMissingTargetBeforeReturningItsValue() {
        var repository = mock(PlatformSecretRepository.class);
        var entity = targetSecret();
        when(repository.findById(entity.getName())).thenReturn(Optional.of(entity));
        var service = new PlatformSecretService(repository, mock(PlatformAuditService.class), new MockEnvironment());
        assertThat(service.resolveSecretForDeployment(entity.getName(), "dep-support", "target-stage")).isEqualTo("test-only-support-key");
        for (String[] scope : new String[][]{{"dep-other", "target-stage"}, {"dep-support", "target-production"},
            {"dep-support", null}, {null, "target-stage"}}) {
            assertThatThrownBy(() -> service.resolveSecretForDeployment(entity.getName(), scope[0], scope[1]))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("SECRET_MATERIALIZATION_SCOPE_MISMATCH")
                .hasMessageNotContaining("test-only-support-key");
        }
    }

    @Test
    void scopedResolutionCannotRereadANewlyBoundCredentialThroughTheGlobalResolver() {
        var repository = mock(PlatformSecretRepository.class);
        var entity = targetSecret();
        when(repository.findById(entity.getName())).thenReturn(Optional.empty(), Optional.of(entity));
        var service = new PlatformSecretService(repository, mock(PlatformAuditService.class), new MockEnvironment());

        assertThat(service.resolveSecretForDeployment(entity.getName(), "dep-other", "target-stage")).isNull();
        verify(repository).findById(entity.getName());
    }

    @Test
    void deploymentOnlyManagedBindingsAndDeliberateSharedOpenaiFallbackRemainAvailable() {
        var repository = mock(PlatformSecretRepository.class);
        var entity = targetSecret();
        entity.setTargetProfileId(null);
        when(repository.findById(entity.getName())).thenReturn(Optional.of(entity));
        var service = new PlatformSecretService(repository, mock(PlatformAuditService.class),
            new MockEnvironment().withProperty("OPENAI_API_KEY", "shared-provider-test-key"));
        assertThat(service.resolveSecretForDeployment(entity.getName(), "dep-support", "target-production")).isEqualTo("test-only-support-key");
        assertThat(service.resolveSecretForDeployment("OPENAI_API_KEY", "dep-other", "target-stage")).isEqualTo("shared-provider-test-key");
        assertThatThrownBy(() -> service.resolveSecretForDeployment(entity.getName(), "dep-other", "target-stage"))
            .hasMessageContaining("SECRET_MATERIALIZATION_SCOPE_MISMATCH");
    }

    @Test
    void scopedMissingValueCannotFallBackToAnUnscopedEnvironmentValue() {
        var repository = mock(PlatformSecretRepository.class);
        var entity = targetSecret();
        entity.setName("OPENAI_API_KEY");
        entity.setSecretValue("");
        when(repository.findById(entity.getName())).thenReturn(Optional.of(entity));
        var service = new PlatformSecretService(repository, mock(PlatformAuditService.class),
            new MockEnvironment().withProperty("OPENAI_API_KEY", "must-not-be-used"));
        assertThat(service.resolveSecretForDeployment(entity.getName(), "dep-support", "target-stage")).isNull();
    }

    @Test
    void rotationPreservesTargetBindingAndCannotReassignItsOwner() {
        var repository = mock(PlatformSecretRepository.class);
        var entity = targetSecret();
        when(repository.findById(entity.getName())).thenReturn(Optional.of(entity));
        var service = new PlatformSecretService(repository, mock(PlatformAuditService.class), new MockEnvironment());
        service.upsertManagedSecret(entity.getName(), "rotated-test-key", Map.of("source", "admin-rotation"));
        assertThat(entity.getDeploymentId()).isEqualTo("dep-support");
        assertThat(entity.getTargetProfileId()).isEqualTo("target-stage");
        assertThatThrownBy(() -> service.upsertTargetManagedSecret(entity.getName(), "other", "dep-other", "target-stage", Map.of()))
            .hasMessageContaining("ownership cannot be reassigned");
        assertThatThrownBy(() -> service.upsertTargetManagedSecret(entity.getName(), "other", "dep-support", "target-production", Map.of()))
            .hasMessageContaining("ownership cannot be reassigned");
        assertThatThrownBy(() -> service.upsertManagedSecret(entity.getName(), "other",
            Map.of("deploymentId", "dep-support", "targetProfileId", "target-production")))
            .hasMessageContaining("ownership cannot be reassigned");
        assertThat(entity.getSecretValue()).isEqualTo("rotated-test-key");
    }

    @Test
    void explicitTargetStorageRequiresBothCoordinatesAndValidManagedOwnership() {
        var repository = mock(PlatformSecretRepository.class);
        var service = new PlatformSecretService(repository, mock(PlatformAuditService.class), new MockEnvironment());
        assertThatThrownBy(() -> service.upsertTargetManagedSecret("MANAGED_SUPPORT", "value", "dep-support", " ", Map.of()))
            .hasMessageContaining("require deploymentId and targetProfileId");
        assertThatThrownBy(() -> service.upsertTargetManagedSecret("MANAGED_SUPPORT", "value", null, "target-stage", Map.of()))
            .hasMessageContaining("require deploymentId and targetProfileId");
        var entity = targetSecret();
        entity.setOwnerType(PlatformSecretOwnerType.PLATFORM);
        when(repository.findById(entity.getName())).thenReturn(Optional.of(entity));
        assertThatThrownBy(() -> service.resolveSecretForDeployment(entity.getName(), "dep-support", "target-stage"))
            .hasMessageContaining("SECRET_MATERIALIZATION_SCOPE_MISMATCH");
    }

    private PlatformSecretEntity targetSecret() {
        var entity = new PlatformSecretEntity();
        entity.setName("MANAGED_SUPPORT_DIAGNOSTICS_STAGE");
        entity.setSecretValue("test-only-support-key");
        entity.setDeploymentId("dep-support");
        entity.setTargetProfileId("target-stage");
        entity.setScopeType(PlatformSecretScopeType.DEPLOYMENT_MANAGED);
        entity.setOwnerType(PlatformSecretOwnerType.PLATFORM_MANAGED);
        entity.setManagedByPlatform(true);
        return entity;
    }

    @Test
    void milvusTokenMetadataAndAuditNeverIncludeRawCredential() {
        var repository = mock(PlatformSecretRepository.class);
        var audit = mock(PlatformAuditService.class);
        var service = new PlatformSecretService(repository, audit, new MockEnvironment());
        String ref = "MANAGED_EXTERNAL_VECTOR_TOKEN_FAKE_TARGET";
        when(repository.findById(ref)).thenReturn(Optional.empty());
        var ownership = Map.of("deploymentId", "dep-fake", "targetProfileId", "target-fake");
        var summary = service.upsertManagedSecret(ref, "fake-native-token-never-report", ownership);
        assertThat(summary.secretPurpose()).isEqualTo("MILVUS_TOKEN");
        assertThat(summary.toString()).doesNotContain("fake-native-token-never-report");
        org.mockito.Mockito.verify(audit).record("MANAGED_SECRET_UPDATED", "PLATFORM_SECRET", ref, ownership);
        assertThat(service.isSupportedSecretName("MILVUS_TOKEN")).isTrue();
        assertThat(service.requiredSecretNames()).doesNotContain("MILVUS_TOKEN");
    }

    @Test
    void resolveSecretPrefersDatabaseValueOverEnvironmentFallback() {
        PlatformSecretRepository repository = mock(PlatformSecretRepository.class);
        PlatformSecretEntity entity = new PlatformSecretEntity();
        entity.setName("OPENAI_API_KEY");
        entity.setSecretValue("db-value");
        entity.setUpdatedAt(Instant.parse("2026-03-29T10:00:00Z"));
        when(repository.findById("OPENAI_API_KEY")).thenReturn(Optional.of(entity));
        when(repository.findAll()).thenReturn(List.of(entity));
        MockEnvironment environment = new MockEnvironment().withProperty("OPENAI_API_KEY", "env-value");
        PlatformAuditService auditService = mock(PlatformAuditService.class);

        PlatformSecretService service = new PlatformSecretService(repository, auditService, environment);

        assertThat(service.resolveSecret("OPENAI_API_KEY")).isEqualTo("db-value");
        PlatformSecretSummary summary = service.listSecrets().stream()
            .filter(item -> "OPENAI_API_KEY".equals(item.name()))
            .findFirst()
            .orElseThrow();
        assertThat(summary.source()).isEqualTo("DATABASE");
        assertThat(summary.present()).isTrue();
    }

    @Test
    void resolveSecretFallsBackToEnvironmentWhenDatabaseIsMissing() {
        PlatformSecretRepository repository = mock(PlatformSecretRepository.class);
        when(repository.findById("CONNECTOR_API_KEY")).thenReturn(Optional.empty());
        when(repository.findAll()).thenReturn(List.of());
        MockEnvironment environment = new MockEnvironment().withProperty("CONNECTOR_API_KEY", "env-value");
        PlatformAuditService auditService = mock(PlatformAuditService.class);

        PlatformSecretService service = new PlatformSecretService(repository, auditService, environment);

        assertThat(service.resolveSecret("CONNECTOR_API_KEY")).isEqualTo("env-value");
        PlatformSecretSummary summary = service.listSecrets().stream()
            .filter(item -> "CONNECTOR_API_KEY".equals(item.name()))
            .findFirst()
            .orElseThrow();
        assertThat(summary.source()).isEqualTo("ENV");
        assertThat(summary.present()).isTrue();
    }

    @Test
    void updateSecretReturnsDatabaseBackedSummary() {
        PlatformSecretRepository repository = mock(PlatformSecretRepository.class);
        when(repository.findById("ACTIONS_CONNECTOR_API_KEY")).thenReturn(Optional.empty());
        when(repository.save(any(PlatformSecretEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.findAll()).thenReturn(List.of());
        PlatformSecretService service = new PlatformSecretService(repository, mock(PlatformAuditService.class), new MockEnvironment());

        PlatformSecretSummary summary = service.updateSecret("ACTIONS_CONNECTOR_API_KEY", "secret-value");

        assertThat(summary.source()).isEqualTo("DATABASE");
        assertThat(summary.present()).isTrue();
    }

    @Test
    void listSecretsIncludesShopifyBridgeCredentialSecrets() {
        PlatformSecretRepository repository = mock(PlatformSecretRepository.class);
        when(repository.findAll()).thenReturn(List.of());
        when(repository.findById("SHOPIFY_APP_API_KEY")).thenReturn(Optional.empty());
        when(repository.findById("SHOPIFY_APP_API_SECRET")).thenReturn(Optional.empty());
        when(repository.findById("SHOPIFY_WEBHOOK_SHARED_SECRET")).thenReturn(Optional.empty());
        when(repository.findById("SHOPIFY_BRIDGE_SHARED_SECRET")).thenReturn(Optional.empty());
        PlatformSecretService service = new PlatformSecretService(repository, mock(PlatformAuditService.class), new MockEnvironment());

        assertThat(service.listSecrets())
            .extracting(PlatformSecretSummary::name)
            .contains(
                "SHOPIFY_APP_API_KEY",
                "SHOPIFY_APP_API_SECRET",
                "SHOPIFY_WEBHOOK_SHARED_SECRET",
                "SHOPIFY_BRIDGE_SHARED_SECRET"
            );
    }

    @Test
    void updateSecretSupportsShopifyBridgeSharedSecretUsedByDeploymentProvisioning() {
        PlatformSecretRepository repository = mock(PlatformSecretRepository.class);
        when(repository.findById("SHOPIFY_BRIDGE_SHARED_SECRET")).thenReturn(Optional.empty());
        when(repository.save(any(PlatformSecretEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PlatformSecretService service = new PlatformSecretService(repository, mock(PlatformAuditService.class), new MockEnvironment());

        PlatformSecretSummary summary = service.updateSecret("SHOPIFY_BRIDGE_SHARED_SECRET", "bridge-secret");

        assertThat(summary.name()).isEqualTo("SHOPIFY_BRIDGE_SHARED_SECRET");
        assertThat(summary.source()).isEqualTo("DATABASE");
        assertThat(summary.present()).isTrue();
    }

    @Test
    void listSecretsIncludesPartnerVerificationJwt() {
        PlatformSecretRepository repository = mock(PlatformSecretRepository.class);
        when(repository.findAll()).thenReturn(List.of());
        when(repository.findById("PARTNER_SUPABASE_JWT")).thenReturn(Optional.empty());
        MockEnvironment environment = new MockEnvironment().withProperty("PARTNER_SUPABASE_JWT", "partner-jwt");
        PlatformSecretService service = new PlatformSecretService(repository, mock(PlatformAuditService.class), environment);

        assertThat(service.resolveSecret("PARTNER_SUPABASE_JWT")).isEqualTo("partner-jwt");
        assertThat(service.listSecrets())
            .extracting(PlatformSecretSummary::name)
            .contains("PARTNER_SUPABASE_JWT");
    }

    @Test
    void listSecretsIncludesCoolifyProviderTokens() {
        PlatformSecretRepository repository = mock(PlatformSecretRepository.class);
        when(repository.findAll()).thenReturn(List.of());
        when(repository.findById("COOLIFY_STAGING_API_TOKEN")).thenReturn(Optional.empty());
        MockEnvironment environment = new MockEnvironment().withProperty("COOLIFY_STAGING_API_TOKEN", "coolify-token");
        PlatformSecretService service = new PlatformSecretService(repository, mock(PlatformAuditService.class), environment);

        assertThat(service.resolveSecret("COOLIFY_STAGING_API_TOKEN")).isEqualTo("coolify-token");
        assertThat(service.listSecrets())
            .extracting(PlatformSecretSummary::name)
            .contains("COOLIFY_STAGING_API_TOKEN", "COOLIFY_PRODUCTION_API_TOKEN");
    }

    @Test
    void upsertManagedSecretReturnsDeploymentManagedSummary() {
        PlatformSecretRepository repository = mock(PlatformSecretRepository.class);
        when(repository.findById("MANAGED_PRODUS_SAFE_KNOWLEDGE_EXPORT_TOKEN_DEP_DEP_7706FAFB")).thenReturn(Optional.empty());
        when(repository.save(any(PlatformSecretEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PlatformSecretService service = new PlatformSecretService(repository, mock(PlatformAuditService.class), new MockEnvironment());

        PlatformSecretSummary summary = service.upsertManagedSecret(
            "MANAGED_PRODUS_SAFE_KNOWLEDGE_EXPORT_TOKEN_DEP_DEP_7706FAFB",
            "secret-value",
            Map.of("deploymentId", "dep-7706fafb", "source", "test")
        );

        assertThat(summary.present()).isTrue();
        assertThat(summary.source()).isEqualTo("DATABASE");
        assertThat(summary.scopeType()).isEqualTo("DEPLOYMENT_MANAGED");
        assertThat(summary.ownerType()).isEqualTo("PLATFORM_MANAGED");
        assertThat(summary.deploymentId()).isEqualTo("dep-7706fafb");
        assertThat(summary.managedByPlatform()).isTrue();
    }

    @Test
    void describeSecretAllowsManagedNamesButRejectsArbitraryNames() {
        PlatformSecretRepository repository = mock(PlatformSecretRepository.class);
        when(repository.findById("MANAGED_PRODUS_SAFE_KNOWLEDGE_EXPORT_TOKEN_DEP_DEP_7706FAFB")).thenReturn(Optional.empty());
        PlatformSecretService service = new PlatformSecretService(repository, mock(PlatformAuditService.class), new MockEnvironment());

        PlatformSecretSummary summary = service.describeSecret("MANAGED_PRODUS_SAFE_KNOWLEDGE_EXPORT_TOKEN_DEP_DEP_7706FAFB");

        assertThat(summary.present()).isFalse();
        assertThat(summary.required()).isFalse();
        assertThat(summary.scopeType()).isEqualTo("DEPLOYMENT_MANAGED");
        assertThatThrownBy(() -> service.describeSecret("SOURCE_API_KEY"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Unsupported platform secret");
    }
}
