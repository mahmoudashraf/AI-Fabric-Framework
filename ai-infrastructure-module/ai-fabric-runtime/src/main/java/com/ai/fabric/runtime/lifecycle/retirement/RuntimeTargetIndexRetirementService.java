package com.ai.fabric.runtime.lifecycle.retirement;

import ai.fabric.rag.VectorDatabaseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.ai.fabric.runtime.lifecycle.retirement.RuntimeTargetRetirementContracts.*;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

/** Clears only the immutable vector scope owned by this fenced runtime target. */
@Service
public class RuntimeTargetIndexRetirementService {
    private final RuntimeTargetRetirementIdentity identity;
    private final RuntimeTargetRetirementService retirement;
    private final RuntimeRetirementTopology topology;
    private final VectorDatabaseService vectors;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final TransactionTemplate transaction;

    public RuntimeTargetIndexRetirementService(RuntimeTargetRetirementIdentity identity,
        RuntimeTargetRetirementService retirement, RuntimeRetirementTopology topology,
        VectorDatabaseService vectors, JdbcTemplate jdbc, ObjectMapper mapper,
        PlatformTransactionManager transactionManager) {
        this.identity = identity;
        this.retirement = retirement;
        this.topology = topology;
        this.vectors = vectors;
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public IndexRetirementReadback retire(IndexRetirementCommand command) {
        Scope scope = requireScope(command);
        String payloadDigest = digest(json(command));
        IndexRetirementReadback replay = receipt(scope.binding().operationId(), payloadDigest);
        if (replay != null) return withReplay(replay);

        Map<String, Long> before = counts(scope.vectorScope().entityTypes());
        long removed = 0;
        for (String entityType : scope.vectorScope().entityTypes()) {
            removed = Math.addExact(removed, vectors.clearVectorsByEntityType(entityType));
        }
        Map<String, Long> after = counts(scope.vectorScope().entityTypes());
        long remaining = total(after);
        if (remaining != 0) throw conflict("TARGET_INDEX_RECLAMATION_INCOMPLETE");
        Instant now = Instant.now();
        String evidence = "runtime-target-index:" + command.binding().target().releaseId() + ":"
            + digest(json(Map.of("before", before, "after", after, "scope", scope.vectorScope())));
        IndexRetirementReadback result = new IndexRetirementReadback(command.binding().target(), identity.tenantId(),
            command.binding().operationId(), command.binding().fenceEpoch(), topology.fingerprint(),
            scope.vectorScope().provider(), scope.vectorScope().scopeType(), scope.vectorScope().scopeValue(),
            after, remaining, removed, now, evidence, true, false);
        return transaction.execute(status -> {
            IndexRetirementReadback concurrent = receipt(command.binding().operationId(), payloadDigest);
            if (concurrent != null) return withReplay(concurrent);
            jdbc.update("""
                INSERT INTO runtime_release_index_retirement
                    (tenant_id, deployment_id, target_profile_id, release_id, operation_id, fence_epoch, request_fingerprint, topology_fingerprint,
                     payload_digest, result_json, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, identity.tenantId(), identity.target().deploymentId(), identity.target().targetProfileId(), identity.target().releaseId(),
                command.binding().operationId(), command.binding().fenceEpoch(),
                command.binding().requestFingerprint(), topology.fingerprint(), payloadDigest,
                json(result), now.atOffset(ZoneOffset.UTC));
            return result;
        });
    }

    public IndexRetirementReadback readback(IndexRetirementCommand command) {
        Scope scope = requireScope(command);
        String payloadDigest = digest(json(command));
        IndexRetirementReadback receipt = receipt(scope.binding().operationId(), payloadDigest);
        Map<String, Long> counts = counts(scope.vectorScope().entityTypes());
        long total = total(counts);
        Instant now = Instant.now();
        return new IndexRetirementReadback(command.binding().target(), identity.tenantId(),
            command.binding().operationId(), command.binding().fenceEpoch(), topology.fingerprint(),
            scope.vectorScope().provider(), scope.vectorScope().scopeType(), scope.vectorScope().scopeValue(),
            counts, total, receipt == null ? 0 : receipt.removedVectors(), now,
            receipt == null ? "runtime-target-index:pending:" + command.binding().target().releaseId()
                : receipt.evidenceReference(), total == 0 && receipt != null, receipt != null);
    }

    private Scope requireScope(IndexRetirementCommand command) {
        if (command == null || command.binding() == null || command.topologyFingerprint() == null
            || command.topologyFingerprint().isBlank()) throw conflict("TARGET_INDEX_COMMAND_INVALID");
        Binding binding = command.binding();
        identity.requireTarget(binding.target());
        Readback current = retirement.status(binding.target());
        if (current.fenceEpoch() != binding.fenceEpoch()
            || !Objects.equals(current.operationId(), binding.operationId())
            || !Objects.equals(current.requestFingerprint(), binding.requestFingerprint())
            || !current.authorityRevoked()
            || current.state() != State.AUTHORITY_REVOKED && current.state() != State.RECLAIM_STARTED) {
            throw conflict("TARGET_INDEX_RETIREMENT_BINDING_CHANGED");
        }
        if (!topology.fingerprint().equals(command.topologyFingerprint())) {
            throw conflict("TARGET_INDEX_TOPOLOGY_CHANGED");
        }
        RuntimeRetirementTopology.VectorScope vectorScope = topology.vectorScope();
        if (vectorScope == null || !vectors.supportsClearByEntityType()) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "TARGET_INDEX_RECLAMATION_UNSUPPORTED");
        }
        validateProviderScope(vectorScope, vectors.adminDiagnostics());
        return new Scope(binding, vectorScope);
    }

    private void validateProviderScope(RuntimeRetirementTopology.VectorScope scope, Map<String, Object> diagnostics) {
        String provider = text(diagnostics, "provider");
        String actualType = text(diagnostics, "scopeType");
        boolean dedicated = "DEDICATED_RUNTIME_STORAGE".equals(scope.scopeType());
        if (!scope.provider().equalsIgnoreCase(provider)
            || dedicated && (!identity.dedicatedStore() || Boolean.TRUE.equals(diagnostics.get("sharedStorage")))
            || !dedicated && (!scope.scopeType().equals(actualType)
                || !scope.scopeValue().equals(text(diagnostics, "scopePrefix")))) {
            throw conflict("TARGET_INDEX_PROVIDER_SCOPE_CHANGED");
        }
    }

    private Map<String, Long> counts(List<String> entityTypes) {
        Map<String, Long> result = new LinkedHashMap<>();
        entityTypes.stream().sorted().forEach(entityType -> {
            long count = vectors.getVectorCountByEntityType(entityType);
            if (count < 0) throw conflict("TARGET_INDEX_COUNT_INVALID");
            result.put(entityType, count);
        });
        return Map.copyOf(result);
    }

    private long total(Map<String, Long> counts) {
        long total = 0;
        for (long count : counts.values()) total = Math.addExact(total, count);
        return total;
    }

    private IndexRetirementReadback receipt(String operationId, String payloadDigest) {
        List<String[]> rows;
        try {
            rows = jdbc.query("""
                SELECT payload_digest, result_json FROM runtime_release_index_retirement
                WHERE tenant_id = ? AND deployment_id = ? AND target_profile_id = ? AND release_id = ? AND operation_id = ?
                """, (rs, row) -> new String[]{rs.getString(1), rs.getString(2)}, identity.tenantId(),
                identity.target().deploymentId(), identity.target().targetProfileId(), identity.target().releaseId(), operationId);
        } catch (RuntimeException error) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "TARGET_INDEX_RETIREMENT_MIGRATION_REQUIRED");
        }
        if (rows.isEmpty()) return null;
        if (!rows.getFirst()[0].equals(payloadDigest)) throw conflict("TARGET_INDEX_COMMAND_REUSE");
        try { return mapper.readValue(rows.getFirst()[1], IndexRetirementReadback.class); }
        catch (Exception error) { throw conflict("TARGET_INDEX_RECEIPT_INVALID"); }
    }

    private IndexRetirementReadback withReplay(IndexRetirementReadback value) {
        return new IndexRetirementReadback(value.target(), value.tenantId(), value.operationId(), value.fenceEpoch(),
            value.topologyFingerprint(), value.provider(), value.scopeType(), value.scopeValue(),
            value.countsByEntityType(), value.totalVectors(), value.removedVectors(), value.observedAt(),
            value.evidenceReference(), value.complete(), true);
    }

    private String text(Map<String, Object> values, String key) {
        Object value = values == null ? null : values.get(key);
        return value == null ? "" : value.toString().trim();
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception error) { throw new IllegalStateException("Unable to encode target index evidence.", error); }
    }

    private String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception error) { throw new IllegalStateException("SHA-256 is unavailable.", error); }
    }

    private ResponseStatusException conflict(String code) { return new ResponseStatusException(CONFLICT, code); }
    private record Scope(Binding binding, RuntimeRetirementTopology.VectorScope vectorScope) { }
}
