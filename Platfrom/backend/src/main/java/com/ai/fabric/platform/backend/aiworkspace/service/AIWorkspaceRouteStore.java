package com.ai.fabric.platform.backend.aiworkspace.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.CONFLICT;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Short database transactions only. No customer transaction locks this shared state. */
@Service
public class AIWorkspaceRouteStore {
    static final Duration LEASE = Duration.ofSeconds(90);
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final TransactionTemplate transaction;
    private final PlatformAuditService audit;
    private final Clock clock;

    @Autowired
    public AIWorkspaceRouteStore(JdbcTemplate jdbc, ObjectMapper mapper,
                                PlatformTransactionManager transactions, PlatformAuditService audit) {
        this(jdbc, mapper, transactions, audit, Clock.systemUTC());
    }

    AIWorkspaceRouteStore(JdbcTemplate jdbc, ObjectMapper mapper, PlatformTransactionManager transactions,
                          PlatformAuditService audit, Clock clock) {
        this.jdbc = jdbc; this.mapper = mapper; this.audit = audit; this.clock = clock;
        this.transaction = new TransactionTemplate(transactions);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public record Binding(String serviceId, String profileId, String profileFingerprint, String applicationUuid, List<String> historicManaged) {
        public Binding { historicManaged = List.copyOf(historicManaged); }
        String key() { return fingerprint(List.of(profileId, applicationUuid)); }
    }
    public record Claim(Binding binding, String token, long generation, String fingerprint,
                        List<String> desired, List<String> managed, List<String> inflight,
                        List<String> expected, String phase, String deploymentUuid) { }

    public record Status(String routeKey, String serviceId, String targetProfileId, String targetProfileFingerprint, String applicationUuid,
                         long generation, String desiredFingerprint, String inflightFingerprint, String phase,
                         String deploymentUuid, long appliedGeneration, String errorCode, Instant nextAttemptAt) { }

    public record ObserveDeployment(String operationKey, long expectedGeneration, String expectedFingerprint,
                                    String expectedInflightFingerprint, String deploymentUuid, String operatorEvidenceRef) { }

    public record Observation(Claim claim, String commandFingerprint, Instant intentAt, boolean replay) { }

    public List<Status> statuses() {
        return transaction.execute(ignored -> jdbc.query("SELECT * FROM ai_workspace_route_reconciliations ORDER BY route_key", this::map)
            .stream().map(s -> new Status(s.key, s.serviceId, s.profile, s.profileFingerprint, s.application, s.generation, s.fingerprint,
                s.flightFingerprint, s.phase, s.deployment, s.appliedGeneration, s.error, s.nextAttempt)).toList());
    }

    public Observation beginObservation(Binding binding, ObserveDeployment command, String actorId) {
        return transaction.execute(ignored -> {
            State state = locked(binding);
            String hash = fingerprint(List.of(binding.key(), binding.profileFingerprint(), actorId, json(command)));
            var previous = jdbc.queryForList("""
                SELECT details_json FROM platform_audit_events WHERE action = 'AI_WORKSPACE_ROUTE_OBSERVATION_ATTACHED'
                  AND target_type = 'MANAGED_PRODUCT_SERVICE' AND target_id = ?
                  AND details_json::jsonb ->> 'operationKey' = ?
                """, String.class, state.serviceId, command.operationKey);
            if (!previous.isEmpty()) {
                if (previous.size() != 1 || !auditField(previous.get(0), "commandFingerprint").equals(hash))
                    throw new ResponseStatusException(CONFLICT, "Observation operation key belongs to another command or actor.");
                return new Observation(null, hash, null, true);
            }
            refreshDesired(state);
            if (state.generation != command.expectedGeneration || !state.fingerprint.equals(command.expectedFingerprint)
                || !state.flightFingerprint.equals(command.expectedInflightFingerprint)
                || !List.of("UNKNOWN", "FAILED").contains(state.phase) || state.flightGeneration == 0) {
                throw new ResponseStatusException(CONFLICT, "Route observation snapshot is stale or not blocked.");
            }
            if (state.leaseUntil != null && state.leaseUntil.isAfter(clock.instant()))
                throw new ResponseStatusException(CONFLICT, "Route observation is already leased.");
            var intents = jdbc.queryForList("""
                SELECT created_at FROM platform_audit_events WHERE action = 'AI_WORKSPACE_ROUTE_PATCH_INTENT'
                  AND target_id = ? AND details_json::jsonb ->> 'inflightGeneration' = ? ORDER BY created_at DESC
                """, Timestamp.class, state.serviceId, Long.toString(state.flightGeneration));
            if (intents.isEmpty()) throw new ResponseStatusException(CONFLICT, "Original route intent is unavailable.");
            state.token = UUID.randomUUID().toString(); state.leaseUntil = clock.instant().plus(LEASE); save(state);
            audit.record("AI_WORKSPACE_ROUTE_OBSERVATION_INTENT", "MANAGED_PRODUCT_SERVICE", state.serviceId,
                Map.of("operationKey", command.operationKey, "commandFingerprint", hash,
                    "operatorEvidenceRef", command.operatorEvidenceRef, "generation", state.generation));
            return new Observation(new Claim(binding, state.token, state.generation, state.fingerprint, state.desired,
                state.managed, state.inflight, state.expected, state.phase, state.deployment), hash,
                intents.get(0).toInstant(), false);
        });
    }

    public boolean attachObservation(Observation observation, ObserveDeployment command) {
        return change(observation.claim, state -> {
            if (!current(observation.claim, state) || !List.of("UNKNOWN", "FAILED").contains(state.phase)) return false;
            state.deployment = command.deploymentUuid; state.phase = "POLLING"; state.error = null;
            release(state, Duration.ZERO);
            audit.record("AI_WORKSPACE_ROUTE_OBSERVATION_ATTACHED", "MANAGED_PRODUCT_SERVICE", state.serviceId,
                Map.of("operationKey", command.operationKey, "commandFingerprint", observation.commandFingerprint,
                    "operatorEvidenceRef", command.operatorEvidenceRef, "deploymentUuid", command.deploymentUuid,
                    "generation", state.generation, "inflightGeneration", state.flightGeneration));
            return true;
        });
    }

    public Claim claim(Binding binding) {
        return transaction.execute(ignored -> {
            jdbc.update("""
                INSERT INTO ai_workspace_route_reconciliations
                    (route_key, service_id, target_profile_id, target_profile_fingerprint, application_uuid, managed_domains)
                VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
                """, binding.key(), binding.serviceId(), binding.profileId(), binding.profileFingerprint(), binding.applicationUuid(), json(binding.historicManaged()));
            State state = locked(binding);
            refreshDesired(state);
            Instant now = clock.instant();
            if (state.leaseUntil != null && state.leaseUntil.isAfter(now)) { save(state); return null; }
            // An expired effect lease is not permission to dispatch the effect again.
            if (state.phase.equals("PATCHING") || state.phase.equals("STARTING")) {
                state.phase = "UNKNOWN"; state.error = "EFFECT_OUTCOME_UNKNOWN";
                event(state, "LEASE_EXPIRED");
            }
            if (state.nextAttempt.isAfter(now)) { save(state); return null; }
            state.token = UUID.randomUUID().toString(); state.leaseUntil = now.plus(LEASE);
            save(state);
            return new Claim(binding, state.token, state.generation, state.fingerprint,
                state.desired, state.managed, state.inflight, state.expected, state.phase, state.deployment);
        });
    }

    public boolean preparePatch(Claim claim, List<String> expected) {
        return change(claim, state -> {
            if (!state.phase.equals("IDLE") || !current(claim, state)) return false;
            state.phase = "PATCHING"; state.inflight = state.desired;
            state.flightGeneration = state.generation; state.flightFingerprint = state.fingerprint;
            state.expected = List.copyOf(expected); state.deployment = null; state.attempts++;
            event(state, "PATCH_INTENT"); return true;
        });
    }
    public void patched(Claim claim) {
        change(claim, state -> {
            if (!state.phase.equals("PATCHING")) return false;
            state.phase = "PATCHED"; release(state, Duration.ZERO); return true;
        });
    }
    public boolean prepareStart(Claim claim) {
        return change(claim, state -> {
            if (!state.phase.equals("PATCHED")) return false;
            // A successful provider patch still needs its native deployment even
            // when newer desired rows exist. Completion only acknowledges flightGeneration.
            state.phase = "STARTING"; event(state, "START_INTENT"); return true;
        });
    }
    public void started(Claim claim, String deploymentUuid) {
        change(claim, state -> {
            if (!state.phase.equals("STARTING")) return false;
            if (deploymentUuid == null || deploymentUuid.isBlank() || deploymentUuid.length() > 128) {
                state.phase = "UNKNOWN"; state.error = "START_RECEIPT_MISSING";
            } else {
                state.deployment = deploymentUuid; state.phase = "POLLING"; state.error = null;
            }
            release(state, Duration.ofSeconds(5)); event(state, "START_RESULT"); return true;
        });
    }
    public void completed(Claim claim) {
        change(claim, state -> {
            if (!state.phase.equals("POLLING")) return false;
            state.managed = state.inflight; state.appliedGeneration = state.flightGeneration;
            state.phase = "IDLE"; state.error = null; state.inflight = List.of();
            release(state, Duration.ofSeconds(5)); event(state, "DEPLOYMENT_FINISHED"); return true;
        });
    }
    public void unchanged(Claim claim) {
        change(claim, state -> {
            if (!state.phase.equals("IDLE") || !current(claim, state)) return false;
            state.managed = state.desired; state.appliedGeneration = state.generation;
            state.inflight = List.of(); state.error = null; release(state, Duration.ofSeconds(15)); return true;
        });
    }
    public void waiting(Claim claim) {
        change(claim, state -> { release(state, Duration.ofSeconds(5)); return true; });
    }
    public void failed(Claim claim, String code, boolean uncertainEffect) {
        if (!code.matches("[A-Z_]{1,64}")) throw new IllegalArgumentException("Invalid route error code");
        change(claim, state -> {
            if (uncertainEffect || state.phase.equals("PATCHING") || state.phase.equals("STARTING")) state.phase = "UNKNOWN";
            else if (code.equals("DEPLOYMENT_FAILED") || code.equals("PROVIDER_STATE_CHANGED")) state.phase = "FAILED";
            state.error = code; release(state, Duration.ofSeconds(30)); event(state, "RECONCILE_BLOCKED"); return true;
        });
    }
    public boolean isApplied(Binding binding, String hostname) {
        return Boolean.TRUE.equals(transaction.execute(ignored -> {
            Desired desired = desired();
            return jdbc.query("SELECT * FROM ai_workspace_route_reconciliations WHERE route_key = ?", this::map, binding.key()).stream()
                .anyMatch(state -> state.serviceId.equals(binding.serviceId) && state.profile.equals(binding.profileId)
                    && state.profileFingerprint.equals(binding.profileFingerprint)
                    && state.application.equals(binding.applicationUuid) && state.phase.equals("IDLE") && state.appliedGeneration > 0
                    && state.appliedGeneration == state.generation && state.fingerprint.equals(desired.fingerprint)
                    && state.managed.contains(hostname));
        }));
    }
    private boolean change(Claim claim, java.util.function.Function<State, Boolean> action) {
        return Boolean.TRUE.equals(transaction.execute(ignored -> {
            State state = locked(claim.binding);
            // Late responses cannot advance a recovered or expired lease.
            if (!Objects.equals(state.token, claim.token) || state.leaseUntil == null
                || !state.leaseUntil.isAfter(clock.instant())) return false;
            refreshDesired(state);
            boolean result = action.apply(state);
            if (!result && Objects.equals(state.token, claim.token)) release(state, Duration.ZERO);
            save(state); return result;
        }));
    }
    private boolean current(Claim claim, State state) {
        return state.generation == claim.generation && state.fingerprint.equals(claim.fingerprint);
    }
    private State locked(Binding binding) {
        var values = jdbc.query("SELECT * FROM ai_workspace_route_reconciliations WHERE route_key = ? FOR UPDATE", this::map, binding.key());
        if (values.size() != 1) throw new IllegalStateException("Route binding changed");
        State state = values.get(0);
        if (!state.serviceId.equals(binding.serviceId) || !state.profile.equals(binding.profileId)
            || !state.profileFingerprint.equals(binding.profileFingerprint)
            || !state.application.equals(binding.applicationUuid)) throw new IllegalStateException("Route binding changed");
        return state;
    }
    private record Desired(String fingerprint, List<String> hosts) { }
    private Desired desired() {
        var rows = jdbc.queryForList("""
            SELECT d.id, d.hostname, d.domain_type, d.installation_entity_id
              FROM ai_workspace_domains d JOIN ai_workspace_installations i ON i.id = d.installation_entity_id
             WHERE i.delivery_mode = 'STANDALONE' AND i.status <> 'DISABLED' AND d.status <> 'DISABLED'
               AND (d.domain_type = 'PLATFORM_DEFAULT' OR d.status IN ('ROUTING_PENDING', 'VERIFIED'))
             ORDER BY d.hostname
            """);
        List<String> identities = new ArrayList<>(); List<String> hosts = new ArrayList<>();
        for (var row : rows) {
            identities.add(json(List.of(row.get("id"), row.get("hostname"), row.get("domain_type"), row.get("installation_entity_id"))));
            hosts.add((String) row.get("hostname"));
        }
        return new Desired(fingerprint(identities), List.copyOf(hosts));
    }
    private void refreshDesired(State state) {
        Desired desired = desired();
        if (!state.fingerprint.equals(desired.fingerprint)) {
            state.generation++; state.fingerprint = desired.fingerprint; state.desired = desired.hosts;
            if (state.phase.equals("IDLE")) state.nextAttempt = clock.instant();
        }
    }
    private void release(State state, Duration delay) {
        state.token = null; state.leaseUntil = null; state.nextAttempt = clock.instant().plus(delay);
    }
    private void event(State state, String outcome) {
        audit.record("AI_WORKSPACE_ROUTE_" + outcome, "MANAGED_PRODUCT_SERVICE", state.serviceId,
            Map.of("generation", state.generation, "inflightGeneration", state.flightGeneration,
                "phase", state.phase, "errorCode", state.error == null ? "NONE" : state.error));
    }
    private void save(State s) {
        jdbc.update("""
            UPDATE ai_workspace_route_reconciliations SET generation=?, desired_fingerprint=?, desired_domains=?,
                managed_domains=?, inflight_domains=?, inflight_generation=?, inflight_fingerprint=?,
                expected_provider_domains=?, deployment_uuid=?, phase=?, lease_token=?, lease_until=?,
                next_attempt_at=?, attempts=?, last_error=?, applied_generation=?, updated_at=? WHERE route_key=?
            """, s.generation, s.fingerprint, json(s.desired), json(s.managed), json(s.inflight), s.flightGeneration,
            s.flightFingerprint, json(s.expected), s.deployment, s.phase, s.token, timestamp(s.leaseUntil),
            timestamp(s.nextAttempt), s.attempts, s.error, s.appliedGeneration, timestamp(clock.instant()), s.key);
    }
    private State map(ResultSet rs, int index) throws SQLException {
        State s = new State();
        s.key = rs.getString("route_key"); s.serviceId = rs.getString("service_id");
        s.profile = rs.getString("target_profile_id"); s.profileFingerprint = rs.getString("target_profile_fingerprint");
        s.application = rs.getString("application_uuid");
        s.generation = rs.getLong("generation"); s.fingerprint = rs.getString("desired_fingerprint");
        s.desired = list(rs.getString("desired_domains")); s.managed = list(rs.getString("managed_domains"));
        s.inflight = list(rs.getString("inflight_domains")); s.expected = list(rs.getString("expected_provider_domains"));
        s.flightGeneration = rs.getLong("inflight_generation"); s.flightFingerprint = rs.getString("inflight_fingerprint");
        s.phase = rs.getString("phase"); s.deployment = rs.getString("deployment_uuid"); s.token = rs.getString("lease_token");
        s.leaseUntil = instant(rs.getTimestamp("lease_until")); s.nextAttempt = instant(rs.getTimestamp("next_attempt_at"));
        s.attempts = rs.getInt("attempts"); s.error = rs.getString("last_error"); s.appliedGeneration = rs.getLong("applied_generation");
        return s;
    }
    private static class State {
        String key, serviceId, profile, profileFingerprint, application, fingerprint, flightFingerprint, phase, deployment, token, error;
        long generation, flightGeneration, appliedGeneration;
        int attempts;
        List<String> desired, managed, inflight, expected;
        Instant leaseUntil, nextAttempt;
    }
    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception ex) { throw new IllegalStateException("Invalid route state"); }
    }
    private List<String> list(String value) {
        try { return List.copyOf(mapper.readValue(value, new TypeReference<List<String>>() { })); }
        catch (Exception ex) { throw new IllegalStateException("Invalid route state"); }
    }
    static String fingerprint(List<String> values) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            for (String value : values) {
                byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array()); digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception ex) { throw new IllegalStateException("Route fingerprint unavailable"); }
    }
    private String auditField(String json, String field) {
        try { return mapper.readTree(json).path(field).asText(""); }
        catch (Exception ex) { throw new IllegalStateException("Invalid route audit receipt"); }
    }
    private static Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }
    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
}
