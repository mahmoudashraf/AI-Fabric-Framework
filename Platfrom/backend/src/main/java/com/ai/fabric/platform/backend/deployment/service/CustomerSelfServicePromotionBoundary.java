package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerSelfServiceAuthorizationService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;

@Service
public class CustomerSelfServicePromotionBoundary {
    private final CustomerOfferDeploymentRepository associations;
    private final CustomerSelfServiceAuthorizationService authorization;

    public CustomerSelfServicePromotionBoundary(CustomerOfferDeploymentRepository associations,
                                                CustomerSelfServiceAuthorizationService authorization) {
        this.associations = associations; this.authorization = authorization;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void require(DeploymentEntity deployment, String targetProfileId, String releaseId,
                        String artifactId, CustomerOfferContracts.Operation operation, String operationKey) {
        var association = associations.findById(deployment.getId());
        var principal = PlatformSecurityContext.currentPrincipal();
        if (association.isEmpty()) {
            if (principal != null && principal.role() == PlatformRole.CUSTOMER_ADMIN) {
                throw new ResponseStatusException(FORBIDDEN, "Customer promotion requires an accepted reviewed offer.");
            }
            return;
        }
        var approved = authorization.authorize(deployment.getCustomerId(), new CustomerOfferContracts.AuthorizationRequest(
            association.get().getOfferId(), targetProfileId, deployment.getId(), deployment.getTenantId(), releaseId,
            operation, operationKey, Set.of(), null, null, null));
        if (artifactId == null || approved.definition().artifacts().stream().noneMatch(pin -> artifactId.equals(pin.artifactId()))) {
            throw new ResponseStatusException(CONFLICT, "Promotion source artifact is not pinned by the reviewed offer.");
        }
    }
}
