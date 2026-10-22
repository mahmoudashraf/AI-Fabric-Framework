package com.ai.fabric.platform.backend.deployment.retirement;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentProviderResourceHandleEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.model.PreservedExternalBindingKinds;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentProviderResourceHandleRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.service.CoolifyApiClient;
import com.ai.fabric.platform.backend.deployment.service.CoolifyApiException;
import com.ai.fabric.platform.backend.deployment.service.CoolifyApplicationStorageSummary;
import com.ai.fabric.platform.backend.deployment.service.CoolifyTargetProfileResolver;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerBindingHistoryRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map;
import java.util.stream.Collectors;

import static com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.*;

@Service
public class TargetRetirementInventory {
    private static final Set<String> APPLICATION_KINDS = Set.of(
        "APPLICATION", "CONNECTOR_APPLICATION", "VECTORIZATION_RUNNER_APPLICATION"
    );
    private static final Set<String> PROVIDER_KINDS = Set.of(
        "APPLICATION", "CONNECTOR_APPLICATION", "VECTORIZATION_RUNNER_APPLICATION", "RUNTIME_POSTGRES_DATABASE"
    );
    private final DeploymentRepository deployments;
    private final DeploymentTargetProfileRepository targets;
    private final DeploymentReleaseRepository releases;
    private final DeploymentProviderResourceHandleRepository handles;
    private final PlatformConsumerRepository consumers;
    private final PlatformConsumerBindingHistoryRepository consumerBindingHistory;
    private final TargetRetirementJson json;
    private final CoolifyTargetProfileResolver resolver;
    private final CoolifyApiClient client;
    private final List<String> approvedRetainedVolumeMounts;

