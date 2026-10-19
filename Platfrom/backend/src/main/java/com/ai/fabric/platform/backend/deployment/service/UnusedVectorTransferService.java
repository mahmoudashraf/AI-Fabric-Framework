package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentProviderResourceHandleEntity;
import com.ai.fabric.platform.backend.deployment.model.UnusedVectorTransferContracts.*;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentProviderResourceHandleRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

import static org.springframework.http.HttpStatus.*;

/** No network federation or provider mutation: enrollment explicitly trusts an authenticated staff coordinator. */
@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class UnusedVectorTransferService {
    private final UnusedVectorTransferStore store;
    private final UnusedVectorTransferJson json;
    private final UnusedVectorTransferIdentity identity;
    private final UnusedVectorTransferEvidence evidence;
    private final ExternalVectorClusterLocks locks;
    private final ExternalVectorClusterReadback inventory;
    private final DeploymentExistingVectorBindingService bindings;
    private final DeploymentProviderResourceHandleRepository handles;
    private final DeploymentRepository deployments;
    private final DeploymentVersionRepository versions;
    private final DeploymentTargetProfileRepository targets;
    private final TenantScopedVectorHandleResolver scopes;
    private final PlatformSecretService secrets;
    private final PlatformAuditService audit;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public UnusedVectorTransferService(UnusedVectorTransferStore store, UnusedVectorTransferJson json,
            UnusedVectorTransferIdentity identity, UnusedVectorTransferEvidence evidence, ExternalVectorClusterLocks locks,
            ExternalVectorClusterReadback inventory, DeploymentExistingVectorBindingService bindings,
            DeploymentProviderResourceHandleRepository handles, DeploymentRepository deployments,
            DeploymentVersionRepository versions, DeploymentTargetProfileRepository targets, TenantScopedVectorHandleResolver scopes,
            PlatformSecretService secrets, PlatformAuditService audit, JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.store = store; this.json = json; this.identity = identity; this.evidence = evidence; this.locks = locks;
        this.inventory = inventory; this.bindings = bindings; this.handles = handles; this.deployments = deployments;
        this.versions = versions; this.targets = targets; this.scopes = scopes; this.secrets = secrets; this.audit = audit; this.jdbc = jdbc;
        tx = new TransactionTemplate(manager); tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public ReleaseReceipt release(ReleaseRequest command) {
        PlatformPrincipal actor = admin(); identity.local();
        if (command == null || command.sources() == null || command.sources().isEmpty() || command.sources().size() > 32) throw invalid();
        text(command.operationKey(), 128); text(command.approvalReference(), 255); text(command.bindingId(), 64);
        scope(command.destination()); identity.requirePeer(command.destination().controlPlane()); inventoryApproval(command.inventoryApproval());
        for (Source source : command.sources()) { if (source == null) throw invalid(); text(source.bindingId(), 64); hash(source.expectedBindingFingerprint()); if (source.cancellationReceiptId() != null) text(source.cancellationReceiptId(), 64); }
        var sources = command.sources().stream().sorted(Comparator.comparing(Source::bindingId)).toList();
        if (sources.stream().map(Source::bindingId).distinct().count() != sources.size()) throw invalid();
        for (Source source : sources) { text(source.bindingId(), 64); hash(source.expectedBindingFingerprint()); if (source.cancellationReceiptId() != null) text(source.cancellationReceiptId(), 64); }
        var normalized = new ReleaseRequest(command.operationKey(), command.approvalReference(), command.bindingId(), command.destination(), sources, command.inventoryApproval());
        String commandFingerprint = json.hash(normalized);
        var prior = store.releaseByOperation(command.operationKey());
        if (prior.isPresent()) return tx.execute(status -> {
            ReleaseReceipt r = prior.get(); lockSource(r.vector().clusterId());
            if (!r.commandFingerprint().equals(commandFingerprint)) throw changed();
            requireClosed(r); return r;
        });
        var primary = handles.findById(command.bindingId()).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        VectorIdentity vector = evidence.vector(primary); Scope sourceScope = evidence.scope(primary, identity.local());
        bindings.verifyTransferEmpty(primary.getId(), evidence.metadata(primary).path("fingerprint").asText());
        ClusterReadback empty = inventory.requireEmpty(vector, command.inventoryApproval());
        return tx.execute(status -> {
            List<DeploymentProviderResourceHandleEntity> all = lockSource(vector.clusterId());
            var replay = store.releaseByOperation(command.operationKey());
            if (replay.isPresent()) {
                if (!replay.get().commandFingerprint().equals(commandFingerprint)) throw changed();
                requireClosed(replay.get()); return replay.get();
            }
            fresh(empty.observedAt());
            if (!all.stream().map(DeploymentProviderResourceHandleEntity::getId).toList().equals(sources.stream().map(Source::bindingId).toList())) throw changed();
            var records = new ArrayList<ReleasedBinding>();
            var clearances = new ArrayList<CredentialClearance>();
            for (int i = 0; i < all.size(); i++) {
                var h = all.get(i);
                var record = evidence.verify(h, sources.get(i), command.destination(), identity.local());
                if (!sourceScope.customerId().equals(record.scope().customerId()) || !sourceScope.targetProfileId().equals(record.scope().targetProfileId())
                    || !vector.equals(evidence.vector(h))) throw changed();
                records.add(record);
            }
            if (records.stream().noneMatch(r -> r.bindingId().equals(command.bindingId()) && r.scope().equals(sourceScope))) throw changed();
            for (var h : all) clearances.add(clearScopedCopies(h));
            Instant now = now();
            ReleaseReceipt receipt = json.seal(new ReleaseReceipt(id("uvr"), command.operationKey(), commandFingerprint,
                sourceScope, command.destination(), vector, records, clearances, empty, command.approvalReference(), actor.actorId(), now, null), ReleaseReceipt.class);
            for (var h : all) {
                int changed = jdbc.update("UPDATE deployment_provider_resource_handles SET status=?,updated_at=? WHERE id=? AND status IN ('ACTIVE','RETAINED_UNUSED') AND release_id IS NULL",
                    ExternalVectorClusterLocks.RELEASED, java.sql.Timestamp.from(now), h.getId());
                if (changed != 1) throw changed();
            }
            store.appendRelease(receipt, command.bindingId());
            audit.record("UNUSED_VECTOR_SOURCE_RELEASED", "EXTERNAL_VECTOR_TRANSFER", receipt.id(), Map.of(
                "clusterId", vector.clusterId(), "fingerprint", receipt.fingerprint(), "destinationControlPlane", command.destination().controlPlane().id(),
                "providerMutation", false, "providerCredentialRevoked", false));
            return receipt;
        });
    }

    public ReleaseReadback releaseReadback(String id) {
        PlatformPrincipal actor = admin(); text(id, 64);
        return tx.execute(status -> {
            ReleaseReceipt release = store.releaseById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
            identity.requireLocal(release.source().controlPlane()); lockSource(release.vector().clusterId()); requireClosed(release);
            String raw = store.releaseJson(id);
            return json.seal(new ReleaseReadback(identity.local(), raw, json.bytesHash(raw), actor.actorId(), actor.authenticationMode(), now(), null), ReleaseReadback.class);
        });
    }

    /** The reviewer asserts they authenticated to the configured source CP and independently checked this response. */
    public ReviewReceipt registerReviewedSourceProof(ReviewRequest command) {
        PlatformPrincipal actor = admin();
        if (command == null) throw invalid();
        text(command.operationKey(), 128); text(command.approvalReference(), 255); text(command.retrievalReference(), 255); hash(command.expectedSourceReceiptFingerprint());
        ReleaseReadback readback = json.read(command.authenticatedReadbackJson(), ReleaseReadback.class);
        ReleaseReceipt source = validateReadback(readback);
        if (!source.fingerprint().equals(command.expectedSourceReceiptFingerprint())) throw changed();
        return tx.execute(status -> {
            lockDestination(source.destination(), source.vector().clusterId()); requireDestination(source.destination(), source.vector());
            var prior = store.reviewByOperation(command.operationKey());
            if (prior.isPresent()) {
                ReviewReceipt r = prior.get();
                if (!r.approvalReference().equals(command.approvalReference()) || !r.authenticatedReadbackJson().equals(command.authenticatedReadbackJson())
                    || !r.retrievalReference().equals(command.retrievalReference()) || !r.sourceReceiptFingerprint().equals(command.expectedSourceReceiptFingerprint())) throw changed();
                return r;
            }
            fresh(readback.observedAt());
            ReviewReceipt receipt = json.seal(new ReviewReceipt(id("uvp"), command.operationKey(), command.approvalReference(),
                command.authenticatedReadbackJson(), json.bytesHash(command.authenticatedReadbackJson()), readback.releaseJson(), source.fingerprint(),
                command.retrievalReference(), actor.actorId(), now(), null), ReviewReceipt.class);
            store.appendReview(receipt, source, readback);
            audit.record("UNUSED_VECTOR_SOURCE_PROOF_REVIEWED", "EXTERNAL_VECTOR_TRANSFER", receipt.id(), Map.of(
                "sourceControlPlane", source.source().controlPlane().id(), "sourceReceiptId", source.id(), "sourceFingerprint", source.fingerprint(),
                "authenticatedResponseDigest", receipt.authenticatedResponseDigest(), "retrievalReference", receipt.retrievalReference()));
            return receipt;
        });
    }

    public ClaimReceipt claim(ClaimRequest command) {
        PlatformPrincipal actor = admin();
        if (command == null || command.connection() == null) throw invalid();
        text(command.operationKey(), 128); text(command.reviewId(), 64); inventoryApproval(command.inventoryApproval());
        ReviewReceipt observed = store.reviewById(command.reviewId(), false).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        ReleaseReceipt source = validateReadback(json.read(observed.authenticatedReadbackJson(), ReleaseReadback.class));
        return tx.execute(status -> {
            lockDestination(source.destination(), source.vector().clusterId()); requireDestination(source.destination(), source.vector());
            ReviewReceipt review = store.reviewById(command.reviewId(), true).orElseThrow(this::changed);
            if (!review.equals(observed)) throw changed();
            var prior = store.claimByReview(review.id());
            var occupiedKey = store.claimByOperation(command.operationKey());
            if (occupiedKey.isPresent() && !occupiedKey.get().reviewId().equals(review.id())) throw changed();
            var c = command.connection();
            if (!source.destination().versionId().equals(c.versionId()) || !source.vector().clusterId().equals(c.clusterId())
                || !source.vector().projectId().equals(c.projectId()) || !source.vector().regionId().equals(c.regionId())
                || !source.vector().deploymentOption().equals(c.expectedDeploymentOption()) || !source.vector().databaseName().equals(c.databaseName())
                || !review.approvalReference().equals(c.approvalReference())) throw changed();
            ClusterReadback empty;
            if (prior.isPresent()) empty = prior.get().emptyClusterReadback();
            else {
                if (handles.findFirstByDeploymentIdAndTargetProfileIdAndResourceKindOrderByUpdatedAtDesc(source.destination().deploymentId(), source.destination().targetProfileId(), DeploymentExistingVectorBindingService.RESOURCE_KIND).isPresent()) throw changed();
                empty = inventory.requireEmpty(source.vector(), command.inventoryApproval()); fresh(empty.observedAt());
            }
            var binding = bindings.bindTransferClaim(source.destination().deploymentId(), source.destination().targetProfileId(), c, prior.isPresent());
            String bindingRequestFingerprint = jdbc.queryForObject("SELECT metadata_json::jsonb ->> 'requestFingerprint' FROM deployment_provider_resource_handles WHERE id=?", String.class, binding.bindingId());
            String fingerprint = json.hash(Map.of("operationKey", command.operationKey(), "reviewId", command.reviewId(),
                "bindingRequestFingerprint", bindingRequestFingerprint, "inventoryApproval", command.inventoryApproval()));
            if (prior.isPresent()) {
                ClaimReceipt r = prior.get();
                if (!r.operationKey().equals(command.operationKey()) || !r.commandFingerprint().equals(fingerprint) || !r.binding().equals(binding)) throw changed();
                return r;
            }
            ClaimReceipt receipt = json.seal(new ClaimReceipt(id("uvc"), command.operationKey(), fingerprint, review.id(), source.id(),
                source.source().controlPlane(), source.destination(), source.vector(), empty, binding, actor.actorId(), now(), null), ClaimReceipt.class);
            store.appendClaim(receipt);
            audit.record("UNUSED_VECTOR_DESTINATION_CLAIMED", "EXTERNAL_VECTOR_TRANSFER", receipt.id(), Map.of(
                "reviewId", review.id(), "sourceReceiptId", source.id(), "bindingId", binding.bindingId(), "fingerprint", receipt.fingerprint(), "providerMutation", false));
            return receipt;
        });
    }
    public ReviewReceipt review(String id) {
        admin(); var r = store.reviewById(text(id, 64), false).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        validateReadback(json.read(r.authenticatedReadbackJson(), ReleaseReadback.class)); return r;
    }
    public ClaimReceipt claimReceipt(String id) {
        admin(); var r = store.claimById(text(id, 64)).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        identity.requireLocal(r.destination().controlPlane()); identity.requirePeer(r.sourceControlPlane()); return r;
    }

    private List<DeploymentProviderResourceHandleEntity> lockSource(String clusterId) {
        // Admission reservation/cancellation holds this mutex before deployment locks. Match that order.
        jdbc.queryForObject("SELECT id FROM deployment_admission_mutex WHERE id=1 FOR UPDATE", Integer.class);
        return lockCluster(null, clusterId, false);
    }
    private List<DeploymentProviderResourceHandleEntity> lockDestination(Scope destination, String clusterId) {
        return lockCluster(destination, clusterId, true);
    }
    private List<DeploymentProviderResourceHandleEntity> lockCluster(Scope destination, String clusterId, boolean requireUsable) {
        var ids = new TreeSet<String>(); if (destination != null) ids.add(destination.deploymentId());
        handles.findByProviderResourceUuidOrderByIdAsc(clusterId).forEach(h -> ids.add(h.getDeploymentId()));
        for (String id : ids) deployments.findByIdForUpdate(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        locks.lock(clusterId); if (requireUsable) locks.requireNotReleased(clusterId);
        var all = handles.lockClusterBindings(clusterId);
        if (all.stream().anyMatch(h -> !ids.contains(h.getDeploymentId()))) throw changed();
        return all;
    }
    private void requireClosed(ReleaseReceipt release) {
        identity.requireLocal(release.source().controlPlane()); identity.requirePeer(release.destination().controlPlane());
        var all = handles.findByProviderResourceUuidOrderByIdAsc(release.vector().clusterId());
        if (!all.stream().map(DeploymentProviderResourceHandleEntity::getId).toList().equals(release.sources().stream().map(ReleasedBinding::bindingId).toList())) throw changed();
        for (int i = 0; i < all.size(); i++) evidence.verifyClosed(all.get(i), release.sources().get(i));
        for (var h : all) for (String name : scopedNames(h)) if (secrets.isSecretPresent(name)) throw changed();
    }
    private CredentialClearance clearScopedCopies(DeploymentProviderResourceHandleEntity h) {
        JsonNode m = evidence.metadata(h);
        for (String name : scopedNames(h)) secrets.clearManagedSecret(name, Map.of("source", "UNUSED_VECTOR_TRANSFER", "bindingId", h.getId(), "providerCredentialRevoked", false));
        return new CredentialClearance(h.getId(), m.path("authType").asText(), true, true);
    }
    private List<String> scopedNames(DeploymentProviderResourceHandleEntity h) {
        JsonNode m = evidence.metadata(h);
        String suffix = json.hash(Map.of("deploymentId", h.getDeploymentId(), "targetProfileId", h.getTargetProfileId())).toUpperCase(Locale.ROOT);
        var names = new ArrayList<String>();
        if ("TOKEN".equals(m.path("authType").asText())) {
            names.add("MANAGED_EXTERNAL_VECTOR_TOKEN_" + suffix);
            if (!names.getFirst().equals(m.path("tokenSecretRef").asText()) || m.has("usernameSecretRef") || m.has("passwordSecretRef")) throw changed();
        } else if ("USERNAME_PASSWORD".equals(m.path("authType").asText())) {
            names.add("MANAGED_EXTERNAL_VECTOR_USERNAME_" + suffix); names.add("MANAGED_EXTERNAL_VECTOR_PASSWORD_" + suffix);
            if (!names.get(0).equals(m.path("usernameSecretRef").asText()) || !names.get(1).equals(m.path("passwordSecretRef").asText()) || m.has("tokenSecretRef")) throw changed();
        } else throw changed();
        names.add("MANAGED_EXTERNAL_VECTOR_FINGERPRINT_" + suffix);
        if (!names.getLast().equals(m.path("credentialFingerprintSecretRef").asText())) throw changed();
        return names;
    }
    private ReleaseReceipt validateReadback(ReleaseReadback readback) {
        if (readback == null || !json.receiptHash(readback).equals(readback.fingerprint())
            || !json.bytesHash(readback.releaseJson()).equals(readback.releaseJsonDigest())
            || !List.of("SESSION", "API_KEY").contains(readback.authenticationMode())) throw changed();
        text(readback.readByActorId(), 255);
        ReleaseReceipt release = json.read(readback.releaseJson(), ReleaseReceipt.class);
        if (!json.receiptHash(release).equals(release.fingerprint()) || !release.source().controlPlane().equals(readback.sourceControlPlane())
            || release.sources().isEmpty() || release.sources().size() > 32 || release.credentialClearances().size() != release.sources().size()) throw changed();
        scope(release.source()); scope(release.destination()); identity.requirePeer(release.source().controlPlane()); identity.requireLocal(release.destination().controlPlane());
        if (release.releasedAt() == null || readback.observedAt() == null || release.releasedAt().isAfter(readback.observedAt())) throw changed();
        if (release.credentialClearances().stream().anyMatch(c -> !c.localServingCopyCleared() || !c.localFingerprintCopyCleared())) throw changed();
        ClusterReadback empty = release.emptyClusterReadback();
        if (empty == null || !json.receiptHash(empty).equals(empty.fingerprint()) || !Map.of("default", List.of()).equals(empty.collectionsByDatabase())
            || !release.vector().endpointHost().equals(empty.endpointHost())) throw changed();
        return release;
    }
    private void requireDestination(Scope destination, VectorIdentity vector) {
        identity.requireLocal(destination.controlPlane());
        var dep = deployments.findById(destination.deploymentId()).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        var version = versions.findById(destination.versionId()).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        var target = targets.findById(destination.targetProfileId()).filter(t -> t.isActive()).orElseThrow(this::changed);
        if (dep.getArchivedAt() != null || !destination.customerId().equals(dep.getCustomerId()) || !destination.tenantId().equals(dep.getTenantId())
            || !vector.providerType().equals(target.getProviderType().name())
            || !dep.getId().equals(version.getDeploymentId()) || !"PUBLISHED".equals(version.getStatus()) || !destination.versionConfigHash().equals(version.getConfigHash())
            || !destination.collectionPrefix().equals(scopes.targetScopedProviderConfig(dep, json.read(version.getProviderConfigJson(), JsonNode.class), target.getId()).path("milvusCollectionPrefix").asText())) throw changed();
    }
    private void scope(Scope scope) {
        if (scope == null || scope.controlPlane() == null) throw invalid();
        text(scope.customerId(), 64); text(scope.tenantId(), 64); text(scope.deploymentId(), 64); text(scope.targetProfileId(), 64);
        text(scope.versionId(), 64); hash(scope.versionConfigHash()); text(scope.collectionPrefix(), 255);
    }
    private void inventoryApproval(InventoryApproval approval) {
        if (approval == null) throw invalid(); hash(approval.expectedCredentialFingerprint());
        text(approval.fullVisibilityReviewReference(), 255); text(approval.noWriterWindowReference(), 255);
    }
    private PlatformPrincipal admin() {
        var actor = PlatformSecurityContext.currentPrincipal();
        if (actor == null) throw new ResponseStatusException(UNAUTHORIZED);
        if (actor.role() != PlatformRole.PLATFORM_ADMIN || !List.of("SESSION", "API_KEY").contains(actor.authenticationMode())) throw new ResponseStatusException(FORBIDDEN);
        text(actor.actorId(), 255); return actor;
    }
    private String text(String value, int limit) { if (value == null || value.isBlank() || value.length() > limit || !value.equals(value.trim()) || value.chars().anyMatch(Character::isISOControl)) throw invalid(); return value; }
    private void hash(String value) { if (value == null || !value.matches("[0-9a-f]{64}")) throw invalid(); }
    private void fresh(Instant value) { if (value == null || value.isAfter(Instant.now()) || value.isBefore(Instant.now().minusSeconds(60))) throw changed(); }
    private Instant now() { return Instant.now().truncatedTo(ChronoUnit.MICROS); }
    private String id(String prefix) { return prefix + "-" + UUID.randomUUID(); }
    private ResponseStatusException invalid() { return new ResponseStatusException(BAD_REQUEST, "UNUSED_VECTOR_TRANSFER_COMMAND_INVALID"); }
    private ResponseStatusException changed() { return new ResponseStatusException(CONFLICT, "UNUSED_VECTOR_TRANSFER_EVIDENCE_CHANGED"); }
}
