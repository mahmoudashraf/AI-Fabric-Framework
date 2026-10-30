package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.*;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class DeploymentTargetContextServiceTest {
    private final DeploymentRepository deployments = mock(DeploymentRepository.class);
    private final DeploymentReleaseRepository releases = mock(DeploymentReleaseRepository.class);
    private final DeploymentVersionRepository versions = mock(DeploymentVersionRepository.class);
    private final DeploymentTargetProfileRepository profiles = mock(DeploymentTargetProfileRepository.class);
    private final DeploymentProviderResourceHandleRepository handles = mock(DeploymentProviderResourceHandleRepository.class);
    private final DeploymentAccessService access = mock(DeploymentAccessService.class);
    private final DeploymentReleaseEndpointService endpoints = new DeploymentReleaseEndpointService(new ObjectMapper());
    private final DeploymentTargetContextService service = new DeploymentTargetContextService(deployments, releases, versions, profiles, handles, access, endpoints, new ObjectMapper(), mock(DeploymentDocumentBindingResolver.class));
    private DeploymentEntity deployment;
    private DeploymentReleaseEntity release;
    private DeploymentVersionEntity version;
    private DeploymentTargetProfileEntity profile;
    private DeploymentProviderResourceHandleEntity handle;

    @BeforeEach
    void setUp() {
        deployment = new DeploymentEntity();
        deployment.setId("dep-1");
        deployment.setRuntimeBaseUrl("https://new-global-production.example");
        deployment.setActiveVersionId("new-global-version");
        release = new DeploymentReleaseEntity();
        release.setId("rel-stage");
        release.setDeploymentId("dep-1");
        release.setTargetProfileId("stage");
        release.setDeploymentVersionId("ver-stage");
        release.setProviderType(DeploymentProviderType.COOLIFY);
        release.setProviderResourceHandleId("handle-stage");
        release.setProvisioningStatus("ACTIVE");
        release.setStatus("APPLIED_VERIFIED");
        release.setProvisioningDetailsJson("{\"runtimeFqdn\":\"https://stage.example\",\"connectorFqdn\":\"https://stage-connector.example\",\"runtimeApplicationUuid\":\"app-stage\"}");
        version = new DeploymentVersionEntity();
        version.setId("ver-stage");
        version.setDeploymentId("dep-1");
        profile = new DeploymentTargetProfileEntity();
        profile.setId("stage");
        profile.setActive(true);
        profile.setProviderType(DeploymentProviderType.COOLIFY);
        profile.setProviderConfigJson("{\"serverUuid\":\"server-stage\"}");
        handle = new DeploymentProviderResourceHandleEntity();
        handle.setId("handle-stage");
        handle.setDeploymentId("dep-1");
        handle.setTargetProfileId("stage");
        handle.setReleaseId("rel-stage");
        handle.setProviderType(DeploymentProviderType.COOLIFY);
        handle.setStatus("ACTIVE");
        handle.setResourceKind("APPLICATION");
        handle.setProviderServerUuid("server-stage");
        handle.setProviderResourceUuid("app-stage");
        when(deployments.findById("dep-1")).thenReturn(Optional.of(deployment));
        when(releases.findById("rel-stage")).thenReturn(Optional.of(release));
        when(versions.findById("ver-stage")).thenReturn(Optional.of(version));
        when(profiles.findById("stage")).thenReturn(Optional.of(profile));
        when(handles.findById("handle-stage")).thenReturn(Optional.of(handle));
    }

    @Test
    void explicitStagingContextDoesNotFollowProductionProjection() {
        var context = resolve(false);
        assertThat(context.version().getId()).isEqualTo("ver-stage");
        assertThat(context.runtimeBaseUrl()).isEqualTo("https://stage.example");
        assertThat(context.connectorBaseUrl()).isEqualTo("https://stage-connector.example");
        verify(access).requireDeploymentAccess(deployment);
    }

    @Test
    void ownedReadReusesExactTargetEvidenceWithoutImpersonatingBrowserOperator() {
        deployment.setCustomerId("customer-a"); deployment.setTenantId("tenant-a");
        var context = service.resolveOwnedRead("dep-1", "stage", "rel-stage", "customer-a", "tenant-a");
        assertThat(context.runtimeBaseUrl()).isEqualTo("https://stage.example");
        verifyNoInteractions(access);
    }

    @Test
    void ownedReadDeniesForeignCustomerAndTenantBeforeLookingUpRelease() {
        deployment.setCustomerId("customer-a"); deployment.setTenantId("tenant-a");
        assertThatThrownBy(() -> service.resolveOwnedRead("dep-1", "stage", "rel-stage", "customer-b", "tenant-a"))
            .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.resolveOwnedRead("dep-1", "stage", "rel-stage", "customer-a", "tenant-b"))
            .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(releases, profiles, versions, handles, access);
    }

    @Test
    void ownedReadStillRejectsStaleRuntimeGeneration() {
        deployment.setCustomerId("customer-a"); deployment.setTenantId("tenant-a"); handle.setReleaseId("superseded");
        assertThatThrownBy(() -> service.resolveOwnedRead("dep-1", "stage", "rel-stage", "customer-a", "tenant-a"))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("generation");
    }

    @Test
    void crossTargetReleaseIsRejected() {
        assertThatThrownBy(() -> service.resolve("dep-1", "production", "rel-stage", false))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("requested target");
    }

    @Test
    void foreignReleaseDoesNotResolve() {
        release.setDeploymentId("foreign-deployment");
        assertConflict("release not found");
    }

    @Test
    void foreignCandidateDoesNotResolve() {
        version.setDeploymentId("foreign-deployment");
        assertConflict("candidate version");
    }

    @Test
    void missingProviderEvidenceDoesNotUseGlobalUrl() {
        release.setProvisioningDetailsJson("{\"runtimeApplicationUuid\":\"app-stage\"}");
        assertConflict("runtime endpoint is unavailable");
    }

    @Test
    void staleResourceGenerationDoesNotResolve() {
        handle.setReleaseId("replacement-release");
        assertConflict("generation is inconsistent");
    }

    @Test
    void foreignResourceDoesNotResolve() {
        handle.setDeploymentId("foreign-deployment");
        assertConflict("ownership or generation");
    }

    @ParameterizedTest
    @ValueSource(strings = {"RETIRED", "DELETED", "DELETE_REQUESTED", "ORPHANED"})
    void retiredResourceCannotBeAdministered(String status) {
        handle.setStatus(status);
        assertConflict("ownership or generation");
    }

    @ParameterizedTest
    @ValueSource(strings = {"VERIFYING", "APPLIED_VERIFICATION_FAILED"})
    void explicitlyProvisionedCandidateAllowsIndexingBeforeActivation(String status) {
        release.setStatus(status);
        assertThat(resolve(true).runtimeBaseUrl()).isEqualTo("https://stage.example");
        verify(access).requireDeploymentOperatorAccess(deployment);
    }

    @Test
    void accessIsCheckedBeforeReleaseLookup() {
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND)).when(access).requireDeploymentAccess(deployment);
        assertThatThrownBy(() -> resolve(false)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(releases, profiles, versions, handles);
    }

    @Test
    void inactiveTargetFailsClosed() {
        profile.setActive(false);
        assertConflict("inactive");
    }

    @Test
    void missingExplicitTargetIsRejected() {
        assertThatThrownBy(() -> service.resolve("dep-1", null, "rel-stage", false))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("targetProfileId is required");
    }

    @Test
    void relocatedTargetCannotAdministerTheOldServer() {
        profile.setProviderConfigJson("{\"serverUuid\":\"production-server\"}");
        assertConflict("server placement");
    }

    @Test
    void anotherApplicationCannotImpersonateTheRuntime() {
        handle.setProviderResourceUuid("another-application");
        assertConflict("application evidence");
    }

    @Test
    void documentBindingHandleIsNotRuntimeEvidence() {
        handle.setResourceKind("EXTERNAL_DOCUMENT_STORAGE_BINDING");
        assertConflict("application evidence");
    }

    private DeploymentTargetContextService.Context resolve(boolean mutate) {
        return service.resolve("dep-1", "stage", "rel-stage", mutate);
    }

    private void assertConflict(String message) {
        assertThatThrownBy(() -> resolve(false)).isInstanceOf(ResponseStatusException.class).hasMessageContaining(message);
    }
}
