package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferEntitlementService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerReviewedCompositionService;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencyState;
import com.ai.fabric.platform.backend.deployment.model.PreservedExternalBindingKinds;
import com.ai.fabric.platform.backend.deployment.service.DeploymentSourceArtifactService;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static com.ai.fabric.platform.backend.deployment.admission.RuntimeArtifactRepairContracts.*;
import static org.springframework.http.HttpStatus.*;

/** Local authoritative ownership and financial fences. Called only inside the admission transaction. */
@Component
public class RuntimeArtifactRepairEvidence {
    private final JdbcTemplate jdbc;
    private final AdmissionJson json;
    private final AdmissionStore store;
    private final CustomerOfferEntitlementService entitlements;
    private final CustomerReviewedCompositionService composition;
    private final DeploymentSourceArtifactService artifacts;
    private final RuntimeArtifactRepairCompatibility compatibility;
    private final Clock clock;
    private RuntimeArtifactRepairColdMaintenanceEligibility coldMaintenance;
    @Autowired
    void setColdMaintenance(RuntimeArtifactRepairColdMaintenanceEligibility coldMaintenance) {
        this.coldMaintenance = coldMaintenance;
    }
    @Autowired
    public RuntimeArtifactRepairEvidence(JdbcTemplate jdbc, AdmissionJson json, AdmissionStore store,
        CustomerOfferEntitlementService entitlements, CustomerReviewedCompositionService composition,
        DeploymentSourceArtifactService artifacts, RuntimeArtifactRepairCompatibility compatibility) {
        this(jdbc, json, store, entitlements, composition, artifacts, compatibility, Clock.systemUTC());
    }
    RuntimeArtifactRepairEvidence(JdbcTemplate jdbc, AdmissionJson json, AdmissionStore store,
        CustomerOfferEntitlementService entitlements, CustomerReviewedCompositionService composition,
        DeploymentSourceArtifactService artifacts, RuntimeArtifactRepairCompatibility compatibility, Clock clock) {
        this.jdbc = jdbc; this.json = json; this.store = store; this.entitlements = entitlements;
        this.composition = composition; this.artifacts = artifacts; this.compatibility = compatibility; this.clock = clock;
    }
    LocalEvidence read(String deploymentId, String releaseId, Request request, Claim claim) {
        entitlements.requireStaffAdmin();
        if (request == null || !request.acceptRuntimeRestart() || request.mode() == null || request.fence() == null
            || request.operationKey() == null || request.operationKey().isBlank() || request.operationKey().length() > 128
            || request.reason() == null || request.reason().isBlank() || request.reason().length() > 1000)
            throw new AdmissionFailure(BAD_REQUEST, "RUNTIME_ARTIFACT_REPAIR_CONSENT_REQUIRED");
        boolean cold = request.mode() == Mode.COLD_MAINTENANCE_REAPPLY;
        if (cold && (coldMaintenance == null || request.coldMaintenance() == null
            || !request.coldMaintenance().acceptInMemoryStateLoss())
            || !cold && request.coldMaintenance() != null)
            throw new AdmissionFailure(BAD_REQUEST, "RUNTIME_ARTIFACT_REPAIR_COLD_CONSENT_REQUIRED");
        var deployment = one("SELECT * FROM platform_deployments WHERE id=? FOR UPDATE NOWAIT", deploymentId);
        var r = store.require(request.fence().reservationId());
        var expectedFence = claim == null ? request.fence() : claim.reservationFence();
        if (!r.fence().equals(expectedFence) || r.state() != State.OUTCOME_UNKNOWN
            || !Objects.equals(r.workerToken(), claim == null ? null : claim.workerToken())
            || r.retirementOperationId() != null || r.retentionEnvelope() != null || r.retentionSettlement() != null
            || !deploymentId.equals(r.deploymentId()) || !r.customerId().equals(deployment.get("customer_id"))
            || !r.tenantId().equals(deployment.get("tenant_id")) || deployment.get("archived_at") != null
            || !"APPLY_FAILED".equals(deployment.get("status"))) fail("RESERVATION_CHANGED");
        var association = one("SELECT * FROM platform_customer_offer_deployments WHERE deployment_id=? FOR UPDATE NOWAIT", deploymentId);
        var offer = entitlements.requireOffer((String) association.get("offer_id"));
        if (!offer.getFingerprint().equals(request.expectedOfferFingerprint())
            || !offer.getFingerprint().equals(association.get("offer_fingerprint"))
            || !r.customerId().equals(association.get("customer_id"))) fail("OFFER_CHANGED");
        var entitlement = entitlements.requireEntitlement(r.customerId(), offer.getId(), null, true);
        if (request.expectedEntitlementRevision() != entitlement.getRevision()) fail("ENTITLEMENT_CHANGED");
        var definition = entitlements.definition(offer);
        if (definition.targets().stream().noneMatch(t -> r.targetProfileId().equals(t.targetProfileId())
            && r.intent().name().equals(t.intent().name()))) fail("TARGET_NOT_REVIEWED");
        var context = store.context(r); var manifest = context.manifest();
        var result = new TreeMap<String, String>();
        var release = one("SELECT * FROM platform_deployment_releases WHERE id=? FOR UPDATE NOWAIT", releaseId);
        if (!deploymentId.equals(release.get("deployment_id")) || !r.targetProfileId().equals(release.get("target_profile_id"))
            || !manifest.versionId().equals(release.get("deployment_version_id"))
            || !cold && !manifest.versionId().equals(deployment.get("active_version_id"))
            || !"FAILED".equals(release.get("status")) || !"FAILED".equals(release.get("provisioning_status"))
            || !"SKIPPED".equals(release.get("verification_status")) || !"apply_failed".equals(release.get("current_step_key"))
            || !"COOLIFY".equals(release.get("provider_type")) || release.get("runtime_authority_fingerprint") == null) fail("RELEASE_CHANGED");
        ColdPriorRuntime prior = cold ? coldMaintenance.prior(r, deployment, release, request, result) : null;
        var version = one("SELECT * FROM platform_deployment_versions WHERE id=? FOR UPDATE NOWAIT", manifest.versionId());
        if (!deploymentId.equals(version.get("deployment_id")) || !"PUBLISHED".equals(version.get("status"))
            || !manifest.versionConfigHash().equals(version.get("config_hash"))) fail("VERSION_CHANGED");
        var binding = one("SELECT * FROM deployment_admission_release_bindings WHERE release_id=? FOR UPDATE NOWAIT", releaseId);
        for (var pair : Map.of("reservation_id", r.id(), "generation", r.generation(), "deployment_id", deploymentId,
            "target_profile_id", r.targetProfileId(), "customer_id", r.customerId(), "tenant_id", r.tenantId(),
            "version_id", manifest.versionId(), "version_config_hash", manifest.versionConfigHash(), "state", "OUTCOME_UNKNOWN").entrySet())
            if (!pair.getValue().equals(binding.get(pair.getKey()))) fail("RELEASE_BINDING_CHANGED");
        if (rows("SELECT * FROM platform_deployment_releases WHERE deployment_id=? AND target_profile_id=? ORDER BY id FOR UPDATE NOWAIT",
            deploymentId, r.targetProfileId()).stream().anyMatch(v -> !releaseId.equals(v.get("id"))
                && List.of("QUEUED", "PROVISIONING", "APPLYING", "VERIFYING").contains(v.get("status")))) fail("OTHER_WORKER_PRESENT");
        var gate = one("SELECT * FROM platform_target_retirement_gates WHERE deployment_id=? AND target_profile_id=? FOR UPDATE NOWAIT",
            deploymentId, r.targetProfileId());
        if (!"AVAILABLE".equals(gate.get("state")) || gate.get("operation_id") != null
            || ((Number) gate.get("epoch")).longValue() < 0) fail("RETIREMENT_PRESENT");
        if (!rows("SELECT * FROM platform_target_retirement_operations WHERE deployment_id=? AND target_profile_id=? AND state NOT IN ('COMPLETE','CANCELLED') FOR UPDATE NOWAIT",
            deploymentId, r.targetProfileId()).isEmpty()) fail("RETIREMENT_PRESENT");
        var graph = one("SELECT * FROM deployment_managed_dependency_graphs WHERE deployment_id=? AND target_profile_id=? FOR UPDATE NOWAIT", deploymentId, r.targetProfileId());
        if (!r.customerId().equals(graph.get("customer_id")) || !r.tenantId().equals(graph.get("tenant_id"))
            || !releaseId.equals(graph.get("release_id")) || !manifest.versionId().equals(graph.get("version_id"))
            || !r.id().equals(graph.get("reservation_ref")) || graph.get("worker_token") != null
            || !Objects.equals(release.get("runtime_authority_fingerprint"), graph.get("binding_fingerprint"))) fail("DEPENDENCY_SCOPE_CHANGED");
        var graphState = json.read((String) graph.get("state_json"), ManagedDependencyState.class);
        if (cold) coldMaintenance.graph(graph, graphState, prior, releaseId);
        else RuntimeArtifactRepairDependencyEligibility.require((String) graph.get("status"), (String) graph.get("step"),
            (String) graph.get("failure_code"), graphState);
        var handles = rows("SELECT * FROM deployment_provider_resource_handles WHERE deployment_id=? ORDER BY id FOR UPDATE NOWAIT", deploymentId);
        var nativeHandles = handles.stream().filter(v -> !PreservedExternalBindingKinds.isPreserved((String) v.get("resource_kind"))).toList();
        if (!nativeHandles.stream().map(v -> (String) v.get("resource_kind")).sorted().toList()
            .equals(List.of("APPLICATION", "CONNECTOR_APPLICATION", "RUNTIME_POSTGRES_DATABASE"))) fail("OWNED_PG_REQUIRED");
        for (var handle : handles) if (!r.targetProfileId().equals(handle.get("target_profile_id"))
            || !PreservedExternalBindingKinds.isPreserved((String) handle.get("resource_kind"))
                && (!(cold && "APPLICATION".equals(handle.get("resource_kind")) ? prior.releaseId() : releaseId).equals(handle.get("release_id")) || !"COOLIFY".equals(handle.get("provider_type"))
                    || handle.get("provider_resource_uuid") == null)) fail("HANDLE_SCOPE_CHANGED");
        var original = artifacts.require(composition.runtimeArtifactId(definition));
        if (!original.getId().equals(release.get("source_artifact_id"))) fail("ORIGINAL_ARTIFACT_CHANGED");
        var replacement = artifacts.require(request.replacementArtifactId()); compatibility.requireCompatible(original, replacement, request.mode());
        compatibility.requireTargetChannel(replacement, r.intent());
        if (cold) compatibility.requireColdMaintenance(replacement);
        if (request.mode() == Mode.IDLE_CHECKPOINT_COLD_RESTART) {
            if (!"BLOCKED".equals(graph.get("status")) || graphState.nodes.stream().anyMatch(node -> node.initialized))
                fail("COLD_RESTART_PREINITIALIZATION_REQUIRED");
            var consumers = rows("SELECT * FROM platform_consumers WHERE bound_deployment_id=? ORDER BY id FOR UPDATE NOWAIT", deploymentId);
            var installations = rows("SELECT i.* FROM ai_workspace_installations i JOIN platform_consumers c ON c.id=i.consumer_entity_id WHERE c.bound_deployment_id=? ORDER BY i.id FOR UPDATE OF i NOWAIT", deploymentId);
            var assignments = rows("SELECT * FROM platform_consumer_binding_history WHERE to_deployment_id=? OR from_deployment_id=? ORDER BY id FOR UPDATE NOWAIT", deploymentId, deploymentId);
            if (consumers.stream().anyMatch(c -> c.get("bound_release_id") != null)
                || installations.stream().anyMatch(i -> !"DRAFT".equals(i.get("status")))
                || assignments.stream().anyMatch(a -> a.get("to_release_id") != null || a.get("from_release_id") != null)
                || !rows("SELECT id FROM platform_deployment_releases WHERE deployment_id=? AND status IN ('APPLIED_VERIFIED','APPLIED_VERIFICATION_FAILED') FOR UPDATE NOWAIT", deploymentId).isEmpty())
                fail("COLD_RESTART_NEVER_ACTIVATED_SETUP_REQUIRED");
            result.put("coldRestartConsumers", json.fingerprint(consumers));
            result.put("coldRestartInstallations", json.fingerprint(installations));
            result.put("coldRestartAssignments", json.fingerprint(assignments));
        }
        result.put("deployment", json.fingerprint(deployment)); result.put("release", json.fingerprint(release));
        result.put("version", json.fingerprint(version)); result.put("offerAssociation", json.fingerprint(association));
        result.put("entitlement", json.fingerprint(entitlements.summary(entitlement)));
        // The admission observer advances only this scheduling clock while retaining the same failed binding.
        var stableBinding = new TreeMap<>(binding); stableBinding.remove("updated_at");
        result.put("binding", json.fingerprint(stableBinding)); result.put("handles", json.fingerprint(handles));
        result.put("reservationIdentity", AdmissionPolicyContinuity.identity(json, r));
        var stableReservation = json.read(json.write(r), com.fasterxml.jackson.databind.node.ObjectNode.class);
        List.of("revision", "updatedAt", "workerToken", "evidenceReference").forEach(stableReservation::remove);
        result.put("reservation", json.fingerprint(stableReservation));
        result.put("gate", json.fingerprint(gate));
        result.put("graph", json.fingerprint(graph));
        result.put("graphOwnership", json.fingerprint(rows("SELECT * FROM platform_managed_resource_ownership WHERE deployment_id=? AND target_profile_id=? ORDER BY id FOR UPDATE NOWAIT", deploymentId, r.targetProfileId())));
        result.put("authority", json.fingerprint(one("SELECT * FROM platform_runtime_authority_snapshots WHERE release_id=? FOR UPDATE NOWAIT", releaseId)));
        jdbc.queryForObject("SELECT id FROM provider_usage_grant_mutex WHERE id=1 FOR UPDATE NOWAIT", Integer.class);
        var allocation = one("SELECT * FROM provider_usage_allocations WHERE deployment_id=? AND target_profile_id=? FOR UPDATE NOWAIT", deploymentId, r.targetProfileId());
        var terms = json.read((String) allocation.get("payload_json"), JsonNode.class);
        if (!"ACTIVE".equals(allocation.get("initialization_state")) || allocation.get("store_uuid") == null
            || !r.customerId().equals(allocation.get("customer_id")) || !r.id().equals(terms.path("initialAdmission").path("reservationId").asText())
            || !r.generation().equals(terms.path("initialAdmission").path("generation").asText())
            || !(cold ? prior.versionId() : manifest.versionId()).equals(terms.path("initialAdmission").path("versionId").asText())) fail("ACTIVE_SAME_ALLOWANCE_REQUIRED");
        result.put("allocation", json.fingerprint(allocation));
        result.put("walletApprovalId", (String) allocation.get("approval_id"));
        result.put("walletStoreUuid", (String) allocation.get("store_uuid"));
        result.put("walletRuntimeFingerprint", (String) allocation.get("runtime_allocation_fingerprint"));
        result.put("grant", json.fingerprint(one("SELECT * FROM provider_usage_grants WHERE id=? FOR UPDATE NOWAIT", allocation.get("grant_id"))));
        result.put("fundingEvents", json.fingerprint(rows("SELECT * FROM provider_usage_allowance_events WHERE subject_id=? ORDER BY id FOR UPDATE NOWAIT", allocation.get("approval_id"))));
        result.put("validityReviews", json.fingerprint(rows("SELECT * FROM provider_usage_validity_reviews WHERE approval_id=? ORDER BY extension_id FOR UPDATE NOWAIT", allocation.get("approval_id"))));
        result.put("validityEvents", json.fingerprint(rows("SELECT e.* FROM provider_usage_validity_events e JOIN provider_usage_validity_reviews r ON r.extension_id=e.extension_id WHERE r.approval_id=? ORDER BY e.extension_id,e.event_type FOR UPDATE OF e NOWAIT", allocation.get("approval_id"))));
        var scope = new Scope(r.customerId(), r.tenantId(), deploymentId, r.targetProfileId(), releaseId, manifest.versionId(),
            manifest.versionConfigHash(), (String) release.get("runtime_authority_fingerprint"), r.id(), r.generation(),
            (String) deployment.get("runtime_base_url"), uuid(nativeHandles, "APPLICATION"), uuid(nativeHandles, "CONNECTOR_APPLICATION"), uuid(nativeHandles, "RUNTIME_POSTGRES_DATABASE"),
            cold ? prior.releaseId() : null);
        if (scope.runtimeBaseUrl() == null || !scope.runtimeBaseUrl().startsWith("https://")) fail("PRIVATE_RUNTIME_ENDPOINT_REQUIRED");
        Instant executionDeadline = clock.instant().plusSeconds(1800).isBefore(r.expiresAt())
            ? clock.instant().plusSeconds(1800) : r.expiresAt();
        if (!executionDeadline.isAfter(clock.instant())) fail("RESERVATION_EXPIRED");
        return new LocalEvidence(scope, request, compatibility.pin(original), compatibility.pin(replacement), Map.copyOf(result),
            clock.instant().plusSeconds(300), executionDeadline);
    }
    void unchanged(LocalEvidence expected, LocalEvidence actual) {
        if (!expected.scope().equals(actual.scope()) || !expected.request().equals(actual.request())
            || !expected.original().equals(actual.original()) || !expected.replacement().equals(actual.replacement())
            || !expected.fingerprints().equals(actual.fingerprints())) fail("LOCAL_STATE_CHANGED");
    }
    private static String uuid(List<Map<String, Object>> rows, String kind) {
        return (String) rows.stream().filter(v -> kind.equals(v.get("resource_kind"))).findFirst().orElseThrow().get("provider_resource_uuid");
    }
    private List<Map<String, Object>> rows(String sql, Object... args) { return jdbc.queryForList(sql, args); }
    private Map<String, Object> one(String sql, Object... args) { var values = rows(sql, args); if (values.size() != 1) fail("EXACT_SCOPE_REQUIRED"); return values.getFirst(); }
    private static void fail(String code) { throw new AdmissionFailure(CONFLICT, "RUNTIME_ARTIFACT_REPAIR_" + code); }
}
