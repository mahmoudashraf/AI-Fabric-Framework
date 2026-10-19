package com.ai.fabric.platform.backend.deployment.retirement;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;

import static com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.*;

@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class TargetRetirementWorker {
    private final TargetRetirementStore store;
    private final TargetRetirementParticipation participation;
    private final TargetRetirementProviderResources resources;

    public TargetRetirementWorker(TargetRetirementStore store, TargetRetirementParticipation participation,
                                  TargetRetirementProviderResources resources) {
        this.store = store;
        this.participation = participation;
        this.resources = resources;
    }

    /** Processes at most one durable step. A restart resumes the same snapshot, never the latest release. */
    public void runNext(String operationId) {
        Context context = store.claim(operationId);
        if (context == null) return;
        try {
            store.recheck(context);
            Step step = store.require(operationId).getStep();
            if (step == Step.VERIFY) { verify(context); return; }
            if (step == Step.RESOURCES) { reclaimResource(context); return; }
            if (step == Step.CANCEL_DRAIN) { cancel(context); return; }
            runParticipant(context, step);
        } catch (TargetRetirementFailure ex) {
            store.failure(context, ex.code(), false);
        } catch (RuntimeException ex) {
            // Provider payloads/errors can contain credentials. Persist bounded codes and readback refs only.
            store.failure(context, "REMOTE_OUTCOME_UNKNOWN", true);
        }
    }

    private void runParticipant(Context context, Step step) {
        var interlock = participation.interlock();
        if (step == Step.DEPENDENCIES) requireCompleted(context, Step.DRAIN, Step.AUTHORITY, Step.INDEX);
        else if (step != Step.DRAIN) inspection(context, true);
        String key = effectKey(context, step, "target");
        var participant = participation.participant(kind(step));
        store.prepareEffect(context, key, step != Step.DRAIN);
        EffectResult result = interlock.underFence(context, key, () -> {
            store.recheck(context);
            if (step == Step.DEPENDENCIES) requireCompleted(context, Step.DRAIN, Step.AUTHORITY, Step.INDEX);
            else if (step != Step.DRAIN) inspection(context, true);
            var prior = participant.readback(context, step, key);
            participation.validate(prior);
            if (!prior.complete()) {
                var accepted = participant.apply(context, step, key);
                participation.validate(accepted);
            }
            var readback = participant.readback(context, step, key);
            participation.validate(readback);
            if (step == Step.DRAIN) {
                var proof = inspection(context, false).evidence().get(ParticipantKind.RUNTIME_DRAIN);
                try { participation.requireDrained(proof); }
                catch (TargetRetirementFailure ex) {
                    if (!context.drainDeadline().isAfter(Instant.now())) throw new TargetRetirementFailure("DRAIN_DEADLINE_EXCEEDED");
                    return new EffectResult(false, readback.evidenceReference(), readback.observedAt());
                }
            } else if (readback.complete()) {
                Evidence proof = step == Step.DEPENDENCIES
                    ? refreshEvidence(context, ParticipantKind.DEPENDENCIES)
                    : inspection(context, true).evidence().get(kind(step));
                requireCompleteProof(proof, "PARTICIPANT_READBACK_INCOMPLETE_" + kind(step));
            }
            return readback;
        });
        participation.validate(result);
        requireBoundedWait(context, step, result);
        store.finishEffect(context, key, result, next(step));
    }

    private void reclaimResource(Context context) {
        requireCompleted(context, Step.DRAIN, Step.AUTHORITY, Step.INDEX);
        var progress = store.progress(store.require(context.operationId()));
        var owned = context.snapshot().resources().stream().filter(resource -> !resource.preserved()).toList();
        var remaining = owned.stream().filter(resource -> {
            var result = progress.effects().get(effectKey(context, Step.RESOURCES, resource.handleId()));
            return result == null || !result.complete();
        }).findFirst();
        if (remaining.isEmpty()) {
            // The stage advances using an actual completed resource readback, never an invented empty drain.
            var last = owned.get(owned.size() - 1);
            String key = effectKey(context, Step.RESOURCES, last.handleId());
            store.finishEffect(context, key, progress.effects().get(key), Step.DEPENDENCIES);
            return;
        }
        var resource = remaining.get();
        String key = effectKey(context, Step.RESOURCES, resource.handleId());
        store.prepareEffect(context, key, true);
        EffectResult result = participation.interlock().underFence(context, key, () -> {
            store.recheck(context);
            requireCompleted(context, Step.DRAIN, Step.AUTHORITY, Step.INDEX);
            var readback = resources.readback(context, resource);
            if (readback.complete()) return absence(context, resource);
            // Accepted asynchronous deletes are polled, not resubmitted on every worker tick.
            if (progress.effects().containsKey(key)) return readback;
            var deleted = resources.delete(context, resource);
            return deleted.complete() ? absence(context, resource) : deleted;
        });
        participation.validate(result);
        requireBoundedWait(context, Step.RESOURCES, result);
        store.finishEffect(context, key, result, Step.RESOURCES);
    }

    private void verify(Context context) {
        requireCompleted(context, Step.DRAIN, Step.AUTHORITY, Step.INDEX, Step.DEPENDENCIES);
        String key = effectKey(context, Step.VERIFY, "target");
        var verified = participation.interlock().underFence(context, key, () -> {
            store.recheck(context);
            requireCompleted(context, Step.DRAIN, Step.AUTHORITY, Step.INDEX, Step.DEPENDENCIES);
            refreshEvidence(context, ParticipantKind.DEPENDENCIES);
            requirePersistedEvidence(context);
            for (var resource : context.snapshot().resources()) {
                if (!resource.preserved()) {
                    if (!resources.readback(context, resource).complete() || !absence(context, resource).complete())
                        throw new TargetRetirementFailure("PROVIDER_RESOURCE_STILL_PRESENT");
                }
            }
            return new EffectResult(true, "target-retirement:" + context.operationId() + ":verified", Instant.now());
        });
        participation.validate(verified);
        if (!verified.complete()) throw new TargetRetirementFailure("PARTIAL_CLEANUP_REMAINS");
        store.finish(context, false);
    }

    private Evidence refreshEvidence(Context context, ParticipantKind kind) {
        var progress = store.progress(store.require(context.operationId()));
        var evidence = new EnumMap<ParticipantKind, Evidence>(ParticipantKind.class);
        evidence.putAll(progress.evidence());
        Evidence participantProof = participation.participant(kind).inspect(context);
        participation.validate(context, participantProof);
        Evidence interlockProof = participation.interlock().inspect(context);
        participation.validate(context, interlockProof);
        evidence.put(kind, participantProof);
        store.evidence(context, evidence, interlockProof);
        return participantProof;
    }

    private void requirePersistedEvidence(Context context) {
        var progress = store.progress(store.require(context.operationId()));
        Instant latestEffect = null;
        for (ParticipantKind kind : ParticipantKind.values()) {
            Evidence proof = progress.evidence().get(kind);
            EffectResult effect = progress.effects().get(effectKey(context, step(kind), "target"));
            if (effect == null || !effect.complete() || effect.observedAt() == null
                || effect.observedAt().isAfter(Instant.now())) {
                throw new TargetRetirementFailure("PARTICIPANT_EFFECT_RECEIPT_MISSING_" + kind);
            }
            requireCompleteProof(proof, "PARTICIPANT_READBACK_INCOMPLETE_" + kind, true);
            if (proof.observedAt().isBefore(effect.observedAt())) {
                throw new TargetRetirementFailure("PARTICIPANT_EVIDENCE_STALE_" + kind);
            }
            if (latestEffect == null || effect.observedAt().isAfter(latestEffect)) latestEffect = effect.observedAt();
            if (kind == ParticipantKind.RUNTIME_DRAIN) participation.requireDrained(proof);
        }
        requireCompleteProof(progress.interlockEvidence(), "RETIREMENT_AUTHORITY_UNPROVEN", false);
        if (latestEffect != null && progress.interlockEvidence().observedAt().isBefore(latestEffect)) {
            throw new TargetRetirementFailure("RETIREMENT_AUTHORITY_EVIDENCE_STALE");
        }
    }

    private void requireCompleteProof(Evidence proof, String code) {
        requireCompleteProof(proof, code, false);
    }

    private void requireCompleteProof(Evidence proof, String code, boolean requireDurable) {
        if (proof == null || !proof.complete() || !proof.inventoryComplete() || !proof.retentionApproved()
            || requireDurable && !proof.durable()
            || proof.blockers() == null || !proof.blockers().isEmpty()) {
            throw new TargetRetirementFailure(code);
        }
    }
    private EffectResult absence(Context context, ResourceGeneration resource) {
        var proof = participation.interlock().verifyResourceAbsent(context, resource);
        participation.validate(proof);
        return proof;
    }

    private void cancel(Context context) {
        String key = effectKey(context, Step.CANCEL_DRAIN, "target");
        var participant = participation.participant(ParticipantKind.RUNTIME_DRAIN);
        store.prepareEffect(context, key, false);
        var result = participation.interlock().underFence(context, key, () -> {
            store.recheck(context);
            var accepted = participant.apply(context, Step.CANCEL_DRAIN, key);
            participation.validate(accepted);
            var readback = participant.readback(context, Step.CANCEL_DRAIN, key);
            participation.validate(readback);
            var proof = participant.inspect(context);
            participation.validate(context, proof);
            if (proof.ingressBlocked()) return new EffectResult(false, readback.evidenceReference(), readback.observedAt());
            return readback;
        });
        participation.validate(result);
        requireBoundedWait(context, Step.CANCEL_DRAIN, result);
        if (result.complete()) store.finish(context, true);
        else store.finishEffect(context, key, result, Step.CANCEL_DRAIN);
    }
    private void requireBoundedWait(Context context, Step step, EffectResult result) {
        if (!result.complete() && step != Step.DRAIN) {
            Instant started = store.require(context.operationId()).getStepStartedAt();
            if (started == null || started.isBefore(Instant.now().minusSeconds(900)))
                throw new TargetRetirementFailure("RECLAMATION_READBACK_TIMEOUT");
        }
    }

    private TargetRetirementParticipation.Inspection inspection(Context context, boolean drained) {
        var inspection = participation.inspect(context);
        store.evidence(context, inspection.evidence(), inspection.interlockEvidence());
        if (!inspection.blockers().isEmpty()) throw new TargetRetirementFailure(inspection.blockers().get(0));
        if (drained) participation.requireDrained(inspection.evidence().get(ParticipantKind.RUNTIME_DRAIN));
        for (var resource : context.snapshot().resources()) {
            if (!resource.preserved()) participation.validate(resources.readback(context, resource));
        }
        return inspection;
    }

    private void requireCompleted(Context context, Step... steps) {
        var effects = store.progress(store.require(context.operationId())).effects();
        for (Step step : steps) {
            if (step == Step.INDEX) store.requireIndexRetentionReceipt(context);
            EffectResult result = effects.get(effectKey(context, step, "target"));
            if (result == null || !result.complete() || result.evidenceReference() == null
                || result.evidenceReference().isBlank() || result.observedAt() == null
                || result.observedAt().isAfter(Instant.now())) {
                throw new TargetRetirementFailure("PARTIAL_CLEANUP_REMAINS");
            }
        }
    }
    private ParticipantKind kind(Step step) {
        return switch (step) {
            case DRAIN, CANCEL_DRAIN -> ParticipantKind.RUNTIME_DRAIN;
            case AUTHORITY -> ParticipantKind.AUTHORITY;
            case INDEX -> ParticipantKind.INDEX;
            case DEPENDENCIES -> ParticipantKind.DEPENDENCIES;
            default -> throw new IllegalArgumentException("Not a participant step: " + step);
        };
    }
    private Step step(ParticipantKind kind) {
        return switch (kind) {
            case RUNTIME_DRAIN -> Step.DRAIN;
            case AUTHORITY -> Step.AUTHORITY;
            case INDEX -> Step.INDEX;
            case DEPENDENCIES -> Step.DEPENDENCIES;
        };
    }
    private Step next(Step step) {
        return switch (step) {
            case DRAIN -> Step.AUTHORITY;
            case AUTHORITY -> Step.INDEX;
            case INDEX -> Step.RESOURCES;
            case DEPENDENCIES -> Step.VERIFY;
            default -> throw new IllegalArgumentException("No participant successor: " + step);
        };
    }
    static String effectKey(Context context, Step step, String resource) {
        return context.operationId() + ":" + context.fenceEpoch() + ":" + step + ":" + resource;
    }
}
