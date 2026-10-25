package com.ai.fabric.platform.backend.deployment.dependency;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.AuthorizationRequest;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferEntitlementRepository;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferRepository;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferEntitlementService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerSelfServiceAuthorizationService;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.tenant.repository.PlatformTenantRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.Set;

import static org.springframework.http.HttpStatus.CONFLICT;

/** Checks current account ownership and the exact release before dependency access grants. */
@Service
class ManagedDependencyCustomerOfferGuard {
    private final CustomerOfferDeploymentRepository offerDeployments;
    private final CustomerOfferEntitlementRepository entitlements;
    private final DeploymentReleaseRepository releases;
    private final CustomerSelfServiceAuthorizationService authorization;
    private final CustomerOfferEntitlementService entitlementEligibility;
    private final PlatformTenantRepository tenants;

    @Autowired
    ManagedDependencyCustomerOfferGuard(CustomerOfferDeploymentRepository offerDeployments,
                                        CustomerOfferRepository offers,
                                        CustomerOfferEntitlementRepository entitlements,
                                        DeploymentReleaseRepository releases,
                                        CustomerSelfServiceAuthorizationService authorization,
                                        CustomerOfferEntitlementService entitlementEligibility,
                                        PlatformTenantRepository tenants) {
        this(offerDeployments, offers, entitlements, releases, authorization, entitlementEligibility,
            Clock.systemUTC(), tenants);
    }

    ManagedDependencyCustomerOfferGuard(CustomerOfferDeploymentRepository offerDeployments,
                                        CustomerOfferRepository offers,
                                        CustomerOfferEntitlementRepository entitlements,
                                        DeploymentReleaseRepository releases,
                                        CustomerSelfServiceAuthorizationService authorization,
                                        CustomerOfferEntitlementService entitlementEligibility,
                                        Clock clock, PlatformTenantRepository tenants) {
        this.offerDeployments = offerDeployments;
        this.entitlements = entitlements;
        this.releases = releases;
        this.authorization = authorization;
        this.entitlementEligibility = entitlementEligibility;
        this.tenants = tenants;
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
        entitlementEligibility.requireActiveCustomer(graph.getCustomerId());
        tenants.findById(graph.getTenantId()).filter(value -> "ACTIVE".equals(value.getStatus())
            && graph.getCustomerId().equals(value.getCustomerId()))
            .orElseThrow(() -> failure("DEPENDENCY_CUSTOMER_SCOPE_CHANGED"));
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
