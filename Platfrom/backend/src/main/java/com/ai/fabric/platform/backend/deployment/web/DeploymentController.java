package com.ai.fabric.platform.backend.deployment.web;

import com.ai.fabric.platform.backend.audit.model.PlatformAuditEventSummary;
import com.ai.fabric.platform.backend.deployment.model.BulkDeploymentActionRequest;
import com.ai.fabric.platform.backend.deployment.model.BulkDeploymentActionResponse;
import com.ai.fabric.platform.backend.deployment.model.CreateDeploymentRequest;
import com.ai.fabric.platform.backend.deployment.model.CreateDeploymentPromptRevisionRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentCuratedModuleSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentConfigDiffCenterSummary;
import com.ai.fabric.platform.backend.deployment.model.DeleteDeploymentRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentDraftResponse;
import com.ai.fabric.platform.backend.deployment.model.DeploymentHostedVerificationContextSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentHostedVerificationDispatchRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentHostedVerificationDispatchSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentHostedVerificationRunSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentOverviewSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPocChatQueryRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPocChatQueryResponse;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPocChatSuggestionsRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPocChatSuggestionsResponse;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPocConversationResponse;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPocImportRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPocImportRunSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPocPromptSessionSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPocRuntimeResetRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPocRuntimeResetResponse;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPocScenarioSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPocWorkspaceSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentRemediationExecutionSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentRemediationSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPromptBaselineSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPromptRevisionSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProductionReadinessScorecardSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderConnectivitySummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentRailwayLogsResponse;
import com.ai.fabric.platform.backend.deployment.model.DeploymentReleaseSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTemplateSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTenantMigrationExecutionSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTenantMigrationPreviewSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVerificationRunSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVerificationRolloutSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVersionSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentServiceConfigModelSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentServiceNavigationSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSecretUsageSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSecurityGovernanceSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSourceOfTruthSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVerificationRolloutSelectionRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentWorkspaceSummary;
import com.ai.fabric.platform.backend.deployment.model.DraftValidationResponse;
import com.ai.fabric.platform.backend.deployment.model.ExecuteDeploymentRemediationRequest;
import com.ai.fabric.platform.backend.deployment.model.ProbeDeploymentProviderConnectivityRequest;
import com.ai.fabric.platform.backend.deployment.model.RailwayProvisioningPlanSummary;
import com.ai.fabric.platform.backend.deployment.model.UpdateDeploymentSourceRequest;
import com.ai.fabric.platform.backend.deployment.model.PreviewDeploymentTenantMigrationRequest;
import com.ai.fabric.platform.backend.deployment.model.CreateDeploymentTenantMigrationRequest;
import com.ai.fabric.platform.backend.deployment.model.UpdateDeploymentTenantBindingRequest;
import com.ai.fabric.platform.backend.deployment.model.UpdateDeploymentGuardrailsRequest;
import com.ai.fabric.platform.backend.deployment.model.UpdateDeploymentDraftRequest;
import com.ai.fabric.platform.backend.deployment.model.UpdateDeploymentCuratedModuleRequest;
import com.ai.fabric.platform.backend.deployment.model.UpdateDeploymentPocPromptSessionRequest;
import com.ai.fabric.platform.backend.deployment.model.UpsertDeploymentPocScenarioRequest;
import com.ai.fabric.platform.backend.deployment.service.DeploymentRailwayLogService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentActivityService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentBulkOperationService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentHostedVerificationService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentPocChatService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentPocImportService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentPocPromptSessionService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentPocScenarioService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentPocWorkspaceService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentRemediationService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentTenantMigrationService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentVerificationRolloutService;
import com.ai.fabric.platform.backend.deployment.service.EcommerceDemoBootstrapService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR','CUSTOMER_ADMIN')")
public class DeploymentController {

    private final DeploymentService deploymentService;
    private final DeploymentActivityService deploymentActivityService;
    private final DeploymentRailwayLogService deploymentRailwayLogService;
    private final DeploymentBulkOperationService deploymentBulkOperationService;
    private final DeploymentHostedVerificationService deploymentHostedVerificationService;
    private final DeploymentTenantMigrationService deploymentTenantMigrationService;
    private final DeploymentVerificationRolloutService deploymentVerificationRolloutService;
    private final DeploymentPocChatService deploymentPocChatService;
    private final DeploymentPocWorkspaceService deploymentPocWorkspaceService;
    private final DeploymentPocImportService deploymentPocImportService;
    private final DeploymentPocPromptSessionService deploymentPocPromptSessionService;
    private final DeploymentPocScenarioService deploymentPocScenarioService;
    private final DeploymentRemediationService deploymentRemediationService;
    private final EcommerceDemoBootstrapService ecommerceDemoBootstrapService;

