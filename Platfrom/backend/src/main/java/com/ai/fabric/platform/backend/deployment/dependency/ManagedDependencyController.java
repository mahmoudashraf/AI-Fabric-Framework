package com.ai.fabric.platform.backend.deployment.dependency;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/deployments/{deploymentId}/targets/{targetProfileId}/managed-dependencies")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','CUSTOMER_ADMIN')")
public class ManagedDependencyController {
    private final ManagedDependencyLifecycleService lifecycle;
    public ManagedDependencyController(ManagedDependencyLifecycleService lifecycle) {
        this.lifecycle = lifecycle;
    }
    @GetMapping
    public ManagedDependencyLifecycleService.Summary status(@PathVariable String deploymentId, @PathVariable String targetProfileId) {
        return lifecycle.status(deploymentId, targetProfileId);
    }
}
