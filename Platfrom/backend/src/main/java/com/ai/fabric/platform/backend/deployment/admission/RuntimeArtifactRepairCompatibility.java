package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.customerselfservice.service.CustomerReviewedCompositionService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentSourceArtifactEntity;
import com.ai.fabric.platform.backend.deployment.service.DeploymentSourceCapabilityManifestService;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;
import static org.springframework.http.HttpStatus.CONFLICT;
import static com.ai.fabric.platform.backend.deployment.admission.RuntimeArtifactRepairContracts.*;

/** Reviewed additive product repair, not a change of framework or an implicit latest-image selection. */
@Component
public final class RuntimeArtifactRepairCompatibility {
    public static final String VALIDITY_CAPABILITY = "deployment-local-provider-budget-validity-extension-v1";
    public static final String VALIDITY_MIGRATION = "provider-budget-validity-extensions-v1";
    public static final String VALIDITY_VERIFICATION = "provider-budget-validity-extension-v1";
    private static final List<String> ARRAYS = List.of("supportedBehaviorTypes", "supportedActivationSources",
        "supportedChannelBindings", "supportedExecutionExtensions", "capabilities", "endpointClasses",
        "migrationIds", "verificationPackIds", "specialistBundles");
    private final DeploymentSourceCapabilityManifestService manifests;
    private final CustomerReviewedCompositionService composition;
    public RuntimeArtifactRepairCompatibility(DeploymentSourceCapabilityManifestService manifests,
        CustomerReviewedCompositionService composition) {
        this.manifests = manifests; this.composition = composition;
    }
    public ArtifactPin pin(DeploymentSourceArtifactEntity artifact) {
        if (artifact == null || !"DOCKER_IMAGE".equals(artifact.getArtifactType())
            || !"RUNTIME".equals(artifact.getArtifactRole()) || artifact.getPromotedAt() == null
            || artifact.getPromotionChannel() == null || !List.of("staging", "production").contains(artifact.getPromotionChannel()) || blank(artifact.getImageRepository())
            || artifact.getImageDigest() == null || !artifact.getImageDigest().matches("sha256:[a-f0-9]{64}")
            || artifact.getGitCommitSha() == null || !artifact.getGitCommitSha().matches("[a-f0-9]{40}")
            || blank(artifact.getBuildRunId())) fail("PROMOTED_IMMUTABLE_RUNTIME_REQUIRED");
        var normalized = manifests.normalize(manifests.read(artifact.getCapabilityManifestJson()));
        if (normalized.hash() == null || !normalized.hash().equals(artifact.getCapabilityManifestHash())) fail("MANIFEST_CHANGED");
        return new ArtifactPin(artifact.getId(), composition.artifactFingerprint(artifact), artifact.getImageRepository(),
            artifact.getImageDigest(), artifact.getGitCommitSha(), artifact.getBuildRunId(), artifact.getPromotionChannel(), artifact.getCapabilityManifestHash());
    }
    public void requireTargetChannel(DeploymentSourceArtifactEntity replacement, AdmissionContracts.Intent intent) {
        String channel = intent == AdmissionContracts.Intent.CUSTOMER_PRODUCTION ? "production" : "staging";
        if (!channel.equals(replacement.getPromotionChannel())) fail("TARGET_PROMOTION_CHANNEL_REQUIRED");
    }
    public void requireCompatible(DeploymentSourceArtifactEntity original, DeploymentSourceArtifactEntity replacement) {
        var beforePin = pin(original); var afterPin = pin(replacement);
        if (beforePin.artifactId().equals(afterPin.artifactId()) || beforePin.digest().equals(afterPin.digest())) fail("DISTINCT_REVIEWED_REPLACEMENT_REQUIRED");
        var before = manifests.read(original.getCapabilityManifestJson());
        var after = manifests.read(replacement.getCapabilityManifestJson());
        if (!Objects.equals(before.get("schemaVersion"), after.get("schemaVersion"))
            || !Objects.equals(before.get("aiFabricVersion"), after.get("aiFabricVersion"))) fail("SCHEMA_OR_FRAMEWORK_CHANGED");
        for (String field : ARRAYS) {
            if (!before.path(field).isArray() || !after.path(field).isArray()) fail("MANIFEST_CHANGED");
            for (JsonNode required : before.path(field)) if (!contains(after.path(field), required)) fail("ORIGINAL_CONTRACT_REMOVED");
        }
        require(after, "capabilities", VALIDITY_CAPABILITY);
        require(after, "migrationIds", VALIDITY_MIGRATION);
        require(after, "verificationPackIds", VALIDITY_VERIFICATION);
    }
    private static void require(JsonNode manifest, String field, String value) {
        for (JsonNode entry : manifest.path(field)) if (entry.isTextual() && value.equals(entry.textValue())) return;
        fail("REVIEWED_VALIDITY_CONTRACT_REQUIRED");
    }
    private static boolean contains(JsonNode values, JsonNode expected) {
        for (JsonNode value : values) if (expected.equals(value)) return true;
        return false;
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static void fail(String code) { throw new AdmissionFailure(CONFLICT, "RUNTIME_ARTIFACT_REPAIR_" + code); }
}
