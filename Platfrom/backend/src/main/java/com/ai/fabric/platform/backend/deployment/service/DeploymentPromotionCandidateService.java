package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.*;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVerificationRunRepository;
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;

@Service
public class DeploymentPromotionCandidateService {
    private final DeploymentSourceArtifactService artifacts;
    private final DeploymentVerificationRunRepository verifications;
    private final DeploymentDocumentBindingResolver documentBindings;
    private final ObjectMapper mapper;

    public DeploymentPromotionCandidateService(DeploymentSourceArtifactService artifacts,
                                               DeploymentVerificationRunRepository verifications,
                                               DeploymentDocumentBindingResolver documentBindings, ObjectMapper mapper) {
        this.artifacts = artifacts; this.verifications = verifications; this.documentBindings = documentBindings; this.mapper = mapper;
    }

    public ObjectNode resolve(DeploymentEntity deployment, DeploymentVersionEntity version, DeploymentReleaseEntity staging,
                              DeploymentTargetProfileEntity production, PlatformConsumerEntity consumer) {
        if (!deployment.getId().equals(consumer.getBoundDeploymentId()) || !staging.getId().equals(consumer.getBoundReleaseId())
            || !staging.getTargetProfileId().equals(consumer.getBoundTargetProfileId())) {
            throw conflict("Promotion candidate must be the consumer's exact bound staging release.");
        }
        documentBindings.requireUnchanged(deployment, version, staging);
        var artifact = artifacts.require(required(staging.getSourceArtifactId(), "Reviewed source artifact is required for promotion."));
        if (!StringUtils.hasText(artifact.getImageRepository()) || artifact.getImageDigest() == null
            || !artifact.getImageDigest().matches("sha256:[a-f0-9]{64}") || !StringUtils.hasText(version.getConfigHash())) {
            throw conflict("Promotion requires an immutable image digest and candidate configuration hash.");
        }
        requireFreshVerification(deployment, version, staging, production);
        ObjectNode result = mapper.createObjectNode();
        result.put("schema", "CONSUMER_BOUND_PROMOTION_V1"); result.put("deploymentId", deployment.getId());
        result.put("customerId", deployment.getCustomerId()); result.put("consumerId", consumer.getConsumerId());
        result.put("expectedBindingRevision", consumer.getBindingRevision());
        result.put("stagingReleaseId", staging.getId()); result.put("stagingTargetProfileId", staging.getTargetProfileId());
        result.put("versionId", version.getId()); result.put("configHash", version.getConfigHash());
        result.put("sourceArtifactId", artifact.getId()); result.put("imageRepository", artifact.getImageRepository());
        result.put("imageDigest", artifact.getImageDigest()); result.put("capabilityManifestHash", artifact.getCapabilityManifestHash());
        result.put("stagingVerificationRunId", staging.getVerificationRunId());
        result.put("productionTargetProfileId", production.getId());
        result.put("productionTargetFingerprint", DeploymentEvidenceFingerprint.hash(mapper, mapper.valueToTree(production)));
        result.set("productionDocumentBinding", documentBindings.snapshot(deployment, version, production.getId()));
        return result;
    }

    public void requireFreshVerification(DeploymentEntity deployment, DeploymentVersionEntity version,
                                         DeploymentReleaseEntity release, DeploymentTargetProfileEntity policy) {
        long maxAge;
        try {
            maxAge = mapper.readTree(policy.getProviderConfigJson()).path("promotionEvidenceMaxAgeSeconds").asLong(0);
        } catch (Exception error) { throw conflict("Promotion evidence policy is unavailable."); }
        if (maxAge < 60 || maxAge > Duration.ofDays(7).toSeconds()) throw conflict("An operator-approved bounded promotion evidence age is required.");
        var run = verifications.findById(required(release.getVerificationRunId(), "Candidate verification evidence is missing."))
            .orElseThrow(() -> conflict("Candidate verification evidence is missing."));
        Instant now = Instant.now();
        if (!deployment.getId().equals(run.getDeploymentId()) || !version.getId().equals(run.getDeploymentVersionId())
            || !release.getId().equals(run.getReleaseId()) || !"POST_APPLY".equals(run.getVerificationType())
            || !"PASSED".equals(run.getStatus()) || run.getCompletedAt() == null || run.getCompletedAt().isAfter(now)
            || run.getCompletedAt().plusSeconds(maxAge).isBefore(now)) {
            throw conflict("Candidate verification is stale, failed or does not belong to the selected release.");
        }
    }

    public String fingerprint(ObjectNode candidate) { return DeploymentEvidenceFingerprint.hash(mapper, candidate); }

    private String required(String value, String error) { if (!StringUtils.hasText(value)) throw conflict(error); return value; }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
