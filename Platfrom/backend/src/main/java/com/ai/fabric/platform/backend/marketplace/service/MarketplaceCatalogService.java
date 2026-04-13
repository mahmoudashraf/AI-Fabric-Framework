package com.ai.fabric.platform.backend.marketplace.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentDraftEntity;
import com.ai.fabric.platform.backend.deployment.model.CreateDeploymentRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSummary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentDraftRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginVersionEntity;
import com.ai.fabric.platform.backend.marketplace.model.MarketplaceTemplateBootstrapRequest;
import com.ai.fabric.platform.backend.marketplace.model.MarketplacePluginSummary;
import com.ai.fabric.platform.backend.marketplace.model.MarketplacePluginVersionSummary;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginRepository;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginVersionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class MarketplaceCatalogService {

    private final MarketplacePluginRepository marketplacePluginRepository;
    private final MarketplacePluginVersionRepository marketplacePluginVersionRepository;
    private final DeploymentService deploymentService;
    private final DeploymentDraftRepository deploymentDraftRepository;
    private final ObjectMapper objectMapper;

    public MarketplaceCatalogService(MarketplacePluginRepository marketplacePluginRepository,
                                     MarketplacePluginVersionRepository marketplacePluginVersionRepository,
                                     DeploymentService deploymentService,
                                     DeploymentDraftRepository deploymentDraftRepository,
                                     ObjectMapper objectMapper) {
        this.marketplacePluginRepository = marketplacePluginRepository;
        this.marketplacePluginVersionRepository = marketplacePluginVersionRepository;
        this.deploymentService = deploymentService;
        this.deploymentDraftRepository = deploymentDraftRepository;
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

    @Transactional
    public DeploymentSummary bootstrapFromTemplatePlugin(String pluginId, MarketplaceTemplateBootstrapRequest request) {
        MarketplacePluginEntity plugin = marketplacePluginRepository.findById(pluginId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Marketplace plugin not found: " + pluginId));
        if (!"TEMPLATE".equalsIgnoreCase(plugin.getPluginType())) {
            throw new ResponseStatusException(BAD_REQUEST, "Plugin is not a template plugin: " + pluginId);
        }
        if (!"ACTIVE".equalsIgnoreCase(plugin.getStatus())) {
            throw new ResponseStatusException(BAD_REQUEST, "Marketplace plugin is not active: " + pluginId);
        }

        MarketplacePluginVersionEntity version = resolveTemplateVersion(pluginId, request.pluginVersionId());
        JsonNode manifest = readJson(version.getManifestJson());
        JsonNode templateContribution = manifest.path("contributions").path("template");
        String curatedModuleId = templateContribution.path("curatedModuleId").asText(null);

        DeploymentSummary created = deploymentService.createDeployment(
            new CreateDeploymentRequest(
                request.name().trim(),
                request.environment().trim(),
                request.templateId().trim(),
                curatedModuleId,
                request.vectorProvisioningMode(),
                request.customerId(),
                request.tenantId()
            )
        );
        applyTemplateShellContribution(created.id(), templateContribution.path("shell"), plugin, version);
        return created;
    }

    private MarketplacePluginVersionEntity resolveTemplateVersion(String pluginId, String requestedVersionId) {
        if (requestedVersionId != null && !requestedVersionId.isBlank()) {
            MarketplacePluginVersionEntity requested = marketplacePluginVersionRepository.findById(requestedVersionId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Marketplace plugin version not found: " + requestedVersionId));
            if (!pluginId.equals(requested.getPluginId())) {
                throw new ResponseStatusException(BAD_REQUEST, "Plugin version does not belong to plugin: " + requestedVersionId);
            }
            if (!"PUBLISHED".equalsIgnoreCase(requested.getStatus())) {
                throw new ResponseStatusException(BAD_REQUEST, "Marketplace plugin version is not published: " + requestedVersionId);
            }
            return requested;
        }
        return marketplacePluginVersionRepository.findTopByPluginIdOrderByPublishedAtDesc(pluginId)
            .filter(version -> "PUBLISHED".equalsIgnoreCase(version.getStatus()))
            .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "No published template plugin version found for: " + pluginId));
    }

    private void applyTemplateShellContribution(String deploymentId,
                                                JsonNode templateShell,
                                                MarketplacePluginEntity plugin,
                                                MarketplacePluginVersionEntity version) {
        if (!templateShell.isObject()) {
            return;
        }
        DeploymentDraftEntity draft = deploymentDraftRepository.findTopByDeploymentIdOrderByRevisionNumberDesc(deploymentId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "No draft found for deployment: " + deploymentId));
        ObjectNode shellConfig = readJsonAsObject(draft.getShellConfigJson());
        ObjectNode marketplace = shellConfig.with("marketplace");
        ObjectNode templateBootstrap = marketplace.with("templateBootstrap");

        ArrayNode moduleRefs = objectMapper.createArrayNode();
        textValues(templateShell.path("enabledModuleIds")).forEach(moduleRefs::add);
        templateBootstrap.set("moduleRefs", moduleRefs);

        ObjectNode defaults = objectMapper.createObjectNode();
        templateShell.fields().forEachRemaining(entry -> {
            if (!"enabledModuleIds".equals(entry.getKey())) {
                defaults.set(entry.getKey(), entry.getValue());
            }
        });
        templateBootstrap.set("defaults", defaults);
        templateBootstrap.put("pluginId", plugin.getId());
        templateBootstrap.put("pluginVersionId", version.getId());
        templateBootstrap.put("pluginVersion", version.getVersion());

        draft.setShellConfigJson(writeJson(shellConfig));
        draft.setUpdatedAt(Instant.now());
        deploymentDraftRepository.save(draft);
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

    private ObjectNode readJsonAsObject(String value) {
        JsonNode node = readJson(value);
        if (node.isObject()) {
            return (ObjectNode) node.deepCopy();
        }
        return objectMapper.createObjectNode();
    }

    private List<String> textValues(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        return java.util.stream.StreamSupport.stream(node.spliterator(), false)
            .filter(JsonNode::isTextual)
            .map(JsonNode::asText)
            .distinct()
            .toList();
    }

    private String writeJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to write marketplace template shell contribution JSON.", ex);
        }
    }
}
