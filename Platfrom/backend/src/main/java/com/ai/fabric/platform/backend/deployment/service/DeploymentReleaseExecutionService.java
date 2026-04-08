package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVerificationRunEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVerificationRunRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

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
    private final ObjectMapper objectMapper;

    public DeploymentReleaseExecutionService(DeploymentRepository deploymentRepository,
                                             DeploymentVersionRepository versionRepository,
                                             DeploymentReleaseRepository releaseRepository,
                                             DeploymentVerificationRunRepository verificationRunRepository,
                                             DeploymentProvisioningService deploymentProvisioningService,
                                             DeploymentReleaseProgressService deploymentReleaseProgressService,
                                             DeploymentReleaseVerificationService deploymentReleaseVerificationService,
                                             DeploymentTenantScopedVectorService deploymentTenantScopedVectorService,
                                             DeploymentTenantScopedVectorRegistryService deploymentTenantScopedVectorRegistryService,
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
        this.objectMapper = objectMapper;
    }

    @Async("releaseExecutionExecutor")
    public void executeApply(String deploymentId, String versionId, String releaseId) {
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
            markActivationUnconfirmed(releaseId, deploymentId, ex);
        } catch (Exception ex) {
            log.error("Async apply failed: deploymentId={}, versionId={}, releaseId={}", deploymentId, versionId, releaseId, ex);
            markFailed(releaseId, deploymentId, ex);
        }
    }

    @Transactional
    protected void runApply(String deploymentId, String versionId, String releaseId) {
        DeploymentEntity deployment = getDeployment(deploymentId);
        DeploymentVersionEntity version = getVersion(versionId);
        DeploymentReleaseEntity release = getRelease(releaseId);

        DeploymentVerificationRunEntity preflightRun = runPreApplyVerification(deployment, version, release);
        if (!"PASSED".equals(preflightRun.getStatus())) {
            blockApplyForFailedPreflight(deployment, release, preflightRun);
            return;
        }

        deploymentReleaseProgressService.transition(
            releaseId,
            "PROVISIONING",
            "RUNNING",
            "PENDING",
            "prepare_apply",
            "Preparing provisioning workflow.",
            null
        );
        deployment.setStatus("PROVISIONING");
        deployment.setUpdatedAt(Instant.now());
        deploymentRepository.save(deployment);

        ProvisioningResult provisioningResult = deploymentProvisioningService.provision(
            deployment,
            version,
            release,
            deploymentReleaseProgressService.tracker(releaseId)
        );

        applyProvisioningResult(deploymentId, versionId, releaseId, provisioningResult);
        runVerification(deploymentId, versionId, releaseId);
    }

    @Transactional
    protected DeploymentVerificationRunEntity runPreApplyVerification(DeploymentEntity deployment,
                                                                      DeploymentVersionEntity version,
                                                                      DeploymentReleaseEntity release) {
        deploymentReleaseProgressService.transition(
            release.getId(),
            "PRE_APPLY_VERIFYING",
            "QUEUED",
            "RUNNING",
            "preflight_verification",
            "Running pre-apply verification gate.",
            null
        );
        deployment.setStatus("VERIFYING");
        deployment.setUpdatedAt(Instant.now());
        deploymentRepository.save(deployment);

        DeploymentVerificationRunEntity verificationRun = deploymentReleaseVerificationService.verify(
            deployment,
            version,
            release,
            "PRE_APPLY"
        );
        verificationRunRepository.save(verificationRun);

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
    protected void applyProvisioningResult(String deploymentId,
                                           String versionId,
                                           String releaseId,
                                           ProvisioningResult provisioningResult) {
        DeploymentEntity deployment = getDeployment(deploymentId);
        DeploymentVersionEntity version = getVersion(versionId);
        DeploymentReleaseEntity release = getRelease(releaseId);

        release.setProvisioningStatus(provisioningResult.status());
        release.setProvisioningTarget(provisioningResult.target());
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

    @Transactional
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
        verificationRunRepository.save(verificationRun);

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
    protected void blockApplyForFailedPreflight(DeploymentEntity deployment,
                                                DeploymentReleaseEntity release,
                                                DeploymentVerificationRunEntity verificationRun) {
        String message = verificationRun.getSummaryMessage();
        deploymentReleaseProgressService.stepFailed(
            release.getId(),
            "preflight_verification",
            "Apply blocked by pre-apply verification.",
            message
        );
        deploymentReleaseProgressService.transition(
            release.getId(),
            "PRE_APPLY_BLOCKED",
            "BLOCKED",
            verificationRun.getStatus(),
            "preflight_verification",
            "Apply blocked by pre-apply verification.",
            message
        );

        release.setVerificationRunId(verificationRun.getId());
        release.setVerificationStatus(verificationRun.getStatus());
        release.setProvisioningStatus("BLOCKED");
        release.setStatus("PRE_APPLY_BLOCKED");
        release.setCurrentStepKey("preflight_verification");
        release.setCurrentStepDescription("Apply blocked by pre-apply verification.");
        release.setErrorMessage(message);
        release.setUpdatedAt(Instant.now());
        releaseRepository.save(release);

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
}
