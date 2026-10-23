package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpec;
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

/** Exact pre-runtime-stop reapply proof. Existing native/configuration effects are retained, not declared absent. */
final class ReapplyBeforeRuntimeStopEvidence {
    private static final List<String> STEPS = List.of("queue_release", "preflight_verification", "prepare_apply",
        "coolify_preflight", "ensure_deployment_execution_secrets", "resolve_coolify_source", "resolve_coolify_customer_scope",
        "reconcile_coolify_runtime_application", "record_coolify_runtime_handle_provisional",
        "reconcile_coolify_document_source_storage", "reconcile_coolify_connector_application",
        "record_coolify_connector_handle_provisional", "configure_coolify_runtime_private_network",
        "configure_coolify_connector_private_network", "reconcile_coolify_runtime_database",
        "record_coolify_runtime_database_handle_provisional", "configure_coolify_runtime_environment",
        "configure_coolify_connector_environment", "stop_coolify_connector_for_generation_transition", "apply_failed");
    private final JdbcTemplate jdbc;
    private final AdmissionJson json;

    ReapplyBeforeRuntimeStopEvidence(JdbcTemplate jdbc, AdmissionJson json) { this.jdbc = jdbc; this.json = json; }

    FailedApplyRetryContracts.Candidate read(Reservation r, String releaseId, Manifest manifest) {
        return read(r, releaseId, manifest, null, null);
    }

