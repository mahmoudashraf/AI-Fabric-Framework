package com.ai.fabric.platform.backend.deployment.budget;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.operations.CoreReleaseAuthorizationStore;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageAllowanceFailure.*;

/** Financial issuance requires immutable core authorization, never an operational reservation. */
final class ProviderUsageAuthorizedReleaseReader {
    enum Phase { READ, PREPARE, INITIALIZE }
    record Release(String id, String deploymentId, String versionId, String targetProfileId,
                   String status, String provisioningStatus, String sourceArtifactId) {
        DeploymentReleaseEntity entity() {
            var release = new DeploymentReleaseEntity();
            release.setId(id); release.setDeploymentId(deploymentId); release.setDeploymentVersionId(versionId);
            release.setTargetProfileId(targetProfileId); release.setStatus(status);
            release.setProvisioningStatus(provisioningStatus); release.setSourceArtifactId(sourceArtifactId);
            return release;
        }
    }

    record CoreAuthorization(String releaseId, String customerId, String tenantId, String deploymentId,
                             String targetProfileId, String versionId, String versionConfigHash, String sourceArtifactId,
                             String operationKey, String actorId, String providerUsageMode, String decision,
                             String requestFingerprint) {
        void requireScope(DeploymentEntity deployment, DeploymentReleaseEntity release) {
            if (deployment == null || release == null || !Objects.equals(releaseId, release.getId())
                || !Objects.equals(customerId, deployment.getCustomerId()) || !Objects.equals(tenantId, deployment.getTenantId())
                || !Objects.equals(deploymentId, deployment.getId()) || !Objects.equals(deploymentId, release.getDeploymentId())
                || !Objects.equals(targetProfileId, release.getTargetProfileId()) || !Objects.equals(versionId, release.getDeploymentVersionId())
                || !Objects.equals(sourceArtifactId, release.getSourceArtifactId()))
                throw conflict("PROVIDER_USAGE_CORE_AUTHORIZATION_SCOPE_CHANGED");
        }

        ProviderUsageAllowanceContracts.CoreReleaseContext context() {
            return new ProviderUsageAllowanceContracts.CoreReleaseContext(releaseId, versionId, versionConfigHash, requestFingerprint);
        }
    }

    private final JdbcTemplate jdbc;
    ProviderUsageAuthorizedReleaseReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Release release(String releaseId, Phase phase) {
        var rows = jdbc.query("""
            SELECT id,deployment_id,deployment_version_id,target_profile_id,status,provisioning_status,source_artifact_id
              FROM platform_deployment_releases WHERE id=?
            """, (rs, row) -> new Release(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7)), releaseId);
        if (rows.size() != 1) throw missing("PROVIDER_USAGE_RELEASE_REQUIRED");
        var release = rows.getFirst();
        Set<String> allowed = phase == Phase.READ
            ? Set.of("APPLY_REQUESTED", "PRE_APPLY_VERIFYING", "PROVISIONING", "VERIFYING", "APPLIED_VERIFIED")
            : Set.of("PRE_APPLY_VERIFYING", "PROVISIONING", "VERIFYING", "APPLIED_VERIFIED");
        if (!allowed.contains(release.status()) || !Set.of("QUEUED", "RUNNING", "ACTIVE").contains(release.provisioningStatus())
            || (phase == Phase.INITIALIZE
            && (!Set.of("VERIFYING", "APPLIED_VERIFIED").contains(release.status()) || !"ACTIVE".equals(release.provisioningStatus()))))
            throw conflict("PROVIDER_USAGE_RELEASE_PHASE_DENIED");
        return release;
    }

    static Optional<CoreAuthorization> coreAuthorization(CoreReleaseAuthorizationStore store, String releaseId) {
        Optional<CoreReleaseAuthorizationStore.Authorization> recorded;
        try { recorded = store.find(releaseId); }
        catch (IllegalStateException error) { throw conflict("PROVIDER_USAGE_CORE_AUTHORIZATION_CHANGED"); }
        if (recorded.isEmpty()) return Optional.empty();
        var record = recorded.get();
        var decision = record.operations();
        if (decision == null) throw conflict("PROVIDER_USAGE_CORE_AUTHORIZATION_CHANGED");
        var authorization = new CoreAuthorization(record.releaseId(), record.customerId(), record.tenantId(),
            record.deploymentId(), record.targetProfileId(), record.versionId(), record.versionConfigHash(),
            record.sourceArtifactId(), record.operationKey(), record.actorId(), record.providerUsageMode(),
            decision.status(), decision.requestFingerprint());
        for (String value : new String[] {authorization.releaseId(), authorization.customerId(), authorization.tenantId(),
            authorization.deploymentId(), authorization.targetProfileId(), authorization.versionId(), authorization.versionConfigHash(),
            authorization.operationKey(), authorization.actorId(), authorization.requestFingerprint()})
            if (value == null || value.isBlank()) throw conflict("PROVIDER_USAGE_CORE_AUTHORIZATION_CHANGED");
        if (authorization.decision() == null || !Set.of("ALLOWED", "OBSERVED", "BYPASSED").contains(authorization.decision()))
            throw conflict("PROVIDER_USAGE_CORE_AUTHORIZATION_DENIED");
        ProviderUsageModeResolver.mode(authorization.providerUsageMode());
        return Optional.of(authorization);
    }

}
