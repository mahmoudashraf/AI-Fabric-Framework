package com.ai.fabric.platform.backend.customerselfservice;

import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferEntity;
import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferEntitlementEntity;
import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerPlacementEvidenceEntity;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferEntitlementRepository;
import com.ai.fabric.platform.backend.customerselfservice.service.*;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.*;
import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerSelfServiceCatalogueServiceTest {
    private final CustomerOfferEntitlementService access = mock(CustomerOfferEntitlementService.class);
    private final CustomerReviewedCompositionService composition = mock(CustomerReviewedCompositionService.class);
    private final DeploymentTargetProfileRepository profiles = mock(DeploymentTargetProfileRepository.class);
    private final CustomerTargetPlacementService placement = mock(CustomerTargetPlacementService.class);
    private final CustomerOfferEntitlementRepository grants = mock(CustomerOfferEntitlementRepository.class);
    private final CustomerSelfServiceCatalogueService catalogue = new CustomerSelfServiceCatalogueService(access,
        grants, profiles, composition, placement);
    private Definition definition;

    @BeforeEach void setUp() {
        var codec = CustomerOfferTestFixtures.codec();
        var base = CustomerOfferTestFixtures.definition();
        definition = new Definition(base.schemaVersion(), base.name(), base.templateId(), base.template(), base.plugins(),
            base.artifacts(), base.targets(), base.operations(), base.settings(), base.bindingSlots(), base.documentAudiences(),
            base.admissionPrerequisites(), base.limits(), base.topology(), base.approvedPolicyRef(), base.operatingPolicyRef(),
            base.identityPolicyRef(), base.storagePolicyRef(), List.of(new WorkspaceProfile("reviewed", "Reviewed workspace",
                codec.tree("{}"), codec.tree("{}"), codec.tree("{}"))));
        when(access.requireCustomerActor()).thenReturn(new CustomerOfferEntitlementService.CustomerActor("customer", "owner"));
        when(access.requireEntitlement("customer", "offer", "owner", false)).thenReturn(new CustomerOfferEntitlementEntity());
        var offer = new CustomerOfferEntity("offer", "{}", "fingerprint", "review", "staff", Instant.now());
        when(access.requireOffer("offer")).thenReturn(offer);
        when(access.definition(offer)).thenReturn(definition);
    }

    @Test void theEntitledOwnerReceivesReviewedPayloadsBeforeTargetPlacementIsReady() {
        var result = catalogue.offer("offer");
        assertThat(result.workspaceProfiles()).isEqualTo(definition.workspaceProfiles());
        assertThat(result.targets()).isEmpty();
        assertThat(result.blockers()).contains(UNENTITLED_TARGET.name());
        verify(access).requireEntitlement("customer", "offer", "owner", false);
        verifyNoInteractions(placement);
    }

    @Test void expiredHistoricalOfferCannotBlockTheCurrentCustomerCatalogue() {
        var valid = grant("offer", EntitlementStatus.ACTIVE, "owner", Instant.now().plusSeconds(3600));
        var expired = grant("old-offer", EntitlementStatus.ACTIVE, "owner", Instant.now().minusSeconds(1));
        var revoked = grant("revoked", EntitlementStatus.REVOKED, "owner", Instant.now().plusSeconds(3600));
        var invited = grant("invited", EntitlementStatus.INVITED, "owner", Instant.now().plusSeconds(3600));
        var foreignOwner = grant("foreign-owner", EntitlementStatus.ACTIVE, "another-owner", Instant.now().plusSeconds(3600));
        var missingExpiry = grant("missing-expiry", EntitlementStatus.ACTIVE, "owner", null);
        when(grants.findByCustomerIdOrderByCreatedAtAsc("customer"))
            .thenReturn(List.of(expired, valid, revoked, invited, foreignOwner, missingExpiry));

        assertThat(catalogue.catalogue()).extracting(OfferSummary::id).containsExactly("offer");

        verify(access).requireEntitlement("customer", "offer", "owner", false);
        for (String id : List.of("old-offer", "revoked", "invited", "foreign-owner", "missing-expiry"))
            verify(access, never()).requireEntitlement("customer", id, "owner", false);
        assertThat(expired.getStatus()).isEqualTo(EntitlementStatus.ACTIVE);
        assertThat(expired.getExpiresAt()).isBefore(Instant.now());
    }

    @Test void catalogueStillRequiresFreshAuthorizationForEveryUnexpiredOffer() {
        when(grants.findByCustomerIdOrderByCreatedAtAsc("customer"))
            .thenReturn(List.of(grant("offer", EntitlementStatus.ACTIVE, "owner", Instant.now().plusSeconds(3600))));
        when(access.requireEntitlement("customer", "offer", "owner", false)).thenThrow(deny(ENTITLEMENT_REVOKED));

        assertThatThrownBy(catalogue::catalogue).hasMessageContaining(ENTITLEMENT_REVOKED.name());
        verify(access, never()).requireOffer(anyString());
    }

    private CustomerOfferEntitlementEntity grant(String offerId, EntitlementStatus status, String owner, Instant expiresAt) {
        var entity = new CustomerOfferEntitlementEntity();
        entity.setCustomerId("customer"); entity.setOfferId(offerId); entity.setOwnerUserId(owner);
        entity.setStatus(status); entity.setExpiresAt(expiresAt);
        return entity;
    }

    @ParameterizedTest @EnumSource(value = Code.class, names = {
        "REVIEWED_COMPOSITION_CHANGED", "PLACEMENT_EVIDENCE_REQUIRED", "PLACEMENT_EVIDENCE_STALE"
    })
    void failedCompositionValidationDoesNotExposeTargetsOrProfilePayloads(Code failure) {
        doThrow(conflict(failure)).when(composition).validate(definition);
        var result = catalogue.offer("offer");
        assertThat(result.workspaceProfiles()).isEmpty();
        assertThat(result.targets()).isEmpty();
        assertThat(result.blockers()).containsExactly(failure.name());
        verifyNoInteractions(profiles, placement);
    }

    @ParameterizedTest @EnumSource(value = Code.class, names = {
        "ENTITLEMENT_REQUIRED", "ENTITLEMENT_EXPIRED", "ENTITLEMENT_REVOKED"
    })
    void missingOrInactiveOwnerEntitlementDeniesBeforeConfigurationIsRead(Code failure) {
        when(access.requireEntitlement("customer", "offer", "owner", false)).thenThrow(deny(failure));
        assertThatThrownBy(() -> catalogue.offer("offer")).hasMessageContaining(failure.name());
        verify(access, never()).requireOffer("offer");
        verifyNoInteractions(composition, profiles, placement);
    }

    @ParameterizedTest @EnumSource(value = Code.class, names = {"PLACEMENT_EVIDENCE_REQUIRED", "PLACEMENT_EVIDENCE_STALE"})
    void reviewedTargetRemainsDiscoverableWithAnExplicitPlacementRecheckBlocker(Code failure) {
        var staging = reviewedProfiles();
        when(placement.requireFreshEvidence(staging, TargetIntent.CUSTOMER_STAGING)).thenThrow(conflict(failure));

        var result = catalogue.offer("offer");

        assertThat(result.targets()).extracting(TargetSummary::id).containsExactly("staging-target", "production-target");
        var target = result.targets().getFirst();
        assertThat(target.intent()).isEqualTo(TargetIntent.CUSTOMER_STAGING);
        assertThat(target.readiness()).isEqualTo("PLACEMENT_RECHECK_REQUIRED");
        assertThat(target.blockers()).containsExactly(failure.name(), PREREQUISITE_EVIDENCE_REQUIRED.name());
        assertThat(target.capabilities()).contains(Operation.APPLY).doesNotContain(Operation.PROMOTION_APPLY);
        assertThat(target.limits()).isEqualTo(definition.limits());
        assertThat(result.targets().getLast().readiness()).isEqualTo("PLACEMENT_VERIFIED_ADMISSION_REQUIRED");
        assertThat(result.targets().getLast().blockers()).containsExactly(PREREQUISITE_EVIDENCE_REQUIRED.name());
        assertThat(result.blockers()).containsExactly(failure.name());
        assertThat(result.workspaceProfiles()).isEqualTo(definition.workspaceProfiles());
        var order = inOrder(composition, profiles, placement);
        order.verify(composition).validate(definition);
        order.verify(profiles).findById("staging-target");
        order.verify(composition).requireProfile(staging, definition.targets().getFirst());
        order.verify(placement).requireFreshEvidence(staging, TargetIntent.CUSTOMER_STAGING);
        verify(placement, never()).probeAndRecord(any(), any());
    }

    @Test void freshPlacementRetainsTheExistingDiscoveryOnlyReadinessAndCapabilities() {
        reviewedProfiles();

        var result = catalogue.offer("offer");

        assertThat(result.targets()).hasSize(2).allSatisfy(target -> {
            assertThat(target.readiness()).isEqualTo("PLACEMENT_VERIFIED_ADMISSION_REQUIRED");
            assertThat(target.blockers()).containsExactly(PREREQUISITE_EVIDENCE_REQUIRED.name());
            assertThat(target.capabilities()).containsExactlyInAnyOrderElementsOf(definition.operations().stream()
                .filter(operation -> CustomerPermissionMatrix.row(operation).targetIntents().contains(target.intent())).toList());
            assertThat(target.limits()).isEqualTo(definition.limits());
        });
        assertThat(result.blockers()).isEmpty();
        assertThat(result.workspaceProfiles()).isEqualTo(definition.workspaceProfiles());
        verify(placement, never()).probeAndRecord(any(), any());
    }

    @Test void expiryOfAllPlacementEvidenceDoesNotEmptyTheReviewedTargetSelector() {
        reviewedProfiles();
        when(placement.requireFreshEvidence(any(), any())).thenThrow(conflict(PLACEMENT_EVIDENCE_STALE));

        var result = catalogue.offer("offer");

        assertThat(result.targets()).extracting(TargetSummary::id).containsExactly("staging-target", "production-target");
        assertThat(result.targets()).allSatisfy(target -> {
            assertThat(target.readiness()).isEqualTo("PLACEMENT_RECHECK_REQUIRED");
            assertThat(target.blockers()).containsExactly(PLACEMENT_EVIDENCE_STALE.name(), PREREQUISITE_EVIDENCE_REQUIRED.name());
        });
        assertThat(result.blockers()).containsExactly(PLACEMENT_EVIDENCE_STALE.name());
        verify(placement, never()).probeAndRecord(any(), any());
    }

    @ParameterizedTest @EnumSource(value = Code.class, names = {
        "PLACEMENT_DENIED", "TRUSTED_PLACEMENT_REQUIRED", "PREFLIGHT_REQUIRED", "REVIEWED_COMPOSITION_CHANGED",
        "UNENTITLED_TARGET", "OPERATION_FORBIDDEN"
    })
    void nonrecoverablePlacementFailuresDoNotExposeTheTarget(Code failure) {
        var staging = reviewedProfiles();
        when(placement.requireFreshEvidence(staging, TargetIntent.CUSTOMER_STAGING)).thenThrow(conflict(failure));

        var result = catalogue.offer("offer");

        assertThat(result.targets()).extracting(TargetSummary::id).containsExactly("production-target");
        assertThat(result.blockers()).containsExactly(failure.name());
        verify(placement, never()).probeAndRecord(any(), any());
    }

    @ParameterizedTest @EnumSource(value = Code.class, names = {
        "REVIEWED_COMPOSITION_CHANGED", "PLACEMENT_DENIED", "PLACEMENT_EVIDENCE_REQUIRED", "PLACEMENT_EVIDENCE_STALE"
    })
    void failedProfileTrustChecksAreNotDowngradedToPlacementRechecks(Code failure) {
        var staging = reviewedProfiles();
        doThrow(conflict(failure)).when(composition).requireProfile(staging, definition.targets().getFirst());

        var result = catalogue.offer("offer");

        assertThat(result.targets()).extracting(TargetSummary::id).containsExactly("production-target");
        assertThat(result.blockers()).containsExactly(failure.name());
        verify(placement, never()).requireFreshEvidence(staging, TargetIntent.CUSTOMER_STAGING);
    }

    private DeploymentTargetProfileEntity reviewedProfiles() {
        var staging = CustomerOfferTestFixtures.profile("staging-target", "stage-server");
        var production = CustomerOfferTestFixtures.profile("production-target", "prod-server");
        when(profiles.findById(staging.getId())).thenReturn(Optional.of(staging));
        when(profiles.findById(production.getId())).thenReturn(Optional.of(production));
        Instant checkedAt = Instant.now().minusSeconds(1);
        when(placement.requireFreshEvidence(staging, TargetIntent.CUSTOMER_STAGING)).thenReturn(new CustomerPlacementEvidenceEntity(
            "staging-proof", staging.getId(), TargetIntent.CUSTOMER_STAGING, "staging-hash", "stage-server", "46.224.145.148",
            "destination", checkedAt, checkedAt.plusSeconds(60)));
        when(placement.requireFreshEvidence(production, TargetIntent.CUSTOMER_PRODUCTION)).thenReturn(new CustomerPlacementEvidenceEntity(
            "production-proof", production.getId(), TargetIntent.CUSTOMER_PRODUCTION, "production-hash", "prod-server", "46.225.162.106",
            "destination", checkedAt, checkedAt.plusSeconds(60)));
        return staging;
    }
}
