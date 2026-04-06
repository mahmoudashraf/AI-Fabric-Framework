package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentRailwayLogsResponse;
import com.ai.fabric.platform.backend.deployment.model.RailwayLogAttributeSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayLogEntrySummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayLogTagsSummary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeploymentRailwayLogServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void fetchLogsUsesLatestRailwayReleaseAndReturnsEntries() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentReleaseRepository releaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentAccessService deploymentAccessService = mock(DeploymentAccessService.class);
        RailwayGraphqlClient railwayGraphqlClient = mock(RailwayGraphqlClient.class);

        DeploymentRailwayLogService service = new DeploymentRailwayLogService(
            deploymentRepository,
            releaseRepository,
            deploymentAccessService,
            railwayGraphqlClient,
            objectMapper
        );

        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-123");

        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setId("rel-123");
        release.setDeploymentId("dep-123");
        release.setDeploymentVersionId("ver-123");
        release.setStatus("APPLIED_VERIFIED");
        release.setProvisioningTarget("RAILWAY_API");
        release.setProvisioningDetailsJson("""
            {
              "railway": {
                "projectId": "proj-1",
                "environmentId": "env-1",
                "services": {
                  "runtime": {
                    "serviceId": "svc-rt",
                    "serviceName": "runtime-dep-123",
                    "deploymentId": "rail-deploy-rt"
                  }
                }
              }
            }
            """);

        RailwayLogEntrySummary entry = new RailwayLogEntrySummary(
            "2026-03-29T12:00:00Z",
            "INFO",
            "Runtime booted",
            new RailwayLogTagsSummary("rail-deploy-rt", "instance-1", "env-1", "proj-1", "svc-rt", "snap-1"),
            List.of(new RailwayLogAttributeSummary("logger", "startup"))
        );

        when(deploymentRepository.findById("dep-123")).thenReturn(Optional.of(deployment));
        when(releaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc("dep-123")).thenReturn(Optional.of(release));
        when(railwayGraphqlClient.fetchDeploymentLogs("rail-deploy-rt", 200, null, null, null))
            .thenReturn(List.of(entry));

        DeploymentRailwayLogsResponse response = service.fetchLogs("dep-123", null, "runtime", "deployment", null, null, null, null);

        assertThat(response.available()).isTrue();
        assertThat(response.releaseId()).isEqualTo("rel-123");
        assertThat(response.service()).isEqualTo("runtime");
        assertThat(response.source()).isEqualTo("deployment");
        assertThat(response.railwayDeploymentId()).isEqualTo("rail-deploy-rt");
        assertThat(response.entries()).containsExactly(entry);
    }

    @Test
    void fetchLogsSupportsVectorizationRunnerService() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentReleaseRepository releaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentAccessService deploymentAccessService = mock(DeploymentAccessService.class);
        RailwayGraphqlClient railwayGraphqlClient = mock(RailwayGraphqlClient.class);

        DeploymentRailwayLogService service = new DeploymentRailwayLogService(
            deploymentRepository,
            releaseRepository,
            deploymentAccessService,
            railwayGraphqlClient,
            objectMapper
        );

        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-123");

        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setId("rel-123");
        release.setDeploymentId("dep-123");
        release.setDeploymentVersionId("ver-123");
        release.setStatus("FAILED");
        release.setProvisioningTarget("RAILWAY_API");
        release.setProvisioningDetailsJson("""
            {
              "railway": {
                "projectId": "proj-1",
                "environmentId": "env-1",
                "services": {
                  "vectorizationRunner": {
                    "serviceId": "svc-runner",
                    "serviceName": "vectorization-runner-dep-123",
                    "deploymentId": "rail-deploy-runner"
                  }
                }
              }
            }
            """);

        RailwayLogEntrySummary entry = new RailwayLogEntrySummary(
            "2026-04-05T12:00:00Z",
            "ERROR",
            "Runner failed",
            new RailwayLogTagsSummary("rail-deploy-runner", "instance-1", "env-1", "proj-1", "svc-runner", "snap-1"),
            List.of()
        );

        when(deploymentRepository.findById("dep-123")).thenReturn(Optional.of(deployment));
        when(releaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc("dep-123")).thenReturn(Optional.of(release));
        when(railwayGraphqlClient.fetchBuildLogs("rail-deploy-runner", 200, null, null, null))
            .thenReturn(List.of(entry));

        DeploymentRailwayLogsResponse response = service.fetchLogs(
            "dep-123",
            null,
            "vectorization-runner",
            "build",
            null,
            null,
            null,
            null
        );

        assertThat(response.available()).isTrue();
        assertThat(response.service()).isEqualTo("vectorizationRunner");
        assertThat(response.source()).isEqualTo("build");
        assertThat(response.serviceName()).isEqualTo("vectorization-runner-dep-123");
        assertThat(response.railwayDeploymentId()).isEqualTo("rail-deploy-runner");
        assertThat(response.entries()).containsExactly(entry);
    }

    @Test
    void fetchLogsReturnsUnavailableForStubRelease() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentReleaseRepository releaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentAccessService deploymentAccessService = mock(DeploymentAccessService.class);
        RailwayGraphqlClient railwayGraphqlClient = mock(RailwayGraphqlClient.class);

        DeploymentRailwayLogService service = new DeploymentRailwayLogService(
            deploymentRepository,
            releaseRepository,
            deploymentAccessService,
            railwayGraphqlClient,
            objectMapper
        );

        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-123");

        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setId("rel-123");
        release.setDeploymentId("dep-123");
        release.setDeploymentVersionId("ver-123");
        release.setStatus("APPLIED_VERIFIED");
        release.setProvisioningTarget("RAILWAY_STUB");
        release.setCreatedAt(Instant.now());

        when(deploymentRepository.findById("dep-123")).thenReturn(Optional.of(deployment));
        when(releaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc("dep-123")).thenReturn(Optional.of(release));

        DeploymentRailwayLogsResponse response = service.fetchLogs("dep-123", null, "runtime", "deployment", 50, null, null, null);

        assertThat(response.available()).isFalse();
        assertThat(response.message()).contains("RAILWAY_API");
        assertThat(response.entries()).isEmpty();
    }

    @Test
    void fetchLogsRejectsReleaseThatDoesNotBelongToDeployment() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentReleaseRepository releaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentAccessService deploymentAccessService = mock(DeploymentAccessService.class);
        RailwayGraphqlClient railwayGraphqlClient = mock(RailwayGraphqlClient.class);

        DeploymentRailwayLogService service = new DeploymentRailwayLogService(
            deploymentRepository,
            releaseRepository,
            deploymentAccessService,
            railwayGraphqlClient,
            objectMapper
        );

        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-123");

        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setId("rel-999");
        release.setDeploymentId("dep-other");

        when(deploymentRepository.findById("dep-123")).thenReturn(Optional.of(deployment));
        when(releaseRepository.findById("rel-999")).thenReturn(Optional.of(release));

        assertThatThrownBy(() -> service.fetchLogs("dep-123", "rel-999", "runtime", "deployment", null, null, null, null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Release does not belong to deployment");
    }
}
