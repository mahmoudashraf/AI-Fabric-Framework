package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.config.PlatformProvisioningProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentDraftEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVerificationRunEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.CreateDeploymentRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentDraftResponse;
import com.ai.fabric.platform.backend.deployment.model.DeploymentReleaseSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTemplateSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVerificationRunSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVersionSummary;
import com.ai.fabric.platform.backend.deployment.model.DraftValidationIssue;
import com.ai.fabric.platform.backend.deployment.model.DraftValidationResponse;
import com.ai.fabric.platform.backend.deployment.model.RailwayProvisioningPlanSummary;
import com.ai.fabric.platform.backend.deployment.model.UpdateDeploymentDraftRequest;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentDraftRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVerificationRunRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class DeploymentService {

    private final DeploymentRepository deploymentRepository;
    private final DeploymentDraftRepository draftRepository;
    private final DeploymentVersionRepository versionRepository;
    private final DeploymentReleaseRepository releaseRepository;
    private final DeploymentVerificationRunRepository verificationRunRepository;
    private final DeploymentConfigCompiler deploymentConfigCompiler;
    private final DeploymentDraftValidationService deploymentDraftValidationService;
    private final DeploymentProvisioningService deploymentProvisioningService;
    private final RailwayProvisioningPlanService railwayProvisioningPlanService;
    private final DeploymentReleaseVerificationService deploymentReleaseVerificationService;
    private final PlatformProvisioningProperties provisioningProperties;
    private final ObjectMapper objectMapper;

    private final List<DeploymentTemplateSummary> templates = List.of(
        new DeploymentTemplateSummary(
            "dev-openai-lucene",
            "Dev / OpenAI / Lucene",
            "Fast bootstrap template for local or Railway dev deployments.",
            "openai",
            "lucene",
            "runtime-dev",
            "connector-hosted"
        ),
        new DeploymentTemplateSummary(
            "dev-openai-qdrant",
            "Dev / OpenAI / Qdrant",
            "Managed-index dev template for testing external vector database flows.",
            "openai",
            "qdrant",
            "runtime-dev",
            "connector-hosted"
        ),
        new DeploymentTemplateSummary(
            "dev-anthropic-lucene",
            "Dev / Anthropic / Lucene",
            "Variant template to validate provider flexibility while keeping deployment simple.",
            "anthropic",
            "lucene",
            "runtime-dev",
            "connector-hosted"
        )
    );

    public DeploymentService(DeploymentRepository deploymentRepository,
                             DeploymentDraftRepository draftRepository,
                             DeploymentVersionRepository versionRepository,
                             DeploymentReleaseRepository releaseRepository,
                             DeploymentVerificationRunRepository verificationRunRepository,
                             DeploymentConfigCompiler deploymentConfigCompiler,
                             DeploymentDraftValidationService deploymentDraftValidationService,
                             DeploymentProvisioningService deploymentProvisioningService,
                             RailwayProvisioningPlanService railwayProvisioningPlanService,
                             DeploymentReleaseVerificationService deploymentReleaseVerificationService,
                             PlatformProvisioningProperties provisioningProperties,
                             ObjectMapper objectMapper) {
        this.deploymentRepository = deploymentRepository;
        this.draftRepository = draftRepository;
        this.versionRepository = versionRepository;
        this.releaseRepository = releaseRepository;
        this.verificationRunRepository = verificationRunRepository;
        this.deploymentConfigCompiler = deploymentConfigCompiler;
        this.deploymentDraftValidationService = deploymentDraftValidationService;
        this.deploymentProvisioningService = deploymentProvisioningService;
        this.railwayProvisioningPlanService = railwayProvisioningPlanService;
        this.deploymentReleaseVerificationService = deploymentReleaseVerificationService;
        this.provisioningProperties = provisioningProperties;
        this.objectMapper = objectMapper;
    }

    public List<DeploymentTemplateSummary> listTemplates() {
        return templates;
    }

    public List<DeploymentSummary> listDeployments() {
        return deploymentRepository.findAll().stream()
            .sorted(Comparator.comparing(DeploymentEntity::getCreatedAt).reversed())
            .map(this::toSummary)
            .toList();
    }

    @Transactional
    public DeploymentSummary createDeployment(CreateDeploymentRequest request) {
        DeploymentTemplateSummary template = templates.stream()
            .filter(item -> item.id().equals(request.templateId()))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Unknown templateId: " + request.templateId()));

        Instant now = Instant.now();

        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId(generateId("dep"));
        deployment.setName(request.name().trim());
        deployment.setEnvironmentName(request.environment().trim());
        deployment.setTemplateId(template.id());
        deployment.setStatus("DRAFT");
        deployment.setCreatedAt(now);
        deployment.setUpdatedAt(now);
        deploymentRepository.save(deployment);

        DeploymentDraftEntity draft = createInitialDraft(deployment, template, now);
        draftRepository.save(draft);

        deployment.setActiveDraftId(draft.getId());
        deployment.setUpdatedAt(now);
        deploymentRepository.save(deployment);

        return toSummary(deployment);
    }

    public DeploymentDraftResponse getActiveDraftForDeployment(String deploymentId) {
        DeploymentEntity deployment = getDeployment(deploymentId);
        String draftId = deployment.getActiveDraftId();
        DeploymentDraftEntity draft = draftId != null
            ? draftRepository.findById(draftId).orElseGet(() -> latestDraft(deploymentId))
            : latestDraft(deploymentId);
        return toDraftResponse(draft);
    }

    @Transactional
    public DeploymentDraftResponse updateDraft(String draftId, UpdateDeploymentDraftRequest request) {
        DeploymentDraftEntity draft = draftRepository.findById(draftId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Draft not found: " + draftId));

        if (request.actionsConfig() != null) {
            draft.setActionsConfigJson(writeJson(request.actionsConfig()));
        }
        if (request.entityConfig() != null) {
            draft.setEntityConfigJson(writeJson(request.entityConfig()));
        }
        if (request.routingConfig() != null) {
            draft.setRoutingConfigJson(writeJson(request.routingConfig()));
        }
        if (request.providerConfig() != null) {
            draft.setProviderConfigJson(writeJson(request.providerConfig()));
        }
        if (request.securityConfig() != null) {
            draft.setSecurityConfigJson(writeJson(request.securityConfig()));
        }

        draft.setStatus("MODIFIED");
        draft.setUpdatedAt(Instant.now());
        draftRepository.save(draft);

        DeploymentEntity deployment = getDeployment(draft.getDeploymentId());
        deployment.setStatus("DRAFT");
        deployment.setUpdatedAt(draft.getUpdatedAt());
        deploymentRepository.save(deployment);

        return toDraftResponse(draft);
    }

    public DraftValidationResponse validateDraft(String draftId) {
        DeploymentDraftEntity draft = draftRepository.findById(draftId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Draft not found: " + draftId));
        return deploymentDraftValidationService.validate(draft);
    }

    @Transactional
    public DeploymentVersionSummary publishDraft(String draftId) {
        DeploymentDraftEntity draft = draftRepository.findById(draftId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Draft not found: " + draftId));
        DeploymentEntity deployment = getDeployment(draft.getDeploymentId());
        Instant now = Instant.now();
        DraftValidationResponse validation = deploymentDraftValidationService.validate(draft);
        if (!validation.publishReady()) {
            String message = validation.issues().stream()
                .filter(issue -> "ERROR".equals(issue.severity()))
                .map(DraftValidationIssue::message)
                .limit(3)
                .reduce((left, right) -> left + " | " + right)
                .orElse("Draft validation failed.");
            throw new ResponseStatusException(BAD_REQUEST, message);
        }

        long nextVersion = versionRepository.countByDeploymentId(deployment.getId()) + 1;
        String versionId = generateId("ver");
        String versionLabel = "v" + nextVersion;

        DeploymentVersionEntity activeVersion = deployment.getActiveVersionId() != null
            ? versionRepository.findById(deployment.getActiveVersionId()).orElse(null)
            : null;
        boolean reindexRequired = deploymentConfigCompiler.requiresReindex(draft, activeVersion);
        DeploymentConfigCompiler.CompiledDeploymentVersion compiled = deploymentConfigCompiler.compile(
            deployment,
            draft,
            versionId,
            versionLabel,
            reindexRequired
        );

        DeploymentVersionEntity version = new DeploymentVersionEntity();
        version.setId(versionId);
        version.setDeploymentId(deployment.getId());
        version.setSourceDraftId(draft.getId());
        version.setVersionLabel(versionLabel);
        version.setStatus("PUBLISHED");
        version.setConfigHash(compiled.configHash());
        version.setReindexRequired(reindexRequired);
        version.setActionsConfigJson(draft.getActionsConfigJson());
        version.setEntityConfigJson(draft.getEntityConfigJson());
        version.setRoutingConfigJson(draft.getRoutingConfigJson());
        version.setProviderConfigJson(draft.getProviderConfigJson());
        version.setSecurityConfigJson(draft.getSecurityConfigJson());
        version.setActionsArtifactYaml(compiled.actionsArtifactYaml());
        version.setEntityArtifactYaml(compiled.entityArtifactYaml());
        version.setRoutingArtifactYaml(compiled.routingArtifactYaml());
        version.setManifestJson(compiled.manifestJson());
        version.setPublishedAt(now);
        versionRepository.save(version);

        draft.setStatus("PUBLISHED");
        draft.setUpdatedAt(now);
        draftRepository.save(draft);

        DeploymentDraftEntity nextDraft = new DeploymentDraftEntity();
        nextDraft.setId(generateId("drf"));
        nextDraft.setDeploymentId(deployment.getId());
        nextDraft.setRevisionNumber(draft.getRevisionNumber() + 1);
        nextDraft.setStatus("DRAFT");
        nextDraft.setActionsConfigJson(draft.getActionsConfigJson());
        nextDraft.setEntityConfigJson(draft.getEntityConfigJson());
        nextDraft.setRoutingConfigJson(draft.getRoutingConfigJson());
        nextDraft.setProviderConfigJson(draft.getProviderConfigJson());
        nextDraft.setSecurityConfigJson(draft.getSecurityConfigJson());
        nextDraft.setCreatedAt(now);
        nextDraft.setUpdatedAt(now);
        draftRepository.save(nextDraft);

        deployment.setActiveDraftId(nextDraft.getId());
        deployment.setStatus("VERSION_PUBLISHED");
        deployment.setUpdatedAt(now);
        deploymentRepository.save(deployment);

        return toVersionSummary(version);
    }

    public List<DeploymentVersionSummary> listVersions(String deploymentId) {
        getDeployment(deploymentId);
        return versionRepository.findByDeploymentIdOrderByPublishedAtDesc(deploymentId).stream()
            .map(this::toVersionSummary)
            .toList();
    }

    @Transactional
    public DeploymentReleaseSummary applyVersion(String deploymentId, String versionId) {
        DeploymentEntity deployment = getDeployment(deploymentId);
        DeploymentVersionEntity version = versionRepository.findById(versionId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Version not found: " + versionId));
        if (!deployment.getId().equals(version.getDeploymentId())) {
            throw new ResponseStatusException(BAD_REQUEST, "Version does not belong to deployment: " + deploymentId);
        }

        Instant now = Instant.now();

        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setId(generateId("rel"));
        release.setDeploymentId(deploymentId);
        release.setDeploymentVersionId(versionId);
        release.setStatus("PROVISIONING");
        release.setVerificationStatus("PENDING");
        release.setProvisioningStatus("PENDING");
        release.setProvisioningTarget(deploymentProvisioningService.selectedTarget());
        release.setProvisioningDetailsJson("{}");
        release.setCreatedAt(now);
        release.setAppliedAt(now);
        releaseRepository.save(release);

        ProvisioningResult provisioningResult = deploymentProvisioningService.provision(
            deployment,
            version,
            release
        );

        release.setProvisioningStatus(provisioningResult.status());
        release.setProvisioningTarget(provisioningResult.target());
        release.setProvisioningDetailsJson(provisioningResult.detailsJson());

        deployment.setActiveVersionId(versionId);
        deployment.setRuntimeBaseUrl(provisioningResult.runtimeBaseUrl());
        deployment.setConnectorBaseUrl(provisioningResult.connectorBaseUrl());
        deployment.setStatus("APPLIED_PENDING_VERIFICATION");
        deployment.setUpdatedAt(now);
        deploymentRepository.save(deployment);

        runVerification(deployment, version, release, "POST_APPLY");
        return toReleaseSummary(releaseRepository.save(release));
    }

    public List<DeploymentReleaseSummary> listReleases(String deploymentId) {
        getDeployment(deploymentId);
        return releaseRepository.findByDeploymentIdOrderByCreatedAtDesc(deploymentId).stream()
            .map(this::toReleaseSummary)
            .toList();
    }

    public List<DeploymentVerificationRunSummary> listVerificationRuns(String deploymentId) {
        getDeployment(deploymentId);
        return verificationRunRepository.findByDeploymentIdOrderByCreatedAtDesc(deploymentId).stream()
            .map(this::toVerificationRunSummary)
            .toList();
    }

    public RailwayProvisioningPlanSummary previewRailwayPlan(String deploymentId, String versionId) {
        DeploymentEntity deployment = getDeployment(deploymentId);
        DeploymentVersionEntity version = versionRepository.findById(versionId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Version not found: " + versionId));
        if (!deploymentId.equals(version.getDeploymentId())) {
            throw new ResponseStatusException(BAD_REQUEST, "Version does not belong to deployment: " + deploymentId);
        }
        return railwayProvisioningPlanService.buildPlan(deployment, version);
    }

    @Transactional
    public DeploymentVerificationRunSummary rerunVerification(String deploymentId) {
        DeploymentEntity deployment = getDeployment(deploymentId);
        if (deployment.getActiveVersionId() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Deployment has no active version to verify.");
        }

        DeploymentVersionEntity version = versionRepository.findById(deployment.getActiveVersionId())
            .orElseThrow(() -> new ResponseStatusException(
                NOT_FOUND,
                "Active version not found: " + deployment.getActiveVersionId()
            ));
        DeploymentReleaseEntity release = releaseRepository
            .findTopByDeploymentIdAndDeploymentVersionIdOrderByCreatedAtDesc(deploymentId, version.getId())
            .orElseThrow(() -> new ResponseStatusException(
                NOT_FOUND,
                "No applied release found for active version: " + version.getVersionLabel()
            ));

        DeploymentVerificationRunEntity verificationRun = runVerification(
            deployment,
            version,
            release,
            "MANUAL_RERUN"
        );
        return toVerificationRunSummary(verificationRun);
    }

    @Transactional
    public void ensureBootstrapSample() {
        if (deploymentRepository.count() > 0) {
            return;
        }

        DeploymentSummary bootstrap = createDeployment(new CreateDeploymentRequest(
            "Sample Commerce Dev",
            "dev",
            "dev-openai-lucene"
        ));
        DeploymentDraftResponse draft = getActiveDraftForDeployment(bootstrap.id());
        DeploymentVersionSummary version = publishDraft(draft.id());
        if ("RAILWAY_STUB".equalsIgnoreCase(provisioningProperties.mode())) {
            applyVersion(bootstrap.id(), version.id());
        }
    }

    private DeploymentEntity getDeployment(String deploymentId) {
        return deploymentRepository.findById(deploymentId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment not found: " + deploymentId));
    }

    private DeploymentDraftEntity latestDraft(String deploymentId) {
        return draftRepository.findTopByDeploymentIdOrderByRevisionNumberDesc(deploymentId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "No draft found for deployment: " + deploymentId));
    }

    private DeploymentDraftEntity createInitialDraft(DeploymentEntity deployment,
                                                     DeploymentTemplateSummary template,
                                                     Instant now) {
        DeploymentDraftEntity draft = new DeploymentDraftEntity();
        draft.setId(generateId("drf"));
        draft.setDeploymentId(deployment.getId());
        draft.setRevisionNumber(1);
        draft.setStatus("DRAFT");
        draft.setActionsConfigJson(writeJson(defaultActionsConfig()));
        draft.setEntityConfigJson(writeJson(defaultEntityConfig()));
        draft.setRoutingConfigJson(writeJson(defaultRoutingConfig()));
        draft.setProviderConfigJson(writeJson(defaultProviderConfig(template)));
        draft.setSecurityConfigJson(writeJson(defaultSecurityConfig()));
        draft.setCreatedAt(now);
        draft.setUpdatedAt(now);
        return draft;
    }

    private JsonNode defaultActionsConfig() {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode actions = objectMapper.createArrayNode();
        root.set("actions", actions);
        return root;
    }

    private JsonNode defaultEntityConfig() {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode aiConfig = root.putObject("ai-config");
        aiConfig.put("vector-dimensions", 512);
        root.set("ai-entities", objectMapper.createObjectNode());
        return root;
    }

    private JsonNode defaultRoutingConfig() {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode connector = root.putObject("connector");
        ObjectNode inboundAuth = connector.putObject("inbound-auth");
        inboundAuth.put("allow-unauthenticated", false);
        ObjectNode apiKey = inboundAuth.putObject("api-key");
        apiKey.put("enabled", true);
        apiKey.put("header", "X-AIFABRIC-API-KEY");
        apiKey.put("value", "${CONNECTOR_API_KEY}");
        ObjectNode upstream = connector.putObject("upstream");
        upstream.put("base-url", "https://customer-api.example");
        root.putObject("actions");
        return root;
    }

    private JsonNode defaultProviderConfig(DeploymentTemplateSummary template) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("llmProvider", template.llmProvider());
        root.put("embeddingProvider", template.llmProvider());
        root.put("vectorStrategy", template.vectorStrategy());
        root.put("runtimeProfile", template.runtimeProfile());
        root.put("connectorProfile", template.connectorProfile());
        return root;
    }

    private JsonNode defaultSecurityConfig() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("authzMode", "REMOTE_HTTP");
        root.put("adminApiKeyEnabled", true);
        root.put("connectorApiKeyEnabled", true);
        return root;
    }

    private String generateId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String writeJson(JsonNode node) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize config", ex);
        }
    }

    private DeploymentVerificationRunEntity runVerification(DeploymentEntity deployment,
                                                            DeploymentVersionEntity version,
                                                            DeploymentReleaseEntity release,
                                                            String verificationType) {
        DeploymentVerificationRunEntity verificationRun = deploymentReleaseVerificationService.verify(
            deployment,
            version,
            release,
            verificationType
        );
        verificationRunRepository.save(verificationRun);

        release.setVerificationRunId(verificationRun.getId());
        release.setVerificationStatus(verificationRun.getStatus());
        release.setStatus("PASSED".equals(verificationRun.getStatus())
            ? "APPLIED_VERIFIED"
            : "APPLIED_VERIFICATION_FAILED");
        releaseRepository.save(release);

        deployment.setStatus("PASSED".equals(verificationRun.getStatus()) ? "ACTIVE" : "VERIFICATION_FAILED");
        deployment.setUpdatedAt(Instant.now());
        deploymentRepository.save(deployment);
        return verificationRun;
    }

    private DeploymentSummary toSummary(DeploymentEntity deployment) {
        String activeVersion = null;
        if (deployment.getActiveVersionId() != null) {
            activeVersion = versionRepository.findById(deployment.getActiveVersionId())
                .map(DeploymentVersionEntity::getVersionLabel)
                .orElse(deployment.getActiveVersionId());
        } else if (deployment.getActiveDraftId() != null) {
            activeVersion = "draft";
        }

        return new DeploymentSummary(
            deployment.getId(),
            deployment.getName(),
            deployment.getEnvironmentName(),
            deployment.getTemplateId(),
            deployment.getStatus(),
            activeVersion,
            deployment.getRuntimeBaseUrl(),
            deployment.getConnectorBaseUrl(),
            deployment.getCreatedAt()
        );
    }

    private DeploymentDraftResponse toDraftResponse(DeploymentDraftEntity draft) {
        try {
            return new DeploymentDraftResponse(
                draft.getId(),
                draft.getDeploymentId(),
                draft.getRevisionNumber(),
                draft.getStatus(),
                objectMapper.readTree(draft.getActionsConfigJson()),
                objectMapper.readTree(draft.getEntityConfigJson()),
                objectMapper.readTree(draft.getRoutingConfigJson()),
                objectMapper.readTree(draft.getProviderConfigJson()),
                objectMapper.readTree(draft.getSecurityConfigJson()),
                draft.getCreatedAt(),
                draft.getUpdatedAt()
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read draft config", ex);
        }
    }

    private DeploymentVersionSummary toVersionSummary(DeploymentVersionEntity version) {
        return new DeploymentVersionSummary(
            version.getId(),
            version.getDeploymentId(),
            version.getSourceDraftId(),
            version.getVersionLabel(),
            version.getStatus(),
            version.getConfigHash(),
            version.isReindexRequired(),
            version.getPublishedAt()
        );
    }

    private DeploymentReleaseSummary toReleaseSummary(DeploymentReleaseEntity release) {
        try {
            return new DeploymentReleaseSummary(
                release.getId(),
                release.getDeploymentId(),
                release.getDeploymentVersionId(),
                release.getStatus(),
                release.getVerificationStatus(),
                release.getProvisioningStatus(),
                release.getProvisioningTarget(),
                release.getVerificationRunId(),
                objectMapper.readTree(release.getProvisioningDetailsJson()),
                release.getCreatedAt(),
                release.getAppliedAt()
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read provisioning details", ex);
        }
    }

    private DeploymentVerificationRunSummary toVerificationRunSummary(DeploymentVerificationRunEntity verificationRun) {
        try {
            return new DeploymentVerificationRunSummary(
                verificationRun.getId(),
                verificationRun.getDeploymentId(),
                verificationRun.getReleaseId(),
                verificationRun.getDeploymentVersionId(),
                verificationRun.getVerificationType(),
                verificationRun.getStatus(),
                verificationRun.getSummaryMessage(),
                objectMapper.readTree(verificationRun.getChecksJson()),
                verificationRun.getCreatedAt(),
                verificationRun.getCompletedAt()
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read verification checks", ex);
        }
    }
}
