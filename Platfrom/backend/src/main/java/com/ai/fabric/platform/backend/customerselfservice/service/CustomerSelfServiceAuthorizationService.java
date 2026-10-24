package com.ai.fabric.platform.backend.customerselfservice.service;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.*;
import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.*;

@Service
public class CustomerSelfServiceAuthorizationService {
    private enum EvidenceMode { REQUIRED, ADMISSION_PREPARATION, SOURCE_REFRESH, OPERATIONAL_READ }
    private final CustomerOfferEntitlementService entitlements;
    private final CustomerReviewedCompositionService composition;
    private final CustomerTargetPlacementService placement;
    private final DeploymentRepository deployments;
    private final DeploymentReleaseRepository releases;
    private final DeploymentVersionRepository versions;
    private final DeploymentTargetProfileRepository profiles;
    private final PlatformTenantRepository tenants;
    private final PlatformConsumerRepository consumers;
    private final DeploymentAccessService access;
    private final CustomerOfferDeploymentRepository offerDeployments;
    private final com.ai.fabric.platform.backend.deployment.service.DeploymentDocumentBindingResolver documentBindings;

    public CustomerSelfServiceAuthorizationService(CustomerOfferEntitlementService entitlements,
        CustomerReviewedCompositionService composition, CustomerTargetPlacementService placement,
        DeploymentRepository deployments, DeploymentReleaseRepository releases, DeploymentVersionRepository versions,
        DeploymentTargetProfileRepository profiles,
        PlatformTenantRepository tenants, PlatformConsumerRepository consumers, DeploymentAccessService access,
        CustomerOfferDeploymentRepository offerDeployments,
        com.ai.fabric.platform.backend.deployment.service.DeploymentDocumentBindingResolver documentBindings) {
        this.entitlements = entitlements;
        this.composition = composition;
        this.placement = placement;
        this.deployments = deployments;
        this.releases = releases;
        this.versions = versions;
        this.profiles = profiles;
        this.tenants = tenants;
        this.consumers = consumers;
        this.access = access;
        this.offerDeployments = offerDeployments;
        this.documentBindings = documentBindings;
    }

