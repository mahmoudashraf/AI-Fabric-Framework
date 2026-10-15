package com.ai.fabric.platform.backend.deployment.retirement;

import com.ai.fabric.platform.backend.deployment.authority.DeploymentRuntimeAuthorityService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationTransitionClient;
import com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationTransitionContracts;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentReleaseEndpointService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationTransitionContracts.CommandResult;
import static com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationTransitionContracts.CommandType;
import static com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationTransitionContracts.Readback;
import static com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationTransitionContracts.Target;
import static com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TargetRetirementRuntimeGatewayTest {
    private final DeploymentReleaseRepository releases = mock(DeploymentReleaseRepository.class);
    private final DeploymentReleaseEndpointService endpoints = mock(DeploymentReleaseEndpointService.class);
    private final DeploymentRuntimeAuthorityService authority = mock(DeploymentRuntimeAuthorityService.class);
    private final RuntimeGenerationTransitionClient client = mock(RuntimeGenerationTransitionClient.class);
    private final Context context = context();
    private TargetRetirementRuntimeGateway gateway;
    private DeploymentReleaseEntity release;

    @BeforeEach void setup() {
        gateway = new TargetRetirementRuntimeGateway(releases, endpoints, authority, client);
        release = new DeploymentReleaseEntity();
        release.setId("release");
        release.setDeploymentId("deployment");
        release.setTargetProfileId("target");
        release.setDeploymentVersionId("version");
        release.setRuntimeAuthorityFingerprint("authority-fingerprint");
        when(releases.findById("release")).thenReturn(Optional.of(release));
        when(endpoints.runtimeBaseUrl(release)).thenReturn("https://runtime.example");
        when(authority.issueTargetRetirementHeaders(anyString(), anyString(), anyString(), anyString(), anyString(), anyList()))
            .thenReturn(Map.of("Authorization", "private"));
    }

    @Test void commandAdvancesRuntimeFenceWithoutConfusingItWithPlatformFence() {
        var target = new Target("deployment", "target", "release");
        var current = new Readback(target, "tenant", RuntimeGenerationTransitionContracts.State.ACTIVE, 7,
            null, null, null, false, false, Instant.now());
        when(client.status(eq("https://runtime.example"), eq(target), anyMap())).thenReturn(current);
        when(client.command(eq("https://runtime.example"), any(), anyMap())).thenAnswer(call -> {
            RuntimeGenerationTransitionContracts.Command command = call.getArgument(1);
            var applied = new Readback(target, "tenant", RuntimeGenerationTransitionContracts.State.DRAINING,
                command.binding().fenceEpoch(), "operation", "fingerprint", context.drainDeadline(), false, false, Instant.now());
            return new CommandResult(applied, applied, false);
        });

        gateway.command(context, CommandType.BEGIN_DRAIN, null);

        ArgumentCaptor<RuntimeGenerationTransitionContracts.Command> command = ArgumentCaptor.forClass(
            RuntimeGenerationTransitionContracts.Command.class);
        verify(client).command(eq("https://runtime.example"), command.capture(), anyMap());
        assertThat(command.getValue().expectedFenceEpoch()).isEqualTo(7);
        assertThat(command.getValue().binding().fenceEpoch()).isEqualTo(8);
        assertThat(command.getValue().binding().operationId()).isEqualTo("operation");
    }

    @Test void activeRuntimeTenantIsValidatedBeforeBeginDrainMutation() {
        var target = new Target("deployment", "target", "release");
        var foreign = new Readback(target, "foreign-tenant", RuntimeGenerationTransitionContracts.State.ACTIVE, 7,
            null, null, null, false, false, Instant.now());
        when(client.status(eq("https://runtime.example"), eq(target), anyMap())).thenReturn(foreign);

        assertThatThrownBy(() -> gateway.command(context, CommandType.BEGIN_DRAIN, null))
            .hasMessageContaining("RUNTIME_RETIREMENT_BINDING_CHANGED");
        verify(client, never()).command(anyString(), any(), anyMap());
    }

    @Test void activeRuntimeMustBeUnboundBeforeBeginDrainMutation() {
        var target = new Target("deployment", "target", "release");
        var stale = new Readback(target, "tenant", RuntimeGenerationTransitionContracts.State.ACTIVE, 7,
            "prior-operation", "prior-fingerprint", null, false, false, Instant.now());
        when(client.status(eq("https://runtime.example"), eq(target), anyMap())).thenReturn(stale);

        assertThatThrownBy(() -> gateway.command(context, CommandType.BEGIN_DRAIN, null))
            .hasMessageContaining("RUNTIME_RETIREMENT_BINDING_CHANGED");
        verify(client, never()).command(anyString(), any(), anyMap());
    }

    @Test void beginDrainMustReturnTheNewExactOperationBinding() {
        var target = new Target("deployment", "target", "release");
        var current = new Readback(target, "tenant", RuntimeGenerationTransitionContracts.State.ACTIVE, 7,
            null, null, null, false, false, Instant.now());
        var foreign = new Readback(target, "tenant", RuntimeGenerationTransitionContracts.State.DRAINING, 8,
            "foreign-operation", "fingerprint", context.drainDeadline(), false, false, Instant.now());
        when(client.status(eq("https://runtime.example"), eq(target), anyMap())).thenReturn(current);
        when(client.command(eq("https://runtime.example"), any(), anyMap()))
            .thenReturn(new CommandResult(foreign, foreign, false));

        assertThatThrownBy(() -> gateway.command(context, CommandType.BEGIN_DRAIN, null))
            .hasMessageContaining("RUNTIME_RETIREMENT_BINDING_CHANGED");
    }

    @Test void activeRuntimeRejectsCommandsThatRequireAnExistingDrainBinding() {
        var target = new Target("deployment", "target", "release");
        var current = new Readback(target, "tenant", RuntimeGenerationTransitionContracts.State.ACTIVE, 7,
            null, null, null, false, false, Instant.now());
        when(client.status(eq("https://runtime.example"), eq(target), anyMap())).thenReturn(current);

        assertThatThrownBy(() -> gateway.command(context, CommandType.FENCE_AUTHORITY, null))
            .hasMessageContaining("RUNTIME_RETIREMENT_BINDING_REQUIRED");
        verify(client, never()).command(anyString(), any(), anyMap());
    }

    @Test void releaseGenerationAndOwnershipAreNeverResolvedFromLatest() {
        release.setDeploymentVersionId("replacement");
        assertThatThrownBy(() -> gateway.status(context)).hasMessageContaining("RELEASE_OWNERSHIP_CHANGED");
        verify(client, never()).status(anyString(), any(), anyMap());
    }

    @Test void snapshotRequiresExactRuntimeOperationBinding() {
        var target = new Target("deployment", "target", "release");
        var foreign = new Readback(target, "tenant", RuntimeGenerationTransitionContracts.State.DRAINING, 8,
            "foreign-operation", "fingerprint", context.drainDeadline(), false, false, Instant.now());
        when(client.status(eq("https://runtime.example"), eq(target), anyMap())).thenReturn(foreign);
        assertThatThrownBy(() -> gateway.snapshot(context)).hasMessageContaining("RUNTIME_RETIREMENT_BINDING_CHANGED");
        verify(client, never()).snapshot(anyString(), any(), anyMap());
    }

    @Test void snapshotRequiresExactTargetIdentityAndCompleteTopologyEvidence() {
        var target = new Target("deployment", "target", "release");
        var current = new Readback(target, "tenant", RuntimeGenerationTransitionContracts.State.DRAINING, 8,
            "operation", "fingerprint", context.drainDeadline(), false, false, Instant.now());
        var metric = new RuntimeGenerationTransitionContracts.Metric(0L, true, List.of());
        var valid = new RuntimeGenerationTransitionContracts.Snapshot("snapshot", "evidence", current,
            Instant.now(), metric, metric, metric, metric, 0, true, true, true, true, true,
            "topology", List.of(), List.of());
        when(client.status(eq("https://runtime.example"), eq(target), anyMap())).thenReturn(current);
        when(client.snapshot(eq("https://runtime.example"), any(), anyMap())).thenReturn(valid);

        assertThat(gateway.snapshot(context)).isSameAs(valid);

        var invalid = new RuntimeGenerationTransitionContracts.Snapshot("snapshot", "evidence", current,
            Instant.now(), metric, metric, metric, metric, 0, true, true, true, true, true,
            "", List.of(), List.of());
        when(client.snapshot(eq("https://runtime.example"), any(), anyMap())).thenReturn(invalid);
        assertThatThrownBy(() -> gateway.snapshot(context))
            .hasMessageContaining("RUNTIME_RETIREMENT_READBACK_INVALID");
    }

    @Test void schedulerAccessUsesExactFrozenReleaseAndRequestedPrivateScopesWithoutBrowserPrincipal() {
        var access = gateway.access(context, List.of("documents:read", "documents:delete-index"));

        assertThat(access.baseUrl()).isEqualTo("https://runtime.example");
        assertThat(access.headers()).containsEntry("Authorization", "private");
        verify(authority).issueTargetRetirementHeaders(
            "deployment", "target", "release", "authority-fingerprint", "operation",
            List.of("documents:read", "documents:delete-index")
        );
    }

    private static Context context() {
        var snapshot = new TargetRetirementContracts.Snapshot("customer", "tenant", "deployment", "target",
            "target-generation", 1, List.of(new ReleaseGeneration("release", "version", "generation")),
            List.of(), List.of());
        return new Context("operation", "operation-key", "fingerprint", snapshot, 12, "worker", "actor",
            "CUSTOMER_ADMIN", Instant.now().plusSeconds(60),
            new Retention(RetentionDecision.RECLAIM, null, null, "backup"));
    }
}
