package com.ai.fabric.platform.backend.deployment.operations.lifecycle.runtimeadapter;

import com.ai.fabric.platform.backend.deployment.operations.lifecycle.LifecycleContracts.Phase;
import com.ai.fabric.platform.backend.deployment.operations.lifecycle.LifecycleContracts.Status;
import com.ai.fabric.platform.backend.deployment.authority.DeploymentRuntimeAuthorityService;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentRuntimeJsonTransport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Exact frozen release transport; grant authorization remains in the fixed-scope authority issuer. */
@Component
public class RuntimeLifecycleGateway {
    private static final String ROOT = "/api/private/runtime-target-retirement";
    private final DeploymentRuntimeJsonTransport transport;
    private final ObjectMapper mapper;
    private final DeploymentRepository deployments;
    private final DeploymentReleaseRepository releases;
    private final DeploymentRuntimeAuthorityService authority;

    public RuntimeLifecycleGateway(DeploymentRuntimeJsonTransport transport, ObjectMapper mapper,
        DeploymentRepository deployments, DeploymentReleaseRepository releases, DeploymentRuntimeAuthorityService authority) {
        this.transport = transport;
        this.mapper = mapper;
        this.deployments = deployments;
        this.releases = releases;
        this.authority = authority;
    }

    public record FrozenTarget(String deploymentId, String tenantId, String targetProfileId, String releaseId,
                               String authorityFingerprint, String runtimeBaseUrl) { }
    public record Operation(String operationId, String requestFingerprint, Instant dueAt, Instant deadlineAt,
                            String externalEvidenceReference) { }
    public record Result(Status status, String code, List<String> blockers, String evidenceReference, long fenceEpoch,
                         List<String> remoteDependencies) {
        public Result(Status status, String code, List<String> blockers, String evidenceReference, long fenceEpoch) {
            this(status, code, blockers, evidenceReference, fenceEpoch, List.of());
        }
    }

    public Result execute(Phase phase, FrozenTarget target, Operation operation) {
        Objects.requireNonNull(target, "Frozen Runtime target is required.");
        Objects.requireNonNull(operation, "Frozen Runtime operation is required.");
        var deployment = deployments.findById(target.deploymentId()).orElseThrow();
        var release = releases.findById(target.releaseId()).orElseThrow();
        if (!Objects.equals(deployment.getTenantId(), target.tenantId())
            || !Objects.equals(release.getDeploymentId(), target.deploymentId())
            || !Objects.equals(release.getTargetProfileId(), target.targetProfileId())
            || !Objects.equals(release.getRuntimeAuthorityFingerprint(), target.authorityFingerprint())) {
            throw new IllegalArgumentException("Frozen Runtime release scope or authority fingerprint changed.");
        }
        return execute(phase, target, operation,
            authority.issueLifecycleHeaders(deployment, release, operation.operationId(), phase != Phase.OBSERVE_DRAIN
                && (phase == Phase.CANCEL || Instant.now().isBefore(operation.deadlineAt()))));
    }

