package com.ai.fabric.runtime.authz;

import com.ai.fabric.runtime.config.RuntimeAuthzProperties;
import com.ai.infrastructure.access.policy.EntityAccessPolicy;
import com.ai.infrastructure.intent.action.connector.AIActionConnectorProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Remote HTTP-backed {@link EntityAccessPolicy} for runtime product deployments.
 *
 * <p>Fail-closed behavior:
 * any error (misconfiguration, timeout, non-2xx, invalid JSON) results in {@code false}.</p>
 */
@Slf4j
public class RemoteHttpEntityAccessPolicy implements EntityAccessPolicy {

    private static final String CONTENT_TYPE_JSON = "application/json";

    private final ObjectMapper objectMapper;
    private final URI endpointUri;
    private final Duration timeout;
    private final HttpClient httpClient;
    private final Map<String, String> defaultHeaders;

    public RemoteHttpEntityAccessPolicy(RuntimeAuthzProperties properties,
                                        AIActionConnectorProperties actionConnectorProperties,
                                        ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");

        RuntimeAuthzProperties.Remote remote = properties != null ? properties.getRemote() : null;
        String baseUrl = remote != null ? remote.getBaseUrl() : null;
        if (!StringUtils.hasText(baseUrl) && actionConnectorProperties != null) {
            baseUrl = actionConnectorProperties.getBaseUrl();
        }
        String path = remote != null ? remote.getPath() : null;
        this.endpointUri = buildEndpointUri(baseUrl, path);

        int connectTimeoutMs = remote != null ? Math.max(100, remote.getConnectTimeoutMs()) : 500;
        this.timeout = Duration.ofMillis(remote != null ? Math.max(100, remote.getTimeoutMs()) : 1500);

        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(connectTimeoutMs))
            .build();

        this.defaultHeaders = buildDefaultHeaders(properties, actionConnectorProperties);
    }

    @Override
    public boolean canUserAccessEntity(String userId, Map<String, Object> entity) {
        if (!StringUtils.hasText(userId) || entity == null || endpointUri == null) {
            return false;
        }

        RemoteAuthzCheckRequest request = buildRequest(userId.trim(), entity);
        String json;
        try {
            json = objectMapper.writeValueAsString(request);
        } catch (Exception ex) {
            log.debug("Remote authz request serialization failed: {}", ex.getMessage());
            return false;
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder(endpointUri)
            .timeout(timeout)
            .header("Content-Type", CONTENT_TYPE_JSON)
            .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        defaultHeaders.forEach(builder::header);

        try {
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response != null ? response.statusCode() : 0;
            if (status < 200 || status >= 300) {
                return false;
            }
            String body = response.body();
            if (!StringUtils.hasText(body)) {
                return false;
            }
            RemoteAuthzCheckResponse parsed = objectMapper.readValue(body, RemoteAuthzCheckResponse.class);
            return parsed != null && Boolean.TRUE.equals(parsed.granted());
        } catch (Exception ex) {
            // Deny on timeout/unavailability/unparseable payload.
            log.debug("Remote authz call failed: {}", ex.getMessage());
            return false;
        }
    }

    @Override
    public void logAccessDenied(String userId, Map<String, Object> entity, String reason) {
        if (entity == null) {
            return;
        }
        String resourceId = Objects.toString(entity.get("resourceId"), "");
        String op = Objects.toString(entity.get("operationType"), "");
        log.debug("Access denied (remote authz) userId={}, resourceId={}, operationType={}, reason={}", userId, resourceId, op, reason);
    }

    private RemoteAuthzCheckRequest buildRequest(String userId, Map<String, Object> entity) {
        Map<String, Object> metadata = entity.get("metadata") instanceof Map<?, ?> raw ? toStringKeyMap(raw) : Map.of();
        Map<String, Object> userAttributes = entity.get("userAttributes") instanceof Map<?, ?> raw ? toStringKeyMap(raw) : Map.of();

        return new RemoteAuthzCheckRequest(
            Objects.toString(entity.get("requestId"), null),
            userId,
            Objects.toString(metadata.get("sessionId"), null),
            Objects.toString(entity.get("resourceId"), "UNKNOWN"),
            Objects.toString(entity.get("operationType"), "READ"),
            metadata,
            userAttributes
        );
    }

    private Map<String, String> buildDefaultHeaders(RuntimeAuthzProperties properties,
                                                    AIActionConnectorProperties actionConnectorProperties) {
        Map<String, String> headers = new LinkedHashMap<>();
        RuntimeAuthzProperties.OutboundAuth outbound = properties != null && properties.getRemote() != null ? properties.getRemote().getOutboundAuth() : null;
        RuntimeAuthzProperties.OutboundAuthType type = outbound != null ? outbound.getType() : RuntimeAuthzProperties.OutboundAuthType.INHERIT_ACTIONS_API_KEY;

        if (type == null || type == RuntimeAuthzProperties.OutboundAuthType.NONE) {
            return Map.of();
        }

        if (type == RuntimeAuthzProperties.OutboundAuthType.API_KEY) {
            String header = outbound != null ? outbound.getApiKeyHeader() : null;
            String value = outbound != null ? outbound.getApiKeyValue() : null;
            if (StringUtils.hasText(header) && StringUtils.hasText(value)) {
                headers.put(header.trim(), value.trim());
            }
            return Map.copyOf(headers);
        }

        // INHERIT_ACTIONS_API_KEY (default)
        if (actionConnectorProperties != null && actionConnectorProperties.getApiKey() != null) {
            String header = actionConnectorProperties.getApiKey().getHeader();
            String value = actionConnectorProperties.getApiKey().getValue();
            if (StringUtils.hasText(header) && StringUtils.hasText(value)) {
                headers.put(header.trim(), value.trim());
            }
        }
        return headers.isEmpty() ? Map.of() : Map.copyOf(headers);
    }

    private URI buildEndpointUri(String baseUrl, String path) {
        if (!StringUtils.hasText(baseUrl)) {
            log.warn("Remote authz is enabled but no base URL is configured (set ai.fabric.runtime.authz.remote.base-url or ai.actions.connector.base-url). Denying all access.");
            return null;
        }
        String base = baseUrl.trim();
        String p = StringUtils.hasText(path) ? path.trim() : "/api/authz/check";
        String joined = joinUrl(base, p);

        try {
            URI uri = URI.create(joined);
            String scheme = uri.getScheme();
            if (!StringUtils.hasText(scheme)) {
                throw new IllegalArgumentException("missing scheme");
            }
            String s = scheme.trim().toLowerCase();
            if (!"http".equals(s) && !"https".equals(s)) {
                throw new IllegalArgumentException("unsupported scheme '" + scheme + "'");
            }
            if (!StringUtils.hasText(uri.getHost())) {
                throw new IllegalArgumentException("missing host");
            }
            return uri;
        } catch (Exception ex) {
            log.warn("Invalid remote authz URL '{}': {}. Denying all access.", joined, ex.getMessage());
            return null;
        }
    }

    private String joinUrl(String baseUrl, String path) {
        String base = baseUrl;
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String p = path;
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        return base + p;
    }

    private Map<String, Object> toStringKeyMap(Map<?, ?> raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry == null || entry.getKey() == null) {
                continue;
            }
            out.put(entry.getKey().toString(), entry.getValue());
        }
        return out;
    }

    record RemoteAuthzCheckRequest(
        String requestId,
        String userId,
        String sessionId,
        String resourceId,
        String operationType,
        Map<String, Object> metadata,
        Map<String, Object> userAttributes
    ) { }

    record RemoteAuthzCheckResponse(Boolean granted, String reason, String policyVersion) { }
}

