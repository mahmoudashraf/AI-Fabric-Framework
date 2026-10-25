package com.ai.fabric.platform.backend.customerselfservice;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerDocumentStorageBindingContracts.BindRequest;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerDocumentStorageBindingContracts.RevokeRequest;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerEntitlementCommandRepository;
import com.ai.fabric.platform.backend.customerselfservice.service.*;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.model.DocumentStorageBindingSummary;
import com.ai.fabric.platform.backend.deployment.model.UpsertDocumentStorageBindingRequest;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentDocumentStorageBindingService;
import com.ai.fabric.platform.backend.marketplace.repository.DeploymentMarketplacePluginInstallRepository;
import com.ai.fabric.platform.backend.marketplace.service.DeploymentMarketplaceInstallService;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerDocumentStorageBindingServiceTest {
    private final CustomerOfferEntitlementService entitlements = mock(CustomerOfferEntitlementService.class);
    private final CustomerSelfServiceAuthorizationService authorization = mock(CustomerSelfServiceAuthorizationService.class);
    private final CustomerEntitlementCommandRepository commands = mock(CustomerEntitlementCommandRepository.class);
    private final DeploymentRepository deployments = mock(DeploymentRepository.class);
    private final DeploymentDocumentStorageBindingService bindings = mock(DeploymentDocumentStorageBindingService.class);
    private final DeploymentMarketplacePluginInstallRepository installs = mock(DeploymentMarketplacePluginInstallRepository.class);
    private final DeploymentMarketplaceInstallService installService = mock(DeploymentMarketplaceInstallService.class);
    private final CustomerOfferCodec codec = CustomerOfferTestFixtures.codec();
    private final PlatformAuditService audit = mock(PlatformAuditService.class);
    private final CustomerDocumentStorageBindingService service = new CustomerDocumentStorageBindingService(
        entitlements, authorization, commands, deployments, bindings, installs, installService, codec, audit);
    private DeploymentEntity deployment;

    @BeforeEach void setup() {
        CustomerOfferTestFixtures.authenticate("owner@test.invalid", PlatformRole.CUSTOMER_ADMIN, "SESSION");
        when(entitlements.requireCustomerActor()).thenReturn(new CustomerOfferEntitlementService.CustomerActor("customer", "owner"));
        deployment = new DeploymentEntity(); deployment.setId("deployment"); deployment.setCustomerId("customer");
        deployment.setTenantId("tenant");
        when(deployments.findByIdForUpdate("deployment")).thenReturn(Optional.of(deployment));
        when(authorization.authorize(eq("customer"), any())).thenReturn(new Authorization(null, null));
        when(bindings.upsert(eq("deployment"), any(), eq("bind-key"), eq("document-storage"), eq("new-s3-profile")))
            .thenReturn(summary("binding", "staging-target"));
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void bindsSelectedProfileWithoutBootstrapDefinitionOrCredentialDisclosure() {
        var result = service.bind("bootstrap-provenance", "deployment", request("secret-value"));
        var input = ArgumentCaptor.forClass(UpsertDocumentStorageBindingRequest.class);
        verify(bindings).upsert(eq("deployment"), input.capture(), eq("bind-key"), eq("document-storage"), eq("new-s3-profile"));
        assertThat(input.getValue()).isEqualTo(new UpsertDocumentStorageBindingRequest("staging-target",
            DeploymentDocumentStorageBindingService.S3_CONNECTOR, "https://s3.eu-north-1.amazonaws.com", "eu-north-1",
            "customer-bucket", "dealership/staging/", "access-key", "secret-value", null, false, false, false, null));
        assertThat(result.status()).isEqualTo("CONFIGURED");
        assertThat(codec.write(result)).doesNotContain("secret-value", "access-key");
        verify(authorization).authorize(eq("customer"), argThat(value -> value.operation() == Operation.BINDING_REPLACE
            && value.targetProfileId().equals("staging-target") && value.selectedProfileId().equals("new-s3-profile")));
        verify(entitlements, never()).requireEntitlement(any(), any(), any(), anyBoolean());
        verifyNoInteractions(commands, installs, installService);
    }

    @Test void normalBindingServiceOwnsReplayAndCredentialConflictChecks() {
        var first = service.bind("offer", "deployment", request("secret-value"));
        assertThat(service.bind("offer", "deployment", request("secret-value"))).isEqualTo(first);
        when(bindings.upsert(eq("deployment"), argThat(input -> "changed".equals(input.secretKey())),
            eq("bind-key"), eq("document-storage"), eq("new-s3-profile")))
            .thenThrow(new IllegalStateException("credential operation conflict"));
        assertThatThrownBy(() -> service.bind("offer", "deployment", request("changed")))
            .hasMessageContaining("credential operation conflict");
        verifyNoInteractions(commands, installs, installService);
    }

    @Test void bindNeverReEnablesOrRewritesCustomerPluginConfiguration() {
        service.bind("offer", "deployment", request("secret-value"));
        verifyNoInteractions(installs, installService);
    }

    @Test void revokeRemovesOnlyTheExactTargetBindingAndPreservesSourceObjects() {
        when(bindings.list("deployment")).thenReturn(List.of(summary("stage", "staging-target"), summary("prod", "production-target")));
        var result = service.revoke("offer", "deployment", new RevokeRequest(0, "revoke-key", "staging-target", "document-storage"));
        assertThat(result.status()).isEqualTo("REVOKED");
        verify(bindings).remove("deployment", "stage", "revoke-key", "document-storage");
        verify(bindings, never()).remove(eq("deployment"), eq("prod"), any(), any());
        verify(audit).record(eq("CUSTOMER_DOCUMENT_STORAGE_REVOKED"), eq("DEPLOYMENT"), eq("deployment"),
            argThat(values -> Boolean.FALSE.equals(values.get("sourceObjectsDeleted"))));
        verifyNoInteractions(commands, installs, installService);
    }

    @Test void foreignCustomerCannotReachStorageOrCredentials() {
        deployment.setCustomerId("foreign");
        assertThatThrownBy(() -> service.bind("offer", "deployment", request("secret-value")))
            .hasMessageContaining(RESOURCE_SCOPE_MISMATCH.name());
        verifyNoInteractions(authorization, bindings, commands, installs, installService);
    }

    @Test void scopedS3WrapperRejectsUnknownLogicalSlotWithoutInvokingStorage() {
        var request = request("secret-value");
        assertThatThrownBy(() -> service.bind("offer", "deployment", new BindRequest(0, "bind-key", "staging-target",
            "foreign-slot", "new-s3-profile", request.endpoint(), request.region(), request.bucket(), request.prefix(),
            request.accessKey(), request.secretKey(), null, false, false))).hasMessageContaining(BINDING_SLOT_FORBIDDEN.name());
        verifyNoInteractions(bindings, commands, installs, installService);
    }

    private BindRequest request(String secret) {
        return new BindRequest(0, "bind-key", "staging-target", "document-storage", "new-s3-profile",
            "https://s3.eu-north-1.amazonaws.com", "eu-north-1", "customer-bucket", "dealership/staging/",
            "access-key", secret, null, false, false);
    }

    private DocumentStorageBindingSummary summary(String id, String target) {
        return new DocumentStorageBindingSummary(id, "deployment", target, DeploymentDocumentStorageBindingService.S3_CONNECTOR,
            "CONFIGURED", true, JsonNodeFactory.instance.objectNode(), Instant.now());
    }
}
