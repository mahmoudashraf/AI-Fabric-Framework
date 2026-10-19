package com.ai.fabric.platform.backend.aiworkspace.service;

import com.ai.fabric.platform.backend.aiworkspace.entity.AIWorkspaceInstallationEntity;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceConnectionMode;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceInstallationSummary;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceReadinessSummary;
import com.ai.fabric.platform.backend.aiworkspace.model.CreateAIWorkspaceInstallationRequest;
import com.ai.fabric.platform.backend.aiworkspace.repository.AIWorkspaceInstallationRepository;
import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.PlatformAIWorkspaceProperties;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Authorization;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerSelfServiceOperationBoundary;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.service.PlatformCustomerAccessService;
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
import com.ai.fabric.platform.backend.tenant.repository.PlatformCustomerRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.FORBIDDEN;

/** Executes the real create path, V189, database serialization and rollback; unrelated registries/providers are stubbed. */
@Testcontainers(disabledWithoutDocker = true)
class AIWorkspaceCreateReceiptPostgresTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private LocalContainerEntityManagerFactoryBean factory;
    private JpaTransactionManager transactions;
    private TransactionTemplate transaction;
    private JdbcTemplate jdbc;
    private AIWorkspaceInstallationRepository installations;
    private PlatformCustomerAccessService access;
    private PlatformConsumerRepository consumers;
    private CustomerSelfServiceOperationBoundary offers;
    private AIWorkspaceDeploymentSecurityProjectionService projection;
    private AIWorkspaceDomainService domains;
    private AIWorkspaceCreateReceiptService receipts;
    private AIWorkspaceInstallationService service;
    private java.util.function.Supplier<AIWorkspaceInstallationService> serviceFactory;
    private PlatformConsumerEntity consumer;

    @BeforeEach void setUp() {
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP TABLE IF EXISTS ai_workspace_create_receipts");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V189__ai_workspace_create_receipts.sql"))
            .execute(dataSource);
        factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setManagedTypes(PersistenceManagedTypes.of(AIWorkspaceInstallationEntity.class.getName()));
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop",
            "hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));
        factory.afterPropertiesSet();
        var entityManager = SharedEntityManagerCreator.createSharedEntityManager(factory.getObject());
        installations = new JpaRepositoryFactory(entityManager).getRepository(AIWorkspaceInstallationRepository.class);
        transactions = new JpaTransactionManager(factory.getObject());
        transactions.setDataSource(dataSource);
        transaction = new TransactionTemplate(transactions);
        receipts = proxy(new AIWorkspaceCreateReceiptService(jdbc, mapper));
        var customers = mock(PlatformCustomerRepository.class);
        when(customers.existsById(anyString())).thenReturn(true);
        access = mock(PlatformCustomerAccessService.class);
        consumers = mock(PlatformConsumerRepository.class);
        consumer = consumer("customer-1", "web");
        var packs = mock(AIWorkspaceExperiencePackRegistry.class);
        var connections = mock(AIWorkspaceConnectionProfileRegistry.class);
        when(connections.resolve(any(), any(), any(), any())).thenReturn(new AIWorkspaceConnectionProfileRegistry.ConnectionProfile(
            "runtime-anonymous-direct", "1.0.0", "Anonymous", AIWorkspaceConnectionMode.PUBLIC_RUNTIME_ANONYMOUS,
            "direct-public-runtime", "loomai-runtime-anonymous-v1", true, null, null, Map.of()));
        var validator = new AIWorkspaceConfigurationValidator(new PlatformAIWorkspaceProperties(Path.of("."), false,
            120, Duration.ofSeconds(60), null, null, "apps.loomai.test", "https://workspace.loomai.test"));
        domains = mock(AIWorkspaceDomainService.class);
        when(domains.synchronize(any(), any(), any(), any(), any())).thenAnswer(call -> call.getArgument(4));
        when(domains.summaries(any())).thenReturn(List.of());
        when(domains.normalizeSlug(anyString())).thenAnswer(call -> call.getArgument(0));
        when(domains.defaultBaseDomain()).thenReturn("apps.loomai.test");
        var readiness = mock(AIWorkspaceReadinessService.class);
        when(readiness.evaluateLocal(any())).thenAnswer(call -> {
            var app = call.getArgument(0, AIWorkspaceInstallationEntity.class);
            return new AIWorkspaceReadinessSummary(false, app.getInstallationId(), app.getConnectionMode().manifestValue(),
                "web", null, null, null, null, List.of());
        });
        offers = mock(CustomerSelfServiceOperationBoundary.class);
        when(offers.requireConsumer(any(), any(), any(), any())).thenReturn(new Authorization(null, null));
        when(offers.profileId(anyString(), any())).thenAnswer(call -> call.getArgument(0) + ":profile");
        projection = mock(AIWorkspaceDeploymentSecurityProjectionService.class);
        serviceFactory = () -> newService(customers, packs, connections, validator, readiness);
        service = serviceFactory.get();
        authenticate("owner@example.test", PlatformRole.CUSTOMER_ADMIN);
    }

    private AIWorkspaceInstallationService newService(PlatformCustomerRepository customers,
        AIWorkspaceExperiencePackRegistry packs, AIWorkspaceConnectionProfileRegistry connections,
        AIWorkspaceConfigurationValidator validator, AIWorkspaceReadinessService readiness) {
        return proxy(new AIWorkspaceInstallationService(installations, customers, consumers, access, packs, connections,
            new AIWorkspaceAuthenticationProfileRegistry(mapper), validator, domains, readiness,
            mock(AIWorkspaceAssetCatalogService.class), mock(PlatformAuditService.class), mapper, offers, projection, receipts));
    }

    @AfterEach void tearDown() {
        SecurityContextHolder.clearContext();
        if (factory != null) factory.destroy();
    }

    @ParameterizedTest @ValueSource(strings = {"embedded", "standalone"})
    void replaysTheWholeOriginalResultAcrossServiceRecreationWithoutSideEffects(String delivery) {
        var request = request("web", delivery);
        var original = service.create("customer-1", request, "lost-response");
        // A fresh receipt service reads the durable response, not any in-memory result.
        receipts = proxy(new AIWorkspaceCreateReceiptService(jdbc, mapper));
        service = serviceFactory.get();
        assertThat(mapper.<JsonNode>valueToTree(service.create("customer-1", request, "lost-response")))
            .isEqualTo(mapper.valueToTree(original));
        assertThat(installations.count()).isEqualTo(1);
        assertThat(receiptCount()).isEqualTo(1);
        verify(domains, times(1)).synchronize(any(), any(), any(), any(), any());
        verify(projection, times(1)).synchronizeForConsumer(consumer);
        verify(offers, times(2)).requireConsumer(eq(consumer), eq(Operation.APP_CONFIGURE), eq("lost-response"), any());
    }

    @Test void completedCreateReplaysAfterItsFormerSlugHasBeenReusedWithoutMutatingEitherApp() {
        var request = request("web", "standalone");
        var original = service.create("customer-1", request, "original-create");
        transaction.executeWithoutResult(ignored -> {
            var renamed = installations.findById(original.id()).orElseThrow();
            renamed.setAppSlug("renamed-support");
            installations.saveAndFlush(renamed);
        });
        var replacement = service.create("customer-1", request, "new-create");
        assertThat(replacement.installationId()).isNotEqualTo(original.installationId());
        clearInvocations(domains, projection, offers);
        assertThat(mapper.<com.fasterxml.jackson.databind.JsonNode>valueToTree(service.create("customer-1", request, "original-create")))
            .isEqualTo(mapper.valueToTree(original));
        assertThat(installations.count()).isEqualTo(2);
        assertThat(installations.findByAppSlugIgnoreCase("support").orElseThrow().getInstallationId())
            .isEqualTo(replacement.installationId());
        assertThat(installations.findByAppSlugIgnoreCase("renamed-support").orElseThrow().getInstallationId())
            .isEqualTo(original.installationId());
        verify(domains, never()).synchronize(any(), any(), any(), any(), any());
        verify(projection, never()).synchronizeForConsumer(any());
        verify(offers).requireConsumer(eq(consumer), eq(Operation.APP_CONFIGURE), eq("original-create"), any());
    }

    @ParameterizedTest @ValueSource(strings = {"consumerId", "displayName", "experiencePackCode", "experiencePackVersion",
        "connectionMode", "connectionProfileCode", "connectionProfileVersion", "connectionConfiguration", "allowedOrigins",
        "configuration", "deliveryMode", "appSlug", "customDomains", "interactionPolicy", "authProfileCode",
        "authProfileVersion", "authConfiguration"})
    void sameKeyRejectsEveryChangedRequestField(String field) {
        var request = request("web", "embedded");
        service.create("customer-1", request, "exact-command");
        ObjectNode changed = mapper.valueToTree(request);
        if (changed.get(field).isNull()) changed.put(field, "changed"); else changed.putNull(field);
        var other = mapper.convertValue(changed, CreateAIWorkspaceInstallationRequest.class);
        assertThatThrownBy(() -> service.create("customer-1", other, "exact-command"))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("different request or actor");
        assertThat(installations.count()).isEqualTo(1);
        assertThat(receiptCount()).isEqualTo(1);
    }

    @Test void canonicalObjectOrderReplaysButArrayOrderIsStillPartOfTheCommand() {
        var request = request("web", "embedded");
        ((ObjectNode) request.configuration()).putObject("options").put("z", 1).put("a", 2);
        ((ObjectNode) request.configuration()).putArray("choices").add("a").add("z");
        var original = service.create("customer-1", request, "canonical");
        ((ObjectNode) request.configuration()).putObject("options").put("a", 2).put("z", 1);
        assertThat(mapper.<JsonNode>valueToTree(service.create("customer-1", request, "canonical")))
            .isEqualTo(mapper.valueToTree(original));
        ((ObjectNode) request.configuration()).putArray("choices").add("z").add("a");
        assertThatThrownBy(() -> service.create("customer-1", request, "canonical"))
            .hasMessageContaining("different request or actor");
    }

    @Test void keyIsActorBoundAndCustomerScoped() {
        var request = request("web", "embedded");
        service.create("customer-1", request, "actor-bound");
        authenticate("another@example.test", PlatformRole.CUSTOMER_ADMIN);
        assertThatThrownBy(() -> service.create("customer-1", request, "actor-bound"))
            .hasMessageContaining("different request or actor");
        authenticate("owner@example.test", PlatformRole.PLATFORM_ADMIN);
        assertThatThrownBy(() -> service.create("customer-1", request, "actor-bound"))
            .hasMessageContaining("different request or actor");
        authenticate("owner@example.test", PlatformRole.CUSTOMER_ADMIN);
        consumer("customer-2", "web");
        var otherCustomer = service.create("customer-2", request, "actor-bound");
        assertThat(otherCustomer.customerId()).isEqualTo("customer-2");
        assertThat(installations.count()).isEqualTo(2);
        assertThat(receiptCount()).isEqualTo(2);
    }

    @ParameterizedTest @EnumSource(value = Operation.class, names = {"APP_CONFIGURE", "AUTH_CONFIGURE", "DOMAIN_CONFIGURE"})
    void currentEntitlementAndSettingsAreReauthorizedBeforeEveryReplay(Operation denied) {
        var request = request("web", "embedded");
        service.create("customer-1", request, "revoked");
        doThrow(new ResponseStatusException(FORBIDDEN, "Current entitlement denied"))
            .when(offers).requireConsumer(eq(consumer), eq(denied), eq("revoked"), any());
        assertThatThrownBy(() -> service.create("customer-1", request, "revoked"))
            .hasMessageContaining("Current entitlement denied");
        assertThat(installations.count()).isEqualTo(1);
        assertThat(receiptCount()).isEqualTo(1);
    }

    @Test void currentCustomerAccessAndConsumerOwnershipAreCheckedBeforeReturningAReceipt() {
        var request = request("web", "embedded");
        service.create("customer-1", request, "owner-check");
        doThrow(new ResponseStatusException(FORBIDDEN, "Owner revoked")).when(access).requireCustomerManagementAccess("customer-1");
        assertThatThrownBy(() -> service.create("customer-1", request, "owner-check")).hasMessageContaining("Owner revoked");
        reset(access);
        consumer.setCustomerId("customer-2");
        assertThatThrownBy(() -> service.create("customer-1", request, "owner-check")).hasMessageContaining("consumer is missing");
        consumer.setCustomerId("customer-1");
        consumer.setStatus("DISABLED");
        assertThatThrownBy(() -> service.create("customer-1", request, "owner-check")).hasMessageContaining("Consumer must be active");
        assertThat(installations.count()).isEqualTo(1);
    }

    @Test void removedOrMovedOriginalInstallationDoesNotGetRecreated() {
        var request = request("web", "embedded");
        var original = service.create("customer-1", request, "tombstone");
        transaction.executeWithoutResult(ignored -> {
            var app = installations.findById(original.id()).orElseThrow();
            app.setConsumerEntityId("other-consumer");
            installations.flush();
        });
        assertThatThrownBy(() -> service.create("customer-1", request, "tombstone")).hasMessageContaining("consumer has changed");
        transaction.executeWithoutResult(ignored -> installations.deleteAll());
        assertThatThrownBy(() -> service.create("customer-1", request, "tombstone")).hasMessageContaining("installation not found");
        assertThat(installations.count()).isZero();
        assertThat(receiptCount()).isEqualTo(1);
    }

    @Test void failedCreateAndFailedOuterCommitLeaveNeitherInstallationNorReceipt() {
        var request = request("web", "embedded");
        doThrow(new IllegalStateException("Projection failed")).when(projection).synchronizeForConsumer(consumer);
        assertThatThrownBy(() -> service.create("customer-1", request, "retry-failure")).hasMessageContaining("Projection failed");
        assertThat(installations.count()).isZero();
        assertThat(receiptCount()).isZero();
        doNothing().when(projection).synchronizeForConsumer(consumer);
        assertThatThrownBy(() -> transaction.executeWithoutResult(ignored -> {
            service.create("customer-1", request, "retry-failure");
            assertThat(receiptCount()).isEqualTo(1);
            throw new IllegalStateException("Outer transaction failed");
        })).hasMessageContaining("Outer transaction failed");
        assertThat(installations.count()).isZero();
        assertThat(receiptCount()).isZero();
        service.create("customer-1", request, "retry-failure");
        assertThat(installations.count()).isEqualTo(1);
        assertThat(receiptCount()).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(strings = {"same", "different-consumer", "first-rolls-back"})
    void concurrentCreatesSerializeOnTheWholeKey(String scenario) throws Exception {
        consumer("customer-1", "other-web");
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        doAnswer(ignored -> {
            if (calls.incrementAndGet() == 1) {
                entered.countDown();
                assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
                if (scenario.equals("first-rolls-back")) throw new IllegalStateException("First create failed");
            }
            return null;
        }).when(projection).synchronizeForConsumer(any());
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> createAsOwner(request("web", "embedded"), "concurrent"));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            var attempted = new CountDownLatch(1);
            var second = executor.submit(() -> {
                attempted.countDown();
                return createAsOwner(request(scenario.equals("different-consumer") ? "other-web" : "web", "embedded"), "concurrent");
            });
            assertThat(attempted.await(10, TimeUnit.SECONDS)).isTrue();
            // Observe PostgreSQL actually waiting on the uncommitted unique key, not just unscheduled Java work.
            awaitDatabaseWait();
            assertThat(second.isDone()).isFalse();
            release.countDown();
            if (scenario.equals("first-rolls-back")) {
                assertThatThrownBy(() -> first.get(10, TimeUnit.SECONDS)).hasRootCauseMessage("First create failed");
                assertThat(second.get(10, TimeUnit.SECONDS).installationId()).startsWith("awi_pub_");
            } else if (scenario.equals("different-consumer")) {
                first.get(10, TimeUnit.SECONDS);
                assertThatThrownBy(() -> second.get(10, TimeUnit.SECONDS)).hasRootCauseMessage(
                    "409 CONFLICT \"AI Workspace create Idempotency-Key was already used for a different request or actor.\"");
            } else {
                assertThat(mapper.<JsonNode>valueToTree(first.get(10, TimeUnit.SECONDS)))
                    .isEqualTo(mapper.valueToTree(second.get(10, TimeUnit.SECONDS)));
            }
        } finally { release.countDown(); }
        assertThat(installations.count()).isEqualTo(1);
        assertThat(receiptCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_workspace_create_receipts WHERE completed_at IS NOT NULL", Long.class))
            .isEqualTo(1);
    }

    @Test void unkeyedStaffCallsAreUnchangedAndDistinctKeysRemainDistinctCreates() {
        var request = request("web", "embedded");
        authenticate("operator@example.test", PlatformRole.PLATFORM_ADMIN);
        service.create("customer-1", request);
        service.create("customer-1", request);
        assertThat(receiptCount()).isZero();
        service.create("customer-1", request, "one");
        service.create("customer-1", request, "two");
        assertThat(receiptCount()).isEqualTo(2);
        assertThat(installations.count()).isEqualTo(4);
    }

    @ParameterizedTest @ValueSource(strings = {"", " ", " leading", "trailing ", "line\nbreak"})
    void malformedKeysNeverCreateAnything(String key) {
        assertThatThrownBy(() -> service.create("customer-1", request("web", "embedded"), key))
            .hasMessageContaining("valid AI Workspace create Idempotency-Key");
        assertThat(installations.count()).isZero();
        assertThat(receiptCount()).isZero();
    }

    @Test void oversizedKeyNeverCreatesAnything() {
        assertThatThrownBy(() -> service.create("customer-1", request("web", "embedded"), "k".repeat(129)))
            .hasMessageContaining("valid AI Workspace create Idempotency-Key");
        assertThat(installations.count()).isZero();
        assertThat(receiptCount()).isZero();
    }

    @Test void receiptRequiresTheOwningTransactionAndDatabaseRejectsDuplicateKeys() {
        assertThatThrownBy(() -> receipts.begin("customer-1", request("web", "embedded"), "key"))
            .isInstanceOf(IllegalTransactionStateException.class);
        service.create("customer-1", request("web", "embedded"), "key");
        assertThatThrownBy(() -> jdbc.update("""
            INSERT INTO ai_workspace_create_receipts(customer_id, operation_key, actor_id, request_fingerprint, created_at)
            VALUES ('customer-1', 'key', 'another', 'changed', CURRENT_TIMESTAMP)
            """)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(receiptCount()).isEqualTo(1);
    }

    @Test void anUnexpectedCommittedIncompleteReceiptFailsClosedInsteadOfCreatingAgain() {
        var request = request("web", "embedded");
        transaction.executeWithoutResult(ignored -> receipts.begin("customer-1", request, "incomplete"));
        assertThatThrownBy(() -> service.create("customer-1", request, "incomplete"))
            .hasMessageContaining("receipt requires reconciliation");
        assertThat(installations.count()).isZero();
        assertThat(receiptCount()).isEqualTo(1);
    }

    private AIWorkspaceInstallationSummary createAsOwner(CreateAIWorkspaceInstallationRequest request, String key) {
        authenticate("owner@example.test", PlatformRole.CUSTOMER_ADMIN);
        try { return service.create("customer-1", request, key); }
        finally { SecurityContextHolder.clearContext(); }
    }

    private void awaitDatabaseWait() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (System.nanoTime() < deadline) {
            if (jdbc.queryForObject("""
                SELECT count(*) FROM pg_stat_activity WHERE datname = current_database() AND wait_event_type = 'Lock'
                AND query LIKE '%INSERT INTO ai_workspace_create_receipts%'
                """, Long.class) > 0) return;
            Thread.sleep(20);
        }
        fail("The concurrent create did not reach the database receipt lock.");
    }

    private long receiptCount() {
        return jdbc.queryForObject("SELECT count(*) FROM ai_workspace_create_receipts", Long.class);
    }

    private CreateAIWorkspaceInstallationRequest request(String consumerId, String delivery) {
        return new CreateAIWorkspaceInstallationRequest(consumerId, "Support", "generic", "1.0.0",
            "public-runtime-anonymous", "runtime-anonymous-direct", "1.0.0", mapper.createObjectNode(),
            List.of("https://customer.example"), mapper.createObjectNode(), delivery,
            delivery.equals("standalone") ? "support" : null, List.of(), "public", "none", "1.0.0", mapper.createObjectNode());
    }

    private PlatformConsumerEntity consumer(String customerId, String consumerId) {
        var value = new PlatformConsumerEntity(); value.setId(customerId + "-" + consumerId);
        value.setCustomerId(customerId); value.setConsumerId(consumerId); value.setStatus("ACTIVE");
        value.setBoundDeploymentId("deployment-" + customerId);
        when(consumers.findBindingForUpdate(customerId, consumerId)).thenReturn(Optional.of(value));
        when(consumers.findById(value.getId())).thenReturn(Optional.of(value));
        return value;
    }

    private void authenticate(String actor, PlatformRole role) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
            new PlatformPrincipal(actor, role, actor, "SESSION"), "unused", List.of()));
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(T target) {
        var proxy = new ProxyFactory(target);
        proxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        return (T) proxy.getProxy();
    }
}
