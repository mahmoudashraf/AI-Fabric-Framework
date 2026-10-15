package com.ai.fabric.platform.backend.deployment.authority;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.*;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.repository.*;
import com.ai.fabric.platform.backend.deployment.service.DeploymentAccessService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentExecutionSecretService;
import com.ai.fabric.platform.backend.secret.repository.PlatformSecretRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.security.RuntimePrivateAssertionSigningService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DataJpaTest(showSql = false, properties = {
    "spring.flyway.target=120.21",
    "spring.datasource.url=jdbc:h2:mem:r1-authority;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;LOCK_TIMEOUT=10000",
    "spring.jpa.show-sql=false"
})
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({DeploymentRuntimeAuthorityService.class, DeploymentExecutionSecretService.class,
    PlatformSecretService.class, RuntimePrivateAssertionSigningService.class, ObjectMapper.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RuntimeAuthorityPersistenceIntegrationTest {
    @Autowired DeploymentRuntimeAuthorityService service;
    @Autowired DeploymentRepository deployments;
    @Autowired DeploymentDraftRepository drafts;
    @Autowired DeploymentVersionRepository versions;
    @Autowired DeploymentReleaseRepository releases;
    @Autowired DeploymentTargetProfileRepository targets;
    @Autowired RuntimeAuthorityRepository authorities;
    @Autowired RuntimeAuthoritySnapshotRepository snapshots;
    @Autowired PlatformSecretRepository secrets;
    @Autowired JdbcTemplate jdbc;
    @MockBean PlatformAuditService audit;
    @MockBean DeploymentAccessService access;
    private String deploymentId;
    private String versionId;
    private String targetId;

    @BeforeEach void fixture() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        deploymentId = "dep-authority-" + suffix;
        versionId = "ver-authority-" + suffix;
        targetId = "target-authority-" + suffix;
        String customer = "customer-" + suffix;
        String tenant = "tenant-" + suffix;
        jdbc.update("INSERT INTO platform_customers (id,name,slug,status,platform_managed,created_at,updated_at) VALUES (?,?,?,'ACTIVE',false,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", customer, customer, customer);
        jdbc.update("INSERT INTO platform_customer_tenants (id,customer_id,name,slug,status,platform_managed,created_at,updated_at) VALUES (?,?,?,?,'ACTIVE',false,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", tenant, customer, tenant, tenant);
        var deployment = new DeploymentEntity();
        deployment.setId(deploymentId); deployment.setName(deploymentId); deployment.setCustomerId(customer); deployment.setTenantId(tenant);
        deployment.setEnvironmentName("staging"); deployment.setTemplateId("fixture"); deployment.setBehaviorType("CONVERSATIONAL"); deployment.setStatus("ACTIVE");
        deployment.setCreatedAt(Instant.now()); deployment.setUpdatedAt(Instant.now());
        deployments.saveAndFlush(deployment);
        var draft = new DeploymentDraftEntity();
        draft.setId("draft-" + suffix); draft.setDeploymentId(deploymentId); draft.setRevisionNumber(1); draft.setStatus("PUBLISHED");
        draft.setActionsConfigJson("{}"); draft.setEntityConfigJson("{}"); draft.setRoutingConfigJson("{}"); draft.setProviderConfigJson("{}");
        draft.setSecurityConfigJson("{}"); draft.setPromptConfigJson("{}"); draft.setCreatedAt(Instant.now()); draft.setUpdatedAt(Instant.now());
        drafts.saveAndFlush(draft);
        var version = new DeploymentVersionEntity();
        version.setId(versionId); version.setDeploymentId(deploymentId); version.setSourceDraftId(draft.getId()); version.setVersionLabel("1");
        version.setStatus("PUBLISHED"); version.setConfigHash("immutable-fixture-config"); version.setPublishedAt(Instant.now());
        version.setActionsConfigJson("{}"); version.setEntityConfigJson("{}"); version.setRoutingConfigJson("{}"); version.setProviderConfigJson("{}");
        version.setSecurityConfigJson("{}"); version.setPromptConfigJson("{}"); version.setActionsArtifactYaml("{}");
        version.setEntityArtifactYaml("{}"); version.setRoutingArtifactYaml("{}"); version.setManifestJson("{}");
        version.setBehaviorConfigJson("{\"type\":\"CONVERSATIONAL\",\"executionExtensions\":[]}");
        versions.saveAndFlush(version);
        var target = new DeploymentTargetProfileEntity();
        target.setId(targetId); target.setName(targetId); target.setProviderType(DeploymentProviderType.COOLIFY); target.setEnvironmentName("staging");
        target.setActive(true); target.setSourceStrategy("IMAGE_SOURCE"); target.setProviderConfigJson("{}"); target.setNetworkPolicyJson("{}");
        target.setResourceDefaultsJson("{}"); target.setCreatedAt(Instant.now()); target.setUpdatedAt(Instant.now());
        targets.saveAndFlush(target);
    }

    @Test void concurrentGenerationUsesDatabaseLockAndReusesOneCommittedGeneration() throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var results = List.of(1, 2).stream().map(i -> executor.submit(() -> {
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out.");
                return freeze();
            })).toList();
            start.countDown();
            var first = results.get(0).get(20, TimeUnit.SECONDS);
            var second = results.get(1).get(20, TimeUnit.SECONDS);
            assertThat(second).isEqualTo(first);
            assertThat(freeze()).isEqualTo(first);
            assertThat(service.requireCandidateUnchanged(deploymentId, targetId, versionId, first.snapshotId(), first.fingerprint())).isEqualTo(first);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform_runtime_target_authorities WHERE deployment_id=? AND target_profile_id=?", Integer.class, deploymentId, targetId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform_secrets WHERE deployment_id=?", Integer.class, deploymentId)).isEqualTo(first.binding().secrets().size());
        }
    }

    @Test void failedFreezeRollsBackSecretsRegistryAndSnapshotBeforeRetry() {
        doThrow(new IllegalStateException("Interrupted before authority commit")).when(audit)
            .record(eq("RUNTIME_AUTHORITY_CANDIDATE_FROZEN"), anyString(), anyString(), anyMap());
        assertThatThrownBy(this::freeze).hasMessageContaining("Interrupted");
        assertThat(authorities.findByDeploymentIdAndTargetProfileId(deploymentId, targetId)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform_secrets WHERE deployment_id=?", Integer.class, deploymentId)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform_runtime_authority_snapshots WHERE deployment_id=?", Integer.class, deploymentId)).isZero();
        doNothing().when(audit).record(eq("RUNTIME_AUTHORITY_CANDIDATE_FROZEN"), anyString(), anyString(), anyMap());
        assertThat(freeze().binding().generation()).isEqualTo(1);
    }

    @Test void databaseRoundTripsPreserveRevisionsAndRotationInvalidatesFrozenEvidence() {
        var first = freeze();
        assertThat(service.candidateEnvironmentPlan(deploymentId, targetId, versionId, first.snapshotId(), first.fingerprint()).runtime()).isNotEmpty();
        assertThat(service.rotate(deploymentId, targetId, 1)).isEqualTo(2);
        assertThatThrownBy(() -> service.requireCandidateUnchanged(deploymentId, targetId, versionId, first.snapshotId(), first.fingerprint()))
            .hasMessageContaining("new apply and verification");
        var second = freeze();
        assertThat(second.fingerprint()).isNotEqualTo(first.fingerprint());
        service.revoke(deploymentId, targetId, 2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform_secrets WHERE deployment_id=?", Integer.class, deploymentId)).isZero();
        assertThat(snapshots.findById(first.snapshotId())).isPresent();
        assertThat(authorities.findByDeploymentIdAndTargetProfileId(deploymentId, targetId).orElseThrow().getState()).isEqualTo("REVOKED");
    }

    @Test void releaseSnapshotReferenceAndFingerprintRoundTripWithForeignKeyProtection() {
        var candidate = service.freezeCandidate(deploymentId, targetId, versionId, List.of("https://workspace.example.com"));
        var release = new DeploymentReleaseEntity();
        release.setId("release-" + UUID.randomUUID().toString().substring(0, 8));
        release.setDeploymentId(deploymentId);
        release.setDeploymentVersionId(versionId);
        release.setStatus("APPLY_REQUESTED");
        release.setVerificationStatus("PENDING");
        release.setProvisioningStatus("QUEUED");
        release.setProvisioningTarget("COOLIFY");
        release.setTargetProfileId(targetId);
        release.setProviderType(DeploymentProviderType.COOLIFY);
        release.setProvisioningDetailsJson("{}");
        release.setCreatedAt(Instant.now());
        release.setAppliedAt(Instant.now());
        release.setUpdatedAt(Instant.now());
        releases.saveAndFlush(release);

        var frozen = service.freezeRelease(
            deploymentId, targetId, release.getId(), candidate.snapshotId(), candidate.fingerprint()
        );
        release.setRuntimeAuthoritySnapshotId(frozen.snapshotId());
        release.setRuntimeAuthorityFingerprint(frozen.fingerprint());
        releases.saveAndFlush(release);

        var stored = releases.findById(release.getId()).orElseThrow();
        assertThat(stored.getRuntimeAuthoritySnapshotId()).isEqualTo(frozen.snapshotId());
        assertThat(stored.getRuntimeAuthorityFingerprint()).isEqualTo(frozen.fingerprint());
        assertThat(service.freezeRelease(
            deploymentId, targetId, release.getId(), candidate.snapshotId(), candidate.fingerprint()
        )).isEqualTo(frozen);
    }

    private RuntimeAuthorityBinding.Snapshot freeze() {
        return service.freezeCandidate(deploymentId, targetId, versionId, List.of());
    }
}
