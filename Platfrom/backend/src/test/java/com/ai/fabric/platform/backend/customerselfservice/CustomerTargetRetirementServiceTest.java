package com.ai.fabric.platform.backend.customerselfservice;

import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferDeploymentEntity;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerTargetRetirementContracts.PlanRequest;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerTargetRetirementContracts;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.customerselfservice.service.*;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.admission.DeploymentAdmissionService;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.retirement.*;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.*;
import static com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CustomerTargetRetirementServiceTest {
    private final CustomerOfferEntitlementService entitlements = mock(CustomerOfferEntitlementService.class);
    private final CustomerSelfServiceAuthorizationService authorization = mock(CustomerSelfServiceAuthorizationService.class);
    private final CustomerOfferDeploymentRepository associations = mock(CustomerOfferDeploymentRepository.class);
    private final DeploymentRepository deployments = mock(DeploymentRepository.class);
    private final DeploymentReleaseRepository releases = mock(DeploymentReleaseRepository.class);
    private final PlatformConsumerRepository consumers = mock(PlatformConsumerRepository.class);
    private final TargetRetirementOperationRepository operations = mock(TargetRetirementOperationRepository.class);
    private final TargetRetirementService retirements = mock(TargetRetirementService.class);
    private final TargetRetirementStore retirementStore = mock(TargetRetirementStore.class);
    private final DeploymentAdmissionService admissions = mock(DeploymentAdmissionService.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final CustomerOfferCodec codec = CustomerOfferTestFixtures.codec();
    private final CustomerTargetRetirementService service = new CustomerTargetRetirementService(entitlements, authorization,
        associations, deployments, releases, consumers, operations, retirements, retirementStore, admissions, codec, entityManager, transactions);
    private final Instant deadline = Instant.now().plusSeconds(3600);
    private final Retention retention = new Retention(RetentionDecision.RECLAIM, null, null, "rebuild-from-source");
    private boolean preparing;
    private DeploymentEntity deployment;
    private PlatformConsumerEntity staging;
    private PlatformConsumerEntity production;

    @BeforeEach void setup() {
        CustomerOfferTestFixtures.authenticate("owner@test.invalid", PlatformRole.CUSTOMER_ADMIN, "SESSION");
        when(entitlements.requireCustomerActor()).thenReturn(new CustomerOfferEntitlementService.CustomerActor("customer", "owner"));
        when(transactions.getTransaction(any())).thenAnswer(call -> { preparing = true; return new SimpleTransactionStatus(); });
        doAnswer(call -> { preparing = false; return null; }).when(transactions).commit(any());
        doAnswer(call -> { preparing = false; return null; }).when(transactions).rollback(any());
        deployment = new DeploymentEntity();
        deployment.setId("deployment"); deployment.setCustomerId("customer"); deployment.setTenantId("tenant"); deployment.setStatus("ACTIVE");
        when(deployments.findById("deployment")).thenReturn(Optional.of(deployment));
        when(deployments.findByIdForUpdate("deployment")).thenReturn(Optional.of(deployment));
        when(associations.findById("deployment")).thenReturn(Optional.of(new CustomerOfferDeploymentEntity(
            "deployment", "customer", "tenant", "offer", "staging-target", "fingerprint", 2, "bootstrap", Instant.now())));
        release("stage-release", "staging-target");
        release("prod-release", "production-target");
        staging = consumer("consumer-z", "stage-release", "staging-target", 3);
        production = consumer("consumer-a", "prod-release", "production-target", 8);
        when(consumers.findByBoundDeploymentIdOrderByUpdatedAtDesc("deployment")).thenReturn(List.of(staging, production));
        when(consumers.findByBoundReleaseIdOrderByUpdatedAtDesc("stage-release")).thenReturn(List.of(staging));
        when(consumers.findAllByIdForUpdate(List.of("consumer-a", "consumer-z"))).thenReturn(List.of(production, staging));
        when(admissions.activeCustomerReservations("deployment", "staging-target")).thenReturn(List.of());
        when(authorization.authorize(eq("customer"), any())).thenAnswer(call -> {
            assertThat(preparing).as("offer authorization must run in the preparation transaction").isTrue();
            return null;
        });
        when(authorization.authorizeOwnedTargetRead(eq("customer"), any())).thenAnswer(call -> {
            assertThat(preparing).as("target read authorization must run in the preparation transaction").isTrue();
            return null;
        });
        when(authorization.authorizeOperationalRead(eq("customer"), any())).thenAnswer(call -> {
            assertThat(preparing).as("release read authorization must run in the preparation transaction").isTrue();
            return null;
        });
        when(retirements.plan(eq("deployment"), eq("staging-target"), any())).thenAnswer(call -> {
            assertThat(preparing).as("participant/provider planning must run after preparation commits").isFalse();
            return result(call.getArgument(2));
        });
    }

    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    @Test void derivesAllOwnedConsumersAndCurrentRevisionsUnderLockWithoutExposingInternalInventory() {
        var result = service.plan("offer", "deployment", request("Finished staging"));
        var captured = ArgumentCaptor.forClass(TargetRetirementContracts.PlanRequest.class);
        verify(retirements).plan(eq("deployment"), eq("staging-target"), captured.capture());
        assertThat(captured.getValue().consumers()).containsExactly(new ConsumerExpectation("consumer-a", 8),
            new ConsumerExpectation("consumer-z", 3));
        var order = inOrder(consumers, entityManager, deployments, authorization, transactions, retirements);
        order.verify(consumers).findAllByIdForUpdate(List.of("consumer-a", "consumer-z"));
        order.verify(entityManager).refresh(production);
        order.verify(entityManager).refresh(staging);
        order.verify(deployments).findByIdForUpdate("deployment");
        order.verify(entityManager).refresh(deployment);
        order.verify(authorization).authorize(eq("customer"), argThat(command -> command.operation() == Operation.TARGET_RETIRE
            && "offer".equals(command.offerId()) && "staging-target".equals(command.targetProfileId())
            && "stage-release".equals(command.releaseId()) && "retire-key".equals(command.operationKey())));
        order.verify(transactions).commit(any());
        order.verify(retirements).plan(eq("deployment"), eq("staging-target"), any());
        assertThat(codec.write(result)).doesNotContain("consumer-z", "consumer-a", "snapshot", "providerResourceUuid");
        assertThat(result.requestFingerprint()).isEqualTo("planned-fingerprint");
        assertThat(result.blockers()).containsExactly("DRAIN_PENDING");
        assertThat(production.getBoundReleaseId()).isEqualTo("prod-release");
    }

    @Test void refreshesAnEntityAlreadyLoadedDuringDiscoveryBeforeReadingItsRevision() {
        doAnswer(call -> { staging.setBindingRevision(4); return null; }).when(entityManager).refresh(staging);
        service.plan("offer", "deployment", request("Finished staging"));
        verify(retirements).plan(eq("deployment"), eq("staging-target"), argThat(plan ->
            plan.consumers().contains(new ConsumerExpectation("consumer-z", 4))));
    }

    @Test void plansAnUnassignedTargetWithAnEmptyConsumerSnapshot() {
        when(consumers.findByBoundDeploymentIdOrderByUpdatedAtDesc("deployment")).thenReturn(List.of());
        when(consumers.findByBoundReleaseIdOrderByUpdatedAtDesc("stage-release")).thenReturn(List.of());
        service.plan("offer", "deployment", request("Finished staging"));
        verify(retirements).plan(eq("deployment"), eq("staging-target"), argThat(plan -> plan.consumers().isEmpty()));
        verify(consumers, never()).findAllByIdForUpdate(any());
    }

    @Test void activeAdmissionOwnsStagingRetirementAndPreventsASecondCustomerPlan() {
        when(admissions.activeCustomerReservations("deployment", "staging-target"))
            .thenReturn(List.of(mock(com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.Reservation.class)));

        assertCode(ADMISSION_LIFECYCLE_REQUIRED,
            () -> service.plan("offer", "deployment", request("Finished staging")));

        verifyNoInteractions(consumers, retirements);
    }

    @Test void anIdenticalReplayUsesFrozenExpectationsAfterRetirementUnbindsTheConsumer() {
        var frozen = new TargetRetirementContracts.PlanRequest("retire-key", List.of("stage-release"),
            List.of(new ConsumerExpectation("consumer-a", 8), new ConsumerExpectation("consumer-z", 3)),
            deadline, retention, "Finished staging");
        stored(frozen, "owner@test.invalid");
        staging.setBoundDeploymentId(null); staging.setBoundReleaseId(null); staging.setBoundTargetProfileId(null);
        staging.setBindingRevision(4);
        service.plan("offer", "deployment", request("Finished staging"));
        verify(retirements).plan("deployment", "staging-target", frozen);
        verify(consumers, never()).findByBoundDeploymentIdOrderByUpdatedAtDesc(any());
        verify(consumers, never()).findByBoundReleaseIdOrderByUpdatedAtDesc(any());
    }

    @Test void changedRequestCannotReuseThePersistedOperationKey() {
        stored(new TargetRetirementContracts.PlanRequest("retire-key", List.of("stage-release"), List.of(),
            deadline, retention, "Old reason"), "owner@test.invalid");
        assertCode(OPERATION_KEY_CONFLICT, () -> service.plan("offer", "deployment", request("New reason")));
        verifyNoInteractions(retirements);
    }

    @Test void anotherActorCannotReuseAStoredRetirementKey() {
        stored(new TargetRetirementContracts.PlanRequest("retire-key", List.of("stage-release"), List.of(),
            deadline, retention, "Finished staging"), "another-owner@test.invalid");
        assertCode(OPERATION_KEY_CONFLICT, () -> service.plan("offer", "deployment", request("Finished staging")));
        verifyNoInteractions(retirements);
    }

    @Test void aMissingConsumerAtLockTimeFailsClosed() {
        when(consumers.findAllByIdForUpdate(any())).thenReturn(List.of(production));
        assertCode(RESOURCE_SCOPE_MISMATCH, () -> service.plan("offer", "deployment", request("Finished staging")));
        verifyNoInteractions(retirements);
    }

    @Test void aForeignConsumerReferencingTheSelectedReleaseFailsClosed() {
        staging.setCustomerId("foreign-customer");
        assertCode(RESOURCE_SCOPE_MISMATCH, () -> service.plan("offer", "deployment", request("Finished staging")));
        verifyNoInteractions(retirements);
    }

    @Test void aConsumerMovedToAnotherDeploymentCannotBeFrozen() {
        staging.setBoundDeploymentId("other-deployment");
        assertCode(RESOURCE_SCOPE_MISMATCH, () -> service.plan("offer", "deployment", request("Finished staging")));
        verifyNoInteractions(retirements);
    }

    @Test void newlyBoundConsumersRequireARefreshedPlanInsteadOfAnIncompleteSnapshot() {
        when(consumers.findByBoundDeploymentIdOrderByUpdatedAtDesc("deployment"))
            .thenReturn(List.of(staging, production), List.of(staging, production, consumer("new-consumer", "prod-release", "production-target", 1)));
        assertCode(REVISION_CONFLICT, () -> service.plan("offer", "deployment", request("Finished staging")));
        verifyNoInteractions(retirements);
    }

    @Test void foreignOrMissingDeploymentCannotReachTheRetirementPlanner() {
        deployment.setCustomerId("foreign-customer");
        assertCode(RESOURCE_SCOPE_MISMATCH, () -> service.plan("offer", "deployment", request("Finished staging")));
        when(deployments.findById("deployment")).thenReturn(Optional.empty());
        assertCode(RESOURCE_SCOPE_MISMATCH, () -> service.plan("offer", "deployment", request("Finished staging")));
        verifyNoInteractions(retirements);
    }

    @Test void ownershipIsRecheckedFromTheLockedDeploymentInsteadOfTheDiscoveryCache() {
        doAnswer(call -> { deployment.setCustomerId("foreign-customer"); return null; }).when(entityManager).refresh(deployment);
        assertCode(RESOURCE_SCOPE_MISMATCH, () -> service.plan("offer", "deployment", request("Finished staging")));
        verifyNoInteractions(retirements);
    }

    @Test void missingOrForeignOfferAssociationCannotReachTheRetirementPlanner() {
        assertCode(RESOURCE_SCOPE_MISMATCH, () -> service.plan("other-offer", "deployment", request("Finished staging")));
        when(associations.findById("deployment")).thenReturn(Optional.empty());
        assertCode(ENTITLEMENT_REQUIRED, () -> service.plan("offer", "deployment", request("Finished staging")));
        verifyNoInteractions(retirements);
    }

    @Test void foreignMissingOrWrongTargetReleaseCannotReachThePlanner() {
        var foreign = release("stage-release", "another-target");
        assertCode(RESOURCE_SCOPE_MISMATCH, () -> service.plan("offer", "deployment", request("Finished staging")));
        foreign.setDeploymentId("foreign-deployment"); foreign.setTargetProfileId("staging-target");
        assertCode(RESOURCE_SCOPE_MISMATCH, () -> service.plan("offer", "deployment", request("Finished staging")));
        when(releases.findById("stage-release")).thenReturn(Optional.empty());
        assertCode(RESOURCE_SCOPE_MISMATCH, () -> service.plan("offer", "deployment", request("Finished staging")));
        verifyNoInteractions(retirements);
    }

    @Test void currentEntitlementAndTargetAuthorizationAreRequiredEvenForReplays() {
        stored(new TargetRetirementContracts.PlanRequest("retire-key", List.of("stage-release"), List.of(),
            deadline, retention, "Finished staging"), "owner@test.invalid");
        doThrow(CustomerSelfServiceException.deny(ENTITLEMENT_REVOKED)).when(authorization).authorize(any(), any());
        assertCode(ENTITLEMENT_REVOKED, () -> service.plan("offer", "deployment", request("Finished staging")));
        doThrow(CustomerSelfServiceException.deny(UNENTITLED_TARGET)).when(authorization).authorize(any(), any());
        assertCode(UNENTITLED_TARGET, () -> service.plan("offer", "deployment", request("Finished staging")));
        verifyNoInteractions(retirements);
    }

    @Test void malformedAndDuplicateReleaseListsAreRejectedBeforeDiscovery() {
        assertCode(INVALID_CONTRACT, () -> service.plan("offer", "deployment", null));
        assertCode(INVALID_CONTRACT, () -> service.plan("offer", "deployment", new PlanRequest("staging-target",
            List.of("stage-release", "stage-release"), deadline, retention, "retire-key", "Finished staging")));
        verifyNoInteractions(consumers, retirements);
    }

    @Test void statusRevalidatesTheCurrentOfferAndProjectsSafeFieldsForAnOwnedSystemOperation() {
        var operation = operation();
        operation.setRequestedByActorId("system"); operation.setRequestedByRole("SYSTEM");
        when(operations.findForUpdate("retirement")).thenReturn(Optional.of(operation));
        when(retirementStore.summary(operation)).thenAnswer(call -> {
            assertThat(preparing).as("status projects only the authorized locked row").isTrue();
            return result(frozen());
        });
        var actual = service.status("offer", "deployment", "retirement");
        assertThat(actual.id()).isEqualTo("retirement");
        assertThat(codec.write(actual)).doesNotContain("consumer-z", "snapshot", "providerResourceUuid", "operationKey");
        verify(entityManager).refresh(operation);
        verifyNoInteractions(retirements);
        verify(authorization).authorizeOperationalRead(eq("customer"), argThat(command -> command.operation() == Operation.READ
            && "stage-release".equals(command.releaseId()) && "staging-target".equals(command.targetProfileId())));
        verify(authorization, never()).authorize(any(), any());
        var lockOrder = inOrder(deployments, operations);
        lockOrder.verify(deployments).findByIdForUpdate("deployment");
        lockOrder.verify(operations).findForUpdate("retirement");
    }

    @Test void listRequiresTheEntitledTargetAndRechecksEachOperationBeforeProjection() {
        var operation = operation();
        when(operations.findByDeploymentIdAndTargetProfileIdOrderByCreatedAtDesc("deployment", "staging-target"))
            .thenReturn(List.of(operation));
        when(operations.findForUpdate("retirement")).thenReturn(Optional.of(operation));
        when(retirementStore.summary(operation)).thenReturn(result(frozen()));
        assertThat(service.list("offer", "deployment", "staging-target")).singleElement()
            .satisfies(value -> assertThat(value.id()).isEqualTo("retirement"));
        verify(authorization).authorizeOwnedTargetRead(eq("customer"), argThat(command -> command.operation() == Operation.READ
            && command.releaseId() == null && "staging-target".equals(command.targetProfileId())
            && "deployment".equals(command.deploymentId()) && "tenant".equals(command.tenantId())
            && "offer".equals(command.offerId())));
        verify(authorization).authorizeOperationalRead(eq("customer"), argThat(command -> command.operation() == Operation.READ
            && "stage-release".equals(command.releaseId()) && "staging-target".equals(command.targetProfileId())));
        verify(authorization, never()).authorize(any(), any());
        verify(operations).findForUpdate("retirement");
        verifyNoInteractions(consumers, retirements);
    }

    @Test void listFailsClosedWhenAnOperationDisappearsAfterDiscovery() {
        when(operations.findByDeploymentIdAndTargetProfileIdOrderByCreatedAtDesc("deployment", "staging-target"))
            .thenReturn(List.of(operation()));
        assertCode(RESOURCE_SCOPE_MISMATCH, () -> service.list("offer", "deployment", "staging-target"));
        verifyNoInteractions(retirements);
    }

    @Test void listRejectsCrossCustomerRowsInsteadOfProjectingAnUnscopedGenericSummary() {
        var operation = operation(); operation.setCustomerId("foreign");
        when(operations.findByDeploymentIdAndTargetProfileIdOrderByCreatedAtDesc("deployment", "staging-target"))
            .thenReturn(List.of(operation));
        assertCode(RESOURCE_SCOPE_MISMATCH, () -> service.list("offer", "deployment", "staging-target"));
        verifyNoInteractions(retirements);
    }

    @Test void targetListsAndExactStatusUseReadAuthorizationWhileAllRetirementCommandsRemainFreshGated() {
        when(authorization.authorize(eq("customer"), any()))
            .thenThrow(CustomerSelfServiceException.conflict(PLACEMENT_EVIDENCE_STALE));
        var operation = operation();
        when(operations.findByDeploymentIdAndTargetProfileIdOrderByCreatedAtDesc("deployment", "staging-target"))
            .thenReturn(List.of(operation));
        when(operations.findForUpdate("retirement")).thenReturn(Optional.of(operation));
        when(retirementStore.summary(operation)).thenReturn(result(frozen()));

        assertThat(service.list("offer", "deployment", "staging-target")).hasSize(1);
        assertThat(service.status("offer", "deployment", "retirement").id()).isEqualTo("retirement");
        verify(authorization).authorizeOwnedTargetRead(eq("customer"), any());
        verify(authorization, times(2)).authorizeOperationalRead(eq("customer"), any());

        assertCode(PLACEMENT_EVIDENCE_STALE, () -> service.plan("offer", "deployment", request("Finished staging")));
        for (String action : List.of("execute", "retry", "cancel")) {
            assertCode(PLACEMENT_EVIDENCE_STALE, () -> command(action, "planned-fingerprint"));
        }
        verify(authorization, times(4)).authorize(eq("customer"), argThat(request -> request.operation() == Operation.TARGET_RETIRE));
        verify(retirements, never()).plan(any(), any(), any());
        verify(retirements, never()).execute(any(), any(), any(), any());
        verify(retirements, never()).retry(any(), any(), any(), any());
        verify(retirements, never()).cancel(any(), any(), any(), any());
        verify(retirements, never()).status(any(), any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = CustomerSelfServiceException.Code.class, names = {
        "ENTITLEMENT_REVOKED", "REVIEWED_COMPOSITION_CHANGED", "UNENTITLED_TARGET", "PLACEMENT_DENIED"})
    void targetListAndExactStatusPropagateCurrentReadDenials(CustomerSelfServiceException.Code code) {
        when(authorization.authorizeOwnedTargetRead(eq("customer"), any()))
            .thenThrow(CustomerSelfServiceException.deny(code));
        when(authorization.authorizeOperationalRead(eq("customer"), any()))
            .thenThrow(CustomerSelfServiceException.deny(code));
        when(operations.findForUpdate("retirement")).thenReturn(Optional.of(operation()));

        assertCode(code, () -> service.list("offer", "deployment", "staging-target"));
        assertCode(code, () -> service.status("offer", "deployment", "retirement"));
        verify(operations, never()).findByDeploymentIdAndTargetProfileIdOrderByCreatedAtDesc(any(), any());
        verifyNoInteractions(retirements, retirementStore);
    }

    @Test void listRechecksEveryFrozenReleaseAndRejectsWrongTargetRows() {
        var operation = operation();
        var request = new TargetRetirementContracts.PlanRequest("retire-key", List.of("stage-release", "second-release"),
            List.of(), deadline, retention, "Finished staging");
        operation.setRequestJson(codec.write(request));
        release("second-release", "staging-target");
        when(operations.findForUpdate("retirement")).thenReturn(Optional.of(operation));
        when(operations.findByDeploymentIdAndTargetProfileIdOrderByCreatedAtDesc("deployment", "staging-target"))
            .thenReturn(List.of(operation));
        when(retirementStore.summary(operation)).thenReturn(result(request));

        assertThat(service.list("offer", "deployment", "staging-target")).hasSize(1);
        verify(authorization).authorizeOperationalRead(eq("customer"), argThat(value -> "stage-release".equals(value.releaseId())));
        verify(authorization).authorizeOperationalRead(eq("customer"), argThat(value -> "second-release".equals(value.releaseId())));

        clearInvocations(retirements, retirementStore);
        release("second-release", "production-target");
        assertCode(RESOURCE_SCOPE_MISMATCH, () -> service.list("offer", "deployment", "staging-target"));
        operation.setTargetProfileId("production-target");
        assertCode(RESOURCE_SCOPE_MISMATCH, () -> service.list("offer", "deployment", "staging-target"));
        verifyNoInteractions(retirements, retirementStore);
    }

    @Test void everyCommandUsesTheLockedTargetAndFrozenFingerprintAfterPreparationCommits() {
        when(operations.findForUpdate("retirement")).thenReturn(Optional.of(operation()));
        for (String action : List.of("execute", "retry", "cancel")) {
            mockCommand(action, call -> {
                assertThat(preparing).isFalse();
                assertThat(call.getArgument(1, String.class)).isEqualTo("staging-target");
                assertThat(call.getArgument(3, TargetRetirementContracts.Command.class))
                    .isEqualTo(new TargetRetirementContracts.Command("planned-fingerprint"));
                return result(frozen());
            });
            assertThat(command(action, "planned-fingerprint").requestFingerprint()).isEqualTo("planned-fingerprint");
        }
        verify(authorization, times(3)).authorize(eq("customer"), argThat(command -> command.operation() == Operation.TARGET_RETIRE
            && "retire-key".equals(command.operationKey()) && "stage-release".equals(command.releaseId())));
    }

    @Test void systemOwnedAdmissionRetirementIsVisibleButCannotBeDrivenByCustomerCommands() {
        var operation = operation();
        operation.setRequestedByActorId("system:admission-lifecycle");
        operation.setRequestedByRole("SYSTEM");
        when(operations.findForUpdate("retirement")).thenReturn(Optional.of(operation));
        when(retirementStore.summary(operation)).thenReturn(result(frozen()));

        assertThat(service.status("offer", "deployment", "retirement").customerCommandsAllowed()).isFalse();
        assertCode(ADMISSION_LIFECYCLE_REQUIRED,
            () -> service.execute("offer", "deployment", "retirement",
                new CustomerTargetRetirementContracts.Command("planned-fingerprint")));

        verify(retirements, never()).execute(any(), any(), any(), any());
    }

    @Test void repeatedExecuteKeepsTheSameFingerprintAndRevalidatesWithoutRebuildingConsumers() {
        when(operations.findForUpdate("retirement")).thenReturn(Optional.of(operation()));
        when(retirements.execute(any(), any(), any(), any())).thenReturn(result(frozen()));
        assertThat(command("execute", "planned-fingerprint")).isEqualTo(command("execute", "planned-fingerprint"));
        verify(operations, times(2)).findForUpdate("retirement");
        verify(authorization, times(2)).authorize(eq("customer"), argThat(value -> value.operation() == Operation.TARGET_RETIRE));
        verifyNoInteractions(consumers);
    }

    @Test void wrongAndMissingFingerprintsNeverReachTheGenericCommands() {
        when(operations.findForUpdate("retirement")).thenReturn(Optional.of(operation()));
        for (String action : List.of("execute", "retry", "cancel")) {
            assertCode(REVISION_CONFLICT, () -> command(action, "stale-fingerprint"));
            assertCode(INVALID_CONTRACT, () -> command(action, null));
            assertCode(INVALID_CONTRACT, () -> command(action, ""));
        }
        verifyNoInteractions(retirements);
    }

    @Test void statusAndCommandsRejectForeignCustomerDeploymentOrReleaseOwnership() {
        for (String changedField : List.of("customer", "deployment", "release")) {
            var operation = operation();
            if (changedField.equals("customer")) operation.setCustomerId("foreign-customer");
            if (changedField.equals("deployment")) operation.setDeploymentId("foreign-deployment");
            if (changedField.equals("release")) release("stage-release", "foreign-target");
            when(operations.findForUpdate("retirement")).thenReturn(Optional.of(operation));
            assertCode(RESOURCE_SCOPE_MISMATCH, () -> service.status("offer", "deployment", "retirement"));
            for (String action : List.of("execute", "retry", "cancel")) {
                assertCode(RESOURCE_SCOPE_MISMATCH, () -> command(action, "planned-fingerprint"));
            }
        }
        verifyNoInteractions(retirements);
    }

    @Test void existingOperationRoutesRequireCurrentCustomerScopeAssociationAndEntitlement() {
        when(operations.findForUpdate("retirement")).thenReturn(Optional.of(operation()));
        doThrow(CustomerSelfServiceException.deny(ENTITLEMENT_REVOKED)).when(authorization).authorize(any(), any());
        doThrow(CustomerSelfServiceException.deny(ENTITLEMENT_REVOKED)).when(authorization).authorizeOperationalRead(any(), any());
        assertCode(ENTITLEMENT_REVOKED, () -> service.status("offer", "deployment", "retirement"));
        for (String action : List.of("execute", "retry", "cancel")) {
            assertCode(ENTITLEMENT_REVOKED, () -> command(action, "planned-fingerprint"));
        }
        reset(authorization);
        when(associations.findById("deployment")).thenReturn(Optional.empty());
        assertCode(ENTITLEMENT_REQUIRED, () -> service.status("offer", "deployment", "retirement"));
        doThrow(CustomerSelfServiceException.deny(ACTIVE_CUSTOMER_SCOPE_REQUIRED)).when(entitlements).requireCustomerActor();
        assertCode(ACTIVE_CUSTOMER_SCOPE_REQUIRED, () -> service.status("offer", "deployment", "retirement"));
        verifyNoInteractions(retirements);
    }

    @Test void ownershipChangesDuringOperationRefreshAreRejectedBeforeCallingTheGenericService() {
        var operation = operation();
        when(operations.findForUpdate("retirement")).thenReturn(Optional.of(operation));
        doAnswer(call -> { operation.setCustomerId("foreign-customer"); return null; }).when(entityManager).refresh(operation);
        assertCode(RESOURCE_SCOPE_MISMATCH, () -> command("execute", "planned-fingerprint"));
        verifyNoInteractions(retirements);
    }

    @Test void genericRaceFailuresPropagateWithoutAHiddenRetryOrReplacementPlan() {
        when(operations.findForUpdate("retirement")).thenReturn(Optional.of(operation()));
        var failure = new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
            "TARGET_GENERATION_CHANGED");
        when(retirements.execute(any(), any(), any(), any())).thenThrow(failure);
        assertThatThrownBy(() -> command("execute", "planned-fingerprint")).isSameAs(failure);
        verify(retirements, times(1)).execute(any(), any(), any(), any());
        verify(retirements, never()).plan(any(), any(), any());
        verify(retirements, never()).retry(any(), any(), any(), any());
    }

    @Test void responseFingerprintIsCheckedBeforeProjectingTheStatus() {
        when(operations.findForUpdate("retirement")).thenReturn(Optional.of(operation()));
        var expected = result(frozen());
        var changed = new TargetRetirementContracts.Summary(expected.id(), expected.deploymentId(), expected.targetProfileId(),
            expected.customerId(), expected.operationKey(), "changed-fingerprint", expected.state(), expected.step(), expected.snapshot(),
            expected.drainDeadline(), expected.retention(), expected.attempt(), expected.nextRetryAt(), expected.errorCode(), expected.result(),
            expected.consumesCapacity(), expected.inFlightEffect(), expected.createdAt(), expected.startedAt(), expected.completedAt(), expected.updatedAt());
        when(retirementStore.summary(any())).thenReturn(changed);
        assertCode(REVISION_CONFLICT, () -> service.status("offer", "deployment", "retirement"));
    }

    private CustomerTargetRetirementContracts.Summary command(String action, String fingerprint) {
        var command = fingerprint == null ? null : new CustomerTargetRetirementContracts.Command(fingerprint);
        return switch (action) {
            case "execute" -> service.execute("offer", "deployment", "retirement", command);
            case "retry" -> service.retry("offer", "deployment", "retirement", command);
            default -> service.cancel("offer", "deployment", "retirement", command);
        };
    }

    private void mockCommand(String action, org.mockito.stubbing.Answer<TargetRetirementContracts.Summary> answer) {
        switch (action) {
            case "execute" -> when(retirements.execute(any(), any(), any(), any())).thenAnswer(answer);
            case "retry" -> when(retirements.retry(any(), any(), any(), any())).thenAnswer(answer);
            default -> when(retirements.cancel(any(), any(), any(), any())).thenAnswer(answer);
        }
    }

    private TargetRetirementContracts.PlanRequest frozen() {
        return new TargetRetirementContracts.PlanRequest("retire-key", List.of("stage-release"),
            List.of(new ConsumerExpectation("consumer-z", 3)), deadline, retention, "Finished staging");
    }

    private TargetRetirementOperationEntity operation() {
        var operation = new TargetRetirementOperationEntity();
        operation.setId("retirement"); operation.setCustomerId("customer"); operation.setDeploymentId("deployment");
        operation.setTargetProfileId("staging-target"); operation.setOperationKey("retire-key");
        operation.setRequestedByActorId("owner@test.invalid"); operation.setRequestedByRole("CUSTOMER_ADMIN");
        operation.setRequestFingerprint("planned-fingerprint"); operation.setRequestJson(codec.write(frozen()));
        return operation;
    }

    private PlanRequest request(String reason) {
        return new PlanRequest("staging-target", List.of("stage-release"), deadline, retention, "retire-key", reason);
    }

    private void stored(TargetRetirementContracts.PlanRequest request, String actor) {
        var entity = new TargetRetirementOperationEntity();
        entity.setId("retirement"); entity.setCustomerId("customer"); entity.setRequestedByActorId(actor);
        entity.setRequestedByRole("CUSTOMER_ADMIN"); entity.setRequestJson(codec.write(request));
        when(operations.findByDeploymentIdAndTargetProfileIdAndOperationKey("deployment", "staging-target", "retire-key"))
            .thenReturn(Optional.of(entity));
    }

    private DeploymentReleaseEntity release(String id, String target) {
        var release = new DeploymentReleaseEntity();
        release.setId(id); release.setDeploymentId("deployment"); release.setTargetProfileId(target);
        when(releases.findById(id)).thenReturn(Optional.of(release));
        return release;
    }

    private PlatformConsumerEntity consumer(String id, String releaseId, String target, long revision) {
        var consumer = new PlatformConsumerEntity();
        consumer.setId(id); consumer.setCustomerId("customer"); consumer.setStatus("ACTIVE");
        consumer.setBoundDeploymentId("deployment"); consumer.setBoundReleaseId(releaseId);
        consumer.setBoundTargetProfileId(target); consumer.setBindingRevision(revision);
        return consumer;
    }

    private TargetRetirementContracts.Summary result(TargetRetirementContracts.PlanRequest request) {
        var snapshot = new Snapshot("customer", "tenant", "deployment", "staging-target", "generation", 1,
            List.of(), List.of(), request.consumers());
        return new TargetRetirementContracts.Summary("retirement", "deployment", "staging-target", "customer",
            request.operationKey(), "planned-fingerprint", State.PLANNED, Step.DRAIN, snapshot, request.drainDeadline(),
            request.retention(), 0, null, null, new Progress(Map.of(), List.of("DRAIN_PENDING")), false, null,
            Instant.now(), null, null, Instant.now());
    }

    private void assertCode(CustomerSelfServiceException.Code code, Runnable operation) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(CustomerSelfServiceException.class,
            error -> assertThat(error.code()).isEqualTo(code));
    }
}
