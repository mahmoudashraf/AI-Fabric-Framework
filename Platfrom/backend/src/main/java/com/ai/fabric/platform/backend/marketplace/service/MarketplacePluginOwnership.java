package com.ai.fabric.platform.backend.marketplace.service;

import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginVersionEntity;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/** Customer ownership is an execution boundary even for trusted background/compiler callers. */
public final class MarketplacePluginOwnership {
    private MarketplacePluginOwnership() { }

    public static boolean isPrivate(MarketplacePluginEntity plugin) {
        return plugin.getOwnerCustomerId() != null || !"PUBLIC".equals(plugin.getVisibility());
    }

    public static void requireCustomer(MarketplacePluginEntity plugin, String customerId) {
        if (isPrivate(plugin) && (plugin.getOwnerCustomerId() == null
            || !plugin.getOwnerCustomerId().equals(customerId))) {
            throw new ResponseStatusException(NOT_FOUND, "Marketplace plugin not found.");
        }
    }

    public static void requireVersion(MarketplacePluginEntity plugin, MarketplacePluginVersionEntity version,
                                      String customerId) {
        requireCustomer(plugin, customerId);
        if (!plugin.getId().equals(version.getPluginId())) {
            throw new ResponseStatusException(NOT_FOUND, "Marketplace plugin version not found.");
        }
    }

    public static void requireReferences(com.fasterxml.jackson.databind.JsonNode node, String customerId,
        com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginRepository plugins,
        com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginVersionRepository versions) {
        if (node == null) return;
        if (node.isObject()) {
            for (String field : java.util.List.of("pluginId", "marketplacePluginId")) {
                if (node.path(field).isTextual()) plugins.findById(node.path(field).asText())
                    .ifPresent(plugin -> {
                        requireCustomer(plugin, customerId);
                        if (node.has("ingestionMode")) MarketplacePrivateDataContract.requireCompiledDataset(plugin, node);
                    });
            }
            if (node.path("pluginVersionId").isTextual()) versions.findById(node.path("pluginVersionId").asText())
                .flatMap(version -> plugins.findById(version.getPluginId()))
                .ifPresent(plugin -> requireCustomer(plugin, customerId));
        }
        if (node.isContainerNode()) node.forEach(child -> requireReferences(child, customerId, plugins, versions));
    }
}
