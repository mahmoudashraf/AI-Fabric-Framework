package com.ai.fabric.product.shopify.bridge.action.service;

import com.ai.fabric.product.shopify.bridge.action.model.ShopifyBridgeActionExecuteRequest;
import com.ai.fabric.product.shopify.bridge.action.model.ShopifyBridgeActionResult;
import com.ai.fabric.product.shopify.bridge.client.mcp.ShopifyMcpClient;
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
import java.util.Map;

@Service
public class ShopifyStorefrontMcpActionAdapter {

    static final String ACTION_SHOPIFY_SEARCH_CATALOG = "shopify_search_catalog";
    static final String ACTION_SHOPIFY_SEARCH_POLICIES = "shopify_search_policies";
    static final String ACTION_SHOPIFY_GET_PRODUCT_DETAILS = "shopify_get_product_details";
    static final String ADAPTER_TYPE = "mcp-tool";
    static final String SERVER_REF_STOREFRONT_UCP = "shopify-storefront-ucp";
    static final String SERVER_REF_STOREFRONT = "shopify-storefront";
    static final String ENDPOINT_KIND_UCP_CATALOG = "UCP_CATALOG";
    static final String ENDPOINT_KIND_STOREFRONT_STANDARD = "STOREFRONT_STANDARD";
    static final String TOOL_SEARCH_CATALOG = "search_catalog";
    static final String TOOL_SEARCH_POLICIES_AND_FAQS = "search_shop_policies_and_faqs";
    static final String TOOL_GET_PRODUCT_DETAILS = "get_product_details";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ShopifyMcpClient mcpClient;
    private final ShopifyStorefrontMcpProperties properties;
    private final ObjectMapper objectMapper;