    public DeploymentController(DeploymentService deploymentService,
                                DeploymentActivityService deploymentActivityService,
                                DeploymentRailwayLogService deploymentRailwayLogService,
                                DeploymentBulkOperationService deploymentBulkOperationService,
                                DeploymentHostedVerificationService deploymentHostedVerificationService,
                                DeploymentTenantMigrationService deploymentTenantMigrationService,
                                DeploymentVerificationRolloutService deploymentVerificationRolloutService,
                                DeploymentPocChatService deploymentPocChatService,
                                DeploymentPocWorkspaceService deploymentPocWorkspaceService,
                                DeploymentPocImportService deploymentPocImportService,
                                DeploymentPocPromptSessionService deploymentPocPromptSessionService,
                                DeploymentPocScenarioService deploymentPocScenarioService,
                                DeploymentRemediationService deploymentRemediationService,
                                EcommerceDemoBootstrapService ecommerceDemoBootstrapService) {
        this.deploymentService = deploymentService;
        this.deploymentActivityService = deploymentActivityService;
        this.deploymentRailwayLogService = deploymentRailwayLogService;
        this.deploymentBulkOperationService = deploymentBulkOperationService;
        this.deploymentHostedVerificationService = deploymentHostedVerificationService;
        this.deploymentTenantMigrationService = deploymentTenantMigrationService;
        this.deploymentVerificationRolloutService = deploymentVerificationRolloutService;
        this.deploymentPocChatService = deploymentPocChatService;
        this.deploymentPocWorkspaceService = deploymentPocWorkspaceService;
        this.deploymentPocImportService = deploymentPocImportService;
        this.deploymentPocPromptSessionService = deploymentPocPromptSessionService;
        this.deploymentPocScenarioService = deploymentPocScenarioService;
        this.deploymentRemediationService = deploymentRemediationService;
        this.ecommerceDemoBootstrapService = ecommerceDemoBootstrapService;
    }

    @GetMapping("/deployment-templates")
    public List<DeploymentTemplateSummary> listTemplates() {
        return deploymentService.listTemplates();
    }

    @GetMapping("/deployment-curated-modules")
    public List<DeploymentCuratedModuleSummary> listCuratedModules() {
        return deploymentService.listCuratedModules();
    }

    @GetMapping("/deployments")
    public List<DeploymentSummary> listDeployments(@RequestParam(defaultValue = "false") boolean includeArchived) {
        return deploymentService.listDeployments(includeArchived);
    }

    @GetMapping("/deployments/overview")
    public List<DeploymentOverviewSummary> listDeploymentOverviews(
        @RequestParam(defaultValue = "false") boolean includeArchived
    ) {
        return deploymentService.listDeploymentOverviews(includeArchived);
    }

    @PostMapping("/deployments")
    @ResponseStatus(HttpStatus.CREATED)
    public DeploymentSummary createDeployment(@Valid @RequestBody CreateDeploymentRequest request) {
        return deploymentService.createDeployment(request);
    }

    @PostMapping("/deployments/bulk/actions")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public BulkDeploymentActionResponse bulkDeploymentAction(@Valid @RequestBody BulkDeploymentActionRequest request) {
        return deploymentBulkOperationService.execute(request);
    }

    @GetMapping("/deployments/verification-rollouts")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public DeploymentVerificationRolloutSummary getDeploymentVerificationRollouts() {
        return deploymentVerificationRolloutService.listRollouts();
    }

    @PostMapping("/deployments/verification-rollouts/recreate")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public DeploymentVerificationRolloutSummary recreateDeploymentVerificationRollouts(@RequestBody(required = false) DeploymentVerificationRolloutSelectionRequest request) {
        return deploymentVerificationRolloutService.recreateRollouts(request == null ? null : request.rolloutKeys());
    }

    @PostMapping("/deployments/verification-rollouts/cleanup")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public DeploymentVerificationRolloutSummary cleanupDeploymentVerificationRollouts(@RequestBody(required = false) DeploymentVerificationRolloutSelectionRequest request) {
        return deploymentVerificationRolloutService.cleanupRollouts(request == null ? null : request.rolloutKeys());
    }

