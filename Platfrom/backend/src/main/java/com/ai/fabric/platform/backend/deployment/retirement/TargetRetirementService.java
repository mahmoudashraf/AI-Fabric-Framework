package com.ai.fabric.platform.backend.deployment.retirement;

import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentAccessService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerSelfServiceOperationBoundary;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

import static com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.*;
import static org.springframework.http.HttpStatus.*;

@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR','CUSTOMER_ADMIN')")
public class TargetRetirementService {
    private final DeploymentRepository deployments;
    private final DeploymentAccessService access;
    private final TargetRetirementOperationRepository operations;
    private final TargetRetirementStore store;
    private final TargetRetirementParticipation participation;
    private final CustomerSelfServiceOperationBoundary offerBoundary;

    public TargetRetirementService(DeploymentRepository deployments, DeploymentAccessService access,
                                   TargetRetirementOperationRepository operations, TargetRetirementStore store,
                                   TargetRetirementParticipation participation, CustomerSelfServiceOperationBoundary offerBoundary) {
        this.deployments = deployments;
        this.access = access;
        this.operations = operations;
        this.store = store;
        this.participation = participation;
        this.offerBoundary = offerBoundary;
    }

    public Summary plan(String deploymentId, String targetProfileId, PlanRequest request) {
        authorize(deploymentId, true);
        required(targetProfileId, 64);
        var normalized = normalize(request);
        requireOffer(deploymentId, targetProfileId, normalized.releaseIds(), normalized.operationKey(), true);
        var summary = store.plan(deploymentId, targetProfileId, normalized);
        if (summary.state() == State.PLANNED) {
            var operation = store.require(summary.id());
            try {
                var inspection = participation.inspect(store.context(operation));
                store.evidence(store.context(operation), inspection.evidence(), inspection.interlockEvidence());
                store.preview(operation.getId(), inspection.blockers());
            } catch (TargetRetirementFailure ex) { store.preview(operation.getId(), List.of(ex.code())); }
            catch (RuntimeException ex) { store.preview(operation.getId(), List.of("PARTICIPANT_UNAVAILABLE")); }
        }
        return status(deploymentId, targetProfileId, summary.id());
    }
    public Summary execute(String deploymentId, String targetId, String operationId, Command command) {
        requireOwned(deploymentId, targetId, operationId, true);
        return store.execute(operationId, command);
    }
    public Summary retry(String deploymentId, String targetId, String operationId, Command command) {
        var operation = requireOwned(deploymentId, targetId, operationId, true);
        store.requireCommand(operation, command);
        store.requireRetryAllowed(operation);
        if (List.of(State.QUEUED, State.RUNNING, State.WAITING, State.RETAINED, State.COMPLETE, State.RETIRED_WITH_RETAINED_DATA).contains(operation.getState()))
            return store.retry(operationId, command, null);
        RecoveryEvidence recovery = null;
        if (operation.getInFlightEffect() != null) {
            recovery = participation.interlock().recover(store.context(operation), operation.getWorkerToken(), operation.getInFlightEffect());
        }
        return store.retry(operationId, command, recovery);
    }
    public Summary cancel(String deploymentId, String targetId, String operationId, Command command) {
        requireOwned(deploymentId, targetId, operationId, true);
        return store.cancel(operationId, command);
    }
    public Summary status(String deploymentId, String targetId, String operationId) {
        return store.summary(requireOwned(deploymentId, targetId, operationId, false));
    }
    public List<Summary> list(String deploymentId, String targetId) {
        authorize(deploymentId, false);
        requireOffer(deploymentId, targetId, List.of(), null, false);
        return operations.findByDeploymentIdAndTargetProfileIdOrderByCreatedAtDesc(deploymentId, required(targetId, 64))
            .stream().map(store::summary).toList();
    }

    /** Invoke AFTER the successful activation transaction commits, under its authenticated actor. */
    public Summary scheduleAfterPromotion(String deploymentId, String stagingTargetId, PlanRequest request) {
        var plan = plan(deploymentId, stagingTargetId, request);
        return execute(deploymentId, stagingTargetId, plan.id(), new Command(plan.requestFingerprint()));
    }

