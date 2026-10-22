package com.ai.fabric.platform.backend.deployment.retirement;

import com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationTransitionContracts;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationTransitionContracts.CommandType;
import static com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.*;

@Service
public class RuntimeDrainTargetRetirementParticipant implements TargetRetirementParticipant {
    private static final Set<String> TRANSIENT_DRAIN_BLOCKERS = Set.of(
        "DRAIN_WORK_REMAINS", "IN_FLIGHT_OR_UNSETTLED_RUNTIME_TURNS", "TARGET_INGRESS_NOT_DRAINED");

    private final TargetRetirementRuntimeGateway runtime;

    public RuntimeDrainTargetRetirementParticipant(TargetRetirementRuntimeGateway runtime) {
        this.runtime = runtime;
    }

    @Override public ParticipantKind kind() { return ParticipantKind.RUNTIME_DRAIN; }

    @Override
    public Evidence inspect(Context context) {
        var status = runtime.status(context);
        if (status.state() == RuntimeGenerationTransitionContracts.State.ACTIVE) {
            return evidence(context, "runtime-target-retirement:drain-not-started", false, false,
                false, null, null, null, null, List.of("DRAIN_NOT_STARTED"));
        }
        var snapshot = runtime.snapshot(context);
        Long background = sum(snapshot.backgroundWork().count(), snapshot.inFlightRequests());
        List<String> blockers = snapshot.blockers().stream()
            .filter(code -> !TRANSIENT_DRAIN_BLOCKERS.contains(code)).distinct().toList();
        return evidence(context, snapshot.evidenceReference(), snapshot.localDrainComplete(), true,
            snapshot.inventoryComplete() && snapshot.workerFencingComplete(),
            snapshot.openConversations().count(), snapshot.pendingConfirmations().count(),
            snapshot.humanWork().count(), background, blockers);
    }

    @Override
    public EffectResult apply(Context context, Step step, String effectKey) {
        if (step == Step.DRAIN) {
            var current = runtime.status(context);
            if (current.state() != RuntimeGenerationTransitionContracts.State.ACTIVE) {
                return result(current.state() == RuntimeGenerationTransitionContracts.State.DRAINING,
                    "runtime-target-retirement:drain:" + current.fenceEpoch());
            }
            var applied = runtime.command(context, CommandType.BEGIN_DRAIN, null).applied();
            return result(applied.state() == RuntimeGenerationTransitionContracts.State.DRAINING,
                "runtime-target-retirement:drain:" + applied.fenceEpoch());
        }
        if (step == Step.CANCEL_DRAIN) {
            var current = runtime.status(context);
            if (current.state() == RuntimeGenerationTransitionContracts.State.ACTIVE) {
                return result(true, "runtime-target-retirement:drain-cancelled:" + current.fenceEpoch());
            }
            var applied = runtime.command(context, CommandType.CANCEL_DRAIN, null).applied();
            return result(applied.state() == RuntimeGenerationTransitionContracts.State.ACTIVE,
                "runtime-target-retirement:drain-cancelled:" + applied.fenceEpoch());
        }
        throw new TargetRetirementFailure("RUNTIME_DRAIN_STEP_UNSUPPORTED");
    }

    @Override
    public EffectResult readback(Context context, Step step, String effectKey) {
        var current = runtime.status(context);
        if (step == Step.DRAIN) {
            boolean accepted = current.state() != RuntimeGenerationTransitionContracts.State.ACTIVE;
            return result(accepted, "runtime-target-retirement:drain-readback:" + current.fenceEpoch());
        }
        if (step == Step.CANCEL_DRAIN) {
            return result(current.state() == RuntimeGenerationTransitionContracts.State.ACTIVE,
                "runtime-target-retirement:drain-cancel-readback:" + current.fenceEpoch());
        }
        throw new TargetRetirementFailure("RUNTIME_DRAIN_STEP_UNSUPPORTED");
    }

    @Override
    public boolean recoverySafe(Context context, Step step, String effectKey) {
        if (step != Step.DRAIN) return false;
        // An exact unbound ACTIVE readback permits the same fenced, idempotent BEGIN_DRAIN.
        // The gateway rejects foreign bindings; a late command is reconciled before any reissue.
        return runtime.status(context).state() == RuntimeGenerationTransitionContracts.State.ACTIVE;
    }

    private Evidence evidence(Context context, String reference, boolean complete, boolean ingressBlocked,
                              boolean inventoryComplete, Long conversations, Long confirmations,
                              Long humanWork, Long backgroundWork, List<String> blockers) {
        return new Evidence(context.requestFingerprint(), context.fenceEpoch(), Instant.now(), reference,
            complete, ingressBlocked, inventoryComplete, true, conversations, confirmations,
            humanWork, backgroundWork, complete, blockers);
    }

    private EffectResult result(boolean complete, String reference) {
        return new EffectResult(complete, reference, Instant.now());
    }

    private Long sum(Long value, long addition) {
        if (value == null) return null;
        try { return Math.addExact(value, addition); }
        catch (ArithmeticException error) { throw new TargetRetirementFailure("DRAIN_WORK_COUNT_INVALID"); }
    }
}
