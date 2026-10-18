package com.ai.fabric.platform.backend.deployment.budget;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentSourceArtifactEntity;
import com.ai.fabric.platform.backend.deployment.service.DeploymentSourceArtifactService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentSourceCapabilityManifestService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProviderUsageRuntimeArtifactGateTest {
    private final ObjectMapper json = new ObjectMapper();
    private final DeploymentSourceCapabilityManifestService manifests = new DeploymentSourceCapabilityManifestService(json);
    private final DeploymentSourceArtifactService artifacts = mock(DeploymentSourceArtifactService.class);
    private final ProviderUsageRuntimeArtifactGate gate = new ProviderUsageRuntimeArtifactGate(artifacts, manifests);

    @Test void requiresAllFourReviewedDeclarationsAndTheirOriginalHash() throws Exception {
        var release = release(); var artifact = artifact(); when(artifacts.require("artifact")).thenReturn(artifact);
        assertThatCode(() -> gate.require(release)).doesNotThrowAnyException();
        String valid = artifact.getCapabilityManifestJson();
        for (String field : java.util.List.of("capabilities", "endpointClasses", "migrationIds", "verificationPackIds")) {
            var missing = json.readTree(valid).deepCopy(); ((com.fasterxml.jackson.databind.node.ObjectNode) missing).putArray(field);
            var normalized = manifests.normalize(missing);
            artifact.setCapabilityManifestJson(json.writeValueAsString(normalized.manifest())); artifact.setCapabilityManifestHash(normalized.hash());
            assertThatThrownBy(() -> gate.require(release)).hasMessage("PROVIDER_USAGE_GUARDED_RUNTIME_ARTIFACT_REQUIRED");
        }
        artifact.setCapabilityManifestJson(valid); artifact.setCapabilityManifestHash("forged");
        assertThatThrownBy(() -> gate.require(release)).hasMessage("PROVIDER_USAGE_GUARDED_RUNTIME_ARTIFACT_REQUIRED");
    }

    @Test void aReleaseCannotFloatToLatestOrAnUnpromotedImage() throws Exception {
        var release = release(); release.setSourceArtifactId(null);
        assertThatThrownBy(() -> gate.require(release)).hasMessage("PROVIDER_USAGE_GUARDED_RUNTIME_ARTIFACT_REQUIRED");
        verifyNoInteractions(artifacts);
        release.setSourceArtifactId("artifact"); var artifact = artifact(); artifact.setPromotedAt(null);
        when(artifacts.require("artifact")).thenReturn(artifact);
        assertThatThrownBy(() -> gate.require(release)).hasMessage("PROVIDER_USAGE_GUARDED_RUNTIME_ARTIFACT_REQUIRED");
    }

    private DeploymentReleaseEntity release() {
        var release = new DeploymentReleaseEntity(); release.setSourceArtifactId("artifact"); return release;
    }

    private DeploymentSourceArtifactEntity artifact() throws Exception {
        var artifact = new DeploymentSourceArtifactEntity(); artifact.setArtifactType("DOCKER_IMAGE"); artifact.setArtifactRole("RUNTIME");
        artifact.setImageDigest("sha256:" + "a".repeat(64)); artifact.setGitCommitSha("b".repeat(40));
        artifact.setBuildRunId("reviewed-build"); artifact.setPromotedAt(Instant.parse("2026-10-18T12:00:00Z"));
        var manifest = manifests.normalize(json.readTree("""
            {"schemaVersion":"loomai-runtime-capabilities-v1","aiFabricVersion":"0.8.35",
             "supportedBehaviorTypes":["CONVERSATIONAL"],"capabilities":["deployment-local-provider-budget-v1"],
             "supportedActivationSources":["USER_QUERY"],"supportedChannelBindings":["CHAT"],"supportedExecutionExtensions":[],
             "endpointClasses":["provider-budget-administration"],"migrationIds":["provider-budget-accounting-v1"],
             "verificationPackIds":["provider-budget-hard-limit-v1"]}
            """));
        artifact.setCapabilityManifestJson(json.writeValueAsString(manifest.manifest())); artifact.setCapabilityManifestHash(manifest.hash());
        return artifact;
    }
}
