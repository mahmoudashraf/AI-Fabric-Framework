package com.loomai.verification.vehicleprovider.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loomai.verification.vehicleprovider.config.ManagedDependencyProperties;
import com.loomai.verification.vehicleprovider.model.SimulatorContracts.Profile;
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
    private final SimulatorService simulator;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public ManagedDependencyLifecycleService(ManagedDependencyProperties properties,
        SimulatorService simulator, JdbcTemplate jdbc, ObjectMapper mapper) {
        this.properties = properties;
        this.simulator = simulator;
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public boolean authorized(String supplied) {
        return supplied != null && properties.key() != null && MessageDigest.isEqual(
            properties.key().getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8));
    }

    @Transactional
    public Readback execute(Request request) {
        validate(request);
        String fingerprint = sha256(write(request));
        var existing = jdbc.query("select request_sha256, response_json from loomai_managed_dependency_operation where operation_key = ?",
            (rs, row) -> new Stored(rs.getString(1), rs.getString(2)), request.operationKey()).stream().findFirst();
        if (existing.isPresent()) {
            if (!existing.get().fingerprint().equals(fingerprint)) conflict("MANAGED_DEPENDENCY_OPERATION_CONFLICT");
            Readback prior = read(existing.get().response());
            return new Readback(prior.contractVersion(), prior.state(), prior.customerId(), prior.tenantId(),
                prior.deploymentId(), prior.targetProfileId(), prior.versionId(), prior.slot(), prior.serviceRef(),
                prior.accountId(), prior.bindingId(), prior.operationKey(), prior.activeWork(), prior.observedAt(),
                prior.evidenceReference(), true);
        }
        String state = switch (request.command()) {
            case "INITIALIZE" -> "READY";
            case "DISABLE" -> "DISABLED";
            case "VERIFY_UNUSED" -> "UNUSED";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MANAGED_DEPENDENCY_COMMAND_INVALID");
        };
        var run = simulator.status().fixture();
        long version = simulator.sourceVersion(Profile.AUTOTRADER, simulator.accountId(Profile.AUTOTRADER));
        Instant now = Instant.now();
        int updated = jdbc.update("update loomai_managed_dependency_state set lifecycle_state = ?, binding_id = ?, updated_at = ? where singleton_id = 1",
            state, properties.bindingId(), Timestamp.from(now));
        if (updated == 0) jdbc.update("insert into loomai_managed_dependency_state (singleton_id, lifecycle_state, binding_id, updated_at) values (1, ?, ?, ?)",
            state, properties.bindingId(), Timestamp.from(now));
        Readback result = readback(request, state, 0, now,
            "simulator:" + run.resetId() + ":source-version:" + version, false);
        jdbc.update("insert into loomai_managed_dependency_operation (operation_key, request_sha256, response_json, created_at) values (?, ?, ?, ?)",
            request.operationKey(), fingerprint, write(result), Timestamp.from(now));
        return result;
    }

    public boolean available() {
        return jdbc.query("select lifecycle_state from loomai_managed_dependency_state where singleton_id = 1",
            (rs, row) -> rs.getString(1)).stream().findFirst().map("READY"::equals).orElse(true);
    }

    private void validate(Request request) {
        require(request); requireConfigured();
        if (!VERSION.equals(request.contractVersion()) || !"scoped-seed-sync-canary/v1".equals(request.initializationContract())
            || !properties.customerId().equals(request.customerId()) || !properties.tenantId().equals(request.tenantId())
            || !properties.deploymentId().equals(request.deploymentId()) || !properties.targetProfileId().equals(request.targetProfileId())
            || !properties.versionId().equals(request.versionId()) || !properties.slot().equals(request.slot())
            || !properties.serviceRef().equals(request.serviceRef()) || !properties.accountId().equals(request.accountId())
            || !properties.bindingId().equals(request.bindingId())) conflict("MANAGED_DEPENDENCY_BINDING_MISMATCH");
    }

    private void requireConfigured() {
        if (properties.key() == null || properties.key().length() < 32 || !StringUtils.hasText(properties.customerId())
            || !StringUtils.hasText(properties.tenantId()) || !StringUtils.hasText(properties.deploymentId())
            || !StringUtils.hasText(properties.targetProfileId()) || !StringUtils.hasText(properties.versionId())
            || !StringUtils.hasText(properties.slot()) || !StringUtils.hasText(properties.serviceRef())
            || !StringUtils.hasText(properties.accountId()) || !StringUtils.hasText(properties.bindingId())) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "MANAGED_DEPENDENCY_IDENTITY_NOT_CONFIGURED");
        }
    }

    private void require(Request request) {
        if (request == null || !StringUtils.hasText(request.operationKey()) || request.operationKey().length() > 240)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MANAGED_DEPENDENCY_REQUEST_INVALID");
    }

    private Readback readback(Request r, String state, long active, Instant at, String evidence, boolean replay) {
        return new Readback(VERSION, state, r.customerId(), r.tenantId(), r.deploymentId(), r.targetProfileId(),
            r.versionId(), r.slot(), r.serviceRef(), r.accountId(), r.bindingId(), r.operationKey(), active, at, evidence, replay);
    }
    private String write(Object value) { try { return mapper.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException(e); } }
    private Readback read(String value) { try { return mapper.readValue(value, Readback.class); } catch (Exception e) { throw new IllegalStateException(e); } }
    private String sha256(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
    private void conflict(String reason) { throw new ResponseStatusException(HttpStatus.CONFLICT, reason); }
    private record Stored(String fingerprint, String response) { }
    public record Request(String contractVersion, String command, String initializationContract, String customerId,
        String tenantId, String deploymentId, String targetProfileId, String versionId, String slot,
        String serviceRef, String accountId, String bindingId, String operationKey) { }
    public record Readback(String contractVersion, String state, String customerId, String tenantId,
        String deploymentId, String targetProfileId, String versionId, String slot, String serviceRef,
        String accountId, String bindingId, String operationKey, long activeWork, Instant observedAt,
        String evidenceReference, boolean replay) { }
}
