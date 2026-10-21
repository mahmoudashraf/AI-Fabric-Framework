package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.customerselfservice.service.CustomerReviewedCompositionService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentSourceArtifactEntity;
import com.ai.fabric.platform.backend.deployment.service.DeploymentSourceCapabilityManifestService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RuntimeArtifactRepairCompatibilityTest {
    final ObjectMapper mapper = new ObjectMapper();
    final DeploymentSourceCapabilityManifestService manifests = new DeploymentSourceCapabilityManifestService(mapper);
    final CustomerReviewedCompositionService composition = mock(CustomerReviewedCompositionService.class);
    final RuntimeArtifactRepairCompatibility compatibility = new RuntimeArtifactRepairCompatibility(manifests, composition);
    @Test void permitsOnlyReviewedAdditionsWithoutFrameworkUpgrade() throws Exception {
        var old = artifact(false); var next = artifact(true);
        assertThatCode(() -> compatibility.requireCompatible(old, next)).doesNotThrowAnyException();
        compatibility.requireTargetChannel(next, AdmissionContracts.Intent.CUSTOMER_STAGING);
        assertThatThrownBy(() -> compatibility.requireTargetChannel(next, AdmissionContracts.Intent.CUSTOMER_PRODUCTION)).isInstanceOf(AdmissionFailure.class);
    }
    @ParameterizedTest @ValueSource(strings = {"capabilities", "endpointClasses", "migrationIds", "verificationPackIds", "supportedBehaviorTypes"})
    void rejectsLossOfOriginalContracts(String field) throws Exception {
        var old = artifact(false); var next = artifact(true); var node = (ObjectNode) mapper.readTree(next.getCapabilityManifestJson());
        node.putArray(field); set(next, node);
        assertThatThrownBy(() -> compatibility.requireCompatible(old, next)).isInstanceOf(AdmissionFailure.class);
    }
    @ParameterizedTest @ValueSource(strings = {"short", "ABCDEF0123456789ABCDEF0123456789ABCDEF0123", "z000000000000000000000000000000000000000"})
    void requiresExactCommitProvenance(String value) throws Exception {
        var next = artifact(true); next.setGitCommitSha(value);
        assertThatThrownBy(() -> compatibility.pin(next)).isInstanceOf(AdmissionFailure.class);
    }
    @ParameterizedTest @ValueSource(strings = {"", "preview", "Production"})
    void rejectsUnreviewedPromotionChannels(String value) throws Exception {
        var next = artifact(true); next.setPromotionChannel(value);
        assertThatThrownBy(() -> compatibility.pin(next)).isInstanceOf(AdmissionFailure.class);
    }
    @Test void rejectsDifferentFrameworkEvenWhenAllCapabilitiesRemain() throws Exception {
        var old = artifact(false); var next = artifact(true); var node = (ObjectNode) mapper.readTree(next.getCapabilityManifestJson());
        node.put("aiFabricVersion", "0.8.37"); set(next, node);
        assertThatThrownBy(() -> compatibility.requireCompatible(old, next)).isInstanceOf(AdmissionFailure.class);
    }
    DeploymentSourceArtifactEntity artifact(boolean replacement) throws Exception {
        var a = new DeploymentSourceArtifactEntity(); a.setId(replacement ? "next" : "original");
        a.setArtifactType("DOCKER_IMAGE"); a.setArtifactRole("RUNTIME"); a.setImageRepository("reviewed/runtime");
        a.setImageDigest("sha256:" + (replacement ? "b" : "a").repeat(64)); a.setGitCommitSha("c".repeat(40));
        a.setBuildRunId("reviewed-ci"); a.setPromotionChannel("staging"); a.setPromotedAt(Instant.parse("2026-10-20T00:00:00Z"));
        var node = mapper.createObjectNode().put("schemaVersion", "loomai-runtime-capabilities-v1").put("aiFabricVersion", "0.8.36");
        node.putArray("supportedBehaviorTypes").add("CONVERSATIONAL");
        node.putArray("supportedActivationSources").add("USER_QUERY"); node.putArray("supportedChannelBindings").add("CHAT");
        node.putArray("supportedExecutionExtensions"); node.putArray("specialistBundles");
        node.putArray("capabilities").add("deployment-local-provider-budget-v1");
        node.putArray("endpointClasses").add("provider-budget-administration");
        node.putArray("migrationIds").add("provider-budget-accounting-v1");
        node.putArray("verificationPackIds").add("provider-budget-hard-limit-v1");
        if (replacement) {
            node.withArray("capabilities").add(RuntimeArtifactRepairCompatibility.VALIDITY_CAPABILITY);
            node.withArray("migrationIds").add(RuntimeArtifactRepairCompatibility.VALIDITY_MIGRATION);
            node.withArray("verificationPackIds").add(RuntimeArtifactRepairCompatibility.VALIDITY_VERIFICATION);
        }
        set(a, node); when(composition.artifactFingerprint(a)).thenReturn(a.getId() + "-fingerprint"); return a;
    }
    void set(DeploymentSourceArtifactEntity a, ObjectNode node) throws Exception {
        var normalized = manifests.normalize(node); a.setCapabilityManifestHash(normalized.hash());
        a.setCapabilityManifestJson(mapper.writeValueAsString(normalized.manifest()));
    }
}
