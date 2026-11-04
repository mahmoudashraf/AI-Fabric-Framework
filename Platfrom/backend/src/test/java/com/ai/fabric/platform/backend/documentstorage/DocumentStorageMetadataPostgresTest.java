package com.ai.fabric.platform.backend.documentstorage;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class DocumentStorageMetadataPostgresTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:16-alpine");
    @Test void sharedSourcesSurviveDeploymentRemovalWhileTargetReceiptsRemainIsolated() {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword())
            .locations("classpath:db/migration").target("230").load().migrate();
        var jdbc=new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword()));
        jdbc.update("INSERT INTO platform_customers(id,name,slug,status,created_at,updated_at) VALUES ('docs-owner','Owner','docs-owner','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO platform_customer_tenants(id,customer_id,name,slug,status,created_at,updated_at) VALUES ('docs-tenant','docs-owner','Tenant','docs-tenant','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO platform_deployments(id,customer_id,tenant_id,name,environment_name,template_id,status,created_at,updated_at) VALUES ('docs-dep','docs-owner','docs-tenant','Docs','staging','custom-start-from-scratch','DRAFT',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("""
            INSERT INTO platform_document_storage_connections(id,customer_id,tenant_id,name,mode,bucket,region,root_prefix,status,operation_key,created_at,updated_at)
            VALUES ('connection','docs-owner','docs-tenant','Guides','LOOMAI_HOSTED_S3','one-shared-bucket','eu-north-1','knowledge/','READY','create',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            """);
        jdbc.update("""
            INSERT INTO platform_document_storage_uploads(id,connection_id,filename,object_key,prefix,size_bytes,content_type,sha256,status,operation_key,created_at,updated_at)
            VALUES ('upload','connection','guide.txt','knowledge/public/upload/guide.txt','public/',50,'text/plain',?,'UPLOADED','upload-one',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            ""","a".repeat(64));
        for(String target:new String[]{"staging","production"}) jdbc.update("""
            INSERT INTO platform_document_storage_selections(id,upload_id,deployment_id,target_profile_id,release_id,dataset_id,public_approved,source_id,status,operation_key,created_at,updated_at)
            VALUES (?, 'upload','docs-dep',?,?,'document-knowledge',true,?,'ACTIVE',?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            ""","selection-"+target,target,"release-"+target,"source-"+target,"index-"+target);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_document_storage_selections",Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT source_id) FROM platform_document_storage_selections",Integer.class)).isEqualTo(2);
        assertThatThrownBy(()->jdbc.update("DELETE FROM platform_document_storage_uploads WHERE id='upload'"))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        jdbc.update("DELETE FROM platform_deployments WHERE id='docs-dep'");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_document_storage_selections",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_document_storage_connections",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_document_storage_uploads",Integer.class)).isEqualTo(1);
    }
}