    @PostMapping("/deployments/ecommerce-demo/rollout")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public DeploymentOverviewSummary rolloutEcommerceDemoDeployment() {
        return ecommerceDemoBootstrapService.rolloutBootstrapDeployment();
    }

    @PostMapping("/deployments/{deploymentId}/archive")
    public DeploymentOverviewSummary archiveDeployment(@PathVariable String deploymentId) {
        return deploymentService.archiveDeployment(deploymentId);
    }

    @PostMapping("/deployments/{deploymentId}/restore")
    public DeploymentOverviewSummary restoreDeployment(@PathVariable String deploymentId) {
        return deploymentService.restoreDeployment(deploymentId);
    }

    @DeleteMapping("/deployments/{deploymentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDeployment(@PathVariable String deploymentId,
                                 @RequestParam(required = false) String approvalId,
                                 @RequestBody(required = false) DeleteDeploymentRequest request) {
        DeleteDeploymentRequest effectiveRequest = request;
        if (effectiveRequest == null && approvalId != null) {
            effectiveRequest = new DeleteDeploymentRequest(false, approvalId, null);
        } else if (effectiveRequest != null
            && (effectiveRequest.approvalId() == null || effectiveRequest.approvalId().isBlank())
            && approvalId != null) {
            effectiveRequest = new DeleteDeploymentRequest(
                effectiveRequest.hardDelete(),
                approvalId,
                effectiveRequest.reason()
            );
        }
        deploymentService.deleteDeployment(deploymentId, effectiveRequest);
    }

    @PutMapping("/deployments/{deploymentId}/source")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public DeploymentOverviewSummary updateDeploymentSource(@PathVariable String deploymentId,
                                                           @RequestBody UpdateDeploymentSourceRequest request) {
        return deploymentService.updateDeploymentSource(deploymentId, request);
    }

    @PutMapping("/deployments/{deploymentId}/guardrails")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public DeploymentOverviewSummary updateDeploymentGuardrails(@PathVariable String deploymentId,
                                                                @RequestBody UpdateDeploymentGuardrailsRequest request) {
        return deploymentService.updateDeploymentGuardrails(deploymentId, request);
    }

    @PutMapping("/deployments/{deploymentId}/tenant-binding")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public DeploymentOverviewSummary updateDeploymentTenantBinding(@PathVariable String deploymentId,
                                                                   @Valid @RequestBody UpdateDeploymentTenantBindingRequest request) {
        return deploymentService.updateDeploymentTenantBinding(deploymentId, request);
    }

    @PostMapping("/deployments/{deploymentId}/tenant-migration-preview")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public DeploymentTenantMigrationPreviewSummary previewDeploymentTenantMigration(
        @PathVariable String deploymentId,
        @RequestBody(required = false) PreviewDeploymentTenantMigrationRequest request
    ) {
        PreviewDeploymentTenantMigrationRequest effectiveRequest = request == null
            ? new PreviewDeploymentTenantMigrationRequest(null, null, null, null)
            : request;
        return deploymentTenantMigrationService.previewMigration(deploymentId, effectiveRequest);
    }

    @PostMapping("/deployments/{deploymentId}/tenant-migrations")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public DeploymentTenantMigrationExecutionSummary createDeploymentTenantMigration(
        @PathVariable String deploymentId,
        @Valid @RequestBody CreateDeploymentTenantMigrationRequest request
    ) {
        return deploymentTenantMigrationService.createMigrationDeployment(deploymentId, request);
    }

    @GetMapping("/deployments/{deploymentId}/draft")
    public DeploymentDraftResponse getActiveDraft(@PathVariable String deploymentId) {
        return deploymentService.getActiveDraftForDeployment(deploymentId);
    }

    @GetMapping("/deployments/{deploymentId}/workspace")
    public DeploymentWorkspaceSummary getDeploymentWorkspace(@PathVariable String deploymentId) {
        return deploymentService.getDeploymentWorkspace(deploymentId);
    }

    @GetMapping("/deployments/{deploymentId}/config-diff-center")
    public DeploymentConfigDiffCenterSummary getDeploymentConfigDiffCenter(@PathVariable String deploymentId) {
        return deploymentService.getDeploymentConfigDiffCenter(deploymentId);
    }

    @GetMapping("/deployments/{deploymentId}/service-config-model")
    public DeploymentServiceConfigModelSummary getDeploymentServiceConfigModel(@PathVariable String deploymentId) {
        return deploymentService.getDeploymentServiceConfigModel(deploymentId);
    }

