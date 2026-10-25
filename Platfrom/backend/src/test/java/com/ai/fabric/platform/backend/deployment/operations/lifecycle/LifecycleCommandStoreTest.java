package com.ai.fabric.platform.backend.deployment.operations.lifecycle;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V212__explicit_recovery_verification_retry.sql"));
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
        assertThatThrownBy(() -> store.effectKnown(first, "stop-runtime")).hasMessageContaining("WORKER_FENCED");
        assertThat(store.effectKnown(resumed, "stop-runtime")).isTrue();
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

    @Test void knownEffectLookupIsReadOnlyCommandScopedAndIncludesIntentWithoutProviderJob() {
        var claim = command(Phase.CLAIM); store.accept(claim); var claimWork = store.claim(claim.commandId());
        store.beforeEffect(claimWork, "delete-runtime"); store.completed(claimWork, "delete-runtime");
        store.finish(claimWork, Status.SUCCEEDED, "CLAIMED", List.of());
        store.accept(command(Phase.STOP)); var work = store.claim("operation-STOP");

        assertThat(store.effectKnown(work, "delete-runtime")).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_lifecycle_effects WHERE command_id=?",
            Integer.class, work.command().commandId())).isZero();
        store.beforeEffect(work, "delete-runtime");
        var persisted = jdbc.queryForMap("SELECT * FROM deployment_lifecycle_effects WHERE command_id=? AND effect_id=?",
            work.command().commandId(), "delete-runtime");
        assertThat(store.providerOperation(work, "delete-runtime")).isNull();
        assertThat(store.effectKnown(work, "delete-runtime")).isTrue();
        assertThat(store.effectComplete(work, "delete-runtime")).isFalse();
        assertThat(store.effectKnown(work, "different-resource")).isFalse();
        assertThat(jdbc.queryForMap("SELECT * FROM deployment_lifecycle_effects WHERE command_id=? AND effect_id=?",
            work.command().commandId(), "delete-runtime")).isEqualTo(persisted);
        store.completed(work, "delete-runtime");
        assertThat(store.effectKnown(work, "delete-runtime")).isTrue();
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

    @Test void anEffectCreatedAfterDeadlineCannotAuthorizeMoreEffectsButItsReadbackCanFinish() {
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

    @Test void startedDeleteCanFinishReclamationLateWithoutRenewingImmutableAuthorityOrIntentDates() {
        var historical = historicalScope("historical", "historical-grant", Goal.RETIRE);
        var command = historicalCommand(historical, Phase.DELETE, Status.PENDING);
        historicalEffect(command, "delete-db", historical.job().deadlineAt().minusSeconds(1));
        var grantBefore = jdbc.queryForMap("SELECT * FROM deployment_lifecycle_grants WHERE grant_id=?", command.grantId());
        var commandBefore = jdbc.queryForMap("SELECT request_json,request_digest,created_at FROM deployment_lifecycle_commands WHERE command_id=?", command.commandId());
        var intentBefore = jdbc.queryForMap("SELECT * FROM deployment_lifecycle_effects WHERE command_id=? AND effect_id='delete-db'", command.commandId());
        var work = store.claim(command.commandId());
        assertThat(store.beforeEffect(work, "delete-db")).isFalse();
        assertThat(store.beforeEffect(work, "reclaim-db-volume")).isTrue();
        assertThat(store.beforeEffect(work, "reclaim-db-volume")).isFalse();
        assertThat(jdbc.queryForMap("SELECT * FROM deployment_lifecycle_effects WHERE command_id=? AND effect_id='delete-db'", command.commandId()))
            .isEqualTo(intentBefore);
        store.completed(work, "delete-db"); store.completed(work, "reclaim-db-volume");
        store.finish(work, Status.SUCCEEDED, "EXACT_FROZEN_DELETE_COMPLETED", List.of());
        assertThat(jdbc.queryForMap("SELECT * FROM deployment_lifecycle_grants WHERE grant_id=?", command.grantId())).isEqualTo(grantBefore);
        assertThat(jdbc.queryForMap("SELECT request_json,request_digest,created_at FROM deployment_lifecycle_commands WHERE command_id=?", command.commandId()))
            .isEqualTo(commandBefore);
        assertThat(jdbc.queryForMap("SELECT * FROM deployment_lifecycle_effects WHERE command_id=? AND effect_id='delete-db'", command.commandId()).get("created_at"))
            .isEqualTo(intentBefore.get("created_at"));
        assertThat(store.require(command.grantId()).scope()).isEqualTo(historical);
    }

    @Test void acceptedButUnstartedPhaseCannotCreateItsFirstEffectAfterExpiry() {
        var historical = historicalScope("historical", "historical-grant", Goal.RETIRE);
        var command = historicalCommand(historical, Phase.DELETE, Status.PENDING);
        var work = store.claim(command.commandId());
        assertThat(store.accept(command).status()).isEqualTo(Status.PENDING);
        assertThatThrownBy(() -> store.beforeEffect(work, "delete-db")).hasMessageContaining("DEADLINE_EXCEEDED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_lifecycle_effects WHERE command_id=?", Integer.class, command.commandId())).isZero();
    }

    @ParameterizedTest
    @EnumSource(value = Phase.class, names = {"CLAIM", "BEGIN_DRAIN", "FENCE", "STOP", "DELETE", "RECOVER", "VERIFY"})
    void expiryRejectsNewAndBlockedRetryMutatingPhases(Phase phase) {
        var historical = historicalScope("historical", "historical-grant",
            phase == Phase.RECOVER || phase == Phase.VERIFY ? Goal.RECOVER : Goal.RETIRE);
        var command = phaseCommand(historical, phase);
        assertThatThrownBy(() -> store.accept(command)).hasMessageContaining("DEADLINE_EXCEEDED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_lifecycle_commands WHERE command_id=?", Integer.class, command.commandId())).isZero();
        store.rejection(command, "LIFECYCLE_DEADLINE_EXCEEDED");
        var rejectedBefore = jdbc.queryForMap("SELECT * FROM deployment_lifecycle_commands WHERE command_id=?", command.commandId());
        assertThatThrownBy(() -> store.accept(command)).hasMessageContaining("DEADLINE_EXCEEDED");
        assertThat(jdbc.queryForMap("SELECT * FROM deployment_lifecycle_commands WHERE command_id=?", command.commandId())).isEqualTo(rejectedBefore);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_lifecycle_effects", Integer.class)).isZero();
    }

    @ParameterizedTest
    @EnumSource(value = Status.class, names = {"PENDING", "UNKNOWN"})
    void existingPendingAndUnknownCommandsReplayReadOnlyAfterExpiry(Status status) {
        var historical = historicalScope("historical", "historical-grant", Goal.RETIRE);
        var command = historicalCommand(historical, Phase.DELETE, status);
        var receipt = store.receipt(command.commandId());
        var before = jdbc.queryForMap("SELECT * FROM deployment_lifecycle_commands WHERE command_id=?", command.commandId());
        assertThat(store.accept(command)).isEqualTo(receipt);
        assertThat(jdbc.queryForMap("SELECT * FROM deployment_lifecycle_commands WHERE command_id=?", command.commandId())).isEqualTo(before);
    }

    @Test void effectsInAnotherCommandOrGrantNeverAuthorizeLateSubEffects() {
        var historical = historicalScope("historical", "historical-grant", Goal.RETIRE);
        var earlier = historicalCommand(historical, Phase.STOP, Status.SUCCEEDED);
        historicalEffect(earlier, "reclaim-volume", historical.job().deadlineAt().minusSeconds(1));
        var command = historicalCommand(historical, Phase.DELETE, Status.PENDING);
        var foreign = historicalScope("foreign", "foreign-grant", Goal.RETIRE);
        var foreignCommand = historicalCommand(foreign, Phase.DELETE, Status.PENDING);
        historicalEffect(foreignCommand, "reclaim-volume", foreign.job().deadlineAt().minusSeconds(1));
        var work = store.claim(command.commandId());
        assertThatThrownBy(() -> store.beforeEffect(work, "reclaim-volume")).hasMessageContaining("DEADLINE_EXCEEDED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_lifecycle_effects WHERE command_id=?", Integer.class, command.commandId())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_lifecycle_effects", Integer.class)).isEqualTo(2);
    }

    @Test void anIntentExactlyAtTheDeadlineDoesNotAuthorizeLateSubEffects() {
        var historical = historicalScope("historical", "historical-grant", Goal.RETIRE);
        var command = historicalCommand(historical, Phase.DELETE, Status.PENDING);
        historicalEffect(command, "delete-db", historical.job().deadlineAt());
        var work = store.claim(command.commandId());
        assertThatThrownBy(() -> store.beforeEffect(work, "reclaim-db-volume")).hasMessageContaining("DEADLINE_EXCEEDED");
    }

    @Test void startedLateCompletionStillRequiresTheCurrentLeaseAndOpenClaim() {
        var historical = historicalScope("historical", "historical-grant", Goal.RETIRE);
        var command = historicalCommand(historical, Phase.DELETE, Status.PENDING);
        historicalEffect(command, "delete-db", historical.job().deadlineAt().minusSeconds(1));
        var work = store.claim(command.commandId());
        jdbc.update("UPDATE deployment_lifecycle_commands SET lease_until=? WHERE command_id=?",
            Instant.now().minusSeconds(1).atOffset(java.time.ZoneOffset.UTC), command.commandId());
        assertThatThrownBy(() -> store.beforeEffect(work, "reclaim-db-volume")).hasMessageContaining("WORKER_FENCED");
        var next = store.claim(command.commandId());
        jdbc.update("UPDATE deployment_lifecycle_grants SET claimed=FALSE WHERE grant_id=?", command.grantId());
        assertThatThrownBy(() -> store.beforeEffect(next, "reclaim-db-volume")).hasMessageContaining("WORKER_FENCED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_lifecycle_effects WHERE command_id=?", Integer.class, command.commandId())).isEqualTo(1);
    }

    @Test void explicitCancelRemainsPossibleWithoutAStartedEffectAfterExpiry() {
        var historical = historicalScope("historical", "historical-grant", Goal.STOP);
        jdbc.update("UPDATE deployment_lifecycle_grants SET claimed=FALSE WHERE grant_id=?", historical.job().grantId());
        var command = phaseCommand(historical, Phase.CANCEL);
        store.accept(command);
        assertThat(store.beforeEffect(store.claim(command.commandId()), "runtime-CANCEL")).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = Goal.class, names = {"STOP", "RETIRE"})
    void readOnlyVerificationAndReleaseCanSettleExactSuccessfulPhasesAfterExpiry(Goal goal) {
        var historical = historicalScope("historical", "historical-grant", goal);
        historicalCommand(historical, Phase.CLAIM, Status.SUCCEEDED);
        historicalCommand(historical, goal == Goal.STOP ? Phase.STOP : Phase.DELETE, Status.SUCCEEDED);
        var verify = phaseCommand(historical, Phase.VERIFY);
        assertThat(store.accept(verify).status()).isEqualTo(Status.PENDING);
        store.finish(store.claim(verify.commandId()), Status.SUCCEEDED, "EXACT_READBACK_CONFIRMED", List.of());
        assertThat(store.accept(phaseCommand(historical, Phase.RELEASE)).status()).isEqualTo(Status.PENDING);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_lifecycle_effects", Integer.class)).isZero();
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

    @Test void onlyExplicitBlockedRecoveryVerificationRetryAdvancesAndPreservesEveryFailedAttempt() {
        var first = recoveryVerificationWork();
        String legacy = store.recoveryVerificationEffect(first);
        assertThat(legacy).isEqualTo("actual-release-verification");
        store.beforeEffect(first, legacy); store.dispatched(first, legacy, "failed-run-0"); store.completed(first, legacy);
        store.finish(first, Status.BLOCKED, "RECOVERY_RELEASE_VERIFICATION_FAILED", List.of("FAILED_0"));
        var failed0 = store.receipt(first.command().commandId());
        var originalEffect = jdbc.queryForMap("SELECT * FROM deployment_lifecycle_effects WHERE command_id=? AND effect_id=?", first.command().commandId(), legacy);
        var originalCommand = jdbc.queryForMap("SELECT request_json,request_digest,created_at FROM deployment_lifecycle_commands WHERE command_id=?", first.command().commandId());
        var originalAuthority = store.require(first.command().grantId()).scope();
        assertThat(store.claim(first.command().commandId())).isNull();
        assertThatThrownBy(() -> store.recoveryVerificationEffect(first)).hasMessageContaining("WORKER_FENCED");

        var accepted = store.accept(first.command());
        assertThat(store.accept(first.command())).isEqualTo(accepted);
        var second = store.claim(first.command().commandId());
        String retry1 = store.recoveryVerificationEffect(second);
        assertThat(retry1).isEqualTo("actual-release-verification:retry:1");
        assertThat(store.recoveryVerificationEffect(second)).isEqualTo(retry1);
        assertThat(store.parse(jdbc.queryForObject("SELECT receipt_json FROM deployment_lifecycle_verification_retry_receipts WHERE command_id=? AND verification_retry_revision=0",
            String.class, first.command().commandId()), Receipt.class)).isEqualTo(failed0);
        store.beforeEffect(second, retry1); store.dispatched(second, retry1, "failed-run-1"); store.completed(second, retry1);
        store.finish(second, Status.BLOCKED, "RECOVERY_RELEASE_VERIFICATION_FAILED", List.of("FAILED_1"));
        var failed1 = store.receipt(first.command().commandId());
        store.accept(first.command());
        var third = store.claim(first.command().commandId());
        assertThat(store.recoveryVerificationEffect(third)).isEqualTo("actual-release-verification:retry:2");
        assertThat(store.parse(jdbc.queryForObject("SELECT receipt_json FROM deployment_lifecycle_verification_retry_receipts WHERE command_id=? AND verification_retry_revision=1",
            String.class, first.command().commandId()), Receipt.class)).isEqualTo(failed1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_lifecycle_verification_retry_receipts", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForMap("SELECT * FROM deployment_lifecycle_effects WHERE command_id=? AND effect_id=?", first.command().commandId(), legacy)).isEqualTo(originalEffect);
        assertThat(store.providerOperation(third, retry1)).isEqualTo("failed-run-1");
        assertThat(jdbc.queryForMap("SELECT request_json,request_digest,created_at FROM deployment_lifecycle_commands WHERE command_id=?", first.command().commandId())).isEqualTo(originalCommand);
        assertThat(store.require(first.command().grantId()).scope()).isEqualTo(originalAuthority);
    }

    @Test void unknownVerificationContinuationCannotMintAnotherRevisionOrRedispatchItsIntent() {
        var first = recoveryVerificationWork();
        store.finish(first, Status.BLOCKED, "RECOVERY_RELEASE_VERIFICATION_FAILED", List.of());
        store.accept(first.command());
        var retry = store.claim(first.command().commandId());
        String effect = store.recoveryVerificationEffect(retry);
        assertThat(store.beforeEffect(retry, effect)).isTrue();
        store.finish(retry, Status.UNKNOWN, "RECOVERY_VERIFICATION_OUTCOME_UNKNOWN", List.of());
        var unknown = store.receipt(first.command().commandId());
        assertThat(store.accept(first.command())).isEqualTo(unknown);
        var continued = store.claim(first.command().commandId());
        assertThat(store.recoveryVerificationEffect(continued)).isEqualTo(effect);
        assertThat(store.beforeEffect(continued, effect)).isFalse();
        assertThat(store.providerOperation(continued, effect)).isNull();
        assertThat(jdbc.queryForObject("SELECT verification_retry_revision FROM deployment_lifecycle_commands WHERE command_id=?", Integer.class, first.command().commandId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_lifecycle_verification_retry_receipts", Integer.class)).isEqualTo(1);
    }

    @Test void concurrentExplicitRetriesAdvanceTheRevisionOnlyOnce() throws Exception {
        var first = recoveryVerificationWork();
        store.finish(first, Status.BLOCKED, "RECOVERY_RELEASE_VERIFICATION_FAILED", List.of());
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var one = executor.submit(() -> { start.await(); return store.accept(first.command()); });
            var two = executor.submit(() -> { start.await(); return store.accept(first.command()); });
            start.countDown();
            assertThat(one.get(5, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(two.get(5, java.util.concurrent.TimeUnit.SECONDS));
        }
        assertThat(store.recoveryVerificationEffect(store.claim(first.command().commandId()))).endsWith(":retry:1");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_lifecycle_verification_retry_receipts", Integer.class)).isEqualTo(1);
    }

    @Test void otherBlockedPhaseRetriesDoNotCreateVerificationRevisions() {
        complete(Phase.CLAIM);
        store.accept(command(Phase.STOP)); var stopped = store.claim("operation-STOP");
        store.finish(stopped, Status.BLOCKED, "NATIVE_STATE_CONFLICT", List.of());
        store.accept(stopped.command()); var retry = store.claim(stopped.command().commandId());
        assertThatThrownBy(() -> store.recoveryVerificationEffect(retry)).hasMessageContaining("RECOVERY_VERIFICATION_PHASE_REQUIRED");
        assertThat(jdbc.queryForObject("SELECT verification_retry_revision FROM deployment_lifecycle_commands WHERE command_id=?", Integer.class, retry.command().commandId())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_lifecycle_verification_retry_receipts", Integer.class)).isZero();
    }

    @Test void expiredExplicitVerificationRetryCannotAdvanceOrArchiveHistory() {
        var historical = historicalScope("historical", "historical-grant", Goal.RECOVER);
        historicalCommand(historical, Phase.CLAIM, Status.SUCCEEDED);
        historicalCommand(historical, Phase.RECOVER, Status.SUCCEEDED);
        var command = historicalCommand(historical, Phase.VERIFY, Status.BLOCKED);
        historicalEffect(command, "actual-release-verification", historical.job().deadlineAt().minusSeconds(1));
        var before = jdbc.queryForMap("SELECT * FROM deployment_lifecycle_commands WHERE command_id=?", command.commandId());
        assertThatThrownBy(() -> store.accept(command)).hasMessageContaining("DEADLINE_EXCEEDED");
        assertThat(jdbc.queryForMap("SELECT * FROM deployment_lifecycle_commands WHERE command_id=?", command.commandId())).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_lifecycle_verification_retry_receipts", Integer.class)).isZero();
    }

    @Test void freshRetryCannotBorrowAnOldAttemptsIntentToStartAfterExpiry() {
        var historical = historicalScope("historical", "historical-grant", Goal.RECOVER);
        var command = historicalCommand(historical, Phase.VERIFY, Status.PENDING);
        historicalEffect(command, "actual-release-verification", historical.job().deadlineAt().minusSeconds(1));
        jdbc.update("UPDATE deployment_lifecycle_commands SET verification_retry_revision=1 WHERE command_id=?", command.commandId());
        var work = store.claim(command.commandId());
        assertThatThrownBy(() -> store.recoveryVerificationEffect(work)).hasMessageContaining("DEADLINE_EXCEEDED");
        historicalEffect(command, "actual-release-verification:retry:1", historical.job().deadlineAt().minusSeconds(1));
        assertThat(store.recoveryVerificationEffect(work)).isEqualTo("actual-release-verification:retry:1");
    }

    @Test void verificationEffectGetterRejectsAnExpiredOrReplacedWorkerLease() {
        var work = recoveryVerificationWork();
        jdbc.update("UPDATE deployment_lifecycle_commands SET lease_until=? WHERE command_id=?",
            Instant.now().minusSeconds(1).atOffset(java.time.ZoneOffset.UTC), work.command().commandId());
        var next = store.claim(work.command().commandId());
        assertThatThrownBy(() -> store.recoveryVerificationEffect(work)).hasMessageContaining("WORKER_FENCED");
        assertThat(store.recoveryVerificationEffect(next)).isEqualTo("actual-release-verification");
    }

    private LifecycleCommandStore.Work recoveryVerificationWork() {
        var recovery = scope("recovery", "recovery-grant", "staging", Goal.RECOVER, Mode.COLD);
        store.create(recovery, store.digest(recovery), "owner");
        for (var phase : List.of(Phase.CLAIM, Phase.RECOVER)) {
            var command = phaseCommand(recovery, phase); store.accept(command);
            store.finish(store.claim(command.commandId()), Status.SUCCEEDED, "EXACT_NATIVE_READBACK", List.of());
        }
        var verify = phaseCommand(recovery, Phase.VERIFY); store.accept(verify);
        return store.claim(verify.commandId());
    }

    private void complete(Phase phase) {
        store.accept(command(phase)); var work = store.claim("operation-" + phase);
        assertThat(work).isNotNull(); store.finish(work, Status.SUCCEEDED, "TEST_READBACK", List.of());
    }
    private LifecycleCommandStore.Scope historicalScope(String operation, String grant, Goal goal) {
        Instant deadline = Instant.now().minusSeconds(300).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        var job = new JobRequest("1", operation, grant, "customer", "tenant", "deployment", "staging", "release", "c".repeat(64),
            goal, Mode.GRACEFUL, "policy-v1", deadline.minusSeconds(300), deadline, deadline.minusSeconds(600));
        var historical = new LifecycleCommandStore.Scope(job, "version", "e".repeat(64), "f".repeat(64), mapper.createObjectNode(), true);
        store.create(historical, store.digest(historical), "owner");
        jdbc.update("UPDATE deployment_lifecycle_grants SET claimed=TRUE WHERE grant_id=?", grant);
        return historical;
    }
    private Command phaseCommand(LifecycleCommandStore.Scope historical, Phase phase) {
        var job = historical.job();
        return new Command("1", job.operationId() + "-" + phase, job.operationId(), job.grantId(), job.scopeHash(), phase);
    }
    private Command historicalCommand(LifecycleCommandStore.Scope historical, Phase phase, Status status) {
        var command = phaseCommand(historical, phase);
        Instant admitted = historical.job().deadlineAt().minusSeconds(30);
        var receipt = new Receipt("1", command.commandId(), command.operationId(), command.grantId(), phase,
            status, "HISTORICAL_ACCEPTED_PHASE", admitted, List.of());
        jdbc.update("INSERT INTO deployment_lifecycle_commands(command_id,grant_id,operation_id,phase,request_digest,request_json,receipt_json,status,created_at,updated_at) "
            + "VALUES(?,?,?,?,?,?,?,?,?,?)", command.commandId(), command.grantId(), command.operationId(), phase.name(),
            store.digest(command), store.json(command), store.json(receipt), status.name(), admitted.atOffset(java.time.ZoneOffset.UTC), admitted.atOffset(java.time.ZoneOffset.UTC));
        return command;
    }
    private void historicalEffect(Command command, String effect, Instant accepted) {
        jdbc.update("INSERT INTO deployment_lifecycle_effects(command_id,effect_id,state,created_at,updated_at) VALUES(?,?,'INTENT',?,?)",
            command.commandId(), effect, accepted.atOffset(java.time.ZoneOffset.UTC), accepted.atOffset(java.time.ZoneOffset.UTC));
    }
    private Command command(Phase phase) { return new Command("1", "operation-" + phase, "operation", "grant", "c".repeat(64), phase); }
    private LifecycleCommandStore.Scope scope(String operation, String grant, String target, Goal goal, Mode mode) {
        var job = new JobRequest("1", operation, grant, "customer", "tenant", "deployment", target, "release", "c".repeat(64),
            goal, mode, "policy-v1", Instant.now().minusSeconds(1), Instant.now().plusSeconds(1800), Instant.now());
        return new LifecycleCommandStore.Scope(job, "version", "e".repeat(64), "f".repeat(64), mapper.createObjectNode(), false);
    }
}
