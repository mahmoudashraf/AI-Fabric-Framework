package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.*;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CoolifyFailedApplyRetryReadbackTest {
    private final ObjectMapper json = new ObjectMapper();
    private final DeploymentTargetProfileRepository targets = mock(DeploymentTargetProfileRepository.class);
    private final DeploymentProviderResourceHandleRepository handles = mock(DeploymentProviderResourceHandleRepository.class);
    private final CoolifyTargetProfileResolver resolver = mock(CoolifyTargetProfileResolver.class);
    private final CoolifyApiClient api = mock(CoolifyApiClient.class);
    private final CoolifyResourceOwnership ownership = mock(CoolifyResourceOwnership.class);
    private CoolifyDeploymentProvider provider;
    private CoolifyConnection connection;
    private final Instant start = Instant.parse("2026-10-20T20:43:00Z"), failure = start.plusSeconds(320);
    private List<DeploymentProviderResourceHandleEntity> inventory;
    private final Map<String, CoolifyApplicationSummary> applications = new HashMap<>();
    private final Map<String, CoolifyDeploymentSummary> jobs = new HashMap<>();

    @BeforeEach void ownedTerminalInitialFailure() {
        var profile = new DeploymentTargetProfileEntity(); profile.setId("target");
        connection = new CoolifyConnection("http://coolify.example", "synthetic", mock(CoolifyTargetProfileConfig.class));
        when(targets.findById("target")).thenReturn(Optional.of(profile));
        when(resolver.requireConnection(profile)).thenReturn(connection);
        provider = new CoolifyDeploymentProvider(targets, handles, mock(DeploymentSourceArtifactService.class),
            mock(RailwayProvisioningPlanService.class), resolver, api, json);
        ReflectionTestUtils.setField(provider, "resourceOwnership", ownership);
        var scope = new ManagedResourceOwnershipService.Scope("customer", "tenant", "deployment", "target");
        var location = new CoolifyResourceOwnership.Location(connection.baseUrl(), "project", "environment", "server", "destination");
        when(ownership.scope(any(DeploymentProviderResourceHandleEntity.class))).thenReturn(scope);
        when(ownership.location(eq(connection), anyString(), anyString())).thenReturn(location);
        var receipt = mock(ManagedResourceOwnershipService.Receipt.class); when(receipt.state()).thenReturn("OWNED");
        when(ownership.requireHandle(eq(scope), eq(location), anyString(), anyString(), any(), eq(false))).thenReturn(receipt);
        when(api.listProjects(connection)).thenReturn(List.of(new CoolifyProjectSummary("project", "Project", null, json.createObjectNode())));
        when(api.getEnvironment(connection, "project", "environment")).thenReturn(Optional.of(
            new CoolifyEnvironmentSummary("environment", "staging", "project", null, json.createObjectNode())));
        inventory = new ArrayList<>();
        for (String kind : List.of("APPLICATION", "CONNECTOR_APPLICATION", "RUNTIME_POSTGRES_DATABASE")) {
            var handle = new DeploymentProviderResourceHandleEntity(); handle.setId("handle-" + kind);
            handle.setDeploymentId("deployment"); handle.setTargetProfileId("target"); handle.setReleaseId("release");
            handle.setProviderType(DeploymentProviderType.COOLIFY); handle.setResourceKind(kind); handle.setProviderResourceUuid(kind);
            handle.setProviderProjectUuid("project"); handle.setProviderEnvironmentUuid("environment"); handle.setProviderServerUuid("server");
            handle.setStatus("DEPLOY_REQUESTED"); handle.setMetadataJson("{\"applicationName\":\"name\",\"databaseName\":\"name\",\"ownershipReceiptId\":\"receipt\"}");
            inventory.add(handle);
        }
        when(handles.findByDeploymentIdAndTargetProfileIdOrderByUpdatedAtDesc("deployment", "target")).thenAnswer(call -> List.copyOf(inventory));
        when(handles.findByProviderResourceUuidOrderByIdAsc(anyString())).thenAnswer(call -> inventory.stream().filter(h -> h.getProviderResourceUuid().equals(call.getArgument(0))).toList());
        applications.put("APPLICATION", application("APPLICATION", "running:healthy"));
        applications.put("CONNECTOR_APPLICATION", application("CONNECTOR_APPLICATION", "exited:unhealthy"));
        when(api.getApplication(eq(connection), anyString())).thenAnswer(call -> Optional.ofNullable(applications.get(call.getArgument(1))));
        when(api.getDatabase(connection, "RUNTIME_POSTGRES_DATABASE")).thenReturn(Optional.of(new CoolifyDatabaseSummary(
            "RUNTIME_POSTGRES_DATABASE", "name", "running:healthy", "postgres", "user", "ai_state", json.createObjectNode())));
        jobs.put("APPLICATION", job("APPLICATION", "finished")); jobs.put("CONNECTOR_APPLICATION", job("CONNECTOR_APPLICATION", "failed"));
        when(api.applicationDeploymentHistory(eq(connection), anyString())).thenAnswer(call -> List.of(jobs.get(call.getArgument(1))));
        when(api.getDeployment(eq(connection), anyString())).thenAnswer(call -> jobs.values().stream().filter(j -> j.deploymentUuid().equals(call.getArgument(1))).findFirst());
    }

    @Test void provesExactOwnershipAndTerminalJobsWithoutStartingOrMutatingAnything() {
        var proof = read(); assertThat(proof.inventory()).containsOnlyKeys("APPLICATION", "CONNECTOR_APPLICATION", "RUNTIME_POSTGRES_DATABASE");
        assertThat(proof.inventory().toString()).doesNotContain("synthetic");
        verify(ownership, times(3)).requireHandle(any(), any(), anyString(), anyString(), any(), eq(false));
        verify(ownership, times(3)).requireLive(any(), anyString(), anyString(), anyString(), anyString(), anyString(), any(), any(), any());
        verify(api, never()).start(any(), anyString(), anyBoolean(), anyBoolean());
        verify(handles, never()).save(any());
    }

    @ParameterizedTest @ValueSource(strings = {"pending", "unknown", "missingFinish", "wrongApplication", "oldJob", "runningConnector", "unhealthyRuntime", "foreignRelease", "extraRole", "ownership"})
    void rejectsAmbiguousNativeEffectsOrOwnership(String change) {
        switch (change) {
            case "pending" -> jobs.put("CONNECTOR_APPLICATION", job("CONNECTOR_APPLICATION", "queued"));
            case "unknown" -> jobs.put("CONNECTOR_APPLICATION", job("CONNECTOR_APPLICATION", "unexpected"));
            case "missingFinish" -> { var j = jobs.get("CONNECTOR_APPLICATION"); jobs.put("CONNECTOR_APPLICATION", new CoolifyDeploymentSummary(j.deploymentUuid(), j.applicationName(), j.applicationUuid(), j.status(), null, null, j.createdAt(), j.updatedAt(), null, j.raw())); }
            case "wrongApplication" -> { var j = jobs.get("CONNECTOR_APPLICATION"); jobs.put("CONNECTOR_APPLICATION", new CoolifyDeploymentSummary(j.deploymentUuid(), j.applicationName(), "foreign", j.status(), null, null, j.createdAt(), j.updatedAt(), j.finishedAt(), j.raw())); }
            case "oldJob" -> { var j = jobs.get("CONNECTOR_APPLICATION"); jobs.put("CONNECTOR_APPLICATION", new CoolifyDeploymentSummary(j.deploymentUuid(), j.applicationName(), j.applicationUuid(), j.status(), null, null, start.minusSeconds(1).toString(), j.updatedAt(), j.finishedAt(), j.raw())); }
            case "runningConnector" -> applications.put("CONNECTOR_APPLICATION", application("CONNECTOR_APPLICATION", "running:healthy"));
            case "unhealthyRuntime" -> applications.put("APPLICATION", application("APPLICATION", "running:unhealthy"));
            case "foreignRelease" -> inventory.getFirst().setReleaseId("foreign");
            case "extraRole" -> inventory.getLast().setResourceKind("VECTORIZATION_RUNNER_APPLICATION");
            case "ownership" -> when(ownership.requireHandle(any(), any(), anyString(), anyString(), any(), eq(false))).thenThrow(new IllegalStateException("receipt ownership changed"));
            default -> throw new AssertionError(change);
        }
        assertThatThrownBy(this::read).isInstanceOf(RuntimeException.class);
        verify(api, never()).start(any(), anyString(), anyBoolean(), anyBoolean()); verify(handles, never()).save(any());
    }
    @Test void postProvisionContinuationRequiresBothApplicationsHealthyAndFinishedWithoutRedeploy() {
        applications.put("CONNECTOR_APPLICATION", application("CONNECTOR_APPLICATION", "running:healthy"));
        jobs.put("CONNECTOR_APPLICATION", job("CONNECTOR_APPLICATION", "finished"));
        assertThat(provider.postProvisionResumeReadback("deployment", "target", "release", start, failure).inventory()).hasSize(3);
        verify(api, never()).start(any(), anyString(), anyBoolean(), anyBoolean()); verify(handles, never()).save(any());
        assertThatThrownBy(this::read).isInstanceOf(RuntimeException.class);
    }
    @ParameterizedTest @ValueSource(strings = {"unhealthy", "failed", "pending", "old", "foreign"})
    void postProvisionContinuationDoesNotReuseTheStartupFailureAcceptance(String change) {
        applications.put("CONNECTOR_APPLICATION", application("CONNECTOR_APPLICATION", "running:healthy"));
        jobs.put("CONNECTOR_APPLICATION", job("CONNECTOR_APPLICATION", "finished"));
        switch (change) {
            case "unhealthy" -> applications.put("CONNECTOR_APPLICATION", application("CONNECTOR_APPLICATION", "exited:unhealthy"));
            case "failed" -> jobs.put("CONNECTOR_APPLICATION", job("CONNECTOR_APPLICATION", "failed"));
            case "pending" -> jobs.put("CONNECTOR_APPLICATION", job("CONNECTOR_APPLICATION", "queued"));
            case "old" -> assertThatThrownBy(() -> provider.postProvisionResumeReadback("deployment", "target", "release", failure.plusSeconds(1), failure.plusSeconds(2))).isInstanceOf(RuntimeException.class);
            case "foreign" -> inventory.getFirst().setReleaseId("other");
            default -> throw new AssertionError(change);
        }
        if (!"old".equals(change)) assertThatThrownBy(() -> provider.postProvisionResumeReadback("deployment", "target", "release", start, failure)).isInstanceOf(RuntimeException.class);
        verify(api, never()).start(any(), anyString(), anyBoolean(), anyBoolean()); verify(handles, never()).save(any());
    }
    @Test void reapplyRetryRetainsPriorRuntimeOwnershipAndAllowsOnlyTheStoppedExistingConnector() {
        preparedReapply();
        var proof = reapplyRead();
        assertThat(proof.inventory()).containsOnlyKeys("APPLICATION", "CONNECTOR_APPLICATION", "RUNTIME_POSTGRES_DATABASE");
        assertThat(proof.inventory().get("APPLICATION")).contains("\"releaseId\":\"prior-release\"");
        assertThat(proof.inventory().get("CONNECTOR_APPLICATION")).contains("\"releaseId\":\"release\"");
        verify(api, never()).start(any(), anyString(), anyBoolean(), anyBoolean());
        verify(handles, never()).save(any());
        verify(ownership, times(3)).requireHandle(any(), any(), anyString(), anyString(), any(), eq(false));
    }

    @ParameterizedTest @ValueSource(strings = {"runtimeRebound", "foreignConnector", "foreignTarget", "duplicateRole",
        "runningConnector", "unhealthyRuntime", "pendingJob", "newJob", "failedJob", "ownership"})
    void reapplyRetryRejectsStartedReplacementAndAmbiguousOwnership(String change) {
        preparedReapply();
        switch (change) {
            case "runtimeRebound" -> inventory.getFirst().setReleaseId("release");
            case "foreignConnector" -> inventory.get(1).setReleaseId("foreign");
            case "foreignTarget" -> inventory.getFirst().setTargetProfileId("foreign");
            case "duplicateRole" -> inventory.add(inventory.getFirst());
            case "runningConnector" -> applications.put("CONNECTOR_APPLICATION", application("CONNECTOR_APPLICATION", "running:healthy"));
            case "unhealthyRuntime" -> applications.put("APPLICATION", application("APPLICATION", "running:unhealthy"));
            case "pendingJob" -> jobs.put("APPLICATION", job("APPLICATION", "in_progress"));
            case "newJob" -> {
                var j = jobs.get("APPLICATION");
                jobs.put("APPLICATION", new CoolifyDeploymentSummary(j.deploymentUuid(), j.applicationName(), j.applicationUuid(),
                    j.status(), null, null, start.plusSeconds(601).toString(), start.plusSeconds(620).toString(),
                    start.plusSeconds(620).toString(), j.raw()));
            }
            case "failedJob" -> jobs.put("APPLICATION", job("APPLICATION", "failed"));
            case "ownership" -> when(ownership.requireHandle(any(), any(), anyString(), anyString(), any(), eq(false)))
                .thenThrow(new IllegalStateException("receipt ownership changed"));
            default -> throw new AssertionError(change);
        }
        assertThatThrownBy(this::reapplyRead).isInstanceOf(RuntimeException.class);
        verify(api, never()).start(any(), anyString(), anyBoolean(), anyBoolean());
        verify(handles, never()).save(any());
    }

    private void preparedReapply() {
        inventory.getFirst().setReleaseId("prior-release");
        applications.put("CONNECTOR_APPLICATION", application("CONNECTOR_APPLICATION", "exited"));
        jobs.put("CONNECTOR_APPLICATION", job("CONNECTOR_APPLICATION", "finished"));
    }

    private com.ai.fabric.platform.backend.deployment.admission.FailedApplyRetryContracts.NativeReadback reapplyRead() {
        return provider.reapplyBeforeRuntimeStopReadback("deployment", "target", "release", "prior-release",
            start, start.plusSeconds(600), start.plusSeconds(650));
    }

    private CoolifyApplicationSummary application(String uuid, String status) {
        return new CoolifyApplicationSummary(uuid, "name", "https://owned.example", status, "image", "digest", json.createObjectNode());
    }
    private CoolifyDeploymentSummary job(String uuid, String status) {
        return new CoolifyDeploymentSummary("job-" + uuid, "name", uuid, status, null, null,
            start.plusSeconds(20).toString(), failure.toString(), failure.toString(), json.createObjectNode());
    }
    private com.ai.fabric.platform.backend.deployment.admission.FailedApplyRetryContracts.NativeReadback read() {
        return provider.failedApplyRetryReadback("deployment", "target", "release", start, failure);
    }
}
