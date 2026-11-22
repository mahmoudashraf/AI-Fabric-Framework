package com.ai.fabric.platform.backend.marketplace;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class RichDocumentCataloguePostgresTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:16-alpine");
    @Test void forwardCatalogueMigrationAddsRevisionsWithoutChangingPublishedPinsOrAccounts() {
        var source=new DriverManagerDataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());
        Flyway.configure().dataSource(source).locations("classpath:db/migration").target("241").load().migrate();
        var jdbc=new JdbcTemplate(source);
        var before=jdbc.queryForList("SELECT id,manifest_json,bundle_sha256 FROM platform_marketplace_plugin_versions ORDER BY id");
        var offers=jdbc.queryForList("SELECT id,definition_json,fingerprint FROM platform_customer_offers ORDER BY id");
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        for(var row:before) assertThat(jdbc.queryForMap("SELECT id,manifest_json,bundle_sha256 FROM platform_marketplace_plugin_versions WHERE id=?",row.get("id"))).isEqualTo(row);
        assertThat(jdbc.queryForList("SELECT id,definition_json,fingerprint FROM platform_customer_offers ORDER BY id")).isEqualTo(offers);
        var added=jdbc.queryForList("SELECT manifest_json FROM platform_marketplace_plugin_versions WHERE id LIKE 'mkv-doc243-%' AND plugin_id='mkp-data-document-knowledge-s3'",String.class);
        assertThat(added).singleElement().satisfies(manifest -> assertThat(manifest).contains(".pdf",".docx","initialIndexRequiresConfirmation"));
        assertThat(jdbc.queryForObject("SELECT character_maximum_length FROM information_schema.columns WHERE table_name='platform_authoring_sessions' AND column_name='resource_id'",Integer.class)).isEqualTo(512);
    }
}
