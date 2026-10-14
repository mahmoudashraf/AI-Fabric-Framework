package com.ai.fabric.platform.backend.aiworkspace.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentReleaseEndpointService;
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
public class AIWorkspaceAssignmentService {

    private final DeploymentRepository deploymentRepository;
    private final DeploymentReleaseRepository releaseRepository;
    private final DeploymentVersionRepository versionRepository;
    private final ObjectMapper objectMapper;
    private final DeploymentReleaseEndpointService endpoints;
    private final com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository profiles;

    public AIWorkspaceAssignmentService(DeploymentRepository deploymentRepository,
                                        DeploymentReleaseRepository releaseRepository,
                                        DeploymentVersionRepository versionRepository,
                                        ObjectMapper objectMapper,
                                        DeploymentReleaseEndpointService endpoints,
                                        com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository profiles) {
        this.deploymentRepository = deploymentRepository;
        this.releaseRepository = releaseRepository;
        this.versionRepository = versionRepository;
        this.objectMapper = objectMapper;
        this.endpoints = endpoints;
        this.profiles = profiles;
    }

    public Assignment resolveCurrent(PlatformConsumerEntity consumer) {
        if (consumer == null || isBlank(consumer.getBoundTargetProfileId())) {
            throw new IllegalStateException("An explicit consumer target binding is required.");
        }
        Assignment assignment = resolveTarget(consumer, consumer.getBoundDeploymentId(), consumer.getBoundReleaseId());
        if (!consumer.getBoundTargetProfileId().equals(assignment.release().getTargetProfileId())) {
            throw new IllegalStateException("Consumer release and target binding are inconsistent.");
        }
        return assignment;
    }

    public Assignment resolveTarget(PlatformConsumerEntity consumer,
                                    String deploymentId,
                                    String releaseId) {
        if (consumer == null) throw new IllegalStateException("Consumer is unavailable.");
        if (!"ACTIVE".equalsIgnoreCase(consumer.getStatus())) throw new IllegalStateException("Consumer is disabled.");
        if (isBlank(deploymentId)) throw new IllegalStateException("Consumer is not bound to a deployment.");
        if (isBlank(releaseId)) throw new IllegalStateException("A verified release binding is required.");

        DeploymentEntity deployment = deploymentRepository.findById(deploymentId.trim())
            .orElseThrow(() -> new IllegalStateException("Consumer binding points to a missing deployment."));
        if (!consumer.getCustomerId().equals(deployment.getCustomerId())) {
            throw new IllegalStateException("Assigned deployment does not belong to the installation customer.");
        }
        if (deployment.getArchivedAt() != null || "ARCHIVED".equalsIgnoreCase(deployment.getStatus())) {
            throw new IllegalStateException("Assigned deployment is archived.");
        }
        if (!"CONVERSATIONAL".equalsIgnoreCase(deployment.getBehaviorType())) {
            throw new IllegalStateException("AI Workspace requires a conversational deployment.");
        }

        DeploymentReleaseEntity release = releaseRepository.findById(releaseId.trim())
            .orElseThrow(() -> new IllegalStateException("Consumer binding points to a missing release."));
        if (!deployment.getId().equals(release.getDeploymentId())) {
            throw new IllegalStateException("Consumer release binding does not belong to its deployment.");
        }
        if (!"APPLIED_VERIFIED".equalsIgnoreCase(release.getStatus())
            || !"PASSED".equalsIgnoreCase(release.getVerificationStatus())) {
            throw new IllegalStateException("Consumer release binding is not verified.");
        }
        DeploymentVersionEntity version = versionRepository.findById(release.getDeploymentVersionId())
            .orElseThrow(() -> new IllegalStateException("Assigned release points to a missing deployment version."));
        if (!deployment.getId().equals(version.getDeploymentId()) || isBlank(release.getTargetProfileId())) {
            throw new IllegalStateException("Assigned release candidate or target evidence is inconsistent.");
        }
        String runtimeBaseUrl = endpoints.runtimeBaseUrl(release);
        var target = profiles.findById(release.getTargetProfileId())
            .filter(profile -> profile.isActive() && profile.getProviderType() == release.getProviderType())
            .orElseThrow(() -> new IllegalStateException("Assigned release target is unavailable or inconsistent."));
        DeploymentEntity targetProjection = new DeploymentEntity();
        org.springframework.beans.BeanUtils.copyProperties(deployment, targetProjection);
        targetProjection.setEnvironmentName(target.getEnvironmentName());
        targetProjection.setRuntimeBaseUrl(runtimeBaseUrl);
        targetProjection.setConnectorBaseUrl(endpoints.connectorBaseUrl(release));
        targetProjection.setActiveVersionId(version.getId());
        JsonNode securityConfig = readJson(version.getSecurityConfigJson());
        String revision = sha256(String.join("\n",
            consumer.getConsumerId(), deployment.getId(), release.getId(), version.getId(), runtimeBaseUrl,
            version.getConfigHash() == null ? "" : version.getConfigHash(),
            Long.toString(consumer.getBindingRevision())
        ));
        return new Assignment(consumer, targetProjection, release, version, securityConfig, runtimeBaseUrl, "sha256:" + revision);
    }

    private JsonNode readJson(String raw) {
        try {
            return raw == null || raw.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(raw);
        } catch (Exception ex) {
            throw new IllegalStateException("Assigned deployment security configuration is invalid.", ex);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is unavailable.", ex);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record Assignment(
        PlatformConsumerEntity consumer,
        DeploymentEntity deployment,
        DeploymentReleaseEntity release,
        DeploymentVersionEntity version,
        JsonNode securityConfig,
        String runtimeBaseUrl,
        String assignmentRevision
    ) {
    }
}
