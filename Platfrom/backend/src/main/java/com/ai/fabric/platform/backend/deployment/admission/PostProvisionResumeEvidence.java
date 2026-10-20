package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencyState;
import com.ai.fabric.platform.backend.deployment.model.PreservedExternalBindingKinds;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static org.springframework.http.HttpStatus.CONFLICT;

/** Proves completed runtime provisioning and a checkpointed, uninitialized dependency graph. */
final class PostProvisionResumeEvidence {
    private final JdbcTemplate jdbc;
    private final AdmissionJson json;
    private final java.util.function.Function<String, PostProvisionResumeContracts.Receipt> receipts;
    PostProvisionResumeEvidence(JdbcTemplate jdbc, AdmissionJson json,
        java.util.function.Function<String, PostProvisionResumeContracts.Receipt> receipts) {
        this.jdbc = jdbc; this.json = json; this.receipts = receipts;
    }

    PostProvisionResumeContracts.Candidate read(Reservation r, String releaseId, Manifest manifest) {
        var inventory = new TreeMap<String, String>();
        var deployment = one("SELECT * FROM platform_deployments WHERE id=? FOR UPDATE NOWAIT", r.deploymentId());
        var release = one("SELECT * FROM platform_deployment_releases WHERE id=? FOR UPDATE NOWAIT", releaseId);
        if (!r.customerId().equals(deployment.get("customer_id")) || !r.tenantId().equals(deployment.get("tenant_id"))
            || !manifest.versionId().equals(deployment.get("active_version_id")) || deployment.get("archived_at") != null
            || !"APPLY_FAILED".equals(deployment.get("status"))
            || !r.deploymentId().equals(release.get("deployment_id")) || !r.targetProfileId().equals(release.get("target_profile_id"))
            || !manifest.versionId().equals(release.get("deployment_version_id")) || !"COOLIFY".equals(release.get("provider_type"))
            || !"FAILED".equals(release.get("status")) || !"FAILED".equals(release.get("provisioning_status"))
            || !"SKIPPED".equals(release.get("verification_status")) || !"apply_failed".equals(release.get("current_step_key"))) fail("RELEASE_PHASE_CHANGED");
        var details = tree(release.get("provisioning_details_json"));
        requireCompletedNativePhase(nativePhase(details, r, releaseId).path("progress").path("steps"));
        if (!Objects.equals(deployment.get("runtime_base_url"), details.path("runtimeBaseUrl").asText())
            || !Objects.equals(deployment.get("connector_base_url"), details.path("connectorBaseUrl").asText())
            || details.path("runtimeBaseUrl").asText().isBlank() || details.path("connectorBaseUrl").asText().isBlank()) fail("ENDPOINT_CHANGED");
        inventory.put("deployment", json.fingerprint(deployment)); inventory.put("release", json.fingerprint(release));
        var binding = one("SELECT * FROM deployment_admission_release_bindings WHERE release_id=? FOR UPDATE NOWAIT", releaseId);
        for (var expected : Map.of("reservation_id", r.id(), "generation", r.generation(), "customer_id", r.customerId(),
            "tenant_id", r.tenantId(), "deployment_id", r.deploymentId(), "target_profile_id", r.targetProfileId(),
            "version_id", manifest.versionId(), "version_config_hash", manifest.versionConfigHash(), "state", "OUTCOME_UNKNOWN").entrySet())
            if (!expected.getValue().equals(binding.get(expected.getKey()))) fail("BINDING_CHANGED");
        var stable = new TreeMap<>(binding); stable.remove("updated_at"); inventory.put("binding", json.fingerprint(stable));
        var otherBindings = rows("SELECT * FROM deployment_admission_release_bindings WHERE deployment_id=? AND target_profile_id=? ORDER BY release_id FOR UPDATE NOWAIT", r.deploymentId(), r.targetProfileId());
        if (otherBindings.stream().anyMatch(b -> !releaseId.equals(b.get("release_id")) && !"NO_EFFECT".equals(b.get("state")))) fail("OTHER_RELEASE_UNSETTLED");
        var releaseIds = rows("SELECT id FROM platform_deployment_releases WHERE deployment_id=? AND (target_profile_id=? OR target_profile_id IS NULL) ORDER BY id FOR UPDATE NOWAIT", r.deploymentId(), r.targetProfileId());
        if (!releaseIds.stream().map(v -> v.get("id")).toList().equals(otherBindings.stream().map(v -> v.get("release_id")).toList())) fail("RELEASE_SET_CHANGED");
        inventory.put("authorization", json.fingerprint(one("SELECT * FROM deployment_admission_authorizations WHERE reservation_id=? AND operation_key=? FOR UPDATE NOWAIT", r.id(), binding.get("authorization_key"))));
        var handles = rows("SELECT * FROM deployment_provider_resource_handles WHERE deployment_id=? ORDER BY id FOR UPDATE NOWAIT", r.deploymentId());
        var nativeHandles = handles.stream().filter(h -> !PreservedExternalBindingKinds.isPreserved((String) h.get("resource_kind"))).toList();
        var kinds = nativeHandles.stream().map(h -> (String) h.get("resource_kind")).sorted().toList();
        if (!kinds.equals(List.of("APPLICATION", "CONNECTOR_APPLICATION", "RUNTIME_POSTGRES_DATABASE"))) fail("NATIVE_INVENTORY_CHANGED");
        for (var h : handles) if (!r.targetProfileId().equals(h.get("target_profile_id"))
            || (!PreservedExternalBindingKinds.isPreserved((String) h.get("resource_kind"))
                && (!releaseId.equals(h.get("release_id")) || !"COOLIFY".equals(h.get("provider_type"))
                    || h.get("provider_resource_uuid") == null))) fail("NATIVE_IDENTITY_CHANGED");
        for (var h : nativeHandles) {
            String field = switch ((String) h.get("resource_kind")) {
                case "APPLICATION" -> "runtimeApplicationUuid"; case "CONNECTOR_APPLICATION" -> "connectorApplicationUuid";
                default -> "runtimeDatabaseUuid";
            };
            if (!h.get("provider_resource_uuid").equals(details.path(field).asText())) fail("NATIVE_IDENTITY_CHANGED");
        }
        inventory.put("nativeKinds", json.write(kinds)); inventory.put("handles", json.fingerprint(handles));
        var graph = one("SELECT * FROM deployment_managed_dependency_graphs WHERE deployment_id=? AND target_profile_id=? FOR UPDATE NOWAIT", r.deploymentId(), r.targetProfileId());
        if (!r.customerId().equals(graph.get("customer_id")) || !r.tenantId().equals(graph.get("tenant_id"))
            || !releaseId.equals(graph.get("release_id")) || !manifest.versionId().equals(graph.get("version_id"))
            || !Objects.equals(release.get("runtime_authority_fingerprint"), graph.get("binding_fingerprint"))
            || !r.id().equals(graph.get("reservation_ref")) || graph.get("worker_token") != null
            || !"BLOCKED".equals(graph.get("status")) || graph.get("failure_code") == null) fail("DEPENDENCY_PHASE_CHANGED");
        var state = json.read((String) graph.get("state_json"), ManagedDependencyState.class);
        if (state.nodes.isEmpty() || state.nodes.stream().noneMatch(node ->
                "ADMISSION".equals(graph.get("step")) || ("PROVISION:" + node.slot).equals(graph.get("step")))
            || state.effectiveConfigurationRevision != -1
            || state.retirementFingerprint != null || state.runtimeGenerationOperationKey != null || state.runtimeGenerationState != null
            || !state.runtimeGenerationStoppedSlots.isEmpty() || state.operatorRevocationOperationKey != null
            || state.operatorRevocationOperatorId != null || state.operatorRevocationLifecycleKey != null
            || !state.retiredOperatorSecretNames.isEmpty()) fail("DEPENDENCY_EFFECTS_PRESENT");
        var identities = new java.util.HashSet<String>();
        var ownership = rows("SELECT * FROM platform_managed_resource_ownership WHERE deployment_id=? AND target_profile_id=? ORDER BY id FOR UPDATE NOWAIT",
            r.deploymentId(), r.targetProfileId());
        for (var node : state.nodes) {
            if (node.baseUrl != null || node.initialized || node.disabled
                || !"PLANNED".equals(node.status) || node.provisionedConfigurationRevision != -1
                || node.applicationCreateIntent != (node.applicationUuid != null)
                || node.databaseCreateIntent != (node.databaseUuid != null)
                || node.retirementOperationKey != null || !node.volumeReclamationPlan.isEmpty()
                || !node.reclaimedVolumeUuids.isEmpty()) fail("DEPENDENCY_EFFECTS_PRESENT");
            requireOwnedCheckpoint(r, node.applicationUuid, "DEPENDENCY_APPLICATION", node.serviceRef, ownership, identities);
            requireOwnedCheckpoint(r, node.databaseUuid, "RUNTIME_POSTGRES_DATABASE", node.serviceRef + "-db", ownership, identities);
        }
        inventory.put("dependencyOwnership", json.fingerprint(ownership));
        for (var operator : state.operators) if (operator.delivered || operator.effectiveAt != null || !"ENROLLED".equals(operator.status)) fail("OPERATOR_EFFECTS_PRESENT");
        inventory.put("graph", json.fingerprint(graph));
        jdbc.queryForObject("SELECT id FROM provider_usage_grant_mutex WHERE id=1 FOR UPDATE NOWAIT", Integer.class);
        var allocation = one("SELECT * FROM provider_usage_allocations WHERE deployment_id=? AND target_profile_id=? FOR UPDATE NOWAIT", r.deploymentId(), r.targetProfileId());
        var terms = tree(allocation.get("payload_json"));
        if (!r.customerId().equals(allocation.get("customer_id")) || !"ACTIVE".equals(allocation.get("initialization_state"))
            || allocation.get("store_uuid") == null || allocation.get("initialized_at") == null || allocation.get("activated_at") == null
            || !Objects.equals(allocation.get("runtime_allocation_fingerprint"), allocation.get("initialization_fingerprint"))
            || !r.id().equals(terms.path("initialAdmission").path("reservationId").asText())
            || !r.generation().equals(terms.path("initialAdmission").path("generation").asText())
            || !manifest.versionId().equals(terms.path("initialAdmission").path("versionId").asText())) fail("ACTIVE_ALLOWANCE_REQUIRED");
        inventory.put("allocation", json.fingerprint(allocation));
        inventory.put("grant", json.fingerprint(one("SELECT * FROM provider_usage_grants WHERE id=? FOR UPDATE NOWAIT", allocation.get("grant_id"))));
        inventory.put("financialEvents", json.fingerprint(rows("SELECT * FROM provider_usage_allowance_events WHERE subject_id=? ORDER BY id FOR UPDATE NOWAIT", allocation.get("approval_id"))));
        return new PostProvisionResumeContracts.Candidate(r, Map.copyOf(inventory), json.write(release),
            ((Timestamp) release.get("created_at")).toInstant(), ((Timestamp) release.get("updated_at")).toInstant(), List.of((String) allocation.get("approval_id")));
    }

