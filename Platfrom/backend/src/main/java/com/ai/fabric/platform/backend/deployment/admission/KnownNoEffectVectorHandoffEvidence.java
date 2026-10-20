package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentProviderResourceHandleEntity;
import com.ai.fabric.platform.backend.deployment.model.PreservedExternalBindingKinds;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static com.ai.fabric.platform.backend.deployment.admission.KnownNoEffectSettlementContracts.*;
import static org.springframework.http.HttpStatus.CONFLICT;

/** Read-only local proof for V202. A settlement is never interpreted as an unused cancellation. */
final class KnownNoEffectVectorHandoffEvidence {
    private final JdbcTemplate jdbc;
    private final AdmissionJson json;

    KnownNoEffectVectorHandoffEvidence(JdbcTemplate jdbc, AdmissionJson json) { this.jdbc = jdbc; this.json = json; }

    void require(DeploymentProviderResourceHandleEntity handle, String receiptId, String bindingFingerprint,
                 String customerId, String destinationId, String approval, String newPrefix) {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) throw denied();
        // Other admission/funding writers must not insert a new effect after the locked absence checks.
        jdbc.queryForObject("SELECT id FROM deployment_admission_mutex WHERE id=1 FOR UPDATE NOWAIT", Integer.class);
        String dep = handle.getDeploymentId(), target = handle.getTargetProfileId();
        JsonNode meta = json.read(handle.getMetadataJson(), JsonNode.class);
        var unsigned = meta.isObject() ? ((com.fasterxml.jackson.databind.node.ObjectNode) meta).deepCopy() : null;
        if (unsigned != null) unsigned.remove("fingerprint");
        if (!Set.of("ACTIVE", "RETAINED_UNUSED").contains(handle.getStatus()) || handle.getReleaseId() != null
            || !PreservedExternalBindingKinds.VECTOR_STORAGE.equals(handle.getResourceKind()) || unsigned == null
            || !bindingFingerprint.equals(meta.path("fingerprint").textValue()) || !bindingFingerprint.equals(json.fingerprint(unsigned))
            || !customerId.equals(meta.path("customerId").textValue()) || !dep.equals(meta.path("deploymentId").textValue())
            || !target.equals(meta.path("targetProfileId").textValue()) || !handle.getProviderResourceUuid().equals(meta.path("clusterId").textValue())
            || !approval.equals(meta.path("approvalReference").textValue()) || !"RETAIN_EXTERNAL".equals(meta.path("lifecycle").textValue())
            || !meta.path("externalResourceOwned").isBoolean() || meta.path("externalResourceOwned").booleanValue()
            || newPrefix == null || newPrefix.isBlank() || !meta.path("collectionPrefix").isTextual()
            || meta.path("collectionPrefix").textValue().isBlank() || newPrefix.equals(meta.path("collectionPrefix").textValue())) throw denied();

