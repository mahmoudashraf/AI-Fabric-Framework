package com.ai.fabric.platform.backend.deployment.web;

import com.ai.fabric.platform.backend.deployment.model.DeploymentDeletionOperationSummary;
import com.ai.fabric.platform.backend.deployment.service.DeploymentDeletionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/platform/notifications")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformNotificationController {

    private final DeploymentDeletionService deploymentDeletionService;

    public PlatformNotificationController(DeploymentDeletionService deploymentDeletionService) {
        this.deploymentDeletionService = deploymentDeletionService;
    }

    @GetMapping("/deployment-deletions")
    public List<DeploymentDeletionOperationSummary> listDeploymentDeletionOperations(
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "100") int limit
    ) {
        return deploymentDeletionService.listOperations(status, limit);
    }

    @GetMapping("/deployment-deletions/{operationId}")
    public DeploymentDeletionOperationSummary getDeploymentDeletionOperation(@PathVariable String operationId) {
        return deploymentDeletionService.getOperation(operationId);
    }

    @PostMapping("/deployment-deletions/{operationId}/retry")
    public DeploymentDeletionOperationSummary retryDeploymentDeletionOperation(@PathVariable String operationId) {
        return deploymentDeletionService.retryFailedOperation(operationId);
    }
}
