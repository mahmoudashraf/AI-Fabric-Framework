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
        return buildPlan(deployment, version, null);
    }

    public RailwayProvisioningPlanSummary buildPlan(DeploymentEntity deployment,
                                                    DeploymentVersionEntity version,
                                                    JsonNode providerConfigOverride) {
        String sourceRepository = deploymentSourceResolver.resolveRepository(deployment);
        String sourceBranch = deploymentSourceResolver.resolveBranch(deployment);
        String runtimeBaseUrl = deployment.getRuntimeBaseUrl() != null
            ? deployment.getRuntimeBaseUrl()
            : "https://runtime-" + deployment.getId() + ".placeholder.local";
        String connectorBaseUrl = deployment.getConnectorBaseUrl() != null
            ? deployment.getConnectorBaseUrl()
            : "https://connector-" + deployment.getId() + ".placeholder.local";
        JsonNode providerConfig = providerConfigOverride != null && providerConfigOverride.isObject()
            ? providerConfigOverride
            : readJson(version.getProviderConfigJson());
        JsonNode entityConfig = readJson(version.getEntityConfigJson());
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
        addRuntimeProviderEnv(runtimeEnv, providerConfig, entityConfig);
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

        boolean managedVectorProvisioningEnabled = ManagedDeploymentProfileCatalog.pineconeManagedIndexEnabled(providerConfig)
            || ManagedDeploymentProfileCatalog.qdrantManagedCollectionsEnabled(providerConfig);
        List<RailwayProvisioningStepSummary> steps = new ArrayList<>();
        steps.add(new RailwayProvisioningStepSummary(1, "publish_artifacts", "Resolve immutable config artifact URLs for the selected version."));
        steps.add(new RailwayProvisioningStepSummary(2, "preflight_verification", "Block rollout unless platform, secrets, and artifact delivery prerequisites are satisfied."));
        int nextStepOrder = 3;
        if (managedVectorProvisioningEnabled) {
            steps.add(new RailwayProvisioningStepSummary(
                nextStepOrder++,
                "ensure_vector_backend",
                "Create or reconcile managed external vector resources before runtime deployment."
            ));
        }
        steps.add(new RailwayProvisioningStepSummary(nextStepOrder++, "prepare_project", "Create or reuse the Railway project for this customer environment."));
        steps.add(new RailwayProvisioningStepSummary(nextStepOrder++, "configure_runtime", "Create or update the runtime service root and its environment variables."));
        steps.add(new RailwayProvisioningStepSummary(nextStepOrder++, "configure_rest_connector", "Create or update the REST connector service root and its environment variables."));
        steps.add(new RailwayProvisioningStepSummary(nextStepOrder++, "trigger_deploy", "Commit staged changes or trigger Railway deployment/redeploy for both services."));
        steps.add(new RailwayProvisioningStepSummary(nextStepOrder++, "wait_for_active", "Wait for Railway deployment states to become active."));
        steps.add(new RailwayProvisioningStepSummary(nextStepOrder, "run_verification", "Run post-deploy verification against runtime and connector endpoints."));

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
            steps
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

    private void addRuntimeProviderEnv(List<RailwayEnvVarSummary> runtimeEnv,
                                       JsonNode providerConfig,
                                       JsonNode entityConfig) {
        String llmProvider = ManagedDeploymentProfileCatalog.resolveLlmProvider(providerConfig);
        String embeddingProvider = ManagedDeploymentProfileCatalog.resolveEmbeddingProvider(providerConfig);
        String vectorStrategy = ManagedDeploymentProfileCatalog.resolveVectorStrategy(providerConfig);
        int vectorDimensions = resolveVectorDimensions(entityConfig, embeddingProvider);

        runtimeEnv.add(new RailwayEnvVarSummary("AI_PROVIDERS_LLM_PROVIDER", llmProvider));
        runtimeEnv.add(new RailwayEnvVarSummary("AI_PROVIDERS_EMBEDDING_PROVIDER", embeddingProvider));
        runtimeEnv.add(new RailwayEnvVarSummary("AI_VECTOR_DB_TYPE", vectorStrategy));
        runtimeEnv.add(new RailwayEnvVarSummary("AI_SERVICE_FEATURES_ENABLE_GENERATION", "true"));
        runtimeEnv.add(new RailwayEnvVarSummary("AI_SERVICE_FEATURES_ENABLE_EMBEDDINGS", "true"));
        runtimeEnv.add(new RailwayEnvVarSummary(
            "AI_PROVIDERS_ENABLE_FALLBACK",
            Boolean.toString(ManagedDeploymentProfileCatalog.providerEnableFallback(providerConfig))
        ));

        boolean usesOpenAi = ManagedDeploymentProfileCatalog.usesOpenAi(providerConfig);
        runtimeEnv.add(new RailwayEnvVarSummary("OPENAI_ENABLED", Boolean.toString(usesOpenAi)));
        if (usesOpenAi) {
            addOpenAiEnv(runtimeEnv, providerConfig, llmProvider, embeddingProvider, vectorDimensions);
        }

        addAnthropicEnv(runtimeEnv, providerConfig, llmProvider);
        addAzureEnv(runtimeEnv, providerConfig, llmProvider, embeddingProvider);
        addCohereEnv(runtimeEnv, providerConfig, llmProvider, embeddingProvider);
        addGeminiEnv(runtimeEnv, providerConfig, llmProvider, embeddingProvider);
        addPurposeSpecificLlmEnv(runtimeEnv, providerConfig);
        addOnnxEnv(runtimeEnv, providerConfig, embeddingProvider);
        addRestEmbeddingEnv(runtimeEnv, providerConfig, embeddingProvider);
        addVectorBackendEnv(runtimeEnv, providerConfig, vectorStrategy, vectorDimensions);
    }

    private int resolveVectorDimensions(JsonNode entityConfig, String embeddingProvider) {
        int configured = entityConfig.path("ai-config").path("vector-dimensions").asInt(0);
        if (configured > 0) {
            return configured;
        }
        return ManagedDeploymentProfileCatalog.defaultEmbeddingDimensions(embeddingProvider);
    }

    private void addOpenAiEnv(List<RailwayEnvVarSummary> runtimeEnv,
                              JsonNode providerConfig,
                              String llmProvider,
                              String embeddingProvider,
                              int vectorDimensions) {
        runtimeEnv.add(new RailwayEnvVarSummary("OPENAI_API_KEY", "${secret:OPENAI_API_KEY}"));
        runtimeEnv.add(new RailwayEnvVarSummary("AI_PROVIDERS_OPENAI_ENABLED", "true"));
        runtimeEnv.add(new RailwayEnvVarSummary("AI_PROVIDERS_OPENAI_API_KEY", "${secret:OPENAI_API_KEY}"));
        addOptionalEnv(runtimeEnv, "AI_PROVIDERS_OPENAI_BASE_URL", ManagedDeploymentProfileCatalog.openAiBaseUrl(providerConfig));
        runtimeEnv.add(new RailwayEnvVarSummary(
            "AI_PROVIDERS_OPENAI_VALIDATE_ON_STARTUP",
            Boolean.toString(ManagedDeploymentProfileCatalog.openAiValidateOnStartup(providerConfig))
        ));
        addOptionalIntEnv(runtimeEnv, "AI_PROVIDERS_OPENAI_MAX_TOKENS", ManagedDeploymentProfileCatalog.openAiMaxTokens(providerConfig));
        addOptionalDoubleEnv(runtimeEnv, "AI_PROVIDERS_OPENAI_TEMPERATURE", ManagedDeploymentProfileCatalog.openAiTemperature(providerConfig));
        addOptionalIntEnv(runtimeEnv, "AI_PROVIDERS_OPENAI_TIMEOUT", ManagedDeploymentProfileCatalog.openAiTimeout(providerConfig));
        addOptionalIntEnv(runtimeEnv, "AI_PROVIDERS_OPENAI_PRIORITY", ManagedDeploymentProfileCatalog.openAiPriority(providerConfig));
        if (ManagedDeploymentProfileCatalog.usesLlmProvider(providerConfig, ManagedDeploymentProfileCatalog.LLM_PROVIDER_OPENAI)) {
            runtimeEnv.add(new RailwayEnvVarSummary(
                "AI_PROVIDERS_OPENAI_MODEL",
                ManagedDeploymentProfileCatalog.openAiModel(providerConfig)
            ));
            if (ManagedDeploymentProfileCatalog.LLM_PROVIDER_OPENAI.equals(llmProvider)) {
                runtimeEnv.add(new RailwayEnvVarSummary(
                    "OPENAI_MODEL",
                    ManagedDeploymentProfileCatalog.openAiModel(providerConfig)
                ));
            }
        }
        if (ManagedDeploymentProfileCatalog.EMBEDDING_PROVIDER_OPENAI.equals(embeddingProvider)) {
            int configuredDimensions = ManagedDeploymentProfileCatalog.openAiEmbeddingDimensions(providerConfig);
            int effectiveDimensions = configuredDimensions > 0 ? configuredDimensions : vectorDimensions;
            runtimeEnv.add(new RailwayEnvVarSummary(
                "AI_PROVIDERS_OPENAI_EMBEDDING_MODEL",
                ManagedDeploymentProfileCatalog.openAiEmbeddingModel(providerConfig)
            ));
            runtimeEnv.add(new RailwayEnvVarSummary(
                "AI_PROVIDERS_OPENAI_EMBEDDING_DIMENSIONS",
                Integer.toString(effectiveDimensions)
            ));
            runtimeEnv.add(new RailwayEnvVarSummary(
                "OPENAI_EMBEDDING_MODEL",
                ManagedDeploymentProfileCatalog.openAiEmbeddingModel(providerConfig)
            ));
            runtimeEnv.add(new RailwayEnvVarSummary("OPENAI_EMBEDDING_DIMENSIONS", Integer.toString(effectiveDimensions)));
        }
    }

    private void addAnthropicEnv(List<RailwayEnvVarSummary> runtimeEnv, JsonNode providerConfig, String llmProvider) {
        if (!ManagedDeploymentProfileCatalog.usesAnthropic(providerConfig)) {
            return;
        }
        runtimeEnv.add(new RailwayEnvVarSummary("AI_PROVIDERS_ANTHROPIC_ENABLED", "true"));
        runtimeEnv.add(new RailwayEnvVarSummary("AI_PROVIDERS_ANTHROPIC_API_KEY", "${secret:ANTHROPIC_API_KEY}"));
        addOptionalEnv(runtimeEnv, "AI_PROVIDERS_ANTHROPIC_BASE_URL", ManagedDeploymentProfileCatalog.anthropicBaseUrl(providerConfig));
        runtimeEnv.add(new RailwayEnvVarSummary(
            "AI_PROVIDERS_ANTHROPIC_MODEL",
            ManagedDeploymentProfileCatalog.anthropicModel(providerConfig)
        ));
        addOptionalIntEnv(runtimeEnv, "AI_PROVIDERS_ANTHROPIC_MAX_TOKENS", ManagedDeploymentProfileCatalog.anthropicMaxTokens(providerConfig));
        addOptionalDoubleEnv(runtimeEnv, "AI_PROVIDERS_ANTHROPIC_TEMPERATURE", ManagedDeploymentProfileCatalog.anthropicTemperature(providerConfig));
        addOptionalIntEnv(runtimeEnv, "AI_PROVIDERS_ANTHROPIC_TIMEOUT", ManagedDeploymentProfileCatalog.anthropicTimeout(providerConfig));
        addOptionalIntEnv(runtimeEnv, "AI_PROVIDERS_ANTHROPIC_PRIORITY", ManagedDeploymentProfileCatalog.anthropicPriority(providerConfig));
    }

    private void addAzureEnv(List<RailwayEnvVarSummary> runtimeEnv,
                             JsonNode providerConfig,
                             String llmProvider,
                             String embeddingProvider) {
        if (!ManagedDeploymentProfileCatalog.usesAzure(providerConfig)) {
            return;
        }
        runtimeEnv.add(new RailwayEnvVarSummary("AI_PROVIDERS_AZURE_ENABLED", "true"));
        runtimeEnv.add(new RailwayEnvVarSummary("AI_PROVIDERS_AZURE_API_KEY", "${secret:AZURE_OPENAI_API_KEY}"));
        addOptionalEnv(runtimeEnv, "AI_PROVIDERS_AZURE_ENDPOINT", ManagedDeploymentProfileCatalog.azureEndpoint(providerConfig));
        addOptionalEnv(runtimeEnv, "AI_PROVIDERS_AZURE_API_VERSION", ManagedDeploymentProfileCatalog.azureApiVersion(providerConfig));
        runtimeEnv.add(new RailwayEnvVarSummary(
            "AI_PROVIDERS_AZURE_VALIDATE_ON_STARTUP",
            Boolean.toString(ManagedDeploymentProfileCatalog.azureValidateOnStartup(providerConfig))
        ));
        addOptionalIntEnv(runtimeEnv, "AI_PROVIDERS_AZURE_TIMEOUT", ManagedDeploymentProfileCatalog.azureTimeout(providerConfig));
        addOptionalIntEnv(runtimeEnv, "AI_PROVIDERS_AZURE_PRIORITY", ManagedDeploymentProfileCatalog.azurePriority(providerConfig));
        if (ManagedDeploymentProfileCatalog.usesLlmProvider(providerConfig, ManagedDeploymentProfileCatalog.LLM_PROVIDER_AZURE)) {
            addOptionalEnv(runtimeEnv, "AI_PROVIDERS_AZURE_DEPLOYMENT_NAME", ManagedDeploymentProfileCatalog.azureDeploymentName(providerConfig));
        }
        if (ManagedDeploymentProfileCatalog.EMBEDDING_PROVIDER_AZURE.equals(embeddingProvider)) {
            addOptionalEnv(
                runtimeEnv,
                "AI_PROVIDERS_AZURE_EMBEDDING_DEPLOYMENT_NAME",
                ManagedDeploymentProfileCatalog.azureEmbeddingDeploymentName(providerConfig)
            );
        }
    }

    private void addCohereEnv(List<RailwayEnvVarSummary> runtimeEnv,
                              JsonNode providerConfig,
                              String llmProvider,
                              String embeddingProvider) {
        if (!ManagedDeploymentProfileCatalog.usesCohere(providerConfig)) {
            return;
        }
        runtimeEnv.add(new RailwayEnvVarSummary("AI_PROVIDERS_COHERE_ENABLED", "true"));
        runtimeEnv.add(new RailwayEnvVarSummary("AI_PROVIDERS_COHERE_API_KEY", "${secret:COHERE_API_KEY}"));
        addOptionalEnv(runtimeEnv, "AI_PROVIDERS_COHERE_BASE_URL", ManagedDeploymentProfileCatalog.cohereBaseUrl(providerConfig));
        runtimeEnv.add(new RailwayEnvVarSummary(
            "AI_PROVIDERS_COHERE_VALIDATE_ON_STARTUP",
            Boolean.toString(ManagedDeploymentProfileCatalog.cohereValidateOnStartup(providerConfig))
        ));
        addOptionalIntEnv(runtimeEnv, "AI_PROVIDERS_COHERE_MAX_TOKENS", ManagedDeploymentProfileCatalog.cohereMaxTokens(providerConfig));
        addOptionalDoubleEnv(runtimeEnv, "AI_PROVIDERS_COHERE_TEMPERATURE", ManagedDeploymentProfileCatalog.cohereTemperature(providerConfig));
        addOptionalIntEnv(runtimeEnv, "AI_PROVIDERS_COHERE_TIMEOUT", ManagedDeploymentProfileCatalog.cohereTimeout(providerConfig));
        addOptionalIntEnv(runtimeEnv, "AI_PROVIDERS_COHERE_PRIORITY", ManagedDeploymentProfileCatalog.coherePriority(providerConfig));
        if (ManagedDeploymentProfileCatalog.usesLlmProvider(providerConfig, ManagedDeploymentProfileCatalog.LLM_PROVIDER_COHERE)) {
            addOptionalEnv(runtimeEnv, "AI_PROVIDERS_COHERE_MODEL", ManagedDeploymentProfileCatalog.cohereModel(providerConfig));
        }
        if (ManagedDeploymentProfileCatalog.EMBEDDING_PROVIDER_COHERE.equals(embeddingProvider)) {
            addOptionalEnv(
                runtimeEnv,
                "AI_PROVIDERS_COHERE_EMBEDDING_MODEL",
                ManagedDeploymentProfileCatalog.cohereEmbeddingModel(providerConfig)
            );
        }
    }

    private void addGeminiEnv(List<RailwayEnvVarSummary> runtimeEnv,
                              JsonNode providerConfig,
                              String llmProvider,
                              String embeddingProvider) {
        if (!ManagedDeploymentProfileCatalog.usesGemini(providerConfig)) {
            return;
        }
        runtimeEnv.add(new RailwayEnvVarSummary("AI_PROVIDERS_GEMINI_ENABLED", "true"));
        runtimeEnv.add(new RailwayEnvVarSummary("AI_PROVIDERS_GEMINI_API_KEY", "${secret:GEMINI_API_KEY}"));
        addOptionalEnv(runtimeEnv, "AI_PROVIDERS_GEMINI_BASE_URL", ManagedDeploymentProfileCatalog.geminiBaseUrl(providerConfig));
        runtimeEnv.add(new RailwayEnvVarSummary(
            "AI_PROVIDERS_GEMINI_VALIDATE_ON_STARTUP",
            Boolean.toString(ManagedDeploymentProfileCatalog.geminiValidateOnStartup(providerConfig))
        ));
        addOptionalIntEnv(runtimeEnv, "AI_PROVIDERS_GEMINI_MAX_TOKENS", ManagedDeploymentProfileCatalog.geminiMaxTokens(providerConfig));
        addOptionalDoubleEnv(runtimeEnv, "AI_PROVIDERS_GEMINI_TEMPERATURE", ManagedDeploymentProfileCatalog.geminiTemperature(providerConfig));
        addOptionalIntEnv(runtimeEnv, "AI_PROVIDERS_GEMINI_TIMEOUT", ManagedDeploymentProfileCatalog.geminiTimeout(providerConfig));
        addOptionalIntEnv(runtimeEnv, "AI_PROVIDERS_GEMINI_PRIORITY", ManagedDeploymentProfileCatalog.geminiPriority(providerConfig));
        if (ManagedDeploymentProfileCatalog.usesLlmProvider(providerConfig, ManagedDeploymentProfileCatalog.LLM_PROVIDER_GEMINI)) {
            addOptionalEnv(runtimeEnv, "AI_PROVIDERS_GEMINI_MODEL", ManagedDeploymentProfileCatalog.geminiModel(providerConfig));
        }
        if (ManagedDeploymentProfileCatalog.EMBEDDING_PROVIDER_GEMINI.equals(embeddingProvider)) {
            addOptionalEnv(
                runtimeEnv,
                "AI_PROVIDERS_GEMINI_EMBEDDING_MODEL",
                ManagedDeploymentProfileCatalog.geminiEmbeddingModel(providerConfig)
            );
        }
    }

    private void addOnnxEnv(List<RailwayEnvVarSummary> runtimeEnv,
                            JsonNode providerConfig,
                            String embeddingProvider) {
        if (ManagedDeploymentProfileCatalog.EMBEDDING_PROVIDER_ONNX.equals(embeddingProvider)) {
            runtimeEnv.add(new RailwayEnvVarSummary("AI_PROVIDERS_ONNX_ENABLED", "true"));
            addOptionalEnv(runtimeEnv, "AI_PROVIDERS_ONNX_MODEL_ALIAS", ManagedDeploymentProfileCatalog.onnxModelAlias(providerConfig));
            addOptionalEnv(runtimeEnv, "AI_PROVIDERS_ONNX_MODEL_PATH", ManagedDeploymentProfileCatalog.onnxModelPath(providerConfig));
            addOptionalEnv(runtimeEnv, "AI_PROVIDERS_ONNX_TOKENIZER_PATH", ManagedDeploymentProfileCatalog.onnxTokenizerPath(providerConfig));
            runtimeEnv.add(new RailwayEnvVarSummary(
                "AI_PROVIDERS_ONNX_MAX_SEQUENCE_LENGTH",
                Integer.toString(ManagedDeploymentProfileCatalog.onnxMaxSequenceLength(providerConfig))
            ));
            runtimeEnv.add(new RailwayEnvVarSummary(
                "AI_PROVIDERS_ONNX_USE_GPU",
                Boolean.toString(ManagedDeploymentProfileCatalog.onnxUseGpu(providerConfig))
            ));
        }
    }

    private void addRestEmbeddingEnv(List<RailwayEnvVarSummary> runtimeEnv,
                                     JsonNode providerConfig,
                                     String embeddingProvider) {
        if (!ManagedDeploymentProfileCatalog.EMBEDDING_PROVIDER_REST.equals(embeddingProvider)) {
            return;
        }
        runtimeEnv.add(new RailwayEnvVarSummary("AI_PROVIDERS_REST_ENABLED", "true"));
        runtimeEnv.add(new RailwayEnvVarSummary(
            "AI_PROVIDERS_REST_VALIDATE_ON_STARTUP",
            Boolean.toString(ManagedDeploymentProfileCatalog.restEmbeddingValidateOnStartup(providerConfig))
        ));
        addOptionalEnv(runtimeEnv, "AI_PROVIDERS_REST_BASE_URL", ManagedDeploymentProfileCatalog.restEmbeddingBaseUrl(providerConfig));
        addOptionalEnv(runtimeEnv, "AI_PROVIDERS_REST_ENDPOINT", ManagedDeploymentProfileCatalog.restEmbeddingEndpoint(providerConfig));
        addOptionalEnv(runtimeEnv, "AI_PROVIDERS_REST_BATCH_ENDPOINT", ManagedDeploymentProfileCatalog.restEmbeddingBatchEndpoint(providerConfig));
        addOptionalEnv(runtimeEnv, "AI_PROVIDERS_REST_MODEL", ManagedDeploymentProfileCatalog.restEmbeddingModel(providerConfig));
        runtimeEnv.add(new RailwayEnvVarSummary(
            "AI_PROVIDERS_REST_TIMEOUT",
            Integer.toString(ManagedDeploymentProfileCatalog.restEmbeddingTimeoutMs(providerConfig))
        ));
    }

    private void addVectorBackendEnv(List<RailwayEnvVarSummary> runtimeEnv,
                                     JsonNode providerConfig,
                                     String vectorStrategy,
                                     int vectorDimensions) {
        if (ManagedDeploymentProfileCatalog.VECTOR_STRATEGY_LUCENE.equals(vectorStrategy)) {
            runtimeEnv.add(new RailwayEnvVarSummary(
                "AI_VECTOR_DB_LUCENE_VECTOR_DIMENSION",
                Integer.toString(vectorDimensions)
            ));
            return;
        }
        if (ManagedDeploymentProfileCatalog.usesMemoryVector(providerConfig)) {
            return;
        }
        if (ManagedDeploymentProfileCatalog.usesQdrant(providerConfig)) {
            runtimeEnv.add(new RailwayEnvVarSummary("AI_PROVIDERS_QDRANT_ENABLED", "true"));
            runtimeEnv.add(new RailwayEnvVarSummary("AI_PROVIDERS_QDRANT_HOST", ManagedDeploymentProfileCatalog.qdrantHost(providerConfig)));
            runtimeEnv.add(new RailwayEnvVarSummary("AI_PROVIDERS_QDRANT_PORT", Integer.toString(ManagedDeploymentProfileCatalog.qdrantPort(providerConfig))));
            runtimeEnv.add(new RailwayEnvVarSummary("AI_PROVIDERS_QDRANT_GRPC_PORT", Integer.toString(ManagedDeploymentProfileCatalog.qdrantGrpcPort(providerConfig))));
            runtimeEnv.add(new RailwayEnvVarSummary("AI_PROVIDERS_QDRANT_PREFER_GRPC", Boolean.toString(ManagedDeploymentProfileCatalog.qdrantPreferGrpc(providerConfig))));
            addOptionalIntEnv(runtimeEnv, "AI_PROVIDERS_QDRANT_TIMEOUT", ManagedDeploymentProfileCatalog.qdrantTimeout(providerConfig));
            if (platformSecretService.isSecretPresent("QDRANT_API_KEY")) {
                runtimeEnv.add(new RailwayEnvVarSummary("AI_PROVIDERS_QDRANT_API_KEY", "${secret:QDRANT_API_KEY}"));
            }
            return;
        }
        if (ManagedDeploymentProfileCatalog.usesPinecone(providerConfig)) {
            runtimeEnv.add(new RailwayEnvVarSummary("AI_PROVIDERS_PINECONE_ENABLED", "true"));
            runtimeEnv.add(new RailwayEnvVarSummary("AI_PROVIDERS_PINECONE_API_KEY", "${secret:PINECONE_API_KEY}"));
            addOptionalEnv(runtimeEnv, "AI_PROVIDERS_PINECONE_ENVIRONMENT", ManagedDeploymentProfileCatalog.pineconeEnvironment(providerConfig));
            addOptionalEnv(runtimeEnv, "AI_PROVIDERS_PINECONE_INDEX_NAME", ManagedDeploymentProfileCatalog.pineconeIndexName(providerConfig));
            addOptionalEnv(runtimeEnv, "AI_PROVIDERS_PINECONE_PROJECT_ID", ManagedDeploymentProfileCatalog.pineconeProjectId(providerConfig));
            addOptionalEnv(runtimeEnv, "AI_PROVIDERS_PINECONE_API_HOST", ManagedDeploymentProfileCatalog.pineconeApiHost(providerConfig));
            runtimeEnv.add(new RailwayEnvVarSummary("AI_PROVIDERS_PINECONE_DIMENSIONS", Integer.toString(
                ManagedDeploymentProfileCatalog.pineconeDimensions(providerConfig) > 0
                    ? ManagedDeploymentProfileCatalog.pineconeDimensions(providerConfig)
                    : vectorDimensions
            )));
            return;
        }
        if (ManagedDeploymentProfileCatalog.usesWeaviate(providerConfig)) {
            runtimeEnv.add(new RailwayEnvVarSummary("AI_PROVIDERS_WEAVIATE_ENABLED", "true"));
            runtimeEnv.add(new RailwayEnvVarSummary("AI_PROVIDERS_WEAVIATE_SCHEME", ManagedDeploymentProfileCatalog.weaviateScheme(providerConfig)));
            runtimeEnv.add(new RailwayEnvVarSummary("AI_PROVIDERS_WEAVIATE_HOST", ManagedDeploymentProfileCatalog.weaviateHost(providerConfig)));
            runtimeEnv.add(new RailwayEnvVarSummary("AI_PROVIDERS_WEAVIATE_PORT", Integer.toString(ManagedDeploymentProfileCatalog.weaviatePort(providerConfig))));
            runtimeEnv.add(new RailwayEnvVarSummary(
                "AI_PROVIDERS_WEAVIATE_CONSISTENCY_LEVEL_STRONG",
                Boolean.toString(ManagedDeploymentProfileCatalog.weaviateConsistencyLevelStrong(providerConfig))
            ));
            addOptionalIntEnv(runtimeEnv, "AI_PROVIDERS_WEAVIATE_TIMEOUT", ManagedDeploymentProfileCatalog.weaviateTimeout(providerConfig));
            if (platformSecretService.isSecretPresent("WEAVIATE_API_KEY")) {
                runtimeEnv.add(new RailwayEnvVarSummary("AI_PROVIDERS_WEAVIATE_API_KEY", "${secret:WEAVIATE_API_KEY}"));
            }
            return;
        }
        if (ManagedDeploymentProfileCatalog.usesMilvus(providerConfig)) {
            runtimeEnv.add(new RailwayEnvVarSummary("AI_PROVIDERS_MILVUS_ENABLED", "true"));
            runtimeEnv.add(new RailwayEnvVarSummary("AI_PROVIDERS_MILVUS_HOST", ManagedDeploymentProfileCatalog.milvusHost(providerConfig)));
            runtimeEnv.add(new RailwayEnvVarSummary("AI_PROVIDERS_MILVUS_PORT", Integer.toString(ManagedDeploymentProfileCatalog.milvusPort(providerConfig))));
            runtimeEnv.add(new RailwayEnvVarSummary("AI_PROVIDERS_MILVUS_DATABASE_NAME", ManagedDeploymentProfileCatalog.milvusDatabaseName(providerConfig)));
            runtimeEnv.add(new RailwayEnvVarSummary("AI_PROVIDERS_MILVUS_SECURE", Boolean.toString(ManagedDeploymentProfileCatalog.milvusSecure(providerConfig))));
            runtimeEnv.add(new RailwayEnvVarSummary("AI_PROVIDERS_MILVUS_FLUSH_ON_WRITE", Boolean.toString(ManagedDeploymentProfileCatalog.milvusFlushOnWrite(providerConfig))));
            addOptionalIntEnv(runtimeEnv, "AI_PROVIDERS_MILVUS_TIMEOUT", ManagedDeploymentProfileCatalog.milvusTimeout(providerConfig));
            if (platformSecretService.isSecretPresent("MILVUS_USERNAME")) {
                runtimeEnv.add(new RailwayEnvVarSummary("AI_PROVIDERS_MILVUS_USERNAME", "${secret:MILVUS_USERNAME}"));
            }
            if (platformSecretService.isSecretPresent("MILVUS_PASSWORD")) {
                runtimeEnv.add(new RailwayEnvVarSummary("AI_PROVIDERS_MILVUS_PASSWORD", "${secret:MILVUS_PASSWORD}"));
            }
        }
    }

    private void addPurposeSpecificLlmEnv(List<RailwayEnvVarSummary> runtimeEnv, JsonNode providerConfig) {
        addOptionalEnv(runtimeEnv, "AI_PROVIDERS_ORCHESTRATION_LLM_PROVIDER", ManagedDeploymentProfileCatalog.orchestrationLlmProvider(providerConfig));
        addOptionalEnv(runtimeEnv, "AI_PROVIDERS_ORCHESTRATION_MODEL", ManagedDeploymentProfileCatalog.orchestrationModel(providerConfig));
        addOptionalIntEnv(runtimeEnv, "AI_PROVIDERS_ORCHESTRATION_MAX_TOKENS", ManagedDeploymentProfileCatalog.orchestrationMaxTokens(providerConfig));
        addOptionalDoubleEnv(runtimeEnv, "AI_PROVIDERS_ORCHESTRATION_TEMPERATURE", ManagedDeploymentProfileCatalog.orchestrationTemperature(providerConfig));
        addOptionalIntEnv(runtimeEnv, "AI_PROVIDERS_ORCHESTRATION_TIMEOUT", ManagedDeploymentProfileCatalog.orchestrationTimeout(providerConfig));

        addOptionalEnv(runtimeEnv, "AI_PROVIDERS_GENERATION_LLM_PROVIDER", ManagedDeploymentProfileCatalog.generationLlmProvider(providerConfig));
        addOptionalEnv(runtimeEnv, "AI_PROVIDERS_GENERATION_MODEL", ManagedDeploymentProfileCatalog.generationModel(providerConfig));
        addOptionalIntEnv(runtimeEnv, "AI_PROVIDERS_GENERATION_MAX_TOKENS", ManagedDeploymentProfileCatalog.generationMaxTokens(providerConfig));
        addOptionalDoubleEnv(runtimeEnv, "AI_PROVIDERS_GENERATION_TEMPERATURE", ManagedDeploymentProfileCatalog.generationTemperature(providerConfig));
        addOptionalIntEnv(runtimeEnv, "AI_PROVIDERS_GENERATION_TIMEOUT", ManagedDeploymentProfileCatalog.generationTimeout(providerConfig));
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

    private void addOptionalIntEnv(List<RailwayEnvVarSummary> env, String key, int value) {
        if (value > 0) {
            env.add(new RailwayEnvVarSummary(key, Integer.toString(value)));
        }
    }

    private void addOptionalDoubleEnv(List<RailwayEnvVarSummary> env, String key, Double value) {
        if (value != null) {
            env.add(new RailwayEnvVarSummary(key, Double.toString(value)));
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
