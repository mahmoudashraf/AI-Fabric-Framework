package com.ai.fabric.platform.backend.customerselfservice.service;

import com.ai.fabric.platform.backend.customerselfservice.config.CustomerSelfServiceProperties;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException;
import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferDeploymentEntity;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.repository.*;
import com.ai.fabric.platform.backend.deployment.service.DeploymentAccessService;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
import com.ai.fabric.platform.backend.tenant.repository.PlatformTenantRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.*;
import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.*;

@Service
public class CustomerSelfServiceAuthorizationService {
    private final CustomerOfferEntitlementService entitlements;
    private final CustomerReviewedCompositionService composition;
    private final CustomerTargetPlacementService placement;
    private final DeploymentRepository deployments;
    private final DeploymentReleaseRepository releases;
    private final DeploymentTargetProfileRepository profiles;
    private final PlatformTenantRepository tenants;
    private final PlatformConsumerRepository consumers;
    private final DeploymentAccessService access;
    private final ObjectProvider<CustomerPrerequisiteEvidenceProvider> evidenceProviders;
    private final CustomerSelfServiceProperties properties;
    private final CustomerOfferDeploymentRepository offerDeployments;

    public CustomerSelfServiceAuthorizationService(CustomerOfferEntitlementService entitlements,
        CustomerReviewedCompositionService composition, CustomerTargetPlacementService placement,
        DeploymentRepository deployments, DeploymentReleaseRepository releases, DeploymentTargetProfileRepository profiles,
        PlatformTenantRepository tenants, PlatformConsumerRepository consumers, DeploymentAccessService access,
        ObjectProvider<CustomerPrerequisiteEvidenceProvider> evidenceProviders, CustomerSelfServiceProperties properties,
        CustomerOfferDeploymentRepository offerDeployments) {
        this.entitlements = entitlements;
        this.composition = composition;
        this.placement = placement;
        this.deployments = deployments;
        this.releases = releases;
        this.profiles = profiles;
        this.tenants = tenants;
        this.consumers = consumers;
        this.access = access;
        this.evidenceProviders = evidenceProviders;
        this.properties = properties;
        this.offerDeployments = offerDeployments;
    }

