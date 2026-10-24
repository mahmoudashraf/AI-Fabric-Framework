package com.ai.fabric.platform.backend.deployment.budget;

import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

class ProviderUsageCoreReleasePostgresTest extends ProviderUsageCoreReleaseTest {
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeAll static void start() { POSTGRES.start(); }
    @AfterAll static void stop() { POSTGRES.stop(); }

    @Override Fixture fixture() {
        return new Fixture() {
            @Override DataSource freshDataSource() {
                var source = new PGSimpleDataSource(); source.setURL(POSTGRES.getJdbcUrl());
                source.setUser(POSTGRES.getUsername()); source.setPassword(POSTGRES.getPassword());
                String schema = "core_funding_" + UUID.randomUUID().toString().replace("-", "");
                try (var connection = source.getConnection(); var statement = connection.createStatement()) {
                    statement.execute("CREATE SCHEMA " + schema);
                } catch (Exception error) { throw new IllegalStateException(error); }
                source.setCurrentSchema(schema); return source;
            }
        };
    }
}
