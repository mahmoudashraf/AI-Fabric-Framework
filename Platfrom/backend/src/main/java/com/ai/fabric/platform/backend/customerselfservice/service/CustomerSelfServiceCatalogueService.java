package com.ai.fabric.platform.backend.customerselfservice.service;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferEntitlementRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.time.Instant;
import java.util.List;

import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.*;
import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.*;

@Service
public class CustomerSelfServiceCatalogueService {
    private final CustomerOfferEntitlementService access;
    private final CustomerOfferEntitlementRepository entitlements;
    private final DeploymentTargetProfileRepository profiles;
    private final CustomerReviewedCompositionService composition;
    private final CustomerTargetPlacementService placement;

    public CustomerSelfServiceCatalogueService(CustomerOfferEntitlementService access,
        CustomerOfferEntitlementRepository entitlements, DeploymentTargetProfileRepository profiles,
        CustomerReviewedCompositionService composition, CustomerTargetPlacementService placement) {
        this.access = access;
        this.entitlements = entitlements;
        this.profiles = profiles;
        this.composition = composition;
        this.placement = placement;
    }

    @Transactional(readOnly = true)
    public List<OfferSummary> catalogue() {
        var actor = access.requireCustomerActor();
        return catalogue(actor.customerId(), actor.userId());
    }

    @Transactional(readOnly = true)
    public List<OfferSummary> catalogueForCustomer(String customerId) {
        access.requireStaffCustomerAccess(customerId);
        access.requireActiveCustomer(customerId);
        return catalogue(customerId, null);
    }

    private List<OfferSummary> catalogue(String customerId, String ownerUserId) {
        var now = Instant.now();
        return entitlements.findByCustomerIdOrderByCreatedAtAsc(customerId).stream()
            .filter(entity -> entity.getStatus() == EntitlementStatus.ACTIVE
                && (ownerUserId == null || ownerUserId.equals(entity.getOwnerUserId())))
            .filter(entity -> entity.getExpiresAt() != null && entity.getExpiresAt().isAfter(now))
            .map(entity -> offer(customerId, ownerUserId, entity.getOfferId())).toList();
    }

    @Transactional(readOnly = true)
    public OfferSummary offer(String offerId) {
        var actor = access.requireCustomerActor();
        return offer(actor.customerId(), actor.userId(), offerId);
    }

    @Transactional(readOnly = true)
    public OfferSummary offerForCustomer(String customerId, String offerId) {
        access.requireStaffCustomerAccess(customerId);
        access.requireActiveCustomer(customerId);
        return offer(customerId, null, offerId);
    }

    private OfferSummary offer(String customerId, String ownerUserId, String offerId) {
        var entitlement = access.requireEntitlement(customerId, offerId, ownerUserId, false);
        var offer = access.requireOffer(offerId);
        var definition = access.definition(offer);
        List<String> blockers = new ArrayList<>();
        List<TargetSummary> targets = new ArrayList<>();
        boolean compositionReady = false;
        try {
            composition.validate(definition);
            compositionReady = true;
            for (TargetPermission permission : definition.targets()) {
                try {
                    var profile = profiles.findById(permission.targetProfileId()).orElseThrow(() -> conflict(UNENTITLED_TARGET));
                    composition.requireProfile(profile, permission);
                    String readiness = "REVIEWED_TARGET_READY";
                    List<String> targetBlockers = new ArrayList<>();
                    placement.requireConfiguredPlacement(profile, permission.intent());
                    var capabilities = definition.operations().stream()
                        .filter(CustomerPermissionMatrix::supported)
                        .filter(operation -> CustomerPermissionMatrix.row(operation).targetIntents().contains(permission.intent()))
                        .collect(java.util.stream.Collectors.toSet());
                    targets.add(new TargetSummary(permission.targetProfileId(), permission.intent(), readiness,
                        capabilities, definition.limits(), List.copyOf(targetBlockers)));
                } catch (CustomerSelfServiceException ex) { blockers.add(ex.code().name()); }
            }
        } catch (CustomerSelfServiceException ex) { blockers.add(ex.code().name()); }
        return new OfferSummary(offer.getId(), definition.name(), definition.templateId(), definition.template().versionId(),
            offer.getFingerprint(), access.summary(entitlement), List.copyOf(targets), CustomerPermissionMatrix.rows(definition.operations()),
            definition.settings(), definition.bindingSlots().stream()
                .filter(slot -> slot.kind() != BindingKind.INBOX_OPERATOR).toList(),
            definition.documentAudiences(), blockers.stream().distinct().toList(),
            compositionReady ? definition.workspaceProfiles() : List.of(),
            compositionReady ? definition.externalIntegrationProfiles() : List.of());
    }
}
