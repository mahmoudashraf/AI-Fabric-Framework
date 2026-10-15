package com.ai.fabric.platform.backend.deployment.retirement;

import com.ai.fabric.platform.backend.deployment.authority.DeploymentRuntimeAuthorityService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationTransitionClient;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentReleaseEndpointService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

import static com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationTransitionContracts.*;
import static com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.Context;

/** Exact-release bridge to the runtime retirement protocol. Never resolves a mutable latest release. */
@Service
public class TargetRetirementRuntimeGateway {
    static final String READ_SCOPE = "runtime:target-retirement:read";
    static final String WRITE_SCOPE = "runtime:target-retirement:write";

    private final DeploymentReleaseRepository releases;
    private final DeploymentReleaseEndpointService endpoints;
    private final DeploymentRuntimeAuthorityService authority;
    private final RuntimeGenerationTransitionClient client;

    public TargetRetirementRuntimeGateway(DeploymentReleaseRepository releases,
                                          DeploymentReleaseEndpointService endpoints,
                                          DeploymentRuntimeAuthorityService authority,
                                          RuntimeGenerationTransitionClient client) {
        this.releases = releases;
        this.endpoints = endpoints;
        this.authority = authority;
        this.client = client;
    }

    public Target target(Context context) {
        var generation = releaseGeneration(context);
        return new Target(context.snapshot().deploymentId(), context.snapshot().targetProfileId(), generation.releaseId());
    }

    public Binding binding(Context context) {
        Readback current = status(context);
        requireBound(context, current);
        return new Binding(target(context), current.fenceEpoch(), context.operationId(), context.requestFingerprint());
    }

    public Readback status(Context context) {
        var access = access(context, List.of(READ_SCOPE));
        Readback current = remote(() -> client.status(access.baseUrl(), target(context), access.headers()));
        requireTarget(context, current);
        if (current.state() == State.ACTIVE) {
            requireUnboundActive(current);
        } else requireBound(context, current);
        return current;
    }

    public Snapshot snapshot(Context context) {
        var access = access(context, List.of(READ_SCOPE));
        Readback current = remote(() -> client.status(access.baseUrl(), target(context), access.headers()));
        requireBound(context, current);
        var binding = new Binding(target(context), current.fenceEpoch(), context.operationId(), context.requestFingerprint());
        Snapshot snapshot = remote(() -> client.snapshot(access.baseUrl(), binding, access.headers()));
        requireSnapshot(context, current, snapshot);
        return snapshot;
    }

    public CommandResult command(Context context, CommandType type, String externalEvidenceReference) {
        var access = access(context, List.of(READ_SCOPE, WRITE_SCOPE));
        Readback current = remote(() -> client.status(access.baseUrl(), target(context), access.headers()));
        requireTarget(context, current);
        if (current.state() == State.ACTIVE) {
            requireUnboundActive(current);
            if (type != CommandType.BEGIN_DRAIN) {
                throw new TargetRetirementFailure("RUNTIME_RETIREMENT_BINDING_REQUIRED");
            }
        } else requireBound(context, current);
        long nextFence;
        try { nextFence = Math.addExact(current.fenceEpoch(), 1); }
        catch (ArithmeticException error) { throw new TargetRetirementFailure("RUNTIME_RETIREMENT_FENCE_EXHAUSTED"); }
        var binding = new Binding(target(context), nextFence, context.operationId(), context.requestFingerprint());
        var command = new Command(binding, current.fenceEpoch(), type,
            context.drainDeadline(), externalEvidenceReference);
        CommandResult result = remote(() -> client.command(access.baseUrl(), command, access.headers()));
        requireCommandResult(context, command, result);
        return result;
    }

    private void requireUnboundActive(Readback current) {
        if (current.operationId() != null || current.requestFingerprint() != null
            || current.authorityRevoked() || current.reclaimStarted()) {
            throw new TargetRetirementFailure("RUNTIME_RETIREMENT_BINDING_CHANGED");
        }
    }

    private void requireBound(Context context, Readback current) {
        requireTarget(context, current);
        if (!context.operationId().equals(current.operationId())
            || !context.requestFingerprint().equals(current.requestFingerprint())) {
            throw new TargetRetirementFailure("RUNTIME_RETIREMENT_BINDING_CHANGED");
        }
    }