    Result execute(Phase phase, FrozenTarget target, Operation operation, Map<String, String> machineHeaders) {
        validate(phase, target, operation, machineHeaders);
        if (phase != Phase.OBSERVE_DRAIN && phase != Phase.CANCEL
            && Instant.now().isBefore(operation.dueAt())) {
            return result(Status.BLOCKED, "RUNTIME_LIFECYCLE_OUTSIDE_WINDOW", -1);
        }
        try {
            JsonNode current = post(target, machineHeaders, "/status", scope(target));
            requireReadback(current, target);
            long epoch = current.path("fenceEpoch").longValue();
            String state = current.path("state").asText();
            boolean bound = bound(current, operation);
            if (phase == Phase.OBSERVE_DRAIN) {
                if (!bound || !"DRAINING".equals(state)) return result(Status.BLOCKED, "RUNTIME_DRAIN_BINDING_MISMATCH", epoch);
                return observe(target, operation, machineHeaders, epoch);
            }
            String desired = switch (phase) {
                case BEGIN_DRAIN -> "DRAINING";
                case FENCE -> "AUTHORITY_REVOKED";
                case CANCEL -> "ACTIVE";
                default -> throw new IllegalArgumentException("Unsupported Runtime lifecycle phase.");
            };
            if (desired.equals(state) && bound) return result(Status.SUCCEEDED, "RUNTIME_" + desired, epoch);
            if ((phase != Phase.BEGIN_DRAIN && (!bound || !"DRAINING".equals(state)))
                || phase == Phase.BEGIN_DRAIN && !"ACTIVE".equals(state)) {
                return result(Status.BLOCKED, "RUNTIME_DRAIN_STATE_OR_BINDING_MISMATCH", epoch);
            }
            if (phase == Phase.FENCE) {
                Result observed = observe(target, operation, machineHeaders, epoch);
                if (observed.status() != Status.SUCCEEDED) return new Result(observed.status(), observed.code(),
                    observed.blockers(), observed.evidenceReference(), epoch);
                if (!observed.remoteDependencies().isEmpty()
                    && (operation.externalEvidenceReference() == null || operation.externalEvidenceReference().isBlank())) {
                    return result(Status.BLOCKED, "AUTHORITATIVE_REMOTE_READBACK_REFERENCE_REQUIRED", epoch);
                }
            }
            if (phase != Phase.CANCEL && !Instant.now().isBefore(operation.deadlineAt())) {
                return result(Status.BLOCKED, "RUNTIME_LIFECYCLE_OUTSIDE_WINDOW", epoch);
            }
            var command = mapper.createObjectNode();
            command.set("binding", binding(target, operation, Math.addExact(epoch, 1)));
            command.put("expectedFenceEpoch", epoch);
            command.put("type", switch (phase) {
                case BEGIN_DRAIN -> "BEGIN_DRAIN";
                case FENCE -> "FENCE_AUTHORITY";
                case CANCEL -> "CANCEL_DRAIN";
                default -> throw new IllegalArgumentException("Unsupported Runtime lifecycle phase.");
            });
            command.put("drainDeadline", operation.deadlineAt().toString());
            if (operation.externalEvidenceReference() != null) command.put("externalEvidenceReference", operation.externalEvidenceReference());
            JsonNode response = post(target, machineHeaders, "/commands", command).path("current");
            requireReadback(response, target);
            if (!desired.equals(response.path("state").asText()) || !bound(response, operation)
                || response.path("fenceEpoch").longValue() != epoch + 1) {
                throw new IllegalStateException("RUNTIME_COMMAND_RESPONSE_INVALID");
            }
            return result(Status.SUCCEEDED, "RUNTIME_" + desired, epoch + 1);
        } catch (Rejected response) {
            return result("DRAIN_NOT_PROVED_COMPLETE".equals(response.code) ? Status.PENDING
                : response.status >= 500 ? Status.UNKNOWN : Status.BLOCKED, response.code, -1);
        } catch (RuntimeException failure) {
            return result(Status.UNKNOWN, "RUNTIME_OUTCOME_UNKNOWN", -1);
        }
    }

    private Result observe(FrozenTarget target, Operation operation, Map<String, String> headers, long epoch) {
        JsonNode snapshot = post(target, headers, "/snapshots", binding(target, operation, epoch));
        requireReadback(snapshot.path("target"), target);
        if (!bound(snapshot.path("target"), operation) || snapshot.path("target").path("fenceEpoch").longValue() != epoch
            || !snapshot.path("inventoryComplete").isBoolean() || !snapshot.path("workerFencingComplete").isBoolean()
            || !snapshot.path("localDrainComplete").isBoolean() || !snapshot.path("remoteObligations").isArray()) {
            throw new IllegalStateException("RUNTIME_DRAIN_RESPONSE_INVALID");
        }
        var dependencies = new java.util.LinkedHashSet<String>();
        for (JsonNode obligation : snapshot.path("remoteObligations")) {
            String kind = obligation.path("dependencyKind").asText();
            if (!scope(target).equals(obligation.path("target")) || !kind.matches("[A-Z][A-Z0-9_]{0,63}")) {
                throw new IllegalStateException("RUNTIME_REMOTE_OBLIGATION_INVALID");
            }
            dependencies.add(kind);
        }
        List<String> blockers = mapper.convertValue(snapshot.path("blockers"),
            mapper.getTypeFactory().constructCollectionType(List.class, String.class));
        boolean complete = snapshot.path("inventoryComplete").asBoolean()
            && snapshot.path("workerFencingComplete").asBoolean() && snapshot.path("localDrainComplete").asBoolean();
        if (!complete && !Instant.now().isBefore(operation.deadlineAt())) {
            return new Result(Status.BLOCKED, "DRAIN_DEADLINE_EXCEEDED", List.copyOf(blockers),
                snapshot.path("evidenceReference").asText(null), epoch, List.copyOf(dependencies));
        }
        return new Result(complete ? Status.SUCCEEDED : Status.PENDING,
            complete ? "RUNTIME_LOCAL_DRAIN_COMPLETE" : "RUNTIME_DRAIN_PENDING", List.copyOf(blockers),
            snapshot.path("evidenceReference").asText(null), epoch, List.copyOf(dependencies));
    }

