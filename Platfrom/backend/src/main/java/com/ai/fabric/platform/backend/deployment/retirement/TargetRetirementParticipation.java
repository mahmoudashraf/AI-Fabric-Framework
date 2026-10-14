package com.ai.fabric.platform.backend.deployment.retirement;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.*;

@Service
public class TargetRetirementParticipation {
    private final Map<ParticipantKind, TargetRetirementParticipant> participants;
    private final List<TargetRetirementInterlock> interlocks;

    public TargetRetirementParticipation(List<TargetRetirementParticipant> participants, List<TargetRetirementInterlock> interlocks) {
        var indexed = new EnumMap<ParticipantKind, TargetRetirementParticipant>(ParticipantKind.class);
        for (var participant : participants) {
            if (indexed.putIfAbsent(participant.kind(), participant) != null)
                throw new IllegalStateException("Duplicate target retirement participant: " + participant.kind());
        }
        if (interlocks.size() > 1) throw new IllegalStateException("Duplicate target retirement interlock.");
        this.participants = Map.copyOf(indexed);
        this.interlocks = List.copyOf(interlocks);
    }

    public Inspection inspect(Context context) {
        var blockers = new ArrayList<String>();
        var evidence = new EnumMap<ParticipantKind, Evidence>(ParticipantKind.class);
        Evidence authority = null;
        if (interlocks.isEmpty()) blockers.add("RETIREMENT_INTERLOCK_MISSING");
        else {
            Evidence proof = interlock().inspect(context);
            validate(context, proof);
            authority = proof;
            if (!proof.complete() || !proof.inventoryComplete() || !proof.retentionApproved()) blockers.add("RETIREMENT_AUTHORITY_UNPROVEN");
            addBlockers(blockers, proof);
        }
        for (var kind : ParticipantKind.values()) {
            var participant = participants.get(kind);
            if (participant == null) { blockers.add("PARTICIPANT_MISSING_" + kind); continue; }
            var proof = participant.inspect(context);
            validate(context, proof);
            evidence.put(kind, proof);
            if (!proof.inventoryComplete() || !proof.retentionApproved()) blockers.add("PARTICIPANT_INVENTORY_UNPROVEN_" + kind);
            if (kind == ParticipantKind.RUNTIME_DRAIN && !knownWork(proof)) blockers.add("DRAIN_WORK_UNKNOWN");
            addBlockers(blockers, proof);
        }
        return new Inspection(Map.copyOf(evidence), authority, List.copyOf(blockers));
    }

    public void requireDrained(Evidence runtime) {
        if (runtime == null || !knownWork(runtime) || !runtime.inventoryComplete()) throw new TargetRetirementFailure("DRAIN_WORK_UNKNOWN");
        if (!runtime.ingressBlocked()) throw new TargetRetirementFailure("NEW_SESSIONS_NOT_BLOCKED");
        if (runtime.humanWork() > 0) throw new TargetRetirementFailure("ACTIVE_HUMAN_WORK");
        if (runtime.confirmations() > 0) throw new TargetRetirementFailure("ACTIVE_CONFIRMATIONS");
        if (runtime.conversations() > 0 || runtime.backgroundWork() > 0) throw new TargetRetirementFailure("ACTIVE_TARGET_WORK");
        if (!runtime.complete()) throw new TargetRetirementFailure("DRAIN_NOT_CONFIRMED");
    }

    public void validate(Context context, Evidence evidence) {
        if (evidence == null || !context.requestFingerprint().equals(evidence.requestFingerprint())
            || context.fenceEpoch() != evidence.fenceEpoch() || evidence.reference() == null || evidence.reference().isBlank()
            || evidence.observedAt() == null || evidence.observedAt().isAfter(Instant.now())
            || evidence.observedAt().isBefore(Instant.now().minusSeconds(60)) || evidence.blockers() == null)
            throw new TargetRetirementFailure("PARTICIPANT_EVIDENCE_INVALID");
    }

    public void validate(EffectResult result) {
        if (result == null || result.evidenceReference() == null || result.evidenceReference().isBlank()
            || result.observedAt() == null || result.observedAt().isAfter(Instant.now())
            || result.observedAt().isBefore(Instant.now().minusSeconds(60))) throw new TargetRetirementFailure("EFFECT_READBACK_UNPROVEN");
    }

    public TargetRetirementParticipant participant(ParticipantKind kind) {
        var participant = participants.get(kind);
        if (participant == null) throw new TargetRetirementFailure("PARTICIPANT_MISSING_" + kind);
        return participant;
    }
    public TargetRetirementInterlock interlock() {
        if (interlocks.isEmpty()) throw new TargetRetirementFailure("RETIREMENT_INTERLOCK_MISSING");
        return interlocks.get(0);
    }
    private boolean knownWork(Evidence proof) {
        return proof.conversations() != null && proof.conversations() >= 0 && proof.confirmations() != null && proof.confirmations() >= 0
            && proof.humanWork() != null && proof.humanWork() >= 0 && proof.backgroundWork() != null && proof.backgroundWork() >= 0;
    }
    private void addBlockers(List<String> blockers, Evidence proof) {
        if (proof.blockers().size() > 32) throw new TargetRetirementFailure("PARTICIPANT_EVIDENCE_INVALID");
        for (String code : proof.blockers()) {
            if (code == null || code.isBlank() || code.length() > 64) throw new TargetRetirementFailure("PARTICIPANT_EVIDENCE_INVALID");
            blockers.add(code);
        }
    }
    public record Inspection(Map<ParticipantKind, Evidence> evidence, Evidence interlockEvidence, List<String> blockers) { }
}
