package com.ai.fabric.runtime.documents;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class DocumentPreparationPostgresTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:16-alpine");
    @Test void forwardMigrationPreservesLegacyDefaultsAndEnforcesEvaluationOwnership() {
        var source=new DriverManagerDataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());
        for(String name:new String[]{"V9__loomai_document_ingestion.sql","V17__document_target_release_ownership.sql","V22__document_storage_scope.sql","V25__document_preparation_evaluation.sql"})
            new ResourceDatabasePopulator(new ClassPathResource("db/runtime-migration/"+name)).execute(source);
        var jdbc=new JdbcTemplate(source);
        jdbc.update("""
            INSERT INTO loomai_document_source(id,logical_source_key,dataset_id,tenant_id,customer_id,deployment_id,target_profile_id,release_id,
              connector_type,object_locator,object_locator_digest,display_name,provider_revision_fingerprint,content_length,source_version,visibility,metadata_json,lifecycle_status,created_at,updated_at)
            VALUES('source','logical','documents','tenant','customer','brain','stage','release','MOUNTED_FOLDER','policy.pdf','digest','Policy','revision',50,1,'tenant','{}','REGISTERED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            """);
        assertThat(jdbc.queryForObject("SELECT preparation_json FROM loomai_document_source WHERE id='source'",String.class)).isEqualTo("{}");
        String insert="INSERT INTO loomai_document_evaluation(id,source_id,tenant_id,customer_id,deployment_id,target_profile_id,release_id,result_json,status,created_at) VALUES(?,'source','tenant',?,'brain','stage','release','{}','COMPLETED',CURRENT_TIMESTAMP)";
        jdbc.update(insert,"valid","customer");
        assertThatThrownBy(() -> jdbc.update(insert,"foreign","other-customer")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM loomai_document_evaluation",Long.class)).isEqualTo(1);
    }
}
