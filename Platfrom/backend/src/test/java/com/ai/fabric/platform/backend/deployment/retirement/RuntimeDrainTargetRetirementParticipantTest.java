package com.ai.fabric.platform.backend.deployment.retirement;

import com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationTransitionContracts;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RuntimeDrainTargetRetirementParticipantTest {
    private final TargetRetirementRuntimeGateway runtime = mock(TargetRetirementRuntimeGateway.class);
    private final RuntimeDrainTargetRetirementParticipant participant = new RuntimeDrainTargetRetirementParticipant(runtime);
    private final Context context = mock(Context.class);

    @Test void exactActiveReadbackAllowsIdempotentDrainRecoveryWithoutIssuingACommand() {
        when(runtime.status(context)).thenReturn(readback(RuntimeGenerationTransitionContracts.State.ACTIVE));
        assertThat(participant.recoverySafe(context, Step.DRAIN, "effect")).isTrue();
        verify(runtime).status(context);
        verifyNoMoreInteractions(runtime);
    }

    @Test void missingOrForeignAuthorityNeverBecomesRecoveryPermission() {
        when(runtime.status(context)).thenThrow(new TargetRetirementFailure("RUNTIME_RETIREMENT_BINDING_CHANGED"));
        assertThatThrownBy(() -> participant.recoverySafe(context, Step.DRAIN, "effect"))
            .hasMessageContaining("RUNTIME_RETIREMENT_BINDING_CHANGED");
    }

    @Test void unknownRemoteReadbackNeverBecomesRecoveryPermission() {
        when(runtime.status(context)).thenThrow(new TargetRetirementFailure("RUNTIME_RETIREMENT_UNAVAILABLE"));
        assertThatThrownBy(() -> participant.recoverySafe(context, Step.DRAIN, "effect"))
            .hasMessageContaining("RUNTIME_RETIREMENT_UNAVAILABLE");
    }

    @Test void cancellationAndDestructiveStepsCannotUseActiveDrainRecovery() {
        for (var step : Step.values()) if (step != Step.DRAIN) {
            assertThat(participant.recoverySafe(context, step, "effect")).isFalse();
        }
        verifyNoInteractions(runtime);
    }

    @Test void lateAppliedDrainIsReadBackWithoutReissuingIt() {
        when(runtime.status(context)).thenReturn(readback(RuntimeGenerationTransitionContracts.State.DRAINING));
        assertThat(participant.readback(context, Step.DRAIN, "effect").complete()).isTrue();
        assertThat(participant.apply(context, Step.DRAIN, "effect").complete()).isTrue();
        verify(runtime, times(2)).status(context);
        verifyNoMoreInteractions(runtime);
    }

    private RuntimeGenerationTransitionContracts.Readback readback(RuntimeGenerationTransitionContracts.State state) {
        return new RuntimeGenerationTransitionContracts.Readback(
            new RuntimeGenerationTransitionContracts.Target("deployment", "target", "release"), "tenant", state,
            0, null, null, null, false, false, Instant.now());
    }
}
