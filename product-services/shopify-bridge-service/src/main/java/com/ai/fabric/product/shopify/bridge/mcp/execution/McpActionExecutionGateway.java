package com.ai.fabric.product.shopify.bridge.mcp.execution;

import com.ai.fabric.product.shopify.bridge.action.model.ShopifyBridgeActionExecuteRequest;
import com.ai.fabric.product.shopify.bridge.action.model.ShopifyBridgeActionResult;
import com.ai.fabric.product.shopify.bridge.config.McpExecutionGatewayProperties;
import com.ai.fabric.product.shopify.bridge.config.ShopifyMcpExternalAuthProperties;
import com.ai.fabric.product.shopify.bridge.customeraccount.service.ShopifyCustomerAccountOAuthService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class McpActionExecutionGateway {

    private static final Logger log = LoggerFactory.getLogger(McpActionExecutionGateway.class);
    private static final List<String> STOREFRONT_STANDARD_EXPECTED_TOOLS = List.of(
        "search_shop_policies_and_faqs"
    );
    private static final List<String> STOREFRONT_UCP_EXPECTED_TOOLS = List.of(
        "search_catalog",
        "lookup_catalog",
        "get_product",
        "get_cart",
        "update_cart",
        "create_cart",
        "cancel_cart"
    );
    private static final String STOREFRONT_UCP_PROFILE_REF = "SHOPIFY_BRIDGE_MCP_UCP_AGENT_PROFILE";
    private static final String SHOPIFY_GET_CART_ACTION_ID = "shopify_get_cart";
    private static final String SHOPIFY_CREATE_CART_ACTION_ID = "shopify_create_cart";
    private static final String SHOPIFY_UPDATE_CART_ACTION_ID = "shopify_update_cart";

    private final McpExecutionGatewayProperties properties;
    private final ShopifyMcpExternalAuthProperties externalAuthProperties;
    private final ShopifyCustomerAccountOAuthService customerAccountOAuthService;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    @Autowired
    public McpActionExecutionGateway(McpExecutionGatewayProperties properties,
                                     ShopifyMcpExternalAuthProperties externalAuthProperties,
                                     ShopifyCustomerAccountOAuthService customerAccountOAuthService,
                                     ObjectMapper objectMapper,
                                     RestClient.Builder restClientBuilder) {
        this(
            properties,
            externalAuthProperties,
            customerAccountOAuthService,
            objectMapper,
            gatewayRestClient(restClientBuilder, properties)
        );
    }

    McpActionExecutionGateway(McpExecutionGatewayProperties properties,
                              ShopifyMcpExternalAuthProperties externalAuthProperties,
                              ObjectMapper objectMapper,
                              RestClient.Builder restClientBuilder) {
        this(properties, externalAuthProperties, null, objectMapper, restClientBuilder.build());
    }

    McpActionExecutionGateway(McpExecutionGatewayProperties properties,
                              ObjectMapper objectMapper,
                              RestClient.Builder restClientBuilder) {
        this(properties, null, null, objectMapper, restClientBuilder.build());
    }

    McpActionExecutionGateway(McpExecutionGatewayProperties properties,
                              ShopifyMcpExternalAuthProperties externalAuthProperties,
                              ShopifyCustomerAccountOAuthService customerAccountOAuthService,
                              ObjectMapper objectMapper,
                              RestClient restClient) {
        this.properties = properties;
        this.externalAuthProperties = externalAuthProperties == null
            ? new ShopifyMcpExternalAuthProperties(false, false, "", "", "", "", List.of(), null, null, null, null, false, false)
            : externalAuthProperties;
        this.customerAccountOAuthService = customerAccountOAuthService;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
    }

    public boolean supports(ShopifyBridgeActionExecuteRequest request) {
        return findMcpExecution(request).isObject();
    }

    public ShopifyBridgeActionResult execute(String shopDomain, ShopifyBridgeActionExecuteRequest request) {
        if (!StringUtils.hasText(properties.baseUrl())) {
            return ShopifyBridgeActionResult.failure(
                "MCP_GATEWAY_NOT_CONFIGURED",
                "MCP execution gateway base URL is not configured."
            );
        }
        if (!StringUtils.hasText(properties.apiKey())) {
            return ShopifyBridgeActionResult.failure(
                "MCP_GATEWAY_NOT_CONFIGURED",
                "MCP execution gateway API key is not configured."
            );
        }
        JsonNode mcp = findMcpExecution(request);
        ShopifyBridgeActionResult externalAuthGate = externalAuthGate(shopDomain, request, mcp);
        if (externalAuthGate != null) {
            return externalAuthGate;
        }
        try {
            UcpCartAdaptation adaptation = adaptUcpCartRequest(shopDomain, request, mcp);
            if (adaptation.failure() != null) {
                return adaptation.failure();
            }
            ShopifyBridgeActionExecuteRequest effectiveRequest = adaptation.request();
            JsonNode response = invokeGateway(shopDomain, effectiveRequest);
            return toBridgeResult(response, effectiveRequest);
        } catch (RestClientResponseException ex) {
            return ShopifyBridgeActionResult.failure(
                "MCP_GATEWAY_REQUEST_FAILED",
                "MCP execution gateway returned HTTP " + ex.getStatusCode().value() + "."
            );
        } catch (Exception ex) {
            log.debug(
                "MCP execution gateway request failed for action {}: {}",
                request == null ? null : request.actionId(),
                ex.getMessage(),
                ex
            );
            return ShopifyBridgeActionResult.failure("MCP_GATEWAY_REQUEST_FAILED", "MCP execution gateway request failed.");
        }
    }

    private JsonNode invokeGateway(String shopDomain, ShopifyBridgeActionExecuteRequest request) {
        Map<String, Object> trace = new LinkedHashMap<>(request.trace() == null ? Map.of() : request.trace());
        trace.put("shopDomain", shopDomain);
        addCustomerAccessTokenIfPresent(trace, shopDomain, request);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("actionId", request.actionId());
        body.put("params", request.params() == null ? Map.of() : request.params());
        body.put("idempotencyKey", request.idempotencyKey());
        body.put("trace", trace);
        JsonNode actionConfig = objectMapper.valueToTree(trace.get("actionConfig"));
        if (actionConfig != null && actionConfig.isObject()) {
            body.put("actionConfig", objectMapper.convertValue(actionConfig, new TypeReference<Map<String, Object>>() {
            }));
        }
        return restClient.post()
            .uri(gatewayUrl(properties.executePath()))
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .header(properties.apiKeyHeader(), properties.apiKey())
            .body(body)
            .retrieve()
            .body(JsonNode.class);
    }

    private UcpCartAdaptation adaptUcpCartRequest(String shopDomain,
                                                   ShopifyBridgeActionExecuteRequest request,
                                                   JsonNode mcp) {
        if (request == null || !"UCP_CART".equals(normalized(text(mcp, "endpointKind")))) {
            return UcpCartAdaptation.success(request);
        }
        String actionId = request.actionId() == null ? "" : request.actionId().trim();
        if (!SHOPIFY_CREATE_CART_ACTION_ID.equals(actionId) && !SHOPIFY_UPDATE_CART_ACTION_ID.equals(actionId)) {
            return UcpCartAdaptation.success(request);
        }
        Map<String, Object> params = new LinkedHashMap<>(request.params() == null ? Map.of() : request.params());
        if (hasLineItems(params.get("line_items"))) {
            return UcpCartAdaptation.success(request);
        }
        if (SHOPIFY_CREATE_CART_ACTION_ID.equals(actionId)) {
            List<Map<String, Object>> lineItems = addedLineItems(params.get("add_items"));
            if (lineItems.isEmpty()) {
                return UcpCartAdaptation.failure(
                    "INVALID_REQUEST",
                    "Creating a Shopify cart requires at least one trusted product variant."
                );
            }
            params.put("line_items", lineItems);
            return UcpCartAdaptation.success(withParams(request, params));
        }

        String cartId = firstText(params, "cart_id", "cartId", "id");
        if (!StringUtils.hasText(cartId)) {
            return UcpCartAdaptation.failure(
                "INVALID_REQUEST",
                "Updating a Shopify cart requires a cart handle from trusted storefront context."
            );
        }
        if (!hasCartChanges(params)) {
            return UcpCartAdaptation.failure(
                "INVALID_REQUEST",
                "Updating a Shopify cart requires an addition, quantity update, or removal."
            );
        }
        CartState cartState = readCurrentUcpCart(shopDomain, cartId, request.trace());
        if (!cartState.available()) {
            return UcpCartAdaptation.failure("CART_STATE_READ_FAILED", cartState.message());
        }
        List<Map<String, Object>> lineItems = mergeCartChanges(
            cartState.lineItems(),
            params.get("add_items"),
            params.get("update_items"),
            params.get("remove_line_ids")
        );
        params.put("line_items", lineItems);
        return UcpCartAdaptation.success(withParams(request, params));
    }

    private CartState readCurrentUcpCart(String shopDomain,
                                         String cartId,
                                         Map<String, Object> sourceTrace) {
        Map<String, Object> trace = new LinkedHashMap<>(sourceTrace == null ? Map.of() : sourceTrace);
        trace.put("actionConfig", Map.of(
            "adapterType", "mcp-tool",
            "execution", Map.of(
                "adapterType", "mcp-tool",
                "mcp", Map.of(
                    "serverRef", "shopify-storefront-ucp",
                    "endpointKind", "UCP_CART",
                    "toolName", "get_cart",
                    "argumentTemplate", Map.of(
                        "meta", Map.of(
                            "ucp-agent", Map.of("profileRef", STOREFRONT_UCP_PROFILE_REF)
                        ),
                        "id", "{{params.cart_id}}"
                    )
                )
            )
        ));
        ShopifyBridgeActionExecuteRequest readRequest = new ShopifyBridgeActionExecuteRequest(
            SHOPIFY_GET_CART_ACTION_ID,
            Map.of("cart_id", cartId),
            null,
            trace
        );
        JsonNode response = invokeGateway(shopDomain, readRequest);
        ShopifyBridgeActionResult readResult = toBridgeResult(response, readRequest);
        if (!readResult.success()) {
            return CartState.unavailable(
                StringUtils.hasText(readResult.message())
                    ? readResult.message()
                    : "The current Shopify cart could not be read before applying the update."
            );
        }
        JsonNode cart = findCartNode(response == null ? MissingNode.getInstance() : response.path("data").path("toolResult"));
        if (!cart.isObject()) {
            return CartState.unavailable("The current Shopify cart response did not contain cart line items.");
        }
        JsonNode rawLineItems = firstArray(cart.path("line_items"), cart.path("lineItems"));
        List<Map<String, Object>> lineItems = new ArrayList<>();
        if (rawLineItems.isArray()) {
            for (JsonNode item : rawLineItems) {
                Map<String, Object> normalized = normalizeExistingLineItem(item);
                if (!normalized.isEmpty()) {
                    lineItems.add(normalized);
                }
            }
        }
        return CartState.available(lineItems);
    }

    private JsonNode findCartNode(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return MissingNode.getInstance();
        }
        if (node.isObject()) {
            if (node.path("line_items").isArray() || node.path("lineItems").isArray()) {
                return node;
            }
            JsonNode structured = node.path("structuredContent");
            JsonNode found = findCartNode(structured);
            if (found.isObject()) {
                return found;
            }
            JsonNode cart = node.path("cart");
            found = findCartNode(cart);
            if (found.isObject()) {
                return found;
            }
            JsonNode content = node.path("content");
            if (content.isArray()) {
                for (JsonNode item : content) {
                    String rawText = text(item, "text");
                    if (StringUtils.hasText(rawText)) {
                        try {
                            found = findCartNode(objectMapper.readTree(rawText));
                            if (found.isObject()) {
                                return found;
                            }
                        } catch (Exception ignored) {
                            // Non-JSON text is valid MCP content and is not cart state.
                        }
                    }
                }
            }
            var fields = node.fields();
            while (fields.hasNext()) {
                found = findCartNode(fields.next().getValue());
                if (found.isObject()) {
                    return found;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                JsonNode found = findCartNode(child);
                if (found.isObject()) {
                    return found;
                }
            }
        }
        return MissingNode.getInstance();
    }

    private Map<String, Object> normalizeExistingLineItem(JsonNode item) {
        if (item == null || !item.isObject()) {
            return Map.of();
        }
        String lineId = firstText(item, "id", "line_id", "lineId");
        String productVariantId = firstText(
            item.path("item"),
            "id",
            "product_variant_id",
            "productVariantId",
            "merchandiseId"
        );
        if (!StringUtils.hasText(productVariantId)) {
            productVariantId = firstText(item, "product_variant_id", "productVariantId", "merchandiseId");
        }
        Long quantity = positiveQuantity(item.path("quantity"));
        if (!StringUtils.hasText(productVariantId) || quantity == null) {
            return Map.of();
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        if (StringUtils.hasText(lineId)) {
            normalized.put("id", lineId);
        }
        normalized.put("item", Map.of("id", productVariantId));
        normalized.put("quantity", quantity);
        return normalized;
    }

    private List<Map<String, Object>> mergeCartChanges(List<Map<String, Object>> current,
                                                       Object rawAddItems,
                                                       Object rawUpdateItems,
                                                       Object rawRemoveLineIds) {
        List<Map<String, Object>> merged = new ArrayList<>();
        if (current != null) {
            current.forEach(item -> merged.add(new LinkedHashMap<>(item)));
        }
        Set<String> removeIds = textValues(rawRemoveLineIds);
        if (!removeIds.isEmpty()) {
            merged.removeIf(item -> removeIds.contains(firstText(item, "id", "line_id", "lineId")));
        }
        for (Map<String, Object> update : objectMaps(rawUpdateItems)) {
            String lineId = firstText(update, "line_id", "lineId", "id");
            Long quantity = nonNegativeQuantity(update.get("quantity"));
            if (!StringUtils.hasText(lineId) || quantity == null) {
                continue;
            }
            if (quantity == 0) {
                merged.removeIf(item -> lineId.equals(firstText(item, "id", "line_id", "lineId")));
                continue;
            }
            for (Map<String, Object> item : merged) {
                if (lineId.equals(firstText(item, "id", "line_id", "lineId"))) {
                    item.put("quantity", quantity);
                    break;
                }
            }
        }
        for (Map<String, Object> addition : addedLineItems(rawAddItems)) {
            String variantId = nestedText(addition, "item", "id");
            Long quantity = nonNegativeQuantity(addition.get("quantity"));
            if (!StringUtils.hasText(variantId) || quantity == null || quantity == 0) {
                continue;
            }
            Map<String, Object> existing = merged.stream()
                .filter(item -> variantId.equals(nestedText(item, "item", "id")))
                .findFirst()
                .orElse(null);
            if (existing == null) {
                merged.add(new LinkedHashMap<>(addition));
            } else {
                Long existingQuantity = nonNegativeQuantity(existing.get("quantity"));
                existing.put("quantity", (existingQuantity == null ? 0L : existingQuantity) + quantity);
            }
        }
        return List.copyOf(merged);
    }

    private List<Map<String, Object>> addedLineItems(Object rawItems) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> item : objectMaps(rawItems)) {
            String variantId = firstText(item, "product_variant_id", "productVariantId", "variant_id", "variantId");
            Long quantity = positiveQuantity(objectMapper.valueToTree(item.get("quantity")));
            if (!StringUtils.hasText(variantId) || quantity == null) {
                continue;
            }
            out.add(Map.of(
                "item", Map.of("id", variantId),
                "quantity", quantity
            ));
        }
        return List.copyOf(out);
    }

    private List<Map<String, Object>> objectMaps(Object raw) {
        if (!(raw instanceof Collection<?> values)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object value : values) {
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> normalized = new LinkedHashMap<>();
                map.forEach((key, item) -> {
                    if (key != null) {
                        normalized.put(key.toString(), item);
                    }
                });
                out.add(normalized);
            }
        }
        return out;
    }

    private Set<String> textValues(Object raw) {
        if (!(raw instanceof Collection<?> values)) {
            return Set.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (Object value : values) {
            if (value != null && StringUtils.hasText(value.toString())) {
                out.add(value.toString().trim());
            }
        }
        return Set.copyOf(out);
    }

    private boolean hasLineItems(Object raw) {
        return raw instanceof Collection<?> collection && !collection.isEmpty();
    }

    private boolean hasCartChanges(Map<String, Object> params) {
        return params != null && (
            hasLineItems(params.get("add_items"))
                || hasLineItems(params.get("update_items"))
                || hasLineItems(params.get("remove_line_ids"))
        );
    }

    private ShopifyBridgeActionExecuteRequest withParams(ShopifyBridgeActionExecuteRequest request,
                                                         Map<String, Object> params) {
        return new ShopifyBridgeActionExecuteRequest(
            request.actionId(),
            params,
            request.idempotencyKey(),
            request.trace()
        );
    }

    private Long positiveQuantity(JsonNode value) {
        Long quantity = value != null && value.isNumber()
            ? value.asLong()
            : value != null && value.isTextual() ? parseLong(value.asText()) : null;
        return quantity != null && quantity > 0 ? quantity : null;
    }

    private Long nonNegativeQuantity(Object value) {
        Long quantity = value instanceof Number number ? number.longValue() : parseLong(value);
        return quantity != null && quantity >= 0 ? quantity : null;
    }

    private Long parseLong(Object value) {
        if (value == null || !StringUtils.hasText(value.toString())) {
            return null;
        }
        try {
            return Long.parseLong(value.toString().trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String nestedText(Map<String, Object> values, String objectKey, String valueKey) {
        Object nested = values == null ? null : values.get(objectKey);
        return nested instanceof Map<?, ?> map ? firstText(map, valueKey) : null;
    }

    private String firstText(Map<?, ?> values, String... keys) {
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

    private String firstText(JsonNode values, String... keys) {
        if (values == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            String value = values.path(key).asText(null);
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private JsonNode firstArray(JsonNode... candidates) {
        if (candidates != null) {
            for (JsonNode candidate : candidates) {
                if (candidate != null && candidate.isArray()) {
                    return candidate;
                }
            }
        }
        return MissingNode.getInstance();
    }

    private record UcpCartAdaptation(
        ShopifyBridgeActionExecuteRequest request,
        ShopifyBridgeActionResult failure
    ) {
        static UcpCartAdaptation success(ShopifyBridgeActionExecuteRequest request) {
            return new UcpCartAdaptation(request, null);
        }

        static UcpCartAdaptation failure(String errorCode, String message) {
            return new UcpCartAdaptation(null, ShopifyBridgeActionResult.failure(errorCode, message));
        }
    }

    private record CartState(boolean available, List<Map<String, Object>> lineItems, String message) {
        static CartState available(List<Map<String, Object>> lineItems) {
            return new CartState(true, List.copyOf(lineItems == null ? List.of() : lineItems), null);
        }

        static CartState unavailable(String message) {
            return new CartState(false, List.of(), message);
        }
    }

    public Map<String, Object> storefrontReadiness(String shopDomain) {
        if (!StringUtils.hasText(properties.baseUrl())) {
            return readinessFailure("MCP_GATEWAY_NOT_CONFIGURED", "MCP execution gateway base URL is not configured.");
        }
        if (!StringUtils.hasText(properties.apiKey())) {
            return readinessFailure("MCP_GATEWAY_NOT_CONFIGURED", "MCP execution gateway API key is not configured.");
        }
        if (!StringUtils.hasText(shopDomain)) {
            return readinessFailure("INVALID_REQUEST", "shopDomain is required.");
        }
        String normalizedShopDomain = shopDomain.trim().toLowerCase();
        try {
            List<Map<String, Object>> serverSummaries = List.of(
                toolsReadiness(
                    "shopify-storefront",
                    "https://" + normalizedShopDomain + "/api/mcp",
                    normalizedShopDomain,
                    STOREFRONT_STANDARD_EXPECTED_TOOLS,
                    null
                ),
                toolsReadiness(
                    "shopify-storefront-ucp",
                    "https://" + normalizedShopDomain + "/api/ucp/mcp",
                    normalizedShopDomain,
                    STOREFRONT_UCP_EXPECTED_TOOLS,
                    STOREFRONT_UCP_PROFILE_REF
                )
            );
            boolean ready = serverSummaries.stream().allMatch(summary -> Boolean.TRUE.equals(summary.get("ready")));
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("ready", ready);
            out.put("message", ready
                ? "MCP gateway tools/list readiness passed."
                : "MCP gateway tools/list readiness found missing tools.");
            out.put("servers", serverSummaries);
            return out;
        } catch (RestClientResponseException ex) {
            return readinessFailure(
                "MCP_GATEWAY_REQUEST_FAILED",
                "MCP execution gateway returned HTTP " + ex.getStatusCode().value() + "."
            );
        } catch (Exception ex) {
            return readinessFailure("MCP_GATEWAY_REQUEST_FAILED", "MCP execution gateway readiness request failed.");
        }
    }

    private Map<String, Object> toolsReadiness(String serverRef,
                                               String endpoint,
                                               String shopDomain,
                                               List<String> expectedTools,
                                               String profileRef) {
        Map<String, Object> server = new LinkedHashMap<>();
        server.put("transport", "STREAMABLE_HTTP");
        server.put("endpointUrl", endpoint);
        server.put("auth", Map.of("mode", "NONE"));
        if (StringUtils.hasText(profileRef)) {
            server.put("toolsListArguments", Map.of(
                "meta", Map.of("ucp-agent", Map.of("profileRef", profileRef))
            ));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("serverRef", serverRef);
        body.put("server", server);
        body.put("trace", Map.of("shopDomain", shopDomain));
        JsonNode response = restClient.post()
            .uri(gatewayUrl("/api/internal/mcp/servers/tools/list"))
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .header(properties.apiKeyHeader(), properties.apiKey())
            .body(body)
            .retrieve()
            .body(JsonNode.class);
        boolean success = response != null && response.path("success").asBoolean(false);
        JsonNode tools = response == null ? MissingNode.getInstance() : response.path("result");
        List<String> presentTools = toolNames(tools);
        List<String> missingTools = expectedTools.stream()
            .filter(expected -> presentTools.stream().noneMatch(present -> present.equalsIgnoreCase(expected)))
            .toList();
        boolean ready = success && missingTools.isEmpty();
        Map<String, Object> serverSummary = new LinkedHashMap<>();
        serverSummary.put("serverRef", serverRef);
        serverSummary.put("endpointUrl", endpoint);
        serverSummary.put("ready", ready);
        serverSummary.put("expectedTools", expectedTools);
        serverSummary.put("presentTools", presentTools);
        serverSummary.put("missingTools", missingTools);
        return serverSummary;
    }

    private ShopifyBridgeActionResult toBridgeResult(JsonNode response, ShopifyBridgeActionExecuteRequest request) {
        if (response == null || !response.isObject()) {
            return ShopifyBridgeActionResult.failure("MCP_GATEWAY_REQUEST_FAILED", "MCP execution gateway returned an invalid response.");
        }
        boolean success = response.path("success").asBoolean(false);
        String gatewayMessage = text(response, "message");
        String toolMessage = mcpToolTextContent(response.path("data").path("toolResult"));
        String message = isGenericMcpToolResult(gatewayMessage) && StringUtils.hasText(toolMessage)
            ? toolMessage
            : firstNonBlank(gatewayMessage, toolMessage);
        String errorCode = text(response, "errorCode");
        Map<String, Object> data = response.path("data").isObject()
            ? objectMapper.convertValue(response.path("data"), new TypeReference<>() {
            })
            : new LinkedHashMap<>();
        if (success && mcpToolReportedError(response.path("data").path("toolResult"))) {
            return ShopifyBridgeActionResult.failure(
                mcpToolFailureErrorCode(request),
                StringUtils.hasText(message) ? message : "MCP tool reported an error."
            );
        }
        return success
            ? ShopifyBridgeActionResult.ok(StringUtils.hasText(message) ? message : "MCP tool result", data)
            : ShopifyBridgeActionResult.failure(
                StringUtils.hasText(errorCode) ? errorCode : "MCP_EXECUTION_FAILED",
                StringUtils.hasText(message) ? message : "MCP execution failed."
            );
    }

    private boolean mcpToolReportedError(JsonNode toolResult) {
        return toolResult != null && toolResult.path("isError").asBoolean(false);
    }

    private String mcpToolFailureErrorCode(ShopifyBridgeActionExecuteRequest request) {
        JsonNode mcp = findMcpExecution(request);
        if (!isCustomerAccountMcp(mcp)) {
            return "MCP_TOOL_REPORTED_ERROR";
        }
        return isReadOnlyCustomerAccountTool(mcp) ? "OWNED_RESOURCE_NOT_FOUND" : "OWNED_RESOURCE_ACTION_FAILED";
    }

    private boolean isReadOnlyCustomerAccountTool(JsonNode mcp) {
        String toolName = text(mcp, "toolName");
        return List.of(
            "get_most_recent_order_status",
            "get_order_status",
            "get_store_credit_balances"
        ).contains(toolName == null ? "" : toolName.trim());
    }

    private String mcpToolTextContent(JsonNode toolResult) {
        JsonNode content = toolResult == null ? MissingNode.getInstance() : toolResult.path("content");
        if (!content.isArray()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (JsonNode item : content) {
            String type = text(item, "type");
            String text = text(item, "text");
            if ("text".equalsIgnoreCase(type) && StringUtils.hasText(text)) {
                parts.add(text.trim());
            }
        }
        return parts.isEmpty() ? null : String.join("\n", parts);
    }

    private boolean isGenericMcpToolResult(String value) {
        return "MCP tool result".equalsIgnoreCase(value == null ? "" : value.trim());
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private List<String> toolNames(JsonNode tools) {
        if (tools == null || !tools.isArray()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (JsonNode tool : tools) {
            String name = text(tool, "name");
            if (StringUtils.hasText(name)) {
                out.add(name);
            }
        }
        return List.copyOf(out);
    }

    private Map<String, Object> readinessFailure(String errorCode, String message) {
        return Map.of(
            "ready", false,
            "errorCode", errorCode,
            "message", message,
            "servers", List.of()
        );
    }

    private String gatewayUrl(String requestPath) {
        String base = properties.baseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String path = StringUtils.hasText(requestPath)
            ? requestPath
            : "/api/internal/mcp/actions/execute";
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return base + path;
    }

    private static RestClient gatewayRestClient(RestClient.Builder restClientBuilder,
                                                McpExecutionGatewayProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        return restClientBuilder
            .requestFactory(requestFactory)
            .build();
    }

    private JsonNode findMcpExecution(ShopifyBridgeActionExecuteRequest request) {
        JsonNode trace = objectMapper.valueToTree(request == null || request.trace() == null ? Map.of() : request.trace());
        for (JsonNode candidate : List.of(
            trace.path("execution").path("mcp"),
            trace.path("actionConfig").path("execution").path("mcp"),
            trace.path("action").path("execution").path("mcp"),
            trace.path("mcp")
        )) {
            if (candidate.isObject()) {
                return candidate;
            }
        }
        return MissingNode.getInstance();
    }

    private ShopifyBridgeActionResult externalAuthGate(String shopDomain, ShopifyBridgeActionExecuteRequest request, JsonNode mcp) {
        if (!mcp.isObject()) {
            return null;
        }
        if (isCustomerAccountMcp(mcp)) {
            if (!externalAuthProperties.customerAccountConfigured()) {
                return ShopifyBridgeActionResult.failure(
                    "CUSTOMER_ACCOUNT_MCP_NOT_CONFIGURED",
                    "Customer Account MCP OAuth/PKCE and protected customer data posture are not configured."
                );
            }
            String customerToken;
            try {
                customerToken = resolveBoundCustomerAccessToken(shopDomain, request);
            } catch (ResponseStatusException ex) {
                return ShopifyBridgeActionResult.failure(
                    "INVALID_REQUEST",
                    StringUtils.hasText(ex.getReason()) ? ex.getReason() : "Invalid Customer Account MCP shopper session."
                );
            }
            if (!StringUtils.hasText(customerToken)) {
                return ShopifyBridgeActionResult.failure(
                    "CUSTOMER_ACCOUNT_AUTH_REQUIRED",
                    "Customer Account MCP requires a bound customer session token."
                );
            }
        }
        if (isCheckoutMcp(mcp)) {
            if (!externalAuthProperties.checkoutMcpEnabled()) {
                return ShopifyBridgeActionResult.failure(
                    "CHECKOUT_MCP_NOT_CONFIGURED",
                    "Checkout MCP client credentials are not configured."
                );
            }
            if (mcp.path("requiresTerminalCheckoutEnablement").asBoolean(false)
                && !externalAuthProperties.checkoutMcpTerminalOperationsEnabled()) {
                return ShopifyBridgeActionResult.failure(
                    "CHECKOUT_TERMINAL_OPERATION_DISABLED",
                    "Terminal checkout MCP operations are disabled."
                );
            }
            if (mcp.path("requiresIdempotencyKey").asBoolean(false)
                && !StringUtils.hasText(request == null ? null : request.idempotencyKey())) {
                return ShopifyBridgeActionResult.failure(
                    "INVALID_REQUEST",
                    "Checkout MCP terminal actions require an idempotency key."
                );
            }
        }
        return null;
    }

    private boolean isCustomerAccountMcp(JsonNode mcp) {
        String endpointKind = normalized(text(mcp, "endpointKind"));
        String authMode = normalized(text(mcp, "authMode"));
        String serverRef = normalized(text(mcp, "serverRef"));
        return endpointKind.contains("CUSTOMER_ACCOUNT")
            || "CUSTOMER_OAUTH_PKCE".equals(authMode)
            || serverRef.contains("CUSTOMER_ACCOUNT");
    }

    private boolean isCheckoutMcp(JsonNode mcp) {
        String endpointKind = normalized(text(mcp, "endpointKind"));
        String authMode = normalized(text(mcp, "authMode"));
        String serverRef = normalized(text(mcp, "serverRef"));
        return endpointKind.contains("CHECKOUT")
            || "SHOPIFY_AGENTIC_CLIENT_CREDENTIALS".equals(authMode)
            || serverRef.contains("CHECKOUT");
    }

    private void addCustomerAccessTokenIfPresent(Map<String, Object> trace,
                                                 String shopDomain,
                                                 ShopifyBridgeActionExecuteRequest request) {
        if (!isCustomerAccountMcp(findMcpExecution(request))) {
            return;
        }
        String token = resolveBoundCustomerAccessToken(shopDomain, request);
        if (StringUtils.hasText(token)) {
            trace.put("mcpCustomerAccessToken", token);
        }
    }

    private String resolveBoundCustomerAccessToken(String shopDomain, ShopifyBridgeActionExecuteRequest request) {
        if (request == null) {
            return null;
        }
        if (customerAccountOAuthService != null) {
            String shopperSessionId = shopperSessionId(request);
            if (StringUtils.hasText(shopDomain) && StringUtils.hasText(shopperSessionId)) {
                return customerAccountOAuthService.resolveAccessToken(shopDomain, shopperSessionId).orElse(null);
            }
        }
        return null;
    }

    private String shopperSessionId(ShopifyBridgeActionExecuteRequest request) {
        for (String key : List.of("shopperSessionId", "shopper_session_id", "sessionId")) {
            String value = textValue(request.trace(), key);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        Object authContext = request.trace() == null ? null : request.trace().get("authContext");
        if (authContext instanceof Map<?, ?> authMap) {
            Object value = authMap.get("sessionId");
            if (value != null && StringUtils.hasText(value.toString())) {
                return value.toString().trim();
            }
        }
        for (String key : List.of("shopperSessionId", "shopper_session_id", "sessionId")) {
            String value = textValue(request.params(), key);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String textValue(Map<String, Object> values, String key) {
        if (values == null || !values.containsKey(key)) {
            return null;
        }
        Object value = values.get(key);
        String text = value == null ? null : value.toString();
        return StringUtils.hasText(text) ? text.trim() : null;
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim().replace('-', '_').toUpperCase(java.util.Locale.ROOT);
    }

    private String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        String value = node.path(field).asText(null);
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
