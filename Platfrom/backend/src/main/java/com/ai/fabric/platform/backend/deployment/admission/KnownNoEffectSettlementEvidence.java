package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.deployment.model.PreservedExternalBindingKinds;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static com.ai.fabric.platform.backend.deployment.admission.KnownNoEffectSettlementContracts.*;
import static org.springframework.http.HttpStatus.CONFLICT;

/** Local trusted ledger proof, collected under the settlement transaction. No provider or secret resolution. */
final class KnownNoEffectSettlementEvidence {
    private static final Set<String> PRE_PROVIDER_STEPS = Set.of("queue_release", "preflight_verification", "apply_failed");
    private final JdbcTemplate jdbc;
    private final AdmissionJson json;
    private final com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationIntegrity integrationIntegrity;

    KnownNoEffectSettlementEvidence(JdbcTemplate jdbc, AdmissionJson json,
        com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationIntegrity integrationIntegrity) {
        this.jdbc = jdbc; this.json = json; this.integrationIntegrity = integrationIntegrity;
    }

    Proof read(Reservation r, Command command, List<AuthorizationReceipt> authorizations) {
        String d = r.deploymentId(), t = r.targetProfileId();
        var inventory = new TreeMap<String, String>();
        var profiles = rows("SELECT * FROM deployment_target_profiles WHERE id=? FOR UPDATE NOWAIT", t);
        if (profiles.size() != 1 || !Boolean.TRUE.equals(profiles.getFirst().get("active"))) throw denied("TARGET_IDENTITY_CHANGED");
        Object provider = profiles.getFirst().get("provider_type");
        var deployments = rows("SELECT * FROM platform_deployments WHERE id=?", d);
        if (deployments.size() != 1) throw denied("OWNERSHIP_CHANGED");
        var deployment = deployments.getFirst();
        if (!r.customerId().equals(deployment.get("customer_id")) || !r.tenantId().equals(deployment.get("tenant_id"))
            || deployment.get("active_version_id") != null || deployment.get("archived_at") != null
            || nonblank(deployment.get("runtime_base_url")) || nonblank(deployment.get("connector_base_url")))
            throw denied("ACTIVATION_OR_OWNERSHIP_HISTORY");

        var releases = rows("SELECT * FROM platform_deployment_releases WHERE deployment_id=? AND (target_profile_id=? OR target_profile_id IS NULL) ORDER BY id FOR UPDATE NOWAIT", d, t);
        var expectedIds = command.failedReleases().stream().map(FailedRelease::releaseId).toList();
        if (!releases.stream().map(row -> (String) row.get("id")).toList().equals(expectedIds))
            throw denied("RELEASE_SET_CHANGED");
        var bindings = rows("SELECT * FROM deployment_admission_release_bindings WHERE reservation_id=? OR (deployment_id=? AND target_profile_id=?) ORDER BY release_id FOR UPDATE NOWAIT", r.id(), d, t);
        if (!bindings.stream().map(row -> (String) row.get("release_id")).toList().equals(expectedIds)
            || authorizations.size() != expectedIds.size()) throw denied("AUTHORIZATION_OR_BINDING_SET_CHANGED");
        var releaseProofs = new ArrayList<RecordProof>();
        var authorizationProofs = new ArrayList<RecordProof>();
        for (int i = 0; i < releases.size(); i++) {
            var release = releases.get(i); var binding = bindings.get(i); var expected = command.failedReleases().get(i);
            boolean failed = "FAILED".equals(release.get("status")) && "FAILED".equals(release.get("provisioning_status"));
            boolean blocked = "PRE_APPLY_BLOCKED".equals(release.get("status")) && "BLOCKED".equals(release.get("provisioning_status"));
            if ((!failed && !blocked) || !t.equals(release.get("target_profile_id"))
                || !command.versionId().equals(release.get("deployment_version_id"))
                || !Objects.equals(provider, release.get("provider_type")) || release.get("provider_resource_handle_id") != null)
                throw denied("TERMINAL_PRE_PROVIDER_RELEASE_REQUIRED");
            for (var entry : Map.of("reservation_id", r.id(), "generation", r.generation(), "customer_id", r.customerId(),
                "tenant_id", r.tenantId(), "deployment_id", d, "target_profile_id", t,
                "version_id", command.versionId(), "version_config_hash", command.versionConfigHash(),
                "authorization_key", expected.authorizationOperationKey(), "state", "NO_EFFECT").entrySet()) {
                if (!entry.getValue().equals(binding.get(entry.getKey()))) throw denied("TRUSTED_NO_EFFECT_BINDING_REQUIRED");
            }
            var selected = authorizations.stream().filter(a -> expected.authorizationOperationKey().equals(a.command().operationKey())).toList();
            if (selected.size() != 1 || !expected.authorizationRequestFingerprint().equals(selected.getFirst().requestFingerprint()))
                throw denied("AUTHORIZATION_CHANGED");
            requirePreProviderProgress(release);
            releaseProofs.add(new RecordProof(expected.releaseId(), json.fingerprint(Map.of("release", release, "binding", binding))));
            authorizationProofs.add(new RecordProof(expected.authorizationOperationKey(), json.fingerprint(selected.getFirst())));
        }
        var events = rows("SELECT * FROM deployment_admission_events WHERE reservation_id=? ORDER BY revision FOR UPDATE NOWAIT", r.id());
        if (events.size() != r.revision() + 1 || events.stream().anyMatch(row -> !Set.of(
            "RESERVED", "INITIAL_EVIDENCE_RENEWED", "ALLOCATION_OPERATION_AUTHORIZED", "ACTIVITY_CONFIGURATION_WRITE").contains(row.get("event_type"))))
            throw denied("EXECUTION_HISTORY_NOT_PRE_PROVIDER");
        for (int i = 0; i < events.size(); i++) if (((Number) events.get(i).get("revision")).longValue() != i)
            throw denied("EXECUTION_HISTORY_NOT_PRE_PROVIDER");
        inventory.put("admissionEvents", json.fingerprint(events));

        absent(inventory, "activity", "SELECT * FROM deployment_admission_activity_watermarks WHERE reservation_id=?", r.id());
        absent(inventory, "managedGraphs", "SELECT * FROM deployment_managed_dependency_graphs WHERE deployment_id=? AND target_profile_id=?", d, t);
        absent(inventory, "providerAllocations", "SELECT * FROM provider_usage_allocations WHERE deployment_id=? AND target_profile_id=?", d, t);
        absent(inventory, "consumerReleaseAssignments", "SELECT * FROM platform_consumers WHERE bound_release_id IN (SELECT id FROM platform_deployment_releases WHERE deployment_id=? AND target_profile_id=?)", d, t);
        absent(inventory, "retirement", "SELECT * FROM platform_target_retirement_operations WHERE deployment_id=? AND target_profile_id=?", d, t);
        absent(inventory, "deletion", "SELECT * FROM platform_deployment_deletion_operations WHERE deployment_id=?", d);
        absent(inventory, "vectorizationRuns", "SELECT * FROM platform_vectorization_runs WHERE deployment_id=?", d);
        absent(inventory, "runnerSessions", "SELECT * FROM platform_vectorization_runner_sessions WHERE deployment_id=?", d);
        absent(inventory, "indexedPlans", "SELECT * FROM platform_vectorization_plans WHERE deployment_id=? AND (active_indexed_output_hash IS NOT NULL OR last_successful_indexed_output_hash IS NOT NULL OR last_run_id IS NOT NULL OR last_successful_run_id IS NOT NULL)", d);
        absent(inventory, "managedVectors", "SELECT * FROM platform_deployment_managed_vector_resources WHERE deployment_id=?", d);
        absent(inventory, "targetIndexes", "SELECT * FROM platform_tenant_scoped_vector_resources WHERE deployment_id=? AND (target_profile_id=? OR target_profile_id IS NULL)", d, t);
        var gates = rows("SELECT * FROM platform_target_retirement_gates WHERE deployment_id=? AND target_profile_id=? FOR UPDATE NOWAIT", d, t);
        // Configuration mutations also advance this fence; retirement/effect evidence is checked separately.
        if (gates.size() > 1 || gates.stream().anyMatch(row -> !"AVAILABLE".equals(row.get("state"))
            || ((Number) row.get("epoch")).longValue() < 0 || row.get("operation_id") != null)) throw denied("RETIREMENT_GATE_CHANGED");
        inventory.put("targetGate", json.fingerprint(gates));

        jdbc.queryForObject("SELECT id FROM provider_usage_grant_mutex WHERE id=1 FOR UPDATE NOWAIT", Integer.class);
        var grants = rows("SELECT * FROM provider_usage_grants WHERE customer_id=? AND target_profile_id=? ORDER BY id FOR UPDATE NOWAIT", r.customerId(), t);
        var allocations = rows("SELECT * FROM provider_usage_allocations WHERE customer_id=? AND target_profile_id=? ORDER BY approval_id FOR UPDATE NOWAIT", r.customerId(), t);
        for (var allocation : allocations) {
            JsonNode terms = tree(allocation.get("payload_json"));
            if (r.id().equals(terms.path("initialAdmission").path("reservationId").asText())
                || d.equals(terms.path("scope").path("deploymentId").asText())) throw denied("FUNDING_EFFECT_HISTORY");
        }
        var financialEvents = rows("SELECT e.* FROM provider_usage_allowance_events e WHERE e.grant_id IN (SELECT id FROM provider_usage_grants WHERE customer_id=? AND target_profile_id=?) OR e.subject_id=? OR e.subject_id=? OR e.subject_id IN (SELECT id FROM platform_deployment_releases WHERE deployment_id=? AND target_profile_id=?) ORDER BY e.id FOR UPDATE NOWAIT",
            r.customerId(), t, r.id(), d, d, t);
        for (var event : financialEvents) {
            if (Set.of("GRANT_APPROVED", "GRANT_VOID_UNUSED").contains(event.get("event_type"))) {
                if (grants.stream().noneMatch(g -> Objects.equals(g.get("id"), event.get("grant_id"))
                    && Objects.equals(g.get("id"), event.get("subject_id")))) throw denied("FUNDING_HISTORY_UNRESOLVED");
                continue;
            }
            var other = allocations.stream().filter(a -> Objects.equals(a.get("approval_id"), event.get("subject_id"))
                && Objects.equals(a.get("grant_id"), event.get("grant_id"))).toList();
            if (other.size() != 1) throw denied("FUNDING_HISTORY_UNRESOLVED");
            JsonNode terms = tree(other.getFirst().get("payload_json"));
            if (!Objects.equals(other.getFirst().get("deployment_id"), terms.path("scope").path("deploymentId").textValue())
                || !Objects.equals(other.getFirst().get("approval_id"), terms.path("approvalId").textValue())
                || !Objects.equals(other.getFirst().get("grant_id"), terms.path("grantId").textValue())
                || !Set.of("ALLOWANCE_ALLOCATED", "ALLOWANCE_INITIALIZED", "ALLOWANCE_ACTIVE").contains(event.get("event_type")))
                throw denied("FUNDING_HISTORY_UNRESOLVED");
        }
        // Other deployments may spend a shared grant. This operation changes no counters or financial history.
        inventory.put("preservedFinancialScope", json.fingerprint(Map.of("grants", grants, "allocations", allocations, "events", financialEvents)));

        // Queuing freezes nonsecret authority metadata before provisioning. It is retained, never treated as activation.
        var snapshots = rows("SELECT * FROM platform_runtime_authority_snapshots WHERE deployment_id=? AND target_profile_id=? ORDER BY id FOR UPDATE NOWAIT", d, t);
        for (var snapshot : snapshots) {
            JsonNode scope = tree(snapshot.get("binding_json"));
            if (!r.customerId().equals(scope.path("customerId").asText()) || !r.tenantId().equals(scope.path("tenantId").asText())
                || !d.equals(scope.path("deploymentId").asText()) || !t.equals(scope.path("targetProfileId").asText())
                || !command.versionId().equals(scope.path("versionId").asText())
                || !command.versionConfigHash().equals(scope.path("versionConfigHash").asText())
                || !scope.path("generation").isIntegralNumber() || scope.path("generation").longValue() <= 0
                || snapshot.get("release_id") != null && !expectedIds.contains(snapshot.get("release_id"))) throw denied("AUTHORITY_HISTORY_AMBIGUOUS");
            if (snapshot.get("release_id") != null) {
                var release = releases.stream().filter(row -> snapshot.get("release_id").equals(row.get("id"))).findFirst().orElseThrow();
                if (!Objects.equals(snapshot.get("id"), release.get("runtime_authority_snapshot_id"))
                    || !Objects.equals(snapshot.get("fingerprint"), release.get("runtime_authority_fingerprint"))) throw denied("AUTHORITY_HISTORY_AMBIGUOUS");
            }
            // This owning subsystem hashes its exact encoded binding, not admission's sorted JSON projection.
            if (!snapshot.get("fingerprint").equals(rawSha256((String) snapshot.get("binding_json")))) throw denied("AUTHORITY_HISTORY_AMBIGUOUS");
        }
        for (var release : releases) {
            if (release.get("runtime_authority_snapshot_id") == null && release.get("runtime_authority_fingerprint") == null) continue;
            if (snapshots.stream().noneMatch(s -> release.get("id").equals(s.get("release_id"))
                && Objects.equals(release.get("runtime_authority_snapshot_id"), s.get("id"))
                && Objects.equals(release.get("runtime_authority_fingerprint"), s.get("fingerprint")))) throw denied("AUTHORITY_HISTORY_AMBIGUOUS");
        }
        inventory.put("preservedAuthoritySnapshots", json.fingerprint(snapshots));
        var authorities = rows("SELECT * FROM platform_runtime_target_authorities WHERE deployment_id=? AND target_profile_id=? ORDER BY id FOR UPDATE NOWAIT", d, t);
        if (authorities.size() > 1 || authorities.stream().anyMatch(row -> !r.customerId().equals(row.get("customer_id"))
            || !r.tenantId().equals(row.get("tenant_id")) || snapshots.stream().noneMatch(s ->
                ((Number) row.get("generation")).longValue() == tree(s.get("binding_json")).path("generation").longValue())))
            throw denied("AUTHORITY_HISTORY_AMBIGUOUS");
        inventory.put("preservedAuthorities", json.fingerprint(authorities));
        var integrations = rows("SELECT * FROM deployment_external_integration_snapshots WHERE deployment_id=? AND target_profile_id=? ORDER BY release_id FOR UPDATE NOWAIT", d, t);
        for (var snapshot : integrations) {
            var release = releases.stream().filter(row -> snapshot.get("release_id").equals(row.get("id"))).findFirst()
                .orElseThrow(() -> denied("INTEGRATION_SNAPSHOT_SCOPE_CHANGED"));
            JsonNode value = tree(snapshot.get("evidence_json"));
            var typed = json.read((String) snapshot.get("evidence_json"),
                com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationContracts.ReleaseSnapshot.class);
            integrationIntegrity.require(typed, (String) snapshot.get("integrity_mac"));
            if (!command.versionId().equals(snapshot.get("version_id"))
                || !release.get("id").equals(value.path("releaseId").asText())
                || !Objects.equals(release.get("runtime_authority_fingerprint"), value.path("runtimeAuthorityFingerprint").textValue()))
                throw denied("INTEGRATION_SNAPSHOT_SCOPE_CHANGED");
            for (var projection : typed.projections()) {
                var scope = projection.scope();
                if (!r.customerId().equals(scope.customerId()) || !r.tenantId().equals(scope.tenantId())
                    || !d.equals(scope.deploymentId()) || !t.equals(scope.targetProfileId())
                    || !command.versionId().equals(scope.versionId())) throw denied("INTEGRATION_SNAPSHOT_SCOPE_CHANGED");
            }
        }
        inventory.put("preservedIntegrationSnapshots", json.fingerprint(integrations));
        for (var release : releases) {
            Object raw = release.get("ai_state_storage_snapshot_json"), fingerprint = release.get("ai_state_storage_fingerprint");
            if (raw == null && fingerprint == null) continue;
            if (raw == null || fingerprint == null) throw denied("AI_STATE_SNAPSHOT_SCOPE_CHANGED");
            JsonNode value = tree(raw);
            if (!r.customerId().equals(value.path("customerId").asText()) || !r.tenantId().equals(value.path("tenantId").asText())
                || !d.equals(value.path("deploymentId").asText()) || !t.equals(value.path("targetProfileId").asText())
                || !release.get("id").equals(value.path("releaseId").asText()) || !command.versionId().equals(value.path("versionId").asText())
                || !command.versionConfigHash().equals(value.path("configHash").asText()) || !fingerprint.equals(json.fingerprint(value)))
                throw denied("AI_STATE_SNAPSHOT_SCOPE_CHANGED");
        }

        var retained = new ArrayList<RecordProof>();
        for (var handle : rows("SELECT * FROM deployment_provider_resource_handles WHERE deployment_id=? AND (target_profile_id=? OR target_profile_id IS NULL) ORDER BY id FOR UPDATE NOWAIT", d, t)) {
            if (!PreservedExternalBindingKinds.isPreserved((String) handle.get("resource_kind"))
                || handle.get("release_id") != null || !t.equals(handle.get("target_profile_id"))) throw denied("NATIVE_OR_AMBIGUOUS_RESOURCE_HISTORY");
            // Preserve the entire row byte-for-byte: no deletion, status change or implied external absence.
            retained.add(new RecordProof((String) handle.get("id"), json.fingerprint(handle)));
        }
        return new Proof("ADMISSION_QUEUED_TO_NO_EFFECT_BEFORE_RELEASE_PROVISIONING_V1", releaseProofs,
            authorizationProofs, retained, inventory);
    }

