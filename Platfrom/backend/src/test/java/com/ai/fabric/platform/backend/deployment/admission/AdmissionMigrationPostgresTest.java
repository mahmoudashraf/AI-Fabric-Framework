package com.ai.fabric.platform.backend.deployment.admission;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class AdmissionMigrationPostgresTest {
    @Container static final PostgreSQLContainer<?> DATABASE = new PostgreSQLContainer<>("postgres:16-alpine");
    @Test
    void v165CreatesNoInventedOfferAndDatabaseEnforcesCommandAndCurrentSlotUniqueness() {
        var source = new DriverManagerDataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V165__durable_capacity_admission_and_staging_expiry.sql")).execute(source);
        var jdbc = new JdbcTemplate(source);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_admission_reviews", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT active_policy_ref FROM deployment_admission_mutex WHERE id=1", String.class)).isNull();
        jdbc.update(insert("one", "op", "d|t", "RESERVED"));
        assertThatThrownBy(() -> jdbc.update(insert("two", "op2", "d|t", "RESERVED"))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert("three", "op", "d2|t", "RESERVED"))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        jdbc.update("UPDATE deployment_capacity_reservations SET state='RELEASED',current_slot=NULL WHERE id='one'");
        jdbc.update(insert("four", "op2", "d|t", "RESERVED"));
        assertThatThrownBy(() -> jdbc.update("UPDATE deployment_capacity_reservations SET current_slot=NULL WHERE id='four'"))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    private String insert(String id, String key, String slot, String state) {
        return """
            INSERT INTO deployment_capacity_reservations(id,customer_id,deployment_id,target_profile_id,operation_key,
              request_fingerprint,generation,revision,state,current_slot,expires_at,expiry_state,payload_json,updated_at)
            VALUES ('%s','c','d','t','%s','fp','g',0,'%s','%s',CURRENT_TIMESTAMP,'CURRENT','{}',CURRENT_TIMESTAMP)
            """.formatted(id, key, state, slot);
    }
}
