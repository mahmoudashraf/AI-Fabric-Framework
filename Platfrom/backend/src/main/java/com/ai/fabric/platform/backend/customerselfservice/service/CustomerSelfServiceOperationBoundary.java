package com.ai.fabric.platform.backend.customerselfservice.service;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentDocumentBindingResolver;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.*;
import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.*;

/** Applies the reviewed offer at owning-service boundaries, including their generic HTTP routes. */
@Service
public class CustomerSelfServiceOperationBoundary {
    private final CustomerOfferDeploymentRepository associations;
    private final DeploymentRepository deployments;
    private final CustomerSelfServiceAuthorizationService authorization;
    private final CustomerOfferEntitlementService entitlements;
    private final CustomerOfferCodec codec;

    public CustomerSelfServiceOperationBoundary(CustomerOfferDeploymentRepository associations,
        DeploymentRepository deployments, CustomerSelfServiceAuthorizationService authorization,
        CustomerOfferEntitlementService entitlements, CustomerOfferCodec codec) {
        this.associations = associations;
        this.deployments = deployments;
        this.authorization = authorization;
        this.entitlements = entitlements;
        this.codec = codec;
    }

    @Transactional
    public Authorization require(DeploymentEntity deployment, String targetProfileId, String releaseId,
                                 Operation operation, String operationKey, Map<Setting, JsonNode> values,
                                 String bindingSlotId, String selectedProfileId, DocumentAudience audience) {
        var association = associations.findById(deployment.getId());
        if (association.isEmpty()) {
            var principal = PlatformSecurityContext.currentPrincipal();
            if (principal != null && principal.role() == PlatformRole.CUSTOMER_ADMIN) throw deny(ENTITLEMENT_REQUIRED);
            return null;
        }
        Map<Setting, JsonNode> settings = values == null ? Map.of() : Map.copyOf(values);
        var approved = authorization.authorize(deployment.getCustomerId(), new AuthorizationRequest(
            association.get().getOfferId(), targetProfileId, deployment.getId(), deployment.getTenantId(), releaseId,
            operation, operationKey, settings.keySet(), bindingSlotId, selectedProfileId, audience));
        authorization.validateSettings(approved, new SettingsPatch(settings));
        return approved;
    }

    /**
     * Staff admission preflight for an already-associated deployment. This performs the same
     * entitlement, target, placement and reviewed-composition checks as apply authorization,
     * but deliberately stops before prerequisite/admission evidence exists.
     */
    @Transactional
    public Authorization prepareAdmission(DeploymentEntity deployment, String targetProfileId,
                                          Operation operation, String operationKey) {
        var association = associations.findById(deployment.getId())
            .orElseThrow(() -> deny(ENTITLEMENT_REQUIRED));
        return authorization.prepareAdmission(deployment.getCustomerId(), new AuthorizationRequest(
            association.getOfferId(), targetProfileId, deployment.getId(), deployment.getTenantId(), null,
            operation, operationKey, Set.of(), null, null, null
        ));
    }

    @Transactional
    public Authorization requireConsumer(PlatformConsumerEntity consumer, Operation operation, String operationKey,
                                         Map<Setting, JsonNode> values) {
        if (consumer == null || consumer.getBoundDeploymentId() == null) {
            var principal = PlatformSecurityContext.currentPrincipal();
            if (principal != null && principal.role() == PlatformRole.CUSTOMER_ADMIN) throw deny(RESOURCE_SCOPE_MISMATCH);
            return null;
        }
        var deployment = deployments.findById(consumer.getBoundDeploymentId()).orElseThrow(() -> deny(RESOURCE_SCOPE_MISMATCH));
        if (!consumer.getCustomerId().equals(deployment.getCustomerId())) throw deny(RESOURCE_SCOPE_MISMATCH);
        var result = require(deployment, consumer.getBoundTargetProfileId(), consumer.getBoundReleaseId(),
            operation, operationKey, values, null, null, null);
        if (result != null) {
            authorization.requireOwnedConsumer(result, consumer.getId());
        }
        return result;
    }

    /** Resolve customer source audiences from the immutable reviewed offer, never from browser input. */
    @Transactional(readOnly = true)
    public Set<DocumentAudience> reviewedDocumentAudiences(DeploymentEntity deployment) {
        Definition definition = reviewedDefinition(deployment);
        return definition == null ? Set.of() : Set.copyOf(definition.documentAudiences());
    }

    /** Authorize an operation against the sole reviewed binding of the requested kind. */
    @Transactional
    public Authorization requireBindingKind(DeploymentEntity deployment, String targetProfileId, String releaseId,
                                            Operation operation, String operationKey, BindingKind bindingKind) {
        Definition definition = reviewedDefinition(deployment);
        if (definition == null) {
            return require(deployment, targetProfileId, releaseId, operation, operationKey,
                Map.of(), null, null, null);
        }
        var slots = definition.bindingSlots().stream().filter(slot -> slot.kind() == bindingKind).toList();
        if (slots.size() != 1) throw deny(BINDING_SLOT_FORBIDDEN);
        return require(deployment, targetProfileId, releaseId, operation, operationKey,
            Map.of(), slots.getFirst().id(), null, null);
    }

    public void requireArtifact(Authorization approved, String artifactId) {
        if (approved != null && (artifactId == null
            || approved.definition().artifacts().stream().noneMatch(pin -> artifactId.equals(pin.artifactId())))) {
            throw deny(REVIEWED_COMPOSITION_CHANGED);
        }
    }

    public void requireVersion(Authorization approved, DeploymentVersionEntity version) {
        if (approved == null) return;
        authorization.requireReviewedVersion(approved, version);
    }

    private Definition reviewedDefinition(DeploymentEntity deployment) {
        if (deployment == null) throw deny(RESOURCE_SCOPE_MISMATCH);
        var association = associations.findById(deployment.getId());
        if (association.isEmpty()) {
            var principal = PlatformSecurityContext.currentPrincipal();
            if (principal != null && principal.role() == PlatformRole.CUSTOMER_ADMIN) throw deny(ENTITLEMENT_REQUIRED);
            return null;
        }
        return entitlements.definition(entitlements.requireOffer(association.get().getOfferId()));
    }

    public void requireS3(Authorization approved, String slotId, String connectorType) {
        if (approved == null) return;
        if (!"S3_COMPATIBLE_OBJECT_STORAGE".equals(connectorType)
            || !DeploymentDocumentBindingResolver.DOCUMENT_STORAGE_SLOT.equals("slot:" + slotId)
            || approved.definition().bindingSlots().stream().noneMatch(slot -> slot.id().equals(slotId)
                && slot.kind() == BindingKind.CUSTOMER_S3)) throw deny(BINDING_SLOT_FORBIDDEN);
    }

    /** Profile rules pin the canonical structured payload, so a browser cannot change its contents behind an ID. */
    public String profileId(String kind, Object configuration) {
        return kind + ":" + codec.hash(configuration);
    }
}
