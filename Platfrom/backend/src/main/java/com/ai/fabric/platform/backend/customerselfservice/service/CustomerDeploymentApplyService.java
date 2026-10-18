package com.ai.fabric.platform.backend.customerselfservice.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerEntitlementCommandEntity;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerDeploymentApplyContracts.Request;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.AuthorizationRequest;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerEntitlementCommandRepository;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.deployment.model.DeploymentReleaseSummary;
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
import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.*;

/** Applies an entitled customer-staging version using only server-owned target and artifact pins. */
@Service
public class CustomerDeploymentApplyService {
    private static final String COMMAND = "APPLY_STAGING_VERSION";

    private final CustomerOfferEntitlementService entitlements;
    private final CustomerOfferDeploymentRepository associations;
    private final DeploymentRepository deployments;
    private final DeploymentVersionRepository versions;
    private final CustomerSelfServiceAuthorizationService authorization;
    private final CustomerSelfServiceOperationBoundary offerBoundary;
    private final CustomerReviewedCompositionService composition;
    private final CustomerEntitlementCommandRepository commands;
    private final DeploymentService deploymentService;
    private final CustomerOfferCodec codec;
    private final PlatformAuditService audit;

    public CustomerDeploymentApplyService(CustomerOfferEntitlementService entitlements,
                                          CustomerOfferDeploymentRepository associations,
                                          DeploymentRepository deployments,
                                          DeploymentVersionRepository versions,
                                          CustomerSelfServiceAuthorizationService authorization,
                                          CustomerSelfServiceOperationBoundary offerBoundary,
                                          CustomerReviewedCompositionService composition,
                                          CustomerEntitlementCommandRepository commands,
                                          DeploymentService deploymentService,
                                          CustomerOfferCodec codec,
                                          PlatformAuditService audit) {
        this.entitlements = entitlements;
        this.associations = associations;
        this.deployments = deployments;
        this.versions = versions;
        this.authorization = authorization;
        this.offerBoundary = offerBoundary;
        this.composition = composition;
        this.commands = commands;
        this.deploymentService = deploymentService;
        this.codec = codec;
        this.audit = audit;
    }

    @Transactional
    public DeploymentReleaseSummary apply(String offerId, String deploymentId, Request request) {
        validate(offerId, deploymentId, request);
        entitlements.requireOperationKey(request.operationKey());
        var actor = entitlements.requireCustomerActor();
        var deployment = deployments.findByIdForUpdate(deploymentId)
            .filter(candidate -> actor.customerId().equals(candidate.getCustomerId()))
            .orElseThrow(() -> deny(RESOURCE_SCOPE_MISMATCH));
        var association = associations.findById(deploymentId).orElseThrow(() -> deny(RESOURCE_SCOPE_MISMATCH));
        if (!actor.customerId().equals(association.getCustomerId())
            || !deployment.getTenantId().equals(association.getTenantId())
            || !offerId.equals(association.getOfferId())) throw deny(RESOURCE_SCOPE_MISMATCH);
        var grant = entitlements.requireEntitlement(actor.customerId(), offerId, actor.userId(), true);
        if (grant.getRevision() != request.expectedEntitlementRevision()) throw conflict(REVISION_CONFLICT);

        String actorId = PlatformSecurityContext.currentPrincipal().actorId();
        String fingerprint = codec.hash(Map.of("command", COMMAND, "offerId", offerId,
            "deploymentId", deploymentId, "request", request, "customerId", actor.customerId(), "actor", actorId));
        var previous = commands.findByEntitlementIdAndOperationKey(grant.getId(), request.operationKey()).orElse(null);
        if (previous != null) {
            if (!COMMAND.equals(previous.getCommandType()) || !fingerprint.equals(previous.getRequestFingerprint())
                || !actorId.equals(previous.getActorId())) throw conflict(OPERATION_KEY_CONFLICT);
            return codec.read(previous.getResultJson(), DeploymentReleaseSummary.class);
        }

        var offer = entitlements.requireOffer(offerId);
        if (!association.getOfferFingerprint().equals(offer.getFingerprint())) throw conflict(REVIEWED_COMPOSITION_CHANGED);
        var definition = entitlements.definition(offer);
        String targetProfileId = association.getBootstrapTargetProfileId();
        String sourceArtifactId = composition.runtimeArtifactId(definition);
        var version = versions.findById(request.versionId()).orElseThrow(() -> deny(RESOURCE_SCOPE_MISMATCH));
        if (!deploymentId.equals(version.getDeploymentId()) || !"PUBLISHED".equals(version.getStatus())) {
            throw deny(RESOURCE_SCOPE_MISMATCH);
        }
        var approved = authorization.authorize(actor.customerId(), new AuthorizationRequest(offerId,
            targetProfileId, deploymentId, deployment.getTenantId(), null, Operation.APPLY,
            request.operationKey(), Set.of(), null, null, null));
        offerBoundary.requireArtifact(approved, sourceArtifactId);
        offerBoundary.requireVersion(approved, version);
        var result = deploymentService.applyVersionForTrustedCaller(
            deploymentId, request.versionId(), targetProfileId, sourceArtifactId);
        commands.saveAndFlush(new CustomerEntitlementCommandEntity("cec-" + UUID.randomUUID(), grant.getId(),
            request.operationKey(), fingerprint, COMMAND, actorId, codec.write(result), Instant.now()));
        audit.record("CUSTOMER_OFFER_STAGING_VERSION_APPLIED", "DEPLOYMENT_RELEASE", result.id(),
            Map.of("deploymentId", deploymentId, "offerId", offerId, "targetProfileId", targetProfileId,
                "operationKey", request.operationKey()));
        return result;
    }

    private void validate(String offerId, String deploymentId, Request request) {
        if (blank(offerId, 64) || blank(deploymentId, 64) || request == null
            || blank(request.versionId(), 64) || request.expectedEntitlementRevision() < 0
            || blank(request.operationKey(), 128)) throw invalid();
    }

    private boolean blank(String value, int maxLength) {
        return value == null || value.isBlank() || value.length() > maxLength || !value.equals(value.trim());
    }
}
