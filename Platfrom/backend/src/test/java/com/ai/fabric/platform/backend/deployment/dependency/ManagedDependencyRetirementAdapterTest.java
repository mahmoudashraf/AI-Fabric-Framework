package com.ai.fabric.platform.backend.deployment.dependency;

import com.ai.fabric.platform.backend.deployment.service.CoolifyConnection;
import com.ai.fabric.platform.backend.deployment.service.CoolifyTargetProfileConfig;
import com.ai.fabric.platform.backend.deployment.service.PlatformManagedProductProvisioningService;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManagedDependencyRetirementAdapterTest {
    private final ManagedDependencyLifecycleService lifecycle = mock(ManagedDependencyLifecycleService.class);
    private final ManagedDependencyGraphStore store = mock(ManagedDependencyGraphStore.class);
    private final PlatformManagedProductProvisioningService provisioning =
        mock(PlatformManagedProductProvisioningService.class);
    private final PlatformSecretService secrets = mock(PlatformSecretService.class);
    private final ManagedDependencyPrerequisites prerequisites = mock(ManagedDependencyPrerequisites.class);
    private final ManagedDependencySpecValidator validator =
        new ManagedDependencySpecValidator(new ObjectMapper().findAndRegisterModules());
    private final ManagedDependencyGraphEntity graph = new ManagedDependencyGraphEntity();
    private final ManagedDependencyState state = new ManagedDependencyState();
    private final ManagedDependencyPrerequisites.Scope scope = new ManagedDependencyPrerequisites.Scope(
        "graph", "customer", "tenant", "deployment", "target", "version", "release",
        "a".repeat(64), "b".repeat(64));
    private ManagedDependencyRetirementAdapter adapter;

    @BeforeEach
    void setUp() {
        graph.setId("graph"); graph.setDeploymentId("deployment"); graph.setTargetProfileId("target");
        graph.setCustomerId("customer"); graph.setTenantId("tenant"); graph.setVersionId("version");
        graph.setReleaseId("release"); graph.setSpecFingerprint(scope.specFingerprint());
        graph.setBindingFingerprint(scope.bindingFingerprint()); graph.setStatus("READY");
        graph.setStep("COMPLETE"); graph.setRevision(7); graph.setReservationRef("reservation");
        var node = new ManagedDependencyState.Node();
        node.slot = "simulator"; node.serviceRef = "simulator-service";
        node.applicationUuid = "application"; node.applicationCreateIntent = true;
        node.status = "READY"; node.volumes.put("/app/data", "volume");
        state.nodes.add(node);

        when(lifecycle.find("deployment", "target")).thenReturn(graph);
        when(lifecycle.gate()).thenReturn(prerequisites);
        when(lifecycle.scope(graph)).thenReturn(scope);
        when(lifecycle.environment(graph)).thenReturn("staging");
        when(lifecycle.summary(graph)).thenAnswer(call -> new ManagedDependencyLifecycleService.Summary(
            graph.getId(), graph.getDeploymentId(), graph.getTargetProfileId(), graph.getVersionId(),
            graph.getReleaseId(), graph.getSpecFingerprint(), graph.getBindingFingerprint(), graph.getRevision(),
            graph.getStatus(), graph.getStep(), graph.getFailureCode(), graph.getAttempt(), 0, -1,
            java.util.List.of(), java.util.List.of()));
        when(store.read("graph")).thenReturn(graph);
        when(store.state(graph)).thenReturn(state);
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            BiConsumer<ManagedDependencyGraphEntity, ManagedDependencyState> mutation = invocation.getArgument(2);
            mutation.accept(graph, state);
            graph.setRevision(graph.getRevision() + 1);
            return null;
        }).when(store).mutate(eq("graph"), nullable(String.class), any());
        var connection = new CoolifyConnection("https://coolify.example.test", "private",
            new CoolifyTargetProfileConfig("https://coolify.example.test", "project", "staging",
                "environment", "server", "destination", "example.test", null, 1, 10,
                false, true, "8080", "/actuator/health", "8080"));
        when(provisioning.ownedDependencyConnection("target", "staging")).thenReturn(connection);
        doAnswer(invocation -> {
            var resources = invocation.getArgument(4,
                PlatformManagedProductProvisioningService.OwnedDependencyResources.class);
            var checkpoint = invocation.getArgument(5,
                PlatformManagedProductProvisioningService.OwnedDependencyCheckpoint.class);
            checkpoint.guard();
            checkpoint.volumeReclamationPlanned(Map.of("volume", "simulator-data"));
            checkpoint.volumesReclaimed(Set.copyOf(resources.volumes().values()));
            checkpoint.guard();
            return null;
        }).when(provisioning).retireOwnedDependency(eq(connection), eq("graph"),
            eq("simulator-service"), eq("simulator"), any(), any());
        doAnswer(invocation -> {
            assertThat(graph.getStatus()).isEqualTo("RETIRING");
            return null;
        }).when(prerequisites).requireUnused(eq(scope), anyLong(), eq("retire-operation"));
        adapter = new ManagedDependencyRetirementAdapter(lifecycle, store, provisioning, secrets, validator);
    }

    @Test
    void retirementTransitionsBeforeDrainProofAndPersistsVolumeReceipt() {
        var plan = adapter.planForTargetRetirement("deployment", "target");

        adapter.retireForTargetRetirement("deployment", "target", plan.expectedRevision(),
            plan.fingerprint(), "retire-operation");

        assertThat(graph.getStatus()).isEqualTo("RETIRED");
        assertThat(graph.getWorkerToken()).isNull();
        assertThat(state.nodes.getFirst().status).isEqualTo("RETIRED");
        assertThat(state.nodes.getFirst().retirementOperationKey).isEqualTo("retire-operation");
        assertThat(state.nodes.getFirst().volumeReclamationPlan).containsEntry("volume", "simulator-data");
        assertThat(state.nodes.getFirst().reclaimedVolumeUuids).containsExactly("volume");
        verify(prerequisites).verifyReclaimed(eq(scope), any());
        verify(prerequisites).releaseReservation(eq(scope), any());
    }

    @Test
    void sameOperationResumesAfterPartialFailureDespiteRevisionAndPlanChanges() {
        var attempts = new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(invocation -> {
            var checkpoint = invocation.getArgument(5,
                PlatformManagedProductProvisioningService.OwnedDependencyCheckpoint.class);
            checkpoint.guard();
            checkpoint.volumeReclamationPlanned(Map.of("volume", "simulator-data"));
            if (attempts.getAndIncrement() == 0) throw new IllegalStateException("provider timeout");
            checkpoint.volumesReclaimed(Set.of("volume"));
            return null;
        }).when(provisioning).retireOwnedDependency(eq(new CoolifyConnection("https://coolify.example.test", "private",
            new CoolifyTargetProfileConfig("https://coolify.example.test", "project", "staging",
                "environment", "server", "destination", "example.test", null, 1, 10,
                false, true, "8080", "/actuator/health", "8080"))), eq("graph"),
            eq("simulator-service"), eq("simulator"), any(), any());

        var firstPlan = adapter.planForTargetRetirement("deployment", "target");
        var first = adapter.retireForTargetRetirement("deployment", "target", firstPlan.expectedRevision(),
            firstPlan.fingerprint(), "retire-operation");
        assertThat(first.status()).isEqualTo("RETIREMENT_BLOCKED");
        assertThat(state.nodes.getFirst().volumeReclamationPlan).containsEntry("volume", "simulator-data");
        assertThat(adapter.canResumeForTargetRetirement("deployment", "target", "retire-operation")).isTrue();
        assertThat(adapter.canResumeForTargetRetirement("deployment", "target", "other-operation")).isFalse();

        var retryPlan = adapter.planForTargetRetirement("deployment", "target");
        assertThat(retryPlan.expectedRevision()).isNotEqualTo(firstPlan.expectedRevision());
        var retried = adapter.retireForTargetRetirement("deployment", "target", retryPlan.expectedRevision(),
            retryPlan.fingerprint(), "retire-operation");

        assertThat(retried.status()).isEqualTo("RETIRED");
        assertThat(state.nodes.getFirst().reclaimedVolumeUuids).containsExactly("volume");
    }

    @Test
    void inconsistentPartialReclamationCheckpointIsNotRecoveryEvidence() {
        graph.setStatus("RETIREMENT_BLOCKED");
        state.commands.put("retire-operation", new ManagedDependencyState.Command(
            validator.fingerprint(java.util.List.of("RETIRE", "graph", "deployment", "target")),
            "graph", java.time.Instant.now(), "actor", "SYSTEM"));
        state.retirementFingerprint = "original-plan";
        state.nodes.getFirst().status = "RETIRING";
        state.nodes.getFirst().retirementOperationKey = "retire-operation";
        state.nodes.getFirst().reclaimedVolumeUuids.add("unknown-volume");

        assertThat(adapter.canResumeForTargetRetirement("deployment", "target", "retire-operation")).isFalse();
    }
}
