package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.PlatformProvisioningProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentDraftEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentPromptRevisionEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVerificationRunEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.CreateDeploymentPromptRevisionRequest;
import com.ai.fabric.platform.backend.deployment.model.CreateDeploymentRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentDraftResponse;
import com.ai.fabric.platform.backend.deployment.model.DeploymentLifecycleSnapshotSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentOverviewSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPromptBaselineSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPromptRevisionSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentReleaseSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSourceSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTemplateSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVerificationRunSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVerificationSnapshotSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVersionSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentWorkspaceAccessSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentWorkspaceDraftSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentWorkspaceLifecycleSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentWorkspaceSummary;
import com.ai.fabric.platform.backend.deployment.model.DraftValidationIssue;
import com.ai.fabric.platform.backend.deployment.model.DraftValidationResponse;
import com.ai.fabric.platform.backend.deployment.model.RailwayProvisioningPlanSummary;
import com.ai.fabric.platform.backend.deployment.model.UpdateDeploymentSourceRequest;
import com.ai.fabric.platform.backend.deployment.model.UpdateDeploymentGuardrailsRequest;
import com.ai.fabric.platform.backend.deployment.model.UpdateDeploymentDraftRequest;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentDraftRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentPromptRevisionRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVerificationRunRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.repository.PublicApiDeploymentRepository;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class DeploymentService {

    private final DeploymentRepository deploymentRepository;
    private final DeploymentDraftRepository draftRepository;
    private final DeploymentPromptRevisionRepository promptRevisionRepository;
    private final DeploymentVersionRepository versionRepository;
    private final DeploymentReleaseRepository releaseRepository;
    private final DeploymentVerificationRunRepository verificationRunRepository;
    private final PublicApiDeploymentRepository publicApiDeploymentRepository;
    private final DeploymentConfigCompiler deploymentConfigCompiler;
    private final DeploymentDraftValidationService deploymentDraftValidationService;
    private final DeploymentProvisioningService deploymentProvisioningService;
    private final RailwayProvisioningPlanService railwayProvisioningPlanService;
    private final DeploymentReleaseVerificationService deploymentReleaseVerificationService;
    private final DeploymentReleaseExecutionService deploymentReleaseExecutionService;
    private final DeploymentSourceResolver deploymentSourceResolver;
    private final DeploymentAccessService deploymentAccessService;
    private final DeploymentAssignmentService deploymentAssignmentService;
    private final DeploymentOperationApprovalService deploymentOperationApprovalService;
    private final PlatformProvisioningProperties provisioningProperties;
    private final PlatformAuditService platformAuditService;
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
                             DeploymentPromptRevisionRepository promptRevisionRepository,
                             DeploymentVersionRepository versionRepository,
                             DeploymentReleaseRepository releaseRepository,
                             DeploymentVerificationRunRepository verificationRunRepository,
                             PublicApiDeploymentRepository publicApiDeploymentRepository,
                             DeploymentConfigCompiler deploymentConfigCompiler,
                             DeploymentDraftValidationService deploymentDraftValidationService,
                             DeploymentProvisioningService deploymentProvisioningService,
                             RailwayProvisioningPlanService railwayProvisioningPlanService,
                             DeploymentReleaseVerificationService deploymentReleaseVerificationService,
                             DeploymentReleaseExecutionService deploymentReleaseExecutionService,
                             DeploymentSourceResolver deploymentSourceResolver,
                             DeploymentAccessService deploymentAccessService,
                             DeploymentAssignmentService deploymentAssignmentService,
                             DeploymentOperationApprovalService deploymentOperationApprovalService,
                             PlatformProvisioningProperties provisioningProperties,
                             PlatformAuditService platformAuditService,
                             ObjectMapper objectMapper) {
        this.deploymentRepository = deploymentRepository;
        this.draftRepository = draftRepository;
        this.promptRevisionRepository = promptRevisionRepository;
        this.versionRepository = versionRepository;
        this.releaseRepository = releaseRepository;
        this.verificationRunRepository = verificationRunRepository;
        this.publicApiDeploymentRepository = publicApiDeploymentRepository;
        this.deploymentConfigCompiler = deploymentConfigCompiler;
        this.deploymentDraftValidationService = deploymentDraftValidationService;
        this.deploymentProvisioningService = deploymentProvisioningService;
        this.railwayProvisioningPlanService = railwayProvisioningPlanService;
        this.deploymentReleaseVerificationService = deploymentReleaseVerificationService;
        this.deploymentReleaseExecutionService = deploymentReleaseExecutionService;
        this.deploymentSourceResolver = deploymentSourceResolver;
        this.deploymentAccessService = deploymentAccessService;
        this.deploymentAssignmentService = deploymentAssignmentService;
        this.deploymentOperationApprovalService = deploymentOperationApprovalService;
        this.provisioningProperties = provisioningProperties;
        this.platformAuditService = platformAuditService;
        this.objectMapper = objectMapper;
    }

    public List<DeploymentTemplateSummary> listTemplates() {
        return templates;
    }

    public List<DeploymentSummary> listDeployments() {
        return listDeployments(false);
    }

    public List<DeploymentSummary> listDeployments(boolean includeArchived) {
        return selectDeployments(includeArchived).stream()
            .map(this::toSummary)
            .toList();
    }

    public List<DeploymentOverviewSummary> listDeploymentOverviews(boolean includeArchived) {
        return selectDeployments(includeArchived).stream()
            .map(this::toOverview)
            .toList();
    }

    public DeploymentOverviewSummary getDeploymentOverview(String deploymentId) {
        return toOverview(getDeployment(deploymentId));
    }

    public DeploymentWorkspaceSummary getDeploymentWorkspace(String deploymentId) {
        DeploymentEntity deployment = getDeployment(deploymentId);
        DeploymentDraftEntity draft = resolveActiveDraft(deployment);
        List<DeploymentVersionEntity> versions = versionRepository.findByDeploymentIdOrderByPublishedAtDesc(deploymentId);
        List<DeploymentReleaseEntity> releases = releaseRepository.findByDeploymentIdOrderByCreatedAtDesc(deploymentId);
        List<DeploymentVerificationRunEntity> verificationRuns = verificationRunRepository
            .findByDeploymentIdOrderByCreatedAtDesc(deploymentId);
        DeploymentWorkspaceAccessSummary access = deploymentAccessService.summarizeAccess(deployment);

        DeploymentVersionEntity latestVersion = versions.stream().findFirst().orElse(null);
        DeploymentVersionEntity liveVersion = deployment.getActiveVersionId() == null
            ? null
            : versionRepository.findById(deployment.getActiveVersionId()).orElse(null);
        DeploymentReleaseEntity latestRelease = releases.stream().findFirst().orElse(null);
        DeploymentReleaseEntity liveRelease = liveVersion == null
            ? null
            : releaseRepository.findTopByDeploymentIdAndDeploymentVersionIdOrderByCreatedAtDesc(
                deploymentId,
                liveVersion.getId()
            ).orElse(null);

        return new DeploymentWorkspaceSummary(
            toOverview(deployment),
            templateForId(deployment.getTemplateId()),
            access,
            toWorkspaceDraftSummary(draft),
            toWorkspaceLifecycleSummary(draft, latestVersion, liveVersion, latestRelease, liveRelease),
            latestVersion == null ? null : toVersionSummary(latestVersion),
            latestRelease == null ? null : toReleaseSummary(latestRelease),
            verificationRuns.stream().findFirst().map(this::toVerificationRunSummary).orElse(null),
            versions.size(),
            releases.size(),
            verificationRuns.size()
        );
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
        DeploymentDraftEntity draft = createInitialDraft(deployment, template, now);
        deployment.setActiveDraftId(draft.getId());
        deploymentRepository.save(deployment);
        draftRepository.save(draft);
        deploymentAssignmentService.grantCreatorAccessIfNeeded(deployment);
        platformAuditService.record(
            "DEPLOYMENT_CREATED",
            "DEPLOYMENT",
            deployment.getId(),
            java.util.Map.of(
                "templateId", template.id(),
                "environment", request.environment().trim(),
                "draftId", draft.getId()
            )
        );

        return toSummary(deployment);
    }

    @Transactional
    public DeploymentOverviewSummary archiveDeployment(String deploymentId) {
        DeploymentEntity deployment = getDeploymentForAdminAction(deploymentId);
        if (isArchived(deployment)) {
            return toOverview(deployment);
        }
        releaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc(deploymentId)
            .filter(this::isReleaseInProgress)
            .ifPresent(release -> {
                throw new ResponseStatusException(
                    CONFLICT,
                    "Deployment cannot be archived while apply is in progress: " + release.getId()
                );
            });

        Instant now = Instant.now();
        deployment.setStatus("ARCHIVED");
        deployment.setArchivedAt(now);
        deployment.setUpdatedAt(now);
        deploymentRepository.save(deployment);
        platformAuditService.record(
            "DEPLOYMENT_ARCHIVED",
            "DEPLOYMENT",
            deployment.getId(),
            java.util.Map.of(
                "environment", deployment.getEnvironmentName(),
                "templateId", deployment.getTemplateId(),
                "archivedAt", now.toString()
            )
        );
        return toOverview(deployment);
    }

    @Transactional
    public DeploymentOverviewSummary restoreDeployment(String deploymentId) {
        DeploymentEntity deployment = getDeploymentForAdminAction(deploymentId);
        if (!isArchived(deployment)) {
            return toOverview(deployment);
        }
        releaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc(deploymentId)
            .filter(this::isReleaseInProgress)
            .ifPresent(release -> {
                throw new ResponseStatusException(
                    CONFLICT,
                    "Deployment cannot be restored while apply is in progress: " + release.getId()
                );
            });

        String restoredStatus = determineRestoredStatus(
            deployment,
            releaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc(deploymentId).orElse(null)
        );
        deployment.setStatus(restoredStatus);
        deployment.setArchivedAt(null);
        deployment.setUpdatedAt(Instant.now());
        deploymentRepository.save(deployment);
        platformAuditService.record(
            "DEPLOYMENT_RESTORED",
            "DEPLOYMENT",
            deployment.getId(),
            java.util.Map.of(
                "environment", deployment.getEnvironmentName(),
                "templateId", deployment.getTemplateId(),
                "restoredStatus", restoredStatus
            )
        );
        return toOverview(deployment);
    }

    @Transactional
    public void deleteDeployment(String deploymentId) {
        deleteDeployment(deploymentId, null);
    }

    @Transactional
    public void deleteDeployment(String deploymentId, String approvalId) {
        DeploymentEntity deployment = getDeploymentForAdminAction(deploymentId);
        deploymentOperationApprovalService.consumeApprovedRequestIfRequired(
            deployment,
            DeploymentOperationApprovalService.DELETE_DEPLOYMENT,
            null,
            deployment.isApprovalRequiredForDelete(),
            approvalId
        );
        if (!isArchived(deployment)) {
            throw new ResponseStatusException(BAD_REQUEST, "Deployment must be archived before it can be deleted.");
        }
        releaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc(deploymentId)
            .filter(this::isReleaseInProgress)
            .ifPresent(release -> {
                throw new ResponseStatusException(
                    CONFLICT,
                    "Deployment cannot be deleted while apply is in progress: " + release.getId()
                );
            });

        verificationRunRepository.deleteByDeploymentId(deploymentId);
        releaseRepository.deleteByDeploymentId(deploymentId);
        versionRepository.deleteByDeploymentId(deploymentId);
        draftRepository.deleteByDeploymentId(deploymentId);
        promptRevisionRepository.deleteByDeploymentId(deploymentId);
        deploymentAssignmentService.deleteAssignmentsForDeployment(deploymentId);
        publicApiDeploymentRepository.deleteByDeploymentId(deploymentId);
        deploymentRepository.delete(deployment);

        platformAuditService.record(
            "DEPLOYMENT_DELETED",
            "DEPLOYMENT",
            deploymentId,
            java.util.Map.of(
                "environment", deployment.getEnvironmentName(),
                "templateId", deployment.getTemplateId()
            )
        );
    }

    public DeploymentDraftResponse getActiveDraftForDeployment(String deploymentId) {
        DeploymentEntity deployment = getDeployment(deploymentId);
        assertNotArchived(deployment, "load draft");
        DeploymentDraftEntity draft = resolveActiveDraft(deployment);
        return toDraftResponse(draft);
    }

    public List<DeploymentPromptRevisionSummary> listPromptRevisions(String deploymentId) {
        DeploymentEntity deployment = getDeployment(deploymentId);
        assertNotArchived(deployment, "load prompt revisions");
        return promptRevisionRepository.findByDeploymentIdOrderByCreatedAtDesc(deployment.getId()).stream()
            .map(this::toPromptRevisionSummary)
            .toList();
    }

    @Transactional
    public DeploymentPromptRevisionSummary createPromptRevision(String deploymentId,
                                                               CreateDeploymentPromptRevisionRequest request) {
        DeploymentEntity deployment = getDeploymentForEditorAction(deploymentId);
        assertNotArchived(deployment, "create prompt revision");
        DeploymentDraftEntity draft = resolveActiveDraft(deployment);
        PlatformPrincipal principal = requirePrincipal();

        String revisionLabel = StringUtils.hasText(request.revisionLabel())
            ? request.revisionLabel().trim()
            : "Prompt revision " + draft.getRevisionNumber();
        String revisionSummary = StringUtils.hasText(request.revisionSummary()) ? request.revisionSummary().trim() : null;

        DeploymentPromptRevisionEntity revision = new DeploymentPromptRevisionEntity();
        revision.setId(generateId("prm"));
        revision.setDeploymentId(deployment.getId());
        revision.setSourceDraftId(draft.getId());
        revision.setRevisionLabel(revisionLabel);
        revision.setRevisionSummary(revisionSummary);
        revision.setPromptConfigJson(draft.getPromptConfigJson());
        revision.setCreatedByActorId(principal.actorId());
        revision.setCreatedByDisplayName(principal.displayName());
        revision.setCreatedAt(Instant.now());
        promptRevisionRepository.save(revision);

        platformAuditService.record(
            "DEPLOYMENT_PROMPT_REVISION_CREATED",
            "DEPLOYMENT_PROMPT_REVISION",
            revision.getId(),
            java.util.Map.of(
                "deploymentId", deployment.getId(),
                "sourceDraftId", draft.getId(),
                "revisionLabel", revisionLabel
            )
        );

        return toPromptRevisionSummary(revision);
    }

    @Transactional
    public DeploymentDraftResponse restorePromptRevision(String deploymentId, String revisionId) {
        DeploymentEntity deployment = getDeploymentForEditorAction(deploymentId);
        assertNotArchived(deployment, "restore prompt revision");
        DeploymentPromptRevisionEntity revision = promptRevisionRepository.findById(revisionId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Prompt revision not found: " + revisionId));
        if (!deployment.getId().equals(revision.getDeploymentId())) {
            throw new ResponseStatusException(BAD_REQUEST, "Prompt revision does not belong to deployment: " + deploymentId);
        }

        DeploymentDraftEntity draft = resolveActiveDraft(deployment);
        Instant now = Instant.now();
        draft.setPromptConfigJson(revision.getPromptConfigJson());
        draft.setStatus("MODIFIED");
        draft.setUpdatedAt(now);
        draftRepository.save(draft);

        deployment.setStatus("DRAFT");
        deployment.setUpdatedAt(now);
        deploymentRepository.save(deployment);

        platformAuditService.record(
            "DEPLOYMENT_PROMPT_REVISION_RESTORED",
            "DEPLOYMENT_PROMPT_REVISION",
            revision.getId(),
            java.util.Map.of(
                "deploymentId", deployment.getId(),
                "draftId", draft.getId(),
                "revisionLabel", revision.getRevisionLabel()
            )
        );

        return toDraftResponse(draft);
    }

    @Transactional
    public DeploymentOverviewSummary updateDeploymentGuardrails(String deploymentId,
                                                               UpdateDeploymentGuardrailsRequest request) {
        DeploymentEntity deployment = getDeploymentForAdminAction(deploymentId);
        deployment.setApprovalRequiredForApply(request.approvalRequiredForApply());
        deployment.setApprovalRequiredForDelete(request.approvalRequiredForDelete());
        deployment.setUpdatedAt(Instant.now());
        deploymentRepository.save(deployment);
        platformAuditService.record(
            "DEPLOYMENT_GUARDRAILS_UPDATED",
            "DEPLOYMENT",
            deployment.getId(),
            java.util.Map.of(
                "approvalRequiredForApply", request.approvalRequiredForApply(),
                "approvalRequiredForDelete", request.approvalRequiredForDelete()
            )
        );
        return toOverview(deployment);
    }

    @Transactional
    public DeploymentDraftResponse updateDraft(String draftId, UpdateDeploymentDraftRequest request) {
        DeploymentDraftEntity draft = draftRepository.findById(draftId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Draft not found: " + draftId));
        DeploymentEntity deployment = getDeploymentForEditorAction(draft.getDeploymentId());
        assertNotArchived(deployment, "update draft");

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
        if (request.promptConfig() != null) {
            draft.setPromptConfigJson(writeJson(request.promptConfig()));
        }

        draft.setStatus("MODIFIED");
        draft.setUpdatedAt(Instant.now());
        draftRepository.save(draft);

        deployment.setStatus("DRAFT");
        deployment.setUpdatedAt(draft.getUpdatedAt());
        deploymentRepository.save(deployment);
        platformAuditService.record(
            "DRAFT_UPDATED",
            "DEPLOYMENT_DRAFT",
            draft.getId(),
            java.util.Map.of(
                "deploymentId", draft.getDeploymentId(),
                "revisionNumber", draft.getRevisionNumber(),
                "status", draft.getStatus()
            )
        );

        return toDraftResponse(draft);
    }

    @Transactional
    public DeploymentOverviewSummary updateDeploymentSource(String deploymentId, UpdateDeploymentSourceRequest request) {
        DeploymentEntity deployment = getDeploymentForAdminAction(deploymentId);
        assertNotArchived(deployment, "update deployment source");
        releaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc(deploymentId)
            .filter(this::isReleaseInProgress)
            .ifPresent(release -> {
                throw new ResponseStatusException(
                    CONFLICT,
                    "Deployment source cannot be changed while apply is in progress: " + release.getId()
                );
            });

        String repositoryOverride = deploymentSourceResolver.normalizeOverride(request.repository());
        String branchOverride = deploymentSourceResolver.normalizeOverride(request.branch());
        String effectiveRepository = repositoryOverride != null ? repositoryOverride : provisioningProperties.repository();
        String effectiveBranch = branchOverride != null ? branchOverride : provisioningProperties.branch();
        deploymentSourceResolver.validateEffectiveSource(effectiveRepository, effectiveBranch);

        deployment.setSourceRepositoryOverride(repositoryOverride);
        deployment.setSourceBranchOverride(branchOverride);
        deployment.setUpdatedAt(Instant.now());
        deploymentRepository.save(deployment);
        platformAuditService.record(
            "DEPLOYMENT_SOURCE_UPDATED",
            "DEPLOYMENT",
            deployment.getId(),
            java.util.Map.of(
                "repositoryOverride", repositoryOverride == null ? "" : repositoryOverride,
                "branchOverride", branchOverride == null ? "" : branchOverride,
                "effectiveRepository", effectiveRepository,
                "effectiveBranch", effectiveBranch
            )
        );

        return toOverview(deployment);
    }

    public DraftValidationResponse validateDraft(String draftId) {
        DeploymentDraftEntity draft = draftRepository.findById(draftId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Draft not found: " + draftId));
        getDeploymentForEditorAction(draft.getDeploymentId());
        return deploymentDraftValidationService.validate(draft);
    }

    @Transactional
    public DeploymentVersionSummary publishDraft(String draftId) {
        return publishDraftInternal(draftId, false);
    }

    DeploymentVersionSummary publishDraftForPublicApi(String draftId) {
        return publishDraftInternal(draftId, true);
    }

    @Transactional
    DeploymentVersionSummary publishDraftInternal(String draftId, boolean skipAccessCheck) {
        DeploymentDraftEntity draft = draftRepository.findById(draftId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Draft not found: " + draftId));
        DeploymentEntity deployment = skipAccessCheck
            ? deploymentRepository.findById(draft.getDeploymentId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment not found: " + draft.getDeploymentId()))
            : getDeploymentForEditorAction(draft.getDeploymentId());
        assertNotArchived(deployment, "publish draft");
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
        version.setPromptConfigJson(draft.getPromptConfigJson());
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
        nextDraft.setPromptConfigJson(draft.getPromptConfigJson());
        nextDraft.setCreatedAt(now);
        nextDraft.setUpdatedAt(now);
        draftRepository.save(nextDraft);

        deployment.setActiveDraftId(nextDraft.getId());
        deployment.setStatus("VERSION_PUBLISHED");
        deployment.setUpdatedAt(now);
        deploymentRepository.save(deployment);
        platformAuditService.record(
            "DRAFT_PUBLISHED",
            "DEPLOYMENT_VERSION",
            version.getId(),
            java.util.Map.of(
                "deploymentId", deployment.getId(),
                "draftId", draft.getId(),
                "versionLabel", versionLabel,
                "reindexRequired", reindexRequired
            )
        );

        return toVersionSummary(version);
    }

    public List<DeploymentVersionSummary> listVersions(String deploymentId) {
        getDeployment(deploymentId);
        return versionRepository.findByDeploymentIdOrderByPublishedAtDesc(deploymentId).stream()
            .map(this::toVersionSummary)
            .toList();
    }

    public DeploymentVersionSummary latestVersion(String deploymentId) {
        getDeployment(deploymentId);
        return versionRepository.findByDeploymentIdOrderByPublishedAtDesc(deploymentId).stream()
            .findFirst()
            .map(this::toVersionSummary)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "No published version found for deployment: " + deploymentId));
    }

    public DeploymentPromptBaselineSummary getPromptBaseline(String deploymentId) {
        getDeployment(deploymentId);
        DeploymentVersionEntity version = versionRepository.findByDeploymentIdOrderByPublishedAtDesc(deploymentId).stream()
            .findFirst()
            .orElse(null);
        if (version == null) {
            return new DeploymentPromptBaselineSummary(
                deploymentId,
                null,
                null,
                null,
                0,
                objectMapper.createObjectNode()
            );
        }
        try {
            return new DeploymentPromptBaselineSummary(
                deploymentId,
                version.getId(),
                version.getVersionLabel(),
                version.getPublishedAt(),
                countPopulatedPrompts(version.getPromptConfigJson()),
                objectMapper.readTree(version.getPromptConfigJson())
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read published prompt config", ex);
        }
    }

    @Transactional
    public DeploymentReleaseSummary applyVersion(String deploymentId, String versionId) {
        return applyVersion(deploymentId, versionId, null);
    }

    @Transactional
    public DeploymentReleaseSummary applyVersion(String deploymentId, String versionId, String approvalId) {
        return applyVersionInternal(deploymentId, versionId, approvalId, false);
    }

    @Transactional
    DeploymentReleaseSummary applyVersionForPublicApi(String deploymentId, String versionId) {
        return applyVersionInternal(deploymentId, versionId, null, true);
    }

    @Transactional
    DeploymentReleaseSummary applyVersionInternal(String deploymentId,
                                                  String versionId,
                                                  String approvalId,
                                                  boolean skipAccessCheck) {
        DeploymentEntity deployment = deploymentRepository.findByIdForUpdate(deploymentId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment not found: " + deploymentId));
        if (!skipAccessCheck) {
            deploymentAccessService.requireDeploymentOperatorAccess(deployment);
        }
        assertNotArchived(deployment, "apply version");
        DeploymentVersionEntity version = versionRepository.findById(versionId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Version not found: " + versionId));
        if (!deployment.getId().equals(version.getDeploymentId())) {
            throw new ResponseStatusException(BAD_REQUEST, "Version does not belong to deployment: " + deploymentId);
        }
        deploymentOperationApprovalService.consumeApprovedRequestIfRequired(
            deployment,
            DeploymentOperationApprovalService.APPLY_VERSION,
            versionId,
            deployment.isApprovalRequiredForApply(),
            approvalId
        );
        releaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc(deploymentId)
            .filter(this::isReleaseInProgress)
            .ifPresent(release -> {
                throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    "Deployment already has an apply in progress: " + release.getId()
                );
            });

        Instant now = Instant.now();

        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setId(generateId("rel"));
        release.setDeploymentId(deploymentId);
        release.setDeploymentVersionId(versionId);
        release.setStatus("APPLY_REQUESTED");
        release.setVerificationStatus("PENDING");
        release.setProvisioningStatus("QUEUED");
        release.setProvisioningTarget(deploymentProvisioningService.selectedTarget());
        release.setCurrentStepKey("queue_release");
        release.setCurrentStepDescription("Apply request accepted and queued.");
        release.setErrorMessage(null);
        release.setProvisioningDetailsJson(initialReleaseDetails("queue_release", "Apply request accepted and queued."));
        release.setCreatedAt(now);
        release.setAppliedAt(now);
        release.setUpdatedAt(now);
        releaseRepository.save(release);

        deployment.setStatus("APPLY_REQUESTED");
        deployment.setUpdatedAt(now);
        deploymentRepository.save(deployment);
        platformAuditService.record(
            "VERSION_APPLY_REQUESTED",
            "DEPLOYMENT_RELEASE",
            release.getId(),
            java.util.Map.of(
                "deploymentId", deploymentId,
                "versionId", versionId,
                "provisioningTarget", release.getProvisioningTarget()
            )
        );

        scheduleApplyAfterCommit(deploymentId, versionId, release.getId());
        return toReleaseSummary(release);
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
        DeploymentEntity deployment = getDeploymentForOperatorAction(deploymentId);
        assertNotArchived(deployment, "rerun verification");
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
        platformAuditService.record(
            "VERIFICATION_RERUN_REQUESTED",
            "DEPLOYMENT_VERIFICATION_RUN",
            verificationRun.getId(),
            java.util.Map.of(
                "deploymentId", deploymentId,
                "releaseId", release.getId(),
                "versionId", version.getId()
            )
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
        DeploymentEntity deployment = deploymentRepository.findById(deploymentId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment not found: " + deploymentId));
        return deploymentAccessService.requireDeploymentAccess(deployment);
    }

    private DeploymentEntity getDeploymentForOperatorAction(String deploymentId) {
        DeploymentEntity deployment = deploymentRepository.findById(deploymentId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment not found: " + deploymentId));
        return deploymentAccessService.requireDeploymentOperatorAccess(deployment);
    }

    private DeploymentEntity getDeploymentForEditorAction(String deploymentId) {
        DeploymentEntity deployment = deploymentRepository.findById(deploymentId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment not found: " + deploymentId));
        return deploymentAccessService.requireDeploymentEditorAccess(deployment);
    }

    private DeploymentEntity getDeploymentForAdminAction(String deploymentId) {
        DeploymentEntity deployment = deploymentRepository.findById(deploymentId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment not found: " + deploymentId));
        return deploymentAccessService.requireDeploymentAdminAccess(deployment);
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
        draft.setPromptConfigJson(writeJson(defaultPromptConfig()));
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

    private JsonNode defaultPromptConfig() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("systemPrompt", "");
        root.put("intentExtractionPrompt", "");
        root.put("actionSelectionPrompt", "");
        root.put("clarificationPrompt", "");
        root.put("answerGenerationPrompt", "");
        root.put("retrievalPrompt", "");
        root.put("assistantUiPrompt", "");
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
        release.setCurrentStepKey("verification_complete");
        release.setCurrentStepDescription("Verification completed.");
        release.setErrorMessage(null);
        release.setUpdatedAt(Instant.now());
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

        DeploymentSourceSummary source = deploymentSourceResolver.summarize(deployment);

        return new DeploymentSummary(
            deployment.getId(),
            deployment.getName(),
            deployment.getEnvironmentName(),
            deployment.getTemplateId(),
            source,
            deployment.getStatus(),
            activeVersion,
            deployment.getRuntimeBaseUrl(),
            deployment.getConnectorBaseUrl(),
            deployment.isApprovalRequiredForApply(),
            deployment.isApprovalRequiredForDelete(),
            deployment.getCreatedAt()
        );
    }

    private DeploymentTemplateSummary templateForId(String templateId) {
        return templates.stream()
            .filter(item -> item.id().equals(templateId))
            .findFirst()
            .orElseGet(() -> new DeploymentTemplateSummary(
                templateId,
                templateId,
                "Template metadata unavailable.",
                "unknown",
                "unknown",
                "unknown",
                "unknown"
            ));
    }

    private DeploymentOverviewSummary toOverview(DeploymentEntity deployment) {
        String activeVersion = null;
        if (deployment.getActiveVersionId() != null) {
            activeVersion = versionRepository.findById(deployment.getActiveVersionId())
                .map(DeploymentVersionEntity::getVersionLabel)
                .orElse(deployment.getActiveVersionId());
        } else if (deployment.getActiveDraftId() != null) {
            activeVersion = "draft";
        }

        DeploymentSourceSummary source = deploymentSourceResolver.summarize(deployment);

        DeploymentReleaseEntity latestRelease = releaseRepository
            .findTopByDeploymentIdOrderByCreatedAtDesc(deployment.getId())
            .orElse(null);
        DeploymentVerificationRunEntity latestVerification = verificationRunRepository
            .findByDeploymentIdOrderByCreatedAtDesc(deployment.getId())
            .stream()
            .findFirst()
            .orElse(null);

        return new DeploymentOverviewSummary(
            deployment.getId(),
            deployment.getName(),
            deployment.getEnvironmentName(),
            deployment.getTemplateId(),
            source,
            deploymentAccessService.summarizeAccess(deployment),
            deployment.getStatus(),
            activeVersion,
            determineHealthStatus(deployment, latestRelease, latestVerification),
            determineHealthSummary(deployment, latestRelease, latestVerification),
            deployment.getRuntimeBaseUrl(),
            deployment.getConnectorBaseUrl(),
            deployment.isApprovalRequiredForApply(),
            deployment.isApprovalRequiredForDelete(),
            toLifecycleSnapshot(latestRelease),
            toVerificationSnapshot(latestVerification),
            deployment.getArchivedAt(),
            deployment.getCreatedAt(),
            deployment.getUpdatedAt()
        );
    }

    private DeploymentDraftEntity resolveActiveDraft(DeploymentEntity deployment) {
        String draftId = deployment.getActiveDraftId();
        return draftId != null
            ? draftRepository.findById(draftId).orElseGet(() -> latestDraft(deployment.getId()))
            : latestDraft(deployment.getId());
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
                objectMapper.readTree(draft.getPromptConfigJson()),
                draft.getCreatedAt(),
                draft.getUpdatedAt()
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read draft config", ex);
        }
    }

    private DeploymentWorkspaceDraftSummary toWorkspaceDraftSummary(DeploymentDraftEntity draft) {
        return new DeploymentWorkspaceDraftSummary(
            draft.getId(),
            draft.getRevisionNumber(),
            draft.getStatus(),
            draft.getUpdatedAt()
        );
    }

    private DeploymentWorkspaceLifecycleSummary toWorkspaceLifecycleSummary(DeploymentDraftEntity draft,
                                                                           DeploymentVersionEntity latestVersion,
                                                                           DeploymentVersionEntity liveVersion,
                                                                           DeploymentReleaseEntity latestRelease,
                                                                           DeploymentReleaseEntity liveRelease) {
        boolean hasPublishedVersion = latestVersion != null;
        boolean hasLiveVersion = liveVersion != null;
        boolean savedDraftMatchesLatestPublished = hasPublishedVersion && draftMatchesVersion(draft, latestVersion);
        boolean liveMatchesLatestPublished = hasPublishedVersion
            && hasLiveVersion
            && latestVersion.getId().equals(liveVersion.getId());

        String savedDraftState = determineSavedDraftState(hasPublishedVersion, savedDraftMatchesLatestPublished);
        String liveState = determineLiveState(latestVersion, liveVersion, latestRelease);

        return new DeploymentWorkspaceLifecycleSummary(
            savedDraftState,
            liveState,
            hasPublishedVersion,
            hasLiveVersion,
            savedDraftMatchesLatestPublished,
            liveMatchesLatestPublished,
            latestVersion == null ? null : latestVersion.getId(),
            latestVersion == null ? null : latestVersion.getVersionLabel(),
            latestVersion == null ? null : latestVersion.getPublishedAt(),
            liveVersion == null ? null : liveVersion.getId(),
            liveVersion == null ? null : liveVersion.getVersionLabel(),
            liveRelease == null ? null : liveRelease.getAppliedAt(),
            summarizeWorkspaceLifecycle(savedDraftState, liveState, latestVersion, liveVersion)
        );
    }

    private String determineSavedDraftState(boolean hasPublishedVersion, boolean savedDraftMatchesLatestPublished) {
        if (!hasPublishedVersion) {
            return "NEVER_PUBLISHED";
        }
        return savedDraftMatchesLatestPublished ? "MATCHES_LATEST_PUBLISHED" : "UNPUBLISHED_CHANGES";
    }

    private String determineLiveState(DeploymentVersionEntity latestVersion,
                                      DeploymentVersionEntity liveVersion,
                                      DeploymentReleaseEntity latestRelease) {
        if (latestVersion == null) {
            return "NOT_PUBLISHED";
        }
        if (latestRelease != null
            && latestVersion.getId().equals(latestRelease.getDeploymentVersionId())
            && isReleaseInProgress(latestRelease)) {
            return "LATEST_APPLY_IN_PROGRESS";
        }
        if (latestRelease != null
            && latestVersion.getId().equals(latestRelease.getDeploymentVersionId())
            && "FAILED".equals(latestRelease.getStatus())) {
            return "LATEST_APPLY_FAILED";
        }
        if (liveVersion == null) {
            return "LATEST_PUBLISHED_NOT_APPLIED";
        }
        if (latestVersion.getId().equals(liveVersion.getId())) {
            return "LIVE_MATCHES_LATEST_PUBLISHED";
        }
        return "LIVE_OUTDATED";
    }

    private String summarizeWorkspaceLifecycle(String savedDraftState,
                                               String liveState,
                                               DeploymentVersionEntity latestVersion,
                                               DeploymentVersionEntity liveVersion) {
        return switch (savedDraftState) {
            case "NEVER_PUBLISHED" -> "Only the editable draft exists. Publish a version before apply.";
            case "UNPUBLISHED_CHANGES" -> switch (liveState) {
                case "LIVE_MATCHES_LATEST_PUBLISHED" ->
                    "Live deployment matches the latest published version, but the saved draft now contains unpublished changes.";
                case "LIVE_OUTDATED" ->
                    "The saved draft contains unpublished changes and the live deployment is still behind the latest published version.";
                case "LATEST_APPLY_IN_PROGRESS" ->
                    "Apply is still running for the latest published version while the saved draft already contains newer unpublished changes.";
                case "LATEST_APPLY_FAILED" ->
                    "The latest published version failed to apply, and the saved draft has already moved ahead again.";
                case "LATEST_PUBLISHED_NOT_APPLIED" ->
                    "The saved draft contains unpublished changes and there is still no applied live version.";
                default ->
                    "The saved draft contains unpublished changes that are not yet reflected in a published or applied version.";
            };
            default -> switch (liveState) {
                case "LIVE_MATCHES_LATEST_PUBLISHED" ->
                    "Saved draft, latest published version, and live deployment are aligned.";
                case "LIVE_OUTDATED" ->
                    "Saved draft matches the latest published version, but the live deployment is still on "
                        + (liveVersion == null ? "an older release." : liveVersion.getVersionLabel() + ".");
                case "LATEST_APPLY_IN_PROGRESS" ->
                    "The latest published version is currently being applied.";
                case "LATEST_APPLY_FAILED" ->
                    "The latest published version failed to apply. Review diagnostics before retrying.";
                case "LATEST_PUBLISHED_NOT_APPLIED" ->
                    "A published version exists, but it has not been applied yet.";
                default ->
                    "Draft and release state need operator review.";
            };
        };
    }

    private boolean draftMatchesVersion(DeploymentDraftEntity draft, DeploymentVersionEntity version) {
        return safeEquals(draft.getActionsConfigJson(), version.getActionsConfigJson())
            && safeEquals(draft.getEntityConfigJson(), version.getEntityConfigJson())
            && safeEquals(draft.getRoutingConfigJson(), version.getRoutingConfigJson())
            && safeEquals(draft.getProviderConfigJson(), version.getProviderConfigJson())
            && safeEquals(draft.getSecurityConfigJson(), version.getSecurityConfigJson())
            && safeEquals(draft.getPromptConfigJson(), version.getPromptConfigJson());
    }

    private boolean safeEquals(String left, String right) {
        return left == null ? right == null : left.equals(right);
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

    private DeploymentPromptRevisionSummary toPromptRevisionSummary(DeploymentPromptRevisionEntity revision) {
        return new DeploymentPromptRevisionSummary(
            revision.getId(),
            revision.getDeploymentId(),
            revision.getSourceDraftId(),
            revision.getRevisionLabel(),
            revision.getRevisionSummary(),
            revision.getCreatedByActorId(),
            revision.getCreatedByDisplayName(),
            countPopulatedPrompts(revision.getPromptConfigJson()),
            revision.getCreatedAt()
        );
    }

    private int countPopulatedPrompts(String promptConfigJson) {
        try {
            JsonNode promptConfig = objectMapper.readTree(promptConfigJson);
            int count = 0;
            for (String key : List.of(
                "systemPrompt",
                "intentExtractionPrompt",
                "actionSelectionPrompt",
                "clarificationPrompt",
                "answerGenerationPrompt",
                "retrievalPrompt",
                "assistantUiPrompt"
            )) {
                if (promptConfig.path(key).isTextual() && !promptConfig.path(key).asText().trim().isEmpty()) {
                    count++;
                }
            }
            return count;
        } catch (Exception ex) {
            return 0;
        }
    }

    private PlatformPrincipal requirePrincipal() {
        PlatformPrincipal principal = PlatformSecurityContext.currentPrincipal();
        if (principal == null) {
            throw new ResponseStatusException(FORBIDDEN, "Platform authentication is required.");
        }
        return principal;
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
                release.getCurrentStepKey(),
                release.getCurrentStepDescription(),
                release.getErrorMessage(),
                release.getVerificationRunId(),
                objectMapper.readTree(release.getProvisioningDetailsJson()),
                release.getCreatedAt(),
                release.getAppliedAt(),
                release.getUpdatedAt()
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read provisioning details", ex);
        }
    }

    private String initialReleaseDetails(String stepKey, String description) {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode progress = root.putObject("progress");
        progress.put("currentStepKey", stepKey);
        progress.put("currentStepDescription", description);
        progress.put("currentStepStatus", "QUEUED");
        ArrayNode steps = progress.putArray("steps");
        ObjectNode queued = steps.addObject();
        queued.put("key", stepKey);
        queued.put("description", description);
        queued.put("status", "QUEUED");
        queued.put("startedAt", Instant.now().toString());
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to build initial release details.", ex);
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

    private boolean isReleaseInProgress(DeploymentReleaseEntity release) {
        return switch (release.getStatus()) {
            case "APPLY_REQUESTED", "PROVISIONING", "VERIFYING" -> true;
            default -> "QUEUED".equals(release.getProvisioningStatus())
                || "RUNNING".equals(release.getProvisioningStatus())
                || "RUNNING".equals(release.getVerificationStatus());
        };
    }

    private List<DeploymentEntity> selectDeployments(boolean includeArchived) {
        List<DeploymentEntity> deployments;
        if (includeArchived) {
            deployments = deploymentRepository.findAllByOrderByCreatedAtDesc();
        } else {
            deployments = deploymentRepository.findByArchivedAtIsNullOrderByCreatedAtDesc();
        }
        return deploymentAccessService.filterVisibleDeployments(deployments);
    }

    private void assertNotArchived(DeploymentEntity deployment, String action) {
        if (isArchived(deployment)) {
            throw new ResponseStatusException(BAD_REQUEST, "Deployment is archived and cannot " + action + ".");
        }
    }

    private boolean isArchived(DeploymentEntity deployment) {
        return deployment.getArchivedAt() != null || "ARCHIVED".equalsIgnoreCase(deployment.getStatus());
    }

    private String determineRestoredStatus(DeploymentEntity deployment, DeploymentReleaseEntity latestRelease) {
        if (latestRelease != null) {
            return switch (latestRelease.getStatus()) {
                case "APPLIED_VERIFIED", "APPLIED_VERIFICATION_FAILED" -> "ACTIVE";
                case "FAILED" -> deployment.getActiveVersionId() != null ? "APPLY_FAILED" : "DRAFT";
                default -> deployment.getActiveVersionId() != null ? "VERSION_PUBLISHED" : "DRAFT";
            };
        }
        return deployment.getActiveVersionId() != null ? "VERSION_PUBLISHED" : "DRAFT";
    }

    private DeploymentLifecycleSnapshotSummary toLifecycleSnapshot(DeploymentReleaseEntity release) {
        if (release == null) {
            return null;
        }
        return new DeploymentLifecycleSnapshotSummary(
            release.getId(),
            release.getDeploymentVersionId(),
            release.getStatus(),
            release.getProvisioningStatus(),
            release.getVerificationStatus(),
            release.getCurrentStepKey(),
            release.getCurrentStepDescription(),
            release.getUpdatedAt()
        );
    }

    private DeploymentVerificationSnapshotSummary toVerificationSnapshot(DeploymentVerificationRunEntity verificationRun) {
        if (verificationRun == null) {
            return null;
        }

        int passed = 0;
        int warning = 0;
        int failed = 0;
        int skipped = 0;
        try {
            JsonNode checks = objectMapper.readTree(verificationRun.getChecksJson());
            if (checks.isArray()) {
                for (JsonNode check : checks) {
                    String status = check.path("status").asText("UNKNOWN");
                    switch (status) {
                        case "PASSED" -> passed++;
                        case "WARNING" -> warning++;
                        case "FAILED" -> failed++;
                        case "SKIPPED" -> skipped++;
                        default -> {
                        }
                    }
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read verification summary counts", ex);
        }

        return new DeploymentVerificationSnapshotSummary(
            verificationRun.getId(),
            verificationRun.getStatus(),
            verificationRun.getSummaryMessage(),
            passed,
            warning,
            failed,
            skipped,
            verificationRun.getCompletedAt()
        );
    }

    private String determineHealthStatus(DeploymentEntity deployment,
                                         DeploymentReleaseEntity latestRelease,
                                         DeploymentVerificationRunEntity latestVerification) {
        if (isArchived(deployment)) {
            return "ARCHIVED";
        }
        if (latestRelease != null && isReleaseInProgress(latestRelease)) {
            return "PROVISIONING";
        }
        if (latestVerification != null) {
            return switch (latestVerification.getStatus()) {
                case "PASSED" -> "HEALTHY";
                case "FAILED" -> "ATTENTION";
                default -> "REVIEW";
            };
        }
        if (deployment.getActiveVersionId() != null) {
            return "READY_TO_APPLY";
        }
        return "DRAFT";
    }

    private String determineHealthSummary(DeploymentEntity deployment,
                                          DeploymentReleaseEntity latestRelease,
                                          DeploymentVerificationRunEntity latestVerification) {
        if (isArchived(deployment)) {
            return "Archived. Hidden from active customer workflows.";
        }
        if (latestRelease != null && isReleaseInProgress(latestRelease)) {
            return latestRelease.getCurrentStepDescription() != null
                ? latestRelease.getCurrentStepDescription()
                : "Apply is still running.";
        }
        if (latestVerification != null) {
            return latestVerification.getSummaryMessage();
        }
        if (deployment.getActiveVersionId() != null) {
            return "A version is published and ready to apply.";
        }
        return "Draft configuration has not been published yet.";
    }

    private void scheduleApplyAfterCommit(String deploymentId, String versionId, String releaseId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deploymentReleaseExecutionService.executeApply(deploymentId, versionId, releaseId);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deploymentReleaseExecutionService.executeApply(deploymentId, versionId, releaseId);
            }
        });
    }
}
