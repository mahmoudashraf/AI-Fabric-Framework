package com.ai.fabric.platform.backend.customerselfservice;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerEntitlementCommandEntity;
import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferEntitlementEntity;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerDeploymentVersionContracts.PublishRequest;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerDeploymentVersionContracts.PublishResult;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerEntitlementCommandRepository;
import com.ai.fabric.platform.backend.customerselfservice.service.*;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentDraftResponse;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVersionSummary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.ai.fabric.platform.backend.security.PlatformRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CustomerDeploymentVersionServiceTest {
    private final CustomerOfferEntitlementService entitlements = mock(CustomerOfferEntitlementService.class);
    private final CustomerSelfServiceAuthorizationService authorization = mock(CustomerSelfServiceAuthorizationService.class);
    private final CustomerSelfServiceOperationBoundary boundary = mock(CustomerSelfServiceOperationBoundary.class);
    private final CustomerEntitlementCommandRepository commands = mock(CustomerEntitlementCommandRepository.class);
    private final DeploymentRepository deployments = mock(DeploymentRepository.class);
    private final DeploymentVersionRepository versions = mock(DeploymentVersionRepository.class);
    private final DeploymentService deploymentService = mock(DeploymentService.class);
    private final CustomerOfferCodec codec = CustomerOfferTestFixtures.codec();
    private final PlatformAuditService audit = mock(PlatformAuditService.class);
    private final PublishRequest request = new PublishRequest("staging-target", 2, "publish-key");
    private CustomerDeploymentVersionService service;
    private CustomerOfferEntitlementEntity grant;
    private Authorization approved;

    @BeforeEach
    void setup() {
        CustomerOfferTestFixtures.authenticate("owner@test.invalid", PlatformRole.CUSTOMER_ADMIN, "SESSION");
        service = new CustomerDeploymentVersionService(entitlements, authorization, boundary, commands, deployments,
            versions, deploymentService, codec, audit);
        grant = new CustomerOfferEntitlementEntity();
        grant.setId("grant"); grant.setRevision(2);
        when(entitlements.requireCustomerActor()).thenReturn(new CustomerOfferEntitlementService.CustomerActor("customer", "owner"));
        when(entitlements.requireEntitlement("customer", "offer", "owner", true)).thenReturn(grant);
        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("deployment"); deployment.setCustomerId("customer"); deployment.setTenantId("tenant");
        when(deployments.findById("deployment")).thenReturn(Optional.of(deployment));
        approved = new Authorization(new AdmissionContext("customer", "tenant", "deployment", null,
            "staging-target", TargetIntent.CUSTOMER_STAGING, "offer", "fingerprint", 2, "placement",
            Operation.VERSION_PUBLISH, request.operationKey(), CustomerOfferTestFixtures.definition().limits()),
            CustomerOfferTestFixtures.definition());
        when(authorization.authorize(eq("customer"), any())).thenReturn(approved);
        DeploymentDraftResponse draft = mock(DeploymentDraftResponse.class);
        when(draft.id()).thenReturn("draft");
        when(deploymentService.getActiveDraftForDeploymentForTrustedCaller("deployment")).thenReturn(draft);
        when(deploymentService.publishDraftForTrustedCaller("draft")).thenReturn(new DeploymentVersionSummary(
            "version", "deployment", "draft", "v1", "PUBLISHED", "config", false, Instant.now()));
        DeploymentVersionEntity version = new DeploymentVersionEntity();
        version.setId("version"); version.setDeploymentId("deployment"); version.setStatus("PUBLISHED");
        when(versions.findById("version")).thenReturn(Optional.of(version));
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void publishesThroughTheTrustedCompilerOnlyAfterExactOfferAuthorization() {
        PublishResult result = service.publish("offer", "deployment", request);

        assertThat(result).isEqualTo(new PublishResult("deployment", "version", "v1", "config", "staging-target", 2));
        var authorizationRequest = ArgumentCaptor.forClass(AuthorizationRequest.class);
        verify(authorization).authorize(eq("customer"), authorizationRequest.capture());
        assertThat(authorizationRequest.getValue()).isEqualTo(new AuthorizationRequest("offer", "staging-target",
            "deployment", "tenant", null, Operation.VERSION_PUBLISH, "publish-key", Set.of(), null, null, null));
        verify(boundary).requireVersion(approved, versions.findById("version").orElseThrow());
        verify(commands).saveAndFlush(any(CustomerEntitlementCommandEntity.class));
        verify(audit).record(eq("CUSTOMER_OFFER_VERSION_PUBLISHED"), eq("DEPLOYMENT_VERSION"), eq("version"), anyMap());
    }

    @Test
    void exactReplayReturnsTheStoredReceiptWithoutPublishingAgain() {
        PublishResult stored = new PublishResult("deployment", "version-old", "v1", "config-old", "staging-target", 2);
        String fingerprint = fingerprint(request);
        when(commands.findByEntitlementIdAndOperationKey("grant", request.operationKey())).thenReturn(Optional.of(
            new CustomerEntitlementCommandEntity("command", "grant", request.operationKey(), fingerprint,
                "PUBLISH_VERSION", "owner@test.invalid", codec.write(stored), Instant.now())));

        assertThat(service.publish("offer", "deployment", request)).isEqualTo(stored);
        verifyNoInteractions(authorization, deployments, versions, deploymentService, boundary, audit);
        verify(commands, never()).saveAndFlush(any());
    }

    @Test
    void changedPayloadCannotReuseTheOperationKey() {
        when(commands.findByEntitlementIdAndOperationKey("grant", request.operationKey())).thenReturn(Optional.of(
            new CustomerEntitlementCommandEntity("command", "grant", request.operationKey(), "other",
                "PUBLISH_VERSION", "owner@test.invalid", "{}", Instant.now())));
        assertCode(OPERATION_KEY_CONFLICT,
            () -> service.publish("offer", "deployment", new PublishRequest("staging-target", 3, "publish-key")));
        verifyNoInteractions(authorization, deploymentService, boundary, audit);
    }

    @Test
    void foreignDeploymentAndStaleRevisionFailBeforePublication() {
        grant.setRevision(3);
        assertCode(REVISION_CONFLICT, () -> service.publish("offer", "deployment", request));
        grant.setRevision(2);
        deployments.findById("deployment").orElseThrow().setCustomerId("foreign");
        assertCode(RESOURCE_SCOPE_MISMATCH, () -> service.publish("offer", "deployment", request));
        verify(deploymentService, never()).publishDraftForTrustedCaller(anyString());
    }

    @Test
    void compositionFailurePreventsReceiptAndAudit() {
        doThrow(CustomerSelfServiceException.deny(REVIEWED_COMPOSITION_CHANGED))
            .when(boundary).requireVersion(eq(approved), any());
        assertCode(REVIEWED_COMPOSITION_CHANGED, () -> service.publish("offer", "deployment", request));
        verify(commands, never()).saveAndFlush(any());
        verifyNoInteractions(audit);
    }

    private String fingerprint(PublishRequest value) {
        return codec.hash(Map.of("command", "PUBLISH_VERSION", "offerId", "offer", "deploymentId", "deployment",
            "request", value, "customerId", "customer", "ownerId", "owner", "actor", "owner@test.invalid"));
    }

    private void assertCode(CustomerSelfServiceException.Code code, org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(CustomerSelfServiceException.class)
            .satisfies(error -> assertThat(((CustomerSelfServiceException) error).code()).isEqualTo(code));
    }
}
