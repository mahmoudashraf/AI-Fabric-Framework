package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.config.PlatformDeliveryProperties;
import com.ai.fabric.platform.backend.config.PlatformProvisioningProperties;
import com.ai.fabric.platform.backend.config.PlatformVectorizationProperties;
import com.ai.fabric.platform.backend.config.PlatformVectorizationRunnerProvisioningProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentArtifactBundleSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayEnvVarSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayProvisioningPlanSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayProvisioningStepSummary;
import com.ai.fabric.platform.backend.productservice.entity.PlatformManagedProductServiceEntity;
import com.ai.fabric.platform.backend.productservice.repository.PlatformManagedProductServiceRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationPlanEntity;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationPlanRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RailwayProvisioningPlanServiceTest {

    @Test
    void buildPlanAddsPrivateVectorizationRunnerServiceWhenManagedRunnerModeIsActive() {
        DeploymentArtifactService artifactService = mock(DeploymentArtifactService.class);
        when(artifactService.toBundleSummary(org.mockito.ArgumentMatchers.any())).thenReturn(
            new DeploymentArtifactBundleSummary(
                "dep-123",
                "ver-123",
                "v1",
                "hash-123",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-actions.yml?expires=2016230400&sig=test-actions",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-entity-config.yml?expires=2016230400&sig=test-entities",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/actions-routing.yml?expires=2016230400&sig=test-routing",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-prompt-config.json?expires=2016230400&sig=test-prompts",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/deployment-manifest.json?expires=2016230400&sig=test-manifest"
            )
        );
        VectorizationPlanRepository vectorizationPlanRepository = mock(VectorizationPlanRepository.class);
        VectorizationPlanEntity planEntity = new VectorizationPlanEntity();
        planEntity.setId("vpl-123");
        planEntity.setDeploymentId("dep-123");
        planEntity.setRunnerMode("PLATFORM_MANAGED_AUTO");
        when(vectorizationPlanRepository.findByDeploymentId("dep-123")).thenReturn(java.util.Optional.of(planEntity));

        RailwayProvisioningPlanService service = new RailwayProvisioningPlanService(
            properties(),
            new PlatformDeliveryProperties("https://platform.example", true, Duration.ofDays(3650)),
            new PlatformVectorizationProperties(
                Duration.ofDays(7),
                Duration.ofHours(6),
                Duration.ofMinutes(15),
                100,
                "2026.04.05",
                "2026.04"
            ),
            new PlatformVectorizationRunnerProvisioningProperties(
                "ai-fabric-product/ai-fabric-vectorization-runner",
                "ai-fabric-product/ai-fabric-vectorization-runner/deploy/railway/Dockerfile",
                "vectorization-runner",
                Duration.ofSeconds(10),
                Duration.ofMinutes(5)
            ),
            artifactService,
            new DeploymentSourceResolver(properties()),
            mock(PlatformSecretService.class),
            vectorizationPlanRepository,
            new ObjectMapper()
        );

        RailwayProvisioningPlanSummary plan = service.buildPlan(deployment(), version());

        assertThat(plan.services().vectorizationRunner()).isNotNull();
        assertThat(plan.services().vectorizationRunner().serviceName()).isEqualTo("vectorization-runner-dep-123");
        assertThat(plan.services().vectorizationRunner().dockerfilePath())
            .isEqualTo("ai-fabric-product/ai-fabric-vectorization-runner/deploy/railway/Dockerfile");
        Map<String, String> runnerEnv = envMap(plan.services().vectorizationRunner().env());
        assertThat(runnerEnv)
            .containsEntry("AI_FABRIC_VECTORIZATION_RUNNER_PLATFORM_BASE_URL", "https://platform.example")
            .containsEntry("AI_FABRIC_VECTORIZATION_RUNNER_RUNNER_INSTANCE_ID", "vectorization-runner-dep-123")
            .containsEntry("AI_FABRIC_VECTORIZATION_RUNNER_DEPLOYMENT_ID", "dep-123")
            .containsEntry("AI_FABRIC_VECTORIZATION_RUNNER_PRODUCT_VERSION", "2026.04.05")
            .containsEntry("AI_FABRIC_VECTORIZATION_RUNNER_COMPATIBILITY_VERSION", "2026.04")
            .containsEntry("AI_FABRIC_VECTORIZATION_RUNNER_REQUEST_TIMEOUT", "PT5M")
            .containsEntry("AI_FABRIC_VECTORIZATION_RUNNER_REGISTRATION_TOKEN", "${secret:MANAGED_VECTORIZATION_RUNNER_TOKEN_DEP_DEP_123}");
    }

    @Test
    void buildPlanAddsDedicatedEmbeddingWorkerWhenDeploymentRequestsDedicatedEmbeddingService() {
        DeploymentArtifactService artifactService = mock(DeploymentArtifactService.class);
        when(artifactService.toBundleSummary(org.mockito.ArgumentMatchers.any())).thenReturn(
            new DeploymentArtifactBundleSummary(
                "dep-123",
                "ver-123",
                "v1",
                "hash-123",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-actions.yml?expires=2016230400&sig=test-actions",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-entity-config.yml?expires=2016230400&sig=test-entities",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/actions-routing.yml?expires=2016230400&sig=test-routing",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-prompt-config.json?expires=2016230400&sig=test-prompts",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/deployment-manifest.json?expires=2016230400&sig=test-manifest"
            )
        );

        RailwayProvisioningPlanService service = new RailwayProvisioningPlanService(
            properties(),
            new PlatformDeliveryProperties("https://platform.example", true, Duration.ofDays(3650)),
            new PlatformVectorizationProperties(
                Duration.ofDays(7),
                Duration.ofHours(6),
                Duration.ofMinutes(15),
                100,
                "2026.04.05",
                "2026.04"
            ),
            new PlatformVectorizationRunnerProvisioningProperties(
                "ai-fabric-product/ai-fabric-vectorization-runner",
                "ai-fabric-product/ai-fabric-vectorization-runner/deploy/railway/Dockerfile",
                "vectorization-runner",
                Duration.ofSeconds(10),
                Duration.ofMinutes(5)
            ),
            artifactService,
            new DeploymentSourceResolver(properties()),
            mock(PlatformSecretService.class),
            mock(VectorizationPlanRepository.class),
            new ObjectMapper()
        );

        DeploymentVersionEntity version = version();
        version.setProviderConfigJson("""
            {
              "llmProvider": "openai",
              "embeddingProvider": "openai",
              "embeddingServiceMode": "DEPLOYMENT_DEDICATED_SERVICE",
              "embeddingManagedServiceRef": "dedicated-embedding-dep-123-install-1",
              "embeddingEndpointProfile": "dep-dep-123-embedding-worker",
              "openaiEmbeddingModel": "text-embedding-3-small"
            }
            """);

        RailwayProvisioningPlanSummary plan = service.buildPlan(deployment(), version);

        assertThat(plan.services().embeddingWorker()).isNotNull();
        assertThat(plan.services().embeddingWorker().serviceName()).isEqualTo("embedding-worker-dep-123");
        assertThat(plan.services().embeddingWorker().dockerfilePath())
            .isEqualTo("ai-fabric-product/ai-fabric-embedding-worker/deploy/railway/Dockerfile");
        Map<String, String> workerEnv = envMap(plan.services().embeddingWorker().env());
        assertThat(workerEnv)
            .containsEntry("AI_SERVICE_FEATURES_ENABLE_GENERATION", "false")
            .containsEntry("AI_SERVICE_FEATURES_ENABLE_EMBEDDINGS", "true")
            .containsEntry("AI_PROVIDERS_EMBEDDING_PROVIDER", "onnx")
            .containsEntry("AI_PROVIDERS_ENABLE_FALLBACK", "false");
        Map<String, String> runtimeEnv = envMap(plan.services().runtime().env());
        assertThat(runtimeEnv)
            .containsEntry("AI_PROVIDERS_EMBEDDING_SERVICE_MODE", "DEPLOYMENT_DEDICATED_SERVICE")
            .containsEntry("AI_PROVIDERS_EMBEDDING_MANAGED_SERVICE_REF", "dedicated-embedding-dep-123-install-1")
            .containsEntry("AI_PROVIDERS_EMBEDDING_ENDPOINT_PROFILE", "dep-dep-123-embedding-worker");
    }

    @Test
    void buildPlanUsesRuntimeAndConnectorEnvKeysExpectedByServices() {
        DeploymentArtifactService artifactService = mock(DeploymentArtifactService.class);
        when(artifactService.toBundleSummary(org.mockito.ArgumentMatchers.any())).thenReturn(
            new DeploymentArtifactBundleSummary(
                "dep-123",
                "ver-123",
                "v1",
                "hash-123",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-actions.yml?expires=2016230400&sig=test-actions",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-entity-config.yml?expires=2016230400&sig=test-entities",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/actions-routing.yml?expires=2016230400&sig=test-routing",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-prompt-config.json?expires=2016230400&sig=test-prompts",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/deployment-manifest.json?expires=2016230400&sig=test-manifest"
            )
        );

        PlatformProvisioningProperties properties = new PlatformProvisioningProperties(
            "RAILWAY_API",
            "https://backboard.railway.com/graphql/v2",
            "token",
            "mahmoudashraf/AI-Fabric-Framework",
            "main",
            "dev",
            "workspace-123",
            "ai-infrastructure-module/ai-fabric-runtime",
            "ai-infrastructure-module/ai-fabric-runtime/deploy/railway/Dockerfile",
            "ai-infrastructure-module/ai-infrastructure-generic-rest-connector",
            "ai-infrastructure-module/ai-infrastructure-generic-rest-connector/deploy/railway/Dockerfile",
            "runtime",
            "rest-connector",
            32,
            "https://ai-fabric.dev,http://localhost:8080",
            "https://*lovable*",
            true,
            false,
            60_000,
            Duration.ofSeconds(5),
            Duration.ofMinutes(10)
        );

        RailwayProvisioningPlanService service = new RailwayProvisioningPlanService(
            properties,
            new PlatformDeliveryProperties("https://platform.example", true, Duration.ofDays(3650)),
            artifactService,
            new DeploymentSourceResolver(properties),
            mock(PlatformSecretService.class),
            new ObjectMapper()
        );

        RailwayProvisioningPlanSummary plan = service.buildPlan(deployment(), version());

        Map<String, String> runtimeEnv = envMap(plan.services().runtime().env());
        Map<String, String> connectorEnv = envMap(plan.services().restConnector().env());

        assertThat(plan.services().runtime().rootDir()).isNull();
        assertThat(plan.services().runtime().dockerfilePath())
            .isEqualTo("ai-infrastructure-module/ai-fabric-runtime/deploy/railway/Dockerfile");
        assertThat(plan.services().restConnector().rootDir()).isNull();
        assertThat(plan.services().restConnector().dockerfilePath())
            .isEqualTo("ai-infrastructure-module/ai-infrastructure-generic-rest-connector/deploy/railway/Dockerfile");
        assertThat(plan.projectName()).isEqualTo("sample-commerce-dev-dev-123");
        assertThat(plan.artifactStrategy()).isEqualTo("SIGNED_REMOTE_CONFIG_BUNDLES");

        assertThat(runtimeEnv)
            .containsEntry("OPENAI_ENABLED", "true")
            .containsEntry("AI_FABRIC_RUNTIME_AUTH_INGRESS_MODE", "VERIFIED_CONTEXT_REQUIRED")
            .containsEntry("AI_FABRIC_FRAMEWORK_VERSION", "0.5.0")
            .containsEntry("AI_ENTITY_CONFIG_CONTRACT_VERSION", "AI_ENTITY_CONFIG_V0_4")
            .containsEntry("AI_ENTITY_CONFIG_HASH", "entity-hash-123")
            .containsEntry("PLATFORM_DEPLOYMENT_VERSION_ID", "ver-123")
            .containsEntry("AI_PROMPTS_DEPLOYMENT_CONFIG_FILE", "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-prompt-config.json?expires=2016230400&sig=test-prompts")
            .containsEntry("CORS_ALLOWED_ORIGINS", "https://ai-fabric.dev,http://localhost:8080")
            .containsEntry("CORS_ALLOWED_ORIGIN_PATTERNS", "https://*lovable*")
            .containsEntry("CORS_ALLOW_CREDENTIALS", "true")
            .doesNotContainKey("AI_ENTITY_CONFIG_PATH");
        assertThat(runtimeEnv.get("AI_ACTIONS_CATALOG_PATH"))
            .startsWith("https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-actions.yml?")
            .contains("expires=")
            .contains("sig=");
        assertThat(runtimeEnv.get("AI_CONFIG_DEFAULT_FILE"))
            .startsWith("https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-entity-config.yml?")
            .contains("expires=")
            .contains("sig=");
        assertThat(runtimeEnv.get("AI_ENTITY_ARTIFACT_URL"))
            .isEqualTo(runtimeEnv.get("AI_CONFIG_DEFAULT_FILE"));

        assertThat(connectorEnv)
            .containsEntry("REST_CONNECTOR_RUNTIME_PROXY_ENABLED", "true")
            .containsEntry("REST_CONNECTOR_RUNTIME_PROXY_TIMEOUT_MS", "60000")
            .containsEntry("CORS_ALLOW_CREDENTIALS", "true")
            .doesNotContainKey("REST_CONNECTOR_ROUTING_CONFIG_PATH");
        assertThat(connectorEnv.get("REST_CONNECTOR_ROUTING_CONFIG_LOCATION"))
            .startsWith("https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/actions-routing.yml?")
            .contains("expires=")
            .contains("sig=");
    }

    @Test
    void buildPlanAddsShopifyBridgeSharedSecretWhenDeploymentMapsToBridgeService() {
        DeploymentArtifactService artifactService = mock(DeploymentArtifactService.class);
        when(artifactService.toBundleSummary(org.mockito.ArgumentMatchers.any())).thenReturn(
            new DeploymentArtifactBundleSummary(
                "dep-123",
                "ver-123",
                "v1",
                "hash-123",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-actions.yml?expires=2016230400&sig=test-actions",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-entity-config.yml?expires=2016230400&sig=test-entities",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/actions-routing.yml?expires=2016230400&sig=test-routing",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-prompt-config.json?expires=2016230400&sig=test-prompts",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/deployment-manifest.json?expires=2016230400&sig=test-manifest"
            )
        );
        ShopifyStoreConnectionRepository storeRepository = mock(ShopifyStoreConnectionRepository.class);
        PlatformManagedProductServiceRepository productServiceRepository = mock(PlatformManagedProductServiceRepository.class);

        ShopifyStoreConnectionEntity store = new ShopifyStoreConnectionEntity();
        store.setDeploymentId("dep-123");
        store.setProductServiceId("ps-1");
        when(storeRepository.findByDeploymentId("dep-123")).thenReturn(Optional.of(store));
        when(productServiceRepository.findById("ps-1")).thenReturn(Optional.of(productService("ps-1")));

        RailwayProvisioningPlanService service = new RailwayProvisioningPlanService(
            properties(),
            new PlatformDeliveryProperties("https://platform.example", true, Duration.ofDays(3650)),
            artifactService,
            new DeploymentSourceResolver(properties()),
            mock(PlatformSecretService.class),
            new ObjectMapper()
        );
        ReflectionTestUtils.setField(service, "shopifyStoreConnectionRepository", storeRepository);
        ReflectionTestUtils.setField(service, "platformManagedProductServiceRepository", productServiceRepository);

        RailwayProvisioningPlanSummary plan = service.buildPlan(deployment(), version());
        Map<String, String> connectorEnv = envMap(plan.services().restConnector().env());

        assertThat(connectorEnv)
            .containsEntry("SHOPIFY_BRIDGE_SHARED_SECRET", "${secret:SHOPIFY_BRIDGE_SHARED_SECRET_PROD}");
    }

    @Test
    void buildPlanUsesSecurityCorsOverridesWhenPresent() {
        DeploymentArtifactService artifactService = mock(DeploymentArtifactService.class);
        when(artifactService.toBundleSummary(org.mockito.ArgumentMatchers.any())).thenReturn(
            new DeploymentArtifactBundleSummary(
                "dep-123",
                "ver-123",
                "v1",
                "hash-123",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-actions.yml?expires=2016230400&sig=test-actions",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-entity-config.yml?expires=2016230400&sig=test-entities",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/actions-routing.yml?expires=2016230400&sig=test-routing",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-prompt-config.json?expires=2016230400&sig=test-prompts",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/deployment-manifest.json?expires=2016230400&sig=test-manifest"
            )
        );

        RailwayProvisioningPlanService service = new RailwayProvisioningPlanService(
            properties(),
            new PlatformDeliveryProperties("https://platform.example", true, Duration.ofDays(3650)),
            artifactService,
            new DeploymentSourceResolver(properties()),
            mock(PlatformSecretService.class),
            new ObjectMapper()
        );

        DeploymentVersionEntity version = version();
        version.setSecurityConfigJson("""
            {
              "authzMode": "REMOTE_HTTP",
              "adminApiKeyEnabled": true,
              "connectorApiKeyEnabled": true,
              "corsAllowedOrigins": "https://ai-fabric.dev,http://localhost:8080",
              "corsAllowedOriginPatterns": "https://*lovable*",
              "corsAllowCredentials": false
            }
            """);

        RailwayProvisioningPlanSummary plan = service.buildPlan(deployment(), version);
        Map<String, String> runtimeEnv = envMap(plan.services().runtime().env());
        Map<String, String> connectorEnv = envMap(plan.services().restConnector().env());

        assertThat(runtimeEnv)
            .containsEntry("CORS_ALLOWED_ORIGINS", "https://ai-fabric.dev,http://localhost:8080")
            .containsEntry("CORS_ALLOWED_ORIGIN_PATTERNS", "https://*lovable*")
            .containsEntry("CORS_ALLOW_CREDENTIALS", "false");
        assertThat(connectorEnv)
            .containsEntry("CORS_ALLOWED_ORIGINS", "https://ai-fabric.dev,http://localhost:8080")
            .containsEntry("CORS_ALLOWED_ORIGIN_PATTERNS", "https://*lovable*")
            .containsEntry("CORS_ALLOW_CREDENTIALS", "false");
    }

    @Test
    void buildPlanExportsPurposeSpecificInferenceEndpointProfiles() {
        DeploymentArtifactService artifactService = mock(DeploymentArtifactService.class);
        when(artifactService.toBundleSummary(org.mockito.ArgumentMatchers.any())).thenReturn(
            new DeploymentArtifactBundleSummary(
                "dep-123",
                "ver-123",
                "v1",
                "hash-123",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-actions.yml?expires=2016230400&sig=test-actions",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-entity-config.yml?expires=2016230400&sig=test-entities",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/actions-routing.yml?expires=2016230400&sig=test-routing",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-prompt-config.json?expires=2016230400&sig=test-prompts",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/deployment-manifest.json?expires=2016230400&sig=test-manifest"
            )
        );

        RailwayProvisioningPlanService service = new RailwayProvisioningPlanService(
            properties(),
            new PlatformDeliveryProperties("https://platform.example", true, Duration.ofDays(3650)),
            artifactService,
            new DeploymentSourceResolver(properties()),
            mock(PlatformSecretService.class),
            new ObjectMapper()
        );

        DeploymentVersionEntity version = version();
        version.setProviderConfigJson("""
            {
              "llmProvider":"openai",
              "embeddingProvider":"openai",
              "orchestrationLlmProvider":"openai",
              "orchestrationEndpointProfile":"openai-cloud-orchestration",
              "orchestrationModel":"gpt-4.1-mini",
              "generationLlmProvider":"openai",
              "generationEndpointProfile":"openai-cloud-default",
              "generationModel":"gpt-4.1-mini",
              "embeddingEndpointProfile":"openai-cloud-default",
              "openaiEmbeddingModel":"text-embedding-3-small",
              "openaiEmbeddingDimensions":1536,
              "vectorStrategy":"lucene"
            }
            """);

        RailwayProvisioningPlanSummary plan = service.buildPlan(deployment(), version);
        Map<String, String> runtimeEnv = envMap(plan.services().runtime().env());

        assertThat(runtimeEnv)
            .containsEntry("AI_PROVIDERS_ORCHESTRATION_ENDPOINT_PROFILE", "openai-cloud-orchestration")
            .containsEntry("AI_PROVIDERS_ORCHESTRATION_MODEL", "gpt-4.1-mini")
            .containsEntry("AI_PROVIDERS_GENERATION_ENDPOINT_PROFILE", "openai-cloud-default")
            .containsEntry("AI_PROVIDERS_GENERATION_MODEL", "gpt-4.1-mini")
            .containsEntry("AI_PROVIDERS_EMBEDDING_ENDPOINT_PROFILE", "openai-cloud-default");
    }

    @Test
    void buildPlanTruncatesProjectNameToRailwaySafeLength() {
        DeploymentArtifactService artifactService = mock(DeploymentArtifactService.class);
        when(artifactService.toBundleSummary(org.mockito.ArgumentMatchers.any())).thenReturn(
            new DeploymentArtifactBundleSummary(
                "dep-1234567890",
                "ver-123",
                "v1",
                "hash-123",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-actions.yml?expires=2016230400&sig=test-actions",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-entity-config.yml?expires=2016230400&sig=test-entities",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/actions-routing.yml?expires=2016230400&sig=test-routing",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-prompt-config.json?expires=2016230400&sig=test-prompts",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/deployment-manifest.json?expires=2016230400&sig=test-manifest"
            )
        );

        PlatformProvisioningProperties properties = new PlatformProvisioningProperties(
            "RAILWAY_API",
            "https://backboard.railway.com/graphql/v2",
            "token",
            "mahmoudashraf/AI-Fabric-Framework",
            "main",
            "development",
            "workspace-123",
            "ai-infrastructure-module/ai-fabric-runtime",
            "ai-infrastructure-module/ai-fabric-runtime/deploy/railway/Dockerfile",
            "ai-infrastructure-module/ai-infrastructure-generic-rest-connector",
            "ai-infrastructure-module/ai-infrastructure-generic-rest-connector/deploy/railway/Dockerfile",
            "runtime",
            "rest-connector",
            32,
            "https://ai-fabric.dev,http://localhost:8080",
            "https://*lovable*",
            true,
            false,
            60_000,
            Duration.ofSeconds(5),
            Duration.ofMinutes(10)
        );

        RailwayProvisioningPlanService service = new RailwayProvisioningPlanService(
            properties,
            new PlatformDeliveryProperties("https://platform.example", true, Duration.ofDays(3650)),
            artifactService,
            new DeploymentSourceResolver(properties),
            mock(PlatformSecretService.class),
            new ObjectMapper()
        );

        DeploymentEntity deployment = deployment();
        deployment.setId("dep-1234567890");
        deployment.setName("Local Tunnel Provisioning Smoke");
        deployment.setEnvironmentName("development");

        RailwayProvisioningPlanSummary plan = service.buildPlan(deployment, version());

        assertThat(plan.projectName()).isEqualTo("local-tunne-development-12345678");
        assertThat(plan.projectName()).hasSizeLessThanOrEqualTo(32);
    }

    @Test
    void buildPlanAddsPrivateRuntimeMachineAuthEnvWhenEnabledAndSecretsExist() {
        DeploymentArtifactService artifactService = mock(DeploymentArtifactService.class);
        when(artifactService.toBundleSummary(org.mockito.ArgumentMatchers.any())).thenReturn(
            new DeploymentArtifactBundleSummary(
                "dep-123",
                "ver-123",
                "v1",
                "hash-123",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-actions.yml?expires=2016230400&sig=test-actions",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-entity-config.yml?expires=2016230400&sig=test-entities",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/actions-routing.yml?expires=2016230400&sig=test-routing",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-prompt-config.json?expires=2016230400&sig=test-prompts",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/deployment-manifest.json?expires=2016230400&sig=test-manifest"
            )
        );
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        when(platformSecretService.isSecretPresent("AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY")).thenReturn(true);
        when(platformSecretService.isSecretPresent("AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY")).thenReturn(true);
        when(platformSecretService.isSecretPresent("APP_ADMIN_API_KEY")).thenReturn(true);

        RailwayProvisioningPlanService service = new RailwayProvisioningPlanService(
            properties(),
            new PlatformDeliveryProperties("https://platform.example", true, Duration.ofDays(3650)),
            artifactService,
            new DeploymentSourceResolver(properties()),
            platformSecretService,
            new ObjectMapper()
        );

        DeploymentVersionEntity version = version();
        version.setSecurityConfigJson("{\"adminApiKeyEnabled\":true}");

        RailwayProvisioningPlanSummary plan = service.buildPlan(deployment(), version);
        Map<String, String> runtimeEnv = envMap(plan.services().runtime().env());
        Map<String, String> connectorEnv = envMap(plan.services().restConnector().env());

        assertThat(runtimeEnv)
            .containsEntry("AI_FABRIC_RUNTIME_AUTH_INGRESS_MODE", "VERIFIED_CONTEXT_REQUIRED")
            .containsEntry("AI_FABRIC_RUNTIME_REJECT_CONFLICTING_REQUEST_IDENTITY", "true")
            .containsEntry(
                "AI_FABRIC_RUNTIME_AUTH_ACCEPTED_ISSUERS",
                "platform-runtime:SESSION,platform-runtime:API_KEY,platform-poc:SESSION,platform-poc:API_KEY,platform-poc:SYSTEM,platform-release-verification,platform-vectorization-verification,platform-runtime-coverage"
            )
            .containsEntry("AI_FABRIC_RUNTIME_AUTH_ACCEPTED_AUDIENCES", "dep-123")
            .containsEntry(
                "AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY",
                "${secret:AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY}"
            )
            .containsEntry(
                "AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY",
                "${secret:AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY}"
            )
            .containsEntry("AI_ACTIONS_CONNECTOR_ADMIN_API_KEY", "${secret:APP_ADMIN_API_KEY}")
            .containsEntry("AI_ACTIONS_CONNECTOR_ADMIN_API_KEY_HEADER", "X-ADMIN-API-KEY");
        assertThat(connectorEnv)
            .containsEntry("REST_CONNECTOR_RUNTIME_PROXY_API_KEY", "${secret:AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY}")
            .containsEntry("REST_CONNECTOR_RUNTIME_PROXY_API_KEY_HEADER", "X-AIFABRIC-RUNTIME-API-KEY");
    }

    @Test
    void buildPlanKeepsDeploymentIdInExplicitPrivateRuntimeAudiences() {
        DeploymentArtifactService artifactService = mock(DeploymentArtifactService.class);
        when(artifactService.toBundleSummary(org.mockito.ArgumentMatchers.any())).thenReturn(
            new DeploymentArtifactBundleSummary(
                "dep-123",
                "ver-123",
                "v1",
                "hash-123",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-actions.yml?expires=2016230400&sig=test-actions",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-entity-config.yml?expires=2016230400&sig=test-entities",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/actions-routing.yml?expires=2016230400&sig=test-routing",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-prompt-config.json?expires=2016230400&sig=test-prompts",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/deployment-manifest.json?expires=2016230400&sig=test-manifest"
            )
        );
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        when(platformSecretService.isSecretPresent("AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY")).thenReturn(true);
        when(platformSecretService.isSecretPresent("AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY")).thenReturn(true);

        RailwayProvisioningPlanService service = new RailwayProvisioningPlanService(
            properties(),
            new PlatformDeliveryProperties("https://platform.example", true, Duration.ofDays(3650)),
            artifactService,
            new DeploymentSourceResolver(properties()),
            platformSecretService,
            new ObjectMapper()
        );

        DeploymentVersionEntity version = version();
        version.setSecurityConfigJson("""
            {
              "privateRuntimeAcceptedAudiences": "produs-staging"
            }
            """);

        RailwayProvisioningPlanSummary plan = service.buildPlan(deployment(), version);
        Map<String, String> runtimeEnv = envMap(plan.services().runtime().env());

        assertThat(runtimeEnv)
            .containsEntry("AI_FABRIC_RUNTIME_AUTH_ACCEPTED_AUDIENCES", "produs-staging,dep-123");
    }

    @Test
    void buildPlanAddsRuntimePublicTokenValidationEnvWhenSecretExists() {
        DeploymentArtifactService artifactService = mock(DeploymentArtifactService.class);
        when(artifactService.toBundleSummary(org.mockito.ArgumentMatchers.any())).thenReturn(
            new DeploymentArtifactBundleSummary(
                "dep-123",
                "ver-123",
                "v1",
                "hash-123",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-actions.yml?expires=2016230400&sig=test-actions",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-entity-config.yml?expires=2016230400&sig=test-entities",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/actions-routing.yml?expires=2016230400&sig=test-routing",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-prompt-config.json?expires=2016230400&sig=test-prompts",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/deployment-manifest.json?expires=2016230400&sig=test-manifest"
            )
        );
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        when(platformSecretService.isSecretPresent("AI_FABRIC_RUNTIME_PUBLIC_TOKEN_SIGNING_KEY")).thenReturn(true);

        RailwayProvisioningPlanService service = new RailwayProvisioningPlanService(
            properties(),
            new PlatformDeliveryProperties("https://platform.example", true, Duration.ofDays(3650)),
            artifactService,
            new DeploymentSourceResolver(properties()),
            platformSecretService,
            new ObjectMapper()
        );

        DeploymentVersionEntity version = version();
        version.setSecurityConfigJson("""
            {
              "authzMode": "REMOTE_HTTP",
              "adminApiKeyEnabled": true,
              "connectorApiKeyEnabled": true,
              "publicRuntimeBootstrapEnabled": true,
              "publicRuntimeTokenIssuer": "shopify-app",
              "publicRuntimeAcceptedIssuers": "shopify-app,runtime-public-bootstrap",
              "publicRuntimeAcceptedAudiences": "storefront-chat",
              "publicRuntimeDefaultAudience": "storefront-chat"
            }
            """);

        RailwayProvisioningPlanSummary plan = service.buildPlan(deployment(), version);
        Map<String, String> runtimeEnv = envMap(plan.services().runtime().env());

        assertThat(runtimeEnv)
            .containsEntry("AI_FABRIC_RUNTIME_AUTH_INGRESS_MODE", "VERIFIED_CONTEXT_REQUIRED")
            .containsEntry("AI_FABRIC_RUNTIME_REJECT_CONFLICTING_REQUEST_IDENTITY", "true")
            .containsEntry(
                "AI_FABRIC_RUNTIME_PUBLIC_TOKEN_SIGNING_KEY",
                "${secret:AI_FABRIC_RUNTIME_PUBLIC_TOKEN_SIGNING_KEY}"
            )
            .containsEntry("AI_FABRIC_RUNTIME_PUBLIC_BOOTSTRAP_ENABLED", "true")
            .containsEntry("AI_FABRIC_RUNTIME_PUBLIC_BOOTSTRAP_ALLOWED_ORIGINS", "https://ai-fabric.dev,http://localhost:8080")
            .containsEntry("AI_FABRIC_RUNTIME_PUBLIC_TOKEN_ISSUER", "shopify-app")
            .containsEntry("AI_FABRIC_RUNTIME_PUBLIC_TOKEN_ACCEPTED_ISSUERS", "shopify-app,runtime-public-bootstrap")
            .containsEntry("AI_FABRIC_RUNTIME_PUBLIC_TOKEN_ACCEPTED_AUDIENCES", "storefront-chat")
            .containsEntry("AI_FABRIC_RUNTIME_PUBLIC_TOKEN_DEFAULT_AUDIENCE", "storefront-chat");
    }

    @Test
    void buildPlanKeepsPublicTokenValidationDisabledWithoutExplicitDeploymentOptIn() {
        DeploymentArtifactService artifactService = mock(DeploymentArtifactService.class);
        when(artifactService.toBundleSummary(org.mockito.ArgumentMatchers.any())).thenReturn(
            new DeploymentArtifactBundleSummary(
                "dep-123",
                "ver-123",
                "v1",
                "hash-123",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-actions.yml?expires=2016230400&sig=test-actions",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-entity-config.yml?expires=2016230400&sig=test-entities",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/actions-routing.yml?expires=2016230400&sig=test-routing",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-prompt-config.json?expires=2016230400&sig=test-prompts",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/deployment-manifest.json?expires=2016230400&sig=test-manifest"
            )
        );
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        when(platformSecretService.isSecretPresent("AI_FABRIC_RUNTIME_PUBLIC_TOKEN_SIGNING_KEY")).thenReturn(true);

        RailwayProvisioningPlanService service = new RailwayProvisioningPlanService(
            properties(),
            new PlatformDeliveryProperties("https://platform.example", true, Duration.ofDays(3650)),
            artifactService,
            new DeploymentSourceResolver(properties()),
            platformSecretService,
            new ObjectMapper()
        );

        DeploymentVersionEntity version = version();
        version.setSecurityConfigJson("""
            {
              "authzMode": "REMOTE_HTTP",
              "adminApiKeyEnabled": true,
              "connectorApiKeyEnabled": true
            }
            """);

        RailwayProvisioningPlanSummary plan = service.buildPlan(deployment(), version);
        Map<String, String> runtimeEnv = envMap(plan.services().runtime().env());

        assertThat(runtimeEnv)
            .containsEntry("AI_FABRIC_RUNTIME_AUTH_INGRESS_MODE", "VERIFIED_CONTEXT_REQUIRED")
            .containsEntry("AI_FABRIC_RUNTIME_REJECT_CONFLICTING_REQUEST_IDENTITY", "true")
            .doesNotContainKey("AI_FABRIC_RUNTIME_PUBLIC_TOKEN_SIGNING_KEY")
            .doesNotContainKey("AI_FABRIC_RUNTIME_PUBLIC_BOOTSTRAP_ENABLED")
            .doesNotContainKey("AI_FABRIC_RUNTIME_PUBLIC_TOKEN_ISSUER")
            .doesNotContainKey("AI_FABRIC_RUNTIME_PUBLIC_TOKEN_ACCEPTED_ISSUERS")
            .doesNotContainKey("AI_FABRIC_RUNTIME_PUBLIC_TOKEN_ACCEPTED_AUDIENCES")
            .doesNotContainKey("AI_FABRIC_RUNTIME_PUBLIC_TOKEN_DEFAULT_AUDIENCE");
    }

    @Test
    void buildPlanUsesDeploymentSourceOverridesWhenPresent() {
        DeploymentArtifactService artifactService = mock(DeploymentArtifactService.class);
        when(artifactService.toBundleSummary(org.mockito.ArgumentMatchers.any())).thenReturn(
            new DeploymentArtifactBundleSummary(
                "dep-123",
                "ver-123",
                "v1",
                "hash-123",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-actions.yml?expires=2016230400&sig=test-actions",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-entity-config.yml?expires=2016230400&sig=test-entities",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/actions-routing.yml?expires=2016230400&sig=test-routing",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-prompt-config.json?expires=2016230400&sig=test-prompts",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/deployment-manifest.json?expires=2016230400&sig=test-manifest"
            )
        );

        PlatformProvisioningProperties properties = properties();
        RailwayProvisioningPlanService service = new RailwayProvisioningPlanService(
            properties,
            new PlatformDeliveryProperties("https://platform.example", true, Duration.ofDays(3650)),
            artifactService,
            new DeploymentSourceResolver(properties),
            mock(PlatformSecretService.class),
            new ObjectMapper()
        );

        DeploymentEntity deployment = deployment();
        deployment.setSourceRepositoryOverride("example/custom-runtime");
        deployment.setSourceBranchOverride("release-candidate");

        RailwayProvisioningPlanSummary plan = service.buildPlan(deployment, version());

        assertThat(plan.repository()).isEqualTo("example/custom-runtime");
        assertThat(plan.branch()).isEqualTo("release-candidate");
    }

    @Test
    void buildPlanAddsCuratedPackEnvFromCuratedModuleFallback() {
        DeploymentArtifactService artifactService = mock(DeploymentArtifactService.class);
        when(artifactService.toBundleSummary(org.mockito.ArgumentMatchers.any())).thenReturn(
            new DeploymentArtifactBundleSummary(
                "dep-123",
                "ver-123",
                "v1",
                "hash-123",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-actions.yml?expires=2016230400&sig=test-actions",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-entity-config.yml?expires=2016230400&sig=test-entities",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/actions-routing.yml?expires=2016230400&sig=test-routing",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-prompt-config.json?expires=2016230400&sig=test-prompts",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/deployment-manifest.json?expires=2016230400&sig=test-manifest"
            )
        );

        RailwayProvisioningPlanService service = new RailwayProvisioningPlanService(
            properties(),
            new PlatformDeliveryProperties("https://platform.example", true, Duration.ofDays(3650)),
            artifactService,
            new DeploymentSourceResolver(properties()),
            mock(PlatformSecretService.class),
            new ObjectMapper()
        );

        DeploymentVersionEntity version = version();
        version.setProviderConfigJson("""
            {
              "llmProvider": "openai",
              "embeddingProvider": "openai",
              "curatedModuleId": "commerce"
            }
            """);

        RailwayProvisioningPlanSummary plan = service.buildPlan(deployment(), version);
        Map<String, String> runtimeEnv = envMap(plan.services().runtime().env());

        assertThat(runtimeEnv).containsEntry("AI_CURATED_PACK", "commerce");
    }

    @Test
    void buildPlanCompilesManagedAnthropicAndQdrantSettingsIntoLiveEnv() {
        DeploymentArtifactService artifactService = mock(DeploymentArtifactService.class);
        when(artifactService.toBundleSummary(org.mockito.ArgumentMatchers.any())).thenReturn(
            new DeploymentArtifactBundleSummary(
                "dep-123",
                "ver-123",
                "v1",
                "hash-123",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-actions.yml?expires=2016230400&sig=test-actions",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-entity-config.yml?expires=2016230400&sig=test-entities",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/actions-routing.yml?expires=2016230400&sig=test-routing",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-prompt-config.json?expires=2016230400&sig=test-prompts",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/deployment-manifest.json?expires=2016230400&sig=test-manifest"
            )
        );
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        when(platformSecretService.isSecretPresent("QDRANT_API_KEY")).thenReturn(true);

        RailwayProvisioningPlanService service = new RailwayProvisioningPlanService(
            properties(),
            new PlatformDeliveryProperties("https://platform.example", true, Duration.ofDays(3650)),
            artifactService,
            new DeploymentSourceResolver(properties()),
            platformSecretService,
            new ObjectMapper()
        );

        DeploymentVersionEntity version = version();
        version.setProviderConfigJson("""
            {
              "llmProvider": "anthropic",
              "embeddingProvider": "onnx",
              "vectorStrategy": "qdrant",
              "runtimeProfile": "runtime-managed",
              "connectorProfile": "connector-passive",
              "anthropicBaseUrl": "https://anthropic-gateway.example",
              "anthropicModel": "claude-3-5-haiku-latest",
              "onnxModelAlias": "all-mpnet-base-v2",
              "onnxMaxSequenceLength": "384",
              "onnxUseGpu": true,
              "qdrantHost": "qdrant.internal",
              "qdrantPort": "6333",
              "qdrantGrpcPort": "6334",
              "qdrantPreferGrpc": true
            }
            """);
        version.setSecurityConfigJson("""
            {
              "authzMode": "DENY_ALL",
              "adminApiKeyEnabled": false,
              "connectorApiKeyEnabled": false
            }
            """);

        RailwayProvisioningPlanSummary plan = service.buildPlan(deployment(), version);
        Map<String, String> runtimeEnv = envMap(plan.services().runtime().env());
        Map<String, String> connectorEnv = envMap(plan.services().restConnector().env());

        assertThat(runtimeEnv)
            .containsEntry("AI_PROVIDERS_LLM_PROVIDER", "anthropic")
            .containsEntry("AI_PROVIDERS_EMBEDDING_PROVIDER", "onnx")
            .containsEntry("AI_VECTOR_DB_TYPE", "qdrant")
            .containsEntry("AI_PROVIDERS_ANTHROPIC_ENABLED", "true")
            .containsEntry("AI_PROVIDERS_ANTHROPIC_API_KEY", "${secret:ANTHROPIC_API_KEY}")
            .containsEntry("AI_PROVIDERS_ANTHROPIC_BASE_URL", "https://anthropic-gateway.example")
            .containsEntry("AI_PROVIDERS_ANTHROPIC_MODEL", "claude-3-5-haiku-latest")
            .containsEntry("AI_PROVIDERS_ONNX_ENABLED", "true")
            .containsEntry("AI_PROVIDERS_ONNX_MODEL_ALIAS", "all-mpnet-base-v2")
            .containsEntry("AI_PROVIDERS_ONNX_MAX_SEQUENCE_LENGTH", "384")
            .containsEntry("AI_PROVIDERS_ONNX_USE_GPU", "true")
            .containsEntry("AI_PROVIDERS_QDRANT_HOST", "qdrant.internal")
            .containsEntry("AI_PROVIDERS_QDRANT_API_KEY", "${secret:QDRANT_API_KEY}")
            .containsEntry("AI_FABRIC_RUNTIME_AUTHZ_MODE", "DENY_ALL")
            .containsEntry("OPENAI_ENABLED", "false")
            .doesNotContainKey("ACTIONS_CONNECTOR_API_KEY")
            .doesNotContainKey("OPENAI_API_KEY")
            .doesNotContainKey("AUTHZ_BASE_URL");
        assertThat(connectorEnv)
            .containsEntry("REST_CONNECTOR_RUNTIME_PROXY_ENABLED", "false")
            .doesNotContainKey("REST_CONNECTOR_RUNTIME_PROXY_BASE_URL")
            .doesNotContainKey("CONNECTOR_API_KEY");
    }

    @Test
    void buildPlanUsesManagedQdrantRuntimeSecretWhenProvisioningInjectsIt() throws Exception {
        DeploymentArtifactService artifactService = mock(DeploymentArtifactService.class);
        when(artifactService.toBundleSummary(org.mockito.ArgumentMatchers.any())).thenReturn(
            new DeploymentArtifactBundleSummary(
                "dep-123",
                "ver-123",
                "v1",
                "hash-123",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-actions.yml?expires=2016230400&sig=test-actions",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-entity-config.yml?expires=2016230400&sig=test-entities",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/actions-routing.yml?expires=2016230400&sig=test-routing",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-prompt-config.json?expires=2016230400&sig=test-prompts",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/deployment-manifest.json?expires=2016230400&sig=test-manifest"
            )
        );
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        when(platformSecretService.isSecretPresent("MANAGED_QDRANT_DB_API_KEY_DEP_DEP_123")).thenReturn(true);

        RailwayProvisioningPlanService service = new RailwayProvisioningPlanService(
            properties(),
            new PlatformDeliveryProperties("https://platform.example", true, Duration.ofDays(3650)),
            artifactService,
            new DeploymentSourceResolver(properties()),
            platformSecretService,
            new ObjectMapper()
        );

        DeploymentVersionEntity version = version();
        JsonNode override = new ObjectMapper().readTree("""
            {
              "llmProvider": "openai",
              "embeddingProvider": "openai",
              "vectorStrategy": "qdrant",
              "vectorProvisioningMode": "PLATFORM_MANAGED",
              "runtimeProfile": "runtime-managed",
              "connectorProfile": "connector-hosted",
              "qdrantHost": "https://cluster.example",
              "qdrantPort": "6333",
              "qdrantGrpcPort": "6334",
              "qdrantRuntimeApiKeySecretName": "MANAGED_QDRANT_DB_API_KEY_DEP_DEP_123"
            }
            """);

        RailwayProvisioningPlanSummary plan = service.buildPlan(deployment(), version, override);
        Map<String, String> runtimeEnv = envMap(plan.services().runtime().env());

        assertThat(runtimeEnv)
            .containsEntry("AI_PROVIDERS_QDRANT_HOST", "https://cluster.example")
            .containsEntry("AI_PROVIDERS_QDRANT_API_KEY", "${secret:MANAGED_QDRANT_DB_API_KEY_DEP_DEP_123}");
    }

    @Test
    void buildPlanCompilesOpenAiOverridesIntoLiveEnv() {
        DeploymentArtifactService artifactService = mock(DeploymentArtifactService.class);
        when(artifactService.toBundleSummary(org.mockito.ArgumentMatchers.any())).thenReturn(
            new DeploymentArtifactBundleSummary(
                "dep-123",
                "ver-123",
                "v1",
                "hash-123",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-actions.yml?expires=2016230400&sig=test-actions",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-entity-config.yml?expires=2016230400&sig=test-entities",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/actions-routing.yml?expires=2016230400&sig=test-routing",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-prompt-config.json?expires=2016230400&sig=test-prompts",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/deployment-manifest.json?expires=2016230400&sig=test-manifest"
            )
        );

        RailwayProvisioningPlanService service = new RailwayProvisioningPlanService(
            properties(),
            new PlatformDeliveryProperties("https://platform.example", true, Duration.ofDays(3650)),
            artifactService,
            new DeploymentSourceResolver(properties()),
            mock(PlatformSecretService.class),
            new ObjectMapper()
        );

        DeploymentVersionEntity version = version();
        version.setEntityConfigJson("""
            {
              "ai-config": { "vector-dimensions": 1536 },
              "ai-entities": {}
            }
            """);
        version.setProviderConfigJson("""
            {
              "llmProvider": "openai",
              "embeddingProvider": "openai",
              "vectorStrategy": "lucene",
              "runtimeProfile": "runtime-managed",
              "connectorProfile": "connector-hosted",
              "openaiBaseUrl": "https://gateway.example/openai",
              "openaiModel": "gpt-4.1-mini",
              "openaiEmbeddingModel": "text-embedding-3-large",
              "openaiEmbeddingDimensions": "1024"
            }
            """);

        RailwayProvisioningPlanSummary plan = service.buildPlan(deployment(), version);
        Map<String, String> runtimeEnv = envMap(plan.services().runtime().env());

        assertThat(runtimeEnv)
            .containsEntry("AI_PROVIDERS_LLM_PROVIDER", "openai")
            .containsEntry("AI_PROVIDERS_EMBEDDING_PROVIDER", "openai")
            .containsEntry("AI_VECTOR_DB_TYPE", "lucene")
            .containsEntry("AI_PROVIDERS_OPENAI_ENABLED", "true")
            .containsEntry("AI_PROVIDERS_OPENAI_API_KEY", "${secret:OPENAI_API_KEY}")
            .containsEntry("AI_PROVIDERS_OPENAI_BASE_URL", "https://gateway.example/openai")
            .containsEntry("AI_PROVIDERS_OPENAI_MODEL", "gpt-4.1-mini")
            .containsEntry("AI_PROVIDERS_OPENAI_EMBEDDING_MODEL", "text-embedding-3-large")
            .containsEntry("AI_PROVIDERS_OPENAI_EMBEDDING_DIMENSIONS", "1024")
            .containsEntry("OPENAI_MODEL", "gpt-4.1-mini")
            .containsEntry("OPENAI_EMBEDDING_MODEL", "text-embedding-3-large")
            .containsEntry("OPENAI_EMBEDDING_DIMENSIONS", "1024")
            .containsEntry("OPENAI_ENABLED", "true");
    }

    @Test
    void buildPlanFallsBackToEntityVectorDimensionsForOpenAiLuceneWhenProviderDimensionsAreUnset() {
        DeploymentArtifactService artifactService = mock(DeploymentArtifactService.class);
        when(artifactService.toBundleSummary(org.mockito.ArgumentMatchers.any())).thenReturn(
            new DeploymentArtifactBundleSummary(
                "dep-123",
                "ver-123",
                "v1",
                "hash-123",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-actions.yml?expires=2016230400&sig=test-actions",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-entity-config.yml?expires=2016230400&sig=test-entities",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/actions-routing.yml?expires=2016230400&sig=test-routing",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-prompt-config.json?expires=2016230400&sig=test-prompts",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/deployment-manifest.json?expires=2016230400&sig=test-manifest"
            )
        );

        RailwayProvisioningPlanService service = new RailwayProvisioningPlanService(
            properties(),
            new PlatformDeliveryProperties("https://platform.example", true, Duration.ofDays(3650)),
            artifactService,
            new DeploymentSourceResolver(properties()),
            mock(PlatformSecretService.class),
            new ObjectMapper()
        );

        DeploymentVersionEntity version = version();
        version.setEntityConfigJson("""
            {
              "ai-config": { "vector-dimensions": 512 },
              "ai-entities": {}
            }
            """);
        version.setProviderConfigJson("""
            {
              "llmProvider": "openai",
              "embeddingProvider": "openai",
              "vectorStrategy": "lucene",
              "runtimeProfile": "runtime-managed",
              "connectorProfile": "connector-hosted",
              "openaiEmbeddingModel": "text-embedding-3-small"
            }
            """);

        RailwayProvisioningPlanSummary plan = service.buildPlan(deployment(), version);
        Map<String, String> runtimeEnv = envMap(plan.services().runtime().env());

        assertThat(runtimeEnv)
            .containsEntry("AI_PROVIDERS_OPENAI_EMBEDDING_DIMENSIONS", "512")
            .containsEntry("OPENAI_EMBEDDING_DIMENSIONS", "512");
    }

    @Test
    void buildPlanCompilesPurposeSpecificRoutingAndAdvancedProviderTuning() {
        DeploymentArtifactService artifactService = mock(DeploymentArtifactService.class);
        when(artifactService.toBundleSummary(org.mockito.ArgumentMatchers.any())).thenReturn(
            new DeploymentArtifactBundleSummary(
                "dep-123",
                "ver-123",
                "v1",
                "hash-123",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-actions.yml?expires=2016230400&sig=test-actions",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-entity-config.yml?expires=2016230400&sig=test-entities",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/actions-routing.yml?expires=2016230400&sig=test-routing",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-prompt-config.json?expires=2016230400&sig=test-prompts",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/deployment-manifest.json?expires=2016230400&sig=test-manifest"
            )
        );

        RailwayProvisioningPlanService service = new RailwayProvisioningPlanService(
            properties(),
            new PlatformDeliveryProperties("https://platform.example", true, Duration.ofDays(3650)),
            artifactService,
            new DeploymentSourceResolver(properties()),
            mock(PlatformSecretService.class),
            new ObjectMapper()
        );

        DeploymentVersionEntity version = version();
        version.setProviderConfigJson("""
            {
              "llmProvider": "openai",
              "embeddingProvider": "onnx",
              "vectorStrategy": "lucene",
              "runtimeProfile": "runtime-managed",
              "connectorProfile": "connector-hosted",
              "enableFallback": false,
              "orchestrationLlmProvider": "azure",
              "orchestrationModel": "gpt-4o-orchestrator",
              "orchestrationTemperature": "0.1",
              "orchestrationMaxTokens": "900",
              "orchestrationTimeout": "22",
              "generationLlmProvider": "anthropic",
              "generationModel": "claude-3-7-sonnet-latest",
              "generationTemperature": "0.4",
              "generationMaxTokens": "1400",
              "generationTimeout": "33",
              "openaiValidateOnStartup": true,
              "openaiMaxTokens": "1200",
              "openaiTemperature": "0.2",
              "openaiTimeout": "45",
              "openaiPriority": "120",
              "azureEndpoint": "https://azure.example",
              "azureDeploymentName": "azure-router",
              "azureApiVersion": "2024-08-01-preview",
              "azureValidateOnStartup": true,
              "azureTimeout": "55",
              "azurePriority": "90",
              "anthropicModel": "claude-3-7-sonnet-latest",
              "anthropicMaxTokens": "2100",
              "anthropicTemperature": "0.5",
              "anthropicTimeout": "44",
              "anthropicPriority": "88"
            }
            """);

        RailwayProvisioningPlanSummary plan = service.buildPlan(deployment(), version);
        Map<String, String> runtimeEnv = envMap(plan.services().runtime().env());

        assertThat(runtimeEnv)
            .containsEntry("AI_PROVIDERS_ENABLE_FALLBACK", "false")
            .containsEntry("AI_PROVIDERS_ORCHESTRATION_LLM_PROVIDER", "azure")
            .containsEntry("AI_PROVIDERS_ORCHESTRATION_MODEL", "gpt-4o-orchestrator")
            .containsEntry("AI_PROVIDERS_ORCHESTRATION_TEMPERATURE", "0.1")
            .containsEntry("AI_PROVIDERS_ORCHESTRATION_MAX_TOKENS", "900")
            .containsEntry("AI_PROVIDERS_ORCHESTRATION_TIMEOUT", "22")
            .containsEntry("AI_PROVIDERS_GENERATION_LLM_PROVIDER", "anthropic")
            .containsEntry("AI_PROVIDERS_GENERATION_MODEL", "claude-3-7-sonnet-latest")
            .containsEntry("AI_PROVIDERS_GENERATION_TEMPERATURE", "0.4")
            .containsEntry("AI_PROVIDERS_GENERATION_MAX_TOKENS", "1400")
            .containsEntry("AI_PROVIDERS_GENERATION_TIMEOUT", "33")
            .containsEntry("AI_PROVIDERS_OPENAI_VALIDATE_ON_STARTUP", "true")
            .containsEntry("AI_PROVIDERS_OPENAI_MAX_TOKENS", "1200")
            .containsEntry("AI_PROVIDERS_OPENAI_TEMPERATURE", "0.2")
            .containsEntry("AI_PROVIDERS_OPENAI_TIMEOUT", "45")
            .containsEntry("AI_PROVIDERS_OPENAI_PRIORITY", "120")
            .containsEntry("AI_PROVIDERS_AZURE_DEPLOYMENT_NAME", "azure-router")
            .containsEntry("AI_PROVIDERS_AZURE_VALIDATE_ON_STARTUP", "true")
            .containsEntry("AI_PROVIDERS_AZURE_TIMEOUT", "55")
            .containsEntry("AI_PROVIDERS_AZURE_PRIORITY", "90")
            .containsEntry("AI_PROVIDERS_ANTHROPIC_MAX_TOKENS", "2100")
            .containsEntry("AI_PROVIDERS_ANTHROPIC_TEMPERATURE", "0.5")
            .containsEntry("AI_PROVIDERS_ANTHROPIC_TIMEOUT", "44")
            .containsEntry("AI_PROVIDERS_ANTHROPIC_PRIORITY", "88");
    }

    @Test
    void buildPlanCompilesAzureAndPineconeSettingsIntoLiveEnv() {
        DeploymentArtifactService artifactService = mock(DeploymentArtifactService.class);
        when(artifactService.toBundleSummary(org.mockito.ArgumentMatchers.any())).thenReturn(
            new DeploymentArtifactBundleSummary(
                "dep-123",
                "ver-123",
                "v1",
                "hash-123",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-actions.yml?expires=2016230400&sig=test-actions",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-entity-config.yml?expires=2016230400&sig=test-entities",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/actions-routing.yml?expires=2016230400&sig=test-routing",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-prompt-config.json?expires=2016230400&sig=test-prompts",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/deployment-manifest.json?expires=2016230400&sig=test-manifest"
            )
        );

        RailwayProvisioningPlanService service = new RailwayProvisioningPlanService(
            properties(),
            new PlatformDeliveryProperties("https://platform.example", true, Duration.ofDays(3650)),
            artifactService,
            new DeploymentSourceResolver(properties()),
            mock(PlatformSecretService.class),
            new ObjectMapper()
        );

        DeploymentVersionEntity version = version();
        version.setEntityConfigJson("""
            {
              "ai-config": { "vector-dimensions": 1024 },
              "ai-entities": {}
            }
            """);
        version.setProviderConfigJson("""
            {
              "llmProvider": "azure",
              "embeddingProvider": "azure",
              "vectorStrategy": "pinecone",
              "runtimeProfile": "runtime-managed",
              "connectorProfile": "connector-hosted",
              "azureEndpoint": "https://example-resource.openai.azure.com",
              "azureDeploymentName": "gpt-4o-deployment",
              "azureEmbeddingDeploymentName": "embedding-deployment",
              "azureApiVersion": "2024-02-15-preview",
              "pineconeEnvironment": "us-east-1-aws",
              "pineconeIndexName": "ai-fabric",
              "pineconeProjectId": "proj-123",
              "pineconeDimensions": "1024"
            }
            """);

        RailwayProvisioningPlanSummary plan = service.buildPlan(deployment(), version);
        Map<String, String> runtimeEnv = envMap(plan.services().runtime().env());

        assertThat(runtimeEnv)
            .containsEntry("AI_PROVIDERS_LLM_PROVIDER", "azure")
            .containsEntry("AI_PROVIDERS_EMBEDDING_PROVIDER", "azure")
            .containsEntry("AI_VECTOR_DB_TYPE", "pinecone")
            .containsEntry("AI_PROVIDERS_AZURE_ENABLED", "true")
            .containsEntry("AI_PROVIDERS_AZURE_API_KEY", "${secret:AZURE_OPENAI_API_KEY}")
            .containsEntry("AI_PROVIDERS_AZURE_ENDPOINT", "https://example-resource.openai.azure.com")
            .containsEntry("AI_PROVIDERS_AZURE_DEPLOYMENT_NAME", "gpt-4o-deployment")
            .containsEntry("AI_PROVIDERS_AZURE_EMBEDDING_DEPLOYMENT_NAME", "embedding-deployment")
            .containsEntry("AI_PROVIDERS_PINECONE_ENABLED", "true")
            .containsEntry("AI_PROVIDERS_PINECONE_API_KEY", "${secret:PINECONE_API_KEY}")
            .containsEntry("AI_PROVIDERS_PINECONE_ENVIRONMENT", "us-east-1-aws")
            .containsEntry("AI_PROVIDERS_PINECONE_INDEX_NAME", "ai-fabric")
            .containsEntry("AI_PROVIDERS_PINECONE_PROJECT_ID", "proj-123")
            .containsEntry("AI_PROVIDERS_PINECONE_DIMENSIONS", "1024")
            .containsEntry("OPENAI_ENABLED", "false");
    }

    @Test
    void buildPlanUsesManagedVectorProviderOverrideForResolvedPineconeHost() throws Exception {
        DeploymentArtifactService artifactService = mock(DeploymentArtifactService.class);
        when(artifactService.toBundleSummary(org.mockito.ArgumentMatchers.any())).thenReturn(
            new DeploymentArtifactBundleSummary(
                "dep-123",
                "ver-123",
                "v1",
                "hash-123",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-actions.yml?expires=2016230400&sig=test-actions",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-entity-config.yml?expires=2016230400&sig=test-entities",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/actions-routing.yml?expires=2016230400&sig=test-routing",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-prompt-config.json?expires=2016230400&sig=test-prompts",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/deployment-manifest.json?expires=2016230400&sig=test-manifest"
            )
        );
        PlatformSecretService secretService = mock(PlatformSecretService.class);
        when(secretService.isSecretPresent("MANAGED_PINECONE_API_KEY_DEP_DEP_123")).thenReturn(true);

        RailwayProvisioningPlanService service = new RailwayProvisioningPlanService(
            properties(),
            new PlatformDeliveryProperties("https://platform.example", true, Duration.ofDays(3650)),
            artifactService,
            new DeploymentSourceResolver(properties()),
            secretService,
            new ObjectMapper()
        );

        DeploymentVersionEntity version = version();
        version.setEntityConfigJson("""
            {
              "ai-config": { "vector-dimensions": 1024 },
              "ai-entities": {}
            }
            """);
        version.setProviderConfigJson("""
            {
              "llmProvider": "azure",
              "embeddingProvider": "azure",
              "vectorStrategy": "pinecone",
              "vectorProvisioningMode": "PLATFORM_MANAGED",
              "runtimeProfile": "runtime-managed",
              "connectorProfile": "connector-hosted",
              "azureEndpoint": "https://example-resource.openai.azure.com",
              "azureDeploymentName": "gpt-4o-deployment",
              "azureEmbeddingDeploymentName": "embedding-deployment",
              "azureApiVersion": "2024-02-15-preview",
              "pineconeManagedIndexEnabled": true,
              "pineconeIndexName": "ai-fabric",
              "pineconeCloud": "aws",
              "pineconeRegion": "eu-west-1",
              "pineconeMetric": "cosine",
              "pineconeDimensions": "1024"
            }
            """);

        JsonNode override = new ObjectMapper().readTree("""
            {
              "llmProvider": "azure",
              "embeddingProvider": "azure",
              "vectorStrategy": "pinecone",
              "vectorProvisioningMode": "PLATFORM_MANAGED",
              "runtimeProfile": "runtime-managed",
              "connectorProfile": "connector-hosted",
              "azureEndpoint": "https://example-resource.openai.azure.com",
              "azureDeploymentName": "gpt-4o-deployment",
              "azureEmbeddingDeploymentName": "embedding-deployment",
              "azureApiVersion": "2024-02-15-preview",
              "pineconeManagedIndexEnabled": true,
              "pineconeIndexName": "ai-fabric",
              "pineconeApiHost": "ai-fabric-abc123.svc.eu-west-1-aws.pinecone.io",
              "pineconeRuntimeApiKeySecretName": "MANAGED_PINECONE_API_KEY_DEP_DEP_123",
              "pineconeCloud": "aws",
              "pineconeRegion": "eu-west-1",
              "pineconeMetric": "cosine",
              "pineconeDimensions": "1024"
            }
            """);

        RailwayProvisioningPlanSummary plan = service.buildPlan(deployment(), version, override);
        Map<String, String> runtimeEnv = envMap(plan.services().runtime().env());

        assertThat(runtimeEnv)
            .containsEntry("AI_PROVIDERS_PINECONE_API_KEY", "${secret:MANAGED_PINECONE_API_KEY_DEP_DEP_123}")
            .containsEntry("AI_PROVIDERS_PINECONE_API_HOST", "ai-fabric-abc123.svc.eu-west-1-aws.pinecone.io")
            .containsEntry("AI_PROVIDERS_PINECONE_INDEX_NAME", "ai-fabric");
        assertThat(plan.steps()).extracting(RailwayProvisioningStepSummary::key).contains("ensure_vector_backend");
    }

    @Test
    void buildPlanCompilesCohereAndWeaviateSettingsIntoLiveEnv() {
        DeploymentArtifactService artifactService = mock(DeploymentArtifactService.class);
        when(artifactService.toBundleSummary(org.mockito.ArgumentMatchers.any())).thenReturn(
            new DeploymentArtifactBundleSummary(
                "dep-123",
                "ver-123",
                "v1",
                "hash-123",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-actions.yml?expires=2016230400&sig=test-actions",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-entity-config.yml?expires=2016230400&sig=test-entities",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/actions-routing.yml?expires=2016230400&sig=test-routing",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-prompt-config.json?expires=2016230400&sig=test-prompts",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/deployment-manifest.json?expires=2016230400&sig=test-manifest"
            )
        );
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        when(platformSecretService.isSecretPresent("WEAVIATE_API_KEY")).thenReturn(true);

        RailwayProvisioningPlanService service = new RailwayProvisioningPlanService(
            properties(),
            new PlatformDeliveryProperties("https://platform.example", true, Duration.ofDays(3650)),
            artifactService,
            new DeploymentSourceResolver(properties()),
            platformSecretService,
            new ObjectMapper()
        );

        DeploymentVersionEntity version = version();
        version.setProviderConfigJson("""
            {
              "llmProvider": "cohere",
              "embeddingProvider": "cohere",
              "vectorStrategy": "weaviate",
              "runtimeProfile": "runtime-managed",
              "connectorProfile": "connector-hosted",
              "cohereModel": "command-r7b-12-2024",
              "cohereEmbeddingModel": "embed-english-v3.0",
              "weaviateScheme": "https",
              "weaviateHost": "weaviate.internal",
              "weaviatePort": "443",
              "weaviateConsistencyLevelStrong": true
            }
            """);

        RailwayProvisioningPlanSummary plan = service.buildPlan(deployment(), version);
        Map<String, String> runtimeEnv = envMap(plan.services().runtime().env());

        assertThat(runtimeEnv)
            .containsEntry("AI_PROVIDERS_LLM_PROVIDER", "cohere")
            .containsEntry("AI_PROVIDERS_EMBEDDING_PROVIDER", "cohere")
            .containsEntry("AI_VECTOR_DB_TYPE", "weaviate")
            .containsEntry("AI_PROVIDERS_COHERE_ENABLED", "true")
            .containsEntry("AI_PROVIDERS_COHERE_API_KEY", "${secret:COHERE_API_KEY}")
            .containsEntry("AI_PROVIDERS_COHERE_MODEL", "command-r7b-12-2024")
            .containsEntry("AI_PROVIDERS_COHERE_EMBEDDING_MODEL", "embed-english-v3.0")
            .containsEntry("AI_PROVIDERS_WEAVIATE_ENABLED", "true")
            .containsEntry("AI_PROVIDERS_WEAVIATE_HOST", "weaviate.internal")
            .containsEntry("AI_PROVIDERS_WEAVIATE_PORT", "443")
            .containsEntry("AI_PROVIDERS_WEAVIATE_API_KEY", "${secret:WEAVIATE_API_KEY}")
            .containsEntry("OPENAI_ENABLED", "false");
    }

    @Test
    void buildPlanUsesManagedMilvusRuntimeSecretsWhenZillizCloudProvisioningHasBoundThem() {
        DeploymentArtifactService artifactService = mock(DeploymentArtifactService.class);
        when(artifactService.toBundleSummary(org.mockito.ArgumentMatchers.any())).thenReturn(
            new DeploymentArtifactBundleSummary(
                "dep-123",
                "ver-123",
                "v1",
                "hash-123",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-actions.yml?expires=2016230400&sig=test-actions",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-entity-config.yml?expires=2016230400&sig=test-entities",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/actions-routing.yml?expires=2016230400&sig=test-routing",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-prompt-config.json?expires=2016230400&sig=test-prompts",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/deployment-manifest.json?expires=2016230400&sig=test-manifest"
            )
        );
        PlatformSecretService secretService = mock(PlatformSecretService.class);
        when(secretService.isSecretPresent("MANAGED_MILVUS_USERNAME_DEP_DEP_123")).thenReturn(true);
        when(secretService.isSecretPresent("MANAGED_MILVUS_PASSWORD_DEP_DEP_123")).thenReturn(true);

        RailwayProvisioningPlanService service = new RailwayProvisioningPlanService(
            properties(),
            new PlatformDeliveryProperties("https://platform.example", true, Duration.ofDays(3650)),
            artifactService,
            new DeploymentSourceResolver(properties()),
            secretService,
            new ObjectMapper()
        );

        DeploymentVersionEntity version = version();
        version.setProviderConfigJson("""
            {
              "llmProvider": "gemini",
              "embeddingProvider": "gemini",
              "vectorStrategy": "milvus",
              "vectorProvisioningMode": "PLATFORM_MANAGED",
              "runtimeProfile": "runtime-managed",
              "connectorProfile": "connector-hosted",
              "geminiModel": "gemini-1.5-flash",
              "milvusHost": "cluster-1.gcp-us-west1.zillizcloud.com",
              "milvusPort": "443",
              "milvusDatabaseName": "default",
              "milvusSecure": true,
              "milvusRuntimeUsernameSecretName": "MANAGED_MILVUS_USERNAME_DEP_DEP_123",
              "milvusRuntimePasswordSecretName": "MANAGED_MILVUS_PASSWORD_DEP_DEP_123"
            }
            """);

        RailwayProvisioningPlanSummary plan = service.buildPlan(deployment(), version);
        Map<String, String> runtimeEnv = envMap(plan.services().runtime().env());

        assertThat(runtimeEnv)
            .containsEntry("AI_VECTOR_DB_TYPE", "milvus")
            .containsEntry("AI_PROVIDERS_MILVUS_HOST", "cluster-1.gcp-us-west1.zillizcloud.com")
            .containsEntry("AI_PROVIDERS_MILVUS_PORT", "443")
            .containsEntry("AI_PROVIDERS_MILVUS_USERNAME", "${secret:MANAGED_MILVUS_USERNAME_DEP_DEP_123}")
            .containsEntry("AI_PROVIDERS_MILVUS_PASSWORD", "${secret:MANAGED_MILVUS_PASSWORD_DEP_DEP_123}");
    }

    @Test
    void buildPlanDerivesSharedStorageProviderHandlesFromCustomerAndTenantBinding() {
        DeploymentArtifactService artifactService = mock(DeploymentArtifactService.class);
        when(artifactService.toBundleSummary(org.mockito.ArgumentMatchers.any())).thenReturn(
            new DeploymentArtifactBundleSummary(
                "dep-123",
                "ver-123",
                "v1",
                "hash-123",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-actions.yml?expires=2016230400&sig=test-actions",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-entity-config.yml?expires=2016230400&sig=test-entities",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/actions-routing.yml?expires=2016230400&sig=test-routing",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-prompt-config.json?expires=2016230400&sig=test-prompts",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/deployment-manifest.json?expires=2016230400&sig=test-manifest"
            )
        );
        PlatformSecretService secretService = mock(PlatformSecretService.class);

        RailwayProvisioningPlanService service = new RailwayProvisioningPlanService(
            properties(),
            new PlatformDeliveryProperties("https://platform.example", true, Duration.ofDays(3650)),
            artifactService,
            new DeploymentSourceResolver(properties()),
            secretService,
            new ObjectMapper()
        );

        DeploymentEntity deployment = deployment();
        deployment.setCustomerId("customer-acme");
        deployment.setTenantId("tenant-retail");

        DeploymentVersionEntity qdrantVersion = version();
        qdrantVersion.setProviderConfigJson("""
            {
              "llmProvider": "openai",
              "embeddingProvider": "openai",
              "vectorStrategy": "qdrant",
              "vectorProvisioningMode": "EXTERNAL_EXISTING",
              "vectorStoragePosture": "SHARED",
              "runtimeProfile": "runtime-managed",
              "connectorProfile": "connector-hosted",
              "qdrantHost": "qdrant.internal"
            }
            """);
        Map<String, String> qdrantEnv = envMap(service.buildPlan(deployment, qdrantVersion).services().runtime().env());
        assertThat(qdrantEnv).containsEntry("AI_PROVIDERS_QDRANT_COLLECTION_PREFIX", "customer_acme__tenant_retail__");

        DeploymentVersionEntity pineconeVersion = version();
        pineconeVersion.setProviderConfigJson("""
            {
              "llmProvider": "openai",
              "embeddingProvider": "openai",
              "vectorStrategy": "pinecone",
              "vectorProvisioningMode": "EXTERNAL_EXISTING",
              "vectorStoragePosture": "SHARED",
              "runtimeProfile": "runtime-managed",
              "connectorProfile": "connector-hosted",
              "pineconeIndexName": "shared-index",
              "pineconeApiHost": "https://shared-index.test.pinecone.io"
            }
            """);
        Map<String, String> pineconeEnv = envMap(service.buildPlan(deployment, pineconeVersion).services().runtime().env());
        assertThat(pineconeEnv).containsEntry("AI_PROVIDERS_PINECONE_NAMESPACE_PREFIX", "customer-acme--tenant-retail");

        DeploymentVersionEntity weaviateVersion = version();
        weaviateVersion.setProviderConfigJson("""
            {
              "llmProvider": "openai",
              "embeddingProvider": "openai",
              "vectorStrategy": "weaviate",
              "vectorProvisioningMode": "EXTERNAL_EXISTING",
              "vectorStoragePosture": "SHARED",
              "runtimeProfile": "runtime-managed",
              "connectorProfile": "connector-hosted",
              "weaviateHost": "weaviate.internal"
            }
            """);
        Map<String, String> weaviateEnv = envMap(service.buildPlan(deployment, weaviateVersion).services().runtime().env());
        assertThat(weaviateEnv)
            .containsEntry("AI_PROVIDERS_WEAVIATE_NATIVE_MULTI_TENANCY_ENABLED", "true")
            .containsEntry("AI_PROVIDERS_WEAVIATE_TENANT_NAME", "tenant-retail");
        assertThat(weaviateEnv.get("AI_PROVIDERS_WEAVIATE_CLASS_PREFIX")).startsWith("CustomerAcme_");

        DeploymentVersionEntity milvusVersion = version();
        milvusVersion.setProviderConfigJson("""
            {
              "llmProvider": "openai",
              "embeddingProvider": "openai",
              "vectorStrategy": "milvus",
              "vectorProvisioningMode": "EXTERNAL_EXISTING",
              "vectorStoragePosture": "SHARED",
              "runtimeProfile": "runtime-managed",
              "connectorProfile": "connector-hosted",
              "milvusHost": "milvus.internal"
            }
            """);
        Map<String, String> milvusEnv = envMap(service.buildPlan(deployment, milvusVersion).services().runtime().env());
        assertThat(milvusEnv).containsEntry("AI_PROVIDERS_MILVUS_COLLECTION_PREFIX", "customer_acme__tenant_retail__");
    }

    @Test
    void buildPlanAddsMcpGatewayRuntimeEnvWhenActionsUseMcpToolAdapter() {
        DeploymentArtifactService artifactService = mock(DeploymentArtifactService.class);
        when(artifactService.toBundleSummary(org.mockito.ArgumentMatchers.any())).thenReturn(
            new DeploymentArtifactBundleSummary(
                "dep-123",
                "ver-123",
                "v1",
                "hash-123",
                "https://platform.example/actions.yml",
                "https://platform.example/entities.yml",
                "https://platform.example/routing.yml",
                "https://platform.example/prompts.json",
                "https://platform.example/manifest.json"
            )
        );
        PlatformManagedProductServiceRepository productServiceRepository = mock(PlatformManagedProductServiceRepository.class);
        PlatformManagedProductServiceEntity gateway = new PlatformManagedProductServiceEntity();
        gateway.setServiceRef("mcp-execution-gateway");
        gateway.setServiceKind("MCP_EXECUTION_GATEWAY_SERVICE");
        gateway.setBaseUrl("https://mcp-gateway.example");
        gateway.setSecretName("MANAGED_PRODUCT_MCP_EXECUTION_GATEWAY_API_KEY");
        when(productServiceRepository.findByServiceRefIgnoreCase("mcp-execution-gateway")).thenReturn(Optional.of(gateway));
        RailwayProvisioningPlanService service = new RailwayProvisioningPlanService(
            properties(),
            new PlatformDeliveryProperties("https://platform.example", true, Duration.ofDays(3650)),
            artifactService,
            new DeploymentSourceResolver(properties()),
            mock(PlatformSecretService.class),
            new ObjectMapper()
        );
        ReflectionTestUtils.setField(service, "platformManagedProductServiceRepository", productServiceRepository);
        DeploymentVersionEntity mcpVersion = version();
        mcpVersion.setActionsConfigJson("""
            {
              "actions": [
                {
                  "name": "shopify_search_catalog",
                  "adapterType": "mcp-tool",
                  "execution": {
                    "adapterType": "mcp-tool",
                    "mcp": {
                      "serverRef": "shopify-storefront",
                      "toolName": "search_catalog",
                      "auth": {
                        "mode": "API_KEY_HEADER_SECRET",
                        "secretRef": "MCP_SECRET_PRODUS_STAGING_MCP_API_KEY"
                      }
                    }
                  }
                }
              ]
            }
            """);

        Map<String, String> runtimeEnv = envMap(service.buildPlan(deployment(), mcpVersion).services().runtime().env());

        assertThat(runtimeEnv)
            .containsEntry("AI_ACTIONS_CONNECTOR_MCP_GATEWAY_BASE_URL", "https://mcp-gateway.example")
            .containsEntry("AI_ACTIONS_CONNECTOR_MCP_GATEWAY_API_KEY", "${secret:MANAGED_PRODUCT_MCP_EXECUTION_GATEWAY_API_KEY}")
            .containsEntry("AI_ACTIONS_CONNECTOR_MCP_GATEWAY_API_KEY_HEADER", "X-MCP-GATEWAY-API-KEY")
            .containsEntry("AI_ACTIONS_CONNECTOR_MCP_GATEWAY_EXECUTE_PATH", "/api/internal/mcp/actions/execute")
            .containsEntry("MCP_SECRET_PRODUS_STAGING_MCP_API_KEY", "${secret:MCP_SECRET_PRODUS_STAGING_MCP_API_KEY}");
    }

    private Map<String, String> envMap(java.util.List<RailwayEnvVarSummary> env) {
        return env.stream().collect(Collectors.toMap(RailwayEnvVarSummary::key, RailwayEnvVarSummary::value));
    }

    private PlatformProvisioningProperties properties() {
        return new PlatformProvisioningProperties(
            "RAILWAY_API",
            "https://backboard.railway.com/graphql/v2",
            "token",
            "mahmoudashraf/AI-Fabric-Framework",
            "main",
            "dev",
            "workspace-123",
            "ai-infrastructure-module/ai-fabric-runtime",
            "ai-infrastructure-module/ai-fabric-runtime/deploy/railway/Dockerfile",
            "ai-infrastructure-module/ai-infrastructure-generic-rest-connector",
            "ai-infrastructure-module/ai-infrastructure-generic-rest-connector/deploy/railway/Dockerfile",
            "runtime",
            "rest-connector",
            32,
            "https://ai-fabric.dev,http://localhost:8080",
            "https://*lovable*",
            true,
            false,
            60_000,
            Duration.ofSeconds(5),
            Duration.ofMinutes(10)
        );
    }

    private DeploymentEntity deployment() {
        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-123");
        deployment.setName("Sample Commerce Dev");
        deployment.setEnvironmentName("dev");
        deployment.setCustomerId("customer-default");
        deployment.setTenantId("tenant-default");
        deployment.setTemplateId("dev-openai-lucene");
        deployment.setStatus("DRAFT");
        deployment.setCreatedAt(Instant.parse("2026-03-29T00:00:00Z"));
        deployment.setUpdatedAt(Instant.parse("2026-03-29T00:00:00Z"));
        return deployment;
    }

    private DeploymentVersionEntity version() {
        DeploymentVersionEntity version = new DeploymentVersionEntity();
        version.setId("ver-123");
        version.setDeploymentId("dep-123");
        version.setSourceDraftId("drf-123");
        version.setVersionLabel("v1");
        version.setStatus("PUBLISHED");
        version.setConfigHash("hash-123");
        version.setAiFabricFrameworkVersion("0.5.0");
        version.setEntityConfigContractVersion("AI_ENTITY_CONFIG_V0_4");
        version.setReindexRequired(false);
        version.setActionsConfigJson("{\"actions\":[]}");
        version.setEntityConfigJson("{\"ai-config\":{\"vector-dimensions\":512},\"ai-entities\":{}}");
        version.setRoutingConfigJson("{\"connector\":{},\"actions\":{}}");
        version.setProviderConfigJson("{\"llmProvider\":\"openai\",\"embeddingProvider\":\"openai\"}");
        version.setSecurityConfigJson("{\"authzMode\":\"REMOTE_HTTP\",\"authzBaseUrl\":\"https://customer.example\"}");
        version.setActionsArtifactYaml("actions: []");
        version.setEntityArtifactYaml("ai-entities: {}");
        version.setRoutingArtifactYaml("actions: {}");
        version.setManifestJson("""
            {
              "aiFabricFrameworkVersion": "0.5.0",
              "entityConfigContractVersion": "AI_ENTITY_CONFIG_V0_4",
              "entityConfigHash": "entity-hash-123"
            }
            """);
        version.setPublishedAt(Instant.parse("2026-03-29T00:00:00Z"));
        return version;
    }

    private PlatformManagedProductServiceEntity productService(String id) {
        PlatformManagedProductServiceEntity entity = new PlatformManagedProductServiceEntity();
        entity.setId(id);
        entity.setServiceKind("SHOPIFY_BRIDGE_SERVICE");
        entity.setSecretName("SHOPIFY_BRIDGE_SHARED_SECRET_PROD");
        entity.setCreatedAt(Instant.parse("2026-03-29T00:00:00Z"));
        entity.setUpdatedAt(Instant.parse("2026-03-29T00:00:00Z"));
        return entity;
    }
}
