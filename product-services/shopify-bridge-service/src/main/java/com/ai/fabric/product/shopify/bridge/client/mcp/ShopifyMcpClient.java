package com.ai.fabric.product.shopify.bridge.client.mcp;

import com.ai.fabric.product.shopify.bridge.config.ShopifyStorefrontMcpProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;

@Component
public class ShopifyMcpClient {

    public static final String MCP_SESSION_ID_HEADER = "MCP-Session-Id";
    public static final String MCP_PROTOCOL_VERSION_HEADER = "MCP-Protocol-Version";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ShopifyStorefrontMcpProperties properties;
    private final AtomicLong requestIds = new AtomicLong(1);
    private final Map<String, StorefrontPasswordCookie> storefrontPasswordCookies = new ConcurrentHashMap<>();

    public ShopifyMcpClient(RestClient.Builder restClientBuilder,
                            ObjectMapper objectMapper,
                            ShopifyStorefrontMcpProperties properties) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public ShopifyMcpSession initialize(URI endpoint) {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("protocolVersion", properties.protocolVersion());
        params.set("capabilities", objectMapper.createObjectNode());
        ObjectNode clientInfo = params.putObject("clientInfo");
        clientInfo.put("name", "loomai-shopify-bridge");
        clientInfo.put("version", "0.1.0");

        McpHttpResponse response = postJsonRpc(endpoint, jsonRpcRequest("initialize", params), null);
        JsonNode result = requireResult(response.message(), "initialize");
        String negotiatedProtocol = result.path("protocolVersion").asText(properties.protocolVersion());
        ShopifyMcpSession session = new ShopifyMcpSession(
            endpoint,
            negotiatedProtocol,
            response.headers().getFirst(MCP_SESSION_ID_HEADER),
            result
        );
        postNotification(endpoint, "notifications/initialized", session);
        return session;
    }

    public JsonNode toolsList(URI endpoint) {
        return toolsList(new ShopifyMcpSession(endpoint, properties.protocolVersion(), null, null));
    }

    public JsonNode toolsList(URI endpoint, ShopifyMcpRequestOptions options) {
        McpHttpResponse response = postJsonRpc(
            endpoint,
            jsonRpcRequest("tools/list", objectMapper.createObjectNode()),
            new ShopifyMcpSession(endpoint, properties.protocolVersion(), null, null),
            false,
            options
        );
        return requireResult(response.message(), "tools/list");
    }

    public JsonNode toolsList(ShopifyMcpSession session) {
        McpHttpResponse response = postJsonRpc(session.endpoint(), jsonRpcRequest("tools/list", objectMapper.createObjectNode()), session);
        return requireResult(response.message(), "tools/list");
    }

    public JsonNode toolsCall(URI endpoint, String toolName, JsonNode arguments) {
        return toolsCall(new ShopifyMcpSession(endpoint, properties.protocolVersion(), null, null), toolName, arguments);
    }

    public JsonNode toolsCall(URI endpoint, String toolName, JsonNode arguments, ShopifyMcpRequestOptions options) {
        return toolsCall(new ShopifyMcpSession(endpoint, properties.protocolVersion(), null, null), toolName, arguments, options);
    }

    public JsonNode toolsCall(ShopifyMcpSession session, String toolName, JsonNode arguments) {
        return toolsCall(session, toolName, arguments, ShopifyMcpRequestOptions.none());
    }

    public JsonNode toolsCall(ShopifyMcpSession session,
                              String toolName,
                              JsonNode arguments,
                              ShopifyMcpRequestOptions options) {
        if (!StringUtils.hasText(toolName)) {
            throw new ResponseStatusException(BAD_GATEWAY, "MCP toolName is required.");
        }
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", toolName.trim());
        params.set("arguments", arguments != null && arguments.isObject() ? arguments : objectMapper.createObjectNode());
        McpHttpResponse response = postJsonRpc(session.endpoint(), jsonRpcRequest("tools/call", params), session, false, options);
        return requireResult(response.message(), "tools/call");
    }

    private void postNotification(URI endpoint, String method, ShopifyMcpSession session) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("jsonrpc", "2.0");
        body.put("method", method);
        postJsonRpc(endpoint, body, session, true);
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

    private McpHttpResponse postJsonRpc(URI endpoint, JsonNode body, ShopifyMcpSession session) {
        return postJsonRpc(endpoint, body, session, false);
    }

    private McpHttpResponse postJsonRpc(URI endpoint,
                                        JsonNode body,
                                        ShopifyMcpSession session,
                                        boolean notification) {
        return postJsonRpc(endpoint, body, session, notification, ShopifyMcpRequestOptions.none());
    }

