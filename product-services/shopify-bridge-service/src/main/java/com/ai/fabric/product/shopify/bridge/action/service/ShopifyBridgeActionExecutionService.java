package com.ai.fabric.product.shopify.bridge.action.service;

import com.ai.fabric.product.shopify.bridge.action.model.ShopifyBridgeActionExecuteRequest;
import com.ai.fabric.product.shopify.bridge.action.model.ShopifyBridgeActionResult;
import com.ai.fabric.product.shopify.bridge.client.shopify.ShopifyAdminGraphqlClient;
import com.ai.fabric.product.shopify.bridge.governedaction.service.ShopifyStorefrontGovernedActionService;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyBridgeInstallCredentialService;
import com.ai.fabric.product.shopify.bridge.mcp.execution.McpActionExecutionGateway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ShopifyBridgeActionExecutionService {

    private final ShopifyBridgeInstallCredentialService installCredentialService;
    private final McpActionExecutionGateway mcpActionExecutionGateway;

    @Autowired
    public ShopifyBridgeActionExecutionService(ShopifyBridgeInstallCredentialService installCredentialService,
                                               ShopifyAdminGraphqlClient ignoredShopifyAdminGraphqlClient,
                                               ShopifyStorefrontGovernedActionService ignoredGovernedActionService,
                                               McpActionExecutionGateway mcpActionExecutionGateway) {
        this.installCredentialService = installCredentialService;
        this.mcpActionExecutionGateway = mcpActionExecutionGateway;
    }

    ShopifyBridgeActionExecutionService(ShopifyBridgeInstallCredentialService installCredentialService,
                                        ShopifyAdminGraphqlClient ignoredShopifyAdminGraphqlClient,
                                        ShopifyStorefrontGovernedActionService ignoredGovernedActionService) {
        this(installCredentialService, ignoredShopifyAdminGraphqlClient, ignoredGovernedActionService, null);
    }

    public ShopifyBridgeActionResult execute(String shopDomain, ShopifyBridgeActionExecuteRequest request) {
        String normalizedShopDomain = normalize(shopDomain);
        String actionId = normalize(request == null ? null : request.actionId());
        if (normalizedShopDomain == null) {
            return ShopifyBridgeActionResult.failure("INVALID_REQUEST", "shopDomain is required.");
        }
        if (actionId == null) {
            return ShopifyBridgeActionResult.failure("INVALID_REQUEST", "actionId is required.");
        }
        if (installCredentialService.resolvePersistedMaterial(normalizedShopDomain).isEmpty()) {
            return ShopifyBridgeActionResult.failure(
                "NOT_CONNECTED",
                "Shopify store credentials are not available for this bridge."
            );
        }
        if (mcpActionExecutionGateway != null && mcpActionExecutionGateway.supports(request)) {
            return mcpActionExecutionGateway.execute(normalizedShopDomain, request);
        }
        return ShopifyBridgeActionResult.failure(
            "ACTION_NOT_SUPPORTED",
            "Action requires marketplace mcp-tool execution config in trace."
        );
    }

    public Map<String, Object> mcpReadiness(String shopDomain) {
        String normalizedShopDomain = normalize(shopDomain);
        if (normalizedShopDomain == null) {
            return Map.of(
                "ready", false,
                "errorCode", "INVALID_REQUEST",
                "message", "shopDomain is required."
            );
        }
        if (mcpActionExecutionGateway == null) {
            return Map.of(
                "ready", false,
                "errorCode", "SERVICE_UNAVAILABLE",
                "message", "MCP execution gateway is not configured."
            );
        }
        return mcpActionExecutionGateway.storefrontReadiness(normalizedShopDomain);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase();
    }
}
