package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.PlatformProvisioningProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPocRuntimeResetRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPocRuntimeResetResponse;
import com.ai.fabric.platform.backend.deployment.model.DeploymentRemediationActionSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentRemediationExecutionSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentRemediationSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentReleaseSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVerificationRunSummary;
import com.ai.fabric.platform.backend.deployment.model.ExecuteDeploymentRemediationRequest;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class DeploymentRemediationService {

    static final String RERUN_VERIFICATION = "RERUN_VERIFICATION";
    static final String REDEPLOY_ACTIVE_VERSION = "REDEPLOY_ACTIVE_VERSION";
    static final String RESTART_RUNTIME_SERVICE = "RESTART_RUNTIME_SERVICE";
    static final String RESTART_REST_CONNECTOR_SERVICE = "RESTART_REST_CONNECTOR_SERVICE";
    static final String RESET_RUNTIME_VECTORS = "RESET_RUNTIME_VECTORS";
    static final String ARCHIVE_DEPLOYMENT = "ARCHIVE_DEPLOYMENT";
    static final String RESTORE_DEPLOYMENT = "RESTORE_DEPLOYMENT";
    static final String DELETE_DEPLOYMENT = "DELETE_DEPLOYMENT";

    private final DeploymentRepository deploymentRepository;
    private final DeploymentVersionRepository deploymentVersionRepository;
    private final DeploymentReleaseRepository deploymentReleaseRepository;
    private final DeploymentAccessService deploymentAccessService;
    private final DeploymentService deploymentService;
    private final DeploymentPocWorkspaceService deploymentPocWorkspaceService;
    private final RailwayGraphqlClient railwayGraphqlClient;
    private final PlatformProvisioningProperties provisioningProperties;
    private final PlatformSecretService platformSecretService;
    private final PlatformAuditService platformAuditService;
    private final ObjectMapper objectMapper;

    public DeploymentRemediationService(DeploymentRepository deploymentRepository,
                                        DeploymentVersionRepository deploymentVersionRepository,
                                        DeploymentReleaseRepository deploymentReleaseRepository,
                                        DeploymentAccessService deploymentAccessService,
                                        DeploymentService deploymentService,
                                        DeploymentPocWorkspaceService deploymentPocWorkspaceService,
                                        RailwayGraphqlClient railwayGraphqlClient,
                                        PlatformProvisioningProperties provisioningProperties,
                                        PlatformSecretService platformSecretService,
                                        PlatformAuditService platformAuditService,
                                        ObjectMapper objectMapper) {
        this.deploymentRepository = deploymentRepository;
        this.deploymentVersionRepository = deploymentVersionRepository;
        this.deploymentReleaseRepository = deploymentReleaseRepository;
        this.deploymentAccessService = deploymentAccessService;
        this.deploymentService = deploymentService;
        this.deploymentPocWorkspaceService = deploymentPocWorkspaceService;
        this.railwayGraphqlClient = railwayGraphqlClient;
        this.provisioningProperties = provisioningProperties;
        this.platformSecretService = platformSecretService;
        this.platformAuditService = platformAuditService;
        this.objectMapper = objectMapper;
    }

    public DeploymentRemediationSummary getSummary(String deploymentId) {
        DeploymentEntity deployment = getDeployment(deploymentId);
        deploymentAccessService.requireDeploymentAccess(deployment);
        DeploymentReleaseEntity latestRelease = latestRelease(deploymentId);
        DeploymentVersionEntity activeVersion = activeVersion(deployment);
        JsonNode details = readJson(latestRelease == null ? null : latestRelease.getProvisioningDetailsJson());
        List<DeploymentRemediationActionSummary> actions = buildActions(
            deployment,
            activeVersion,
            latestRelease,
            details
        );
        long availableCount = actions.stream().filter(DeploymentRemediationActionSummary::available).count();
        String summaryMessage = availableCount > 0
            ? availableCount + " governed remediation action(s) are currently available for this deployment."
            : "No governed remediation actions are currently available for this deployment state.";
        return new DeploymentRemediationSummary(
            deployment.getId(),
            deployment.getName(),
            deployment.getEnvironmentName(),
            actions,
            summaryMessage
        );
    }

    @Transactional
    public DeploymentRemediationExecutionSummary execute(String deploymentId,
                                                         String actionKey,
                                                         ExecuteDeploymentRemediationRequest request) {
        DeploymentEntity deployment = getDeployment(deploymentId);
        DeploymentReleaseEntity latestRelease = latestRelease(deploymentId);
        DeploymentVersionEntity activeVersion = activeVersion(deployment);
        JsonNode details = readJson(latestRelease == null ? null : latestRelease.getProvisioningDetailsJson());
        String normalizedAction = normalizeActionKey(actionKey);

        return switch (normalizedAction) {
            case RERUN_VERIFICATION -> rerunVerification(deployment);
            case REDEPLOY_ACTIVE_VERSION -> redeployActiveVersion(deployment, activeVersion, request);
            case RESTART_RUNTIME_SERVICE -> restartRailwayService(
                deployment,
                latestRelease,
                details,
                request,
                "runtime",
                RESTART_RUNTIME_SERVICE,
                "REMEDIATION_RUNTIME_RESTART_TRIGGERED",
                "Runtime Railway restart triggered."
            );
            case RESTART_REST_CONNECTOR_SERVICE -> restartRailwayService(
                deployment,
                latestRelease,
                details,
                request,
                "restConnector",
                RESTART_REST_CONNECTOR_SERVICE,
                "REMEDIATION_REST_CONNECTOR_RESTART_TRIGGERED",
                "REST connector Railway restart triggered."
            );
            case RESET_RUNTIME_VECTORS -> resetRuntimeVectors(deployment, request);
            case ARCHIVE_DEPLOYMENT -> archiveDeployment(deployment, request);
            case RESTORE_DEPLOYMENT -> restoreDeployment(deployment);
            case DELETE_DEPLOYMENT -> deleteDeployment(deployment, request);
            default -> throw new ResponseStatusException(BAD_REQUEST, "Unsupported remediation action: " + actionKey);
        };
    }

    private List<DeploymentRemediationActionSummary> buildActions(DeploymentEntity deployment,
                                                                  DeploymentVersionEntity activeVersion,
                                                                  DeploymentReleaseEntity latestRelease,
                                                                  JsonNode details) {
        boolean archived = isArchived(deployment);
        boolean releaseInProgress = latestRelease != null && isReleaseInProgress(latestRelease);
        var access = deploymentAccessService.summarizeAccess(deployment);
        boolean canOperate = access.canOperate();
        boolean canAdmin = access.canAdmin();
        boolean hasRuntimeAdminSecret = platformSecretService.isSecretPresent("APP_ADMIN_API_KEY");
        boolean railwayApiMode = "RAILWAY_API".equalsIgnoreCase(latestRelease != null && hasText(latestRelease.getProvisioningTarget())
            ? latestRelease.getProvisioningTarget()
            : provisioningProperties.mode());

        List<DeploymentRemediationActionSummary> actions = new ArrayList<>();
        actions.add(action(
            RERUN_VERIFICATION,
            "Rerun verification",
            "Run the deployment verification suite again against the current active release.",
            "VERIFICATION",
            "LOW",
            "DEPLOYMENT_OPERATOR",
            canOperate && !archived && activeVersion != null && !releaseInProgress,
            false,
            false,
            false,
            blockedReason(
                canOperate,
                !archived,
                activeVersion != null,
                !releaseInProgress,
                "Operator access or higher is required.",
                "Archived deployments cannot be reverified.",
                "Apply a version before rerunning verification.",
                "Wait for the current release to finish before rerunning verification."
            ),
            "This runs verification only and does not change deployment configuration."
        ));
        actions.add(action(
            REDEPLOY_ACTIVE_VERSION,
            "Redeploy active version",
            "Launch a fresh rollout of the currently active published version.",
            "ROLLOUT",
            "WARNING",
            "DEPLOYMENT_OPERATOR",
            canOperate && !archived && activeVersion != null && !releaseInProgress,
            true,
            true,
            deployment.isApprovalRequiredForApply(),
            blockedReason(
                canOperate,
                !archived,
                activeVersion != null,
                !releaseInProgress,
                "Operator access or higher is required.",
                "Archived deployments cannot be redeployed.",
                "No active version exists to redeploy.",
                "Wait for the current release to finish before launching another rollout."
            ),
            "This creates a new apply request for the active version and may rotate live service deployments."
        ));
        actions.add(action(
            RESTART_RUNTIME_SERVICE,
            "Restart runtime service",
            "Trigger a provider-side restart of the runtime service without changing config artifacts.",
            "SERVICE_RECOVERY",
            "WARNING",
            "DEPLOYMENT_ADMIN",
            canAdmin && !archived && !releaseInProgress && railwayApiMode && hasText(text(details, "railway", "environmentId")) && hasText(text(details, "railway", "services", "runtime", "serviceId")),
            true,
            true,
            false,
            blockedReason(
                canAdmin,
                !archived,
                railwayApiMode,
                !releaseInProgress && hasText(text(details, "railway", "environmentId")) && hasText(text(details, "railway", "services", "runtime", "serviceId")),
                "Deployment admin access is required for provider-side service restarts.",
                "Archived deployments cannot trigger provider-side restarts.",
                "Railway API mode is required for direct service restarts.",
                "A completed Railway-managed release with service ids is required before runtime restart is available."
            ),
            "This triggers a live Railway service deployment for runtime and should be used only for service-level recovery."
        ));
        actions.add(action(
            RESTART_REST_CONNECTOR_SERVICE,
            "Restart REST connector service",
            "Trigger a provider-side restart of the REST connector without changing config artifacts.",
            "SERVICE_RECOVERY",
            "WARNING",
            "DEPLOYMENT_ADMIN",
            canAdmin && !archived && !releaseInProgress && railwayApiMode && hasText(text(details, "railway", "environmentId")) && hasText(text(details, "railway", "services", "restConnector", "serviceId")),
            true,
            true,
            false,
            blockedReason(
                canAdmin,
                !archived,
                railwayApiMode,
                !releaseInProgress && hasText(text(details, "railway", "environmentId")) && hasText(text(details, "railway", "services", "restConnector", "serviceId")),
                "Deployment admin access is required for provider-side service restarts.",
                "Archived deployments cannot trigger provider-side restarts.",
                "Railway API mode is required for direct service restarts.",
                "A completed Railway-managed release with service ids is required before connector restart is available."
            ),
            "This triggers a live Railway service deployment for the REST connector and should be reserved for targeted recovery."
        ));
        actions.add(action(
            RESET_RUNTIME_VECTORS,
            "Reset runtime vectors",
            "Clear live runtime vectors through the governed admin path used by the POC tooling.",
            "DATA_RESET",
            "ERROR",
            "DEPLOYMENT_OPERATOR",
            canOperate && !archived && hasText(deployment.getRuntimeBaseUrl()) && hasRuntimeAdminSecret,
            true,
            true,
            false,
            blockedReason(
                canOperate,
                !archived,
                hasText(deployment.getRuntimeBaseUrl()),
                hasRuntimeAdminSecret,
                "Operator access or higher is required.",
                "Archived deployments cannot clear runtime vectors.",
                "Apply the deployment before attempting a runtime vector reset.",
                "APP_ADMIN_API_KEY must be configured in platform secrets before runtime reset is allowed."
            ),
            "This clears live vector state and should only be used when reloading or recovering indexed data."
        ));
        actions.add(action(
            ARCHIVE_DEPLOYMENT,
            "Archive deployment",
            "Take the deployment out of the active operating set without deleting its history.",
            "GOVERNANCE",
            "WARNING",
            "DEPLOYMENT_ADMIN",
            canAdmin && !archived && !releaseInProgress,
            true,
            true,
            false,
            blockedReason(
                canAdmin,
                !archived,
                true,
                !releaseInProgress,
                "Deployment admin access is required to archive deployments.",
                "Deployment is already archived.",
                null,
                "Wait for the current release to finish before archiving the deployment."
            ),
            "Archiving removes the deployment from normal active operations but preserves history and drafts."
        ));
        actions.add(action(
            RESTORE_DEPLOYMENT,
            "Restore deployment",
            "Return an archived deployment to the active operating set.",
            "GOVERNANCE",
            "LOW",
            "DEPLOYMENT_ADMIN",
            canAdmin && archived && !releaseInProgress,
            false,
            false,
            false,
            blockedReason(
                canAdmin,
                archived,
                true,
                !releaseInProgress,
                "Deployment admin access is required to restore deployments.",
                "Deployment is not archived.",
                null,
                "Wait for the current release to finish before restoring the deployment."
            ),
            "Restoring reopens the deployment for normal operations and configuration work."
        ));
        actions.add(action(
            DELETE_DEPLOYMENT,
            "Delete deployment",
            "Permanently remove an archived deployment after approvals and operator confirmation.",
            "DESTRUCTIVE",
            "ERROR",
            "DEPLOYMENT_ADMIN",
            canAdmin && archived && !releaseInProgress,
            true,
            true,
            deployment.isApprovalRequiredForDelete(),
            blockedReason(
                canAdmin,
                archived,
                true,
                !releaseInProgress,
                "Deployment admin access is required to delete deployments.",
                "Deployment must be archived before deletion is allowed.",
                null,
                "Wait for the current release to finish before deleting the deployment."
            ),
            "Deletion is irreversible. Archive first, then provide the required approval id if delete guardrails are enabled."
        ));
        return actions;
    }

    private DeploymentRemediationExecutionSummary rerunVerification(DeploymentEntity deployment) {
        deploymentAccessService.requireDeploymentOperatorAccess(deployment);
        DeploymentVerificationRunSummary run = deploymentService.rerunVerification(deployment.getId());
        platformAuditService.record(
            "DEPLOYMENT_REMEDIATION_EXECUTED",
            "DEPLOYMENT",
            deployment.getId(),
            Map.of("actionKey", RERUN_VERIFICATION, "referenceId", run.id())
        );
        return new DeploymentRemediationExecutionSummary(
            RERUN_VERIFICATION,
            "STARTED",
            run.summaryMessage(),
            "DEPLOYMENT_VERIFICATION_RUN",
            run.id()
        );
    }

    private DeploymentRemediationExecutionSummary redeployActiveVersion(DeploymentEntity deployment,
                                                                        DeploymentVersionEntity activeVersion,
                                                                        ExecuteDeploymentRemediationRequest request) {
        deploymentAccessService.requireDeploymentOperatorAccess(deployment);
        requireConfirmation(request, "confirm=true is required to redeploy the active version.");
        String reason = requireReason(request, "A remediation reason is required to redeploy the active version.");
        if (activeVersion == null) {
            throw new ResponseStatusException(BAD_REQUEST, "No active version exists to redeploy.");
        }
        DeploymentReleaseSummary release = deploymentService.applyVersion(
            deployment.getId(),
            activeVersion.getId(),
            trimToNull(request == null ? null : request.approvalId())
        );
        platformAuditService.record(
            "DEPLOYMENT_REMEDIATION_EXECUTED",
            "DEPLOYMENT_RELEASE",
            release.id(),
            Map.of(
                "actionKey", REDEPLOY_ACTIVE_VERSION,
                "deploymentId", deployment.getId(),
                "versionId", activeVersion.getId(),
                "reason", reason
            )
        );
        return new DeploymentRemediationExecutionSummary(
            REDEPLOY_ACTIVE_VERSION,
            "TRIGGERED",
            "A new rollout was requested for " + activeVersion.getVersionLabel() + ".",
            "DEPLOYMENT_RELEASE",
            release.id()
        );
    }

    private DeploymentRemediationExecutionSummary restartRailwayService(DeploymentEntity deployment,
                                                                        DeploymentReleaseEntity latestRelease,
                                                                        JsonNode details,
                                                                        ExecuteDeploymentRemediationRequest request,
                                                                        String serviceKey,
                                                                        String actionKey,
                                                                        String auditAction,
                                                                        String successMessage) {
        deploymentAccessService.requireDeploymentAdminAccess(deployment);
        requireConfirmation(request, "confirm=true is required to trigger a provider-side service restart.");
        String reason = requireReason(request, "A remediation reason is required before restarting a live service.");
        if (latestRelease == null) {
            throw new ResponseStatusException(BAD_REQUEST, "No release exists yet for provider-side service restarts.");
        }
        if (!"RAILWAY_API".equalsIgnoreCase(hasText(latestRelease.getProvisioningTarget())
            ? latestRelease.getProvisioningTarget()
            : provisioningProperties.mode())) {
            throw new ResponseStatusException(BAD_REQUEST, "Provider-side restarts require RAILWAY_API mode.");
        }

        String environmentId = text(details, "railway", "environmentId");
        String serviceId = text(details, "railway", "services", serviceKey, "serviceId");
        if (!hasText(environmentId) || !hasText(serviceId)) {
            throw new ResponseStatusException(
                BAD_REQUEST,
                "Latest release does not contain the Railway service context needed for provider-side restart."
            );
        }

        String railwayDeploymentId = railwayGraphqlClient.deployService(serviceId, environmentId);
        platformAuditService.record(
            auditAction,
            "DEPLOYMENT",
            deployment.getId(),
            Map.of(
                "deploymentId", deployment.getId(),
                "releaseId", latestRelease.getId(),
                "serviceKey", serviceKey,
                "railwayDeploymentId", railwayDeploymentId,
                "reason", reason
            )
        );
        platformAuditService.record(
            "DEPLOYMENT_REMEDIATION_EXECUTED",
            "DEPLOYMENT",
            deployment.getId(),
            Map.of(
                "actionKey", actionKey,
                "railwayDeploymentId", railwayDeploymentId,
                "reason", reason
            )
        );
        return new DeploymentRemediationExecutionSummary(
            actionKey,
            "TRIGGERED",
            successMessage,
            "RAILWAY_DEPLOYMENT",
            railwayDeploymentId
        );
    }

    private DeploymentRemediationExecutionSummary resetRuntimeVectors(DeploymentEntity deployment,
                                                                      ExecuteDeploymentRemediationRequest request) {
        deploymentAccessService.requireDeploymentOperatorAccess(deployment);
        requireConfirmation(request, "confirm=true is required to clear runtime vectors.");
        String reason = requireReason(request, "A remediation reason is required before clearing runtime vectors.");
        DeploymentPocRuntimeResetResponse response = deploymentPocWorkspaceService.clearRuntimeVectors(
            deployment.getId(),
            new DeploymentPocRuntimeResetRequest(true, reason)
        );
        platformAuditService.record(
            "DEPLOYMENT_REMEDIATION_EXECUTED",
            "DEPLOYMENT",
            deployment.getId(),
            Map.of(
                "actionKey", RESET_RUNTIME_VECTORS,
                "removedVectors", response.removedVectors(),
                "reason", reason
            )
        );
        return new DeploymentRemediationExecutionSummary(
            RESET_RUNTIME_VECTORS,
            response.success() ? "COMPLETED" : "FAILED",
            response.message() == null ? "Runtime vector reset completed." : response.message(),
            "DEPLOYMENT",
            deployment.getId()
        );
    }

    private DeploymentRemediationExecutionSummary archiveDeployment(DeploymentEntity deployment,
                                                                    ExecuteDeploymentRemediationRequest request) {
        requireConfirmation(request, "confirm=true is required to archive the deployment.");
        String reason = requireReason(request, "A remediation reason is required before archiving a deployment.");
        deploymentService.archiveDeployment(deployment.getId());
        platformAuditService.record(
            "DEPLOYMENT_REMEDIATION_EXECUTED",
            "DEPLOYMENT",
            deployment.getId(),
            Map.of("actionKey", ARCHIVE_DEPLOYMENT, "reason", reason)
        );
        return new DeploymentRemediationExecutionSummary(
            ARCHIVE_DEPLOYMENT,
            "COMPLETED",
            "Deployment archived.",
            "DEPLOYMENT",
            deployment.getId()
        );
    }

    private DeploymentRemediationExecutionSummary restoreDeployment(DeploymentEntity deployment) {
        deploymentService.restoreDeployment(deployment.getId());
        platformAuditService.record(
            "DEPLOYMENT_REMEDIATION_EXECUTED",
            "DEPLOYMENT",
            deployment.getId(),
            Map.of("actionKey", RESTORE_DEPLOYMENT)
        );
        return new DeploymentRemediationExecutionSummary(
            RESTORE_DEPLOYMENT,
            "COMPLETED",
            "Deployment restored.",
            "DEPLOYMENT",
            deployment.getId()
        );
    }

    private DeploymentRemediationExecutionSummary deleteDeployment(DeploymentEntity deployment,
                                                                   ExecuteDeploymentRemediationRequest request) {
        requireConfirmation(request, "confirm=true is required to delete the deployment.");
        String reason = requireReason(request, "A remediation reason is required before deleting a deployment.");
        deploymentService.deleteDeployment(deployment.getId(), trimToNull(request == null ? null : request.approvalId()));
        platformAuditService.record(
            "DEPLOYMENT_REMEDIATION_EXECUTED",
            "DEPLOYMENT",
            deployment.getId(),
            Map.of("actionKey", DELETE_DEPLOYMENT, "reason", reason)
        );
        return new DeploymentRemediationExecutionSummary(
            DELETE_DEPLOYMENT,
            "COMPLETED",
            "Deployment deleted.",
            "DEPLOYMENT",
            deployment.getId()
        );
    }

    private DeploymentEntity getDeployment(String deploymentId) {
        return deploymentRepository.findById(deploymentId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment not found: " + deploymentId));
    }

    private DeploymentReleaseEntity latestRelease(String deploymentId) {
        return deploymentReleaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc(deploymentId).orElse(null);
    }

    private DeploymentVersionEntity activeVersion(DeploymentEntity deployment) {
        if (!hasText(deployment.getActiveVersionId())) {
            return null;
        }
        return deploymentVersionRepository.findById(deployment.getActiveVersionId()).orElse(null);
    }

    private boolean isReleaseInProgress(DeploymentReleaseEntity release) {
        String status = normalizeStatus(release.getStatus());
        String provisioningStatus = normalizeStatus(release.getProvisioningStatus());
        String verificationStatus = normalizeStatus(release.getVerificationStatus());
        return List.of("APPLY_REQUESTED", "PROVISIONING", "VERIFYING").contains(status)
            || List.of("QUEUED", "RUNNING").contains(provisioningStatus)
            || "RUNNING".equals(verificationStatus);
    }

    private boolean isArchived(DeploymentEntity deployment) {
        return deployment.getArchivedAt() != null || "ARCHIVED".equalsIgnoreCase(deployment.getStatus());
    }

    private DeploymentRemediationActionSummary action(String key,
                                                      String label,
                                                      String description,
                                                      String category,
                                                      String severity,
                                                      String requiredRole,
                                                      boolean available,
                                                      boolean requiresConfirmation,
                                                      boolean requiresReason,
                                                      boolean requiresApproval,
                                                      String blockedReason,
                                                      String confirmationText) {
        return new DeploymentRemediationActionSummary(
            key,
            label,
            description,
            category,
            severity,
            requiredRole,
            available,
            requiresConfirmation,
            requiresReason,
            requiresApproval,
            available ? null : blockedReason,
            confirmationText
        );
    }

    private String blockedReason(boolean accessOkay,
                                 boolean stateOkay,
                                 boolean dependencyOkay,
                                 boolean readinessOkay,
                                 String accessMessage,
                                 String stateMessage,
                                 String dependencyMessage,
                                 String readinessMessage) {
        if (!accessOkay && hasText(accessMessage)) {
            return accessMessage;
        }
        if (!stateOkay && hasText(stateMessage)) {
            return stateMessage;
        }
        if (!dependencyOkay && hasText(dependencyMessage)) {
            return dependencyMessage;
        }
        if (!readinessOkay && hasText(readinessMessage)) {
            return readinessMessage;
        }
        return "This action is not currently available.";
    }

    private void requireConfirmation(ExecuteDeploymentRemediationRequest request, String message) {
        if (request == null || !Boolean.TRUE.equals(request.confirm())) {
            throw new ResponseStatusException(BAD_REQUEST, message);
        }
    }

    private String requireReason(ExecuteDeploymentRemediationRequest request, String message) {
        String reason = trimToNull(request == null ? null : request.reason());
        if (!hasText(reason)) {
            throw new ResponseStatusException(BAD_REQUEST, message);
        }
        return reason;
    }

    private JsonNode readJson(String json) {
        if (!hasText(json)) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }

    private String normalizeActionKey(String actionKey) {
        return hasText(actionKey) ? actionKey.trim().toUpperCase() : "";
    }

    private String normalizeStatus(String status) {
        return hasText(status) ? status.trim().toUpperCase() : "";
    }

    private String text(JsonNode node, String... path) {
        JsonNode current = node;
        for (String part : path) {
            current = current.path(part);
        }
        return current.isMissingNode() || current.isNull() ? null : trimToNull(current.asText());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }
}