    private void requireOwnedCheckpoint(Reservation r, String uuid, String kind, String name,
        List<Map<String, Object>> ownership, java.util.Set<String> identities) {
        if (uuid == null) return;
        if (uuid.isBlank() || !identities.add(uuid)) fail("DEPENDENCY_IDENTITY_CHANGED");
        var matches = ownership.stream().filter(row -> uuid.equals(row.get("resource_identity"))).toList();
        if (matches.size() != 1) fail("DEPENDENCY_OWNERSHIP_UNPROVEN");
        var receipt = matches.getFirst();
        if (!"OWNED".equals(receipt.get("state")) || !"coolify".equals(receipt.get("provider"))
            || !kind.equals(receipt.get("resource_kind")) || !name.equals(receipt.get("resource_name"))
            || !r.customerId().equals(receipt.get("customer_id")) || !r.tenantId().equals(receipt.get("tenant_id")))
            fail("DEPENDENCY_OWNERSHIP_UNPROVEN");
    }

    private JsonNode nativePhase(JsonNode current, Reservation reservation, String releaseId) {
        long revision = reservation.revision();
        // Repeated continuation follows verified immutable receipts, never an editable history entry.
        while (current.has("postProvisionResume")) {
            var marker = current.path("postProvisionResume");
            if (!PostProvisionResumeContracts.BOUNDARY.equals(marker.path("boundary").asText())) fail("CONTROL_FLOW_UNPROVEN");
            requireSteps(current.path("progress").path("steps"), List.of("queue_release", "preflight_verification", "resume_managed_dependencies", "apply_failed"),
                List.of("QUEUED", "COMPLETED", "RUNNING", "FAILED"));
            String key = json.fingerprint(Map.of("reservation", reservation.id(), "generation", reservation.generation(),
                "operation", marker.path("operationKey").asText()));
            var receipt = receipts.apply(key); var command = receipt.command(); var prior = receipt.failedAttempt();
            if (!reservation.id().equals(command.fence().reservationId()) || !reservation.generation().equals(command.fence().generation())
                || !releaseId.equals(command.releaseId()) || !command.proofFingerprint().equals(marker.path("proofFingerprint").asText())
                || !command.acceptRemainingDependencyProvisioning() || command.fence().revision() >= revision
                || !command.fence().equals(prior.reservation().fence())
                || !reservation.id().equals(receipt.summary().reservationId()) || !reservation.generation().equals(receipt.summary().generation())
                || !releaseId.equals(receipt.summary().releaseId()) || receipt.summary().fromRevision() != command.fence().revision()
                || receipt.summary().revision() != command.fence().revision() + 1
                || receipt.summary().financialMutationPerformed() || receipt.summary().runtimeRedeployRequired()
                || !prior.failedAt().toString().equals(marker.path("failedAt").asText())
                || !reservation.id().equals(prior.reservation().id())
                || !reservation.generation().equals(prior.reservation().generation())
                || !reservation.customerId().equals(prior.reservation().customerId())
                || !reservation.tenantId().equals(prior.reservation().tenantId())
                || !reservation.deploymentId().equals(prior.reservation().deploymentId())
                || !reservation.targetProfileId().equals(prior.reservation().targetProfileId())) fail("CONTROL_FLOW_UNPROVEN");
            revision = command.fence().revision();
            current = tree(tree(prior.failedReleaseJson()).path("provisioning_details_json").asText());
        }
        return current;
    }

