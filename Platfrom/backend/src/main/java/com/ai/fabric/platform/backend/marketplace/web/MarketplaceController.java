package com.ai.fabric.platform.backend.marketplace.web;

import com.ai.fabric.platform.backend.marketplace.model.CreateDeploymentMarketplaceInstallRequest;
import com.ai.fabric.platform.backend.marketplace.model.CreateMarketplaceTemplateBootstrapRequest;
import com.ai.fabric.platform.backend.marketplace.model.DeploymentMarketplaceImpactSummary;
import com.ai.fabric.platform.backend.marketplace.model.DeploymentMarketplaceInstallResolutionSummary;
import com.ai.fabric.platform.backend.marketplace.model.DeploymentMarketplaceInstallSummary;
import com.ai.fabric.platform.backend.marketplace.model.MarketplaceCategorySummary;
import com.ai.fabric.platform.backend.marketplace.model.MarketplacePluginDetailSummary;
import com.ai.fabric.platform.backend.marketplace.model.MarketplacePluginSummary;
import com.ai.fabric.platform.backend.marketplace.model.MarketplacePluginVersionSummary;
import com.ai.fabric.platform.backend.marketplace.model.UpdateDeploymentMarketplaceEntitlementRequest;
import com.ai.fabric.platform.backend.marketplace.model.UpdateDeploymentMarketplaceInstallRequest;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceTemplateBootstrapService;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSummary;
import com.ai.fabric.platform.backend.marketplace.service.DeploymentMarketplaceInstallService;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceCatalogService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR','CUSTOMER_ADMIN')")
public class MarketplaceController {

    private final MarketplaceCatalogService marketplaceCatalogService;
    private final DeploymentMarketplaceInstallService deploymentMarketplaceInstallService;
    private final MarketplaceTemplateBootstrapService marketplaceTemplateBootstrapService;

    public MarketplaceController(MarketplaceCatalogService marketplaceCatalogService,
                                 DeploymentMarketplaceInstallService deploymentMarketplaceInstallService,
                                 MarketplaceTemplateBootstrapService marketplaceTemplateBootstrapService) {
        this.marketplaceCatalogService = marketplaceCatalogService;
        this.deploymentMarketplaceInstallService = deploymentMarketplaceInstallService;
        this.marketplaceTemplateBootstrapService = marketplaceTemplateBootstrapService;
    }

    @GetMapping("/marketplace/plugins")
    public List<MarketplacePluginSummary> listPlugins() {
        return marketplaceCatalogService.listPlugins();
    }

    @GetMapping("/marketplace/plugins/{pluginId}")
    public MarketplacePluginDetailSummary getPlugin(@PathVariable String pluginId) {
        return marketplaceCatalogService.getPlugin(pluginId);
    }

    @GetMapping("/marketplace/plugins/{pluginId}/versions/{version}")
    public MarketplacePluginVersionSummary getPluginVersion(@PathVariable String pluginId,
                                                            @PathVariable String version) {
        return marketplaceCatalogService.getPluginVersion(pluginId, version);
    }

    @GetMapping("/marketplace/categories")
    public List<MarketplaceCategorySummary> listCategories() {
        return marketplaceCatalogService.listCategories();
    }

    @PostMapping("/marketplace/templates/{pluginId}/bootstrap")
    @ResponseStatus(HttpStatus.CREATED)
    public DeploymentSummary bootstrapTemplatePlugin(@PathVariable String pluginId,
                                                     @Valid @RequestBody CreateMarketplaceTemplateBootstrapRequest request) {
        return marketplaceTemplateBootstrapService.bootstrap(pluginId, request);
    }

    @GetMapping("/deployments/{deploymentId}/marketplace-installs")
    public List<DeploymentMarketplaceInstallSummary> listDeploymentInstalls(@PathVariable String deploymentId) {
        return deploymentMarketplaceInstallService.listInstalls(deploymentId);
    }

    @GetMapping("/deployments/{deploymentId}/marketplace-impact")
    public DeploymentMarketplaceImpactSummary getDeploymentImpact(@PathVariable String deploymentId) {
        return deploymentMarketplaceInstallService.getImpact(deploymentId);
    }

    @PostMapping("/deployments/{deploymentId}/marketplace-installs")
    @ResponseStatus(HttpStatus.CREATED)
    public DeploymentMarketplaceInstallSummary createDeploymentInstall(@PathVariable String deploymentId,
                                                                       @Valid @RequestBody CreateDeploymentMarketplaceInstallRequest request) {
        return deploymentMarketplaceInstallService.createInstall(deploymentId, request);
    }

    @PutMapping("/deployments/{deploymentId}/marketplace-installs/{installId}")
    public DeploymentMarketplaceInstallSummary updateDeploymentInstall(@PathVariable String deploymentId,
                                                                       @PathVariable String installId,
                                                                       @RequestBody UpdateDeploymentMarketplaceInstallRequest request) {
        return deploymentMarketplaceInstallService.updateInstall(deploymentId, installId, request);
    }

    @PutMapping("/deployments/{deploymentId}/marketplace-installs/{installId}/entitlement")
    public DeploymentMarketplaceInstallSummary updateDeploymentInstallEntitlement(@PathVariable String deploymentId,
                                                                                  @PathVariable String installId,
                                                                                  @RequestBody UpdateDeploymentMarketplaceEntitlementRequest request) {
        return deploymentMarketplaceInstallService.updateEntitlement(deploymentId, installId, request);
    }

    @DeleteMapping("/deployments/{deploymentId}/marketplace-installs/{installId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDeploymentInstall(@PathVariable String deploymentId,
                                        @PathVariable String installId) {
        deploymentMarketplaceInstallService.deleteInstall(deploymentId, installId);
    }

    @PostMapping("/deployments/{deploymentId}/marketplace-installs/{installId}/resolve")
    public DeploymentMarketplaceInstallResolutionSummary resolveDeploymentInstall(@PathVariable String deploymentId,
                                                                                  @PathVariable String installId) {
        return deploymentMarketplaceInstallService.resolveInstall(deploymentId, installId);
    }
}
