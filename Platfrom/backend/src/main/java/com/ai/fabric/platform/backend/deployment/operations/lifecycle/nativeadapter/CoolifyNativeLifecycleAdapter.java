package com.ai.fabric.platform.backend.deployment.operations.lifecycle.nativeadapter;

import com.ai.fabric.platform.backend.customerselfservice.config.CustomerSelfServiceProperties;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.TargetIntent;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencyGraphEntity;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencyGraphStore;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpec;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpecValidator;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencyState;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentProviderResourceHandleEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.model.PreservedExternalBindingKinds;
import com.ai.fabric.platform.backend.deployment.provider.CoolifyHostVolumeReclamationService;
import com.ai.fabric.platform.backend.deployment.provider.ProviderHostCommandRunner;
import com.ai.fabric.platform.backend.deployment.provider.ProviderResourceVerificationFailure;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentProviderResourceHandleRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.service.CoolifyActionResponse;
import com.ai.fabric.platform.backend.deployment.service.CoolifyApiClient;
import com.ai.fabric.platform.backend.deployment.service.CoolifyApplicationStorageSummary;
import com.ai.fabric.platform.backend.deployment.service.CoolifyConnection;
import com.ai.fabric.platform.backend.deployment.service.CoolifyRetirementOwnership;
import com.ai.fabric.platform.backend.deployment.service.CoolifyTargetProfileResolver;
import com.ai.fabric.platform.backend.deployment.service.PlatformManagedProductProvisioningService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Exact owned native resources, not a deployment scheduler or a recovery-policy engine. */
@Service
public class CoolifyNativeLifecycleAdapter implements NativeLifecycleAdapter {
    private static final Set<String> HANDLE_KINDS = Set.of("APPLICATION", "CONNECTOR_APPLICATION",
        "VECTORIZATION_RUNNER_APPLICATION", "RUNTIME_POSTGRES_DATABASE");
    private static final int MAX_RESOURCES = 64;
    private static final int MAX_NATIVE_INVENTORY = 512;
    private final DeploymentRepository deployments;
    private final DeploymentTargetProfileRepository targets;
    private final DeploymentReleaseRepository releases;
    private final DeploymentProviderResourceHandleRepository handles;
    private final ManagedDependencyGraphStore graphs;
    private final CoolifyTargetProfileResolver resolver;
    private final CoolifyApiClient api;
    private final CoolifyRetirementOwnership ownership;
    private final PlatformManagedProductProvisioningService dependencies;
    private final CoolifyHostVolumeReclamationService volumes;
    private final ProviderHostCommandRunner host;
    private final CustomerSelfServiceProperties customerProperties;
    private final ObjectMapper mapper;
    private final NativeLifecycleFingerprint fingerprints;

    public CoolifyNativeLifecycleAdapter(DeploymentRepository deployments, DeploymentTargetProfileRepository targets,
            DeploymentReleaseRepository releases, DeploymentProviderResourceHandleRepository handles,
            ManagedDependencyGraphStore graphs, CoolifyTargetProfileResolver resolver, CoolifyApiClient api,
            CoolifyRetirementOwnership ownership, PlatformManagedProductProvisioningService dependencies,
            CoolifyHostVolumeReclamationService volumes, ProviderHostCommandRunner host,
            CustomerSelfServiceProperties customerProperties, ObjectMapper mapper) {
        this.deployments = deployments; this.targets = targets; this.releases = releases; this.handles = handles;
        this.graphs = graphs; this.resolver = resolver; this.api = api; this.ownership = ownership;
        this.dependencies = dependencies; this.volumes = volumes; this.host = host;
        this.customerProperties = customerProperties; this.mapper = mapper;
        this.fingerprints = new NativeLifecycleFingerprint(mapper);
    }

    @Override
    public NativeSnapshot freeze(String deploymentId, String targetProfileId, String releaseId) {
        noTransaction();
        var deployment = deployment(deploymentId);
        var target = target(targetProfileId);
        var release = release(deploymentId, targetProfileId, releaseId);
        var connection = connection(target);
        var resources = new ArrayList<NativeResource>();
        var exclusions = new ArrayList<Exclusion>();
        for (var handle : handles.findByDeploymentIdAndTargetProfileIdOrderByUpdatedAtDesc(deploymentId, targetProfileId)) {
            if (PreservedExternalBindingKinds.isPreserved(handle.getResourceKind())) {
                exclusions.add(new Exclusion(handle.getId(), handle.getResourceKind(), handleFingerprint(handle)));
                continue;
            }
            if ("DELETED".equals(handle.getStatus())) continue;
            if (!HANDLE_KINDS.contains(handle.getResourceKind()) || handle.getProviderType() != DeploymentProviderType.COOLIFY
                || !releaseId.equals(handle.getReleaseId())) throw failure("NATIVE_RESOURCE_INVENTORY_UNSUPPORTED");
            ownership.requireRetirementOwnership(handle);
            var raw = raw(connection, handle.getResourceKind(), handle.getProviderResourceUuid());
            if (raw == null) throw failure("NATIVE_RESOURCE_ALREADY_ABSENT");
            JsonNode metadata = document(handle.getMetadataJson());
            String name = required(metadata.path(handle.getResourceKind().equals("RUNTIME_POSTGRES_DATABASE")
                ? "databaseName" : "applicationName").asText(null));
            String marker = handle.getResourceKind().equals("RUNTIME_POSTGRES_DATABASE")
                ? "Durable runtime chat database for AI Fabric deployment " + deploymentId
                : "Managed by AI Fabric deployment " + deploymentId;
            var placement = placement(connection, handle.getProviderProjectUuid(), handle.getProviderEnvironmentUuid(), target);
            var resource = new NativeResource(handle.getId(), Origin.HANDLE, handle.getResourceKind(),
                required(handle.getProviderResourceUuid()), name, marker, identity(raw, name, marker), placement,
                new HandleIdentity(handle.getReleaseId(), Objects.requireNonNull(handle.getCreatedAt()),
                    handleFingerprint(handle), required(metadata.path("ownershipReceiptId").asText(null))),
                null, storage(connection, handle.getResourceKind(), handle.getProviderResourceUuid()));
            requireNativePlacement(resource, raw);
            resources.add(resource);
        }
        var graphIdentities = new ArrayList<GraphIdentity>();
        graphs.find(deploymentId, targetProfileId).ifPresent(graph -> {
            requireGraph(graph, deployment, releaseId);
            graphIdentities.add(graphIdentity(graph));
            var ownedConnection = dependencies.ownedDependencyConnection(targetProfileId, target.getEnvironmentName());
            for (var node : orderedNodes(graph)) {
                for (var observed : dependencies.inspectOwnedLifecycleResources(ownedConnection, graph.getId(),
                        node.serviceRef, node.slot, ownedResources(node))) {
                    if (observed.raw() == null) throw failure("NATIVE_DEPENDENCY_ALREADY_ABSENT");
                    resources.add(new NativeResource(graph.getId() + ":" + node.slot + ":" + observed.kind(),
                        Origin.DEPENDENCY, observed.kind(), observed.uuid(), observed.name(), observed.marker(),
                        identity(observed.raw(), observed.name(), observed.marker()), placement(ownedConnection,
                            ownedConnection.config().projectUuid(), ownedConnection.config().environmentUuid(), target),
                        null, new DependencyIdentity(graph.getId(), node.slot, node.serviceRef),
                        storage(observed.storages())));
                }
            }
        });
        if (resources.isEmpty() || resources.size() > MAX_RESOURCES
            || resources.stream().map(NativeResource::id).distinct().count() != resources.size()
            || resources.stream().map(NativeResource::uuid).distinct().count() != resources.size())
            throw failure("NATIVE_RESOURCE_INVENTORY_INVALID");
        resources.sort(Comparator.comparing(NativeResource::id));
        exclusions.sort(Comparator.comparing(Exclusion::id));
        var unsigned = new NativeSnapshot(customer(deployment.getCustomerId()), required(deployment.getTenantId()),
            deploymentId, targetProfileId, releaseId, targetFingerprint(target), releaseFingerprint(release),
            resources, graphIdentities, exclusions, null, null);
        var snapshot = new NativeSnapshot(unsigned.customerId(), unsigned.tenantId(), deploymentId, targetProfileId,
            releaseId, unsigned.targetFingerprint(), unsigned.releaseFingerprint(), resources, graphIdentities,
            exclusions, scopeHash(unsigned), Instant.now());
        requireExclusiveVolumes(snapshot, connection);
        return snapshot;
    }