    FailedApplyRetryContracts.Candidate read(Reservation r, String releaseId, Manifest manifest,
        RuntimeArtifactRepairContracts.VerifiedRepair repair,
        com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationCheckpointService.Checkpoint checkpoint) {
        boolean cold = repair != null;
        var coldPrior = cold && repair.finalLocalFingerprints().containsKey("coldPriorRuntime")
            ? json.read(repair.finalLocalFingerprints().get("coldPriorRuntime"), RuntimeArtifactRepairContracts.ColdPriorRuntime.class) : null;
        boolean unassigned = coldPrior != null && coldPrior.unassignedVerificationFailure();
        var inventory = new TreeMap<String, String>();
        var deployment = one("SELECT * FROM platform_deployments WHERE id=? FOR UPDATE NOWAIT", r.deploymentId());
        String priorVersionId = (String) deployment.get("active_version_id");
        if (!r.customerId().equals(deployment.get("customer_id")) || !r.tenantId().equals(deployment.get("tenant_id"))
            || !text(priorVersionId) || !unassigned && manifest.versionId().equals(priorVersionId) || deployment.get("archived_at") != null
            || !"APPLY_FAILED".equals(deployment.get("status")) || !text(deployment.get("runtime_base_url"))
            || !text(deployment.get("connector_base_url")) || !r.demand().equals(manifest.demand())) fail("ACTIVE_DEPLOYMENT_REQUIRED");
        inventory.put("deployment", json.fingerprint(deployment));
        var release = one("SELECT * FROM platform_deployment_releases WHERE id=? FOR UPDATE NOWAIT", releaseId);
        if (!r.deploymentId().equals(release.get("deployment_id")) || !r.targetProfileId().equals(release.get("target_profile_id"))
            || !manifest.versionId().equals(release.get("deployment_version_id")) || !"COOLIFY".equals(release.get("provider_type"))
            || !"FAILED".equals(release.get("status")) || !"FAILED".equals(release.get("provisioning_status"))
            || !"SKIPPED".equals(release.get("verification_status")) || !"apply_failed".equals(release.get("current_step_key"))
            || release.get("provider_resource_handle_id") != null
            || !cold && (release.get("runtime_generation_transition_state") != null || release.get("runtime_generation_transition_json") != null
                || release.get("runtime_generation_transition_fingerprint") != null)) fail("FAILED_PRE_STOP_RELEASE_REQUIRED");
        if (cold) {
            var scope = repair.scope();
            if (checkpoint == null || checkpoint.phase() != com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationCheckpointService.Phase.COLD_ADOPTED
                || checkpoint.coldMaintenanceReceipt() == null || !releaseId.equals(scope.releaseId())
                || !r.deploymentId().equals(scope.deploymentId()) || !r.targetProfileId().equals(scope.targetProfileId())
                || !r.customerId().equals(scope.customerId()) || !r.tenantId().equals(scope.tenantId())
                || !r.id().equals(scope.reservationId()) || !r.generation().equals(scope.generation())
                || !manifest.versionId().equals(scope.versionId()) || !manifest.versionConfigHash().equals(scope.versionConfigHash())
                || !Objects.equals(release.get("runtime_authority_fingerprint"), scope.authorityFingerprint())
                || !Objects.equals(release.get("source_artifact_id"), repair.original().artifactId())
                || !"RUNTIME_GENERATION_DRAIN_INCOMPLETE".equals(release.get("error_message"))
                || !json.write(checkpoint.coldMaintenanceReceipt()).equals(repair.finalNativeInventory().get("coldMaintenanceReceipt")))
                fail("VERIFIED_COLD_MAINTENANCE_REQUIRED");
            inventory.put("coldMaintenanceRepair", json.write(repair));
            inventory.put("coldMaintenanceCheckpoint", json.fingerprint(checkpoint));
        }
        requireProgress(tree(release.get("provisioning_details_json")), cold);
        inventory.put("release", json.fingerprint(release));

        var bindings = rows("SELECT * FROM deployment_admission_release_bindings WHERE deployment_id=? AND target_profile_id=? ORDER BY release_id FOR UPDATE NOWAIT",
            r.deploymentId(), r.targetProfileId());
        var current = exact(bindings, "release_id", releaseId);
        requireBinding(current, r, manifest.versionId(), manifest.versionConfigHash(), "OUTCOME_UNKNOWN");
        var active = bindings.stream().filter(b -> "ACTIVE".equals(b.get("state"))).toList();
        if (unassigned ? !active.isEmpty() : active.size() != 1) fail("PRIOR_ACTIVE_RELEASE_REQUIRED");
        var priorBinding = unassigned ? exact(bindings, "release_id", coldPrior.releaseId()) : active.getFirst();
        String priorId = (String) priorBinding.get("release_id");
        if (cold && (!priorId.equals(checkpoint.priorRuntime().releaseId())
            || !priorId.equals(repair.scope().priorRuntimeReleaseId()))) fail("COLD_PRIOR_BINDING_CHANGED");
        var priorVersion = one("SELECT * FROM platform_deployment_versions WHERE id=? FOR UPDATE NOWAIT", priorVersionId);
        if (!r.deploymentId().equals(priorVersion.get("deployment_id")) || !"PUBLISHED".equals(priorVersion.get("status"))) fail("PRIOR_VERSION_CHANGED");
        requireBinding(priorBinding, r, priorVersionId, (String) priorVersion.get("config_hash"), unassigned ? "SUPERSEDED" : "ACTIVE");
        var prior = one("SELECT * FROM platform_deployment_releases WHERE id=? FOR UPDATE NOWAIT", priorId);
        if (!r.deploymentId().equals(prior.get("deployment_id")) || !r.targetProfileId().equals(prior.get("target_profile_id"))
            || !priorVersionId.equals(prior.get("deployment_version_id")) || !"COOLIFY".equals(prior.get("provider_type"))
            || !(unassigned ? "APPLIED_VERIFICATION_FAILED" : "APPLIED_VERIFIED").equals(prior.get("status")) || !"ACTIVE".equals(prior.get("provisioning_status"))
            || !(unassigned ? "FAILED" : "PASSED").equals(prior.get("verification_status")) || prior.get("applied_at") == null
            || !instant(prior.get("created_at")).isBefore(instant(release.get("created_at")))) fail("PRIOR_VERIFIED_RELEASE_REQUIRED");
        if (bindings.stream().anyMatch(b -> !releaseId.equals(b.get("release_id")) && !priorId.equals(b.get("release_id"))
            && !List.of("NO_EFFECT", "SUPERSEDED").contains(b.get("state")))) fail("OTHER_RELEASE_UNSETTLED");
        var allReleases = rows("SELECT id FROM platform_deployment_releases WHERE deployment_id=? AND (target_profile_id=? OR target_profile_id IS NULL) ORDER BY id FOR UPDATE NOWAIT",
            r.deploymentId(), r.targetProfileId());
        if (!allReleases.stream().map(v -> v.get("id")).toList().equals(bindings.stream().map(v -> v.get("release_id")).toList())) fail("RELEASE_SET_CHANGED");
        inventory.put("bindings", json.fingerprint(bindings.stream().map(b -> {
            var stable = new TreeMap<>(b); stable.remove("updated_at"); return stable;
        }).toList()));
        inventory.put("priorRelease", json.fingerprint(prior)); inventory.put("priorVersion", json.fingerprint(priorVersion));
        authorization(inventory, "authorization", current, r, manifest);
        var priorAuthorization = one("SELECT * FROM deployment_admission_authorizations WHERE reservation_id=? AND operation_key=? FOR UPDATE NOWAIT",
            r.id(), priorBinding.get("authorization_key"));
        var priorReceipt = json.read((String) priorAuthorization.get("payload_json"), AuthorizationReceipt.class);
        if (priorReceipt.command() == null) fail("PRIOR_AUTHORIZATION_CHANGED");
        var priorManifestRow = one("SELECT * FROM deployment_admission_reviews WHERE kind='MANIFEST' AND record_key=? FOR UPDATE NOWAIT",
            priorReceipt.command().manifestId());
        var priorManifest = json.read((String) priorManifestRow.get("payload_json"), Manifest.class);
        if (!json.fingerprint(priorManifest).equals(priorManifestRow.get("fingerprint"))
            || !r.demand().equals(priorManifest.demand()) || !priorVersionId.equals(priorManifest.versionId())
            || !priorVersion.get("config_hash").equals(priorManifest.versionConfigHash())) fail("FULL_DEMAND_CHANGED");
        authorization(inventory, "priorAuthorization", priorBinding, r, priorManifest);
        inventory.put("priorManifest", json.fingerprint(priorManifestRow));

        var handles = rows("SELECT * FROM deployment_provider_resource_handles WHERE deployment_id=? AND target_profile_id=? ORDER BY id FOR UPDATE NOWAIT", r.deploymentId(), r.targetProfileId());
        var nativeHandles = handles.stream().filter(h -> !PreservedExternalBindingKinds.isPreserved((String) h.get("resource_kind"))).toList();
        if (!nativeHandles.stream().map(h -> (String) h.get("resource_kind")).sorted().toList()
            .equals(List.of("APPLICATION", "CONNECTOR_APPLICATION", "RUNTIME_POSTGRES_DATABASE"))) fail("NATIVE_INVENTORY_CHANGED");
        var identities = new java.util.HashSet<String>();
        for (var handle : handles) {
            String kind = (String) handle.get("resource_kind");
            if (!r.targetProfileId().equals(handle.get("target_profile_id"))) fail("NATIVE_SCOPE_CHANGED");
            if (PreservedExternalBindingKinds.isPreserved(kind)) {
                if (handle.get("release_id") != null) fail("EXTERNAL_BINDING_CHANGED");
            } else if (!"COOLIFY".equals(handle.get("provider_type"))
                || !("APPLICATION".equals(kind) ? priorId : releaseId).equals(handle.get("release_id"))
                || !text(handle.get("provider_resource_uuid")) || !identities.add((String) handle.get("provider_resource_uuid"))) fail("NATIVE_OWNERSHIP_CHANGED");
        }
        inventory.put("handles", json.fingerprint(handles));
        inventory.put("nativeKinds", json.write(List.of("APPLICATION", "CONNECTOR_APPLICATION", "RUNTIME_GENERATION", "RUNTIME_POSTGRES_DATABASE")));
        var consumers = rows("SELECT * FROM platform_consumers WHERE bound_deployment_id=? ORDER BY id FOR UPDATE NOWAIT", r.deploymentId());
        if ((!unassigned && consumers.isEmpty()) || consumers.stream().anyMatch(c -> !r.customerId().equals(c.get("customer_id"))
            || (unassigned ? r.targetProfileId().equals(c.get("bound_target_profile_id")) || priorId.equals(c.get("bound_release_id"))
                || releaseId.equals(c.get("bound_release_id"))
                : !priorId.equals(c.get("bound_release_id")) || !r.targetProfileId().equals(c.get("bound_target_profile_id"))))) fail("PRIOR_CONSUMER_CHANGED");
        inventory.put("consumers", json.fingerprint(consumers));
        var history = rows("SELECT * FROM platform_consumer_binding_history WHERE to_deployment_id=? OR from_deployment_id=? ORDER BY id FOR UPDATE NOWAIT", r.deploymentId(), r.deploymentId());
        if (unassigned && history.stream().anyMatch(row -> priorId.equals(row.get("to_release_id")) || priorId.equals(row.get("from_release_id"))
            || releaseId.equals(row.get("to_release_id")) || releaseId.equals(row.get("from_release_id")))) fail("PRIOR_CONSUMER_CHANGED");
        inventory.put("consumerHistory", json.fingerprint(history));
        inventory.put("installations", json.fingerprint(rows("SELECT i.* FROM ai_workspace_installations i JOIN platform_consumers c ON c.id=i.consumer_entity_id WHERE c.bound_deployment_id=? ORDER BY i.id FOR UPDATE OF i NOWAIT", r.deploymentId())));
        requireInbox(inventory, r, release, manifest, cold);

        jdbc.queryForObject("SELECT id FROM provider_usage_grant_mutex WHERE id=1 FOR UPDATE NOWAIT", Integer.class);
        var allocation = one("SELECT * FROM provider_usage_allocations WHERE deployment_id=? AND target_profile_id=? FOR UPDATE NOWAIT", r.deploymentId(), r.targetProfileId());
        var terms = tree(allocation.get("payload_json"));
        if (!r.customerId().equals(allocation.get("customer_id")) || !"ACTIVE".equals(allocation.get("initialization_state"))
            || !text(allocation.get("store_uuid")) || allocation.get("initialized_at") == null || allocation.get("activated_at") == null
            || !Objects.equals(allocation.get("runtime_allocation_fingerprint"), allocation.get("initialization_fingerprint"))
            || !r.id().equals(terms.path("initialAdmission").path("reservationId").asText())
            || !r.generation().equals(terms.path("initialAdmission").path("generation").asText())
            || !priorVersionId.equals(terms.path("initialAdmission").path("versionId").asText())) fail("ACTIVE_SAME_ALLOWANCE_REQUIRED");
        inventory.put("allocation", json.fingerprint(allocation));
        inventory.put("grant", json.fingerprint(one("SELECT * FROM provider_usage_grants WHERE id=? FOR UPDATE NOWAIT", allocation.get("grant_id"))));
        inventory.put("financialEvents", json.fingerprint(rows("SELECT * FROM provider_usage_allowance_events WHERE subject_id=? OR grant_id=? ORDER BY id FOR UPDATE NOWAIT", allocation.get("approval_id"), allocation.get("grant_id"))));
        inventory.put("validityReviews", json.fingerprint(rows("SELECT * FROM provider_usage_validity_reviews WHERE approval_id=? ORDER BY extension_id FOR UPDATE NOWAIT", allocation.get("approval_id"))));
        inventory.put("validityEvents", json.fingerprint(rows("SELECT e.* FROM provider_usage_validity_events e JOIN provider_usage_validity_reviews v ON v.extension_id=e.extension_id WHERE v.approval_id=? ORDER BY e.extension_id,e.event_type FOR UPDATE OF e NOWAIT", allocation.get("approval_id"))));
        for (String table : List.of("platform_target_retirement_operations", "platform_deployment_deletion_operations")) {
            if (!rows("SELECT id FROM " + table + " WHERE deployment_id=? FOR UPDATE NOWAIT", r.deploymentId()).isEmpty()) fail("LIFECYCLE_OPERATION_PRESENT");
        }
        inventory.put("admissionEvents", json.fingerprint(rows("SELECT * FROM deployment_admission_events WHERE reservation_id=? ORDER BY revision FOR UPDATE NOWAIT", r.id())));
        inventory.put("reapplyScope", json.write(new FailedApplyRetryContracts.ReapplyScope(priorId, instant(prior.get("created_at")), (String) deployment.get("runtime_base_url"))));
        return new FailedApplyRetryContracts.Candidate(r, Map.copyOf(inventory), json.write(release),
            instant(release.get("created_at")), instant(release.get("updated_at")), List.of((String) allocation.get("approval_id")));
    }

