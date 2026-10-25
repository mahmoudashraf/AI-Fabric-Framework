package com.ai.fabric.platform.backend.externalintegration;

import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferEntitlementEntity;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferEntitlementRepository;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferEntitlementService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformTenantRepository;
import org.springframework.stereotype.Service;

import static com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationPolicy.conflict;

/** Current account/tenant eligibility; an invitation supplies only the historic command namespace. */
@Service
public class ExternalIntegrationOwnerEligibility {
    private final CustomerOfferEntitlementRepository owners;
    private final CustomerOfferEntitlementService eligibility;
    private final PlatformTenantRepository tenants;

    public ExternalIntegrationOwnerEligibility(CustomerOfferEntitlementRepository owners,
                                               CustomerOfferEntitlementService eligibility,
                                               PlatformTenantRepository tenants) {
        this.owners = owners;
        this.eligibility = eligibility;
        this.tenants = tenants;
    }

    CustomerOfferEntitlementEntity require(String offerId, DeploymentEntity deployment, String actorId, boolean lock) {
        requireAccount(deployment);
        var owner = (lock ? owners.findForUpdate(deployment.getCustomerId(), offerId)
            : owners.findByCustomerIdAndOfferId(deployment.getCustomerId(), offerId))
            .filter(value -> deployment.getCustomerId().equals(value.getCustomerId()) && offerId.equals(value.getOfferId()))
            .orElseThrow(() -> conflict("DEPLOYMENT_OWNER_REQUIRED"));
        // Retained only as the durable command namespace, never as personal or invitation authority.
        return owner;
    }

    void requireAccount(DeploymentEntity deployment) {
        eligibility.requireActiveCustomer(deployment.getCustomerId());
        tenants.findById(deployment.getTenantId()).filter(value -> "ACTIVE".equals(value.getStatus())
            && deployment.getCustomerId().equals(value.getCustomerId()))
            .orElseThrow(() -> conflict("RESOURCE_SCOPE_MISMATCH"));
    }

    void fenceCommands(String offerId, DeploymentEntity deployment) {
        requireAccount(deployment);
        // Serialize an existing bootstrap command namespace, but absence cannot revoke deployment ownership.
        owners.findForUpdate(deployment.getCustomerId(), offerId);
    }
}
