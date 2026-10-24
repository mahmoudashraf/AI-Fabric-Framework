package com.ai.fabric.platform.backend.deployment.lifecycle;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentReleaseExecutionService;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class DeploymentReleaseLifecycleServiceTest {
    private final DeploymentReleaseRepository releases = mock(DeploymentReleaseRepository.class);
    private final DeploymentReleaseExecutionService execution = mock(DeploymentReleaseExecutionService.class);
    private final DeploymentReleaseLifecycleService lifecycle = new DeploymentReleaseLifecycleService(releases, execution);

    @Test void startupRecordsEveryInterruptedReleaseWithoutDispatchingProviderWork() {
        var interrupted = DeploymentTargetMutationService.ACTIVE_RELEASE_STATUSES.stream()
            .map(this::release).toList();
        when(releases.findByStatusIn(DeploymentTargetMutationService.ACTIVE_RELEASE_STATUSES)).thenReturn(interrupted);
        lifecycle.markInterruptedReleasesOnStartup();
        for (var release : interrupted) verify(execution).markInterrupted(release.getId(), "deployment");
        verifyNoMoreInteractions(execution);
    }

    @Test void oneInterruptionFailureDoesNotPreventOtherReleasesBeingMarked() {
        when(releases.findByStatusIn(DeploymentTargetMutationService.ACTIVE_RELEASE_STATUSES))
            .thenReturn(List.of(release("first"), release("second")));
        doThrow(new IllegalStateException("unavailable release")).when(execution).markInterrupted("first", "deployment");
        lifecycle.markInterruptedReleasesOnStartup();
        verify(execution).markInterrupted("first", "deployment");
        verify(execution).markInterrupted("second", "deployment");
        verifyNoMoreInteractions(execution);
    }

    @Test void startupWithNoActiveReleasesDoesNothing() {
        when(releases.findByStatusIn(DeploymentTargetMutationService.ACTIVE_RELEASE_STATUSES)).thenReturn(List.of());
        lifecycle.markInterruptedReleasesOnStartup();
        verifyNoInteractions(execution);
    }

    private DeploymentReleaseEntity release(String id) {
        var release = new DeploymentReleaseEntity(); release.setId(id); release.setDeploymentId("deployment"); return release;
    }
}
