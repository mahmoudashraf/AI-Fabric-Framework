package com.ai.fabric.runtime.lifecycle.retirement;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;

@Component
public class RuntimeRetirementSchema {
    private final DataSource dataSource;

    public RuntimeRetirementSchema(DataSource dataSource) { this.dataSource = dataSource; }

    public boolean supports(String table, String... columns) {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            Set<String> present = new HashSet<>();
            // Schema checks avoid deliberately issuing invalid SQL inside a PostgreSQL transaction.
            try (ResultSet result = connection.getMetaData().getColumns(connection.getCatalog(), connection.getSchema(), "%", "%")) {
                while (result.next()) {
                    if (table.equalsIgnoreCase(result.getString("TABLE_NAME"))) {
                        present.add(result.getString("COLUMN_NAME").toLowerCase(java.util.Locale.ROOT));
                    }
                }
            }
            return !present.isEmpty() && present.containsAll(Set.of(columns));
        } catch (SQLException exception) {
            throw new IllegalStateException("Runtime retirement schema cannot be verified.", exception);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }
}
