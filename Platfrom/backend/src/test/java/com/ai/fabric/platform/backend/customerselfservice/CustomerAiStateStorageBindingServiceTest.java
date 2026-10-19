package com.ai.fabric.platform.backend.customerselfservice;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerEntitlementCommandEntity;
import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferEntitlementEntity;
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
import java.util.HashMap;
import java.util.Map;
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
    private final Map<String, String> secretValues = new HashMap<>();
    private final Map<String, CustomerEntitlementCommandEntity> receipts = new HashMap<>();
    private CustomerOfferEntitlementEntity grant;
    private Authorization approved;

    @BeforeEach
    void setUp() {
        CustomerOfferTestFixtures.authenticate("owner@test.invalid", PlatformRole.CUSTOMER_ADMIN, "SESSION");
        when(entitlements.requireCustomerActor()).thenReturn(new CustomerOfferEntitlementService.CustomerActor("customer", "owner"));
        grant = new CustomerOfferEntitlementEntity(); grant.setId("grant"); grant.setRevision(2);
        when(entitlements.requireEntitlement("customer", "offer", "owner", true)).thenReturn(grant);
        var deployment = new DeploymentEntity(); deployment.setId("deployment"); deployment.setCustomerId("customer");
        deployment.setTenantId("tenant");
        when(deployments.findByIdForUpdate("deployment")).thenReturn(Optional.of(deployment));
        when(deployments.findById("deployment")).thenReturn(Optional.of(deployment));
        var version = new com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity();
        version.setId("version"); version.setDeploymentId("deployment"); version.setStatus("PUBLISHED");
        when(versions.findById("version")).thenReturn(Optional.of(version));
        var tree = (com.fasterxml.jackson.databind.node.ObjectNode) codec.tree(codec.write(CustomerOfferTestFixtures.definition()));
        tree.withArray("bindingSlots").addObject().put("id", "ai-state-database").put("kind", "CUSTOMER_AI_STATE")
            .putArray("supportedProfileIds").add("CUSTOMER_POSTGRES");
        approved = new Authorization(null, codec.read(tree.toString(), Definition.class));
        when(authorization.authorize(eq("customer"), any())).thenReturn(approved);
        when(secrets.resolveSecret(anyString())).thenAnswer(call -> secretValues.get(call.getArgument(0)));
        doAnswer(call -> { secretValues.put(call.getArgument(0), call.getArgument(1)); return null; })
            .when(secrets).upsertManagedSecret(anyString(), anyString(), anyMap());
        when(commands.findByEntitlementIdAndOperationKey(eq("grant"), anyString()))
            .thenAnswer(call -> Optional.ofNullable(receipts.get(call.getArgument(1))));
        when(commands.saveAndFlush(any())).thenAnswer(call -> {
            CustomerEntitlementCommandEntity value = call.getArgument(0); receipts.put(value.getOperationKey(), value); return value;
        });
        when(bindings.bind(eq("deployment"), eq("target"), any())).thenReturn(
            new DeploymentAiStateStorageContracts.Summary("binding", "customer", "tenant", "deployment", "target", "version",
                "config", "CUSTOMER_POSTGRES", "RETAIN_EXTERNAL", true, "fingerprint", Instant.parse("2026-10-19T00:00:00Z")));
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void bindsOnlyAnOwnedReviewedTargetAndDerivesApprovalWithoutReturningCredentials() {
        var result = service.bind("offer", "deployment", request("synthetic-secret"));
        var input = ArgumentCaptor.forClass(DeploymentAiStateStorageContracts.Request.class);
        verify(bindings).bind(eq("deployment"), eq("target"), input.capture());
        assertThat(input.getValue().password()).isEqualTo("synthetic-secret");
        assertThat(input.getValue().approvalReference()).startsWith("customer-ai-state:");
        assertThat(codec.write(result)).doesNotContain("synthetic-secret", "db.example.test", "runtime_role");
        assertThat(receipts.get("bind-key").getRequestFingerprint()).hasSize(64);
        assertThat(secretValues.keySet()).allMatch(name -> name.matches("MANAGED_[A-Z0-9_]+"));
        verify(authorization).authorize(eq("customer"), argThat(value -> value.operation() == Operation.BINDING_REPLACE
            && value.bindingSlotId().equals("ai-state-database") && value.selectedProfileId().equals("CUSTOMER_POSTGRES")));
    }

    @Test
    void exactReplayReturnsReceiptButChangedWriteOnlyPasswordConflicts() {
        var first = service.bind("offer", "deployment", request("synthetic-secret"));
        assertThat(service.bind("offer", "deployment", request("synthetic-secret"))).isEqualTo(first);
        assertThatThrownBy(() -> service.bind("offer", "deployment", request("different-secret")))
            .hasMessageContaining(OPERATION_KEY_CONFLICT.name());
        verify(bindings, times(1)).bind(anyString(), anyString(), any());
    }

    @Test
    void foreignDeploymentCannotAccessTheCredentialVaultOrSqlBinder() {
        when(deployments.findByIdForUpdate("deployment")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.bind("offer", "deployment", request("synthetic-secret")))
            .hasMessageContaining(RESOURCE_SCOPE_MISMATCH.name());
        verifyNoInteractions(bindings, secrets, commands, authorization);
    }

    @Test
    void missingReviewedSlotCannotBecomeAnInfrastructurePermission() {
        approved = new Authorization(null, CustomerOfferTestFixtures.definition());
        when(authorization.authorize(eq("customer"), any())).thenReturn(approved);
        assertThatThrownBy(() -> service.bind("offer", "deployment", request("synthetic-secret")))
            .hasMessageContaining(BINDING_SLOT_FORBIDDEN.name());
        verifyNoInteractions(bindings, secrets, commands);
    }

    @Test
    void staleGrantCannotBindOrProbeAnExternalDatabase() {
        grant.setRevision(3);
        assertThatThrownBy(() -> service.bind("offer", "deployment", request("synthetic-secret")))
            .hasMessageContaining(REVISION_CONFLICT.name());
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
