package com.ai.fabric.product.mcp.gateway.client;

import com.ai.fabric.product.mcp.gateway.config.McpGatewayProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.McpHttpClientTransportAuthorizationException;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpClientSession;
import io.modelcontextprotocol.spec.McpClientTransport;
import jakarta.annotation.PreDestroy;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;

@Component
public class McpStreamableHttpClient {

    public static final String MCP_SESSION_ID_HEADER = "MCP-Session-Id";
    public static final String MCP_PROTOCOL_VERSION_HEADER = "MCP-Protocol-Version";
    private static final Pattern HEADER_NAME = Pattern.compile("[!#$%&'*+.^_`|~0-9A-Za-z-]+");
    private static final Set<String> BLOCKED_OPTION_HEADER_NAMES = Set.of(
        "ACCEPT", "CONNECTION", "CONTENT-LENGTH", "CONTENT-TYPE", "COOKIE", "EXPECT",
        "FORWARDED", "HOST", "MCP-PROTOCOL-VERSION", "MCP-SESSION-ID", "ORIGIN", "REFERER",
        "SET-COOKIE", "TE", "TRAILER", "TRANSFER-ENCODING", "UPGRADE", "LAST-EVENT-ID",
        "PROXY-AUTHORIZATION", "PROXY-AUTHENTICATE", "PROXY-CONNECTION", "X-REAL-IP",
        "X-FORWARDED-FOR", "X-FORWARDED-HOST", "X-FORWARDED-PROTO", "X-MCP-GATEWAY-API-KEY",
        "X-BRIDGE-API-KEY", "X-PLATFORM-API-KEY", "X-PLATFORM-PUBLIC-API-KEY"
    );
    private static final Set<String> BUILTIN_ALLOWED_OPTION_HEADER_NAMES = Set.of("AUTHORIZATION", "SHOPIFY-BUYER-IP");
    // 2024-11-05 preserves existing JSON-only HTTP endpoints, not the separate HTTP+SSE adapter.
    private static final List<String> HTTP_PROTOCOL_VERSIONS = List.of("2025-11-25", "2025-06-18", "2024-11-05");
    private static final TypeRef<Object> RAW_RESULT = new TypeRef<>() {};

    private final ObjectMapper objectMapper;
    private final McpGatewayProperties properties;
    private final HttpClient httpClient;

    @Autowired
    public McpStreamableHttpClient(RestClient.Builder restClientBuilder,
                                   ObjectMapper objectMapper,
                                   McpGatewayProperties properties) {
        this(objectMapper, properties, new PinnedDnsHttpClient(properties));
    }

