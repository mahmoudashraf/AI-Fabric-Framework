package com.ai.fabric.platform.backend.marketplace.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentDraftEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentDraftRepository;
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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
    private final DeploymentDraftRepository deploymentDraftRepository;
    private final DeploymentAccessService deploymentAccessService;
    private final DeploymentMarketplacePluginInstallRepository deploymentMarketplacePluginInstallRepository;
    private final MarketplacePluginRepository marketplacePluginRepository;
    private final MarketplacePluginVersionRepository marketplacePluginVersionRepository;
    private final PlatformAuditService platformAuditService;
    private final ObjectMapper objectMapper;

    public DeploymentMarketplaceInstallService(DeploymentRepository deploymentRepository,
                                               DeploymentDraftRepository deploymentDraftRepository,
                                               DeploymentAccessService deploymentAccessService,
                                               DeploymentMarketplacePluginInstallRepository deploymentMarketplacePluginInstallRepository,
                                               MarketplacePluginRepository marketplacePluginRepository,
                                               MarketplacePluginVersionRepository marketplacePluginVersionRepository,
                                               PlatformAuditService platformAuditService,
                                               ObjectMapper objectMapper) {
        this.deploymentRepository = deploymentRepository;
        this.deploymentDraftRepository = deploymentDraftRepository;
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
        syncMarketplaceContributionsToDraft(deployment.getId());

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
        syncMarketplaceContributionsToDraft(deployment.getId());
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

    private void syncMarketplaceContributionsToDraft(String deploymentId) {
        DeploymentDraftEntity draft = deploymentDraftRepository.findTopByDeploymentIdOrderByRevisionNumberDesc(deploymentId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "No draft found for deployment: " + deploymentId));
        List<DeploymentMarketplacePluginInstallEntity> installs =
            deploymentMarketplacePluginInstallRepository.findByDeploymentIdOrderByCreatedAtAsc(deploymentId);
        Map<String, MarketplacePluginVersionEntity> versionsById = marketplacePluginVersionRepository.findAllById(
            installs.stream().map(DeploymentMarketplacePluginInstallEntity::getPluginVersionId).distinct().toList()
        ).stream().collect(Collectors.toMap(MarketplacePluginVersionEntity::getId, Function.identity()));

        ObjectNode actionsConfig = objectNode(readJson(draft.getActionsConfigJson()));
        ObjectNode knowledgeSourceConfig = objectNode(readJson(draft.getKnowledgeSourceConfigJson()));
        ObjectNode shellConfig = objectNode(readJson(draft.getShellConfigJson()));

        ArrayNode marketplaceActions = objectMapper.createArrayNode();
        ArrayNode marketplaceKnowledgeSources = objectMapper.createArrayNode();
        LinkedHashSet<String> marketplaceShellModuleRefs = new LinkedHashSet<>();
        ObjectNode marketplaceShellDefaults = objectMapper.createObjectNode();

        for (DeploymentMarketplacePluginInstallEntity install : installs) {
            MarketplacePluginVersionEntity version = versionsById.get(install.getPluginVersionId());
            if (version == null) {
                continue;
            }
            JsonNode contributions = readJson(version.getManifestJson()).path("contributions");
            JsonNode installConfig = readJson(install.getConfigJson());
            JsonNode installSecretRefs = readJson(install.getSecretRefsJson());
            arrayNodeOrEmpty(contributions.path("actions")).forEach(node ->
                marketplaceActions.add(withInstallContext(node, install, installConfig, installSecretRefs))
            );
            arrayNodeOrEmpty(contributions.path("knowledgeSources")).forEach(node ->
                marketplaceKnowledgeSources.add(withInstallContext(node, install, installConfig, installSecretRefs))
            );
            textList(contributions.path("shell").path("moduleRefs")).forEach(marketplaceShellModuleRefs::add);

            JsonNode templateShell = contributions.path("template").path("shell");
            if (templateShell.isObject()) {
                textList(templateShell.path("enabledModuleIds")).forEach(marketplaceShellModuleRefs::add);
            }
            JsonNode mergedShellDefaults = combinedShellDefaults(templateShell, contributions.path("shell"));
            if (mergedShellDefaults.isObject()) {
                marketplaceShellDefaults.setAll((ObjectNode) mergedShellDefaults);
            }
        }

        ArrayNode effectiveActions = objectMapper.createArrayNode();
        JsonNode existingActions = actionsConfig.path("actions");
        if (existingActions.isArray()) {
            existingActions.forEach(node -> {
                if (!node.path("marketplaceInstall").isObject()) {
                    effectiveActions.add(node.deepCopy());
                }
            });
        }
        marketplaceActions.forEach(node -> effectiveActions.add(node.deepCopy()));
        actionsConfig.set("actions", effectiveActions);

        if (marketplaceActions.size() > 0) {
            actionsConfig.set("marketplaceActions", marketplaceActions);
        } else {
            actionsConfig.remove("marketplaceActions");
        }
        if (marketplaceKnowledgeSources.size() > 0) {
            knowledgeSourceConfig.set("marketplaceSources", marketplaceKnowledgeSources);
        } else {
            knowledgeSourceConfig.remove("marketplaceSources");
        }

        if (!marketplaceShellModuleRefs.isEmpty() || marketplaceShellDefaults.size() > 0) {
            ObjectNode marketplaceShell = objectMapper.createObjectNode();
            ArrayNode moduleRefs = objectMapper.createArrayNode();
            marketplaceShellModuleRefs.forEach(moduleRefs::add);
            marketplaceShell.set("moduleRefs", moduleRefs);
            marketplaceShell.set("defaults", marketplaceShellDefaults);
            shellConfig.set("marketplace", marketplaceShell);
        } else {
            shellConfig.remove("marketplace");
        }

        draft.setActionsConfigJson(writeJson(actionsConfig));
        draft.setKnowledgeSourceConfigJson(writeJson(knowledgeSourceConfig));
        draft.setShellConfigJson(writeJson(shellConfig));
        draft.setUpdatedAt(Instant.now());
        deploymentDraftRepository.save(draft);
    }

    private ObjectNode objectNode(JsonNode node) {
        if (node != null && node.isObject()) {
            return (ObjectNode) node.deepCopy();
        }
        return objectMapper.createObjectNode();
    }

    private JsonNode withInstallContext(JsonNode contribution,
                                        DeploymentMarketplacePluginInstallEntity install,
                                        JsonNode installConfig,
                                        JsonNode installSecretRefs) {
        ObjectNode contextualized = contribution != null && contribution.isObject()
            ? (ObjectNode) contribution.deepCopy()
            : objectMapper.createObjectNode();
        ObjectNode installContext = contextualized.putObject("marketplaceInstall");
        installContext.put("installId", install.getId());
        installContext.put("deploymentId", install.getDeploymentId());
        installContext.put("pluginId", install.getPluginId());
        installContext.put("pluginVersionId", install.getPluginVersionId());
        installContext.set("config", installConfig.deepCopy());
        installContext.set("secretRefs", installSecretRefs.deepCopy());
        return contextualized;
    }
}
