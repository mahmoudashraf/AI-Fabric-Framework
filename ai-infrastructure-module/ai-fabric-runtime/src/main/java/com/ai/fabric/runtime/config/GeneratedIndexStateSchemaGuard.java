package com.ai.fabric.runtime.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Set;

@Component
public class GeneratedIndexStateSchemaGuard {

    private static final Logger log = LoggerFactory.getLogger(GeneratedIndexStateSchemaGuard.class);
    private static final String QUEUE_TABLE = "ai_indexing_queue";
    private static final String STATE_TABLE = "ai_indexing_entity_state";
    private static final Set<Integer> NUMERIC_TYPES = Set.of(
        Types.BIGINT,
        Types.INTEGER,
        Types.SMALLINT,
        Types.NUMERIC,
        Types.DECIMAL
    );

    private final DataSource dataSource;
    private final boolean rebuildIncompatibleState;

    public GeneratedIndexStateSchemaGuard(
        DataSource dataSource,
        @Value("${ai.fabric.runtime.indexing.rebuild-incompatible-generated-state:false}")
        boolean rebuildIncompatibleState
    ) {
        this.dataSource = dataSource;
        this.rebuildIncompatibleState = rebuildIncompatibleState;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public void onApplicationReady() {
        rebuildIfRequired();
    }

    void rebuildIfRequired() {
        if (!rebuildIncompatibleState) {
            return;
        }

        try (Connection connection = dataSource.getConnection()) {
            if (!tableExists(connection, QUEUE_TABLE) || queueKeyTypesAreCompatible(connection)) {
                return;
            }

            boolean autoCommit = connection.getAutoCommit();
            if (autoCommit) {
                connection.setAutoCommit(false);
            }
            try (Statement statement = connection.createStatement()) {
                // Both tables contain generated indexing state and are rebuilt from source data.
                statement.executeUpdate("DROP TABLE IF EXISTS " + STATE_TABLE);
                statement.executeUpdate("DROP TABLE IF EXISTS " + QUEUE_TABLE);
                connection.commit();
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            } finally {
                if (autoCommit) {
                    connection.setAutoCommit(true);
                }
            }

            log.warn(
                "Rebuilt incompatible generated indexing state tables; a governed reindex is required to restore index coverage."
            );
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to inspect or rebuild generated indexing state tables.", ex);
        }
    }

    private boolean queueKeyTypesAreCompatible(Connection connection) throws SQLException {
        Integer idType = columnType(connection, QUEUE_TABLE, "id");
        Integer dependencyType = columnType(connection, QUEUE_TABLE, "depends_on_work_id");
        return isNumeric(idType) && isNumeric(dependencyType);
    }

    private boolean isNumeric(Integer jdbcType) {
        return jdbcType != null && NUMERIC_TYPES.contains(jdbcType);
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet tables = metadata.getTables(
            connection.getCatalog(),
            connection.getSchema(),
            tableName,
            new String[]{"TABLE"}
        )) {
            if (tables.next()) {
                return true;
            }
        }
        try (ResultSet tables = metadata.getTables(
            connection.getCatalog(),
            connection.getSchema(),
            tableName.toUpperCase(),
            new String[]{"TABLE"}
        )) {
            return tables.next();
        }
    }

    private Integer columnType(Connection connection, String tableName, String columnName) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        Integer type = findColumnType(metadata, connection, tableName, columnName);
        if (type != null) {
            return type;
        }
        return findColumnType(metadata, connection, tableName.toUpperCase(), columnName.toUpperCase());
    }

    private Integer findColumnType(
        DatabaseMetaData metadata,
        Connection connection,
        String tableName,
        String columnName
    ) throws SQLException {
        try (ResultSet columns = metadata.getColumns(
            connection.getCatalog(),
            connection.getSchema(),
            tableName,
            columnName
        )) {
            return columns.next() ? columns.getInt("DATA_TYPE") : null;
        }
    }
}
