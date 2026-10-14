package com.ai.fabric.platform.backend.deployment.generation;

import com.ai.fabric.platform.backend.deployment.authority.DeploymentRuntimeAuthorityService;
import com.ai.fabric.platform.backend.deployment.authority.RuntimeAuthoritySnapshotRepository;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationTransitionContracts.*;

@Service
public class DeploymentRuntimeGenerationService {
    public static final String READ_SCOPE = "runtime:target-retirement:read";
    public static final String WRITE_SCOPE = "runtime:target-retirement:write";
    private static final Duration HEADER_TTL = Duration.ofMinutes(5);

    private final RuntimeGenerationTransitionClient client;
    private final DeploymentRuntimeAuthorityService authority;
    private final RuntimeAuthoritySnapshotRepository authoritySnapshots;
    private final Clock clock;
    private final Sleeper sleeper;

    @Autowired
    public DeploymentRuntimeGenerationService(RuntimeGenerationTransitionClient client,
                                              DeploymentRuntimeAuthorityService authority,
                                              RuntimeAuthoritySnapshotRepository authoritySnapshots) {
        this(client, authority, authoritySnapshots, Clock.systemUTC(), Thread::sleep);
    }

    DeploymentRuntimeGenerationService(RuntimeGenerationTransitionClient client,
                                       DeploymentRuntimeAuthorityService authority,
                                       RuntimeAuthoritySnapshotRepository authoritySnapshots,
                                       Clock clock, Sleeper sleeper) {
        this.client = client;
        this.authority = authority;
        this.authoritySnapshots = authoritySnapshots;
        this.clock = clock;
        this.sleeper = sleeper;
    }

    public DrainReceipt drainAndFence(DeploymentEntity deployment, DeploymentReleaseEntity nextRelease,
                                      String previousReleaseId, String previousRuntimeBaseUrl,
                                      Duration timeout, Duration pollInterval,
                                      RemoteDependencyEvidence remoteDependencyEvidence) {
        Scope scope = scope(deployment, nextRelease, previousReleaseId);
        Duration boundedTimeout = bounded(timeout, Duration.ofSeconds(10), Duration.ofMinutes(10), "RUNTIME_GENERATION_DRAIN_TIMEOUT_INVALID");
        Duration boundedPoll = bounded(pollInterval, Duration.ofMillis(250), Duration.ofSeconds(10), "RUNTIME_GENERATION_DRAIN_POLL_INVALID");
        if (boundedPoll.compareTo(boundedTimeout) >= 0) throw failure("RUNTIME_GENERATION_DRAIN_POLL_INVALID");
        Target previous = new Target(deployment.getId(), nextRelease.getTargetProfileId(), previousReleaseId);
        Target next = new Target(deployment.getId(), nextRelease.getTargetProfileId(), nextRelease.getId());
        String operationId = "replace:" + nextRelease.getId();
        String requestFingerprint = digest(String.join("\n", deployment.getCustomerId(), deployment.getTenantId(),
            previous.deploymentId(), previous.targetProfileId(), previous.releaseId(), next.releaseId(), operationId));
        Map<String, String> headers = headers(scope, operationId);
        Readback initial = client.status(previousRuntimeBaseUrl, previous, headers);
        validate(initial, previous, deployment.getTenantId());
        if (initial.state() != State.ACTIVE) throw failure("RUNTIME_GENERATION_PREVIOUS_NOT_ACTIVE");

        long drainFence = Math.addExact(initial.fenceEpoch(), 1L);
        Binding drainBinding = new Binding(previous, drainFence, operationId, requestFingerprint);
        Instant deadline = clock.instant().plus(boundedTimeout);
        CommandResult draining = client.command(previousRuntimeBaseUrl,
            new Command(drainBinding, initial.fenceEpoch(), CommandType.BEGIN_DRAIN, deadline, null), headers);
        validate(draining.current(), previous, deployment.getTenantId());
        if (draining.current().state() != State.DRAINING || draining.current().fenceEpoch() != drainFence) {
            throw failure("RUNTIME_GENERATION_DRAIN_NOT_ACCEPTED");
        }

        Snapshot snapshot = null;
        long maxPolls = Math.max(1L, boundedTimeout.toMillis() / boundedPoll.toMillis());
        for (long poll = 0; poll < maxPolls && !clock.instant().isAfter(deadline); poll++) {
            snapshot = client.snapshot(previousRuntimeBaseUrl, drainBinding, headers(scope, operationId));
            validate(snapshot, drainBinding);
            if (snapshot.localDrainComplete()) break;
            sleep(boundedPoll);
        }
        if (snapshot == null || !snapshot.localDrainComplete() || !snapshot.inventoryComplete()
            || !snapshot.workerFencingComplete()) {
            throw failure("RUNTIME_GENERATION_DRAIN_INCOMPLETE");
        }
        String remoteDependencyEvidenceReference = validateRemoteEvidence(
            snapshot.remoteObligations(), remoteDependencyEvidence
        );

        long authorityFence = Math.addExact(drainFence, 1L);
        Binding fenceBinding = new Binding(previous, authorityFence, operationId, requestFingerprint);
        CommandResult fenced = client.command(previousRuntimeBaseUrl,
            new Command(fenceBinding, drainFence, CommandType.FENCE_AUTHORITY, null,
                normalizedReference(remoteDependencyEvidenceReference)), headers(scope, operationId));
        validate(fenced.current(), previous, deployment.getTenantId());
        if (fenced.current().state() != State.AUTHORITY_REVOKED || !fenced.current().authorityRevoked()
            || fenced.current().fenceEpoch() != authorityFence) {
            throw failure("RUNTIME_GENERATION_AUTHORITY_FENCE_UNCONFIRMED");
        }
        return new DrainReceipt(previous, next, deployment.getTenantId(), operationId, requestFingerprint,
            authorityFence, snapshot.topologyFingerprint(), snapshot.evidenceReference(),
            remoteDependencyEvidenceReference);
    }

