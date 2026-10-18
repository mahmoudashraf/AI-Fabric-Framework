package com.ai.infrastructure.connector.rest.persistence;

import com.ai.infrastructure.connector.rest.service.ProviderCallException;
import com.ai.infrastructure.connector.rest.service.ProviderErrorClass;
import com.ai.infrastructure.connector.rest.util.Hashing;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Claims commit before provider/index calls; an uncertain command must be reconciled, never auto-replayed. */
final class JdbcIntegrationAdminCommands {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final TransactionTemplate transactions;

    JdbcIntegrationAdminCommands(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper.copy().findAndRegisterModules();
        this.transactions = new TransactionTemplate(new DataSourceTransactionManager(
            Objects.requireNonNull(jdbc.getDataSource(), "Connector command store requires a DataSource.")));
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    <T> T execute(String idempotencyKey, String operation, String resource, Class<T> type, Supplier<T> mutation) {
        String key = IntegrationStateRepository.requireAdminCommandKey(idempotencyKey);
        if (operation == null || operation.isBlank() || resource == null || resource.isBlank()) {
            throw new ProviderCallException(ProviderErrorClass.BAD_REQUEST, 0, "Integration command scope is required.");
        }
        String commandId = Hashing.sha256Hex(key);
        String fingerprint = Hashing.sha256Hex(write(List.of(operation, resource)));
        Claim claim;
        try {
            claim = transactions.execute(status -> {
                Instant now = Instant.now();
                int inserted = jdbc.update("""
                    INSERT INTO integration_admin_command
                      (command_id, operation, request_fingerprint, state, created_at, updated_at)
                    VALUES (?, ?, ?, 'STARTED', ?, ?)
                    ON CONFLICT (command_id) DO NOTHING
                    """, commandId, operation, fingerprint, Timestamp.from(now), Timestamp.from(now));
                var receipt = jdbc.queryForMap(
                    "SELECT request_fingerprint, state, response_json FROM integration_admin_command WHERE command_id = ?",
                    commandId);
                if (!fingerprint.equals(receipt.get("request_fingerprint"))) {
                    throw new ProviderCallException(ProviderErrorClass.BAD_REQUEST, 409, "IDEMPOTENCY_KEY_REUSED");
                }
                if (inserted == 1) return new Claim(true, null);
                if ("COMPLETED".equals(receipt.get("state"))) return new Claim(false, (String) receipt.get("response_json"));
                throw new ProviderCallException(ProviderErrorClass.BAD_REQUEST, 409,
                    "INTEGRATION_COMMAND_OUTCOME_UNCONFIRMED: inspect source/event state before issuing a new key.");
            });
        } catch (ProviderCallException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw unavailable("Durable integration command store is unavailable.");
        }
        if (claim == null) throw unavailable("Durable integration command claim was not persisted.");
        if (!claim.owner()) return read(claim.responseJson(), type);
        try {
            T result = Objects.requireNonNull(mutation.get(), "Integration admin mutation returned no result.");
            String response = write(result);
            if (response.length() > 2 * 1024 * 1024) throw unavailable("Integration command receipt is too large.");
            transactions.executeWithoutResult(status -> {
                int changed = jdbc.update("""
                    UPDATE integration_admin_command SET state = 'COMPLETED', response_json = ?, updated_at = ?
                     WHERE command_id = ? AND state = 'STARTED'
                    """, response, Timestamp.from(Instant.now()), commandId);
                if (changed != 1) throw unavailable("Integration command receipt fence changed.");
            });
            return result;
        } catch (RuntimeException failure) {
            try {
                transactions.executeWithoutResult(status -> jdbc.update("""
                    UPDATE integration_admin_command SET state = 'OUTCOME_UNKNOWN', updated_at = ?
                     WHERE command_id = ? AND state = 'STARTED'
                    """, Timestamp.from(Instant.now()), commandId));
            } catch (RuntimeException receiptFailure) {
                failure.addSuppressed(receiptFailure);
            }
            if (failure instanceof ProviderCallException providerFailure) throw providerFailure;
            throw unavailable("Integration command outcome is unconfirmed; inspect source/event state before issuing a new key.");
        }
    }

    private String write(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception failure) { throw unavailable("Integration command receipt serialization failed."); }
    }

    private <T> T read(String json, Class<T> type) {
        try { return Objects.requireNonNull(mapper.readValue(json, type)); }
        catch (Exception failure) { throw unavailable("Integration command receipt is unavailable."); }
    }

    private ProviderCallException unavailable(String message) {
        return new ProviderCallException(ProviderErrorClass.SERVICE_UNAVAILABLE, 0, message);
    }

    private record Claim(boolean owner, String responseJson) { }
}
