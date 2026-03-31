package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.config.PlatformDeliveryProperties;
import com.ai.fabric.platform.backend.config.PlatformProvisioningProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.RailwayArtifactUrlsSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayEnvVarSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayProvisioningPlanSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayProvisioningServicesSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayProvisioningStepSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayServicePlanSummary;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class RailwayProvisioningPlanService {

    private final PlatformProvisioningProperties provisioningProperties;
    private final PlatformDeliveryProperties deliveryProperties;
    private final DeploymentArtifactService artifactService;
    private final DeploymentSourceResolver deploymentSourceResolver;
    private final PlatformSecretService platformSecretService;
    private final ObjectMapper objectMapper;

    public RailwayProvisioningPlanService(PlatformProvisioningProperties provisioningProperties,
                                          PlatformDeliveryProperties deliveryProperties,
                                          DeploymentArtifactService artifactService,
                                          DeploymentSourceResolver deploymentSourceResolver,
                                          PlatformSecretService platformSecretService,
                                          ObjectMapper objectMapper) {
        this.provisioningProperties = provisioningProperties;
        this.deliveryProperties = deliveryProperties;
        this.artifactService = artifactService;
        this.deploymentSourceResolver = deploymentSourceResolver;
        this.platformSecretService = platformSecretService;
        this.objectMapper = objectMapper;
    }

    public RailwayProvisioningPlanSummary buildPlan(DeploymentEntity deployment, DeploymentVersionEntity version) {
        String sourceRepository = deploymentSourceResolver.resolveRepository(deployment);
        String sourceBranch = deploymentSourceResolver.resolveBranch(deployment);
        String runtimeBaseUrl = deployment.getRuntimeBaseUrl() != null
            ? deployment.getRuntimeBaseUrl()
            : "https://runtime-" + deployment.getId() + ".placeholder.local";
        String connectorBaseUrl = deployment.getConnectorBaseUrl() != null
            ? deployment.getConnectorBaseUrl()
            : "https://connector-" + deployment.getId() + ".placeholder.local";
        JsonNode providerConfig = readJson(version.getProviderConfigJson());
        JsonNode securityConfig = readJson(version.getSecurityConfigJson());

        var artifacts = artifactService.toBundleSummary(version);
        RailwayArtifactUrlsSummary artifactUrls = new RailwayArtifactUrlsSummary(
            artifacts.actionsArtifactUrl(),
            artifacts.entityArtifactUrl(),
            artifacts.routingArtifactUrl(),
            artifacts.promptArtifactUrl(),
            artifacts.manifestUrl()
        );

        List<RailwayEnvVarSummary> runtimeEnv = new ArrayList<>();
        runtimeEnv.add(new RailwayEnvVarSummary("AI_ACTIONS_CATALOG_PATH", artifactUrls.actions()));
        runtimeEnv.add(new RailwayEnvVarSummary("AI_CONFIG_DEFAULT_FILE", artifactUrls.entities()));
        runtimeEnv.add(new RailwayEnvVarSummary("AI_PROMPTS_DEPLOYMENT_CONFIG_FILE", artifactUrls.prompts()));
        runtimeEnv.add(new RailwayEnvVarSummary("ACTIONS_CONNECTOR_BASE_URL", connectorBaseUrl));
        addRuntimeProviderEnv(runtimeEnv, providerConfig);
        addRuntimeConnectorAuthEnv(runtimeEnv, securityConfig);
        addOptionalEnv(runtimeEnv, "AI_CURATED_PACK", text(providerConfig, "curatedPackId"));
        runtimeEnv.add(new RailwayEnvVarSummary(
            "AI_FABRIC_RUNTIME_DEV_DEFAULTS_ENABLED",
            Boolean.toString(ManagedDeploymentProfileCatalog.runtimeDevDefaultsEnabled(providerConfig))
        ));
        runtimeEnv.add(new RailwayEnvVarSummary(
            "AI_FABRIC_RUNTIME_AUTHZ_MODE",
            ManagedDeploymentProfileCatalog.resolveAuthzMode(securityConfig)
        ));
        if (ManagedDeploymentProfileCatalog.adminApiKeyEnabled(securityConfig)
            && platformSecretService.isSecretPresent("APP_ADMIN_API_KEY")) {
            runtimeEnv.add(new RailwayEnvVarSummary("APP_ADMIN_API_KEY", "${secret:APP_ADMIN_API_KEY}"));
            runtimeEnv.add(new RailwayEnvVarSummary(
                "APP_ADMIN_API_KEY_HEADER",
                ManagedDeploymentProfileCatalog.ADMIN_API_KEY_HEADER
            ));
        }
        addCorsEnv(runtimeEnv, securityConfig);
        String runtimeAuthzBaseUrl = resolveRuntimeAuthzBaseUrl(securityConfig, connectorBaseUrl);
        addOptionalEnv(runtimeEnv, "AUTHZ_BASE_URL", runtimeAuthzBaseUrl);

        RailwayServicePlanSummary runtime = new RailwayServicePlanSummary(
            provisioningProperties.runtimeServiceNamePrefix() + "-" + deployment.getId(),
            resolveRootDirectory(provisioningProperties.runtimeDockerfilePath(), provisioningProperties.runtimeServiceRoot()),
            provisioningProperties.runtimeDockerfilePath(),
            runtimeBaseUrl,
            runtimeEnv
        );

        List<RailwayEnvVarSummary> connectorEnv = new ArrayList<>();
        connectorEnv.add(new RailwayEnvVarSummary("REST_CONNECTOR_ROUTING_CONFIG_LOCATION", artifactUrls.routing()));
        addConnectorProfileEnv(connectorEnv, providerConfig, runtimeBaseUrl, securityConfig);
        if (ManagedDeploymentProfileCatalog.connectorApiKeyEnabled(securityConfig)) {
            connectorEnv.add(new RailwayEnvVarSummary("CONNECTOR_API_KEY", "${secret:CONNECTOR_API_KEY}"));
        }
        if (ManagedDeploymentProfileCatalog.adminApiKeyEnabled(securityConfig)
            && platformSecretService.isSecretPresent("APP_ADMIN_API_KEY")) {
            connectorEnv.add(new RailwayEnvVarSummary("APP_ADMIN_API_KEY", "${secret:APP_ADMIN_API_KEY}"));
            connectorEnv.add(new RailwayEnvVarSummary(
                "APP_ADMIN_API_KEY_HEADER",
                ManagedDeploymentProfileCatalog.ADMIN_API_KEY_HEADER
            ));
        }
        addCorsEnv(connectorEnv, securityConfig);

        RailwayServicePlanSummary restConnector = new RailwayServicePlanSummary(
            provisioningProperties.connectorServiceNamePrefix() + "-" + deployment.getId(),
            resolveRootDirectory(
                provisioningProperties.connectorDockerfilePath(),
                provisioningProperties.connectorServiceRoot()
            ),
            provisioningProperties.connectorDockerfilePath(),
            connectorBaseUrl,
            connectorEnv
        );

        return new RailwayProvisioningPlanSummary(
            deployment.getId(),
            deployment.getName(),
            deployment.getEnvironmentName(),
            deployment.getTemplateId(),
            version.getId(),
            version.getVersionLabel(),
            version.getConfigHash(),
            provisioningProperties.mode(),
            buildProjectName(deployment),
            sourceRepository,
            sourceBranch,
            provisioningProperties.workspaceId().isBlank() ? null : provisioningProperties.workspaceId(),
            artifactServiceSignedStrategy(),
            artifactUrls,
            new RailwayProvisioningServicesSummary(runtime, restConnector),
            List.of(
                new RailwayProvisioningStepSummary(1, "publish_artifacts", "Resolve immutable config artifact URLs for the selected version."),
                new RailwayProvisioningStepSummary(2, "prepare_project", "Create or reuse the Railway project for this customer environment."),
                new RailwayProvisioningStepSummary(3, "configure_runtime", "Create or update the runtime service root and its environment variables."),
                new RailwayProvisioningStepSummary(4, "configure_rest_connector", "Create or update the REST connector service root and its environment variables."),
                new RailwayProvisioningStepSummary(5, "trigger_deploy", "Commit staged changes or trigger Railway deployment/redeploy for both services."),
                new RailwayProvisioningStepSummary(6, "wait_for_active", "Wait for Railway deployment states to become active."),
                new RailwayProvisioningStepSummary(7, "run_verification", "Run post-deploy verification against runtime and connector endpoints.")
            )
        );
    }

    private String artifactServiceSignedStrategy() {
        return deliveryProperties.signedArtifactsEnabled()
            ? "SIGNED_REMOTE_CONFIG_BUNDLES"
            : "REMOTE_CONFIG_BUNDLES";
    }

    private String normalizeName(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("-{2,}", "-")
            .replaceAll("(^-|-$)", "");
    }

    private String buildProjectName(DeploymentEntity deployment) {
        String environment = fallbackName(normalizeName(deployment.getEnvironmentName()), "dev");
        String deploymentSlug = fallbackName(normalizeName(deployment.getName()), "deployment");
        String idSuffix = fallbackName(
            normalizeName(shortDeploymentId(deployment.getId())),
            "default"
        );
        String suffix = environment + "-" + idSuffix;
        int maxLength = provisioningProperties.projectNameMaxLength();
        int maxBaseLength = Math.max(1, maxLength - suffix.length() - 1);
        String truncatedBase = trimHyphenated(deploymentSlug.substring(0, Math.min(deploymentSlug.length(), maxBaseLength)));
        if (truncatedBase.isBlank()) {
            truncatedBase = "deployment";
            if (truncatedBase.length() > maxBaseLength) {
                truncatedBase = truncatedBase.substring(0, maxBaseLength);
            }
            truncatedBase = trimHyphenated(truncatedBase);
        }
        String projectName = trimHyphenated(truncatedBase + "-" + suffix);
        if (projectName.length() > maxLength) {
            projectName = trimHyphenated(projectName.substring(0, maxLength));
        }
        return fallbackName(projectName, "deployment-" + idSuffix);
    }

    private String shortDeploymentId(String deploymentId) {
        String normalized = normalizeName(deploymentId);
        if (normalized.startsWith("dep-")) {
            normalized = normalized.substring(4);
        }
        return normalized.length() <= 8 ? normalized : normalized.substring(0, 8);
    }

    private String fallbackName(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String trimHyphenated(String value) {
        return value == null ? "" : value.replaceAll("(^-|-$)", "");
    }

    private JsonNode readJson(String value) {
        try {
            return value == null || value.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse deployment version config JSON.", ex);
        }
    }

    private String text(JsonNode node, String field) {
        if (node == null) {
            return "";
        }
        return node.path(field).asText("").trim();
    }

    private void addRuntimeProviderEnv(List<RailwayEnvVarSummary> runtimeEnv, JsonNode providerConfig) {
        String llmProvider = ManagedDeploymentProfileCatalog.resolveLlmProvider(providerConfig);
        String embeddingProvider = ManagedDeploymentProfileCatalog.resolveEmbeddingProvider(providerConfig);
        String vectorStrategy = ManagedDeploymentProfileCatalog.resolveVectorStrategy(providerConfig);
        int vectorDimensions = ManagedDeploymentProfileCatalog.defaultEmbeddingDimensions(embeddingProvider);

        runtimeEnv.add(new RailwayEnvVarSummary("AI_PROVIDERS_LLM_PROVIDER", llmProvider));
        runtimeEnv.add(new RailwayEnvVarSummary("AI_PROVIDERS_EMBEDDING_PROVIDER", embeddingProvider));
        runtimeEnv.add(new RailwayEnvVarSummary("AI_VECTOR_DB_TYPE", vectorStrategy));
        runtimeEnv.add(new RailwayEnvVarSummary("AI_SERVICE_FEATURES_ENABLE_GENERATION", "true"));
        runtimeEnv.add(new RailwayEnvVarSummary("AI_SERVICE_FEATURES_ENABLE_EMBEDDINGS", "true"));

        boolean usesOpenAi = ManagedDeploymentProfileCatalog.usesOpenAi(providerConfig);
        runtimeEnv.add(new RailwayEnvVarSummary("OPENAI_ENABLED", Boolean.toString(usesOpenAi)));
        if (usesOpenAi) {
            runtimeEnv.add(new RailwayEnvVarSummary("OPENAI_API_KEY", "${secret:OPENAI_API_KEY}"));
            runtimeEnv.add(new RailwayEnvVarSummary("AI_PROVIDERS_OPENAI_ENABLED", "true"));
            runtimeEnv.add(new RailwayEnvVarSummary("AI_PROVIDERS_OPENAI_API_KEY", "${secret:OPENAI_API_KEY}"));
            runtimeEnv.add(new RailwayEnvVarSummary(
                "AI_PROVIDERS_OPENAI_MODEL",
                ManagedDeploymentProfileCatalog.defaultLlmModel(ManagedDeploymentProfileCatalog.LLM_PROVIDER_OPENAI)
            ));
            runtimeEnv.add(new RailwayEnvVarSummary(
                "AI_PROVIDERS_OPENAI_EMBEDDING_MODEL",
                ManagedDeploymentProfileCatalog.defaultEmbeddingModel(ManagedDeploymentProfileCatalog.EMBEDDING_PROVIDER_OPENAI)
            ));
            runtimeEnv.add(new RailwayEnvVarSummary(
                "AI_PROVIDERS_OPENAI_EMBEDDING_DIMENSIONS",
                Integer.toString(vectorDimensions)
            ));
            runtimeEnv.add(new RailwayEnvVarSummary(
                "OPENAI_MODEL",
                ManagedDeploymentProfileCatalog.defaultLlmModel(ManagedDeploymentProfileCatalog.LLM_PROVIDER_OPENAI)
            ));
            runtimeEnv.add(new RailwayEnvVarSummary(
                "OPENAI_EMBEDDING_MODEL",
                ManagedDeploymentProfileCatalog.defaultEmbeddingModel(ManagedDeploymentProfileCatalog.EMBEDDING_PROVIDER_OPENAI)
            ));
            runtimeEnv.add(new RailwayEnvVarSummary("OPENAI_EMBEDDING_DIMENSIONS", Integer.toString(vectorDimensions)));
        }

        if (ManagedDeploymentProfileCatalog.usesAnthropic(providerConfig)) {
            runtimeEnv.add(new RailwayEnvVarSummary("AI_PROVIDERS_ANTHROPIC_ENABLED", "true"));
            runtimeEnv.add(new RailwayEnvVarSummary("AI_PROVIDERS_ANTHROPIC_API_KEY", "${secret:ANTHROPIC_API_KEY}"));
            runtimeEnv.add(new RailwayEnvVarSummary(
                "AI_PROVIDERS_ANTHROPIC_MODEL",
                ManagedDeploymentProfileCatalog.defaultLlmModel(ManagedDeploymentProfileCatalog.LLM_PROVIDER_ANTHROPIC)
            ));
        }

        if (ManagedDeploymentProfileCatalog.EMBEDDING_PROVIDER_ONNX.equals(embeddingProvider)) {
            runtimeEnv.add(new RailwayEnvVarSummary("AI_PROVIDERS_ONNX_ENABLED", "true"));
        }

        if (ManagedDeploymentProfileCatalog.VECTOR_STRATEGY_LUCENE.equals(vectorStrategy)) {
            runtimeEnv.add(new RailwayEnvVarSummary(
                "AI_VECTOR_DB_LUCENE_VECTOR_DIMENSION",
                Integer.toString(vectorDimensions)
            ));
        }

        if (ManagedDeploymentProfileCatalog.usesQdrant(providerConfig)) {
            runtimeEnv.add(new RailwayEnvVarSummary("AI_PROVIDERS_QDRANT_ENABLED", "true"));
            runtimeEnv.add(new RailwayEnvVarSummary(
                "AI_PROVIDERS_QDRANT_HOST",
                ManagedDeploymentProfileCatalog.qdrantHost(providerConfig)
            ));
            runtimeEnv.add(new RailwayEnvVarSummary(
                "AI_PROVIDERS_QDRANT_PORT",
                Integer.toString(ManagedDeploymentProfileCatalog.qdrantPort(providerConfig))
            ));
            runtimeEnv.add(new RailwayEnvVarSummary(
                "AI_PROVIDERS_QDRANT_GRPC_PORT",
                Integer.toString(ManagedDeploymentProfileCatalog.qdrantGrpcPort(providerConfig))
            ));
            runtimeEnv.add(new RailwayEnvVarSummary(
                "AI_PROVIDERS_QDRANT_PREFER_GRPC",
                Boolean.toString(ManagedDeploymentProfileCatalog.qdrantPreferGrpc(providerConfig))
            ));
            if (platformSecretService.isSecretPresent("QDRANT_API_KEY")) {
                runtimeEnv.add(new RailwayEnvVarSummary("AI_PROVIDERS_QDRANT_API_KEY", "${secret:QDRANT_API_KEY}"));
            }
        }
    }

    private void addRuntimeConnectorAuthEnv(List<RailwayEnvVarSummary> runtimeEnv, JsonNode securityConfig) {
        if (ManagedDeploymentProfileCatalog.connectorApiKeyEnabled(securityConfig)) {
            runtimeEnv.add(new RailwayEnvVarSummary("ACTIONS_CONNECTOR_API_KEY", "${secret:ACTIONS_CONNECTOR_API_KEY}"));
        }
    }

    private void addConnectorProfileEnv(List<RailwayEnvVarSummary> connectorEnv,
                                        JsonNode providerConfig,
                                        String runtimeBaseUrl,
                                        JsonNode securityConfig) {
        boolean runtimeProxyEnabled = ManagedDeploymentProfileCatalog.connectorRuntimeProxyEnabled(providerConfig);
        connectorEnv.add(new RailwayEnvVarSummary(
            "REST_CONNECTOR_RUNTIME_PROXY_ENABLED",
            Boolean.toString(runtimeProxyEnabled)
        ));
        if (!runtimeProxyEnabled) {
            return;
        }
        connectorEnv.add(new RailwayEnvVarSummary("REST_CONNECTOR_RUNTIME_PROXY_BASE_URL", runtimeBaseUrl));
        connectorEnv.add(new RailwayEnvVarSummary(
            "REST_CONNECTOR_RUNTIME_PROXY_TIMEOUT_MS",
            Integer.toString(provisioningProperties.runtimeProxyTimeoutMs())
        ));
        if (ManagedDeploymentProfileCatalog.adminApiKeyEnabled(securityConfig)
            && platformSecretService.isSecretPresent("APP_ADMIN_API_KEY")) {
            connectorEnv.add(new RailwayEnvVarSummary(
                "REST_CONNECTOR_RUNTIME_PROXY_API_KEY",
                "${secret:APP_ADMIN_API_KEY}"
            ));
            connectorEnv.add(new RailwayEnvVarSummary(
                "REST_CONNECTOR_RUNTIME_PROXY_API_KEY_HEADER",
                ManagedDeploymentProfileCatalog.ADMIN_API_KEY_HEADER
            ));
        }
    }

    private String resolveRuntimeAuthzBaseUrl(JsonNode securityConfig, String connectorBaseUrl) {
        if (!ManagedDeploymentProfileCatalog.AUTHZ_MODE_REMOTE_HTTP.equals(
            ManagedDeploymentProfileCatalog.resolveAuthzMode(securityConfig)
        )) {
            return "";
        }
        String configured = text(securityConfig, "authzBaseUrl");
        return configured.isEmpty() ? connectorBaseUrl : configured;
    }

    private void addCorsEnv(List<RailwayEnvVarSummary> env, JsonNode securityConfig) {
        String allowedOrigins = text(securityConfig, "corsAllowedOrigins");
        String allowedOriginPatterns = text(securityConfig, "corsAllowedOriginPatterns");
        boolean allowCredentials = securityConfig.path("corsAllowCredentials").isBoolean()
            ? securityConfig.path("corsAllowCredentials").asBoolean()
            : provisioningProperties.corsAllowCredentials();

        addOptionalEnv(
            env,
            "CORS_ALLOWED_ORIGINS",
            allowedOrigins.isEmpty() ? provisioningProperties.corsAllowedOrigins() : allowedOrigins
        );
        addOptionalEnv(
            env,
            "CORS_ALLOWED_ORIGIN_PATTERNS",
            allowedOriginPatterns.isEmpty() ? provisioningProperties.corsAllowedOriginPatterns() : allowedOriginPatterns
        );
        env.add(new RailwayEnvVarSummary(
            "CORS_ALLOW_CREDENTIALS",
            Boolean.toString(allowCredentials)
        ));
    }

    private void addOptionalEnv(List<RailwayEnvVarSummary> env, String key, String value) {
        if (value != null && !value.isBlank()) {
            env.add(new RailwayEnvVarSummary(key, value));
        }
    }

    private String resolveRootDirectory(String dockerfilePath, String configuredRootDirectory) {
        if (dockerfilePath != null && !dockerfilePath.isBlank()) {
            // The Railway-specific Dockerfiles in this repo expect repo-root build context.
            return null;
        }
        return configuredRootDirectory;
    }
}
