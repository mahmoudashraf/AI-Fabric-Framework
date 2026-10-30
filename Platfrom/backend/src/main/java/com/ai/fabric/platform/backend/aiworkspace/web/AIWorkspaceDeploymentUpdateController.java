package com.ai.fabric.platform.backend.aiworkspace.web;

import com.ai.fabric.platform.backend.aiworkspace.service.AIWorkspaceDeploymentUpdateService;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVersionSummary;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/platform/customers/{customerId}/ai-workspace-installations/{installationId}/deployment-update")
@PreAuthorize("hasAnyRole('CUSTOMER_ADMIN','PLATFORM_ADMIN')")
public class AIWorkspaceDeploymentUpdateController {
    private final AIWorkspaceDeploymentUpdateService service;
    public AIWorkspaceDeploymentUpdateController(AIWorkspaceDeploymentUpdateService service) { this.service = service; }

    @PostMapping("/review")
    public AIWorkspaceDeploymentUpdateService.Review review(@PathVariable String customerId, @PathVariable String installationId) {
        return service.review(customerId, installationId);
    }

    @PostMapping("/publish")
    public DeploymentVersionSummary publish(@PathVariable String customerId, @PathVariable String installationId,
                                            @RequestBody AIWorkspaceDeploymentUpdateService.ReviewedPublish request) {
        return service.publish(customerId, installationId, request);
    }
}
