package com.ai.infrastructure.connector.rest.persistence;

import com.ai.infrastructure.connector.rest.config.IntegrationPersistenceConfiguration;
import com.ai.infrastructure.connector.rest.config.RestConnectorServiceProperties;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class JdbcIntegrationStateRepositoryTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void bootstrapsRestrictedSchemaAndPersistsOperationalStateAcrossRepositoryRestart() throws Exception {
        RestConnectorServiceProperties properties = properties();
        IntegrationPersistenceConfiguration configuration = new IntegrationPersistenceConfiguration();
        try (HikariDataSource dataSource = configuration.connectorDataSource(properties)) {
            IntegrationStateRepository repository = configuration.integrationStateRepository(
                properties,
                provider(dataSource)
            );
            Instant now = Instant.parse("2026-09-27T01:00:00Z");
            repository.startSync("neutral-source", "run-1", "source-v1");
            repository.applyProjectionChanges(
                "neutral-source",
                "run-1",
                List.of(new IntegrationStateRepository.SourceProjectionRecord(
                    "record-1",
                    "fingerprint-1",
                    "title: Current record",
                    Map.of("title", "Current record", "price", 95),
                    Map.of("sourceVersion", "v1"),
                    now
                )),
                Set.of()
            );
            repository.recordWork("work-1", "neutral-source", "record-1", "UPSERT", "ACCEPTED");
            repository.updateWork("work-1", "COMPLETED", null);
            assertThat(repository.registerEvent(new IntegrationStateRepository.WebhookEvent(
                "neutral-hook", "event-1", "record.changed", null, "resource-fingerprint", "payload-hash",
                "COMPLETED", 1, 0, 0, null, now, now
            ))).isTrue();
            repository.recordEventRejection("neutral-hook", "WEBHOOK_SIGNATURE_INVALID");
            repository.recordEventRejection("neutral-hook", "WEBHOOK_SIGNATURE_INVALID");
            repository.completeSync(
                "neutral-source",
                "run-1",
                "cursor-1",
                new IntegrationStateRepository.SyncCounts(1, 1, 1, 0, 1, 1, 0)
            );

            IntegrationStateRepository restarted = new JdbcIntegrationStateRepository(new JdbcTemplate(dataSource));
            assertThat(restarted.syncState("neutral-source")).get()
                .satisfies(state -> {
                    assertThat(state.status()).isEqualTo("COMPLETED");
                    assertThat(state.cursor()).isEqualTo("cursor-1");
                    assertThat(state.counts().indexedCount()).isEqualTo(1);
                });
            assertThat(restarted.activeRecordIds("neutral-source")).containsExactly("record-1");
            IntegrationStateRepository.ProjectionQueryResult projection = restarted.queryProjection(
                "neutral-source",
                new IntegrationStateRepository.ProjectionQuery(
                    List.of(
                        new IntegrationStateRepository.ProjectionCriterion(
                            List.of("title"),
                            IntegrationStateRepository.ProjectionOperator.EQUALS_IGNORE_CASE,
                            List.of("current record")
                        ),
                        new IntegrationStateRepository.ProjectionCriterion(
                            List.of("price"),
                            IntegrationStateRepository.ProjectionOperator.NUMBER_LESS_THAN_OR_EQUAL,
                            List.of("100")
                        )
                    ),
                    10
                )
            );
            assertThat(projection.totalMatches()).isEqualTo(1);
            assertThat(projection.records()).singleElement().satisfies(record -> {
                assertThat(record.recordId()).isEqualTo("record-1");
                assertThat(record.entity()).containsEntry("title", "Current record");
                assertThat(record.metadata()).containsEntry("sourceVersion", "v1");
            });
            assertThat(restarted.workStates("neutral-source")).singleElement()
                .satisfies(work -> assertThat(work.status()).isEqualTo("COMPLETED"));
            assertThat(restarted.event("neutral-hook", "event-1")).isPresent();
            assertThat(restarted.eventRejectionCount("neutral-hook")).isEqualTo(2);

            restarted.applyProjectionChanges("neutral-source", "run-2", List.of(), Set.of("record-1"));
            assertThat(restarted.activeRecordIds("neutral-source")).isEmpty();
            assertThat(restarted.queryProjection(
                "neutral-source",
                new IntegrationStateRepository.ProjectionQuery(List.of(), 10)
            ).records()).isEmpty();

            createRuntimeOwnedTable();
            assertThatThrownBy(() -> new JdbcTemplate(dataSource).queryForObject(
                "SELECT secret_value FROM public.runtime_owned_secret",
                String.class
            )).isInstanceOf(DataAccessException.class);
        }
    }

    @Test
    void v3RequiresFreshReconciliationForRecordsCreatedBeforeQueryableProjection() {
        String schema = "projection_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .schemas(schema)
            .locations("classpath:db/migration/integration")
            .target(MigrationVersion.fromVersion("2"))
            .load()
            .migrate();
        try {
            JdbcTemplate admin = new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
            ));
            Instant now = Instant.parse("2026-10-04T12:00:00Z");
            admin.update("""
                INSERT INTO %s.integration_sync_state
                    (source_id, status, source_count, normalized_count, indexed_count, deleted_count,
                     accepted_work_count, completed_work_count, failed_work_count, source_version,
                     last_started_at, last_success_at, updated_at)
                VALUES ('stock-source', 'COMPLETED', 1, 1, 1, 0, 1, 1, 0, 'v1', ?, ?, ?)
                """.formatted(schema), Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
            admin.update("""
                INSERT INTO %s.integration_source_record
                    (source_id, record_id, fingerprint, last_seen_run, active, updated_at)
                VALUES ('stock-source', 'stock-1', 'fingerprint-1', 'run-1', TRUE, ?)
                """.formatted(schema), Timestamp.from(now));

            Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema)
                .locations("classpath:db/migration/integration")
                .load()
                .migrate();

            Map<String, Object> state = admin.queryForMap("""
                SELECT status, last_started_at, last_success_at
                  FROM %s.integration_sync_state
                 WHERE source_id = 'stock-source'
                """.formatted(schema));
            assertThat(state.get("status")).isEqualTo("RECONCILIATION_REQUIRED");
            assertThat(state.get("last_started_at")).isNull();
            assertThat(state.get("last_success_at")).isNull();
            assertThat(admin.queryForObject("""
                SELECT entity_data = '{}'::jsonb
                  FROM %s.integration_source_record
                 WHERE source_id = 'stock-source' AND record_id = 'stock-1'
                """.formatted(schema), Boolean.class)).isTrue();
        } finally {
            try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
            ); Statement statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            } catch (Exception ignored) {
            }
        }
    }

    private RestConnectorServiceProperties properties() {
        RestConnectorServiceProperties properties = new RestConnectorServiceProperties();
        RestConnectorServiceProperties.Persistence persistence = properties.getPersistence();
        persistence.setEnabled(true);
        persistence.setJdbcUrl(POSTGRES.getJdbcUrl());
        persistence.setUsername("integration_test_role");
        persistence.setPassword("integration-test-password");
        persistence.setSchema("integration_connector_test");
        persistence.setRoleName("integration_test_role");
        persistence.setBootstrapJdbcUrl(POSTGRES.getJdbcUrl());
        persistence.setBootstrapUsername(POSTGRES.getUsername());
        persistence.setBootstrapPassword(POSTGRES.getPassword());
        return properties;
    }

    private org.springframework.beans.factory.ObjectProvider<DataSource> provider(DataSource dataSource) {
        StaticListableBeanFactory factory = new StaticListableBeanFactory();
        factory.addBean("connectorDataSource", dataSource);
        return factory.getBeanProvider(DataSource.class);
    }

    private void createRuntimeOwnedTable() throws Exception {
        try (var connection = DriverManager.getConnection(
            POSTGRES.getJdbcUrl(),
            POSTGRES.getUsername(),
            POSTGRES.getPassword()
        ); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS public.runtime_owned_secret (secret_value VARCHAR(80))");
        }
    }
}
