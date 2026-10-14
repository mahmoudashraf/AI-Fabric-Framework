package com.ai.fabric.platform.backend.customerselfservice.service;

import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerPlacementEvidenceEntity;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.*;
import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.*;

@Service
public class CustomerPlacementProbeService {
    private final CustomerOfferEntitlementService entitlements;
    private final CustomerReviewedCompositionService composition;
    private final CustomerTargetPlacementService placement;
    private final DeploymentTargetProfileRepository profiles;

    public CustomerPlacementProbeService(CustomerOfferEntitlementService entitlements,
        CustomerReviewedCompositionService composition, CustomerTargetPlacementService placement,
        DeploymentTargetProfileRepository profiles) {
        this.entitlements = entitlements;
        this.composition = composition;
        this.placement = placement;
        this.profiles = profiles;
    }

    @Transactional(propagation = Propagation.NEVER)
    public PlacementEvidenceSummary probeForOffer(String offerId, String targetProfileId) {
        var actor = entitlements.requireCustomerActor();
        entitlements.requireEntitlement(actor.customerId(), offerId, actor.userId(), false);
        var definition = entitlements.definition(entitlements.requireOffer(offerId));
        var permission = definition.targets().stream().filter(target -> target.targetProfileId().equals(targetProfileId))
            .findFirst().orElseThrow(() -> deny(UNENTITLED_TARGET));
        composition.validate(definition);
        var profile = profiles.findById(targetProfileId).orElseThrow(() -> deny(UNENTITLED_TARGET));
        composition.requireProfile(profile, permission);
        return summary(placement.probeAndRecord(profile, permission.intent()));
    }

    @Transactional(propagation = Propagation.NEVER)
    public PlacementEvidenceSummary probeForStaff(String targetProfileId, PlacementProbeRequest request) {
        entitlements.requireStaffAdmin();
        if (request == null || request.intent() == null) throw invalid();
        var profile = profiles.findById(targetProfileId).orElseThrow(() -> deny(UNENTITLED_TARGET));
        composition.requireProfile(profile, new TargetPermission(targetProfileId, request.intent(), composition.profileFingerprint(profile)));
        return summary(placement.probeAndRecord(profile, request.intent()));
    }

    private PlacementEvidenceSummary summary(CustomerPlacementEvidenceEntity evidence) {
        return new PlacementEvidenceSummary(evidence.getId(), evidence.getTargetProfileId(), evidence.getIntent(),
            evidence.getProfileFingerprint(), evidence.getCheckedAt(), evidence.getExpiresAt());
    }
}
