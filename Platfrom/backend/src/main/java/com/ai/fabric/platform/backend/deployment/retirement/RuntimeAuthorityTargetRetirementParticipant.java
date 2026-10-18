package com.ai.fabric.platform.backend.deployment.retirement;

import com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationTransitionContracts;
import org.springframework.stereotype.Service;

import java.time.Instant;
import static com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationTransitionContracts.CommandType;
import static com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.*;

@Service
public class RuntimeAuthorityTargetRetirementParticipant implements TargetRetirementParticipant {
    private final TargetRetirementRuntimeGateway runtime;
    private final TargetRetirementConsumerAssignments assignments;

    public RuntimeAuthorityTargetRetirementParticipant(TargetRetirementRuntimeGateway runtime,
                                                       TargetRetirementConsumerAssignments assignments) {
        this.runtime = runtime;
        this.assignments = assignments;
    }

    @Override public ParticipantKind kind() { return ParticipantKind.AUTHORITY; }

    @Override
    public Evidence inspect(Context context) {
        var current = runtime.status(context);
        var assignment = assignments.inspect(context);
        boolean runtimeComplete = current.authorityRevoked()
            || current.state() == RuntimeGenerationTransitionContracts.State.RECLAIM_STARTED;
        boolean complete = runtimeComplete && assignment.complete();
        return new Evidence(context.requestFingerprint(), context.fenceEpoch(), Instant.now(),
            assignment.reference() + ":runtime:" + current.fenceEpoch(), complete,
            current.state() != RuntimeGenerationTransitionContracts.State.ACTIVE, true, true,
            null, null, null, null, complete, assignment.blockers());
    }

    @Override
    public EffectResult apply(Context context, Step step, String effectKey) {
        if (step != Step.AUTHORITY) throw new TargetRetirementFailure("RUNTIME_AUTHORITY_STEP_UNSUPPORTED");
        var current = runtime.status(context);
        var snapshot = runtime.snapshot(context);
        if (!snapshot.localDrainComplete()) throw new TargetRetirementFailure("DRAIN_NOT_PROVED_COMPLETE");
        if (!snapshot.remoteObligations().isEmpty()) {
            throw new TargetRetirementFailure("AUTHORITATIVE_REMOTE_READBACK_REQUIRED");
        }
        assignments.detach(context);
        if (current.authorityRevoked()) return result(context, true, current.fenceEpoch());
        var applied = runtime.command(context, CommandType.FENCE_AUTHORITY, null).applied();
        return result(context, applied.authorityRevoked(), applied.fenceEpoch());
    }

    @Override
    public EffectResult readback(Context context, Step step, String effectKey) {
        if (step != Step.AUTHORITY) throw new TargetRetirementFailure("RUNTIME_AUTHORITY_STEP_UNSUPPORTED");
        var current = runtime.status(context);
        return result(context, current.authorityRevoked(), current.fenceEpoch());
    }

    @Override
    public boolean recoverySafe(Context context, Step step, String effectKey) {
        if (step != Step.AUTHORITY) return false;
        var assignment = assignments.inspect(context);
        return assignment.blockers().isEmpty();
    }

    private EffectResult result(Context context, boolean runtimeComplete, long fence) {
        var assignment = assignments.inspect(context);
        if (!assignment.blockers().isEmpty()) throw new TargetRetirementFailure(assignment.blockers().getFirst());
        return new EffectResult(runtimeComplete && assignment.complete(),
            assignment.reference() + ":runtime-readback:" + fence, Instant.now());
    }
}
