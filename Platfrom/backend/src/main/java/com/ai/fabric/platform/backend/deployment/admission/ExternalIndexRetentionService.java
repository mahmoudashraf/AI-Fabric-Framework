package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.retirement.ExternalIndexRetentionContracts;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementFailure;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementJson;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementRuntimeIndexGateway.IndexReadback;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementRuntimeGateway;
import com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationTransitionContracts;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static com.ai.fabric.platform.backend.deployment.admission.AdmissionRules.required;
import static com.ai.fabric.platform.backend.deployment.retirement.ExternalIndexRetentionContracts.*;
import static com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.*;
import static org.springframework.http.HttpStatus.*;

/** Staff-only preservation review plus append-only proof. This component has no provider mutation boundary. */
@Service
public class ExternalIndexRetentionService {
    private final AdmissionStore admissions;
    private final JdbcTemplate jdbc;
    private final AdmissionJson json;
    private final TargetRetirementJson retirementJson;
    private final PlatformAuditService audit;
    private final TargetRetirementRuntimeGateway runtime;
    private final TransactionTemplate transactions;

    public ExternalIndexRetentionService(AdmissionStore admissions, JdbcTemplate jdbc, AdmissionJson json,
                                         TargetRetirementJson retirementJson, PlatformAuditService audit,
                                         PlatformTransactionManager manager, TargetRetirementRuntimeGateway runtime) {
        this.admissions = admissions; this.jdbc = jdbc; this.json = json;
        this.retirementJson = retirementJson; this.audit = audit;
        this.runtime = runtime;
        transactions = new TransactionTemplate(manager);
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public Authorization approve(Policy policy) {
        var principal = PlatformSecurityContext.currentPrincipal();
        if (principal == null || principal.role() != PlatformRole.PLATFORM_ADMIN)
            throw new AdmissionFailure(FORBIDDEN, "INDEX_RETENTION_STAFF_REVIEW_REQUIRED");
        if (policy == null || policy.binding() == null) throw new AdmissionFailure(BAD_REQUEST, "INDEX_RETENTION_POLICY_INVALID");
        required(policy.reservationId(), 64); required(policy.generation(), 64);
        required(policy.manifestFingerprint(), 64); required(policy.bindingFingerprint(), 128);
        required(policy.policyReference(), 255); required(policy.payerReference(), 255);
        required(policy.binding().bindingId(), 64); required(policy.binding().metadataFingerprint(), 64);
        required(policy.binding().clusterId(), 255); required(policy.binding().endpointHost(), 253);
        required(policy.binding().databaseName(), 128); required(policy.binding().provider(), 64);
        required(policy.binding().scopeType(), 64); required(policy.binding().scopeValue(), 255);
        if (policy.indexes() < 1 || policy.indexBytes() < 1 || policy.reviewAcceptBefore() == null)
            throw new AdmissionFailure(BAD_REQUEST, "INDEX_RETENTION_ENVELOPE_REQUIRED");
        var r = admissions.require(policy.reservationId());
        admissions.approveIndexRetention(policy, principal.actorId(), Instant.now(), () -> {
            validateBinding(r, policy);
            requireUnstartedPlanIfPresent(r);
        });
        return admissions.indexRetentionAuthorization(r.id());
    }

    private void requireUnstartedPlanIfPresent(Reservation reservation) {
        var permitted = jdbc.query("""
            SELECT * FROM platform_target_retirement_operations
             WHERE deployment_id=? AND target_profile_id=? AND operation_key=? FOR UPDATE NOWAIT
            """, (rs, row) -> {
            var request = retirementJson.read(rs.getString("request_json"), PlanRequest.class);
            var snapshot = retirementJson.read(rs.getString("snapshot_json"), TargetRetirementContracts.Snapshot.class);
            var progress = retirementJson.read(rs.getString("result_json"), Progress.class);
            return "PLANNED".equals(rs.getString("state")) && "DRAIN".equals(rs.getString("step"))
                && rs.getInt("attempt") == 0 && rs.getTimestamp("started_at") == null
                && rs.getString("worker_token") == null && rs.getString("in_flight_effect") == null
                && rs.getLong("fence_epoch") == 0 && progress != null && progress.effects().isEmpty()
                && "system".equals(rs.getString("requested_by_actor_id")) && "SYSTEM".equals(rs.getString("requested_by_role"))
                && reservation.customerId().equals(rs.getString("customer_id"))
                && reservation.customerId().equals(snapshot.customerId()) && reservation.tenantId().equals(snapshot.tenantId())
                && reservation.deploymentId().equals(snapshot.deploymentId()) && reservation.targetProfileId().equals(snapshot.targetProfileId())
                && operationKey(reservation).equals(request.operationKey()) && request.retention() != null
                && request.retention().decision() == RetentionDecision.RECLAIM && request.retention().externalIndexRetention() == null
                && retirementJson.fingerprint(Map.of("request", request, "snapshot", snapshot)).equals(rs.getString("request_fingerprint"))
                && Integer.valueOf(1).equals(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM platform_target_retirement_gates
                     WHERE deployment_id=? AND target_profile_id=? AND state='AVAILABLE' AND operation_id IS NULL AND epoch=?
                    """, Integer.class, reservation.deploymentId(), reservation.targetProfileId(), snapshot.targetEpoch()));
        }, reservation.deploymentId(), reservation.targetProfileId(), operationKey(reservation));
        if (permitted.size() > 1 || !permitted.isEmpty() && !permitted.getFirst())
            throw new AdmissionFailure(CONFLICT, "INDEX_RETENTION_REVIEW_BEFORE_EFFECT_REQUIRED");
    }

    public Authorization reviewedAuthorization(Context context) {
        Authorization authorization = context.retention().externalIndexRetention();
        if (authorization != null) return authorization;
        if (!"system".equals(context.requestedByActorId()) || !"SYSTEM".equals(context.requestedByRole())) return null;
        String[] parts = context.operationKey().split(":", -1);
        if (parts.length != 3 || !"admission-expiry".equals(parts[0])) return null;
        Reservation reservation = admissions.require(parts[1]);
        if (!parts[2].equals(reservation.generation()) || !operationKey(reservation).equals(context.operationKey())) return null;
        // The explicit staff review is append-only; the unstarted plan itself remains immutable.
        return admissions.indexRetentionAuthorization(reservation.id());
    }

    public Policy requirePolicy(Context context) {
        Authorization authorization = reviewedAuthorization(context);
        if (authorization == null) throw failure("EXTERNAL_VECTOR_INDEX_RETENTION_REQUIRED");
        Policy policy = admissions.reviewed("INDEX_RETENTION", authorization.reservationId(), Policy.class);
        Reservation r = admissions.require(authorization.reservationId());
        if (!authorization.policyFingerprint().equals(json.fingerprint(policy))
            || !r.id().equals(policy.reservationId()) || !r.generation().equals(policy.generation())
            || !r.manifestFingerprint().equals(policy.manifestFingerprint())
            || !r.bindingFingerprint().equals(policy.bindingFingerprint())
            || !r.customerId().equals(context.snapshot().customerId()) || !r.tenantId().equals(context.snapshot().tenantId())
            || !r.deploymentId().equals(context.snapshot().deploymentId()) || !r.targetProfileId().equals(context.snapshot().targetProfileId())
            || !operationKey(r).equals(context.operationKey()) || r.retentionEnvelope() == null
            || r.retentionEnvelope().amounts().get(Dimension.INDEXES) != policy.indexes()
            || r.retentionEnvelope().amounts().get(Dimension.INDEX_BYTES) != policy.indexBytes()
            || context.retention().decision() != RetentionDecision.RECLAIM)
            throw failure("INDEX_RETENTION_POLICY_SCOPE_CHANGED");
        validateBinding(r, policy);
        validateOperation(context);
        var vectorResources = context.snapshot().resources().stream()
            .filter(resource -> "EXTERNAL_VECTOR_STORAGE_BINDING".equals(resource.resourceKind())).toList();
        if (vectorResources.size() != 1 || !vectorResources.getFirst().preserved()
            || !vectorResources.getFirst().handleId().equals(policy.binding().bindingId())
            || !vectorResources.getFirst().providerResourceUuid().equals(policy.binding().clusterId()))
            throw failure("INDEX_RETENTION_INVENTORY_CHANGED");
        if (context.snapshot().resources().stream().anyMatch(resource -> resource.volumes().stream().anyMatch(ProviderVolumeGeneration::preserved)
            || resource.preserved() && !java.util.Set.of("EXTERNAL_VECTOR_STORAGE_BINDING", "EXTERNAL_DOCUMENT_STORAGE_BINDING").contains(resource.resourceKind())))
            throw failure("INDEX_RETENTION_OTHER_STORAGE_UNPROVEN");
        return policy;
    }

    public Receipt receipt(Context context) {
        Policy policy = requirePolicy(context);
        var rows = jdbc.query("SELECT * FROM deployment_external_index_retention_receipts WHERE operation_id=?", (rs, row) -> {
            Receipt receipt = json.read(rs.getString("payload_json"), Receipt.class);
            if (!rs.getString("id").equals(receipt.id()) || !rs.getString("request_fingerprint").equals(receipt.requestFingerprint())
                || rs.getLong("fence_epoch") != receipt.fenceEpoch() || !rs.getString("reservation_id").equals(receipt.reservationId())
                || !rs.getString("generation").equals(receipt.generation())
                || !rs.getString("policy_fingerprint").equals(receipt.policyFingerprint())
                || !rs.getString("binding_id").equals(receipt.binding().bindingId())
                || !rs.getString("fingerprint").equals(json.fingerprint(receipt))
                || !rs.getTimestamp("recorded_at").toInstant().equals(receipt.recordedAt()))
                throw failure("INDEX_RETENTION_RECEIPT_INVALID");
            return receipt;
        }, context.operationId());
        if (rows.isEmpty()) return null;
        Receipt value = rows.getFirst();
        if (!value.operationId().equals(context.operationId()) || !value.operationKey().equals(context.operationKey())
            || !value.requestFingerprint().equals(context.requestFingerprint()) || value.fenceEpoch() != context.fenceEpoch()
            || !value.snapshotFingerprint().equals(retirementJson.fingerprint(context.snapshot()))
            || !value.reservationId().equals(policy.reservationId()) || !value.generation().equals(policy.generation())
            || !value.policyFingerprint().equals(json.fingerprint(policy)) || !value.binding().equals(policy.binding())
            || value.indexes() != policy.indexes() || value.indexBytes() != policy.indexBytes())
            throw failure("INDEX_RETENTION_RECEIPT_SCOPE_CHANGED");
        validateReadback(context, policy, value.indexReadback(), value.runtimeSnapshot(), false);
        return value;
    }

    public Receipt requireReceipt(Context context) {
        Receipt receipt = receipt(context);
        if (receipt == null) throw failure("INDEX_RETENTION_RECEIPT_REQUIRED");
        return receipt;
    }

    public Receipt record(Context context, IndexReadback proof) {
        Receipt replay = receipt(context);
        if (replay != null) return compatibleReplay(replay, proof);
        Policy policy = requirePolicy(context);
        var runtimeSnapshot = runtime.snapshot(context);
        validateReadback(context, policy, proof, runtimeSnapshot, true);
        return transactions.execute(status -> {
            jdbc.queryForObject("SELECT id FROM platform_target_retirement_gates WHERE deployment_id=? AND target_profile_id=? FOR UPDATE",
                String.class, context.snapshot().deploymentId(), context.snapshot().targetProfileId());
            jdbc.queryForObject("SELECT id FROM platform_target_retirement_operations WHERE id=? FOR UPDATE", String.class, context.operationId());
            Receipt existing = receipt(context);
            if (existing != null) return compatibleReplay(existing, proof);
            Integer fenced = jdbc.queryForObject("""
                SELECT COUNT(*) FROM platform_target_retirement_operations o JOIN platform_target_retirement_gates g
                  ON g.deployment_id=o.deployment_id AND g.target_profile_id=o.target_profile_id
                 WHERE o.id=? AND o.state='RUNNING' AND o.step='INDEX' AND o.worker_token=?
                   AND o.lease_expires_at>? AND o.fence_epoch=? AND o.request_fingerprint=?
                   AND g.state='RETIRING' AND g.operation_id=o.id AND g.epoch=o.fence_epoch
                """, Integer.class, context.operationId(), context.workerToken(), Timestamp.from(Instant.now()),
                context.fenceEpoch(), context.requestFingerprint());
            if (fenced == null || fenced != 1) throw failure("INDEX_RETENTION_WORKER_FENCED");
            Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
            Receipt value = new Receipt("eir-" + UUID.randomUUID(), context.operationId(), context.operationKey(),
                context.requestFingerprint(), context.fenceEpoch(), retirementJson.fingerprint(context.snapshot()),
                policy.reservationId(), policy.generation(), json.fingerprint(policy), policy.binding(),
                policy.indexes(), policy.indexBytes(), proof, runtimeSnapshot, now);
            jdbc.update("""
                INSERT INTO deployment_external_index_retention_receipts
                  (id,operation_id,request_fingerprint,fence_epoch,reservation_id,binding_id,generation,policy_fingerprint,fingerprint,payload_json,recorded_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """, value.id(), value.operationId(), value.requestFingerprint(), value.fenceEpoch(), value.reservationId(),
                value.binding().bindingId(), value.generation(), value.policyFingerprint(), json.fingerprint(value), json.write(value), Timestamp.from(now));
            audit.record("EXTERNAL_INDEX_RETENTION_RECORDED", "TARGET_RETIREMENT", context.operationId(), Map.of(
                "receiptId", value.id(), "fingerprint", json.fingerprint(value), "deploymentId", context.snapshot().deploymentId(),
                "targetProfileId", context.snapshot().targetProfileId(), "reviewedIndexEnvelope", policy.indexes(),
                "reviewedByteEnvelope", policy.indexBytes(), "observedVectors", proof.totalVectors(), "providerMutation", false));
            return value;
        });
    }

    private Receipt compatibleReplay(Receipt receipt, IndexReadback proof) {
        if (!receipt.indexReadback().equals(proof)) throw failure("INDEX_RETENTION_RECEIPT_REPLAY_CHANGED");
        return receipt;
    }

    private void validateBinding(Reservation r, Policy policy) {
        Binding b = policy.binding();
        if (b == null || !"milvus".equals(b.provider()) || !"COLLECTION_PREFIX".equals(b.scopeType()))
            throw failure("INDEX_RETENTION_SCOPE_UNSUPPORTED");
        var rows = jdbc.query("SELECT id,provider_resource_uuid,status,metadata_json FROM deployment_provider_resource_handles WHERE deployment_id=? AND target_profile_id=? AND resource_kind='EXTERNAL_VECTOR_STORAGE_BINDING' ORDER BY id",
            (rs, row) -> Map.of("id", rs.getString(1), "cluster", rs.getString(2), "status", rs.getString(3), "metadata", rs.getString(4)),
            r.deploymentId(), r.targetProfileId());
        if (rows.size() != 1) throw failure("INDEX_RETENTION_INVENTORY_CHANGED");
        var row = rows.getFirst();
        JsonNode metadata = json.read(row.get("metadata"), JsonNode.class);
        if (!metadata.isObject()) throw failure("INDEX_RETENTION_BINDING_CHANGED");
        ObjectNode identity = ((ObjectNode) metadata).deepCopy(); identity.remove("fingerprint");
        Manifest manifest = admissions.reviewed("MANIFEST", r.manifestId(), Manifest.class);
        Integer owner = jdbc.queryForObject("SELECT COUNT(*) FROM platform_deployments WHERE id=? AND customer_id=? AND tenant_id=? AND archived_at IS NULL",
            Integer.class, r.deploymentId(), r.customerId(), r.tenantId());
        Integer published = jdbc.queryForObject("SELECT COUNT(*) FROM platform_deployment_versions WHERE id=? AND deployment_id=? AND status='PUBLISHED' AND config_hash=?",
            Integer.class, manifest.versionId(), r.deploymentId(), manifest.versionConfigHash());
        if (!Objects.equals(row.get("id"), b.bindingId()) || !Objects.equals(row.get("cluster"), b.clusterId())
            || owner == null || owner != 1 || published == null || published != 1
            || !"ACTIVE".equals(row.get("status")) || !json.fingerprint(identity).equals(b.metadataFingerprint())
            || !b.metadataFingerprint().equals(metadata.path("fingerprint").asText())
            || !r.customerId().equals(metadata.path("customerId").asText()) || !r.tenantId().equals(metadata.path("tenantId").asText())
            || !r.deploymentId().equals(metadata.path("deploymentId").asText()) || !r.targetProfileId().equals(metadata.path("targetProfileId").asText())
            || !manifest.versionId().equals(metadata.path("versionId").asText()) || !manifest.versionConfigHash().equals(metadata.path("configHash").asText())
            || !"RETAIN_EXTERNAL".equals(metadata.path("lifecycle").asText()) || !metadata.path("externalResourceOwned").isBoolean()
            || metadata.path("externalResourceOwned").booleanValue() || !b.clusterId().equals(metadata.path("clusterId").asText())
            || !Objects.equals(b.endpointHost(), metadata.path("endpointHost").asText())
            || !Objects.equals(b.databaseName(), metadata.path("databaseName").asText())
            || !Objects.equals(b.scopeValue(), metadata.path("collectionPrefix").asText())
            || b.scopeValue() == null || b.scopeValue().isBlank() || b.databaseName() == null || b.databaseName().isBlank())
            throw failure("INDEX_RETENTION_BINDING_CHANGED");
    }

    private void validateOperation(Context context) {
        var matched = jdbc.query("SELECT * FROM platform_target_retirement_operations WHERE id=?", (rs, row) -> {
            var request = retirementJson.read(rs.getString("request_json"), PlanRequest.class);
            var snapshot = retirementJson.read(rs.getString("snapshot_json"), TargetRetirementContracts.Snapshot.class);
            return context.operationKey().equals(rs.getString("operation_key"))
                && context.snapshot().customerId().equals(rs.getString("customer_id"))
                && context.snapshot().deploymentId().equals(rs.getString("deployment_id"))
                && context.snapshot().targetProfileId().equals(rs.getString("target_profile_id"))
                && context.requestFingerprint().equals(rs.getString("request_fingerprint"))
                && context.fenceEpoch() == rs.getLong("fence_epoch")
                && context.retention().equals(request.retention())
                && context.operationKey().equals(request.operationKey())
                && retirementJson.fingerprint(context.snapshot()).equals(retirementJson.fingerprint(snapshot))
                && context.requestFingerprint().equals(retirementJson.fingerprint(Map.of("request", request, "snapshot", snapshot)));
        }, context.operationId());
        if (matched.size() != 1 || !matched.getFirst()) throw failure("INDEX_RETENTION_OPERATION_CHANGED");
    }

    private void validateReadback(Context context, Policy policy, IndexReadback proof,
                                  RuntimeGenerationTransitionContracts.Snapshot snapshot, boolean fresh) {
        Binding b = policy.binding();
        if (snapshot == null || snapshot.target() == null || snapshot.target().target() == null
            || !context.operationId().equals(snapshot.target().operationId())
            || !context.requestFingerprint().equals(snapshot.target().requestFingerprint())
            || !context.snapshot().tenantId().equals(snapshot.target().tenantId())
            || snapshot.observedAt() == null || snapshot.observedAt().isAfter(Instant.now())
            || fresh && snapshot.observedAt().isBefore(Instant.now().minusSeconds(60))
            || !snapshot.target().authorityRevoked() || !snapshot.inventoryComplete() || !snapshot.workerFencingComplete()
            || !snapshot.localDrainComplete() || snapshot.inFlightRequests() != 0 || snapshot.remoteObligations() == null
            || !snapshot.remoteObligations().isEmpty() || snapshot.blockers() == null || !snapshot.blockers().isEmpty()
            || !zero(snapshot.openConversations()) || !zero(snapshot.pendingConfirmations())
            || !zero(snapshot.humanWork()) || !zero(snapshot.backgroundWork())
            || proof == null || proof.target() == null || !proof.target().equals(snapshot.target().target())
            || proof.fenceEpoch() != snapshot.target().fenceEpoch()
            || !Objects.equals(proof.topologyFingerprint(), snapshot.topologyFingerprint())
            || !proof.target().deploymentId().equals(context.snapshot().deploymentId())
            || !proof.target().targetProfileId().equals(context.snapshot().targetProfileId())
            || context.snapshot().releases().stream().noneMatch(release -> release.releaseId().equals(proof.target().releaseId()))
            || !context.snapshot().tenantId().equals(proof.tenantId()) || !context.operationId().equals(proof.operationId())
            || !b.provider().equals(proof.provider()) || !b.scopeType().equals(proof.scopeType()) || !b.scopeValue().equals(proof.scopeValue())
            || proof.removedVectors() != 0 || proof.totalVectors() < 0 || proof.countsByEntityType() == null
            || proof.evidenceReference() == null || proof.evidenceReference().isBlank()
            || proof.topologyFingerprint() == null || proof.topologyFingerprint().isBlank()
            || proof.observedAt() == null || proof.observedAt().isAfter(Instant.now())
            || fresh && proof.observedAt().isBefore(Instant.now().minusSeconds(60)))
            throw failure("INDEX_RETENTION_READBACK_INVALID");
        long count = 0;
        try {
            for (var entry : proof.countsByEntityType().entrySet()) {
                if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null || entry.getValue() < 0)
                    throw failure("INDEX_RETENTION_READBACK_INVALID");
                count = Math.addExact(count, entry.getValue());
            }
        } catch (ArithmeticException ex) { throw failure("INDEX_RETENTION_READBACK_INVALID"); }
        if (count != proof.totalVectors() || proof.complete() && count != 0) throw failure("INDEX_RETENTION_READBACK_INVALID");
    }

    private boolean zero(RuntimeGenerationTransitionContracts.Metric metric) {
        return metric != null && metric.complete() && metric.count() != null && metric.count() == 0;
    }

    private String operationKey(Reservation r) { return "admission-expiry:" + r.id() + ":" + r.generation(); }
    private TargetRetirementFailure failure(String code) { return new TargetRetirementFailure(code); }
}
