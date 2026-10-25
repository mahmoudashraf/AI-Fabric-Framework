package com.ai.fabric.platform.backend.deployment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.CountDownLatch;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.ai.fabric.platform.backend.deployment.service.ManagedResourceOwnershipFixture.SCOPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class ManagedResourceOwnershipPostgresTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private JdbcTemplate jdbc;
    private ManagedResourceOwnershipService ownership;
    private TransactionTemplate caller;

    @BeforeEach
    void initialize() {
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var transactions = new DataSourceTransactionManager(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        ownership = new ManagedResourceOwnershipService(jdbc, new ObjectMapper(), transactions);
        caller = new TransactionTemplate(transactions);
        Flyway.configure().dataSource(dataSource).cleanDisabled(false).load().clean();
        migrate("196");
    }

    @Test
    void fullChainUpgradeDoesNotBackfillOwnershipFromHistoricalRegistry() {
        jdbc.update("""
            INSERT INTO platform_customers (id,name,slug,status,platform_managed,created_at,updated_at)
            VALUES ('historical-customer','Synthetic customer','historical-customer','ACTIVE',false,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            """);
        jdbc.update("""
            INSERT INTO platform_customer_tenants (id,customer_id,name,slug,status,platform_managed,created_at,updated_at)
            VALUES ('historical-tenant','historical-customer','Synthetic tenant','historical-tenant','ACTIVE',false,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            """);
        jdbc.update("""
            INSERT INTO platform_deployments
              (id,name,environment_name,template_id,status,customer_id,tenant_id,created_at,updated_at)
            VALUES ('historical-deployment','Historical AI','stage','synthetic','ARCHIVED','historical-customer','historical-tenant',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            """);
        jdbc.update("""
            INSERT INTO platform_deployment_managed_vector_resources
              (id,deployment_id,vendor,vector_strategy,vector_provisioning_mode,managed_mode,resource_type,
               resource_name,resource_reference,endpoint,resource_status,created_at,updated_at)
            VALUES ('historical-vector','historical-deployment','pinecone','pinecone','PLATFORM_MANAGED',
                    'MANAGED_SERVERLESS_INDEX','INDEX','historical','historical','https://historical.example',
                    'DETACHED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            """);
        String history = jdbc.queryForObject("SELECT row_to_json(v)::text FROM platform_deployment_managed_vector_resources v WHERE id='historical-vector'", String.class);
        migrate("197");
        assertThat(jdbc.queryForObject("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1", String.class))
            .isEqualTo("197");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform_managed_resource_ownership", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform_managed_resource_ownership_mutex", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT row_to_json(v)::text FROM platform_deployment_managed_vector_resources v WHERE id='historical-vector'", String.class))
            .isEqualTo(history);
        assertThatThrownBy(() -> ownership.requireOwned(SCOPE, "pinecone", "INDEX", "historical", "https://historical.example"))
            .hasMessageContaining("OWNERSHIP_REQUIRED");
    }

    @Test
    void durableReceiptAndMicrosecondIdentitySurviveOuterRollback() {
        migrate("197");
        caller.executeWithoutResult(tx -> {
            ownership.create(SCOPE, "railway", "SERVICE", "project", "runtime", () -> "service",
                value -> new ManagedResourceOwnershipService.Identity(value, null));
            tx.setRollbackOnly();
        });
        var receipt = ownership.requireOwned(SCOPE, "railway", "SERVICE", "service", null);
        assertThat(receipt.state()).isEqualTo("OWNED");
        ownership.beginDeletion(receipt);
        ownership.deleted(receipt.id());
        assertThat(ownership.receipt(receipt.id()).state()).isEqualTo("DELETED");
        assertThatThrownBy(() -> ownership.requireOwned(SCOPE, "railway", "SERVICE", "service", null))
            .hasMessageContaining("OWNERSHIP_CHANGED");
    }

    @Test
    void ambiguousCreatePersistsPendingEvidenceAfterOuterRollback() {
        migrate("197");
        caller.executeWithoutResult(tx -> {
            assertThatThrownBy(() -> ownership.create(SCOPE, "railway", "SERVICE", "project", "runtime", () -> {
                throw new IllegalStateException("synthetic uncertain provider outcome");
            }, value -> new ManagedResourceOwnershipService.Identity("service", null)))
                .hasMessageContaining("uncertain provider outcome");
            tx.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("SELECT state FROM platform_managed_resource_ownership", String.class)).isEqualTo("CREATE_PENDING");
        var effects = new AtomicInteger();
        assertThatThrownBy(() -> ownership.create(SCOPE, "railway", "SERVICE", "project", "runtime", () -> effects.incrementAndGet(),
            value -> new ManagedResourceOwnershipService.Identity("service", null)))
            .hasMessageContaining("RECONCILIATION_REQUIRED");
        assertThat(effects).hasValue(0);
    }

    @Test
    void competingCreateCannotExecuteWhileFirstProviderOutcomeIsPending() throws Exception {
        migrate("197");
        var effectStarted = new CountDownLatch(1);
        var finishEffect = new CountDownLatch(1);
        var effects = new AtomicInteger();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> ownership.create(SCOPE, "railway", "SERVICE", "project", "runtime", () -> {
                effects.incrementAndGet();
                effectStarted.countDown();
                try {
                    if (!finishEffect.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("synthetic provider timeout");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
                return "service";
            }, value -> new ManagedResourceOwnershipService.Identity(value, null)));
            try {
                assertThat(effectStarted.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> ownership.create(SCOPE, "railway", "SERVICE", "project", "runtime", () -> {
                    effects.incrementAndGet(); return "other-service";
                }, value -> new ManagedResourceOwnershipService.Identity(value, null)))
                    .hasMessageContaining("RECONCILIATION_REQUIRED");
                assertThat(jdbc.queryForObject("SELECT state FROM platform_managed_resource_ownership", String.class)).isEqualTo("CREATE_PENDING");
            } finally {
                finishEffect.countDown();
            }
            assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo("service");
        }
        assertThat(effects).hasValue(1);
        assertThat(ownership.requireOwned(SCOPE, "railway", "SERVICE", "service", null).state()).isEqualTo("OWNED");
    }

    @Test
    void atomicDeletionPreparationHasOnePostgresWinnerAndNeverRearmsPendingOrDeleted() throws Exception {
        migrate("197");
        ownership.create(SCOPE, "coolify", "DEPENDENCY_APPLICATION", "parent", "inbox", () -> "app",
            value -> new ManagedResourceOwnershipService.Identity(value, "https://coolify.test"));
        var receipt = ownership.requireOwned(SCOPE, "coolify", "DEPENDENCY_APPLICATION", "app", "https://coolify.test");
        var preparation = new ManagedResourceOwnershipService.DeletionPreparation(receipt, List.of());
        var start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> { start.await(); return ownership.prepareDeletion(preparation, null, () -> { }); });
            var second = executor.submit(() -> { start.await(); return ownership.prepareDeletion(preparation, null, () -> { }); });
            start.countDown();
            assertThat(List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        }
        assertThat(ownership.receipt(receipt.id()).state()).isEqualTo("DELETE_PENDING");
        assertThat(ownership.prepareDeletion(preparation, null, () -> { })).isFalse();
        ownership.deleted(receipt.id());
        assertThat(ownership.prepareDeletion(preparation, null, () -> { })).isFalse();
    }

    @Test
    void atomicDeletionPreparationRollsBackOnFencedWorkerWithoutConsumingFirstDispatch() {
        migrate("197");
        ownership.create(SCOPE, "coolify", "DEPENDENCY_APPLICATION", "parent", "inbox", () -> "app",
            value -> new ManagedResourceOwnershipService.Identity(value, "https://coolify.test"));
        var receipt = ownership.requireOwned(SCOPE, "coolify", "DEPENDENCY_APPLICATION", "app", "https://coolify.test");
        var preparation = new ManagedResourceOwnershipService.DeletionPreparation(receipt, List.of());
        assertThatThrownBy(() -> ownership.prepareDeletion(preparation, null, () -> {
            throw new IllegalStateException("LIFECYCLE_WORKER_FENCED");
        })).hasMessageContaining("LIFECYCLE_WORKER_FENCED");
        assertThat(ownership.receipt(receipt.id())).isEqualTo(receipt);
        assertThat(ownership.prepareDeletion(preparation, null, () -> { })).isTrue();
    }

    private void migrate(String target) {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration").target(MigrationVersion.fromVersion(target)).load().migrate();
    }
}