    @GetMapping("/deployments/{deploymentId}/service-navigation")
    public DeploymentServiceNavigationSummary getDeploymentServiceNavigation(@PathVariable String deploymentId) {
        return deploymentService.getDeploymentServiceNavigation(deploymentId);
    }

    @GetMapping("/deployments/{deploymentId}/production-readiness")
    public DeploymentProductionReadinessScorecardSummary getDeploymentProductionReadiness(@PathVariable String deploymentId) {
        return deploymentService.getDeploymentProductionReadinessScorecard(deploymentId);
    }

    @GetMapping("/deployments/{deploymentId}/provider-connectivity")
    public DeploymentProviderConnectivitySummary getDeploymentProviderConnectivity(@PathVariable String deploymentId) {
        return deploymentService.getDeploymentProviderConnectivity(deploymentId);
    }

    @PostMapping("/deployments/{deploymentId}/provider-connectivity/probe")
    public DeploymentProviderConnectivitySummary probeDeploymentProviderConnectivity(@PathVariable String deploymentId,
                                                                                     @RequestBody ProbeDeploymentProviderConnectivityRequest request) {
        return deploymentService.probeDeploymentProviderConnectivity(deploymentId, request);
    }

    @GetMapping("/deployments/{deploymentId}/remediation")
    public DeploymentRemediationSummary getDeploymentRemediation(@PathVariable String deploymentId) {
        return deploymentRemediationService.getSummary(deploymentId);
    }

    @PostMapping("/deployments/{deploymentId}/remediation/{actionKey}")
    public DeploymentRemediationExecutionSummary executeDeploymentRemediation(@PathVariable String deploymentId,
                                                                              @PathVariable String actionKey,
                                                                              @RequestBody(required = false) ExecuteDeploymentRemediationRequest request) {
        return deploymentRemediationService.execute(deploymentId, actionKey, request);
    }

    @GetMapping("/deployments/{deploymentId}/secret-usage")
    public DeploymentSecretUsageSummary getDeploymentSecretUsage(@PathVariable String deploymentId) {
        return deploymentService.getDeploymentSecretUsage(deploymentId);
    }

    @GetMapping("/deployments/{deploymentId}/security-governance")
    public DeploymentSecurityGovernanceSummary getDeploymentSecurityGovernance(@PathVariable String deploymentId) {
        return deploymentService.getDeploymentSecurityGovernance(deploymentId);
    }

    @GetMapping("/deployments/{deploymentId}/source-of-truth")
    public DeploymentSourceOfTruthSummary getDeploymentSourceOfTruth(@PathVariable String deploymentId) {
        return deploymentService.getDeploymentSourceOfTruth(deploymentId);
    }

    @GetMapping("/deployments/{deploymentId}/activity")
    public List<PlatformAuditEventSummary> listDeploymentActivity(@PathVariable String deploymentId,
                                                                  @RequestParam(defaultValue = "100") Integer limit) {
        return deploymentActivityService.listRecentActivity(deploymentId, limit);
    }

    @PutMapping("/deployment-drafts/{draftId}")
    public DeploymentDraftResponse updateDraft(@PathVariable String draftId,
                                               @RequestBody UpdateDeploymentDraftRequest request) {
        return deploymentService.updateDraft(draftId, request);
    }

    @PutMapping("/deployments/{deploymentId}/curated-module")
    public DeploymentDraftResponse applyCuratedModuleToDraft(@PathVariable String deploymentId,
                                                             @Valid @RequestBody UpdateDeploymentCuratedModuleRequest request) {
        return deploymentService.applyCuratedModuleToDraft(deploymentId, request);
    }

    @GetMapping("/deployments/{deploymentId}/prompt-revisions")
    public List<DeploymentPromptRevisionSummary> listPromptRevisions(@PathVariable String deploymentId) {
        return deploymentService.listPromptRevisions(deploymentId);
    }

    @GetMapping("/deployments/{deploymentId}/prompt-baseline")
    public DeploymentPromptBaselineSummary getPromptBaseline(@PathVariable String deploymentId) {
        return deploymentService.getPromptBaseline(deploymentId);
    }

    @PostMapping("/deployments/{deploymentId}/prompt-revisions")
    @ResponseStatus(HttpStatus.CREATED)
    public DeploymentPromptRevisionSummary createPromptRevision(@PathVariable String deploymentId,
                                                                @RequestBody CreateDeploymentPromptRevisionRequest request) {
        return deploymentService.createPromptRevision(deploymentId, request);
    }

