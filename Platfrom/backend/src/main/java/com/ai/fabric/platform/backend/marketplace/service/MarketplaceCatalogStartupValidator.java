package com.ai.fabric.platform.backend.marketplace.service;

import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginVersionEntity;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginRepository;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginVersionRepository;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class MarketplaceCatalogStartupValidator implements SmartInitializingSingleton {

    private final MarketplacePluginRepository pluginRepository;
    private final MarketplacePluginVersionRepository versionRepository;
    private final MarketplaceManifestService manifestService;

    public MarketplaceCatalogStartupValidator(MarketplacePluginRepository pluginRepository,
                                              MarketplacePluginVersionRepository versionRepository,
                                              MarketplaceManifestService manifestService) {
        this.pluginRepository = pluginRepository;
        this.versionRepository = versionRepository;
        this.manifestService = manifestService;
    }

    @Override
    public void afterSingletonsInstantiated() {
        Map<String, MarketplacePluginEntity> pluginsById = pluginRepository.findAll().stream()
            .collect(Collectors.toMap(MarketplacePluginEntity::getId, Function.identity()));
        for (MarketplacePluginVersionEntity version : versionRepository.findAll()) {
            MarketplacePluginEntity plugin = pluginsById.get(version.getPluginId());
            if (plugin == null) {
                throw new IllegalStateException(
                    "Marketplace plugin version " + version.getId() + " references missing plugin " + version.getPluginId()
                );
            }
            manifestService.parseAndValidate(plugin, version);
        }
    }
}
