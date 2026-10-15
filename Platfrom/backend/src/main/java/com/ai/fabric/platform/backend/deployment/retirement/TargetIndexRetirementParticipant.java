package com.ai.fabric.platform.backend.deployment.retirement;

import com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationTransitionContracts;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

import static com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.*;

/** Retires the exact target's vector scope through its deployment-local runtime. */
@Service
public class TargetIndexRetirementParticipant implements TargetRetirementParticipant {
    private final TargetRetirementRuntimeGateway runtime;
    private final TargetRetirementRuntimeIndexGateway indexes;

    public TargetIndexRetirementParticipant(TargetRetirementRuntimeGateway runtime,
                                            TargetRetirementRuntimeIndexGateway indexes) {
        this.runtime = runtime;
        this.indexes = indexes;
    }

    @Override public ParticipantKind kind() { return ParticipantKind.INDEX; }

    @Override
    public Evidence inspect(Context context) {
        var status = runtime.status(context);
        if (!status.authorityRevoked()
            && status.state() != RuntimeGenerationTransitionContracts.State.RECLAIM_STARTED) {
            return evidence(context, false, "runtime-target-index:pending", List.of());
        }
        var proof = indexes.readback(context);
        return evidence(context, proof.complete(), proof.evidenceReference(), List.of());
    }

    @Override
    public EffectResult apply(Context context, Step step, String effectKey) {
        requireStep(step);
        var current = indexes.readback(context);
        if (current.complete()) return result(current);
        return result(indexes.retire(context));
    }

    @Override
    public EffectResult readback(Context context, Step step, String effectKey) {
        requireStep(step);
        return result(indexes.readback(context));
    }

    private Evidence evidence(Context context, boolean complete, String reference, List<String> blockers) {
        return new Evidence(context.requestFingerprint(), context.fenceEpoch(), Instant.now(), reference,
            complete, true, true, true, null, null, null, null, complete, blockers);
    }

    private EffectResult result(TargetRetirementRuntimeIndexGateway.IndexReadback value) {
        return new EffectResult(value.complete(), value.evidenceReference(), value.observedAt());
    }

    private void requireStep(Step step) {
        if (step != Step.INDEX) throw new TargetRetirementFailure("INDEX_RETIREMENT_STEP_UNSUPPORTED");
    }
}
