package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentDraftEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentServiceConfigIssueSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentServiceConfigModelSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentServiceConfigSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSourceSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTemplateSummary;
import com.ai.fabric.platform.backend.deployment.model.DraftValidationResponse;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeploymentServiceConfigModelServiceTest {

    private final DeploymentDraftValidationService deploymentDraftValidationService = mock(DeploymentDraftValidationService.class);
    private final PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
    private final DeploymentServiceConfigModelService service = new DeploymentServiceConfigModelService(
        deploymentDraftValidationService,
        platformSecretService,
        new ObjectMapper()
    );

    @Test
    void buildDoesNotRequireLiveBaseUrlsBeforeAnyVersionIsLive() {
        DeploymentEntity deployment = deployment(null, null, null);
        DeploymentServiceConfigModelSummary summary = service.build(
            deployment,
            draft(),
            publishedVersion(),
            template(),
            source()
        );

        DeploymentServiceConfigSummary runtime = service(summary, "runtime");
        DeploymentServiceConfigSummary restConnector = service(summary, "restConnector");

        assertThat(requiredMissingIssuePaths(runtime)).doesNotContain("runtime.baseUrl", "runtime.connectorBaseUrl");
        assertThat(requiredMissingIssuePaths(restConnector)).doesNotContain("rest.baseUrl", "rest.runtimeProxyBaseUrl");
    }

    @Test
    void buildRequiresLiveBaseUrlsOnceDeploymentHasActiveVersion() {
        DeploymentEntity deployment = deployment("ver-live", null, null);
        DeploymentServiceConfigModelSummary summary = service.build(
            deployment,
            draft(),
            publishedVersion(),
            template(),
            source()
        );

        DeploymentServiceConfigSummary runtime = service(summary, "runtime");
        DeploymentServiceConfigSummary restConnector = service(summary, "restConnector");

        assertThat(runtime.status()).isEqualTo("BLOCKED");
        assertThat(restConnector.status()).isEqualTo("BLOCKED");
        assertThat(issuePaths(runtime)).contains("runtime.baseUrl", "runtime.connectorBaseUrl");
        assertThat(issuePaths(restConnector)).contains("rest.baseUrl", "rest.runtimeProxyBaseUrl");
    }

    private DeploymentEntity deployment(String activeVersionId, String runtimeBaseUrl, String connectorBaseUrl) {
        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-123");
        deployment.setName("Test deployment");
        deployment.setEnvironmentName("dev");
        deployment.setActiveVersionId(activeVersionId);
        deployment.setRuntimeBaseUrl(runtimeBaseUrl);
        deployment.setConnectorBaseUrl(connectorBaseUrl);
        when(deploymentDraftValidationService.validate(org.mockito.ArgumentMatchers.any())).thenReturn(new DraftValidationResponse(
            "drf-123",
            deployment.getId(),
            true,
            0,
            0,
            Instant.now(),
            List.of()
        ));
        when(platformSecretService.isSecretPresent(anyString())).thenReturn(false);
        return deployment;
    }

    private DeploymentDraftEntity draft() {
        DeploymentDraftEntity draft = new DeploymentDraftEntity();
        draft.setId("drf-123");
        draft.setRoutingConfigJson("""
            {
              "connector": {}
            }
            """);
        draft.setProviderConfigJson("""
            {
              "runtimeProfile": "runtime-managed",
              "connectorProfile": "connector-hosted"
            }
            """);
        draft.setSecurityConfigJson("""
            {
              "authzMode": "NONE",
              "adminApiKeyEnabled": false
            }
            """);
        return draft;
    }

    private DeploymentVersionEntity publishedVersion() {
        DeploymentVersionEntity version = new DeploymentVersionEntity();
        version.setId("ver-123");
        version.setVersionLabel("v1");
        return version;
    }

    private DeploymentTemplateSummary template() {
        return new DeploymentTemplateSummary(
            "tpl-123",
            "Template",
            "Template description",
            "openai",
            "openai",
            "qdrant",
            "runtime-managed",
            "connector-hosted",
            true,
            "MANAGED_CLOUD_CLUSTER",
            "summary"
        );
    }

    private DeploymentSourceSummary source() {
        return new DeploymentSourceSummary(
            "mahmoudashraf/AI-Fabric-Framework",
            "Platform-V4",
            null,
            null,
            false
        );
    }

    private DeploymentServiceConfigSummary service(DeploymentServiceConfigModelSummary summary, String key) {
        return summary.services().stream()
            .filter(item -> key.equals(item.key()))
            .findFirst()
            .orElseThrow();
    }

    private List<String> issuePaths(DeploymentServiceConfigSummary summary) {
        return summary.issues().stream().map(DeploymentServiceConfigIssueSummary::path).toList();
    }

    private List<String> requiredMissingIssuePaths(DeploymentServiceConfigSummary summary) {
        return summary.issues().stream()
            .filter(issue -> "REQUIRED_FIELD_MISSING".equals(issue.code()))
            .map(DeploymentServiceConfigIssueSummary::path)
            .toList();
    }
}
