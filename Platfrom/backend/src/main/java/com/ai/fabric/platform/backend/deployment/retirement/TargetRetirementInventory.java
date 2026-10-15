package com.ai.fabric.platform.backend.deployment.retirement;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentProviderResourceHandleEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentProviderResourceHandleRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.service.CoolifyApiClient;
import com.ai.fabric.platform.backend.deployment.service.CoolifyApplicationStorageSummary;
import com.ai.fabric.platform.backend.deployment.service.CoolifyTargetProfileResolver;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
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
    private static final String SOURCE_BINDING = "EXTERNAL_DOCUMENT_STORAGE_BINDING";
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
    private final TargetRetirementJson json;
    private final CoolifyTargetProfileResolver resolver;
    private final CoolifyApiClient client;
    private final List<String> approvedRetainedVolumeMounts;

    public TargetRetirementInventory(DeploymentRepository deployments, DeploymentTargetProfileRepository targets,
                                     DeploymentReleaseRepository releases, DeploymentProviderResourceHandleRepository handles,
                                     PlatformConsumerRepository consumers, TargetRetirementJson json,
                                     CoolifyTargetProfileResolver resolver, CoolifyApiClient client,
                                     @Value("${platform.target-retirement.approved-retained-volume-mounts:}") List<String> retainedMounts) {
        this.deployments = deployments;
        this.targets = targets;
        this.releases = releases;
        this.handles = handles;
        this.consumers = consumers;
        this.json = json;
        this.resolver = resolver;
        this.client = client;
        this.approvedRetainedVolumeMounts = List.copyOf(retainedMounts);
    }

    @Transactional(readOnly = true)
    public Snapshot capture(String deploymentId, String targetId, PlanRequest request, long epoch) {
        return capture(deploymentId, targetId, request, epoch, null);
    }

    private Snapshot capture(String deploymentId, String targetId, PlanRequest request, long epoch, Snapshot expected) {
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
                boolean preserved = SOURCE_BINDING.equals(handle.getResourceKind());
                if (!preserved && (handle.getReleaseId() == null || !request.releaseIds().contains(handle.getReleaseId())
                    || !PROVIDER_KINDS.contains(handle.getResourceKind()))) throw failure("RESOURCE_INVENTORY_UNSUPPORTED");
                if (handle.getProviderType() != target.getProviderType()) throw failure("RESOURCE_PROVIDER_CHANGED");
                if (handle.getCreatedAt() == null || handle.getProviderResourceUuid() == null)
                    throw failure("RESOURCE_GENERATION_UNAVAILABLE");
                return resource(handle, preserved, target, expectedResources.get(handle.getId()));
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
        checkReferences(snapshot);
        return snapshot;
    }

    @Transactional(readOnly = true)
    public void recheck(Snapshot snapshot, PlanRequest request) {
        var current = capture(snapshot.deploymentId(), snapshot.targetProfileId(), request, snapshot.targetEpoch(), snapshot);
        if (!json.fingerprint(snapshot).equals(json.fingerprint(current))) throw failure("SNAPSHOT_GENERATION_CHANGED");
    }

    private void checkReferences(Snapshot snapshot) {
        var expected = snapshot.consumers().stream().collect(Collectors.toMap(ConsumerExpectation::consumerEntityId,
            ConsumerExpectation::expectedBindingRevision));
        for (var entry : expected.entrySet()) {
            var consumer = consumers.findById(entry.getKey()).orElseThrow(() -> failure("CONSUMER_MISSING"));
            if (!snapshot.customerId().equals(consumer.getCustomerId())
                || consumer.getBindingRevision() != entry.getValue()) throw failure("CONSUMER_REVISION_CHANGED");
        }
        Set<String> releaseIds = snapshot.releases().stream().map(ReleaseGeneration::releaseId).collect(Collectors.toSet());
        for (var consumer : consumers.findAll()) {
            boolean targetReference = snapshot.deploymentId().equals(consumer.getBoundDeploymentId())
                && (consumer.getBoundTargetProfileId() == null || snapshot.targetProfileId().equals(consumer.getBoundTargetProfileId()));
            if (targetReference || releaseIds.contains(consumer.getBoundReleaseId())) throw failure("TARGET_STILL_ASSIGNED");
            if (snapshot.deploymentId().equals(consumer.getBoundDeploymentId()) && !expected.containsKey(consumer.getId()))
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
        // Opaque provider/config/index/dependency references are proved by the mandatory interlock/participants.
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

    @Transactional
    public void recordVolumesReclaimed(ResourceGeneration expected, Instant observedAt) {
        if (expected.volumes().isEmpty()) return;
        var handle = requireResource(expected);
        handle.setLastObservedStatus(volumeDispositionReceipt(expected));
        handle.setLastObservedAt(observedAt);
        handle.setUpdatedAt(Instant.now());
        handles.save(handle);
    }

    @Transactional(readOnly = true)
    public boolean volumesReclaimed(ResourceGeneration expected) {
        if (expected.volumes().isEmpty()) return true;
        var handle = handles.findById(expected.handleId()).orElseThrow(() -> failure("RESOURCE_MISSING"));
        return volumeDispositionReceipt(expected).equals(handle.getLastObservedStatus());
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
                                        ResourceGeneration expected) {
        return resource(handle, preserved, target, expected, true);
    }

    private ResourceGeneration resource(DeploymentProviderResourceHandleEntity handle, boolean preserved,
                                        com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity target,
                                        ResourceGeneration expected, boolean observeVolumes) {
        List<ProviderVolumeGeneration> volumes = observeVolumes ? volumes(handle, target, expected) : expected.volumes();
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
        if (expected != null && observeVolumes && !"DELETED".equals(handle.getStatus())) {
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
        if (!APPLICATION_KINDS.contains(handle.getResourceKind())) return List.of();
        if (expected != null && volumeDispositionReceipt(expected).equals(handle.getLastObservedStatus())) {
            return expected.volumes();
        }
        var connection = resolver.requireConnection(target);
        var observed = client.listApplicationStorages(connection, handle.getProviderResourceUuid());
        var result = observedVolumes(handle, observed);
        if (expected != null) {
            requireVolumeInventory(expected, handle, observed, true);
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
