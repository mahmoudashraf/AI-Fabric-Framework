package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferDeploymentEntity;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentDeletionOperationEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.repository.*;
import com.ai.fabric.platform.backend.secret.service.DeploymentProviderSecretOverrideService;
import com.ai.fabric.platform.backend.tenant.service.PlatformCustomerConsumerService;
import com.ai.fabric.platform.backend.vectorization.service.VectorizationDeploymentCleanupService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real migrations, bootstrap JPA repository and transaction; other cleanup collaborators are isolated. */
@Testcontainers(disabledWithoutDocker = true)
class DeploymentDeletionExecutionPostgresTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    private JdbcTemplate jdbc;
    private LocalContainerEntityManagerFactoryBean factory;
    private DeploymentDeletionExecutionService service;
    private CustomerOfferDeploymentRepository associations;
    private final DeploymentRepository deployments = mock(DeploymentRepository.class);
    private final DeploymentDeletionOperationRepository operations = mock(DeploymentDeletionOperationRepository.class);
    private final PlatformCustomerConsumerService consumers = mock(PlatformCustomerConsumerService.class);
    private DeploymentEntity deployment;
    private DeploymentDeletionOperationEntity operation;
    private String customer;
    private String offer;
    private String kit;

    @BeforeAll static void migrate() {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration").target("222").load().migrate();
    }

    @BeforeEach void setUp() {
        var source = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(source);
        factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(source);
        factory.setManagedTypes(PersistenceManagedTypes.of(CustomerOfferDeploymentEntity.class.getName()));
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "none", "hibernate.physical_naming_strategy",
            "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));
        factory.afterPropertiesSet();
        var manager = factory.getObject();
        associations = new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(manager))
            .getRepository(CustomerOfferDeploymentRepository.class);

        customer = unique("customer"); offer = unique("offer");
        jdbc.update("INSERT INTO platform_customers(id,name,slug,status,created_at,updated_at) VALUES (?,'Deletion test',?,'ACTIVE',now(),now())", customer, customer);
        jdbc.update("INSERT INTO platform_customer_offers(id,definition_json,fingerprint,review_reference,reviewed_by,reviewed_at) VALUES (?,'{}',?,'test','test',now())", offer, "a".repeat(64));
        deployment = insertDeployment(true);
        kit = unique("kit");
        jdbc.update("""
            INSERT INTO platform_customer_experience_kits(id,customer_id,kit_code,kit_version,name,deployment_id,consumer_id,
                delivery_mode,offer_id,target_profile_id,operation_key,request_fingerprint,created_at,updated_at)
            VALUES (?,?,'dealership-ai-experience','1.0.0','Deletion kit',?,'unused-test-consumer','standalone',?,
                'dtp-coolify-staging-behavior',?,?,now(),now())
            """, kit, customer, deployment.getId(), offer, unique("operation"), "a".repeat(64));
        operation = new DeploymentDeletionOperationEntity();
        operation.setId(unique("del")); operation.setDeploymentId(deployment.getId()); operation.setStatus("QUEUED");
        operation.setRequestedByActorId("test-admin"); operation.setRequestedByRole("PLATFORM_ADMIN");
        operation.setHardDelete(false);
        when(operations.findById(operation.getId())).thenAnswer(ignored -> Optional.of(operation));
        when(deployments.findByIdForUpdate(deployment.getId())).thenReturn(Optional.of(deployment));
        doAnswer(invocation -> { jdbc.update("DELETE FROM platform_deployments WHERE id=?", deployment.getId()); return null; })
            .when(deployments).delete(deployment);
        var resources = mock(DeploymentManagedVectorResourceRepository.class);
        when(resources.findByDeploymentIdOrderByUpdatedAtDesc(anyString())).thenReturn(List.of());
        service = new DeploymentDeletionExecutionService(operations, deployments, mock(DeploymentReleaseRepository.class),
            mock(DeploymentVersionRepository.class), mock(DeploymentDraftRepository.class), mock(DeploymentVerificationRunRepository.class),
            resources, mock(PublicApiDeploymentRepository.class), mock(DeploymentPromptRevisionRepository.class),
            mock(DeploymentAssignmentService.class), mock(DeploymentInfrastructureCleanupService.class),
            mock(DeploymentTenantScopedVectorRegistryService.class), mock(VectorizationDeploymentCleanupService.class),
            mock(DeploymentProviderSecretOverrideService.class), mock(DeploymentDocumentStorageBindingService.class),
            mock(PlatformAuditService.class), new ObjectMapper().findAndRegisterModules(), new JpaTransactionManager(manager),
            consumers, associations);
    }

    @AfterEach void close() { if (factory != null) factory.destroy(); }

    @Test void removesOnlyTheDeletedDeploymentsBootstrapLinkAndKitMetadata() {
        var other = insertDeployment(true);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM platform_deployments WHERE id=?", deployment.getId()))
            .isInstanceOf(DataIntegrityViolationException.class);

        service.executeDeletion(operation.getId());

        assertThat(operation.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(count("platform_deployments", "id", deployment.getId())).isZero();
        assertThat(count("platform_customer_offer_deployments", "deployment_id", deployment.getId())).isZero();
        assertThat(count("platform_customer_experience_kits", "id", kit)).isZero();
        assertThat(count("platform_deployments", "id", other.getId())).isEqualTo(1);
        assertThat(count("platform_customer_offer_deployments", "deployment_id", other.getId())).isEqualTo(1);
        assertThat(count("platform_customer_offers", "id", offer)).isEqualTo(1);
        assertThat(count("platform_customers", "id", customer)).isEqualTo(1);
    }

    @Test void deletionWithoutATemplateAssociationStillSucceeds() {
        jdbc.update("DELETE FROM platform_customer_offer_deployments WHERE deployment_id=?", deployment.getId());
        service.executeDeletion(operation.getId());
        assertThat(operation.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(count("platform_deployments", "id", deployment.getId())).isZero();
    }

    @Test void aLaterFailureRollsBackBootstrapLinkAndKitDeletionTogether() {
        doAnswer(invocation -> {
            jdbc.update("DELETE FROM platform_deployments WHERE id=?", deployment.getId());
            throw new IllegalStateException("Simulated later transaction failure");
        }).when(deployments).delete(deployment);
        service.executeDeletion(operation.getId());
        assertThat(operation.getStatus()).isEqualTo("FAILED");
        assertThat(count("platform_deployments", "id", deployment.getId())).isEqualTo(1);
        assertThat(count("platform_customer_offer_deployments", "deployment_id", deployment.getId())).isEqualTo(1);
        assertThat(count("platform_customer_experience_kits", "id", kit)).isEqualTo(1);
    }

    @Test void aBoundConsumerStillPreventsAllMetadataDeletion() {
        when(consumers.hasBoundConsumer(deployment.getId())).thenReturn(true);
        service.executeDeletion(operation.getId());
        assertThat(operation.getStatus()).isEqualTo("FAILED");
        verify(deployments, never()).delete(any());
        assertThat(count("platform_customer_offer_deployments", "deployment_id", deployment.getId())).isEqualTo(1);
        assertThat(count("platform_customer_experience_kits", "id", kit)).isEqualTo(1);
    }

    private DeploymentEntity insertDeployment(boolean bootstrap) {
        String id = unique("dep"), tenant = unique("tenant");
        jdbc.update("INSERT INTO platform_customer_tenants(id,customer_id,name,slug,status,created_at,updated_at) VALUES (?,?,'Deletion tenant',?,'ACTIVE',now(),now())", tenant, customer, tenant);
        jdbc.update("""
            INSERT INTO platform_deployments(id,customer_id,tenant_id,name,environment_name,template_id,status,archived_at,created_at,updated_at)
            VALUES (?,?,?,'Deletion fixture','staging','mkp-template-autotrader-dealership-v1','ARCHIVED',now(),now(),now())
            """, id, customer, tenant);
        if (bootstrap) jdbc.update("""
            INSERT INTO platform_customer_offer_deployments(deployment_id,customer_id,tenant_id,offer_id,offer_fingerprint,
                authorization_entitlement_revision,operation_key,created_at,bootstrap_target_profile_id)
            VALUES (?,?,?,?,?,1,?,now(),'dtp-coolify-staging-behavior')
            """, id, customer, tenant, offer, "a".repeat(64), unique("bootstrap"));
        var entity = new DeploymentEntity(); entity.setId(id); entity.setName("Deletion fixture");
        entity.setCustomerId(customer); entity.setTenantId(tenant); entity.setStatus("ARCHIVED"); entity.setArchivedAt(Instant.now());
        return entity;
    }

    private long count(String table, String column, String id) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE " + column + "=?", Long.class, id);
    }

    private static String unique(String prefix) { return prefix + "-" + UUID.randomUUID(); }
}
