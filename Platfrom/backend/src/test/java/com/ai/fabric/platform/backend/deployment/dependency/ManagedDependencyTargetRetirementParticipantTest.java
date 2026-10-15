package com.ai.fabric.platform.backend.deployment.dependency;

import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementFailure;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ManagedDependencyTargetRetirementParticipantTest {
    private final ManagedDependencyGraphStore graphs = mock(ManagedDependencyGraphStore.class);
    private final ManagedDependencyRetirementAdapter adapter = mock(ManagedDependencyRetirementAdapter.class);
    private final ManagedDependencyTargetRetirementParticipant participant =
        new ManagedDependencyTargetRetirementParticipant(graphs, adapter);

    @Test void absentGraphIsAnExplicitCompleteEmptyInventory() {
        when(graphs.find("deployment", "target")).thenReturn(Optional.empty());
        assertThat(participant.inspect(context()).complete()).isTrue();
        assertThat(participant.apply(context(), Step.DEPENDENCIES, "effect").complete()).isTrue();
        verifyNoInteractions(adapter);
    }

    @Test void foreignGraphOwnershipCannotBeRetired() {
        var graph = graph();
        graph.setCustomerId("foreign");
        when(graphs.find("deployment", "target")).thenReturn(Optional.of(graph));
        assertThatThrownBy(() -> participant.inspect(context()))
            .isInstanceOf(TargetRetirementFailure.class).hasMessageContaining("DEPENDENCY_SCOPE_CHANGED");
        verifyNoInteractions(adapter);
    }

    @Test void exactPlanAndOperationKeyArePassedToOwnedRetirementAdapter() {
        var graph = graph();
        when(graphs.find("deployment", "target")).thenReturn(Optional.of(graph));
        var plan = new ManagedDependencyRetirementAdapter.Plan("graph", 7, "plan-fingerprint", List.of(), List.of());
        when(adapter.planForTargetRetirement("deployment", "target")).thenReturn(plan);
        var summary = new ManagedDependencyLifecycleService.Summary("graph", "deployment", "target", "version",
            "release", "spec", "binding", 8, "RETIRED", "COMPLETE", null, 1, 1, 1, List.of(), List.of());
        when(adapter.retireForTargetRetirement("deployment", "target", 7, "plan-fingerprint", "operation-key"))
            .thenReturn(summary);

        assertThat(participant.apply(context(), Step.DEPENDENCIES, "effect").complete()).isTrue();
        verify(adapter).retireForTargetRetirement("deployment", "target", 7, "plan-fingerprint", "operation-key");
    }

    @Test void recoverySafetyDelegatesToTheOperationBoundAdapterProof() {
        when(adapter.canResumeForTargetRetirement("deployment", "target", "operation-key")).thenReturn(true);
        assertThat(participant.recoverySafe(context(), Step.DEPENDENCIES, "effect")).isTrue();
        assertThat(participant.recoverySafe(context(), Step.AUTHORITY, "effect")).isFalse();
    }

    private ManagedDependencyGraphEntity graph() {
        var value = new ManagedDependencyGraphEntity();
        value.setId("graph");
        value.setDeploymentId("deployment");
        value.setTargetProfileId("target");
        value.setCustomerId("customer");
        value.setTenantId("tenant");
        value.setStatus("READY");
        value.setRevision(7);
        return value;
    }

    private Context context() {
        var snapshot = new TargetRetirementContracts.Snapshot("customer", "tenant", "deployment", "target",
            "target-generation", 1, List.of(new ReleaseGeneration("release", "version", "generation")),
            List.of(), List.of());
        return new Context("operation", "operation-key", "fingerprint", snapshot, 2, "worker", "actor",
            "CUSTOMER_ADMIN", Instant.now().plusSeconds(60),
            new Retention(RetentionDecision.RECLAIM, null, null, "backup"));
    }
}
