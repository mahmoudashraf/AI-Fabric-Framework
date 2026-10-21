package com.ai.fabric.platform.backend.deployment.admission;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class RuntimeArtifactRepairJournalPostgresTest extends RuntimeArtifactRepairJournalTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    @Override @BeforeEach void database() {
        String schema = "repair_" + UUID.randomUUID().toString().replace("-", "");
        var admin = new JdbcTemplate(new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword()));
        admin.execute("CREATE SCHEMA " + schema);
        dataSource = new DriverManagerDataSource(PG.getJdbcUrl() + (PG.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=" + schema, PG.getUsername(), PG.getPassword());
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V165__durable_capacity_admission_and_staging_expiry.sql"),
            new ClassPathResource("db/migration/V178__admission_allocation_authorizations.sql")).execute(dataSource);
        mapper = new ObjectMapper().registerModule(new JavaTimeModule()); json = new AdmissionJson(mapper); jdbc = new JdbcTemplate(dataSource);
        store = new AdmissionStore(jdbc, json, new DataSourceTransactionManager(dataSource), Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
