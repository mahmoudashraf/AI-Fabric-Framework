package com.ai.fabric.platform.backend.deployment.budget;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation;
import com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageAllowanceFailure.*;

/** New releases use immutable core authorization. Admission reads are confined to legacy releases. */
final class ProviderUsageAuthorizedReleaseReader {
    enum Phase { READ, PREPARE, INITIALIZE }
    record Release(String id, String deploymentId, String versionId, String targetProfileId,
                   String status, String provisioningStatus, String sourceArtifactId) { }

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
    private final ProviderUsageAllowanceCodec codec;

    ProviderUsageAuthorizedReleaseReader(JdbcTemplate jdbc, ProviderUsageAllowanceCodec codec) {
        this.jdbc = jdbc; this.codec = codec;
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

    static Optional<CoreAuthorization> coreAuthorization(JdbcTemplate jdbc, String releaseId) {
        var rows = jdbc.query("""
            SELECT release_id,customer_id,tenant_id,deployment_id,target_profile_id,version_id,version_config_hash,
                   source_artifact_id,operation_key,actor_id,provider_usage_mode,decision,request_fingerprint
              FROM platform_release_authorizations WHERE release_id=?
            """, (rs, row) -> new CoreAuthorization(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9), rs.getString(10),
                rs.getString(11), rs.getString(12), rs.getString(13)), releaseId);
        if (rows.isEmpty()) return Optional.empty();
        if (rows.size() != 1) throw conflict("PROVIDER_USAGE_CORE_AUTHORIZATION_CHANGED");
        var authorization = rows.getFirst();
        for (String value : new String[] {authorization.releaseId(), authorization.customerId(), authorization.tenantId(),
            authorization.deploymentId(), authorization.targetProfileId(), authorization.versionId(), authorization.versionConfigHash(),
            authorization.operationKey(), authorization.actorId(), authorization.requestFingerprint()})
            if (value == null || value.isBlank()) throw conflict("PROVIDER_USAGE_CORE_AUTHORIZATION_CHANGED");
        if (authorization.decision() == null || !Set.of("ALLOWED", "OBSERVED", "BYPASSED").contains(authorization.decision()))
            throw conflict("PROVIDER_USAGE_CORE_AUTHORIZATION_DENIED");
        ProviderUsageModeResolver.mode(authorization.providerUsageMode());
        return Optional.of(authorization);
    }

    ReleaseBinding binding(String releaseId, boolean lock) {
        var rows = jdbc.query("SELECT * FROM deployment_admission_release_bindings WHERE release_id=?" + (lock ? " FOR UPDATE" : ""),
            (rs, row) -> new ReleaseBinding(rs.getString("release_id"), rs.getString("reservation_id"), rs.getString("generation"),
                rs.getString("authorization_key"), rs.getString("version_id"), rs.getString("version_config_hash"),
                rs.getString("customer_id"), rs.getString("tenant_id"), rs.getString("deployment_id"),
                rs.getString("target_profile_id"), rs.getString("state")), releaseId);
        if (rows.size() != 1) throw conflict("PROVIDER_USAGE_RELEASE_BINDING_REQUIRED");
        return rows.getFirst();
    }

