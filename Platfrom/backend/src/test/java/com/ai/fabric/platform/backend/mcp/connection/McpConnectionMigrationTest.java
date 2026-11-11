package com.ai.fabric.platform.backend.mcp.connection;

import static org.assertj.core.api.Assertions.*;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class McpConnectionMigrationTest {
    @Test void schemaCreatesDurableOwnedRecordsAndCascadesDeploymentCleanup() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:mcp-migration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")) {
            try (var sql = connection.createStatement()) {
                sql.execute("CREATE TABLE platform_deployments(id VARCHAR(64) PRIMARY KEY)");
                sql.execute("CREATE TABLE deployment_target_profiles(id VARCHAR(128) PRIMARY KEY)");
            }
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V241__customer_owned_mcp_connections.sql"));
            try (var sql = connection.createStatement()) {
                sql.execute("INSERT INTO platform_deployments VALUES ('dep-one')");
                sql.execute("INSERT INTO deployment_target_profiles VALUES ('stage')");
                sql.execute("INSERT INTO platform_mcp_connections(id,deployment_id,customer_id,target_profile_id,server_ref,name,endpoint_url,connection_model,transport,auth_config_json,revision,status,auth_status,created_at,updated_at) "
                    + "VALUES ('c','dep-one','customer-one','stage','server','Docs','https://docs.example/mcp','PUBLIC','AUTO','{}',1,'ACTIVE','NOT_REQUIRED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
                sql.execute("INSERT INTO platform_mcp_discovery_snapshots VALUES('s','c',1,CURRENT_TIMESTAMP,'{}','hash')");
                sql.execute("INSERT INTO platform_mcp_import_receipts(id,connection_id,snapshot_id,draft_id,created_at,receipt_json,before_actions_json,after_actions_hash) VALUES('r','c','s','draft',CURRENT_TIMESTAMP,'{}','{}','hash')");
                sql.execute("INSERT INTO platform_mcp_oauth_attempts VALUES('state','c',1,'actor','CUSTOMER_ADMIN','browser','/smart-brains/dep-one/capabilities','sealed',CURRENT_TIMESTAMP,NULL)");
                assertThatThrownBy(() -> sql.execute("UPDATE platform_mcp_connections SET encrypted_tokens='forbidden' WHERE id='c'")).isInstanceOf(java.sql.SQLException.class);
                sql.execute("DELETE FROM platform_deployments WHERE id='dep-one'");
                for (String table : java.util.List.of("platform_mcp_connections", "platform_mcp_discovery_snapshots", "platform_mcp_import_receipts", "platform_mcp_oauth_attempts"))
                    try (var result = sql.executeQuery("SELECT COUNT(*) FROM " + table)) { result.next(); assertThat(result.getInt(1)).isZero(); }
            }
        }
    }
}