    private void requireInbox(Map<String, String> inventory, Reservation r, Map<String, Object> release, Manifest manifest, boolean cold) {
        var graph = one("SELECT * FROM deployment_managed_dependency_graphs WHERE deployment_id=? AND target_profile_id=? FOR UPDATE NOWAIT", r.deploymentId(), r.targetProfileId());
        if (!r.customerId().equals(graph.get("customer_id")) || !r.tenantId().equals(graph.get("tenant_id"))
            || !release.get("id").equals(graph.get("release_id")) || !manifest.versionId().equals(graph.get("version_id"))
            || !Objects.equals(release.get("runtime_authority_fingerprint"), graph.get("binding_fingerprint"))
            || !r.id().equals(graph.get("reservation_ref")) || graph.get("worker_token") != null || graph.get("failure_code") != null
            || !"PROVISIONED".equals(graph.get("status")) || !"RUNTIME_INITIALIZATION_REQUIRED".equals(graph.get("step"))) fail("PREPARED_GRAPH_REQUIRED");
        var reviewed = one("SELECT * FROM deployment_managed_dependency_specs WHERE id=? FOR UPDATE NOWAIT", graph.get("spec_ref"));
        if (!Boolean.TRUE.equals(reviewed.get("active")) || reviewed.get("reviewed_at") == null || !text(reviewed.get("reviewed_by"))
            || !Objects.equals(reviewed.get("fingerprint"), graph.get("spec_fingerprint"))
            || !tree(reviewed.get("spec_json")).equals(tree(graph.get("spec_json")))) fail("REVIEWED_SPEC_CHANGED");
        var spec = json.read((String) graph.get("spec_json"), ManagedDependencySpec.class);
        var state = json.read((String) graph.get("state_json"), ManagedDependencyState.class);
        if (spec.services() == null || spec.services().size() != 1 || state.nodes.size() != 1
            || spec.services().getFirst().contract() != ManagedDependencySpec.Contract.BASIC_INBOX_KEYS_V1
            || spec.services().getFirst().database() == null || state.configurationRevision < 1 || state.effectiveConfigurationRevision != -1
            || !cold && (state.runtimeGenerationOperationKey != null || state.runtimeGenerationState != null || !state.runtimeGenerationStoppedSlots.isEmpty())
            || state.retirementFingerprint != null || state.operatorRevocationOperationKey != null || state.operatorRevocationOperatorId != null
            || state.operatorRevocationLifecycleKey != null || !state.retiredOperatorSecretNames.isEmpty()) fail("INBOX_GENERATION_ALREADY_STARTED");
        var inbox = state.nodes.getFirst();
        if (!spec.services().getFirst().slot().equals(inbox.slot) || !text(inbox.serviceRef) || !text(inbox.accountId) || !text(inbox.bindingId)
            || !"PLANNED".equals(inbox.status) || inbox.initialized || inbox.disabled || !text(inbox.applicationUuid) || !text(inbox.databaseUuid)
            || !text(inbox.baseUrl) || !inbox.applicationCreateIntent || !inbox.databaseCreateIntent
            || inbox.provisionedConfigurationRevision < 0 || inbox.provisionedConfigurationRevision >= state.configurationRevision
            || inbox.retirementOperationKey != null || !inbox.volumeReclamationPlan.isEmpty() || !inbox.reclaimedVolumeUuids.isEmpty()) fail("RETAINED_INBOX_REQUIRED");
        if (state.operators.isEmpty() || state.operators.stream().anyMatch(o -> !inbox.slot.equals(o.slot)
            || !"ENROLLED".equals(o.status) || o.effectiveAt == null || !text(o.id) || !text(o.secretName) || o.credentialRevision < 1)) fail("RETAINED_OPERATOR_REQUIRED");
        var ownership = rows("SELECT * FROM platform_managed_resource_ownership WHERE deployment_id=? AND target_profile_id=? ORDER BY id FOR UPDATE NOWAIT", r.deploymentId(), r.targetProfileId());
        owned(ownership, r, inbox.applicationUuid, "DEPENDENCY_APPLICATION", inbox.serviceRef);
        owned(ownership, r, inbox.databaseUuid, "RUNTIME_POSTGRES_DATABASE", inbox.serviceRef + "-db");
        if (ownership.stream().anyMatch(o -> !"OWNED".equals(o.get("state")))) fail("DEPENDENCY_OWNERSHIP_UNPROVEN");
        inventory.put("graph", json.fingerprint(graph)); inventory.put("spec", json.fingerprint(reviewed));
        if (cold) {
            var repair = json.read(inventory.get("coldMaintenanceRepair"), RuntimeArtifactRepairContracts.VerifiedRepair.class);
            if (!Objects.equals(repair.finalNativeInventory().get("dependencyGraph"), inventory.get("graph"))
                || !"QUIESCED".equals(state.runtimeGenerationState)
                || !state.runtimeGenerationStoppedSlots.equals(List.of(inbox.slot))) fail("COLD_GRAPH_CHANGED");
        }
        inventory.put("dependencyOwnership", json.fingerprint(ownership));
    }

