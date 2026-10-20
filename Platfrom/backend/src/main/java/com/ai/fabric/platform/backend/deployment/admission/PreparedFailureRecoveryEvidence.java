package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencyState;
import com.ai.fabric.platform.backend.deployment.model.PreservedExternalBindingKinds;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static org.springframework.http.HttpStatus.CONFLICT;

/** Positive local control-flow proof for narrowly defined initial apply failures. */
final class PreparedFailureRecoveryEvidence {
    private static final String BOUNDARY = "COOLIFY_SOURCE_RESOLUTION_FAILED_BEFORE_NATIVE_EFFECTS_V1";
    private static final Map<String, String> STEPS = Map.of("queue_release", "QUEUED",
        "preflight_verification", "COMPLETED", "prepare_apply", "RUNNING", "coolify_preflight", "COMPLETED",
        "ensure_deployment_execution_secrets", "COMPLETED", "resolve_coolify_source", "FAILED", "apply_failed", "FAILED");
    private static final List<String> ORDER = List.of("queue_release", "preflight_verification", "prepare_apply",
        "coolify_preflight", "ensure_deployment_execution_secrets", "resolve_coolify_source", "apply_failed");
    private final JdbcTemplate jdbc;
    private final AdmissionJson json;
    private final boolean nativeRetry;

    PreparedFailureRecoveryEvidence(JdbcTemplate jdbc, AdmissionJson json) { this(jdbc, json, false); }
    PreparedFailureRecoveryEvidence(JdbcTemplate jdbc, AdmissionJson json, boolean nativeRetry) {
        this.jdbc = jdbc; this.json = json; this.nativeRetry = nativeRetry;
    }

