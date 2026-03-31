package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentAssignmentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentDraftEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVerificationRunEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProductionReadinessAreaSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProductionReadinessOwnerSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProductionReadinessScorecardSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSecretUsageSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSecurityGovernanceSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentServiceConfigModelSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTemplateSummary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentAssignmentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVerificationRunRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DeploymentProductionReadinessScorecardService {

    private final DeploymentServiceConfigModelService deploymentServiceConfigModelService;
    private final DeploymentSecretUsageService deploymentSecretUsageService;
    private final DeploymentSecurityGovernanceService deploymentSecurityGovernanceService;
    private final DeploymentSourceResolver deploymentSourceResolver;
    private final DeploymentVersionRepository deploymentVersionRepository;
    private final DeploymentReleaseRepository deploymentReleaseRepository;
    private final DeploymentVerificationRunRepository deploymentVerificationRunRepository;
    private final DeploymentAssignmentRepository deploymentAssignmentRepository;

    public DeploymentProductionReadinessScorecardService(DeploymentServiceConfigModelService deploymentServiceConfigModelService,
                                                         DeploymentSecretUsageService deploymentSecretUsageService,
                                                         DeploymentSecurityGovernanceService deploymentSecurityGovernanceService,
                                                         DeploymentSourceResolver deploymentSourceResolver,
                                                         DeploymentVersionRepository deploymentVersionRepository,
                                                         DeploymentReleaseRepository deploymentReleaseRepository,
                                                         DeploymentVerificationRunRepository deploymentVerificationRunRepository,
                                                         DeploymentAssignmentRepository deploymentAssignmentRepository) {
        this.deploymentServiceConfigModelService = deploymentServiceConfigModelService;
        this.deploymentSecretUsageService = deploymentSecretUsageService;
        this.deploymentSecurityGovernanceService = deploymentSecurityGovernanceService;
        this.deploymentSourceResolver = deploymentSourceResolver;
        this.deploymentVersionRepository = deploymentVersionRepository;
        this.deploymentReleaseRepository = deploymentReleaseRepository;
        this.deploymentVerificationRunRepository = deploymentVerificationRunRepository;
        this.deploymentAssignmentRepository = deploymentAssignmentRepository;
    }

    public DeploymentProductionReadinessScorecardSummary build(DeploymentEntity deployment,
                                                               DeploymentDraftEntity draft,
                                                               DeploymentTemplateSummary template) {
        DeploymentVersionEntity latestVersion = deploymentVersionRepository
            .findByDeploymentIdOrderByPublishedAtDesc(deployment.getId())
            .stream()
            .findFirst()
            .orElse(null);
        DeploymentReleaseEntity latestRelease = deploymentReleaseRepository
            .findTopByDeploymentIdOrderByCreatedAtDesc(deployment.getId())
            .orElse(null);
        DeploymentVerificationRunEntity latestVerification = deploymentVerificationRunRepository
            .findByDeploymentIdOrderByCreatedAtDesc(deployment.getId())
            .stream()
            .findFirst()
            .orElse(null);
        List<DeploymentAssignmentEntity> assignments = deploymentAssignmentRepository
            .findByDeploymentIdOrderByCreatedAtAsc(deployment.getId());

        DeploymentServiceConfigModelSummary serviceConfig = deploymentServiceConfigModelService.build(
            deployment,
            draft,
            latestVersion,
            template,
            deploymentSourceResolver.summarize(deployment)
        );
        DeploymentSecretUsageSummary secretUsage = deploymentSecretUsageService.build(deployment.getId(), draft);
        DeploymentSecurityGovernanceSummary security = deploymentSecurityGovernanceService.build(deployment, draft);

        DeploymentProductionReadinessAreaSummary configArea = configurationArea(serviceConfig);
        DeploymentProductionReadinessAreaSummary securityArea = securityArea(security, secretUsage);
        DeploymentProductionReadinessAreaSummary verificationArea = verificationArea(deployment, latestVerification, latestRelease);
        DeploymentProductionReadinessAreaSummary serviceHealthArea = serviceHealthArea(deployment, latestRelease);
        DeploymentProductionReadinessOwnerSummary ownership = ownership(assignments);
        DeploymentProductionReadinessAreaSummary ownershipArea = new DeploymentProductionReadinessAreaSummary(
            "ownership",
            "Operator ownership",
            ownership.status(),
            statusScore(ownership.status()),
            ownership.message()
        );

        List<DeploymentProductionReadinessAreaSummary> areas = List.of(
            configArea,
            securityArea,
            verificationArea,
            serviceHealthArea,
            ownershipArea
        );

        String overallStatus = areas.stream().anyMatch(area -> "BLOCKED".equals(area.status()))
            ? "BLOCKED"
            : areas.stream().anyMatch(area -> "WARNING".equals(area.status()))
                ? "ATTENTION"
                : "READY";
        int overallScore = (int) Math.round(
            areas.stream().mapToInt(DeploymentProductionReadinessAreaSummary::score).average().orElse(0)
        );
        String summaryMessage = switch (overallStatus) {
            case "READY" -> "This deployment currently meets the modeled production readiness checks for rollout and ownership.";
            case "ATTENTION" -> "This deployment has warning-level readiness gaps that should be reviewed before go-live.";
            default -> "This deployment has blocking readiness gaps and should not be treated as production-ready yet.";
        };

        return new DeploymentProductionReadinessScorecardSummary(
            deployment.getId(),
            deployment.getName(),
            deployment.getEnvironmentName(),
            overallStatus,
            overallScore,
            latestRelease == null ? "NOT_APPLIED" : latestRelease.getStatus(),
            latestVerification == null ? "NOT_RUN" : latestVerification.getStatus(),
            ownership,
            areas,
            summaryMessage
        );
    }

    private DeploymentProductionReadinessAreaSummary configurationArea(DeploymentServiceConfigModelSummary serviceConfig) {
        long blocked = serviceConfig.services().stream().filter(service -> "BLOCKED".equals(service.status())).count();
        long warning = serviceConfig.services().stream().filter(service -> "WARNING".equals(service.status())).count();
        String status = blocked > 0 ? "BLOCKED" : warning > 0 ? "WARNING" : "READY";
        long readyServices = serviceConfig.services().stream().filter(service -> "READY".equals(service.status())).count();
        String message = readyServices + "/" + serviceConfig.services().size()
            + " modeled services are ready. " + serviceConfig.summaryMessage();
        return new DeploymentProductionReadinessAreaSummary(
            "configuration",
            "Configuration completeness",
            status,
            statusScore(status),
            message
        );
    }

    private DeploymentProductionReadinessAreaSummary securityArea(DeploymentSecurityGovernanceSummary security,
                                                                  DeploymentSecretUsageSummary secretUsage) {
        boolean blocked = security.blockedCount() > 0
            || secretUsage.missingRequiredCount() > 0
            || secretUsage.literalRiskCount() > 0;
        boolean warning = security.warningCount() > 0;
        String status = blocked ? "BLOCKED" : warning ? "WARNING" : "READY";
        String message = blocked
            ? "Blocked security findings: " + security.blockedCount()
            + ", missing secrets: " + secretUsage.missingRequiredCount()
            + ", literal credential risks: " + secretUsage.literalRiskCount() + "."
            : warning
                ? security.summaryMessage()
                : "Security governance and secret hygiene checks are satisfied for the current deployment draft.";
        return new DeploymentProductionReadinessAreaSummary(
            "security",
            "Security posture",
            status,
            statusScore(status),
            message
        );
    }

    private DeploymentProductionReadinessAreaSummary verificationArea(DeploymentEntity deployment,
                                                                      DeploymentVerificationRunEntity latestVerification,
                                                                      DeploymentReleaseEntity latestRelease) {
        boolean inProgress = latestRelease != null && isReleaseInProgress(latestRelease);
        String status;
        String message;
        if (deployment.getActiveVersionId() == null) {
            status = "BLOCKED";
            message = "No active version is applied yet, so there is nothing production-grade to verify.";
        } else if (latestVerification == null) {
            status = "BLOCKED";
            message = "No verification run exists for the active deployment.";
        } else if ("PASSED".equalsIgnoreCase(latestVerification.getStatus())) {
            status = "READY";
            message = latestVerification.getSummaryMessage();
        } else if (inProgress || "RUNNING".equalsIgnoreCase(latestVerification.getStatus())) {
            status = "WARNING";
            message = "Verification is still running or the latest rollout is still in progress.";
        } else {
            status = "BLOCKED";
            message = latestVerification.getSummaryMessage();
        }
        return new DeploymentProductionReadinessAreaSummary(
            "verification",
            "Verification evidence",
            status,
            statusScore(status),
            message
        );
    }

    private DeploymentProductionReadinessAreaSummary serviceHealthArea(DeploymentEntity deployment,
                                                                       DeploymentReleaseEntity latestRelease) {
        String status;
        String message;
        if (blank(deployment.getRuntimeBaseUrl()) || blank(deployment.getConnectorBaseUrl())) {
            status = "BLOCKED";
            message = "Runtime and REST connector public URLs must both exist before the deployment can be considered live.";
        } else if (latestRelease == null) {
            status = "BLOCKED";
            message = "No release has been applied yet to establish a live deployment footprint.";
        } else if ("APPLIED_VERIFIED".equalsIgnoreCase(latestRelease.getStatus())) {
            status = "READY";
            message = "Runtime and REST connector are live and the latest release is applied and verified.";
        } else if (isReleaseInProgress(latestRelease) || "APPLIED_VERIFICATION_FAILED".equalsIgnoreCase(latestRelease.getStatus())) {
            status = "WARNING";
            message = latestRelease.getCurrentStepDescription() == null
                ? "The latest live deployment needs attention."
                : latestRelease.getCurrentStepDescription();
        } else {
            status = "BLOCKED";
            message = latestRelease.getErrorMessage() == null
                ? "The latest release is not healthy enough for production handoff."
                : latestRelease.getErrorMessage();
        }
        return new DeploymentProductionReadinessAreaSummary(
            "serviceHealth",
            "Live service health",
            status,
            statusScore(status),
            message
        );
    }

    private DeploymentProductionReadinessOwnerSummary ownership(List<DeploymentAssignmentEntity> assignments) {
        int adminCount = countRole(assignments, "DEPLOYMENT_ADMIN");
        int operatorCount = countRole(assignments, "DEPLOYMENT_OPERATOR");
        int editorCount = countRole(assignments, "DEPLOYMENT_EDITOR");
        int viewerCount = countRole(assignments, "DEPLOYMENT_VIEWER");
        int totalAssigned = assignments.size();

        String status;
        String message;
        if (adminCount == 0 || operatorCount == 0) {
            status = "BLOCKED";
            message = "At least one deployment admin and one deployment operator should be assigned before go-live.";
        } else if (totalAssigned < 2 || editorCount == 0) {
            status = "WARNING";
            message = "Core ownership exists, but editor and broader operational coverage are still thin.";
        } else {
            status = "READY";
            message = "Deployment ownership includes admin, operator, and editor coverage for production support.";
        }

        return new DeploymentProductionReadinessOwnerSummary(
            status,
            totalAssigned,
            adminCount,
            operatorCount,
            editorCount,
            viewerCount,
            message
        );
    }

    private int countRole(List<DeploymentAssignmentEntity> assignments, String role) {
        return (int) assignments.stream()
            .filter(assignment -> role.equalsIgnoreCase(assignment.getAssignmentRole()))
            .count();
    }

    private boolean isReleaseInProgress(DeploymentReleaseEntity release) {
        String status = normalizeStatus(release.getStatus());
        String provisioningStatus = normalizeStatus(release.getProvisioningStatus());
        String verificationStatus = normalizeStatus(release.getVerificationStatus());
        return List.of("APPLY_REQUESTED", "PRE_APPLY_VERIFYING", "PROVISIONING", "VERIFYING").contains(status)
            || List.of("QUEUED", "RUNNING").contains(provisioningStatus)
            || "RUNNING".equals(verificationStatus);
    }

    private int statusScore(String status) {
        return switch (status == null ? "" : status.trim().toUpperCase()) {
            case "READY" -> 100;
            case "WARNING", "ATTENTION" -> 70;
            default -> 35;
        };
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String normalizeStatus(String status) {
        return status == null ? "" : status.trim().toUpperCase();
    }
}
