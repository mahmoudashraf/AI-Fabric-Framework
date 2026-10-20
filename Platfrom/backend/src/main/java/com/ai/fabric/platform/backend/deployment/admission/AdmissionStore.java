package com.ai.fabric.platform.backend.deployment.admission;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import com.ai.fabric.platform.backend.deployment.retirement.ExternalIndexRetentionContracts;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static com.ai.fabric.platform.backend.deployment.admission.AdmissionRules.*;
import static org.springframework.http.HttpStatus.*;

/** All ledger writes serialize on a database row, across threads/processes and across target pools. */
@Repository
class AdmissionStore {
    private record InitialRenewalHead(long revision, String actor, String reference) { }
    record ReleasePrerequisiteAllocation(
        Reservation reservation,
        ReleaseBinding release,
        com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.AdmissionContext admittedContext
    ) { }
    private final JdbcTemplate jdbc;
    private final AdmissionJson json;
    private final TransactionTemplate transaction;
    private final Clock clock;

    @Autowired
    AdmissionStore(JdbcTemplate jdbc, AdmissionJson json, PlatformTransactionManager transactions) {
        this(jdbc, json, transactions, Clock.systemUTC());
    }
    AdmissionStore(JdbcTemplate jdbc, AdmissionJson json, PlatformTransactionManager transactions, Clock clock) {
        this.jdbc = jdbc; this.json = json;
        this.clock = clock;
        transaction = new TransactionTemplate(transactions);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }
    private <T> T locked(Supplier<T> action) {
        return transaction.execute(status -> {
            jdbc.queryForObject("SELECT id FROM deployment_admission_mutex WHERE id=1 FOR UPDATE", Integer.class);
            return action.get();
        });
    }
    void approvePolicy(FleetPolicy policy, String actor, Instant requestedAt) {
        approvePolicy(policy, actor, requestedAt, Map::of);
    }
    void approvePolicy(FleetPolicy policy, String actor, Instant requestedAt, Supplier<Map<String, String>> placementGuard) {
        try {
        locked(() -> {
            Instant now = effectiveTime(requestedAt);
            policy(policy, now);
            var charged = held();
            checkCapacity(policy, charged, null, null);
            var coverage = new java.util.HashSet<String>();
            var continuities = new ArrayList<AdmissionPolicyContinuity>();
            if (!policy.continuations().isEmpty()) {
                for (String targetId : policy.targets().keySet().stream().sorted().toList()) {
                    if (jdbc.query("SELECT id FROM deployment_target_profiles WHERE id=? FOR UPDATE NOWAIT",
                        (rs, row) -> rs.getString(1), targetId).size() != 1)
                        throw new AdmissionFailure(PRECONDITION_FAILED, "POLICY_CONTINUITY_PLACEMENT_REQUIRED");
                }
                var ids = policy.continuations().stream().flatMap(value -> value.reservations().stream())
                    .map(Fence::reservationId).distinct().sorted().toList();
                for (String reservationId : ids) {
                    if (jdbc.query("SELECT id FROM deployment_capacity_reservations WHERE id=? FOR UPDATE NOWAIT",
                        (rs, row) -> rs.getString(1), reservationId).size() != 1)
                        throw new AdmissionFailure(NOT_FOUND, "RESERVATION_NOT_FOUND");
                }
            }
            Map<String, String> placements = policy.continuations().isEmpty() ? Map.of() : placementGuard.get();
            if (!policy.continuations().isEmpty() && !placements.keySet().equals(policy.targets().keySet()))
                throw new AdmissionFailure(PRECONDITION_FAILED, "POLICY_CONTINUITY_PLACEMENT_REQUIRED");
            placements.values().forEach(value -> required(value, 255));
            policy(policy, effectiveTime(requestedAt));
            var existing = jdbc.queryForList("SELECT fingerprint FROM deployment_admission_reviews WHERE kind='POLICY' AND record_key=?", String.class, policy.id());
            if (!existing.isEmpty() && !policy.id().equals(activePolicy().id()))
                throw new AdmissionFailure(CONFLICT, "POLICY_CONTINUITY_HEAD_CHANGED");
            if (!existing.isEmpty() && !policy.continuations().isEmpty()) {
                if (!existing.equals(List.of(json.fingerprint(policy)))) throw new AdmissionFailure(CONFLICT, "REVIEWED_RECORD_IMMUTABLE");
                for (var request : policy.continuations()) for (Fence fence : request.reservations()) {
                    coverage.add(fence.reservationId());
                    effectivePolicyAuthority(require(fence.reservationId()), policy);
                }
                if (charged.stream().anyMatch(reservation -> !reservation.policyId().equals(policy.id()) && !coverage.contains(reservation.id())))
                    throw new AdmissionFailure(CONFLICT, "POLICY_CONTINUITY_COVERAGE_REQUIRED");
                jdbc.update("UPDATE deployment_admission_mutex SET active_policy_ref=? WHERE id=1", policy.id());
                return null;
            }
            if (!policy.continuations().isEmpty()) AdmissionPolicyContinuity.compatible(activePolicy(), policy);
            for (var request : policy.continuations()) {
                FleetPolicy original = verifiedReview("POLICY", required(request.originalPolicyId(), 64), FleetPolicy.class);
                if (!json.fingerprint(original).equals(required(request.originalPolicyFingerprint(), 64))
                    || request.reservations() == null || request.reservations().isEmpty())
                    throw new AdmissionFailure(CONFLICT, "POLICY_CONTINUITY_SCOPE_CHANGED");
                AdmissionPolicyContinuity.compatible(original, policy);
                for (Fence fence : request.reservations()) {
                    if (fence == null || fence.revision() < 0 || !coverage.add(required(fence.reservationId(), 64)))
                        throw new AdmissionFailure(BAD_REQUEST, "POLICY_CONTINUITY_COVERAGE_INVALID");
                    Reservation reservation = fenced(fence);
                    if (!reservation.consumesCapacity() || !original.id().equals(reservation.policyId()))
                        throw new AdmissionFailure(CONFLICT, "POLICY_CONTINUITY_SCOPE_CHANGED");
                    var allowed = new java.util.TreeSet<String>();
                    var prior = effectivePolicyAuthority(reservation, activePolicy());
                    allowed.add("policy:" + original.id());
                    if (prior != null) allowed.addAll(prior.authorizedPolicyReferences());
                    allowed.add("policy:" + policy.id());
                    var currentManifest = reviewed("MANIFEST", reservation.manifestId(), Manifest.class);
                    if (!allowed.containsAll(policyReferences(currentManifest)))
                        throw new AdmissionFailure(PRECONDITION_FAILED, "POLICY_CONTINUITY_MANIFEST_CHANGED");
                    continuities.add(new AdmissionPolicyContinuity(original.id(), json.fingerprint(original), policy.id(),
                        json.fingerprint(policy), activePolicy().id(), json.fingerprint(activePolicy()), fence,
                        json.fingerprint(reservation), AdmissionPolicyContinuity.identity(json, reservation),
                        allowed, placements, actor, now));
                }
            }
            if (charged.stream().anyMatch(reservation -> !reservation.policyId().equals(policy.id()) && !coverage.contains(reservation.id())))
                throw new AdmissionFailure(CONFLICT, "POLICY_CONTINUITY_COVERAGE_REQUIRED");
            now = effectiveTime(requestedAt);
            policy(policy, now);
            checkCapacity(policy, held(), null, null);
            review("POLICY", policy.id(), policy, actor, now);
            for (var continuity : continuities)
                review(AdmissionPolicyContinuity.KIND, AdmissionPolicyContinuity.key(json, policy.id(), continuity.fence().reservationId()),
                    continuity, actor, now);
            jdbc.update("UPDATE deployment_admission_mutex SET active_policy_ref=? WHERE id=1", policy.id());
            return null;
        });
        } catch (org.springframework.dao.DataAccessException busy) {
            throw unusedCancellationLockFailure(busy, "POLICY_CONTINUITY_BUSY");
        }
    }
    void approveGrant(CustomerGrant grant, String actor, Instant requestedAt) {
        locked(() -> {
            Instant now = effectiveTime(requestedAt);
            grant(grant, now);
            var current = held().stream().filter(r -> grant.customerId().equals(r.customerId())).toList();
            checkCustomer(grant, current);
            String key = json.fingerprint(grant);
            review("GRANT", key, grant, actor, now);
            if (jdbc.update("UPDATE deployment_admission_grants SET record_ref=? WHERE customer_id=?", key, grant.customerId()) == 0)
                jdbc.update("INSERT INTO deployment_admission_grants(customer_id,record_ref) VALUES (?,?)", grant.customerId(), key);
            return null;
        });
    }
    void approveManifest(Manifest manifest, String actor, Instant now) {
        locked(() -> { manifest(manifest); review("MANIFEST", manifest.id(), manifest, actor, now); return null; });
    }
    void approveIndexRetention(ExternalIndexRetentionContracts.Policy policy, String actor, Instant requestedAt, Runnable localGuard) {
        locked(() -> {
            Instant now = effectiveTime(requestedAt);
            Reservation r = require(required(policy.reservationId(), 64));
            var existing = jdbc.query("SELECT fingerprint FROM deployment_admission_reviews WHERE kind='INDEX_RETENTION' AND record_key=?",
                (rs, row) -> rs.getString(1), r.id());
            if (!existing.isEmpty()) {
                if (!json.fingerprint(policy).equals(existing.getFirst())) throw new AdmissionFailure(CONFLICT, "REVIEWED_RECORD_IMMUTABLE");
                return null;
            }
            if (r.state() != State.ACTIVE || !r.generation().equals(policy.generation())
                || !r.manifestFingerprint().equals(policy.manifestFingerprint())
                || !r.bindingFingerprint().equals(policy.bindingFingerprint())
                || policy.indexes() < Math.max(1L, r.demand().amounts().get(Dimension.INDEXES))
                || policy.indexBytes() < Math.max(1L, r.demand().amounts().get(Dimension.INDEX_BYTES))
                || policy.reviewAcceptBefore() == null || !policy.reviewAcceptBefore().isAfter(now))
                throw new AdmissionFailure(PRECONDITION_FAILED, "INDEX_RETENTION_POLICY_INVALID");
            localGuard.run();
            requireEffectivePolicy(r, context(r));
            Mutation m = new Mutation(r);
            var envelope = new java.util.EnumMap<Dimension, Long>(Dimension.class);
            for (Dimension d : Dimension.values()) envelope.put(d, 0L);
            envelope.put(Dimension.INDEXES, policy.indexes()); envelope.put(Dimension.INDEX_BYTES, policy.indexBytes());
            m.retentionEnvelope = new Vector(envelope);
            var charged = new ArrayList<>(held().stream().filter(other -> !other.id().equals(r.id())).toList());
            charged.add(m.build(r, now));
            FleetPolicy fleet = activePolicy(); policy(fleet, now);
            CustomerGrant grant = customerGrant(r.customerId()); grant(grant, now);
            if (!grant.payerReference().equals(policy.payerReference())) throw new AdmissionFailure(CONFLICT, "INDEX_RETENTION_PAYER_CHANGED");
            checkCapacity(fleet, charged, null, null);
            checkCustomer(grant, charged.stream().filter(other -> r.customerId().equals(other.customerId())).toList());
            review("INDEX_RETENTION", r.id(), policy, actor, now);
            m.evidence = required(policy.policyReference(), 255);
            save(r, m, "INDEX_RETENTION_ENVELOPE_RESERVED", actor, now);
            return null;
        });
    }
    ExternalIndexRetentionContracts.Authorization indexRetentionAuthorization(String reservationId) {
        var rows = jdbc.query("SELECT fingerprint FROM deployment_admission_reviews WHERE kind='INDEX_RETENTION' AND record_key=?",
            (rs, row) -> rs.getString(1), reservationId);
        return rows.isEmpty() ? null : new ExternalIndexRetentionContracts.Authorization(reservationId, rows.getFirst());
    }
    private void review(String kind, String key, Object value, String actor, Instant now) {
        String fingerprint = json.fingerprint(value);
        var existing = jdbc.query("SELECT fingerprint FROM deployment_admission_reviews WHERE kind=? AND record_key=?",
            (rs, row) -> rs.getString(1), kind, key);
        if (!existing.isEmpty()) {
            if (!fingerprint.equals(existing.get(0))) throw new AdmissionFailure(CONFLICT, "REVIEWED_RECORD_IMMUTABLE");
            return;
        }
        jdbc.update("INSERT INTO deployment_admission_reviews(kind,record_key,fingerprint,payload_json,reviewed_by,reviewed_at) VALUES (?,?,?,?,?,?)",
            kind, key, fingerprint, json.write(value), required(actor, 255), time(now));
    }
    <T> T reviewed(String kind, String key, Class<T> type) {
        var rows = jdbc.query("SELECT payload_json FROM deployment_admission_reviews WHERE kind=? AND record_key=?",
            (rs, row) -> json.read(rs.getString(1), type), kind, key);
        if (rows.isEmpty()) throw new AdmissionFailure(PRECONDITION_FAILED, "REVIEWED_" + kind + "_REQUIRED");
        return rows.get(0);
    }
    private <T> T verifiedReview(String kind, String key, Class<T> type) {
        var rows = jdbc.query("SELECT fingerprint,payload_json FROM deployment_admission_reviews WHERE kind=? AND record_key=?",
            (rs, row) -> {
                T value = json.read(rs.getString("payload_json"), type);
                if (!json.fingerprint(value).equals(rs.getString("fingerprint")))
                    throw new AdmissionFailure(PRECONDITION_FAILED, "POLICY_CONTINUITY_RECORD_CHANGED");
                return value;
            }, kind, key);
        if (rows.size() != 1) throw new AdmissionFailure(PRECONDITION_FAILED, "POLICY_CONTINUITY_REQUIRED");
        return rows.getFirst();
    }
    FleetPolicy activePolicy() {
        String id = jdbc.queryForObject("SELECT active_policy_ref FROM deployment_admission_mutex WHERE id=1", String.class);
        if (id == null) throw new AdmissionFailure(PRECONDITION_FAILED, "MEASURED_POLICY_REQUIRED");
        return verifiedReview("POLICY", id, FleetPolicy.class);
    }
    CustomerGrant customerGrant(String customerId) {
        var rows = jdbc.query("SELECT record_ref FROM deployment_admission_grants WHERE customer_id=?", (rs, row) -> rs.getString(1), customerId);
        if (rows.isEmpty()) throw new AdmissionFailure(PRECONDITION_FAILED, "ACTIVE_CUSTOMER_QUOTA_REQUIRED");
        return reviewed("GRANT", rows.get(0), CustomerGrant.class);
    }
    AdmissionContext context(String customer, String tenant, String deployment, String manifestId) {
        FleetPolicy policy = activePolicy(); CustomerGrant grant = customerGrant(customer);
        Manifest manifest = reviewed("MANIFEST", manifestId, Manifest.class);
        TargetBudget target = policy.targets().get(manifest.targetProfileId());
        if (target == null || !manifest.deploymentId().equals(deployment) || !grant.targetLimits().containsKey(target.targetProfileId()))
            throw new AdmissionFailure(FORBIDDEN, "TARGET_OR_MANIFEST_NOT_ENTITLED");
        return new AdmissionContext(customer, tenant, deployment, policy, grant, manifest, json.fingerprint(manifest), target);
    }
    AdmissionContext context(Reservation reservation) { return context(reservation, reservation.manifestId()); }
    AdmissionContext context(Reservation reservation, String manifestId) {
        AdmissionContext current = context(reservation.customerId(), reservation.tenantId(), reservation.deploymentId(), manifestId);
        var resolved = new AdmissionContext(current.customerId(), current.tenantId(), current.deploymentId(), current.policy(), current.grant(),
            current.manifest(), current.manifestFingerprint(), current.target(), effectivePolicyAuthority(reservation, current.policy()));
        requireEffectivePolicy(reservation, resolved);
        return resolved;
    }
    private PolicyAuthority effectivePolicyAuthority(Reservation reservation, FleetPolicy measured) {
        FleetPolicy original = verifiedReview("POLICY", reservation.policyId(), FleetPolicy.class);
        if (original.id().equals(measured.id())) {
            if (!json.fingerprint(original).equals(json.fingerprint(measured)))
                throw new AdmissionFailure(PRECONDITION_FAILED, "POLICY_CONTINUITY_RECORD_CHANGED");
            return null;
        }
        var coverage = measured.continuations().stream().filter(value -> value.originalPolicyId().equals(original.id())
            && value.originalPolicyFingerprint().equals(json.fingerprint(original)))
            .flatMap(value -> value.reservations().stream()).filter(value -> value.reservationId().equals(reservation.id())).toList();
        if (coverage.size() != 1) throw new AdmissionFailure(PRECONDITION_FAILED, "POLICY_CONTINUITY_REQUIRED");
        Fence fence = coverage.getFirst();
        var relation = verifiedReview(AdmissionPolicyContinuity.KIND,
            AdmissionPolicyContinuity.key(json, measured.id(), reservation.id()), AdmissionPolicyContinuity.class);
        AdmissionPolicyContinuity.compatible(original, measured);
        FleetPolicy previous = verifiedReview("POLICY", relation.previousMeasuredPolicyId(), FleetPolicy.class);
        if (!json.fingerprint(previous).equals(relation.previousMeasuredPolicyFingerprint()))
            throw new AdmissionFailure(PRECONDITION_FAILED, "POLICY_CONTINUITY_RECORD_CHANGED");
        AdmissionPolicyContinuity.compatible(previous, measured);
        if (!relation.originalPolicyId().equals(original.id()) || !relation.originalPolicyFingerprint().equals(json.fingerprint(original))
            || !relation.measuredPolicyId().equals(measured.id()) || !relation.measuredPolicyFingerprint().equals(json.fingerprint(measured))
            || !relation.fence().equals(fence) || !fence.generation().equals(reservation.generation())
            || fence.revision() > reservation.revision() || !relation.allocationIdentity().equals(AdmissionPolicyContinuity.identity(json, reservation))
            || !relation.placementEvidence().keySet().equals(measured.targets().keySet())
            || !relation.authorizedPolicyReferences().contains("policy:" + original.id())
            || !relation.authorizedPolicyReferences().contains("policy:" + measured.id()))
            throw new AdmissionFailure(PRECONDITION_FAILED, "POLICY_CONTINUITY_RECORD_CHANGED");
        return new PolicyAuthority(reservation.id(), reservation.generation(), original.id(), json.fingerprint(original),
            json.fingerprint(relation), relation.authorizedPolicyReferences());
    }
    void requireEffectivePolicy(Reservation reservation, AdmissionContext context) {
        if (!json.fingerprint(activePolicy()).equals(json.fingerprint(context.policy())))
            throw new AdmissionFailure(CONFLICT, "ADMISSION_POLICY_CHANGED");
        if (!Objects.equals(context.policyAuthority(), effectivePolicyAuthority(reservation, context.policy())))
            throw new AdmissionFailure(PRECONDITION_FAILED, "POLICY_CONTINUITY_RECORD_CHANGED");
        var allowed = context.policyAuthority() == null ? java.util.Set.of("policy:" + context.policy().id())
            : context.policyAuthority().authorizedPolicyReferences();
        if (!allowed.containsAll(policyReferences(context.manifest())))
            throw new AdmissionFailure(PRECONDITION_FAILED, "POLICY_CONTINUITY_MANIFEST_CHANGED");
    }
    java.util.Set<String> authorizedPolicyReferences(AdmissionContext context) {
        if (!json.fingerprint(activePolicy()).equals(json.fingerprint(context.policy())))
            throw new AdmissionFailure(CONFLICT, "ADMISSION_POLICY_CHANGED");
        if (context.policyAuthority() == null) return java.util.Set.of("policy:" + context.policy().id());
        var reservation = require(context.policyAuthority().reservationId());
        requireEffectivePolicy(reservation, context);
        if (!reservation.customerId().equals(context.customerId()) || !reservation.tenantId().equals(context.tenantId())
            || !reservation.deploymentId().equals(context.deploymentId()) || !reservation.targetProfileId().equals(context.target().targetProfileId()))
            throw new AdmissionFailure(PRECONDITION_FAILED, "POLICY_CONTINUITY_SCOPE_CHANGED");
        return context.policyAuthority().authorizedPolicyReferences();
    }
    private java.util.Set<String> policyReferences(Manifest manifest) {
        return manifest.prerequisiteReferences().stream().filter(value -> value.startsWith("policy:"))
            .collect(java.util.stream.Collectors.toSet());
    }
    FleetPolicy manifestPolicy(String deploymentId, String targetId, String versionId) {
        var matches = held(deploymentId, targetId).stream().filter(Reservation::occupiesActiveSlot).toList();
        if (matches.size() > 1) throw new AdmissionFailure(CONFLICT, "CURRENT_RESERVATION_REQUIRES_RECONCILIATION");
        if (matches.isEmpty()) return activePolicy();
        Reservation reservation = matches.getFirst();
        Manifest manifest = reviewed("MANIFEST", reservation.manifestId(), Manifest.class);
        if (!manifest.versionId().equals(versionId)) return activePolicy();
        var current = context(reservation);
        policy(current.policy(), clock.instant());
        var references = policyReferences(manifest);
        if (references.size() != 1 || !authorizedPolicyReferences(current).containsAll(references))
            throw new AdmissionFailure(PRECONDITION_FAILED, "POLICY_CONTINUITY_MANIFEST_CHANGED");
        return verifiedReview("POLICY", references.iterator().next().substring("policy:".length()), FleetPolicy.class);
    }
    Reservation replay(String customer, String deployment, String target, Reserve command, String fingerprint) {
        if (command.customerContext() != null) {
            var receipts = authorizationRows(customer, deployment, target, command.operationKey());
            if (receipts.isEmpty()) return null;
            AuthorizationReceipt receipt = receipts.getFirst();
            if (!receipt.requestFingerprint().equals(fingerprint)) throw new AdmissionFailure(CONFLICT, "OPERATION_KEY_REUSED");
            return require(receipt.reservationId());
        }
        var rows = jdbc.query("SELECT payload_json FROM deployment_capacity_reservations WHERE customer_id=? AND deployment_id=? AND target_profile_id=? AND operation_key=?",
            (rs, row) -> json.read(rs.getString(1), Reservation.class), customer, deployment, target, command.operationKey());
        if (rows.isEmpty()) return null;
        if (!rows.get(0).requestFingerprint().equals(fingerprint)) throw new AdmissionFailure(CONFLICT, "OPERATION_KEY_REUSED");
        return rows.get(0);
    }
    String commandFingerprint(String customer, String tenant, String deployment, Manifest manifest, Reserve command) {
        Map<String, Object> value = new java.util.TreeMap<>();
        value.put("customer", customer); value.put("tenant", tenant); value.put("deployment", deployment);
        value.put("manifestId", manifest.id()); value.put("manifestFingerprint", json.fingerprint(manifest));
        value.put("target", manifest.targetProfileId());
        if (command.allocationId() != null) value.put("allocationId", command.allocationId());
        if (command.customerContext() != null) value.put("customerContext", command.customerContext());
        return json.fingerprint(value);
    }
    Reservation reserve(AdmissionContext checked, Reserve command, PrerequisiteReadback evidence, String actor, Instant now) {
        return reserve(checked, command, evidence, actor, now, () -> { });
    }
    Reservation reserve(AdmissionContext checked, Reserve command, PrerequisiteReadback evidence, String actor, Instant requestedAt, Runnable localGuard) {
        return locked(() -> {
            Instant now = effectiveTime(requestedAt);
            if (command.allocationId() != null) {
                Reservation allocation = require(command.allocationId());
                if (!allocation.customerId().equals(checked.customerId()) || !allocation.tenantId().equals(checked.tenantId())
                    || !allocation.deploymentId().equals(checked.deploymentId()) || !allocation.targetProfileId().equals(checked.target().targetProfileId()))
                    throw new AdmissionFailure(CONFLICT, "ALLOCATION_REAPPLY_SCOPE_INVALID");
            }
            AdmissionContext current = command.allocationId() == null
                ? context(checked.customerId(), checked.tenantId(), checked.deploymentId(), command.manifestId())
                : context(require(command.allocationId()), command.manifestId());
            validateCustomerScope(current, command);
            String fingerprint = commandFingerprint(current.customerId(), current.tenantId(), current.deploymentId(), current.manifest(), command);
            Reservation replay = replay(current.customerId(), current.deploymentId(), current.target().targetProfileId(), command, fingerprint);
            if (replay != null) return replay;
            if (!json.fingerprint(current).equals(json.fingerprint(checked))) throw new AdmissionFailure(CONFLICT, "ADMISSION_POLICY_CHANGED");
            localGuard.run();
            now = effectiveTime(requestedAt);
            policy(current.policy(), now); grant(current.grant(), now); manifest(current.manifest()); prerequisites(current, evidence, now);
            if (command.allocationId() != null) {
                String allocationId = required(command.allocationId(), 64);
                // Match bindRelease's allocation-before-release order before reading the live manifest.
                try {
                    jdbc.queryForObject("SELECT id FROM deployment_capacity_reservations WHERE id=? FOR UPDATE", String.class, allocationId);
                } catch (EmptyResultDataAccessException missing) {
                    throw new AdmissionFailure(NOT_FOUND, "RESERVATION_NOT_FOUND");
                }
                Reservation allocation = require(allocationId);
                boolean lifecycleExtension = command.customerContext() != null && (command.customerContext().operation()
                    == com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation.STAGING_EXTEND
                    || command.customerContext().operation() == com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation.STAGING_RETAIN);
                boolean reviewedReapply = !allocation.manifestFingerprint().equals(current.manifestFingerprint());
                if (reviewedReapply) requireReviewedReapply(allocation, current, command, now);
                else {
                    requireUsableAllocation(allocation, current, now, lifecycleExtension);
                    requireApplyIdle(allocation, command);
                }
                requireReleaseAuthorization(allocation, command);
                Mutation refresh = new Mutation(allocation);
                if (reviewedReapply) {
                    refresh.manifestId = current.manifest().id();
                    refresh.manifestFingerprint = current.manifestFingerprint();
                    refresh.bindingFingerprint = current.manifest().bindingFingerprint();
                }
                refresh.prerequisiteReadback = evidence;
                persistAuthorization(refresh.build(allocation, now), current, command, fingerprint, now);
                if (reviewedReapply) {
                    review("REAPPLY_PROOF", fingerprint, evidence, actor, now);
                    // Planning must not replace the still-active release's manifest or readback.
                    return save(allocation, new Mutation(allocation), "ALLOCATION_REAPPLY_PLANNED", actor, now);
                }
                return save(allocation, refresh, "ALLOCATION_OPERATION_AUTHORIZED", actor, now);
            }
            var held = held();
            if (held.stream().anyMatch(r -> r.occupiesActiveSlot() && r.deploymentId().equals(current.deploymentId()) && r.targetProfileId().equals(current.target().targetProfileId())))
                throw new AdmissionFailure(CONFLICT, "CURRENT_RESERVATION_REQUIRES_RECONCILIATION");
            checkCapacity(current.policy(), held, current.manifest().demand(), current.target().targetProfileId());
            var customerHeld = new ArrayList<>(held.stream().filter(r -> current.customerId().equals(r.customerId())).toList());
            Reservation reservation = new Reservation(uuid(), current.customerId(), current.tenantId(), current.deploymentId(),
                current.target().targetProfileId(), current.target().intent(), command.operationKey(), fingerprint, uuid(), 0,
                State.RESERVED, current.manifest().demand(), current.policy().id(), current.manifest().id(), current.manifestFingerprint(),
                current.manifest().bindingFingerprint(), now, now, now, now.plusSeconds(current.policy().lifecycle().inactivitySeconds()),
                0, null, null, ExpiryState.CURRENT, uuid(), null, 0, null, null, null, null, evidence.evidenceReference(), null,
                command.customerContext() == null ? null : json.fingerprint(command.customerContext()),
                command.customerContext() == null ? null : command.customerContext().operation(), evidence);
            customerHeld.add(reservation); checkCustomer(current.grant(), customerHeld);
            jdbc.update("""
                INSERT INTO deployment_capacity_reservations(id,customer_id,deployment_id,target_profile_id,operation_key,
                  request_fingerprint,generation,revision,state,current_slot,expires_at,next_retry_at,expiry_state,payload_json,updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, reservation.id(), reservation.customerId(), reservation.deploymentId(), reservation.targetProfileId(), reservation.operationKey(),
                fingerprint, reservation.generation(), 0L, reservation.state().name(), slot(reservation), time(reservation.expiresAt()), null,
                reservation.expiryState().name(), json.write(reservation), time(now));
            event(reservation, "RESERVED", actor, evidence.evidenceReference(), now);
            if (command.customerContext() != null) persistAuthorization(reservation, current, command, fingerprint, now);
            return reservation;
        });
    }

    private void requireReviewedReapply(Reservation allocation, AdmissionContext current, Reserve command, Instant now) {
        var scope = command.customerContext();
        if (scope == null || !reviewedReapplyOperation(scope.operation()) || scope.releaseId() != null || scope.topology() == null
            || !allocation.customerId().equals(current.customerId()) || !allocation.tenantId().equals(current.tenantId())
            || !allocation.deploymentId().equals(current.deploymentId())
            || !allocation.targetProfileId().equals(current.target().targetProfileId())
            || allocation.intent() != current.target().intent()) {
            throw new AdmissionFailure(CONFLICT, "ALLOCATION_REAPPLY_SCOPE_INVALID");
        }
        requireEffectivePolicy(allocation, current);
        AdmissionContext previous = context(allocation);
        requireUsableAllocation(allocation, previous, now);
        if (allocation.state() != State.ACTIVE || allocation.failureCode() != null || allocation.retirementOperationId() != null
            || allocation.drainDeadline() != null || allocation.recoveryUntil() != null) {
            throw new AdmissionFailure(CONFLICT, "ALLOCATION_REAPPLY_REQUIRES_IDLE_ACTIVE");
        }
        if (previous.manifest().versionId().equals(current.manifest().versionId())
            || !previous.manifest().topologyReference().equals(current.manifest().topologyReference())) {
            throw new AdmissionFailure(CONFLICT, "ALLOCATION_REAPPLY_REQUIRES_NEW_REVIEWED_VERSION");
        }
        // Reapply reuses the held envelope, never credits old resources or silently resizes a live allocation.
        within(current.manifest().demand(), allocation.demand(), "ALLOCATION_REAPPLY_CAPACITY_REVIEW_REQUIRED");
        requireApplyIdle(allocation, command);
        String releaseId = currentReleaseId(allocation);
        if (releaseId == null) throw new AdmissionFailure(CONFLICT, "ALLOCATION_REAPPLY_ACTIVE_RELEASE_REQUIRED");
        jdbc.queryForObject("SELECT release_id FROM deployment_admission_release_bindings WHERE release_id=? FOR UPDATE", String.class, releaseId);
        activeCustomerReleaseReservation(allocation.customerId(), allocation.tenantId(), allocation.deploymentId(), releaseId);
        var binding = releaseBinding(releaseId);
        var admitted = authorizationRows(allocation.customerId(), allocation.deploymentId(), allocation.targetProfileId(),
            binding.authorizationKey()).getFirst().command().customerContext();
        if (!scope.offerId().equals(admitted.offerId()) || !scope.offerFingerprint().equals(admitted.offerFingerprint())) {
            throw new AdmissionFailure(CONFLICT, "ALLOCATION_REAPPLY_SCOPE_INVALID");
        }
        var held = held();
        checkCapacity(current.policy(), held, null, null);
        checkCustomer(current.grant(), held.stream().filter(r -> current.customerId().equals(r.customerId())).toList());
    }

    // Planning may inspect a replacement, but only apply receipts may bind a release for provider work.
    private boolean reviewedReapplyOperation(com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation operation) {
        return operation == com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation.PROMOTION_PLAN
            || applyOperation(operation);
    }

    private boolean applyOperation(com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation operation) {
        return operation == com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation.APPLY
            || operation == com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation.PROMOTION_APPLY;
    }

    private void requireApplyIdle(Reservation allocation, Reserve command) {
        if (command.customerContext() == null || !applyOperation(command.customerContext().operation())) return;
        Long pending = jdbc.queryForObject("""
            SELECT COUNT(*) FROM deployment_admission_authorizations a
             WHERE a.reservation_id=? AND a.generation=? AND a.operation IN ('APPLY','PROMOTION_APPLY')
               AND a.version_id=? AND a.operation_key<>?
               AND NOT EXISTS (SELECT 1 FROM deployment_admission_release_bindings b
                 WHERE b.reservation_id=a.reservation_id AND b.generation=a.generation AND b.authorization_key=a.operation_key)
            """, Long.class, allocation.id(), allocation.generation(),
            reviewed("MANIFEST", command.manifestId(), Manifest.class).versionId(), command.operationKey());
        if (pending == null || pending > 0) throw new AdmissionFailure(CONFLICT, "ALLOCATION_APPLY_ALREADY_AUTHORIZED");
        String releaseId = currentReleaseId(allocation);
        if (releaseId != null && !"ACTIVE".equals(releaseBinding(releaseId).state())) {
            throw new AdmissionFailure(CONFLICT, "ALLOCATION_RELEASE_ALREADY_IN_PROGRESS");
        }
    }

    private void requireUsableAllocation(Reservation reservation, AdmissionContext current, Instant now) {
        requireUsableAllocation(reservation, current, now, false);
    }
    private void requireUsableAllocation(Reservation reservation, AdmissionContext current, Instant now, boolean lifecycleExtension) {
        requireEffectivePolicy(reservation, current);
        if (!reservation.customerId().equals(current.customerId()) || !reservation.tenantId().equals(current.tenantId())
            || !reservation.deploymentId().equals(current.deploymentId())
            || !reservation.targetProfileId().equals(current.target().targetProfileId())
            || reservation.intent() != current.target().intent()
            || !reservation.manifestFingerprint().equals(current.manifestFingerprint())
            || !reservation.bindingFingerprint().equals(current.manifest().bindingFingerprint())
            || !List.of(State.RESERVED, State.PROVISIONING, State.ACTIVE).contains(reservation.state())
            || (reservation.expiryState() != ExpiryState.CURRENT && !(lifecycleExtension
                && List.of(ExpiryState.NOTICE_PENDING, ExpiryState.NOTICE_FAILED, ExpiryState.NOTIFIED).contains(reservation.expiryState())))
            || !reservation.expiresAt().isAfter(now)
            || reservation.workerToken() != null) {
            throw new AdmissionFailure(CONFLICT, "ALLOCATION_NOT_CURRENT_FOR_REVIEWED_LIFECYCLE");
        }
    }

    private void requireReleaseAuthorization(Reservation allocation, Reserve command) {
        if (command.customerContext() == null) throw new AdmissionFailure(BAD_REQUEST, "CUSTOMER_ADMISSION_CONTEXT_REQUIRED");
        var context = command.customerContext();
        if ((context.operation() == com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation.ACTIVATE
            || context.operation() == com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation.ROLLBACK)
            && context.releaseId() == null) throw new AdmissionFailure(CONFLICT, "ALLOCATION_RELEASE_SCOPE_REQUIRED");
        if (context.releaseId() != null) {
            ReleaseBinding binding = releaseBinding(context.releaseId());
            Manifest manifest = reviewed("MANIFEST", allocation.manifestId(), Manifest.class);
            if (binding == null || !allocation.id().equals(binding.reservationId())
                || !allocation.generation().equals(binding.generation())
                || !allocation.customerId().equals(binding.customerId()) || !allocation.tenantId().equals(binding.tenantId())
                || !allocation.deploymentId().equals(binding.deploymentId()) || !allocation.targetProfileId().equals(binding.targetProfileId())
                || !manifest.versionId().equals(binding.versionId()) || !manifest.versionConfigHash().equals(binding.versionConfigHash())) {
                throw new AdmissionFailure(CONFLICT, "ALLOCATION_RELEASE_SCOPE_MISMATCH");
            }
            if (context.operation() == com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation.ACTIVATE
                && !"ACTIVE".equals(binding.state())) throw new AdmissionFailure(CONFLICT, "ALLOCATION_RELEASE_NOT_VERIFIED");
        }
    }

    private void persistAuthorization(Reservation reservation, AdmissionContext current, Reserve command,
                                      String fingerprint, Instant now) {
        var scope = command.customerContext();
        AuthorizationReceipt receipt = new AuthorizationReceipt(reservation.id(), reservation.generation(),
            reservation.manifestFingerprint(), fingerprint, json.fingerprint(scope), command);
        jdbc.update("""
            INSERT INTO deployment_admission_authorizations
             (customer_id,deployment_id,target_profile_id,operation_key,reservation_id,generation,operation,
              version_id,request_fingerprint,context_fingerprint,payload_json,created_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
            """, current.customerId(), current.deploymentId(), current.target().targetProfileId(), command.operationKey(),
            reservation.id(), reservation.generation(), scope.operation().name(), current.manifest().versionId(),
            fingerprint, receipt.contextFingerprint(), json.write(receipt), time(now));
    }

    private List<AuthorizationReceipt> authorizationRows(String customer, String deployment, String target, String key) {
        return jdbc.query("""
            SELECT payload_json FROM deployment_admission_authorizations
             WHERE customer_id=? AND deployment_id=? AND target_profile_id=? AND operation_key=?
            """, (rs, row) -> json.read(rs.getString(1), AuthorizationReceipt.class), customer, deployment, target, key);
    }

    void requireInitialRenewalScope(Reservation reservation,
        com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.AdmissionContext fresh) {
        var receipt = initialRenewalAuthorization(reservation, fresh);
        if (!InitialAdmissionEvidenceRenewal.sameScope(receipt.command().customerContext(), fresh))
            throw new AdmissionFailure(CONFLICT, "INITIAL_RENEWAL_SCOPE_CHANGED");
    }

    private AuthorizationReceipt initialRenewalAuthorization(Reservation reservation,
        com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.AdmissionContext fresh) {
        if (fresh == null || reservation.state() != State.RESERVED || reservation.expiryState() != ExpiryState.CURRENT
            || !reservation.expiresAt().isAfter(clock.instant()) || reservation.workerToken() != null
            || reservation.failureCode() != null || reservation.retirementOperationId() != null
            || reservation.drainDeadline() != null || reservation.recoveryUntil() != null
            || reservation.nextRetryAt() != null || reservation.attempts() != 0 || reservation.retentionEnvelope() != null
            || reservation.keepUntil() != null || reservation.keepEntitlementReference() != null || reservation.extensions() != 0)
            throw new AdmissionFailure(CONFLICT, "INITIAL_RENEWAL_REQUIRES_UNUSED_RESERVATION");
        var receipts = authorizationRows(reservation.customerId(), reservation.deploymentId(), reservation.targetProfileId(), fresh.operationKey());
        if (receipts.size() != 1) throw new AdmissionFailure(CONFLICT, "INITIAL_RENEWAL_AUTHORIZATION_REQUIRED");
        var receipt = receipts.getFirst();
        var command = receipt.command();
        Manifest manifest = reviewed("MANIFEST", reservation.manifestId(), Manifest.class);
        if (command == null || command.customerContext() == null || command.allocationId() != null
            || !reservation.id().equals(receipt.reservationId()) || !reservation.generation().equals(receipt.generation())
            || !reservation.operationKey().equals(command.operationKey()) || !reservation.manifestId().equals(command.manifestId())
            || !reservation.manifestFingerprint().equals(receipt.manifestFingerprint())
            || !reservation.requestFingerprint().equals(receipt.requestFingerprint())
            || !Objects.equals(reservation.customerContextFingerprint(), receipt.contextFingerprint())
            || reservation.customerOperation() != command.customerContext().operation()
            || !json.fingerprint(command.customerContext()).equals(receipt.contextFingerprint())
            || !commandFingerprint(reservation.customerId(), reservation.tenantId(), reservation.deploymentId(), manifest, command)
                .equals(receipt.requestFingerprint()))
            throw new AdmissionFailure(CONFLICT, "INITIAL_RENEWAL_AUTHORIZATION_CHANGED");
        return receipt;
    }

    String initialProviderGrantsFingerprint(Reservation reservation) {
        var rows = jdbc.query("""
            SELECT id,fingerprint,payload_json,lifecycle_status,allocated_chat_attempts,allocated_total_tokens,allocated_cost_nano_usd
              FROM provider_usage_grants WHERE customer_id=? AND target_profile_id=? ORDER BY id
            """, (rs, row) -> {
                return List.of(rs.getString("id"), rs.getString("fingerprint"), rs.getString("payload_json"), rs.getString("lifecycle_status"),
                    rs.getLong("allocated_chat_attempts"), rs.getLong("allocated_total_tokens"), rs.getLong("allocated_cost_nano_usd"));
            }, reservation.customerId(), reservation.targetProfileId());
        return json.fingerprint(rows);
    }

    Reservation renewInitialCustomerEvidence(Fence fence, AdmissionContext checked,
        com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.AdmissionContext fresh,
        PrerequisiteReadback evidence, String providerGrantsFingerprint, String actor, Instant requestedAt, Runnable localGuard) {
        try {
            return transaction.execute(status -> {
            // Apply owns deployment first. Never wait for it, or hold its ledger while waiting for its authorization.
            try {
                if (jdbc.query("SELECT id FROM platform_deployments WHERE id=? FOR UPDATE NOWAIT",
                    (rs, row) -> rs.getString(1), checked.deploymentId()).size() != 1)
                    throw new AdmissionFailure(NOT_FOUND, "DEPLOYMENT_NOT_FOUND");
                jdbc.queryForObject("SELECT id FROM deployment_admission_mutex WHERE id=1 FOR UPDATE NOWAIT", Integer.class);
                jdbc.query("SELECT operation_key FROM deployment_admission_authorizations WHERE reservation_id=? ORDER BY operation_key FOR UPDATE NOWAIT",
                    (rs, row) -> rs.getString(1), fence.reservationId());
                jdbc.query("SELECT id FROM deployment_capacity_reservations WHERE id=? FOR UPDATE NOWAIT",
                    (rs, row) -> rs.getString(1), fence.reservationId());
            } catch (org.springframework.dao.DataAccessException busy) {
                throw unusedCancellationLockFailure(busy, "INITIAL_RENEWAL_BUSY");
            }
            Reservation reservation = fenced(fence);
            requireInitialRenewalScope(reservation, fresh);
            var receipt = initialRenewalAuthorization(reservation, fresh);
            AdmissionContext current = context(reservation);
            if (!json.fingerprint(current).equals(json.fingerprint(checked)))
                throw new AdmissionFailure(CONFLICT, "ADMISSION_POLICY_CHANGED");
            validateCustomerScope(current, new Reserve(fresh.operationKey(), reservation.manifestId(), fresh));
            requireUsableAllocation(reservation, current, effectiveTime(requestedAt));
            var owners = jdbc.queryForList("SELECT actor_id FROM deployment_admission_events WHERE reservation_id=? AND event_type='RESERVED'",
                String.class, reservation.id());
            if (!owners.equals(List.of(actor))) throw new AdmissionFailure(CONFLICT, "INITIAL_RENEWAL_OWNER_CHANGED");
            Long authorizations = jdbc.queryForObject("SELECT COUNT(*) FROM deployment_admission_authorizations WHERE reservation_id=?", Long.class, reservation.id());
            if (authorizations == null || authorizations != 1L)
                throw new AdmissionFailure(CONFLICT, "INITIAL_RENEWAL_AUTHORIZATION_AMBIGUOUS");
            var unused = new AdmissionUnusedReservationEvidence(jdbc, json);
            var configured = AdmissionUnusedConsumerDetachment.readForInitialEvidenceRenewal(jdbc, json, reservation, fresh);
            var noEffects = unused.read(reservation, current.manifest(), configured.consumerIds());
            if (!providerGrantsFingerprint.equals(initialProviderGrantsFingerprint(reservation)))
                throw new AdmissionFailure(CONFLICT, "INITIAL_RENEWAL_PROVIDER_GRANT_CHANGED");
            localGuard.run();
            Instant now = effectiveTime(requestedAt);
            if (!json.fingerprint(reservation).equals(json.fingerprint(fenced(fence)))
                || !json.fingerprint(current).equals(json.fingerprint(context(reservation))))
                throw new AdmissionFailure(CONFLICT, "INITIAL_RENEWAL_AUTHORITY_CHANGED");
            policy(current.policy(), now); grant(current.grant(), now); prerequisites(current, evidence, now);
            requireUsableAllocation(reservation, current, now);
            if (!configured.equals(AdmissionUnusedConsumerDetachment.readForInitialEvidenceRenewal(jdbc, json, reservation, fresh))
                || !noEffects.equals(unused.read(reservation, current.manifest(), configured.consumerIds()))
                || !providerGrantsFingerprint.equals(initialProviderGrantsFingerprint(reservation)))
                throw new AdmissionFailure(CONFLICT, "INITIAL_RENEWAL_EFFECTS_CHANGED");
            var prior = initialEvidenceRenewal(reservation, receipt);
            if (prior != null && prior.context().equals(fresh) && prior.readback().equals(evidence)
                && reservation.prerequisiteReadback().equals(evidence)) return reservation;
            long revision = Math.incrementExact(reservation.revision());
            var renewal = new InitialAdmissionEvidenceRenewal(reservation.id(), reservation.generation(), receipt.requestFingerprint(),
                receipt.contextFingerprint(), reservation.revision(), revision, fresh, evidence, actor, now);
            String key = InitialAdmissionEvidenceRenewal.key(receipt.requestFingerprint(), revision);
            review(InitialAdmissionEvidenceRenewal.KIND, key, renewal, actor, now);
            Mutation mutation = new Mutation(reservation);
            mutation.prerequisiteReadback = evidence;
            mutation.evidence = "initial-proof:" + key + ":" + json.fingerprint(renewal);
            return save(reservation, mutation, "INITIAL_EVIDENCE_RENEWED", actor, now);
            });
        } catch (org.springframework.dao.DataAccessException busy) {
            throw unusedCancellationLockFailure(busy, "INITIAL_RENEWAL_BUSY");
        }
    }

    private InitialAdmissionEvidenceRenewal initialEvidenceRenewal(Reservation reservation, AuthorizationReceipt receipt) {
        if (!reservation.requestFingerprint().equals(receipt.requestFingerprint())) return null;
        // The ledger emits exactly one event per revision; a deleted head cannot hide behind an older proof.
        Long events = jdbc.queryForObject("SELECT COUNT(*) FROM deployment_admission_events WHERE reservation_id=? AND revision BETWEEN 0 AND ?",
            Long.class, reservation.id(), reservation.revision());
        if (events == null || events != Math.incrementExact(reservation.revision()))
            throw new AdmissionFailure(PRECONDITION_FAILED, "INITIAL_RENEWAL_RECORD_CHANGED");
        var heads = jdbc.query("""
            SELECT revision,actor_id,evidence_reference FROM deployment_admission_events
             WHERE reservation_id=? AND event_type='INITIAL_EVIDENCE_RENEWED' ORDER BY revision DESC LIMIT 1
            """, (rs, row) -> new InitialRenewalHead(rs.getLong("revision"), rs.getString("actor_id"), rs.getString("evidence_reference")), reservation.id());
        if (heads.isEmpty()) {
            Long orphaned = jdbc.queryForObject("SELECT COUNT(*) FROM deployment_admission_reviews WHERE kind=? AND record_key LIKE ?",
                Long.class, InitialAdmissionEvidenceRenewal.KIND, receipt.requestFingerprint() + ":%");
            if (orphaned == null || orphaned != 0 || reservation.evidenceReference() == null || reservation.evidenceReference().startsWith("initial-proof:"))
                throw new AdmissionFailure(PRECONDITION_FAILED, "INITIAL_RENEWAL_RECORD_CHANGED");
            return null;
        }
        var head = heads.getFirst();
        if (head.revision() > reservation.revision())
            throw new AdmissionFailure(PRECONDITION_FAILED, "INITIAL_RENEWAL_RECORD_CHANGED");
        String key = InitialAdmissionEvidenceRenewal.key(receipt.requestFingerprint(), head.revision());
        var rows = jdbc.query("""
            SELECT record_key,fingerprint,payload_json FROM deployment_admission_reviews
             WHERE kind=? AND record_key=?
            """, (rs, row) -> {
                var value = json.read(rs.getString("payload_json"), InitialAdmissionEvidenceRenewal.class);
                if (!json.fingerprint(value).equals(rs.getString("fingerprint"))
                    || !key.equals(InitialAdmissionEvidenceRenewal.key(receipt.requestFingerprint(), value.revision()))
                    || !head.actor().equals(value.actor())
                    || !("initial-proof:" + key + ":" + json.fingerprint(value)).equals(head.reference()))
                    throw new AdmissionFailure(PRECONDITION_FAILED, "INITIAL_RENEWAL_RECORD_CHANGED");
                return value;
            }, InitialAdmissionEvidenceRenewal.KIND, key);
        if (rows.size() != 1) throw new AdmissionFailure(PRECONDITION_FAILED, "INITIAL_RENEWAL_RECORD_CHANGED");
        var renewal = rows.getFirst();
        if (!reservation.id().equals(renewal.reservationId()) || !reservation.generation().equals(renewal.generation())
            || !receipt.requestFingerprint().equals(renewal.authorizationRequestFingerprint())
            || !receipt.contextFingerprint().equals(renewal.originalContextFingerprint())
            || renewal.expectedRevision() < 0 || renewal.revision() != renewal.expectedRevision() + 1
            || renewal.revision() > reservation.revision()
            || !InitialAdmissionEvidenceRenewal.sameScope(receipt.command().customerContext(), renewal.context())
            || renewal.readback() == null || !reservation.manifestFingerprint().equals(renewal.readback().manifestFingerprint())
            || !reservation.bindingFingerprint().equals(renewal.readback().bindingFingerprint()))
            throw new AdmissionFailure(PRECONDITION_FAILED, "INITIAL_RENEWAL_RECORD_CHANGED");
        return renewal;
    }

    private com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.AdmissionContext effectiveCustomerContext(
        Reservation reservation, AuthorizationReceipt receipt) {
        var renewal = initialEvidenceRenewal(reservation, receipt);
        return renewal == null ? receipt.command().customerContext() : renewal.context();
    }

    void requireCustomerAuthorization(Reservation reservation,
        com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.AdmissionContext requested) {
        var receipts = authorizationRows(requested.customerId(), requested.deploymentId(), requested.targetProfileId(), requested.operationKey());
        if (receipts.size() != 1) throw new AdmissionFailure(PRECONDITION_FAILED, "CUSTOMER_OPERATION_AUTHORIZATION_REQUIRED");
        AuthorizationReceipt receipt = receipts.getFirst();
        reservation = candidateAuthorization(reservation, receipt);
        Manifest manifest = reviewed("MANIFEST", reservation.manifestId(), Manifest.class);
        var effectiveContext = effectiveCustomerContext(reservation, receipt);
        if (!reservation.id().equals(receipt.reservationId()) || !reservation.generation().equals(receipt.generation())
            || !reservation.manifestFingerprint().equals(receipt.manifestFingerprint())
            || !json.fingerprint(receipt.command().customerContext()).equals(receipt.contextFingerprint())
            || !receipt.requestFingerprint().equals(commandFingerprint(requested.customerId(), requested.tenantId(),
                requested.deploymentId(), manifest, receipt.command()))
            || !json.fingerprint(requested).equals(json.fingerprint(effectiveContext)) || !requested.equals(effectiveContext)) {
            throw new AdmissionFailure(PRECONDITION_FAILED, "CUSTOMER_CAPACITY_RESERVATION_INVALID");
        }
        requireReleaseAuthorization(reservation, receipt.command());
        if (reservation.state() == State.ACTIVE && (requested.operation() == com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation.APPLY
            || requested.operation() == com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation.PROMOTION_APPLY)) {
            Long consumed = jdbc.queryForObject("""
                SELECT COUNT(*) FROM deployment_admission_release_bindings
                 WHERE reservation_id=? AND generation=? AND authorization_key=?
                """, Long.class, reservation.id(), reservation.generation(), requested.operationKey());
            if (consumed == null || consumed > 0 || currentReleaseId(reservation) == null) {
                throw new AdmissionFailure(PRECONDITION_FAILED, "CUSTOMER_CAPACITY_RESERVATION_INVALID");
            }
        }
    }

    /** A command-scoped projection, never a write to the live allocation during prerequisite/readiness checks. */
    private Reservation candidateAuthorization(Reservation allocation, AuthorizationReceipt receipt) {
        if (allocation.manifestFingerprint().equals(receipt.manifestFingerprint())) return allocation;
        Reserve command = receipt.command();
        if (command == null || command.customerContext() == null || !reviewedReapplyOperation(command.customerContext().operation())
            || !allocation.id().equals(receipt.reservationId()) || !allocation.generation().equals(receipt.generation())) {
            throw new AdmissionFailure(PRECONDITION_FAILED, "CUSTOMER_CAPACITY_RESERVATION_INVALID");
        }
        AdmissionContext candidate = context(allocation, command.manifestId());
        validateCustomerScope(candidate, command);
        if (!candidate.manifestFingerprint().equals(receipt.manifestFingerprint())
            || !json.fingerprint(command.customerContext()).equals(receipt.contextFingerprint())
            || !commandFingerprint(candidate.customerId(), candidate.tenantId(), candidate.deploymentId(), candidate.manifest(), command)
                .equals(receipt.requestFingerprint())) {
            throw new AdmissionFailure(PRECONDITION_FAILED, "CUSTOMER_CAPACITY_RESERVATION_INVALID");
        }
        Instant now = clock.instant();
        requireReviewedReapply(allocation, candidate, command, now);
        PrerequisiteReadback proof = reviewed("REAPPLY_PROOF", receipt.requestFingerprint(), PrerequisiteReadback.class);
        policy(candidate.policy(), now); grant(candidate.grant(), now); prerequisites(candidate, proof, now);
        Mutation projection = new Mutation(allocation);
        projection.manifestId = candidate.manifest().id(); projection.manifestFingerprint = candidate.manifestFingerprint();
        projection.bindingFingerprint = candidate.manifest().bindingFingerprint(); projection.prerequisiteReadback = proof;
        return projection.build(allocation, now, allocation.revision());
    }
    private void validateCustomerScope(AdmissionContext current, Reserve command) {
        var scope = command.customerContext();
        if (scope == null) return;
        Intent expectedIntent;
        try { expectedIntent = Intent.valueOf(scope.intent().name()); }
        catch (RuntimeException ex) { throw new AdmissionFailure(BAD_REQUEST, "CUSTOMER_ADMISSION_SCOPE_INVALID"); }
        if (scope.operation() == null || scope.limits() == null || scope.entitlementRevision() <= 0
            || !current.customerId().equals(scope.customerId()) || !current.tenantId().equals(scope.tenantId())
            || !current.deploymentId().equals(scope.deploymentId())
            || !current.target().targetProfileId().equals(scope.targetProfileId()) || current.target().intent() != expectedIntent
            || !command.operationKey().equals(scope.operationKey()) || scope.offerId() == null || scope.offerId().isBlank()
            || scope.offerFingerprint() == null || scope.offerFingerprint().isBlank()
            || scope.placementEvidenceId() == null || scope.placementEvidenceId().isBlank()) {
            throw new AdmissionFailure(BAD_REQUEST, "CUSTOMER_ADMISSION_SCOPE_INVALID");
        }
    }
    private void checkCapacity(FleetPolicy policy, List<Reservation> held, Vector extra, String targetId) {
        var fleet = new ArrayList<>(held.stream().map(Reservation::chargedDemand).toList());
        fleet.add(policy.fleet().baseline()); fleet.add(policy.fleet().headroom()); if (extra != null) fleet.add(extra);
        within(sum(fleet), policy.fleet().measured(), "FLEET_QUOTA_EXCEEDED");
        for (Reservation r : held) if (!policy.targets().containsKey(r.targetProfileId()))
            throw new AdmissionFailure(CONFLICT, "LIVE_TARGET_POLICY_REQUIRED");
        policy.targets().forEach((id, target) -> {
            var usage = new ArrayList<>(held.stream().filter(r -> id.equals(r.targetProfileId())).map(Reservation::chargedDemand).toList());
            usage.add(target.budget().baseline()); usage.add(target.budget().headroom());
            if (extra != null && id.equals(targetId)) usage.add(extra);
            within(sum(usage), target.budget().measured(), "TARGET_QUOTA_EXCEEDED");
        });
    }
    private void checkCustomer(CustomerGrant grant, List<Reservation> held) {
        within(sum(held.stream().map(Reservation::chargedDemand).toList()), grant.fleetLimit(), "CUSTOMER_FLEET_QUOTA_EXCEEDED");
        for (Reservation r : held) if (!grant.targetLimits().containsKey(r.targetProfileId()))
            throw new AdmissionFailure(CONFLICT, "LIVE_CUSTOMER_TARGET_QUOTA_REQUIRED");
        grant.targetLimits().forEach((id, limit) -> within(sum(held.stream().filter(r -> id.equals(r.targetProfileId()))
            .map(Reservation::chargedDemand).toList()), limit, "CUSTOMER_TARGET_QUOTA_EXCEEDED"));
    }
    List<Reservation> held() {
        return jdbc.query("SELECT payload_json FROM deployment_capacity_reservations WHERE state<>'RELEASED' ORDER BY id",
            (rs, row) -> json.read(rs.getString(1), Reservation.class));
    }
    List<Reservation> held(String deploymentId, String targetProfileId) {
        return jdbc.query("""
                SELECT payload_json FROM deployment_capacity_reservations
                 WHERE state<>'RELEASED' AND deployment_id=? AND target_profile_id=? ORDER BY id
                """,
            (rs, row) -> json.read(rs.getString(1), Reservation.class), deploymentId, targetProfileId);
    }
    List<Reservation> lifecycleBatch(int requestedSize) {
        return locked(() -> {
            int size = Math.max(1, Math.min(requestedSize, 256));
            String cursor = jdbc.queryForObject("SELECT lifecycle_cursor FROM deployment_admission_mutex WHERE id=1",
                String.class);
            List<Reservation> batch = new ArrayList<>();
            if (cursor != null) {
                batch.addAll(jdbc.query("""
                    SELECT payload_json FROM deployment_capacity_reservations
                     WHERE state NOT IN ('RELEASED','RETAINED_EXTERNAL') AND id>? ORDER BY id LIMIT ?
                    """, (rs, row) -> json.read(rs.getString(1), Reservation.class), cursor, size));
            }
            if (batch.size() < size) {
                int remaining = size - batch.size();
                String sql = cursor == null
                    ? "SELECT payload_json FROM deployment_capacity_reservations WHERE state NOT IN ('RELEASED','RETAINED_EXTERNAL') ORDER BY id LIMIT ?"
                    : "SELECT payload_json FROM deployment_capacity_reservations WHERE state NOT IN ('RELEASED','RETAINED_EXTERNAL') AND id<=? ORDER BY id LIMIT ?";
                if (cursor == null) {
                    batch.addAll(jdbc.query(sql, (rs, row) -> json.read(rs.getString(1), Reservation.class), remaining));
                } else {
                    batch.addAll(jdbc.query(sql, (rs, row) -> json.read(rs.getString(1), Reservation.class), cursor, remaining));
                }
            }
            if (!batch.isEmpty()) {
                jdbc.update("UPDATE deployment_admission_mutex SET lifecycle_cursor=? WHERE id=1",
                    batch.getLast().id());
            }
            return List.copyOf(batch);
        });
    }
    Reservation require(String id) {
        var rows = jdbc.query("SELECT payload_json FROM deployment_capacity_reservations WHERE id=?",
            (rs, row) -> json.read(rs.getString(1), Reservation.class), required(id, 64));
        if (rows.isEmpty()) throw new AdmissionFailure(NOT_FOUND, "RESERVATION_NOT_FOUND");
        return rows.get(0);
    }
    List<Reservation> customerPrerequisiteReservations(
        com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.AdmissionContext context) {
        return jdbc.query("""
            SELECT r.customer_id,r.deployment_id,r.target_profile_id,r.generation,r.state,r.payload_json,
                   a.payload_json AS authorization_payload
              FROM deployment_capacity_reservations r JOIN deployment_admission_authorizations a ON a.reservation_id=r.id
             WHERE a.customer_id=? AND a.deployment_id=? AND a.target_profile_id=? AND a.operation_key=?
             FOR UPDATE
            """, (rs, row) -> {
                Reservation reservation = json.read(rs.getString("payload_json"), Reservation.class);
                if (!rs.getString("customer_id").equals(reservation.customerId())
                    || !rs.getString("deployment_id").equals(reservation.deploymentId())
                    || !rs.getString("target_profile_id").equals(reservation.targetProfileId())
                    || !rs.getString("generation").equals(reservation.generation())
                    || !rs.getString("state").equals(reservation.state().name())) {
                    throw new AdmissionFailure(CONFLICT, "ADMISSION_RESERVATION_RECORD_MISMATCH");
                }
                return candidateAuthorization(reservation, json.read(rs.getString("authorization_payload"), AuthorizationReceipt.class));
            }, context.customerId(), context.deploymentId(), context.targetProfileId(), context.operationKey());
    }

    ReleasePrerequisiteAllocation customerPrerequisiteReleaseAllocation(
        com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.AdmissionContext requested) {
        var rows = jdbc.query("""
            SELECT b.*,r.payload_json AS reservation_payload,a.payload_json AS authorization_payload,
                   r.customer_id AS reservation_customer_id,r.deployment_id AS reservation_deployment_id,
                   r.target_profile_id AS reservation_target_profile_id,r.generation AS reservation_generation,
                   r.state AS reservation_state
              FROM deployment_admission_release_bindings b
              JOIN deployment_capacity_reservations r
                ON r.id=b.reservation_id AND r.generation=b.generation
              JOIN deployment_admission_authorizations a
                ON a.reservation_id=b.reservation_id AND a.generation=b.generation
               AND a.operation_key=b.authorization_key AND a.version_id=b.version_id
             WHERE b.release_id=? AND b.customer_id=? AND b.tenant_id=?
               AND b.deployment_id=? AND b.target_profile_id=?
               AND b.state IN ('ACTIVE','PROVISIONING','OUTCOME_UNKNOWN')
               AND a.operation IN ('APPLY','PROMOTION_APPLY')
             FOR UPDATE
            """, (rs, row) -> {
                Reservation reservation = json.read(rs.getString("reservation_payload"), Reservation.class);
                AuthorizationReceipt receipt = json.read(rs.getString("authorization_payload"), AuthorizationReceipt.class);
                ReleaseBinding binding = new ReleaseBinding(rs.getString("release_id"), rs.getString("reservation_id"),
                    rs.getString("generation"), rs.getString("authorization_key"), rs.getString("version_id"),
                    rs.getString("version_config_hash"), rs.getString("customer_id"), rs.getString("tenant_id"),
                    rs.getString("deployment_id"), rs.getString("target_profile_id"), rs.getString("state"));
                if (!rs.getString("reservation_customer_id").equals(reservation.customerId())
                    || !rs.getString("reservation_deployment_id").equals(reservation.deploymentId())
                    || !rs.getString("reservation_target_profile_id").equals(reservation.targetProfileId())
                    || !rs.getString("reservation_generation").equals(reservation.generation())
                    || !rs.getString("reservation_state").equals(reservation.state().name())
                    || receipt.command() == null || receipt.command().customerContext() == null) {
                    throw new AdmissionFailure(CONFLICT, "ADMISSION_RELEASE_PREREQUISITE_RECORD_MISMATCH");
                }
                return new ReleasePrerequisiteAllocation(reservation, binding, effectiveCustomerContext(reservation, receipt));
            }, requested.releaseId(), requested.customerId(), requested.tenantId(), requested.deploymentId(),
            requested.targetProfileId());
        if (rows.size() != 1) {
            throw new AdmissionFailure(PRECONDITION_FAILED, "ACTIVE_RELEASE_PREREQUISITE_EVIDENCE_REQUIRED");
        }
        ReleasePrerequisiteAllocation allocation = rows.getFirst();
        if (!requested.releaseId().equals(currentReleaseId(allocation.reservation()))) {
            throw new AdmissionFailure(PRECONDITION_FAILED, "ACTIVE_RELEASE_PREREQUISITE_EVIDENCE_REQUIRED");
        }
        return allocation;
    }

    Reservation activeCustomerReleaseReservation(String customerId, String tenantId, String deploymentId, String releaseId) {
        ReleaseBinding binding = releaseBinding(releaseId);
        if (binding == null || !customerId.equals(binding.customerId()) || !tenantId.equals(binding.tenantId())
            || !deploymentId.equals(binding.deploymentId()) || !"ACTIVE".equals(binding.state())) {
            throw new AdmissionFailure(PRECONDITION_FAILED, "ACTIVE_RELEASE_ALLOCATION_REQUIRED");
        }
        Reservation reservation = require(binding.reservationId());
        Manifest manifest = reviewed("MANIFEST", reservation.manifestId(), Manifest.class);
        var receipts = authorizationRows(customerId, deploymentId, binding.targetProfileId(), binding.authorizationKey());
        if (receipts.size() != 1) throw new AdmissionFailure(PRECONDITION_FAILED, "EXACT_APPLY_AUTHORIZATION_REQUIRED");
        AuthorizationReceipt receipt = receipts.getFirst();
        var command = receipt.command();
        var scope = command == null ? null : effectiveCustomerContext(reservation, receipt);
        if (scope == null || !java.util.Set.of(
                com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation.APPLY,
                com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation.PROMOTION_APPLY).contains(scope.operation())
            || !customerId.equals(reservation.customerId()) || !tenantId.equals(reservation.tenantId())
            || !deploymentId.equals(reservation.deploymentId()) || !binding.targetProfileId().equals(reservation.targetProfileId())
            || !binding.generation().equals(reservation.generation()) || !releaseId.equals(currentReleaseId(reservation))
            || !binding.versionId().equals(manifest.versionId()) || !binding.versionConfigHash().equals(manifest.versionConfigHash())
            || !reservation.id().equals(receipt.reservationId()) || !reservation.generation().equals(receipt.generation())
            || !reservation.manifestFingerprint().equals(receipt.manifestFingerprint())
            || !json.fingerprint(command.customerContext()).equals(receipt.contextFingerprint())
            || !receipt.requestFingerprint().equals(commandFingerprint(customerId, tenantId, deploymentId, manifest, command))
            || !customerId.equals(scope.customerId()) || !tenantId.equals(scope.tenantId())
            || !deploymentId.equals(scope.deploymentId()) || !binding.targetProfileId().equals(scope.targetProfileId())
            || !binding.authorizationKey().equals(scope.operationKey()) || reservation.state() != State.ACTIVE
            || reservation.expiryState() != ExpiryState.CURRENT || !reservation.expiresAt().isAfter(clock.instant())
            || reservation.workerToken() != null || reservation.failureCode() != null) {
            throw new AdmissionFailure(PRECONDITION_FAILED, "ACTIVE_RELEASE_ALLOCATION_INVALID");
        }
        policy(context(reservation).policy(), clock.instant());
        return reservation;
    }

    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void bindRelease(com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity deployment,
                            com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity version,
                            com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity release) {
        ReleaseBinding existing = releaseBinding(release.getId());
        if (existing != null) {
            if (!deployment.getId().equals(existing.deploymentId()) || !deployment.getCustomerId().equals(existing.customerId())
                || !deployment.getTenantId().equals(existing.tenantId()) || !release.getTargetProfileId().equals(existing.targetProfileId())
                || !version.getId().equals(existing.versionId()) || !version.getConfigHash().equals(existing.versionConfigHash())) {
                throw new AdmissionFailure(CONFLICT, "RELEASE_ALLOCATION_SCOPE_CHANGED");
            }
            return;
        }
        var receipts = jdbc.query("""
            SELECT a.payload_json FROM deployment_admission_authorizations a
             WHERE a.customer_id=? AND a.deployment_id=? AND a.target_profile_id=? AND a.version_id=?
               AND a.operation IN ('APPLY','PROMOTION_APPLY')
               AND NOT EXISTS (SELECT 1 FROM deployment_admission_release_bindings b
                 WHERE b.reservation_id=a.reservation_id AND b.generation=a.generation AND b.authorization_key=a.operation_key)
             FOR UPDATE
            """, (rs, row) -> json.read(rs.getString(1), AuthorizationReceipt.class),
            deployment.getCustomerId(), deployment.getId(), release.getTargetProfileId(), version.getId());
        if (receipts.size() != 1) throw new AdmissionFailure(PRECONDITION_FAILED, "EXACT_APPLY_AUTHORIZATION_REQUIRED");
        var receipt = receipts.getFirst();
        jdbc.queryForObject("SELECT id FROM deployment_capacity_reservations WHERE id=? FOR UPDATE", String.class, receipt.reservationId());
        Reservation allocation = require(receipt.reservationId());
        Reservation authorized = candidateAuthorization(allocation, receipt);
        AdmissionContext current = context(authorized);
        requireUsableAllocation(authorized, current, clock.instant());
        requireCustomerAuthorization(authorized, effectiveCustomerContext(authorized, receipt));
        if (initialEvidenceRenewal(authorized, receipt) != null) prerequisites(current, authorized.prerequisiteReadback(), clock.instant());
        String currentRelease = currentReleaseId(allocation);
        if (currentRelease != null && !"ACTIVE".equals(releaseBinding(currentRelease).state())) {
            throw new AdmissionFailure(CONFLICT, "ALLOCATION_RELEASE_ALREADY_IN_PROGRESS");
        }
        if (!version.getId().equals(current.manifest().versionId()) || !version.getConfigHash().equals(current.manifest().versionConfigHash())) {
            throw new AdmissionFailure(CONFLICT, "ALLOCATION_VERSION_CHANGED");
        }
        if (!allocation.manifestFingerprint().equals(authorized.manifestFingerprint())) {
            Mutation committed = new Mutation(allocation);
            committed.manifestId = authorized.manifestId(); committed.manifestFingerprint = authorized.manifestFingerprint();
            committed.bindingFingerprint = authorized.bindingFingerprint(); committed.prerequisiteReadback = authorized.prerequisiteReadback();
            allocation = save(allocation, committed, "ALLOCATION_REVIEWED_VERSION_COMMITTED", "system:admission-apply", clock.instant());
        }
        jdbc.update("""
            INSERT INTO deployment_admission_release_bindings
             (release_id,reservation_id,generation,authorization_key,version_id,version_config_hash,
              customer_id,tenant_id,deployment_id,target_profile_id,state,created_at,updated_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
            """, release.getId(), allocation.id(), allocation.generation(), receipt.command().operationKey(), version.getId(),
            version.getConfigHash(), deployment.getCustomerId(), deployment.getTenantId(), deployment.getId(), release.getTargetProfileId(),
            "QUEUED", time(clock.instant()), time(clock.instant()));
    }

    KnownNoEffectSettlementContracts.Plan planKnownNoEffect(String customerId, String deploymentId, String reservationId,
        KnownNoEffectSettlementContracts.PlanRequest request, String actor,
        java.util.function.BiConsumer<Reservation, KnownNoEffectSettlementContracts.Command> authorize,
        com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationIntegrity integrationIntegrity) {
        required(customerId, 64); required(deploymentId, 64); required(reservationId, 64); required(actor, 255);
        if (request == null || authorize == null || integrationIntegrity == null)
            throw new AdmissionFailure(BAD_REQUEST, "NO_EFFECT_SETTLEMENT_PLAN_REQUIRED");
        required(request.operationKey(), 128); required(request.reason(), 1000);
        return locked(() -> {
            try {
                if (jdbc.query("SELECT id FROM platform_deployments WHERE id=? FOR UPDATE NOWAIT", (rs, row) -> rs.getString(1), deploymentId).size() != 1)
                    throw new AdmissionFailure(NOT_FOUND, "RESERVATION_NOT_FOUND");
                var authorizations = jdbc.query("SELECT payload_json FROM deployment_admission_authorizations WHERE reservation_id=? ORDER BY operation_key FOR UPDATE NOWAIT",
                    (rs, row) -> json.read(rs.getString(1), AuthorizationReceipt.class), reservationId);
                if (jdbc.query("SELECT id FROM deployment_capacity_reservations WHERE id=? FOR UPDATE NOWAIT", (rs, row) -> rs.getString(1), reservationId).size() != 1)
                    throw new AdmissionFailure(NOT_FOUND, "RESERVATION_NOT_FOUND");
                Reservation r = require(reservationId);
                if (!customerId.equals(r.customerId()) || !deploymentId.equals(r.deploymentId()))
                    throw new AdmissionFailure(NOT_FOUND, "RESERVATION_NOT_FOUND");
                var context = context(r);
                var manifest = context.manifest();
                var bindings = jdbc.query("SELECT release_id,authorization_key FROM deployment_admission_release_bindings WHERE reservation_id=? ORDER BY release_id",
                    (rs, row) -> List.of(rs.getString(1), rs.getString(2)), reservationId);
                var expected = bindings.stream().map(binding -> {
                    var matches = authorizations.stream().filter(a -> a.command() != null && binding.get(1).equals(a.command().operationKey())).toList();
                    if (matches.size() != 1) throw new AdmissionFailure(CONFLICT, "NO_EFFECT_SETTLEMENT_AUTHORIZATION_CHANGED");
                    return new KnownNoEffectSettlementContracts.FailedRelease(binding.get(0), binding.get(1), matches.getFirst().requestFingerprint());
                }).toList();
                var withoutConsumers = new KnownNoEffectSettlementContracts.Command(r.fence(), request.operationKey(), r.manifestFingerprint(),
                    manifest.versionId(), manifest.versionConfigHash(), r.bindingFingerprint(), expected, request.reason(), List.of());
                validateKnownNoEffect(withoutConsumers);
                authorize.accept(r, withoutConsumers);
                requireKnownNoEffectState(r);
                requireEffectivePolicy(r, context);
                for (var authorization : authorizations) requireCustomerAuthorization(r, effectiveCustomerContext(r, authorization));
                var configuration = AdmissionUnusedConsumerDetachment.readForInitialEvidenceRenewal(jdbc, json, r,
                    authorizations.getFirst().command().customerContext());
                var command = new KnownNoEffectSettlementContracts.Command(r.fence(), request.operationKey(), r.manifestFingerprint(),
                    manifest.versionId(), manifest.versionConfigHash(), r.bindingFingerprint(), expected, request.reason(), configuration.assignments());
                var evidence = new KnownNoEffectSettlementEvidence(jdbc, json, integrationIntegrity).read(r, command, authorizations);
                return new KnownNoEffectSettlementContracts.Plan(command,
                    json.fingerprint(Map.of("effects", evidence, "configuration", configuration.fingerprint())));
            } catch (org.springframework.dao.DataAccessException busy) {
                throw unusedCancellationLockFailure(busy, "NO_EFFECT_SETTLEMENT_BUSY");
            }
        });
    }

    private void requireKnownNoEffectState(Reservation r) {
        if (r.state() != State.RESERVED || r.workerToken() != null || r.retirementOperationId() != null
            || r.drainDeadline() != null || r.recoveryUntil() != null || r.failureCode() != null
            || r.expiryState() != ExpiryState.CURRENT || r.attempts() != 0 || r.nextRetryAt() != null
            || r.retentionEnvelope() != null || r.retentionSettlement() != null)
            throw new AdmissionFailure(CONFLICT, "NO_EFFECT_SETTLEMENT_CURRENT_PRE_PROVIDER_RESERVATION_REQUIRED");
    }

    KnownNoEffectSettlementContracts.Receipt settleKnownNoEffect(String customerId, String deploymentId,
        String reservationId, KnownNoEffectSettlementContracts.Command command, String actor,
        java.util.function.Consumer<Reservation> authorize, AdmissionUnusedConsumerDetachment detachment,
        com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationIntegrity integrationIntegrity) {
        validateKnownNoEffect(command);
        required(customerId, 64); required(deploymentId, 64); required(reservationId, 64); required(actor, 255);
        if (!reservationId.equals(command.fence().reservationId()) || authorize == null || integrationIntegrity == null)
            throw new AdmissionFailure(BAD_REQUEST, "NO_EFFECT_SETTLEMENT_SCOPE_REQUIRED");
        String fingerprint = json.fingerprint(Map.of("customerId", customerId, "deploymentId", deploymentId,
            "reservationId", reservationId, "actorId", actor, "command", command));
        return locked(() -> {
            try {
                // Apply may own these rows before entering the ledger. Never wait on that inverse lock order.
                if (jdbc.query("SELECT id FROM platform_deployments WHERE id=? FOR UPDATE NOWAIT",
                    (rs, row) -> rs.getString(1), deploymentId).size() != 1)
                    throw new AdmissionFailure(NOT_FOUND, "RESERVATION_NOT_FOUND");
                var authorizations = jdbc.query("SELECT payload_json FROM deployment_admission_authorizations WHERE reservation_id=? ORDER BY operation_key FOR UPDATE NOWAIT",
                    (rs, row) -> json.read(rs.getString(1), AuthorizationReceipt.class), reservationId);
                if (jdbc.query("SELECT id FROM deployment_capacity_reservations WHERE id=? FOR UPDATE NOWAIT",
                    (rs, row) -> rs.getString(1), reservationId).size() != 1)
                    throw new AdmissionFailure(NOT_FOUND, "RESERVATION_NOT_FOUND");
                Reservation observed = require(reservationId);
                if (!customerId.equals(observed.customerId()) || !deploymentId.equals(observed.deploymentId())
                    || !command.fence().generation().equals(observed.generation()))
                    throw new AdmissionFailure(NOT_FOUND, "RESERVATION_NOT_FOUND");
                for (var row : Map.of("platform_customers", customerId, "platform_customer_tenants", observed.tenantId(),
                    "platform_deployment_versions", command.versionId(), "deployment_target_profiles", observed.targetProfileId()).entrySet()) {
                    if (jdbc.query("SELECT id FROM " + row.getKey() + " WHERE id=? FOR UPDATE NOWAIT",
                        (rs, index) -> rs.getString(1), row.getValue()).size() != 1)
                        throw new AdmissionFailure(CONFLICT, "NO_EFFECT_SETTLEMENT_OWNERSHIP_CHANGED");
                }
                authorize.accept(observed);
                String receiptKey = json.fingerprint(Map.of("reservation", reservationId, "generation", observed.generation()));
                var existing = jdbc.queryForList("SELECT record_key FROM deployment_admission_reviews WHERE kind='NO_EFFECT_SETTLE' AND record_key=?", String.class, receiptKey);
                if (!existing.isEmpty()) {
                    var receipt = verifiedReview("NO_EFFECT_SETTLE", receiptKey, KnownNoEffectSettlementContracts.Receipt.class);
                    if (!fingerprint.equals(receipt.commandFingerprint()) || !json.fingerprint(observed).equals(json.fingerprint(receipt.reservation()))
                        || observed.state() != State.RELEASED || observed.expiryState() != ExpiryState.RELEASED)
                        throw new AdmissionFailure(CONFLICT, "NO_EFFECT_SETTLEMENT_REPLAY_CHANGED");
                    return receipt;
                }
                Reservation r = fenced(command.fence());
                requireKnownNoEffectState(r);
                // Completed receipt recovery needs no continuity for a released allocation; new settlement still does.
                AdmissionContext current = context(r);
                requireEffectivePolicy(r, current);
                Manifest manifest = current.manifest();
                if (!command.manifestFingerprint().equals(r.manifestFingerprint())
                    || !command.manifestFingerprint().equals(json.fingerprint(manifest))
                    || !command.versionId().equals(manifest.versionId()) || !command.versionConfigHash().equals(manifest.versionConfigHash())
                    || !command.bindingFingerprint().equals(r.bindingFingerprint())
                    || !command.bindingFingerprint().equals(manifest.bindingFingerprint()))
                    throw new AdmissionFailure(CONFLICT, "NO_EFFECT_SETTLEMENT_MANIFEST_CHANGED");
                String operationIndex = json.fingerprint(Map.of("customer", customerId, "deployment", deploymentId,
                    "target", r.targetProfileId(), "operationKey", command.operationKey()));
                if (!jdbc.queryForList("SELECT record_key FROM deployment_admission_reviews WHERE kind='NO_EFFECT_OP' AND record_key=?", String.class, operationIndex).isEmpty())
                    throw new AdmissionFailure(CONFLICT, "NO_EFFECT_SETTLEMENT_OPERATION_REUSED");
                if (authorizations.isEmpty()) throw new AdmissionFailure(CONFLICT, "NO_EFFECT_SETTLEMENT_AUTHORIZATION_REQUIRED");
                for (var authorization : authorizations) {
                    if (authorization.command() == null || authorization.command().customerContext() == null
                        || !r.id().equals(authorization.reservationId()) || !r.generation().equals(authorization.generation())
                        || !r.manifestFingerprint().equals(authorization.manifestFingerprint()))
                        throw new AdmissionFailure(CONFLICT, "NO_EFFECT_SETTLEMENT_AUTHORIZATION_CHANGED");
                    requireCustomerAuthorization(r, effectiveCustomerContext(r, authorization));
                }
                var configuration = AdmissionUnusedConsumerDetachment.readForInitialEvidenceRenewal(jdbc, json, r,
                    authorizations.getFirst().command().customerContext());
                if (!configuration.assignments().equals(command.consumerDetachments()))
                    throw new AdmissionFailure(CONFLICT, "NO_EFFECT_SETTLEMENT_EXPLICIT_CONSUMER_EXPECTATIONS_REQUIRED");
                var evidence = new KnownNoEffectSettlementEvidence(jdbc, json, integrationIntegrity);
                var proof = evidence.read(r, command, authorizations);
                List<UnusedConsumerDetachmentReceipt> detachments = List.of();
                if (!command.consumerDetachments().isEmpty()) {
                    if (detachment == null) throw new AdmissionFailure(CONFLICT, "NO_EFFECT_SETTLEMENT_DETACHMENT_UNAVAILABLE");
                    detachments = detachment.detachForKnownNoEffectSettlement(r, command.consumerDetachments(), fingerprint);
                }
                if (!proof.equals(evidence.read(r, command, authorizations)))
                    throw new AdmissionFailure(CONFLICT, "NO_EFFECT_SETTLEMENT_EFFECT_EVIDENCE_CHANGED");
                evidence.requireDetachedConsumers(r, command.consumerDetachments(), detachments);
                var completeInventory = new java.util.TreeMap<>(proof.inventoryFingerprints());
                completeInventory.put("explicitConfigurationBefore", configuration.fingerprint());
                proof = new KnownNoEffectSettlementContracts.Proof(proof.executionBoundary(), proof.releases(), proof.authorizations(),
                    proof.retainedExternalBindings(), completeInventory);
                Instant now = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
                String id = uuid();
                Mutation m = new Mutation(r); m.state = State.RELEASED; m.expiryState = ExpiryState.RELEASED;
                m.evidence = "known-no-effect-settlement:" + id;
                Reservation settled = save(r, m, "SETTLED_KNOWN_NO_EFFECT", actor, now);
                var receipt = new KnownNoEffectSettlementContracts.Receipt(id, customerId, r.tenantId(), deploymentId,
                    r.targetProfileId(), actor, fingerprint, command, proof, KnownNoEffectSettlementContracts.Outcome.SETTLED_KNOWN_NO_EFFECT,
                    false, now, settled, detachments);
                review("NO_EFFECT_SETTLE", receiptKey, receipt, actor, now);
                review("NO_EFFECT_OP", operationIndex, receipt, actor, now);
                return receipt;
            } catch (org.springframework.dao.DataAccessException busy) {
                throw unusedCancellationLockFailure(busy, "NO_EFFECT_SETTLEMENT_BUSY");
            }
        });
    }

    private void validateKnownNoEffect(KnownNoEffectSettlementContracts.Command command) {
        if (command == null || command.fence() == null || command.fence().revision() < 0
            || command.failedReleases() == null || command.failedReleases().isEmpty() || command.failedReleases().size() > 32
            || command.failedReleases().stream().map(KnownNoEffectSettlementContracts.FailedRelease::releaseId).distinct().count() != command.failedReleases().size()
            || command.failedReleases().stream().map(KnownNoEffectSettlementContracts.FailedRelease::authorizationOperationKey).distinct().count() != command.failedReleases().size())
            throw new AdmissionFailure(BAD_REQUEST, "NO_EFFECT_SETTLEMENT_COMMAND_REQUIRED");
        for (String value : List.of(required(command.fence().reservationId(), 64), required(command.fence().generation(), 64),
            required(command.operationKey(), 128), required(command.manifestFingerprint(), 64), required(command.versionId(), 64),
            required(command.versionConfigHash(), 255), required(command.bindingFingerprint(), 64), required(command.reason(), 1000)))
            if (value.chars().anyMatch(Character::isISOControl)) throw new AdmissionFailure(BAD_REQUEST, "NO_EFFECT_SETTLEMENT_COMMAND_INVALID");
        for (var release : command.failedReleases()) {
            required(release.releaseId(), 64); required(release.authorizationOperationKey(), 128);
            required(release.authorizationRequestFingerprint(), 64);
        }
        // Share only the existing strict structural validation, not its unused-state/evidence behavior.
        validateUnusedCancellation(new CancelUnusedReservation(command.fence(), command.operationKey(),
            command.failedReleases().getFirst().authorizationOperationKey(), command.failedReleases().getFirst().authorizationRequestFingerprint(),
            command.manifestFingerprint(), command.versionId(), command.versionConfigHash(), command.bindingFingerprint(), command.consumerDetachments()));
    }

    UnusedReservationCancellationReceipt cancelUnused(String deploymentId, String targetId,
                                                       CancelUnusedReservation command, String actor) {
        return cancelUnused(deploymentId, targetId, command, actor, null);
    }

    UnusedReservationCancellationReceipt cancelUnused(String deploymentId, String targetId,
            CancelUnusedReservation command, String actor, AdmissionUnusedConsumerDetachment consumerDetachment) {
        validateUnusedCancellation(command);
        required(deploymentId, 64); required(targetId, 64); required(actor, 255);
        String fingerprint = json.fingerprint(Map.of("deploymentId", deploymentId, "targetProfileId", targetId, "command", command));
        return locked(() -> {
            // Apply owns deployment first and may subsequently need the ledger. Never wait here while owning its mutex.
            try {
                var deployment = jdbc.query("SELECT id FROM platform_deployments WHERE id=? FOR UPDATE NOWAIT",
                    (rs, row) -> rs.getString(1), deploymentId);
                if (deployment.isEmpty()) throw new AdmissionFailure(NOT_FOUND, "DEPLOYMENT_NOT_FOUND");
            } catch (org.springframework.dao.DataAccessException busy) {
                throw unusedCancellationLockFailure(busy, "UNUSED_CANCELLATION_DEPLOYMENT_BUSY");
            }
            // bindRelease locks authorizations before the reservation. Never invert that order here.
            List<AuthorizationReceipt> authorizations;
            List<String> ids;
            try {
                authorizations = jdbc.query("""
                    SELECT payload_json FROM deployment_admission_authorizations WHERE reservation_id=?
                     ORDER BY operation_key FOR UPDATE NOWAIT
                    """, (rs, row) -> json.read(rs.getString(1), AuthorizationReceipt.class), command.fence().reservationId());
                ids = jdbc.query("SELECT id FROM deployment_capacity_reservations WHERE id=? FOR UPDATE NOWAIT",
                    (rs, row) -> rs.getString(1), command.fence().reservationId());
            } catch (org.springframework.dao.DataAccessException busy) {
                throw unusedCancellationLockFailure(busy, "UNUSED_CANCELLATION_AUTHORIZATION_BUSY");
            }
            if (ids.size() != 1) throw new AdmissionFailure(NOT_FOUND, "RESERVATION_NOT_FOUND");
            Reservation observed = require(command.fence().reservationId());
            if (!deploymentId.equals(observed.deploymentId()) || !targetId.equals(observed.targetProfileId()))
                throw new AdmissionFailure(NOT_FOUND, "RESERVATION_NOT_FOUND");
            var prior = jdbc.query("""
                SELECT payload_json FROM deployment_admission_unused_cancellations WHERE reservation_id=? AND generation=?
                """, (rs, row) -> json.read(rs.getString(1), UnusedReservationCancellationReceipt.class),
                observed.id(), command.fence().generation());
            if (!prior.isEmpty()) {
                var receipt = prior.getFirst();
                if (!fingerprint.equals(receipt.commandFingerprint())) throw new AdmissionFailure(CONFLICT, "CANCELLATION_OPERATION_REUSED");
                return receipt;
            }
            Reservation reservation = fenced(command.fence());
            if (reservation.state() != State.RESERVED || reservation.expiryState() != ExpiryState.CURRENT
                || reservation.workerToken() != null || reservation.retirementOperationId() != null
                || reservation.drainDeadline() != null || reservation.recoveryUntil() != null
                || reservation.failureCode() != null || reservation.attempts() != 0)
                throw new AdmissionFailure(CONFLICT, "UNUSED_CANCELLATION_REQUIRES_UNUSED_RESERVATION");
            Manifest manifest = reviewed("MANIFEST", reservation.manifestId(), Manifest.class);
            if (!reservation.manifestFingerprint().equals(command.manifestFingerprint())
                || !json.fingerprint(manifest).equals(command.manifestFingerprint())
                || !reservation.bindingFingerprint().equals(command.bindingFingerprint())
                || !manifest.bindingFingerprint().equals(command.bindingFingerprint())
                || !manifest.deploymentId().equals(deploymentId) || !manifest.targetProfileId().equals(targetId)
                || !manifest.versionId().equals(command.versionId()) || !manifest.versionConfigHash().equals(command.versionConfigHash()))
                throw new AdmissionFailure(CONFLICT, "UNUSED_CANCELLATION_SCOPE_CHANGED");
            AuthorizationReceipt selected = null;
            for (var authorization : authorizations) {
                if (!reservation.id().equals(authorization.reservationId()) || !reservation.generation().equals(authorization.generation())
                    || !reservation.manifestFingerprint().equals(authorization.manifestFingerprint())
                    || authorization.command() == null || authorization.command().customerContext() == null)
                    throw new AdmissionFailure(CONFLICT, "UNUSED_CANCELLATION_AUTHORIZATION_CHANGED");
                requireCustomerAuthorization(reservation, effectiveCustomerContext(reservation, authorization));
                if (command.authorizationOperationKey().equals(authorization.command().operationKey())) selected = authorization;
            }
            if (selected == null || !command.authorizationRequestFingerprint().equals(selected.requestFingerprint()))
                throw new AdmissionFailure(CONFLICT, "UNUSED_CANCELLATION_AUTHORIZATION_CHANGED");
            var localEvidence = new AdmissionUnusedReservationEvidence(jdbc, json);
            UnusedReservationEvidence evidence;
            List<UnusedConsumerDetachmentReceipt> detachments = List.of();
            try {
                evidence = localEvidence.read(reservation, manifest,
                    command.consumerDetachments().stream().map(UnusedConsumerDetachment::consumerEntityId).toList());
                if (!command.consumerDetachments().isEmpty()) {
                    if (consumerDetachment == null) throw new AdmissionFailure(CONFLICT, "UNUSED_CONSUMER_DETACHMENT_UNAVAILABLE");
                    detachments = consumerDetachment.detach(reservation, command.consumerDetachments(), fingerprint);
                }
                // All assignments must now be absent, and every other no-effect input must remain identical.
                if (!evidence.equals(localEvidence.read(reservation, manifest)))
                    throw new AdmissionFailure(CONFLICT, "UNUSED_CANCELLATION_EVIDENCE_CHANGED");
            }
            catch (org.springframework.dao.DataAccessException busy) {
                throw unusedCancellationLockFailure(busy, "UNUSED_CANCELLATION_BINDING_BUSY");
            }
            Instant now = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
            localEvidence.retainUnusedBindings(reservation, evidence, now);
            String receiptId = uuid();
            Mutation mutation = new Mutation(reservation);
            mutation.state = State.RELEASED; mutation.expiryState = ExpiryState.RELEASED;
            mutation.worker = null; mutation.nextRetry = null; mutation.failure = null;
            mutation.evidence = "unused-reservation-cancellation:" + receiptId;
            Reservation cancelled = save(reservation, mutation, "CANCELLED_UNUSED_NO_EFFECT", actor, now);
            var receipt = new UnusedReservationCancellationReceipt(receiptId, command.operationKey(), fingerprint,
                command.authorizationOperationKey(), manifest.versionId(), manifest.versionConfigHash(),
                CancellationOutcome.CANCELLED_UNUSED_NO_EFFECT, false, actor, now, evidence, detachments, cancelled);
            jdbc.update("""
                INSERT INTO deployment_admission_unused_cancellations
                 (reservation_id,generation,id,operation_key,command_fingerprint,payload_json,cancelled_at)
                VALUES (?,?,?,?,?,?,?)
                """, reservation.id(), reservation.generation(), receiptId, command.operationKey(), fingerprint, json.write(receipt), time(now));
            return receipt;
        });
    }

    private void validateUnusedCancellation(CancelUnusedReservation command) {
        if (command == null || command.fence() == null || command.fence().revision() < 0)
            throw new AdmissionFailure(BAD_REQUEST, "UNUSED_CANCELLATION_COMMAND_REQUIRED");
        required(command.fence().reservationId(), 64); required(command.fence().generation(), 64);
        for (String value : new String[]{command.operationKey(), command.authorizationOperationKey()}) required(value, 128);
        required(command.authorizationRequestFingerprint(), 64); required(command.manifestFingerprint(), 64);
        required(command.versionId(), 64); required(command.versionConfigHash(), 255); required(command.bindingFingerprint(), 64);
        for (String value : new String[]{command.operationKey(), command.authorizationOperationKey(),
            command.authorizationRequestFingerprint(), command.manifestFingerprint(), command.versionId(),
            command.versionConfigHash(), command.bindingFingerprint(), command.fence().reservationId(), command.fence().generation()})
            if (value.chars().anyMatch(Character::isISOControl))
                throw new AdmissionFailure(BAD_REQUEST, "UNUSED_CANCELLATION_COMMAND_INVALID");
        if (command.consumerDetachments().size() > 32 || command.consumerDetachments().stream()
            .map(UnusedConsumerDetachment::consumerEntityId).distinct().count() != command.consumerDetachments().size())
            throw new AdmissionFailure(BAD_REQUEST, "UNUSED_CONSUMER_EXPECTATIONS_INVALID");
        for (var consumer : command.consumerDetachments()) {
            required(consumer.consumerEntityId(), 64);
            if (consumer.expectedBindingRevision() < 0 || consumer.draftWorkspaces() == null || consumer.draftWorkspaces().size() > 64
                || consumer.draftWorkspaces().stream().map(DraftWorkspaceExpectation::installationEntityId).distinct().count() != consumer.draftWorkspaces().size())
                throw new AdmissionFailure(BAD_REQUEST, "UNUSED_CONSUMER_EXPECTATIONS_INVALID");
            for (var workspace : consumer.draftWorkspaces()) {
                required(workspace.installationEntityId(), 64);
                if (workspace.expectedRowVersion() < 0) throw new AdmissionFailure(BAD_REQUEST, "UNUSED_CONSUMER_EXPECTATIONS_INVALID");
            }
        }
    }

    private RuntimeException unusedCancellationLockFailure(org.springframework.dao.DataAccessException failure, String code) {
        // Spring's default translator does not consistently classify PostgreSQL NOWAIT (55P03).
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.sql.SQLException sql && ("55P03".equals(sql.getSQLState())
                || "HYT00".equals(sql.getSQLState()) && sql.getErrorCode() == 50200))
                return new AdmissionFailure(CONFLICT, code);
        }
        return failure;
    }

    ReleaseBinding releaseBinding(String releaseId) {
        var rows = jdbc.query("SELECT * FROM deployment_admission_release_bindings WHERE release_id=?",
            (rs, row) -> new ReleaseBinding(rs.getString("release_id"), rs.getString("reservation_id"), rs.getString("generation"),
                rs.getString("authorization_key"), rs.getString("version_id"), rs.getString("version_config_hash"),
                rs.getString("customer_id"), rs.getString("tenant_id"), rs.getString("deployment_id"),
                rs.getString("target_profile_id"), rs.getString("state")), releaseId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    String currentReleaseId(Reservation reservation) {
        var rows = jdbc.query("""
            SELECT release_id FROM deployment_admission_release_bindings
             WHERE reservation_id=? AND generation=? AND state NOT IN ('SUPERSEDED','NO_EFFECT')
             ORDER BY CASE WHEN state='ACTIVE' THEN 1 ELSE 0 END,created_at DESC,release_id DESC
            """, (rs, row) -> rs.getString(1), reservation.id(), reservation.generation());
        return rows.isEmpty() ? null : rows.getFirst();
    }

    List<String> unsettledReleases(int limit) {
        return locked(() -> {
            var batch = jdbc.query("""
                SELECT release_id FROM deployment_admission_release_bindings WHERE state IN ('QUEUED','PROVISIONING','OUTCOME_UNKNOWN')
                 ORDER BY updated_at,release_id LIMIT ?
                """, (rs, row) -> rs.getString(1), Math.max(1, Math.min(limit, 32)));
            for (String releaseId : batch) jdbc.update("""
                UPDATE deployment_admission_release_bindings SET updated_at=?
                 WHERE release_id=? AND state IN ('QUEUED','PROVISIONING','OUTCOME_UNKNOWN')
                """, time(clock.instant()), releaseId);
            return batch;
        });
    }

    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void releaseBindingState(String releaseId, String state) {
        ReleaseBinding binding = releaseBinding(releaseId);
        if (binding == null) throw new AdmissionFailure(PRECONDITION_FAILED, "RELEASE_ALLOCATION_REQUIRED");
        // A terminal effect boundary cannot be resurrected by an observer that read before recovery committed.
        int changed = jdbc.update("UPDATE deployment_admission_release_bindings SET state=?,updated_at=? WHERE release_id=? AND state NOT IN ('NO_EFFECT','SUPERSEDED')",
            state, time(clock.instant()), releaseId);
        if (changed != 1) return;
        if ("ACTIVE".equals(state)) jdbc.update("""
            UPDATE deployment_admission_release_bindings SET state='SUPERSEDED'
             WHERE reservation_id=? AND generation=? AND release_id<>? AND state='ACTIVE'
            """, binding.reservationId(), binding.generation(), releaseId);
    }

    Reservation acceptActivity(Fence fence, ReleaseBinding binding, ActivityReadback readback) {
        return locked(() -> {
            Instant now = clock.instant();
            Reservation reservation = fenced(fence);
            FleetPolicy policy = reviewed("POLICY", reservation.policyId(), FleetPolicy.class);
            if (!binding.reservationId().equals(reservation.id()) || !binding.generation().equals(reservation.generation())
                || !binding.releaseId().equals(currentReleaseId(reservation)) || reservation.state() != State.ACTIVE
                || reservation.intent() != Intent.CUSTOMER_STAGING || reservation.expiryState() == ExpiryState.CLAIMED
                || reservation.expiryState() == ExpiryState.HANDED_OFF || reservation.expiryState() == ExpiryState.RELEASED) {
                throw new AdmissionFailure(CONFLICT, "ACTIVITY_ALLOCATION_CHANGED");
            }
            if (readback == null || !"loomai-runtime-admission-activity-v1".equals(readback.schemaVersion())
                || !reservation.deploymentId().equals(readback.deploymentId()) || !reservation.tenantId().equals(readback.tenantId())
                || !reservation.targetProfileId().equals(readback.targetProfileId()) || !binding.releaseId().equals(readback.releaseId())
                || readback.signals() == null || readback.signals().size() > 3) {
                throw new AdmissionFailure(PRECONDITION_FAILED, "ACTIVITY_READBACK_SCOPE_INVALID");
            }
            fresh(readback.observedAt(), now, policy.lifecycle().readbackMaxAgeSeconds());
            java.util.Set<Activity> seen = java.util.EnumSet.noneOf(Activity.class);
            Instant latest = reservation.lastActivityAt();
            for (ActivitySignal signal : readback.signals()) {
                if (signal == null || signal.kind() == null || signal.kind() == Activity.CONFIGURATION_WRITE || !seen.add(signal.kind())
                    || signal.sequence() < 1 || signal.acceptedAt() == null || signal.acceptedAt().isAfter(readback.observedAt())) {
                    throw new AdmissionFailure(PRECONDITION_FAILED, "ACTIVITY_SIGNAL_INVALID");
                }
                var prior = jdbc.query("""
                    SELECT sequence,accepted_at FROM deployment_admission_activity_watermarks
                     WHERE reservation_id=? AND release_id=? AND activity=?
                    """, (rs, row) -> new ActivitySignal(signal.kind(), rs.getLong(1), rs.getTimestamp(2).toInstant()),
                    reservation.id(), binding.releaseId(), signal.kind().name());
                if (!prior.isEmpty()) {
                    ActivitySignal previous = prior.getFirst();
                    if (signal.sequence() < previous.sequence() || signal.acceptedAt().isBefore(previous.acceptedAt())
                        || signal.sequence() == previous.sequence() && !signal.acceptedAt().equals(previous.acceptedAt())) {
                        throw new AdmissionFailure(CONFLICT, "ACTIVITY_SIGNAL_REGRESSED");
                    }
                    if (signal.sequence() == previous.sequence()) continue;
                    jdbc.update("""
                        UPDATE deployment_admission_activity_watermarks SET sequence=?,accepted_at=?
                         WHERE reservation_id=? AND release_id=? AND activity=?
                        """, signal.sequence(), time(signal.acceptedAt()), reservation.id(), binding.releaseId(), signal.kind().name());
                } else {
                    jdbc.update("""
                        INSERT INTO deployment_admission_activity_watermarks(reservation_id,release_id,activity,sequence,accepted_at)
                         VALUES (?,?,?,?,?)
                        """, reservation.id(), binding.releaseId(), signal.kind().name(), signal.sequence(), time(signal.acceptedAt()));
                }
                if (signal.acceptedAt().isAfter(latest)) latest = signal.acceptedAt();
            }
            if (!latest.isAfter(reservation.lastActivityAt())) return reservation;
            Instant requestedExpiry = latest.plusSeconds(policy.lifecycle().inactivitySeconds());
            if (requestedExpiry.isBefore(reservation.expiresAt())) requestedExpiry = reservation.expiresAt();
            Instant expires = boundedExpiry(reservation, requestedExpiry, policy.lifecycle());
            Instant grantExpiry = customerGrant(reservation.customerId()).validUntil();
            if (expires.isAfter(grantExpiry)) expires = grantExpiry;
            if (!expires.isAfter(now)) return reservation;
            Mutation mutation = new Mutation(reservation);
            mutation.lastActivity = latest;
            mutation.expires = expires;
            mutation.expiryState = ExpiryState.CURRENT;
            mutation.noticeCycle = uuid(); mutation.attempts = 0; mutation.nextRetry = null; mutation.failure = null;
            mutation.evidence = "runtime-accepted-work:" + json.fingerprint(readback);
            return save(reservation, mutation, "ACCEPTED_RUNTIME_ACTIVITY", "system:admission-activity", now);
        });
    }

    Reservation extensionReplay(String reservationId, String key, String fingerprint) {
        var rows = jdbc.query("""
            SELECT request_fingerprint,result_json FROM deployment_admission_extension_commands
             WHERE reservation_id=? AND operation_key=?
            """, (rs, row) -> new String[]{rs.getString(1), rs.getString(2)}, reservationId, key);
        if (rows.isEmpty()) return null;
        if (!fingerprint.equals(rows.getFirst()[0])) throw new AdmissionFailure(CONFLICT, "OPERATION_KEY_REUSED");
        return json.read(rows.getFirst()[1], Reservation.class);
    }

    Reservation extendCommand(Fence fence, boolean keep, CustomerGrant grant, String key, String fingerprint,
                               String actor, Instant now) {
        return locked(() -> extendAuthorized(fence, keep, grant, key, fingerprint, actor, now));
    }

    /** The owning authorization transaction already locks the exact reservation and entitlement. */
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public Reservation extendAuthorized(Fence fence, boolean keep, CustomerGrant grant, String key, String fingerprint,
                                         String actor, Instant now) {
            Reservation replay = extensionReplay(fence.reservationId(), key, fingerprint);
            if (replay != null) return replay;
            jdbc.queryForObject("SELECT customer_id FROM deployment_admission_grants WHERE customer_id=? FOR UPDATE", String.class, grant.customerId());
            Reservation result = extendLocked(fence, keep, grant, "customer-extension:" + fingerprint, actor, now);
            jdbc.update("""
                INSERT INTO deployment_admission_extension_commands(reservation_id,operation_key,request_fingerprint,result_json)
                 VALUES (?,?,?,?)
                """, result.id(), key, fingerprint, json.write(result));
            return result;
    }
    private Reservation fenced(Fence fence) {
        if (fence == null) throw new AdmissionFailure(BAD_REQUEST, "FENCE_REQUIRED");
        Reservation r = require(fence.reservationId());
        if (r.revision() != fence.revision() || !r.generation().equals(fence.generation()))
            throw new AdmissionFailure(CONFLICT, "STALE_RESERVATION_FENCE");
        return r;
    }
    Reservation outcome(Fence fence, State state, String evidence, String actor, Instant now) {
        return locked(() -> outcomeLocked(fence, state, evidence, actor, now));
    }
    Reservation beginProvisioning(Fence fence, AdmissionContext checked, PrerequisiteReadback evidence, String actor, Instant requestedAt, Runnable localGuard) {
        return locked(() -> {
            Instant now = effectiveTime(requestedAt);
            Reservation r = fenced(fence);
            AdmissionContext current = context(r);
            requireEffectivePolicy(r, current);
            if (!json.fingerprint(checked).equals(json.fingerprint(current))
                || !r.manifestFingerprint().equals(current.manifestFingerprint())) throw new AdmissionFailure(CONFLICT, "RESERVATION_POLICY_CHANGED");
            localGuard.run(); now = effectiveTime(requestedAt);
            policy(current.policy(), now); grant(current.grant(), now); prerequisites(current, evidence, now);
            return outcomeLocked(fence, State.PROVISIONING, evidence.evidenceReference(), evidence, actor, now);
        });
    }

    Reservation beginReleaseProvisioning(ReleaseBinding binding, AdmissionContext checked, PrerequisiteReadback evidence,
                                         String actor, Instant requestedAt) {
        return locked(() -> {
            Instant now = effectiveTime(requestedAt);
            Reservation reservation = require(binding.reservationId());
            AdmissionContext current = context(reservation);
            requireUsableAllocation(reservation, current, now);
            if (!binding.generation().equals(reservation.generation()) || !binding.releaseId().equals(currentReleaseId(reservation))
                || !binding.versionId().equals(current.manifest().versionId())
                || !binding.versionConfigHash().equals(current.manifest().versionConfigHash())
                || !json.fingerprint(current).equals(json.fingerprint(checked))) {
                throw new AdmissionFailure(CONFLICT, "RELEASE_ALLOCATION_GENERATION_CHANGED");
            }
            policy(current.policy(), now); grant(current.grant(), now); prerequisites(current, evidence, now);
            Mutation mutation = new Mutation(reservation);
            mutation.state = State.PROVISIONING;
            mutation.prerequisiteReadback = evidence;
            mutation.evidence = evidence.evidenceReference();
            reservation = save(reservation, mutation, "RELEASE_PROVISIONING_STARTED", actor, now);
            releaseBindingState(binding.releaseId(), "PROVISIONING");
            return reservation;
        });
    }
    private Reservation outcomeLocked(Fence fence, State state, String evidence, String actor, Instant now) {
        return outcomeLocked(fence, state, evidence, null, actor, now);
    }
    private Reservation outcomeLocked(Fence fence, State state, String evidence, PrerequisiteReadback prerequisiteReadback,
                                      String actor, Instant now) {
        Reservation r = fenced(fence);
        if (!r.occupiesActiveSlot() || (state != State.OUTCOME_UNKNOWN && state != State.CLEANUP_FAILED
            && state != State.PROVISIONING)) throw new AdmissionFailure(CONFLICT, "RESERVATION_TRANSITION_FORBIDDEN");
        if (state == State.PROVISIONING && (r.state() != State.RESERVED || r.expiryState() != ExpiryState.CURRENT || !r.expiresAt().isAfter(now)))
            throw new AdmissionFailure(CONFLICT, "PROVISIONING_REQUIRES_CURRENT_RESERVATION");
        Mutation m = new Mutation(r); m.state = state; m.evidence = required(evidence, 255);
        if (prerequisiteReadback != null) m.prerequisiteReadback = prerequisiteReadback;
        if (state == State.CLEANUP_FAILED) m.failure = "CLEANUP_FAILED";
        return save(r, m, state.name(), actor, now);
    }
    Reservation reconcile(Fence fence, ResourceReadback evidence, boolean release, String actor, Instant requestedAt) {
        return locked(() -> {
            Instant now = effectiveTime(requestedAt);
            Reservation r = fenced(fence); FleetPolicy policy = reviewed("POLICY", r.policyId(), FleetPolicy.class);
            if (r.state() == State.RELEASED) throw new AdmissionFailure(CONFLICT, "RESERVATION_ALREADY_RELEASED");
            if (!r.occupiesActiveSlot()) throw new AdmissionFailure(CONFLICT, "RESERVATION_ALREADY_SETTLED");
            if (release && r.retentionEnvelope() != null) throw new AdmissionFailure(CONFLICT, "RETAINED_INDEX_CAPACITY_REQUIRED");
            if (release) releaseReadback(new ReadbackContext(r, policy, r.workerToken()), evidence, now);
            else {
                if (evidence == null) throw new AdmissionFailure(PRECONDITION_FAILED, "RESOURCE_READBACK_REQUIRED");
                fresh(evidence.observedAt(), now, policy.lifecycle().readbackMaxAgeSeconds());
                required(evidence.evidenceReference(), 255);
                resourceIdentity(new ReadbackContext(r, policy, r.workerToken()), evidence);
                if (!evidence.inventoryComplete() || !evidence.outcomeSettled() || evidence.resourcesAbsent()
                    || r.expiryState() == ExpiryState.CLAIMED || r.expiryState() == ExpiryState.HANDED_OFF)
                    throw new AdmissionFailure(PRECONDITION_FAILED, "ACTIVE_INVENTORY_UNPROVEN");
            }
            Mutation m = new Mutation(r); m.state = release ? State.RELEASED : State.ACTIVE;
            m.evidence = evidence.evidenceReference(); m.failure = null;
            if (release) { m.expiryState = ExpiryState.RELEASED; m.worker = null; m.nextRetry = null; }
            return save(r, m, release ? "CAPACITY_RELEASED" : "INVENTORY_RECONCILED", actor, now);
        });
    }
    Reservation settleRetained(Fence fence, ResourceReadback evidence, String actor, Instant requestedAt) {
        return locked(() -> {
            Instant now = effectiveTime(requestedAt);
            Reservation r = require(required(fence.reservationId(), 64));
            if (r.retentionSettlement() != null) {
                var prior = r.retentionSettlement();
                if (!r.generation().equals(fence.generation()) || prior.fromRevision() != fence.revision()
                    || evidence == null || evidence.retainedIndex() == null
                    || !prior.receiptFingerprint().equals(json.fingerprint(evidence.retainedIndex()))
                    || !prior.readbackFingerprint().equals(json.fingerprint(evidence)))
                    throw new AdmissionFailure(CONFLICT, "RETENTION_SETTLEMENT_REPLAY_CHANGED");
                return r;
            }
            r = fenced(fence);
            FleetPolicy policy = reviewed("POLICY", r.policyId(), FleetPolicy.class);
            retainedReadback(new ReadbackContext(r, policy, r.workerToken()), evidence, now);
            var receipt = evidence.retainedIndex();
            if (r.retentionEnvelope() == null || r.expiryState() != ExpiryState.HANDED_OFF
                || !Objects.equals(r.retirementOperationId(), receipt.operationId())
                || !r.id().equals(receipt.reservationId()) || !r.generation().equals(receipt.generation()))
                throw new AdmissionFailure(PRECONDITION_FAILED, "RETENTION_SETTLEMENT_UNPROVEN");
            var committed = jdbc.query("SELECT fingerprint FROM deployment_external_index_retention_receipts WHERE id=? AND operation_id=? AND reservation_id=? AND generation=?",
                (rs, row) -> rs.getString(1), receipt.id(), receipt.operationId(), r.id(), r.generation());
            var authorization = indexRetentionAuthorization(r.id());
            Integer finished = jdbc.queryForObject("SELECT COUNT(*) FROM platform_target_retirement_operations WHERE id=? AND state='RETIRED_WITH_RETAINED_DATA' AND request_fingerprint=? AND fence_epoch=?",
                Integer.class, receipt.operationId(), receipt.requestFingerprint(), receipt.fenceEpoch());
            if (committed.size() != 1 || !committed.getFirst().equals(json.fingerprint(receipt))
                || authorization == null || !authorization.policyFingerprint().equals(receipt.policyFingerprint())
                || finished == null || finished != 1
                || r.retentionEnvelope().amounts().get(Dimension.INDEXES) != receipt.indexes()
                || r.retentionEnvelope().amounts().get(Dimension.INDEX_BYTES) != receipt.indexBytes())
                throw new AdmissionFailure(PRECONDITION_FAILED, "RETENTION_SETTLEMENT_UNPROVEN");
            var remaining = new java.util.EnumMap<Dimension, Long>(Dimension.class);
            var released = new java.util.EnumMap<Dimension, Long>(Dimension.class);
            var reclaimable = java.util.EnumSet.of(Dimension.ENVIRONMENTS, Dimension.REPLICAS, Dimension.CPU_MILLICORES,
                Dimension.MEMORY_BYTES, Dimension.DISK_BYTES, Dimension.DATABASES, Dimension.DEPENDENCIES,
                Dimension.CONCURRENT_APPLIES, Dimension.CONCURRENT_BUILDS);
            for (Dimension d : Dimension.values()) {
                long before = r.chargedDemand().amounts().get(d);
                remaining.put(d, reclaimable.contains(d) ? 0L : before);
                released.put(d, reclaimable.contains(d) ? before : 0L);
            }
            Mutation m = new Mutation(r); m.state = State.RETAINED_EXTERNAL; m.expiryState = ExpiryState.SETTLED_RETAINED;
            m.worker = null; m.nextRetry = null; m.failure = null; m.evidence = evidence.evidenceReference();
            m.retentionSettlement = new RetainedCapacitySettlement(r.revision(), receipt.id(), json.fingerprint(receipt), json.fingerprint(evidence),
                receipt.operationId(), new Vector(remaining), new Vector(released), now);
            return save(r, m, "COMPUTE_RECLAIMED_INDEX_RETAINED", actor, now);
        });
    }
    Reservation activity(Fence fence, Activity activity, String evidence, String actor, Instant requestedAt) {
        return locked(() -> {
            Instant now = effectiveTime(requestedAt);
            Reservation r = fenced(fence); LifecyclePolicy policy = reviewed("POLICY", r.policyId(), FleetPolicy.class).lifecycle();
            if (activity == null || r.intent() != Intent.CUSTOMER_STAGING || !r.occupiesActiveSlot() || r.expiryState() == ExpiryState.CLAIMED
                || r.expiryState() == ExpiryState.HANDED_OFF || !r.expiresAt().isAfter(now))
                throw new AdmissionFailure(CONFLICT, "ACTIVITY_REQUIRES_CURRENT_STAGING");
            Mutation m = new Mutation(r); m.lastActivity = now;
            Instant requestedExpiry = now.plusSeconds(policy.inactivitySeconds());
            if (requestedExpiry.isBefore(r.expiresAt())) requestedExpiry = r.expiresAt();
            m.expires = boundedExpiry(r, requestedExpiry, policy);
            m.expiryState = ExpiryState.CURRENT; m.noticeCycle = uuid(); m.attempts = 0; m.nextRetry = null; m.failure = null;
            m.evidence = required(evidence, 255);
            return save(r, m, "ACTIVITY_" + activity.name(), actor, now);
        });
    }
    Reservation scheduleRetirementAfterPromotion(String deploymentId, String targetProfileId,
                                                  String operationKey, String actor, Instant requestedAt) {
        return locked(() -> {
            Instant now = effectiveTime(requestedAt);
            String evidence = "promotion-retirement:" + required(operationKey, 128);
            var rows = jdbc.query("""
                SELECT payload_json FROM deployment_capacity_reservations
                WHERE deployment_id=? AND target_profile_id=? AND current_slot=?
                """, (rs, row) -> json.read(rs.getString(1), Reservation.class),
                required(deploymentId, 64), required(targetProfileId, 64), deploymentId + "|" + targetProfileId);
            if (rows.isEmpty()) throw new AdmissionFailure(PRECONDITION_FAILED, "STAGING_RESERVATION_REQUIRED");
            if (rows.size() != 1) throw new AdmissionFailure(CONFLICT, "STAGING_RESERVATION_AMBIGUOUS");
            Reservation r = rows.getFirst();
            Integer replay = jdbc.queryForObject("""
                SELECT COUNT(*) FROM deployment_admission_events
                WHERE reservation_id=? AND event_type='PROMOTION_RETIREMENT_SCHEDULED' AND evidence_reference=?
                """, Integer.class, r.id(), evidence);
            if (replay != null && replay > 0) return r;
            if (r.intent() != Intent.CUSTOMER_STAGING || !r.occupiesActiveSlot()
                || !List.of(ExpiryState.CURRENT, ExpiryState.NOTICE_PENDING,
                    ExpiryState.NOTICE_FAILED, ExpiryState.NOTIFIED).contains(r.expiryState())) {
                throw new AdmissionFailure(CONFLICT, "PROMOTION_RETIREMENT_SCHEDULE_STATE_INVALID");
            }
            LifecyclePolicy policy = reviewed("POLICY", r.policyId(), FleetPolicy.class).lifecycle();
            Instant scheduled = now.plusSeconds(policy.noticeLeadSeconds());
            if (r.keepUntil() != null && scheduled.isBefore(r.keepUntil())) scheduled = r.keepUntil();
            Instant maximum = r.createdAt().plusSeconds(policy.maxLifetimeSeconds());
            if (scheduled.isAfter(maximum)) {
                throw new AdmissionFailure(CONFLICT, "PROMOTION_RETIREMENT_NOTICE_WINDOW_UNAVAILABLE");
            }
            Mutation m = new Mutation(r);
            m.expires = scheduled;
            m.expiryState = ExpiryState.CURRENT;
            m.noticeCycle = uuid();
            m.attempts = 0;
            m.nextRetry = null;
            m.worker = null;
            m.drainDeadline = null;
            m.recoveryUntil = null;
            m.retirement = null;
            m.failure = null;
            m.evidence = evidence;
            return save(r, m, "PROMOTION_RETIREMENT_SCHEDULED", required(actor, 255), now);
        });
    }
    Reservation extend(Fence fence, boolean keep, CustomerGrant checkedGrant, String evidence, String actor, Instant requestedAt) {
        return locked(() -> extendLocked(fence, keep, checkedGrant, evidence, actor, requestedAt));
    }
    private Reservation extendLocked(Fence fence, boolean keep, CustomerGrant checkedGrant, String evidence, String actor, Instant requestedAt) {
            Instant now = effectiveTime(requestedAt);
            Reservation r = fenced(fence); CustomerGrant grant = customerGrant(r.customerId()); grant(grant, now);
            if (!json.fingerprint(grant).equals(json.fingerprint(checkedGrant))) throw new AdmissionFailure(CONFLICT, "CUSTOMER_GRANT_CHANGED");
            LifecyclePolicy p = reviewed("POLICY", r.policyId(), FleetPolicy.class).lifecycle();
            if (r.intent() != Intent.CUSTOMER_STAGING || !r.occupiesActiveSlot() || r.expiryState() == ExpiryState.CLAIMED
                || r.expiryState() == ExpiryState.HANDED_OFF || r.extensions() >= p.maxExtensions() || !r.expiresAt().isAfter(now))
                throw new AdmissionFailure(CONFLICT, "STAGING_EXTENSION_FORBIDDEN");
            if (keep && grant.keepStagingEntitlementReference() == null) throw new AdmissionFailure(FORBIDDEN, "KEEP_STAGING_ENTITLEMENT_REQUIRED");
            checkCustomer(grant, held().stream().filter(h -> r.customerId().equals(h.customerId())).toList());
            Mutation m = new Mutation(r);
            Instant requested = r.expiresAt().plusSeconds(keep ? p.maxKeepSeconds() : p.extensionSeconds());
            m.expires = boundedExpiry(r, requested, p);
            if (!m.expires.isAfter(r.expiresAt()) || m.expires.isAfter(grant.validUntil()))
                throw new AdmissionFailure(CONFLICT, "STAGING_EXTENSION_LIMIT");
            m.extensions++; m.expiryState = ExpiryState.CURRENT; m.noticeCycle = uuid(); m.attempts = 0;
            m.nextRetry = null; m.failure = null; m.evidence = required(evidence, 255);
            if (keep) { m.keepUntil = m.expires; m.keepEntitlement = grant.keepStagingEntitlementReference(); }
            return save(r, m, keep ? "KEEP_STAGING_APPROVED" : "STAGING_EXTENDED", actor, now);
    }
    private Instant boundedExpiry(Reservation r, Instant requested, LifecyclePolicy policy) {
        Instant max = r.createdAt().plusSeconds(policy.maxLifetimeSeconds());
        Instant result = requested.isBefore(max) ? requested : max;
        if (r.keepUntil() != null && result.isBefore(r.keepUntil())) result = r.keepUntil();
        return result;
    }
    List<NoticeWork> noticeWork(Fence fence, String actor, Instant requestedAt) {
        return locked(() -> {
            Instant now = effectiveTime(requestedAt);
            Reservation r = fenced(fence); LifecyclePolicy p = reviewed("POLICY", r.policyId(), FleetPolicy.class).lifecycle();
            if (!r.occupiesActiveSlot() || r.intent() != Intent.CUSTOMER_STAGING || r.expiryState() == ExpiryState.CLAIMED
                || r.expiryState() == ExpiryState.HANDED_OFF || r.expiryState() == ExpiryState.BLOCKED)
                throw new AdmissionFailure(CONFLICT, "NOTICE_STATE_FORBIDDEN");
            if (r.expiryState() == ExpiryState.NOTIFIED) return List.of();
            if (now.isBefore(r.expiresAt().minusSeconds(p.noticeLeadSeconds()))
                || (r.nextRetryAt() != null && now.isBefore(r.nextRetryAt()))) return List.of();
            if (r.attempts() >= p.maxAttempts()) {
                Mutation m = new Mutation(r); m.expiryState = ExpiryState.BLOCKED; m.failure = "NOTICE_DELIVERY_DEAD_LETTER"; m.nextRetry = null;
                save(r, m, "NOTICE_DELIVERY_DEAD_LETTER", actor, now);
                return List.of();
            }
            Mutation m = new Mutation(r); m.expiryState = ExpiryState.NOTICE_PENDING;
            m.attempts++; m.nextRetry = now.plusSeconds(p.retrySeconds());
            Reservation next = save(r, m, "NOTICE_REQUESTED", actor, now);
            return List.of(work(next, NoticeChannel.CONSOLE, p), work(next, NoticeChannel.OWNER_EMAIL, p)).stream()
                .filter(w -> !delivered(next, w.channel())).toList();
        });
    }
    NoticeWork work(Reservation r, NoticeChannel channel, LifecyclePolicy p) {
        String key = r.id() + ":" + r.noticeCycle() + ":" + channel.name();
        return new NoticeWork(r.fence(), r.noticeCycle(), channel, key, r.expiresAt(), p.noticeTransportReference());
    }
    private boolean delivered(Reservation r, NoticeChannel channel) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM deployment_admission_notices WHERE reservation_id=? AND notice_cycle=? AND channel=?",
            Long.class, r.id(), r.noticeCycle(), channel.name()) > 0;
    }
    Reservation noticeDelivered(NoticeWork work, NoticeDelivery receipt, String actor, Instant requestedAt) {
        return locked(() -> {
            Instant now = effectiveTime(requestedAt);
            Reservation r = require(work.fence().reservationId()); LifecyclePolicy p = reviewed("POLICY", r.policyId(), FleetPolicy.class).lifecycle();
            if (!r.generation().equals(work.fence().generation()) || !r.noticeCycle().equals(work.noticeCycle())
                || !this.work(r, work.channel(), p).deliveryKey().equals(work.deliveryKey())
                || !p.noticeTransportReference().equals(work.transportReference()) || !r.expiresAt().equals(work.expiresAt())
                || !List.of(ExpiryState.NOTICE_PENDING, ExpiryState.NOTICE_FAILED, ExpiryState.NOTIFIED, ExpiryState.BLOCKED).contains(r.expiryState())
                || !r.occupiesActiveSlot())
                throw new AdmissionFailure(CONFLICT, "STALE_NOTICE_CYCLE");
            if (receipt == null || !work.deliveryKey().equals(receipt.deliveryKey())) throw new AdmissionFailure(PRECONDITION_FAILED, "NOTICE_DELIVERY_UNPROVEN");
            fresh(receipt.deliveredAt(), now, p.readbackMaxAgeSeconds()); required(receipt.evidenceReference(), 255);
            if (receipt.deliveredAt().isBefore(r.expiresAt().minusSeconds(p.inactivitySeconds())))
                throw new AdmissionFailure(CONFLICT, "STALE_NOTICE_DELIVERY");
            if (delivered(r, work.channel())) return r;
            jdbc.update("INSERT INTO deployment_admission_notices(delivery_key,reservation_id,notice_cycle,channel,delivered_at,evidence_reference) VALUES (?,?,?,?,?,?)",
                work.deliveryKey(), r.id(), r.noticeCycle(), work.channel().name(), time(receipt.deliveredAt()), receipt.evidenceReference());
            Mutation m = new Mutation(r); m.evidence = receipt.evidenceReference();
            m.expiryState = delivered(r, NoticeChannel.CONSOLE) && delivered(r, NoticeChannel.OWNER_EMAIL) ? ExpiryState.NOTIFIED : ExpiryState.NOTICE_PENDING;
            if (m.expiryState == ExpiryState.NOTIFIED) { m.nextRetry = null; m.failure = null; }
            return save(r, m, "NOTICE_DELIVERED_" + work.channel().name(), actor, now);
        });
    }
    Reservation noticeFailed(Fence fence, String code, String actor, Instant now) {
        return locked(() -> {
            Reservation r = fenced(fence); LifecyclePolicy p = reviewed("POLICY", r.policyId(), FleetPolicy.class).lifecycle();
            if (r.expiryState() != ExpiryState.NOTICE_PENDING) throw new AdmissionFailure(CONFLICT, "NOTICE_NOT_PENDING");
            Mutation m = new Mutation(r); m.failure = required(code, 64);
            m.expiryState = r.attempts() >= p.maxAttempts() ? ExpiryState.BLOCKED : ExpiryState.NOTICE_FAILED;
            return save(r, m, "NOTICE_FAILED", actor, now);
        });
    }
    Reservation noticeOutcomeUnknown(Fence fence, String actor, Instant requestedAt) {
        return locked(() -> {
            Instant now = effectiveTime(requestedAt);
            Reservation r = fenced(fence);
            if (r.expiryState() != ExpiryState.NOTICE_PENDING) {
                throw new AdmissionFailure(CONFLICT, "NOTICE_NOT_PENDING");
            }
            Mutation m = new Mutation(r);
            m.expiryState = ExpiryState.BLOCKED;
            m.failure = "NOTICE_DELIVERY_OUTCOME_UNKNOWN";
            m.nextRetry = null;
            return save(r, m, "NOTICE_DELIVERY_OUTCOME_UNKNOWN", actor, now);
        });
    }
    Reservation resumeNoticeAfterResolution(String reservationId, String noticeCycle, String actor,
                                              Instant requestedAt) {
        return locked(() -> {
            Instant now = effectiveTime(requestedAt);
            Reservation r = require(required(reservationId, 64));
            if (!Objects.equals(r.noticeCycle(), required(noticeCycle, 64)) || !r.occupiesActiveSlot()
                || r.intent() != Intent.CUSTOMER_STAGING
                || r.expiryState() == ExpiryState.CLAIMED || r.expiryState() == ExpiryState.HANDED_OFF) {
                throw new AdmissionFailure(CONFLICT, "NOTICE_RESOLUTION_STALE");
            }
            if (r.expiryState() == ExpiryState.NOTIFIED) return r;
            if (!List.of(ExpiryState.BLOCKED, ExpiryState.NOTICE_FAILED, ExpiryState.NOTICE_PENDING)
                .contains(r.expiryState())) {
                throw new AdmissionFailure(CONFLICT, "NOTICE_RESOLUTION_STALE");
            }
            Mutation m = new Mutation(r);
            m.expiryState = ExpiryState.NOTICE_FAILED;
            m.attempts = 0;
            m.nextRetry = now;
            m.failure = null;
            return save(r, m, "NOTICE_DELIVERY_RESOLVED", required(actor, 255), now);
        });
    }
    ExpiryWork claimExpiry(Fence fence, String actor, Instant requestedAt) {
        return locked(() -> {
            Instant now = effectiveTime(requestedAt);
            Reservation r = fenced(fence); LifecyclePolicy p = reviewed("POLICY", r.policyId(), FleetPolicy.class).lifecycle();
            if (r.intent() != Intent.CUSTOMER_STAGING || !r.occupiesActiveSlot() || r.expiryState() != ExpiryState.NOTIFIED
                || now.isBefore(r.expiresAt()) || (r.keepUntil() != null && now.isBefore(r.keepUntil())))
                throw new AdmissionFailure(CONFLICT, "EXPIRY_NOT_ELIGIBLE");
            if (!delivered(r, NoticeChannel.CONSOLE) || !delivered(r, NoticeChannel.OWNER_EMAIL))
                throw new AdmissionFailure(PRECONDITION_FAILED, "NOTICE_DELIVERY_UNPROVEN");
            Instant latestDelivery = jdbc.queryForObject("SELECT MAX(delivered_at) FROM deployment_admission_notices WHERE reservation_id=? AND notice_cycle=?",
                Timestamp.class, r.id(), r.noticeCycle()).toInstant();
            if (now.isBefore(latestDelivery.plusSeconds(p.noticeLeadSeconds()))) throw new AdmissionFailure(CONFLICT, "NOTICE_WINDOW_NOT_ELAPSED");
            Mutation m = new Mutation(r); m.expiryState = ExpiryState.CLAIMED; m.worker = uuid(); m.nextRetry = null;
            m.drainDeadline = now.plusSeconds(p.drainSeconds());
            m.recoveryUntil = m.drainDeadline.plusSeconds(p.recoverySeconds());
            Reservation next = save(r, m, "EXPIRY_CLAIMED", actor, now);
            return expiryWork(next);
        });
    }
    ExpiryWork expiryWork(Reservation r) {
        if (r.workerToken() == null || r.drainDeadline() == null || r.recoveryUntil() == null)
            throw new AdmissionFailure(CONFLICT, "EXPIRY_NOT_CLAIMED");
        return new ExpiryWork(r.fence(), r.workerToken(), r.noticeCycle(), r.expiresAt(), r.drainDeadline(),
            r.recoveryUntil(), "STAGING_INACTIVITY_EXPIRED");
    }
    Reservation handoff(ExpiryWork work, String retirementOperationId, String evidence, String actor, Instant now) {
        return locked(() -> {
            Reservation r = fenced(work.fence());
            if (r.expiryState() != ExpiryState.CLAIMED || !Objects.equals(r.workerToken(), work.workerToken())
                || !r.noticeCycle().equals(work.noticeCycle())) throw new AdmissionFailure(CONFLICT, "STALE_EXPIRY_CLAIM");
            Mutation m = new Mutation(r); m.expiryState = ExpiryState.HANDED_OFF;
            m.retirement = required(retirementOperationId, 64); m.evidence = required(evidence, 255);
            return save(r, m, "RETIREMENT_HANDOFF", actor, now);
        });
    }
    PreparedFailureRecoveryContracts.Plan planPreparedFailureRecovery(String customerId, String deploymentId,
        String reservationId, PreparedFailureRecoveryContracts.Command draft,
        java.util.function.Consumer<Reservation> authorize) {
        validatePreparedRecovery(draft, false);
        return locked(() -> {
            try {
            Reservation r = preparedRecoveryReservation(customerId, deploymentId, reservationId, draft, authorize);
            var proof = new PreparedFailureRecoveryEvidence(jdbc, json).read(r, draft.releaseId(), context(r).manifest());
            return new PreparedFailureRecoveryContracts.Plan(new PreparedFailureRecoveryContracts.Command(r.fence(),
                draft.releaseId(), draft.operationKey(), draft.reason(), json.fingerprint(proof)), proof.boundary());
            } catch (org.springframework.dao.DataAccessException busy) {
                throw unusedCancellationLockFailure(busy, "PREPARED_RECOVERY_BUSY");
            }
        });
    }

    PreparedFailureRecoveryContracts.Summary recoverPreparedFailure(String customerId, String deploymentId,
        String reservationId, PreparedFailureRecoveryContracts.Command command, String actor,
        java.util.function.Consumer<Reservation> authorize) {
        validatePreparedRecovery(command, true); required(actor, 255);
        String fingerprint = json.fingerprint(Map.of("customer", customerId, "deployment", deploymentId,
            "reservation", reservationId, "actor", actor, "command", command));
        String key = json.fingerprint(Map.of("reservation", reservationId, "generation", command.fence().generation(),
            "operation", command.operationKey()));
        return locked(() -> {
            try {
            if (jdbc.query("SELECT id FROM platform_deployments WHERE id=? FOR UPDATE NOWAIT", (rs, row) -> rs.getString(1), deploymentId).size() != 1)
                throw new AdmissionFailure(NOT_FOUND, "RESERVATION_NOT_FOUND");
            Reservation observed = require(reservationId);
            if (!customerId.equals(observed.customerId()) || !deploymentId.equals(observed.deploymentId())
                || !reservationId.equals(command.fence().reservationId())) throw new AdmissionFailure(NOT_FOUND, "RESERVATION_NOT_FOUND");
            authorize.accept(observed);
            if (!jdbc.queryForList("SELECT record_key FROM deployment_admission_reviews WHERE kind='PREPARED_REPAIR' AND record_key=?",
                String.class, key).isEmpty()) {
                var receipt = verifiedReview("PREPARED_REPAIR", key, PreparedFailureRecoveryContracts.Receipt.class);
                if (!fingerprint.equals(receipt.commandFingerprint())) throw new AdmissionFailure(CONFLICT, "PREPARED_RECOVERY_OPERATION_REUSED");
                return receipt.summary();
            }
            Reservation r = preparedRecoveryReservation(customerId, deploymentId, reservationId, command, authorize);
            var proof = new PreparedFailureRecoveryEvidence(jdbc, json).read(r, command.releaseId(), context(r).manifest());
            if (!json.fingerprint(proof).equals(command.proofFingerprint())) throw new AdmissionFailure(CONFLICT, "PREPARED_RECOVERY_PROOF_CHANGED");
            Instant now = clock.instant();
            Mutation mutation = new Mutation(r); mutation.state = State.RESERVED;
            mutation.evidence = "prepared-recovery:" + command.proofFingerprint();
            Reservation recovered = save(r, mutation, "PREPARED_FAILURE_RECOVERED_BEFORE_NATIVE_EFFECTS", actor, now);
            if (jdbc.update("UPDATE deployment_admission_release_bindings SET state='NO_EFFECT',updated_at=? WHERE release_id=? AND state='OUTCOME_UNKNOWN'",
                time(now), command.releaseId()) != 1) throw new AdmissionFailure(CONFLICT, "PREPARED_RECOVERY_BINDING_CHANGED");
            var summary = new PreparedFailureRecoveryContracts.Summary(uuid(), r.id(), r.generation(), r.revision(),
                recovered.revision(), command.releaseId(), recovered.state(), proof.retainedAllocationIds(), false, false, now);
            review("PREPARED_REPAIR", key, new PreparedFailureRecoveryContracts.Receipt(actor, fingerprint, command,
                proof, recovered, summary), actor, now);
            return summary;
            } catch (org.springframework.dao.DataAccessException busy) {
                throw unusedCancellationLockFailure(busy, "PREPARED_RECOVERY_BUSY");
            }
        });
    }

    private Reservation preparedRecoveryReservation(String customerId, String deploymentId, String reservationId,
        PreparedFailureRecoveryContracts.Command command, java.util.function.Consumer<Reservation> authorize) {
        required(customerId, 64); required(deploymentId, 64); required(reservationId, 64);
        if (authorize == null || !reservationId.equals(command.fence().reservationId()))
            throw new AdmissionFailure(BAD_REQUEST, "PREPARED_RECOVERY_SCOPE_REQUIRED");
        if (jdbc.query("SELECT id FROM platform_deployments WHERE id=? FOR UPDATE NOWAIT", (rs, row) -> rs.getString(1), deploymentId).size() != 1)
            throw new AdmissionFailure(NOT_FOUND, "RESERVATION_NOT_FOUND");
        Reservation r = fenced(command.fence());
        if (!customerId.equals(r.customerId()) || !deploymentId.equals(r.deploymentId()))
            throw new AdmissionFailure(NOT_FOUND, "RESERVATION_NOT_FOUND");
        var current = context(r);
        for (var row : Map.of("platform_customers", r.customerId(), "platform_customer_tenants", r.tenantId(),
            "platform_deployment_versions", current.manifest().versionId(), "deployment_target_profiles", r.targetProfileId()).entrySet()) {
            if (jdbc.query("SELECT id FROM " + row.getKey() + " WHERE id=? FOR UPDATE NOWAIT", (rs, index) -> rs.getString(1), row.getValue()).size() != 1)
                throw new AdmissionFailure(CONFLICT, "PREPARED_RECOVERY_OWNERSHIP_CHANGED");
        }
        authorize.accept(r);
        requireEffectivePolicy(r, current);
        policy(current.policy(), clock.instant()); grant(current.grant(), clock.instant());
        if (r.state() != State.OUTCOME_UNKNOWN || r.expiryState() != ExpiryState.CURRENT || !r.expiresAt().isAfter(clock.instant())
            || r.workerToken() != null || r.retirementOperationId() != null || r.retentionEnvelope() != null || r.retentionSettlement() != null
            || r.drainDeadline() != null || r.recoveryUntil() != null || r.nextRetryAt() != null || r.failureCode() != null
            || r.attempts() != 0 || !command.releaseId().equals(currentReleaseId(r)))
            throw new AdmissionFailure(CONFLICT, "PREPARED_RECOVERY_CURRENT_FAILED_ALLOCATION_REQUIRED");
        return r;
    }

    private void validatePreparedRecovery(PreparedFailureRecoveryContracts.Command command, boolean execute) {
        if (command == null || command.fence() == null || command.fence().revision() < 0)
            throw new AdmissionFailure(BAD_REQUEST, "PREPARED_RECOVERY_COMMAND_REQUIRED");
        required(command.fence().reservationId(), 64); required(command.fence().generation(), 64);
        required(command.releaseId(), 64); required(command.operationKey(), 128); required(command.reason(), 255);
        if (execute && (command.proofFingerprint() == null || !command.proofFingerprint().matches("[a-f0-9]{64}")))
            throw new AdmissionFailure(BAD_REQUEST, "PREPARED_RECOVERY_PROOF_REQUIRED");
    }

    private Reservation save(Reservation r, Mutation m, String type, String actor, Instant now) {
        Reservation next = m.build(r, now);
        int changed = jdbc.update("""
            UPDATE deployment_capacity_reservations SET revision=?,state=?,current_slot=?,expires_at=?,next_retry_at=?,
              expiry_state=?,payload_json=?,updated_at=? WHERE id=? AND revision=? AND generation=?
            """, next.revision(), next.state().name(), next.occupiesActiveSlot() ? slot(next) : null, time(next.expiresAt()),
            time(next.nextRetryAt()), next.expiryState().name(), json.write(next), time(now), r.id(), r.revision(), r.generation());
        if (changed != 1) throw new AdmissionFailure(CONFLICT, "STALE_RESERVATION_FENCE");
        event(next, type, actor, next.evidenceReference(), now);
        return next;
    }
    private void event(Reservation r, String type, String actor, String evidence, Instant now) {
        jdbc.update("INSERT INTO deployment_admission_events(id,reservation_id,revision,event_type,actor_id,evidence_reference,created_at) VALUES (?,?,?,?,?,?,?)",
            uuid(), r.id(), r.revision(), type, required(actor, 255), evidence, time(now));
    }
    /** Called only inside the ledger transaction, before local authorization/version/profile revalidation. */
    void lockAuthoritativeRows(AdmissionContext context) {
        lockRow("platform_deployments", context.deploymentId());
        lockRow("platform_customers", context.customerId());
        lockRow("platform_customer_tenants", context.tenantId());
        lockRow("platform_deployment_versions", context.manifest().versionId());
        lockRow("deployment_target_profiles", context.target().targetProfileId());
    }
    private void lockRow(String table, String id) {
        // Table names are code constants, not input. Locks contain only local reads, never remote calls.
        var found = jdbc.query("SELECT id FROM " + table + " WHERE id=? FOR UPDATE", (rs, row) -> rs.getString(1), id);
        if (found.isEmpty()) throw new AdmissionFailure(CONFLICT, "ADMISSION_OWNERSHIP_CHANGED");
    }
    private static String uuid() { return UUID.randomUUID().toString(); }
    private static String slot(Reservation r) { return r.deploymentId() + "|" + r.targetProfileId(); }
    private static Timestamp time(Instant value) { return value == null ? null : Timestamp.from(value); }
    private Instant effectiveTime(Instant requestedAt) {
        Instant current = clock.instant();
        return current.isAfter(requestedAt) ? current : requestedAt;
    }

    private static final class Mutation {
        State state; ExpiryState expiryState; Instant lastActivity; Instant expires; int extensions;
        Instant keepUntil; String keepEntitlement; String noticeCycle; Instant nextRetry; int attempts;
        String worker; Instant drainDeadline; Instant recoveryUntil; String retirement; String evidence; String failure;
        String manifestId; String manifestFingerprint; String bindingFingerprint;
        PrerequisiteReadback prerequisiteReadback;
        Vector retentionEnvelope; RetainedCapacitySettlement retentionSettlement;
        Mutation(Reservation r) {
            state = r.state(); expiryState = r.expiryState(); lastActivity = r.lastActivityAt(); expires = r.expiresAt();
            extensions = r.extensions(); keepUntil = r.keepUntil(); keepEntitlement = r.keepEntitlementReference();
            noticeCycle = r.noticeCycle(); nextRetry = r.nextRetryAt(); attempts = r.attempts(); worker = r.workerToken();
            drainDeadline = r.drainDeadline(); recoveryUntil = r.recoveryUntil();
            retirement = r.retirementOperationId(); evidence = r.evidenceReference(); failure = r.failureCode();
            prerequisiteReadback = r.prerequisiteReadback();
            retentionEnvelope = r.retentionEnvelope(); retentionSettlement = r.retentionSettlement();
            manifestId = r.manifestId(); manifestFingerprint = r.manifestFingerprint(); bindingFingerprint = r.bindingFingerprint();
        }
        Reservation build(Reservation r, Instant now) {
            return build(r, now, Math.incrementExact(r.revision()));
        }
        Reservation build(Reservation r, Instant now, long revision) {
            return new Reservation(r.id(), r.customerId(), r.tenantId(), r.deploymentId(), r.targetProfileId(), r.intent(),
                r.operationKey(), r.requestFingerprint(), r.generation(), revision, state, r.demand(),
                r.policyId(), manifestId, manifestFingerprint, bindingFingerprint, r.createdAt(), now, lastActivity,
                expires, extensions, keepUntil, keepEntitlement, expiryState, noticeCycle, nextRetry, attempts, worker,
                drainDeadline, recoveryUntil, retirement, evidence, failure, r.customerContextFingerprint(),
                r.customerOperation(), prerequisiteReadback, retentionEnvelope, retentionSettlement);
        }
    }
}
