package com.ai.fabric.platform.backend.deployment.generation;

import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;

import static com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationTransitionContracts.ColdMaintenanceTransitionResult;
import static com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationTransitionContracts.State;

/** Durable control-plane checkpoint for the stop/start gap of an in-place runtime replacement. */
@Service
public class RuntimeGenerationCheckpointService {
    public enum Phase { FENCED, STOP_CONFIRMED, ADOPTED, COLD_ADOPTED }

    public record PriorRuntime(String releaseId, String providerResourceUuid, String baseUrl) { }

    public record Checkpoint(Phase phase,
                             PriorRuntime priorRuntime,
                             DeploymentRuntimeGenerationService.DrainReceipt drainReceipt,
                             String nextTopologyFingerprint,
                             String providerStopEvidenceReference,
                             Instant adoptedAt,
                             @JsonInclude(JsonInclude.Include.NON_NULL)
                             ColdMaintenanceTransitionResult coldMaintenanceReceipt) {
        public Checkpoint(Phase phase, PriorRuntime priorRuntime,
                          DeploymentRuntimeGenerationService.DrainReceipt drainReceipt,
                          String nextTopologyFingerprint, String providerStopEvidenceReference, Instant adoptedAt) {
            this(phase, priorRuntime, drainReceipt, nextTopologyFingerprint, providerStopEvidenceReference, adoptedAt, null);
        }
    }

    private final DeploymentReleaseRepository releases;
    private final ObjectMapper mapper;

