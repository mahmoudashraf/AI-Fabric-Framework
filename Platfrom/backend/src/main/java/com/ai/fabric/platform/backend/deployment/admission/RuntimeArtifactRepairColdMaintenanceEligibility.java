package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpecValidator;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencyState;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.ai.fabric.platform.backend.deployment.admission.RuntimeArtifactRepairContracts.*;
import static org.springframework.http.HttpStatus.CONFLICT;

/** Attended staging maintenance for a failed conversational reapply, not automatic recovery. */
@Component
public final class RuntimeArtifactRepairColdMaintenanceEligibility {
    private final JdbcTemplate jdbc;
    private final AdmissionJson json;
    private final ManagedDependencySpecValidator dependencyValidator;

    public RuntimeArtifactRepairColdMaintenanceEligibility(JdbcTemplate jdbc, AdmissionJson json,
        ManagedDependencySpecValidator dependencyValidator) {
        this.jdbc = jdbc;
        this.json = json;
        this.dependencyValidator = dependencyValidator;
    }

    ColdPriorRuntime prior(AdmissionContracts.Reservation reservation, Map<String, Object> deployment,
        Map<String, Object> failed, Request request, Map<String, String> fingerprints) {
        var acknowledgement = request.coldMaintenance();
        if (request.mode() != Mode.COLD_MAINTENANCE_REAPPLY || acknowledgement == null
            || !acknowledgement.acceptInMemoryStateLoss() || !text(acknowledgement.approvalReference())
            || acknowledgement.approvalReference().length() > 255 || !text(acknowledgement.previousReleaseId())
            || reservation.intent() != AdmissionContracts.Intent.CUSTOMER_STAGING
            || !"RUNTIME_GENERATION_DRAIN_INCOMPLETE".equals(failed.get("error_message"))
            || failed.get("runtime_generation_transition_state") != null
            || failed.get("runtime_generation_transition_json") != null
            || failed.get("runtime_generation_transition_fingerprint") != null) fail("EXPLICIT_STAGING_MAINTENANCE_REQUIRED");
        var prior = one("SELECT * FROM platform_deployment_releases WHERE id=? FOR UPDATE NOWAIT",
            acknowledgement.previousReleaseId());
        String priorVersionId = (String) prior.get("deployment_version_id");
        if (!reservation.deploymentId().equals(prior.get("deployment_id"))
            || !reservation.targetProfileId().equals(prior.get("target_profile_id"))
            || !Objects.equals(deployment.get("active_version_id"), priorVersionId)
            || Objects.equals(failed.get("deployment_version_id"), priorVersionId)
            || !"APPLIED_VERIFIED".equals(prior.get("status"))
            || !"ACTIVE".equals(prior.get("provisioning_status"))
            || !"PASSED".equals(prior.get("verification_status"))
            || !"COOLIFY".equals(prior.get("provider_type"))
            || !text((String) failed.get("source_artifact_id"))
            || !Objects.equals(failed.get("source_artifact_id"), prior.get("source_artifact_id"))
            || !(prior.get("created_at") instanceof Timestamp before)
            || !(failed.get("created_at") instanceof Timestamp after) || !before.before(after)) fail("PRIOR_VERIFIED_RUNTIME_REQUIRED");
        var priorVersion = one("SELECT * FROM platform_deployment_versions WHERE id=? FOR UPDATE NOWAIT", priorVersionId);
        var nextVersion = one("SELECT * FROM platform_deployment_versions WHERE id=? FOR UPDATE NOWAIT", failed.get("deployment_version_id"));
        for (var version : List.of(priorVersion, nextVersion)) {
            if (!reservation.deploymentId().equals(version.get("deployment_id"))
                || !"PUBLISHED".equals(version.get("status"))
                || !"CONVERSATIONAL".equals(json.read((String) version.get("behavior_config_json"),
                    com.fasterxml.jackson.databind.JsonNode.class).path("type").asText())) fail("CONVERSATIONAL_CANDIDATE_REQUIRED");
        }
        var bindings = jdbc.queryForList("SELECT * FROM deployment_admission_release_bindings WHERE deployment_id=? AND target_profile_id=? ORDER BY release_id FOR UPDATE NOWAIT",
            reservation.deploymentId(), reservation.targetProfileId());
        var active = bindings.stream().filter(row -> "ACTIVE".equals(row.get("state"))).toList();
        if (active.size() != 1 || !prior.get("id").equals(active.getFirst().get("release_id"))
            || !reservation.id().equals(active.getFirst().get("reservation_id"))
            || !reservation.generation().equals(active.getFirst().get("generation"))) fail("PRIOR_ALLOCATION_CHANGED");
        var consumers = jdbc.queryForList("SELECT * FROM platform_consumers WHERE bound_deployment_id=? ORDER BY id FOR UPDATE NOWAIT",
            reservation.deploymentId());
        if (consumers.isEmpty() || consumers.stream().anyMatch(row -> !reservation.customerId().equals(row.get("customer_id"))
            || !prior.get("id").equals(row.get("bound_release_id"))
            || !reservation.targetProfileId().equals(row.get("bound_target_profile_id")))) fail("PRIOR_CONSUMER_CHANGED");
        var installations = jdbc.queryForList("SELECT i.* FROM ai_workspace_installations i JOIN platform_consumers c ON c.id=i.consumer_entity_id WHERE c.bound_deployment_id=? ORDER BY i.id FOR UPDATE OF i NOWAIT",
            reservation.deploymentId());
        if (installations.stream().anyMatch(row -> !reservation.customerId().equals(row.get("customer_id"))
            || !"DRAFT".equals(row.get("status")) || !row.containsKey("activated_at")
            || row.get("activated_at") != null)) fail("UNACTIVATED_WORKSPACE_REQUIRED");
        var authority = one("SELECT * FROM platform_runtime_authority_snapshots WHERE release_id=? FOR UPDATE NOWAIT", prior.get("id"));
        String authorityFingerprint = (String) prior.get("runtime_authority_fingerprint");
        if (!text(authorityFingerprint) || !authorityFingerprint.matches("[a-f0-9]{64}")
            || !reservation.deploymentId().equals(authority.get("deployment_id"))
            || !reservation.targetProfileId().equals(authority.get("target_profile_id"))
            || !priorVersionId.equals(authority.get("version_id"))
            || !authorityFingerprint.equals(authority.get("fingerprint"))) fail("PRIOR_AUTHORITY_CHANGED");
        var result = new ColdPriorRuntime((String) prior.get("id"), priorVersionId, authorityFingerprint);
        fingerprints.put("coldPriorRuntime", json.write(result));
        fingerprints.put("coldPriorRelease", json.fingerprint(prior));
        fingerprints.put("coldPriorVersion", json.fingerprint(priorVersion));
        fingerprints.put("coldPriorAuthority", json.fingerprint(authority));
        fingerprints.put("coldConsumers", json.fingerprint(consumers));
        fingerprints.put("coldInstallations", json.fingerprint(installations));
        fingerprints.put("coldConsumerHistory", json.fingerprint(jdbc.queryForList(
            "SELECT * FROM platform_consumer_binding_history WHERE to_deployment_id=? OR from_deployment_id=? ORDER BY id FOR UPDATE NOWAIT",
            reservation.deploymentId(), reservation.deploymentId())));
        return result;
    }