    public TargetRetirementInventory(DeploymentRepository deployments, DeploymentTargetProfileRepository targets,
                                     DeploymentReleaseRepository releases, DeploymentProviderResourceHandleRepository handles,
                                     PlatformConsumerRepository consumers,
                                     PlatformConsumerBindingHistoryRepository consumerBindingHistory,
                                     TargetRetirementJson json,
                                     CoolifyTargetProfileResolver resolver, CoolifyApiClient client,
                                     @Value("${platform.target-retirement.approved-retained-volume-mounts:}") List<String> retainedMounts) {
        this.deployments = deployments;
        this.targets = targets;
        this.releases = releases;
        this.handles = handles;
        this.consumers = consumers;
        this.consumerBindingHistory = consumerBindingHistory;
        this.json = json;
        this.resolver = resolver;
        this.client = client;
        this.approvedRetainedVolumeMounts = List.copyOf(retainedMounts);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Snapshot capture(String deploymentId, String targetId, PlanRequest request, long epoch) {
        return capture(deploymentId, targetId, request, epoch, null, true, true);
    }

    /** Stop-only inventory: freeze storage, but grant no volume reclamation authority. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Snapshot captureRetainingStorage(String deploymentId, String targetId, PlanRequest request, long epoch) {
        return capture(deploymentId, targetId, request, epoch, null, true, false);
    }

    private Snapshot capture(String deploymentId, String targetId, PlanRequest request, long epoch, Snapshot expected,
                             boolean observeProvider, boolean reclaimStorage) {
        var deployment = deployments.findById(deploymentId).orElseThrow(() -> failure("DEPLOYMENT_MISSING"));
        if (deployment.getCustomerId() == null || deployment.getCustomerId().isBlank()
            || deployment.getTenantId() == null || deployment.getTenantId().isBlank()
            || deployment.getArchivedAt() != null) throw failure("OWNERSHIP_UNAVAILABLE");
        var target = targets.findById(targetId).orElseThrow(() -> failure("TARGET_MISSING"));
        if (!"staging".equals(target.getEnvironmentName()) || !target.isActive()) throw failure("STAGING_TARGET_REQUIRED");
        var selected = request.releaseIds().stream().sorted().map(id -> {
            var release = releases.findById(id).orElseThrow(() -> failure("RELEASE_MISSING"));
            if (!deploymentId.equals(release.getDeploymentId()) || !targetId.equals(release.getTargetProfileId())
                || target.getProviderType() != release.getProviderType()) throw failure("RELEASE_OWNERSHIP_CHANGED");
            return new ReleaseGeneration(id, release.getDeploymentVersionId(), releaseGeneration(release));
        }).toList();
        Map<String, ResourceGeneration> expectedResources = expected == null ? Map.of() : expected.resources().stream()
            .collect(Collectors.toMap(ResourceGeneration::handleId, value -> value));
        var resources = handles.findByDeploymentIdAndTargetProfileIdOrderByUpdatedAtDesc(deploymentId, targetId).stream()
            .sorted(Comparator.comparing(DeploymentProviderResourceHandleEntity::getId))
            .map(handle -> {
                boolean preserved = PreservedExternalBindingKinds.isPreserved(handle.getResourceKind());
                if (!preserved && (handle.getReleaseId() == null || !request.releaseIds().contains(handle.getReleaseId())
                    || !PROVIDER_KINDS.contains(handle.getResourceKind()))) throw failure("RESOURCE_INVENTORY_UNSUPPORTED");
                if (handle.getProviderType() != target.getProviderType()) throw failure("RESOURCE_PROVIDER_CHANGED");
                if (handle.getCreatedAt() == null || handle.getProviderResourceUuid() == null)
                    throw failure("RESOURCE_GENERATION_UNAVAILABLE");
                return resource(handle, preserved, target, expectedResources.get(handle.getId()), observeProvider);
            }).toList();
        if (resources.stream().noneMatch(r -> !r.preserved())) throw failure("RESOURCE_INVENTORY_EMPTY");
        var targetFields = new TreeMap<String, Object>();
        targetFields.put("providerType", target.getProviderType());
        targetFields.put("environmentName", target.getEnvironmentName());
        targetFields.put("credentialRefId", target.getCredentialRefId());
        targetFields.put("providerConfig", json.read(target.getProviderConfigJson(), Object.class));
        targetFields.put("networkPolicy", json.read(target.getNetworkPolicyJson(), Object.class));
        targetFields.put("resourceDefaults", json.read(target.getResourceDefaultsJson(), Object.class));
        var snapshot = new Snapshot(deployment.getCustomerId(), deployment.getTenantId(), deploymentId, targetId,
            json.fingerprint(targetFields), epoch, selected, resources, request.consumers());
        checkReferences(snapshot, request, observeProvider, reclaimStorage);
        return snapshot;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void recheck(Snapshot snapshot, PlanRequest request) {
        var current = capture(snapshot.deploymentId(), snapshot.targetProfileId(), request, snapshot.targetEpoch(), snapshot, true, true);
        if (!json.fingerprint(snapshot).equals(json.fingerprint(current))) throw failure("SNAPSHOT_GENERATION_CHANGED");
    }

    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public void recheckLocal(Snapshot snapshot, PlanRequest request) {
        var current = capture(snapshot.deploymentId(), snapshot.targetProfileId(), request, snapshot.targetEpoch(), snapshot, false, true);
        if (!json.fingerprint(snapshot).equals(json.fingerprint(current))) throw failure("SNAPSHOT_GENERATION_CHANGED");
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void recheckRetainingStorage(Snapshot snapshot, PlanRequest request) {
        var current = capture(snapshot.deploymentId(), snapshot.targetProfileId(), request, snapshot.targetEpoch(), snapshot, true, false);
        if (!json.fingerprint(snapshot).equals(json.fingerprint(current))) throw failure("SNAPSHOT_GENERATION_CHANGED");
    }

    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public void recheckLocalRetainingStorage(Snapshot snapshot, PlanRequest request) {
        var current = capture(snapshot.deploymentId(), snapshot.targetProfileId(), request, snapshot.targetEpoch(), snapshot, false, false);
        if (!json.fingerprint(snapshot).equals(json.fingerprint(current))) throw failure("SNAPSHOT_GENERATION_CHANGED");
    }

    private void checkReferences(Snapshot snapshot, PlanRequest request, boolean observeProvider, boolean reclaimStorage) {
        var expected = snapshot.consumers().stream().collect(Collectors.toMap(ConsumerExpectation::consumerEntityId,
            ConsumerExpectation::expectedBindingRevision));
        Set<String> releaseIds = snapshot.releases().stream().map(ReleaseGeneration::releaseId).collect(Collectors.toSet());
        for (var entry : expected.entrySet()) {
            var consumer = consumers.findById(entry.getKey()).orElseThrow(() -> failure("CONSUMER_MISSING"));
            if (!snapshot.customerId().equals(consumer.getCustomerId())) throw failure("CONSUMER_REVISION_CHANGED");
            if (consumer.getBindingRevision() == entry.getValue()) continue;
            boolean detached = consumer.getBindingRevision() == entry.getValue() + 1
                && consumer.getBoundDeploymentId() == null && consumer.getBoundReleaseId() == null
                && consumer.getBoundTargetProfileId() == null
                && consumerBindingHistory.findByConsumerEntityIdAndOperationKey(consumer.getId(), request.operationKey())
                    .filter(receipt -> snapshot.deploymentId().equals(receipt.getFromDeploymentId())
                        && snapshot.targetProfileId().equals(receipt.getFromTargetProfileId())
                        && receipt.getToDeploymentId() == null && receipt.getToReleaseId() == null
                        && receipt.getToTargetProfileId() == null
                        && Long.valueOf(entry.getValue()).equals(receipt.getFromBindingRevision())
                        && Long.valueOf(entry.getValue() + 1).equals(receipt.getToBindingRevision()))
                    .isPresent();
            if (!detached) throw failure("CONSUMER_REVISION_CHANGED");
        }
        for (var consumer : consumers.findAll()) {
            boolean sameDeployment = snapshot.deploymentId().equals(consumer.getBoundDeploymentId());
            boolean exactTargetReference = sameDeployment
                && snapshot.targetProfileId().equals(consumer.getBoundTargetProfileId());
            boolean exactReleaseReference = releaseIds.contains(consumer.getBoundReleaseId());
            boolean ambiguousTargetReference = sameDeployment && consumer.getBoundTargetProfileId() == null
                && !exactReleaseReference;
            if (ambiguousTargetReference) throw failure("TARGET_STILL_ASSIGNED");
            if ((exactTargetReference || exactReleaseReference) && !expected.containsKey(consumer.getId()))
                throw failure("TARGET_STILL_ASSIGNED");
            if (sameDeployment && !expected.containsKey(consumer.getId()))
                throw failure("CONSUMER_EXPECTATION_MISSING");
        }
        var allHandles = handles.findAll();
        var allReleases = releases.findAll();
        for (var resource : snapshot.resources()) {
            if (resource.preserved()) continue;
            for (var other : allHandles) {
                if (!resource.handleId().equals(other.getId()) && resource.providerType() == other.getProviderType()
                    && resource.providerResourceUuid().equals(other.getProviderResourceUuid())) throw failure("SHARED_RESOURCE_REFERENCE");
            }
            for (var other : allReleases) {
                if (resource.handleId().equals(other.getProviderResourceHandleId()) && !releaseIds.contains(other.getId()))
                    throw failure("SHARED_RELEASE_REFERENCE");
            }
        }
        if (reclaimStorage) requireExclusiveDatabaseVolumes(snapshot.resources(), allHandles, observeProvider);
        // Opaque provider/config/index/dependency references are proved by the mandatory interlock/participants.
    }

    private void requireExclusiveDatabaseVolumes(List<ResourceGeneration> resources,
                                                 List<DeploymentProviderResourceHandleEntity> allHandles,
                                                 boolean observeProvider) {
        var databaseResources = resources.stream()
            .filter(resource -> "RUNTIME_POSTGRES_DATABASE".equals(resource.resourceKind()))
            .toList();
        for (var database : databaseResources) {
            if (database.volumes().isEmpty()) throw failure("DATABASE_VOLUME_OWNERSHIP_UNPROVEN");
            if (database.volumes().stream().anyMatch(ProviderVolumeGeneration::preserved)) {
                throw failure("DATABASE_VOLUME_NOT_EXCLUSIVELY_OWNED");
            }
            for (var volume : database.volumes()) {
                if (volume.name() == null || volume.name().isBlank()) {
                    throw failure("DATABASE_VOLUME_OWNERSHIP_UNPROVEN");
                }
                if (!observeProvider) continue;
                for (var other : allHandles) {
                    if (database.handleId().equals(other.getId()) || other.getProviderType() != database.providerType()
                        || !Objects.equals(other.getProviderServerUuid(), database.serverUuid())
                        || !PROVIDER_KINDS.contains(other.getResourceKind()) || "DELETED".equals(other.getStatus())) {
                        continue;
                    }
                    var target = targets.findById(other.getTargetProfileId())
                        .orElseThrow(() -> failure("DATABASE_VOLUME_REFERENCE_INVENTORY_UNPROVEN"));
                    var connection = resolver.requireConnection(target);
                    List<CoolifyApplicationStorageSummary> observed;
                    try {
                        observed = APPLICATION_KINDS.contains(other.getResourceKind())
                            ? client.listApplicationStorages(connection, other.getProviderResourceUuid())
                            : client.listDatabaseStorages(connection, other.getProviderResourceUuid());
                    } catch (CoolifyApiException ex) {
                        if (ex.statusCode() != 404) throw ex;
                        if (!Objects.equals(other.getTargetProfileId(), target.getId())
                            || other.getProviderType() != target.getProviderType()
                            || other.getProviderServerUuid() == null
                            || !other.getProviderServerUuid().equals(connection.config().serverUuid())) {
                            throw failure("DATABASE_VOLUME_REFERENCE_INVENTORY_UNPROVEN");
                        }
                        // A missing storage endpoint alone cannot prove that its parent is absent.
                        boolean absent = APPLICATION_KINDS.contains(other.getResourceKind())
                            ? client.getApplication(connection, other.getProviderResourceUuid()).isEmpty()
                            : client.getDatabase(connection, other.getProviderResourceUuid()).isEmpty();
                        if (!absent) throw ex;
                        continue;
                    }
                    boolean shared = observed.stream().filter(storage -> "persistent".equals(storage.type()))
                        .anyMatch(storage -> volume.storageUuid().equals(storage.uuid())
                            || volume.name().equals(storage.name()));
                    if (shared) throw failure("SHARED_PERSISTENT_VOLUME_REFERENCE");
                }
            }
        }
    }

    public DeploymentProviderResourceHandleEntity requireResource(ResourceGeneration expected) {
        var handle = handles.findById(expected.handleId()).orElseThrow(() -> failure("RESOURCE_MISSING"));
        var target = targets.findById(handle.getTargetProfileId()).orElseThrow(() -> failure("TARGET_MISSING"));
        if (!Objects.equals(expected.generation(), resource(handle, expected.preserved(), target, expected, false).generation()))
            throw failure("RESOURCE_GENERATION_CHANGED");
        return handle;
    }

    public void requireProviderIdentity(ResourceGeneration expected, JsonNode observed) {
        if (expected.providerIdentityFingerprint() == null || expected.providerIdentityFingerprint().isBlank()) {
            throw failure("PROVIDER_RESOURCE_IDENTITY_UNFROZEN");
        }
        String fingerprint = providerIdentityFingerprint(expected, observed);
        if (!expected.providerIdentityFingerprint().equals(fingerprint)) {
            throw failure("PROVIDER_RESOURCE_GENERATION_CHANGED");
        }
    }

    public void requireVolumeInventory(ResourceGeneration expected,
                                       List<CoolifyApplicationStorageSummary> observed,
                                       boolean allowMissingDisposable) {
        var handle = requireResource(expected);
        requireVolumeInventory(expected, handle, observed, allowMissingDisposable);
    }

    private void requireVolumeInventory(ResourceGeneration expected,
                                        DeploymentProviderResourceHandleEntity handle,
                                        List<CoolifyApplicationStorageSummary> observed,
                                        boolean allowMissingDisposable) {
        var current = observedVolumes(handle, observed);
        var frozen = expected.volumes().stream().collect(Collectors.toMap(
            ProviderVolumeGeneration::storageUuid, value -> value
        ));
        for (var entry : current.entrySet()) {
            var prior = frozen.get(entry.getKey());
            if (prior == null || !prior.generation().equals(entry.getValue().generation())) {
                throw failure("PERSISTENT_VOLUME_GENERATION_CHANGED");
            }
        }
        for (var prior : expected.volumes()) {
            if ((prior.preserved() || !allowMissingDisposable) && !current.containsKey(prior.storageUuid())) {
                throw failure(prior.preserved()
                    ? "PERSISTENT_VOLUME_RETENTION_UNPROVEN"
                    : "PERSISTENT_VOLUME_GENERATION_CHANGED");
            }
        }
    }

    public void requireApplicationDeletionVolumeInventory(ResourceGeneration expected,
                                                           List<CoolifyApplicationStorageSummary> observed) {
        var handle = requireResource(expected);
        var current = observedVolumes(handle, observed);
        var frozen = expected.volumes().stream().collect(Collectors.toMap(
            ProviderVolumeGeneration::storageUuid, value -> value
        ));
        for (var entry : current.entrySet()) {
            var prior = frozen.get(entry.getKey());
            if (prior == null || !prior.generation().equals(entry.getValue().generation())) {
                throw failure("PERSISTENT_VOLUME_GENERATION_CHANGED");
            }
        }
        for (var prior : expected.volumes()) {
            boolean present = current.containsKey(prior.storageUuid());
            if (prior.preserved() == present) {
                throw failure(prior.preserved()
                    ? "PERSISTENT_VOLUME_RETENTION_UNPROVEN"
                    : "PERSISTENT_VOLUME_GENERATION_CHANGED");
            }
        }
    }

    public void requireVolumeDeletionProgress(ResourceGeneration expected,
                                              List<CoolifyApplicationStorageSummary> observed) {
        requireVolumeDeletionProgress(expected, requireResource(expected), observed);
    }

    private void requireVolumeDeletionProgress(ResourceGeneration expected,
                                               DeploymentProviderResourceHandleEntity handle,
                                               List<CoolifyApplicationStorageSummary> observed) {
        var current = observedVolumes(handle, observed);
        var frozen = expected.volumes().stream().collect(Collectors.toMap(
            ProviderVolumeGeneration::storageUuid, value -> value
        ));
        for (var entry : current.entrySet()) {
            var prior = frozen.get(entry.getKey());
            if (prior == null || !prior.generation().equals(entry.getValue().generation())) {
                throw failure("PERSISTENT_VOLUME_GENERATION_CHANGED");
            }
        }
    }

    @Transactional
    public void recordVolumeDeletionIntent(ResourceGeneration expected, Instant observedAt) {
        if (expected.volumes().isEmpty()) return;
        var handle = requireResource(expected);
        String intent = volumeDeletionIntentReceipt(expected);
        String existing = handle.getVolumeDeletionIntentReceipt();
        if (existing != null && !intent.equals(existing)) {
            throw failure("PROVIDER_VOLUME_DELETE_INTENT_CHANGED");
        }
        String disposition = handle.getVolumeReclamationReceipt();
        if (disposition != null && !volumeDispositionReceipt(expected).equals(disposition)) {
            throw failure("PROVIDER_VOLUME_RECLAMATION_RECEIPT_CHANGED");
        }
        handle.setVolumeDeletionIntentReceipt(intent);
        handle.setLastObservedAt(observedAt);
        handle.setUpdatedAt(Instant.now());
        handles.save(handle);
    }

    @Transactional(readOnly = true)
    public boolean volumeDeletionIntentRecorded(ResourceGeneration expected) {
        if (expected.volumes().isEmpty()) return true;
        var handle = handles.findById(expected.handleId()).orElseThrow(() -> failure("RESOURCE_MISSING"));
        return volumeDeletionIntentReceipt(expected).equals(handle.getVolumeDeletionIntentReceipt());
    }

    @Transactional
    public void recordVolumesReclaimed(ResourceGeneration expected, Instant observedAt) {
        if (expected.volumes().isEmpty()) return;
        var handle = requireResource(expected);
        if (!volumeDeletionIntentReceipt(expected).equals(handle.getVolumeDeletionIntentReceipt())) {
            throw failure("PROVIDER_VOLUME_DELETE_INTENT_REQUIRED");
        }
        String receipt = volumeDispositionReceipt(expected);
        if (handle.getVolumeReclamationReceipt() != null
            && !receipt.equals(handle.getVolumeReclamationReceipt())) {
            throw failure("PROVIDER_VOLUME_RECLAMATION_RECEIPT_CHANGED");
        }
        handle.setVolumeReclamationReceipt(receipt);
        handle.setLastObservedAt(observedAt);
        handle.setUpdatedAt(Instant.now());
        handles.save(handle);
    }

    @Transactional(readOnly = true)
    public boolean volumesReclaimed(ResourceGeneration expected) {
        if (expected.volumes().isEmpty()) return true;
        var handle = handles.findById(expected.handleId()).orElseThrow(() -> failure("RESOURCE_MISSING"));
        return volumeDispositionReceipt(expected).equals(handle.getVolumeReclamationReceipt());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordReclaimed(ResourceGeneration expected, Instant observedAt) {
        if (expected.preserved()) throw failure("CUSTOMER_SOURCE_PRESERVED");
        if (!volumesReclaimed(expected)) throw failure("PROVIDER_VOLUME_ABSENCE_UNPROVEN");
        var handle = requireResource(expected);
        handle.setStatus("DELETED");
        handle.setLastObservedStatus(expected.volumes().isEmpty()
            ? "ABSENT"
            : volumeDispositionReceipt(expected));
        handle.setLastObservedAt(observedAt);
        handle.setUpdatedAt(Instant.now());
        handles.save(handle);
    }

    private ResourceGeneration resource(DeploymentProviderResourceHandleEntity handle, boolean preserved,
                                        com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity target,
                                        ResourceGeneration expected, boolean observeProvider) {
        List<ProviderVolumeGeneration> volumes = observeProvider ? volumes(handle, target, expected) : expected.volumes();
        var fields = new TreeMap<String, Object>();
        fields.put("id", handle.getId());
        fields.put("deploymentId", handle.getDeploymentId());
        fields.put("releaseId", handle.getReleaseId());
        fields.put("targetProfileId", handle.getTargetProfileId());
        fields.put("providerType", handle.getProviderType());
        fields.put("resourceKind", handle.getResourceKind());
        fields.put("resourceUuid", handle.getProviderResourceUuid());
        fields.put("projectUuid", handle.getProviderProjectUuid());
        fields.put("environmentUuid", handle.getProviderEnvironmentUuid());
        fields.put("serverUuid", handle.getProviderServerUuid());
        fields.put("createdAt", handle.getCreatedAt());
        fields.put("metadata", json.read(handle.getMetadataJson(), Object.class));
        // Observation/status timestamps are not instance generations. Reactivation is fenced by the target epoch.
        String generation = json.fingerprint(fields);
        JsonNode metadata = json.read(handle.getMetadataJson(), JsonNode.class);
        String providerName = APPLICATION_KINDS.contains(handle.getResourceKind())
            ? metadata.path("applicationName").asText(null)
            : "RUNTIME_POSTGRES_DATABASE".equals(handle.getResourceKind())
                ? metadata.path("databaseName").asText(null) : null;
        String marker = APPLICATION_KINDS.contains(handle.getResourceKind())
            ? "Managed by AI Fabric deployment " + handle.getDeploymentId()
            : "RUNTIME_POSTGRES_DATABASE".equals(handle.getResourceKind())
                ? "Durable runtime chat database for AI Fabric deployment " + handle.getDeploymentId() : null;
        String destinationUuid = preserved ? null : resolver.requireConnection(target).config().destinationUuid();
        String providerFingerprint = expected == null ? observeProviderIdentity(handle, target, generation,
            providerName, marker, destinationUuid) : expected.providerIdentityFingerprint();
        if (expected != null && observeProvider && !"DELETED".equals(handle.getStatus())) {
            String observedFingerprint = observeProviderIdentity(handle, target, generation,
                providerName, marker, destinationUuid);
            if (observedFingerprint != null) providerFingerprint = observedFingerprint;
        }
        return new ResourceGeneration(handle.getId(), handle.getReleaseId(), handle.getProviderType(),
            handle.getResourceKind(), handle.getProviderResourceUuid(), handle.getProviderServerUuid(),
            handle.getProviderProjectUuid(), handle.getProviderEnvironmentUuid(), destinationUuid,
            providerName, marker, generation, providerFingerprint, volumes, preserved);
    }

    private String observeProviderIdentity(DeploymentProviderResourceHandleEntity handle,
                                           com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity target,
                                           String generation, String providerName, String marker,
                                           String destinationUuid) {
        if (!PROVIDER_KINDS.contains(handle.getResourceKind())) return null;
        var connection = resolver.requireConnection(target);
        JsonNode raw = APPLICATION_KINDS.contains(handle.getResourceKind())
            ? client.getApplication(connection, handle.getProviderResourceUuid()).map(value -> value.raw()).orElse(null)
            : client.getDatabase(connection, handle.getProviderResourceUuid()).map(value -> value.raw()).orElse(null);
        if (raw == null) return null;
        var expected = new ResourceGeneration(handle.getId(), handle.getReleaseId(), handle.getProviderType(),
            handle.getResourceKind(), handle.getProviderResourceUuid(), handle.getProviderServerUuid(),
            handle.getProviderProjectUuid(), handle.getProviderEnvironmentUuid(), destinationUuid,
            providerName, marker, generation, "pending", List.of(), false);
        return providerIdentityFingerprint(expected, raw);
    }

    private String providerIdentityFingerprint(ResourceGeneration expected, JsonNode raw) {
        if (raw == null) throw failure("PROVIDER_RESOURCE_READBACK_REQUIRED");
        String uuid = text(raw, "/uuid");
        String name = text(raw, "/name");
        String description = text(raw, "/description");
        String serverUuid = first(text(raw, "/server_uuid"), text(raw, "/destination/server/uuid"));
        String projectUuid = first(text(raw, "/project_uuid"), text(raw, "/environment/project/uuid"));
        String environmentUuid = first(text(raw, "/environment_uuid"), text(raw, "/environment/uuid"));
        String destinationUuid = first(text(raw, "/destination_uuid"), text(raw, "/destination/uuid"));
        if (projectUuid == null || environmentUuid == null) {
            // Coolify resource details expose environment_id; resolve it through the scoped environment API.
            var handle = handles.findById(expected.handleId()).orElseThrow(() -> failure("RESOURCE_MISSING"));
            var target = targets.findById(handle.getTargetProfileId()).orElseThrow(() -> failure("TARGET_MISSING"));
            var environment = client.getEnvironment(resolver.requireConnection(target), expected.projectUuid(),
                expected.environmentUuid()).orElseThrow(() -> failure("PROVIDER_RESOURCE_OWNERSHIP_CHANGED"));
            JsonNode numericId = raw.path("environment_id"), observedId = environment.raw().path("id");
            String members = APPLICATION_KINDS.contains(expected.resourceKind()) ? "applications" : "postgresqls";
            boolean member = false;
            for (JsonNode resource : environment.raw().path(members)) {
                if (Objects.equals(uuid, text(resource, "/uuid"))) member = true;
            }
            if (!Objects.equals(expected.environmentUuid(), environment.uuid())
                || !Objects.equals(expected.projectUuid(), environment.projectUuid())
                || !numericId.isIntegralNumber() || !numericId.canConvertToLong() || numericId.longValue() <= 0
                || !observedId.isIntegralNumber() || !observedId.canConvertToLong()
                || numericId.longValue() != observedId.longValue() || !member) {
                throw failure("PROVIDER_RESOURCE_OWNERSHIP_CHANGED");
            }
            if (projectUuid == null) projectUuid = environment.projectUuid();
            if (environmentUuid == null) environmentUuid = environment.uuid();
        }
        if (!Objects.equals(expected.providerResourceUuid(), uuid)
            || !Objects.equals(expected.providerName(), name)
            || !Objects.equals(expected.deploymentMarker(), description)
            || !Objects.equals(expected.serverUuid(), serverUuid)
            || !Objects.equals(expected.projectUuid(), projectUuid)
            || !Objects.equals(expected.environmentUuid(), environmentUuid)
            || !Objects.equals(expected.destinationUuid(), destinationUuid)) {
            throw failure("PROVIDER_RESOURCE_OWNERSHIP_CHANGED");
        }
        var identity = new TreeMap<String, Object>();
        identity.put("releaseId", expected.releaseId());
        identity.put("resourceKind", expected.resourceKind());
        identity.put("resourceUuid", uuid);
        identity.put("name", name);
        identity.put("description", description);
        identity.put("serverUuid", serverUuid);
        identity.put("projectUuid", projectUuid);
        identity.put("environmentUuid", environmentUuid);
        identity.put("destinationUuid", destinationUuid);
        identity.put("handleGeneration", expected.generation());
        if (APPLICATION_KINDS.contains(expected.resourceKind())) {
            identity.put("imageRepository", text(raw, "/docker_registry_image_name"));
            identity.put("imageTag", text(raw, "/docker_registry_image_tag"));
            identity.put("gitRepository", text(raw, "/git_repository"));
            identity.put("gitBranch", text(raw, "/git_branch"));
            identity.put("baseDirectory", text(raw, "/base_directory"));
            identity.put("dockerfileLocation", text(raw, "/dockerfile_location"));
            identity.put("fqdn", text(raw, "/fqdn"));
        } else {
            identity.put("databaseType", first(text(raw, "/type"), text(raw, "/database_type"), text(raw, "/kind")));
            identity.put("postgresDatabase", first(text(raw, "/postgres_db"), text(raw, "/database")));
            identity.put("postgresUser", first(text(raw, "/postgres_user"), text(raw, "/username")));
        }
        return json.fingerprint(identity);
    }

    private String text(JsonNode value, String pointer) {
        String result = value.at(pointer).asText(null);
        return result == null || result.isBlank() ? null : result;
    }

    private String first(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return null;
    }

    private List<ProviderVolumeGeneration> volumes(DeploymentProviderResourceHandleEntity handle,
                                                   com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity target,
                                                   ResourceGeneration expected) {
        if (!APPLICATION_KINDS.contains(handle.getResourceKind())
            && !"RUNTIME_POSTGRES_DATABASE".equals(handle.getResourceKind())) return List.of();
        if (expected != null && volumeDispositionReceipt(expected).equals(handle.getVolumeReclamationReceipt())) {
            return expected.volumes();
        }
        var connection = resolver.requireConnection(target);
        var observed = APPLICATION_KINDS.contains(handle.getResourceKind())
            ? client.listApplicationStorages(connection, handle.getProviderResourceUuid())
            : client.listDatabaseStorages(connection, handle.getProviderResourceUuid());
        var result = observedVolumes(handle, observed);
        if (expected != null) {
            if (volumeDeletionIntentReceipt(expected).equals(handle.getVolumeDeletionIntentReceipt())) {
                requireVolumeDeletionProgress(expected, handle, observed);
            } else {
                requireVolumeInventory(expected, handle, observed, true);
            }
            return expected.volumes();
        }
        return result.values().stream().sorted(Comparator.comparing(ProviderVolumeGeneration::storageUuid)).toList();
    }

    private Map<String, ProviderVolumeGeneration> observedVolumes(DeploymentProviderResourceHandleEntity handle,
                                                                   List<CoolifyApplicationStorageSummary> observed) {
        var seen = new HashSet<String>();
        var result = new HashMap<String, ProviderVolumeGeneration>();
        for (var storage : observed == null ? List.<CoolifyApplicationStorageSummary>of() : observed) {
            if (!"persistent".equals(storage.type())) continue;
            var volume = volume(handle, storage, seen);
            result.put(volume.storageUuid(), volume);
        }
        return Map.copyOf(result);
    }

    private ProviderVolumeGeneration volume(DeploymentProviderResourceHandleEntity handle,
                                            CoolifyApplicationStorageSummary storage,
                                            Set<String> seen) {
        if (storage.uuid() == null || storage.uuid().isBlank() || !seen.add(storage.uuid())
            || storage.mountPath() == null || storage.mountPath().isBlank()) {
            throw failure("PERSISTENT_VOLUME_INVENTORY_INVALID");
        }
        boolean hostBacked = storage.hostPath() != null && !storage.hostPath().isBlank();
        if (hostBacked && !approvedRetainedVolumeMounts.contains(storage.mountPath())) {
            throw failure("PERSISTENT_VOLUME_RETENTION_UNAPPROVED");
        }
        var fields = new TreeMap<String, Object>();
        fields.put("applicationUuid", handle.getProviderResourceUuid());
        fields.put("storageUuid", storage.uuid());
        fields.put("type", storage.type());
        fields.put("name", storage.name());
        fields.put("mountPath", storage.mountPath());
        fields.put("hostPath", storage.hostPath());
        fields.put("directory", storage.directory());
        return new ProviderVolumeGeneration(storage.uuid(), storage.type(), storage.name(), storage.mountPath(),
            storage.hostPath(), json.fingerprint(fields), hostBacked);
    }

    public String volumeDispositionReceipt(ResourceGeneration expected) {
        if (expected.volumes().isEmpty()) return "VOLUME_DISPOSITION:empty";
        var disposition = expected.volumes().stream().map(volume -> Map.of(
            "storageUuid", volume.storageUuid(),
            "mountPath", volume.mountPath(),
            "generation", volume.generation(),
            "disposition", volume.preserved() ? "RETAINED" : "DELETED"
        )).toList();
        return "VOLUME_DISPOSITION:" + json.fingerprint(disposition);
    }

    public String volumeDeletionIntentReceipt(ResourceGeneration expected) {
        if (expected.volumes().isEmpty()) return "VOLUME_DELETE_INTENT:empty";
        var intent = expected.volumes().stream().map(volume -> Map.of(
            "storageUuid", volume.storageUuid(),
            "mountPath", volume.mountPath(),
            "generation", volume.generation(),
            "disposition", volume.preserved() ? "DETACH_AND_RETAIN" : "DELETE_WITH_RESOURCE"
        )).toList();
        return "VOLUME_DELETE_INTENT:" + json.fingerprint(intent);
    }

    private String releaseGeneration(DeploymentReleaseEntity release) {
        var fields = new TreeMap<String, Object>();
        fields.put("id", release.getId());
        fields.put("deploymentId", release.getDeploymentId());
        fields.put("targetProfileId", release.getTargetProfileId());
        fields.put("versionId", release.getDeploymentVersionId());
        fields.put("sourceArtifactId", release.getSourceArtifactId());
        fields.put("handleId", release.getProviderResourceHandleId());
        fields.put("providerType", release.getProviderType());
        fields.put("createdAt", release.getCreatedAt());
        fields.put("status", release.getStatus());
        fields.put("verificationStatus", release.getVerificationStatus());
        fields.put("provisioningStatus", release.getProvisioningStatus());
        fields.put("verificationRunId", release.getVerificationRunId());
        fields.put("provisioningDetails", json.read(release.getProvisioningDetailsJson(), Object.class));
        return json.fingerprint(fields);
    }
    private TargetRetirementFailure failure(String code) { return new TargetRetirementFailure(code); }
}