    PreparedFailureRecoveryContracts.Proof read(Reservation r, String releaseId, Manifest manifest) {
        var inventory = new TreeMap<String, String>();
        var deployment = one("SELECT * FROM platform_deployments WHERE id=? FOR UPDATE NOWAIT", r.deploymentId());
        if (!r.customerId().equals(deployment.get("customer_id")) || !r.tenantId().equals(deployment.get("tenant_id"))
            || deployment.get("active_version_id") != null || deployment.get("archived_at") != null
            || nonblank(deployment.get("runtime_base_url")) || nonblank(deployment.get("connector_base_url")))
            throw denied("ACTIVATION_OR_OWNERSHIP_HISTORY");
        var release = one("SELECT * FROM platform_deployment_releases WHERE id=? FOR UPDATE NOWAIT", releaseId);
        if (!r.deploymentId().equals(release.get("deployment_id")) || !r.targetProfileId().equals(release.get("target_profile_id"))
            || !manifest.versionId().equals(release.get("deployment_version_id")) || !"COOLIFY".equals(release.get("provider_type"))
            || !"FAILED".equals(release.get("status")) || !"FAILED".equals(release.get("provisioning_status"))
            || !"SKIPPED".equals(release.get("verification_status")) || release.get("provider_resource_handle_id") != null
            || !"apply_failed".equals(release.get("current_step_key"))) throw denied("TERMINAL_RELEASE_REQUIRED");
        JsonNode details = tree(release.get("provisioning_details_json")), progress = details.path("progress");
        if (!details.isObject() || details.size() != 1 || !progress.isObject()
            || !"apply_failed".equals(progress.path("currentStepKey").asText())
            || !"FAILED".equals(progress.path("currentStepStatus").asText()) || !progress.path("steps").isArray()
            || !nativeRetry && progress.path("steps").size() != ORDER.size()) throw denied("CONTROL_FLOW_UNPROVEN");
        if (nativeRetry) requireConnectorStartupFailure(progress.path("steps"));
        else for (int i = 0; i < ORDER.size(); i++) {
            var step = progress.path("steps").get(i);
            if (!ORDER.get(i).equals(step.path("key").asText())
                || !STEPS.get(ORDER.get(i)).equals(step.path("status").asText())) throw denied("CONTROL_FLOW_UNPROVEN");
        }
        inventory.put("release", json.fingerprint(release));
        var binding = one("SELECT * FROM deployment_admission_release_bindings WHERE release_id=? FOR UPDATE NOWAIT", releaseId);
        for (var expected : Map.of("reservation_id", r.id(), "generation", r.generation(), "customer_id", r.customerId(),
            "tenant_id", r.tenantId(), "deployment_id", r.deploymentId(), "target_profile_id", r.targetProfileId(),
            "version_id", manifest.versionId(), "version_config_hash", manifest.versionConfigHash(), "state", "OUTCOME_UNKNOWN").entrySet()) {
            if (!expected.getValue().equals(binding.get(expected.getKey()))) throw denied("BINDING_CHANGED");
        }
        // The reconciliation poll updates only this clock, even when the failed release is unchanged.
        var stableBinding = new TreeMap<>(binding); stableBinding.remove("updated_at");
        inventory.put("binding", json.fingerprint(stableBinding));
        var authorization = one("SELECT * FROM deployment_admission_authorizations WHERE reservation_id=? AND operation_key=? FOR UPDATE NOWAIT",
            r.id(), binding.get("authorization_key"));
        var receipt = json.read((String) authorization.get("payload_json"), AuthorizationReceipt.class);
        if (!r.id().equals(receipt.reservationId()) || !r.generation().equals(receipt.generation())
            || !r.manifestFingerprint().equals(receipt.manifestFingerprint())
            || !Objects.equals(authorization.get("request_fingerprint"), receipt.requestFingerprint())
            || receipt.command() == null || !Objects.equals(binding.get("authorization_key"), receipt.command().operationKey()))
            throw denied("AUTHORIZATION_CHANGED");
        inventory.put("authorization", json.fingerprint(authorization));
        var bindings = rows("SELECT * FROM deployment_admission_release_bindings WHERE deployment_id=? AND target_profile_id=? ORDER BY release_id FOR UPDATE NOWAIT",
            r.deploymentId(), r.targetProfileId());
        if (bindings.stream().anyMatch(b -> !releaseId.equals(b.get("release_id")) && !"NO_EFFECT".equals(b.get("state"))))
            throw denied("OTHER_RELEASE_UNSETTLED");
        var releases = rows("SELECT id FROM platform_deployment_releases WHERE deployment_id=? AND (target_profile_id=? OR target_profile_id IS NULL) ORDER BY id FOR UPDATE NOWAIT",
            r.deploymentId(), r.targetProfileId());
        if (!releases.stream().map(row -> row.get("id")).toList().equals(bindings.stream().map(row -> row.get("release_id")).toList()))
            throw denied("RELEASE_SET_AMBIGUOUS");
        var events = rows("SELECT * FROM deployment_admission_events WHERE reservation_id=? ORDER BY revision FOR UPDATE NOWAIT", r.id());
        if (events.size() != r.revision() + 1 || events.stream().anyMatch(event -> !List.of("RESERVED", "INITIAL_EVIDENCE_RENEWED",
            "ALLOCATION_OPERATION_AUTHORIZED", "RELEASE_PROVISIONING_STARTED", "OUTCOME_UNKNOWN").contains(event.get("event_type"))
                && !(nativeRetry && "PREPARED_FAILURE_RECOVERED_BEFORE_NATIVE_EFFECTS".equals(event.get("event_type")))))
            throw denied("ADMISSION_HISTORY_AMBIGUOUS");
        for (int i = 0; i < events.size(); i++) if (((Number) events.get(i).get("revision")).longValue() != i)
            throw denied("ADMISSION_HISTORY_AMBIGUOUS");
        inventory.put("admissionEvents", json.fingerprint(events));
        var handles = rows("SELECT * FROM deployment_provider_resource_handles WHERE deployment_id=? ORDER BY id FOR UPDATE NOWAIT", r.deploymentId());
        if (handles.stream().anyMatch(h -> !r.targetProfileId().equals(h.get("target_profile_id"))
            || (PreservedExternalBindingKinds.isPreserved((String) h.get("resource_kind")) ? h.get("release_id") != null
                : !nativeRetry || !releaseId.equals(h.get("release_id")) || !"COOLIFY".equals(h.get("provider_type"))
                    || h.get("provider_resource_uuid") == null))) throw denied("NATIVE_HISTORY_PRESENT");
        if (nativeRetry) {
            var nativeKinds = handles.stream().filter(h -> !PreservedExternalBindingKinds.isPreserved((String) h.get("resource_kind")))
                .map(h -> (String) h.get("resource_kind")).sorted().toList();
            if (nativeKinds.size() != new java.util.HashSet<>(nativeKinds).size()
                || !nativeKinds.containsAll(List.of("APPLICATION", "CONNECTOR_APPLICATION"))
                || !List.of("APPLICATION", "CONNECTOR_APPLICATION", "RUNTIME_POSTGRES_DATABASE").containsAll(nativeKinds))
                throw denied("NATIVE_INVENTORY_AMBIGUOUS");
            inventory.put("nativeKinds", json.write(nativeKinds));
        }
        inventory.put("retainedExternalBindings", json.fingerprint(handles));
        var graphs = rows("SELECT * FROM deployment_managed_dependency_graphs WHERE deployment_id=? AND target_profile_id=? ORDER BY id FOR UPDATE NOWAIT",
            r.deploymentId(), r.targetProfileId());
        if (graphs.size() != 1) throw denied("PREPARED_GRAPH_REQUIRED");
        var graph = graphs.getFirst();
        if (!r.customerId().equals(graph.get("customer_id")) || !r.tenantId().equals(graph.get("tenant_id"))
            || !releaseId.equals(graph.get("release_id")) || !manifest.versionId().equals(graph.get("version_id"))
            || !Objects.equals(release.get("runtime_authority_fingerprint"), graph.get("binding_fingerprint"))
            || !r.id().equals(graph.get("reservation_ref")) || graph.get("worker_token") != null || graph.get("failure_code") != null
            || !"PROVISIONED".equals(graph.get("status")) || !"RUNTIME_INITIALIZATION_REQUIRED".equals(graph.get("step")))
            throw denied("GRAPH_SCOPE_OR_WORKER_CHANGED");
        var state = json.read((String) graph.get("state_json"), ManagedDependencyState.class);
        if (state.nodes.isEmpty() || state.effectiveConfigurationRevision != -1 || !state.callbacks.isEmpty()
            || state.retirementFingerprint != null || state.runtimeGenerationOperationKey != null || state.runtimeGenerationState != null
            || !state.runtimeGenerationStoppedSlots.isEmpty() || state.operatorRevocationOperationKey != null
            || state.operatorRevocationOperatorId != null || state.operatorRevocationLifecycleKey != null
            || !state.retiredOperatorSecretNames.isEmpty()) throw denied("GRAPH_EFFECT_HISTORY");
        for (var node : state.nodes) if (node.applicationUuid != null || node.databaseUuid != null || node.baseUrl != null
            || node.applicationCreateIntent || node.databaseCreateIntent || node.initialized || node.disabled
            || !"PLANNED".equals(node.status) || node.provisionedConfigurationRevision != -1 || !node.volumes.isEmpty()
            || node.retirementOperationKey != null || !node.volumeReclamationPlan.isEmpty() || !node.reclaimedVolumeUuids.isEmpty())
            throw denied("GRAPH_EFFECT_HISTORY");
        for (var operator : state.operators) if (operator.delivered || operator.effectiveAt != null || !"ENROLLED".equals(operator.status))
            throw denied("OPERATOR_EFFECT_HISTORY");
        inventory.put("retainedGraph", json.fingerprint(graph));

        for (var check : Map.of("activity", "deployment_admission_activity_watermarks WHERE reservation_id=?",
            "retirement", "platform_target_retirement_operations WHERE deployment_id=?",
            "deletion", "platform_deployment_deletion_operations WHERE deployment_id=?",
            "vectorRuns", "platform_vectorization_runs WHERE deployment_id=?",
            "vectorSessions", "platform_vectorization_runner_sessions WHERE deployment_id=?",
            "managedVectors", "platform_deployment_managed_vector_resources WHERE deployment_id=?",
            "targetIndexes", "platform_tenant_scoped_vector_resources WHERE deployment_id=?").entrySet()) {
            var found = rows("SELECT * FROM " + check.getValue() + " FOR UPDATE NOWAIT", "activity".equals(check.getKey()) ? r.id() : r.deploymentId());
            if (!found.isEmpty()) throw denied("EFFECT_HISTORY_" + check.getKey());
            inventory.put(check.getKey(), json.fingerprint(found));
        }
        jdbc.queryForObject("SELECT id FROM provider_usage_grant_mutex WHERE id=1 FOR UPDATE NOWAIT", Integer.class);
        var allocations = rows("SELECT * FROM provider_usage_allocations WHERE deployment_id=? AND target_profile_id=? ORDER BY approval_id FOR UPDATE NOWAIT",
            r.deploymentId(), r.targetProfileId());
        if (allocations.size() != 1) throw denied("RETAINED_ALLOCATION_REQUIRED");
        var allocation = allocations.getFirst();
        var terms = tree(allocation.get("payload_json"));
        if (!r.customerId().equals(allocation.get("customer_id")) || !"UNINITIALIZED".equals(allocation.get("initialization_state"))
            || allocation.get("store_uuid") != null || allocation.get("initialization_fingerprint") != null
            || allocation.get("initialized_at") != null || allocation.get("activated_at") != null
            || !r.id().equals(terms.path("initialAdmission").path("reservationId").asText())
            || !r.generation().equals(terms.path("initialAdmission").path("generation").asText())
            || !manifest.versionId().equals(terms.path("initialAdmission").path("versionId").asText())) throw denied("ALLOCATION_EFFECT_HISTORY");
        inventory.put("retainedAllocations", json.fingerprint(allocations));
        inventory.put("retainedGrant", json.fingerprint(one("SELECT * FROM provider_usage_grants WHERE id=? FOR UPDATE NOWAIT", allocation.get("grant_id"))));
        var financialEvents = rows("SELECT * FROM provider_usage_allowance_events WHERE subject_id=? ORDER BY id FOR UPDATE NOWAIT", allocation.get("approval_id"));
        if (financialEvents.size() != 1 || !"ALLOWANCE_ALLOCATED".equals(financialEvents.getFirst().get("event_type"))
            || !Objects.equals(allocation.get("grant_id"), financialEvents.getFirst().get("grant_id"))) throw denied("ALLOCATION_EFFECT_HISTORY");
        inventory.put("retainedFinancialEvents", json.fingerprint(financialEvents));
        return new PreparedFailureRecoveryContracts.Proof(nativeRetry ? FailedApplyRetryContracts.BOUNDARY : BOUNDARY, Map.copyOf(inventory),
            List.of((String) allocation.get("approval_id")));
    }

