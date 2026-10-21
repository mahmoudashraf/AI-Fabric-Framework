package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencyState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real PostgreSQL admission transactions, reusing the existing cold-reapply fixture; no remote calls. */
@Testcontainers
class PostProvisionRetainedInboxPostgresTest extends AdmissionFixture {
    @Container static final PostgreSQLContainer<?> DATABASE = ReapplyBeforeRuntimeStopPostgresTest.DATABASE;
    private final ReapplyBeforeRuntimeStopPostgresTest cold = new ReapplyBeforeRuntimeStopPostgresTest();
    private Reservation failed;

    @BeforeAll static void template() { ReapplyBeforeRuntimeStopPostgresTest.template(); }

    @Override @BeforeEach void database() {
        cold.database();
        dataSource = cold.dataSource; jdbc = cold.jdbc; mapper = cold.mapper; json = cold.json; store = cold.store;
        RuntimeArtifactRepairContracts.VerifiedRepair proof = ReflectionTestUtils.invokeMethod(cold, "prepareVerifiedColdMaintenance");
        var expanded = (ObjectNode) mapper.valueToTree(proof);
        expanded.withObject("/finalNativeInventory").put("inbox", json.write(Map.of("applicationUuid", "inbox-app", "databaseUuid", "inbox-db")));
        proof = json.read(json.write(expanded), RuntimeArtifactRepairContracts.VerifiedRepair.class);
        RuntimeArtifactRepairJournal repairs = (RuntimeArtifactRepairJournal) ReflectionTestUtils.getField(store, "artifactRepairs");
        when(repairs.verifiedRepair("failed-release")).thenReturn(proof);
        var provider = (com.ai.fabric.platform.backend.deployment.service.CoolifyDeploymentProvider) ReflectionTestUtils.getField(cold, "provider");
        when(provider.coldMaintenanceReapplyReadback(proof)).thenReturn(nativeReadback(NOW));
        jdbc.update("INSERT INTO deployment_admission_reviews(kind,record_key,fingerprint,payload_json,reviewed_by,reviewed_at) VALUES ('ART_VERIFIED','failed-release',?,?,'staff',?)",
            json.fingerprint(proof), json.write(proof), java.sql.Timestamp.from(NOW));
        FailedApplyRetryService retry = (FailedApplyRetryService) ReflectionTestUtils.getField(cold, "api");
        Reservation held = (Reservation) ReflectionTestUtils.getField(cold, "failed");
        var plan = retry.plan("offer", "deployment", held.id(), new FailedApplyRetryContracts.PlanRequest("failed-release", "cold-retry", "Continue verified cold reapply", true));
        retry.retry("offer", "deployment", held.id(), plan.command());
        store.beginReleaseProvisioning(store.releaseBinding("failed-release"), store.context(store.require(held.id())),
            evidence(store.context(store.require(held.id())), NOW), "worker", NOW);
        jdbc.update("UPDATE platform_deployments SET status='APPLY_FAILED',active_version_id='version' WHERE id='deployment'");
        jdbc.update("UPDATE deployment_provider_resource_handles SET release_id='failed-release' WHERE resource_kind='APPLICATION'");
        jdbc.update("UPDATE platform_deployment_releases SET status='FAILED',provisioning_status='FAILED',verification_status='SKIPPED',current_step_key='apply_failed',error_message='409 CONFLICT DEPENDENCY_ADMISSION_INVALID',provisioning_details_json=? WHERE id='failed-release'", json.write(completedNativeProgress()));
        changeGraph(state -> {
            state.runtimeGenerationState = "RESUMED"; state.runtimeGenerationStoppedSlots.clear();
            var node = state.nodes.getFirst(); node.status = "INITIALIZING";
            node.provisionedConfigurationRevision = state.configurationRevision;
            node.secrets.put("api", secretName(node.slot, "api"));
            var owner = state.operators.getFirst(); owner.displayName = "owner";
            owner.secretName = secretName(owner.slot, owner.id + "-" + owner.credentialRevision);
            state.commands.put("release:prior-release:dependency:initial-owner", new ManagedDependencyState.Command(
                json.fingerprint(List.of("ENROLL", owner.slot, owner.displayName)), owner.id, NOW.minusSeconds(100), "system", "SYSTEM"));
            state.commands.put("release:failed-release:dependency:runtime-bound", new ManagedDependencyState.Command(
                json.fingerprint(List.of("RECONCILE", "d".repeat(64), "c".repeat(64), state.configurationRevision)), "graph", NOW, "system", "SYSTEM"));
        });
        jdbc.update("UPDATE deployment_managed_dependency_graphs SET status='BLOCKED',step='INITIALIZE:inbox',failure_code='DEPENDENCY_ADMISSION_INVALID' WHERE id='graph'");
        store.observeFailedRelease(store.releaseBinding("failed-release"), () -> ReflectionTestUtils.invokeMethod(cold, "release", "failed-release"), "observer", NOW);
        failed = store.require(held.id());
        assertThat(failed.state()).isEqualTo(State.OUTCOME_UNKNOWN);
    }