    @Override public Observation observe(NativeSnapshot snapshot) { return observation(snapshot, false, false); }
    @Override public Observation verifyStopped(NativeSnapshot snapshot) { return observation(snapshot, true, false); }
    @Override public Observation verifyAbsent(NativeSnapshot snapshot) { return observation(snapshot, false, true); }

    @Override
    public Observation stop(NativeSnapshot snapshot, Checkpoint checkpoint) {
        check(snapshot); checkpoint.guard();
        for (var resource : ordered(snapshot, false)) {
            checkpoint.guard();
            var observed = observeResource(snapshot, resource, false, false);
            String effect = effect(snapshot, resource, "STOP");
            if (observed.state() == State.STOPPED || observed.state() == State.ABSENT) {
                if (!checkpoint.isComplete(effect)) {
                    checkpoint.beforeEffect(effect);
                    requireNoRunning(resource);
                    checkpoint.completed(effect);
                }
                continue;
            }
            if (checkpoint.isComplete(effect)) return pending(snapshot, "NATIVE_STOP_STATE_DRIFT");
            if (!checkpoint.beforeEffect(effect)) return pending(snapshot, "NATIVE_STOP_OUTCOME_UNKNOWN");
            checkpoint.guard(); check(snapshot);
            dispatch(snapshot, resource, "STOP", false);
            var after = observeResource(snapshot, resource, true, false);
            if ((after.state() == State.STOPPED || after.state() == State.ABSENT) && after.containersAbsent())
                checkpoint.completed(effect);
            return observation(snapshot, true, false);
        }
        return observation(snapshot, true, false);
    }

    @Override
    public Observation quiesceWorkers(NativeSnapshot snapshot, Checkpoint checkpoint) {
        check(snapshot); checkpoint.guard();
        observe(snapshot); // Revalidate native identities for runtime and databases as well as workers.
        for (var resource : ordered(snapshot, false).stream().filter(this::worker).toList()) {
            checkpoint.guard();
            var observed = observeResource(snapshot, resource, true, false, true);
            String effect = effect(snapshot, resource, "STOP");
            if (observed.state() == State.STOPPED || observed.state() == State.ABSENT) {
                if (!observed.containersAbsent()) return workerObservation(snapshot, null);
                if (!checkpoint.isComplete(effect)) {
                    checkpoint.beforeEffect(effect);
                    checkpoint.completed(effect);
                }
                continue;
            }
            if (checkpoint.isComplete(effect)) return workerObservation(snapshot, "NATIVE_WORKER_STOP_STATE_DRIFT");
            if (!checkpoint.beforeEffect(effect)) return workerObservation(snapshot, "NATIVE_WORKER_STOP_OUTCOME_UNKNOWN");
            checkpoint.guard();
            observe(snapshot);
            dispatch(snapshot, resource, "STOP", false);
            var after = observeResource(snapshot, resource, true, false, true);
            if ((after.state() == State.STOPPED || after.state() == State.ABSENT) && after.containersAbsent())
                checkpoint.completed(effect);
            return workerObservation(snapshot, null);
        }
        return workerObservation(snapshot, null);
    }

    private boolean worker(NativeResource resource) {
        return "CONNECTOR_APPLICATION".equals(resource.kind()) || "VECTORIZATION_RUNNER_APPLICATION".equals(resource.kind())
            || resource.origin() == Origin.DEPENDENCY && !resource.database();
    }

