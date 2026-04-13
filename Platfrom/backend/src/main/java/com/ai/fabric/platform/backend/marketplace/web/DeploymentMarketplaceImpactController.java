package com.ai.fabric.platform.backend.marketplace.web;

import com.ai.fabric.platform.backend.marketplace.model.DeploymentMarketplaceImpactSnapshot;
import com.ai.fabric.platform.backend.marketplace.service.DeploymentMarketplaceInstallService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/deployments/{deploymentId}/marketplace-impact")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR','CUSTOMER_ADMIN')")
public class DeploymentMarketplaceImpactController {

    private final DeploymentMarketplaceInstallService deploymentMarketplaceInstallService;

    public DeploymentMarketplaceImpactController(DeploymentMarketplaceInstallService deploymentMarketplaceInstallService) {
        this.deploymentMarketplaceInstallService = deploymentMarketplaceInstallService;
    }

    @GetMapping
    public DeploymentMarketplaceImpactSnapshot getImpact(@PathVariable String deploymentId) {
        return deploymentMarketplaceInstallService.getImpactSnapshot(deploymentId);
    }
}
