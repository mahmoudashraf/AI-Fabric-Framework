package com.loomai.operations.service;

import static com.loomai.operations.model.OperationsContracts.*;

import com.loomai.operations.config.OperationsProperties;
import com.loomai.operations.config.TargetCapacityPolicy;
import com.loomai.operations.persistence.OperationsRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DeploymentOperationsService {

    private final OperationsRepository repository;
    private final TargetCapacityPolicy policy;
    private final OperationsProperties properties;
    private final Clock clock;

    public DeploymentOperationsService(OperationsRepository repository, TargetCapacityPolicy policy,
                                       OperationsProperties properties, Clock clock) {
        this.repository = repository;
        this.policy = policy;
        this.properties = properties;
        this.clock = clock;
    }

    public AssessmentResponse assess(AssessmentRequest request) {
        return repository.locked(() -> {
            String digest = repository.digest(request);
            StoredAssessment existing = repository.assessment(request.commandId()).orElse(null);
            if (existing != null) {
                if (!existing.request().requestFingerprint().equals(request.requestFingerprint())
                    || !existing.requestDigest().equals(digest)) {
                    throw conflict("COMMAND_PAYLOAD_CONFLICT", "Command identity was reused with different request data.");
                }
                return existing.response();
            }
            Instant now = clock.instant();
            requireFresh(request.requestedAt(), now);
            if (!request.resourceDemand().validRequest()) {
                throw new OperationsException(400, "RESOURCE_DEMAND_INVALID",
                    "CPU, memory, replicas and concurrent applies must be positive; disk and other counts may be zero.");
            }
            repository.expireHolds(now);
            for (Allocation allocation : repository.deploymentAllocations(request.deploymentId())) {
                requireScope(allocation, request);
            }
            Allocation current = repository.allocation(request.deploymentId(), request.targetProfileId()).orElse(null);
            requireScope(current, request);
            ResourceDemand replacement = current == null || current.state() == AllocationState.STOPPED
                ? request.resourceDemand() : current.demand().max(request.resourceDemand());
            ResourceDemand total = replacement;
            for (Allocation allocation : repository.allocations(request.targetProfileId())) {
                if (!allocation.deploymentId().equals(request.deploymentId())) {
                    total = total.plus(allocation.demand());
                }
            }
            ResourceDemand limit = policy.targets().get(request.targetProfileId());
            Decision decision = limit == null ? Decision.UNAVAILABLE : total.fits(limit) ? Decision.ALLOW : Decision.DENY;
            String reason = switch (decision) {
                case ALLOW -> "WITHIN_TARGET_CAPACITY";
                case DENY -> "TARGET_CAPACITY_EXCEEDED";
                case UNAVAILABLE -> "TARGET_POLICY_UNAVAILABLE";
            };
            AssessmentResponse response = new AssessmentResponse("1", request.commandId(), request.requestFingerprint(),
                "assess-" + UUID.randomUUID(), decision, reason, policy.revision(), now, now.plus(properties.holdTtl()));
            repository.saveAssessment(request, digest, response);
            if (decision != Decision.DENY || request.mode() == Mode.OBSERVE) {
                repository.saveAllocation(new Allocation(request.customerId(), request.tenantId(), request.deploymentId(),
                    request.targetProfileId(), request.commandId(), replacement, AllocationState.HOLD,
                    response.expiresAt(), now));
            }
            return response;
        });
    }

    public void outcome(OutcomeRequest outcome) {
        repository.locked(() -> {
            StoredAssessment assessment = repository.assessment(outcome.commandId())
                .orElseThrow(() -> new OperationsException(404, "ASSESSMENT_NOT_FOUND", "Assessment does not exist."));
            if (!assessment.request().requestFingerprint().equals(outcome.requestFingerprint())) {
                throw conflict("OUTCOME_FINGERPRINT_CONFLICT", "Outcome fingerprint does not match the assessed request.");
            }
            String digest = repository.digest(outcome);
            if (repository.hasOutcome(digest)) {
                return null;
            }
            Instant now = clock.instant();
            if (outcome.observedAt().isAfter(now.plus(properties.maxClockSkew()))
                || outcome.observedAt().isBefore(assessment.response().observedAt())) {
                throw new OperationsException(400, "OUTCOME_TIME_INVALID", "Outcome observation time is outside its permitted range.");
            }
            OutcomeRequest last = assessment.lastOutcome();
            if (last != null && !outcome.observedAt().isAfter(last.observedAt())) {
                throw conflict("OUTCOME_STALE", "A newer or conflicting outcome has already been recorded.");
            }
            if (last != null && last.status() == OutcomeStatus.STOPPED) {
                throw conflict("OUTCOME_TERMINAL", "A stopped command cannot be reactivated; use a new assessment command.");
            }
            Allocation current = repository.allocation(assessment.request().deploymentId(), assessment.request().targetProfileId())
                .orElseThrow(() -> conflict("OUTCOME_NOT_ADMITTED", "An enforced denial cannot report provider activation."));
            requireScope(current, assessment.request());
            if (!current.commandId().equals(outcome.commandId())) {
                throw conflict("OUTCOME_SUPERSEDED", "Outcome belongs to a superseded assessment, not the current target operation.");
            }
            AllocationState state = switch (outcome.status()) {
                case ACTIVE -> AllocationState.ACTIVE;
                case FAILED -> AllocationState.UNCONFIRMED;
                case STOPPED -> AllocationState.STOPPED;
            };
            ResourceDemand demand = outcome.status() == OutcomeStatus.ACTIVE
                ? assessment.request().resourceDemand().withoutApplySlot() : current.demand();
            repository.saveAllocation(new Allocation(current.customerId(), current.tenantId(), current.deploymentId(),
                current.targetProfileId(), current.commandId(), demand, state, current.holdExpiresAt(), now));
            repository.saveOutcome(outcome, digest, "outcome-" + UUID.randomUUID());
            return null;
        });
    }

    private void requireFresh(Instant requestedAt, Instant now) {
        if (requestedAt.isBefore(now.minus(properties.maxRequestAge()))
            || requestedAt.isAfter(now.plus(properties.maxClockSkew()))) {
            throw new OperationsException(400, "REQUEST_TIME_INVALID", "Request timestamp is outside its permitted range.");
        }
    }

    private void requireScope(Allocation allocation, AssessmentRequest request) {
        if (allocation != null && (!allocation.customerId().equals(request.customerId())
            || !allocation.tenantId().equals(request.tenantId()))) {
            throw conflict("DEPLOYMENT_SCOPE_CONFLICT", "Deployment target already belongs to a different customer or tenant.");
        }
    }

    private OperationsException conflict(String code, String message) {
        return new OperationsException(409, code, message);
    }
}
