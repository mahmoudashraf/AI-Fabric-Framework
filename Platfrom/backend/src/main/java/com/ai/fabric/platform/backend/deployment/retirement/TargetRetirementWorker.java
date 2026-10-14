package com.ai.fabric.platform.backend.deployment.retirement;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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
        inspection(context, step != Step.DRAIN);
        String key = effectKey(context, step, "target");
        var participant = participation.participant(kind(step));
        store.prepareEffect(context, key, step != Step.DRAIN);
        EffectResult result = participation.interlock().underFence(context, key, () -> {
            store.recheck(context);
            inspection(context, step != Step.DRAIN);
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
            }
            return readback;
        });
        participation.validate(result);
        requireBoundedWait(context, step, result);
        store.finishEffect(context, key, result, next(step));
    }

    private void reclaimResource(Context context) {
        inspection(context, true);
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
            inspection(context, true);
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
        inspection(context, true);
        String key = effectKey(context, Step.VERIFY, "target");
        var verified = participation.interlock().underFence(context, key, () -> {
            store.recheck(context);
            inspection(context, true);
            for (var resource : context.snapshot().resources()) {
                if (!resource.preserved()) {
                    if (!resources.readback(context, resource).complete() || !absence(context, resource).complete())
                        throw new TargetRetirementFailure("PROVIDER_RESOURCE_STILL_PRESENT");
                }
            }
            EffectResult last = null;
            for (var step : List.of(Step.DRAIN, Step.AUTHORITY, Step.INDEX, Step.DEPENDENCIES)) {
                last = participation.participant(kind(step)).readback(context, step, effectKey(context, step, "target"));
                participation.validate(last);
                if (!last.complete()) throw new TargetRetirementFailure("PARTIAL_CLEANUP_REMAINS");
            }
            return last;
        });
        participation.validate(verified);
        if (!verified.complete()) throw new TargetRetirementFailure("PARTIAL_CLEANUP_REMAINS");
        store.finish(context, false);
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
    private ParticipantKind kind(Step step) {
        return switch (step) {
            case DRAIN, CANCEL_DRAIN -> ParticipantKind.RUNTIME_DRAIN;
            case AUTHORITY -> ParticipantKind.AUTHORITY;
            case INDEX -> ParticipantKind.INDEX;
            case DEPENDENCIES -> ParticipantKind.DEPENDENCIES;
            default -> throw new IllegalArgumentException("Not a participant step: " + step);
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
