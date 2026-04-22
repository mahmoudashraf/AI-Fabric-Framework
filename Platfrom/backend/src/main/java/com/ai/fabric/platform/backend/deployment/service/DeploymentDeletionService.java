package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentDeletionOperationEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentManagedVectorResourceEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.model.DeleteDeploymentRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentDeletionOperationSummary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentDeletionOperationRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentManagedVectorResourceRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.ai.fabric.platform.backend.tenant.service.PlatformCustomerConsumerService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class DeploymentDeletionService {

    private final DeploymentRepository deploymentRepository;
    private final DeploymentReleaseRepository releaseRepository;
    private final DeploymentManagedVectorResourceRepository deploymentManagedVectorResourceRepository;
    private final DeploymentAccessService deploymentAccessService;
    private final DeploymentOperationApprovalService deploymentOperationApprovalService;
    private final DeploymentDeletionOperationRepository deletionOperationRepository;
    private final DeploymentDeletionExecutionService deploymentDeletionExecutionService;
    private final PlatformAuditService platformAuditService;
    private final ObjectMapper objectMapper;
    private final PlatformCustomerConsumerService platformCustomerConsumerService;

    public DeploymentDeletionService(DeploymentRepository deploymentRepository,
                                     DeploymentReleaseRepository releaseRepository,
                                     DeploymentManagedVectorResourceRepository deploymentManagedVectorResourceRepository,
                                     DeploymentAccessService deploymentAccessService,
                                     DeploymentOperationApprovalService deploymentOperationApprovalService,
                                     DeploymentDeletionOperationRepository deletionOperationRepository,
                                     DeploymentDeletionExecutionService deploymentDeletionExecutionService,
                                     PlatformAuditService platformAuditService,
                                     ObjectMapper objectMapper,
                                     PlatformCustomerConsumerService platformCustomerConsumerService) {
        this.deploymentRepository = deploymentRepository;
        this.releaseRepository = releaseRepository;
        this.deploymentManagedVectorResourceRepository = deploymentManagedVectorResourceRepository;
        this.deploymentAccessService = deploymentAccessService;
        this.deploymentOperationApprovalService = deploymentOperationApprovalService;
        this.deletionOperationRepository = deletionOperationRepository;
        this.deploymentDeletionExecutionService = deploymentDeletionExecutionService;
        this.platformAuditService = platformAuditService;
        this.objectMapper = objectMapper;
        this.platformCustomerConsumerService = platformCustomerConsumerService;
    }

    @Transactional
    public DeploymentDeletionOperationSummary queueDeleteDeployment(String deploymentId, DeleteDeploymentRequest request) {
        DeploymentEntity deployment = deploymentRepository.findByIdForUpdate(deploymentId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment not found: " + deploymentId));
        deployment = deploymentAccessService.requireDeploymentAdminAccess(deployment);

        DeleteDeploymentRequest normalizedRequest = request == null
            ? new DeleteDeploymentRequest(false, null, null)
            : request;
        boolean hardDelete = Boolean.TRUE.equals(normalizedRequest.hardDelete());
        if (hardDelete) {
            requirePlatformAdminForHardDelete();
        }

        deploymentOperationApprovalService.consumeApprovedRequestIfRequired(
            deployment,
            DeploymentOperationApprovalService.DELETE_DEPLOYMENT,
            null,
            deployment.isApprovalRequiredForDelete(),
            normalizedRequest.approvalId()
        );
        if (deployment.getArchivedAt() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Deployment must be archived before it can be deleted.");
        }
        if (platformCustomerConsumerService.hasBoundConsumer(deployment.getId())) {
            throw new ResponseStatusException(CONFLICT, "Deployment cannot be deleted while a consumer is bound to it.");
        }

        DeploymentReleaseEntity latestRelease = releaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc(deploymentId).orElse(null);
        if (latestRelease != null && isReleaseInProgress(latestRelease)) {
            throw new ResponseStatusException(CONFLICT, "Deployment cannot be deleted while apply is in progress: " + latestRelease.getId());
        }
        if ("QUEUED".equalsIgnoreCase(deployment.getDeletionStatus()) || "RUNNING".equalsIgnoreCase(deployment.getDeletionStatus())) {
            throw new ResponseStatusException(CONFLICT, "Deployment deletion is already in progress.");
        }

        Instant now = Instant.now();
        PlatformPrincipal principal = PlatformSecurityContext.currentPrincipal();
        DeploymentDeletionOperationEntity operation = new DeploymentDeletionOperationEntity();
        operation.setId("del-" + UUID.randomUUID().toString().substring(0, 8));
        operation.setDeploymentId(deployment.getId());
        operation.setDeploymentName(deployment.getName());
        operation.setEnvironmentName(deployment.getEnvironmentName());
        operation.setCustomerId(deployment.getCustomerId());
        operation.setTenantId(deployment.getTenantId());
        operation.setStatus("QUEUED");
        operation.setHardDelete(hardDelete);
        operation.setApprovalId(trimToNull(normalizedRequest.approvalId()));
        operation.setRequestReason(trimToNull(normalizedRequest.reason()));
        operation.setRequestedByActorId(principal == null ? "system" : principal.actorId());
        operation.setRequestedByRole(principal == null ? "SYSTEM" : principal.role().name());
        operation.setRequestDetailsJson(writeJson(buildRequestDetails(deployment, latestRelease, hardDelete, normalizedRequest.reason())));
        operation.setResultDetailsJson(writeJson(Map.of()));
        operation.setCreatedAt(now);
        operation.setUpdatedAt(now);
        deletionOperationRepository.save(operation);

        deployment.setDeletionStatus("QUEUED");
        deployment.setDeletionOperationId(operation.getId());
        deployment.setDeletionRequestedAt(now);
        deployment.setDeletionFailureMessage(null);
        deployment.setUpdatedAt(now);
        deploymentRepository.save(deployment);

        updateManagedResourceDeletionState(deployment.getId(), operation.getId(), "QUEUED", now);

        platformAuditService.record(
            "DEPLOYMENT_DELETE_QUEUED",
            "DEPLOYMENT",
            deployment.getId(),
            Map.of(
                "operationId", operation.getId(),
                "hardDelete", hardDelete,
                "requestReason", defaultText(operation.getRequestReason(), "No reason provided."),
                "requestedByActorId", operation.getRequestedByActorId(),
                "requestedByRole", operation.getRequestedByRole()
            )
        );

        scheduleExecutionAfterCommit(operation.getId());
        return toSummary(operation);
    }

    @Transactional(readOnly = true)
    public List<DeploymentDeletionOperationSummary> listOperations(String status, int limit) {
        int normalizedLimit = Math.max(1, Math.min(limit, 200));
        List<DeploymentDeletionOperationEntity> entities = status == null || status.isBlank()
            ? deletionOperationRepository.findTop200ByOrderByCreatedAtDesc()
            : deletionOperationRepository.findTop200ByStatusOrderByCreatedAtDesc(status.trim().toUpperCase(Locale.ROOT));
        return entities.stream().limit(normalizedLimit).map(this::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public DeploymentDeletionOperationSummary getOperation(String operationId) {
        return deletionOperationRepository.findById(operationId)
            .map(this::toSummary)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deletion operation not found: " + operationId));
    }

    @Transactional
    public DeploymentDeletionOperationSummary retryFailedOperation(String failedOperationId) {
        DeploymentDeletionOperationEntity failedOperation = deletionOperationRepository.findById(failedOperationId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deletion operation not found: " + failedOperationId));
        if (!"FAILED".equalsIgnoreCase(failedOperation.getStatus())) {
            throw new ResponseStatusException(CONFLICT, "Only failed deletion operations can be retried.");
        }

        DeploymentEntity deployment = deploymentRepository.findByIdForUpdate(failedOperation.getDeploymentId())
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment not found: " + failedOperation.getDeploymentId()));
        deployment = deploymentAccessService.requireDeploymentAdminAccess(deployment);
        if ("QUEUED".equalsIgnoreCase(deployment.getDeletionStatus()) || "RUNNING".equalsIgnoreCase(deployment.getDeletionStatus())) {
            throw new ResponseStatusException(CONFLICT, "Deployment deletion is already in progress.");
        }
        if (deployment.getArchivedAt() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Deployment must remain archived before deletion can be retried.");
        }

        DeploymentReleaseEntity latestRelease = releaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc(deployment.getId()).orElse(null);
        if (latestRelease != null && isReleaseInProgress(latestRelease)) {
            throw new ResponseStatusException(CONFLICT, "Deployment cannot be deleted while apply is in progress: " + latestRelease.getId());
        }

        Instant now = Instant.now();
        PlatformPrincipal principal = PlatformSecurityContext.currentPrincipal();
        ObjectNode requestDetails = objectMapper.valueToTree(
            buildRequestDetails(deployment, latestRelease, failedOperation.isHardDelete(), failedOperation.getRequestReason())
        );
        requestDetails.put("retryOfOperationId", failedOperation.getId());
        requestDetails.put("retriedAt", now.toString());

        DeploymentDeletionOperationEntity retryOperation = new DeploymentDeletionOperationEntity();
        retryOperation.setId("del-" + UUID.randomUUID().toString().substring(0, 8));
        retryOperation.setDeploymentId(deployment.getId());
        retryOperation.setDeploymentName(deployment.getName());
        retryOperation.setEnvironmentName(deployment.getEnvironmentName());
        retryOperation.setCustomerId(deployment.getCustomerId());
        retryOperation.setTenantId(deployment.getTenantId());
        retryOperation.setStatus("QUEUED");
        retryOperation.setHardDelete(failedOperation.isHardDelete());
        retryOperation.setApprovalId(trimToNull(failedOperation.getApprovalId()));
        retryOperation.setRequestReason(trimToNull(failedOperation.getRequestReason()));
        retryOperation.setRequestedByActorId(principal == null ? "system" : principal.actorId());
        retryOperation.setRequestedByRole(principal == null ? "SYSTEM" : principal.role().name());
        retryOperation.setRequestDetailsJson(writeJson(requestDetails));
        retryOperation.setResultDetailsJson(writeJson(Map.of("retryOfOperationId", failedOperation.getId())));
        retryOperation.setCreatedAt(now);
        retryOperation.setUpdatedAt(now);
        deletionOperationRepository.save(retryOperation);

        deployment.setDeletionStatus("QUEUED");
        deployment.setDeletionOperationId(retryOperation.getId());
        deployment.setDeletionRequestedAt(now);
        deployment.setDeletionFailureMessage(null);
        deployment.setUpdatedAt(now);
        deploymentRepository.save(deployment);

        updateManagedResourceDeletionState(deployment.getId(), retryOperation.getId(), "QUEUED", now);

        platformAuditService.record(
            "DEPLOYMENT_DELETE_REQUEUED",
            "DEPLOYMENT",
            deployment.getId(),
            Map.of(
                "operationId", retryOperation.getId(),
                "retryOfOperationId", failedOperation.getId(),
                "hardDelete", retryOperation.isHardDelete(),
                "requestReason", defaultText(retryOperation.getRequestReason(), "No reason provided."),
                "requestedByActorId", retryOperation.getRequestedByActorId(),
                "requestedByRole", retryOperation.getRequestedByRole()
            )
        );

        scheduleExecutionAfterCommit(retryOperation.getId());
        return toSummary(retryOperation);
    }

    @Transactional
    public DeploymentDeletionOperationSummary retriggerRunningOperation(String operationId) {
        DeploymentDeletionOperationEntity operation = deletionOperationRepository.findById(operationId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deletion operation not found: " + operationId));
        if (!operation.isHardDelete()) {
            throw new ResponseStatusException(BAD_REQUEST, "Only hard-delete cleanup operations can be retriggered.");
        }
        if (!"RUNNING".equalsIgnoreCase(operation.getStatus())) {
            throw new ResponseStatusException(CONFLICT, "Only running deletion operations can be retriggered.");
        }

        DeploymentEntity deployment = deploymentRepository.findByIdForUpdate(operation.getDeploymentId())
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment not found: " + operation.getDeploymentId()));
        deployment = deploymentAccessService.requireDeploymentAdminAccess(deployment);
        if (deployment.getArchivedAt() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Deployment must remain archived before cleanup can be retriggered.");
        }
        if (!operation.getId().equals(deployment.getDeletionOperationId())) {
            throw new ResponseStatusException(CONFLICT, "Only the active deletion operation can be retriggered.");
        }
        if (!"RUNNING".equalsIgnoreCase(deployment.getDeletionStatus())) {
            throw new ResponseStatusException(CONFLICT, "Deployment deletion is not currently running.");
        }

        Instant now = Instant.now();
        PlatformPrincipal principal = PlatformSecurityContext.currentPrincipal();
        ObjectNode requestDetails = objectMapper.valueToTree(readJson(operation.getRequestDetailsJson()));
        ArrayNode retriggerHistory = requestDetails.withArray("retriggerHistory");
        retriggerHistory.addObject()
            .put("requestedAt", now.toString())
            .put("requestedByActorId", principal == null ? "system" : principal.actorId())
            .put("requestedByRole", principal == null ? "SYSTEM" : principal.role().name());
        requestDetails.put("lastRetriggerRequestedAt", now.toString());
        requestDetails.put("lastRetriggerRequestedByActorId", principal == null ? "system" : principal.actorId());
        requestDetails.put("lastRetriggerRequestedByRole", principal == null ? "SYSTEM" : principal.role().name());
        requestDetails.put("retriggerCount", retriggerHistory.size());

        operation.setRequestDetailsJson(writeJson(requestDetails));
        operation.setUpdatedAt(now);
        deletionOperationRepository.save(operation);

        deployment.setDeletionFailureMessage(null);
        deployment.setUpdatedAt(now);
        deploymentRepository.save(deployment);

        updateManagedResourceDeletionState(deployment.getId(), operation.getId(), "RUNNING", now);

        platformAuditService.record(
            "DEPLOYMENT_DELETE_RETRIGGERED",
            "DEPLOYMENT",
            deployment.getId(),
            Map.of(
                "operationId", operation.getId(),
                "hardDelete", operation.isHardDelete(),
                "requestReason", defaultText(operation.getRequestReason(), "No reason provided."),
                "requestedByActorId", principal == null ? "system" : principal.actorId(),
                "requestedByRole", principal == null ? "SYSTEM" : principal.role().name()
            )
        );

        scheduleExecutionAfterCommit(operation.getId());
        return toSummary(operation);
    }

    DeploymentDeletionOperationSummary toSummary(DeploymentDeletionOperationEntity entity) {
        try {
            JsonNode requestDetails = objectMapper.readTree(blankToObject(entity.getRequestDetailsJson()));
            JsonNode resultDetails = objectMapper.readTree(blankToObject(entity.getResultDetailsJson()));
            return new DeploymentDeletionOperationSummary(
                entity.getId(),
                entity.getDeploymentId(),
                entity.getDeploymentName(),
                entity.getEnvironmentName(),
                entity.getCustomerId(),
                entity.getTenantId(),
                entity.getStatus(),
                summarizeStatus(entity),
                entity.isHardDelete(),
                entity.getApprovalId(),
                entity.getRequestReason(),
                entity.getRequestedByActorId(),
                entity.getRequestedByRole(),
                requestDetails,
                resultDetails,
                entity.getErrorMessage(),
                entity.getCreatedAt(),
                entity.getStartedAt(),
                entity.getCompletedAt(),
                entity.getUpdatedAt()
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read deployment deletion operation details.", ex);
        }
    }

    private void scheduleExecutionAfterCommit(String operationId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deploymentDeletionExecutionService.executeDeletion(operationId);
                }
            });
            return;
        }
        deploymentDeletionExecutionService.executeDeletion(operationId);
    }

    private Map<String, Object> buildRequestDetails(DeploymentEntity deployment,
                                                    DeploymentReleaseEntity latestRelease,
                                                    boolean hardDelete,
                                                    String reason) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("deploymentId", deployment.getId());
        details.put("deploymentName", deployment.getName());
        details.put("environmentName", deployment.getEnvironmentName());
        details.put("hardDelete", hardDelete);
        details.put("reason", defaultText(trimToNull(reason), "No reason provided."));
        details.put("deploymentStatus", deployment.getStatus());
        details.put("archivedAt", deployment.getArchivedAt() == null ? null : deployment.getArchivedAt().toString());
        if (latestRelease != null) {
            details.put("latestReleaseId", latestRelease.getId());
            details.put("latestReleaseStatus", latestRelease.getStatus());
            details.put("latestProvisioningStatus", latestRelease.getProvisioningStatus());
            details.put("railwayInventory", summarizeRailwayInventory(latestRelease.getProvisioningDetailsJson()));
        }
        details.put("managedVectorResources", summarizeManagedResources(deployment.getId()));
        return details;
    }

    private ObjectNode summarizeRailwayInventory(String provisioningDetailsJson) {
        JsonNode details = readJson(provisioningDetailsJson);
        ObjectNode node = objectMapper.createObjectNode();
        node.put("projectId", text(details, "railway", "projectId"));
        ArrayNode services = node.putArray("services");
        addServiceSummary(services, "runtime", text(details, "railway", "services", "runtime", "serviceId"));
        addServiceSummary(services, "restConnector", text(details, "railway", "services", "restConnector", "serviceId"));
        addServiceSummary(services, "vectorizationRunner", text(details, "railway", "services", "vectorizationRunner", "serviceId"));
        return node;
    }

    private void addServiceSummary(ArrayNode services, String role, String serviceId) {
        if (serviceId == null || serviceId.isBlank()) {
            return;
        }
        ObjectNode service = services.addObject();
        service.put("role", role);
        service.put("serviceId", serviceId);
    }

    private List<Map<String, Object>> summarizeManagedResources(String deploymentId) {
        return deploymentManagedVectorResourceRepository.findByDeploymentIdOrderByUpdatedAtDesc(deploymentId).stream()
            .map(resource -> Map.<String, Object>of(
                "id", resource.getId(),
                "vendor", resource.getVendor(),
                "resourceType", resource.getResourceType(),
                "resourceName", resource.getResourceName(),
                "resourceStatus", resource.getResourceStatus()
            ))
            .toList();
    }

    private void updateManagedResourceDeletionState(String deploymentId,
                                                    String operationId,
                                                    String deletionStatus,
                                                    Instant requestedAt) {
        List<DeploymentManagedVectorResourceEntity> resources = deploymentManagedVectorResourceRepository
            .findByDeploymentIdOrderByUpdatedAtDesc(deploymentId);
        if (resources.isEmpty()) {
            return;
        }
        for (DeploymentManagedVectorResourceEntity resource : resources) {
            resource.setDeletionStatus(deletionStatus);
            resource.setDeletionOperationId(operationId);
            if (resource.getDeletionRequestedAt() == null) {
                resource.setDeletionRequestedAt(requestedAt);
            }
            resource.setUpdatedAt(requestedAt);
        }
        deploymentManagedVectorResourceRepository.saveAll(resources);
    }

    private boolean isReleaseInProgress(DeploymentReleaseEntity release) {
        return switch (release.getStatus()) {
            case "APPLY_REQUESTED", "PRE_APPLY_VERIFYING", "PROVISIONING", "VERIFYING" -> true;
            default -> "QUEUED".equals(release.getProvisioningStatus())
                || "RUNNING".equals(release.getProvisioningStatus())
                || "AWAITING_CONFIRMATION".equals(release.getProvisioningStatus())
                || "RUNNING".equals(release.getVerificationStatus());
        };
    }

    private void requirePlatformAdminForHardDelete() {
        PlatformPrincipal principal = PlatformSecurityContext.currentPrincipal();
        if (principal == null || principal.role() != PlatformRole.PLATFORM_ADMIN) {
            throw new ResponseStatusException(FORBIDDEN, "Hard delete is restricted to platform administrators.");
        }
    }

    private String summarizeStatus(DeploymentDeletionOperationEntity entity) {
        return switch (entity.getStatus()) {
            case "QUEUED" -> "Subject to deletion completion. Cleanup is queued.";
            case "RUNNING" -> "Deletion is running. Platform records remain until cleanup finishes.";
            case "SUCCEEDED" -> "Deletion completed and all tracked platform records were removed.";
            case "FAILED" -> defaultText(entity.getErrorMessage(), "Deletion failed. Review the full notification details.");
            default -> "Deletion request recorded.";
        };
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(blankToObject(json));
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }

    private String text(JsonNode root, String... path) {
        JsonNode current = root == null ? null : root;
        for (String segment : path) {
            if (current == null || current.isMissingNode() || current.isNull()) {
                return null;
            }
            current = current.path(segment);
        }
        if (current == null || current.isMissingNode() || current.isNull()) {
            return null;
        }
        String value = current.asText();
        return value == null || value.isBlank() ? null : value;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize deployment deletion request details.", ex);
        }
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String blankToObject(String json) {
        return json == null || json.isBlank() ? "{}" : json;
    }
}
