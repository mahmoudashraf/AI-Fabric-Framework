package com.ai.fabric.product.shopify.bridge.action.service;

import com.ai.fabric.product.shopify.bridge.action.model.ShopifyBridgeActionExecuteRequest;
import com.ai.fabric.product.shopify.bridge.action.model.ShopifyBridgeActionResult;
import com.ai.fabric.product.shopify.bridge.client.shopify.ShopifyAdminGraphqlClient;
import com.ai.fabric.product.shopify.bridge.governedaction.service.ShopifyStorefrontGovernedActionService;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyBridgeInstallCredentialService;
import com.ai.fabric.product.shopify.bridge.mcp.execution.McpActionExecutionGateway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ShopifyBridgeActionExecutionService {

    private static final String CUSTOMER_CONTEXT_SUMMARY_ACTION_ID = "shopify_get_customer_context_summary";

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
        if (CUSTOMER_CONTEXT_SUMMARY_ACTION_ID.equals(actionId)) {
            return executeCustomerContextSummary(normalizedShopDomain, request);
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

    private ShopifyBridgeActionResult executeCustomerContextSummary(String shopDomain, ShopifyBridgeActionExecuteRequest request) {
        if (mcpActionExecutionGateway == null) {
            return ShopifyBridgeActionResult.failure(
                "MCP_GATEWAY_NOT_CONFIGURED",
                "MCP execution gateway is not configured."
            );
        }

        Map<String, Object> params = request == null || request.params() == null ? Map.of() : request.params();
        Map<String, Object> trace = request == null || request.trace() == null ? Map.of() : request.trace();
        String cartId = firstText(params, "cart_id", "cartId", "currentCartId");
        String orderNumber = firstText(params, "order_number", "orderNumber");

        Map<String, Object> cart = summarizeOptionalCart(shopDomain, cartId, trace);
        Map<String, Object> latestOrder = summarizeCustomerAccountRead(
            shopDomain,
            "shopify_get_most_recent_order_status",
            "get_most_recent_order_status",
            Map.of(),
            trace
        );
        Map<String, Object> specificOrder = StringUtils.hasText(orderNumber)
            ? summarizeCustomerAccountRead(
                shopDomain,
                "shopify_get_order_status",
                "get_order_status",
                Map.of("order_number", orderNumber),
                trace
            )
            : Map.of("available", false, "reason", "NO_ORDER_NUMBER_REQUESTED");
        Map<String, Object> storeCredit = summarizeCustomerAccountRead(
            shopDomain,
            "shopify_get_store_credit_balances",
            "get_store_credit_balances",
            Map.of(),
            trace
        );

        Map<String, Object> returns = new LinkedHashMap<>();
        returns.put("requestReturnSupported", true);
        returns.put("requestReturnActionId", "shopify_request_return");
        returns.put("confirmationRequired", true);
        returns.put("requiresOrderNumber", true);
        returns.put("returnableOrdersReadSupported", false);
        returns.put(
            "summary",
            "Return requests require an authenticated customer session, a concrete order number, and shopper confirmation."
        );

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "SHOPIFY_CUSTOMER_CONTEXT_SUMMARY");
        data.put("shopDomain", shopDomain);
        data.put("cart", cart);
        data.put("latestOrder", latestOrder);
        data.put("specificOrder", specificOrder);
        data.put("storeCredit", storeCredit);
        data.put("returns", returns);
        data.put("nonPersistent", true);
        data.put("includedReads", List.of(
            "get_current_cart",
            "get_most_recent_order_status",
            "get_order_status",
            "get_store_credit_balances"
        ));
        data.put("excludedMutations", List.of("request_return"));

        return ShopifyBridgeActionResult.ok("Shopify customer context summary", data);
    }

    private Map<String, Object> summarizeOptionalCart(String shopDomain, String cartId, Map<String, Object> trace) {
        if (!StringUtils.hasText(cartId)) {
            return Map.of(
                "available", false,
                "reason", "NO_CART_ID_IN_CONTEXT",
                "summary", "No current cart handle was supplied by trusted storefront context."
            );
        }
        return summarizeMcpRead(
            shopDomain,
            "shopify_get_cart",
            "shopify-storefront",
            "STOREFRONT_STANDARD",
            null,
            "get_cart",
            Map.of("cart_id", cartId.trim()),
            trace
        );
    }

    private Map<String, Object> summarizeCustomerAccountRead(String shopDomain,
                                                             String actionId,
                                                             String toolName,
                                                             Map<String, Object> params,
                                                             Map<String, Object> trace) {
        Map<String, Object> summary = summarizeMcpRead(
            shopDomain,
            actionId,
            "shopify-customer-account",
            "CUSTOMER_ACCOUNT",
            "CUSTOMER_OAUTH_PKCE",
            toolName,
            params,
            trace
        );
        if (!Boolean.TRUE.equals(summary.get("available"))
            && "CUSTOMER_ACCOUNT_AUTH_REQUIRED".equals(summary.get("errorCode"))) {
            summary.put("authRequired", true);
            summary.put("summary", "Customer Account authorization is required before this owned customer resource can be read.");
        }
        return summary;
    }

    private Map<String, Object> summarizeMcpRead(String shopDomain,
                                                 String actionId,
                                                 String serverRef,
                                                 String endpointKind,
                                                 String authMode,
                                                 String toolName,
                                                 Map<String, Object> params,
                                                 Map<String, Object> trace) {
        ShopifyBridgeActionResult result = mcpActionExecutionGateway.execute(
            shopDomain,
            new ShopifyBridgeActionExecuteRequest(
                actionId,
                params == null ? Map.of() : params,
                null,
                traceWithMcpConfig(trace, serverRef, endpointKind, authMode, toolName)
            )
        );
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sourceActionId", actionId);
        out.put("mcpToolName", toolName);
        out.put("available", result != null && result.success());
        if (result != null && StringUtils.hasText(result.errorCode())) {
            out.put("errorCode", result.errorCode());
        }
        String message = result == null ? null : result.message();
        if (StringUtils.hasText(message)) {
            out.put("summary", message);
        }
        Map<String, Object> data = result == null ? null : result.data();
        if (data != null && !data.isEmpty()) {
            out.put("evidence", data);
            if ("shopify_get_cart".equals(actionId)) {
                copyFirstPresent(data, out, "cart_id", "cartId", "id");
            }
            if (actionId != null && actionId.contains("_order_")) {
                copyFirstPresent(data, out, "order_number", "orderNumber", "order_number", "name", "number");
            }
        }
        return out;
    }

    private Map<String, Object> traceWithMcpConfig(Map<String, Object> trace,
                                                   String serverRef,
                                                   String endpointKind,
                                                   String authMode,
                                                   String toolName) {
        Map<String, Object> out = new LinkedHashMap<>(trace == null ? Map.of() : trace);
        Map<String, Object> mcp = new LinkedHashMap<>();
        mcp.put("serverRef", serverRef);
        mcp.put("endpointKind", endpointKind);
        if (StringUtils.hasText(authMode)) {
            mcp.put("authMode", authMode);
            mcp.put("requiredCustomerScopes", List.of("customer-account-mcp-api:full"));
        }
        mcp.put("toolName", toolName);
        out.put("actionConfig", Map.of(
            "adapterType", "mcp-tool",
            "execution", Map.of(
                "adapterType", "mcp-tool",
                "mcp", mcp
            )
        ));
        return out;
    }

    private void copyFirstPresent(Map<String, Object> source, Map<String, Object> target, String targetKey, String... keys) {
        if (source == null || target == null || keys == null) {
            return;
        }
        for (String key : keys) {
            Object value = findNestedValue(source, key);
            if (value != null && StringUtils.hasText(value.toString())) {
                target.put(targetKey, value.toString().trim());
                return;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Object findNestedValue(Object source, String key) {
        if (!(source instanceof Map<?, ?> map) || !StringUtils.hasText(key)) {
            return null;
        }
        if (map.containsKey(key)) {
            return map.get(key);
        }
        for (Object value : map.values()) {
            if (value instanceof Map<?, ?> nested) {
                Object found = findNestedValue((Map<String, Object>) nested, key);
                if (found != null) {
                    return found;
                }
            } else if (value instanceof Iterable<?> iterable) {
                for (Object item : iterable) {
                    Object found = findNestedValue(item, key);
                    if (found != null) {
                        return found;
                    }
                }
            }
        }
        return null;
    }

    private String firstText(Map<String, Object> values, String... keys) {
        if (values == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            Object value = values.get(key);
            if (value != null && StringUtils.hasText(value.toString())) {
                return value.toString().trim();
            }
        }
        return null;
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase();
    }
}