    protected McpStreamableHttpClient(ObjectMapper objectMapper, McpGatewayProperties properties, HttpClient httpClient) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.httpClient = httpClient;
    }

    public McpSession initialize(URI endpoint, McpRequestOptions options) {
        McpRequestOptions resolved = options == null ? McpRequestOptions.none(properties.protocolVersion()) : options;
        SdkSession sdk = open(endpoint, resolved);
        try {
            ObjectNode params = objectMapper.createObjectNode();
            params.put("protocolVersion", sdk.protocolVersion);
            params.set("capabilities", objectMapper.createObjectNode());
            params.putObject("clientInfo").put("name", "loomai-mcp-execution-gateway").put("version", "0.1.0");
            JsonNode result = request(sdk, "initialize", params, properties.readTimeout());
            String negotiated = result.path("protocolVersion").asText("");
            if (!sdk.supportedProtocolVersions.contains(negotiated)) {
                throw new ResponseStatusException(BAD_GATEWAY, "MCP server negotiated an unsupported protocol version.");
            }
            sdk.protocolVersion = negotiated;
            sdk.session.sendNotification("notifications/initialized", Map.of())
                .contextWrite(context -> context.put(McpAsyncClient.NEGOTIATED_PROTOCOL_VERSION, negotiated))
                .block(properties.readTimeout());
            return new McpSession(endpoint, negotiated, null, result, sdk);
        } catch (RuntimeException failure) {
            sdk.close();
            throw safeFailure(failure);
        }
    }

    public JsonNode toolsList(McpSession session, McpRequestOptions options) {
        return toolsList(session, null, null, options);
    }

    public JsonNode toolsList(McpSession session, JsonNode arguments, McpRequestOptions options) {
        return toolsList(session, null, arguments, options);
    }

    public JsonNode toolsList(McpSession session, String cursor, JsonNode arguments, McpRequestOptions options) {
        return toolsList(session, cursor, arguments, options, properties.readTimeout());
    }

    public JsonNode toolsList(McpSession session, String cursor, JsonNode arguments,
                              McpRequestOptions options, Duration remaining) {
        ObjectNode params = objectMapper.createObjectNode();
        if (cursor != null) {
            params.put("cursor", cursor);
        }
        // Existing Shopify UCP profiles require this extension, independently of MCP's pagination cursor.
        if (arguments != null && arguments.isObject() && !arguments.isEmpty()) {
            params.set("arguments", arguments);
        }
        return request(session.sdk(), "tools/list", params, remaining);
    }

    public JsonNode toolsCall(McpSession session, String toolName, JsonNode arguments, McpRequestOptions options) {
        return request(session.sdk(), "tools/call", callParams(toolName, arguments), properties.readTimeout());
    }

    public JsonNode toolsCall(URI endpoint, String toolName, JsonNode arguments, McpRequestOptions options) {
        // Checkout UCP's explicitly configured direct JSON-RPC route does not perform initialize.
        try (SdkSession sdk = open(endpoint, options == null ? McpRequestOptions.none(properties.protocolVersion()) : options)) {
            return request(sdk, "tools/call", callParams(toolName, arguments), properties.readTimeout());
        }
    }

    private ObjectNode callParams(String toolName, JsonNode arguments) {
        if (!StringUtils.hasText(toolName)) {
            throw new RequestConfigurationException("MCP toolName is required.");
        }
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", toolName.trim());
        params.set("arguments", arguments != null && arguments.isObject() ? arguments : objectMapper.createObjectNode());
        return params;
    }

    private JsonNode request(SdkSession sdk, String method, JsonNode params, Duration timeout) {
        if (sdk == null) {
            throw new RequestConfigurationException("MCP session is not initialized.");
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new RequestConfigurationException("MCP request deadline exceeded before dispatch.");
        }
        try {
            Object result = sdk.session.sendRequest(method, objectMapper.convertValue(params, Object.class), RAW_RESULT)
                .contextWrite(context -> context.put(McpAsyncClient.NEGOTIATED_PROTOCOL_VERSION, sdk.protocolVersion))
                .block(timeout.compareTo(properties.readTimeout()) < 0 ? timeout : properties.readTimeout());
            if (result == null) {
                throw new ResponseStatusException(BAD_GATEWAY, "MCP " + method + " returned no result.");
            }
            JsonNode resultNode = objectMapper.valueToTree(result);
            if (!resultNode.isObject()) {
                throw new ResponseStatusException(BAD_GATEWAY, "MCP " + method + " returned an invalid result envelope.");
            }
            return resultNode;
        } catch (RuntimeException failure) {
            throw safeFailure(failure);
        }
    }

    @SuppressWarnings("deprecation")
    private SdkSession open(URI endpoint, McpRequestOptions options) {
        if (endpoint == null || !StringUtils.hasText(endpoint.getHost())) {
            throw new RequestConfigurationException("MCP endpoint is required.");
        }
        HttpHeaders outbound = new HttpHeaders();
        if (options.headers().size() > properties.maxAuthHeaders()) {
            throw new RequestConfigurationException("MCP outbound header count exceeds the configured limit.");
        }
        options.headers().forEach((name, value) -> {
            String validated = validateOutboundHeaderName(name);
            if (outbound.containsKey(validated)) {
                throw new RequestConfigurationException("MCP outbound header is duplicated.");
            }
            outbound.set(validated, validateOutboundHeaderValue(value));
        });
        HttpRequest.Builder requests = HttpRequest.newBuilder().timeout(properties.readTimeout());
        outbound.forEach((name, values) -> requests.setHeader(name, values.get(0)));
        String base = endpoint.getScheme() + "://" + endpoint.getRawAuthority();
        String path = endpoint.getRawPath() + (endpoint.getRawQuery() == null ? "" : "?" + endpoint.getRawQuery());
        McpClientTransport transport;
        String protocolVersion = options.protocolVersion();
        List<String> supportedProtocolVersions;
        if ("HTTP_SSE".equals(options.transport())) {
            protocolVersion = "2024-11-05";
            supportedProtocolVersions = List.of(protocolVersion);
            transport = HttpClientSseClientTransport.builder(base)
                .sseEndpoint(path)
                .clientBuilder(sharedClientBuilder())
                .requestBuilder(requests)
                .jsonMapper(new JacksonMcpJsonMapper(objectMapper))
                .maxResponseSize(properties.maxResponseBytes())
                .build();
        } else if ("STREAMABLE_HTTP".equals(options.transport())) {
            int preferredVersion = HTTP_PROTOCOL_VERSIONS.indexOf(protocolVersion);
            if (preferredVersion < 0) {
                throw new RequestConfigurationException("MCP configured protocol version is not supported.");
            }
            supportedProtocolVersions = HTTP_PROTOCOL_VERSIONS.subList(preferredVersion, HTTP_PROTOCOL_VERSIONS.size());
            transport = HttpClientStreamableHttpTransport.builder(base)
                .endpoint(path)
                .clientBuilder(sharedClientBuilder())
                .requestBuilder(requests)
                .jsonMapper(new JacksonMcpJsonMapper(objectMapper))
                .supportedProtocolVersions(supportedProtocolVersions)
                .resumableStreams(false)
                .maxResponseSize(properties.maxResponseBytes())
                .build();
        } else {
            throw new RequestConfigurationException("MCP transport is not supported.");
        }
        // The SDK's default authorization handler has zero retries. Do not install refresh/replay handlers here.
        McpClientSession session = new McpClientSession(properties.readTimeout(), transport, Map.of(), Map.of(
            "notifications/progress", params -> Mono.empty(),
            "notifications/message", params -> Mono.empty(),
            "notifications/tools/list_changed", params -> Mono.empty()
        ), connection -> connection.timeout(properties.connectTimeout()));
        return new SdkSession(session, transport, protocolVersion, supportedProtocolVersions);
    }

    private HttpClient.Builder sharedClientBuilder() {
        HttpClient.Builder delegate = HttpClient.newBuilder();
        // The SDK accepts a builder, not an existing client. Reuse one host client instead of leaking a selector per action.
        return (HttpClient.Builder) Proxy.newProxyInstance(HttpClient.Builder.class.getClassLoader(),
            new Class<?>[]{HttpClient.Builder.class}, (proxy, method, args) -> {
                if ("build".equals(method.getName())) {
                    return httpClient;
                }
                try {
                    Object result = method.invoke(delegate, args);
                    return result == delegate ? proxy : result;
                } catch (InvocationTargetException failure) {
                    throw failure.getCause();
                }
            });
    }

    private ResponseStatusException safeFailure(RuntimeException failure) {
        if (failure instanceof ResponseStatusException response) {
            return response;
        }
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof PublicMcpAddressPolicy.DestinationRejectedException) {
                return new RequestConfigurationException("MCP destination was rejected before dispatch; use public HTTPS.");
            }
            if (cause instanceof McpHttpClientTransportAuthorizationException authorization) {
                return new UpstreamAuthException(authorization.getResponseInfo().statusCode(), failure);
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return new ResponseStatusException(BAD_GATEWAY, "MCP server request failed; no tool call was retried.", failure);
    }

    private String validateOutboundHeaderName(String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        String normalized = name.toUpperCase(Locale.ROOT);
        if (!StringUtils.hasText(name) || !HEADER_NAME.matcher(name).matches()
            || BLOCKED_OPTION_HEADER_NAMES.contains(normalized)
            || normalized.startsWith("X-FORWARDED-")
            || properties.internalApiKeyHeader().equalsIgnoreCase(name)) {
            throw new RequestConfigurationException("MCP outbound header is not allowed.");
        }
        boolean allowed = BUILTIN_ALLOWED_OPTION_HEADER_NAMES.contains(normalized)
            || properties.apiKeyHeaderAllowlist().stream().anyMatch(name::equalsIgnoreCase);
        if (!allowed) {
            throw new RequestConfigurationException("MCP outbound header is not allowlisted.");
        }
        return name;
    }

    private String validateOutboundHeaderValue(String rawValue) {
        String value = rawValue == null ? "" : rawValue.trim();
        if (!StringUtils.hasText(value)) {
            throw new RequestConfigurationException("MCP outbound header value is required.");
        }
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (ch == '\r' || ch == '\n' || ch == 0 || (Character.isISOControl(ch) && ch != '\t')) {
                throw new RequestConfigurationException("MCP outbound header value is invalid.");
            }
        }
        return value;
    }

    @PreDestroy
    public void close() {
        // HttpClient gained AutoCloseable in Java 21; the SDK itself also supports Java 17.
        if ((Object) httpClient instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception ignored) {
                // Host shutdown must not turn a completed action into a retry.
            }
        }
    }

    public static final class RequestConfigurationException extends ResponseStatusException {
        public RequestConfigurationException(String message) {
            super(BAD_GATEWAY, message);
        }
    }

    public static final class UpstreamAuthException extends ResponseStatusException {
        private final String errorCode;

        private UpstreamAuthException(int status, RuntimeException cause) {
            super(BAD_GATEWAY, status == 401 ? "MCP upstream authentication is required. No tool call was retried."
                : "MCP upstream access was denied. No tool call was retried.", cause);
            this.errorCode = status == 401 ? "MCP_UPSTREAM_AUTH_REQUIRED" : "MCP_UPSTREAM_ACCESS_DENIED";
        }

        public String errorCode() {
            return errorCode;
        }
    }

    public record McpSession(URI endpoint, String protocolVersion, String sessionId,
                             JsonNode initializeResult, SdkSession sdk) implements AutoCloseable {
        public McpSession(URI endpoint, String protocolVersion, String sessionId, JsonNode initializeResult) {
            this(endpoint, protocolVersion, sessionId, initializeResult, null);
        }

        @Override
        public void close() {
            if (sdk != null) {
                sdk.close();
            }
        }
    }

    public record McpRequestOptions(String protocolVersion, Map<String, String> headers, String transport) {
        public McpRequestOptions(String protocolVersion, Map<String, String> headers) {
            this(protocolVersion, headers, "STREAMABLE_HTTP");
        }

        public McpRequestOptions {
            protocolVersion = StringUtils.hasText(protocolVersion) ? protocolVersion.trim() : "2025-11-25";
            headers = headers == null ? Map.of() : Map.copyOf(headers);
            transport = StringUtils.hasText(transport) ? transport.trim().toUpperCase(Locale.ROOT) : "STREAMABLE_HTTP";
        }

        public static McpRequestOptions none(String protocolVersion) {
            return new McpRequestOptions(protocolVersion, Map.of());
        }

        public static McpRequestOptions withHeaders(String protocolVersion, Map<String, String> headers) {
            return new McpRequestOptions(protocolVersion, headers);
        }
    }

    public static final class SdkSession implements AutoCloseable {
        private final McpClientSession session;
        private final McpClientTransport transport;
        private String protocolVersion;
        private final List<String> supportedProtocolVersions;

        private SdkSession(McpClientSession session, McpClientTransport transport, String protocolVersion,
                           List<String> supportedProtocolVersions) {
            this.session = session;
            this.transport = transport;
            this.protocolVersion = protocolVersion;
            this.supportedProtocolVersions = List.copyOf(supportedProtocolVersions);
        }

        @Override
        public void close() {
            session.close();
            try {
                transport.closeGracefully().block(Duration.ofSeconds(1));
            } catch (RuntimeException ignored) {
                // Session cleanup must not replace an already observed tool outcome or cause a replay.
            }
        }
    }
}
