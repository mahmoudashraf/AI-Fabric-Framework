package com.ai.fabric.platform.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "platform.bootstrap")
public record PlatformBootstrapProperties(
    boolean sampleEnabled,
    EcommerceDemoProperties ecommerceDemo
) {

    public PlatformBootstrapProperties {
        ecommerceDemo = ecommerceDemo == null
            ? new EcommerceDemoProperties(
                false,
                false,
                "Ecommerce Demo Restored",
                "dev",
                "dev-openai-lucene",
                "Real_Apps/ecommerce-store/deploy/runtime/config/ai-actions.yml",
                "Real_Apps/ecommerce-store/deploy/runtime/config/ai-entity-config.yml",
                "Real_Apps/ecommerce-store/deploy/rest-connector/actions-routing.yml",
                512,
                "https://ai-fabric-framework-production-a247.up.railway.app",
                "https://ai-fabric-framework-production-a247.up.railway.app",
                "/api/authz/check",
                true,
                false,
                true,
                "X-AIFABRIC-API-KEY",
                "${CONNECTOR_API_KEY}"
            )
            : ecommerceDemo;
    }

    public record EcommerceDemoProperties(
        boolean enabled,
        boolean autoApply,
        String name,
        String environment,
        String templateId,
        String actionsFile,
        String entitiesFile,
        String routingFile,
        int vectorDimensions,
        String upstreamBaseUrl,
        String authzUpstreamBaseUrl,
        String authzPath,
        boolean authzEnabled,
        boolean connectorAllowUnauthenticated,
        boolean connectorApiKeyEnabled,
        String connectorApiKeyHeader,
        String connectorApiKeyValue
    ) {

        public EcommerceDemoProperties {
            name = defaultText(name, "Ecommerce Demo Restored");
            environment = defaultText(environment, "dev");
            templateId = defaultText(templateId, "dev-openai-lucene");
            actionsFile = defaultText(
                actionsFile,
                "Real_Apps/ecommerce-store/deploy/runtime/config/ai-actions.yml"
            );
            entitiesFile = defaultText(
                entitiesFile,
                "Real_Apps/ecommerce-store/deploy/runtime/config/ai-entity-config.yml"
            );
            routingFile = defaultText(
                routingFile,
                "Real_Apps/ecommerce-store/deploy/rest-connector/actions-routing.yml"
            );
            vectorDimensions = vectorDimensions <= 0 ? 512 : vectorDimensions;
            upstreamBaseUrl = defaultText(
                upstreamBaseUrl,
                "https://ai-fabric-framework-production-a247.up.railway.app"
            );
            authzUpstreamBaseUrl = defaultText(authzUpstreamBaseUrl, upstreamBaseUrl);
            authzPath = defaultText(authzPath, "/api/authz/check");
            connectorApiKeyHeader = defaultText(connectorApiKeyHeader, "X-AIFABRIC-API-KEY");
            connectorApiKeyValue = defaultText(connectorApiKeyValue, "${CONNECTOR_API_KEY}");
        }

        private static String defaultText(String value, String fallback) {
            if (value == null || value.isBlank()) {
                return fallback;
            }
            return value.trim();
        }
    }
}
