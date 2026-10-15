package com.ai.fabric.platform.backend.deployment.retirement;

import com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationTransitionContracts.Binding;
import com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationTransitionContracts.Target;
import com.ai.fabric.platform.backend.deployment.service.DeploymentRuntimeJsonTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

import static com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.Context;

/** Fenced bridge to the deployment-local runtime's topology-owned index retirement protocol. */
@Service
public class TargetRetirementRuntimeIndexGateway {
    private static final String COMMAND_PATH = "/api/private/runtime-target-retirement/index-retirements";
    private static final String READBACK_PATH = COMMAND_PATH + "/readback";
    private static final int MAX_ENTITY_TYPES = 1_000;

    private final TargetRetirementRuntimeGateway runtime;
    private final DeploymentRuntimeJsonTransport transport;
    private final ObjectMapper mapper;

    public TargetRetirementRuntimeIndexGateway(TargetRetirementRuntimeGateway runtime,
                                               DeploymentRuntimeJsonTransport transport,
                                               ObjectMapper mapper) {
        this.runtime = runtime;
        this.transport = transport;
        this.mapper = mapper;
    }

    public IndexReadback retire(Context context) {
        Request request = request(context);
        var access = runtime.access(context, java.util.List.of(TargetRetirementRuntimeGateway.WRITE_SCOPE));
        return response(context, request, transport.send(access.baseUrl(), "POST", COMMAND_PATH,
            access.headers(), mapper.valueToTree(request), null));
    }

    public IndexReadback readback(Context context) {
        Request request = request(context);
        var access = runtime.access(context, java.util.List.of(TargetRetirementRuntimeGateway.READ_SCOPE));
        return response(context, request, transport.send(access.baseUrl(), "POST", READBACK_PATH,
            access.headers(), mapper.valueToTree(request), null));
    }

    private Request request(Context context) {
        var snapshot = runtime.snapshot(context);
        String topology = required(snapshot.topologyFingerprint(), "TARGET_INDEX_TOPOLOGY_UNPROVEN");
        Binding binding = new Binding(runtime.target(context), snapshot.target().fenceEpoch(),
            context.operationId(), context.requestFingerprint());
        return new Request(binding, topology);
    }

    private IndexReadback response(Context context, Request request,
                                   DeploymentRuntimeJsonTransport.Response response) {
        if (response == null || response.statusCode() < 200 || response.statusCode() >= 300
            || response.body() == null || !response.body().isObject()) {
            throw new TargetRetirementFailure("TARGET_INDEX_RUNTIME_REJECTED");
        }
        final IndexReadback value;
        try { value = mapper.treeToValue(response.body(), IndexReadback.class); }
        catch (Exception error) { throw new TargetRetirementFailure("TARGET_INDEX_READBACK_INVALID"); }
        validate(context, request, value);
        return value;
    }

    private void validate(Context context, Request request, IndexReadback value) {
        if (value == null || !request.binding().target().equals(value.target())
            || !context.snapshot().tenantId().equals(value.tenantId())
            || !context.operationId().equals(value.operationId())
            || request.binding().fenceEpoch() != value.fenceEpoch()
            || !request.topologyFingerprint().equals(value.topologyFingerprint())
            || blank(value.provider()) || blank(value.scopeType()) || blank(value.scopeValue())
            || value.countsByEntityType() == null || value.countsByEntityType().size() > MAX_ENTITY_TYPES
            || value.totalVectors() < 0 || value.removedVectors() < 0 || value.observedAt() == null
            || value.observedAt().isAfter(Instant.now()) || blank(value.evidenceReference())) {
            throw new TargetRetirementFailure("TARGET_INDEX_READBACK_INVALID");
        }
        long total = 0;
        try {
            for (var entry : value.countsByEntityType().entrySet()) {
                if (blank(entry.getKey()) || entry.getValue() == null || entry.getValue() < 0) {
                    throw new TargetRetirementFailure("TARGET_INDEX_READBACK_INVALID");
                }
                total = Math.addExact(total, entry.getValue());
            }
        } catch (ArithmeticException error) {
            throw new TargetRetirementFailure("TARGET_INDEX_READBACK_INVALID");
        }
        if (total != value.totalVectors() || value.complete() && total != 0) {
            throw new TargetRetirementFailure("TARGET_INDEX_READBACK_INVALID");
        }
    }

    private String required(String value, String code) {
        if (blank(value)) throw new TargetRetirementFailure(code);
        return value;
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }

    public record Request(Binding binding, String topologyFingerprint) { }
    public record IndexReadback(Target target, String tenantId, String operationId, long fenceEpoch,
                                String topologyFingerprint, String provider, String scopeType,
                                String scopeValue, Map<String, Long> countsByEntityType,
                                long totalVectors, long removedVectors, Instant observedAt,
                                String evidenceReference, boolean complete, boolean replay) { }
}
