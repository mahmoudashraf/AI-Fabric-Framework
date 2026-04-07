package com.ai.fabric.runtime.web.admin;

import com.ai.fabric.runtime.admin.RuntimeActionCatalogGateway;
import com.ai.fabric.runtime.auth.RuntimeLegacyIdentityContract;
import com.ai.fabric.runtime.config.RuntimeAuthProperties;
import com.ai.fabric.runtime.config.RuntimeAuthStartupValidator;
import com.ai.infrastructure.config.AIEntityConfigurationLoader;
import com.ai.infrastructure.intent.action.AIActionMetaData;
import com.ai.infrastructure.intent.action.AIActionRegistry;
import com.ai.infrastructure.rag.VectorDatabaseService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class RuntimeAdminOverviewController {

    private static final Logger log = LoggerFactory.getLogger(RuntimeAdminOverviewController.class);

    private final AIActionRegistry actionRegistry;
    private final RuntimeActionCatalogGateway actionCatalogGateway;
    private final AIEntityConfigurationLoader entityConfigurationLoader;
    private final VectorDatabaseService vectorDatabaseService;
    private final RuntimeAuthProperties runtimeAuthProperties;

    @Value("${ai.config.default-file:ai-entity-config.yml}")
    private String entityConfigLocation;

    @Value("${ai.prompts.deployment.config-file:}")
    private String promptConfigLocation;

    @Value("${app.admin.api-key:}")
    private String adminApiKey;

    @Value("${app.admin.api-key-header:X-ADMIN-API-KEY}")
    private String adminApiKeyHeader;

    @GetMapping("/overview")
    public ResponseEntity<?> overview(HttpServletRequest httpRequest) {
        if (!AdminAuth.isAuthorized(adminApiKey, adminApiKeyHeader, httpRequest)) {
            log.warn("Unauthorized admin request: path=/api/admin/overview remoteAddr={}",
                httpRequest != null ? httpRequest.getRemoteAddr() : "unknown");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "success", false,
                "message", "Unauthorized"
            ));
        }

        List<AIActionMetaData> actions = actionRegistry != null ? actionRegistry.getAllMetadata() : List.of();
        long actionCount = actions.stream()
            .filter(action -> action != null && StringUtils.hasText(action.getName()))
            .count();

        Set<String> entityTypes = entityConfigurationLoader != null
            ? entityConfigurationLoader.getSupportedEntityTypes()
            : Set.of();

        List<Map<String, Object>> sources = actionCatalogGateway != null
            ? actionCatalogGateway.getSources().stream()
                .map(source -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("type", source.getType());
                    item.put("path", source.getPath());
                    item.put("optional", source.isOptional());
                    return item;
                })
                .toList()
            : List.of();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("entityConfigLocation", entityConfigLocation);
        body.put("promptConfigLocation", promptConfigLocation);
        body.put("actionCatalogSources", sources);
        body.put("actionsCount", actionCount);
        body.put("supportedEntityTypes", entityTypes);
        body.put("vectorDb", vectorDatabaseService.getClass().getSimpleName());
        body.put("supportsVectorScan", vectorDatabaseService.supportsVectorScan());
        body.put("vectorScope", vectorDatabaseService.adminDiagnostics());
        body.put("auth", authDiagnostics(runtimeAuthProperties));
        body.put("authWarnings", authWarnings(runtimeAuthProperties));
        return ResponseEntity.ok(body);
    }

    @GetMapping("/auth/overview")
    public ResponseEntity<?> authOverview(HttpServletRequest httpRequest) {
        if (!AdminAuth.isAuthorized(adminApiKey, adminApiKeyHeader, httpRequest)) {
            log.warn("Unauthorized admin request: path=/api/admin/auth/overview remoteAddr={}",
                httpRequest != null ? httpRequest.getRemoteAddr() : "unknown");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "success", false,
                "message", "Unauthorized"
            ));
        }
        return ResponseEntity.ok(buildAuthOverviewBody(runtimeAuthProperties));
    }

    private static Map<String, Object> buildAuthOverviewBody(RuntimeAuthProperties properties) {
        Map<String, Object> body = new LinkedHashMap<>();
        Map<String, Object> auth = authDiagnostics(properties);
        List<String> warnings = authWarnings(properties);
        body.put("success", true);
        body.put("contractVersion", "RUNTIME_AUTH_OVERVIEW_V1");
        body.put("auth", auth);
        body.put("warnings", warnings);
        body.put("warningCount", warnings.size());
        body.put("legacyIdentityDeprecated", true);
        body.put("guidance",
            Boolean.TRUE.equals(auth.get("trustedBackendConfigured"))
                ? "Runtime auth posture is configured for verified context. Prefer /api/chat/me/* and runtime-backed admin surfaces."
                : "Runtime auth posture still lacks a configured trusted backend secret. Verified private-runtime callers will not succeed until the trusted backend credential is provisioned.");
        return body;
    }

    private static Map<String, Object> authDiagnostics(RuntimeAuthProperties properties) {
        Map<String, Object> out = new LinkedHashMap<>();
        RuntimeAuthProperties.Ingress ingress = properties != null ? properties.getIngress() : new RuntimeAuthProperties.Ingress();
        RuntimeAuthProperties.PublicTokens publicTokens = properties != null ? properties.getPublicTokens() : new RuntimeAuthProperties.PublicTokens();

        Map<String, Object> verifiedHeaders = new LinkedHashMap<>();
        RuntimeAuthProperties.Headers headers = ingress.getHeaders();
        verifiedHeaders.put("subjectId", headers.getSubjectId());
        verifiedHeaders.put("subjectType", headers.getSubjectType());
        verifiedHeaders.put("authMode", headers.getAuthMode());
        verifiedHeaders.put("callerType", headers.getCallerType());
        verifiedHeaders.put("sessionId", headers.getSessionId());
        verifiedHeaders.put("deploymentId", headers.getDeploymentId());
        verifiedHeaders.put("customerId", headers.getCustomerId());
        verifiedHeaders.put("tenantId", headers.getTenantId());
        verifiedHeaders.put("issuer", headers.getIssuer());
        verifiedHeaders.put("expiresAt", headers.getExpiresAt());
        verifiedHeaders.put("scopes", headers.getScopes());
        verifiedHeaders.put("audiences", headers.getAudiences());

        Map<String, Object> bootstrap = new LinkedHashMap<>();
        bootstrap.put("enabled", publicTokens.getBootstrap().isEnabled());
        bootstrap.put("allowMissingOrigin", publicTokens.getBootstrap().isAllowMissingOrigin());
        bootstrap.put("allowedOrigins", List.copyOf(publicTokens.getBootstrap().getAllowedOrigins()));
        bootstrap.put("maxRequestsPerWindow", publicTokens.getBootstrap().getMaxRequestsPerWindow());
        bootstrap.put("rateLimitWindowSeconds", publicTokens.getBootstrap().getRateLimitWindowSeconds());

        out.put("ingressMode", ingress.getMode() != null ? ingress.getMode().name() : null);
        out.put("legacyRequestIdentityEnabled", ingress.isLegacyRequestIdentityEnabled());
        out.put("logLegacyRequestIdentity", ingress.isLogLegacyRequestIdentity());
        out.put("rejectConflictingRequestIdentity", ingress.isRejectConflictingRequestIdentity());
        out.put("rejectRequestIdentityWhenVerifiedContextPresent", ingress.isRejectRequestIdentityWhenVerifiedContextPresent());
        out.put("trustedBackendHeader", ingress.getTrustedBackend().getApiKeyHeader());
        out.put("trustedBackendConfigured", StringUtils.hasText(ingress.getTrustedBackend().getApiKeyValue()));
        out.put("verifiedContextAcceptedIssuers", List.copyOf(ingress.getAcceptedIssuers()));
        out.put("verifiedContextAcceptedAudiences", List.copyOf(ingress.getAcceptedAudiences()));
        out.put("verifiedContextIssuerPolicyConfigured", ingress.getAcceptedIssuers().stream().anyMatch(StringUtils::hasText));
        out.put("verifiedContextAudiencePolicyConfigured", ingress.getAcceptedAudiences().stream().anyMatch(StringUtils::hasText));
        out.put("verifiedContextHeaders", verifiedHeaders);
        out.put("publicTokenValidationConfigured", StringUtils.hasText(publicTokens.getSigningKey()));
        out.put("publicAuthorizationHeader", publicTokens.getAuthorizationHeader());
        out.put("publicTokenScheme", publicTokens.getTokenScheme());
        out.put("publicTokenIssuer", publicTokens.getIssuer());
        out.put("publicAcceptedIssuers", List.copyOf(publicTokens.getAcceptedIssuers()));
        out.put("publicAcceptedAudiences", List.copyOf(publicTokens.getAcceptedAudiences()));
        out.put("publicDefaultAudience", publicTokens.getDefaultAudience());
        out.put("publicTokenTtlSeconds", publicTokens.getTtlSeconds());
        out.put("publicAnonymousGrantedScopes", List.copyOf(publicTokens.getAnonymousGrantedScopes()));
        out.put("publicAuthenticatedDefaultScopes", List.copyOf(publicTokens.getAuthenticatedDefaultScopes()));
        out.put("publicAuthenticatedAllowedScopes", List.copyOf(publicTokens.getAuthenticatedAllowedScopes()));
        out.put("publicAnonymousConversationHistoryAllowed", publicTokens.getAnonymousGrantedScopes().contains("chat:conversations"));
        out.put("publicAuthenticatedConversationHistoryAllowed", publicTokens.getAuthenticatedAllowedScopes().contains("chat:conversations"));
        out.put("publicBootstrap", bootstrap);
        out.put("legacyIdentityMigration", legacyIdentityMigrationDiagnostics(ingress));
        return out;
    }

    private static List<String> authWarnings(RuntimeAuthProperties properties) {
        return new RuntimeAuthStartupValidator(properties).validationWarnings();
    }

    private static Map<String, Object> legacyIdentityMigrationDiagnostics(RuntimeAuthProperties.Ingress ingress) {
        Map<String, Object> out = new LinkedHashMap<>();
        boolean legacyEnabled = ingress != null && ingress.isLegacyRequestIdentityEnabled();
        out.put("deprecated", true);
        out.put("legacyRequestIdentityEnabled", legacyEnabled);
        out.put("rejectRequestIdentityWhenVerifiedContextPresent", ingress != null && ingress.isRejectRequestIdentityWhenVerifiedContextPresent());
        out.put("sunset", RuntimeLegacyIdentityContract.LEGACY_ENDPOINT_SUNSET);
        out.put("successorPaths", RuntimeLegacyIdentityContract.successorPaths());
        out.put(
            "guidance",
            legacyEnabled
                ? "Legacy chat identity compatibility is still enabled. Migrate callers to verified /api/chat/me/* endpoints before the sunset date."
                : (ingress != null && ingress.isRejectRequestIdentityWhenVerifiedContextPresent()
                    ? "Legacy chat identity compatibility is disabled for ingress resolution and verified callers may not send request identity aliases. Keep callers on verified /api/chat/me/* endpoints and remove compatibility traffic before the sunset date."
                    : "Legacy chat identity compatibility is disabled for ingress resolution. Keep callers on verified /api/chat/me/* endpoints and remove compatibility traffic before the sunset date.")
        );
        return out;
    }
}
