package com.ai.fabric.platform.backend.deployment.retirement;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.admission.AdmissionTargetLifecycleInterlock;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

import static com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.*;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/** All transactions here are local/short. Never invoke provider or participant methods inside them. */
@Service
public class TargetRetirementStore {
    static final int MAX_ATTEMPTS = 8;
    static final Duration LEASE = Duration.ofMinutes(2);
    private final TargetRetirementOperationRepository operations;
    private final TargetRetirementGateService gates;
    private final TargetRetirementGateRepository gateRepository;
    private final TargetRetirementInventory inventory;
    private final TargetRetirementJson json;
    private final PlatformAuditService audit;
    private final AdmissionTargetLifecycleInterlock admissionInterlock;
    private final TransactionTemplate transactions;

    public TargetRetirementStore(TargetRetirementOperationRepository operations, TargetRetirementGateService gates,
                                 TargetRetirementGateRepository gateRepository, TargetRetirementInventory inventory,
                                 TargetRetirementJson json, PlatformAuditService audit,
                                 AdmissionTargetLifecycleInterlock admissionInterlock,
                                 PlatformTransactionManager manager) {
        this.operations = operations;
        this.gates = gates;
        this.gateRepository = gateRepository;
        this.inventory = inventory;
        this.json = json;
        this.audit = audit;
        this.admissionInterlock = admissionInterlock;
        this.transactions = new TransactionTemplate(manager);
    }

    public Summary plan(String deploymentId, String targetId, PlanRequest request) {
        return plan(deploymentId, targetId, request, null);
    }

    public Summary planForAdmissionExpiry(String deploymentId, String targetId, String reservationId,
                                          String generation, PlanRequest request) {
        if (reservationId == null || reservationId.isBlank() || generation == null || generation.isBlank()) {
            throw conflict("ACTIVE_ADMISSION_RESERVATION");
        }
        return plan(deploymentId, targetId, request, new AdmissionExpiryReservation(reservationId, generation));
    }

    private Summary plan(String deploymentId, String targetId, PlanRequest request,
                         AdmissionExpiryReservation expiryReservation) {
        var preparation = transactions.execute(tx -> {
            var gate = gates.lock(deploymentId, targetId);
            var existing = operations.findByDeploymentIdAndTargetProfileIdAndOperationKey(deploymentId, targetId, request.operationKey());
            if (existing.isPresent()) {
                if (!json.fingerprint(request).equals(json.fingerprint(request(existing.get())))) throw conflict("OPERATION_KEY_REUSED");
                return new PlanPreparation(summary(existing.get()), gate.getEpoch());
            }
            if (!"AVAILABLE".equals(gate.getState())) throw conflict("TARGET_RETIREMENT_FENCED");
            requireAdmissionInterlock(deploymentId, targetId, expiryReservation);
            return new PlanPreparation(null, gate.getEpoch());
        });
        if (preparation.existing() != null) return preparation.existing();
        Snapshot snapshot;
        try { snapshot = inventory.capture(deploymentId, targetId, request, preparation.epoch()); }
        catch (TargetRetirementFailure ex) { throw conflict(ex.code()); }
        return transactions.execute(tx -> {
            var gate = gates.lock(deploymentId, targetId);
            var existing = operations.findByDeploymentIdAndTargetProfileIdAndOperationKey(deploymentId, targetId, request.operationKey());
            if (existing.isPresent()) {
                if (!json.fingerprint(request).equals(json.fingerprint(request(existing.get())))) throw conflict("OPERATION_KEY_REUSED");
                return summary(existing.get());
            }
            if (!"AVAILABLE".equals(gate.getState()) || gate.getEpoch() != preparation.epoch()) {
                throw conflict("TARGET_GENERATION_CHANGED");
            }
            requireAdmissionInterlock(deploymentId, targetId, expiryReservation);
            try { inventory.recheckLocal(snapshot, request); }
            catch (TargetRetirementFailure ex) { throw conflict(ex.code()); }
            var operation = new TargetRetirementOperationEntity();
            operation.setId(UUID.randomUUID().toString());
            operation.setCustomerId(snapshot.customerId());
            operation.setDeploymentId(deploymentId);
            operation.setTargetProfileId(targetId);
            operation.setOperationKey(request.operationKey());
            operation.setRequestedByActorId(PlatformSecurityContext.actorIdOrSystem());
            operation.setRequestedByRole(PlatformSecurityContext.actorRoleOrSystem());
            operation.setRequestJson(json.write(request));
            operation.setSnapshotJson(json.write(snapshot));
            operation.setRequestFingerprint(json.fingerprint(Map.of("request", request, "snapshot", snapshot)));
            operation.setState(State.PLANNED);
            operation.setStep(Step.DRAIN);
            operation.setDrainDeadline(request.drainDeadline());
            operation.setResultJson(json.write(new Progress(Map.of(), List.of())));
            operation.setCreatedAt(Instant.now());
            operation.setUpdatedAt(operation.getCreatedAt());
            operations.saveAndFlush(operation);
            record(operation, "TARGET_RETIREMENT_PLANNED");
            return summary(operation);
        });
    }

