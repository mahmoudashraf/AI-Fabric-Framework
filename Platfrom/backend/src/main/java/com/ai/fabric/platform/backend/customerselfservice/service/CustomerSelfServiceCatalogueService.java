package com.ai.fabric.platform.backend.customerselfservice.service;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferEntitlementRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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
        return entitlements.findByCustomerIdOrderByCreatedAtAsc(actor.customerId()).stream()
            .filter(entity -> entity.getStatus() == EntitlementStatus.ACTIVE && actor.userId().equals(entity.getOwnerUserId()))
            .map(entity -> offer(entity.getOfferId())).toList();
    }

    @Transactional(readOnly = true)
    public OfferSummary offer(String offerId) {
        var actor = access.requireCustomerActor();
        var entitlement = access.requireEntitlement(actor.customerId(), offerId, actor.userId(), false);
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
                    String readiness = "PLACEMENT_VERIFIED_ADMISSION_REQUIRED";
                    List<String> targetBlockers = new ArrayList<>();
                    try {
                        placement.requireFreshEvidence(profile, permission.intent());
                    } catch (CustomerSelfServiceException ex) {
                        if (ex.code() != PLACEMENT_EVIDENCE_REQUIRED && ex.code() != PLACEMENT_EVIDENCE_STALE) throw ex;
                        readiness = "PLACEMENT_RECHECK_REQUIRED";
                        targetBlockers.add(ex.code().name());
                        blockers.add(ex.code().name());
                    }
                    // Discovery never reserves capacity or claims admission readiness.
                    targetBlockers.add(PREREQUISITE_EVIDENCE_REQUIRED.name());
                    var capabilities = definition.operations().stream()
                        .filter(operation -> CustomerPermissionMatrix.row(operation).targetIntents().contains(permission.intent()))
                        .collect(java.util.stream.Collectors.toSet());
                    targets.add(new TargetSummary(permission.targetProfileId(), permission.intent(), readiness,
                        capabilities, definition.limits(), List.copyOf(targetBlockers)));
                } catch (CustomerSelfServiceException ex) { blockers.add(ex.code().name()); }
            }
        } catch (CustomerSelfServiceException ex) { blockers.add(ex.code().name()); }
        return new OfferSummary(offer.getId(), definition.name(), definition.templateId(), definition.template().versionId(),
            offer.getFingerprint(), access.summary(entitlement), List.copyOf(targets), CustomerPermissionMatrix.rows(definition.operations()),
            definition.settings(), definition.bindingSlots(), definition.documentAudiences(), blockers.stream().distinct().toList(),
            compositionReady ? definition.workspaceProfiles() : List.of());
    }
}
