package com.ai.fabric.platform.backend.deployment.generation;

import com.ai.fabric.platform.backend.deployment.authority.DeploymentRuntimeAuthorityService;
import com.ai.fabric.platform.backend.deployment.authority.RuntimeAuthoritySnapshotEntity;
import com.ai.fabric.platform.backend.deployment.authority.RuntimeAuthoritySnapshotRepository;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationTransitionContracts.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DeploymentRuntimeGenerationServiceTest {
    private final RuntimeGenerationTransitionClient client = mock(RuntimeGenerationTransitionClient.class);
    private final DeploymentRuntimeAuthorityService authority = mock(DeploymentRuntimeAuthorityService.class);
    private final RuntimeAuthoritySnapshotRepository snapshots = mock(RuntimeAuthoritySnapshotRepository.class);
    private final Instant now = Instant.parse("2026-10-14T12:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private DeploymentRuntimeGenerationService service;
    private DeploymentEntity deployment;
    private DeploymentReleaseEntity next;
    private Target previous;

    @BeforeEach void setUp() {
        service = new DeploymentRuntimeGenerationService(client, authority, snapshots, clock, millis -> { });
        deployment = new DeploymentEntity();
        deployment.setId("dep-1"); deployment.setCustomerId("customer-1"); deployment.setTenantId("tenant-1");
        next = new DeploymentReleaseEntity();
        next.setId("release-new"); next.setDeploymentId("dep-1"); next.setDeploymentVersionId("version-new");
        next.setTargetProfileId("staging");
        previous = new Target("dep-1", "staging", "release-old");
        RuntimeAuthoritySnapshotEntity snapshot = new RuntimeAuthoritySnapshotEntity();
        snapshot.setId("authority-old"); snapshot.setDeploymentId("dep-1"); snapshot.setTargetProfileId("staging");
        snapshot.setVersionId("version-old"); snapshot.setReleaseId("release-old"); snapshot.setFingerprint("a".repeat(64));
        when(snapshots.findByReleaseId("release-old")).thenReturn(Optional.of(snapshot));
        when(authority.issueGenerationTransitionHeaders(deployment, next, "release-old", "a".repeat(64), "replace:release-new"))
            .thenReturn(Map.of("Authorization", "private"));
    }

    @Test void reapplyReadbackProvesPriorGenerationWithoutDrainingOrIssuingWriteCommands() {
        when(authority.issueSystemHeaders("dep-1", "staging", "release-old", "a".repeat(64),
            "platform-reapply-readback", "reapply-readback:release-new", List.of(DeploymentRuntimeGenerationService.READ_SCOPE),
            Duration.ofMinutes(2))).thenReturn(Map.of("Authorization", "read-only-private"));
        var active = readback(State.ACTIVE, 1, null, null);
        when(client.status(anyString(), eq(previous), anyMap())).thenReturn(active);
        assertThat(service.requireActiveForReapply(deployment, next, "release-old", "https://old.example"))
            .isEqualTo(active);
        verify(client, never()).command(anyString(), any(), anyMap());
        verify(client, never()).transition(anyString(), any(), anyMap());
        verify(client).status("https://old.example", previous, Map.of("Authorization", "read-only-private"));
        verify(authority, never()).issueGenerationTransitionHeaders(any(), any(), anyString(), anyString(), anyString());
        when(client.status(anyString(), eq(previous), anyMap())).thenReturn(readback(State.DRAINING, 2, null, null));
        assertThatThrownBy(() -> service.requireActiveForReapply(deployment, next, "release-old", "https://old.example"))
            .hasMessage("RUNTIME_GENERATION_PREVIOUS_NOT_ACTIVE");
        when(client.status(anyString(), eq(previous), anyMap())).thenReturn(new Readback(
            new Target("dep-other", "staging", "release-old"), "tenant-1", State.ACTIVE, 1,
            null, null, null, false, false, now));
        assertThatThrownBy(() -> service.requireActiveForReapply(deployment, next, "release-old", "https://old.example"))
            .hasMessage("RUNTIME_GENERATION_READBACK_INVALID");
    }

    @Test void drainsFencesAndAdoptsExactGenerationWithoutPersistingHeaders() {
        when(client.status(anyString(), eq(previous), anyMap())).thenReturn(readback(State.ACTIVE, 1, null, null));
        when(client.command(anyString(), argThat(command -> command != null && command.type() == CommandType.BEGIN_DRAIN), anyMap()))
            .thenReturn(new CommandResult(readback(State.DRAINING, 2, "replace:release-new", null),
                readback(State.DRAINING, 2, "replace:release-new", null), false));
        when(client.snapshot(anyString(), any(), anyMap())).thenReturn(snapshot(List.of()));
        when(client.command(anyString(), argThat(command -> command != null && command.type() == CommandType.FENCE_AUTHORITY), anyMap()))
            .thenReturn(new CommandResult(readback(State.AUTHORITY_REVOKED, 3, "replace:release-new", null),
                readback(State.AUTHORITY_REVOKED, 3, "replace:release-new", null), false));

        var receipt = service.drainAndFence(deployment, next, "release-old", "https://old.example",
            Duration.ofMinutes(1), Duration.ofSeconds(1), null);
        assertThat(receipt.previousTarget()).isEqualTo(previous);
        assertThat(receipt.nextTarget().releaseId()).isEqualTo("release-new");
        assertThat(receipt.fencedEpoch()).isEqualTo(3);
        assertThat(receipt.requestFingerprint()).hasSize(64);
        assertThat(receipt.toString()).doesNotContain("private");

        when(client.transition(anyString(), any(), anyMap())).thenAnswer(invocation -> {
            GenerationTransition command = invocation.getArgument(1);
            return new GenerationTransitionResult(readback(State.AUTHORITY_REVOKED, 3,
                "replace:release-new", command.requestFingerprint()),
                new Readback(command.nextTarget(), "tenant-1", State.ACTIVE, 4, null, null,
                    null, false, false, now), command.providerStopEvidenceReference(), now, false);
        });
        var result = service.adoptAfterProviderStop(deployment, next, receipt, "https://new.example",
            "c".repeat(64), "coolify-stop:runtime-uuid:2026-10-14T12:00:00Z");
        assertThat(result.current().target().releaseId()).isEqualTo("release-new");
        assertThat(result.current().fenceEpoch()).isEqualTo(4);
        verify(authority, atLeastOnce()).issueGenerationTransitionHeaders(deployment, next, "release-old", "a".repeat(64), "replace:release-new");
        verify(authority, never()).issueSystemHeaders(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test void remoteObligationsRequireAuthoritativeEvidenceBeforeFencing() {
        when(client.status(anyString(), eq(previous), anyMap())).thenReturn(readback(State.ACTIVE, 1, null, null));
        when(client.command(anyString(), argThat(command -> command != null && command.type() == CommandType.BEGIN_DRAIN), anyMap()))
            .thenReturn(new CommandResult(readback(State.DRAINING, 2, "replace:release-new", null),
                readback(State.DRAINING, 2, "replace:release-new", null), false));
        when(client.snapshot(anyString(), any(), anyMap())).thenReturn(snapshot(List.of(
            new RemoteObligation("CONNECTOR", previous, "b".repeat(64), "R5"))));

        assertThatThrownBy(() -> service.drainAndFence(deployment, next, "release-old", "https://old.example",
            Duration.ofMinutes(1), Duration.ofSeconds(1), null))
            .hasMessage("RUNTIME_GENERATION_REMOTE_DEPENDENCY_EVIDENCE_REQUIRED");
        verify(client, never()).command(anyString(), argThat(command -> command != null && command.type() == CommandType.FENCE_AUTHORITY), anyMap());

        assertThatThrownBy(() -> service.drainAndFence(deployment, next, "release-old", "https://old.example",
            Duration.ofMinutes(1), Duration.ofSeconds(1),
            new DeploymentRuntimeGenerationService.RemoteDependencyEvidence(Map.of("HUMAN_INBOX", "inbox:stopped"))))
            .hasMessage("RUNTIME_GENERATION_REMOTE_DEPENDENCY_EVIDENCE_REQUIRED");
    }

    @Test void incompleteDrainOrForeignAuthorityFailsClosed() {
        when(client.status(anyString(), eq(previous), anyMap())).thenReturn(readback(State.ACTIVE, 1, null, null));
        when(client.command(anyString(), any(), anyMap())).thenReturn(new CommandResult(
            readback(State.DRAINING, 2, "replace:release-new", null), readback(State.DRAINING, 2, "replace:release-new", null), false));
        Snapshot incomplete = new Snapshot("snap", "evidence", readback(State.DRAINING, 2, "replace:release-new", null),
            now, zero(), zero(), zero(), zero(), 0, true, false, false, false, false,
            "b".repeat(64), List.of(), List.of("WORKER_FENCE_UNVERIFIED"));
        when(client.snapshot(anyString(), any(), anyMap())).thenReturn(incomplete);
        assertThatThrownBy(() -> service.drainAndFence(deployment, next, "release-old", "https://old.example",
            Duration.ofSeconds(10), Duration.ofSeconds(1), null)).hasMessage("RUNTIME_GENERATION_DRAIN_INCOMPLETE");

        RuntimeAuthoritySnapshotEntity foreign = new RuntimeAuthoritySnapshotEntity();
        foreign.setDeploymentId("dep-other"); foreign.setTargetProfileId("staging"); foreign.setReleaseId("release-old");
        foreign.setFingerprint("a".repeat(64));
        when(snapshots.findByReleaseId("release-old")).thenReturn(Optional.of(foreign));
        assertThatThrownBy(() -> service.drainAndFence(deployment, next, "release-old", "https://old.example",
            Duration.ofSeconds(10), Duration.ofSeconds(1), null))
            .hasMessage("RUNTIME_GENERATION_PREVIOUS_AUTHORITY_SCOPE_INVALID");
    }

    private Readback readback(State state, long fence, String operation, String fingerprint) {
        return new Readback(previous, "tenant-1", state, fence, operation,
            fingerprint == null && operation != null ? fingerprintForOperation() : fingerprint,
            state == State.ACTIVE ? null : now.plusSeconds(60), state == State.AUTHORITY_REVOKED,
            false, now);
    }

    private String fingerprintForOperation() {
        try {
            var method = DeploymentRuntimeGenerationService.class.getDeclaredMethod("digest", String.class);
            method.setAccessible(true);
            String value = String.join("\n", "customer-1", "tenant-1", "dep-1", "staging",
                "release-old", "release-new", "replace:release-new");
            return (String) method.invoke(service, value);
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private Snapshot snapshot(List<RemoteObligation> obligations) {
        return new Snapshot("snapshot-1", "runtime-target-drain:snapshot-1",
            readback(State.DRAINING, 2, "replace:release-new", fingerprintForOperation()), now,
            zero(), zero(), zero(), zero(), 0, true, true, true, obligations.isEmpty(), false,
            "b".repeat(64), obligations, List.of());
    }

    private Metric zero() { return new Metric(0L, true, List.of()); }
}
