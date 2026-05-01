package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVerificationRunEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVerificationRunRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceDatasetSyncService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Service
public class DeploymentReleaseExecutionService {

    private static final Logger log = LoggerFactory.getLogger(DeploymentReleaseExecutionService.class);

    private final DeploymentRepository deploymentRepository;
    private final DeploymentVersionRepository versionRepository;
    private final DeploymentReleaseRepository releaseRepository;
    private final DeploymentVerificationRunRepository verificationRunRepository;
    private final DeploymentProvisioningService deploymentProvisioningService;
    private final DeploymentReleaseProgressService deploymentReleaseProgressService;
    private final DeploymentReleaseVerificationService deploymentReleaseVerificationService;
    private final DeploymentTenantScopedVectorService deploymentTenantScopedVectorService;
    private final DeploymentTenantScopedVectorRegistryService deploymentTenantScopedVectorRegistryService;
    private final MarketplaceDatasetSyncService marketplaceDatasetSyncService;
    private final Executor releaseExecutionExecutor;
    private final TransactionOperations transactionOperations;
    private final ObjectMapper objectMapper;

    @Autowired
    public DeploymentReleaseExecutionService(DeploymentRepository deploymentRepository,
                                             DeploymentVersionRepository versionRepository,
                                             DeploymentReleaseRepository releaseRepository,
                                             DeploymentVerificationRunRepository verificationRunRepository,
                                             DeploymentProvisioningService deploymentProvisioningService,
                                             DeploymentReleaseProgressService deploymentReleaseProgressService,
                                             DeploymentReleaseVerificationService deploymentReleaseVerificationService,
                                             DeploymentTenantScopedVectorService deploymentTenantScopedVectorService,
                                             DeploymentTenantScopedVectorRegistryService deploymentTenantScopedVectorRegistryService,
                                             MarketplaceDatasetSyncService marketplaceDatasetSyncService,
                                             @Qualifier("releaseExecutionExecutor") Executor releaseExecutionExecutor,
                                             PlatformTransactionManager transactionManager,
                                             ObjectMapper objectMapper) {
        this(
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
            releaseExecutionExecutor,
            new TransactionTemplate(transactionManager),
            objectMapper
        );
    }

    DeploymentReleaseExecutionService(DeploymentRepository deploymentRepository,
                                      DeploymentVersionRepository versionRepository,
                                      DeploymentReleaseRepository releaseRepository,
                                      DeploymentVerificationRunRepository verificationRunRepository,
                                      DeploymentProvisioningService deploymentProvisioningService,
                                      DeploymentReleaseProgressService deploymentReleaseProgressService,
                                      DeploymentReleaseVerificationService deploymentReleaseVerificationService,
                                      DeploymentTenantScopedVectorService deploymentTenantScopedVectorService,
                                      DeploymentTenantScopedVectorRegistryService deploymentTenantScopedVectorRegistryService,
                                      MarketplaceDatasetSyncService marketplaceDatasetSyncService,
                                      Executor releaseExecutionExecutor,
                                      TransactionOperations transactionOperations,
                                      ObjectMapper objectMapper) {
        this.deploymentRepository = deploymentRepository;
        this.versionRepository = versionRepository;
        this.releaseRepository = releaseRepository;
        this.verificationRunRepository = verificationRunRepository;
        this.deploymentProvisioningService = deploymentProvisioningService;
        this.deploymentReleaseProgressService = deploymentReleaseProgressService;
        this.deploymentReleaseVerificationService = deploymentReleaseVerificationService;
        this.deploymentTenantScopedVectorService = deploymentTenantScopedVectorService;
        this.deploymentTenantScopedVectorRegistryService = deploymentTenantScopedVectorRegistryService;
        this.marketplaceDatasetSyncService = marketplaceDatasetSyncService;
        this.releaseExecutionExecutor = releaseExecutionExecutor;
        this.transactionOperations = transactionOperations;
        this.objectMapper = objectMapper;
    }

    public void executeApply(String deploymentId, String versionId, String releaseId) {
        if (!tryDispatchApplyAsync(deploymentId, versionId, releaseId)) {
            log.warn(
                "Async apply dispatch rejected; falling back to inline execution: deploymentId={}, versionId={}, releaseId={}",
                deploymentId,
                versionId,
                releaseId
            );
            executeApplyInline(deploymentId, versionId, releaseId);
        }
    }