    private record AdmissionExpiryReservation(String id, String generation) { }
    private record PlanPreparation(Summary existing, long epoch) { }
    private record ObservedOperation(Snapshot snapshot, PlanRequest request) { }

    private void requireAdmissionInterlock(String deploymentId, String targetId,
                                           AdmissionExpiryReservation expiryReservation) {
        if (expiryReservation == null) {
            admissionInterlock.requireNoActiveReservation(deploymentId, targetId);
        } else {
            admissionInterlock.requireOnlyClaimedReservation(deploymentId, targetId,
                expiryReservation.id(), expiryReservation.generation());
        }
    }

    public Summary execute(String id, Command command) {
        var observed = observe(id, State.PLANNED);
        return change(id, operation -> {
            requireCommand(operation, command);
            if (operation.getState() != State.PLANNED) return summary(operation);
            requireObserved(operation, observed);
            var snapshot = snapshot(operation);
            var gate = gates.lock(operation.getDeploymentId(), operation.getTargetProfileId());
            if (!"AVAILABLE".equals(gate.getState()) || gate.getEpoch() != snapshot.targetEpoch())
                throw conflict("TARGET_GENERATION_CHANGED");
            gate.setEpoch(gate.getEpoch() + 1);
            gate.setState("RETIRING");
            gate.setOperationId(id);
            gate.setUpdatedAt(Instant.now());
            gateRepository.save(gate);
            operation.setFenceEpoch(gate.getEpoch());
            operation.setAttempt(1);
            operation.setStartedAt(Instant.now());
            operation.setStepStartedAt(Instant.now());
            if (request(operation).retention().decision() == RetentionDecision.RETAIN_UNTIL
                && request(operation).retention().expiresAt().isAfter(Instant.now())) {
                operation.setState(State.RETAINED);
                operation.setNextRetryAt(request(operation).retention().expiresAt());
            } else operation.setState(State.QUEUED);
            record(operation, "TARGET_RETIREMENT_REQUESTED");
            return summary(operation);
        });
    }

    public void preview(String id, List<String> blockers) {
        change(id, operation -> {
            if (operation.getState() == State.PLANNED) {
                operation.setResultJson(json.write(new Progress(progress(operation).effects(), progress(operation).evidence(), progress(operation).interlockEvidence(), blockers)));
            }
            return null;
        });
    }

    public Summary retry(String id, Command command, RecoveryEvidence recovery) {
        return change(id, operation -> {
            requireCommand(operation, command);
            if (List.of(State.QUEUED, State.RUNNING, State.WAITING, State.RETAINED, State.COMPLETE).contains(operation.getState()))
                return summary(operation);
            if (!(operation.getState() == State.BLOCKED || operation.getState() == State.RETRYABLE_FAILURE))
                throw conflict("RETIREMENT_NOT_RETRYABLE");
            if (operation.getAttempt() >= MAX_ATTEMPTS) throw conflict("RETIREMENT_ATTEMPTS_EXHAUSTED");
            if (operation.getNextRetryAt() != null && operation.getNextRetryAt().isAfter(Instant.now()))
                throw conflict("RETIREMENT_BACKOFF_ACTIVE");
            if (operation.getInFlightEffect() != null) {
                requireRecovery(operation, recovery);
                operation.setInFlightEffect(null);
            }
            requireFence(operation);
            operation.setWorkerToken(null);
            operation.setLeaseExpiresAt(null);
            operation.setState(State.QUEUED);
            operation.setAttempt(operation.getAttempt() + 1);
            operation.setStepStartedAt(Instant.now());
            operation.setNextRetryAt(null);
            operation.setErrorCode(null);
            record(operation, "TARGET_RETIREMENT_RETRY_REQUESTED");
            return summary(operation);
        });
    }

