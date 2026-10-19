package com.ai.fabric.platform.backend.customerselfservice;

import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferDeploymentEntity;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerAdmissionReservationContracts.Request;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerAdmissionReservationContracts.RefreshRequest;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerAdmissionReservationService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferEntitlementService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerSelfServiceAuthorizationService;
import com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.Reservation;
import com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.Reserve;
import com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.Manifest;
import com.ai.fabric.platform.backend.deployment.admission.CustomerAdmissionManifestService;
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
import java.util.List;

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
    private final CustomerAdmissionManifestService manifests = mock(CustomerAdmissionManifestService.class);
    private final DeploymentAdmissionService admissions = mock(DeploymentAdmissionService.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private CustomerAdmissionReservationService service;
    private DeploymentEntity deployment;
    private Authorization prepared;

    @BeforeEach
    void setup() {
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new CustomerAdmissionReservationService(entitlements, authorization, deployments, versions, releases,
            associations, manifests, admissions, transactions);
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
        version.setId("version"); version.setDeploymentId("deployment"); version.setStatus("PUBLISHED"); version.setConfigHash("hash");
        when(versions.findById("version")).thenReturn(Optional.of(version));
        when(manifests.prepare(deployment, version, prepared)).thenReturn(new Manifest(
            "manifest", "deployment", "version", "hash", "staging-target", "binding", "topology",
            mock(com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.Vector.class),
            java.util.Set.of("reviewed")));
        Reservation reservation = mock(Reservation.class);
        when(reservation.id()).thenReturn("reservation");
        when(reservation.deploymentId()).thenReturn("deployment");
        when(reservation.targetProfileId()).thenReturn("staging-target");
        when(reservation.intent()).thenReturn(com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.Intent.CUSTOMER_STAGING);
        when(reservation.customerOperation()).thenReturn(Operation.APPLY);
        when(reservation.state()).thenReturn(com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.State.RESERVED);
        when(reservation.revision()).thenReturn(0L);
        when(reservation.expiresAt()).thenReturn(Instant.parse("2026-10-16T00:00:00Z"));
        when(reservation.expiryState()).thenReturn(
            com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.ExpiryState.CURRENT);
        when(reservation.keepUntil()).thenReturn(null);
        when(reservation.updatedAt()).thenReturn(Instant.parse("2026-10-15T23:00:00Z"));
        when(admissions.reserveCustomer(eq("deployment"), eq("version"), any())).thenReturn(reservation);
        when(admissions.status("deployment", "reservation")).thenReturn(reservation);
        when(admissions.activeCustomerReservations("deployment", "staging-target")).thenReturn(List.of(reservation));
        when(associations.findById("deployment")).thenReturn(Optional.of(new CustomerOfferDeploymentEntity(
            "deployment", "customer", "tenant", "offer", "staging-target", "offer-fingerprint", 2, "bootstrap", Instant.now())));
    }

    @Test
    void preparesInAShortTransactionThenReservesTheExactCustomerContext() {
        var result = service.reserve("offer", "deployment", request());

        assertThat(result.id()).isEqualTo("reservation");
        assertThat(result.intent()).isEqualTo(TargetIntent.CUSTOMER_STAGING);
        assertThat(result.expiryState()).isEqualTo("CURRENT");
        assertThat(result.keepUntil()).isNull();
        ArgumentCaptor<AuthorizationRequest> authorizationRequest = ArgumentCaptor.forClass(AuthorizationRequest.class);
        verify(authorization).prepareAdmission(eq("customer"), authorizationRequest.capture());
        var reviewedVersion = versions.findById("version").orElseThrow();
        verify(authorization).requireReviewedVersion(eq(prepared), same(reviewedVersion));
        assertThat(authorizationRequest.getValue().tenantId()).isEqualTo("tenant");
        ArgumentCaptor<Reserve> reserve = ArgumentCaptor.forClass(Reserve.class);
        verify(admissions).reserveCustomer(eq("deployment"), eq("version"), reserve.capture());
        assertThat(reserve.getValue().manifestId()).isEqualTo("manifest");
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
        var withRelease = new Request("staging-target", "version", "release", Operation.APPLY, "operation");
        assertThatThrownBy(() -> service.reserve("offer", "deployment", withRelease))
            .isInstanceOfSatisfying(CustomerSelfServiceException.class,
                error -> assertThat(error.code()).isEqualTo(RESOURCE_SCOPE_MISMATCH));
        verifyNoInteractions(admissions);
    }

    @Test
    void statusRequiresTheOwnedOfferAssociationAndReturnsOnlySafeFields() {
        assertThat(service.status("offer", "deployment", "reservation").state()).isEqualTo("RESERVED");
        verifyReadAuthorization("staging-target");
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

    @Test
    void listsOnlyTheOwnedActiveCustomerReservationProjection() {
        var result = service.list("offer", "deployment", "staging-target");

        assertThat(result).singleElement().satisfies(summary -> {
            assertThat(summary.id()).isEqualTo("reservation");
            assertThat(summary.targetProfileId()).isEqualTo("staging-target");
        });
        verifyReadAuthorization("staging-target");
        verify(admissions).activeCustomerReservations("deployment", "staging-target");
    }

    @Test
    void refreshesTheOriginalApplyEvidenceAfterAuthorizationTransactionEnds() {
        var reservation = admissions.status("deployment", "reservation");
        when(admissions.refreshCustomerEvidence(eq("deployment"), eq("version"), eq("reservation"), eq(0L), any()))
            .thenReturn(reservation);

        assertThat(service.refreshEvidence("offer", "deployment", "reservation", refreshRequest(0)).id())
            .isEqualTo("reservation");

        var order = inOrder(transactions, authorization, manifests, admissions);
        order.verify(authorization).prepareAdmission(eq("customer"), any());
        order.verify(authorization).requireReviewedVersion(eq(prepared), any());
        order.verify(transactions).commit(any());
        order.verify(manifests).prepare(eq(deployment), any(), eq(prepared));
        ArgumentCaptor<Reserve> command = ArgumentCaptor.forClass(Reserve.class);
        order.verify(admissions).refreshCustomerEvidence(eq("deployment"), eq("version"), eq("reservation"), eq(0L), command.capture());
        assertThat(command.getValue().operationKey()).isEqualTo("operation");
        assertThat(command.getValue().customerContext()).isEqualTo(prepared.context());
        assertThat(command.getValue().allocationId()).isNull();
        verify(admissions, never()).reserveCustomer(anyString(), anyString(), any());
    }

    @Test
    void evidenceRefreshRejectsOtherOperationsNegativeFencesAndForeignVersions() {
        assertThatThrownBy(() -> service.refreshEvidence("offer", "deployment", "reservation", refreshRequest(-1)))
            .isInstanceOf(CustomerSelfServiceException.class);
        assertThatThrownBy(() -> service.refreshEvidence("offer", "deployment", "reservation",
            new RefreshRequest("staging-target", "version", Operation.READ, "operation", 0)))
            .isInstanceOf(CustomerSelfServiceException.class);
        verifyNoInteractions(admissions);

        versions.findById("version").orElseThrow().setDeploymentId("foreign");
        assertThatThrownBy(() -> service.refreshEvidence("offer", "deployment", "reservation", refreshRequest(0)))
            .isInstanceOfSatisfying(CustomerSelfServiceException.class,
                error -> assertThat(error.code()).isEqualTo(RESOURCE_SCOPE_MISMATCH));
        verifyNoInteractions(admissions);
    }

    @Test
    void evidenceRefreshCannotReachAdmissionWhenOwnerAuthorizationFails() {
        when(authorization.prepareAdmission(eq("customer"), any()))
            .thenThrow(CustomerSelfServiceException.deny(RESOURCE_SCOPE_MISMATCH));
        assertThatThrownBy(() -> service.refreshEvidence("offer", "deployment", "reservation", refreshRequest(0)))
            .isInstanceOf(CustomerSelfServiceException.class);
        verifyNoInteractions(admissions, manifests);
    }

    private RefreshRequest refreshRequest(long revision) {
        return new RefreshRequest("staging-target", "version", Operation.APPLY, "operation", revision);
    }

    private void verifyReadAuthorization(String targetProfileId) {
        ArgumentCaptor<AuthorizationRequest> request = ArgumentCaptor.forClass(AuthorizationRequest.class);
        verify(authorization, atLeastOnce()).authorize(eq("customer"), request.capture());
        assertThat(request.getAllValues()).anySatisfy(value -> {
            assertThat(value.offerId()).isEqualTo("offer");
            assertThat(value.deploymentId()).isEqualTo("deployment");
            assertThat(value.tenantId()).isEqualTo("tenant");
            assertThat(value.targetProfileId()).isEqualTo(targetProfileId);
            assertThat(value.operation()).isEqualTo(Operation.READ);
        });
        verify(transactions, atLeastOnce()).commit(any());
    }

    private Request request() {
        return new Request("staging-target", "version", null, Operation.APPLY, "operation");
    }
}