    boolean tryDispatchApplyAsync(String deploymentId, String versionId, String releaseId) {
        try {
            releaseExecutionExecutor.execute(() -> executeApplyInline(deploymentId, versionId, releaseId));
            return true;
        } catch (RuntimeException ex) {
            if (!(ex instanceof TaskRejectedException) && !(ex instanceof RejectedExecutionException)) {
                throw ex;
            }
            log.warn(
                "Async apply dispatch deferred because the release executor rejected the task: deploymentId={}, versionId={}, releaseId={}, message={}",
                deploymentId,
                versionId,
                releaseId,
                ex.getMessage()
            );
            return false;
        }
    }

    void executeApplyInline(String deploymentId, String versionId, String releaseId) {
        try {
            runApply(deploymentId, versionId, releaseId);
        } catch (RailwayActivationUnconfirmedException ex) {
            log.warn(
                "Async apply timed out before Railway activation could be confirmed: deploymentId={}, versionId={}, releaseId={}, message={}",
                deploymentId,
                versionId,
                releaseId,
                ex.getMessage()
            );
            transactionOperations.executeWithoutResult(status -> markActivationUnconfirmed(releaseId, deploymentId, ex));
        } catch (Exception ex) {
            log.error("Async apply failed: deploymentId={}, versionId={}, releaseId={}", deploymentId, versionId, releaseId, ex);
            transactionOperations.executeWithoutResult(status -> markFailed(releaseId, deploymentId, ex));
        }
    }

    protected void runApply(String deploymentId, String versionId, String releaseId) {
        boolean claimed = transactionOperations.execute(status -> claimQueuedApplyExecution(deploymentId, releaseId));
        if (!claimed) {
            log.info(
                "Skipping duplicate or stale apply execution because release is no longer queued: deploymentId={}, versionId={}, releaseId={}",
                deploymentId,
                versionId,
                releaseId
            );
            return;
        }

        DeploymentVerificationRunEntity preflightRun = runPreApplyVerification(deploymentId, versionId, releaseId);
        if (!"PASSED".equals(preflightRun.getStatus())) {
            transactionOperations.executeWithoutResult(
                status -> blockApplyForFailedPreflight(deploymentId, releaseId, preflightRun)
            );
            return;
        }

        transactionOperations.executeWithoutResult(status -> markProvisioningStarted(deploymentId, releaseId));

        DeploymentEntity deployment = getDeployment(deploymentId);
        DeploymentVersionEntity version = getVersion(versionId);
        DeploymentReleaseEntity release = getRelease(releaseId);

        ProvisioningResult provisioningResult = deploymentProvisioningService.provision(
            deployment,
            version,
            release,
            deploymentReleaseProgressService.tracker(releaseId)
        );

        applyProvisioningResult(deploymentId, versionId, releaseId, provisioningResult);
        syncMarketplaceDatasets(deploymentId, versionId, releaseId);
        runVerification(deploymentId, versionId, releaseId);
    }

    protected DeploymentVerificationRunEntity runPreApplyVerification(String deploymentId,
                                                                      String versionId,
                                                                      String releaseId) {
        DeploymentEntity deployment = getDeployment(deploymentId);
        DeploymentVersionEntity version = getVersion(versionId);
        DeploymentReleaseEntity release = getRelease(releaseId);
        DeploymentVerificationRunEntity verificationRun = deploymentReleaseVerificationService.verify(
            deployment,
            version,
            release,
            "PRE_APPLY"
        );
        return transactionOperations.execute(status -> completePreflightVerification(releaseId, verificationRun));
    }

