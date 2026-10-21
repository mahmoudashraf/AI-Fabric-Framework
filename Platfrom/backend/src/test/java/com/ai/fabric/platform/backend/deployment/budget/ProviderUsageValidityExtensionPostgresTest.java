package com.ai.fabric.platform.backend.deployment.budget;

import javax.sql.DataSource;
import java.util.UUID;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.*;

class ProviderUsageValidityExtensionPostgresTest extends ProviderUsageValidityExtensionContract {
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @BeforeAll static void start() { POSTGRES.start(); }
    @AfterAll static void stop() { POSTGRES.stop(); }
    @Override DataSource freshDataSource() throws Exception {
        var source = new PGSimpleDataSource(); source.setURL(POSTGRES.getJdbcUrl());
        source.setUser(POSTGRES.getUsername()); source.setPassword(POSTGRES.getPassword());
        String schema = "validity_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = source.getConnection(); var statement = connection.createStatement()) { statement.execute("CREATE SCHEMA " + schema); }
        source.setCurrentSchema(schema); return source;
    }

    @Test void extensionActualMigrationMakesReviewsAndAllIntentAckUnknownEventsAppendOnly() {
        var request = request("immutable"); var plan = extensions.plan(request); extensions.execute(request.extensionId(), plan.command());
        for (String table : List.of("provider_usage_validity_reviews", "provider_usage_validity_events")) {
            for (String sql : List.of("UPDATE " + table + " SET payload_json='{}'", "DELETE FROM " + table, "TRUNCATE " + table + " CASCADE"))
                assertThatThrownBy(() -> jdbc.execute(sql)).isInstanceOf(DataIntegrityViolationException.class);
        }
        assertThat(extensions.read(request.extensionId()).status()).isEqualTo(ProviderUsageValidityExtensionContracts.Status.ACKNOWLEDGED);
    }
}
