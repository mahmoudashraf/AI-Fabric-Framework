package com.ai.fabric.platform.backend.customerselfservice;

import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferEntity;
import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferEntitlementEntity;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferEntitlementRepository;
import com.ai.fabric.platform.backend.customerselfservice.service.*;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.*;
import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerSelfServiceCatalogueServiceTest {
    private final CustomerOfferEntitlementService access = mock(CustomerOfferEntitlementService.class);
    private final CustomerReviewedCompositionService composition = mock(CustomerReviewedCompositionService.class);
    private final DeploymentTargetProfileRepository profiles = mock(DeploymentTargetProfileRepository.class);
    private final CustomerSelfServiceCatalogueService catalogue = new CustomerSelfServiceCatalogueService(access,
        mock(CustomerOfferEntitlementRepository.class), profiles, composition, mock(CustomerTargetPlacementService.class));
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
        assertThat(result.blockers()).contains(UNENTITLED_TARGET.name());
        verify(access).requireEntitlement("customer", "offer", "owner", false);
    }

    @Test void failedCompositionValidationDoesNotExposeProfilePayloads() {
        doThrow(conflict(REVIEWED_COMPOSITION_CHANGED)).when(composition).validate(definition);
        var result = catalogue.offer("offer");
        assertThat(result.workspaceProfiles()).isEmpty();
        assertThat(result.blockers()).contains(REVIEWED_COMPOSITION_CHANGED.name());
    }

    @Test void missingOwnerEntitlementDeniesBeforeConfigurationIsRead() {
        when(access.requireEntitlement("customer", "offer", "owner", false)).thenThrow(deny(ENTITLEMENT_REQUIRED));
        assertThatThrownBy(() -> catalogue.offer("offer")).hasMessageContaining(ENTITLEMENT_REQUIRED.name());
        verify(access, never()).requireOffer("offer");
        verifyNoInteractions(composition, profiles);
    }
}