    void requireDetachedConsumers(Reservation r, List<UnusedConsumerDetachment> requests,
                                  List<UnusedConsumerDetachmentReceipt> receipts) {
        if (requests.size() != receipts.size()) throw denied("CONSUMER_DETACHMENT_UNPROVEN");
        if (!rows("SELECT id FROM platform_consumers WHERE bound_deployment_id=? AND (bound_target_profile_id=? OR bound_target_profile_id IS NULL) FOR UPDATE NOWAIT",
            r.deploymentId(), r.targetProfileId()).isEmpty()) throw denied("CONSUMER_ASSIGNMENT_REMAINS");
        var allowedHistory = new java.util.HashSet<String>();
        for (int i = 0; i < requests.size(); i++) {
            var expected = requests.get(i); var receipt = receipts.get(i);
            if (!expected.consumerEntityId().equals(receipt.consumerEntityId()) || receipt.fromBindingRevision() != 1
                || receipt.toBindingRevision() != 2 || !expected.draftWorkspaces().equals(receipt.preservedDraftWorkspaces()))
                throw denied("CONSUMER_DETACHMENT_UNPROVEN");
            var consumers = rows("SELECT * FROM platform_consumers WHERE id=? FOR UPDATE NOWAIT", expected.consumerEntityId());
            if (consumers.size() != 1 || !r.customerId().equals(consumers.getFirst().get("customer_id"))
                || consumers.getFirst().get("bound_deployment_id") != null || consumers.getFirst().get("bound_release_id") != null
                || consumers.getFirst().get("bound_target_profile_id") != null
                || ((Number) consumers.getFirst().get("binding_revision")).longValue() != 2) throw denied("CONSUMER_DETACHMENT_UNPROVEN");
            var history = rows("SELECT * FROM platform_consumer_binding_history WHERE consumer_entity_id=? ORDER BY to_binding_revision FOR UPDATE NOWAIT", expected.consumerEntityId());
            if (history.size() != 2) throw denied("CONSUMER_DETACHMENT_UNPROVEN");
            var old = history.getFirst(); var latest = history.getLast();
            if (old.get("from_deployment_id") != null || !r.deploymentId().equals(old.get("to_deployment_id"))
                || old.get("to_release_id") != null || latest.get("from_release_id") != null
                || !r.deploymentId().equals(latest.get("from_deployment_id")) || latest.get("to_deployment_id") != null
                || !r.targetProfileId().equals(latest.get("from_target_profile_id")) || latest.get("to_target_profile_id") != null
                || latest.get("to_release_id") != null || !receipt.historyId().equals(latest.get("id"))
                || !receipt.operationKey().equals(latest.get("operation_key"))) throw denied("CONSUMER_DETACHMENT_UNPROVEN");
            allowedHistory.add((String) old.get("id")); allowedHistory.add((String) latest.get("id"));
        }
        for (var history : rows("SELECT id FROM platform_consumer_binding_history WHERE from_deployment_id=? OR to_deployment_id=? FOR UPDATE NOWAIT", r.deploymentId(), r.deploymentId()))
            if (!allowedHistory.contains(history.get("id"))) throw denied("CONSUMER_HISTORY_AMBIGUOUS");
    }

