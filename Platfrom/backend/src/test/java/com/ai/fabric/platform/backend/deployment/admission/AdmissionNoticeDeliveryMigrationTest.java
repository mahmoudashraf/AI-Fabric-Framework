package com.ai.fabric.platform.backend.deployment.admission;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AdmissionNoticeDeliveryMigrationTest {
    @Test
    void productionAndTestMigrationsCreateTheSameDurableOutbox() throws Exception {
        for (String migration : new String[] {
            "db/migration/V170__admission_notice_delivery_outbox.sql",
            "db/test-migration/V120_18__admission_notice_delivery_outbox_test.sql"
        }) {
            try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:admission-outbox-" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
                "sa", "")) {
                connection.createStatement().execute("CREATE TABLE deployment_capacity_reservations (id VARCHAR(64) PRIMARY KEY)");
                connection.createStatement().execute("CREATE TABLE deployment_admission_mutex (id INTEGER PRIMARY KEY)");
                connection.createStatement().execute("INSERT INTO deployment_admission_mutex (id) VALUES (1)");
                ScriptUtils.executeSqlScript(connection, new ClassPathResource(migration));
                try (var columns = connection.getMetaData().getColumns(null, null,
                    "deployment_admission_notice_deliveries", null)) {
                    int count = 0;
                    while (columns.next()) count++;
                    assertThat(count).isEqualTo(19);
                }
                try (var columns = connection.getMetaData().getColumns(null, null,
                    "deployment_admission_mutex", "lifecycle_cursor")) {
                    assertThat(columns.next()).isTrue();
                }
            }
        }
    }
}
