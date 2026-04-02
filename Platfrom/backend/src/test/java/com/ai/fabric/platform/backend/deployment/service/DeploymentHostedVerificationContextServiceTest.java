package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.config.PlatformDeliveryProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentHostedVerificationContextSummary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeploymentHostedVerificationContextServiceTest {

    @Test
    void canonicalRolloutUsesCanonicalProfileEvenWhenOperatorRequestsVector() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentReleaseRepository releaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentVersionRepository versionRepository = mock(DeploymentVersionRepository.class);
        DeploymentAccessService accessService = mock(DeploymentAccessService.class);
        DeploymentVerificationRolloutService rolloutService = mock(DeploymentVerificationRolloutService.class);

        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-pinecone");
        deployment.setName("OpenAI Pinecone Verification");
        deployment.setEnvironmentName("dev");
        deployment.setActiveVersionId("ver-1");
        deployment.setRuntimeBaseUrl("https://runtime.example");
        deployment.setConnectorBaseUrl("https://connector.example");

        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setId("rel-1");
        release.setDeploymentId("dep-pinecone");
        release.setDeploymentVersionId("ver-1");
        release.setStatus("APPLIED");
        release.setVerificationStatus("PASSED");

        DeploymentVersionEntity version = new DeploymentVersionEntity();
        version.setId("ver-1");
        version.setDeploymentId("dep-pinecone");
        version.setProviderConfigJson("""
            {"vectorStrategy":"pinecone"}
            """);
        version.setEntityConfigJson("""
            {"ai-config":{"vector-dimensions":1536},"ai-entities":{"product":{},"policy":{},"review":{}}}
            """);
        version.setRoutingConfigJson("""
            {"connector":{"upstream":{"base-url":"https://store.example"}}}
            """);

        when(deploymentRepository.findById(eq("dep-pinecone"))).thenReturn(Optional.of(deployment));
        when(accessService.requireDeploymentOperatorAccess(eq(deployment))).thenReturn(deployment);
        when(releaseRepository.findTopByDeploymentIdAndDeploymentVersionIdOrderByCreatedAtDesc(eq("dep-pinecone"), eq("ver-1")))
            .thenReturn(Optional.of(release));
        when(versionRepository.findById(eq("ver-1"))).thenReturn(Optional.of(version));
        when(rolloutService.canonicalVerificationProfile(eq("dep-pinecone"))).thenReturn("ecommerce");

        DeploymentHostedVerificationContextService service = new DeploymentHostedVerificationContextService(
            deploymentRepository,
            releaseRepository,
            versionRepository,
            accessService,
            rolloutService,
            new PlatformDeliveryProperties("https://platform.example", true, Duration.ofHours(1)),
            new ObjectMapper()
        );

        DeploymentHostedVerificationContextSummary context = service.buildContextForOperator("dep-pinecone", "vector", false);

        assertThat(context.profile()).isEqualTo("ecommerce");
        assertThat(context.script()).isEqualTo("scripts/verify-ecommerce-deployment.sh");
        assertThat(context.env()).containsEntry("STORE_BASE_URL", "https://store.example");
        assertThat(context.env()).doesNotContainKey("EXPECTED_VECTOR_DB");
    }
}
