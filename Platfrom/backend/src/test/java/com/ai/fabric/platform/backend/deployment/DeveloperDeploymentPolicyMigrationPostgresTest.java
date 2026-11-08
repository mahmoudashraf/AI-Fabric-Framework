package com.ai.fabric.platform.backend.deployment;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class DeveloperDeploymentPolicyMigrationPostgresTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test void v238PreservesOrdinaryRowsAndEnforcesTypedPolicyWithoutExpandingTemplatePins() {
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        jdbc.execute("CREATE TABLE platform_deployments(id VARCHAR(64) PRIMARY KEY)");
        jdbc.update("INSERT INTO platform_deployments(id) VALUES ('existing')");
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration").baselineOnMigrate(true).baselineVersion("237").target("238").load().migrate();
        assertThat(jdbc.queryForObject("SELECT environment_policy FROM platform_deployments WHERE id='existing'", String.class))
            .isEqualTo("UNRESTRICTED");
        jdbc.update("INSERT INTO platform_deployments(id,environment_policy) VALUES ('developer','STAGING_ONLY')");
        assertThat(jdbc.queryForObject("SELECT environment_policy FROM platform_deployments WHERE id='developer'", String.class))
            .isEqualTo("STAGING_ONLY");
        assertThatThrownBy(() -> jdbc.update("UPDATE platform_deployments SET environment_policy='unknown' WHERE id='developer'"))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE platform_deployments SET environment_policy=NULL WHERE id='developer'"))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