    public GenerationTransitionResult adoptAfterProviderStop(DeploymentEntity deployment,
                                                             DeploymentReleaseEntity nextRelease,
                                                             DrainReceipt receipt,
                                                             String nextRuntimeBaseUrl,
                                                             String nextTopologyFingerprint,
                                                             String providerStopEvidenceReference) {
        Scope scope = scope(deployment, nextRelease, receipt.previousTarget().releaseId());
        if (!receipt.nextTarget().equals(new Target(deployment.getId(), nextRelease.getTargetProfileId(), nextRelease.getId()))
            || !receipt.tenantId().equals(deployment.getTenantId())
            || !sha256(receipt.requestFingerprint()) || !sha256(receipt.previousTopologyFingerprint())
            || !sha256(nextTopologyFingerprint)) {
            throw failure("RUNTIME_GENERATION_TRANSITION_SCOPE_INVALID");
        }
        String stopEvidence = normalizedReference(providerStopEvidenceReference);
        if (stopEvidence == null) throw failure("RUNTIME_GENERATION_PROVIDER_STOP_EVIDENCE_REQUIRED");
        GenerationTransition command = new GenerationTransition(receipt.previousTarget(), receipt.nextTarget(),
            receipt.tenantId(), receipt.fencedEpoch(), receipt.previousTopologyFingerprint(),
            nextTopologyFingerprint, receipt.operationId(), receipt.requestFingerprint(), stopEvidence);
        GenerationTransitionResult result = client.transition(nextRuntimeBaseUrl, command,
            headers(scope, receipt.operationId()));
        validate(result.previous(), receipt.previousTarget(), receipt.tenantId());
        validate(result.current(), receipt.nextTarget(), receipt.tenantId());
        if (result.current().state() != State.ACTIVE || result.current().authorityRevoked()
            || result.current().reclaimStarted() || result.current().fenceEpoch() <= receipt.fencedEpoch()
            || !Objects.equals(stopEvidence, result.providerStopEvidenceReference())) {
            throw failure("RUNTIME_GENERATION_TRANSITION_UNCONFIRMED");
        }
        return result;
    }

    private Scope scope(DeploymentEntity deployment, DeploymentReleaseEntity nextRelease, String previousReleaseId) {
        if (deployment == null || nextRelease == null || previousReleaseId == null || previousReleaseId.isBlank()
            || !deployment.getId().equals(nextRelease.getDeploymentId()) || nextRelease.getTargetProfileId() == null
            || nextRelease.getTargetProfileId().isBlank() || previousReleaseId.equals(nextRelease.getId())
            || deployment.getCustomerId() == null || deployment.getCustomerId().isBlank()
            || deployment.getTenantId() == null || deployment.getTenantId().isBlank()) {
            throw failure("RUNTIME_GENERATION_SCOPE_INVALID");
        }
        var snapshot = authoritySnapshots.findByReleaseId(previousReleaseId)
            .orElseThrow(() -> failure("RUNTIME_GENERATION_PREVIOUS_AUTHORITY_MISSING"));
        if (!deployment.getId().equals(snapshot.getDeploymentId())
            || !nextRelease.getTargetProfileId().equals(snapshot.getTargetProfileId())
            || !previousReleaseId.equals(snapshot.getReleaseId())) {
            throw failure("RUNTIME_GENERATION_PREVIOUS_AUTHORITY_SCOPE_INVALID");
        }
        return new Scope(deployment.getId(), nextRelease.getTargetProfileId(), previousReleaseId, snapshot.getFingerprint());
    }

