package com.ai.fabric.runtime.authoring;

import static org.assertj.core.api.Assertions.*;

import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class ConfigurationAuthoringPostgresBootTest extends ConfigurationAuthoringBootTest {
    @Container static final PostgreSQLContainer<?> STATE = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource static void internalStateSchema(DynamicPropertyRegistry properties) throws Exception {
        // Simulate an existing shared internal state database with unrelated public-schema data.
        try (var connection = DriverManager.getConnection(STATE.getJdbcUrl(), STATE.getUsername(), STATE.getPassword());
             var sql = connection.createStatement()) {
            sql.execute("CREATE TABLE public.existing_service_state (id INTEGER PRIMARY KEY)");
            sql.execute("INSERT INTO public.existing_service_state (id) VALUES (1)");
        }
        properties.add("spring.datasource.url", () -> STATE.getJdbcUrl() + "&currentSchema=configuration_authoring");
        properties.add("spring.datasource.driver-class-name", STATE::getDriverClassName);
        properties.add("spring.datasource.username", STATE::getUsername);
        properties.add("spring.datasource.password", STATE::getPassword);
        properties.add("spring.flyway.schemas", () -> "configuration_authoring");
        properties.add("spring.flyway.default-schema", () -> "configuration_authoring");
    }

    @Test void isolatedInternalSchemaDoesNotCreateBusinessStateInPublicSchema() throws Exception {
        try (var connection = DriverManager.getConnection(STATE.getJdbcUrl(), STATE.getUsername(), STATE.getPassword());
             var sql = connection.createStatement()) {
            try (var rows = sql.executeQuery("SELECT count(*) FROM public.existing_service_state")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(1);
            }
            try (var rows = sql.executeQuery("SELECT count(*) FROM information_schema.tables WHERE table_schema='public'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(1);
            }
            try (var rows = sql.executeQuery("SELECT count(*) FROM configuration_authoring.runtime_provider_budget_binding")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(1);
            }
        }
    }
}
