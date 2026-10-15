package com.ai.fabric.platform.backend.deployment.admission;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.Optional;

import static org.springframework.http.HttpStatus.PRECONDITION_FAILED;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
class CustomerSourcePrerequisiteEvidenceStore {
    private final JdbcTemplate jdbc;
    private final AdmissionJson json;

    CustomerSourcePrerequisiteEvidenceStore(JdbcTemplate jdbc, AdmissionJson json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    void save(CustomerSourcePrerequisiteEvidence evidence) {
        jdbc.update("""
            INSERT INTO deployment_source_prerequisite_evidence
              (id,customer_id,tenant_id,deployment_id,target_profile_id,release_id,reservation_id,
               allocation_generation,scope_fingerprint,payload_fingerprint,payload_json,checked_at,expires_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
            """, evidence.id(), evidence.scope().customerId(), evidence.scope().tenantId(), evidence.scope().deploymentId(),
            evidence.scope().targetProfileId(), evidence.scope().releaseId(), evidence.scope().reservationId(),
            evidence.scope().allocationGeneration(), json.fingerprint(evidence.scope()), json.fingerprint(evidence),
            json.write(evidence), Timestamp.from(evidence.checkedAt()), Timestamp.from(evidence.expiresAt()));
    }

    Optional<CustomerSourcePrerequisiteEvidence> latest(CustomerSourcePrerequisiteEvidence.Scope scope) {
        var rows = jdbc.query("""
            SELECT * FROM deployment_source_prerequisite_evidence
             WHERE customer_id=? AND tenant_id=? AND deployment_id=? AND target_profile_id=? AND release_id=?
               AND reservation_id=? AND allocation_generation=? AND scope_fingerprint=?
             ORDER BY checked_at DESC,id DESC LIMIT 1
            """, (rs, row) -> {
                var evidence = json.read(rs.getString("payload_json"), CustomerSourcePrerequisiteEvidence.class);
                if (!scope.equals(evidence.scope()) || !evidence.id().equals(rs.getString("id"))
                    || !json.fingerprint(evidence).equals(rs.getString("payload_fingerprint"))
                    || !evidence.checkedAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS)
                        .equals(rs.getTimestamp("checked_at").toInstant())
                    || !evidence.expiresAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS)
                        .equals(rs.getTimestamp("expires_at").toInstant())) {
                    throw new AdmissionFailure(PRECONDITION_FAILED, "SOURCE_PREREQUISITE_RECORD_MISMATCH");
                }
                return evidence;
            }, scope.customerId(), scope.tenantId(), scope.deploymentId(), scope.targetProfileId(), scope.releaseId(),
            scope.reservationId(), scope.allocationGeneration(), json.fingerprint(scope));
        return rows.stream().findFirst();
    }
}
