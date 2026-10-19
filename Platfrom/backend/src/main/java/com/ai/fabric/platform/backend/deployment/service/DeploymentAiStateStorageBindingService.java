package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentProviderResourceHandleEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentAiStateStorageContracts.Request;
import com.ai.fabric.platform.backend.deployment.model.DeploymentAiStateStorageContracts.Summary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.model.PreservedExternalBindingKinds;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentProviderResourceHandleRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementGateService;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** A version-approved connection to retained AI-only schemas. No provider lifecycle operation is available here. */
@Service
public class DeploymentAiStateStorageBindingService {
    public static final String RESOURCE_KIND = PreservedExternalBindingKinds.AI_STATE_STORAGE;
    private static final String LIFECYCLE = "RETAIN_EXTERNAL";
    private final DeploymentRepository deployments;
    private final DeploymentVersionRepository versions;
    private final DeploymentTargetProfileRepository targets;
    private final DeploymentProviderResourceHandleRepository handles;
    private final PlatformSecretService secrets;
    private final TargetRetirementGateService retirement;
    private final PlatformAuditService audit;
    private final DeploymentAiStateStorageProbe probe;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public DeploymentAiStateStorageBindingService(DeploymentRepository deployments, DeploymentVersionRepository versions,
        DeploymentTargetProfileRepository targets, DeploymentProviderResourceHandleRepository handles,
        PlatformSecretService secrets, TargetRetirementGateService retirement, PlatformAuditService audit,
        DeploymentAiStateStorageProbe probe, JdbcTemplate jdbc, ObjectMapper mapper) {
        this.deployments = deployments; this.versions = versions; this.targets = targets; this.handles = handles;
        this.secrets = secrets; this.retirement = retirement; this.audit = audit; this.probe = probe;
        this.jdbc = jdbc; this.mapper = mapper;
    }

