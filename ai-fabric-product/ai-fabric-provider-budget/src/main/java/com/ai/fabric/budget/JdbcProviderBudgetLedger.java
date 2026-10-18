package com.ai.fabric.budget;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import static com.ai.fabric.budget.BudgetException.Code.*;
import static com.ai.fabric.budget.ProviderBudgetLedger.*;

/**
 * No DDL, network calls, background refunds, or framework dependencies.
 * Use a non-transaction-enlisted DataSource: every call owns a fresh committed JDBC transaction.
 */
public final class JdbcProviderBudgetLedger implements ProviderBudgetLedger {
    private final DataSource dataSource;
    private final Clock clock;

    public JdbcProviderBudgetLedger(DataSource dataSource) { this(dataSource, Clock.systemUTC()); }

    public JdbcProviderBudgetLedger(DataSource dataSource, Clock clock) {
        this.dataSource = Objects.requireNonNull(dataSource);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override public Snapshot approve(Approval approval) {
        if (approval == null) throw new BudgetException(INVALID_REQUEST);
        try {
            return transaction(connection -> {
                Snapshot existing = find(connection, approval.id(), true);
                if (existing != null) return sameApproval(existing, approval);
                if (!clock.instant().isBefore(approval.expiresAt())) throw new BudgetException(EXPIRED);
                try (var statement = connection.prepareStatement("""
                    INSERT INTO provider_budget_approval
                    (approval_id, customer_id, deployment_id, target_profile_id, environment_name, credential_identity, rate_card_reference,
                     max_chat_attempts, max_total_tokens, max_cost_nano_usd, expires_second, expires_nano)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                    bind(statement, approval.id(), approval.scope().customerId(), approval.scope().deploymentId(),
                        approval.scope().targetProfileId(), approval.scope().environment(), approval.scope().credentialIdentity(), approval.scope().rateCardReference(),
                        approval.limits().chatAttempts(), approval.limits().totalTokens(), approval.limits().costNanoUsd(),
                        approval.expiresAt().getEpochSecond(), approval.expiresAt().getNano());
                    statement.executeUpdate();
                } catch (SQLException error) {
                    if ("23505".equals(error.getSQLState())) throw new ApprovalRace();
                    throw error;
                }
                return require(connection, approval.id(), approval.scope());
            });
        } catch (ApprovalRace race) {
            return transaction(connection -> {
                Snapshot existing = find(connection, approval.id(), true);
                if (existing == null) throw new BudgetException(STORAGE_UNAVAILABLE);
                return sameApproval(existing, approval);
            });
        }
    }

    @Override public ChatReceipt recordChatIngress(String approvalId, Scope scope, UUID serverAttemptId) {
        check(approvalId, scope, serverAttemptId);
        return decided(transaction(connection -> {
            Snapshot snapshot = require(connection, approvalId, scope);
            try (var query = connection.prepareStatement("""
                SELECT ordinal FROM provider_budget_chat_ingress WHERE approval_id = ? AND server_attempt_id = ?
                """)) {
                bind(query, approvalId, serverAttemptId.toString());
                try (var rows = query.executeQuery()) {
                    if (rows.next()) return Decision.value(new ChatReceipt(serverAttemptId, rows.getLong(1), false));
                }
            }
            active(snapshot);
            if (snapshot.chatAttempts() >= snapshot.approval().limits().chatAttempts())
                return capacityDenied("CHAT_LIMIT");
            long ordinal = checkedAdd(snapshot.chatAttempts(), 1);
            execute(connection, "INSERT INTO provider_budget_chat_ingress (approval_id, server_attempt_id, ordinal) VALUES (?, ?, ?)",
                approvalId, serverAttemptId.toString(), ordinal);
            execute(connection, "UPDATE provider_budget_approval SET chat_attempts = ? WHERE approval_id = ?", ordinal, approvalId);
            return Decision.value(new ChatReceipt(serverAttemptId, ordinal, true));
        }));
    }

    @Override public ReservationReceipt reserveAttempt(String approvalId, Scope scope, UUID attemptId, Usage upperBound) {
        check(approvalId, scope, attemptId);
        if (upperBound == null) throw new BudgetException(INVALID_REQUEST);
        return decided(transaction(connection -> {
            Snapshot snapshot = require(connection, approvalId, scope);
            Attempt previous = attempt(connection, approvalId, attemptId, false);
            if (previous != null) {
                if (!previous.upperBound().equals(upperBound)) throw new BudgetException(ATTEMPT_CONFLICT);
                return Decision.value(new ReservationReceipt(previous, false));
            }
            active(snapshot);
            Usage next;
            try {
                next = new Usage(checkedAdd(snapshot.accountedUsage().inputTokens(), upperBound.inputTokens()),
                    checkedAdd(snapshot.accountedUsage().outputTokens(), upperBound.outputTokens()),
                    checkedAdd(snapshot.accountedUsage().costNanoUsd(), upperBound.costNanoUsd()));
            } catch (BudgetException overflow) {
                if (overflow.code() != ARITHMETIC_OVERFLOW) throw overflow;
                return integrityDenied(connection, approvalId, "ARITHMETIC_OVERFLOW", ARITHMETIC_OVERFLOW);
            }
            if (next.totalTokens() > snapshot.approval().limits().totalTokens()) return capacityDenied("TOKEN_LIMIT");
            if (next.costNanoUsd() > snapshot.approval().limits().costNanoUsd()) return capacityDenied("COST_LIMIT");
            long attempts;
            try { attempts = checkedAdd(snapshot.providerAttempts(), 1); }
            catch (BudgetException overflow) { return integrityDenied(connection, approvalId, "ARITHMETIC_OVERFLOW", ARITHMETIC_OVERFLOW); }
            execute(connection, """
                INSERT INTO provider_budget_attempt
                (approval_id, attempt_id, bound_input_tokens, bound_output_tokens, bound_cost_nano_usd, attempt_state)
                VALUES (?, ?, ?, ?, ?, 'RESERVED')
                """, approvalId, attemptId.toString(), upperBound.inputTokens(), upperBound.outputTokens(), upperBound.costNanoUsd());
            execute(connection, """
                UPDATE provider_budget_approval SET provider_attempts = ?, accounted_input_tokens = ?,
                    accounted_output_tokens = ?, accounted_cost_nano_usd = ? WHERE approval_id = ?
                """, attempts, next.inputTokens(), next.outputTokens(), next.costNanoUsd(), approvalId);
            return Decision.value(new ReservationReceipt(attempt(connection, approvalId, attemptId, true), true));
        }));
    }

    @Override public Attempt settle(String approvalId, Scope scope, UUID attemptId, Usage settledUsage, String evidenceReference) {
        check(approvalId, scope, attemptId);
        if (settledUsage == null) throw new BudgetException(INVALID_REQUEST);
        text(evidenceReference, 512);
        return decided(transaction(connection -> {
            Snapshot snapshot = require(connection, approvalId, scope);
            Attempt previous = attempt(connection, approvalId, attemptId, true);
            if (previous.state() == AttemptState.SETTLED) {
                if (!settledUsage.equals(previous.settledUsage()) || !evidenceReference.equals(previous.evidenceReference()))
                    throw new BudgetException(SETTLEMENT_CONFLICT);
                return Decision.value(previous);
            }
            if (settledUsage.inputTokens() > previous.upperBound().inputTokens()
                || settledUsage.outputTokens() > previous.upperBound().outputTokens()
                || settledUsage.costNanoUsd() > previous.upperBound().costNanoUsd()) {
                execute(connection, """
                    UPDATE provider_budget_attempt SET attempt_state = 'AMBIGUOUS', ambiguity_reason = 'SETTLEMENT_BOUND_EXCEEDED'
                    WHERE approval_id = ? AND attempt_id = ?
                    """, approvalId, attemptId.toString());
                return integrityDenied(connection, approvalId, "SETTLEMENT_BOUND_EXCEEDED", SETTLEMENT_CONFLICT);
            }
            Usage next = new Usage(replace(snapshot.accountedUsage().inputTokens(), previous.upperBound().inputTokens(), settledUsage.inputTokens()),
                replace(snapshot.accountedUsage().outputTokens(), previous.upperBound().outputTokens(), settledUsage.outputTokens()),
                replace(snapshot.accountedUsage().costNanoUsd(), previous.upperBound().costNanoUsd(), settledUsage.costNanoUsd()));
            execute(connection, """
                UPDATE provider_budget_attempt SET attempt_state = 'SETTLED', actual_input_tokens = ?, actual_output_tokens = ?,
                    actual_cost_nano_usd = ?, evidence_reference = ? WHERE approval_id = ? AND attempt_id = ?
                """, settledUsage.inputTokens(), settledUsage.outputTokens(), settledUsage.costNanoUsd(), evidenceReference, approvalId, attemptId.toString());
            execute(connection, """
                UPDATE provider_budget_approval SET accounted_input_tokens = ?, accounted_output_tokens = ?,
                    accounted_cost_nano_usd = ? WHERE approval_id = ?
                """, next.inputTokens(), next.outputTokens(), next.costNanoUsd(), approvalId);
            return Decision.value(attempt(connection, approvalId, attemptId, true));
        }));
    }

    @Override public Attempt markAmbiguous(String approvalId, Scope scope, UUID attemptId, String reason) {
        check(approvalId, scope, attemptId);
        text(reason, 512);
        return transaction(connection -> {
            require(connection, approvalId, scope);
            Attempt previous = attempt(connection, approvalId, attemptId, true);
            if (previous.state() == AttemptState.SETTLED) throw new BudgetException(ATTEMPT_CONFLICT);
            if (previous.state() == AttemptState.AMBIGUOUS) {
                if (!reason.equals(previous.ambiguityReason())) throw new BudgetException(ATTEMPT_CONFLICT);
                return previous;
            }
            execute(connection, """
                UPDATE provider_budget_attempt SET attempt_state = 'AMBIGUOUS', ambiguity_reason = ?
                WHERE approval_id = ? AND attempt_id = ?
                """, reason, approvalId, attemptId.toString());
            return attempt(connection, approvalId, attemptId, true);
        });
    }

    @Override public Snapshot stop(String approvalId, Scope scope, String reason) {
        check(approvalId, scope, new UUID(0, 0));
        text(reason, 512);
        return transaction(connection -> {
            Snapshot snapshot = require(connection, approvalId, scope);
            if (snapshot.stopped()) {
                if (!reason.equals(snapshot.stopReason())) throw new BudgetException(ATTEMPT_CONFLICT);
                return snapshot;
            }
            stopRow(connection, approvalId, reason);
            return require(connection, approvalId, scope);
        });
    }

    @Override public Snapshot read(String approvalId, Scope scope) {
        check(approvalId, scope, new UUID(0, 0));
        return transaction(connection -> require(connection, approvalId, scope));
    }

    private void active(Snapshot snapshot) {
        if (snapshot.stopped()) throw new BudgetException(STOPPED);
        if (!clock.instant().isBefore(snapshot.approval().expiresAt())) throw new BudgetException(EXPIRED);
    }

    private static Snapshot sameApproval(Snapshot existing, Approval approval) {
        if (!existing.approval().equals(approval)) throw new BudgetException(APPROVAL_CONFLICT);
        return existing;
    }

    private static Snapshot require(Connection connection, String id, Scope scope) throws SQLException {
        Snapshot snapshot = find(connection, id, true);
        if (snapshot == null) throw new BudgetException(NOT_FOUND);
        if (!snapshot.approval().scope().equals(scope)) throw new BudgetException(SCOPE_MISMATCH);
        return snapshot;
    }

    private static Snapshot find(Connection connection, String id, boolean lock) throws SQLException {
        try (var query = connection.prepareStatement("SELECT * FROM provider_budget_approval WHERE approval_id = ?" + (lock ? " FOR UPDATE" : ""))) {
            bind(query, id);
            try (var rows = query.executeQuery()) {
                if (!rows.next()) return null;
                Scope scope = new Scope(rows.getString("customer_id"), rows.getString("deployment_id"), rows.getString("target_profile_id"), rows.getString("environment_name"),
                    rows.getString("credential_identity"), rows.getString("rate_card_reference"));
                Approval approval = new Approval(id, scope,
                    new Limits(rows.getLong("max_chat_attempts"), rows.getLong("max_total_tokens"), rows.getLong("max_cost_nano_usd")),
                    Instant.ofEpochSecond(rows.getLong("expires_second"), rows.getInt("expires_nano")));
                return new Snapshot(approval, rows.getLong("chat_attempts"), rows.getLong("provider_attempts"),
                    new Usage(rows.getLong("accounted_input_tokens"), rows.getLong("accounted_output_tokens"), rows.getLong("accounted_cost_nano_usd")),
                    rows.getBoolean("stopped"), rows.getString("stop_reason"));
            }
        }
    }

    private static Attempt attempt(Connection connection, String approvalId, UUID id, boolean required) throws SQLException {
        try (var query = connection.prepareStatement("SELECT * FROM provider_budget_attempt WHERE approval_id = ? AND attempt_id = ?")) {
            bind(query, approvalId, id.toString());
            try (var rows = query.executeQuery()) {
                if (!rows.next()) {
                    if (required) throw new BudgetException(NOT_FOUND);
                    return null;
                }
                AttemptState state = AttemptState.valueOf(rows.getString("attempt_state"));
                return new Attempt(id, new Usage(rows.getLong("bound_input_tokens"), rows.getLong("bound_output_tokens"), rows.getLong("bound_cost_nano_usd")),
                    state, state == AttemptState.SETTLED ? new Usage(rows.getLong("actual_input_tokens"), rows.getLong("actual_output_tokens"),
                    rows.getLong("actual_cost_nano_usd")) : null, rows.getString("evidence_reference"), rows.getString("ambiguity_reason"));
            }
        }
    }

    private static void check(String id, Scope scope, UUID attemptId) {
        text(id, 128);
        if (scope == null || attemptId == null) throw new BudgetException(INVALID_REQUEST);
    }

    private static long replace(long total, long bound, long settled) {
        try {
            long value = Math.addExact(Math.subtractExact(total, bound), settled);
            if (value < 0) throw new BudgetException(STORAGE_UNAVAILABLE);
            return value;
        } catch (ArithmeticException error) { throw new BudgetException(ARITHMETIC_OVERFLOW); }
    }

    private static <T> Decision<T> capacityDenied(String reason) {
        return new Decision<>(null, new BudgetException(LIMIT_EXCEEDED, reason));
    }

    private static <T> Decision<T> integrityDenied(Connection connection, String approvalId, String reason, BudgetException.Code code) throws SQLException {
        stopRow(connection, approvalId, reason);
        return new Decision<>(null, new BudgetException(code, reason));
    }

    private static void stopRow(Connection connection, String approvalId, String reason) throws SQLException {
        execute(connection, """
            UPDATE provider_budget_approval SET stop_reason = CASE WHEN stopped THEN stop_reason ELSE ? END, stopped = TRUE
            WHERE approval_id = ?
            """, reason, approvalId);
    }

    private static <T> T decided(Decision<T> decision) {
        if (decision.failure() != null) throw decision.failure();
        return decision.value();
    }

    private static void execute(Connection connection, String sql, Object... values) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            bind(statement, values);
            if (statement.executeUpdate() != 1) throw new BudgetException(STORAGE_UNAVAILABLE);
        }
    }

    private static void bind(PreparedStatement statement, Object... values) throws SQLException {
        for (int index = 0; index < values.length; index++) statement.setObject(index + 1, values[index]);
    }

    private <T> T transaction(SqlWork<T> work) {
        try (Connection connection = dataSource.getConnection()) {
            // Reject a transaction-aware/enlisted connection rather than accidentally joining caller work.
            if (!connection.getAutoCommit()) throw new BudgetException(STORAGE_UNAVAILABLE);
            connection.setAutoCommit(false);
            boolean ended = false;
            try {
                T result = work.execute(connection);
                connection.commit();
                ended = true;
                return result;
            } catch (SQLException | RuntimeException error) {
                try { connection.rollback(); ended = true; }
                catch (SQLException ignored) { /* Never toggle autocommit after an uncertain rollback. */ }
                throw error;
            } finally {
                if (ended) connection.setAutoCommit(true);
            }
        } catch (SQLException error) {
            throw new BudgetException(STORAGE_UNAVAILABLE);
        }
    }

    @FunctionalInterface private interface SqlWork<T> { T execute(Connection connection) throws SQLException; }
    private record Decision<T>(T value, BudgetException failure) {
        static <T> Decision<T> value(T value) { return new Decision<>(value, null); }
    }
    private static final class ApprovalRace extends RuntimeException { }
}
