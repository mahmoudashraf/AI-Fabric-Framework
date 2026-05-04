package com.ai.fabric.product.shopify.bridge.mcp.execution;

import com.ai.fabric.product.shopify.bridge.action.model.ShopifyBridgeActionExecuteRequest;
import com.ai.fabric.product.shopify.bridge.action.model.ShopifyBridgeActionResult;
import com.ai.fabric.product.shopify.bridge.mcp.client.McpStreamableHttpClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class McpActionExecutionGateway {

    private static final Pattern EXACT_TEMPLATE = Pattern.compile("^\\{\\{\\s*([^{}]+?)\\s*}}$");
    private static final Pattern TEMPLATE = Pattern.compile("\\{\\{\\s*([^{}]+?)\\s*}}");
    private static final Set<String> ALLOWED_API_KEY_HEADERS = Set.of(
        "X-API-KEY",
        "X-MCP-API-KEY",
        "X-LOOM-MCP-KEY"
    );
    private static final Set<String> BLOCKED_HEADER_NAMES = Set.of(
        "AUTHORIZATION",
        "COOKIE",
        "SET-COOKIE",
        "HOST",
        "ORIGIN",
        "REFERER",
        "X-FORWARDED-FOR",
        "X-FORWARDED-HOST",
        "X-FORWARDED-PROTO",
        "X-BRIDGE-API-KEY",
        "X-PLATFORM-API-KEY",
        "X-PLATFORM-PUBLIC-API-KEY"
    );

    private final McpStreamableHttpClient mcpClient;
    private final ObjectMapper objectMapper;

    public McpActionExecutionGateway(McpStreamableHttpClient mcpClient, ObjectMapper objectMapper) {
        this.mcpClient = mcpClient;
        this.objectMapper = objectMapper;
    }

    public boolean supports(ShopifyBridgeActionExecuteRequest request) {
        return findMcpExecution(request).isObject();
    }

    public ShopifyBridgeActionResult execute(String shopDomain, ShopifyBridgeActionExecuteRequest request) {
        try {
            JsonNode trace = objectMapper.valueToTree(request.trace() == null ? Map.of() : request.trace());
            JsonNode mcp = findMcpExecution(request, trace);
            if (!mcp.isObject()) {
                return ShopifyBridgeActionResult.failure("ACTION_NOT_SUPPORTED", "Action is not supported.");
            }

            String serverRef = text(mcp, "serverRef");
            String toolName = text(mcp, "toolName");
            if (!StringUtils.hasText(serverRef) || !StringUtils.hasText(toolName)) {
                return ShopifyBridgeActionResult.failure(
                    "INVALID_MCP_ACTION_CONFIG",
                    "MCP action config requires execution.mcp.serverRef and execution.mcp.toolName."
                );
            }

            JsonNode serverBinding = findServerBinding(trace, serverRef);
            URI endpoint = resolveEndpoint(shopDomain, request, trace, mcp, serverBinding);
            JsonNode arguments = renderArguments(shopDomain, request, trace, mcp.path("argumentTemplate"));
            Map<String, String> headers = resolveAuthHeaders(trace, firstObject(mcp.path("auth"), serverBinding.path("auth")));

            JsonNode result = mcpClient.toolsCall(
                endpoint,
                toolName,
                arguments,
                McpStreamableHttpClient.McpRequestOptions.withHeaders(headers)
            );
            return normalizeResult(serverRef, toolName, mcp, result);
        } catch (IllegalArgumentException ex) {
            return ShopifyBridgeActionResult.failure("INVALID_MCP_ACTION_CONFIG", ex.getMessage());
        } catch (ResponseStatusException ex) {
            String message = StringUtils.hasText(ex.getReason()) ? ex.getReason() : "MCP execution failed.";
            return ShopifyBridgeActionResult.failure("MCP_EXECUTION_FAILED", message);
        } catch (Exception ex) {
            return ShopifyBridgeActionResult.failure("MCP_EXECUTION_FAILED", "MCP execution failed.");
        }
    }

    private ShopifyBridgeActionResult normalizeResult(String serverRef, String toolName, JsonNode mcp, JsonNode result) {
        ObjectNode data = objectMapper.createObjectNode();
        data.put("adapterType", "mcp-tool");
        data.put("mcpServerRef", serverRef);
        data.put("mcpToolName", toolName);
        data.put("evidenceType", "MCP_TOOL_RESULT");
        copyText(mcp, data, "toolSchemaHash");
        copyText(mcp, data, "schemaDriftPolicy");
        data.set("toolResult", result == null ? objectMapper.createObjectNode() : result.deepCopy());

        JsonNode responseMapping = mcp.path("responseMapping");
        if (responseMapping.isObject()) {
            ObjectNode mapped = objectMapper.createObjectNode();
            mapPath(responseMapping, result, mapped, "resultPath", "mappedResult");
            mapPath(responseMapping, result, mapped, "contentPath", "content");
            mapPath(responseMapping, result, mapped, "structuredContentPath", "structuredContent");
            mapPath(responseMapping, result, mapped, "citationsPath", "citations");
            mapPath(responseMapping, result, mapped, "resourceLinksPath", "resourceLinks");
            mapPath(responseMapping, result, mapped, "pinnedTargetsPath", "pinnedTargets");
            if (!mapped.isEmpty()) {
                data.set("normalizedEvidence", mapped);
            }
        }

        Map<String, Object> normalized = objectMapper.convertValue(data, new TypeReference<>() {
        });
        return ShopifyBridgeActionResult.ok("MCP tool result", normalized);
    }

    private void mapPath(JsonNode mapping, JsonNode source, ObjectNode target, String pathField, String targetField) {
        String path = text(mapping, pathField);
        if (!StringUtils.hasText(path)) {
            return;
        }
        JsonNode value = readRestrictedJsonPath(source, path);
        if (!value.isMissingNode() && !value.isNull()) {
            target.set(targetField, value.deepCopy());
        }
    }

    private JsonNode findMcpExecution(ShopifyBridgeActionExecuteRequest request) {
        JsonNode trace = objectMapper.valueToTree(request == null || request.trace() == null ? Map.of() : request.trace());
        return findMcpExecution(request, trace);
    }

    private JsonNode findMcpExecution(ShopifyBridgeActionExecuteRequest request, JsonNode trace) {
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

    private JsonNode findServerBinding(JsonNode trace, String serverRef) {
        for (JsonNode container : List.of(
            trace.path("mcpServers"),
            trace.path("actionConfig").path("mcpServers"),
            trace.path("action").path("mcpServers")
        )) {
            JsonNode found = findServerBindingInContainer(container, serverRef);
            if (found.isObject()) {
                return found;
            }
        }
        JsonNode single = firstObject(trace.path("mcpServer"), trace.path("actionConfig").path("mcpServer"));
        if (single.isObject()) {
            String ref = text(single, "serverRef", "id");
            if (!StringUtils.hasText(ref) || ref.equals(serverRef)) {
                return single;
            }
        }
        return objectMapper.createObjectNode();
    }

    private JsonNode findServerBindingInContainer(JsonNode container, String serverRef) {
        if (container.isObject()) {
            JsonNode byRef = container.path(serverRef);
            if (byRef.isObject()) {
                return byRef;
            }
            JsonNode server = container.path("servers").path(serverRef);
            if (server.isObject()) {
                return server;
            }
        }
        if (container.isArray()) {
            for (JsonNode entry : container) {
                if (!entry.isObject()) {
                    continue;
                }
                String ref = text(entry, "serverRef", "id");
                if (serverRef.equals(ref)) {
                    return entry;
                }
            }
        }
        return MissingNode.getInstance();
    }

    private URI resolveEndpoint(String shopDomain,
                                ShopifyBridgeActionExecuteRequest request,
                                JsonNode trace,
                                JsonNode mcp,
                                JsonNode serverBinding) {
        String endpoint = firstText(
            mcp,
            serverBinding,
            List.of("endpointUrl", "endpoint", "url", "discoveryUrl")
        );
        if (!StringUtils.hasText(endpoint)) {
            String template = firstText(
                mcp,
                serverBinding,
                List.of("endpointUrlTemplate", "urlTemplate", "discoveryUrlTemplate")
            );
            if (StringUtils.hasText(template)) {
                endpoint = renderEndpointTemplate(template, shopDomain, request, trace);
            }
        }
        if (!StringUtils.hasText(endpoint)) {
            throw new IllegalArgumentException("MCP server endpoint is required.");
        }
        URI uri = URI.create(endpoint.trim());
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"https".equals(scheme) && !"http".equals(scheme)) {
            throw new IllegalArgumentException("MCP server endpoint must be http or https.");
        }
        return uri;
    }

    private String renderEndpointTemplate(String template,
                                          String shopDomain,
                                          ShopifyBridgeActionExecuteRequest request,
                                          JsonNode trace) {
        Matcher matcher = TEMPLATE.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            JsonNode value = resolveTemplateValue(matcher.group(1), shopDomain, request, trace, false);
            if (value.isMissingNode() || value.isNull()) {
                throw new IllegalArgumentException("MCP endpoint template has an unresolved placeholder.");
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(value.asText()));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private JsonNode renderArguments(String shopDomain,
                                     ShopifyBridgeActionExecuteRequest request,
                                     JsonNode trace,
                                     JsonNode argumentTemplate) {
        JsonNode params = objectMapper.valueToTree(request.params() == null ? Map.of() : request.params());
        if (argumentTemplate == null || argumentTemplate.isMissingNode() || argumentTemplate.isNull()) {
            return params.isObject() ? params : objectMapper.createObjectNode();
        }
        if (!argumentTemplate.isObject()) {
            throw new IllegalArgumentException("MCP argumentTemplate must be an object.");
        }
        return renderTemplateNode(argumentTemplate, shopDomain, request, trace);
    }

    private JsonNode renderTemplateNode(JsonNode node,
                                        String shopDomain,
                                        ShopifyBridgeActionExecuteRequest request,
                                        JsonNode trace) {
        if (node.isObject()) {
            ObjectNode rendered = objectMapper.createObjectNode();
            node.fields().forEachRemaining(entry ->
                rendered.set(entry.getKey(), renderTemplateNode(entry.getValue(), shopDomain, request, trace)));
            return rendered;
        }
        if (node.isArray()) {
            ArrayNode rendered = objectMapper.createArrayNode();
            for (JsonNode child : node) {
                rendered.add(renderTemplateNode(child, shopDomain, request, trace));
            }
            return rendered;
        }
        if (!node.isTextual()) {
            return node.deepCopy();
        }
        String value = node.asText();
        Matcher exact = EXACT_TEMPLATE.matcher(value);
        if (exact.matches()) {
            JsonNode resolved = resolveTemplateValue(exact.group(1), shopDomain, request, trace, true);
            return resolved.isMissingNode() ? objectMapper.getNodeFactory().nullNode() : resolved.deepCopy();
        }
        Matcher matcher = TEMPLATE.matcher(value);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            JsonNode resolved = resolveTemplateValue(matcher.group(1), shopDomain, request, trace, true);
            matcher.appendReplacement(out, Matcher.quoteReplacement(resolved.isMissingNode() || resolved.isNull()
                ? ""
                : resolved.asText()));
        }
        matcher.appendTail(out);
        return objectMapper.getNodeFactory().textNode(out.toString());
    }

    private JsonNode resolveTemplateValue(String expression,
                                          String shopDomain,
                                          ShopifyBridgeActionExecuteRequest request,
                                          JsonNode trace,
                                          boolean allowParams) {
        String key = expression == null ? "" : expression.trim();
        if ("shopDomain".equals(key)) {
            return objectMapper.getNodeFactory().textNode(shopDomain == null ? "" : shopDomain);
        }
        if ("actionId".equals(key)) {
            return objectMapper.getNodeFactory().textNode(request == null || request.actionId() == null ? "" : request.actionId());
        }
        if ("idempotencyKey".equals(key)) {
            return objectMapper.getNodeFactory().textNode(request == null || request.idempotencyKey() == null ? "" : request.idempotencyKey());
        }
        if (allowParams && key.startsWith("params.")) {
            JsonNode params = objectMapper.valueToTree(request == null || request.params() == null ? Map.of() : request.params());
            return readDottedPath(params, key.substring("params.".length()));
        }
        if (key.startsWith("trace.")) {
            return readDottedPath(trace, key.substring("trace.".length()));
        }
        if (key.startsWith("config.")) {
            return readDottedPath(trace.path("config"), key.substring("config.".length()));
        }
        if (key.startsWith("install.")) {
            return readDottedPath(trace.path("install"), key.substring("install.".length()));
        }
        return MissingNode.getInstance();
    }

    private Map<String, String> resolveAuthHeaders(JsonNode trace, JsonNode auth) {
        if (!auth.isObject()) {
            return Map.of();
        }
        String mode = normalizedEnum(text(auth, "mode", "authMode"));
        if (!StringUtils.hasText(mode) || "NONE".equals(mode)) {
            return Map.of();
        }
        if ("BEARER_TOKEN_SECRET_REF".equals(mode) || "STATIC_BEARER_SECRET".equals(mode)) {
            String secretRef = text(auth, "tokenSecretRef", "secretRef");
            String secretValue = resolveSecretValue(trace, secretRef);
            if (!StringUtils.hasText(secretValue)) {
                throw new IllegalArgumentException("MCP bearer auth secret is not available.");
            }
            String bearer = secretValue.trim().regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())
                ? secretValue.trim()
                : "Bearer " + secretValue.trim();
            return Map.of(HttpHeaders.AUTHORIZATION, bearer);
        }
        if ("API_KEY_HEADER_SECRET".equals(mode)) {
            String headerName = text(auth, "headerName");
            validateApiKeyHeader(headerName);
            String secretRef = text(auth, "secretRef", "valueSecretRef");
            String secretValue = resolveSecretValue(trace, secretRef);
            if (!StringUtils.hasText(secretValue)) {
                throw new IllegalArgumentException("MCP API key auth secret is not available.");
            }
            return Map.of(headerName.trim(), secretValue.trim());
        }
        throw new IllegalArgumentException("MCP auth mode is not supported by the generic execution gateway: " + mode);
    }

    private void validateApiKeyHeader(String headerName) {
        String normalized = headerName == null ? "" : headerName.trim().toUpperCase(Locale.ROOT);
        if (BLOCKED_HEADER_NAMES.contains(normalized) || !ALLOWED_API_KEY_HEADERS.contains(normalized)) {
            throw new IllegalArgumentException("MCP API key headerName is not allowlisted.");
        }
    }

    private String resolveSecretValue(JsonNode trace, String secretRef) {
        if (!StringUtils.hasText(secretRef)) {
            return null;
        }
        for (JsonNode container : List.of(
            trace.path("mcpSecretValues"),
            trace.path("secretValues"),
            trace.path("resolvedSecrets")
        )) {
            String value = container.path(secretRef).asText("").trim();
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return System.getenv(secretRef);
    }

    private JsonNode readRestrictedJsonPath(JsonNode source, String path) {
        if (source == null || !StringUtils.hasText(path) || !path.startsWith("$")) {
            return MissingNode.getInstance();
        }
        JsonNode current = source;
        int index = 1;
        while (index < path.length()) {
            char ch = path.charAt(index);
            if (ch == '.') {
                int start = ++index;
                while (index < path.length() && path.charAt(index) != '.' && path.charAt(index) != '[') {
                    index++;
                }
                if (start == index) {
                    return MissingNode.getInstance();
                }
                current = current.path(path.substring(start, index));
            } else if (ch == '[') {
                int end = path.indexOf(']', index);
                if (end < 0) {
                    return MissingNode.getInstance();
                }
                try {
                    current = current.path(Integer.parseInt(path.substring(index + 1, end)));
                } catch (NumberFormatException ex) {
                    return MissingNode.getInstance();
                }
                index = end + 1;
            } else {
                return MissingNode.getInstance();
            }
        }
        return current;
    }

    private JsonNode readDottedPath(JsonNode source, String dottedPath) {
        if (source == null || !StringUtils.hasText(dottedPath)) {
            return MissingNode.getInstance();
        }
        JsonNode current = source;
        for (String segment : dottedPath.split("\\.")) {
            if (!StringUtils.hasText(segment)) {
                return MissingNode.getInstance();
            }
            current = current.path(segment.trim());
        }
        return current;
    }

    private JsonNode firstObject(JsonNode left, JsonNode right) {
        if (left != null && left.isObject()) {
            return left;
        }
        return right != null && right.isObject() ? right : objectMapper.createObjectNode();
    }

    private String firstText(JsonNode first, JsonNode second, List<String> fieldNames) {
        for (String fieldName : fieldNames) {
            String value = text(first, fieldName);
            if (StringUtils.hasText(value)) {
                return value;
            }
            value = text(second, fieldName);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private void copyText(JsonNode source, ObjectNode target, String field) {
        String value = text(source, field);
        if (StringUtils.hasText(value)) {
            target.put(field, value);
        }
    }

    private String text(JsonNode node, String... fields) {
        if (node == null) {
            return null;
        }
        for (String field : fields) {
            String value = node.path(field).asText("").trim();
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String normalizedEnum(String value) {
        return value == null ? "" : value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
    }
}
