package com.ai.fabric.platform.backend.customerselfservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

final class CustomerLegacyOperationsHistoryFixtures {
    private CustomerLegacyOperationsHistoryFixtures() { }

    static JdbcTemplate history(ObjectMapper mapper) {
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(
            "jdbc:h2:mem:customer-history-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("""
            CREATE TABLE deployment_capacity_reservations (
                id VARCHAR(64) PRIMARY KEY, customer_id VARCHAR(64), deployment_id VARCHAR(64),
                target_profile_id VARCHAR(64), payload_json TEXT, updated_at TIMESTAMP WITH TIME ZONE)
            """);
        jdbc.execute("""
            CREATE TABLE platform_target_retirement_operations (
                id VARCHAR(64) PRIMARY KEY, customer_id VARCHAR(64), deployment_id VARCHAR(64), target_profile_id VARCHAR(64),
                operation_key VARCHAR(128), request_fingerprint VARCHAR(64), request_json TEXT, result_json TEXT,
                state VARCHAR(64), step VARCHAR(64), drain_deadline TIMESTAMP WITH TIME ZONE, attempt INT,
                next_retry_at TIMESTAMP WITH TIME ZONE, error_code VARCHAR(64), created_at TIMESTAMP WITH TIME ZONE,
                started_at TIMESTAMP WITH TIME ZONE, completed_at TIMESTAMP WITH TIME ZONE, updated_at TIMESTAMP WITH TIME ZONE)
            """);
        var now = Instant.parse("2026-10-24T12:00:00Z");
        var payload = mapper.createObjectNode().put("id", "reservation").put("customerId", "customer")
            .put("tenantId", "tenant").put("deploymentId", "deployment").put("targetProfileId", "staging-target")
            .put("intent", "CUSTOMER_STAGING").put("customerOperation", "APPLY").put("state", "OUTCOME_UNKNOWN")
            .put("revision", 3).put("expiresAt", now.minusSeconds(3600).toString()).put("expiryState", "CURRENT")
            .put("providerSecret", "PRIVATE_HISTORY_MUST_NOT_LEAK");
        jdbc.update("INSERT INTO deployment_capacity_reservations VALUES (?,?,?,?,?,?)",
            "reservation", "customer", "deployment", "staging-target", payload.toString(), Timestamp.from(now));
        var request = mapper.createObjectNode().put("operationKey", "retire-key");
        request.putArray("releaseIds").add("release");
        request.putObject("retention").put("decision", "RECLAIM").put("backupDecisionReference", "reviewed-backup");
        var result = mapper.createObjectNode();
        result.putArray("blockers").add("DRAIN_PENDING");
        result.put("providerSecret", "PRIVATE_HISTORY_MUST_NOT_LEAK");
        jdbc.update("""
            INSERT INTO platform_target_retirement_operations
            (id,customer_id,deployment_id,target_profile_id,operation_key,request_fingerprint,request_json,result_json,
             state,step,drain_deadline,attempt,created_at,updated_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """, "retirement", "customer", "deployment", "staging-target", "retire-key", "retire-fingerprint",
            request.toString(), result.toString(), "RETRYABLE_FAILURE", "DRAIN", Timestamp.from(now), 2,
            Timestamp.from(now), Timestamp.from(now));
        return jdbc;
    }
}
