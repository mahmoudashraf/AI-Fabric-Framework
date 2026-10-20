package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationIntegrity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static com.ai.fabric.platform.backend.deployment.admission.KnownNoEffectSettlementContracts.*;

/** Synthetic trusted admission inputs; only the real binding/settlement writers create the terminal proof. */
public final class KnownNoEffectHandoffTestFixture {
    public record SettledSource(String receiptId, String reservationId, String releaseId) { }

    public static SettledSource settle(JdbcTemplate jdbc, AdmissionJson json, PlatformTransactionManager transactions,
            ExternalIntegrationIntegrity integrity, DeploymentEntity deployment, DeploymentVersionEntity version,
            String target, String owner, String offer, Instant now) {
        var store = new AdmissionStore(jdbc, json, transactions, Clock.fixed(now, ZoneOffset.UTC));
        var defaults = new AdmissionFixture() { };
        var template = defaults.policy(1000);
        var budget = new Budget(AdmissionFixture.vector(1000), AdmissionFixture.vector(0), AdmissionFixture.vector(0),
            "synthetic-known-measurement", now.minusSeconds(1), now.plusSeconds(10000));
        var policy = new FleetPolicy("known-policy-" + deployment.getId(), budget,
            Map.of(target, new TargetBudget(target, Intent.CUSTOMER_STAGING, AdmissionFixture.STAGING, "synthetic-profile", budget)),
            template.lifecycle(), "restore-proof", "privacy-proof", "support-owner");
        // This test database also holds independent malformed historical fixtures. Seed this fixture's measured input,
        // rather than publishing continuity for those unrelated reservations as part of a handoff test.
        jdbc.update("INSERT INTO deployment_admission_reviews(kind,record_key,fingerprint,payload_json,reviewed_by,reviewed_at) VALUES ('POLICY',?,?,?,'fixture',?)",
            policy.id(), json.fingerprint(policy), json.write(policy), Timestamp.from(now));
        jdbc.update("UPDATE deployment_admission_mutex SET active_policy_ref=? WHERE id=1", policy.id());
        store.approveGrant(new CustomerGrant(deployment.getCustomerId(), AdmissionFixture.vector(1000),
            Map.of(target, AdmissionFixture.vector(1000)), now.plusSeconds(5000), "owner", "payer", "keep"), "fixture", now);
        var manifest = new Manifest("known-manifest-" + deployment.getId(), deployment.getId(), version.getId(), version.getConfigHash(),
            target, "synthetic-binding", "synthetic-topology", AdmissionFixture.vector(1), Set.of("policy:" + policy.id()));
        store.approveManifest(manifest, "fixture", now);
        var current = store.context(deployment.getCustomerId(), deployment.getTenantId(), deployment.getId(), manifest.id());
        var scope = new CustomerOfferContracts.AdmissionContext(deployment.getCustomerId(), deployment.getTenantId(), deployment.getId(),
            null, target, CustomerOfferContracts.TargetIntent.CUSTOMER_STAGING, offer, "synthetic-offer-fingerprint", 1,
            "synthetic-placement", CustomerOfferContracts.Operation.APPLY, "known-apply",
            new CustomerOfferContracts.ReviewedLimits("measurement-proof", 1, 2, 2, 2, 2, 2, 2, 2, 2, 1, 1, 1),
            new CustomerOfferContracts.ReviewedTopology(1, 1, 1, 1, 1, 1, 1, 1));
        var evidence = new PrerequisiteReadback(now, now.plusSeconds(60), "synthetic-readback", AdmissionFixture.STAGING,
            "synthetic-profile", current.manifestFingerprint(), manifest.bindingFingerprint(), manifest.prerequisiteReferences(),
            policy.restoreEvidenceReference(), policy.privacyEvidenceReference(), current.grant().ownerContactEvidenceReference(),
            policy.lifecycle().noticeTransportReference());
        var reserve = new Reserve(scope.operationKey(), manifest.id(), scope);
        String requestHash = store.commandFingerprint(deployment.getCustomerId(), deployment.getTenantId(), deployment.getId(), manifest, reserve);
        var r = new Reservation("known-reservation-" + deployment.getId(), deployment.getCustomerId(), deployment.getTenantId(), deployment.getId(),
            target, Intent.CUSTOMER_STAGING, scope.operationKey(), requestHash, "known-generation", 0, State.RESERVED, manifest.demand(), policy.id(),
            manifest.id(), current.manifestFingerprint(), manifest.bindingFingerprint(), now, now, now, now.plusSeconds(3600), 0, null, null,
            ExpiryState.CURRENT, "synthetic-cycle", null, 0, null, null, null, null, "synthetic-readback", null,
            json.fingerprint(scope), scope.operation(), evidence);
        var auth = new AuthorizationReceipt(r.id(), r.generation(), r.manifestFingerprint(), requestHash, json.fingerprint(scope), reserve);
        jdbc.update("""
            INSERT INTO deployment_capacity_reservations(id,customer_id,deployment_id,target_profile_id,operation_key,request_fingerprint,
                generation,revision,state,current_slot,expires_at,expiry_state,payload_json,updated_at)
            VALUES (?,?,?,?,?,?,?,0,'RESERVED',?,?,'CURRENT',?,?)
            """, r.id(), r.customerId(), r.deploymentId(), target, r.operationKey(), requestHash, r.generation(),
            r.deploymentId() + ":" + target, Timestamp.from(r.expiresAt()), json.write(r), Timestamp.from(now));
        jdbc.update("""
            INSERT INTO deployment_admission_authorizations(customer_id,deployment_id,target_profile_id,operation_key,reservation_id,generation,
                operation,version_id,request_fingerprint,context_fingerprint,payload_json,created_at)
            VALUES (?,?,?,?,?,?,'APPLY',?,?,?,?,?)
            """, r.customerId(), r.deploymentId(), target, r.operationKey(), r.id(), r.generation(), version.getId(), requestHash,
            auth.contextFingerprint(), json.write(auth), Timestamp.from(now));
        jdbc.update("INSERT INTO deployment_admission_events(id,reservation_id,revision,event_type,actor_id,evidence_reference,created_at) VALUES (?,?,0,'RESERVED',?,'synthetic-readback',?)",
            "known-event-" + deployment.getId(), r.id(), owner, Timestamp.from(now));
        String releaseId = "known-release-" + deployment.getId();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            jdbc.queryForObject("SELECT id FROM platform_deployments WHERE id=? FOR UPDATE", String.class, deployment.getId());
            jdbc.update("""
                INSERT INTO platform_deployment_releases(id,deployment_id,deployment_version_id,target_profile_id,provider_type,status,
                    verification_status,provisioning_status,provisioning_target,provisioning_details_json,created_at,applied_at,updated_at)
                VALUES (?,?,?,?,'COOLIFY','APPLY_REQUESTED','PENDING','QUEUED','COOLIFY','{}',?,?,?)
                """, releaseId, deployment.getId(), version.getId(), target, Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
            var release = new DeploymentReleaseEntity(); release.setId(releaseId); release.setDeploymentId(deployment.getId());
            release.setDeploymentVersionId(version.getId()); release.setTargetProfileId(target);
            store.bindRelease(deployment, version, release);
        });
        jdbc.update("UPDATE platform_deployment_releases SET status='FAILED',provisioning_status='FAILED',current_step_key='apply_failed',provisioning_details_json=? WHERE id=?",
            "{\"progress\":{\"currentStepKey\":\"apply_failed\",\"steps\":[{\"key\":\"queue_release\",\"status\":\"COMPLETED\"},{\"key\":\"preflight_verification\",\"status\":\"COMPLETED\"},{\"key\":\"apply_failed\",\"status\":\"FAILED\"}]}}", releaseId);
        store.releaseBindingState(releaseId, "NO_EFFECT");
        String authorization = jdbc.queryForObject("SELECT request_fingerprint FROM deployment_admission_authorizations WHERE reservation_id=?", String.class, r.id());
        var command = new Command(r.fence(), "known-settlement", r.manifestFingerprint(), version.getId(), version.getConfigHash(),
            r.bindingFingerprint(), java.util.List.of(new FailedRelease(releaseId, scope.operationKey(), authorization)),
            "Synthetic pre-provider failure", java.util.List.of());
        var receipt = store.settleKnownNoEffect(deployment.getCustomerId(), deployment.getId(), r.id(), command, owner, ignored -> { }, null, integrity);
        return new SettledSource(receipt.id(), r.id(), releaseId);
    }
}
