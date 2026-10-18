package com.ai.fabric.platform.backend.deployment.budget;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import javax.sql.DataSource;
import java.util.UUID;

class ProviderUsageAllowancePostgresTest extends ProviderUsageAllowanceContract {
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @BeforeAll static void start() { POSTGRES.start(); }
    @AfterAll static void stop() { POSTGRES.stop(); }
    @Override DataSource freshDataSource() throws Exception {
        var source = new PGSimpleDataSource(); source.setURL(POSTGRES.getJdbcUrl());
        source.setUser(POSTGRES.getUsername()); source.setPassword(POSTGRES.getPassword());
        String schema = "allowance_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = source.getConnection(); var statement = connection.createStatement()) { statement.execute("CREATE SCHEMA " + schema); }
        source.setCurrentSchema(schema); return source;
    }
}
