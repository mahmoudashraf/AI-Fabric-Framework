package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.config.PlatformProvisioningProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentArtifactBundleSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayEnvVarSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayProvisioningPlanSummary;
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
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-actions.yml",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-entity-config.yml",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/actions-routing.yml",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/deployment-manifest.json"
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
            "ai-infrastructure-module/ai-infrastructure-generic-rest-connector",
            "runtime",
            "rest-connector",
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
            artifactService,
            new ObjectMapper()
        );

        RailwayProvisioningPlanSummary plan = service.buildPlan(deployment(), version());

        Map<String, String> runtimeEnv = envMap(plan.services().runtime().env());
        Map<String, String> connectorEnv = envMap(plan.services().restConnector().env());

        assertThat(runtimeEnv)
            .containsEntry("AI_ACTIONS_CATALOG_PATH", "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-actions.yml")
            .containsEntry("AI_CONFIG_DEFAULT_FILE", "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-entity-config.yml")
            .containsEntry("OPENAI_ENABLED", "true")
            .containsEntry("AI_FABRIC_RUNTIME_DEV_DEFAULTS_ENABLED", "false")
            .containsEntry("CORS_ALLOWED_ORIGINS", "https://ai-fabric.dev,http://localhost:8080")
            .containsEntry("CORS_ALLOWED_ORIGIN_PATTERNS", "https://*lovable*")
            .containsEntry("CORS_ALLOW_CREDENTIALS", "true")
            .doesNotContainKey("AI_ENTITY_CONFIG_PATH");

        assertThat(connectorEnv)
            .containsEntry("REST_CONNECTOR_ROUTING_CONFIG_LOCATION", "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/actions-routing.yml")
            .containsEntry("REST_CONNECTOR_RUNTIME_PROXY_ENABLED", "true")
            .containsEntry("REST_CONNECTOR_RUNTIME_PROXY_TIMEOUT_MS", "60000")
            .containsEntry("CORS_ALLOW_CREDENTIALS", "true")
            .doesNotContainKey("REST_CONNECTOR_ROUTING_CONFIG_PATH");
    }

    private Map<String, String> envMap(java.util.List<RailwayEnvVarSummary> env) {
        return env.stream().collect(Collectors.toMap(RailwayEnvVarSummary::key, RailwayEnvVarSummary::value));
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
