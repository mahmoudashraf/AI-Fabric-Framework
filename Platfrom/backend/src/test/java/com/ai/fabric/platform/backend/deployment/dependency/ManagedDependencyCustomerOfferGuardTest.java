package com.ai.fabric.platform.backend.deployment.dependency;

import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferDeploymentEntity;
import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferEntitlementEntity;
import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferEntity;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.AuthorizationRequest;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.EntitlementStatus;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferEntitlementRepository;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferRepository;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerSelfServiceAuthorizationService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferEntitlementService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.tenant.entity.PlatformTenantEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformTenantRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ManagedDependencyCustomerOfferGuardTest {
    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");
    private final CustomerOfferDeploymentRepository associations = mock(CustomerOfferDeploymentRepository.class);
    private final CustomerOfferRepository offers = mock(CustomerOfferRepository.class);
    private final CustomerOfferEntitlementRepository entitlements = mock(CustomerOfferEntitlementRepository.class);
    private final DeploymentReleaseRepository releases = mock(DeploymentReleaseRepository.class);
    private final CustomerSelfServiceAuthorizationService authorization = mock(CustomerSelfServiceAuthorizationService.class);
    private final CustomerOfferEntitlementService entitlementEligibility = mock(CustomerOfferEntitlementService.class);
    private final PlatformTenantRepository tenants = mock(PlatformTenantRepository.class);
    private final ManagedDependencyCustomerOfferGuard guard = new ManagedDependencyCustomerOfferGuard(
        associations, offers, entitlements, releases, authorization, entitlementEligibility,
        Clock.fixed(NOW, ZoneOffset.UTC), tenants);

    @org.junit.jupiter.api.BeforeEach void activeTenant() {
        var tenant = new PlatformTenantEntity(); tenant.setId("tenant-1"); tenant.setCustomerId("customer-1"); tenant.setStatus("ACTIVE");
        when(tenants.findById("tenant-1")).thenReturn(Optional.of(tenant));
    }

    @Test void customerGrantIsBoundToExactOfferGraphReleaseAndInboxSlot() {
        var graph = graph(); var association = association(); var release = release();
        when(associations.findById("dep-1")).thenReturn(Optional.of(association));
        when(releases.findById("rel-1")).thenReturn(Optional.of(release));

        guard.authorizeGrant(graph, Operation.INBOX_OPERATOR_DELIVER, "deliver-1", "inbox");

        var request = ArgumentCaptor.forClass(AuthorizationRequest.class);
        verify(authorization).authorize(eq("customer-1"), request.capture());
        assertThat(request.getValue()).satisfies(value -> {
            assertThat(value.offerId()).isEqualTo("offer-1");
            assertThat(value.deploymentId()).isEqualTo("dep-1");
            assertThat(value.tenantId()).isEqualTo("tenant-1");
            assertThat(value.targetProfileId()).isEqualTo("target-1");
            assertThat(value.releaseId()).isEqualTo("rel-1");
            assertThat(value.operation()).isEqualTo(Operation.INBOX_OPERATOR_DELIVER);
            assertThat(value.bindingSlotId()).isEqualTo("inbox");
        });
    }

    @Test void invitationRevocationExpiryAndRevisionDoNotExpireAnOwnedDeployment() {
        var graph = graph();
        when(associations.findById("dep-1")).thenReturn(Optional.of(association()));
        when(releases.findById("rel-1")).thenReturn(Optional.of(release()));
        when(offers.findById("offer-1")).thenReturn(Optional.of(
            new CustomerOfferEntity("offer-1", "{}", "fingerprint-1", "review", "reviewer", NOW)));
        var entitlement = new CustomerOfferEntitlementEntity();
        entitlement.setCustomerId("customer-1"); entitlement.setOfferId("offer-1");
        entitlement.setStatus(EntitlementStatus.REVOKED); entitlement.setExpiresAt(NOW.plusSeconds(60));
        entitlement.setOwnerUserId("owner-1"); entitlement.setRevision(100);
        entitlement.setExpiresAt(Instant.EPOCH);
        when(entitlements.findForUpdate("customer-1", "offer-1"))
            .thenReturn(Optional.of(entitlement));

        guard.requireActiveExecution(graph);
        verify(entitlementEligibility).requireActiveCustomer("customer-1");
        verify(entitlementEligibility, never()).requireCurrentOwnerEligibility(anyString(), anyString());
        verifyNoInteractions(offers, entitlements);
    }

    @Test void inactiveCurrentCustomerAccountBlocksWorkerExecution() {
        var graph = graph();
        when(associations.findById("dep-1")).thenReturn(Optional.of(association()));
        when(releases.findById("rel-1")).thenReturn(Optional.of(release()));
        when(offers.findById("offer-1")).thenReturn(Optional.of(
            new CustomerOfferEntity("offer-1", "{}", "fingerprint-1", "review", "reviewer", NOW)));
        var entitlement = new CustomerOfferEntitlementEntity();
        entitlement.setCustomerId("customer-1"); entitlement.setOfferId("offer-1");
        entitlement.setOwnerUserId("owner-1"); entitlement.setRevision(7);
        entitlement.setStatus(EntitlementStatus.ACTIVE); entitlement.setExpiresAt(NOW.plusSeconds(60));
        when(entitlements.findForUpdate("customer-1", "offer-1")).thenReturn(Optional.of(entitlement));
        doThrow(new IllegalStateException("owner suspended")).when(entitlementEligibility)
            .requireActiveCustomer("customer-1");

        assertThatThrownBy(() -> guard.requireActiveExecution(graph))
            .hasMessageContaining("owner suspended");
    }

    @Test void foreignGraphScopeIsRejectedBeforeOfferAuthorization() {
        var graph = graph(); graph.setTenantId("other-tenant");
        when(associations.findById("dep-1")).thenReturn(Optional.of(association()));

        assertThatThrownBy(() -> guard.authorizeGrant(graph, Operation.INBOX_OPERATOR_ENROLL, "enroll-1", "inbox"))
            .hasMessageContaining("DEPENDENCY_CUSTOMER_SCOPE_CHANGED");
        verifyNoInteractions(authorization);
    }

    @Test void ordinaryDeploymentWithoutOfferAssociationIsUnaffected() {
        when(associations.findById("dep-1")).thenReturn(Optional.empty());
        guard.authorizeGrant(graph(), Operation.INBOX_OPERATOR_ENROLL, "enroll-1", "inbox");
        guard.requireActiveExecution(graph());
        verifyNoInteractions(authorization, offers, entitlements, releases, entitlementEligibility);
    }

    private ManagedDependencyGraphEntity graph() {
        var graph = new ManagedDependencyGraphEntity();
        graph.setId("graph-1"); graph.setDeploymentId("dep-1"); graph.setCustomerId("customer-1");
        graph.setTenantId("tenant-1"); graph.setTargetProfileId("target-1");
        graph.setVersionId("version-1"); graph.setReleaseId("rel-1");
        return graph;
    }

    private CustomerOfferDeploymentEntity association() {
        return new CustomerOfferDeploymentEntity("dep-1", "customer-1", "tenant-1", "offer-1",
            "target-1", "fingerprint-1", 7, "bootstrap-1", NOW);
    }

    private DeploymentReleaseEntity release() {
        var release = new DeploymentReleaseEntity();
        release.setId("rel-1"); release.setDeploymentId("dep-1");
        release.setTargetProfileId("target-1"); release.setDeploymentVersionId("version-1");
        return release;
    }
}