    private TargetRetirementOperationEntity requireOwned(String deploymentId, String targetId, String id, boolean mutate) {
        authorize(deploymentId, mutate);
        var operation = store.require(required(id, 64));
        if (!deploymentId.equals(operation.getDeploymentId()) || !targetId.equals(operation.getTargetProfileId()))
            throw new ResponseStatusException(NOT_FOUND, "Retirement not found.");
        var deployment = deployments.findById(deploymentId).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        if (!operation.getCustomerId().equals(deployment.getCustomerId())) throw new ResponseStatusException(CONFLICT, "OWNERSHIP_CHANGED");
        var releases = store.summary(operation).snapshot().releases().stream().map(ReleaseGeneration::releaseId).toList();
        requireOffer(deploymentId, targetId, releases, operation.getOperationKey(), mutate);
        return operation;
    }

    private void requireOffer(String deploymentId, String targetId, List<String> releaseIds, String operationKey, boolean mutate) {
        var deployment = deployments.findById(deploymentId).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        if (!mutate) {
            offerBoundary.require(deployment, targetId, null, Operation.READ, null, java.util.Map.of(), null, null, null);
        } else {
            for (String releaseId : releaseIds) {
                offerBoundary.require(deployment, targetId, releaseId, Operation.TARGET_RETIRE, operationKey,
                    java.util.Map.of(), null, null, null);
            }
        }
    }
    private void authorize(String deploymentId, boolean mutate) {
        if (!PlatformSecurityContext.isAuthenticated()) throw new ResponseStatusException(FORBIDDEN, "Authentication required.");
        var deployment = deployments.findById(required(deploymentId, 64))
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment not found."));
        if (mutate) access.requireDeploymentOperatorAccess(deployment);
        else access.requireDeploymentAccess(deployment);
    }
    private PlanRequest normalize(PlanRequest request) {
        if (request == null || request.releaseIds() == null || request.releaseIds().isEmpty() || request.releaseIds().size() > 128
            || request.consumers() == null || request.consumers().size() > 256 || request.drainDeadline() == null
            || request.retention() == null || request.retention().decision() == null) throw badRequest();
        var releases = request.releaseIds().stream().map(id -> required(id, 64)).sorted().toList();
        if (new HashSet<>(releases).size() != releases.size()) throw badRequest();
        var consumers = request.consumers().stream().map(expectation -> {
            if (expectation == null || expectation.expectedBindingRevision() < 0) throw badRequest();
            return new ConsumerExpectation(required(expectation.consumerEntityId(), 64), expectation.expectedBindingRevision());
        }).sorted(Comparator.comparing(ConsumerExpectation::consumerEntityId)).toList();
        if (consumers.stream().map(ConsumerExpectation::consumerEntityId).distinct().count() != consumers.size()) throw badRequest();
        if (Duration.between(Instant.now(), request.drainDeadline()).abs().compareTo(Duration.ofDays(30)) > 0) throw badRequest();
        var retention = request.retention();
        if (retention.externalIndexRetention() != null && retention.decision() != RetentionDecision.RECLAIM) throw badRequest();
        required(retention.backupDecisionReference(), 255);
        if (retention.decision() == RetentionDecision.RETAIN_UNTIL) {
            if (retention.expiresAt() == null || retention.expiresAt().isAfter(Instant.now().plus(Duration.ofDays(30)))) throw badRequest();
            required(retention.entitlementReference(), 255);
            if (!request.drainDeadline().isAfter(retention.expiresAt())) throw badRequest();
        } else if (retention.expiresAt() != null || retention.entitlementReference() != null) throw badRequest();
        return new PlanRequest(required(request.operationKey(), 128), releases, consumers, request.drainDeadline(), retention, required(request.reason(), 1000));
    }
    private String required(String value, int limit) {
        if (value == null || value.isBlank() || value.length() > limit || !value.equals(value.trim())) throw badRequest();
        return value;
    }
    private ResponseStatusException badRequest() { return new ResponseStatusException(BAD_REQUEST, "RETIREMENT_REQUEST_INVALID"); }
}
