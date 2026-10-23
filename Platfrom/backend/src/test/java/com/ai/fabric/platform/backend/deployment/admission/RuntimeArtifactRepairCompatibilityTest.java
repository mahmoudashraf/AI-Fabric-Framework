package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.customerselfservice.service.CustomerReviewedCompositionService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentSourceArtifactEntity;
import com.ai.fabric.platform.backend.deployment.service.DeploymentSourceCapabilityManifestService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import static com.ai.fabric.platform.backend.deployment.admission.RuntimeArtifactRepairContracts.Mode;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RuntimeArtifactRepairCompatibilityTest {
    final ObjectMapper mapper = new ObjectMapper();
    final DeploymentSourceCapabilityManifestService manifests = new DeploymentSourceCapabilityManifestService(mapper);
    final CustomerReviewedCompositionService composition = mock(CustomerReviewedCompositionService.class);
    final RuntimeArtifactRepairCompatibility compatibility = new RuntimeArtifactRepairCompatibility(manifests, composition);
    @ParameterizedTest @EnumSource(Mode.class)
    void permitsOnlyReviewedAdditionsWithoutFrameworkUpgrade(Mode mode) throws Exception {
        var old = artifact(false); var next = artifact(true);
        assertThatCode(() -> compatibility.requireCompatible(old, next)).doesNotThrowAnyException();
        assertThatCode(() -> compatibility.requireCompatible(old, next, mode)).doesNotThrowAnyException();
        compatibility.requireTargetChannel(next, AdmissionContracts.Intent.CUSTOMER_STAGING);
        assertThatThrownBy(() -> compatibility.requireTargetChannel(next, AdmissionContracts.Intent.CUSTOMER_PRODUCTION)).isInstanceOf(AdmissionFailure.class);
    }
    @Test void permitsExactSamePinForColdMaintenance() throws Exception {
        var old = artifact(false); var next = artifact(false);
        assertThat(next).isNotSameAs(old);
        assertThat(compatibility.pin(next)).isEqualTo(compatibility.pin(old));
        assertThatCode(() -> compatibility.requireCompatible(old, next, Mode.COLD_MAINTENANCE_REAPPLY)).doesNotThrowAnyException();
    }
    @ParameterizedTest @EnumSource(value = Mode.class, names = "COLD_MAINTENANCE_REAPPLY", mode = EnumSource.Mode.EXCLUDE)
    @NullSource
    void rejectsExactSamePinWithoutExplicitColdMaintenance(Mode mode) throws Exception {
        var old = artifact(false); var next = artifact(false);
        assertThatThrownBy(() -> compatibility.requireCompatible(old, next, mode)).isInstanceOf(AdmissionFailure.class)
            .hasMessageContaining("RUNTIME_ARTIFACT_REPAIR_DISTINCT_REVIEWED_REPLACEMENT_REQUIRED");
    }
    @Test void strictOverloadRejectsExactSamePin() throws Exception {
        var old = artifact(false); var next = artifact(false);
        assertThatThrownBy(() -> compatibility.requireCompatible(old, next)).isInstanceOf(AdmissionFailure.class)
            .hasMessageContaining("RUNTIME_ARTIFACT_REPAIR_DISTINCT_REVIEWED_REPLACEMENT_REQUIRED");
    }
    @ParameterizedTest @EnumSource(Mode.class)
    void rejectsArtifactAliasWithSameDigest(Mode mode) throws Exception {
        var old = artifact(false); var alias = artifact(false); alias.setId("alias");
        when(composition.artifactFingerprint(alias)).thenReturn("alias-fingerprint");
        assertThatThrownBy(() -> compatibility.requireCompatible(old, alias)).isInstanceOf(AdmissionFailure.class)
            .hasMessageContaining("RUNTIME_ARTIFACT_REPAIR_DISTINCT_REVIEWED_REPLACEMENT_REQUIRED");
        assertThatThrownBy(() -> compatibility.requireCompatible(old, alias, mode)).isInstanceOf(AdmissionFailure.class)
            .hasMessageContaining("RUNTIME_ARTIFACT_REPAIR_DISTINCT_REVIEWED_REPLACEMENT_REQUIRED");
    }
    @ParameterizedTest @ValueSource(strings = {"fingerprint", "repository", "digest", "gitCommit", "buildRunId", "promotionChannel", "capabilityManifestHash"})
    void rejectsChangedPinForSameArtifactEvenInColdMaintenance(String field) throws Exception {
        var old = artifact(false); var next = artifact(false);
        switch (field) {
            case "fingerprint" -> when(composition.artifactFingerprint(next)).thenReturn("changed-fingerprint");
            case "repository" -> next.setImageRepository("reviewed/other-runtime");
            case "digest" -> next.setImageDigest("sha256:" + "b".repeat(64));
            case "gitCommit" -> next.setGitCommitSha("d".repeat(40));
            case "buildRunId" -> next.setBuildRunId("other-reviewed-ci");
            case "promotionChannel" -> next.setPromotionChannel("production");
            case "capabilityManifestHash" -> {
                var node = (ObjectNode) mapper.readTree(next.getCapabilityManifestJson());
                node.withArray("capabilities").add(RuntimeArtifactRepairCompatibility.VALIDITY_CAPABILITY); set(next, node);
            }
            default -> throw new IllegalArgumentException(field);
        }
        assertThat(compatibility.pin(next)).isNotEqualTo(compatibility.pin(old));
        assertThatThrownBy(() -> compatibility.requireCompatible(old, next, Mode.COLD_MAINTENANCE_REAPPLY)).isInstanceOf(AdmissionFailure.class)
            .hasMessageContaining("RUNTIME_ARTIFACT_REPAIR_DISTINCT_REVIEWED_REPLACEMENT_REQUIRED");
    }
    @ParameterizedTest @ValueSource(strings = {"capabilities", "endpointClasses", "migrationIds", "verificationPackIds", "supportedBehaviorTypes"})
    void rejectsLossOfOriginalContracts(String field) throws Exception {
        var old = artifact(false); var next = artifact(true); var node = (ObjectNode) mapper.readTree(next.getCapabilityManifestJson());
        node.putArray(field); set(next, node);
        assertThatThrownBy(() -> compatibility.requireCompatible(old, next)).isInstanceOf(AdmissionFailure.class);
        for (Mode mode : Mode.values()) {
            assertThatThrownBy(() -> compatibility.requireCompatible(old, next, mode)).isInstanceOf(AdmissionFailure.class)
                .hasMessageContaining("RUNTIME_ARTIFACT_REPAIR_ORIGINAL_CONTRACT_REMOVED");
        }
    }
    @ParameterizedTest @ValueSource(strings = {"capabilities", "migrationIds", "verificationPackIds"})
    void requiresReviewedValidityContractsForChangedArtifact(String field) throws Exception {
        var old = artifact(false); var next = artifact(true); var node = (ObjectNode) mapper.readTree(next.getCapabilityManifestJson());
        node.set(field, manifests.read(old.getCapabilityManifestJson()).path(field)); set(next, node);
        assertThatThrownBy(() -> compatibility.requireCompatible(old, next)).isInstanceOf(AdmissionFailure.class)
            .hasMessageContaining("RUNTIME_ARTIFACT_REPAIR_REVIEWED_VALIDITY_CONTRACT_REQUIRED");
        for (Mode mode : Mode.values()) {
            assertThatThrownBy(() -> compatibility.requireCompatible(old, next, mode)).isInstanceOf(AdmissionFailure.class)
                .hasMessageContaining("RUNTIME_ARTIFACT_REPAIR_REVIEWED_VALIDITY_CONTRACT_REQUIRED");
        }
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
    @ParameterizedTest @EnumSource(Mode.class)
    void rejectsDifferentFrameworkEvenWhenAllCapabilitiesRemain(Mode mode) throws Exception {
        var old = artifact(false); var next = artifact(true); var node = (ObjectNode) mapper.readTree(next.getCapabilityManifestJson());
        node.put("aiFabricVersion", "0.8.37"); set(next, node);
        assertThatThrownBy(() -> compatibility.requireCompatible(old, next)).isInstanceOf(AdmissionFailure.class);
        assertThatThrownBy(() -> compatibility.requireCompatible(old, next, mode)).isInstanceOf(AdmissionFailure.class)
            .hasMessageContaining("RUNTIME_ARTIFACT_REPAIR_SCHEMA_OR_FRAMEWORK_CHANGED");
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
