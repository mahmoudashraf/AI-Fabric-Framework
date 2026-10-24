package com.ai.fabric.platform.backend.deployment.budget;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageAllowanceContracts.*;
import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageAllowanceFailure.*;
import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageTopUpContracts.*;

/** Append-only funding history. Call mutations and cap projection under the existing grant/allocation lock. */
final class ProviderUsageTopUpStore {
    private final JdbcTemplate jdbc;
    private final ProviderUsageAllowanceCodec codec;

    ProviderUsageTopUpStore(JdbcTemplate jdbc, ProviderUsageAllowanceCodec codec) { this.jdbc = jdbc; this.codec = codec; }

    Review read(String id) {
        var rows = jdbc.query("SELECT * FROM provider_usage_top_up_reviews WHERE top_up_id=?", (rs, i) -> {
            var review = codec.read(rs.getString("payload_json"), Review.class);
            var command = review.command();
            if (command == null || review.reviewedAt() == null || !command.runtimeCommand().authorizedBy().equals(review.actorId())
                || !id.equals(command.review().topUpId()) || !command.review().scope().approvalId().equals(rs.getString("approval_id"))
                || !command.review().approvalEvidenceRef().equals(rs.getString("approval_evidence_ref"))
                || !command.reviewFingerprint().equals(rs.getString("review_fingerprint"))
                || !command.reviewFingerprint().equals(commandFingerprint(codec, command))
                || !codec.fingerprint(review).equals(rs.getString("record_fingerprint"))) throw changed();
            return review;
        }, id);
        if (rows.size() > 1) throw changed();
        return rows.isEmpty() ? null : rows.getFirst();
    }

    void requireNewFunding(ReviewRequest request) {
        var original = jdbc.queryForObject("""
            SELECT g.payload_json FROM provider_usage_grants g JOIN provider_usage_allocations a ON a.grant_id=g.id
            WHERE a.approval_id=?
            """, (rs, i) -> codec.read(rs.getString(1), ProviderUsageGrant.class), request.scope().approvalId());
        if (original == null || original.ownerApprovalEvidenceRef().equals(request.approvalEvidenceRef())
            || jdbc.queryForObject("SELECT count(*) FROM provider_usage_top_up_reviews WHERE approval_evidence_ref=?", Long.class,
                request.approvalEvidenceRef()) != 0) throw conflict("PROVIDER_USAGE_TOP_UP_NEW_FUNDING_REQUIRED");
    }

    void save(Review review) {
        var command = review.command();
        jdbc.update("INSERT INTO provider_usage_top_up_reviews VALUES (?,?,?,?,?,?)", command.review().topUpId(),
            command.review().scope().approvalId(), command.review().approvalEvidenceRef(), command.reviewFingerprint(),
            codec.fingerprint(review), codec.write(review));
    }

    Event event(Review review, String kind) {
        var id = review.command().review().topUpId();
        var rows = jdbc.query("SELECT * FROM provider_usage_top_up_events WHERE top_up_id=? AND event_type=?", (rs, i) -> {
            var event = codec.read(rs.getString("payload_json"), Event.class);
            if (!id.equals(event.topUpId()) || !kind.equals(event.eventType())
                || !review.command().reviewFingerprint().equals(event.commandFingerprint())
                || event.actorId() == null || event.actorId().isBlank() || event.recordedAt() == null
                || event.recordedAt().isBefore(review.reviewedAt())
                || !codec.fingerprint(event).equals(rs.getString("record_fingerprint"))
                || ("ACK".equals(kind) != (event.receipt() != null))) throw changed();
            if (event.receipt() != null) verifyReceipt(review.command(), event.receipt());
            return event;
        }, id, kind);
        if (rows.size() > 1) throw changed();
        return rows.isEmpty() ? null : rows.getFirst();
    }

    void append(Review review, String kind, String actor, Instant now, RuntimeReceipt receipt) {
        var event = new Event(review.command().review().topUpId(), kind, review.command().reviewFingerprint(), actor, now, receipt);
        jdbc.update("INSERT INTO provider_usage_top_up_events VALUES (?,?,?,?)", event.topUpId(), kind,
            codec.fingerprint(event), codec.write(event));
    }

    private List<Review> reviews(String approvalId) {
        return jdbc.query("SELECT top_up_id FROM provider_usage_top_up_reviews WHERE approval_id=? ORDER BY top_up_id",
            (rs, i) -> rs.getString(1), approvalId).stream().map(this::read).toList();
    }

