package com.ai.fabric.platform.backend.deployment.budget;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.service.DeploymentSourceArtifactService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentSourceCapabilityManifestService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

/** A startup flag cannot retrofit a guard into an older image. Require the reviewed immutable artifact. */
@Component
public class ProviderUsageRuntimeArtifactGate {
    private final DeploymentSourceArtifactService artifacts;
    private final DeploymentSourceCapabilityManifestService manifests;

    public ProviderUsageRuntimeArtifactGate(DeploymentSourceArtifactService artifacts,
        DeploymentSourceCapabilityManifestService manifests) {
        this.artifacts = artifacts; this.manifests = manifests;
    }

    public void require(DeploymentReleaseEntity release) {
        if (release == null || release.getSourceArtifactId() == null || release.getSourceArtifactId().isBlank()) fail();
        var artifact = artifacts.require(release.getSourceArtifactId());
        if (!"RUNTIME".equals(artifact.getArtifactRole()) || !"DOCKER_IMAGE".equals(artifact.getArtifactType())
            || artifact.getImageDigest() == null || !artifact.getImageDigest().matches("sha256:[a-f0-9]{64}")
            || artifact.getGitCommitSha() == null || !artifact.getGitCommitSha().matches("[a-f0-9]{40}")
            || artifact.getBuildRunId() == null || artifact.getBuildRunId().isBlank() || artifact.getPromotedAt() == null) fail();
        var manifest = manifests.normalize(manifests.read(artifact.getCapabilityManifestJson()));
        if (!java.util.Objects.equals(artifact.getCapabilityManifestHash(), manifest.hash())
            || manifest.hash() == null
            || !contains(manifest.manifest().path("capabilities"), "deployment-local-provider-budget-v1")
            || !contains(manifest.manifest().path("endpointClasses"), "provider-budget-administration")
            || !contains(manifest.manifest().path("migrationIds"), "provider-budget-accounting-v1")
            || !contains(manifest.manifest().path("verificationPackIds"), "provider-budget-hard-limit-v1")) fail();
    }

    private static boolean contains(JsonNode values, String expected) {
        if (!values.isArray()) return false;
        for (var value : values) if (value.isTextual() && expected.equals(value.textValue())) return true;
        return false;
    }

    private static void fail() { throw new IllegalStateException("PROVIDER_USAGE_GUARDED_RUNTIME_ARTIFACT_REQUIRED"); }
}
