package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.PlatformAuthProperties;
import com.ai.fabric.platform.backend.config.PlatformDeliveryProperties;
import com.ai.fabric.platform.backend.config.PlatformGitHubActionsProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentGitHubVerificationDispatchRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentGitHubVerificationDispatchSummary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeploymentGitHubVerificationServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void dispatchBuildsSignedVectorVerificationContextAndReturnsGitHubRun() throws Exception {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentReleaseRepository releaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentVersionRepository versionRepository = mock(DeploymentVersionRepository.class);
        DeploymentAccessService deploymentAccessService = mock(DeploymentAccessService.class);
        DeploymentSourceResolver deploymentSourceResolver = mock(DeploymentSourceResolver.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        PlatformAuditService platformAuditService = mock(PlatformAuditService.class);
        HttpClient httpClient = mock(HttpClient.class);

        PlatformAuthProperties authProperties = new PlatformAuthProperties(
            true,
            "X-PLATFORM-API-KEY",
            true,
            true,
            "platform_session",
            Duration.ofHours(12),
            true,
            "Strict",
            null,
            null,
            false,
            null,
            null,
            null
        );
        PlatformDeliveryProperties deliveryProperties = new PlatformDeliveryProperties(
            "https://platform.example.com",
            true,
            Duration.ofDays(1)
        );
        PlatformGitHubActionsProperties gitHubActionsProperties = new PlatformGitHubActionsProperties(
            "https://api.github.test",
            "deployment-verification.yml",
            Duration.ofMinutes(20),
            Duration.ofSeconds(2)
        );

        DeploymentGitHubVerificationService service = new DeploymentGitHubVerificationService(
            deploymentRepository,
            releaseRepository,
            versionRepository,
            deploymentAccessService,
            deploymentSourceResolver,
            platformSecretService,
            authProperties,
            deliveryProperties,
            gitHubActionsProperties,
            platformAuditService,
            objectMapper,
            httpClient
        );

        DeploymentEntity deployment = deployment("dep-123", "ver-123");
        DeploymentReleaseEntity release = release("dep-123", "rel-123", "ver-123");
        DeploymentVersionEntity version = version(
            "dep-123",
            "ver-123",
            """
                {"vectorStrategy":"qdrant"}
                """,
            """
                {"ai-entities":{"product":{"description":"Product"}}}
                """,
            """
                {"connector":{"upstream":{"base-url":"https://store.example.com"}}}
                """
        );

        when(deploymentRepository.findById("dep-123")).thenReturn(Optional.of(deployment));
        when(releaseRepository.findTopByDeploymentIdAndDeploymentVersionIdOrderByCreatedAtDesc("dep-123", "ver-123"))
            .thenReturn(Optional.of(release));
        when(versionRepository.findById("ver-123")).thenReturn(Optional.of(version));
        when(deploymentAccessService.requireDeploymentOperatorAccess(deployment)).thenReturn(deployment);
        when(deploymentSourceResolver.resolveRepository(deployment)).thenReturn("example/platform");
        when(deploymentSourceResolver.resolveBranch(deployment)).thenReturn("main");
        when(platformSecretService.resolveSecret("GITHUB_ACTIONS_TOKEN")).thenReturn("github-token");
        when(platformSecretService.resolveSecret("PLATFORM_ARTIFACT_SIGNING_KEY")).thenReturn("artifact-signing-key");
        when(platformSecretService.resolveSecret("PLATFORM_OPERATOR_API_KEY")).thenReturn("operator-secret");
        when(platformSecretService.resolveSecret("PLATFORM_ADMIN_API_KEY")).thenReturn(null);
        when(platformSecretService.resolveSecret("CONNECTOR_API_KEY")).thenReturn("connector-secret");
        when(platformSecretService.resolveSecret("APP_ADMIN_API_KEY")).thenReturn("admin-secret");

        HttpResponse<String> dispatchResponse = mock(HttpResponse.class);
        when(dispatchResponse.statusCode()).thenReturn(204);

        Instant now = Instant.now();
        HttpResponse<String> runsResponse = mock(HttpResponse.class);
        when(runsResponse.statusCode()).thenReturn(200);
        when(runsResponse.body()).thenReturn("""
            {
              "workflow_runs": [
                {
                  "id": 401,
                  "path": ".github/workflows/deployment-verification.yml",
                  "display_title": "Deployment verify dep-123 / rel-123 / vector",
                  "status": "queued",
                  "conclusion": null,
                  "html_url": "https://github.com/example/platform/actions/runs/401",
                  "head_branch": "main",
                  "event": "workflow_dispatch",
                  "created_at": "%s",
                  "updated_at": "%s"
                }
              ]
            }
            """.formatted(now, now.plusSeconds(2)));

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(dispatchResponse, runsResponse);

        DeploymentGitHubVerificationDispatchSummary summary = service.dispatch(
            "dep-123",
            new DeploymentGitHubVerificationDispatchRequest("vector", true)
        );

        assertThat(summary.deploymentId()).isEqualTo("dep-123");
        assertThat(summary.releaseId()).isEqualTo("rel-123");
        assertThat(summary.deploymentVersionId()).isEqualTo("ver-123");
        assertThat(summary.profile()).isEqualTo("vector");
        assertThat(summary.verifyWrite()).isTrue();
        assertThat(summary.run()).isNotNull();
        assertThat(summary.run().runId()).isEqualTo(401L);
        assertThat(summary.run().htmlUrl()).isEqualTo("https://github.com/example/platform/actions/runs/401");
    }

    @Test
    void dispatchRejectsWhenPlatformAuthUsesSessionOnly() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentReleaseRepository releaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentVersionRepository versionRepository = mock(DeploymentVersionRepository.class);
        DeploymentAccessService deploymentAccessService = mock(DeploymentAccessService.class);
        DeploymentSourceResolver deploymentSourceResolver = mock(DeploymentSourceResolver.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        PlatformAuditService platformAuditService = mock(PlatformAuditService.class);

        PlatformAuthProperties authProperties = new PlatformAuthProperties(
            true,
            "X-PLATFORM-API-KEY",
            false,
            true,
            "platform_session",
            Duration.ofHours(12),
            true,
            "Strict",
            null,
            null,
            false,
            null,
            null,
            null
        );

        DeploymentGitHubVerificationService service = new DeploymentGitHubVerificationService(
            deploymentRepository,
            releaseRepository,
            versionRepository,
            deploymentAccessService,
            deploymentSourceResolver,
            platformSecretService,
            authProperties,
            new PlatformDeliveryProperties("https://platform.example.com", true, Duration.ofDays(1)),
            new PlatformGitHubActionsProperties("https://api.github.test", "deployment-verification.yml", Duration.ofMinutes(20), Duration.ofSeconds(2)),
            platformAuditService,
            objectMapper,
            mock(HttpClient.class)
        );

        DeploymentEntity deployment = deployment("dep-123", "ver-123");
        DeploymentReleaseEntity release = release("dep-123", "rel-123", "ver-123");
        DeploymentVersionEntity version = version(
            "dep-123",
            "ver-123",
            "{\"vectorStrategy\":\"lucene\"}",
            "{\"ai-entities\":{\"product\":{}}}",
            "{\"connector\":{\"upstream\":{\"base-url\":\"https://store.example.com\"}}}"
        );

        when(deploymentRepository.findById("dep-123")).thenReturn(Optional.of(deployment));
        when(releaseRepository.findTopByDeploymentIdAndDeploymentVersionIdOrderByCreatedAtDesc("dep-123", "ver-123"))
            .thenReturn(Optional.of(release));
        when(versionRepository.findById("ver-123")).thenReturn(Optional.of(version));
        when(deploymentAccessService.requireDeploymentOperatorAccess(deployment)).thenReturn(deployment);

        assertThatThrownBy(() -> service.dispatch("dep-123", null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("platform API-key auth");
    }

    @Test
    void dispatchRejectsWhenPlatformApiKeyIsMissingForAutomation() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentReleaseRepository releaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentVersionRepository versionRepository = mock(DeploymentVersionRepository.class);
        DeploymentAccessService deploymentAccessService = mock(DeploymentAccessService.class);
        DeploymentSourceResolver deploymentSourceResolver = mock(DeploymentSourceResolver.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        PlatformAuditService platformAuditService = mock(PlatformAuditService.class);

        PlatformAuthProperties authProperties = new PlatformAuthProperties(
            true,
            "X-PLATFORM-API-KEY",
            true,
            true,
            "platform_session",
            Duration.ofHours(12),
            true,
            "Strict",
            null,
            null,
            false,
            null,
            null,
            null
        );

        DeploymentGitHubVerificationService service = new DeploymentGitHubVerificationService(
            deploymentRepository,
            releaseRepository,
            versionRepository,
            deploymentAccessService,
            deploymentSourceResolver,
            platformSecretService,
            authProperties,
            new PlatformDeliveryProperties("https://platform.example.com", true, Duration.ofDays(1)),
            new PlatformGitHubActionsProperties("https://api.github.test", "deployment-verification.yml", Duration.ofMinutes(20), Duration.ofSeconds(2)),
            platformAuditService,
            objectMapper,
            mock(HttpClient.class)
        );

        DeploymentEntity deployment = deployment("dep-123", "ver-123");
        DeploymentReleaseEntity release = release("dep-123", "rel-123", "ver-123");
        DeploymentVersionEntity version = version(
            "dep-123",
            "ver-123",
            "{\"vectorStrategy\":\"lucene\"}",
            "{\"ai-entities\":{\"product\":{}}}",
            "{\"connector\":{\"upstream\":{\"base-url\":\"https://store.example.com\"}}}"
        );

        when(deploymentRepository.findById("dep-123")).thenReturn(Optional.of(deployment));
        when(releaseRepository.findTopByDeploymentIdAndDeploymentVersionIdOrderByCreatedAtDesc("dep-123", "ver-123"))
            .thenReturn(Optional.of(release));
        when(versionRepository.findById("ver-123")).thenReturn(Optional.of(version));
        when(deploymentAccessService.requireDeploymentOperatorAccess(deployment)).thenReturn(deployment);

        assertThatThrownBy(() -> service.dispatch("dep-123", null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("PLATFORM_OPERATOR_API_KEY")
            .hasMessageContaining("PLATFORM_ADMIN_API_KEY");
    }

    private DeploymentEntity deployment(String deploymentId, String activeVersionId) {
        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId(deploymentId);
        deployment.setName("GitHub Verification Smoke");
        deployment.setTemplateId("openai-lucene");
        deployment.setEnvironmentName("dev");
        deployment.setStatus("ACTIVE");
        deployment.setRuntimeBaseUrl("https://runtime.example.com");
        deployment.setConnectorBaseUrl("https://connector.example.com");
        deployment.setActiveVersionId(activeVersionId);
        deployment.setCreatedAt(Instant.now());
        deployment.setUpdatedAt(Instant.now());
        return deployment;
    }

    private DeploymentReleaseEntity release(String deploymentId, String releaseId, String versionId) {
        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setId(releaseId);
        release.setDeploymentId(deploymentId);
        release.setDeploymentVersionId(versionId);
        release.setStatus("APPLIED_VERIFIED");
        release.setVerificationStatus("PASSED");
        release.setProvisioningStatus("COMPLETED");
        release.setProvisioningTarget("RAILWAY_API");
        release.setProvisioningDetailsJson("""
            {
              "artifactUrls": {
                "entities": "https://platform.example.com/entities.yml",
                "prompts": "https://platform.example.com/prompts.json"
              }
            }
            """);
        release.setCreatedAt(Instant.parse("2026-04-01T10:00:00Z"));
        release.setUpdatedAt(Instant.parse("2026-04-01T10:00:00Z"));
        release.setAppliedAt(Instant.parse("2026-04-01T10:00:00Z"));
        return release;
    }

    private DeploymentVersionEntity version(String deploymentId,
                                            String versionId,
                                            String providerConfigJson,
                                            String entityConfigJson,
                                            String routingConfigJson) {
        DeploymentVersionEntity version = new DeploymentVersionEntity();
        version.setId(versionId);
        version.setDeploymentId(deploymentId);
        version.setSourceDraftId("draft-123");
        version.setVersionLabel("v1");
        version.setStatus("PUBLISHED");
        version.setConfigHash("hash");
        version.setReindexRequired(false);
        version.setProviderConfigJson(providerConfigJson);
        version.setEntityConfigJson(entityConfigJson);
        version.setRoutingConfigJson(routingConfigJson);
        version.setSecurityConfigJson("{}");
        version.setPromptConfigJson("{}");
        version.setActionsConfigJson("{}");
        version.setPublishedAt(Instant.parse("2026-04-01T09:00:00Z"));
        return version;
    }
}
