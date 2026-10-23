package com.ai.fabric.platform.backend.customerselfservice.service;

import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferDeploymentEntity;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerAdmissionReservationContracts.Request;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerAdmissionReservationContracts.RefreshRequest;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerAdmissionReservationContracts.Summary;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Authorization;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.AuthorizationRequest;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.TargetIntent;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.Reservation;
import com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.Reserve;
import com.ai.fabric.platform.backend.deployment.admission.CustomerAdmissionManifestService;
import com.ai.fabric.platform.backend.deployment.admission.DeploymentAdmissionService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.RESOURCE_SCOPE_MISMATCH;
import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.deny;
import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.invalid;

/** Bridges reviewed customer authorization to the durable R7 admission ledger without holding provider locks. */
@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class CustomerAdmissionReservationService {
    private final CustomerOfferEntitlementService entitlements;
    private final CustomerSelfServiceAuthorizationService authorization;
    private final DeploymentRepository deployments;
    private final DeploymentVersionRepository versions;
    private final DeploymentReleaseRepository releases;
    private final CustomerOfferDeploymentRepository offerDeployments;
    private final CustomerAdmissionManifestService manifests;
    private final DeploymentAdmissionService admissions;
    private final TransactionTemplate preparation;

    public CustomerAdmissionReservationService(CustomerOfferEntitlementService entitlements,
                                               CustomerSelfServiceAuthorizationService authorization,
                                               DeploymentRepository deployments,
                                               DeploymentVersionRepository versions,
                                               DeploymentReleaseRepository releases,
                                               CustomerOfferDeploymentRepository offerDeployments,
                                               CustomerAdmissionManifestService manifests,
                                               DeploymentAdmissionService admissions,
                                               PlatformTransactionManager transactionManager) {
        this.entitlements = entitlements;
        this.authorization = authorization;
        this.deployments = deployments;
        this.versions = versions;
        this.releases = releases;
        this.offerDeployments = offerDeployments;
        this.manifests = manifests;
        this.admissions = admissions;
        this.preparation = new TransactionTemplate(transactionManager);
        this.preparation.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public Summary reserve(String offerId, String deploymentId, Request request) {
        validate(request);
        var actor = entitlements.requireCustomerActor();
        DeploymentEntity deployment = ownedDeployment(actor.customerId(), deploymentId);
        Authorization prepared = preparation.execute(status -> prepare(offerId, deployment, request));
        if (prepared == null) throw deny(RESOURCE_SCOPE_MISMATCH);
        var version = versions.findById(request.versionId()).orElseThrow(() -> deny(RESOURCE_SCOPE_MISMATCH));
        var manifest = manifests.prepare(deployment, version, prepared);
        Reservation reservation = admissions.reserveCustomer(deploymentId, request.versionId(),
            new Reserve(request.operationKey(), manifest.id(), prepared.context(), request.allocationId()));
        Summary result = summary(reservation);
        return new Summary(result.id(), result.deploymentId(), result.targetProfileId(), result.intent(),
            request.operation(), result.state(), result.revision(), result.expiresAt(), result.expiryState(),
            result.keepUntil(), result.occupiesActiveSlot(), result.chargedDemand());
    }

    public Summary refreshEvidence(String offerId, String deploymentId, String reservationId, RefreshRequest request) {
        if (blank(offerId, 64) || blank(reservationId, 64) || request == null || request.expectedRevision() < 0
            || (request.operation() != Operation.APPLY && request.operation() != Operation.PROMOTION_APPLY)) throw invalid();
        Request originalApply = new Request(request.targetProfileId(), request.versionId(), null,
            request.operation(), request.operationKey());
        validate(originalApply);
        var actor = entitlements.requireCustomerActor();
        DeploymentEntity deployment = ownedDeployment(actor.customerId(), deploymentId);
        Authorization prepared = preparation.execute(status -> prepare(offerId, deployment, originalApply));
        if (prepared == null) throw deny(RESOURCE_SCOPE_MISMATCH);
        var version = versions.findById(request.versionId()).orElseThrow(() -> deny(RESOURCE_SCOPE_MISMATCH));
        var manifest = manifests.prepare(deployment, version, prepared);
        return summary(admissions.refreshCustomerEvidence(deploymentId, request.versionId(), reservationId,
            request.expectedRevision(), new Reserve(request.operationKey(), manifest.id(), prepared.context(), null)));
    }

    public Summary status(String offerId, String deploymentId, String reservationId) {
        if (blank(offerId, 64) || blank(deploymentId, 64) || blank(reservationId, 64)) throw invalid();
        var actor = entitlements.requireCustomerActor();
        DeploymentEntity deployment = ownedDeployment(actor.customerId(), deploymentId);
        Reservation reservation = admissions.status(deploymentId, reservationId);
        authorizeRead(actor.customerId(), offerId, deployment, reservation.targetProfileId());
        return summary(reservation);
    }

    public List<Summary> list(String offerId, String deploymentId, String targetProfileId) {
        if (blank(offerId, 64) || blank(deploymentId, 64) || blank(targetProfileId, 64)) throw invalid();
        var actor = entitlements.requireCustomerActor();
        DeploymentEntity deployment = ownedDeployment(actor.customerId(), deploymentId);
        authorizeRead(actor.customerId(), offerId, deployment, targetProfileId);
        return admissions.activeCustomerReservations(deploymentId, targetProfileId).stream()
            .sorted(Comparator.comparing(Reservation::updatedAt).reversed())
            .map(this::summary)
            .toList();
    }

    private void authorizeRead(String customerId, String offerId, DeploymentEntity deployment, String targetProfileId) {
        if (blank(targetProfileId, 64)) throw deny(RESOURCE_SCOPE_MISMATCH);
        Boolean authorized = preparation.execute(status -> {
            requireAssociation(offerId, deployment);
            authorization.authorizeOwnedTargetRead(customerId, new AuthorizationRequest(offerId, targetProfileId,
                deployment.getId(), deployment.getTenantId(), null, Operation.READ, null, Set.of(), null, null, null));
            return Boolean.TRUE;
        });
        if (!Boolean.TRUE.equals(authorized)) throw deny(RESOURCE_SCOPE_MISMATCH);
    }

    private Authorization prepare(String offerId, DeploymentEntity deployment, Request request) {
        Authorization prepared = authorization.prepareAdmission(deployment.getCustomerId(), new AuthorizationRequest(
            offerId, request.targetProfileId(), deployment.getId(), deployment.getTenantId(), request.releaseId(),
            request.operation(), request.operationKey(), Set.of(), null, null, null));
        var version = versions.findById(request.versionId()).orElseThrow(() -> deny(RESOURCE_SCOPE_MISMATCH));
        if (!deployment.getId().equals(version.getDeploymentId()) || !"PUBLISHED".equalsIgnoreCase(version.getStatus())) {
            throw deny(RESOURCE_SCOPE_MISMATCH);
        }
        authorization.requireReviewedVersion(prepared, version);
        if (request.releaseId() != null) {
            var release = releases.findById(request.releaseId()).orElseThrow(() -> deny(RESOURCE_SCOPE_MISMATCH));
            if (!deployment.getId().equals(release.getDeploymentId())
                || !request.versionId().equals(release.getDeploymentVersionId())
                || !request.targetProfileId().equals(release.getTargetProfileId())) {
                throw deny(RESOURCE_SCOPE_MISMATCH);
            }
        }
        return prepared;
    }

    private DeploymentEntity ownedDeployment(String customerId, String deploymentId) {
        if (blank(deploymentId, 64)) throw invalid();
        return deployments.findById(deploymentId).filter(deployment -> customerId.equals(deployment.getCustomerId()))
            .orElseThrow(() -> deny(RESOURCE_SCOPE_MISMATCH));
    }

    private void requireAssociation(String offerId, DeploymentEntity deployment) {
        CustomerOfferDeploymentEntity association = offerDeployments.findById(deployment.getId())
            .orElseThrow(() -> deny(RESOURCE_SCOPE_MISMATCH));
        if (!offerId.equals(association.getOfferId()) || !deployment.getCustomerId().equals(association.getCustomerId())
            || !deployment.getTenantId().equals(association.getTenantId())) throw deny(RESOURCE_SCOPE_MISMATCH);
    }

    private Summary summary(Reservation reservation) {
        if (reservation == null || reservation.customerOperation() == null) throw deny(RESOURCE_SCOPE_MISMATCH);
        TargetIntent intent;
        try { intent = TargetIntent.valueOf(reservation.intent().name()); }
        catch (RuntimeException ex) { throw deny(RESOURCE_SCOPE_MISMATCH); }
        return new Summary(reservation.id(), reservation.deploymentId(), reservation.targetProfileId(), intent,
            reservation.customerOperation(), reservation.state().name(), reservation.revision(), reservation.expiresAt(),
            reservation.expiryState().name(), reservation.keepUntil(), reservation.occupiesActiveSlot(), reservation.chargedDemand());
    }

    private void validate(Request request) {
        if (request == null || blank(request.targetProfileId(), 64) || blank(request.versionId(), 64)
            || request.operation() == null || blank(request.operationKey(), 128)
            || (request.allocationId() != null && blank(request.allocationId(), 64))
            || (request.releaseId() != null && blank(request.releaseId(), 64))) throw invalid();
    }

    private boolean blank(String value, int maxLength) {
        return value == null || value.isBlank() || value.length() > maxLength || !value.equals(value.trim());
    }
}
