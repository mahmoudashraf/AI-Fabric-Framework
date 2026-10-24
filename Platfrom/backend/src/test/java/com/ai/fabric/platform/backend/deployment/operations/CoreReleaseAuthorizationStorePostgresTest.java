package com.ai.fabric.platform.backend.deployment.operations;

import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

class CoreReleaseAuthorizationStorePostgresTest extends CoreReleaseAuthorizationStoreTest {
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @BeforeAll static void start() { POSTGRES.start(); }
    @AfterAll static void stop() { POSTGRES.stop(); }

    @Override protected DataSource dataSource() {
        var source = new PGSimpleDataSource(); source.setURL(POSTGRES.getJdbcUrl());
        source.setUser(POSTGRES.getUsername()); source.setPassword(POSTGRES.getPassword());
        String schema = "core_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = source.getConnection(); var sql = connection.createStatement()) { sql.execute("CREATE SCHEMA " + schema); }
        catch (Exception error) { throw new IllegalStateException(error); }
        source.setCurrentSchema(schema); return source;
    }
}