    @Override
    public boolean supportsRemoteDependency(NativeSnapshot snapshot, String kind) {
        if (kind == null || !Set.of("CONNECTOR", "VECTORIZATION_RUNNER", "HUMAN_INBOX").contains(kind)) return false;
        try {
            observe(snapshot);
            for (var resource : snapshot.resources()) {
                if ("CONNECTOR".equals(kind) && resource.origin() == Origin.HANDLE
                    && "CONNECTOR_APPLICATION".equals(resource.kind())) return true;
                if ("VECTORIZATION_RUNNER".equals(kind) && resource.origin() == Origin.HANDLE
                    && "VECTORIZATION_RUNNER_APPLICATION".equals(resource.kind())) return true;
                if (!"HUMAN_INBOX".equals(kind) || resource.origin() != Origin.DEPENDENCY || resource.database()) continue;
                var graph = graphs.read(resource.dependency().graphId());
                var spec = graphs.spec(graph);
                var validator = new ManagedDependencySpecValidator(mapper);
                if (spec == null || !Objects.equals(graph.getSpecFingerprint(), validator.fingerprint(spec))) continue;
                validator.validate(spec);
                if (spec.services().stream().anyMatch(service -> resource.dependency().slot().equals(service.slot())
                    && service.contract() == ManagedDependencySpec.Contract.BASIC_INBOX_KEYS_V1)) return true;
            }
            return false;
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    private Observation workerObservation(NativeSnapshot snapshot, String blocker) {
        observe(snapshot);
        var observations = ordered(snapshot, false).stream().filter(this::worker)
            .map(resource -> observeResource(snapshot, resource, true, false, true)).toList();
        var blockers = new ArrayList<>(observations.stream().flatMap(value -> value.blockers().stream()).distinct().toList());
        if (blocker != null && !blockers.contains(blocker)) blockers.add(blocker);
        boolean complete = blockers.isEmpty() && observations.stream().allMatch(value ->
            (value.state() == State.STOPPED || value.state() == State.ABSENT) && value.containersAbsent());
        // The complete bit applies only to these workers. No global stop, absence or storage claim is made.
        return new Observation(snapshot.scopeHash(), Instant.now(), observations, false, false, false, false, complete, blockers);
    }

    @Override
    public Observation restart(NativeSnapshot snapshot, Checkpoint checkpoint) {
        check(snapshot); checkpoint.guard();
        for (var resource : ordered(snapshot, true)) {
            checkpoint.guard();
            String effect = effect(snapshot, resource, "START");
            var observed = observeResource(snapshot, resource, false, false);
            if (checkpoint.isComplete(effect)) {
                if (!ready(observed)) return pending(snapshot, "NATIVE_START_STATE_DRIFT");
                continue;
            }
            String nativeJob = checkpoint.operationId(effect);
            if (nativeJob != null) {
                if (nativeJobComplete(snapshot, resource, nativeJob) && ready(observed)) {
                    checkpoint.completed(effect);
                    continue;
                }
                return pending(snapshot, "NATIVE_START_READBACK_PENDING");
            }
            if (observed.state() != State.STOPPED) return pending(snapshot,
                observed.state() == State.RUNNING ? "NATIVE_RESTART_REQUIRES_STOP_OR_EXACT_JOB"
                    : "NATIVE_START_RESOURCE_NOT_STOPPED");
            if (!checkpoint.beforeEffect(effect)) return pending(snapshot, "NATIVE_START_OUTCOME_UNKNOWN");
            checkpoint.guard(); check(snapshot);
            var response = dispatch(snapshot, resource, "START", false);
            if (response != null && response.deploymentUuid() != null) {
                checkpoint.dispatched(effect, response.deploymentUuid());
                if (nativeJobComplete(snapshot, resource, response.deploymentUuid())
                    && ready(observeResource(snapshot, resource, false, false))) checkpoint.completed(effect);
            } else if (resource.database() && response != null) {
                // Database /start has no deployment-job resource. Preserve its successful HTTP acknowledgment;
                // a lost response never reaches this checkpoint and cannot be inferred from RUNNING alone.
                checkpoint.dispatched(effect, "database-start-ack:" + resource.uuid());
                if (ready(observeResource(snapshot, resource, false, false))) checkpoint.completed(effect);
            }
            return pending(snapshot, "NATIVE_START_READBACK_PENDING");
        }
        var result = observation(snapshot, false, false);
        return withCompletion(result, result.ready(), result.ready() ? null : "NATIVE_START_NOT_READY");
    }

    @Override
    public Observation delete(NativeSnapshot snapshot, boolean reclaimOwnedData, Checkpoint checkpoint) {
        check(snapshot); checkpoint.guard();
        if (!reclaimOwnedData && snapshot.resources().stream().anyMatch(resource -> !resource.volumes().isEmpty()))
            throw failure("NATIVE_STORAGE_RECLAMATION_APPROVAL_REQUIRED");
        if (reclaimOwnedData && snapshot.resources().stream().flatMap(resource -> resource.volumes().stream()).anyMatch(Volume::retained))
            throw failure("NATIVE_EXTERNAL_MOUNT_MUST_BE_RETAINED");
        var stopped = observation(snapshot, true, false, checkpoint);
        if (!stopped.complete()) return withCompletion(stopped, false, "NATIVE_STOP_REQUIRED");
        for (var resource : ordered(snapshot, false)) {
            checkpoint.guard();
            String deleteEffect = effect(snapshot, resource, "DELETE");
            var observed = observeResource(snapshot, resource, false, true, false, checkpoint.isKnown(deleteEffect));
            if (observed.state() != State.ABSENT) {
                if (!checkpoint.beforeEffect(deleteEffect)) return pendingDeletion(snapshot, checkpoint, "NATIVE_DELETE_OUTCOME_UNKNOWN");
                checkpoint.guard(); check(snapshot);
                requireExclusiveVolumes(snapshot, connection(target(snapshot.targetProfileId())));
                dispatch(snapshot, resource, "DELETE", reclaimOwnedData);
                observed = observeResource(snapshot, resource, false, true, false, true);
                if (observed.state() != State.ABSENT || !observed.containersAbsent())
                    return pendingDeletion(snapshot, checkpoint, "NATIVE_DELETE_READBACK_PENDING");
            }
            if (!observed.containersAbsent()) return pendingDeletion(snapshot, checkpoint, "NATIVE_CONTAINER_ABSENCE_UNPROVEN");
            if (!checkpoint.isComplete(deleteEffect)) {
                checkpoint.beforeEffect(deleteEffect);
                checkpoint.completed(deleteEffect);
            }
            String volumeEffect = effect(snapshot, resource, "RECLAIM");
            if (!resource.volumes().isEmpty() && !observed.volumesAbsent()) {
                if (!reclaimOwnedData) throw failure("NATIVE_STORAGE_RECLAMATION_APPROVAL_REQUIRED");
                if (!checkpoint.beforeEffect(volumeEffect)) return pendingDeletion(snapshot, checkpoint, "NATIVE_RECLAMATION_OUTCOME_UNKNOWN");
                checkpoint.guard(); check(snapshot);
                requireExclusiveVolumes(snapshot, connection(target(snapshot.targetProfileId())));
                volumes.reclaimAndVerify(resource.placement().targetIntent(), resource.placement().serverUuid(),
                    snapshot.scopeHash() + ":" + resource.id(), resource.volumes().stream().map(volume ->
                        new CoolifyHostVolumeReclamationService.Volume(volume.uuid(), volume.name(), volume.hostPath(), volume.retained())).toList());
                if (!volumesAbsent(resource)) return pendingDeletion(snapshot, checkpoint, "NATIVE_VOLUME_ABSENCE_UNPROVEN");
            }
            if (!resource.volumes().isEmpty() && !checkpoint.isComplete(volumeEffect)) {
                checkpoint.beforeEffect(volumeEffect);
                checkpoint.completed(volumeEffect);
            }
            checkpoint.guard();
            finishDeletion(snapshot, resource, reclaimOwnedData);
        }
        var result = observation(snapshot, false, true);
        boolean complete = result.absent() && (!reclaimOwnedData || result.dataReclaimed());
        if (complete) markGraphsRetired(snapshot, checkpoint);
        return withCompletion(result, complete, null);
    }

    private Observation observation(NativeSnapshot snapshot, boolean stoppedProof, boolean absenceProof) {
        return observation(snapshot, stoppedProof, absenceProof, null);
    }

    private Observation observation(NativeSnapshot snapshot, boolean stoppedProof, boolean absenceProof, Checkpoint deletion) {
        check(snapshot);
        var observations = snapshot.resources().stream().map(resource -> observeResource(snapshot, resource, stoppedProof,
            absenceProof, false, deletion != null && deletion.isKnown(effect(snapshot, resource, "DELETE")))).toList();
        boolean stopped = observations.stream().allMatch(value -> (value.state() == State.STOPPED || value.state() == State.ABSENT)
            && (!stoppedProof || value.containersAbsent()));
        boolean absent = observations.stream().allMatch(value -> value.state() == State.ABSENT && value.containersAbsent());
        boolean ready = observations.stream().allMatch(this::ready);
        boolean reclaimed = observations.stream().allMatch(ResourceObservation::volumesAbsent);
        List<String> blockers = observations.stream().flatMap(value -> value.blockers().stream()).distinct().toList();
        return new Observation(snapshot.scopeHash(), Instant.now(), observations, stopped, absent, ready, reclaimed,
            blockers.isEmpty() && (stoppedProof ? stopped : absenceProof ? absent : true), blockers);
    }

    private ResourceObservation observeResource(NativeSnapshot snapshot, NativeResource resource, boolean stoppedProof, boolean absenceProof) {
        return observeResource(snapshot, resource, stoppedProof, absenceProof, false);
    }

    private ResourceObservation observeResource(NativeSnapshot snapshot, NativeResource resource, boolean stoppedProof,
            boolean absenceProof, boolean workerProof) {
        return observeResource(snapshot, resource, stoppedProof, absenceProof, workerProof, false);
    }

    private ResourceObservation observeResource(NativeSnapshot snapshot, NativeResource resource, boolean stoppedProof,
            boolean absenceProof, boolean workerProof, boolean deleteReadback) {
        var connection = connection(target(snapshot.targetProfileId()));
        requireConnection(resource, connection);
        JsonNode raw;
        if (resource.origin() == Origin.HANDLE) {
            var handle = requireHandle(snapshot, resource);
            ownership.requireRetirementOwnership(handle);
            raw = raw(connection, resource.kind(), resource.uuid());
        } else {
            var node = requireNode(snapshot, resource);
            raw = dependencies.inspectOwnedLifecycleResources(connection, resource.dependency().graphId(),
                resource.dependency().serviceRef(), resource.dependency().slot(), ownedResources(node)).stream()
                .filter(value -> resource.uuid().equals(value.uuid()) && resource.kind().equals(value.kind()))
                .findFirst().orElseThrow(() -> failure("NATIVE_DEPENDENCY_INVENTORY_CHANGED")).raw();
        }
        var blockers = new ArrayList<String>();
        State state = raw == null ? State.ABSENT : state(raw.path("status").asText(null));
        String status = raw == null ? "ABSENT" : raw.path("status").asText("UNKNOWN");
        if (raw != null) {
            if (!resource.identityHash().equals(identity(raw, resource.name(), resource.marker())))
                throw failure("NATIVE_RESOURCE_IDENTITY_CHANGED");
            requireNativePlacement(resource, raw);
            var observedVolumes = storage(connection, resource.kind(), resource.uuid());
            // An exact persisted DELETE can remove storage rows before its resource disappears.
            // This is observation only: dispatch() still requires equality, and no new/edited row is accepted.
            if (deleteReadback ? !resource.volumes().containsAll(observedVolumes) : !resource.volumes().equals(observedVolumes))
                throw failure("NATIVE_VOLUME_INVENTORY_CHANGED");
        }
        boolean containersAbsent = false;
        if (state == State.ABSENT || stoppedProof && state == State.STOPPED) {
            try {
                if (state == State.ABSENT && !workerProof) volumes.verifyContainersAbsent(resource.placement().targetIntent(),
                    resource.placement().serverUuid(), snapshot.scopeHash() + ":" + resource.id(),
                    resource.database() ? "database" : "application", resource.uuid());
                else requireNoRunning(resource);
                containersAbsent = true;
            } catch (ProviderResourceVerificationFailure failure) { blockers.add("NATIVE_CONTAINER_ABSENCE_UNPROVEN"); }
        }
        boolean volumesAbsent = resource.volumes().isEmpty();
        if (!volumesAbsent && state == State.ABSENT && !workerProof) volumesAbsent = volumesAbsent(resource);
        if (state == State.UNKNOWN) blockers.add("NATIVE_RESOURCE_STATE_UNKNOWN");
        if (stoppedProof && state != State.STOPPED && state != State.ABSENT) blockers.add("NATIVE_RESOURCE_NOT_STOPPED");
        if (absenceProof && state != State.ABSENT) blockers.add("NATIVE_RESOURCE_STILL_PRESENT");
        return new ResourceObservation(resource.id(), state, status, containersAbsent, volumesAbsent, blockers);
    }

    private CoolifyActionResponse dispatch(NativeSnapshot snapshot, NativeResource resource, String action, boolean reclaim) {
        var connection = connection(target(snapshot.targetProfileId()));
        requireConnection(resource, connection);
        observeResource(snapshot, resource, false, false);
        if (resource.origin() == Origin.DEPENDENCY) {
            var node = requireNode(snapshot, resource);
            return dependencies.dispatchOwnedLifecycleResource(connection, resource.dependency().graphId(),
                resource.dependency().serviceRef(), resource.dependency().slot(), ownedResources(node), resource.uuid(), action, reclaim);
        }
        var handle = requireHandle(snapshot, resource);
        ownership.requireRetirementOwnership(handle);
        if ("DELETE".equals(action)) {
            ownership.beginRetirementDeletion(handle);
            return resource.database() ? api.deleteDatabase(connection, resource.uuid(), true, false, false, false)
                : api.delete(connection, resource.uuid(), true, false, false, false);
        }
        if ("STOP".equals(action)) return resource.database() ? api.stopDatabase(connection, resource.uuid())
            : api.stop(connection, resource.uuid(), false);
        return resource.database() ? api.startDatabase(connection, resource.uuid())
            : api.start(connection, resource.uuid(), false, true);
    }

    private void finishDeletion(NativeSnapshot snapshot, NativeResource resource, boolean reclaim) {
        if (resource.origin() == Origin.HANDLE) {
            var handle = requireHandle(snapshot, resource);
            ownership.finishRetirementDeletion(handle);
            handle.setStatus("DELETED"); handle.setLastObservedStatus("ABSENT");
            if (reclaim && !resource.volumes().isEmpty()) handle.setVolumeReclamationReceipt("native-lifecycle:" + snapshot.scopeHash());
            handle.setLastObservedAt(Instant.now()); handle.setUpdatedAt(Instant.now()); handles.saveAndFlush(handle);
        } else {
            var node = requireNode(snapshot, resource);
            var connection = connection(target(snapshot.targetProfileId()));
            dependencies.finishOwnedLifecycleResource(connection, resource.dependency().graphId(), resource.dependency().serviceRef(),
                resource.dependency().slot(), ownedResources(node), resource.uuid());
            if (reclaim && !resource.volumes().isEmpty()) dependencies.finishOwnedLifecycleVolumes(connection,
                resource.dependency().graphId(), resource.dependency().serviceRef(), resource.dependency().slot(),
                ownedResources(node), resource.uuid());
        }
    }

    private boolean nativeJobComplete(NativeSnapshot snapshot, NativeResource resource, String operationId) {
        if (resource.database() && ("database-start-ack:" + resource.uuid()).equals(operationId)) return true;
        var job = api.getDeployment(connection(target(snapshot.targetProfileId())), required(operationId))
            .orElseThrow(() -> failure("NATIVE_START_JOB_READBACK_UNAVAILABLE"));
        if (!operationId.equals(job.deploymentUuid()) || !resource.uuid().equals(job.applicationUuid()))
            throw failure("NATIVE_START_JOB_IDENTITY_MISMATCH");
        String status = job.status() == null ? "" : job.status().toLowerCase(Locale.ROOT);
        if (Set.of("failed", "cancelled", "canceled").contains(status)) throw failure("NATIVE_START_JOB_FAILED");
        return "finished".equals(status) && job.finishedAt() != null;
    }

    private void check(NativeSnapshot snapshot) {
        noTransaction();
        if (snapshot == null || !Objects.equals(snapshot.scopeHash(), scopeHash(snapshot))
            || snapshot.resources().isEmpty() || snapshot.resources().size() > MAX_RESOURCES)
            throw failure("NATIVE_SNAPSHOT_INVALID");
        var deployment = deployment(snapshot.deploymentId());
        if (!snapshot.customerId().equals(customer(deployment.getCustomerId())) || !snapshot.tenantId().equals(deployment.getTenantId()))
            throw failure("NATIVE_OWNER_CHANGED");
        if (!snapshot.targetFingerprint().equals(targetFingerprint(target(snapshot.targetProfileId())))
            || !snapshot.releaseFingerprint().equals(releaseFingerprint(release(snapshot.deploymentId(), snapshot.targetProfileId(), snapshot.releaseId()))))
            throw failure("NATIVE_SCOPE_CHANGED");
        var current = handles.findByDeploymentIdAndTargetProfileIdOrderByUpdatedAtDesc(snapshot.deploymentId(), snapshot.targetProfileId());
        var expectedIds = snapshot.resources().stream().filter(value -> value.origin() == Origin.HANDLE).map(NativeResource::id).collect(java.util.stream.Collectors.toSet());
        if (!expectedIds.equals(current.stream().filter(value -> !PreservedExternalBindingKinds.isPreserved(value.getResourceKind()))
            .filter(value -> !"DELETED".equals(value.getStatus()) || expectedIds.contains(value.getId()))
            .map(DeploymentProviderResourceHandleEntity::getId).collect(java.util.stream.Collectors.toSet())))
            throw failure("NATIVE_RESOURCE_INVENTORY_CHANGED");
        var exclusions = current.stream().filter(value -> PreservedExternalBindingKinds.isPreserved(value.getResourceKind()))
            .map(value -> new Exclusion(value.getId(), value.getResourceKind(), handleFingerprint(value)))
            .sorted(Comparator.comparing(Exclusion::id)).toList();
        if (!snapshot.exclusions().equals(exclusions)) throw failure("NATIVE_EXTERNAL_BINDING_CHANGED");
        var graph = graphs.find(snapshot.deploymentId(), snapshot.targetProfileId());
        if (snapshot.graphs().isEmpty() != graph.isEmpty()) throw failure("NATIVE_DEPENDENCY_INVENTORY_CHANGED");
        if (graph.isPresent()) {
            requireGraph(graph.get(), deployment, snapshot.releaseId());
            if (!snapshot.graphs().equals(List.of(graphIdentity(graph.get())))) throw failure("NATIVE_DEPENDENCY_INVENTORY_CHANGED");
        }
    }

    private DeploymentProviderResourceHandleEntity requireHandle(NativeSnapshot snapshot, NativeResource resource) {
        var handle = handles.findById(resource.id()).orElseThrow(() -> failure("NATIVE_HANDLE_MISSING"));
        if (!snapshot.deploymentId().equals(handle.getDeploymentId()) || !snapshot.targetProfileId().equals(handle.getTargetProfileId())
            || !resource.uuid().equals(handle.getProviderResourceUuid()) || !resource.kind().equals(handle.getResourceKind())
            || !resource.handle().metadataHash().equals(handleFingerprint(handle))) throw failure("NATIVE_HANDLE_IDENTITY_CHANGED");
        return handle;
    }

    private ManagedDependencyState.Node requireNode(NativeSnapshot snapshot, NativeResource resource) {
        var graph = graphs.read(resource.dependency().graphId());
        requireGraph(graph, deployment(snapshot.deploymentId()), snapshot.releaseId());
        if (snapshot.graphs().stream().noneMatch(graphIdentity(graph)::equals)) throw failure("NATIVE_DEPENDENCY_INVENTORY_CHANGED");
        return orderedNodes(graph).stream().filter(node -> resource.dependency().slot().equals(node.slot)
            && resource.dependency().serviceRef().equals(node.serviceRef)).findFirst()
            .orElseThrow(() -> failure("NATIVE_DEPENDENCY_INVENTORY_CHANGED"));
    }

    private void requireGraph(ManagedDependencyGraphEntity graph, DeploymentEntity deployment, String releaseId) {
        if (!deployment.getId().equals(graph.getDeploymentId()) || !customer(deployment.getCustomerId()).equals(customer(graph.getCustomerId()))
            || !deployment.getTenantId().equals(graph.getTenantId()) || !releaseId.equals(graph.getReleaseId())
            || graph.getWorkerToken() != null) throw failure("NATIVE_DEPENDENCY_SCOPE_UNAVAILABLE");
    }

    private List<ManagedDependencyState.Node> orderedNodes(ManagedDependencyGraphEntity graph) {
        return graphs.state(graph).nodes.stream().sorted(Comparator.comparing(node -> required(node.slot))).toList();
    }

    private GraphIdentity graphIdentity(ManagedDependencyGraphEntity graph) {
        var fields = orderedNodes(graph).stream().map(node -> {
            var values = new TreeMap<String, Object>();
            values.put("slot", node.slot); values.put("serviceRef", node.serviceRef);
            values.put("applicationUuid", node.applicationUuid); values.put("databaseUuid", node.databaseUuid);
            values.put("applicationCreateIntent", node.applicationCreateIntent); values.put("databaseCreateIntent", node.databaseCreateIntent);
            values.put("volumes", node.volumes);
            return values;
        }).toList();
        return new GraphIdentity(graph.getId(), graph.getVersionId(), graph.getReleaseId(), graph.getSpecFingerprint(),
            graph.getBindingFingerprint(), fingerprints.hash(fields));
    }

    private void markGraphsRetired(NativeSnapshot snapshot, Checkpoint checkpoint) {
        for (var frozen : snapshot.graphs()) {
            checkpoint.guard();
            graphs.mutate(frozen.id(), null, (graph, state) -> {
                if (graph.getWorkerToken() != null || !frozen.equals(graphIdentity(graph)))
                    throw failure("NATIVE_DEPENDENCY_INVENTORY_CHANGED");
                graph.setStatus("RETIRED"); graph.setStep("NATIVE_ABSENCE_VERIFIED"); graph.setFailureCode(null);
                state.retirementFingerprint = "native-lifecycle:" + snapshot.scopeHash();
                for (var node : state.nodes) {
                    node.disabled = true; node.status = "RETIRED";
                    var plan = new TreeMap<String, String>();
                    snapshot.resources().stream().filter(resource -> resource.origin() == Origin.DEPENDENCY
                        && frozen.id().equals(resource.dependency().graphId()) && node.slot.equals(resource.dependency().slot()))
                        .flatMap(resource -> resource.volumes().stream()).forEach(volume -> plan.put(volume.uuid(), volume.name()));
                    node.volumeReclamationPlan = plan;
                    node.reclaimedVolumeUuids = new ArrayList<>(plan.keySet());
                }
            });
        }
    }

    private PlatformManagedProductProvisioningService.OwnedDependencyResources ownedResources(ManagedDependencyState.Node node) {
        return new PlatformManagedProductProvisioningService.OwnedDependencyResources(node.applicationUuid, node.databaseUuid,
            node.applicationCreateIntent, node.databaseCreateIntent, node.volumes, node.volumeReclamationPlan, Set.copyOf(node.reclaimedVolumeUuids));
    }

    private CoolifyConnection connection(DeploymentTargetProfileEntity target) {
        var connection = resolver.requireConnection(target);
        var intent = intent(target);
        var trusted = customerProperties.trustedServers().get(intent);
        if (trusted == null || !Objects.equals(trusted.uuid(), connection.config().serverUuid()))
            throw failure("NATIVE_SERVER_PLACEMENT_UNTRUSTED");
        JsonNode server = api.getServer(connection, connection.config().serverUuid());
        if (server == null || !trusted.uuid().equals(server.path("uuid").asText(null))
            || !required(trusted.reportedAddress()).equals(server.path("ip").asText(null)))
            throw failure("NATIVE_SERVER_PLACEMENT_CHANGED");
        var destination = api.resolveDestinationPlacement(connection, connection.config().serverUuid(), connection.config().destinationUuid())
            .orElseThrow(() -> failure("NATIVE_DESTINATION_UNVERIFIED"));
        if (!connection.config().serverUuid().equals(destination.serverUuid())) throw failure("NATIVE_DESTINATION_CHANGED");
        return connection;
    }

    private Placement placement(CoolifyConnection connection, String project, String environment, DeploymentTargetProfileEntity target) {
        return new Placement(endpoint(connection), required(project), required(environment), required(connection.config().serverUuid()),
            required(connection.config().destinationUuid()), intent(target));
    }

    private void requireNativePlacement(NativeResource resource, JsonNode raw) {
        String server = raw.at("/destination/server/uuid").asText(raw.path("server_uuid").asText(null));
        String destination = raw.at("/destination/uuid").asText(raw.path("destination_uuid").asText(null));
        if (!resource.placement().serverUuid().equals(server) || !resource.placement().destinationUuid().equals(destination))
            throw failure("NATIVE_RESOURCE_PLACEMENT_CHANGED");
        // Parent membership is independently required by the ownership helper's scoped inventory.
        if (raw.hasNonNull("project_uuid") && !resource.placement().projectUuid().equals(raw.path("project_uuid").asText(null))
            || raw.hasNonNull("environment_uuid") && !resource.placement().environmentUuid().equals(raw.path("environment_uuid").asText(null)))
            throw failure("NATIVE_RESOURCE_PLACEMENT_CHANGED");
    }

    private void requireConnection(NativeResource resource, CoolifyConnection connection) {
        if (!resource.placement().controlPlane().equals(endpoint(connection))
            || !resource.placement().serverUuid().equals(connection.config().serverUuid())
            || !resource.placement().destinationUuid().equals(connection.config().destinationUuid()))
            throw failure("NATIVE_RESOURCE_CONNECTION_CHANGED");
    }

    private JsonNode raw(CoolifyConnection connection, String kind, String uuid) {
        return "RUNTIME_POSTGRES_DATABASE".equals(kind) ? api.getDatabase(connection, uuid).map(value -> value.raw()).orElse(null)
            : api.getApplication(connection, uuid).map(value -> value.raw()).orElse(null);
    }

    private List<Volume> storage(CoolifyConnection connection, String kind, String uuid) {
        return storage("RUNTIME_POSTGRES_DATABASE".equals(kind) ? api.listDatabaseStorages(connection, uuid) : api.listApplicationStorages(connection, uuid));
    }

    private List<Volume> storage(List<CoolifyApplicationStorageSummary> storages) {
        if (storages == null || storages.size() > 32) throw failure("NATIVE_STORAGE_INVENTORY_INVALID");
        var result = storages.stream().map(value -> {
            if (!"persistent".equals(value.type()) || value.raw() == null
                || !required(value.uuid()).equals(value.raw().path("uuid").asText(null))
                || !required(value.name()).equals(value.raw().path("name").asText(null))
                || !required(value.mountPath()).equals(value.raw().path("mount_path").asText(null)))
                throw failure("NATIVE_STORAGE_INVENTORY_INVALID");
            return new Volume(value.uuid(), value.type(), required(value.name()), value.mountPath(), value.hostPath(),
                value.hostPath() != null && !value.hostPath().isBlank());
        }).sorted(Comparator.comparing(Volume::uuid)).toList();
        if (result.stream().map(Volume::uuid).distinct().count() != result.size()
            || result.stream().map(Volume::name).distinct().count() != result.size()) throw failure("NATIVE_STORAGE_INVENTORY_INVALID");
        return result;
    }

    private void requireExclusiveVolumes(NativeSnapshot snapshot, CoolifyConnection connection) {
        Set<String> names = new HashSet<>();
        Set<String> ids = new HashSet<>();
        for (var resource : snapshot.resources()) for (var volume : resource.volumes()) {
            if (!names.add(volume.name()) || !ids.add(volume.uuid())) throw failure("NATIVE_VOLUME_SHARED");
        }
        if (names.isEmpty()) return;
        Set<String> own = snapshot.resources().stream().map(NativeResource::uuid).collect(java.util.stream.Collectors.toSet());
        var apps = api.listApplications(connection);
        var dbs = api.listDatabases(connection);
        if (apps.size() + dbs.size() > MAX_NATIVE_INVENTORY) throw failure("NATIVE_VOLUME_INVENTORY_TOO_LARGE");
        for (var app : apps) if (!own.contains(app.uuid())) requireNotShared(names, ids, api.listApplicationStorages(connection, app.uuid()));
        for (var db : dbs) if (!own.contains(db.uuid())) requireNotShared(names, ids, api.listDatabaseStorages(connection, db.uuid()));
    }

    private void requireNotShared(Set<String> names, Set<String> ids, List<CoolifyApplicationStorageSummary> observed) {
        if (observed == null || observed.stream().anyMatch(volume -> names.contains(volume.name()) || ids.contains(volume.uuid())))
            throw failure("NATIVE_VOLUME_SHARED");
    }

    private void requireNoRunning(NativeResource resource) {
        for (var command : containerCommands(resource, false)) {
            var result = host.run(resource.placement().targetIntent(), resource.placement().serverUuid(), command);
            if (result.exitCode() != 0 || !result.output().isBlank())
                throw new ProviderResourceVerificationFailure("NATIVE_RUNNING_CONTAINER_REMAINS");
        }
    }

    private List<List<String>> containerCommands(NativeResource resource, boolean all) {
        String kind = resource.database() ? "database" : "application";
        var prefix = all ? List.of("docker", "ps", "-a", "--quiet", "--filter") : List.of("docker", "ps", "--quiet", "--filter");
        var commands = new ArrayList<List<String>>();
        var exact = new ArrayList<>(prefix); exact.add("label=coolify." + kind + "Uuid=" + resource.uuid()); commands.add(exact);
        var legacy = new ArrayList<>(prefix); legacy.add("label=coolify." + kind + "Id");
        legacy.add("--filter"); legacy.add("label=com.docker.compose.project=" + resource.uuid()); commands.add(legacy);
        if (!resource.database()) {
            var old = new ArrayList<>(prefix); old.add("label=coolify.applicationId"); old.add("--filter"); old.add("name=" + resource.uuid()); commands.add(old);
        }
        return commands;
    }

    private boolean volumesAbsent(NativeResource resource) {
        for (var volume : resource.volumes()) {
            if (volume.retained()) return false;
            if (!volume.name().matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")) throw failure("NATIVE_VOLUME_NAME_INVALID");
            var result = host.run(resource.placement().targetIntent(), resource.placement().serverUuid(),
                List.of("docker", "volume", "ls", "--quiet", "--filter", "name=^" + volume.name() + "$"));
            if (result.exitCode() != 0) throw failure("NATIVE_VOLUME_READBACK_FAILED");
            if (!result.output().isBlank()) {
                if (!volume.name().equals(result.output().strip())) throw failure("NATIVE_VOLUME_READBACK_INVALID");
                return false;
            }
        }
        return true;
    }

    private List<NativeResource> ordered(NativeSnapshot snapshot, boolean start) {
        var comparator = Comparator.comparingInt(this::rank).thenComparing(NativeResource::id);
        return snapshot.resources().stream().sorted(start ? comparator.reversed() : comparator).toList();
    }

    private int rank(NativeResource resource) {
        if ("CONNECTOR_APPLICATION".equals(resource.kind()) || "VECTORIZATION_RUNNER_APPLICATION".equals(resource.kind())) return 0;
        if ("DEPENDENCY_APPLICATION".equals(resource.kind())) return 1;
        return resource.database() ? 3 : 2;
    }

    private String identity(JsonNode raw, String name, String marker) {
        if (raw == null || !required(name).equals(raw.path("name").asText(null))
            || !required(marker).equals(raw.path("description").asText(null))) throw failure("NATIVE_RESOURCE_OWNERSHIP_CHANGED");
        if (!raw.path("created_at").isTextual() || raw.path("created_at").textValue().isBlank())
            throw failure("NATIVE_RESOURCE_GENERATION_UNAVAILABLE");
        var fields = new TreeMap<String, Object>();
        for (String field : List.of("uuid", "name", "description", "created_at", "environment_id", "server_uuid", "destination_uuid", "project_uuid", "environment_uuid",
                "docker_registry_image_name", "docker_registry_image_tag", "git_repository", "git_branch", "dockerfile_location", "base_directory", "ports_exposes"))
            fields.put(field, raw.path(field));
        fields.put("destinationUuid", raw.at("/destination/uuid"));
        fields.put("destinationServerUuid", raw.at("/destination/server/uuid"));
        return fingerprints.hash(fields);
    }

    private String handleFingerprint(DeploymentProviderResourceHandleEntity handle) {
        var fields = new TreeMap<String, Object>();
        fields.put("id", handle.getId()); fields.put("deployment", handle.getDeploymentId()); fields.put("target", handle.getTargetProfileId());
        fields.put("release", handle.getReleaseId()); fields.put("provider", handle.getProviderType()); fields.put("kind", handle.getResourceKind());
        fields.put("uuid", handle.getProviderResourceUuid()); fields.put("project", handle.getProviderProjectUuid());
        fields.put("environment", handle.getProviderEnvironmentUuid()); fields.put("server", handle.getProviderServerUuid());
        fields.put("created", handle.getCreatedAt()); fields.put("metadata", document(handle.getMetadataJson()));
        return fingerprints.hash(fields);
    }

    private String targetFingerprint(DeploymentTargetProfileEntity target) {
        var values = new TreeMap<String, Object>();
        values.put("id", target.getId()); values.put("provider", target.getProviderType()); values.put("environment", target.getEnvironmentName());
        values.put("credential", target.getCredentialRefId()); values.put("config", document(target.getProviderConfigJson()));
        values.put("network", document(target.getNetworkPolicyJson())); values.put("defaults", document(target.getResourceDefaultsJson()));
        return fingerprints.hash(values);
    }

    private String releaseFingerprint(DeploymentReleaseEntity release) {
        var values = new TreeMap<String, Object>();
        values.put("id", release.getId()); values.put("deployment", release.getDeploymentId()); values.put("target", release.getTargetProfileId());
        values.put("version", release.getDeploymentVersionId()); values.put("source", release.getSourceArtifactId());
        values.put("authority", release.getRuntimeAuthorityFingerprint()); values.put("created", release.getCreatedAt());
        values.put("provider", release.getProviderType());
        return fingerprints.hash(values);
    }

    private String scopeHash(NativeSnapshot value) {
        return fingerprints.hash(new NativeSnapshot(value.customerId(), value.tenantId(), value.deploymentId(), value.targetProfileId(),
            value.releaseId(), value.targetFingerprint(), value.releaseFingerprint(), value.resources(), value.graphs(), value.exclusions(), null, null));
    }

    private Observation pending(NativeSnapshot snapshot, String code) { return withCompletion(observation(snapshot, false, false), false, code); }
    private Observation pendingDeletion(NativeSnapshot snapshot, Checkpoint checkpoint, String code) {
        return withCompletion(observation(snapshot, false, false, checkpoint), false, code);
    }
    private Observation withCompletion(Observation value, boolean complete, String code) {
        var blockers = new ArrayList<>(value.blockers()); if (code != null && !blockers.contains(code)) blockers.add(code);
        return new Observation(value.scopeHash(), value.observedAt(), value.resources(), value.stopped(), value.absent(), value.ready(), value.dataReclaimed(), complete, blockers);
    }
    private boolean ready(ResourceObservation value) { return value.state() == State.RUNNING && "running:healthy".equalsIgnoreCase(value.providerStatus()); }
    private State state(String raw) {
        if (raw == null) return State.UNKNOWN;
        return switch (raw.toLowerCase(Locale.ROOT).split(":", 2)[0]) {
            case "running" -> State.RUNNING;
            case "exited", "stopped" -> State.STOPPED;
            case "starting", "restarting", "pending", "deploying" -> State.TRANSITIONING;
            default -> State.UNKNOWN;
        };
    }
    private String effect(NativeSnapshot snapshot, NativeResource resource, String action) { return "native:" + snapshot.scopeHash() + ":" + action + ":" + resource.id(); }
    private DeploymentEntity deployment(String id) { return deployments.findById(required(id)).orElseThrow(() -> failure("NATIVE_DEPLOYMENT_MISSING")); }
    private DeploymentTargetProfileEntity target(String id) {
        var target = targets.findById(required(id)).orElseThrow(() -> failure("NATIVE_TARGET_MISSING"));
        if (!target.isActive() || target.getProviderType() != DeploymentProviderType.COOLIFY) throw failure("NATIVE_PROVIDER_UNSUPPORTED");
        intent(target); return target;
    }
    private TargetIntent intent(DeploymentTargetProfileEntity target) {
        return switch (target.getEnvironmentName()) {
            case "staging" -> TargetIntent.CUSTOMER_STAGING;
            case "production" -> TargetIntent.CUSTOMER_PRODUCTION;
            default -> throw failure("NATIVE_ENVIRONMENT_UNSUPPORTED");
        };
    }
    private DeploymentReleaseEntity release(String deployment, String target, String id) {
        var release = releases.findById(required(id)).orElseThrow(() -> failure("NATIVE_RELEASE_MISSING"));
        if (!deployment.equals(release.getDeploymentId()) || !target.equals(release.getTargetProfileId())
            || release.getProviderType() != DeploymentProviderType.COOLIFY) throw failure("NATIVE_RELEASE_SCOPE_CHANGED");
        return release;
    }
    private JsonNode document(String json) {
        if (json == null || json.isBlank()) return mapper.createObjectNode();
        try { return mapper.readTree(json); } catch (Exception error) { throw failure("NATIVE_PERSISTED_IDENTITY_INVALID"); }
    }
    private String endpoint(CoolifyConnection connection) { return required(connection.baseUrl()).replaceAll("/+$", ""); }
    private String customer(String value) { return value == null ? "platform" : required(value); }
    private void noTransaction() { if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Native lifecycle calls must run outside database transactions."); }
    private static String required(String value) {
        if (value == null || value.isBlank() || !value.equals(value.strip()) || value.chars().anyMatch(Character::isISOControl)) throw failure("NATIVE_IDENTITY_INCOMPLETE");
        return value;
    }
    private static ResponseStatusException failure(String code) { return new ResponseStatusException(HttpStatus.CONFLICT, code); }
}
