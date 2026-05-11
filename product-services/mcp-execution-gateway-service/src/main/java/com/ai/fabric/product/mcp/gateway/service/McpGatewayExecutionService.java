package com.ai.fabric.product.mcp.gateway.service;

import com.ai.fabric.product.mcp.gateway.client.McpStreamableHttpClient;
import com.ai.fabric.product.mcp.gateway.config.McpGatewayProperties;
import com.ai.fabric.product.mcp.gateway.model.McpGatewayContracts.ActionExecuteRequest;
import com.ai.fabric.product.mcp.gateway.model.McpGatewayContracts.ActionExecuteResponse;
import com.ai.fabric.product.mcp.gateway.model.McpGatewayContracts.DiscoveryRequest;
import com.ai.fabric.product.mcp.gateway.model.McpGatewayContracts.DiscoveryResponse;
import com.ai.fabric.product.mcp.gateway.model.McpGatewayContracts.ExpectedTool;
import com.ai.fabric.product.mcp.gateway.model.McpGatewayContracts.McpToolSummary;
import com.ai.fabric.product.mcp.gateway.model.McpGatewayContracts.ServerRequest;
import com.ai.fabric.product.mcp.gateway.model.McpGatewayContracts.ServerVerificationRequest;
import com.ai.fabric.product.mcp.gateway.model.McpGatewayContracts.ServerVerificationResponse;
import com.ai.fabric.product.mcp.gateway.model.McpGatewayContracts.ToolsCallRequest;
import com.ai.fabric.product.mcp.gateway.model.McpGatewayContracts.ToolsResultResponse;
import com.ai.fabric.product.mcp.gateway.model.McpGatewayContracts.ToolVerificationResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;

@Service
public class McpGatewayExecutionService {

    private static final Pattern EXACT_TEMPLATE = Pattern.compile("^\\{\\{\\s*([^{}]+?)\\s*}}$");
    private static final Pattern TEMPLATE = Pattern.compile("\\{\\{\\s*([^{}]+?)\\s*}}");
    private static final Pattern SAFE_SECRET_REF = Pattern.compile("[A-Z][A-Z0-9_]{1,127}");
    private static final Pattern IPV4_LITERAL = Pattern.compile(
        "^(25[0-5]|2[0-4]\\d|1?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|1?\\d?\\d)){3}$"
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
        "X-PLATFORM-PUBLIC-API-KEY",
        "X-MCP-GATEWAY-API-KEY"
    );
    private static final Set<String> CANONICAL_HASH_IGNORED_FIELDS = Set.of(
        "description",
        "title",
        "examples",
        "default",
        "annotations",
        "$comment"
    );
    private static final String SHOPIFY_CHECKOUT_TOKEN_URL = "https://api.shopify.com/auth/access_token";
    private static final String SHOPIFY_CHECKOUT_CLIENT_ID_SECRET_REF = "MCP_SECRET_SHOPIFY_CHECKOUT_MCP_CLIENT_ID";
    private static final String SHOPIFY_CHECKOUT_CLIENT_SECRET_SECRET_REF = "MCP_SECRET_SHOPIFY_CHECKOUT_MCP_CLIENT_SECRET";

    private final McpStreamableHttpClient mcpClient;
    private final ObjectMapper objectMapper;
    private final McpGatewayProperties properties;
    private final Environment environment;
    private final RestClient tokenRestClient;

    public McpGatewayExecutionService(McpStreamableHttpClient mcpClient,
                                      ObjectMapper objectMapper,
                                      McpGatewayProperties properties) {
        this(mcpClient, objectMapper, properties, null, RestClient.builder());
    }

    public McpGatewayExecutionService(McpStreamableHttpClient mcpClient,
                                      ObjectMapper objectMapper,
                                      McpGatewayProperties properties,
                                      Environment environment) {
        this(mcpClient, objectMapper, properties, environment, RestClient.builder());
    }

    @Autowired
    public McpGatewayExecutionService(McpStreamableHttpClient mcpClient,
                                      ObjectMapper objectMapper,
                                      McpGatewayProperties properties,
                                      Environment environment,
                                      RestClient.Builder restClientBuilder) {
        this.mcpClient = mcpClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.environment = environment;
        this.tokenRestClient = (restClientBuilder == null ? RestClient.builder() : restClientBuilder).build();
    }

    public DiscoveryResponse discover(DiscoveryRequest request) {
        try {
            JsonNode trace = objectMapper.valueToTree(request.trace() == null ? Map.of() : request.trace());
            JsonNode server = objectMapper.valueToTree(request.server() == null ? Map.of() : request.server());
            String serverRef = firstNonBlank(request.serverRef(), text(server, "serverRef", "id"));
            URI endpoint = resolveEndpoint(serverRef, null, trace, server, server);
            Map<String, String> headers = resolveAuthHeaders(trace, server.path("auth"));
            McpStreamableHttpClient.McpRequestOptions options =
                McpStreamableHttpClient.McpRequestOptions.withHeaders(properties.protocolVersion(), headers);
            McpStreamableHttpClient.McpSession session = mcpClient.initialize(endpoint, options);
            JsonNode result = mcpClient.toolsList(session, options);
            List<McpToolSummary> tools = summarizeTools(result, request.allowedTools());
            return new DiscoveryResponse(
                true,
                "MCP tools/list completed.",
                serverRef,
                endpoint.toString(),
                session.protocolVersion(),
                tools,
                null
            );
        } catch (RuntimeException ex) {
            return new DiscoveryResponse(
                false,
                safeReason(ex, "MCP discovery failed."),
                firstNonBlank(request == null ? null : request.serverRef(), null),
                null,
                properties.protocolVersion(),
                List.of(),
                "MCP_DISCOVERY_FAILED"
            );
        }
    }

