package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferEntitlementService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerSelfServiceOperationBoundary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;
import static com.ai.fabric.platform.backend.deployment.admission.PreparedFailureRecoveryContracts.*;
import static org.springframework.http.HttpStatus.FORBIDDEN;

@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@PreAuthorize("hasRole('CUSTOMER_ADMIN')")
public class PreparedFailureRecoveryService {
    private final AdmissionStore store;
    private final CustomerOfferEntitlementService entitlements;
    private final CustomerSelfServiceOperationBoundary boundary;
    private final DeploymentRepository deployments;
    private final DeploymentVersionRepository versions;

    public PreparedFailureRecoveryService(AdmissionStore store, CustomerOfferEntitlementService entitlements,
        CustomerSelfServiceOperationBoundary boundary, DeploymentRepository deployments, DeploymentVersionRepository versions) {
        this.store = store; this.entitlements = entitlements; this.boundary = boundary;
        this.deployments = deployments; this.versions = versions;
    }

    public Plan plan(String offerId, String deploymentId, String reservationId, PlanRequest request) {
        if (request == null) throw new AdmissionFailure(org.springframework.http.HttpStatus.BAD_REQUEST, "PREPARED_RECOVERY_COMMAND_REQUIRED");
        var actor = entitlements.requireCustomerActor();
        var reservation = store.require(reservationId);
        var draft = new Command(reservation.fence(), request.releaseId(), request.operationKey(), request.reason(), null);
        return store.planPreparedFailureRecovery(actor.customerId(), deploymentId, reservationId, draft,
            r -> authorize(actor, offerId, deploymentId, r, draft.operationKey()));
    }

    public Summary recover(String offerId, String deploymentId, String reservationId, Command command) {
        var actor = entitlements.requireCustomerActor();
        return store.recoverPreparedFailure(actor.customerId(), deploymentId, reservationId, command, actor.userId(),
            r -> authorize(actor, offerId, deploymentId, r, command.operationKey()));
    }

    private void authorize(CustomerOfferEntitlementService.CustomerActor actor, String offerId, String deploymentId,
                           AdmissionContracts.Reservation reservation, String operationKey) {
        if (!actor.equals(entitlements.requireCustomerActor())) throw denied();
        var deployment = deployments.findById(deploymentId).orElseThrow(this::denied);
        if (!actor.customerId().equals(deployment.getCustomerId()) || !Objects.equals(reservation.tenantId(), deployment.getTenantId())) throw denied();
        // Recovery repairs admission only. It cannot satisfy or skip the next apply's fresh prerequisites.
        var operation = reservation.intent() == AdmissionContracts.Intent.CUSTOMER_PRODUCTION
            ? Operation.PROMOTION_APPLY : Operation.APPLY;
        var approved = boundary.prepareAdmission(deployment, reservation.targetProfileId(), operation, operationKey);
        if (approved == null || !offerId.equals(approved.context().offerId()) || !actor.customerId().equals(approved.context().customerId())) throw denied();
        var manifest = store.context(reservation).manifest();
        var version = versions.findById(manifest.versionId()).orElseThrow(this::denied);
        if (!deploymentId.equals(version.getDeploymentId()) || !"PUBLISHED".equals(version.getStatus())
            || !manifest.versionConfigHash().equals(version.getConfigHash())) throw denied();
        boundary.requireVersion(approved, version);
    }

    private AdmissionFailure denied() { return new AdmissionFailure(FORBIDDEN, "PREPARED_RECOVERY_NOT_AUTHORIZED"); }
}
