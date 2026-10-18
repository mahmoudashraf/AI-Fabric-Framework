package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;
import java.util.List;

import static org.springframework.http.HttpStatus.CONFLICT;

/** A configured token cannot retrofit native SDK support into an older runtime image. */
@Component
public class MilvusTokenRuntimeArtifactGate {
    public static final String CAPABILITY = "milvus-token-authentication-v1";
    private final DeploymentSourceArtifactService artifacts;
    private final DeploymentSourceCapabilityManifestService manifests;

    public MilvusTokenRuntimeArtifactGate(DeploymentSourceArtifactService artifacts,
                                          DeploymentSourceCapabilityManifestService manifests) {
        this.artifacts = artifacts;
        this.manifests = manifests;
    }

    public static boolean required(JsonNode provider) {
        ManagedDeploymentProfileCatalog.validateMilvusAuthentication(provider, false);
        return ManagedDeploymentProfileCatalog.usesMilvus(provider)
            && ManagedDeploymentProfileCatalog.milvusTokenAuthentication(provider);
    }

    public void require(DeploymentVersionEntity version, JsonNode provider, String artifactId) {
        if (!required(provider)) return;
        if (version == null || artifactId == null || artifactId.isBlank()) throw denied();
        var artifact = artifacts.require(artifactId);
        if (!"RUNTIME".equals(artifact.getArtifactRole()) || !"DOCKER_IMAGE".equals(artifact.getArtifactType())
            || artifact.getPromotedAt() == null || artifact.getPromotionChannel() == null || artifact.getPromotionChannel().isBlank()
            || artifact.getImageDigest() == null || !artifact.getImageDigest().matches("sha256:[a-f0-9]{64}")
            || artifact.getGitCommitSha() == null || !artifact.getGitCommitSha().matches("[a-f0-9]{40}")
            || artifact.getBuildRunId() == null || artifact.getBuildRunId().isBlank()) throw denied();
        var normalized = manifests.normalize(manifests.read(artifact.getCapabilityManifestJson()));
        if (normalized.hash() == null || !Objects.equals(normalized.hash(), artifact.getCapabilityManifestHash())
            || version.getAiFabricFrameworkVersion() == null || version.getAiFabricFrameworkVersion().isBlank()
            || !normalized.manifest().path("capabilities").isArray()
            || !version.getAiFabricFrameworkVersion().equals(normalized.manifest().path("aiFabricVersion").asText())) throw denied();
        for (JsonNode capability : normalized.manifest().path("capabilities")) {
            if (capability.isTextual() && CAPABILITY.equals(capability.textValue())) return;
        }
        throw denied();
    }

    public void requireReviewedCandidates(DeploymentVersionEntity version, JsonNode provider, List<String> artifactIds) {
        if (!required(provider)) return;
        if (artifactIds == null || artifactIds.isEmpty()) throw denied();
        boolean runtimeFound = false;
        for (String artifactId : artifactIds) {
            if (artifactId == null || artifactId.isBlank()) throw denied();
            if ("RUNTIME".equals(artifacts.require(artifactId).getArtifactRole())) {
                require(version, provider, artifactId);
                runtimeFound = true;
            }
        }
        if (!runtimeFound) throw denied();
    }

    private static ResponseStatusException denied() {
        return new ResponseStatusException(CONFLICT, "MILVUS_TOKEN_RUNTIME_ARTIFACT_REQUIRED");
    }
}
