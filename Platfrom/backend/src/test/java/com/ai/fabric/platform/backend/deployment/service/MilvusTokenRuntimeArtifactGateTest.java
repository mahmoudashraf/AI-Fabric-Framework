package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentSourceArtifactEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MilvusTokenRuntimeArtifactGateTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final DeploymentSourceCapabilityManifestService manifests = new DeploymentSourceCapabilityManifestService(mapper);
    private final DeploymentSourceArtifactService artifacts = mock(DeploymentSourceArtifactService.class);
    private final MilvusTokenRuntimeArtifactGate gate = new MilvusTokenRuntimeArtifactGate(artifacts, manifests);

    @Test
    void requiresExactReviewedTokenCapabilityHashAndPublishedFrameworkVersion() throws Exception {
        var version = new DeploymentVersionEntity(); version.setAiFabricFrameworkVersion("0.8.36");
        var provider = provider();
        var artifact = artifact(); when(artifacts.require("runtime")).thenReturn(artifact);
        assertThatCode(() -> gate.require(version, provider, "runtime")).doesNotThrowAnyException();
        artifact.setCapabilityManifestHash("changed");
        denied(version, provider);
        artifact = artifact(); when(artifacts.require("runtime")).thenReturn(artifact);
        version.setAiFabricFrameworkVersion("0.8.35"); denied(version, provider);
        version.setAiFabricFrameworkVersion("0.8.36");
        var missing = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(artifact.getCapabilityManifestJson());
        missing.putArray("capabilities");
        var normalized = manifests.normalize(missing);
        artifact.setCapabilityManifestJson(normalized.manifest().toString()); artifact.setCapabilityManifestHash(normalized.hash());
        denied(version, provider);
    }

    @Test
    void rejectsMissingOrUnreviewedRuntimeAndDoesNotFloatToLatest() throws Exception {
        var version = new DeploymentVersionEntity(); var provider = provider();
        assertThatThrownBy(() -> gate.require(version, provider, null)).hasMessageContaining("MILVUS_TOKEN_RUNTIME_ARTIFACT_REQUIRED");
        verifyNoInteractions(artifacts);
        for (String field : java.util.List.of("promotion", "digest", "build", "role", "commit")) {
            var artifact = artifact(); when(artifacts.require("runtime")).thenReturn(artifact);
            switch (field) {
                case "promotion" -> artifact.setPromotedAt(null);
                case "digest" -> artifact.setImageDigest("mutable");
                case "build" -> artifact.setBuildRunId(null);
                case "role" -> artifact.setArtifactRole("CONNECTOR");
                case "commit" -> artifact.setGitCommitSha(null);
            }
            denied(version, provider);
        }
    }

    @Test
    void unchangedUsernamePasswordModeRequiresNoTokenArtifactAndInvalidModeFailsClosed() {
        var provider = provider(); provider.remove("milvusAuthenticationType");
        gate.require(new DeploymentVersionEntity(), provider, null);
        verifyNoInteractions(artifacts);
        provider.put("milvusAuthenticationType", "token");
        assertThatThrownBy(() -> gate.require(new DeploymentVersionEntity(), provider, null))
            .hasMessage("MILVUS_AUTHENTICATION_TYPE_INVALID");
    }

    @Test
    void everyOfferedRuntimeMustSupportTokensBeforeAdmission() throws Exception {
        var version = new DeploymentVersionEntity(); version.setAiFabricFrameworkVersion("0.8.36");
        var valid = artifact(); when(artifacts.require("runtime")).thenReturn(valid);
        var dependency = new DeploymentSourceArtifactEntity(); dependency.setArtifactRole("CONNECTOR");
        when(artifacts.require("dependency")).thenReturn(dependency);
        gate.requireReviewedCandidates(version, provider(), java.util.List.of("runtime", "dependency"));
        assertThatThrownBy(() -> gate.requireReviewedCandidates(version, provider(), java.util.List.of("dependency")))
            .hasMessageContaining("MILVUS_TOKEN_RUNTIME_ARTIFACT_REQUIRED");
        var old = artifact(); old.setCapabilityManifestHash("changed");
        when(artifacts.require("old-runtime")).thenReturn(old);
        assertThatThrownBy(() -> gate.requireReviewedCandidates(version, provider(), java.util.List.of("runtime", "old-runtime")))
            .hasMessageContaining("MILVUS_TOKEN_RUNTIME_ARTIFACT_REQUIRED");
    }

    private void denied(DeploymentVersionEntity version, com.fasterxml.jackson.databind.JsonNode provider) {
        assertThatThrownBy(() -> gate.require(version, provider, "runtime"))
            .hasMessageContaining("MILVUS_TOKEN_RUNTIME_ARTIFACT_REQUIRED");
    }

    private com.fasterxml.jackson.databind.node.ObjectNode provider() {
        return mapper.createObjectNode().put("vectorStrategy", "milvus").put("vectorProvisioningMode", "EXTERNAL_EXISTING")
            .put("vectorStoragePosture", "SHARED").put("milvusExternalBindingRequired", true).put("milvusAuthenticationType", "TOKEN");
    }

    private DeploymentSourceArtifactEntity artifact() throws Exception {
        var value = new DeploymentSourceArtifactEntity(); value.setArtifactType("DOCKER_IMAGE"); value.setArtifactRole("RUNTIME");
        value.setImageDigest("sha256:" + "a".repeat(64)); value.setGitCommitSha("b".repeat(40));
        value.setBuildRunId("verified-build"); value.setPromotionChannel("staging"); value.setPromotedAt(Instant.now());
        var manifest = manifests.normalize(mapper.readTree("""
            {"schemaVersion":"loomai-runtime-capabilities-v1","aiFabricVersion":"0.8.36",
             "supportedBehaviorTypes":["CONVERSATIONAL"],"supportedActivationSources":["USER_QUERY"],
             "supportedChannelBindings":["CHAT"],"supportedExecutionExtensions":[],
             "capabilities":["milvus-token-authentication-v1"],"endpointClasses":[],"migrationIds":[],"verificationPackIds":[]}
            """));
        value.setCapabilityManifestJson(manifest.manifest().toString()); value.setCapabilityManifestHash(manifest.hash());
        return value;
    }
}