    public Summary cancel(String id, Command command) {
        return change(id, operation -> {
            requireCommand(operation, command);
            if (operation.getState() == State.CANCELLED) return summary(operation);
            if (operation.isDestructiveStarted() || operation.getState() == State.COMPLETE)
                throw conflict("RETIREMENT_CANCEL_UNSAFE");
            if (operation.getState() == State.PLANNED) {
                operation.setState(State.CANCELLED);
                operation.setCompletedAt(Instant.now());
            } else {
                requireFence(operation);
                operation.setState(State.CANCEL_REQUESTED);
                operation.setNextRetryAt(null);
                operation.setStep(Step.CANCEL_DRAIN);
                operation.setStepStartedAt(Instant.now());
            }
            record(operation, "TARGET_RETIREMENT_CANCEL_REQUESTED");
            return summary(operation);
        });
    }

    public Context claim(String id) {
        return change(id, operation -> {
            Instant now = Instant.now();
            if (!List.of(State.QUEUED, State.WAITING, State.RUNNING, State.RETAINED, State.CANCEL_REQUESTED).contains(operation.getState()))
                return null;
            if (operation.getState() == State.RETAINED && request(operation).retention().expiresAt().isAfter(now)) return null;
            if (operation.getNextRetryAt() != null && operation.getNextRetryAt().isAfter(now)) return null;
            if (operation.getWorkerToken() != null) {
                if (operation.getLeaseExpiresAt().isAfter(now)) return null;
                if (operation.getInFlightEffect() != null) {
                    block(operation, "WORKER_OUTCOME_UNKNOWN", true);
                    record(operation, "TARGET_RETIREMENT_WORKER_OUTCOME_UNKNOWN");
                    return null;
                }
            }
            try { requireFence(operation); }
            catch (TargetRetirementFailure ex) { block(operation, ex.code(), false); return null; }
            if (operation.getState() == State.CANCEL_REQUESTED) operation.setStep(Step.CANCEL_DRAIN);
            operation.setWorkerToken(UUID.randomUUID().toString());
            operation.setLeaseExpiresAt(now.plus(LEASE));
            operation.setState(State.RUNNING);
            operation.setNextRetryAt(null);
            return context(operation);
        });
    }

    public void prepareEffect(Context context, String effectKey, boolean destructive) {
        var observed = observe(context.operationId(), State.RUNNING, State.CANCEL_REQUESTED);
        change(context.operationId(), operation -> {
            requireWorker(operation, context);
            if (operation.getState() == State.CANCEL_REQUESTED && destructive) throw new TargetRetirementFailure("CANCEL_PENDING");
            requireFence(operation);
            requireObserved(operation, observed);
            operation.setInFlightEffect(effectKey);
            operation.setDestructiveStarted(operation.isDestructiveStarted() || destructive);
            return null;
        });
    }

    public void recheck(Context context) {
        var observed = observe(context.operationId(), State.RUNNING, State.CANCEL_REQUESTED);
        change(context.operationId(), operation -> {
            requireWorker(operation, context);
            requireFence(operation);
            requireObserved(operation, observed);
            return null;
        });
    }

    public void finishEffect(Context context, String key, EffectResult result, Step next) {
        change(context.operationId(), operation -> {
            requireWorker(operation, context);
            requireFence(operation);
            if (operation.getStep() == Step.RESOURCES && result.complete()) {
                var resource = snapshot(operation).resources().stream().filter(value -> !value.preserved()
                    && TargetRetirementWorker.effectKey(context, Step.RESOURCES, value.handleId()).equals(key)).findFirst()
                    .orElseThrow(() -> new TargetRetirementFailure("RESOURCE_EFFECT_MISMATCH"));
                inventory.recordReclaimed(resource, result.observedAt());
            }
            var effects = new LinkedHashMap<>(progress(operation).effects());
            effects.put(key, result);
            operation.setResultJson(json.write(new Progress(Map.copyOf(effects), progress(operation).evidence(), progress(operation).interlockEvidence(), List.of())));
            operation.setInFlightEffect(null);
            releaseWorker(operation);
            if (operation.getState() == State.CANCEL_REQUESTED) {
                operation.setStep(Step.CANCEL_DRAIN);
            } else {
                operation.setState(result.complete() ? State.QUEUED : State.WAITING);
                if (result.complete() && next != operation.getStep()) operation.setStepStartedAt(Instant.now());
                operation.setStep(result.complete() ? next : operation.getStep());
                operation.setNextRetryAt(result.complete() ? null : Instant.now().plusSeconds(15));
            }
            record(operation, "TARGET_RETIREMENT_STEP_OBSERVED");
            return null;
        });
    }

