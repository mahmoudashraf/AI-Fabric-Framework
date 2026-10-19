package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.admission.AdmissionUnusedReservationEvidence;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentProviderResourceHandleEntity;
import com.ai.fabric.platform.backend.deployment.model.PreservedExternalBindingKinds;
import com.ai.fabric.platform.backend.deployment.model.UnusedVectorTransferContracts.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import static org.springframework.http.HttpStatus.CONFLICT;

/** Local no-effect verification, including genuine binding-only releases without invented reservations. */
@Component
public class UnusedVectorTransferEvidence {
    private final JdbcTemplate jdbc;
    private final UnusedVectorTransferJson json;
    private final AdmissionUnusedReservationEvidence cancellations;
    public UnusedVectorTransferEvidence(JdbcTemplate jdbc, UnusedVectorTransferJson json, AdmissionUnusedReservationEvidence cancellations) {
        this.jdbc = jdbc; this.json = json; this.cancellations = cancellations;
    }

    public JsonNode metadata(DeploymentProviderResourceHandleEntity h) {
        JsonNode meta = json.read(h.getMetadataJson(), JsonNode.class);
        if (!meta.isObject()) throw denied();
        ObjectNode unsigned = ((ObjectNode) meta).deepCopy(); unsigned.remove("fingerprint");
        if (!json.hash(unsigned).equals(meta.path("fingerprint").asText()) || !"RETAIN_EXTERNAL".equals(meta.path("lifecycle").asText())
            || !meta.path("externalResourceOwned").isBoolean() || meta.path("externalResourceOwned").booleanValue()
            || !h.getDeploymentId().equals(meta.path("deploymentId").asText())
            || !h.getTargetProfileId().equals(meta.path("targetProfileId").asText())
            || !h.getProviderResourceUuid().equals(meta.path("clusterId").asText())) throw denied();
        return meta;
    }
    public Scope scope(DeploymentProviderResourceHandleEntity h, ControlPlane cp) {
        return scope(h, cp, true);
    }
    private Scope scope(DeploymentProviderResourceHandleEntity h, ControlPlane cp, boolean requireUnarchived) {
        JsonNode m = metadata(h);
        var owner = jdbc.query("SELECT customer_id,tenant_id,archived_at FROM platform_deployments WHERE id=?", (rs, row) ->
            (!requireUnarchived || rs.getTimestamp(3) == null) && m.path("customerId").asText().equals(rs.getString(1)) && m.path("tenantId").asText().equals(rs.getString(2)), h.getDeploymentId());
        Integer version = jdbc.queryForObject("SELECT COUNT(*) FROM platform_deployment_versions WHERE id=? AND deployment_id=? AND status='PUBLISHED' AND config_hash=?",
            Integer.class, m.path("versionId").asText(), h.getDeploymentId(), m.path("configHash").asText());
        if (owner.size() != 1 || !owner.getFirst() || version == null || version != 1) throw denied();
        return new Scope(cp, m.path("customerId").asText(), m.path("tenantId").asText(), h.getDeploymentId(), h.getTargetProfileId(),
            m.path("versionId").asText(), m.path("configHash").asText(), m.path("collectionPrefix").asText());
    }
    public VectorIdentity vector(DeploymentProviderResourceHandleEntity h) {
        JsonNode m = metadata(h);
        return new VectorIdentity(h.getProviderResourceUuid(), m.path("projectId").asText(), m.path("regionId").asText(),
            m.path("deploymentOption").asText(), m.path("endpointHost").asText(), m.path("databaseName").asText(), h.getProviderType().name());
    }
    public ReleasedBinding verify(DeploymentProviderResourceHandleEntity h, Source supplied, Scope destination, ControlPlane cp) {
        Scope source = scope(h, cp);
        JsonNode m = metadata(h);
        if (!h.getId().equals(supplied.bindingId()) || !m.path("fingerprint").asText().equals(supplied.expectedBindingFingerprint())
            || h.getReleaseId() != null || !PreservedExternalBindingKinds.VECTOR_STORAGE.equals(h.getResourceKind())
            || source.collectionPrefix().equals(destination.collectionPrefix())) throw denied();
        String cancellation = supplied.cancellationReceiptId();
        if ("RETAINED_UNUSED".equals(h.getStatus())) {
            if (cancellation == null) throw denied();
            cancellations.requireUnusedHandoffSource(h, cancellation, supplied.expectedBindingFingerprint(), source.customerId(),
                m.path("approvalReference").asText(), destination.collectionPrefix());
        } else if (!"ACTIVE".equals(h.getStatus()) || cancellation != null) throw denied();
        String noEffects = noEffects(h, cancellation);
        return new ReleasedBinding(h.getId(), source, m.path("fingerprint").asText(), identity(h), cancellation, h.getStatus(), noEffects);
    }
    public void verifyClosed(DeploymentProviderResourceHandleEntity h, ReleasedBinding proof) {
        if (!ExternalVectorClusterLocks.RELEASED.equals(h.getStatus()) || !proof.identityFingerprint().equals(identity(h))
            || !proof.scope().equals(scope(h, proof.scope().controlPlane(), false))
            || !proof.metadataFingerprint().equals(metadata(h).path("fingerprint").asText())
            || !proof.noEffectFingerprint().equals(noEffects(h, proof.cancellationReceiptId()))) throw denied();
    }
    public void requireConfigurationOnlyDetachment(DeploymentProviderResourceHandleEntity handle) {
        if (!"ACTIVE".equals(handle.getStatus()) || handle.getReleaseId() != null) throw denied();
        noEffects(handle, null, true);
    }
    private String noEffects(DeploymentProviderResourceHandleEntity h, String cancellationId) {
        return noEffects(h, cancellationId, false);
    }
    private String noEffects(DeploymentProviderResourceHandleEntity h, String cancellationId, boolean detachingConfiguration) {
        String dep = h.getDeploymentId(), target = h.getTargetProfileId();
        if (cancellationId == null) absent("SELECT COUNT(*) FROM deployment_capacity_reservations WHERE deployment_id=? AND target_profile_id=?", dep, target);
        else {
            var receipt = cancellations.requireTransferCancellation(h, cancellationId);
            absent("SELECT COUNT(*) FROM deployment_capacity_reservations WHERE deployment_id=? AND target_profile_id=? AND id<>?", dep, target, receipt.reservation().id());
            absent("SELECT COUNT(*) FROM deployment_admission_events WHERE reservation_id=? AND event_type NOT IN ('RESERVED','ALLOCATION_OPERATION_AUTHORIZED','CANCELLED_UNUSED_NO_EFFECT')", receipt.reservation().id());
        }
        absent("SELECT COUNT(*) FROM platform_deployment_releases WHERE deployment_id=? AND (target_profile_id=? OR target_profile_id IS NULL)", dep, target);
        absent("SELECT COUNT(*) FROM deployment_admission_release_bindings WHERE deployment_id=? AND target_profile_id=?", dep, target);
        absent("SELECT COUNT(*) FROM deployment_managed_dependency_graphs WHERE deployment_id=? AND target_profile_id=?", dep, target);
        absent("SELECT COUNT(*) FROM provider_usage_allocations WHERE deployment_id=? AND target_profile_id=?", dep, target);
        absent("SELECT COUNT(*) FROM platform_target_retirement_operations WHERE deployment_id=? AND target_profile_id=?", dep, target);
        absent("SELECT COUNT(*) FROM platform_deployment_deletion_operations WHERE deployment_id=?", dep);
        if (!detachingConfiguration)
            absent("SELECT COUNT(*) FROM platform_consumers WHERE bound_deployment_id=? AND (bound_target_profile_id=? OR bound_target_profile_id IS NULL)", dep, target);
        var gate = jdbc.query("SELECT id,state,epoch,operation_id,row_version FROM platform_target_retirement_gates WHERE deployment_id=? AND target_profile_id=? FOR UPDATE NOWAIT", (rs, row) -> {
            if (!"AVAILABLE".equals(rs.getString("state")) || rs.getString("operation_id") != null || rs.getLong("epoch") < 0) throw denied();
            return List.of(rs.getString("id"), rs.getLong("epoch"), rs.getLong("row_version"));
        }, dep, target);
        if (gate.size() > 1 || detachingConfiguration && gate.size() != 1) throw denied();
        var resources = jdbc.query("SELECT id,release_id,target_profile_id,resource_kind,status,metadata_json,provider_resource_uuid FROM deployment_provider_resource_handles WHERE deployment_id=? AND (target_profile_id=? OR target_profile_id IS NULL) ORDER BY id", (rs, row) -> {
            String kind = rs.getString("resource_kind");
            if (rs.getString("release_id") != null || !target.equals(rs.getString("target_profile_id")) || !PreservedExternalBindingKinds.isPreserved(kind)) throw denied();
            if (PreservedExternalBindingKinds.VECTOR_STORAGE.equals(kind) && !h.getId().equals(rs.getString("id"))) throw denied();
            JsonNode meta = json.read(rs.getString("metadata_json"), JsonNode.class);
            String status = rs.getString("status");
            if (!h.getId().equals(rs.getString("id")) && !List.of("ACTIVE", "CONFIGURED").contains(status)) throw denied();
            if (PreservedExternalBindingKinds.DOCUMENT_STORAGE.equals(kind)
                && (!meta.path("readOnly").asBoolean() || meta.path("deleteSourceOnRemoval").asBoolean()
                    || !"CUSTOMER_MANAGED".equals(meta.path("storageOwnership").asText()))) throw denied();
            return List.of(rs.getString("id"), kind, meta, rs.getString("provider_resource_uuid"));
        }, dep, target);
        var authorities = jdbc.query("SELECT id,version_id,release_id,fingerprint,binding_json FROM platform_runtime_authority_snapshots WHERE deployment_id=? AND target_profile_id=? ORDER BY id", (rs, row) -> {
            JsonNode value = json.read(rs.getString("binding_json"), JsonNode.class);
            JsonNode original = metadata(h);
            if (rs.getString("release_id") != null || !original.path("versionId").asText().equals(rs.getString("version_id"))
                || !original.path("customerId").asText().equals(value.path("customerId").asText()) || !original.path("tenantId").asText().equals(value.path("tenantId").asText())
                || !dep.equals(value.path("deploymentId").asText()) || !target.equals(value.path("targetProfileId").asText())
                || !original.path("versionId").asText().equals(value.path("versionId").asText()) || !original.path("configHash").asText().equals(value.path("versionConfigHash").asText())
                || !value.path("generation").isIntegralNumber() || value.path("generation").longValue() <= 0) throw denied();
            Integer matching = jdbc.queryForObject("SELECT COUNT(*) FROM platform_runtime_target_authorities WHERE deployment_id=? AND target_profile_id=? AND customer_id=? AND tenant_id=? AND generation=?",
                Integer.class, dep, target, original.path("customerId").asText(), original.path("tenantId").asText(), value.path("generation").longValue());
            if (matching == null || matching != 1) throw denied();
            return List.of(rs.getString("id"), rs.getString("fingerprint"), value.path("generation").longValue());
        }, dep, target);
        return json.hash(Map.of("gate", gate, "resources", resources, "authorities", authorities));
    }
    private String identity(DeploymentProviderResourceHandleEntity h) {
        var fields = new TreeMap<String, Object>();
        fields.put("id", h.getId()); fields.put("deployment", h.getDeploymentId()); fields.put("target", h.getTargetProfileId());
        fields.put("release", h.getReleaseId()); fields.put("kind", h.getResourceKind()); fields.put("provider", h.getProviderType());
        fields.put("cluster", h.getProviderResourceUuid()); fields.put("project", h.getProviderProjectUuid());
        fields.put("fqdn", h.getFqdn()); fields.put("created", h.getCreatedAt()); fields.put("metadata", json.read(h.getMetadataJson(), JsonNode.class));
        return json.hash(fields);
    }
    private void absent(String sql, Object... args) { Long n = jdbc.queryForObject(sql, Long.class, args); if (n == null || n != 0) throw denied(); }
    private ResponseStatusException denied() { return new ResponseStatusException(CONFLICT, "UNUSED_VECTOR_TRANSFER_SOURCE_NOT_UNUSED"); }
}
