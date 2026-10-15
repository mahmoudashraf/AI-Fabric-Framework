package com.loomai.inbox.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loomai.inbox.config.BasicInboxProperties;
import com.loomai.inbox.config.ManagedDependencyProperties;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;

@Service
public class ManagedDependencyLifecycleService {
    public static final String VERSION = "loomai-managed-dependency-lifecycle/v1";
    private final ManagedDependencyProperties managed;
    private final BasicInboxProperties inbox;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public ManagedDependencyLifecycleService(ManagedDependencyProperties managed,
        BasicInboxProperties inbox, JdbcTemplate jdbc, ObjectMapper mapper) {
        this.managed = managed; this.inbox = inbox; this.jdbc = jdbc; this.mapper = mapper;
    }

    public boolean authorized(String supplied) {
        return supplied != null && managed.key() != null && MessageDigest.isEqual(
            managed.key().getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8));
    }

    @Transactional
    public Readback execute(Request request) {
        validate(request);
        String fingerprint = sha256(write(request));
        var existing = jdbc.query("SELECT request_sha256, response_json FROM loomai_managed_dependency_operation WHERE operation_key = ?",
            (rs, row) -> new Stored(rs.getString(1), rs.getString(2)), request.operationKey()).stream().findFirst();
        if (existing.isPresent()) {
            if (!existing.get().fingerprint().equals(fingerprint)) conflict("MANAGED_DEPENDENCY_OPERATION_CONFLICT");
            return copy(read(existing.get().response()), true);
        }
        long active = activeWork();
        String state;
        String evidence;
        switch (request.command()) {
            case "INITIALIZE" -> {
                if (!managed.bindingId().equals(inbox.getBindingId()) || !managed.tenantId().equals(inbox.getTenantId())
                    || !managed.deploymentId().equals(inbox.getDeploymentId())) conflict("INBOX_RUNTIME_BINDING_MISMATCH");
                URI callback = URI.create(inbox.getConnectorCallbackUrl());
                state = "READY";
                evidence = "inbox:" + managed.bindingId() + ":callback-host:" + callback.getHost();
            }
            case "DISABLE" -> { state = "DISABLED"; evidence = "inbox:disabled:" + managed.bindingId(); }
            case "VERIFY_UNUSED" -> {
                if (active != 0) conflict("MANAGED_DEPENDENCY_ACTIVE_WORK_REMAINS");
                state = "UNUSED"; evidence = "inbox:unused:" + managed.bindingId();
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MANAGED_DEPENDENCY_COMMAND_INVALID");
        }
        Instant now = Instant.now();
        int updated = jdbc.update("UPDATE loomai_managed_dependency_state SET lifecycle_state = ?, binding_id = ?, updated_at = ? WHERE singleton_id = 1",
            state, managed.bindingId(), Timestamp.from(now));
        if (updated == 0) jdbc.update("INSERT INTO loomai_managed_dependency_state (singleton_id, lifecycle_state, binding_id, updated_at) VALUES (1, ?, ?, ?)",
            state, managed.bindingId(), Timestamp.from(now));
        Readback result = readback(request, state, active, now, evidence, false);
        jdbc.update("INSERT INTO loomai_managed_dependency_operation (operation_key, request_sha256, response_json, created_at) VALUES (?, ?, ?, ?)",
            request.operationKey(), fingerprint, write(result), Timestamp.from(now));
        return result;
    }

    public boolean available() {
        return jdbc.query("SELECT lifecycle_state FROM loomai_managed_dependency_state WHERE singleton_id = 1",
            (rs, row) -> rs.getString(1)).stream().findFirst().map("READY"::equals).orElse(true);
    }

    private long activeWork() {
        Long conversations = jdbc.queryForObject("SELECT COUNT(*) FROM inbox_conversation WHERE binding_id = ? AND status <> 'CLOSED'",
            Long.class, managed.bindingId());
        Long callbacks = jdbc.queryForObject("SELECT COUNT(*) FROM inbox_callback_outbox callback JOIN inbox_conversation conversation ON conversation.handover_id = callback.handover_id WHERE conversation.binding_id = ? AND callback.status IN ('PENDING','LEASED')",
            Long.class, managed.bindingId());
        return (conversations == null ? 0 : conversations) + (callbacks == null ? 0 : callbacks);
    }
    private void validate(Request r) {
        requireConfigured();
        if (r == null || !StringUtils.hasText(r.operationKey()) || r.operationKey().length() > 240)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MANAGED_DEPENDENCY_REQUEST_INVALID");
        if (!VERSION.equals(r.contractVersion()) || !"inbox-callback-canary/v1".equals(r.initializationContract())
            || !managed.customerId().equals(r.customerId()) || !managed.tenantId().equals(r.tenantId())
            || !managed.deploymentId().equals(r.deploymentId()) || !managed.targetProfileId().equals(r.targetProfileId())
            || !managed.versionId().equals(r.versionId()) || !managed.slot().equals(r.slot())
            || !managed.serviceRef().equals(r.serviceRef()) || !managed.accountId().equals(r.accountId())
            || !managed.bindingId().equals(r.bindingId())) conflict("MANAGED_DEPENDENCY_BINDING_MISMATCH");
    }
    private void requireConfigured() {
        if (managed.key() == null || managed.key().length() < 32 || !StringUtils.hasText(managed.customerId())
            || !StringUtils.hasText(managed.tenantId()) || !StringUtils.hasText(managed.deploymentId())
            || !StringUtils.hasText(managed.targetProfileId()) || !StringUtils.hasText(managed.versionId())
            || !StringUtils.hasText(managed.slot()) || !StringUtils.hasText(managed.serviceRef())
            || !StringUtils.hasText(managed.accountId()) || !StringUtils.hasText(managed.bindingId()))
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "MANAGED_DEPENDENCY_IDENTITY_NOT_CONFIGURED");
    }
    private Readback readback(Request r, String state, long active, Instant at, String evidence, boolean replay) {
        return new Readback(VERSION, state, r.customerId(), r.tenantId(), r.deploymentId(), r.targetProfileId(), r.versionId(), r.slot(), r.serviceRef(), r.accountId(), r.bindingId(), r.operationKey(), active, at, evidence, replay);
    }
    private Readback copy(Readback r, boolean replay) { return new Readback(r.contractVersion(), r.state(), r.customerId(), r.tenantId(), r.deploymentId(), r.targetProfileId(), r.versionId(), r.slot(), r.serviceRef(), r.accountId(), r.bindingId(), r.operationKey(), r.activeWork(), r.observedAt(), r.evidenceReference(), replay); }
    private String write(Object v) { try { return mapper.writeValueAsString(v); } catch (Exception e) { throw new IllegalStateException(e); } }
    private Readback read(String v) { try { return mapper.readValue(v, Readback.class); } catch (Exception e) { throw new IllegalStateException(e); } }
    private String sha256(String v) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
    private void conflict(String reason) { throw new ResponseStatusException(HttpStatus.CONFLICT, reason); }
    private record Stored(String fingerprint, String response) { }
    public record Request(String contractVersion, String command, String initializationContract, String customerId, String tenantId,
        String deploymentId, String targetProfileId, String versionId, String slot, String serviceRef, String accountId,
        String bindingId, String operationKey) { }
    public record Readback(String contractVersion, String state, String customerId, String tenantId, String deploymentId,
        String targetProfileId, String versionId, String slot, String serviceRef, String accountId, String bindingId,
        String operationKey, long activeWork, Instant observedAt, String evidenceReference, boolean replay) { }
}
