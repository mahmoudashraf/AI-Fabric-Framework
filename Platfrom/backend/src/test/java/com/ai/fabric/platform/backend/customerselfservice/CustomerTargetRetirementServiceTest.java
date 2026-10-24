package com.ai.fabric.platform.backend.customerselfservice;

import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferDeploymentEntity;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerTargetRetirementContracts.*;
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
import java.util.List;
import java.util.Optional;

import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CustomerTargetRetirementServiceTest {
    private final CustomerOfferEntitlementService entitlements = mock(CustomerOfferEntitlementService.class);
    private final CustomerSelfServiceAuthorizationService authorization = mock(CustomerSelfServiceAuthorizationService.class);
    private final CustomerOfferDeploymentRepository associations = mock(CustomerOfferDeploymentRepository.class);
    private final DeploymentRepository deployments = mock(DeploymentRepository.class);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final CustomerOfferCodec codec = new CustomerOfferCodec(mapper);
    private final DeploymentEntity deployment = new DeploymentEntity();
    private JdbcTemplate jdbc;
    private CustomerTargetRetirementService service;

    @BeforeEach void setup() {
        jdbc = CustomerLegacyOperationsHistoryFixtures.history(mapper);
        service = new CustomerTargetRetirementService(entitlements, authorization, associations, deployments, jdbc, codec);
        deployment.setId("deployment"); deployment.setCustomerId("customer"); deployment.setTenantId("tenant");
        when(deployments.findById("deployment")).thenReturn(Optional.of(deployment));
        when(entitlements.requireCustomerActor()).thenReturn(new CustomerOfferEntitlementService.CustomerActor("customer", "owner"));
        when(associations.findById("deployment")).thenReturn(Optional.of(new CustomerOfferDeploymentEntity(
            "deployment", "customer", "tenant", "offer", "staging-target", "offer-fp", 1, "bootstrap", Instant.now())));
    }

    @Test void historyRevalidatesTargetAndEveryFrozenReleaseAndNeverAdvertisesCustomerCommands() {
        var before = jdbc.queryForList("SELECT * FROM platform_target_retirement_operations");
        var summary = service.status("offer", "deployment", "retirement");
        assertThat(summary.customerCommandsAllowed()).isFalse();
        assertThat(summary.state()).isEqualTo("RETRYABLE_FAILURE");
        assertThat(summary.blockers()).containsExactly("DRAIN_PENDING");
        assertThat(service.list("offer", "deployment", "staging-target")).containsExactly(summary);
        assertThat(codec.write(summary)).doesNotContain("PRIVATE_HISTORY", "providerSecret", "resources", "consumers", "request_json");
        assertThat(jdbc.queryForList("SELECT * FROM platform_target_retirement_operations")).isEqualTo(before);
        verify(authorization, times(2)).authorizeOperationalRead(eq("customer"), argThat(value ->
            value.operation() == Operation.READ && "release".equals(value.releaseId())
                && "staging-target".equals(value.targetProfileId()) && "tenant".equals(value.tenantId())));
        verify(authorization, never()).authorize(anyString(), any());
    }

    @Test void allMutationRoutesAreGoneAndPreserveFailureHistoryRatherThanPretendingToRetire() {
        var before = jdbc.queryForList("SELECT * FROM platform_target_retirement_operations");
        assertGone(() -> service.plan("offer", "deployment", plan()));
        var command = new Command("retire-fingerprint");
        assertGone(() -> service.execute("offer", "deployment", "retirement", command));
        assertGone(() -> service.retry("offer", "deployment", "retirement", command));
        assertGone(() -> service.cancel("offer", "deployment", "retirement", command));
        assertThat(jdbc.queryForList("SELECT * FROM platform_target_retirement_operations")).isEqualTo(before);
    }

    @ParameterizedTest
    @EnumSource(value = CustomerSelfServiceException.Code.class,
        names = {"ENTITLEMENT_REVOKED", "INVITE_OWNER_MISMATCH", "UNENTITLED_TARGET", "RESOURCE_SCOPE_MISMATCH"})
    void targetAndEntitlementDenialsRemainEffectiveForHistoryAndRetiredCommands(CustomerSelfServiceException.Code code) {
        doThrow(CustomerSelfServiceException.deny(code)).when(authorization).authorizeOwnedTargetRead(anyString(), any());
        assertCode(code, () -> service.list("offer", "deployment", "staging-target"));
        assertCode(code, () -> service.status("offer", "deployment", "retirement"));
        assertCode(code, () -> service.plan("offer", "deployment", plan()));
        assertCode(code, () -> service.execute("offer", "deployment", "retirement", new Command("retire-fingerprint")));
    }

    @Test void foreignMissingAndArchivedDeploymentsAndForeignOfferAssociationsFailClosed() {
        deployment.setCustomerId("foreign");
        assertCode(RESOURCE_SCOPE_MISMATCH, () -> service.list("offer", "deployment", "staging-target"));
        assertCode(RESOURCE_SCOPE_MISMATCH, () -> service.plan("offer", "deployment", plan()));
        deployment.setCustomerId("customer"); deployment.setArchivedAt(Instant.now());
        assertCode(RESOURCE_SCOPE_MISMATCH, () -> service.status("offer", "deployment", "retirement"));
        deployment.setArchivedAt(null);
        assertCode(RESOURCE_SCOPE_MISMATCH, () -> service.status("other-offer", "deployment", "retirement"));
        when(associations.findById("deployment")).thenReturn(Optional.empty());
        assertCode(RESOURCE_SCOPE_MISMATCH, () -> service.status("offer", "deployment", "retirement"));
        when(deployments.findById("deployment")).thenReturn(Optional.empty());
        assertCode(RESOURCE_SCOPE_MISMATCH, () -> service.status("offer", "deployment", "retirement"));
        verifyNoInteractions(authorization);
    }

    @Test void anonymousCustomerAndForeignRowsCannotReadOrDriveHistory() {
        jdbc.update("UPDATE platform_target_retirement_operations SET customer_id='foreign'");
        assertCode(RESOURCE_SCOPE_MISMATCH, () -> service.status("offer", "deployment", "retirement"));
        assertThat(service.list("offer", "deployment", "staging-target")).isEmpty();
        when(entitlements.requireCustomerActor()).thenThrow(CustomerSelfServiceException.deny(ACTIVE_CUSTOMER_SCOPE_REQUIRED));
        assertCode(ACTIVE_CUSTOMER_SCOPE_REQUIRED, () -> service.status("offer", "deployment", "retirement"));
    }

    @Test void missingForeignOrWrongTargetFrozenReleaseIsDeniedByTheRealAuthorizationBoundary() {
        doThrow(CustomerSelfServiceException.deny(RESOURCE_SCOPE_MISMATCH))
            .when(authorization).authorizeOperationalRead(anyString(), any());
        assertCode(RESOURCE_SCOPE_MISMATCH, () -> service.status("offer", "deployment", "retirement"));
        assertCode(RESOURCE_SCOPE_MISMATCH, () -> service.list("offer", "deployment", "staging-target"));
        assertCode(RESOURCE_SCOPE_MISMATCH, () -> service.retry("offer", "deployment", "retirement", new Command("retire-fingerprint")));
    }

    @Test void corruptFrozenRequestCannotBecomeAReadOrMutationAuthority() {
        jdbc.update("UPDATE platform_target_retirement_operations SET request_json='{}'");
        assertCode(RESOURCE_SCOPE_MISMATCH, () -> service.status("offer", "deployment", "retirement"));
        assertCode(RESOURCE_SCOPE_MISMATCH, () -> service.cancel("offer", "deployment", "retirement", new Command("anything")));
    }

    private PlanRequest plan() {
        return new PlanRequest("staging-target", List.of("release"), Instant.now().plusSeconds(60),
            mapper.createObjectNode().put("decision", "RECLAIM"), "retire", "Finished staging");
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