    private void owned(List<Map<String, Object>> ownership, Reservation r, String uuid, String kind, String name) {
        var receipt = exact(ownership, "resource_identity", uuid);
        if (!"OWNED".equals(receipt.get("state")) || !"coolify".equals(receipt.get("provider"))
            || !kind.equals(receipt.get("resource_kind")) || !name.equals(receipt.get("resource_name"))
            || !r.customerId().equals(receipt.get("customer_id")) || !r.tenantId().equals(receipt.get("tenant_id"))) fail("DEPENDENCY_OWNERSHIP_UNPROVEN");
    }
    private void authorization(Map<String, String> inventory, String key, Map<String, Object> binding, Reservation r, Manifest manifest) {
        var row = one("SELECT * FROM deployment_admission_authorizations WHERE reservation_id=? AND operation_key=? FOR UPDATE NOWAIT", r.id(), binding.get("authorization_key"));
        var receipt = json.read((String) row.get("payload_json"), AuthorizationReceipt.class);
        if (!r.id().equals(receipt.reservationId()) || !r.generation().equals(receipt.generation())
            || !json.fingerprint(manifest).equals(receipt.manifestFingerprint()) || receipt.command() == null
            || !manifest.id().equals(receipt.command().manifestId()) || !binding.get("authorization_key").equals(receipt.command().operationKey())
            || !Objects.equals(row.get("request_fingerprint"), receipt.requestFingerprint())) fail("AUTHORIZATION_CHANGED");
        inventory.put(key, json.fingerprint(row));
    }
    private void requireBinding(Map<String, Object> binding, Reservation r, String version, String hash, String state) {
        for (var expected : Map.of("reservation_id", r.id(), "generation", r.generation(), "customer_id", r.customerId(),
            "tenant_id", r.tenantId(), "deployment_id", r.deploymentId(), "target_profile_id", r.targetProfileId(),
            "version_id", version, "version_config_hash", hash, "state", state).entrySet()) {
            if (!expected.getValue().equals(binding.get(expected.getKey()))) fail("BINDING_CHANGED");
        }
    }
    private void requireProgress(JsonNode details, boolean cold) {
        var progress = details.path("progress"); var steps = progress.path("steps");
        var fields = new java.util.HashSet<String>(); details.fieldNames().forEachRemaining(fields::add);
        if (!details.isObject() || !(cold ? java.util.Set.of("progress", "retryHistory").containsAll(fields) : details.size() == 1)
            || !"apply_failed".equals(progress.path("currentStepKey").asText())
            || !"FAILED".equals(progress.path("currentStepStatus").asText()) || !steps.isArray() || steps.size() != STEPS.size()) fail("CONTROL_FLOW_UNPROVEN");
        for (int i = 0; i < STEPS.size(); i++) {
            String key = STEPS.get(i), status = switch (key) {
                case "queue_release" -> "QUEUED"; case "prepare_apply" -> "RUNNING"; case "apply_failed" -> "FAILED"; default -> "COMPLETED";
            };
            if (!key.equals(steps.get(i).path("key").asText()) || !status.equals(steps.get(i).path("status").asText())) fail("CONTROL_FLOW_UNPROVEN");
        }
    }
    private Map<String, Object> exact(List<Map<String, Object>> rows, String field, Object value) {
        var matches = rows.stream().filter(row -> Objects.equals(value, row.get(field))).toList();
        if (matches.size() != 1) fail("EXACT_SCOPE_REQUIRED"); return matches.getFirst();
    }
    private List<Map<String, Object>> rows(String sql, Object... args) { return jdbc.queryForList(sql, args); }
    private Map<String, Object> one(String sql, Object... args) { var values = rows(sql, args); if (values.size() != 1) fail("EXACT_SCOPE_REQUIRED"); return values.getFirst(); }
    private JsonNode tree(Object value) { return json.read((String) value, JsonNode.class); }
    private java.time.Instant instant(Object value) { return ((Timestamp) value).toInstant(); }
    private boolean text(Object value) { return value instanceof String string && !string.isBlank(); }
    private void fail(String code) { throw new AdmissionFailure(CONFLICT, "REAPPLY_RETRY_" + code); }
}
