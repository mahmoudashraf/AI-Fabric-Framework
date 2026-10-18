package com.ai.fabric.platform.backend.customerselfservice;

import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerEntitlementCommandEntity;
import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferDeploymentEntity;
import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferEntitlementEntity;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Authorization;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerStagingConsumerActivationContracts.Request;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerStagingConsumerActivationContracts.CreateRequest;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerEntitlementCommandRepository;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.customerselfservice.service.*;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import com.ai.fabric.platform.backend.tenant.model.PlatformConsumerSummary;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
import com.ai.fabric.platform.backend.tenant.service.PlatformCustomerConsumerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.*;
import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.deny;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CustomerStagingConsumerActivationServiceTest {
    private static final String ACTOR = "owner@test.invalid";
    private static final String CREATE_COMMAND = "CREATE_STAGING_CONSUMER";
    private final CustomerOfferEntitlementService entitlements = mock(CustomerOfferEntitlementService.class);
    private final PlatformConsumerRepository consumers = mock(PlatformConsumerRepository.class);
    private final CustomerOfferDeploymentRepository associations = mock(CustomerOfferDeploymentRepository.class);
    private final DeploymentRepository deployments = mock(DeploymentRepository.class);
    private final DeploymentReleaseRepository releases = mock(DeploymentReleaseRepository.class);
    private final DeploymentVersionRepository versions = mock(DeploymentVersionRepository.class);
    private final CustomerSelfServiceAuthorizationService authorization = mock(CustomerSelfServiceAuthorizationService.class);
    private final CustomerSelfServiceOperationBoundary boundary = mock(CustomerSelfServiceOperationBoundary.class);
    private final PlatformCustomerConsumerService consumerService = mock(PlatformCustomerConsumerService.class);
    private final CustomerEntitlementCommandRepository commands = mock(CustomerEntitlementCommandRepository.class);
    private final CustomerOfferCodec codec = CustomerOfferTestFixtures.codec();
    private final CustomerStagingConsumerActivationService service = new CustomerStagingConsumerActivationService(
        entitlements, consumers, associations, deployments, releases, versions, authorization, boundary, consumerService,
        commands, codec);
    private final Request request = new Request("app", "release", 3, 7, "activate-staging");
    private final CreateRequest createRequest = new CreateRequest("app", "App", "description", "release", 7, "create-staging");
    private final Instant now = Instant.now();
    private final PlatformConsumerSummary created = new PlatformConsumerSummary("app", "customer", "App", "description", "ACTIVE",
        null, null, null, null, null, null, null, null, now, now, 0);
    private final PlatformConsumerSummary activated = new PlatformConsumerSummary("app", "customer", "App", "description", "ACTIVE",
        "deployment", "Deployment", "staging", "ACTIVE", "release", "APPLIED_VERIFIED", "staging", now, now, now, 1);
    private PlatformConsumerEntity consumer;
    private CustomerOfferEntitlementEntity entitlement;
    private DeploymentReleaseEntity release;

    @BeforeEach void setUp() {
        CustomerOfferTestFixtures.authenticate(ACTOR, PlatformRole.CUSTOMER_ADMIN, "SESSION");
        when(entitlements.requireCustomerActor()).thenReturn(
            new CustomerOfferEntitlementService.CustomerActor("customer", "owner"));
        consumer = new PlatformConsumerEntity();
        consumer.setId("consumer-entity"); consumer.setCustomerId("customer"); consumer.setConsumerId("app");
        when(consumers.findBindingForUpdate("customer", "app")).thenReturn(Optional.of(consumer));
        var deployment = new DeploymentEntity();
        deployment.setId("deployment"); deployment.setCustomerId("customer"); deployment.setTenantId("tenant");
        when(deployments.findByIdForUpdate("deployment")).thenReturn(Optional.of(deployment));
        when(associations.findById("deployment")).thenReturn(Optional.of(new CustomerOfferDeploymentEntity(
            "deployment", "customer", "tenant", "offer", "staging", "fingerprint", 7,
            "bootstrap", Instant.now())));
        entitlement = new CustomerOfferEntitlementEntity();
        entitlement.setId("grant"); entitlement.setRevision(7);
        when(entitlements.requireEntitlement("customer", "offer", "owner", true)).thenReturn(entitlement);
        release = new DeploymentReleaseEntity();
        release.setId("release"); release.setDeploymentId("deployment"); release.setTargetProfileId("staging");
        release.setDeploymentVersionId("version"); release.setStatus("APPLIED_VERIFIED"); release.setVerificationStatus("PASSED");
        when(releases.findById("release")).thenReturn(Optional.of(release));
        var version = new DeploymentVersionEntity();
        version.setId("version"); version.setDeploymentId("deployment"); version.setStatus("PUBLISHED");
        when(versions.findById("version")).thenReturn(Optional.of(version));
        when(authorization.authorize(eq("customer"), any())).thenReturn(mock(Authorization.class));
        when(consumerService.updateBindingForTrustedPromotion(eq("customer"), eq("app"), eq("deployment"),
            eq("release"), eq("staging"), anyString(), eq(3L), eq("activate-staging")))
            .thenReturn(mock(PlatformConsumerSummary.class));
        when(consumerService.createConsumer(eq("customer"), any())).thenReturn(created);
        when(consumerService.updateBindingForTrustedPromotion(eq("customer"), eq("app"), eq("deployment"),
            eq("release"), eq("staging"), anyString(), eq(0L), eq("create-staging"))).thenReturn(activated);
    }

    @AfterEach void clearAuthentication() { SecurityContextHolder.clearContext(); }

    @Test void bindsOnlyThroughTheExactReviewedStagingRelease() {
        service.activate("offer", "deployment", request);

        var locks = inOrder(consumers, deployments, entitlements);
        locks.verify(consumers).findBindingForUpdate("customer", "app");
        locks.verify(deployments).findByIdForUpdate("deployment");
        locks.verify(entitlements).requireEntitlement("customer", "offer", "owner", true);
        verify(authorization).authorize(eq("customer"), argThat(value -> "offer".equals(value.offerId())
            && "release".equals(value.releaseId()) && "staging".equals(value.targetProfileId())
            && value.operation() == com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation.ACTIVATE));
        verify(boundary).requireVersion(any(), argThat(version -> "version".equals(version.getId())));
        verify(consumerService).updateBindingForTrustedPromotion(eq("customer"), eq("app"), eq("deployment"),
            eq("release"), eq("staging"), anyString(), eq(3L), eq("activate-staging"));
    }

    @Test void anUnverifiedReleaseNeverReachesTheBindingMutation() {
        release.setVerificationStatus("FAILED");

        assertThatThrownBy(() -> service.activate("offer", "deployment", request))
            .isInstanceOf(com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.class)
            .hasMessageContaining(VERIFICATION_REQUIRED.name());
        verifyNoInteractions(authorization, boundary, consumerService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a-previous-deployment", "z-previous-deployment"})
    void rejectsCrossDeploymentMovesBeforeLockingTheDestination(String previousDeploymentId) {
        consumer.setBoundDeploymentId(previousDeploymentId);

        assertThatThrownBy(() -> service.activate("offer", "deployment", request))
            .isInstanceOf(CustomerSelfServiceException.class).hasMessageContaining(RESOURCE_SCOPE_MISMATCH.name());

        verifyNoInteractions(deployments, commands, authorization, boundary, consumerService);
        verify(entitlements, never()).requireEntitlement(anyString(), anyString(), anyString(), anyBoolean());
    }

    @Test void locksAnUnchangedDeploymentOnlyOnce() {
        consumer.setBoundDeploymentId("deployment");

        service.activate("offer", "deployment", request);

        verify(deployments).findByIdForUpdate("deployment");
        verify(deployments, times(1)).findByIdForUpdate(anyString());
    }

    @Test void rejectsAForeignDestinationBeforeEntitlement() {
        var foreign = new DeploymentEntity();
        foreign.setId("deployment"); foreign.setCustomerId("other-customer");
        when(deployments.findByIdForUpdate("deployment")).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.activate("offer", "deployment", request))
            .isInstanceOf(com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.class)
            .hasMessageContaining("RESOURCE_SCOPE_MISMATCH");

        verify(entitlements, never()).requireEntitlement(anyString(), anyString(), anyString(), anyBoolean());
        verifyNoInteractions(authorization, boundary, consumerService);
    }

    @Test void createsAndActivatesTheInitialAssignmentInOneServiceTransaction() {
        assertThat(service.createAndActivate("offer", "deployment", createRequest)).isEqualTo(activated);

        var order = inOrder(deployments, entitlements, commands, consumerService);
        order.verify(deployments).findByIdForUpdate("deployment");
        order.verify(entitlements).requireEntitlement("customer", "offer", "owner", true);
        order.verify(commands).findByEntitlementIdAndOperationKey("grant", "create-staging");
        order.verify(consumerService).createConsumer(eq("customer"), any());
        verify(consumerService).createConsumer(eq("customer"), argThat(value -> "app".equals(value.consumerId())
            && value.deploymentId() == null));
        verify(consumerService).updateBindingForTrustedPromotion(eq("customer"), eq("app"), eq("deployment"),
            eq("release"), eq("staging"), anyString(), eq(0L), eq("create-staging"));
        var saved = ArgumentCaptor.forClass(CustomerEntitlementCommandEntity.class);
        verify(commands).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEntitlementId()).isEqualTo("grant");
        assertThat(saved.getValue().getOperationKey()).isEqualTo(createRequest.operationKey());
        assertThat(saved.getValue().getCommandType()).isEqualTo(CREATE_COMMAND);
        assertThat(saved.getValue().getActorId()).isEqualTo(ACTOR);
        assertThat(saved.getValue().getRequestFingerprint()).isEqualTo(fingerprint(createRequest));
        assertThat(codec.read(saved.getValue().getResultJson(), PlatformConsumerSummary.class)).isEqualTo(activated);
    }

    @Test void lostResponseReplayReturnsTheStoredResultWithoutCreatingOrBindingAgain() {
        service.createAndActivate("offer", "deployment", createRequest);
        var saved = ArgumentCaptor.forClass(CustomerEntitlementCommandEntity.class);
        verify(commands).saveAndFlush(saved.capture());
        when(commands.findByEntitlementIdAndOperationKey("grant", "create-staging"))
            .thenReturn(Optional.of(saved.getValue()));
        entitlement.setRevision(8);

        assertThat(service.createAndActivate("offer", "deployment", createRequest)).isEqualTo(activated);

        verify(consumerService).createConsumer(eq("customer"), any());
        verify(consumerService).updateBindingForTrustedPromotion(eq("customer"), eq("app"), eq("deployment"),
            eq("release"), eq("staging"), anyString(), eq(0L), eq("create-staging"));
        verify(commands).saveAndFlush(any());
        verify(authorization).authorize(eq("customer"), any());
    }

    @ParameterizedTest @MethodSource("changedCreatePayloads")
    void changedPayloadCannotReplayTheSameCommandKey(CreateRequest changed) {
        when(commands.findByEntitlementIdAndOperationKey("grant", "create-staging"))
            .thenReturn(Optional.of(receipt(CREATE_COMMAND, ACTOR)));

        assertThatThrownBy(() -> service.createAndActivate("offer", "deployment", changed))
            .isInstanceOf(CustomerSelfServiceException.class).hasMessageContaining(OPERATION_KEY_CONFLICT.name());

        verifyNoInteractions(consumerService, consumers, authorization, boundary);
        verify(commands, never()).saveAndFlush(any());
    }

    @Test void receiptCannotBeReplayedAgainstAnotherDeployment() {
        var other = new DeploymentEntity(); other.setId("other-deployment"); other.setCustomerId("customer");
        other.setTenantId("other-tenant");
        when(deployments.findByIdForUpdate("other-deployment")).thenReturn(Optional.of(other));
        when(associations.findById("other-deployment")).thenReturn(Optional.of(new CustomerOfferDeploymentEntity(
            "other-deployment", "customer", "other-tenant", "offer", "staging", "fingerprint", 7, "bootstrap", now)));
        when(commands.findByEntitlementIdAndOperationKey("grant", "create-staging"))
            .thenReturn(Optional.of(receipt(CREATE_COMMAND, ACTOR)));

        assertThatThrownBy(() -> service.createAndActivate("offer", "other-deployment", createRequest))
            .isInstanceOf(CustomerSelfServiceException.class).hasMessageContaining(OPERATION_KEY_CONFLICT.name());

        verifyNoInteractions(consumerService, consumers, authorization, boundary);
    }

    @Test void anotherCommandTypeOrRecordedActorCannotReplay() {
        for (var stored : new CustomerEntitlementCommandEntity[] {
            receipt("PUBLISH_VERSION", ACTOR), receipt(CREATE_COMMAND, "another-owner@test.invalid")}) {
            when(commands.findByEntitlementIdAndOperationKey("grant", "create-staging"))
                .thenReturn(Optional.of(stored));
            assertThatThrownBy(() -> service.createAndActivate("offer", "deployment", createRequest))
                .isInstanceOf(CustomerSelfServiceException.class).hasMessageContaining(OPERATION_KEY_CONFLICT.name());
        }
        verifyNoInteractions(consumerService, consumers, authorization, boundary);
    }

    @ParameterizedTest @ValueSource(strings = {"ENTITLEMENT_REVOKED", "ENTITLEMENT_EXPIRED", "INVITE_OWNER_MISMATCH"})
    void currentGrantDenialPrecedesReceiptLookup(String code) {
        when(entitlements.requireEntitlement("customer", "offer", "owner", true))
            .thenThrow(deny(CustomerSelfServiceException.Code.valueOf(code)));

        assertThatThrownBy(() -> service.createAndActivate("offer", "deployment", createRequest))
            .isInstanceOf(CustomerSelfServiceException.class).hasMessageContaining(code);

        verifyNoInteractions(commands, consumerService, consumers, authorization, boundary);
    }

    @Test void staleNewCommandCannotCreateAConsumer() {
        entitlement.setRevision(8);

        assertThatThrownBy(() -> service.createAndActivate("offer", "deployment", createRequest))
            .isInstanceOf(CustomerSelfServiceException.class).hasMessageContaining(REVISION_CONFLICT.name());

        verifyNoInteractions(consumerService, consumers, authorization, boundary);
        verify(commands, never()).saveAndFlush(any());
    }

    @Test void failedActivationDoesNotWriteASuccessReceipt() {
        release.setVerificationStatus("FAILED");

        assertThatThrownBy(() -> service.createAndActivate("offer", "deployment", createRequest))
            .isInstanceOf(CustomerSelfServiceException.class).hasMessageContaining(VERIFICATION_REQUIRED.name());

        verify(consumerService).createConsumer(eq("customer"), any());
        verify(commands, never()).saveAndFlush(any());
        verify(consumerService, never()).updateBindingForTrustedPromotion(anyString(), anyString(), anyString(),
            anyString(), anyString(), anyString(), anyLong(), anyString());
    }

    @Test void absentReleaseCreatesOnlyAnAuthorizedConfigurationBindingAndReceipt() {
        var configuration = configurationRequest();
        var configured = configurationResult();
        when(consumerService.updateBindingForTrustedPromotion(eq("customer"), eq("app"), eq("deployment"),
            isNull(), eq("staging"), anyString(), eq(0L), eq("create-staging"))).thenReturn(configured);

        assertThat(service.createAndActivate("offer", "deployment", configuration)).isEqualTo(configured);

        var order = inOrder(authorization, consumerService, commands);
        order.verify(authorization).authorize(eq("customer"), argThat(value -> value.operation()
            == com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation.APP_CONFIGURE
            && "offer".equals(value.offerId()) && "deployment".equals(value.deploymentId())
            && "tenant".equals(value.tenantId()) && "staging".equals(value.targetProfileId()) && value.releaseId() == null));
        order.verify(consumerService).createConsumer(eq("customer"), any());
        order.verify(consumerService).updateBindingForTrustedPromotion(eq("customer"), eq("app"), eq("deployment"),
            isNull(), eq("staging"), contains("configured"), eq(0L), eq("create-staging"));
        var saved = ArgumentCaptor.forClass(CustomerEntitlementCommandEntity.class);
        order.verify(commands).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getRequestFingerprint()).isEqualTo(fingerprint(configuration));
        assertThat(codec.read(saved.getValue().getResultJson(), PlatformConsumerSummary.class)).isEqualTo(configured);
        assertThat(CustomerPermissionMatrix.row(
            com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation.APP_CONFIGURE)
            .admissionEvidenceRequired()).isFalse();
        verifyNoInteractions(releases, versions, boundary);
    }

    @Test void configurationReceiptReplaysButCannotBecomeAnActivationWithTheSameKey() {
        var configuration = configurationRequest();
        var configured = configurationResult();
        when(commands.findByEntitlementIdAndOperationKey("grant", "create-staging"))
            .thenReturn(Optional.of(new CustomerEntitlementCommandEntity("receipt", "grant", "create-staging",
                fingerprint(configuration), CREATE_COMMAND, ACTOR, codec.write(configured), now)));

        assertThat(service.createAndActivate("offer", "deployment", configuration)).isEqualTo(configured);
        assertThatThrownBy(() -> service.createAndActivate("offer", "deployment", createRequest))
            .isInstanceOf(CustomerSelfServiceException.class).hasMessageContaining(OPERATION_KEY_CONFLICT.name());

        verifyNoInteractions(consumerService, consumers, releases, versions, authorization, boundary);
        verify(commands, never()).saveAndFlush(any());
    }

    @Test void deniedConfigurationCannotCreateOrBindAConsumer() {
        when(authorization.authorize(eq("customer"), argThat(value -> value != null
            && value.operation() == com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation.APP_CONFIGURE)))
            .thenThrow(deny(OPERATION_FORBIDDEN));

        assertThatThrownBy(() -> service.createAndActivate("offer", "deployment", configurationRequest()))
            .isInstanceOf(CustomerSelfServiceException.class).hasMessageContaining(OPERATION_FORBIDDEN.name());

        verifyNoInteractions(consumerService, consumers, releases, versions, boundary);
        verify(commands, never()).saveAndFlush(any());
    }

    @ParameterizedTest @ValueSource(strings = {"customer", "tenant", "offer", "target"})
    void configurationRequiresTheExactOwnedOfferAssociation(String changed) {
        when(associations.findById("deployment")).thenReturn(Optional.of(new CustomerOfferDeploymentEntity(
            "deployment", "customer".equals(changed) ? "foreign" : "customer",
            "tenant".equals(changed) ? "foreign" : "tenant", "offer".equals(changed) ? "foreign" : "offer",
            "target".equals(changed) ? null : "staging", "fingerprint", 7, "bootstrap", now)));

        assertThatThrownBy(() -> service.createAndActivate("offer", "deployment", configurationRequest()))
            .isInstanceOf(CustomerSelfServiceException.class).hasMessageContaining(RESOURCE_SCOPE_MISMATCH.name());

        verifyNoInteractions(commands, consumerService, consumers, releases, versions, authorization, boundary);
        verify(entitlements, never()).requireEntitlement(anyString(), anyString(), anyString(), anyBoolean());
    }

    @ParameterizedTest @ValueSource(strings = {"", " ", " release "})
    void anExplicitMalformedReleaseCannotBecomeConfigurationOnly(String invalidRelease) {
        assertThatThrownBy(() -> service.createAndActivate("offer", "deployment", new CreateRequest("app", "App",
            "description", invalidRelease, 7, "create-staging")))
            .isInstanceOf(CustomerSelfServiceException.class).hasMessageContaining(INVALID_CONTRACT.name());

        verifyNoInteractions(entitlements, deployments, commands, consumerService, authorization);
    }

    private CreateRequest configurationRequest() {
        return new CreateRequest("app", "App", "description", null, 7, "create-staging");
    }

    private PlatformConsumerSummary configurationResult() {
        return new PlatformConsumerSummary("app", "customer", "App", "description", "ACTIVE", "deployment",
            "Deployment", "staging", "DRAFT", null, null, "staging", now, now, now, 1);
    }

    private CustomerEntitlementCommandEntity receipt(String commandType, String actor) {
        return new CustomerEntitlementCommandEntity("receipt", "grant", "create-staging", fingerprint(createRequest),
            commandType, actor, codec.write(activated), now);
    }

    private String fingerprint(CreateRequest input) {
        return codec.hash(Map.of("command", CREATE_COMMAND, "offerId", "offer", "deploymentId", "deployment",
            "request", input, "customerId", "customer", "ownerId", "owner", "actor", ACTOR));
    }

    private static Stream<CreateRequest> changedCreatePayloads() {
        return Stream.of(new CreateRequest("other-app", "App", "description", "release", 7, "create-staging"),
            new CreateRequest("app", "Different name", "description", "release", 7, "create-staging"),
            new CreateRequest("app", "App", "changed description", "release", 7, "create-staging"),
            new CreateRequest("app", "App", null, "release", 7, "create-staging"),
            new CreateRequest("app", "App", "description", null, 7, "create-staging"),
            new CreateRequest("app", "App", "description", "another-release", 7, "create-staging"),
            new CreateRequest("app", "App", "description", "release", 8, "create-staging"));
    }
}
