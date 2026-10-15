package com.ai.fabric.platform.backend.customerselfservice;

import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferEntitlementEntity;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerDeploymentVersionContracts.PublishRequest;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerDeploymentVersionService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferEntitlementService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerSelfServiceAuthorizationService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerSelfServiceOperationBoundary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentDraftRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.model.UpdateDeploymentDraftRequest;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.ai.fabric.platform.backend.marketplace.model.CreateMarketplaceTemplateBootstrapRequest;
import com.ai.fabric.platform.backend.marketplace.model.UpdateDeploymentMarketplaceInstallRequest;
import com.ai.fabric.platform.backend.marketplace.repository.DeploymentMarketplacePluginInstallRepository;
import com.ai.fabric.platform.backend.marketplace.service.DeploymentMarketplaceInstallService;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceTemplateBootstrapService;
import com.ai.fabric.platform.backend.security.PlatformTestSecurity;
import com.ai.fabric.platform.backend.tenant.entity.PlatformCustomerEntity;
import com.ai.fabric.platform.backend.tenant.entity.PlatformTenantEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformCustomerRepository;
import com.ai.fabric.platform.backend.tenant.repository.PlatformTenantRepository;
import jakarta.persistence.EntityManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.REVIEWED_COMPOSITION_CHANGED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/** Real publishing/JPA/audit writes; only the upstream authorization and final offer verdict are stubbed. */
@SpringBootTest(properties = {
    "spring.flyway.locations=classpath:db/migration",
    "spring.flyway.target=latest",
    "platform.bootstrap.sample-enabled=false",
    "platform.bootstrap.ecommerce-demo.enabled=false"
})
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class CustomerDeploymentVersionPostgresIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @Autowired CustomerDeploymentVersionService service;
    @Autowired DeploymentService deploymentService;
    @Autowired MarketplaceTemplateBootstrapService templateBootstrap;
    @Autowired DeploymentMarketplaceInstallService installService;
    @Autowired DeploymentMarketplacePluginInstallRepository installs;
    @Autowired ObjectMapper mapper;
    @Autowired DeploymentRepository deployments;
    @Autowired DeploymentDraftRepository drafts;
    @Autowired DeploymentVersionRepository versions;
    @Autowired PlatformCustomerRepository customers;
    @Autowired PlatformTenantRepository tenants;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;
    @MockBean CustomerOfferEntitlementService entitlements;
    @MockBean CustomerSelfServiceAuthorizationService authorization;
    @MockBean CustomerSelfServiceOperationBoundary offerBoundary;

    @BeforeEach void authenticate() { PlatformTestSecurity.authenticateAsPlatformAdmin(); }
    @AfterEach void clearAuthentication() { PlatformTestSecurity.clearAuthentication(); }

    @Test
    void rejectedReviewedVersionRollsBackCompilerVersionDraftAndAuditWrites() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String customerId = "customer-" + suffix;
        String tenantId = "tenant-" + suffix;
        Instant now = Instant.now();
        var customer = new PlatformCustomerEntity();
        customer.setId(customerId); customer.setName("Publish rollback customer"); customer.setSlug(customerId);
        customer.setStatus("ACTIVE"); customer.setCreatedAt(now); customer.setUpdatedAt(now);
        customers.saveAndFlush(customer);
        var tenant = new PlatformTenantEntity();
        tenant.setId(tenantId); tenant.setCustomerId(customerId); tenant.setName("Publish rollback tenant");
        tenant.setSlug(tenantId); tenant.setStatus("ACTIVE"); tenant.setCreatedAt(now); tenant.setUpdatedAt(now);
        tenants.saveAndFlush(tenant);
        var created = templateBootstrap.bootstrapConfiguration("mkp-template-simulated-dealership-experience-v1",
            new CreateMarketplaceTemplateBootstrapRequest("1.2.0", "Reviewed publish rollback fixture",
                "staging", "custom-start-from-scratch", null, customerId, tenantId));
        String deploymentId = created.id();
        var setupDraft = deploymentService.getActiveDraftForDeployment(deploymentId);
        ObjectNode provider = setupDraft.providerConfig().deepCopy();
        provider.put("vectorStrategy", "milvus").put("vectorProvisioningMode", "EXTERNAL_EXISTING")
            .put("vectorStoragePosture", "DEDICATED").put("milvusHost", "vector.fixture.example")
            .put("milvusPort", 443).put("milvusSecure", true);
        deploymentService.updateDraft(setupDraft.id(), new UpdateDeploymentDraftRequest(
            null, null, null, provider, null, null));
        var knowledge = installs.findByDeploymentIdAndPluginId(deploymentId, "mkp-data-dealership-knowledge-v1")
            .orElseThrow();
        installService.updateInstall(deploymentId, knowledge.getId(), new UpdateDeploymentMarketplaceInstallRequest(
            null, "ENABLED", mapper.createObjectNode().put("documentStorageBindingRef", "slot:document-storage")
                .put("publicContentApprovalConfirmed", true), mapper.createObjectNode()));
        var original = deployments.findById(deploymentId).orElseThrow();
        String originalStatus = original.getStatus();
        String originalDraftId = original.getActiveDraftId();
        var originalDraft = drafts.findById(originalDraftId).orElseThrow();
        String originalDraftStatus = originalDraft.getStatus();
        long draftCount = count("platform_deployment_drafts", "deployment_id", deploymentId);
        long auditCount = jdbc.queryForObject("SELECT COUNT(*) FROM platform_audit_events", Long.class);
        var grant = new CustomerOfferEntitlementEntity(); grant.setId("grant-" + suffix); grant.setRevision(2);
        var approved = new Authorization(new AdmissionContext(customerId, tenantId, deploymentId, null,
            "staging-target", TargetIntent.CUSTOMER_STAGING, "offer", "fingerprint", 2, "placement",
            Operation.VERSION_PUBLISH, "reject-publish", null, null), null);
        when(entitlements.requireCustomerActor()).thenReturn(
            new CustomerOfferEntitlementService.CustomerActor(customerId, "owner"));
        when(entitlements.requireEntitlement(customerId, "offer", "owner", true)).thenReturn(grant);
        when(authorization.authorize(eq(customerId), any())).thenReturn(approved);
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            entityManager.flush();
            assertThat(count("platform_deployment_versions", "deployment_id", deploymentId)).isEqualTo(1);
            assertThat(count("platform_deployment_drafts", "deployment_id", deploymentId)).isEqualTo(draftCount + 1);
            assertThat(jdbc.queryForObject("SELECT status FROM platform_deployment_drafts WHERE id=?", String.class,
                originalDraftId)).isEqualTo("PUBLISHED");
            assertThat(deployments.findById(deploymentId).orElseThrow().getActiveDraftId()).isNotEqualTo(originalDraftId);
            throw CustomerSelfServiceException.deny(REVIEWED_COMPOSITION_CHANGED);
        }).when(offerBoundary).requireVersion(eq(approved), any());

        assertThatThrownBy(() -> service.publish("offer", deploymentId,
            new PublishRequest("staging-target", 2, "reject-publish")))
            .isInstanceOf(CustomerSelfServiceException.class)
            .satisfies(error -> assertThat(((CustomerSelfServiceException) error).code()).isEqualTo(REVIEWED_COMPOSITION_CHANGED));

        assertThat(versions.countByDeploymentId(deploymentId)).isZero();
        assertThat(count("platform_deployment_drafts", "deployment_id", deploymentId)).isEqualTo(draftCount);
        var unchanged = deployments.findById(deploymentId).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(originalStatus);
        assertThat(unchanged.getActiveDraftId()).isEqualTo(originalDraftId);
        assertThat(drafts.findById(originalDraftId).orElseThrow().getStatus()).isEqualTo(originalDraftStatus);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform_customer_entitlement_commands WHERE entitlement_id=?",
            Long.class, grant.getId())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform_audit_events", Long.class)).isEqualTo(auditCount);
    }

    private long count(String table, String column, String id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + column + "=?", Long.class, id);
    }
}
