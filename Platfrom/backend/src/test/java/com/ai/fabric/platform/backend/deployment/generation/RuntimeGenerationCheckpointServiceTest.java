package com.ai.fabric.platform.backend.deployment.generation;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationTransitionContracts.Target;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RuntimeGenerationCheckpointServiceTest {
    private final DeploymentReleaseRepository releases = mock(DeploymentReleaseRepository.class);
    private final DeploymentReleaseEntity release = release();
    private RuntimeGenerationCheckpointService service;

    @BeforeEach
    void setUp() {
        when(releases.findById("release-new")).thenReturn(Optional.of(release));
        when(releases.findByIdForUpdate("release-new")).thenReturn(Optional.of(release));
        when(releases.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service = new RuntimeGenerationCheckpointService(releases, new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void persistsFencedStopAndAdoptedPhasesWithExactReplay() {
        var fenced = service.recordFenced("release-new", prior(), receipt(), "c".repeat(64));
        assertThat(fenced.phase()).isEqualTo(RuntimeGenerationCheckpointService.Phase.FENCED);
        assertThat(service.read("release-new")).contains(fenced);
        assertThat(service.recordFenced("release-new", prior(), receipt(), "c".repeat(64))).isEqualTo(fenced);

        var stopped = service.recordProviderStop("release-new", service.fingerprint(fenced), "coolify-stop:runtime-1:proof");
        assertThat(stopped.phase()).isEqualTo(RuntimeGenerationCheckpointService.Phase.STOP_CONFIRMED);
        assertThat(stopped.providerStopEvidenceReference()).isEqualTo("coolify-stop:runtime-1:proof");

        var refreshed = service.refreshProviderStop("release-new", service.fingerprint(stopped),
            "coolify-stop:runtime-1:fresh-proof");
        assertThat(refreshed.phase()).isEqualTo(RuntimeGenerationCheckpointService.Phase.STOP_CONFIRMED);
        assertThat(refreshed.providerStopEvidenceReference()).isEqualTo("coolify-stop:runtime-1:fresh-proof");

        Instant adoptedAt = Instant.now().minusSeconds(1);
        var adopted = service.recordAdopted("release-new", service.fingerprint(refreshed), adoptedAt);
        assertThat(adopted.phase()).isEqualTo(RuntimeGenerationCheckpointService.Phase.ADOPTED);
        assertThat(service.read("release-new")).contains(adopted);
    }

    @Test
    void rejectsCheckpointDriftAndOutOfOrderMutation() {
        var fenced = service.recordFenced("release-new", prior(), receipt(), "c".repeat(64));
        assertThatThrownBy(() -> service.recordAdopted("release-new", service.fingerprint(fenced), Instant.now()))
            .hasMessage("RUNTIME_GENERATION_CHECKPOINT_STALE");
        assertThatThrownBy(() -> service.refreshProviderStop("release-new", service.fingerprint(fenced), "proof"))
            .hasMessage("RUNTIME_GENERATION_CHECKPOINT_STALE");

        release.setRuntimeGenerationTransitionFingerprint("0".repeat(64));
        assertThatThrownBy(() -> service.read("release-new"))
            .hasMessage("RUNTIME_GENERATION_CHECKPOINT_DRIFT");
    }

    @Test
    void rejectsCrossReleaseReceiptAndChangedInitialCheckpoint() {
        var foreign = new DeploymentRuntimeGenerationService.DrainReceipt(
            new Target("dep-1", "staging", "release-old"),
            new Target("dep-1", "staging", "release-other"),
            "tenant-1", "replace:release-new", "a".repeat(64), 3,
            "b".repeat(64), "runtime-evidence", null
        );
        assertThatThrownBy(() -> service.recordFenced("release-new", prior(), foreign, "c".repeat(64)))
            .hasMessage("RUNTIME_GENERATION_CHECKPOINT_INVALID");

        service.recordFenced("release-new", prior(), receipt(), "c".repeat(64));
        assertThatThrownBy(() -> service.recordFenced("release-new",
            new RuntimeGenerationCheckpointService.PriorRuntime("release-old", "runtime-2", "https://runtime.example"),
            receipt(), "c".repeat(64)))
            .hasMessage("RUNTIME_GENERATION_CHECKPOINT_ALREADY_EXISTS");
    }

    private RuntimeGenerationCheckpointService.PriorRuntime prior() {
        return new RuntimeGenerationCheckpointService.PriorRuntime(
            "release-old", "runtime-1", "https://runtime.example"
        );
    }

    private DeploymentRuntimeGenerationService.DrainReceipt receipt() {
        return new DeploymentRuntimeGenerationService.DrainReceipt(
            new Target("dep-1", "staging", "release-old"),
            new Target("dep-1", "staging", "release-new"),
            "tenant-1", "replace:release-new", "a".repeat(64), 3,
            "b".repeat(64), "runtime-evidence", null
        );
    }

    private DeploymentReleaseEntity release() {
        var value = new DeploymentReleaseEntity();
        value.setId("release-new");
        value.setDeploymentId("dep-1");
        value.setDeploymentVersionId("version-new");
        value.setTargetProfileId("staging");
        value.setUpdatedAt(Instant.parse("2026-10-14T00:00:00Z"));
        return value;
    }
}
