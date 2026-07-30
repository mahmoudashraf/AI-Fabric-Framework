package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.config.PlatformDeliveryProperties;
import com.ai.fabric.platform.backend.config.PlatformInferenceProvisioningProperties;
import com.ai.fabric.platform.backend.config.PlatformProvisioningProperties;
import com.ai.fabric.platform.backend.config.PlatformVectorizationProperties;
import com.ai.fabric.platform.backend.config.PlatformVectorizationRunnerProvisioningProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.RailwayArtifactUrlsSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayEnvVarSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayProvisioningPlanSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayProvisioningServicesSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayProvisioningStepSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayServicePlanSummary;
import com.ai.fabric.platform.backend.productservice.entity.PlatformManagedProductServiceEntity;
import com.ai.fabric.platform.backend.productservice.repository.PlatformManagedProductServiceRepository;
import com.ai.fabric.platform.backend.security.RuntimePrivateAccessSupport;
import com.ai.fabric.platform.backend.secret.service.DeploymentProviderSecretResolutionService;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationPlanEntity;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationPlanRepository;
import com.ai.fabric.platform.backend.vectorization.service.VectorizationManagedSecretNames;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class RailwayProvisioningPlanService {

    private static final String RUNTIME_TRUSTED_BACKEND_SECRET = "AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY";
    private static final String RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY_SECRET = "AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY";
    private static final String RUNTIME_PUBLIC_TOKEN_SIGNING_KEY_SECRET = "AI_FABRIC_RUNTIME_PUBLIC_TOKEN_SIGNING_KEY";
    private static final String CONNECTOR_ADMIN_SECRET = "APP_ADMIN_API_KEY";
    private static final String SHOPIFY_BRIDGE_SHARED_SECRET_ENV = "SHOPIFY_BRIDGE_SHARED_SECRET";
    private static final Pattern MCP_SECRET_REF_PATTERN = Pattern.compile("^MCP_SECRET_[A-Z0-9_]+$");

    private final PlatformProvisioningProperties provisioningProperties;
    private final PlatformDeliveryProperties deliveryProperties;
    private final PlatformVectorizationProperties vectorizationProperties;
    private final PlatformVectorizationRunnerProvisioningProperties vectorizationRunnerProvisioningProperties;
    private final PlatformInferenceProvisioningProperties inferenceProvisioningProperties;
    private final DeploymentArtifactService artifactService;
    private final DeploymentSourceResolver deploymentSourceResolver;
    private final PlatformSecretService platformSecretService;
    private final DeploymentProviderSecretResolutionService deploymentProviderSecretResolutionService;
    private final VectorizationPlanRepository vectorizationPlanRepository;
    private final TenantScopedVectorHandleResolver tenantScopedVectorHandleResolver;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private ShopifyStoreConnectionRepository shopifyStoreConnectionRepository;

    @Autowired(required = false)
    private PlatformManagedProductServiceRepository platformManagedProductServiceRepository;

    RailwayProvisioningPlanService(PlatformProvisioningProperties provisioningProperties,
                                   PlatformDeliveryProperties deliveryProperties,
                                   DeploymentArtifactService artifactService,
                                   DeploymentSourceResolver deploymentSourceResolver,
                                   PlatformSecretService platformSecretService,
                                   ObjectMapper objectMapper) {
        this(
            provisioningProperties,
            deliveryProperties,
            new PlatformVectorizationProperties(null, null, null, 0, null, null),
            new PlatformVectorizationRunnerProvisioningProperties(null, null, null, null, null),
            new PlatformInferenceProvisioningProperties(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null),
            artifactService,
            deploymentSourceResolver,
            platformSecretService,
            new DeploymentProviderSecretResolutionService(platformSecretService),
            null,
            objectMapper
        );
    }

    RailwayProvisioningPlanService(PlatformProvisioningProperties provisioningProperties,
                                   PlatformDeliveryProperties deliveryProperties,
                                   PlatformVectorizationProperties vectorizationProperties,
                                   PlatformVectorizationRunnerProvisioningProperties vectorizationRunnerProvisioningProperties,
                                   DeploymentArtifactService artifactService,
                                   DeploymentSourceResolver deploymentSourceResolver,
                                   PlatformSecretService platformSecretService,
                                   VectorizationPlanRepository vectorizationPlanRepository,
                                   ObjectMapper objectMapper) {
        this(
            provisioningProperties,
            deliveryProperties,
            vectorizationProperties,
            vectorizationRunnerProvisioningProperties,
            new PlatformInferenceProvisioningProperties(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null),
            artifactService,
            deploymentSourceResolver,
            platformSecretService,
            vectorizationPlanRepository,
            objectMapper
        );
    }

    RailwayProvisioningPlanService(PlatformProvisioningProperties provisioningProperties,
                                   PlatformDeliveryProperties deliveryProperties,
                                   PlatformVectorizationProperties vectorizationProperties,
                                   PlatformVectorizationRunnerProvisioningProperties vectorizationRunnerProvisioningProperties,
                                   PlatformInferenceProvisioningProperties inferenceProvisioningProperties,
                                   DeploymentArtifactService artifactService,
                                   DeploymentSourceResolver deploymentSourceResolver,
                                   PlatformSecretService platformSecretService,
                                   VectorizationPlanRepository vectorizationPlanRepository,
                                   ObjectMapper objectMapper) {
        this(
            provisioningProperties,
            deliveryProperties,
            vectorizationProperties,
            vectorizationRunnerProvisioningProperties,
            inferenceProvisioningProperties,
            artifactService,
            deploymentSourceResolver,
            platformSecretService,
            new DeploymentProviderSecretResolutionService(platformSecretService),
            vectorizationPlanRepository,
            objectMapper
        );
    }

    RailwayProvisioningPlanService(PlatformProvisioningProperties provisioningProperties,
                                   PlatformDeliveryProperties deliveryProperties,
                                   PlatformVectorizationProperties vectorizationProperties,
                                   PlatformVectorizationRunnerProvisioningProperties vectorizationRunnerProvisioningProperties,
                                   DeploymentArtifactService artifactService,
                                   DeploymentSourceResolver deploymentSourceResolver,
                                   PlatformSecretService platformSecretService,
                                   DeploymentProviderSecretResolutionService deploymentProviderSecretResolutionService,
                                   VectorizationPlanRepository vectorizationPlanRepository,
                                   ObjectMapper objectMapper) {
        this(
            provisioningProperties,
            deliveryProperties,
            vectorizationProperties,
            vectorizationRunnerProvisioningProperties,
            new PlatformInferenceProvisioningProperties(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null),
            artifactService,
            deploymentSourceResolver,
            platformSecretService,
            deploymentProviderSecretResolutionService,
            vectorizationPlanRepository,
            objectMapper
        );
    }

    RailwayProvisioningPlanService(PlatformProvisioningProperties provisioningProperties,
                                   PlatformDeliveryProperties deliveryProperties,
                                   PlatformVectorizationProperties vectorizationProperties,
                                   PlatformVectorizationRunnerProvisioningProperties vectorizationRunnerProvisioningProperties,
                                   PlatformInferenceProvisioningProperties inferenceProvisioningProperties,
                                   DeploymentArtifactService artifactService,
                                   DeploymentSourceResolver deploymentSourceResolver,
                                   PlatformSecretService platformSecretService,
                                   DeploymentProviderSecretResolutionService deploymentProviderSecretResolutionService,
                                   VectorizationPlanRepository vectorizationPlanRepository,
                                   ObjectMapper objectMapper) {
        this(
            provisioningProperties,
            deliveryProperties,
            vectorizationProperties,
            vectorizationRunnerProvisioningProperties,
            inferenceProvisioningProperties,
            artifactService,
            deploymentSourceResolver,
            platformSecretService,
            deploymentProviderSecretResolutionService,
            vectorizationPlanRepository,
            new TenantScopedVectorHandleResolver(),
            objectMapper
        );
    }

    @Autowired
    public RailwayProvisioningPlanService(PlatformProvisioningProperties provisioningProperties,
                                          PlatformDeliveryProperties deliveryProperties,
                                          PlatformVectorizationProperties vectorizationProperties,
                                          PlatformVectorizationRunnerProvisioningProperties vectorizationRunnerProvisioningProperties,
                                          PlatformInferenceProvisioningProperties inferenceProvisioningProperties,
                                          DeploymentArtifactService artifactService,
                                          DeploymentSourceResolver deploymentSourceResolver,
                                          PlatformSecretService platformSecretService,
                                          DeploymentProviderSecretResolutionService deploymentProviderSecretResolutionService,
                                          VectorizationPlanRepository vectorizationPlanRepository,
                                          TenantScopedVectorHandleResolver tenantScopedVectorHandleResolver,
                                          ObjectMapper objectMapper) {
        this.provisioningProperties = provisioningProperties;
        this.deliveryProperties = deliveryProperties;
        this.vectorizationProperties = vectorizationProperties;
        this.vectorizationRunnerProvisioningProperties = vectorizationRunnerProvisioningProperties;
        this.inferenceProvisioningProperties = inferenceProvisioningProperties;
        this.artifactService = artifactService;
        this.deploymentSourceResolver = deploymentSourceResolver;
        this.platformSecretService = platformSecretService;
        this.deploymentProviderSecretResolutionService = deploymentProviderSecretResolutionService;
        this.vectorizationPlanRepository = vectorizationPlanRepository;
        this.tenantScopedVectorHandleResolver = tenantScopedVectorHandleResolver;
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
        JsonNode actionsConfig = readJson(version.getActionsConfigJson());
        JsonNode entityConfig = readJson(version.getEntityConfigJson());
        JsonNode securityConfig = readJson(version.getSecurityConfigJson());

        var artifacts = artifactService.toBundleSummary(version);
        JsonNode manifest = readJson(version.getManifestJson());
        String aiFabricFrameworkVersion = requireReleaseMetadata(
            "aiFabricFrameworkVersion",
            version.getAiFabricFrameworkVersion()
        );
        String entityConfigContractVersion = requireReleaseMetadata(
            "entityConfigContractVersion",
            version.getEntityConfigContractVersion()
        );
        String entityConfigHash = requireReleaseMetadata(
            "entityConfigHash",
            manifest.path("entityConfigHash").asText("")
        );
        String deploymentVersionId = requireReleaseMetadata(
            "deploymentVersionId",
            version.getId()
        );
        RailwayArtifactUrlsSummary artifactUrls = new RailwayArtifactUrlsSummary(
            artifacts.actionsArtifactUrl(),
            artifacts.entityArtifactUrl(),
            artifacts.routingArtifactUrl(),
            artifacts.promptArtifactUrl(),
            artifacts.knowledgeSourceArtifactUrl(),
            artifacts.shellArtifactUrl(),
            artifacts.manifestUrl()
        );

        List<RailwayEnvVarSummary> runtimeEnv = new ArrayList<>();
        runtimeEnv.add(new RailwayEnvVarSummary("AI_ACTIONS_CATALOG_PATH", artifactUrls.actions()));
        runtimeEnv.add(new RailwayEnvVarSummary("AI_CONFIG_DEFAULT_FILE", artifactUrls.entities()));
        runtimeEnv.add(new RailwayEnvVarSummary(
            "AI_FABRIC_FRAMEWORK_VERSION",
            aiFabricFrameworkVersion
        ));
        runtimeEnv.add(new RailwayEnvVarSummary(
            "AI_ENTITY_CONFIG_CONTRACT_VERSION",
            entityConfigContractVersion
        ));
        runtimeEnv.add(new RailwayEnvVarSummary(
            "AI_ENTITY_CONFIG_HASH",
            entityConfigHash
        ));
        runtimeEnv.add(new RailwayEnvVarSummary(
            "AI_ENTITY_ARTIFACT_URL",
            artifactUrls.entities()
        ));
        runtimeEnv.add(new RailwayEnvVarSummary(
            "PLATFORM_DEPLOYMENT_VERSION_ID",
            deploymentVersionId
        ));
        runtimeEnv.add(new RailwayEnvVarSummary("AI_PROMPTS_DEPLOYMENT_CONFIG_FILE", artifactUrls.prompts()));
        addOptionalEnv(runtimeEnv, "AI_KNOWLEDGE_SOURCES_DEPLOYMENT_CONFIG_FILE", artifactUrls.knowledgeSources());
        addOptionalEnv(runtimeEnv, "AI_SHELL_DEPLOYMENT_CONFIG_FILE", artifactUrls.shell());
        runtimeEnv.add(new RailwayEnvVarSummary("ACTIONS_CONNECTOR_BASE_URL", connectorBaseUrl));
        addRuntimeProviderEnv(runtimeEnv, deployment, providerConfig, entityConfig);
        addRuntimeConnectorAuthEnv(runtimeEnv, securityConfig);
        addRuntimeMcpGatewayEnv(runtimeEnv, actionsConfig);
        addRuntimeWebhookTargetEnv(runtimeEnv, actionsConfig);
        addOptionalEnv(runtimeEnv, "AI_CURATED_PACK", resolveRuntimeCuratedPack(providerConfig));
        addRuntimeIngressAuthEnv(runtimeEnv, deployment, securityConfig);
        addRuntimePublicTokenValidationEnv(runtimeEnv, securityConfig);
        runtimeEnv.add(new RailwayEnvVarSummary(
            "AI_FABRIC_RUNTIME_AUTHZ_MODE",
            ManagedDeploymentProfileCatalog.resolveAuthzMode(securityConfig)
        ));
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
        addShopifyBridgeConnectorEnv(connectorEnv, deployment);
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

        RailwayServicePlanSummary vectorizationRunner = buildVectorizationRunnerPlan(deployment);
        RailwayServicePlanSummary embeddingWorker = buildDedicatedEmbeddingWorkerPlan(deployment, providerConfig);

        boolean managedVectorProvisioningEnabled = ManagedDeploymentProfileCatalog.managedVectorProvisioningRequested(providerConfig);
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
        if (embeddingWorker != null) {
            steps.add(new RailwayProvisioningStepSummary(
                nextStepOrder++,
                "configure_embedding_worker",
                "Create or update the deployment-dedicated embedding worker and its environment variables."
            ));
        }
        steps.add(new RailwayProvisioningStepSummary(nextStepOrder++, "configure_runtime", "Create or update the runtime service root and its environment variables."));
        steps.add(new RailwayProvisioningStepSummary(nextStepOrder++, "configure_rest_connector", "Create or update the REST connector service root and its environment variables."));
        if (vectorizationRunner != null) {
            steps.add(new RailwayProvisioningStepSummary(nextStepOrder++, "configure_vectorization_runner", "Create or update the vectorization runner service root and its environment variables."));
        }
        steps.add(new RailwayProvisioningStepSummary(nextStepOrder++, "trigger_deploy", vectorizationRunner == null
            ? "Commit staged changes or trigger Railway deployment/redeploy for runtime and REST connector."
            : embeddingWorker == null
                ? "Commit staged changes or trigger Railway deployment/redeploy for runtime and REST connector."
                : "Commit staged changes or trigger Railway deployment/redeploy for embedding worker, runtime, REST connector, and optional vectorization runner."));
        steps.add(new RailwayProvisioningStepSummary(nextStepOrder++, "wait_for_active", "Wait for Railway deployment states to become active."));
        steps.add(new RailwayProvisioningStepSummary(nextStepOrder, "run_verification", "Run post-deploy verification against runtime and runtime-backed connector operational endpoints."));

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
            new RailwayProvisioningServicesSummary(runtime, restConnector, vectorizationRunner, embeddingWorker),
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

    private void addShopifyBridgeConnectorEnv(List<RailwayEnvVarSummary> connectorEnv, DeploymentEntity deployment) {
        if (connectorEnv == null
            || deployment == null
            || shopifyStoreConnectionRepository == null
            || platformManagedProductServiceRepository == null) {
            return;
        }
        ShopifyStoreConnectionEntity store = shopifyStoreConnectionRepository.findByDeploymentId(deployment.getId()).orElse(null);
        if (store == null || !hasText(store.getProductServiceId())) {
            return;
        }
        PlatformManagedProductServiceEntity productService =
            platformManagedProductServiceRepository.findById(store.getProductServiceId()).orElse(null);
        if (productService == null || !hasText(productService.getSecretName())) {
            return;
        }
        if (!"SHOPIFY_BRIDGE_SERVICE".equalsIgnoreCase(productService.getServiceKind())) {
            return;
        }
        connectorEnv.add(new RailwayEnvVarSummary(
            SHOPIFY_BRIDGE_SHARED_SECRET_ENV,
            "${secret:" + productService.getSecretName() + "}"
        ));
    }

    private void addRuntimeProviderEnv(List<RailwayEnvVarSummary> runtimeEnv,
                                       DeploymentEntity deployment,
                                       JsonNode providerConfig,
                                       JsonNode entityConfig) {
        String llmProvider = ManagedDeploymentProfileCatalog.resolveLlmProvider(providerConfig);
        String embeddingProvider = ManagedDeploymentProfileCatalog.resolveEmbeddingProvider(providerConfig);
        String vectorStrategy = ManagedDeploymentProfileCatalog.resolveVectorStrategy(providerConfig);
        int vectorDimensions = resolveVectorDimensions(entityConfig, embeddingProvider, vectorStrategy);

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
            addOpenAiEnv(runtimeEnv, deployment, providerConfig, llmProvider, embeddingProvider, vectorDimensions);
        }

        addAnthropicEnv(runtimeEnv, deployment, providerConfig, llmProvider);
        addAzureEnv(runtimeEnv, deployment, providerConfig, llmProvider, embeddingProvider);
        addCohereEnv(runtimeEnv, deployment, providerConfig, llmProvider, embeddingProvider);
        addGeminiEnv(runtimeEnv, deployment, providerConfig, llmProvider, embeddingProvider);
        addPurposeSpecificLlmEnv(runtimeEnv, providerConfig);
        addOnnxEnv(runtimeEnv, providerConfig, embeddingProvider);
        addVectorBackendEnv(runtimeEnv, deployment, providerConfig, vectorStrategy, vectorDimensions);
    }

    private String resolveRuntimeCuratedPack(JsonNode providerConfig) {
        String explicit = text(providerConfig, "curatedPackId");
        if (StringUtils.hasText(explicit)) {
            return explicit;
        }
        String curatedModuleId = text(providerConfig, "curatedModuleId");
        if (!StringUtils.hasText(curatedModuleId)) {
            return null;
        }
        String normalized = curatedModuleId.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "default", "commerce", "support" -> normalized;
            default -> null;
        };
    }

    private int resolveVectorDimensions(JsonNode entityConfig, String embeddingProvider, String vectorStrategy) {
        int configured = entityConfig.path("ai-config").path("vector-dimensions").asInt(0);
        if (configured > 0) {
            return configured;
        }
        return ManagedDeploymentProfileCatalog.defaultVectorDimensions(embeddingProvider, vectorStrategy);
    }

    private void addOpenAiEnv(List<RailwayEnvVarSummary> runtimeEnv,
                              DeploymentEntity deployment,
                              JsonNode providerConfig,
                              String llmProvider,
                              String embeddingProvider,
                              int vectorDimensions) {
        runtimeEnv.add(new RailwayEnvVarSummary("AI_PROVIDERS_OPENAI_ENABLED", "true"));
        addResolvedSingleSecretEnv(
            runtimeEnv,
            deployment,
            "OPENAI_API_KEY",
            preferredLlmProviderSecretRef(providerConfig, ManagedDeploymentProfileCatalog.LLM_PROVIDER_OPENAI),
            "OPENAI_API_KEY",
            "AI_PROVIDERS_OPENAI_API_KEY"
        );
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
            int effectiveDimensions = ManagedDeploymentProfileCatalog.effectiveOpenAiEmbeddingDimensions(
                providerConfig,
                vectorDimensions
            );
            addOptionalEnv(runtimeEnv, "AI_PROVIDERS_OPENAI_EMBEDDING_BASE_URL", ManagedDeploymentProfileCatalog.openAiEmbeddingBaseUrl(providerConfig));
            addResolvedSingleSecretEnv(
                runtimeEnv,
                deployment,
                "OPENAI_API_KEY",
                ManagedDeploymentProfileCatalog.embeddingApiKeySecretRef(providerConfig),
                "AI_PROVIDERS_OPENAI_EMBEDDING_API_KEY"
            );
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

    private void addAnthropicEnv(List<RailwayEnvVarSummary> runtimeEnv,
                                 DeploymentEntity deployment,
                                 JsonNode providerConfig,
                                 String llmProvider) {
        if (!ManagedDeploymentProfileCatalog.usesAnthropic(providerConfig)) {
            return;
        }
        runtimeEnv.add(new RailwayEnvVarSummary("AI_PROVIDERS_ANTHROPIC_ENABLED", "true"));
        addResolvedSingleSecretEnv(
            runtimeEnv,
            deployment,
            "ANTHROPIC_API_KEY",
            preferredLlmProviderSecretRef(providerConfig, ManagedDeploymentProfileCatalog.LLM_PROVIDER_ANTHROPIC),
            "AI_PROVIDERS_ANTHROPIC_API_KEY"
        );
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
                             DeploymentEntity deployment,
                             JsonNode providerConfig,
                             String llmProvider,
                             String embeddingProvider) {
        if (!ManagedDeploymentProfileCatalog.usesAzure(providerConfig)) {
            return;
        }
        runtimeEnv.add(new RailwayEnvVarSummary("AI_PROVIDERS_AZURE_ENABLED", "true"));
        addResolvedSingleSecretEnv(
            runtimeEnv,
            deployment,
            "AZURE_OPENAI_API_KEY",
            preferredLlmProviderSecretRef(providerConfig, ManagedDeploymentProfileCatalog.LLM_PROVIDER_AZURE),
            "AI_PROVIDERS_AZURE_API_KEY"
        );
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
            addOptionalEnv(
                runtimeEnv,
                "AI_PROVIDERS_AZURE_EMBEDDING_ENDPOINT",
                ManagedDeploymentProfileCatalog.azureEmbeddingEndpoint(providerConfig)
            );
            addOptionalEnv(
                runtimeEnv,
                "AI_PROVIDERS_AZURE_EMBEDDING_API_VERSION",
                ManagedDeploymentProfileCatalog.azureEmbeddingApiVersion(providerConfig)
            );
            addResolvedSingleSecretEnv(
                runtimeEnv,
                deployment,
                "AZURE_OPENAI_API_KEY",
                ManagedDeploymentProfileCatalog.embeddingApiKeySecretRef(providerConfig),
                "AI_PROVIDERS_AZURE_EMBEDDING_API_KEY"
            );
        }
    }

    private void addCohereEnv(List<RailwayEnvVarSummary> runtimeEnv,
                              DeploymentEntity deployment,
                              JsonNode providerConfig,
                              String llmProvider,
                              String embeddingProvider) {
        if (!ManagedDeploymentProfileCatalog.usesCohere(providerConfig)) {
            return;
        }
        runtimeEnv.add(new RailwayEnvVarSummary("AI_PROVIDERS_COHERE_ENABLED", "true"));
        addResolvedSingleSecretEnv(
            runtimeEnv,
            deployment,
            "COHERE_API_KEY",
            preferredLlmProviderSecretRef(providerConfig, ManagedDeploymentProfileCatalog.LLM_PROVIDER_COHERE),
            "AI_PROVIDERS_COHERE_API_KEY"
        );
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
                              DeploymentEntity deployment,
                              JsonNode providerConfig,
                              String llmProvider,
                              String embeddingProvider) {
        if (!ManagedDeploymentProfileCatalog.usesGemini(providerConfig)) {
            return;
        }
        runtimeEnv.add(new RailwayEnvVarSummary("AI_PROVIDERS_GEMINI_ENABLED", "true"));
        addResolvedSingleSecretEnv(
            runtimeEnv,
            deployment,
            "GEMINI_API_KEY",
            preferredLlmProviderSecretRef(providerConfig, ManagedDeploymentProfileCatalog.LLM_PROVIDER_GEMINI),
            "AI_PROVIDERS_GEMINI_API_KEY"
        );
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

    private void addVectorBackendEnv(List<RailwayEnvVarSummary> runtimeEnv,
                                     DeploymentEntity deployment,
                                     JsonNode providerConfig,
                                     String vectorStrategy,
                                     int vectorDimensions) {
        boolean sharedStorage = ManagedDeploymentProfileCatalog.sharedVectorStorageRequested(providerConfig);
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
            String qdrantCollectionPrefix = tenantScopedVectorHandleResolver.resolveQdrantCollectionPrefix(deployment, providerConfig);
            addOptionalEnv(runtimeEnv, "AI_PROVIDERS_QDRANT_COLLECTION_PREFIX", qdrantCollectionPrefix);
            String runtimeSecretName = ManagedDeploymentProfileCatalog.qdrantRuntimeApiKeySecretName(providerConfig);
            addResolvedSingleSecretEnv(runtimeEnv, deployment, "QDRANT_API_KEY", runtimeSecretName, "AI_PROVIDERS_QDRANT_API_KEY");
            return;
        }
        if (ManagedDeploymentProfileCatalog.usesPinecone(providerConfig)) {
            runtimeEnv.add(new RailwayEnvVarSummary("AI_PROVIDERS_PINECONE_ENABLED", "true"));
            String runtimeSecretName = ManagedDeploymentProfileCatalog.pineconeRuntimeApiKeySecretName(providerConfig);
            addResolvedSingleSecretEnv(runtimeEnv, deployment, "PINECONE_API_KEY", runtimeSecretName, "AI_PROVIDERS_PINECONE_API_KEY");
            addOptionalEnv(runtimeEnv, "AI_PROVIDERS_PINECONE_ENVIRONMENT", ManagedDeploymentProfileCatalog.pineconeEnvironment(providerConfig));
            addOptionalEnv(runtimeEnv, "AI_PROVIDERS_PINECONE_INDEX_NAME", ManagedDeploymentProfileCatalog.pineconeIndexName(providerConfig));
            addOptionalEnv(runtimeEnv, "AI_PROVIDERS_PINECONE_PROJECT_ID", ManagedDeploymentProfileCatalog.pineconeProjectId(providerConfig));
            addOptionalEnv(runtimeEnv, "AI_PROVIDERS_PINECONE_API_HOST", ManagedDeploymentProfileCatalog.pineconeApiHost(providerConfig));
            String pineconeNamespacePrefix = tenantScopedVectorHandleResolver.resolvePineconeNamespacePrefix(deployment, providerConfig);
            addOptionalEnv(runtimeEnv, "AI_PROVIDERS_PINECONE_NAMESPACE_PREFIX", pineconeNamespacePrefix);
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
            String weaviateClassPrefix = tenantScopedVectorHandleResolver.resolveWeaviateClassPrefix(deployment, providerConfig);
            addOptionalEnv(runtimeEnv, "AI_PROVIDERS_WEAVIATE_CLASS_PREFIX", weaviateClassPrefix);
            boolean nativeMultiTenancyEnabled = tenantScopedVectorHandleResolver.resolveWeaviateNativeMultiTenancyEnabled(providerConfig);
            if (sharedStorage || nativeMultiTenancyEnabled) {
                runtimeEnv.add(new RailwayEnvVarSummary(
                    "AI_PROVIDERS_WEAVIATE_NATIVE_MULTI_TENANCY_ENABLED",
                    Boolean.toString(nativeMultiTenancyEnabled)
                ));
                String weaviateTenantName = tenantScopedVectorHandleResolver.resolveWeaviateTenantName(deployment, providerConfig);
                addOptionalEnv(runtimeEnv, "AI_PROVIDERS_WEAVIATE_TENANT_NAME", weaviateTenantName);
            }
            addResolvedSingleSecretEnv(runtimeEnv, deployment, "WEAVIATE_API_KEY", null, "AI_PROVIDERS_WEAVIATE_API_KEY");
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
            String milvusCollectionPrefix = tenantScopedVectorHandleResolver.resolveMilvusCollectionPrefix(deployment, providerConfig);
            addOptionalEnv(runtimeEnv, "AI_PROVIDERS_MILVUS_COLLECTION_PREFIX", milvusCollectionPrefix);
            String runtimeUsernameSecretName = ManagedDeploymentProfileCatalog.milvusRuntimeUsernameSecretName(providerConfig);
            String runtimePasswordSecretName = ManagedDeploymentProfileCatalog.milvusRuntimePasswordSecretName(providerConfig);
            addResolvedPairedSecretEnv(
                runtimeEnv,
                deployment,
                "MILVUS_RUNTIME_CREDENTIALS",
                runtimeUsernameSecretName,
                runtimePasswordSecretName,
                "AI_PROVIDERS_MILVUS_USERNAME",
                "AI_PROVIDERS_MILVUS_PASSWORD"
            );
        }
    }

    private void addResolvedSingleSecretEnv(List<RailwayEnvVarSummary> runtimeEnv,
                                            DeploymentEntity deployment,
                                            String secretPurpose,
                                            String managedSecretName,
                                            String... envNames) {
        DeploymentProviderSecretResolutionService.ResolvedSecretValue resolved =
            deploymentProviderSecretResolutionService.resolve(deployment.getId(), secretPurpose, managedSecretName);
        String secretName = resolved.resolved()
            ? resolved.primarySecretName()
            : fallbackSecretName(secretPurpose, managedSecretName);
        if (!hasText(secretName)) {
            return;
        }
        for (String envName : envNames) {
            runtimeEnv.add(new RailwayEnvVarSummary(envName, "${secret:" + secretName + "}"));
        }
    }

    private void addResolvedPairedSecretEnv(List<RailwayEnvVarSummary> runtimeEnv,
                                            DeploymentEntity deployment,
                                            String secretPurpose,
                                            String managedPrimarySecretName,
                                            String managedSecondarySecretName,
                                            String primaryEnvName,
                                            String secondaryEnvName) {
        DeploymentProviderSecretResolutionService.ResolvedSecretValue resolved =
            deploymentProviderSecretResolutionService.resolve(
                deployment.getId(),
                secretPurpose,
                managedPrimarySecretName,
                managedSecondarySecretName
            );
        String primarySecretName = resolved.resolved()
            ? resolved.primarySecretName()
            : fallbackSecretName("MILVUS_USERNAME", managedPrimarySecretName);
        String secondarySecretName = resolved.resolved()
            ? resolved.secondarySecretName()
            : fallbackSecretName("MILVUS_PASSWORD", managedSecondarySecretName);
        if (!hasText(primarySecretName) || !hasText(secondarySecretName)) {
            return;
        }
        runtimeEnv.add(new RailwayEnvVarSummary(primaryEnvName, "${secret:" + primarySecretName + "}"));
        runtimeEnv.add(new RailwayEnvVarSummary(secondaryEnvName, "${secret:" + secondarySecretName + "}"));
    }

    private String fallbackSecretName(String defaultSecretName, String managedSecretName) {
        if (hasText(managedSecretName) && platformSecretService.isSecretPresent(managedSecretName)) {
            return managedSecretName;
        }
        return defaultSecretName;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String requireReleaseMetadata(String field, String value) {
        if (!hasText(value)) {
            throw new IllegalStateException(
                "Published deployment version is missing required release "
                    + "metadata: "
                    + field
            );
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void addPurposeSpecificLlmEnv(List<RailwayEnvVarSummary> runtimeEnv, JsonNode providerConfig) {
        String orchestrationProvider = ManagedDeploymentProfileCatalog.orchestrationLlmProvider(providerConfig);
        addOptionalEnv(runtimeEnv, "AI_PROVIDERS_ORCHESTRATION_LLM_PROVIDER", orchestrationProvider);
        addOptionalEnv(runtimeEnv, "AI_PROVIDERS_ORCHESTRATION_MODEL", ManagedDeploymentProfileCatalog.orchestrationModel(providerConfig));
        addOptionalIntEnv(runtimeEnv, "AI_PROVIDERS_ORCHESTRATION_MAX_TOKENS", ManagedDeploymentProfileCatalog.orchestrationMaxTokens(providerConfig));
        addOptionalDoubleEnv(runtimeEnv, "AI_PROVIDERS_ORCHESTRATION_TEMPERATURE", ManagedDeploymentProfileCatalog.orchestrationTemperature(providerConfig));
        addOptionalIntEnv(runtimeEnv, "AI_PROVIDERS_ORCHESTRATION_TIMEOUT", ManagedDeploymentProfileCatalog.orchestrationTimeout(providerConfig));
        addOptionalEnv(runtimeEnv, "AI_PROVIDERS_ORCHESTRATION_ENDPOINT_PROFILE", ManagedDeploymentProfileCatalog.orchestrationEndpointProfile(providerConfig));
        addOptionalEnv(runtimeEnv, "AI_PROVIDERS_ORCHESTRATION_MANAGED_SERVICE_REF", ManagedDeploymentProfileCatalog.orchestrationManagedServiceRef(providerConfig));
        addOptionalEnv(runtimeEnv, "AI_PROVIDERS_ORCHESTRATION_BASE_URL", ManagedDeploymentProfileCatalog.orchestrationBaseUrl(providerConfig));
        addOptionalEnv(runtimeEnv, "AI_PROVIDERS_ORCHESTRATION_DEPLOYMENT_NAME", ManagedDeploymentProfileCatalog.orchestrationDeploymentName(providerConfig));
        addOptionalEnv(runtimeEnv, "AI_PROVIDERS_ORCHESTRATION_API_VERSION", ManagedDeploymentProfileCatalog.orchestrationApiVersion(providerConfig));
        addPurposeScopedSecretEnv(
            runtimeEnv,
            "AI_PROVIDERS_ORCHESTRATION_API_KEY",
            ManagedDeploymentProfileCatalog.orchestrationApiKeySecretRef(providerConfig),
            ManagedDeploymentProfileCatalog.secretNameForLlmProvider(orchestrationProvider)
        );

        String generationProvider = ManagedDeploymentProfileCatalog.generationLlmProvider(providerConfig);
        addOptionalEnv(runtimeEnv, "AI_PROVIDERS_GENERATION_LLM_PROVIDER", generationProvider);
        addOptionalEnv(runtimeEnv, "AI_PROVIDERS_GENERATION_MODEL", ManagedDeploymentProfileCatalog.generationModel(providerConfig));
        addOptionalIntEnv(runtimeEnv, "AI_PROVIDERS_GENERATION_MAX_TOKENS", ManagedDeploymentProfileCatalog.generationMaxTokens(providerConfig));
        addOptionalDoubleEnv(runtimeEnv, "AI_PROVIDERS_GENERATION_TEMPERATURE", ManagedDeploymentProfileCatalog.generationTemperature(providerConfig));
        addOptionalIntEnv(runtimeEnv, "AI_PROVIDERS_GENERATION_TIMEOUT", ManagedDeploymentProfileCatalog.generationTimeout(providerConfig));
        addOptionalEnv(runtimeEnv, "AI_PROVIDERS_GENERATION_ENDPOINT_PROFILE", ManagedDeploymentProfileCatalog.generationEndpointProfile(providerConfig));
        addOptionalEnv(runtimeEnv, "AI_PROVIDERS_GENERATION_MANAGED_SERVICE_REF", ManagedDeploymentProfileCatalog.generationManagedServiceRef(providerConfig));
        addOptionalEnv(runtimeEnv, "AI_PROVIDERS_GENERATION_BASE_URL", ManagedDeploymentProfileCatalog.generationBaseUrl(providerConfig));
        addOptionalEnv(runtimeEnv, "AI_PROVIDERS_GENERATION_DEPLOYMENT_NAME", ManagedDeploymentProfileCatalog.generationDeploymentName(providerConfig));
        addOptionalEnv(runtimeEnv, "AI_PROVIDERS_GENERATION_API_VERSION", ManagedDeploymentProfileCatalog.generationApiVersion(providerConfig));
        addPurposeScopedSecretEnv(
            runtimeEnv,
            "AI_PROVIDERS_GENERATION_API_KEY",
            ManagedDeploymentProfileCatalog.generationApiKeySecretRef(providerConfig),
            ManagedDeploymentProfileCatalog.secretNameForLlmProvider(generationProvider)
        );
        addOptionalEnv(runtimeEnv, "AI_PROVIDERS_EMBEDDING_ENDPOINT_PROFILE", ManagedDeploymentProfileCatalog.embeddingEndpointProfile(providerConfig));
        addOptionalEnv(runtimeEnv, "AI_PROVIDERS_EMBEDDING_MANAGED_SERVICE_REF", ManagedDeploymentProfileCatalog.embeddingManagedServiceRef(providerConfig));
        addOptionalEnv(runtimeEnv, "AI_PROVIDERS_EMBEDDING_SERVICE_MODE", ManagedDeploymentProfileCatalog.embeddingServiceMode(providerConfig));
        addOptionalEnv(runtimeEnv, "AI_PROVIDERS_EMBEDDING_BASE_URL", ManagedDeploymentProfileCatalog.embeddingBaseUrl(providerConfig));
        addOptionalEnv(runtimeEnv, "AI_PROVIDERS_EMBEDDING_DEPLOYMENT_NAME", ManagedDeploymentProfileCatalog.embeddingDeploymentName(providerConfig));
        addOptionalEnv(runtimeEnv, "AI_PROVIDERS_EMBEDDING_API_VERSION", ManagedDeploymentProfileCatalog.embeddingApiVersion(providerConfig));
        addPurposeScopedSecretEnv(
            runtimeEnv,
            "AI_PROVIDERS_EMBEDDING_API_KEY",
            ManagedDeploymentProfileCatalog.embeddingApiKeySecretRef(providerConfig),
            ManagedDeploymentProfileCatalog.secretNameForEmbeddingProvider(ManagedDeploymentProfileCatalog.resolveEmbeddingProvider(providerConfig))
        );
    }

    private String preferredLlmProviderSecretRef(JsonNode providerConfig, String providerName) {
        String normalizedProvider = trimToNull(providerName);
        if (normalizedProvider == null) {
            return trimToNull(ManagedDeploymentProfileCatalog.embeddingApiKeySecretRef(providerConfig));
        }
        if (normalizedProvider.equals(trimToNull(ManagedDeploymentProfileCatalog.generationLlmProvider(providerConfig)))) {
            String generationRef = trimToNull(ManagedDeploymentProfileCatalog.generationApiKeySecretRef(providerConfig));
            if (generationRef != null) {
                return generationRef;
            }
        }
        if (normalizedProvider.equals(trimToNull(ManagedDeploymentProfileCatalog.orchestrationLlmProvider(providerConfig)))) {
            String orchestrationRef = trimToNull(ManagedDeploymentProfileCatalog.orchestrationApiKeySecretRef(providerConfig));
            if (orchestrationRef != null) {
                return orchestrationRef;
            }
        }
        String embeddingProvider = trimToNull(ManagedDeploymentProfileCatalog.resolveEmbeddingProvider(providerConfig));
        if (normalizedProvider.equals(embeddingProvider)) {
            return trimToNull(ManagedDeploymentProfileCatalog.embeddingApiKeySecretRef(providerConfig));
        }
        return null;
    }

    private void addPurposeScopedSecretEnv(List<RailwayEnvVarSummary> runtimeEnv,
                                           String envName,
                                           String directSecretRef,
                                           String fallbackSecretName) {
        String direct = trimToNull(directSecretRef);
        if (hasText(direct)) {
            runtimeEnv.add(new RailwayEnvVarSummary(envName, "${secret:" + direct + "}"));
            return;
        }
        if (hasText(fallbackSecretName)) {
            runtimeEnv.add(new RailwayEnvVarSummary(envName, "${secret:" + fallbackSecretName + "}"));
        }
    }

    private void addRuntimeConnectorAuthEnv(List<RailwayEnvVarSummary> runtimeEnv, JsonNode securityConfig) {
        if (ManagedDeploymentProfileCatalog.connectorApiKeyEnabled(securityConfig)) {
            runtimeEnv.add(new RailwayEnvVarSummary("ACTIONS_CONNECTOR_API_KEY", "${secret:ACTIONS_CONNECTOR_API_KEY}"));
        }
        if (platformSecretService.isSecretPresent(CONNECTOR_ADMIN_SECRET)) {
            runtimeEnv.add(new RailwayEnvVarSummary("AI_ACTIONS_CONNECTOR_ADMIN_API_KEY", "${secret:APP_ADMIN_API_KEY}"));
            runtimeEnv.add(new RailwayEnvVarSummary("AI_ACTIONS_CONNECTOR_ADMIN_API_KEY_HEADER", "X-ADMIN-API-KEY"));
        }
    }

    private void addRuntimeMcpGatewayEnv(List<RailwayEnvVarSummary> runtimeEnv, JsonNode actionsConfig) {
        if (!hasMcpToolActions(actionsConfig)) {
            return;
        }
        if (platformManagedProductServiceRepository == null) {
            throw new IllegalStateException("MCP tool actions require the managed MCP execution gateway service repository.");
        }
        PlatformManagedProductServiceEntity gateway = platformManagedProductServiceRepository
            .findByServiceRefIgnoreCase("mcp-execution-gateway")
            .orElseThrow(() -> new IllegalStateException("MCP tool actions require managed product service mcp-execution-gateway."));
        String baseUrl = trimToNull(gateway.getPrivateNetworkUrl());
        if (baseUrl == null) {
            baseUrl = trimToNull(gateway.getBaseUrl());
        }
        String secretName = trimToNull(gateway.getSecretName());
        if (!hasText(baseUrl) || !hasText(secretName)) {
            throw new IllegalStateException("MCP execution gateway requires base URL and admin secret before runtime provisioning.");
        }
        runtimeEnv.add(new RailwayEnvVarSummary("AI_ACTIONS_CONNECTOR_MCP_GATEWAY_BASE_URL", baseUrl));
        runtimeEnv.add(new RailwayEnvVarSummary("AI_ACTIONS_CONNECTOR_MCP_GATEWAY_API_KEY", "${secret:" + secretName + "}"));
        runtimeEnv.add(new RailwayEnvVarSummary("AI_ACTIONS_CONNECTOR_MCP_GATEWAY_API_KEY_HEADER", "X-MCP-GATEWAY-API-KEY"));
        runtimeEnv.add(new RailwayEnvVarSummary("AI_ACTIONS_CONNECTOR_MCP_GATEWAY_EXECUTE_PATH", "/api/internal/mcp/actions/execute"));
        for (String secretRef : collectMcpSecretRefs(actionsConfig)) {
            runtimeEnv.add(new RailwayEnvVarSummary(secretRef, "${secret:" + secretRef + "}"));
        }
    }

    private boolean hasMcpToolActions(JsonNode actionsConfig) {
        JsonNode actions = actionsConfig == null ? null : actionsConfig.path("actions");
        if (actions == null || !actions.isArray()) {
            return false;
        }
        for (JsonNode action : actions) {
            String adapterType = text(action, "adapterType");
            if ("mcp-tool".equalsIgnoreCase(adapterType)) {
                return true;
            }
            JsonNode execution = action.path("execution");
            if (execution.isObject()) {
                String executionAdapterType = text(execution, "adapterType");
                if ("mcp-tool".equalsIgnoreCase(executionAdapterType) || execution.path("mcp").isObject()) {
                    return true;
                }
            }
        }
        return false;
    }

    private Set<String> collectMcpSecretRefs(JsonNode node) {
        Set<String> secretRefs = new LinkedHashSet<>();
        collectMcpSecretRefs(node, secretRefs);
        return secretRefs;
    }

    private void collectMcpSecretRefs(JsonNode node, Set<String> secretRefs) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return;
        }
        if (node.isTextual()) {
            String value = trimToNull(node.asText());
            if (value != null && MCP_SECRET_REF_PATTERN.matcher(value).matches()) {
                secretRefs.add(value);
            }
            return;
        }
        if (node.isObject()) {
            node.elements().forEachRemaining(child -> collectMcpSecretRefs(child, secretRefs));
            return;
        }
        if (node.isArray()) {
            node.elements().forEachRemaining(child -> collectMcpSecretRefs(child, secretRefs));
        }
    }

    private void addRuntimeWebhookTargetEnv(List<RailwayEnvVarSummary> runtimeEnv, JsonNode actionsConfig) {
        Set<String> secretRefs = new LinkedHashSet<>();
        JsonNode webhookTargets = actionsConfig.path("webhookTargets");
        if (!webhookTargets.isArray()) {
            return;
        }
        for (JsonNode target : webhookTargets) {
            if (!target.isObject()) {
                continue;
            }
            String urlSecretRef = trimToNull(text(target, "urlSecretRef"));
            if (hasText(urlSecretRef)) {
                secretRefs.add(urlSecretRef);
            }
            String signingSecretRef = trimToNull(text(target, "signingSecretRef"));
            if (hasText(signingSecretRef)) {
                secretRefs.add(signingSecretRef);
            }
        }
        for (String secretRef : secretRefs) {
            runtimeEnv.add(new RailwayEnvVarSummary(secretRef, "${secret:" + secretRef + "}"));
        }
    }

    private void addRuntimeIngressAuthEnv(List<RailwayEnvVarSummary> runtimeEnv,
                                          DeploymentEntity deployment,
                                          JsonNode securityConfig) {
        boolean trustedBackendConfigured = platformSecretService.isSecretPresent(RUNTIME_TRUSTED_BACKEND_SECRET);
        boolean privateAssertionConfigured = platformSecretService.isSecretPresent(RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY_SECRET);
        boolean publicTokenConfigured = platformSecretService.isSecretPresent(RUNTIME_PUBLIC_TOKEN_SIGNING_KEY_SECRET)
            && ManagedDeploymentProfileCatalog.publicRuntimeRequested(securityConfig);
        runtimeEnv.add(new RailwayEnvVarSummary(
            "AI_FABRIC_RUNTIME_AUTH_INGRESS_MODE",
            "VERIFIED_CONTEXT_REQUIRED"
        ));
        runtimeEnv.add(new RailwayEnvVarSummary(
            "AI_FABRIC_RUNTIME_REJECT_CONFLICTING_REQUEST_IDENTITY",
            "true"
        ));
        runtimeEnv.add(new RailwayEnvVarSummary(
            "AI_FABRIC_RUNTIME_REJECT_REQUEST_IDENTITY_WHEN_VERIFIED_CONTEXT_PRESENT",
            "true"
        ));
        if (trustedBackendConfigured) {
            runtimeEnv.add(new RailwayEnvVarSummary(
                RUNTIME_TRUSTED_BACKEND_SECRET,
                "${secret:" + RUNTIME_TRUSTED_BACKEND_SECRET + "}"
            ));
            addOptionalEnv(
                runtimeEnv,
                "AI_FABRIC_RUNTIME_AUTH_ACCEPTED_ISSUERS",
                ManagedDeploymentProfileCatalog.effectivePrivateRuntimeAcceptedIssuers(securityConfig)
            );
            addOptionalEnv(
                runtimeEnv,
                "AI_FABRIC_RUNTIME_AUTH_ACCEPTED_AUDIENCES",
                ManagedDeploymentProfileCatalog.effectivePrivateRuntimeAcceptedAudiences(
                    securityConfig,
                    deployment == null ? null : deployment.getId()
                )
            );
        }
        if (privateAssertionConfigured) {
            runtimeEnv.add(new RailwayEnvVarSummary(
                RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY_SECRET,
                "${secret:" + RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY_SECRET + "}"
            ));
        }
    }

    private void addRuntimePublicTokenValidationEnv(List<RailwayEnvVarSummary> runtimeEnv, JsonNode securityConfig) {
        if (!platformSecretService.isSecretPresent(RUNTIME_PUBLIC_TOKEN_SIGNING_KEY_SECRET)
            || !ManagedDeploymentProfileCatalog.publicRuntimeRequested(securityConfig)) {
            return;
        }
        runtimeEnv.add(new RailwayEnvVarSummary(
            RUNTIME_PUBLIC_TOKEN_SIGNING_KEY_SECRET,
            "${secret:" + RUNTIME_PUBLIC_TOKEN_SIGNING_KEY_SECRET + "}"
        ));
        addOptionalEnv(
            runtimeEnv,
            "AI_FABRIC_RUNTIME_PUBLIC_TOKEN_ISSUER",
            ManagedDeploymentProfileCatalog.publicRuntimeTokenIssuer(securityConfig)
        );
        addOptionalEnv(
            runtimeEnv,
            "AI_FABRIC_RUNTIME_PUBLIC_TOKEN_ACCEPTED_ISSUERS",
            ManagedDeploymentProfileCatalog.publicRuntimeAcceptedIssuers(securityConfig)
        );
        addOptionalEnv(
            runtimeEnv,
            "AI_FABRIC_RUNTIME_PUBLIC_TOKEN_ACCEPTED_AUDIENCES",
            ManagedDeploymentProfileCatalog.publicRuntimeAcceptedAudiences(securityConfig)
        );
        addOptionalEnv(
            runtimeEnv,
            "AI_FABRIC_RUNTIME_PUBLIC_TOKEN_DEFAULT_AUDIENCE",
            ManagedDeploymentProfileCatalog.publicRuntimeDefaultAudience(securityConfig)
        );
        runtimeEnv.add(new RailwayEnvVarSummary(
            "AI_FABRIC_RUNTIME_PUBLIC_BOOTSTRAP_ENABLED",
            Boolean.toString(ManagedDeploymentProfileCatalog.publicRuntimeBootstrapEnabled(securityConfig))
        ));
        addOptionalEnv(
            runtimeEnv,
            "AI_FABRIC_RUNTIME_PUBLIC_BOOTSTRAP_ALLOWED_ORIGINS",
            effectiveCorsAllowedOrigins(securityConfig)
        );
    }

    private String effectiveCorsAllowedOrigins(JsonNode securityConfig) {
        String configured = text(securityConfig, "corsAllowedOrigins");
        return configured.isEmpty() ? provisioningProperties.corsAllowedOrigins() : configured;
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
        if (platformSecretService.isSecretPresent(RUNTIME_TRUSTED_BACKEND_SECRET)) {
            connectorEnv.add(new RailwayEnvVarSummary(
                "REST_CONNECTOR_RUNTIME_PROXY_API_KEY",
                "${secret:" + RUNTIME_TRUSTED_BACKEND_SECRET + "}"
            ));
            connectorEnv.add(new RailwayEnvVarSummary(
                "REST_CONNECTOR_RUNTIME_PROXY_API_KEY_HEADER",
                RuntimePrivateAccessSupport.TRUSTED_BACKEND_API_KEY_HEADER
            ));
        }
    }

    private RailwayServicePlanSummary buildVectorizationRunnerPlan(DeploymentEntity deployment) {
        if (vectorizationPlanRepository == null) {
            return null;
        }
        VectorizationPlanEntity plan = vectorizationPlanRepository.findByDeploymentId(deployment.getId()).orElse(null);
        if (plan == null || !"PLATFORM_MANAGED_AUTO".equalsIgnoreCase(plan.getRunnerMode())) {
            return null;
        }
        List<RailwayEnvVarSummary> runnerEnv = new ArrayList<>();
        runnerEnv.add(new RailwayEnvVarSummary(
            "AI_FABRIC_VECTORIZATION_RUNNER_PLATFORM_BASE_URL",
            deliveryProperties.publicBaseUrl()
        ));
        runnerEnv.add(new RailwayEnvVarSummary(
            "AI_FABRIC_VECTORIZATION_RUNNER_REGISTRATION_TOKEN",
            "${secret:" + VectorizationManagedSecretNames.registrationTokenSecretName(deployment.getId()) + "}"
        ));
        runnerEnv.add(new RailwayEnvVarSummary(
            "AI_FABRIC_VECTORIZATION_RUNNER_RUNNER_INSTANCE_ID",
            vectorizationRunnerServiceName(deployment)
        ));
        runnerEnv.add(new RailwayEnvVarSummary(
            "AI_FABRIC_VECTORIZATION_RUNNER_PRODUCT_VERSION",
            vectorizationProperties.requiredProductVersion()
        ));
        runnerEnv.add(new RailwayEnvVarSummary(
            "AI_FABRIC_VECTORIZATION_RUNNER_COMPATIBILITY_VERSION",
            vectorizationProperties.requiredCompatibilityVersion()
        ));
        runnerEnv.add(new RailwayEnvVarSummary(
            "AI_FABRIC_VECTORIZATION_RUNNER_POLL_INTERVAL",
            vectorizationRunnerProvisioningProperties.pollInterval().toString()
        ));
        runnerEnv.add(new RailwayEnvVarSummary(
            "AI_FABRIC_VECTORIZATION_RUNNER_REQUEST_TIMEOUT",
            vectorizationRunnerProvisioningProperties.requestTimeout().toString()
        ));
        runnerEnv.add(new RailwayEnvVarSummary(
            "AI_FABRIC_VECTORIZATION_RUNNER_DEPLOYMENT_ID",
            deployment.getId()
        ));
        return new RailwayServicePlanSummary(
            vectorizationRunnerServiceName(deployment),
            resolveRootDirectory(
                vectorizationRunnerProvisioningProperties.dockerfilePath(),
                vectorizationRunnerProvisioningProperties.serviceRoot()
            ),
            vectorizationRunnerProvisioningProperties.dockerfilePath(),
            null,
            runnerEnv
        );
    }

    public String vectorizationRunnerServiceName(DeploymentEntity deployment) {
        return vectorizationRunnerProvisioningProperties.serviceNamePrefix() + "-" + deployment.getId();
    }

    public String dedicatedEmbeddingWorkerServiceName(DeploymentEntity deployment) {
        return inferenceProvisioningProperties.dedicatedEmbeddingServiceNamePrefix() + "-" + deployment.getId();
    }

    private RailwayServicePlanSummary buildDedicatedEmbeddingWorkerPlan(DeploymentEntity deployment,
                                                                        JsonNode providerConfig) {
        if (!ManagedDeploymentProfileCatalog.dedicatedEmbeddingServiceRequested(providerConfig)) {
            return null;
        }
        List<RailwayEnvVarSummary> workerEnv = new ArrayList<>();
        workerEnv.add(new RailwayEnvVarSummary("AI_SERVICE_FEATURES_ENABLE_GENERATION", "false"));
        workerEnv.add(new RailwayEnvVarSummary("AI_SERVICE_FEATURES_ENABLE_EMBEDDINGS", "true"));
        workerEnv.add(new RailwayEnvVarSummary("AI_PROVIDERS_EMBEDDING_PROVIDER", ManagedDeploymentProfileCatalog.EMBEDDING_PROVIDER_ONNX));
        workerEnv.add(new RailwayEnvVarSummary("AI_PROVIDERS_ENABLE_FALLBACK", "false"));
        workerEnv.add(new RailwayEnvVarSummary("AI_PROVIDERS_ONNX_ENABLED", "true"));
        addOptionalEnv(workerEnv, "AI_PROVIDERS_ONNX_MODEL_ALIAS", blankToFallback(ManagedDeploymentProfileCatalog.onnxModelAlias(providerConfig), "bge-small-en-v1.5"));
        workerEnv.add(new RailwayEnvVarSummary(
            "AI_PROVIDERS_ONNX_MAX_SEQUENCE_LENGTH",
            Integer.toString(ManagedDeploymentProfileCatalog.onnxMaxSequenceLength(providerConfig))
        ));
        workerEnv.add(new RailwayEnvVarSummary(
            "AI_PROVIDERS_ONNX_USE_GPU",
            Boolean.toString(ManagedDeploymentProfileCatalog.onnxUseGpu(providerConfig))
        ));
        return new RailwayServicePlanSummary(
            dedicatedEmbeddingWorkerServiceName(deployment),
            resolveRootDirectory(
                inferenceProvisioningProperties.dedicatedEmbeddingDockerfilePath(),
                inferenceProvisioningProperties.dedicatedEmbeddingServiceRoot()
            ),
            inferenceProvisioningProperties.dedicatedEmbeddingDockerfilePath(),
            null,
            workerEnv
        );
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

    private String blankToFallback(String value, String fallback) {
        if (value != null && !value.isBlank()) {
            return value;
        }
        return fallback;
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
