package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferEntitlementService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerSelfServiceOperationBoundary;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpec;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencyState;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpecValidator;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.generation.DeploymentRuntimeGenerationService;
import com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationTransitionContracts;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.service.CoolifyDeploymentProvider;
import com.ai.fabric.platform.backend.deployment.service.DeploymentReleaseExecutionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Actual PostgreSQL ledger/review transactions; provider and authenticated-runtime reads are mocked, never hosted. */
@Testcontainers
class ReapplyBeforeRuntimeStopPostgresTest extends AdmissionFixture {
    @Container static final PostgreSQLContainer<?> DATABASE = new PostgreSQLContainer<>("postgres:16-alpine");
    private String databaseName;
    private Reservation failed;
    private FailedApplyRetryService api;
    private CoolifyDeploymentProvider provider;
    private DeploymentRuntimeGenerationService generations;
    private DeploymentReleaseExecutionService execution;

    @BeforeAll static void template() {
        var admin = new JdbcTemplate(new DriverManagerDataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword()));
        admin.execute("CREATE DATABASE reapply_template");
        Flyway.configure().dataSource(url("reapply_template"), DATABASE.getUsername(), DATABASE.getPassword())
            .locations("classpath:db/migration").target("207").load().migrate();
    }
    private static String url(String name) { return "jdbc:postgresql://" + DATABASE.getHost() + ":" + DATABASE.getMappedPort(5432) + "/" + name; }

    @Override @BeforeEach void database() {
        databaseName = "reapply_" + UUID.randomUUID().toString().replace("-", "");
        new JdbcTemplate(new DriverManagerDataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword()))
            .execute("CREATE DATABASE " + databaseName + " TEMPLATE reapply_template");
        dataSource = new DriverManagerDataSource(url(databaseName), DATABASE.getUsername(), DATABASE.getPassword());
        mapper = new ObjectMapper().registerModule(new JavaTimeModule()); json = new AdmissionJson(mapper); jdbc = new JdbcTemplate(dataSource);
        store = new AdmissionStore(jdbc, json, new DataSourceTransactionManager(dataSource), Clock.fixed(NOW, ZoneOffset.UTC));
        seed(); wire();
    }
    @AfterEach void close() {
        if (databaseName != null) new JdbcTemplate(new DriverManagerDataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword()))
            .execute("DROP DATABASE " + databaseName + " WITH (FORCE)");
    }

    @Test void ownerPlanRetryAndReplayRetainOldActiveScopeFullHoldsAndAllFundingHistory() {
        var before = preserved(); var prior = row("platform_deployment_releases", "prior-release");
        String failedHistory = json.write(jdbc.queryForMap("SELECT * FROM platform_deployment_releases WHERE id='failed-release'"));
        var plan = plan();
        assertThat(preserved()).isEqualTo(before);
        assertThat(plan.boundary()).isEqualTo(FailedApplyRetryContracts.REAPPLY_BOUNDARY);
        assertThat(json.write(plan)).doesNotContain("inventory", "payload_json", "priorRuntimeBaseUrl");
        var receipt = api.retry("offer", "deployment", failed.id(), plan.command());
        assertThat(api.retry("offer", "deployment", failed.id(), plan.command())).isEqualTo(receipt);
        verify(execution, times(1)).executeApply("deployment", "version", "failed-release");
        verify(provider, never()).failedApplyRetryReadback(anyString(), anyString(), anyString(), any(), any());
        verify(generations, times(2)).requireActiveForReapply(any(), any(), eq("prior-release"), eq("https://runtime.example"));
        assertThat(preserved()).isEqualTo(before);
        assertThat(row("platform_deployment_releases", "prior-release")).isEqualTo(prior);
        assertThat(store.releaseBinding("prior-release").state()).isEqualTo("ACTIVE");
        assertThat(store.releaseBinding("failed-release").state()).isEqualTo("RETRY_QUEUED");
        var current = store.require(failed.id());
        assertThat(current.state()).isEqualTo(State.RESERVED); assertThat(current.generation()).isEqualTo(failed.generation());
        assertThat(current.chargedDemand()).isEqualTo(failed.chargedDemand()); assertThat(current.expiresAt()).isEqualTo(failed.expiresAt());
        assertThat(store.held()).hasSize(1); assertThat(receipt.financialMutationPerformed()).isFalse();
        assertThat(receipt.retainedAllocationIds()).containsExactly("wallet");
        assertThat(jdbc.queryForObject("SELECT active_version_id FROM platform_deployments WHERE id='deployment'", String.class)).isEqualTo("prior-version");
        String review = jdbc.queryForObject("SELECT payload_json FROM deployment_admission_reviews WHERE kind='NATIVE_RETRY'", String.class);
        var durable = json.read(review, FailedApplyRetryContracts.Receipt.class);
        assertThat(json.read(durable.failedAttempt().failedReleaseJson(), com.fasterxml.jackson.databind.JsonNode.class))
            .isEqualTo(json.read(failedHistory, com.fasterxml.jackson.databind.JsonNode.class));
        assertThat(durable.providerReadback().inventory()).containsKey("RUNTIME_GENERATION");
        assertThatThrownBy(() -> api.retry("offer", "deployment", failed.id(), new FailedApplyRetryContracts.Command(
            plan.command().fence(), "failed-release", plan.command().operationKey(), "changed", true, plan.command().proofFingerprint())))
            .hasMessageContaining("OPERATION_REUSED");
    }

    @Test void coldMaintenanceEligibilityPinsPriorRuntimeAndPreservesFundingWithoutPretendingItDrained() {
        prepareColdMaintenance();
        var before = allState();
        var fingerprints = new TreeMap<String, String>();
        var prior = coldEligibility(fingerprints, coldRequest(true));
        assertThat(prior.releaseId()).isEqualTo("prior-release");
        assertThat(prior.versionId()).isEqualTo("prior-version");
        assertThat(fingerprints).containsKeys("coldPriorRuntime", "coldPriorAuthority", "coldConsumers", "coldInstallations");
        assertThat(json.read(fingerprints.get("coldPriorRuntime"), RuntimeArtifactRepairContracts.ColdPriorRuntime.class)).isEqualTo(prior);
        assertThat(allState()).isEqualTo(before);
        assertThat(store.require(failed.id()).chargedDemand()).isEqualTo(failed.chargedDemand());
        assertThat(jdbc.queryForObject("SELECT payload_json FROM provider_usage_allocations WHERE approval_id='wallet'", String.class))
            .contains("prior-version");
        verifyNoInteractions(provider, generations, execution);
    }

    @ParameterizedTest @ValueSource(strings = {"activatedApp", "foreignConsumer", "wrongGraphOperation", "missingConsent"})
    void coldMaintenanceEligibilityRejectsLiveOrUnprovenScopeWithoutChangingHistory(String change) {
        prepareColdMaintenance();
        switch (change) {
            case "activatedApp" -> jdbc.update("UPDATE ai_workspace_installations SET status='ACTIVE',activated_at=? WHERE id='workspace'", Timestamp.from(NOW));
            case "foreignConsumer" -> jdbc.update("UPDATE platform_consumers SET bound_target_profile_id='foreign-target' WHERE id='consumer'");
            case "wrongGraphOperation" -> changeGraph(state -> state.runtimeGenerationOperationKey = "foreign-operation");
            case "missingConsent" -> { }
            default -> throw new AssertionError(change);
        }
        var before = allState();
        assertThatThrownBy(() -> coldEligibility(new TreeMap<>(), coldRequest(!"missingConsent".equals(change))))
            .isInstanceOf(AdmissionFailure.class);
        assertThat(allState()).isEqualTo(before);
        verifyNoInteractions(provider, generations, execution);
    }

    private RuntimeArtifactRepairContracts.ColdPriorRuntime coldEligibility(Map<String, String> fingerprints,
        RuntimeArtifactRepairContracts.Request request) {
        return new TransactionTemplate(new DataSourceTransactionManager(dataSource)).execute(tx -> {
            var eligibility = new RuntimeArtifactRepairColdMaintenanceEligibility(jdbc, json, new ManagedDependencySpecValidator(mapper));
            var prior = eligibility.prior(failed,
                jdbc.queryForMap("SELECT * FROM platform_deployments WHERE id='deployment'"),
                jdbc.queryForMap("SELECT * FROM platform_deployment_releases WHERE id='failed-release'"), request, fingerprints);
            var graph = jdbc.queryForMap("SELECT * FROM deployment_managed_dependency_graphs WHERE id='graph'");
            eligibility.graph(graph, json.read((String) graph.get("state_json"), ManagedDependencyState.class), prior, "failed-release");
            return prior;
        });
    }

    @Test void verifiedColdMaintenanceContinuesExactCandidateWithoutRequiringOldGenerationToBeActive() {
        var proof = prepareVerifiedColdMaintenance();
        var before = preserved();
        var plan = plan();
        assertThat(plan.boundary()).isEqualTo(FailedApplyRetryContracts.COLD_MAINTENANCE_BOUNDARY);
        var receipt = api.retry("offer", "deployment", failed.id(), plan.command());
        assertThat(receipt.financialMutationPerformed()).isFalse();
        assertThat(preserved()).isEqualTo(before);
        assertThat(api.retry("offer", "deployment", failed.id(), plan.command())).isEqualTo(receipt);
        verify(provider, times(2)).coldMaintenanceReapplyReadback(proof);
        verify(provider, never()).reapplyBeforeRuntimeStopReadback(anyString(), anyString(), anyString(), anyString(), any(), any(), any());
        verify(generations, never()).requireActiveForReapply(any(), any(), anyString(), anyString());
        verify(generations, times(2)).requireActiveAfterColdMaintenance(any(), any(), eq("https://runtime.example"));
        verify(execution, times(1)).executeApply("deployment", "version", "failed-release");
        assertThat(store.require(failed.id()).generation()).isEqualTo(failed.generation());
        assertThat(store.require(failed.id()).chargedDemand()).isEqualTo(failed.chargedDemand());
        var details = json.read(jdbc.queryForObject("SELECT provisioning_details_json FROM platform_deployment_releases WHERE id='failed-release'", String.class),
            com.fasterxml.jackson.databind.JsonNode.class);
        assertThat(details.path("retryHistory")).hasSize(2);
        assertThat(details.path("retryHistory").get(0).path("errorMessage").asText()).isEqualTo("original-first-attempt");
    }

    @ParameterizedTest @ValueSource(strings = {"epoch", "checkpointReceipt", "dependencyDrift"})
    void coldContinuationRejectsChangedProofOrGenerationWithoutDispatch(String change) {
        prepareVerifiedColdMaintenance();
        if (change.equals("epoch")) {
            when(generations.requireActiveAfterColdMaintenance(any(), any(), anyString())).thenReturn(
                new RuntimeGenerationTransitionContracts.Readback(new RuntimeGenerationTransitionContracts.Target("deployment", TARGET, "failed-release"),
                    "tenant", RuntimeGenerationTransitionContracts.State.ACTIVE, 9, null, null, null, false, false, NOW));
        } else if (change.equals("checkpointReceipt")) {
            jdbc.update("UPDATE platform_deployment_releases SET source_artifact_id='different' WHERE id='failed-release'");
        } else changeGraph(state -> state.runtimeGenerationState = "RESUMED");
        var before = allState();
        assertThatThrownBy(this::plan).isInstanceOf(AdmissionFailure.class);
        assertThat(allState()).isEqualTo(before);
        verifyNoInteractions(execution);
    }

    private RuntimeArtifactRepairContracts.VerifiedRepair prepareVerifiedColdMaintenance() {
        prepareColdMaintenance();
        var failedDetails = progress(); failedDetails.putArray("retryHistory").addObject().put("errorMessage", "original-first-attempt");
        jdbc.update("UPDATE platform_deployment_releases SET provisioning_details_json=? WHERE id='failed-release'", json.write(failedDetails));
        var previous = new RuntimeGenerationTransitionContracts.Readback(
            new RuntimeGenerationTransitionContracts.Target("deployment", TARGET, "prior-release"), "tenant",
            RuntimeGenerationTransitionContracts.State.DRAINING, 1, "replace:failed-release", "f".repeat(64), NOW, false, false, NOW);
        var active = new RuntimeGenerationTransitionContracts.Readback(
            new RuntimeGenerationTransitionContracts.Target("deployment", TARGET, "failed-release"), "tenant",
            RuntimeGenerationTransitionContracts.State.ACTIVE, 2, null, null, null, false, false, NOW);
        var zero = new RuntimeGenerationTransitionContracts.Metric(0L, true, List.of());
        var snapshot = new RuntimeGenerationTransitionContracts.Snapshot("actual", "actual-inventory", previous, NOW,
            zero, zero, zero, zero, 0, false, false, false, false, false, "e".repeat(64), List.of(), List.of("UNSUPPORTED_FENCE"));
        var cold = new RuntimeGenerationTransitionContracts.ColdMaintenanceTransitionResult(
            new RuntimeGenerationTransitionContracts.GenerationTransitionResult(previous, active, "physical-stop", NOW, false), snapshot,
            "PHYSICAL_STOP_COLD_MAINTENANCE_V1", "owner-approved", true);
        var scope = new RuntimeArtifactRepairContracts.Scope("customer", "tenant", "deployment", TARGET, "failed-release", "version", "version-hash",
            "c".repeat(64), failed.id(), failed.generation(), "https://runtime.example", "native-APPLICATION", "native-CONNECTOR_APPLICATION",
            "native-RUNTIME_POSTGRES_DATABASE", "prior-release");
        var original = new RuntimeArtifactRepairContracts.ArtifactPin("original-artifact", "original", "runtime", "sha256:" + "a".repeat(64), "a".repeat(40), "old-build", "staging", "manifest");
        var replacement = new RuntimeArtifactRepairContracts.ArtifactPin("replacement", "new", "runtime", "sha256:" + "b".repeat(64), "b".repeat(40), "new-build", "staging", "manifest2");
        var graph = jdbc.queryForMap("SELECT * FROM deployment_managed_dependency_graphs WHERE id='graph'");
        var proof = new RuntimeArtifactRepairContracts.VerifiedRepair("cold-plan", "plan-hash", scope, original, replacement,
            "native-APPLICATION", "cold-runtime-job", NOW, NOW, null, null, Map.of(),
            Map.of("coldMaintenanceReceipt", json.write(cold), "dependencyGraph", json.fingerprint(graph)), NOW);
        var checkpoint = new com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationCheckpointService.Checkpoint(
            com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationCheckpointService.Phase.COLD_ADOPTED,
            new com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationCheckpointService.PriorRuntime("prior-release", "native-APPLICATION", "https://runtime.example"),
            null, "e".repeat(64), "physical-stop", NOW, cold);
        jdbc.update("UPDATE platform_deployment_releases SET runtime_generation_transition_state='COLD_ADOPTED',runtime_generation_transition_json=?,runtime_generation_transition_fingerprint=? WHERE id='failed-release'",
            json.write(checkpoint), "e".repeat(64));
        var repairs = mock(RuntimeArtifactRepairJournal.class);
        when(repairs.verifiedRepair("failed-release")).thenReturn(proof);
        var checkpoints = mock(com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationCheckpointService.class);
        when(checkpoints.read("failed-release")).thenReturn(Optional.of(checkpoint));
        store.setColdMaintenanceEvidence(repairs, checkpoints);
        when(provider.coldMaintenanceReapplyReadback(proof)).thenReturn(nativeReadback(NOW));
        when(generations.requireActiveAfterColdMaintenance(any(), any(), anyString())).thenReturn(active);
        return proof;
    }

    private RuntimeArtifactRepairContracts.Request coldRequest(boolean consent) {
        return new RuntimeArtifactRepairContracts.Request(failed.fence(), "replacement", "offer", 1,
            "cold-maintenance", "Reviewed downtime for never-activated staging workspace", true,
            RuntimeArtifactRepairContracts.Mode.COLD_MAINTENANCE_REAPPLY,
            new RuntimeArtifactRepairContracts.ColdMaintenanceAcknowledgement("prior-release", "owner-downtime-approval", consent));
    }

    private void prepareColdMaintenance() {
        jdbc.update("UPDATE platform_deployment_releases SET source_artifact_id='original-artifact',runtime_authority_fingerprint=? WHERE id='prior-release'", "a".repeat(64));
        jdbc.update("UPDATE platform_deployment_releases SET source_artifact_id='original-artifact',error_message='RUNTIME_GENERATION_DRAIN_INCOMPLETE' WHERE id='failed-release'");
        jdbc.update("UPDATE platform_deployment_versions SET behavior_config_json='{\"type\":\"CONVERSATIONAL\"}' WHERE deployment_id='deployment'");
        jdbc.update("INSERT INTO platform_runtime_authority_snapshots(id,deployment_id,target_profile_id,version_id,release_id,fingerprint,binding_json,created_at) VALUES ('prior-authority','deployment',?,'prior-version','prior-release',?,'{}',?)",
            TARGET, "a".repeat(64), Timestamp.from(NOW));
        jdbc.update("INSERT INTO ai_workspace_installations(id,installation_id,customer_id,consumer_entity_id,display_name,status,experience_pack_code,experience_pack_version,connection_mode,connection_profile_code,connection_profile_version,connection_configuration_json,allowed_origins_json,configuration_json,created_at,updated_at) VALUES ('workspace','workspace-public','customer','consumer','Fixture','DRAFT','generic','1','PUBLIC_RUNTIME_ANONYMOUS','public','1','{}','[]','{}',?,?)",
            Timestamp.from(NOW), Timestamp.from(NOW));
        changeGraph(state -> {
            state.nodes.getFirst().provisionedConfigurationRevision = state.configurationRevision;
            state.nodes.getFirst().status = "PROVISIONED";
            state.runtimeGenerationState = "QUIESCED";
            state.runtimeGenerationStoppedSlots.add("inbox");
            state.runtimeGenerationOperationKey = new ManagedDependencySpecValidator(mapper).fingerprint(List.of(
                "RUNTIME_GENERATION", "graph", TARGET, "prior-release", "failed-release", "d".repeat(64), "c".repeat(64)));
        });
    }

    @ParameterizedTest @ValueSource(strings = {"checkpoint", "inboxGeneration", "ownership", "consumer", "allowance", "laterStep", "busyGraph"})
    void deniesUnprovenBoundaryBeforeAnyRemoteReadOrMutation(String change) {
        switch (change) {
            case "checkpoint" -> jdbc.update("UPDATE platform_deployment_releases SET runtime_generation_transition_state='FENCED',runtime_generation_transition_json='{}' WHERE id='failed-release'");
            case "inboxGeneration" -> changeGraph(state -> state.runtimeGenerationOperationKey = "begun");
            case "ownership" -> jdbc.update("UPDATE platform_managed_resource_ownership SET customer_id='foreign' WHERE resource_kind='DEPENDENCY_APPLICATION'");
            case "consumer" -> jdbc.update("UPDATE platform_consumers SET bound_release_id='failed-release' WHERE id='consumer'");
            case "allowance" -> jdbc.update("UPDATE provider_usage_allocations SET initialization_state='INITIALIZED',activated_at=NULL WHERE approval_id='wallet'");
            case "laterStep" -> {
                var details = progress(); details.withObject("/progress").withArray("steps").addObject()
                    .put("key", "stop_coolify_runtime_for_generation_transition").put("status", "COMPLETED");
                jdbc.update("UPDATE platform_deployment_releases SET provisioning_details_json=? WHERE id='failed-release'", json.write(details));
            }
            case "busyGraph" -> jdbc.update("UPDATE deployment_managed_dependency_graphs SET worker_token='other' WHERE id='graph'");
            default -> throw new AssertionError(change);
        }
        var before = allState();
        assertThatThrownBy(this::plan).isInstanceOf(AdmissionFailure.class);
        assertThat(allState()).isEqualTo(before); verifyNoInteractions(provider, generations, execution);
    }

    @Test void deniesForeignOwnerAndChangedOrStaleRemoteProofWithoutReleasingCapacity() {
        var plan = plan(); var before = allState();
        when(provider.reapplyBeforeRuntimeStopReadback(anyString(), anyString(), anyString(), anyString(), any(), any(), any()))
            .thenReturn(new FailedApplyRetryContracts.NativeReadback(NOW, Map.of("APPLICATION", "changed", "CONNECTOR_APPLICATION", "connector", "RUNTIME_POSTGRES_DATABASE", "postgres")));
        assertThatThrownBy(() -> api.retry("offer", "deployment", failed.id(), plan.command())).hasMessageContaining("PROOF_CHANGED");
        assertThat(allState()).isEqualTo(before);
        when(provider.reapplyBeforeRuntimeStopReadback(anyString(), anyString(), anyString(), anyString(), any(), any(), any()))
            .thenReturn(nativeReadback(NOW.minusSeconds(61)));
        assertThatThrownBy(() -> api.retry("offer", "deployment", failed.id(), plan.command())).hasMessageContaining("FRESH_READBACK_REQUIRED");
        assertThat(allState()).isEqualTo(before);
        assertThatThrownBy(() -> store.failedApplyRetryCandidate("foreign", "deployment", failed.id(), plan.command(), r -> { }))
            .isInstanceOf(AdmissionFailure.class);
        assertThat(allState()).isEqualTo(before); verifyNoInteractions(execution);
    }

    @Test void deniesRevokedOrForeignOldGenerationBeforeQueueing() {
        var before = allState();
        when(generations.requireActiveForReapply(any(), any(), anyString(), anyString())).thenReturn(
            new RuntimeGenerationTransitionContracts.Readback(new RuntimeGenerationTransitionContracts.Target("deployment", TARGET, "foreign"),
                "tenant", RuntimeGenerationTransitionContracts.State.ACTIVE, 1, null, null, null, false, false, NOW));
        assertThatThrownBy(this::plan).hasMessageContaining("PREVIOUS_GENERATION_REQUIRED");
        assertThat(allState()).isEqualTo(before); verifyNoInteractions(execution);
    }

    @Test void unprovenGenerationReturnsExplicitConflictWithoutQueueingOrChangingHistory() {
        var before = allState();
        when(generations.requireActiveForReapply(any(), any(), anyString(), anyString()))
            .thenThrow(new IllegalStateException("RUNTIME_GENERATION_READBACK_INVALID"));
        assertThatThrownBy(this::plan).isInstanceOfSatisfying(org.springframework.web.server.ResponseStatusException.class,
            error -> {
                assertThat(error.getStatusCode().value()).isEqualTo(409);
                assertThat(error.getReason()).isEqualTo("FAILED_APPLY_RETRY_GENERATION_READBACK_UNPROVEN");
                assertThat(error.getCause()).hasMessage("RUNTIME_GENERATION_READBACK_INVALID");
            });
        assertThat(allState()).isEqualTo(before); verifyNoInteractions(execution);
    }

    @Test void concurrentExecuteDispatchesOnlyTheTransactionThatActuallyQueued() throws Exception {
        var plan = plan(); var arrived = new CountDownLatch(2); var proceed = new CountDownLatch(1);
        when(generations.requireActiveForReapply(any(), any(), anyString(), anyString())).thenAnswer(call -> {
            arrived.countDown(); await(proceed); return generation();
        });
        try (var pool = Executors.newFixedThreadPool(2)) {
            var one = pool.submit(() -> api.retry("offer", "deployment", failed.id(), plan.command()));
            var two = pool.submit(() -> api.retry("offer", "deployment", failed.id(), plan.command()));
            try { assertThat(arrived.await(10, TimeUnit.SECONDS)).isTrue(); } finally { proceed.countDown(); }
            assertThat(one.get(15, TimeUnit.SECONDS)).isEqualTo(two.get(15, TimeUnit.SECONDS));
        }
        verify(execution, times(1)).executeApply("deployment", "version", "failed-release");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM deployment_admission_reviews WHERE kind='NATIVE_RETRY'", Long.class)).isEqualTo(1L);
        assertThat(store.releaseBinding("prior-release").state()).isEqualTo("ACTIVE");
    }

    @Test void pausedFailedObserverCannotUndoRetryAndLocalDriftInvalidatesPlan() throws Exception {
        var plan = plan(); var binding = store.releaseBinding("failed-release");
        jdbc.update("UPDATE platform_consumers SET binding_revision=binding_revision+1 WHERE id='consumer'");
        var before = allState();
        assertThatThrownBy(() -> api.retry("offer", "deployment", failed.id(), plan.command())).hasMessageContaining("PROOF_CHANGED");
        assertThat(allState()).isEqualTo(before);
        var fresh = plan(); var checked = new CountDownLatch(1); var resume = new CountDownLatch(1);
        try (var pool = Executors.newSingleThreadExecutor()) {
            var observer = pool.submit(() -> { checked.countDown(); await(resume);
                store.observeFailedRelease(binding, () -> release("failed-release"), "observer", NOW); });
            assertThat(checked.await(10, TimeUnit.SECONDS)).isTrue();
            try { api.retry("offer", "deployment", failed.id(), fresh.command()); } finally { resume.countDown(); }
            observer.get(10, TimeUnit.SECONDS);
        }
        assertThat(store.require(failed.id()).state()).isEqualTo(State.RESERVED);
        assertThat(store.releaseBinding("failed-release").state()).isEqualTo("RETRY_QUEUED");
        assertThat(store.releaseBinding("prior-release").state()).isEqualTo("ACTIVE");
    }

    private FailedApplyRetryContracts.Plan plan() {
        return api.plan("offer", "deployment", failed.id(), new FailedApplyRetryContracts.PlanRequest("failed-release", "reviewed-retry", "Retry owned reapply before runtime stop", true));
    }
    private void wire() {
        var entitlements = mock(CustomerOfferEntitlementService.class);
        when(entitlements.requireCustomerActor()).thenReturn(new CustomerOfferEntitlementService.CustomerActor("customer", "owner"));
        var boundary = mock(CustomerSelfServiceOperationBoundary.class);
        when(boundary.prepareAdmission(any(), eq(TARGET), eq(CustomerOfferContracts.Operation.APPLY), anyString()))
            .thenAnswer(call -> new CustomerOfferContracts.Authorization(scope(call.getArgument(3)), null));
        var deployments = mock(DeploymentRepository.class); when(deployments.findById("deployment")).thenReturn(Optional.of(deployment()));
        var versions = mock(DeploymentVersionRepository.class); when(versions.findById("version")).thenReturn(Optional.of(version("version")));
        var releases = mock(DeploymentReleaseRepository.class); when(releases.findById("failed-release")).thenAnswer(call -> Optional.of(release("failed-release")));
        provider = mock(CoolifyDeploymentProvider.class);
        when(provider.reapplyBeforeRuntimeStopReadback(anyString(), anyString(), anyString(), anyString(), any(), any(), any())).thenReturn(nativeReadback(NOW));
        generations = mock(DeploymentRuntimeGenerationService.class);
        when(generations.requireActiveForReapply(any(), any(), eq("prior-release"), eq("https://runtime.example"))).thenReturn(generation());
        execution = mock(DeploymentReleaseExecutionService.class);
        api = new FailedApplyRetryService(store, entitlements, boundary, deployments, versions, provider, execution, releases, generations);
    }
    private RuntimeGenerationTransitionContracts.Readback generation() {
        return new RuntimeGenerationTransitionContracts.Readback(new RuntimeGenerationTransitionContracts.Target("deployment", TARGET, "prior-release"),
            "tenant", RuntimeGenerationTransitionContracts.State.ACTIVE, 1, null, null, null, false, false, NOW.minusSeconds(100));
    }
    private FailedApplyRetryContracts.NativeReadback nativeReadback(java.time.Instant at) {
        return new FailedApplyRetryContracts.NativeReadback(at, Map.of("APPLICATION", "runtime", "CONNECTOR_APPLICATION", "connector", "RUNTIME_POSTGRES_DATABASE", "postgres"));
    }
    private void seed() {
        Timestamp now = Timestamp.from(NOW);
        jdbc.update("INSERT INTO platform_customers(id,name,slug,status,created_at,updated_at) VALUES ('customer','Fixture','reapply-fixture','ACTIVE',?,?)", now, now);
        jdbc.update("INSERT INTO platform_customer_tenants(id,customer_id,name,slug,status,created_at,updated_at) VALUES ('tenant','customer','Fixture','fixture','ACTIVE',?,?)", now, now);
        jdbc.update("INSERT INTO deployment_target_profiles(id,name,provider_type,environment_name,active,source_strategy,created_at,updated_at) VALUES (?,'Fixture','COOLIFY','staging',true,'IMAGE',?,?)", TARGET, now, now);
        jdbc.update("INSERT INTO platform_deployments(id,name,environment_name,template_id,status,customer_id,tenant_id,created_at,updated_at) VALUES ('deployment','Fixture','staging','fixture','ACTIVE','customer','tenant',?,?)", now, now);
        for (String id : List.of("prior-version", "version")) {
            jdbc.update("INSERT INTO platform_deployment_drafts(id,deployment_id,revision_number,status,actions_config_json,entity_config_json,routing_config_json,provider_config_json,security_config_json,created_at,updated_at) VALUES (?,'deployment',?,'PUBLISHED','{}','{}','{}','{}','{}',?,?)", id + "-draft", id.equals("version") ? 2 : 1, now, now);
            jdbc.update("INSERT INTO platform_deployment_versions(id,deployment_id,source_draft_id,version_label,status,config_hash,reindex_required,actions_config_json,entity_config_json,routing_config_json,provider_config_json,security_config_json,actions_artifact_yaml,entity_artifact_yaml,routing_artifact_yaml,manifest_json,published_at) VALUES (?,'deployment',?,?,'PUBLISHED',?,false,'{}','{}','{}','{}','{}','{}','{}','{}','{}',?)", id, id + "-draft", id, id + "-hash", now);
        }
        approve(3); store.approveManifest(manifestFor("prior-manifest", "prior-version"), "staff", NOW);
        var oldContext = context("prior-manifest");
        var original = store.reserve(oldContext, new Reserve("initial-apply", "prior-manifest", scope("initial-apply")), evidence(oldContext, NOW), "owner", NOW);
        bind("prior-release", "prior-version", NOW.minusSeconds(300));
        store.beginReleaseProvisioning(store.releaseBinding("prior-release"), oldContext, evidence(oldContext, NOW), "worker", NOW);
        var provisioning = store.require(original.id()); store.reconcile(provisioning.fence(), readback(provisioning, NOW, false, 0L, 0L), false, "worker", NOW);
        store.releaseBindingState("prior-release", "ACTIVE");
        jdbc.update("UPDATE platform_deployment_releases SET status='APPLIED_VERIFIED',provisioning_status='ACTIVE',verification_status='PASSED' WHERE id='prior-release'");
        jdbc.update("UPDATE platform_deployments SET active_version_id='prior-version',runtime_base_url='https://runtime.example',connector_base_url='https://connector.example' WHERE id='deployment'");
        store.approveManifest(manifestFor("manifest", "version"), "staff", NOW);
        var candidateContext = store.context(store.require(original.id()), "manifest");
        store.reserve(candidateContext, new Reserve("reapply", "manifest", scope("reapply"), original.id()), evidence(candidateContext, NOW), "owner", NOW);
        bind("failed-release", "version", NOW.minusSeconds(10));
        var committed = store.context(store.require(original.id()));
        store.beginReleaseProvisioning(store.releaseBinding("failed-release"), committed, evidence(committed, NOW), "worker", NOW);
        jdbc.update("UPDATE platform_deployment_releases SET status='FAILED',provisioning_status='FAILED',verification_status='SKIPPED',current_step_key='apply_failed',runtime_authority_fingerprint=?,provisioning_details_json=? WHERE id='failed-release'", "c".repeat(64), json.write(progress()));
        jdbc.update("UPDATE platform_deployments SET status='APPLY_FAILED' WHERE id='deployment'");
        store.observeFailedRelease(store.releaseBinding("failed-release"), () -> release("failed-release"), "observer", NOW);
        failed = store.require(original.id()); assertThat(failed.state()).isEqualTo(State.OUTCOME_UNKNOWN);
        for (String kind : List.of("APPLICATION", "CONNECTOR_APPLICATION", "RUNTIME_POSTGRES_DATABASE")) {
            jdbc.update("INSERT INTO deployment_provider_resource_handles(id,deployment_id,release_id,target_profile_id,provider_type,resource_kind,provider_resource_uuid,status,metadata_json,created_at,updated_at) VALUES (?,'deployment',?,?,'COOLIFY',?,?,'ACTIVE','{}',?,?)", kind, kind.equals("APPLICATION") ? "prior-release" : "failed-release", TARGET, kind, "native-" + kind, now, now);
        }
        jdbc.update("INSERT INTO platform_consumers(id,customer_id,consumer_id,display_name,status,bound_deployment_id,bound_release_id,bound_target_profile_id,created_at,updated_at) VALUES ('consumer','customer','consumer','Fixture','ACTIVE','deployment','prior-release',?,?,?)", TARGET, now, now);
        seedInbox();
        jdbc.update("INSERT INTO provider_usage_grants(id,campaign_id,customer_id,target_profile_id,environment_name,fingerprint,payload_json,allocated_chat_attempts,allocated_total_tokens,allocated_cost_nano_usd,approved_by,approved_role,approved_at) VALUES ('grant','whole-task','customer',?,'staging',?,'{}',25,200000,6000000000,'staff','PLATFORM_ADMIN',?)", TARGET, "f".repeat(64), now);
        jdbc.update("INSERT INTO provider_usage_allocations(approval_id,grant_id,customer_id,deployment_id,target_profile_id,environment_name,approval_fingerprint,runtime_allocation_fingerprint,payload_json,initialization_state,store_uuid,initialization_fingerprint,created_at,initialized_at,activated_at) VALUES ('wallet','grant','customer','deployment',?,'staging',?,?,?,'ACTIVE',?,?,?, ?,?)", TARGET, "a".repeat(64), "b".repeat(64), json.write(Map.of("initialAdmission", Map.of("reservationId", failed.id(), "generation", failed.generation(), "versionId", "prior-version"))), UUID.randomUUID().toString(), "b".repeat(64), now, now, now);
        jdbc.update("INSERT INTO provider_usage_allowance_events(id,event_type,subject_id,grant_id,actor_id,actor_role,payload_fingerprint,created_at) VALUES ('funding','ALLOWANCE_ACTIVE','wallet','grant','owner','CUSTOMER_ADMIN',?,?)", "e".repeat(64), now);
    }
    private void seedInbox() {
        var capacity = new ManagedDependencySpec.Capacity(100, 128, 64, 1);
        var service = new ManagedDependencySpec.Service("inbox", ManagedDependencySpec.Contract.BASIC_INBOX_KEYS_V1,
            "fixture/inbox:1", "artifact", "a".repeat(40), 8090, "/actuator/health", capacity, Set.of("api"), Map.of(),
            new ManagedDependencySpec.Database("postgres:16", "password", capacity), List.of(), List.of(), "basic-inbox-keys/v1");
        String spec = json.write(new ManagedDependencySpec("managed-dependencies/v1", List.of(service)));
        var state = new ManagedDependencyState(); state.configurationRevision = 2;
        var node = new ManagedDependencyState.Node(); node.slot = "inbox"; node.serviceRef = "owned-inbox"; node.accountId = "account";
        node.bindingId = "binding"; node.applicationUuid = "inbox-app"; node.databaseUuid = "inbox-db"; node.baseUrl = "https://inbox.example";
        node.applicationCreateIntent = true; node.databaseCreateIntent = true; node.provisionedConfigurationRevision = 1; state.nodes.add(node);
        var operator = new ManagedDependencyState.Operator(); operator.id = "operator"; operator.slot = "inbox";
        operator.secretName = "OWNED_OPERATOR_KEY"; operator.effectiveAt = NOW.minusSeconds(100); operator.delivered = true; state.operators.add(operator);
        jdbc.update("INSERT INTO deployment_managed_dependency_specs(id,spec_json,fingerprint,reviewed_by,reviewed_at,active,review_reference) VALUES ('spec',?,?,'staff',?,true,'fixture-review')", spec, "d".repeat(64), Timestamp.from(NOW));
        jdbc.update("INSERT INTO deployment_managed_dependency_graphs(id,deployment_id,target_profile_id,customer_id,tenant_id,version_id,release_id,spec_ref,spec_fingerprint,binding_fingerprint,status,step,reservation_ref,spec_json,state_json,created_at,updated_at) VALUES ('graph','deployment',?,'customer','tenant','version','failed-release','spec',?,?,'PROVISIONED','RUNTIME_INITIALIZATION_REQUIRED',?,?,?, ?,?)", TARGET, "d".repeat(64), "c".repeat(64), failed.id(), spec, json.write(state), Timestamp.from(NOW), Timestamp.from(NOW));
        for (String kind : List.of("DEPENDENCY_APPLICATION", "RUNTIME_POSTGRES_DATABASE")) {
            jdbc.update("INSERT INTO platform_managed_resource_ownership(id,location_key,provider,resource_kind,parent_identity,resource_name,customer_id,tenant_id,deployment_id,target_profile_id,state,resource_identity,created_at,updated_at) VALUES (?,?,'coolify',?,'fixture-parent',?,'customer','tenant','deployment',?,'OWNED',?,?,?)", kind, kind, kind, kind.equals("DEPENDENCY_APPLICATION") ? "owned-inbox" : "owned-inbox-db", TARGET, kind.equals("DEPENDENCY_APPLICATION") ? "inbox-app" : "inbox-db", Timestamp.from(NOW), Timestamp.from(NOW));
        }
    }
    private void bind(String id, String version, java.time.Instant createdAt) {
        new TransactionTemplate(new DataSourceTransactionManager(dataSource)).executeWithoutResult(tx -> {
            jdbc.update("INSERT INTO platform_deployment_releases(id,deployment_id,deployment_version_id,target_profile_id,provider_type,status,verification_status,provisioning_status,provisioning_target,provisioning_details_json,created_at,applied_at,updated_at) VALUES (?,'deployment',? ,?,'COOLIFY','APPLY_REQUESTED','PENDING','QUEUED','COOLIFY','{}',?,?,?)", id, version, TARGET, Timestamp.from(createdAt), Timestamp.from(createdAt), Timestamp.from(NOW));
            store.bindRelease(deployment(), version(version), release(id));
        });
    }
    private Manifest manifestFor(String id, String version) {
        var base = manifest(id, "deployment");
        return new Manifest(id, "deployment", version, version + "-hash", TARGET, "binding-fingerprint", base.topologyReference(), base.demand(), base.prerequisiteReferences(), base.customerPrerequisiteReferences());
    }
    private CustomerOfferContracts.AdmissionContext scope(String key) {
        return new CustomerOfferContracts.AdmissionContext("customer", "tenant", "deployment", null, TARGET,
            CustomerOfferContracts.TargetIntent.CUSTOMER_STAGING, "offer", "offer-fingerprint", 1, "placement-proof", CustomerOfferContracts.Operation.APPLY, key,
            new CustomerOfferContracts.ReviewedLimits("measurement-proof", 1, 2, 2, 2, 2, 2, 2, 2, 2, 1, 1, 1), new CustomerOfferContracts.ReviewedTopology(1, 1, 1, 1, 1, 1, 1, 1));
    }
    private ObjectNode progress() {
        var details = mapper.createObjectNode(); var progress = details.putObject("progress");
        progress.put("currentStepKey", "apply_failed").put("currentStepStatus", "FAILED");
        var steps = progress.putArray("steps");
        for (String key : List.of("queue_release", "preflight_verification", "prepare_apply", "coolify_preflight", "ensure_deployment_execution_secrets",
            "resolve_coolify_source", "resolve_coolify_customer_scope", "reconcile_coolify_runtime_application", "record_coolify_runtime_handle_provisional",
            "reconcile_coolify_document_source_storage", "reconcile_coolify_connector_application", "record_coolify_connector_handle_provisional",
            "configure_coolify_runtime_private_network", "configure_coolify_connector_private_network", "reconcile_coolify_runtime_database",
            "record_coolify_runtime_database_handle_provisional", "configure_coolify_runtime_environment", "configure_coolify_connector_environment",
            "stop_coolify_connector_for_generation_transition", "apply_failed")) {
            steps.addObject().put("key", key).put("status", switch (key) { case "queue_release" -> "QUEUED"; case "prepare_apply" -> "RUNNING"; case "apply_failed" -> "FAILED"; default -> "COMPLETED"; });
        }
        return details;
    }
    private DeploymentEntity deployment() { var d = new DeploymentEntity(); d.setId("deployment"); d.setCustomerId("customer"); d.setTenantId("tenant"); return d; }
    private DeploymentVersionEntity version(String id) { var v = new DeploymentVersionEntity(); v.setId(id); v.setDeploymentId("deployment"); v.setStatus("PUBLISHED"); v.setConfigHash(id + "-hash"); return v; }
    private DeploymentReleaseEntity release(String id) {
        return jdbc.query("SELECT * FROM platform_deployment_releases WHERE id=?", (rs, n) -> {
            var r = new DeploymentReleaseEntity(); r.setId(id); r.setDeploymentId("deployment"); r.setTargetProfileId(TARGET);
            r.setDeploymentVersionId(rs.getString("deployment_version_id")); r.setStatus(rs.getString("status")); r.setProvisioningStatus(rs.getString("provisioning_status")); return r;
        }, id).getFirst();
    }
    private void changeGraph(java.util.function.Consumer<ManagedDependencyState> change) {
        var state = json.read(jdbc.queryForObject("SELECT state_json FROM deployment_managed_dependency_graphs WHERE id='graph'", String.class), ManagedDependencyState.class);
        change.accept(state); jdbc.update("UPDATE deployment_managed_dependency_graphs SET state_json=? WHERE id='graph'", json.write(state));
    }
    private Map<String, String> preserved() {
        var result = new TreeMap<String, String>();
        for (String table : List.of("provider_usage_grants", "provider_usage_allocations", "provider_usage_allowance_events", "provider_usage_validity_reviews", "provider_usage_validity_events", "deployment_managed_dependency_graphs", "deployment_managed_dependency_specs", "platform_managed_resource_ownership", "deployment_provider_resource_handles", "platform_deployment_versions", "platform_consumers", "platform_consumer_binding_history", "ai_workspace_installations")) result.put(table, rows(table));
        return result;
    }
    private Map<String, String> allState() {
        var result = new TreeMap<>(preserved());
        for (String table : List.of("platform_deployments", "platform_deployment_releases", "deployment_capacity_reservations", "deployment_admission_release_bindings", "deployment_admission_authorizations", "deployment_admission_reviews", "deployment_admission_events")) result.put(table, rows(table)); return result;
    }
    private String rows(String table) { return jdbc.queryForObject("SELECT coalesce(json_agg(row_to_json(t) ORDER BY row_to_json(t)::text),'[]'::json)::text FROM " + table + " t", String.class); }
    private String row(String table, String id) { return jdbc.queryForObject("SELECT row_to_json(t)::text FROM " + table + " t WHERE id=?", String.class, id); }
    private static void await(CountDownLatch latch) { try { if (!latch.await(15, TimeUnit.SECONDS)) throw new AssertionError("timeout"); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); } }
}