    private Map<String, String> headers(Scope scope, String operationId) {
        return authority.issueSystemHeaders(scope.deploymentId(), scope.targetProfileId(), scope.releaseId(),
            scope.authorityFingerprint(), "platform-runtime-generation", operationId,
            List.of(READ_SCOPE, WRITE_SCOPE), HEADER_TTL);
    }

    private void validate(Readback readback, Target target, String tenantId) {
        if (readback == null || !target.equals(readback.target()) || !tenantId.equals(readback.tenantId())
            || readback.updatedAt() == null || readback.fenceEpoch() < 1) {
            throw failure("RUNTIME_GENERATION_READBACK_INVALID");
        }
    }

    private void validate(Snapshot snapshot, Binding binding) {
        if (snapshot == null || snapshot.target() == null || !binding.target().equals(snapshot.target().target())
            || snapshot.target().fenceEpoch() != binding.fenceEpoch()
            || !binding.operationId().equals(snapshot.target().operationId())
            || !binding.requestFingerprint().equals(snapshot.target().requestFingerprint())
            || snapshot.observedAt() == null || snapshot.observedAt().isAfter(clock.instant())
            || snapshot.blockers() == null || snapshot.remoteObligations() == null
            || !sha256(snapshot.topologyFingerprint()) || snapshot.evidenceReference() == null
            || snapshot.evidenceReference().isBlank()) {
            throw failure("RUNTIME_GENERATION_DRAIN_EVIDENCE_INVALID");
        }
    }

    private Duration bounded(Duration value, Duration minimum, Duration maximum, String code) {
        if (value == null || value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0) throw failure(code);
        return value;
    }

    private String normalizedReference(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (!normalized.equals(value) || normalized.length() > 1000 || normalized.chars().anyMatch(Character::isISOControl)) {
            throw failure("RUNTIME_GENERATION_EVIDENCE_REFERENCE_INVALID");
        }
        return normalized;
    }

    private String validateRemoteEvidence(List<RemoteObligation> obligations, RemoteDependencyEvidence evidence) {
        Set<String> required = obligations.stream().map(RemoteObligation::dependencyKind)
            .collect(Collectors.toCollection(java.util.TreeSet::new));
        Map<String, String> readbacks = evidence == null ? Map.of() : new TreeMap<>(evidence.readbacks());
        if (!required.equals(readbacks.keySet())) {
            throw failure("RUNTIME_GENERATION_REMOTE_DEPENDENCY_EVIDENCE_REQUIRED");
        }
        if (required.isEmpty()) return null;
        var canonical = new LinkedHashMap<String, String>();
        readbacks.forEach((kind, reference) -> {
            if (kind == null || !kind.matches("[A-Z][A-Z0-9_]{1,63}")) {
                throw failure("RUNTIME_GENERATION_REMOTE_DEPENDENCY_EVIDENCE_INVALID");
            }
            canonical.put(kind, normalizedReference(reference));
        });
        if (canonical.containsValue(null)) throw failure("RUNTIME_GENERATION_REMOTE_DEPENDENCY_EVIDENCE_INVALID");
        return "remote-dependencies:" + digest(canonical.entrySet().stream()
            .map(entry -> entry.getKey() + "=" + entry.getValue()).collect(Collectors.joining("\n")));
    }

    private void sleep(Duration duration) {
        try { sleeper.sleep(duration.toMillis()); }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw failure("RUNTIME_GENERATION_DRAIN_INTERRUPTED");
        }
    }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private boolean sha256(String value) { return value != null && value.matches("[a-f0-9]{64}"); }
    private IllegalStateException failure(String code) { return new IllegalStateException(code); }

    public record DrainReceipt(Target previousTarget, Target nextTarget, String tenantId,
                               String operationId, String requestFingerprint, long fencedEpoch,
                               String previousTopologyFingerprint, String drainEvidenceReference,
                               String remoteDependencyEvidenceReference) { }
    public record RemoteDependencyEvidence(Map<String, String> readbacks) {
        public RemoteDependencyEvidence { readbacks = readbacks == null ? Map.of() : Map.copyOf(readbacks); }
    }
    private record Scope(String deploymentId, String targetProfileId, String releaseId,
                         String authorityFingerprint) { }
    @FunctionalInterface interface Sleeper { void sleep(long millis) throws InterruptedException; }
}