    /** Join the owning command transaction so revoke cannot race the persisted mutation. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Authorization authorize(String customerId, AuthorizationRequest request) {
        return authorize(customerId, request, EvidenceMode.REQUIRED);
    }

    /** Only pure reads of an explicit existing release may use historical placement identity. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Authorization authorizeOperationalRead(String customerId, AuthorizationRequest request) {
        if (request == null || request.operation() != Operation.READ) throw deny(OPERATION_FORBIDDEN);
        if (blank(request.releaseId())) throw deny(RESOURCE_SCOPE_MISMATCH);
        return authorize(customerId, request, EvidenceMode.OPERATIONAL_READ);
    }

    /** Pure target status/list reads require an existing owned deployment and its reviewed offer target. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Authorization authorizeOwnedTargetRead(String customerId, AuthorizationRequest request) {
        if (request == null || request.operation() != Operation.READ) throw deny(OPERATION_FORBIDDEN);
        if (blank(request.deploymentId()) || blank(request.targetProfileId()) || request.releaseId() != null) {
            throw deny(RESOURCE_SCOPE_MISMATCH);
        }
        return authorize(customerId, request, EvidenceMode.OPERATIONAL_READ);
    }

    /**
     * Compatibility boundary for release preparation. Core prerequisites always apply;
     * optional capacity policy does not authorize the command.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Authorization prepareAdmission(String customerId, AuthorizationRequest request) {
        return authorize(customerId, request, EvidenceMode.ADMISSION_PREPARATION);
    }

    /** Compatibility boundary for source preparation; it grants no prerequisite bypass. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Authorization prepareSourcePrerequisiteRefresh(String customerId, AuthorizationRequest request) {
        return authorize(customerId, request, EvidenceMode.SOURCE_REFRESH);
    }

    private Authorization authorize(String customerId, AuthorizationRequest request, EvidenceMode evidenceMode) {
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
        if ((evidenceMode == EvidenceMode.ADMISSION_PREPARATION && !row.admissionEvidenceRequired())
            || (evidenceMode == EvidenceMode.SOURCE_REFRESH && !isSourceOperation(request.operation()))) {
            throw deny(OPERATION_FORBIDDEN);
        }
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
            var version = versions.findById(release.getDeploymentVersionId())
                .orElseThrow(() -> deny(RESOURCE_SCOPE_MISMATCH));
            if (!deployment.getId().equals(version.getDeploymentId())) throw deny(RESOURCE_SCOPE_MISMATCH);
            composition.requireVersion(definition, version);
        } else if (request.operation() == Operation.ACTIVATE || request.operation() == Operation.ROLLBACK
            || request.operation() == Operation.TARGET_RETIRE || request.operation() == Operation.VERIFY
            || isSourceOperation(request.operation())) throw deny(RESOURCE_SCOPE_MISMATCH);
        requireBindingAndAudience(definition, row, request);
        composition.validate(definition);
        var profile = profiles.findById(target.targetProfileId()).orElseThrow(() -> deny(UNENTITLED_TARGET));
        composition.requireProfile(profile, target);
        placement.requireConfiguredPlacement(profile, target.intent());
        if (request.operation() != Operation.READ && request.operation() != Operation.TEMPLATE_SELECT
            && request.operation() != Operation.SOURCE_DISCOVER && request.operation() != Operation.SOURCE_PREVIEW) {
            entitlements.requireOperationKey(request.operationKey());
        }
        AdmissionContext context = new AdmissionContext(customerId, request.tenantId(), request.deploymentId(),
            request.releaseId(), target.targetProfileId(), target.intent(), offer.getId(), offer.getFingerprint(),
            entitlement.getRevision(), null,
            request.operation(), request.operationKey(), definition.limits(), definition.topology());
        requirePrerequisites(context, requiredPrerequisites(definition, row));
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
        requireCorePrerequisites(context, required);
    }

    private void requireCorePrerequisites(AdmissionContext context, Set<Prerequisite> required) {
        // Provisioning validates new connections; source commands validate the actual frozen release binding.
        if (blank(context.releaseId())) return;
        var release = releases.findById(context.releaseId()).orElseThrow(() -> deny(RESOURCE_SCOPE_MISMATCH));
        var deployment = deployments.findById(context.deploymentId()).orElseThrow(() -> deny(RESOURCE_SCOPE_MISMATCH));
        var version = versions.findById(release.getDeploymentVersionId()).orElseThrow(() -> deny(RESOURCE_SCOPE_MISMATCH));
        if (!context.deploymentId().equals(release.getDeploymentId())
            || !context.targetProfileId().equals(release.getTargetProfileId())
            || !context.customerId().equals(deployment.getCustomerId()) || !context.tenantId().equals(deployment.getTenantId())
            || !deployment.getId().equals(version.getDeploymentId()) || !"PUBLISHED".equals(version.getStatus()))
            throw deny(RESOURCE_SCOPE_MISMATCH);
        if (required.contains(Prerequisite.STORAGE) && documentBindings.required(version))
            documentBindings.requireUnchanged(deployment, version, release);
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

    public void requireReviewedVersion(Authorization authorization,
                                       com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity version) {
        if (authorization == null || version == null
            || !authorization.context().deploymentId().equals(version.getDeploymentId())) {
            throw deny(RESOURCE_SCOPE_MISMATCH);
        }
        composition.requireVersion(authorization.definition(), version);
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
        placement.requireConfiguredPlacement(profile, context.intent());
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
                || !context.targetProfileId().equals(previous.getBootstrapTargetProfileId())
                || !context.offerFingerprint().equals(previous.getOfferFingerprint())) throw conflict(OPERATION_KEY_CONFLICT);
            return;
        }
        offerDeployments.saveAndFlush(new CustomerOfferDeploymentEntity(deploymentId, context.customerId(), deployment.getTenantId(),
            context.offerId(), context.targetProfileId(), context.offerFingerprint(), context.entitlementRevision(),
            context.operationKey(), Instant.now()));
    }

    private boolean isSourceOperation(Operation operation) {
        return switch (operation) {
            case SOURCE_DISCOVER, SOURCE_PREVIEW, SOURCE_INDEX, SOURCE_REFRESH, SOURCE_REMOVE -> true;
            default -> false;
        };
    }
    private boolean blank(String value) { return value == null || value.isBlank(); }
}