        var reservations = rows("SELECT * FROM deployment_capacity_reservations WHERE deployment_id=? AND target_profile_id=?", dep, target);
        if (reservations.size() != 1) throw denied();
        var row = reservations.getFirst();
        Reservation r = json.read((String) row.get("payload_json"), Reservation.class);
        String key = json.fingerprint(Map.of("reservation", r.id(), "generation", r.generation()));
        Receipt receipt = review("NO_EFFECT_SETTLE", key);
        if (!receiptId.equals(receipt.id()) || receipt.outcome() != Outcome.SETTLED_KNOWN_NO_EFFECT || receipt.reclamationPerformed()
            || !r.equals(receipt.reservation()) || r.state() != State.RELEASED || r.expiryState() != ExpiryState.RELEASED
            || !r.id().equals(row.get("id")) || !r.generation().equals(row.get("generation"))
            || r.revision() != ((Number) row.get("revision")).longValue() || !"RELEASED".equals(row.get("state"))
            || !"RELEASED".equals(row.get("expiry_state")) || row.get("current_slot") != null
            || !customerId.equals(row.get("customer_id")) || !dep.equals(row.get("deployment_id")) || !target.equals(row.get("target_profile_id"))
            || !customerId.equals(r.customerId()) || !customerId.equals(receipt.customerId()) || !dep.equals(receipt.deploymentId())
            || !target.equals(receipt.targetProfileId()) || !r.tenantId().equals(receipt.tenantId())
            || !r.tenantId().equals(meta.path("tenantId").textValue())
            || !receipt.command().versionId().equals(meta.path("versionId").textValue())
            || !receipt.command().versionConfigHash().equals(meta.path("configHash").textValue())
            || !receipt.command().fence().reservationId().equals(r.id()) || !receipt.command().fence().generation().equals(r.generation())
            || receipt.command().fence().revision() + 1 != r.revision()
            || !r.manifestFingerprint().equals(receipt.command().manifestFingerprint())
            || !r.bindingFingerprint().equals(receipt.command().bindingFingerprint())
            || !("known-no-effect-settlement:" + receipt.id()).equals(r.evidenceReference())
            || !receipt.commandFingerprint().equals(json.fingerprint(Map.of("customerId", customerId, "deploymentId", dep,
                "reservationId", r.id(), "actorId", receipt.actorId(), "command", receipt.command())))) throw denied();
        String operation = json.fingerprint(Map.of("customer", customerId, "deployment", dep, "target", target,
            "operationKey", receipt.command().operationKey()));
        if (!receipt.equals(review("NO_EFFECT_OP", operation))) throw denied();
        var manifests = rows("SELECT * FROM deployment_admission_reviews WHERE kind='MANIFEST' AND record_key=?", r.manifestId());
        if (manifests.size() != 1 || !r.manifestFingerprint().equals(manifests.getFirst().get("fingerprint"))
            || !r.manifestFingerprint().equals(json.fingerprint(json.read((String) manifests.getFirst().get("payload_json"), JsonNode.class)))) throw denied();
        String sourceOffer = requireReleases(receipt);
        requireOwners(receipt, destinationId, sourceOffer);
        var version = rows("SELECT deployment_id,status,config_hash FROM platform_deployment_versions WHERE id=?", receipt.command().versionId());
        if (version.size() != 1 || !dep.equals(version.getFirst().get("deployment_id")) || !"PUBLISHED".equals(version.getFirst().get("status"))
            || !receipt.command().versionConfigHash().equals(version.getFirst().get("config_hash"))) throw denied();
        var deployment = rows("SELECT * FROM platform_deployments WHERE id=?", dep);
        if (deployment.size() != 1 || !customerId.equals(deployment.getFirst().get("customer_id"))
            || !r.tenantId().equals(deployment.getFirst().get("tenant_id")) || deployment.getFirst().get("archived_at") != null
            || deployment.getFirst().get("active_version_id") != null || text(deployment.getFirst().get("runtime_base_url"))
            || text(deployment.getFirst().get("connector_base_url"))) throw denied();

