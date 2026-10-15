package com.ai.fabric.platform.backend.deployment.retirement;

import com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationTransitionContracts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationTransitionContracts.*;
import static com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class RuntimeTargetRetirementParticipantTest {
    private final TargetRetirementRuntimeGateway runtime = mock(TargetRetirementRuntimeGateway.class);
    private final Context context = context();
    private final Target target = new Target("deployment", "target", "release");
    private RuntimeDrainTargetRetirementParticipant drain;
    private RuntimeAuthorityTargetRetirementParticipant authority;

    @BeforeEach void setup() {
        drain = new RuntimeDrainTargetRetirementParticipant(runtime);
        authority = new RuntimeAuthorityTargetRetirementParticipant(runtime);
    }

    @Test void activeWorkRemainsExplicitAndIsNotHiddenByDynamicRuntimeBlockers() {
        when(runtime.status(context)).thenReturn(readback(RuntimeGenerationTransitionContracts.State.DRAINING, false, 4));
        when(runtime.snapshot(context)).thenReturn(snapshot(2L, 0L, 0L, 0L, 1,
            false, List.of("DRAIN_WORK_REMAINS", "IN_FLIGHT_OR_UNSETTLED_RUNTIME_TURNS"), List.of()));

        Evidence evidence = drain.inspect(context);

        assertThat(evidence.blockers()).isEmpty();
        assertThat(evidence.conversations()).isEqualTo(2);
        assertThat(evidence.backgroundWork()).isEqualTo(1);
        assertThat(evidence.complete()).isFalse();
        var participation = new TargetRetirementParticipation(List.of(drain, authority,
            mockParticipant(ParticipantKind.INDEX), mockParticipant(ParticipantKind.DEPENDENCIES)), List.of());
        assertThatThrownBy(() -> participation.requireDrained(evidence)).hasMessageContaining("ACTIVE_TARGET_WORK");
    }

    @Test void structuralInventoryBlockerRemainsFailClosed() {
        when(runtime.status(context)).thenReturn(readback(RuntimeGenerationTransitionContracts.State.DRAINING, false, 4));
        when(runtime.snapshot(context)).thenReturn(snapshot(null, 0L, 0L, 0L, 0,
            false, List.of("DRAIN_STORE_SCHEMA_UNAVAILABLE:chat_sessions"), List.of()));

        assertThat(drain.inspect(context).blockers())
            .containsExactly("DRAIN_STORE_SCHEMA_UNAVAILABLE:chat_sessions");
    }

    @Test void authorityRefusesToTreatRuntimeSnapshotAsExternalObligationProof() {
        when(runtime.status(context)).thenReturn(readback(RuntimeGenerationTransitionContracts.State.DRAINING, false, 4));
        var obligation = new RemoteObligation("inbox", target, "topology", "drain externally");
        when(runtime.snapshot(context)).thenReturn(snapshot(0L, 0L, 0L, 0L, 0,
            true, List.of(), List.of(obligation)));

        assertThatThrownBy(() -> authority.apply(context, Step.AUTHORITY, "effect"))
            .hasMessageContaining("AUTHORITATIVE_REMOTE_READBACK_REQUIRED");
        verify(runtime, never()).command(any(), any(), any());
    }

    @Test void beginDrainUsesRuntimeCommandAndReadsBackAcceptedState() {
        when(runtime.status(context)).thenReturn(readback(RuntimeGenerationTransitionContracts.State.ACTIVE, false, 3),
            readback(RuntimeGenerationTransitionContracts.State.DRAINING, false, 4));
        when(runtime.command(context, CommandType.BEGIN_DRAIN, null)).thenReturn(new CommandResult(
            readback(RuntimeGenerationTransitionContracts.State.DRAINING, false, 4),
            readback(RuntimeGenerationTransitionContracts.State.DRAINING, false, 4), false));

        assertThat(drain.apply(context, Step.DRAIN, "effect").complete()).isTrue();
        assertThat(drain.readback(context, Step.DRAIN, "effect").complete()).isTrue();
    }

    private TargetRetirementParticipant mockParticipant(ParticipantKind kind) {
        var participant = mock(TargetRetirementParticipant.class);
        when(participant.kind()).thenReturn(kind);
        return participant;
    }

    private Readback readback(RuntimeGenerationTransitionContracts.State state, boolean revoked, long fence) {
        return new Readback(target, "tenant", state, fence, state == RuntimeGenerationTransitionContracts.State.ACTIVE ? null : "operation",
            state == RuntimeGenerationTransitionContracts.State.ACTIVE ? null : "fingerprint", context.drainDeadline(), revoked, false, Instant.now());
    }

    private RuntimeGenerationTransitionContracts.Snapshot snapshot(Long conversations, Long confirmations,
            Long human, Long background, long inFlight, boolean localComplete, List<String> blockers,
            List<RemoteObligation> obligations) {
        return new RuntimeGenerationTransitionContracts.Snapshot("snapshot", "runtime-proof", readback(
            RuntimeGenerationTransitionContracts.State.DRAINING, false, 4), Instant.now(),
            metric(conversations), metric(confirmations), metric(human), metric(background), inFlight,
            conversations != null && confirmations != null && human != null && background != null,
            true, localComplete, localComplete && obligations.isEmpty(), false, "topology", obligations, blockers);
    }

    private Metric metric(Long count) { return new Metric(count, count != null, List.of()); }

    private static Context context() {
        var snapshot = new TargetRetirementContracts.Snapshot("customer", "tenant", "deployment", "target",
            "target-generation", 1, List.of(new ReleaseGeneration("release", "version", "generation")),
            List.of(), List.of());
        return new Context("operation", "operation-key", "fingerprint", snapshot, 2, "worker", "actor",
            "CUSTOMER_ADMIN", Instant.now().plusSeconds(60),
            new Retention(RetentionDecision.RECLAIM, null, null, "backup"));
    }
}
