package com.ai.fabric.platform.backend.deployment.budget;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageAllowanceContracts.*;
import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageAllowanceFailure.*;
import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageValidityExtensionContracts.*;

/** All writes are append-only and serialized grant -> allocation; no runtime I/O belongs here. */
final class ProviderUsageValidityExtensionStore {
    // Receipt chronology only. Does not relax private assertion expiry or the runtime's 24-hour validity cap.
    static final long RECEIPT_CLOCK_SKEW_SECONDS = 30;
    private final JdbcTemplate jdbc;
    private final ProviderUsageAllowanceCodec codec;
    private final TransactionTemplate transaction;

    ProviderUsageValidityExtensionStore(JdbcTemplate jdbc, PlatformTransactionManager manager, ProviderUsageAllowanceCodec codec) {
        this.jdbc = jdbc; this.codec = codec; transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    private ProviderUsageValidityExtensionStore(JdbcTemplate jdbc, ProviderUsageAllowanceCodec codec) {
        this.jdbc = jdbc; this.codec = codec; this.transaction = null;
    }

    <T> T locked(SafeAllowanceProjection expected, Supplier<T> work) {
        return transaction.execute(status -> {
            jdbc.queryForObject("SELECT id FROM provider_usage_grant_mutex WHERE id=1 FOR UPDATE", Integer.class);
            var grants = jdbc.query("SELECT grant_id FROM provider_usage_allocations WHERE approval_id=?",
                (rs, i) -> rs.getString(1), expected.approvalId());
            if (grants.size() != 1) throw changed();
            var grant = jdbc.queryForObject("SELECT * FROM provider_usage_grants WHERE id=? FOR UPDATE", (rs, i) -> {
                var value = codec.read(rs.getString("payload_json"), ProviderUsageGrant.class);
                if (!value.id().equals(grants.getFirst()) || !codec.fingerprint(value).equals(rs.getString("fingerprint"))
                    || !value.customerId().equals(rs.getString("customer_id")) || !value.targetProfileId().equals(rs.getString("target_profile_id"))
                    || !value.environment().equals(rs.getString("environment_name")) || !value.campaignId().equals(rs.getString("campaign_id"))
                    || !"ACTIVE".equals(rs.getString("lifecycle_status"))) throw changed();
                return value;
            }, grants.getFirst());
            if (grant == null || jdbc.queryForObject("SELECT count(*) FROM provider_usage_grant_void_receipts WHERE grant_id=?",
                Long.class, grant.id()) != 0) throw changed();
            var actual = jdbc.queryForObject("SELECT * FROM provider_usage_allocations WHERE approval_id=? FOR UPDATE", (rs, i) -> {
                var terms = codec.read(rs.getString("payload_json"), AllocationTerms.class);
                if (!terms.approvalId().equals(expected.approvalId()) || !terms.scope().equals(expected.scope())
                    || !terms.grantId().equals(grants.getFirst()) || !codec.fingerprint(terms).equals(expected.approvalFingerprint())
                    || !terms.expiresAt().equals(grant.expiresAt()) || !terms.binding().equals(grant.binding())
                    || !terms.pricingReference().equals(grant.rateCardReference())
                    || !terms.scope().customerId().equals(grant.customerId()) || !terms.scope().targetProfileId().equals(grant.targetProfileId())
                    || !terms.scope().environment().equals(grant.environment())
                    || !codec.fingerprint(terms).equals(rs.getString("approval_fingerprint"))
                    || !ProviderUsageRuntimeAllocationIdentity.fingerprint(terms).equals(expected.runtimeAllocationFingerprint())
                    || !expected.runtimeAllocationFingerprint().equals(rs.getString("runtime_allocation_fingerprint"))
                    || !expected.storeUuid().toString().equals(rs.getString("store_uuid"))
                    || !expected.runtimeAllocationFingerprint().equals(rs.getString("initialization_fingerprint"))
                    || !"ACTIVE".equals(rs.getString("initialization_state"))
                    || !terms.scope().customerId().equals(rs.getString("customer_id"))
                    || !terms.scope().deploymentId().equals(rs.getString("deployment_id"))
                    || !terms.scope().targetProfileId().equals(rs.getString("target_profile_id"))
                    || !terms.scope().environment().equals(rs.getString("environment_name"))) throw changed();
                return terms;
            }, expected.approvalId());
            if (actual == null) throw changed();
            return work.get();
        });
    }

    Review read(String id) {
        var rows = jdbc.query("SELECT * FROM provider_usage_validity_reviews WHERE extension_id=?", (rs, i) -> {
            var review = codec.read(rs.getString("payload_json"), Review.class);
            var command = review.command();
            if (command == null || review.actorId() == null || review.actorId().isBlank() || review.reviewedAt() == null
                || !id.equals(command.review().extensionId()) || !id.equals(command.runtimeCommand().extensionId())
                || !command.review().scope().approvalId().equals(rs.getString("approval_id"))
                || !command.reviewFingerprint().equals(rs.getString("review_fingerprint"))
                || !command.reviewFingerprint().equals(commandFingerprint(codec, command))
                || !codec.fingerprint(review).equals(rs.getString("record_fingerprint"))) throw changed();
            return review;
        }, id);
        if (rows.size() > 1) throw changed();
        return rows.isEmpty() ? null : rows.getFirst();
    }

    void save(Review review) {
        var command = review.command();
        jdbc.update("INSERT INTO provider_usage_validity_reviews VALUES (?,?,?,?,?)", command.review().extensionId(),
            command.review().scope().approvalId(), command.reviewFingerprint(), codec.fingerprint(review), codec.write(review));
    }

    Event event(Review review, String kind) {
        var id = review.command().review().extensionId();
        var rows = jdbc.query("SELECT * FROM provider_usage_validity_events WHERE extension_id=? AND event_type=?", (rs, i) -> {
            var event = codec.read(rs.getString("payload_json"), Event.class);
            if (!id.equals(event.extensionId()) || !review.command().reviewFingerprint().equals(event.commandFingerprint())
                || event.actorId() == null || event.actorId().isBlank() || event.recordedAt() == null
                || !codec.fingerprint(event).equals(rs.getString("record_fingerprint"))
                || ("ACK".equals(kind) != (event.receipt() != null))) throw changed();
            if (event.receipt() != null) verifyReceipt(review.command(), event.receipt());
            return event;
        }, id, kind);
        if (rows.size() > 1) throw changed();
        return rows.isEmpty() ? null : rows.getFirst();
    }

    void append(Review review, String kind, String actor, Instant now, RuntimeReceipt receipt) {
        var event = new Event(review.command().review().extensionId(), review.command().reviewFingerprint(), actor, now, receipt);
        jdbc.update("INSERT INTO provider_usage_validity_events VALUES (?,?,?,?)", event.extensionId(), kind,
            codec.fingerprint(event), codec.write(event));
    }

    List<Review> reviews(String approvalId) {
        return jdbc.query("SELECT extension_id FROM provider_usage_validity_reviews WHERE approval_id=? ORDER BY extension_id",
            (rs, i) -> rs.getString(1), approvalId).stream().map(this::read).toList();
    }

    void noPending(String approvalId) {
        for (var review : reviews(approvalId)) {
            if (event(review, "INTENT") != null && event(review, "ACK") == null)
                throw conflict("PROVIDER_USAGE_EXTENSION_RECONCILIATION_REQUIRED");
        }
    }

    static String commandFingerprint(ProviderUsageAllowanceCodec codec, Command command) {
        return codec.fingerprint(List.of(command.review(), command.runtimeCommand(), command.runtimeBaseUrl(), command.authorityFingerprint(),
            command.capabilityManifestHash()));
    }

    static Instant effectiveExpiry(JdbcTemplate jdbc, ProviderUsageAllowanceCodec codec, SafeAllowanceProjection allocation) {
        var store = new ProviderUsageValidityExtensionStore(jdbc, codec);
        var acknowledged = new ArrayList<Review>();
        for (var review : store.reviews(allocation.approvalId())) {
            var command = review.command();
            var request = command.review();
            if (!request.scope().equals(allocation.scope()) || !request.expectedApprovalFingerprint().equals(allocation.approvalFingerprint())
                || !request.storeUuid().equals(allocation.storeUuid())
                || !command.runtimeCommand().runtimeFingerprint().equals(allocation.runtimeAllocationFingerprint())) throw changed();
            var intent = store.event(review, "INTENT"); var unknown = store.event(review, "UNKNOWN"); var ack = store.event(review, "ACK");
            if ((ack != null || unknown != null) && intent == null) throw changed();
            if (ack != null) {
                verifyScope(ack.receipt(), allocation);
                verifyReceiptTime(ack.receipt(), intent.recordedAt(), ack.recordedAt());
                acknowledged.add(review);
            }
        }
        acknowledged.sort(Comparator.comparing(r -> r.command().runtimeCommand().expiresAt()));
        Instant effective = allocation.expiresAt();
        for (var review : acknowledged) {
            var command = review.command().runtimeCommand();
            if (!command.expectedEffectiveExpiresAt().equals(effective) || !command.expiresAt().isAfter(effective)) throw changed();
            effective = command.expiresAt();
        }
        return effective;
    }

    static void verifyReceipt(Command command, RuntimeReceipt receipt) {
        var expected = command.runtimeCommand();
        if (receipt == null || receipt.schemaVersion() != 1 || !expected.extensionId().equals(receipt.extensionId())
            || !expected.approvalId().equals(receipt.approvalId()) || !expected.storeUuid().equals(receipt.storeUuid())
            || !expected.runtimeFingerprint().equals(receipt.runtimeFingerprint())
            || !expected.expectedEffectiveExpiresAt().equals(receipt.previousEffectiveExpiresAt())
            || !expected.expiresAt().equals(receipt.expiresAt()) || receipt.authorizedAt() == null
            || !ProviderUsageAllowanceCodec.sha256(expected.reason()).equals(receipt.reasonSha256())) throw changed();
    }

    static void verifyScope(RuntimeReceipt receipt, SafeAllowanceProjection allocation) {
        var scope = allocation.scope();
        var expected = new RuntimeScope(scope.customerId(), scope.deploymentId(), scope.targetProfileId(), scope.environment(),
            "sha256:" + allocation.credentialSha256(), allocation.pricingReference());
        if (!expected.equals(receipt.scope())) throw changed();
    }

    static void verifyReceiptTime(RuntimeReceipt receipt, Instant intentAt, Instant acknowledgementAt) {
        if (receipt.authorizedAt().isBefore(intentAt.minusSeconds(RECEIPT_CLOCK_SKEW_SECONDS))
            || receipt.authorizedAt().isAfter(acknowledgementAt.plusSeconds(RECEIPT_CLOCK_SKEW_SECONDS))
            || !receipt.expiresAt().isAfter(receipt.authorizedAt())
            || receipt.expiresAt().isAfter(receipt.authorizedAt().plusSeconds(86_400)))
            throw conflict("PROVIDER_USAGE_EXTENSION_RECEIPT_TIME_CHANGED");
    }

    private static ProviderUsageAllowanceFailure changed() { return conflict("PROVIDER_USAGE_EXTENSION_RECORD_CHANGED"); }
}
