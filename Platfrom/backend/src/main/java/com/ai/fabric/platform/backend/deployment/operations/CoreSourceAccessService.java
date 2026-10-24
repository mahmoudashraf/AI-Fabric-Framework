package com.ai.fabric.platform.backend.deployment.operations;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerSelfServiceAuthorizationService;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTargetReference;
import com.ai.fabric.platform.backend.deployment.service.DeploymentTargetContextService;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Source authorization follows the owned runtime and frozen storage binding, never a capacity reservation. */
@Service
public class CoreSourceAccessService {
    private final CustomerOfferDeploymentRepository associations;
    private final CustomerSelfServiceAuthorizationService authorization;
    private final DeploymentTargetContextService targets;

    public CoreSourceAccessService(CustomerOfferDeploymentRepository associations,
        CustomerSelfServiceAuthorizationService authorization, DeploymentTargetContextService targets) {
        this.associations = associations; this.authorization = authorization; this.targets = targets;
    }

    @Transactional
    public void require(DeploymentTargetReference target, Operation operation, String operationKey, Set<DocumentAudience> audiences) {
        if (target == null || operation == null || audiences == null || audiences.isEmpty()
            || !Set.of(Operation.SOURCE_DISCOVER, Operation.SOURCE_PREVIEW, Operation.SOURCE_INDEX,
                Operation.SOURCE_REFRESH, Operation.SOURCE_REMOVE).contains(operation))
            throw new IllegalArgumentException("SOURCE_AUTHORIZATION_CONTEXT_REQUIRED");
        var runtime = targets.resolve(target.deploymentId(), target.targetProfileId(), target.releaseId(), false);
        var association = associations.findById(target.deploymentId());
        if (association.isEmpty()) return;
        for (var audience : audiences) {
            authorization.authorize(runtime.deployment().getCustomerId(), new AuthorizationRequest(
                association.get().getOfferId(), target.targetProfileId(), target.deploymentId(), runtime.deployment().getTenantId(),
                target.releaseId(), operation, operationKey,
                operation == Operation.SOURCE_INDEX || operation == Operation.SOURCE_REFRESH ? Set.of(Setting.DOCUMENT_AUDIENCE) : Set.of(),
                null, null, audience));
        }
    }
}
