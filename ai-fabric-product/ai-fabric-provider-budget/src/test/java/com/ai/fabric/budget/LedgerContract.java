package com.ai.fabric.budget;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.UnaryOperator;

import static com.ai.fabric.budget.BudgetException.Code.*;
import static com.ai.fabric.budget.ProviderBudgetLedger.*;
import static org.junit.jupiter.api.Assertions.*;

abstract class LedgerContract {
    DataSource dataSource;
    MutableClock clock;
    JdbcProviderBudgetLedger ledger;
    final Scope scope = new Scope("customer", "deployment", "target-a", "staging", "credential-identity", "rate-card-v1");

    protected abstract DataSource freshDataSource() throws Exception;

    @BeforeEach void initialize() throws Exception {
        dataSource = freshDataSource();
        clock = new MutableClock(Instant.parse("2026-10-18T12:00:00.123456789Z"));
        try (var input = getClass().getResourceAsStream("/com/ai/fabric/budget/provider-budget-schema.sql")) {
            assertNotNull(input);
            String schema = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
                for (String sql : schema.split(";")) if (!sql.isBlank()) statement.execute(sql);
            }
        }
        ledger = new JdbcProviderBudgetLedger(dataSource, clock);
    }

    Approval approval(Limits limits) { return new Approval("approval", scope, limits, clock.instant().plusSeconds(3600)); }
    void approve() { ledger.approve(approval(new Limits(8, 100, 1_000))); }
    Usage usage(long input, long output, long nanos) { return new Usage(input, output, nanos); }
    void denied(BudgetException.Code code, org.junit.jupiter.api.function.Executable work) {
        assertEquals(code, assertThrows(BudgetException.class, work).code());
    }

    @Test void approvalIsImmutableAndExactReplayNeverResetsCounters() {
        Approval approved = approval(new Limits(8, 100, 1_000));
        ledger.approve(approved);
        ledger.recordChatIngress(approved.id(), scope, UUID.randomUUID());
        ledger.reserveAttempt(approved.id(), scope, UUID.randomUUID(), usage(20, 10, 80));
        Snapshot replay = new JdbcProviderBudgetLedger(dataSource, clock).approve(approved);
        assertEquals(1, replay.chatAttempts()); assertEquals(1, replay.providerAttempts());
        assertEquals(usage(20, 10, 80), replay.accountedUsage());
        for (Limits changed : List.of(new Limits(9, 100, 1_000), new Limits(8, 101, 1_000), new Limits(8, 100, 1_001), new Limits(0, 0, 0)))
            denied(APPROVAL_CONFLICT, () -> ledger.approve(new Approval(approved.id(), scope, changed, approved.expiresAt())));
        denied(APPROVAL_CONFLICT, () -> ledger.approve(new Approval(approved.id(), scope, approved.limits(), approved.expiresAt().plusNanos(1))));
        for (Scope changed : changedScopes())
            denied(APPROVAL_CONFLICT, () -> ledger.approve(new Approval(approved.id(), changed, approved.limits(), approved.expiresAt())));
        assertEquals(replay, ledger.read(approved.id(), scope));
    }

    @Test void ingressRetriesUseFreshServerIdsAndLimitDenialDoesNotStopOtherValidWork() {
        ledger.approve(approval(new Limits(2, 100, 1_000)));
        UUID first = UUID.randomUUID();
        assertEquals(new ChatReceipt(first, 1, true), ledger.recordChatIngress("approval", scope, first));
        assertEquals(new ChatReceipt(first, 1, false), ledger.recordChatIngress("approval", scope, first));
        assertEquals(2, ledger.recordChatIngress("approval", scope, UUID.randomUUID()).ordinal());
        denied(LIMIT_EXCEEDED, () -> ledger.recordChatIngress("approval", scope, UUID.randomUUID()));
        Snapshot snapshot = ledger.read("approval", scope);
        assertEquals(2, snapshot.chatAttempts()); assertFalse(snapshot.stopped()); assertNull(snapshot.stopReason());
        assertTrue(ledger.reserveAttempt("approval", scope, UUID.randomUUID(), usage(1, 1, 1)).newlyReserved());
    }

    @Test void exactReservationReplayIsNotAnotherSendPermitAndChangedPayloadDenies() {
        approve(); UUID id = UUID.randomUUID(); Usage bound = usage(20, 10, 80);
        assertTrue(ledger.reserveAttempt("approval", scope, id, bound).newlyReserved());
        ReservationReceipt replay = new JdbcProviderBudgetLedger(dataSource, clock).reserveAttempt("approval", scope, id, bound);
        assertFalse(replay.newlyReserved()); assertEquals(AttemptState.RESERVED, replay.attempt().state());
        denied(ATTEMPT_CONFLICT, () -> ledger.reserveAttempt("approval", scope, id, usage(21, 10, 80)));
        assertEquals(1, ledger.read("approval", scope).providerAttempts());
        assertEquals(bound, ledger.read("approval", scope).accountedUsage());
    }

    @Test void settlementIsExactOnceAndConservativeAccountedCostIsRetained() {
        approve(); UUID id = UUID.randomUUID(); Usage bound = usage(20, 10, 80); Usage settled = usage(12, 6, 70);
        ledger.reserveAttempt("approval", scope, id, bound);
        Attempt result = ledger.settle("approval", scope, id, settled, "native-usage-plus-conservative-rate-card");
        assertEquals(AttemptState.SETTLED, result.state()); assertEquals(settled, result.settledUsage());
        assertEquals(result, ledger.settle("approval", scope, id, settled, "native-usage-plus-conservative-rate-card"));
        denied(SETTLEMENT_CONFLICT, () -> ledger.settle("approval", scope, id, usage(11, 6, 70), "native-usage-plus-conservative-rate-card"));
        denied(SETTLEMENT_CONFLICT, () -> ledger.settle("approval", scope, id, settled, "different-evidence"));
        assertEquals(settled, ledger.read("approval", scope).accountedUsage());
        assertFalse(ledger.reserveAttempt("approval", scope, id, bound).newlyReserved());
        denied(ATTEMPT_CONFLICT, () -> ledger.markAmbiguous("approval", scope, id, "late-timeout"));
    }

    @Test void everySettlementDimensionMustFitTheOriginalReservation() {
        approve(); UUID id = UUID.randomUUID(); Usage bound = usage(20, 10, 80);
        ledger.reserveAttempt("approval", scope, id, bound);
        for (Usage invalid : List.of(usage(21, 0, 1), usage(0, 11, 1), usage(0, 0, 81)))
            denied(SETTLEMENT_CONFLICT, () -> ledger.settle("approval", scope, id, invalid, "evidence"));
        assertEquals(bound, ledger.read("approval", scope).accountedUsage());
        Snapshot stopped = new JdbcProviderBudgetLedger(dataSource, clock).read("approval", scope);
        assertTrue(stopped.stopped()); assertEquals("SETTLEMENT_BOUND_EXCEEDED", stopped.stopReason());
        assertEquals(AttemptState.AMBIGUOUS, ledger.reserveAttempt("approval", scope, id, bound).attempt().state());
        denied(STOPPED, () -> ledger.reserveAttempt("approval", scope, UUID.randomUUID(), usage(1, 0, 1)));
    }

    @Test void timeoutAndCrashHoldsSurviveRestartAndTimeWithoutRefund() {
        approve(); UUID ambiguous = UUID.randomUUID(); UUID crashed = UUID.randomUUID();
        ledger.reserveAttempt("approval", scope, ambiguous, usage(10, 10, 40));
        ledger.reserveAttempt("approval", scope, crashed, usage(10, 5, 30));
        Attempt uncertain = ledger.markAmbiguous("approval", scope, ambiguous, "TIMEOUT_AFTER_SEND");
        assertEquals(uncertain, ledger.markAmbiguous("approval", scope, ambiguous, "TIMEOUT_AFTER_SEND"));
        denied(ATTEMPT_CONFLICT, () -> ledger.markAmbiguous("approval", scope, ambiguous, "different-reason"));
        clock.now = clock.instant().plusSeconds(86_400);
        var restarted = new JdbcProviderBudgetLedger(dataSource, clock);
        assertEquals(usage(20, 15, 70), restarted.read("approval", scope).accountedUsage());
        assertFalse(restarted.reserveAttempt("approval", scope, crashed, usage(10, 5, 30)).newlyReserved());
        assertEquals(AttemptState.AMBIGUOUS, restarted.reserveAttempt("approval", scope, ambiguous, usage(10, 10, 40)).attempt().state());
        denied(EXPIRED, () -> restarted.reserveAttempt("approval", scope, UUID.randomUUID(), usage(1, 0, 1)));
        restarted.settle("approval", scope, ambiguous, usage(5, 3, 20), "reconciled-native-usage");
        assertEquals(usage(15, 8, 50), restarted.read("approval", scope).accountedUsage());
    }

    @Test void expiryAtNanosecondBoundaryBlocksNewWorkButNotReadbackOrSettlement() {
        Approval approved = approval(new Limits(8, 100, 1_000)); ledger.approve(approved);
        UUID id = UUID.randomUUID(); ledger.reserveAttempt("approval", scope, id, usage(20, 10, 80));
        clock.now = approved.expiresAt();
        denied(EXPIRED, () -> ledger.recordChatIngress("approval", scope, UUID.randomUUID()));
        denied(EXPIRED, () -> ledger.reserveAttempt("approval", scope, UUID.randomUUID(), usage(1, 0, 1)));
        assertEquals(approved, ledger.approve(approved).approval());
        assertEquals(usage(20, 10, 80), ledger.read("approval", scope).accountedUsage());
        ledger.settle("approval", scope, id, usage(10, 5, 50), "usage");
        assertEquals(usage(10, 5, 50), ledger.read("approval", scope).accountedUsage());
    }

    @Test void explicitStopIsDurableCannotBeResetAndDoesNotReleaseHolds() {
        Approval approved = approval(new Limits(8, 100, 1_000)); ledger.approve(approved);
        UUID id = UUID.randomUUID(); ledger.reserveAttempt("approval", scope, id, usage(20, 10, 80));
        Snapshot stopped = ledger.stop("approval", scope, "OWNER_STOP");
        assertEquals(stopped, ledger.stop("approval", scope, "OWNER_STOP"));
        denied(ATTEMPT_CONFLICT, () -> ledger.stop("approval", scope, "different-reason"));
        assertTrue(ledger.approve(approved).stopped());
        denied(STOPPED, () -> ledger.reserveAttempt("approval", scope, UUID.randomUUID(), usage(0, 0, 0)));
        denied(STOPPED, () -> ledger.recordChatIngress("approval", scope, UUID.randomUUID()));
        ledger.settle("approval", scope, id, usage(10, 5, 50), "usage");
        assertTrue(ledger.read("approval", scope).stopped());
        assertEquals(usage(10, 5, 50), ledger.read("approval", scope).accountedUsage());
    }

    @Test void allScopeDimensionsAreRequiredAcrossEveryOperation() {
        approve(); UUID id = UUID.randomUUID(); ledger.reserveAttempt("approval", scope, id, usage(2, 1, 4));
        Snapshot original = ledger.read("approval", scope);
        for (Scope changed : changedScopes()) {
            denied(SCOPE_MISMATCH, () -> ledger.read("approval", changed));
            denied(SCOPE_MISMATCH, () -> ledger.recordChatIngress("approval", changed, UUID.randomUUID()));
            denied(SCOPE_MISMATCH, () -> ledger.reserveAttempt("approval", changed, id, usage(2, 1, 4)));
            denied(SCOPE_MISMATCH, () -> ledger.settle("approval", changed, id, usage(1, 0, 2), "usage"));
            denied(SCOPE_MISMATCH, () -> ledger.markAmbiguous("approval", changed, id, "timeout"));
            denied(SCOPE_MISMATCH, () -> ledger.stop("approval", changed, "stop"));
        }
        assertEquals(original, ledger.read("approval", scope));
    }

    @Test void targetProfileIdentityIsDurableAndCannotTransferOrResetAnApproval() throws Exception {
        Approval original = approval(new Limits(8, 100, 1_000)); ledger.approve(original);
        UUID id = UUID.randomUUID(); Usage bound = usage(10, 5, 20);
        ledger.reserveAttempt("approval", scope, id, bound);
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(
            "SELECT target_profile_id FROM provider_budget_approval WHERE approval_id = ?")) {
            statement.setString(1, "approval");
            try (var rows = statement.executeQuery()) {
                assertTrue(rows.next()); assertEquals("target-a", rows.getString(1));
            }
        }
        var restarted = new JdbcProviderBudgetLedger(dataSource, clock);
        assertEquals(original, restarted.read("approval", scope).approval());
        Scope otherTarget = new Scope("customer", "deployment", "target-b", "staging", "credential-identity", "rate-card-v1");
        denied(SCOPE_MISMATCH, () -> restarted.reserveAttempt("approval", otherTarget, id, bound));
        denied(SCOPE_MISMATCH, () -> restarted.reserveAttempt("approval", otherTarget, UUID.randomUUID(), bound));
        denied(APPROVAL_CONFLICT, () -> restarted.approve(new Approval(original.id(), otherTarget, original.limits(), original.expiresAt())));
        Snapshot replay = restarted.approve(original);
        assertEquals(bound, replay.accountedUsage()); assertEquals(1, replay.providerAttempts());
        assertEquals("target-a", replay.approval().scope().targetProfileId());
    }

    @Test void tokenLimitIncludesBothDirectionsAndDenialChangesNoCountersOrStopState() {
        ledger.approve(approval(new Limits(8, 10, 1_000)));
        ledger.reserveAttempt("approval", scope, UUID.randomUUID(), usage(6, 4, 10));
        denied(LIMIT_EXCEEDED, () -> ledger.reserveAttempt("approval", scope, UUID.randomUUID(), usage(0, 1, 0)));
        Snapshot snapshot = ledger.read("approval", scope);
        assertEquals(usage(6, 4, 10), snapshot.accountedUsage()); assertEquals(1, snapshot.providerAttempts());
        assertFalse(snapshot.stopped()); assertNull(snapshot.stopReason());
    }

    @Test void costLimitIsIntegerNanoUsdAndDoesNotRound() {
        ledger.approve(approval(new Limits(8, 100, 2)));
        ledger.reserveAttempt("approval", scope, UUID.randomUUID(), usage(1, 0, 2));
        denied(LIMIT_EXCEEDED, () -> ledger.reserveAttempt("approval", scope, UUID.randomUUID(), usage(0, 0, 1)));
        assertEquals(2, ledger.read("approval", scope).accountedUsage().costNanoUsd());
        assertFalse(ledger.read("approval", scope).stopped());
    }

    @Test void concurrentRuntimeAndWorkerInstancesCannotExceedTokenCap() throws Exception {
        ledger.approve(approval(new Limits(100, 100, 1_000)));
        var worker = new JdbcProviderBudgetLedger(dataSource, clock);
        List<Boolean> results = race(20, index -> () -> {
            try { return (index % 2 == 0 ? ledger : worker).reserveAttempt("approval", scope, UUID.randomUUID(), usage(7, 3, 5)).newlyReserved(); }
            catch (BudgetException error) { assertTrue(error.code() == LIMIT_EXCEEDED || error.code() == STOPPED); return false; }
        });
        assertEquals(10, results.stream().filter(Boolean::booleanValue).count());
        Snapshot result = worker.read("approval", scope);
        assertEquals(10, result.providerAttempts()); assertEquals(usage(70, 30, 50), result.accountedUsage()); assertFalse(result.stopped());
    }

    @Test void concurrentCostReservationsCannotOverbookUnusedFractionOfCap() throws Exception {
        ledger.approve(approval(new Limits(100, 100, 10)));
        List<Boolean> results = race(12, index -> () -> {
            try { return new JdbcProviderBudgetLedger(dataSource, clock).reserveAttempt("approval", scope, UUID.randomUUID(), usage(1, 0, 3)).newlyReserved(); }
            catch (BudgetException error) { assertTrue(error.code() == LIMIT_EXCEEDED || error.code() == STOPPED); return false; }
        });
        assertEquals(3, results.stream().filter(Boolean::booleanValue).count());
        assertEquals(9, ledger.read("approval", scope).accountedUsage().costNanoUsd());
    }

    @Test void concurrentChatIngressCountsEveryFreshServerAttemptUpToLimit() throws Exception {
        approve();
        List<Boolean> results = race(16, index -> () -> {
            try { return new JdbcProviderBudgetLedger(dataSource, clock).recordChatIngress("approval", scope, UUID.randomUUID()).newlyCounted(); }
            catch (BudgetException error) { assertTrue(error.code() == LIMIT_EXCEEDED || error.code() == STOPPED); return false; }
        });
        assertEquals(8, results.stream().filter(Boolean::booleanValue).count());
        assertEquals(8, ledger.read("approval", scope).chatAttempts());
    }

    @Test void concurrentExactReservationRetryCreatesOnePermit() throws Exception {
        approve(); UUID id = UUID.randomUUID();
        List<Boolean> results = race(8, index -> () -> new JdbcProviderBudgetLedger(dataSource, clock)
            .reserveAttempt("approval", scope, id, usage(10, 5, 20)).newlyReserved());
        assertEquals(1, results.stream().filter(Boolean::booleanValue).count());
        assertEquals(usage(10, 5, 20), ledger.read("approval", scope).accountedUsage());
    }

    @Test void concurrentSettlementReplaysCannotRefundTwice() throws Exception {
        approve(); UUID id = UUID.randomUUID(); ledger.reserveAttempt("approval", scope, id, usage(20, 10, 80));
        List<Attempt> results = race(8, index -> () -> new JdbcProviderBudgetLedger(dataSource, clock)
            .settle("approval", scope, id, usage(10, 3, 40), "same-usage"));
        assertTrue(results.stream().allMatch(results.getFirst()::equals));
        assertEquals(usage(10, 3, 40), ledger.read("approval", scope).accountedUsage());
    }

    @Test void concurrentApprovalCreationAcceptsOnlyTheExactImmutableRequest() throws Exception {
        Approval same = approval(new Limits(8, 100, 1_000));
        List<Snapshot> results = race(8, index -> () -> new JdbcProviderBudgetLedger(dataSource, clock).approve(same));
        assertTrue(results.stream().allMatch(results.getFirst()::equals));
        ledger.reserveAttempt("approval", scope, UUID.randomUUID(), usage(10, 0, 10));
        assertEquals(usage(10, 0, 10), ledger.approve(same).accountedUsage());
    }

    @Test void reservationCommitsIndependentlyOfCallerRollback() throws Exception {
        approve();
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE caller_work (id INTEGER PRIMARY KEY)");
            connection.setAutoCommit(false); statement.executeUpdate("INSERT INTO caller_work VALUES (1)");
            ledger.reserveAttempt("approval", scope, UUID.randomUUID(), usage(20, 10, 80));
            connection.rollback();
        }
        assertEquals(usage(20, 10, 80), new JdbcProviderBudgetLedger(dataSource, clock).read("approval", scope).accountedUsage());
    }

    @Test void databaseFailureRollsBackBothAttemptAndAggregateBeforeAnySend() throws Exception {
        approve(); UUID id = UUID.randomUUID();
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("ALTER TABLE provider_budget_attempt ADD CONSTRAINT fixture_reject_seven CHECK (bound_input_tokens <> 7)");
        }
        denied(STORAGE_UNAVAILABLE, () -> ledger.reserveAttempt("approval", scope, id, usage(7, 0, 10)));
        assertEquals(usage(0, 0, 0), ledger.read("approval", scope).accountedUsage());
        assertEquals(0, ledger.read("approval", scope).providerAttempts());
        assertTrue(ledger.reserveAttempt("approval", scope, id, usage(6, 0, 10)).newlyReserved());
    }

    @Test void owningApplicationSchemaAcceptsAnUnsettledReservation() throws Exception {
        approve();
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            assertEquals(1, statement.executeUpdate("""
                INSERT INTO provider_budget_attempt
                (approval_id, attempt_id, bound_input_tokens, bound_output_tokens, bound_cost_nano_usd, attempt_state)
                VALUES ('approval', '00000000-0000-0000-0000-000000000001', 20, 10, 80, 'RESERVED')
                """));
        }
    }

    @Test void commitResponseLossRetainsHoldAndRetryDoesNotIssueAnotherPermit() {
        approve(); UUID id = UUID.randomUUID(); AtomicBoolean loseResponse = new AtomicBoolean(true);
        DataSource uncertain = wrapped(connection -> proxy(connection, (method, arguments) -> {
            Object result = invoke(connection, method, arguments);
            if (method.getName().equals("commit") && loseResponse.getAndSet(false)) throw new SQLException("fixture commit response loss");
            return result;
        }));
        denied(STORAGE_UNAVAILABLE, () -> new JdbcProviderBudgetLedger(uncertain, clock).reserveAttempt("approval", scope, id, usage(20, 10, 80)));
        assertEquals(usage(20, 10, 80), ledger.read("approval", scope).accountedUsage());
        assertFalse(ledger.reserveAttempt("approval", scope, id, usage(20, 10, 80)).newlyReserved());
    }

    @Test void enlistedConnectionIsRejectedInsteadOfJoiningAnExternalTransaction() {
        approve();
        DataSource enlisted = wrapped(connection -> {
            try { connection.setAutoCommit(false); } catch (SQLException error) { throw new RuntimeException(error); }
            return connection;
        });
        denied(STORAGE_UNAVAILABLE, () -> new JdbcProviderBudgetLedger(enlisted, clock).reserveAttempt("approval", scope, UUID.randomUUID(), usage(1, 0, 1)));
        assertEquals(0, ledger.read("approval", scope).providerAttempts());
    }

    @Test void overflowFailsClosedWithoutWrappingCountersOrDroppingExistingHolds() {
        ledger.approve(approval(new Limits(Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE)));
        ledger.reserveAttempt("approval", scope, UUID.randomUUID(), usage(Long.MAX_VALUE, 0, Long.MAX_VALUE));
        denied(ARITHMETIC_OVERFLOW, () -> ledger.reserveAttempt("approval", scope, UUID.randomUUID(), usage(1, 0, 1)));
        Snapshot result = ledger.read("approval", scope);
        assertEquals(usage(Long.MAX_VALUE, 0, Long.MAX_VALUE), result.accountedUsage());
        assertEquals(1, result.providerAttempts()); assertEquals("ARITHMETIC_OVERFLOW", result.stopReason());
        denied(ARITHMETIC_OVERFLOW, () -> usage(Long.MAX_VALUE, 1, 0));
    }

    @Test void temporaryFullContextHoldDeniesCurrentSendButSettlementAllowsAFreshFittingAttempt() {
        ledger.approve(approval(new Limits(10, 200_000, 1_000_000_000)));
        UUID first = UUID.randomUUID(); Usage bound = usage(128_000, 1_000, 77_400_000);
        ledger.reserveAttempt("approval", scope, first, bound);
        Snapshot held = ledger.read("approval", scope);
        BudgetException denial = assertThrows(BudgetException.class,
            () -> ledger.reserveAttempt("approval", scope, UUID.randomUUID(), bound));
        assertEquals(LIMIT_EXCEEDED, denial.code()); assertEquals("TOKEN_LIMIT", denial.reason());
        assertEquals(held, ledger.read("approval", scope));
        ledger.settle("approval", scope, first, usage(800, 200, 600_000), "native-usage-conservative-cost");
        assertTrue(new JdbcProviderBudgetLedger(dataSource, clock).reserveAttempt("approval", scope, UUID.randomUUID(), bound).newlyReserved());
        Snapshot result = ledger.read("approval", scope);
        assertEquals(130_000, result.accountedUsage().totalTokens()); assertEquals(2, result.providerAttempts());
        assertFalse(result.stopped()); assertNull(result.stopReason());
    }

    @Test void invalidAndUnknownRequestsNeverCreateAuthority() {
        denied(INVALID_REQUEST, () -> new Limits(-1, 0, 0));
        denied(INVALID_REQUEST, () -> usage(0, -1, 0));
        denied(INVALID_REQUEST, () -> new Scope(" ", "deployment", "target", "staging", "credential", "rate"));
        for (String target : new String[]{null, "", " \t"})
            denied(INVALID_REQUEST, () -> new Scope("customer", "deployment", target, "staging", "credential", "rate"));
        denied(EXPIRED, () -> ledger.approve(new Approval("expired", scope, new Limits(1, 1, 1), clock.instant())));
        denied(NOT_FOUND, () -> ledger.read("missing", scope));
        denied(NOT_FOUND, () -> ledger.reserveAttempt("missing", scope, UUID.randomUUID(), usage(1, 0, 1)));
        approve();
        denied(NOT_FOUND, () -> ledger.settle("approval", scope, UUID.randomUUID(), usage(1, 0, 1), "usage"));
        denied(INVALID_REQUEST, () -> ledger.reserveAttempt("approval", scope, null, usage(1, 0, 1)));
        denied(INVALID_REQUEST, () -> ledger.settle("approval", scope, UUID.randomUUID(), usage(0, 0, 0), " "));
    }

    List<Scope> changedScopes() {
        return List.of(new Scope("other", "deployment", "target-a", "staging", "credential-identity", "rate-card-v1"),
            new Scope("customer", "other", "target-a", "staging", "credential-identity", "rate-card-v1"),
            new Scope("customer", "deployment", "target-b", "staging", "credential-identity", "rate-card-v1"),
            new Scope("customer", "deployment", "target-a", "production", "credential-identity", "rate-card-v1"),
            new Scope("customer", "deployment", "target-a", "staging", "other", "rate-card-v1"),
            new Scope("customer", "deployment", "target-a", "staging", "credential-identity", "other"));
    }

    <T> List<T> race(int count, java.util.function.IntFunction<Callable<T>> work) throws Exception {
        CountDownLatch ready = new CountDownLatch(count); CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(count)) {
            var futures = new ArrayList<java.util.concurrent.Future<T>>();
            for (int index = 0; index < count; index++) {
                Callable<T> task = work.apply(index);
                futures.add(executor.submit(() -> { ready.countDown(); assertTrue(start.await(20, TimeUnit.SECONDS)); return task.call(); }));
            }
            assertTrue(ready.await(20, TimeUnit.SECONDS)); start.countDown();
            var results = new ArrayList<T>();
            for (var future : futures) results.add(future.get(30, TimeUnit.SECONDS));
            return results;
        } finally { start.countDown(); }
    }

    DataSource wrapped(UnaryOperator<Connection> wrapper) {
        return (DataSource) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{DataSource.class}, (proxy, method, arguments) -> {
            Object result = invoke(dataSource, method, arguments);
            return method.getName().equals("getConnection") ? wrapper.apply((Connection) result) : result;
        });
    }

    Connection proxy(Connection delegate, Invocation invocation) {
        return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, arguments) -> invocation.call(method, arguments));
    }

    static Object invoke(Object target, java.lang.reflect.Method method, Object[] arguments) throws Throwable {
        try { return method.invoke(target, arguments); } catch (InvocationTargetException error) { throw error.getCause(); }
    }

    @FunctionalInterface interface Invocation { Object call(java.lang.reflect.Method method, Object[] arguments) throws Throwable; }

    static final class MutableClock extends Clock {
        volatile Instant now;
        MutableClock(Instant now) { this.now = now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
