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
    Plan plan(String operation) { return plan(operation, NOW.plusSeconds(1800), Mode.FULL_DRAIN); }
    Plan plan(String operation, Instant deadline, Mode mode) {
        var scope = new Scope("customer", "tenant", "deployment1", TARGET, "release", "version", "config-hash", "authority",
            reservation.id(), reservation.generation(), "https://runtime.example.test", "runtime-app", "connector-app", "runtime-pg");
        var request = new Request(reservation.fence(), "replacement", "offer-fingerprint", 2, operation, "reviewed fix", true, mode);
        var original = new ArtifactPin("original", "original-pin", "reviewed/runtime", "sha256:" + "a".repeat(64), "a".repeat(40), "ci-old", "staging", "old-manifest");
        var replacement = new ArtifactPin("replacement", "replacement-pin", "reviewed/runtime", "sha256:" + "b".repeat(64), "b".repeat(40), "ci-new", "staging", "new-manifest");
        var local = new LocalEvidence(scope, request, original, replacement, Map.of("graph", "unchanged"), NOW.plusSeconds(300), deadline);
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
    @Test void explicitWindowRenewalPreservesOriginalPlanReservationAndUnknownIntent() {
        prepare(); plan = plan("window-basic", NOW.plusSeconds(300), Mode.IDLE_CHECKPOINT_COLD_RESTART);
        var claim = claim(); var before = store.require(reservation.id());
        var intent = journal.intent(plan, Effect.QUIESCE_CONNECTOR, "admin", () -> {});
        journal.outcome(plan, intent, Outcome.UNKNOWN, Map.of("failureType", "lost-response"), "admin");
        clock.advance(301);
        var review = new RenewWindow(command(), NOW.plusSeconds(300), NOW.plusSeconds(900), "Continue the reviewed empty initial setup", true);
        var renewed = journal.renewWindow(plan, review, "admin", () -> before.expiresAt());
        assertThat(journal.renewWindow(plan, review, "admin", () -> { throw new AssertionError("duplicate review must not revalidate or dispatch"); })).isEqualTo(renewed);
        assertThat(journal.executionDeadline(plan)).isEqualTo(review.executionDeadline());
        assertThat(journal.requireClaim(plan)).isEqualTo(claim);
        assertThat(journal.requirePlan(command())).isEqualTo(plan);
        assertThat(store.require(reservation.id())).isEqualTo(before);
        assertThat(journal.emptyJdbcColdRestartAccepted(plan)).isTrue();
        assertThatThrownBy(() -> journal.intent(plan, Effect.QUIESCE_CONNECTOR, "admin", () -> {})).hasMessageContaining("READ_ONLY_RECONCILIATION_REQUIRED");
        journal.reconciled(plan, Effect.QUIESCE_CONNECTOR, Map.of("CONNECTOR", "observed-stop"), "admin");
        assertThatCode(() -> journal.intent(plan, Effect.QUIESCE_INBOX, "admin", () -> {})).doesNotThrowAnyException();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_admission_reviews WHERE kind='ART_WINDOW'", Integer.class)).isEqualTo(1);
    }
    @Test void operatorCanAppendASecondFiniteReviewAfterAnApprovalDelayWithoutReplayingEffects() {
        prepare(); plan = plan("window-pause", NOW.plusSeconds(300), Mode.IDLE_CHECKPOINT_COLD_RESTART);
        var claim = claim(); var before = store.require(reservation.id());
        var intent = journal.intent(plan, Effect.QUIESCE_CONNECTOR, "admin", () -> {});
        journal.outcome(plan, intent, Outcome.UNKNOWN, Map.of("failureType", "lost-response"), "admin");
        var firstReview = new RenewWindow(command(), NOW.plusSeconds(300), NOW.plusSeconds(600), "Await the required host approval", true);
        var first = journal.renewWindow(plan, firstReview, "admin", before::expiresAt);
        clock.advance(601);
        assertThatThrownBy(() -> journal.intent(plan, Effect.QUIESCE_INBOX, "admin", () -> {})).hasMessageContaining("PLAN_EXPIRED");
        var nextReview = new RenewWindow(command(), NOW.plusSeconds(600), NOW.plusSeconds(900), "Approval received; continue the same repair", true);
        var next = journal.renewWindow(plan, nextReview, "admin", before::expiresAt);
        assertThat(journal.renewWindow(plan, nextReview, "admin", () -> { throw new AssertionError("duplicate must not dispatch"); })).isEqualTo(next);
        assertThat(journal.renewWindow(plan, firstReview, "admin", () -> { throw new AssertionError("historical duplicate must not dispatch"); })).isEqualTo(first);
        assertThat(journal.executionDeadline(plan)).isEqualTo(nextReview.executionDeadline());
        assertThat(journal.requireClaim(plan)).isEqualTo(claim); assertThat(journal.requirePlan(command())).isEqualTo(plan);
        assertThat(store.require(reservation.id())).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_admission_reviews WHERE kind='ART_WINDOW'", Integer.class)).isEqualTo(2);
        assertThatThrownBy(() -> journal.intent(plan, Effect.QUIESCE_CONNECTOR, "admin", () -> {})).hasMessageContaining("READ_ONLY_RECONCILIATION_REQUIRED");
        journal.reconciled(plan, Effect.QUIESCE_CONNECTOR, Map.of("CONNECTOR", "observed-stop"), "admin");
        assertThatCode(() -> journal.intent(plan, Effect.QUIESCE_INBOX, "admin", () -> {})).doesNotThrowAnyException();
    }
    @Test void laterReviewMustNameTheCurrentDeadlineAndCurrentConsent() {
        prepare(); plan = plan("window-chain", NOW.plusSeconds(300), Mode.IDLE_CHECKPOINT_COLD_RESTART); claim();
        journal.renewWindow(plan, new RenewWindow(command(), NOW.plusSeconds(300), NOW.plusSeconds(600), "First reviewed window", true), "admin", reservation::expiresAt);
        var stale = new RenewWindow(command(), NOW.plusSeconds(300), NOW.plusSeconds(900), "Stale predecessor", true);
        assertThatThrownBy(() -> journal.renewWindow(plan, stale, "admin", reservation::expiresAt)).hasMessageContaining("WINDOW_INVALID");
        journal.renewWindow(plan, new RenewWindow(command(), NOW.plusSeconds(600), NOW.plusSeconds(900), "Continue without cold-state consent", false), "admin", reservation::expiresAt);
        assertThat(journal.emptyJdbcColdRestartAccepted(plan)).isFalse();
    }
    @Test void windowRenewalCannotExceedReservationOrChangeActor() {
        prepare(); plan = plan("window-bounds", NOW.plusSeconds(300), Mode.IDLE_CHECKPOINT_COLD_RESTART); claim(); clock.advance(301);
        var review = new RenewWindow(command(), NOW.plusSeconds(300), reservation.expiresAt().plusSeconds(1), "Continue setup", true);
        assertThatThrownBy(() -> journal.renewWindow(plan, review, "admin", () -> reservation.expiresAt())).hasMessageContaining("RESERVATION_EXPIRED");
        assertThatThrownBy(() -> journal.renewWindow(plan, review, "other-admin", () -> reservation.expiresAt())).hasMessageContaining("ACTOR_CHANGED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_admission_reviews WHERE kind='ART_WINDOW'", Integer.class)).isZero();
    }
    @Test void windowRenewalRequiresExactDeadlineAndBoundsTheNewWindow() {
        prepare(); claim();
        var stale = new RenewWindow(command(), NOW.plusSeconds(1799), NOW.plusSeconds(2000), "Continue setup", false);
        assertThatThrownBy(() -> journal.renewWindow(plan, stale, "admin", () -> NOW.plusSeconds(10000))).hasMessageContaining("WINDOW_INVALID");
        var excessive = new RenewWindow(command(), NOW.plusSeconds(1800), NOW.plusSeconds(7201), "Continue setup", false);
        assertThatThrownBy(() -> journal.renewWindow(plan, excessive, "admin", () -> NOW.plusSeconds(10000))).hasMessageContaining("WINDOW_INVALID");
    }
    @Test void fullDrainReviewCannotAuthorizeDiscardingNonDurableColdState() {
        prepare(); claim();
        var review = new RenewWindow(command(), NOW.plusSeconds(1800), NOW.plusSeconds(2000), "Continue setup", true);
        assertThatThrownBy(() -> journal.renewWindow(plan, review, "admin", () -> NOW.plusSeconds(10000))).hasMessageContaining("WINDOW_REVIEW_REQUIRED");
    }
    @Test void singleEffectFailureDoesNotAuthorizeItsSuccessor() {
        prepare(); claim(); journal.intent(plan, Effect.QUIESCE_CONNECTOR, "admin", () -> {});
        assertThatThrownBy(() -> journal.intent(plan, Effect.QUIESCE_INBOX, "admin", () -> {})).hasMessageContaining("PRIOR_EFFECT_UNCONFIRMED");
    }
    @Test void explicitColdMaintenanceHasItsOwnOrderedAdoptionAndCannotClaimDrainCommands() {
        prepare();
        plan = plan("cold-maintenance", NOW.plusSeconds(1800), Mode.COLD_MAINTENANCE_REAPPLY);
        claim();
        for (Effect effect : java.util.List.of(Effect.QUIESCE_CONNECTOR, Effect.QUIESCE_INBOX)) {
            var intent = journal.intent(plan, effect, "admin", () -> {});
            journal.outcome(plan, intent, Outcome.CONFIRMED, Map.of("observed", "stopped"), "admin");
        }
        assertThatThrownBy(() -> journal.intent(plan, Effect.BEGIN_DRAIN, "admin", () -> {}))
            .hasMessageContaining("EFFECT_NOT_APPROVED");
        assertThatThrownBy(() -> journal.intent(plan, Effect.CANCEL_DRAIN, "admin", () -> {}))
            .hasMessageContaining("EFFECT_NOT_APPROVED");
        assertThatThrownBy(() -> journal.intent(plan, Effect.ADOPT_COLD_GENERATION, "admin", () -> {}))
            .hasMessageContaining("PRIOR_EFFECT_UNCONFIRMED");
        assertThatCode(() -> journal.intent(plan, Effect.STOP_RUNTIME, "admin", () -> {})).doesNotThrowAnyException();
        assertThat(effects(plan.local().request().mode())).containsSubsequence(
            Effect.STOP_RUNTIME, Effect.PATCH_IMAGE, Effect.PATCH_PROVENANCE, Effect.DEPLOY_RUNTIME,
            Effect.ADOPT_COLD_GENERATION, Effect.RESUME_INBOX, Effect.RESUME_CONNECTOR);
    }
    @Test void absentColdConsentFieldDoesNotChangeExistingSealedRequestFingerprints() {
        var fields = new java.util.LinkedHashMap<String, Object>();
        fields.put("fence", new AdmissionContracts.Fence("reservation", 7, "generation"));
        fields.put("replacementArtifactId", "replacement");
        fields.put("expectedOfferFingerprint", "offer");
        fields.put("expectedEntitlementRevision", 2);
        fields.put("operationKey", "legacy-repair");
        fields.put("reason", "reviewed fix");
        fields.put("acceptRuntimeRestart", true);
        fields.put("mode", "FULL_DRAIN");
        var parsed = json.read(json.write(fields), Request.class);
        assertThat(parsed.coldMaintenance()).isNull();
        assertThat(json.fingerprint(parsed)).isEqualTo(json.fingerprint(fields));
        assertThat(json.write(parsed)).doesNotContain("coldMaintenance");
        var acknowledged = new Request(parsed.fence(), parsed.replacementArtifactId(), parsed.expectedOfferFingerprint(),
            parsed.expectedEntitlementRevision(), parsed.operationKey(), parsed.reason(), true,
            Mode.COLD_MAINTENANCE_REAPPLY, new ColdMaintenanceAcknowledgement("prior-release", "owner-approval", true));
        assertThat(json.fingerprint(acknowledged)).isNotEqualTo(json.fingerprint(parsed));
        assertThat(json.read(json.write(acknowledged), Request.class)).isEqualTo(acknowledged);
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
        for (Effect effect : effects(plan.local().request().mode())) {
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