    private McpHttpResponse postJsonRpc(URI endpoint,
                                        JsonNode body,
                                        ShopifyMcpSession session,
                                        boolean notification,
                                        ShopifyMcpRequestOptions options) {
        if (endpoint == null || !StringUtils.hasText(endpoint.toString())) {
            throw new ResponseStatusException(BAD_GATEWAY, "MCP endpoint is required.");
        }
        try {
            ResponseEntity<String> response = restClient.post()
                .uri(endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .headers(headers -> applyMcpHeaders(headers, session, endpoint, options))
                .body(body)
                .retrieve()
                .toEntity(String.class);
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
        }
    }

    private void applyMcpHeaders(HttpHeaders headers,
                                 ShopifyMcpSession session,
                                 URI endpoint,
                                 ShopifyMcpRequestOptions options) {
        headers.set(MCP_PROTOCOL_VERSION_HEADER, session != null && StringUtils.hasText(session.protocolVersion())
            ? session.protocolVersion()
            : properties.protocolVersion());
        if (session != null && StringUtils.hasText(session.sessionId())) {
            headers.set(MCP_SESSION_ID_HEADER, session.sessionId());
        }
        String cookieHeader = storefrontPasswordCookieHeader(endpoint);
        if (StringUtils.hasText(cookieHeader)) {
            headers.add(HttpHeaders.COOKIE, cookieHeader);
        }
        if (options != null && options.headers() != null) {
            options.headers().forEach((name, value) -> {
                if (StringUtils.hasText(name) && StringUtils.hasText(value)) {
                    headers.set(name.trim(), value.trim());
                }
            });
        }
    }

    private String storefrontPasswordCookieHeader(URI endpoint) {
        if (endpoint == null || !StringUtils.hasText(properties.storefrontPassword())) {
            return null;
        }
        String host = endpoint.getHost();
        if (!StringUtils.hasText(host)) {
            return null;
        }
        String cacheKey = storefrontPasswordCookieCacheKey(endpoint);
        Instant now = Instant.now();
        StorefrontPasswordCookie cached = storefrontPasswordCookies.get(cacheKey);
        if (cached != null && cached.expiresAt().isAfter(now) && StringUtils.hasText(cached.cookieHeader())) {
            return cached.cookieHeader();
        }

        URI passwordEndpoint = endpoint.resolve("/password");
        MultiValueMap<String, String> form = new RedactedStorefrontPasswordForm();
        form.add("form_type", "storefront_password");
        form.add("password", properties.storefrontPassword());
        try {
            ResponseEntity<String> response = restClient.post()
                .uri(passwordEndpoint)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .toEntity(String.class);
            String cookieHeader = cookieHeader(response.getHeaders().get(HttpHeaders.SET_COOKIE));
            if (StringUtils.hasText(cookieHeader)) {
                storefrontPasswordCookies.put(
                    cacheKey,
                    new StorefrontPasswordCookie(cookieHeader, now.plus(properties.storefrontPasswordCookieTtl()))
                );
                return cookieHeader;
            }
            return null;
        } catch (RestClientResponseException ex) {
            throw new ResponseStatusException(
                BAD_GATEWAY,
                "Shopify storefront password unlock returned HTTP " + ex.getStatusCode().value() + ".",
                ex
            );
        }
    }

    private String storefrontPasswordCookieCacheKey(URI endpoint) {
        String host = endpoint.getHost().trim().toLowerCase(Locale.ROOT);
        return endpoint.getPort() < 0 ? host : host + ":" + endpoint.getPort();
    }

    private String cookieHeader(List<String> setCookieHeaders) {
        if (setCookieHeaders == null || setCookieHeaders.isEmpty()) {
            return null;
        }
        Map<String, String> cookies = new LinkedHashMap<>();
        for (String setCookie : setCookieHeaders) {
            if (!StringUtils.hasText(setCookie)) {
                continue;
            }
            String nameValue = setCookie.split(";", 2)[0].trim();
            int equalsIndex = nameValue.indexOf('=');
            if (equalsIndex <= 0 || equalsIndex == nameValue.length() - 1) {
                continue;
            }
            cookies.put(nameValue.substring(0, equalsIndex), nameValue);
        }
        return cookies.isEmpty() ? null : String.join("; ", cookies.values());
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
                if ("[DONE]".equals(value)) {
                    continue;
                }
                data.append(value);
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

    public record ShopifyMcpSession(
        URI endpoint,
        String protocolVersion,
        String sessionId,
        JsonNode initializeResult
    ) {
    }

    public record ShopifyMcpRequestOptions(Map<String, String> headers) {
        public static ShopifyMcpRequestOptions none() {
            return new ShopifyMcpRequestOptions(Map.of());
        }

        public static ShopifyMcpRequestOptions authorization(String authorizationHeaderValue) {
            if (!StringUtils.hasText(authorizationHeaderValue)) {
                return none();
            }
            return new ShopifyMcpRequestOptions(Map.of(HttpHeaders.AUTHORIZATION, authorizationHeaderValue.trim()));
        }

        public static ShopifyMcpRequestOptions bearer(String token) {
            if (!StringUtils.hasText(token)) {
                return none();
            }
            String normalized = token.trim();
            return authorization(normalized.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())
                ? normalized
                : "Bearer " + normalized);
        }
    }

    private record McpHttpResponse(JsonNode message, HttpHeaders headers) {
    }

    private record StorefrontPasswordCookie(String cookieHeader, Instant expiresAt) {
    }

    private static final class RedactedStorefrontPasswordForm extends LinkedMultiValueMap<String, String> {
        @Override
        public String toString() {
            LinkedMultiValueMap<String, String> redacted = new LinkedMultiValueMap<>(this);
            if (redacted.containsKey("password")) {
                redacted.put("password", List.of("[REDACTED]"));
            }
            return redacted.toString();
        }
    }
}
