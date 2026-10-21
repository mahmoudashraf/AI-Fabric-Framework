package com.ai.fabric.platform.backend.deployment.admission;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import static com.ai.fabric.platform.backend.deployment.admission.RuntimeArtifactRepairContracts.*;
import static org.assertj.core.api.Assertions.*;

class RuntimeArtifactRepairJournalTest extends AdmissionFixture {
    RuntimeArtifactRepairJournal journal;
    MovingClock clock;
    AdmissionContracts.Reservation reservation;
    Plan plan;
    void prepare() {
        reservation = one();
        reservation = store.outcome(reservation.fence(), AdmissionContracts.State.OUTCOME_UNKNOWN, "native-failed", "worker", NOW);
        jdbc.update("INSERT INTO deployment_admission_release_bindings(release_id,reservation_id,generation,authorization_key,version_id,version_config_hash,customer_id,tenant_id,deployment_id,target_profile_id,state,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
            "release", reservation.id(), reservation.generation(), "op1", "version", "config-hash", "customer", "tenant", "deployment1", TARGET, "OUTCOME_UNKNOWN", Timestamp.from(NOW), Timestamp.from(NOW));
        clock = new MovingClock(NOW);
        journal = new RuntimeArtifactRepairJournal(jdbc, json, new DataSourceTransactionManager(dataSource), clock);
        plan = plan("repair-once");
    }
    Plan plan(String operation) {
        var scope = new Scope("customer", "tenant", "deployment1", TARGET, "release", "version", "config-hash", "authority",
            reservation.id(), reservation.generation(), "https://runtime.example.test", "runtime-app", "connector-app", "runtime-pg");
        var request = new Request(reservation.fence(), "replacement", "offer-fingerprint", 2, operation, "reviewed fix", true, Mode.FULL_DRAIN);
        var original = new ArtifactPin("original", "original-pin", "reviewed/runtime", "sha256:" + "a".repeat(64), "a".repeat(40), "ci-old", "staging", "old-manifest");
        var replacement = new ArtifactPin("replacement", "replacement-pin", "reviewed/runtime", "sha256:" + "b".repeat(64), "b".repeat(40), "ci-new", "staging", "new-manifest");
        var local = new LocalEvidence(scope, request, original, replacement, Map.of("graph", "unchanged"), NOW.plusSeconds(300), NOW.plusSeconds(1800));
        String id = journal.preview(local, "admin");
        return journal.savePlan(id, "admin", new NativeEvidence(Map.of("inventory", "synthetic"), RuntimeArtifactRepairWalletTest.wallet(null, 3), null, clock.instant()));
    }
    Claim claim() {
        return journal.claim(plan, "admin", () -> store.claimRuntimeArtifactRepair(reservation.fence(), "release", "admin", journal.fingerprint(plan)));
    }
    Command command() { return new Command(plan.id(), journal.fingerprint(plan)); }
    @Test void expiredReviewCannotClaimOrChangeReservation() {
        prepare(); clock.advance(301);
        assertThatThrownBy(this::claim).hasMessageContaining("PLAN_EXPIRED");
        assertThat(store.require(reservation.id())).isEqualTo(reservation);
    }
    @Test void claimedWindowCanOutliveFiveMinuteReviewWithoutChangingExpiryOrAllowance() {
        prepare(); var claim = claim(); clock.advance(420);
        assertThatCode(() -> journal.intent(plan, Effect.QUIESCE_CONNECTOR, "admin", () -> {})).doesNotThrowAnyException();
        var current = store.require(reservation.id());
        assertThat(current.expiresAt()).isEqualTo(reservation.expiresAt()); assertThat(current.demand()).isEqualTo(reservation.demand());
        assertThat(current.generation()).isEqualTo(reservation.generation()); assertThat(current.workerToken()).isEqualTo(claim.workerToken());
    }
    @Test void executionDeadlineStopsDispatchButNotReadOnlyReconciliation() {
        prepare(); claim(); var intent = journal.intent(plan, Effect.QUIESCE_CONNECTOR, "admin", () -> {});
        journal.outcome(plan, intent, Outcome.UNKNOWN, Map.of("failureType", "lost-response"), "admin"); clock.advance(1801);
        assertThatCode(() -> journal.reconciled(plan, Effect.QUIESCE_CONNECTOR, Map.of("CONNECTOR", "observed-stop"), "admin")).doesNotThrowAnyException();
        assertThatThrownBy(() -> journal.intent(plan, Effect.QUIESCE_INBOX, "admin", () -> {})).hasMessageContaining("PLAN_EXPIRED");
        assertThat(journal.summary(command()).confirmedEffects()).containsExactly(Effect.QUIESCE_CONNECTOR);
    }
    @Test void lostResponseRetainsWorkerAndCannotRepeatNativeIntent() {
        prepare(); var claim = claim(); var intent = journal.intent(plan, Effect.QUIESCE_CONNECTOR, "admin", () -> {});
        journal.outcome(plan, intent, Outcome.UNKNOWN, Map.of("failureType", "network"), "admin");
        assertThatThrownBy(() -> journal.intent(plan, Effect.QUIESCE_CONNECTOR, "admin", () -> {})).hasMessageContaining("READ_ONLY_RECONCILIATION_REQUIRED");
        assertThat(store.require(reservation.id()).workerToken()).isEqualTo(claim.workerToken());
        assertThat(journal.summary(command()).status()).isEqualTo("READ_ONLY_RECONCILIATION_REQUIRED");
    }
    @Test void singleEffectFailureDoesNotAuthorizeItsSuccessor() {
        prepare(); claim(); journal.intent(plan, Effect.QUIESCE_CONNECTOR, "admin", () -> {});
        assertThatThrownBy(() -> journal.intent(plan, Effect.QUIESCE_INBOX, "admin", () -> {})).hasMessageContaining("PRIOR_EFFECT_UNCONFIRMED");
    }
    @Test void reconciledFirstEffectAllowsOnlyNewSecondEffect() {
        prepare(); claim(); journal.intent(plan, Effect.QUIESCE_CONNECTOR, "admin", () -> {});
        journal.reconciled(plan, Effect.QUIESCE_CONNECTOR, Map.of("CONNECTOR", "observed"), "admin");
        assertThatCode(() -> journal.intent(plan, Effect.QUIESCE_INBOX, "admin", () -> {})).doesNotThrowAnyException();
        assertThatThrownBy(() -> journal.intent(plan, Effect.QUIESCE_CONNECTOR, "admin", () -> {})).hasMessageContaining("READ_ONLY_RECONCILIATION_REQUIRED");
    }
    @Test void concurrentClaimsAreOneLedgerMutationAndOneWorker() throws Exception {
        prepare();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(this::claim); var b = pool.submit(this::claim);
            assertThat(a.get()).isEqualTo(b.get());
        }
        assertThat(store.require(reservation.id()).revision()).isEqualTo(reservation.revision() + 1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_admission_events WHERE event_type='RUNTIME_ARTIFACT_REPAIR_CLAIMED'", Integer.class)).isEqualTo(1);
    }
    @Test void competingOperationCannotClaimSameFailedRelease() {
        prepare(); claim(); var other = plan("competing-review");
        assertThatThrownBy(() -> journal.claim(other, "admin", () -> { throw new AssertionError("must not mutate ledger"); })).hasMessageContaining("COMPETING_REPAIR");
    }
    @Test void hashDriftFailsBeforeAnyNativeIntent() {
        prepare(); claim(); jdbc.update("UPDATE deployment_admission_reviews SET payload_json=? WHERE kind='ART_PLAN' AND record_key=?", json.write(Map.of("forged", true)), plan.id());
        assertThatThrownBy(() -> journal.requirePlan(command())).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_admission_reviews WHERE kind='ART_INTENT'", Integer.class)).isZero();
    }
    @Test void failedFinalVerificationRollsBackReceiptAndWorkerReleaseTogether() {
        prepare(); var claim = claim(); completeEffects(); var proof = proof();
        assertThatThrownBy(() -> journal.finish(plan, proof, "admin", () -> {
            store.finishRuntimeArtifactRepair(claim, "admin", json.fingerprint(proof));
            throw new IllegalStateException("forced final transaction rollback");
        })).hasMessageContaining("forced final transaction rollback");
        assertThat(journal.verifiedRepair("release")).isNull(); assertThat(store.require(reservation.id()).workerToken()).isEqualTo(claim.workerToken());
    }
    @Test void verifiedRepairPreservesOriginalFailedBindingAndReservationContract() {
        prepare(); var claim = claim(); completeEffects(); var proof = proof();
        String reviewsBefore = json.write(jdbc.queryForList("SELECT * FROM deployment_admission_reviews WHERE kind IN ('POLICY','MANIFEST','GRANT') ORDER BY kind,record_key"));
        journal.finish(plan, proof, "admin", () -> store.finishRuntimeArtifactRepair(claim, "admin", json.fingerprint(proof)));
        var current = store.require(reservation.id());
        assertThat(current.state()).isEqualTo(AdmissionContracts.State.OUTCOME_UNKNOWN); assertThat(current.workerToken()).isNull();
        assertThat(current.manifestFingerprint()).isEqualTo(reservation.manifestFingerprint()); assertThat(current.demand()).isEqualTo(reservation.demand());
        assertThat(current.expiresAt()).isEqualTo(reservation.expiresAt()); assertThat(current.generation()).isEqualTo(reservation.generation());
        assertThat(store.releaseBinding("release").state()).isEqualTo("OUTCOME_UNKNOWN");
        assertThat(json.write(jdbc.queryForList("SELECT * FROM deployment_admission_reviews WHERE kind IN ('POLICY','MANIFEST','GRANT') ORDER BY kind,record_key"))).isEqualTo(reviewsBefore);
        assertThat(journal.verifiedRepair("release")).isEqualTo(proof); assertThat(journal.summary(command()).status()).isEqualTo("VERIFIED");
    }
    void completeEffects() {
        for (Effect effect : Effect.values()) {
            var intent = journal.intent(plan, effect, "admin", () -> {});
            Map<String, String> result = switch (effect) {
                case DEPLOY_RUNTIME -> Map.of("jobUuid", "runtime-job", "applicationUuid", "runtime-app", "repository", "reviewed/runtime", "tag", "sha256-" + "b".repeat(64));
                case RESUME_CONNECTOR -> Map.of("connectorJob", json.write(connectorJob()));
                default -> Map.of("observed", "exact-synthetic-effect");
            };
            journal.outcome(plan, intent, Outcome.CONFIRMED, result, "admin");
        }
    }
    NativeJob connectorJob() { return new NativeJob("connector-job", "connector-app", "finished", NOW, NOW); }
    VerifiedRepair proof() {
        return new VerifiedRepair(plan.id(), journal.fingerprint(plan), plan.local().scope(), plan.local().original(), plan.local().replacement(),
            "runtime-app", "runtime-job", NOW, NOW, connectorJob(), RuntimeArtifactRepairWalletTest.wallet(null, 3),
            plan.local().fingerprints(), Map.of("synthetic", "final-inventory"), NOW);
    }
    static final class MovingClock extends Clock {
        final AtomicReference<Instant> value;
        MovingClock(Instant now) { value = new AtomicReference<>(now); }
        void advance(long seconds) { value.updateAndGet(i -> i.plusSeconds(seconds)); }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return value.get(); }
    }
}
