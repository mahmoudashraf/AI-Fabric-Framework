package com.ai.fabric.platform.backend.tenant;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class ConsumerBindingCommandMigrationPostgresTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void preservesExistingConsumerAndHistoryAndEnforcesScopedCommandUniqueness() throws Exception {
        migrateTo("158");
        try (var connection = POSTGRES.createConnection(""); var statement = connection.createStatement()) {
            statement.executeUpdate("""
                insert into platform_consumers (id, customer_id, consumer_id, display_name, status, created_at, updated_at)
                values ('binding-one', 'cust-internal', 'binding-one', 'Binding One', 'ACTIVE', current_timestamp, current_timestamp),
                       ('binding-two', 'cust-internal', 'binding-two', 'Binding Two', 'ACTIVE', current_timestamp, current_timestamp)
                """);
            statement.executeUpdate(historyInsert("historical", "binding-one", null));
        }
        migrateTo("159");
        try (var connection = POSTGRES.createConnection(""); var statement = connection.createStatement()) {
            try (var result = statement.executeQuery("select binding_revision, row_version from platform_consumers where id = 'binding-one'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getLong(1)).isZero();
                assertThat(result.getLong(2)).isZero();
            }
            try (var result = statement.executeQuery("select operation_key, from_binding_revision from platform_consumer_binding_history where id = 'historical'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getObject(1)).isNull();
                assertThat(result.getObject(2)).isNull();
            }
            statement.executeUpdate(historyInsert("command-one", "binding-one", "operation-1"));
            assertThatThrownBy(() -> statement.executeUpdate(historyInsert("duplicate", "binding-one", "operation-1")))
                .isInstanceOf(SQLException.class);
            statement.executeUpdate(historyInsert("command-two", "binding-two", "operation-1"));
        }
    }

    private String historyInsert(String id, String consumer, String operationKey) {
        String column = operationKey == null ? "" : ", operation_key";
        String value = operationKey == null ? "" : ", '" + operationKey + "'";
        return """
            insert into platform_consumer_binding_history (id, consumer_entity_id, consumer_id, customer_id,
                actor_id, actor_role, created_at%s)
            values ('%s', '%s', '%s', 'cust-internal', 'migration', 'PLATFORM_ADMIN', current_timestamp%s)
            """.formatted(column, id, consumer, consumer, value);
    }

    private void migrateTo(String target) {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration").target(MigrationVersion.fromVersion(target)).load().migrate();
    }
}
