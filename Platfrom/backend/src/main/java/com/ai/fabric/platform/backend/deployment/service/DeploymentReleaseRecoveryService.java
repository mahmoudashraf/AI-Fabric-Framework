package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.config.PlatformProvisioningProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class DeploymentReleaseRecoveryService {

    private static final Duration MIN_STALE_WINDOW = Duration.ofSeconds(45);

    private final DeploymentRepository deploymentRepository;
    private final DeploymentReleaseRepository releaseRepository;
    private final DeploymentReleaseExecutionService deploymentReleaseExecutionService;
    private final RailwayGraphqlClient railwayGraphqlClient;
    private final PlatformProvisioningProperties provisioningProperties;
    private final ObjectMapper objectMapper;

    public DeploymentReleaseRecoveryService(DeploymentRepository deploymentRepository,
                                            DeploymentReleaseRepository releaseRepository,
                                            DeploymentReleaseExecutionService deploymentReleaseExecutionService,
                                            RailwayGraphqlClient railwayGraphqlClient,
                                            PlatformProvisioningProperties provisioningProperties,
                                            ObjectMapper objectMapper) {
        this.deploymentRepository = deploymentRepository;
        this.releaseRepository = releaseRepository;
        this.deploymentReleaseExecutionService = deploymentReleaseExecutionService;
        this.railwayGraphqlClient = railwayGraphqlClient;
        this.provisioningProperties = provisioningProperties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public boolean reconcileLatestInProgressRelease(String deploymentId) {
        DeploymentEntity deployment = deploymentRepository.findByIdForUpdate(deploymentId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment not found: " + deploymentId));
        return reconcileLatestInProgressRelease(deployment, false);
    }

    @Transactional
    public boolean reconcileLatestInProgressRelease(String deploymentId, boolean force) {
        DeploymentEntity deployment = deploymentRepository.findByIdForUpdate(deploymentId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment not found: " + deploymentId));
        return reconcileLatestInProgressRelease(deployment, force);
    }

    @Transactional
    boolean reconcileLatestInProgressRelease(DeploymentEntity deployment) {
        return reconcileLatestInProgressRelease(deployment, false);
    }

    @Transactional
    boolean reconcileLatestInProgressRelease(DeploymentEntity deployment, boolean force) {
        DeploymentReleaseEntity latestRelease = releaseRepository
            .findTopByDeploymentIdOrderByCreatedAtDesc(deployment.getId())
            .orElse(null);
        if (latestRelease == null || !isRecoveryCandidate(latestRelease) || (!force && !isStale(latestRelease))) {
            return false;
        }

        String stepKey = latestRelease.getCurrentStepKey() == null ? "" : latestRelease.getCurrentStepKey().trim();
        if ("wait_for_active".equals(stepKey)) {
            return reconcileRailwayProvisioningWait(deployment, latestRelease);
        }
        if ("run_verification".equals(stepKey)) {
            return reconcileRailwayVerification(deployment, latestRelease);
        }
        if ("queue_release".equals(stepKey)) {
            return reconcileQueuedApply(deployment, latestRelease);
        }
        return false;
    }

    private boolean reconcileQueuedApply(DeploymentEntity deployment, DeploymentReleaseEntity release) {
        if (!StringUtils.hasText(release.getDeploymentVersionId())) {
            return false;
        }
        return deploymentReleaseExecutionService.tryDispatchApplyAsync(
            deployment.getId(),
            release.getDeploymentVersionId(),
            release.getId()
        );
    }

    private boolean reconcileRailwayProvisioningWait(DeploymentEntity deployment, DeploymentReleaseEntity release) {
        JsonNode details = readJson(release.getProvisioningDetailsJson());
        String runtimeDeploymentId = text(details.at("/railway/services/runtime/deploymentId"));
        String connectorDeploymentId = text(details.at("/railway/services/restConnector/deploymentId"));
        String runnerDeploymentId = text(details.at("/railway/services/vectorizationRunner/deploymentId"));
        if (!StringUtils.hasText(runtimeDeploymentId) || !StringUtils.hasText(connectorDeploymentId)) {
            return false;
        }

        RailwayGraphqlClient.RailwayDeploymentSummary runtimeDeployment = railwayGraphqlClient.getDeployment(runtimeDeploymentId);
        RailwayGraphqlClient.RailwayDeploymentSummary connectorDeployment = railwayGraphqlClient.getDeployment(connectorDeploymentId);
        RailwayGraphqlClient.RailwayDeploymentSummary runnerDeployment = StringUtils.hasText(runnerDeploymentId)
            ? railwayGraphqlClient.getDeployment(runnerDeploymentId)
            : null;
        String runtimeStatus = normalizeStatus(runtimeDeployment.status());
        String connectorStatus = normalizeStatus(connectorDeployment.status());
        String runnerStatus = runnerDeployment == null ? null : normalizeStatus(runnerDeployment.status());

        if (isFailureStatus(runtimeStatus) || isFailureStatus(connectorStatus) || isFailureStatus(runnerStatus)) {
            String message = "Recovered stale Railway apply and found failed deployment state: runtime=" + runtimeStatus
                + ", restConnector=" + connectorStatus
                + (runnerStatus == null ? "" : ", vectorizationRunner=" + runnerStatus)
                + ".";
            deploymentReleaseExecutionService.markFailed(release.getId(), deployment.getId(), new IllegalStateException(message));
            return true;
        }
        if (!isSuccessStatus(runtimeStatus) || !isSuccessStatus(connectorStatus) || (runnerStatus != null && !isSuccessStatus(runnerStatus))) {
            return false;
        }

        ProvisioningResult recoveredResult = new ProvisioningResult(
            "ACTIVE",
            StringUtils.hasText(release.getProvisioningTarget()) ? release.getProvisioningTarget() : "RAILWAY_API",
            firstNonBlank(
                text(details.at("/railway/services/runtime/baseUrl")),
                deployment.getRuntimeBaseUrl()
            ),
            firstNonBlank(
                text(details.at("/railway/services/restConnector/baseUrl")),
                deployment.getConnectorBaseUrl()
            ),
            details.toPrettyString()
        );
        deploymentReleaseExecutionService.applyProvisioningResult(
            deployment.getId(),
            release.getDeploymentVersionId(),
            release.getId(),
            recoveredResult
        );
        deploymentReleaseExecutionService.runVerification(
            deployment.getId(),
            release.getDeploymentVersionId(),
            release.getId()
        );
        return true;
    }

    private boolean reconcileRailwayVerification(DeploymentEntity deployment, DeploymentReleaseEntity release) {
        if (!StringUtils.hasText(release.getDeploymentVersionId())) {
            return false;
        }
        if (!release.getDeploymentVersionId().equals(deployment.getActiveVersionId())) {
            JsonNode details = readJson(release.getProvisioningDetailsJson());
            ProvisioningResult recoveredResult = new ProvisioningResult(
                "ACTIVE",
                StringUtils.hasText(release.getProvisioningTarget()) ? release.getProvisioningTarget() : "RAILWAY_API",
                firstNonBlank(text(details.at("/railway/services/runtime/baseUrl")), deployment.getRuntimeBaseUrl()),
                firstNonBlank(text(details.at("/railway/services/restConnector/baseUrl")), deployment.getConnectorBaseUrl()),
                details.toPrettyString()
            );
            deploymentReleaseExecutionService.applyProvisioningResult(
                deployment.getId(),
                release.getDeploymentVersionId(),
                release.getId(),
                recoveredResult
            );
        }
        deploymentReleaseExecutionService.runVerification(
            deployment.getId(),
            release.getDeploymentVersionId(),
            release.getId()
        );
        return true;
    }

    private boolean isRecoveryCandidate(DeploymentReleaseEntity release) {
        if (!"RAILWAY_API".equalsIgnoreCase(release.getProvisioningTarget())) {
            return false;
        }
        return switch (release.getStatus()) {
            case "APPLY_REQUESTED" -> "queue_release".equals(release.getCurrentStepKey());
            case "PROVISIONING" -> "wait_for_active".equals(release.getCurrentStepKey());
            case "VERIFYING" -> "run_verification".equals(release.getCurrentStepKey());
            default -> false;
        };
    }

    private boolean isStale(DeploymentReleaseEntity release) {
        Instant lastUpdated = firstNonNull(release.getUpdatedAt(), release.getAppliedAt(), release.getCreatedAt());
        if (lastUpdated == null) {
            return true;
        }
        return lastUpdated.isBefore(Instant.now().minus(recoveryWindow()));
    }

    private Duration recoveryWindow() {
        Duration pollInterval = provisioningProperties.deploymentPollInterval() == null
            ? Duration.ofSeconds(5)
            : provisioningProperties.deploymentPollInterval();
        Duration derived = pollInterval.multipliedBy(6L);
        return derived.compareTo(MIN_STALE_WINDOW) >= 0 ? derived : MIN_STALE_WINDOW;
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json == null || json.isBlank() ? "{}" : json);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse deployment release provisioning details.", ex);
        }
    }

    private String normalizeStatus(String status) {
        return status == null ? "" : status.trim().toUpperCase();
    }

    private boolean isSuccessStatus(String status) {
        return "SUCCESS".equals(status) || "SLEEPING".equals(status);
    }

    private boolean isFailureStatus(String status) {
        return "FAILED".equals(status)
            || "CRASHED".equals(status)
            || "REMOVED".equals(status)
            || "CANCELED".equals(status);
    }

    private String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    @SafeVarargs
    private <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
