package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentProviderResourceHandleEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentSourceArtifactEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.model.RailwayArtifactUrlsSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayEnvVarSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayProvisioningPlanSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayProvisioningServicesSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayServicePlanSummary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentProviderResourceHandleRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CoolifyDeploymentProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void provisionsDockerImageApplicationAndPersistsResourceHandle() throws Exception {
        DeploymentTargetProfileRepository targetProfileRepository = mock(DeploymentTargetProfileRepository.class);
        DeploymentProviderResourceHandleRepository resourceHandleRepository = mock(DeploymentProviderResourceHandleRepository.class);
        DeploymentSourceArtifactService sourceArtifactService = mock(DeploymentSourceArtifactService.class);
        RailwayProvisioningPlanService railwayProvisioningPlanService = mock(RailwayProvisioningPlanService.class);
        CoolifyTargetProfileResolver targetProfileResolver = mock(CoolifyTargetProfileResolver.class);
        CoolifyApiClient coolifyApiClient = mock(CoolifyApiClient.class);

        DeploymentTargetProfileEntity profile = profile();
        DeploymentSourceArtifactEntity artifact = artifact();
        CoolifyConnection connection = new CoolifyConnection(
            "http://coolify.example",
            "mock-token",
            new CoolifyTargetProfileConfig(
                "http://coolify.example",
                "project",
                "staging",
                "env",
                "server",
                "destination",
                "runtime.example.test",
                "4.0.0",
                5,
                600,
                false,
                false,
                "8080",
                "/actuator/health",
                "8080"
            )
        );
        CoolifyApplicationSummary application = new CoolifyApplicationSummary(
            "app-uuid",
            "ai-fabric-runtime-dep-123",
            "http://dep-123.runtime.example.test",
            "running",
            "ghcr.io/example/runtime",
            "sha",
            objectMapper.readTree("{\"uuid\":\"app-uuid\",\"status\":\"running\"}")
        );

        when(targetProfileRepository.findById("dtp-coolify-staging")).thenReturn(Optional.of(profile));
        when(targetProfileResolver.requireConnection(profile)).thenReturn(connection);
        when(coolifyApiClient.health(connection)).thenReturn(objectMapper.readTree("{\"status\":\"ok\"}"));
        when(sourceArtifactService.require("dsa-123")).thenReturn(artifact);
        when(resourceHandleRepository.findFirstByDeploymentIdAndTargetProfileIdAndResourceKindOrderByUpdatedAtDesc(
            eq("dep-123"),
            eq("dtp-coolify-staging"),
            eq("APPLICATION")
        )).thenReturn(Optional.empty());
        when(coolifyApiClient.listApplications(connection)).thenReturn(List.of());
        when(coolifyApiClient.createDockerImageApplication(eq(connection), any())).thenReturn("app-uuid");
        when(coolifyApiClient.getApplication(connection, "app-uuid")).thenReturn(Optional.of(application));
        when(coolifyApiClient.updateEnvironmentVariables(eq(connection), eq("app-uuid"), any())).thenReturn(6);
        when(coolifyApiClient.start(connection, "app-uuid", true, true))
            .thenReturn(new CoolifyActionResponse("Deployment request queued.", "deploy-uuid", objectMapper.createObjectNode()));
        when(resourceHandleRepository.save(any(DeploymentProviderResourceHandleEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        CoolifyDeploymentProvider provider = new CoolifyDeploymentProvider(
            targetProfileRepository,
            resourceHandleRepository,
            sourceArtifactService,
            railwayProvisioningPlanService,
            targetProfileResolver,
            coolifyApiClient,
            objectMapper
        );
        DeploymentReleaseEntity release = release();

        ProvisioningResult result = provider.provision(deployment(), version(), release, ProvisioningProgressTracker.noop());

        assertThat(result.target()).isEqualTo("COOLIFY");
        assertThat(result.runtimeBaseUrl()).isEqualTo("http://dep-123.runtime.example.test");
        assertThat(result.detailsJson()).contains("providerResourceHandleId", "app-uuid", "dsa-123");
        ArgumentCaptor<CoolifyCreateDockerImageApplicationRequest> request =
            ArgumentCaptor.forClass(CoolifyCreateDockerImageApplicationRequest.class);
        verify(coolifyApiClient).createDockerImageApplication(eq(connection), request.capture());
        assertThat(request.getValue().domains()).isEqualTo("http://dep-123.runtime.example.test");
    }

    @Test
    void provisionsPublicGitApplicationFromRailwayPlan() throws Exception {
        DeploymentTargetProfileRepository targetProfileRepository = mock(DeploymentTargetProfileRepository.class);
        DeploymentProviderResourceHandleRepository resourceHandleRepository = mock(DeploymentProviderResourceHandleRepository.class);
        DeploymentSourceArtifactService sourceArtifactService = mock(DeploymentSourceArtifactService.class);
        RailwayProvisioningPlanService railwayProvisioningPlanService = mock(RailwayProvisioningPlanService.class);
        CoolifyTargetProfileResolver targetProfileResolver = mock(CoolifyTargetProfileResolver.class);
        CoolifyApiClient coolifyApiClient = mock(CoolifyApiClient.class);

        DeploymentTargetProfileEntity profile = profile();
        profile.setSourceStrategy("GIT_SOURCE");
        CoolifyConnection connection = new CoolifyConnection(
            "http://coolify.example",
            "mock-token",
            new CoolifyTargetProfileConfig(
                "http://coolify.example",
                "project",
                "staging",
                "env",
                "server",
                "destination",
                "runtime.example.test",
                "4.0.0",
                5,
                600,
                false,
                false,
                "8080",
                "/actuator/health",
                "8080"
            )
        );
        CoolifyApplicationSummary application = new CoolifyApplicationSummary(
            "app-uuid",
            "runtime-dep-123",
            "http://dep-123.runtime.example.test",
            "running:healthy",
            null,
            null,
            objectMapper.readTree("{\"uuid\":\"app-uuid\",\"status\":\"running:healthy\"}")
        );

        when(targetProfileRepository.findById("dtp-coolify-staging")).thenReturn(Optional.of(profile));
        when(targetProfileResolver.requireConnection(profile)).thenReturn(connection);
        when(coolifyApiClient.health(connection)).thenReturn(objectMapper.readTree("{\"status\":\"ok\"}"));
        when(railwayProvisioningPlanService.buildPlan(any(), any())).thenReturn(railwayPlan());
        when(resourceHandleRepository.findFirstByDeploymentIdAndTargetProfileIdAndResourceKindOrderByUpdatedAtDesc(
            eq("dep-123"),
            eq("dtp-coolify-staging"),
            eq("APPLICATION")
        )).thenReturn(Optional.empty());
        when(coolifyApiClient.listApplications(connection)).thenReturn(List.of());
        when(coolifyApiClient.createPublicApplication(eq(connection), any())).thenReturn("app-uuid");
        when(coolifyApiClient.getApplication(connection, "app-uuid")).thenReturn(Optional.of(application));
        when(coolifyApiClient.updateEnvironmentVariables(eq(connection), eq("app-uuid"), any())).thenReturn(10);
        when(coolifyApiClient.start(connection, "app-uuid", true, true))
            .thenReturn(new CoolifyActionResponse("Deployment request queued.", "deploy-uuid", objectMapper.createObjectNode()));
        when(resourceHandleRepository.save(any(DeploymentProviderResourceHandleEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        CoolifyDeploymentProvider provider = new CoolifyDeploymentProvider(
            targetProfileRepository,
            resourceHandleRepository,
            sourceArtifactService,
            railwayProvisioningPlanService,
            targetProfileResolver,
            coolifyApiClient,
            objectMapper
        );
        DeploymentReleaseEntity release = release();
        release.setSourceArtifactId(null);

        ProvisioningResult result = provider.provision(deployment(), version(), release, ProvisioningProgressTracker.noop());

        assertThat(result.target()).isEqualTo("COOLIFY");
        assertThat(result.detailsJson()).contains(
            "GIT_SOURCE",
            "https://github.com/mahmoudashraf/AI-Fabric-Framework.git",
            "Platform-V8",
            "/ai-infrastructure-module/ai-fabric-runtime/deploy/railway/Dockerfile"
        );
        ArgumentCaptor<CoolifyCreatePublicApplicationRequest> request =
            ArgumentCaptor.forClass(CoolifyCreatePublicApplicationRequest.class);
        verify(coolifyApiClient).createPublicApplication(eq(connection), request.capture());
        assertThat(request.getValue().gitRepository()).isEqualTo("https://github.com/mahmoudashraf/AI-Fabric-Framework.git");
        assertThat(request.getValue().gitBranch()).isEqualTo("Platform-V8");
        assertThat(request.getValue().buildPack()).isEqualTo("dockerfile");
        assertThat(request.getValue().baseDirectory()).isEqualTo("/");
        assertThat(request.getValue().dockerfileLocation()).isEqualTo("/ai-infrastructure-module/ai-fabric-runtime/deploy/railway/Dockerfile");
        assertThat(request.getValue().autoDeployEnabled()).isFalse();
        verifyNoInteractions(sourceArtifactService);
    }

    @Test
    void statusRedactsRawCoolifySecrets() throws Exception {
        DeploymentTargetProfileRepository targetProfileRepository = mock(DeploymentTargetProfileRepository.class);
        DeploymentProviderResourceHandleRepository resourceHandleRepository = mock(DeploymentProviderResourceHandleRepository.class);
        DeploymentSourceArtifactService sourceArtifactService = mock(DeploymentSourceArtifactService.class);
        RailwayProvisioningPlanService railwayProvisioningPlanService = mock(RailwayProvisioningPlanService.class);
        CoolifyTargetProfileResolver targetProfileResolver = mock(CoolifyTargetProfileResolver.class);
        CoolifyApiClient coolifyApiClient = mock(CoolifyApiClient.class);

        DeploymentTargetProfileEntity profile = profile();
        CoolifyConnection connection = new CoolifyConnection(
            "http://coolify.example",
            "mock-token",
            new CoolifyTargetProfileConfig(
                "http://coolify.example",
                "project",
                "staging",
                "env",
                "server",
                "destination",
                "runtime.example.test",
                "4.0.0",
                5,
                600,
                false,
                false,
                "8080",
                "/actuator/health",
                "8080"
            )
        );
        CoolifyApplicationSummary application = new CoolifyApplicationSummary(
            "app-uuid",
            "runtime-dep-123",
            "http://dep-123.runtime.example.test",
            "running:healthy",
            null,
            null,
            objectMapper.readTree("""
                {
                  "uuid": "app-uuid",
                  "name": "runtime-dep-123",
                  "status": "running:healthy",
                  "fqdn": "http://dep-123.runtime.example.test",
                  "git_branch": "Platform-V8",
                  "manual_webhook_secret_github": "should-not-leak",
                  "destination": {
                    "uuid": "destination-uuid",
                    "network": "coolify",
                    "server": {
                      "uuid": "server-uuid",
                      "name": "localhost",
                      "settings": {
                        "sentinel_token": "should-not-leak-either"
                      }
                    }
                  }
                }
                """)
        );
        DeploymentProviderResourceHandleEntity handle = new DeploymentProviderResourceHandleEntity();
        handle.setId("dprh-123");
        handle.setProviderType(DeploymentProviderType.COOLIFY);
        handle.setProviderResourceUuid("app-uuid");
        handle.setTargetProfileId("dtp-coolify-staging");

        when(targetProfileRepository.findById("dtp-coolify-staging")).thenReturn(Optional.of(profile));
        when(targetProfileResolver.requireConnection(profile)).thenReturn(connection);
        when(coolifyApiClient.getApplication(connection, "app-uuid")).thenReturn(Optional.of(application));

        CoolifyDeploymentProvider provider = new CoolifyDeploymentProvider(
            targetProfileRepository,
            resourceHandleRepository,
            sourceArtifactService,
            railwayProvisioningPlanService,
            targetProfileResolver,
            coolifyApiClient,
            objectMapper
        );

        var summary = provider.status(handle);

        assertThat(summary.status()).isEqualTo("RUNNING_HEALTHY");
        assertThat(summary.details().path("git_branch").asText()).isEqualTo("Platform-V8");
        assertThat(summary.details().path("destinationUuid").asText()).isEqualTo("destination-uuid");
        assertThat(summary.details().path("serverUuid").asText()).isEqualTo("server-uuid");
        assertThat(summary.details().toString())
            .doesNotContain("should-not-leak")
            .doesNotContain("manual_webhook_secret")
            .doesNotContain("sentinel_token");
    }

    private DeploymentEntity deployment() {
        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-123");
        deployment.setName("Demo");
        deployment.setEnvironmentName("staging");
        deployment.setTemplateId("template");
        deployment.setStatus("VERSION_PUBLISHED");
        deployment.setCustomerId("customer");
        deployment.setTenantId("tenant");
        deployment.setCreatedAt(Instant.parse("2026-05-01T00:00:00Z"));
        deployment.setUpdatedAt(Instant.parse("2026-05-01T00:00:00Z"));
        return deployment;
    }

    private DeploymentVersionEntity version() {
        DeploymentVersionEntity version = new DeploymentVersionEntity();
        version.setId("ver-123");
        version.setDeploymentId("dep-123");
        version.setSourceDraftId("draft");
        version.setVersionLabel("v1");
        version.setStatus("PUBLISHED");
        version.setConfigHash("hash");
        version.setProviderConfigJson("{}");
        version.setSecurityConfigJson("{}");
        version.setEntityConfigJson("{}");
        version.setActionsConfigJson("{}");
        version.setRoutingConfigJson("{}");
        version.setPromptConfigJson("{}");
        version.setActionsArtifactYaml("");
        version.setEntityArtifactYaml("");
        version.setRoutingArtifactYaml("");
        version.setManifestJson("{}");
        version.setPublishedAt(Instant.parse("2026-05-01T00:00:00Z"));
        return version;
    }

    private DeploymentReleaseEntity release() {
        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setId("rel-123");
        release.setDeploymentId("dep-123");
        release.setDeploymentVersionId("ver-123");
        release.setTargetProfileId("dtp-coolify-staging");
        release.setProviderType(DeploymentProviderType.COOLIFY);
        release.setProvisioningTarget("COOLIFY");
        release.setSourceArtifactId("dsa-123");
        return release;
    }

    private DeploymentTargetProfileEntity profile() {
        DeploymentTargetProfileEntity profile = new DeploymentTargetProfileEntity();
        profile.setId("dtp-coolify-staging");
        profile.setName("Coolify staging");
        profile.setProviderType(DeploymentProviderType.COOLIFY);
        profile.setEnvironmentName("staging");
        profile.setActive(true);
        profile.setSourceStrategy("IMAGE_SOURCE");
        profile.setProviderConfigJson("{}");
        profile.setNetworkPolicyJson("{}");
        profile.setResourceDefaultsJson("{}");
        profile.setCreatedAt(Instant.parse("2026-05-01T00:00:00Z"));
        profile.setUpdatedAt(Instant.parse("2026-05-01T00:00:00Z"));
        return profile;
    }

    private DeploymentSourceArtifactEntity artifact() {
        DeploymentSourceArtifactEntity artifact = new DeploymentSourceArtifactEntity();
        artifact.setId("dsa-123");
        artifact.setServiceName("ai-fabric-runtime");
        artifact.setArtifactType("DOCKER_IMAGE");
        artifact.setImageRepository("ghcr.io/example/runtime");
        artifact.setImageTag("sha");
        artifact.setCreatedAt(Instant.parse("2026-05-01T00:00:00Z"));
        return artifact;
    }

    private RailwayProvisioningPlanSummary railwayPlan() {
        RailwayServicePlanSummary runtime = new RailwayServicePlanSummary(
            "runtime-dep-123",
            null,
            "ai-infrastructure-module/ai-fabric-runtime/deploy/railway/Dockerfile",
            "https://runtime-dep-123.placeholder.local",
            List.of(new RailwayEnvVarSummary("AI_CONFIG_DEFAULT_FILE", "https://artifacts.example/entities.yaml"))
        );
        return new RailwayProvisioningPlanSummary(
            "dep-123",
            "Demo",
            "staging",
            "template",
            "ver-123",
            "v1",
            "hash",
            "api",
            "demo-staging",
            "mahmoudashraf/AI-Fabric-Framework",
            "Platform-V8",
            null,
            "REMOTE_CONFIG_BUNDLES",
            new RailwayArtifactUrlsSummary(
                "https://artifacts.example/actions.yaml",
                "https://artifacts.example/entities.yaml",
                "https://artifacts.example/routing.yaml",
                "https://artifacts.example/prompts.yaml",
                "https://artifacts.example/manifest.json"
            ),
            new RailwayProvisioningServicesSummary(runtime, null),
            List.of()
        );
    }
}
