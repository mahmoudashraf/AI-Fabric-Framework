package com.ai.fabric.platform.backend.deployment.operations.lifecycle;

import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

class LifecycleCommandStorePostgresTest extends LifecycleCommandStoreTest {
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    @BeforeAll static void start() { postgres.start(); }
    @AfterAll static void stop() { postgres.stop(); }
    @Override protected DataSource dataSource() {
        String schema = "lifecycle_" + UUID.randomUUID().toString().replace("-", "");
        var bootstrap = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        new JdbcTemplate(bootstrap).execute("CREATE SCHEMA " + schema);
        return new DriverManagerDataSource(postgres.getJdbcUrl() + (postgres.getJdbcUrl().contains("?") ? "&" : "?")
            + "currentSchema=" + schema, postgres.getUsername(), postgres.getPassword());
    }
}
