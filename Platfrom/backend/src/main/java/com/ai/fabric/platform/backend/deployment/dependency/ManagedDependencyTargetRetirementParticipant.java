package com.ai.fabric.platform.backend.deployment.dependency;

import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementFailure;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementParticipant;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.*;

@Service
public class ManagedDependencyTargetRetirementParticipant implements TargetRetirementParticipant {
    private final ManagedDependencyGraphStore graphs;
    private final ManagedDependencyRetirementAdapter adapter;

    public ManagedDependencyTargetRetirementParticipant(ManagedDependencyGraphStore graphs,
                                                         ManagedDependencyRetirementAdapter adapter) {
        this.graphs = graphs;
        this.adapter = adapter;
    }

    @Override public ParticipantKind kind() { return ParticipantKind.DEPENDENCIES; }

    @Override
    public Evidence inspect(Context context) {
        var graph = graphs.find(context.snapshot().deploymentId(), context.snapshot().targetProfileId());
        if (graph.isEmpty()) return evidence(context, true, "managed-dependencies:empty", List.of());
        var value = graph.get();
        if (!context.snapshot().customerId().equals(value.getCustomerId())
            || !context.snapshot().tenantId().equals(value.getTenantId())) {
            throw new TargetRetirementFailure("DEPENDENCY_SCOPE_CHANGED");
        }
        if ("RETIRED".equals(value.getStatus())) {
            return evidence(context, true, "managed-dependencies:" + value.getId() + ":retired", List.of());
        }
        var plan = adapter.planForTargetRetirement(context.snapshot().deploymentId(), context.snapshot().targetProfileId());
        List<String> blockers = new ArrayList<>(plan.blockers());
        if ("RETIREMENT_BLOCKED".equals(value.getStatus())) blockers.add("DEPENDENCY_RETIREMENT_UNCONFIRMED");
        return evidence(context, false, "managed-dependencies:" + value.getId() + ":revision:" + value.getRevision(),
            blockers.stream().distinct().toList());
    }

    @Override
    public EffectResult apply(Context context, Step step, String effectKey) {
        if (step != Step.DEPENDENCIES) throw new TargetRetirementFailure("DEPENDENCY_RETIREMENT_STEP_UNSUPPORTED");
        var graph = graphs.find(context.snapshot().deploymentId(), context.snapshot().targetProfileId());
        if (graph.isEmpty()) return result(true, "managed-dependencies:empty");
        if ("RETIRED".equals(graph.get().getStatus())) return result(true, "managed-dependencies:" + graph.get().getId() + ":retired");
        var plan = adapter.planForTargetRetirement(context.snapshot().deploymentId(), context.snapshot().targetProfileId());
        if (!plan.blockers().isEmpty()) throw new TargetRetirementFailure(plan.blockers().getFirst());
        var summary = adapter.retireForTargetRetirement(context.snapshot().deploymentId(),
            context.snapshot().targetProfileId(), plan.expectedRevision(), plan.fingerprint(), context.operationKey());
        if (!"RETIRED".equals(summary.status())) throw new TargetRetirementFailure(
            summary.failureCode() == null ? "DEPENDENCY_RETIREMENT_UNCONFIRMED" : summary.failureCode());
        return result(true, "managed-dependencies:" + summary.graphId() + ":retired");
    }

    @Override
    public EffectResult readback(Context context, Step step, String effectKey) {
        if (step != Step.DEPENDENCIES) throw new TargetRetirementFailure("DEPENDENCY_RETIREMENT_STEP_UNSUPPORTED");
        var graph = graphs.find(context.snapshot().deploymentId(), context.snapshot().targetProfileId());
        if (graph.isEmpty()) return result(true, "managed-dependencies:empty");
        return result("RETIRED".equals(graph.get().getStatus()), "managed-dependencies:" + graph.get().getId()
            + ":" + graph.get().getStatus().toLowerCase(java.util.Locale.ROOT));
    }

    private Evidence evidence(Context context, boolean complete, String reference, List<String> blockers) {
        return new Evidence(context.requestFingerprint(), context.fenceEpoch(), Instant.now(), reference,
            complete, true, true, true, null, null, null, null, complete, blockers);
    }

    private EffectResult result(boolean complete, String reference) {
        return new EffectResult(complete, reference, Instant.now());
    }
}
