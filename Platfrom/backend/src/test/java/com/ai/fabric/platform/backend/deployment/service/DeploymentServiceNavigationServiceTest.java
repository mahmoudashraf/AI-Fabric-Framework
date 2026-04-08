package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.config.PlatformProvisioningProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentNavigationRelationshipSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentNavigationSurfaceSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentServiceConfigModelSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSourceSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTemplateSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeploymentServiceNavigationServiceTest {

    @Test
    void buildUsesRuntimeBackedConnectorAdminSurfaceWhenConnectorIsInternal() {
        DeploymentServiceConfigModelService configModelService = mock(DeploymentServiceConfigModelService.class);
        DeploymentSourceResolver sourceResolver = mock(DeploymentSourceResolver.class);
        RailwayProvisioningPlanService railwayProvisioningPlanService = mock(RailwayProvisioningPlanService.class);
        PlatformProvisioningProperties provisioningProperties = new PlatformProvisioningProperties(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            0,
            null,
            null,
            false,
            false,
            0,
            null,
            null
        );
        DeploymentServiceNavigationService service = new DeploymentServiceNavigationService(
            configModelService,
            sourceResolver,
            railwayProvisioningPlanService,
            provisioningProperties,
            new ObjectMapper()
        );

        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-123");
        deployment.setName("Secure storefront");
        deployment.setEnvironmentName("dev");
        deployment.setTemplateId("tpl-1");
        deployment.setRuntimeBaseUrl("https://runtime.example");
        deployment.setConnectorBaseUrl("https://connector.internal");

        DeploymentSourceSummary source = new DeploymentSourceSummary(
            "org/repo",
            "main",
            null,
            null,
            false
        );
        DeploymentTemplateSummary template = new DeploymentTemplateSummary(
            "tpl-1",
            "Secure storefront",
            "test",
            "openai",
            "openai",
            "qdrant",
            "runtime",
            "hosted",
            false,
            "EXTERNAL_EXISTING",
            "bring your own"
        );
        when(sourceResolver.summarize(deployment)).thenReturn(source);
        when(configModelService.build(deployment, null, null, template, source)).thenReturn(
            new DeploymentServiceConfigModelSummary(
                deployment.getId(),
                deployment.getName(),
                deployment.getEnvironmentName(),
                List.of(),
                "ok"
            )
        );

        var summary = service.build(deployment, null, template, null, null, null);

        DeploymentNavigationSurfaceSummary connectorSurface = summary.surfaces().stream()
            .filter(surface -> "restConnector".equals(surface.key()))
            .findFirst()
            .orElseThrow();
        assertThat(connectorSurface.primaryUrl()).isNull();
        assertThat(connectorSurface.docsUrl()).isEqualTo("https://runtime.example/swagger-ui/index.html");
        assertThat(connectorSurface.openApiUrl()).isEqualTo("https://runtime.example/v3/api-docs");
        assertThat(connectorSurface.adminUrl()).isEqualTo("https://runtime.example/api/admin/connector/overview");
        assertThat(connectorSurface.summaryMessage()).contains("runtime-backed admin");

        DeploymentNavigationSurfaceSummary browserSurface = summary.surfaces().stream()
            .filter(surface -> "uiSurface".equals(surface.key()))
            .findFirst()
            .orElseThrow();
        assertThat(browserSurface.summaryMessage()).contains("trusted host-backed APIs");

        DeploymentNavigationRelationshipSummary relationship = summary.relationships().stream()
            .filter(item -> "runtime-admin-to-connector".equals(item.key()))
            .findFirst()
            .orElseThrow();
        assertThat(relationship.summaryMessage()).contains("runtime admin endpoints");
    }
}
