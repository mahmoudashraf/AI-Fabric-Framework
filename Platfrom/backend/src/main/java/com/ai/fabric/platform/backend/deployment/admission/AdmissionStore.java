package com.ai.fabric.platform.backend.deployment.admission;

import org.springframework.jdbc.core.JdbcTemplate;
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

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static com.ai.fabric.platform.backend.deployment.admission.AdmissionRules.*;
import static org.springframework.http.HttpStatus.*;

/** All ledger writes serialize on a database row, across threads/processes and across target pools. */
@Repository
class AdmissionStore {
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
        locked(() -> {
            Instant now = effectiveTime(requestedAt);
            policy(policy, now);
            checkCapacity(policy, held(), null, null);
            review("POLICY", policy.id(), policy, actor, now);
            jdbc.update("UPDATE deployment_admission_mutex SET active_policy_ref=? WHERE id=1", policy.id());
            return null;
        });
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
    FleetPolicy activePolicy() {
        String id = jdbc.queryForObject("SELECT active_policy_ref FROM deployment_admission_mutex WHERE id=1", String.class);
        if (id == null) throw new AdmissionFailure(PRECONDITION_FAILED, "MEASURED_POLICY_REQUIRED");
        return reviewed("POLICY", id, FleetPolicy.class);
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
    Reservation replay(String customer, String deployment, String target, Reserve command, String fingerprint) {
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
        if (command.customerContext() != null) value.put("customerContext", command.customerContext());
        return json.fingerprint(value);
    }
    Reservation reserve(AdmissionContext checked, Reserve command, PrerequisiteReadback evidence, String actor, Instant now) {
        return reserve(checked, command, evidence, actor, now, () -> { });
    }
    Reservation reserve(AdmissionContext checked, Reserve command, PrerequisiteReadback evidence, String actor, Instant requestedAt, Runnable localGuard) {
        return locked(() -> {
            Instant now = effectiveTime(requestedAt);
            AdmissionContext current = context(checked.customerId(), checked.tenantId(), checked.deploymentId(), command.manifestId());
            validateCustomerScope(current, command);
            String fingerprint = commandFingerprint(current.customerId(), current.tenantId(), current.deploymentId(), current.manifest(), command);
            Reservation replay = replay(current.customerId(), current.deploymentId(), current.target().targetProfileId(), command, fingerprint);
            if (replay != null) return replay;
            if (!json.fingerprint(current).equals(json.fingerprint(checked))) throw new AdmissionFailure(CONFLICT, "ADMISSION_POLICY_CHANGED");
            localGuard.run();
            now = effectiveTime(requestedAt);
            policy(current.policy(), now); grant(current.grant(), now); manifest(current.manifest()); prerequisites(current, evidence, now);
            var held = held();
            if (held.stream().anyMatch(r -> r.deploymentId().equals(current.deploymentId()) && r.targetProfileId().equals(current.target().targetProfileId())))
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
            return reservation;
        });
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
        var fleet = new ArrayList<>(held.stream().map(Reservation::demand).toList());
        fleet.add(policy.fleet().baseline()); fleet.add(policy.fleet().headroom()); if (extra != null) fleet.add(extra);
        within(sum(fleet), policy.fleet().measured(), "FLEET_QUOTA_EXCEEDED");
        for (Reservation r : held) if (!policy.targets().containsKey(r.targetProfileId()))
            throw new AdmissionFailure(CONFLICT, "LIVE_TARGET_POLICY_REQUIRED");
        policy.targets().forEach((id, target) -> {
            var usage = new ArrayList<>(held.stream().filter(r -> id.equals(r.targetProfileId())).map(Reservation::demand).toList());
            usage.add(target.budget().baseline()); usage.add(target.budget().headroom());
            if (extra != null && id.equals(targetId)) usage.add(extra);
            within(sum(usage), target.budget().measured(), "TARGET_QUOTA_EXCEEDED");
        });
    }
    private void checkCustomer(CustomerGrant grant, List<Reservation> held) {
        within(sum(held.stream().map(Reservation::demand).toList()), grant.fleetLimit(), "CUSTOMER_FLEET_QUOTA_EXCEEDED");
        for (Reservation r : held) if (!grant.targetLimits().containsKey(r.targetProfileId()))
            throw new AdmissionFailure(CONFLICT, "LIVE_CUSTOMER_TARGET_QUOTA_REQUIRED");
        grant.targetLimits().forEach((id, limit) -> within(sum(held.stream().filter(r -> id.equals(r.targetProfileId()))
            .map(Reservation::demand).toList()), limit, "CUSTOMER_TARGET_QUOTA_EXCEEDED"));
    }
    List<Reservation> held() {
        return jdbc.query("SELECT payload_json FROM deployment_capacity_reservations WHERE state<>'RELEASED' ORDER BY id",
            (rs, row) -> json.read(rs.getString(1), Reservation.class));
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
            SELECT customer_id,deployment_id,target_profile_id,operation_key,state,payload_json
              FROM deployment_capacity_reservations
             WHERE customer_id=? AND deployment_id=? AND target_profile_id=? AND operation_key=?
             FOR UPDATE
            """, (rs, row) -> {
                Reservation reservation = json.read(rs.getString("payload_json"), Reservation.class);
                if (!rs.getString("customer_id").equals(reservation.customerId())
                    || !rs.getString("deployment_id").equals(reservation.deploymentId())
                    || !rs.getString("target_profile_id").equals(reservation.targetProfileId())
                    || !rs.getString("operation_key").equals(reservation.operationKey())
                    || !rs.getString("state").equals(reservation.state().name())) {
                    throw new AdmissionFailure(CONFLICT, "ADMISSION_RESERVATION_RECORD_MISMATCH");
                }
                return reservation;
            }, context.customerId(), context.deploymentId(), context.targetProfileId(), context.operationKey());
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
            AdmissionContext current = context(r.customerId(), r.tenantId(), r.deploymentId(), r.manifestId());
            if (!json.fingerprint(checked).equals(json.fingerprint(current)) || !r.policyId().equals(current.policy().id())
                || !r.manifestFingerprint().equals(current.manifestFingerprint())) throw new AdmissionFailure(CONFLICT, "RESERVATION_POLICY_CHANGED");
            localGuard.run(); now = effectiveTime(requestedAt);
            policy(current.policy(), now); grant(current.grant(), now); prerequisites(current, evidence, now);
            return outcomeLocked(fence, State.PROVISIONING, evidence.evidenceReference(), evidence, actor, now);
        });
    }
    private Reservation outcomeLocked(Fence fence, State state, String evidence, String actor, Instant now) {
        return outcomeLocked(fence, state, evidence, null, actor, now);
    }
    private Reservation outcomeLocked(Fence fence, State state, String evidence, PrerequisiteReadback prerequisiteReadback,
                                      String actor, Instant now) {
        Reservation r = fenced(fence);
        if (r.state() == State.RELEASED || (state != State.OUTCOME_UNKNOWN && state != State.CLEANUP_FAILED
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
    Reservation activity(Fence fence, Activity activity, String evidence, String actor, Instant requestedAt) {
        return locked(() -> {
            Instant now = effectiveTime(requestedAt);
            Reservation r = fenced(fence); LifecyclePolicy policy = reviewed("POLICY", r.policyId(), FleetPolicy.class).lifecycle();
            if (activity == null || r.intent() != Intent.CUSTOMER_STAGING || !r.consumesCapacity() || r.expiryState() == ExpiryState.CLAIMED
                || r.expiryState() == ExpiryState.HANDED_OFF || !r.expiresAt().isAfter(now))
                throw new AdmissionFailure(CONFLICT, "ACTIVITY_REQUIRES_CURRENT_STAGING");
            Mutation m = new Mutation(r); m.lastActivity = now;
            m.expires = boundedExpiry(r, now.plusSeconds(policy.inactivitySeconds()), policy);
            m.expiryState = ExpiryState.CURRENT; m.noticeCycle = uuid(); m.attempts = 0; m.nextRetry = null; m.failure = null;
            m.evidence = required(evidence, 255);
            return save(r, m, "ACTIVITY_" + activity.name(), actor, now);
        });
    }
    Reservation extend(Fence fence, boolean keep, CustomerGrant checkedGrant, String evidence, String actor, Instant requestedAt) {
        return locked(() -> {
            Instant now = effectiveTime(requestedAt);
            Reservation r = fenced(fence); CustomerGrant grant = customerGrant(r.customerId()); grant(grant, now);
            if (!json.fingerprint(grant).equals(json.fingerprint(checkedGrant))) throw new AdmissionFailure(CONFLICT, "CUSTOMER_GRANT_CHANGED");
            LifecyclePolicy p = reviewed("POLICY", r.policyId(), FleetPolicy.class).lifecycle();
            if (r.intent() != Intent.CUSTOMER_STAGING || !r.consumesCapacity() || r.expiryState() == ExpiryState.CLAIMED
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
        });
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
            if (!r.consumesCapacity() || r.intent() != Intent.CUSTOMER_STAGING || r.expiryState() == ExpiryState.CLAIMED
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
                || !r.consumesCapacity())
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
    ExpiryWork claimExpiry(Fence fence, String actor, Instant requestedAt) {
        return locked(() -> {
            Instant now = effectiveTime(requestedAt);
            Reservation r = fenced(fence); LifecyclePolicy p = reviewed("POLICY", r.policyId(), FleetPolicy.class).lifecycle();
            if (r.intent() != Intent.CUSTOMER_STAGING || !r.consumesCapacity() || r.expiryState() != ExpiryState.NOTIFIED
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
    private Reservation save(Reservation r, Mutation m, String type, String actor, Instant now) {
        Reservation next = m.build(r, now);
        int changed = jdbc.update("""
            UPDATE deployment_capacity_reservations SET revision=?,state=?,current_slot=?,expires_at=?,next_retry_at=?,
              expiry_state=?,payload_json=?,updated_at=? WHERE id=? AND revision=? AND generation=?
            """, next.revision(), next.state().name(), next.consumesCapacity() ? slot(next) : null, time(next.expiresAt()),
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
        PrerequisiteReadback prerequisiteReadback;
        Mutation(Reservation r) {
            state = r.state(); expiryState = r.expiryState(); lastActivity = r.lastActivityAt(); expires = r.expiresAt();
            extensions = r.extensions(); keepUntil = r.keepUntil(); keepEntitlement = r.keepEntitlementReference();
            noticeCycle = r.noticeCycle(); nextRetry = r.nextRetryAt(); attempts = r.attempts(); worker = r.workerToken();
            drainDeadline = r.drainDeadline(); recoveryUntil = r.recoveryUntil();
            retirement = r.retirementOperationId(); evidence = r.evidenceReference(); failure = r.failureCode();
            prerequisiteReadback = r.prerequisiteReadback();
        }
        Reservation build(Reservation r, Instant now) {
            return new Reservation(r.id(), r.customerId(), r.tenantId(), r.deploymentId(), r.targetProfileId(), r.intent(),
                r.operationKey(), r.requestFingerprint(), r.generation(), Math.incrementExact(r.revision()), state, r.demand(),
                r.policyId(), r.manifestId(), r.manifestFingerprint(), r.bindingFingerprint(), r.createdAt(), now, lastActivity,
                expires, extensions, keepUntil, keepEntitlement, expiryState, noticeCycle, nextRetry, attempts, worker,
                drainDeadline, recoveryUntil, retirement, evidence, failure, r.customerContextFingerprint(),
                r.customerOperation(), prerequisiteReadback);
        }
    }
}
