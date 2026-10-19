package com.loomai.demo.dealership.lead;

import com.loomai.demo.dealership.security.ReceiverBindingService.Scope;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.ArgumentPreparedStatementSetter;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class LeadRepository {

    private final JdbcTemplate jdbc;

    public LeadRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public LeadRecord insert(LeadRecord lead) {
        Scope scope = lead.receiverScope();
        if (scope == null || !scope.advertiserId().equals(lead.providerAdvertiserId())
            || !scope.storageKey(lead.receiverRequestKey()).equals(lead.idempotencyKey()))
            throw new DataIntegrityViolationException("A scoped receiver receipt is required.");
        String sql = """
            INSERT INTO dealership_lead_request
                (id, idempotency_key, action_type, vehicle_id, provider_stock_id, provider_advertiser_id, encrypted_contact, status,
                 consent_recorded, source_session_id, receipt_code, created_at, updated_at,
                 receiver_binding_id, receiver_binding_fingerprint, receiver_customer_id, receiver_tenant_id,
                 receiver_deployment_id, receiver_advertiser_id, receiver_request_key)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        Object[] values = {lead.id(), lead.idempotencyKey(), lead.actionType(), lead.vehicleId(), lead.providerStockId(),
            lead.providerAdvertiserId(), lead.encryptedContact(),
            lead.status(), lead.consentRecorded(), lead.sourceSessionId(), lead.receiptCode(),
            Timestamp.from(lead.createdAt()), Timestamp.from(lead.updatedAt()), scope.bindingId(), scope.fingerprint(),
            scope.customerId(), scope.tenantId(), scope.deploymentId(), scope.advertiserId(), lead.receiverRequestKey()};
        boolean inserted = Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>) connection -> {
            // A unique-key violation aborts PostgreSQL transactions unless its statement is rolled back first.
            Savepoint savepoint = connection.getAutoCommit() ? null : connection.setSavepoint();
            try (var statement = connection.prepareStatement(sql)) {
                new ArgumentPreparedStatementSetter(values).setValues(statement);
                statement.executeUpdate();
                return true;
            } catch (SQLException failure) {
                if (savepoint != null) connection.rollback(savepoint);
                if (!"23505".equals(failure.getSQLState())) throw failure;
                return false;
            } finally {
                if (savepoint != null) connection.releaseSavepoint(savepoint);
            }
        }));
        if (!inserted) {
            // Only an idempotency-key winner may be recovered; ID/receipt collisions are store failures.
            if (findById(lead.id()).isPresent() || !query(" WHERE receipt_code = ?", lead.receiptCode()).isEmpty()) {
                throw new DataIntegrityViolationException("The lead identity could not be stored.");
            }
            return findByIdempotencyKey(scope, lead.receiverRequestKey())
                .orElseThrow(() -> new DataIntegrityViolationException("The conflicting lead receipt is unavailable."));
        }
        return findById(lead.id()).orElseThrow(() -> new DataIntegrityViolationException("The stored lead receipt is unavailable."));
    }

    public Optional<LeadRecord> findByIdempotencyKey(Scope scope, String key) {
        return query(" WHERE receiver_binding_fingerprint = ? AND receiver_request_key = ? AND idempotency_key = ?",
            scope.fingerprint(), key, scope.storageKey(key)).stream().findFirst();
    }

    public Optional<LeadRecord> findById(String id) {
        return query(" WHERE id = ?", id).stream().findFirst();
    }

    public List<LeadRecord> latest(int limit) {
        return jdbc.query(select() + " ORDER BY created_at DESC LIMIT ?", mapper(), Math.max(1, Math.min(limit, 100)));
    }

    public void updateStatus(String id, String status) {
        jdbc.update("UPDATE dealership_lead_request SET status = ?, updated_at = ? WHERE id = ? AND receiver_binding_fingerprint IS NOT NULL",
            status, Timestamp.from(Instant.now()), id);
    }

    public int deleteOlderThan(Instant cutoff) {
        return jdbc.update("DELETE FROM dealership_lead_request WHERE created_at < ? AND receiver_binding_fingerprint IS NOT NULL", Timestamp.from(cutoff));
    }

    private List<LeadRecord> query(String where, Object... args) {
        return jdbc.query(select() + where, mapper(), args);
    }

    private String select() {
        return """
            SELECT id, idempotency_key, action_type, vehicle_id, provider_stock_id, provider_advertiser_id, encrypted_contact, status,
                   consent_recorded, source_session_id, receipt_code, created_at, updated_at,
                   receiver_binding_id, receiver_binding_fingerprint, receiver_customer_id, receiver_tenant_id,
                   receiver_deployment_id, receiver_advertiser_id, receiver_request_key
              FROM dealership_lead_request
            """;
    }

    private org.springframework.jdbc.core.RowMapper<LeadRecord> mapper() {
        return (rs, rowNum) -> new LeadRecord(
            rs.getString("id"), rs.getString("idempotency_key"), rs.getString("action_type"),
            rs.getString("vehicle_id"), rs.getString("encrypted_contact"), rs.getString("status"),
            rs.getBoolean("consent_recorded"), rs.getString("source_session_id"), rs.getString("receipt_code"),
            rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant(),
            rs.getString("provider_stock_id"), rs.getString("provider_advertiser_id"),
            scope(rs), rs.getString("receiver_request_key")
        );
    }

    private Scope scope(java.sql.ResultSet rs) throws SQLException {
        if (rs.getString("receiver_binding_fingerprint") == null) return null;
        var scope = new Scope(rs.getString("receiver_binding_id"), rs.getString("receiver_customer_id"),
            rs.getString("receiver_tenant_id"), rs.getString("receiver_deployment_id"), rs.getString("receiver_advertiser_id"));
        if (!scope.fingerprint().equals(rs.getString("receiver_binding_fingerprint"))
            || !scope.storageKey(rs.getString("receiver_request_key")).equals(rs.getString("idempotency_key")))
            throw new DataIntegrityViolationException("The stored receiver scope is invalid.");
        return scope;
    }

    public record LeadRecord(String id, String idempotencyKey, String actionType, String vehicleId,
                             String encryptedContact, String status, boolean consentRecorded,
                             String sourceSessionId, String receiptCode, Instant createdAt, Instant updatedAt,
                             String providerStockId, String providerAdvertiserId,
                             Scope receiverScope, String receiverRequestKey) { }
}
