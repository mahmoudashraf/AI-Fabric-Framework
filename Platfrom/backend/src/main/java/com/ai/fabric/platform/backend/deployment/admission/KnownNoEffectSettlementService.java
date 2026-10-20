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

import java.util.Map;
import java.util.Objects;

import static com.ai.fabric.platform.backend.deployment.admission.KnownNoEffectSettlementContracts.*;
import static org.springframework.http.HttpStatus.*;

@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@PreAuthorize("hasRole('CUSTOMER_ADMIN')")
public class KnownNoEffectSettlementService {
    private final AdmissionStore store;
    private final CustomerOfferEntitlementService entitlements;
    private final CustomerSelfServiceOperationBoundary boundary;
    private final DeploymentRepository deployments;
    private final DeploymentVersionRepository versions;
    private final AdmissionUnusedConsumerDetachment detachment;
    private final com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationIntegrity integrationIntegrity;

    public KnownNoEffectSettlementService(AdmissionStore store, CustomerOfferEntitlementService entitlements,
        CustomerSelfServiceOperationBoundary boundary, DeploymentRepository deployments, DeploymentVersionRepository versions,
        AdmissionUnusedConsumerDetachment detachment,
        com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationIntegrity integrationIntegrity) {
        this.store = store; this.entitlements = entitlements; this.boundary = boundary;
        this.deployments = deployments; this.versions = versions; this.detachment = detachment;
        this.integrationIntegrity = integrationIntegrity;
    }

    public Summary settle(String offerId, String deploymentId, String reservationId, Command command) {
        AdmissionRules.required(offerId, 64);
        var actor = entitlements.requireCustomerActor();
        var receipt = store.settleKnownNoEffect(actor.customerId(), deploymentId, reservationId, command,
            actor.userId(), reservation -> authorize(actor, offerId, deploymentId, reservation, command), detachment, integrationIntegrity);
        return new Summary(receipt.id(), receipt.reservation().id(), receipt.reservation().generation(),
            receipt.command().fence().revision(), receipt.reservation().revision(), receipt.deploymentId(),
            receipt.targetProfileId(), receipt.outcome(), receipt.reclamationPerformed(), receipt.settledAt(),
            receipt.command().failedReleases().stream().map(FailedRelease::releaseId).toList(), receipt.consumerDetachments());
    }

    public Plan plan(String offerId, String deploymentId, String reservationId, PlanRequest request) {
        AdmissionRules.required(offerId, 64);
        var actor = entitlements.requireCustomerActor();
        return store.planKnownNoEffect(actor.customerId(), deploymentId, reservationId, request, actor.userId(),
            (reservation, command) -> authorize(actor, offerId, deploymentId, reservation, command), integrationIntegrity);
    }

    private void authorize(CustomerOfferEntitlementService.CustomerActor actor, String offerId, String deploymentId,
        AdmissionContracts.Reservation reservation, Command command) {
        // Joins the fenced transaction; the normal entitlement service locks current owner authorization.
        var currentActor = entitlements.requireCustomerActor();
        if (!actor.equals(currentActor)) throw denied();
        var deployment = deployments.findById(deploymentId).orElseThrow(this::denied);
        if (!actor.customerId().equals(deployment.getCustomerId())
            || !Objects.equals(reservation.tenantId(), deployment.getTenantId())) throw denied();
        var version = versions.findById(command.versionId()).orElseThrow(this::denied);
        if (!deploymentId.equals(version.getDeploymentId()) || !"PUBLISHED".equals(version.getStatus())
            || !command.versionConfigHash().equals(version.getConfigHash())) throw denied();
        for (var failed : command.failedReleases()) {
            var approved = boundary.require(deployment, reservation.targetProfileId(), failed.releaseId(),
                Operation.TARGET_RETIRE, command.operationKey(), Map.of(), null, null, null);
            if (approved == null || !offerId.equals(approved.context().offerId())
                || !actor.customerId().equals(approved.context().customerId())) throw denied();
            boundary.requireVersion(approved, version);
        }
    }

    private AdmissionFailure denied() { return new AdmissionFailure(FORBIDDEN, "NO_EFFECT_SETTLEMENT_NOT_AUTHORIZED"); }
}
