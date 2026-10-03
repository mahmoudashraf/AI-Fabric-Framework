package com.ai.infrastructure.connector.rest.persistence;

import com.ai.infrastructure.connector.rest.config.IntegrationPersistenceConfiguration;
import com.ai.infrastructure.connector.rest.config.RestConnectorServiceProperties;
import com.zaxxer.hikari.HikariDataSource;
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
import java.time.Instant;

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
            repository.markRecordSeen("neutral-source", "record-1", "fingerprint-1", "run-1");
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
            assertThat(restarted.workStates("neutral-source")).singleElement()
                .satisfies(work -> assertThat(work.status()).isEqualTo("COMPLETED"));
            assertThat(restarted.event("neutral-hook", "event-1")).isPresent();
            assertThat(restarted.eventRejectionCount("neutral-hook")).isEqualTo(2);

            createRuntimeOwnedTable();
            assertThatThrownBy(() -> new JdbcTemplate(dataSource).queryForObject(
                "SELECT secret_value FROM public.runtime_owned_secret",
                String.class
            )).isInstanceOf(DataAccessException.class);
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
