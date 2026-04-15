package com.ai.fabric.platform.backend.marketplace.service;

import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginVersionEntity;
import com.ai.fabric.platform.backend.marketplace.model.MarketplaceCategorySummary;
import com.ai.fabric.platform.backend.marketplace.model.MarketplacePluginDetailSummary;
import com.ai.fabric.platform.backend.marketplace.model.MarketplacePluginSummary;
import com.ai.fabric.platform.backend.marketplace.model.MarketplacePluginVersionSummary;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginRepository;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class MarketplaceCatalogService {

    private final MarketplacePluginRepository pluginRepository;
    private final MarketplacePluginVersionRepository versionRepository;
    private final MarketplaceManifestService manifestService;

    public MarketplaceCatalogService(MarketplacePluginRepository pluginRepository,
                                     MarketplacePluginVersionRepository versionRepository,
                                     MarketplaceManifestService manifestService) {
        this.pluginRepository = pluginRepository;
        this.versionRepository = versionRepository;
        this.manifestService = manifestService;
    }

    public List<MarketplacePluginSummary> listPlugins() {
        List<MarketplacePluginEntity> plugins = pluginRepository.findAll().stream()
            .filter(plugin -> "ACTIVE".equalsIgnoreCase(plugin.getStatus()))
            .sorted(Comparator.comparing(MarketplacePluginEntity::getDisplayName, String.CASE_INSENSITIVE_ORDER))
            .toList();
        Map<String, MarketplacePluginVersionEntity> latestVersions = versionRepository.findAll().stream()
            .filter(version -> "PUBLISHED".equalsIgnoreCase(version.getStatus()))
            .collect(Collectors.toMap(
                MarketplacePluginVersionEntity::getPluginId,
                Function.identity(),
                (left, right) -> left.getPublishedAt().isAfter(right.getPublishedAt()) ? left : right
            ));
        return plugins.stream()
            .filter(plugin -> latestVersions.containsKey(plugin.getId()))
            .map(plugin -> toSummary(plugin, latestVersions.get(plugin.getId())))
            .toList();
    }

    public MarketplacePluginDetailSummary getPlugin(String pluginIdOrSlug) {
        MarketplacePluginEntity plugin = requirePlugin(pluginIdOrSlug);
        List<MarketplacePluginVersionEntity> versionEntities = versionRepository.findByPluginIdOrderByPublishedAtDesc(plugin.getId()).stream()
            .filter(version -> "PUBLISHED".equalsIgnoreCase(version.getStatus()))
            .toList();
        if (versionEntities.isEmpty()) {
            throw new ResponseStatusException(NOT_FOUND, "Marketplace plugin not found: " + pluginIdOrSlug);
        }
        List<MarketplacePluginVersionSummary> versions = versionEntities.stream()
            .map(version -> toVersionSummary(plugin, version))
            .toList();
        MarketplacePluginVersionEntity latest = versionEntities.isEmpty() ? null : versionEntities.get(0);
        return new MarketplacePluginDetailSummary(toSummary(plugin, latest), versions);
    }

    public MarketplacePluginVersionSummary getPluginVersion(String pluginIdOrSlug, String versionLabel) {
        MarketplacePluginEntity plugin = requirePlugin(pluginIdOrSlug);
        MarketplacePluginVersionEntity version = versionRepository.findByPluginIdAndVersion(plugin.getId(), versionLabel)
            .orElseThrow(() -> new ResponseStatusException(
                NOT_FOUND,
                "Marketplace plugin version not found: " + plugin.getId() + "@" + versionLabel
            ));
        if (!"PUBLISHED".equalsIgnoreCase(version.getStatus())) {
            throw new ResponseStatusException(
                NOT_FOUND,
                "Marketplace plugin version not found: " + plugin.getId() + "@" + versionLabel
            );
        }
        return toVersionSummary(plugin, version);
    }

    public List<MarketplaceCategorySummary> listCategories() {
        Map<String, Long> counts = pluginRepository.findAll().stream()
            .collect(Collectors.groupingBy(
                plugin -> normalizeType(plugin.getPluginType()),
                Collectors.counting()
            ));
        return List.of(
            category("template", "Templates", counts.getOrDefault("TEMPLATE", 0L)),
            category("action", "Actions", counts.getOrDefault("ACTION", 0L)),
            category("data", "Data", counts.getOrDefault("DATA", 0L))
        );
    }

    MarketplacePluginEntity requirePluginEntity(String pluginIdOrSlug) {
        return requirePlugin(pluginIdOrSlug);
    }

    MarketplacePluginVersionEntity requirePluginVersionEntity(String pluginId, String versionLabel) {
        return versionRepository.findByPluginIdAndVersion(pluginId, versionLabel)
            .orElseThrow(() -> new ResponseStatusException(
                NOT_FOUND,
                "Marketplace plugin version not found: " + pluginId + "@" + versionLabel
            ));
    }

    MarketplacePluginVersionEntity requirePluginVersionEntityById(String pluginVersionId) {
        return versionRepository.findById(pluginVersionId)
            .orElseThrow(() -> new ResponseStatusException(
                NOT_FOUND,
                "Marketplace plugin version not found: " + pluginVersionId
            ));
    }

    MarketplacePluginVersionEntity requireLatestPublishedVersionEntity(String pluginId) {
        return versionRepository.findByPluginIdOrderByPublishedAtDesc(pluginId).stream()
            .filter(version -> "PUBLISHED".equalsIgnoreCase(version.getStatus()))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(
                NOT_FOUND,
                "Marketplace plugin has no published versions: " + pluginId
            ));
    }

    MarketplacePluginSummary toSummary(MarketplacePluginEntity plugin, MarketplacePluginVersionEntity latestVersion) {
        MarketplaceManifestService.ParsedMarketplaceManifest parsed = latestVersion == null
            ? null
            : manifestService.parseAndValidate(plugin, latestVersion);
        return new MarketplacePluginSummary(
            plugin.getId(),
            plugin.getSlug(),
            plugin.getDisplayName(),
            normalizeType(plugin.getPluginType()),
            plugin.getPublisherSlug(),
            plugin.getPublisherDisplayName(),
            plugin.getShortDescription(),
            plugin.getStatus(),
            latestVersion == null ? null : latestVersion.getVersion(),
            parsed == null ? new com.ai.fabric.platform.backend.marketplace.model.MarketplacePluginPricingSummary("FREE", null, null, null, null, false) : parsed.pricing(),
            List.of(normalizeType(plugin.getPluginType()).toLowerCase(Locale.ROOT)),
            parsed == null ? null : parsed.contributions(),
            plugin.getUpdatedAt()
        );
    }

    private MarketplacePluginVersionSummary toVersionSummary(MarketplacePluginEntity plugin, MarketplacePluginVersionEntity version) {
        MarketplaceManifestService.ParsedMarketplaceManifest parsed = manifestService.parseAndValidate(plugin, version);
        return new MarketplacePluginVersionSummary(
            version.getId(),
            plugin.getId(),
            version.getVersion(),
            version.getReleaseChannel(),
            version.getStatus(),
            parsed.manifest(),
            parsed.pricing(),
            parsed.compatibility(),
            parsed.installForm(),
            parsed.permissions(),
            parsed.contributions(),
            parsed.recommendedPluginIds(),
            version.getPublishedAt()
        );
    }

    private MarketplacePluginEntity requirePlugin(String pluginIdOrSlug) {
        return resolvePlugin(pluginIdOrSlug)
            .orElseThrow(() -> new ResponseStatusException(
                NOT_FOUND,
                "Marketplace plugin not found: " + pluginIdOrSlug
            ));
    }

    private Optional<MarketplacePluginEntity> resolvePlugin(String pluginIdOrSlug) {
        return pluginRepository.findById(pluginIdOrSlug)
            .or(() -> pluginRepository.findBySlugIgnoreCase(pluginIdOrSlug));
    }

    private MarketplaceCategorySummary category(String id, String label, long pluginCount) {
        return new MarketplaceCategorySummary(id, label, pluginCount);
    }

    private String normalizeType(String pluginType) {
        return pluginType == null ? "" : pluginType.trim().toUpperCase(Locale.ROOT);
    }
}
