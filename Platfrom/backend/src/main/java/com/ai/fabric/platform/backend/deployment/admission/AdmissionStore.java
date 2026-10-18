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

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static com.ai.fabric.platform.backend.deployment.admission.AdmissionRules.*;
import static org.springframework.http.HttpStatus.*;

/** All ledger writes serialize on a database row, across threads/processes and across target pools. */
@Repository
class AdmissionStore {
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
            AdmissionContext current = context(checked.customerId(), checked.tenantId(), checked.deploymentId(), command.manifestId());
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
            || allocation.intent() != current.target().intent() || !allocation.policyId().equals(current.policy().id())) {
            throw new AdmissionFailure(CONFLICT, "ALLOCATION_REAPPLY_SCOPE_INVALID");
        }
        AdmissionContext previous = context(allocation.customerId(), allocation.tenantId(), allocation.deploymentId(), allocation.manifestId());
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
        if (!reservation.customerId().equals(current.customerId()) || !reservation.tenantId().equals(current.tenantId())
            || !reservation.deploymentId().equals(current.deploymentId())
            || !reservation.targetProfileId().equals(current.target().targetProfileId())
            || reservation.intent() != current.target().intent() || !reservation.policyId().equals(current.policy().id())
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

    void requireCustomerAuthorization(Reservation reservation,
        com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.AdmissionContext requested) {
        var receipts = authorizationRows(requested.customerId(), requested.deploymentId(), requested.targetProfileId(), requested.operationKey());
        if (receipts.size() != 1) throw new AdmissionFailure(PRECONDITION_FAILED, "CUSTOMER_OPERATION_AUTHORIZATION_REQUIRED");
        AuthorizationReceipt receipt = receipts.getFirst();
        reservation = candidateAuthorization(reservation, receipt);
        Manifest manifest = reviewed("MANIFEST", reservation.manifestId(), Manifest.class);
        if (!reservation.id().equals(receipt.reservationId()) || !reservation.generation().equals(receipt.generation())
            || !reservation.manifestFingerprint().equals(receipt.manifestFingerprint())
            || !json.fingerprint(requested).equals(receipt.contextFingerprint())
            || !receipt.requestFingerprint().equals(commandFingerprint(requested.customerId(), requested.tenantId(),
                requested.deploymentId(), manifest, receipt.command()))
            || !requested.equals(receipt.command().customerContext())) {
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
        AdmissionContext candidate = context(allocation.customerId(), allocation.tenantId(), allocation.deploymentId(), command.manifestId());
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
                     WHERE state<>'RELEASED' AND id>? ORDER BY id LIMIT ?
                    """, (rs, row) -> json.read(rs.getString(1), Reservation.class), cursor, size));
            }
            if (batch.size() < size) {
                int remaining = size - batch.size();
                String sql = cursor == null
                    ? "SELECT payload_json FROM deployment_capacity_reservations WHERE state<>'RELEASED' ORDER BY id LIMIT ?"
                    : "SELECT payload_json FROM deployment_capacity_reservations WHERE state<>'RELEASED' AND id<=? ORDER BY id LIMIT ?";
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
                return new ReleasePrerequisiteAllocation(reservation, binding, receipt.command().customerContext());
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
        var scope = command == null ? null : command.customerContext();
        if (scope == null || !java.util.Set.of(
                com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation.APPLY,
                com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation.PROMOTION_APPLY).contains(scope.operation())
            || !customerId.equals(reservation.customerId()) || !tenantId.equals(reservation.tenantId())
            || !deploymentId.equals(reservation.deploymentId()) || !binding.targetProfileId().equals(reservation.targetProfileId())
            || !binding.generation().equals(reservation.generation()) || !releaseId.equals(currentReleaseId(reservation))
            || !binding.versionId().equals(manifest.versionId()) || !binding.versionConfigHash().equals(manifest.versionConfigHash())
            || !reservation.id().equals(receipt.reservationId()) || !reservation.generation().equals(receipt.generation())
            || !reservation.manifestFingerprint().equals(receipt.manifestFingerprint())
            || !json.fingerprint(scope).equals(receipt.contextFingerprint())
            || !receipt.requestFingerprint().equals(commandFingerprint(customerId, tenantId, deploymentId, manifest, command))
            || !customerId.equals(scope.customerId()) || !tenantId.equals(scope.tenantId())
            || !deploymentId.equals(scope.deploymentId()) || !binding.targetProfileId().equals(scope.targetProfileId())
            || !binding.authorizationKey().equals(scope.operationKey()) || reservation.state() != State.ACTIVE
            || reservation.expiryState() != ExpiryState.CURRENT || !reservation.expiresAt().isAfter(clock.instant())
            || reservation.workerToken() != null || reservation.failureCode() != null) {
            throw new AdmissionFailure(PRECONDITION_FAILED, "ACTIVE_RELEASE_ALLOCATION_INVALID");
        }
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
        AdmissionContext current = context(deployment.getCustomerId(), deployment.getTenantId(), deployment.getId(), authorized.manifestId());
        requireUsableAllocation(authorized, current, clock.instant());
        requireCustomerAuthorization(authorized, receipt.command().customerContext());
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
        if ("ACTIVE".equals(state)) jdbc.update("""
            UPDATE deployment_admission_release_bindings SET state='SUPERSEDED'
             WHERE reservation_id=? AND generation=? AND release_id<>? AND state='ACTIVE'
            """, binding.reservationId(), binding.generation(), releaseId);
        jdbc.update("UPDATE deployment_admission_release_bindings SET state=?,updated_at=? WHERE release_id=?",
            state, time(clock.instant()), releaseId);
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
            AdmissionContext current = context(r.customerId(), r.tenantId(), r.deploymentId(), r.manifestId());
            if (!json.fingerprint(checked).equals(json.fingerprint(current)) || !r.policyId().equals(current.policy().id())
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
            AdmissionContext current = context(binding.customerId(), binding.tenantId(), binding.deploymentId(), reservation.manifestId());
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
            if (r.intent() != Intent.CUSTOMER_STAGING || !r.consumesCapacity()
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
            if (!Objects.equals(r.noticeCycle(), required(noticeCycle, 64)) || !r.consumesCapacity()
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
        String manifestId; String manifestFingerprint; String bindingFingerprint;
        PrerequisiteReadback prerequisiteReadback;
        Mutation(Reservation r) {
            state = r.state(); expiryState = r.expiryState(); lastActivity = r.lastActivityAt(); expires = r.expiresAt();
            extensions = r.extensions(); keepUntil = r.keepUntil(); keepEntitlement = r.keepEntitlementReference();
            noticeCycle = r.noticeCycle(); nextRetry = r.nextRetryAt(); attempts = r.attempts(); worker = r.workerToken();
            drainDeadline = r.drainDeadline(); recoveryUntil = r.recoveryUntil();
            retirement = r.retirementOperationId(); evidence = r.evidenceReference(); failure = r.failureCode();
            prerequisiteReadback = r.prerequisiteReadback();
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
                r.customerOperation(), prerequisiteReadback);
        }
    }
}
