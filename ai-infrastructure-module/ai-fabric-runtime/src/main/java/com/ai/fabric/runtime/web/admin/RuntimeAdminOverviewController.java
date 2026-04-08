package com.ai.fabric.runtime.web.admin;

import com.ai.fabric.runtime.admin.RuntimeActionCatalogGateway;
import com.ai.fabric.runtime.auth.RuntimeRequestAuthResolver;
import com.ai.fabric.runtime.config.RuntimeAuthProperties;
import com.ai.fabric.runtime.config.RuntimeAuthStartupValidator;
import com.ai.infrastructure.config.AIEntityConfigurationLoader;
import com.ai.infrastructure.intent.action.AIActionMetaData;
import com.ai.infrastructure.intent.action.AIActionRegistry;
import com.ai.infrastructure.rag.VectorDatabaseService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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

    private final AIActionRegistry actionRegistry;
    private final RuntimeActionCatalogGateway actionCatalogGateway;
    private final AIEntityConfigurationLoader entityConfigurationLoader;
    private final VectorDatabaseService vectorDatabaseService;
    private final RuntimeAuthProperties runtimeAuthProperties;
    private final RuntimeRequestAuthResolver runtimeRequestAuthResolver;

    @Value("${ai.config.default-file:ai-entity-config.yml}")
    private String entityConfigLocation;

    @Value("${ai.prompts.deployment.config-file:}")
    private String promptConfigLocation;

    @GetMapping("/overview")
    public ResponseEntity<?> overview(HttpServletRequest httpRequest) {
        authorize(httpRequest, RuntimeAdminScopeCatalog.RUNTIME_ADMIN_OVERVIEW, "/api/admin/overview");

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
        authorize(httpRequest, RuntimeAdminScopeCatalog.RUNTIME_AUTH_OVERVIEW, "/api/admin/auth/overview");
        return ResponseEntity.ok(buildAuthOverviewBody(runtimeAuthProperties));
    }

    private void authorize(HttpServletRequest request, String scope, String surface) {
        runtimeRequestAuthResolver.requireScope(
            runtimeRequestAuthResolver.resolveVerifiedPrivateContext(request, surface),
            scope,
            surface
        );
    }

    private static Map<String, Object> buildAuthOverviewBody(RuntimeAuthProperties properties) {
        Map<String, Object> body = new LinkedHashMap<>();
        Map<String, Object> auth = authDiagnostics(properties);
        List<String> errors = authErrors(properties);
        List<String> warnings = authWarnings(properties);
        body.put("success", true);
        body.put("contractVersion", "RUNTIME_AUTH_OVERVIEW_V1");
        body.put("auth", auth);
        body.put("errors", errors);
        body.put("errorCount", errors.size());
        body.put("warnings", warnings);
        body.put("warningCount", warnings.size());
        body.put("guidance",
            errors.isEmpty()
                    && Boolean.TRUE.equals(auth.get("trustedBackendConfigured"))
                    && Boolean.TRUE.equals(auth.get("privateAssertionValidationConfigured"))
                ? "Runtime auth posture is configured for signed private assertions and public/browser tokens where enabled. Prefer /api/chat/me/* and runtime-backed admin surfaces."
                : "Runtime auth posture is not production-ready. Resolve the listed auth errors before starting or exposing the runtime.");
        return body;
    }

    private static Map<String, Object> authDiagnostics(RuntimeAuthProperties properties) {
        Map<String, Object> out = new LinkedHashMap<>();
        RuntimeAuthProperties.Ingress ingress = properties != null ? properties.getIngress() : new RuntimeAuthProperties.Ingress();
        RuntimeAuthProperties.PublicTokens publicTokens = properties != null ? properties.getPublicTokens() : new RuntimeAuthProperties.PublicTokens();
        RuntimeAuthProperties.PrivateAssertions privateAssertions = ingress.getPrivateAssertions();

        Map<String, Object> bootstrap = new LinkedHashMap<>();
        bootstrap.put("enabled", publicTokens.getBootstrap().isEnabled());
        bootstrap.put("allowMissingOrigin", publicTokens.getBootstrap().isAllowMissingOrigin());
        bootstrap.put("allowedOrigins", List.copyOf(publicTokens.getBootstrap().getAllowedOrigins()));
        bootstrap.put("maxRequestsPerWindow", publicTokens.getBootstrap().getMaxRequestsPerWindow());
        bootstrap.put("rateLimitWindowSeconds", publicTokens.getBootstrap().getRateLimitWindowSeconds());

        out.put("ingressMode", ingress.getMode() != null ? ingress.getMode().name() : null);
        out.put("verifiedContextRequired", true);
        out.put("rejectConflictingRequestIdentity", ingress.isRejectConflictingRequestIdentity());
        out.put("rejectRequestIdentityWhenVerifiedContextPresent", ingress.isRejectRequestIdentityWhenVerifiedContextPresent());
        out.put("trustedBackendHeader", ingress.getTrustedBackend().getApiKeyHeader());
        out.put("trustedBackendConfigured", StringUtils.hasText(ingress.getTrustedBackend().getApiKeyValue()));
        out.put("privateAssertionAuthorizationHeader", privateAssertions.getAuthorizationHeader());
        out.put("privateAssertionTokenScheme", privateAssertions.getTokenScheme());
        out.put("privateAssertionValidationConfigured", StringUtils.hasText(privateAssertions.getSigningKey()));
        out.put("privateAssertionAcceptedIssuers", List.copyOf(ingress.getAcceptedIssuers()));
        out.put("privateAssertionAcceptedAudiences", List.copyOf(ingress.getAcceptedAudiences()));
        out.put("privateAssertionIssuerPolicyConfigured", ingress.getAcceptedIssuers().stream().anyMatch(StringUtils::hasText));
        out.put("privateAssertionAudiencePolicyConfigured", ingress.getAcceptedAudiences().stream().anyMatch(StringUtils::hasText));
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
        out.put("supportedChatEndpoints", List.of(
            "/api/chat/me/query",
            "/api/chat/me/suggestions",
            "/api/chat/me/auth-context",
            "/api/chat/me/conversations",
            "/api/chat/me/conversations/{conversationId}"
        ));
        return out;
    }

    private static List<String> authWarnings(RuntimeAuthProperties properties) {
        return new RuntimeAuthStartupValidator(properties).validationWarnings();
    }

    private static List<String> authErrors(RuntimeAuthProperties properties) {
        return new RuntimeAuthStartupValidator(properties).validationErrors();
    }
}
