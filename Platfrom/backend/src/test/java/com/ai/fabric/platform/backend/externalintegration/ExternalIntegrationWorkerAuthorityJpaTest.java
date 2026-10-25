package com.ai.fabric.platform.backend.externalintegration;

import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferDeploymentEntity;
import com.ai.fabric.platform.backend.deployment.authority.DeploymentRuntimeAuthorityService;
import com.ai.fabric.platform.backend.deployment.authority.RuntimeAuthorityBinding;
import com.ai.fabric.platform.backend.deployment.authority.RuntimeAuthoritySnapshotRepository;
import com.ai.fabric.platform.backend.deployment.authority.RuntimeAuthorityEntity;
import com.ai.fabric.platform.backend.deployment.authority.RuntimeAuthoritySnapshotEntity;
import com.ai.fabric.platform.backend.secret.entity.PlatformSecretEntity;
import jakarta.persistence.EntityManagerFactory;
import com.ai.fabric.platform.backend.deployment.entity.*;
import com.ai.fabric.platform.backend.deployment.repository.*;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual application boot, real JPA/frozen authority/access service, and an uncredentialed worker thread.
 * Offer policy/binding fixtures and DNS are synthetic collaborators; no provider or hosted connection. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:worker-frozen-authority;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ExternalIntegrationWorkerAuthorityJpaTest {
    @Autowired DeploymentRuntimeAuthorityService authority;
    @Autowired DeploymentRepository deployments;
    @Autowired DeploymentVersionRepository versions;
    @Autowired DeploymentDraftRepository drafts;
    @Autowired DeploymentReleaseRepository releases;
    @Autowired DeploymentTargetProfileRepository targets;
    @Autowired RuntimeAuthoritySnapshotRepository snapshots;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManagerFactory entityManagerFactory;

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void principalNullWorkerResolvesFrozenBindingsWhileTheOperatorApiStillDenies() throws Exception {
        var fixture = fixture();
        assertThat(AopUtils.isAopProxy(authority)).isTrue();
        String original = snapshots.findById(fixture.frozen().snapshotId()).orElseThrow().getBindingJson();
        fixture.f().customer();
        var caller = PlatformSecurityContext.currentPrincipal();
        inWorker(() -> {
            assertThat(PlatformSecurityContext.currentPrincipal()).isNull();
            assertThatThrownBy(() -> authority.requireReleaseUnchanged(fixture.f().deployment.getId(), fixture.f().target.getId(),
                fixture.release().getId(), fixture.frozen().fingerprint()))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
            var environment = transaction(() -> fixture.resolver().releaseEnvironmentForProvisioning(
                fixture.f().deployment, fixture.f().version, fixture.release()));
            assertThat(environment.secretEnvironment()).hasSize(1);
            assertThat(fixture.f().codec.write(environment)).doesNotContain("synthetic-customer-key");
            // Redispatch/recovery revalidates the same persisted authority, without a browser session.
            transaction(() -> { fixture.resolver().requireForRecovery(fixture.f().deployment, fixture.release()); return null; });
            assertThat(PlatformSecurityContext.currentPrincipal()).isNull();
            return null;
        });
        assertThat(PlatformSecurityContext.currentPrincipal()).isEqualTo(caller);
        assertThat(snapshots.findById(fixture.frozen().snapshotId()).orElseThrow().getBindingJson()).isEqualTo(original);
        assertThat(releases.findById(fixture.release().getId()).orElseThrow().getRuntimeAuthorityFingerprint())
            .isEqualTo(fixture.frozen().fingerprint());
    }

    @ParameterizedTest
    @ValueSource(strings = {"customer", "tenant", "deployment", "version", "release", "target", "config", "snapshot",
        "fingerprint", "generation", "snapshot-row", "revoked", "unpublished", "inactive-target", "archived"})
    void persistedScopeOrFrozenAuthorityDriftFailsBeforeWorkerDnsOrAnyEnvironmentIsReturned(String fault) throws Exception {
        var fixture = fixture();
        var f = fixture.f(); var release = fixture.release();
        switch (fault) {
            case "customer" -> jdbc.update("update platform_deployments set customer_id=? where id=?", "foreign-customer", f.deployment.getId());
            case "tenant" -> jdbc.update("update platform_deployments set tenant_id=? where id=?", f.deployment.getTenantId() + "-other", f.deployment.getId());
            case "deployment" -> f.deployment.setId("foreign-deployment");
            case "version" -> f.version.setId("foreign-version");
            case "release" -> release.setId("foreign-release");
            case "target" -> release.setTargetProfileId("foreign-target");
            case "config" -> jdbc.update("update platform_deployment_versions set config_hash=? where id=?", "changed", f.version.getId());
            case "snapshot" -> jdbc.update("update platform_deployment_releases set runtime_authority_snapshot_id=? where id=?", candidateId(fixture), release.getId());
            case "fingerprint" -> jdbc.update("update platform_deployment_releases set runtime_authority_fingerprint=? where id=?", "0".repeat(64), release.getId());
            case "generation" -> jdbc.update("update platform_runtime_target_authorities set generation=generation+1 where deployment_id=?", f.deployment.getId());
            case "snapshot-row" -> jdbc.update("update platform_runtime_authority_snapshots set binding_json=? where id=?", "{}", fixture.frozen().snapshotId());
            case "revoked" -> jdbc.update("update platform_runtime_target_authorities set state='REVOKED' where deployment_id=?", f.deployment.getId());
            case "unpublished" -> jdbc.update("update platform_deployment_versions set status='DRAFT' where id=?", f.version.getId());
            case "inactive-target" -> jdbc.update("update deployment_target_profiles set active=false where id=?", f.target.getId());
            case "archived" -> jdbc.update("update platform_deployments set archived_at=current_timestamp where id=?", f.deployment.getId());
            default -> throw new AssertionError(fault);
        }
        int dnsBefore = f.dnsCalls.get();
        inWorker(() -> {
            assertThat(PlatformSecurityContext.currentPrincipal()).isNull();
            assertThatThrownBy(() -> transaction(() -> fixture.resolver().releaseEnvironmentForProvisioning(f.deployment, f.version, release)))
                .as(fault).isInstanceOf(ResponseStatusException.class);
            assertThat(PlatformSecurityContext.currentPrincipal()).isNull(); return null;
        });
        assertThat(f.dnsCalls.get()).isEqualTo(dnsBefore);
    }

    @ParameterizedTest
    @ValueSource(strings = {"customer", "tenant", "config", "snapshot", "revoked", "generation", "secret-revision"})
    void cachedBeforeFenceRowsCannotHideASeparatelyCommittedAuthorityChange(String fault) throws Exception {
        var fixture = fixture(); int dnsBefore = fixture.f().dnsCalls.get();
        assertThatThrownBy(() -> inWorker(() -> transaction(() -> {
            cache(fixture);
            inWorker(() -> {
                var f = fixture.f();
                switch (fault) {
                    case "customer" -> jdbc.update("update platform_deployments set customer_id='foreign-customer' where id=?", f.deployment.getId());
                    case "tenant" -> jdbc.update("update platform_deployments set tenant_id=? where id=?", f.deployment.getTenantId() + "-other", f.deployment.getId());
                    case "config" -> jdbc.update("update platform_deployment_versions set config_hash='separately-changed' where id=?", f.version.getId());
                    case "snapshot" -> jdbc.update("update platform_deployment_releases set runtime_authority_snapshot_id=? where id=?", candidateId(fixture), fixture.release().getId());
                    case "revoked" -> jdbc.update("update platform_runtime_target_authorities set state='REVOKED' where deployment_id=?", f.deployment.getId());
                    case "generation" -> jdbc.update("update platform_runtime_target_authorities set generation=generation+1 where deployment_id=?", f.deployment.getId());
                    case "secret-revision" -> jdbc.update("update platform_secrets set updated_at=? where name=?", java.sql.Timestamp.from(Instant.now().plusSeconds(60)),
                        fixture.frozen().binding().secrets().values().iterator().next().reference());
                    default -> throw new AssertionError(fault);
                }
                return null;
            });
            assertThat(PlatformSecurityContext.currentPrincipal()).isNull();
            return fixture.resolver().releaseEnvironmentForProvisioning(fixture.f().deployment, fixture.f().version, fixture.release());
        }))).as(fault).hasRootCauseInstanceOf(ResponseStatusException.class);
        assertThat(fixture.f().dnsCalls.get()).isEqualTo(dnsBefore);
    }

    @Test void authoritativeReadbackDoesNotDetachOrDiscardOuterPendingProviderState() throws Exception {
        var fixture = fixture();
        inWorker(() -> transaction(() -> {
            cache(fixture);
            var managed = releases.findById(fixture.release().getId()).orElseThrow();
            managed.setCurrentStepDescription("pending provider state must survive authority readback");
            var result = fixture.resolver().releaseEnvironmentForProvisioning(fixture.f().deployment, fixture.f().version, fixture.release());
            assertThat(result.secretEnvironment()).hasSize(1);
            var em = EntityManagerFactoryUtils.getTransactionalEntityManager(entityManagerFactory);
            assertThat(em.contains(managed)).isTrue();
            assertThat(managed.getCurrentStepDescription()).isEqualTo("pending provider state must survive authority readback");
            return null;
        }));
        assertThat(releases.findById(fixture.release().getId()).orElseThrow().getCurrentStepDescription())
            .isEqualTo("pending provider state must survive authority readback");
    }

    private void cache(Fixture fixture) {
        var f = fixture.f();
        deployments.findById(f.deployment.getId()).orElseThrow(); versions.findById(f.version.getId()).orElseThrow();
        releases.findById(fixture.release().getId()).orElseThrow(); targets.findById(f.target.getId()).orElseThrow();
        var em = EntityManagerFactoryUtils.getTransactionalEntityManager(entityManagerFactory);
        var authorityId = jdbc.queryForObject("select id from platform_runtime_target_authorities where deployment_id=? and target_profile_id=?",
            String.class, f.deployment.getId(), f.target.getId());
        assertThat(em.find(RuntimeAuthorityEntity.class, authorityId)).isNotNull();
        assertThat(em.find(RuntimeAuthoritySnapshotEntity.class, fixture.frozen().snapshotId())).isNotNull();
        for (var secret : fixture.frozen().binding().secrets().values()) assertThat(em.find(PlatformSecretEntity.class, secret.reference())).isNotNull();
    }

    private String candidateId(Fixture fixture) {
        return jdbc.queryForObject("select id from platform_runtime_authority_snapshots where deployment_id=? and release_id is null",
            String.class, fixture.f().deployment.getId());
    }

    private Fixture fixture() throws Exception {
        var f = new ExternalIntegrationFixture();
        String suffix = UUID.randomUUID().toString(); Instant now = Instant.now();
        f.deployment.setTenantId("worker-tenant-" + suffix);
        var ownerTenant = new com.ai.fabric.platform.backend.tenant.entity.PlatformTenantEntity();
        ownerTenant.setId(f.deployment.getTenantId());
        ownerTenant.setCustomerId(f.deployment.getCustomerId());
        ownerTenant.setStatus("ACTIVE");
        when(f.tenants.findById(ownerTenant.getId())).thenReturn(Optional.of(ownerTenant));
        for (String customer : List.of("customer-generic", "foreign-customer")) {
            if (jdbc.queryForObject("select count(*) from platform_customers where id=?", Long.class, customer) == 0)
                jdbc.update("insert into platform_customers(id,name,slug,status,platform_managed,created_at,updated_at) values (?,?,?,'ACTIVE',false,current_timestamp,current_timestamp)", customer, customer, customer);
        }
        for (String tenant : List.of(f.deployment.getTenantId(), f.deployment.getTenantId() + "-other")) {
            if (jdbc.queryForObject("select count(*) from platform_customer_tenants where id=?", Long.class, tenant) == 0)
                jdbc.update("insert into platform_customer_tenants(id,customer_id,name,slug,status,platform_managed,created_at,updated_at) values (?,'customer-generic',?,?,'ACTIVE',false,current_timestamp,current_timestamp)", tenant, tenant, tenant);
        }
        f.deployment.setId("worker-dep-" + suffix); f.deployment.setName("Worker fixture");
        f.deployment.setEnvironmentName("staging"); f.deployment.setTemplateId("dev-openai-milvus");
        f.deployment.setBehaviorType("CONVERSATIONAL"); f.deployment.setCreatedAt(now); f.deployment.setUpdatedAt(now);
        f.version.setId("worker-ver-" + suffix); f.version.setDeploymentId(f.deployment.getId());
        f.version.setSourceDraftId("worker-draft-" + suffix); f.version.setVersionLabel("v1");
        f.version.setActionsConfigJson("{}"); f.version.setEntityConfigJson("{}"); f.version.setRoutingConfigJson("{}");
        f.version.setProviderConfigJson("{}"); f.version.setSecurityConfigJson("{\"publicRuntimeBootstrapEnabled\":false}");
        f.version.setPromptConfigJson("{}"); f.version.setActionsArtifactYaml("actions: []");
        f.version.setEntityArtifactYaml("entities: []"); f.version.setRoutingArtifactYaml("routing: []");
        f.version.setManifestJson("{}"); f.version.setPublishedAt(now);
        f.target.setName("Worker target"); f.target.setCreatedAt(now); f.target.setUpdatedAt(now);
        var draft = new DeploymentDraftEntity(); BeanUtils.copyProperties(f.version, draft);
        draft.setId(f.version.getSourceDraftId()); draft.setRevisionNumber(1); draft.setCreatedAt(now); draft.setUpdatedAt(now);
        deployments.saveAndFlush(f.deployment); drafts.saveAndFlush(draft); versions.saveAndFlush(f.version); targets.saveAndFlush(f.target);
        when(f.deployments.findById(f.deployment.getId())).thenReturn(Optional.of(f.deployment));
        when(f.deployments.findByIdForUpdate(f.deployment.getId())).thenReturn(Optional.of(f.deployment));
        when(f.versions.findById(f.version.getId())).thenReturn(Optional.of(f.version));
        when(f.associations.findById(f.deployment.getId())).thenReturn(Optional.of(new CustomerOfferDeploymentEntity(f.deployment.getId(),
            f.deployment.getCustomerId(), f.deployment.getTenantId(), "offer-generic", f.target.getId(), "offer-fingerprint", 3, "fixture", now)));
        f.bind();
        var candidate = authority.freezeCandidateForAuthorizedApply(f.deployment.getId(), f.target.getId(), f.version.getId(), List.of());
        var release = new DeploymentReleaseEntity(); release.setId("worker-rel-" + suffix);
        release.setDeploymentId(f.deployment.getId()); release.setDeploymentVersionId(f.version.getId()); release.setTargetProfileId(f.target.getId());
        release.setStatus("PRE_APPLY_VERIFYING"); release.setProvisioningStatus("QUEUED"); release.setVerificationStatus("PASSED");
        release.setProvisioningTarget("COOLIFY"); release.setProvisioningDetailsJson("{}"); release.setCreatedAt(now); release.setAppliedAt(now); release.setUpdatedAt(now);
        releases.saveAndFlush(release);
        var frozen = authority.freezeReleaseForAuthorizedApply(f.deployment.getId(), f.target.getId(), release.getId(), candidate.snapshotId(), candidate.fingerprint());
        release.setRuntimeAuthoritySnapshotId(frozen.snapshotId()); release.setRuntimeAuthorityFingerprint(frozen.fingerprint()); releases.saveAndFlush(release);
        var resolver = new ExternalIntegrationResolver(deployments, versions, targets, releases, f.associations, f.entitlements, f.composition,
            authority, f.store, f.policy, f.integrity, f.endpoints, f.compiler, f.codec, f.mapper, f.owners);
        // Only synchronous fixture publication uses an authenticated staff principal. It is not carried to the executor.
        f.staff();
        transaction(() -> resolver.freezeRelease(f.deployment.getId(), f.version.getId(), f.target.getId(), release.getId(), frozen.fingerprint()));
        SecurityContextHolder.clearContext();
        return new Fixture(f, resolver, release, frozen);
    }

    private <T> T transaction(Callable<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            try { return work.call(); } catch (RuntimeException error) { throw error; } catch (Exception error) { throw new IllegalStateException(error); }
        });
    }
    private <T> T inWorker(Callable<T> work) throws Exception {
        try (var executor = Executors.newSingleThreadExecutor()) {
            return executor.submit(work).get(30, TimeUnit.SECONDS);
        }
    }
    private record Fixture(ExternalIntegrationFixture f, ExternalIntegrationResolver resolver,
                           DeploymentReleaseEntity release, RuntimeAuthorityBinding.Snapshot frozen) {}
}
