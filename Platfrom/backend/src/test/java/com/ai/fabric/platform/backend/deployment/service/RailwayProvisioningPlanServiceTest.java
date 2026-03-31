package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.config.PlatformDeliveryProperties;
import com.ai.fabric.platform.backend.config.PlatformProvisioningProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentArtifactBundleSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayEnvVarSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayProvisioningPlanSummary;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RailwayProvisioningPlanServiceTest {

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
            .containsEntry("AI_FABRIC_RUNTIME_DEV_DEFAULTS_ENABLED", "false")
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
    void buildPlanAddsRuntimeAdminKeyEnvWhenEnabledAndSecretExists() {
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
            .containsEntry("APP_ADMIN_API_KEY", "${secret:APP_ADMIN_API_KEY}")
            .containsEntry("APP_ADMIN_API_KEY_HEADER", "X-ADMIN-API-KEY");
        assertThat(connectorEnv)
            .containsEntry("APP_ADMIN_API_KEY", "${secret:APP_ADMIN_API_KEY}")
            .containsEntry("APP_ADMIN_API_KEY_HEADER", "X-ADMIN-API-KEY")
            .containsEntry("REST_CONNECTOR_RUNTIME_PROXY_API_KEY", "${secret:APP_ADMIN_API_KEY}")
            .containsEntry("REST_CONNECTOR_RUNTIME_PROXY_API_KEY_HEADER", "X-ADMIN-API-KEY");
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
    void buildPlanAddsCuratedPackEnvWhenProviderConfigSpecifiesPack() {
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
              "curatedModuleId": "commerce",
              "curatedPackId": "commerce"
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
            .containsEntry("AI_FABRIC_RUNTIME_DEV_DEFAULTS_ENABLED", "false")
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
    void buildPlanCompilesGeminiRestAndMilvusSettingsIntoLiveEnv() {
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
        when(platformSecretService.isSecretPresent("MILVUS_USERNAME")).thenReturn(true);
        when(platformSecretService.isSecretPresent("MILVUS_PASSWORD")).thenReturn(true);

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
              "llmProvider": "gemini",
              "embeddingProvider": "rest",
              "vectorStrategy": "milvus",
              "runtimeProfile": "runtime-managed",
              "connectorProfile": "connector-hosted",
              "geminiModel": "gemini-1.5-flash",
              "restEmbeddingBaseUrl": "https://embedder.example",
              "restEmbeddingEndpoint": "/embed",
              "restEmbeddingBatchEndpoint": "/embed/batch",
              "restEmbeddingModel": "custom-embedder",
              "restEmbeddingTimeoutMs": "45000",
              "milvusHost": "milvus.internal",
              "milvusPort": "19530",
              "milvusDatabaseName": "customer",
              "milvusSecure": true,
              "milvusFlushOnWrite": true
            }
            """);

        RailwayProvisioningPlanSummary plan = service.buildPlan(deployment(), version);
        Map<String, String> runtimeEnv = envMap(plan.services().runtime().env());

        assertThat(runtimeEnv)
            .containsEntry("AI_PROVIDERS_LLM_PROVIDER", "gemini")
            .containsEntry("AI_PROVIDERS_EMBEDDING_PROVIDER", "rest")
            .containsEntry("AI_VECTOR_DB_TYPE", "milvus")
            .containsEntry("AI_PROVIDERS_GEMINI_ENABLED", "true")
            .containsEntry("AI_PROVIDERS_GEMINI_API_KEY", "${secret:GEMINI_API_KEY}")
            .containsEntry("AI_PROVIDERS_GEMINI_MODEL", "gemini-1.5-flash")
            .containsEntry("AI_PROVIDERS_REST_ENABLED", "true")
            .containsEntry("AI_PROVIDERS_REST_BASE_URL", "https://embedder.example")
            .containsEntry("AI_PROVIDERS_REST_ENDPOINT", "/embed")
            .containsEntry("AI_PROVIDERS_REST_BATCH_ENDPOINT", "/embed/batch")
            .containsEntry("AI_PROVIDERS_REST_MODEL", "custom-embedder")
            .containsEntry("AI_PROVIDERS_REST_TIMEOUT", "45000")
            .containsEntry("AI_PROVIDERS_MILVUS_ENABLED", "true")
            .containsEntry("AI_PROVIDERS_MILVUS_HOST", "milvus.internal")
            .containsEntry("AI_PROVIDERS_MILVUS_PORT", "19530")
            .containsEntry("AI_PROVIDERS_MILVUS_DATABASE_NAME", "customer")
            .containsEntry("AI_PROVIDERS_MILVUS_SECURE", "true")
            .containsEntry("AI_PROVIDERS_MILVUS_FLUSH_ON_WRITE", "true")
            .containsEntry("AI_PROVIDERS_MILVUS_USERNAME", "${secret:MILVUS_USERNAME}")
            .containsEntry("AI_PROVIDERS_MILVUS_PASSWORD", "${secret:MILVUS_PASSWORD}")
            .containsEntry("OPENAI_ENABLED", "false");
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
        version.setReindexRequired(false);
        version.setActionsConfigJson("{\"actions\":[]}");
        version.setEntityConfigJson("{\"ai-config\":{\"vector-dimensions\":512},\"ai-entities\":{}}");
        version.setRoutingConfigJson("{\"connector\":{},\"actions\":{}}");
        version.setProviderConfigJson("{\"llmProvider\":\"openai\",\"embeddingProvider\":\"openai\"}");
        version.setSecurityConfigJson("{\"authzMode\":\"REMOTE_HTTP\",\"authzBaseUrl\":\"https://customer.example\"}");
        version.setActionsArtifactYaml("actions: []");
        version.setEntityArtifactYaml("ai-entities: {}");
        version.setRoutingArtifactYaml("actions: {}");
        version.setManifestJson("{}");
        version.setPublishedAt(Instant.parse("2026-03-29T00:00:00Z"));
        return version;
    }
}
