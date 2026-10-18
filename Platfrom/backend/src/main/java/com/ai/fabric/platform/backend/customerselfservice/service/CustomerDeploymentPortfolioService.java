package com.ai.fabric.platform.backend.customerselfservice.service;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.CustomerDeploymentSummary;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.RESOURCE_SCOPE_MISMATCH;
import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.deny;

@Service
public class CustomerDeploymentPortfolioService {
    private final CustomerOfferEntitlementService access;
    private final CustomerOfferDeploymentRepository associations;
    private final DeploymentRepository deployments;

    public CustomerDeploymentPortfolioService(CustomerOfferEntitlementService access,
                                              CustomerOfferDeploymentRepository associations,
                                              DeploymentRepository deployments) {
        this.access = access;
        this.associations = associations;
        this.deployments = deployments;
    }

    @Transactional(readOnly = true)
    public List<CustomerDeploymentSummary> deployments() {
        var actor = access.requireCustomerActor();
        return associations.findByCustomerIdOrderByCreatedAtDesc(actor.customerId()).stream().map(association -> {
            var deployment = deployments.findById(association.getDeploymentId())
                .orElseThrow(() -> deny(RESOURCE_SCOPE_MISMATCH));
            if (!actor.customerId().equals(deployment.getCustomerId())
                || !association.getTenantId().equals(deployment.getTenantId())) {
                throw deny(RESOURCE_SCOPE_MISMATCH);
            }
            return new CustomerDeploymentSummary(deployment.getId(), deployment.getName(), deployment.getEnvironmentName(),
                deployment.getStatus(), deployment.getTenantId(), association.getOfferId(),
                association.getBootstrapTargetProfileId(), association.getOfferFingerprint(),
                association.getAuthorizationEntitlementRevision(), deployment.getActiveVersionId(), association.getCreatedAt());
        }).toList();
    }
}