    @PostMapping("/deployments/{deploymentId}/prompt-revisions/{revisionId}/restore")
    public DeploymentDraftResponse restorePromptRevision(@PathVariable String deploymentId,
                                                         @PathVariable String revisionId) {
        return deploymentService.restorePromptRevision(deploymentId, revisionId);
    }

    @PostMapping("/deployments/{deploymentId}/poc-chat/query")
    public DeploymentPocChatQueryResponse queryPocChat(@PathVariable String deploymentId,
                                                       @RequestBody DeploymentPocChatQueryRequest request) {
        return deploymentPocChatService.query(deploymentId, request);
    }

    @GetMapping("/deployments/{deploymentId}/poc")
    public DeploymentPocWorkspaceSummary getPocWorkspace(@PathVariable String deploymentId) {
        return deploymentPocWorkspaceService.getWorkspace(deploymentId);
    }

    @GetMapping("/deployments/{deploymentId}/poc/prompt-session")
    public DeploymentPocPromptSessionSummary getPocPromptSession(@PathVariable String deploymentId) {
        return deploymentPocPromptSessionService.getSession(deploymentId);
    }

    @PutMapping("/deployments/{deploymentId}/poc/prompt-session")
    public DeploymentPocPromptSessionSummary activatePocPromptSession(
        @PathVariable String deploymentId,
        @RequestBody UpdateDeploymentPocPromptSessionRequest request
    ) {
        return deploymentPocPromptSessionService.activateSession(deploymentId, request);
    }

    @DeleteMapping("/deployments/{deploymentId}/poc/prompt-session")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearPocPromptSession(@PathVariable String deploymentId) {
        deploymentPocPromptSessionService.clearSession(deploymentId);
    }

    @PostMapping("/deployments/{deploymentId}/poc/import-runs")
    @ResponseStatus(HttpStatus.CREATED)
    public DeploymentPocImportRunSummary createPocImportRun(@PathVariable String deploymentId,
                                                            @RequestBody DeploymentPocImportRequest request) {
        return deploymentPocImportService.importDataset(deploymentId, request);
    }

    @PostMapping("/deployments/{deploymentId}/poc/reset/runtime-vectors")
    public DeploymentPocRuntimeResetResponse clearPocRuntimeVectors(
        @PathVariable String deploymentId,
        @RequestBody DeploymentPocRuntimeResetRequest request
    ) {
        return deploymentPocWorkspaceService.clearRuntimeVectors(deploymentId, request);
    }

    @GetMapping("/deployments/{deploymentId}/poc/scenarios")
    public List<DeploymentPocScenarioSummary> listPocScenarios(@PathVariable String deploymentId) {
        return deploymentPocScenarioService.listScenarios(deploymentId);
    }

    @PostMapping("/deployments/{deploymentId}/poc/scenarios")
    @ResponseStatus(HttpStatus.CREATED)
    public DeploymentPocScenarioSummary createPocScenario(@PathVariable String deploymentId,
                                                          @RequestBody UpsertDeploymentPocScenarioRequest request) {
        return deploymentPocScenarioService.createScenario(deploymentId, request);
    }

    @PutMapping("/deployments/{deploymentId}/poc/scenarios/{scenarioId}")
    public DeploymentPocScenarioSummary updatePocScenario(@PathVariable String deploymentId,
                                                          @PathVariable String scenarioId,
                                                          @RequestBody UpsertDeploymentPocScenarioRequest request) {
        return deploymentPocScenarioService.updateScenario(deploymentId, scenarioId, request);
    }

    @DeleteMapping("/deployments/{deploymentId}/poc/scenarios/{scenarioId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePocScenario(@PathVariable String deploymentId,
                                  @PathVariable String scenarioId) {
        deploymentPocScenarioService.deleteScenario(deploymentId, scenarioId);
    }

    @PostMapping("/deployments/{deploymentId}/poc-chat/suggestions")
    public DeploymentPocChatSuggestionsResponse suggestPocChat(@PathVariable String deploymentId,
                                                               @RequestBody(required = false) DeploymentPocChatSuggestionsRequest request) {
        return deploymentPocChatService.suggestions(
            deploymentId,
            request == null ? new DeploymentPocChatSuggestionsRequest(null, null) : request
        );
    }

    @GetMapping("/deployments/{deploymentId}/poc-chat/conversations/{conversationId}")
    public DeploymentPocConversationResponse getPocConversation(@PathVariable String deploymentId,
                                                                @PathVariable String conversationId) {
        return deploymentPocChatService.getConversation(deploymentId, conversationId);
    }

