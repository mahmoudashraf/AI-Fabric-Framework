package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.PublicApiDeploymentEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.model.DeploymentReleaseSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVersionSummary;
import com.ai.fabric.platform.backend.deployment.model.PublicApplyDeploymentRequest;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.repository.PublicApiDeploymentRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.tenant.service.PlatformCustomerConsumerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PublicProvisioningApplySelectionTest {
    private final PublicApiDeploymentRepository bindings = mock(PublicApiDeploymentRepository.class);
    private final DeploymentService deployments = mock(DeploymentService.class);
    private final PublicProvisioningApiService service = new PublicProvisioningApiService(bindings, deployments,
        mock(DeploymentVersionRepository.class), mock(PlatformAuditService.class), mock(PlatformSecretService.class),
        mock(PlatformCustomerConsumerService.class), new ObjectMapper());

    @BeforeEach
    void callerAndOwnedDeployment() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            new PlatformPrincipal("client", PlatformRole.PUBLIC_API_CLIENT, "Client", "PUBLIC_API_KEY"), null, List.of()));
        var binding = new PublicApiDeploymentEntity();
        binding.setId("binding"); binding.setClientId("client");
        binding.setDeploymentId("deployment"); binding.setExternalDeploymentKey("external");
        when(bindings.findByClientIdAndDeploymentId("client", "deployment")).thenReturn(Optional.of(binding));
        when(deployments.listVersions("deployment")).thenReturn(List.of(new DeploymentVersionSummary(
            "version", "deployment", "draft", "v1", "PUBLISHED", "hash", false, Instant.now())));
        when(deployments.listReleases("deployment")).thenReturn(List.of(release("existing", "old-target", "old-artifact")));
    }

    @AfterEach
    void clearCaller() { SecurityContextHolder.clearContext(); }

    @Test
    void exactSelectionReadsBackTheExistingReleaseWithoutAnotherApply() {
        var result = service.applyDeployment("deployment",
            new PublicApplyDeploymentRequest("version", " old-target ", " old-artifact "));
        assertThat(result.idempotentReplay()).isTrue();
        assertThat(result.release().id()).isEqualTo("existing");
        verify(deployments, never()).applyVersionForVerifiedPublicApiBinding(anyString(), anyString(), any(), any());
    }

    @Test
    void differentTargetDoesNotReplayTheOtherDestination() {
        var selected = release("new", "new-target", "old-artifact");
        when(deployments.applyVersionForVerifiedPublicApiBinding("deployment", "version", "new-target", "old-artifact"))
            .thenReturn(selected);
        var result = service.applyDeployment("deployment", new PublicApplyDeploymentRequest("version", "new-target", "old-artifact"));
        assertThat(result.idempotentReplay()).isFalse();
        assertThat(result.release()).isEqualTo(selected);
    }

    @Test
    void differentArtifactDoesNotReplayTheOldRuntime() {
        var selected = release("new", "old-target", "new-artifact");
        when(deployments.applyVersionForVerifiedPublicApiBinding("deployment", "version", "old-target", "new-artifact"))
            .thenReturn(selected);
        var result = service.applyDeployment("deployment", new PublicApplyDeploymentRequest("version", "old-target", "new-artifact"));
        assertThat(result.idempotentReplay()).isFalse();
        assertThat(result.release()).isEqualTo(selected);
    }

    @Test
    void foreignDeploymentIsDeniedBeforeResolvingOrApplying() {
        assertThatThrownBy(() -> service.applyDeployment("foreign",
            new PublicApplyDeploymentRequest("version", "old-target", "old-artifact")))
            .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(deployments);
    }

    @Test
    void explicitSelectionDoesNotHideCoreArtifactDenial() {
        var denial = new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "Artifact is not promoted");
        when(deployments.applyVersionForVerifiedPublicApiBinding("deployment", "version", "old-target", "unreviewed"))
            .thenThrow(denial);
        assertThatThrownBy(() -> service.applyDeployment("deployment",
            new PublicApplyDeploymentRequest("version", "old-target", "unreviewed"))).isSameAs(denial);
    }

    private static DeploymentReleaseSummary release(String id, String target, String artifact) {
        return new DeploymentReleaseSummary(id, "deployment", "version", "APPLY_REQUESTED", "PENDING", "PENDING",
            "coolify", target, DeploymentProviderType.COOLIFY, artifact, null, null, null, null, null, null,
            Instant.now(), null, Instant.now());
    }
}
