package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentBehaviorReadinessEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentSourceArtifactEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVerificationRunEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.ApproveDeploymentBehaviorReadinessRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentBehaviorReadinessEvidenceInput;
import com.ai.fabric.platform.backend.deployment.model.DeploymentBehaviorVerificationProofInput;
import com.ai.fabric.platform.backend.deployment.model.EvaluateDeploymentBehaviorReadinessRequest;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentBehaviorReadinessRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentSourceArtifactRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVerificationRunRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginVersionEntity;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginRepository;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginVersionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeploymentBehaviorReadinessServiceTest {

    private final DeploymentBehaviorReadinessRepository readinessRepository = mock(DeploymentBehaviorReadinessRepository.class);
    private final DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
    private final DeploymentVersionRepository versionRepository = mock(DeploymentVersionRepository.class);
    private final DeploymentReleaseRepository releaseRepository = mock(DeploymentReleaseRepository.class);
    private final DeploymentVerificationRunRepository verificationRunRepository = mock(DeploymentVerificationRunRepository.class);
    private final DeploymentSourceArtifactRepository sourceArtifactRepository = mock(DeploymentSourceArtifactRepository.class);
    private final DeploymentTargetProfileRepository targetProfileRepository = mock(DeploymentTargetProfileRepository.class);
    private final MarketplacePluginRepository pluginRepository = mock(MarketplacePluginRepository.class);
    private final MarketplacePluginVersionRepository pluginVersionRepository = mock(MarketplacePluginVersionRepository.class);
    private final PlatformAuditService auditService = mock(PlatformAuditService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DeploymentSourceCapabilityManifestService capabilityManifestService =
        new DeploymentSourceCapabilityManifestService(objectMapper);

    @Test
    void evaluateCreatesHostedProofOnlyFromExactPublishedTemplateAndVerifiedImmutableRelease() throws Exception {
        Fixture fixture = verifiedFixture("staging");
        when(readinessRepository.findByMaterialHash(any())).thenReturn(Optional.empty());
        when(readinessRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service().evaluate(
            fixture.deployment().getId(),
            new EvaluateDeploymentBehaviorReadinessRequest(fixture.release().getId(), conversationalProof(), null)
        );

        assertThat(result.maturity()).isEqualTo("HOSTED_PROVEN");
        assertThat(result.effectiveMaturity()).isEqualTo("HOSTED_PROVEN");
        assertThat(result.templatePluginId()).isEqualTo("mkp-template-conversational-assistant");
        assertThat(result.templatePluginVersion()).isEqualTo("1.0.1");
        assertThat(result.frameworkVersion()).isEqualTo("0.8.3");
        assertThat(result.hostedProofs()).hasSize(1);
        assertThat(result.hostedProofs().get(0).path("environment").asText()).isEqualTo("staging");
        assertThat(result.hostedProofs().get(0).path("expiresAt").asText()).isNotBlank();
        assertThat(result.hostedProofs().get(0).path("behaviorProofs").get(0).path("status").asText())
            .isEqualTo("PASSED");
        assertThat(result.materialHash()).matches("sha256:[0-9a-f]{64}");
        verify(auditService).record(
            eq("DEPLOYMENT_BEHAVIOR_READINESS_CREATED"),
            eq("DEPLOYMENT_BEHAVIOR_READINESS"),
            any(),
            any()
        );
    }

    @Test
    void evaluateRejectsDirectDeploymentsWithoutBootstrappedMarketplaceTemplateProvenance() throws Exception {
        Fixture fixture = verifiedFixture("staging");
        fixture.version().setCompositionProvenanceJson("""
            {
              "deploymentBehaviorType": "CONVERSATIONAL",
              "compositionHash": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
              "verificationPackIds": ["conversational-behavior-v1"],
              "marketplaceInstalls": []
            }
            """);

        assertThatThrownBy(() -> service().evaluate(
            fixture.deployment().getId(),
            new EvaluateDeploymentBehaviorReadinessRequest(fixture.release().getId(), conversationalProof(), null)
        ))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("exactly one BOOTSTRAPPED Marketplace TEMPLATE");
    }

    @Test
    void marketReadyApprovalRequiresBothEnvironmentsAndEveryNamedEvidenceArea() {
        String proofExpiry = Instant.now().plusSeconds(86_400).toString();
        DeploymentBehaviorReadinessEntity candidate = candidate("""
            [{"environment":"staging","releaseStatus":"APPLIED_VERIFIED","verificationStatus":"PASSED","expiresAt":"%s"}]
            """.formatted(proofExpiry));
        when(readinessRepository.findById(candidate.getId())).thenReturn(Optional.of(candidate));

        assertThatThrownBy(() -> service().approve(candidate.getId(), approvalRequest()))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("both staging and production");

        candidate.setHostedProofsJson("""
            [
              {"environment":"staging","releaseStatus":"APPLIED_VERIFIED","verificationStatus":"PASSED","expiresAt":"%s"},
              {"environment":"production","releaseStatus":"APPLIED_VERIFIED","verificationStatus":"PASSED","expiresAt":"%s"}
            ]
            """.formatted(proofExpiry, proofExpiry));
        when(readinessRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service().approve(candidate.getId(), approvalRequest());

        assertThat(result.maturity()).isEqualTo("MARKET_READY");
        assertThat(result.approvalEvidence()).hasSize(DeploymentBehaviorReadinessService.REQUIRED_APPROVAL_AREAS.size());
        assertThat(result.expiresAt()).isBeforeOrEqualTo(Instant.parse(proofExpiry));
        verify(auditService).record(
            eq("DEPLOYMENT_BEHAVIOR_MARKET_READY_APPROVED"),
            eq("DEPLOYMENT_BEHAVIOR_READINESS"),
            eq(candidate.getId()),
            any()
        );
    }

    @Test
    void marketReadyApprovalRejectsExpiredEnvironmentProof() {
        String expired = Instant.now().minusSeconds(30).toString();
        String current = Instant.now().plusSeconds(86_400).toString();
        DeploymentBehaviorReadinessEntity candidate = candidate("""
            [
              {"environment":"staging","releaseStatus":"APPLIED_VERIFIED","verificationStatus":"PASSED","expiresAt":"%s"},
              {"environment":"production","releaseStatus":"APPLIED_VERIFIED","verificationStatus":"PASSED","expiresAt":"%s"}
            ]
            """.formatted(expired, current));
        when(readinessRepository.findById(candidate.getId())).thenReturn(Optional.of(candidate));

        assertThatThrownBy(() -> service().approve(candidate.getId(), approvalRequest()))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("current hosted proof")
            .hasMessageContaining("staging and production");
    }

    @Test
    void expiredOrWithdrawnEvidenceCannotRaiseBehaviorMaturity() {
        DeploymentBehaviorReadinessEntity current = candidate("[]");
        current.setMaturity("MARKET_READY");
        current.setExpiresAt(Instant.now().plusSeconds(300));
        DeploymentBehaviorReadinessEntity expired = candidate("[]");
        expired.setId("brr-expired");
        expired.setMaturity("MARKET_READY");
        expired.setExpiresAt(Instant.now().minusSeconds(1));
        when(readinessRepository.findByBehaviorTypeOrderByUpdatedAtDesc("CONVERSATIONAL"))
            .thenReturn(List.of(expired, current));

        assertThat(service().bestMaturity("CONVERSATIONAL", "PLATFORM_SELECTABLE"))
            .isEqualTo("MARKET_READY");

        current.setStatus("WITHDRAWN");
        assertThat(service().bestMaturity("CONVERSATIONAL", "PLATFORM_SELECTABLE"))
            .isEqualTo("PLATFORM_SELECTABLE");
    }

    @Test
    void evaluateRejectsBehaviorProofMissingRequiredChecks() throws Exception {
        Fixture fixture = verifiedFixture("staging");

        assertThatThrownBy(() -> service().evaluate(
            fixture.deployment().getId(),
            new EvaluateDeploymentBehaviorReadinessRequest(
                fixture.release().getId(),
                List.of(new DeploymentBehaviorVerificationProofInput(
                    "conversational-behavior-v1",
                    "PASSED",
                    "suite:run-1:stage-1",
                    List.of("AUTHENTICATED_QUERY")
                )),
                null
            )
        ))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("CONTINUATION")
            .hasMessageContaining("STRUCTURED_RESULT");
    }

    @Test
    void evaluateRejectsArtifactPromotedForAnotherEnvironment() throws Exception {
        Fixture fixture = verifiedFixture("staging");
        fixture.artifact().setPromotionChannel("production");

        assertThatThrownBy(() -> service().evaluate(
            fixture.deployment().getId(),
            new EvaluateDeploymentBehaviorReadinessRequest(fixture.release().getId(), conversationalProof(), null)
        ))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("promotion channel")
            .hasMessageContaining("hosted-proof environment");
    }

    @Test
    void reevaluatingExpiredMarketApprovalFallsBackToHostedProof() throws Exception {
        Fixture fixture = verifiedFixture("production");
        DeploymentBehaviorReadinessEntity expired = candidate("[]");
        expired.setMaterialHash(materialHashFor(fixture));
        expired.setMaturity("MARKET_READY");
        expired.setApprovalEvidenceJson("[{\"area\":\"COMMERCIAL\",\"status\":\"PASSED\"}]");
        expired.setApprovedByActorId("owner");
        expired.setApprovedAt(Instant.now().minusSeconds(600));
        expired.setApprovalNote("Expired approval");
        expired.setExpiresAt(Instant.now().minusSeconds(1));
        when(readinessRepository.findByMaterialHash(any())).thenReturn(Optional.of(expired));
        when(readinessRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service().evaluate(
            fixture.deployment().getId(),
            new EvaluateDeploymentBehaviorReadinessRequest(fixture.release().getId(), conversationalProof(), null)
        );

        assertThat(result.maturity()).isEqualTo("HOSTED_PROVEN");
        assertThat(result.approvalEvidence()).isEmpty();
        assertThat(result.approvedByActorId()).isNull();
        assertThat(result.approvedAt()).isNull();
    }

    private List<DeploymentBehaviorVerificationProofInput> conversationalProof() {
        return List.of(new DeploymentBehaviorVerificationProofInput(
            "conversational-behavior-v1",
            "PASSED",
            "suite:run-1:stage-1",
            List.of("AUTHENTICATED_QUERY", "CONTINUATION", "STRUCTURED_RESULT")
        ));
    }

    private String materialHashFor(Fixture fixture) throws Exception {
        JsonNode provenance = objectMapper.readTree(fixture.version().getCompositionProvenanceJson());
        JsonNode manifest = objectMapper.readTree(fixture.pluginVersion().getManifestJson());
        var material = objectMapper.createObjectNode();
        material.put("schemaVersion", DeploymentBehaviorReadinessService.SCHEMA_VERSION);
        material.put("behaviorType", "CONVERSATIONAL");
        material.put("templatePluginId", fixture.plugin().getId());
        material.put("templatePluginVersionId", fixture.pluginVersion().getId());
        material.put("templatePluginVersion", fixture.pluginVersion().getVersion());
        material.put("templateManifestHash", "sha256:" + sha256(canonicalJson(manifest)));
        material.put("compositionHash", provenance.path("compositionHash").asText());
        material.put("frameworkVersion", fixture.version().getAiFabricFrameworkVersion());
        material.put("sourceCommit", fixture.artifact().getGitCommitSha());
        material.put("imageDigest", fixture.artifact().getImageDigest());
        material.put("sourceCapabilityManifestHash", fixture.artifact().getCapabilityManifestHash());
        material.set("verificationPackIds", objectMapper.valueToTree(List.of("conversational-behavior-v1")));
        return "sha256:" + sha256(canonicalJson(material));
    }

    private Fixture verifiedFixture(String environment) throws Exception {
        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-1");
        deployment.setBehaviorType("CONVERSATIONAL");
        deployment.setEnvironmentName(environment);
        when(deploymentRepository.findById(deployment.getId())).thenReturn(Optional.of(deployment));

        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setId("rel-1");
        release.setDeploymentId(deployment.getId());
        release.setDeploymentVersionId("ver-1");
        release.setStatus("APPLIED_VERIFIED");
        release.setVerificationStatus("PASSED");
        release.setProvisioningStatus("ACTIVE");
        release.setSourceArtifactId("art-1");
        release.setTargetProfileId("target-1");
        release.setVerificationRunId("vr-1");
        when(releaseRepository.findById(release.getId())).thenReturn(Optional.of(release));

        JsonNode templateManifest = objectMapper.readTree("""
            {
              "schemaVersion": 1,
              "pluginId": "mkp-template-conversational-assistant",
              "version": "1.0.1",
              "pluginType": "TEMPLATE",
              "contributions": {
                "template": {
                  "deploymentBehavior": {
                    "type": "CONVERSATIONAL",
                    "verificationPackIds": ["conversational-behavior-v1"]
                  }
                }
              }
            }
            """);
        String manifestHash = "sha256:" + sha256(canonicalJson(templateManifest));
        DeploymentVersionEntity version = new DeploymentVersionEntity();
        version.setId("ver-1");
        version.setDeploymentId(deployment.getId());
        version.setStatus("PUBLISHED");
        version.setAiFabricFrameworkVersion("0.8.3");
        version.setCompositionProvenanceJson("""
            {
              "deploymentBehaviorType": "CONVERSATIONAL",
              "compositionHash": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
              "verificationPackIds": ["conversational-behavior-v1"],
              "marketplaceInstalls": [
                {
                  "pluginId": "mkp-template-conversational-assistant",
                  "pluginType": "TEMPLATE",
                  "pluginVersionId": "mkv-template-conversational-assistant-v101",
                  "pluginVersion": "1.0.1",
                  "installStatus": "BOOTSTRAPPED",
                  "manifestSha256": "%s"
                }
              ]
            }
            """.formatted(manifestHash));
        when(versionRepository.findById(version.getId())).thenReturn(Optional.of(version));

        MarketplacePluginEntity plugin = new MarketplacePluginEntity();
        plugin.setId("mkp-template-conversational-assistant");
        plugin.setPluginType("TEMPLATE");
        plugin.setStatus("ACTIVE");
        when(pluginRepository.findById(plugin.getId())).thenReturn(Optional.of(plugin));
        MarketplacePluginVersionEntity pluginVersion = new MarketplacePluginVersionEntity();
        pluginVersion.setId("mkv-template-conversational-assistant-v101");
        pluginVersion.setPluginId(plugin.getId());
        pluginVersion.setVersion("1.0.1");
        pluginVersion.setStatus("PUBLISHED");
        pluginVersion.setManifestJson(templateManifest.toString());
        when(pluginVersionRepository.findById(pluginVersion.getId())).thenReturn(Optional.of(pluginVersion));

        JsonNode capabilityManifest = objectMapper.readTree("""
            {
              "schemaVersion": "loomai-runtime-capabilities-v1",
              "aiFabricVersion": "0.8.3",
              "supportedBehaviorTypes": ["CONVERSATIONAL"],
              "supportedActivationSources": ["AUTHENTICATED_INTERACTIVE"],
              "supportedChannelBindings": ["BACKEND_API"],
              "supportedExecutionExtensions": [],
              "capabilities": ["ai-fabric-core"],
              "endpointClasses": ["chat-query"],
              "migrationIds": [],
              "verificationPackIds": ["conversational-behavior-v1"],
              "specialistBundles": []
            }
            """);
        String capabilityHash = capabilityManifestService.normalize(capabilityManifest).hash();
        DeploymentSourceArtifactEntity artifact = new DeploymentSourceArtifactEntity();
        artifact.setId("art-1");
        artifact.setImageDigest("sha256:" + "b".repeat(64));
        artifact.setGitCommitSha("c".repeat(40));
        artifact.setCapabilityManifestJson(capabilityManifest.toString());
        artifact.setCapabilityManifestHash(capabilityHash);
        artifact.setPromotionChannel(environment);
        artifact.setPromotedAt(Instant.now());
        when(sourceArtifactRepository.findById(artifact.getId())).thenReturn(Optional.of(artifact));

        DeploymentVerificationRunEntity verification = new DeploymentVerificationRunEntity();
        verification.setId("vr-1");
        verification.setReleaseId(release.getId());
        verification.setStatus("PASSED");
        when(verificationRunRepository.findById(verification.getId())).thenReturn(Optional.of(verification));

        DeploymentTargetProfileEntity targetProfile = new DeploymentTargetProfileEntity();
        targetProfile.setId("target-1");
        targetProfile.setEnvironmentName(environment);
        when(targetProfileRepository.findById(targetProfile.getId())).thenReturn(Optional.of(targetProfile));
        return new Fixture(deployment, release, version, plugin, pluginVersion, artifact);
    }

    private ApproveDeploymentBehaviorReadinessRequest approvalRequest() {
        List<DeploymentBehaviorReadinessEvidenceInput> evidence =
            DeploymentBehaviorReadinessService.REQUIRED_APPROVAL_AREAS.stream()
                .sorted()
                .map(area -> new DeploymentBehaviorReadinessEvidenceInput(
                    area,
                    "PASSED",
                    "evidence://" + area.toLowerCase(),
                    area + " evidence reviewed."
                ))
                .toList();
        return new ApproveDeploymentBehaviorReadinessRequest(
            evidence,
            "Approved only for this exact immutable candidate.",
            Instant.now().plusSeconds(86_400)
        );
    }

    private DeploymentBehaviorReadinessEntity candidate(String hostedProofs) {
        Instant now = Instant.now();
        DeploymentBehaviorReadinessEntity entity = new DeploymentBehaviorReadinessEntity();
        entity.setId("brr-1");
        entity.setBehaviorType("CONVERSATIONAL");
        entity.setTemplatePluginId("mkp-template-conversational-assistant");
        entity.setTemplatePluginVersionId("mkv-template-conversational-assistant-v101");
        entity.setTemplatePluginVersion("1.0.1");
        entity.setCompositionHash("a".repeat(64));
        entity.setMaterialHash("sha256:" + "b".repeat(64));
        entity.setFrameworkVersion("0.8.3");
        entity.setSourceArtifactId("art-1");
        entity.setSourceCommit("c".repeat(40));
        entity.setImageDigest("sha256:" + "d".repeat(64));
        entity.setSourceCapabilityManifestHash("e".repeat(64));
        entity.setVerificationPackIdsJson("[\"conversational-behavior-v1\"]");
        entity.setMaturity("HOSTED_PROVEN");
        entity.setStatus("ACTIVE");
        entity.setHostedProofsJson(hostedProofs);
        entity.setApprovalEvidenceJson("[]");
        entity.setDeploymentId("dep-1");
        entity.setDeploymentVersionId("ver-1");
        entity.setReleaseId("rel-1");
        entity.setEvaluatedAt(now);
        entity.setExpiresAt(now.plusSeconds(86_400));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }

    private DeploymentBehaviorReadinessService service() {
        return new DeploymentBehaviorReadinessService(
            readinessRepository,
            deploymentRepository,
            versionRepository,
            releaseRepository,
            verificationRunRepository,
            sourceArtifactRepository,
            targetProfileRepository,
            pluginRepository,
            pluginVersionRepository,
            capabilityManifestService,
            auditService,
            objectMapper
        );
    }

    private String canonicalJson(JsonNode node) throws Exception {
        return objectMapper.writeValueAsString(canonicalize(node));
    }

    private Object canonicalize(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isObject()) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            node.fields().forEachRemaining(entry -> sorted.put(entry.getKey(), canonicalize(entry.getValue())));
            return sorted;
        }
        if (node.isArray()) {
            List<Object> values = new ArrayList<>();
            node.forEach(value -> values.add(canonicalize(value)));
            return values;
        }
        if (node.isNumber()) return node.numberValue();
        if (node.isBoolean()) return node.booleanValue();
        return node.asText();
    }

    private String sha256(String value) throws Exception {
        return java.util.HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
        );
    }

    private record Fixture(
        DeploymentEntity deployment,
        DeploymentReleaseEntity release,
        DeploymentVersionEntity version,
        MarketplacePluginEntity plugin,
        MarketplacePluginVersionEntity pluginVersion,
        DeploymentSourceArtifactEntity artifact
    ) {
    }
}
