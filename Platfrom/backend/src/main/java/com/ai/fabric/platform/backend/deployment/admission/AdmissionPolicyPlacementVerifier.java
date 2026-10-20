package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.TargetIntent;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerTargetPlacementService;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentTargetProfileIdentity;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static org.springframework.http.HttpStatus.PRECONDITION_FAILED;

/** Only local proof reads: publication already holds the authoritative profile rows before clearing JPA. */
@Component
class AdmissionPolicyPlacementVerifier {
    private final DeploymentTargetProfileRepository targets;
    private final CustomerTargetPlacementService placements;
    private final ObjectMapper mapper;
    private final AdmissionJson json;
    @PersistenceContext private EntityManager entities;

    AdmissionPolicyPlacementVerifier(DeploymentTargetProfileRepository targets, CustomerTargetPlacementService placements,
                                     ObjectMapper mapper, AdmissionJson json) {
        this.targets = targets; this.placements = placements; this.mapper = mapper; this.json = json;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Map<String, String> requireCurrent(FleetPolicy policy) {
        entities.clear();
        var references = new TreeMap<String, String>();
        policy.targets().forEach((id, budget) -> {
            var profile = targets.findById(id).orElseThrow(() -> denied());
            if (!profile.isActive() || profile.getProviderType() != DeploymentProviderType.COOLIFY
                || !budget.profileFingerprint().equals(DeploymentTargetProfileIdentity.fingerprint(mapper, profile))) throw denied();
            var proof = placements.requireFreshEvidence(profile, TargetIntent.valueOf(budget.intent().name()));
            Instant now = Instant.now();
            if (!id.equals(proof.getTargetProfileId()) || !budget.intent().name().equals(proof.getIntent().name())
                || !budget.profileFingerprint().equals(proof.getProfileFingerprint())
                || !budget.placement().equals(new Placement(proof.getServerUuid(), proof.getDestinationUuid(), proof.getServerAddress()))
                || proof.getCheckedAt() == null || proof.getCheckedAt().isAfter(now)
                || proof.getExpiresAt() == null || !proof.getExpiresAt().isAfter(now)) throw denied();
            references.put(id, json.fingerprint(Map.of("id", proof.getId(), "checkedAt", proof.getCheckedAt(),
                "expiresAt", proof.getExpiresAt(), "profile", proof.getProfileFingerprint(), "placement", budget.placement())));
        });
        return Map.copyOf(references);
    }

    private AdmissionFailure denied() { return new AdmissionFailure(PRECONDITION_FAILED, "POLICY_CONTINUITY_PLACEMENT_REQUIRED"); }
}
