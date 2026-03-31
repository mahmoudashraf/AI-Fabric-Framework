package com.ai.fabric.platform.backend.deployment.web;

import com.ai.fabric.platform.backend.deployment.model.BulkDeploymentActionRequest;
import com.ai.fabric.platform.backend.deployment.model.BulkDeploymentActionResponse;
import com.ai.fabric.platform.backend.deployment.model.CreateDeploymentRequest;
import com.ai.fabric.platform.backend.deployment.model.CreateDeploymentPromptRevisionRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentDraftResponse;
import com.ai.fabric.platform.backend.deployment.model.DeploymentOverviewSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPocChatQueryRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPocChatQueryResponse;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPocChatSuggestionsRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPocChatSuggestionsResponse;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPocConversationResponse;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPocImportRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPocImportRunSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPocRuntimeResetRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPocRuntimeResetResponse;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPocScenarioSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPocWorkspaceSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPromptRevisionSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentRailwayLogsResponse;
import com.ai.fabric.platform.backend.deployment.model.DeploymentReleaseSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTemplateSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVerificationRunSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVersionSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentWorkspaceSummary;
import com.ai.fabric.platform.backend.deployment.model.DraftValidationResponse;
import com.ai.fabric.platform.backend.deployment.model.RailwayProvisioningPlanSummary;
import com.ai.fabric.platform.backend.deployment.model.UpdateDeploymentSourceRequest;
import com.ai.fabric.platform.backend.deployment.model.UpdateDeploymentGuardrailsRequest;
import com.ai.fabric.platform.backend.deployment.model.UpdateDeploymentDraftRequest;
import com.ai.fabric.platform.backend.deployment.model.UpsertDeploymentPocScenarioRequest;
import com.ai.fabric.platform.backend.deployment.service.DeploymentRailwayLogService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentBulkOperationService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentPocChatService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentPocImportService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentPocScenarioService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentPocWorkspaceService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
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
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR')")
public class DeploymentController {

    private final DeploymentService deploymentService;
    private final DeploymentRailwayLogService deploymentRailwayLogService;
    private final DeploymentBulkOperationService deploymentBulkOperationService;
    private final DeploymentPocChatService deploymentPocChatService;
    private final DeploymentPocWorkspaceService deploymentPocWorkspaceService;
    private final DeploymentPocImportService deploymentPocImportService;
    private final DeploymentPocScenarioService deploymentPocScenarioService;

    public DeploymentController(DeploymentService deploymentService,
                                DeploymentRailwayLogService deploymentRailwayLogService,
                                DeploymentBulkOperationService deploymentBulkOperationService,
                                DeploymentPocChatService deploymentPocChatService,
                                DeploymentPocWorkspaceService deploymentPocWorkspaceService,
                                DeploymentPocImportService deploymentPocImportService,
                                DeploymentPocScenarioService deploymentPocScenarioService) {
        this.deploymentService = deploymentService;
        this.deploymentRailwayLogService = deploymentRailwayLogService;
        this.deploymentBulkOperationService = deploymentBulkOperationService;
        this.deploymentPocChatService = deploymentPocChatService;
        this.deploymentPocWorkspaceService = deploymentPocWorkspaceService;
        this.deploymentPocImportService = deploymentPocImportService;
        this.deploymentPocScenarioService = deploymentPocScenarioService;
    }

    @GetMapping("/deployment-templates")
    public List<DeploymentTemplateSummary> listTemplates() {
        return deploymentService.listTemplates();
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
                                 @RequestParam(required = false) String approvalId) {
        deploymentService.deleteDeployment(deploymentId, approvalId);
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

    @GetMapping("/deployments/{deploymentId}/draft")
    public DeploymentDraftResponse getActiveDraft(@PathVariable String deploymentId) {
        return deploymentService.getActiveDraftForDeployment(deploymentId);
    }

    @GetMapping("/deployments/{deploymentId}/workspace")
    public DeploymentWorkspaceSummary getDeploymentWorkspace(@PathVariable String deploymentId) {
        return deploymentService.getDeploymentWorkspace(deploymentId);
    }

    @PutMapping("/deployment-drafts/{draftId}")
    public DeploymentDraftResponse updateDraft(@PathVariable String draftId,
                                               @RequestBody UpdateDeploymentDraftRequest request) {
        return deploymentService.updateDraft(draftId, request);
    }

    @GetMapping("/deployments/{deploymentId}/prompt-revisions")
    public List<DeploymentPromptRevisionSummary> listPromptRevisions(@PathVariable String deploymentId) {
        return deploymentService.listPromptRevisions(deploymentId);
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

    @GetMapping("/deployments/{deploymentId}/verification-runs")
    public List<DeploymentVerificationRunSummary> listVerificationRuns(@PathVariable String deploymentId) {
        return deploymentService.listVerificationRuns(deploymentId);
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
}
