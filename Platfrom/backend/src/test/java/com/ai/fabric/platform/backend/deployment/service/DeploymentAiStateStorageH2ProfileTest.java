package com.ai.fabric.platform.backend.deployment.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.*;

/** Actual application test-profile boot and Hibernate validation, not a hand-created H2 schema. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:ai-state-generations-profile;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DeploymentAiStateStorageH2ProfileTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Flyway flyway;
    @Autowired private Environment environment;
    @Autowired private DeploymentAiStateStorageBindingService binding;

    @Test void configuredProfileAppliesTheGenerationMirrorAndValidatesTheFullEntitySchema() {
        assertThat(environment.getProperty("spring.flyway.target")).isEqualTo("120.26");
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("120.26");
        assertThat(jdbc.queryForObject("select count(*) from deployment_ai_state_storage_credential_generations", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("""
            select count(*) from information_schema.columns where table_name='platform_deployment_releases'
             and column_name in ('ai_state_storage_snapshot_json','ai_state_storage_fingerprint')
            """, Integer.class)).isEqualTo(2);
        assertThat(AopUtils.isAopProxy(binding)).isTrue();
    }

    @Test void profileGenerationCannotExistWithoutItsImmutableVersionApproval() {
        assertThatThrownBy(() -> jdbc.update("""
            insert into deployment_ai_state_storage_credential_generations
            (binding_id,version_id,generation_id,generation_json,fingerprint,created_at) values (?,?,?,?,?,current_timestamp)
            """, "missing-binding", "missing-version", "unapproved-generation", "{}", "f".repeat(64)))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("select count(*) from deployment_ai_state_storage_credential_generations", Integer.class)).isZero();
    }
}