    public RuntimeGenerationCheckpointService(DeploymentReleaseRepository releases, ObjectMapper mapper) {
        this.releases = releases;
        this.mapper = mapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    @Transactional(readOnly = true)
    public Optional<Checkpoint> read(String releaseId) {
        var release = releases.findById(required(releaseId))
            .orElseThrow(() -> new IllegalStateException("RUNTIME_GENERATION_RELEASE_MISSING"));
        if (release.getRuntimeGenerationTransitionState() == null
            && release.getRuntimeGenerationTransitionFingerprint() == null
            && release.getRuntimeGenerationTransitionJson() == null) return Optional.empty();
        if (release.getRuntimeGenerationTransitionState() == null
            || release.getRuntimeGenerationTransitionFingerprint() == null
            || release.getRuntimeGenerationTransitionJson() == null) {
            throw failure("RUNTIME_GENERATION_CHECKPOINT_INCOMPLETE");
        }
        Checkpoint checkpoint = decode(release.getRuntimeGenerationTransitionJson());
        if (!release.getRuntimeGenerationTransitionState().equals(checkpoint.phase().name())
            || !release.getRuntimeGenerationTransitionFingerprint().equals(digest(release.getRuntimeGenerationTransitionJson()))) {
            throw failure("RUNTIME_GENERATION_CHECKPOINT_DRIFT");
        }
        validate(releaseId, checkpoint);
        validateColdScope(release.getDeploymentId(), release.getTargetProfileId(), checkpoint);
        return Optional.of(checkpoint);
    }

    @Transactional
    public Checkpoint recordFenced(String releaseId, PriorRuntime prior,
                                   DeploymentRuntimeGenerationService.DrainReceipt receipt,
                                   String nextTopologyFingerprint) {
        Checkpoint proposed = new Checkpoint(Phase.FENCED, prior, receipt,
            sha256(nextTopologyFingerprint), null, null);
        return save(releaseId, null, proposed);
    }

    @Transactional
    public Checkpoint recordProviderStop(String releaseId, String expectedFingerprint,
                                         String providerStopEvidenceReference) {
        Checkpoint current = require(releaseId, expectedFingerprint, Phase.FENCED);
        Checkpoint proposed = new Checkpoint(Phase.STOP_CONFIRMED, current.priorRuntime(), current.drainReceipt(),
            current.nextTopologyFingerprint(), reference(providerStopEvidenceReference), null);
        return save(releaseId, current, proposed);
    }

    /** A restarted worker must refresh provider stop-state before it may adopt the replacement. */
    @Transactional
    public Checkpoint refreshProviderStop(String releaseId, String expectedFingerprint,
                                          String providerStopEvidenceReference) {
        Checkpoint current = require(releaseId, expectedFingerprint, Phase.STOP_CONFIRMED);
        Checkpoint proposed = new Checkpoint(Phase.STOP_CONFIRMED, current.priorRuntime(), current.drainReceipt(),
            current.nextTopologyFingerprint(), reference(providerStopEvidenceReference), null);
        return save(releaseId, current, proposed);
    }

    @Transactional
    public Checkpoint recordAdopted(String releaseId, String expectedFingerprint, Instant adoptedAt) {
        Checkpoint current = require(releaseId, expectedFingerprint, Phase.STOP_CONFIRMED);
        if (adoptedAt == null || adoptedAt.isAfter(Instant.now().plusSeconds(5))) {
            throw failure("RUNTIME_GENERATION_ADOPTION_TIME_INVALID");
        }
        Checkpoint proposed = new Checkpoint(Phase.ADOPTED, current.priorRuntime(), current.drainReceipt(),
            current.nextTopologyFingerprint(), current.providerStopEvidenceReference(), adoptedAt);
        return save(releaseId, current, proposed);
    }

    @Transactional
    public Checkpoint recordColdAdopted(String releaseId, PriorRuntime prior,
                                        ColdMaintenanceTransitionResult receipt) {
        if (receipt == null || receipt.observedInventory() == null) {
            throw failure("RUNTIME_GENERATION_CHECKPOINT_INVALID");
        }
        return recordColdAdopted(releaseId, prior, receipt, receipt.observedInventory().topologyFingerprint());
    }

    @Transactional
    public Checkpoint recordColdAdopted(String releaseId, PriorRuntime prior,
                                        ColdMaintenanceTransitionResult receipt, String nextTopologyFingerprint) {
        if (receipt == null || receipt.transition() == null) {
            throw failure("RUNTIME_GENERATION_CHECKPOINT_INVALID");
        }
        Instant adoptedAt = receipt.transition().transitionedAt();
        if (adoptedAt == null || adoptedAt.isAfter(Instant.now().plusSeconds(5))) {
            throw failure("RUNTIME_GENERATION_ADOPTION_TIME_INVALID");
        }
        return save(releaseId, null, new Checkpoint(Phase.COLD_ADOPTED, prior, null,
            sha256(nextTopologyFingerprint), reference(receipt.transition().providerStopEvidenceReference()),
            adoptedAt, receipt));
    }

    public String fingerprint(Checkpoint checkpoint) { return digest(write(checkpoint)); }

    private Checkpoint require(String releaseId, String fingerprint, Phase phase) {
        Checkpoint current = read(releaseId).orElseThrow(() -> failure("RUNTIME_GENERATION_CHECKPOINT_MISSING"));
        if (!phase.equals(current.phase()) || !fingerprint(current).equals(sha256(fingerprint))) {
            throw failure("RUNTIME_GENERATION_CHECKPOINT_STALE");
        }
        return current;
    }

    private Checkpoint save(String releaseId, Checkpoint expected, Checkpoint proposed) {
        var release = releases.findByIdForUpdate(required(releaseId))
            .orElseThrow(() -> failure("RUNTIME_GENERATION_RELEASE_MISSING"));
        Optional<Checkpoint> persisted = persisted(release.getRuntimeGenerationTransitionState(),
            release.getRuntimeGenerationTransitionFingerprint(), release.getRuntimeGenerationTransitionJson(), releaseId);
        if (proposed.phase() == Phase.COLD_ADOPTED) {
            validate(releaseId, proposed);
            validateColdScope(release.getDeploymentId(), release.getTargetProfileId(), proposed);
        }
        persisted.ifPresent(checkpoint ->
            validateColdScope(release.getDeploymentId(), release.getTargetProfileId(), checkpoint));
        if (expected == null && persisted.isPresent()) {
            if (persisted.get().equals(proposed)) return persisted.get();
            throw failure("RUNTIME_GENERATION_CHECKPOINT_ALREADY_EXISTS");
        }
        if (expected != null && (persisted.isEmpty() || !persisted.get().equals(expected))) {
            throw failure("RUNTIME_GENERATION_CHECKPOINT_STALE");
        }
        validate(releaseId, proposed);
        String json = write(proposed);
        release.setRuntimeGenerationTransitionState(proposed.phase().name());
        release.setRuntimeGenerationTransitionJson(json);
        release.setRuntimeGenerationTransitionFingerprint(digest(json));
        release.setUpdatedAt(Instant.now());
        releases.saveAndFlush(release);
        return proposed;
    }

    private Optional<Checkpoint> persisted(String state, String fingerprint, String json, String releaseId) {
        if (state == null && fingerprint == null && json == null) return Optional.empty();
        if (state == null || fingerprint == null || json == null || !fingerprint.equals(digest(json))) {
            throw failure("RUNTIME_GENERATION_CHECKPOINT_DRIFT");
        }
        Checkpoint checkpoint = decode(json);
        if (!state.equals(checkpoint.phase().name())) throw failure("RUNTIME_GENERATION_CHECKPOINT_DRIFT");
        validate(releaseId, checkpoint);
        return Optional.of(checkpoint);
    }

    private void validate(String releaseId, Checkpoint checkpoint) {
        if (checkpoint == null || checkpoint.phase() == null || checkpoint.priorRuntime() == null
            || checkpoint.priorRuntime().releaseId() == null
            || checkpoint.priorRuntime().providerResourceUuid() == null || checkpoint.priorRuntime().baseUrl() == null
            || !isSha256(checkpoint.nextTopologyFingerprint())) {
            throw failure("RUNTIME_GENERATION_CHECKPOINT_INVALID");
        }
        if (checkpoint.phase() == Phase.COLD_ADOPTED) {
            validateCold(releaseId, checkpoint);
            return;
        }
        if (checkpoint.coldMaintenanceReceipt() != null || checkpoint.drainReceipt() == null
            || checkpoint.drainReceipt().nextTarget() == null || checkpoint.drainReceipt().previousTarget() == null
            || !releaseId.equals(checkpoint.drainReceipt().nextTarget().releaseId())
            || !checkpoint.priorRuntime().releaseId().equals(checkpoint.drainReceipt().previousTarget().releaseId())) {
            throw failure("RUNTIME_GENERATION_CHECKPOINT_INVALID");
        }
        if (checkpoint.phase() == Phase.FENCED
            && (checkpoint.providerStopEvidenceReference() != null || checkpoint.adoptedAt() != null)) {
            throw failure("RUNTIME_GENERATION_CHECKPOINT_INVALID");
        }
        if (checkpoint.phase() == Phase.STOP_CONFIRMED
            && (checkpoint.providerStopEvidenceReference() == null || checkpoint.adoptedAt() != null)) {
            throw failure("RUNTIME_GENERATION_CHECKPOINT_INVALID");
        }
        if (checkpoint.phase() == Phase.ADOPTED
            && (checkpoint.providerStopEvidenceReference() == null || checkpoint.adoptedAt() == null)) {
            throw failure("RUNTIME_GENERATION_CHECKPOINT_INVALID");
        }
    }

    private void validateCold(String releaseId, Checkpoint checkpoint) {
        var receipt = checkpoint.coldMaintenanceReceipt();
        if (checkpoint.drainReceipt() != null || receipt == null || receipt.transition() == null
            || receipt.observedInventory() == null || !receipt.inMemoryStateLossAcknowledged()
            || !"PHYSICAL_STOP_COLD_MAINTENANCE_V1".equals(receipt.evidenceKind())
            || !validReference(receipt.approvalReference())
            || !validReference(checkpoint.priorRuntime().releaseId())
            || !validReference(checkpoint.priorRuntime().providerResourceUuid())
            || !validReference(checkpoint.priorRuntime().baseUrl())) {
            throw failure("RUNTIME_GENERATION_CHECKPOINT_INVALID");
        }
        var transition = receipt.transition();
        var previous = transition.previous();
        var current = transition.current();
        var observed = receipt.observedInventory();
        if (previous == null || current == null || previous.target() == null || current.target() == null
            || !validReference(previous.target().deploymentId()) || !validReference(previous.target().targetProfileId())
            || !validReference(previous.tenantId()) || !previous.tenantId().equals(current.tenantId())
            || !previous.target().deploymentId().equals(current.target().deploymentId())
            || !previous.target().targetProfileId().equals(current.target().targetProfileId())
            || !checkpoint.priorRuntime().releaseId().equals(previous.target().releaseId())
            || !releaseId.equals(current.target().releaseId()) || releaseId.equals(previous.target().releaseId())
            || previous.state() != State.DRAINING || previous.authorityRevoked() || previous.reclaimStarted()
            || current.state() != State.ACTIVE || current.authorityRevoked() || current.reclaimStarted()
            || previous.fenceEpoch() < 0 || previous.fenceEpoch() == Long.MAX_VALUE
            || current.fenceEpoch() != previous.fenceEpoch() + 1
            || !validReference(previous.operationId()) || !isSha256(previous.requestFingerprint())
            || current.operationId() != null || current.requestFingerprint() != null || current.drainDeadline() != null
            || !validReference(transition.providerStopEvidenceReference())
            || !transition.providerStopEvidenceReference().equals(checkpoint.providerStopEvidenceReference())
            || transition.transitionedAt() == null || !transition.transitionedAt().equals(checkpoint.adoptedAt())
            || !sameStoredInstant(current.updatedAt(), transition.transitionedAt())
            || !previous.equals(observed.target()) || observed.observedAt() == null
            || observed.observedAt().isAfter(transition.transitionedAt())
            || !validReference(observed.snapshotId()) || !validReference(observed.evidenceReference())
            || !checkpoint.nextTopologyFingerprint().equals(observed.topologyFingerprint())) {
            throw failure("RUNTIME_GENERATION_CHECKPOINT_INVALID");
        }
    }

    private boolean sameStoredInstant(Instant stored, Instant original) {
        if (stored == null || original == null) return false;
        if (stored.equals(original) || stored.equals(original.truncatedTo(ChronoUnit.MICROS))) return true;
        try {
            return stored.equals(original.plusNanos(500).truncatedTo(ChronoUnit.MICROS));
        } catch (DateTimeException exception) {
            return false;
        }
    }

    private void validateColdScope(String deploymentId, String targetProfileId, Checkpoint checkpoint) {
        if (checkpoint.phase() == Phase.COLD_ADOPTED) {
            var target = checkpoint.coldMaintenanceReceipt().transition().current().target();
            if (!target.deploymentId().equals(deploymentId) || !target.targetProfileId().equals(targetProfileId)) {
                throw failure("RUNTIME_GENERATION_CHECKPOINT_INVALID");
            }
        }
    }

    private boolean validReference(String value) {
        return value != null && !value.isBlank() && value.equals(value.trim()) && value.length() <= 1000
            && value.chars().noneMatch(Character::isISOControl);
    }

    private String reference(String value) {
        if (value == null || value.isBlank() || !value.equals(value.trim()) || value.length() > 1000
            || value.chars().anyMatch(Character::isISOControl)) throw failure("RUNTIME_GENERATION_STOP_EVIDENCE_INVALID");
        return value;
    }

    private String required(String value) {
        if (value == null || value.isBlank() || !value.equals(value.trim()) || value.length() > 64) {
            throw failure("RUNTIME_GENERATION_RELEASE_INVALID");
        }
        return value;
    }

    private String sha256(String value) {
        if (!isSha256(value)) throw failure("RUNTIME_GENERATION_FINGERPRINT_INVALID");
        return value;
    }

    private boolean isSha256(String value) {
        return value != null && value.matches("[a-f0-9]{64}");
    }

    private String write(Checkpoint value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception exception) { throw failure("RUNTIME_GENERATION_CHECKPOINT_ENCODING_FAILED"); }
    }

    private Checkpoint decode(String value) {
        try { return mapper.readValue(value, Checkpoint.class); }
        catch (Exception exception) { throw failure("RUNTIME_GENERATION_CHECKPOINT_INVALID"); }
    }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private IllegalStateException failure(String code) { return new IllegalStateException(code); }
}
