package com.ai.fabric.platform.backend.security;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RuntimePrivateAccessSupport {

    public static final String TRUSTED_BACKEND_SECRET_NAME = "AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY";
    public static final String TRUSTED_BACKEND_API_KEY_HEADER = "X-AIFABRIC-RUNTIME-API-KEY";
    public static final String PRIVATE_AUTHORIZATION_HEADER = RuntimePrivateAssertionSigningService.AUTHORIZATION_HEADER;

    public static final String SCOPE_RUNTIME_ADMIN_OVERVIEW = "runtime:admin:overview";
    public static final String SCOPE_RUNTIME_AUTH_OVERVIEW = "runtime:admin:auth-overview";
    public static final String SCOPE_RUNTIME_ACTIONS_OVERVIEW = "runtime:admin:actions";
    public static final String SCOPE_RUNTIME_WEBHOOKS_OVERVIEW = "runtime:admin:webhooks";
    public static final String SCOPE_RUNTIME_WEBHOOKS_RETRY = "runtime:admin:webhooks:retry";
    public static final String SCOPE_RUNTIME_INDEXING_OVERVIEW = "runtime:index:overview";
    public static final String SCOPE_RUNTIME_INDEXING_VECTORS = "runtime:index:vectors";
    public static final String SCOPE_RUNTIME_CONNECTOR_READ = "runtime:connector:read";
    public static final String SCOPE_RUNTIME_MIGRATION_CLEAR = "runtime:migration:clear";

    private RuntimePrivateAccessSupport() {
    }

    public static List<String> adminReadScopes() {
        return List.of(
            SCOPE_RUNTIME_ADMIN_OVERVIEW,
            SCOPE_RUNTIME_AUTH_OVERVIEW,
            SCOPE_RUNTIME_ACTIONS_OVERVIEW,
            SCOPE_RUNTIME_WEBHOOKS_OVERVIEW,
            SCOPE_RUNTIME_INDEXING_OVERVIEW,
            SCOPE_RUNTIME_INDEXING_VECTORS,
            SCOPE_RUNTIME_CONNECTOR_READ
        );
    }

    public static boolean isConfigured(PlatformSecretService platformSecretService, ObjectMapper objectMapper) {
        String trustedBackendApiKey = trimToNull(platformSecretService.resolveSecret(TRUSTED_BACKEND_SECRET_NAME));
        RuntimePrivateAssertionSigningService signingService =
            new RuntimePrivateAssertionSigningService(platformSecretService, objectMapper);
        return StringUtils.hasText(trustedBackendApiKey) && signingService.isConfigured();
    }

    public static Map<String, String> issuePlatformProxyHeaders(PlatformSecretService platformSecretService,
                                                                ObjectMapper objectMapper,
                                                                DeploymentEntity deployment,
                                                                String purpose,
                                                                List<String> grantedScopes,
                                                                Duration ttl) {
        PlatformPrincipal principal = PlatformSecurityContext.currentPrincipal();
        String subjectId = principal != null && StringUtils.hasText(principal.actorId())
            ? principal.actorId().trim()
            : "platform-system";
        String subjectType = principal == null ? "SYSTEM_PROCESS" : "INTERNAL_PLATFORM_USER";
        String authenticationMode = principal == null
            ? "SYSTEM"
            : normalizeValue(principal.authenticationMode(), "SESSION");
        String normalizedPurpose = normalizeValue(purpose, "platform-proxy");
        String sessionSeed = subjectId + "|" + safeDeploymentId(deployment) + "|" + authenticationMode + "|" + normalizedPurpose;
        String issuer = principal == null
            ? "platform-runtime:" + normalizedPurpose
            : "platform-runtime:" + authenticationMode;
        RuntimePrivateAssertionSigningService.RuntimePrivateAssertionClaims claims =
            new RuntimePrivateAssertionSigningService.RuntimePrivateAssertionClaims(
                subjectId,
                subjectType,
                "PLATFORM_PROXY_SESSION",
                "PLATFORM_PROXY",
                "platform-runtime-" + safeDeploymentId(deployment) + "-" + shortSha(sessionSeed),
                safeDeploymentId(deployment),
                trimToNull(deployment == null ? null : deployment.getCustomerId()),
                trimToNull(deployment == null ? null : deployment.getTenantId()),
                issuer,
                Instant.now().plus(ttl == null ? Duration.ofMinutes(10) : ttl),
                List.of(safeDeploymentId(deployment)),
                normalizeScopes(grantedScopes)
            );
        return issueHeaders(platformSecretService, objectMapper, claims);
    }

    public static Map<String, String> issueSystemHeaders(PlatformSecretService platformSecretService,
                                                         ObjectMapper objectMapper,
                                                         DeploymentEntity deployment,
                                                         String subjectId,
                                                         String sessionId,
                                                         String issuer,
                                                         List<String> grantedScopes,
                                                         Duration ttl) {
        RuntimePrivateAssertionSigningService.RuntimePrivateAssertionClaims claims =
            new RuntimePrivateAssertionSigningService.RuntimePrivateAssertionClaims(
                normalizeValue(subjectId, "platform-system"),
                "SYSTEM_PROCESS",
                "PRIVATE_RUNTIME_BACKEND_MEDIATED",
                "TRUSTED_BACKEND",
                normalizeValue(sessionId, "runtime-system-" + safeDeploymentId(deployment)),
                safeDeploymentId(deployment),
                trimToNull(deployment == null ? null : deployment.getCustomerId()),
                trimToNull(deployment == null ? null : deployment.getTenantId()),
                normalizeValue(issuer, "platform-runtime-system"),
                Instant.now().plus(ttl == null ? Duration.ofMinutes(10) : ttl),
                List.of(safeDeploymentId(deployment)),
                normalizeScopes(grantedScopes)
            );
        return issueHeaders(platformSecretService, objectMapper, claims);
    }

    public static Map<String, String> issueHeaders(PlatformSecretService platformSecretService,
                                                   ObjectMapper objectMapper,
                                                   RuntimePrivateAssertionSigningService.RuntimePrivateAssertionClaims claims) {
        String trustedBackendApiKey = trimToNull(platformSecretService.resolveSecret(TRUSTED_BACKEND_SECRET_NAME));
        RuntimePrivateAssertionSigningService signingService =
            new RuntimePrivateAssertionSigningService(platformSecretService, objectMapper);
        if (!StringUtils.hasText(trustedBackendApiKey) || !signingService.isConfigured()) {
            return Map.of();
        }
        String authorization = signingService.toAuthorizationHeaderValue(claims);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(TRUSTED_BACKEND_API_KEY_HEADER, trustedBackendApiKey);
        headers.put(PRIVATE_AUTHORIZATION_HEADER, authorization);
        return Map.copyOf(headers);
    }

    private static List<String> normalizeScopes(List<String> grantedScopes) {
        if (grantedScopes == null || grantedScopes.isEmpty()) {
            return List.of();
        }
        return grantedScopes.stream()
            .filter(StringUtils::hasText)
            .map(String::trim)
            .distinct()
            .toList();
    }

    private static String safeDeploymentId(DeploymentEntity deployment) {
        return normalizeValue(deployment == null ? null : deployment.getId(), "unknown-deployment");
    }

    private static String normalizeValue(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String shortSha(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < 12 && i < hash.length; i++) {
                builder.append(String.format("%02x", hash[i]));
            }
            return builder.toString();
        } catch (Exception ex) {
            return Integer.toHexString(value.hashCode());
        }
    }
}
