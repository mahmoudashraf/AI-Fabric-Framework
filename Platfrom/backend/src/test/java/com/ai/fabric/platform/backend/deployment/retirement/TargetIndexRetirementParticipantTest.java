package com.ai.fabric.platform.backend.deployment.retirement;

import com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationTransitionContracts;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class TargetIndexRetirementParticipantTest {
    private final TargetRetirementRuntimeGateway runtime = mock(TargetRetirementRuntimeGateway.class);
    private final TargetRetirementRuntimeIndexGateway indexes = mock(TargetRetirementRuntimeIndexGateway.class);
    private final TargetIndexRetirementParticipant participant = new TargetIndexRetirementParticipant(runtime, indexes);
    private final Context retirement = context();

    @Test void inspectionWaitsUntilRuntimeAuthorityIsRevoked() {
        when(runtime.status(retirement)).thenReturn(status(false,
            RuntimeGenerationTransitionContracts.State.DRAINING));

        Evidence evidence = participant.inspect(retirement);

        assertThat(evidence.complete()).isFalse();
        assertThat(evidence.reference()).isEqualTo("runtime-target-index:pending");
        verifyNoInteractions(indexes);
    }

    @Test void inspectionUsesOnlyValidatedRuntimeReadbackAfterAuthorityRevocation() {
        when(runtime.status(retirement)).thenReturn(status(true,
            RuntimeGenerationTransitionContracts.State.AUTHORITY_REVOKED));
        when(indexes.readback(retirement)).thenReturn(readback(true, Map.of(
            "dealer-vehicle", 0L, "document", 0L)));

        Evidence evidence = participant.inspect(retirement);

        assertThat(evidence.complete()).isTrue();
        assertThat(evidence.reference()).isEqualTo("runtime-target-index:evidence");
    }

    @Test void applyIsIdempotentAndDoesNotIssueAnotherDeleteWhenReadbackIsComplete() {
        when(indexes.readback(retirement)).thenReturn(readback(true, Map.of("dealer-vehicle", 0L)));

        EffectResult result = participant.apply(retirement, Step.INDEX, "effect");

        assertThat(result.complete()).isTrue();
        verify(indexes, never()).retire(any());
    }

    @Test void applyUsesTargetScopedRuntimeProtocolWhenVectorsRemain() {
        when(indexes.readback(retirement)).thenReturn(readback(false, Map.of("dealer-vehicle", 12L)));
        when(indexes.retire(retirement)).thenReturn(readback(true, Map.of("dealer-vehicle", 0L)));

        EffectResult result = participant.apply(retirement, Step.INDEX, "effect");

        assertThat(result.complete()).isTrue();
        verify(indexes).retire(retirement);
    }

    @Test void refusesAnyOtherRetirementStep() {
        assertThatThrownBy(() -> participant.apply(retirement, Step.DRAIN, "effect"))
            .hasMessageContaining("INDEX_RETIREMENT_STEP_UNSUPPORTED");
    }

    private RuntimeGenerationTransitionContracts.Readback status(boolean revoked,
        RuntimeGenerationTransitionContracts.State state) {
        return new RuntimeGenerationTransitionContracts.Readback(
            new RuntimeGenerationTransitionContracts.Target("deployment", "target", "release"),
            "tenant", state, 8, "operation", "fingerprint", retirement.drainDeadline(),
            revoked, false, Instant.now());
    }

    private TargetRetirementRuntimeIndexGateway.IndexReadback readback(boolean complete,
        Map<String, Long> counts) {
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        return new TargetRetirementRuntimeIndexGateway.IndexReadback(
            new RuntimeGenerationTransitionContracts.Target("deployment", "target", "release"),
            "tenant", "operation", 8, "topology", "qdrant", "COLLECTION_PREFIX", "deployment-target",
            counts, total, complete ? 12 : 0, Instant.now(), "runtime-target-index:evidence", complete, false);
    }

    private Context context() {
        var snapshot = new Snapshot("customer", "tenant", "deployment", "target", "target-generation", 1,
            List.of(new ReleaseGeneration("release", "version", "generation")), List.of(), List.of());
        return new Context("operation", "operation-key", "fingerprint", snapshot, 2, "worker", "actor",
            "CUSTOMER_ADMIN", Instant.now().plusSeconds(60),
            new Retention(RetentionDecision.RECLAIM, null, null, "backup"));
    }
}
