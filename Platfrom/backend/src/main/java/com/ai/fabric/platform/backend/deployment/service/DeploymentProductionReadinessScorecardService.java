package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentAssignmentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentDraftEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVerificationRunEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentManagedVectorStateSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProductionReadinessAreaSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProductionReadinessOwnerSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProductionReadinessScorecardSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSecretUsageSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSecurityGovernanceSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentServiceConfigModelSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTenantScopedVectorSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTemplateSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVectorizationVerificationSummary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentAssignmentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVerificationRunRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
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
    private final DeploymentManagedVectorResourceService deploymentManagedVectorResourceService;
    private final DeploymentTenantScopedVectorService deploymentTenantScopedVectorService;
    private final DeploymentVectorizationVerificationService deploymentVectorizationVerificationService;
    private final ObjectMapper objectMapper;

    public DeploymentProductionReadinessScorecardService(DeploymentServiceConfigModelService deploymentServiceConfigModelService,
                                                         DeploymentSecretUsageService deploymentSecretUsageService,
                                                         DeploymentSecurityGovernanceService deploymentSecurityGovernanceService,
                                                         DeploymentSourceResolver deploymentSourceResolver,
                                                         DeploymentVersionRepository deploymentVersionRepository,
                                                         DeploymentReleaseRepository deploymentReleaseRepository,
                                                         DeploymentVerificationRunRepository deploymentVerificationRunRepository,
                                                         DeploymentAssignmentRepository deploymentAssignmentRepository,
                                                         DeploymentManagedVectorResourceService deploymentManagedVectorResourceService,
                                                         DeploymentTenantScopedVectorService deploymentTenantScopedVectorService,
                                                         DeploymentVectorizationVerificationService deploymentVectorizationVerificationService,
                                                         ObjectMapper objectMapper) {
        this.deploymentServiceConfigModelService = deploymentServiceConfigModelService;
        this.deploymentSecretUsageService = deploymentSecretUsageService;
        this.deploymentSecurityGovernanceService = deploymentSecurityGovernanceService;
        this.deploymentSourceResolver = deploymentSourceResolver;
        this.deploymentVersionRepository = deploymentVersionRepository;
        this.deploymentReleaseRepository = deploymentReleaseRepository;
        this.deploymentVerificationRunRepository = deploymentVerificationRunRepository;
        this.deploymentAssignmentRepository = deploymentAssignmentRepository;
        this.deploymentManagedVectorResourceService = deploymentManagedVectorResourceService;
        this.deploymentTenantScopedVectorService = deploymentTenantScopedVectorService;
        this.deploymentVectorizationVerificationService = deploymentVectorizationVerificationService;
        this.objectMapper = objectMapper;
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
        DeploymentTenantScopedVectorSummary tenantScopedVector = deploymentTenantScopedVectorService.build(
            deployment,
            readJson(draft.getProviderConfigJson())
        );
        DeploymentVectorizationVerificationSummary vectorization = deploymentVectorizationVerificationService.build(
            deployment,
            latestVersion == null ? readJson(draft.getEntityConfigJson()) : readJson(latestVersion.getEntityConfigJson())
        );

        DeploymentProductionReadinessAreaSummary configArea = configurationArea(serviceConfig);
        DeploymentProductionReadinessAreaSummary securityArea = securityArea(security, secretUsage);
        DeploymentProductionReadinessAreaSummary providerConnectivityArea = providerConnectivityArea(draft, latestVerification);
        DeploymentProductionReadinessAreaSummary managedVectorArea = managedVectorArea(deployment, draft);
        DeploymentProductionReadinessAreaSummary tenantScopeArea = tenantScopeArea(tenantScopedVector);
        DeploymentProductionReadinessAreaSummary vectorizationArea = vectorizationArea(vectorization);
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
            providerConnectivityArea,
            managedVectorArea,
            tenantScopeArea,
            vectorizationArea,
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

    private DeploymentProductionReadinessAreaSummary tenantScopeArea(DeploymentTenantScopedVectorSummary tenantScopedVector) {
        String status = tenantScopedVector.status();
        if (tenantScopedVector.registry() != null) {
            if ("BLOCKED".equalsIgnoreCase(tenantScopedVector.registry().status())) {
                status = "BLOCKED";
            } else if ("WARNING".equalsIgnoreCase(tenantScopedVector.registry().status())
                && !"BLOCKED".equalsIgnoreCase(status)) {
                status = "WARNING";
            }
        }
        String message = tenantScopedVector.summaryMessage()
            + " "
            + tenantScopedVector.migrationMessage()
            + (tenantScopedVector.registry() == null ? "" : " " + tenantScopedVector.registry().message());
        return new DeploymentProductionReadinessAreaSummary(
            "tenantScopedVector",
            "Tenant-scoped vector scope",
            status,
            statusScore(status),
            message
        );
    }

    private DeploymentProductionReadinessAreaSummary vectorizationArea(DeploymentVectorizationVerificationSummary vectorization) {
        String status;
        String message;
        if (!vectorization.planPresent() && !vectorization.sourceConnectionPresent() && !vectorization.runnerPresent()) {
            status = "READY";
            message = "Vectorization is not configured for this deployment yet.";
        } else if (!vectorization.configured()) {
            status = "BLOCKED";
            message = "Vectorization control plane is partially configured. Source connection, active revision, and linked plan state must all be present.";
        } else if (vectorization.runnerRequired() && !vectorization.runnerPresent()) {
            status = "BLOCKED";
            message = "Vectorization is configured, but no eligible runner registration is present for the selected runner mode.";
        } else if (vectorization.runner() != null
            && !"ACTIVE".equalsIgnoreCase(vectorization.runner().registrationStatus())) {
            status = "BLOCKED";
            message = "Vectorization runner registration is not active.";
        } else if (vectorization.runner() != null
            && vectorization.runner().tokenExpiresAt() != null
            && vectorization.runner().tokenExpiresAt().isBefore(java.time.Instant.now())) {
            status = "BLOCKED";
            message = "Vectorization runner registration token has expired and must be rotated before execution.";
        } else if (vectorization.runnerRequired()
            && vectorization.runner() != null
            && vectorization.runner().lastConnectedAt() == null) {
            status = "WARNING";
            message = "Vectorization runner registration exists, but no runner instance has connected yet.";
        } else if (vectorization.runner() != null
            && "INCOMPATIBLE".equalsIgnoreCase(vectorization.runner().compatibilityStatus())) {
            status = "BLOCKED";
            message = "The latest vectorization runner is incompatible with the platform-required compatibility version.";
        } else if (vectorization.runner() != null
            && "OUTDATED".equalsIgnoreCase(vectorization.runner().compatibilityStatus())) {
            status = "WARNING";
            message = "The latest vectorization runner is connected, but it is running an outdated product version.";
        } else {
            String syncState = vectorization.plan() == null ? "" : normalizeStatus(vectorization.plan().syncState());
            status = switch (syncState) {
                case "IN_SYNC", "MANUALLY_CONFIRMED", "SOURCE_EMPTY" -> "READY";
                case "RUNNING", "REINDEX_DEFERRED", "OUT_OF_DATE", "BOOTSTRAP_REQUIRED" -> "WARNING";
                default -> "WARNING";
            };
            String runnerMode = vectorization.plan() == null ? "UNKNOWN" : vectorization.plan().runnerMode();
            String adapterType = vectorization.sourceConnection() == null ? "UNKNOWN" : vectorization.sourceConnection().adapterType();
            message = "Vectorization sync state is " + (syncState.isBlank() ? "UNKNOWN" : syncState.toLowerCase())
                + " using " + adapterType + " source connectivity and runner mode " + runnerMode + ".";
        }
        return new DeploymentProductionReadinessAreaSummary(
            "vectorization",
            "Vectorization layer",
            status,
            statusScore(status),
            message
        );
    }

    private DeploymentProductionReadinessAreaSummary providerConnectivityArea(DeploymentDraftEntity draft,
                                                                              DeploymentVerificationRunEntity latestVerification) {
        JsonNode providerConfig = readJson(draft.getProviderConfigJson());
        String vectorStrategy = ManagedDeploymentProfileCatalog.resolveVectorStrategy(providerConfig);
        String embeddingProvider = ManagedDeploymentProfileCatalog.resolveEmbeddingProvider(providerConfig);
        boolean requiresExternalVendorEvidence =
            ManagedDeploymentProfileCatalog.VECTOR_STRATEGY_PINECONE.equals(vectorStrategy)
                || ManagedDeploymentProfileCatalog.VECTOR_STRATEGY_QDRANT.equals(vectorStrategy)
                || ManagedDeploymentProfileCatalog.VECTOR_STRATEGY_WEAVIATE.equals(vectorStrategy)
                || ManagedDeploymentProfileCatalog.VECTOR_STRATEGY_MILVUS.equals(vectorStrategy)
                || ManagedDeploymentProfileCatalog.EMBEDDING_PROVIDER_REST.equals(embeddingProvider);

        if (!requiresExternalVendorEvidence) {
            return new DeploymentProductionReadinessAreaSummary(
                "providerConnectivity",
                "External provider connectivity",
                "READY",
                statusScore("READY"),
                "The current provider stack does not require external endpoint or vector-cluster connectivity evidence."
            );
        }
        if (latestVerification == null || !StringUtils.hasText(latestVerification.getChecksJson())) {
            return new DeploymentProductionReadinessAreaSummary(
                "providerConnectivity",
                "External provider connectivity",
                "BLOCKED",
                statusScore("BLOCKED"),
                "No verification evidence exists yet for the selected external provider or vector backend."
            );
        }

        JsonNode checks = readJson(latestVerification.getChecksJson());
        int passed = 0;
        int failed = 0;
        int skipped = 0;
        for (JsonNode check : checks) {
            String name = check.path("name").asText("");
            if (!name.startsWith("provider_connectivity_")) {
                continue;
            }
            String status = check.path("status").asText("");
            if ("PASSED".equals(status)) {
                passed += 1;
            } else if ("FAILED".equals(status)) {
                failed += 1;
            } else if ("SKIPPED".equals(status)) {
                skipped += 1;
            }
        }

        if (passed == 0 && failed == 0 && skipped == 0) {
            return new DeploymentProductionReadinessAreaSummary(
                "providerConnectivity",
                "External provider connectivity",
                "BLOCKED",
                statusScore("BLOCKED"),
                "The latest verification run did not record external provider connectivity checks for this provider stack."
            );
        }
        if (failed > 0) {
            return new DeploymentProductionReadinessAreaSummary(
                "providerConnectivity",
                "External provider connectivity",
                "BLOCKED",
                statusScore("BLOCKED"),
                passed + " passed, " + failed + " failed, " + skipped + " skipped external provider connectivity checks."
            );
        }
        if (skipped > 0) {
            return new DeploymentProductionReadinessAreaSummary(
                "providerConnectivity",
                "External provider connectivity",
                "WARNING",
                statusScore("WARNING"),
                passed + " passed and " + skipped + " were skipped; manual operator review is still required for non-probeable vendors."
            );
        }
        return new DeploymentProductionReadinessAreaSummary(
            "providerConnectivity",
            "External provider connectivity",
            "READY",
            statusScore("READY"),
            passed + " external provider connectivity checks passed for the current provider stack."
        );
    }

    private DeploymentProductionReadinessAreaSummary managedVectorArea(DeploymentEntity deployment,
                                                                       DeploymentDraftEntity draft) {
        DeploymentManagedVectorStateSummary managedVector = deploymentManagedVectorResourceService.buildStateSummary(
            deployment.getId(),
            readJson(draft.getProviderConfigJson()),
            readJson(draft.getEntityConfigJson()),
            deployment.getActiveVersionId()
        );
        String status = switch (managedVector.status()) {
            case "BLOCKED" -> "BLOCKED";
            case "WARNING" -> "WARNING";
            case "READY" -> "READY";
            default -> "READY";
        };
        return new DeploymentProductionReadinessAreaSummary(
            "managedVector",
            "Managed vector resources",
            status,
            statusScore(status),
            managedVector.summaryMessage()
        );
    }

    private DeploymentProductionReadinessAreaSummary serviceHealthArea(DeploymentEntity deployment,
                                                                       DeploymentReleaseEntity latestRelease) {
        String status;
        String message;
        if (blank(deployment.getRuntimeBaseUrl()) || blank(deployment.getConnectorBaseUrl())) {
            status = "BLOCKED";
            message = "Runtime and REST connector service URLs must both exist before the deployment can be considered live.";
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

    private JsonNode readJson(String value) {
        try {
            return value == null || value.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(value);
        } catch (IOException ex) {
            return objectMapper.createObjectNode();
        }
    }
}
