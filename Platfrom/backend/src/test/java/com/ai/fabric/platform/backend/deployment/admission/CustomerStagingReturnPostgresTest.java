package com.ai.fabric.platform.backend.deployment.admission;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@Testcontainers
class CustomerStagingReturnPostgresTest extends CustomerStagingReturnTest {
    @Container static final PostgreSQLContainer<?> DATABASE = new PostgreSQLContainer<>("postgres:16-alpine");

    @Override @BeforeEach
    void activeTarget() {
        if (store == null) database();
        super.activeTarget();
    }

    @Override
    void database() {
        String schema = "return_" + UUID.randomUUID().toString().replace("-", "");
        var admin = new DriverManagerDataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
        new JdbcTemplate(admin).execute("CREATE SCHEMA " + schema);
        dataSource = new DriverManagerDataSource(DATABASE.getJdbcUrl()
            + (DATABASE.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=" + schema,
            DATABASE.getUsername(), DATABASE.getPassword());
        mapper = new ObjectMapper().registerModule(new JavaTimeModule()); json = new AdmissionJson(mapper);
        org.flywaydb.core.Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(dataSource);
        assertThat(jdbc.queryForObject("SELECT MAX(version::integer) FROM flyway_schema_history WHERE success AND version IS NOT NULL", Integer.class))
            .isEqualTo(208);
        assertThat(jdbc.queryForObject("SELECT character_maximum_length FROM information_schema.columns WHERE table_schema=current_schema() "
            + "AND table_name='deployment_admission_reviews' AND column_name='kind'", Integer.class)).isEqualTo(16);
        store = new AdmissionStore(jdbc, json, new DataSourceTransactionManager(dataSource), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Override
    void seedTargetRows() {
        var now = java.sql.Timestamp.from(NOW);
        jdbc.update("INSERT INTO platform_customers (id,name,slug,status,created_at,updated_at) VALUES ('customer','Customer','return-customer','ACTIVE',?,?)", now, now);
        for (String tenant : java.util.List.of("tenant", "foreign"))
            jdbc.update("INSERT INTO platform_customer_tenants (id,customer_id,name,slug,status,created_at,updated_at) VALUES (?,'customer',?,?,'ACTIVE',?,?)",
                tenant, tenant, tenant, now, now);
        jdbc.update("""
            INSERT INTO platform_deployments (id,name,environment_name,template_id,status,customer_id,tenant_id,active_version_id,created_at,updated_at)
            VALUES ('deployment1','Preview','staging','template','ACTIVE','customer','tenant','version',?,?)
            """, now, now);
        jdbc.update("""
            INSERT INTO platform_deployment_drafts (id,deployment_id,revision_number,status,actions_config_json,entity_config_json,routing_config_json,
                provider_config_json,security_config_json,created_at,updated_at)
            VALUES ('draft','deployment1',1,'PUBLISHED','{}','{}','{}','{}','{}',?,?)
            """, now, now);
        jdbc.update("""
            INSERT INTO platform_deployment_versions (id,deployment_id,source_draft_id,version_label,status,config_hash,reindex_required,actions_config_json,
                entity_config_json,routing_config_json,provider_config_json,security_config_json,actions_artifact_yaml,entity_artifact_yaml,
                routing_artifact_yaml,manifest_json,published_at)
            VALUES ('version','deployment1','draft','v1','PUBLISHED','config-hash',FALSE,'{}','{}','{}','{}','{}','','','','{}',?)
            """, now);
        jdbc.update("""
            INSERT INTO deployment_target_profiles (id,name,provider_type,environment_name,source_strategy,active,created_at,updated_at)
            VALUES (?,'Reviewed staging','COOLIFY','staging','IMMUTABLE_IMAGE',TRUE,?,?)
            """, TARGET, now, now);
        jdbc.update("""
            INSERT INTO platform_deployment_releases (id,deployment_id,target_profile_id,deployment_version_id,status,verification_status,provisioning_status,
                provisioning_target,provisioning_details_json,provider_type,created_at,applied_at,updated_at)
            VALUES ('release','deployment1',?,'version','APPLIED_VERIFIED','PASSED','ACTIVE','CUSTOMER_STAGING','{}','COOLIFY',?,?,?)
            """, TARGET, now, now, now);
        jdbc.update("INSERT INTO deployment_managed_dependency_specs (id,fingerprint,spec_json,reviewed_by,reviewed_at,review_reference,active) VALUES ('spec',?,'{}','staff',?,'return-spec-review',TRUE)",
            "a".repeat(64), now);
        jdbc.update("""
            INSERT INTO deployment_managed_dependency_graphs (id,deployment_id,target_profile_id,customer_id,tenant_id,version_id,release_id,spec_ref,
                spec_fingerprint,binding_fingerprint,status,step,spec_json,state_json,created_at,updated_at)
            VALUES ('graph','deployment1',?,'customer','tenant','version','release','spec',?,'binding-fingerprint','READY','READY','{}','{}',?,?)
            """, TARGET, "a".repeat(64), now, now);
        jdbc.update("INSERT INTO platform_target_retirement_gates (id,deployment_id,target_profile_id,state,epoch,updated_at) VALUES ('gate','deployment1',?,'AVAILABLE',7,?)",
            TARGET, now);
    }

    @Test
    void deploymentWorkerLockDeniesReturnWithoutWaitingOrChangingHolds() throws Exception {
        try (var worker = dataSource.getConnection()) {
            worker.setAutoCommit(false);
            try (var lock = worker.prepareStatement("SELECT id FROM platform_deployments WHERE id=? FOR UPDATE")) {
                lock.setString(1, "deployment1");
                lock.executeQuery().close();
            }
            assertThatThrownBy(() -> service.returnStaging("offer", "deployment1", request(true)))
                .hasMessageContaining("STAGING_RETURN_BUSY");
            assertThat(store.require(allocation.id())).isEqualTo(allocation);
            worker.rollback();
        }
    }
}
