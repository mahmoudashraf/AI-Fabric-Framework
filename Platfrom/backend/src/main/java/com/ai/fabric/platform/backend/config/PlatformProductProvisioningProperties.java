package com.ai.fabric.platform.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.time.Duration;

@ConfigurationProperties(prefix = "platform.product-service.provisioning")
public record PlatformProductProvisioningProperties(
    String sharedProjectNamePrefix,
    String shopifyBridgeServiceRoot,
    String shopifyBridgeDockerfilePath,
    String shopifyBridgeServiceNamePrefix,
    String shopifyBridgeHealthPath,
    String shopifyBridgeAdminApiVersion,
    String shopifyBridgeShopifyApiKeySecretName,
    String shopifyBridgeShopifyApiSecretSecretName,
    String shopifyBridgeWebhookSharedSecretName,
    String mcpExecutionGatewayServiceRef,
    String mcpExecutionGatewayServiceRoot,
    String mcpExecutionGatewayDockerfilePath,
    String mcpExecutionGatewayServiceNamePrefix,
    String mcpExecutionGatewayHealthPath,
    Duration pollInterval,
    Duration requestTimeout
) {

    public PlatformProductProvisioningProperties(String sharedProjectNamePrefix,
                                                 String shopifyBridgeServiceRoot,
                                                 String shopifyBridgeDockerfilePath,
                                                 String shopifyBridgeServiceNamePrefix,
                                                 String shopifyBridgeHealthPath,
                                                 String shopifyBridgeAdminApiVersion,
                                                 String shopifyBridgeShopifyApiKeySecretName,
                                                 String shopifyBridgeShopifyApiSecretSecretName,
                                                 String shopifyBridgeWebhookSharedSecretName,
                                                 Duration pollInterval,
                                                 Duration requestTimeout) {
        this(
            sharedProjectNamePrefix,
            shopifyBridgeServiceRoot,
            shopifyBridgeDockerfilePath,
            shopifyBridgeServiceNamePrefix,
            shopifyBridgeHealthPath,
            shopifyBridgeAdminApiVersion,
            shopifyBridgeShopifyApiKeySecretName,
            shopifyBridgeShopifyApiSecretSecretName,
            shopifyBridgeWebhookSharedSecretName,
            null,
            null,
            null,
            null,
            null,
            pollInterval,
            requestTimeout
        );
    }

    @ConstructorBinding
    public PlatformProductProvisioningProperties {
        sharedProjectNamePrefix = normalizeText(sharedProjectNamePrefix, "loom-product");
        shopifyBridgeServiceRoot = normalizeText(shopifyBridgeServiceRoot, "product-services/shopify-bridge-service");
        shopifyBridgeDockerfilePath = normalizeText(
            shopifyBridgeDockerfilePath,
            "product-services/shopify-bridge-service/deploy/railway/Dockerfile"
        );
        shopifyBridgeServiceNamePrefix = normalizeText(shopifyBridgeServiceNamePrefix, "shopify-bridge");
        shopifyBridgeHealthPath = normalizeText(shopifyBridgeHealthPath, "/actuator/health");
        shopifyBridgeAdminApiVersion = normalizeText(shopifyBridgeAdminApiVersion, "2026-04");
        shopifyBridgeShopifyApiKeySecretName = normalizeText(shopifyBridgeShopifyApiKeySecretName, "SHOPIFY_APP_API_KEY");
        shopifyBridgeShopifyApiSecretSecretName = normalizeText(shopifyBridgeShopifyApiSecretSecretName, "SHOPIFY_APP_API_SECRET");
        shopifyBridgeWebhookSharedSecretName = normalizeText(shopifyBridgeWebhookSharedSecretName, "SHOPIFY_WEBHOOK_SHARED_SECRET");
        mcpExecutionGatewayServiceRef = normalizeText(mcpExecutionGatewayServiceRef, "mcp-execution-gateway");
        mcpExecutionGatewayServiceRoot = normalizeText(mcpExecutionGatewayServiceRoot, "product-services/mcp-execution-gateway-service");
        mcpExecutionGatewayDockerfilePath = normalizeText(
            mcpExecutionGatewayDockerfilePath,
            "product-services/mcp-execution-gateway-service/deploy/railway/Dockerfile"
        );
        mcpExecutionGatewayServiceNamePrefix = normalizeText(mcpExecutionGatewayServiceNamePrefix, "mcp-gateway");
        mcpExecutionGatewayHealthPath = normalizeText(mcpExecutionGatewayHealthPath, "/actuator/health");
        pollInterval = pollInterval == null || pollInterval.isZero() || pollInterval.isNegative()
            ? Duration.ofSeconds(15)
            : pollInterval;
        requestTimeout = requestTimeout == null || requestTimeout.isZero() || requestTimeout.isNegative()
            ? Duration.ofMinutes(5)
            : requestTimeout;
    }

    private static String normalizeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
