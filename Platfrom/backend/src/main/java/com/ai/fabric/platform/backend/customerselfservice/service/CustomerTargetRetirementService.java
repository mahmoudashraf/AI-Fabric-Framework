package com.ai.fabric.platform.backend.customerselfservice.service;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.AuthorizationRequest;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerTargetRetirementContracts.Command;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerTargetRetirementContracts.PlanRequest;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerTargetRetirementContracts.Summary;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.deployment.admission.DeploymentAdmissionService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.ConsumerExpectation;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementOperationRepository;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementOperationEntity;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementService;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementStore;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.*;
import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.*;

@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class CustomerTargetRetirementService {
    private final CustomerOfferEntitlementService entitlements;
    private final CustomerSelfServiceAuthorizationService authorization;
    private final CustomerOfferDeploymentRepository associations;
    private final DeploymentRepository deployments;
    private final DeploymentReleaseRepository releases;
    private final PlatformConsumerRepository consumers;
    private final TargetRetirementOperationRepository operations;
    private final TargetRetirementService retirements;
    private final TargetRetirementStore retirementStore;
    private final DeploymentAdmissionService admissions;
    private final CustomerOfferCodec codec;
    private final EntityManager entityManager;
    private final TransactionTemplate preparation;

    public CustomerTargetRetirementService(CustomerOfferEntitlementService entitlements,
                                           CustomerSelfServiceAuthorizationService authorization,
                                           CustomerOfferDeploymentRepository associations,
                                           DeploymentRepository deployments,
                                           DeploymentReleaseRepository releases,
                                           PlatformConsumerRepository consumers,
                                           TargetRetirementOperationRepository operations,
                                           TargetRetirementService retirements,
                                           TargetRetirementStore retirementStore,
                                           DeploymentAdmissionService admissions,
                                           CustomerOfferCodec codec,
                                           EntityManager entityManager,
                                           PlatformTransactionManager transactionManager) {
        this.entitlements = entitlements;
        this.authorization = authorization;
        this.associations = associations;
        this.deployments = deployments;
        this.releases = releases;
        this.consumers = consumers;
        this.operations = operations;
        this.retirements = retirements;
        this.retirementStore = retirementStore;
        this.admissions = admissions;
        this.codec = codec;
        this.entityManager = entityManager;
        this.preparation = new TransactionTemplate(transactionManager);
        this.preparation.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public Summary plan(String offerId, String deploymentId, PlanRequest request) {
        PlanRequest normalized = normalize(offerId, deploymentId, request);
        var actor = entitlements.requireCustomerActor();
        var prepared = preparation.execute(status -> prepare(actor.customerId(), offerId, deploymentId, normalized));
        if (prepared == null) throw deny(RESOURCE_SCOPE_MISMATCH);
        // The generic planner rechecks revisions and persists its snapshot after these short locks are released.
        var result = retirements.plan(deploymentId, normalized.targetProfileId(), prepared);
        requireResultScope(actor.customerId(), deploymentId, normalized.targetProfileId(), result);
        return summary(result, true);
    }

    public List<Summary> list(String offerId, String deploymentId, String targetProfileId) {
        validateScope(offerId, deploymentId);
        if (blank(targetProfileId, 64)) throw invalid();
        var actor = entitlements.requireCustomerActor();
        var ids = preparation.execute(status -> {
            var deployment = ownedDeployment(actor.customerId(), deploymentId, false);
            requireOwnedAssociation(actor.customerId(), offerId, deployment);
            authorizeOperation(actor.customerId(), offerId, deployment, targetProfileId, null, null, Operation.READ);
            return operations.findByDeploymentIdAndTargetProfileIdOrderByCreatedAtDesc(deploymentId, targetProfileId)
                .stream().map(operation -> {
                    requireOperationScope(actor.customerId(), deploymentId, operation);
                    if (!targetProfileId.equals(operation.getTargetProfileId())) throw deny(RESOURCE_SCOPE_MISMATCH);
                    return operation.getId();
                }).toList();
        });
        if (ids == null) throw deny(RESOURCE_SCOPE_MISMATCH);
        // Each operation is checked again after discovery; the list never projects generic inventory.
        return ids.stream().map(id -> {
            var result = status(offerId, deploymentId, id);
            if (!targetProfileId.equals(result.targetProfileId())) throw deny(RESOURCE_SCOPE_MISMATCH);
            return result;
        }).toList();
    }

    public Summary status(String offerId, String deploymentId, String operationId) {
        var scope = prepareOperation(offerId, deploymentId, operationId, null, false);
        var result = scope.readResult();
        requireResult(scope, result);
        return summary(result, scope.customerCommandsAllowed());
    }

    public Summary execute(String offerId, String deploymentId, String operationId, Command command) {
        return command(offerId, deploymentId, operationId, command, CommandKind.EXECUTE);
    }

    public Summary retry(String offerId, String deploymentId, String operationId, Command command) {
        return command(offerId, deploymentId, operationId, command, CommandKind.RETRY);
    }

    public Summary cancel(String offerId, String deploymentId, String operationId, Command command) {
        return command(offerId, deploymentId, operationId, command, CommandKind.CANCEL);
    }

    private Summary command(String offerId, String deploymentId, String operationId, Command command, CommandKind kind) {
        if (command == null || blank(command.requestFingerprint(), 64)) throw invalid();
        var scope = prepareOperation(offerId, deploymentId, operationId, command, true);
        if (!scope.customerCommandsAllowed()) throw conflict(ADMISSION_LIFECYCLE_REQUIRED);
        var frozen = new TargetRetirementContracts.Command(scope.requestFingerprint());
        var result = switch (kind) {
            case EXECUTE -> retirements.execute(deploymentId, scope.targetProfileId(), operationId, frozen);
            case RETRY -> retirements.retry(deploymentId, scope.targetProfileId(), operationId, frozen);
            case CANCEL -> retirements.cancel(deploymentId, scope.targetProfileId(), operationId, frozen);
        };
        requireResult(scope, result);
        return summary(result, true);
    }

    private OperationScope prepareOperation(String offerId, String deploymentId, String operationId,
                                            Command command, boolean mutate) {
        validateScope(offerId, deploymentId);
        if (blank(operationId, 64)) throw invalid();
        var actor = entitlements.requireCustomerActor();
        var scope = preparation.execute(status -> {
            // Match the worker's canonical deployment -> gate -> operation lock order.
            var deployment = ownedDeployment(actor.customerId(), deploymentId, true);
            requireOwnedAssociation(actor.customerId(), offerId, deployment);
            var operation = operations.findForUpdate(operationId).orElseThrow(() -> deny(RESOURCE_SCOPE_MISMATCH));
            entityManager.refresh(operation);
            requireOperationScope(actor.customerId(), deploymentId, operation);
            if (mutate && !operation.getRequestFingerprint().equals(command.requestFingerprint())) throw conflict(REVISION_CONFLICT);
            var request = codec.read(operation.getRequestJson(), TargetRetirementContracts.PlanRequest.class);
            if (!operation.getOperationKey().equals(request.operationKey()) || request.releaseIds() == null
                || request.releaseIds().isEmpty() || request.releaseIds().size() > 128) throw deny(RESOURCE_SCOPE_MISMATCH);
            for (String releaseId : request.releaseIds()) {
                if (blank(releaseId, 64)) throw deny(RESOURCE_SCOPE_MISMATCH);
                requireRelease(deploymentId, operation.getTargetProfileId(), releaseId);
                authorizeOperation(actor.customerId(), offerId, deployment, operation.getTargetProfileId(), releaseId,
                    operation.getOperationKey(), mutate ? Operation.TARGET_RETIRE : Operation.READ);
            }
            // Project the authorized locked row; the generic status route intentionally remains fresh-gated.
            return new OperationScope(actor.customerId(), deploymentId, operation.getTargetProfileId(),
                operationId, operation.getRequestFingerprint(), "CUSTOMER_ADMIN".equals(operation.getRequestedByRole()),
                mutate ? null : retirementStore.summary(operation));
        });
        if (scope == null) throw deny(RESOURCE_SCOPE_MISMATCH);
        return scope;
    }

    private void authorizeOperation(String customerId, String offerId, DeploymentEntity deployment,
                                    String targetProfileId, String releaseId, String operationKey, Operation operation) {
        var request = new AuthorizationRequest(offerId, targetProfileId, deployment.getId(),
            deployment.getTenantId(), releaseId, operation, operationKey, Set.of(), null, null, null);
        if (operation == Operation.READ) {
            if (releaseId == null) authorization.authorizeOwnedTargetRead(customerId, request);
            else authorization.authorizeOperationalRead(customerId, request);
        } else authorization.authorize(customerId, request);
    }

    private void requireOperationScope(String customerId, String deploymentId, TargetRetirementOperationEntity operation) {
        if (!customerId.equals(operation.getCustomerId()) || !deploymentId.equals(operation.getDeploymentId())
            || blank(operation.getTargetProfileId(), 64) || blank(operation.getOperationKey(), 128)
            || blank(operation.getRequestFingerprint(), 64)) throw deny(RESOURCE_SCOPE_MISMATCH);
    }

    private void requireResult(OperationScope scope, TargetRetirementContracts.Summary result) {
        requireResultScope(scope.customerId(), scope.deploymentId(), scope.targetProfileId(), result);
        if (!scope.operationId().equals(result.id()) || !scope.requestFingerprint().equals(result.requestFingerprint())) {
            throw conflict(REVISION_CONFLICT);
        }
    }

    private void requireResultScope(String customerId, String deploymentId, String targetProfileId,
                                    TargetRetirementContracts.Summary result) {
        if (result == null || !customerId.equals(result.customerId()) || !deploymentId.equals(result.deploymentId())
            || !targetProfileId.equals(result.targetProfileId())) throw deny(RESOURCE_SCOPE_MISMATCH);
    }

    private Summary summary(TargetRetirementContracts.Summary result, boolean customerCommandsAllowed) {
        return new Summary(result.id(), result.deploymentId(), result.targetProfileId(), result.requestFingerprint(),
            result.state(), result.step(), result.drainDeadline(), result.retention(), result.attempt(), result.nextRetryAt(),
            result.errorCode(), result.result() == null ? List.of() : result.result().blockers(), result.consumesCapacity(),
            customerCommandsAllowed,
            result.createdAt(), result.startedAt(), result.completedAt(), result.updatedAt());
    }

    private void validateScope(String offerId, String deploymentId) {
        if (blank(offerId, 64) || blank(deploymentId, 64)) throw invalid();
    }

    private enum CommandKind { EXECUTE, RETRY, CANCEL }
    private record OperationScope(String customerId, String deploymentId, String targetProfileId,
                                  String operationId, String requestFingerprint, boolean customerCommandsAllowed,
                                  TargetRetirementContracts.Summary readResult) { }

    private TargetRetirementContracts.PlanRequest prepare(String customerId, String offerId, String deploymentId,
                                                          PlanRequest request) {
        requireOwnedAssociation(customerId, offerId, ownedDeployment(customerId, deploymentId, false));
        if (!admissions.activeCustomerReservations(deploymentId, request.targetProfileId()).isEmpty()) {
            throw conflict(ADMISSION_LIFECYCLE_REQUIRED);
        }
        for (String releaseId : request.releaseIds()) requireRelease(deploymentId, request.targetProfileId(), releaseId);

        var previous = operations.findByDeploymentIdAndTargetProfileIdAndOperationKey(
            deploymentId, request.targetProfileId(), request.operationKey()).orElse(null);
        TargetRetirementContracts.PlanRequest frozen = null;
        if (previous != null) {
            if (!customerId.equals(previous.getCustomerId())
                || !PlatformSecurityContext.actorIdOrSystem().equals(previous.getRequestedByActorId())
                || !"CUSTOMER_ADMIN".equals(previous.getRequestedByRole())) throw conflict(OPERATION_KEY_CONFLICT);
            frozen = codec.read(previous.getRequestJson(), TargetRetirementContracts.PlanRequest.class);
            if (!frozen.equals(genericRequest(request, frozen.consumers()))) throw conflict(OPERATION_KEY_CONFLICT);
        }

        var discovered = frozen == null ? boundConsumers(deploymentId, request.releaseIds()) : List.<PlatformConsumerEntity>of();
        List<String> ids = frozen == null ? discovered.stream().map(PlatformConsumerEntity::getId).sorted().toList()
            : frozen.consumers().stream().map(ConsumerExpectation::consumerEntityId).sorted().toList();
        List<PlatformConsumerEntity> locked = ids.isEmpty() ? List.of() : consumers.findAllByIdForUpdate(ids);
        if (!ids.equals(locked.stream().map(PlatformConsumerEntity::getId).sorted().toList())) throw deny(RESOURCE_SCOPE_MISMATCH);
        // Discovery may already have loaded these entities before another binding transaction committed.
        locked.forEach(entityManager::refresh);

        // Consumer-before-deployment ordering matches workspace activation and assignment mutation.
        var deployment = ownedDeployment(customerId, deploymentId, true);
        requireOwnedAssociation(customerId, offerId, deployment);
        for (String releaseId : request.releaseIds()) {
            authorization.authorize(customerId, new AuthorizationRequest(offerId, request.targetProfileId(),
                deploymentId, deployment.getTenantId(), releaseId, Operation.TARGET_RETIRE, request.operationKey(),
                Set.of(), null, null, null));
        }
        if (locked.stream().anyMatch(consumer -> !customerId.equals(consumer.getCustomerId()))) throw deny(RESOURCE_SCOPE_MISMATCH);
        if (frozen != null) return frozen;

        var current = boundConsumers(deploymentId, request.releaseIds());
        if (!ids.equals(current.stream().map(PlatformConsumerEntity::getId).sorted().toList())) throw conflict(REVISION_CONFLICT);
        for (PlatformConsumerEntity consumer : locked) {
            if (!deploymentId.equals(consumer.getBoundDeploymentId()) || consumer.getBindingRevision() < 0
                || blank(consumer.getBoundReleaseId(), 64) || blank(consumer.getBoundTargetProfileId(), 64)) {
                throw deny(RESOURCE_SCOPE_MISMATCH);
            }
            requireRelease(deploymentId, consumer.getBoundTargetProfileId(), consumer.getBoundReleaseId());
        }
        return genericRequest(request, locked.stream().sorted(Comparator.comparing(PlatformConsumerEntity::getId))
            .map(consumer -> new ConsumerExpectation(consumer.getId(), consumer.getBindingRevision())).toList());
    }

    private List<PlatformConsumerEntity> boundConsumers(String deploymentId, List<String> releaseIds) {
        var result = new TreeMap<String, PlatformConsumerEntity>();
        for (var consumer : consumers.findByBoundDeploymentIdOrderByUpdatedAtDesc(deploymentId)) result.put(consumer.getId(), consumer);
        for (String releaseId : releaseIds) {
            for (var consumer : consumers.findByBoundReleaseIdOrderByUpdatedAtDesc(releaseId)) result.put(consumer.getId(), consumer);
        }
        return List.copyOf(result.values());
    }

    private DeploymentEntity ownedDeployment(String customerId, String deploymentId, boolean lock) {
        var deployment = (lock ? deployments.findByIdForUpdate(deploymentId) : deployments.findById(deploymentId))
            .orElseThrow(() -> deny(RESOURCE_SCOPE_MISMATCH));
        if (lock) entityManager.refresh(deployment);
        if (!customerId.equals(deployment.getCustomerId()) || deployment.getArchivedAt() != null
            || "ARCHIVED".equals(deployment.getStatus())) throw deny(RESOURCE_SCOPE_MISMATCH);
        return deployment;
    }

    private void requireOwnedAssociation(String customerId, String offerId, DeploymentEntity deployment) {
        var association = associations.findById(deployment.getId()).orElseThrow(() -> deny(ENTITLEMENT_REQUIRED));
        if (!customerId.equals(association.getCustomerId()) || !offerId.equals(association.getOfferId())
            || !association.getTenantId().equals(deployment.getTenantId())) throw deny(RESOURCE_SCOPE_MISMATCH);
    }

    private void requireRelease(String deploymentId, String targetProfileId, String releaseId) {
        releases.findById(releaseId).filter(release -> deploymentId.equals(release.getDeploymentId())
            && targetProfileId.equals(release.getTargetProfileId())).orElseThrow(() -> deny(RESOURCE_SCOPE_MISMATCH));
    }

    private TargetRetirementContracts.PlanRequest genericRequest(PlanRequest request, List<ConsumerExpectation> expected) {
        return new TargetRetirementContracts.PlanRequest(request.operationKey(), request.releaseIds(), expected,
            request.drainDeadline(), request.retention(), request.reason());
    }

    private PlanRequest normalize(String offerId, String deploymentId, PlanRequest request) {
        if (blank(offerId, 64) || blank(deploymentId, 64) || request == null || blank(request.targetProfileId(), 64)
            || request.releaseIds() == null || request.releaseIds().isEmpty() || request.releaseIds().size() > 128
            || request.releaseIds().stream().anyMatch(id -> blank(id, 64)) || request.drainDeadline() == null
            || request.retention() == null || request.retention().decision() == null || blank(request.reason(), 1000)) throw invalid();
        entitlements.requireOperationKey(request.operationKey());
        List<String> selected = request.releaseIds().stream().sorted().toList();
        if (selected.stream().distinct().count() != selected.size()) throw invalid();
        return new PlanRequest(request.targetProfileId(), selected, request.drainDeadline(), request.retention(),
            request.operationKey(), request.reason());
    }

    private boolean blank(String value, int limit) {
        return value == null || value.isBlank() || value.length() > limit || !value.equals(value.trim());
    }
}
