package com.ai.fabric.platform.backend.secret.service;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import static org.assertj.core.api.Assertions.*;

class PlatformSecretTargetMigrationTest {
    @Test
    void migrationPreservesExistingBindingsAndRequiresManagedDeploymentForAnExactTarget() {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE platform_secrets(name VARCHAR(255) PRIMARY KEY, deployment_id VARCHAR(64), scope_type VARCHAR(64), owner_type VARCHAR(64), managed_by_platform BOOLEAN NOT NULL)");
        jdbc.update("INSERT INTO platform_secrets VALUES ('OPENAI_API_KEY',NULL,'GLOBAL_PLATFORM','PLATFORM',FALSE)");
        jdbc.update("INSERT INTO platform_secrets VALUES ('MANAGED_OWNED','dep-support','DEPLOYMENT_MANAGED','PLATFORM_MANAGED',TRUE)");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V220__managed_secret_target_ownership.sql")).execute(dataSource);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform_secrets WHERE target_profile_id IS NULL", Integer.class)).isEqualTo(2);
        jdbc.update("UPDATE platform_secrets SET target_profile_id='support-stage' WHERE name='MANAGED_OWNED'");
        assertThat(jdbc.queryForObject("SELECT target_profile_id FROM platform_secrets WHERE name='MANAGED_OWNED'", String.class)).isEqualTo("support-stage");
        assertThatThrownBy(() -> jdbc.update("UPDATE platform_secrets SET target_profile_id='support-stage' WHERE name='OPENAI_API_KEY'"))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO platform_secrets VALUES ('INVALID','dep-support',NULL,NULL,TRUE,'support-stage')"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}
