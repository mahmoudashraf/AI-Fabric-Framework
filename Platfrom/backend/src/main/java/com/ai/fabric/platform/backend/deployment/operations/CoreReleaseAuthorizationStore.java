package com.ai.fabric.platform.backend.deployment.operations;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import static com.ai.fabric.platform.backend.deployment.operations.DeploymentOperationsContracts.*;

/** Core-owned authorization is independent of optional policy reservations and their database. */
@Component
public class CoreReleaseAuthorizationStore {
    public record Authorization(String releaseId, String customerId, String tenantId, String deploymentId,
        String targetProfileId, String versionId, String versionConfigHash, String sourceArtifactId,
        String operationKey, String actorId, String providerUsageMode, EffectiveDecision operations, Instant createdAt) { }
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public CoreReleaseAuthorizationStore(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc; this.mapper = mapper;
    }

    public void save(DeploymentEntity deployment, DeploymentVersionEntity version, DeploymentReleaseEntity release,
                     String operationKey, String providerUsageMode, EffectiveDecision decision) {
        if (!deployment.getId().equals(version.getDeploymentId()) || !deployment.getId().equals(release.getDeploymentId())
            || !version.getId().equals(release.getDeploymentVersionId()) || !"PUBLISHED".equals(version.getStatus())
            || version.getConfigHash() == null || release.getTargetProfileId() == null)
            throw new IllegalStateException("CORE_RELEASE_AUTHORIZATION_SCOPE_CHANGED");
        String key = operationKey == null || operationKey.isBlank() ? release.getId() : operationKey;
        String actor = PlatformSecurityContext.actorIdOrSystem();
        try {
            jdbc.update("""
                INSERT INTO platform_release_authorizations
                (release_id,customer_id,tenant_id,deployment_id,target_profile_id,version_id,version_config_hash,
                 source_artifact_id,operation_key,actor_id,provider_usage_mode,operations_mode,decision,reason,
                 request_fingerprint,decision_json,created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, release.getId(), deployment.getCustomerId(), deployment.getTenantId(), deployment.getId(),
                release.getTargetProfileId(), version.getId(), version.getConfigHash(), release.getSourceArtifactId(),
                key, actor, providerUsageMode, decision.mode(), decision.status(), decision.reason(),
                decision.requestFingerprint(), mapper.writeValueAsString(decision), Timestamp.from(Instant.now()));
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("CORE_RELEASE_AUTHORIZATION_ENCODING_FAILED");
        }
    }

    public Optional<Authorization> find(String releaseId) {
        if (releaseId == null) return Optional.empty();
        List<Authorization> rows = jdbc.query("SELECT * FROM platform_release_authorizations WHERE release_id=?", (rs, index) -> {
            EffectiveDecision decision;
            try { decision = mapper.readValue(rs.getString("decision_json"), EffectiveDecision.class); }
            catch (Exception error) { throw new IllegalStateException("CORE_RELEASE_AUTHORIZATION_CHANGED"); }
            if (decision == null || !Objects.equals(decision.mode(), rs.getString("operations_mode"))
                || !Objects.equals(decision.status(), rs.getString("decision"))
                || !Objects.equals(decision.reason(), rs.getString("reason"))
                || !Objects.equals(decision.requestFingerprint(), rs.getString("request_fingerprint")))
                throw new IllegalStateException("CORE_RELEASE_AUTHORIZATION_CHANGED");
            return new Authorization(rs.getString("release_id"), rs.getString("customer_id"), rs.getString("tenant_id"),
                rs.getString("deployment_id"), rs.getString("target_profile_id"), rs.getString("version_id"),
                rs.getString("version_config_hash"), rs.getString("source_artifact_id"), rs.getString("operation_key"),
                rs.getString("actor_id"), rs.getString("provider_usage_mode"), decision, rs.getTimestamp("created_at").toInstant());
        }, releaseId);
        return rows.stream().findFirst();
    }

    public Authorization require(DeploymentEntity deployment, DeploymentVersionEntity version, DeploymentReleaseEntity release) {
        var authorization = find(release.getId()).orElseThrow(() -> new IllegalStateException("CORE_RELEASE_AUTHORIZATION_REQUIRED"));
        if (!Objects.equals(deployment.getCustomerId(), authorization.customerId())
            || !Objects.equals(deployment.getTenantId(), authorization.tenantId())
            || !Objects.equals(deployment.getId(), authorization.deploymentId())
            || !Objects.equals(release.getDeploymentId(), authorization.deploymentId())
            || !Objects.equals(release.getTargetProfileId(), authorization.targetProfileId())
            || !Objects.equals(release.getDeploymentVersionId(), authorization.versionId())
            || !Objects.equals(version.getId(), authorization.versionId())
            || !Objects.equals(version.getDeploymentId(), deployment.getId())
            || !"PUBLISHED".equals(version.getStatus())
            || !Objects.equals(version.getConfigHash(), authorization.versionConfigHash())
            || !Objects.equals(release.getSourceArtifactId(), authorization.sourceArtifactId()))
            throw new IllegalStateException("CORE_RELEASE_AUTHORIZATION_SCOPE_CHANGED");
        return authorization;
    }
}