    private void requireConnectorStartupFailure(JsonNode steps) {
        var ordered = List.of("queue_release", "preflight_verification", "prepare_apply", "coolify_preflight",
            "ensure_deployment_execution_secrets", "resolve_coolify_source", "resolve_coolify_customer_scope",
            "reconcile_coolify_runtime_application", "record_coolify_runtime_handle_provisional",
            "reconcile_coolify_document_source_storage", "reconcile_coolify_connector_application",
            "record_coolify_connector_handle_provisional", "configure_coolify_runtime_private_network",
            "configure_coolify_connector_private_network", "reconcile_coolify_runtime_database",
            "record_coolify_runtime_database_handle_provisional", "configure_coolify_runtime_environment",
            "configure_coolify_connector_environment", "trigger_coolify_runtime_deploy", "wait_for_coolify_runtime",
            "trigger_coolify_connector_deploy", "wait_for_coolify_connector", "apply_failed");
        var optional = java.util.Set.of("reconcile_coolify_document_source_storage", "reconcile_coolify_runtime_database",
            "record_coolify_runtime_database_handle_provisional");
        var keys = new java.util.ArrayList<String>();
        for (var step : steps) {
            String key = step.path("key").asText(); keys.add(key);
            String status = switch (key) {
                case "queue_release" -> "QUEUED";
                case "prepare_apply" -> "RUNNING";
                case "wait_for_coolify_connector", "apply_failed" -> "FAILED";
                default -> "COMPLETED";
            };
            if (!status.equals(step.path("status").asText())) throw denied("CONTROL_FLOW_UNPROVEN");
        }
        if (keys.size() != new java.util.HashSet<>(keys).size()
            || !keys.equals(ordered.stream().filter(key -> !optional.contains(key) || keys.contains(key)).toList()))
            throw denied("CONTROL_FLOW_UNPROVEN");
    }

    private List<Map<String, Object>> rows(String sql, Object... args) { return jdbc.queryForList(sql, args); }
    private Map<String, Object> one(String sql, Object... args) {
        var rows = rows(sql, args); if (rows.size() != 1) throw denied("EXACT_SCOPE_REQUIRED"); return rows.getFirst();
    }
    private JsonNode tree(Object raw) { return json.read((String) raw, JsonNode.class); }
    private boolean nonblank(Object raw) { return raw != null && !raw.toString().isBlank(); }
    private AdmissionFailure denied(String code) { return new AdmissionFailure(CONFLICT, "PREPARED_RECOVERY_" + code); }
}
