package com.ai.fabric.platform.backend.marketplace.web;

import com.ai.fabric.platform.backend.marketplace.model.DeploymentMarketplacePluginInstallSummary;
import com.ai.fabric.platform.backend.marketplace.model.DeploymentMarketplacePluginImpactSummary;
import com.ai.fabric.platform.backend.marketplace.model.InstallDeploymentMarketplacePluginRequest;
import com.ai.fabric.platform.backend.marketplace.service.DeploymentMarketplaceInstallService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/deployments/{deploymentId}/marketplace-installs")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR','CUSTOMER_ADMIN')")
public class DeploymentMarketplaceInstallController {

    private final DeploymentMarketplaceInstallService deploymentMarketplaceInstallService;

    public DeploymentMarketplaceInstallController(DeploymentMarketplaceInstallService deploymentMarketplaceInstallService) {
        this.deploymentMarketplaceInstallService = deploymentMarketplaceInstallService;
    }

    @GetMapping
    public List<DeploymentMarketplacePluginInstallSummary> listInstalls(@PathVariable String deploymentId) {
        return deploymentMarketplaceInstallService.listInstalls(deploymentId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DeploymentMarketplacePluginInstallSummary installPlugin(@PathVariable String deploymentId,
                                                                   @RequestBody InstallDeploymentMarketplacePluginRequest request) {
        return deploymentMarketplaceInstallService.installPlugin(deploymentId, request);
    }

    @PostMapping("/preview")
    public DeploymentMarketplacePluginImpactSummary previewInstall(@PathVariable String deploymentId,
                                                                  @RequestBody InstallDeploymentMarketplacePluginRequest request) {
        return deploymentMarketplaceInstallService.previewInstallImpact(deploymentId, request);
    }

    @DeleteMapping("/{installId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void uninstallPlugin(@PathVariable String deploymentId,
                                @PathVariable String installId) {
        deploymentMarketplaceInstallService.uninstallPlugin(deploymentId, installId);
    }
}
