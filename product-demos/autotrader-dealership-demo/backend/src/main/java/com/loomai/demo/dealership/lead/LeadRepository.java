package com.loomai.demo.dealership.lead;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

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
        jdbc.update("""
            INSERT INTO dealership_lead_request
                (id, idempotency_key, action_type, vehicle_id, encrypted_contact, status,
                 consent_recorded, source_session_id, receipt_code, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, lead.id(), lead.idempotencyKey(), lead.actionType(), lead.vehicleId(), lead.encryptedContact(),
            lead.status(), lead.consentRecorded(), lead.sourceSessionId(), lead.receiptCode(),
            Timestamp.from(lead.createdAt()), Timestamp.from(lead.updatedAt()));
        return lead;
    }

    public Optional<LeadRecord> findByIdempotencyKey(String key) {
        return query(" WHERE idempotency_key = ?", key).stream().findFirst();
    }

    public Optional<LeadRecord> findById(String id) {
        return query(" WHERE id = ?", id).stream().findFirst();
    }

    public List<LeadRecord> latest(int limit) {
        return jdbc.query(select() + " ORDER BY created_at DESC LIMIT ?", mapper(), Math.max(1, Math.min(limit, 100)));
    }

    public void updateStatus(String id, String status) {
        jdbc.update("UPDATE dealership_lead_request SET status = ?, updated_at = ? WHERE id = ?",
            status, Timestamp.from(Instant.now()), id);
    }

    public int deleteOlderThan(Instant cutoff) {
        return jdbc.update("DELETE FROM dealership_lead_request WHERE created_at < ?", Timestamp.from(cutoff));
    }

    private List<LeadRecord> query(String where, Object... args) {
        return jdbc.query(select() + where, mapper(), args);
    }

    private String select() {
        return """
            SELECT id, idempotency_key, action_type, vehicle_id, encrypted_contact, status,
                   consent_recorded, source_session_id, receipt_code, created_at, updated_at
              FROM dealership_lead_request
            """;
    }

    private org.springframework.jdbc.core.RowMapper<LeadRecord> mapper() {
        return (rs, rowNum) -> new LeadRecord(
            rs.getString("id"), rs.getString("idempotency_key"), rs.getString("action_type"),
            rs.getString("vehicle_id"), rs.getString("encrypted_contact"), rs.getString("status"),
            rs.getBoolean("consent_recorded"), rs.getString("source_session_id"), rs.getString("receipt_code"),
            rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()
        );
    }

    public record LeadRecord(String id, String idempotencyKey, String actionType, String vehicleId,
                             String encryptedContact, String status, boolean consentRecorded,
                             String sourceSessionId, String receiptCode, Instant createdAt, Instant updatedAt) { }
}
