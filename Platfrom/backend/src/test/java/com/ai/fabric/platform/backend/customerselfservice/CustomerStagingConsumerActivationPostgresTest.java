package com.ai.fabric.platform.backend.customerselfservice;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerEntitlementCommandEntity;
import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferDeploymentEntity;
import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferEntity;
import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferEntitlementEntity;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Authorization;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.EntitlementStatus;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerStagingConsumerActivationContracts.CreateRequest;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerStagingConsumerActivationContracts.Request;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerEntitlementCommandRepository;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferEntitlementRepository;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferRepository;
import com.ai.fabric.platform.backend.customerselfservice.service.*;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentReleaseSummary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.entity.PlatformUserEntity;
import com.ai.fabric.platform.backend.security.repository.PlatformUserRepository;
import com.ai.fabric.platform.backend.security.service.PlatformCustomerAccessService;
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import com.ai.fabric.platform.backend.tenant.entity.PlatformCustomerEntity;
import com.ai.fabric.platform.backend.tenant.model.CreatePlatformConsumerRequest;
import com.ai.fabric.platform.backend.tenant.model.PlatformConsumerSummary;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
import com.ai.fabric.platform.backend.tenant.repository.PlatformCustomerRepository;
import com.ai.fabric.platform.backend.tenant.service.PlatformCustomerConsumerService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real activation transaction, JPA locks, grant checks and receipts; unrelated admission/provider services are stubbed. */
@Testcontainers(disabledWithoutDocker = true)
class CustomerStagingConsumerActivationPostgresTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String ACTOR = "owner@test.invalid";
    private final CreateRequest request = new CreateRequest("app", "App", "description", "release", 7, "create-staging");
    private final CustomerOfferCodec codec = CustomerOfferTestFixtures.codec();
    private final AtomicInteger creations = new AtomicInteger();
    private final AtomicInteger bindings = new AtomicInteger();
    private LocalContainerEntityManagerFactoryBean factory;
    private EntityManager entityManager;
    private JdbcTemplate jdbc;
    private TransactionTemplate transaction;
    private PlatformConsumerRepository consumers;
    private CustomerOfferEntitlementRepository grants;
    private CustomerEntitlementCommandRepository commands;
    private CustomerOfferEntitlementService entitlements;
    private CustomerOfferDeploymentRepository associations;
    private DeploymentRepository deployments;
    private DeploymentVersionRepository versions;
    private CustomerSelfServiceAuthorizationService authorization;
    private CustomerStagingConsumerActivationService service;
    private DeploymentReleaseEntity release;
    private CountDownLatch consumerCreated;
    private CountDownLatch completeCreation;

    @BeforeEach void setUp() {
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setManagedTypes(PersistenceManagedTypes.of(DeploymentEntity.class.getName(),
            PlatformConsumerEntity.class.getName(), CustomerOfferEntitlementEntity.class.getName(),
            CustomerEntitlementCommandEntity.class.getName()));
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop",
            "hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));
        factory.afterPropertiesSet();
        var entityManagerFactory = factory.getObject();
        entityManager = SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory);
        var repositoryFactory = new JpaRepositoryFactory(entityManager);
        consumers = repositoryFactory.getRepository(PlatformConsumerRepository.class);
        grants = repositoryFactory.getRepository(CustomerOfferEntitlementRepository.class);
        commands = repositoryFactory.getRepository(CustomerEntitlementCommandRepository.class);
        deployments = repositoryFactory.getRepository(DeploymentRepository.class);
        var transactionManager = new JpaTransactionManager(entityManagerFactory);
        transactionManager.setDataSource(dataSource);
        transaction = new TransactionTemplate(transactionManager);

        jdbc.execute("ALTER TABLE platform_customer_entitlement_commands ADD CONSTRAINT ux_customer_entitlement_command UNIQUE (entitlement_id, operation_key)");
        jdbc.execute("ALTER TABLE platform_customer_entitlement_commands ADD CONSTRAINT platform_customer_entitlement_commands_command_type_check CHECK (command_type IN ('INVITE', 'ACCEPT', 'REVOKE'))");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V181__customer_offer_command_types.sql"),
            new ClassPathResource("db/migration/V188__customer_staging_activation_command.sql")).execute(dataSource);

        var customers = mock(PlatformCustomerRepository.class);
        var customer = new PlatformCustomerEntity(); customer.setId("customer"); customer.setStatus("ACTIVE");
        when(customers.findById("customer")).thenReturn(Optional.of(customer));
        var users = mock(PlatformUserRepository.class);
        var owner = new PlatformUserEntity(); owner.setId("owner"); owner.setCustomerId("customer");
        owner.setStatus("ACTIVE"); owner.setRole("CUSTOMER_ADMIN");
        when(users.findById("owner")).thenReturn(Optional.of(owner));
        entitlements = spy(new CustomerOfferEntitlementService(mock(CustomerOfferRepository.class), grants, commands,
            mock(PlatformCustomerAccessService.class), customers, users, codec, mock(CustomerReviewedCompositionService.class),
            mock(CustomerTargetPlacementService.class), mock(DeploymentTargetProfileRepository.class), mock(PlatformAuditService.class)));
        doAnswer(ignored -> {
            jdbc.execute("SET LOCAL lock_timeout = '8s'");
            jdbc.execute("SET LOCAL statement_timeout = '15s'");
            return new CustomerOfferEntitlementService.CustomerActor("customer", "owner");
        }).when(entitlements).requireCustomerActor();
        associations = mock(CustomerOfferDeploymentRepository.class);
        when(associations.findById("deployment")).thenReturn(Optional.of(new CustomerOfferDeploymentEntity(
            "deployment", "customer", "tenant", "offer", "staging", "fingerprint", 7, "bootstrap", Instant.now())));
        var releases = mock(DeploymentReleaseRepository.class);
        release = new DeploymentReleaseEntity(); release.setId("release"); release.setDeploymentId("deployment");
        release.setTargetProfileId("staging"); release.setDeploymentVersionId("version");
        release.setStatus("APPLIED_VERIFIED"); release.setVerificationStatus("PASSED");
        when(releases.findById("release")).thenReturn(Optional.of(release));
        versions = mock(DeploymentVersionRepository.class);
        var version = new DeploymentVersionEntity(); version.setId("version"); version.setDeploymentId("deployment");
        version.setStatus("PUBLISHED");
        when(versions.findById("version")).thenReturn(Optional.of(version));
        authorization = mock(CustomerSelfServiceAuthorizationService.class);
        when(authorization.authorize(eq("customer"), any())).thenReturn(mock(Authorization.class));
        var consumerService = mock(PlatformCustomerConsumerService.class);
        when(consumerService.createConsumer(eq("customer"), any())).thenAnswer(call -> {
            var input = call.getArgument(1, CreatePlatformConsumerRequest.class);
            var consumer = new PlatformConsumerEntity(); consumer.setId("con-" + UUID.randomUUID());
            consumer.setCustomerId("customer"); consumer.setConsumerId(input.consumerId());
            consumer.setDisplayName(input.displayName()); consumer.setDescription(input.description());
            consumer.setStatus("ACTIVE"); consumer.setCreatedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
            consumer.setUpdatedAt(consumer.getCreatedAt());
            entityManager.persist(consumer);
            entityManager.flush();
            creations.incrementAndGet();
            if (consumerCreated != null) {
                consumerCreated.countDown();
                await(completeCreation);
            }
            return summary(consumer);
        });
        when(consumerService.updateBindingForTrustedPromotion(eq("customer"), eq("app"), eq("deployment"),
            nullable(String.class), eq("staging"), anyString(), anyLong(), anyString())).thenAnswer(call -> {
                var consumer = consumers.findBindingForUpdate("customer", "app").orElseThrow();
                assertThat(consumer.getBindingRevision()).isEqualTo(call.getArgument(6, Long.class));
                consumer.setBoundDeploymentId("deployment"); consumer.setBoundReleaseId(call.getArgument(3, String.class));
                consumer.setBoundTargetProfileId("staging"); consumer.setBindingRevision(consumer.getBindingRevision() + 1);
                consumer.setLastBoundAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
                entityManager.flush();
                bindings.incrementAndGet();
                return summary(consumer);
            });
        var target = new CustomerStagingConsumerActivationService(entitlements, consumers, associations, deployments,
            releases, versions, authorization, mock(CustomerSelfServiceOperationBoundary.class), consumerService, commands, codec);
        var proxy = new ProxyFactory(target);
        proxy.addAdvice(new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
        service = (CustomerStagingConsumerActivationService) proxy.getProxy();
        transaction.executeWithoutResult(ignored -> {
            var deployment = new DeploymentEntity(); deployment.setId("deployment"); deployment.setName("Deployment");
            deployment.setCustomerId("customer"); deployment.setTenantId("tenant"); deployment.setEnvironmentName("staging");
            deployment.setTemplateId("template"); deployment.setBehaviorType("CONVERSATIONAL"); deployment.setStatus("ACTIVE");
            deployment.setCreatedAt(Instant.now()); deployment.setUpdatedAt(deployment.getCreatedAt());
            entityManager.persist(deployment);
            var grant = new CustomerOfferEntitlementEntity(); grant.setId("grant"); grant.setCustomerId("customer");
            grant.setOfferId("offer"); grant.setOwnerUserId("owner"); grant.setInvitationReference("invitation");
            grant.setStatus(EntitlementStatus.ACTIVE); grant.setRevision(7); grant.setExpiresAt(Instant.now().plusSeconds(3600));
            grant.setCreatedAt(Instant.now()); grant.setUpdatedAt(grant.getCreatedAt());
            entityManager.persist(grant);
        });
        CustomerOfferTestFixtures.authenticate(ACTOR, PlatformRole.CUSTOMER_ADMIN, "SESSION");
    }

    @AfterEach void tearDown() {
        SecurityContextHolder.clearContext();
        if (factory != null) factory.destroy();
    }

    @Test void applyServicePersistsReceiptAgainstMigratedCommandConstraint() {
        var definition = CustomerOfferTestFixtures.definition();
        var offer = new CustomerOfferEntity("offer", codec.write(definition), "fingerprint", "review", ACTOR, Instant.now());
        doReturn(offer).when(entitlements).requireOffer("offer");
        doReturn(definition).when(entitlements).definition(offer);
        var composition = mock(CustomerReviewedCompositionService.class);
        when(composition.runtimeArtifactId(definition)).thenReturn("runtime-artifact");
        var deploymentService = mock(DeploymentService.class);
        var result = new DeploymentReleaseSummary("apply-release", "deployment", "version", "APPLY_REQUESTED", "PENDING",
            "QUEUED", "coolify", "staging", null, "runtime-artifact", null, "queue_release",
            "Queued", null, null, null, Instant.now(), null, Instant.now());
        when(deploymentService.applyVersionForTrustedCaller("deployment", "version", "staging", "runtime-artifact"))
            .thenReturn(result);
        var apply = new CustomerDeploymentApplyService(entitlements, associations, deployments, versions, authorization,
            mock(CustomerSelfServiceOperationBoundary.class), composition, commands, deploymentService, codec,
            mock(PlatformAuditService.class));

        var applied = transaction.execute(ignored -> apply.apply("offer", "deployment",
            new com.ai.fabric.platform.backend.customerselfservice.model.CustomerDeploymentApplyContracts.Request(
                "version", 7, "apply-staging")));
        assertThat(applied).isEqualTo(result);

        var receipt = commands.findByEntitlementIdAndOperationKey("grant", "apply-staging").orElseThrow();
        assertThat(receipt.getCommandType()).isEqualTo("APPLY_STAGING_VERSION");
        DeploymentReleaseSummary persistedResult = codec.read(receipt.getResultJson(), DeploymentReleaseSummary.class);
        // A nullable JsonNode reads back as NullNode; the persisted contract is the complete JSON payload.
        assertThat(codec.write(persistedResult)).isEqualTo(codec.write(result));
        assertThat(commands.count()).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(strings = {"INVITE", "ACCEPT", "REVOKE", "BOOTSTRAP_CONFIG", "BIND_S3",
        "REVOKE_S3", "PUBLISH_VERSION", "CREATE_STAGING_CONSUMER"})
    void migratedConstraintRetainsExistingCommandTypes(String commandType) {
        String operationKey = "retained-" + commandType;
        transaction.executeWithoutResult(ignored -> commands.saveAndFlush(new CustomerEntitlementCommandEntity(
            "cec-" + UUID.randomUUID(), "grant", operationKey, codec.hash(commandType), commandType, ACTOR,
            codec.write(Map.of("retained", commandType)), Instant.now())));

        var persisted = commands.findByEntitlementIdAndOperationKey("grant", operationKey).orElseThrow();
        assertThat(persisted.getCommandType()).isEqualTo(commandType);
        assertThat(commands.count()).isEqualTo(1);
    }

    @ParameterizedTest @NullSource @ValueSource(strings = {"release"})
    void concurrentResponseLossRetryCommitsOneConsumerOneBindingAndOneReceipt(String releaseId) throws Exception {
        var selectedRequest = requestForRelease(releaseId);
        consumerCreated = new CountDownLatch(1);
        completeCreation = new CountDownLatch(1);
        var retryPid = new AtomicInteger();
        try (var executor = Executors.newFixedThreadPool(2)) {
            try {
                var first = executor.submit(() -> createAndActivate(selectedRequest));
                assertThat(consumerCreated.await(10, TimeUnit.SECONDS)).isTrue();
                var retry = executor.submit(() -> transaction.execute(ignored -> {
                    retryPid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                    return createAndActivate(selectedRequest);
                }));
                awaitBlockedRetry(retryPid, retry);
                assertThat(creations.get()).isEqualTo(1);
                assertThat(commands.count()).isZero();
                completeCreation.countDown();

                assertThat(retry.get(20, TimeUnit.SECONDS)).isEqualTo(first.get(20, TimeUnit.SECONDS));
                assertPersistedOnce(releaseId);
            } finally {
                completeCreation.countDown();
            }
        }
    }

    @Test void failedActivationRollsBackTheCreatedConsumerAndCanRetryTheSameKey() {
        release.setVerificationStatus("FAILED");

        assertThatThrownBy(this::createAndActivate).isInstanceOf(CustomerSelfServiceException.class)
            .hasMessageContaining(VERIFICATION_REQUIRED.name());
        assertThat(consumers.count()).isZero();
        assertThat(commands.count()).isZero();
        assertThat(bindings.get()).isZero();

        release.setVerificationStatus("PASSED");
        createAndActivate();
        assertThat(consumers.count()).isEqualTo(1);
        assertThat(commands.count()).isEqualTo(1);
        assertThat(bindings.get()).isEqualTo(1);
    }

    @Test void configurationBindingCanActivateLaterOnlyAfterReleaseVerification() {
        var configured = createAndActivate(requestForRelease(null));
        assertThat(configured.boundReleaseId()).isNull();
        assertThat(configured.bindingRevision()).isEqualTo(1);
        CustomerOfferTestFixtures.authenticate(ACTOR, PlatformRole.CUSTOMER_ADMIN, "SESSION");
        release.setVerificationStatus("FAILED");
        var activation = new Request("app", "release", 1, 7, "activate-staging");

        assertThatThrownBy(() -> service.activate("offer", "deployment", activation))
            .isInstanceOf(CustomerSelfServiceException.class).hasMessageContaining(VERIFICATION_REQUIRED.name());
        assertThat(consumers.findByConsumerIdIgnoreCase("app").orElseThrow().getBoundReleaseId()).isNull();

        release.setVerificationStatus("PASSED");
        var activated = service.activate("offer", "deployment", activation);

        assertThat(activated.boundReleaseId()).isEqualTo("release");
        assertThat(activated.bindingRevision()).isEqualTo(2);
        assertThat(creations.get()).isEqualTo(1);
        assertThat(bindings.get()).isEqualTo(2);
        assertThat(commands.count()).isEqualTo(1);
        assertThat(codec.read(commands.findByEntitlementIdAndOperationKey("grant", "create-staging").orElseThrow()
            .getResultJson(), PlatformConsumerSummary.class)).isEqualTo(configured);
    }

    @ParameterizedTest @NullSource @ValueSource(strings = {"release"})
    void receiptWriteFailureRollsBackTheCreatedConsumerAndBinding(String releaseId) {
        jdbc.execute("ALTER TABLE platform_customer_entitlement_commands ADD CONSTRAINT reject_activation_receipt CHECK (command_type <> 'CREATE_STAGING_CONSUMER')");

        assertThatThrownBy(() -> createAndActivate(requestForRelease(releaseId))).isInstanceOf(RuntimeException.class)
            .hasMessageContaining("reject_activation_receipt");
        assertThat(bindings.get()).isEqualTo(1);
        assertThat(consumers.count()).isZero();
        assertThat(commands.count()).isZero();
    }

    @ParameterizedTest @NullSource @ValueSource(strings = {"release"})
    void revokedGrantCannotReplayAnAlreadyCommittedReceipt(String releaseId) {
        var selectedRequest = requestForRelease(releaseId);
        createAndActivate(selectedRequest);
        transaction.executeWithoutResult(ignored -> {
            var grant = grants.findForUpdate("customer", "offer").orElseThrow();
            grant.setStatus(EntitlementStatus.REVOKED); grant.setRevision(8);
        });

        assertThatThrownBy(() -> createAndActivate(selectedRequest)).isInstanceOf(CustomerSelfServiceException.class)
            .hasMessageContaining(ENTITLEMENT_REVOKED.name());

        assertPersistedOnce(releaseId);
    }

    @ParameterizedTest @NullSource @ValueSource(strings = {"release"})
    void changedPayloadCannotReuseTheCommittedKey(String releaseId) {
        createAndActivate(requestForRelease(releaseId));
        CustomerOfferTestFixtures.authenticate(ACTOR, PlatformRole.CUSTOMER_ADMIN, "SESSION");

        assertThatThrownBy(() -> service.createAndActivate("offer", "deployment", new CreateRequest("app", "Changed name",
            "description", releaseId, 7, "create-staging"))).isInstanceOf(CustomerSelfServiceException.class)
            .hasMessageContaining(OPERATION_KEY_CONFLICT.name());

        assertPersistedOnce(releaseId);
    }

    private PlatformConsumerSummary createAndActivate() {
        return createAndActivate(request);
    }

    private PlatformConsumerSummary createAndActivate(CreateRequest input) {
        CustomerOfferTestFixtures.authenticate(ACTOR, PlatformRole.CUSTOMER_ADMIN, "SESSION");
        try { return service.createAndActivate("offer", "deployment", input); }
        finally { SecurityContextHolder.clearContext(); }
    }

    private CreateRequest requestForRelease(String releaseId) {
        return new CreateRequest(request.consumerId(), request.displayName(), request.description(), releaseId,
            request.expectedEntitlementRevision(), request.operationKey());
    }

    private PlatformConsumerSummary summary(PlatformConsumerEntity consumer) {
        return new PlatformConsumerSummary(consumer.getConsumerId(), consumer.getCustomerId(), consumer.getDisplayName(),
            consumer.getDescription(), consumer.getStatus(), consumer.getBoundDeploymentId(), null, null, null,
            consumer.getBoundReleaseId(), null, consumer.getBoundTargetProfileId(), consumer.getLastBoundAt(),
            consumer.getCreatedAt(), consumer.getUpdatedAt(), consumer.getBindingRevision());
    }

    private void assertPersistedOnce(String releaseId) {
        assertThat(creations.get()).isEqualTo(1);
        assertThat(bindings.get()).isEqualTo(1);
        assertThat(consumers.count()).isEqualTo(1);
        assertThat(commands.count()).isEqualTo(1);
        var consumer = consumers.findByConsumerIdIgnoreCase("app").orElseThrow();
        assertThat(consumer.getBoundDeploymentId()).isEqualTo("deployment");
        assertThat(consumer.getBoundReleaseId()).isEqualTo(releaseId);
        assertThat(consumer.getBoundTargetProfileId()).isEqualTo("staging");
        assertThat(consumer.getBindingRevision()).isEqualTo(1);
        var receipt = commands.findByEntitlementIdAndOperationKey("grant", "create-staging").orElseThrow();
        assertThat(codec.read(receipt.getResultJson(), PlatformConsumerSummary.class)).isEqualTo(summary(consumer));
    }

    private void awaitBlockedRetry(AtomicInteger pid, Future<?> retry) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (retry.isDone()) {
                retry.get();
                fail("The retry must wait for the first activation transaction.");
            }
            Boolean waiting = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM pg_stat_activity
                WHERE pid = ? AND wait_event_type = 'Lock' AND query LIKE '%platform_deployments%')
                """, Boolean.class, pid.get());
            if (Boolean.TRUE.equals(waiting)) return;
            Thread.sleep(25);
        }
        fail("Expected PostgreSQL to report the retry waiting on the deployment lock.");
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("Activation concurrency latch timed out.");
    }
}