    /** Join the owning command transaction so revoke cannot race the persisted mutation. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Authorization authorize(String customerId, AuthorizationRequest request) {
        return authorize(customerId, request, true);
    }

    /**
     * Validate the exact customer command before its admission reservation exists. This is intentionally
     * limited to operations whose permission row requires admission evidence; every other authorization
     * check remains identical to the owning command path.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Authorization prepareAdmission(String customerId, AuthorizationRequest request) {
        return authorize(customerId, request, false);
    }

    private Authorization authorize(String customerId, AuthorizationRequest request, boolean requireAdmissionEvidence) {
        if (request == null || request.operation() == null || blank(request.targetProfileId())
            || blank(request.offerId())) throw invalid();
        var principal = PlatformSecurityContext.currentPrincipal();
        if (principal == null) throw deny(ACTIVE_CUSTOMER_SCOPE_REQUIRED);
        String ownerId = null;
        if (principal.role() == PlatformRole.CUSTOMER_ADMIN) {
            var actor = entitlements.requireCustomerActor();
            if (!actor.customerId().equals(customerId)) throw deny(RESOURCE_SCOPE_MISMATCH);
            ownerId = actor.userId();
        } else {
            entitlements.requireActiveStaff(principal);
        }
        var entitlement = entitlements.requireEntitlement(customerId, request.offerId(), ownerId, true);
        var offer = entitlements.requireOffer(request.offerId());
        var definition = entitlements.definition(offer);
        if (!definition.operations().contains(request.operation())) throw deny(OPERATION_FORBIDDEN);
        PermissionRow row = CustomerPermissionMatrix.row(request.operation());
        if (!requireAdmissionEvidence && !row.admissionEvidenceRequired()) throw deny(OPERATION_FORBIDDEN);
        if (!row.settings().containsAll(request.changedSettings()) || !definition.settings().stream()
            .map(SettingRule::setting).collect(java.util.stream.Collectors.toSet()).containsAll(request.changedSettings())) {
            throw deny(SETTING_FORBIDDEN);
        }
        TargetPermission target = definition.targets().stream().filter(value -> value.targetProfileId().equals(request.targetProfileId()))
            .findFirst().orElseThrow(() -> deny(UNENTITLED_TARGET));
        if (!row.targetIntents().contains(target.intent())) throw deny(UNENTITLED_TARGET);

        DeploymentEntity deployment = null;
        if (!blank(request.deploymentId())) {
            deployment = deployments.findById(request.deploymentId()).orElseThrow(() -> deny(RESOURCE_SCOPE_MISMATCH));
            if (!customerId.equals(deployment.getCustomerId()) || blank(deployment.getTenantId())
                || !deployment.getTenantId().equals(request.tenantId())
                || deployment.getArchivedAt() != null || "ARCHIVED".equals(deployment.getStatus())
                || !definition.templateId().equals(deployment.getTemplateId())
                || !blank(deployment.getSourceRepositoryOverride()) || !blank(deployment.getSourceBranchOverride())) {
                throw deny(RESOURCE_SCOPE_MISMATCH);
            }
            requireStaffDeploymentAccess(deployment, row);
            if (request.operation() != Operation.BOOTSTRAP && request.operation() != Operation.BOOTSTRAP_CONFIGURATION
                && request.operation() != Operation.TEMPLATE_SELECT) {
                var association = offerDeployments.findById(deployment.getId()).orElseThrow(() -> deny(ENTITLEMENT_REQUIRED));
                if (!customerId.equals(association.getCustomerId()) || !request.tenantId().equals(association.getTenantId())
                    || !offer.getId().equals(association.getOfferId()) || !offer.getFingerprint().equals(association.getOfferFingerprint())) {
                    throw deny(RESOURCE_SCOPE_MISMATCH);
                }
            }
        } else if (request.operation() != Operation.TEMPLATE_SELECT && request.operation() != Operation.BOOTSTRAP
            && request.operation() != Operation.BOOTSTRAP_CONFIGURATION) {
            throw deny(RESOURCE_SCOPE_MISMATCH);
        } else if (principal.role() != PlatformRole.CUSTOMER_ADMIN) {
            // An assigned operator has no authority to create a new unassigned customer's deployment.
            entitlements.requireStaffAdmin();
        }
        if (!blank(request.tenantId())) {
            tenants.findById(request.tenantId()).filter(tenant -> customerId.equals(tenant.getCustomerId())
                && "ACTIVE".equals(tenant.getStatus())).orElseThrow(() -> deny(RESOURCE_SCOPE_MISMATCH));
        } else if (deployment != null) throw deny(RESOURCE_SCOPE_MISMATCH);
        if (!blank(request.releaseId())) {
            var release = releases.findById(request.releaseId()).orElseThrow(() -> deny(RESOURCE_SCOPE_MISMATCH));
            if (deployment == null || !deployment.getId().equals(release.getDeploymentId())
                || !target.targetProfileId().equals(release.getTargetProfileId())
                || definition.artifacts().stream().noneMatch(pin -> pin.artifactId().equals(release.getSourceArtifactId()))) {
                throw deny(RESOURCE_SCOPE_MISMATCH);
            }
        } else if (request.operation() == Operation.ACTIVATE || request.operation() == Operation.ROLLBACK
            || request.operation() == Operation.TARGET_RETIRE || request.operation() == Operation.VERIFY
            || isSourceOperation(request.operation())) throw deny(RESOURCE_SCOPE_MISMATCH);
        requireBindingAndAudience(definition, row, request);
        composition.validate(definition);
        var profile = profiles.findById(target.targetProfileId()).orElseThrow(() -> deny(UNENTITLED_TARGET));
        composition.requireProfile(profile, target);
        var placementEvidence = placement.requireFreshEvidence(profile, target.intent());
        if (request.operation() != Operation.READ && request.operation() != Operation.TEMPLATE_SELECT
            && request.operation() != Operation.SOURCE_DISCOVER && request.operation() != Operation.SOURCE_PREVIEW) {
            entitlements.requireOperationKey(request.operationKey());
        }
        AdmissionContext context = new AdmissionContext(customerId, request.tenantId(), request.deploymentId(),
            request.releaseId(), target.targetProfileId(), target.intent(), offer.getId(), offer.getFingerprint(),
            entitlement.getRevision(), placementEvidence.getId(), request.operation(), request.operationKey(), definition.limits());
        if (requireAdmissionEvidence) requirePrerequisites(context, requiredPrerequisites(definition, row));
        return new Authorization(context, definition);
    }

    private void requireStaffDeploymentAccess(DeploymentEntity deployment, PermissionRow row) {
        try {
            switch (row.staffAccess()) {
                case VIEWER -> access.requireDeploymentAccess(deployment);
                case EDITOR -> access.requireDeploymentEditorAccess(deployment);
                case OPERATOR -> access.requireDeploymentOperatorAccess(deployment);
                case ADMIN -> access.requireDeploymentAdminAccess(deployment);
            }
        } catch (ResponseStatusException ex) { throw deny(OPERATION_FORBIDDEN); }
    }

    private void requireBindingAndAudience(Definition definition, PermissionRow row, AuthorizationRequest request) {
        if (row.ownedBindingRequired()) {
            BindingSlot slot = definition.bindingSlots().stream().filter(value -> value.id().equals(request.bindingSlotId()))
                .findFirst().orElseThrow(() -> deny(BINDING_SLOT_FORBIDDEN));
            if (request.operation() == Operation.BINDING_REPLACE
                && !slot.supportedProfileIds().contains(request.selectedProfileId())) throw deny(BINDING_SLOT_FORBIDDEN);
            if (request.operation() == Operation.BINDING_REVOKE && request.selectedProfileId() != null) throw invalid();
        } else if (request.bindingSlotId() != null || request.selectedProfileId() != null) throw deny(BINDING_SLOT_FORBIDDEN);
        if (isSourceOperation(request.operation()) && definition.documentAudiences().isEmpty()) throw deny(OPERATION_FORBIDDEN);
        if (request.documentAudience() != null && !definition.documentAudiences().contains(request.documentAudience())) {
            throw deny(SETTING_FORBIDDEN);
        }
        if ((request.operation() == Operation.SOURCE_INDEX || request.operation() == Operation.SOURCE_REFRESH)
            && request.documentAudience() == null) throw deny(SETTING_FORBIDDEN);
    }

    private Set<Prerequisite> requiredPrerequisites(Definition definition, PermissionRow row) {
        if (row.admissionEvidenceRequired()) return definition.admissionPrerequisites();
        if (isSourceOperation(row.operation())) {
            return definition.documentAudiences().contains(DocumentAudience.AUTHENTICATED)
                ? EnumSet.of(Prerequisite.STORAGE, Prerequisite.IDENTITY) : EnumSet.of(Prerequisite.STORAGE);
        }
        return Set.of();
    }

    public void requirePrerequisites(AdmissionContext context, Set<Prerequisite> required) {
        if (required.isEmpty()) return;
        List<CustomerPrerequisiteEvidenceProvider> providers = evidenceProviders.orderedStream().toList();
        if (providers.size() != 1 || properties.evidenceMaxAgeSeconds() <= 0) throw conflict(PREREQUISITE_EVIDENCE_REQUIRED);
        PrerequisiteEvidence evidence;
        try { evidence = providers.getFirst().resolve(context); }
        catch (RuntimeException ex) { throw conflict(PREREQUISITE_EVIDENCE_REQUIRED); }
        Instant now = Instant.now();
        if (evidence == null || !context.equals(evidence.context()) || blank(evidence.evidenceReference())
            || evidence.checkedAt() == null || evidence.expiresAt() == null || evidence.checkedAt().isAfter(now)
            || evidence.checkedAt().isBefore(now.minusSeconds(properties.evidenceMaxAgeSeconds()))
            || !evidence.expiresAt().isAfter(now)) throw conflict(PREREQUISITE_EVIDENCE_REQUIRED);
        for (Prerequisite prerequisite : Prerequisite.values()) {
            if (required.contains(prerequisite) && !evidence.passed().contains(prerequisite)) throw conflict(error(prerequisite));
        }
        if (required.contains(Prerequisite.CAPACITY) && blank(evidence.capacityReservationId())) throw conflict(CAPACITY_REQUIRED);
    }

    /** Validate actual values, not just the caller's declaration of changed field names. */
    public void validateSettings(Authorization authorization, SettingsPatch patch) {
        if (authorization == null || patch == null || patch.values() == null) throw invalid();
        PermissionRow row = CustomerPermissionMatrix.row(authorization.context().operation());
        for (var entry : patch.values().entrySet()) {
            if (!row.settings().contains(entry.getKey())) throw deny(SETTING_FORBIDDEN);
            var rule = authorization.definition().settings().stream().filter(value -> value.setting() == entry.getKey())
                .findFirst().orElseThrow(() -> deny(SETTING_FORBIDDEN));
            var value = entry.getValue();
            if (value == null || !value.isTextual() || value.asText().isBlank() || value.asText().length() > rule.maxLength()
                || (rule.type() == ValueType.CHOICE && !rule.allowedValues().contains(value.asText()))) throw deny(SETTING_FORBIDDEN);
        }
    }

