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
 * Binds customer-owned object storage through the reviewed offer contract. The command never
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
        var grant = entitlements.requireEntitlement(actor.customerId(), offerId, actor.userId(), true);
        String actorId = PlatformSecurityContext.currentPrincipal().actorId();
        String fingerprint = codec.hash(Map.of("command", BIND_COMMAND, "offerId", offerId,
            "deploymentId", deploymentId, "request", request, "customerId", actor.customerId(), "actor", actorId));
        Result replay = replay(grant.getId(), request.operationKey(), BIND_COMMAND, fingerprint, actorId);
        if (replay != null) return replay;
        if (grant.getRevision() != request.expectedEntitlementRevision()) throw conflict(REVISION_CONFLICT);

        var deployment = deployments.findById(deploymentId).orElseThrow(() -> deny(RESOURCE_SCOPE_MISMATCH));
        var approved = authorization.authorize(actor.customerId(), new AuthorizationRequest(offerId,
            request.targetProfileId(), deploymentId, deployment.getTenantId(), null, Operation.BINDING_REPLACE,
            request.operationKey(), Set.of(), request.bindingSlotId(), request.selectedProfileId(), null));
        BindingSlot slot = requireS3Slot(approved, request.bindingSlotId());
        requireMappedInstall(deploymentId, slot);

        var binding = bindings.upsert(deploymentId, new UpsertDocumentStorageBindingRequest(
            request.targetProfileId(), DeploymentDocumentStorageBindingService.S3_CONNECTOR,
            request.endpoint(), request.region(), request.bucket(), request.prefix(), request.accessKey(),
            request.secretKey(), request.sessionToken(), request.pathStyleAccess(),
            request.objectVersioningAvailable(), false, null));
        applyBindingToMappedInstall(deployment, slot, binding.bindingRef());
        Result result = new Result(offerId, deploymentId, request.targetProfileId(), request.bindingSlotId(),
            request.selectedProfileId(), "CONFIGURED", binding);
        persist(grant.getId(), request.operationKey(), fingerprint, BIND_COMMAND, actorId, result);
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
        var grant = entitlements.requireEntitlement(actor.customerId(), offerId, actor.userId(), true);
        String actorId = PlatformSecurityContext.currentPrincipal().actorId();
        String fingerprint = codec.hash(Map.of("command", REVOKE_COMMAND, "offerId", offerId,
            "deploymentId", deploymentId, "request", request, "customerId", actor.customerId(), "actor", actorId));
        Result replay = replay(grant.getId(), request.operationKey(), REVOKE_COMMAND, fingerprint, actorId);
        if (replay != null) return replay;
        if (grant.getRevision() != request.expectedEntitlementRevision()) throw conflict(REVISION_CONFLICT);

        var deployment = deployments.findById(deploymentId).orElseThrow(() -> deny(RESOURCE_SCOPE_MISMATCH));
        var approved = authorization.authorize(actor.customerId(), new AuthorizationRequest(offerId,
            request.targetProfileId(), deploymentId, deployment.getTenantId(), null, Operation.BINDING_REVOKE,
            request.operationKey(), Set.of(), request.bindingSlotId(), null, null));
        BindingSlot slot = requireS3Slot(approved, request.bindingSlotId());
        requireMappedInstall(deploymentId, slot);
        var matches = bindings.list(deploymentId).stream()
            .filter(value -> request.targetProfileId().equals(value.targetProfileId())).toList();
        if (matches.size() != 1) throw conflict(STORAGE_REQUIRED);
        removeBindingFromMappedInstall(deployment, slot);
        bindings.remove(deploymentId, matches.getFirst().bindingRef());
        Result result = new Result(offerId, deploymentId, request.targetProfileId(), request.bindingSlotId(),
            null, "REVOKED", null);
        persist(grant.getId(), request.operationKey(), fingerprint, REVOKE_COMMAND, actorId, result);
        audit.record("CUSTOMER_DOCUMENT_STORAGE_REVOKED", "DEPLOYMENT", deploymentId,
            Map.of("offerId", offerId, "targetProfileId", request.targetProfileId(),
                "bindingSlotId", request.bindingSlotId(), "sourceObjectsDeleted", false));
        return result;
    }

    private BindingSlot requireS3Slot(
        com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Authorization approved,
        String slotId
    ) {
        var slot = approved.definition().bindingSlots().stream().filter(value -> value.id().equals(slotId))
            .findFirst().orElseThrow(() -> deny(BINDING_SLOT_FORBIDDEN));
        if (slot.kind() != BindingKind.CUSTOMER_S3) throw deny(BINDING_SLOT_FORBIDDEN);
        return slot;
    }

    private void requireMappedInstall(String deploymentId, BindingSlot slot) {
        if (slot.pluginId() == null) return;
        installs.findByDeploymentIdAndPluginId(deploymentId, slot.pluginId())
            .orElseThrow(() -> conflict(REVIEWED_COMPOSITION_CHANGED));
    }

    private void applyBindingToMappedInstall(DeploymentEntity deployment, BindingSlot slot, String bindingRef) {
        if (slot.pluginId() == null) return;
        var install = installs.findByDeploymentIdAndPluginId(deployment.getId(), slot.pluginId())
            .orElseThrow(() -> conflict(REVIEWED_COMPOSITION_CHANGED));
        ObjectNode config = requireObjectConfig(install.getConfigJson());
        config.put(slot.installConfigField(), bindingRef);
        installService.updateInstallForTrustedCaller(
            deployment,
            install.getId(),
            new UpdateDeploymentMarketplaceInstallRequest(null, "ENABLED", config, null)
        );
    }

    private void removeBindingFromMappedInstall(DeploymentEntity deployment, BindingSlot slot) {
        if (slot.pluginId() == null) return;
        var install = installs.findByDeploymentIdAndPluginId(deployment.getId(), slot.pluginId())
            .orElseThrow(() -> conflict(REVIEWED_COMPOSITION_CHANGED));
        ObjectNode config = requireObjectConfig(install.getConfigJson());
        config.remove(slot.installConfigField());
        installService.updateInstallForTrustedCaller(
            deployment,
            install.getId(),
            new UpdateDeploymentMarketplaceInstallRequest(null, "DISABLED", config, null)
        );
    }

    private ObjectNode requireObjectConfig(String configJson) {
        var config = codec.tree(configJson);
        if (!(config instanceof ObjectNode object)) {
            throw conflict(REVIEWED_COMPOSITION_CHANGED);
        }
        return object.deepCopy();
    }

    private Result replay(String entitlementId, String operationKey, String command, String fingerprint, String actorId) {
        var previous = commands.findByEntitlementIdAndOperationKey(entitlementId, operationKey).orElse(null);
        if (previous == null) return null;
        if (!command.equals(previous.getCommandType()) || !fingerprint.equals(previous.getRequestFingerprint())
            || !actorId.equals(previous.getActorId())) throw conflict(OPERATION_KEY_CONFLICT);
        return codec.read(previous.getResultJson(), Result.class);
    }

    private void persist(String entitlementId, String operationKey, String fingerprint, String command,
                         String actorId, Result result) {
        commands.saveAndFlush(new CustomerEntitlementCommandEntity("cec-" + UUID.randomUUID(), entitlementId,
            operationKey, fingerprint, command, actorId, codec.write(result), Instant.now()));
    }
}
