package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.AdmissionContext;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.AuthorizationRequest;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerSelfServiceAuthorizationService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerTargetPlacementService;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static org.springframework.http.HttpStatus.PRECONDITION_FAILED;

/** Database-only owning-command revalidation, called inside the renewal transaction. */
@Component
public class InitialAdmissionRenewalAuthorizer {
    private final CustomerSelfServiceAuthorizationService authorization;
    private final CustomerTargetPlacementService placement;
    private final DeploymentTargetProfileRepository targets;
    @PersistenceContext private EntityManager entityManager;

    public InitialAdmissionRenewalAuthorizer(CustomerSelfServiceAuthorizationService authorization,
        CustomerTargetPlacementService placement, DeploymentTargetProfileRepository targets) {
        this.authorization = authorization; this.placement = placement; this.targets = targets;
    }
    /**
     * Require the active owning customer, exact prepared context and entitlement revision. Validate the
     * exact placement ID and bind readback.observedAt/validUntil to that row's checkedAt/expiresAt.
     * Never probe a provider or suspend this transaction; a failed check must roll back the renewal.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireCurrent(AdmissionContext context, AdmissionContracts.PrerequisiteReadback readback) {
        if (context == null || readback == null || readback.observedAt() == null || readback.validUntil() == null
            || readback.placement() == null || readback.profileFingerprint() == null)
            throw new AdmissionFailure(PRECONDITION_FAILED, "INITIAL_RENEWAL_PLACEMENT_PROOF_REQUIRED");
        // This is the dedicated renewal transaction. Do not reuse pre-probe OpenEntityManagerInView entities.
        if (entityManager != null) entityManager.clear();
        var current = authorization.prepareAdmission(context.customerId(), new AuthorizationRequest(context.offerId(),
            context.targetProfileId(), context.deploymentId(), context.tenantId(), null, context.operation(),
            context.operationKey(), Set.of(), null, null, null));
        if (!context.equals(current.context())) throw new AdmissionFailure(PRECONDITION_FAILED, "INITIAL_RENEWAL_CUSTOMER_CONTEXT_CHANGED");
        var profile = targets.findById(context.targetProfileId())
            .orElseThrow(() -> new AdmissionFailure(PRECONDITION_FAILED, "INITIAL_RENEWAL_TARGET_MISSING"));
        var proof = placement.requireEvidenceById(profile, context.intent(), context.placementEvidenceId());
        if (!context.placementEvidenceId().equals(proof.getId()) || !readback.observedAt().equals(proof.getCheckedAt())
            || readback.validUntil().isAfter(proof.getExpiresAt())
            || !readback.profileFingerprint().equals(proof.getProfileFingerprint())
            || !readback.placement().equals(new AdmissionContracts.Placement(proof.getServerUuid(),
                proof.getDestinationUuid(), proof.getServerAddress())))
            throw new AdmissionFailure(PRECONDITION_FAILED, "INITIAL_RENEWAL_PLACEMENT_PROOF_CHANGED");
    }
}