    private JsonNode post(FrozenTarget target, Map<String, String> headers, String path, JsonNode body) {
        var response = transport.send(target.runtimeBaseUrl(), "POST", ROOT + path, headers, body, null);
        if (response.statusCode() != 200) {
            String code = response.body().path("code").asText();
            if (!code.matches("[A-Z][A-Z0-9_]{0,127}")) code = "RUNTIME_HTTP_" + response.statusCode();
            throw new Rejected(response.statusCode(), code);
        }
        return response.body();
    }
    private JsonNode scope(FrozenTarget target) {
        return mapper.createObjectNode().put("deploymentId", target.deploymentId())
            .put("targetProfileId", target.targetProfileId()).put("releaseId", target.releaseId());
    }
    private JsonNode binding(FrozenTarget target, Operation operation, long epoch) {
        var value = mapper.createObjectNode();
        value.set("target", scope(target));
        return value.put("fenceEpoch", epoch).put("operationId", operation.operationId())
            .put("requestFingerprint", operation.requestFingerprint());
    }
    private boolean bound(JsonNode readback, Operation operation) {
        return operation.operationId().equals(readback.path("operationId").asText())
            && operation.requestFingerprint().equals(readback.path("requestFingerprint").asText())
            && ("ACTIVE".equals(readback.path("state").asText())
                || operation.deadlineAt().equals(Instant.parse(readback.path("drainDeadline").asText())));
    }
    private void requireReadback(JsonNode value, FrozenTarget target) {
        if (!scope(target).equals(value.path("target")) || !target.tenantId().equals(value.path("tenantId").asText())
            || !value.path("fenceEpoch").isIntegralNumber() || !value.path("fenceEpoch").canConvertToLong()
            || value.path("fenceEpoch").longValue() < 0 || !value.path("authorityRevoked").isBoolean()
            || !value.path("reclaimStarted").isBoolean()) throw new IllegalStateException("RUNTIME_SCOPE_READBACK_MISMATCH");
        String state = value.path("state").asText();
        boolean revoked = value.path("authorityRevoked").booleanValue();
        boolean reclaim = value.path("reclaimStarted").booleanValue();
        if (!List.of("ACTIVE", "DRAINING", "AUTHORITY_REVOKED", "RECLAIM_STARTED").contains(state)
            || "AUTHORITY_REVOKED".equals(state) && (!revoked || reclaim)
            || "RECLAIM_STARTED".equals(state) && !reclaim
            || List.of("ACTIVE", "DRAINING").contains(state) && (revoked || reclaim)) {
            throw new IllegalStateException("RUNTIME_STATE_READBACK_MISMATCH");
        }
    }
    private Result result(Status status, String code, long epoch) {
        return new Result(status, code, status == Status.SUCCEEDED ? List.of() : List.of(code), null, epoch);
    }
    private void validate(Phase phase, FrozenTarget target, Operation operation, Map<String, String> headers) {
        if (!List.of(Phase.BEGIN_DRAIN, Phase.OBSERVE_DRAIN, Phase.FENCE, Phase.CANCEL).contains(Objects.requireNonNull(phase))
            || target == null || operation == null || headers == null || headers.isEmpty()
            || !id(target.deploymentId()) || !id(target.tenantId()) || !id(target.targetProfileId()) || !id(target.releaseId())
            || !sha(target.authorityFingerprint()) || target.runtimeBaseUrl() == null || target.runtimeBaseUrl().isBlank()
            || !id(operation.operationId()) || !sha(operation.requestFingerprint())
            || operation.dueAt() == null || operation.deadlineAt() == null || !operation.deadlineAt().isAfter(operation.dueAt())) {
            throw new IllegalArgumentException("Frozen Runtime lifecycle context is required.");
        }
    }
    private boolean id(String value) { return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._:/@-]{0,159}"); }
    private boolean sha(String value) { return value != null && value.matches("[a-f0-9]{64}"); }
    private static final class Rejected extends RuntimeException {
        private final int status;
        private final String code;
        private Rejected(int status, String code) { this.status = status; this.code = code; }
    }
}
