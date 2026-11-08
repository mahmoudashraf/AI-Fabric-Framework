package com.ai.fabric.platform.backend.marketplace.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "spring.flyway.locations=classpath:db/migration", "spring.flyway.target=latest",
    "platform.bootstrap.sample-enabled=false", "platform.auth.bootstrap-admin-enabled=false"
})
@Testcontainers(disabledWithoutDocker = true)
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class CustomerPluginLibraryPostgresIntegrationTest extends CustomerPluginLibraryIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }
    @Autowired JdbcTemplate jdbc;

    @Test void productionMigrationPreservesCatalogueAndExplicitlyLabelsSources() {
        assertThat(jdbc.queryForObject("select visibility from platform_marketplace_plugins where id = 'mkp-data-document-knowledge-s3'", String.class)).isEqualTo("PUBLIC");
        for (String id : java.util.List.of("mkp-data-document-knowledge-s3", "mkp-data-dealership-knowledge-v1", "mkp-data-ai-support-documents")) {
            assertThat(jdbc.queryForObject("select catalogue_family from platform_marketplace_plugins where id = ?", String.class, id)).isEqualTo("DOCUMENT_STORAGE");
        }
        for (String id : java.util.List.of("mkp-data-commerce-catalog", "mkp-data-dealership-knowledge-mounted-demo-v1", "mkp-data-simulated-dealership-stock-v1")) {
            assertThat(jdbc.queryForObject("select catalogue_audience from platform_marketplace_plugins where id = ?", String.class, id)).isEqualTo("DEMO");
        }
        for (String id : java.util.List.of("mkp-data-dealership-external-stock-v1", "mkp-data-autotrader-dealership-stock-v1")) {
            assertThat(jdbc.queryForObject("select catalogue_audience from platform_marketplace_plugins where id = ?", String.class, id)).isEqualTo("CUSTOMER");
            assertThat(jdbc.queryForObject("select adapter_contract from platform_marketplace_plugins where id = ?", String.class, id)).isEqualTo("HTTP_JSON");
        }
        assertThat(jdbc.queryForObject("select max(version::numeric) from flyway_schema_history where success = true and version ~ '^[0-9]+$'", Integer.class)).isEqualTo(237);
    }
}
