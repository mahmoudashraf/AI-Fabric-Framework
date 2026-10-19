package com.ai.fabric.platform.backend.deployment.web;

import com.ai.fabric.platform.backend.deployment.model.DeploymentAiStateStorageContracts.Request;
import com.ai.fabric.platform.backend.deployment.model.DeploymentAiStateStorageContracts.Summary;
import com.ai.fabric.platform.backend.deployment.service.DeploymentAiStateStorageBindingService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/deployments/{deploymentId}/targets/{targetId}/ai-state-storage-binding")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class DeploymentAiStateStorageBindingController {
    private final DeploymentAiStateStorageBindingService service;

    public DeploymentAiStateStorageBindingController(DeploymentAiStateStorageBindingService service) { this.service = service; }

    @PostMapping
    public Summary bind(@PathVariable String deploymentId, @PathVariable String targetId, @RequestBody Request request) {
        return service.bind(deploymentId, targetId, request);
    }

    @GetMapping
    public Summary get(@PathVariable String deploymentId, @PathVariable String targetId, @RequestParam String versionId) {
        return service.get(deploymentId, targetId, versionId);
    }
}
