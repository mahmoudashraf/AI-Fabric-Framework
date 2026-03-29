package com.ai.fabric.platform.backend.deployment.web;

import com.ai.fabric.platform.backend.deployment.model.CreateDeploymentRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentDraftResponse;
import com.ai.fabric.platform.backend.deployment.model.DeploymentReleaseSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTemplateSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVerificationRunSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVersionSummary;
import com.ai.fabric.platform.backend.deployment.model.DraftValidationResponse;
import com.ai.fabric.platform.backend.deployment.model.RailwayProvisioningPlanSummary;
import com.ai.fabric.platform.backend.deployment.model.UpdateDeploymentDraftRequest;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class DeploymentController {

    private final DeploymentService deploymentService;

    public DeploymentController(DeploymentService deploymentService) {
        this.deploymentService = deploymentService;
    }

    @GetMapping("/deployment-templates")
    public List<DeploymentTemplateSummary> listTemplates() {
        return deploymentService.listTemplates();
    }

    @GetMapping("/deployments")
    public List<DeploymentSummary> listDeployments() {
        return deploymentService.listDeployments();
    }

    @PostMapping("/deployments")
    @ResponseStatus(HttpStatus.CREATED)
    public DeploymentSummary createDeployment(@Valid @RequestBody CreateDeploymentRequest request) {
        return deploymentService.createDeployment(request);
    }

    @GetMapping("/deployments/{deploymentId}/draft")
    public DeploymentDraftResponse getActiveDraft(@PathVariable String deploymentId) {
        return deploymentService.getActiveDraftForDeployment(deploymentId);
    }

    @PutMapping("/deployment-drafts/{draftId}")
    public DeploymentDraftResponse updateDraft(@PathVariable String draftId,
                                               @RequestBody UpdateDeploymentDraftRequest request) {
        return deploymentService.updateDraft(draftId, request);
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
                                                 @PathVariable String versionId) {
        return deploymentService.applyVersion(deploymentId, versionId);
    }

    @GetMapping("/deployments/{deploymentId}/releases")
    public List<DeploymentReleaseSummary> listReleases(@PathVariable String deploymentId) {
        return deploymentService.listReleases(deploymentId);
    }

    @GetMapping("/deployments/{deploymentId}/verification-runs")
    public List<DeploymentVerificationRunSummary> listVerificationRuns(@PathVariable String deploymentId) {
        return deploymentService.listVerificationRuns(deploymentId);
    }

    @PostMapping("/deployments/{deploymentId}/verification-runs/recheck")
    @ResponseStatus(HttpStatus.CREATED)
    public DeploymentVerificationRunSummary rerunVerification(@PathVariable String deploymentId) {
        return deploymentService.rerunVerification(deploymentId);
    }
}
