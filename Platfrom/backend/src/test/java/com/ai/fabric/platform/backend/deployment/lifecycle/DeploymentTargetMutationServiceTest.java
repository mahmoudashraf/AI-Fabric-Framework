package com.ai.fabric.platform.backend.deployment.lifecycle;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeploymentTargetMutationServiceTest {
    private final DeploymentRepository deployments = mock(DeploymentRepository.class);
    private final DeploymentReleaseRepository releases = mock(DeploymentReleaseRepository.class);
    private final DeploymentTargetMutationService service = new DeploymentTargetMutationService(deployments, releases);

    @ParameterizedTest
    @ValueSource(strings = {"APPLY_REQUESTED", "PRE_APPLY_VERIFYING", "PROVISIONING", "VERIFYING"})
    void sameTargetActiveReleaseBlocksMutationAndReplay(String status) {
        var release = new DeploymentReleaseEntity();
        release.setStatus(status);
        when(deployments.findByIdForUpdate("deployment")).thenReturn(Optional.of(new DeploymentEntity()));
        when(releases.findByDeploymentIdAndTargetProfileIdOrderByCreatedAtDesc("deployment", "staging"))
            .thenReturn(List.of(release));

        assertThatThrownBy(() -> service.requireAvailableForMutation("deployment", "staging"))
            .isInstanceOfSatisfying(ResponseStatusException.class, error -> {
                assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(error.getReason()).isEqualTo("RELEASE_APPLY_IN_PROGRESS");
            });
        assertThatThrownBy(() -> service.requireAvailableForReplay("deployment", "staging"))
            .hasMessageContaining("RELEASE_APPLY_IN_PROGRESS");
        var order = inOrder(deployments, releases);
        order.verify(deployments).findByIdForUpdate("deployment");
        order.verify(releases).findByDeploymentIdAndTargetProfileIdOrderByCreatedAtDesc("deployment", "staging");
        verify(releases, never()).save(any());
    }

    @Test void independentTargetIsAllowedWithoutInspectingAnotherTargetsReleases() {
        when(deployments.findByIdForUpdate("deployment")).thenReturn(Optional.of(new DeploymentEntity()));
        when(releases.findByDeploymentIdAndTargetProfileIdOrderByCreatedAtDesc("deployment", "production"))
            .thenReturn(List.of());
        service.requireAvailableForMutation("deployment", "production");
        verify(releases).findByDeploymentIdAndTargetProfileIdOrderByCreatedAtDesc("deployment", "production");
        verifyNoMoreInteractions(releases);
    }

    @ParameterizedTest
    @ValueSource(strings = {"FAILED", "PRE_APPLY_BLOCKED", "APPLIED_VERIFIED", "APPLIED_VERIFICATION_FAILED"})
    void terminalReleaseDoesNotFenceANewExplicitApply(String status) {
        var release = new DeploymentReleaseEntity(); release.setStatus(status);
        when(deployments.findByIdForUpdate("deployment")).thenReturn(Optional.of(new DeploymentEntity()));
        when(releases.findByDeploymentIdAndTargetProfileIdOrderByCreatedAtDesc("deployment", "staging"))
            .thenReturn(List.of(release));
        service.requireAvailableForMutation("deployment", "staging");
        service.requireAvailableForReplay("deployment", "staging");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void missingTargetFailsBeforeAnyLockOrReleaseLookup(String target) {
        assertThatThrownBy(() -> service.requireAvailableForMutation("deployment", target))
            .hasMessageContaining("DEPLOYMENT_TARGET_REQUIRED");
        verifyNoInteractions(deployments, releases);
    }

    @Test void missingDeploymentCannotAuthorizeAMutation() {
        assertThatThrownBy(() -> service.requireAvailableForMutation("missing", "staging"))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verifyNoInteractions(releases);
    }
}
