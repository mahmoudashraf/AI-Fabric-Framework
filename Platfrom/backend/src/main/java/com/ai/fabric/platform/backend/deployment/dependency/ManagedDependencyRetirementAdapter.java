package com.ai.fabric.platform.backend.deployment.dependency;

import com.ai.fabric.platform.backend.deployment.service.PlatformManagedProductProvisioningService;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencyLifecycleService.*;
import static com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpecValidator.fail;

/** R3 integration only. Never invokes the deployment-wide deletion executor. */
@Service
public class ManagedDependencyRetirementAdapter {
    private final ManagedDependencyLifecycleService lifecycle;
    private final ManagedDependencyGraphStore store;
    private final PlatformManagedProductProvisioningService provisioning;
    private final PlatformSecretService secrets;
    private final ManagedDependencySpecValidator validator;
    public ManagedDependencyRetirementAdapter(ManagedDependencyLifecycleService lifecycle, ManagedDependencyGraphStore store,
            PlatformManagedProductProvisioningService provisioning, PlatformSecretService secrets, ManagedDependencySpecValidator validator) {
        this.lifecycle = lifecycle; this.store = store; this.provisioning = provisioning; this.secrets = secrets; this.validator = validator;
    }
    public record Resource(String slot, String serviceRef, String applicationUuid, String databaseUuid, Map<String, String> volumes) {}
    public record Plan(String graphId, long expectedRevision, String fingerprint, List<Resource> resources, List<String> blockers) {}

    public Plan plan(String deploymentId, String targetProfileId) {
        lifecycle.requireScope(deploymentId, targetProfileId, true);
        var graph = lifecycle.find(deploymentId, targetProfileId); var state = store.state(graph);
        List<String> blockers = new ArrayList<>();
        if (graph.getWorkerToken() != null) blockers.add("DEPENDENCY_OPERATION_IN_PROGRESS");
        for (var node : state.nodes) {
            if (node.applicationCreateIntent && node.applicationUuid == null || node.databaseCreateIntent && node.databaseUuid == null) blockers.add("DEPENDENCY_CREATE_UNCERTAIN_RECONCILE_REQUIRED");
        }
        var resources = state.nodes.stream().map(n -> new Resource(n.slot, n.serviceRef, n.applicationUuid, n.databaseUuid, Map.copyOf(n.volumes))).toList();
        String fingerprint = validator.fingerprint(List.of(lifecycle.scope(graph), graph.getRevision(), resources));
        return new Plan(graph.getId(), graph.getRevision(), fingerprint, resources, List.copyOf(blockers));
    }