    void graph(Map<String, Object> graph, ManagedDependencyState state, ColdPriorRuntime prior, String nextReleaseId) {
        String operation = dependencyValidator.fingerprint(List.of("RUNTIME_GENERATION", graph.get("id"),
            graph.get("target_profile_id"), prior.releaseId(), nextReleaseId,
            graph.get("spec_fingerprint"), graph.get("binding_fingerprint")));
        if (!operation.equals(state.runtimeGenerationOperationKey) || !"QUIESCED".equals(state.runtimeGenerationState)
            || state.nodes.size() != 1 || !state.runtimeGenerationStoppedSlots.equals(List.of(state.nodes.getFirst().slot)))
            fail("QUIESCED_INBOX_REQUIRED");
        RuntimeArtifactRepairDependencyEligibility.requireColdPrepared((String) graph.get("status"),
            (String) graph.get("step"), (String) graph.get("failure_code"), state);
    }

    private Map<String, Object> one(String sql, Object... args) {
        var rows = jdbc.queryForList(sql, args);
        if (rows.size() != 1) fail("EXACT_SCOPE_REQUIRED");
        return rows.getFirst();
    }
    private static boolean text(String value) { return value != null && !value.isBlank() && value.equals(value.trim()); }
    private static void fail(String code) { throw new AdmissionFailure(CONFLICT, "RUNTIME_ARTIFACT_REPAIR_COLD_" + code); }
}
