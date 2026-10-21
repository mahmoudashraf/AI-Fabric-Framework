package com.ai.fabric.platform.backend.deployment.admission;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import static com.ai.fabric.platform.backend.deployment.admission.RuntimeArtifactRepairContracts.*;
import static org.springframework.http.HttpStatus.*;

/** Immutable repair evidence in the existing admission review ledger. No lock spans remote I/O. */
@Repository
public class RuntimeArtifactRepairJournal {
    private static final String PREVIEW = "ART_PREVIEW", PLAN = "ART_PLAN", CLAIM = "ART_CLAIM",
        INTENT = "ART_INTENT", RESULT = "ART_RESULT", VERIFIED = "ART_VERIFIED";
    private final JdbcTemplate jdbc;
    private final AdmissionJson json;
    private final TransactionTemplate transaction;
    private final Clock clock;
    @Autowired
    public RuntimeArtifactRepairJournal(JdbcTemplate jdbc, AdmissionJson json, PlatformTransactionManager transactions) {
        this(jdbc, json, transactions, Clock.systemUTC());
    }
    RuntimeArtifactRepairJournal(JdbcTemplate jdbc, AdmissionJson json, PlatformTransactionManager transactions, Clock clock) {
        this.jdbc = jdbc; this.json = json; this.clock = clock;
        transaction = new TransactionTemplate(transactions);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    <T> T locked(Supplier<T> action) {
        return transaction.execute(status -> {
            jdbc.queryForObject("SELECT id FROM deployment_admission_mutex WHERE id=1 FOR UPDATE", Integer.class);
            return action.get();
        });
    }
    String preview(LocalEvidence evidence, String actor) {
        return locked(() -> {
            requireFresh(evidence.validUntil());
            String id = json.fingerprint(Map.of("scope", evidence.scope(), "operation", evidence.request().operationKey()));
            append(PREVIEW, id, new Preview(actor, evidence), actor);
            return id;
        });
    }
    public LocalEvidence requirePreview(String id, String actor) {
        var preview = required(PREVIEW, id, Preview.class);
        if (!preview.actor().equals(actor)) fail("ACTOR_CHANGED");
        requireFresh(preview.evidence().validUntil());
        return preview.evidence();
    }
    /** Fixed-scope authority, with no caller-selected scope list or failed-release status bypass. */
    public LocalEvidence authorityEvidence(String id, String actor, boolean write) {
        var plan = optional(PLAN, id, Plan.class);
        if (plan == null) {
            if (write) fail("WORKER_CLAIM_REQUIRED");
            return requirePreview(id, actor);
        }
        if (!plan.actorId().equals(actor)) fail("ACTOR_CHANGED");
        var claim = optional(CLAIM, plan.local().scope().releaseId(), Claim.class);
        if (write || claim != null) {
            claim = requireClaim(plan);
            var rows = jdbc.queryForList("SELECT payload_json FROM deployment_capacity_reservations WHERE id=?", plan.local().scope().reservationId());
            if (rows.size() != 1) fail("WORKER_CHANGED");
            var r = json.read((String) rows.getFirst().get("payload_json"), AdmissionContracts.Reservation.class);
            if (!r.fence().equals(claim.reservationFence()) || !Objects.equals(r.workerToken(), claim.workerToken())
                || r.state() != AdmissionContracts.State.OUTCOME_UNKNOWN) fail("WORKER_CHANGED");
            if (write) requireFresh(executionDeadline(plan));
        } else requireFresh(plan.expiresAt());
        return plan.local();
    }
    Plan savePlan(String id, String actor, NativeEvidence nativeEvidence) {
        return locked(() -> {
            LocalEvidence local = requirePreview(id, actor);
            Instant now = clock.instant();
            if (nativeEvidence.observedAt() == null || nativeEvidence.observedAt().isAfter(now)
                || nativeEvidence.observedAt().isBefore(now.minusSeconds(60))) fail("NATIVE_EVIDENCE_STALE");
            var prior = optional(PLAN, id, Plan.class);
            if (prior != null) {
                if (!prior.actorId().equals(actor) || !json.fingerprint(prior.local()).equals(json.fingerprint(local))) fail("PLAN_CHANGED");
                return prior;
            }
            Instant expiry = now.plusSeconds(300).isBefore(local.validUntil()) ? now.plusSeconds(300) : local.validUntil();
            var plan = new Plan(id, actor, local, nativeEvidence, now, expiry);
            append(PLAN, id, plan, actor);
            return plan;
        });
    }
    public Plan requirePlan(Command command) {
        if (command == null || command.planId() == null || command.planFingerprint() == null) fail("COMMAND_REQUIRED");
        var plan = required(PLAN, command.planId(), Plan.class);
        if (!json.fingerprint(plan).equals(command.planFingerprint())) fail("PLAN_CHANGED");
        return plan;
    }
    public String fingerprint(Plan plan) { return json.fingerprint(plan); }
    Claim claim(Plan plan, String actor, Supplier<AdmissionContracts.Reservation> claimWorker) {
        return locked(() -> {
            var prior = optional(CLAIM, plan.local().scope().releaseId(), Claim.class);
            if (prior != null) {
                if (!prior.planId().equals(plan.id()) || !prior.planFingerprint().equals(json.fingerprint(plan))
                    || !prior.actorId().equals(actor)) fail("COMPETING_REPAIR");
                return prior;
            }
            requireFresh(plan.expiresAt());
            if (!plan.actorId().equals(actor)) fail("ACTOR_CHANGED");
            requireNoActiveRepair(plan.local().scope().deploymentId(), plan.local().scope().targetProfileId());
            var reservation = claimWorker.get();
            requireFresh(plan.local().executionDeadline());
            var claim = new Claim(plan.id(), json.fingerprint(plan), actor, reservation.workerToken(), reservation.fence(),
                clock.instant(), plan.local().executionDeadline());
            if (claim.workerToken() == null) fail("WORKER_CLAIM_REQUIRED");
            append(CLAIM, plan.local().scope().releaseId(), claim, actor);
            return claim;
        });
    }
    public Claim requireClaim(Plan plan) {
        var claim = required(CLAIM, plan.local().scope().releaseId(), Claim.class);
        if (!claim.planId().equals(plan.id()) || !claim.planFingerprint().equals(json.fingerprint(plan))) fail("CLAIM_CHANGED");
        return claim;
    }
    public Instant executionDeadline(Plan plan) {
        var windows = windows(plan);
        return windows.isEmpty() ? requireClaim(plan).executionDeadline() : windows.getLast().review().executionDeadline();
    }
    public boolean emptyJdbcColdRestartAccepted(Plan plan) {
        var windows = windows(plan);
        return !windows.isEmpty() && windows.getLast().review().acceptEmptyJdbcColdRestart();
    }
    private List<ExecutionWindow> windows(Plan plan) {
        var keys = jdbc.queryForList("SELECT record_key FROM deployment_admission_reviews WHERE kind='ART_WINDOW' AND (record_key=? OR record_key LIKE ?)",
            String.class, plan.id(), plan.id() + ":%");
        if (keys.size() > 32) fail("WINDOW_REVIEW_LIMIT");
        var values = new ArrayList<ExecutionWindow>();
        Instant previous = requireClaim(plan).executionDeadline();
        for (int index = 0; index < keys.size(); index++) {
            String key = index == 0 ? plan.id() : plan.id() + ":" + index;
            if (!keys.contains(key)) fail("WINDOW_CHAIN_CHANGED");
            var value = required("ART_WINDOW", key, ExecutionWindow.class);
            if (!plan.actorId().equals(value.actorId()) || value.review() == null
                || !new Command(plan.id(), fingerprint(plan)).equals(value.review().command())
                || !previous.equals(value.review().expectedExecutionDeadline())
                || value.review().executionDeadline() == null || !value.review().executionDeadline().isAfter(previous))
                fail("WINDOW_CHAIN_CHANGED");
            previous = value.review().executionDeadline(); values.add(value);
        }
        return List.copyOf(values);
    }
    void recordColdStoreEvidence(Plan plan, Object proof) {
        locked(() -> {
            requireClaim(plan);
            var value = Map.of("planId", plan.id(), "planFingerprint", fingerprint(plan), "scope", plan.local().scope(), "proof", proof);
            append("ART_COLD_STORE", json.fingerprint(value), value, plan.actorId());
            return null;
        });
    }
    ExecutionWindow renewWindow(Plan plan, RenewWindow review, String actor, Supplier<Instant> revalidate) {
        return locked(() -> {
            var claim = requireClaim(plan);
            if (!claim.actorId().equals(actor) || !plan.actorId().equals(actor)) fail("ACTOR_CHANGED");
            if (review == null || !new Command(plan.id(), fingerprint(plan)).equals(review.command())
                || review.expectedExecutionDeadline() == null || review.executionDeadline() == null
                || review.reason() == null || review.reason().isBlank() || review.reason().length() > 1000
                || review.acceptEmptyJdbcColdRestart() && plan.local().request().mode() != Mode.IDLE_CHECKPOINT_COLD_RESTART)
                fail("WINDOW_REVIEW_REQUIRED");
            var windows = windows(plan);
            for (var prior : windows) if (prior.review().equals(review)) return prior;
            if (windows.size() >= 32) fail("WINDOW_REVIEW_LIMIT");
            Instant currentDeadline = windows.isEmpty() ? claim.executionDeadline() : windows.getLast().review().executionDeadline();
            if (!currentDeadline.equals(review.expectedExecutionDeadline())
                || !review.executionDeadline().isAfter(clock.instant())
                || !review.executionDeadline().isAfter(currentDeadline)
                || review.executionDeadline().isAfter(clock.instant().plusSeconds(7200))) fail("WINDOW_INVALID");
            if (verifiedRepair(plan.local().scope().releaseId()) != null) fail("ALREADY_VERIFIED");
            Instant reservationExpiry = revalidate.get();
            if (reservationExpiry == null || review.executionDeadline().isAfter(reservationExpiry)) fail("RESERVATION_EXPIRED");
            var value = new ExecutionWindow(review, actor, clock.instant());
            append("ART_WINDOW", windows.isEmpty() ? plan.id() : plan.id() + ":" + windows.size(), value, actor);
            return value;
        });
    }
    EffectReceipt intent(Plan plan, Effect effect, String actor, Runnable revalidate) {
        return locked(() -> {
            var claim = requireClaim(plan);
            if (!claim.actorId().equals(actor)) fail("ACTOR_CHANGED");
            if (verifiedRepair(plan.local().scope().releaseId()) != null) fail("ALREADY_VERIFIED");
            requireFresh(executionDeadline(plan));
            for (Effect previous : Effect.values()) {
                if (previous == effect) break;
                if (confirmed(plan, previous) == null) fail("PRIOR_EFFECT_UNCONFIRMED");
            }
            if (optional(INTENT, key(plan.id(), effect), EffectReceipt.class) != null) fail("READ_ONLY_RECONCILIATION_REQUIRED");
            revalidate.run();
            String hash = json.fingerprint(Map.of("plan", claim.planFingerprint(), "effect", effect, "worker", claim.workerToken()));
            var value = new EffectReceipt(plan.id(), effect, Outcome.INTENT, hash, Map.of(), clock.instant());
            append(INTENT, key(plan.id(), effect), value, actor);
            return value;
        });
    }
    void outcome(Plan plan, EffectReceipt intent, Outcome outcome, Map<String, String> evidence, String actor) {
        locked(() -> {
            if (outcome == Outcome.INTENT) fail("OUTCOME_INVALID");
            var actual = required(INTENT, key(plan.id(), intent.effect()), EffectReceipt.class);
            if (!actual.equals(intent)) fail("INTENT_CHANGED");
            append(RESULT, key(plan.id(), intent.effect()), new EffectReceipt(plan.id(), intent.effect(), outcome,
                intent.requestFingerprint(), evidence, clock.instant()), actor);
            return null;
        });
    }
    void reconciled(Plan plan, Effect effect, Map<String, String> evidence, String actor) {
        locked(() -> {
            required(INTENT, key(plan.id(), effect), EffectReceipt.class);
            var prior = optional(RESULT, key(plan.id(), effect), EffectReceipt.class);
            if (prior != null && prior.outcome() == Outcome.CONFIRMED) fail("ALREADY_CONFIRMED");
            var intent = required(INTENT, key(plan.id(), effect), EffectReceipt.class);
            append("ART_RECONCILED", key(plan.id(), effect), new EffectReceipt(plan.id(), effect, Outcome.CONFIRMED,
                intent.requestFingerprint(), evidence, clock.instant()), actor);
            return null;
        });
    }
    public EffectReceipt confirmed(Plan plan, Effect effect) {
        var reconciled = optional("ART_RECONCILED", key(plan.id(), effect), EffectReceipt.class);
        var value = reconciled != null ? reconciled : optional(RESULT, key(plan.id(), effect), EffectReceipt.class);
        return value != null && value.outcome() == Outcome.CONFIRMED ? value : null;
    }
    public void dispatched(Plan plan, Effect effect, Map<String, String> response, String actor) {
        locked(() -> {
            required(INTENT, key(plan.id(), effect), EffectReceipt.class);
            append("ART_DISPATCH", key(plan.id(), effect), response, actor);
            return null;
        });
    }
    @SuppressWarnings("unchecked")
    public Map<String, String> dispatch(Plan plan, Effect effect) {
        return optional("ART_DISPATCH", key(plan.id(), effect), Map.class);
    }
    public void requireEffectAuthority(Plan plan, Effect effect, String actor) {
        authorityEvidence(plan.id(), actor, true);
        required(INTENT, key(plan.id(), effect), EffectReceipt.class);
        if (optional(RESULT, key(plan.id(), effect), EffectReceipt.class) != null
            || optional("ART_RECONCILED", key(plan.id(), effect), EffectReceipt.class) != null) fail("READ_ONLY_RECONCILIATION_REQUIRED");
    }
    VerifiedRepair finish(Plan plan, VerifiedRepair proof, String actor, Runnable releaseWorker) {
        return locked(() -> {
            requireClaim(plan);
            for (Effect effect : Effect.values()) if (confirmed(plan, effect) == null) fail("EFFECTS_INCOMPLETE");
            if (!proof.planId().equals(plan.id()) || !proof.planFingerprint().equals(json.fingerprint(plan))
                || !proof.scope().equals(plan.local().scope()) || !proof.original().equals(plan.local().original())
                || !proof.replacement().equals(plan.local().replacement())
                || !proof.applicationUuid().equals(proof.scope().runtimeApplicationUuid())
                || proof.deploymentJobUuid() == null || proof.deploymentJobUuid().isBlank()
                || proof.jobCreatedAt() == null || proof.jobFinishedAt() == null
                || proof.jobCreatedAt().isBefore(plan.createdAt()) || proof.jobFinishedAt().isBefore(proof.jobCreatedAt())
                || proof.verifiedAt().isBefore(proof.jobFinishedAt()) || proof.verifiedAt().isAfter(clock.instant())) fail("FINAL_PROOF_CHANGED");
            var deploy = confirmed(plan, Effect.DEPLOY_RUNTIME);
            if (!Objects.equals(deploy.evidence().get("jobUuid"), proof.deploymentJobUuid())
                || !Objects.equals(deploy.evidence().get("applicationUuid"), proof.applicationUuid())
                || !Objects.equals(deploy.evidence().get("repository"), proof.replacement().repository())
                || !Objects.equals(deploy.evidence().get("tag"), "sha256-" + proof.replacement().digest().substring(7))) fail("DEPLOY_PROOF_CHANGED");
            var connector = confirmed(plan, Effect.RESUME_CONNECTOR);
            if (proof.connectorJob() == null || !Objects.equals(proof.connectorJob(),
                json.read(connector.evidence().get("connectorJob"), NativeJob.class))
                || !proof.scope().connectorApplicationUuid().equals(proof.connectorJob().applicationUuid())
                || !"finished".equals(proof.connectorJob().status())
                || !plan.local().fingerprints().equals(proof.finalLocalFingerprints())) fail("FINAL_INVENTORY_CHANGED");
            RuntimeArtifactRepairWallet.requireUnchanged(plan.nativeEvidence().wallet(), proof.wallet());
            append(VERIFIED, proof.scope().releaseId(), proof, actor);
            releaseWorker.run();
            return proof;
        });
    }
    public VerifiedRepair verifiedRepair(String releaseId) {
        var proof = optional(VERIFIED, releaseId, VerifiedRepair.class);
        if (proof == null) return null;
        var plan = required(PLAN, proof.planId(), Plan.class);
        if (!json.fingerprint(plan).equals(proof.planFingerprint()) || !plan.local().scope().equals(proof.scope())
            || !plan.local().original().equals(proof.original()) || !plan.local().replacement().equals(proof.replacement())) fail("VERIFIED_RECEIPT_CHANGED");
        requireClaim(plan);
        for (Effect effect : Effect.values()) if (confirmed(plan, effect) == null) fail("VERIFIED_RECEIPT_INCOMPLETE");
        return proof;
    }
    public boolean hasClaim(String releaseId) { return optional(CLAIM, releaseId, Claim.class) != null; }
    public void requireNoActiveRepair(String deploymentId, String targetProfileId) {
        for (String key : jdbc.queryForList("SELECT record_key FROM deployment_admission_reviews WHERE kind=? ORDER BY record_key", String.class, CLAIM)) {
            var claim = required(CLAIM, key, Claim.class); var plan = required(PLAN, claim.planId(), Plan.class);
            if (deploymentId.equals(plan.local().scope().deploymentId()) && targetProfileId.equals(plan.local().scope().targetProfileId())
                && verifiedRepair(key) == null) fail("REPAIR_IN_PROGRESS");
        }
    }
    public Summary summary(Command command) {
        var plan = requirePlan(command); var proof = verifiedRepair(plan.local().scope().releaseId());
        var confirmed = new ArrayList<Effect>(); Effect pending = null;
        for (Effect effect : Effect.values()) {
            if (confirmed(plan, effect) != null) confirmed.add(effect);
            else if (pending == null && optional(INTENT, key(plan.id(), effect), EffectReceipt.class) != null) pending = effect;
        }
        return new Summary(plan.id(), plan.local().scope().releaseId(), proof != null ? "VERIFIED" : pending != null
            ? "READ_ONLY_RECONCILIATION_REQUIRED" : hasClaim(plan.local().scope().releaseId()) ? "CLAIMED" : "PLANNED",
            pending, List.copyOf(confirmed), proof == null ? null : json.fingerprint(proof), false);
    }
    private String key(String plan, Effect effect) { return json.fingerprint(Map.of("plan", plan, "effect", effect)); }
    private void append(String kind, String key, Object value, String actor) {
        String hash = json.fingerprint(value);
        var prior = jdbc.queryForList("SELECT fingerprint FROM deployment_admission_reviews WHERE kind=? AND record_key=?", String.class, kind, key);
        if (!prior.isEmpty()) { if (!prior.equals(List.of(hash))) fail("REVIEW_IMMUTABLE"); return; }
        jdbc.update("INSERT INTO deployment_admission_reviews(kind,record_key,fingerprint,payload_json,reviewed_by,reviewed_at) VALUES (?,?,?,?,?,?)",
            kind, key, hash, json.write(value), actor, Timestamp.from(clock.instant()));
    }
    private <T> T required(String kind, String key, Class<T> type) {
        var value = optional(kind, key, type); if (value == null) fail("REVIEW_REQUIRED"); return value;
    }
    private <T> T optional(String kind, String key, Class<T> type) {
        var values = jdbc.queryForList("SELECT fingerprint,payload_json FROM deployment_admission_reviews WHERE kind=? AND record_key=?", kind, key);
        if (values.isEmpty()) return null;
        var row = values.getFirst(); var value = json.read((String) row.get("payload_json"), type);
        if (!json.fingerprint(value).equals(row.get("fingerprint"))) fail("REVIEW_HASH_CHANGED");
        return value;
    }
    private void requireFresh(Instant until) { if (until == null || !until.isAfter(clock.instant())) fail("PLAN_EXPIRED"); }
    private static void fail(String code) { throw new AdmissionFailure(CONFLICT, "RUNTIME_ARTIFACT_REPAIR_" + code); }
    private record Preview(String actor, LocalEvidence evidence) { }
}
