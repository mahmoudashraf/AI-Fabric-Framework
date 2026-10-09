package com.ai.fabric.platform.backend.aiworkspace.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
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

    public AIWorkspaceAssignmentService(DeploymentRepository deploymentRepository,
                                        DeploymentReleaseRepository releaseRepository,
                                        DeploymentVersionRepository versionRepository,
                                        ObjectMapper objectMapper) {
        this.deploymentRepository = deploymentRepository;
        this.releaseRepository = releaseRepository;
        this.versionRepository = versionRepository;
        this.objectMapper = objectMapper;
    }

    public Assignment resolveCurrent(PlatformConsumerEntity consumer) {
        return resolveTarget(consumer, consumer.getBoundDeploymentId(), consumer.getBoundReleaseId());
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
        String runtimeBaseUrl = releaseRuntimeBaseUrl(release, deployment);
        if (isBlank(runtimeBaseUrl)) {
            throw new IllegalStateException("Assigned release does not expose a runtime URL.");
        }
        JsonNode securityConfig = readJson(version.getSecurityConfigJson());
        String revision = sha256(String.join("\n",
            consumer.getConsumerId(), deployment.getId(), release.getId(), version.getId(), runtimeBaseUrl,
            version.getConfigHash() == null ? "" : version.getConfigHash()
        ));
        return new Assignment(consumer, deployment, release, version, securityConfig, runtimeBaseUrl, "sha256:" + revision);
    }

    private String releaseRuntimeBaseUrl(DeploymentReleaseEntity release, DeploymentEntity deployment) {
        JsonNode details = readJson(release.getProvisioningDetailsJson());
        return normalizeBaseUrl(firstNonBlank(
            details.path("runtimeBaseUrl").asText(null),
            details.path("runtime").path("baseUrl").asText(null),
            details.path("railway").path("services").path("runtime").path("baseUrl").asText(null),
            details.path("runtimeFqdn").asText(null),
            details.path("fqdn").asText(null),
            deployment.getRuntimeBaseUrl()
        ));
    }

    private JsonNode readJson(String raw) {
        try {
            return raw == null || raw.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(raw);
        } catch (Exception ex) {
            throw new IllegalStateException("Assigned deployment security configuration is invalid.", ex);
        }
    }

    private String normalizeBaseUrl(String value) {
        if (isBlank(value)) return null;
        String normalized = value.trim();
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "https://" + normalized;
        }
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) if (!isBlank(value)) return value;
        return null;
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
