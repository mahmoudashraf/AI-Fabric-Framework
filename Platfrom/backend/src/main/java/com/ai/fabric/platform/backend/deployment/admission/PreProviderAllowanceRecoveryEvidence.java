package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.deployment.model.PreservedExternalBindingKinds;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static org.springframework.http.HttpStatus.CONFLICT;

/** Exact control-flow proof for allowance selection failing before any target provisioning. */
final class PreProviderAllowanceRecoveryEvidence {
    private static final String ERROR = "409 CONFLICT \"PROVIDER_USAGE_REVIEWED_GRANT_AMBIGUOUS\"";
    private static final List<String> ORDER = List.of("queue_release", "preflight_verification", "prepare_apply", "apply_failed");
    private static final List<String> STATUS = List.of("QUEUED", "COMPLETED", "RUNNING", "FAILED");
    private final JdbcTemplate jdbc;
    private final AdmissionJson json;

    PreProviderAllowanceRecoveryEvidence(JdbcTemplate jdbc, AdmissionJson json) {
        this.jdbc = jdbc; this.json = json;
    }

    PreparedFailureRecoveryContracts.Proof readIfApplicable(Reservation r, String releaseId, Manifest manifest) {
        var release = one("SELECT * FROM platform_deployment_releases WHERE id=? FOR UPDATE NOWAIT", releaseId);
        var details = json.read((String) release.get("provisioning_details_json"), JsonNode.class);
        var progress = details.path("progress");
        if (!ERROR.equals(progress.path("errorMessage").asText())) return null;
        if (!details.isObject() || details.size() != 1 || !progress.isObject()
            || !"apply_failed".equals(progress.path("currentStepKey").asText())
            || !"FAILED".equals(progress.path("currentStepStatus").asText())
            || !progress.path("steps").isArray() || progress.path("steps").size() != ORDER.size()) throw denied();
        for (int i = 0; i < ORDER.size(); i++) {
            var step = progress.path("steps").get(i);
            if (!ORDER.get(i).equals(step.path("key").asText()) || !STATUS.get(i).equals(step.path("status").asText())) throw denied();
        }
        if (!r.deploymentId().equals(release.get("deployment_id")) || !r.targetProfileId().equals(release.get("target_profile_id"))
            || !manifest.versionId().equals(release.get("deployment_version_id")) || !"COOLIFY".equals(release.get("provider_type"))
            || !"FAILED".equals(release.get("status")) || !"FAILED".equals(release.get("provisioning_status"))
            || !"SKIPPED".equals(release.get("verification_status")) || release.get("provider_resource_handle_id") != null) throw denied();
        var deployment = one("SELECT * FROM platform_deployments WHERE id=? FOR UPDATE NOWAIT", r.deploymentId());
        if (!r.customerId().equals(deployment.get("customer_id")) || !r.tenantId().equals(deployment.get("tenant_id"))
            || deployment.get("archived_at") != null) throw denied();
        var inventory = new TreeMap<String, String>();
        inventory.put("release", json.fingerprint(release));
        var bindings = rows("SELECT * FROM deployment_admission_release_bindings WHERE deployment_id=? AND target_profile_id=? ORDER BY release_id FOR UPDATE NOWAIT", r.deploymentId(), r.targetProfileId());
        var binding = bindings.stream().filter(b -> releaseId.equals(b.get("release_id"))).toList();
        if (binding.size() != 1 || bindings.stream().anyMatch(b -> !releaseId.equals(b.get("release_id")) && !"NO_EFFECT".equals(b.get("state")))) throw denied();
        for (var expected : Map.of("reservation_id", r.id(), "generation", r.generation(), "customer_id", r.customerId(),
            "tenant_id", r.tenantId(), "deployment_id", r.deploymentId(), "target_profile_id", r.targetProfileId(),
            "version_id", manifest.versionId(), "version_config_hash", manifest.versionConfigHash(), "state", "OUTCOME_UNKNOWN").entrySet())
            if (!expected.getValue().equals(binding.getFirst().get(expected.getKey()))) throw denied();
        var stable = bindings.stream().map(b -> { var v = new TreeMap<>(b); v.remove("updated_at"); return v; }).toList();
        inventory.put("bindings", json.fingerprint(stable));
        var releases = rows("SELECT id FROM platform_deployment_releases WHERE deployment_id=? AND (target_profile_id=? OR target_profile_id IS NULL) ORDER BY id FOR UPDATE NOWAIT", r.deploymentId(), r.targetProfileId());
        if (!releases.stream().map(b -> b.get("id")).toList().equals(bindings.stream().map(b -> b.get("release_id")).toList())) throw denied();
        var authorization = one("SELECT * FROM deployment_admission_authorizations WHERE reservation_id=? AND operation_key=? FOR UPDATE NOWAIT", r.id(), binding.getFirst().get("authorization_key"));
        var receipt = json.read((String) authorization.get("payload_json"), AuthorizationReceipt.class);
        if (!r.id().equals(receipt.reservationId()) || !r.generation().equals(receipt.generation())
            || !r.manifestFingerprint().equals(receipt.manifestFingerprint()) || receipt.command() == null
            || !binding.getFirst().get("authorization_key").equals(receipt.command().operationKey())
            || !authorization.get("request_fingerprint").equals(receipt.requestFingerprint())) throw denied();
        inventory.put("authorization", json.fingerprint(authorization));
        var events = rows("SELECT * FROM deployment_admission_events WHERE reservation_id=? ORDER BY revision FOR UPDATE NOWAIT", r.id());
        if (events.size() != r.revision() + 1 || events.stream().anyMatch(e -> !Set.of("RESERVED", "INITIAL_EVIDENCE_RENEWED",
            "ALLOCATION_OPERATION_AUTHORIZED", "RELEASE_PROVISIONING_STARTED", "OUTCOME_UNKNOWN").contains(e.get("event_type")))) throw denied();
        for (int i = 0; i < events.size(); i++) if (((Number) events.get(i).get("revision")).longValue() != i) throw denied();
        inventory.put("events", json.fingerprint(events));
        var handles = rows("SELECT * FROM deployment_provider_resource_handles WHERE deployment_id=? AND (target_profile_id=? OR target_profile_id IS NULL) ORDER BY id FOR UPDATE NOWAIT", r.deploymentId(), r.targetProfileId());
        if (handles.stream().anyMatch(h -> h.get("release_id") != null || !PreservedExternalBindingKinds.isPreserved((String) h.get("resource_kind")))) throw denied();
        inventory.put("preservedExternalBindings", json.fingerprint(handles));
        absent(inventory, "graph", "SELECT * FROM deployment_managed_dependency_graphs WHERE deployment_id=? AND target_profile_id=? FOR UPDATE NOWAIT", r.deploymentId(), r.targetProfileId());
        jdbc.queryForObject("SELECT id FROM provider_usage_grant_mutex WHERE id=1 FOR UPDATE NOWAIT", Integer.class);
        absent(inventory, "allowance", "SELECT * FROM provider_usage_allocations WHERE deployment_id=? AND target_profile_id=? FOR UPDATE NOWAIT", r.deploymentId(), r.targetProfileId());
        inventory.put("preservedGrants", json.fingerprint(rows("SELECT * FROM provider_usage_grants WHERE customer_id=? AND target_profile_id=? ORDER BY id FOR UPDATE NOWAIT", r.customerId(), r.targetProfileId())));
        absent(inventory, "activity", "SELECT * FROM deployment_admission_activity_watermarks WHERE reservation_id=? FOR UPDATE NOWAIT", r.id());
        absent(inventory, "retirement", "SELECT * FROM platform_target_retirement_operations WHERE deployment_id=? AND target_profile_id=? FOR UPDATE NOWAIT", r.deploymentId(), r.targetProfileId());
        absent(inventory, "targetIndexes", "SELECT * FROM platform_tenant_scoped_vector_resources WHERE deployment_id=? AND (target_profile_id=? OR target_profile_id IS NULL) FOR UPDATE NOWAIT", r.deploymentId(), r.targetProfileId());
        absent(inventory, "assignedConsumers", "SELECT * FROM platform_consumers WHERE bound_deployment_id=? AND bound_target_profile_id=? FOR UPDATE NOWAIT", r.deploymentId(), r.targetProfileId());
        absent(inventory, "deletion", "SELECT * FROM platform_deployment_deletion_operations WHERE deployment_id=? FOR UPDATE NOWAIT", r.deploymentId());
        var gates = rows("SELECT * FROM platform_target_retirement_gates WHERE deployment_id=? AND target_profile_id=? FOR UPDATE NOWAIT", r.deploymentId(), r.targetProfileId());
        if (gates.size() > 1 || gates.stream().anyMatch(g -> !"AVAILABLE".equals(g.get("state")) || g.get("operation_id") != null)) throw denied();
        inventory.put("targetGate", json.fingerprint(gates));
        return new PreparedFailureRecoveryContracts.Proof("COOLIFY_ALLOWANCE_SELECTION_FAILED_BEFORE_NATIVE_EFFECTS_V1", Map.copyOf(inventory), List.of());
    }

    private void absent(Map<String, String> inventory, String key, String sql, Object... args) {
        var found = rows(sql, args); if (!found.isEmpty()) throw denied(); inventory.put(key, json.fingerprint(found));
    }
    private List<Map<String, Object>> rows(String sql, Object... args) { return jdbc.queryForList(sql, args); }
    private Map<String, Object> one(String sql, Object... args) {
        var found = rows(sql, args); if (found.size() != 1) throw denied(); return found.getFirst();
    }
    private AdmissionFailure denied() { return new AdmissionFailure(CONFLICT, "PREPARED_RECOVERY_ALLOWANCE_FAILURE_UNPROVEN"); }
}
