package com.ai.fabric.platform.backend.deployment.retirement;

import com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationTransitionContracts;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

import static com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationTransitionContracts.CommandType;
import static com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.*;

@Service
public class RuntimeAuthorityTargetRetirementParticipant implements TargetRetirementParticipant {
    private final TargetRetirementRuntimeGateway runtime;

    public RuntimeAuthorityTargetRetirementParticipant(TargetRetirementRuntimeGateway runtime) {
        this.runtime = runtime;
    }

    @Override public ParticipantKind kind() { return ParticipantKind.AUTHORITY; }

    @Override
    public Evidence inspect(Context context) {
        var current = runtime.status(context);
        boolean complete = current.authorityRevoked()
            || current.state() == RuntimeGenerationTransitionContracts.State.RECLAIM_STARTED;
        return new Evidence(context.requestFingerprint(), context.fenceEpoch(), Instant.now(),
            "runtime-target-retirement:authority:" + current.fenceEpoch(), complete,
            current.state() != RuntimeGenerationTransitionContracts.State.ACTIVE, true, true,
            null, null, null, null, complete, List.of());
    }

    @Override
    public EffectResult apply(Context context, Step step, String effectKey) {
        if (step != Step.AUTHORITY) throw new TargetRetirementFailure("RUNTIME_AUTHORITY_STEP_UNSUPPORTED");
        var current = runtime.status(context);
        if (current.authorityRevoked()) return result(true, current.fenceEpoch());
        var snapshot = runtime.snapshot(context);
        if (!snapshot.localDrainComplete()) throw new TargetRetirementFailure("DRAIN_NOT_PROVED_COMPLETE");
        if (!snapshot.remoteObligations().isEmpty()) {
            throw new TargetRetirementFailure("AUTHORITATIVE_REMOTE_READBACK_REQUIRED");
        }
        var applied = runtime.command(context, CommandType.FENCE_AUTHORITY, null).applied();
        return result(applied.authorityRevoked(), applied.fenceEpoch());
    }

    @Override
    public EffectResult readback(Context context, Step step, String effectKey) {
        if (step != Step.AUTHORITY) throw new TargetRetirementFailure("RUNTIME_AUTHORITY_STEP_UNSUPPORTED");
        var current = runtime.status(context);
        return result(current.authorityRevoked(), current.fenceEpoch());
    }

    private EffectResult result(boolean complete, long fence) {
        return new EffectResult(complete, "runtime-target-retirement:authority-readback:" + fence, Instant.now());
    }
}
