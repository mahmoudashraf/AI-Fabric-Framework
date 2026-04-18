package com.ai.fabric.platform.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "shopify.companion.bootstrap")
public record ShopifyCompanionBootstrapProperties(
    String defaultEnvironment,
    String defaultTemplateId,
    String defaultVectorProvisioningMode,
    String templatePluginId,
    String templatePluginVersion,
    List<String> defaultPluginIds
) {

    public ShopifyCompanionBootstrapProperties {
        defaultEnvironment = normalize(defaultEnvironment, "dev");
        defaultTemplateId = normalize(defaultTemplateId, "custom-start-from-scratch");
        defaultVectorProvisioningMode = normalize(defaultVectorProvisioningMode, "");
        templatePluginId = normalize(templatePluginId, "mkp-template-shopify-companion");
        templatePluginVersion = normalize(templatePluginVersion, "");
        defaultPluginIds = defaultPluginIds == null
            ? List.of(
                "mkp-action-shopify-companion-read",
                "mkp-data-shopify-catalog",
                "mkp-data-shopify-policies",
                "mkp-inference-shopify-companion-default"
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
