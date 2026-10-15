package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerAdmissionReservationContracts.ExtensionRequest;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerAdmissionReservationContracts.Summary;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferEntitlementService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerSelfServiceAuthorizationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Set;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static com.ai.fabric.platform.backend.deployment.admission.AdmissionRules.required;

@Service
@PreAuthorize("hasRole('CUSTOMER_ADMIN')")
public class CustomerStagingLifecycleService {
    private final CustomerOfferEntitlementService entitlements;
    private final CustomerSelfServiceAuthorizationService authorization;
    private final DeploymentAdmissionService admissions;
    private final AdmissionStore store;
    private final AdmissionJson json;
    private final java.time.Clock clock;
    private final org.springframework.transaction.support.TransactionTemplate transactions;

    @org.springframework.beans.factory.annotation.Autowired
    public CustomerStagingLifecycleService(CustomerOfferEntitlementService entitlements,
        CustomerSelfServiceAuthorizationService authorization, DeploymentAdmissionService admissions,
        AdmissionStore store, AdmissionJson json, org.springframework.transaction.PlatformTransactionManager transactionManager) {
        this(entitlements, authorization, admissions, store, json, transactionManager, java.time.Clock.systemUTC());
    }

    CustomerStagingLifecycleService(CustomerOfferEntitlementService entitlements,
        CustomerSelfServiceAuthorizationService authorization, DeploymentAdmissionService admissions,
        AdmissionStore store, AdmissionJson json, org.springframework.transaction.PlatformTransactionManager transactionManager,
        java.time.Clock clock) {
        this.entitlements = entitlements;
        this.authorization = authorization;
        this.admissions = admissions;
        this.store = store;
        this.json = json;
        this.clock = clock;
        this.transactions = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public Summary extend(String offerId, String deploymentId, ExtensionRequest request) {
        required(offerId, 64); required(deploymentId, 64);
        if (request == null || request.expectedRevision() < 0) throw invalid();
        required(request.targetProfileId(), 64); required(request.allocationId(), 64);
        required(request.operationKey(), 128); required(request.reason(), 500);
        var actor = entitlements.requireCustomerActor();
        Reservation current = admissions.status(deploymentId, request.allocationId());
        if (!actor.customerId().equals(current.customerId()) || !request.targetProfileId().equals(current.targetProfileId())
            || current.intent() != Intent.CUSTOMER_STAGING) throw invalid();
        Operation operation = request.keepAfterPromotion() ? Operation.STAGING_RETAIN : Operation.STAGING_EXTEND;
        AuthorizationRequest intent = new AuthorizationRequest(offerId, request.targetProfileId(), deploymentId,
            current.tenantId(), store.currentReleaseId(current), operation, request.operationKey(), Set.of(), null, null, null);
        var prepared = transactions.execute(tx -> authorization.prepareAdmission(actor.customerId(), intent));
        if (prepared == null) throw invalid();
        var policy = store.reviewed("POLICY", current.policyId(), FleetPolicy.class).lifecycle();
        long offerExtensionSeconds = Math.multiplyExact((long) prepared.definition().limits().maxExtensionHours(), 3_600L);
        if ((request.keepAfterPromotion() ? policy.maxKeepSeconds() : policy.extensionSeconds()) > offerExtensionSeconds) {
            throw new AdmissionFailure(org.springframework.http.HttpStatus.CONFLICT, "REVIEWED_STAGING_EXTENSION_LIMIT_CHANGED");
        }
        String fingerprint = json.fingerprint(request);
        Reservation replay = store.extensionReplay(current.id(), request.operationKey(), fingerprint);
        if (replay != null) return summary(replay, operation);
        if (current.revision() != request.expectedRevision()) throw new AdmissionFailure(
            org.springframework.http.HttpStatus.CONFLICT, "STALE_RESERVATION_FENCE");
        Manifest manifest = store.reviewed("MANIFEST", current.manifestId(), Manifest.class);
        Reservation permitted = admissions.reserveCustomer(deploymentId, manifest.versionId(),
            new Reserve(request.operationKey(), manifest.id(), prepared.context(), current.id()));
        Reservation result = transactions.execute(tx -> {
            authorization.authorize(actor.customerId(), intent);
            return store.extendAuthorized(permitted.fence(), request.keepAfterPromotion(),
                store.customerGrant(actor.customerId()), request.operationKey(), fingerprint, actor.userId(), clock.instant());
        });
        if (result == null) throw invalid();
        return summary(result, operation);
    }

    private Summary summary(Reservation value, Operation operation) {
        return new Summary(value.id(), value.deploymentId(), value.targetProfileId(), TargetIntent.CUSTOMER_STAGING,
            operation, value.state().name(), value.revision(), value.expiresAt());
    }

    private AdmissionFailure invalid() {
        return new AdmissionFailure(org.springframework.http.HttpStatus.BAD_REQUEST, "CUSTOMER_STAGING_LIFECYCLE_INPUT_INVALID");
    }
}
