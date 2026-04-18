package com.ai.fabric.platform.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "shopify.companion.bootstrap")
public record ShopifyCompanionBootstrapProperties(
    String defaultEnvironment,
    String defaultTemplateId,
    String defaultVectorProvisioningMode,
    String defaultVectorStoragePosture,
    String defaultQdrantHost,
    String defaultQdrantSourceDeploymentId,
    String defaultQdrantCloudProviderId,
    String defaultQdrantCloudRegionId,
    Boolean defaultQdrantManagedCollectionsEnabled,
    String templatePluginId,
    String templatePluginVersion,
    List<String> defaultPluginIds
) {

    public ShopifyCompanionBootstrapProperties {
        defaultEnvironment = normalize(defaultEnvironment, "dev");
        defaultTemplateId = normalize(defaultTemplateId, "dev-openai-qdrant");
        defaultVectorProvisioningMode = normalize(defaultVectorProvisioningMode, "PLATFORM_MANAGED");
        defaultVectorStoragePosture = normalize(defaultVectorStoragePosture, "SHARED");
        defaultQdrantHost = normalize(defaultQdrantHost, "");
        defaultQdrantSourceDeploymentId = normalize(defaultQdrantSourceDeploymentId, "");
        defaultQdrantCloudProviderId = normalize(defaultQdrantCloudProviderId, "aws");
        defaultQdrantCloudRegionId = normalize(defaultQdrantCloudRegionId, "eu-west-1");
        defaultQdrantManagedCollectionsEnabled = defaultQdrantManagedCollectionsEnabled == null
            ? Boolean.TRUE
            : defaultQdrantManagedCollectionsEnabled;
        templatePluginId = normalize(templatePluginId, "mkp-template-shopify-companion");
        templatePluginVersion = normalize(templatePluginVersion, "");
        defaultPluginIds = defaultPluginIds == null
            ? List.of(
                "mkp-action-shopify-companion-read",
                "mkp-data-shopify-catalog",
                "mkp-data-shopify-policies",
                "mkp-inference-shared-embeddings"
            )
            : defaultPluginIds.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
