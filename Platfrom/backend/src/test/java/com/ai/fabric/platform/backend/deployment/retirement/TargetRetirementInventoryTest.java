package com.ai.fabric.platform.backend.deployment.retirement;

import com.ai.fabric.platform.backend.deployment.entity.*;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.repository.*;
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TargetRetirementInventoryTest {
    private final DeploymentRepository deployments = mock(DeploymentRepository.class);
    private final DeploymentTargetProfileRepository targets = mock(DeploymentTargetProfileRepository.class);
    private final DeploymentReleaseRepository releases = mock(DeploymentReleaseRepository.class);
    private final DeploymentProviderResourceHandleRepository handles = mock(DeploymentProviderResourceHandleRepository.class);
    private final PlatformConsumerRepository consumers = mock(PlatformConsumerRepository.class);
    private TargetRetirementInventory service;
    private PlanRequest request;
    private DeploymentProviderResourceHandleEntity handle;
    private DeploymentReleaseEntity release;
    private PlatformConsumerEntity consumer;
    private DeploymentTargetProfileEntity target;

    @BeforeEach void setup() {
        service = new TargetRetirementInventory(deployments, targets, releases, handles, consumers,
            new TargetRetirementJson(new ObjectMapper().registerModule(new JavaTimeModule())));
        var deployment = new DeploymentEntity();
        deployment.setId("deployment"); deployment.setCustomerId("customer"); deployment.setTenantId("tenant");
        when(deployments.findById("deployment")).thenReturn(Optional.of(deployment));
        target = new DeploymentTargetProfileEntity();
        target.setId("staging"); target.setEnvironmentName("staging"); target.setProviderType(DeploymentProviderType.COOLIFY); target.setActive(true);
        target.setProviderConfigJson("{\"serverUuid\":\"staging-server\"}"); target.setResourceDefaultsJson("{}"); target.setNetworkPolicyJson("{}");
        when(targets.findById("staging")).thenReturn(Optional.of(target));
        release = new DeploymentReleaseEntity();
        release.setId("staging-release"); release.setDeploymentId("deployment"); release.setTargetProfileId("staging");
        release.setDeploymentVersionId("version"); release.setProviderType(DeploymentProviderType.COOLIFY);
        release.setProviderResourceHandleId("handle"); release.setProvisioningDetailsJson("{}"); release.setCreatedAt(Instant.now());
        when(releases.findById("staging-release")).thenReturn(Optional.of(release));
        when(releases.findAll()).thenReturn(List.of(release));
        handle = new DeploymentProviderResourceHandleEntity();
        handle.setId("handle"); handle.setDeploymentId("deployment"); handle.setTargetProfileId("staging"); handle.setReleaseId("staging-release");
        handle.setResourceKind("APPLICATION"); handle.setProviderType(DeploymentProviderType.COOLIFY);
        handle.setProviderResourceUuid("staging-app"); handle.setProviderServerUuid("staging-server"); handle.setCreatedAt(Instant.now());
        handle.setStatus("ACTIVE"); handle.setMetadataJson("{}");
        when(handles.findByDeploymentIdAndTargetProfileIdOrderByUpdatedAtDesc("deployment", "staging")).thenReturn(List.of(handle));
        when(handles.findById("handle")).thenReturn(Optional.of(handle));
        when(handles.findAll()).thenReturn(List.of(handle));
        consumer = new PlatformConsumerEntity();
        consumer.setId("consumer"); consumer.setCustomerId("customer"); consumer.setBindingRevision(7);
        consumer.setBoundDeploymentId("deployment"); consumer.setBoundReleaseId("production-release"); consumer.setBoundTargetProfileId("production");
        when(consumers.findById("consumer")).thenReturn(Optional.of(consumer));
        when(consumers.findAll()).thenReturn(List.of(consumer));
        request = new PlanRequest("key", List.of("staging-release"), List.of(new ConsumerExpectation("consumer", 7)), Instant.now().plusSeconds(60),
            new Retention(RetentionDecision.RECLAIM, null, null, "backup-policy"), "post-promotion");
    }

    @Test void productionAssignmentIsPreservedWhileItsRevisionIsChecked() {
        var snapshot = capture();
        assertThat(snapshot.resources()).hasSize(1);
        assertThat(consumer.getBoundReleaseId()).isEqualTo("production-release");
        service.recheck(snapshot, request);
        consumer.setBindingRevision(8);
        assertThatThrownBy(() -> service.recheck(snapshot, request)).hasMessageContaining("CONSUMER_REVISION_CHANGED");
        verify(consumers, never()).save(any());
        verify(releases, never()).delete(any());
    }
    @Test void activeOrAmbiguousTargetAssignmentBlocksRetirement() {
        consumer.setBoundTargetProfileId("staging");
        assertThatThrownBy(this::capture).hasMessageContaining("TARGET_STILL_ASSIGNED");
        consumer.setBoundTargetProfileId(null);
        assertThatThrownBy(this::capture).hasMessageContaining("TARGET_STILL_ASSIGNED");
    }
    @Test void crossTargetReleasePointerAndSharedResourceAliasAreRejected() {
        var production = new DeploymentReleaseEntity();
        production.setId("production-release"); production.setProviderResourceHandleId("handle");
        when(releases.findAll()).thenReturn(List.of(release, production));
        assertThatThrownBy(this::capture).hasMessageContaining("SHARED_RELEASE_REFERENCE");
        when(releases.findAll()).thenReturn(List.of(release));
        var alias = new DeploymentProviderResourceHandleEntity();
        alias.setId("shared"); alias.setProviderType(DeploymentProviderType.COOLIFY); alias.setProviderResourceUuid("staging-app");
        when(handles.findAll()).thenReturn(List.of(handle, alias));
        assertThatThrownBy(this::capture).hasMessageContaining("SHARED_RESOURCE_REFERENCE");
    }
    @Test void replacementOrMetadataBindingChangeInvalidatesSnapshotButObservationsDoNot() {
        var snapshot = capture();
        handle.setUpdatedAt(Instant.now()); handle.setLastObservedStatus("running");
        service.recheck(snapshot, request);
        handle.setCreatedAt(handle.getCreatedAt().plusSeconds(1));
        assertThatThrownBy(() -> service.recheck(snapshot, request)).hasMessageContaining("SNAPSHOT_GENERATION_CHANGED");
        assertThatThrownBy(() -> service.requireResource(snapshot.resources().get(0))).hasMessageContaining("RESOURCE_GENERATION_CHANGED");
    }
    @Test void releaseGenerationAndProfilePlacementChangeInvalidateSnapshot() {
        var snapshot = capture();
        release.setDeploymentVersionId("replacement-version");
        assertThatThrownBy(() -> service.recheck(snapshot, request)).hasMessageContaining("SNAPSHOT_GENERATION_CHANGED");
        release.setDeploymentVersionId("version");
        target.setProviderConfigJson("{\"serverUuid\":\"production-server\"}");
        assertThatThrownBy(() -> service.recheck(snapshot, request)).hasMessageContaining("SNAPSHOT_GENERATION_CHANGED");
    }
    @Test void unownedOrUnenumeratedConsumersAndForeignResourceReleaseFailClosed() {
        consumer.setCustomerId("foreign");
        assertThatThrownBy(this::capture).hasMessageContaining("CONSUMER_REVISION_CHANGED");
        consumer.setCustomerId("customer");
        var unknown = new PlatformConsumerEntity(); unknown.setId("unknown"); unknown.setBoundDeploymentId("deployment");
        unknown.setBoundTargetProfileId("production");
        when(consumers.findAll()).thenReturn(List.of(consumer, unknown));
        assertThatThrownBy(this::capture).hasMessageContaining("CONSUMER_EXPECTATION_MISSING");
        when(consumers.findAll()).thenReturn(List.of(consumer));
        handle.setReleaseId("foreign-release");
        assertThatThrownBy(this::capture).hasMessageContaining("RESOURCE_INVENTORY_UNSUPPORTED");
    }
    @Test void productionTargetAndEmptyResourceInventoryAreNeverRetirementSuccess() {
        target.setEnvironmentName("production");
        assertThatThrownBy(this::capture).hasMessageContaining("STAGING_TARGET_REQUIRED");
        target.setEnvironmentName("staging");
        when(handles.findByDeploymentIdAndTargetProfileIdOrderByUpdatedAtDesc("deployment", "staging")).thenReturn(List.of());
        assertThatThrownBy(this::capture).hasMessageContaining("RESOURCE_INVENTORY_EMPTY");
    }
    private Snapshot capture() { return service.capture("deployment", "staging", request, 0); }
}
