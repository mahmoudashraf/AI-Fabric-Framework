package com.ai.fabric.platform.backend.deployment.retirement;

import com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationTransitionContracts;
import com.ai.fabric.platform.backend.deployment.model.PreservedExternalBindingKinds;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentProviderResourceHandleRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

import static com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.*;

/** Retires the exact target's vector scope through its deployment-local runtime. */
@Service
public class TargetIndexRetirementParticipant implements TargetRetirementParticipant {
    public static final String EXTERNAL_VECTOR_INDEX_RETENTION_REQUIRED = "EXTERNAL_VECTOR_INDEX_RETENTION_REQUIRED";
    private final TargetRetirementRuntimeGateway runtime;
    private final TargetRetirementRuntimeIndexGateway indexes;
    private final DeploymentProviderResourceHandleRepository handles;

    public TargetIndexRetirementParticipant(TargetRetirementRuntimeGateway runtime,
                                            TargetRetirementRuntimeIndexGateway indexes,
                                            DeploymentProviderResourceHandleRepository handles) {
        this.runtime = runtime;
        this.indexes = indexes;
        this.handles = handles;
    }

    @Override public ParticipantKind kind() { return ParticipantKind.INDEX; }

    @Override
    public Evidence inspect(Context context) {
        if (retainedExternalIndexes(context)) {
            return new Evidence(context.requestFingerprint(), context.fenceEpoch(), Instant.now(),
                "external-vector-index-retention:blocked:" + context.snapshot().targetProfileId(),
                false, false, false, false, null, null, null, null, false,
                List.of(EXTERNAL_VECTOR_INDEX_RETENTION_REQUIRED));
        }
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
        requireReclamationAllowed(context);
        var current = indexes.readback(context);
        if (current.complete()) return result(current);
        return result(indexes.retire(context));
    }

    @Override
    public EffectResult readback(Context context, Step step, String effectKey) {
        requireStep(step);
        requireReclamationAllowed(context);
        return result(indexes.readback(context));
    }

    private void requireReclamationAllowed(Context context) {
        if (retainedExternalIndexes(context)) throw new TargetRetirementFailure(EXTERNAL_VECTOR_INDEX_RETENTION_REQUIRED);
    }

    private boolean retainedExternalIndexes(Context context) {
        var snapshot = context.snapshot();
        // This binding kind retains external data. Missing/changed lifecycle metadata cannot authorize a clear.
        if (snapshot.resources().stream().anyMatch(resource ->
            PreservedExternalBindingKinds.VECTOR_STORAGE.equals(resource.resourceKind()))) return true;
        return handles.findByDeploymentIdAndTargetProfileIdOrderByUpdatedAtDesc(
            snapshot.deploymentId(), snapshot.targetProfileId()).stream().anyMatch(handle ->
                snapshot.deploymentId().equals(handle.getDeploymentId())
                    && snapshot.targetProfileId().equals(handle.getTargetProfileId())
                    && PreservedExternalBindingKinds.VECTOR_STORAGE.equals(handle.getResourceKind()));
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
