package com.ai.fabric.platform.backend.customerselfservice.service;

import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerEntitlementCommandEntity;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.AuthorizationRequest;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerStagingConsumerActivationContracts.CreateRequest;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerStagingConsumerActivationContracts.Request;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerEntitlementCommandRepository;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.ai.fabric.platform.backend.tenant.model.PlatformConsumerSummary;
import com.ai.fabric.platform.backend.tenant.model.CreatePlatformConsumerRequest;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
import com.ai.fabric.platform.backend.tenant.service.PlatformCustomerConsumerService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.*;
import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.*;

/** Separates reviewed staging app configuration from activation of an exact verified release. */
@Service
public class CustomerStagingConsumerActivationService {
    private static final String CREATE_COMMAND = "CREATE_STAGING_CONSUMER";

    private final CustomerOfferEntitlementService entitlements;
    private final PlatformConsumerRepository consumers;
    private final CustomerOfferDeploymentRepository associations;
    private final DeploymentRepository deployments;
    private final DeploymentReleaseRepository releases;
    private final DeploymentVersionRepository versions;
    private final CustomerSelfServiceAuthorizationService authorization;
    private final CustomerSelfServiceOperationBoundary offerBoundary;
    private final PlatformCustomerConsumerService consumerService;
    private final CustomerEntitlementCommandRepository commands;
    private final CustomerOfferCodec codec;

    public CustomerStagingConsumerActivationService(CustomerOfferEntitlementService entitlements,
                                                    PlatformConsumerRepository consumers,
                                                    CustomerOfferDeploymentRepository associations,
                                                    DeploymentRepository deployments,
                                                    DeploymentReleaseRepository releases,
                                                    DeploymentVersionRepository versions,
                                                    CustomerSelfServiceAuthorizationService authorization,
                                                    CustomerSelfServiceOperationBoundary offerBoundary,
                                                    PlatformCustomerConsumerService consumerService,
                                                    CustomerEntitlementCommandRepository commands,
                                                    CustomerOfferCodec codec) {
        this.entitlements = entitlements;
        this.consumers = consumers;
        this.associations = associations;
        this.deployments = deployments;
        this.releases = releases;
        this.versions = versions;
        this.authorization = authorization;
        this.offerBoundary = offerBoundary;
        this.consumerService = consumerService;
        this.commands = commands;
        this.codec = codec;
    }

    @Transactional
    public PlatformConsumerSummary activate(String offerId, String deploymentId, Request request) {
        validate(offerId, deploymentId, request);
        var actor = entitlements.requireCustomerActor();
        // Consumer -> deployment is the canonical assignment lock order.
        var consumer = consumers.findBindingForUpdate(actor.customerId(), request.consumerId())
            .orElseThrow(() -> deny(RESOURCE_SCOPE_MISMATCH));
        if (!actor.customerId().equals(consumer.getCustomerId())) throw deny(RESOURCE_SCOPE_MISMATCH);
        if (consumer.getBoundDeploymentId() != null && !deploymentId.equals(consumer.getBoundDeploymentId())) {
            // This endpoint owns one reviewed deployment only. Cross-deployment moves use a separately
            // reviewed transition so origin projection cannot introduce an inconsistent lock order.
            throw deny(RESOURCE_SCOPE_MISMATCH);
        }
        var deployment = deployments.findByIdForUpdate(deploymentId)
            .filter(candidate -> actor.customerId().equals(candidate.getCustomerId()))
            .orElseThrow(() -> deny(RESOURCE_SCOPE_MISMATCH));
        var association = associations.findById(deploymentId).orElseThrow(() -> deny(RESOURCE_SCOPE_MISMATCH));
        if (!actor.customerId().equals(association.getCustomerId())
            || !deployment.getTenantId().equals(association.getTenantId())
            || !offerId.equals(association.getOfferId())) throw deny(RESOURCE_SCOPE_MISMATCH);
        var entitlement = entitlements.requireEntitlement(actor.customerId(), offerId, actor.userId(), true);
        if (entitlement.getRevision() != request.expectedEntitlementRevision()) throw conflict(REVISION_CONFLICT);

        var release = releases.findById(request.releaseId()).orElseThrow(() -> deny(RESOURCE_SCOPE_MISMATCH));
        if (!deploymentId.equals(release.getDeploymentId())
            || !association.getBootstrapTargetProfileId().equals(release.getTargetProfileId())
            || !"APPLIED_VERIFIED".equals(release.getStatus())
            || !"PASSED".equals(release.getVerificationStatus())) throw conflict(VERIFICATION_REQUIRED);
        var version = versions.findById(release.getDeploymentVersionId())
            .orElseThrow(() -> deny(RESOURCE_SCOPE_MISMATCH));
        var approved = authorization.authorize(actor.customerId(), new AuthorizationRequest(offerId,
            release.getTargetProfileId(), deploymentId, deployment.getTenantId(), release.getId(), Operation.ACTIVATE,
            request.operationKey(), Set.of(), null, null, null));
        offerBoundary.requireVersion(approved, version);

        return consumerService.updateBindingForTrustedPromotion(actor.customerId(), consumer.getConsumerId(),
            deploymentId, release.getId(), release.getTargetProfileId(),
            "Customer activated a reviewed staging assignment.", request.expectedBindingRevision(), request.operationKey());
    }