    public void finish(Context context, boolean cancelled) {
        var observed = observe(context.operationId(), State.RUNNING, State.CANCEL_REQUESTED);
        change(context.operationId(), operation -> {
            requireWorker(operation, context);
            requireFence(operation);
            requireObserved(operation, observed);
            var gate = gates.lock(operation.getDeploymentId(), operation.getTargetProfileId());
            gate.setState(cancelled ? "AVAILABLE" : "RETIRED");
            gate.setEpoch(gate.getEpoch() + 1);
            gate.setOperationId(cancelled ? null : operation.getId());
            gate.setUpdatedAt(Instant.now());
            gateRepository.save(gate);
            operation.setState(cancelled ? State.CANCELLED : State.COMPLETE);
            operation.setCompletedAt(Instant.now());
            operation.setInFlightEffect(null);
            operation.setNextRetryAt(null);
            operation.setErrorCode(null);
            releaseWorker(operation);
            record(operation, cancelled ? "TARGET_RETIREMENT_CANCELLED" : "TARGET_RETIREMENT_COMPLETED");
            return null;
        });
    }

    public void failure(Context context, String code, boolean uncertain) {
        change(context.operationId(), operation -> {
            if (!Objects.equals(operation.getWorkerToken(), context.workerToken())) return null;
            block(operation, code, uncertain || operation.getInFlightEffect() != null);
            record(operation, "TARGET_RETIREMENT_BLOCKED");
            return null;
        });
    }

    private void block(TargetRetirementOperationEntity operation, String code, boolean uncertain) {
        operation.setState(uncertain ? State.RETRYABLE_FAILURE : State.BLOCKED);
        operation.setErrorCode(code);
        operation.setResultJson(json.write(new Progress(progress(operation).effects(), progress(operation).evidence(), progress(operation).interlockEvidence(), List.of(code))));
        long backoff = Math.min(900, 15L << Math.min(operation.getAttempt(), 6));
        operation.setNextRetryAt(Instant.now().plusSeconds(backoff));
        if (!uncertain) {
            operation.setInFlightEffect(null);
            releaseWorker(operation);
        } else operation.setLeaseExpiresAt(Instant.now());
    }

    private void requireRecovery(TargetRetirementOperationEntity operation, RecoveryEvidence recovery) {
        if (recovery == null || !recovery.workerFenced()
            || !(recovery.remoteOutcomeSettled() || recovery.resumeSafe())
            || !operation.getId().equals(recovery.operationId())
            || !Objects.equals(operation.getWorkerToken(), recovery.previousWorkerToken())
            || !operation.getInFlightEffect().equals(recovery.effectKey())
            || recovery.reference() == null || recovery.reference().isBlank()
            || recovery.observedAt() == null || recovery.observedAt().isAfter(Instant.now())
            || recovery.observedAt().isBefore(Instant.now().minusSeconds(60))) throw conflict("RECOVERY_EVIDENCE_REQUIRED");
        var effects = new LinkedHashMap<>(progress(operation).effects());
        effects.put("recovery:" + operation.getAttempt(), new EffectResult(true, recovery.reference(), recovery.observedAt()));
        operation.setResultJson(json.write(new Progress(effects, progress(operation).evidence(), progress(operation).interlockEvidence(), List.of())));
    }

    private void requireWorker(TargetRetirementOperationEntity operation, Context context) {
        if (!Objects.equals(operation.getWorkerToken(), context.workerToken()) || operation.getLeaseExpiresAt() == null
            || !operation.getLeaseExpiresAt().isAfter(Instant.now())
            || !List.of(State.RUNNING, State.CANCEL_REQUESTED).contains(operation.getState()))
            throw new TargetRetirementFailure("STALE_WORKER");
    }

    private void requireFence(TargetRetirementOperationEntity operation) {
        var gate = gates.lock(operation.getDeploymentId(), operation.getTargetProfileId());
        if (!"RETIRING".equals(gate.getState()) || !operation.getId().equals(gate.getOperationId())
            || gate.getEpoch() != operation.getFenceEpoch()) throw new TargetRetirementFailure("TARGET_FENCE_CHANGED");
    }

