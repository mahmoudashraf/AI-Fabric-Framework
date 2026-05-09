package com.ai.fabric.product.mcp.gateway.client;

import com.ai.fabric.product.mcp.gateway.config.McpGatewayProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;

@Component
public class McpStreamableHttpClient {

    public static final String MCP_SESSION_ID_HEADER = "MCP-Session-Id";
    public static final String MCP_PROTOCOL_VERSION_HEADER = "MCP-Protocol-Version";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final McpGatewayProperties properties;
    private final AtomicLong requestIds = new AtomicLong(1);

    public McpStreamableHttpClient(RestClient.Builder restClientBuilder,
                                   ObjectMapper objectMapper,
                                   McpGatewayProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.restClient = restClientBuilder
            .requestFactory(requestFactory(properties))
            .build();
    }

    public McpSession initialize(URI endpoint, McpRequestOptions options) {
        McpRequestOptions resolvedOptions = options == null ? McpRequestOptions.none(properties.protocolVersion()) : options;
        ObjectNode params = objectMapper.createObjectNode();
        params.put("protocolVersion", resolvedOptions.protocolVersion());
        params.set("capabilities", objectMapper.createObjectNode());
        ObjectNode clientInfo = params.putObject("clientInfo");
        clientInfo.put("name", "loomai-mcp-execution-gateway");
        clientInfo.put("version", "0.1.0");

        McpHttpResponse response = postJsonRpc(endpoint, jsonRpcRequest("initialize", params), null, false, resolvedOptions);
        JsonNode result = requireResult(response.message(), "initialize");
        String protocolVersion = result.path("protocolVersion").asText(resolvedOptions.protocolVersion());
        McpSession session = new McpSession(
            endpoint,
            protocolVersion,
            response.headers().getFirst(MCP_SESSION_ID_HEADER),
            result
        );
        postNotification(endpoint, "notifications/initialized", session, resolvedOptions);
        return session;
    }

    public JsonNode toolsList(McpSession session, McpRequestOptions options) {
        McpHttpResponse response = postJsonRpc(
            session.endpoint(),
            jsonRpcRequest("tools/list", objectMapper.createObjectNode()),
            session,
            false,
            options == null ? McpRequestOptions.none(session.protocolVersion()) : options
        );
        return requireResult(response.message(), "tools/list");
    }

    public JsonNode toolsCall(McpSession session, String toolName, JsonNode arguments, McpRequestOptions options) {
        if (!StringUtils.hasText(toolName)) {
            throw new ResponseStatusException(BAD_GATEWAY, "MCP toolName is required.");
        }
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", toolName.trim());
        params.set("arguments", arguments != null && arguments.isObject() ? arguments : objectMapper.createObjectNode());
        McpHttpResponse response = postJsonRpc(
            session.endpoint(),
            jsonRpcRequest("tools/call", params),
            session,
            false,
            options == null ? McpRequestOptions.none(session.protocolVersion()) : options
        );
        return requireResult(response.message(), "tools/call");
    }

    public JsonNode toolsCall(URI endpoint, String toolName, JsonNode arguments, McpRequestOptions options) {
        if (!StringUtils.hasText(toolName)) {
            throw new ResponseStatusException(BAD_GATEWAY, "MCP toolName is required.");
        }
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", toolName.trim());
        params.set("arguments", arguments != null && arguments.isObject() ? arguments : objectMapper.createObjectNode());
        McpHttpResponse response = postJsonRpc(
            endpoint,
            jsonRpcRequest("tools/call", params),
            null,
            false,
            options == null ? McpRequestOptions.none(properties.protocolVersion()) : options
        );
        return requireResult(response.message(), "tools/call");
    }

