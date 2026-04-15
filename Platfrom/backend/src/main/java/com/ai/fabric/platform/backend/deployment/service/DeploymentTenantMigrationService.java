package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentDraftEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.model.CreateDeploymentTenantMigrationRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTenantBindingSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTenantMigrationExecutionSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTenantMigrationPreviewSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTenantScopedVectorSummary;
import com.ai.fabric.platform.backend.deployment.model.PreviewDeploymentTenantMigrationRequest;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentDraftRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.tenant.service.PlatformCustomerTenantService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class DeploymentTenantMigrationService {

    private final DeploymentRepository deploymentRepository;
    private final DeploymentDraftRepository draftRepository;
    private final DeploymentAccessService deploymentAccessService;
    private final DeploymentAssignmentService deploymentAssignmentService;
    private final PlatformCustomerTenantService platformCustomerTenantService;
    private final DeploymentTenantScopedVectorService deploymentTenantScopedVectorService;
    private final PlatformAuditService platformAuditService;
    private final ObjectMapper objectMapper;

    public DeploymentTenantMigrationService(DeploymentRepository deploymentRepository,
                                            DeploymentDraftRepository draftRepository,
                                            DeploymentAccessService deploymentAccessService,
                                            DeploymentAssignmentService deploymentAssignmentService,
                                            PlatformCustomerTenantService platformCustomerTenantService,
                                            DeploymentTenantScopedVectorService deploymentTenantScopedVectorService,
                                            PlatformAuditService platformAuditService,
                                            ObjectMapper objectMapper) {
        this.deploymentRepository = deploymentRepository;
        this.draftRepository = draftRepository;
        this.deploymentAccessService = deploymentAccessService;
        this.deploymentAssignmentService = deploymentAssignmentService;
        this.platformCustomerTenantService = platformCustomerTenantService;
        this.deploymentTenantScopedVectorService = deploymentTenantScopedVectorService;
        this.platformAuditService = platformAuditService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public DeploymentTenantMigrationPreviewSummary previewMigration(String deploymentId,
                                                                   PreviewDeploymentTenantMigrationRequest request) {
        MigrationPlan plan = buildPlan(deploymentId, request.customerId(), request.tenantId(), request.proposedDeploymentName(), request.proposedEnvironmentName());
        return new DeploymentTenantMigrationPreviewSummary(
            plan.source().getId(),
            plan.source().getName(),
            plan.source().getEnvironmentName(),
            plan.sourceBinding().customerId(),
            plan.sourceBinding().customerName(),
            plan.sourceBinding().tenantId(),
            plan.sourceBinding().tenantName(),
            plan.targetBindingPreview().customerId(),
            plan.targetBindingPreview().customerName(),
            plan.targetBindingPreview().tenantId(),
            plan.targetBindingPreview().tenantName(),
            plan.targetBindingPreview().autoCreatedTenant(),
            plan.proposedDeploymentName(),
            plan.proposedEnvironmentName(),
            plan.sourceBinding().publishedVersionCount(),
            plan.sourceBinding().releaseCount(),
            sourceConfigStrategy(plan.sourceBinding()),
            plan.sourceTenantScope().sharedStorage(),
            plan.sourceTenantScope().status(),
            sharedVectorMessage(plan.sourceTenantScope()),
            rollbackPosture(),
            "READY",
            readinessMessage(plan)
        );
    }

    @Transactional
    public DeploymentTenantMigrationExecutionSummary createMigrationDeployment(String deploymentId,
                                                                              CreateDeploymentTenantMigrationRequest request) {
        MigrationPlan plan = buildPlan(deploymentId, request.customerId(), request.tenantId(), request.proposedDeploymentName(), request.proposedEnvironmentName());
        DeploymentDraftEntity sourceDraft = requireActiveDraft(plan.source());
        PlatformCustomerTenantService.ResolvedDeploymentBinding resolvedBinding = platformCustomerTenantService.resolveBindingForNewDeployment(
            plan.proposedDeploymentName(),
            plan.proposedEnvironmentName(),
            plan.targetBindingPreview().customerId(),
            plan.targetBindingPreview().tenantId()
        );

        Instant now = Instant.now();
        DeploymentEntity migrated = new DeploymentEntity();
        migrated.setId(generateId("dep"));
        migrated.setName(plan.proposedDeploymentName());
        migrated.setEnvironmentName(plan.proposedEnvironmentName());
        migrated.setTemplateId(plan.source().getTemplateId());
        migrated.setStatus("DRAFT");
        migrated.setCustomerId(resolvedBinding.customer().getId());
        migrated.setTenantId(resolvedBinding.tenant().getId());
        migrated.setApprovalRequiredForApply(plan.source().isApprovalRequiredForApply());
        migrated.setApprovalRequiredForDelete(plan.source().isApprovalRequiredForDelete());
        migrated.setSourceRepositoryOverride(plan.source().getSourceRepositoryOverride());
        migrated.setSourceBranchOverride(plan.source().getSourceBranchOverride());
        migrated.setCreatedAt(now);
        migrated.setUpdatedAt(now);

        DeploymentDraftEntity migratedDraft = cloneDraft(sourceDraft, migrated.getId(), now);
        migrated.setActiveDraftId(migratedDraft.getId());

        deploymentRepository.save(migrated);
        draftRepository.save(migratedDraft);
        deploymentAssignmentService.grantCreatorAccessIfNeeded(migrated);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("sourceDeploymentId", plan.source().getId());
        details.put("sourceCustomerId", plan.sourceBinding().customerId());
        details.put("sourceTenantId", plan.sourceBinding().tenantId());
        details.put("targetCustomerId", resolvedBinding.customer().getId());
        details.put("targetTenantId", resolvedBinding.tenant().getId());
        details.put("autoCreatedTenant", resolvedBinding.autoCreatedTenant());
        details.put("reason", request.reason().trim());
        details.put("sourceDraftId", sourceDraft.getId());
        platformAuditService.record(
            "DEPLOYMENT_TENANT_MIGRATION_CREATED",
            "DEPLOYMENT",
            migrated.getId(),
            details
        );

        return new DeploymentTenantMigrationExecutionSummary(
            plan.source().getId(),
            migrated.getId(),
            migrated.getName(),
            migrated.getEnvironmentName(),
            resolvedBinding.customer().getId(),
            resolvedBinding.customer().getName(),
            resolvedBinding.tenant().getId(),
            resolvedBinding.tenant().getName(),
            resolvedBinding.autoCreatedTenant(),
            "CREATED",
            "Created a new tenant-bound draft deployment from the source deployment's current draft. The source deployment remains unchanged."
        );
    }

    private MigrationPlan buildPlan(String deploymentId,
                                    String requestedCustomerId,
                                    String requestedTenantId,
                                    String proposedDeploymentName,
                                    String proposedEnvironmentName) {
        DeploymentEntity source = deploymentRepository.findById(deploymentId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment not found: " + deploymentId));
        source = deploymentAccessService.requireDeploymentAdminAccess(source);
        DeploymentTenantBindingSummary sourceBinding = platformCustomerTenantService.summarizeBinding(source);
        if (sourceBinding == null || !StringUtils.hasText(sourceBinding.customerId()) || !StringUtils.hasText(sourceBinding.tenantId())) {
            throw new ResponseStatusException(BAD_REQUEST, "Source deployment is missing a valid customer and tenant binding.");
        }
        PlatformCustomerTenantService.DeploymentBindingPreview targetBindingPreview =
            platformCustomerTenantService.previewBindingForNewDeployment(requestedCustomerId, requestedTenantId);
        if (!sourceBinding.customerId().equals(targetBindingPreview.customerId())) {
            throw new ResponseStatusException(
                BAD_REQUEST,
                "Tenant migration currently supports reassignment only within the same customer boundary."
            );
        }
        if (sourceBinding.customerId().equals(targetBindingPreview.customerId())
            && sourceBinding.tenantId().equals(targetBindingPreview.tenantId())) {
            throw new ResponseStatusException(BAD_REQUEST, "The target binding matches the source deployment's current tenant.");
        }
        String environmentName = normalizeOptionalText(proposedEnvironmentName);
        if (environmentName == null) {
            environmentName = source.getEnvironmentName();
        }
        String deploymentName = normalizeOptionalText(proposedDeploymentName);
        if (deploymentName == null) {
            deploymentName = nextAvailableName(defaultMigrationName(source.getName(), targetBindingPreview), environmentName);
        } else {
            deploymentName = nextAvailableName(deploymentName, environmentName);
        }
        DeploymentTenantScopedVectorSummary sourceTenantScope = deploymentTenantScopedVectorService.build(
            source,
            parseProviderConfig(requireActiveDraft(source).getProviderConfigJson())
        );
        return new MigrationPlan(source, sourceBinding, targetBindingPreview, deploymentName, environmentName, sourceTenantScope);
    }

    private DeploymentDraftEntity requireActiveDraft(DeploymentEntity deployment) {
        if (!StringUtils.hasText(deployment.getActiveDraftId())) {
            throw new ResponseStatusException(BAD_REQUEST, "Deployment has no active draft to clone for tenant migration.");
        }
        return draftRepository.findById(deployment.getActiveDraftId())
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Active draft not found: " + deployment.getActiveDraftId()));
    }

    private JsonNode parseProviderConfig(String providerConfigJson) {
        try {
            return objectMapper.readTree(providerConfigJson);
        } catch (Exception ex) {
            throw new ResponseStatusException(BAD_REQUEST, "Active draft provider config is not valid JSON.");
        }
    }

    private DeploymentDraftEntity cloneDraft(DeploymentDraftEntity sourceDraft, String deploymentId, Instant now) {
        DeploymentDraftEntity clone = new DeploymentDraftEntity();
        clone.setId(generateId("drf"));
        clone.setDeploymentId(deploymentId);
        clone.setRevisionNumber(1);
        clone.setStatus("DRAFT");
        clone.setActionsConfigJson(sourceDraft.getActionsConfigJson());
        clone.setEntityConfigJson(sourceDraft.getEntityConfigJson());
        clone.setRoutingConfigJson(sourceDraft.getRoutingConfigJson());
        clone.setProviderConfigJson(sourceDraft.getProviderConfigJson());
        clone.setSecurityConfigJson(sourceDraft.getSecurityConfigJson());
        clone.setPromptConfigJson(sourceDraft.getPromptConfigJson());
        clone.setKnowledgeSourceConfigJson(sourceDraft.getKnowledgeSourceConfigJson());
        clone.setShellConfigJson(sourceDraft.getShellConfigJson());
        clone.setAutomationConfigJson(sourceDraft.getAutomationConfigJson());
        clone.setMarketplaceDatasetConfigJson(sourceDraft.getMarketplaceDatasetConfigJson());
        clone.setCreatedAt(now);
        clone.setUpdatedAt(now);
        return clone;
    }

    private String sourceConfigStrategy(DeploymentTenantBindingSummary sourceBinding) {
        if (sourceBinding.publishedVersionCount() > 0 || sourceBinding.releaseCount() > 0) {
            return "The migration deployment clones the current active draft while preserving the source deployment's published and live history.";
        }
        return "The migration deployment clones the current active draft. The source deployment remains editable until you decide to cut over.";
    }

    private String sharedVectorMessage(DeploymentTenantScopedVectorSummary sourceTenantScope) {
        if (!sourceTenantScope.sharedStorage()) {
            return "The source deployment is not using shared vector storage. The migration deployment will bind the copied draft to the target tenant without mutating the source deployment.";
        }
        String handle = StringUtils.hasText(sourceTenantScope.scopePattern())
            ? sourceTenantScope.scopePattern()
            : "the current tenant-scoped shared handle";
        return "The source deployment currently resolves " + handle
            + ". Creating the migration deployment does not move provider data automatically; publish, apply, and verify the new deployment against the target tenant scope before cutover.";
    }

    private String rollbackPosture() {
        return "The source deployment stays unchanged. Roll back by discarding, archiving, or deleting the new migration deployment before cutover.";
    }

    private String readinessMessage(MigrationPlan plan) {
        if (plan.targetBindingPreview().autoCreatedTenant()) {
            return "The new deployment will create a dedicated tenant under the same customer and start from a cloned draft.";
        }
        return "The new deployment will bind the cloned draft to the selected tenant while keeping the source deployment and its history intact.";
    }

    private String defaultMigrationName(String sourceName, PlatformCustomerTenantService.DeploymentBindingPreview targetBindingPreview) {
        String suffix = StringUtils.hasText(targetBindingPreview.tenantName())
            ? targetBindingPreview.tenantName()
            : "Tenant Migration";
        return sourceName + " - " + suffix;
    }

    private String nextAvailableName(String baseName, String environmentName) {
        String normalizedBase = baseName.trim();
        String candidate = normalizedBase;
        int suffix = 2;
        while (deploymentRepository.findByNameIgnoreCaseAndEnvironmentNameIgnoreCaseAndArchivedAtIsNull(candidate, environmentName).isPresent()) {
            candidate = normalizedBase + " " + suffix;
            suffix += 1;
        }
        return candidate;
    }

    private String normalizeOptionalText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String generateId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private record MigrationPlan(DeploymentEntity source,
                                 DeploymentTenantBindingSummary sourceBinding,
                                 PlatformCustomerTenantService.DeploymentBindingPreview targetBindingPreview,
                                 String proposedDeploymentName,
                                 String proposedEnvironmentName,
                                 DeploymentTenantScopedVectorSummary sourceTenantScope) {
    }
}
