package com.ai.fabric.runtime.admin;

import com.ai.fabric.runtime.auth.RuntimeResolvedIdentity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import static org.springframework.http.HttpStatus.*;

/** Durable at-most-once execution for private administrative recovery commands. */
@Service
public class RuntimeAdminMutationService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final TransactionTemplate transactions;
    private final String deploymentId;
    private final String targetProfileId;
    private final String releaseId;
    private final String tenantId;

    public RuntimeAdminMutationService(DataSource dataSource, ObjectMapper mapper,
        @Value("${ai.fabric.runtime.auth.public-tokens.defaults.deployment-id:${AI_FABRIC_RUNTIME_DEPLOYMENT_ID:}}") String deploymentId,
        @Value("${loomai.runtime.target-profile-id:${LOOMAI_RUNTIME_TARGET_PROFILE_ID:}}") String targetProfileId,
        @Value("${loomai.runtime.release-id:${LOOMAI_RUNTIME_RELEASE_ID:}}") String releaseId,
        @Value("${ai.fabric.runtime.auth.public-tokens.defaults.tenant-id:${AI_FABRIC_RUNTIME_TENANT_ID:}}") String tenantId) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.mapper = mapper.copy().findAndRegisterModules();
        this.transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.deploymentId = deploymentId;
        this.targetProfileId = targetProfileId;
        this.releaseId = releaseId;
        this.tenantId = tenantId;
    }

    public <T> T execute(RuntimeResolvedIdentity identity, String operation, String resource, String idempotencyKey,
                         Class<T> resultType, Supplier<T> mutation) {
        String key = requireKey(idempotencyKey);
        requireScope(identity);
        String customerId = required(identity.getAuthContext().getCustomerId(), 128);
        String commandId = digest(write(List.of(tenantId, customerId, deploymentId, targetProfileId, releaseId, key)));
        String fingerprint = digest(write(List.of(required(operation, 64), required(resource, 512))));
        Claim claim = transactions.execute(status -> {
            Instant now = Instant.now();
            int inserted = jdbc.update("""
                INSERT INTO loomai_runtime_admin_command
                  (command_id, tenant_id, customer_id, deployment_id, target_profile_id, release_id,
                   operation, request_fingerprint, state, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'STARTED', ?, ?)
                ON CONFLICT (command_id) DO NOTHING
                """, commandId, tenantId, customerId, deploymentId, targetProfileId, releaseId,
                operation, fingerprint, Timestamp.from(now), Timestamp.from(now));
            var receipt = jdbc.queryForMap(
                "SELECT request_fingerprint, state, response_json FROM loomai_runtime_admin_command WHERE command_id = ?",
                commandId);
            if (!fingerprint.equals(receipt.get("request_fingerprint"))) {
                throw new ResponseStatusException(CONFLICT, "IDEMPOTENCY_KEY_REUSED");
            }
            if (inserted == 1) return new Claim(true, null);
            if ("COMPLETED".equals(receipt.get("state"))) return new Claim(false, (String) receipt.get("response_json"));
            throw new ResponseStatusException(CONFLICT,
                "ADMIN_MUTATION_OUTCOME_UNCONFIRMED: inspect operation state before issuing a new key.");
        });
        if (claim == null) throw new IllegalStateException("Runtime admin command claim was not persisted.");
        if (!claim.owner()) return read(claim.responseJson(), resultType);
        // The claim commits before side effects. Interrupted/failed commands are never silently re-executed.
        try {
            T result = Objects.requireNonNull(mutation.get(), "Runtime admin mutation returned no result.");
            String response = write(result);
            if (response.length() > 2 * 1024 * 1024) throw new IllegalStateException("Runtime admin receipt is too large.");
            transactions.executeWithoutResult(status -> {
                int changed = jdbc.update("""
                    UPDATE loomai_runtime_admin_command SET state = 'COMPLETED', response_json = ?, updated_at = ?
                     WHERE command_id = ? AND state = 'STARTED'
                    """, response, Timestamp.from(Instant.now()), commandId);
                if (changed != 1) throw new IllegalStateException("Runtime admin receipt fence changed.");
            });
            return result;
        } catch (RuntimeException failure) {
            try {
                transactions.executeWithoutResult(status -> jdbc.update("""
                    UPDATE loomai_runtime_admin_command SET state = 'OUTCOME_UNKNOWN', updated_at = ?
                     WHERE command_id = ? AND state = 'STARTED'
                    """, Timestamp.from(Instant.now()), commandId));
            } catch (RuntimeException receiptFailure) {
                failure.addSuppressed(receiptFailure);
            }
            throw failure;
        }
    }

    public static String requireKey(String value) {
        if (value == null || value.isEmpty() || value.length() > 128
            || value.chars().anyMatch(character -> character < 33 || character > 126)) {
            throw new ResponseStatusException(BAD_REQUEST,
                "Idempotency-Key is required and must contain 1 to 128 visible ASCII characters.");
        }
        return value;
    }

    private void requireScope(RuntimeResolvedIdentity identity) {
        if (!valid(deploymentId, 128) || !valid(targetProfileId, 128) || !valid(releaseId, 128) || !valid(tenantId, 128)) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "RUNTIME_ADMIN_TARGET_IDENTITY_REQUIRED");
        }
        if (identity == null || identity.getAuthContext() == null
            || !deploymentId.equals(identity.getAuthContext().getDeploymentId())
            || !tenantId.equals(identity.getAuthContext().getTenantId())) {
            throw new ResponseStatusException(FORBIDDEN, "RUNTIME_ADMIN_SCOPE_MISMATCH");
        }
    }

    private String required(String value, int limit) {
        if (!valid(value, limit)) throw new ResponseStatusException(BAD_REQUEST, "Runtime admin command scope is invalid.");
        return value;
    }

    private boolean valid(String value, int limit) {
        return value != null && !value.isBlank() && value.equals(value.trim()) && value.length() <= limit
            && value.chars().noneMatch(Character::isISOControl);
    }

    private String write(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception failure) { throw new IllegalStateException("Runtime admin receipt serialization failed.", failure); }
    }

    private <T> T read(String json, Class<T> type) {
        try { return Objects.requireNonNull(mapper.readValue(json, type)); }
        catch (Exception failure) { throw new IllegalStateException("Runtime admin receipt is unavailable.", failure); }
    }

    private String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception failure) { throw new IllegalStateException(failure); }
    }

    private record Claim(boolean owner, String responseJson) { }
}
