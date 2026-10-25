package com.ai.fabric.platform.backend.customerselfservice.service;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentDocumentBindingResolver;
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.*;
import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.*;

/** Authorizes deployment operations; the offer describes bootstrap defaults only. */
@Service
public class CustomerSelfServiceOperationBoundary {
    private final CustomerOfferDeploymentRepository associations;
    private final DeploymentRepository deployments;
    private final CustomerSelfServiceAuthorizationService authorization;
    private final CustomerOfferCodec codec;

    public CustomerSelfServiceOperationBoundary(CustomerOfferDeploymentRepository associations,
        DeploymentRepository deployments, CustomerSelfServiceAuthorizationService authorization,
        CustomerOfferEntitlementService entitlements, CustomerOfferCodec codec) {
        this.associations = associations;
        this.deployments = deployments;
        this.authorization = authorization;
        this.codec = codec;
    }

    @Transactional
    public Authorization require(DeploymentEntity deployment, String targetProfileId, String releaseId,
                                 Operation operation, String operationKey, Map<Setting, JsonNode> values,
                                 String bindingSlotId, String selectedProfileId, DocumentAudience audience) {
        return require(deployment, targetProfileId, releaseId, operation, operationKey, values,
            bindingSlotId, selectedProfileId, audience, false);
    }

    @Transactional
    public Authorization requireOperationalRead(DeploymentEntity deployment, String targetProfileId, String releaseId,
                                               DocumentAudience audience) {
        return require(deployment, targetProfileId, releaseId, Operation.READ, null, Map.of(), null, null, audience, true);
    }

    private Authorization require(DeploymentEntity deployment, String targetProfileId, String releaseId,
                                  Operation operation, String operationKey, Map<Setting, JsonNode> values,
                                  String bindingSlotId, String selectedProfileId, DocumentAudience audience, boolean operationalRead) {
        var association = associations.findById(deployment.getId());
        Map<Setting, JsonNode> settings = values == null ? Map.of() : Map.copyOf(values);
        var request = new AuthorizationRequest(
            association.map(value -> value.getOfferId()).orElse(null), targetProfileId, deployment.getId(), deployment.getTenantId(), releaseId,
            operation, operationKey, settings.keySet(), bindingSlotId, selectedProfileId, audience);
        var approved = operationalRead ? authorization.authorizeOperationalRead(deployment.getCustomerId(), request)
            : authorization.authorize(deployment.getCustomerId(), request);
        authorization.validateSettings(approved, new SettingsPatch(settings));
        return approved;
    }

    /**
     * Resolve actual deployment ownership, role and target before preparing a release.
     * Bootstrap association metadata does not authorize or restrict the operation.
     */
    @Transactional
    public Authorization prepareAdmission(DeploymentEntity deployment, String targetProfileId,
                                          Operation operation, String operationKey) {
        var association = associations.findById(deployment.getId());
        return authorization.prepareAdmission(deployment.getCustomerId(), new AuthorizationRequest(
            association.map(value -> value.getOfferId()).orElse(null), targetProfileId, deployment.getId(), deployment.getTenantId(), null,
            operation, operationKey, Set.of(), null, null, null
        ));
    }

    @Transactional
    public Authorization requireConsumer(PlatformConsumerEntity consumer, Operation operation, String operationKey,
                                         Map<Setting, JsonNode> values) {
        if (consumer == null) throw deny(RESOURCE_SCOPE_MISMATCH);
        if (consumer.getBoundDeploymentId() == null) {
            var result = authorization.authorizeUnboundConsumer(consumer.getId(), operation, operationKey);
            authorization.requireOwnedConsumer(result, consumer.getId());
            authorization.validateSettings(result, new SettingsPatch(values == null ? Map.of() : Map.copyOf(values)));
            return result;
        }
        var deployment = deployments.findById(consumer.getBoundDeploymentId()).orElseThrow(() -> deny(RESOURCE_SCOPE_MISMATCH));
        if (!consumer.getCustomerId().equals(deployment.getCustomerId())) throw deny(RESOURCE_SCOPE_MISMATCH);
        var result = require(deployment, consumer.getBoundTargetProfileId(), consumer.getBoundReleaseId(),
            operation, operationKey, values, null, null, null);
        authorization.requireOwnedConsumer(result, consumer.getId());
        return result;
    }

    /** Actual release/source policy owns audiences; bootstrap must not narrow later source operations. */
    @Transactional(readOnly = true)
    public Set<DocumentAudience> reviewedDocumentAudiences(DeploymentEntity deployment) {
        authorization.requireExistingDeploymentAccess(deployment, Operation.READ);
        return Set.of();
    }

    /** The owning binding service resolves and validates the actual slot and credentials. */
    @Transactional
    public Authorization requireBindingKind(DeploymentEntity deployment, String targetProfileId, String releaseId,
                                            Operation operation, String operationKey, BindingKind bindingKind) {
        return require(deployment, targetProfileId, releaseId, operation, operationKey,
            Map.of(), null, null, null);
    }

    public void requireArtifact(Authorization approved, String artifactId) {
        if (approved == null || approved.context() == null) throw invalid();
        if (artifactId == null || artifactId.isBlank()) throw deny(ARTIFACT_REQUIRED);
        if (CustomerSelfServiceAuthorizationService.bootstrapOperation(approved.context().operation())
            && approved.definition().artifacts().stream().noneMatch(pin -> artifactId.equals(pin.artifactId()))) {
            throw deny(REVIEWED_COMPOSITION_CHANGED);
        }
    }

    public void requireVersion(Authorization approved, DeploymentVersionEntity version) {
        if (approved == null) throw invalid();
        authorization.requireReviewedVersion(approved, version);
    }

    public void requireS3(Authorization approved, String slotId, String connectorType) {
        if (approved == null || approved.context() == null) throw invalid();
        // Existing binding services validate their actual transport, scope and credential ownership.
        if (!CustomerSelfServiceAuthorizationService.bootstrapOperation(approved.context().operation())) return;
        if (!"S3_COMPATIBLE_OBJECT_STORAGE".equals(connectorType)
            || !DeploymentDocumentBindingResolver.DOCUMENT_STORAGE_SLOT.equals("slot:" + slotId)) throw deny(BINDING_SLOT_FORBIDDEN);
        if (CustomerSelfServiceAuthorizationService.bootstrapOperation(approved.context().operation())
            && approved.definition().bindingSlots().stream().noneMatch(slot -> slot.id().equals(slotId)
                && slot.kind() == BindingKind.CUSTOMER_S3)) throw deny(BINDING_SLOT_FORBIDDEN);
    }

    /** Profile rules pin the canonical structured payload, so a browser cannot change its contents behind an ID. */
    public String profileId(String kind, Object configuration) {
        return kind + ":" + codec.hash(configuration);
    }
}
