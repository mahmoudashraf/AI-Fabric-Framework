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
    String defaultQdrantRuntimeApiKeySecretName,
    String defaultQdrantCloudProviderId,
    String defaultQdrantCloudRegionId,
    Boolean defaultQdrantManagedCollectionsEnabled,
    String templatePluginId,
    String templatePluginVersion,
    String stagingTemplatePluginId,
    String productionTemplatePluginId,
    String goLiveTargetProfileId,
    List<String> defaultPluginIds
) {

    public ShopifyCompanionBootstrapProperties {
        String defaultStagingTemplatePluginId = "mkp-template-shopify-companion-staging";
        stagingTemplatePluginId = normalize(stagingTemplatePluginId, defaultStagingTemplatePluginId);
        defaultEnvironment = normalize(defaultEnvironment, "dev");
        defaultTemplateId = normalize(defaultTemplateId, "dev-openai-qdrant");
        defaultVectorProvisioningMode = normalize(defaultVectorProvisioningMode, "PLATFORM_MANAGED");
        defaultVectorStoragePosture = normalize(defaultVectorStoragePosture, "SHARED");
        defaultQdrantHost = normalize(defaultQdrantHost, "");
        defaultQdrantSourceDeploymentId = normalize(defaultQdrantSourceDeploymentId, "");
        defaultQdrantRuntimeApiKeySecretName = normalize(defaultQdrantRuntimeApiKeySecretName, "");
        defaultQdrantCloudProviderId = normalize(defaultQdrantCloudProviderId, "aws");
        defaultQdrantCloudRegionId = normalize(defaultQdrantCloudRegionId, "eu-west-1");
        defaultQdrantManagedCollectionsEnabled = defaultQdrantManagedCollectionsEnabled == null
            ? Boolean.TRUE
            : defaultQdrantManagedCollectionsEnabled;
        templatePluginId = normalize(templatePluginId, stagingTemplatePluginId);
        templatePluginVersion = normalize(templatePluginVersion, "");
        productionTemplatePluginId = normalize(productionTemplatePluginId, "mkp-template-shopify-companion-production");
        goLiveTargetProfileId = normalize(goLiveTargetProfileId, "");
        defaultPluginIds = defaultPluginIds == null
            ? List.of(
                "mkp-action-shopify-storefront-read-mcp",
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
