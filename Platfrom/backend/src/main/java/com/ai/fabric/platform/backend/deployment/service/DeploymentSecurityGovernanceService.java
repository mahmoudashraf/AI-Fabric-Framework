package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.config.PlatformProvisioningProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentDraftEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSecurityGovernanceAreaSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSecurityGovernanceCheckSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSecurityGovernanceSummary;
import com.ai.fabric.platform.backend.secret.model.PlatformSecretSummary;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DeploymentSecurityGovernanceService {

    private static final Pattern SECRET_REFERENCE_PATTERN = Pattern.compile("^\\$\\{(?:(?:secret:)?)([A-Z0-9_]+)}$");

    private final PlatformSecretService platformSecretService;
    private final PlatformProvisioningProperties provisioningProperties;
    private final ObjectMapper objectMapper;

    public DeploymentSecurityGovernanceService(PlatformSecretService platformSecretService,
                                               PlatformProvisioningProperties provisioningProperties,
                                               ObjectMapper objectMapper) {
        this.platformSecretService = platformSecretService;
        this.provisioningProperties = provisioningProperties;
        this.objectMapper = objectMapper;
    }

    public DeploymentSecurityGovernanceSummary build(DeploymentEntity deployment, DeploymentDraftEntity draft) {
        JsonNode routingConfig = readJson(draft.getRoutingConfigJson());
        JsonNode securityConfig = readJson(draft.getSecurityConfigJson());
        Map<String, PlatformSecretSummary> secretCatalog = platformSecretService.listSecrets().stream()
            .collect(LinkedHashMap::new, (map, item) -> map.put(item.name(), item), Map::putAll);

        List<DeploymentSecurityGovernanceAreaSummary> areas = List.of(
            runtimeAdminArea(deployment, securityConfig, secretCatalog),
            connectorAccessArea(routingConfig, securityConfig, secretCatalog),
            upstreamArea(deployment, routingConfig, securityConfig, secretCatalog),
            corsArea(deployment, securityConfig)
        );

        int blockedCount = areas.stream().mapToInt(DeploymentSecurityGovernanceAreaSummary::blockedCount).sum();
        int warningCount = areas.stream().mapToInt(DeploymentSecurityGovernanceAreaSummary::warningCount).sum();
        String summaryMessage;
        if (blockedCount > 0) {
            summaryMessage = blockedCount + " governance control(s) are blocked and should be fixed before production rollout.";
        } else if (warningCount > 0) {
            summaryMessage = warningCount + " governance control(s) need review before production rollout.";
        } else {
            summaryMessage = "Deployment auth, upstream, and CORS posture currently matches the platform governance guide.";
        }

        return new DeploymentSecurityGovernanceSummary(
            deployment.getId(),
            deployment.getName(),
            deployment.getEnvironmentName(),
            areas,
            blockedCount,
            warningCount,
            summaryMessage
        );
    }

    private DeploymentSecurityGovernanceAreaSummary runtimeAdminArea(DeploymentEntity deployment,
                                                                     JsonNode securityConfig,
                                                                     Map<String, PlatformSecretSummary> secretCatalog) {
        boolean adminEnabled = securityConfig.path("adminApiKeyEnabled").asBoolean(false);
        boolean adminSecretPresent = platformSecretService.isSecretPresent("APP_ADMIN_API_KEY");
        boolean trustedBackendSecretPresent = platformSecretService.isSecretPresent("AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY");
        boolean publicTokenSigningKeyPresent = platformSecretService.isSecretPresent("AI_FABRIC_RUNTIME_PUBLIC_TOKEN_SIGNING_KEY");
        boolean publicBootstrapEnabled = ManagedDeploymentProfileCatalog.publicRuntimeBootstrapEnabled(securityConfig);
        boolean runtimeLive = hasText(deployment.getRuntimeBaseUrl());
        boolean connectorLive = hasText(deployment.getConnectorBaseUrl());
        String liveSummary = joinLiveUrls(
            deployment.getRuntimeBaseUrl(),
            deployment.getConnectorBaseUrl()
        );

        List<DeploymentSecurityGovernanceCheckSummary> checks = List.of(
            check(
                "adminProtection",
                "Admin API protection",
                adminEnabled ? "READY" : "BLOCKED",
                adminEnabled ? "Enabled" : "Disabled",
                adminEnabled
                    ? "Runtime and runtime-backed connector admin APIs are configured to require a shared admin key."
                    : "Admin API key protection is disabled for platform-managed admin surfaces.",
                "Keep admin API protection enabled for production deployments."
            ),
            check(
                "adminSecret",
                "Admin API secret",
                adminEnabled && !adminSecretPresent ? "BLOCKED" : adminSecretPresent ? "READY" : "WARNING",
                secretValueSummary(secretCatalog.get("APP_ADMIN_API_KEY")),
                adminEnabled && !adminSecretPresent
                    ? "APP_ADMIN_API_KEY is required before runtime and runtime-backed connector admin endpoints can be governed safely."
                    : adminSecretPresent
                        ? "APP_ADMIN_API_KEY is available in the platform secret store."
                        : "Admin protection is disabled, so APP_ADMIN_API_KEY is currently unused.",
                "Rotate APP_ADMIN_API_KEY in the platform secret store and re-apply the deployment when it changes."
            ),
            check(
                "trustedBackendCallerAuth",
                "Trusted backend caller auth",
                trustedBackendSecretPresent ? "READY" : "WARNING",
                secretValueSummary(secretCatalog.get("AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY")),
                trustedBackendSecretPresent
                    ? "Trusted private-runtime callers can authenticate when they send verified auth context headers."
                    : "Trusted backend caller authentication is not configured, so private runtime callers cannot yet rely on verified auth context enforcement.",
                "Configure AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY and re-apply the deployment before switching private runtime callers to verified auth context mode."
            ),
            check(
                "publicRuntimeTokenValidation",
                "Public runtime token validation",
                publicTokenSigningKeyPresent ? "READY" : "WARNING",
                secretValueSummary(secretCatalog.get("AI_FABRIC_RUNTIME_PUBLIC_TOKEN_SIGNING_KEY")),
                publicTokenSigningKeyPresent
                    ? "Signed public browser tokens can be validated when deployments opt into public-runtime mode."
                    : "Public runtime token validation is not configured, so signed public-browser mode is unavailable.",
                "Configure AI_FABRIC_RUNTIME_PUBLIC_TOKEN_SIGNING_KEY before enabling signed public-runtime access."
            ),
            check(
                "publicRuntimeBootstrap",
                "Anonymous public bootstrap",
                publicBootstrapEnabled
                    ? publicTokenSigningKeyPresent ? "WARNING" : "BLOCKED"
                    : "READY",
                publicBootstrapEnabled ? "Enabled in draft security config" : "Disabled",
                publicBootstrapEnabled
                    ? publicTokenSigningKeyPresent
                        ? "Anonymous browser bootstrap is enabled. Keep origin allowlists, token TTL, and abuse controls tightened before exposing this mode."
                        : "Anonymous browser bootstrap is enabled in the draft, but the runtime public token signing key is missing."
                    : "Anonymous public browser bootstrap is disabled for this deployment.",
                "Only enable anonymous bootstrap for deliberate public browser integrations with explicit origin and abuse controls."
            ),
            check(
                "liveExposure",
                "Live admin surface",
                !runtimeLive && !connectorLive
                    ? "WARNING"
                    : adminEnabled && adminSecretPresent
                        ? "READY"
                        : "BLOCKED",
                liveSummary,
                !runtimeLive && !connectorLive
                    ? "No live runtime URL or internal connector service has been applied yet."
                    : adminEnabled && adminSecretPresent
                        ? "Live service exposure exists and admin protection is configured."
                        : "A live public service exists while admin protection is incomplete.",
                "Do not leave live admin APIs exposed without APP_ADMIN_API_KEY protection."
            )
        );

        return area("runtimeAdmin", "Runtime and admin exposure", checks);
    }

    private DeploymentSecurityGovernanceAreaSummary connectorAccessArea(JsonNode routingConfig,
                                                                        JsonNode securityConfig,
                                                                        Map<String, PlatformSecretSummary> secretCatalog) {
        JsonNode connector = routingConfig.path("connector");
        JsonNode inboundAuth = connector.path("inbound-auth");
        JsonNode apiKey = inboundAuth.path("api-key");
        boolean allowUnauthenticated = inboundAuth.path("allow-unauthenticated").asBoolean(false);
        boolean apiKeyEnabled = apiKey.path("enabled").asBoolean(false);
        String header = apiKey.path("header").asText("").trim();
        String value = apiKey.path("value").asText("").trim();
        String secretReference = referencedSecretName(value);
        PlatformSecretSummary referencedSecret = secretReference == null ? null : secretCatalog.get(secretReference);
        boolean connectorApiKeyEnabled = securityConfig.path("connectorApiKeyEnabled").asBoolean(false);

        List<DeploymentSecurityGovernanceCheckSummary> checks = List.of(
            check(
                "runtimeConnectorPolicy",
                "Runtime to connector auth policy",
                connectorApiKeyEnabled ? "READY" : "BLOCKED",
                connectorApiKeyEnabled ? "Enabled" : "Disabled",
                connectorApiKeyEnabled
                    ? "The draft expects runtime-to-connector calls to stay API-key protected."
                    : "connectorApiKeyEnabled is disabled in the deployment draft.",
                "Keep runtime-to-connector authentication enabled for platform-managed deployments."
            ),
            check(
                "connectorIngressMode",
                "Connector inbound auth mode",
                !allowUnauthenticated && apiKeyEnabled ? "READY" : "BLOCKED",
                !allowUnauthenticated && apiKeyEnabled ? "API key required" : "Unauthenticated or disabled",
                !allowUnauthenticated && apiKeyEnabled
                    ? "REST connector inbound traffic is protected by an API key."
                    : "REST connector inbound traffic is not fully protected.",
                "Set connector.inbound-auth.allow-unauthenticated=false and connector.inbound-auth.api-key.enabled=true."
            ),
            check(
                "connectorIngressHeader",
                "Connector inbound auth header",
                !allowUnauthenticated && apiKeyEnabled && !hasText(header)
                    ? "BLOCKED"
                    : "X-AIFABRIC-API-KEY".equals(header)
                        ? "READY"
                        : "WARNING",
                hasText(header) ? header : "Not configured",
                !hasText(header)
                    ? "No inbound auth header is configured for the REST connector."
                    : "X-AIFABRIC-API-KEY".equals(header)
                        ? "Connector inbound auth uses the standard operator header."
                        : "Connector inbound auth uses a non-standard header name.",
                "Prefer X-AIFABRIC-API-KEY for connector ingress to keep client and operator guidance consistent."
            ),
            check(
                "connectorIngressCredential",
                "Connector inbound auth credential",
                connectorCredentialStatus(allowUnauthenticated, apiKeyEnabled, value, referencedSecret),
                connectorCredentialSummary(value, referencedSecret),
                connectorCredentialMessage(allowUnauthenticated, apiKeyEnabled, value, referencedSecret),
                "Use a managed placeholder such as ${secret:CONNECTOR_API_KEY} instead of a literal credential."
            )
        );

        return area("connectorAccess", "Connector access posture", checks);
    }

    private DeploymentSecurityGovernanceAreaSummary upstreamArea(DeploymentEntity deployment,
                                                                 JsonNode routingConfig,
                                                                 JsonNode securityConfig,
                                                                 Map<String, PlatformSecretSummary> secretCatalog) {
        JsonNode connector = routingConfig.path("connector");
        JsonNode upstream = connector.path("upstream");
        JsonNode upstreamAuth = upstream.path("auth");

        String upstreamBaseUrl = upstream.path("base-url").asText("").trim();
        String authType = upstreamAuth.path("type").asText("NONE").trim().toUpperCase(Locale.ROOT);
        String authHeader = upstreamAuth.path("header").asText("").trim();
        String authValue = upstreamAuth.path("value").asText("").trim();
        String authSecretReference = referencedSecretName(authValue);
        PlatformSecretSummary upstreamSecret = authSecretReference == null ? null : secretCatalog.get(authSecretReference);
        String authzMode = securityConfig.path("authzMode").asText("").trim();
        String authzBaseUrl = securityConfig.path("authzBaseUrl").asText("").trim();

        List<DeploymentSecurityGovernanceCheckSummary> checks = List.of(
            check(
                "upstreamBaseUrl",
                "Upstream base URL",
                upstreamBaseUrlStatus(upstreamBaseUrl),
                hasText(upstreamBaseUrl) ? upstreamBaseUrl : "Not configured",
                upstreamBaseUrlMessage(upstreamBaseUrl),
                "Point connector.upstream.base-url at the real customer or store API, not a placeholder."
            ),
            check(
                "upstreamAuth",
                "Upstream auth posture",
                upstreamAuthStatus(authType, authHeader, authValue, upstreamSecret),
                upstreamAuthSummary(authType, authHeader, authValue, upstreamSecret),
                upstreamAuthMessage(authType, authHeader, authValue, upstreamSecret),
                "When upstream APIs are protected, provide a header and a managed secret placeholder instead of a literal token."
            ),
            check(
                "remoteAuthz",
                "Runtime authz upstream",
                remoteAuthzStatus(authzMode, authzBaseUrl),
                hasText(authzBaseUrl) ? authzBaseUrl : authzMode.isEmpty() ? "Not configured" : authzMode,
                remoteAuthzMessage(authzMode, authzBaseUrl),
                "REMOTE_HTTP requires a reachable AUTHZ_BASE_URL because runtime does not infer it from connector routing."
            ),
            check(
                "environmentMatch",
                "Upstream environment match",
                environmentMatchStatus(deployment.getEnvironmentName(), upstreamBaseUrl, authzBaseUrl),
                environmentMatchSummary(deployment.getEnvironmentName(), upstreamBaseUrl, authzBaseUrl),
                environmentMatchMessage(deployment.getEnvironmentName(), upstreamBaseUrl, authzBaseUrl),
                "Avoid local or placeholder upstream/authz URLs outside dev environments."
            )
        );

        return area("upstream", "Upstream and delegated authz", checks);
    }

    private DeploymentSecurityGovernanceAreaSummary corsArea(DeploymentEntity deployment, JsonNode securityConfig) {
        String allowedOrigins = effectiveValue(
            securityConfig.path("corsAllowedOrigins").asText("").trim(),
            provisioningProperties.corsAllowedOrigins()
        );
        String allowedPatterns = effectiveValue(
            securityConfig.path("corsAllowedOriginPatterns").asText("").trim(),
            provisioningProperties.corsAllowedOriginPatterns()
        );
        boolean allowCredentials = securityConfig.path("corsAllowCredentials").isBoolean()
            ? securityConfig.path("corsAllowCredentials").asBoolean()
            : provisioningProperties.corsAllowCredentials();
        List<String> origins = splitCsv(allowedOrigins);
        List<String> patterns = splitCsv(allowedPatterns);

        List<DeploymentSecurityGovernanceCheckSummary> checks = List.of(
            check(
                "allowlistCoverage",
                "Browser allowlist coverage",
                origins.isEmpty() && patterns.isEmpty() ? "BLOCKED" : "READY",
                describeCorsCoverage(origins, patterns),
                origins.isEmpty() && patterns.isEmpty()
                    ? "No exact origins or origin patterns are configured for browser access."
                    : "Browser access allowlists are configured for this deployment.",
                "Set exact origins for production clients and keep patterns narrow."
            ),
            check(
                "credentialsWildcard",
                "Credentials and wildcard safety",
                allowCredentials && containsWildcardOrigin(origins) ? "BLOCKED" : "READY",
                allowCredentials ? "Credentials allowed" : "Credentials blocked",
                allowCredentials && containsWildcardOrigin(origins)
                    ? "Wildcard exact origins cannot be combined with browser credentials."
                    : "Credentials posture is consistent with the configured exact origin allowlist.",
                "Do not combine corsAllowCredentials=true with '*' in corsAllowedOrigins."
            ),
            check(
                "localhostScope",
                "Localhost scope",
                !"dev".equalsIgnoreCase(deployment.getEnvironmentName()) && containsLocalhostEntry(origins)
                    ? "WARNING"
                    : "READY",
                origins.isEmpty() ? "No localhost origins" : String.join(", ", origins),
                !"dev".equalsIgnoreCase(deployment.getEnvironmentName()) && containsLocalhostEntry(origins)
                    ? "Non-dev deployment still allows localhost browser origins."
                    : "Localhost origins are not widening the current environment posture.",
                "Restrict localhost origins to development environments."
            ),
            check(
                "patternBreadth",
                "Origin pattern breadth",
                containsBroadWildcardPattern(patterns) ? "WARNING" : "READY",
                patterns.isEmpty() ? "No origin patterns" : String.join(", ", patterns),
                containsBroadWildcardPattern(patterns)
                    ? "Origin patterns include wildcard breadth and should be reviewed before production rollout."
                    : "Origin patterns stay within the expected breadth for the current deployment.",
                "Prefer exact origins first. Use origin patterns only where multi-tenant frontends genuinely require them."
            )
        );

        return area("cors", "Browser CORS posture", checks);
    }

    private DeploymentSecurityGovernanceAreaSummary area(String key,
                                                         String label,
                                                         List<DeploymentSecurityGovernanceCheckSummary> checks) {
        int blockedCount = (int) checks.stream().filter(check -> "BLOCKED".equals(check.status())).count();
        int warningCount = (int) checks.stream().filter(check -> "WARNING".equals(check.status())).count();
        String status = blockedCount > 0 ? "BLOCKED" : warningCount > 0 ? "WARNING" : "READY";
        String summaryMessage;
        if (blockedCount > 0) {
            summaryMessage = blockedCount + " blocked control(s) need action in " + label.toLowerCase(Locale.ROOT) + ".";
        } else if (warningCount > 0) {
            summaryMessage = warningCount + " warning control(s) should be reviewed in " + label.toLowerCase(Locale.ROOT) + ".";
        } else {
            summaryMessage = label + " matches the current platform governance guidance.";
        }
        return new DeploymentSecurityGovernanceAreaSummary(
            key,
            label,
            status,
            blockedCount,
            warningCount,
            checks,
            summaryMessage
        );
    }

    private DeploymentSecurityGovernanceCheckSummary check(String key,
                                                           String label,
                                                           String status,
                                                           String valueSummary,
                                                           String message,
                                                           String guidance) {
        return new DeploymentSecurityGovernanceCheckSummary(key, label, status, valueSummary, message, guidance);
    }

    private String connectorCredentialStatus(boolean allowUnauthenticated,
                                             boolean apiKeyEnabled,
                                             String value,
                                             PlatformSecretSummary referencedSecret) {
        if (allowUnauthenticated || !apiKeyEnabled) {
            return "WARNING";
        }
        if (!hasText(value)) {
            return "BLOCKED";
        }
        if (referencedSecret == null && referencedSecretName(value) == null) {
            return "BLOCKED";
        }
        if (referencedSecret == null) {
            return "WARNING";
        }
        return referencedSecret.present() ? "READY" : "BLOCKED";
    }

    private String connectorCredentialSummary(String value, PlatformSecretSummary referencedSecret) {
        if (!hasText(value)) {
            return "Not configured";
        }
        if (referencedSecret != null) {
            return "Managed placeholder: " + referencedSecret.name();
        }
        String secretReference = referencedSecretName(value);
        if (secretReference != null) {
            return "Unmanaged placeholder: " + secretReference;
        }
        return "Literal value detected";
    }

    private String connectorCredentialMessage(boolean allowUnauthenticated,
                                              boolean apiKeyEnabled,
                                              String value,
                                              PlatformSecretSummary referencedSecret) {
        if (allowUnauthenticated || !apiKeyEnabled) {
            return "Connector inbound API key auth is not active, so no governed credential is currently enforced.";
        }
        if (!hasText(value)) {
            return "Connector inbound auth requires a credential value or managed placeholder.";
        }
        if (referencedSecret != null) {
            return referencedSecret.present()
                ? referencedSecret.name() + " is present and can back the connector inbound auth credential."
                : referencedSecret.name() + " is referenced but missing from the platform secret store.";
        }
        String secretReference = referencedSecretName(value);
        if (secretReference != null) {
            return secretReference + " is referenced, but it is outside the managed platform secret catalog.";
        }
        return "A literal connector credential is stored in draft config.";
    }

    private String upstreamBaseUrlStatus(String upstreamBaseUrl) {
        if (!hasText(upstreamBaseUrl)) {
            return "WARNING";
        }
        if (!isValidHttpUrl(upstreamBaseUrl)) {
            return "BLOCKED";
        }
        if (isExampleUrl(upstreamBaseUrl)) {
            return "WARNING";
        }
        if (isLocalUrl(upstreamBaseUrl)) {
            return "WARNING";
        }
        return "READY";
    }

    private String upstreamBaseUrlMessage(String upstreamBaseUrl) {
        if (!hasText(upstreamBaseUrl)) {
            return "No upstream base URL is configured yet.";
        }
        if (!isValidHttpUrl(upstreamBaseUrl)) {
            return "The upstream base URL is not a valid absolute http(s) URL.";
        }
        if (isExampleUrl(upstreamBaseUrl)) {
            return "The upstream base URL still points at a placeholder example host.";
        }
        if (isLocalUrl(upstreamBaseUrl)) {
            return "The upstream base URL points at a local host and should be reviewed outside dev.";
        }
        return "The upstream base URL is set to a concrete reachable http(s) endpoint.";
    }

    private String upstreamAuthStatus(String authType,
                                      String authHeader,
                                      String authValue,
                                      PlatformSecretSummary upstreamSecret) {
        if ("NONE".equals(authType)) {
            return hasText(authValue) ? "WARNING" : "READY";
        }
        if (!hasText(authHeader) || !hasText(authValue)) {
            return "BLOCKED";
        }
        if (upstreamSecret != null) {
            return upstreamSecret.present() ? "READY" : "BLOCKED";
        }
        return referencedSecretName(authValue) != null ? "WARNING" : "BLOCKED";
    }

    private String upstreamAuthSummary(String authType,
                                       String authHeader,
                                       String authValue,
                                       PlatformSecretSummary upstreamSecret) {
        if ("NONE".equals(authType)) {
            return "NONE";
        }
        if (upstreamSecret != null) {
            return authType + " via " + authHeader + " using " + upstreamSecret.name();
        }
        String secretReference = referencedSecretName(authValue);
        if (secretReference != null) {
            return authType + " via " + authHeader + " using " + secretReference;
        }
        if (!hasText(authValue)) {
            return authType + " via " + authHeader;
        }
        return authType + " via " + authHeader + " using literal value";
    }

    private String upstreamAuthMessage(String authType,
                                       String authHeader,
                                       String authValue,
                                       PlatformSecretSummary upstreamSecret) {
        if ("NONE".equals(authType)) {
            return hasText(authValue)
                ? "upstream.auth.type is NONE but a value is still present in draft config."
                : "Connector calls the upstream API without an auth header.";
        }
        if (!hasText(authHeader) || !hasText(authValue)) {
            return "Protected upstream auth requires both a header name and a credential value.";
        }
        if (upstreamSecret != null) {
            return upstreamSecret.present()
                ? upstreamSecret.name() + " is available for upstream auth."
                : upstreamSecret.name() + " is referenced for upstream auth but missing from the platform secret store.";
        }
        String secretReference = referencedSecretName(authValue);
        if (secretReference != null) {
            return secretReference + " is referenced for upstream auth, but it is outside the managed platform secret catalog.";
        }
        return "A literal upstream auth credential is stored in draft config.";
    }

    private String remoteAuthzStatus(String authzMode, String authzBaseUrl) {
        if (!"REMOTE_HTTP".equalsIgnoreCase(authzMode)) {
            return "READY";
        }
        if (!hasText(authzBaseUrl) || !isValidHttpUrl(authzBaseUrl)) {
            return "BLOCKED";
        }
        if (isExampleUrl(authzBaseUrl) || isLocalUrl(authzBaseUrl)) {
            return "WARNING";
        }
        return "READY";
    }

    private String remoteAuthzMessage(String authzMode, String authzBaseUrl) {
        if (!"REMOTE_HTTP".equalsIgnoreCase(authzMode)) {
            return "Runtime authz mode does not depend on a remote HTTP authorization service.";
        }
        if (!hasText(authzBaseUrl)) {
            return "REMOTE_HTTP is enabled but authzBaseUrl is empty.";
        }
        if (!isValidHttpUrl(authzBaseUrl)) {
            return "authzBaseUrl must be a valid absolute http(s) URL.";
        }
        if (isExampleUrl(authzBaseUrl)) {
            return "authzBaseUrl still points at an example placeholder host.";
        }
        if (isLocalUrl(authzBaseUrl)) {
            return "authzBaseUrl points at a local host and should be reviewed outside dev.";
        }
        return "Runtime authz points at a concrete remote HTTP authorization service.";
    }

    private String environmentMatchStatus(String environmentName, String upstreamBaseUrl, String authzBaseUrl) {
        boolean prodLike = !"dev".equalsIgnoreCase(environmentName);
        if (!prodLike) {
            return "READY";
        }
        if (isLocalUrl(upstreamBaseUrl) || isLocalUrl(authzBaseUrl) || isExampleUrl(upstreamBaseUrl) || isExampleUrl(authzBaseUrl)) {
            return "WARNING";
        }
        return "READY";
    }

    private String environmentMatchSummary(String environmentName, String upstreamBaseUrl, String authzBaseUrl) {
        return "Env=" + environmentName
            + " • Upstream=" + (hasText(upstreamBaseUrl) ? upstreamBaseUrl : "none")
            + " • Authz=" + (hasText(authzBaseUrl) ? authzBaseUrl : "none");
    }

    private String environmentMatchMessage(String environmentName, String upstreamBaseUrl, String authzBaseUrl) {
        if ("dev".equalsIgnoreCase(environmentName)) {
            return "Development deployments may temporarily use local or placeholder upstream/authz URLs.";
        }
        if (isLocalUrl(upstreamBaseUrl) || isLocalUrl(authzBaseUrl)) {
            return "Non-dev deployment still points at a local upstream or authz URL.";
        }
        if (isExampleUrl(upstreamBaseUrl) || isExampleUrl(authzBaseUrl)) {
            return "Non-dev deployment still points at a placeholder upstream or authz URL.";
        }
        return "Upstream and authz targets are consistent with a non-dev deployment posture.";
    }

    private String effectiveValue(String primary, String fallback) {
        return hasText(primary) ? primary.trim() : fallback == null ? "" : fallback.trim();
    }

    private String describeCorsCoverage(List<String> origins, List<String> patterns) {
        return origins.size() + " exact origin(s) • " + patterns.size() + " origin pattern(s)";
    }

    private boolean containsWildcardOrigin(List<String> origins) {
        return origins.stream().anyMatch(origin -> "*".equals(origin.trim()));
    }

    private boolean containsLocalhostEntry(List<String> values) {
        return values.stream().anyMatch(this::isLocalEntry);
    }

    private boolean containsBroadWildcardPattern(List<String> patterns) {
        return patterns.stream().anyMatch(pattern -> {
            String normalized = pattern.trim().toLowerCase(Locale.ROOT);
            return normalized.contains("*") && (normalized.startsWith("http://") || normalized.startsWith("https://"));
        });
    }

    private boolean isLocalEntry(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.contains("localhost") || normalized.contains("127.0.0.1") || normalized.contains("0.0.0.0");
    }

    private String secretValueSummary(PlatformSecretSummary summary) {
        if (summary == null) {
            return "Not managed";
        }
        return summary.present() ? summary.source() : "Missing";
    }

    private String joinLiveUrls(String runtimeBaseUrl, String connectorBaseUrl) {
        List<String> values = new ArrayList<>();
        if (hasText(runtimeBaseUrl)) {
            values.add("Runtime");
        }
        if (hasText(connectorBaseUrl)) {
            values.add("Connector");
        }
        return values.isEmpty() ? "Not applied yet" : String.join(" + ", values);
    }

    private List<String> splitCsv(String value) {
        if (!hasText(value)) {
            return List.of();
        }
        return List.of(value.split(",")).stream()
            .map(String::trim)
            .filter(this::hasText)
            .toList();
    }

    private boolean isValidHttpUrl(String value) {
        if (!hasText(value)) {
            return false;
        }
        try {
            URI uri = URI.create(value.trim());
            return hasText(uri.getScheme())
                && hasText(uri.getHost())
                && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()));
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean isExampleUrl(String value) {
        if (!hasText(value)) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.contains(".example") || normalized.contains("example.com") || normalized.contains("customer-api.example");
    }

    private boolean isLocalUrl(String value) {
        if (!isValidHttpUrl(value)) {
            return false;
        }
        String host = URI.create(value.trim()).getHost();
        if (!hasText(host)) {
            return false;
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        return normalized.equals("localhost")
            || normalized.equals("0.0.0.0")
            || normalized.equals("127.0.0.1")
            || normalized.equals("::1");
    }

    private String referencedSecretName(String value) {
        if (!hasText(value)) {
            return null;
        }
        Matcher matcher = SECRET_REFERENCE_PATTERN.matcher(value.trim());
        if (!matcher.matches()) {
            return null;
        }
        return matcher.group(1);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private JsonNode readJson(String value) {
        try {
            return !hasText(value) ? objectMapper.createObjectNode() : objectMapper.readTree(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse deployment draft config JSON.", ex);
        }
    }
}
