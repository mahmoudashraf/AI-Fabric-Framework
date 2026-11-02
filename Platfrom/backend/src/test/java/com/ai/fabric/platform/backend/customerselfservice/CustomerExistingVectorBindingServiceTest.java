package com.ai.fabric.platform.backend.customerselfservice;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.customerselfservice.service.*;
import com.ai.fabric.platform.backend.deployment.entity.*;
import com.ai.fabric.platform.backend.deployment.model.ExistingVectorBindingContracts.*;
import com.ai.fabric.platform.backend.deployment.repository.*;
import com.ai.fabric.platform.backend.deployment.service.*;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.tenant.entity.PlatformTenantEntity;
import com.ai.fabric.platform.backend.tenant.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Optional;

import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.*;
import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CustomerExistingVectorBindingServiceTest {
    private final CustomerOfferEntitlementService customers = mock(CustomerOfferEntitlementService.class);
    private final DeploymentRepository deployments = mock(DeploymentRepository.class);
    private final DeploymentTargetProfileRepository targets = mock(DeploymentTargetProfileRepository.class);
    private final PlatformTenantRepository tenants = mock(PlatformTenantRepository.class);
    private final DeploymentAccessService access = mock(DeploymentAccessService.class);
    private final CustomerTargetPlacementService placement = mock(CustomerTargetPlacementService.class);
    private final DeploymentExistingVectorBindingService bindings = mock(DeploymentExistingVectorBindingService.class);
    private final CustomerOfferDeploymentRepository associations = mock(CustomerOfferDeploymentRepository.class);
    private final CustomerOfferCodec codec = CustomerOfferTestFixtures.codec();
    private final DeploymentEntity deployment = new DeploymentEntity();
    private CustomerExistingVectorBindingService service;

    @BeforeEach void setup() {
        CustomerOfferTestFixtures.authenticate("owner@test.invalid", PlatformRole.CUSTOMER_ADMIN, "SESSION");
        when(customers.requireCustomerActor()).thenReturn(new CustomerOfferEntitlementService.CustomerActor("customer", "owner"));
        deployment.setId("deployment"); deployment.setCustomerId("customer"); deployment.setTenantId("tenant"); deployment.setStatus("ACTIVE");
        when(deployments.findById("deployment")).thenReturn(Optional.of(deployment));
        when(deployments.findByIdForUpdate("deployment")).thenReturn(Optional.of(deployment));
        var tenant = new PlatformTenantEntity(); tenant.setId("tenant"); tenant.setCustomerId("customer"); tenant.setStatus("ACTIVE");
        when(tenants.findById("tenant")).thenReturn(Optional.of(tenant));
        for (String id : java.util.List.of("staging", "production")) {
            var target = new DeploymentTargetProfileEntity(); target.setId(id); target.setActive(true); target.setEnvironmentName(id);
            when(targets.findById(id)).thenReturn(Optional.of(target));
        }
        var authorization = new CustomerSelfServiceAuthorizationService(customers, mock(CustomerReviewedCompositionService.class),
            placement, deployments, mock(DeploymentReleaseRepository.class), mock(DeploymentVersionRepository.class), targets,
            tenants, mock(PlatformConsumerRepository.class), access, associations, mock(DeploymentDocumentBindingResolver.class));
        var boundary = new CustomerSelfServiceOperationBoundary(associations, deployments, authorization, customers, codec);
        service = new CustomerExistingVectorBindingService(customers, deployments, boundary, bindings, codec);
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @ParameterizedTest @EnumSource(AuthenticationType.class)
    void ownedCustomerDelegatesExactTypedCredentialsAndServerApprovalToExistingProbes(AuthenticationType auth) {
        var input = request(auth, null);
        var summary = summary("staging");
        when(bindings.bind(eq("deployment"), eq("staging"), any())).thenReturn(summary);
        assertThat(service.bind("deployment", "staging", input)).isSameAs(summary);
        var captured = ArgumentCaptor.forClass(Request.class);
        verify(bindings).bind(eq("deployment"), eq("staging"), captured.capture());
        assertThat(captured.getValue().versionId()).isEqualTo(input.versionId());
        assertThat(captured.getValue().authType()).isEqualTo(auth);
        assertThat(captured.getValue().token()).isEqualTo(input.token());
        assertThat(captured.getValue().password()).isEqualTo(input.password());
        assertThat(captured.getValue().approvalReference()).startsWith("customer-existing-vector:");
        assertThat(codec.write(summary)).doesNotContain("synthetic-serving", "control-plane");
        verify(access).requireDeploymentAdminAccess(deployment);
        verify(placement).requireConfiguredPlacement(any(), eq(TargetIntent.CUSTOMER_STAGING));
        verify(customers, never()).requireOffer(any());
    }

    @Test void readUsesOwnedTargetBoundaryAndReturnsOnlyStoredSummary() {
        when(bindings.get("deployment", "production")).thenReturn(summary("production"));
        assertThat(service.get("deployment", "production").targetProfileId()).isEqualTo("production");
        verify(access).requireDeploymentAccess(deployment);
        verify(placement).requireConfiguredPlacement(any(), eq(TargetIntent.CUSTOMER_PRODUCTION));
        verify(bindings, never()).bind(any(), any(), any());
    }

    @Test void foreignCustomerCannotReadOrBind() {
        deployment.setCustomerId("another-customer");
        assertThatThrownBy(() -> service.get("deployment", "staging")).hasMessageContaining("RESOURCE_SCOPE_MISMATCH");
        assertThatThrownBy(() -> service.bind("deployment", "staging", request(AuthenticationType.TOKEN, null)))
            .hasMessageContaining("RESOURCE_SCOPE_MISMATCH");
        verifyNoInteractions(bindings, access, placement);
    }

    @Test void absentInactiveOrUnapprovedTargetCannotReachBinder() {
        assertThatThrownBy(() -> service.bind("deployment", "foreign", request(AuthenticationType.TOKEN, null)))
            .hasMessageContaining("UNENTITLED_TARGET");
        targets.findById("staging").orElseThrow().setActive(false);
        assertThatThrownBy(() -> service.get("deployment", "staging")).hasMessageContaining("UNENTITLED_TARGET");
        doThrow(deny(PLACEMENT_DENIED)).when(placement).requireConfiguredPlacement(any(), any());
        assertThatThrownBy(() -> service.bind("deployment", "production", request(AuthenticationType.TOKEN, null)))
            .hasMessageContaining("PLACEMENT_DENIED");
        verifyNoInteractions(bindings);
    }

    @Test void archivedDeploymentInactiveTenantAndRevokedCustomerFailBeforeBinder() {
        deployment.setStatus("ARCHIVED");
        assertThatThrownBy(() -> service.get("deployment", "staging")).hasMessageContaining("RESOURCE_SCOPE_MISMATCH");
        deployment.setStatus("ACTIVE"); tenants.findById("tenant").orElseThrow().setStatus("INACTIVE");
        assertThatThrownBy(() -> service.bind("deployment", "staging", request(AuthenticationType.TOKEN, null)))
            .hasMessageContaining("RESOURCE_SCOPE_MISMATCH");
        when(customers.requireCustomerActor()).thenThrow(deny(ACTIVE_CUSTOMER_SCOPE_REQUIRED));
        assertThatThrownBy(() -> service.get("deployment", "staging")).hasMessageContaining("ACTIVE_CUSTOMER_SCOPE_REQUIRED");
        verifyNoInteractions(bindings);
    }

    @Test void callerCannotSupplyStaffApprovalAndBindingFailuresAreNotReinterpretedAsSuccess() {
        assertThatThrownBy(() -> service.bind("deployment", "staging", request(AuthenticationType.TOKEN, "invented-review")))
            .hasMessageContaining("INVALID_CONTRACT");
        verifyNoInteractions(bindings);
        var failed = new ResponseStatusException(org.springframework.http.HttpStatus.PRECONDITION_FAILED, "EXTERNAL_VECTOR_SERVING_VERIFICATION_FAILED");
        when(bindings.bind(any(), any(), any())).thenThrow(failed);
        assertThatThrownBy(() -> service.bind("deployment", "staging", request(AuthenticationType.TOKEN, null))).isSameAs(failed);
    }

    @Test void eachTargetAndVersionGetsADistinctStableApprovalWithoutNewBootstrapRestrictions() {
        service.bind("deployment", "staging", request(AuthenticationType.TOKEN, null));
        service.bind("deployment", "staging", request(AuthenticationType.TOKEN, null));
        service.bind("deployment", "production", request(AuthenticationType.TOKEN, null));
        var input = request(AuthenticationType.TOKEN, null);
        service.bind("deployment", "staging", new Request("next-version", input.clusterId(), input.projectId(), input.regionId(),
            input.expectedDeploymentOption(), input.databaseName(), input.username(), input.password(), null, input.authType(), input.token()));
        var captured = ArgumentCaptor.forClass(Request.class);
        verify(bindings, times(4)).bind(eq("deployment"), any(), captured.capture());
        assertThat(captured.getAllValues().get(0).approvalReference()).isEqualTo(captured.getAllValues().get(1).approvalReference())
            .isNotEqualTo(captured.getAllValues().get(2).approvalReference())
            .isNotEqualTo(captured.getAllValues().get(3).approvalReference());
    }

    private Request request(AuthenticationType auth, String approval) {
        return new Request("version", "cluster", "project", "region", "Free", "default",
            auth == AuthenticationType.USERNAME_PASSWORD ? "serving-user" : null,
            auth == AuthenticationType.USERNAME_PASSWORD ? "synthetic-serving-password" : null,
            approval, auth, auth == AuthenticationType.TOKEN ? "synthetic-serving-token" : null);
    }

    private Summary summary(String target) {
        return new Summary("binding", "deployment", target, "version", "cluster", "Free", "cluster.zilliz.com", "default",
            "scoped-prefix", "RETAIN_EXTERNAL", "fingerprint", java.time.Instant.parse("2026-11-02T12:00:00Z"), AuthenticationType.TOKEN);
    }
}