    /** Creates a reviewed configuration-only assignment, or activates an exact verified staging release. */
    @Transactional
    public PlatformConsumerSummary createAndActivate(String offerId, String deploymentId, CreateRequest request) {
        validate(offerId, deploymentId, request);
        var actor = entitlements.requireCustomerActor();
        // No existing consumer is locked on this path; serialize creation and its receipt on the owned deployment/grant.
        var deployment = deployments.findByIdForUpdate(deploymentId)
            .filter(candidate -> actor.customerId().equals(candidate.getCustomerId()))
            .orElseThrow(() -> deny(RESOURCE_SCOPE_MISMATCH));
        var association = associations.findById(deploymentId).orElseThrow(() -> deny(RESOURCE_SCOPE_MISMATCH));
        if (!actor.customerId().equals(association.getCustomerId())
            || !deployment.getTenantId().equals(association.getTenantId())
            || !offerId.equals(association.getOfferId())
            || blank(association.getBootstrapTargetProfileId(), 64)) throw deny(RESOURCE_SCOPE_MISMATCH);
        var grant = entitlements.requireEntitlement(actor.customerId(), offerId, actor.userId(), true);
        String actorId = PlatformSecurityContext.currentPrincipal().actorId();
        String fingerprint = codec.hash(Map.of("command", CREATE_COMMAND, "offerId", offerId,
            "deploymentId", deploymentId, "request", request, "customerId", actor.customerId(),
            "ownerId", actor.userId(), "actor", actorId));
        var previous = commands.findByEntitlementIdAndOperationKey(grant.getId(), request.operationKey()).orElse(null);
        if (previous != null) {
            if (!CREATE_COMMAND.equals(previous.getCommandType()) || !fingerprint.equals(previous.getRequestFingerprint())
                || !actorId.equals(previous.getActorId())) throw conflict(OPERATION_KEY_CONFLICT);
            return codec.read(previous.getResultJson(), PlatformConsumerSummary.class);
        }
        if (grant.getRevision() != request.expectedEntitlementRevision()) throw conflict(REVISION_CONFLICT);
        if (request.releaseId() == null) {
            authorization.authorize(actor.customerId(), new AuthorizationRequest(offerId,
                association.getBootstrapTargetProfileId(), deploymentId, deployment.getTenantId(), null,
                Operation.APP_CONFIGURE, request.operationKey(), Set.of(), null, null, null));
        }
        var created = consumerService.createConsumer(actor.customerId(), new CreatePlatformConsumerRequest(
            request.consumerId(), request.displayName(), request.description(), null, null));
        var result = request.releaseId() == null
            ? consumerService.updateBindingForTrustedPromotion(actor.customerId(), created.consumerId(), deploymentId,
                null, association.getBootstrapTargetProfileId(), "Customer configured a reviewed staging assignment.",
                created.bindingRevision(), request.operationKey())
            : activate(offerId, deploymentId, new Request(created.consumerId(), request.releaseId(),
                created.bindingRevision(), request.expectedEntitlementRevision(), request.operationKey()));
        commands.saveAndFlush(new CustomerEntitlementCommandEntity("cec-" + UUID.randomUUID(), grant.getId(),
            request.operationKey(), fingerprint, CREATE_COMMAND, actorId, codec.write(result), Instant.now()));
        return result;
    }

    private void validate(String offerId, String deploymentId, Request request) {
        if (blank(offerId, 64) || blank(deploymentId, 64) || request == null
            || blank(request.consumerId(), 128) || blank(request.releaseId(), 64)
            || request.expectedBindingRevision() < 0 || request.expectedEntitlementRevision() < 0
            || blank(request.operationKey(), 128)) throw invalid();
    }

    private void validate(String offerId, String deploymentId, CreateRequest request) {
        if (blank(offerId, 64) || blank(deploymentId, 64) || request == null
            || blank(request.consumerId(), 128) || blank(request.displayName(), 255)
            || (request.description() != null && request.description().length() > 1000)
            || (request.releaseId() != null && blank(request.releaseId(), 64)) || request.expectedEntitlementRevision() < 0
            || blank(request.operationKey(), 128)) throw invalid();
    }

    private boolean blank(String value, int maxLength) {
        return value == null || value.isBlank() || value.length() > maxLength || !value.equals(value.trim());
    }
}
