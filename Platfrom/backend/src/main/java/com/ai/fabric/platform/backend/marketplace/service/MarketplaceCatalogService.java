package com.ai.fabric.platform.backend.marketplace.service;

import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginVersionEntity;
import com.ai.fabric.platform.backend.marketplace.model.MarketplacePluginSummary;
import com.ai.fabric.platform.backend.marketplace.model.MarketplacePluginVersionSummary;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginRepository;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginVersionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class MarketplaceCatalogService {

    private final MarketplacePluginRepository marketplacePluginRepository;
    private final MarketplacePluginVersionRepository marketplacePluginVersionRepository;
    private final ObjectMapper objectMapper;

    public MarketplaceCatalogService(MarketplacePluginRepository marketplacePluginRepository,
                                     MarketplacePluginVersionRepository marketplacePluginVersionRepository,
                                     ObjectMapper objectMapper) {
        this.marketplacePluginRepository = marketplacePluginRepository;
        this.marketplacePluginVersionRepository = marketplacePluginVersionRepository;
        this.objectMapper = objectMapper;
    }

    public List<MarketplacePluginSummary> listPlugins() {
        return marketplacePluginRepository.findAllByOrderByDisplayNameAsc().stream()
            .map(plugin -> toSummary(plugin, marketplacePluginVersionRepository.findTopByPluginIdOrderByPublishedAtDesc(plugin.getId()).orElse(null)))
            .toList();
    }

    public List<MarketplacePluginVersionSummary> listVersions(String pluginId) {
        MarketplacePluginEntity plugin = marketplacePluginRepository.findById(pluginId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Marketplace plugin not found: " + pluginId));
        return marketplacePluginVersionRepository.findByPluginIdOrderByPublishedAtDesc(plugin.getId()).stream()
            .map(this::toVersionSummary)
            .toList();
    }

    private MarketplacePluginSummary toSummary(MarketplacePluginEntity plugin,
                                               MarketplacePluginVersionEntity latestVersion) {
        return new MarketplacePluginSummary(
            plugin.getId(),
            plugin.getSlug(),
            plugin.getDisplayName(),
            plugin.getPluginType(),
            plugin.getPublisherSlug(),
            plugin.getPublisherDisplayName(),
            plugin.getShortDescription(),
            plugin.getStatus(),
            latestVersion == null ? null : toVersionSummary(latestVersion),
            plugin.getUpdatedAt()
        );
    }

    private MarketplacePluginVersionSummary toVersionSummary(MarketplacePluginVersionEntity version) {
        return new MarketplacePluginVersionSummary(
            version.getId(),
            version.getPluginId(),
            version.getVersion(),
            version.getReleaseChannel(),
            version.getStatus(),
            readJson(version.getManifestJson()),
            version.getPublishedAt()
        );
    }

    private JsonNode readJson(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read marketplace plugin manifest JSON.", ex);
        }
    }
}
