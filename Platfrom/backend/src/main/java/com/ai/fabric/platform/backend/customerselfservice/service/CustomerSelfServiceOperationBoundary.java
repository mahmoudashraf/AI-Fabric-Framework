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
import java.util.HashSet;
import java.util.Set;

import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.*;
import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.*;

/** Applies the reviewed offer at owning-service boundaries, including their generic HTTP routes. */
@Service
public class CustomerSelfServiceOperationBoundary {
    private final CustomerOfferDeploymentRepository associations;
    private final DeploymentRepository deployments;
    private final CustomerSelfServiceAuthorizationService authorization;
    private final CustomerOfferCodec codec;

    public CustomerSelfServiceOperationBoundary(CustomerOfferDeploymentRepository associations,
        DeploymentRepository deployments, CustomerSelfServiceAuthorizationService authorization, CustomerOfferCodec codec) {
        this.associations = associations;
        this.deployments = deployments;
        this.authorization = authorization;
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
            if (consumer.getBoundReleaseId() == null || consumer.getBoundReleaseId().isBlank()) throw deny(RESOURCE_SCOPE_MISMATCH);
            authorization.requireOwnedConsumer(result, consumer.getId());
        }
        return result;
    }

    public void requireArtifact(Authorization approved, String artifactId) {
        if (approved != null && (artifactId == null
            || approved.definition().artifacts().stream().noneMatch(pin -> artifactId.equals(pin.artifactId())))) {
            throw deny(REVIEWED_COMPOSITION_CHANGED);
        }
    }

    public void requireVersion(Authorization approved, DeploymentVersionEntity version) {
        if (approved == null) return;
        var provenance = codec.tree(version.getCompositionProvenanceJson());
        var expected = new HashSet<>(approved.definition().plugins());
        expected.add(approved.definition().template());
        var actual = new HashSet<PluginPin>();
        var installs = provenance.path("marketplaceInstalls");
        if (!"PUBLISHED".equals(version.getStatus())
            || !approved.definition().templateId().equals(provenance.path("templateId").asText()) || !installs.isArray()) {
            throw deny(REVIEWED_COMPOSITION_CHANGED);
        }
        for (var install : installs) {
            var match = expected.stream().filter(pin -> pin.pluginId().equals(install.path("pluginId").asText())
                && pin.versionId().equals(install.path("pluginVersionId").asText())
                && ("sha256:" + pin.manifestSha256()).equals(install.path("manifestSha256").asText())).findFirst();
            if (match.isEmpty() || !actual.add(match.get())) throw deny(REVIEWED_COMPOSITION_CHANGED);
        }
        if (!expected.equals(actual)) throw deny(REVIEWED_COMPOSITION_CHANGED);
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
