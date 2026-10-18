package com.ai.fabric.platform.backend.deployment.web;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.service.DeploymentTargetContextService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeploymentTargetContextControllerTest {

    private final DeploymentTargetContextService service = mock(DeploymentTargetContextService.class);
    private final DeploymentTargetContextController controller =
        new DeploymentTargetContextController(service, new ObjectMapper());

    @Test
    void projectsCapabilitiesFromTheSelectedReleaseVersion() {
        DeploymentVersionEntity version = version(
            "{\"datasets\":["
                + "{\"datasetId\":\"stock\",\"ingestionMode\":\"EXTERNAL_SYNC_HTTP\"},"
                + "{\"datasetId\":\"policies\",\"ingestionMode\":\"EXTERNAL_DOCUMENT_STORAGE\"}]}",
            "{\"conversationHandover\":{\"enabled\":true}}"
        );
        when(service.resolve("dep-1", "stage", "rel-1", false)).thenReturn(context(version));

        DeploymentTargetContextController.Summary summary = controller.context("dep-1", "stage", "rel-1");

        assertThat(summary.documentDatasetIds()).containsExactly("policies");
        assertThat(summary.externalIntegrationConfigured()).isTrue();
        assertThat(summary.conversationHandoverConfigured()).isTrue();
        assertThat(summary.runtimeBaseUrl()).isEqualTo("https://runtime.example");
    }

    @Test
    void invalidSelectedReleaseCapabilityConfigurationFailsClosed() {
        DeploymentVersionEntity version = version("{broken", "{}");
        when(service.resolve("dep-1", "stage", "rel-1", false)).thenReturn(context(version));

        assertThatThrownBy(() -> controller.context("dep-1", "stage", "rel-1"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("capability configuration is invalid");
    }

    private DeploymentTargetContextService.Context context(DeploymentVersionEntity version) {
        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-1");
        DeploymentTargetProfileEntity target = new DeploymentTargetProfileEntity();
        target.setId("stage");
        target.setEnvironmentName("customer-staging");
        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setId("rel-1");
        release.setStatus("APPLIED_VERIFIED");
        return new DeploymentTargetContextService.Context(
            deployment,
            target,
            release,
            version,
            "https://runtime.example",
            "https://connector.example"
        );
    }

    private DeploymentVersionEntity version(String datasetConfig, String behaviorConfig) {
        DeploymentVersionEntity version = new DeploymentVersionEntity();
        version.setId("ver-1");
        version.setConfigHash("config-hash");
        version.setMarketplaceDatasetConfigJson(datasetConfig);
        version.setBehaviorConfigJson(behaviorConfig);
        return version;
    }
}