    public void requirePinnedComposition(Authorization authorization, String templateVersionId, Set<String> pluginVersionIds,
                                         Set<String> artifactIds) {
        var definition = authorization.definition();
        if (!definition.template().versionId().equals(templateVersionId)
            || !definition.plugins().stream().map(PluginPin::versionId).collect(java.util.stream.Collectors.toSet()).equals(pluginVersionIds)
            || !definition.artifacts().stream().map(ArtifactPin::artifactId).collect(java.util.stream.Collectors.toSet()).equals(artifactIds)) {
            throw deny(REVIEWED_COMPOSITION_CHANGED);
        }
    }

    public void requireOwnedConsumer(Authorization authorization, String consumerEntityId) {
        var consumer = consumers.findById(consumerEntityId).orElseThrow(() -> deny(RESOURCE_SCOPE_MISMATCH));
        if (!authorization.context().customerId().equals(consumer.getCustomerId()) || !"ACTIVE".equals(consumer.getStatus())
            || (consumer.getBoundDeploymentId() != null && !consumer.getBoundDeploymentId().equals(authorization.context().deploymentId()))) {
            throw deny(RESOURCE_SCOPE_MISMATCH);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void registerBootstrap(Authorization authorization, String deploymentId) {
        if (authorization == null || (authorization.context().operation() != Operation.BOOTSTRAP
            && authorization.context().operation() != Operation.BOOTSTRAP_CONFIGURATION)) throw invalid();
        var context = authorization.context();
        String ownerId = null;
        var principal = PlatformSecurityContext.currentPrincipal();
        if (principal != null && principal.role() == PlatformRole.CUSTOMER_ADMIN) {
            var actor = entitlements.requireCustomerActor();
            if (!context.customerId().equals(actor.customerId())) throw deny(RESOURCE_SCOPE_MISMATCH);
            ownerId = actor.userId();
        } else entitlements.requireStaffAdmin();
        var offer = entitlements.requireOffer(context.offerId());
        if (!context.offerFingerprint().equals(offer.getFingerprint())
            || !authorization.definition().equals(entitlements.definition(offer))) throw conflict(REVIEWED_COMPOSITION_CHANGED);
        var entitlement = entitlements.requireEntitlement(context.customerId(), context.offerId(), ownerId, true);
        if (entitlement.getRevision() != context.entitlementRevision()) throw conflict(REVISION_CONFLICT);
        var profile = profiles.findById(context.targetProfileId()).orElseThrow(() -> deny(UNENTITLED_TARGET));
        placement.requireEvidenceById(profile, context.intent(), context.placementEvidenceId());
        var deployment = deployments.findById(deploymentId).orElseThrow(() -> deny(RESOURCE_SCOPE_MISMATCH));
        entitlements.requireOperationKey(context.operationKey());
        if (!context.customerId().equals(deployment.getCustomerId()) || blank(deployment.getTenantId())
            || deployment.getArchivedAt() != null || "ARCHIVED".equals(deployment.getStatus())
            || !authorization.definition().templateId().equals(deployment.getTemplateId())
            || (!blank(context.tenantId()) && !context.tenantId().equals(deployment.getTenantId()))
            || !blank(deployment.getSourceRepositoryOverride()) || !blank(deployment.getSourceBranchOverride())) {
            throw deny(RESOURCE_SCOPE_MISMATCH);
        }
        tenants.findById(deployment.getTenantId()).filter(tenant -> context.customerId().equals(tenant.getCustomerId())
            && "ACTIVE".equals(tenant.getStatus())).orElseThrow(() -> deny(RESOURCE_SCOPE_MISMATCH));
        var previous = offerDeployments.findById(deploymentId).orElse(null);
        if (previous != null) {
            if (!context.offerId().equals(previous.getOfferId()) || !context.operationKey().equals(previous.getOperationKey())
                || !context.offerFingerprint().equals(previous.getOfferFingerprint())) throw conflict(OPERATION_KEY_CONFLICT);
            return;
        }
        offerDeployments.saveAndFlush(new CustomerOfferDeploymentEntity(deploymentId, context.customerId(), deployment.getTenantId(),
            context.offerId(), context.offerFingerprint(), context.entitlementRevision(), context.operationKey(), Instant.now()));
    }

    private CustomerSelfServiceException.Code error(Prerequisite prerequisite) {
        return switch (prerequisite) {
            case APPROVED_POLICY -> APPROVED_POLICY_REQUIRED;
            case IMMUTABLE_ARTIFACTS -> ARTIFACT_REQUIRED;
            case STORAGE -> STORAGE_REQUIRED;
            case IDENTITY -> IDENTITY_REQUIRED;
            case CAPACITY -> CAPACITY_REQUIRED;
            case NOTIFICATIONS -> NOTIFICATIONS_REQUIRED;
            case OPERATING_POLICY -> OPERATING_POLICY_REQUIRED;
        };
    }
    private boolean isSourceOperation(Operation operation) {
        return switch (operation) {
            case SOURCE_DISCOVER, SOURCE_PREVIEW, SOURCE_INDEX, SOURCE_REFRESH, SOURCE_REMOVE -> true;
            default -> false;
        };
    }
    private boolean blank(String value) { return value == null || value.isBlank(); }
}
