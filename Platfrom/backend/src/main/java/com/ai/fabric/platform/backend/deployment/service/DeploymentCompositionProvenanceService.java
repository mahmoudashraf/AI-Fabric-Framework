package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.marketplace.entity.DeploymentMarketplacePluginInstallEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginVersionEntity;
import com.ai.fabric.platform.backend.marketplace.repository.DeploymentMarketplacePluginInstallRepository;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginRepository;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginVersionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

@Service
public class DeploymentCompositionProvenanceService {

    private final DeploymentMarketplacePluginInstallRepository installRepository;
    private final MarketplacePluginRepository pluginRepository;
    private final MarketplacePluginVersionRepository versionRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    public DeploymentCompositionProvenanceService(
        DeploymentMarketplacePluginInstallRepository installRepository,
        MarketplacePluginRepository pluginRepository,
        MarketplacePluginVersionRepository versionRepository,
        ObjectMapper objectMapper
    ) {
        this.installRepository = installRepository;
        this.pluginRepository = pluginRepository;
        this.versionRepository = versionRepository;
        this.objectMapper = objectMapper;
    }

    ArrayNode marketplaceInstalls(String deploymentId) {
        List<DeploymentMarketplacePluginInstallEntity> installs = new ArrayList<>(
            installRepository.findByDeploymentIdOrderByUpdatedAtDesc(deploymentId)
        );
        installs.removeIf(install -> !isComposedStatus(install.getStatus()));
        installs.sort(Comparator
            .comparing(DeploymentMarketplacePluginInstallEntity::getPluginId)
            .thenComparing(DeploymentMarketplacePluginInstallEntity::getPluginVersionId)
            .thenComparing(DeploymentMarketplacePluginInstallEntity::getId));

        ArrayNode result = objectMapper.createArrayNode();
        for (DeploymentMarketplacePluginInstallEntity install : installs) {
            MarketplacePluginEntity plugin = pluginRepository.findById(install.getPluginId())
                .orElseThrow(() -> new IllegalStateException(
                    "Marketplace composition references missing plugin: " + install.getPluginId()
                ));
            MarketplacePluginVersionEntity version = versionRepository.findById(install.getPluginVersionId())
                .orElseThrow(() -> new IllegalStateException(
                    "Marketplace composition references missing plugin version: " + install.getPluginVersionId()
                ));
            if (!plugin.getId().equals(version.getPluginId())) {
                throw new IllegalStateException(
                    "Marketplace install " + install.getId() + " references a version owned by another plugin."
                );
            }
            JsonNode manifest = readManifest(version);
            ObjectNode entry = result.addObject();
            entry.put("installId", install.getId());
            entry.put("installStatus", install.getStatus().trim().toUpperCase(Locale.ROOT));
            entry.put("pluginId", plugin.getId());
            entry.put("pluginType", plugin.getPluginType().trim().toUpperCase(Locale.ROOT));
            entry.put("pluginVersionId", version.getId());
            entry.put("pluginVersion", version.getVersion());
            entry.put("manifestSha256", "sha256:" + sha256(canonicalJson(manifest)));
            if (StringUtils.hasText(version.getBundleSha256())) {
                entry.put("declaredBundleSha256", version.getBundleSha256().trim());
            }
            JsonNode specialistBundles = manifest.path("contributions").path("specialist").path("sourceBundleRefs");
            if (specialistBundles.isArray()) {
                entry.set("specialistBundleRefs", specialistBundles.deepCopy());
            }
        }
        return result;
    }

    private boolean isComposedStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return false;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        return "ENABLED".equals(normalized) || "BOOTSTRAPPED".equals(normalized);
    }

    private JsonNode readManifest(MarketplacePluginVersionEntity version) {
        try {
            JsonNode manifest = objectMapper.readTree(version.getManifestJson());
            if (manifest == null || !manifest.isObject()) {
                throw new IllegalStateException("Marketplace plugin manifest must be an object: " + version.getId());
            }
            return manifest;
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to parse marketplace plugin manifest: " + version.getId(), exception);
        }
    }

    private String canonicalJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(canonicalize(node));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to canonicalize marketplace plugin manifest.", exception);
        }
    }

    private Object canonicalize(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isObject()) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            node.fields().forEachRemaining(entry -> sorted.put(entry.getKey(), canonicalize(entry.getValue())));
            return sorted;
        }
        if (node.isArray()) {
            List<Object> values = new ArrayList<>();
            node.forEach(value -> values.add(canonicalize(value)));
            return values;
        }
        if (node.isNumber()) {
            return node.numberValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        return node.asText();
    }

    private String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte valueByte : hash) {
                result.append(String.format("%02x", valueByte));
            }
            return result.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to hash marketplace composition provenance.", exception);
        }
    }
}
