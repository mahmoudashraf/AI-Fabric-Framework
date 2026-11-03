package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;

import static org.springframework.http.HttpStatus.CONFLICT;

/** A canonical-only flag cannot expose routes that an older runtime packages only with handover. */
@Component
public class StandaloneConversationRuntimeArtifactGate {
    public static final String CAPABILITY = "standalone-conversation-control";
    private final DeploymentSourceArtifactService artifacts;
    private final DeploymentSourceCapabilityManifestService manifests;

    public StandaloneConversationRuntimeArtifactGate(DeploymentSourceArtifactService artifacts,
            DeploymentSourceCapabilityManifestService manifests) {
        this.artifacts = artifacts; this.manifests = manifests;
    }

    public static boolean required(JsonNode provider) {
        if (provider == null || provider.path("canonicalConversationsEnabled").isMissingNode()
            || provider.path("canonicalConversationsEnabled").isNull()) return false;
        var preference = provider.path("canonicalConversationsEnabled");
        if (!preference.isBoolean()) throw new Required();
        return preference.booleanValue();
    }

    public void requireEnabled(DeploymentVersionEntity version, DeploymentReleaseEntity release) {
        if (version == null || release == null || !Objects.equals(version.getId(), release.getDeploymentVersionId())
            || !Objects.equals(version.getDeploymentId(), release.getDeploymentId())
            || !required(manifests.read(version.getProviderConfigJson()))) throw new Required();
        require(version, release.getSourceArtifactId());
    }

    public void require(DeploymentVersionEntity version, String artifactId) {
        if (version == null || artifactId == null || artifactId.isBlank()) throw new Required();
        var artifact = artifacts.require(artifactId);
        if (!"RUNTIME".equals(artifact.getArtifactRole()) || !"DOCKER_IMAGE".equals(artifact.getArtifactType())
            || artifact.getPromotedAt() == null || artifact.getPromotionChannel() == null || artifact.getPromotionChannel().isBlank()
            || artifact.getImageDigest() == null || !artifact.getImageDigest().matches("sha256:[a-f0-9]{64}")
            || artifact.getGitCommitSha() == null || !artifact.getGitCommitSha().matches("[a-f0-9]{40}")
            || artifact.getBuildRunId() == null || artifact.getBuildRunId().isBlank()) throw new Required();
        var normalized = manifests.normalize(manifests.read(artifact.getCapabilityManifestJson()));
        if (normalized.hash() == null || !Objects.equals(normalized.hash(), artifact.getCapabilityManifestHash())
            || version.getAiFabricFrameworkVersion() == null || version.getAiFabricFrameworkVersion().isBlank()
            || !version.getAiFabricFrameworkVersion().equals(normalized.manifest().path("aiFabricVersion").asText()))
            throw new Required();
        for (JsonNode capability : normalized.manifest().path("capabilities"))
            if (capability.isTextual() && CAPABILITY.equals(capability.textValue())) return;
        throw new Required();
    }

    public static class Required extends ResponseStatusException {
        public Required() {
            super(CONFLICT, "Apply a reviewed runtime artifact advertising standalone-conversation-control with canonicalConversationsEnabled enabled.");
        }
    }
}
