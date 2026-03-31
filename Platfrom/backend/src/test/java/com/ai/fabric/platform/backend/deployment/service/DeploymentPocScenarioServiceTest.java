package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentPocScenarioEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.UpsertDeploymentPocScenarioRequest;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentDraftRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentPocScenarioRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeploymentPocScenarioServiceTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listScenariosIncludesBuiltInAndCustomEntries() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentDraftRepository deploymentDraftRepository = mock(DeploymentDraftRepository.class);
        DeploymentVersionRepository deploymentVersionRepository = mock(DeploymentVersionRepository.class);
        DeploymentPocScenarioRepository deploymentPocScenarioRepository = mock(DeploymentPocScenarioRepository.class);
        DeploymentAccessService deploymentAccessService = mock(DeploymentAccessService.class);
        PlatformAuditService platformAuditService = mock(PlatformAuditService.class);

        DeploymentEntity deployment = deployment();
        DeploymentVersionEntity version = new DeploymentVersionEntity();
        version.setId("ver-123");
        version.setEntityConfigJson(
            """
                {
                  "ai-entities": {
                    "product": {},
                    "review": {},
                    "policy": {}
                  }
                }
                """
        );
        DeploymentPocScenarioEntity customScenario = new DeploymentPocScenarioEntity();
        customScenario.setId("psc-custom");
        customScenario.setDeploymentId("dep-123");
        customScenario.setTitle("Custom smoke");
        customScenario.setCategory("Custom");
        customScenario.setPromptText("Run my custom smoke test.");
        customScenario.setExpectedOutcome("Assistant follows the custom path.");
        customScenario.setCreatedAt(Instant.parse("2026-03-31T03:00:00Z"));

        when(deploymentRepository.findById("dep-123")).thenReturn(Optional.of(deployment));
        when(deploymentVersionRepository.findById("ver-123")).thenReturn(Optional.of(version));
        when(deploymentAccessService.requireDeploymentAccess(deployment)).thenReturn(deployment);
        when(deploymentPocScenarioRepository.findByDeploymentIdOrderByCreatedAtAsc("dep-123"))
            .thenReturn(List.of(customScenario));

        DeploymentPocScenarioService service = new DeploymentPocScenarioService(
            deploymentRepository,
            deploymentDraftRepository,
            deploymentVersionRepository,
            deploymentPocScenarioRepository,
            deploymentAccessService,
            platformAuditService,
            new ObjectMapper()
        );

        var scenarios = service.listScenarios("dep-123");

        assertThat(scenarios).extracting(item -> item.source()).contains("builtin", "custom");
        assertThat(scenarios).extracting(item -> item.title())
            .contains("Grounded catalog summary", "Custom smoke");
    }

    @Test
    void createUpdateAndDeleteScenarioPersistAndAudit() {
        authenticateOperator();

        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentDraftRepository deploymentDraftRepository = mock(DeploymentDraftRepository.class);
        DeploymentVersionRepository deploymentVersionRepository = mock(DeploymentVersionRepository.class);
        DeploymentPocScenarioRepository deploymentPocScenarioRepository = mock(DeploymentPocScenarioRepository.class);
        DeploymentAccessService deploymentAccessService = mock(DeploymentAccessService.class);
        PlatformAuditService platformAuditService = mock(PlatformAuditService.class);

        DeploymentEntity deployment = deployment();
        DeploymentPocScenarioEntity existing = new DeploymentPocScenarioEntity();
        existing.setId("psc-123");
        existing.setDeploymentId("dep-123");
        existing.setTitle("Existing");
        existing.setCategory("Custom");
        existing.setPromptText("Before");
        existing.setCreatedAt(Instant.parse("2026-03-31T03:00:00Z"));
        existing.setUpdatedAt(Instant.parse("2026-03-31T03:00:00Z"));

        when(deploymentRepository.findById("dep-123")).thenReturn(Optional.of(deployment));
        when(deploymentAccessService.requireDeploymentAccess(deployment)).thenReturn(deployment);
        when(deploymentPocScenarioRepository.findById("psc-123")).thenReturn(Optional.of(existing));
        when(deploymentPocScenarioRepository.save(any(DeploymentPocScenarioEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        DeploymentPocScenarioService service = new DeploymentPocScenarioService(
            deploymentRepository,
            deploymentDraftRepository,
            deploymentVersionRepository,
            deploymentPocScenarioRepository,
            deploymentAccessService,
            platformAuditService,
            new ObjectMapper()
        );

        var created = service.createScenario(
            "dep-123",
            new UpsertDeploymentPocScenarioRequest("Scenario title", "Custom", "Prompt text", "Expected")
        );
        assertThat(created.title()).isEqualTo("Scenario title");
        assertThat(created.editable()).isTrue();

        var updated = service.updateScenario(
            "dep-123",
            "psc-123",
            new UpsertDeploymentPocScenarioRequest("Updated title", "Validation", "Updated prompt", "Updated outcome")
        );
        assertThat(updated.title()).isEqualTo("Updated title");
        assertThat(updated.category()).isEqualTo("Validation");

        service.deleteScenario("dep-123", "psc-123");

        ArgumentCaptor<DeploymentPocScenarioEntity> captor = ArgumentCaptor.forClass(DeploymentPocScenarioEntity.class);
        verify(deploymentPocScenarioRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        verify(deploymentPocScenarioRepository).delete(existing);
        verify(platformAuditService).record(eq("DEPLOYMENT_POC_SCENARIO_CREATED"), eq("DEPLOYMENT"), eq("dep-123"), anyMap());
        verify(platformAuditService).record(eq("DEPLOYMENT_POC_SCENARIO_UPDATED"), eq("DEPLOYMENT"), eq("dep-123"), anyMap());
        verify(platformAuditService).record(eq("DEPLOYMENT_POC_SCENARIO_DELETED"), eq("DEPLOYMENT"), eq("dep-123"), anyMap());
        assertThat(captor.getAllValues()).hasSize(2);
    }

    private DeploymentEntity deployment() {
        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-123");
        deployment.setActiveVersionId("ver-123");
        return deployment;
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
