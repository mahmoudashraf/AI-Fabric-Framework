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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ShopifyStorefrontMcpActionAdapter {

    static final String ACTION_SHOPIFY_SEARCH_CATALOG = "shopify_search_catalog";
    static final String ACTION_SHOPIFY_LOOKUP_CATALOG = "shopify_lookup_catalog";
    static final String ACTION_SHOPIFY_GET_PRODUCT = "shopify_get_product";
    static final String ACTION_SHOPIFY_SEARCH_POLICIES = "shopify_search_policies";
    static final String ACTION_SHOPIFY_GET_CART = "shopify_get_cart";
    static final String ACTION_SHOPIFY_UPDATE_CART = "shopify_update_cart";
    static final String ACTION_SHOPIFY_GET_PRODUCT_DETAILS = "shopify_get_product_details";
    static final String ADAPTER_TYPE = "mcp-tool";
    static final String SERVER_REF_STOREFRONT_UCP = "shopify-storefront-ucp";
    static final String SERVER_REF_STOREFRONT = "shopify-storefront";
    static final String ENDPOINT_KIND_UCP_CATALOG = "UCP_CATALOG";
    static final String ENDPOINT_KIND_STOREFRONT_STANDARD = "STOREFRONT_STANDARD";
    static final String TOOL_SEARCH_CATALOG = "search_catalog";
    static final String TOOL_LOOKUP_CATALOG = "lookup_catalog";
    static final String TOOL_GET_PRODUCT = "get_product";
    static final String TOOL_SEARCH_POLICIES_AND_FAQS = "search_shop_policies_and_faqs";
    static final String TOOL_GET_CART = "get_cart";
    static final String TOOL_UPDATE_CART = "update_cart";
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

        URI endpoint = ucpEndpoint(shopDomain);
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

    public ShopifyBridgeActionResult lookupCatalog(ShopifyBridgeCredentialAcquisition acquisition,
                                                   ShopifyBridgeActionExecuteRequest request) {
        String shopDomain = shopDomain(acquisition);
        JsonNode ids = catalogIds(request);
        if (shopDomain == null) {
            return ShopifyBridgeActionResult.failure("INVALID_REQUEST", "shopDomain is required.");
        }
        if (ids == null || !ids.isArray() || ids.isEmpty()) {
            return ShopifyBridgeActionResult.failure("INVALID_REQUEST", "params.ids or params.id is required.");
        }
        if (!StringUtils.hasText(properties.ucpAgentProfile())) {
            return ShopifyBridgeActionResult.failure(
                "MCP_PROFILE_NOT_CONFIGURED",
                "Shopify UCP agent profile is not configured."
            );
        }

        ObjectNode arguments = buildUcpCatalogArguments();
        ObjectNode catalog = (ObjectNode) arguments.path("catalog");
        catalog.set("ids", ids);
        ObjectNode context = catalogContext(request);
        if (!context.isEmpty()) {
            catalog.set("context", context);
        }

        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("ids", objectMapper.convertValue(ids, Object.class));
        return executeTool(
            ucpEndpoint(shopDomain),
            SERVER_REF_STOREFRONT_UCP,
            ENDPOINT_KIND_UCP_CATALOG,
            TOOL_LOOKUP_CATALOG,
            arguments,
            "Catalog lookup",
            "Shopify MCP catalog lookup failed.",
            data
        );
    }

    public ShopifyBridgeActionResult getProduct(ShopifyBridgeCredentialAcquisition acquisition,
                                                ShopifyBridgeActionExecuteRequest request) {
        String shopDomain = shopDomain(acquisition);
        String productId = firstNonBlank(textParam(request, "id"), textParam(request, "product_id"), textParam(request, "productId"));
        if (shopDomain == null) {
            return ShopifyBridgeActionResult.failure("INVALID_REQUEST", "shopDomain is required.");
        }
        if (productId == null) {
            return ShopifyBridgeActionResult.failure("INVALID_REQUEST", "params.id is required.");
        }
        if (!StringUtils.hasText(properties.ucpAgentProfile())) {
            return ShopifyBridgeActionResult.failure(
                "MCP_PROFILE_NOT_CONFIGURED",
                "Shopify UCP agent profile is not configured."
            );
        }

        ObjectNode arguments = buildUcpCatalogArguments();
        ObjectNode catalog = (ObjectNode) arguments.path("catalog");
        catalog.put("id", productId);
        setIfPresent(catalog, "selected", paramValue(request, "selected"));
        setIfPresent(catalog, "preferences", paramValue(request, "preferences"));
        ObjectNode context = catalogContext(request);
        if (!context.isEmpty()) {
            catalog.set("context", context);
        }

        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("productId", productId);
        return executeTool(
            ucpEndpoint(shopDomain),
            SERVER_REF_STOREFRONT_UCP,
            ENDPOINT_KIND_UCP_CATALOG,
            TOOL_GET_PRODUCT,
            arguments,
            "Product details",
            "Shopify MCP product lookup failed.",
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

        URI endpoint = storefrontEndpoint(shopDomain);
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

        URI endpoint = storefrontEndpoint(shopDomain);
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

    public ShopifyBridgeActionResult getCart(ShopifyBridgeCredentialAcquisition acquisition,
                                             ShopifyBridgeActionExecuteRequest request) {
        String shopDomain = shopDomain(acquisition);
        String cartId = firstNonBlank(textParam(request, "cart_id"), textParam(request, "cartId"));
        if (shopDomain == null) {
            return ShopifyBridgeActionResult.failure("INVALID_REQUEST", "shopDomain is required.");
        }
        if (cartId == null) {
            return ShopifyBridgeActionResult.failure("INVALID_REQUEST", "params.cart_id is required.");
        }

        ObjectNode arguments = objectMapper.createObjectNode();
        arguments.put("cart_id", cartId);
        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("cartId", cartId);
        return executeTool(
            storefrontEndpoint(shopDomain),
            SERVER_REF_STOREFRONT,
            ENDPOINT_KIND_STOREFRONT_STANDARD,
            TOOL_GET_CART,
            arguments,
            "Cart",
            "Shopify MCP cart lookup failed.",
            data
        );
    }

    public ShopifyBridgeActionResult updateCart(ShopifyBridgeCredentialAcquisition acquisition,
                                                ShopifyBridgeActionExecuteRequest request) {
        String shopDomain = shopDomain(acquisition);
        if (shopDomain == null) {
            return ShopifyBridgeActionResult.failure("INVALID_REQUEST", "shopDomain is required.");
        }

        ObjectNode arguments = objectMapper.createObjectNode();
        putIfText(arguments, "cart_id", firstNonBlank(textParam(request, "cart_id"), textParam(request, "cartId")));
        setIfPresent(arguments, "add_items", paramValue(request, "add_items"));
        setIfPresent(arguments, "update_items", paramValue(request, "update_items"));
        setIfPresent(arguments, "remove_line_ids", paramValue(request, "remove_line_ids"));
        setIfPresent(arguments, "buyer_identity", paramValue(request, "buyer_identity"));
        setIfPresent(arguments, "delivery_addresses_to_add", paramValue(request, "delivery_addresses_to_add"));
        setIfPresent(arguments, "delivery_addresses_to_replace", paramValue(request, "delivery_addresses_to_replace"));
        setIfPresent(arguments, "selected_delivery_options", paramValue(request, "selected_delivery_options"));
        setIfPresent(arguments, "discount_codes", paramValue(request, "discount_codes"));
        setIfPresent(arguments, "gift_card_codes", paramValue(request, "gift_card_codes"));
        putIfText(arguments, "note", textParam(request, "note"));
        if (arguments.isEmpty()) {
            return ShopifyBridgeActionResult.failure(
                "INVALID_REQUEST",
                "Cart update params must include cart_id or at least one update field."
            );
        }

        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("cartId", firstNonBlank(textParam(request, "cart_id"), textParam(request, "cartId")));
        return executeTool(
            storefrontEndpoint(shopDomain),
            SERVER_REF_STOREFRONT,
            ENDPOINT_KIND_STOREFRONT_STANDARD,
            TOOL_UPDATE_CART,
            arguments,
            "Cart updated",
            "Shopify MCP cart update failed.",
            data
        );
    }

    public Map<String, Object> readiness(String shopDomain) {
        String normalizedShop = normalize(shopDomain);
        if (normalizedShop == null) {
            return Map.of(
                "ready", false,
                "errorCode", "INVALID_REQUEST",
                "message", "shopDomain is required."
            );
        }

        List<Map<String, Object>> servers = new ArrayList<>();
        servers.add(inspectUcpCatalogServer(
            SERVER_REF_STOREFRONT_UCP,
            ENDPOINT_KIND_UCP_CATALOG,
            ucpEndpoint(normalizedShop),
            Set.of(TOOL_SEARCH_CATALOG, TOOL_LOOKUP_CATALOG, TOOL_GET_PRODUCT)
        ));
        servers.add(inspectToolsListServer(
            SERVER_REF_STOREFRONT,
            ENDPOINT_KIND_STOREFRONT_STANDARD,
            storefrontEndpoint(normalizedShop),
            Set.of(TOOL_SEARCH_POLICIES_AND_FAQS, TOOL_GET_CART, TOOL_UPDATE_CART)
        ));

        boolean ready = servers.stream().allMatch(server -> Boolean.TRUE.equals(server.get("ready")));
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("ready", ready);
        summary.put("shopDomain", normalizedShop);
        summary.put("adapterType", ADAPTER_TYPE);
        summary.put("servers", servers);
        summary.put("message", ready
            ? "Shopify MCP tools match the expected greenfield action bundles."
            : "Shopify MCP tool drift or endpoint unavailability detected.");
        return summary;
    }

    private Map<String, Object> inspectUcpCatalogServer(String serverRef,
                                                        String endpointKind,
                                                        URI endpoint,
                                                        Set<String> expectedTools) {
        LinkedHashMap<String, Object> server = baseReadinessServer(serverRef, endpointKind, endpoint, expectedTools);
        try {
            JsonNode result = mcpClient.toolsCall(endpoint, TOOL_SEARCH_CATALOG, buildUcpReadinessProbeArguments());
            boolean toolReturnedError = result != null && result.path("isError").asBoolean(false);
            server.put("presentTools", toolReturnedError ? List.of() : List.copyOf(expectedTools));
            server.put("missingTools", toolReturnedError ? List.copyOf(expectedTools) : List.of());
            server.put("verificationMethod", "tools/call:" + TOOL_SEARCH_CATALOG);
            server.put("ready", !toolReturnedError);
            if (toolReturnedError) {
                server.put("errorCode", "SHOPIFY_MCP_PROBE_FAILED");
                server.put("message", "Shopify UCP catalog probe returned an MCP tool error.");
            }
        } catch (ResponseStatusException ex) {
            server.put("presentTools", List.of());
            server.put("missingTools", List.copyOf(expectedTools));
            server.put("ready", false);
            server.put("errorCode", "SHOPIFY_MCP_PROBE_FAILED");
            server.put("message", ex.getReason() == null || ex.getReason().isBlank()
                ? "Shopify UCP catalog probe failed."
                : ex.getReason());
        }
        return server;
    }

    private ObjectNode buildUcpReadinessProbeArguments() {
        ObjectNode arguments = buildUcpCatalogArguments();
        ObjectNode catalog = (ObjectNode) arguments.path("catalog");
        catalog.put("query", "readiness probe");
        ObjectNode pagination = catalog.putObject("pagination");
        pagination.put("limit", 1);
        return arguments;
    }

    private Map<String, Object> inspectToolsListServer(String serverRef,
                                                       String endpointKind,
                                                       URI endpoint,
                                                       Set<String> expectedTools) {
        LinkedHashMap<String, Object> server = baseReadinessServer(serverRef, endpointKind, endpoint, expectedTools);
        try {
            ShopifyMcpClient.ShopifyMcpSession session = mcpClient.initialize(endpoint);
            JsonNode result = mcpClient.toolsList(session);
            LinkedHashSet<String> presentTools = toolNames(result);
            List<String> missingTools = expectedTools.stream()
                .filter(expected -> presentTools.stream().noneMatch(expected::equalsIgnoreCase))
                .toList();
            server.put("presentTools", List.copyOf(presentTools));
            server.put("missingTools", missingTools);
            server.put("protocolVersion", session.protocolVersion());
            server.put("sessionEstablished", StringUtils.hasText(session.sessionId()));
            server.put("verificationMethod", "initialize+tools/list");
            server.put("ready", missingTools.isEmpty());
        } catch (ResponseStatusException ex) {
            server.put("presentTools", List.of());
            server.put("missingTools", List.copyOf(expectedTools));
            server.put("ready", false);
            server.put("errorCode", "SHOPIFY_MCP_TOOLS_LIST_FAILED");
            server.put("message", ex.getReason() == null || ex.getReason().isBlank()
                ? "Shopify MCP tools/list failed."
                : ex.getReason());
        }
        return server;
    }

    private LinkedHashMap<String, Object> baseReadinessServer(String serverRef,
                                                              String endpointKind,
                                                              URI endpoint,
                                                              Set<String> expectedTools) {
        LinkedHashMap<String, Object> server = new LinkedHashMap<>();
        server.put("serverRef", serverRef);
        server.put("endpointKind", endpointKind);
        server.put("endpoint", endpoint.toString());
        server.put("expectedTools", List.copyOf(expectedTools));
        return server;
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
        ObjectNode arguments = buildUcpCatalogArguments();
        ObjectNode catalog = (ObjectNode) arguments.path("catalog");
        catalog.put("query", query);

        ObjectNode context = catalogContext(request);
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

    private ObjectNode buildUcpCatalogArguments() {
        ObjectNode arguments = objectMapper.createObjectNode();
        ObjectNode meta = arguments.putObject("meta");
        ObjectNode ucpAgent = meta.putObject("ucp-agent");
        ucpAgent.put("profile", properties.ucpAgentProfile());
        arguments.putObject("catalog");
        return arguments;
    }

    private ObjectNode catalogContext(ShopifyBridgeActionExecuteRequest request) {
        ObjectNode context = objectMapper.createObjectNode();
        putIfText(context, "address_country", firstNonBlank(textParam(request, "country"), textParam(request, "addressCountry")));
        putIfText(context, "language", textParam(request, "language"));
        putIfText(context, "currency", textParam(request, "currency"));
        putIfText(context, "intent", textParam(request, "intent"));
        return context;
    }

    private void putIfText(ObjectNode target, String fieldName, String value) {
        if (target != null && StringUtils.hasText(fieldName) && StringUtils.hasText(value)) {
            target.put(fieldName, value.trim());
        }
    }

    private void setIfPresent(ObjectNode target, String fieldName, Object value) {
        if (target == null || !StringUtils.hasText(fieldName) || value == null) {
            return;
        }
        JsonNode node = objectMapper.valueToTree(value);
        if (node == null || node.isNull() || node.isMissingNode()) {
            return;
        }
        if (node.isTextual() && !StringUtils.hasText(node.asText())) {
            return;
        }
        if ((node.isArray() || node.isObject()) && node.isEmpty()) {
            return;
        }
        target.set(fieldName, node);
    }

    private JsonNode catalogIds(ShopifyBridgeActionExecuteRequest request) {
        Object value = paramValue(request, "ids");
        if (value == null) {
            value = firstNonBlank(textParam(request, "id"), textParam(request, "product_id"), textParam(request, "productId"));
        }
        if (value == null) {
            return objectMapper.createArrayNode();
        }
        if (value instanceof Iterable<?> iterable) {
            com.fasterxml.jackson.databind.node.ArrayNode array = objectMapper.createArrayNode();
            for (Object item : iterable) {
                String normalized = item == null ? null : normalize(item.toString());
                if (normalized != null && array.size() < 10) {
                    array.add(normalized);
                }
            }
            return array;
        }
        if (value.getClass().isArray()) {
            return objectMapper.valueToTree(value);
        }
        String raw = normalize(value.toString());
        com.fasterxml.jackson.databind.node.ArrayNode array = objectMapper.createArrayNode();
        if (raw != null) {
            for (String part : raw.split(",")) {
                String normalized = normalize(part);
                if (normalized != null && array.size() < 10) {
                    array.add(normalized);
                }
            }
        }
        return array;
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

    private URI storefrontEndpoint(String shopDomain) {
        return URI.create("https://" + shopDomain + "/api/mcp");
    }

    private URI ucpEndpoint(String shopDomain) {
        return URI.create("https://" + shopDomain + "/api/ucp/mcp");
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
