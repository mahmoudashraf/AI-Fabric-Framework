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
    private final CustomerPluginLibraryService library;

    public MarketplaceCatalogService(MarketplacePluginRepository pluginRepository,
                                     MarketplacePluginVersionRepository versionRepository,
                                     MarketplaceManifestService manifestService,
                                     CustomerPluginLibraryService library) {
        this.pluginRepository = pluginRepository;
        this.versionRepository = versionRepository;
        this.manifestService = manifestService;
        this.library = library;
    }

    public List<MarketplacePluginSummary> listPlugins() {
        return listForScope(null, true);
    }

    public List<MarketplacePluginSummary> listPlugins(String customerId, boolean includeDemo) {
        return listForScope(catalogueScope(customerId), includeDemo);
    }

    private List<MarketplacePluginSummary> listForScope(String customerId, boolean includeDemo) {
        List<MarketplacePluginEntity> plugins = pluginRepository.findAll().stream()
            .filter(plugin -> !MarketplacePluginOwnership.isPrivate(plugin)
                || (customerId != null && customerId.equals(plugin.getOwnerCustomerId())))
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
            .filter(plugin -> includeDemo || "CUSTOMER".equals(plugin.catalogueAudience()))
            .toList();
    }

    public MarketplacePluginDetailSummary getPlugin(String pluginIdOrSlug) {
        MarketplacePluginEntity plugin = requirePlugin(pluginIdOrSlug);
        MarketplacePluginOwnership.requireCustomer(plugin, null);
        return detail(plugin, pluginIdOrSlug);
    }

    public MarketplacePluginDetailSummary getPlugin(String pluginIdOrSlug, String customerId) {
        MarketplacePluginEntity plugin = requirePlugin(pluginIdOrSlug);
        MarketplacePluginOwnership.requireCustomer(plugin, catalogueScope(customerId));
        return detail(plugin, pluginIdOrSlug);
    }

    private MarketplacePluginDetailSummary detail(MarketplacePluginEntity plugin, String pluginIdOrSlug) {
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
        MarketplacePluginOwnership.requireCustomer(plugin, null);
        return versionSummary(plugin, versionLabel);
    }

    public MarketplacePluginVersionSummary getPluginVersion(String pluginIdOrSlug, String versionLabel, String customerId) {
        MarketplacePluginEntity plugin = requirePlugin(pluginIdOrSlug);
        MarketplacePluginOwnership.requireCustomer(plugin, catalogueScope(customerId));
        return versionSummary(plugin, versionLabel);
    }

    private MarketplacePluginVersionSummary versionSummary(MarketplacePluginEntity plugin, String versionLabel) {
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

    public String resolveLatestPublishedVersionLabel(String pluginIdOrSlug) {
        MarketplacePluginEntity plugin = requirePlugin(pluginIdOrSlug);
        MarketplacePluginOwnership.requireCustomer(plugin, null);
        return requireLatestPublishedVersionEntity(plugin.getId()).getVersion();
    }

    public List<MarketplaceCategorySummary> listCategories() {
        Map<String, Long> counts = pluginRepository.findAll().stream()
            .filter(plugin -> !MarketplacePluginOwnership.isPrivate(plugin))
            .collect(Collectors.groupingBy(
                plugin -> normalizeType(plugin.getPluginType()),
                Collectors.counting()
            ));
        return List.of(
            category("template", "Templates", counts.getOrDefault("TEMPLATE", 0L)),
            category("specialist", "Specialists", counts.getOrDefault("SPECIALIST", 0L)),
            category("action", "Actions", counts.getOrDefault("ACTION", 0L)),
            category("data", "Data", counts.getOrDefault("DATA", 0L)),
            category("inference-profile", "Inference Profiles", counts.getOrDefault("INFERENCE_PROFILE", 0L)),
            category("integration", "Integrations", counts.getOrDefault("INTEGRATION", 0L))
        );
    }

    MarketplacePluginEntity requirePluginEntity(String pluginIdOrSlug) {
        return requirePlugin(pluginIdOrSlug);
    }

    MarketplacePluginEntity requirePluginEntityForUpdate(String pluginIdOrSlug) {
        MarketplacePluginEntity plugin = requirePlugin(pluginIdOrSlug);
        return pluginRepository.findByIdForUpdate(plugin.getId())
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Marketplace plugin not found."));
    }

    public void requireConfigurationOwnership(com.fasterxml.jackson.databind.JsonNode config, String customerId) {
        MarketplacePluginOwnership.requireReferences(config, customerId, pluginRepository, versionRepository);
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
        boolean privateVersion = parsed != null && MarketplacePluginOwnership.isPrivate(plugin);
        var presentation = privateVersion ? parsed.manifest().path("presentation") : null;
        return new MarketplacePluginSummary(
            plugin.getId(),
            plugin.getSlug(),
            privateVersion ? parsed.manifest().path("displayName").asText(plugin.getDisplayName()) : plugin.getDisplayName(),
            normalizeType(plugin.getPluginType()),
            plugin.getPublisherSlug(),
            plugin.getPublisherDisplayName(),
            privateVersion ? parsed.manifest().path("description").asText(plugin.getShortDescription()) : plugin.getShortDescription(),
            plugin.getStatus(),
            latestVersion == null ? null : latestVersion.getVersion(),
            parsed == null ? new com.ai.fabric.platform.backend.marketplace.model.MarketplacePluginPricingSummary("FREE", null, null, null, null, false) : parsed.pricing(),
            List.of(normalizeType(plugin.getPluginType()).toLowerCase(Locale.ROOT)),
            parsed == null ? null : parsed.contributions(),
            plugin.getUpdatedAt(),
            plugin.getOwnerCustomerId(), plugin.getVisibility(),
            privateVersion ? presentation.path("catalogueFamily").textValue() : plugin.getCatalogueFamily(),
            privateVersion ? presentation.path("catalogueAudience").asText("CUSTOMER") : plugin.getCatalogueAudience(),
            privateVersion ? presentation.path("adapterContract").textValue() : plugin.getAdapterContract()
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

    private String catalogueScope(String customerId) {
        var principal = com.ai.fabric.platform.backend.security.PlatformSecurityContext.currentPrincipal();
        if (customerId != null || (principal != null && principal.role()
            == com.ai.fabric.platform.backend.security.PlatformRole.CUSTOMER_ADMIN)) {
            return library.requireWorkspace(customerId);
        }
        return null;
    }

    private MarketplaceCategorySummary category(String id, String label, long pluginCount) {
        return new MarketplaceCategorySummary(id, label, pluginCount);
    }

    private String normalizeType(String pluginType) {
        return pluginType == null ? "" : pluginType.trim().toUpperCase(Locale.ROOT);
    }
}
