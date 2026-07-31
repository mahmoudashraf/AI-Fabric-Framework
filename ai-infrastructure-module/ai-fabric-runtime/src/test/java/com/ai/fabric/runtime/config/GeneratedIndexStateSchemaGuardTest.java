package com.ai.fabric.runtime.config;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GeneratedIndexStateSchemaGuardTest {

    @Test
    void rebuildsOnlyGeneratedStateWhenQueueKeyTypesAreIncompatible() throws Exception {
        DataSource dataSource = dataSource();
        execute(
            dataSource,
            "CREATE TABLE ai_indexing_queue (id VARCHAR(64) PRIMARY KEY, depends_on_work_id BIGINT)",
            "CREATE TABLE ai_indexing_entity_state (state_key VARCHAR(64) PRIMARY KEY)",
            "CREATE TABLE runtime_chat_session (id VARCHAR(64) PRIMARY KEY)"
        );

        new GeneratedIndexStateSchemaGuard(dataSource, true).rebuildIfRequired();

        assertThat(tableExists(dataSource, "AI_INDEXING_QUEUE")).isFalse();
        assertThat(tableExists(dataSource, "AI_INDEXING_ENTITY_STATE")).isFalse();
        assertThat(tableExists(dataSource, "RUNTIME_CHAT_SESSION")).isTrue();
    }

    @Test
    void preservesCompatibleGeneratedState() throws Exception {
        DataSource dataSource = dataSource();
        execute(
            dataSource,
            "CREATE TABLE ai_indexing_queue (id BIGINT PRIMARY KEY, depends_on_work_id BIGINT)",
            "INSERT INTO ai_indexing_queue (id, depends_on_work_id) VALUES (1, NULL)"
        );

        new GeneratedIndexStateSchemaGuard(dataSource, true).rebuildIfRequired();

        assertThat(tableExists(dataSource, "AI_INDEXING_QUEUE")).isTrue();
        assertThat(rowCount(dataSource, "ai_indexing_queue")).isEqualTo(1);
    }

    @Test
    void leavesGeneratedStateUntouchedWhenRebuildIsDisabled() throws Exception {
        DataSource dataSource = dataSource();
        execute(
            dataSource,
            "CREATE TABLE ai_indexing_queue (id VARCHAR(64) PRIMARY KEY, depends_on_work_id BIGINT)"
        );

        new GeneratedIndexStateSchemaGuard(dataSource, false).rebuildIfRequired();

        assertThat(tableExists(dataSource, "AI_INDEXING_QUEUE")).isTrue();
    }

    private DataSource dataSource() {
        return new DriverManagerDataSource(
            "jdbc:h2:mem:index-state-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1",
            "sa",
            ""
        );
    }

    private void execute(DataSource dataSource, String... statements) throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.executeUpdate(sql);
            }
        }
    }

    private boolean tableExists(DataSource dataSource, String tableName) throws Exception {
        try (
            Connection connection = dataSource.getConnection();
            ResultSet tables = connection.getMetaData().getTables(null, null, tableName, new String[]{"TABLE"})
        ) {
            return tables.next();
        }
    }

    private int rowCount(DataSource dataSource, String tableName) throws Exception {
        try (
            Connection connection = dataSource.getConnection();
            Statement statement = connection.createStatement();
            ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM " + tableName)
        ) {
            rows.next();
            return rows.getInt(1);
        }
    }
}
