package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.config.PlatformProvisioningProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentNavigationRelationshipSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentNavigationSurfaceSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentServiceConfigModelSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSourceSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTemplateSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayProvisioningPlanSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayProvisioningServicesSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayServicePlanSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DeploymentServiceNavigationServiceTest {

    @Test
    void deskNavigationKeepsPublishedMetadataWithoutInferringATarget() {
        var fixture = new NavigationFixture();
        var latest = version("ver-desk", "{}");
        latest.setBehaviorConfigJson("{\"conversationHandover\":{\"marketplacePluginId\":\"mkp-integration-loomai-desk\"}}");
        fixture.stubModel(latest);
        var summary = fixture.service.build(fixture.deployment, null, null, latest, null, null);
        verifyNoInteractions(fixture.plans);
        assertThat(summary.provider().repository()).isEqualTo("org/repo");
        assertThat(summary.surfaces().stream().filter(surface -> "runtime".equals(surface.key())).findFirst().orElseThrow().primaryUrl())
            .isEqualTo("https://runtime.example");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void externalBindingNavigationUsesExistingMetadataWithoutExecutablePlan(boolean live) {
        var fixture = new NavigationFixture();
        var bound = version(live ? "ver-live" : "ver-latest", "{\"milvusExternalBindingRequired\":true}");
        var latest = live ? version("ver-latest", "{}") : bound;
        fixture.stubModel(latest);
        var release = new DeploymentReleaseEntity();
        release.setTargetProfileId("target-navigation-must-not-infer");
        release.setProvisioningTarget("COOLIFY");
        release.setProvisioningDetailsJson("{\"projectId\":\"recorded-project\",\"projectName\":\"Recorded project\"}");

        var summary = fixture.service.build(fixture.deployment, null, null, latest, live ? bound : null, release);

        verifyNoInteractions(fixture.plans);
        assertThat(summary.provider().projectId()).isEqualTo("recorded-project");
        assertThat(summary.provider().projectName()).isEqualTo("Recorded project");
        assertThat(summary.provider().repository()).isEqualTo("org/repo");
        assertThat(summary.provider().branch()).isEqualTo("main");
        var runtime = summary.surfaces().stream().filter(surface -> "runtime".equals(surface.key())).findFirst().orElseThrow();
        assertThat(runtime.primaryUrl()).isEqualTo("https://runtime.example");
        assertThat(runtime.serviceName()).isNull();
        var connector = summary.surfaces().stream().filter(surface -> "restConnector".equals(surface.key())).findFirst().orElseThrow();
        assertThat(connector.adminUrl()).isEqualTo("https://runtime.example/api/admin/connector/overview");
        assertThat(connector.serviceName()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"milvusExternalBindingRequired\":false}"})
    void ordinaryPublishedVersionStillUsesExecutablePlanForNavigation(String providerConfig) {
        var fixture = new NavigationFixture();
        var latest = version("ver-latest", providerConfig);
        fixture.stubModel(latest);
        var plan = mock(RailwayProvisioningPlanSummary.class);
        var services = mock(RailwayProvisioningServicesSummary.class);
        var runtime = mock(RailwayServicePlanSummary.class);
        var connector = mock(RailwayServicePlanSummary.class);
        when(fixture.plans.buildPlan(fixture.deployment, latest)).thenReturn(plan);
        when(plan.projectName()).thenReturn("Planned project");
        when(plan.repository()).thenReturn("planned/repo");
        when(plan.branch()).thenReturn("planned-branch");
        when(plan.services()).thenReturn(services);
        when(services.runtime()).thenReturn(runtime);
        when(services.restConnector()).thenReturn(connector);
        when(runtime.serviceName()).thenReturn("planned-runtime");

        var summary = fixture.service.build(fixture.deployment, null, null, latest, null, null);

        verify(fixture.plans).buildPlan(fixture.deployment, latest);
        assertThat(summary.provider().projectName()).isEqualTo("Planned project");
        assertThat(summary.provider().repository()).isEqualTo("planned/repo");
        assertThat(summary.provider().branch()).isEqualTo("planned-branch");
        assertThat(summary.surfaces().stream().filter(surface -> "runtime".equals(surface.key())).findFirst().orElseThrow().serviceName())
            .isEqualTo("planned-runtime");
    }

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
    private static DeploymentVersionEntity version(String id, String providerConfig) {
        var version = new DeploymentVersionEntity();
        version.setId(id); version.setDeploymentId("dep-123"); version.setStatus("PUBLISHED");
        version.setProviderConfigJson(providerConfig);
        return version;
    }

    private static final class NavigationFixture {
        final DeploymentServiceConfigModelService configs = mock(DeploymentServiceConfigModelService.class);
        final DeploymentSourceResolver sources = mock(DeploymentSourceResolver.class);
        final RailwayProvisioningPlanService plans = mock(RailwayProvisioningPlanService.class);
        final DeploymentEntity deployment = new DeploymentEntity();
        final DeploymentSourceSummary source = new DeploymentSourceSummary("org/repo", "main", null, null, false);
        final DeploymentServiceNavigationService service = new DeploymentServiceNavigationService(
            configs, sources, plans, mock(PlatformProvisioningProperties.class), new ObjectMapper());

        NavigationFixture() {
            deployment.setId("dep-123"); deployment.setName("Synthetic staging"); deployment.setEnvironmentName("staging");
            deployment.setRuntimeBaseUrl("https://runtime.example"); deployment.setConnectorBaseUrl("https://connector.internal");
            when(sources.summarize(deployment)).thenReturn(source);
        }

        void stubModel(DeploymentVersionEntity latest) {
            when(configs.build(deployment, null, latest, null, source)).thenReturn(new DeploymentServiceConfigModelSummary(
                deployment.getId(), deployment.getName(), deployment.getEnvironmentName(), List.of(), "ok"));
        }
    }
}
