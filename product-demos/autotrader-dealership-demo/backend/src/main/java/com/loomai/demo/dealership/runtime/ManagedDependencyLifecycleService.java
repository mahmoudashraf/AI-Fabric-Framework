package com.loomai.demo.dealership.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loomai.demo.dealership.config.ManagedDependencyProperties;
import com.loomai.demo.dealership.inventory.VehicleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;

@Service
public class ManagedDependencyLifecycleService {
    public static final String VERSION = "loomai-managed-dependency-lifecycle/v1";
    private final ManagedDependencyProperties properties;
    private final RuntimeConnectorOperationsClient connector;
    private final VehicleRepository vehicles;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public ManagedDependencyLifecycleService(ManagedDependencyProperties properties,
        RuntimeConnectorOperationsClient connector, VehicleRepository vehicles, JdbcTemplate jdbc, ObjectMapper mapper) {
        this.properties = properties; this.connector = connector; this.vehicles = vehicles; this.jdbc = jdbc; this.mapper = mapper;
    }

    public boolean authorized(String supplied) {
        return supplied != null && properties.key() != null && MessageDigest.isEqual(
            properties.key().getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * The source reconcile is idempotent but remote, so it must not run while a
     * database transaction is held. The reviewed topology uses one receiver
     * replica; serialization prevents duplicate in-process execution while the
     * durable operation ledger handles restart replay.
     */
    public synchronized Readback execute(Request request) {
        validate(request);
        String fingerprint = sha256(write(request));
        var existing = jdbc.query("SELECT request_sha256, response_json FROM loomai_managed_dependency_operation WHERE operation_key = ?",
            (rs, row) -> new Stored(rs.getString(1), rs.getString(2)), request.operationKey()).stream().findFirst();
        if (existing.isPresent()) {
            if (!existing.get().fingerprint().equals(fingerprint)) conflict("MANAGED_DEPENDENCY_OPERATION_CONFLICT");
            Readback prior = read(existing.get().response());
            return copy(prior, true);
        }
        long active = activeWork();
        String state;
        String evidence;
        switch (request.command()) {
            case "INITIALIZE" -> {
                JsonNode reconcile = connector.reconcileSource();
                JsonNode status = connector.sourceStatus();
                if (vehicles.count() < 1 || status == null || status.isNull()) conflict("MANAGED_DEPENDENCY_SEED_SYNC_NOT_READY");
                state = "READY";
                evidence = "receiver:" + connector.configuredSourceId() + ":vehicles:" + vehicles.count()
                    + ":reconcile:" + sha256(reconcile == null ? "null" : reconcile.toString()).substring(0, 16);
            }
            case "DISABLE" -> { state = "DISABLED"; evidence = "receiver:disabled:" + properties.bindingId(); }
            case "VERIFY_UNUSED" -> {
                if (active != 0) conflict("MANAGED_DEPENDENCY_ACTIVE_WORK_REMAINS");
                state = "UNUSED"; evidence = "receiver:unused:" + properties.bindingId();
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MANAGED_DEPENDENCY_COMMAND_INVALID");
        }
        Instant now = Instant.now();
        int updated = jdbc.update("UPDATE loomai_managed_dependency_state SET lifecycle_state = ?, binding_id = ?, updated_at = ?, operation_key = ? WHERE singleton_id = 1",
            state, properties.bindingId(), Timestamp.from(now), request.operationKey());
        if (updated == 0) jdbc.update("INSERT INTO loomai_managed_dependency_state (singleton_id, lifecycle_state, binding_id, updated_at, operation_key) VALUES (1, ?, ?, ?, ?)",
            state, properties.bindingId(), Timestamp.from(now), request.operationKey());
        Readback result = readback(request, state, active, now, evidence, false);
        jdbc.update("INSERT INTO loomai_managed_dependency_operation (operation_key, request_sha256, response_json, created_at) VALUES (?, ?, ?, ?)",
            request.operationKey(), fingerprint, write(result), Timestamp.from(now));
        return result;
    }

    @Transactional(readOnly = true)
    public synchronized CurrentReadback current(String challenge) {
        requireConfigured();
        if (challenge == null || !challenge.matches("[A-Za-z0-9-]{16,128}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MANAGED_DEPENDENCY_CHALLENGE_INVALID");
        }
        var state = jdbc.query("SELECT lifecycle_state, binding_id, operation_key FROM loomai_managed_dependency_state WHERE singleton_id = 1",
            (rs, row) -> new CurrentState(rs.getString(1), rs.getString(2), rs.getString(3))).stream()
            .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "MANAGED_DEPENDENCY_NOT_INITIALIZED"));
        if (!properties.bindingId().equals(state.bindingId()) || !StringUtils.hasText(state.operationKey())) {
            conflict("MANAGED_DEPENDENCY_CURRENT_STATE_UNPROVEN");
        }
        var current = new Readback(VERSION, state.state(), properties.customerId(), properties.tenantId(),
            properties.deploymentId(), properties.targetProfileId(), properties.versionId(), properties.slot(), properties.serviceRef(),
            properties.accountId(), state.bindingId(), state.operationKey(), activeWork(), Instant.now(),
            "receiver:current:" + state.bindingId() + ":" + state.operationKey(), false);
        return new CurrentReadback(current, challenge);
    }

    public boolean available() {
        return jdbc.query("SELECT lifecycle_state FROM loomai_managed_dependency_state WHERE singleton_id = 1",
            (rs, row) -> rs.getString(1)).stream().findFirst().map("READY"::equals).orElse(true);
    }

    private long activeWork() {
        Long value = jdbc.queryForObject("SELECT COUNT(*) FROM dealership_sync_run WHERE status NOT IN ('COMPLETED','FAILED')", Long.class);
        return value == null ? 0 : value;
    }
    private void validate(Request r) {
        requireConfigured();
        if (r == null || !StringUtils.hasText(r.operationKey()) || r.operationKey().length() > 240)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MANAGED_DEPENDENCY_REQUEST_INVALID");
        if (!VERSION.equals(r.contractVersion()) || !"scoped-seed-sync-canary/v1".equals(r.initializationContract())
            || !properties.customerId().equals(r.customerId()) || !properties.tenantId().equals(r.tenantId())
            || !properties.deploymentId().equals(r.deploymentId()) || !properties.targetProfileId().equals(r.targetProfileId())
            || !properties.versionId().equals(r.versionId()) || !properties.slot().equals(r.slot())
            || !properties.serviceRef().equals(r.serviceRef()) || !properties.accountId().equals(r.accountId())
            || !properties.bindingId().equals(r.bindingId())) conflict("MANAGED_DEPENDENCY_BINDING_MISMATCH");
    }
    private void requireConfigured() {
        if (properties.key() == null || properties.key().length() < 32 || !StringUtils.hasText(properties.customerId())
            || !StringUtils.hasText(properties.tenantId()) || !StringUtils.hasText(properties.deploymentId())
            || !StringUtils.hasText(properties.targetProfileId()) || !StringUtils.hasText(properties.versionId())
            || !StringUtils.hasText(properties.slot()) || !StringUtils.hasText(properties.serviceRef())
            || !StringUtils.hasText(properties.accountId()) || !StringUtils.hasText(properties.bindingId()))
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "MANAGED_DEPENDENCY_IDENTITY_NOT_CONFIGURED");
    }
    private Readback readback(Request r, String state, long active, Instant at, String evidence, boolean replay) {
        return new Readback(VERSION, state, r.customerId(), r.tenantId(), r.deploymentId(), r.targetProfileId(), r.versionId(),
            r.slot(), r.serviceRef(), r.accountId(), r.bindingId(), r.operationKey(), active, at, evidence, replay);
    }
    private Readback copy(Readback r, boolean replay) { return new Readback(r.contractVersion(), r.state(), r.customerId(), r.tenantId(), r.deploymentId(), r.targetProfileId(), r.versionId(), r.slot(), r.serviceRef(), r.accountId(), r.bindingId(), r.operationKey(), r.activeWork(), r.observedAt(), r.evidenceReference(), replay); }
    private String write(Object v) { try { return mapper.writeValueAsString(v); } catch (Exception e) { throw new IllegalStateException(e); } }
    private Readback read(String v) { try { return mapper.readValue(v, Readback.class); } catch (Exception e) { throw new IllegalStateException(e); } }
    private String sha256(String v) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
    private void conflict(String reason) { throw new ResponseStatusException(HttpStatus.CONFLICT, reason); }
    private record Stored(String fingerprint, String response) { }
    private record CurrentState(String state, String bindingId, String operationKey) { }
    public record Request(String contractVersion, String command, String initializationContract, String customerId, String tenantId,
        String deploymentId, String targetProfileId, String versionId, String slot, String serviceRef, String accountId,
        String bindingId, String operationKey) { }
    public record Readback(String contractVersion, String state, String customerId, String tenantId, String deploymentId,
        String targetProfileId, String versionId, String slot, String serviceRef, String accountId, String bindingId,
        String operationKey, long activeWork, Instant observedAt, String evidenceReference, boolean replay) { }
    public record CurrentReadback(Readback current, String challenge) { }
}
