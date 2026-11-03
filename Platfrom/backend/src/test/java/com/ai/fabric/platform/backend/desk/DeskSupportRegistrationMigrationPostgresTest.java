package com.ai.fabric.platform.backend.desk;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class DeskSupportRegistrationMigrationPostgresTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test void realMigrationsThroughV228AddNullableObjectReceiptWithoutInventingRegistrationHistory() {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration").target("228").load().migrate();
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        assertThat(jdbc.queryForMap("""
            SELECT data_type, is_nullable, column_default FROM information_schema.columns
            WHERE table_name='platform_desk_bindings' AND column_name='support_registration_json'
            """)).containsEntry("data_type", "text").containsEntry("is_nullable", "YES").containsEntry("column_default", null);
        assertThat(jdbc.queryForObject("""
            SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname='platform_desk_support_registration_object'
            """, String.class)).contains("jsonb_typeof", "object");
    }
}
