package com.ai.fabric.platform.backend.secret.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.secret.entity.PlatformSecretEntity;
import com.ai.fabric.platform.backend.secret.model.PlatformSecretSummary;
import com.ai.fabric.platform.backend.secret.repository.PlatformSecretRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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
}
