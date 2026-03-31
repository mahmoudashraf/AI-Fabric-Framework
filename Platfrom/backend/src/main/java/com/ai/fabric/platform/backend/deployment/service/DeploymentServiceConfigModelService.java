package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentDraftEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentServiceConfigFieldSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentServiceConfigIssueSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentServiceConfigModelSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentServiceConfigSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSourceSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTemplateSummary;
import com.ai.fabric.platform.backend.deployment.model.DraftValidationIssue;
import com.ai.fabric.platform.backend.deployment.model.DraftValidationResponse;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class DeploymentServiceConfigModelService {

    private final DeploymentDraftValidationService deploymentDraftValidationService;
    private final PlatformSecretService platformSecretService;
    private final ObjectMapper objectMapper;

    public DeploymentServiceConfigModelService(DeploymentDraftValidationService deploymentDraftValidationService,
                                               PlatformSecretService platformSecretService,
                                               ObjectMapper objectMapper) {
        this.deploymentDraftValidationService = deploymentDraftValidationService;
        this.platformSecretService = platformSecretService;
        this.objectMapper = objectMapper;
    }

    public DeploymentServiceConfigModelSummary build(DeploymentEntity deployment,
                                                     DeploymentDraftEntity draft,
                                                     DeploymentVersionEntity latestPublishedVersion,
                                                     DeploymentTemplateSummary template,
                                                     DeploymentSourceSummary source) {
        JsonNode routingConfig = readJson(draft.getRoutingConfigJson());
        JsonNode providerConfig = readJson(draft.getProviderConfigJson());
        JsonNode securityConfig = readJson(draft.getSecurityConfigJson());
        DraftValidationResponse validation = deploymentDraftValidationService.validate(draft);

        List<DeploymentServiceConfigSummary> services = List.of(
            runtimeService(deployment, latestPublishedVersion, providerConfig, securityConfig, validation),
            restConnectorService(deployment, latestPublishedVersion, routingConfig, providerConfig, securityConfig, validation),
            uiSurfaceService(deployment, source, securityConfig, validation),
            upstreamService(routingConfig, securityConfig, validation),
            providerService(providerConfig, template, validation)
        );

        long blocked = services.stream().filter(service -> "BLOCKED".equals(service.status())).count();
        long warning = services.stream().filter(service -> "WARNING".equals(service.status())).count();
        String summaryMessage;
        if (blocked > 0) {
            summaryMessage = blocked + " service surface(s) are blocked and need operator action before production rollout.";
        } else if (warning > 0) {
            summaryMessage = warning + " service surface(s) are configured with warnings that should be reviewed before rollout.";
        } else {
            summaryMessage = "All modeled deployment services currently satisfy the required configuration checks.";
        }

        return new DeploymentServiceConfigModelSummary(
            deployment.getId(),
            deployment.getName(),
            deployment.getEnvironmentName(),
            services,
            summaryMessage
        );
    }

    private DeploymentServiceConfigSummary runtimeService(DeploymentEntity deployment,
                                                          DeploymentVersionEntity latestPublishedVersion,
                                                          JsonNode providerConfig,
                                                          JsonNode securityConfig,
                                                          DraftValidationResponse validation) {
        List<DeploymentServiceConfigFieldSummary> fields = List.of(
            field(
                "runtime.baseUrl",
                "Runtime base URL",
                blankOrValue(deployment.getRuntimeBaseUrl(), "Not applied yet"),
                true,
                hasText(deployment.getRuntimeBaseUrl()),
                "DEPLOYMENT_RELEASE",
                "Apply the deployment so the runtime service has a live public URL."
            ),
            field(
                "runtime.connectorBaseUrl",
                "Connector base URL",
                blankOrValue(deployment.getConnectorBaseUrl(), "Not applied yet"),
                true,
                hasText(deployment.getConnectorBaseUrl()),
                "DEPLOYMENT_RELEASE",
                "Runtime calls the REST connector for action execution and indexing sync."
            ),
            field(
                "runtime.publishedConfig",
                "Published config bundle",
                latestPublishedVersion == null ? "No published version yet" : latestPublishedVersion.getVersionLabel(),
                true,
                latestPublishedVersion != null,
                "DEPLOYMENT_VERSION",
                "Publish the current draft before expecting immutable runtime config artifacts."
            ),
            field(
                "runtime.authzBaseUrl",
                "Authz base URL",
                blankOrValue(securityConfig.path("authzBaseUrl").asText(""), "Not configured"),
                "REMOTE_HTTP".equalsIgnoreCase(securityConfig.path("authzMode").asText("")),
                hasText(securityConfig.path("authzBaseUrl").asText("")),
                "DRAFT_SECURITY",
                "Required when runtime authz mode depends on a remote HTTP service."
            ),
            field(
                "runtime.adminApiKey",
                "Admin API key",
                secretSummary("APP_ADMIN_API_KEY"),
                securityConfig.path("adminApiKeyEnabled").asBoolean(false),
                platformSecretService.isSecretPresent("APP_ADMIN_API_KEY"),
                "PLATFORM_SECRET",
                "Protects runtime admin endpoints and supports governed operations."
            ),
            field(
                "runtime.runtimeProfile",
                "Runtime profile",
                blankOrValue(providerConfig.path("runtimeProfile").asText(""), "Not configured"),
                true,
                hasText(providerConfig.path("runtimeProfile").asText("")),
                "DRAFT_PROVIDER",
                "Controls the runtime packaging profile used during deployment."
            )
        );

        return buildServiceSummary(
            "runtime",
            "Runtime service",
            "AI Fabric runtime responsible for inference, orchestration, search, and admin APIs.",
            deployment.getRuntimeBaseUrl(),
            fields,
            relevantIssues(validation, Set.of("security")),
            List.of()
        );
    }

    private DeploymentServiceConfigSummary restConnectorService(DeploymentEntity deployment,
                                                                DeploymentVersionEntity latestPublishedVersion,
                                                                JsonNode routingConfig,
                                                                JsonNode providerConfig,
                                                                JsonNode securityConfig,
                                                                DraftValidationResponse validation) {
        JsonNode connector = routingConfig.path("connector");
        JsonNode inboundAuth = connector.path("inbound-auth");
        JsonNode apiKey = inboundAuth.path("api-key");
        boolean allowUnauthenticated = inboundAuth.path("allow-unauthenticated").asBoolean(false);
        boolean apiKeyEnabled = apiKey.path("enabled").asBoolean(false);
        boolean requiresInboundCredential = !allowUnauthenticated && apiKeyEnabled;

        String proxySecretName = securityConfig.path("adminApiKeyEnabled").asBoolean(false)
            ? "APP_ADMIN_API_KEY"
            : "ACTIONS_CONNECTOR_API_KEY";

        List<DeploymentServiceConfigFieldSummary> fields = List.of(
            field(
                "rest.baseUrl",
                "REST connector base URL",
                blankOrValue(deployment.getConnectorBaseUrl(), "Not applied yet"),
                true,
                hasText(deployment.getConnectorBaseUrl()),
                "DEPLOYMENT_RELEASE",
                "Apply the deployment so the REST connector has a live public URL."
            ),
            field(
                "rest.routingConfig",
                "Published routing config",
                latestPublishedVersion == null ? "No published version yet" : latestPublishedVersion.getVersionLabel(),
                true,
                latestPublishedVersion != null,
                "DEPLOYMENT_VERSION",
                "Publish the current draft so the connector can load immutable routing configuration."
            ),
            field(
                "rest.upstreamBaseUrl",
                "Upstream base URL",
                blankOrValue(connector.path("upstream").path("base-url").asText(""), "Not configured"),
                requiresConnectorUpstream(routingConfig),
                hasText(connector.path("upstream").path("base-url").asText("")),
                "DRAFT_ROUTING",
                "Required for path-based routes or authz checks that depend on the upstream/store API."
            ),
            field(
                "rest.inboundAuthHeader",
                "Inbound auth header",
                blankOrValue(apiKey.path("header").asText(""), "Not configured"),
                requiresInboundCredential,
                hasText(apiKey.path("header").asText("")),
                "DRAFT_ROUTING",
                "Header name used to authenticate inbound REST connector requests."
            ),
            field(
                "rest.inboundAuthCredential",
                "Inbound auth credential",
                maskedDraftValue(apiKey.path("value").asText("")),
                requiresInboundCredential,
                hasText(apiKey.path("value").asText("")),
                "DRAFT_ROUTING",
                "Should normally be a secret placeholder rather than a literal value."
            ),
            field(
                "rest.runtimeProxyBaseUrl",
                "Runtime proxy base URL",
                blankOrValue(deployment.getRuntimeBaseUrl(), "Not applied yet"),
                true,
                hasText(deployment.getRuntimeBaseUrl()),
                "DEPLOYMENT_RELEASE",
                "REST admin and indexing proxies need the runtime public URL."
            ),
            field(
                "rest.runtimeProxyCredential",
                "Runtime proxy credential",
                secretSummary(proxySecretName),
                true,
                platformSecretService.isSecretPresent(proxySecretName),
                "PLATFORM_SECRET",
                "Used by the connector when it calls protected runtime endpoints."
            ),
            field(
                "rest.connectorProfile",
                "Connector profile",
                blankOrValue(providerConfig.path("connectorProfile").asText(""), "Not configured"),
                true,
                hasText(providerConfig.path("connectorProfile").asText("")),
                "DRAFT_PROVIDER",
                "Controls the connector packaging profile used during deployment."
            )
        );

        return buildServiceSummary(
            "restConnector",
            "REST connector",
            "Connector service exposing routed actions, admin operations, and runtime proxy flows.",
            deployment.getConnectorBaseUrl(),
            fields,
            relevantIssues(validation, Set.of("routing", "security")),
            List.of()
        );
    }

    private DeploymentServiceConfigSummary uiSurfaceService(DeploymentEntity deployment,
                                                            DeploymentSourceSummary source,
                                                            JsonNode securityConfig,
                                                            DraftValidationResponse validation) {
        String allowedOrigins = securityConfig.path("corsAllowedOrigins").asText("");
        String allowedPatterns = securityConfig.path("corsAllowedOriginPatterns").asText("");
        boolean hasCorsEntries = hasText(allowedOrigins) || hasText(allowedPatterns);

        List<DeploymentServiceConfigFieldSummary> fields = List.of(
            field(
                "ui.runtimeBaseUrl",
                "Runtime endpoint",
                blankOrValue(deployment.getRuntimeBaseUrl(), "Not applied yet"),
                true,
                hasText(deployment.getRuntimeBaseUrl()),
                "DEPLOYMENT_RELEASE",
                "Browser and operator clients need the runtime public URL."
            ),
            field(
                "ui.connectorBaseUrl",
                "REST connector endpoint",
                blankOrValue(deployment.getConnectorBaseUrl(), "Not applied yet"),
                true,
                hasText(deployment.getConnectorBaseUrl()),
                "DEPLOYMENT_RELEASE",
                "Browser and operator clients need the connector public URL for admin and sync flows."
            ),
            field(
                "ui.corsOrigins",
                "Allowed origins or patterns",
                hasCorsEntries
                    ? summarizeCorsEntries(allowedOrigins, allowedPatterns)
                    : "No browser origins configured",
                true,
                hasCorsEntries,
                "DRAFT_SECURITY",
                "At least one explicit origin or origin pattern should be configured for browser clients."
            ),
            field(
                "ui.corsCredentials",
                "CORS credentials",
                String.valueOf(securityConfig.path("corsAllowCredentials").asBoolean(false)),
                false,
                true,
                "DRAFT_SECURITY",
                "Controls whether browser credentials can be sent from allowed origins."
            ),
            field(
                "ui.releaseBranch",
                "Source branch",
                source.branch(),
                true,
                hasText(source.branch()),
                "DEPLOYMENT_SOURCE",
                "Useful for tracing which branch is producing deployment builds consumed by browser clients."
            )
        );

        return buildServiceSummary(
            "uiSurface",
            "UI and browser surface",
            "Operator and customer browser clients that call runtime and connector public endpoints.",
            null,
            fields,
            relevantIssues(validation, Set.of("security")),
            List.of()
        );
    }

    private DeploymentServiceConfigSummary upstreamService(JsonNode routingConfig,
                                                           JsonNode securityConfig,
                                                           DraftValidationResponse validation) {
        JsonNode connector = routingConfig.path("connector");
        JsonNode upstream = connector.path("upstream");
        JsonNode upstreamAuth = upstream.path("auth");
        JsonNode authz = routingConfig.path("authz");
        boolean authzEnabled = authz.path("enabled").asBoolean(false);
        String authType = upstreamAuth.path("type").asText("NONE").trim();
        boolean requiresUpstreamAuth = hasText(authType) && !"NONE".equalsIgnoreCase(authType);
        boolean requiresUpstreamBaseUrl = requiresConnectorUpstream(routingConfig);
        List<DeploymentServiceConfigIssueSummary> customIssues = new ArrayList<>();
        if (!requiresUpstreamBaseUrl && !hasConfiguredRoutes(routingConfig)) {
            customIssues.add(new DeploymentServiceConfigIssueSummary(
                "WARNING",
                "UPSTREAM_NOT_USED_YET",
                "$.connector.upstream.base-url",
                "No routed actions are configured yet, so the upstream/store surface is not active."
            ));
        }

        List<DeploymentServiceConfigFieldSummary> fields = List.of(
            field(
                "upstream.baseUrl",
                "Upstream base URL",
                blankOrValue(upstream.path("base-url").asText(""), "Not configured"),
                requiresUpstreamBaseUrl,
                hasText(upstream.path("base-url").asText("")),
                "DRAFT_ROUTING",
                "Required when routed actions or authz depend on the store/upstream application."
            ),
            field(
                "upstream.authType",
                "Upstream auth type",
                blankOrValue(authType, "NONE"),
                false,
                true,
                "DRAFT_ROUTING",
                "NONE means the connector calls the upstream/store API without an auth header."
            ),
            field(
                "upstream.authHeader",
                "Upstream auth header",
                blankOrValue(upstreamAuth.path("header").asText(""), "Not configured"),
                requiresUpstreamAuth,
                hasText(upstreamAuth.path("header").asText("")),
                "DRAFT_ROUTING",
                "Required when the upstream/store auth type is not NONE."
            ),
            field(
                "upstream.authValue",
                "Upstream auth credential",
                maskedDraftValue(upstreamAuth.path("value").asText("")),
                requiresUpstreamAuth,
                hasText(upstreamAuth.path("value").asText("")),
                "DRAFT_ROUTING",
                "Use a placeholder or secret reference instead of storing a literal upstream credential."
            ),
            field(
                "upstream.authzPath",
                "Authz path",
                blankOrValue(authz.path("path").asText(""), "Not configured"),
                authzEnabled,
                hasText(authz.path("path").asText("")),
                "DRAFT_ROUTING",
                "Required when request authorization is delegated to the upstream/store application."
            ),
            field(
                "upstream.authzBaseUrl",
                "Authz base URL",
                blankOrValue(resolveAuthzBaseUrl(authz, upstream, securityConfig), "Not configured"),
                authzEnabled || "REMOTE_HTTP".equalsIgnoreCase(securityConfig.path("authzMode").asText("")),
                hasText(resolveAuthzBaseUrl(authz, upstream, securityConfig)),
                "DRAFT_ROUTING",
                "Remote authz depends on a reachable upstream/store URL."
            )
        );

        return buildServiceSummary(
            "upstreamStore",
            "Store and upstream integration",
            "Customer or store-facing upstream APIs used by routed actions and delegated authorization.",
            textOrNull(upstream, "base-url"),
            fields,
            relevantIssues(validation, Set.of("routing", "security")),
            customIssues
        );
    }

    private DeploymentServiceConfigSummary providerService(JsonNode providerConfig,
                                                           DeploymentTemplateSummary template,
                                                           DraftValidationResponse validation) {
        String llmProvider = providerConfig.path("llmProvider").asText("").trim();
        String embeddingProvider = providerConfig.path("embeddingProvider").asText("").trim();
        boolean openAiRequired = "openai".equalsIgnoreCase(llmProvider) || "openai".equalsIgnoreCase(embeddingProvider);

        List<DeploymentServiceConfigIssueSummary> customIssues = new ArrayList<>();
        if (!openAiRequired && (
            "anthropic".equalsIgnoreCase(llmProvider)
                || "anthropic".equalsIgnoreCase(embeddingProvider)
        )) {
            customIssues.add(new DeploymentServiceConfigIssueSummary(
                "WARNING",
                "EXTERNAL_PROVIDER_CREDENTIALS",
                "$.llmProvider",
                "This provider is outside the current platform secret catalog and needs external credential management."
            ));
        }

        List<DeploymentServiceConfigFieldSummary> fields = List.of(
            field(
                "providers.llmProvider",
                "LLM provider",
                blankOrValue(llmProvider, "Not configured"),
                true,
                hasText(llmProvider),
                "DRAFT_PROVIDER",
                "Primary inference provider selected for the runtime."
            ),
            field(
                "providers.embeddingProvider",
                "Embedding provider",
                blankOrValue(embeddingProvider, "Not configured"),
                true,
                hasText(embeddingProvider),
                "DRAFT_PROVIDER",
                "Provider used to generate vector embeddings."
            ),
            field(
                "providers.vectorStrategy",
                "Vector strategy",
                blankOrValue(providerConfig.path("vectorStrategy").asText(""), "Not configured"),
                true,
                hasText(providerConfig.path("vectorStrategy").asText("")),
                "DRAFT_PROVIDER",
                "Determines which vector database or local index strategy is used."
            ),
            field(
                "providers.runtimeProfile",
                "Runtime profile",
                blankOrValue(providerConfig.path("runtimeProfile").asText(""), template.runtimeProfile()),
                true,
                hasText(providerConfig.path("runtimeProfile").asText("")),
                "DRAFT_PROVIDER",
                "Build/runtime packaging profile for the deployed runtime service."
            ),
            field(
                "providers.connectorProfile",
                "Connector profile",
                blankOrValue(providerConfig.path("connectorProfile").asText(""), template.connectorProfile()),
                true,
                hasText(providerConfig.path("connectorProfile").asText("")),
                "DRAFT_PROVIDER",
                "Build/runtime packaging profile for the deployed REST connector."
            ),
            field(
                "providers.primaryCredential",
                "Primary provider credential",
                openAiRequired
                    ? secretSummary("OPENAI_API_KEY")
                    : "Managed outside current platform secret catalog",
                openAiRequired,
                openAiRequired ? platformSecretService.isSecretPresent("OPENAI_API_KEY") : true,
                openAiRequired ? "PLATFORM_SECRET" : "EXTERNAL",
                openAiRequired
                    ? "OpenAI-backed deployments require OPENAI_API_KEY in the platform secret store."
                    : "Non-OpenAI provider credentials are not yet modeled as first-class platform secrets on this branch."
            )
        );

        return buildServiceSummary(
            "providerStack",
            "Provider stack",
            "LLM, embedding, and vector backend configuration used by the deployment.",
            null,
            fields,
            relevantIssues(validation, Set.of("providers")),
            customIssues
        );
    }

    private DeploymentServiceConfigSummary buildServiceSummary(String key,
                                                              String label,
                                                              String purpose,
                                                              String baseUrl,
                                                              List<DeploymentServiceConfigFieldSummary> fields,
                                                              List<DeploymentServiceConfigIssueSummary> validationIssues,
                                                              List<DeploymentServiceConfigIssueSummary> customIssues) {
        List<DeploymentServiceConfigIssueSummary> issues = new ArrayList<>(validationIssues);
        fields.stream()
            .filter(field -> field.required() && !field.configured())
            .map(field -> new DeploymentServiceConfigIssueSummary(
                "ERROR",
                "REQUIRED_FIELD_MISSING",
                field.key(),
                field.label() + " is required for " + label + "."
            ))
            .forEach(issues::add);
        issues.addAll(customIssues);

        int requiredFieldCount = (int) fields.stream().filter(DeploymentServiceConfigFieldSummary::required).count();
        int configuredRequiredFieldCount = (int) fields.stream()
            .filter(field -> field.required() && field.configured())
            .count();

        boolean blocked = issues.stream().anyMatch(issue -> "ERROR".equals(issue.severity()));
        boolean warning = !blocked && issues.stream().anyMatch(issue -> "WARNING".equals(issue.severity()));
        String status = blocked ? "BLOCKED" : warning ? "WARNING" : "READY";
        String summaryMessage = blocked
            ? configuredRequiredFieldCount + " of " + requiredFieldCount + " required fields are configured."
            : warning
                ? "Required fields are configured, but advisory issues still need review."
                : "All required fields are configured for this service surface.";

        return new DeploymentServiceConfigSummary(
            key,
            label,
            purpose,
            status,
            baseUrl,
            requiredFieldCount,
            configuredRequiredFieldCount,
            fields,
            List.copyOf(issues),
            summaryMessage
        );
    }

    private List<DeploymentServiceConfigIssueSummary> relevantIssues(DraftValidationResponse validation,
                                                                     Set<String> sections) {
        return validation.issues().stream()
            .filter(issue -> sections.contains(issue.section()))
            .map(this::toIssueSummary)
            .toList();
    }

    private DeploymentServiceConfigIssueSummary toIssueSummary(DraftValidationIssue issue) {
        return new DeploymentServiceConfigIssueSummary(
            issue.severity(),
            issue.code(),
            issue.path(),
            issue.message()
        );
    }

    private DeploymentServiceConfigFieldSummary field(String key,
                                                      String label,
                                                      String valueSummary,
                                                      boolean required,
                                                      boolean configured,
                                                      String source,
                                                      String guidance) {
        return new DeploymentServiceConfigFieldSummary(key, label, valueSummary, required, configured, source, guidance);
    }

    private boolean requiresConnectorUpstream(JsonNode routingConfig) {
        JsonNode connector = routingConfig.path("connector");
        JsonNode actions = routingConfig.path("actions");
        if (actions.isObject()) {
            var names = actions.fieldNames();
            while (names.hasNext()) {
                JsonNode route = actions.path(names.next());
                if (hasText(route.path("path").asText(""))) {
                    return true;
                }
            }
        }
        return routingConfig.path("authz").path("enabled").asBoolean(false)
            || hasConfiguredRoutes(routingConfig)
            || hasText(connector.path("upstream").path("base-url").asText(""));
    }

    private boolean hasConfiguredRoutes(JsonNode routingConfig) {
        JsonNode actions = routingConfig.path("actions");
        return actions.isObject() && actions.fieldNames().hasNext();
    }

    private String resolveAuthzBaseUrl(JsonNode authz, JsonNode upstream, JsonNode securityConfig) {
        String authzBaseUrl = authz.path("upstream").path("base-url").asText("").trim();
        if (hasText(authzBaseUrl)) {
            return authzBaseUrl;
        }
        String upstreamBaseUrl = upstream.path("base-url").asText("").trim();
        if (hasText(upstreamBaseUrl)) {
            return upstreamBaseUrl;
        }
        return securityConfig.path("authzBaseUrl").asText("").trim();
    }

    private String summarizeCorsEntries(String allowedOrigins, String allowedPatterns) {
        List<String> parts = new ArrayList<>();
        int originCount = countCsvEntries(allowedOrigins);
        int patternCount = countCsvEntries(allowedPatterns);
        if (originCount > 0) {
            parts.add(originCount + " origin(s)");
        }
        if (patternCount > 0) {
            parts.add(patternCount + " pattern(s)");
        }
        return parts.isEmpty() ? "No browser origins configured" : String.join(" · ", parts);
    }

    private int countCsvEntries(String value) {
        if (!hasText(value)) {
            return 0;
        }
        int count = 0;
        for (String part : value.split(",")) {
            if (!part.trim().isEmpty()) {
                count += 1;
            }
        }
        return count;
    }

    private String secretSummary(String secretName) {
        return platformSecretService.isSecretPresent(secretName)
            ? secretName + " present"
            : secretName + " missing";
    }

    private String maskedDraftValue(String value) {
        return hasText(value) ? "Configured (masked)" : "Not configured";
    }

    private String blankOrValue(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String textOrNull(JsonNode node, String field) {
        String value = node.path(field).asText("").trim();
        return value.isEmpty() ? null : value;
    }

    private JsonNode readJson(String value) {
        try {
            return value == null || value.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse deployment draft config JSON.", ex);
        }
    }
}