    void validate(Release release, ReleaseBinding binding, Reservation reservation, Manifest manifest, Phase phase) {
        var allowed = phase == Phase.READ ? Set.of("QUEUED", "PROVISIONING", "ACTIVE") : Set.of("PROVISIONING", "ACTIVE");
        var current = jdbc.query("""
            SELECT release_id FROM deployment_admission_release_bindings
             WHERE reservation_id=? AND generation=? AND state NOT IN ('SUPERSEDED','NO_EFFECT')
             ORDER BY CASE WHEN state='ACTIVE' THEN 1 ELSE 0 END,created_at DESC,release_id DESC
            """, (rs, row) -> rs.getString(1), reservation.id(), reservation.generation());
        if (!allowed.contains(binding.state()) || current.isEmpty() || !release.id().equals(current.getFirst())
            || !release.id().equals(binding.releaseId()) || !release.deploymentId().equals(binding.deploymentId())
            || !release.versionId().equals(binding.versionId()) || !release.targetProfileId().equals(binding.targetProfileId())
            || !binding.customerId().equals(reservation.customerId()) || !binding.tenantId().equals(reservation.tenantId())
            || !binding.deploymentId().equals(reservation.deploymentId()) || !binding.targetProfileId().equals(reservation.targetProfileId())
            || !binding.generation().equals(reservation.generation()) || !binding.versionConfigHash().equals(manifest.versionConfigHash())
            || !binding.versionId().equals(manifest.versionId()) || reservation.expiryState() != ExpiryState.CURRENT
            || (phase != Phase.READ && reservation.state() != State.PROVISIONING && reservation.state() != State.ACTIVE))
            throw conflict("PROVIDER_USAGE_CURRENT_RELEASE_REQUIRED");
        validateAuthorization(binding, reservation, manifest);
    }

    private void validateAuthorization(ReleaseBinding binding, Reservation reservation, Manifest manifest) {
        var receipts = jdbc.query("""
            SELECT * FROM deployment_admission_authorizations
             WHERE customer_id=? AND deployment_id=? AND target_profile_id=? AND operation_key=?
            """, (rs, row) -> {
                var receipt = codec.read(rs.getString("payload_json"), AuthorizationReceipt.class);
                var command = receipt.command(); var scope = command == null ? null : command.customerContext();
                if (scope == null || !Set.of(Operation.APPLY, Operation.PROMOTION_APPLY).contains(scope.operation())
                    || !scope.operation().name().equals(rs.getString("operation"))
                    || !binding.versionId().equals(rs.getString("version_id"))
                    || !binding.reservationId().equals(rs.getString("reservation_id"))
                    || !binding.generation().equals(rs.getString("generation"))
                    || !receipt.requestFingerprint().equals(rs.getString("request_fingerprint"))
                    || !receipt.contextFingerprint().equals(rs.getString("context_fingerprint")))
                    throw conflict("PROVIDER_USAGE_RELEASE_AUTHORIZATION_CHANGED");
                return receipt;
            }, binding.customerId(), binding.deploymentId(), binding.targetProfileId(), binding.authorizationKey());
        if (receipts.size() != 1) throw conflict("PROVIDER_USAGE_RELEASE_AUTHORIZATION_REQUIRED");
        var receipt = receipts.getFirst(); var command = receipt.command(); var scope = command.customerContext();
        var request = new TreeMap<String, Object>(Map.of("customer", binding.customerId(), "tenant", binding.tenantId(),
            "deployment", binding.deploymentId(), "manifestId", manifest.id(), "manifestFingerprint", codec.fingerprint(manifest),
            "target", binding.targetProfileId(), "customerContext", scope));
        if (command.allocationId() != null) request.put("allocationId", command.allocationId());
        if (!binding.reservationId().equals(receipt.reservationId()) || !binding.generation().equals(receipt.generation())
            || !reservation.manifestFingerprint().equals(receipt.manifestFingerprint())
            || !manifest.id().equals(command.manifestId()) || !binding.authorizationKey().equals(command.operationKey())
            || !binding.authorizationKey().equals(scope.operationKey()) || !binding.customerId().equals(scope.customerId())
            || !binding.tenantId().equals(scope.tenantId()) || !binding.deploymentId().equals(scope.deploymentId())
            || !binding.targetProfileId().equals(scope.targetProfileId()) || !codec.fingerprint(scope).equals(receipt.contextFingerprint())
            || !codec.fingerprint(request).equals(receipt.requestFingerprint()) || scope.intent() == null
            || !scope.intent().name().equals(reservation.intent().name()))
            throw conflict("PROVIDER_USAGE_RELEASE_AUTHORIZATION_CHANGED");
    }
}
