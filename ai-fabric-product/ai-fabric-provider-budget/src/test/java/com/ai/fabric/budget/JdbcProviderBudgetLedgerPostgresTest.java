package com.ai.fabric.budget;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.util.UUID;

/** Docker is required: an unavailable real PostgreSQL proof fails, rather than silently skipping. */
class JdbcProviderBudgetLedgerPostgresTest extends LedgerContract {
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeAll static void postgres() { POSTGRES.start(); }
    @AfterAll static void cleanup() { POSTGRES.stop(); }

    @Override protected DataSource freshDataSource() throws Exception {
        var source = new PGSimpleDataSource();
        source.setURL(POSTGRES.getJdbcUrl()); source.setUser(POSTGRES.getUsername()); source.setPassword(POSTGRES.getPassword());
        String schema = "budget_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
        }
        source.setCurrentSchema(schema);
        return source;
    }
}
