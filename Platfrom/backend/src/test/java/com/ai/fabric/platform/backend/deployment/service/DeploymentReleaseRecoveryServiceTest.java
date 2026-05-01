package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.config.PlatformProvisioningProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVerificationRunEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVerificationRunRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
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
        DeploymentVerificationRunRepository verificationRunRepository = mock(DeploymentVerificationRunRepository.class);

        DeploymentReleaseRecoveryService service = new DeploymentReleaseRecoveryService(
            deploymentRepository,
            releaseRepository,
            deploymentReleaseExecutionService,
            verificationRunRepository,
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
        DeploymentVerificationRunRepository verificationRunRepository = mock(DeploymentVerificationRunRepository.class);

        DeploymentReleaseRecoveryService service = new DeploymentReleaseRecoveryService(
            deploymentRepository,
            releaseRepository,
            deploymentReleaseExecutionService,
            verificationRunRepository,
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
    void reconcileLatestInProgressReleaseRunsVerificationAfterCompletedMarketplaceDatasetSync() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentReleaseRepository releaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentReleaseExecutionService deploymentReleaseExecutionService = mock(DeploymentReleaseExecutionService.class);
        RailwayGraphqlClient railwayGraphqlClient = mock(RailwayGraphqlClient.class);
        DeploymentVerificationRunRepository verificationRunRepository = mock(DeploymentVerificationRunRepository.class);

        DeploymentReleaseRecoveryService service = new DeploymentReleaseRecoveryService(
            deploymentRepository,
            releaseRepository,
            deploymentReleaseExecutionService,
            verificationRunRepository,
            railwayGraphqlClient,
            provisioningProperties(),
            objectMapper
        );

        DeploymentEntity deployment = deployment();
        DeploymentReleaseEntity release = staleMarketplaceDatasetSyncRelease(true);

        when(deploymentRepository.findByIdForUpdate(deployment.getId())).thenReturn(Optional.of(deployment));
        when(releaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc(deployment.getId())).thenReturn(Optional.of(release));

        boolean recovered = service.reconcileLatestInProgressRelease(deployment.getId());

        assertThat(recovered).isTrue();
        verify(deploymentReleaseExecutionService, never()).syncMarketplaceDatasets(any(), any(), any());
        verify(deploymentReleaseExecutionService).runVerification(
            deployment.getId(),
            release.getDeploymentVersionId(),
            release.getId()
        );
        verifyNoRailwayInteractions(railwayGraphqlClient);
    }

    @Test
    void reconcileLatestInProgressReleaseRetriesMarketplaceDatasetSyncBeforeVerificationWhenSummaryIsMissing() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentReleaseRepository releaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentReleaseExecutionService deploymentReleaseExecutionService = mock(DeploymentReleaseExecutionService.class);
        RailwayGraphqlClient railwayGraphqlClient = mock(RailwayGraphqlClient.class);
        DeploymentVerificationRunRepository verificationRunRepository = mock(DeploymentVerificationRunRepository.class);

        DeploymentReleaseRecoveryService service = new DeploymentReleaseRecoveryService(
            deploymentRepository,
            releaseRepository,
            deploymentReleaseExecutionService,
            verificationRunRepository,
            railwayGraphqlClient,
            provisioningProperties(),
            objectMapper
        );

        DeploymentEntity deployment = deployment();
        DeploymentReleaseEntity release = staleMarketplaceDatasetSyncRelease(false);

        when(deploymentRepository.findByIdForUpdate(deployment.getId())).thenReturn(Optional.of(deployment));
        when(releaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc(deployment.getId())).thenReturn(Optional.of(release));

        boolean recovered = service.reconcileLatestInProgressRelease(deployment.getId());

        assertThat(recovered).isTrue();
        InOrder inOrder = inOrder(deploymentReleaseExecutionService);
        inOrder.verify(deploymentReleaseExecutionService).syncMarketplaceDatasets(
            deployment.getId(),
            release.getDeploymentVersionId(),
            release.getId()
        );
        inOrder.verify(deploymentReleaseExecutionService).runVerification(
            deployment.getId(),
            release.getDeploymentVersionId(),
            release.getId()
        );
        verifyNoRailwayInteractions(railwayGraphqlClient);
    }

    @Test
    void reconcileLatestInProgressReleaseRetriesCoolifyMarketplaceDatasetSyncBeforeVerificationWhenSummaryIsMissing() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentReleaseRepository releaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentReleaseExecutionService deploymentReleaseExecutionService = mock(DeploymentReleaseExecutionService.class);
        RailwayGraphqlClient railwayGraphqlClient = mock(RailwayGraphqlClient.class);
        DeploymentVerificationRunRepository verificationRunRepository = mock(DeploymentVerificationRunRepository.class);

        DeploymentReleaseRecoveryService service = new DeploymentReleaseRecoveryService(
            deploymentRepository,
            releaseRepository,
            deploymentReleaseExecutionService,
            verificationRunRepository,
            railwayGraphqlClient,
            provisioningProperties(),
            objectMapper
        );

        DeploymentEntity deployment = deployment();
        DeploymentReleaseEntity release = staleMarketplaceDatasetSyncRelease(false);
        release.setProvisioningTarget("COOLIFY");
        release.setProviderType(DeploymentProviderType.COOLIFY);

        when(deploymentRepository.findByIdForUpdate(deployment.getId())).thenReturn(Optional.of(deployment));
        when(releaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc(deployment.getId())).thenReturn(Optional.of(release));

        boolean recovered = service.reconcileLatestInProgressRelease(deployment.getId());

        assertThat(recovered).isTrue();
        InOrder inOrder = inOrder(deploymentReleaseExecutionService);
        inOrder.verify(deploymentReleaseExecutionService).syncMarketplaceDatasets(
            deployment.getId(),
            release.getDeploymentVersionId(),
            release.getId()
        );
        inOrder.verify(deploymentReleaseExecutionService).runVerification(
            deployment.getId(),
            release.getDeploymentVersionId(),
            release.getId()
        );
        verifyNoRailwayInteractions(railwayGraphqlClient);
    }

    @Test
    void reconcileLatestInProgressReleaseIgnoresFreshRelease() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentReleaseRepository releaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentReleaseExecutionService deploymentReleaseExecutionService = mock(DeploymentReleaseExecutionService.class);
        RailwayGraphqlClient railwayGraphqlClient = mock(RailwayGraphqlClient.class);
        DeploymentVerificationRunRepository verificationRunRepository = mock(DeploymentVerificationRunRepository.class);

        DeploymentReleaseRecoveryService service = new DeploymentReleaseRecoveryService(
            deploymentRepository,
            releaseRepository,
            deploymentReleaseExecutionService,
            verificationRunRepository,
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
        DeploymentVerificationRunRepository verificationRunRepository = mock(DeploymentVerificationRunRepository.class);

        DeploymentReleaseRecoveryService service = new DeploymentReleaseRecoveryService(
            deploymentRepository,
            releaseRepository,
            deploymentReleaseExecutionService,
            verificationRunRepository,
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

    @Test
    void reconcileLatestInProgressReleaseRedispatchesStaleQueuedApply() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentReleaseRepository releaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentReleaseExecutionService deploymentReleaseExecutionService = mock(DeploymentReleaseExecutionService.class);
        RailwayGraphqlClient railwayGraphqlClient = mock(RailwayGraphqlClient.class);
        DeploymentVerificationRunRepository verificationRunRepository = mock(DeploymentVerificationRunRepository.class);

        DeploymentReleaseRecoveryService service = new DeploymentReleaseRecoveryService(
            deploymentRepository,
            releaseRepository,
            deploymentReleaseExecutionService,
            verificationRunRepository,
            railwayGraphqlClient,
            provisioningProperties(),
            objectMapper
        );

        DeploymentEntity deployment = deployment();
        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setId("rel-queued");
        release.setDeploymentId(deployment.getId());
        release.setDeploymentVersionId("ver-queued");
        release.setStatus("APPLY_REQUESTED");
        release.setProvisioningTarget("RAILWAY_API");
        release.setProvisioningStatus("QUEUED");
        release.setCurrentStepKey("queue_release");
        release.setUpdatedAt(Instant.now().minus(Duration.ofMinutes(5)));

        when(deploymentRepository.findByIdForUpdate(deployment.getId())).thenReturn(Optional.of(deployment));
        when(releaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc(deployment.getId())).thenReturn(Optional.of(release));
        when(deploymentReleaseExecutionService.tryDispatchApplyAsync(
            deployment.getId(),
            release.getDeploymentVersionId(),
            release.getId()
        )).thenReturn(true);

        boolean recovered = service.reconcileLatestInProgressRelease(deployment.getId());

        assertThat(recovered).isTrue();
        verify(deploymentReleaseExecutionService).tryDispatchApplyAsync(
            deployment.getId(),
            release.getDeploymentVersionId(),
            release.getId()
        );
        verifyNoRailwayInteractions(railwayGraphqlClient);
    }

    @Test
    void reconcileLatestInProgressReleaseFailsStalePreApplyVerificationStep() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentReleaseRepository releaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentReleaseExecutionService deploymentReleaseExecutionService = mock(DeploymentReleaseExecutionService.class);
        RailwayGraphqlClient railwayGraphqlClient = mock(RailwayGraphqlClient.class);
        DeploymentVerificationRunRepository verificationRunRepository = mock(DeploymentVerificationRunRepository.class);

        DeploymentReleaseRecoveryService service = new DeploymentReleaseRecoveryService(
            deploymentRepository,
            releaseRepository,
            deploymentReleaseExecutionService,
            verificationRunRepository,
            railwayGraphqlClient,
            provisioningProperties(),
            objectMapper
        );

        DeploymentEntity deployment = deployment();
        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setId("rel-preapply");
        release.setDeploymentId(deployment.getId());
        release.setDeploymentVersionId("ver-preapply");
        release.setStatus("PRE_APPLY_VERIFYING");
        release.setProvisioningTarget("RAILWAY_API");
        release.setCurrentStepKey("preflight_verification");
        release.setCurrentStepDescription("Running pre-apply verification gate.");
        release.setUpdatedAt(Instant.now().minus(Duration.ofMinutes(5)));

        when(deploymentRepository.findByIdForUpdate(deployment.getId())).thenReturn(Optional.of(deployment));
        when(releaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc(deployment.getId())).thenReturn(Optional.of(release));

        boolean recovered = service.reconcileLatestInProgressRelease(deployment.getId());

        assertThat(recovered).isTrue();
        verify(deploymentReleaseExecutionService).markFailed(
            eq(release.getId()),
            eq(deployment.getId()),
            argThat(ex -> ex instanceof IllegalStateException
                && ex.getMessage() != null
                && ex.getMessage().contains("preflight_verification"))
        );
        verifyNoRailwayInteractions(railwayGraphqlClient);
    }

    @Test
    void reconcileLatestInProgressReleaseFailsStalePreActivationProvisioningStep() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentReleaseRepository releaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentReleaseExecutionService deploymentReleaseExecutionService = mock(DeploymentReleaseExecutionService.class);
        RailwayGraphqlClient railwayGraphqlClient = mock(RailwayGraphqlClient.class);
        DeploymentVerificationRunRepository verificationRunRepository = mock(DeploymentVerificationRunRepository.class);

        DeploymentReleaseRecoveryService service = new DeploymentReleaseRecoveryService(
            deploymentRepository,
            releaseRepository,
            deploymentReleaseExecutionService,
            verificationRunRepository,
            railwayGraphqlClient,
            provisioningProperties(),
            objectMapper
        );

        DeploymentEntity deployment = deployment();
        DeploymentReleaseEntity release = staleProvisioningRelease();
        release.setCurrentStepKey("configure_vectorization_runner");
        release.setCurrentStepDescription("Create or update the vectorization runner service root and its environment variables.");

        when(deploymentRepository.findByIdForUpdate(deployment.getId())).thenReturn(Optional.of(deployment));
        when(releaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc(deployment.getId())).thenReturn(Optional.of(release));

        boolean recovered = service.reconcileLatestInProgressRelease(deployment.getId());

        assertThat(recovered).isTrue();
        verify(deploymentReleaseExecutionService).markFailed(
            eq(release.getId()),
            eq(deployment.getId()),
            argThat(ex -> ex instanceof IllegalStateException
                && ex.getMessage() != null
                && ex.getMessage().contains("configure_vectorization_runner"))
        );
        verifyNoRailwayInteractions(railwayGraphqlClient);
    }

    @Test
    void reconcileLatestInProgressReleaseFailsStaleEnsureVectorBackendStep() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentReleaseRepository releaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentReleaseExecutionService deploymentReleaseExecutionService = mock(DeploymentReleaseExecutionService.class);
        RailwayGraphqlClient railwayGraphqlClient = mock(RailwayGraphqlClient.class);
        DeploymentVerificationRunRepository verificationRunRepository = mock(DeploymentVerificationRunRepository.class);

        DeploymentReleaseRecoveryService service = new DeploymentReleaseRecoveryService(
            deploymentRepository,
            releaseRepository,
            deploymentReleaseExecutionService,
            verificationRunRepository,
            railwayGraphqlClient,
            provisioningProperties(),
            objectMapper
        );

        DeploymentEntity deployment = deployment();
        DeploymentReleaseEntity release = staleProvisioningRelease();
        release.setCurrentStepKey("ensure_vector_backend");
        release.setCurrentStepDescription("Create or reconcile managed external vector resources before runtime deployment.");

        when(deploymentRepository.findByIdForUpdate(deployment.getId())).thenReturn(Optional.of(deployment));
        when(releaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc(deployment.getId())).thenReturn(Optional.of(release));

        boolean recovered = service.reconcileLatestInProgressRelease(deployment.getId());

        assertThat(recovered).isTrue();
        verify(deploymentReleaseExecutionService).markFailed(
            eq(release.getId()),
            eq(deployment.getId()),
            argThat(ex -> ex instanceof IllegalStateException
                && ex.getMessage() != null
                && ex.getMessage().contains("ensure_vector_backend"))
        );
        verifyNoRailwayInteractions(railwayGraphqlClient);
    }

    @Test
    void reconcileLatestInProgressReleasePromotesFailedReleaseWhenLaterVerificationPassed() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentReleaseRepository releaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentReleaseExecutionService deploymentReleaseExecutionService = mock(DeploymentReleaseExecutionService.class);
        RailwayGraphqlClient railwayGraphqlClient = mock(RailwayGraphqlClient.class);
        DeploymentVerificationRunRepository verificationRunRepository = mock(DeploymentVerificationRunRepository.class);

        DeploymentReleaseRecoveryService service = new DeploymentReleaseRecoveryService(
            deploymentRepository,
            releaseRepository,
            deploymentReleaseExecutionService,
            verificationRunRepository,
            railwayGraphqlClient,
            provisioningProperties(),
            objectMapper
        );

        DeploymentEntity deployment = deployment();
        DeploymentReleaseEntity release = failedCoolifyVerificationRelease();
        DeploymentVerificationRunEntity passedRun = passedVerificationRun(release);

        when(deploymentRepository.findByIdForUpdate(deployment.getId())).thenReturn(Optional.of(deployment));
        when(releaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc(deployment.getId())).thenReturn(Optional.of(release));
        when(verificationRunRepository.findByReleaseIdOrderByCreatedAtDesc(release.getId())).thenReturn(List.of(passedRun));

        boolean recovered = service.reconcileLatestInProgressRelease(deployment.getId());

        assertThat(recovered).isTrue();
        assertThat(release.getStatus()).isEqualTo("APPLIED_VERIFIED");
        assertThat(release.getVerificationStatus()).isEqualTo("PASSED");
        assertThat(release.getVerificationRunId()).isEqualTo(passedRun.getId());
        assertThat(deployment.getStatus()).isEqualTo("ACTIVE");
        assertThat(deployment.getActiveVersionId()).isEqualTo(release.getDeploymentVersionId());
        verify(releaseRepository).save(release);
        verify(deploymentRepository).save(deployment);
        verify(deploymentReleaseExecutionService, never()).runVerification(any(), any(), any());
        verifyNoRailwayInteractions(railwayGraphqlClient);
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

    private DeploymentReleaseEntity failedCoolifyVerificationRelease() {
        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setId("rel-coolify");
        release.setDeploymentId("dep-123");
        release.setDeploymentVersionId("ver-coolify");
        release.setStatus("APPLIED_VERIFICATION_FAILED");
        release.setProvisioningTarget("COOLIFY");
        release.setProvisioningStatus("DEPLOY_REQUESTED");
        release.setVerificationStatus("FAILED");
        release.setVerificationRunId("vrf-failed");
        release.setCurrentStepKey("run_verification");
        release.setCurrentStepDescription("Run post-deploy verification.");
        release.setUpdatedAt(Instant.now());
        release.setProvisioningDetailsJson("{}");
        return release;
    }

    private DeploymentVerificationRunEntity passedVerificationRun(DeploymentReleaseEntity release) {
        DeploymentVerificationRunEntity run = new DeploymentVerificationRunEntity();
        run.setId("vrf-passed");
        run.setDeploymentId(release.getDeploymentId());
        run.setReleaseId(release.getId());
        run.setDeploymentVersionId(release.getDeploymentVersionId());
        run.setVerificationType("MANUAL_RERUN");
        run.setStatus("PASSED");
        run.setSummaryMessage("Runtime verification passed after Coolify startup settled.");
        run.setChecksJson("[]");
        run.setCreatedAt(Instant.now());
        run.setCompletedAt(Instant.now());
        return run;
    }

    private DeploymentReleaseEntity staleMarketplaceDatasetSyncRelease(boolean withSummary) {
        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setId("rel-marketplace-sync");
        release.setDeploymentId("dep-123");
        release.setDeploymentVersionId("ver-123");
        release.setStatus("VERIFYING");
        release.setProvisioningTarget("RAILWAY_API");
        release.setProvisioningStatus("ACTIVE");
        release.setVerificationStatus("RUNNING");
        release.setCurrentStepKey("sync_marketplace_datasets");
        release.setCurrentStepDescription("Sync marketplace DATA plugin datasets.");
        release.setUpdatedAt(Instant.now().minus(Duration.ofMinutes(5)));
        release.setProvisioningDetailsJson(withSummary
            ? """
                {
                  "marketplaceDatasets": {
                    "datasetsCount": 2,
                    "syncedDatasets": 2,
                    "skippedDatasets": 0,
                    "handleRefs": ["starter", "elite"]
                  }
                }
                """
            : "{}");
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