    @DeleteMapping("/deployments/{deploymentId}/poc-chat/conversations/{conversationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePocConversation(@PathVariable String deploymentId,
                                      @PathVariable String conversationId) {
        deploymentPocChatService.deleteConversation(deploymentId, conversationId);
    }

    @PostMapping("/deployment-drafts/{draftId}/validate")
    public DraftValidationResponse validateDraft(@PathVariable String draftId) {
        return deploymentService.validateDraft(draftId);
    }

    @PostMapping("/deployment-drafts/{draftId}/publish")
    @ResponseStatus(HttpStatus.CREATED)
    public DeploymentVersionSummary publishDraft(@PathVariable String draftId) {
        return deploymentService.publishDraft(draftId);
    }

    @GetMapping("/deployments/{deploymentId}/versions")
    public List<DeploymentVersionSummary> listVersions(@PathVariable String deploymentId) {
        return deploymentService.listVersions(deploymentId);
    }

    @GetMapping("/deployments/{deploymentId}/versions/{versionId}/railway-plan")
    public RailwayProvisioningPlanSummary previewRailwayPlan(@PathVariable String deploymentId,
                                                             @PathVariable String versionId) {
        return deploymentService.previewRailwayPlan(deploymentId, versionId);
    }

    @PostMapping("/deployments/{deploymentId}/apply/{versionId}")
    @ResponseStatus(HttpStatus.CREATED)
    public DeploymentReleaseSummary applyVersion(@PathVariable String deploymentId,
                                                 @PathVariable String versionId,
                                                 @RequestParam(required = false) String approvalId) {
        return deploymentService.applyVersion(deploymentId, versionId, approvalId);
    }

    @GetMapping("/deployments/{deploymentId}/releases")
    public List<DeploymentReleaseSummary> listReleases(@PathVariable String deploymentId) {
        return deploymentService.listReleases(deploymentId);
    }

    @PostMapping("/deployments/{deploymentId}/releases/reconcile")
    public DeploymentReleaseSummary reconcileLatestRelease(@PathVariable String deploymentId) {
        return deploymentService.reconcileLatestRelease(deploymentId);
    }

    @GetMapping("/deployments/{deploymentId}/verification-runs")
    public List<DeploymentVerificationRunSummary> listVerificationRuns(@PathVariable String deploymentId) {
        return deploymentService.listVerificationRuns(deploymentId);
    }

    @GetMapping("/deployments/{deploymentId}/hosted-verifications")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public List<DeploymentHostedVerificationRunSummary> listHostedVerificationRuns(@PathVariable String deploymentId) {
        return deploymentHostedVerificationService.listRuns(deploymentId);
    }

    @GetMapping("/deployments/{deploymentId}/hosted-verification-context")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public DeploymentHostedVerificationContextSummary getHostedVerificationContext(@PathVariable String deploymentId,
                                                                                   @RequestParam(required = false) String profile,
                                                                                   @RequestParam(defaultValue = "false") boolean verifyWrite) {
        return deploymentHostedVerificationService.getContext(deploymentId, profile, verifyWrite);
    }

    @GetMapping("/deployments/{deploymentId}/railway-logs")
    public DeploymentRailwayLogsResponse fetchRailwayLogs(@PathVariable String deploymentId,
                                                          @RequestParam(required = false) String releaseId,
                                                          @RequestParam(defaultValue = "runtime") String service,
                                                          @RequestParam(defaultValue = "deployment") String source,
                                                          @RequestParam(required = false) Integer limit,
                                                          @RequestParam(required = false) String filter,
                                                          @RequestParam(required = false) String startDate,
                                                          @RequestParam(required = false) String endDate) {
        return deploymentRailwayLogService.fetchLogs(
            deploymentId,
            releaseId,
            service,
            source,
            limit,
            filter,
            startDate,
            endDate
        );
    }

    @PostMapping("/deployments/{deploymentId}/verification-runs/recheck")
    @ResponseStatus(HttpStatus.CREATED)
    public DeploymentVerificationRunSummary rerunVerification(@PathVariable String deploymentId) {
        return deploymentService.rerunVerification(deploymentId);
    }

    @PostMapping("/deployments/{deploymentId}/hosted-verifications")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public DeploymentHostedVerificationDispatchSummary dispatchHostedVerification(@PathVariable String deploymentId,
                                                                                  @RequestBody(required = false) DeploymentHostedVerificationDispatchRequest request) {
        return deploymentHostedVerificationService.dispatch(deploymentId, request);
    }
}
