package com.ai.fabric.product.shopify.bridge.action.service;

import com.ai.fabric.product.shopify.bridge.action.model.ShopifyBridgeActionExecuteRequest;
import com.ai.fabric.product.shopify.bridge.action.model.ShopifyBridgeActionResult;
import com.ai.fabric.product.shopify.bridge.client.mcp.ShopifyMcpClient;
import com.ai.fabric.product.shopify.bridge.client.mcp.ShopifyMcpClient.ShopifyMcpRequestOptions;
import com.ai.fabric.product.shopify.bridge.config.ShopifyCustomerAccountMcpProperties;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyBridgeCredentialAcquisition;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ShopifyCustomerAccountMcpActionAdapter {

    static final String SERVER_REF_CUSTOMER_ACCOUNT = "shopify-customer-account";
    static final String ENDPOINT_KIND_CUSTOMER_ACCOUNT = "CUSTOMER_ACCOUNT";
    static final String TOOL_GET_CUSTOMER_ORDERS = "get_customer_orders";
    static final String TOOL_LOOKUP_ORDER = "lookup_order";
    static final String TOOL_GET_ORDER_STATUS = "get_order_status";
    static final String TOOL_GET_RETURN_ELIGIBILITY = "get_return_eligibility";
    static final String TOOL_START_RETURN_REQUEST = "start_return_request";
    private static final String ADAPTER_TYPE = ShopifyStorefrontMcpActionAdapter.ADAPTER_TYPE;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ShopifyMcpClient mcpClient;
    private final ShopifyCustomerAccountMcpProperties properties;
    private final ShopifyCustomerAccountMcpDiscoveryService discoveryService;
    private final ObjectMapper objectMapper;

    public ShopifyCustomerAccountMcpActionAdapter(ShopifyMcpClient mcpClient,
                                                  ShopifyCustomerAccountMcpProperties properties,
                                                  ShopifyCustomerAccountMcpDiscoveryService discoveryService,
                                                  ObjectMapper objectMapper) {
        this.mcpClient = mcpClient;
        this.properties = properties;
        this.discoveryService = discoveryService;
        this.objectMapper = objectMapper;
    }

    public ShopifyBridgeActionResult getCustomerOrders(ShopifyBridgeCredentialAcquisition acquisition,
                                                       ShopifyBridgeActionExecuteRequest request) {
        return executeCustomerTool(acquisition, request, TOOL_GET_CUSTOMER_ORDERS, "Customer orders");
    }

    public ShopifyBridgeActionResult lookupOrder(ShopifyBridgeCredentialAcquisition acquisition,
                                                 ShopifyBridgeActionExecuteRequest request) {
        return executeCustomerTool(acquisition, request, TOOL_LOOKUP_ORDER, "Order lookup");
    }

    public ShopifyBridgeActionResult getOrderStatus(ShopifyBridgeCredentialAcquisition acquisition,
                                                    ShopifyBridgeActionExecuteRequest request) {
        return executeCustomerTool(acquisition, request, TOOL_GET_ORDER_STATUS, "Order status");
    }

    public ShopifyBridgeActionResult getReturnEligibility(ShopifyBridgeCredentialAcquisition acquisition,
                                                          ShopifyBridgeActionExecuteRequest request) {
        return executeCustomerTool(acquisition, request, TOOL_GET_RETURN_ELIGIBILITY, "Return eligibility");
    }

    public ShopifyBridgeActionResult startReturnRequest(ShopifyBridgeCredentialAcquisition acquisition,
                                                        ShopifyBridgeActionExecuteRequest request) {
        return executeCustomerTool(acquisition, request, TOOL_START_RETURN_REQUEST, "Return request started");
    }

    public Map<String, Object> readiness(String shopDomain) {
        LinkedHashMap<String, Object> server = new LinkedHashMap<>();
        server.put("serverRef", SERVER_REF_CUSTOMER_ACCOUNT);
        server.put("endpointKind", ENDPOINT_KIND_CUSTOMER_ACCOUNT);
        server.put("expectedTools", List.of(
            TOOL_GET_CUSTOMER_ORDERS,
            TOOL_LOOKUP_ORDER,
            TOOL_GET_ORDER_STATUS,
            TOOL_GET_RETURN_ELIGIBILITY,
            TOOL_START_RETURN_REQUEST
        ));
        server.put("enabled", properties.enabled());
        server.put("protectedCustomerDataApproved", properties.protectedCustomerDataApproved());
        server.put("authMode", "CUSTOMER_OAUTH_PKCE");
        server.put("scopes", properties.scopes());
        if (!properties.configured()) {
            server.put("ready", false);
            server.put("errorCode", "CUSTOMER_ACCOUNT_MCP_NOT_CONFIGURED");
            server.put("message", "Customer Accounts MCP is gated until OAuth/PKCE settings and protected customer data approval are configured.");
            return server;
        }
        try {
            var discovery = discoveryService.discover(shopDomain);
            server.put("ready", true);
            server.put("discoveryUrl", discovery.discoveryUri().toString());
            server.put("endpoint", discovery.mcpEndpoint().toString());
            server.put("verificationMethod", "well-known-discovery");
            server.put("message", "Customer Accounts MCP discovery is configured. Tool execution still requires a bound authenticated customer token.");
        } catch (ResponseStatusException ex) {
            server.put("ready", false);
            server.put("errorCode", "CUSTOMER_ACCOUNT_MCP_DISCOVERY_FAILED");
            server.put("message", safeReason(ex, "Customer Accounts MCP discovery failed."));
        }
        return server;
    }

    private ShopifyBridgeActionResult executeCustomerTool(ShopifyBridgeCredentialAcquisition acquisition,
                                                          ShopifyBridgeActionExecuteRequest request,
                                                          String toolName,
                                                          String successMessage) {
        String shopDomain = shopDomain(acquisition);
        if (shopDomain == null) {
            return ShopifyBridgeActionResult.failure("INVALID_REQUEST", "shopDomain is required.");
        }
        if (!properties.configured()) {
            return ShopifyBridgeActionResult.failure(
                "CUSTOMER_ACCOUNT_MCP_NOT_CONFIGURED",
                "Customer Accounts MCP is not configured for protected customer data execution."
            );
        }
        if (shopperSessionId(request) == null) {
            return ShopifyBridgeActionResult.failure(
                "CUSTOMER_SESSION_REQUIRED",
                "A bound shopper session is required for Customer Accounts MCP actions."
            );
        }
        String authorization = customerAuthorization(request);
        if (authorization == null) {
            return ShopifyBridgeActionResult.failure(
                "CUSTOMER_ACCOUNT_AUTH_REQUIRED",
                "A customer account OAuth token is required for this action."
            );
        }

        URI endpoint;
        try {
            endpoint = discoveryService.discover(shopDomain).mcpEndpoint();
        } catch (ResponseStatusException ex) {
            return ShopifyBridgeActionResult.failure(
                "CUSTOMER_ACCOUNT_MCP_DISCOVERY_FAILED",
                safeReason(ex, "Customer Accounts MCP discovery failed.")
            );
        }
        ObjectNode arguments = customerArguments(request);
        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("shopperSessionBound", true);
        return executeTool(endpoint, toolName, arguments, authorization, successMessage, data);
    }

    private ShopifyBridgeActionResult executeTool(URI endpoint,
                                                  String toolName,
                                                  JsonNode arguments,
                                                  String authorization,
                                                  String successMessage,
                                                  LinkedHashMap<String, Object> data) {
        try {
            JsonNode result = mcpClient.toolsCall(
                endpoint,
                toolName,
                arguments,
                ShopifyMcpRequestOptions.authorization(authorization)
            );
            Map<String, Object> toolResult = objectMapper.convertValue(result, MAP_TYPE);
            LinkedHashMap<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("type", "SHOPIFY_MCP_TOOL_RESULT");
            evidence.put("adapterType", ADAPTER_TYPE);
            evidence.put("serverRef", SERVER_REF_CUSTOMER_ACCOUNT);
            evidence.put("endpointKind", ENDPOINT_KIND_CUSTOMER_ACCOUNT);
            evidence.put("toolName", toolName);
            evidence.put("endpoint", endpoint.toString());

            data.put("adapterType", ADAPTER_TYPE);
            data.put("mcpServerRef", SERVER_REF_CUSTOMER_ACCOUNT);
            data.put("mcpEndpointKind", ENDPOINT_KIND_CUSTOMER_ACCOUNT);
            data.put("mcpToolName", toolName);
            data.put("mcpEndpoint", endpoint.toString());
            data.put("evidenceType", "SHOPIFY_MCP_TOOL_RESULT");
            data.put("evidence", evidence);
            data.put("toolResult", toolResult);
            return ShopifyBridgeActionResult.ok(successMessage, data);
        } catch (ResponseStatusException ex) {
            return ShopifyBridgeActionResult.failure("SHOPIFY_MCP_CALL_FAILED", safeReason(ex, "Customer Accounts MCP tool call failed."));
        }
    }

    private ObjectNode customerArguments(ShopifyBridgeActionExecuteRequest request) {
        ObjectNode arguments = objectMapper.createObjectNode();
        if (request == null || request.params() == null) {
            return arguments;
        }
        request.params().forEach((key, value) -> {
            if (!StringUtils.hasText(key) || value == null || isBridgeOnlyParam(key)) {
                return;
            }
            JsonNode node = objectMapper.valueToTree(value);
            if (node != null && !node.isNull()) {
                arguments.set(key.trim(), node);
            }
        });
        return arguments;
    }

    private boolean isBridgeOnlyParam(String key) {
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("customeraccountaccesstoken")
            || normalized.equals("customeraccesstoken")
            || normalized.equals("customerauthorization")
            || normalized.equals("authorization")
            || normalized.equals("shoppersessionid")
            || normalized.equals("confirmationaccepted");
    }

    private String customerAuthorization(ShopifyBridgeActionExecuteRequest request) {
        String direct = firstNonBlank(
            textParam(request, "customerAuthorization"),
            textParam(request, "customerAccountAccessToken"),
            textParam(request, "customerAccessToken")
        );
        if (direct != null) {
            return direct;
        }
        if (request != null && request.trace() != null) {
            Object authContext = request.trace().get("authContext");
            if (authContext instanceof Map<?, ?> map) {
                return firstNonBlank(
                    traceText(map, "customerAuthorization"),
                    traceText(map, "customerAccountAccessToken"),
                    traceText(map, "customerAccessToken")
                );
            }
        }
        return null;
    }

    private String shopperSessionId(ShopifyBridgeActionExecuteRequest request) {
        if (request == null) {
            return null;
        }
        String direct = textParam(request, "shopperSessionId");
        if (direct != null) {
            return direct;
        }
        if (request.trace() != null) {
            Object session = request.trace().get("sessionId");
            if (session != null && StringUtils.hasText(session.toString())) {
                return session.toString().trim();
            }
        }
        return normalize(request.idempotencyKey());
    }

    private String shopDomain(ShopifyBridgeCredentialAcquisition acquisition) {
        return acquisition == null || acquisition.store() == null
            ? null
            : normalize(acquisition.store().shopDomain());
    }

    private String textParam(ShopifyBridgeActionExecuteRequest request, String key) {
        Object value = request == null || request.params() == null ? null : request.params().get(key);
        return value == null ? null : normalize(value.toString());
    }

    private String traceText(Map<?, ?> map, String key) {
        Object value = map == null ? null : map.get(key);
        return value == null ? null : normalize(value.toString());
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = normalize(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String safeReason(ResponseStatusException ex, String fallback) {
        return ex.getReason() == null || ex.getReason().isBlank() ? fallback : ex.getReason();
    }
}
