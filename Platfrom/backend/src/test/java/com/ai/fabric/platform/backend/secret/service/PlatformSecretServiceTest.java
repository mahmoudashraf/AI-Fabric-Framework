package com.ai.fabric.platform.backend.secret.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.secret.entity.PlatformSecretEntity;
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

class PlatformSecretServiceTest {

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
        PlatformSecretService service = new PlatformSecretService(repository, mock(PlatformAuditService.class), new MockEnvironment());

        assertThat(service.listSecrets())
            .extracting(PlatformSecretSummary::name)
            .contains("SHOPIFY_APP_API_KEY", "SHOPIFY_APP_API_SECRET", "SHOPIFY_WEBHOOK_SHARED_SECRET");
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
