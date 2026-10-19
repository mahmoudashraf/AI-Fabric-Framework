package com.ai.fabric.platform.backend.deployment.admission;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class InitialAdmissionEvidenceRenewalPostgresTest extends InitialAdmissionEvidenceRenewalTest {
    @Container static final PostgreSQLContainer<?> DATABASE = new PostgreSQLContainer<>("postgres:16-alpine");

    @Override @BeforeEach void initialUnusedApply() {
        database();
        super.initialUnusedApply();
    }

    @Override void database() {
        String schema = "initial_proof_" + UUID.randomUUID().toString().replace("-", "");
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

    @ParameterizedTest @ValueSource(strings={"deployment_admission_mutex","deployment_admission_authorizations","deployment_capacity_reservations","platform_consumers","ai_workspace_installations"})
    void reverseOrderContentionReturnsNowaitConflictAndDoesNotHoldTheLedger(String table) throws Exception {
        if (table.equals("platform_consumers") || table.equals("ai_workspace_installations")) configurationOnlyConsumer();
        var held = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var owner = workers.submit(() -> new TransactionTemplate(new DataSourceTransactionManager(dataSource)).executeWithoutResult(tx -> {
                jdbc.queryForList("SELECT * FROM " + table + " FOR UPDATE"); held.countDown();
                try { if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("lock timeout"); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
            }));
            assertThat(held.await(5, TimeUnit.SECONDS)).isTrue();
            try {
                var attempt = workers.submit(() -> renewCurrentFixture());
                assertThatThrownBy(() -> attempt.get(3, TimeUnit.SECONDS)).hasRootCauseMessage("409 CONFLICT \"INITIAL_RENEWAL_BUSY\"");
            } finally { release.countDown(); }
            owner.get(10, TimeUnit.SECONDS);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_admission_reviews WHERE kind='INITIAL_PROOF'", Long.class)).isZero();
        assertThat(renewCurrentFixture().revision()).isEqualTo(1);
    }
}
