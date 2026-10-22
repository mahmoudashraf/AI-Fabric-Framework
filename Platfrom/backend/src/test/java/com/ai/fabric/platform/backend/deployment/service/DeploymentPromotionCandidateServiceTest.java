package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.*;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVerificationRunRepository;
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeploymentPromotionCandidateServiceTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final DeploymentSourceArtifactService artifacts = mock(DeploymentSourceArtifactService.class);
    private final DeploymentVerificationRunRepository runs = mock(DeploymentVerificationRunRepository.class);
    private final DeploymentDocumentBindingResolver bindings = mock(DeploymentDocumentBindingResolver.class);
    private final DeploymentPromotionCandidateService service = new DeploymentPromotionCandidateService(artifacts, runs, bindings, mapper);
    private final DeploymentEntity deployment = new DeploymentEntity();
    private final DeploymentVersionEntity version = new DeploymentVersionEntity();
    private final DeploymentReleaseEntity staging = new DeploymentReleaseEntity();
    private final DeploymentTargetProfileEntity production = new DeploymentTargetProfileEntity();
    private final PlatformConsumerEntity consumer = new PlatformConsumerEntity();
    private final DeploymentSourceArtifactEntity artifact = new DeploymentSourceArtifactEntity();
    private final DeploymentVerificationRunEntity run = new DeploymentVerificationRunEntity();

    @BeforeEach void fixtures() {
        deployment.setId("dep-a"); deployment.setCustomerId("customer-a");
        version.setId("version-a"); version.setDeploymentId("dep-a"); version.setConfigHash("reviewed-config");
        staging.setId("stage-release-a"); staging.setTargetProfileId("stage-target");
        staging.setVerificationRunId("run-a"); staging.setSourceArtifactId("artifact-a");
        production.setId("prod-target"); production.setProviderConfigJson("{\"promotionEvidenceMaxAgeSeconds\":3600}");
        consumer.setConsumerId("consumer-a"); consumer.setBoundDeploymentId("dep-a");
        consumer.setBoundReleaseId("stage-release-a"); consumer.setBoundTargetProfileId("stage-target");
        artifact.setId("artifact-a"); artifact.setImageRepository("reviewed/runtime"); artifact.setImageDigest("sha256:" + "a".repeat(64));
        artifact.setCapabilityManifestHash("reviewed-capabilities");
        run.setId("run-a"); run.setDeploymentId("dep-a"); run.setDeploymentVersionId("version-a");
        run.setReleaseId("stage-release-a"); run.setStatus("PASSED"); run.setVerificationType("POST_APPLY");
        run.setCompletedAt(Instant.now().minusSeconds(60));
        when(artifacts.require("artifact-a")).thenReturn(artifact);
        when(runs.findById("run-a")).thenReturn(Optional.of(run));
        when(bindings.snapshot(deployment, version, "prod-target")).thenReturn(mapper.createObjectNode().put("bindingRevision", 1));
    }

    @Test void candidateCapturesExactConsumerImageEvidenceAndBindingWithoutMutableLatestLookup() {
        var candidate = service.resolve(deployment, version, staging, production, consumer);
        assertThat(candidate.path("stagingReleaseId").asText()).isEqualTo("stage-release-a");
        assertThat(candidate.path("imageDigest").asText()).isEqualTo(artifact.getImageDigest());
        assertThat(candidate.path("productionDocumentBinding").path("bindingRevision").asInt()).isEqualTo(1);
        assertThat(service.fingerprint(candidate)).hasSize(64);
        verify(bindings).requireUnchanged(deployment, version, staging);
    }

    @Test void aDifferentStagingReleaseOrTargetCannotReplaceTheBoundCandidate() {
        consumer.setBoundReleaseId("newer-stage-release");
        assertThatThrownBy(() -> service.resolve(deployment, version, staging, production, consumer)).hasMessageContaining("exact bound");
        consumer.setBoundReleaseId(staging.getId()); consumer.setBoundTargetProfileId("other-stage-target");
        assertThatThrownBy(() -> service.resolve(deployment, version, staging, production, consumer)).hasMessageContaining("exact bound");
    }

    @Test void imageConfigBindingAndConsumerRevisionChangesInvalidateTheFingerprint() {
        String first = service.fingerprint(service.resolve(deployment, version, staging, production, consumer));
        consumer.setBindingRevision(1);
        assertThat(service.fingerprint(service.resolve(deployment, version, staging, production, consumer))).isNotEqualTo(first);
        consumer.setBindingRevision(0); artifact.setImageDigest("sha256:" + "b".repeat(64));
        assertThat(service.fingerprint(service.resolve(deployment, version, staging, production, consumer))).isNotEqualTo(first);
        artifact.setImageDigest("sha256:" + "a".repeat(64)); version.setConfigHash("changed-config");
        assertThat(service.fingerprint(service.resolve(deployment, version, staging, production, consumer))).isNotEqualTo(first);
        version.setConfigHash("reviewed-config");
        when(bindings.snapshot(deployment, version, "prod-target")).thenReturn(mapper.createObjectNode().put("bindingRevision", 2));
        assertThat(service.fingerprint(service.resolve(deployment, version, staging, production, consumer))).isNotEqualTo(first);
    }

    @Test void tagOnlyImagesAndMissingReviewedConfigHashCannotBePromoted() {
        artifact.setImageDigest(null);
        assertThatThrownBy(() -> service.resolve(deployment, version, staging, production, consumer)).hasMessageContaining("immutable image");
        artifact.setImageDigest("sha256:" + "a".repeat(64)); version.setConfigHash(null);
        assertThatThrownBy(() -> service.resolve(deployment, version, staging, production, consumer)).hasMessageContaining("configuration hash");
    }

    @Test void freshnessRequiresAnApprovedPolicyAndExactCurrentVerificationIdentity() {
        production.setProviderConfigJson("{}");
        assertThatThrownBy(() -> service.resolve(deployment, version, staging, production, consumer)).hasMessageContaining("operator-approved");
        production.setProviderConfigJson("{\"promotionEvidenceMaxAgeSeconds\":3600}"); run.setReleaseId("different-release");
        assertThatThrownBy(() -> service.resolve(deployment, version, staging, production, consumer)).hasMessageContaining("does not belong");
    }

    @Test void failedExpiredOrFutureDatedEvidenceCannotPassPromotion() {
        run.setStatus("FAILED");
        assertThatThrownBy(() -> service.resolve(deployment, version, staging, production, consumer)).hasMessageContaining("failed");
        run.setStatus("PASSED"); run.setCompletedAt(Instant.now().minusSeconds(3601));
        assertThatThrownBy(() -> service.resolve(deployment, version, staging, production, consumer)).hasMessageContaining("stale");
        run.setCompletedAt(Instant.now().plusSeconds(100));
        assertThatThrownBy(() -> service.resolve(deployment, version, staging, production, consumer)).hasMessageContaining("stale");
    }

    @Test void explicitlyConfiguredOperatorPolicySupportsExistingFrozenTargetProfiles() {
        production.setProviderConfigJson("{}");
        ReflectionTestUtils.setField(service, "approvedPromotionEvidenceMaxAgeSeconds", 3600L);
        var candidate = service.resolve(deployment, version, staging, production, consumer);
        assertThat(candidate.path("promotionEvidenceMaxAgeSeconds").asLong()).isEqualTo(3600);
        String fingerprint = service.fingerprint(candidate);
        ReflectionTestUtils.setField(service, "approvedPromotionEvidenceMaxAgeSeconds", 1800L);
        assertThat(service.fingerprint(service.resolve(deployment, version, staging, production, consumer)))
            .isNotEqualTo(fingerprint);
        run.setCompletedAt(Instant.now().minusSeconds(1801));
        assertThatThrownBy(() -> service.resolve(deployment, version, staging, production, consumer))
            .hasMessageContaining("stale");
        assertThat(production.getProviderConfigJson()).isEqualTo("{}");
    }

    @Test void explicitTargetPolicyTakesPrecedenceAndInvalidValuesCannotUseOperatorDefault() {
        ReflectionTestUtils.setField(service, "approvedPromotionEvidenceMaxAgeSeconds", 7200L);
        assertThat(service.promotionEvidenceMaxAgeSeconds(production)).isEqualTo(3600);
        for (String value : new String[] { "0", "59", "604801", "null", "\"3600\"", "3600.5", "{}" }) {
            production.setProviderConfigJson("{\"promotionEvidenceMaxAgeSeconds\":" + value + "}");
            assertThatThrownBy(() -> service.promotionEvidenceMaxAgeSeconds(production))
                .as("invalid explicit policy %s", value).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        }
    }

    @Test void unapprovedOrOutOfBoundsOperatorPolicyRemainsClosed() {
        production.setProviderConfigJson("{}");
        for (long value : new long[] { 0, -1, 59, 604801, Long.MAX_VALUE }) {
            ReflectionTestUtils.setField(service, "approvedPromotionEvidenceMaxAgeSeconds", value);
            assertThatThrownBy(() -> service.promotionEvidenceMaxAgeSeconds(production)).hasMessageContaining("operator-approved");
        }
        production.setProviderConfigJson("[]");
        assertThatThrownBy(() -> service.promotionEvidenceMaxAgeSeconds(production)).hasMessageContaining("unavailable");
    }
}
