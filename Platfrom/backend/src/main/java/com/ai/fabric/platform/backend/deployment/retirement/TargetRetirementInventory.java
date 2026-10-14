package com.ai.fabric.platform.backend.deployment.retirement;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentProviderResourceHandleEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentProviderResourceHandleRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.util.Comparator;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.*;

@Service
public class TargetRetirementInventory {
    private static final String SOURCE_BINDING = "EXTERNAL_DOCUMENT_STORAGE_BINDING";
    private static final Set<String> PROVIDER_KINDS = Set.of("APPLICATION", "RUNTIME_POSTGRES_DATABASE");
    private final DeploymentRepository deployments;
    private final DeploymentTargetProfileRepository targets;
    private final DeploymentReleaseRepository releases;
    private final DeploymentProviderResourceHandleRepository handles;
    private final PlatformConsumerRepository consumers;
    private final TargetRetirementJson json;

    public TargetRetirementInventory(DeploymentRepository deployments, DeploymentTargetProfileRepository targets,
                                     DeploymentReleaseRepository releases, DeploymentProviderResourceHandleRepository handles,
                                     PlatformConsumerRepository consumers, TargetRetirementJson json) {
        this.deployments = deployments;
        this.targets = targets;
        this.releases = releases;
        this.handles = handles;
        this.consumers = consumers;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public Snapshot capture(String deploymentId, String targetId, PlanRequest request, long epoch) {
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
        var resources = handles.findByDeploymentIdAndTargetProfileIdOrderByUpdatedAtDesc(deploymentId, targetId).stream()
            .sorted(Comparator.comparing(DeploymentProviderResourceHandleEntity::getId))
            .map(handle -> {
                boolean preserved = SOURCE_BINDING.equals(handle.getResourceKind());
                if (!preserved && (handle.getReleaseId() == null || !request.releaseIds().contains(handle.getReleaseId())
                    || !PROVIDER_KINDS.contains(handle.getResourceKind()))) throw failure("RESOURCE_INVENTORY_UNSUPPORTED");
                if (handle.getProviderType() != target.getProviderType()) throw failure("RESOURCE_PROVIDER_CHANGED");
                if (handle.getCreatedAt() == null || handle.getProviderResourceUuid() == null)
                    throw failure("RESOURCE_GENERATION_UNAVAILABLE");
                return resource(handle, preserved);
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
        var current = capture(snapshot.deploymentId(), snapshot.targetProfileId(), request, snapshot.targetEpoch());
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
        if (!Objects.equals(expected.generation(), resource(handle, expected.preserved()).generation()))
            throw failure("RESOURCE_GENERATION_CHANGED");
        return handle;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordReclaimed(ResourceGeneration expected, Instant observedAt) {
        if (expected.preserved()) throw failure("CUSTOMER_SOURCE_PRESERVED");
        var handle = requireResource(expected);
        handle.setStatus("DELETED");
        handle.setLastObservedStatus("ABSENT");
        handle.setLastObservedAt(observedAt);
        handle.setUpdatedAt(Instant.now());
        handles.save(handle);
    }

    private ResourceGeneration resource(DeploymentProviderResourceHandleEntity handle, boolean preserved) {
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
        return new ResourceGeneration(handle.getId(), handle.getReleaseId(), handle.getProviderType(),
            handle.getResourceKind(), handle.getProviderResourceUuid(), handle.getProviderServerUuid(), json.fingerprint(fields), preserved);
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
