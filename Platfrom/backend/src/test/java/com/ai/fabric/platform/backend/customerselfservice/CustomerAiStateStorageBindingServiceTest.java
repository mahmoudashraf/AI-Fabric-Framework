package com.ai.fabric.platform.backend.customerselfservice;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerAiStateStorageBindingContracts.Request;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerEntitlementCommandRepository;
import com.ai.fabric.platform.backend.customerselfservice.service.*;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentAiStateStorageContracts;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentAiStateStorageBindingService;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.security.PlatformRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.Optional;

import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CustomerAiStateStorageBindingServiceTest {
    private final CustomerOfferEntitlementService entitlements = mock(CustomerOfferEntitlementService.class);
    private final CustomerSelfServiceAuthorizationService authorization = mock(CustomerSelfServiceAuthorizationService.class);
    private final CustomerEntitlementCommandRepository commands = mock(CustomerEntitlementCommandRepository.class);
    private final DeploymentRepository deployments = mock(DeploymentRepository.class);
    private final DeploymentVersionRepository versions = mock(DeploymentVersionRepository.class);
    private final DeploymentAiStateStorageBindingService bindings = mock(DeploymentAiStateStorageBindingService.class);
    private final PlatformSecretService secrets = mock(PlatformSecretService.class);
    private final CustomerOfferCodec codec = CustomerOfferTestFixtures.codec();
    private final PlatformAuditService audit = mock(PlatformAuditService.class);
    private final CustomerAiStateStorageBindingService service = new CustomerAiStateStorageBindingService(
        entitlements, authorization, commands, deployments, versions, bindings, secrets, codec, audit);
    private Authorization approved;
    private com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity version;

    @BeforeEach
    void setUp() {
        CustomerOfferTestFixtures.authenticate("owner@test.invalid", PlatformRole.CUSTOMER_ADMIN, "SESSION");
        when(entitlements.requireCustomerActor()).thenReturn(new CustomerOfferEntitlementService.CustomerActor("customer", "owner"));
        var deployment = new DeploymentEntity(); deployment.setId("deployment"); deployment.setCustomerId("customer");
        deployment.setTenantId("tenant");
        when(deployments.findByIdForUpdate("deployment")).thenReturn(Optional.of(deployment));
        when(deployments.findById("deployment")).thenReturn(Optional.of(deployment));
        version = new com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity();
        version.setId("version"); version.setDeploymentId("deployment"); version.setStatus("PUBLISHED");
        version.setProviderConfigJson("{\"aiStateStorageMode\":\"CUSTOMER_POSTGRES\"}");
        when(versions.findById("version")).thenReturn(Optional.of(version));
        approved = new Authorization(null, null);
        when(authorization.authorize(eq("customer"), any())).thenReturn(approved);
        when(bindings.bind(eq("deployment"), eq("target"), any())).thenReturn(
            new DeploymentAiStateStorageContracts.Summary("binding", "customer", "tenant", "deployment", "target", "version",
                "config", "CUSTOMER_POSTGRES", "RETAIN_EXTERNAL", true, "fingerprint", Instant.parse("2026-10-19T00:00:00Z")));
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void bindsActualOwnedVersionWithoutBootstrapDefinitionOrReturningCredentials() {
        var result = service.bind("offer", "deployment", request("synthetic-secret"));
        var input = ArgumentCaptor.forClass(DeploymentAiStateStorageContracts.Request.class);
        verify(bindings).bind(eq("deployment"), eq("target"), input.capture());
        assertThat(input.getValue().password()).isEqualTo("synthetic-secret");
        assertThat(input.getValue().approvalReference()).startsWith("customer-ai-state:");
        assertThat(codec.write(result)).doesNotContain("synthetic-secret", "db.example.test", "runtime_role");
        verifyNoInteractions(commands, secrets);
        verify(entitlements, never()).requireEntitlement(any(), any(), any(), anyBoolean());
        verify(authorization).authorize(eq("customer"), argThat(value -> value.operation() == Operation.BINDING_REPLACE
            && value.bindingSlotId().equals("ai-state-database") && value.selectedProfileId().equals("CUSTOMER_POSTGRES")));
    }

    @Test
    void replayDelegatesToTheNormalVersionAndCredentialBindingService() {
        var first = service.bind("offer", "deployment", request("synthetic-secret"));
        assertThat(service.bind("offer", "deployment", request("synthetic-secret"))).isEqualTo(first);
        when(bindings.bind(eq("deployment"), eq("target"), argThat(value -> value.password().equals("different-secret"))))
            .thenThrow(new IllegalStateException("immutable binding credentials changed"));
        assertThatThrownBy(() -> service.bind("offer", "deployment", request("different-secret")))
            .hasMessageContaining("immutable binding credentials changed");
        verify(bindings, times(3)).bind(anyString(), anyString(), any());
        verifyNoInteractions(commands, secrets);
    }

    @Test
    void foreignDeploymentCannotAccessTheCredentialVaultOrSqlBinder() {
        when(deployments.findByIdForUpdate("deployment")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.bind("offer", "deployment", request("synthetic-secret")))
            .hasMessageContaining(RESOURCE_SCOPE_MISMATCH.name());
        verifyNoInteractions(bindings, secrets, commands, authorization);
    }

    @Test
    void actualNonExternalStorageSelectionCannotBecomeAnExternalDatabaseBinding() {
        version.setProviderConfigJson("{\"aiStateStorageMode\":\"MANAGED_POSTGRES\"}");
        assertThatThrownBy(() -> service.bind("offer", "deployment", request("synthetic-secret")))
            .hasMessageContaining(BINDING_SLOT_FORBIDDEN.name());
        verifyNoInteractions(bindings, secrets, commands);
    }

    @Test
    void foreignVersionCannotBindOrProbeAnExternalDatabase() {
        version.setDeploymentId("another-deployment");
        assertThatThrownBy(() -> service.bind("offer", "deployment", request("synthetic-secret")))
            .hasMessageContaining(RESOURCE_SCOPE_MISMATCH.name());
        verifyNoInteractions(bindings, secrets);
    }

    @Test
    void unreviewedPublishedVersionCannotReachVaultOrProbe() {
        doThrow(com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.deny(REVIEWED_COMPOSITION_CHANGED))
            .when(authorization).requireReviewedVersion(eq(approved), any());
        assertThatThrownBy(() -> service.bind("offer", "deployment", request("synthetic-secret")))
            .hasMessageContaining(REVIEWED_COMPOSITION_CHANGED.name());
        verifyNoInteractions(bindings, secrets, commands);
        assertThatThrownBy(() -> service.get("offer", "deployment", "target", "ai-state-database", "version"))
            .hasMessageContaining(REVIEWED_COMPOSITION_CHANGED.name());
        verifyNoInteractions(bindings, secrets, commands);
    }

    private Request request(String password) {
        return new Request(2, "bind-key", "target", "ai-state-database",
            new DeploymentAiStateStorageContracts.Request("version", "db.example.test", 5432, "ai_state", "runtime_state",
                "connector_state", "runtime_role", password, true, null));
    }
}
