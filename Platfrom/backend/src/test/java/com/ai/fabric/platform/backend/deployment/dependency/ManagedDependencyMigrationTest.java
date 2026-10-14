package com.ai.fabric.platform.backend.deployment.dependency;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.DriverManager;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class ManagedDependencyMigrationTest {
    @Test void postgresMigrationAndH2MirrorHaveIdenticalColumns() throws Exception {
        assertThat(columns("db/migration/V162__managed_dependency_lifecycle.sql", "production"))
            .isEqualTo(columns("db/test-migration/V120_10__managed_dependency_lifecycle_test.sql", "test"));
    }
    private Map<String, Integer> columns(String resource, String name) throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:" + name + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(resource));
            Map<String, Integer> columns = new LinkedHashMap<>();
            for (String table : new String[]{"deployment_managed_dependency_specs", "deployment_managed_dependency_graphs"}) {
                try (var result = connection.getMetaData().getColumns(null, null, table, null)) {
                    while (result.next()) columns.put(table + "." + result.getString("COLUMN_NAME"), result.getInt("DATA_TYPE"));
                }
            }
            assertThat(columns).hasSize(26);
            try (var indexes = connection.getMetaData().getIndexInfo(null, null, "deployment_managed_dependency_graphs", true, false)) {
                int uniqueColumns = 0;
                while (indexes.next()) if (indexes.getString("INDEX_NAME").startsWith("uq_dependency_deployment_target")) uniqueColumns++;
                assertThat(uniqueColumns).isEqualTo(2);
            }
            return columns;
        }
    }
}
