package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.behavior.DeploymentBehaviorReadinessLookup;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentBehaviorReadinessEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentSourceArtifactEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVerificationRunEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.ApproveDeploymentBehaviorReadinessRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentBehaviorReadinessEvidenceInput;
import com.ai.fabric.platform.backend.deployment.model.DeploymentBehaviorReadinessSummary;
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
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class DeploymentBehaviorReadinessService implements DeploymentBehaviorReadinessLookup {

    public static final String SCHEMA_VERSION = "loomai-behavior-readiness-v1";
    private static final Duration DEFAULT_EVIDENCE_TTL = Duration.ofDays(90);
    private static final Duration MAX_EVIDENCE_TTL = Duration.ofDays(180);
    private static final List<String> MATURITY_ORDER = List.of(
        "FRAMEWORK_AVAILABLE",
        "RUNTIME_PACKAGED",
        "PLATFORM_SELECTABLE",
        "HOSTED_PROVEN",
        "MARKET_READY"
    );
    public static final Set<String> REQUIRED_APPROVAL_AREAS = Set.of(
        "BEHAVIOR",
        "SECURITY_ISOLATION",
        "LIFECYCLE_RECOVERY",
        "OPERATIONS",
        "COST_LIMITS",
        "CUSTOMER_UX",
        "SUPPORT",
        "COMMERCIAL",
        "CONTROLLED_PRODUCTION"
    );
    private static final Map<String, Set<String>> REQUIRED_BEHAVIOR_CHECKS = Map.of(
        "CONVERSATIONAL", Set.of("AUTHENTICATED_QUERY", "CONTINUATION", "STRUCTURED_RESULT"),
        "AGENTIC_SPECIALIST_TEAM", Set.of("DURABLE_EXECUTION", "DISTINCT_SPECIALISTS", "IDEMPOTENT_REPLAY"),
        "SMART_BRAIN", Set.of("CLOUD_EVENT_INGRESS", "DURABLE_RESULT", "IDEMPOTENT_REPLAY")
    );

    private final DeploymentBehaviorReadinessRepository readinessRepository;
    private final DeploymentRepository deploymentRepository;
    private final DeploymentVersionRepository versionRepository;
    private final DeploymentReleaseRepository releaseRepository;
    private final DeploymentVerificationRunRepository verificationRunRepository;
    private final DeploymentSourceArtifactRepository sourceArtifactRepository;
    private final DeploymentTargetProfileRepository targetProfileRepository;
    private final MarketplacePluginRepository pluginRepository;
    private final MarketplacePluginVersionRepository pluginVersionRepository;
    private final DeploymentSourceCapabilityManifestService capabilityManifestService;
    private final PlatformAuditService auditService;
    private final ObjectMapper objectMapper;

    public DeploymentBehaviorReadinessService(
        DeploymentBehaviorReadinessRepository readinessRepository,
        DeploymentRepository deploymentRepository,
        DeploymentVersionRepository versionRepository,
        DeploymentReleaseRepository releaseRepository,
        DeploymentVerificationRunRepository verificationRunRepository,
        DeploymentSourceArtifactRepository sourceArtifactRepository,
        DeploymentTargetProfileRepository targetProfileRepository,
        MarketplacePluginRepository pluginRepository,
        MarketplacePluginVersionRepository pluginVersionRepository,
        DeploymentSourceCapabilityManifestService capabilityManifestService,
        PlatformAuditService auditService,
        ObjectMapper objectMapper
    ) {
        this.readinessRepository = readinessRepository;
        this.deploymentRepository = deploymentRepository;
        this.versionRepository = versionRepository;
        this.releaseRepository = releaseRepository;
        this.verificationRunRepository = verificationRunRepository;
        this.sourceArtifactRepository = sourceArtifactRepository;
        this.targetProfileRepository = targetProfileRepository;
        this.pluginRepository = pluginRepository;
        this.pluginVersionRepository = pluginVersionRepository;
        this.capabilityManifestService = capabilityManifestService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<DeploymentBehaviorReadinessSummary> list(String behaviorType, String templatePluginId) {
        List<DeploymentBehaviorReadinessEntity> candidates;
        if (StringUtils.hasText(behaviorType)) {
            candidates = readinessRepository.findByBehaviorTypeOrderByUpdatedAtDesc(normalizeCode(behaviorType));
        } else if (StringUtils.hasText(templatePluginId)) {
            candidates = readinessRepository.findByTemplatePluginIdOrderByUpdatedAtDesc(templatePluginId.trim());
        } else {
            candidates = readinessRepository.findAllByOrderByUpdatedAtDesc();
        }
        return candidates.stream()
            .filter(candidate -> !StringUtils.hasText(templatePluginId)
                || templatePluginId.trim().equals(candidate.getTemplatePluginId()))
            .map(this::toSummary)
            .toList();
    }

    @Transactional(readOnly = true)
    public DeploymentBehaviorReadinessSummary get(String candidateId) {
        return toSummary(requireCandidate(candidateId));
    }

    @Override
    @Transactional(readOnly = true)
    public String bestMaturity(String behaviorType, String fallbackMaturity) {
        String fallback = normalizeMaturity(fallbackMaturity);
        return readinessRepository.findByBehaviorTypeOrderByUpdatedAtDesc(normalizeCode(behaviorType)).stream()
            .filter(candidate -> "ACTIVE".equals(candidate.getStatus()))
            .map(this::effectiveMaturity)
            .max(Comparator.comparingInt(this::maturityRank))
            .filter(value -> maturityRank(value) > maturityRank(fallback))
            .orElse(fallback);
    }

    @Transactional
    public DeploymentBehaviorReadinessSummary evaluate(
        String deploymentId,
        EvaluateDeploymentBehaviorReadinessRequest request
    ) {
        DeploymentEntity deployment = deploymentRepository.findById(deploymentId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment not found: " + deploymentId));
        DeploymentReleaseEntity release = releaseRepository.findById(request.releaseId())
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Release not found: " + request.releaseId()));
        if (!deploymentId.equals(release.getDeploymentId())) {
            throw new ResponseStatusException(BAD_REQUEST, "Release does not belong to deployment: " + deploymentId);
        }
        requireVerifiedRelease(release);

        DeploymentVersionEntity version = versionRepository.findById(release.getDeploymentVersionId())
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment version not found: " + release.getDeploymentVersionId()));
        if (!deploymentId.equals(version.getDeploymentId()) || !"PUBLISHED".equalsIgnoreCase(version.getStatus())) {
            throw new ResponseStatusException(CONFLICT, "Readiness evaluation requires a published version owned by the deployment.");
        }

        JsonNode provenance = readObject(version.getCompositionProvenanceJson(), "composition provenance");
        String behaviorType = requireText(provenance.path("deploymentBehaviorType").asText(null), "deployment behavior type");
        if (!behaviorType.equals(deployment.getBehaviorType())) {
            throw new ResponseStatusException(CONFLICT, "Published behavior provenance does not match the deployment behavior.");
        }
        String compositionHash = requireHash(provenance.path("compositionHash").asText(null), "compositionHash");
        TemplateOrigin template = requirePublishedTemplateOrigin(provenance, behaviorType);
        List<String> verificationPackIds = requireVerificationPacks(provenance, template.manifest());
        ArrayNode behaviorProofs = requireBehaviorProofs(
            behaviorType,
            verificationPackIds,
            request.behaviorProofs()
        );

        String sourceArtifactId = requireText(release.getSourceArtifactId(), "sourceArtifactId");
        DeploymentTargetProfileEntity targetProfile = targetProfileRepository.findById(requireText(
            release.getTargetProfileId(),
            "targetProfileId"
        )).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment target profile not found."));

        String environment = normalizedEnvironment(targetProfile.getEnvironmentName(), deployment.getEnvironmentName());
        DeploymentSourceArtifactEntity artifact = sourceArtifactRepository.findById(sourceArtifactId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Source artifact not found: " + sourceArtifactId));
        requireImmutablePromotedArtifact(artifact, version, behaviorType, verificationPackIds, environment);
        DeploymentVerificationRunEntity verification = requirePassedVerificationRun(release);
        Instant now = Instant.now();
        Instant expiresAt = requireExpiry(request.expiresAt(), now, DEFAULT_EVIDENCE_TTL);
        String materialHash = materialHash(
            behaviorType,
            template,
            compositionHash,
            version.getAiFabricFrameworkVersion(),
            artifact,
            verificationPackIds
        );
        DeploymentBehaviorReadinessEntity candidate = readinessRepository.findByMaterialHash(materialHash)
            .orElseGet(DeploymentBehaviorReadinessEntity::new);
        boolean created = candidate.getId() == null;
        if (created) {
            candidate.setId("brr-" + UUID.randomUUID().toString().substring(0, 12));
            candidate.setCreatedAt(now);
            candidate.setApprovalEvidenceJson("[]");
            candidate.setHostedProofsJson("[]");
            candidate.setMaturity("HOSTED_PROVEN");
        } else if ("WITHDRAWN".equals(candidate.getStatus())) {
            throw new ResponseStatusException(
                CONFLICT,
                "Withdrawn readiness evidence cannot be reactivated without a new immutable candidate."
            );
        } else if (candidate.getExpiresAt() == null || !candidate.getExpiresAt().isAfter(now)) {
            clearMarketApproval(candidate);
        }
        candidate.setBehaviorType(behaviorType);
        candidate.setTemplatePluginId(template.plugin().getId());
        candidate.setTemplatePluginVersionId(template.version().getId());
        candidate.setTemplatePluginVersion(template.version().getVersion());
        candidate.setCompositionHash(compositionHash);
        candidate.setMaterialHash(materialHash);
        candidate.setFrameworkVersion(version.getAiFabricFrameworkVersion());
        candidate.setSourceArtifactId(artifact.getId());
        candidate.setSourceCommit(artifact.getGitCommitSha().trim());
        candidate.setImageDigest(artifact.getImageDigest().trim());
        candidate.setSourceCapabilityManifestHash(artifact.getCapabilityManifestHash().trim());
        candidate.setVerificationPackIdsJson(writeJson(verificationPackIds));
        candidate.setStatus("ACTIVE");
        ArrayNode hostedProofs = mergeHostedProof(
            candidate.getHostedProofsJson(),
            deployment,
            version,
            release,
            verification,
            targetProfile,
            artifact,
            behaviorProofs,
            environment,
            now,
            expiresAt
        );
        candidate.setHostedProofsJson(hostedProofs.toString());
        candidate.setDeploymentId(deploymentId);
        candidate.setDeploymentVersionId(version.getId());
        candidate.setReleaseId(release.getId());
        candidate.setEvaluatedAt(now);
        if ("MARKET_READY".equals(candidate.getMaturity())) {
            Optional<Instant> proofBoundary = currentStagingAndProductionProofBoundary(hostedProofs, now);
            if (proofBoundary.isPresent()) {
                candidate.setExpiresAt(earlier(candidate.getExpiresAt(), proofBoundary.get()));
            } else {
                clearMarketApproval(candidate);
                candidate.setExpiresAt(latestCurrentHostedProofExpiry(hostedProofs, now).orElse(expiresAt));
            }
        } else {
            candidate.setExpiresAt(latestCurrentHostedProofExpiry(hostedProofs, now).orElse(expiresAt));
        }
        candidate.setUpdatedAt(now);
        candidate = readinessRepository.save(candidate);

        auditService.record(
            created ? "DEPLOYMENT_BEHAVIOR_READINESS_CREATED" : "DEPLOYMENT_BEHAVIOR_READINESS_REEVALUATED",
            "DEPLOYMENT_BEHAVIOR_READINESS",
            candidate.getId(),
            Map.of(
                "deploymentId", deploymentId,
                "releaseId", release.getId(),
                "behaviorType", behaviorType,
                "templatePluginId", template.plugin().getId(),
                "templatePluginVersion", template.version().getVersion(),
                "compositionHash", compositionHash,
                "environment", environment,
                "maturity", candidate.getMaturity()
            )
        );
        return toSummary(candidate);
    }

    @Transactional
    public DeploymentBehaviorReadinessSummary approve(
        String candidateId,
        ApproveDeploymentBehaviorReadinessRequest request
    ) {
        DeploymentBehaviorReadinessEntity candidate = requireCandidate(candidateId);
        if (!"ACTIVE".equals(candidate.getStatus())) {
            throw new ResponseStatusException(CONFLICT, "Withdrawn readiness evidence cannot be approved.");
        }
        if (!"HOSTED_PROVEN".equals(effectiveMaturity(candidate))
            && !"MARKET_READY".equals(effectiveMaturity(candidate))) {
            throw new ResponseStatusException(CONFLICT, "The candidate requires current hosted proof before market approval.");
        }
        Instant now = Instant.now();
        Instant proofBoundary = requireStagingAndProductionProof(candidate.getHostedProofsJson(), now);
        ArrayNode evidence = requireApprovalEvidence(request.evidence());
        candidate.setApprovalEvidenceJson(evidence.toString());
        candidate.setMaturity("MARKET_READY");
        candidate.setApprovedByActorId(PlatformSecurityContext.actorIdOrSystem());
        candidate.setApprovedAt(now);
        candidate.setApprovalNote(request.approvalNote().trim());
        candidate.setExpiresAt(earlier(requireExpiry(request.expiresAt(), now, MAX_EVIDENCE_TTL), proofBoundary));
        candidate.setUpdatedAt(now);
        candidate = readinessRepository.save(candidate);
        auditService.record(
            "DEPLOYMENT_BEHAVIOR_MARKET_READY_APPROVED",
            "DEPLOYMENT_BEHAVIOR_READINESS",
            candidate.getId(),
            Map.of(
                "behaviorType", candidate.getBehaviorType(),
                "templatePluginId", candidate.getTemplatePluginId(),
                "templatePluginVersion", candidate.getTemplatePluginVersion(),
                "materialHash", candidate.getMaterialHash(),
                "evidenceAreas", REQUIRED_APPROVAL_AREAS
            )
        );
        return toSummary(candidate);
    }

    @Transactional
    public DeploymentBehaviorReadinessSummary withdraw(String candidateId, String reason) {
        DeploymentBehaviorReadinessEntity candidate = requireCandidate(candidateId);
        candidate.setStatus("WITHDRAWN");
        candidate.setMaturity("PLATFORM_SELECTABLE");
        candidate.setApprovalNote(reason.trim());
        candidate.setUpdatedAt(Instant.now());
        candidate = readinessRepository.save(candidate);
        auditService.record(
            "DEPLOYMENT_BEHAVIOR_READINESS_WITHDRAWN",
            "DEPLOYMENT_BEHAVIOR_READINESS",
            candidate.getId(),
            Map.of("reason", reason.trim(), "materialHash", candidate.getMaterialHash())
        );
        return toSummary(candidate);
    }

    private void requireVerifiedRelease(DeploymentReleaseEntity release) {
        if (!"APPLIED_VERIFIED".equals(release.getStatus())
            || !"PASSED".equals(release.getVerificationStatus())
            || !"ACTIVE".equals(release.getProvisioningStatus())) {
            throw new ResponseStatusException(
                CONFLICT,
                "Readiness evaluation requires an APPLIED_VERIFIED release with PASSED verification and ACTIVE provisioning."
            );
        }
    }

    private TemplateOrigin requirePublishedTemplateOrigin(JsonNode provenance, String behaviorType) {
        List<JsonNode> templates = new ArrayList<>();
        JsonNode installs = provenance.path("marketplaceInstalls");
        if (installs.isArray()) {
            installs.forEach(item -> {
                if ("TEMPLATE".equals(item.path("pluginType").asText(""))
                    && "BOOTSTRAPPED".equals(item.path("installStatus").asText(""))) {
                    templates.add(item);
                }
            });
        }
        if (templates.size() != 1) {
            throw new ResponseStatusException(
                CONFLICT,
                "Readiness evaluation requires exactly one BOOTSTRAPPED Marketplace TEMPLATE in composition provenance."
            );
        }
        JsonNode origin = templates.get(0);
        String pluginId = requireText(origin.path("pluginId").asText(null), "template plugin id");
        String versionId = requireText(origin.path("pluginVersionId").asText(null), "template plugin version id");
        MarketplacePluginEntity plugin = pluginRepository.findById(pluginId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Marketplace template not found: " + pluginId));
        MarketplacePluginVersionEntity version = pluginVersionRepository.findById(versionId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Marketplace template version not found: " + versionId));
        if (!"TEMPLATE".equals(plugin.getPluginType())
            || !"ACTIVE".equals(plugin.getStatus())
            || !pluginId.equals(version.getPluginId())
            || !"PUBLISHED".equals(version.getStatus())) {
            throw new ResponseStatusException(CONFLICT, "Template origin must reference an active, published Marketplace template version.");
        }
        JsonNode manifest = readObject(version.getManifestJson(), "template manifest");
        String manifestBehavior = manifest.path("contributions").path("template")
            .path("deploymentBehavior").path("type").asText("");
        if (!behaviorType.equals(manifestBehavior)) {
            throw new ResponseStatusException(CONFLICT, "Template behavior does not match published composition behavior.");
        }
        String expectedManifestHash = "sha256:" + sha256(canonicalJson(manifest));
        if (!expectedManifestHash.equals(origin.path("manifestSha256").asText(""))) {
            throw new ResponseStatusException(CONFLICT, "Template manifest hash does not match immutable composition provenance.");
        }
        return new TemplateOrigin(plugin, version, manifest, expectedManifestHash);
    }

    private List<String> requireVerificationPacks(JsonNode provenance, JsonNode templateManifest) {
        List<String> actual = stringList(provenance.path("verificationPackIds"));
        List<String> expected = stringList(templateManifest.path("contributions").path("template")
            .path("deploymentBehavior").path("verificationPackIds"));
        if (actual.isEmpty() || !new LinkedHashSet<>(actual).equals(new LinkedHashSet<>(expected))) {
            throw new ResponseStatusException(CONFLICT, "Published verification packs must exactly match the template contract.");
        }
        return actual.stream().distinct().sorted().toList();
    }

    private ArrayNode requireBehaviorProofs(
        String behaviorType,
        List<String> verificationPackIds,
        List<DeploymentBehaviorVerificationProofInput> inputs
    ) {
        if (inputs == null || inputs.isEmpty()) {
            throw new ResponseStatusException(CONFLICT, "Behavior verification proof is required.");
        }
        Map<String, DeploymentBehaviorVerificationProofInput> byPack = new LinkedHashMap<>();
        for (DeploymentBehaviorVerificationProofInput input : inputs) {
            String packId = requireText(input.verificationPackId(), "behavior verification pack id");
            if (byPack.put(packId, input) != null) {
                throw new ResponseStatusException(BAD_REQUEST, "Duplicate behavior verification proof: " + packId);
            }
            if (!"PASSED".equals(normalizeCode(input.status()))) {
                throw new ResponseStatusException(CONFLICT, "Behavior verification proof must be PASSED: " + packId);
            }
        }
        if (!byPack.keySet().equals(new LinkedHashSet<>(verificationPackIds))) {
            throw new ResponseStatusException(
                CONFLICT,
                "Behavior verification proofs must exactly match the published template verification packs."
            );
        }

        Set<String> requiredChecks = REQUIRED_BEHAVIOR_CHECKS.getOrDefault(behaviorType, Set.of());
        ArrayNode result = objectMapper.createArrayNode();
        byPack.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            DeploymentBehaviorVerificationProofInput input = entry.getValue();
            Set<String> checks = input.passedChecks().stream()
                .map(this::normalizeCode)
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            if (!checks.containsAll(requiredChecks)) {
                Set<String> missing = new LinkedHashSet<>(requiredChecks);
                missing.removeAll(checks);
                throw new ResponseStatusException(
                    CONFLICT,
                    "Behavior verification proof is missing required checks: " + String.join(", ", missing)
                );
            }
            ObjectNode proof = result.addObject();
            proof.put("verificationPackId", entry.getKey());
            proof.put("status", "PASSED");
            proof.put("evidenceRef", input.evidenceRef().trim());
            proof.set("passedChecks", objectMapper.valueToTree(checks.stream().sorted().toList()));
        });
        return result;
    }

    private void requireImmutablePromotedArtifact(
        DeploymentSourceArtifactEntity artifact,
        DeploymentVersionEntity version,
        String behaviorType,
        List<String> verificationPackIds,
        String environment
    ) {
        if (artifact.getPromotedAt() == null
            || !StringUtils.hasText(artifact.getPromotionChannel())
            || !StringUtils.hasText(artifact.getImageDigest())
            || !artifact.getImageDigest().matches("sha256:[0-9a-f]{64}")
            || !StringUtils.hasText(artifact.getGitCommitSha())
            || !StringUtils.hasText(artifact.getCapabilityManifestHash())) {
            throw new ResponseStatusException(
                CONFLICT,
                "Readiness evaluation requires a promoted source artifact with immutable image, commit, and capability-manifest hashes."
            );
        }
        JsonNode manifest = readObject(artifact.getCapabilityManifestJson(), "source capability manifest");
        DeploymentSourceCapabilityManifestService.NormalizedCapabilityManifest normalized =
            capabilityManifestService.normalize(manifest);
        if (!artifact.getCapabilityManifestHash().equals(normalized.hash())) {
            throw new ResponseStatusException(CONFLICT, "Source capability manifest hash does not match its content.");
        }
        if (!version.getAiFabricFrameworkVersion().equals(manifest.path("aiFabricVersion").asText(""))) {
            throw new ResponseStatusException(CONFLICT, "Source artifact AI Fabric version does not match the published V04 version.");
        }
        if (!stringList(manifest.path("supportedBehaviorTypes")).contains(behaviorType)) {
            throw new ResponseStatusException(CONFLICT, "Source artifact capability manifest does not support the published behavior.");
        }
        if (!new LinkedHashSet<>(stringList(manifest.path("verificationPackIds")))
            .containsAll(verificationPackIds)) {
            throw new ResponseStatusException(CONFLICT, "Source artifact capability manifest does not support the published verification packs.");
        }
        if (("staging".equals(environment) || "production".equals(environment))
            && !environment.equalsIgnoreCase(artifact.getPromotionChannel())) {
            throw new ResponseStatusException(
                CONFLICT,
                "Source artifact promotion channel does not match the hosted-proof environment."
            );
        }
    }

    private DeploymentVerificationRunEntity requirePassedVerificationRun(DeploymentReleaseEntity release) {
        if (StringUtils.hasText(release.getVerificationRunId())) {
            DeploymentVerificationRunEntity run = verificationRunRepository.findById(release.getVerificationRunId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Release verification run not found."));
            if (release.getId().equals(run.getReleaseId()) && "PASSED".equals(run.getStatus())) {
                return run;
            }
        }
        return verificationRunRepository.findByReleaseIdOrderByCreatedAtDesc(release.getId()).stream()
            .filter(run -> "PASSED".equals(run.getStatus()))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(CONFLICT, "Release does not have a passed persisted verification run."));
    }

    private String materialHash(
        String behaviorType,
        TemplateOrigin template,
        String compositionHash,
        String frameworkVersion,
        DeploymentSourceArtifactEntity artifact,
        List<String> verificationPackIds
    ) {
        ObjectNode material = objectMapper.createObjectNode();
        material.put("schemaVersion", SCHEMA_VERSION);
        material.put("behaviorType", behaviorType);
        material.put("templatePluginId", template.plugin().getId());
        material.put("templatePluginVersionId", template.version().getId());
        material.put("templatePluginVersion", template.version().getVersion());
        material.put("templateManifestHash", template.manifestHash());
        material.put("compositionHash", compositionHash);
        material.put("frameworkVersion", requireText(frameworkVersion, "framework version"));
        material.put("sourceCommit", artifact.getGitCommitSha().trim());
        material.put("imageDigest", artifact.getImageDigest().trim());
        material.put("sourceCapabilityManifestHash", artifact.getCapabilityManifestHash().trim());
        material.set("verificationPackIds", objectMapper.valueToTree(verificationPackIds));
        return "sha256:" + sha256(canonicalJson(material));
    }

    private ArrayNode mergeHostedProof(
        String currentJson,
        DeploymentEntity deployment,
        DeploymentVersionEntity version,
        DeploymentReleaseEntity release,
        DeploymentVerificationRunEntity verification,
        DeploymentTargetProfileEntity targetProfile,
        DeploymentSourceArtifactEntity artifact,
        ArrayNode behaviorProofs,
        String environment,
        Instant verifiedAt,
        Instant expiresAt
    ) {
        ArrayNode result = objectMapper.createArrayNode();
        JsonNode current = readJson(currentJson, objectMapper.createArrayNode());
        if (current.isArray()) {
            current.forEach(item -> {
                if (!release.getId().equals(item.path("releaseId").asText(""))) {
                    result.add(item.deepCopy());
                }
            });
        }
        ObjectNode proof = result.addObject();
        proof.put("environment", environment);
        proof.put("deploymentId", deployment.getId());
        proof.put("deploymentVersionId", version.getId());
        proof.put("releaseId", release.getId());
        proof.put("verificationRunId", verification.getId());
        proof.put("targetProfileId", targetProfile.getId());
        proof.put("sourceArtifactId", artifact.getId());
        proof.put("releaseStatus", release.getStatus());
        proof.put("verificationStatus", verification.getStatus());
        proof.set("behaviorProofs", behaviorProofs.deepCopy());
        proof.put("verifiedAt", verifiedAt.toString());
        proof.put("expiresAt", expiresAt.toString());
        return result;
    }

    private Instant requireStagingAndProductionProof(String hostedProofsJson, Instant now) {
        JsonNode proofs = readJson(hostedProofsJson, objectMapper.createArrayNode());
        return currentStagingAndProductionProofBoundary(proofs, now)
            .orElseThrow(() -> new ResponseStatusException(
                CONFLICT,
                "MARKET_READY requires current hosted proof for the exact candidate in both staging and production."
            ));
    }

    private Optional<Instant> currentStagingAndProductionProofBoundary(JsonNode proofs, Instant now) {
        Map<String, Instant> current = new LinkedHashMap<>();
        if (proofs != null && proofs.isArray()) {
            proofs.forEach(proof -> {
                Instant proofExpiry = parseInstant(proof.path("expiresAt").asText(null));
                String environment = proof.path("environment").asText("").toLowerCase(Locale.ROOT);
                boolean passed = "APPLIED_VERIFIED".equals(proof.path("releaseStatus").asText(""))
                    && "PASSED".equals(proof.path("verificationStatus").asText(""));
                if (passed && proofExpiry != null && proofExpiry.isAfter(now)
                    && ("staging".equals(environment) || "production".equals(environment))) {
                    current.merge(environment, proofExpiry, (left, right) -> left.isAfter(right) ? left : right);
                }
            });
        }
        if (!current.containsKey("staging") || !current.containsKey("production")) {
            return Optional.empty();
        }
        return Optional.of(earlier(current.get("staging"), current.get("production")));
    }

    private Optional<Instant> latestCurrentHostedProofExpiry(JsonNode proofs, Instant now) {
        if (proofs == null || !proofs.isArray()) {
            return Optional.empty();
        }
        Instant latest = null;
        for (JsonNode proof : proofs) {
            Instant expiry = parseInstant(proof.path("expiresAt").asText(null));
            boolean passed = "APPLIED_VERIFIED".equals(proof.path("releaseStatus").asText(""))
                && "PASSED".equals(proof.path("verificationStatus").asText(""));
            if (passed && expiry != null && expiry.isAfter(now) && (latest == null || expiry.isAfter(latest))) {
                latest = expiry;
            }
        }
        return Optional.ofNullable(latest);
    }

    private Instant earlier(Instant left, Instant right) {
        if (left == null) {
            return right;
        }
        return left.isBefore(right) ? left : right;
    }

    private Instant parseInstant(String value) {
        try {
            return StringUtils.hasText(value) ? Instant.parse(value) : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void clearMarketApproval(DeploymentBehaviorReadinessEntity candidate) {
        candidate.setMaturity("HOSTED_PROVEN");
        candidate.setApprovalEvidenceJson("[]");
        candidate.setApprovedByActorId(null);
        candidate.setApprovedAt(null);
        candidate.setApprovalNote(null);
    }

    private ArrayNode requireApprovalEvidence(List<DeploymentBehaviorReadinessEvidenceInput> inputs) {
        Map<String, DeploymentBehaviorReadinessEvidenceInput> byArea = new LinkedHashMap<>();
        for (DeploymentBehaviorReadinessEvidenceInput input : inputs) {
            String area = normalizeCode(input.area());
            if (!REQUIRED_APPROVAL_AREAS.contains(area)) {
                throw new ResponseStatusException(BAD_REQUEST, "Unsupported market-readiness evidence area: " + area);
            }
            if (byArea.put(area, input) != null) {
                throw new ResponseStatusException(BAD_REQUEST, "Duplicate market-readiness evidence area: " + area);
            }
            if (!"PASSED".equals(normalizeCode(input.status()))) {
                throw new ResponseStatusException(CONFLICT, "Every market-readiness evidence area must be PASSED: " + area);
            }
        }
        if (!byArea.keySet().equals(REQUIRED_APPROVAL_AREAS)) {
            Set<String> missing = new LinkedHashSet<>(REQUIRED_APPROVAL_AREAS);
            missing.removeAll(byArea.keySet());
            throw new ResponseStatusException(BAD_REQUEST, "Missing market-readiness evidence areas: " + String.join(", ", missing));
        }
        ArrayNode result = objectMapper.createArrayNode();
        byArea.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            DeploymentBehaviorReadinessEvidenceInput input = entry.getValue();
            ObjectNode item = result.addObject();
            item.put("area", entry.getKey());
            item.put("status", "PASSED");
            item.put("evidenceRef", input.evidenceRef().trim());
            item.put("summary", input.summary().trim());
        });
        return result;
    }

    private Instant requireExpiry(Instant requested, Instant now, Duration defaultTtl) {
        Instant value = requested == null ? now.plus(defaultTtl) : requested;
        if (!value.isAfter(now) || value.isAfter(now.plus(MAX_EVIDENCE_TTL))) {
            throw new ResponseStatusException(BAD_REQUEST, "expiresAt must be in the future and no more than 180 days away.");
        }
        return value;
    }

    private DeploymentBehaviorReadinessEntity requireCandidate(String candidateId) {
        return readinessRepository.findById(candidateId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Behavior readiness candidate not found: " + candidateId));
    }

    private DeploymentBehaviorReadinessSummary toSummary(DeploymentBehaviorReadinessEntity entity) {
        boolean expired = !entity.getExpiresAt().isAfter(Instant.now());
        boolean operatorView = isPlatformOperatorView();
        return new DeploymentBehaviorReadinessSummary(
            entity.getId(),
            SCHEMA_VERSION,
            entity.getBehaviorType(),
            entity.getTemplatePluginId(),
            entity.getTemplatePluginVersionId(),
            entity.getTemplatePluginVersion(),
            entity.getCompositionHash(),
            entity.getMaterialHash(),
            entity.getFrameworkVersion(),
            operatorView ? entity.getSourceArtifactId() : null,
            operatorView ? entity.getSourceCommit() : null,
            operatorView ? entity.getImageDigest() : null,
            operatorView ? entity.getSourceCapabilityManifestHash() : null,
            stringList(readJson(entity.getVerificationPackIdsJson(), objectMapper.createArrayNode())),
            entity.getMaturity(),
            effectiveMaturity(entity),
            entity.getStatus(),
            expired,
            safeHostedProofs(entity.getHostedProofsJson(), operatorView),
            safeApprovalEvidence(entity.getApprovalEvidenceJson(), operatorView),
            operatorView ? entity.getDeploymentId() : null,
            operatorView ? entity.getDeploymentVersionId() : null,
            operatorView ? entity.getReleaseId() : null,
            entity.getEvaluatedAt(),
            entity.getExpiresAt(),
            operatorView ? entity.getApprovedByActorId() : null,
            entity.getApprovedAt(),
            operatorView ? entity.getApprovalNote() : null,
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    private boolean isPlatformOperatorView() {
        PlatformPrincipal principal = PlatformSecurityContext.currentPrincipal();
        return principal != null
            && (principal.role() == PlatformRole.PLATFORM_ADMIN
                || principal.role() == PlatformRole.PLATFORM_OPERATOR);
    }

    private JsonNode safeHostedProofs(String value, boolean operatorView) {
        JsonNode proofs = readJson(value, objectMapper.createArrayNode());
        if (operatorView || !proofs.isArray()) {
            return proofs;
        }
        ArrayNode result = objectMapper.createArrayNode();
        proofs.forEach(proof -> {
            ObjectNode safe = result.addObject();
            safe.put("environment", proof.path("environment").asText(""));
            safe.put("releaseStatus", proof.path("releaseStatus").asText(""));
            safe.put("verificationStatus", proof.path("verificationStatus").asText(""));
            ArrayNode behaviorProofs = safe.putArray("behaviorProofs");
            JsonNode rawBehaviorProofs = proof.path("behaviorProofs");
            if (rawBehaviorProofs.isArray()) {
                rawBehaviorProofs.forEach(raw -> {
                    ObjectNode behaviorProof = behaviorProofs.addObject();
                    behaviorProof.put("verificationPackId", raw.path("verificationPackId").asText(""));
                    behaviorProof.put("status", raw.path("status").asText(""));
                    behaviorProof.set("passedChecks", raw.path("passedChecks").deepCopy());
                });
            }
            safe.put("verifiedAt", proof.path("verifiedAt").asText(""));
            safe.put("expiresAt", proof.path("expiresAt").asText(""));
        });
        return result;
    }

    private JsonNode safeApprovalEvidence(String value, boolean operatorView) {
        JsonNode evidence = readJson(value, objectMapper.createArrayNode());
        if (operatorView || !evidence.isArray()) {
            return evidence;
        }
        ArrayNode result = objectMapper.createArrayNode();
        evidence.forEach(item -> {
            ObjectNode safe = result.addObject();
            safe.put("area", item.path("area").asText(""));
            safe.put("status", item.path("status").asText(""));
            safe.put("summary", item.path("summary").asText(""));
        });
        return result;
    }

    private String effectiveMaturity(DeploymentBehaviorReadinessEntity entity) {
        if (!"ACTIVE".equals(entity.getStatus()) || !entity.getExpiresAt().isAfter(Instant.now())) {
            return "PLATFORM_SELECTABLE";
        }
        return normalizeMaturity(entity.getMaturity());
    }

    private int maturityRank(String maturity) {
        int index = MATURITY_ORDER.indexOf(normalizeMaturity(maturity));
        return index < 0 ? 0 : index;
    }

    private String normalizeMaturity(String maturity) {
        String normalized = normalizeCode(maturity);
        return MATURITY_ORDER.contains(normalized) ? normalized : "FRAMEWORK_AVAILABLE";
    }

    private String normalizedEnvironment(String profileEnvironment, String deploymentEnvironment) {
        String value = firstText(profileEnvironment, deploymentEnvironment).toLowerCase(Locale.ROOT);
        if (value.contains("prod") && !value.contains("staging")) {
            return "production";
        }
        if (value.contains("stag")) {
            return "staging";
        }
        return value;
    }

    private String normalizeCode(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private String requireText(String value, String label) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(CONFLICT, "Readiness evidence is missing " + label + ".");
        }
        return value.trim();
    }

    private String requireHash(String value, String label) {
        String normalized = requireText(value, label);
        if (!normalized.matches("(?:sha256:)?[0-9a-f]{64}")) {
            throw new ResponseStatusException(CONFLICT, label + " must be an immutable SHA-256 hash.");
        }
        return normalized;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        throw new ResponseStatusException(CONFLICT, "Readiness evidence is missing environment identity.");
    }

    private List<String> stringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(value -> {
            if (StringUtils.hasText(value.asText(null))) {
                values.add(value.asText().trim());
            }
        });
        return List.copyOf(values);
    }

    private JsonNode readObject(String value, String label) {
        JsonNode node = readJson(value, null);
        if (node == null || !node.isObject()) {
            throw new ResponseStatusException(CONFLICT, "Readiness " + label + " must be a JSON object.");
        }
        return node;
    }

    private JsonNode readJson(String value, JsonNode fallback) {
        try {
            if (!StringUtils.hasText(value)) {
                return fallback;
            }
            JsonNode node = objectMapper.readTree(value);
            return node == null ? fallback : node;
        } catch (Exception exception) {
            if (fallback != null) {
                return fallback;
            }
            throw new ResponseStatusException(CONFLICT, "Readiness evidence contains invalid JSON.");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to write behavior readiness evidence.", exception);
        }
    }

    private String canonicalJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(canonicalize(node));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to canonicalize behavior readiness evidence.", exception);
        }
    }

    private Object canonicalize(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
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
        if (node.isNumber()) {
            return node.numberValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        return node.asText();
    }

    private String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to hash behavior readiness evidence.", exception);
        }
    }

    private record TemplateOrigin(
        MarketplacePluginEntity plugin,
        MarketplacePluginVersionEntity version,
        JsonNode manifest,
        String manifestHash
    ) {
    }
}