    public ToolsResultResponse initializeAndList(ServerRequest request) {
        DiscoveryResponse discovery = discover(new DiscoveryRequest(
            request.serverRef(),
            request.server(),
            request.trace(),
            List.of()
        ));
        return new ToolsResultResponse(
            discovery.ready(),
            discovery.message(),
            discovery.serverRef(),
            "tools/list",
            objectMapper.valueToTree(discovery.tools()),
            discovery.errorCode()
        );
    }

    public ServerVerificationResponse verify(ServerVerificationRequest request) {
        List<String> allowedTools = request.expectedTools() == null
            ? List.of()
            : request.expectedTools().stream()
                .map(ExpectedTool::name)
                .filter(StringUtils::hasText)
                .toList();
        DiscoveryResponse discovery = discover(new DiscoveryRequest(
            request.serverRef(),
            request.server(),
            request.trace(),
            allowedTools
        ));
        if (!discovery.ready()) {
            return new ServerVerificationResponse(
                false,
                discovery.message(),
                discovery.serverRef(),
                discovery.protocolVersion(),
                List.of(),
                discovery.errorCode()
            );
        }

        Map<String, McpToolSummary> observed = discovery.tools().stream()
            .collect(java.util.stream.Collectors.toMap(
                McpToolSummary::name,
                tool -> tool,
                (left, ignored) -> left,
                LinkedHashMap::new
            ));
        List<ToolVerificationResult> results = new ArrayList<>();
        boolean blockingDrift = false;
        for (ExpectedTool expected : request.expectedTools() == null ? List.<ExpectedTool>of() : request.expectedTools()) {
            if (expected == null || !StringUtils.hasText(expected.name())) {
                continue;
            }
            String policy = schemaDriftPolicy(expected.schemaDriftPolicy());
            McpToolSummary actual = observed.get(expected.name().trim());
            boolean present = actual != null;
            boolean schemaMatches = !StringUtils.hasText(expected.schemaHash())
                || (actual != null && expected.schemaHash().trim().equals(actual.schemaHash()));
            String status;
            if (!present) {
                status = "TOOL_MISSING";
                blockingDrift = true;
            } else if (!schemaMatches) {
                status = "SCHEMA_DRIFT";
                if (schemaDriftBlocks(policy)) {
                    blockingDrift = true;
                }
            } else {
                status = "OK";
            }
            results.add(new ToolVerificationResult(
                expected.name().trim(),
                present,
                schemaMatches,
                trim(expected.schemaHash()),
                actual == null ? null : actual.schemaHash(),
                policy,
                status
            ));
        }

        boolean ready = !blockingDrift;
        return new ServerVerificationResponse(
            ready,
            ready ? "MCP server verification passed." : "MCP server verification found blocking drift.",
            discovery.serverRef(),
            discovery.protocolVersion(),
            results,
            ready ? null : "MCP_SCHEMA_DRIFT"
        );
    }

    public ToolsResultResponse toolsCall(ToolsCallRequest request) {
        try {
            JsonNode trace = objectMapper.valueToTree(request.trace() == null ? Map.of() : request.trace());
            JsonNode server = objectMapper.valueToTree(request.server() == null ? Map.of() : request.server());
            String serverRef = firstNonBlank(request.serverRef(), text(server, "serverRef", "id"));
            URI endpoint = resolveEndpoint(serverRef, request, trace, server, server);
            Map<String, String> headers = resolveAuthHeaders(trace, server.path("auth"));
            McpStreamableHttpClient.McpRequestOptions options =
                McpStreamableHttpClient.McpRequestOptions.withHeaders(properties.protocolVersion(), headers);
            McpStreamableHttpClient.McpSession session = mcpClient.initialize(endpoint, options);
            JsonNode result = mcpClient.toolsCall(
                session,
                request.toolName(),
                objectMapper.valueToTree(request.arguments() == null ? Map.of() : request.arguments()),
                options
            );
            return new ToolsResultResponse(true, "MCP tools/call completed.", serverRef, request.toolName(), result, null);
        } catch (RuntimeException ex) {
            return new ToolsResultResponse(
                false,
                safeReason(ex, "MCP tools/call failed."),
                request == null ? null : request.serverRef(),
                request == null ? null : request.toolName(),
                null,
                "MCP_EXECUTION_FAILED"
            );
        }
    }

