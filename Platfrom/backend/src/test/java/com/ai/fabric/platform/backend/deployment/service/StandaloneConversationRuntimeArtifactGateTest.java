package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StandaloneConversationRuntimeArtifactGateTest {
    final ObjectMapper mapper = new ObjectMapper();
    final DeploymentSourceArtifactService artifacts = mock(DeploymentSourceArtifactService.class);
    final DeploymentSourceCapabilityManifestService manifests = new DeploymentSourceCapabilityManifestService(mapper);
    final StandaloneConversationRuntimeArtifactGate gate = new StandaloneConversationRuntimeArtifactGate(artifacts, manifests);

    @Test void newCapabilityMustBelongToTheExactReviewedReleaseArtifact() throws Exception {
        var artifact = artifact(true); when(artifacts.require("release-runtime")).thenReturn(artifact);
        assertThatCode(() -> gate.requireEnabled(version(), release())).doesNotThrowAnyException();
        verify(artifacts).require("release-runtime"); verifyNoMoreInteractions(artifacts);
    }

    @Test void oldCanonicalAndHandoverMarkersCannotQualifyTheNewFlag() throws Exception {
        when(artifacts.require("release-runtime")).thenReturn(artifact(false));
        denied(() -> gate.requireEnabled(version(), release()));
        verify(artifacts).require("release-runtime"); verifyNoMoreInteractions(artifacts);
    }

    @Test void nonBooleanDirectDraftFlagCannotBypassTheArtifactGateThroughRuntimeCoercion() {
        denied(() -> StandaloneConversationRuntimeArtifactGate.required(mapper.createObjectNode()
            .put("canonicalConversationsEnabled", "true")));
        assertThat(StandaloneConversationRuntimeArtifactGate.required(mapper.createObjectNode()
            .put("canonicalConversationsEnabled", false))).isFalse();
        assertThat(StandaloneConversationRuntimeArtifactGate.required(mapper.createObjectNode())).isFalse();
        verifyNoInteractions(artifacts);
    }

    @ParameterizedTest @ValueSource(strings = {"false", "missing", "string", "version", "deployment", "artifact"})
    void flagAndExactReleaseBindingAreRequiredBeforeArtifactLookup(String failure) {
        var version = version(); var release = release();
        switch (failure) {
            case "false" -> version.setProviderConfigJson("{\"canonicalConversationsEnabled\":false}");
            case "missing" -> version.setProviderConfigJson("{}");
            case "string" -> version.setProviderConfigJson("{\"canonicalConversationsEnabled\":\"true\"}");
            case "version" -> release.setDeploymentVersionId("other-version");
            case "deployment" -> release.setDeploymentId("other-deployment");
            case "artifact" -> release.setSourceArtifactId(null);
        }
        denied(() -> gate.requireEnabled(version, release));
        verifyNoInteractions(artifacts);
    }

    @ParameterizedTest @ValueSource(strings = {"hash", "framework", "role", "type", "digest", "commit", "build", "promotion", "channel"})
    void mutableUnreviewedOrMismatchedArtifactCannotQualify(String failure) throws Exception {
        var artifact = artifact(true); var version = version();
        switch (failure) {
            case "hash" -> artifact.setCapabilityManifestHash("changed");
            case "framework" -> version.setAiFabricFrameworkVersion("0.8.44");
            case "role" -> artifact.setArtifactRole("CONNECTOR");
            case "type" -> artifact.setArtifactType("SOURCE");
            case "digest" -> artifact.setImageDigest("mutable:latest");
            case "commit" -> artifact.setGitCommitSha("branch");
            case "build" -> artifact.setBuildRunId(null);
            case "promotion" -> artifact.setPromotedAt(null);
            case "channel" -> artifact.setPromotionChannel(null);
        }
        when(artifacts.require("release-runtime")).thenReturn(artifact);
        denied(() -> gate.requireEnabled(version, release()));
    }

    private void denied(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(StandaloneConversationRuntimeArtifactGate.Required.class)
            .hasMessageContaining("standalone-conversation-control");
    }

    private DeploymentVersionEntity version() {
        var version = new DeploymentVersionEntity(); version.setId("version"); version.setDeploymentId("deployment");
        version.setAiFabricFrameworkVersion("0.8.45"); version.setProviderConfigJson("{\"canonicalConversationsEnabled\":true}");
        return version;
    }

    private DeploymentReleaseEntity release() {
        var release = new DeploymentReleaseEntity(); release.setDeploymentId("deployment");
        release.setDeploymentVersionId("version"); release.setSourceArtifactId("release-runtime"); return release;
    }

    private DeploymentSourceArtifactEntity artifact(boolean standalone) throws Exception {
        var artifact = new DeploymentSourceArtifactEntity(); artifact.setId("release-runtime");
        artifact.setArtifactRole("RUNTIME"); artifact.setArtifactType("DOCKER_IMAGE");
        artifact.setImageDigest("sha256:" + "a".repeat(64)); artifact.setGitCommitSha("b".repeat(40));
        artifact.setBuildRunId("reviewed-build"); artifact.setPromotedAt(Instant.now()); artifact.setPromotionChannel("staging");
        var manifest = (ObjectNode) mapper.readTree("""
            {"schemaVersion":"loomai-runtime-capabilities-v1","aiFabricVersion":"0.8.45",
             "supportedBehaviorTypes":["CONVERSATIONAL"],"supportedActivationSources":[],
             "supportedChannelBindings":[],"supportedExecutionExtensions":["CONVERSATION_HANDOVER"],
             "capabilities":["conversation-control","canonical-conversation-orchestration-state"],
             "endpointClasses":["canonical-conversation-message"],"migrationIds":[],"verificationPackIds":[]}
            """);
        if (standalone) manifest.withArray("capabilities").add(StandaloneConversationRuntimeArtifactGate.CAPABILITY);
        var normalized = manifests.normalize(manifest);
        artifact.setCapabilityManifestJson(normalized.manifest().toString()); artifact.setCapabilityManifestHash(normalized.hash());
        return artifact;
    }
}
