package com.ai.fabric.platform.backend.vectorization.web;

import com.ai.fabric.platform.backend.vectorization.model.CreateVectorizationRunRequest;
import com.ai.fabric.platform.backend.vectorization.model.CreateVectorizationVerificationRunRequest;
import com.ai.fabric.platform.backend.vectorization.model.RotateVectorizationRunnerTokenRequest;
import com.ai.fabric.platform.backend.vectorization.model.UpdateVectorizationSyncStateRequest;
import com.ai.fabric.platform.backend.vectorization.model.UpsertVectorizationPlanRequest;
import com.ai.fabric.platform.backend.vectorization.model.UpsertVectorizationSourceConnectionRequest;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationOverviewSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationPlanSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationPreviewSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationRunDetailsSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationRunSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationRunnerTokenSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationSourceConnectionSummary;
import com.ai.fabric.platform.backend.vectorization.service.VectorizationService;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationVerificationRunDetailsSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationVerificationRunSummary;
import com.ai.fabric.platform.backend.vectorization.service.VectorizationVerificationService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/deployments/{deploymentId}/vectorization")
public class VectorizationController {

    private final VectorizationService vectorizationService;
    private final VectorizationVerificationService vectorizationVerificationService;

    public VectorizationController(VectorizationService vectorizationService,
                                   VectorizationVerificationService vectorizationVerificationService) {
        this.vectorizationService = vectorizationService;
        this.vectorizationVerificationService = vectorizationVerificationService;
    }

    @GetMapping
    public VectorizationOverviewSummary getOverview(@PathVariable String deploymentId) {
        return vectorizationService.getOverview(deploymentId);
    }

    @GetMapping("/preview")
    public VectorizationPreviewSummary preview(@PathVariable String deploymentId) {
        return vectorizationService.preview(deploymentId);
    }

    @PutMapping("/connection")
    public VectorizationSourceConnectionSummary upsertConnection(@PathVariable String deploymentId,
                                                                @Valid @RequestBody UpsertVectorizationSourceConnectionRequest request) {
        return vectorizationService.upsertSourceConnection(deploymentId, request);
    }

    @PutMapping("/plan")
    public VectorizationPlanSummary upsertPlan(@PathVariable String deploymentId,
                                               @Valid @RequestBody UpsertVectorizationPlanRequest request) {
        return vectorizationService.upsertPlan(deploymentId, request);
    }

    @PostMapping("/runs")
    @ResponseStatus(HttpStatus.CREATED)
    public VectorizationRunSummary createRun(@PathVariable String deploymentId,
                                             @Valid @RequestBody CreateVectorizationRunRequest request) {
        return vectorizationService.createRun(deploymentId, request);
    }

    @GetMapping("/runs/{runId}")
    public VectorizationRunDetailsSummary getRun(@PathVariable String deploymentId, @PathVariable String runId) {
        return vectorizationService.getRunDetails(deploymentId, runId);
    }

    @PostMapping("/runs/{runId}/pause")
    public VectorizationRunSummary pauseRun(@PathVariable String deploymentId, @PathVariable String runId) {
        return vectorizationService.updateRunCommand(deploymentId, runId, "PAUSE");
    }

    @PostMapping("/runs/{runId}/resume")
    public VectorizationRunSummary resumeRun(@PathVariable String deploymentId, @PathVariable String runId) {
        return vectorizationService.updateRunCommand(deploymentId, runId, "RESUME");
    }

    @PostMapping("/runs/{runId}/cancel")
    public VectorizationRunSummary cancelRun(@PathVariable String deploymentId, @PathVariable String runId) {
        return vectorizationService.updateRunCommand(deploymentId, runId, "CANCEL");
    }

    @PostMapping("/runs/{runId}/retry")
    public VectorizationRunSummary retryRun(@PathVariable String deploymentId, @PathVariable String runId) {
        return vectorizationService.updateRunCommand(deploymentId, runId, "RETRY");
    }

    @PostMapping("/sync-state")
    public VectorizationPlanSummary updateSyncState(@PathVariable String deploymentId,
                                                    @Valid @RequestBody UpdateVectorizationSyncStateRequest request) {
        return vectorizationService.updateSyncState(deploymentId, request);
    }

    @PostMapping("/runner/token")
    public VectorizationRunnerTokenSummary rotateRunnerToken(@PathVariable String deploymentId,
                                                             @RequestBody(required = false) RotateVectorizationRunnerTokenRequest request) {
        return vectorizationService.rotateRunnerToken(
            deploymentId,
            request == null ? new RotateVectorizationRunnerTokenRequest(null, null) : request
        );
    }

    @GetMapping("/verifications")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public java.util.List<VectorizationVerificationRunSummary> listVerifications(@PathVariable String deploymentId) {
        return vectorizationVerificationService.listRuns(deploymentId);
    }

    @PostMapping("/verifications")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public VectorizationVerificationRunSummary createVerification(@PathVariable String deploymentId,
                                                                  @Valid @RequestBody CreateVectorizationVerificationRunRequest request) {
        return vectorizationVerificationService.createRun(deploymentId, request);
    }

    @GetMapping("/verifications/{verificationRunId}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public VectorizationVerificationRunDetailsSummary getVerification(@PathVariable String deploymentId,
                                                                      @PathVariable String verificationRunId) {
        return vectorizationVerificationService.getRunDetails(deploymentId, verificationRunId);
    }
}