    public ShopifyStorefrontMcpActionAdapter(ShopifyMcpClient mcpClient,
                                             ShopifyStorefrontMcpProperties properties,
                                             ObjectMapper objectMapper) {
        this.mcpClient = mcpClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public ShopifyBridgeActionResult searchCatalog(ShopifyBridgeCredentialAcquisition acquisition,
                                                   ShopifyBridgeActionExecuteRequest request) {
        String shopDomain = acquisition == null || acquisition.store() == null
            ? null
            : normalize(acquisition.store().shopDomain());
        String query = firstNonBlank(textParam(request, "query"), textParam(request, "q"), textParam(request, "searchTerm"));
        if (shopDomain == null) {
            return ShopifyBridgeActionResult.failure("INVALID_REQUEST", "shopDomain is required.");
        }
        if (query == null) {
            return ShopifyBridgeActionResult.failure("INVALID_REQUEST", "params.query is required.");
        }
        if (!StringUtils.hasText(properties.ucpAgentProfile())) {
            return ShopifyBridgeActionResult.failure(
                "MCP_PROFILE_NOT_CONFIGURED",
                "Shopify UCP agent profile is not configured."
            );
        }

        URI endpoint = URI.create("https://" + shopDomain + "/api/mcp");
        ObjectNode arguments = buildSearchCatalogArguments(request, query);
        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("query", query);
        return executeTool(
            endpoint,
            SERVER_REF_STOREFRONT_UCP,
            ENDPOINT_KIND_UCP_CATALOG,
            TOOL_SEARCH_CATALOG,
            arguments,
            "Catalog search",
            "Shopify MCP catalog search failed.",
            data
        );
    }

    public ShopifyBridgeActionResult searchPolicies(ShopifyBridgeCredentialAcquisition acquisition,
                                                    ShopifyBridgeActionExecuteRequest request) {
        String shopDomain = shopDomain(acquisition);
        String query = firstNonBlank(textParam(request, "query"), textParam(request, "q"), textParam(request, "policyType"));
        if (shopDomain == null) {
            return ShopifyBridgeActionResult.failure("INVALID_REQUEST", "shopDomain is required.");
        }
        if (query == null) {
            return ShopifyBridgeActionResult.failure("INVALID_REQUEST", "params.query is required.");
        }

        URI endpoint = URI.create("https://" + shopDomain + "/api/mcp");
        ObjectNode arguments = objectMapper.createObjectNode();
        arguments.put("query", query);
        putIfText(arguments, "context", firstNonBlank(textParam(request, "context"), textParam(request, "intent")));

        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("query", query);
        return executeTool(
            endpoint,
            SERVER_REF_STOREFRONT,
            ENDPOINT_KIND_STOREFRONT_STANDARD,
            TOOL_SEARCH_POLICIES_AND_FAQS,
            arguments,
            "Policy search",
            "Shopify MCP policy search failed.",
            data
        );
    }

    public ShopifyBridgeActionResult getProductDetails(ShopifyBridgeCredentialAcquisition acquisition,
                                                       ShopifyBridgeActionExecuteRequest request) {
        String shopDomain = shopDomain(acquisition);
        String productId = firstNonBlank(
            textParam(request, "product_id"),
            textParam(request, "productId"),
            textParam(request, "id")
        );
        if (shopDomain == null) {
            return ShopifyBridgeActionResult.failure("INVALID_REQUEST", "shopDomain is required.");
        }
        if (productId == null) {
            return ShopifyBridgeActionResult.failure("INVALID_REQUEST", "params.product_id is required.");
        }

        URI endpoint = URI.create("https://" + shopDomain + "/api/mcp");
        ObjectNode arguments = objectMapper.createObjectNode();
        arguments.put("product_id", productId);
        putIfText(arguments, "country", textParam(request, "country"));
        putIfText(arguments, "language", textParam(request, "language"));
        Object options = paramValue(request, "options");
        if (options != null) {
            JsonNode optionsNode = objectMapper.valueToTree(options);
            if (optionsNode.isObject() && !optionsNode.isEmpty()) {
                arguments.set("options", optionsNode);
            }
        }

        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("productId", productId);
        return executeTool(
            endpoint,
            SERVER_REF_STOREFRONT,
            ENDPOINT_KIND_STOREFRONT_STANDARD,
            TOOL_GET_PRODUCT_DETAILS,
            arguments,
            "Product details",
            "Shopify MCP product details lookup failed.",
            data
        );
    }

    private ShopifyBridgeActionResult executeTool(URI endpoint,
                                                  String serverRef,
                                                  String endpointKind,
                                                  String toolName,
                                                  JsonNode arguments,
                                                  String successMessage,
                                                  String failureMessage,
                                                  LinkedHashMap<String, Object> data) {
        try {
            JsonNode result = mcpClient.toolsCall(endpoint, toolName, arguments);
            Map<String, Object> toolResult = objectMapper.convertValue(result, MAP_TYPE);
            LinkedHashMap<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("type", "SHOPIFY_MCP_TOOL_RESULT");
            evidence.put("adapterType", ADAPTER_TYPE);
            evidence.put("serverRef", serverRef);
            evidence.put("endpointKind", endpointKind);
            evidence.put("toolName", toolName);
            evidence.put("endpoint", endpoint.toString());

            data.put("adapterType", ADAPTER_TYPE);
            data.put("mcpServerRef", serverRef);
            data.put("mcpEndpointKind", endpointKind);
            data.put("mcpToolName", toolName);
            data.put("mcpEndpoint", endpoint.toString());
            data.put("evidenceType", "SHOPIFY_MCP_TOOL_RESULT");
            data.put("evidence", evidence);
            data.put("toolResult", toolResult);
            return ShopifyBridgeActionResult.ok(successMessage, data);
        } catch (ResponseStatusException ex) {
            String message = ex.getReason() == null || ex.getReason().isBlank()
                ? failureMessage
                : ex.getReason();
            return ShopifyBridgeActionResult.failure("SHOPIFY_MCP_CALL_FAILED", message);
        }
    }

    private String shopDomain(ShopifyBridgeCredentialAcquisition acquisition) {
        return acquisition == null || acquisition.store() == null
            ? null
            : normalize(acquisition.store().shopDomain());
    }

    private ObjectNode buildSearchCatalogArguments(ShopifyBridgeActionExecuteRequest request, String query) {
        ObjectNode arguments = objectMapper.createObjectNode();
        ObjectNode meta = arguments.putObject("meta");
        ObjectNode ucpAgent = meta.putObject("ucp-agent");
        ucpAgent.put("profile", properties.ucpAgentProfile());

        ObjectNode catalog = arguments.putObject("catalog");
        catalog.put("query", query);

        ObjectNode context = objectMapper.createObjectNode();
        putIfText(context, "address_country", firstNonBlank(textParam(request, "country"), textParam(request, "addressCountry")));
        putIfText(context, "intent", textParam(request, "intent"));
        if (!context.isEmpty()) {
            catalog.set("context", context);
        }

        Integer limit = integerParam(request, "limit", 1, 20);
        if (limit != null) {
            ObjectNode pagination = catalog.putObject("pagination");
            pagination.put("limit", limit);
        }
        return arguments;
    }

    private void putIfText(ObjectNode target, String fieldName, String value) {
        if (target != null && StringUtils.hasText(fieldName) && StringUtils.hasText(value)) {
            target.put(fieldName, value.trim());
        }
    }

    private String textParam(ShopifyBridgeActionExecuteRequest request, String key) {
        Object value = paramValue(request, key);
        return value == null ? null : normalize(value.toString());
    }

    private Object paramValue(ShopifyBridgeActionExecuteRequest request, String key) {
        if (request == null || request.params() == null) {
            return null;
        }
        return request.params().get(key);
    }

    private Integer integerParam(ShopifyBridgeActionExecuteRequest request, String key, int min, int max) {
        String raw = textParam(request, key);
        if (raw == null) {
            return null;
        }
        try {
            int value = Integer.parseInt(raw);
            return Math.max(min, Math.min(max, value));
        } catch (NumberFormatException ignored) {
            return null;
        }
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
}
