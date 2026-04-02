package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.config.PlatformProvisioningProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeploymentReleaseRecoveryServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void reconcileLatestInProgressReleaseCompletesStaleRailwayReleaseAfterDeploymentsSucceed() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentReleaseRepository releaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentReleaseExecutionService deploymentReleaseExecutionService = mock(DeploymentReleaseExecutionService.class);
        RailwayGraphqlClient railwayGraphqlClient = mock(RailwayGraphqlClient.class);

        DeploymentReleaseRecoveryService service = new DeploymentReleaseRecoveryService(
            deploymentRepository,
            releaseRepository,
            deploymentReleaseExecutionService,
            railwayGraphqlClient,
            provisioningProperties(),
            objectMapper
        );

        DeploymentEntity deployment = deployment();
        DeploymentReleaseEntity release = staleProvisioningRelease();

        when(deploymentRepository.findByIdForUpdate(deployment.getId())).thenReturn(Optional.of(deployment));
        when(releaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc(deployment.getId())).thenReturn(Optional.of(release));
        when(railwayGraphqlClient.getDeployment("rt-deploy")).thenReturn(new RailwayGraphqlClient.RailwayDeploymentSummary(
            "rt-deploy",
            "SUCCESS",
            null,
            null,
            null
        ));
        when(railwayGraphqlClient.getDeployment("rest-deploy")).thenReturn(new RailwayGraphqlClient.RailwayDeploymentSummary(
            "rest-deploy",
            "SUCCESS",
            null,
            null,
            null
        ));

        boolean recovered = service.reconcileLatestInProgressRelease(deployment.getId());

        assertThat(recovered).isTrue();
        verify(deploymentReleaseExecutionService).applyProvisioningResult(
            eq(deployment.getId()),
            eq(release.getDeploymentVersionId()),
            eq(release.getId()),
            argThat(result -> "ACTIVE".equals(result.status())
                && "RAILWAY_API".equals(result.target())
                && "https://runtime.example".equals(result.runtimeBaseUrl())
                && "https://rest.example".equals(result.connectorBaseUrl()))
        );
        verify(deploymentReleaseExecutionService).runVerification(
            deployment.getId(),
            release.getDeploymentVersionId(),
            release.getId()
        );
    }

    @Test
    void reconcileLatestInProgressReleaseMarksRailwayFailure() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentReleaseRepository releaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentReleaseExecutionService deploymentReleaseExecutionService = mock(DeploymentReleaseExecutionService.class);
        RailwayGraphqlClient railwayGraphqlClient = mock(RailwayGraphqlClient.class);

        DeploymentReleaseRecoveryService service = new DeploymentReleaseRecoveryService(
            deploymentRepository,
            releaseRepository,
            deploymentReleaseExecutionService,
            railwayGraphqlClient,
            provisioningProperties(),
            objectMapper
        );

        DeploymentEntity deployment = deployment();
        DeploymentReleaseEntity release = staleProvisioningRelease();

        when(deploymentRepository.findByIdForUpdate(deployment.getId())).thenReturn(Optional.of(deployment));
        when(releaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc(deployment.getId())).thenReturn(Optional.of(release));
        when(railwayGraphqlClient.getDeployment("rt-deploy")).thenReturn(new RailwayGraphqlClient.RailwayDeploymentSummary(
            "rt-deploy",
            "FAILED",
            null,
            null,
            null
        ));
        when(railwayGraphqlClient.getDeployment("rest-deploy")).thenReturn(new RailwayGraphqlClient.RailwayDeploymentSummary(
            "rest-deploy",
            "SUCCESS",
            null,
            null,
            null
        ));

        boolean recovered = service.reconcileLatestInProgressRelease(deployment.getId());

        assertThat(recovered).isTrue();
        verify(deploymentReleaseExecutionService).markFailed(
            eq(release.getId()),
            eq(deployment.getId()),
            any(IllegalStateException.class)
        );
        verify(deploymentReleaseExecutionService, never()).runVerification(any(), any(), any());
    }

    @Test
    void reconcileLatestInProgressReleaseIgnoresFreshRelease() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentReleaseRepository releaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentReleaseExecutionService deploymentReleaseExecutionService = mock(DeploymentReleaseExecutionService.class);
        RailwayGraphqlClient railwayGraphqlClient = mock(RailwayGraphqlClient.class);

        DeploymentReleaseRecoveryService service = new DeploymentReleaseRecoveryService(
            deploymentRepository,
            releaseRepository,
            deploymentReleaseExecutionService,
            railwayGraphqlClient,
            provisioningProperties(),
            objectMapper
        );

        DeploymentEntity deployment = deployment();
        DeploymentReleaseEntity release = staleProvisioningRelease();
        release.setUpdatedAt(Instant.now());

        when(deploymentRepository.findByIdForUpdate(deployment.getId())).thenReturn(Optional.of(deployment));
        when(releaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc(deployment.getId())).thenReturn(Optional.of(release));

        boolean recovered = service.reconcileLatestInProgressRelease(deployment.getId());

        assertThat(recovered).isFalse();
        verify(deploymentReleaseExecutionService, never()).applyProvisioningResult(any(), any(), any(), any());
        verifyNoRailwayInteractions(railwayGraphqlClient);
    }

    @Test
    void reconcileLatestInProgressReleaseCanBeForcedForManualRefresh() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentReleaseRepository releaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentReleaseExecutionService deploymentReleaseExecutionService = mock(DeploymentReleaseExecutionService.class);
        RailwayGraphqlClient railwayGraphqlClient = mock(RailwayGraphqlClient.class);

        DeploymentReleaseRecoveryService service = new DeploymentReleaseRecoveryService(
            deploymentRepository,
            releaseRepository,
            deploymentReleaseExecutionService,
            railwayGraphqlClient,
            provisioningProperties(),
            objectMapper
        );

        DeploymentEntity deployment = deployment();
        DeploymentReleaseEntity release = staleProvisioningRelease();
        release.setUpdatedAt(Instant.now());

        when(deploymentRepository.findByIdForUpdate(deployment.getId())).thenReturn(Optional.of(deployment));
        when(releaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc(deployment.getId())).thenReturn(Optional.of(release));
        when(railwayGraphqlClient.getDeployment("rt-deploy")).thenReturn(new RailwayGraphqlClient.RailwayDeploymentSummary(
            "rt-deploy",
            "SUCCESS",
            null,
            null,
            null
        ));
        when(railwayGraphqlClient.getDeployment("rest-deploy")).thenReturn(new RailwayGraphqlClient.RailwayDeploymentSummary(
            "rest-deploy",
            "SUCCESS",
            null,
            null,
            null
        ));

        boolean recovered = service.reconcileLatestInProgressRelease(deployment.getId(), true);

        assertThat(recovered).isTrue();
        verify(deploymentReleaseExecutionService).applyProvisioningResult(
            eq(deployment.getId()),
            eq(release.getDeploymentVersionId()),
            eq(release.getId()),
            any()
        );
        verify(deploymentReleaseExecutionService).runVerification(
            deployment.getId(),
            release.getDeploymentVersionId(),
            release.getId()
        );
    }

    private void verifyNoRailwayInteractions(RailwayGraphqlClient railwayGraphqlClient) {
        verify(railwayGraphqlClient, never()).getDeployment(any());
    }

    private DeploymentEntity deployment() {
        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-123");
        deployment.setActiveVersionId(null);
        deployment.setRuntimeBaseUrl("https://runtime.example");
        deployment.setConnectorBaseUrl("https://rest.example");
        return deployment;
    }

    private DeploymentReleaseEntity staleProvisioningRelease() {
        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setId("rel-123");
        release.setDeploymentId("dep-123");
        release.setDeploymentVersionId("ver-123");
        release.setStatus("PROVISIONING");
        release.setProvisioningTarget("RAILWAY_API");
        release.setProvisioningStatus("RUNNING");
        release.setVerificationStatus("PENDING");
        release.setCurrentStepKey("wait_for_active");
        release.setUpdatedAt(Instant.now().minus(Duration.ofMinutes(5)));
        release.setProvisioningDetailsJson("""
            {
              "railway": {
                "services": {
                  "runtime": {
                    "deploymentId": "rt-deploy",
                    "baseUrl": "https://runtime.example"
                  },
                  "restConnector": {
                    "deploymentId": "rest-deploy",
                    "baseUrl": "https://rest.example"
                  }
                }
              }
            }
            """);
        return release;
    }

    private PlatformProvisioningProperties provisioningProperties() {
        return new PlatformProvisioningProperties(
            "RAILWAY_API",
            null,
            "token",
            "mahmoudashraf/AI-Fabric-Framework",
            "Platformv-V2",
            "dev",
            "workspace",
            null,
            null,
            null,
            null,
            null,
            null,
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
