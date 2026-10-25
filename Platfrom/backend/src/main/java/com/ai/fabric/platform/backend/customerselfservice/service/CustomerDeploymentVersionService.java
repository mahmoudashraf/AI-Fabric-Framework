package com.ai.fabric.platform.backend.customerselfservice.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerEntitlementCommandEntity;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerDeploymentVersionContracts.PublishRequest;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerDeploymentVersionContracts.PublishResult;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.AuthorizationRequest;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerEntitlementCommandRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.*;
import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.conflict;
import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.deny;
import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.invalid;

/** Customer-owned adapter to the normal draft publication engine. */
@Service
public class CustomerDeploymentVersionService {
    private static final String COMMAND = "PUBLISH_VERSION";

    private final CustomerOfferEntitlementService entitlements;
    private final CustomerSelfServiceAuthorizationService authorization;
    private final CustomerSelfServiceOperationBoundary offerBoundary;
    private final CustomerEntitlementCommandRepository commands;
    private final DeploymentRepository deployments;
    private final DeploymentVersionRepository versions;
    private final DeploymentService deploymentService;
    private final CustomerOfferCodec codec;
    private final PlatformAuditService audit;

    public CustomerDeploymentVersionService(CustomerOfferEntitlementService entitlements,
                                            CustomerSelfServiceAuthorizationService authorization,
                                            CustomerSelfServiceOperationBoundary offerBoundary,
                                            CustomerEntitlementCommandRepository commands,
                                            DeploymentRepository deployments,
                                            DeploymentVersionRepository versions,
                                            DeploymentService deploymentService,
                                            CustomerOfferCodec codec,
                                            PlatformAuditService audit) {
        this.entitlements = entitlements;
        this.authorization = authorization;
        this.offerBoundary = offerBoundary;
        this.commands = commands;
        this.deployments = deployments;
        this.versions = versions;
        this.deploymentService = deploymentService;
        this.codec = codec;
        this.audit = audit;
    }

    @Transactional
    public PublishResult publish(String offerId, String deploymentId, PublishRequest request) {
        validate(offerId, deploymentId, request);
        entitlements.requireOperationKey(request.operationKey());
        var actor = entitlements.requireCustomerActor();
        var deployment = deployments.findByIdForUpdate(deploymentId)
            .filter(candidate -> actor.customerId().equals(candidate.getCustomerId()))
            .orElseThrow(() -> deny(RESOURCE_SCOPE_MISMATCH));
        var approved = authorization.authorize(actor.customerId(), new AuthorizationRequest(offerId,
            request.targetProfileId(), deploymentId, deployment.getTenantId(), null, Operation.VERSION_PUBLISH,
            request.operationKey(), Set.of(), null, null, null));
        var previous = versions.findByDeploymentIdOrderByPublishedAtDesc(deploymentId).stream()
            .filter(value -> request.draftId().equals(value.getSourceDraftId())).toList();
        if (previous.size() > 1) throw conflict(REVISION_CONFLICT);
        if (!previous.isEmpty()) {
            var version = previous.getFirst();
            offerBoundary.requireVersion(approved, version);
            return new PublishResult(deploymentId, version.getId(), version.getVersionLabel(), version.getConfigHash(), request.targetProfileId());
        }
        var draft = deploymentService.getActiveDraftForDeploymentForTrustedCaller(deploymentId);
        if (!request.draftId().equals(draft.id())) throw conflict(REVISION_CONFLICT);
        var published = deploymentService.publishDraftForTrustedCaller(draft.id());
        var version = versions.findById(published.id()).orElseThrow(() -> deny(RESOURCE_SCOPE_MISMATCH));
        offerBoundary.requireVersion(approved, version);
        var result = new PublishResult(deploymentId, published.id(), published.versionLabel(), published.configHash(),
            request.targetProfileId());
        audit.record("CUSTOMER_VERSION_PUBLISHED", "DEPLOYMENT_VERSION", published.id(),
            Map.of("deploymentId", deploymentId,
                "targetProfileId", request.targetProfileId(), "operationKey", request.operationKey()));
        return result;
    }

    private void validate(String offerId, String deploymentId, PublishRequest request) {
        if (blank(deploymentId, 64) || request == null || blank(request.draftId(), 64)
            || blank(request.targetProfileId(), 64)
            || blank(request.operationKey(), 128)) throw invalid();
    }

    private boolean blank(String value, int maxLength) {
        return value == null || value.isBlank() || value.length() > maxLength || !value.equals(value.trim());
    }
}