        var events = rows("SELECT * FROM deployment_admission_events WHERE reservation_id=? ORDER BY revision", r.id());
        if (events.size() != r.revision() + 1) throw denied();
        for (int i = 0; i < events.size(); i++) if (((Number) events.get(i).get("revision")).longValue() != i) throw denied();
        var settled = events.getLast();
        if (!"SETTLED_KNOWN_NO_EFFECT".equals(settled.get("event_type")) || !receipt.actorId().equals(settled.get("actor_id"))
            || !r.evidenceReference().equals(settled.get("evidence_reference"))
            || !receipt.settledAt().equals(((java.sql.Timestamp) settled.get("created_at")).toInstant())) throw denied();
        unchanged(receipt, "admissionEvents", events.subList(0, events.size() - 1));
        unchanged(receipt, "preservedAuthoritySnapshots", rows("SELECT * FROM platform_runtime_authority_snapshots WHERE deployment_id=? AND target_profile_id=? ORDER BY id", dep, target));
        unchanged(receipt, "preservedAuthorities", rows("SELECT * FROM platform_runtime_target_authorities WHERE deployment_id=? AND target_profile_id=? ORDER BY id", dep, target));
        unchanged(receipt, "preservedIntegrationSnapshots", rows("SELECT * FROM deployment_external_integration_snapshots WHERE deployment_id=? AND target_profile_id=? ORDER BY release_id", dep, target));
        unchanged(receipt, "targetGate", rows("SELECT * FROM platform_target_retirement_gates WHERE deployment_id=? AND target_profile_id=?", dep, target));
        var retained = rows("SELECT * FROM deployment_provider_resource_handles WHERE deployment_id=? AND (target_profile_id=? OR target_profile_id IS NULL) ORDER BY id", dep, target);
        var proofs = retained.stream().map(value -> new RecordProof((String) value.get("id"), json.fingerprint(originalRetainedRow(receipt, value)))).toList();
        if (!proofs.equals(receipt.proof().retainedExternalBindings()) || proofs.stream().noneMatch(value -> handle.getId().equals(value.id()))) throw denied();
        requireAbsent(receipt, "activity", "SELECT * FROM deployment_admission_activity_watermarks WHERE reservation_id=?", r.id());
        requireAbsent(receipt, "managedGraphs", "SELECT * FROM deployment_managed_dependency_graphs WHERE deployment_id=? AND target_profile_id=?", dep, target);
        requireAbsent(receipt, "providerAllocations", "SELECT * FROM provider_usage_allocations WHERE deployment_id=? AND target_profile_id=?", dep, target);
        requireAbsent(receipt, "consumerReleaseAssignments", "SELECT * FROM platform_consumers WHERE bound_release_id IN (SELECT id FROM platform_deployment_releases WHERE deployment_id=? AND target_profile_id=?)", dep, target);
        requireAbsent(receipt, "retirement", "SELECT * FROM platform_target_retirement_operations WHERE deployment_id=? AND target_profile_id=?", dep, target);
        requireAbsent(receipt, "deletion", "SELECT * FROM platform_deployment_deletion_operations WHERE deployment_id=?", dep);
        requireAbsent(receipt, "vectorizationRuns", "SELECT * FROM platform_vectorization_runs WHERE deployment_id=?", dep);
        requireAbsent(receipt, "runnerSessions", "SELECT * FROM platform_vectorization_runner_sessions WHERE deployment_id=?", dep);
        requireAbsent(receipt, "indexedPlans", "SELECT * FROM platform_vectorization_plans WHERE deployment_id=? AND (active_indexed_output_hash IS NOT NULL OR last_successful_indexed_output_hash IS NOT NULL OR last_run_id IS NOT NULL OR last_successful_run_id IS NOT NULL)", dep);
        requireAbsent(receipt, "managedVectors", "SELECT * FROM platform_deployment_managed_vector_resources WHERE deployment_id=?", dep);
        requireAbsent(receipt, "targetIndexes", "SELECT * FROM platform_tenant_scoped_vector_resources WHERE deployment_id=? AND (target_profile_id=? OR target_profile_id IS NULL)", dep, target);
        if (!rows("SELECT id FROM platform_consumers WHERE bound_deployment_id=? AND (bound_target_profile_id=? OR bound_target_profile_id IS NULL)", dep, target).isEmpty()) throw denied();
        requireDetachedHistory(receipt);
        requireFinancialAbsence(receipt);
    }

    private Receipt review(String kind, String key) {
        var found = rows("SELECT * FROM deployment_admission_reviews WHERE kind=? AND record_key=?", kind, key);
        if (found.size() != 1) throw denied();
        Receipt value = json.read((String) found.getFirst().get("payload_json"), Receipt.class);
        if (!json.fingerprint(value).equals(found.getFirst().get("fingerprint"))
            || !value.actorId().equals(found.getFirst().get("reviewed_by"))
            || !value.settledAt().equals(((java.sql.Timestamp) found.getFirst().get("reviewed_at")).toInstant())
            || value.proof() == null || !"ADMISSION_QUEUED_TO_NO_EFFECT_BEFORE_RELEASE_PROVISIONING_V1".equals(value.proof().executionBoundary())) throw denied();
        return value;
    }

    private void requireOwners(Receipt receipt, String destinationId, String sourceOffer) {
        String owner = null;
        for (String dep : List.of(receipt.deploymentId(), destinationId).stream().sorted().toList()) {
            var found = rows("""
                SELECT e.*,u.status AS owner_status,u.role AS owner_role,u.customer_id AS owner_customer,
                       c.status AS customer_status,t.status AS tenant_status
                  FROM platform_customer_offer_deployments d
                  JOIN platform_customer_offer_entitlements e ON e.customer_id=d.customer_id AND e.offer_id=d.offer_id
                  JOIN platform_users u ON u.id=e.owner_user_id
                  JOIN platform_customers c ON c.id=d.customer_id
                  JOIN platform_customer_tenants t ON t.id=d.tenant_id AND t.customer_id=c.id
                 WHERE d.deployment_id=?
                """, dep);
            if (found.size() != 1) throw denied();
            var row = found.getFirst();
            if (!receipt.customerId().equals(row.get("customer_id")) || !receipt.customerId().equals(row.get("owner_customer"))
                || !"ACTIVE".equals(row.get("status")) || !"ACTIVE".equals(row.get("owner_status"))
                || !"CUSTOMER_ADMIN".equals(row.get("owner_role")) || !"ACTIVE".equals(row.get("customer_status"))
                || !"ACTIVE".equals(row.get("tenant_status")) || !((java.sql.Timestamp) row.get("expires_at")).toInstant().isAfter(Instant.now())) throw denied();
            if (dep.equals(receipt.deploymentId()) && !sourceOffer.equals(row.get("offer_id"))) throw denied();
            if (owner != null && !owner.equals(row.get("owner_user_id"))) throw denied();
            owner = (String) row.get("owner_user_id");
        }
    }

    private String requireReleases(Receipt receipt) {
        var r = receipt.reservation();
        var releases = rows("SELECT * FROM platform_deployment_releases WHERE deployment_id=? AND (target_profile_id=? OR target_profile_id IS NULL) ORDER BY id", r.deploymentId(), r.targetProfileId());
        var bindings = rows("SELECT * FROM deployment_admission_release_bindings WHERE reservation_id=? OR (deployment_id=? AND target_profile_id=?) ORDER BY release_id", r.id(), r.deploymentId(), r.targetProfileId());
        var expected = receipt.command().failedReleases();
        if (expected == null || expected.isEmpty() || releases.size() != expected.size() || bindings.size() != expected.size()) throw denied();
        for (int i = 0; i < releases.size(); i++) {
            if (!expected.get(i).releaseId().equals(releases.get(i).get("id")) || !expected.get(i).releaseId().equals(bindings.get(i).get("release_id"))
                || !"NO_EFFECT".equals(bindings.get(i).get("state"))) throw denied();
        }
        var proofs = java.util.stream.IntStream.range(0, releases.size()).mapToObj(i -> new RecordProof(expected.get(i).releaseId(),
            json.fingerprint(Map.of("release", releases.get(i), "binding", bindings.get(i))))).toList();
        if (!proofs.equals(receipt.proof().releases())) throw denied();
        var authorizations = rows("SELECT * FROM deployment_admission_authorizations WHERE reservation_id=? ORDER BY operation_key", r.id());
        if (authorizations.size() != expected.size()) throw denied();
        var byKey = new java.util.HashMap<String, AuthorizationReceipt>();
        var offers = new java.util.HashSet<String>();
        for (var row : authorizations) {
            var a = json.read((String) row.get("payload_json"), AuthorizationReceipt.class);
            if (!r.id().equals(a.reservationId()) || !r.generation().equals(a.generation())
                || !r.id().equals(row.get("reservation_id")) || !r.generation().equals(row.get("generation"))
                || !r.customerId().equals(row.get("customer_id")) || !r.deploymentId().equals(row.get("deployment_id"))
                || !r.targetProfileId().equals(row.get("target_profile_id")) || !receipt.command().versionId().equals(row.get("version_id"))
                || !a.requestFingerprint().equals(row.get("request_fingerprint")) || !a.contextFingerprint().equals(row.get("context_fingerprint"))
                || !a.command().operationKey().equals(row.get("operation_key")) || !a.command().customerContext().operation().name().equals(row.get("operation"))
                || byKey.put(a.command().operationKey(), a) != null) throw denied();
            offers.add(a.command().customerContext().offerId());
        }
        var authProofs = expected.stream().map(value -> {
            var a = byKey.get(value.authorizationOperationKey());
            if (a == null || !value.authorizationRequestFingerprint().equals(a.requestFingerprint())) throw denied();
            return new RecordProof(value.authorizationOperationKey(), json.fingerprint(a));
        }).toList();
        if (!authProofs.equals(receipt.proof().authorizations())) throw denied();
        if (offers.size() != 1 || offers.contains(null)) throw denied();
        return offers.iterator().next();
    }

    private void requireDetachedHistory(Receipt receipt) {
        var history = rows("SELECT * FROM platform_consumer_binding_history WHERE from_deployment_id=? OR to_deployment_id=? ORDER BY consumer_entity_id,to_binding_revision", receipt.deploymentId(), receipt.deploymentId());
        if (history.size() != receipt.consumerDetachments().size() * 2) throw denied();
        for (var detached : receipt.consumerDetachments()) {
            var pair = history.stream().filter(row -> detached.consumerEntityId().equals(row.get("consumer_entity_id"))).toList();
            if (pair.size() != 2) throw denied();
            var first = pair.getFirst(); var last = pair.getLast();
            if (!receipt.customerId().equals(first.get("customer_id")) || !receipt.customerId().equals(last.get("customer_id"))
                || first.get("from_deployment_id") != null || first.get("from_release_id") != null || first.get("to_release_id") != null
                || !receipt.deploymentId().equals(first.get("to_deployment_id")) || !receipt.targetProfileId().equals(first.get("to_target_profile_id"))
                || !Objects.equals(0L, first.get("from_binding_revision")) || !Objects.equals(1L, first.get("to_binding_revision"))
                || !detached.historyId().equals(last.get("id")) || !detached.operationKey().equals(last.get("operation_key"))
                || !receipt.deploymentId().equals(last.get("from_deployment_id")) || !receipt.targetProfileId().equals(last.get("from_target_profile_id"))
                || last.get("from_release_id") != null || last.get("to_deployment_id") != null || last.get("to_release_id") != null
                || last.get("to_target_profile_id") != null || !Objects.equals(1L, last.get("from_binding_revision"))
                || !Objects.equals(2L, last.get("to_binding_revision"))) throw denied();
        }
    }

    private void requireFinancialAbsence(Receipt receipt) {
        var r = receipt.reservation();
        jdbc.queryForObject("SELECT id FROM provider_usage_grant_mutex WHERE id=1 FOR UPDATE NOWAIT", Integer.class);
        var grants = rows("SELECT * FROM provider_usage_grants WHERE customer_id=? AND target_profile_id=? ORDER BY id", r.customerId(), r.targetProfileId());
        var allocations = rows("SELECT * FROM provider_usage_allocations WHERE customer_id=? AND target_profile_id=? ORDER BY approval_id", r.customerId(), r.targetProfileId());
        for (var allocation : allocations) {
            JsonNode value = json.read((String) allocation.get("payload_json"), JsonNode.class);
            if (r.deploymentId().equals(allocation.get("deployment_id")) || r.deploymentId().equals(value.path("scope").path("deploymentId").textValue())
                || r.id().equals(value.path("initialAdmission").path("reservationId").textValue())) throw denied();
        }
        var events = rows("SELECT e.* FROM provider_usage_allowance_events e WHERE e.grant_id IN (SELECT id FROM provider_usage_grants WHERE customer_id=? AND target_profile_id=?) OR e.subject_id=? OR e.subject_id=? OR e.subject_id IN (SELECT id FROM platform_deployment_releases WHERE deployment_id=? AND target_profile_id=?) ORDER BY e.id",
            r.customerId(), r.targetProfileId(), r.id(), r.deploymentId(), r.deploymentId(), r.targetProfileId());
        for (var event : events) {
            if (Set.of("GRANT_APPROVED", "GRANT_VOID_UNUSED").contains(event.get("event_type"))) {
                if (grants.stream().noneMatch(g -> Objects.equals(g.get("id"), event.get("grant_id"))
                    && Objects.equals(g.get("id"), event.get("subject_id")))) throw denied();
                continue;
            }
            var other = allocations.stream().filter(a -> Objects.equals(a.get("approval_id"), event.get("subject_id"))
                && Objects.equals(a.get("grant_id"), event.get("grant_id"))).toList();
            if (other.size() != 1) throw denied();
            JsonNode value = json.read((String) other.getFirst().get("payload_json"), JsonNode.class);
            if (!Objects.equals(other.getFirst().get("deployment_id"), value.path("scope").path("deploymentId").textValue())
                || !Objects.equals(other.getFirst().get("approval_id"), value.path("approvalId").textValue())
                || !Objects.equals(other.getFirst().get("grant_id"), value.path("grantId").textValue())
                || !Set.of("ALLOWANCE_ALLOCATED", "ALLOWANCE_INITIALIZED", "ALLOWANCE_ACTIVE").contains(event.get("event_type"))) throw denied();
        }
        // Proved other-deployment spend can advance; no counters or financial rows are changed here.
    }

    private Map<String, Object> originalRetainedRow(Receipt settlement, Map<String, Object> current) {
        if (!"RETAINED_UNUSED".equals(current.get("status"))) return current;
        String id = (String) current.get("id");
        if (!PreservedExternalBindingKinds.VECTOR_STORAGE.equals(current.get("resource_kind"))) throw denied();
        var candidates = rows("""
            SELECT * FROM deployment_unused_vector_handoffs
             WHERE EXISTS (SELECT 1 FROM jsonb_array_elements(COALESCE(payload_json::jsonb->'sourceTransitions','[]'::jsonb)) t
                            WHERE t->>'bindingId'=?) ORDER BY id
            """, id);
        if (candidates.size() != 1) throw denied();
        var row = candidates.getFirst();
        var prior = json.read((String) row.get("payload_json"), com.ai.fabric.platform.backend.deployment.model.UnusedVectorBindingHandoffContracts.Receipt.class);
        JsonNode encoded = json.read((String) row.get("payload_json"), JsonNode.class);
        var unsigned = ((com.fasterxml.jackson.databind.node.ObjectNode) encoded).deepCopy(); unsigned.remove("fingerprint");
        if (!prior.id().equals(row.get("id")) || !prior.deploymentId().equals(row.get("deployment_id"))
            || !prior.targetProfileId().equals(row.get("target_profile_id")) || !prior.operationKey().equals(row.get("operation_key"))
            || !prior.commandFingerprint().equals(row.get("command_fingerprint")) || !prior.binding().bindingId().equals(row.get("binding_id"))
            || !prior.actorId().equals(row.get("actor_id")) || !prior.approvalReference().equals(row.get("approval_reference"))
            || !prior.handedOffAt().equals(((java.sql.Timestamp) row.get("handed_off_at")).toInstant())
            || !prior.fingerprint().equals(row.get("fingerprint")) || !prior.fingerprint().equals(json.fingerprint(unsigned))
            || !settlement.customerId().equals(prior.customerId()) || !settlement.targetProfileId().equals(prior.targetProfileId())
            || prior.sources().stream().noneMatch(source -> id.equals(source.bindingId())
                && settlement.id().equals(source.knownNoEffectSettlementReceiptId())
                && source.cancellationReceiptId() == null)) throw denied();
        var transitions = prior.sourceTransitions().stream().filter(value -> id.equals(value.bindingId())).toList();
        if (transitions.size() != 1) throw denied();
        var transition = transitions.getFirst();
        if (!settlement.id().equals(transition.knownNoEffectSettlementReceiptId()) || !"ACTIVE".equals(transition.fromStatus())
            || !"RETAINED_UNUSED".equals(transition.toStatus()) || transition.beforeUpdatedAt() == null || transition.transitionedAt() == null
            || transition.beforeUpdatedAt().isAfter(transition.transitionedAt()) || transition.transitionedAt().isAfter(prior.handedOffAt())
            || !transition.transitionedAt().equals(((java.sql.Timestamp) current.get("updated_at")).toInstant())
            || !transition.afterRowFingerprint().equals(json.fingerprint(current))) throw denied();
        var before = new java.util.TreeMap<>(current);
        before.put("status", "ACTIVE"); before.put("updated_at", java.sql.Timestamp.from(transition.beforeUpdatedAt()));
        if (!transition.beforeRowFingerprint().equals(json.fingerprint(before)) || settlement.proof().retainedExternalBindings().stream()
            .noneMatch(value -> id.equals(value.id()) && transition.beforeRowFingerprint().equals(value.fingerprint()))) throw denied();
        var audit = rows("SELECT * FROM platform_audit_events WHERE action='EXTERNAL_VECTOR_UNUSED_HANDOFF' AND target_id=? AND details_json::jsonb->>'receiptId'=?", prior.deploymentId(), prior.id());
        if (audit.size() != 1 || !"PLATFORM_ADMIN".equals(audit.getFirst().get("actor_role"))
            || !prior.actorId().equals(audit.getFirst().get("actor_id"))) throw denied();
        JsonNode details = json.read((String) audit.getFirst().get("details_json"), JsonNode.class);
        if (!prior.fingerprint().equals(details.path("fingerprint").textValue())
            || !json.fingerprint(prior.sourceTransitions()).equals(json.fingerprint(details.path("sourceTransitions")))) throw denied();
        return before;
    }

    private void requireAbsent(Receipt receipt, String key, String sql, Object... args) {
        var found = rows(sql, args);
        if (!found.isEmpty()) throw denied();
        unchanged(receipt, key, found);
    }
    private void unchanged(Receipt receipt, String key, Object value) {
        if (!json.fingerprint(value).equals(receipt.proof().inventoryFingerprints().get(key))) throw denied();
    }
    private List<Map<String, Object>> rows(String sql, Object... args) { return jdbc.queryForList(sql + " FOR UPDATE NOWAIT", args); }
    private boolean text(Object value) { return value != null && !value.toString().isBlank(); }
    private AdmissionFailure denied() { return new AdmissionFailure(CONFLICT, "KNOWN_NO_EFFECT_VECTOR_HANDOFF_SOURCE_CHANGED"); }
}
