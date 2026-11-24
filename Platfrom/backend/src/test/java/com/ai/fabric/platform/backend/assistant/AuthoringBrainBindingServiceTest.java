package com.ai.fabric.platform.backend.assistant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.ai.fabric.platform.backend.deployment.authority.DeploymentRuntimeAuthorityService;
import com.ai.fabric.platform.backend.deployment.entity.*;
import com.ai.fabric.platform.backend.deployment.repository.*;
import com.ai.fabric.platform.backend.deployment.service.DeploymentReleaseEndpointService;
import com.ai.fabric.platform.backend.deployment.specialist.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthoringBrainBindingServiceTest {
    final ObjectMapper mapper = new ObjectMapper(); final AuthoringProperties properties = new AuthoringProperties();
    final DeploymentRepository brains = mock(DeploymentRepository.class);
    final DeploymentReleaseRepository releases = mock(DeploymentReleaseRepository.class);
    final DeploymentVersionRepository versions = mock(DeploymentVersionRepository.class);
    final DeploymentReleaseEndpointService endpoints = mock(DeploymentReleaseEndpointService.class);
    final DeploymentRuntimeAuthorityService authority = mock(DeploymentRuntimeAuthorityService.class);
    AuthoringBrainBindingService service; DeploymentEntity brain; DeploymentReleaseEntity release; DeploymentVersionEntity version;
    @BeforeEach void setup() {
        properties.setEnabled(true); properties.setConfiguredBrainEnabled(true);
        service = new AuthoringBrainBindingService(properties, brains, releases, versions, endpoints, authority, mapper);
        brain = new DeploymentEntity(); brain.setId(properties.getDeploymentId()); brain.setCustomerId(properties.getCustomerId());
        brain.setTenantId(properties.getTenantId()); brain.setActiveVersionId("version");
        release = new DeploymentReleaseEntity(); release.setId("release"); release.setDeploymentId(brain.getId());
        release.setDeploymentVersionId("version"); release.setStatus("APPLIED_VERIFIED"); release.setVerificationStatus("PASSED");
        version = new DeploymentVersionEntity(); version.setId("version"); version.setDeploymentId(brain.getId()); version.setStatus("PUBLISHED");
        publish(new ConfigurationAssistantTemplate(mapper).behavior());
        when(brains.findById(brain.getId())).thenReturn(Optional.of(brain));
        when(releases.findTopByDeploymentIdOrderByCreatedAtDesc(brain.getId())).thenReturn(Optional.of(release));
        when(releases.findById("release")).thenReturn(Optional.of(release));
        when(versions.findById("version")).thenReturn(Optional.of(version));
        when(endpoints.runtimeBaseUrl(release)).thenReturn("https://assistant.example.test");
    }
    void publish(ObjectNode behavior) {
        var installed = new SpecialistAssistanceCompiler.Authority(AuthoringNativeCapabilities.READS.stream().collect(Collectors.toMap(n -> n, n -> Set.of("configuration:author"))),
            Set.of(), AuthoringNativeCapabilities.WRITES.stream().collect(Collectors.toMap(n -> n, n -> Set.of("configuration:author"))));
        version.setBehaviorConfigJson(new WorkflowAssistanceCompiler(mapper).compileBehavior(behavior, installed).toString());
    }
    @Test void pageSelectionUsesThePublishedWorkerAndFrozenRelease() {
        var selected = service.resolve("ACTIONS");
        assertThat(selected.profileId()).isEqualTo("api"); assertThat(selected.runtimeUrl()).isEqualTo("https://assistant.example.test");
        assertThat(selected.scopes()).contains("configuration:author", "chat:messages");
        verify(authority).requireReleaseUnchangedForProvisioning(brain, version, release);
    }
    @Test void newerUnverifiedReleaseCannotSilentlyFallBackToOldSuccessfulHistory() {
        release.setStatus("PROVISIONING");
        assertThatThrownBy(() -> service.resolve("ACTIONS")).hasMessageContaining("MANAGED_BRAIN_UNAVAILABLE");
        verifyNoInteractions(authority);
    }
    @Test void disabledUnpublishedOrChangedAuthorityCannotBeUsed() {
        properties.setConfiguredBrainEnabled(false);
        assertThatThrownBy(() -> service.resolve("ACTIONS")).hasMessageContaining("UNAVAILABLE");
        properties.setConfiguredBrainEnabled(true); version.setStatus("DRAFT");
        assertThatThrownBy(() -> service.resolve("ACTIONS")).hasMessageContaining("UNAVAILABLE");
        version.setStatus("PUBLISHED"); brain.setActiveVersionId("other-version");
        assertThatThrownBy(() -> service.resolve("ACTIONS")).hasMessageContaining("UNAVAILABLE");
        brain.setActiveVersionId(version.getId());
        doThrow(new IllegalStateException("Frozen authority changed")).when(authority).requireReleaseUnchangedForProvisioning(brain, version, release);
        assertThatThrownBy(() -> service.resolve("ACTIONS")).hasMessageContaining("Frozen authority changed");
    }
    @Test void publishedInstructionsAndBindingsChangeSelectionWithoutApplicationChanges() {
        var before = service.resolve("ACTIONS"); var configuration = new ConfigurationAssistantTemplate(mapper).behavior();
        ((ObjectNode) configuration.at("/workflowAssistance/profiles/0")).put("instructions", "Explain actual receipts briefly.");
        ((ObjectNode) configuration.at("/workflowAssistance/taskBindings/0")).put("profileId", "data"); publish(configuration);
        var after = service.resolve("ACTIONS");
        assertThat(after.bundleHash()).isNotEqualTo(before.bundleHash()); assertThat(after.profileId()).isEqualTo("data");
    }
    @Test void explicitReleaseMustStillBeCurrentAndVerified() {
        properties.setManagedReleaseId("release"); assertThat(service.resolve("ACTIONS").releaseId()).isEqualTo("release");
        release.setVerificationStatus("FAILED");
        assertThatThrownBy(() -> service.resolve("ACTIONS")).hasMessageContaining("UNAVAILABLE");
    }
    @Test void wholeBrainSetupAndKitGuidanceUseDistinctPublishedAuthority() {
        var setup = service.resolve("BRAIN_SETUP");
        assertThat(setup.profileId()).isEqualTo("setup"); assertThat(setup.maxWrites()).isEqualTo(3);
        assertThat(setup.scopes()).contains("action:authoring_update_draft");
        var guide = service.resolve("KIT");
        assertThat(guide.maxWrites()).isZero();
        assertThat(guide.scopes()).doesNotContain("action:authoring_update_draft", "action:authoring_undo_request");
        assertThat(service.resolve("HUMAN_SUPPORT").scopes()).contains("action:authoring_update_draft");
    }
    @Test void disabledWorkflowRetainsOnlyItsVerifiedConversationMaintenanceBinding() {
        properties.setConfiguredBrainEnabled(false);
        var configuration = new ConfigurationAssistantTemplate(mapper).behavior();
        ((ObjectNode) configuration.path("workflowAssistance")).put("enabled", false); publish(configuration);
        assertThatThrownBy(() -> service.resolve("ACTIONS")).hasMessageContaining("UNAVAILABLE");
        var maintenance = service.conversationBinding();
        assertThat(maintenance.maxWrites()).isZero(); assertThat(maintenance.specialistId()).isEmpty();
        assertThat(maintenance.scopes()).doesNotContain("action:authoring_update_draft");
        assertThat(maintenance.releaseId()).isEqualTo("release");
    }
}