    private void requireCompletedNativePhase(JsonNode steps) {
        var order = List.of("queue_release", "preflight_verification", "prepare_apply", "coolify_preflight",
            "ensure_deployment_execution_secrets", "resolve_coolify_source", "resolve_coolify_customer_scope",
            "reconcile_coolify_runtime_application", "record_coolify_runtime_handle_provisional",
            "reconcile_coolify_document_source_storage", "reconcile_coolify_connector_application",
            "record_coolify_connector_handle_provisional", "configure_coolify_runtime_private_network",
            "configure_coolify_connector_private_network", "reconcile_coolify_runtime_database",
            "record_coolify_runtime_database_handle_provisional", "configure_coolify_runtime_environment",
            "configure_coolify_connector_environment", "trigger_coolify_runtime_deploy", "wait_for_coolify_runtime",
            "trigger_coolify_connector_deploy", "wait_for_coolify_connector", "remove_coolify_connector_database_bootstrap_credentials",
            "redeploy_coolify_connector_without_database_bootstrap_credentials", "wait_for_coolify_connector_after_database_credential_scrub",
            "record_coolify_connector_handle", "record_coolify_runtime_handle", "record_coolify_runtime_database_handle", "run_verification", "apply_failed");
        if (steps.size() != order.size()) fail("CONTROL_FLOW_UNPROVEN");
        for (int i = 0; i < order.size(); i++) {
            String key = order.get(i), expected = switch (key) {
                case "queue_release" -> "QUEUED"; case "prepare_apply", "run_verification" -> "RUNNING";
                case "apply_failed" -> "FAILED"; default -> "COMPLETED";
            };
            if (!key.equals(steps.get(i).path("key").asText()) || !expected.equals(steps.get(i).path("status").asText())) fail("CONTROL_FLOW_UNPROVEN");
        }
    }
    private void requireSteps(JsonNode steps, List<String> keys, List<String> statuses) {
        if (!steps.isArray() || steps.size() != keys.size()) fail("CONTROL_FLOW_UNPROVEN");
        for (int i = 0; i < keys.size(); i++) if (!keys.get(i).equals(steps.get(i).path("key").asText())
            || !statuses.get(i).equals(steps.get(i).path("status").asText())) fail("CONTROL_FLOW_UNPROVEN");
    }
    private List<Map<String, Object>> rows(String sql, Object... args) { return jdbc.queryForList(sql, args); }
    private Map<String, Object> one(String sql, Object... args) { var values = rows(sql, args); if (values.size() != 1) fail("EXACT_SCOPE_REQUIRED"); return values.getFirst(); }
    private JsonNode tree(Object raw) { return json.read((String) raw, JsonNode.class); }
    private void fail(String code) { throw new AdmissionFailure(CONFLICT, "POST_PROVISION_RESUME_" + code); }
}