    public ActionExecuteResponse executeAction(ActionExecuteRequest request) {
        try {
            JsonNode trace = objectMapper.valueToTree(request.trace() == null ? Map.of() : request.trace());
            JsonNode actionConfig = firstObject(
                objectMapper.valueToTree(request.actionConfig() == null ? Map.of() : request.actionConfig()),
                trace.path("actionConfig")
            );
            JsonNode mcp = findMcpExecution(trace, actionConfig);
            if (!mcp.isObject()) {
                return failure("INVALID_MCP_ACTION_CONFIG", "MCP action config requires execution.mcp.");
            }
            String serverRef = text(mcp, "serverRef");
            String toolName = text(mcp, "toolName");
            if (!StringUtils.hasText(serverRef) || !StringUtils.hasText(toolName)) {
                return failure(
                    "INVALID_MCP_ACTION_CONFIG",
                    "MCP action config requires execution.mcp.serverRef and execution.mcp.toolName."
                );
            }
            JsonNode serverBinding = findServerBinding(trace, actionConfig, serverRef);
            URI endpoint = resolveEndpoint(serverRef, request, trace, mcp, serverBinding);
            JsonNode arguments = renderArguments(request, trace, mcp.path("argumentTemplate"));
            Map<String, String> headers = resolveAuthHeaders(trace, mcpAuthConfig(mcp, serverBinding));
            McpStreamableHttpClient.McpRequestOptions options =
                McpStreamableHttpClient.McpRequestOptions.withHeaders(properties.protocolVersion(), headers);
            ToolVerificationResult drift = null;
            JsonNode result;
            if (usesDirectJsonRpcToolCall(mcp, serverBinding)) {
                result = mcpClient.toolsCall(endpoint, toolName, arguments, options);
            } else {
                McpStreamableHttpClient.McpSession session = mcpClient.initialize(endpoint, options);
                drift = verifyActionToolSchema(session, options, serverRef, toolName, mcp);
                if (drift != null && (!drift.present() || !drift.schemaMatches()) && schemaDriftBlocks(drift.schemaDriftPolicy())) {
                    return failure(
                        "MCP_SCHEMA_DRIFT",
                        "MCP tool schema verification failed for " + toolName + "."
                    );
                }
                result = mcpClient.toolsCall(session, toolName, arguments, options);
            }
            return normalizeResult(serverRef, toolName, mcp, result, drift);
        } catch (McpAuthGateException ex) {
            return failure(ex.errorCode(), ex.getMessage());
        } catch (IllegalArgumentException ex) {
            return failure("INVALID_MCP_ACTION_CONFIG", ex.getMessage());
        } catch (ResponseStatusException ex) {
            return failure("MCP_EXECUTION_FAILED", safeReason(ex, "MCP execution failed."));
        } catch (Exception ex) {
            return failure("MCP_EXECUTION_FAILED", "MCP execution failed.");
        }
    }

    private List<McpToolSummary> summarizeTools(JsonNode result, List<String> allowedTools) {
        Set<String> allowed = allowedTools == null || allowedTools.isEmpty()
            ? Set.of()
            : allowedTools.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        List<McpToolSummary> out = new ArrayList<>();
        JsonNode tools = result.path("tools");
        if (!tools.isArray()) {
            return List.of();
        }
        for (JsonNode tool : tools) {
            String name = text(tool, "name");
            if (!StringUtils.hasText(name) || (!allowed.isEmpty() && !allowed.contains(name))) {
                continue;
            }
            JsonNode inputSchema = tool.path("inputSchema");
            JsonNode outputSchema = tool.path("outputSchema");
            out.add(new McpToolSummary(
                name,
                text(tool, "title"),
                text(tool, "description"),
                inputSchema.isMissingNode() ? objectMapper.createObjectNode() : inputSchema.deepCopy(),
                outputSchema.isMissingNode() ? objectMapper.createObjectNode() : outputSchema.deepCopy(),
                schemaHash(name, inputSchema, outputSchema)
            ));
        }
        return List.copyOf(out);
    }

