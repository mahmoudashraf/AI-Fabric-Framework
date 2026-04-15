package com.ai.fabric.platform.backend.marketplace.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.CONFLICT;

@Service
public class MarketplaceDatasetHandleResolver {

    public String resolveHandleRef(DeploymentEntity deployment,
                                   MarketplacePluginEntity plugin,
                                   MarketplaceManifestService.ParsedMarketplaceDatasetDefinition dataset,
                                   String datasetHash) {
        if (deployment == null || plugin == null || dataset == null) {
            throw new IllegalStateException("Deployment, plugin, and dataset are required to resolve a marketplace dataset handle.");
        }
        String tenantId = trimToNull(deployment.getTenantId());
        if (!StringUtils.hasText(tenantId)) {
            throw new ResponseStatusException(
                CONFLICT,
                "Marketplace DATA plugin datasets require a tenant-scoped deployment binding before they can be compiled."
            );
        }
        String customerId = trimToNull(deployment.getCustomerId());
        if (!StringUtils.hasText(customerId)) {
            throw new ResponseStatusException(
                CONFLICT,
                "Marketplace DATA plugin datasets require a customer-scoped deployment binding before they can be compiled."
            );
        }
        String defaultHandle = "plugin/" + plugin.getId()
            + "/tenant/" + tenantId
            + "/" + dataset.datasetId()
            + "/" + abbreviateHash(datasetHash)
            + "/" + dataset.entityType();
        String template = trimToNull(dataset.handleTemplate());
        if (!StringUtils.hasText(template)) {
            return defaultHandle;
        }
        return template
            .replace("{pluginId}", plugin.getId())
            .replace("{tenantId}", tenantId)
            .replace("{customerId}", customerId)
            .replace("{datasetId}", dataset.datasetId())
            .replace("{datasetHash}", abbreviateHash(datasetHash))
            .replace("{entityType}", dataset.entityType());
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String abbreviateHash(String value) {
        String normalized = trimToNull(value);
        if (!StringUtils.hasText(normalized)) {
            return "default";
        }
        return normalized.length() <= 12 ? normalized : normalized.substring(0, 12);
    }
}
