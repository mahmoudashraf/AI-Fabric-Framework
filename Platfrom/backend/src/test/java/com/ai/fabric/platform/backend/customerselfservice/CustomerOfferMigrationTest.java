package com.ai.fabric.platform.backend.customerselfservice;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class CustomerOfferMigrationTest {
    @Test void assignedPostgresAndH2MirrorMigrationsEnforceDurableScopesAndUniqueReceipts() throws Exception {
        for (String migration : new String[]{"db/migration/V160__customer_self_service_offer_authorization.sql",
            "db/test-migration/V120_8__customer_self_service_offer_authorization_test.sql"}) {
            try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:r6-" + UUID.randomUUID() + ";MODE=PostgreSQL", "sa", "")) {
                verifyMigration(connection, migration);
            }
        }
    }

    static void verifyMigration(Connection connection, String migration) throws Exception {
        execute(connection, "CREATE TABLE platform_customers (id VARCHAR(64) PRIMARY KEY)");
        execute(connection, "CREATE TABLE platform_users (id VARCHAR(64) PRIMARY KEY)");
        execute(connection, "CREATE TABLE platform_customer_tenants (id VARCHAR(64) PRIMARY KEY)");
        execute(connection, "CREATE TABLE platform_deployments (id VARCHAR(64) PRIMARY KEY)");
        execute(connection, "CREATE TABLE deployment_target_profiles (id VARCHAR(64) PRIMARY KEY)");
        ScriptUtils.executeSqlScript(connection, new ClassPathResource(migration));
        execute(connection, "INSERT INTO platform_customers VALUES ('customer')");
        execute(connection, "INSERT INTO platform_users VALUES ('owner')");
        execute(connection, "INSERT INTO platform_customer_tenants VALUES ('tenant')");
        execute(connection, "INSERT INTO platform_deployments VALUES ('deployment')");
        execute(connection, "INSERT INTO deployment_target_profiles VALUES ('staging-target')");
        try (var insert = connection.prepareStatement("INSERT INTO platform_customer_placement_evidence VALUES (?,?,?,?,?,?,?,?,?)")) {
            insert.setString(1, "placement-proof"); insert.setString(2, "staging-target"); insert.setString(3, "CUSTOMER_STAGING");
            insert.setString(4, "profile-hash"); insert.setString(5, "stage-server"); insert.setString(6, "46.224.145.148");
            insert.setString(7, "destination");
            var now = java.time.Instant.now();
            insert.setTimestamp(8, java.sql.Timestamp.from(now)); insert.setTimestamp(9, java.sql.Timestamp.from(now.plusSeconds(60)));
            insert.executeUpdate();
        }
        assertThatThrownBy(() -> execute(connection, "DELETE FROM deployment_target_profiles WHERE id='staging-target'")).isInstanceOf(SQLException.class);
        execute(connection, "INSERT INTO platform_customer_offers VALUES ('offer','{}','hash','review','admin',CURRENT_TIMESTAMP)");
        execute(connection, "INSERT INTO platform_customer_offer_entitlements VALUES "
            + "('grant','customer','offer','owner','invite','ACTIVE',1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        assertThatThrownBy(() -> execute(connection, "INSERT INTO platform_customer_offer_entitlements VALUES "
            + "('other','customer','offer','owner','invite','ACTIVE',1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)"))
            .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> execute(connection, "INSERT INTO platform_customer_offer_entitlements VALUES "
            + "('foreign','missing-customer','offer','owner','invite','ACTIVE',1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)"))
            .isInstanceOf(SQLException.class);
        execute(connection, "INSERT INTO platform_customer_entitlement_commands VALUES ('command','grant','key','hash','INVITE','admin','{}',CURRENT_TIMESTAMP)");
        assertThatThrownBy(() -> execute(connection, "INSERT INTO platform_customer_entitlement_commands VALUES "
            + "('other-command','grant','key','hash','ACCEPT','owner','{}',CURRENT_TIMESTAMP)"))
            .isInstanceOf(SQLException.class);
        execute(connection, "INSERT INTO platform_customer_offer_deployments VALUES ('deployment','customer','tenant','offer','hash',1,'bootstrap',CURRENT_TIMESTAMP)");
        assertThatThrownBy(() -> execute(connection, "DELETE FROM platform_customer_offers WHERE id='offer'")).isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> execute(connection, "DELETE FROM platform_users WHERE id='owner'")).isInstanceOf(SQLException.class);
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) { statement.execute(sql); }
    }
}
