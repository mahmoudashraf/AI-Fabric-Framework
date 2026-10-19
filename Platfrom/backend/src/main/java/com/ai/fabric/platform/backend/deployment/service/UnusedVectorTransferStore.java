package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.model.UnusedVectorTransferContracts.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import static org.springframework.http.HttpStatus.CONFLICT;

/** Immutable source release, trusted staff enrollment, and single-consumption destination journals. */
@Component
public class UnusedVectorTransferStore {
    private final JdbcTemplate jdbc;
    private final UnusedVectorTransferJson json;
    public UnusedVectorTransferStore(JdbcTemplate jdbc, UnusedVectorTransferJson json) { this.jdbc = jdbc; this.json = json; }

    public Optional<ReleaseReceipt> releaseByOperation(String key) { return release("operation_key", key); }
    public Optional<ReleaseReceipt> releaseById(String id) { return release("id", id); }
    private Optional<ReleaseReceipt> release(String field, String key) {
        return jdbc.query("SELECT * FROM deployment_vector_transfer_releases WHERE " + field + "=?", (rs, row) -> {
            ReleaseReceipt r = json.read(rs.getString("payload_json"), ReleaseReceipt.class);
            common(rs, r.id(), r.fingerprint(), r.actorId(), r.releasedAt(), r);
            if (!rs.getString("cluster_id").equals(r.vector().clusterId()) || !rs.getString("operation_key").equals(r.operationKey())
                || !rs.getString("command_fingerprint").equals(r.commandFingerprint())
                || !rs.getString("source_cp_id").equals(r.source().controlPlane().id())
                || !rs.getString("destination_cp_id").equals(r.destination().controlPlane().id())
                || r.sources().stream().noneMatch(b -> b.bindingId().equals(rsString(rs, "binding_id")) && b.scope().equals(r.source()))) throw changed();
            return r;
        }, key).stream().findFirst();
    }
    public String releaseJson(String id) {
        releaseById(id).orElseThrow(this::changed);
        return jdbc.queryForObject("SELECT payload_json FROM deployment_vector_transfer_releases WHERE id=?", String.class, id);
    }
    public Optional<ReviewReceipt> reviewByOperation(String key) { return review("operation_key", key, false); }
    public Optional<ReviewReceipt> reviewById(String id, boolean lock) { return review("id", id, lock); }
    private Optional<ReviewReceipt> review(String field, String key, boolean lock) {
        return jdbc.query("SELECT * FROM deployment_vector_transfer_reviews WHERE " + field + "=?" + (lock ? " FOR UPDATE" : ""), (rs, row) -> {
            ReviewReceipt r = json.read(rs.getString("payload_json"), ReviewReceipt.class);
            common(rs, r.id(), r.fingerprint(), r.reviewerActorId(), r.reviewedAt(), r);
            ReleaseReceipt source = json.read(r.sourceReceiptJson(), ReleaseReceipt.class);
            ReleaseReadback readback = json.read(r.authenticatedReadbackJson(), ReleaseReadback.class);
            if (!rs.getString("operation_key").equals(r.operationKey())
                || !rs.getString("source_cp_id").equals(source.source().controlPlane().id())
                || !rs.getString("source_receipt_id").equals(source.id())
                || !rs.getString("source_fingerprint").equals(source.fingerprint())
                || !rs.getString("readback_fingerprint").equals(readback.fingerprint())
                || !r.authenticatedResponseDigest().equals(json.bytesHash(r.authenticatedReadbackJson()))
                || !r.sourceReceiptFingerprint().equals(source.fingerprint())
                || !r.sourceReceiptJson().equals(readback.releaseJson())) throw changed();
            return r;
        }, key).stream().findFirst();
    }
    public Optional<ClaimReceipt> claimByOperation(String key) { return claim("operation_key", key); }
    public Optional<ClaimReceipt> claimByReview(String id) { return claim("review_id", id); }
    public Optional<ClaimReceipt> claimById(String id) { return claim("id", id); }
    private Optional<ClaimReceipt> claim(String field, String key) {
        return jdbc.query("SELECT * FROM deployment_vector_transfer_claims WHERE " + field + "=?", (rs, row) -> {
            ClaimReceipt r = json.read(rs.getString("payload_json"), ClaimReceipt.class);
            common(rs, r.id(), r.fingerprint(), r.actorId(), r.claimedAt(), r);
            if (!rs.getString("operation_key").equals(r.operationKey()) || !rs.getString("review_id").equals(r.reviewId())
                || !rs.getString("cluster_id").equals(r.vector().clusterId())
                || !rs.getString("binding_id").equals(r.binding().bindingId())
                || !rs.getString("command_fingerprint").equals(r.commandFingerprint())) throw changed();
            return r;
        }, key).stream().findFirst();
    }
    public void appendRelease(ReleaseReceipt r, String primaryBinding) {
        jdbc.update("INSERT INTO deployment_vector_transfer_releases(id,cluster_id,binding_id,operation_key,command_fingerprint,source_cp_id,destination_cp_id,fingerprint,payload_json,actor_id,recorded_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
            r.id(), r.vector().clusterId(), primaryBinding, r.operationKey(), r.commandFingerprint(), r.source().controlPlane().id(),
            r.destination().controlPlane().id(), r.fingerprint(), json.write(r), r.actorId(), Timestamp.from(r.releasedAt()));
        for (ReleasedBinding b : r.sources()) jdbc.update("INSERT INTO deployment_vector_transfer_release_bindings(binding_id,release_id,metadata_fingerprint,identity_fingerprint) VALUES (?,?,?,?)",
            b.bindingId(), r.id(), b.metadataFingerprint(), b.identityFingerprint());
    }
    public void appendReview(ReviewReceipt r, ReleaseReceipt source, ReleaseReadback readback) {
        jdbc.update("INSERT INTO deployment_vector_transfer_reviews(id,operation_key,source_cp_id,source_receipt_id,source_fingerprint,readback_fingerprint,fingerprint,payload_json,actor_id,recorded_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
            r.id(), r.operationKey(), source.source().controlPlane().id(), source.id(), source.fingerprint(), readback.fingerprint(),
            r.fingerprint(), json.write(r), r.reviewerActorId(), Timestamp.from(r.reviewedAt()));
    }
    public void appendClaim(ClaimReceipt r) {
        jdbc.update("INSERT INTO deployment_vector_transfer_claims(id,operation_key,review_id,cluster_id,binding_id,command_fingerprint,fingerprint,payload_json,actor_id,recorded_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
            r.id(), r.operationKey(), r.reviewId(), r.vector().clusterId(), r.binding().bindingId(), r.commandFingerprint(), r.fingerprint(),
            json.write(r), r.actorId(), Timestamp.from(r.claimedAt()));
    }
    private void common(ResultSet rs, String id, String fingerprint, String actor, Instant time, Object value) throws java.sql.SQLException {
        if (!rs.getString("id").equals(id) || !rs.getString("fingerprint").equals(fingerprint)
            || !fingerprint.equals(json.receiptHash(value)) || !rs.getString("actor_id").equals(actor)
            || !rs.getTimestamp("recorded_at").toInstant().equals(time)) throw changed();
    }
    private String rsString(ResultSet rs, String name) { try { return rs.getString(name); } catch (Exception ex) { throw changed(); } }
    private ResponseStatusException changed() { return new ResponseStatusException(CONFLICT, "UNUSED_VECTOR_TRANSFER_LEDGER_CHANGED"); }
}
