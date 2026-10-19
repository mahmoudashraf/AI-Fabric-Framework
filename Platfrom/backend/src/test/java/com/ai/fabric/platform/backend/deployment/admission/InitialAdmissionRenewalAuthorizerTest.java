package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerPlacementEvidenceEntity;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerSelfServiceAuthorizationService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerTargetPlacementService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InitialAdmissionRenewalAuthorizerTest {
    private CustomerSelfServiceAuthorizationService authorization;
    private CustomerTargetPlacementService placement;
    private DeploymentTargetProfileRepository targets;
    private EntityManager entityManager;
    private InitialAdmissionRenewalAuthorizer guard;
    private CustomerOfferContracts.AdmissionContext scope;
    private PrerequisiteReadback readback;
    private DeploymentTargetProfileEntity target;
    private final Instant checked = Instant.parse("2026-10-19T22:00:00Z");

    @BeforeEach void authority() {
        authorization = mock(CustomerSelfServiceAuthorizationService.class);
        placement = mock(CustomerTargetPlacementService.class);
        targets = mock(DeploymentTargetProfileRepository.class);
        entityManager = mock(EntityManager.class);
        guard = new InitialAdmissionRenewalAuthorizer(authorization, placement, targets);
        ReflectionTestUtils.setField(guard, "entityManager", entityManager);
        scope = scope("P2", 4);
        target = new DeploymentTargetProfileEntity(); target.setId("target");
        when(authorization.prepareAdmission(eq("customer"), any()))
            .thenReturn(new CustomerOfferContracts.Authorization(scope, null));
        when(targets.findById("target")).thenReturn(Optional.of(target));
        when(placement.requireEvidenceById(target, scope.intent(), "P2")).thenReturn(proof("server", checked, checked.plusSeconds(900)));
        readback = readback(checked, checked.plusSeconds(300));
    }

    @Test void rerunsOwningAuthorizationAndChecksExactPlacementWithoutRemoteProbe() {
        guard.requireCurrent(scope, readback);
        var ordered = inOrder(entityManager, authorization, placement);
        ordered.verify(entityManager).clear();
        ordered.verify(authorization).prepareAdmission(eq("customer"), argThat(request -> request.offerId().equals("offer")
            && request.operationKey().equals("apply") && request.operation() == CustomerOfferContracts.Operation.APPLY
            && request.releaseId() == null && request.targetProfileId().equals("target")));
        ordered.verify(placement).requireEvidenceById(target, scope.intent(), "P2");
        verify(placement, never()).probeAndRecord(any(), any());
    }

    @Test void entitlementOrPlacementContextChangedDuringCollectionFailsClosed() {
        when(authorization.prepareAdmission(eq("customer"), any()))
            .thenReturn(new CustomerOfferContracts.Authorization(scope("P2", 5), null));
        assertThatThrownBy(() -> guard.requireCurrent(scope, readback)).hasMessageContaining("INITIAL_RENEWAL_CUSTOMER_CONTEXT_CHANGED");
        when(authorization.prepareAdmission(eq("customer"), any()))
            .thenReturn(new CustomerOfferContracts.Authorization(scope("P3", 4), null));
        assertThatThrownBy(() -> guard.requireCurrent(scope, readback)).hasMessageContaining("INITIAL_RENEWAL_CUSTOMER_CONTEXT_CHANGED");
        verifyNoInteractions(placement);
    }

    @Test void revokedOrExpiredEntitlementDenialIsNotSwallowed() {
        when(authorization.prepareAdmission(eq("customer"), any()))
            .thenThrow(new AdmissionFailure(org.springframework.http.HttpStatus.FORBIDDEN, "ENTITLEMENT_EXPIRED"));
        assertThatThrownBy(() -> guard.requireCurrent(scope, readback)).hasMessageContaining("ENTITLEMENT_EXPIRED");
        verifyNoInteractions(placement);
    }

    @Test void readbackFromDifferentPlacementTimeOrLongerLifetimeIsRejected() {
        assertThatThrownBy(() -> guard.requireCurrent(scope, readback(checked.plusNanos(1), checked.plusSeconds(300))))
            .hasMessageContaining("INITIAL_RENEWAL_PLACEMENT_PROOF_CHANGED");
        assertThatThrownBy(() -> guard.requireCurrent(scope, readback(checked, checked.plusSeconds(901))))
            .hasMessageContaining("INITIAL_RENEWAL_PLACEMENT_PROOF_CHANGED");
    }

    @Test void sameIdCannotSubstituteDifferentPhysicalPlacement() {
        when(placement.requireEvidenceById(target, scope.intent(), "P2"))
            .thenReturn(proof("different-server", checked, checked.plusSeconds(900)));
        assertThatThrownBy(() -> guard.requireCurrent(scope, readback)).hasMessageContaining("INITIAL_RENEWAL_PLACEMENT_PROOF_CHANGED");
    }

    @Test void staleExactPlacementRowDenialIsPreserved() {
        when(placement.requireEvidenceById(target, scope.intent(), "P2"))
            .thenThrow(new AdmissionFailure(org.springframework.http.HttpStatus.CONFLICT, "PLACEMENT_EVIDENCE_STALE"));
        assertThatThrownBy(() -> guard.requireCurrent(scope, readback)).hasMessageContaining("PLACEMENT_EVIDENCE_STALE");
    }

    @Test void missingReadbackCannotTriggerAuthorizationOrProviderCalls() {
        assertThatThrownBy(() -> guard.requireCurrent(scope, null)).hasMessageContaining("INITIAL_RENEWAL_PLACEMENT_PROOF_REQUIRED");
        verifyNoInteractions(authorization, placement);
    }

    private CustomerOfferContracts.AdmissionContext scope(String placementId, long revision) {
        return new CustomerOfferContracts.AdmissionContext("customer", "tenant", "deployment", null, "target",
            CustomerOfferContracts.TargetIntent.CUSTOMER_STAGING, "offer", "offer-fingerprint", revision, placementId,
            CustomerOfferContracts.Operation.APPLY, "apply", null, null);
    }
    private CustomerPlacementEvidenceEntity proof(String server, Instant observed, Instant expires) {
        return new CustomerPlacementEvidenceEntity("P2", "target", scope.intent(), "profile-fingerprint",
            server, "address", "destination", observed, expires);
    }
    private PrerequisiteReadback readback(Instant observed, Instant expires) {
        return new PrerequisiteReadback(observed, expires, "proof", new Placement("server", "destination", "address"),
            "profile-fingerprint", "manifest-fingerprint", "binding-fingerprint", java.util.Set.of(),
            "restore", "privacy", "owner", "notice");
    }
}
