package com.ai.fabric.runtime.mcp;

import ai.fabric.http.OutboundHttpExecutionRequest;
import ai.fabric.http.OutboundHttpExecutor;
import ai.fabric.intent.action.*;
import ai.fabric.intent.action.connector.McpActionExecutor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Executes owned connections through Platform without receiving upstream credentials. */
@Component
@ConditionalOnProperty(name = "loomai.mcp.enabled", havingValue = "true")
public class RuntimeManagedMcpExecutor implements McpActionExecutor {
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };
    private final OutboundHttpExecutor http;
    private final ObjectMapper mapper;
    private final String url;
    private final String key;
    private final String deploymentId;

    public RuntimeManagedMcpExecutor(OutboundHttpExecutor http, ObjectMapper mapper,
        @Value("${loomai.mcp.platform-base-url}") String baseUrl,
        @Value("${loomai.mcp.execution-api-key}") String key,
        @Value("${loomai.mcp.deployment-id}") String deploymentId,
        @Value("${loomai.mcp.target-profile-id}") String targetId) {
        this.http = http; this.mapper = mapper; this.key = key; this.deploymentId = identifier(deploymentId);
        URI base = URI.create(baseUrl);
        if (!List.of("https", "http").contains(base.getScheme()) || base.getHost() == null
            || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null
            || (base.getPath() != null && !base.getPath().isEmpty() && !"/".equals(base.getPath()))
            || key == null || key.isBlank()) throw new IllegalArgumentException("Managed MCP requires a configured Platform origin and target key.");
        this.url = baseUrl.replaceAll("/+$", "") + "/api/mcp/runtime/" + this.deploymentId + "/"
            + identifier(targetId) + "/actions/execute";
    }

    @Override public boolean isAvailable() { return true; }

    @Override public ActionResult execute(String actionId, ActionAccessMode accessMode, Map<String, Object> params,
                                          ActionContext context, Map<String, Object> actionConfig) {
        JsonNode config = mapper.valueToTree(actionConfig);
        if (!config.path("execution").path("mcp").path("connectionId").isTextual()) {
            return failure(ERROR_MCP_TOOL_NOT_AVAILABLE, "This action uses the existing MCP connector.");
        }
        if (context == null || !deploymentId.equals(context.authContext().getDeploymentId())) {
            return failure("MCP_ACCESS_DENIED", "The action needs its verified runtime context.");
        }
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("authContext", mapper.convertValue(context.authContext(), MAP));
        trace.put("authenticated", context.isAuthenticated());
        put(trace, "requestId", context.requestId());
        put(trace, "conversationId", context.conversationId());
        put(trace, "sessionId", context.sessionId());
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("actionId", actionId);
        request.put("params", params == null ? Map.of() : params);
        request.put("trace", trace);
        if (accessMode != ActionAccessMode.READ) request.put("idempotencyKey", UUID.randomUUID().toString());
        try {
            // A lost write response has an unknown outcome. This boundary never replays it.
            var response = http.execute(new OutboundHttpExecutionRequest(url, HttpMethod.POST,
                Map.of("Content-Type", "application/json", "X-LoomAI-MCP-Runtime-Key", key), mapper.writeValueAsString(request),
                Duration.ofSeconds(10), Duration.ofSeconds(75)));
            if (response == null || response.body() == null || response.body().length() > 8 * 1024 * 1024) {
                return invalidResult(accessMode, "The integration returned no bounded result.");
            }
            JsonNode body = mapper.readTree(response.body());
            String upstreamCode = body.path("errorCode").asText("");
            if (List.of("MCP_UPSTREAM_AUTH_REQUIRED", "MCP_UPSTREAM_ACCESS_DENIED").contains(upstreamCode)) {
                return failure(upstreamCode,
                    "The business integration needs its administrator to check the company connection and permissions. Your normal app sign-in is unchanged; no personal MCP login is required.");
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                if (accessMode != ActionAccessMode.READ && response.statusCode() >= 500) {
                    return failure("MCP_EXECUTION_OUTCOME_UNKNOWN",
                        "The write result could not be confirmed. Check the connected business system before retrying; this request was not automatically repeated.");
                }
                return failure(body.path("errorCode").asText("MCP_EXECUTION_FAILED"),
                    body.path("message").asText("The integration request was not authorized or could not complete."));
            }
            if (!body.path("success").isBoolean()) return invalidResult(accessMode, "The integration returned an invalid result contract.");
            ActionResult result = ActionResult.builder().success(body.path("success").booleanValue())
                .message(body.path("message").isTextual() ? body.path("message").textValue() : null)
                .errorCode(body.path("errorCode").isTextual() ? body.path("errorCode").textValue() : null).build();
            JsonNode data = body.get("data");
            if (data != null && !data.isNull()) {
                if (!data.isObject()) return invalidResult(accessMode, "The integration result must use the action payload contract.");
                Map<String, Object> fields = mapper.convertValue(data, MAP);
                if (data.has(ActionResultContracts.LIST_ITEMS)) {
                    JsonNode items = data.get(ActionResultContracts.LIST_ITEMS);
                    if (!items.isArray()) return invalidResult(accessMode, "The integration returned invalid list items.");
                    fields.remove(ActionResultContracts.LIST_ITEMS);
                    fields.remove(ActionResultContracts.LIST_COUNT);
                    fields.remove(ActionResultContracts.LIST_TOTAL_COUNT);
                    fields.remove(ActionResultContracts.LIST_CURSOR);
                    JsonNode count = data.get(ActionResultContracts.LIST_COUNT);
                    JsonNode total = data.get(ActionResultContracts.LIST_TOTAL_COUNT);
                    JsonNode cursor = data.get(ActionResultContracts.LIST_CURSOR);
                    if ((count != null && !count.isIntegralNumber()) || (total != null && !total.isIntegralNumber())
                        || (cursor != null && !cursor.isTextual())) return invalidResult(accessMode, "The integration returned invalid list metadata.");
                    result.setData(ActionListPayload.of(mapper.convertValue(items, new TypeReference<List<Object>>() { }),
                        count == null ? null : count.intValue(), total == null ? null : total.longValue(),
                        cursor == null ? null : cursor.textValue(), fields));
                } else result.setData(ActionObjectPayload.of(fields));
            }
            if (body.path("pinnedTargets").isArray()) result.setPinnedTargets(mapper.convertValue(body.get("pinnedTargets"), new TypeReference<List<ActionTargetRef>>() { }));
            if (body.path("groundingSufficiency").isTextual()) result.setGroundingSufficiency(ActionGroundingSufficiency.valueOf(body.path("groundingSufficiency").textValue()));
            return result;
        } catch (Exception failure) {
            return failure(accessMode == ActionAccessMode.READ ? "MCP_EXECUTION_FAILED" : "MCP_EXECUTION_OUTCOME_UNKNOWN",
                "The integration result could not be confirmed. The request was not automatically repeated.");
        }
    }

    private static void put(Map<String, Object> out, String name, String value) { if (value != null) out.put(name, value); }
    private static ActionResult invalidResult(ActionAccessMode mode, String message) {
        return mode == ActionAccessMode.READ ? failure("MCP_INVALID_RESPONSE", message)
            : failure("MCP_EXECUTION_OUTCOME_UNKNOWN", message
                + " The write may have completed. Check the connected business system before retrying; this request was not automatically repeated.");
    }
    private static String identifier(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{1,128}")) throw new IllegalArgumentException("Managed MCP needs exact resource identifiers.");
        return value;
    }
    private static ActionResult failure(String code, String message) { return ActionResult.builder().success(false).errorCode(code).message(message).build(); }
}
