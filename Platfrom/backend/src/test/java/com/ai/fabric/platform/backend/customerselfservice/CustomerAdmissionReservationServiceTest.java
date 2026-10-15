package com.ai.fabric.platform.backend.customerselfservice;

import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferDeploymentEntity;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerAdmissionReservationContracts.Request;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerAdmissionReservationService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferEntitlementService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerSelfServiceAuthorizationService;
import com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.Reservation;
import com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.Reserve;
import com.ai.fabric.platform.backend.deployment.admission.DeploymentAdmissionService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Instant;
import java.util.Optional;

import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.RESOURCE_SCOPE_MISMATCH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CustomerAdmissionReservationServiceTest {
    private final CustomerOfferEntitlementService entitlements = mock(CustomerOfferEntitlementService.class);
    private final CustomerSelfServiceAuthorizationService authorization = mock(CustomerSelfServiceAuthorizationService.class);
    private final DeploymentRepository deployments = mock(DeploymentRepository.class);
    private final DeploymentVersionRepository versions = mock(DeploymentVersionRepository.class);
    private final DeploymentReleaseRepository releases = mock(DeploymentReleaseRepository.class);
    private final CustomerOfferDeploymentRepository associations = mock(CustomerOfferDeploymentRepository.class);
    private final DeploymentAdmissionService admissions = mock(DeploymentAdmissionService.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private CustomerAdmissionReservationService service;
    private DeploymentEntity deployment;
    private Authorization prepared;

    @BeforeEach
    void setup() {
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new CustomerAdmissionReservationService(entitlements, authorization, deployments, versions, releases,
            associations, admissions, transactions);
        when(entitlements.requireCustomerActor()).thenReturn(new CustomerOfferEntitlementService.CustomerActor("customer", "owner"));
        deployment = new DeploymentEntity();
        deployment.setId("deployment"); deployment.setCustomerId("customer"); deployment.setTenantId("tenant");
        when(deployments.findById("deployment")).thenReturn(Optional.of(deployment));
        var context = new AdmissionContext("customer", "tenant", "deployment", null, "staging-target",
            TargetIntent.CUSTOMER_STAGING, "offer", "offer-fingerprint", 2, "placement-proof",
            Operation.APPLY, "operation", CustomerOfferTestFixtures.definition().limits(),
            CustomerOfferTestFixtures.definition().topology());
        prepared = new Authorization(context, CustomerOfferTestFixtures.definition());
        when(authorization.prepareAdmission(eq("customer"), any())).thenReturn(prepared);
        var version = new DeploymentVersionEntity();
        version.setId("version"); version.setDeploymentId("deployment"); version.setStatus("PUBLISHED");
        when(versions.findById("version")).thenReturn(Optional.of(version));
        Reservation reservation = mock(Reservation.class);
        when(reservation.id()).thenReturn("reservation");
        when(reservation.deploymentId()).thenReturn("deployment");
        when(reservation.targetProfileId()).thenReturn("staging-target");
        when(reservation.intent()).thenReturn(com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.Intent.CUSTOMER_STAGING);
        when(reservation.customerOperation()).thenReturn(Operation.APPLY);
        when(reservation.state()).thenReturn(com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.State.RESERVED);
        when(reservation.revision()).thenReturn(0L);
        when(reservation.expiresAt()).thenReturn(Instant.parse("2026-10-16T00:00:00Z"));
        when(admissions.reserveCustomer(eq("deployment"), eq("version"), any())).thenReturn(reservation);
        when(admissions.status("deployment", "reservation")).thenReturn(reservation);
        when(associations.findById("deployment")).thenReturn(Optional.of(new CustomerOfferDeploymentEntity(
            "deployment", "customer", "tenant", "offer", "offer-fingerprint", 2, "bootstrap", Instant.now())));
    }

    @Test
    void preparesInAShortTransactionThenReservesTheExactCustomerContext() {
        var result = service.reserve("offer", "deployment", request());

        assertThat(result.id()).isEqualTo("reservation");
        assertThat(result.intent()).isEqualTo(TargetIntent.CUSTOMER_STAGING);
        ArgumentCaptor<AuthorizationRequest> authorizationRequest = ArgumentCaptor.forClass(AuthorizationRequest.class);
        verify(authorization).prepareAdmission(eq("customer"), authorizationRequest.capture());
        assertThat(authorizationRequest.getValue().tenantId()).isEqualTo("tenant");
        ArgumentCaptor<Reserve> reserve = ArgumentCaptor.forClass(Reserve.class);
        verify(admissions).reserveCustomer(eq("deployment"), eq("version"), reserve.capture());
        assertThat(reserve.getValue().customerContext()).isEqualTo(prepared.context());
        verify(transactions).commit(any());
    }

    @Test
    void foreignVersionOrReleaseCannotCreateCapacity() {
        versions.findById("version").orElseThrow().setDeploymentId("other");
        assertThatThrownBy(() -> service.reserve("offer", "deployment", request()))
            .isInstanceOfSatisfying(CustomerSelfServiceException.class,
                error -> assertThat(error.code()).isEqualTo(RESOURCE_SCOPE_MISMATCH));
        verifyNoInteractions(admissions);

        versions.findById("version").orElseThrow().setDeploymentId("deployment");
        var release = new DeploymentReleaseEntity();
        release.setId("release"); release.setDeploymentId("deployment"); release.setDeploymentVersionId("other-version");
        release.setTargetProfileId("staging-target");
        when(releases.findById("release")).thenReturn(Optional.of(release));
        var withRelease = new Request("staging-target", "manifest", "version", "release", Operation.APPLY, "operation");
        assertThatThrownBy(() -> service.reserve("offer", "deployment", withRelease))
            .isInstanceOfSatisfying(CustomerSelfServiceException.class,
                error -> assertThat(error.code()).isEqualTo(RESOURCE_SCOPE_MISMATCH));
        verifyNoInteractions(admissions);
    }

    @Test
    void statusRequiresTheOwnedOfferAssociationAndReturnsOnlySafeFields() {
        assertThat(service.status("offer", "deployment", "reservation").state()).isEqualTo("RESERVED");
        when(associations.findById("deployment")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.status("offer", "deployment", "reservation"))
            .isInstanceOfSatisfying(CustomerSelfServiceException.class,
                error -> assertThat(error.code()).isEqualTo(RESOURCE_SCOPE_MISMATCH));
    }

    @Test
    void statusDoesNotExposeAStaffOriginReservationThroughTheCustomerRoute() {
        when(admissions.status("deployment", "reservation").customerOperation()).thenReturn(null);

        assertThatThrownBy(() -> service.status("offer", "deployment", "reservation"))
            .isInstanceOfSatisfying(CustomerSelfServiceException.class,
                error -> assertThat(error.code()).isEqualTo(RESOURCE_SCOPE_MISMATCH));
    }

    private Request request() {
        return new Request("staging-target", "manifest", "version", null, Operation.APPLY, "operation");
    }
}
