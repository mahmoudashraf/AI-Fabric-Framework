package com.ai.fabric.platform.backend.deployment.retirement;

import com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationTransitionContracts;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentProviderResourceHandleEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentProviderResourceHandleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
    private final DeploymentProviderResourceHandleRepository handles = mock(DeploymentProviderResourceHandleRepository.class);
    private final TargetIndexRetirementParticipant participant = new TargetIndexRetirementParticipant(runtime, indexes, handles);
    private final Context retirement = context();

    @Test void frozenExternalVectorBindingBlocksEveryIndexEntryPointEvenIfItsRowDisappears() {
        var binding = new ResourceGeneration("external-binding", null, DeploymentProviderType.COOLIFY,
            "EXTERNAL_VECTOR_STORAGE_BINDING", "external-cluster", null, "binding-generation", true);
        var snapshot = retirement.snapshot();
        var scoped = new Snapshot(snapshot.customerId(), snapshot.tenantId(), snapshot.deploymentId(),
            snapshot.targetProfileId(), snapshot.targetGeneration(), snapshot.targetEpoch(),
            snapshot.releases(), List.of(binding), snapshot.consumers());
        var context = new Context(retirement.operationId(), retirement.operationKey(), retirement.requestFingerprint(),
            scoped, retirement.fenceEpoch(), retirement.workerToken(), retirement.requestedByActorId(),
            retirement.requestedByRole(), retirement.drainDeadline(), retirement.retention());

        assertBlocked(context);
        verifyNoInteractions(runtime, indexes, handles);
    }

    @ParameterizedTest
    @ValueSource(strings = {"RETAIN_EXTERNAL", "", "DELETE"})
    void currentExactTargetBindingBlocksAndChangedLifecycleCannotAuthorizeClear(String lifecycle) {
        var binding = binding("deployment", "target");
        binding.setMetadataJson(new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode()
            .put("lifecycle", lifecycle).toString());
        when(handles.findByDeploymentIdAndTargetProfileIdOrderByUpdatedAtDesc("deployment", "target"))
            .thenReturn(List.of(binding));

        assertBlocked(retirement);
        verifyNoInteractions(runtime, indexes);
    }

    @ParameterizedTest
    @ValueSource(strings = {"foreign-target", "foreign-deployment"})
    void aForeignExternalBindingDoesNotBlockThisTargetsActualIndexReadback(String foreignScope) {
        var binding = binding("deployment", "target");
        if (foreignScope.equals("foreign-target")) binding.setTargetProfileId("other-target");
        else binding.setDeploymentId("other-deployment");
        when(handles.findByDeploymentIdAndTargetProfileIdOrderByUpdatedAtDesc("deployment", "target"))
            .thenReturn(List.of(binding));
        when(runtime.status(retirement)).thenReturn(status(true,
            RuntimeGenerationTransitionContracts.State.AUTHORITY_REVOKED));
        when(indexes.readback(retirement)).thenReturn(readback(false, Map.of("document", 12L)));

        Evidence evidence = participant.inspect(retirement);

        assertThat(evidence.blockers()).isEmpty();
        assertThat(evidence.complete()).isFalse();
        verify(indexes).readback(retirement);
        verify(indexes, never()).retire(any());
    }

    private void assertBlocked(Context context) {
        var evidence = participant.inspect(context);
        assertThat(evidence.blockers()).containsExactly("EXTERNAL_VECTOR_INDEX_RETENTION_REQUIRED");
        assertThat(evidence.complete()).isFalse();
        assertThat(evidence.inventoryComplete()).isFalse();
        assertThat(evidence.retentionApproved()).isFalse();
        assertThat(evidence.durable()).isFalse();
        assertThat(evidence.conversations()).isNull();
        assertThat(evidence.backgroundWork()).isNull();
        assertThatThrownBy(() -> participant.apply(context, Step.INDEX, "effect"))
            .isInstanceOf(TargetRetirementFailure.class).hasMessageContaining("EXTERNAL_VECTOR_INDEX_RETENTION_REQUIRED");
        assertThatThrownBy(() -> participant.readback(context, Step.INDEX, "effect"))
            .isInstanceOf(TargetRetirementFailure.class).hasMessageContaining("EXTERNAL_VECTOR_INDEX_RETENTION_REQUIRED");
    }

    private DeploymentProviderResourceHandleEntity binding(String deploymentId, String targetId) {
        var binding = new DeploymentProviderResourceHandleEntity();
        binding.setId("external-binding"); binding.setDeploymentId(deploymentId); binding.setTargetProfileId(targetId);
        binding.setResourceKind("EXTERNAL_VECTOR_STORAGE_BINDING"); binding.setStatus("ACTIVE");
        binding.setMetadataJson("{\"lifecycle\":\"RETAIN_EXTERNAL\"}");
        return binding;
    }

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
