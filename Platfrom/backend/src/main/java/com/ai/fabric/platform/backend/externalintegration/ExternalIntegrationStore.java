package com.ai.fabric.platform.backend.externalintegration;

import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferCodec;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.UncategorizedSQLException;

import java.util.List;
import java.util.Optional;

import static com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationContracts.*;

/** Joins the caller's existing JPA/JDBC transaction; every writer first locks the deployment. */
@Repository
public class ExternalIntegrationStore {
    public record Row<T>(T value, String integrity, String status, long revision) { }
    public record SecretEvidence(String deploymentId, String scopeType, String ownerType,
                                 boolean managed, boolean present, String revision) { }
    private final JdbcTemplate jdbc;
    private final CustomerOfferCodec codec;
    public ExternalIntegrationStore(JdbcTemplate jdbc, CustomerOfferCodec codec) { this.jdbc = jdbc; this.codec = codec; }

    public Optional<Row<Approval>> approval(String id) {
        return one(jdbc.query("select evidence_json,integrity_mac from deployment_external_integration_approvals where id=?",
            (rs, n) -> new Row<>(codec.read(rs.getString(1), Approval.class), rs.getString(2), "APPROVED", 0), id));
    }
    public Optional<Row<Approval>> approvalReplay(String deploymentId, String key) {
        return one(jdbc.query("select evidence_json,integrity_mac from deployment_external_integration_approvals where deployment_id=? and operation_key=?",
            (rs, n) -> new Row<>(codec.read(rs.getString(1), Approval.class), rs.getString(2), "APPROVED", 0), deploymentId, key));
    }
    public void insertApproval(Approval approval, String mac) {
        jdbc.update("insert into deployment_external_integration_approvals(id,deployment_id,version_id,target_profile_id,operation_key,evidence_json,integrity_mac) values (?,?,?,?,?,?,?)",
            approval.id(), approval.scope().deploymentId(), approval.scope().versionId(), approval.scope().targetProfileId(),
            approval.operationKey(), codec.write(approval), mac);
    }
    public Optional<Row<Binding>> binding(String deploymentId, String versionId, String targetId, String slotId) {
        return one(jdbc.query("select evidence_json,integrity_mac,status,revision from deployment_external_integration_bindings where deployment_id=? and version_id=? and target_profile_id=? and slot_id=?",
            (rs, n) -> new Row<>(codec.read(rs.getString(1), Binding.class), rs.getString(2), rs.getString(3), rs.getLong(4)),
            deploymentId, versionId, targetId, slotId));
    }
    public void insertBinding(Binding binding, String mac) {
        jdbc.update("insert into deployment_external_integration_bindings(id,approval_id,deployment_id,version_id,target_profile_id,slot_id,evidence_json,integrity_mac,status,revision) values (?,?,?,?,?,?,?,?,'CONFIGURED',0)",
            binding.id(), binding.approvalId(), binding.scope().deploymentId(), binding.scope().versionId(),
            binding.scope().targetProfileId(), binding.scope().bindingSlotId(), codec.write(binding), mac);
    }
    public void revokeBinding(String id, long expectedRevision, String mac) {
        if (jdbc.update("update deployment_external_integration_bindings set status='REVOKED',revision=revision+1,integrity_mac=? where id=? and status='CONFIGURED' and revision=?",
            mac, id, expectedRevision) != 1) throw ExternalIntegrationPolicy.conflict("BINDING_REVISION_CONFLICT");
    }
    public void requireNoInProgressRelease(Scope scope) {
        // Apply and revoke serialize on the deployment. The persisted nonterminal
        // release is the effect lease; do not claim a completed revocation mid-apply.
        Long count = jdbc.queryForObject("""
            select count(*) from platform_deployment_releases
             where deployment_id=? and deployment_version_id=?
               and (target_profile_id=? or target_profile_id is null)
               and (status is null or status not in
                    ('APPLIED_VERIFIED','APPLIED_VERIFICATION_FAILED','PRE_APPLY_BLOCKED','FAILED'))
            """, Long.class, scope.deploymentId(), scope.versionId(), scope.targetProfileId());
        if (count == null || count != 0) throw ExternalIntegrationPolicy.conflict("RELEASE_IN_PROGRESS");
    }
    public Optional<Row<ReleaseSnapshot>> snapshot(String releaseId) {
        return one(jdbc.query("select evidence_json,integrity_mac from deployment_external_integration_snapshots where release_id=?",
            (rs, n) -> new Row<>(codec.read(rs.getString(1), ReleaseSnapshot.class), rs.getString(2), "FROZEN", 0), releaseId));
    }
    public void insertSnapshot(ReleaseSnapshot snapshot, Scope scope, String mac) {
        jdbc.update("insert into deployment_external_integration_snapshots(release_id,deployment_id,version_id,target_profile_id,evidence_json,integrity_mac) values (?,?,?,?,?,?)",
            snapshot.releaseId(), scope.deploymentId(), scope.versionId(), scope.targetProfileId(), codec.write(snapshot), mac);
    }
    public Optional<SecretEvidence> secretEvidence(String name) {
        return one(jdbc.query("select deployment_id,scope_type,owner_type,managed_by_platform,(length(trim(secret_value))>0),updated_at from platform_secrets where name=?",
            (rs, n) -> new SecretEvidence(rs.getString(1), rs.getString(2), rs.getString(3), rs.getBoolean(4),
                rs.getBoolean(5), rs.getTimestamp(6).toInstant().toString()), name));
    }
    public void fenceDeployment(String deploymentId) {
        try {
            var rows = jdbc.queryForList("select id from platform_deployments where id=? for update nowait", String.class, deploymentId);
            if (rows.size() != 1) throw ExternalIntegrationPolicy.conflict("DEPLOYMENT_REQUIRED");
        } catch (PessimisticLockingFailureException busy) { throw ExternalIntegrationPolicy.conflict("DEPLOYMENT_BUSY"); }
        catch (UncategorizedSQLException failure) {
            if ("55P03".equals(failure.getSQLException().getSQLState())) throw ExternalIntegrationPolicy.conflict("DEPLOYMENT_BUSY");
            throw failure;
        }
    }
    private <T> Optional<T> one(List<T> values) {
        if (values.size() > 1) throw new IllegalStateException("External integration scope is not unique");
        return values.stream().findFirst();
    }
}