    private void postNotification(URI endpoint, String method, McpSession session, McpRequestOptions options) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("jsonrpc", "2.0");
        body.put("method", method);
        postJsonRpc(endpoint, body, session, true, options);
    }

    private ObjectNode jsonRpcRequest(String method, JsonNode params) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("jsonrpc", "2.0");
        body.put("id", requestIds.getAndIncrement());
        body.put("method", method);
        if (params != null && !params.isMissingNode() && !params.isNull()) {
            body.set("params", params);
        }
        return body;
    }

    private McpHttpResponse postJsonRpc(URI endpoint,
                                        JsonNode body,
                                        McpSession session,
                                        boolean notification,
                                        McpRequestOptions options) {
        if (endpoint == null || !StringUtils.hasText(endpoint.toString())) {
            throw new ResponseStatusException(BAD_GATEWAY, "MCP endpoint is required.");
        }
        McpRequestOptions resolvedOptions = options == null ? McpRequestOptions.none(properties.protocolVersion()) : options;
        try {
            ResponseEntity<String> response = restClient.post()
                .uri(endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .headers(headers -> applyMcpHeaders(headers, session, resolvedOptions))
                .body(body)
                .retrieve()
                .toEntity(String.class);
            if (response.getStatusCode().is3xxRedirection()) {
                throw new ResponseStatusException(
                    BAD_GATEWAY,
                    "MCP server returned HTTP " + response.getStatusCode().value()
                        + " redirect" + safeRedirectLocation(response) + "."
                );
            }
            if (notification) {
                return new McpHttpResponse(null, response.getHeaders());
            }
            return new McpHttpResponse(readJsonRpcMessage(response), response.getHeaders());
        } catch (RestClientResponseException ex) {
            throw new ResponseStatusException(
                BAD_GATEWAY,
                "MCP server returned HTTP " + ex.getStatusCode().value() + ".",
                ex
            );
        } catch (RestClientException ex) {
            throw new ResponseStatusException(BAD_GATEWAY, "MCP server request failed.", ex);
        }
    }

    private String safeRedirectLocation(ResponseEntity<String> response) {
        URI location = response == null ? null : response.getHeaders().getLocation();
        if (location == null) {
            return "";
        }
        String path = location.getPath();
        return StringUtils.hasText(path) ? " to " + path : "";
    }

    private SimpleClientHttpRequestFactory requestFactory(McpGatewayProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        return requestFactory;
    }

    private void applyMcpHeaders(HttpHeaders headers, McpSession session, McpRequestOptions options) {
        String protocolVersion = session != null && StringUtils.hasText(session.protocolVersion())
            ? session.protocolVersion()
            : options.protocolVersion();
        headers.set(MCP_PROTOCOL_VERSION_HEADER, protocolVersion);
        if (session != null && StringUtils.hasText(session.sessionId())) {
            headers.set(MCP_SESSION_ID_HEADER, session.sessionId());
        }
        options.headers().forEach((name, value) -> {
            if (StringUtils.hasText(name) && StringUtils.hasText(value)) {
                headers.set(name.trim(), value.trim());
            }
        });
    }

    private JsonNode readJsonRpcMessage(ResponseEntity<String> response) {
        String body = response == null ? null : response.getBody();
        if (!StringUtils.hasText(body)) {
            throw new ResponseStatusException(BAD_GATEWAY, "MCP server returned an empty response.");
        }
        String payload = isEventStream(response.getHeaders())
            ? firstSseDataPayload(body)
            : body.trim().startsWith("data:") ? firstSseDataPayload(body) : body.trim();
        if (!StringUtils.hasText(payload)) {
            throw new ResponseStatusException(BAD_GATEWAY, "MCP server returned an empty event stream.");
        }
        try {
            return objectMapper.readTree(payload);
        } catch (Exception ex) {
            throw new ResponseStatusException(BAD_GATEWAY, "MCP server returned invalid JSON-RPC.", ex);
        }
    }

    private boolean isEventStream(HttpHeaders headers) {
        MediaType contentType = headers == null ? null : headers.getContentType();
        return contentType != null && MediaType.TEXT_EVENT_STREAM.isCompatibleWith(contentType);
    }

    private String firstSseDataPayload(String body) {
        StringBuilder data = new StringBuilder();
        for (String line : body.split("\\R")) {
            if (line.startsWith("data:")) {
                String value = line.substring("data:".length()).trim();
                if (!"[DONE]".equals(value)) {
                    data.append(value);
                }
            } else if (line.isBlank() && !data.isEmpty()) {
                return data.toString();
            }
        }
        return data.toString();
    }

    private JsonNode requireResult(JsonNode message, String method) {
        if (message == null || !message.isObject()) {
            throw new ResponseStatusException(BAD_GATEWAY, "MCP " + method + " returned no JSON-RPC message.");
        }
        JsonNode error = message.path("error");
        if (error.isObject()) {
            String messageText = error.path("message").asText("MCP request failed.");
            throw new ResponseStatusException(BAD_GATEWAY, "MCP " + method + " failed: " + messageText);
        }
        JsonNode result = message.path("result");
        if (result.isMissingNode() || result.isNull()) {
            throw new ResponseStatusException(BAD_GATEWAY, "MCP " + method + " returned no result.");
        }
        return result;
    }

    public record McpSession(
        URI endpoint,
        String protocolVersion,
        String sessionId,
        JsonNode initializeResult
    ) {
    }

    public record McpRequestOptions(String protocolVersion, Map<String, String> headers) {
        public McpRequestOptions {
            protocolVersion = StringUtils.hasText(protocolVersion) ? protocolVersion.trim() : "2025-11-25";
            headers = headers == null ? Map.of() : Map.copyOf(headers);
        }

        public static McpRequestOptions none(String protocolVersion) {
            return new McpRequestOptions(protocolVersion, Map.of());
        }

        public static McpRequestOptions withHeaders(String protocolVersion, Map<String, String> headers) {
            return new McpRequestOptions(protocolVersion, headers);
        }
    }

    private record McpHttpResponse(JsonNode message, HttpHeaders headers) {
    }
}
