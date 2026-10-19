package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.*;
import com.ai.fabric.platform.backend.deployment.model.DeploymentAiStateStorageContracts.Request;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.repository.*;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementGateService;
import com.ai.fabric.platform.backend.secret.entity.PlatformSecretEntity;
import com.ai.fabric.platform.backend.secret.repository.PlatformSecretRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hibernate.SessionFactory;
import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaDialect;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real JPA deployment-row locks, PostgreSQL constraints and transactional vault writes; no skipped Docker cases. */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeploymentAiStateStorageGenerationsPostgresTest {
    @Container private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final PlatformAuditService audit = mock(PlatformAuditService.class);
    private final TargetRetirementGateService retirement = mock(TargetRetirementGateService.class);
    private final DeploymentAiStateStorageProbe probe = mock(DeploymentAiStateStorageProbe.class);
    private SessionFactory entityManagerFactory;
    private TransactionTemplate transaction;
    private JdbcTemplate jdbc;
    private DeploymentRepository deployments;
    private DeploymentVersionRepository versions;
    private DeploymentTargetProfileRepository targets;
    private DeploymentProviderResourceHandleRepository handles;
    private PlatformSecretService secrets;
    private DeploymentAiStateStorageBindingService service;

    @BeforeAll void persistence() {
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        var configuration = new Configuration().addAnnotatedClass(DeploymentEntity.class)
            .addAnnotatedClass(DeploymentVersionEntity.class).addAnnotatedClass(DeploymentTargetProfileEntity.class)
            .addAnnotatedClass(DeploymentProviderResourceHandleEntity.class).addAnnotatedClass(PlatformSecretEntity.class)
            .setPhysicalNamingStrategy(new CamelCaseToUnderscoresNamingStrategy());
        configuration.getProperties().put("hibernate.connection.datasource", dataSource);
        configuration.setProperty("hibernate.hbm2ddl.auto", "create");
        entityManagerFactory = configuration.buildSessionFactory();
        var manager = new JpaTransactionManager(entityManagerFactory);
        manager.setDataSource(dataSource); manager.setJpaDialect(new HibernateJpaDialect()); manager.afterPropertiesSet();
        transaction = new TransactionTemplate(manager);
        var factory = new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory));
        deployments = factory.getRepository(DeploymentRepository.class);
        versions = factory.getRepository(DeploymentVersionRepository.class);
        targets = factory.getRepository(DeploymentTargetProfileRepository.class);
        handles = factory.getRepository(DeploymentProviderResourceHandleRepository.class);
        secrets = new PlatformSecretService(factory.getRepository(PlatformSecretRepository.class), audit, new MockEnvironment());
        jdbc.execute("create table platform_deployment_releases(id varchar primary key,deployment_id varchar,"
            + "deployment_version_id varchar,target_profile_id varchar,status varchar)");
        String root = System.getProperty("repoRoot", Path.of("../..").toAbsolutePath().normalize().toString());
        new ResourceDatabasePopulator(new FileSystemResource(root + "/Platfrom/backend/src/main/resources/db/migration/V198__customer_ai_state_storage_bindings.sql"),
            new FileSystemResource(root + "/Platfrom/backend/src/main/resources/db/migration/V200__ai_state_storage_credential_generations.sql")).execute(dataSource);
    }

    @AfterAll void close() { if (entityManagerFactory != null) entityManagerFactory.close(); }

    @BeforeEach void seed() {
        reset(audit, retirement, probe);
        jdbc.execute("truncate deployment_ai_state_storage_credential_generations, deployment_ai_state_storage_approvals,"
            + "deployment_ai_state_schema_claims, deployment_provider_resource_handles, platform_secrets,"
            + "platform_deployment_versions, platform_deployments, platform_deployment_releases, deployment_target_profiles cascade");
        tx(() -> {
            var deployment = new DeploymentEntity(); deployment.setId("deployment"); deployment.setName("AI only");
            deployment.setCustomerId("customer"); deployment.setTenantId("tenant"); deployment.setEnvironmentName("staging");
            deployment.setTemplateId("ai-template"); deployment.setBehaviorType("CONVERSATIONAL"); deployment.setStatus("DRAFT");
            deployment.setCreatedAt(Instant.now()); deployment.setUpdatedAt(Instant.now()); deployments.saveAndFlush(deployment);
            var target = new DeploymentTargetProfileEntity(); target.setId("target"); target.setName("Scoped target");
            target.setEnvironmentName("staging"); target.setProviderType(DeploymentProviderType.COOLIFY); target.setActive(true);
            target.setSourceStrategy("PREBUILT_IMAGE"); target.setProviderConfigJson("{}"); target.setResourceDefaultsJson("{}");
            target.setNetworkPolicyJson("{\"aiStateStorageAllowedHosts\":[\"customer.example\"]}");
            target.setCreatedAt(Instant.now()); target.setUpdatedAt(Instant.now()); targets.saveAndFlush(target);
            for (String id : new String[]{"old", "candidate"}) {
                var version = new DeploymentVersionEntity(); version.setId(id); version.setDeploymentId("deployment");
                version.setSourceDraftId("draft"); version.setVersionLabel(id); version.setStatus("PUBLISHED"); version.setConfigHash("hash-" + id);
                version.setActionsConfigJson("{}"); version.setEntityConfigJson("{}"); version.setRoutingConfigJson("{}");
                version.setProviderConfigJson("{\"aiStateStorageMode\":\"CUSTOMER_POSTGRES\"}"); version.setSecurityConfigJson("{}");
                version.setPromptConfigJson("{}"); version.setActionsArtifactYaml("{}"); version.setEntityArtifactYaml("{}");
                version.setRoutingArtifactYaml("{}"); version.setManifestJson("{}"); version.setPublishedAt(Instant.now());
                versions.saveAndFlush(version);
            }
            return null;
        });
        service = new DeploymentAiStateStorageBindingService(deployments, versions, targets, handles, secrets,
            retirement, audit, probe, jdbc, mapper);
    }

    @Test void committedGenerationAndOldVaultSurviveReconstructionAndCandidateFailureRollback() {
        var old = tx(() -> service.bind("deployment", "target", request("old", "old-private-password")));
        var oldSecrets = jdbc.queryForList("select * from platform_secrets order by name");
        var oldApprovals = jdbc.queryForList("select * from deployment_ai_state_storage_approvals");
        doThrow(new IllegalStateException("fixture final-audit failure"))
            .when(audit).record(eq("EXTERNAL_AI_STATE_BOUND"), anyString(), anyString(), anyMap());
        assertThatThrownBy(() -> tx(() -> service.bind("deployment", "target", request("candidate", "new-private-password"))))
            .hasMessageContaining("fixture final-audit failure");
        assertThat(jdbc.queryForList("select * from platform_secrets order by name")).isEqualTo(oldSecrets);
        assertThat(jdbc.queryForList("select * from deployment_ai_state_storage_approvals")).isEqualTo(oldApprovals);
        assertThat(jdbc.queryForObject("select count(*) from deployment_ai_state_storage_credential_generations", Integer.class)).isEqualTo(1);
        reset(audit);
        var staged = tx(() -> service.bind("deployment", "target", request("candidate", "new-private-password")));
        service = new DeploymentAiStateStorageBindingService(deployments, versions, targets, handles, secrets,
            retirement, audit, probe, jdbc, mapper);
        assertThat(tx(() -> service.get("deployment", "target", "old"))).isEqualTo(old);
        assertThat(tx(() -> service.get("deployment", "target", "candidate"))).isEqualTo(staged);
        assertThat(jdbc.queryForList("select * from platform_secrets order by name")).containsAll(oldSecrets);
        assertThat(jdbc.queryForObject("select count(*) from platform_secrets", Integer.class)).isEqualTo(4);
    }

    @Test void competingCandidateCredentialsSerializeOnTheDeploymentRowAndCannotOverwriteTheWinner() throws Exception {
        tx(() -> service.bind("deployment", "target", request("old", "old-private-password")));
        var enteredProbe = new CountDownLatch(1); var allowCommit = new CountDownLatch(1);
        doAnswer(call -> { enteredProbe.countDown(); assertThat(allowCommit.await(10, TimeUnit.SECONDS)).isTrue(); return null; })
            .when(probe).verify(any());
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> tx(() -> service.bind("deployment", "target", request("candidate", "first-candidate-password"))));
            assertThat(enteredProbe.await(10, TimeUnit.SECONDS)).isTrue();
            var secondPid = new AtomicInteger();
            var second = executor.submit(() -> tx(() -> {
                secondPid.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                return service.bind("deployment", "target", request("candidate", "competing-password"));
            }));
            awaitBlockedRowLock(secondPid);
            assertThat(second.isDone()).isFalse();
            allowCommit.countDown();
            var committed = first.get(10, TimeUnit.SECONDS);
            assertThatThrownBy(() -> second.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasStackTraceContaining("VERSION_CREDENTIALS_IMMUTABLE");
            assertThat(tx(() -> service.get("deployment", "target", "candidate"))).isEqualTo(committed);
            assertThat(jdbc.queryForObject("select count(*) from deployment_ai_state_storage_credential_generations", Integer.class)).isEqualTo(2);
            assertThat(jdbc.queryForObject("select count(*) from platform_secrets", Integer.class)).isEqualTo(4);
        } finally { allowCommit.countDown(); executor.shutdownNow(); assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); }
    }

    @Test void queuedApplyCommittedUnderTheSameRowLockBlocksANewCredentialGeneration() throws Exception {
        tx(() -> service.bind("deployment", "target", request("old", "old-private-password")));
        var locked = new CountDownLatch(1); var allowCommit = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var apply = executor.submit(() -> tx(() -> {
                deployments.findByIdForUpdate("deployment").orElseThrow();
                jdbc.update("insert into platform_deployment_releases values (?,?,?,?,?,?,?)", "queued", "deployment", "candidate", "target",
                    "APPLY_REQUESTED", null, null);
                locked.countDown();
                try { assertThat(allowCommit.await(10, TimeUnit.SECONDS)).isTrue(); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
                return null;
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            var bindPid = new AtomicInteger();
            var bind = executor.submit(() -> tx(() -> {
                bindPid.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                return service.bind("deployment", "target", request("candidate", "new-private-password"));
            }));
            awaitBlockedRowLock(bindPid);
            allowCommit.countDown(); apply.get(10, TimeUnit.SECONDS);
            assertThatThrownBy(() -> bind.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasStackTraceContaining("NEVER_APPLIED_VERSION_REQUIRED");
            assertThat(jdbc.queryForObject("select count(*) from deployment_ai_state_storage_credential_generations", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from platform_secrets", Integer.class)).isEqualTo(2);
        } finally { allowCommit.countDown(); executor.shutdownNow(); assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); }
    }

    private void awaitBlockedRowLock(AtomicInteger pid) {
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(pid.get()).isPositive();
            assertThat(jdbc.queryForObject("select wait_event_type from pg_stat_activity where pid=?", String.class, pid.get())).isEqualTo("Lock");
        });
    }
    private <T> T tx(Supplier<T> work) { return transaction.execute(status -> work.get()); }
    private Request request(String version, String password) {
        return new Request(version, "customer.example", 5432, "customer_database", "ai_runtime", "ai_connector", "ai_role", password,
            true, "approval-" + version);
    }
}
