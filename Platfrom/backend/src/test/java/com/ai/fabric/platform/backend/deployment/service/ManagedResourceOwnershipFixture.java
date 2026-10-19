package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

final class ManagedResourceOwnershipFixture {
    static final ManagedResourceOwnershipService.Scope SCOPE =
        new ManagedResourceOwnershipService.Scope("customer-a","tenant-a","dep-123","target-stage");
    final DriverManagerDataSource dataSource = new DriverManagerDataSource(
        "jdbc:h2:mem:ownership-"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000","sa","");
    final JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    final DataSourceTransactionManager transactions = new DataSourceTransactionManager(dataSource);
    final ManagedResourceOwnershipService ownership = new ManagedResourceOwnershipService(jdbc,new ObjectMapper(),transactions);

    ManagedResourceOwnershipFixture() {
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V197__managed_resource_creation_ownership.sql"))
            .execute(dataSource);
        jdbc.execute("CREATE TABLE platform_deployment_releases(id VARCHAR PRIMARY KEY,deployment_id VARCHAR,target_profile_id VARCHAR,provisioning_details_json TEXT)");
        jdbc.execute("CREATE TABLE platform_deployment_managed_vector_resources(vendor VARCHAR,resource_reference VARCHAR,endpoint VARCHAR,deployment_id VARCHAR,deployment_release_id VARCHAR)");
        jdbc.execute("CREATE TABLE deployment_provider_resource_handles(provider_resource_uuid VARCHAR,status VARCHAR,deployment_id VARCHAR,target_profile_id VARCHAR,resource_kind VARCHAR,fqdn VARCHAR,metadata_json TEXT)");
        release("rel-123",SCOPE,"{}");
    }

    void inject(Object service) { ReflectionTestUtils.setField(service,"ownership",ownership); }

    void release(String id,ManagedResourceOwnershipService.Scope scope,String details) {
        jdbc.update("INSERT INTO platform_deployment_releases VALUES(?,?,?,?)",id,scope.deploymentId(),scope.targetProfileId(),details);
    }

    ManagedResourceOwnershipService.Receipt own(String provider,String kind,String parent,String name,String id,String endpoint) {
        return own(SCOPE,provider,kind,parent,name,id,endpoint);
    }

    ManagedResourceOwnershipService.Receipt own(ManagedResourceOwnershipService.Scope scope,String provider,String kind,
                                               String parent,String name,String id,String endpoint) {
        ownership.create(scope,provider,kind,parent,name,()->id,value->new ManagedResourceOwnershipService.Identity(value,endpoint));
        return ownership.requireOwned(scope,provider,kind,id,endpoint);
    }

    DeploymentEntity deployment() {
        var deployment=new DeploymentEntity();
        deployment.setId(SCOPE.deploymentId()); deployment.setCustomerId(SCOPE.customerId()); deployment.setTenantId(SCOPE.tenantId());
        deployment.setName("Synthetic ownership test"); deployment.setEnvironmentName("stage");
        return deployment;
    }

    TransactionTemplate transaction() { return new TransactionTemplate(transactions); }
}
