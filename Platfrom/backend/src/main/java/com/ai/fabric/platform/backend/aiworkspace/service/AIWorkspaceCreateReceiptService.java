package com.ai.fabric.platform.backend.aiworkspace.service;

import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceInstallationSummary;
import com.ai.fabric.platform.backend.aiworkspace.model.CreateAIWorkspaceInstallationRequest;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;

/** A receipt belongs to the installation transaction, never an independently committed reservation. */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class AIWorkspaceCreateReceiptService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public AIWorkspaceCreateReceiptService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public Receipt begin(String customerId, CreateAIWorkspaceInstallationRequest request, String operationKey) {
        if (operationKey == null) return null;
        if (operationKey.isBlank() || operationKey.length() > 128 || !operationKey.equals(operationKey.trim())
            || operationKey.chars().anyMatch(Character::isISOControl)) {
            throw new ResponseStatusException(BAD_REQUEST, "A valid AI Workspace create Idempotency-Key is required.");
        }
        String actor = PlatformSecurityContext.actorIdOrSystem();
        String fingerprint = fingerprint(Map.of("command", "AI_WORKSPACE_CREATE", "customerId", customerId,
            "actorId", actor, "actorRole", PlatformSecurityContext.actorRoleOrSystem(),
            "consumerId", request.consumerId() == null ? "" : request.consumerId(), "request", request));
        // ON CONFLICT waits for an in-flight create, including when its consumer differs. The PK is the final guard.
        int inserted = jdbc.update("""
            INSERT INTO ai_workspace_create_receipts(customer_id, operation_key, actor_id, request_fingerprint, created_at)
            VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT (customer_id, operation_key) DO NOTHING
            """, customerId, operationKey, actor, fingerprint);
        Receipt receipt = jdbc.queryForObject("""
            SELECT customer_id, operation_key, request_fingerprint, installation_id, result_json
            FROM ai_workspace_create_receipts WHERE customer_id = ? AND operation_key = ? FOR UPDATE
            """, (row, ignored) -> new Receipt(row.getString("customer_id"), row.getString("operation_key"),
                row.getString("request_fingerprint"), row.getString("installation_id"), row.getString("result_json")),
            customerId, operationKey);
        if (receipt == null || !fingerprint.equals(receipt.requestFingerprint())) {
            throw new ResponseStatusException(CONFLICT, "AI Workspace create Idempotency-Key was already used for a different request or actor.");
        }
        if (inserted == 0 && receipt.installationId() == null) {
            throw new ResponseStatusException(CONFLICT, "AI Workspace create receipt requires reconciliation.");
        }
        return receipt;
    }

    public AIWorkspaceInstallationSummary replay(Receipt receipt) {
        if (receipt == null || receipt.installationId() == null || receipt.resultJson() == null) {
            throw new IllegalStateException("The AI Workspace create receipt is incomplete.");
        }
        try {
            var result = mapper.readValue(receipt.resultJson(), AIWorkspaceInstallationSummary.class);
            if (result == null || !receipt.customerId().equals(result.customerId())
                || !receipt.installationId().equals(result.installationId())) {
                throw new IllegalStateException("The AI Workspace create receipt does not match its result.");
            }
            return result;
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("The AI Workspace create receipt cannot be read.", ex);
        }
    }

    public void complete(Receipt receipt, AIWorkspaceInstallationSummary result) {
        if (receipt == null) return;
        if (!receipt.customerId().equals(result.customerId())) {
            throw new IllegalStateException("The AI Workspace create receipt customer does not match.");
        }
        int updated = jdbc.update("""
            UPDATE ai_workspace_create_receipts SET installation_id = ?, result_json = ?, completed_at = CURRENT_TIMESTAMP
            WHERE customer_id = ? AND operation_key = ? AND request_fingerprint = ? AND installation_id IS NULL
            """, result.installationId(), write(result), receipt.customerId(), receipt.operationKey(), receipt.requestFingerprint());
        if (updated != 1) throw new IllegalStateException("The AI Workspace create receipt was not completed exactly once.");
    }

    private String fingerprint(Object value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(write(canonical(mapper.valueToTree(value))).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable.", ex);
        }
    }

    private JsonNode canonical(JsonNode value) {
        if (value.isObject()) {
            var sorted = new TreeMap<String, JsonNode>();
            value.fields().forEachRemaining(field -> sorted.put(field.getKey(), canonical(field.getValue())));
            var result = mapper.createObjectNode();
            sorted.forEach(result::set);
            return result;
        }
        if (value.isArray()) {
            var result = mapper.createArrayNode();
            value.forEach(item -> result.add(canonical(item)));
            return result;
        }
        return value;
    }

    private String write(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (java.io.IOException ex) { throw new IllegalStateException("AI Workspace create receipt serialization failed.", ex); }
    }

    public record Receipt(String customerId, String operationKey, String requestFingerprint,
                          String installationId, String resultJson) {}
}