    @AfterEach void close() { cold.close(); }

    @Test void resumesOnlyRemainingSetupPreservingPriorBindingFundingFullDemandAndFailedHistory() {
        var preserved = preserved(); var before = allState();
        String history = jdbc.queryForObject("SELECT provisioning_details_json FROM platform_deployment_releases WHERE id='failed-release'", String.class);
        var inspected = inspect(); var plan = plan(inspected);
        assertThat(allState()).isEqualTo(before);
        assertThat(plan.runtimeRedeployRequired()).isFalse();
        assertThat(plan.retainedAllocationIds()).containsExactly("wallet");
        var result = store.queuePostProvisionResume("customer", "deployment", failed.id(), plan.command(), inspected, nativeReadback(NOW), "owner", r -> { });
        assertThat(result.financialMutationPerformed()).isFalse(); assertThat(result.runtimeRedeployRequired()).isFalse();
        assertThat(store.recordedPostProvisionResume("customer", "deployment", failed.id(), plan.command(), "owner", r -> { })).isEqualTo(result);
        assertThat(preserved()).isEqualTo(preserved);
        assertThat(store.releaseBinding("prior-release").state()).isEqualTo("ACTIVE");
        assertThat(store.releaseBinding("failed-release").state()).isEqualTo("RETRY_QUEUED");
        var current = store.require(failed.id());
        assertThat(current.state()).isEqualTo(State.RESERVED); assertThat(current.revision()).isEqualTo(failed.revision() + 1);
        assertThat(current.generation()).isEqualTo(failed.generation()); assertThat(current.demand()).isEqualTo(failed.demand());
        assertThat(current.chargedDemand()).isEqualTo(failed.chargedDemand()); assertThat(current.expiresAt()).isEqualTo(failed.expiresAt());
        var receipt = json.read(jdbc.queryForObject("SELECT payload_json FROM deployment_admission_reviews WHERE kind='POST_RESUME'", String.class), PostProvisionResumeContracts.Receipt.class);
        assertThat(tree(receipt.failedAttempt().failedReleaseJson()).path("provisioning_details_json").asText()).isEqualTo(history);
        var queued = tree(jdbc.queryForObject("SELECT provisioning_details_json FROM platform_deployment_releases WHERE id='failed-release'", String.class));
        assertThat(queued.path("retryHistory").size()).isEqualTo(tree(history).path("retryHistory").size() + 1);
        assertThat(queued.path("retryHistory").get(queued.path("retryHistory").size() - 1).path("progress")).isEqualTo(tree(history).path("progress"));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void deliveryStateIsRetainedRatherThanReset(boolean delivered) {
        changeGraph(state -> state.operators.getFirst().delivered = delivered);
        var before = preserved(); var inspected = inspect(); var plan = plan(inspected);
        store.queuePostProvisionResume("customer", "deployment", failed.id(), plan.command(), inspected, nativeReadback(NOW), "owner", r -> { });
        assertThat(preserved()).isEqualTo(before);
    }

    @Test void usesRecordedConfigurationAndActualCredentialRevisionsRatherThanPilotCounters() {
        changeGraph(state -> {
            state.configurationRevision = 7; state.nodes.getFirst().provisionedConfigurationRevision = 7;
            var owner = state.operators.getFirst(); owner.credentialRevision = 3;
            owner.secretName = secretName(owner.slot, owner.id + "-3");
            state.commands.put("release:failed-release:dependency:runtime-bound", new ManagedDependencyState.Command(
                json.fingerprint(List.of("RECONCILE", "d".repeat(64), "c".repeat(64), 7)), "graph", NOW, "system", "SYSTEM"));
        });
        assertThat(inspect().reservation()).isEqualTo(failed);
    }

    @ParameterizedTest @ValueSource(strings = {"scope", "prior-binding", "consumer", "ownership", "resource", "operator", "operator-secret", "operator-revision", "secret", "configuration", "quiesced", "initialized", "effective", "worker", "failure", "native-progress", "checkpoint", "retry-hash", "retry-proof", "retry-summary", "retry-history", "repair", "allocation", "funding"})
    void rejectsUnprovenOrChangedRetainedEvidenceWithoutWriting(String change) {
        switch (change) {
            case "scope" -> jdbc.update("UPDATE deployment_managed_dependency_graphs SET customer_id='foreign' WHERE id='graph'");
            case "prior-binding" -> jdbc.update("UPDATE deployment_admission_release_bindings SET state='SUPERSEDED' WHERE release_id='prior-release'");
            case "consumer" -> jdbc.update("UPDATE platform_consumers SET bound_release_id='failed-release' WHERE id='consumer'");
            case "ownership" -> jdbc.update("UPDATE platform_managed_resource_ownership SET state='DELETE_PENDING' WHERE resource_kind='DEPENDENCY_APPLICATION'");
            case "resource" -> changeGraph(state -> state.nodes.getFirst().applicationUuid = "foreign");
            case "operator" -> changeGraph(state -> state.operators.getFirst().displayName = "foreign");
            case "operator-secret" -> changeGraph(state -> state.operators.getFirst().secretName = "FOREIGN_KEY");
            case "operator-revision" -> changeGraph(state -> state.operators.getFirst().credentialRevision++);
            case "secret" -> changeGraph(state -> state.nodes.getFirst().secrets.put("api", "FOREIGN_KEY"));
            case "configuration" -> changeGraph(state -> { state.configurationRevision++; state.nodes.getFirst().provisionedConfigurationRevision++; });
            case "quiesced" -> changeGraph(state -> state.runtimeGenerationState = "QUIESCED");
            case "initialized" -> changeGraph(state -> state.nodes.getFirst().initialized = true);
            case "effective" -> changeGraph(state -> state.effectiveConfigurationRevision = state.configurationRevision);
            case "worker" -> jdbc.update("UPDATE deployment_managed_dependency_graphs SET worker_token='live-worker' WHERE id='graph'");
            case "failure" -> jdbc.update("UPDATE deployment_managed_dependency_graphs SET failure_code='OTHER_FAILURE' WHERE id='graph'");
            case "native-progress" -> {
                var details = completedNativeProgress(); details.withObject("/progress").withArray("steps").addObject().put("key", "source_sync").put("status", "COMPLETED");
                jdbc.update("UPDATE platform_deployment_releases SET provisioning_details_json=? WHERE id='failed-release'", json.write(details));
            }
            case "checkpoint" -> jdbc.update("UPDATE platform_deployment_releases SET runtime_generation_transition_fingerprint='changed' WHERE id='failed-release'");
            case "retry-hash" -> jdbc.update("UPDATE deployment_admission_reviews SET fingerprint='changed' WHERE kind='NATIVE_RETRY'");
            case "retry-proof", "retry-summary" -> {
                var receipt = (ObjectNode) tree(jdbc.queryForObject("SELECT payload_json FROM deployment_admission_reviews WHERE kind='NATIVE_RETRY'", String.class));
                if ("retry-proof".equals(change)) receipt.withObject("/command").put("proofFingerprint", "0".repeat(64));
                else receipt.withObject("/summary").put("revision", 999);
                var value = json.read(json.write(receipt), FailedApplyRetryContracts.Receipt.class);
                jdbc.update("UPDATE deployment_admission_reviews SET payload_json=?,fingerprint=? WHERE kind='NATIVE_RETRY'", json.write(value), json.fingerprint(value));
            }
            case "retry-history" -> {
                var details = (ObjectNode) tree(jdbc.queryForObject("SELECT provisioning_details_json FROM platform_deployment_releases WHERE id='failed-release'", String.class));
                details.putArray("retryHistory"); jdbc.update("UPDATE platform_deployment_releases SET provisioning_details_json=? WHERE id='failed-release'", json.write(details));
            }
            case "repair" -> jdbc.update("DELETE FROM deployment_admission_reviews WHERE kind='ART_VERIFIED'");
            case "allocation" -> jdbc.update("UPDATE provider_usage_allocations SET store_uuid=? WHERE approval_id='wallet'", java.util.UUID.randomUUID().toString());
            case "funding" -> jdbc.update("UPDATE provider_usage_grants SET allocated_total_tokens=allocated_total_tokens+1 WHERE id='grant'");
            default -> throw new AssertionError(change);
        }
        var before = allState();
        assertThatThrownBy(this::inspect).isInstanceOf(AdmissionFailure.class);
        assertThat(allState()).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings = {"origins", "callbacks", "operator-delivery", "funding-event", "authority"})
    void ownerPlanCannotQueueAfterConfigurationAuthorityOrFundingChanges(String change) {
        var inspected = inspect(); var plan = plan(inspected);
        switch (change) {
            case "origins" -> changeGraph(state -> state.origins = List.of("https://changed.example"));
            case "callbacks" -> changeGraph(state -> state.callbacks = Map.of("inbox", "https://changed.example/callback"));
            case "operator-delivery" -> changeGraph(state -> state.operators.getFirst().delivered = !state.operators.getFirst().delivered);
            case "funding-event" -> jdbc.update("INSERT INTO provider_usage_allowance_events(id,event_type,subject_id,grant_id,actor_id,actor_role,payload_fingerprint,created_at) VALUES ('later-funding','ALLOWANCE_ACTIVE','wallet','grant','staff','PLATFORM_ADMIN',?,?)", "c".repeat(64), java.sql.Timestamp.from(NOW));
            case "authority" -> jdbc.update("UPDATE platform_deployment_releases SET runtime_authority_fingerprint=? WHERE id='failed-release'", "e".repeat(64));
            default -> throw new AssertionError(change);
        }
        var before = allState();
        assertThatThrownBy(() -> store.queuePostProvisionResume("customer", "deployment", failed.id(), plan.command(), inspected, nativeReadback(NOW), "owner", r -> { }))
            .isInstanceOf(AdmissionFailure.class);
        assertThat(allState()).isEqualTo(before);
    }

    private PostProvisionResumeContracts.Command command() {
        return new PostProvisionResumeContracts.Command(failed.fence(), "failed-release", "resume-retained-inbox", "Continue remaining setup", true, null);
    }
    private PostProvisionResumeContracts.Candidate inspect() {
        return store.postProvisionResumeCandidate("customer", "deployment", failed.id(), command(), r -> { });
    }
    private PostProvisionResumeContracts.Plan plan(PostProvisionResumeContracts.Candidate inspected) {
        return store.planPostProvisionResume("customer", "deployment", failed.id(), command(), inspected, nativeReadback(NOW), r -> { });
    }
    private ObjectNode completedNativeProgress() {
        var details = (ObjectNode) tree(jdbc.queryForObject("SELECT provisioning_details_json FROM platform_deployment_releases WHERE id='failed-release'", String.class));
        ObjectNode original = ReflectionTestUtils.invokeMethod(cold, "progress");
        details.set("progress", original.path("progress"));
        var steps = details.withObject("/progress").withArray("steps"); steps.remove(steps.size() - 1);
        for (String key : List.of("stop_coolify_runtime_after_verified_cold_maintenance", "trigger_coolify_runtime_deploy", "wait_for_coolify_runtime",
            "resume_remote_dependencies_after_generation_transition", "trigger_coolify_connector_deploy", "wait_for_coolify_connector",
            "remove_coolify_connector_database_bootstrap_credentials", "redeploy_coolify_connector_without_database_bootstrap_credentials",
            "wait_for_coolify_connector_after_database_credential_scrub", "record_coolify_connector_handle", "record_coolify_runtime_handle",
            "record_coolify_runtime_database_handle", "run_verification", "apply_failed"))
            steps.addObject().put("key", key).put("status", "apply_failed".equals(key) ? "FAILED" : "COMPLETED");
        details.put("runtimeBaseUrl", "https://runtime.example").put("connectorBaseUrl", "https://connector.example")
            .put("runtimeApplicationUuid", "native-APPLICATION").put("connectorApplicationUuid", "native-CONNECTOR_APPLICATION")
            .put("runtimeDatabaseUuid", "native-RUNTIME_POSTGRES_DATABASE");
        return details;
    }
    private void changeGraph(java.util.function.Consumer<ManagedDependencyState> change) {
        ManagedDependencyState state = json.read(jdbc.queryForObject("SELECT state_json FROM deployment_managed_dependency_graphs WHERE id='graph'", String.class), ManagedDependencyState.class);
        change.accept(state); jdbc.update("UPDATE deployment_managed_dependency_graphs SET state_json=? WHERE id='graph'", json.write(state));
    }
    private String secretName(String slot, String purpose) {
        return ("MANAGED_DEP_h_" + json.fingerprint(List.of(slot, purpose)).substring(0, 24)).toUpperCase(java.util.Locale.ROOT);
    }
    private JsonNode tree(String value) { return json.read(value, JsonNode.class); }
    private FailedApplyRetryContracts.NativeReadback nativeReadback(java.time.Instant at) {
        return new FailedApplyRetryContracts.NativeReadback(at, Map.of("APPLICATION", "runtime", "CONNECTOR_APPLICATION", "connector", "RUNTIME_POSTGRES_DATABASE", "postgres"));
    }
    private Map<String, String> preserved() {
        var result = new TreeMap<String, String>();
        for (String table : List.of("provider_usage_grants", "provider_usage_allocations", "provider_usage_allowance_events", "provider_usage_validity_reviews", "provider_usage_validity_events",
            "deployment_managed_dependency_graphs", "deployment_managed_dependency_specs", "platform_managed_resource_ownership", "deployment_provider_resource_handles", "platform_deployment_versions",
            "platform_consumers", "platform_consumer_binding_history", "ai_workspace_installations", "deployment_admission_authorizations")) result.put(table, rows(table));
        result.put("priorRelease", jdbc.queryForObject("SELECT row_to_json(t)::text FROM platform_deployment_releases t WHERE id='prior-release'", String.class));
        result.put("priorBinding", jdbc.queryForObject("SELECT row_to_json(t)::text FROM deployment_admission_release_bindings t WHERE release_id='prior-release'", String.class));
        result.put("immutableReviews", jdbc.queryForObject("SELECT json_agg(t ORDER BY kind,record_key)::text FROM deployment_admission_reviews t WHERE kind<>'POST_RESUME'", String.class));
        return result;
    }
    private Map<String, String> allState() {
        var result = new TreeMap<>(preserved());
        for (String table : List.of("platform_deployments", "platform_deployment_releases", "deployment_capacity_reservations", "deployment_admission_release_bindings", "deployment_admission_reviews", "deployment_admission_events")) result.put(table, rows(table));
        return result;
    }
    private String rows(String table) {
        return jdbc.queryForObject("SELECT coalesce(json_agg(row_to_json(t) ORDER BY row_to_json(t)::text),'[]'::json)::text FROM " + table + " t", String.class);
    }
}
