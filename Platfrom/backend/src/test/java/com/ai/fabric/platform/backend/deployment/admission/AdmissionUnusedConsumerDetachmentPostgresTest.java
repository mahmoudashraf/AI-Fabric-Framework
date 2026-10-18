package com.ai.fabric.platform.backend.deployment.admission;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static com.ai.fabric.platform.backend.deployment.admission.AdmissionFixture.NOW;
import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class AdmissionUnusedConsumerDetachmentPostgresTest extends AdmissionUnusedConsumerDetachmentTest {
    @Container static final PostgreSQLContainer<?> DATABASE = new PostgreSQLContainer<>("postgres:16-alpine");

    @Override AdmissionUnusedReservationCancellationTest fixture() {
        var result = new AdmissionUnusedReservationCancellationTest() {
            @Override void database() {
                String schema = "detach_" + UUID.randomUUID().toString().replace("-", "");
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

    @ParameterizedTest @ValueSource(strings = {"consumer", "history", "app"})
    void reverseOrderConsumerAndAppLocksAreNonblockingAndNeverReleaseCapacity(String resource) throws Exception {
        String sql = switch (resource) {
            case "consumer" -> "SELECT id FROM platform_consumers WHERE id='consumer' FOR UPDATE";
            case "history" -> "SELECT id FROM platform_consumer_binding_history WHERE id='initial' FOR UPDATE";
            case "app" -> "SELECT id FROM ai_workspace_installations WHERE id='draft-app' FOR UPDATE";
            default -> throw new AssertionError(resource);
        };
        var held = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var holder = workers.submit(() -> new TransactionTemplate(transactions).execute(tx -> {
                fixture.jdbc.queryForList(sql, String.class); held.countDown();
                try { if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("row lock timeout"); }
                catch (InterruptedException error) { throw new IllegalStateException(error); }
                return true;
            }));
            assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();
            try {
                var attempt = workers.submit(() -> { authenticate(com.ai.fabric.platform.backend.security.PlatformRole.PLATFORM_ADMIN); return cancel(); });
                assertThatThrownBy(() -> attempt.get(3, TimeUnit.SECONDS))
                    .hasRootCauseMessage("409 CONFLICT \"UNUSED_CANCELLATION_BINDING_BUSY\"");
                // Failed cancellation relinquishes the mutex as well as every acquired source row lock.
                fixture.store.approveManifest(fixture.admitted.manifest(), "staff", NOW);
            } finally { release.countDown(); }
            holder.get(10, TimeUnit.SECONDS);
        }
        assertUnchanged();
        var receipt = cancel();
        assertThat(receipt.reservation().state()).isEqualTo(State.RELEASED);
        assertThat(cancel()).isEqualTo(receipt);
    }
}
