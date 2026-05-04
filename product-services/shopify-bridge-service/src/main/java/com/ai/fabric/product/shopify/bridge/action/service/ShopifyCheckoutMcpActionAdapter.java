package com.ai.fabric.product.shopify.bridge.action.service;

import com.ai.fabric.product.shopify.bridge.action.model.ShopifyBridgeActionExecuteRequest;
import com.ai.fabric.product.shopify.bridge.action.model.ShopifyBridgeActionResult;
import com.ai.fabric.product.shopify.bridge.client.mcp.ShopifyMcpClient;
import com.ai.fabric.product.shopify.bridge.client.mcp.ShopifyMcpClient.ShopifyMcpRequestOptions;
import com.ai.fabric.product.shopify.bridge.config.ShopifyCheckoutMcpProperties;
import com.ai.fabric.product.shopify.bridge.config.ShopifyStorefrontMcpProperties;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ShopifyCheckoutMcpActionAdapter {

    static final String SERVER_REF_CHECKOUT = "shopify-checkout";
    static final String ENDPOINT_KIND_CHECKOUT_UCP = "CHECKOUT_UCP";
    static final String TOOL_CREATE_CHECKOUT = "create_checkout";
    static final String TOOL_GET_CHECKOUT = "get_checkout";
    static final String TOOL_UPDATE_CHECKOUT = "update_checkout";
    static final String TOOL_COMPLETE_CHECKOUT = "complete_checkout";
    static final String TOOL_CANCEL_CHECKOUT = "cancel_checkout";
    private static final String ADAPTER_TYPE = ShopifyStorefrontMcpActionAdapter.ADAPTER_TYPE;
    private static final Set<String> TERMINAL_TOOLS = Set.of(TOOL_COMPLETE_CHECKOUT, TOOL_CANCEL_CHECKOUT);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ShopifyMcpClient mcpClient;
    private final ShopifyCheckoutMcpTokenService tokenService;
    private final ShopifyCheckoutMcpProperties checkoutProperties;
    private final ShopifyStorefrontMcpProperties storefrontMcpProperties;
    private final ObjectMapper objectMapper;

    public ShopifyCheckoutMcpActionAdapter(ShopifyMcpClient mcpClient,
                                           ShopifyCheckoutMcpTokenService tokenService,
                                           ShopifyCheckoutMcpProperties checkoutProperties,
                                           ShopifyStorefrontMcpProperties storefrontMcpProperties,
                                           ObjectMapper objectMapper) {
        this.mcpClient = mcpClient;
        this.tokenService = tokenService;
        this.checkoutProperties = checkoutProperties;
        this.storefrontMcpProperties = storefrontMcpProperties;
        this.objectMapper = objectMapper;
    }

    public ShopifyBridgeActionResult createCheckout(ShopifyBridgeCredentialAcquisition acquisition,
                                                    ShopifyBridgeActionExecuteRequest request) {
        return executeCheckoutTool(acquisition, request, TOOL_CREATE_CHECKOUT, "Checkout created");
    }

    public ShopifyBridgeActionResult getCheckout(ShopifyBridgeCredentialAcquisition acquisition,
                                                 ShopifyBridgeActionExecuteRequest request) {
        return executeCheckoutTool(acquisition, request, TOOL_GET_CHECKOUT, "Checkout");
    }

    public ShopifyBridgeActionResult updateCheckout(ShopifyBridgeCredentialAcquisition acquisition,
                                                    ShopifyBridgeActionExecuteRequest request) {
        return executeCheckoutTool(acquisition, request, TOOL_UPDATE_CHECKOUT, "Checkout updated");
    }

    public ShopifyBridgeActionResult completeCheckout(ShopifyBridgeCredentialAcquisition acquisition,
                                                      ShopifyBridgeActionExecuteRequest request) {
        return executeCheckoutTool(acquisition, request, TOOL_COMPLETE_CHECKOUT, "Checkout completed");
    }

    public ShopifyBridgeActionResult cancelCheckout(ShopifyBridgeCredentialAcquisition acquisition,
                                                    ShopifyBridgeActionExecuteRequest request) {
        return executeCheckoutTool(acquisition, request, TOOL_CANCEL_CHECKOUT, "Checkout cancelled");
    }

    public Map<String, Object> readiness(String shopDomain) {
        LinkedHashMap<String, Object> server = new LinkedHashMap<>();
        String normalizedShop = normalize(shopDomain);
        server.put("serverRef", SERVER_REF_CHECKOUT);
        server.put("endpointKind", ENDPOINT_KIND_CHECKOUT_UCP);
        server.put("expectedTools", List.of(
            TOOL_CREATE_CHECKOUT,
            TOOL_GET_CHECKOUT,
            TOOL_UPDATE_CHECKOUT,
            TOOL_COMPLETE_CHECKOUT,
            TOOL_CANCEL_CHECKOUT
        ));
        server.put("enabled", checkoutProperties.enabled());
        server.put("authMode", "SHOPIFY_AGENTIC_CLIENT_CREDENTIALS");
        server.put("terminalOperationsEnabled", checkoutProperties.terminalOperationsEnabled());
        if (normalizedShop != null) {
            server.put("endpoint", checkoutEndpoint(normalizedShop).toString());
        }
        if (!checkoutProperties.configured()) {
            server.put("ready", false);
            server.put("errorCode", "CHECKOUT_MCP_NOT_CONFIGURED");
            server.put("message", "Checkout MCP is gated until Shopify agentic client credentials are configured.");
            return server;
        }
        if (normalizedShop == null) {
            server.put("ready", false);
            server.put("errorCode", "INVALID_REQUEST");
            server.put("message", "shopDomain is required.");
            return server;
        }
        try {
            String token = tokenService.accessToken();
            JsonNode result = mcpClient.toolsList(checkoutEndpoint(normalizedShop), ShopifyMcpRequestOptions.bearer(token));
            LinkedHashSet<String> present = toolNames(result);
            List<String> missing = List.of(
                TOOL_CREATE_CHECKOUT,
                TOOL_GET_CHECKOUT,
                TOOL_UPDATE_CHECKOUT,
                TOOL_COMPLETE_CHECKOUT,
                TOOL_CANCEL_CHECKOUT
            ).stream().filter(expected -> present.stream().noneMatch(expected::equalsIgnoreCase)).toList();
            server.put("presentTools", List.copyOf(present));
            server.put("missingTools", missing);
            server.put("verificationMethod", "tools/list");
            server.put("ready", missing.isEmpty());
        } catch (ResponseStatusException ex) {
            server.put("ready", false);
            server.put("errorCode", "CHECKOUT_MCP_READINESS_FAILED");
            server.put("message", safeReason(ex, "Checkout MCP readiness failed."));
        }
        return server;
    }

    private ShopifyBridgeActionResult executeCheckoutTool(ShopifyBridgeCredentialAcquisition acquisition,
                                                          ShopifyBridgeActionExecuteRequest request,
                                                          String toolName,
                                                          String successMessage) {
        String shopDomain = shopDomain(acquisition);
        if (shopDomain == null) {
            return ShopifyBridgeActionResult.failure("INVALID_REQUEST", "shopDomain is required.");
        }
        if (!checkoutProperties.configured()) {
            return ShopifyBridgeActionResult.failure(
                "CHECKOUT_MCP_NOT_CONFIGURED",
                "Shopify Checkout MCP client credentials are not configured."
            );
        }
        if (TERMINAL_TOOLS.contains(toolName) && !checkoutProperties.terminalOperationsEnabled()) {
            return ShopifyBridgeActionResult.failure(
                "CHECKOUT_TERMINAL_OPERATION_DISABLED",
                "Checkout terminal operations are disabled; hand off the buyer with continue_url instead."
            );
        }
        if (shopperSessionId(request) == null) {
            return ShopifyBridgeActionResult.failure(
                "SHOPPER_SESSION_REQUIRED",
                "A shopper session identifier is required for governed checkout actions."
            );
        }
        if (requiresCheckoutId(toolName) && checkoutId(request) == null) {
            return ShopifyBridgeActionResult.failure("INVALID_REQUEST", "params.id or params.checkout_id is required.");
        }

        String token;
        try {
            token = tokenService.accessToken();
        } catch (ResponseStatusException ex) {
            return ShopifyBridgeActionResult.failure("CHECKOUT_MCP_NOT_CONFIGURED", safeReason(ex, "Checkout MCP token acquisition failed."));
        }
        ObjectNode arguments = checkoutArguments(request, toolName);
        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("shopperSessionBound", true);
        return executeTool(
            checkoutEndpoint(shopDomain),
            toolName,
            arguments,
            token,
            successMessage,
            data
        );
    }

    private ShopifyBridgeActionResult executeTool(URI endpoint,
                                                  String toolName,
                                                  JsonNode arguments,
                                                  String bearerToken,
                                                  String successMessage,
                                                  LinkedHashMap<String, Object> data) {
        try {
            JsonNode result = mcpClient.toolsCall(
                endpoint,
                toolName,
                arguments,
                ShopifyMcpRequestOptions.bearer(bearerToken)
            );
            Map<String, Object> toolResult = objectMapper.convertValue(result, MAP_TYPE);
            LinkedHashMap<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("type", "SHOPIFY_MCP_TOOL_RESULT");
            evidence.put("adapterType", ADAPTER_TYPE);
            evidence.put("serverRef", SERVER_REF_CHECKOUT);
            evidence.put("endpointKind", ENDPOINT_KIND_CHECKOUT_UCP);
            evidence.put("toolName", toolName);
            evidence.put("endpoint", endpoint.toString());

            data.put("adapterType", ADAPTER_TYPE);
            data.put("mcpServerRef", SERVER_REF_CHECKOUT);
            data.put("mcpEndpointKind", ENDPOINT_KIND_CHECKOUT_UCP);
            data.put("mcpToolName", toolName);
            data.put("mcpEndpoint", endpoint.toString());
            data.put("evidenceType", "SHOPIFY_MCP_TOOL_RESULT");
            data.put("evidence", evidence);
            data.put("toolResult", toolResult);
            return ShopifyBridgeActionResult.ok(successMessage, data);
        } catch (ResponseStatusException ex) {
            return ShopifyBridgeActionResult.failure("SHOPIFY_MCP_CALL_FAILED", safeReason(ex, "Checkout MCP tool call failed."));
        }
    }

    private ObjectNode checkoutArguments(ShopifyBridgeActionExecuteRequest request, String toolName) {
        ObjectNode arguments = objectMapper.createObjectNode();
        ObjectNode meta = arguments.putObject("meta");
        ObjectNode ucpAgent = meta.putObject("ucp-agent");
        ucpAgent.put("profile", storefrontMcpProperties.ucpAgentProfile());
        if (TOOL_COMPLETE_CHECKOUT.equals(toolName) || TOOL_CANCEL_CHECKOUT.equals(toolName)) {
            meta.put("idempotency-key", firstNonBlank(request == null ? null : request.idempotencyKey(), UUID.randomUUID().toString()));
        }
        if (requiresCheckoutId(toolName)) {
            arguments.put("id", checkoutId(request));
        }
        if (request == null || request.params() == null) {
            return arguments;
        }
        request.params().forEach((key, value) -> {
            if (!StringUtils.hasText(key) || value == null || isBridgeOnlyParam(key)) {
                return;
            }
            String normalizedKey = normalizeCheckoutParam(key);
            if ("id".equals(normalizedKey) && requiresCheckoutId(toolName)) {
                return;
            }
            JsonNode node = objectMapper.valueToTree(value);
            if (node != null && !node.isNull()) {
                arguments.set(normalizedKey, node);
            }
        });
        return arguments;
    }

    private LinkedHashSet<String> toolNames(JsonNode result) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        JsonNode tools = result == null ? null : result.path("tools");
        if (tools != null && tools.isArray()) {
            for (JsonNode tool : tools) {
                String name = normalize(tool.path("name").asText(null));
                if (name != null) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    private boolean requiresCheckoutId(String toolName) {
        return TOOL_GET_CHECKOUT.equals(toolName)
            || TOOL_UPDATE_CHECKOUT.equals(toolName)
            || TOOL_COMPLETE_CHECKOUT.equals(toolName)
            || TOOL_CANCEL_CHECKOUT.equals(toolName);
    }

    private boolean isBridgeOnlyParam(String key) {
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("shoppersessionid")
            || normalized.equals("confirmationaccepted")
            || normalized.equals("idempotencykey")
            || normalized.equals("customeraccountaccesstoken")
            || normalized.equals("customeraccesstoken")
            || normalized.equals("customerauthorization");
    }

    private String normalizeCheckoutParam(String key) {
        String normalized = key.trim();
        if ("checkout_id".equalsIgnoreCase(normalized) || "checkoutId".equals(normalized)) {
            return "id";
        }
        return normalized;
    }

    private String checkoutId(ShopifyBridgeActionExecuteRequest request) {
        return firstNonBlank(textParam(request, "id"), textParam(request, "checkout_id"), textParam(request, "checkoutId"));
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

    private URI checkoutEndpoint(String shopDomain) {
        return URI.create("https://" + shopDomain + "/api/ucp/mcp");
    }

    private String textParam(ShopifyBridgeActionExecuteRequest request, String key) {
        Object value = request == null || request.params() == null ? null : request.params().get(key);
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
