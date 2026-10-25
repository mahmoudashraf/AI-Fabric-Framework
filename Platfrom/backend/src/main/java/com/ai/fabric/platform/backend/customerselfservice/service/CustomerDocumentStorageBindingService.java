package com.ai.fabric.platform.backend.customerselfservice.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerEntitlementCommandEntity;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerDocumentStorageBindingContracts.BindRequest;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerDocumentStorageBindingContracts.Result;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerDocumentStorageBindingContracts.RevokeRequest;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.AuthorizationRequest;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.BindingKind;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.BindingSlot;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerEntitlementCommandRepository;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.model.UpsertDocumentStorageBindingRequest;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentDocumentStorageBindingService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentDocumentBindingResolver;
import com.ai.fabric.platform.backend.marketplace.model.UpdateDeploymentMarketplaceInstallRequest;
import com.ai.fabric.platform.backend.marketplace.repository.DeploymentMarketplacePluginInstallRepository;
import com.ai.fabric.platform.backend.marketplace.service.DeploymentMarketplaceInstallService;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.*;
import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.*;

/**
 * Binds customer-owned object storage through the normal target binding service. The command never
 * returns credentials and deliberately supports only read-only S3 bindings.
 */
@Service
public class CustomerDocumentStorageBindingService {
    private static final String BIND_COMMAND = "BIND_S3";
    private static final String REVOKE_COMMAND = "REVOKE_S3";

    private final CustomerOfferEntitlementService entitlements;
    private final CustomerSelfServiceAuthorizationService authorization;
    private final CustomerEntitlementCommandRepository commands;
    private final DeploymentRepository deployments;
    private final DeploymentDocumentStorageBindingService bindings;
    private final DeploymentMarketplacePluginInstallRepository installs;
    private final DeploymentMarketplaceInstallService installService;
    private final CustomerOfferCodec codec;
    private final PlatformAuditService audit;

    public CustomerDocumentStorageBindingService(CustomerOfferEntitlementService entitlements,
        CustomerSelfServiceAuthorizationService authorization, CustomerEntitlementCommandRepository commands,
        DeploymentRepository deployments, DeploymentDocumentStorageBindingService bindings,
        DeploymentMarketplacePluginInstallRepository installs,
        DeploymentMarketplaceInstallService installService, CustomerOfferCodec codec, PlatformAuditService audit) {
        this.entitlements = entitlements;
        this.authorization = authorization;
        this.commands = commands;
        this.deployments = deployments;
        this.bindings = bindings;
        this.installs = installs;
        this.installService = installService;
        this.codec = codec;
        this.audit = audit;
    }

    @Transactional
    public Result bind(String offerId, String deploymentId, BindRequest request) {
        if (request == null) throw invalid();
        entitlements.requireOperationKey(request.operationKey());
        var actor = entitlements.requireCustomerActor();
        var deployment = lockOwnedDeployment(deploymentId, actor.customerId());
        authorization.authorize(actor.customerId(), new AuthorizationRequest(offerId,
            request.targetProfileId(), deploymentId, deployment.getTenantId(), null, Operation.BINDING_REPLACE,
            request.operationKey(), Set.of(), request.bindingSlotId(), request.selectedProfileId(), null));
        requireS3Slot(request.bindingSlotId());

        var binding = bindings.upsert(deploymentId, new UpsertDocumentStorageBindingRequest(
            request.targetProfileId(), DeploymentDocumentStorageBindingService.S3_CONNECTOR,
            request.endpoint(), request.region(), request.bucket(), request.prefix(), request.accessKey(),
            request.secretKey(), request.sessionToken(), request.pathStyleAccess(),
            request.objectVersioningAvailable(), false, null), request.operationKey(), request.bindingSlotId(), request.selectedProfileId());
        Result result = new Result(offerId, deploymentId, request.targetProfileId(), request.bindingSlotId(),
            request.selectedProfileId(), "CONFIGURED", binding);
        audit.record("CUSTOMER_DOCUMENT_STORAGE_BOUND", "DEPLOYMENT", deploymentId,
            Map.of("offerId", offerId, "targetProfileId", request.targetProfileId(),
                "bindingSlotId", request.bindingSlotId(), "bindingRef", binding.bindingRef()));
        return result;
    }

    @Transactional
    public Result revoke(String offerId, String deploymentId, RevokeRequest request) {
        if (request == null) throw invalid();
        entitlements.requireOperationKey(request.operationKey());
        var actor = entitlements.requireCustomerActor();
        var deployment = lockOwnedDeployment(deploymentId, actor.customerId());
        authorization.authorize(actor.customerId(), new AuthorizationRequest(offerId,
            request.targetProfileId(), deploymentId, deployment.getTenantId(), null, Operation.BINDING_REVOKE,
            request.operationKey(), Set.of(), request.bindingSlotId(), null, null));
        requireS3Slot(request.bindingSlotId());
        var matches = bindings.list(deploymentId).stream()
            .filter(value -> request.targetProfileId().equals(value.targetProfileId())).toList();
        if (matches.size() != 1) throw conflict(STORAGE_REQUIRED);
        bindings.remove(deploymentId, matches.getFirst().bindingRef(), request.operationKey(), request.bindingSlotId());
        Result result = new Result(offerId, deploymentId, request.targetProfileId(), request.bindingSlotId(),
            null, "REVOKED", null);
        audit.record("CUSTOMER_DOCUMENT_STORAGE_REVOKED", "DEPLOYMENT", deploymentId,
            Map.of("offerId", offerId, "targetProfileId", request.targetProfileId(),
                "bindingSlotId", request.bindingSlotId(), "sourceObjectsDeleted", false));
        return result;
    }

    private void requireS3Slot(String slotId) {
        if (!DeploymentDocumentBindingResolver.DOCUMENT_STORAGE_SLOT.equals("slot:" + slotId)) throw deny(BINDING_SLOT_FORBIDDEN);
    }

    private DeploymentEntity lockOwnedDeployment(String deploymentId, String customerId) {
        return deployments.findByIdForUpdate(deploymentId)
            .filter(candidate -> customerId.equals(candidate.getCustomerId()))
            .orElseThrow(() -> deny(RESOURCE_SCOPE_MISMATCH));
    }

}
