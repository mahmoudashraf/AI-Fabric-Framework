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
import java.util.Map;
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
            summaryMessage = blocked + " deployment surface(s) are blocked and need operator action before production rollout.";
        } else if (warning > 0) {
            summaryMessage = warning + " deployment surface(s) are configured with warnings that should be reviewed before rollout.";
        } else {
            summaryMessage = "Platform-managed services, external dependencies, and client-facing surfaces currently satisfy the required configuration checks.";
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
                "runtime.privateAdminAuth",
                "Private runtime admin contract",
                privateRuntimeAdminSummary(),
                securityConfig.path("adminApiKeyEnabled").asBoolean(false),
                hasPrivateRuntimeAdminContract(),
                "PLATFORM_SECRET",
                "Protects runtime admin endpoints and runtime-backed connector reads with trusted backend auth plus signed private assertions."
            ),
            field(
                "runtime.publicTokenIssuer",
                "Public token issuer hint",
                blankOrValue(ManagedDeploymentProfileCatalog.publicRuntimeTokenIssuer(securityConfig), "Runtime default"),
                platformSecretService.isSecretPresent("AI_FABRIC_RUNTIME_PUBLIC_TOKEN_SIGNING_KEY"),
                hasText(ManagedDeploymentProfileCatalog.publicRuntimeTokenIssuer(securityConfig)),
                "DRAFT_SECURITY",
                "Optional issuer hint for signed public-browser tokens. Leave blank to use the runtime default or a host-issued token contract."
            ),
            field(
                "runtime.publicTokenAudiences",
                "Public token audiences",
                blankOrValue(
                    ManagedDeploymentProfileCatalog.publicRuntimeAcceptedAudiences(securityConfig),
                    ManagedDeploymentProfileCatalog.publicRuntimeDefaultAudience(securityConfig)
                ),
                platformSecretService.isSecretPresent("AI_FABRIC_RUNTIME_PUBLIC_TOKEN_SIGNING_KEY"),
                hasText(ManagedDeploymentProfileCatalog.publicRuntimeAcceptedAudiences(securityConfig))
                    || hasText(ManagedDeploymentProfileCatalog.publicRuntimeDefaultAudience(securityConfig)),
                "DRAFT_SECURITY",
                "Use deployment-scoped audiences when browser tokens need an explicit runtime audience contract."
            ),
            field(
                "runtime.publicBootstrapEnabled",
                "Anonymous public bootstrap",
                String.valueOf(ManagedDeploymentProfileCatalog.publicRuntimeBootstrapEnabled(securityConfig)),
                platformSecretService.isSecretPresent("AI_FABRIC_RUNTIME_PUBLIC_TOKEN_SIGNING_KEY"),
                true,
                "DRAFT_SECURITY",
                "Enables runtime-issued anonymous browser session tokens. Keep this disabled unless the deployment intentionally supports public browser chat."
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
            "PROVISIONED_SERVICE",
            true,
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
        boolean connectorApiKeyEnabled = ManagedDeploymentProfileCatalog.connectorApiKeyEnabled(securityConfig);
        boolean requiresInboundCredential = connectorApiKeyEnabled;
        boolean adminApiKeyEnabled = ManagedDeploymentProfileCatalog.adminApiKeyEnabled(securityConfig);
        boolean connectorRuntimeProxyEnabled = ManagedDeploymentProfileCatalog.connectorRuntimeProxyEnabled(providerConfig);

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
                blankOrValue(apiKey.path("header").asText(""), ManagedDeploymentProfileCatalog.CONNECTOR_API_KEY_HEADER),
                requiresInboundCredential,
                true,
                "DRAFT_ROUTING",
                "Header name used to authenticate inbound REST connector requests when the platform-managed connector key is enabled."
            ),
            field(
                "rest.inboundAuthCredential",
                "Inbound auth credential",
                secretSummary("CONNECTOR_API_KEY"),
                requiresInboundCredential,
                requiresInboundCredential && platformSecretService.isSecretPresent("CONNECTOR_API_KEY"),
                "PLATFORM_SECRET",
                "Platform-managed deployments compile connector inbound auth to CONNECTOR_API_KEY."
            ),
            field(
                "rest.runtimeProxyBaseUrl",
                "Runtime proxy base URL",
                blankOrValue(deployment.getRuntimeBaseUrl(), "Not applied yet"),
                connectorRuntimeProxyEnabled,
                !connectorRuntimeProxyEnabled || hasText(deployment.getRuntimeBaseUrl()),
                "DEPLOYMENT_RELEASE",
                "REST admin and indexing proxies need the runtime public URL."
            ),
            field(
                "rest.runtimeProxyCredential",
                "Runtime proxy machine credential",
                adminApiKeyEnabled ? secretSummary("AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY") : "Not required",
                adminApiKeyEnabled && connectorRuntimeProxyEnabled,
                !adminApiKeyEnabled || platformSecretService.isSecretPresent("AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY"),
                adminApiKeyEnabled ? "PLATFORM_SECRET" : "CONFIG",
                adminApiKeyEnabled
                    ? "Required when the internal REST connector forwards trusted machine calls to the private runtime."
                    : "Private admin access is disabled, so the hosted runtime proxy does not send a trusted backend credential."
            ),
            field(
                "rest.connectorProfile",
                "Connector profile",
                blankOrValue(providerConfig.path("connectorProfile").asText(""), ManagedDeploymentProfileCatalog.CONNECTOR_PROFILE_HOSTED),
                true,
                hasText(providerConfig.path("connectorProfile").asText("")),
                "DRAFT_PROVIDER",
                "Controls the connector packaging profile used during deployment."
            )
        );

        return buildServiceSummary(
            "restConnector",
            "REST connector",
            "PROVISIONED_SERVICE",
            true,
            "Internal connector service exposing routed actions, operator-only admin operations, and runtime proxy flows.",
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
                "Browser and host-backed integrations need the runtime URL appropriate for the deployment auth mode."
            ),
            field(
                "ui.connectorBaseUrl",
                "REST connector internal endpoint",
                blankOrValue(deployment.getConnectorBaseUrl(), "Not applied yet"),
                true,
                hasText(deployment.getConnectorBaseUrl()),
                "DEPLOYMENT_RELEASE",
                "The connector remains internal. Supported inspection should go through runtime-backed connector admin paths."
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
            "CLIENT_SURFACE",
            false,
            "Operator and customer browser clients that integrate through runtime URLs or trusted host-backed APIs while the connector remains internal.",
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
            "EXTERNAL_DEPENDENCY",
            false,
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
        String llmProvider = ManagedDeploymentProfileCatalog.resolveLlmProvider(providerConfig);
        String embeddingProvider = ManagedDeploymentProfileCatalog.resolveEmbeddingProvider(providerConfig);
        String vectorStrategy = ManagedDeploymentProfileCatalog.resolveVectorStrategy(providerConfig);
        String vectorProvisioningMode = ManagedDeploymentProfileCatalog.resolveVectorProvisioningMode(providerConfig);
        String orchestrationProvider = ManagedDeploymentProfileCatalog.orchestrationLlmProvider(providerConfig);
        String generationProvider = ManagedDeploymentProfileCatalog.generationLlmProvider(providerConfig);
        Map<String, String> llmSecretNamesByProvider = ManagedDeploymentProfileCatalog.providerSecretNamesByLlmSelection(providerConfig);
        String embeddingSecretName = ManagedDeploymentProfileCatalog.secretNameForEmbeddingProvider(embeddingProvider);
        String requiredVectorSecretName = ManagedDeploymentProfileCatalog.requiredVectorSecretName(providerConfig);
        Set<String> optionalVectorSecretNames = ManagedDeploymentProfileCatalog.optionalVectorSecretNames(providerConfig);

        List<DeploymentServiceConfigFieldSummary> fields = new ArrayList<>(List.of(
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
                blankOrValue(vectorStrategy, "Not configured"),
                true,
                hasText(vectorStrategy),
                "DRAFT_PROVIDER",
                "Determines which vector database or local index strategy is used."
            ),
            field(
                "providers.vectorProvisioningMode",
                "Vector provisioning mode",
                blankOrValue(vectorProvisioningMode, "Not configured"),
                true,
                hasText(vectorProvisioningMode),
                "DRAFT_PROVIDER",
                ManagedDeploymentProfileCatalog.vectorProvisioningModeGuidance(vectorStrategy)
            ),
            field(
                "providers.enableFallback",
                "Provider fallback",
                Boolean.toString(ManagedDeploymentProfileCatalog.providerEnableFallback(providerConfig)),
                false,
                true,
                "DRAFT_PROVIDER",
                "Controls whether runtime provider fallback is enabled when the preferred provider fails."
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
                "providers.orchestrationProvider",
                "Orchestration provider override",
                blankOrValue(orchestrationProvider, "Uses primary LLM provider"),
                false,
                true,
                "DRAFT_PROVIDER",
                "Optional. When set, orchestration and intent work can use a different LLM provider than answer generation."
            ),
            field(
                "providers.generationProvider",
                "Generation provider override",
                blankOrValue(generationProvider, "Uses primary LLM provider"),
                false,
                true,
                "DRAFT_PROVIDER",
                "Optional. When set, answer generation can use a different LLM provider than the primary profile."
            ),
            field(
                "providers.embeddingCredential",
                "Embedding credential",
                hasText(embeddingSecretName) ? secretSummary(embeddingSecretName) : "Not required",
                hasText(embeddingSecretName),
                !hasText(embeddingSecretName) || platformSecretService.isSecretPresent(embeddingSecretName),
                hasText(embeddingSecretName) ? "PLATFORM_SECRET" : "CONFIG",
                hasText(embeddingSecretName)
                    ? "The selected embedding provider must be backed by a platform-managed credential."
                    : "The selected embedding provider does not require a managed secret."
            )
        ));

        for (Map.Entry<String, String> entry : llmSecretNamesByProvider.entrySet()) {
            fields.add(field(
                "providers.llmCredential." + entry.getKey(),
                entry.getKey().substring(0, 1).toUpperCase() + entry.getKey().substring(1) + " credential",
                secretSummary(entry.getValue()),
                true,
                platformSecretService.isSecretPresent(entry.getValue()),
                "PLATFORM_SECRET",
                "Platform-managed credential required for the " + entry.getKey() + " LLM profile."
            ));
        }

        addProviderSpecificFields(fields, providerConfig, llmProvider, embeddingProvider, vectorStrategy, requiredVectorSecretName, optionalVectorSecretNames);

        return buildServiceSummary(
            "providerStack",
            "Provider stack",
            "PLATFORM_STACK",
            true,
            "LLM, embedding, and vector backend configuration used by the deployment.",
            null,
            fields,
            relevantIssues(validation, Set.of("providers")),
            List.of()
        );
    }

    private void addProviderSpecificFields(List<DeploymentServiceConfigFieldSummary> fields,
                                           JsonNode providerConfig,
                                           String llmProvider,
                                           String embeddingProvider,
                                           String vectorStrategy,
                                           String requiredVectorSecretName,
                                           Set<String> optionalVectorSecretNames) {
        if (ManagedDeploymentProfileCatalog.usesLlmProvider(providerConfig, ManagedDeploymentProfileCatalog.LLM_PROVIDER_OPENAI)
            || ManagedDeploymentProfileCatalog.EMBEDDING_PROVIDER_OPENAI.equals(embeddingProvider)) {
            fields.add(field(
                "providers.openAiSelection",
                "OpenAI model selection",
                "LLM=" + ManagedDeploymentProfileCatalog.openAiModel(providerConfig)
                    + " · embedding=" + ManagedDeploymentProfileCatalog.openAiEmbeddingModel(providerConfig),
                false,
                true,
                "DRAFT_PROVIDER",
                "The platform uses these values when OpenAI is selected for generation or embeddings."
            ));
            fields.add(field(
                "providers.openAiBaseUrl",
                "OpenAI base URL override",
                blankOrValue(ManagedDeploymentProfileCatalog.openAiBaseUrl(providerConfig), "Default provider endpoint"),
                false,
                true,
                "DRAFT_PROVIDER",
                "Optional. Useful for private gateways or OpenAI-compatible routed endpoints."
            ));
        }
        if (ManagedDeploymentProfileCatalog.usesLlmProvider(providerConfig, ManagedDeploymentProfileCatalog.LLM_PROVIDER_ANTHROPIC)) {
            fields.add(field(
                "providers.anthropicSelection",
                "Anthropic model selection",
                ManagedDeploymentProfileCatalog.anthropicModel(providerConfig),
                false,
                true,
                "DRAFT_PROVIDER",
                "The selected Anthropic model is compiled directly into the managed runtime env."
            ));
            fields.add(field(
                "providers.anthropicBaseUrl",
                "Anthropic base URL override",
                blankOrValue(ManagedDeploymentProfileCatalog.anthropicBaseUrl(providerConfig), "Default provider endpoint"),
                false,
                true,
                "DRAFT_PROVIDER",
                "Optional. Useful for regional gateways or private routing layers."
            ));
        }
        if (ManagedDeploymentProfileCatalog.usesLlmProvider(providerConfig, ManagedDeploymentProfileCatalog.LLM_PROVIDER_AZURE)
            || ManagedDeploymentProfileCatalog.EMBEDDING_PROVIDER_AZURE.equals(embeddingProvider)) {
            fields.add(field(
                "providers.azureEndpoint",
                "Azure endpoint",
                blankOrValue(ManagedDeploymentProfileCatalog.azureEndpoint(providerConfig), "Not configured"),
                true,
                hasText(ManagedDeploymentProfileCatalog.azureEndpoint(providerConfig)),
                "DRAFT_PROVIDER",
                "Required when Azure OpenAI is selected for LLM or embeddings."
            ));
        }
        if (ManagedDeploymentProfileCatalog.usesLlmProvider(providerConfig, ManagedDeploymentProfileCatalog.LLM_PROVIDER_AZURE)) {
            fields.add(field(
                "providers.azureDeploymentName",
                "Azure LLM deployment",
                blankOrValue(ManagedDeploymentProfileCatalog.azureDeploymentName(providerConfig), "Not configured"),
                true,
                hasText(ManagedDeploymentProfileCatalog.azureDeploymentName(providerConfig)),
                "DRAFT_PROVIDER",
                "Azure OpenAI LLM deployments are customer-defined and must be set explicitly."
            ));
        }
        if (ManagedDeploymentProfileCatalog.EMBEDDING_PROVIDER_AZURE.equals(embeddingProvider)) {
            fields.add(field(
                "providers.azureEmbeddingDeploymentName",
                "Azure embedding deployment",
                blankOrValue(ManagedDeploymentProfileCatalog.azureEmbeddingDeploymentName(providerConfig), "Not configured"),
                true,
                hasText(ManagedDeploymentProfileCatalog.azureEmbeddingDeploymentName(providerConfig)),
                "DRAFT_PROVIDER",
                "Azure embedding deployments are customer-defined and must be set explicitly."
            ));
        }
        if (ManagedDeploymentProfileCatalog.usesLlmProvider(providerConfig, ManagedDeploymentProfileCatalog.LLM_PROVIDER_COHERE)
            || ManagedDeploymentProfileCatalog.EMBEDDING_PROVIDER_COHERE.equals(embeddingProvider)) {
            fields.add(field(
                "providers.cohereModelSelection",
                "Cohere model selection",
                "LLM=" + ManagedDeploymentProfileCatalog.cohereModel(providerConfig)
                    + " · embedding=" + ManagedDeploymentProfileCatalog.cohereEmbeddingModel(providerConfig),
                false,
                true,
                "DRAFT_PROVIDER",
                "Defaults are supplied by the platform but can be overridden in the provider workspace."
            ));
            fields.add(field(
                "providers.cohereBaseUrl",
                "Cohere base URL override",
                blankOrValue(ManagedDeploymentProfileCatalog.cohereBaseUrl(providerConfig), "Default provider endpoint"),
                false,
                true,
                "DRAFT_PROVIDER",
                "Optional. Use when traffic must go through a private or regional Cohere gateway."
            ));
        }
        if (ManagedDeploymentProfileCatalog.usesLlmProvider(providerConfig, ManagedDeploymentProfileCatalog.LLM_PROVIDER_GEMINI)
            || ManagedDeploymentProfileCatalog.EMBEDDING_PROVIDER_GEMINI.equals(embeddingProvider)) {
            fields.add(field(
                "providers.geminiModelSelection",
                "Gemini model selection",
                "LLM=" + ManagedDeploymentProfileCatalog.geminiModel(providerConfig)
                    + " · embedding=" + ManagedDeploymentProfileCatalog.geminiEmbeddingModel(providerConfig),
                false,
                true,
                "DRAFT_PROVIDER",
                "Defaults are supplied by the platform but can be overridden in the provider workspace."
            ));
            fields.add(field(
                "providers.geminiBaseUrl",
                "Gemini base URL override",
                blankOrValue(ManagedDeploymentProfileCatalog.geminiBaseUrl(providerConfig), "Default provider endpoint"),
                false,
                true,
                "DRAFT_PROVIDER",
                "Optional. Useful when Gemini traffic must route through a gateway or proxy."
            ));
        }
        if (ManagedDeploymentProfileCatalog.EMBEDDING_PROVIDER_ONNX.equals(embeddingProvider)) {
            fields.add(field(
                "providers.onnxModelAlias",
                "ONNX model alias",
                ManagedDeploymentProfileCatalog.onnxModelAlias(providerConfig),
                false,
                true,
                "DRAFT_PROVIDER",
                "The managed runtime will resolve bundled ONNX assets using this alias unless an explicit path override is supplied."
            ));
            fields.add(field(
                "providers.onnxUseGpu",
                "ONNX GPU acceleration",
                Boolean.toString(ManagedDeploymentProfileCatalog.onnxUseGpu(providerConfig)),
                false,
                true,
                "DRAFT_PROVIDER",
                "GPU acceleration is optional and should only be enabled for runtimes built with the required ONNX GPU support."
            ));
        }
        if (ManagedDeploymentProfileCatalog.EMBEDDING_PROVIDER_REST.equals(embeddingProvider)) {
            fields.add(field(
                "providers.restEmbeddingBaseUrl",
                "REST embedding base URL",
                blankOrValue(ManagedDeploymentProfileCatalog.restEmbeddingBaseUrl(providerConfig), "Not configured"),
                true,
                hasText(ManagedDeploymentProfileCatalog.restEmbeddingBaseUrl(providerConfig)),
                "DRAFT_PROVIDER",
                "Required when the runtime uses an external REST embedding service."
            ));
        }
        if (ManagedDeploymentProfileCatalog.VECTOR_STRATEGY_QDRANT.equals(vectorStrategy)) {
            boolean platformManaged = ManagedDeploymentProfileCatalog.qdrantPlatformManaged(providerConfig);
            if (platformManaged) {
                fields.add(field(
                    "providers.qdrantCloudProviderId",
                    "Qdrant Cloud provider",
                    ManagedDeploymentProfileCatalog.qdrantCloudProviderId(providerConfig),
                    false,
                    true,
                    "DRAFT_PROVIDER",
                    "Cloud provider used when the platform provisions a Qdrant Cloud managed cluster."
                ));
                fields.add(field(
                    "providers.qdrantCloudRegionId",
                    "Qdrant Cloud region",
                    blankOrValue(ManagedDeploymentProfileCatalog.qdrantCloudRegionId(providerConfig), "Not configured"),
                    true,
                    hasText(ManagedDeploymentProfileCatalog.qdrantCloudRegionId(providerConfig)),
                    "DRAFT_PROVIDER",
                    "Required when platform-managed Qdrant Cloud provisioning is enabled."
                ));
                fields.add(field(
                    "providers.qdrantCloudAccountId",
                    "Qdrant Cloud account",
                    blankOrValue(ManagedDeploymentProfileCatalog.qdrantCloudAccountId(providerConfig), "Auto-resolve from management key"),
                    false,
                    true,
                    "DRAFT_PROVIDER",
                    "Optional when the Qdrant Cloud management key only exposes a single account."
                ));
                fields.add(field(
                    "providers.qdrantCloudPackageId",
                    "Qdrant Cloud package",
                    blankOrValue(ManagedDeploymentProfileCatalog.qdrantCloudPackageId(providerConfig), "Auto-select cheapest active package"),
                    false,
                    true,
                    "DRAFT_PROVIDER",
                    "Optional package override. Leave blank to let the platform choose the cheapest active package in the selected region."
                ));
                fields.add(field(
                    "providers.qdrantCloudClusterNameOverride",
                    "Qdrant Cloud cluster name",
                    blankOrValue(ManagedDeploymentProfileCatalog.qdrantCloudClusterNameOverride(providerConfig), "Derived from deployment id"),
                    false,
                    true,
                    "DRAFT_PROVIDER",
                    "Optional override for the deployment-owned Qdrant Cloud cluster name."
                ));
            } else {
                fields.add(field(
                    "providers.qdrantHost",
                    "Qdrant host",
                    blankOrValue(ManagedDeploymentProfileCatalog.qdrantHost(providerConfig), "Not configured"),
                    true,
                    hasText(ManagedDeploymentProfileCatalog.qdrantHost(providerConfig)),
                    "DRAFT_PROVIDER",
                    "Required when the deployment targets an existing Qdrant cluster."
                ));
            }
            fields.add(field(
                "providers.qdrantManagedCollectionsEnabled",
                "Platform-managed Qdrant collections",
                Boolean.toString(ManagedDeploymentProfileCatalog.qdrantCollectionsManagedByPlatform(providerConfig)),
                false,
                true,
                "DRAFT_PROVIDER",
                platformManaged
                    ? "Platform-managed Qdrant Cloud clusters always reconcile one collection per configured entity type."
                    : "When enabled, apply will create or reconcile Qdrant collections for configured entity types."
            ));
        }
        if (ManagedDeploymentProfileCatalog.VECTOR_STRATEGY_PINECONE.equals(vectorStrategy)) {
            boolean platformManaged = ManagedDeploymentProfileCatalog.pineconePlatformManaged(providerConfig);
            fields.add(field(
                "providers.pineconeIndexName",
                "Pinecone index",
                blankOrValue(ManagedDeploymentProfileCatalog.pineconeIndexName(providerConfig), "Derived from API host or not configured"),
                true,
                hasText(ManagedDeploymentProfileCatalog.pineconeIndexName(providerConfig))
                    || hasText(ManagedDeploymentProfileCatalog.pineconeApiHost(providerConfig)),
                "DRAFT_PROVIDER",
                platformManaged
                    ? "Required when the platform manages the Pinecone serverless index for this deployment."
                    : "Provide pineconeIndexName directly or set pineconeApiHost so the platform can derive it."
            ));
            fields.add(field(
                "providers.pineconeCloud",
                "Pinecone cloud",
                ManagedDeploymentProfileCatalog.pineconeCloud(providerConfig),
                false,
                true,
                "DRAFT_PROVIDER",
                platformManaged
                    ? "Cloud target used when the platform provisions a Pinecone serverless index."
                    : "Only used when the platform manages the Pinecone serverless index."
            ));
            fields.add(field(
                "providers.pineconeRegion",
                "Pinecone region",
                blankOrValue(ManagedDeploymentProfileCatalog.pineconeRegion(providerConfig), "Not configured"),
                platformManaged,
                !platformManaged
                    || hasText(ManagedDeploymentProfileCatalog.pineconeRegion(providerConfig)),
                "DRAFT_PROVIDER",
                platformManaged
                    ? "Required when platform-managed Pinecone serverless provisioning is enabled."
                    : "Optional when bring-your-own Pinecone is selected."
            ));
            fields.add(field(
                "providers.pineconeMetric",
                "Pinecone metric",
                ManagedDeploymentProfileCatalog.pineconeMetric(providerConfig),
                false,
                true,
                "DRAFT_PROVIDER",
                "Similarity metric used when the platform provisions a Pinecone serverless index."
            ));
            fields.add(field(
                "providers.pineconeApiHost",
                "Pinecone API host",
                blankOrValue(ManagedDeploymentProfileCatalog.pineconeApiHost(providerConfig), "Resolved during apply or not configured"),
                false,
                platformManaged
                    ? hasText(ManagedDeploymentProfileCatalog.pineconeApiHost(providerConfig))
                    : hasText(ManagedDeploymentProfileCatalog.pineconeApiHost(providerConfig)) || hasText(ManagedDeploymentProfileCatalog.pineconeEnvironment(providerConfig)),
                "DRAFT_PROVIDER",
                platformManaged
                    ? "Platform-managed Pinecone deployments resolve this host during apply and bind it back into runtime config."
                    : "Optional explicit Pinecone API host for bring-your-own mode."
            ));
            fields.add(field(
                "providers.pineconeRuntimeApiKeySecretName",
                "Pinecone runtime API key secret",
                blankOrValue(ManagedDeploymentProfileCatalog.pineconeRuntimeApiKeySecretName(providerConfig), "Bound during apply"),
                false,
                !platformManaged || hasText(ManagedDeploymentProfileCatalog.pineconeRuntimeApiKeySecretName(providerConfig)),
                "PROVISIONED",
                "Platform-managed Pinecone deployments bind runtime to a managed secret reference after apply."
            ));
        }
        if (ManagedDeploymentProfileCatalog.VECTOR_STRATEGY_WEAVIATE.equals(vectorStrategy)) {
            fields.add(field(
                "providers.weaviateHost",
                "Weaviate host",
                blankOrValue(ManagedDeploymentProfileCatalog.weaviateHost(providerConfig), "Not configured"),
                true,
                hasText(ManagedDeploymentProfileCatalog.weaviateHost(providerConfig)),
                "DRAFT_PROVIDER",
                "Required when the deployment targets Weaviate Cloud or another operator-managed Weaviate service."
            ));
        }
        if (ManagedDeploymentProfileCatalog.VECTOR_STRATEGY_MILVUS.equals(vectorStrategy)) {
            boolean platformManaged = ManagedDeploymentProfileCatalog.milvusPlatformManaged(providerConfig);
            if (platformManaged) {
                fields.add(field(
                    "providers.zillizCloudProjectId",
                    "Zilliz Cloud project ID",
                    blankOrValue(ManagedDeploymentProfileCatalog.zillizCloudProjectId(providerConfig), "Not configured"),
                    true,
                    hasText(ManagedDeploymentProfileCatalog.zillizCloudProjectId(providerConfig)),
                    "DRAFT_PROVIDER",
                    "Required before the platform can provision or reconcile a managed Zilliz Cloud cluster."
                ));
                fields.add(field(
                    "providers.zillizCloudRegionId",
                    "Zilliz Cloud region ID",
                    blankOrValue(ManagedDeploymentProfileCatalog.zillizCloudRegionId(providerConfig), "Not configured"),
                    true,
                    hasText(ManagedDeploymentProfileCatalog.zillizCloudRegionId(providerConfig)),
                    "DRAFT_PROVIDER",
                    "Required before the platform can provision or reconcile a managed Zilliz Cloud cluster."
                ));
                fields.add(field(
                    "providers.zillizCloudClusterPlan",
                    "Zilliz Cloud cluster plan",
                    ManagedDeploymentProfileCatalog.zillizCloudClusterPlan(providerConfig),
                    false,
                    true,
                    "DRAFT_PROVIDER",
                    "Determines whether the platform provisions a Free, Serverless, Standard, or Enterprise Zilliz Cloud cluster."
                ));
                fields.add(field(
                    "providers.milvusRuntimeUsernameSecretName",
                    "Managed Milvus username secret",
                    blankOrValue(ManagedDeploymentProfileCatalog.milvusRuntimeUsernameSecretName(providerConfig), "Bound during apply"),
                    false,
                    hasText(ManagedDeploymentProfileCatalog.milvusRuntimeUsernameSecretName(providerConfig)),
                    "PROVISIONED",
                    "Platform-managed Zilliz Cloud deployments bind runtime to a managed username secret after apply."
                ));
                fields.add(field(
                    "providers.milvusRuntimePasswordSecretName",
                    "Managed Milvus password secret",
                    blankOrValue(ManagedDeploymentProfileCatalog.milvusRuntimePasswordSecretName(providerConfig), "Bound during apply"),
                    false,
                    hasText(ManagedDeploymentProfileCatalog.milvusRuntimePasswordSecretName(providerConfig)),
                    "PROVISIONED",
                    "Platform-managed Zilliz Cloud deployments bind runtime to a managed password secret after apply."
                ));
            }
            fields.add(field(
                "providers.milvusHost",
                "Milvus host",
                blankOrValue(ManagedDeploymentProfileCatalog.milvusHost(providerConfig), "Not configured"),
                !platformManaged,
                platformManaged || hasText(ManagedDeploymentProfileCatalog.milvusHost(providerConfig)),
                "DRAFT_PROVIDER",
                platformManaged
                    ? "Resolved during apply when the platform provisions or reuses a managed Zilliz Cloud cluster."
                    : "Required when the deployment targets an existing Milvus deployment."
            ));
        }
        if (hasText(requiredVectorSecretName)) {
            fields.add(field(
                "providers.vectorCredential",
                "Vector backend credential",
                secretSummary(requiredVectorSecretName),
                true,
                platformSecretService.isSecretPresent(requiredVectorSecretName),
                "PLATFORM_SECRET",
                "Required platform credential for the selected managed vector backend."
            ));
        }
        for (String optionalVectorSecretName : optionalVectorSecretNames) {
            fields.add(field(
                "providers." + optionalVectorSecretName.toLowerCase(),
                optionalVectorSecretName.replace('_', ' '),
                secretSummary(optionalVectorSecretName),
                false,
                true,
                "PLATFORM_SECRET",
                "Optional platform secret used when the selected vector backend requires authentication."
            ));
        }
    }

    private DeploymentServiceConfigSummary buildServiceSummary(String key,
                                                              String label,
                                                              String surfaceType,
                                                              boolean platformManaged,
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
                : "All required fields are configured for this deployment surface.";

        return new DeploymentServiceConfigSummary(
            key,
            label,
            surfaceType,
            platformManaged,
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

    private boolean hasPrivateRuntimeAdminContract() {
        return platformSecretService.isSecretPresent("AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY")
            && platformSecretService.isSecretPresent("AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY");
    }

    private String privateRuntimeAdminSummary() {
        return hasPrivateRuntimeAdminContract()
            ? "Trusted backend key + private assertion signing key present"
            : "Private runtime admin contract incomplete";
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
