package com.ai.fabric.platform.backend.deployment.operations.lifecycle;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import static com.ai.fabric.platform.backend.deployment.operations.lifecycle.LifecycleContracts.*;
import static org.assertj.core.api.Assertions.*;

class LifecycleCommandStoreTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private JdbcTemplate jdbc;
    private LifecycleCommandStore store;
    private LifecycleCommandStore.Scope scope;

    @BeforeEach void setup() throws Exception {
        var source = dataSource();
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE platform_deployments(id VARCHAR(64) PRIMARY KEY)");
        jdbc.execute("CREATE TABLE deployment_target_profiles(id VARCHAR(64) PRIMARY KEY)");
        jdbc.execute("CREATE TABLE platform_deployment_releases(id VARCHAR(64) PRIMARY KEY,deployment_id VARCHAR(64),target_profile_id VARCHAR(64),status VARCHAR(64),created_at TIMESTAMP WITH TIME ZONE)");
        try (var connection = source.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V211__scoped_lifecycle_commands.sql"));
        }
        jdbc.update("INSERT INTO platform_deployments VALUES ('deployment')");
        jdbc.update("INSERT INTO deployment_target_profiles VALUES ('staging'),('production')");
        jdbc.update("INSERT INTO platform_deployment_releases VALUES ('release','deployment','staging','APPLIED_VERIFIED',CURRENT_TIMESTAMP)");
        store = new LifecycleCommandStore(jdbc, mapper, new DataSourceTransactionManager(source));
        scope = scope("operation", "grant", "staging", Goal.STOP, Mode.COLD);
        store.create(scope, "a".repeat(64), "owner");
    }

    protected javax.sql.DataSource dataSource() {
        return new DriverManagerDataSource("jdbc:h2:mem:lifecycle_" + UUID.randomUUID()
            + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=3000", "sa", "");
    }

    @Test void grantAndCommandReplaysAreStableAndChangedPayloadsConflict() {
        assertThat(store.create(scope, "a".repeat(64), "owner").id()).isEqualTo("grant");
        assertThatThrownBy(() -> store.create(scope, "b".repeat(64), "owner")).hasMessageContaining("OPERATION_CONFLICT");
        var command = command(Phase.CLAIM);
        var first = store.accept(command);
        assertThat(store.accept(command)).isEqualTo(first);
        assertThatThrownBy(() -> store.accept(new Command("1", command.commandId(), "other", "grant", "c".repeat(64), Phase.CLAIM)))
            .hasMessageContaining("SCOPE_MISMATCH");
        assertThatThrownBy(() -> store.accept(new Command("1", command.commandId(), "operation", "grant", "c".repeat(64), Phase.CANCEL)))
            .hasMessageContaining("COMMAND_CONFLICT");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_lifecycle_commands", Integer.class)).isEqualTo(1);
    }

    @Test void acceptedNativeClaimBlocksColdMutationOnlyOnItsExactTarget() {
        var guard = new LifecycleClaimGuard(jdbc);
        guard.requireAvailable("deployment", "staging");
        store.accept(command(Phase.CLAIM));
        assertThatThrownBy(() -> guard.requireAvailable("deployment", "staging")).hasMessageContaining("TARGET_NATIVE_OPERATION_IN_PROGRESS");
        guard.requireAvailable("deployment", "production");
        assertThatThrownBy(() -> {
            var other = scope("another-operation", "another-grant", "staging", Goal.STOP, Mode.COLD);
            store.create(other, "d".repeat(64), "owner");
            store.accept(new Command("1", "another-command", "another-operation", "another-grant", "c".repeat(64), Phase.CLAIM));
        }).hasMessageContaining("TARGET_NATIVE_OPERATION_IN_PROGRESS");
    }

    @Test void nativeApplyCannotBeClaimedAndAQueuedJobDoesNotBlockNormalApply() {
        new LifecycleClaimGuard(jdbc).requireAvailable("deployment", "staging");
        jdbc.update("UPDATE platform_deployment_releases SET status='PROVISIONING' WHERE id='release'");
        assertThatThrownBy(() -> store.accept(command(Phase.CLAIM))).hasMessageContaining("RELEASE_APPLY_IN_PROGRESS");
        assertThat(store.require("grant").claimed()).isFalse();
        assertThat(store.pending()).isEmpty();
    }

    @Test void commandLeaseAndEffectIntentSurviveCrashWithoutSecondDispatch() {
        complete(Phase.CLAIM);
        store.accept(command(Phase.STOP));
        var first = store.claim("operation-STOP");
        assertThat(first).isNotNull();
        assertThat(store.claim("operation-STOP")).isNull();
        assertThat(store.beforeEffect(first, "stop-runtime")).isTrue();
        assertThat(store.beforeEffect(first, "stop-runtime")).isFalse();
        jdbc.update("UPDATE deployment_lifecycle_commands SET lease_until=? WHERE command_id=?",
            Instant.now().minusSeconds(1).atOffset(java.time.ZoneOffset.UTC), "operation-STOP");
        var resumed = store.claim("operation-STOP");
        assertThat(resumed).isNotNull();
        assertThatThrownBy(() -> store.beforeEffect(first, "delete-runtime")).hasMessageContaining("WORKER_FENCED");
        assertThat(store.beforeEffect(resumed, "stop-runtime")).isFalse();
        assertThat(store.effectComplete(resumed, "stop-runtime")).isFalse();
        store.completed(resumed, "stop-runtime");
        assertThat(store.effectComplete(resumed, "stop-runtime")).isTrue();
        store.finish(resumed, Status.SUCCEEDED, "STOPPED_READBACK", List.of());
        assertThat(store.claim("operation-STOP")).isNull();
    }

    @Test void phasesCannotSkipStopVerificationOrBroadenTheAuthorizedGoal() {
        assertThatThrownBy(() -> store.accept(command(Phase.STOP))).hasMessageContaining("CLAIM_REQUIRED");
        complete(Phase.CLAIM);
        assertThatThrownBy(() -> store.accept(command(Phase.DELETE))).hasMessageContaining("STOP_NOT_VERIFIED");
        assertThatThrownBy(() -> store.accept(command(Phase.BEGIN_DRAIN))).hasMessageContaining("PHASE_NOT_AUTHORIZED");
        assertThatThrownBy(() -> store.accept(command(Phase.RECOVER))).hasMessageContaining("PHASE_NOT_AUTHORIZED");
        assertThatThrownBy(() -> store.accept(command(Phase.VERIFY))).hasMessageContaining("EFFECT_NOT_VERIFIED");
        complete(Phase.STOP);
        assertThatThrownBy(() -> store.accept(command(Phase.RELEASE))).hasMessageContaining("VERIFICATION_REQUIRED");
        complete(Phase.VERIFY); complete(Phase.RELEASE);
        assertThat(store.require("grant").state()).isEqualTo("CLOSED");
        new LifecycleClaimGuard(jdbc).requireAvailable("deployment", "staging");
    }

    @Test void explicitReobservationKeepsTheOriginalCommandAndEffectIntent() {
        complete(Phase.CLAIM);
        store.accept(command(Phase.STOP)); var work = store.claim("operation-STOP");
        store.beforeEffect(work, "stop-runtime");
        store.finish(work, Status.UNKNOWN, "TIMEOUT", List.of("TIMEOUT"));
        assertThat(store.require("grant").claimed()).isTrue();
        var next = store.claim("operation-STOP");
        assertThat(store.beforeEffect(next, "stop-runtime")).isFalse();
        store.finish(next, Status.BLOCKED, "OBSERVATION_UNAVAILABLE", List.of());
        assertThat(store.accept(command(Phase.STOP)).status()).isEqualTo(Status.PENDING);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_lifecycle_commands WHERE phase='STOP'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_lifecycle_effects", Integer.class)).isEqualTo(1);
    }

    @Test void deadlinesPreventNewEffectsButDoNotPreventRecordingLateReadback() {
        complete(Phase.CLAIM);
        store.accept(command(Phase.STOP)); var work = store.claim("operation-STOP");
        store.beforeEffect(work, "stop-runtime");
        var expiredJob = new JobRequest("1", "operation", "grant", "customer", "tenant", "deployment", "staging", "release",
            "c".repeat(64), Goal.STOP, Mode.COLD, "policy-v1", Instant.now().minusSeconds(300), Instant.now().minusSeconds(1), Instant.now());
        var expiredScope = new LifecycleCommandStore.Scope(expiredJob, "version", "e".repeat(64), "f".repeat(64), mapper.createObjectNode(), false);
        jdbc.update("UPDATE deployment_lifecycle_grants SET snapshot_json=? WHERE grant_id='grant'", store.json(expiredScope));
        var expiredWork = new LifecycleCommandStore.Work(work.command(), store.require("grant"), work.token());
        assertThatThrownBy(() -> store.beforeEffect(expiredWork, "stop-another")).hasMessageContaining("DEADLINE_EXCEEDED");
        store.completed(expiredWork, "stop-runtime");
        store.finish(expiredWork, Status.SUCCEEDED, "LATE_READBACK_CONFIRMED", List.of());
        assertThat(store.receipt("operation-STOP").status()).isEqualTo(Status.SUCCEEDED);
    }

    @Test void futureScheduledGrantCannotClaimEarly() {
        var future = scope("future-operation", "future-grant", "staging", Goal.STOP, Mode.COLD);
        var old = future.job();
        var futureJob = new JobRequest(old.schemaVersion(), old.operationId(), old.grantId(), old.customerId(), old.tenantId(),
            old.deploymentId(), old.targetProfileId(), old.releaseId(), old.scopeHash(), old.goal(), old.mode(), old.policyRevision(),
            Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200), old.requestedAt());
        store.create(new LifecycleCommandStore.Scope(futureJob, "version", "e".repeat(64), null, mapper.createObjectNode(), false),
            "f".repeat(64), "owner");
        assertThatThrownBy(() -> store.accept(new Command("1", "future-claim", "future-operation", "future-grant", "c".repeat(64), Phase.CLAIM)))
            .hasMessageContaining("NOT_DUE");
        assertThat(store.require("future-grant").claimed()).isFalse();
    }

    @Test void staleExpiryCannotClaimOrStopAReplacementRelease() {
        jdbc.update("INSERT INTO platform_deployment_releases VALUES ('replacement','deployment','staging','APPLIED_VERIFIED',?)",
            Instant.now().plusSeconds(1).atOffset(java.time.ZoneOffset.UTC));
        assertThatThrownBy(() -> store.accept(command(Phase.CLAIM))).hasMessageContaining("RELEASE_SUPERSEDED");
        assertThat(store.require("grant").claimed()).isFalse();
        new LifecycleClaimGuard(jdbc).requireAvailable("deployment", "staging");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_lifecycle_effects", Integer.class)).isZero();
    }

    @Test void knownRejectedClaimIsRecordedAndRetryRevalidatesBeforeClaiming() {
        var claim = command(Phase.CLAIM);
        jdbc.update("UPDATE platform_deployment_releases SET status='PROVISIONING' WHERE id='release'");
        assertThatThrownBy(() -> store.accept(claim)).hasMessageContaining("RELEASE_APPLY_IN_PROGRESS");
        var denied = store.rejection(claim, "RELEASE_APPLY_IN_PROGRESS");
        assertThat(denied.status()).isEqualTo(Status.BLOCKED);
        assertThat(store.require("grant").claimed()).isFalse();
        assertThatThrownBy(() -> store.accept(claim)).hasMessageContaining("RELEASE_APPLY_IN_PROGRESS");
        jdbc.update("UPDATE platform_deployment_releases SET status='APPLIED_VERIFIED' WHERE id='release'");
        assertThat(store.accept(claim).status()).isEqualTo(Status.PENDING);
        assertThat(store.require("grant").claimed()).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_lifecycle_commands", Integer.class)).isEqualTo(1);
    }

    private void complete(Phase phase) {
        store.accept(command(phase)); var work = store.claim("operation-" + phase);
        assertThat(work).isNotNull(); store.finish(work, Status.SUCCEEDED, "TEST_READBACK", List.of());
    }
    private Command command(Phase phase) { return new Command("1", "operation-" + phase, "operation", "grant", "c".repeat(64), phase); }
    private LifecycleCommandStore.Scope scope(String operation, String grant, String target, Goal goal, Mode mode) {
        var job = new JobRequest("1", operation, grant, "customer", "tenant", "deployment", target, "release", "c".repeat(64),
            goal, mode, "policy-v1", Instant.now().minusSeconds(1), Instant.now().plusSeconds(1800), Instant.now());
        return new LifecycleCommandStore.Scope(job, "version", "e".repeat(64), "f".repeat(64), mapper.createObjectNode(), false);
    }
}
