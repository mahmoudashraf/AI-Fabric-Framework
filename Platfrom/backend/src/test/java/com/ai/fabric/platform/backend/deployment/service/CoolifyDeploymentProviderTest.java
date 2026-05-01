package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentProviderResourceHandleEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentSourceArtifactEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CoolifyDeploymentProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void provisionsDockerImageApplicationAndPersistsResourceHandle() throws Exception {
        DeploymentTargetProfileRepository targetProfileRepository = mock(DeploymentTargetProfileRepository.class);
        DeploymentProviderResourceHandleRepository resourceHandleRepository = mock(DeploymentProviderResourceHandleRepository.class);
        DeploymentSourceArtifactService sourceArtifactService = mock(DeploymentSourceArtifactService.class);
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
}
