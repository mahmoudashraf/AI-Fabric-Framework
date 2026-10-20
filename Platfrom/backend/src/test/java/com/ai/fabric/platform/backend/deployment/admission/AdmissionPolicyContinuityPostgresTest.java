package com.ai.fabric.platform.backend.deployment.admission;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class AdmissionPolicyContinuityPostgresTest extends AdmissionPolicyContinuityTest {
    @Container static final PostgreSQLContainer<?> DATABASE = new PostgreSQLContainer<>("postgres:16-alpine");

    @Override @BeforeEach void initialPolicyAndAllocation() { database(); super.initialPolicyAndAllocation(); }

    @Override void database() {
        String schema = "policy_continuity_" + UUID.randomUUID().toString().replace("-", "");
        var admin = new DriverManagerDataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
        new JdbcTemplate(admin).execute("CREATE SCHEMA " + schema);
        dataSource = new DriverManagerDataSource(DATABASE.getJdbcUrl() + (DATABASE.getJdbcUrl().contains("?") ? "&" : "?")
            + "currentSchema=" + schema, DATABASE.getUsername(), DATABASE.getPassword());
        mapper = new ObjectMapper().registerModule(new JavaTimeModule()); json = new AdmissionJson(mapper);
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V165__durable_capacity_admission_and_staging_expiry.sql"),
            new ClassPathResource("db/migration/V178__admission_allocation_authorizations.sql")).execute(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        store = new AdmissionStore(jdbc, json, new DataSourceTransactionManager(dataSource), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @ParameterizedTest @ValueSource(strings={"deployment_target_profiles","deployment_capacity_reservations"})
    void authoritativeProfileAndBindingContentionDoNotDeadlockPublicationBehindApply(String table) throws Exception {
        at(NOW.plusSeconds(31));
        var candidate = continuation(store, json, "policy2", List.of(original), current, NOW.plusSeconds(300));
        jdbc.execute("CREATE TABLE deployment_target_profiles(id VARCHAR(64) PRIMARY KEY)");
        jdbc.update("INSERT INTO deployment_target_profiles VALUES (?)", TARGET);
        var held = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var owner = workers.submit(() -> new TransactionTemplate(new DataSourceTransactionManager(dataSource)).executeWithoutResult(tx -> {
                jdbc.queryForList("SELECT id FROM " + table + " FOR UPDATE"); held.countDown();
                try { if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("lock timeout"); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
            }));
            assertThat(held.await(5, TimeUnit.SECONDS)).isTrue();
            try {
                var attempt = workers.submit(() -> publishFixture(store, jdbc, candidate, current));
                assertThatThrownBy(() -> attempt.get(3, TimeUnit.SECONDS)).hasRootCauseMessage("409 CONFLICT \"POLICY_CONTINUITY_BUSY\"");
            } finally { release.countDown(); }
            owner.get(10, TimeUnit.SECONDS);
        }
        assertThat(store.activePolicy()).isEqualTo(originalPolicy);
        assertThat(store.held()).containsExactly(original);
        publishFixture(store, jdbc, candidate, current);
        assertThat(store.context(original).policy().id()).isEqualTo(candidate.id());
    }
}
