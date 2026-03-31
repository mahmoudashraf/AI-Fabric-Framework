package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentPocPromptSessionEntity;
import com.ai.fabric.platform.backend.deployment.model.UpdateDeploymentPocPromptSessionRequest;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentPocPromptSessionRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeploymentPocPromptSessionServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void activateSessionStoresSanitizedPromptOverlay() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentPocPromptSessionRepository promptSessionRepository = mock(DeploymentPocPromptSessionRepository.class);
        DeploymentAccessService deploymentAccessService = mock(DeploymentAccessService.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        PlatformAuditService platformAuditService = mock(PlatformAuditService.class);

        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-123");
        deployment.setRuntimeBaseUrl("https://runtime.example.com");

        when(deploymentRepository.findById("dep-123")).thenReturn(Optional.of(deployment));
        when(deploymentAccessService.requireDeploymentAccess(deployment)).thenReturn(deployment);
        when(platformSecretService.resolveSecret("APP_ADMIN_API_KEY")).thenReturn("admin-secret");
        when(promptSessionRepository.findByDeploymentIdAndActorId("dep-123", "operator@example.com"))
            .thenReturn(Optional.empty());
        when(promptSessionRepository.save(org.mockito.ArgumentMatchers.any(DeploymentPocPromptSessionEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        DeploymentPocPromptSessionService service = new DeploymentPocPromptSessionService(
            deploymentRepository,
            promptSessionRepository,
            deploymentAccessService,
            platformSecretService,
            platformAuditService,
            objectMapper
        );
        authenticateOperator();

        ObjectNode promptPreview = objectMapper.createObjectNode();
        promptPreview.put("systemPrompt", "Use a friendly tone.");
        promptPreview.put("answerGenerationPrompt", "Use two bullets.");
        promptPreview.put("ignored", "ignore me");

        var summary = service.activateSession(
            "dep-123",
            new UpdateDeploymentPocPromptSessionRequest("Workshop", promptPreview)
        );

        assertThat(summary.active()).isTrue();
        assertThat(summary.promptKeyCount()).isEqualTo(2);
        assertThat(summary.promptKeys()).containsExactly("systemPrompt", "answerGenerationPrompt");
        assertThat(summary.sessionLabel()).isEqualTo("Workshop");

        ArgumentCaptor<DeploymentPocPromptSessionEntity> entityCaptor =
            ArgumentCaptor.forClass(DeploymentPocPromptSessionEntity.class);
        verify(promptSessionRepository).save(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getActorId()).isEqualTo("operator@example.com");
        assertThat(entityCaptor.getValue().getActorDisplayName()).isEqualTo("Operator");
        assertThat(entityCaptor.getValue().getPromptPreviewJson()).contains("systemPrompt");
        assertThat(entityCaptor.getValue().getPromptPreviewJson()).doesNotContain("ignored");

        verify(platformAuditService).record(
            eq("DEPLOYMENT_POC_PROMPT_SESSION_ACTIVATED"),
            eq("DEPLOYMENT"),
            eq("dep-123"),
            anyMap()
        );
    }

    @Test
    void clearSessionRemovesStoredOverlayForCurrentActor() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentPocPromptSessionRepository promptSessionRepository = mock(DeploymentPocPromptSessionRepository.class);
        DeploymentAccessService deploymentAccessService = mock(DeploymentAccessService.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        PlatformAuditService platformAuditService = mock(PlatformAuditService.class);

        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-123");

        DeploymentPocPromptSessionEntity existing = new DeploymentPocPromptSessionEntity();
        existing.setId("pps-1234");
        existing.setDeploymentId("dep-123");
        existing.setActorId("operator@example.com");
        existing.setPromptPreviewJson("{\"systemPrompt\":\"x\"}");
        existing.setCreatedAt(Instant.parse("2026-03-31T09:00:00Z"));
        existing.setUpdatedAt(Instant.parse("2026-03-31T09:05:00Z"));

        when(deploymentRepository.findById("dep-123")).thenReturn(Optional.of(deployment));
        when(deploymentAccessService.requireDeploymentAccess(deployment)).thenReturn(deployment);
        when(promptSessionRepository.findByDeploymentIdAndActorId("dep-123", "operator@example.com"))
            .thenReturn(Optional.of(existing));

        DeploymentPocPromptSessionService service = new DeploymentPocPromptSessionService(
            deploymentRepository,
            promptSessionRepository,
            deploymentAccessService,
            platformSecretService,
            platformAuditService,
            objectMapper
        );
        authenticateOperator();

        service.clearSession("dep-123");

        verify(promptSessionRepository).delete(existing);
        verify(platformAuditService).record(
            eq("DEPLOYMENT_POC_PROMPT_SESSION_CLEARED"),
            eq("DEPLOYMENT"),
            eq("dep-123"),
            anyMap()
        );
    }

    private void authenticateOperator() {
        PlatformPrincipal principal = new PlatformPrincipal(
            "operator@example.com",
            PlatformRole.PLATFORM_OPERATOR,
            "Operator",
            "SESSION"
        );
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            principal,
            null,
            List.of(new SimpleGrantedAuthority(principal.role().authority()))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