    private <T> T change(String id, Function<TargetRetirementOperationEntity, T> action) {
        var known = require(id);
        return transactions.execute(tx -> {
            gates.lock(known.getDeploymentId(), known.getTargetProfileId());
            var operation = operations.findForUpdate(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Retirement not found."));
            operation.setUpdatedAt(Instant.now());
            T result = action.apply(operation);
            operations.saveAndFlush(operation);
            return result;
        });
    }

    private ObservedOperation observe(String id, State... eligibleStates) {
        var operation = require(id);
        if (eligibleStates.length > 0 && java.util.Arrays.stream(eligibleStates)
            .noneMatch(state -> state == operation.getState())) return null;
        var snapshot = snapshot(operation);
        var request = request(operation);
        inventory.recheck(snapshot, request);
        return new ObservedOperation(snapshot, request);
    }

    private void requireObserved(TargetRetirementOperationEntity operation, ObservedOperation observed) {
        if (observed == null
            || !json.fingerprint(snapshot(operation)).equals(json.fingerprint(observed.snapshot()))
            || !json.fingerprint(request(operation)).equals(json.fingerprint(observed.request()))) {
            throw new TargetRetirementFailure("SNAPSHOT_GENERATION_CHANGED");
        }
        inventory.recheckLocal(observed.snapshot(), observed.request());
    }

    public void evidence(Context context, Map<ParticipantKind, Evidence> evidence, Evidence interlockEvidence) {
        change(context.operationId(), operation -> {
            if (operation.getState() != State.PLANNED) requireWorker(operation, context);
            operation.setResultJson(json.write(new Progress(progress(operation).effects(), evidence, interlockEvidence, progress(operation).blockers())));
            return null;
        });
    }

    public TargetRetirementOperationEntity require(String id) {
        return operations.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Retirement not found."));
    }
    public Summary summary(TargetRetirementOperationEntity operation) {
        var request = request(operation);
        return new Summary(operation.getId(), operation.getDeploymentId(), operation.getTargetProfileId(), operation.getCustomerId(),
            operation.getOperationKey(), operation.getRequestFingerprint(), operation.getState(), operation.getStep(), snapshot(operation),
            operation.getDrainDeadline(), request.retention(), operation.getAttempt(), operation.getNextRetryAt(), operation.getErrorCode(),
            progress(operation), operation.getState() != State.COMPLETE, operation.getInFlightEffect(), operation.getCreatedAt(),
            operation.getStartedAt(), operation.getCompletedAt(), operation.getUpdatedAt());
    }
    public Context context(TargetRetirementOperationEntity operation) {
        return new Context(operation.getId(), operation.getOperationKey(), operation.getRequestFingerprint(), snapshot(operation), operation.getFenceEpoch(),
            operation.getWorkerToken(), operation.getRequestedByActorId(), operation.getRequestedByRole(), operation.getDrainDeadline(), request(operation).retention());
    }
    public PlanRequest request(TargetRetirementOperationEntity operation) { return json.read(operation.getRequestJson(), PlanRequest.class); }
    public Snapshot snapshot(TargetRetirementOperationEntity operation) { return json.read(operation.getSnapshotJson(), Snapshot.class); }
    public Progress progress(TargetRetirementOperationEntity operation) { return json.read(operation.getResultJson(), Progress.class); }
    public void requireCommand(TargetRetirementOperationEntity operation, Command command) {
        if (command == null || !operation.getRequestFingerprint().equals(command.requestFingerprint()))
            throw conflict("REQUEST_FINGERPRINT_CHANGED");
    }
    public void requireRetryAllowed(TargetRetirementOperationEntity operation) {
        if (operation.getState() != State.BLOCKED && operation.getState() != State.RETRYABLE_FAILURE) return;
        if (operation.getAttempt() >= MAX_ATTEMPTS) throw conflict("RETIREMENT_ATTEMPTS_EXHAUSTED");
        if (operation.getNextRetryAt() != null && operation.getNextRetryAt().isAfter(Instant.now()))
            throw conflict("RETIREMENT_BACKOFF_ACTIVE");
    }
    private void releaseWorker(TargetRetirementOperationEntity operation) {
        operation.setWorkerToken(null);
        operation.setLeaseExpiresAt(null);
    }
    private void record(TargetRetirementOperationEntity operation, String action) {
        audit.record(action, "TARGET_RETIREMENT", operation.getId(), Map.of("deploymentId", operation.getDeploymentId(),
            "targetProfileId", operation.getTargetProfileId(), "requestedBy", operation.getRequestedByActorId(),
            "requestFingerprint", operation.getRequestFingerprint(), "state", operation.getState().name(), "step", operation.getStep().name()));
    }
    private ResponseStatusException conflict(String code) { return new ResponseStatusException(CONFLICT, code); }
}