    // R3 must hold its target-retirement admission barrier throughout this call. Retention/drain proof
    // belongs to requireUnused; a preview by itself is never authorization to destroy a resource.
    public ManagedDependencyLifecycleService.Summary retire(String deploymentId, String targetProfileId,
            long expectedRevision, String expectedPlanFingerprint, String operationKey) {
        lifecycle.requireScope(deploymentId, targetProfileId, true); required(operationKey, 128);
        var graph = lifecycle.find(deploymentId, targetProfileId);
        var gate = lifecycle.gate();
        var scope = lifecycle.scope(graph);
        gate.requireUnused(scope, graph.getRevision(), operationKey);
        String token = id("retire");
        boolean[] completed = {false};
        store.mutate(graph.getId(), null, (current, state) -> {
            String fingerprint = validator.fingerprint(List.of("RETIRE", expectedRevision, expectedPlanFingerprint));
            boolean fresh = command(state, operationKey, fingerprint, current.getId());
            if (!fresh && "RETIRED".equals(current.getStatus())) { completed[0] = true; return; }
            if (current.getWorkerToken() != null) fail("DEPENDENCY_OPERATION_IN_PROGRESS");
            if (fresh) {
                var plan = plan(deploymentId, targetProfileId);
                if (current.getRevision() != expectedRevision || !plan.fingerprint().equals(expectedPlanFingerprint)
                    || !plan.blockers().isEmpty()) fail("DEPENDENCY_RETIREMENT_PLAN_STALE");
                state.retirementFingerprint = expectedPlanFingerprint;
            } else if (!expectedPlanFingerprint.equals(state.retirementFingerprint)) fail("DEPENDENCY_RETIREMENT_PLAN_STALE");
            current.setStatus("RETIRING"); current.setWorkerToken(token); current.setStep("RECLAIM");
        });
        if (completed[0]) return lifecycle.summary(store.read(graph.getId()));
        try {
            var connection = provisioning.ownedDependencyConnection(targetProfileId, lifecycle.environment(graph));
            var state = store.state(store.read(graph.getId()));
            for (int i = state.nodes.size() - 1; i >= 0; i--) {
                var node = state.nodes.get(i);
                var checkpoint = new PlatformManagedProductProvisioningService.OwnedDependencyCheckpoint() {
                    public void guard() {
                        var current = store.read(graph.getId());
                        if (!token.equals(current.getWorkerToken()) || !"RETIRING".equals(current.getStatus())) fail("DEPENDENCY_WORKER_FENCED");
                        gate.requireUnused(scope, current.getRevision(), operationKey);
                    }
                    public void creating(String kind) { fail("DEPENDENCY_RETIREMENT_CANNOT_CREATE"); }
                    public void created(String kind, String uuid) { fail("DEPENDENCY_RETIREMENT_CANNOT_CREATE"); }
                    public void volume(String mountPath, String uuid) { fail("DEPENDENCY_RETIREMENT_CANNOT_CREATE"); }
                };
                provisioning.retireOwnedDependency(connection, graph.getId(), node.serviceRef, node.slot, resources(node), checkpoint);
                store.mutate(graph.getId(), token, (current, owned) -> node(owned, node.slot).status = "RETIRED");
                if (node.applicationUuid != null) lifecycle.updateProduct(node.serviceRef, null, "DECOMMISSIONED");
            }
            var current = store.read(graph.getId());
            gate.requireUnused(scope, current.getRevision(), operationKey);
            gate.verifyReclaimed(scope, state.nodes.stream().map(n -> new Resource(n.slot, n.serviceRef,
                n.applicationUuid, n.databaseUuid, Map.copyOf(n.volumes))).toList());
            for (var node : state.nodes) for (String ref : node.secrets.values()) secrets.clearManagedSecret(ref, Map.of("dependencyGraphId", graph.getId()));
            for (var operator : state.operators) secrets.clearManagedSecret(operator.secretName, Map.of("dependencyGraphId", graph.getId()));
            for (String ref : state.retiredOperatorSecretNames) secrets.clearManagedSecret(ref, Map.of("dependencyGraphId", graph.getId()));
            if (graph.getReservationRef() != null) gate.releaseReservation(scope, graph.getReservationRef());
            store.mutate(graph.getId(), token, (owned, data) -> {
                owned.setWorkerToken(null); owned.setStatus("RETIRED"); owned.setStep("COMPLETE"); owned.setFailureCode(null);
            });
            lifecycle.audit(graph.getId(), "DEPENDENCY_GRAPH_RETIRED", Map.of("operationKey", operationKey));
        } catch (RuntimeException error) {
            store.mutate(graph.getId(), token, (owned, data) -> {
                owned.setWorkerToken(null); owned.setStatus("RETIREMENT_BLOCKED"); owned.setFailureCode("DEPENDENCY_RETIREMENT_UNCONFIRMED");
            });
            lifecycle.audit(graph.getId(), "DEPENDENCY_RETIREMENT_BLOCKED", Map.of("operationKey", operationKey));
        }
        return lifecycle.summary(store.read(graph.getId()));
    }

    public ManagedDependencyLifecycleService.Summary compensateUnused(String deploymentId, String targetProfileId,
            long expectedRevision, String expectedPlanFingerprint, String operationKey) {
        return retire(deploymentId, targetProfileId, expectedRevision, expectedPlanFingerprint, operationKey);
    }
}
