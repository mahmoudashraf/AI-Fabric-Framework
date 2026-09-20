package com.ai.fabric.platform.backend.deployment.web;

import com.ai.fabric.platform.backend.deployment.model.ApproveDeploymentBehaviorReadinessRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentBehaviorReadinessSummary;
import com.ai.fabric.platform.backend.deployment.model.EvaluateDeploymentBehaviorReadinessRequest;
import com.ai.fabric.platform.backend.deployment.model.WithdrawDeploymentBehaviorReadinessRequest;
import com.ai.fabric.platform.backend.deployment.service.DeploymentBehaviorReadinessService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR','CUSTOMER_ADMIN')")
public class DeploymentBehaviorReadinessController {

    private final DeploymentBehaviorReadinessService readinessService;

    public DeploymentBehaviorReadinessController(DeploymentBehaviorReadinessService readinessService) {
        this.readinessService = readinessService;
    }

    @GetMapping("/deployment-behavior-readiness")
    public List<DeploymentBehaviorReadinessSummary> list(
        @RequestParam(required = false) String behaviorType,
        @RequestParam(required = false) String templatePluginId
    ) {
        return readinessService.list(behaviorType, templatePluginId);
    }

    @GetMapping("/deployment-behavior-readiness/{candidateId}")
    public DeploymentBehaviorReadinessSummary get(@PathVariable String candidateId) {
        return readinessService.get(candidateId);
    }

    @PostMapping("/deployments/{deploymentId}/behavior-readiness/evaluate")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR')")
    public DeploymentBehaviorReadinessSummary evaluate(
        @PathVariable String deploymentId,
        @Valid @RequestBody EvaluateDeploymentBehaviorReadinessRequest request
    ) {
        return readinessService.evaluate(deploymentId, request);
    }

    @PostMapping("/deployment-behavior-readiness/{candidateId}/approve")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public DeploymentBehaviorReadinessSummary approve(
        @PathVariable String candidateId,
        @Valid @RequestBody ApproveDeploymentBehaviorReadinessRequest request
    ) {
        return readinessService.approve(candidateId, request);
    }

    @PostMapping("/deployment-behavior-readiness/{candidateId}/withdraw")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public DeploymentBehaviorReadinessSummary withdraw(
        @PathVariable String candidateId,
        @Valid @RequestBody WithdrawDeploymentBehaviorReadinessRequest request
    ) {
        return readinessService.withdraw(candidateId, request.reason());
    }
}
