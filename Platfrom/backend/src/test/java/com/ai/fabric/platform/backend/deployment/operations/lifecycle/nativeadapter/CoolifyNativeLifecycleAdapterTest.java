package com.ai.fabric.platform.backend.deployment.operations.lifecycle.nativeadapter;

import com.ai.fabric.platform.backend.customerselfservice.config.CustomerSelfServiceProperties;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.TargetIntent;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencyGraphEntity;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencyGraphStore;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencyFixtures;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpec;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpecValidator;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencyState;
import com.ai.fabric.platform.backend.deployment.entity.*;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.model.PreservedExternalBindingKinds;
import com.ai.fabric.platform.backend.deployment.provider.*;
import com.ai.fabric.platform.backend.deployment.repository.*;
import com.ai.fabric.platform.backend.deployment.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.*;

import static com.ai.fabric.platform.backend.deployment.operations.lifecycle.nativeadapter.NativeLifecycleAdapter.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CoolifyNativeLifecycleAdapterTest {
    final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    final DeploymentRepository deployments = mock(DeploymentRepository.class);
    final DeploymentTargetProfileRepository targets = mock(DeploymentTargetProfileRepository.class);
    final DeploymentReleaseRepository releases = mock(DeploymentReleaseRepository.class);
    final DeploymentProviderResourceHandleRepository handles = mock(DeploymentProviderResourceHandleRepository.class);
    final ManagedDependencyGraphStore graphs = mock(ManagedDependencyGraphStore.class);
    final CoolifyTargetProfileResolver resolver = mock(CoolifyTargetProfileResolver.class);
    final CoolifyApiClient api = mock(CoolifyApiClient.class);
    final CoolifyRetirementOwnership ownership = mock(CoolifyRetirementOwnership.class);
    final PlatformManagedProductProvisioningService dependencies = mock(PlatformManagedProductProvisioningService.class);
    final CoolifyHostVolumeReclamationService volumeService = mock(CoolifyHostVolumeReclamationService.class);
    final ProviderHostCommandRunner host = mock(ProviderHostCommandRunner.class);
    final CoolifyTargetProfileConfig config = new CoolifyTargetProfileConfig("https://coolify.test", "project", "staging",
        "env", "server", "destination", "example.test", null, 1, 10, false, true, "8080", "/health", "8080");
    final CoolifyConnection connection = new CoolifyConnection(config.baseUrl(), "private", config);
    final List<DeploymentProviderResourceHandleEntity> rows = new ArrayList<>();
    final Map<String, ObjectNode> nativeRows = new LinkedHashMap<>();
    final Map<String, List<CoolifyApplicationStorageSummary>> storages = new HashMap<>();
    final Set<String> presentVolumes = new HashSet<>();
    final List<String> effects = new ArrayList<>();
    final DeploymentTargetProfileEntity target = new DeploymentTargetProfileEntity();
    final Journal journal = new Journal();
    CoolifyNativeLifecycleAdapter adapter;

    @BeforeEach void setup() {
        var deployment = new DeploymentEntity(); deployment.setId("dep"); deployment.setCustomerId("customer"); deployment.setTenantId("tenant");
        target.setId("target"); target.setActive(true); target.setProviderType(DeploymentProviderType.COOLIFY); target.setEnvironmentName("staging");
        target.setProviderConfigJson("{}"); target.setNetworkPolicyJson("{}"); target.setResourceDefaultsJson("{}");
        var release = new DeploymentReleaseEntity(); release.setId("release"); release.setDeploymentId("dep"); release.setTargetProfileId("target");
        release.setProviderType(DeploymentProviderType.COOLIFY); release.setDeploymentVersionId("version"); release.setCreatedAt(Instant.parse("2026-10-25T00:00:00Z"));
        when(deployments.findById("dep")).thenReturn(Optional.of(deployment)); when(targets.findById("target")).thenReturn(Optional.of(target));
        when(releases.findById("release")).thenReturn(Optional.of(release)); when(resolver.requireConnection(target)).thenReturn(connection);
        when(graphs.find("dep", "target")).thenReturn(Optional.empty());
        when(handles.findByDeploymentIdAndTargetProfileIdOrderByUpdatedAtDesc("dep", "target")).thenReturn(rows);
        when(handles.findById(anyString())).thenAnswer(call -> rows.stream().filter(value -> value.getId().equals(call.getArgument(0))).findFirst());
        when(api.getServer(connection, "server")).thenReturn(mapper.createObjectNode().put("uuid", "server").put("ip", "127.0.0.1"));
        when(api.resolveDestinationPlacement(connection, "server", "destination")).thenReturn(Optional.of(new CoolifyDestinationPlacement("destination", "server", "native", mapper.createObjectNode())));
        when(api.getApplication(eq(connection), anyString())).thenAnswer(call -> Optional.ofNullable(nativeRows.get(call.getArgument(1)))
            .map(raw -> new CoolifyApplicationSummary(raw.path("uuid").asText(), raw.path("name").asText(), null, raw.path("status").asText(), null, null, raw)));
        when(api.getDatabase(eq(connection), anyString())).thenAnswer(call -> Optional.ofNullable(nativeRows.get(call.getArgument(1)))
            .map(raw -> new CoolifyDatabaseSummary(raw.path("uuid").asText(), raw.path("name").asText(), raw.path("status").asText(), "postgresql", "runtime", "runtime", raw)));
        when(api.listApplicationStorages(eq(connection), anyString())).thenAnswer(call -> storages.getOrDefault(call.getArgument(1), List.of()));
        when(api.listDatabaseStorages(eq(connection), anyString())).thenAnswer(call -> storages.getOrDefault(call.getArgument(1), List.of()));
        when(api.listApplications(connection)).thenReturn(List.of()); when(api.listDatabases(connection)).thenReturn(List.of());
        when(host.run(any(), anyString(), anyList())).thenAnswer(call -> {
            List<String> command = call.getArgument(2);
            if (command.size() == 6 && command.get(1).equals("volume")) {
                String name = command.get(5).substring(6, command.get(5).length() - 1);
                return new ProviderHostCommandRunner.Result(0, presentVolumes.contains(name) ? name : "");
            }
            return new ProviderHostCommandRunner.Result(0, "");
        });
        when(api.stop(eq(connection), anyString(), eq(false))).thenAnswer(call -> stopped(call.getArgument(1)));
        when(api.stopDatabase(eq(connection), anyString())).thenAnswer(call -> stopped(call.getArgument(1)));
        when(api.start(eq(connection), anyString(), eq(false), eq(true))).thenAnswer(call -> started(call.getArgument(1)));
        when(api.startDatabase(eq(connection), anyString())).thenAnswer(call -> { started(call.getArgument(1)); return new CoolifyActionResponse("started", null, null); });
        when(api.getDeployment(eq(connection), anyString())).thenAnswer(call -> {
            String job = call.getArgument(1); String uuid = job.substring(4);
            return Optional.of(new CoolifyDeploymentSummary(job, uuid, uuid, "finished", null, null, null, null, "2026-10-25T01:00:00Z", mapper.createObjectNode()));
        });
        when(api.delete(eq(connection), anyString(), eq(true), eq(false), eq(false), eq(false))).thenAnswer(call -> deleted(call.getArgument(1)));
        when(api.deleteDatabase(eq(connection), anyString(), eq(true), eq(false), eq(false), eq(false))).thenAnswer(call -> deleted(call.getArgument(1)));
        when(volumeService.reclaimAndVerify(any(), anyString(), anyString(), anyList())).thenAnswer(call -> {
            List<CoolifyHostVolumeReclamationService.Volume> values = call.getArgument(3);
            values.forEach(value -> presentVolumes.remove(value.name()));
            return new CoolifyHostVolumeReclamationService.Receipt("host", Instant.now(), values.stream().map(CoolifyHostVolumeReclamationService.Volume::uuid).collect(java.util.stream.Collectors.toSet()));
        });
        adapter = new CoolifyNativeLifecycleAdapter(deployments, targets, releases, handles, graphs, resolver, api, ownership,
            dependencies, volumeService, host, new CustomerSelfServiceProperties(Map.of(TargetIntent.CUSTOMER_STAGING,
                new CustomerSelfServiceProperties.TrustedServer("server", "127.0.0.1", "127.0.0.1")), 60), mapper);
    }

    @Test void immutableSnapshotRoundTripsWithoutSecrets() throws Exception {
        add("runtime", "APPLICATION"); add("db", "RUNTIME_POSTGRES_DATABASE");
        var frozen = freeze();
        String json = mapper.writeValueAsString(frozen);
        assertThat(json).doesNotContain("private");
        assertThat(adapter.observe(mapper.readValue(json, NativeSnapshot.class)).ready()).isTrue();
    }

    @Test void stopsWorkersThenRuntimeThenDatabaseAndStartsReverse() {
        add("runtime", "APPLICATION"); add("connector", "CONNECTOR_APPLICATION"); add("runner", "VECTORIZATION_RUNNER_APPLICATION"); add("db", "RUNTIME_POSTGRES_DATABASE");
        var snapshot = freeze();
        for (int index = 0; index < 4; index++) adapter.stop(snapshot, journal);
        assertThat(adapter.verifyStopped(snapshot).complete()).isTrue();
        assertThat(effects).containsExactly("STOP connector", "STOP runner", "STOP runtime", "STOP db");
        effects.clear();
        for (int index = 0; index < 5; index++) adapter.restart(snapshot, journal);
        assertThat(effects).containsExactly("START db", "START runtime", "START runner", "START connector");
        assertThat(adapter.restart(snapshot, journal).complete()).isTrue();
    }

    @Test void queuedStopDoesNotPermitLaterResourceOrClaimStopped() {
        add("connector", "CONNECTOR_APPLICATION"); add("runtime", "APPLICATION");
        doReturn(new CoolifyActionResponse("queued", "job", null)).when(api).stop(connection, "connector", false);
        var snapshot = freeze();
        assertThat(adapter.stop(snapshot, journal).complete()).isFalse();
        assertThat(adapter.stop(snapshot, journal).blockers()).contains("NATIVE_STOP_OUTCOME_UNKNOWN");
        verify(api, times(1)).stop(connection, "connector", false);
        verify(api, never()).stop(connection, "runtime", false);
    }

    @Test void quiescesWorkersAndInboxWithoutStoppingRuntimeOrAnyDatabase() {
        add("runtime", "APPLICATION"); add("db", "RUNTIME_POSTGRES_DATABASE");
        add("connector", "CONNECTOR_APPLICATION"); add("runner", "VECTORIZATION_RUNNER_APPLICATION");
        addManagedInbox("inbox", ManagedDependencySpec.Contract.BASIC_INBOX_KEYS_V1);
        var snapshot = freeze();
        Observation result = null;
        for (int index = 0; index < 3; index++) result = adapter.quiesceWorkers(snapshot, journal);
        assertThat(result.complete()).isTrue(); assertThat(result.stopped()).isFalse(); assertThat(result.absent()).isFalse();
        assertThat(result.ready()).isFalse(); assertThat(result.dataReclaimed()).isFalse();
        assertThat(result.resources()).extracting(ResourceObservation::resourceId)
            .containsExactly("connector", "runner", "graph:inbox:DEPENDENCY_APPLICATION");
        assertThat(effects).containsExactly("STOP connector", "STOP runner", "STOP inbox");
        assertThat(nativeRows.get("runtime").path("status").asText()).isEqualTo("running:healthy");
        assertThat(nativeRows.get("db").path("status").asText()).isEqualTo("running:healthy");
        assertThat(nativeRows.get("inbox-db").path("status").asText()).isEqualTo("running:healthy");
        verify(api, never()).stopDatabase(any(), anyString());
    }

    @Test void zeroWorkersSucceedsWithoutMakingGlobalStopOrStorageClaims() {
        add("runtime", "APPLICATION"); add("db", "RUNTIME_POSTGRES_DATABASE");
        var result = adapter.quiesceWorkers(freeze(), journal);
        assertThat(result.complete()).isTrue(); assertThat(result.resources()).isEmpty();
        assertThat(result.stopped()).isFalse(); assertThat(result.absent()).isFalse();
        assertThat(result.ready()).isFalse(); assertThat(result.dataReclaimed()).isFalse();
        verify(api, never()).stop(any(), anyString(), anyBoolean());
        verify(api, never()).stopDatabase(any(), anyString());
    }

    @Test void queuedWorkerStopIsReadBackWithoutRedispatchAndCanSettleLater() {
        add("runtime", "APPLICATION"); add("connector", "CONNECTOR_APPLICATION");
        doReturn(new CoolifyActionResponse("queued", null, null)).when(api).stop(connection, "connector", false);
        var snapshot = freeze();
        assertThat(adapter.quiesceWorkers(snapshot, journal).complete()).isFalse();
        assertThat(adapter.quiesceWorkers(snapshot, journal).blockers()).contains("NATIVE_WORKER_STOP_OUTCOME_UNKNOWN");
        nativeRows.get("connector").put("status", "exited");
        assertThat(adapter.quiesceWorkers(snapshot, journal).complete()).isTrue();
        verify(api, times(1)).stop(connection, "connector", false);
        verify(api, never()).stop(connection, "runtime", false);
    }

    @Test void workerStopRequiresNativeHostProofNotJustProviderStoppedStatus() {
        add("runtime", "APPLICATION"); add("connector", "CONNECTOR_APPLICATION");
        when(host.run(any(), anyString(), anyList())).thenReturn(new ProviderHostCommandRunner.Result(0, "running-container"));
        var result = adapter.quiesceWorkers(freeze(), journal);
        assertThat(result.complete()).isFalse();
        assertThat(result.blockers()).contains("NATIVE_CONTAINER_ABSENCE_UNPROVEN");
        assertThat(journal.complete).isEmpty();
        verify(api, never()).stop(connection, "runtime", false);
    }

    @Test void workerQuiescenceRevalidatesTheNonWorkerNativeIdentityBeforeAnyEffect() {
        add("runtime", "APPLICATION"); add("connector", "CONNECTOR_APPLICATION");
        var snapshot = freeze(); nativeRows.get("runtime").put("created_at", "foreign-generation");
        assertThatThrownBy(() -> adapter.quiesceWorkers(snapshot, journal)).hasMessageContaining("NATIVE_RESOURCE_IDENTITY_CHANGED");
        verify(api, never()).stop(any(), anyString(), anyBoolean());
    }

    @Test void remoteDependencySupportUsesOwnedNativeKindsNotNames() {
        add("connector", "APPLICATION"); var snapshot = freeze();
        assertThat(adapter.supportsRemoteDependency(snapshot, "CONNECTOR")).isFalse();
        add("real-connector", "CONNECTOR_APPLICATION"); add("runner", "VECTORIZATION_RUNNER_APPLICATION");
        snapshot = freeze();
        assertThat(adapter.supportsRemoteDependency(snapshot, "CONNECTOR")).isTrue();
        assertThat(adapter.supportsRemoteDependency(snapshot, "VECTORIZATION_RUNNER")).isTrue();
        assertThat(adapter.supportsRemoteDependency(snapshot, "HUMAN_INBOX")).isFalse();
        assertThat(adapter.supportsRemoteDependency(snapshot, "external-connector")).isFalse();
        assertThat(adapter.supportsRemoteDependency(snapshot, null)).isFalse();
    }

    @Test void inboxObligationRequiresActualInboxContractNotAnInboxSlotName() {
        add("runtime", "APPLICATION"); addManagedInbox("inbox", ManagedDependencySpec.Contract.HTTP_SERVICE);
        assertThat(adapter.supportsRemoteDependency(freeze(), "HUMAN_INBOX")).isFalse();
    }

    @Test void actualInboxContractCanUseAnyReviewedSlotName() {
        add("runtime", "APPLICATION"); addManagedInbox("helpdesk", ManagedDependencySpec.Contract.BASIC_INBOX_KEYS_V1);
        assertThat(adapter.supportsRemoteDependency(freeze(), "HUMAN_INBOX")).isTrue();
    }

    @Test void remoteDependencySupportRejectsDriftAndUnsupportedProvider() {
        add("connector", "CONNECTOR_APPLICATION"); var snapshot = freeze();
        nativeRows.get("connector").put("description", "foreign-owner");
        assertThat(adapter.supportsRemoteDependency(snapshot, "CONNECTOR")).isFalse();
        target.setProviderType(DeploymentProviderType.RAILWAY_API);
        assertThat(adapter.supportsRemoteDependency(snapshot, "CONNECTOR")).isFalse();
        verify(api, never()).stop(any(), anyString(), anyBoolean());
    }

    @Test void unknownStopCanSettleByStrongStoppedAndHostReadbackWithoutRedispatch() {
        add("runtime", "APPLICATION"); var snapshot = freeze();
        doThrow(new IllegalStateException("socket lost")).when(api).stop(connection, "runtime", false);
        assertThatThrownBy(() -> adapter.stop(snapshot, journal)).isInstanceOf(IllegalStateException.class);
        nativeRows.get("runtime").put("status", "exited");
        assertThat(adapter.stop(snapshot, journal).complete()).isTrue();
        verify(api, times(1)).stop(connection, "runtime", false);
    }

    @Test void unknownStartDoesNotTreatRunningAsRestartProof() {
        add("runtime", "APPLICATION"); var snapshot = freeze(); adapter.stop(snapshot, journal);
        doAnswer(call -> {
            nativeRows.get("runtime").put("status", "running:healthy"); throw new IllegalStateException("socket lost");
        }).when(api).start(connection, "runtime", false, true);
        assertThatThrownBy(() -> adapter.restart(snapshot, journal)).isInstanceOf(IllegalStateException.class);
        assertThat(adapter.restart(snapshot, journal).complete()).isFalse();
        assertThat(adapter.restart(snapshot, journal).blockers()).contains("NATIVE_RESTART_REQUIRES_STOP_OR_EXACT_JOB");
        verify(api, times(1)).start(connection, "runtime", false, true);
    }

    @Test void resumesExactNativeStartJobAfterCheckpointedDispatch() {
        add("runtime", "APPLICATION"); var snapshot = freeze(); adapter.stop(snapshot, journal);
        when(api.getDeployment(connection, "job-runtime")).thenReturn(Optional.of(new CoolifyDeploymentSummary("job-runtime", "runtime", "runtime", "in_progress", null, null, null, null, null, null)));
        assertThat(adapter.restart(snapshot, journal).complete()).isFalse();
        when(api.getDeployment(connection, "job-runtime")).thenReturn(Optional.of(new CoolifyDeploymentSummary("job-runtime", "runtime", "runtime", "finished", null, null, null, null, "done", null)));
        assertThat(adapter.restart(snapshot, journal).complete()).isTrue();
        verify(api, times(1)).start(connection, "runtime", false, true);
    }

    @Test void nativeJobForDifferentResourceIsRejected() {
        add("runtime", "APPLICATION"); var snapshot = freeze(); adapter.stop(snapshot, journal);
        when(api.getDeployment(connection, "job-runtime")).thenReturn(Optional.of(new CoolifyDeploymentSummary("job-runtime", "other", "other", "finished", null, null, null, null, "done", null)));
        assertThatThrownBy(() -> adapter.restart(snapshot, journal)).hasMessageContaining("NATIVE_START_JOB_IDENTITY_MISMATCH");
    }

    @Test void databaseStartUsesDurableSuccessfulAcknowledgmentAndStillRequiresHealthyReadback() {
        add("db", "RUNTIME_POSTGRES_DATABASE"); var snapshot = freeze(); adapter.stop(snapshot, journal);
        doReturn(new CoolifyActionResponse("accepted", null, null)).when(api).startDatabase(connection, "db");
        assertThat(adapter.restart(snapshot, journal).complete()).isFalse();
        assertThat(journal.jobs.values()).containsExactly("database-start-ack:db");
        nativeRows.get("db").put("status", "running:healthy");
        assertThat(adapter.restart(snapshot, journal).complete()).isTrue();
        verify(api, times(1)).startDatabase(connection, "db");
        verify(api, never()).getDeployment(any(), anyString());
    }

    @Test void lostDatabaseStartResponseCannotBeInferredFromRunningState() {
        add("db", "RUNTIME_POSTGRES_DATABASE"); var snapshot = freeze(); adapter.stop(snapshot, journal);
        doAnswer(call -> { nativeRows.get("db").put("status", "running:healthy"); throw new IllegalStateException("lost"); })
            .when(api).startDatabase(connection, "db");
        assertThatThrownBy(() -> adapter.restart(snapshot, journal)).isInstanceOf(IllegalStateException.class);
        assertThat(journal.jobs).isEmpty();
        assertThat(adapter.restart(snapshot, journal).complete()).isFalse();
        verify(api, times(1)).startDatabase(connection, "db");
    }

    @Test void requiresExplicitStorageApprovalBeforeAnyDelete() {
        add("runtime", "APPLICATION"); volume("runtime", "state", "owned-state"); var snapshot = freeze(); adapter.stop(snapshot, journal);
        assertThatThrownBy(() -> adapter.delete(snapshot, false, journal)).hasMessageContaining("NATIVE_STORAGE_RECLAMATION_APPROVAL_REQUIRED");
        verify(api, never()).delete(any(), anyString(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());
    }

    @Test void deleteReclaimsOwnedVolumesOnlyAndPreservesExternalBindings() {
        add("runtime", "APPLICATION"); volume("runtime", "state", "owned-state");
        var external = add("external", PreservedExternalBindingKinds.DOCUMENT_STORAGE); nativeRows.remove("external"); external.setProviderResourceUuid("customer-bucket");
        var snapshot = freeze(); adapter.stop(snapshot, journal);
        var result = adapter.delete(snapshot, true, journal);
        assertThat(result.complete()).isTrue(); assertThat(result.dataReclaimed()).isTrue();
        assertThat(snapshot.exclusions()).extracting(Exclusion::kind).containsExactly(PreservedExternalBindingKinds.DOCUMENT_STORAGE);
        verify(api, never()).delete(eq(connection), eq("customer-bucket"), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());
        assertThat(external.getStatus()).isEqualTo("ACTIVE");
        assertThat(presentVolumes).isEmpty();
    }

    @Test void queuedDeleteIsObservedAndNeverRepeated() {
        add("runtime", "APPLICATION"); var snapshot = freeze(); adapter.stop(snapshot, journal);
        doReturn(new CoolifyActionResponse("queued", null, null)).when(api).delete(connection, "runtime", true, false, false, false);
        assertThat(adapter.delete(snapshot, false, journal).complete()).isFalse();
        assertThat(adapter.delete(snapshot, false, journal).blockers()).contains("NATIVE_DELETE_OUTCOME_UNKNOWN");
        verify(api, times(1)).delete(connection, "runtime", true, false, false, false);
        nativeRows.remove("runtime");
        assertThat(adapter.delete(snapshot, false, journal).complete()).isTrue();
    }

    @Test void noAbsenceClaimWhenHostContainersRemain() {
        add("runtime", "APPLICATION"); var snapshot = freeze(); adapter.stop(snapshot, journal);
        doThrow(new ProviderResourceVerificationFailure("remaining")).when(volumeService).verifyContainersAbsent(any(), anyString(), anyString(), anyString(), anyString());
        assertThat(adapter.delete(snapshot, false, journal).complete()).isFalse();
        assertThat(adapter.verifyAbsent(snapshot).complete()).isFalse();
    }

    @Test void nativeReusedIdentityAndEditedHandleAndNewHandleAreRejected() {
        add("runtime", "APPLICATION"); var snapshot = freeze();
        nativeRows.get("runtime").put("created_at", "2027-01-01");
        assertThatThrownBy(() -> adapter.observe(snapshot)).hasMessageContaining("NATIVE_RESOURCE_IDENTITY_CHANGED");
        nativeRows.get("runtime").put("created_at", "2026-10-25");
        rows.getFirst().setMetadataJson("{\"applicationName\":\"other\",\"ownershipReceiptId\":\"receipt\"}");
        assertThatThrownBy(() -> adapter.stop(snapshot, journal)).hasMessageContaining("NATIVE_HANDLE_IDENTITY_CHANGED");
    }

    @Test void sharedVolumeAndForeignOwnershipFailBeforeNativeEffect() {
        add("runtime", "APPLICATION"); volume("runtime", "state", "owned-state");
        when(api.listApplications(connection)).thenReturn(List.of(new CoolifyApplicationSummary("foreign", "foreign", null, "running", null, null, null)));
        storages.put("foreign", storages.get("runtime"));
        assertThatThrownBy(this::freeze).hasMessageContaining("NATIVE_VOLUME_SHARED");
        verify(api, never()).stop(any(), anyString(), anyBoolean());
    }

    @Test void refusesAmbientDatabaseTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try { assertThatThrownBy(this::freeze).hasMessageContaining("outside database transactions"); }
        finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
    }

    @Test void unsupportedProviderIsExplicitAndDoesNoNativeCalls() {
        target.setProviderType(DeploymentProviderType.RAILWAY_API);
        assertThatThrownBy(this::freeze).hasMessageContaining("NATIVE_PROVIDER_UNSUPPORTED");
        verifyNoInteractions(api);
    }

    @Test void ownsManagedInboxApplicationAndDatabaseInNativeOrdering() {
        add("runtime", "APPLICATION");
        addManagedInbox("inbox", ManagedDependencySpec.Contract.BASIC_INBOX_KEYS_V1);
        var snapshot = freeze();
        for (int index = 0; index < 3; index++) adapter.stop(snapshot, journal);
        assertThat(snapshot.graphs()).hasSize(1);
        assertThat(effects).containsExactly("STOP inbox", "STOP runtime", "STOP inbox-db");
        assertThat(adapter.verifyStopped(snapshot).complete()).isTrue();
    }

    void addManagedInbox(String slot, ManagedDependencySpec.Contract contract) {
        var graph = new ManagedDependencyGraphEntity(); graph.setId("graph"); graph.setDeploymentId("dep"); graph.setTargetProfileId("target");
        graph.setCustomerId("customer"); graph.setTenantId("tenant"); graph.setReleaseId("release"); graph.setVersionId("version");
        var base = contract == ManagedDependencySpec.Contract.HTTP_SERVICE ? ManagedDependencyFixtures.http(slot, true) : ManagedDependencyFixtures.productionInbox();
        var environment = new TreeMap<>(base.environment());
        environment.put("LOOMAI_MANAGED_DEPENDENCY_SLOT", new ManagedDependencySpec.Value(ManagedDependencySpec.ValueKind.LITERAL, slot));
        var service = new ManagedDependencySpec.Service(slot, base.contract(), base.image(), base.sourceArtifactId(), base.sourceCommit(), base.port(),
            base.healthPath(), base.capacity(), base.secretPurposes(), environment, base.database(), base.volumes(), base.dependsOn(), base.initializationContract());
        var spec = new ManagedDependencySpec("managed-dependencies/v1", List.of(service));
        graph.setSpecFingerprint(new ManagedDependencySpecValidator(mapper).fingerprint(spec));
        var state = new ManagedDependencyState(); var node = new ManagedDependencyState.Node(); node.slot = slot; node.serviceRef = slot;
        node.applicationUuid = slot; node.databaseUuid = slot + "-db"; state.nodes.add(node);
        when(graphs.find("dep", "target")).thenReturn(Optional.of(graph)); when(graphs.read("graph")).thenReturn(graph); when(graphs.state(graph)).thenReturn(state);
        when(graphs.spec(graph)).thenReturn(spec);
        when(dependencies.ownedDependencyConnection("target", "staging")).thenReturn(connection);
        String marker = "ManagedDependency:graph:" + slot;
        nativeRows.put(slot, raw(slot, marker)); nativeRows.put(slot + "-db", raw(slot + "-db", marker));
        when(dependencies.inspectOwnedLifecycleResources(eq(connection), eq("graph"), eq(slot), eq(slot), any())).thenAnswer(call -> List.of(
            new PlatformManagedProductProvisioningService.OwnedLifecycleResource("DEPENDENCY_APPLICATION", slot, slot, marker, nativeRows.get(slot).path("status").asText(), nativeRows.get(slot), List.of()),
            new PlatformManagedProductProvisioningService.OwnedLifecycleResource("RUNTIME_POSTGRES_DATABASE", slot + "-db", slot + "-db", marker, nativeRows.get(slot + "-db").path("status").asText(), nativeRows.get(slot + "-db"), List.of())));
        when(dependencies.dispatchOwnedLifecycleResource(eq(connection), eq("graph"), eq(slot), eq(slot), any(), anyString(), eq("STOP"), eq(false)))
            .thenAnswer(call -> stopped(call.getArgument(5)));
    }

    NativeSnapshot freeze() { return adapter.freeze("dep", "target", "release"); }
    DeploymentProviderResourceHandleEntity add(String id, String kind) {
        var handle = new DeploymentProviderResourceHandleEntity(); handle.setId(id); handle.setDeploymentId("dep"); handle.setTargetProfileId("target"); handle.setReleaseId("release");
        handle.setResourceKind(kind); handle.setProviderType(DeploymentProviderType.COOLIFY); handle.setProviderResourceUuid(id); handle.setProviderProjectUuid("project");
        handle.setProviderEnvironmentUuid("env"); handle.setProviderServerUuid("server"); handle.setCreatedAt(Instant.parse("2026-10-25T00:00:00Z")); handle.setStatus("ACTIVE");
        handle.setMetadataJson("{\"applicationName\":\"" + id + "\",\"databaseName\":\"" + id + "\",\"ownershipReceiptId\":\"receipt-" + id + "\"}"); rows.add(handle);
        nativeRows.put(id, raw(id, kind.equals("RUNTIME_POSTGRES_DATABASE") ? "Durable runtime chat database for AI Fabric deployment dep" : "Managed by AI Fabric deployment dep"));
        return handle;
    }
    ObjectNode raw(String id, String marker) {
        return mapper.createObjectNode().put("uuid", id).put("name", id).put("description", marker).put("created_at", "2026-10-25")
            .put("server_uuid", "server").put("destination_uuid", "destination").put("project_uuid", "project").put("environment_uuid", "env").put("status", "running:healthy");
    }
    void volume(String owner, String id, String name) {
        storages.put(owner, List.of(new CoolifyApplicationStorageSummary(id, "persistent", name, "/app/data", null, false,
            mapper.createObjectNode().put("uuid", id).put("name", name).put("mount_path", "/app/data")))); presentVolumes.add(name);
    }
    CoolifyActionResponse stopped(String id) { effects.add("STOP " + id); nativeRows.get(id).put("status", "exited"); return new CoolifyActionResponse("queued", null, null); }
    CoolifyActionResponse started(String id) { effects.add("START " + id); nativeRows.get(id).put("status", "running:healthy"); return new CoolifyActionResponse("queued", "job-" + id, null); }
    CoolifyActionResponse deleted(String id) { effects.add("DELETE " + id); nativeRows.remove(id); return new CoolifyActionResponse("queued", null, null); }

    static class Journal implements Checkpoint {
        final Set<String> intents = new HashSet<>(); final Set<String> complete = new HashSet<>(); final Map<String, String> jobs = new HashMap<>();
        public boolean beforeEffect(String id) { return intents.add(id); }
        public void completed(String id) { assertThat(intents).contains(id); complete.add(id); }
        public boolean isComplete(String id) { return complete.contains(id); }
        public void guard() { }
        public void dispatched(String id, String job) { jobs.put(id, job); }
        public String operationId(String id) { return jobs.get(id); }
    }
}