    void noPending(String approvalId) {
        for (var review : reviews(approvalId)) {
            if (event(review, "INTENT") != null && event(review, "ACK") == null)
                throw conflict("PROVIDER_USAGE_TOP_UP_RECONCILIATION_REQUIRED");
        }
    }

    Limits effectiveLimits(SafeAllowanceProjection allocation) {
        var acknowledged = new ArrayList<RuntimeReceipt>();
        int pending = 0;
        for (var review : reviews(allocation.approvalId())) {
            var command = review.command(); var request = command.review();
            if (!request.scope().equals(allocation.scope()) || !request.expectedApprovalFingerprint().equals(allocation.approvalFingerprint())
                || !request.storeUuid().equals(allocation.storeUuid())
                || !command.runtimeCommand().runtimeFingerprint().equals(allocation.runtimeAllocationFingerprint())) throw changed();
            var intent = event(review, "INTENT"); var unknown = event(review, "UNKNOWN"); var ack = event(review, "ACK");
            if ((ack != null || unknown != null) && intent == null) throw changed();
            if (unknown != null && unknown.recordedAt().isBefore(intent.recordedAt())) throw changed();
            if (ack != null) {
                if (ack.recordedAt().isBefore(intent.recordedAt())) throw changed();
                verifyScope(ack.receipt(), allocation);
                verifyReceiptTime(ack.receipt(), intent.recordedAt(), ack.recordedAt());
                acknowledged.add(ack.receipt());
            } else if (intent != null) pending++;
        }
        if (pending > 1) throw changed();
        // Every nonzero additive step strictly increases this lexicographic ordering, even at identical timestamps.
        acknowledged.sort(Comparator.comparingLong((RuntimeReceipt r) -> r.effectiveLimits().chatAttempts())
            .thenComparingLong(r -> r.effectiveLimits().totalTokens()).thenComparingLong(r -> r.effectiveLimits().costNanoUsd()));
        Limits effective = allocation.caps();
        for (var receipt : acknowledged) {
            if (!receipt.previousEffectiveLimits().equals(effective)) throw changed();
            effective = receipt.effectiveLimits();
        }
        return effective;
    }

    static String commandFingerprint(ProviderUsageAllowanceCodec codec, Command command) {
        return codec.fingerprint(List.of(command.review(), command.runtimeCommand(), command.runtimeBaseUrl(),
            command.authorityFingerprint(), command.capabilityManifestHash()));
    }

    static void verifyReceipt(Command command, RuntimeReceipt receipt) {
        var expected = command.runtimeCommand();
        if (receipt == null || receipt.schemaVersion() != 1 || !expected.topUpId().equals(receipt.topUpId())
            || !expected.approvalId().equals(receipt.approvalId()) || !expected.storeUuid().equals(receipt.storeUuid())
            || !expected.runtimeFingerprint().equals(receipt.runtimeFingerprint())
            || !expected.expectedEffectiveLimits().equals(receipt.previousEffectiveLimits())
            || !expected.additionalLimits().equals(receipt.additionalLimits())
            || !add(expected.expectedEffectiveLimits(), expected.additionalLimits()).equals(receipt.effectiveLimits())
            || !expected.authorizedBy().equals(receipt.authorizedBy())
            || !expected.approvalEvidenceRef().equals(receipt.approvalEvidenceRef()) || !expected.payerRef().equals(receipt.payerRef())
            || !ProviderUsageAllowanceCodec.sha256(expected.reason()).equals(receipt.reasonSha256()) || receipt.authorizedAt() == null) throw changed();
    }

    static void verifyScope(RuntimeReceipt receipt, SafeAllowanceProjection allocation) {
        var scope = allocation.scope();
        var expected = new ProviderUsageValidityExtensionContracts.RuntimeScope(scope.customerId(), scope.deploymentId(),
            scope.targetProfileId(), scope.environment(), "sha256:" + allocation.credentialSha256(), allocation.pricingReference());
        if (!expected.equals(receipt.scope())) throw changed();
    }

    static void verifyReceiptTime(RuntimeReceipt receipt, Instant intentAt, Instant acknowledgedAt) {
        long skew = ProviderUsageValidityExtensionStore.RECEIPT_CLOCK_SKEW_SECONDS;
        if (receipt.authorizedAt().isBefore(intentAt.minusSeconds(skew)) || receipt.authorizedAt().isAfter(acknowledgedAt.plusSeconds(skew)))
            throw conflict("PROVIDER_USAGE_TOP_UP_RECEIPT_TIME_CHANGED");
    }

    private static ProviderUsageAllowanceFailure changed() { return conflict("PROVIDER_USAGE_TOP_UP_RECORD_CHANGED"); }
}
