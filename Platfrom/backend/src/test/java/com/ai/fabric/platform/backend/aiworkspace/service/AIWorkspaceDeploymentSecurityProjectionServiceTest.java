package com.ai.fabric.platform.backend.aiworkspace.service;

import com.ai.fabric.platform.backend.aiworkspace.entity.AIWorkspaceInstallationEntity;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceInstallationStatus;
import com.ai.fabric.platform.backend.aiworkspace.repository.AIWorkspaceInstallationRepository;
import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.PlatformAIWorkspaceProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentDraftEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentDraftRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AIWorkspaceDeploymentSecurityProjectionServiceTest {

    private final AIWorkspaceInstallationRepository installations = mock(AIWorkspaceInstallationRepository.class);
    private final PlatformConsumerRepository consumers = mock(PlatformConsumerRepository.class);
    private final DeploymentRepository deployments = mock(DeploymentRepository.class);
    private final DeploymentDraftRepository drafts = mock(DeploymentDraftRepository.class);
    private final PlatformAuditService audit = mock(PlatformAuditService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AIWorkspaceConfigurationValidator validator = new AIWorkspaceConfigurationValidator(
        new PlatformAIWorkspaceProperties(Path.of("."), false, 120, Duration.ofSeconds(60), null, null,
            "apps.loomai.test", "https://workspace.loomai.test"));

    @Test
    void replacesOnlyPreviouslyManagedOriginsAndProjectsAllCurrentAppsDeterministically() throws Exception {
        DeploymentEntity deployment = deployment();
        DeploymentDraftEntity draft = draft("""
            {
              "authzMode":"LOCAL",
              "adminApiKeyEnabled":true,
              "connectorApiKeyEnabled":true,
              "corsAllowedOrigins":"https://manual.example,https://stale.apps.example",
              "aiWorkspaceManagedCorsOrigins":["https://stale.apps.example"]
            }
            """);
        PlatformConsumerEntity consumerA = consumer("consumer-a");
        PlatformConsumerEntity consumerB = consumer("consumer-b");
        when(deployments.findByIdForUpdate("deployment-1")).thenReturn(Optional.of(deployment));
        when(drafts.findByIdForUpdate("draft-1")).thenReturn(Optional.of(draft));
        when(consumers.findByBoundDeploymentIdOrderByUpdatedAtDesc("deployment-1"))
            .thenReturn(List.of(consumerA, consumerB));
        when(installations.findByConsumerEntityIdInAndStatusIn(eq(List.of("consumer-a", "consumer-b")), any()))
            .thenReturn(List.of(
                installation("consumer-a", AIWorkspaceInstallationStatus.DRAFT, "[\"https://z.apps.example\"]"),
                installation("consumer-b", AIWorkspaceInstallationStatus.ACTIVE, "[\"https://a.apps.example\"]")
            ));

        service().synchronizeDeployment("deployment-1");

        var security = objectMapper.readTree(draft.getSecurityConfigJson());
        assertThat(security.path("corsAllowedOrigins").asText())
            .isEqualTo("https://manual.example,https://a.apps.example,https://z.apps.example");
        assertThat(security.path("aiWorkspaceManagedCorsOrigins")).hasToString(
            "[\"https://a.apps.example\",\"https://z.apps.example\"]");
        assertThat(draft.getStatus()).isEqualTo("MODIFIED");
        assertThat(deployment.getStatus()).isEqualTo("DRAFT");
        verify(drafts).save(draft);
        verify(deployments).save(deployment);
        verify(audit).record(eq("AI_WORKSPACE_DEPLOYMENT_ORIGINS_SYNCHRONIZED"),
            eq("DEPLOYMENT_DRAFT"), eq("draft-1"), any());
    }

    @Test
    void removesRetiredWorkspaceOriginsWithoutRemovingIndependentOrigins() throws Exception {
        DeploymentEntity deployment = deployment();
        DeploymentDraftEntity draft = draft("""
            {
              "authzMode":"LOCAL",
              "adminApiKeyEnabled":true,
              "connectorApiKeyEnabled":true,
              "corsAllowedOrigins":"https://manual.example,https://retired.apps.example",
              "aiWorkspaceManagedCorsOrigins":["https://retired.apps.example"]
            }
            """);
        when(deployments.findByIdForUpdate("deployment-1")).thenReturn(Optional.of(deployment));
        when(drafts.findByIdForUpdate("draft-1")).thenReturn(Optional.of(draft));
        when(consumers.findByBoundDeploymentIdOrderByUpdatedAtDesc("deployment-1")).thenReturn(List.of());

        service().synchronizeDeployment("deployment-1");

        var security = objectMapper.readTree(draft.getSecurityConfigJson());
        assertThat(security.path("corsAllowedOrigins").asText()).isEqualTo("https://manual.example");
        assertThat(security.path("aiWorkspaceManagedCorsOrigins").isArray()).isTrue();
        assertThat(security.path("aiWorkspaceManagedCorsOrigins")).isEmpty();
    }

    @Test
    void malformedProjectionMetadataFailsClosedBeforeDraftMutation() {
        DeploymentEntity deployment = deployment();
        DeploymentDraftEntity draft = draft("""
            {"corsAllowedOrigins":"https://app.example","aiWorkspaceManagedCorsOrigins":"https://app.example"}
            """);
        when(deployments.findByIdForUpdate("deployment-1")).thenReturn(Optional.of(deployment));
        when(drafts.findByIdForUpdate("draft-1")).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service().synchronizeDeployment("deployment-1"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("projection metadata requires reconciliation");
        verify(drafts, never()).save(any());
        verify(deployments, never()).save(any());
    }

    @Test
    void consumerMustBeBoundBeforeWorkspaceConfigurationCanProjectAuthority() {
        PlatformConsumerEntity consumer = new PlatformConsumerEntity();
        consumer.setId("consumer-a");

        assertThatThrownBy(() -> service().synchronizeForConsumer(consumer))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("deployment-bound consumer");
    }

    @Test
    void deploymentAuthorityLocksUseDeterministicOrderBeforeOfferAuthorization() {
        PlatformConsumerEntity consumerZ = consumer("consumer-z");
        consumerZ.setBoundDeploymentId("deployment-z");
        PlatformConsumerEntity consumerA = consumer("consumer-a");
        consumerA.setBoundDeploymentId("deployment-a");
        when(deployments.findByIdForUpdate("deployment-a"))
            .thenReturn(Optional.of(deployment("deployment-a", "draft-a")));
        when(deployments.findByIdForUpdate("deployment-z"))
            .thenReturn(Optional.of(deployment("deployment-z", "draft-z")));

        service().lockDeploymentsForConsumers(List.of(consumerZ, consumerA));

        var order = inOrder(deployments);
        order.verify(deployments).findByIdForUpdate("deployment-a");
        order.verify(deployments).findByIdForUpdate("deployment-z");
    }

    @Test
    void originThatAlreadyBelongsToManualConfigurationIsNeverClaimedByProjection() throws Exception {
        DeploymentEntity deployment = deployment();
        DeploymentDraftEntity draft = draft("""
            {"corsAllowedOrigins":"https://shared.example"}
            """);
        PlatformConsumerEntity consumer = consumer("consumer-a");
        when(deployments.findByIdForUpdate("deployment-1")).thenReturn(Optional.of(deployment));
        when(drafts.findByIdForUpdate("draft-1")).thenReturn(Optional.of(draft));
        when(consumers.findByBoundDeploymentIdOrderByUpdatedAtDesc("deployment-1"))
            .thenReturn(List.of(consumer));
        when(installations.findByConsumerEntityIdInAndStatusIn(eq(List.of("consumer-a")), any()))
            .thenReturn(List.of(installation("consumer-a", AIWorkspaceInstallationStatus.DRAFT,
                "[\"https://shared.example\"]")));

        service().synchronizeDeployment("deployment-1");
        var withInstallation = objectMapper.readTree(draft.getSecurityConfigJson());
        assertThat(withInstallation.path("corsAllowedOrigins").asText()).isEqualTo("https://shared.example");
        assertThat(withInstallation.path("aiWorkspaceManagedCorsOrigins")).isEmpty();

        when(installations.findByConsumerEntityIdInAndStatusIn(eq(List.of("consumer-a")), any()))
            .thenReturn(List.of());
        service().synchronizeDeployment("deployment-1");

        var withoutInstallation = objectMapper.readTree(draft.getSecurityConfigJson());
        assertThat(withoutInstallation.path("corsAllowedOrigins").asText()).isEqualTo("https://shared.example");
        assertThat(withoutInstallation.path("aiWorkspaceManagedCorsOrigins")).isEmpty();
    }

    @Test
    void bindingChangeSynchronizesOldAndNewDeploymentsOnlyWhenWorkspaceIsCurrent() {
        when(installations.findByConsumerEntityIdAndStatusIn("consumer-a",
            List.of(AIWorkspaceInstallationStatus.DRAFT, AIWorkspaceInstallationStatus.ACTIVE)))
            .thenReturn(List.of(installation("consumer-a", AIWorkspaceInstallationStatus.DRAFT,
                "[\"https://app.example\"]")));
        DeploymentEntity oldDeployment = deployment("deployment-old", "draft-old");
        DeploymentEntity newDeployment = deployment("deployment-new", "draft-new");
        when(deployments.findByIdForUpdate("deployment-old")).thenReturn(Optional.of(oldDeployment));
        when(deployments.findByIdForUpdate("deployment-new")).thenReturn(Optional.of(newDeployment));
        when(drafts.findByIdForUpdate("draft-old"))
            .thenReturn(Optional.of(draft("draft-old", "deployment-old", "{}")));
        when(drafts.findByIdForUpdate("draft-new"))
            .thenReturn(Optional.of(draft("draft-new", "deployment-new", "{}")));
        when(consumers.findByBoundDeploymentIdOrderByUpdatedAtDesc("deployment-old")).thenReturn(List.of());
        PlatformConsumerEntity rebound = consumer("consumer-a");
        rebound.setBoundDeploymentId("deployment-new");
        when(consumers.findByBoundDeploymentIdOrderByUpdatedAtDesc("deployment-new")).thenReturn(List.of(rebound));
        when(installations.findByConsumerEntityIdInAndStatusIn(eq(List.of("consumer-a")), any()))
            .thenReturn(List.of(installation("consumer-a", AIWorkspaceInstallationStatus.DRAFT,
                "[\"https://app.example\"]")));

        service().synchronizeBindingChange("consumer-a", "deployment-old", "deployment-new");

        verify(drafts).save(org.mockito.ArgumentMatchers.argThat(value -> "draft-old".equals(value.getId())));
        verify(drafts).save(org.mockito.ArgumentMatchers.argThat(value -> "draft-new".equals(value.getId())));
    }

    private AIWorkspaceDeploymentSecurityProjectionService service() {
        return new AIWorkspaceDeploymentSecurityProjectionService(
            installations, consumers, deployments, drafts, validator, audit, objectMapper);
    }

    private DeploymentEntity deployment() {
        return deployment("deployment-1", "draft-1");
    }

    private DeploymentEntity deployment(String deploymentId, String draftId) {
        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId(deploymentId);
        deployment.setStatus("DRAFT");
        deployment.setActiveDraftId(draftId);
        return deployment;
    }

    private DeploymentDraftEntity draft(String security) {
        return draft("draft-1", "deployment-1", security);
    }

    private DeploymentDraftEntity draft(String draftId, String deploymentId, String security) {
        DeploymentDraftEntity draft = new DeploymentDraftEntity();
        draft.setId(draftId);
        draft.setDeploymentId(deploymentId);
        draft.setStatus("MODIFIED");
        draft.setSecurityConfigJson(security);
        return draft;
    }

    private PlatformConsumerEntity consumer(String id) {
        PlatformConsumerEntity consumer = new PlatformConsumerEntity();
        consumer.setId(id);
        consumer.setBoundDeploymentId("deployment-1");
        return consumer;
    }

    private AIWorkspaceInstallationEntity installation(String consumerId,
                                                        AIWorkspaceInstallationStatus status,
                                                        String origins) {
        AIWorkspaceInstallationEntity installation = new AIWorkspaceInstallationEntity();
        installation.setId("installation-" + consumerId);
        installation.setConsumerEntityId(consumerId);
        installation.setStatus(status);
        installation.setAllowedOriginsJson(origins);
        return installation;
    }
}
