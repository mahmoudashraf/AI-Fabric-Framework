package com.ai.fabric.platform.backend.customerselfservice;

import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferDeploymentEntity;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerAdmissionReservationContracts.*;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.customerselfservice.service.*;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CustomerAdmissionReservationServiceTest {
    private final CustomerOfferEntitlementService entitlements = mock(CustomerOfferEntitlementService.class);
    private final CustomerSelfServiceAuthorizationService authorization = mock(CustomerSelfServiceAuthorizationService.class);
    private final DeploymentRepository deployments = mock(DeploymentRepository.class);
    private final CustomerOfferDeploymentRepository associations = mock(CustomerOfferDeploymentRepository.class);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final CustomerOfferCodec codec = new CustomerOfferCodec(mapper);
    private final DeploymentEntity deployment = new DeploymentEntity();
    private JdbcTemplate jdbc;
    private CustomerAdmissionReservationService service;

    @BeforeEach void setup() {
        jdbc = CustomerLegacyOperationsHistoryFixtures.history(mapper);
        service = new CustomerAdmissionReservationService(entitlements, authorization, deployments, associations, jdbc, codec);
        deployment.setId("deployment"); deployment.setCustomerId("customer"); deployment.setTenantId("tenant");
        when(deployments.findById("deployment")).thenReturn(Optional.of(deployment));
        when(entitlements.requireCustomerActor()).thenReturn(new CustomerOfferEntitlementService.CustomerActor("customer", "owner"));
        when(associations.findById("deployment")).thenReturn(Optional.of(new CustomerOfferDeploymentEntity(
            "deployment", "customer", "tenant", "offer", "staging-target", "offer-fp", 1, "bootstrap", Instant.now())));
    }

    @Test void historyReadsAreScopedRedactedAndDoNotReinterpretUnknownOutcomesAsSuccess() {
        var before = jdbc.queryForList("SELECT * FROM deployment_capacity_reservations");
        var summary = service.status("offer", "deployment", "reservation");
        assertThat(summary.state()).isEqualTo("OUTCOME_UNKNOWN");
        assertThat(summary.chargedDemand()).isNull();
        assertThat(codec.write(summary)).doesNotContain("PRIVATE_HISTORY", "providerSecret", "generation", "manifestId");
        assertThat(service.list("offer", "deployment", "staging-target")).containsExactly(summary);
        assertThat(jdbc.queryForList("SELECT * FROM deployment_capacity_reservations")).isEqualTo(before);
        verify(authorization, times(2)).authorizeOwnedTargetRead(eq("customer"), argThat(value ->
            value.operation() == Operation.READ && value.releaseId() == null
                && "staging-target".equals(value.targetProfileId()) && "tenant".equals(value.tenantId())));
        verify(authorization, never()).authorize(anyString(), any());
    }

    @Test void reservationAndRefreshAreExplicitlyGoneAndLeaveHistoryUntouched() {
        var before = jdbc.queryForList("SELECT * FROM deployment_capacity_reservations");
        assertGone(() -> service.reserve("offer", "deployment",
            new Request("staging-target", "foreign-version", "foreign-release", Operation.APPLY, "attempt")));
        assertGone(() -> service.refreshEvidence("offer", "deployment", "reservation",
            new RefreshRequest("staging-target", "version", Operation.APPLY, "refresh", 3)));
        assertThat(jdbc.queryForList("SELECT * FROM deployment_capacity_reservations")).isEqualTo(before);
    }

    @ParameterizedTest
    @EnumSource(value = CustomerSelfServiceException.Code.class,
        names = {"ENTITLEMENT_REVOKED", "INVITE_OWNER_MISMATCH", "UNENTITLED_TARGET", "RESOURCE_SCOPE_MISMATCH"})
    void allRoutesRetainCurrentTargetAndEntitlementDenials(CustomerSelfServiceException.Code code) {
        doThrow(CustomerSelfServiceException.deny(code)).when(authorization).authorizeOwnedTargetRead(anyString(), any());
        assertCode(code, () -> service.list("offer", "deployment", "staging-target"));
        assertCode(code, () -> service.status("offer", "deployment", "reservation"));
        assertCode(code, () -> service.reserve("offer", "deployment",
            new Request("staging-target", "version", null, Operation.APPLY, "attempt")));
        assertCode(code, () -> service.refreshEvidence("offer", "deployment", "reservation", null));
    }

    @Test void foreignMissingOrArchivedDeploymentCannotReadHistoryOrObtainARetiredMutationResponse() {
        deployment.setCustomerId("foreign");
        assertCode(RESOURCE_SCOPE_MISMATCH, () -> service.status("offer", "deployment", "reservation"));
        assertCode(RESOURCE_SCOPE_MISMATCH, () -> service.list("offer", "deployment", "staging-target"));
        deployment.setCustomerId("customer"); deployment.setArchivedAt(Instant.now());
        assertCode(RESOURCE_SCOPE_MISMATCH, () -> service.status("offer", "deployment", "reservation"));
        when(deployments.findById("deployment")).thenReturn(Optional.empty());
        assertCode(RESOURCE_SCOPE_MISMATCH, () -> service.reserve("offer", "deployment",
            new Request("staging-target", "version", null, Operation.APPLY, "attempt")));
        verifyNoInteractions(authorization);
    }

    @Test void foreignOrMissingOfferAssociationAndAnonymousActorFailClosed() {
        assertCode(RESOURCE_SCOPE_MISMATCH, () -> service.status("other-offer", "deployment", "reservation"));
        when(associations.findById("deployment")).thenReturn(Optional.empty());
        assertCode(RESOURCE_SCOPE_MISMATCH, () -> service.list("offer", "deployment", "staging-target"));
        when(entitlements.requireCustomerActor()).thenThrow(CustomerSelfServiceException.deny(ACTIVE_CUSTOMER_SCOPE_REQUIRED));
        assertCode(ACTIVE_CUSTOMER_SCOPE_REQUIRED, () -> service.status("offer", "deployment", "reservation"));
        verifyNoInteractions(authorization);
    }

    @Test void foreignRowsAndTamperedPayloadScopeCannotBeProjected() {
        jdbc.update("UPDATE deployment_capacity_reservations SET customer_id='foreign'");
        assertCode(RESOURCE_SCOPE_MISMATCH, () -> service.status("offer", "deployment", "reservation"));
        assertThat(service.list("offer", "deployment", "staging-target")).isEmpty();
        jdbc.update("UPDATE deployment_capacity_reservations SET customer_id='customer'");
        String payload = jdbc.queryForObject("SELECT payload_json FROM deployment_capacity_reservations", String.class);
        var tampered = (com.fasterxml.jackson.databind.node.ObjectNode) codec.tree(payload);
        tampered.put("tenantId", "foreign");
        jdbc.update("UPDATE deployment_capacity_reservations SET payload_json=?", tampered.toString());
        assertCode(RESOURCE_SCOPE_MISMATCH, () -> service.status("offer", "deployment", "reservation"));
    }

    @Test void staffOriginHistoryWithoutACustomerOperationCannotBecomeACustomerReservation() {
        var payload = (com.fasterxml.jackson.databind.node.ObjectNode) codec.tree(
            jdbc.queryForObject("SELECT payload_json FROM deployment_capacity_reservations", String.class));
        payload.remove("customerOperation");
        jdbc.update("UPDATE deployment_capacity_reservations SET payload_json=?", payload.toString());
        assertThatThrownBy(() -> service.status("offer", "deployment", "reservation"))
            .isInstanceOfSatisfying(ResponseStatusException.class, error ->
                assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    private void assertGone(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class, error ->
            assertThat(error.getStatusCode()).isEqualTo(HttpStatus.GONE));
    }

    private void assertCode(CustomerSelfServiceException.Code code, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(CustomerSelfServiceException.class, error ->
            assertThat(error.code()).isEqualTo(code));
    }
}
