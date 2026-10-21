package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencyState;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpec;
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
        boolean preflightBlocked = "PRE_APPLY_BLOCKED".equals(release.get("status"))
            && "BLOCKED".equals(release.get("provisioning_status")) && "FAILED".equals(release.get("verification_status"))
            && "preflight_verification".equals(release.get("current_step_key"));
        boolean setupFailed = "FAILED".equals(release.get("status")) && "FAILED".equals(release.get("provisioning_status"))
            && "SKIPPED".equals(release.get("verification_status")) && "apply_failed".equals(release.get("current_step_key"));
        if (!r.customerId().equals(deployment.get("customer_id")) || !r.tenantId().equals(deployment.get("tenant_id"))
            || !manifest.versionId().equals(deployment.get("active_version_id")) || deployment.get("archived_at") != null
            || !(preflightBlocked ? "ACTIVE".equals(deployment.get("status")) : "APPLY_FAILED".equals(deployment.get("status")))
            || !r.deploymentId().equals(release.get("deployment_id")) || !r.targetProfileId().equals(release.get("target_profile_id"))
            || !manifest.versionId().equals(release.get("deployment_version_id")) || !"COOLIFY".equals(release.get("provider_type"))
            || !(setupFailed || preflightBlocked)) fail("RELEASE_PHASE_CHANGED");
        var details = tree(release.get("provisioning_details_json"));
        boolean retainedInbox = "COLD_ADOPTED".equals(release.get("runtime_generation_transition_state"));
        if (preflightBlocked && !details.has("postProvisionResume")) fail("CONTROL_FLOW_UNPROVEN");
        var preflightCheckpoints = new java.util.ArrayList<PostProvisionResumeContracts.Candidate>();
        requireCompletedNativePhase(nativePhase(tree(json.write(release)), r, releaseId, manifest, inventory, preflightCheckpoints)
            .path("progress").path("steps"), retainedInbox);
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
        var retained = retainedInbox ? requireRetainedRetry(r, release, manifest, otherBindings, inventory) : null;
        if (!retainedInbox && otherBindings.stream().anyMatch(b -> !releaseId.equals(b.get("release_id")) && !"NO_EFFECT".equals(b.get("state")))) fail("OTHER_RELEASE_UNSETTLED");
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
                "ADMISSION".equals(graph.get("step")) || ("PROVISION:" + node.slot).equals(graph.get("step"))
                    || ("INITIALIZE:" + node.slot).equals(graph.get("step"))
                        && java.util.Set.of("PROVISIONED", "INITIALIZING").contains(node.status))
            || state.effectiveConfigurationRevision != -1
            || state.retirementFingerprint != null || !retainedInbox && (state.runtimeGenerationOperationKey != null || state.runtimeGenerationState != null
                || !state.runtimeGenerationStoppedSlots.isEmpty()) || state.operatorRevocationOperationKey != null
            || state.operatorRevocationOperatorId != null || state.operatorRevocationLifecycleKey != null
            || !state.retiredOperatorSecretNames.isEmpty()) fail("DEPENDENCY_EFFECTS_PRESENT");
        var identities = new java.util.HashSet<String>();
        var ownership = rows("SELECT * FROM platform_managed_resource_ownership WHERE deployment_id=? AND target_profile_id=? ORDER BY id FOR UPDATE NOWAIT",
            r.deploymentId(), r.targetProfileId());
        for (var node : state.nodes) {
            boolean planned = "PLANNED".equals(node.status) && node.baseUrl == null
                && node.provisionedConfigurationRevision == -1;
            boolean provisioned = java.util.Set.of("PROVISIONED", "INITIALIZING").contains(node.status)
                && node.baseUrl != null && !node.baseUrl.isBlank() && node.applicationUuid != null
                && node.provisionedConfigurationRevision == state.configurationRevision;
            if ((!planned && !provisioned) || node.initialized || node.disabled
                || node.applicationCreateIntent != (node.applicationUuid != null)
                || node.databaseCreateIntent != (node.databaseUuid != null)
                || node.retirementOperationKey != null || !node.volumeReclamationPlan.isEmpty()
                || !node.reclaimedVolumeUuids.isEmpty()) fail("DEPENDENCY_EFFECTS_PRESENT");
            requireOwnedCheckpoint(r, node.applicationUuid, "DEPENDENCY_APPLICATION", node.serviceRef, ownership, identities);
            requireOwnedCheckpoint(r, node.databaseUuid, "RUNTIME_POSTGRES_DATABASE", node.serviceRef + "-db", ownership, identities);
        }
        inventory.put("dependencyOwnership", json.fingerprint(ownership));
        if (retainedInbox) requireRetainedInbox(r, release, graph, state, retained, inventory);
        else for (var operator : state.operators) if (operator.delivered || operator.effectiveAt != null || !"ENROLLED".equals(operator.status)) fail("OPERATOR_EFFECTS_PRESENT");
        inventory.put("graph", json.fingerprint(graph));
        jdbc.queryForObject("SELECT id FROM provider_usage_grant_mutex WHERE id=1 FOR UPDATE NOWAIT", Integer.class);
        var allocation = one("SELECT * FROM provider_usage_allocations WHERE deployment_id=? AND target_profile_id=? FOR UPDATE NOWAIT", r.deploymentId(), r.targetProfileId());
        var terms = tree(allocation.get("payload_json"));
        if (!r.customerId().equals(allocation.get("customer_id")) || !"ACTIVE".equals(allocation.get("initialization_state"))
            || allocation.get("store_uuid") == null || allocation.get("initialized_at") == null || allocation.get("activated_at") == null
            || !Objects.equals(allocation.get("runtime_allocation_fingerprint"), allocation.get("initialization_fingerprint"))
            || !r.id().equals(terms.path("initialAdmission").path("reservationId").asText())
            || !r.generation().equals(terms.path("initialAdmission").path("generation").asText())
            || !(retainedInbox ? inventory.get("retainedAllowanceVersion") : manifest.versionId())
                .equals(terms.path("initialAdmission").path("versionId").asText())) fail("ACTIVE_ALLOWANCE_REQUIRED");
        inventory.put("allocation", json.fingerprint(allocation));
        inventory.put("grant", json.fingerprint(one("SELECT * FROM provider_usage_grants WHERE id=? FOR UPDATE NOWAIT", allocation.get("grant_id"))));
        inventory.put("financialEvents", json.fingerprint(retainedInbox
            ? rows("SELECT * FROM provider_usage_allowance_events WHERE subject_id=? OR grant_id=? ORDER BY id FOR UPDATE NOWAIT", allocation.get("approval_id"), allocation.get("grant_id"))
            : rows("SELECT * FROM provider_usage_allowance_events WHERE subject_id=? ORDER BY id FOR UPDATE NOWAIT", allocation.get("approval_id"))));
        inventory.put("validityReviews", json.fingerprint(rows("SELECT * FROM provider_usage_validity_reviews WHERE approval_id=? ORDER BY extension_id FOR UPDATE NOWAIT", allocation.get("approval_id"))));
        inventory.put("validityEvents", json.fingerprint(rows("SELECT e.* FROM provider_usage_validity_events e JOIN provider_usage_validity_reviews r ON r.extension_id=e.extension_id WHERE r.approval_id=? ORDER BY e.extension_id,e.event_type FOR UPDATE OF e NOWAIT", allocation.get("approval_id"))));
        if (retainedInbox) for (String field : List.of("authorization", "dependencyOwnership", "spec", "allocation", "grant", "financialEvents", "validityReviews", "validityEvents"))
            if (!inventory.get(field).equals(retained.inventory().get(field))) fail("RETAINED_INVENTORY_CHANGED");
        for (var checkpoint : preflightCheckpoints) for (String field : List.of("graph", "dependencyOwnership", "handles"))
            if (!inventory.get(field).equals(checkpoint.inventory().get(field))) fail("PREFLIGHT_CHECKPOINT_CHANGED");
        return new PostProvisionResumeContracts.Candidate(r, Map.copyOf(inventory), json.write(release),
            ((Timestamp) release.get("created_at")).toInstant(), ((Timestamp) release.get("updated_at")).toInstant(), List.of((String) allocation.get("approval_id")));
    }

    // Only an already-authorized cold reapply may retain a prior ACTIVE binding and delivered owner.
    private FailedApplyRetryContracts.Candidate requireRetainedRetry(Reservation r, Map<String, Object> release,
        Manifest manifest, List<Map<String, Object>> bindings, Map<String, String> inventory) {
        String releaseId = (String) release.get("id");
        var row = one("""
            SELECT * FROM deployment_admission_reviews WHERE kind='NATIVE_RETRY'
              AND payload_json::jsonb->'command'->>'releaseId'=?
              AND payload_json::jsonb->'failedAttempt'->'inventory'->>'coldMaintenanceRepair' IS NOT NULL
            FOR UPDATE NOWAIT
            """, releaseId);
        var receipt = json.read((String) row.get("payload_json"), FailedApplyRetryContracts.Receipt.class);
        var command = receipt.command(); var prior = receipt.failedAttempt(); var summary = receipt.summary();
        if (!json.fingerprint(receipt).equals(row.get("fingerprint")) || command == null || prior == null || summary == null
            || receipt.providerReadback() == null || receipt.providerReadback().inventory() == null
            || !Objects.equals(command.proofFingerprint(), json.fingerprint(Map.of("boundary", FailedApplyRetryContracts.COLD_MAINTENANCE_BOUNDARY,
                "local", prior.inventory(), "native", receipt.providerReadback().inventory())))
            || !json.fingerprint(Map.of("reservation", r.id(), "generation", r.generation(), "operation", command.operationKey())).equals(row.get("record_key"))
            || !command.fence().equals(prior.reservation().fence()) || command.fence().revision() >= r.revision()
            || !command.acceptOwnedServiceRedeploy() || !releaseId.equals(command.releaseId())
            || !r.id().equals(summary.reservationId()) || !r.generation().equals(summary.generation())
            || !releaseId.equals(summary.releaseId()) || !"APPLY_REQUESTED".equals(summary.status()) || summary.fromRevision() != command.fence().revision()
            || summary.revision() != summary.fromRevision() + 1 || summary.revision() > r.revision() || summary.financialMutationPerformed()
            || !r.id().equals(prior.reservation().id()) || !r.generation().equals(prior.reservation().generation())
            || !r.customerId().equals(prior.reservation().customerId()) || !r.tenantId().equals(prior.reservation().tenantId())
            || !r.deploymentId().equals(prior.reservation().deploymentId()) || !r.targetProfileId().equals(prior.reservation().targetProfileId())
            || !r.demand().equals(manifest.demand()) || !r.demand().equals(prior.reservation().demand())
            || !r.chargedDemand().equals(prior.reservation().chargedDemand())
            || !r.manifestFingerprint().equals(prior.reservation().manifestFingerprint())) fail("COLD_RETRY_UNPROVEN");
        var repair = json.read(prior.inventory().get("coldMaintenanceRepair"), RuntimeArtifactRepairContracts.VerifiedRepair.class);
        var verified = one("SELECT * FROM deployment_admission_reviews WHERE kind='ART_VERIFIED' AND record_key=? FOR UPDATE NOWAIT", releaseId);
        var recordedRepair = json.read((String) verified.get("payload_json"), RuntimeArtifactRepairContracts.VerifiedRepair.class);
        var scope = repair.scope();
        var checkpoint = tree(release.get("runtime_generation_transition_json"));
        var cold = checkpoint.path("coldMaintenanceReceipt"); var transition = cold.path("transition");
        var current = transition.path("current"); var previous = transition.path("previous");
        String priorId = checkpoint.path("priorRuntime").path("releaseId").asText();
        var originalRelease = tree(prior.failedReleaseJson());
        boolean recordedAttempt = false;
        for (var history : tree(release.get("provisioning_details_json")).path("retryHistory"))
            if (summary.authorizedAt().toString().equals(history.path("authorizedAt").asText())
                && receipt.actorId().equals(history.path("actorId").asText())
                && prior.failedAt().toString().equals(history.path("failedAt").asText())
                && tree(originalRelease.path("provisioning_details_json").asText()).path("progress").equals(history.path("progress"))) recordedAttempt = true;
        if (!json.fingerprint(repair).equals(json.fingerprint(recordedRepair)) || !json.fingerprint(recordedRepair).equals(verified.get("fingerprint"))
            || !recordedAttempt || !releaseId.equals(originalRelease.path("id").asText())
            || !summary.retainedAllocationIds().equals(prior.retainedAllocationIds())
            || !r.id().equals(scope.reservationId()) || !r.generation().equals(scope.generation())
            || !r.customerId().equals(scope.customerId()) || !r.tenantId().equals(scope.tenantId())
            || !r.deploymentId().equals(scope.deploymentId()) || !r.targetProfileId().equals(scope.targetProfileId())
            || !releaseId.equals(scope.releaseId()) || !manifest.versionId().equals(scope.versionId())
            || !manifest.versionConfigHash().equals(scope.versionConfigHash()) || !priorId.equals(scope.priorRuntimeReleaseId())
            || !Objects.equals(release.get("runtime_authority_fingerprint"), scope.authorityFingerprint())
            || !Objects.equals(release.get("source_artifact_id"), repair.original().artifactId())
            || !checkpoint.equals(tree(originalRelease.path("runtime_generation_transition_json").asText()))
            || !Objects.equals(release.get("runtime_generation_transition_fingerprint"), originalRelease.path("runtime_generation_transition_fingerprint").asText())
            || !json.fingerprint(json.read((String) release.get("runtime_generation_transition_json"),
                com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationCheckpointService.Checkpoint.class))
                    .equals(prior.inventory().get("coldMaintenanceCheckpoint"))
            || !"COLD_ADOPTED".equals(checkpoint.path("phase").asText())
            || !cold.equals(tree(repair.finalNativeInventory().get("coldMaintenanceReceipt")))
            || !"PHYSICAL_STOP_COLD_MAINTENANCE_V1".equals(cold.path("evidenceKind").asText())
            || !cold.path("inMemoryStateLossAcknowledged").asBoolean()
            || !"ACTIVE".equals(current.path("state").asText()) || current.path("authorityRevoked").asBoolean()
            || current.path("reclaimStarted").asBoolean() || current.path("fenceEpoch").asLong() != previous.path("fenceEpoch").asLong() + 1
            || !r.tenantId().equals(current.path("tenantId").asText())
            || !r.deploymentId().equals(current.path("target").path("deploymentId").asText())
            || !r.targetProfileId().equals(current.path("target").path("targetProfileId").asText())
            || !releaseId.equals(current.path("target").path("releaseId").asText())
            || !priorId.equals(previous.path("target").path("releaseId").asText())) fail("COLD_AUTHORITY_CHANGED");
        var priorBinding = bindings.stream().filter(b -> priorId.equals(b.get("release_id")) && "ACTIVE".equals(b.get("state"))).toList();
        if (priorBinding.size() != 1 || priorId.equals(releaseId)
            || bindings.stream().anyMatch(b -> !releaseId.equals(b.get("release_id")) && !priorId.equals(b.get("release_id"))
                && !List.of("NO_EFFECT", "SUPERSEDED").contains(b.get("state")))) fail("OTHER_RELEASE_UNSETTLED");
        inventory.put("bindings", json.fingerprint(bindings.stream().map(b -> { var stable = new TreeMap<>(b); stable.remove("updated_at"); return stable; }).toList()));
        inventory.put("priorRelease", json.fingerprint(one("SELECT * FROM platform_deployment_releases WHERE id=? FOR UPDATE NOWAIT", priorId)));
        inventory.put("priorVersion", json.fingerprint(one("SELECT * FROM platform_deployment_versions WHERE id=? FOR UPDATE NOWAIT", priorBinding.getFirst().get("version_id"))));
        var authorization = one("SELECT * FROM deployment_admission_authorizations WHERE reservation_id=? AND operation_key=? FOR UPDATE NOWAIT", r.id(), priorBinding.getFirst().get("authorization_key"));
        inventory.put("priorAuthorization", json.fingerprint(authorization));
        var authorized = json.read((String) authorization.get("payload_json"), AuthorizationReceipt.class);
        var priorManifest = one("SELECT * FROM deployment_admission_reviews WHERE kind='MANIFEST' AND record_key=? FOR UPDATE NOWAIT", authorized.command().manifestId());
        inventory.put("priorManifest", json.fingerprint(priorManifest));
        inventory.put("consumers", json.fingerprint(rows("SELECT * FROM platform_consumers WHERE bound_deployment_id=? ORDER BY id FOR UPDATE NOWAIT", r.deploymentId())));
        inventory.put("consumerHistory", json.fingerprint(rows("SELECT * FROM platform_consumer_binding_history WHERE to_deployment_id=? OR from_deployment_id=? ORDER BY id FOR UPDATE NOWAIT", r.deploymentId(), r.deploymentId())));
        inventory.put("installations", json.fingerprint(rows("SELECT i.* FROM ai_workspace_installations i JOIN platform_consumers c ON c.id=i.consumer_entity_id WHERE c.bound_deployment_id=? ORDER BY i.id FOR UPDATE OF i NOWAIT", r.deploymentId())));
        for (String field : List.of("bindings", "priorRelease", "priorVersion", "priorAuthorization", "priorManifest", "consumers", "consumerHistory", "installations"))
            if (!inventory.get(field).equals(prior.inventory().get(field))) fail("RETAINED_SCOPE_CHANGED");
        for (var pair : Map.of("runtimeApplicationUuid", scope.runtimeApplicationUuid(), "connectorApplicationUuid", scope.connectorApplicationUuid(), "runtimeDatabaseUuid", scope.databaseUuid(), "runtimeBaseUrl", scope.runtimeBaseUrl()).entrySet())
            if (!pair.getValue().equals(tree(release.get("provisioning_details_json")).path(pair.getKey()).asText())) fail("NATIVE_IDENTITY_CHANGED");
        for (String table : List.of("platform_target_retirement_operations", "platform_deployment_deletion_operations"))
            if (!rows("SELECT id FROM " + table + " WHERE deployment_id=? FOR UPDATE NOWAIT", r.deploymentId()).isEmpty()) fail("LIFECYCLE_OPERATION_PRESENT");
        inventory.put("retainedAllowanceVersion", (String) priorBinding.getFirst().get("version_id"));
        inventory.put("coldRetry", json.fingerprint(row)); inventory.put("coldRepair", json.fingerprint(verified));
        return prior;
    }

    private void requireRetainedInbox(Reservation r, Map<String, Object> release, Map<String, Object> graph,
        ManagedDependencyState state, FailedApplyRetryContracts.Candidate retained, Map<String, String> inventory) {
        var reviewed = one("SELECT * FROM deployment_managed_dependency_specs WHERE id=? FOR UPDATE NOWAIT", graph.get("spec_ref"));
        var spec = json.read((String) graph.get("spec_json"), ManagedDependencySpec.class);
        inventory.put("spec", json.fingerprint(reviewed));
        if (!Boolean.TRUE.equals(reviewed.get("active")) || reviewed.get("reviewed_at") == null || reviewed.get("reviewed_by") == null
            || !Objects.equals(reviewed.get("fingerprint"), graph.get("spec_fingerprint"))
            || !tree(reviewed.get("spec_json")).equals(tree(graph.get("spec_json")))
            || spec.services().size() != 1 || state.nodes.size() != 1 || state.operators.size() != 1
            || spec.services().getFirst().contract() != ManagedDependencySpec.Contract.BASIC_INBOX_KEYS_V1
            || spec.services().getFirst().database() == null || !spec.services().getFirst().volumes().isEmpty()
            || state.configurationRevision < 1 || !"RESUMED".equals(state.runtimeGenerationState)
            || !state.runtimeGenerationStoppedSlots.isEmpty() || !state.targetValues.isEmpty()
            || !"DEPENDENCY_ADMISSION_INVALID".equals(graph.get("failure_code"))) fail("RETAINED_INBOX_UNPROVEN");
        var node = state.nodes.getFirst(); var owner = state.operators.getFirst();
        String releaseId = (String) release.get("id"), graphId = (String) graph.get("id");
        String priorId = tree(release.get("runtime_generation_transition_json")).path("priorRuntime").path("releaseId").asText();
        var enrollment = state.commands.get("release:" + priorId + ":dependency:initial-owner");
        var initialization = state.commands.get("release:" + releaseId + ":dependency:runtime-bound");
        if (!spec.services().getFirst().slot().equals(node.slot) || !"INITIALIZING".equals(node.status)
            || !("INITIALIZE:" + node.slot).equals(graph.get("step")) || node.accountId == null || node.bindingId == null
            || node.applicationUuid == null || node.databaseUuid == null || !node.volumes.isEmpty()
            || !json.fingerprint(List.of("RUNTIME_GENERATION", graphId, r.targetProfileId(), priorId, releaseId,
                graph.get("spec_fingerprint"), graph.get("binding_fingerprint"))).equals(state.runtimeGenerationOperationKey)
            || initialization == null || !graphId.equals(initialization.resultId())
            || !json.fingerprint(List.of("RECONCILE", graph.get("spec_fingerprint"), graph.get("binding_fingerprint"), state.configurationRevision)).equals(initialization.fingerprint())
            || !"ENROLLED".equals(owner.status) || owner.effectiveAt == null || owner.credentialRevision < 1
            || !owner.effectiveAt.isBefore(((Timestamp) release.get("created_at")).toInstant()) || !node.slot.equals(owner.slot)
            || enrollment == null || !Objects.equals(owner.id, enrollment.resultId())
            || !json.fingerprint(List.of("ENROLL", owner.slot, owner.displayName)).equals(enrollment.fingerprint())
            || !managedSecretName(graphId, node.slot, owner.id + "-" + owner.credentialRevision).equals(owner.secretName)
            || !node.secrets.keySet().equals(spec.services().getFirst().secretPurposes())) fail("RETAINED_CONFIGURATION_CHANGED");
        for (var secret : node.secrets.entrySet())
            if (!managedSecretName(graphId, node.slot, secret.getKey()).equals(secret.getValue())) fail("RETAINED_CONFIGURATION_CHANGED");
        var repair = json.read(retained.inventory().get("coldMaintenanceRepair"), RuntimeArtifactRepairContracts.VerifiedRepair.class);
        var inbox = tree(repair.finalNativeInventory().get("inbox"));
        if (!node.applicationUuid.equals(inbox.path("applicationUuid").asText())
            || !node.databaseUuid.equals(inbox.path("databaseUuid").asText())) fail("RETAINED_INBOX_IDENTITY_CHANGED");
    }

    private String managedSecretName(String graphId, String slot, String purpose) {
        return ("MANAGED_DEP_" + graphId.substring(4) + "_" + json.fingerprint(List.of(slot, purpose)).substring(0, 24)).toUpperCase(java.util.Locale.ROOT);
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

    private JsonNode nativePhase(JsonNode failedRelease, Reservation reservation, String releaseId, Manifest manifest,
        Map<String, String> inventory, List<PostProvisionResumeContracts.Candidate> preflightCheckpoints) {
        long revision = reservation.revision();
        var current = tree(failedRelease.path("provisioning_details_json").asText());
        boolean currentAttempt = true;
        // Repeated continuation follows verified immutable receipts, never an editable history entry.
        while (current.has("postProvisionResume")) {
            var marker = current.path("postProvisionResume");
            if (!PostProvisionResumeContracts.BOUNDARY.equals(marker.path("boundary").asText())) fail("CONTROL_FLOW_UNPROVEN");
            boolean preflightBlocked = "PRE_APPLY_BLOCKED".equals(failedRelease.path("status").asText());
            if (preflightBlocked) {
                requireFailedPreflight(failedRelease, reservation, releaseId, manifest, inventory);
                requireSteps(current.path("progress").path("steps"), List.of("queue_release", "preflight_verification"),
                    List.of("QUEUED", "FAILED"));
            } else {
                requireSteps(current.path("progress").path("steps"), List.of("queue_release", "preflight_verification", "resume_managed_dependencies", "apply_failed"),
                    List.of("QUEUED", "COMPLETED", "RUNNING", "FAILED"));
            }
            String key = json.fingerprint(Map.of("reservation", reservation.id(), "generation", reservation.generation(),
                "operation", marker.path("operationKey").asText()));
            var receipt = receipts.apply(key);
            if (receipt == null) fail("CONTROL_FLOW_UNPROVEN");
            var command = receipt.command(); var prior = receipt.failedAttempt();
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
            // Older links prove lineage, not equality with checkpoints changed by later dependency work.
            if (preflightBlocked && currentAttempt) preflightCheckpoints.add(prior);
            revision = command.fence().revision();
            failedRelease = tree(prior.failedReleaseJson());
            current = tree(failedRelease.path("provisioning_details_json").asText());
            currentAttempt = false;
        }
        return current;
    }

    private void requireFailedPreflight(JsonNode release, Reservation reservation, String releaseId, Manifest manifest,
        Map<String, String> inventory) {
        if (!releaseId.equals(release.path("id").asText()) || !"BLOCKED".equals(release.path("provisioning_status").asText())
            || !"FAILED".equals(release.path("verification_status").asText())
            || !"preflight_verification".equals(release.path("current_step_key").asText())
            || release.path("verification_run_id").asText().isBlank()) fail("PREFLIGHT_VERIFICATION_UNPROVEN");
        var run = one("SELECT * FROM platform_deployment_verification_runs WHERE id=? FOR UPDATE NOWAIT",
            release.path("verification_run_id").asText());
        if (!reservation.deploymentId().equals(run.get("deployment_id")) || !releaseId.equals(run.get("release_id"))
            || !manifest.versionId().equals(run.get("deployment_version_id"))
            || !"PRE_APPLY".equals(run.get("verification_type")) || !"FAILED".equals(run.get("status")))
            fail("PREFLIGHT_VERIFICATION_UNPROVEN");
        inventory.put("preflightVerification:" + run.get("id"), json.fingerprint(run));
    }

    private void requireCompletedNativePhase(JsonNode steps, boolean retainedInbox) {
        var order = new java.util.ArrayList<>(List.of("queue_release", "preflight_verification", "prepare_apply", "coolify_preflight",
            "ensure_deployment_execution_secrets", "resolve_coolify_source", "resolve_coolify_customer_scope",
            "reconcile_coolify_runtime_application", "record_coolify_runtime_handle_provisional",
            "reconcile_coolify_document_source_storage", "reconcile_coolify_connector_application",
            "record_coolify_connector_handle_provisional", "configure_coolify_runtime_private_network",
            "configure_coolify_connector_private_network", "reconcile_coolify_runtime_database",
            "record_coolify_runtime_database_handle_provisional", "configure_coolify_runtime_environment",
            "configure_coolify_connector_environment", "trigger_coolify_runtime_deploy", "wait_for_coolify_runtime",
            "trigger_coolify_connector_deploy", "wait_for_coolify_connector", "remove_coolify_connector_database_bootstrap_credentials",
            "redeploy_coolify_connector_without_database_bootstrap_credentials", "wait_for_coolify_connector_after_database_credential_scrub",
            "record_coolify_connector_handle", "record_coolify_runtime_handle", "record_coolify_runtime_database_handle", "run_verification", "apply_failed"));
        if (retainedInbox) {
            order.addAll(order.indexOf("trigger_coolify_runtime_deploy"), List.of("stop_coolify_connector_for_generation_transition", "stop_coolify_runtime_after_verified_cold_maintenance"));
            order.add(order.indexOf("trigger_coolify_connector_deploy"), "resume_remote_dependencies_after_generation_transition");
        }
        if (steps.size() != order.size()) fail("CONTROL_FLOW_UNPROVEN");
        for (int i = 0; i < order.size(); i++) {
            String key = order.get(i), expected = switch (key) {
                case "queue_release" -> "QUEUED"; case "prepare_apply" -> "RUNNING";
                case "run_verification" -> retainedInbox ? "COMPLETED" : "RUNNING";
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
