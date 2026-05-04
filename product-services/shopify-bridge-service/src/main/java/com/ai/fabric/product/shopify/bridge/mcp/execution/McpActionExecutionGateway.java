package com.ai.fabric.product.shopify.bridge.mcp.execution;

import com.ai.fabric.product.shopify.bridge.action.model.ShopifyBridgeActionExecuteRequest;
import com.ai.fabric.product.shopify.bridge.action.model.ShopifyBridgeActionResult;
import com.ai.fabric.product.shopify.bridge.config.McpExecutionGatewayProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class McpActionExecutionGateway {

    private final McpExecutionGatewayProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public McpActionExecutionGateway(McpExecutionGatewayProperties properties,
                                     ObjectMapper objectMapper,
                                     RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder.build();
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
        try {
            Map<String, Object> trace = new LinkedHashMap<>(request.trace() == null ? Map.of() : request.trace());
            trace.put("shopDomain", shopDomain);
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
            JsonNode response = restClient.post()
                .uri(executeUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .header(properties.apiKeyHeader(), properties.apiKey())
                .body(body)
                .retrieve()
                .body(JsonNode.class);
            return toBridgeResult(response);
        } catch (RestClientResponseException ex) {
            return ShopifyBridgeActionResult.failure(
                "MCP_GATEWAY_REQUEST_FAILED",
                "MCP execution gateway returned HTTP " + ex.getStatusCode().value() + "."
            );
        } catch (Exception ex) {
            return ShopifyBridgeActionResult.failure("MCP_GATEWAY_REQUEST_FAILED", "MCP execution gateway request failed.");
        }
    }

    private ShopifyBridgeActionResult toBridgeResult(JsonNode response) {
        if (response == null || !response.isObject()) {
            return ShopifyBridgeActionResult.failure("MCP_GATEWAY_REQUEST_FAILED", "MCP execution gateway returned an invalid response.");
        }
        boolean success = response.path("success").asBoolean(false);
        String message = text(response, "message");
        String errorCode = text(response, "errorCode");
        Map<String, Object> data = response.path("data").isObject()
            ? objectMapper.convertValue(response.path("data"), new TypeReference<>() {
            })
            : new LinkedHashMap<>();
        return success
            ? ShopifyBridgeActionResult.ok(StringUtils.hasText(message) ? message : "MCP tool result", data)
            : ShopifyBridgeActionResult.failure(
                StringUtils.hasText(errorCode) ? errorCode : "MCP_EXECUTION_FAILED",
                StringUtils.hasText(message) ? message : "MCP execution failed."
            );
    }

    private String executeUrl() {
        String base = properties.baseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String path = StringUtils.hasText(properties.executePath())
            ? properties.executePath()
            : "/api/internal/mcp/actions/execute";
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return base + path;
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

    private String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        String value = node.path(field).asText(null);
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