    @Transactional
    public Summary bind(String deploymentId, String targetId, Request request) {
        if (request == null) throw denied("AI_STATE_STORAGE_REQUEST_REQUIRED");
        DeploymentEntity deployment = deployments.findByIdForUpdate(deploymentId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        DeploymentTargetProfileEntity target = target(targetId);
        DeploymentVersionEntity version = version(deployment, request.versionId());
        requireExternal(deployment, version, target);
        retirement.requireAvailableForMutation(deploymentId, targetId);
        if (!Boolean.TRUE.equals(request.migrationAuthorized())) throw denied("AI_STATE_STORAGE_MIGRATION_AUTHORIZATION_REQUIRED");
        String approval = field(request.approvalReference(), 255);
        DeploymentAiStateStorageConnection connection = connection(request);
        requireAllowedHost(connection.host(), target);
        var existing = handles.findFirstByDeploymentIdAndTargetProfileIdAndResourceKindOrderByUpdatedAtDesc(
            deploymentId, targetId, RESOURCE_KIND);
        DeploymentProviderResourceHandleEntity handle;
        if (existing.isPresent()) {
            handle = existing.get();
            requireBinding(deployment, target, handle);
            requireStore(deployment, target, handle, connection);
            var previous = approval(handle, version);
            if (previous != null) {
                var generation = generation(deployment, target, version, handle, previous);
                if (!approval.equals(previous.approvalReference())) throw conflict("AI_STATE_STORAGE_APPROVAL_IMMUTABLE");
                if (!generation.connection().equals(connection)) throw conflict("AI_STATE_STORAGE_VERSION_CREDENTIALS_IMMUTABLE");
                probe.verify(connection);
                return summary(deployment, targetId, version, handle, previous);
            }
            requireNeverApplied(deployment, target, version);
            probe.verify(connection);
        } else {
            requireNeverApplied(deployment, target, version);
            probe.verify(connection);
            Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
            String id = "asb-" + UUID.randomUUID();
            ObjectNode identity = mapper.createObjectNode().put("customerId", deployment.getCustomerId())
                .put("tenantId", deployment.getTenantId()).put("deploymentId", deploymentId).put("targetProfileId", targetId)
                .put("mode", "CUSTOMER_POSTGRES").put("lifecycle", LIFECYCLE).put("externalResourceOwned", false)
                .put("migrationAuthorized", true).put("dedicatedAiSchemas", true)
                .put("runtimeSchemaIdentity", schemaIdentity(connection, connection.runtimeSchema()))
                .put("connectorSchemaIdentity", schemaIdentity(connection, connection.connectorSchema()));
            String store = schemaIdentity(connection, null);
            identity.put("storeIdentity", store);
            identity.put("fingerprint", hash(identity));
            handle = new DeploymentProviderResourceHandleEntity();
            handle.setId(id); handle.setDeploymentId(deploymentId); handle.setTargetProfileId(targetId);
            handle.setProviderType(target.getProviderType()); handle.setResourceKind(RESOURCE_KIND);
            handle.setProviderResourceUuid("external-ai-state:" + store); handle.setStatus("ACTIVE");
            handle.setLastObservedStatus("VERIFIED_AI_SCHEMAS"); handle.setLastObservedAt(now);
            handle.setCreatedAt(now); handle.setUpdatedAt(now); handle.setMetadataJson(identity.toString());
            handles.saveAndFlush(handle);
            try {
                for (String schema : List.of(connection.runtimeSchema(), connection.connectorSchema())) {
                    jdbc.update("""
                        insert into deployment_ai_state_schema_claims
                          (schema_identity,binding_id,customer_id,tenant_id,deployment_id,target_profile_id) values (?,?,?,?,?,?)
                        """, schemaIdentity(connection, schema), id, deployment.getCustomerId(), deployment.getTenantId(), deploymentId, targetId);
                }
            } catch (DataIntegrityViolationException error) {
                throw conflict("AI_STATE_STORAGE_SCHEMA_ALREADY_BOUND");
            }
        }
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        String generationId = "asg-" + UUID.randomUUID();
        String token = generationId.substring(4).replace("-", "").toUpperCase(Locale.ROOT);
        String ref = "MANAGED_EXTERNAL_AI_STATE_CONNECTION_" + token;
        String keyRef = "MANAGED_EXTERNAL_AI_STATE_FINGERPRINT_" + token;
        // New names only. Staging a candidate must never rewrite a live generation's vault entries.
        if (secrets.resolveSecret(ref) != null || secrets.resolveSecret(keyRef) != null)
            throw conflict("AI_STATE_STORAGE_GENERATION_SECRET_ALREADY_EXISTS");
        byte[] random = new byte[32]; new SecureRandom().nextBytes(random);
        String fingerprintKey = Base64.getEncoder().encodeToString(random);
        String encoded = encode(connection);
        ObjectNode generation = mapper.createObjectNode().put("generationId", generationId).put("bindingId", handle.getId())
            .put("customerId", deployment.getCustomerId()).put("tenantId", deployment.getTenantId())
            .put("deploymentId", deploymentId).put("targetProfileId", targetId).put("versionId", version.getId())
            .put("configHash", version.getConfigHash()).put("bindingFingerprint", metadata(handle).path("fingerprint").asText())
            .put("targetFingerprint", targetFingerprint(target)).put("migrationAuthorized", true)
            .put("approvalReference", approval).put("approvedAt", now.toString())
            .put("connectionSecretRef", ref).put("credentialFingerprintSecretRef", keyRef);
        generation.put("credentialDigest", digest(hash(generation) + "\n" + encoded, fingerprintKey));
        String fingerprint = hash(generation);
        Map<String, Object> ownership = Map.of("deploymentId", deploymentId, "targetProfileId", targetId,
            "versionId", version.getId(), "generationId", generationId, "purpose", "EXTERNAL_AI_STATE_CONNECTION",
            "externalResourceOwned", false);
        secrets.upsertManagedSecret(ref, encoded, ownership);
        secrets.upsertManagedSecret(keyRef, fingerprintKey, ownership);
        jdbc.update("""
            insert into deployment_ai_state_storage_approvals
            (binding_id,version_id,config_hash,binding_fingerprint,approval_reference,approved_at) values (?,?,?,?,?,?)
            """, handle.getId(), version.getId(), version.getConfigHash(), fingerprint, approval, Timestamp.from(now));
        jdbc.update("""
            insert into deployment_ai_state_storage_credential_generations
            (binding_id,version_id,generation_id,generation_json,fingerprint,created_at) values (?,?,?,?,?,?)
            """, handle.getId(), version.getId(), generationId, generation.toString(), fingerprint, Timestamp.from(now));
        audit.record("EXTERNAL_AI_STATE_BOUND", "DEPLOYMENT", deploymentId,
            Map.of("bindingId", handle.getId(), "targetProfileId", targetId, "versionId", version.getId(),
                "generationId", generationId, "fingerprint", fingerprint, "externalResourceOwned", false));
        return summary(deployment, targetId, version, handle,
            new Approval(version.getConfigHash(), fingerprint, approval, now));
    }

    public Summary get(String deploymentId, String targetId, String versionId) {
        var deployment = deployments.findById(deploymentId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var version = version(deployment, versionId); var target = target(targetId);
        var resolved = validated(deployment, version, target);
        return summary(deployment, targetId, version, resolved.handle(), resolved.approval());
    }

    /** Local immutable scope/credential check for readiness; no provider reachability claim is made. */
    public CoolifyRuntimeDatabaseBinding resolveLocal(DeploymentEntity deployment, DeploymentVersionEntity version,
                                                     DeploymentTargetProfileEntity target) {
        var resolved = validated(deployment, version, target);
        var connection = resolved.connection();
        return new CoolifyRuntimeDatabaseBinding("CUSTOMER_POSTGRES", null, connection.runtimeJdbcUrl(),
            connection.username(), connection.password(), null, resolved.handle().getId(),
            resolved.approval().bindingFingerprint(), connection.runtimeSchema(), connection.connectorSchema(),
            connection.connectorJdbcUrl());
    }

    /** Used before apply/promote/retry effects and again by provisioning, never to provision PostgreSQL. */
    public CoolifyRuntimeDatabaseBinding resolve(DeploymentEntity deployment, DeploymentVersionEntity version,
                                                DeploymentTargetProfileEntity target) {
        var resolved = validated(deployment, version, target);
        probe.verify(resolved.connection());
        return resolveLocal(deployment, version, target);
    }

    /** Freeze only non-secret identity. Replays must use the same store even after backend restart. */
    public void freeze(DeploymentEntity deployment, DeploymentVersionEntity version, DeploymentReleaseEntity release) {
        JsonNode snapshot = snapshot(deployment, version, release);
        if (snapshot == null) return;
        if (release.getAiStateStorageFingerprint() != null || release.getAiStateStorageSnapshotJson() != null) {
            requireUnchanged(deployment, version, release);
            return;
        }
        release.setAiStateStorageSnapshotJson(snapshot.toString());
        release.setAiStateStorageFingerprint(hash(snapshot));
    }

    public void requireUnchanged(DeploymentEntity deployment, DeploymentVersionEntity version,
                                 DeploymentReleaseEntity release) {
        JsonNode current = snapshot(deployment, version, release);
        if (current == null && release.getAiStateStorageFingerprint() == null
            && release.getAiStateStorageSnapshotJson() == null) return;
        if (current == null || release.getAiStateStorageFingerprint() == null
            || release.getAiStateStorageSnapshotJson() == null
            || !hash(current).equals(release.getAiStateStorageFingerprint())
            || !hash(json(release.getAiStateStorageSnapshotJson())).equals(release.getAiStateStorageFingerprint())) {
            throw conflict("AI_STATE_STORAGE_RELEASE_BINDING_CHANGED");
        }
    }

    public void requireForRecovery(DeploymentEntity deployment, DeploymentReleaseEntity release) {
        var version = versions.findById(release.getDeploymentVersionId())
            .orElseThrow(() -> denied("AI_STATE_STORAGE_PUBLISHED_VERSION_REQUIRED"));
        requireUnchanged(deployment, version, release);
    }

    private JsonNode snapshot(DeploymentEntity deployment, DeploymentVersionEntity version,
                              DeploymentReleaseEntity release) {
        var mode = DeploymentAiStateStorageSelection.selected(json(version.getProviderConfigJson()));
        if (mode == null) return null;
        if (!deployment.getId().equals(version.getDeploymentId()) || !deployment.getId().equals(release.getDeploymentId())
            || !version.getId().equals(release.getDeploymentVersionId()) || release.getId() == null
            || release.getTargetProfileId() == null || !"PUBLISHED".equals(version.getStatus())
            || version.getConfigHash() == null || version.getConfigHash().isBlank()) {
            throw denied("AI_STATE_STORAGE_RELEASE_SCOPE_CHANGED");
        }
        var target = target(release.getTargetProfileId());
        if (target.getProviderType() != DeploymentProviderType.COOLIFY || deployment.getCustomerId() == null
            || deployment.getCustomerId().isBlank() || deployment.getTenantId() == null || deployment.getTenantId().isBlank())
            throw denied("AI_STATE_STORAGE_COMPLETE_SCOPE_REQUIRED");
        ObjectNode snapshot = mapper.createObjectNode().put("customerId", deployment.getCustomerId())
            .put("tenantId", deployment.getTenantId()).put("deploymentId", deployment.getId())
            .put("targetProfileId", target.getId()).put("releaseId", release.getId())
            .put("versionId", version.getId()).put("configHash", version.getConfigHash()).put("mode", mode.name());
        snapshot.put("targetFingerprint", targetFingerprint(target));
        if (mode == com.ai.fabric.platform.backend.deployment.model.AiStateStorageMode.CUSTOMER_POSTGRES) {
            var resolved = validated(deployment, version, target);
            snapshot.put("bindingId", resolved.handle().getId())
                .put("bindingFingerprint", resolved.approval().bindingFingerprint())
                .put("credentialGenerationId", resolved.generationId()).put("lifecycle", LIFECYCLE);
        }
        return snapshot;
    }

    public void requireForApply(DeploymentEntity deployment, DeploymentVersionEntity version, String targetId) {
        var target = target(targetId);
        var mode = DeploymentAiStateStorageSelection.selected(json(version.getProviderConfigJson()));
        if (mode == null) return;
        if (target.getProviderType() != DeploymentProviderType.COOLIFY)
            throw denied("AI_STATE_STORAGE_TARGET_REQUIRED");
        if (!deployment.getId().equals(version.getDeploymentId()) || !"PUBLISHED".equals(version.getStatus())
            || version.getConfigHash() == null || version.getConfigHash().isBlank())
            throw denied("AI_STATE_STORAGE_PUBLISHED_VERSION_REQUIRED");
        if (mode == com.ai.fabric.platform.backend.deployment.model.AiStateStorageMode.CUSTOMER_POSTGRES) {
            resolve(deployment, version, target);
        } else {
            if (json(target.getResourceDefaultsJson()).has("runtimeDatabaseJdbcUrl"))
                throw denied("AI_STATE_STORAGE_RAW_JDBC_OVERRIDE_FORBIDDEN");
            if (handles.findFirstByDeploymentIdAndTargetProfileIdAndResourceKindOrderByUpdatedAtDesc(
                deployment.getId(), targetId, RESOURCE_KIND).isPresent())
                throw denied("AI_STATE_STORAGE_EXISTING_CUSTOMER_STORE_REQUIRES_MIGRATION");
        }
    }

    private Resolved validated(DeploymentEntity deployment, DeploymentVersionEntity version, DeploymentTargetProfileEntity target) {
        requireExternal(deployment, version, target);
        var handle = handles.findFirstByDeploymentIdAndTargetProfileIdAndResourceKindOrderByUpdatedAtDesc(
            deployment.getId(), target.getId(), RESOURCE_KIND).orElseThrow(() -> denied("AI_STATE_STORAGE_BINDING_REQUIRED"));
        requireBinding(deployment, target, handle);
        var approval = approval(handle, version);
        var generation = generation(deployment, target, version, handle, approval);
        return new Resolved(handle, approval, generation.connection(), generation.id());
    }

    private void requireBinding(DeploymentEntity deployment, DeploymentTargetProfileEntity target,
                                DeploymentProviderResourceHandleEntity handle) {
        var metadata = metadata(handle);
        ObjectNode integrity = metadata.deepCopy(); integrity.remove("fingerprint");
        if (!hash(integrity).equals(metadata.path("fingerprint").asText())) throw denied("AI_STATE_STORAGE_BINDING_CHANGED");
        if (!deployment.getId().equals(handle.getDeploymentId()) || !target.getId().equals(handle.getTargetProfileId())
            || !RESOURCE_KIND.equals(handle.getResourceKind()) || !"ACTIVE".equals(handle.getStatus())
            || target.getProviderType() != handle.getProviderType()
            || !deployment.getCustomerId().equals(metadata.path("customerId").asText())
            || !deployment.getTenantId().equals(metadata.path("tenantId").asText())
            || !deployment.getId().equals(metadata.path("deploymentId").asText())
            || !target.getId().equals(metadata.path("targetProfileId").asText())
            || !"CUSTOMER_POSTGRES".equals(metadata.path("mode").asText())
            || !LIFECYCLE.equals(metadata.path("lifecycle").asText())
            || !metadata.path("externalResourceOwned").isBoolean() || metadata.path("externalResourceOwned").asBoolean()
            || !metadata.path("migrationAuthorized").asBoolean() || !metadata.path("dedicatedAiSchemas").asBoolean())
            throw denied("AI_STATE_STORAGE_BINDING_SCOPE_CHANGED");
    }

    private Generation generation(DeploymentEntity deployment, DeploymentTargetProfileEntity target,
                                  DeploymentVersionEntity version, DeploymentProviderResourceHandleEntity handle, Approval approval) {
        if (approval == null || !version.getConfigHash().equals(approval.configHash()))
            throw denied("AI_STATE_STORAGE_VERSION_APPROVAL_REQUIRED");
        var rows = jdbc.query("""
            select generation_id,generation_json,fingerprint from deployment_ai_state_storage_credential_generations
             where binding_id=? and version_id=?
            """, (rs, row) -> new StoredGeneration(rs.getString(1), rs.getString(2), rs.getString(3)), handle.getId(), version.getId());
        if (rows.size() != 1) throw denied("AI_STATE_STORAGE_CREDENTIAL_GENERATION_REQUIRED");
        var stored = rows.getFirst();
        JsonNode decoded = json(stored.json());
        if (!decoded.isObject() || !hash(decoded).equals(stored.fingerprint())
            || !stored.fingerprint().equals(approval.bindingFingerprint())) throw denied("AI_STATE_STORAGE_GENERATION_CHANGED");
        ObjectNode value = (ObjectNode) decoded;
        if (!stored.id().matches("asg-[a-f0-9-]{36}") || !stored.id().equals(value.path("generationId").asText())
            || !handle.getId().equals(value.path("bindingId").asText())
            || !deployment.getCustomerId().equals(value.path("customerId").asText())
            || !deployment.getTenantId().equals(value.path("tenantId").asText())
            || !deployment.getId().equals(value.path("deploymentId").asText())
            || !target.getId().equals(value.path("targetProfileId").asText())
            || !version.getId().equals(value.path("versionId").asText())
            || !version.getConfigHash().equals(value.path("configHash").asText())
            || !metadata(handle).path("fingerprint").asText().equals(value.path("bindingFingerprint").asText())
            || !targetFingerprint(target).equals(value.path("targetFingerprint").asText())
            || !value.path("migrationAuthorized").asBoolean()
            || !approval.approvalReference().equals(value.path("approvalReference").asText())
            || !approval.approvedAt().toString().equals(value.path("approvedAt").asText()))
            throw denied("AI_STATE_STORAGE_GENERATION_SCOPE_CHANGED");
        String token = stored.id().substring(4).replace("-", "").toUpperCase(Locale.ROOT);
        String ref = "MANAGED_EXTERNAL_AI_STATE_CONNECTION_" + token;
        String keyRef = "MANAGED_EXTERNAL_AI_STATE_FINGERPRINT_" + token;
        if (!ref.equals(value.path("connectionSecretRef").asText())
            || !keyRef.equals(value.path("credentialFingerprintSecretRef").asText())) throw denied("AI_STATE_STORAGE_SECRET_SCOPE_CHANGED");
        String encoded = secrets.resolveSecret(ref); String key = secrets.resolveSecret(keyRef);
        if (encoded == null || encoded.isBlank() || key == null || key.isBlank()) throw denied("AI_STATE_STORAGE_CREDENTIALS_REQUIRED");
        ObjectNode integrity = value.deepCopy(); integrity.remove("credentialDigest");
        if (!digest(hash(integrity) + "\n" + encoded, key).equals(value.path("credentialDigest").asText()))
            throw denied("AI_STATE_STORAGE_CREDENTIALS_CHANGED");
        DeploymentAiStateStorageConnection connection;
        try {
            connection = mapper.readValue(encoded, DeploymentAiStateStorageConnection.class);
            connection = connection(new Request(null, connection.host(), connection.port(), connection.databaseName(),
                connection.runtimeSchema(), connection.connectorSchema(), connection.username(), connection.password(), true, null));
        } catch (Exception error) { throw denied("AI_STATE_STORAGE_CONNECTION_INVALID"); }
        requireStore(deployment, target, handle, connection);
        return new Generation(stored.id(), connection);
    }

    private void requireStore(DeploymentEntity deployment, DeploymentTargetProfileEntity target,
                               DeploymentProviderResourceHandleEntity handle, DeploymentAiStateStorageConnection connection) {
        var metadata = metadata(handle);
        requireAllowedHost(connection.host(), target);
        String store = schemaIdentity(connection, null);
        if (!store.equals(metadata.path("storeIdentity").asText())
            || !("external-ai-state:" + store).equals(handle.getProviderResourceUuid())
            || !schemaIdentity(connection, connection.runtimeSchema()).equals(metadata.path("runtimeSchemaIdentity").asText())
            || !schemaIdentity(connection, connection.connectorSchema()).equals(metadata.path("connectorSchemaIdentity").asText()))
            throw conflict("AI_STATE_STORAGE_BINDING_IMMUTABLE");
        for (String schema : List.of(connection.runtimeSchema(), connection.connectorSchema())) {
            List<String> owners = jdbc.queryForList("""
                select binding_id from deployment_ai_state_schema_claims
                 where schema_identity=? and customer_id=? and tenant_id=? and deployment_id=? and target_profile_id=?
                """, String.class, schemaIdentity(connection, schema), deployment.getCustomerId(), deployment.getTenantId(),
                deployment.getId(), target.getId());
            if (!owners.equals(List.of(handle.getId()))) throw denied("AI_STATE_STORAGE_SCHEMA_OWNERSHIP_CHANGED");
        }
    }

    private void requireNeverApplied(DeploymentEntity deployment, DeploymentTargetProfileEntity target, DeploymentVersionEntity version) {
        Long used = jdbc.queryForObject("""
            select count(*) from platform_deployment_releases where deployment_id=? and deployment_version_id=?
             and (target_profile_id=? or target_profile_id is null)
            """, Long.class, deployment.getId(), version.getId(), target.getId());
        if (used == null || used != 0)
            throw conflict("AI_STATE_STORAGE_NEVER_APPLIED_VERSION_REQUIRED");
        // The deployment-wide active pointer does not identify a target (e.g. staging-to-production promotion).
        // An unexplained active pointer is nevertheless not proof of a never-applied version.
        if (version.getId().equals(deployment.getActiveVersionId())) {
            Long history = jdbc.queryForObject("""
                select count(*) from platform_deployment_releases where deployment_id=? and deployment_version_id=?
                """, Long.class, deployment.getId(), version.getId());
            if (history == null || history == 0) throw conflict("AI_STATE_STORAGE_NEVER_APPLIED_VERSION_REQUIRED");
        }
    }

    private String targetFingerprint(DeploymentTargetProfileEntity target) {
        ObjectNode identity = mapper.createObjectNode().put("providerType", target.getProviderType().name())
            .put("credentialRefId", target.getCredentialRefId());
        identity.set("providerConfig", json(target.getProviderConfigJson()));
        identity.set("resourceDefaults", json(target.getResourceDefaultsJson()));
        identity.set("networkPolicy", json(target.getNetworkPolicyJson()));
        return hash(identity);
    }

    private void requireExternal(DeploymentEntity deployment, DeploymentVersionEntity version, DeploymentTargetProfileEntity target) {
        if (deployment == null || deployment.getId() == null || deployment.getCustomerId() == null || deployment.getCustomerId().isBlank()
            || deployment.getTenantId() == null || deployment.getTenantId().isBlank() || deployment.getArchivedAt() != null
            || target == null || !target.isActive() || target.getProviderType() != DeploymentProviderType.COOLIFY)
            throw denied("AI_STATE_STORAGE_COMPLETE_SCOPE_REQUIRED");
        if (version == null || !deployment.getId().equals(version.getDeploymentId()) || !"PUBLISHED".equals(version.getStatus())
            || version.getConfigHash() == null || version.getConfigHash().isBlank()) throw denied("AI_STATE_STORAGE_PUBLISHED_VERSION_REQUIRED");
        if (!DeploymentAiStateStorageSelection.external(json(version.getProviderConfigJson())))
            throw denied("AI_STATE_STORAGE_CUSTOMER_MODE_REQUIRED");
        if (json(target.getResourceDefaultsJson()).has("runtimeDatabaseJdbcUrl")
            || json(version.getProviderConfigJson()).has("runtimeDatabaseJdbcUrl"))
            throw denied("AI_STATE_STORAGE_RAW_JDBC_OVERRIDE_FORBIDDEN");
        boolean managed = handles.findByDeploymentIdAndTargetProfileIdOrderByUpdatedAtDesc(deployment.getId(), target.getId()).stream()
            .anyMatch(handle -> "RUNTIME_POSTGRES_DATABASE".equals(handle.getResourceKind()) && !"DELETED".equals(handle.getStatus()));
        if (managed) throw denied("AI_STATE_STORAGE_EXISTING_MANAGED_STORE_REQUIRES_MIGRATION");
    }

    private DeploymentTargetProfileEntity target(String id) {
        return targets.findById(field(id, 64)).filter(DeploymentTargetProfileEntity::isActive)
            .orElseThrow(() -> denied("AI_STATE_STORAGE_TARGET_REQUIRED"));
    }

    private DeploymentVersionEntity version(DeploymentEntity deployment, String id) {
        return versions.findById(field(id, 64)).filter(value -> deployment.getId().equals(value.getDeploymentId()))
            .orElseThrow(() -> denied("AI_STATE_STORAGE_PUBLISHED_VERSION_REQUIRED"));
    }

    private Approval approval(DeploymentProviderResourceHandleEntity handle, DeploymentVersionEntity version) {
        List<Approval> approvals = jdbc.query("""
            select config_hash,binding_fingerprint,approval_reference,approved_at
              from deployment_ai_state_storage_approvals where binding_id=? and version_id=?
            """, (rs, row) -> new Approval(rs.getString(1), rs.getString(2), rs.getString(3), rs.getTimestamp(4).toInstant()),
            handle.getId(), version.getId());
        return approvals.isEmpty() ? null : approvals.getFirst();
    }

    private Summary summary(DeploymentEntity deployment, String target, DeploymentVersionEntity version,
                            DeploymentProviderResourceHandleEntity handle, Approval approval) {
        return new Summary(handle.getId(), deployment.getCustomerId(), deployment.getTenantId(), deployment.getId(), target,
            version.getId(), version.getConfigHash(), "CUSTOMER_POSTGRES", LIFECYCLE, true,
            approval.bindingFingerprint(), approval.approvedAt());
    }

    private DeploymentAiStateStorageConnection connection(Request request) {
        String host = field(request.host(), 253).toLowerCase(Locale.ROOT);
        if (!host.matches("[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?") || host.contains("..")) throw denied("AI_STATE_STORAGE_HOST_INVALID");
        if (request.port() == null || request.port() < 1 || request.port() > 65535) throw denied("AI_STATE_STORAGE_PORT_INVALID");
        String database = identifier(request.databaseName()); String runtime = identifier(request.runtimeSchema());
        String connector = identifier(request.connectorSchema()); String username = identifier(request.username());
        if (runtime.equals(connector) || forbiddenSchema(runtime) || forbiddenSchema(connector)) throw denied("AI_STATE_STORAGE_DEDICATED_SCHEMAS_REQUIRED");
        String password = field(request.password(), 4096);
        return new DeploymentAiStateStorageConnection(host, request.port(), database, runtime, connector, username, password);
    }

    private boolean forbiddenSchema(String value) {
        return "public".equals(value) || "information_schema".equals(value) || value.startsWith("pg_");
    }

    private void requireAllowedHost(String host, DeploymentTargetProfileEntity target) {
        JsonNode allowed = json(target.getNetworkPolicyJson()).path("aiStateStorageAllowedHosts");
        if (!allowed.isArray()) throw denied("AI_STATE_STORAGE_ENDPOINT_NOT_APPROVED");
        for (JsonNode item : allowed) if (item.isTextual() && host.equals(item.textValue())) return;
        throw denied("AI_STATE_STORAGE_ENDPOINT_NOT_APPROVED");
    }

    private String schemaIdentity(DeploymentAiStateStorageConnection connection, String schema) {
        ObjectNode identity = mapper.createObjectNode().put("host", connection.host()).put("port", connection.port())
            .put("database", connection.databaseName());
        if (schema != null) identity.put("schema", schema);
        else identity.set("schemas", mapper.valueToTree(java.util.stream.Stream.of(connection.runtimeSchema(), connection.connectorSchema()).sorted().toList()));
        return hash(identity);
    }

    private ObjectNode metadata(DeploymentProviderResourceHandleEntity handle) {
        JsonNode value = json(handle.getMetadataJson());
        if (!value.isObject()) throw denied("AI_STATE_STORAGE_METADATA_INVALID");
        return (ObjectNode) value;
    }
    private JsonNode json(String value) {
        try { return mapper.readTree(value == null ? "{}" : value); }
        catch (Exception error) { throw denied("AI_STATE_STORAGE_CONFIG_INVALID"); }
    }
    private String identifier(String value) {
        if (!field(value, 63).matches("[a-z][a-z0-9_]{0,62}")) throw denied("AI_STATE_STORAGE_IDENTIFIER_INVALID");
        return value;
    }
    private String field(String value, int max) {
        if (value == null || value.isBlank() || value.length() > max || !value.equals(value.trim())
            || value.chars().anyMatch(Character::isISOControl)) throw denied("AI_STATE_STORAGE_FIELD_INVALID");
        return value;
    }
    private String encode(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception error) { throw denied("AI_STATE_STORAGE_CONNECTION_INVALID"); }
    }
    private String hash(JsonNode value) { return DeploymentEvidenceFingerprint.hash(mapper, value); }
    private String digest(String value, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) { throw denied("AI_STATE_STORAGE_FINGERPRINT_FAILED"); }
    }
    private ResponseStatusException denied(String code) { return new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, code); }
    private ResponseStatusException conflict(String code) { return new ResponseStatusException(HttpStatus.CONFLICT, code); }
    private record Approval(String configHash, String bindingFingerprint, String approvalReference, Instant approvedAt) { }
    private record StoredGeneration(String id, String json, String fingerprint) { }
    private record Generation(String id, DeploymentAiStateStorageConnection connection) { }
    private record Resolved(DeploymentProviderResourceHandleEntity handle, Approval approval,
                            DeploymentAiStateStorageConnection connection, String generationId) { }
}
