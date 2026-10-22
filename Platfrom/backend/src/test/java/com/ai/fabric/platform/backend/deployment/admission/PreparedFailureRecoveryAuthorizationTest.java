package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferEntitlementService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerSelfServiceOperationBoundary;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Optional;
import java.util.function.Consumer;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PreparedFailureRecoveryAuthorizationTest {
    @ParameterizedTest
    @EnumSource(value = Intent.class, names = {"CUSTOMER_STAGING", "CUSTOMER_PRODUCTION"})
    void requiresTheOwningTargetsApplyOperation(Intent intent) {
        var store = mock(AdmissionStore.class);
        var entitlement = mock(CustomerOfferEntitlementService.class);
        var boundary = mock(CustomerSelfServiceOperationBoundary.class);
        var deployments = mock(DeploymentRepository.class);
        var versions = mock(DeploymentVersionRepository.class);
        var reservation = mock(Reservation.class);
        when(reservation.fence()).thenReturn(new Fence("reservation", 4, "generation"));
        when(reservation.intent()).thenReturn(intent); when(reservation.targetProfileId()).thenReturn("target");
        when(reservation.tenantId()).thenReturn("tenant"); when(store.require("reservation")).thenReturn(reservation);
        when(entitlement.requireCustomerActor()).thenReturn(new CustomerOfferEntitlementService.CustomerActor("customer", "owner"));
        var deployment = new DeploymentEntity(); deployment.setId("deployment"); deployment.setCustomerId("customer"); deployment.setTenantId("tenant");
        when(deployments.findById("deployment")).thenReturn(Optional.of(deployment));
        var manifest = mock(Manifest.class); when(manifest.versionId()).thenReturn("version"); when(manifest.versionConfigHash()).thenReturn("hash");
        var context = mock(AdmissionContext.class); when(context.manifest()).thenReturn(manifest); when(store.context(reservation)).thenReturn(context);
        var version = new DeploymentVersionEntity(); version.setDeploymentId("deployment"); version.setStatus("PUBLISHED"); version.setConfigHash("hash");
        when(versions.findById("version")).thenReturn(Optional.of(version));
        var approved = mock(CustomerOfferContracts.Authorization.class, RETURNS_DEEP_STUBS);
        when(approved.context().offerId()).thenReturn("offer"); when(approved.context().customerId()).thenReturn("customer");
        var operation = intent == Intent.CUSTOMER_PRODUCTION ? CustomerOfferContracts.Operation.PROMOTION_APPLY : CustomerOfferContracts.Operation.APPLY;
        when(boundary.prepareAdmission(deployment, "target", operation, "recover")).thenReturn(approved);
        when(store.planPreparedFailureRecovery(eq("customer"), eq("deployment"), eq("reservation"), any(), any())).thenAnswer(call -> {
            Consumer<Reservation> authorize = call.getArgument(4); authorize.accept(reservation);
            return new PreparedFailureRecoveryContracts.Plan(call.getArgument(3), "synthetic-exact-proof");
        });
        var service = new PreparedFailureRecoveryService(store, entitlement, boundary, deployments, versions);
        assertThat(service.plan("offer", "deployment", "reservation", new PreparedFailureRecoveryContracts.PlanRequest("release", "recover", "Recover exact target"))).isNotNull();
        verify(boundary).prepareAdmission(deployment, "target", operation, "recover"); verify(boundary).requireVersion(approved, version);
        when(boundary.prepareAdmission(deployment, "target", operation, "recover")).thenReturn(null);
        assertThatThrownBy(() -> service.plan("offer", "deployment", "reservation", new PreparedFailureRecoveryContracts.PlanRequest("release", "recover", "Recover exact target")))
            .isInstanceOf(AdmissionFailure.class).hasMessageContaining("NOT_AUTHORIZED");
    }
}
