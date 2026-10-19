package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.deployment.model.PreservedExternalBindingKinds;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentProviderResourceHandleEntity;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.TreeMap;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static org.springframework.http.HttpStatus.CONFLICT;

/** Local reads inside the cancellation transaction. No provider clients or credential resolution. */
@Component
public final class AdmissionUnusedReservationEvidence {
    public static final String RETAINED_UNUSED = "RETAINED_UNUSED";
    private final JdbcTemplate jdbc;
    private final AdmissionJson json;

    public AdmissionUnusedReservationEvidence(JdbcTemplate jdbc, AdmissionJson json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    UnusedReservationEvidence read(Reservation reservation, Manifest manifest) {
        return read(reservation, manifest, List.of());
    }

    UnusedReservationEvidence read(Reservation reservation, Manifest manifest, List<String> authorizedConsumerIds) {
        String deployment = reservation.deploymentId();
        String target = reservation.targetProfileId();
        var owners = jdbc.query("SELECT customer_id,tenant_id FROM platform_deployments WHERE id=?",
            (rs, row) -> List.of(rs.getString(1), rs.getString(2)), deployment);
        if (owners.size() != 1 || !owners.getFirst().equals(List.of(reservation.customerId(), reservation.tenantId())))
            throw denied("UNUSED_CANCELLATION_OWNERSHIP_CHANGED");
        var versions = jdbc.query("SELECT deployment_id,status,config_hash FROM platform_deployment_versions WHERE id=?",
            (rs, row) -> List.of(rs.getString(1), rs.getString(2), rs.getString(3)), manifest.versionId());
        if (versions.size() != 1 || !versions.getFirst().equals(List.of(deployment, "PUBLISHED", manifest.versionConfigHash())))
            throw denied("UNUSED_CANCELLATION_VERSION_CHANGED");

        absent("SELECT COUNT(*) FROM platform_deployment_releases WHERE deployment_id=? AND (target_profile_id=? OR target_profile_id IS NULL)",
            deployment, target);
        // All states/generations count, including NO_EFFECT and SUPERSEDED.
        absent("SELECT COUNT(*) FROM deployment_admission_release_bindings WHERE reservation_id=? OR (deployment_id=? AND target_profile_id=?)",
            reservation.id(), deployment, target);
        absent("SELECT COUNT(*) FROM deployment_managed_dependency_graphs WHERE deployment_id=? AND target_profile_id=?",
            deployment, target);
        absent("SELECT COUNT(*) FROM provider_usage_allocations WHERE deployment_id=? AND target_profile_id=?",
            deployment, target);
        absent("SELECT COUNT(*) FROM platform_target_retirement_operations WHERE deployment_id=? AND target_profile_id=?",
            deployment, target);
        absent("SELECT COUNT(*) FROM platform_target_retirement_gates WHERE deployment_id=? AND target_profile_id=? AND (state<>'AVAILABLE' OR epoch<>0 OR operation_id IS NOT NULL)",
            deployment, target);
        absent("SELECT COUNT(*) FROM platform_deployment_deletion_operations WHERE deployment_id=?", deployment);
        var consumers = jdbc.queryForList("SELECT id FROM platform_consumers WHERE bound_deployment_id=? AND (bound_target_profile_id=? OR bound_target_profile_id IS NULL) ORDER BY id",
            String.class, deployment, target);
        if (!consumers.equals(authorizedConsumerIds)) throw denied("UNUSED_CANCELLATION_CONSUMER_ASSIGNED");
        absent("SELECT COUNT(*) FROM deployment_admission_events WHERE reservation_id=? AND event_type NOT IN ('RESERVED','ALLOCATION_OPERATION_AUTHORIZED')",
            reservation.id());

        var retained = jdbc.query("""
            SELECT * FROM deployment_provider_resource_handles
             WHERE deployment_id=? AND (target_profile_id=? OR target_profile_id IS NULL) ORDER BY id FOR UPDATE NOWAIT
            """, (rs, row) -> {
            String kind = rs.getString("resource_kind");
            if (!PreservedExternalBindingKinds.isPreserved(kind) || rs.getString("release_id") != null
                || !target.equals(rs.getString("target_profile_id")))
                throw denied("UNUSED_CANCELLATION_RESOURCE_HISTORY_PRESENT");
            JsonNode metadata = json.read(rs.getString("metadata_json"), JsonNode.class);
            if (metadata == null || !metadata.isObject()) throw denied("UNUSED_CANCELLATION_BINDING_METADATA_INVALID");
            if (PreservedExternalBindingKinds.VECTOR_STORAGE.equals(kind)
                || PreservedExternalBindingKinds.AI_STATE_STORAGE.equals(kind)) {
                if (!"ACTIVE".equals(rs.getString("status")) || !"RETAIN_EXTERNAL".equals(metadata.path("lifecycle").textValue())
                    || !metadata.path("externalResourceOwned").isBoolean() || metadata.path("externalResourceOwned").booleanValue()
                    || !reservation.customerId().equals(metadata.path("customerId").textValue())
                    || !reservation.tenantId().equals(metadata.path("tenantId").textValue())
                    || !deployment.equals(metadata.path("deploymentId").textValue())
                    || !target.equals(metadata.path("targetProfileId").textValue()))
                    throw denied("UNUSED_CANCELLATION_BINDING_METADATA_INVALID");
                if (PreservedExternalBindingKinds.AI_STATE_STORAGE.equals(kind)
                    && (!metadata.path("migrationAuthorized").asBoolean()
                        || !metadata.path("dedicatedAiSchemas").asBoolean()
                        || !"CUSTOMER_POSTGRES".equals(metadata.path("mode").asText())))
                    throw denied("UNUSED_CANCELLATION_BINDING_METADATA_INVALID");
            } else if (!"CONFIGURED".equals(rs.getString("status"))
                || !"CUSTOMER_MANAGED".equals(metadata.path("storageOwnership").textValue())
                || !metadata.path("readOnly").isBoolean() || !metadata.path("readOnly").booleanValue()
                || !metadata.path("deleteSourceOnRemoval").isBoolean() || metadata.path("deleteSourceOnRemoval").booleanValue()) {
                throw denied("UNUSED_CANCELLATION_BINDING_METADATA_INVALID");
            }
            var handle = new DeploymentProviderResourceHandleEntity();
            handle.setId(rs.getString("id")); handle.setDeploymentId(deployment); handle.setTargetProfileId(target);
            handle.setResourceKind(kind); handle.setReleaseId(rs.getString("release_id"));
            handle.setProviderType(com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType.valueOf(rs.getString("provider_type")));
            handle.setProviderResourceUuid(rs.getString("provider_resource_uuid"));
            handle.setProviderProjectUuid(rs.getString("provider_project_uuid"));
            handle.setProviderEnvironmentUuid(rs.getString("provider_environment_uuid"));
            handle.setProviderServerUuid(rs.getString("provider_server_uuid")); handle.setFqdn(rs.getString("fqdn"));
            handle.setCreatedAt(rs.getTimestamp("created_at").toInstant()); handle.setMetadataJson(rs.getString("metadata_json"));
            return new RetainedBindingMetadata(handle.getId(), kind, identity(handle),
                PreservedExternalBindingKinds.VECTOR_STORAGE.equals(kind)
                    ? BindingCancellationDisposition.RETAINED_UNUSED : BindingCancellationDisposition.PRESERVED_METADATA);
        }, deployment, target);

        var candidates = jdbc.query("""
            SELECT id,version_id,release_id,fingerprint,binding_json FROM platform_runtime_authority_snapshots
             WHERE deployment_id=? AND target_profile_id=? ORDER BY id
            """, (rs, row) -> {
            JsonNode binding = json.read(rs.getString("binding_json"), JsonNode.class);
            if (rs.getString("release_id") != null || !manifest.versionId().equals(rs.getString("version_id"))
                || binding == null || !reservation.customerId().equals(binding.path("customerId").textValue())
                || !reservation.tenantId().equals(binding.path("tenantId").textValue())
                || !deployment.equals(binding.path("deploymentId").textValue()) || !target.equals(binding.path("targetProfileId").textValue())
                || !manifest.versionId().equals(binding.path("versionId").textValue())
                || !manifest.versionConfigHash().equals(binding.path("versionConfigHash").textValue())
                || !binding.path("generation").isIntegralNumber() || binding.path("generation").longValue() <= 0)
                throw denied("UNUSED_CANCELLATION_AUTHORITY_HISTORY_PRESENT");
            long generation = binding.path("generation").longValue();
            Long matched = jdbc.queryForObject("""
                SELECT COUNT(*) FROM platform_runtime_target_authorities
                 WHERE deployment_id=? AND target_profile_id=? AND customer_id=? AND tenant_id=? AND generation=?
                """, Long.class, deployment, target, reservation.customerId(), reservation.tenantId(), generation);
            if (matched == null || matched != 1) throw denied("UNUSED_CANCELLATION_AUTHORITY_CHANGED");
            return new UnusedAuthorityCandidate(rs.getString("id"), rs.getString("fingerprint"), generation);
        }, deployment, target);
        return new UnusedReservationEvidence(retained, candidates);
    }


    void retainUnusedBindings(Reservation reservation, UnusedReservationEvidence evidence, Instant now) {
        for (var binding : evidence.retainedBindings()) {
            if (binding.disposition() != BindingCancellationDisposition.RETAINED_UNUSED) continue;
            int changed = jdbc.update("""
                UPDATE deployment_provider_resource_handles SET status=?,updated_at=?
                 WHERE id=? AND deployment_id=? AND target_profile_id=? AND resource_kind=? AND status='ACTIVE' AND release_id IS NULL
                """, RETAINED_UNUSED, Timestamp.from(now), binding.handleId(), reservation.deploymentId(),
                reservation.targetProfileId(), PreservedExternalBindingKinds.VECTOR_STORAGE);
            if (changed != 1) throw denied("UNUSED_CANCELLATION_BINDING_CHANGED");
        }
    }

    /** Requires an exact committed cancellation for this historical handle, not merely an inactive status. */
    public boolean permitsFreshBinding(DeploymentProviderResourceHandleEntity handle, String customerId,
                                        String tenantId, String approvalReference, String newPrefix) {
        if (!RETAINED_UNUSED.equals(handle.getStatus()) || !PreservedExternalBindingKinds.VECTOR_STORAGE.equals(handle.getResourceKind())
            || handle.getReleaseId() != null || newPrefix == null || newPrefix.isBlank()) return false;
        JsonNode metadata = json.read(handle.getMetadataJson(), JsonNode.class);
        if (!customerId.equals(metadata.path("customerId").textValue()) || !tenantId.equals(metadata.path("tenantId").textValue())
            || !approvalReference.equals(metadata.path("approvalReference").textValue())
            || !metadata.path("collectionPrefix").isTextual() || metadata.path("collectionPrefix").textValue().isBlank()
            || newPrefix.equals(metadata.path("collectionPrefix").textValue())) return false;
        var receipts = jdbc.query("""
            SELECT c.*,r.payload_json AS reservation_json FROM deployment_admission_unused_cancellations c
             JOIN deployment_capacity_reservations r ON r.id=c.reservation_id AND r.generation=c.generation
             WHERE r.deployment_id=? AND r.target_profile_id=? AND r.state='RELEASED'
            """, (rs, row) -> {
                var receipt = json.read(rs.getString("payload_json"), UnusedReservationCancellationReceipt.class);
                var current = json.read(rs.getString("reservation_json"), Reservation.class);
                if (!rs.getString("id").equals(receipt.id()) || !rs.getString("command_fingerprint").equals(receipt.commandFingerprint())
                    || !rs.getString("operation_key").equals(receipt.operationKey())
                    || !rs.getString("reservation_id").equals(receipt.reservation().id())
                    || !rs.getString("generation").equals(receipt.reservation().generation())
                    || !rs.getTimestamp("cancelled_at").toInstant().equals(receipt.cancelledAt())
                    || !current.equals(receipt.reservation())) throw denied("UNUSED_CANCELLATION_RECEIPT_CHANGED");
                Long events = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM deployment_admission_events WHERE reservation_id=? AND revision=?
                     AND event_type='CANCELLED_UNUSED_NO_EFFECT' AND evidence_reference=? AND actor_id=?
                    """, Long.class, current.id(), current.revision(), "unused-reservation-cancellation:" + receipt.id(), receipt.actorId());
                if (events == null || events != 1) throw denied("UNUSED_CANCELLATION_RECEIPT_CHANGED");
                return receipt;
            },
            handle.getDeploymentId(), handle.getTargetProfileId());
        return receipts.stream().anyMatch(receipt -> {
            Reservation reservation = receipt.reservation();
            return receipt.outcome() == CancellationOutcome.CANCELLED_UNUSED_NO_EFFECT && !receipt.reclamationPerformed()
                && reservation.state() == State.RELEASED && reservation.expiryState() == ExpiryState.RELEASED
                && customerId.equals(reservation.customerId()) && tenantId.equals(reservation.tenantId())
                && handle.getDeploymentId().equals(reservation.deploymentId()) && handle.getTargetProfileId().equals(reservation.targetProfileId())
                && receipt.evidence().retainedBindings().stream().anyMatch(binding -> binding.handleId().equals(handle.getId())
                    && binding.disposition() == BindingCancellationDisposition.RETAINED_UNUSED
                    && binding.resourceKind().equals(handle.getResourceKind()) && binding.identityFingerprint().equals(identity(handle)));
        });
    }

    private String identity(DeploymentProviderResourceHandleEntity handle) {
        var fields = new TreeMap<String, Object>();
        fields.put("id", handle.getId()); fields.put("deploymentId", handle.getDeploymentId());
        fields.put("targetProfileId", handle.getTargetProfileId()); fields.put("releaseId", handle.getReleaseId());
        fields.put("resourceKind", handle.getResourceKind()); fields.put("providerType", handle.getProviderType());
        fields.put("providerResourceUuid", handle.getProviderResourceUuid()); fields.put("providerProjectUuid", handle.getProviderProjectUuid());
        fields.put("providerEnvironmentUuid", handle.getProviderEnvironmentUuid()); fields.put("providerServerUuid", handle.getProviderServerUuid());
        fields.put("fqdn", handle.getFqdn()); fields.put("createdAt", handle.getCreatedAt());
        fields.put("metadata", json.read(handle.getMetadataJson(), JsonNode.class));
        return json.fingerprint(fields);
    }

    private void absent(String sql, Object... arguments) {
        Long count = jdbc.queryForObject(sql, Long.class, arguments);
        if (count == null || count != 0) throw denied("UNUSED_CANCELLATION_EFFECT_HISTORY_PRESENT");
    }

    private AdmissionFailure denied(String code) { return new AdmissionFailure(CONFLICT, code); }
}
