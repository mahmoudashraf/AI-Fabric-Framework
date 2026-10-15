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

/** Publishes only the exact reviewed Marketplace composition owned by an accepted customer offer. */
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
        var grant = entitlements.requireEntitlement(actor.customerId(), offerId, actor.userId(), true);
        String actorId = PlatformSecurityContext.currentPrincipal().actorId();
        String fingerprint = codec.hash(Map.of("command", COMMAND, "offerId", offerId,
            "deploymentId", deploymentId, "request", request, "customerId", actor.customerId(),
            "ownerId", actor.userId(), "actor", actorId));
        var previous = commands.findByEntitlementIdAndOperationKey(grant.getId(), request.operationKey()).orElse(null);
        if (previous != null) {
            if (!COMMAND.equals(previous.getCommandType()) || !fingerprint.equals(previous.getRequestFingerprint())
                || !actorId.equals(previous.getActorId())) throw conflict(OPERATION_KEY_CONFLICT);
            return codec.read(previous.getResultJson(), PublishResult.class);
        }
        if (grant.getRevision() != request.expectedEntitlementRevision()) throw conflict(REVISION_CONFLICT);
        var deployment = deployments.findById(deploymentId)
            .filter(candidate -> actor.customerId().equals(candidate.getCustomerId()))
            .orElseThrow(() -> deny(RESOURCE_SCOPE_MISMATCH));
        var approved = authorization.authorize(actor.customerId(), new AuthorizationRequest(offerId,
            request.targetProfileId(), deploymentId, deployment.getTenantId(), null, Operation.VERSION_PUBLISH,
            request.operationKey(), Set.of(), null, null, null));
        var draft = deploymentService.getActiveDraftForDeploymentForTrustedCaller(deploymentId);
        var published = deploymentService.publishDraftForTrustedCaller(draft.id());
        var version = versions.findById(published.id()).orElseThrow(() -> deny(RESOURCE_SCOPE_MISMATCH));
        offerBoundary.requireVersion(approved, version);
        var result = new PublishResult(deploymentId, published.id(), published.versionLabel(), published.configHash(),
            request.targetProfileId(), grant.getRevision());
        commands.saveAndFlush(new CustomerEntitlementCommandEntity("cec-" + UUID.randomUUID(), grant.getId(),
            request.operationKey(), fingerprint, COMMAND, actorId, codec.write(result), Instant.now()));
        audit.record("CUSTOMER_OFFER_VERSION_PUBLISHED", "DEPLOYMENT_VERSION", published.id(),
            Map.of("deploymentId", deploymentId, "offerId", offerId,
                "targetProfileId", request.targetProfileId(), "operationKey", request.operationKey()));
        return result;
    }

    private void validate(String offerId, String deploymentId, PublishRequest request) {
        if (blank(offerId, 64) || blank(deploymentId, 64) || request == null
            || blank(request.targetProfileId(), 64) || request.expectedEntitlementRevision() < 0
            || blank(request.operationKey(), 128)) throw invalid();
    }

    private boolean blank(String value, int maxLength) {
        return value == null || value.isBlank() || value.length() > maxLength || !value.equals(value.trim());
    }
}
