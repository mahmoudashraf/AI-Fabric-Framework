package com.ai.fabric.platform.backend.deployment.authority;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.security.RuntimePrivateAssertionSigningService;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static com.ai.fabric.platform.backend.deployment.authority.RuntimeAuthorityPurpose.PRIVATE_ASSERTION_SIGNING_KEY;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers
@TestPropertySource(properties = "AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY=test-only-global-key-not-a-fallback")
class RuntimeAuthorityReadOnlyPostgresTest extends RuntimeAuthorityPersistenceIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @Autowired PlatformTransactionManager transactions;
    @Autowired PlatformSecretService secretService;

    @DynamicPropertySource static void postgres(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
        properties.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        properties.add("spring.flyway.locations", () -> "classpath:db/migration");
        properties.add("spring.flyway.target", () -> "latest");
    }

    @Test void readOnlyValidationUsesRegularReadsAndLeavesFrozenStateUnchanged() {
        var release = frozenRelease();
        var deployment = deployments.findById(deploymentId).orElseThrow();
        var version = versions.findById(versionId).orElseThrow();
        Long secretCount = jdbc.queryForObject("select count(*) from platform_secrets where deployment_id=?", Long.class, deploymentId);
        Long snapshotCount = jdbc.queryForObject("select count(*) from platform_runtime_authority_snapshots where deployment_id=?", Long.class, deploymentId);
        String binding = snapshots.findById(release.getRuntimeAuthoritySnapshotId()).orElseThrow().getBindingJson();
        clearInvocations(audit);

        var result = readOnlyTransaction().execute(status -> {
            assertReadOnly();
            var checked = service.requireReleaseUnchangedForRead(deployment, version, release);
            assertThat(status.isRollbackOnly()).isFalse();
            return checked;
        });

        assertThat(result).isNotNull();
        assertThat(result.snapshotId()).isEqualTo(release.getRuntimeAuthoritySnapshotId());
        assertThat(result.fingerprint()).isEqualTo(release.getRuntimeAuthorityFingerprint());
        assertThat(result.binding().targetProfileId()).isEqualTo(targetId);
        assertThat(result.binding().privateIssuer()).startsWith("platform-target:");
        assertThat(result.binding().privateAudience()).startsWith("runtime-private:");
        assertThat(snapshots.findById(result.snapshotId()).orElseThrow().getBindingJson()).isEqualTo(binding);
        assertThat(jdbc.queryForObject("select count(*) from platform_secrets where deployment_id=?", Long.class, deploymentId)).isEqualTo(secretCount);
        assertThat(jdbc.queryForObject("select count(*) from platform_runtime_authority_snapshots where deployment_id=?", Long.class, deploymentId)).isEqualTo(snapshotCount);
        verifyNoInteractions(audit);
    }

    @ParameterizedTest @ValueSource(strings = {"target", "version", "integrity", "missing-key", "stale"})
    void readOnlyValidationRejectsInvalidEvidenceWithoutGlobalFallbackOrRollbackPoisoning(String fault) {
        var release = frozenRelease();
        var deployment = deployments.findById(deploymentId).orElseThrow();
        var requested = new DeploymentReleaseEntity(); BeanUtils.copyProperties(release, requested);
        var version = versions.findById(versionId).orElseThrow();
        var candidate = new DeploymentVersionEntity(); BeanUtils.copyProperties(version, candidate);
        var snapshot = snapshots.findById(release.getRuntimeAuthoritySnapshotId()).orElseThrow();
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String reference;
        try {
            reference = mapper.readValue(snapshot.getBindingJson(), RuntimeAuthorityBinding.class)
                .secrets().get(PRIVATE_ASSERTION_SIGNING_KEY).reference();
        } catch (java.io.IOException invalid) { throw new AssertionError(invalid); }
        switch (fault) {
            case "target" -> {
                var other = new DeploymentTargetProfileEntity();
                BeanUtils.copyProperties(targets.findById(targetId).orElseThrow(), other);
                other.setId("other-" + UUID.randomUUID().toString().substring(0, 8));
                targets.saveAndFlush(other);
                requested.setTargetProfileId(other.getId());
            }
            case "version" -> candidate.setId("foreign-version");
            case "integrity" -> jdbc.update("update platform_secrets set secret_value=? where name=?", "tampered-with-same-revision", reference);
            case "missing-key" -> jdbc.update("delete from platform_secrets where name=?", reference);
            case "stale" -> jdbc.update("update platform_deployment_versions set config_hash=? where id=?", "changed-config", versionId);
            default -> throw new AssertionError(fault);
        }
        assertThat(secretService.resolveSecret(RuntimePrivateAssertionSigningService.SECRET_NAME)).isEqualTo("test-only-global-key-not-a-fallback");
        clearInvocations(audit);

        readOnlyTransaction().executeWithoutResult(status -> {
            assertReadOnly();
            // Readiness catches this domain denial and must still return a normal BLOCKED summary.
            assertThatThrownBy(() -> service.requireReleaseUnchangedForRead(deployment, candidate, requested))
                .isInstanceOf(ResponseStatusException.class);
            assertThat(status.isRollbackOnly()).isFalse();
            assertThat(jdbc.queryForObject("select 1", Integer.class)).isEqualTo(1);
        });

        verifyNoInteractions(audit);
    }

    @Test void provisioningFenceStillRejectsAnActualPostgresReadOnlyTransaction() {
        var release = frozenRelease();
        var deployment = deployments.findById(deploymentId).orElseThrow();
        var version = versions.findById(versionId).orElseThrow();

        Throwable failure = catchThrowable(() -> readOnlyTransaction().execute(status -> {
            assertReadOnly();
            return service.requireReleaseUnchangedForProvisioning(deployment, version, release);
        }));

        assertThat(failure).isNotNull();
        Throwable root = failure;
        while (root.getCause() != null) root = root.getCause();
        assertThat(root).isInstanceOfSatisfying(SQLException.class,
            exception -> assertThat(exception.getSQLState()).isEqualTo("25006"));
    }

    private TransactionTemplate readOnlyTransaction() {
        var template = new TransactionTemplate(transactions);
        template.setReadOnly(true);
        return template;
    }

    private void assertReadOnly() {
        assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
        assertThat(jdbc.queryForObject("show transaction_read_only", String.class)).isEqualTo("on");
    }

    private DeploymentReleaseEntity frozenRelease() {
        var candidate = service.freezeCandidate(deploymentId, targetId, versionId, List.of());
        var release = new DeploymentReleaseEntity();
        release.setId("release-read-" + UUID.randomUUID().toString().substring(0, 8));
        release.setDeploymentId(deploymentId); release.setDeploymentVersionId(versionId); release.setTargetProfileId(targetId);
        release.setStatus("APPLIED_VERIFIED"); release.setVerificationStatus("PASSED"); release.setProvisioningStatus("ACTIVE");
        release.setProvisioningTarget("COOLIFY"); release.setProviderType(DeploymentProviderType.COOLIFY);
        release.setProvisioningDetailsJson("{}"); release.setCreatedAt(Instant.now()); release.setAppliedAt(Instant.now()); release.setUpdatedAt(Instant.now());
        releases.saveAndFlush(release);
        var frozen = service.freezeRelease(deploymentId, targetId, release.getId(), candidate.snapshotId(), candidate.fingerprint());
        release.setRuntimeAuthoritySnapshotId(frozen.snapshotId()); release.setRuntimeAuthorityFingerprint(frozen.fingerprint());
        return releases.saveAndFlush(release);
    }
}