    private void requirePreProviderProgress(Map<String, Object> release) {
        if (!PRE_PROVIDER_STEPS.contains(release.get("current_step_key"))) throw denied("PRE_PROVIDER_PROGRESS_REQUIRED");
        JsonNode details = tree(release.get("provisioning_details_json"));
        if (!details.isObject() || details.size() != 1 || !details.path("progress").isObject()) throw denied("PRE_PROVIDER_PROGRESS_REQUIRED");
        JsonNode progress = details.path("progress"), steps = progress.path("steps");
        if (!steps.isArray() || steps.isEmpty() || steps.size() > PRE_PROVIDER_STEPS.size()
            || !Objects.equals(release.get("current_step_key"), progress.path("currentStepKey").asText())) throw denied("PRE_PROVIDER_PROGRESS_REQUIRED");
        var seen = new java.util.HashSet<String>();
        for (JsonNode step : steps) {
            String key = step.path("key").asText();
            if (!PRE_PROVIDER_STEPS.contains(key) || !seen.add(key)
                || !Set.of("PENDING", "RUNNING", "COMPLETED", "FAILED").contains(step.path("status").asText()))
                throw denied("PRE_PROVIDER_PROGRESS_REQUIRED");
        }
        if (!seen.contains("queue_release") || !seen.contains("preflight_verification")
            || "FAILED".equals(release.get("status")) && !seen.contains("apply_failed")) throw denied("PRE_PROVIDER_PROGRESS_REQUIRED");
    }

    private List<Map<String, Object>> rows(String sql, Object... args) { return jdbc.queryForList(sql, args); }
    private void absent(Map<String, String> inventory, String kind, String sql, Object... args) {
        var found = rows(sql + " FOR UPDATE NOWAIT", args);
        if (!found.isEmpty()) throw denied("EFFECT_OR_ASSIGNMENT_HISTORY_" + kind);
        inventory.put(kind, json.fingerprint(found));
    }
    private JsonNode tree(Object value) { return json.read((String) value, JsonNode.class); }
    private String rawSha256(String value) {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
            .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
    private boolean nonblank(Object value) { return value != null && !value.toString().isBlank(); }
    private AdmissionFailure denied(String code) { return new AdmissionFailure(CONFLICT, "NO_EFFECT_SETTLEMENT_" + code); }
}
