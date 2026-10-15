package com.ai.fabric.platform.backend.deployment.retirement;

import com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationTransitionContracts;
import com.ai.fabric.platform.backend.deployment.service.DeploymentRuntimeJsonTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TargetRetirementRuntimeIndexGatewayTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final TargetRetirementRuntimeGateway runtime = mock(TargetRetirementRuntimeGateway.class);
    private final DeploymentRuntimeJsonTransport transport = mock(DeploymentRuntimeJsonTransport.class);
    private final TargetRetirementRuntimeIndexGateway gateway =
        new TargetRetirementRuntimeIndexGateway(runtime, transport, mapper);
    private final Context context = context();
    private final RuntimeGenerationTransitionContracts.Target target =
        new RuntimeGenerationTransitionContracts.Target("deployment", "target", "release");

    @BeforeEach
    void setup() {
        var status = new RuntimeGenerationTransitionContracts.Readback(target, "tenant",
            RuntimeGenerationTransitionContracts.State.AUTHORITY_REVOKED, 8, "operation", "fingerprint",
            context.drainDeadline(), true, false, Instant.now());
        var metric = new RuntimeGenerationTransitionContracts.Metric(0L, true, List.of());
        var snapshot = new RuntimeGenerationTransitionContracts.Snapshot("snapshot", "runtime-snapshot", status,
            Instant.now(), metric, metric, metric, metric, 0, true, true, true, true, true,
            "topology-fingerprint", List.of(), List.of());
        when(runtime.snapshot(context)).thenReturn(snapshot);
        when(runtime.target(context)).thenReturn(target);
        when(runtime.access(eq(context), anyList())).thenReturn(
            new TargetRetirementRuntimeGateway.Access("https://runtime.example", Map.of("Authorization", "private")));
    }

    @Test
    void commandUsesDedicatedRuntimeProtocolAndExactTopologyBinding() throws Exception {
        when(transport.send(anyString(), anyString(), anyString(), anyMap(), any(), isNull()))
            .thenReturn(response(true, 0, "runtime-target-index:proof"));

        var result = gateway.retire(context);

        assertThat(result.complete()).isTrue();
        ArgumentCaptor<com.fasterxml.jackson.databind.JsonNode> body =
            ArgumentCaptor.forClass(com.fasterxml.jackson.databind.JsonNode.class);
        verify(runtime).access(context, List.of(TargetRetirementRuntimeGateway.WRITE_SCOPE));
        verify(transport).send(eq("https://runtime.example"), eq("POST"),
            eq("/api/private/runtime-target-retirement/index-retirements"), anyMap(), body.capture(), isNull());
        assertThat(body.getValue().path("binding").path("target").path("releaseId").asText()).isEqualTo("release");
        assertThat(body.getValue().path("binding").path("fenceEpoch").asLong()).isEqualTo(8);
        assertThat(body.getValue().path("topologyFingerprint").asText()).isEqualTo("topology-fingerprint");
    }

    @Test
    void readbackUsesReadScopeAndRejectsForeignOrInconsistentEvidence() throws Exception {
        when(transport.send(anyString(), anyString(), anyString(), anyMap(), any(), isNull()))
            .thenReturn(response(false, 2, "runtime-target-index:pending"));

        var pending = gateway.readback(context);

        assertThat(pending.complete()).isFalse();
        verify(runtime).access(context, List.of(TargetRetirementRuntimeGateway.READ_SCOPE));
        verify(transport).send(eq("https://runtime.example"), eq("POST"),
            eq("/api/private/runtime-target-retirement/index-retirements/readback"), anyMap(), any(), isNull());

        var invalid = mapper.valueToTree(new TargetRetirementRuntimeIndexGateway.IndexReadback(
            target, "foreign-tenant", "operation", 8, "topology-fingerprint", "QDRANT",
            "PREFIX", "deployment:target", Map.of("document", 0L), 0, 1, Instant.now(),
            "runtime-target-index:proof", true, false));
        when(transport.send(anyString(), anyString(), anyString(), anyMap(), any(), isNull()))
            .thenReturn(new DeploymentRuntimeJsonTransport.Response(200, invalid));
        assertThatThrownBy(() -> gateway.readback(context))
            .hasMessageContaining("TARGET_INDEX_READBACK_INVALID");
    }

    private DeploymentRuntimeJsonTransport.Response response(boolean complete, long count,
                                                              String evidence) {
        var body = mapper.valueToTree(new TargetRetirementRuntimeIndexGateway.IndexReadback(
            target, "tenant", "operation", 8, "topology-fingerprint", "QDRANT", "PREFIX",
            "deployment:target", Map.of("document", count), count, complete ? 4 : 0,
            Instant.now(), evidence, complete, false));
        return new DeploymentRuntimeJsonTransport.Response(200, body);
    }

    private static Context context() {
        var snapshot = new Snapshot("customer", "tenant", "deployment", "target", "target-generation", 1,
            List.of(new ReleaseGeneration("release", "version", "generation")), List.of(), List.of());
        return new Context("operation", "operation-key", "fingerprint", snapshot, 2, "worker", "actor",
            "CUSTOMER_ADMIN", Instant.now().plusSeconds(60),
            new Retention(RetentionDecision.RECLAIM, null, null, "backup"));
    }
}
