package com.ai.fabric.product.shopify.bridge.action.service;

import com.ai.fabric.product.shopify.bridge.action.model.ShopifyBridgeActionExecuteRequest;
import com.ai.fabric.product.shopify.bridge.action.model.ShopifyBridgeActionResult;
import com.ai.fabric.product.shopify.bridge.client.shopify.ShopifyAdminGraphqlClient;
import com.ai.fabric.product.shopify.bridge.governedaction.service.ShopifyStorefrontGovernedActionService;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyBridgeInstallCredentialService;
import com.ai.fabric.product.shopify.bridge.mcp.execution.McpActionExecutionGateway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ShopifyBridgeActionExecutionService {

    private final ShopifyBridgeInstallCredentialService installCredentialService;
    private final ShopifyStorefrontMcpActionAdapter storefrontMcpActionAdapter;
    private final ShopifyCustomerAccountMcpActionAdapter customerAccountMcpActionAdapter;
    private final ShopifyCheckoutMcpActionAdapter checkoutMcpActionAdapter;
    private final McpActionExecutionGateway mcpActionExecutionGateway;

    public ShopifyBridgeActionExecutionService(ShopifyBridgeInstallCredentialService installCredentialService,
                                               ShopifyAdminGraphqlClient ignoredShopifyAdminGraphqlClient,
                                               ShopifyStorefrontGovernedActionService ignoredGovernedActionService,
                                               ShopifyStorefrontMcpActionAdapter storefrontMcpActionAdapter) {
        this(
            installCredentialService,
            ignoredShopifyAdminGraphqlClient,
            ignoredGovernedActionService,
            storefrontMcpActionAdapter,
            null,
            null,
            null
        );
    }

    @Autowired
    public ShopifyBridgeActionExecutionService(ShopifyBridgeInstallCredentialService installCredentialService,
                                               ShopifyAdminGraphqlClient ignoredShopifyAdminGraphqlClient,
                                               ShopifyStorefrontGovernedActionService ignoredGovernedActionService,
                                               ShopifyStorefrontMcpActionAdapter storefrontMcpActionAdapter,
                                               ShopifyCustomerAccountMcpActionAdapter customerAccountMcpActionAdapter,
                                               ShopifyCheckoutMcpActionAdapter checkoutMcpActionAdapter,
                                               McpActionExecutionGateway mcpActionExecutionGateway) {
        this.installCredentialService = installCredentialService;
        this.storefrontMcpActionAdapter = storefrontMcpActionAdapter;
        this.customerAccountMcpActionAdapter = customerAccountMcpActionAdapter;
        this.checkoutMcpActionAdapter = checkoutMcpActionAdapter;
        this.mcpActionExecutionGateway = mcpActionExecutionGateway;
    }

    ShopifyBridgeActionExecutionService(ShopifyBridgeInstallCredentialService installCredentialService,
                                        ShopifyAdminGraphqlClient ignoredShopifyAdminGraphqlClient,
                                        ShopifyStorefrontGovernedActionService ignoredGovernedActionService,
                                        ShopifyStorefrontMcpActionAdapter storefrontMcpActionAdapter,
                                        ShopifyCustomerAccountMcpActionAdapter customerAccountMcpActionAdapter,
                                        ShopifyCheckoutMcpActionAdapter checkoutMcpActionAdapter) {
        this(
            installCredentialService,
            ignoredShopifyAdminGraphqlClient,
            ignoredGovernedActionService,
            storefrontMcpActionAdapter,
            customerAccountMcpActionAdapter,
            checkoutMcpActionAdapter,
            null
        );
    }

    ShopifyBridgeActionExecutionService(ShopifyBridgeInstallCredentialService installCredentialService,
                                        ShopifyAdminGraphqlClient ignoredShopifyAdminGraphqlClient,
                                        ShopifyStorefrontGovernedActionService ignoredGovernedActionService) {
        this(installCredentialService, ignoredShopifyAdminGraphqlClient, ignoredGovernedActionService, null, null, null, null);
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
        if (storefrontMcpActionAdapter == null) {
            return Map.of(
                "ready", false,
                "errorCode", "SERVICE_UNAVAILABLE",
                "message", "Shopify MCP readiness adapter is not configured."
            );
        }
        Map<String, Object> storefrontReadiness = storefrontMcpActionAdapter.readiness(normalizedShopDomain);
        LinkedHashMap<String, Object> merged = new LinkedHashMap<>(storefrontReadiness);
        List<Map<String, Object>> gatedServers = new ArrayList<>();
        if (customerAccountMcpActionAdapter != null) {
            gatedServers.add(customerAccountMcpActionAdapter.readiness(normalizedShopDomain));
        }
        if (checkoutMcpActionAdapter != null) {
            gatedServers.add(checkoutMcpActionAdapter.readiness(normalizedShopDomain));
        }
        if (!gatedServers.isEmpty()) {
            merged.put("gatedServers", gatedServers);
        }
        return merged;
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase();
    }
}
