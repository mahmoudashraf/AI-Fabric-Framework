package com.ai.fabric.platform.backend.marketplace.web;

import com.ai.fabric.platform.backend.marketplace.model.MarketplacePluginSummary;
import com.ai.fabric.platform.backend.marketplace.model.MarketplacePluginVersionSummary;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceCatalogService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/marketplace/plugins")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR','CUSTOMER_ADMIN')")
public class MarketplaceController {

    private final MarketplaceCatalogService marketplaceCatalogService;

    public MarketplaceController(MarketplaceCatalogService marketplaceCatalogService) {
        this.marketplaceCatalogService = marketplaceCatalogService;
    }

    @GetMapping
    public List<MarketplacePluginSummary> listPlugins() {
        return marketplaceCatalogService.listPlugins();
    }

    @GetMapping("/{pluginId}/versions")
    public List<MarketplacePluginVersionSummary> listPluginVersions(@PathVariable String pluginId) {
        return marketplaceCatalogService.listVersions(pluginId);
    }
}
