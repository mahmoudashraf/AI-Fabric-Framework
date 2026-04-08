package com.ai.fabric.runtime.authz;

import com.ai.fabric.runtime.config.RuntimeAuthzProperties;
import com.ai.infrastructure.access.policy.EntityAccessPolicy;
import com.ai.infrastructure.intent.orchestration.OrchestrationContextMetadataKeys;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
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
    private static final String AUTHZ_CONTRACT_VERSION = "AUTH_CONTEXT_V1";

    private final ObjectMapper objectMapper;
    private final URI endpointUri;
    private final Duration timeout;
    private final HttpClient httpClient;
    private final Map<String, String> defaultHeaders;

    public RemoteHttpEntityAccessPolicy(RuntimeAuthzProperties properties,
                                        ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");

        RuntimeAuthzProperties.Remote remote = properties != null ? properties.getRemote() : null;
        String baseUrl = remote != null ? remote.getBaseUrl() : null;
        String path = remote != null ? remote.getPath() : null;
        this.endpointUri = buildEndpointUri(baseUrl, path);

        int connectTimeoutMs = remote != null ? Math.max(100, remote.getConnectTimeoutMs()) : 500;
        this.timeout = Duration.ofMillis(remote != null ? Math.max(100, remote.getTimeoutMs()) : 1500);

        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(connectTimeoutMs))
            .build();

        this.defaultHeaders = buildDefaultHeaders(properties);
    }

    @Override
    public boolean canUserAccessEntity(String userId, Map<String, Object> entity) {
        if (entity == null || endpointUri == null) {
            return false;
        }

        Map<String, Object> metadata = entity.get("metadata") instanceof Map<?, ?> raw ? toStringKeyMap(raw) : Map.of();
        String effectiveSubjectId = resolveEffectiveSubjectId(userId, metadata);
        if (!StringUtils.hasText(effectiveSubjectId)) {
            return false;
        }

        RemoteAuthzCheckRequest request = buildRequest(userId, effectiveSubjectId, metadata, entity);
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
        } catch (InterruptedException ex) {
            // Preserve the interrupt flag for cooperative cancellation.
            Thread.currentThread().interrupt();
            log.debug("Remote authz call interrupted: {}", ex.getMessage());
            return false;
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
        Map<String, Object> metadata = entity.get("metadata") instanceof Map<?, ?> raw ? toStringKeyMap(raw) : Map.of();
        String effectiveSubjectId = resolveEffectiveSubjectId(userId, metadata);
        String resourceId = Objects.toString(entity.get("resourceId"), "");
        String op = Objects.toString(entity.get("operationType"), "");
        log.debug("Access denied (remote authz) subjectId={}, resourceId={}, operationType={}, reason={}",
            effectiveSubjectId, resourceId, op, reason);
    }

    private RemoteAuthzCheckRequest buildRequest(String userId,
                                                 String effectiveSubjectId,
                                                 Map<String, Object> metadata,
                                                 Map<String, Object> entity) {
        Map<String, Object> userAttributes = entity.get("userAttributes") instanceof Map<?, ?> raw ? toStringKeyMap(raw) : Map.of();
        List<String> grantedScopes = toStringList(metadata.get(OrchestrationContextMetadataKeys.GRANTED_SCOPES));
        String subjectType = Objects.toString(metadata.get(OrchestrationContextMetadataKeys.SUBJECT_TYPE), null);
        String authMode = Objects.toString(metadata.get(OrchestrationContextMetadataKeys.AUTH_MODE), null);
        String callerType = Objects.toString(metadata.get(OrchestrationContextMetadataKeys.CALLER_TYPE), null);
        String sessionId = resolveEffectiveSessionId(metadata, effectiveSubjectId, subjectType);
        String deploymentId = Objects.toString(metadata.get(OrchestrationContextMetadataKeys.DEPLOYMENT_ID), null);
        String customerId = Objects.toString(metadata.get(OrchestrationContextMetadataKeys.CUSTOMER_ID), null);
        String tenantId = Objects.toString(metadata.get(OrchestrationContextMetadataKeys.TENANT_ID), null);
        String issuer = Objects.toString(metadata.get(OrchestrationContextMetadataKeys.AUTH_ISSUER), null);
        String compatibilityUserId = StringUtils.hasText(userId) ? userId.trim() : effectiveSubjectId;
        String compatibilitySessionId = resolveCompatibilitySessionId(metadata, sessionId);
        List<String> audiences = toStringList(metadata.get(OrchestrationContextMetadataKeys.AUTH_AUDIENCES));
        String expiresAt = Objects.toString(metadata.get(OrchestrationContextMetadataKeys.AUTH_EXPIRES_AT), null);

        return new RemoteAuthzCheckRequest(
            AUTHZ_CONTRACT_VERSION,
            Objects.toString(entity.get("requestId"), null),
            compatibilityUserId,
            effectiveSubjectId,
            subjectType,
            authMode,
            callerType,
            sessionId,
            deploymentId,
            customerId,
            tenantId,
            issuer,
            grantedScopes,
            Objects.toString(entity.get("resourceId"), "UNKNOWN"),
            Objects.toString(entity.get("operationType"), "READ"),
            metadata,
            userAttributes,
            new RemoteAuthzCompatibilityAliases(
                compatibilityUserId,
                compatibilitySessionId
            ),
            new RemoteAuthzVerifiedAuthContext(
                effectiveSubjectId,
                subjectType,
                authMode,
                callerType,
                sessionId,
                deploymentId,
                customerId,
                tenantId,
                issuer,
                grantedScopes,
                audiences,
                expiresAt
            )
        );
    }

    private Map<String, String> buildDefaultHeaders(RuntimeAuthzProperties properties) {
        Map<String, String> headers = new LinkedHashMap<>();
        RuntimeAuthzProperties.OutboundAuth outbound = properties != null && properties.getRemote() != null ? properties.getRemote().getOutboundAuth() : null;
        RuntimeAuthzProperties.OutboundAuthType type = outbound != null ? outbound.getType() : RuntimeAuthzProperties.OutboundAuthType.INHERIT_ACTIONS_API_KEY;

        if (type == null || type == RuntimeAuthzProperties.OutboundAuthType.NONE) {
            return Map.of();
        }

        String header = outbound != null ? outbound.getApiKeyHeader() : null;
        String value = outbound != null ? outbound.getApiKeyValue() : null;
        if (StringUtils.hasText(header) && StringUtils.hasText(value)) {
            headers.put(header.trim(), value.trim());
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

    private String resolveEffectiveSubjectId(String userId, Map<String, Object> metadata) {
        if (StringUtils.hasText(userId)) {
            return userId.trim();
        }
        Object metadataSubjectId = metadata.get(OrchestrationContextMetadataKeys.SUBJECT_ID);
        return metadataSubjectId != null && StringUtils.hasText(metadataSubjectId.toString())
            ? metadataSubjectId.toString().trim()
            : null;
    }

    private List<String> toStringList(Object value) {
        if (!(value instanceof Iterable<?> iterable)) {
            return List.of();
        }
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        for (Object item : iterable) {
            if (item == null) {
                continue;
            }
            String trimmed = item.toString().trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out.isEmpty() ? List.of() : List.copyOf(out);
    }

    private String resolveEffectiveSessionId(Map<String, Object> metadata,
                                             String effectiveSubjectId,
                                             String subjectType) {
        Object rawSessionId = metadata.get("sessionId");
        if (rawSessionId != null && StringUtils.hasText(rawSessionId.toString())) {
            return rawSessionId.toString().trim();
        }
        if ("ANONYMOUS_SESSION".equalsIgnoreCase(subjectType) && StringUtils.hasText(effectiveSubjectId)) {
            return effectiveSubjectId.trim();
        }
        return null;
    }

    private String resolveCompatibilitySessionId(Map<String, Object> metadata, String effectiveSessionId) {
        if (StringUtils.hasText(effectiveSessionId)) {
            return effectiveSessionId.trim();
        }
        Object rawSessionId = metadata.get("sessionId");
        if (rawSessionId != null && StringUtils.hasText(rawSessionId.toString())) {
            return rawSessionId.toString().trim();
        }
        return null;
    }

    record RemoteAuthzCheckRequest(
        String contractVersion,
        String requestId,
        String userId,
        String subjectId,
        String subjectType,
        String authMode,
        String callerType,
        String sessionId,
        String deploymentId,
        String customerId,
        String tenantId,
        String issuer,
        List<String> grantedScopes,
        String resourceId,
        String operationType,
        Map<String, Object> metadata,
        Map<String, Object> userAttributes,
        RemoteAuthzCompatibilityAliases compatibilityAliases,
        RemoteAuthzVerifiedAuthContext authContext
    ) { }

    record RemoteAuthzCompatibilityAliases(
        String userId,
        String sessionId
    ) { }

    record RemoteAuthzVerifiedAuthContext(
        String subjectId,
        String subjectType,
        String authMode,
        String callerType,
        String sessionId,
        String deploymentId,
        String customerId,
        String tenantId,
        String issuer,
        List<String> grantedScopes,
        List<String> audiences,
        String expiresAt
    ) { }

    record RemoteAuthzCheckResponse(Boolean granted, String reason, String policyVersion) { }
}
