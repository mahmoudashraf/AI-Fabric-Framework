package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.PlatformProductProvisioningProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentDraftEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentManagedVectorResourceEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentProviderResourceHandleEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVerificationRunEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.entity.PublicApiDeploymentEntity;
import com.ai.fabric.platform.backend.deployment.model.CreateDeploymentRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentBundleModels;
import com.ai.fabric.platform.backend.deployment.model.DeploymentBundleModels.DeploymentBundleExportPreviewSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentBundleModels.DeploymentBundleExportSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentBundleModels.DeploymentBundleExternalIntegrationImpact;
import com.ai.fabric.platform.backend.deployment.model.DeploymentBundleModels.DeploymentBundleImportExecutionSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentBundleModels.DeploymentBundleImportPreviewSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentBundleModels.DeploymentBundleSecretInventoryItem;
import com.ai.fabric.platform.backend.deployment.model.DeploymentBundleModels.DeploymentBundleSecretSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentBundleModels.DeploymentExportPreviewRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentBundleModels.DeploymentExportRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentBundleModels.DeploymentImportPreviewRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentBundleModels.DeploymentImportRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentBundleModels.ExportMode;
import com.ai.fabric.platform.backend.deployment.model.DeploymentBundleModels.ImportMode;
import com.ai.fabric.platform.backend.deployment.model.DeploymentBundleModels.SecretClassification;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentDraftRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentManagedVectorResourceRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentProviderResourceHandleRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVerificationRunRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.repository.PublicApiDeploymentRepository;
import com.ai.fabric.platform.backend.marketplace.entity.DeploymentMarketplacePluginInstallEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginDatasetEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginVersionEntity;
import com.ai.fabric.platform.backend.marketplace.repository.DeploymentMarketplacePluginInstallRepository;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginDatasetRepository;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginRepository;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginVersionRepository;
import com.ai.fabric.platform.backend.productservice.entity.PlatformManagedProductServiceEntity;
import com.ai.fabric.platform.backend.productservice.repository.PlatformManagedProductServiceRepository;
import com.ai.fabric.platform.backend.secret.entity.DeploymentProviderSecretBindingEntity;
import com.ai.fabric.platform.backend.secret.entity.PlatformSecretCleanupPolicy;
import com.ai.fabric.platform.backend.secret.entity.PlatformSecretEntity;
import com.ai.fabric.platform.backend.secret.entity.PlatformSecretOwnerType;
import com.ai.fabric.platform.backend.secret.entity.PlatformSecretScopeType;
import com.ai.fabric.platform.backend.secret.repository.DeploymentProviderSecretBindingRepository;
import com.ai.fabric.platform.backend.secret.repository.PlatformSecretRepository;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationPlanEntity;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationPlanRevisionEntity;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationSourceConnectionEntity;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationPlanRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationPlanRevisionRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationSourceConnectionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

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
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.ai.fabric.platform.backend.deployment.model.DeploymentBundleModels.SCHEMA_VERSION;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class DeploymentBundleExportImportService {

    private static final Pattern ENV_REF_PATTERN = Pattern.compile("\\$\\{([A-Z0-9_]+)}");
    private static final Set<String> FORBIDDEN_SECRET_NAMES = Set.of(
        "PLATFORM_ADMIN_API_KEY",
        "PLATFORM_OPERATOR_API_KEY",
        "SHOPIFY_ADMIN_ACCESS_TOKEN",
        "SHOPIFY_MERCHANT_AUTHORIZATION"
    );
    private static final Set<String> ENVIRONMENT_BOUND_PREFIXES = Set.of(
        "SHOPIFY_",
        "COOLIFY_",
        "RAILWAY_",
        "SPRING_DATASOURCE_",
        "DATABASE_",
        "POSTGRES_"
    );
    private static final Set<String> SHARED_PROVIDER_SECRETS = Set.of(
        "OPENAI_API_KEY",
        "ANTHROPIC_API_KEY",
        "AZURE_OPENAI_API_KEY",
        "COHERE_API_KEY",
        "GEMINI_API_KEY",
        "PLATFORM_ADMIN_API_KEY",
        "PLATFORM_OPERATOR_API_KEY"
    );

    private final ObjectMapper objectMapper;
    private final DeploymentRepository deploymentRepository;
    private final DeploymentDraftRepository draftRepository;
    private final DeploymentVersionRepository versionRepository;
    private final DeploymentReleaseRepository releaseRepository;
    private final DeploymentVerificationRunRepository verificationRunRepository;
    private final DeploymentProviderResourceHandleRepository resourceHandleRepository;
    private final DeploymentManagedVectorResourceRepository managedVectorResourceRepository;
    private final DeploymentMarketplacePluginInstallRepository marketplacePluginInstallRepository;
    private final MarketplacePluginRepository marketplacePluginRepository;
    private final MarketplacePluginVersionRepository marketplacePluginVersionRepository;
    private final MarketplacePluginDatasetRepository marketplacePluginDatasetRepository;
    private final DeploymentProviderSecretBindingRepository secretBindingRepository;
    private final PlatformSecretRepository platformSecretRepository;
    private final PublicApiDeploymentRepository publicApiDeploymentRepository;
    private final VectorizationPlanRepository vectorizationPlanRepository;
    private final VectorizationSourceConnectionRepository vectorizationSourceConnectionRepository;
    private final VectorizationPlanRevisionRepository vectorizationPlanRevisionRepository;
    private final PlatformManagedProductServiceRepository managedProductServiceRepository;
    private final PlatformProductProvisioningProperties productProvisioningProperties;
    private final DeploymentAccessService deploymentAccessService;
    private final DeploymentService deploymentService;
    private final PlatformAuditService platformAuditService;
    private final DeploymentBundleSealingService sealingService;

    public DeploymentBundleExportImportService(ObjectMapper objectMapper,
                                               DeploymentRepository deploymentRepository,
                                               DeploymentDraftRepository draftRepository,
                                               DeploymentVersionRepository versionRepository,
                                               DeploymentReleaseRepository releaseRepository,
                                               DeploymentVerificationRunRepository verificationRunRepository,
                                               DeploymentProviderResourceHandleRepository resourceHandleRepository,
                                               DeploymentManagedVectorResourceRepository managedVectorResourceRepository,
                                               DeploymentMarketplacePluginInstallRepository marketplacePluginInstallRepository,
                                               MarketplacePluginRepository marketplacePluginRepository,
                                               MarketplacePluginVersionRepository marketplacePluginVersionRepository,
                                               MarketplacePluginDatasetRepository marketplacePluginDatasetRepository,
                                               DeploymentProviderSecretBindingRepository secretBindingRepository,
                                               PlatformSecretRepository platformSecretRepository,
                                               PublicApiDeploymentRepository publicApiDeploymentRepository,
                                               VectorizationPlanRepository vectorizationPlanRepository,
                                               VectorizationSourceConnectionRepository vectorizationSourceConnectionRepository,
                                               VectorizationPlanRevisionRepository vectorizationPlanRevisionRepository,
                                               PlatformManagedProductServiceRepository managedProductServiceRepository,
                                               PlatformProductProvisioningProperties productProvisioningProperties,
                                               DeploymentAccessService deploymentAccessService,
                                               DeploymentService deploymentService,
                                               PlatformAuditService platformAuditService,
                                               DeploymentBundleSealingService sealingService) {
        this.objectMapper = objectMapper;
        this.deploymentRepository = deploymentRepository;
        this.draftRepository = draftRepository;
        this.versionRepository = versionRepository;
        this.releaseRepository = releaseRepository;
        this.verificationRunRepository = verificationRunRepository;
        this.resourceHandleRepository = resourceHandleRepository;
        this.managedVectorResourceRepository = managedVectorResourceRepository;
        this.marketplacePluginInstallRepository = marketplacePluginInstallRepository;
        this.marketplacePluginRepository = marketplacePluginRepository;
        this.marketplacePluginVersionRepository = marketplacePluginVersionRepository;
        this.marketplacePluginDatasetRepository = marketplacePluginDatasetRepository;
        this.secretBindingRepository = secretBindingRepository;
        this.platformSecretRepository = platformSecretRepository;
        this.publicApiDeploymentRepository = publicApiDeploymentRepository;
        this.vectorizationPlanRepository = vectorizationPlanRepository;
        this.vectorizationSourceConnectionRepository = vectorizationSourceConnectionRepository;
        this.vectorizationPlanRevisionRepository = vectorizationPlanRevisionRepository;
        this.managedProductServiceRepository = managedProductServiceRepository;
        this.productProvisioningProperties = productProvisioningProperties;
        this.deploymentAccessService = deploymentAccessService;
        this.deploymentService = deploymentService;
        this.platformAuditService = platformAuditService;
        this.sealingService = sealingService;
    }

    public DeploymentBundleExportPreviewSummary previewExport(String deploymentId, DeploymentExportPreviewRequest request) {
        ExportMode exportMode = request == null ? ExportMode.CONFIG_ONLY : request.normalizedExportMode();
        DeploymentEntity deployment = requireDeploymentAdmin(deploymentId);
        BundleState state = buildBundleState(deployment, exportMode);
        DeploymentBundleSecretSummary secretSummary = secretSummary(state.secretInventory(), exportMode == ExportMode.SEALED_BACKUP);
        platformAuditService.record(
            "DEPLOYMENT_EXPORT_PREVIEWED",
            "DEPLOYMENT",
            deployment.getId(),
            Map.of(
                "exportMode", exportMode.name(),
                "secretCount", secretSummary.items().size(),
                "includedValues", secretSummary.includedValues()
            )
        );
        return new DeploymentBundleExportPreviewSummary(
            deployment.getId(),
            exportMode,
            includedSections(state.manifest()),
            secretSummary,
            externalImpact(exportMode, null, deployment.getId(), deployment.getId()),
            previewWarnings(secretSummary)
        );
    }

    public DeploymentBundleExportSummary exportDeployment(String deploymentId, DeploymentExportRequest request) {
        DeploymentEntity deployment = requireDeploymentAdmin(deploymentId);
        ExportMode exportMode = request == null ? ExportMode.CONFIG_ONLY : request.normalizedExportMode();
        if (exportMode == ExportMode.SEALED_BACKUP && !StringUtils.hasText(request == null ? null : request.reason())) {
            throw new ResponseStatusException(BAD_REQUEST, "Sealed backup export requires a reason.");
        }

        BundleState state = buildBundleState(deployment, exportMode);
        String bundleId = generateId("dxb");
        Instant now = Instant.now();
        ObjectNode bundle = objectMapper.createObjectNode();
        bundle.put("schemaVersion", SCHEMA_VERSION);
        bundle.put("bundleId", bundleId);
        bundle.put("exportMode", exportMode.name());
        bundle.put("createdAt", now.toString());
        bundle.set("createdBy", createdBy());
        bundle.set("source", sourceNode(deployment));
        bundle.set("manifest", state.manifest());

        boolean includeSecretValues = exportMode == ExportMode.SEALED_BACKUP;
        DeploymentBundleSecretSummary secretSummary = secretSummary(state.secretInventory(), includeSecretValues);
        bundle.set("secretInventory", objectMapper.valueToTree(secretSummary.items()));

        String secretEnvelopeHash = null;
        if (includeSecretValues) {
            ObjectNode secretPayload = secretPayload(deployment, state.secretInventory());
            DeploymentBundleSealingService.SealedPayload sealed = sealingService.seal(
                secretPayload,
                request.recipient() == null ? null : request.recipient().publicKeyPem(),
                Map.of(
                    "bundleId", bundleId,
                    "schemaVersion", SCHEMA_VERSION,
                    "sourceDeploymentId", deployment.getId(),
                    "manifestHash", state.manifestHash()
                )
            );
            secretEnvelopeHash = sealed.envelopeHash();
            bundle.set("secretEnvelope", sealed.envelope());
        }

        ObjectNode integrity = objectMapper.createObjectNode();
        integrity.put("manifestHash", state.manifestHash());
        integrity.put("secretEnvelopeHash", secretEnvelopeHash);
        bundle.set("integrity", integrity);
        bundle.set("importGuidance", importGuidance(deployment));
        String bundleHash = sealingService.sha256(bundle);
        ((ObjectNode) bundle.path("integrity")).put("bundleHash", bundleHash);

        String action = exportMode == ExportMode.SEALED_BACKUP
            ? "DEPLOYMENT_SEALED_BACKUP_EXPORTED"
            : "DEPLOYMENT_CONFIG_EXPORTED";
        platformAuditService.record(
            action,
            "DEPLOYMENT",
            deployment.getId(),
            Map.of(
                "bundleId", bundleId,
                "bundleHash", bundleHash,
                "manifestHash", state.manifestHash(),
                "secretEnvelopeHash", secretEnvelopeHash == null ? "" : secretEnvelopeHash,
                "secretCount", secretSummary.items().size(),
                "includedValues", secretSummary.includedValues(),
                "reason", request == null || request.reason() == null ? "" : request.reason().trim()
            )
        );
        return new DeploymentBundleExportSummary(
            generateId("dexp"),
            bundleId,
            exportMode,
            "READY",
            bundleHash,
            state.manifestHash(),
            secretEnvelopeHash,
            secretSummary,
            bundle,
            now
        );
    }

    public DeploymentBundleImportPreviewSummary previewImport(DeploymentImportPreviewRequest request) {
        ImportMode importMode = request == null ? ImportMode.CONFIG_ONLY_CLONE : request.normalizedImportMode();
        JsonNode bundle = requireBundle(request == null ? null : request.bundle());
        ImportValidation validation = validateBundle(bundle, importMode, request == null ? null : request.targetDeploymentId());
        boolean secretsReadable = false;
        if (bundle.hasNonNull("secretEnvelope") && StringUtils.hasText(request == null ? null : request.privateKeyPem())) {
            sealingService.unseal(bundle.path("secretEnvelope"), request.privateKeyPem());
            secretsReadable = true;
        }
        String sourceDeploymentId = sourceDeploymentId(bundle);
        String targetDeploymentId = targetDeploymentIdForPreview(importMode, request, sourceDeploymentId);
        DeploymentBundleExternalIntegrationImpact impact = externalImpact(importMode, request, sourceDeploymentId, targetDeploymentId);
        List<String> requiredSecretActions = requiredSecretActions(bundle, importMode, secretsReadable);
        platformAuditService.record(
            "DEPLOYMENT_IMPORT_PREVIEWED",
            "DEPLOYMENT",
            targetDeploymentId == null ? sourceDeploymentId : targetDeploymentId,
            Map.of(
                "importMode", importMode.name(),
                "sourceDeploymentId", sourceDeploymentId,
                "integrityValid", validation.integrityValid(),
                "blockingIssueCount", validation.blockingIssues().size(),
                "secretsReadable", secretsReadable
            )
        );
        return new DeploymentBundleImportPreviewSummary(
            validation.schemaValid(),
            validation.integrityValid(),
            secretsReadable,
            importMode,
            sourceDeploymentId,
            targetDeploymentId,
            resolvedNewName(request, bundle),
            impact,
            validation.blockingIssues(),
            validation.warnings(),
            requiredSecretActions
        );
    }

    @Transactional
    public DeploymentBundleImportExecutionSummary importDeployment(DeploymentImportRequest request) {
        ImportMode importMode = request == null ? ImportMode.CONFIG_ONLY_CLONE : request.normalizedImportMode();
        JsonNode bundle = requireBundle(request == null ? null : request.bundle());
        ImportValidation validation = validateBundle(bundle, importMode, request == null ? null : request.targetDeploymentId());
        if (!validation.blockingIssues().isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, String.join(" | ", validation.blockingIssues()));
        }
        JsonNode decryptedSecrets = null;
        if (importMode == ImportMode.SEALED_CLONE || (importMode == ImportMode.RESTORE_IN_PLACE && bundle.hasNonNull("secretEnvelope"))) {
            decryptedSecrets = sealingService.unseal(bundle.path("secretEnvelope"), request == null ? null : request.privateKeyPem());
        }

        String importId = generateId("dimp");
        String deploymentId;
        String draftId;
        if (importMode == ImportMode.RESTORE_IN_PLACE) {
            RestoreResult restoreResult = restoreInPlace(bundle, request, decryptedSecrets);
            deploymentId = restoreResult.deploymentId();
            draftId = restoreResult.draftId();
        } else {
            RestoreResult cloneResult = cloneAsNew(bundle, request, decryptedSecrets, importMode == ImportMode.SEALED_CLONE);
            deploymentId = cloneResult.deploymentId();
            draftId = cloneResult.draftId();
        }

        boolean secretsReadable = decryptedSecrets != null;
        DeploymentBundleExternalIntegrationImpact impact = externalImpact(importMode, request, sourceDeploymentId(bundle), deploymentId);
        List<String> secretActions = requiredSecretActions(bundle, importMode, secretsReadable);
        platformAuditService.record(
            importMode == ImportMode.RESTORE_IN_PLACE ? "DEPLOYMENT_RESTORE_IN_PLACE_REQUESTED" : "DEPLOYMENT_IMPORT_DRAFT_CREATED",
            "DEPLOYMENT",
            deploymentId,
            Map.of(
                "importId", importId,
                "sourceDeploymentId", sourceDeploymentId(bundle),
                "importMode", importMode.name(),
                "draftId", draftId,
                "secretActions", secretActions.size()
            )
        );
        return new DeploymentBundleImportExecutionSummary(
            importId,
            "DRAFT_CREATED",
            importMode,
            deploymentId,
            draftId,
            impact,
            secretActions,
            List.of("validate draft", "publish version", "apply through target profile", "run hosted verification"),
            Instant.now()
        );
    }

    private DeploymentEntity requireDeploymentAdmin(String deploymentId) {
        DeploymentEntity deployment = deploymentRepository.findById(deploymentId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment not found: " + deploymentId));
        return deploymentAccessService.requireDeploymentAdminAccess(deployment);
    }

    private BundleState buildBundleState(DeploymentEntity deployment, ExportMode exportMode) {
        DeploymentDraftEntity draft = activeDraft(deployment);
        ObjectNode manifest = objectMapper.createObjectNode();
        manifest.put(
            "bundlePurpose",
            exportMode == ExportMode.SEALED_BACKUP
                ? "SEALED_BACKUP_CONFIGURATION_WITH_SOURCE_OPERATIONAL_SNAPSHOT"
                : "DEPLOYABLE_CONFIGURATION"
        );
        manifest.set("deployment", deploymentNode(deployment));
        manifest.set("activeDraft", draftNode(draft));
        manifest.set("activeVersion", activeVersionNode(deployment.getActiveVersionId()));
        ArrayNode marketplaceInstalls = marketplaceInstallsNode(deployment.getId());
        manifest.set("marketplaceCatalog", marketplaceCatalogNode(marketplaceInstalls));
        manifest.set("marketplaceInstalls", marketplaceInstalls);
        manifest.set("vectorizationControlPlane", vectorizationControlPlaneNode(deployment.getId()));
        manifest.set("providerSecretBindings", secretBindingsNode(deployment.getId()));
        manifest.set("publicApiBindings", publicApiBindingsNode(deployment.getId()));
        manifest.set("managedProductServiceDependencies", managedProductServiceDependenciesNode(draft));
        if (exportMode == ExportMode.SEALED_BACKUP) {
            manifest.set("sourceOperationalSnapshot", sourceOperationalSnapshotNode(deployment.getId()));
        }

        SecretInventory secretInventory = collectSecretInventory(deployment, manifest);
        return new BundleState(manifest, sealingService.sha256(manifest), secretInventory);
    }

    private DeploymentDraftEntity activeDraft(DeploymentEntity deployment) {
        if (StringUtils.hasText(deployment.getActiveDraftId())) {
            return draftRepository.findById(deployment.getActiveDraftId())
                .orElseGet(() -> latestDraft(deployment.getId()));
        }
        return latestDraft(deployment.getId());
    }

    private DeploymentDraftEntity latestDraft(String deploymentId) {
        return draftRepository.findTopByDeploymentIdOrderByRevisionNumberDesc(deploymentId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "No draft found for deployment: " + deploymentId));
    }

    private ObjectNode deploymentNode(DeploymentEntity deployment) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", deployment.getId());
        node.put("name", deployment.getName());
        node.put("environmentName", deployment.getEnvironmentName());
        node.put("templateId", deployment.getTemplateId());
        node.put("customerId", deployment.getCustomerId());
        node.put("tenantId", deployment.getTenantId());
        node.put("sourceRepositoryOverride", deployment.getSourceRepositoryOverride());
        node.put("sourceBranchOverride", deployment.getSourceBranchOverride());
        node.put("approvalRequiredForApply", deployment.isApprovalRequiredForApply());
        node.put("approvalRequiredForDelete", deployment.isApprovalRequiredForDelete());
        node.put("createdAt", stringTime(deployment.getCreatedAt()));
        node.put("updatedAt", stringTime(deployment.getUpdatedAt()));
        return node;
    }

    private ObjectNode draftNode(DeploymentDraftEntity draft) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", draft.getId());
        node.put("deploymentId", draft.getDeploymentId());
        node.put("revisionNumber", draft.getRevisionNumber());
        node.put("status", draft.getStatus());
        ObjectNode configs = node.putObject("configs");
        configs.set("actions", readJson(draft.getActionsConfigJson()));
        configs.set("entities", readJson(draft.getEntityConfigJson()));
        configs.set("routing", readJson(draft.getRoutingConfigJson()));
        configs.set("provider", readJson(draft.getProviderConfigJson()));
        configs.set("security", readJson(draft.getSecurityConfigJson()));
        configs.set("prompt", readJson(draft.getPromptConfigJson()));
        configs.set("knowledgeSource", readJson(draft.getKnowledgeSourceConfigJson()));
        configs.set("shell", readJson(draft.getShellConfigJson()));
        configs.set("marketplaceDataset", readJson(draft.getMarketplaceDatasetConfigJson()));
        node.put("createdAt", stringTime(draft.getCreatedAt()));
        node.put("updatedAt", stringTime(draft.getUpdatedAt()));
        return node;
    }

    private JsonNode activeVersionNode(String activeVersionId) {
        if (!StringUtils.hasText(activeVersionId)) {
            return objectMapper.createObjectNode();
        }
        return versionRepository.findById(activeVersionId.trim())
            .map(this::versionNode)
            .orElseGet(objectMapper::createObjectNode);
    }

    private ObjectNode versionNode(DeploymentVersionEntity version) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", version.getId());
        node.put("deploymentId", version.getDeploymentId());
        node.put("sourceDraftId", version.getSourceDraftId());
        node.put("versionLabel", version.getVersionLabel());
        node.put("status", version.getStatus());
        node.put("configHash", version.getConfigHash());
        node.put("reindexRequired", version.isReindexRequired());
        ObjectNode configs = node.putObject("configs");
        configs.set("actions", readJson(version.getActionsConfigJson()));
        configs.set("entities", readJson(version.getEntityConfigJson()));
        configs.set("routing", readJson(version.getRoutingConfigJson()));
        configs.set("provider", readJson(version.getProviderConfigJson()));
        configs.set("security", readJson(version.getSecurityConfigJson()));
        configs.set("prompt", readJson(version.getPromptConfigJson()));
        configs.set("knowledgeSource", readJson(version.getKnowledgeSourceConfigJson()));
        configs.set("shell", readJson(version.getShellConfigJson()));
        configs.set("marketplaceDataset", readJson(version.getMarketplaceDatasetConfigJson()));
        ObjectNode artifacts = node.putObject("artifacts");
        artifacts.put("actionsYaml", version.getActionsArtifactYaml());
        artifacts.put("entityYaml", version.getEntityArtifactYaml());
        artifacts.put("routingYaml", version.getRoutingArtifactYaml());
        artifacts.set("manifest", readJson(version.getManifestJson()));
        node.put("publishedAt", stringTime(version.getPublishedAt()));
        return node;
    }

    private JsonNode latestReleaseNode(String deploymentId) {
        return releaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc(deploymentId)
            .map(this::releaseNode)
            .orElseGet(objectMapper::createObjectNode);
    }

    private ObjectNode releaseNode(DeploymentReleaseEntity release) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", release.getId());
        node.put("deploymentId", release.getDeploymentId());
        node.put("deploymentVersionId", release.getDeploymentVersionId());
        node.put("status", release.getStatus());
        node.put("verificationStatus", release.getVerificationStatus());
        node.put("provisioningStatus", release.getProvisioningStatus());
        node.put("provisioningTarget", release.getProvisioningTarget());
        node.put("targetProfileId", release.getTargetProfileId());
        node.put("providerType", release.getProviderType() == null ? null : release.getProviderType().name());
        node.put("providerResourceHandleId", release.getProviderResourceHandleId());
        node.put("verificationRunId", release.getVerificationRunId());
        node.set("provisioningDetails", readJson(release.getProvisioningDetailsJson()));
        node.put("createdAt", stringTime(release.getCreatedAt()));
        node.put("appliedAt", stringTime(release.getAppliedAt()));
        node.put("updatedAt", stringTime(release.getUpdatedAt()));
        return node;
    }

    private JsonNode latestVerificationNode(String deploymentId) {
        return verificationRunRepository.findByDeploymentIdOrderByCreatedAtDesc(deploymentId).stream()
            .findFirst()
            .map(this::verificationNode)
            .orElseGet(objectMapper::createObjectNode);
    }

    private ObjectNode verificationNode(DeploymentVerificationRunEntity run) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", run.getId());
        node.put("deploymentId", run.getDeploymentId());
        node.put("releaseId", run.getReleaseId());
        node.put("deploymentVersionId", run.getDeploymentVersionId());
        node.put("verificationType", run.getVerificationType());
        node.put("status", run.getStatus());
        node.put("summaryMessage", run.getSummaryMessage());
        node.set("checks", readJson(run.getChecksJson()));
        node.put("createdAt", stringTime(run.getCreatedAt()));
        node.put("completedAt", stringTime(run.getCompletedAt()));
        return node;
    }

    private ArrayNode marketplaceInstallsNode(String deploymentId) {
        ArrayNode items = objectMapper.createArrayNode();
        marketplacePluginInstallRepository.findByDeploymentIdOrderByUpdatedAtDesc(deploymentId)
            .forEach(install -> {
                ObjectNode node = objectMapper.createObjectNode();
                node.put("id", install.getId());
                node.put("deploymentId", install.getDeploymentId());
                node.put("pluginId", install.getPluginId());
                node.put("pluginVersionId", install.getPluginVersionId());
                node.put("status", install.getStatus());
                node.set("config", readJson(install.getConfigJson()));
                node.set("secretRefs", readJson(install.getSecretRefsJson()));
                node.put("createdAt", stringTime(install.getCreatedAt()));
                node.put("updatedAt", stringTime(install.getUpdatedAt()));
                items.add(node);
            });
        return items;
    }

    private ObjectNode sourceOperationalSnapshotNode(String deploymentId) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("authoritativeForTargetImport", false);
        node.put("purpose", "Historical source-environment evidence only. Import must provision and observe fresh target resources.");
        node.put("capturedAt", Instant.now().toString());
        node.set("latestRelease", latestReleaseNode(deploymentId));
        node.set("latestVerification", latestVerificationNode(deploymentId));
        node.set("providerResourceHandles", providerResourceHandlesNode(deploymentId));
        node.set("managedVectorResources", managedVectorResourcesNode(deploymentId));
        return node;
    }

    private ObjectNode marketplaceCatalogNode(JsonNode marketplaceInstalls) {
        ObjectNode catalog = objectMapper.createObjectNode();
        ArrayNode plugins = catalog.putArray("plugins");
        ArrayNode versions = catalog.putArray("versions");
        ArrayNode datasets = catalog.putArray("datasets");
        if (!marketplaceInstalls.isArray()) {
            return catalog;
        }

        Set<String> pluginIds = new LinkedHashSet<>();
        Set<String> versionIds = new LinkedHashSet<>();
        marketplaceInstalls.forEach(install -> {
            String pluginId = install.path("pluginId").asText(null);
            String pluginVersionId = install.path("pluginVersionId").asText(null);
            if (StringUtils.hasText(pluginId)) {
                pluginIds.add(pluginId.trim());
            }
            if (StringUtils.hasText(pluginVersionId)) {
                versionIds.add(pluginVersionId.trim());
            }
        });

        pluginIds.stream()
            .map(marketplacePluginRepository::findById)
            .flatMap(Optional::stream)
            .map(this::marketplacePluginNode)
            .forEach(plugins::add);
        versionIds.stream()
            .map(marketplacePluginVersionRepository::findById)
            .flatMap(Optional::stream)
            .map(this::marketplacePluginVersionNode)
            .forEach(versions::add);
        versionIds.stream()
            .flatMap(versionId -> marketplacePluginDatasetsForVersion(versionId).stream())
            .map(this::marketplacePluginDatasetNode)
            .forEach(datasets::add);
        return catalog;
    }

    private List<MarketplacePluginDatasetEntity> marketplacePluginDatasetsForVersion(String versionId) {
        List<MarketplacePluginDatasetEntity> datasets =
            marketplacePluginDatasetRepository.findByPluginVersionIdOrderByDatasetIdAsc(versionId);
        return datasets == null ? List.of() : datasets;
    }

    private ObjectNode marketplacePluginNode(MarketplacePluginEntity plugin) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", plugin.getId());
        node.put("slug", plugin.getSlug());
        node.put("displayName", plugin.getDisplayName());
        node.put("pluginType", plugin.getPluginType());
        node.put("publisherSlug", plugin.getPublisherSlug());
        node.put("publisherDisplayName", plugin.getPublisherDisplayName());
        node.put("shortDescription", plugin.getShortDescription());
        node.put("status", plugin.getStatus());
        node.put("createdAt", stringTime(plugin.getCreatedAt()));
        node.put("updatedAt", stringTime(plugin.getUpdatedAt()));
        return node;
    }

    private ObjectNode marketplacePluginVersionNode(MarketplacePluginVersionEntity version) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", version.getId());
        node.put("pluginId", version.getPluginId());
        node.put("version", version.getVersion());
        node.put("releaseChannel", version.getReleaseChannel());
        node.put("status", version.getStatus());
        node.set("manifest", readJson(version.getManifestJson()));
        node.put("submittedByPublisherId", version.getSubmittedByPublisherId());
        node.put("submittedByActorId", version.getSubmittedByActorId());
        node.put("reviewedByActorId", version.getReviewedByActorId());
        node.put("reviewedAt", stringTime(version.getReviewedAt()));
        node.put("reviewNotes", version.getReviewNotes());
        node.put("bundleSha256", version.getBundleSha256());
        node.put("createdAt", stringTime(version.getCreatedAt()));
        node.put("publishedAt", stringTime(version.getPublishedAt()));
        return node;
    }

    private ObjectNode marketplacePluginDatasetNode(MarketplacePluginDatasetEntity dataset) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", dataset.getId());
        node.put("pluginId", dataset.getPluginId());
        node.put("pluginVersionId", dataset.getPluginVersionId());
        node.put("datasetId", dataset.getDatasetId());
        node.put("entityType", dataset.getEntityType());
        node.put("storageScope", dataset.getStorageScope());
        node.put("sharingScope", dataset.getSharingScope());
        node.put("ingestionMode", dataset.getIngestionMode());
        node.put("updateStrategy", dataset.getUpdateStrategy());
        node.put("vectorizationProfile", dataset.getVectorizationProfile());
        node.put("handleTemplate", dataset.getHandleTemplate());
        node.put("seedDatasetRef", dataset.getSeedDatasetRef());
        node.put("connectorType", dataset.getConnectorType());
        node.set("connectorConfig", readJson(dataset.getConnectorConfigJson()));
        node.put("datasetHash", dataset.getDatasetHash());
        node.put("createdAt", stringTime(dataset.getCreatedAt()));
        node.put("updatedAt", stringTime(dataset.getUpdatedAt()));
        return node;
    }

    private ArrayNode providerResourceHandlesNode(String deploymentId) {
        ArrayNode items = objectMapper.createArrayNode();
        resourceHandleRepository.findByDeploymentIdOrderByUpdatedAtDesc(deploymentId)
            .forEach(handle -> {
                ObjectNode node = objectMapper.createObjectNode();
                node.put("id", handle.getId());
                node.put("deploymentId", handle.getDeploymentId());
                node.put("releaseId", handle.getReleaseId());
                node.put("targetProfileId", handle.getTargetProfileId());
                node.put("providerType", handle.getProviderType() == null ? null : handle.getProviderType().name());
                node.put("resourceKind", handle.getResourceKind());
                node.put("providerResourceUuid", handle.getProviderResourceUuid());
                node.put("providerProjectUuid", handle.getProviderProjectUuid());
                node.put("providerEnvironmentUuid", handle.getProviderEnvironmentUuid());
                node.put("providerServerUuid", handle.getProviderServerUuid());
                node.put("fqdn", handle.getFqdn());
                node.put("status", handle.getStatus());
                node.put("lastObservedStatus", handle.getLastObservedStatus());
                node.put("lastObservedAt", stringTime(handle.getLastObservedAt()));
                node.set("metadata", readJson(handle.getMetadataJson()));
                node.put("createdAt", stringTime(handle.getCreatedAt()));
                node.put("updatedAt", stringTime(handle.getUpdatedAt()));
                items.add(node);
            });
        return items;
    }

    private ArrayNode managedVectorResourcesNode(String deploymentId) {
        ArrayNode items = objectMapper.createArrayNode();
        managedVectorResourceRepository.findByDeploymentIdOrderByUpdatedAtDesc(deploymentId)
            .forEach(resource -> {
                ObjectNode node = objectMapper.createObjectNode();
                node.put("id", resource.getId());
                node.put("deploymentId", resource.getDeploymentId());
                node.put("deploymentVersionId", resource.getDeploymentVersionId());
                node.put("deploymentReleaseId", resource.getDeploymentReleaseId());
                node.put("vendor", resource.getVendor());
                node.put("vectorStrategy", resource.getVectorStrategy());
                node.put("vectorProvisioningMode", resource.getVectorProvisioningMode());
                node.put("managedMode", resource.getManagedMode());
                node.put("resourceType", resource.getResourceType());
                node.put("resourceName", resource.getResourceName());
                node.put("resourceReference", resource.getResourceReference());
                node.put("endpoint", resource.getEndpoint());
                node.put("resourceStatus", resource.getResourceStatus());
                node.put("provisioningState", resource.getProvisioningState());
                node.set("secretReferenceNames", readJson(resource.getSecretReferenceNamesJson()));
                node.set("details", readJson(resource.getDetailsJson()));
                node.put("createdAt", stringTime(resource.getCreatedAt()));
                node.put("updatedAt", stringTime(resource.getUpdatedAt()));
                items.add(node);
            });
        return items;
    }

    private ObjectNode vectorizationControlPlaneNode(String deploymentId) {
        ObjectNode node = objectMapper.createObjectNode();
        vectorizationPlanRepository.findByDeploymentId(deploymentId)
            .ifPresent(plan -> node.set("plan", vectorizationPlanNode(plan)));
        vectorizationSourceConnectionRepository.findByDeploymentId(deploymentId)
            .ifPresent(connection -> node.set("sourceConnection", vectorizationSourceConnectionNode(connection)));
        String planId = node.path("plan").path("id").asText(null);
        ArrayNode revisions = node.putArray("revisions");
        if (StringUtils.hasText(planId)) {
            vectorizationPlanRevisionRepository.findByPlanIdOrderByRevisionNumberDesc(planId).stream()
                .map(this::vectorizationPlanRevisionNode)
                .forEach(revisions::add);
        }
        return node;
    }

    private ObjectNode vectorizationPlanNode(VectorizationPlanEntity plan) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", plan.getId());
        node.put("deploymentId", plan.getDeploymentId());
        node.put("customerId", plan.getCustomerId());
        node.put("tenantId", plan.getTenantId());
        node.put("name", plan.getName());
        node.put("status", plan.getStatus());
        node.put("runnerMode", plan.getRunnerMode());
        node.put("syncState", plan.getSyncState());
        node.set("syncReasonCodes", readJson(plan.getSyncReasonCodesJson()));
        node.set("syncReasonDetails", readJson(plan.getSyncReasonDetailsJson()));
        node.put("sourceConnectionId", plan.getSourceConnectionId());
        node.put("activeRevisionId", plan.getActiveRevisionId());
        node.put("activeIndexedOutputHash", plan.getActiveIndexedOutputHash());
        node.put("lastSuccessfulIndexedOutputHash", plan.getLastSuccessfulIndexedOutputHash());
        node.put("manualConfirmationNote", plan.getManualConfirmationNote());
        node.put("manualConfirmationActorId", plan.getManualConfirmationActorId());
        node.put("manualConfirmationHash", plan.getManualConfirmationHash());
        node.put("manuallyConfirmedAt", stringTime(plan.getManuallyConfirmedAt()));
        node.put("deferredReindexAt", stringTime(plan.getDeferredReindexAt()));
        node.put("deferredReindexNote", plan.getDeferredReindexNote());
        node.put("deferredReindexHash", plan.getDeferredReindexHash());
        node.put("createdAt", stringTime(plan.getCreatedAt()));
        node.put("updatedAt", stringTime(plan.getUpdatedAt()));
        return node;
    }

    private ObjectNode vectorizationSourceConnectionNode(VectorizationSourceConnectionEntity connection) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", connection.getId());
        node.put("deploymentId", connection.getDeploymentId());
        node.put("customerId", connection.getCustomerId());
        node.put("tenantId", connection.getTenantId());
        node.put("name", connection.getName());
        node.put("adapterType", connection.getAdapterType());
        node.put("authMode", connection.getAuthMode());
        node.put("status", connection.getStatus());
        node.set("connectionConfig", readJson(connection.getConnectionConfigJson()));
        node.set("secretReferences", readJson(connection.getSecretReferencesJson()));
        node.set("discoverySummary", readJson(connection.getDiscoverySummaryJson()));
        node.put("createdAt", stringTime(connection.getCreatedAt()));
        node.put("updatedAt", stringTime(connection.getUpdatedAt()));
        return node;
    }

    private ObjectNode vectorizationPlanRevisionNode(VectorizationPlanRevisionEntity revision) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", revision.getId());
        node.put("planId", revision.getPlanId());
        node.put("deploymentId", revision.getDeploymentId());
        node.put("revisionNumber", revision.getRevisionNumber());
        node.put("status", revision.getStatus());
        node.put("sourceConnectionId", revision.getSourceConnectionId());
        node.set("entityScope", readJson(revision.getEntityScopeJson()));
        node.set("mappingConfig", readJson(revision.getMappingConfigJson()));
        node.set("executionConfig", readJson(revision.getExecutionConfigJson()));
        node.put("indexedOutputHash", revision.getIndexedOutputHash());
        node.put("createdByActorId", revision.getCreatedByActorId());
        node.put("createdAt", stringTime(revision.getCreatedAt()));
        node.put("updatedAt", stringTime(revision.getUpdatedAt()));
        return node;
    }

    private ArrayNode secretBindingsNode(String deploymentId) {
        ArrayNode items = objectMapper.createArrayNode();
        secretBindingRepository.findByDeploymentIdOrderBySecretPurposeAsc(deploymentId)
            .forEach(binding -> {
                ObjectNode node = objectMapper.createObjectNode();
                node.put("id", binding.getId());
                node.put("deploymentId", binding.getDeploymentId());
                node.put("secretPurpose", binding.getSecretPurpose());
                node.put("bindingMode", binding.getBindingMode() == null ? null : binding.getBindingMode().name());
                node.put("secretName", binding.getSecretName());
                node.put("secondarySecretName", binding.getSecondarySecretName());
                node.put("createdAt", stringTime(binding.getCreatedAt()));
                node.put("updatedAt", stringTime(binding.getUpdatedAt()));
                items.add(node);
            });
        return items;
    }

    private ArrayNode publicApiBindingsNode(String deploymentId) {
        ArrayNode items = objectMapper.createArrayNode();
        publicApiDeploymentRepository.findByDeploymentId(deploymentId)
            .forEach(binding -> {
                ObjectNode node = objectMapper.createObjectNode();
                node.put("id", binding.getId());
                node.put("clientId", binding.getClientId());
                node.put("externalDeploymentKey", binding.getExternalDeploymentKey());
                node.put("deploymentId", binding.getDeploymentId());
                node.set("callbackMetadata", readJson(binding.getCallbackMetadataJson()));
                node.put("createdAt", stringTime(binding.getCreatedAt()));
                node.put("updatedAt", stringTime(binding.getUpdatedAt()));
                items.add(node);
            });
        return items;
    }

    private ArrayNode managedProductServiceDependenciesNode(DeploymentDraftEntity draft) {
        ArrayNode items = objectMapper.createArrayNode();
        JsonNode actionsConfig = readJson(draft.getActionsConfigJson());
        Set<String> serviceRefs = new LinkedHashSet<>();
        if (hasMcpToolActions(actionsConfig)) {
            serviceRefs.add(productProvisioningProperties.mcpExecutionGatewayServiceRef());
        }
        serviceRefs.stream()
            .map(serviceRef -> managedProductServiceRepository.findByServiceRefIgnoreCase(serviceRef))
            .flatMap(Optional::stream)
            .map(this::managedProductServiceDependencyNode)
            .forEach(items::add);
        return items;
    }

    private ObjectNode managedProductServiceDependencyNode(PlatformManagedProductServiceEntity service) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", service.getId());
        node.put("serviceRef", service.getServiceRef());
        node.put("displayName", service.getDisplayName());
        node.put("productFamily", service.getProductFamily());
        node.put("serviceKind", service.getServiceKind());
        node.put("deploymentMode", service.getDeploymentMode());
        node.put("tenantMode", service.getTenantMode());
        node.put("environmentScope", service.getEnvironmentScope());
        node.put("deploymentId", service.getDeploymentId());
        node.put("desiredReplicas", service.getDesiredReplicas());
        node.put("minReplicas", service.getMinReplicas());
        node.put("maxReplicas", service.getMaxReplicas());
        node.put("healthPath", service.getHealthPath());
        node.put("serviceRoot", service.getServiceRoot());
        node.put("dockerfilePath", service.getDockerfilePath());
        node.put("secretName", service.getSecretName());
        node.put("status", service.getStatus());
        node.put("sourceBindingPresent", StringUtils.hasText(service.getBaseUrl()) || StringUtils.hasText(service.getPrivateNetworkUrl()));
        node.put("restorePolicy", "RECREATE_IN_TARGET_ENVIRONMENT");
        node.set("portableDetails", portableManagedProductServiceDetails(service.getDetailsJson()));
        node.put("createdAt", stringTime(service.getCreatedAt()));
        node.put("updatedAt", stringTime(service.getUpdatedAt()));
        return node;
    }

    private ObjectNode portableManagedProductServiceDetails(String detailsJson) {
        JsonNode source = readJson(detailsJson);
        ObjectNode portable = objectMapper.createObjectNode();
        if (!source.isObject()) {
            return portable;
        }
        source.fields().forEachRemaining(entry -> {
            String field = entry.getKey();
            if (field == null) {
                return;
            }
            String normalized = field.toLowerCase(Locale.ROOT);
            boolean providerBinding = normalized.startsWith("coolify")
                || normalized.startsWith("railway")
                || normalized.equals("targetprofileid")
                || normalized.equals("providertype")
                || normalized.startsWith("lastreconcile")
                || normalized.startsWith("lastdeployment")
                || normalized.startsWith("lastprobe")
                || normalized.startsWith("lastverified")
                || normalized.startsWith("drift");
            if (!providerBinding) {
                portable.set(field, entry.getValue());
            }
        });
        return portable;
    }

    private boolean hasMcpToolActions(JsonNode actionsConfig) {
        JsonNode actions = actionsConfig == null ? null : actionsConfig.path("actions");
        if (actions == null || !actions.isArray()) {
            return false;
        }
        for (JsonNode action : actions) {
            String adapterType = text(action, "adapterType");
            if ("mcp-tool".equalsIgnoreCase(adapterType)) {
                return true;
            }
            JsonNode execution = action.path("execution");
            if (execution.isObject()) {
                String executionAdapterType = text(execution, "adapterType");
                if ("mcp-tool".equalsIgnoreCase(executionAdapterType) || execution.path("mcp").isObject()) {
                    return true;
                }
            }
        }
        return false;
    }

    private SecretInventory collectSecretInventory(DeploymentEntity deployment, ObjectNode manifest) {
        Map<String, Set<String>> sources = new LinkedHashMap<>();
        platformSecretRepository.findByScopeTypeAndDeploymentIdOrderByUpdatedAtDesc(
                PlatformSecretScopeType.DEPLOYMENT_MANAGED,
                deployment.getId()
            )
            .forEach(secret -> addSource(sources, secret.getName(), "deployment-managed-secret"));

        secretBindingRepository.findByDeploymentIdOrderBySecretPurposeAsc(deployment.getId()).forEach(binding -> {
            addSource(sources, binding.getSecretName(), "provider-secret-binding:" + binding.getSecretPurpose());
            addSource(sources, binding.getSecondarySecretName(), "provider-secret-binding:" + binding.getSecretPurpose());
        });
        marketplacePluginInstallRepository.findByDeploymentIdOrderByUpdatedAtDesc(deployment.getId())
            .forEach(install -> collectSecretRefsFromJson(readJson(install.getSecretRefsJson()), sources, "marketplace-plugin:" + install.getPluginId()));
        managedVectorResourceRepository.findByDeploymentIdOrderByUpdatedAtDesc(deployment.getId())
            .forEach(resource -> collectSecretRefsFromJson(
                readJson(resource.getSecretReferenceNamesJson()),
                sources,
                "managed-vector-resource:" + resource.getResourceName()
            ));
        vectorizationSourceConnectionRepository.findByDeploymentId(deployment.getId())
            .ifPresent(connection -> collectSecretRefsFromJson(
                readJson(connection.getSecretReferencesJson()),
                sources,
                "vectorization-source-connection:" + connection.getName()
            ));
        collectSecretRefsFromJson(
            manifest.path("managedProductServiceDependencies"),
            sources,
            "managed-product-service-dependency"
        );
        collectDraftSecretRefsFromJson(manifest.path("activeDraft").path("configs"), sources, "active-draft-config");

        List<SecretInventoryItem> items = sources.entrySet().stream()
            .filter(entry -> StringUtils.hasText(entry.getKey()))
            .map(entry -> inventoryItem(deployment, entry.getKey(), entry.getValue()))
            .sorted(Comparator.comparing(SecretInventoryItem::secretName))
            .toList();
        return new SecretInventory(items);
    }

    private SecretInventoryItem inventoryItem(DeploymentEntity deployment, String secretName, Set<String> sources) {
        PlatformSecretEntity entity = platformSecretRepository.findById(secretName).orElse(null);
        SecretClassification classification = classifySecret(deployment, secretName, entity, sources);
        boolean valuePresent = entity != null && StringUtils.hasText(entity.getSecretValue());
        return new SecretInventoryItem(secretName, classification, valuePresent, List.copyOf(sources), entity);
    }

    private SecretClassification classifySecret(DeploymentEntity deployment,
                                                String secretName,
                                                PlatformSecretEntity entity,
                                                Set<String> sources) {
        String normalized = secretName == null ? "" : secretName.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return SecretClassification.MISSING_REFERENCE;
        }
        if (FORBIDDEN_SECRET_NAMES.contains(normalized)
            || normalized.contains("OAUTH")
            || normalized.contains("SESSION")
            || normalized.contains("CHECKOUT")
            || normalized.contains("TEMPORARY")
            || normalized.contains("ONE_TIME")
            || normalized.contains("IDEMPOTENCY")) {
            return SecretClassification.FORBIDDEN;
        }
        if (entity == null || !StringUtils.hasText(entity.getSecretValue())) {
            return SecretClassification.MISSING_REFERENCE;
        }
        boolean deploymentScoped = deployment.getId().equals(entity.getDeploymentId())
            || (entity.getScopeType() == PlatformSecretScopeType.DEPLOYMENT_MANAGED && deployment.getId().equals(entity.getDeploymentId()));
        boolean explicitlyBound = sources.stream().anyMatch(source -> source.startsWith("provider-secret-binding")
            || source.startsWith("marketplace-plugin")
            || source.startsWith("active-draft-config")
            || source.startsWith("managed-vector-resource")
            || source.startsWith("managed-product-service")
            || source.startsWith("vectorization-source-connection"));
        if (deploymentScoped || explicitlyBound && !SHARED_PROVIDER_SECRETS.contains(normalized)) {
            return SecretClassification.SEALED_EXPORTABLE;
        }
        if (SHARED_PROVIDER_SECRETS.contains(normalized) || ENVIRONMENT_BOUND_PREFIXES.stream().anyMatch(normalized::startsWith)) {
            return SecretClassification.ENVIRONMENT_BOUND;
        }
        if (normalized.contains("PASSWORD") || normalized.contains("TOKEN") || normalized.contains("SECRET") || normalized.contains("API_KEY")) {
            return SecretClassification.REGENERATE_RECOMMENDED;
        }
        return SecretClassification.ENVIRONMENT_BOUND;
    }

    private DeploymentBundleSecretSummary secretSummary(SecretInventory inventory, boolean includeValues) {
        List<DeploymentBundleSecretInventoryItem> items = inventory.items().stream()
            .map(item -> new DeploymentBundleSecretInventoryItem(
                item.secretName(),
                item.classification(),
                item.valuePresent(),
                includeValues && item.classification() == SecretClassification.SEALED_EXPORTABLE && item.valuePresent(),
                restorePolicy(item.classification()),
                item.sources()
            ))
            .toList();
        return new DeploymentBundleSecretSummary(
            (int) items.stream().filter(DeploymentBundleSecretInventoryItem::valueIncluded).count(),
            (int) items.stream().filter(item -> item.classification() == SecretClassification.SEALED_EXPORTABLE).count(),
            (int) items.stream().filter(item -> item.classification() == SecretClassification.REGENERATE_RECOMMENDED).count(),
            (int) items.stream().filter(item -> item.classification() == SecretClassification.FORBIDDEN).count(),
            (int) items.stream().filter(item -> item.classification() == SecretClassification.ENVIRONMENT_BOUND).count(),
            (int) items.stream().filter(item -> item.classification() == SecretClassification.MISSING_REFERENCE).count(),
            items
        );
    }

    private ObjectNode secretPayload(DeploymentEntity deployment, SecretInventory inventory) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("schemaVersion", "loomai.deployment-secrets.v1");
        payload.put("sourceDeploymentId", deployment.getId());
        payload.put("createdAt", Instant.now().toString());
        ArrayNode secrets = payload.putArray("secrets");
        inventory.items().stream()
            .filter(item -> item.classification() == SecretClassification.SEALED_EXPORTABLE)
            .filter(item -> item.entity() != null && StringUtils.hasText(item.entity().getSecretValue()))
            .forEach(item -> {
                ObjectNode secret = objectMapper.createObjectNode();
                secret.put("secretName", item.secretName());
                secret.put("value", item.entity().getSecretValue());
                secret.put("classification", item.classification().name());
                secret.put("restorePolicy", restorePolicy(item.classification()));
                secret.set("sources", objectMapper.valueToTree(item.sources()));
                ObjectNode metadata = secret.putObject("metadata");
                metadata.put("scopeType", item.entity().getScopeType() == null ? null : item.entity().getScopeType().name());
                metadata.put("deploymentId", item.entity().getDeploymentId());
                metadata.put("secretPurpose", item.entity().getSecretPurpose());
                metadata.put("ownerType", item.entity().getOwnerType() == null ? null : item.entity().getOwnerType().name());
                metadata.put("managedByPlatform", item.entity().isManagedByPlatform());
                metadata.put("cleanupPolicy", item.entity().getCleanupPolicy() == null ? null : item.entity().getCleanupPolicy().name());
                secrets.add(secret);
            });
        return payload;
    }

    private String restorePolicy(SecretClassification classification) {
        return switch (classification) {
            case SEALED_EXPORTABLE -> "PRESERVE_FOR_RESTORE_IN_PLACE";
            case REGENERATE_RECOMMENDED -> "SUPPLY_OR_REGENERATE";
            case FORBIDDEN -> "DO_NOT_EXPORT";
            case ENVIRONMENT_BOUND -> "REMAP_OR_SUPPLY_IN_TARGET_ENVIRONMENT";
            case MISSING_REFERENCE -> "SUPPLY_BEFORE_APPLY";
        };
    }

    private ImportValidation validateBundle(JsonNode bundle, ImportMode importMode, String targetDeploymentId) {
        List<String> blocking = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        boolean schemaValid = SCHEMA_VERSION.equals(bundle.path("schemaVersion").asText(null));
        if (!schemaValid) {
            blocking.add("BUNDLE_SCHEMA_UNSUPPORTED");
        }
        JsonNode manifest = bundle.path("manifest");
        if (!manifest.isObject()) {
            blocking.add("BUNDLE_MANIFEST_MISSING");
        }
        String expectedManifestHash = bundle.path("integrity").path("manifestHash").asText(null);
        boolean integrityValid = StringUtils.hasText(expectedManifestHash)
            && expectedManifestHash.equals(sealingService.sha256(manifest));
        if (!integrityValid) {
            blocking.add("BUNDLE_INTEGRITY_FAILED");
        }
        if (importMode == ImportMode.SEALED_CLONE && !bundle.hasNonNull("secretEnvelope")) {
            blocking.add("BUNDLE_SEALED_SECRET_ENVELOPE_MISSING");
        }
        if (importMode == ImportMode.RESTORE_IN_PLACE) {
            if (!StringUtils.hasText(targetDeploymentId)) {
                blocking.add("IMPORT_RESTORE_TARGET_REQUIRED");
            } else if (!sourceDeploymentId(bundle).equals(targetDeploymentId.trim())) {
                blocking.add("IMPORT_RESTORE_IN_PLACE_ID_MISMATCH");
            } else if (deploymentRepository.findById(targetDeploymentId.trim()).isEmpty()) {
                blocking.add("IMPORT_RESTORE_TARGET_MISSING");
            }
        }
        if (bundle.path("secretInventory").isArray()
            && importMode != ImportMode.SEALED_CLONE
            && importMode != ImportMode.RESTORE_IN_PLACE) {
            warnings.add("Config-only imports do not restore sealed secrets.");
        }
        if (manifest.path("activeDraft").path("configs").isMissingNode()) {
            blocking.add("BUNDLE_ACTIVE_DRAFT_CONFIG_MISSING");
        }
        validateMarketplaceCatalogAvailability(manifest, blocking, warnings);
        return new ImportValidation(schemaValid, integrityValid, blocking, warnings);
    }

    private void validateMarketplaceCatalogAvailability(JsonNode manifest,
                                                        List<String> blocking,
                                                        List<String> warnings) {
        JsonNode installs = manifest.path("marketplaceInstalls");
        if (!installs.isArray() || installs.isEmpty()) {
            return;
        }
        JsonNode catalog = manifest.path("marketplaceCatalog");
        Set<String> bundledPluginIds = catalog.path("plugins").isArray()
            ? idsIn(catalog.path("plugins"))
            : Set.of();
        Set<String> bundledVersionIds = catalog.path("versions").isArray()
            ? idsIn(catalog.path("versions"))
            : Set.of();
        Set<String> bundledDatasetKeys = catalog.path("datasets").isArray()
            ? marketplaceDatasetKeysIn(catalog.path("datasets"))
            : Set.of();
        Map<String, String> installVersions = marketplaceInstallVersionIds(installs);

        for (JsonNode install : installs) {
            String pluginId = install.path("pluginId").asText(null);
            if (StringUtils.hasText(pluginId)
                && !bundledPluginIds.contains(pluginId)
                && marketplacePluginRepository.findById(pluginId).isEmpty()) {
                blocking.add("MARKETPLACE_PLUGIN_MISSING: " + pluginId);
            }
            String versionId = install.path("pluginVersionId").asText(null);
            if (StringUtils.hasText(versionId)
                && !bundledVersionIds.contains(versionId)
                && marketplacePluginVersionRepository.findById(versionId).isEmpty()) {
                blocking.add("MARKETPLACE_PLUGIN_VERSION_MISSING: " + versionId);
            }
        }
        JsonNode configuredDatasets = manifest.path("activeDraft").path("configs").path("marketplaceDataset").path("datasets");
        if (configuredDatasets.isArray()) {
            for (JsonNode dataset : configuredDatasets) {
                if (!dataset.path("marketplaceManaged").asBoolean(false)) {
                    continue;
                }
                String installId = dataset.path("marketplaceInstallId").asText(null);
                String datasetId = dataset.path("datasetId").asText(null);
                String versionId = StringUtils.hasText(installId) ? installVersions.get(installId) : null;
                if (!StringUtils.hasText(versionId) || !StringUtils.hasText(datasetId)) {
                    continue;
                }
                String datasetKey = versionId + "/" + datasetId;
                if (!bundledDatasetKeys.contains(datasetKey)
                    && marketplacePluginDatasetRepository.findByPluginVersionIdAndDatasetId(versionId, datasetId).isEmpty()) {
                    blocking.add("MARKETPLACE_PLUGIN_DATASET_MISSING: " + datasetKey);
                }
            }
        }
        if (catalog.isMissingNode() || !catalog.isObject()) {
            warnings.add("Bundle does not include portable Marketplace catalog rows; target must already have referenced plugins.");
        }
    }

    private Map<String, String> marketplaceInstallVersionIds(JsonNode installs) {
        Map<String, String> versionIds = new LinkedHashMap<>();
        if (installs != null && installs.isArray()) {
            installs.forEach(install -> {
                String installId = install.path("id").asText(null);
                String versionId = install.path("pluginVersionId").asText(null);
                if (StringUtils.hasText(installId) && StringUtils.hasText(versionId)) {
                    versionIds.put(installId.trim(), versionId.trim());
                }
            });
        }
        return versionIds;
    }

    private Set<String> marketplaceDatasetKeysIn(JsonNode array) {
        Set<String> keys = new LinkedHashSet<>();
        if (array != null && array.isArray()) {
            array.forEach(item -> {
                String versionId = item.path("pluginVersionId").asText(null);
                String datasetId = item.path("datasetId").asText(null);
                if (StringUtils.hasText(versionId) && StringUtils.hasText(datasetId)) {
                    keys.add(versionId.trim() + "/" + datasetId.trim());
                }
            });
        }
        return keys;
    }

    private Set<String> idsIn(JsonNode array) {
        Set<String> ids = new LinkedHashSet<>();
        if (array != null && array.isArray()) {
            array.forEach(item -> {
                String id = item.path("id").asText(null);
                if (StringUtils.hasText(id)) {
                    ids.add(id.trim());
                }
            });
        }
        return ids;
    }

    private RestoreResult cloneAsNew(JsonNode bundle,
                                     DeploymentImportRequest request,
                                     JsonNode decryptedSecrets,
                                     boolean restoreSecrets) {
        MarketplaceCatalogRestore marketplaceCatalogRestore = restoreMarketplaceCatalog(bundle);
        JsonNode deployment = bundle.path("manifest").path("deployment");
        String name = resolvedNewName(request, bundle);
        String environment = firstNonBlank(
            request == null ? null : request.targetEnvironment(),
            deployment.path("environmentName").asText("imported")
        );
        String customerId = firstNonBlank(request == null ? null : request.targetCustomerId(), deployment.path("customerId").asText(null));
        String tenantId = request == null ? null : request.targetTenantId();
        var created = deploymentService.createDeployment(new CreateDeploymentRequest(
            name,
            environment,
            deployment.path("templateId").asText(),
            curatedModuleId(bundle),
            vectorProvisioningMode(bundle),
            customerId,
            tenantId
        ));
        DeploymentEntity createdDeployment = deploymentRepository.findById(created.id())
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Imported deployment was not created."));
        DeploymentDraftEntity draft = activeDraft(createdDeployment);
        ImportConfigRewrite rewrite = restoreMarketplacePluginInstalls(createdDeployment, bundle, marketplaceCatalogRestore);
        overwriteDraftFromBundle(draft, bundle, "IMPORTED", rewrite);
        if (restoreSecrets && decryptedSecrets != null) {
            restoreSecrets(createdDeployment.getId(), decryptedSecrets);
        }
        restoreManagedProductServiceDependencies(createdDeployment, bundle, request == null ? null : request.targetProfileId());
        restoreVectorizationControlPlane(createdDeployment, bundle);
        return new RestoreResult(createdDeployment.getId(), draft.getId());
    }

    private RestoreResult restoreInPlace(JsonNode bundle,
                                         DeploymentImportRequest request,
                                         JsonNode decryptedSecrets) {
        MarketplaceCatalogRestore marketplaceCatalogRestore = restoreMarketplaceCatalog(bundle);
        String targetDeploymentId = request == null ? null : request.targetDeploymentId();
        DeploymentEntity deployment = requireDeploymentAdmin(targetDeploymentId);
        DeploymentDraftEntity latestDraft = latestDraft(deployment.getId());
        Instant now = Instant.now();
        DeploymentDraftEntity restoredDraft = new DeploymentDraftEntity();
        restoredDraft.setId(generateId("drf"));
        restoredDraft.setDeploymentId(deployment.getId());
        restoredDraft.setRevisionNumber(latestDraft.getRevisionNumber() + 1);
        restoredDraft.setStatus("IMPORTED_RESTORE_DRAFT");
        ImportConfigRewrite rewrite = restoreMarketplacePluginInstalls(deployment, bundle, marketplaceCatalogRestore);
        copyConfigsFromBundle(restoredDraft, bundle, rewrite);
        restoredDraft.setCreatedAt(now);
        restoredDraft.setUpdatedAt(now);
        draftRepository.save(restoredDraft);

        deployment.setActiveDraftId(restoredDraft.getId());
        deployment.setStatus("DRAFT");
        deployment.setUpdatedAt(now);
        deploymentRepository.save(deployment);

        if (decryptedSecrets != null) {
            restoreSecrets(deployment.getId(), decryptedSecrets);
        }
        restoreManagedProductServiceDependencies(deployment, bundle, request == null ? null : request.targetProfileId());
        restoreVectorizationControlPlane(deployment, bundle);
        return new RestoreResult(deployment.getId(), restoredDraft.getId());
    }

    private void overwriteDraftFromBundle(DeploymentDraftEntity draft, JsonNode bundle, String status, ImportConfigRewrite rewrite) {
        copyConfigsFromBundle(draft, bundle, rewrite);
        draft.setStatus(status);
        draft.setUpdatedAt(Instant.now());
        draftRepository.save(draft);
        DeploymentEntity deployment = deploymentRepository.findById(draft.getDeploymentId())
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment not found: " + draft.getDeploymentId()));
        deployment.setStatus("DRAFT");
        deployment.setUpdatedAt(draft.getUpdatedAt());
        deploymentRepository.save(deployment);
    }

    private void copyConfigsFromBundle(DeploymentDraftEntity draft, JsonNode bundle, ImportConfigRewrite rewrite) {
        JsonNode configs = bundle.path("manifest").path("activeDraft").path("configs");
        draft.setActionsConfigJson(writeJson(rewrittenConfig(requiredConfig(configs, "actions"), rewrite)));
        draft.setEntityConfigJson(writeJson(rewrittenConfig(requiredConfig(configs, "entities"), rewrite)));
        draft.setRoutingConfigJson(writeJson(rewrittenConfig(requiredConfig(configs, "routing"), rewrite)));
        draft.setProviderConfigJson(writeJson(rewrittenConfig(requiredConfig(configs, "provider"), rewrite)));
        draft.setSecurityConfigJson(writeJson(rewrittenConfig(requiredConfig(configs, "security"), rewrite)));
        draft.setPromptConfigJson(writeJson(rewrittenConfig(requiredConfig(configs, "prompt"), rewrite)));
        draft.setKnowledgeSourceConfigJson(writeJson(rewrittenConfig(requiredConfig(configs, "knowledgeSource"), rewrite)));
        draft.setShellConfigJson(writeJson(rewrittenConfig(requiredConfig(configs, "shell"), rewrite)));
        draft.setMarketplaceDatasetConfigJson(writeJson(rewrittenConfig(requiredConfig(configs, "marketplaceDataset"), rewrite)));
    }

    private MarketplaceCatalogRestore restoreMarketplaceCatalog(JsonNode bundle) {
        JsonNode catalog = bundle.path("manifest").path("marketplaceCatalog");
        if (!catalog.isObject()) {
            return MarketplaceCatalogRestore.empty();
        }
        Map<String, String> pluginIdRemap = new LinkedHashMap<>();
        Map<String, String> versionIdRemap = new LinkedHashMap<>();
        JsonNode plugins = catalog.path("plugins");
        if (plugins.isArray()) {
            for (JsonNode pluginNode : plugins) {
                if (!pluginNode.isObject()) {
                    continue;
                }
                String sourcePluginId = text(pluginNode, "id");
                String slug = text(pluginNode, "slug");
                if (!StringUtils.hasText(sourcePluginId) || !StringUtils.hasText(slug)) {
                    continue;
                }
                MarketplacePluginEntity plugin = marketplacePluginRepository.findById(sourcePluginId)
                    .filter(existing -> slug.equalsIgnoreCase(existing.getSlug()))
                    .or(() -> marketplacePluginRepository.findBySlugIgnoreCase(slug))
                    .orElseGet(MarketplacePluginEntity::new);
                boolean existing = StringUtils.hasText(plugin.getId());
                plugin.setId(existing ? plugin.getId() : sourcePluginId);
                plugin.setSlug(slug);
                plugin.setDisplayName(firstNonBlank(pluginNode.path("displayName").asText(null), slug));
                plugin.setPluginType(firstNonBlank(pluginNode.path("pluginType").asText(null), "ACTION"));
                plugin.setPublisherSlug(firstNonBlank(pluginNode.path("publisherSlug").asText(null), "imported"));
                plugin.setPublisherDisplayName(firstNonBlank(pluginNode.path("publisherDisplayName").asText(null), "Imported"));
                plugin.setShortDescription(firstNonBlank(pluginNode.path("shortDescription").asText(null), "Imported Marketplace plugin."));
                plugin.setStatus(firstNonBlank(pluginNode.path("status").asText(null), "PUBLISHED"));
                if (plugin.getCreatedAt() == null) {
                    plugin.setCreatedAt(timeOrNow(pluginNode.path("createdAt").asText(null)));
                }
                plugin.setUpdatedAt(Instant.now());
                marketplacePluginRepository.save(plugin);
                pluginIdRemap.put(sourcePluginId, plugin.getId());
            }
        }

        JsonNode versions = catalog.path("versions");
        if (versions.isArray()) {
            for (JsonNode versionNode : versions) {
                if (!versionNode.isObject()) {
                    continue;
                }
                String sourceVersionId = text(versionNode, "id");
                String sourcePluginId = text(versionNode, "pluginId");
                String versionLabel = text(versionNode, "version");
                if (!StringUtils.hasText(sourceVersionId)
                    || !StringUtils.hasText(sourcePluginId)
                    || !StringUtils.hasText(versionLabel)) {
                    continue;
                }
                String targetPluginId = pluginIdRemap.getOrDefault(sourcePluginId, sourcePluginId);
                MarketplacePluginVersionEntity version = marketplacePluginVersionRepository.findById(sourceVersionId)
                    .filter(existing -> targetPluginId.equals(existing.getPluginId())
                        && versionLabel.equalsIgnoreCase(existing.getVersion()))
                    .or(() -> marketplacePluginVersionRepository.findByPluginIdAndVersion(targetPluginId, versionLabel))
                    .orElseGet(MarketplacePluginVersionEntity::new);
                boolean existing = StringUtils.hasText(version.getId());
                version.setId(existing ? version.getId() : sourceVersionId);
                version.setPluginId(targetPluginId);
                version.setVersion(versionLabel);
                version.setReleaseChannel(firstNonBlank(versionNode.path("releaseChannel").asText(null), "stable"));
                version.setStatus(firstNonBlank(versionNode.path("status").asText(null), "PUBLISHED"));
                version.setManifestJson(writeJson(versionNode.path("manifest")));
                version.setSubmittedByPublisherId(versionNode.path("submittedByPublisherId").asText(null));
                version.setSubmittedByActorId(versionNode.path("submittedByActorId").asText(null));
                version.setReviewedByActorId(versionNode.path("reviewedByActorId").asText(null));
                version.setReviewedAt(timeOrNull(versionNode.path("reviewedAt").asText(null)));
                version.setReviewNotes(versionNode.path("reviewNotes").asText(null));
                version.setBundleSha256(versionNode.path("bundleSha256").asText(null));
                if (version.getCreatedAt() == null) {
                    version.setCreatedAt(timeOrNow(versionNode.path("createdAt").asText(null)));
                }
                if (version.getPublishedAt() == null) {
                    version.setPublishedAt(timeOrNow(versionNode.path("publishedAt").asText(null)));
                }
                marketplacePluginVersionRepository.save(version);
                versionIdRemap.put(sourceVersionId, version.getId());
            }
        }
        restoreMarketplacePluginDatasets(catalog.path("datasets"), pluginIdRemap, versionIdRemap);
        return new MarketplaceCatalogRestore(pluginIdRemap, versionIdRemap);
    }

    private void restoreMarketplacePluginDatasets(JsonNode datasets,
                                                  Map<String, String> pluginIdRemap,
                                                  Map<String, String> versionIdRemap) {
        if (!datasets.isArray()) {
            return;
        }
        Instant now = Instant.now();
        for (JsonNode datasetNode : datasets) {
            if (!datasetNode.isObject()) {
                continue;
            }
            String sourcePluginId = text(datasetNode, "pluginId");
            String sourceVersionId = text(datasetNode, "pluginVersionId");
            String datasetId = text(datasetNode, "datasetId");
            if (!StringUtils.hasText(sourcePluginId)
                || !StringUtils.hasText(sourceVersionId)
                || !StringUtils.hasText(datasetId)) {
                continue;
            }
            String targetPluginId = pluginIdRemap.getOrDefault(sourcePluginId, sourcePluginId);
            String targetVersionId = versionIdRemap.getOrDefault(sourceVersionId, sourceVersionId);
            MarketplacePluginDatasetEntity dataset = marketplacePluginDatasetRepository
                .findByPluginVersionIdAndDatasetId(targetVersionId, datasetId)
                .orElseGet(MarketplacePluginDatasetEntity::new);
            boolean existing = StringUtils.hasText(dataset.getId());
            dataset.setId(existing ? dataset.getId() : firstNonBlank(text(datasetNode, "id"), generateId("mpd")));
            dataset.setPluginId(targetPluginId);
            dataset.setPluginVersionId(targetVersionId);
            dataset.setDatasetId(datasetId);
            dataset.setEntityType(firstNonBlank(datasetNode.path("entityType").asText(null), datasetId));
            dataset.setStorageScope(firstNonBlank(datasetNode.path("storageScope").asText(null), "PLUGIN_SCOPED"));
            dataset.setSharingScope(firstNonBlank(datasetNode.path("sharingScope").asText(null), "TENANT_SHARED"));
            dataset.setIngestionMode(firstNonBlank(datasetNode.path("ingestionMode").asText(null), "EXTERNAL_SYNC_FOLDER"));
            dataset.setUpdateStrategy(firstNonBlank(datasetNode.path("updateStrategy").asText(null), "UPSERT_BY_ID"));
            dataset.setVectorizationProfile(datasetNode.path("vectorizationProfile").asText(null));
            dataset.setHandleTemplate(datasetNode.path("handleTemplate").asText(null));
            dataset.setSeedDatasetRef(datasetNode.path("seedDatasetRef").asText(null));
            dataset.setConnectorType(datasetNode.path("connectorType").asText(null));
            dataset.setConnectorConfigJson(writeJson(
                datasetNode.path("connectorConfig").isMissingNode() ? objectMapper.createObjectNode() : datasetNode.path("connectorConfig")
            ));
            dataset.setDatasetHash(firstNonBlank(datasetNode.path("datasetHash").asText(null), "imported-" + datasetId));
            if (dataset.getCreatedAt() == null) {
                dataset.setCreatedAt(timeOrNow(datasetNode.path("createdAt").asText(null)));
            }
            dataset.setUpdatedAt(now);
            marketplacePluginDatasetRepository.save(dataset);
        }
    }

    private ImportConfigRewrite restoreMarketplacePluginInstalls(DeploymentEntity deployment,
                                                                 JsonNode bundle,
                                                                 MarketplaceCatalogRestore marketplaceCatalogRestore) {
        JsonNode installs = bundle.path("manifest").path("marketplaceInstalls");
        Map<String, String> installIdRemap = new LinkedHashMap<>();
        if (!installs.isArray()) {
            return ImportConfigRewrite.from(deployment, bundle, installIdRemap, marketplaceCatalogRestore);
        }
        Instant now = Instant.now();
        List<PendingMarketplaceInstallRestore> pendingInstalls = new ArrayList<>();
        for (JsonNode installNode : installs) {
            if (!installNode.isObject()) {
                continue;
            }
            String sourceInstallId = text(installNode, "id");
            String pluginId = text(installNode, "pluginId");
            String pluginVersionId = text(installNode, "pluginVersionId");
            if (!StringUtils.hasText(sourceInstallId)
                || !StringUtils.hasText(pluginId)
                || !StringUtils.hasText(pluginVersionId)) {
                continue;
            }
            String targetPluginId = marketplaceCatalogRestore.pluginId(pluginId);
            String targetPluginVersionId = marketplaceCatalogRestore.versionId(pluginVersionId);
            DeploymentMarketplacePluginInstallEntity install = marketplacePluginInstallRepository
                .findByDeploymentIdAndPluginIdAndPluginVersionId(deployment.getId(), targetPluginId, targetPluginVersionId)
                .or(() -> marketplacePluginInstallRepository.findByDeploymentIdAndPluginId(deployment.getId(), targetPluginId))
                .orElseGet(DeploymentMarketplacePluginInstallEntity::new);
            boolean existing = StringUtils.hasText(install.getId());
            install.setId(existing ? install.getId() : generateId("mpi"));
            installIdRemap.put(sourceInstallId, install.getId());
            install.setDeploymentId(deployment.getId());
            install.setPluginId(targetPluginId);
            install.setPluginVersionId(targetPluginVersionId);
            install.setStatus(firstNonBlank(installNode.path("status").asText(null), "ENABLED"));
            if (install.getCreatedAt() == null) {
                install.setCreatedAt(now);
            }
            install.setUpdatedAt(now);
            pendingInstalls.add(new PendingMarketplaceInstallRestore(
                install,
                installNode.path("config").isMissingNode() ? objectMapper.createObjectNode() : installNode.path("config"),
                installNode.path("secretRefs").isMissingNode() ? objectMapper.createObjectNode() : installNode.path("secretRefs")
            ));
        }
        ImportConfigRewrite rewrite = ImportConfigRewrite.from(deployment, bundle, installIdRemap, marketplaceCatalogRestore);
        for (PendingMarketplaceInstallRestore pendingInstall : pendingInstalls) {
            DeploymentMarketplacePluginInstallEntity install = pendingInstall.install();
            install.setConfigJson(writeJson(rewrittenConfig(pendingInstall.config(), rewrite)));
            install.setSecretRefsJson(writeJson(rewrittenConfig(pendingInstall.secretRefs(), rewrite)));
            marketplacePluginInstallRepository.save(install);
        }
        return rewrite;
    }

    private JsonNode rewrittenConfig(JsonNode source, ImportConfigRewrite rewrite) {
        JsonNode copy = source == null ? objectMapper.createObjectNode() : source.deepCopy();
        if (rewrite == null || rewrite.isEmpty()) {
            return copy;
        }
        return rewriteConfigNode(copy, rewrite);
    }

    private JsonNode rewriteConfigNode(JsonNode node, ImportConfigRewrite rewrite) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return node;
        }
        if (node.isTextual()) {
            String rewritten = rewriteConfigText(node.asText(), rewrite);
            return rewritten.equals(node.asText()) ? node : TextNode.valueOf(rewritten);
        }
        if (node instanceof ObjectNode objectNode) {
            List<String> fieldNames = new ArrayList<>();
            objectNode.fieldNames().forEachRemaining(fieldNames::add);
            for (String fieldName : fieldNames) {
                objectNode.set(fieldName, rewriteConfigNode(objectNode.path(fieldName), rewrite));
            }
            return objectNode;
        }
        if (node instanceof ArrayNode arrayNode) {
            for (int index = 0; index < arrayNode.size(); index += 1) {
                arrayNode.set(index, rewriteConfigNode(arrayNode.get(index), rewrite));
            }
        }
        return node;
    }

    private String rewriteConfigText(String value, ImportConfigRewrite rewrite) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String remappedInstallId = rewrite.installIdRemap().get(value);
        if (StringUtils.hasText(remappedInstallId)) {
            return remappedInstallId;
        }
        String remappedPluginId = rewrite.pluginIdRemap().get(value);
        if (StringUtils.hasText(remappedPluginId)) {
            return remappedPluginId;
        }
        String remappedVersionId = rewrite.versionIdRemap().get(value);
        if (StringUtils.hasText(remappedVersionId)) {
            return remappedVersionId;
        }
        String rewritten = value;
        if (StringUtils.hasText(rewrite.sourceTenantId()) && StringUtils.hasText(rewrite.targetTenantId())) {
            rewritten = rewritten.replace("/tenant/" + rewrite.sourceTenantId() + "/", "/tenant/" + rewrite.targetTenantId() + "/");
        }
        if (StringUtils.hasText(rewrite.sourceCustomerId()) && StringUtils.hasText(rewrite.targetCustomerId())) {
            rewritten = rewritten.replace("/customer/" + rewrite.sourceCustomerId() + "/", "/customer/" + rewrite.targetCustomerId() + "/");
        }
        return rewritten;
    }

    private JsonNode requiredConfig(JsonNode configs, String name) {
        JsonNode value = configs.path(name);
        if (!value.isObject() && !value.isArray()) {
            throw new ResponseStatusException(BAD_REQUEST, "Bundle active draft is missing config section: " + name);
        }
        return value;
    }

    private void restoreSecrets(String deploymentId, JsonNode decryptedSecrets) {
        JsonNode secrets = decryptedSecrets.path("secrets");
        if (!secrets.isArray()) {
            throw new ResponseStatusException(BAD_REQUEST, "Decrypted secret payload is missing secrets array.");
        }
        for (JsonNode secret : secrets) {
            String name = secret.path("secretName").asText(null);
            String value = secret.path("value").asText(null);
            if (!StringUtils.hasText(name) || !StringUtils.hasText(value)) {
                continue;
            }
            PlatformSecretEntity entity = platformSecretRepository.findById(name).orElseGet(PlatformSecretEntity::new);
            entity.setName(name);
            entity.setSecretValue(value.trim());
            entity.setUpdatedAt(Instant.now());
            boolean managedProductServiceSecret = secretSources(secret).stream()
                .anyMatch(source -> source.startsWith("managed-product-service"));
            entity.setScopeType(scopeType(secret, PlatformSecretScopeType.DEPLOYMENT_MANAGED));
            entity.setDeploymentId(managedProductServiceSecret ? null : deploymentId);
            entity.setSecretPurpose(firstNonBlank(
                secret.path("metadata").path("secretPurpose").asText(null),
                managedProductServiceSecret ? "PRODUCT_SERVICE_SECRET" : name
            ));
            entity.setOwnerType(ownerType(secret, PlatformSecretOwnerType.PLATFORM_MANAGED));
            entity.setManagedByPlatform(true);
            entity.setCleanupPolicy(cleanupPolicy(secret, PlatformSecretCleanupPolicy.DELETE_ON_HARD_DELETE));
            platformSecretRepository.save(entity);
        }
    }

    private void restoreManagedProductServiceDependencies(DeploymentEntity deployment, JsonNode bundle, String targetProfileId) {
        JsonNode services = bundle.path("manifest").path("managedProductServiceDependencies");
        if (!services.isArray()) {
            return;
        }
        Instant now = Instant.now();
        for (JsonNode serviceNode : services) {
            if (!serviceNode.isObject()) {
                continue;
            }
            String serviceRef = serviceNode.path("serviceRef").asText(null);
            if (!StringUtils.hasText(serviceRef)) {
                continue;
            }
            PlatformManagedProductServiceEntity service = managedProductServiceRepository
                .findByServiceRefIgnoreCase(serviceRef)
                .orElseGet(PlatformManagedProductServiceEntity::new);
            boolean existing = StringUtils.hasText(service.getId());
            service.setId(existing ? service.getId() : generateId("psv"));
            service.setServiceRef(serviceRef.trim());
            service.setDisplayName(firstNonBlank(serviceNode.path("displayName").asText(null), serviceRef));
            service.setProductFamily(firstNonBlank(serviceNode.path("productFamily").asText(null), "PLATFORM"));
            service.setServiceKind(firstNonBlank(serviceNode.path("serviceKind").asText(null), "MCP_EXECUTION_GATEWAY_SERVICE"));
            service.setDeploymentMode(firstNonBlank(serviceNode.path("deploymentMode").asText(null), "SHARED"));
            service.setTenantMode(firstNonBlank(serviceNode.path("tenantMode").asText(null), "MULTI_TENANT"));
            service.setEnvironmentScope(firstNonBlank(deployment.getEnvironmentName(), serviceNode.path("environmentScope").asText(null)));
            service.setDeploymentId(null);
            service.setDesiredReplicas(intOrNull(serviceNode, "desiredReplicas", existing ? service.getDesiredReplicas() : 1));
            service.setMinReplicas(intOrNull(serviceNode, "minReplicas", existing ? service.getMinReplicas() : 1));
            service.setMaxReplicas(intOrNull(serviceNode, "maxReplicas", existing ? service.getMaxReplicas() : 3));
            service.setHealthPath(firstNonBlank(serviceNode.path("healthPath").asText(null), "/actuator/health"));
            service.setServiceRoot(serviceNode.path("serviceRoot").asText(null));
            service.setDockerfilePath(serviceNode.path("dockerfilePath").asText(null));
            service.setSecretName(serviceNode.path("secretName").asText(null));

            boolean existingActiveBinding = existing && StringUtils.hasText(service.getBaseUrl());
            if (!existingActiveBinding) {
                service.setBaseUrl(null);
                service.setPrivateNetworkUrl(null);
                service.setRailwayProjectId(null);
                service.setRailwayEnvironmentId(null);
                service.setRailwayServiceId(null);
                service.setActualReplicas(0);
                service.setStatus("CREATED");
            }
            service.setDetailsJson(importedManagedProductServiceDetails(serviceNode, targetProfileId).toPrettyString());
            if (service.getCreatedAt() == null) {
                service.setCreatedAt(now);
            }
            service.setUpdatedAt(now);
            managedProductServiceRepository.save(service);
        }
    }

    private ObjectNode importedManagedProductServiceDetails(JsonNode serviceNode, String targetProfileId) {
        ObjectNode details = objectMapper.createObjectNode();
        JsonNode portable = serviceNode.path("portableDetails");
        if (portable.isObject()) {
            portable.fields().forEachRemaining(entry -> details.set(entry.getKey(), entry.getValue()));
        }
        details.put("providerType", "COOLIFY");
        String normalizedTargetProfile = firstNonBlank(targetProfileId, null);
        if (normalizedTargetProfile != null) {
            details.put("targetProfileId", normalizedTargetProfile);
        }
        details.put("importedManagedProductDependency", true);
        details.put("importedAt", Instant.now().toString());
        details.put("restorePolicy", serviceNode.path("restorePolicy").asText("RECREATE_IN_TARGET_ENVIRONMENT"));
        return details;
    }

    private List<String> secretSources(JsonNode secret) {
        JsonNode sources = secret.path("sources");
        if (!sources.isArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        sources.forEach(source -> {
            if (source.isTextual() && StringUtils.hasText(source.asText())) {
                result.add(source.asText());
            }
        });
        return result;
    }

    private PlatformSecretScopeType scopeType(JsonNode secret, PlatformSecretScopeType fallback) {
        String value = secret.path("metadata").path("scopeType").asText(null);
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        try {
            return PlatformSecretScopeType.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private PlatformSecretOwnerType ownerType(JsonNode secret, PlatformSecretOwnerType fallback) {
        String value = secret.path("metadata").path("ownerType").asText(null);
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        try {
            return PlatformSecretOwnerType.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private PlatformSecretCleanupPolicy cleanupPolicy(JsonNode secret, PlatformSecretCleanupPolicy fallback) {
        String value = secret.path("metadata").path("cleanupPolicy").asText(null);
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        try {
            return PlatformSecretCleanupPolicy.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private void restoreVectorizationControlPlane(DeploymentEntity deployment, JsonNode bundle) {
        JsonNode vectorization = bundle.path("manifest").path("vectorizationControlPlane");
        if (!vectorization.isObject()) {
            return;
        }
        boolean hasControlPlane = vectorization.path("plan").isObject()
            || vectorization.path("sourceConnection").isObject()
            || vectorization.path("revisions").isArray() && vectorization.path("revisions").size() > 0;
        if (!hasControlPlane) {
            return;
        }

        String deploymentId = deployment.getId();
        vectorizationPlanRevisionRepository.deleteByDeploymentId(deploymentId);
        vectorizationSourceConnectionRepository.deleteByDeploymentId(deploymentId);

        Instant now = Instant.now();
        String oldSourceConnectionId = vectorization.path("sourceConnection").path("id").asText(null);
        String newSourceConnectionId = null;
        if (vectorization.path("sourceConnection").isObject()) {
            newSourceConnectionId = generateId("vcn");
            VectorizationSourceConnectionEntity connection = new VectorizationSourceConnectionEntity();
            JsonNode source = vectorization.path("sourceConnection");
            connection.setId(newSourceConnectionId);
            connection.setDeploymentId(deploymentId);
            connection.setCustomerId(importedOwnerValue(deployment.getCustomerId(), source.path("customerId").asText(null), "customer"));
            connection.setTenantId(importedOwnerValue(deployment.getTenantId(), source.path("tenantId").asText(null), "tenant"));
            connection.setName(source.path("name").asText("Imported vectorization source"));
            connection.setAdapterType(source.path("adapterType").asText("REST_API"));
            connection.setAuthMode(source.path("authMode").asText("NONE"));
            connection.setStatus(source.path("status").asText("ACTIVE"));
            connection.setConnectionConfigJson(writeJson(source.path("connectionConfig")));
            connection.setSecretReferencesJson(writeJson(source.path("secretReferences")));
            connection.setDiscoverySummaryJson(writeJson(source.path("discoverySummary")));
            connection.setCreatedAt(now);
            connection.setUpdatedAt(now);
            vectorizationSourceConnectionRepository.save(connection);
        }

        JsonNode planNode = vectorization.path("plan");
        if (!planNode.isObject()) {
            return;
        }
        String oldPlanId = planNode.path("id").asText(null);
        String oldActiveRevisionId = planNode.path("activeRevisionId").asText(null);
        VectorizationPlanEntity plan = vectorizationPlanRepository.findByDeploymentId(deploymentId)
            .orElseGet(VectorizationPlanEntity::new);
        String newPlanId = StringUtils.hasText(plan.getId()) ? plan.getId() : generateId("vpl");
        String newActiveRevisionId = null;
        String lastImportedRevisionId = null;

        plan.setId(newPlanId);
        plan.setDeploymentId(deploymentId);
        plan.setCustomerId(importedOwnerValue(deployment.getCustomerId(), planNode.path("customerId").asText(null), "customer"));
        plan.setTenantId(importedOwnerValue(deployment.getTenantId(), planNode.path("tenantId").asText(null), "tenant"));
        plan.setName(planNode.path("name").asText("Imported vectorization plan"));
        plan.setStatus(planNode.path("status").asText("ACTIVE"));
        plan.setRunnerMode(planNode.path("runnerMode").asText("PLATFORM_MANAGED_AUTO"));
        plan.setSyncState("BOOTSTRAP_REQUIRED");
        plan.setSyncReasonCodesJson("[\"IMPORTED_REINDEX_REQUIRED\"]");
        ObjectNode reasonDetails = objectMapper.createObjectNode();
        reasonDetails.put("sourceDeploymentId", sourceDeploymentId(bundle));
        reasonDetails.put("sourcePlanId", oldPlanId);
        reasonDetails.put("importedAt", now.toString());
        reasonDetails.put("reason", "Vectorization control plane restored from deployment bundle; target environment must run its own indexing job.");
        plan.setSyncReasonDetailsJson(writeJson(reasonDetails));
        plan.setSourceConnectionId(remapId(planNode.path("sourceConnectionId").asText(null), oldSourceConnectionId, newSourceConnectionId));
        plan.setActiveRevisionId(null);
        plan.setActiveIndexedOutputHash(null);
        plan.setLastSuccessfulIndexedOutputHash(null);
        plan.setLastRunId(null);
        plan.setLastSuccessfulRunId(null);
        plan.setManualConfirmationNote(planNode.path("manualConfirmationNote").asText(null));
        plan.setManualConfirmationActorId(null);
        plan.setManualConfirmationHash(null);
        plan.setManuallyConfirmedAt(null);
        plan.setDeferredReindexAt(null);
        plan.setDeferredReindexNote(null);
        plan.setDeferredReindexHash(null);
        if (plan.getCreatedAt() == null) {
            plan.setCreatedAt(now);
        }
        plan.setUpdatedAt(now);
        vectorizationPlanRepository.save(plan);

        JsonNode revisions = vectorization.path("revisions");
        if (revisions.isArray()) {
            for (JsonNode revisionNode : revisions) {
                if (!revisionNode.isObject()) {
                    continue;
                }
                String newRevisionId = generateId("vpr");
                if (StringUtils.hasText(oldActiveRevisionId)
                    && oldActiveRevisionId.equals(revisionNode.path("id").asText(null))) {
                    newActiveRevisionId = newRevisionId;
                }
                lastImportedRevisionId = newRevisionId;
                VectorizationPlanRevisionEntity revision = new VectorizationPlanRevisionEntity();
                revision.setId(newRevisionId);
                revision.setPlanId(newPlanId);
                revision.setDeploymentId(deploymentId);
                revision.setRevisionNumber(revisionNode.path("revisionNumber").asInt(1));
                revision.setStatus(revisionNode.path("status").asText("ACTIVE"));
                revision.setSourceConnectionId(remapId(revisionNode.path("sourceConnectionId").asText(null), oldSourceConnectionId, newSourceConnectionId));
                revision.setEntityScopeJson(writeJson(revisionNode.path("entityScope")));
                revision.setMappingConfigJson(writeJson(revisionNode.path("mappingConfig")));
                revision.setExecutionConfigJson(writeJson(revisionNode.path("executionConfig")));
                revision.setIndexedOutputHash(null);
                revision.setCreatedByActorId(PlatformSecurityContext.actorIdOrSystem());
                revision.setCreatedAt(now);
                revision.setUpdatedAt(now);
                vectorizationPlanRevisionRepository.save(revision);
            }
        }

        if (!StringUtils.hasText(newActiveRevisionId)) {
            newActiveRevisionId = lastImportedRevisionId;
        }

        plan.setActiveRevisionId(newActiveRevisionId);
        plan.setUpdatedAt(now);
        vectorizationPlanRepository.save(plan);
    }

    private String remapId(String candidate, String oldId, String newId) {
        if (!StringUtils.hasText(candidate)) {
            return null;
        }
        if (StringUtils.hasText(oldId) && candidate.equals(oldId)) {
            return newId;
        }
        return candidate;
    }

    private String importedOwnerValue(String deploymentValue, String bundleValue, String kind) {
        String value = firstNonBlank(deploymentValue, bundleValue);
        if (StringUtils.hasText(value)) {
            return value;
        }
        return "imported-" + kind;
    }

    private List<String> requiredSecretActions(JsonNode bundle, ImportMode importMode, boolean secretsReadable) {
        List<String> actions = new ArrayList<>();
        JsonNode items = bundle.path("secretInventory");
        if (!items.isArray()) {
            return actions;
        }
        for (JsonNode item : items) {
            String name = item.path("secretName").asText("");
            String classification = item.path("classification").asText("");
            boolean included = item.path("valueIncluded").asBoolean(false);
            if ("FORBIDDEN".equals(classification) || "ENVIRONMENT_BOUND".equals(classification) || "MISSING_REFERENCE".equals(classification)) {
                actions.add(name + ": " + item.path("restorePolicy").asText("SUPPLY_OR_REMAP"));
            } else if ("SEALED_EXPORTABLE".equals(classification) && (!included || !secretsReadable)) {
                actions.add(name + ": SUPPLY_OR_DECRYPT_SEALED_BACKUP");
            } else if ("REGENERATE_RECOMMENDED".equals(classification)) {
                actions.add(name + ": SUPPLY_OR_REGENERATE");
            }
        }
        if (importMode == ImportMode.CONFIG_ONLY_CLONE && actions.isEmpty()) {
            actions.add("Review target environment secrets before publish/apply.");
        }
        return actions;
    }

    private DeploymentBundleExternalIntegrationImpact externalImpact(ImportMode importMode,
                                                                     DeploymentImportPreviewRequest request,
                                                                     String sourceDeploymentId,
                                                                     String targetDeploymentId) {
        if (importMode == ImportMode.RESTORE_IN_PLACE && sourceDeploymentId.equals(targetDeploymentId)) {
            return new DeploymentBundleExternalIntegrationImpact(
                false,
                List.of(),
                "Restore-in-place preserves deployment id, assertion audience, and intended runtime route."
            );
        }
        return new DeploymentBundleExternalIntegrationImpact(
            true,
            List.of("runtimeBaseUrl", "assertionAudience", "assertionDeploymentId"),
            "Clone/import creates a new deployment contract unless a stable alias is configured."
        );
    }

    private DeploymentBundleExternalIntegrationImpact externalImpact(ImportMode importMode,
                                                                     DeploymentImportRequest request,
                                                                     String sourceDeploymentId,
                                                                     String targetDeploymentId) {
        if (importMode == ImportMode.RESTORE_IN_PLACE && sourceDeploymentId.equals(targetDeploymentId)) {
            return new DeploymentBundleExternalIntegrationImpact(
                false,
                List.of(),
                "Restore-in-place preserves deployment id, assertion audience, and intended runtime route."
            );
        }
        return new DeploymentBundleExternalIntegrationImpact(
            true,
            List.of("runtimeBaseUrl", "assertionAudience", "assertionDeploymentId"),
            "Clone/import creates a new deployment contract unless a stable alias is configured."
        );
    }

    private DeploymentBundleExternalIntegrationImpact externalImpact(ExportMode exportMode,
                                                                     DeploymentImportRequest request,
                                                                     String sourceDeploymentId,
                                                                     String targetDeploymentId) {
        return new DeploymentBundleExternalIntegrationImpact(
            false,
            List.of(),
            "Export does not mutate deployment state."
        );
    }

    private JsonNode requireBundle(JsonNode bundle) {
        if (bundle == null || !bundle.isObject()) {
            throw new ResponseStatusException(BAD_REQUEST, "Deployment import requires a bundle JSON object.");
        }
        return bundle;
    }

    private String sourceDeploymentId(JsonNode bundle) {
        String sourceDeploymentId = bundle.path("source").path("deploymentId").asText(null);
        if (!StringUtils.hasText(sourceDeploymentId)) {
            sourceDeploymentId = bundle.path("manifest").path("deployment").path("id").asText(null);
        }
        if (!StringUtils.hasText(sourceDeploymentId)) {
            throw new ResponseStatusException(BAD_REQUEST, "Bundle source deployment id is missing.");
        }
        return sourceDeploymentId;
    }

    private String targetDeploymentIdForPreview(ImportMode importMode,
                                                DeploymentImportPreviewRequest request,
                                                String sourceDeploymentId) {
        if (importMode == ImportMode.RESTORE_IN_PLACE) {
            return firstNonBlank(request == null ? null : request.targetDeploymentId(), sourceDeploymentId);
        }
        return null;
    }

    private String resolvedNewName(DeploymentImportPreviewRequest request, JsonNode bundle) {
        return firstNonBlank(
            request == null ? null : request.newDeploymentName(),
            bundle.path("manifest").path("deployment").path("name").asText("Imported Deployment") + " (imported)"
        );
    }

    private String resolvedNewName(DeploymentImportRequest request, JsonNode bundle) {
        return firstNonBlank(
            request == null ? null : request.newDeploymentName(),
            bundle.path("manifest").path("deployment").path("name").asText("Imported Deployment") + " (imported)"
        );
    }

    private String curatedModuleId(JsonNode bundle) {
        return firstNonBlank(
            bundle.path("manifest").path("activeDraft").path("configs").path("provider").path("curatedModuleId").asText(null),
            "default"
        );
    }

    private String vectorProvisioningMode(JsonNode bundle) {
        return firstNonBlank(
            bundle.path("manifest").path("activeDraft").path("configs").path("provider").path("vectorProvisioningMode").asText(null),
            bundle.path("manifest").path("activeDraft").path("configs").path("provider").path("managedVectorProvisioningMode").asText(null)
        );
    }

    private ObjectNode createdBy() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("actorType", "PLATFORM_USER");
        node.put("actorIdHash", sealingService.sha256(PlatformSecurityContext.actorIdOrSystem().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        node.put("actorRole", PlatformSecurityContext.actorRoleOrSystem());
        return node;
    }

    private ObjectNode sourceNode(DeploymentEntity deployment) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("deploymentId", deployment.getId());
        node.put("deploymentName", deployment.getName());
        node.put("environment", deployment.getEnvironmentName());
        node.put("customerId", deployment.getCustomerId());
        node.put("tenantId", deployment.getTenantId());
        node.put("templateId", deployment.getTemplateId());
        return node;
    }

    private ObjectNode importGuidance(DeploymentEntity deployment) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("restoreInPlacePreservesExternalContract", true);
        node.put("cloneRequiresExternalEnvChange", true);
        node.put("sourceDeploymentId", deployment.getId());
        node.put("sourceAssertionAudience", deployment.getId());
        return node;
    }

    private List<String> includedSections(JsonNode manifest) {
        List<String> sections = new ArrayList<>();
        manifest.fieldNames().forEachRemaining(sections::add);
        return sections;
    }

    private List<String> previewWarnings(DeploymentBundleSecretSummary secretSummary) {
        List<String> warnings = new ArrayList<>();
        if (secretSummary.forbidden() > 0) {
            warnings.add("Forbidden secret classes are referenced but will never be exported.");
        }
        if (secretSummary.environmentBound() > 0) {
            warnings.add("Environment-bound values must be supplied or remapped in the target environment.");
        }
        if (secretSummary.missingReference() > 0) {
            warnings.add("Some secret references are missing values in the Platform secret store.");
        }
        return warnings;
    }

    private void collectSecretRefsFromJson(JsonNode node, Map<String, Set<String>> sources, String source) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isTextual()) {
            collectSecretRefsFromText(node.asText(), sources, source);
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> collectSecretRefsFromJson(child, sources, source));
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                String fieldName = entry.getKey();
                JsonNode value = entry.getValue();
                if (looksLikeSecretField(fieldName) && value.isTextual()) {
                    addSource(sources, value.asText(), source + ":" + fieldName);
                }
                collectSecretRefsFromJson(value, sources, source);
            });
        }
    }

    private void collectDraftSecretRefsFromJson(JsonNode node, Map<String, Set<String>> sources, String source) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isTextual()) {
            collectEnvRefsFromText(node.asText(), sources, source);
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> collectDraftSecretRefsFromJson(child, sources, source));
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                String fieldName = entry.getKey();
                JsonNode value = entry.getValue();
                if (value.isTextual()) {
                    collectEnvRefsFromText(value.asText(), sources, source);
                    if (isExplicitSecretReferenceField(fieldName)) {
                        addSource(sources, value.asText(), source + ":" + fieldName);
                    }
                } else {
                    collectDraftSecretRefsFromJson(value, sources, source);
                }
            });
        }
    }

    private void collectSecretRefsFromText(String text, Map<String, Set<String>> sources, String source) {
        if (!StringUtils.hasText(text)) {
            return;
        }
        collectEnvRefsFromText(text, sources, source);
        String normalized = text.trim();
        if (normalized.startsWith("secret://")) {
            addSource(sources, normalized, source);
        } else if (looksLikeSecretName(normalized)) {
            addSource(sources, normalized, source);
        }
    }

    private void collectEnvRefsFromText(String text, Map<String, Set<String>> sources, String source) {
        if (!StringUtils.hasText(text)) {
            return;
        }
        Matcher matcher = ENV_REF_PATTERN.matcher(text);
        while (matcher.find()) {
            addSource(sources, matcher.group(1), source);
        }
    }

    private boolean isExplicitSecretReferenceField(String fieldName) {
        String normalized = fieldName == null ? "" : fieldName.toLowerCase(Locale.ROOT)
            .replace("-", "")
            .replace("_", "");
        return normalized.equals("secretref")
            || normalized.equals("secretrefs")
            || normalized.equals("secretreference")
            || normalized.equals("secretreferences")
            || normalized.equals("secretname")
            || normalized.equals("secretrefname")
            || normalized.equals("apikeysecretname")
            || normalized.equals("tokensecretname")
            || normalized.equals("passwordsecretname")
            || normalized.equals("signingsecretname");
    }

    private boolean looksLikeSecretField(String fieldName) {
        String normalized = fieldName == null ? "" : fieldName.toLowerCase(Locale.ROOT);
        return normalized.contains("secret") || normalized.contains("token") || normalized.contains("api_key") || normalized.contains("apikey");
    }

    private boolean looksLikeSecretName(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return normalized.matches("^[A-Z0-9_]{8,}$")
            && (normalized.contains("SECRET")
            || normalized.contains("TOKEN")
            || normalized.contains("API_KEY")
            || normalized.contains("PASSWORD")
            || normalized.contains("SIGNING_KEY"));
    }

    private void addSource(Map<String, Set<String>> sources, String secretName, String source) {
        if (!StringUtils.hasText(secretName)) {
            return;
        }
        String normalized = secretName.trim();
        boolean explicitSecretRef = normalized.startsWith("secret://");
        if (normalized.startsWith("secret://")) {
            normalized = normalized.substring("secret://".length());
        }
        if (!explicitSecretRef && !looksLikeSecretName(normalized)) {
            return;
        }
        sources.computeIfAbsent(normalized, ignored -> new LinkedHashSet<>()).add(source);
    }

    private JsonNode readJson(String json) {
        if (!StringUtils.hasText(json)) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("raw", json);
            node.put("parseError", true);
            return node;
        }
    }

    private String writeJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node == null || node.isMissingNode() ? objectMapper.createObjectNode() : node);
        } catch (Exception ex) {
            throw new ResponseStatusException(BAD_REQUEST, "Failed to serialize imported deployment config.");
        }
    }

    private String stringTime(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    private Instant timeOrNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private Instant timeOrNow(String value) {
        Instant parsed = timeOrNull(value);
        return parsed == null ? Instant.now() : parsed;
    }

    private String firstNonBlank(String primary, String fallback) {
        if (StringUtils.hasText(primary)) {
            return primary.trim();
        }
        return StringUtils.hasText(fallback) ? fallback.trim() : null;
    }

    private Integer intOrNull(JsonNode node, String fieldName, Integer fallback) {
        JsonNode value = node == null ? null : node.path(fieldName);
        if (value == null || value.isMissingNode() || value.isNull()) {
            return fallback;
        }
        return value.isInt() || value.isLong() ? value.asInt() : fallback;
    }

    private String text(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText(null);
        return StringUtils.hasText(text) ? text.trim() : null;
    }

    private String generateId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private record BundleState(ObjectNode manifest, String manifestHash, SecretInventory secretInventory) {
    }

    private record SecretInventory(List<SecretInventoryItem> items) {
    }

    private record SecretInventoryItem(String secretName,
                                       SecretClassification classification,
                                       boolean valuePresent,
                                       List<String> sources,
                                       PlatformSecretEntity entity) {
    }

    private record ImportValidation(boolean schemaValid,
                                    boolean integrityValid,
                                    List<String> blockingIssues,
                                    List<String> warnings) {
    }

    private record RestoreResult(String deploymentId, String draftId) {
    }

    private record PendingMarketplaceInstallRestore(DeploymentMarketplacePluginInstallEntity install,
                                                    JsonNode config,
                                                    JsonNode secretRefs) {
    }

    private record MarketplaceCatalogRestore(Map<String, String> pluginIdRemap,
                                             Map<String, String> versionIdRemap) {

        private static MarketplaceCatalogRestore empty() {
            return new MarketplaceCatalogRestore(Map.of(), Map.of());
        }

        private String pluginId(String sourcePluginId) {
            return pluginIdRemap.getOrDefault(sourcePluginId, sourcePluginId);
        }

        private String versionId(String sourceVersionId) {
            return versionIdRemap.getOrDefault(sourceVersionId, sourceVersionId);
        }
    }

    private record ImportConfigRewrite(Map<String, String> installIdRemap,
                                       Map<String, String> pluginIdRemap,
                                       Map<String, String> versionIdRemap,
                                       String sourceTenantId,
                                       String targetTenantId,
                                       String sourceCustomerId,
                                       String targetCustomerId) {

        private static ImportConfigRewrite from(DeploymentEntity deployment,
                                                JsonNode bundle,
                                                Map<String, String> installIdRemap,
                                                MarketplaceCatalogRestore marketplaceCatalogRestore) {
            JsonNode sourceDeployment = bundle.path("manifest").path("deployment");
            return new ImportConfigRewrite(
                installIdRemap == null ? Map.of() : Map.copyOf(installIdRemap),
                marketplaceCatalogRestore == null ? Map.of() : Map.copyOf(marketplaceCatalogRestore.pluginIdRemap()),
                marketplaceCatalogRestore == null ? Map.of() : Map.copyOf(marketplaceCatalogRestore.versionIdRemap()),
                sourceDeployment.path("tenantId").asText(null),
                deployment == null ? null : deployment.getTenantId(),
                sourceDeployment.path("customerId").asText(null),
                deployment == null ? null : deployment.getCustomerId()
            );
        }

        private boolean isEmpty() {
            return installIdRemap.isEmpty()
                && pluginIdRemap.isEmpty()
                && versionIdRemap.isEmpty()
                && (!StringUtils.hasText(sourceTenantId) || !StringUtils.hasText(targetTenantId) || sourceTenantId.equals(targetTenantId))
                && (!StringUtils.hasText(sourceCustomerId) || !StringUtils.hasText(targetCustomerId) || sourceCustomerId.equals(targetCustomerId));
        }
    }
}
