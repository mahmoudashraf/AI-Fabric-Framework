package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementSystemAuthorizer;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementOperationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.ExpiryState;
import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.Intent;
import static com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.*;

/** Authorizes only the exact R7 expiry operation proven by the durable admission reservation. */
@Component
public final class AdmissionTargetRetirementSystemAuthorizer implements TargetRetirementSystemAuthorizer {
    private static final String PREFIX = "admission-expiry:";
    private final AdmissionStore admissions;
    private final TargetRetirementOperationRepository operations;
    private final Clock clock;

    @Autowired
    AdmissionTargetRetirementSystemAuthorizer(AdmissionStore admissions,
                                              TargetRetirementOperationRepository operations) {
        this(admissions, operations, Clock.systemUTC());
    }

    AdmissionTargetRetirementSystemAuthorizer(AdmissionStore admissions,
                                              TargetRetirementOperationRepository operations,
                                              Clock clock) {
        this.admissions = admissions;
        this.operations = operations;
        this.clock = clock;
    }

    @Override
    public boolean supports(Context context) {
        return context != null && "system".equals(context.requestedByActorId())
            && "SYSTEM".equals(context.requestedByRole()) && context.operationKey() != null
            && context.operationKey().startsWith(PREFIX);
    }

    @Override
    public String blocker(Context context) {
        if (!supports(context) || context.snapshot() == null || context.retention() == null
            || context.retention().decision() != RetentionDecision.RECLAIM
            || context.retention().expiresAt() != null || context.retention().entitlementReference() != null) {
            return "RETIREMENT_ACTOR_REVOKED";
        }
        List<String> key = List.of(context.operationKey().substring(PREFIX.length()).split(":", -1));
        if (key.size() != 2 || key.get(0).isBlank() || key.get(1).isBlank()) return "RETIREMENT_ACTOR_REVOKED";
        try {
            var reservation = admissions.require(key.get(0));
            var policy = admissions.reviewed("POLICY", reservation.policyId(),
                AdmissionContracts.FleetPolicy.class);
            var operation = operations.findById(context.operationId()).orElse(null);
            if (operation == null) return "RETIREMENT_ACTOR_REVOKED";
            boolean stateAuthorized = (reservation.expiryState() == ExpiryState.CLAIMED
                && reservation.retirementOperationId() == null)
                || (reservation.expiryState() == ExpiryState.HANDED_OFF
                    && context.operationId().equals(reservation.retirementOperationId()));
            boolean exact = stateAuthorized && reservation.intent() == Intent.CUSTOMER_STAGING
                && reservation.consumesCapacity() && key.get(1).equals(reservation.generation())
                && context.operationKey().equals(PREFIX + reservation.id() + ":" + reservation.generation())
                && reservation.customerId().equals(context.snapshot().customerId())
                && reservation.tenantId().equals(context.snapshot().tenantId())
                && reservation.deploymentId().equals(context.snapshot().deploymentId())
                && reservation.targetProfileId().equals(context.snapshot().targetProfileId())
                && reservation.workerToken() != null && !reservation.workerToken().isBlank()
                && reservation.drainDeadline() != null
                && reservation.drainDeadline().equals(context.drainDeadline())
                && policy.restoreEvidenceReference().equals(context.retention().backupDecisionReference())
                && reservation.recoveryUntil() != null && reservation.expiresAt() != null
                && !reservation.expiresAt().isAfter(clock.instant());
            if (!exact) return "RETIREMENT_ACTOR_REVOKED";
            boolean nonDestructive = operation.getState()
                == com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.State.PLANNED
                || operation.getStep() == Step.DRAIN || operation.getStep() == Step.CANCEL_DRAIN;
            return nonDestructive || !clock.instant().isBefore(reservation.recoveryUntil())
                ? null : "RECOVERY_WINDOW_NOT_ELAPSED";
        } catch (AdmissionFailure failure) {
            return "RETIREMENT_ACTOR_REVOKED";
        }
    }
}
