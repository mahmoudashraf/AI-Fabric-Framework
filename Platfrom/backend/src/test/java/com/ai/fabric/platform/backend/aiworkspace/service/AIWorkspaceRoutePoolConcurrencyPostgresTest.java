package com.ai.fabric.platform.backend.aiworkspace.service;

import com.ai.fabric.platform.backend.audit.entity.PlatformAuditEventEntity;
import com.ai.fabric.platform.backend.audit.repository.PlatformAuditEventRepository;
import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Actual PG/Hikari/JPA only: neither test creates a worker, provider client or DNS verifier. */
@Testcontainers
@Timeout(20)
class AIWorkspaceRoutePoolConcurrencyPostgresTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withImagePullPolicy(image -> false);

    private static final String HOSTNAME = "pool.apps.test";
    private final ObjectMapper mapper = new ObjectMapper();
    private final AIWorkspaceRouteStore.Binding binding = new AIWorkspaceRouteStore.Binding(
        "pool-service", "pool-profile", "a".repeat(64), "pool-native-app", List.of());
    private HikariDataSource pool;
    private CountingDataSource dataSource;
    private JdbcTemplate jdbc;
    private LocalContainerEntityManagerFactoryBean factory;
    private JpaTransactionManager transactions;
    private PlatformAuditService audit;

    @BeforeEach
    void setUp() throws Exception {
        var config = new HikariConfig();
        config.setPoolName("route-read-pool");
        config.setJdbcUrl(POSTGRES.getJdbcUrl());
        config.setUsername(POSTGRES.getUsername());
        config.setPassword(POSTGRES.getPassword());
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(500);
        config.setInitializationFailTimeout(1_000);
        pool = new HikariDataSource(config);
        dataSource = new CountingDataSource(pool);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP SCHEMA public CASCADE");
        jdbc.execute("CREATE SCHEMA public");
        // Real prerequisite and route migrations, not reduced stand-ins for these tables.
        for (String migration : List.of("V1__initial_platform_schema.sql", "V2__platform_audit_events.sql",
            "V16__customer_tenant_foundation.sql", "V47__customer_consumers.sql", "V153__ai_workspace_installations.sql",
            "V158__standalone_ai_workspace_delivery.sql", "V206__ai_workspace_durable_domain_routes.sql")) {
            new ResourceDatabasePopulator(new ClassPathResource("db/migration/" + migration)).execute(dataSource);
        }
        seedAppliedRoute();
        factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setManagedTypes(PersistenceManagedTypes.of(PlatformAuditEventEntity.class.getName()));
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "none", "hibernate.physical_naming_strategy",
            "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));
        factory.afterPropertiesSet();
        EntityManagerFactory entityManagerFactory = factory.getObject();
        transactions = new JpaTransactionManager(entityManagerFactory);
        transactions.setDataSource(dataSource);
        var repositories = new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory));
        audit = new PlatformAuditService(repositories.getRepository(PlatformAuditEventRepository.class), mapper);
    }

    @AfterEach
    void tearDown() {
        if (factory != null) factory.destroy();
        if (pool != null) pool.close();
    }

    @Test
    void oldRequiresNewReadReproducesTwoPoolAcquisitionTimeouts() throws Exception {
        DatabaseSnapshot before = snapshot();
        // Force only this store's reads to the old propagation; committed source is never edited.
        PlatformTransactionManager oldReadPropagation = new PlatformTransactionManager() {
            @Override public TransactionStatus getTransaction(TransactionDefinition definition) {
                var old = new DefaultTransactionDefinition(definition);
                old.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                return transactions.getTransaction(old);
            }
            @Override public void commit(TransactionStatus status) { transactions.commit(status); }
            @Override public void rollback(TransactionStatus status) { transactions.rollback(status); }
        };
        var store = new AIWorkspaceRouteStore(jdbc, mapper, oldReadPropagation, audit);
        List<ReadOutcome> outcomes = readWithBothConnectionsHeld(store);
        assertThat(outcomes).allSatisfy(outcome -> {
            assertThat(outcome.failure()).isInstanceOf(CannotCreateTransactionException.class)
                .hasRootCauseInstanceOf(SQLTransientConnectionException.class)
                .hasStackTraceContaining("Connection is not available, request timed out");
            assertThat(outcome.applied()).isFalse();
            assertThat(outcome.beforePid()).isEqualTo(outcome.afterPid());
        });
        assertThat(dataSource.borrowAttempts.get()).isEqualTo(4);
        assertThat(dataSource.successfulBorrows.get()).isEqualTo(2);
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void appliedReadJoinsBothOuterConnectionsWithoutAnExtraBorrowOrMutation() throws Exception {
        DatabaseSnapshot before = snapshot();
        var store = new AIWorkspaceRouteStore(jdbc, mapper, transactions, audit);
        List<ReadOutcome> outcomes = readWithBothConnectionsHeld(store);
        assertThat(outcomes).allSatisfy(outcome -> {
            assertThat(outcome.failure()).isNull();
            assertThat(outcome.applied()).isTrue();
            assertThat(outcome.beforePid()).isEqualTo(outcome.afterPid());
        });
        assertThat(outcomes).extracting(ReadOutcome::beforePid).doesNotHaveDuplicates();
        assertThat(dataSource.borrowAttempts.get()).isEqualTo(2);
        assertThat(dataSource.successfulBorrows.get()).isEqualTo(2);
        assertThat(snapshot()).isEqualTo(before);
    }

    private List<ReadOutcome> readWithBothConnectionsHeld(AIWorkspaceRouteStore store) throws Exception {
        dataSource.borrowAttempts.set(0);
        dataSource.successfulBorrows.set(0);
        var borrowed = new CountDownLatch(2);
        var beginReads = new CountDownLatch(1);
        var finishReadsWhileHoldingConnections = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Callable<ReadOutcome> reader = () -> {
                var outer = new TransactionTemplate(transactions);
                outer.setTimeout(8);
                return outer.execute(ignored -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                    int beforePid = jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);
                    Connection outerConnection = heldConnection();
                    borrowed.countDown();
                    await(beginReads);
                    boolean applied = false;
                    RuntimeException failure = null;
                    try { applied = store.isApplied(binding, HOSTNAME); }
                    catch (RuntimeException ex) { failure = ex; }
                    // A first timed-out caller must not release a connection for the other old read.
                    await(finishReadsWhileHoldingConnections);
                    assertThat(heldConnection()).isSameAs(outerConnection);
                    int afterPid = jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);
                    return new ReadOutcome(beforePid, afterPid, applied, failure);
                });
            };
            Future<ReadOutcome> first = executor.submit(reader);
            Future<ReadOutcome> second = executor.submit(reader);
            assertThat(borrowed.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(pool.getMaximumPoolSize()).isEqualTo(2);
            assertThat(pool.getHikariPoolMXBean().getTotalConnections()).isEqualTo(2);
            assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isEqualTo(2);
            assertThat(pool.getHikariPoolMXBean().getIdleConnections()).isZero();
            assertThat(dataSource.borrowAttempts.get()).isEqualTo(2);
            beginReads.countDown();
            var outcomes = List.of(first.get(8, TimeUnit.SECONDS), second.get(8, TimeUnit.SECONDS));
            assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isZero();
            assertThat(pool.getHikariPoolMXBean().getThreadsAwaitingConnection()).isZero();
            return outcomes;
        } finally {
            beginReads.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private Connection heldConnection() {
        var holder = (ConnectionHolder) TransactionSynchronizationManager.getResource(dataSource);
        assertThat(holder).isNotNull();
        return holder.getConnection();
    }

    private static void await(CountDownLatch latch) {
        try { assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
    }

    private static void await(CyclicBarrier barrier) {
        try { barrier.await(3, TimeUnit.SECONDS); }
        catch (Exception ex) { throw new IllegalStateException("Pool fixture barrier did not complete", ex); }
    }

    private void seedAppliedRoute() throws Exception {
        jdbc.update("""
            INSERT INTO platform_customers(id,name,slug,status,created_at,updated_at)
            VALUES ('pool-customer','Synthetic pool customer','pool-customer','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            """);
        jdbc.update("""
            INSERT INTO platform_consumers(id,customer_id,consumer_id,display_name,status,created_at,updated_at)
            VALUES ('pool-consumer','pool-customer','pool-consumer','Synthetic pool consumer','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            """);
        jdbc.update("""
            INSERT INTO ai_workspace_installations(id,installation_id,customer_id,consumer_entity_id,display_name,status,
                experience_pack_code,experience_pack_version,connection_mode,connection_profile_code,connection_profile_version,
                connection_configuration_json,allowed_origins_json,configuration_json,created_at,updated_at,delivery_mode,app_slug)
            VALUES ('pool-installation','awi-pool','pool-customer','pool-consumer','Synthetic pool installation','ACTIVE',
                'generic','1.0.0','PUBLIC_RUNTIME_ANONYMOUS','runtime-anonymous-direct','1.0.0','{}','[]','{}',
                CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'STANDALONE','pool')
            """);
        jdbc.update("""
            INSERT INTO ai_workspace_domains(id,installation_entity_id,hostname,domain_type,status,verification_token,
                created_at,updated_at,verified_at)
            VALUES ('pool-domain','pool-installation',?,'PLATFORM_DEFAULT','VERIFIED','SYNTHETIC-NOT-A-CREDENTIAL',
                CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            """, HOSTNAME);
        String identity = mapper.writeValueAsString(List.of("pool-domain", HOSTNAME, "PLATFORM_DEFAULT", "pool-installation"));
        String fingerprint = AIWorkspaceRouteStore.fingerprint(List.of(identity));
        jdbc.update("""
            INSERT INTO ai_workspace_route_reconciliations(route_key,service_id,target_profile_id,target_profile_fingerprint,
                application_uuid,generation,desired_fingerprint,desired_domains,managed_domains,phase,applied_generation)
            VALUES (?,?,?,?,?,1,?,?,?,'IDLE',1)
            """, binding.key(), binding.serviceId(), binding.profileId(), binding.profileFingerprint(), binding.applicationUuid(),
            fingerprint, mapper.writeValueAsString(List.of(HOSTNAME)), mapper.writeValueAsString(List.of(HOSTNAME)));
        jdbc.update("""
            INSERT INTO platform_audit_events(id,actor_id,actor_role,action,target_type,target_id,details_json,created_at)
            VALUES ('pool-audit','synthetic-reviewer','PLATFORM_ADMIN','SYNTHETIC_BASELINE','MANAGED_PRODUCT_SERVICE',
                'pool-service','{"synthetic":true}',CURRENT_TIMESTAMP)
            """);
    }

    private DatabaseSnapshot snapshot() {
        List<String> tableNames = jdbc.queryForList(
            "SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename", String.class);
        Map<String, List<String>> rows = new LinkedHashMap<>();
        for (String table : tableNames) {
            assertThat(table).matches("[a-z_][a-z0-9_]*");
            rows.put(table, jdbc.queryForList("SELECT to_jsonb(t)::text FROM public.\"" + table
                + "\" t ORDER BY to_jsonb(t)::text COLLATE \"C\"", String.class));
        }
        List<String> structure = new ArrayList<>(jdbc.queryForList("""
            SELECT to_jsonb(c)::text FROM information_schema.columns c WHERE table_schema='public'
            ORDER BY table_name,ordinal_position
            """, String.class));
        structure.addAll(jdbc.queryForList("""
            SELECT tablename || ':' || indexname || ':' || indexdef FROM pg_indexes
            WHERE schemaname='public' ORDER BY tablename,indexname
            """, String.class));
        structure.addAll(jdbc.queryForList("""
            SELECT c.conrelid::regclass::text || ':' || c.conname || ':' || pg_get_constraintdef(c.oid)
            FROM pg_constraint c JOIN pg_namespace n ON n.oid=c.connamespace
            WHERE n.nspname='public' ORDER BY c.conrelid::regclass::text,c.conname
            """, String.class));
        return new DatabaseSnapshot(rows, structure);
    }

    private record ReadOutcome(int beforePid, int afterPid, boolean applied, RuntimeException failure) { }
    private record DatabaseSnapshot(Map<String, List<String>> rows, List<String> structure) { }

    private static class CountingDataSource extends DelegatingDataSource {
        private final AtomicInteger borrowAttempts = new AtomicInteger();
        private final AtomicInteger successfulBorrows = new AtomicInteger();

        CountingDataSource(HikariDataSource target) { super(target); }
        @Override public Connection getConnection() throws SQLException {
            borrowAttempts.incrementAndGet();
            Connection connection = super.getConnection();
            successfulBorrows.incrementAndGet();
            return connection;
        }
        @Override public Connection getConnection(String username, String password) throws SQLException {
            borrowAttempts.incrementAndGet();
            Connection connection = super.getConnection(username, password);
            successfulBorrows.incrementAndGet();
            return connection;
        }
    }
}
