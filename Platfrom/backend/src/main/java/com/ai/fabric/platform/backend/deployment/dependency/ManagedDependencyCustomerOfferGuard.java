package com.ai.fabric.platform.backend.deployment.dependency;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.AuthorizationRequest;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.EntitlementStatus;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferEntitlementRepository;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferRepository;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferEntitlementService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerSelfServiceAuthorizationService;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;

import static org.springframework.http.HttpStatus.CONFLICT;

/** Keeps customer-offer authority attached to managed dependency credential grants. */
@Service
class ManagedDependencyCustomerOfferGuard {
    private final CustomerOfferDeploymentRepository offerDeployments;
    private final CustomerOfferRepository offers;
    private final CustomerOfferEntitlementRepository entitlements;
    private final DeploymentReleaseRepository releases;
    private final CustomerSelfServiceAuthorizationService authorization;
    private final CustomerOfferEntitlementService entitlementEligibility;
    private final Clock clock;

    @Autowired
    ManagedDependencyCustomerOfferGuard(CustomerOfferDeploymentRepository offerDeployments,
                                        CustomerOfferRepository offers,
                                        CustomerOfferEntitlementRepository entitlements,
                                        DeploymentReleaseRepository releases,
                                        CustomerSelfServiceAuthorizationService authorization,
                                        CustomerOfferEntitlementService entitlementEligibility) {
        this(offerDeployments, offers, entitlements, releases, authorization, entitlementEligibility,
            Clock.systemUTC());
    }

    ManagedDependencyCustomerOfferGuard(CustomerOfferDeploymentRepository offerDeployments,
                                        CustomerOfferRepository offers,
                                        CustomerOfferEntitlementRepository entitlements,
                                        DeploymentReleaseRepository releases,
                                        CustomerSelfServiceAuthorizationService authorization,
                                        CustomerOfferEntitlementService entitlementEligibility,
                                        Clock clock) {
        this.offerDeployments = offerDeployments;
        this.offers = offers;
        this.entitlements = entitlements;
        this.releases = releases;
        this.authorization = authorization;
        this.entitlementEligibility = entitlementEligibility;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    void authorizeGrant(ManagedDependencyGraphEntity graph, Operation operation,
                        String operationKey, String bindingSlotId) {
        var association = offerDeployments.findById(graph.getDeploymentId()).orElse(null);
        if (association == null) return;
        requireScope(graph, association.getCustomerId(), association.getTenantId());
        requireRelease(graph);
        authorization.authorize(association.getCustomerId(), new AuthorizationRequest(
            association.getOfferId(), graph.getTargetProfileId(), graph.getDeploymentId(), graph.getTenantId(),
            graph.getReleaseId(), operation, operationKey, Set.of(), bindingSlotId, null, null));
    }

    /** Worker-side fail-closed recheck immediately before any provider access-granting work. */
    @Transactional
    void requireActiveExecution(ManagedDependencyGraphEntity graph) {
        var association = offerDeployments.findById(graph.getDeploymentId()).orElse(null);
        if (association == null) return;
        requireScope(graph, association.getCustomerId(), association.getTenantId());
        requireRelease(graph);
        var offer = offers.findById(association.getOfferId())
            .orElseThrow(() -> failure("DEPENDENCY_OFFER_MISSING"));
        if (!association.getOfferFingerprint().equals(offer.getFingerprint())) {
            throw failure("DEPENDENCY_OFFER_CHANGED");
        }
        Instant now = clock.instant();
        var entitlement = entitlements.findForUpdate(association.getCustomerId(), association.getOfferId())
            .filter(value -> value.getStatus() == EntitlementStatus.ACTIVE)
            .filter(value -> value.getExpiresAt() != null && value.getExpiresAt().isAfter(now))
            .orElseThrow(() -> failure("DEPENDENCY_ENTITLEMENT_INACTIVE"));
        entitlementEligibility.requireCurrentOwnerEligibility(
            entitlement.getOwnerUserId(), association.getCustomerId());
        if (entitlement.getRevision() != association.getAuthorizationEntitlementRevision()) {
            throw failure("DEPENDENCY_ENTITLEMENT_CHANGED");
        }
    }

    private void requireRelease(ManagedDependencyGraphEntity graph) {
        if (graph.getReleaseId() == null || graph.getReleaseId().isBlank()) {
            throw failure("DEPENDENCY_RELEASE_BINDING_REQUIRED");
        }
        var release = releases.findById(graph.getReleaseId())
            .orElseThrow(() -> failure("DEPENDENCY_RELEASE_BINDING_CHANGED"));
        if (!graph.getDeploymentId().equals(release.getDeploymentId())
            || !graph.getTargetProfileId().equals(release.getTargetProfileId())
            || !graph.getVersionId().equals(release.getDeploymentVersionId())) {
            throw failure("DEPENDENCY_RELEASE_BINDING_CHANGED");
        }
    }

    private void requireScope(ManagedDependencyGraphEntity graph, String customerId, String tenantId) {
        if (!customerId.equals(graph.getCustomerId()) || !tenantId.equals(graph.getTenantId())) {
            throw failure("DEPENDENCY_CUSTOMER_SCOPE_CHANGED");
        }
    }

    private ResponseStatusException failure(String code) {
        return new ResponseStatusException(CONFLICT, code);
    }
}
