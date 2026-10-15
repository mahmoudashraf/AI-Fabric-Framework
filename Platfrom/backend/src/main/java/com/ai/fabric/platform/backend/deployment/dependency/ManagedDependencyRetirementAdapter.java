package com.ai.fabric.platform.backend.deployment.dependency;

import com.ai.fabric.platform.backend.deployment.service.PlatformManagedProductProvisioningService;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    public record Resource(String slot, String serviceRef, String applicationUuid, String databaseUuid,
                           Map<String, String> volumes, Map<String, String> volumeReclamationPlan,
                           Set<String> reclaimedVolumeUuids) {
        public Resource {
            volumes = volumes == null ? Map.of() : Map.copyOf(volumes);
            volumeReclamationPlan = volumeReclamationPlan == null ? Map.of() : Map.copyOf(volumeReclamationPlan);
            reclaimedVolumeUuids = reclaimedVolumeUuids == null ? Set.of() : Set.copyOf(reclaimedVolumeUuids);
        }

        public Resource(String slot, String serviceRef, String applicationUuid, String databaseUuid,
                        Map<String, String> volumes) {
            this(slot, serviceRef, applicationUuid, databaseUuid, volumes, Map.of(), Set.of());
        }

        public Resource(String slot, String serviceRef, String applicationUuid, String databaseUuid,
                        Map<String, String> volumes, Set<String> reclaimedVolumeUuids) {
            this(slot, serviceRef, applicationUuid, databaseUuid, volumes,
                reclaimedVolumeUuids.stream().collect(java.util.stream.Collectors.toMap(
                    value -> value, value -> value)), reclaimedVolumeUuids);
        }
    }
    public record Plan(String graphId, long expectedRevision, String fingerprint, List<Resource> resources, List<String> blockers) {}

    public Plan plan(String deploymentId, String targetProfileId) {
        lifecycle.requireScope(deploymentId, targetProfileId, true);
        return planInternal(deploymentId, targetProfileId);
    }

    /** Scheduler path; destructive authorization is supplied by requireUnused under the retirement fence. */
    public Plan planForTargetRetirement(String deploymentId, String targetProfileId) {
        return planInternal(deploymentId, targetProfileId);
    }

    private Plan planInternal(String deploymentId, String targetProfileId) {
        var graph = lifecycle.find(deploymentId, targetProfileId); var state = store.state(graph);
        List<String> blockers = new ArrayList<>();
        if (graph.getWorkerToken() != null) blockers.add("DEPENDENCY_OPERATION_IN_PROGRESS");
        for (var node : state.nodes) {
            if (node.applicationCreateIntent && node.applicationUuid == null || node.databaseCreateIntent && node.databaseUuid == null) blockers.add("DEPENDENCY_CREATE_UNCERTAIN_RECONCILE_REQUIRED");
        }
        var resources = state.nodes.stream().map(this::resource).toList();
        String fingerprint = validator.fingerprint(List.of(lifecycle.scope(graph), graph.getRevision(),
            resources.stream().map(this::resourceIdentity).toList()));
        return new Plan(graph.getId(), graph.getRevision(), fingerprint, resources, List.copyOf(blockers));
    }

    // R3 must hold its target-retirement admission barrier throughout this call. Retention/drain proof
    // belongs to requireUnused; a preview by itself is never authorization to destroy a resource.
    public ManagedDependencyLifecycleService.Summary retire(String deploymentId, String targetProfileId,
            long expectedRevision, String expectedPlanFingerprint, String operationKey) {
        lifecycle.requireScope(deploymentId, targetProfileId, true); required(operationKey, 128);
        return retireInternal(deploymentId, targetProfileId, expectedRevision, expectedPlanFingerprint, operationKey);
    }

    /** Exact target-retirement worker path; cannot run without the R5 durable gate proof. */
    public ManagedDependencyLifecycleService.Summary retireForTargetRetirement(String deploymentId, String targetProfileId,
            long expectedRevision, String expectedPlanFingerprint, String operationKey) {
        required(operationKey, 128);
        return retireInternal(deploymentId, targetProfileId, expectedRevision, expectedPlanFingerprint, operationKey);
    }

    /**
     * Read-only restart proof for R3 recovery. It accepts only the same durable retirement command
     * and internally consistent per-node checkpoints after the graph worker has been released.
     */
    public boolean canResumeForTargetRetirement(String deploymentId, String targetProfileId, String operationKey) {
        if (operationKey == null || operationKey.isBlank() || operationKey.length() > 128) return false;
        try {
            var graph = lifecycle.find(deploymentId, targetProfileId);
            if (!"RETIREMENT_BLOCKED".equals(graph.getStatus()) || graph.getWorkerToken() != null) return false;
            var state = store.state(graph);
            String commandFingerprint = validator.fingerprint(List.of(
                "RETIRE", graph.getId(), deploymentId, targetProfileId));
            var command = state.commands.get(operationKey);
            if (command == null || !commandFingerprint.equals(command.fingerprint())
                || state.retirementFingerprint == null || state.retirementFingerprint.isBlank()
                || state.nodes.isEmpty()) return false;
            Set<String> allowed = Set.of("READY", "PROVISIONED", "DISABLED", "RETIRING", "RETIRED");
            for (var node : state.nodes) {
                if (!allowed.contains(node.status)) return false;
                boolean started = Set.of("RETIRING", "RETIRED").contains(node.status);
                if (started != operationKey.equals(node.retirementOperationKey)) return false;
                Set<String> reclaimed = Set.copyOf(node.reclaimedVolumeUuids);
                Map<String, String> plan = Map.copyOf(node.volumeReclamationPlan);
                if (!plan.keySet().containsAll(reclaimed)) return false;
                if (!plan.isEmpty()) {
                    if (!started || !plan.keySet().containsAll(Set.copyOf(node.volumes.values()))
                        || node.databaseUuid != null && plan.keySet().equals(Set.copyOf(node.volumes.values()))
                        || plan.values().stream().anyMatch(value -> value == null || value.isBlank())
                        || plan.values().stream().distinct().count() != plan.size()) return false;
                } else if (!reclaimed.isEmpty()) return false;
            }
            return true;
        } catch (RuntimeException error) {
            return false;
        }
    }

    private ManagedDependencyLifecycleService.Summary retireInternal(String deploymentId, String targetProfileId,
            long expectedRevision, String expectedPlanFingerprint, String operationKey) {
        var graph = lifecycle.find(deploymentId, targetProfileId);
        var gate = lifecycle.gate();
        var scope = lifecycle.scope(graph);
        String token = id("retire");
        boolean[] completed = {false};
        store.mutate(graph.getId(), null, (current, state) -> {
            String fingerprint = validator.fingerprint(List.of("RETIRE", current.getId(), deploymentId, targetProfileId));
            boolean fresh = command(state, operationKey, fingerprint, current.getId());
            if (!fresh && "RETIRED".equals(current.getStatus())) { completed[0] = true; return; }
            if (current.getWorkerToken() != null) fail("DEPENDENCY_OPERATION_IN_PROGRESS");
            if (fresh) {
                var plan = planInternal(deploymentId, targetProfileId);
                if (current.getRevision() != expectedRevision || !plan.fingerprint().equals(expectedPlanFingerprint)
                    || !plan.blockers().isEmpty()) fail("DEPENDENCY_RETIREMENT_PLAN_STALE");
                state.retirementFingerprint = expectedPlanFingerprint;
            } else if (state.retirementFingerprint == null || state.retirementFingerprint.isBlank()) {
                fail("DEPENDENCY_RETIREMENT_PLAN_STALE");
            }
            current.setStatus("RETIRING"); current.setWorkerToken(token); current.setStep("RECLAIM");
        });
        if (completed[0]) return lifecycle.summary(store.read(graph.getId()));
        try {
            var connection = provisioning.ownedDependencyConnection(targetProfileId, lifecycle.environment(graph));
            var acceptedState = store.state(store.read(graph.getId()));
            List<String> slots = acceptedState.nodes.stream().map(node -> node.slot).toList();
            gate.requireUnused(scope, store.read(graph.getId()).getRevision(), operationKey);
            for (int i = slots.size() - 1; i >= 0; i--) {
                String slot = slots.get(i);
                var before = node(store.state(store.read(graph.getId())), slot);
                if ("RETIRED".equals(before.status)) {
                    if (!operationKey.equals(before.retirementOperationKey)) fail("DEPENDENCY_RETIREMENT_GENERATION_CHANGED");
                    continue;
                }
                if ("RETIRING".equals(before.status)) {
                    if (!operationKey.equals(before.retirementOperationKey)) fail("DEPENDENCY_RETIREMENT_GENERATION_CHANGED");
                } else {
                    var current = store.read(graph.getId());
                    gate.requireUnused(scope, current.getRevision(), operationKey);
                    store.mutate(graph.getId(), token, (owned, state) -> {
                        var retiring = node(state, slot);
                        if ("RETIRED".equals(retiring.status)) return;
                        if ("RETIRING".equals(retiring.status)
                            && !operationKey.equals(retiring.retirementOperationKey)) {
                            fail("DEPENDENCY_RETIREMENT_GENERATION_CHANGED");
                        }
                        retiring.status = "RETIRING";
                        retiring.retirementOperationKey = operationKey;
                    });
                }
                var retiringNode = node(store.state(store.read(graph.getId())), slot);
                var checkpoint = new PlatformManagedProductProvisioningService.OwnedDependencyCheckpoint() {
                    public void guard() {
                        var current = store.read(graph.getId());
                        if (!token.equals(current.getWorkerToken()) || !"RETIRING".equals(current.getStatus())) fail("DEPENDENCY_WORKER_FENCED");
                        var currentNode = node(store.state(current), slot);
                        if (!"RETIRING".equals(currentNode.status)
                            || !operationKey.equals(currentNode.retirementOperationKey)) {
                            fail("DEPENDENCY_RETIREMENT_GENERATION_CHANGED");
                        }
                        gate.requireUnused(scope, current.getRevision(), operationKey);
                    }
                    public void creating(String kind) { fail("DEPENDENCY_RETIREMENT_CANNOT_CREATE"); }
                    public void created(String kind, String uuid) { fail("DEPENDENCY_RETIREMENT_CANNOT_CREATE"); }
                    public void volume(String mountPath, String uuid) { fail("DEPENDENCY_RETIREMENT_CANNOT_CREATE"); }
                    public void volumeReclamationPlanned(Map<String, String> volumeNamesByUuid) {
                        Map<String, String> evidence = volumeNamesByUuid == null ? Map.of() : Map.copyOf(volumeNamesByUuid);
                        store.mutate(graph.getId(), token, (current, state) -> {
                            var currentNode = node(state, slot);
                            if (!"RETIRING".equals(currentNode.status)
                                || !operationKey.equals(currentNode.retirementOperationKey)
                                || !evidence.keySet().containsAll(Set.copyOf(currentNode.volumes.values()))
                                || currentNode.databaseUuid != null
                                    && evidence.keySet().equals(Set.copyOf(currentNode.volumes.values()))
                                || evidence.entrySet().stream().anyMatch(entry -> entry.getKey() == null
                                    || entry.getKey().isBlank() || entry.getValue() == null || entry.getValue().isBlank())
                                || evidence.values().stream().distinct().count() != evidence.size()) {
                                fail("DEPENDENCY_VOLUME_RECLAMATION_EVIDENCE_INVALID");
                            }
                            Map<String, String> existing = Map.copyOf(currentNode.volumeReclamationPlan);
                            if (!existing.isEmpty() && !existing.equals(evidence)) {
                                fail("DEPENDENCY_VOLUME_RECLAMATION_EVIDENCE_INVALID");
                            }
                            currentNode.volumeReclamationPlan = new java.util.LinkedHashMap<>(new java.util.TreeMap<>(evidence));
                        });
                    }
                    public void volumesReclaimed(Set<String> volumeUuids) {
                        Set<String> evidence = volumeUuids == null ? Set.of() : Set.copyOf(volumeUuids);
                        store.mutate(graph.getId(), token, (current, state) -> {
                            var currentNode = node(state, slot);
                            if (!"RETIRING".equals(currentNode.status)
                                || !operationKey.equals(currentNode.retirementOperationKey)
                                || evidence.stream().anyMatch(value -> !currentNode.volumeReclamationPlan.containsKey(value))) {
                                fail("DEPENDENCY_VOLUME_RECLAMATION_EVIDENCE_INVALID");
                            }
                            Set<String> existing = Set.copyOf(currentNode.reclaimedVolumeUuids);
                            if (!evidence.containsAll(existing)) {
                                fail("DEPENDENCY_VOLUME_RECLAMATION_EVIDENCE_INVALID");
                            }
                            currentNode.reclaimedVolumeUuids = new ArrayList<>(new LinkedHashSet<>(evidence));
                            currentNode.reclaimedVolumeUuids.sort(String::compareTo);
                        });
                    }
                };
                provisioning.retireOwnedDependency(connection, graph.getId(), retiringNode.serviceRef, retiringNode.slot,
                    resources(retiringNode), checkpoint);
                store.mutate(graph.getId(), token, (current, owned) -> node(owned, slot).status = "RETIRED");
                if (retiringNode.applicationUuid != null) lifecycle.updateProduct(retiringNode.serviceRef, null, "DECOMMISSIONED");
            }
            var current = store.read(graph.getId());
            gate.requireUnused(scope, current.getRevision(), operationKey);
            var reclaimedState = store.state(current);
            gate.verifyReclaimed(scope, reclaimedState.nodes.stream().map(this::resource).toList());
            for (var node : reclaimedState.nodes) for (String ref : node.secrets.values()) secrets.clearManagedSecret(ref, Map.of("dependencyGraphId", graph.getId()));
            for (var operator : reclaimedState.operators) secrets.clearManagedSecret(operator.secretName, Map.of("dependencyGraphId", graph.getId()));
            for (String ref : reclaimedState.retiredOperatorSecretNames) secrets.clearManagedSecret(ref, Map.of("dependencyGraphId", graph.getId()));
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

    private Resource resource(ManagedDependencyState.Node node) {
        return new Resource(node.slot, node.serviceRef, node.applicationUuid, node.databaseUuid,
            Map.copyOf(node.volumes), Map.copyOf(node.volumeReclamationPlan),
            Set.copyOf(node.reclaimedVolumeUuids));
    }

    private List<Object> resourceIdentity(Resource resource) {
        List<Object> identity = new ArrayList<>();
        identity.add(resource.slot());
        identity.add(resource.serviceRef());
        identity.add(resource.applicationUuid());
        identity.add(resource.databaseUuid());
        identity.add(new java.util.TreeMap<>(resource.volumes()));
        return identity;
    }
}
