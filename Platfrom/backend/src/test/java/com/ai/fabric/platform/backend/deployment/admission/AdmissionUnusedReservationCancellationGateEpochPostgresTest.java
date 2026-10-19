package com.ai.fabric.platform.backend.deployment.admission;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
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

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static com.ai.fabric.platform.backend.deployment.admission.AdmissionFixture.NOW;
import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class AdmissionUnusedReservationCancellationGateEpochPostgresTest extends AdmissionUnusedReservationCancellationGateEpochTest {
    @Container static final PostgreSQLContainer<?> DATABASE = new PostgreSQLContainer<>("postgres:16-alpine");

    @Override AdmissionUnusedReservationCancellationTest fixture() {
        var result = new AdmissionUnusedReservationCancellationTest() {
            @Override void database() {
                String schema = "gate_" + UUID.randomUUID().toString().replace("-", "");
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
        };
        result.database(); result.candidate(); return result;
    }

    @Test void busyConfigurationGateReturnsBoundedConflictWithoutDetachmentOrCapacityRelease() throws Exception {
        gate(7);
        var originalGate = fixture.jdbc.queryForMap("SELECT * FROM platform_target_retirement_gates");
        var held = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var holder = workers.submit(() -> new TransactionTemplate(transactions).execute(tx -> {
                fixture.jdbc.queryForList("SELECT id FROM platform_target_retirement_gates WHERE id='gate' FOR UPDATE", String.class);
                held.countDown();
                try { if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("gate lock timeout"); }
                catch (InterruptedException error) { throw new IllegalStateException(error); }
                return true;
            }));
            assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();
            try {
                var attempt = workers.submit(() -> {
                    authenticate(com.ai.fabric.platform.backend.security.PlatformRole.PLATFORM_ADMIN);
                    try { return cancel(); }
                    finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
                });
                assertThatThrownBy(() -> attempt.get(3, TimeUnit.SECONDS))
                    .hasRootCauseMessage("409 CONFLICT \"UNUSED_CANCELLATION_BINDING_BUSY\"");
                fixture.store.approveManifest(fixture.admitted.manifest(), "staff", NOW);
            } finally { release.countDown(); }
            holder.get(10, TimeUnit.SECONDS);
        }
        assertUnchanged();
        assertThat(fixture.jdbc.queryForMap("SELECT * FROM platform_target_retirement_gates")).isEqualTo(originalGate);
        assertThat(cancel().reservation().state()).isEqualTo(State.RELEASED);
        assertThat(fixture.jdbc.queryForMap("SELECT * FROM platform_target_retirement_gates")).isEqualTo(originalGate);
    }
}
