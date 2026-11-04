package com.ai.fabric.platform.backend.desk;

import com.ai.fabric.platform.backend.deployment.entity.*;
import com.ai.fabric.platform.backend.deployment.repository.*;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeskBindingReconciliationServiceTest {
    final DeskBindingRepository bindings = mock(DeskBindingRepository.class);
    final DeploymentRepository deployments = mock(DeploymentRepository.class);
    final DeploymentReleaseRepository releases = mock(DeploymentReleaseRepository.class);
    final DeploymentTargetProfileRepository targets = mock(DeploymentTargetProfileRepository.class);
    final DeploymentVerificationRunRepository runs = mock(DeploymentVerificationRunRepository.class);
    final DeskBindingReconciliationService service = new DeskBindingReconciliationService(bindings, deployments, releases, targets, runs);
    final DeskBindingEntity binding = new DeskBindingEntity();
    final DeploymentEntity deployment = new DeploymentEntity();
    final DeploymentReleaseEntity release = new DeploymentReleaseEntity();
    final DeploymentVerificationRunEntity run = new DeploymentVerificationRunEntity();
    final DeploymentTargetProfileEntity target = new DeploymentTargetProfileEntity();

    @BeforeEach void prepare() {
        deployment.setId("dep-a"); deployment.setCustomerId("customer-a"); deployment.setTenantId("tenant-a");
        binding.setId("desk-a"); binding.setDeploymentId("dep-a"); binding.setTargetProfileId("target-a");
        binding.setCustomerId("customer-a"); binding.setTenantId("tenant-a"); binding.setReleaseId("rel-a");
        binding.setVersionId("ver-a"); binding.setStatus("APPLYING");
        release.setId("rel-a"); release.setDeploymentId("dep-a"); release.setTargetProfileId("target-a");
        release.setDeploymentVersionId("ver-a"); release.setVerificationRunId("run-a");
        release.setStatus("APPLIED_VERIFIED"); release.setVerificationStatus("PASSED"); release.setProvisioningStatus("ACTIVE");
        run.setId("run-a"); run.setReleaseId("rel-a"); run.setDeploymentId("dep-a"); run.setDeploymentVersionId("ver-a");
        run.setVerificationType("POST_APPLY"); run.setStatus("PASSED"); run.setCompletedAt(Instant.now());
        target.setId("target-a"); target.setActive(true);
        when(releases.findByIdForUpdate("rel-a")).thenReturn(Optional.of(release));
        when(bindings.findForUpdate("dep-a", "target-a")).thenReturn(Optional.of(binding));
        when(deployments.findById("dep-a")).thenReturn(Optional.of(deployment));
        when(targets.findById("target-a")).thenReturn(Optional.of(target));
        when(runs.findById("run-a")).thenReturn(Optional.of(run));
    }

    @ParameterizedTest @ValueSource(strings = {"POST_APPLY", "MANUAL_RERUN", "OPERATIONS_RECOVERY"})
    void verifiedReleaseEnablesTheExactBindingAndRepeatedCompletionDoesNotWriteAgain(String type) {
        run.setVerificationType(type); binding.setErrorMessage("old failure");
        service.reconcile("rel-a", "run-a");
        assertThat(binding.getStatus()).isEqualTo("ENABLED"); assertThat(binding.getErrorMessage()).isNull();
        var changed = binding.getUpdatedAt();
        service.reconcile("rel-a", "run-a");
        assertThat(binding.getUpdatedAt()).isEqualTo(changed);
        verify(bindings).save(binding);
    }

    @Test void failedVerificationStaysFailedAndLaterExactManualPassCanRepairIt() {
        release.setStatus("APPLIED_VERIFICATION_FAILED"); release.setVerificationStatus("FAILED"); run.setStatus("FAILED");
        service.reconcile("rel-a", "run-a");
        assertThat(binding.getStatus()).isEqualTo("FAILED");
        assertThat(binding.getErrorMessage()).contains("did not pass deployment verification");
        release.setStatus("APPLIED_VERIFIED"); release.setVerificationStatus("PASSED"); run.setStatus("PASSED");
        run.setVerificationType("MANUAL_RERUN");
        service.reconcile("rel-a", null);
        assertThat(binding.getStatus()).isEqualTo("ENABLED"); assertThat(binding.getErrorMessage()).isNull();
    }

    @ParameterizedTest @ValueSource(strings = {"binding-release", "binding-version", "run-release", "run-deployment", "run-version",
        "run-status", "run-incomplete", "run-preflight", "release-inflight", "release-inconsistent", "release-stopped",
        "target-disabled", "archived", "missing-run", "missing-target", "missing-deployment", "support-only", "disabled"})
    void mismatchedOrNonterminalEvidenceNeverEnablesOrFailsTheBinding(String change) {
        switch (change) {
            case "binding-release" -> binding.setReleaseId("rel-newer");
            case "binding-version" -> binding.setVersionId("ver-newer");
            case "run-release" -> run.setReleaseId("rel-other");
            case "run-deployment" -> run.setDeploymentId("dep-other");
            case "run-version" -> run.setDeploymentVersionId("ver-other");
            case "run-status" -> run.setStatus("FAILED");
            case "run-incomplete" -> run.setCompletedAt(null);
            case "run-preflight" -> run.setVerificationType("PRE_APPLY");
            case "release-inflight" -> release.setStatus("VERIFYING");
            case "release-inconsistent" -> release.setStatus("APPLIED_VERIFICATION_FAILED");
            case "release-stopped" -> release.setProvisioningStatus("STOPPED");
            case "target-disabled" -> target.setActive(false);
            case "archived" -> deployment.setArchivedAt(Instant.now());
            case "missing-run" -> when(runs.findById("run-a")).thenReturn(Optional.empty());
            case "missing-target" -> when(targets.findById("target-a")).thenReturn(Optional.empty());
            case "missing-deployment" -> when(deployments.findById("dep-a")).thenReturn(Optional.empty());
            case "support-only" -> binding.setStatus("SUPPORT_ONLY");
            case "disabled" -> binding.setStatus("DISABLED");
            default -> throw new AssertionError(change);
        }
        String original = binding.getStatus();
        service.reconcile("rel-a", "run-a");
        assertThat(binding.getStatus()).isEqualTo(original);
        verify(bindings, never()).save(any());
    }

    @ParameterizedTest @ValueSource(strings = {"customer", "tenant", "deployment", "target"})
    void ownershipMismatchCannotBeReconciled(String change) {
        switch (change) {
            case "customer" -> binding.setCustomerId("other");
            case "tenant" -> binding.setTenantId("other");
            case "deployment" -> binding.setDeploymentId("other");
            case "target" -> binding.setTargetProfileId("other");
        }
        assertThatThrownBy(() -> service.reconcile("rel-a", "run-a"))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class).hasMessageContaining("ownership");
        verify(bindings, never()).save(any());
    }

    @Test void delayedEventForEarlierRunDoesNotOverwriteTheLatestVerification() {
        service.reconcile("rel-a", "earlier-run");
        verifyNoInteractions(bindings, deployments, targets, runs);
    }

    @Test void ownerRepairPreservesTerminalApplyFailureWithoutInventingAVerificationRun() {
        release.setStatus("FAILED"); release.setVerificationRunId(null);
        release.setVerificationStatus("SKIPPED"); release.setProvisioningStatus("FAILED");
        service.reconcile("rel-a", null);
        assertThat(binding.getStatus()).isEqualTo("FAILED");
        assertThat(binding.getErrorMessage()).contains("could not be applied and verified");
        verifyNoInteractions(runs);
    }

    @Test void releasesWithoutDeskAndUnknownReleasesAreNoOps() {
        when(bindings.findForUpdate("dep-a", "target-a")).thenReturn(Optional.empty());
        service.reconcile("rel-a", "run-a"); service.reconcile("unknown", "run-a");
        verifyNoInteractions(deployments, targets, runs);
    }
}