    private String schemaHash(String toolName, JsonNode inputSchema, JsonNode outputSchema) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("toolName", toolName == null ? "" : toolName);
        payload.set("inputSchema", canonicalSchema(inputSchema));
        payload.set("outputSchema", canonicalSchema(outputSchema));
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(payload);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return "sha256:" + HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to hash MCP tool schema.", ex);
        }
    }

    private JsonNode canonicalSchema(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return objectMapper.createObjectNode();
        }
        if (node.isObject()) {
            ObjectNode out = objectMapper.createObjectNode();
            List<String> names = new ArrayList<>();
            node.fieldNames().forEachRemaining(names::add);
            names.stream()
                .filter(name -> !CANONICAL_HASH_IGNORED_FIELDS.contains(name))
                .sorted()
                .forEach(name -> out.set(name, canonicalSchema(sortableArray(name, node.path(name)))));
            return out;
        }
        if (node.isArray()) {
            ArrayNode out = objectMapper.createArrayNode();
            for (JsonNode child : node) {
                out.add(canonicalSchema(child));
            }
            return out;
        }
        return node.deepCopy();
    }

    private JsonNode sortableArray(String fieldName, JsonNode node) {
        if (!node.isArray() || !Set.of("required", "enum", "type").contains(fieldName)) {
            return node;
        }
        List<JsonNode> values = new ArrayList<>();
        node.forEach(value -> values.add(value.deepCopy()));
        values.sort(Comparator.comparing(JsonNode::toString));
        ArrayNode out = objectMapper.createArrayNode();
        values.forEach(out::add);
        return out;
    }

    private ToolVerificationResult verifyActionToolSchema(McpStreamableHttpClient.McpSession session,
                                                          McpStreamableHttpClient.McpRequestOptions options,
                                                          String serverRef,
                                                          String toolName,
                                                          JsonNode mcp) {
        String expectedHash = text(mcp, "toolSchemaHash");
        if (!StringUtils.hasText(expectedHash)) {
            return null;
        }
        JsonNode toolsResult = mcpClient.toolsList(session, options);
        List<McpToolSummary> tools = summarizeTools(toolsResult, List.of(toolName));
        McpToolSummary actual = tools.stream()
            .filter(tool -> toolName.equals(tool.name()))
            .findFirst()
            .orElse(null);
        String policy = schemaDriftPolicy(text(mcp, "schemaDriftPolicy"));
        boolean present = actual != null;
        boolean schemaMatches = actual != null && expectedHash.trim().equals(actual.schemaHash());
        return new ToolVerificationResult(
            toolName,
            present,
            schemaMatches,
            expectedHash.trim(),
            actual == null ? null : actual.schemaHash(),
            policy,
            present ? schemaMatches ? "OK" : "SCHEMA_DRIFT" : "TOOL_MISSING"
        );
    }

    private ActionExecuteResponse normalizeResult(String serverRef,
                                                  String toolName,
                                                  JsonNode mcp,
                                                  JsonNode result,
                                                  ToolVerificationResult drift) {
        ObjectNode data = objectMapper.createObjectNode();
        data.put("adapterType", "mcp-tool");
        data.put("mcpServerRef", serverRef);
        data.put("mcpToolName", toolName);
        data.put("evidenceType", "MCP_TOOL_RESULT");
        copyText(mcp, data, "toolSchemaHash");
        copyText(mcp, data, "schemaDriftPolicy");
        if (drift != null) {
            data.put("schemaDriftStatus", drift.status());
            if (StringUtils.hasText(drift.actualSchemaHash())) {
                data.put("actualToolSchemaHash", drift.actualSchemaHash());
            }
        }
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
        return new ActionExecuteResponse(true, "MCP tool result", normalized, null, List.of());
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

    private JsonNode findMcpExecution(JsonNode trace, JsonNode actionConfig) {
        for (JsonNode candidate : List.of(
            actionConfig.path("execution").path("mcp"),
            trace.path("actionConfig").path("execution").path("mcp"),
            trace.path("action").path("execution").path("mcp"),
            trace.path("execution").path("mcp"),
            trace.path("mcp")
        )) {
            if (candidate.isObject()) {
                return candidate;
            }
        }
        return MissingNode.getInstance();
    }

    private JsonNode findServerBinding(JsonNode trace, JsonNode actionConfig, String serverRef) {
        for (JsonNode container : List.of(
            actionConfig.path("mcpServers"),
            trace.path("actionConfig").path("mcpServers"),
            trace.path("action").path("mcpServers"),
            trace.path("mcpServers")
        )) {
            JsonNode found = findServerBindingInContainer(container, serverRef);
            if (found.isObject()) {
                return found;
            }
        }
        JsonNode single = firstObject(trace.path("mcpServer"), actionConfig.path("mcpServer"));
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

    private URI resolveEndpoint(String serverRef, Object request, JsonNode trace, JsonNode mcp, JsonNode serverBinding) {
        String endpoint = firstText(mcp, serverBinding, List.of("endpointUrl", "endpoint", "url", "discoveryUrl"));
        if (!StringUtils.hasText(endpoint)) {
            String template = firstText(mcp, serverBinding, List.of("endpointUrlTemplate", "urlTemplate", "discoveryUrlTemplate"));
            if (StringUtils.hasText(template)) {
                endpoint = renderEndpointTemplate(template, request, trace);
            }
        }
        if (!StringUtils.hasText(endpoint)) {
            endpoint = endpointForKind(firstText(mcp, serverBinding, List.of("endpointKind", "kind")), request, trace);
        }
        if (!StringUtils.hasText(endpoint)) {
            throw new IllegalArgumentException("MCP server endpoint is required for " + firstNonBlank(serverRef, "server") + ".");
        }
        return requirePublicHttpsUri(endpoint, "MCP server endpoint");
    }

    private URI requirePublicHttpsUri(String rawUri, String fieldName) {
        URI uri = URI.create(rawUri.trim());
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"https".equals(scheme)) {
            throw new IllegalArgumentException(fieldName + " must use https.");
        }
        String host = uri.getHost();
        if (!StringUtils.hasText(host)
            || StringUtils.hasText(uri.getUserInfo())
            || isBlockedOutboundHost(host)
            || resolvesToBlockedOutboundAddress(host)) {
            throw new IllegalArgumentException(fieldName + " host is not allowed.");
        }
        return uri;
    }

    private boolean resolvesToBlockedOutboundAddress(String host) {
        String normalized = normalizeOutboundHost(host);
        if (IPV4_LITERAL.matcher(normalized).matches() || normalized.contains(":")) {
            return false;
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(normalized)) {
                if (isBlockedOutboundAddress(address)) {
                    return true;
                }
            }
            return false;
        } catch (UnknownHostException ex) {
            throw new IllegalArgumentException("MCP outbound host could not be resolved.");
        }
    }

    private boolean isBlockedOutboundAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
            || address.isLoopbackAddress()
            || address.isLinkLocalAddress()
            || address.isSiteLocalAddress()
            || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 16) {
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            return first == 0
                || first == 0xfc
                || first == 0xfd
                || (first == 0xfe && (second & 0xc0) == 0x80);
        }
        return false;
    }

    private boolean isBlockedOutboundHost(String host) {
        String normalized = normalizeOutboundHost(host);
        if (!StringUtils.hasText(normalized)
            || "localhost".equals(normalized)
            || normalized.endsWith(".localhost")
            || normalized.endsWith(".local")
            || normalized.endsWith(".internal")
            || "metadata.google.internal".equals(normalized)) {
            return true;
        }
        if (IPV4_LITERAL.matcher(normalized).matches()) {
            String[] parts = normalized.split("\\.");
            int first = Integer.parseInt(parts[0]);
            int second = Integer.parseInt(parts[1]);
            return first == 0
                || first == 10
                || first == 127
                || first == 169 && second == 254
                || first == 172 && second >= 16 && second <= 31
                || first == 192 && second == 168
                || first >= 224;
        }
        if (!normalized.contains(":")) {
            return false;
        }
        return "::".equals(normalized)
            || "::1".equals(normalized)
            || normalized.startsWith("0:")
            || normalized.startsWith("fc")
            || normalized.startsWith("fd")
            || normalized.startsWith("fe80:")
            || normalized.startsWith("::ffff:0:")
            || normalized.startsWith("::ffff:127.")
            || normalized.startsWith("::ffff:169.254.")
            || normalized.startsWith("::ffff:10.")
            || normalized.startsWith("::ffff:192.168.");
    }

    private String normalizeOutboundHost(String host) {
        String normalized = host == null ? "" : host.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        while (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String renderEndpointTemplate(String template, Object request, JsonNode trace) {
        Matcher matcher = TEMPLATE.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            JsonNode value = resolveTemplateValue(matcher.group(1), request, trace, false);
            if (value.isMissingNode() || value.isNull()) {
                throw new IllegalArgumentException("MCP endpoint template has an unresolved placeholder.");
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(value.asText()));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private JsonNode renderArguments(ActionExecuteRequest request, JsonNode trace, JsonNode argumentTemplate) {
        JsonNode params = objectMapper.valueToTree(request.params() == null ? Map.of() : request.params());
        if (argumentTemplate == null || argumentTemplate.isMissingNode() || argumentTemplate.isNull()) {
            return params.isObject() ? params : objectMapper.createObjectNode();
        }
        if (!argumentTemplate.isObject()) {
            throw new IllegalArgumentException("MCP argumentTemplate must be an object.");
        }
        JsonNode rendered = resolveProfileRefs(renderTemplateNode(argumentTemplate, request, trace), trace);
        JsonNode pruned = pruneEmptyArgumentValues(rendered);
        return pruned != null && pruned.isObject() ? pruned : objectMapper.createObjectNode();
    }

    private String endpointForKind(String endpointKind, Object request, JsonNode trace) {
        String kind = normalizedEnum(endpointKind);
        if (!StringUtils.hasText(kind)) {
            return null;
        }
        String shopDomain = resolveShopDomain(request, trace);
        if (!StringUtils.hasText(shopDomain)) {
            throw new IllegalArgumentException("MCP endpointKind " + kind + " requires trace.shopDomain or params.shopDomain.");
        }
        return switch (kind) {
            case "STOREFRONT_STANDARD", "SHOPIFY_STOREFRONT_STANDARD" ->
                "https://" + shopDomain + "/api/mcp";
            case "UCP_CATALOG", "CHECKOUT_UCP", "SHOPIFY_UCP", "SHOPIFY_UCP_CATALOG" ->
                "https://" + shopDomain + "/api/ucp/mcp";
            case "CUSTOMER_ACCOUNT", "SHOPIFY_CUSTOMER_ACCOUNT" -> discoverCustomerAccountMcpEndpoint(shopDomain);
            default -> throw new IllegalArgumentException("Unsupported MCP endpointKind: " + kind);
        };
    }

    private String discoverCustomerAccountMcpEndpoint(String shopDomain) {
        try {
            JsonNode response = tokenRestClient.get()
                .uri(URI.create("https://" + shopDomain + "/.well-known/customer-account-api"))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(JsonNode.class);
            String endpoint = response == null ? null : firstNonBlank(
                text(response, "mcp_api"),
                text(response, "mcpApi", "mcp_endpoint", "mcpEndpoint")
            );
            if (!StringUtils.hasText(endpoint)) {
                throw new IllegalArgumentException("Customer Account MCP discovery response did not include mcp_api.");
            }
            return endpoint;
        } catch (RestClientException ex) {
            throw new IllegalArgumentException("Customer Account MCP endpoint discovery failed.");
        }
    }

    private String resolveShopDomain(Object request, JsonNode trace) {
        for (JsonNode candidate : List.of(
            trace.path("shopDomain"),
            trace.path("install").path("shopDomain"),
            trace.path("config").path("shopDomain")
        )) {
            String value = candidate.asText("").trim();
            if (StringUtils.hasText(value)) {
                return normalizeShopDomain(value);
            }
        }
        if (request instanceof ActionExecuteRequest executeRequest) {
            Object value = executeRequest.params() == null ? null : executeRequest.params().get("shopDomain");
            if (value != null && StringUtils.hasText(value.toString())) {
                return normalizeShopDomain(value.toString());
            }
        }
        return null;
    }

    private String normalizeShopDomain(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9][a-z0-9.-]*\\.myshopify\\.com")
            && !normalized.matches("[a-z0-9][a-z0-9.-]*\\.[a-z]{2,63}")) {
            throw new IllegalArgumentException("MCP Shopify shopDomain is invalid.");
        }
        return normalized;
    }

    private JsonNode renderTemplateNode(JsonNode node, Object request, JsonNode trace) {
        if (node.isObject()) {
            ObjectNode rendered = objectMapper.createObjectNode();
            node.fields().forEachRemaining(entry ->
                rendered.set(entry.getKey(), renderTemplateNode(entry.getValue(), request, trace)));
            return rendered;
        }
        if (node.isArray()) {
            ArrayNode rendered = objectMapper.createArrayNode();
            for (JsonNode child : node) {
                rendered.add(renderTemplateNode(child, request, trace));
            }
            return rendered;
        }
        if (!node.isTextual()) {
            return node.deepCopy();
        }
        String value = node.asText();
        Matcher exact = EXACT_TEMPLATE.matcher(value);
        if (exact.matches()) {
            JsonNode resolved = resolveTemplateValue(exact.group(1), request, trace, true);
            return resolved.isMissingNode() ? objectMapper.getNodeFactory().nullNode() : resolved.deepCopy();
        }
        Matcher matcher = TEMPLATE.matcher(value);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            JsonNode resolved = resolveTemplateValue(matcher.group(1), request, trace, true);
            matcher.appendReplacement(out, Matcher.quoteReplacement(resolved.isMissingNode() || resolved.isNull()
                ? ""
                : resolved.asText()));
        }
        matcher.appendTail(out);
        return objectMapper.getNodeFactory().textNode(out.toString());
    }

    private JsonNode pruneEmptyArgumentValues(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isTextual() && !StringUtils.hasText(node.asText())) {
            return null;
        }
        if (node.isObject()) {
            ObjectNode out = objectMapper.createObjectNode();
            node.fields().forEachRemaining(entry -> {
                JsonNode pruned = pruneEmptyArgumentValues(entry.getValue());
                if (pruned != null) {
                    out.set(entry.getKey(), pruned);
                }
            });
            return out.isEmpty() ? null : out;
        }
        if (node.isArray()) {
            ArrayNode out = objectMapper.createArrayNode();
            for (JsonNode child : node) {
                JsonNode pruned = pruneEmptyArgumentValues(child);
                if (pruned != null) {
                    out.add(pruned);
                }
            }
            return out.isEmpty() ? null : out;
        }
        return node.deepCopy();
    }

    private JsonNode resolveProfileRefs(JsonNode node, JsonNode trace) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return node;
        }
        if (node.isArray()) {
            ArrayNode out = objectMapper.createArrayNode();
            for (JsonNode child : node) {
                out.add(resolveProfileRefs(child, trace));
            }
            return out;
        }
        if (!node.isObject()) {
            return node;
        }
        ObjectNode out = objectMapper.createObjectNode();
        node.fields().forEachRemaining(entry -> out.set(entry.getKey(), resolveProfileRefs(entry.getValue(), trace)));
        String profileRef = text(out, "profileRef");
        if (StringUtils.hasText(profileRef)) {
            String profile = resolveProfileValue(trace, profileRef);
            if (!StringUtils.hasText(profile)) {
                throw new IllegalArgumentException("MCP profileRef is not available: " + profileRef);
            }
            out.remove("profileRef");
            out.put("profile", profile);
        }
        return out;
    }

    private String resolveProfileValue(JsonNode trace, String profileRef) {
        if (!StringUtils.hasText(profileRef)) {
            return null;
        }
        for (JsonNode container : List.of(
            trace.path("mcpProfileValues"),
            trace.path("profileValues"),
            trace.path("config")
        )) {
            String value = container.path(profileRef).asText("").trim();
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        String normalizedRef = profileRef.trim().toUpperCase(Locale.ROOT);
        Set<String> allowlist = properties.profileRefAllowlist().stream()
            .map(value -> value.trim().toUpperCase(Locale.ROOT))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (environment != null && allowlist.contains(normalizedRef)) {
            String value = environment.getProperty(profileRef.trim());
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private JsonNode mcpAuthConfig(JsonNode mcp, JsonNode serverBinding) {
        JsonNode auth = firstObject(mcp.path("auth"), serverBinding.path("auth"));
        if (auth.isObject() && !auth.isEmpty()) {
            return auth;
        }
        String authMode = firstNonBlank(text(mcp, "authMode"), text(serverBinding, "authMode"));
        if (!StringUtils.hasText(authMode)) {
            return objectMapper.createObjectNode();
        }
        ObjectNode out = objectMapper.createObjectNode();
        out.put("mode", authMode);
        for (String field : List.of(
            "tokenSecretRef",
            "secretRef",
            "headerName",
            "tokenUrl",
            "tokenEndpoint",
            "clientId",
            "clientIdSecretRef",
            "clientSecretRef",
            "scope",
            "audience"
        )) {
            copyText(mcp, out, field);
            copyText(serverBinding, out, field);
        }
        return out;
    }

    private JsonNode resolveTemplateValue(String expression, Object request, JsonNode trace, boolean allowParams) {
        String key = expression == null ? "" : expression.trim();
        if ("actionId".equals(key) && request instanceof ActionExecuteRequest executeRequest) {
            return objectMapper.getNodeFactory().textNode(executeRequest.actionId() == null ? "" : executeRequest.actionId());
        }
        if ("idempotencyKey".equals(key) && request instanceof ActionExecuteRequest executeRequest) {
            return objectMapper.getNodeFactory().textNode(executeRequest.idempotencyKey() == null ? "" : executeRequest.idempotencyKey());
        }
        if (allowParams && key.startsWith("params.") && request instanceof ActionExecuteRequest executeRequest) {
            JsonNode params = objectMapper.valueToTree(executeRequest.params() == null ? Map.of() : executeRequest.params());
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
        if ("OAUTH2_CLIENT_CREDENTIALS".equals(mode)) {
            String accessToken = fetchClientCredentialsToken(trace, auth, false);
            return Map.of(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        }
        if ("SHOPIFY_AGENTIC_CLIENT_CREDENTIALS".equals(mode)) {
            String accessToken = fetchClientCredentialsToken(trace, auth, true);
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
            String buyerIp = resolveShopifyBuyerIp(trace);
            if (StringUtils.hasText(buyerIp)) {
                headers.put("Shopify-Buyer-IP", buyerIp);
            }
            return Map.copyOf(headers);
        }
        if ("CUSTOMER_OAUTH_PKCE".equals(mode)) {
            String customerToken = resolveCustomerAccountAccessToken(trace, auth);
            if (!StringUtils.hasText(customerToken)) {
                throw new McpAuthGateException(
                    "CUSTOMER_ACCOUNT_AUTH_REQUIRED",
                    "Customer Account MCP requires a bound customer OAuth/PKCE access token."
                );
            }
            String bearer = customerToken.trim().regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())
                ? customerToken.trim()
                : "Bearer " + customerToken.trim();
            return Map.of(HttpHeaders.AUTHORIZATION, bearer);
        }
        throw new IllegalArgumentException("MCP auth mode is not supported by the generic execution gateway: " + mode);
    }

    private boolean usesDirectJsonRpcToolCall(JsonNode mcp, JsonNode serverBinding) {
        String endpointKind = normalizedEnum(firstText(mcp, serverBinding, List.of("endpointKind", "kind")));
        String serverRef = normalizedEnum(firstText(mcp, serverBinding, List.of("serverRef", "id")));
        String authMode = normalizedEnum(firstText(mcp, serverBinding, List.of("authMode", "mode")));
        return "CHECKOUT_UCP".equals(endpointKind)
            || serverRef.contains("CHECKOUT")
            || ("SHOPIFY_AGENTIC_CLIENT_CREDENTIALS".equals(authMode) && endpointKind.contains("UCP"));
    }

    private String resolveShopifyBuyerIp(JsonNode trace) {
        String value = firstNonBlank(
            text(trace, "shopifyBuyerIp", "buyerIp", "clientIp", "remoteAddr"),
            firstNonBlank(
                text(trace.path("request"), "shopifyBuyerIp", "buyerIp", "clientIp", "remoteAddr"),
                text(trace.path("authContext"), "shopifyBuyerIp", "buyerIp", "clientIp", "remoteAddr")
            )
        );
        String candidate = firstForwardedIp(value);
        return isSafeIpLiteral(candidate) ? candidate : null;
    }

    private String firstForwardedIp(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String candidate = value.split(",", 2)[0].trim();
        if (candidate.startsWith("[") && candidate.contains("]")) {
            candidate = candidate.substring(1, candidate.indexOf(']'));
        }
        int portSeparator = candidate.lastIndexOf(':');
        if (portSeparator > 0 && candidate.indexOf(':') == portSeparator) {
            candidate = candidate.substring(0, portSeparator);
        }
        return candidate.trim();
    }

    private boolean isSafeIpLiteral(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        if (value.matches("^(25[0-5]|2[0-4]\\d|1?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|1?\\d?\\d)){3}$")) {
            return true;
        }
        if (!value.contains(":") || !value.matches("^[0-9A-Fa-f:.]+$")) {
            return false;
        }
        try {
            return java.net.InetAddress.getByName(value) instanceof java.net.Inet6Address;
        } catch (Exception ex) {
            return false;
        }
    }

    private void validateApiKeyHeader(String headerName) {
        String normalized = headerName == null ? "" : headerName.trim().toUpperCase(Locale.ROOT);
        Set<String> allowlist = properties.apiKeyHeaderAllowlist().stream()
            .map(value -> value.trim().toUpperCase(Locale.ROOT))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (BLOCKED_HEADER_NAMES.contains(normalized) || !allowlist.contains(normalized)) {
            throw new IllegalArgumentException("MCP API key headerName is not allowlisted.");
        }
    }

    private String fetchClientCredentialsToken(JsonNode trace, JsonNode auth, boolean shopifyAgenticCheckout) {
        String tokenUrl = text(auth, "tokenUrl", "tokenEndpoint", "tokenEndpointUrl");
        if (!StringUtils.hasText(tokenUrl) && shopifyAgenticCheckout) {
            tokenUrl = SHOPIFY_CHECKOUT_TOKEN_URL;
        }
        if (!StringUtils.hasText(tokenUrl)) {
            throw new IllegalArgumentException("MCP OAuth2 client credentials auth requires tokenUrl.");
        }
        URI tokenUri = requirePublicHttpsUri(tokenUrl, "MCP OAuth2 tokenUrl");
        String clientId = firstNonBlank(
            text(auth, "clientId"),
            firstNonBlank(
                resolveSecretValue(trace, text(auth, "clientIdSecretRef")),
                shopifyAgenticCheckout ? resolveSecretValue(trace, SHOPIFY_CHECKOUT_CLIENT_ID_SECRET_REF) : null
            )
        );
        String clientSecret = firstNonBlank(
            resolveSecretValue(trace, text(auth, "clientSecretRef", "secretRef")),
            shopifyAgenticCheckout ? resolveSecretValue(trace, SHOPIFY_CHECKOUT_CLIENT_SECRET_SECRET_REF) : null
        );
        if (!StringUtils.hasText(clientId) || !StringUtils.hasText(clientSecret)) {
            if (shopifyAgenticCheckout) {
                throw new McpAuthGateException(
                    "CHECKOUT_MCP_NOT_CONFIGURED",
                    "Checkout MCP client credentials are not configured."
                );
            }
            throw new IllegalArgumentException("MCP OAuth2 client credentials are not available.");
        }
        boolean jsonRequest = shopifyAgenticCheckout || "JSON".equals(normalizedEnum(text(auth, "tokenRequestFormat", "requestFormat")));
        String scope = text(auth, "scope", "scopes");
        String audience = text(auth, "audience");
        try {
            JsonNode response;
            if (jsonRequest) {
                ObjectNode payload = objectMapper.createObjectNode();
                payload.put("grant_type", "client_credentials");
                payload.put("client_id", clientId.trim());
                payload.put("client_secret", clientSecret.trim());
                if (StringUtils.hasText(scope)) {
                    payload.put("scope", scope.trim());
                }
                if (StringUtils.hasText(audience)) {
                    payload.put("audience", audience.trim());
                }
                response = tokenRestClient.post()
                    .uri(tokenUri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(JsonNode.class);
            } else {
                MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
                form.add("grant_type", "client_credentials");
                form.add("client_id", clientId.trim());
                form.add("client_secret", clientSecret.trim());
                if (StringUtils.hasText(scope)) {
                    form.add("scope", scope.trim());
                }
                if (StringUtils.hasText(audience)) {
                    form.add("audience", audience.trim());
                }
                response = tokenRestClient.post()
                    .uri(tokenUri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            }
            String accessToken = response == null ? null : response.path("access_token").asText("").trim();
            if (!StringUtils.hasText(accessToken)) {
                throw new IllegalArgumentException("MCP OAuth2 token response did not include access_token.");
            }
            return accessToken;
        } catch (RestClientException ex) {
            throw new IllegalArgumentException("MCP OAuth2 client credentials token request failed.");
        }
    }

    private String resolveCustomerAccountAccessToken(JsonNode trace, JsonNode auth) {
        String token = resolveSecretValue(trace, text(auth, "tokenSecretRef", "secretRef"));
        if (StringUtils.hasText(token)) {
            return token;
        }
        for (String field : List.of(
            "mcpCustomerAccessToken",
            "customerAccountAccessToken",
            "customerAccessToken",
            "customerAuthorization"
        )) {
            token = trace.path(field).asText("").trim();
            if (StringUtils.hasText(token)) {
                return token;
            }
        }
        for (String dottedPath : List.of(
            "customerAccount.accessToken",
            "customerAccount.authorization",
            "customer.accessToken",
            "shopper.customerAccessToken"
        )) {
            JsonNode value = readDottedPath(trace, dottedPath);
            token = value.asText("").trim();
            if (StringUtils.hasText(token)) {
                return token;
            }
        }
        return null;
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
        if (properties.environmentSecretResolutionEnabled()
            && environment != null
            && SAFE_SECRET_REF.matcher(secretRef.trim()).matches()
            && secretRef.trim().startsWith(properties.environmentSecretRefPrefix())) {
            String value = environment.getProperty(secretRef.trim());
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
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
        if (left != null && left.isObject() && !left.isEmpty()) {
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

    private String firstNonBlank(String left, String right) {
        return StringUtils.hasText(left) ? left.trim() : StringUtils.hasText(right) ? right.trim() : null;
    }

    private String normalizedEnum(String value) {
        return value == null ? "" : value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
    }

    private String schemaDriftPolicy(String value) {
        String policy = normalizedEnum(value);
        return StringUtils.hasText(policy) ? policy : "BLOCK_RELEASE";
    }

    private boolean schemaDriftBlocks(String policy) {
        return !"WARN_ONLY".equals(schemaDriftPolicy(policy));
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private String safeReason(RuntimeException ex, String fallback) {
        if (ex instanceof ResponseStatusException statusException && StringUtils.hasText(statusException.getReason())) {
            return statusException.getReason();
        }
        return StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : fallback;
    }

    private ActionExecuteResponse failure(String errorCode, String message) {
        return new ActionExecuteResponse(false, message, Map.of(), errorCode, List.of());
    }

    private static class McpAuthGateException extends RuntimeException {
        private final String errorCode;

        McpAuthGateException(String errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }

        String errorCode() {
            return errorCode;
        }
    }
}
