package com.ai.fabric.platform.backend.marketplace.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentAccessService;
import com.ai.fabric.platform.backend.marketplace.entity.DeploymentMarketplacePluginInstallEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginVersionEntity;
import com.ai.fabric.platform.backend.marketplace.model.DeploymentMarketplaceImpactSnapshot;
import com.ai.fabric.platform.backend.marketplace.model.DeploymentMarketplacePluginInstallSummary;
import com.ai.fabric.platform.backend.marketplace.model.DeploymentMarketplacePluginImpactSummary;
import com.ai.fabric.platform.backend.marketplace.model.InstallDeploymentMarketplacePluginRequest;
import com.ai.fabric.platform.backend.marketplace.repository.DeploymentMarketplacePluginInstallRepository;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginRepository;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginVersionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class DeploymentMarketplaceInstallService {

    private final DeploymentRepository deploymentRepository;
    private final DeploymentAccessService deploymentAccessService;
    private final DeploymentMarketplacePluginInstallRepository deploymentMarketplacePluginInstallRepository;
    private final MarketplacePluginRepository marketplacePluginRepository;
    private final MarketplacePluginVersionRepository marketplacePluginVersionRepository;
    private final PlatformAuditService platformAuditService;
    private final ObjectMapper objectMapper;

    public DeploymentMarketplaceInstallService(DeploymentRepository deploymentRepository,
                                               DeploymentAccessService deploymentAccessService,
                                               DeploymentMarketplacePluginInstallRepository deploymentMarketplacePluginInstallRepository,
                                               MarketplacePluginRepository marketplacePluginRepository,
                                               MarketplacePluginVersionRepository marketplacePluginVersionRepository,
                                               PlatformAuditService platformAuditService,
                                               ObjectMapper objectMapper) {
        this.deploymentRepository = deploymentRepository;
        this.deploymentAccessService = deploymentAccessService;
        this.deploymentMarketplacePluginInstallRepository = deploymentMarketplacePluginInstallRepository;
        this.marketplacePluginRepository = marketplacePluginRepository;
        this.marketplacePluginVersionRepository = marketplacePluginVersionRepository;
        this.platformAuditService = platformAuditService;
        this.objectMapper = objectMapper;
    }

    public List<DeploymentMarketplacePluginInstallSummary> listInstalls(String deploymentId) {
        DeploymentEntity deployment = requireDeploymentAccess(deploymentId);
        List<DeploymentMarketplacePluginInstallEntity> installs = deploymentMarketplacePluginInstallRepository.findByDeploymentIdOrderByCreatedAtAsc(deployment.getId());
        return toSummaries(installs);
    }

    public DeploymentMarketplaceImpactSnapshot getImpactSnapshot(String deploymentId) {
        DeploymentEntity deployment = requireDeploymentAccess(deploymentId);
        List<DeploymentMarketplacePluginInstallEntity> installs = deploymentMarketplacePluginInstallRepository
            .findByDeploymentIdOrderByCreatedAtAsc(deployment.getId());
        List<DeploymentMarketplacePluginImpactSummary> impacts = toImpactSummaries(installs);
        List<String> affectedConfigKeys = impacts.stream()
            .flatMap(impact -> impact.affectedConfigKeys().stream())
            .distinct()
            .toList();
        return new DeploymentMarketplaceImpactSnapshot(
            deployment.getId(),
            installs.size(),
            affectedConfigKeys,
            impacts
        );
    }

    public DeploymentMarketplacePluginImpactSummary previewInstallImpact(String deploymentId,
                                                                         InstallDeploymentMarketplacePluginRequest request) {
        if (request == null || request.pluginVersionId() == null || request.pluginVersionId().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "pluginVersionId is required.");
        }

        DeploymentEntity deployment = requireDeploymentEditorAccess(deploymentId);
        MarketplacePluginVersionEntity version = marketplacePluginVersionRepository.findById(request.pluginVersionId())
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Marketplace plugin version not found: " + request.pluginVersionId()));
        MarketplacePluginEntity plugin = marketplacePluginRepository.findById(version.getPluginId())
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Marketplace plugin not found: " + version.getPluginId()));

        if (!"ACTIVE".equalsIgnoreCase(plugin.getStatus())) {
            throw new ResponseStatusException(BAD_REQUEST, "Marketplace plugin is not active: " + plugin.getId());
        }
        if (!"PUBLISHED".equalsIgnoreCase(version.getStatus())) {
            throw new ResponseStatusException(BAD_REQUEST, "Marketplace plugin version is not published: " + version.getId());
        }

        DeploymentMarketplacePluginInstallEntity existingInstall = deploymentMarketplacePluginInstallRepository
            .findByDeploymentIdAndPluginId(deployment.getId(), plugin.getId())
            .orElse(null);

        return toImpactSummary(
            plugin,
            version,
            existingInstall == null ? "NEW_INSTALL" : "UPDATE",
            defaultObject(request.config()),
            defaultArray(request.secretRefs())
        );
    }

    @Transactional
    public DeploymentMarketplacePluginInstallSummary installPlugin(String deploymentId,
                                                                   InstallDeploymentMarketplacePluginRequest request) {
        if (request == null || request.pluginVersionId() == null || request.pluginVersionId().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "pluginVersionId is required.");
        }

        DeploymentEntity deployment = requireDeploymentEditorAccess(deploymentId);
        MarketplacePluginVersionEntity version = marketplacePluginVersionRepository.findById(request.pluginVersionId())
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Marketplace plugin version not found: " + request.pluginVersionId()));
        MarketplacePluginEntity plugin = marketplacePluginRepository.findById(version.getPluginId())
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Marketplace plugin not found: " + version.getPluginId()));

        if (!"ACTIVE".equalsIgnoreCase(plugin.getStatus())) {
            throw new ResponseStatusException(BAD_REQUEST, "Marketplace plugin is not active: " + plugin.getId());
        }
        if (!"PUBLISHED".equalsIgnoreCase(version.getStatus())) {
            throw new ResponseStatusException(BAD_REQUEST, "Marketplace plugin version is not published: " + version.getId());
        }

        Instant now = Instant.now();
        DeploymentMarketplacePluginInstallEntity install = deploymentMarketplacePluginInstallRepository
            .findByDeploymentIdAndPluginId(deployment.getId(), plugin.getId())
            .orElseGet(DeploymentMarketplacePluginInstallEntity::new);

        if (install.getId() == null) {
            install.setId("mpi-" + UUID.randomUUID().toString().substring(0, 8));
            install.setDeploymentId(deployment.getId());
            install.setPluginId(plugin.getId());
            install.setCreatedAt(now);
        }

        install.setPluginVersionId(version.getId());
        install.setStatus("INSTALLED");
        install.setConfigJson(writeJson(defaultObject(request.config())));
        install.setSecretRefsJson(writeJson(defaultArray(request.secretRefs())));
        install.setUpdatedAt(now);
        deploymentMarketplacePluginInstallRepository.save(install);

        platformAuditService.record(
            "DEPLOYMENT_MARKETPLACE_PLUGIN_INSTALLED",
            "DEPLOYMENT_MARKETPLACE_PLUGIN_INSTALL",
            install.getId(),
            Map.of(
                "deploymentId", deployment.getId(),
                "pluginId", plugin.getId(),
                "pluginVersionId", version.getId()
            )
        );

        return toSummary(install, plugin, version);
    }

    @Transactional
    public void uninstallPlugin(String deploymentId, String installId) {
        DeploymentEntity deployment = requireDeploymentEditorAccess(deploymentId);
        DeploymentMarketplacePluginInstallEntity install = deploymentMarketplacePluginInstallRepository.findById(installId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Marketplace install not found: " + installId));
        if (!deployment.getId().equals(install.getDeploymentId())) {
            throw new ResponseStatusException(BAD_REQUEST, "Marketplace install does not belong to deployment: " + deploymentId);
        }

        deploymentMarketplacePluginInstallRepository.delete(install);
        platformAuditService.record(
            "DEPLOYMENT_MARKETPLACE_PLUGIN_UNINSTALLED",
            "DEPLOYMENT_MARKETPLACE_PLUGIN_INSTALL",
            install.getId(),
            Map.of(
                "deploymentId", deployment.getId(),
                "pluginId", install.getPluginId(),
                "pluginVersionId", install.getPluginVersionId()
            )
        );
    }

    private List<DeploymentMarketplacePluginInstallSummary> toSummaries(List<DeploymentMarketplacePluginInstallEntity> installs) {
        Map<String, MarketplacePluginEntity> pluginsById = marketplacePluginRepository.findAllById(
            installs.stream().map(DeploymentMarketplacePluginInstallEntity::getPluginId).distinct().toList()
        ).stream().collect(Collectors.toMap(MarketplacePluginEntity::getId, Function.identity()));

        Map<String, MarketplacePluginVersionEntity> versionsById = marketplacePluginVersionRepository.findAllById(
            installs.stream().map(DeploymentMarketplacePluginInstallEntity::getPluginVersionId).distinct().toList()
        ).stream().collect(Collectors.toMap(MarketplacePluginVersionEntity::getId, Function.identity()));

        return installs.stream()
            .map(install -> {
                MarketplacePluginEntity plugin = pluginsById.get(install.getPluginId());
                MarketplacePluginVersionEntity version = versionsById.get(install.getPluginVersionId());
                if (plugin == null || version == null) {
                    throw new IllegalStateException("Marketplace install references missing plugin catalog state: " + install.getId());
                }
                return toSummary(install, plugin, version);
            })
            .toList();
    }

    private List<DeploymentMarketplacePluginImpactSummary> toImpactSummaries(List<DeploymentMarketplacePluginInstallEntity> installs) {
        Map<String, MarketplacePluginEntity> pluginsById = marketplacePluginRepository.findAllById(
            installs.stream().map(DeploymentMarketplacePluginInstallEntity::getPluginId).distinct().toList()
        ).stream().collect(Collectors.toMap(MarketplacePluginEntity::getId, Function.identity()));

        Map<String, MarketplacePluginVersionEntity> versionsById = marketplacePluginVersionRepository.findAllById(
            installs.stream().map(DeploymentMarketplacePluginInstallEntity::getPluginVersionId).distinct().toList()
        ).stream().collect(Collectors.toMap(MarketplacePluginVersionEntity::getId, Function.identity()));

        return installs.stream()
            .map(install -> {
                MarketplacePluginEntity plugin = pluginsById.get(install.getPluginId());
                MarketplacePluginVersionEntity version = versionsById.get(install.getPluginVersionId());
                if (plugin == null || version == null) {
                    throw new IllegalStateException("Marketplace install references missing plugin catalog state: " + install.getId());
                }
                return toImpactSummary(
                    plugin,
                    version,
                    "INSTALLED",
                    readJson(install.getConfigJson()),
                    readJson(install.getSecretRefsJson())
                );
            })
            .toList();
    }

    private DeploymentMarketplacePluginInstallSummary toSummary(DeploymentMarketplacePluginInstallEntity install,
                                                                MarketplacePluginEntity plugin,
                                                                MarketplacePluginVersionEntity version) {
        return new DeploymentMarketplacePluginInstallSummary(
            install.getId(),
            install.getDeploymentId(),
            plugin.getId(),
            plugin.getSlug(),
            plugin.getDisplayName(),
            plugin.getPluginType(),
            version.getId(),
            version.getVersion(),
            install.getStatus(),
            readJson(install.getConfigJson()),
            readJson(install.getSecretRefsJson()),
            install.getCreatedAt(),
            install.getUpdatedAt()
        );
    }

    private DeploymentMarketplacePluginImpactSummary toImpactSummary(MarketplacePluginEntity plugin,
                                                                     MarketplacePluginVersionEntity version,
                                                                     String installMode,
                                                                     JsonNode config,
                                                                     JsonNode secretRefs) {
        JsonNode manifest = readJson(version.getManifestJson());
        JsonNode contributions = manifest.path("contributions");
        JsonNode templateContribution = contributions.path("template");
        JsonNode templateShell = templateContribution.path("shell");
        JsonNode directShell = contributions.path("shell");

        LinkedHashSet<String> affectedConfigKeys = new LinkedHashSet<>();
        List<String> actionIds = textList(contributions.path("actions"), "actionId");
        if (!actionIds.isEmpty()) {
            affectedConfigKeys.add("actionsConfig");
        }

        JsonNode knowledgeSources = arrayNodeOrEmpty(contributions.path("knowledgeSources"));
        if (knowledgeSources.size() > 0) {
            affectedConfigKeys.add("knowledgeSourceConfig");
        }

        LinkedHashSet<String> shellModuleRefs = new LinkedHashSet<>();
        shellModuleRefs.addAll(textList(directShell.path("moduleRefs")));
        shellModuleRefs.addAll(textList(templateShell.path("enabledModuleIds")));
        JsonNode shellDefaults = combinedShellDefaults(templateShell, directShell);
        if (shellDefaults.size() > 0 || !shellModuleRefs.isEmpty()) {
            affectedConfigKeys.add("shellConfig");
        }

        return new DeploymentMarketplacePluginImpactSummary(
            plugin.getId(),
            plugin.getSlug(),
            plugin.getDisplayName(),
            plugin.getPluginType(),
            version.getId(),
            version.getVersion(),
            installMode,
            List.copyOf(affectedConfigKeys),
            actionIds,
            knowledgeSources,
            List.copyOf(shellModuleRefs),
            shellDefaults,
            config,
            secretRefs.size()
        );
    }

    private DeploymentEntity requireDeploymentAccess(String deploymentId) {
        DeploymentEntity deployment = deploymentRepository.findById(deploymentId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment not found: " + deploymentId));
        deploymentAccessService.requireDeploymentAccess(deployment);
        assertMutableState(deployment, "read marketplace installs for");
        return deployment;
    }

    private DeploymentEntity requireDeploymentEditorAccess(String deploymentId) {
        DeploymentEntity deployment = deploymentRepository.findById(deploymentId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment not found: " + deploymentId));
        deploymentAccessService.requireDeploymentEditorAccess(deployment);
        assertMutableState(deployment, "modify marketplace installs for");
        return deployment;
    }

    private void assertMutableState(DeploymentEntity deployment, String action) {
        if (deployment.getArchivedAt() != null) {
            throw new ResponseStatusException(BAD_REQUEST, "Cannot " + action + " archived deployment " + deployment.getId() + '.');
        }
        if ("QUEUED".equalsIgnoreCase(deployment.getDeletionStatus()) || "IN_PROGRESS".equalsIgnoreCase(deployment.getDeletionStatus())) {
            throw new ResponseStatusException(CONFLICT, "Cannot " + action + " deployment " + deployment.getId() + " while deletion is pending.");
        }
    }

    private JsonNode defaultObject(JsonNode node) {
        return node != null && node.isObject() ? node : objectMapper.createObjectNode();
    }

    private JsonNode defaultArray(JsonNode node) {
        return node != null && node.isArray() ? node : objectMapper.createArrayNode();
    }

    private JsonNode arrayNodeOrEmpty(JsonNode node) {
        return node != null && node.isArray() ? node.deepCopy() : objectMapper.createArrayNode();
    }

    private List<String> textList(JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return List.of();
        }
        return java.util.stream.StreamSupport.stream(arrayNode.spliterator(), false)
            .filter(JsonNode::isTextual)
            .map(JsonNode::asText)
            .distinct()
            .toList();
    }

    private List<String> textList(JsonNode arrayNode, String fieldName) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return List.of();
        }
        return java.util.stream.StreamSupport.stream(arrayNode.spliterator(), false)
            .map(node -> node.path(fieldName).asText(null))
            .filter(value -> value != null && !value.isBlank())
            .distinct()
            .toList();
    }

    private JsonNode combinedShellDefaults(JsonNode templateShell, JsonNode directShell) {
        var result = objectMapper.createObjectNode();
        if (templateShell != null && templateShell.isObject()) {
            result.setAll((com.fasterxml.jackson.databind.node.ObjectNode) templateShell.deepCopy());
        }
        if (directShell != null && directShell.isObject()) {
            directShell.fields().forEachRemaining(entry -> {
                if (!"moduleRefs".equals(entry.getKey())) {
                    result.set(entry.getKey(), entry.getValue());
                }
            });
        }
        return result;
    }

    private String writeJson(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to write marketplace install JSON.", ex);
        }
    }

    private JsonNode readJson(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read marketplace install JSON.", ex);
        }
    }
}
