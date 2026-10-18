package com.ai.fabric.platform.backend.deployment.web;

import com.ai.fabric.platform.backend.deployment.model.ExistingVectorBindingContracts.Request;
import com.ai.fabric.platform.backend.deployment.model.ExistingVectorBindingContracts.Summary;
import com.ai.fabric.platform.backend.deployment.service.DeploymentExistingVectorBindingService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/deployments/{deploymentId}/targets/{targetId}/existing-vector-binding")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class DeploymentExistingVectorBindingController {
    private final DeploymentExistingVectorBindingService service;

    public DeploymentExistingVectorBindingController(DeploymentExistingVectorBindingService service) { this.service = service; }

    @PostMapping public Summary bind(@PathVariable String deploymentId, @PathVariable String targetId, @RequestBody Request request) {
        return service.bind(deploymentId, targetId, request);
    }

    @GetMapping public Summary get(@PathVariable String deploymentId, @PathVariable String targetId) {
        return service.get(deploymentId, targetId);
    }
}
