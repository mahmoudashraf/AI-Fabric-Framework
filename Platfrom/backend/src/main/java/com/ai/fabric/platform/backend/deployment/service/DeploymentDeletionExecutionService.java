package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentDeletionOperationEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentManagedVectorResourceEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentDeletionOperationRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentDraftRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentManagedVectorResourceRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentPromptRevisionRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVerificationRunRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.repository.PublicApiDeploymentRepository;
import com.ai.fabric.platform.backend.secret.model.DeploymentProviderSecretOverrideCleanupSummary;
import com.ai.fabric.platform.backend.secret.service.DeploymentProviderSecretOverrideService;
import com.ai.fabric.platform.backend.tenant.service.PlatformCustomerConsumerService;
import com.ai.fabric.platform.backend.vectorization.service.VectorizationDeploymentCleanupService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DeploymentDeletionExecutionService {

    private static final Logger log = LoggerFactory.getLogger(DeploymentDeletionExecutionService.class);

    private final DeploymentDeletionOperationRepository deletionOperationRepository;
    private final DeploymentRepository deploymentRepository;
    private final DeploymentReleaseRepository releaseRepository;
    private final DeploymentVersionRepository versionRepository;
    private final DeploymentDraftRepository draftRepository;
    private final DeploymentVerificationRunRepository verificationRunRepository;
    private final DeploymentManagedVectorResourceRepository deploymentManagedVectorResourceRepository;
    private final PublicApiDeploymentRepository publicApiDeploymentRepository;
    private final DeploymentPromptRevisionRepository promptRevisionRepository;
    private final DeploymentAssignmentService deploymentAssignmentService;
    private final DeploymentInfrastructureCleanupService deploymentInfrastructureCleanupService;
    private final DeploymentTenantScopedVectorRegistryService deploymentTenantScopedVectorRegistryService;
    private final VectorizationDeploymentCleanupService vectorizationDeploymentCleanupService;
    private final DeploymentProviderSecretOverrideService deploymentProviderSecretOverrideService;
    private final PlatformAuditService platformAuditService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final PlatformCustomerConsumerService platformCustomerConsumerService;

    public DeploymentDeletionExecutionService(DeploymentDeletionOperationRepository deletionOperationRepository,
                                              DeploymentRepository deploymentRepository,
                                              DeploymentReleaseRepository releaseRepository,
                                              DeploymentVersionRepository versionRepository,
                                              DeploymentDraftRepository draftRepository,
                                              DeploymentVerificationRunRepository verificationRunRepository,
                                              DeploymentManagedVectorResourceRepository deploymentManagedVectorResourceRepository,
                                              PublicApiDeploymentRepository publicApiDeploymentRepository,
                                              DeploymentPromptRevisionRepository promptRevisionRepository,
                                              DeploymentAssignmentService deploymentAssignmentService,
                                              DeploymentInfrastructureCleanupService deploymentInfrastructureCleanupService,
                                              DeploymentTenantScopedVectorRegistryService deploymentTenantScopedVectorRegistryService,
                                              VectorizationDeploymentCleanupService vectorizationDeploymentCleanupService,
                                              DeploymentProviderSecretOverrideService deploymentProviderSecretOverrideService,
                                              PlatformAuditService platformAuditService,
                                              ObjectMapper objectMapper,
                                              PlatformTransactionManager transactionManager,
                                              PlatformCustomerConsumerService platformCustomerConsumerService) {
        this.deletionOperationRepository = deletionOperationRepository;
        this.deploymentRepository = deploymentRepository;
        this.releaseRepository = releaseRepository;
        this.versionRepository = versionRepository;
        this.draftRepository = draftRepository;
        this.verificationRunRepository = verificationRunRepository;
        this.deploymentManagedVectorResourceRepository = deploymentManagedVectorResourceRepository;
        this.publicApiDeploymentRepository = publicApiDeploymentRepository;
        this.promptRevisionRepository = promptRevisionRepository;
        this.deploymentAssignmentService = deploymentAssignmentService;
        this.deploymentInfrastructureCleanupService = deploymentInfrastructureCleanupService;
        this.deploymentTenantScopedVectorRegistryService = deploymentTenantScopedVectorRegistryService;
        this.vectorizationDeploymentCleanupService = vectorizationDeploymentCleanupService;
        this.deploymentProviderSecretOverrideService = deploymentProviderSecretOverrideService;
        this.platformAuditService = platformAuditService;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.platformCustomerConsumerService = platformCustomerConsumerService;
    }

    @Async("deploymentDeletionExecutor")
    public void executeDeletion(String operationId) {
        DeletionExecutionContext context = transactionTemplate.execute(status -> transitionToRunning(operationId));
        if (context == null) {
            return;
        }

        Map<String, Object> resultDetails = new LinkedHashMap<>();
        resultDetails.put("deploymentId", context.deployment().getId());
        resultDetails.put("deploymentName", context.deployment().getName());
        resultDetails.put("hardDelete", context.operation().isHardDelete());

        try {
            if (context.operation().isHardDelete()) {
                DeploymentInfrastructureCleanupService.DeploymentInfrastructureCleanupResult cleanupResult =
                    deploymentInfrastructureCleanupService.cleanupForHardDelete(
                    context.deployment(),
                    context.latestRelease(),
                    context.operation().getRequestReason()
                );
                resultDetails.put("infrastructureCleanup", summarizeInfrastructureCleanup(cleanupResult));
            }

            transactionTemplate.executeWithoutResult(status -> completeDeletion(context, resultDetails));
        } catch (Exception ex) {
            log.error("Async deployment deletion failed: operationId={}, deploymentId={}", operationId, context.deployment().getId(), ex);
            transactionTemplate.executeWithoutResult(status -> markFailed(context, resultDetails, ex));
        }
    }

    private DeletionExecutionContext transitionToRunning(String operationId) {
        DeploymentDeletionOperationEntity operation = deletionOperationRepository.findById(operationId).orElse(null);
        if (operation == null) {
            return null;
        }
        if ("SUCCEEDED".equalsIgnoreCase(operation.getStatus()) || "FAILED".equalsIgnoreCase(operation.getStatus())) {
            return null;
        }

        DeploymentEntity deployment = deploymentRepository.findByIdForUpdate(operation.getDeploymentId()).orElse(null);
        if (deployment == null) {
            operation.setStatus("FAILED");
            operation.setErrorMessage("Deployment no longer exists before queued deletion could run.");
            operation.setStartedAt(Instant.now());
            operation.setCompletedAt(Instant.now());
            operation.setUpdatedAt(Instant.now());
            deletionOperationRepository.save(operation);
            return null;
        }

        Instant now = Instant.now();
        operation.setStatus("RUNNING");
        operation.setStartedAt(now);
        operation.setUpdatedAt(now);
        deletionOperationRepository.save(operation);

        deployment.setDeletionStatus("RUNNING");
        deployment.setUpdatedAt(now);
        deploymentRepository.save(deployment);

        updateManagedResourceDeletionState(deployment.getId(), operation.getId(), "RUNNING", now);

        DeploymentReleaseEntity latestRelease = releaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc(deployment.getId()).orElse(null);
        return new DeletionExecutionContext(operation, deployment, latestRelease);
    }

    private void completeDeletion(DeletionExecutionContext context, Map<String, Object> resultDetails) {
        DeploymentDeletionOperationEntity operation = deletionOperationRepository.findById(context.operation().getId())
            .orElseThrow(() -> new IllegalStateException("Deletion operation disappeared: " + context.operation().getId()));
        DeploymentEntity deployment = deploymentRepository.findByIdForUpdate(context.deployment().getId())
            .orElseThrow(() -> new IllegalStateException("Deployment disappeared before deletion completed: " + context.deployment().getId()));
        if (platformCustomerConsumerService.hasBoundConsumer(deployment.getId())) {
            throw new IllegalStateException("Deployment cannot be deleted while a consumer is bound to it.");
        }

        deploymentTenantScopedVectorRegistryService.detachForDeletedDeployment(deployment, context.operation().getRequestReason());
        vectorizationDeploymentCleanupService.deleteForDeployment(deployment);
        DeploymentProviderSecretOverrideCleanupSummary overrideCleanupSummary =
            deploymentProviderSecretOverrideService.cleanupForHardDelete(deployment.getId(), context.operation().getRequestReason());
        resultDetails.put("providerSecretOverrideCleanup", summarizeOverrideCleanup(overrideCleanupSummary));

        verificationRunRepository.deleteByDeploymentId(deployment.getId());
        releaseRepository.deleteByDeploymentId(deployment.getId());
        versionRepository.deleteByDeploymentId(deployment.getId());
        draftRepository.deleteByDeploymentId(deployment.getId());
        promptRevisionRepository.deleteByDeploymentId(deployment.getId());
        deploymentManagedVectorResourceRepository.deleteByDeploymentId(deployment.getId());
        deploymentAssignmentService.deleteAssignmentsForDeployment(deployment.getId());
        publicApiDeploymentRepository.deleteByDeploymentId(deployment.getId());
        deploymentRepository.delete(deployment);

        Instant now = Instant.now();
        resultDetails.put("completedAt", now.toString());
        operation.setStatus("SUCCEEDED");
        operation.setErrorMessage(null);
        operation.setResultDetailsJson(writeJson(resultDetails));
        operation.setCompletedAt(now);
        operation.setUpdatedAt(now);
        deletionOperationRepository.save(operation);

        platformAuditService.record(
            "DEPLOYMENT_DELETE_COMPLETED",
            "DEPLOYMENT",
            context.deployment().getId(),
            Map.of(
                "operationId", operation.getId(),
                "requestedByActorId", operation.getRequestedByActorId(),
                "requestedByRole", operation.getRequestedByRole(),
                "hardDelete", operation.isHardDelete(),
                "requestReason", defaultText(operation.getRequestReason(), "No reason provided."),
                "result", resultDetails
            )
        );
    }

    private void markFailed(DeletionExecutionContext context, Map<String, Object> resultDetails, Exception ex) {
        DeploymentDeletionOperationEntity operation = deletionOperationRepository.findById(context.operation().getId())
            .orElseThrow(() -> new IllegalStateException("Deletion operation disappeared: " + context.operation().getId()));
        DeploymentEntity deployment = deploymentRepository.findByIdForUpdate(context.deployment().getId()).orElse(null);

        Instant now = Instant.now();
        resultDetails.put("failedAt", now.toString());
        resultDetails.put("errorType", ex.getClass().getName());
        resultDetails.put("errorMessage", ex.getMessage());
        operation.setStatus("FAILED");
        operation.setErrorMessage(ex.getMessage());
        operation.setResultDetailsJson(writeJson(resultDetails));
        operation.setCompletedAt(now);
        operation.setUpdatedAt(now);
        deletionOperationRepository.save(operation);

        if (deployment != null) {
            deployment.setDeletionStatus("FAILED");
            deployment.setDeletionFailureMessage(ex.getMessage());
            deployment.setUpdatedAt(now);
            deploymentRepository.save(deployment);
            updateManagedResourceDeletionState(deployment.getId(), operation.getId(), "FAILED", now);
        }

        Map<String, Object> auditDetails = new LinkedHashMap<>();
        auditDetails.put("operationId", operation.getId());
        auditDetails.put("requestedByActorId", operation.getRequestedByActorId());
        auditDetails.put("requestedByRole", operation.getRequestedByRole());
        auditDetails.put("hardDelete", operation.isHardDelete());
        auditDetails.put("requestReason", defaultText(operation.getRequestReason(), "No reason provided."));
        auditDetails.put("errorMessage", ex.getMessage());
        auditDetails.put("result", resultDetails);
        platformAuditService.record(
            "DEPLOYMENT_DELETE_FAILED",
            "DEPLOYMENT",
            context.deployment().getId(),
            auditDetails
        );
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

    private ObjectNode summarizeInfrastructureCleanup(DeploymentInfrastructureCleanupService.DeploymentInfrastructureCleanupResult cleanupResult) {
        ObjectNode node = objectMapper.createObjectNode();
        if (cleanupResult == null) {
            return node;
        }
        node.put("managedVectorResourceCount", cleanupResult.managedVector().cleanedResourceIds().size());
        node.put("managedSecretCount", cleanupResult.managedVector().clearedManagedSecrets().size());
        node.put("railwayDeletedProject", cleanupResult.railway().projectDeleted());
        node.put("railwayDeletedServiceCount", cleanupResult.railway().deletedServiceIds().size());
        return node;
    }

    private ObjectNode summarizeOverrideCleanup(DeploymentProviderSecretOverrideCleanupSummary cleanupSummary) {
        ObjectNode node = objectMapper.createObjectNode();
        if (cleanupSummary == null) {
            return node;
        }
        node.putPOJO("removedBindingIds", cleanupSummary.removedBindingIds());
        node.putPOJO("deletedSecretNames", cleanupSummary.deletedSecretNames());
        node.putPOJO("preservedSecretNames", cleanupSummary.preservedSecretNames());
        return node;
    }

    private String writeJson(Map<String, Object> details) {
        try {
            return objectMapper.writeValueAsString(details == null ? Map.of() : details);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize deployment deletion result details.", ex);
        }
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record DeletionExecutionContext(
        DeploymentDeletionOperationEntity operation,
        DeploymentEntity deployment,
        DeploymentReleaseEntity latestRelease
    ) {
    }
}
