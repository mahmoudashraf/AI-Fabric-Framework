package com.ai.fabric.runtime.budget;

import static com.ai.fabric.runtime.budget.RuntimeBudgetBindingException.Code.*;
import static com.ai.fabric.runtime.budget.RuntimeBudgetBindingService.State.*;
import static com.ai.fabric.runtime.budget.RuntimeBudgetFixture.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ai.fabric.budget.BudgetException;
import com.ai.fabric.budget.JdbcProviderBudgetLedger;
import com.ai.fabric.budget.ProviderBudgetLedger;
import com.ai.fabric.budget.ProviderBudgetLedger.Approval;
import com.ai.fabric.budget.ProviderBudgetLedger.Limits;
import com.ai.fabric.budget.ProviderBudgetLedger.Scope;
import com.ai.fabric.budget.ProviderBudgetLedger.Usage;
import com.ai.fabric.runtime.auth.RuntimeResolvedIdentity;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

class RuntimeBudgetBindingServiceTest extends RuntimeBudgetFixture.TestScope {
    @Test
    void bindingSchemaAcceptsTheDurableInitializingClaimUnderItsRowLock() throws Exception {
        var fixture = new RuntimeBudgetFixture();
        try (var connection = fixture.dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var query = connection.prepareStatement("SELECT binding_state FROM runtime_provider_budget_binding WHERE singleton_id = 1 FOR UPDATE")) {
                try (var rows = query.executeQuery()) { assertThat(rows.next()).isTrue(); }
            }
            try (var update = connection.prepareStatement("UPDATE runtime_provider_budget_binding SET binding_state = ?, store_uuid = ?,"
                + " approval_id = ?, allocation_fingerprint = ? WHERE singleton_id = 1")) {
                update.setString(1, "INITIALIZING"); update.setString(2, UUID.randomUUID().toString());
                update.setString(3, APPROVAL.id()); update.setString(4, ALLOCATION.runtimeFingerprint());
                assertThat(update.executeUpdate()).isEqualTo(1);
            }
            connection.rollback();
        }
    }

    @Test
    void matchesTheControlPlaneGoldenAllocationIncludingNanosecondExpiryWithoutHashAliases() {
        var approval = new Approval("pua-fixture",
            new Scope("customer", "deployment", "target", "staging", "sha256:" + "a".repeat(64), "fixture-rate-card"),
            new Limits(50, 100_000, 2_000_000_000L), Instant.parse("2030-01-01T00:00:00.123456789Z"));
        String expected = "bb50b722cd30aeb4de14d10a88696ed733468ce8c2ee23e9b5a014d9e4c25999";

        assertThat(RuntimeBudgetAllocation.fingerprint(approval)).isEqualTo(expected);
        assertThat(new RuntimeBudgetAllocation(approval, expected).runtimeFingerprint()).isEqualTo(expected);
        assertBinding(ALLOCATION_FINGERPRINT_MISMATCH,
            () -> new RuntimeBudgetAllocation(approval, RuntimeBudgetAllocation.fingerprint(APPROVAL)));
    }

    @Test
    void disabledConfigurationDoesNotAccessOrRegisterAnything() {
        var ledger = mock(ProviderBudgetLedger.class);
        var dataSource = mock(DataSource.class);
        var service = new RuntimeBudgetBindingService(RuntimeBudgetConfiguration.disabled(), ledger, dataSource, CLOCK);
        assertThat(service.readiness().ready()).isTrue();
        assertBinding(DISABLED, service::readCurrent);
        verifyNoInteractions(ledger, dataSource);
    }

    @Test
    void uninitializedBootRemainsAvailableForAdministrationWithoutIssuingAnApproval() throws Exception {
        var fixture = new RuntimeBudgetFixture();
        var service = fixture.service();
        assertThat(service.readiness()).isEqualTo(new RuntimeBudgetBindingService.Readiness(false, "NOT_INITIALIZED"));
        assertThat(service.readStore().binding().state()).isEqualTo(UNINITIALIZED);
        assertThat(service.readStore().binding().storeUuid()).isNull();
        assertBinding(NOT_INITIALIZED, service::requireActive);
        assertBudget(BudgetException.Code.NOT_FOUND, () -> fixture.ledger.read(APPROVAL.id(), SCOPE));
    }

    @Test
    void missingSchemaIsReadinessDownNotAConstructorFailureOrSchemaBootstrap() {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL", "sa", "");
        var service = new RuntimeBudgetBindingService(new RuntimeBudgetConfiguration(true, SCOPE, "tenant"),
            new JdbcProviderBudgetLedger(dataSource, CLOCK), dataSource, CLOCK);
        assertThat(service.readiness()).isEqualTo(new RuntimeBudgetBindingService.Readiness(false, "STORAGE_UNAVAILABLE"));
    }

    @Test
    void privateInitializationIsNotAuthorityToTransmitUntilExactActivation() throws Exception {
        var service = new RuntimeBudgetFixture().service();
        var receipt = service.initialize(ALLOCATION);
        assertThat(receipt.state()).isEqualTo(INITIALIZED);
        assertThat(receipt.storeUuid()).isNotNull();
        assertBinding(NOT_ACTIVE, service::requireActive);
        assertThat(service.readiness().ready()).isFalse();
        var activated = activate(service, receipt);
        assertThat(activated.state()).isEqualTo(ACTIVE);
        assertThat(activated.storeUuid()).isEqualTo(receipt.storeUuid());
        assertThat(activate(service, receipt)).isEqualTo(activated);
        assertThat(service.readiness().ready()).isTrue();
    }

    @Test
    void initializeReplayAndRestartPreserveTheSameStoreAndConsumedUnknownHolds() throws Exception {
        var fixture = new RuntimeBudgetFixture();
        var service = fixture.service();
        var initialized = service.initialize(ALLOCATION);
        assertThat(service.initialize(ALLOCATION)).isEqualTo(initialized);
        var activated = activate(service, initialized);
        service.recordVerifiedChatIngress(chatIdentity());
        UUID attempt = UUID.randomUUID();
        fixture.ledger.reserveAttempt(APPROVAL.id(), SCOPE, attempt, new Usage(100, 10, 1000));
        fixture.ledger.markAmbiguous(APPROVAL.id(), SCOPE, attempt, "NATIVE_EXCHANGE_UNVERIFIED");
        var before = service.readCurrent();
        var restarted = fixture.service();
        assertThat(restarted.readiness().ready()).isTrue();
        assertThat(restarted.initialize(ALLOCATION)).isEqualTo(activated);
        assertThat(activate(restarted, initialized)).isEqualTo(activated);
        assertThat(restarted.readCurrent()).isEqualTo(before);
    }

    @Test
    void initializedBindingRejectsNewIdsCapsExpiryAndWrongActivationProof() throws Exception {
        var service = new RuntimeBudgetFixture().service();
        var receipt = service.initialize(ALLOCATION);
        for (Approval changed : List.of(
            new Approval("new-budget", SCOPE, APPROVAL.limits(), APPROVAL.expiresAt()),
            new Approval(APPROVAL.id(), SCOPE, new Limits(50, 200_000, 2_000_000_000L), APPROVAL.expiresAt()),
            new Approval(APPROVAL.id(), SCOPE, APPROVAL.limits(), APPROVAL.expiresAt().plusSeconds(1)))) {
            assertBinding(BINDING_CONFLICT, () -> service.initialize(allocation(changed)));
        }
        for (var wrong : List.of(
            new RuntimeBudgetBindingService.Activation("other", receipt.storeUuid(), receipt.runtimeFingerprint()),
            new RuntimeBudgetBindingService.Activation(receipt.approvalId(), UUID.randomUUID(), receipt.runtimeFingerprint()),
            new RuntimeBudgetBindingService.Activation(receipt.approvalId(), receipt.storeUuid(), "b".repeat(64)))) {
            assertBinding(BINDING_CONFLICT, () -> service.activate(wrong));
        }
        assertThat(service.readStore().binding()).isEqualTo(receipt);
        assertBinding(NOT_ACTIVE, service::requireActive);
    }

    @Test
    void allScopeFieldsIncludingTargetAndEnvironmentMustMatchStartupExactly() throws Exception {
        var service = new RuntimeBudgetFixture().service();
        for (Scope changed : List.of(
            new Scope("other", "deployment", "target", "staging", CREDENTIAL, SCOPE.rateCardReference()),
            new Scope("customer", "other", "target", "staging", CREDENTIAL, SCOPE.rateCardReference()),
            new Scope("customer", "deployment", "other", "staging", CREDENTIAL, SCOPE.rateCardReference()),
            new Scope("customer", "deployment", "target", "production", CREDENTIAL, SCOPE.rateCardReference()),
            new Scope("customer", "deployment", "target", "staging", "sha256:" + "b".repeat(64), SCOPE.rateCardReference()),
            new Scope("customer", "deployment", "target", "staging", CREDENTIAL, "other-card"))) {
            var approval = new Approval(APPROVAL.id(), changed, APPROVAL.limits(), APPROVAL.expiresAt());
            assertBinding(ALLOCATION_SCOPE_MISMATCH, () -> service.initialize(allocation(approval)));
        }
        assertThat(service.readStore().binding().storeUuid()).isNull();
    }

    @Test
    void noncanonicalAndFingerprintMismatchedProjectionsAreRejected() {
        for (Scope scope : List.of(
            new Scope(" customer", "deployment", "target", "staging", CREDENTIAL, SCOPE.rateCardReference()),
            new Scope("customer", "deployment", "target", "staging\n", CREDENTIAL, SCOPE.rateCardReference()),
            new Scope("customer", "deployment", "target", "staging", "raw-secret", SCOPE.rateCardReference()))) {
            assertThatThrownBy(() -> new RuntimeBudgetConfiguration(true, scope, "tenant")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("raw-secret");
        }
        assertBinding(ALLOCATION_FINGERPRINT_MISMATCH, () -> new RuntimeBudgetAllocation(APPROVAL, "0".repeat(64)));
        assertThat(RuntimeBudgetAllocation.fingerprint(APPROVAL))
            .isEqualTo("aed33993f88af73aca5a97155b20c9e2c1115175805b6988e81afbfbe238c524");
    }

    @Test
    void concurrentIdenticalInitializationsAndActivationsHaveOnePersistentStoreUuid() throws Exception {
        var fixture = new RuntimeBudgetFixture();
        var service = fixture.service();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> service.initialize(ALLOCATION));
            var retry = executor.submit(() -> fixture.service().initialize(ALLOCATION));
            var receipt = first.get(5, TimeUnit.SECONDS);
            assertThat(retry.get(5, TimeUnit.SECONDS)).isEqualTo(receipt);
            var activation = executor.submit(() -> activate(service, receipt));
            var replay = executor.submit(() -> activate(fixture.service(), receipt));
            assertThat(activation.get(5, TimeUnit.SECONDS)).isEqualTo(replay.get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void lostStoreCannotReactivateTheControlPlanesPreviouslyRecordedReceiptOrResetOnBoot() throws Exception {
        var original = new RuntimeBudgetFixture().register();
        original.recordVerifiedChatIngress(chatIdentity());
        var prior = original.readStore().binding();
        var lost = new RuntimeBudgetFixture();
        var restoredEmpty = lost.service();
        assertThat(restoredEmpty.readiness().ready()).isFalse();
        assertBinding(NOT_INITIALIZED, () -> activate(restoredEmpty, prior));
        assertThat(restoredEmpty.readStore().binding().storeUuid()).isNull();
        assertBudget(BudgetException.Code.NOT_FOUND, () -> lost.ledger.read(APPROVAL.id(), SCOPE));
        // The CP's durable initialized receipt forbids calling initialize again without recovery review.
    }

    @Test
    void completedInitializationNeverRecreatesALostLedgerEvenOnExactPrivateReplay() throws Exception {
        var fixture = new RuntimeBudgetFixture();
        var service = fixture.register();
        var jdbc = new JdbcTemplate(fixture.dataSource);
        jdbc.update("DELETE FROM provider_budget_approval WHERE approval_id = ?", APPROVAL.id());
        assertThat(service.readiness()).isEqualTo(new RuntimeBudgetBindingService.Readiness(false, "NOT_FOUND"));
        assertBudget(BudgetException.Code.NOT_FOUND, () -> service.initialize(ALLOCATION));
        assertBudget(BudgetException.Code.NOT_FOUND, () -> fixture.ledger.read(APPROVAL.id(), SCOPE));
    }

    @Test
    void interruptedInitializationRetainsItsClaimAndExactRetryCanCompleteBeforeAnyActivation() throws Exception {
        var fixture = new RuntimeBudgetFixture();
        var ledger = mock(ProviderBudgetLedger.class);
        when(ledger.approve(APPROVAL)).thenThrow(new BudgetException(BudgetException.Code.STORAGE_UNAVAILABLE))
            .thenAnswer(invocation -> fixture.ledger.approve(APPROVAL));
        when(ledger.read(APPROVAL.id(), SCOPE)).thenAnswer(invocation -> fixture.ledger.read(APPROVAL.id(), SCOPE));
        var service = new RuntimeBudgetBindingService(new RuntimeBudgetConfiguration(true, SCOPE, "tenant"), ledger, fixture.dataSource, CLOCK);
        assertBudget(BudgetException.Code.STORAGE_UNAVAILABLE, () -> service.initialize(ALLOCATION));
        var pending = service.readStore().binding();
        assertThat(pending.state()).isEqualTo(INITIALIZING);
        assertThat(service.readiness().ready()).isFalse();
        assertBinding(NOT_ACTIVE, () -> activate(service, pending));
        var completed = service.initialize(ALLOCATION);
        assertThat(completed.state()).isEqualTo(INITIALIZED);
        assertThat(completed.storeUuid()).isEqualTo(pending.storeUuid());
        assertThat(activate(service, completed).state()).isEqualTo(ACTIVE);
    }

    @Test
    void initializationAndActivationDoNotNestLedgerConnectionsUnderTheBindingLock() throws Exception {
        var fixture = new RuntimeBudgetFixture();
        var inUse = new AtomicInteger();
        var pool = mock(DataSource.class);
        when(pool.getConnection()).thenAnswer(invocation -> {
            if (inUse.incrementAndGet() != 1) {
                inUse.decrementAndGet();
                throw new SQLException("single connection pool exhausted");
            }
            Connection physical = fixture.dataSource.getConnection();
            return Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                try {
                    return method.invoke(physical, args);
                } catch (InvocationTargetException error) {
                    throw error.getCause();
                } finally {
                    if (method.getName().equals("close")) inUse.decrementAndGet();
                }
            });
        });
        var service = new RuntimeBudgetBindingService(new RuntimeBudgetConfiguration(true, SCOPE, "tenant"),
            new JdbcProviderBudgetLedger(pool, CLOCK), pool, CLOCK);
        var initialized = service.initialize(ALLOCATION);
        activate(service, initialized);
        assertThat(service.requireActive().approval()).isEqualTo(APPROVAL);
        assertThat(inUse).hasValue(0);
    }

    @Test
    void foreignMissingExpiredAndUnscopedIdentitiesCannotIncrementTheLedger() throws Exception {
        var service = new RuntimeBudgetFixture().register();
        for (RuntimeResolvedIdentity identity : List.of(
            identity("foreign", "deployment", List.of("chat:query")),
            identity("customer", "foreign", List.of("chat:query")), identity("customer", "deployment", List.of()),
            RuntimeResolvedIdentity.builder().build(),
            chatIdentity().toBuilder().authContext(chatIdentity().getAuthContext().toBuilder().expiresAt(NOW).build()).build())) {
            assertThatThrownBy(() -> service.recordVerifiedChatIngress(identity)).isInstanceOf(ResponseStatusException.class);
        }
        assertThat(service.readCurrent().chatAttempts()).isZero();
    }

    @Test
    void retryGetsFreshServerAttemptAndLastAdmittedRequestCanFinishWithoutCampaignStop() throws Exception {
        var service = new RuntimeBudgetFixture().service();
        var limited = new Approval(APPROVAL.id(), SCOPE, new Limits(2, 100_000, 2_000_000_000L), APPROVAL.expiresAt());
        activate(service, service.initialize(allocation(limited)));
        var first = service.recordVerifiedChatIngress(chatIdentity());
        var retry = service.recordVerifiedChatIngress(chatIdentity());
        assertThat(first.serverAttemptId()).isNotEqualTo(retry.serverAttemptId());
        assertThat(retry.ordinal()).isEqualTo(2);
        assertThat(service.requireActive().chatAttempts()).isEqualTo(2);
        assertBudget(BudgetException.Code.LIMIT_EXCEEDED, () -> service.recordVerifiedChatIngress(chatIdentity()));
        assertThat(service.readCurrent().stopped()).isFalse();
    }

    @Test
    void oversizedHoldDeniesOnlyThatAttemptAndUnknownHoldStillConsumesCapacity() throws Exception {
        var fixture = new RuntimeBudgetFixture();
        var service = fixture.register();
        assertBudget(BudgetException.Code.LIMIT_EXCEEDED, () -> fixture.ledger.reserveAttempt(APPROVAL.id(), SCOPE,
            UUID.randomUUID(), new Usage(128_000, 800, 1)));
        assertThat(service.requireActive().stopped()).isFalse();
        UUID unknown = UUID.randomUUID();
        fixture.ledger.reserveAttempt(APPROVAL.id(), SCOPE, unknown, new Usage(90_000, 0, 1));
        fixture.ledger.markAmbiguous(APPROVAL.id(), SCOPE, unknown, "NATIVE_EXCHANGE_UNVERIFIED");
        assertBudget(BudgetException.Code.LIMIT_EXCEEDED, () -> fixture.ledger.reserveAttempt(APPROVAL.id(), SCOPE,
            UUID.randomUUID(), new Usage(10_001, 0, 1)));
        assertThat(service.readCurrent().accountedUsage().totalTokens()).isEqualTo(90_000);
        assertThat(fixture.ledger.reserveAttempt(APPROVAL.id(), SCOPE, UUID.randomUUID(), new Usage(1, 0, 1)).newlyReserved()).isTrue();
    }

    @Test
    void expiredAndStoppedApprovalsRemainReadableButCannotAuthorizeOrActivate() throws Exception {
        var fixture = new RuntimeBudgetFixture();
        var service = fixture.service();
        var initialized = service.initialize(ALLOCATION);
        var expired = new RuntimeBudgetBindingService(new RuntimeBudgetConfiguration(true, SCOPE, "tenant"), fixture.ledger,
            fixture.dataSource, Clock.fixed(APPROVAL.expiresAt(), ZoneOffset.UTC));
        assertBudget(BudgetException.Code.EXPIRED, () -> activate(expired, initialized));
        assertThat(expired.readCurrent().approval()).isEqualTo(APPROVAL);
        activate(service, initialized);
        fixture.ledger.stop(APPROVAL.id(), SCOPE, "OWNER_STOP");
        assertBudget(BudgetException.Code.STOPPED, service::requireActive);
        assertThat(service.readiness().ready()).isFalse();
    }

    private static void assertBinding(RuntimeBudgetBindingException.Code code, org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(RuntimeBudgetBindingException.class, error -> assertThat(error.code()).isEqualTo(code));
    }
    private static void assertBudget(BudgetException.Code code, org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(BudgetException.class, error -> assertThat(error.code()).isEqualTo(code));
    }
}
