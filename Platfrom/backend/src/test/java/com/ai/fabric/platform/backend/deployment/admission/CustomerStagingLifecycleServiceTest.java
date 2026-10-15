package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerAdmissionReservationContracts.ExtensionRequest;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;
import com.ai.fabric.platform.backend.customerselfservice.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneOffset;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerStagingLifecycleServiceTest extends AdmissionFixture {
    private CustomerOfferEntitlementService entitlements;
    private CustomerSelfServiceAuthorizationService authorization;
    private DeploymentAdmissionService admissions;
    private CustomerStagingLifecycleService service;
    private Reservation allocation;

    @BeforeEach
    void owner() {
        allocation = one();
        entitlements = mock(CustomerOfferEntitlementService.class); authorization = mock(CustomerSelfServiceAuthorizationService.class);
        admissions = mock(DeploymentAdmissionService.class);
        when(entitlements.requireCustomerActor()).thenReturn(new CustomerOfferEntitlementService.CustomerActor("customer", "owner"));
        when(admissions.status("deployment1", allocation.id())).thenAnswer(call -> store.require(allocation.id()));
        var limits = new ReviewedLimits("measurement-proof", 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1);
        var topology = new ReviewedTopology(1, 1, 1, 1, 1, 1, 1, 1);
        var definition = mock(Definition.class); when(definition.limits()).thenReturn(limits);
        when(definition.topology()).thenReturn(topology);
        when(authorization.prepareAdmission(eq("customer"), any())).thenAnswer(call -> {
            AuthorizationRequest request = call.getArgument(1);
            var scope = new com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.AdmissionContext(
                "customer", "tenant", "deployment1", request.releaseId(), TARGET, TargetIntent.CUSTOMER_STAGING,
                "offer", "offer-fingerprint", 1, "placement-proof", request.operation(), request.operationKey(), limits, topology);
            return new Authorization(scope, definition);
        });
        when(admissions.reserveCustomer(eq("deployment1"), eq("version"), any())).thenAnswer(call -> {
            Reserve command = call.getArgument(2); var c = context("manifest1");
            return store.reserve(c, command, evidence(c, NOW), "owner", NOW);
        });
        service = new CustomerStagingLifecycleService(entitlements, authorization, admissions, store, json,
            new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource),
            Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void ownerCanExtendAndReplayWithoutAnotherExtensionOrGlobalGrant() {
        var request = request(false);
        var extended = service.extend("offer", "deployment1", request);
        assertThat(service.extend("offer", "deployment1", request)).isEqualTo(extended);
        assertThat(store.require(allocation.id()).extensions()).isEqualTo(1);
        verify(authorization).authorize(eq("customer"), argThat(x -> x.operation() == Operation.STAGING_EXTEND));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_admission_extension_commands", Long.class)).isEqualTo(1);
    }

    @Test
    void foreignCustomerStaleRevisionAndChangedReplayAreRejected() {
        when(entitlements.requireCustomerActor()).thenReturn(new CustomerOfferEntitlementService.CustomerActor("foreign", "owner"));
        assertThatThrownBy(() -> service.extend("offer", "deployment1", request(false))).hasMessageContaining("INPUT_INVALID");
        verifyNoInteractions(authorization);
        when(entitlements.requireCustomerActor()).thenReturn(new CustomerOfferEntitlementService.CustomerActor("customer", "owner"));
        var request = request(false);
        service.extend("offer", "deployment1", request);
        assertThatThrownBy(() -> service.extend("offer", "deployment1", request(true))).hasMessageContaining("OPERATION_KEY_REUSED");
        assertThatThrownBy(() -> service.extend("offer", "deployment1", new ExtensionRequest(TARGET, allocation.id(), 0,
            "different-key", "Extend preview", false))).hasMessageContaining("STALE_RESERVATION_FENCE");
    }

    @Test
    void retainRequiresCurrentGrantEntitlement() {
        var grant = grant(1);
        store.approveGrant(new CustomerGrant(grant.customerId(), grant.fleetLimit(), grant.targetLimits(), grant.validUntil(),
            grant.ownerContactEvidenceReference(), grant.payerReference(), null), "staff", NOW);
        assertThatThrownBy(() -> service.extend("offer", "deployment1", request(true)))
            .hasMessageContaining("KEEP_STAGING_ENTITLEMENT_REQUIRED");
        assertThat(store.require(allocation.id()).extensions()).isZero();
    }

    private ExtensionRequest request(boolean keep) {
        return new ExtensionRequest(TARGET, allocation.id(), allocation.revision(), "extend", "Continue customer preview", keep);
    }
}
