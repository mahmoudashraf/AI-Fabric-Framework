package com.ai.fabric.platform.backend.aiworkspace.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentReleaseEndpointService;
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AIWorkspaceAssignmentServiceTest {
    private final DeploymentRepository deployments = mock(DeploymentRepository.class);
    private final DeploymentReleaseRepository releases = mock(DeploymentReleaseRepository.class);
    private final DeploymentVersionRepository versions = mock(DeploymentVersionRepository.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository profiles =
        mock(com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository.class);
    private final AIWorkspaceAssignmentService service = new AIWorkspaceAssignmentService(
        deployments, releases, versions, mapper, new DeploymentReleaseEndpointService(mapper), profiles);
    private PlatformConsumerEntity consumer;
    private DeploymentReleaseEntity release;

    @BeforeEach
    void setUp() {
        consumer = new PlatformConsumerEntity();
        consumer.setConsumerId("consumer-1");
        consumer.setCustomerId("customer-1");
        consumer.setStatus("ACTIVE");
        consumer.setBoundDeploymentId("dep-1");
        consumer.setBoundReleaseId("rel-stage");
        consumer.setBoundTargetProfileId("stage");
        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-1");
        deployment.setCustomerId("customer-1");
        deployment.setBehaviorType("CONVERSATIONAL");
        deployment.setRuntimeBaseUrl("https://wrong-production.example");
        deployment.setEnvironmentName("production");
        release = new DeploymentReleaseEntity();
        release.setId("rel-stage");
        release.setDeploymentId("dep-1");
        release.setDeploymentVersionId("ver-stage");
        release.setTargetProfileId("stage");
        release.setProviderType(com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType.COOLIFY);
        release.setStatus("APPLIED_VERIFIED");
        release.setVerificationStatus("PASSED");
        release.setProvisioningDetailsJson("{\"runtimeFqdn\":\"https://stage.example\"}");
        DeploymentVersionEntity version = new DeploymentVersionEntity();
        version.setId("ver-stage");
        version.setDeploymentId("dep-1");
        version.setSecurityConfigJson("{}");
        when(deployments.findById("dep-1")).thenReturn(Optional.of(deployment));
        when(releases.findById("rel-stage")).thenReturn(Optional.of(release));
        when(versions.findById("ver-stage")).thenReturn(Optional.of(version));
        var profile = new com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity();
        profile.setId("stage"); profile.setActive(true); profile.setEnvironmentName("staging");
        profile.setProviderType(com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType.COOLIFY);
        when(profiles.findById("stage")).thenReturn(Optional.of(profile));
    }

    @Test
    void assignmentUsesItsBoundReleaseInsteadOfGlobalProductionSummary() {
        assertThat(service.resolveCurrent(consumer).runtimeBaseUrl()).isEqualTo("https://stage.example");
        assertThat(service.resolveCurrent(consumer).deployment().getEnvironmentName()).isEqualTo("staging");
        assertThat(deployments.findById("dep-1").orElseThrow().getEnvironmentName()).isEqualTo("production");
    }

    @Test
    void missingProviderEvidenceDoesNotFallBackToGlobalUrl() {
        release.setProvisioningDetailsJson("{}");
        assertThatThrownBy(() -> service.resolveCurrent(consumer)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void roundTripRebindChangesAssignmentRevisionEvenForTheSameRelease() {
        String first = service.resolveCurrent(consumer).assignmentRevision();
        consumer.setBindingRevision(2);
        assertThat(service.resolveCurrent(consumer).assignmentRevision()).isNotEqualTo(first);
    }

    @Test
    void inconsistentOrAbsentTargetIsRejected() {
        consumer.setBoundTargetProfileId("production");
        assertThatThrownBy(() -> service.resolveCurrent(consumer)).isInstanceOf(IllegalStateException.class);
        consumer.setBoundTargetProfileId(null);
        assertThatThrownBy(() -> service.resolveCurrent(consumer)).isInstanceOf(IllegalStateException.class);
    }
}