    @Transactional
    protected boolean claimQueuedApplyExecution(String deploymentId,
                                                String releaseId) {
        DeploymentReleaseEntity release = releaseRepository.findByIdForUpdate(releaseId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Release not found: " + releaseId));
        if (!deploymentId.equals(release.getDeploymentId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Release does not belong to deployment: " + deploymentId);
        }
        if (!"APPLY_REQUESTED".equals(release.getStatus()) || !"queue_release".equals(release.getCurrentStepKey())) {
            return false;
        }
        deploymentReleaseProgressService.transition(
            releaseId,
            "PRE_APPLY_VERIFYING",
            "QUEUED",
            "RUNNING",
            "preflight_verification",
            "Running pre-apply verification gate.",
            null
        );
        DeploymentEntity deployment = getDeployment(deploymentId);
        deployment.setStatus("VERIFYING");
        deployment.setUpdatedAt(Instant.now());
        deploymentRepository.save(deployment);
        return true;
    }

    @Transactional
    protected DeploymentVerificationRunEntity completePreflightVerification(String releaseId,
                                                                            DeploymentVerificationRunEntity verificationRun) {
        verificationRunRepository.save(verificationRun);

        DeploymentReleaseEntity release = getRelease(releaseId);
        release.setVerificationRunId(verificationRun.getId());
        release.setVerificationStatus(verificationRun.getStatus());
        release.setUpdatedAt(Instant.now());
        releaseRepository.save(release);
        if ("PASSED".equals(verificationRun.getStatus())) {
            deploymentReleaseProgressService.stepCompleted(
                release.getId(),
                "preflight_verification",
                "Pre-apply verification gate passed."
            );
        }
        return verificationRun;
    }

    @Transactional
    protected void markProvisioningStarted(String deploymentId,
                                           String releaseId) {
        deploymentReleaseProgressService.transition(
            releaseId,
            "PROVISIONING",
            "RUNNING",
            "PENDING",
            "prepare_apply",
            "Preparing provisioning workflow.",
            null
        );
        DeploymentEntity deployment = getDeployment(deploymentId);
        deployment.setStatus("PROVISIONING");
        deployment.setUpdatedAt(Instant.now());
        deploymentRepository.save(deployment);
    }

    @Transactional
    protected void applyProvisioningResult(String deploymentId,
                                           String versionId,
                                           String releaseId,
                                           ProvisioningResult provisioningResult) {
        DeploymentEntity deployment = getDeployment(deploymentId);
        DeploymentVersionEntity version = getVersion(versionId);
        DeploymentReleaseEntity release = getRelease(releaseId);

        release.setProvisioningStatus(provisioningResult.status());
        release.setProvisioningTarget(provisioningResult.target());
        release.setProviderType(DeploymentProviderType.fromLegacyMode(provisioningResult.target()));
        release.setVerificationRunId(null);
        release.setUpdatedAt(Instant.now());
        releaseRepository.save(release);

        JsonNode providerConfig = readJson(version.getProviderConfigJson());
        deploymentTenantScopedVectorRegistryService.syncResolvedHandle(
            deployment,
            version,
            release,
            deploymentTenantScopedVectorService.build(deployment, providerConfig)
        );

        deploymentReleaseProgressService.mergeProvisioningDetails(releaseId, provisioningResult.detailsJson());
        deploymentReleaseProgressService.transition(
            releaseId,
            "VERIFYING",
            provisioningResult.status(),
            "RUNNING",
            "run_verification",
            "Running post-deploy verification.",
            null
        );

        deployment.setActiveVersionId(versionId);
        deployment.setRuntimeBaseUrl(provisioningResult.runtimeBaseUrl());
        deployment.setConnectorBaseUrl(provisioningResult.connectorBaseUrl());
        deployment.setStatus("VERIFYING");
        deployment.setUpdatedAt(Instant.now());
        deploymentRepository.save(deployment);
    }

    protected void runVerification(String deploymentId, String versionId, String releaseId) {
        DeploymentEntity deployment = getDeployment(deploymentId);
        DeploymentVersionEntity version = getVersion(versionId);
        DeploymentReleaseEntity release = getRelease(releaseId);

        DeploymentVerificationRunEntity verificationRun = deploymentReleaseVerificationService.verify(
            deployment,
            version,
            release,
            "POST_APPLY"
        );
        transactionOperations.executeWithoutResult(
            status -> completeVerification(deploymentId, releaseId, verificationRun)
        );
    }

    protected void syncMarketplaceDatasets(String deploymentId, String versionId, String releaseId) {
        DeploymentEntity deployment = getDeployment(deploymentId);
        DeploymentVersionEntity version = getVersion(versionId);
        DeploymentReleaseEntity release = getRelease(releaseId);
        deploymentReleaseProgressService.stepStarted(
            releaseId,
            "sync_marketplace_datasets",
            "Sync marketplace DATA plugin datasets."
        );
        MarketplaceDatasetSyncService.DatasetSyncSummary summary =
            marketplaceDatasetSyncService.syncReleaseDatasets(deployment, version, release);
        if (summary.datasetsCount() > 0) {
            deploymentReleaseProgressService.mergeProvisioningDetails(
                releaseId,
                writeJson(Map.of(
                    "marketplaceDatasets", Map.of(
                        "datasetsCount", summary.datasetsCount(),
                        "syncedDatasets", summary.syncedDatasets(),
                        "skippedDatasets", summary.skippedDatasets(),
                        "handleRefs", summary.handleRefs()
                    )
                ))
            );
        }
        deploymentReleaseProgressService.stepCompleted(
            releaseId,
            "sync_marketplace_datasets",
            summary.datasetsCount() > 0
                ? "Synchronized marketplace DATA plugin datasets."
                : "No marketplace DATA plugin datasets required synchronization."
        );
    }

    @Transactional
    protected void completeVerification(String deploymentId,
                                        String releaseId,
                                        DeploymentVerificationRunEntity verificationRun) {
        verificationRunRepository.save(verificationRun);

        DeploymentEntity deployment = getDeployment(deploymentId);
        DeploymentReleaseEntity release = getRelease(releaseId);
        release.setVerificationRunId(verificationRun.getId());
        release.setVerificationStatus(verificationRun.getStatus());
        release.setStatus("PASSED".equals(verificationRun.getStatus())
            ? "APPLIED_VERIFIED"
            : "APPLIED_VERIFICATION_FAILED");
        release.setCurrentStepKey("verification_complete");
        release.setCurrentStepDescription("Verification completed.");
        release.setErrorMessage(null);
        release.setUpdatedAt(Instant.now());
        releaseRepository.save(release);

        deploymentReleaseProgressService.stepCompleted(releaseId, "run_verification", "Run post-deploy verification against runtime and runtime-backed connector operational endpoints.");

        deployment.setStatus("PASSED".equals(verificationRun.getStatus()) ? "ACTIVE" : "VERIFICATION_FAILED");
        deployment.setUpdatedAt(Instant.now());
        deploymentRepository.save(deployment);
    }

    @Transactional
    protected void blockApplyForFailedPreflight(String deploymentId,
                                                String releaseId,
                                                DeploymentVerificationRunEntity verificationRun) {
        String message = verificationRun.getSummaryMessage();
        deploymentReleaseProgressService.stepFailed(
            releaseId,
            "preflight_verification",
            "Apply blocked by pre-apply verification.",
            message
        );
        deploymentReleaseProgressService.transition(
            releaseId,
            "PRE_APPLY_BLOCKED",
            "BLOCKED",
            verificationRun.getStatus(),
            "preflight_verification",
            "Apply blocked by pre-apply verification.",
            message
        );

        DeploymentReleaseEntity release = getRelease(releaseId);
        release.setVerificationRunId(verificationRun.getId());
        release.setVerificationStatus(verificationRun.getStatus());
        release.setProvisioningStatus("BLOCKED");
        release.setStatus("PRE_APPLY_BLOCKED");
        release.setCurrentStepKey("preflight_verification");
        release.setCurrentStepDescription("Apply blocked by pre-apply verification.");
        release.setErrorMessage(message);
        release.setUpdatedAt(Instant.now());
        releaseRepository.save(release);

        DeploymentEntity deployment = getDeployment(deploymentId);
        deployment.setStatus(deployment.getActiveVersionId() == null ? "VERSION_PUBLISHED" : "ACTIVE");
        deployment.setUpdatedAt(Instant.now());
        deploymentRepository.save(deployment);
    }

    @Transactional
    protected void markActivationUnconfirmed(String releaseId,
                                             String deploymentId,
                                             RailwayActivationUnconfirmedException ex) {
        String message = ex.getMessage() == null || ex.getMessage().isBlank()
            ? "Railway activation is not confirmed yet."
            : ex.getMessage();
        deploymentReleaseProgressService.transitionAwaitingConfirmation(
            releaseId,
            "PROVISIONING",
            "AWAITING_CONFIRMATION",
            "PENDING",
            "wait_for_active",
            "Deployment activation status is not confirmed yet. Railway may still be finishing startup.",
            message
        );

        DeploymentEntity deployment = getDeployment(deploymentId);
        deployment.setStatus("PROVISIONING");
        deployment.setUpdatedAt(Instant.now());
        deploymentRepository.save(deployment);
    }

    @Transactional
    protected void markFailed(String releaseId, String deploymentId, Exception ex) {
        String message = ex.getMessage() == null || ex.getMessage().isBlank()
            ? ex.getClass().getSimpleName()
            : ex.getMessage();
        deploymentReleaseProgressService.stepFailed(
            releaseId,
            "apply_failed",
            "Apply failed before completion.",
            message
        );
        deploymentReleaseProgressService.transition(
            releaseId,
            "FAILED",
            "FAILED",
            "SKIPPED",
            "apply_failed",
            "Apply failed before completion.",
            message
        );

        DeploymentEntity deployment = getDeployment(deploymentId);
        deployment.setStatus("APPLY_FAILED");
        deployment.setUpdatedAt(Instant.now());
        deploymentRepository.save(deployment);
    }

    private DeploymentEntity getDeployment(String deploymentId) {
        return deploymentRepository.findById(deploymentId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Deployment not found: " + deploymentId
            ));
    }

    private DeploymentVersionEntity getVersion(String versionId) {
        return versionRepository.findById(versionId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Version not found: " + versionId
            ));
    }

    private DeploymentReleaseEntity getRelease(String releaseId) {
        return releaseRepository.findById(releaseId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Release not found: " + releaseId
            ));
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json == null || json.isBlank() ? "{}" : json);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read deployment provider config during release execution.", ex);
        }
    }

    private String writeJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize deployment release execution details.", ex);
        }
    }
}
