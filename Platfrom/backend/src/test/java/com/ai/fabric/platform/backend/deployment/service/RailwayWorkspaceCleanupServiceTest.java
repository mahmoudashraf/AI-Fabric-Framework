package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.PlatformProvisioningProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.model.ExecuteRailwayWorkspaceCleanupRequest;
import com.ai.fabric.platform.backend.deployment.model.RailwayWorkspaceCleanupSummary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RailwayWorkspaceCleanupServiceTest {

    @Test
    void summaryAndCleanupExposeOnlyCurrentOrphanRailwayResources() {
        RailwayGraphqlClient railwayGraphqlClient = mock(RailwayGraphqlClient.class);
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentReleaseRepository deploymentReleaseRepository = mock(DeploymentReleaseRepository.class);
        PlatformAuditService platformAuditService = mock(PlatformAuditService.class);

        RailwayWorkspaceCleanupService service = new RailwayWorkspaceCleanupService(
            provisioningProperties(),
            railwayGraphqlClient,
            deploymentRepository,
            deploymentReleaseRepository,
            platformAuditService,
            new ObjectMapper()
        );

        DeploymentEntity ownedDeployment = new DeploymentEntity();
        ownedDeployment.setId("dep-owned");
        ownedDeployment.setName("Owned Deployment");
        ownedDeployment.setEnvironmentName("dev");

        DeploymentReleaseEntity ownedRelease = new DeploymentReleaseEntity();
        ownedRelease.setProvisioningDetailsJson("""
            {
              "railway": {
                "projectId": "proj-owned",
                "services": {
                  "runtime": { "serviceId": "svc-owned-runtime" },
                  "restConnector": { "serviceId": "svc-owned-rest" }
                }
              }
            }
            """);

        when(deploymentRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(ownedDeployment));
        when(deploymentReleaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc("dep-owned")).thenReturn(Optional.of(ownedRelease));
        when(railwayGraphqlClient.listAccessibleWorkspaces()).thenReturn(List.of(
            new RailwayGraphqlClient.RailwayWorkspaceSummary("workspace-1", "AI Fabric")
        ));
        when(railwayGraphqlClient.listProjectsInWorkspace("workspace-1")).thenReturn(List.of(
            new RailwayGraphqlClient.RailwayProjectSnapshot(
                "proj-owned",
                "owned-dev-aaaa1111",
                List.of(new RailwayGraphqlClient.RailwayEnvironmentSummary("env-1", "dev")),
                List.of(
                    new RailwayGraphqlClient.RailwayServiceSummary("svc-owned-runtime", "runtime-dep-owned"),
                    new RailwayGraphqlClient.RailwayServiceSummary("svc-orphan", "runtime-dep-orphan")
                )
            ),
            new RailwayGraphqlClient.RailwayProjectSnapshot(
                "proj-orphan",
                "orphan-dev-bbbb2222",
                List.of(new RailwayGraphqlClient.RailwayEnvironmentSummary("env-2", "dev")),
                List.of(new RailwayGraphqlClient.RailwayServiceSummary("svc-orphan-project", "rest-connector-dep-orphan"))
            )
        ));
        when(railwayGraphqlClient.getServiceSource("svc-owned-runtime")).thenReturn(new RailwayGraphqlClient.RailwayServiceSourceSummary(
            "svc-owned-runtime",
            "runtime-dep-owned",
            List.of(new RailwayGraphqlClient.RailwayDeploymentTriggerSummary("tr-1", "svc-owned-runtime", "mahmoudashraf/AI-Fabric-Framework", "main", "github"))
        ));
        when(railwayGraphqlClient.getServiceSource("svc-orphan")).thenReturn(new RailwayGraphqlClient.RailwayServiceSourceSummary(
            "svc-orphan",
            "runtime-dep-orphan",
            List.of(new RailwayGraphqlClient.RailwayDeploymentTriggerSummary("tr-2", "svc-orphan", "mahmoudashraf/AI-Fabric-Framework", "main", "github"))
        ));
        when(railwayGraphqlClient.getServiceSource("svc-orphan-project")).thenReturn(new RailwayGraphqlClient.RailwayServiceSourceSummary(
            "svc-orphan-project",
            "rest-connector-dep-orphan",
            List.of(new RailwayGraphqlClient.RailwayDeploymentTriggerSummary("tr-3", "svc-orphan-project", "mahmoudashraf/AI-Fabric-Framework", "main", "github"))
        ));

        RailwayWorkspaceCleanupSummary summary = service.getSummary();

        assertThat(summary.available()).isTrue();
        assertThat(summary.orphanProjectCount()).isEqualTo(1);
        assertThat(summary.orphanServiceCount()).isEqualTo(2);
        assertThat(summary.projects()).hasSize(2);
        assertThat(summary.projects().stream().filter(project -> "proj-owned".equals(project.projectId())).findFirst().orElseThrow()
            .orphanServices()).extracting("serviceId").containsExactly("svc-orphan");
        assertThat(summary.projects().stream().filter(project -> "proj-orphan".equals(project.projectId())).findFirst().orElseThrow()
            .deletable()).isTrue();

        service.cleanupOrphans(new ExecuteRailwayWorkspaceCleanupRequest(
            true,
            "remove orphan platform-owned railway resources",
            List.of("proj-orphan"),
            List.of("svc-orphan")
        ));

        verify(railwayGraphqlClient).deleteProject("proj-orphan");
        verify(railwayGraphqlClient).deleteService("svc-orphan");
    }

    private PlatformProvisioningProperties provisioningProperties() {
        return new PlatformProvisioningProperties(
            "RAILWAY_API",
            null,
            "railway-token",
            "mahmoudashraf/AI-Fabric-Framework",
            "main",
            "dev",
            "workspace-1",
            null,
            null,
            null,
            null,
            "runtime",
            "rest-connector",
            32,
            null,
            null,
            false,
            false,
            60_000,
            Duration.ofSeconds(5),
            Duration.ofMinutes(10)
        );
    }
}
