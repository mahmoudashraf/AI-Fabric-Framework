package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerPlacementEvidenceEntity;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.TargetIntent;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerTargetPlacementService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentTargetProfileIdentity;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdmissionPolicyPlacementVerifierTest extends AdmissionFixture {
    private DeploymentTargetProfileRepository targets;
    private CustomerTargetPlacementService placements;
    private EntityManager entities;
    private AdmissionPolicyPlacementVerifier verifier;
    private DeploymentTargetProfileEntity profile;
    private FleetPolicy reviewed;
    private Instant checked;

    @BeforeEach void verifier() throws Exception {
        targets = mock(DeploymentTargetProfileRepository.class); placements = mock(CustomerTargetPlacementService.class); entities = mock(EntityManager.class);
        verifier = new AdmissionPolicyPlacementVerifier(targets, placements, mapper, json);
        ReflectionTestUtils.setField(verifier, "entities", entities);
        profile = new DeploymentTargetProfileEntity(); profile.setId(TARGET); profile.setActive(true); profile.setProviderType(DeploymentProviderType.COOLIFY);
        profile.setEnvironmentName("customer-staging"); profile.setSourceStrategy("IMAGE_SOURCE");
        profile.setProviderConfigJson(mapper.writeValueAsString(Map.of("serverUuid", STAGING.serverUuid(), "destinationUuid", STAGING.destinationUuid())));
        profile.setNetworkPolicyJson("{}"); profile.setResourceDefaultsJson("{}");
        String fingerprint = DeploymentTargetProfileIdentity.fingerprint(mapper, profile);
        var template = policy(3);
        reviewed = new FleetPolicy(template.id(), template.fleet(), Map.of(TARGET,
            new TargetBudget(TARGET, Intent.CUSTOMER_STAGING, STAGING, fingerprint, template.fleet())), template.lifecycle(),
            template.restoreEvidenceReference(), template.privacyEvidenceReference(), template.supportReference());
        checked = Instant.now().minusSeconds(1);
        when(targets.findById(TARGET)).thenReturn(Optional.of(profile));
        when(placements.requireFreshEvidence(profile, TargetIntent.CUSTOMER_STAGING)).thenReturn(proof(STAGING, fingerprint, checked, checked.plusSeconds(120)));
    }

    @Test void clearsCachedProfilesThenChecksCurrentImmutablePlacementWithoutProviderCalls() {
        var receipts = verifier.requireCurrent(reviewed);
        assertThat(receipts).containsOnlyKeys(TARGET);
        assertThat(receipts.get(TARGET)).matches("[a-f0-9]{64}");
        var order = inOrder(entities, targets, placements);
        order.verify(entities).clear(); order.verify(targets).findById(TARGET);
        order.verify(placements).requireFreshEvidence(profile, TargetIntent.CUSTOMER_STAGING);
        verify(placements, never()).probeAndRecord(any(), any());
    }

    @ParameterizedTest @ValueSource(strings={"disabled","profile","placement","fingerprint","expired","future"})
    void currentAuthorityDriftCannotBeReplacedByCallerDeclaredPolicy(String changed) {
        switch (changed) {
            case "disabled" -> profile.setActive(false);
            case "profile" -> profile.setNetworkPolicyJson("{\"changed\":true}");
            case "placement" -> when(placements.requireFreshEvidence(any(), any())).thenReturn(proof(new Placement("other", STAGING.destinationUuid(), STAGING.ip()),
                reviewed.targets().get(TARGET).profileFingerprint(), checked, checked.plusSeconds(120)));
            case "fingerprint" -> when(placements.requireFreshEvidence(any(), any())).thenReturn(proof(STAGING, "different", checked, checked.plusSeconds(120)));
            case "expired" -> when(placements.requireFreshEvidence(any(), any())).thenReturn(proof(STAGING, reviewed.targets().get(TARGET).profileFingerprint(), checked.minusSeconds(120), checked));
            case "future" -> when(placements.requireFreshEvidence(any(), any())).thenReturn(proof(STAGING, reviewed.targets().get(TARGET).profileFingerprint(), checked.plusSeconds(60), checked.plusSeconds(120)));
        }
        assertThatThrownBy(() -> verifier.requireCurrent(reviewed)).hasMessageContaining("POLICY_CONTINUITY_PLACEMENT_REQUIRED");
    }

    private CustomerPlacementEvidenceEntity proof(Placement placement, String fingerprint, Instant at, Instant until) {
        return new CustomerPlacementEvidenceEntity("fresh-placement", TARGET, TargetIntent.CUSTOMER_STAGING, fingerprint,
            placement.serverUuid(), placement.ip(), placement.destinationUuid(), at, until);
    }
}
