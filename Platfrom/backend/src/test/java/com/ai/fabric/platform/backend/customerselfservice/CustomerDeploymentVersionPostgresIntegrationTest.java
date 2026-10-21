package com.ai.fabric.platform.backend.customerselfservice;

import com.ai.fabric.platform.backend.aiworkspace.entity.AIWorkspaceInstallationEntity;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceConnectionMode;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceDeliveryMode;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceInstallationStatus;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceInteractionPolicy;
import com.ai.fabric.platform.backend.aiworkspace.repository.AIWorkspaceInstallationRepository;
import com.ai.fabric.platform.backend.aiworkspace.service.AIWorkspaceDeploymentSecurityProjectionService;
import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferEntitlementEntity;
import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferEntity;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerDeploymentVersionContracts.PublishRequest;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerDocumentStorageBindingContracts.BindRequest;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerDeploymentVersionService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerDocumentStorageBindingService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferEntitlementService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerSelfServiceAuthorizationService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerSelfServiceOperationBoundary;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferEntitlementRepository;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentDraftRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.model.UpdateDeploymentDraftRequest;
import com.ai.fabric.platform.backend.deployment.model.DocumentStorageBindingSummary;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentDocumentStorageBindingService;
import com.ai.fabric.platform.backend.marketplace.model.CreateMarketplaceTemplateBootstrapRequest;
import com.ai.fabric.platform.backend.marketplace.model.UpdateDeploymentMarketplaceInstallRequest;
import com.ai.fabric.platform.backend.marketplace.repository.DeploymentMarketplacePluginInstallRepository;
import com.ai.fabric.platform.backend.marketplace.service.DeploymentMarketplaceInstallService;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceTemplateBootstrapService;
import com.ai.fabric.platform.backend.security.PlatformTestSecurity;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.entity.PlatformUserEntity;
import com.ai.fabric.platform.backend.security.repository.PlatformUserRepository;
import com.ai.fabric.platform.backend.tenant.entity.PlatformCustomerEntity;
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import com.ai.fabric.platform.backend.tenant.entity.PlatformTenantEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
    @Autowired CustomerDocumentStorageBindingService storageBindingService;
    @Autowired DeploymentService deploymentService;
    @Autowired MarketplaceTemplateBootstrapService templateBootstrap;
    @Autowired DeploymentMarketplaceInstallService installService;
    @Autowired DeploymentMarketplacePluginInstallRepository installs;
    @Autowired ObjectMapper mapper;
    @Autowired DeploymentRepository deployments;
    @Autowired DeploymentDraftRepository drafts;
    @Autowired DeploymentVersionRepository versions;
    @Autowired PlatformCustomerRepository customers;
    @Autowired PlatformUserRepository users;
    @Autowired CustomerOfferRepository offers;
    @Autowired CustomerOfferEntitlementRepository grants;
    @Autowired PlatformConsumerRepository consumers;
    @Autowired AIWorkspaceInstallationRepository workspaceInstallations;
    @Autowired AIWorkspaceDeploymentSecurityProjectionService workspaceSecurityProjection;
    @Autowired PlatformTenantRepository tenants;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;
    @Autowired CustomerOfferEntitlementService entitlements;
    @MockBean CustomerSelfServiceAuthorizationService authorization;
    @MockBean CustomerSelfServiceOperationBoundary offerBoundary;
    @MockBean DeploymentDocumentStorageBindingService documentBindings;

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
        var entitlement = entitlementFixture(suffix, customerId);
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
        var approved = new Authorization(new AdmissionContext(customerId, tenantId, deploymentId, null,
            "staging-target", TargetIntent.CUSTOMER_STAGING, entitlement.offerId(), "fingerprint", 2, "placement",
            Operation.VERSION_PUBLISH, "reject-publish", null, null), null);
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

        CustomerOfferTestFixtures.authenticate(entitlement.ownerEmail(), PlatformRole.CUSTOMER_ADMIN, "SESSION");
        assertThatThrownBy(() -> service.publish(entitlement.offerId(), deploymentId,
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
            Long.class, entitlement.grantId())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform_audit_events", Long.class)).isEqualTo(auditCount);
    }

    @Test
    void concurrentWorkspaceProjectionCompletesBeforeImmutableVersionPublication() throws Exception {
        ProjectionFixture fixture = projectionFixture();
        var transaction = new TransactionTemplate(transactionManager);
        var draftLocked = new CountDownLatch(1);
        var releaseDraft = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(3)) {
            var holder = executor.submit(() -> transaction.executeWithoutResult(ignored -> {
                drafts.findByIdForUpdate(fixture.draftId()).orElseThrow();
                draftLocked.countDown();
                await(releaseDraft);
            }));
            assertThat(draftLocked.await(10, TimeUnit.SECONDS)).isTrue();
            var projection = executor.submit(() -> workspaceSecurityProjection.synchronizeDeployment(fixture.deploymentId()));
            awaitBlockedQuery("platform_deployment_drafts");
            var publication = executor.submit(() -> transaction.execute(
                ignored -> deploymentService.publishDraftForTrustedCaller(fixture.draftId())));
            awaitBlockedQuery("platform_deployments");
            assertThat(projection.isDone()).isFalse();
            assertThat(publication.isDone()).isFalse();

            releaseDraft.countDown();
            holder.get(20, TimeUnit.SECONDS);
            projection.get(20, TimeUnit.SECONDS);
            var published = publication.get(20, TimeUnit.SECONDS);
            var security = mapper.readTree(versions.findById(published.id()).orElseThrow().getSecurityConfigJson());
            assertThat(security.path("corsAllowedOrigins").asText()).isEqualTo(fixture.origin());
            assertThat(security.path("aiWorkspaceManagedCorsOrigins"))
                .hasToString("[\"" + fixture.origin() + "\"]");
        } finally {
            releaseDraft.countDown();
        }
    }

    @Test
    void publicationThatWinsTheLockRemainsImmutableAndProjectionMovesToTheNextDraft() throws Exception {
        ProjectionFixture fixture = projectionFixture();
        String nextOrigin = "https://next-workspace.example";
        var transaction = new TransactionTemplate(transactionManager);
        var draftLocked = new CountDownLatch(1);
        var releaseDraft = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(3)) {
            var holder = executor.submit(() -> transaction.executeWithoutResult(ignored -> {
                drafts.findByIdForUpdate(fixture.draftId()).orElseThrow();
                draftLocked.countDown();
                await(releaseDraft);
            }));
            assertThat(draftLocked.await(10, TimeUnit.SECONDS)).isTrue();
            var publication = executor.submit(() -> transaction.execute(
                ignored -> deploymentService.publishDraftForTrustedCaller(fixture.draftId())));
            awaitBlockedQuery("platform_deployment_drafts");
            var projection = executor.submit(() -> transaction.executeWithoutResult(ignored -> {
                var consumer = consumers.findById(fixture.consumerEntityId()).orElseThrow();
                workspaceSecurityProjection.lockDeploymentsForConsumers(List.of(consumer));
                var installation = workspaceInstallations.findByCustomerIdOrderByCreatedAtDesc(fixture.customerId()).getFirst();
                installation.setAllowedOriginsJson(mapper.createArrayNode().add(nextOrigin).toString());
                workspaceInstallations.saveAndFlush(installation);
                workspaceSecurityProjection.synchronizeDeployment(fixture.deploymentId());
            }));
            awaitBlockedQuery("platform_deployments");
            assertThat(publication.isDone()).isFalse();
            assertThat(projection.isDone()).isFalse();

            releaseDraft.countDown();
            holder.get(20, TimeUnit.SECONDS);
            var published = publication.get(20, TimeUnit.SECONDS);
            projection.get(20, TimeUnit.SECONDS);
            var publishedSecurity = mapper.readTree(
                versions.findById(published.id()).orElseThrow().getSecurityConfigJson());
            assertThat(publishedSecurity.path("corsAllowedOrigins").asText()).isEqualTo(fixture.origin());
            assertThat(publishedSecurity.path("corsAllowedOrigins").asText()).doesNotContain(nextOrigin);
            String nextDraftId = deployments.findById(fixture.deploymentId()).orElseThrow().getActiveDraftId();
            var nextDraftSecurity = mapper.readTree(drafts.findById(nextDraftId).orElseThrow().getSecurityConfigJson());
            assertThat(nextDraftSecurity.path("corsAllowedOrigins").asText()).isEqualTo(nextOrigin);
        } finally {
            releaseDraft.countDown();
        }
    }

    @Test
    void customerPublicationProjectsReviewedIdentityWithoutAnExtraWorkspaceUpdate() throws Exception {
        ProjectionFixture fixture = projectionFixture();
        var installation = workspaceInstallations.findByCustomerIdOrderByCreatedAtDesc(fixture.customerId()).getFirst();
        installation.setInteractionPolicy(AIWorkspaceInteractionPolicy.PUBLIC_WITH_PROTECTED_CAPABILITIES);
        installation.setAuthProfileCode("supabase");
        installation.setAuthConfigurationJson("""
            {"projectUrl":"https://synthetic.supabase.co","publishableKey":"sb_publishable_synthetic",
             "methods":["email-password"]}
            """);
        workspaceInstallations.saveAndFlush(installation);
        var approved = new Authorization(new AdmissionContext(fixture.customerId(), fixture.tenantId(),
            fixture.deploymentId(), null, "staging-target", TargetIntent.CUSTOMER_STAGING, fixture.offerId(),
            "fingerprint", 2, "placement", Operation.VERSION_PUBLISH, "publish-identity", null, null), null);
        when(authorization.authorize(eq(fixture.customerId()), any())).thenReturn(approved);
        CustomerOfferTestFixtures.authenticate(fixture.ownerEmail(), PlatformRole.CUSTOMER_ADMIN, "SESSION");

        var published = service.publish(fixture.offerId(), fixture.deploymentId(),
            new PublishRequest("staging-target", 2, "publish-identity"));

        var security = mapper.readTree(versions.findById(published.versionId()).orElseThrow().getSecurityConfigJson());
        assertThat(security.path("externalIdentityBrokerEnabled").asBoolean()).isTrue();
        assertThat(security.path("externalIdentityIssuer").asText()).isEqualTo("https://synthetic.supabase.co/auth/v1");
        assertThat(security.path("externalIdentityJwkSetUri").asText())
            .isEqualTo("https://synthetic.supabase.co/auth/v1/.well-known/jwks.json");
        assertThat(security.path("externalIdentityGrantedScopes").asText())
            .contains("chat:query", "chat:conversations");
        assertThat(security.path("corsAllowedOrigins").asText()).contains(fixture.origin());
        assertThat(security.path("aiWorkspaceManagedExternalIdentity").isObject()).isTrue();
    }

    @Test
    void customerPublicationLocksDeploymentBeforeRealEntitlementAndAvoidsWorkspaceOrderDeadlock() throws Exception {
        ProjectionFixture fixture = projectionFixture();
        var approved = new Authorization(new AdmissionContext(fixture.customerId(), fixture.tenantId(),
            fixture.deploymentId(), null, "staging-target", TargetIntent.CUSTOMER_STAGING, fixture.offerId(),
            "fingerprint", 2, "placement", Operation.VERSION_PUBLISH, "publish-lock-order", null, null), null);
        when(authorization.authorize(eq(fixture.customerId()), any())).thenReturn(approved);
        var transaction = new TransactionTemplate(transactionManager);
        var entitlementLocked = new CountDownLatch(1);
        var releaseEntitlement = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(3)) {
            var holder = executor.submit(() -> transaction.executeWithoutResult(ignored -> {
                grants.findByIdForUpdate(fixture.grantId()).orElseThrow();
                entitlementLocked.countDown();
                await(releaseEntitlement);
            }));
            assertThat(entitlementLocked.await(10, TimeUnit.SECONDS)).isTrue();
            var publication = executor.submit(() -> {
                CustomerOfferTestFixtures.authenticate(fixture.ownerEmail(), PlatformRole.CUSTOMER_ADMIN, "SESSION");
                try {
                    return service.publish(fixture.offerId(), fixture.deploymentId(),
                        new PublishRequest("staging-target", 2, "publish-lock-order"));
                } finally {
                    org.springframework.security.core.context.SecurityContextHolder.clearContext();
                }
            });
            awaitBlockedQuery("platform_customer_offer_entitlements");
            var workspaceOrder = executor.submit(() -> transaction.executeWithoutResult(ignored -> {
                var consumer = consumers.findById(fixture.consumerEntityId()).orElseThrow();
                workspaceSecurityProjection.lockDeploymentsForConsumers(List.of(consumer));
                entitlements.requireEntitlement(fixture.customerId(), fixture.offerId(), fixture.ownerId(), true);
            }));
            awaitBlockedQuery("platform_deployments");
            assertThat(publication.isDone()).isFalse();
            assertThat(workspaceOrder.isDone()).isFalse();

            releaseEntitlement.countDown();
            holder.get(20, TimeUnit.SECONDS);
            var published = publication.get(20, TimeUnit.SECONDS);
            workspaceOrder.get(20, TimeUnit.SECONDS);
            assertThat(published.deploymentId()).isEqualTo(fixture.deploymentId());
            assertThat(versions.findById(published.versionId())).isPresent();
        } finally {
            releaseEntitlement.countDown();
        }
    }

    @Test
    void customerStorageBindingLocksDeploymentBeforeRealEntitlementAndAvoidsWorkspaceOrderDeadlock() throws Exception {
        ProjectionFixture fixture = projectionFixture();
        var definition = CustomerOfferTestFixtures.definition();
        var approved = new Authorization(new AdmissionContext(fixture.customerId(), fixture.tenantId(),
            fixture.deploymentId(), null, "staging-target", TargetIntent.CUSTOMER_STAGING, fixture.offerId(),
            "fingerprint", 2, "placement", Operation.BINDING_REPLACE, "bind-lock-order",
            definition.limits(), definition.topology()), definition);
        when(authorization.authorize(eq(fixture.customerId()), any())).thenReturn(approved);
        var binding = new DocumentStorageBindingSummary("binding-lock-order", fixture.deploymentId(), "staging-target",
            DeploymentDocumentStorageBindingService.S3_CONNECTOR, "CONFIGURED", true, mapper.createObjectNode(), Instant.now());
        when(documentBindings.upsert(eq(fixture.deploymentId()), any(), eq("bind-lock-order"),
            eq("document-storage"), eq("reviewed-s3"))).thenReturn(binding);
        var transaction = new TransactionTemplate(transactionManager);
        var entitlementLocked = new CountDownLatch(1);
        var releaseEntitlement = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(3)) {
            var holder = executor.submit(() -> transaction.executeWithoutResult(ignored -> {
                grants.findByIdForUpdate(fixture.grantId()).orElseThrow();
                entitlementLocked.countDown();
                await(releaseEntitlement);
            }));
            assertThat(entitlementLocked.await(10, TimeUnit.SECONDS)).isTrue();
            var bindingOperation = executor.submit(() -> {
                CustomerOfferTestFixtures.authenticate(fixture.ownerEmail(), PlatformRole.CUSTOMER_ADMIN, "SESSION");
                try {
                    return storageBindingService.bind(fixture.offerId(), fixture.deploymentId(), new BindRequest(
                        2, "bind-lock-order", "staging-target", "document-storage", "reviewed-s3",
                        "https://s3.eu-north-1.amazonaws.com", "eu-north-1", "customer-bucket",
                        "dealership/staging/", "access-key", "secret-key", null, false, false));
                } finally {
                    org.springframework.security.core.context.SecurityContextHolder.clearContext();
                }
            });
            awaitBlockedQuery("platform_customer_offer_entitlements");
            var workspaceOrder = executor.submit(() -> transaction.executeWithoutResult(ignored -> {
                var consumer = consumers.findById(fixture.consumerEntityId()).orElseThrow();
                workspaceSecurityProjection.lockDeploymentsForConsumers(List.of(consumer));
                entitlements.requireEntitlement(fixture.customerId(), fixture.offerId(), fixture.ownerId(), true);
            }));
            awaitBlockedQuery("platform_deployments");
            assertThat(bindingOperation.isDone()).isFalse();
            assertThat(workspaceOrder.isDone()).isFalse();

            releaseEntitlement.countDown();
            holder.get(20, TimeUnit.SECONDS);
            var result = bindingOperation.get(20, TimeUnit.SECONDS);
            workspaceOrder.get(20, TimeUnit.SECONDS);
            assertThat(result.status()).isEqualTo("CONFIGURED");
            assertThat(result.binding()).isEqualTo(binding);
        } finally {
            releaseEntitlement.countDown();
        }
    }

    private ProjectionFixture projectionFixture() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String customerId = "customer-lock-" + suffix;
        String tenantId = "tenant-lock-" + suffix;
        String origin = "https://workspace-" + suffix.substring(0, 8) + ".example";
        Instant now = Instant.now();
        var customer = new PlatformCustomerEntity();
        customer.setId(customerId); customer.setName("Projection lock customer"); customer.setSlug(customerId);
        customer.setStatus("ACTIVE"); customer.setCreatedAt(now); customer.setUpdatedAt(now);
        customers.saveAndFlush(customer);
        var entitlement = entitlementFixture(suffix, customerId);
        var tenant = new PlatformTenantEntity();
        tenant.setId(tenantId); tenant.setCustomerId(customerId); tenant.setName("Projection lock tenant");
        tenant.setSlug(tenantId); tenant.setStatus("ACTIVE"); tenant.setCreatedAt(now); tenant.setUpdatedAt(now);
        tenants.saveAndFlush(tenant);
        var created = templateBootstrap.bootstrapConfiguration("mkp-template-simulated-dealership-experience-v1",
            new CreateMarketplaceTemplateBootstrapRequest("1.2.0", "Projection publication lock fixture",
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

        var consumer = new PlatformConsumerEntity();
        consumer.setId("consumer-" + suffix); consumer.setCustomerId(customerId);
        consumer.setConsumerId("workspace-" + suffix); consumer.setDisplayName("Workspace"); consumer.setStatus("ACTIVE");
        consumer.setBoundDeploymentId(deploymentId); consumer.setBindingRevision(1);
        consumer.setCreatedAt(now); consumer.setUpdatedAt(now); consumer.setLastBoundAt(now);
        consumers.saveAndFlush(consumer);
        var installation = new AIWorkspaceInstallationEntity();
        installation.setId("installation-" + suffix); installation.setInstallationId("awi_pub_" + suffix.replace("-", ""));
        installation.setCustomerId(customerId); installation.setConsumerEntityId(consumer.getId());
        installation.setDisplayName("Workspace"); installation.setStatus(AIWorkspaceInstallationStatus.DRAFT);
        installation.setExperiencePackCode("dealership"); installation.setExperiencePackVersion("1.1.0");
        installation.setConnectionMode(AIWorkspaceConnectionMode.PUBLIC_RUNTIME_ANONYMOUS);
        installation.setConnectionProfileCode("runtime-anonymous-direct"); installation.setConnectionProfileVersion("1.0.0");
        installation.setDeliveryMode(AIWorkspaceDeliveryMode.EMBEDDED);
        installation.setInteractionPolicy(AIWorkspaceInteractionPolicy.PUBLIC);
        installation.setAuthProfileCode("none"); installation.setAuthProfileVersion("1.0.0");
        installation.setAuthConfigurationJson("{}"); installation.setConnectionConfigurationJson("{}");
        installation.setAllowedOriginsJson(mapper.writeValueAsString(List.of(origin))); installation.setConfigurationJson("{}");
        installation.setCreatedAt(now); installation.setUpdatedAt(now);
        workspaceInstallations.saveAndFlush(installation);
        return new ProjectionFixture(deploymentId, setupDraft.id(), origin, customerId, tenantId, consumer.getId(),
            entitlement.ownerId(), entitlement.ownerEmail(), entitlement.offerId(), entitlement.grantId());
    }

    private EntitlementFixture entitlementFixture(String suffix, String customerId) {
        Instant now = Instant.now();
        String ownerId = "owner-" + suffix;
        String ownerEmail = suffix + "@test.invalid";
        String offerId = "offer-" + suffix;
        String grantId = "grant-" + suffix;
        var owner = new PlatformUserEntity();
        owner.setId(ownerId); owner.setEmail(ownerEmail); owner.setDisplayName("Customer owner");
        owner.setPasswordHash("test-not-a-real-password"); owner.setRole("CUSTOMER_ADMIN");
        owner.setCustomerId(customerId); owner.setStatus("ACTIVE"); owner.setCreatedAt(now); owner.setUpdatedAt(now);
        users.saveAndFlush(owner);
        var definition = CustomerOfferTestFixtures.definition();
        offers.saveAndFlush(new CustomerOfferEntity(offerId, mapper.valueToTree(definition).toString(),
            new com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferCodec(mapper).hash(definition),
            "test-review", "test-admin", now));
        var grant = new CustomerOfferEntitlementEntity();
        grant.setId(grantId); grant.setCustomerId(customerId); grant.setOfferId(offerId); grant.setOwnerUserId(ownerId);
        grant.setInvitationReference("test-invitation"); grant.setStatus(EntitlementStatus.ACTIVE); grant.setRevision(2);
        grant.setExpiresAt(now.plusSeconds(3600)); grant.setCreatedAt(now); grant.setUpdatedAt(now);
        grants.saveAndFlush(grant);
        return new EntitlementFixture(offerId, ownerId, ownerEmail, grantId);
    }

    private void awaitBlockedQuery(String table) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Integer waiting = jdbc.queryForObject("""
                select count(*) from pg_stat_activity
                where datname = current_database()
                  and pid <> pg_backend_pid()
                  and wait_event_type = 'Lock'
                  and lower(query) like ?
                """, Integer.class, "%" + table.toLowerCase() + "%");
            if (waiting != null && waiting > 0) return;
            try {
                Thread.sleep(25);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Lock observation was interrupted.", error);
            }
        }
        throw new AssertionError("Expected a blocked query for " + table + ".");
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent operation did not start.");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent operation was interrupted.", error);
        }
    }

    private long count(String table, String column, String id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + column + "=?", Long.class, id);
    }

    private record ProjectionFixture(String deploymentId, String draftId, String origin, String customerId,
                                     String tenantId, String consumerEntityId, String ownerId, String ownerEmail,
                                     String offerId, String grantId) { }
    private record EntitlementFixture(String offerId, String ownerId, String ownerEmail, String grantId) { }
}
