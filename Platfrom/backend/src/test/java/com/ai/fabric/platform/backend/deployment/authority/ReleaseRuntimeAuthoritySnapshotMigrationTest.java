package com.ai.fabric.platform.backend.deployment.authority;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReleaseRuntimeAuthoritySnapshotMigrationTest {

    @Test
    void productionAndTestMigrationsAddTheSameProtectedReleaseEvidence() throws Exception {
        for (String migration : new String[] {
            "db/migration/V168__release_runtime_authority_snapshot.sql",
            "db/test-migration/V120_16__release_runtime_authority_snapshot_test.sql"
        }) {
            try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:release-authority-" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
                "sa",
                ""
            )) {
                execute(connection, "CREATE TABLE platform_deployment_releases (id VARCHAR(64) PRIMARY KEY)");
                execute(connection, "CREATE TABLE platform_runtime_authority_snapshots (id VARCHAR(64) PRIMARY KEY)");
                ScriptUtils.executeSqlScript(connection, new ClassPathResource(migration));

                assertThat(columns(connection, "platform_deployment_releases"))
                    .contains("runtime_authority_snapshot_id", "runtime_authority_fingerprint");
                execute(connection, "INSERT INTO platform_runtime_authority_snapshots (id) VALUES ('snapshot-1')");
                execute(connection, "INSERT INTO platform_deployment_releases "
                    + "(id,runtime_authority_snapshot_id,runtime_authority_fingerprint) "
                    + "VALUES ('release-1','snapshot-1','fingerprint-1')");
                assertThatThrownBy(() -> execute(connection, "INSERT INTO platform_deployment_releases "
                    + "(id,runtime_authority_snapshot_id,runtime_authority_fingerprint) "
                    + "VALUES ('release-invalid','missing-snapshot','fingerprint-2')"))
                    .isInstanceOf(SQLException.class);
            }
        }
    }

    private Set<String> columns(Connection connection, String table) throws SQLException {
        Set<String> columns = new LinkedHashSet<>();
        try (var result = connection.getMetaData().getColumns(null, null, table, null)) {
            while (result.next()) {
                columns.add(result.getString("COLUMN_NAME"));
            }
        }
        return columns;
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
