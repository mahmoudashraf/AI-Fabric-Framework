package com.ai.fabric.platform.backend.deployment.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import javax.sql.DataSource;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class CustomerEnvironmentQuotaPostgresTest extends CustomerEnvironmentQuotaTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Override protected DataSource dataSource() {
        return new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    @Test void fullMigrationGivesExistingAndNewCustomersTwoSlotsWithoutDeletingResources() {
        var config = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .schemas("quota_migration").locations("classpath:db/migration");
        config.target("232").load().migrate();
        jdbc.update("INSERT INTO quota_migration.platform_customers(id,name,slug,status,created_at,updated_at) VALUES ('pre-quota','Pre quota','pre-quota','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        config.target("233").load().migrate();
        assertThat(jdbc.queryForObject("SELECT infrastructure_environment_limit FROM quota_migration.platform_customers WHERE id='pre-quota'", Integer.class)).isEqualTo(2);
        jdbc.update("INSERT INTO quota_migration.platform_customers(id,name,slug,status,created_at,updated_at) VALUES ('post-quota','Post quota','post-quota','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        assertThat(jdbc.queryForObject("SELECT infrastructure_environment_limit FROM quota_migration.platform_customers WHERE id='post-quota'", Integer.class)).isEqualTo(2);
        assertThatThrownBy(() -> jdbc.update("UPDATE quota_migration.platform_customers SET infrastructure_environment_limit=-1 WHERE id='post-quota'"))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