    private void requireTarget(Context context, Readback current) {
        if (current == null || !target(context).equals(current.target())
            || !context.snapshot().tenantId().equals(current.tenantId()) || current.state() == null
            || current.updatedAt() == null || current.updatedAt().isAfter(java.time.Instant.now())) {
            throw new TargetRetirementFailure("RUNTIME_RETIREMENT_BINDING_CHANGED");
        }
    }

    private void requireCommandResult(Context context, Command command, CommandResult result) {
        if (result == null || result.applied() == null || result.current() == null) {
            throw new TargetRetirementFailure("RUNTIME_RETIREMENT_READBACK_INVALID");
        }
        requireBound(context, result.applied());
        requireBound(context, result.current());
        if (result.applied().fenceEpoch() != command.binding().fenceEpoch()
            || result.current().fenceEpoch() != command.binding().fenceEpoch()
            || command.type() == CommandType.BEGIN_DRAIN && result.applied().state() != State.DRAINING) {
            throw new TargetRetirementFailure("RUNTIME_RETIREMENT_BINDING_CHANGED");
        }
    }

    private void requireSnapshot(Context context, Readback current, Snapshot snapshot) {
        if (snapshot == null || snapshot.target() == null || !snapshot.target().equals(current)
            || snapshot.observedAt() == null || snapshot.observedAt().isAfter(java.time.Instant.now())
            || snapshot.evidenceReference() == null || snapshot.evidenceReference().isBlank()
            || snapshot.topologyFingerprint() == null || snapshot.topologyFingerprint().isBlank()
            || snapshot.openConversations() == null || snapshot.pendingConfirmations() == null
            || snapshot.humanWork() == null || snapshot.backgroundWork() == null
            || snapshot.remoteObligations() == null || snapshot.blockers() == null) {
            throw new TargetRetirementFailure("RUNTIME_RETIREMENT_READBACK_INVALID");
        }
        requireBound(context, snapshot.target());
    }

    Access access(Context context, List<String> scopes) {
        var release = release(context);
        String fingerprint = release.getRuntimeAuthorityFingerprint();
        if (fingerprint == null || fingerprint.isBlank()) {
            throw new TargetRetirementFailure("RUNTIME_AUTHORITY_FINGERPRINT_MISSING");
        }
        try {
            return new Access(endpoints.runtimeBaseUrl(release), authority.issueTargetRetirementHeaders(
                context.snapshot().deploymentId(), context.snapshot().targetProfileId(), release.getId(),
                fingerprint, context.operationId(), scopes));
        } catch (TargetRetirementFailure error) {
            throw error;
        } catch (RuntimeException error) {
            throw new TargetRetirementFailure("RUNTIME_AUTHORITY_UNAVAILABLE");
        }
    }

    private DeploymentReleaseEntity release(Context context) {
        var expected = releaseGeneration(context);
        var release = releases.findById(expected.releaseId())
            .orElseThrow(() -> new TargetRetirementFailure("RELEASE_MISSING"));
        if (!context.snapshot().deploymentId().equals(release.getDeploymentId())
            || !context.snapshot().targetProfileId().equals(release.getTargetProfileId())
            || !expected.versionId().equals(release.getDeploymentVersionId())) {
            throw new TargetRetirementFailure("RELEASE_OWNERSHIP_CHANGED");
        }
        return release;
    }

    private TargetRetirementContracts.ReleaseGeneration releaseGeneration(Context context) {
        if (context.snapshot().releases().size() != 1) {
            throw new TargetRetirementFailure("RUNTIME_RELEASE_AMBIGUOUS");
        }
        return context.snapshot().releases().getFirst();
    }

    private <T> T remote(java.util.function.Supplier<T> action) {
        try {
            T value = action.get();
            if (value == null) throw new TargetRetirementFailure("RUNTIME_RETIREMENT_READBACK_INVALID");
            return value;
        } catch (TargetRetirementFailure error) {
            throw error;
        } catch (RuntimeException error) {
            throw new TargetRetirementFailure("RUNTIME_RETIREMENT_UNAVAILABLE");
        }
    }

    record Access(String baseUrl, Map<String, String> headers) { }
}
