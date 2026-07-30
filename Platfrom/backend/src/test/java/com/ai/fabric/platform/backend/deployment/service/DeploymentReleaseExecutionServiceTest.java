package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentProviderResourceHandleEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVerificationRunEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderResourceStatusSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTenantScopedVectorRegistrySummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTenantScopedVectorSummary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentProviderResourceHandleRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVerificationRunRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceDatasetSyncService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeploymentReleaseExecutionServiceTest {

    @Test
    void runApplyStopsBeforeProvisioningWhenPreflightVerificationFails() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentVersionRepository versionRepository = mock(DeploymentVersionRepository.class);
        DeploymentReleaseRepository releaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentVerificationRunRepository verificationRunRepository = mock(DeploymentVerificationRunRepository.class);
        DeploymentProvisioningService deploymentProvisioningService = mock(DeploymentProvisioningService.class);
        DeploymentReleaseProgressService deploymentReleaseProgressService = mock(DeploymentReleaseProgressService.class);
        DeploymentReleaseVerificationService deploymentReleaseVerificationService = mock(DeploymentReleaseVerificationService.class);
        DeploymentTenantScopedVectorService deploymentTenantScopedVectorService = mock(DeploymentTenantScopedVectorService.class);
        DeploymentTenantScopedVectorRegistryService deploymentTenantScopedVectorRegistryService = mock(DeploymentTenantScopedVectorRegistryService.class);
        MarketplaceDatasetSyncService marketplaceDatasetSyncService = mock(MarketplaceDatasetSyncService.class);
        when(marketplaceDatasetSyncService.syncReleaseDatasets(any(), any(), any()))
            .thenReturn(new MarketplaceDatasetSyncService.DatasetSyncSummary(0, 0, 0, List.of()));

        DeploymentReleaseExecutionService service = new DeploymentReleaseExecutionService(
            deploymentRepository,
            versionRepository,
            releaseRepository,
            verificationRunRepository,
            deploymentProvisioningService,
            deploymentReleaseProgressService,
            deploymentReleaseVerificationService,
            deploymentTenantScopedVectorService,
            deploymentTenantScopedVectorRegistryService,
            marketplaceDatasetSyncService,
            Runnable::run,
            TransactionOperations.withoutTransaction(),
            new ObjectMapper()
        );

        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-123");
        deployment.setName("Sample Commerce Dev");
        deployment.setEnvironmentName("dev");
        deployment.setTemplateId("dev-openai-lucene");
        deployment.setStatus("APPLY_REQUESTED");
        deployment.setActiveVersionId(null);
        deployment.setCreatedAt(Instant.parse("2026-03-31T00:00:00Z"));
        deployment.setUpdatedAt(Instant.parse("2026-03-31T00:00:00Z"));

        DeploymentVersionEntity version = new DeploymentVersionEntity();
        version.setId("ver-123");
        version.setDeploymentId("dep-123");
        version.setVersionLabel("v1");
        version.setStatus("PUBLISHED");
        version.setConfigHash("hash-123");
        version.setPublishedAt(Instant.parse("2026-03-31T00:00:00Z"));

        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setId("rel-123");
        release.setDeploymentId("dep-123");
        release.setDeploymentVersionId("ver-123");
        release.setStatus("APPLY_REQUESTED");
        release.setVerificationStatus("PENDING");
        release.setProvisioningStatus("QUEUED");
        release.setProvisioningTarget("RAILWAY_API");
        release.setCurrentStepKey("queue_release");
        release.setCurrentStepDescription("Apply request accepted and queued.");
        release.setProvisioningDetailsJson("{\"progress\":{\"steps\":[]}}");
        release.setCreatedAt(Instant.parse("2026-03-31T00:00:00Z"));
        release.setAppliedAt(Instant.parse("2026-03-31T00:00:00Z"));
        release.setUpdatedAt(Instant.parse("2026-03-31T00:00:00Z"));

        DeploymentVerificationRunEntity failedPreflight = new DeploymentVerificationRunEntity();
        failedPreflight.setId("vrf-123");
        failedPreflight.setDeploymentId("dep-123");
        failedPreflight.setReleaseId("rel-123");
        failedPreflight.setDeploymentVersionId("ver-123");
        failedPreflight.setVerificationType("PRE_APPLY");
        failedPreflight.setStatus("FAILED");
        failedPreflight.setSummaryMessage("2 passed, 1 failed, 0 skipped");
        failedPreflight.setChecksJson("[]");
        failedPreflight.setCreatedAt(Instant.parse("2026-03-31T00:00:00Z"));
        failedPreflight.setCompletedAt(Instant.parse("2026-03-31T00:00:01Z"));

        when(deploymentRepository.findById("dep-123")).thenReturn(Optional.of(deployment));
        when(versionRepository.findById("ver-123")).thenReturn(Optional.of(version));
        when(releaseRepository.findById("rel-123")).thenReturn(Optional.of(release));
        when(releaseRepository.findByIdForUpdate("rel-123")).thenReturn(Optional.of(release));
        when(deploymentRepository.save(any(DeploymentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(releaseRepository.save(any(DeploymentReleaseEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(verificationRunRepository.save(any(DeploymentVerificationRunEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(deploymentReleaseVerificationService.verify(deployment, version, release, "PRE_APPLY")).thenReturn(failedPreflight);

        service.runApply("dep-123", "ver-123", "rel-123");

        verify(deploymentProvisioningService, never()).provision(any(), any(), any(), any());
        assertThat(release.getStatus()).isEqualTo("PRE_APPLY_BLOCKED");
        assertThat(release.getProvisioningStatus()).isEqualTo("BLOCKED");
        assertThat(release.getVerificationStatus()).isEqualTo("FAILED");
        assertThat(release.getVerificationRunId()).isEqualTo("vrf-123");
        assertThat(deployment.getStatus()).isEqualTo("VERSION_PUBLISHED");
    }

    @Test
    void markActivationUnconfirmedKeepsReleaseRecoverable() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentVersionRepository versionRepository = mock(DeploymentVersionRepository.class);
        DeploymentReleaseRepository releaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentVerificationRunRepository verificationRunRepository = mock(DeploymentVerificationRunRepository.class);
        DeploymentProvisioningService deploymentProvisioningService = mock(DeploymentProvisioningService.class);
        DeploymentReleaseProgressService deploymentReleaseProgressService = new DeploymentReleaseProgressService(
            releaseRepository,
            new com.fasterxml.jackson.databind.ObjectMapper()
        );
        DeploymentReleaseVerificationService deploymentReleaseVerificationService = mock(DeploymentReleaseVerificationService.class);
        DeploymentTenantScopedVectorService deploymentTenantScopedVectorService = mock(DeploymentTenantScopedVectorService.class);
        DeploymentTenantScopedVectorRegistryService deploymentTenantScopedVectorRegistryService = mock(DeploymentTenantScopedVectorRegistryService.class);
        MarketplaceDatasetSyncService marketplaceDatasetSyncService = mock(MarketplaceDatasetSyncService.class);
        when(marketplaceDatasetSyncService.syncReleaseDatasets(any(), any(), any()))
            .thenReturn(new MarketplaceDatasetSyncService.DatasetSyncSummary(0, 0, 0, List.of()));

        DeploymentReleaseExecutionService service = new DeploymentReleaseExecutionService(
            deploymentRepository,
            versionRepository,
            releaseRepository,
            verificationRunRepository,
            deploymentProvisioningService,
            deploymentReleaseProgressService,
            deploymentReleaseVerificationService,
            deploymentTenantScopedVectorService,
            deploymentTenantScopedVectorRegistryService,
            marketplaceDatasetSyncService,
            Runnable::run,
            TransactionOperations.withoutTransaction(),
            new ObjectMapper()
        );

        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-123");
        deployment.setStatus("PROVISIONING");
        deployment.setCreatedAt(Instant.parse("2026-03-31T00:00:00Z"));
        deployment.setUpdatedAt(Instant.parse("2026-03-31T00:00:00Z"));

        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setId("rel-123");
        release.setDeploymentId("dep-123");
        release.setDeploymentVersionId("ver-123");
        release.setStatus("PROVISIONING");
        release.setVerificationStatus("PENDING");
        release.setProvisioningStatus("RUNNING");
        release.setCurrentStepKey("wait_for_active");
        release.setCurrentStepDescription("Wait for Railway deployment states to become active.");
        release.setProvisioningDetailsJson("""
            {
              "progress": {
                "currentStepKey": "wait_for_active",
                "currentStepDescription": "Wait for Railway deployment states to become active.",
                "currentStepStatus": "RUNNING",
                "steps": [
                  {
                    "key": "wait_for_active",
                    "description": "Wait for Railway deployment states to become active.",
                    "status": "RUNNING",
                    "startedAt": "2026-03-31T00:00:00Z"
                  }
                ]
              }
            }
            """);
        release.setCreatedAt(Instant.parse("2026-03-31T00:00:00Z"));
        release.setAppliedAt(Instant.parse("2026-03-31T00:00:00Z"));
        release.setUpdatedAt(Instant.parse("2026-03-31T00:00:00Z"));

        when(releaseRepository.findById("rel-123")).thenReturn(Optional.of(release));
        when(deploymentRepository.findById("dep-123")).thenReturn(Optional.of(deployment));
        when(releaseRepository.save(any(DeploymentReleaseEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(deploymentRepository.save(any(DeploymentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.markActivationUnconfirmed(
            "rel-123",
            "dep-123",
            new RailwayActivationUnconfirmedException("Timed out waiting for Railway deployment dep-123 to become active.")
        );

        assertThat(release.getStatus()).isEqualTo("PROVISIONING");
        assertThat(release.getProvisioningStatus()).isEqualTo("AWAITING_CONFIRMATION");
        assertThat(release.getVerificationStatus()).isEqualTo("PENDING");
        assertThat(release.getCurrentStepKey()).isEqualTo("wait_for_active");
        assertThat(release.getCurrentStepDescription()).contains("not confirmed yet");
        assertThat(release.getErrorMessage()).contains("Timed out waiting for Railway deployment");
        assertThat(release.getProvisioningDetailsJson()).contains("\"currentStepStatus\" : \"RUNNING\"");
        assertThat(deployment.getStatus()).isEqualTo("PROVISIONING");
    }

    @Test
    void applyProvisioningResultSyncsTenantScopedRegistryAfterSuccessfulProvisioning() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentVersionRepository versionRepository = mock(DeploymentVersionRepository.class);
        DeploymentReleaseRepository releaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentVerificationRunRepository verificationRunRepository = mock(DeploymentVerificationRunRepository.class);
        DeploymentProvisioningService deploymentProvisioningService = mock(DeploymentProvisioningService.class);
        DeploymentReleaseProgressService deploymentReleaseProgressService = mock(DeploymentReleaseProgressService.class);
        DeploymentReleaseVerificationService deploymentReleaseVerificationService = mock(DeploymentReleaseVerificationService.class);
        DeploymentTenantScopedVectorService deploymentTenantScopedVectorService = mock(DeploymentTenantScopedVectorService.class);
        DeploymentTenantScopedVectorRegistryService deploymentTenantScopedVectorRegistryService = mock(DeploymentTenantScopedVectorRegistryService.class);
        MarketplaceDatasetSyncService marketplaceDatasetSyncService = mock(MarketplaceDatasetSyncService.class);
        when(marketplaceDatasetSyncService.syncReleaseDatasets(any(), any(), any()))
            .thenReturn(new MarketplaceDatasetSyncService.DatasetSyncSummary(0, 0, 0, List.of()));

        DeploymentReleaseExecutionService service = new DeploymentReleaseExecutionService(
            deploymentRepository,
            versionRepository,
            releaseRepository,
            verificationRunRepository,
            deploymentProvisioningService,
            deploymentReleaseProgressService,
            deploymentReleaseVerificationService,
            deploymentTenantScopedVectorService,
            deploymentTenantScopedVectorRegistryService,
            marketplaceDatasetSyncService,
            Runnable::run,
            TransactionOperations.withoutTransaction(),
            new ObjectMapper()
        );

        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-123");
        deployment.setCustomerId("cust-acme");
        deployment.setTenantId("ten-retail");
        deployment.setStatus("PROVISIONING");
        deployment.setCreatedAt(Instant.parse("2026-03-31T00:00:00Z"));
        deployment.setUpdatedAt(Instant.parse("2026-03-31T00:00:00Z"));

        DeploymentVersionEntity version = new DeploymentVersionEntity();
        version.setId("ver-123");
        version.setDeploymentId("dep-123");
        version.setProviderConfigJson("{\"vectorStrategy\":\"pinecone\",\"vectorProvisioningMode\":\"EXTERNAL_EXISTING\",\"vectorStoragePosture\":\"SHARED\"}");

        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setId("rel-123");
        release.setDeploymentId("dep-123");
        release.setDeploymentVersionId("ver-123");
        release.setStatus("PROVISIONING");
        release.setCreatedAt(Instant.parse("2026-03-31T00:00:00Z"));
        release.setAppliedAt(Instant.parse("2026-03-31T00:00:00Z"));
        release.setUpdatedAt(Instant.parse("2026-03-31T00:00:00Z"));

        DeploymentTenantScopedVectorSummary tenantSummary = new DeploymentTenantScopedVectorSummary(
            "READY",
            "pinecone",
            "EXTERNAL_EXISTING",
            "SHARED",
            true,
            "CUSTOMER_MANAGED_EXTERNAL_RESOURCE",
            "cust-acme",
            "Acme",
            "ten-retail",
            "Retail",
            "NAMESPACE_PREFIX",
            "Index",
            "shared-index",
            "cust-acme--ten-retail",
            null,
            "cust-acme--ten-retail__<entity-type>",
            true,
            "Migration locked",
            "Backup posture",
            new DeploymentTenantScopedVectorRegistrySummary("WARNING", null, 0, 0, null, "INFO", "Pending cleanup.", "Pending apply"),
            "Summary"
        );

        when(deploymentRepository.findById("dep-123")).thenReturn(Optional.of(deployment));
        when(versionRepository.findById("ver-123")).thenReturn(Optional.of(version));
        when(releaseRepository.findById("rel-123")).thenReturn(Optional.of(release));
        when(releaseRepository.save(any(DeploymentReleaseEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(deploymentRepository.save(any(DeploymentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(deploymentTenantScopedVectorService.build(any(), any())).thenReturn(tenantSummary);

        ProvisioningResult provisioningResult = new ProvisioningResult(
            "RUNNING",
            "RAILWAY_API",
            "https://runtime.example",
            "https://connector.example",
            "{}"
        );

        service.applyProvisioningResult("dep-123", "ver-123", "rel-123", provisioningResult);

        verify(deploymentTenantScopedVectorRegistryService).syncResolvedHandle(deployment, version, release, tenantSummary);
        assertThat(deployment.getActiveVersionId()).isEqualTo("ver-123");
        assertThat(deployment.getRuntimeBaseUrl()).isEqualTo("https://runtime.example");
    }

    @Test
    void refreshProviderResourceHandlesAfterProvisioningPersistsFreshProviderStatus() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentVersionRepository versionRepository = mock(DeploymentVersionRepository.class);
        DeploymentReleaseRepository releaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentVerificationRunRepository verificationRunRepository = mock(DeploymentVerificationRunRepository.class);
        DeploymentProvisioningService deploymentProvisioningService = mock(DeploymentProvisioningService.class);
        DeploymentReleaseProgressService deploymentReleaseProgressService = mock(DeploymentReleaseProgressService.class);
        DeploymentReleaseVerificationService deploymentReleaseVerificationService = mock(DeploymentReleaseVerificationService.class);
        DeploymentTenantScopedVectorService deploymentTenantScopedVectorService = mock(DeploymentTenantScopedVectorService.class);
        DeploymentTenantScopedVectorRegistryService deploymentTenantScopedVectorRegistryService = mock(DeploymentTenantScopedVectorRegistryService.class);
        MarketplaceDatasetSyncService marketplaceDatasetSyncService = mock(MarketplaceDatasetSyncService.class);
        DeploymentProviderResourceHandleRepository resourceHandleRepository =
            mock(DeploymentProviderResourceHandleRepository.class);
        DeploymentProviderRegistry providerRegistry = mock(DeploymentProviderRegistry.class);
        DeploymentProvisioningProvider provider = mock(DeploymentProvisioningProvider.class);

        DeploymentReleaseExecutionService service = new DeploymentReleaseExecutionService(
            deploymentRepository,
            versionRepository,
            releaseRepository,
            verificationRunRepository,
            deploymentProvisioningService,
            deploymentReleaseProgressService,
            deploymentReleaseVerificationService,
            deploymentTenantScopedVectorService,
            deploymentTenantScopedVectorRegistryService,
            marketplaceDatasetSyncService,
            resourceHandleRepository,
            providerRegistry,
            Runnable::run,
            TransactionOperations.withoutTransaction(),
            new ObjectMapper()
        );

        DeploymentProviderResourceHandleEntity handle = providerHandle("dep-123", "rel-123", "exited:unhealthy");
        when(resourceHandleRepository.findByDeploymentIdOrderByUpdatedAtDesc("dep-123")).thenReturn(List.of(handle));
        when(providerRegistry.require(DeploymentProviderType.COOLIFY)).thenReturn(provider);
        when(provider.status(handle)).thenReturn(new DeploymentProviderResourceStatusSummary(
            handle.getId(),
            DeploymentProviderType.COOLIFY,
            handle.getProviderResourceUuid(),
            "ACTIVE",
            "running:healthy",
            "runtime.example.test",
            new ObjectMapper().createObjectNode(),
            Instant.parse("2026-03-31T00:00:01Z")
        ));
        when(resourceHandleRepository.save(any(DeploymentProviderResourceHandleEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        service.refreshProviderResourceHandlesAfterProvisioning("dep-123", "rel-123");

        verify(resourceHandleRepository).save(handle);
        assertThat(handle.getStatus()).isEqualTo("ACTIVE");
        assertThat(handle.getLastObservedStatus()).isEqualTo("running:healthy");
        assertThat(handle.getFqdn()).isEqualTo("runtime.example.test");
    }

    @Test
    void executeApplyFallsBackInlineWhenExecutorRejectsDispatch() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentVersionRepository versionRepository = mock(DeploymentVersionRepository.class);
        DeploymentReleaseRepository releaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentVerificationRunRepository verificationRunRepository = mock(DeploymentVerificationRunRepository.class);
        DeploymentProvisioningService deploymentProvisioningService = mock(DeploymentProvisioningService.class);
        DeploymentReleaseProgressService deploymentReleaseProgressService = mock(DeploymentReleaseProgressService.class);
        DeploymentReleaseVerificationService deploymentReleaseVerificationService = mock(DeploymentReleaseVerificationService.class);
        DeploymentTenantScopedVectorService deploymentTenantScopedVectorService = mock(DeploymentTenantScopedVectorService.class);
        DeploymentTenantScopedVectorRegistryService deploymentTenantScopedVectorRegistryService = mock(DeploymentTenantScopedVectorRegistryService.class);
        MarketplaceDatasetSyncService marketplaceDatasetSyncService = mock(MarketplaceDatasetSyncService.class);
        when(marketplaceDatasetSyncService.syncReleaseDatasets(any(), any(), any()))
            .thenReturn(new MarketplaceDatasetSyncService.DatasetSyncSummary(0, 0, 0, List.of()));

        DeploymentReleaseExecutionService service = new DeploymentReleaseExecutionService(
            deploymentRepository,
            versionRepository,
            releaseRepository,
            verificationRunRepository,
            deploymentProvisioningService,
            deploymentReleaseProgressService,
            deploymentReleaseVerificationService,
            deploymentTenantScopedVectorService,
            deploymentTenantScopedVectorRegistryService,
            marketplaceDatasetSyncService,
            task -> {
                throw new TaskRejectedException("executor saturated");
            },
            TransactionOperations.withoutTransaction(),
            new ObjectMapper()
        );

        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-123");
        deployment.setStatus("APPLY_REQUESTED");
        deployment.setCreatedAt(Instant.parse("2026-03-31T00:00:00Z"));
        deployment.setUpdatedAt(Instant.parse("2026-03-31T00:00:00Z"));

        DeploymentVersionEntity version = new DeploymentVersionEntity();
        version.setId("ver-123");
        version.setDeploymentId("dep-123");
        version.setProviderConfigJson("{\"provider\":\"pinecone\"}");

        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setId("rel-123");
        release.setDeploymentId("dep-123");
        release.setDeploymentVersionId("ver-123");
        release.setStatus("APPLY_REQUESTED");
        release.setVerificationStatus("PENDING");
        release.setProvisioningStatus("QUEUED");
        release.setProvisioningTarget("RAILWAY_API");
        release.setCurrentStepKey("queue_release");
        release.setCurrentStepDescription("Apply request accepted and queued.");
        release.setProvisioningDetailsJson("{}");
        release.setCreatedAt(Instant.parse("2026-03-31T00:00:00Z"));
        release.setAppliedAt(Instant.parse("2026-03-31T00:00:00Z"));
        release.setUpdatedAt(Instant.parse("2026-03-31T00:00:00Z"));

        DeploymentVerificationRunEntity preflight = new DeploymentVerificationRunEntity();
        preflight.setId("vrf-pre");
        preflight.setStatus("PASSED");
        preflight.setSummaryMessage("passed");

        DeploymentVerificationRunEntity postApply = new DeploymentVerificationRunEntity();
        postApply.setId("vrf-post");
        postApply.setStatus("PASSED");
        postApply.setSummaryMessage("passed");

        when(deploymentRepository.findById("dep-123")).thenReturn(Optional.of(deployment));
        when(versionRepository.findById("ver-123")).thenReturn(Optional.of(version));
        when(releaseRepository.findById("rel-123")).thenReturn(Optional.of(release));
        when(releaseRepository.findByIdForUpdate("rel-123")).thenReturn(Optional.of(release));
        when(deploymentRepository.save(any(DeploymentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(releaseRepository.save(any(DeploymentReleaseEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(verificationRunRepository.save(any(DeploymentVerificationRunEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(deploymentReleaseVerificationService.verify(deployment, version, release, "PRE_APPLY")).thenReturn(preflight);
        when(deploymentReleaseVerificationService.verify(deployment, version, release, "POST_APPLY")).thenReturn(postApply);
        when(deploymentProvisioningService.provision(any(), any(), any(), any())).thenReturn(
            new ProvisioningResult("ACTIVE", "RAILWAY_API", "https://runtime.example", "https://connector.example", "{}")
        );
        when(deploymentTenantScopedVectorService.build(any(), any())).thenReturn(
            new DeploymentTenantScopedVectorSummary(
                "READY",
                "pinecone",
                "EXTERNAL_EXISTING",
                "SHARED",
                true,
                "CUSTOMER_MANAGED_EXTERNAL_RESOURCE",
                "cust-acme",
                "Acme",
                "ten-retail",
                "Retail",
                "NAMESPACE_PREFIX",
                "Index",
                "shared-index",
                "cust-acme--ten-retail",
                null,
                "cust-acme--ten-retail__<entity-type>",
                true,
                "Migration locked",
                "Backup posture",
                new DeploymentTenantScopedVectorRegistrySummary("READY", null, 0, 0, null, "INFO", "Ready.", "Ready"),
                "Summary"
            )
        );

        service.executeApply("dep-123", "ver-123", "rel-123");

        verify(deploymentProvisioningService).provision(any(), any(), any(), any());
        verify(marketplaceDatasetSyncService).syncReleaseDatasets(deployment, version, release);
        assertThat(release.getStatus()).isEqualTo("APPLIED_VERIFIED");
        assertThat(deployment.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void runApplyNoopsWhenReleaseWasAlreadyClaimedByAnotherWorker() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentVersionRepository versionRepository = mock(DeploymentVersionRepository.class);
        DeploymentReleaseRepository releaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentVerificationRunRepository verificationRunRepository = mock(DeploymentVerificationRunRepository.class);
        DeploymentProvisioningService deploymentProvisioningService = mock(DeploymentProvisioningService.class);
        DeploymentReleaseProgressService deploymentReleaseProgressService = mock(DeploymentReleaseProgressService.class);
        DeploymentReleaseVerificationService deploymentReleaseVerificationService = mock(DeploymentReleaseVerificationService.class);
        DeploymentTenantScopedVectorService deploymentTenantScopedVectorService = mock(DeploymentTenantScopedVectorService.class);
        DeploymentTenantScopedVectorRegistryService deploymentTenantScopedVectorRegistryService = mock(DeploymentTenantScopedVectorRegistryService.class);
        MarketplaceDatasetSyncService marketplaceDatasetSyncService = mock(MarketplaceDatasetSyncService.class);
        when(marketplaceDatasetSyncService.syncReleaseDatasets(any(), any(), any()))
            .thenReturn(new MarketplaceDatasetSyncService.DatasetSyncSummary(0, 0, 0, List.of()));

        DeploymentReleaseExecutionService service = new DeploymentReleaseExecutionService(
            deploymentRepository,
            versionRepository,
            releaseRepository,
            verificationRunRepository,
            deploymentProvisioningService,
            deploymentReleaseProgressService,
            deploymentReleaseVerificationService,
            deploymentTenantScopedVectorService,
            deploymentTenantScopedVectorRegistryService,
            marketplaceDatasetSyncService,
            Runnable::run,
            TransactionOperations.withoutTransaction(),
            new ObjectMapper()
        );

        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setId("rel-123");
        release.setDeploymentId("dep-123");
        release.setDeploymentVersionId("ver-123");
        release.setStatus("PRE_APPLY_VERIFYING");
        release.setVerificationStatus("RUNNING");
        release.setProvisioningStatus("QUEUED");
        release.setCurrentStepKey("preflight_verification");
        release.setCurrentStepDescription("Running pre-apply verification gate.");

        when(releaseRepository.findByIdForUpdate("rel-123")).thenReturn(Optional.of(release));

        service.runApply("dep-123", "ver-123", "rel-123");

        verify(deploymentReleaseVerificationService, never()).verify(any(), any(), any(), any());
        verify(deploymentProvisioningService, never()).provision(any(), any(), any(), any());
        verify(deploymentRepository, never()).save(any(DeploymentEntity.class));
    }

    private static DeploymentProviderResourceHandleEntity providerHandle(String deploymentId,
                                                                         String releaseId,
                                                                         String status) {
        Instant now = Instant.parse("2026-03-31T00:00:00Z");
        DeploymentProviderResourceHandleEntity handle = new DeploymentProviderResourceHandleEntity();
        handle.setId("dpr-123");
        handle.setDeploymentId(deploymentId);
        handle.setReleaseId(releaseId);
        handle.setTargetProfileId("dtp-coolify");
        handle.setProviderType(DeploymentProviderType.COOLIFY);
        handle.setResourceKind("APPLICATION");
        handle.setProviderResourceUuid("coolify-app-123");
        handle.setStatus(status);
        handle.setLastObservedStatus(status);
        handle.setLastObservedAt(now);
        handle.setMetadataJson("{}");
        handle.setCreatedAt(now);
        handle.setUpdatedAt(now);
        return handle;
    }
}
