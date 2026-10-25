package com.ai.fabric.platform.backend.deployment.operations.lifecycle.runtimeadapter;

import static com.ai.fabric.platform.backend.deployment.operations.lifecycle.LifecycleContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ai.fabric.platform.backend.deployment.authority.DeploymentRuntimeAuthorityService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentRuntimeJsonTransport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RuntimeLifecycleGatewayTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final DeploymentRuntimeJsonTransport transport = mock(DeploymentRuntimeJsonTransport.class);
    private final DeploymentRepository deployments = mock(DeploymentRepository.class);
    private final DeploymentReleaseRepository releases = mock(DeploymentReleaseRepository.class);
    private final DeploymentRuntimeAuthorityService authority = mock(DeploymentRuntimeAuthorityService.class);
    private final RuntimeLifecycleGateway gateway = new RuntimeLifecycleGateway(transport, mapper, deployments, releases, authority);
    private final RuntimeLifecycleGateway.FrozenTarget target = new RuntimeLifecycleGateway.FrozenTarget(
        "dep-1", "tenant-1", "target-1", "rel-1", "a".repeat(64), "https://runtime.example");
    private final RuntimeLifecycleGateway.Operation operation = new RuntimeLifecycleGateway.Operation(
        "op-1", "b".repeat(64), Instant.parse("2020-01-01T00:00:00Z"), Instant.parse("2100-01-01T00:00:00Z"), null);

    @Test
    void beginUsesExactFrozenScopeAndFixedScopeMachineIssuer() {
        var deployment = new DeploymentEntity();
        deployment.setId("dep-1"); deployment.setTenantId("tenant-1");
        var release = new DeploymentReleaseEntity();
        release.setId("rel-1"); release.setDeploymentId("dep-1"); release.setTargetProfileId("target-1");
        release.setRuntimeAuthorityFingerprint("a".repeat(64));
        when(deployments.findById("dep-1")).thenReturn(Optional.of(deployment));
        when(releases.findById("rel-1")).thenReturn(Optional.of(release));
        when(authority.issueLifecycleHeaders(deployment, release, "op-1", true)).thenReturn(Map.of("Authorization", "Bearer machine"));
        reply("/status", readback("ACTIVE", 0));
        reply("/commands", mapper.createObjectNode().set("current", readback("DRAINING", 1)));

        assertThat(gateway.execute(Phase.BEGIN_DRAIN, target, operation).status()).isEqualTo(Status.SUCCEEDED);

        verify(authority).issueLifecycleHeaders(deployment, release, "op-1", true);
        verify(transport).send(eq(target.runtimeBaseUrl()), eq("POST"), endsWith("/commands"), anyMap(), argThat(command ->
            command.path("type").asText().equals("BEGIN_DRAIN")
                && command.path("expectedFenceEpoch").asLong() == 0
                && command.path("binding").path("requestFingerprint").asText().equals(operation.requestFingerprint())
                && command.path("binding").path("target").path("releaseId").asText().equals("rel-1")), isNull());
    }

    @Test
    void changedFrozenAuthorityOrTenantNeverIssuesMachineHeaders() {
        var deployment = new DeploymentEntity(); deployment.setTenantId("tenant-other");
        var release = new DeploymentReleaseEntity(); release.setRuntimeAuthorityFingerprint("c".repeat(64));
        when(deployments.findById("dep-1")).thenReturn(Optional.of(deployment));
        when(releases.findById("rel-1")).thenReturn(Optional.of(release));
        assertThatThrownBy(() -> gateway.execute(Phase.BEGIN_DRAIN, target, operation)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(authority, transport);
    }

    @Test
    void observesActualInventoryAndDoesNotTreatMissingCoverageAsDrainSuccess() {
        reply("/status", readback("DRAINING", 1));
        var snapshot = mapper.createObjectNode(); snapshot.set("target", readback("DRAINING", 1));
        snapshot.put("inventoryComplete", true).put("workerFencingComplete", false).put("localDrainComplete", false);
        snapshot.putArray("blockers").add("FRAMEWORK_INDEXING_PROCESSOR_TARGET_EPOCH_FENCE_UNSUPPORTED");
        snapshot.putArray("remoteObligations");
        reply("/snapshots", snapshot);
        var result = execute(Phase.OBSERVE_DRAIN);
        assertThat(result.status()).isEqualTo(Status.PENDING);
        assertThat(result.blockers()).contains("FRAMEWORK_INDEXING_PROCESSOR_TARGET_EPOCH_FENCE_UNSUPPORTED");
        verify(transport, never()).send(anyString(), anyString(), endsWith("/commands"), anyMap(), any(), any());
    }

    @Test
    void completeSnapshotCarriesItsRealEvidenceReference() {
        reply("/status", readback("DRAINING", 1));
        var snapshot = mapper.createObjectNode(); snapshot.set("target", readback("DRAINING", 1));
        snapshot.put("inventoryComplete", true).put("workerFencingComplete", true).put("localDrainComplete", true)
            .put("evidenceReference", "runtime-target-drain:snapshot-1");
        snapshot.putArray("blockers");
        snapshot.putArray("remoteObligations");
        reply("/snapshots", snapshot);
        assertThat(execute(Phase.OBSERVE_DRAIN)).isEqualTo(new RuntimeLifecycleGateway.Result(
            Status.SUCCEEDED, "RUNTIME_LOCAL_DRAIN_COMPLETE", java.util.List.of(), "runtime-target-drain:snapshot-1", 1));
    }

    @Test
    void exactPhaseReadbackReplaysWithoutDispatchButAnotherOperationNeverDoes() {
        reply("/status", readback("DRAINING", 1));
        assertThat(execute(Phase.BEGIN_DRAIN).status()).isEqualTo(Status.SUCCEEDED);
        var other = readback("DRAINING", 1); ((com.fasterxml.jackson.databind.node.ObjectNode) other).put("operationId", "other");
        reply("/status", other);
        assertThat(execute(Phase.BEGIN_DRAIN).status()).isEqualTo(Status.BLOCKED);
        verify(transport, never()).send(anyString(), anyString(), endsWith("/commands"), anyMap(), any(), any());
    }

    @Test
    void localCompletionNeverFabricatesRemoteQuiescenceEvidence() {
        reply("/status", readback("DRAINING", 1));
        var snapshot = mapper.createObjectNode(); snapshot.set("target", readback("DRAINING", 1));
        snapshot.put("inventoryComplete", true).put("workerFencingComplete", true).put("localDrainComplete", true);
        snapshot.putArray("blockers");
        var remote = snapshot.putArray("remoteObligations").addObject();
        remote.put("dependencyKind", "CONNECTOR").set("target", readback("DRAINING", 1).path("target"));
        reply("/snapshots", snapshot);
        assertThat(execute(Phase.OBSERVE_DRAIN).status()).isEqualTo(Status.SUCCEEDED);
        assertThat(execute(Phase.OBSERVE_DRAIN).remoteDependencies()).containsExactly("CONNECTOR");
        assertThat(execute(Phase.FENCE).status()).isEqualTo(Status.BLOCKED);
        assertThat(execute(Phase.FENCE).code()).isEqualTo("AUTHORITATIVE_REMOTE_READBACK_REFERENCE_REQUIRED");
        verify(transport, never()).send(anyString(), anyString(), endsWith("/commands"), anyMap(), any(), any());
    }

    @Test
    void actualParentJournalCanFenceOnlyAfterFreshLocalObservationAndRuntimeRecheck() {
        reply("/status", readback("DRAINING", 1));
        var snapshot = mapper.createObjectNode(); snapshot.set("target", readback("DRAINING", 1));
        snapshot.put("inventoryComplete", true).put("workerFencingComplete", true).put("localDrainComplete", true);
        snapshot.putArray("blockers");
        var remote = snapshot.putArray("remoteObligations").addObject();
        remote.put("dependencyKind", "CONNECTOR").set("target", readback("DRAINING", 1).path("target"));
        reply("/snapshots", snapshot);
        reply("/commands", mapper.createObjectNode().set("current", readback("AUTHORITY_REVOKED", 2)));
        var journalled = new RuntimeLifecycleGateway.Operation(operation.operationId(), operation.requestFingerprint(),
            operation.dueAt(), operation.deadlineAt(), "core-native-journal:workers-confirmed-stopped");
        var result = gateway.execute(Phase.FENCE, target, journalled, Map.of("Authorization", "machine"));
        assertThat(result.status()).isEqualTo(Status.SUCCEEDED);
        assertThat(result.remoteDependencies()).isEmpty();
        verify(transport).send(anyString(), eq("POST"), endsWith("/commands"), anyMap(), argThat(command ->
            command.path("externalEvidenceReference").asText().equals(journalled.externalEvidenceReference())), isNull());
    }

    @Test
    void namedRuntimeDrainRaceIsPendingAndUnsafeErrorsAreSanitized() {
        when(transport.send(anyString(), anyString(), anyString(), anyMap(), any(), isNull()))
            .thenReturn(new DeploymentRuntimeJsonTransport.Response(409, mapper.createObjectNode().put("code", "DRAIN_NOT_PROVED_COMPLETE")));
        assertThat(execute(Phase.FENCE).status()).isEqualTo(Status.PENDING);
        assertThat(execute(Phase.FENCE).code()).isEqualTo("DRAIN_NOT_PROVED_COMPLETE");
        when(transport.send(anyString(), anyString(), anyString(), anyMap(), any(), isNull()))
            .thenReturn(new DeploymentRuntimeJsonTransport.Response(409, mapper.createObjectNode().put("code", "secret=token-value")));
        assertThat(execute(Phase.FENCE).code()).isEqualTo("RUNTIME_HTTP_409");
    }

    @Test
    void wrongReleaseReadbackAndMalformedSnapshotAreUnknownNotSuccessful() {
        var wrong = readback("DRAINING", 1);
        ((com.fasterxml.jackson.databind.node.ObjectNode) wrong.path("target")).put("releaseId", "other");
        reply("/status", wrong);
        assertThat(execute(Phase.OBSERVE_DRAIN).status()).isEqualTo(Status.UNKNOWN);
        reply("/status", readback("DRAINING", 1)); reply("/snapshots", mapper.createObjectNode());
        assertThat(execute(Phase.OBSERVE_DRAIN).status()).isEqualTo(Status.UNKNOWN);
        var forged = readback("AUTHORITY_REVOKED", 2);
        ((com.fasterxml.jackson.databind.node.ObjectNode) forged).put("authorityRevoked", false);
        reply("/status", forged);
        assertThat(execute(Phase.FENCE).status()).isEqualTo(Status.UNKNOWN);
    }

    @Test
    void timeoutAndServerFailureAreUnknownAndNoAutomaticRetryOccurs() {
        when(transport.send(anyString(), anyString(), anyString(), anyMap(), any(), isNull())).thenThrow(new IllegalStateException("timeout"));
        assertThat(execute(Phase.BEGIN_DRAIN).status()).isEqualTo(Status.UNKNOWN);
        verify(transport).send(anyString(), anyString(), anyString(), anyMap(), any(), isNull());
        reset(transport);
        when(transport.send(anyString(), anyString(), anyString(), anyMap(), any(), isNull()))
            .thenReturn(new DeploymentRuntimeJsonTransport.Response(503, mapper.createObjectNode()));
        assertThat(execute(Phase.BEGIN_DRAIN).status()).isEqualTo(Status.UNKNOWN);
    }

    @Test
    void disabledOrDeniedRuntimeIsBlockedAndExpiredBeginDoesNotDispatch() {
        when(transport.send(anyString(), anyString(), anyString(), anyMap(), any(), isNull()))
            .thenReturn(new DeploymentRuntimeJsonTransport.Response(403, mapper.createObjectNode()));
        assertThat(execute(Phase.BEGIN_DRAIN).status()).isEqualTo(Status.BLOCKED);
        reset(transport);
        var expired = new RuntimeLifecycleGateway.Operation("op-1", "b".repeat(64), Instant.EPOCH, Instant.EPOCH.plusSeconds(1), null);
        reply("/status", readback("ACTIVE", 0));
        assertThat(gateway.execute(Phase.BEGIN_DRAIN, target, expired, Map.of("Authorization", "machine")).status()).isEqualTo(Status.BLOCKED);
        verify(transport).send(anyString(), anyString(), endsWith("/status"), anyMap(), any(), isNull());
        verify(transport, never()).send(anyString(), anyString(), endsWith("/commands"), anyMap(), any(), any());
    }

    @Test
    void expiredFenceCanAcknowledgeAlreadyAppliedWithoutDispatch() {
        var expired = expiredOperation();
        var fenced = readback("AUTHORITY_REVOKED", 2);
        ((com.fasterxml.jackson.databind.node.ObjectNode) fenced).put("drainDeadline", expired.deadlineAt().toString());
        reply("/status", fenced);
        var result = gateway.execute(Phase.FENCE, target, expired, Map.of("Authorization", "machine"));
        assertThat(result.status()).isEqualTo(Status.SUCCEEDED);
        assertThat(result.code()).isEqualTo("RUNTIME_AUTHORITY_REVOKED");
        verify(transport, never()).send(anyString(), anyString(), endsWith("/commands"), anyMap(), any(), any());
    }

    @Test
    void expiredUnappliedFenceCannotDispatchEvenWithCompleteObservation() {
        var expired = expiredOperation();
        var draining = readback("DRAINING", 1);
        ((com.fasterxml.jackson.databind.node.ObjectNode) draining).put("drainDeadline", expired.deadlineAt().toString());
        reply("/status", draining);
        var snapshot = mapper.createObjectNode(); snapshot.set("target", draining);
        snapshot.put("inventoryComplete", true).put("workerFencingComplete", true).put("localDrainComplete", true);
        snapshot.putArray("blockers"); snapshot.putArray("remoteObligations");
        reply("/snapshots", snapshot);
        assertThat(gateway.execute(Phase.FENCE, target, expired, Map.of("Authorization", "machine")).status()).isEqualTo(Status.BLOCKED);
        verify(transport, never()).send(anyString(), anyString(), endsWith("/commands"), anyMap(), any(), any());
    }

    @Test
    void expiredPendingDrainReturnsDeadlineBlockerWithItsRealObservation() {
        var expired = expiredOperation();
        var draining = readback("DRAINING", 1);
        ((com.fasterxml.jackson.databind.node.ObjectNode) draining).put("drainDeadline", expired.deadlineAt().toString());
        reply("/status", draining);
        var snapshot = mapper.createObjectNode(); snapshot.set("target", draining);
        snapshot.put("inventoryComplete", true).put("workerFencingComplete", true).put("localDrainComplete", false);
        snapshot.putArray("blockers").add("DRAIN_WORK_REMAINS"); snapshot.putArray("remoteObligations");
        reply("/snapshots", snapshot);
        var result = gateway.execute(Phase.OBSERVE_DRAIN, target, expired, Map.of("Authorization", "machine"));
        assertThat(result.status()).isEqualTo(Status.BLOCKED);
        assertThat(result.code()).isEqualTo("DRAIN_DEADLINE_EXCEEDED");
        assertThat(result.blockers()).contains("DRAIN_WORK_REMAINS");
    }

    private RuntimeLifecycleGateway.Operation expiredOperation() {
        return new RuntimeLifecycleGateway.Operation(operation.operationId(), operation.requestFingerprint(), Instant.EPOCH, Instant.EPOCH.plusSeconds(1), null);
    }

    private RuntimeLifecycleGateway.Result execute(Phase phase) {
        return gateway.execute(phase, target, operation, Map.of("Authorization", "machine"));
    }
    private void reply(String path, JsonNode body) {
        when(transport.send(eq(target.runtimeBaseUrl()), eq("POST"), eq("/api/private/runtime-target-retirement" + path), anyMap(), any(), isNull()))
            .thenReturn(new DeploymentRuntimeJsonTransport.Response(200, body));
    }
    private JsonNode readback(String state, long epoch) {
        var value = mapper.createObjectNode();
        value.set("target", mapper.createObjectNode().put("deploymentId", "dep-1").put("targetProfileId", "target-1").put("releaseId", "rel-1"));
        value.put("tenantId", "tenant-1").put("state", state).put("fenceEpoch", epoch)
            .put("authorityRevoked", state.equals("AUTHORITY_REVOKED") || state.equals("RECLAIM_STARTED"))
            .put("reclaimStarted", state.equals("RECLAIM_STARTED"))
            .put("operationId", "op-1").put("requestFingerprint", operation.requestFingerprint())
            .put("drainDeadline", operation.deadlineAt().toString());
        return value;
    }
}
