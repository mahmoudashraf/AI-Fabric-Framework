package com.ai.fabric.platform.backend.customerselfservice;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerEntitlementCommandEntity;
import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferEntitlementEntity;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerDocumentStorageBindingContracts.BindRequest;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerDocumentStorageBindingContracts.Result;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerDocumentStorageBindingContracts.RevokeRequest;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerEntitlementCommandRepository;
import com.ai.fabric.platform.backend.customerselfservice.service.*;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.model.DocumentStorageBindingSummary;
import com.ai.fabric.platform.backend.deployment.model.UpsertDocumentStorageBindingRequest;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentDocumentStorageBindingService;
import com.ai.fabric.platform.backend.marketplace.entity.DeploymentMarketplacePluginInstallEntity;
import com.ai.fabric.platform.backend.marketplace.model.UpdateDeploymentMarketplaceInstallRequest;
import com.ai.fabric.platform.backend.marketplace.repository.DeploymentMarketplacePluginInstallRepository;
import com.ai.fabric.platform.backend.marketplace.service.DeploymentMarketplaceInstallService;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.OPERATION_KEY_CONFLICT;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerDocumentStorageBindingServiceTest {
    private static final String ACTOR = "owner@test.invalid";
    private final CustomerOfferEntitlementService entitlements = mock(CustomerOfferEntitlementService.class);
    private final CustomerSelfServiceAuthorizationService authorization = mock(CustomerSelfServiceAuthorizationService.class);
    private final CustomerEntitlementCommandRepository commands = mock(CustomerEntitlementCommandRepository.class);
    private final DeploymentRepository deployments = mock(DeploymentRepository.class);
    private final DeploymentDocumentStorageBindingService bindings = mock(DeploymentDocumentStorageBindingService.class);
    private final DeploymentMarketplacePluginInstallRepository installs = mock(DeploymentMarketplacePluginInstallRepository.class);
    private final DeploymentMarketplaceInstallService installService = mock(DeploymentMarketplaceInstallService.class);
    private final CustomerOfferCodec codec = CustomerOfferTestFixtures.codec();
    private final PlatformAuditService audit = mock(PlatformAuditService.class);
    private CustomerDocumentStorageBindingService service;
    private CustomerOfferEntitlementEntity grant;
    private DeploymentEntity deployment;
    private Authorization approved;

    @BeforeEach void setup() {
        CustomerOfferTestFixtures.authenticate(ACTOR, PlatformRole.CUSTOMER_ADMIN, "SESSION");
        service = new CustomerDocumentStorageBindingService(entitlements, authorization, commands,
            deployments, bindings, installs, installService, codec, audit);
        grant = new CustomerOfferEntitlementEntity();
        grant.setId("grant");
        grant.setRevision(2);
        when(entitlements.requireCustomerActor()).thenReturn(
            new CustomerOfferEntitlementService.CustomerActor("customer", "owner"));
        when(entitlements.requireEntitlement("customer", "offer", "owner", true)).thenReturn(grant);
        deployment = new DeploymentEntity();
        deployment.setId("deployment");
        deployment.setCustomerId("customer");
        deployment.setTenantId("tenant");
        when(deployments.findById("deployment")).thenReturn(Optional.of(deployment));
        var definition = CustomerOfferTestFixtures.definition();
        approved = new Authorization(new AdmissionContext("customer", "tenant", "deployment", null,
            "staging-target", TargetIntent.CUSTOMER_STAGING, "offer", codec.hash(definition), 2,
            "placement", Operation.BINDING_REPLACE, "bind-key", definition.limits()), definition);
        when(authorization.authorize(eq("customer"), any())).thenReturn(approved);
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void bindsOnlyTheReviewedS3SlotAndNeverPersistsCredentialsInTheResult() {
        BindRequest request = bindRequest("bind-key");
        DocumentStorageBindingSummary summary = summary("binding");
        when(bindings.upsert(eq("deployment"), any())).thenReturn(summary);

        Result result = service.bind("offer", "deployment", request);

        assertThat(result.status()).isEqualTo("CONFIGURED");
        assertThat(result.binding()).isEqualTo(summary);
        var auth = ArgumentCaptor.forClass(AuthorizationRequest.class);
        verify(authorization).authorize(eq("customer"), auth.capture());
        assertThat(auth.getValue()).isEqualTo(new AuthorizationRequest("offer", "staging-target", "deployment",
            "tenant", null, Operation.BINDING_REPLACE, "bind-key", Set.of(), "document-storage",
            "reviewed-s3", null));
        var storage = ArgumentCaptor.forClass(UpsertDocumentStorageBindingRequest.class);
        verify(bindings).upsert(eq("deployment"), storage.capture());
        assertThat(storage.getValue()).isEqualTo(new UpsertDocumentStorageBindingRequest("staging-target",
            DeploymentDocumentStorageBindingService.S3_CONNECTOR, "https://s3.eu-north-1.amazonaws.com",
            "eu-north-1", "customer-bucket", "dealership/staging/", "access-key", "secret-value",
            null, false, false, false, null));
        var saved = ArgumentCaptor.forClass(CustomerEntitlementCommandEntity.class);
        verify(commands).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getCommandType()).isEqualTo("BIND_S3");
        assertThat(saved.getValue().getResultJson()).doesNotContain("access-key", "secret-value");
        verify(audit).record(eq("CUSTOMER_DOCUMENT_STORAGE_BOUND"), eq("DEPLOYMENT"), eq("deployment"), anyMap());
    }

    @Test void exactReplayReturnsTheStoredSafeResultWithoutRepeatingSecretMutation() {
        BindRequest request = bindRequest("bind-key");
        Result stored = new Result("offer", "deployment", "staging-target", "document-storage",
            "reviewed-s3", "CONFIGURED", summary("original"));
        when(commands.findByEntitlementIdAndOperationKey("grant", "bind-key")).thenReturn(Optional.of(
            command("BIND_S3", fingerprint(request), codec.write(stored))));
        grant.setRevision(3);

        assertThat(service.bind("offer", "deployment", request)).isEqualTo(stored);
        verifyNoInteractions(authorization, deployments, bindings, audit);
    }

    @Test void sameOperationKeyWithChangedCredentialsIsRejectedWithoutWriting() {
        BindRequest request = bindRequest("bind-key");
        BindRequest previous = new BindRequest(2, "bind-key", "staging-target", "document-storage",
            "reviewed-s3", request.endpoint(), request.region(), request.bucket(), request.prefix(),
            request.accessKey(), "older-secret", null, false, false);
        when(commands.findByEntitlementIdAndOperationKey("grant", "bind-key")).thenReturn(Optional.of(
            command("BIND_S3", fingerprint(previous), "{}")));

        assertThatThrownBy(() -> service.bind("offer", "deployment", request))
            .hasMessageContaining(OPERATION_KEY_CONFLICT.name());
        verifyNoInteractions(authorization, deployments, bindings, audit);
    }

    @Test void revokeRemovesOnlyTheExactTargetBindingAndPreservesSourceObjects() {
        RevokeRequest request = new RevokeRequest(2, "revoke-key", "staging-target", "document-storage");
        when(bindings.list("deployment")).thenReturn(List.of(summary("binding"),
            new DocumentStorageBindingSummary("production-binding", "deployment", "production-target",
                DeploymentDocumentStorageBindingService.S3_CONNECTOR, "CONFIGURED", true,
                JsonNodeFactory.instance.objectNode(), Instant.now())));

        Result result = service.revoke("offer", "deployment", request);

        assertThat(result.status()).isEqualTo("REVOKED");
        assertThat(result.binding()).isNull();
        verify(bindings).remove("deployment", "binding");
        verify(bindings, never()).remove("deployment", "production-binding");
        verify(audit).record(eq("CUSTOMER_DOCUMENT_STORAGE_REVOKED"), eq("DEPLOYMENT"), eq("deployment"),
            argThat(values -> Boolean.FALSE.equals(values.get("sourceObjectsDeleted"))));
    }

    @Test void reviewedBindingMappingEnablesThePinnedPluginWithTheCreatedBindingReference() {
        useMappedDefinition(Operation.BINDING_REPLACE, "bind-key");
        DeploymentMarketplacePluginInstallEntity install = install("install", "{\"publicContentApprovalConfirmed\":true}");
        when(installs.findByDeploymentIdAndPluginId("deployment", "knowledge-plugin"))
            .thenReturn(Optional.of(install));
        when(bindings.upsert(eq("deployment"), any())).thenReturn(summary("binding"));

        service.bind("offer", "deployment", bindRequest("bind-key"));

        var request = ArgumentCaptor.forClass(UpdateDeploymentMarketplaceInstallRequest.class);
        verify(installService).updateInstallForTrustedCaller(eq(deployment), eq("install"), request.capture());
        assertThat(request.getValue().status()).isEqualTo("ENABLED");
        assertThat(request.getValue().config().path("documentStorageBindingRef").asText()).isEqualTo("binding");
        assertThat(request.getValue().config().path("publicContentApprovalConfirmed").asBoolean()).isTrue();
    }

    @Test void reviewedBindingRevocationDisablesThePinnedPluginBeforeRemovingStorage() {
        useMappedDefinition(Operation.BINDING_REVOKE, "revoke-key");
        DeploymentMarketplacePluginInstallEntity install = install(
            "install",
            "{\"documentStorageBindingRef\":\"binding\",\"publicContentApprovalConfirmed\":true}"
        );
        when(installs.findByDeploymentIdAndPluginId("deployment", "knowledge-plugin"))
            .thenReturn(Optional.of(install));
        when(bindings.list("deployment")).thenReturn(List.of(summary("binding")));

        service.revoke("offer", "deployment", new RevokeRequest(
            2,
            "revoke-key",
            "staging-target",
            "document-storage"
        ));

        var request = ArgumentCaptor.forClass(UpdateDeploymentMarketplaceInstallRequest.class);
        verify(installService).updateInstallForTrustedCaller(eq(deployment), eq("install"), request.capture());
        assertThat(request.getValue().status()).isEqualTo("DISABLED");
        assertThat(request.getValue().config().has("documentStorageBindingRef")).isFalse();
        var order = inOrder(installService, bindings);
        order.verify(installService).updateInstallForTrustedCaller(eq(deployment), eq("install"), any());
        order.verify(bindings).remove("deployment", "binding");
    }

    private void useMappedDefinition(Operation operation, String operationKey) {
        Definition base = CustomerOfferTestFixtures.definition();
        Definition mapped = new Definition(base.schemaVersion(), base.name(), base.templateId(), base.template(),
            base.plugins(), base.artifacts(), base.targets(), base.operations(), base.settings(),
            List.of(new BindingSlot("document-storage", BindingKind.CUSTOMER_S3, Set.of("reviewed-s3"),
                "knowledge-plugin", "documentStorageBindingRef")),
            base.documentAudiences(), base.admissionPrerequisites(), base.limits(), base.approvedPolicyRef(),
            base.operatingPolicyRef(), base.identityPolicyRef(), base.storagePolicyRef());
        approved = new Authorization(new AdmissionContext("customer", "tenant", "deployment", null,
            "staging-target", TargetIntent.CUSTOMER_STAGING, "offer", codec.hash(mapped), 2,
            "placement", operation, operationKey, mapped.limits()), mapped);
        when(authorization.authorize(eq("customer"), any())).thenReturn(approved);
    }

    private DeploymentMarketplacePluginInstallEntity install(String id, String configJson) {
        DeploymentMarketplacePluginInstallEntity install = new DeploymentMarketplacePluginInstallEntity();
        install.setId(id);
        install.setDeploymentId("deployment");
        install.setPluginId("knowledge-plugin");
        install.setConfigJson(configJson);
        install.setSecretRefsJson("{}");
        install.setStatus("DISABLED");
        return install;
    }

    private BindRequest bindRequest(String operationKey) {
        return new BindRequest(2, operationKey, "staging-target", "document-storage", "reviewed-s3",
            "https://s3.eu-north-1.amazonaws.com", "eu-north-1", "customer-bucket",
            "dealership/staging/", "access-key", "secret-value", null, false, false);
    }

    private DocumentStorageBindingSummary summary(String ref) {
        return new DocumentStorageBindingSummary(ref, "deployment", "staging-target",
            DeploymentDocumentStorageBindingService.S3_CONNECTOR, "CONFIGURED", true,
            JsonNodeFactory.instance.objectNode(), Instant.now());
    }

    private CustomerEntitlementCommandEntity command(String type, String fingerprint, String json) {
        return new CustomerEntitlementCommandEntity("command", "grant", "bind-key", fingerprint,
            type, ACTOR, json, Instant.now());
    }

    private String fingerprint(BindRequest request) {
        return codec.hash(java.util.Map.of("command", "BIND_S3", "offerId", "offer",
            "deploymentId", "deployment", "request", request, "customerId", "customer", "actor", ACTOR));
    }
}
