package com.ai.infrastructure.connector.rest.util;

import com.ai.infrastructure.connector.rest.api.TraceContextDto;
import com.ai.infrastructure.connector.rest.api.VerifiedAuthContextDto;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class TraceContextSupport {

    private TraceContextSupport() {
    }

    public static Map<String, String> forwardHeaders(TraceContextDto trace) {
        Map<String, String> out = new LinkedHashMap<>();
        if (trace == null) {
            return out;
        }

        putIfText(out, "X-AIFABRIC-REQUEST-ID", trace.requestId());
        putIfText(out, "X-AIFABRIC-CONVERSATION-ID", trace.conversationId());

        VerifiedAuthContextDto auth = trace.authContext();
        if (auth != null && StringUtils.hasText(auth.subjectId())) {
            putIfText(out, "X-AIFABRIC-AUTH-SUBJECT-ID", auth.subjectId());
            putIfText(out, "X-AIFABRIC-AUTH-SUBJECT-TYPE", auth.subjectType());
            putIfText(out, "X-AIFABRIC-AUTH-MODE", auth.authMode());
            putIfText(out, "X-AIFABRIC-AUTH-CALLER-TYPE", auth.callerType());
            putIfText(out, "X-AIFABRIC-AUTH-SESSION-ID", effectiveSessionId(trace));
            putIfText(out, "X-AIFABRIC-AUTH-DEPLOYMENT-ID", auth.deploymentId());
            putIfText(out, "X-AIFABRIC-AUTH-CUSTOMER-ID", auth.customerId());
            putIfText(out, "X-AIFABRIC-AUTH-TENANT-ID", effectiveTenantId(trace));
            putIfText(out, "X-AIFABRIC-AUTH-ISSUER", auth.issuer());
            putIfText(out, "X-AIFABRIC-AUTH-EXPIRES-AT", auth.expiresAt());
            if (auth.grantedScopes() != null && !auth.grantedScopes().isEmpty()) {
                String scopes = auth.grantedScopes().stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .distinct()
                    .collect(Collectors.joining(","));
                putIfText(out, "X-AIFABRIC-AUTH-SCOPES", scopes);
            }
        }

        // Preserve legacy aliases for existing upstream templates and compatibility-only consumers.
        putIfText(out, "X-AIFABRIC-USER-ID", effectiveUserId(trace));
        putIfText(out, "X-AIFABRIC-SESSION-ID", effectiveSessionId(trace));
        return out;
    }

    public static Map<String, Object> templateMap(TraceContextDto trace) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (trace == null) {
            return out;
        }

        putIfTextObject(out, "requestId", trace.requestId());
        putIfTextObject(out, "conversationId", trace.conversationId());
        putIfTextObject(out, "userId", effectiveUserId(trace));
        putIfTextObject(out, "sessionId", effectiveSessionId(trace));
        putIfTextObject(out, "tenantId", effectiveTenantId(trace));

        VerifiedAuthContextDto auth = trace.authContext();
        if (auth != null && StringUtils.hasText(auth.subjectId())) {
            Map<String, Object> authMap = new LinkedHashMap<>();
            putIfTextObject(authMap, "subjectId", auth.subjectId());
            putIfTextObject(authMap, "subjectType", auth.subjectType());
            putIfTextObject(authMap, "authMode", auth.authMode());
            putIfTextObject(authMap, "callerType", auth.callerType());
            putIfTextObject(authMap, "sessionId", effectiveSessionId(trace));
            putIfTextObject(authMap, "deploymentId", auth.deploymentId());
            putIfTextObject(authMap, "customerId", auth.customerId());
            putIfTextObject(authMap, "tenantId", effectiveTenantId(trace));
            putIfTextObject(authMap, "issuer", auth.issuer());
            putIfTextObject(authMap, "expiresAt", auth.expiresAt());
            if (auth.grantedScopes() != null && !auth.grantedScopes().isEmpty()) {
                authMap.put(
                    "grantedScopes",
                    auth.grantedScopes().stream()
                        .filter(Objects::nonNull)
                        .map(Object::toString)
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .distinct()
                        .toList()
                );
            }
            out.put("authContext", Map.copyOf(authMap));
        }

        return out;
    }

    public static String effectiveUserId(TraceContextDto trace) {
        if (trace == null) {
            return null;
        }
        VerifiedAuthContextDto auth = trace.authContext();
        if (auth != null && StringUtils.hasText(auth.subjectId())
            && !"ANONYMOUS_SESSION".equalsIgnoreCase(auth.subjectType())) {
            return auth.subjectId().trim();
        }
        return StringUtils.hasText(trace.userId()) ? trace.userId().trim() : null;
    }

    public static String effectiveSessionId(TraceContextDto trace) {
        if (trace == null) {
            return null;
        }
        VerifiedAuthContextDto auth = trace.authContext();
        if (auth != null && StringUtils.hasText(auth.sessionId())) {
            return auth.sessionId().trim();
        }
        if (auth != null && StringUtils.hasText(auth.subjectId())
            && "ANONYMOUS_SESSION".equalsIgnoreCase(auth.subjectType())) {
            return auth.subjectId().trim();
        }
        return StringUtils.hasText(trace.sessionId()) ? trace.sessionId().trim() : null;
    }

    public static String effectiveTenantId(TraceContextDto trace) {
        if (trace == null) {
            return null;
        }
        VerifiedAuthContextDto auth = trace.authContext();
        if (auth != null && StringUtils.hasText(auth.tenantId())) {
            return auth.tenantId().trim();
        }
        return StringUtils.hasText(trace.tenantId()) ? trace.tenantId().trim() : null;
    }

    private static void putIfText(Map<String, String> out, String key, String value) {
        if (out == null || !StringUtils.hasText(key) || !StringUtils.hasText(value)) {
            return;
        }
        out.put(key.trim(), value.trim());
    }

    private static void putIfTextObject(Map<String, Object> out, String key, String value) {
        if (out == null || !StringUtils.hasText(key) || !StringUtils.hasText(value)) {
            return;
        }
        out.put(key.trim(), value.trim());
    }
}
