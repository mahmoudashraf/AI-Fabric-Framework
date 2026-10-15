package com.ai.fabric.platform.backend.deployment.admission;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.UUID;

/** Executes the release-allocation joins and V180 evidence isolation assertions on PostgreSQL. */
@Testcontainers(disabledWithoutDocker = true)
class DeploymentAdmissionCustomerPrerequisiteEvidenceProviderPostgresTest
    extends DeploymentAdmissionCustomerPrerequisiteEvidenceProviderTest {

    @Container
    static final PostgreSQLContainer<?> DATABASE = new PostgreSQLContainer<>("postgres:16-alpine");

    @Override
    @BeforeEach
    void bridge() throws Exception {
        database();
        super.bridge();
    }

    @Override
    void database() {
        String schema = "source_evidence_" + UUID.randomUUID().toString().replace("-", "");
        var admin = new DriverManagerDataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
        new JdbcTemplate(admin).execute("CREATE SCHEMA " + schema);
        dataSource = new DriverManagerDataSource(DATABASE.getJdbcUrl()
            + (DATABASE.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=" + schema,
            DATABASE.getUsername(), DATABASE.getPassword());
        mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        json = new AdmissionJson(mapper);
        new ResourceDatabasePopulator(
            new ClassPathResource("db/migration/V165__durable_capacity_admission_and_staging_expiry.sql"),
            new ClassPathResource("db/migration/V178__admission_allocation_authorizations.sql"))
            .execute(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        store = new AdmissionStore(jdbc, json, new DataSourceTransactionManager(dataSource),
            Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
