package com.ai.fabric.platform.backend.marketplace.web;

import com.ai.fabric.platform.backend.deployment.model.DeploymentSummary;
import com.ai.fabric.platform.backend.marketplace.model.MarketplaceTemplateBootstrapRequest;
import com.ai.fabric.platform.backend.marketplace.model.MarketplacePluginSummary;
import com.ai.fabric.platform.backend.marketplace.model.MarketplacePluginVersionSummary;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceCatalogService;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceShellModuleRegistry;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/marketplace/plugins")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR','CUSTOMER_ADMIN')")
public class MarketplaceController {

    private final MarketplaceCatalogService marketplaceCatalogService;
    private final MarketplaceShellModuleRegistry marketplaceShellModuleRegistry;

    public MarketplaceController(MarketplaceCatalogService marketplaceCatalogService,
                                 MarketplaceShellModuleRegistry marketplaceShellModuleRegistry) {
        this.marketplaceCatalogService = marketplaceCatalogService;
        this.marketplaceShellModuleRegistry = marketplaceShellModuleRegistry;
    }

    @GetMapping
    public List<MarketplacePluginSummary> listPlugins() {
        return marketplaceCatalogService.listPlugins();
    }

    @GetMapping("/{pluginId}/versions")
    public List<MarketplacePluginVersionSummary> listPluginVersions(@PathVariable String pluginId) {
        return marketplaceCatalogService.listVersions(pluginId);
    }

    @GetMapping("/shell-modules")
    public List<String> listAllowedShellModules() {
        return marketplaceShellModuleRegistry.allowedModuleIds().stream().sorted().toList();
    }

    @PostMapping("/{pluginId}/template-bootstrap")
    @ResponseStatus(HttpStatus.CREATED)
    public DeploymentSummary bootstrapTemplatePlugin(@PathVariable String pluginId,
                                                     @Valid @RequestBody MarketplaceTemplateBootstrapRequest request) {
        return marketplaceCatalogService.bootstrapFromTemplatePlugin(pluginId, request);
    }
}
