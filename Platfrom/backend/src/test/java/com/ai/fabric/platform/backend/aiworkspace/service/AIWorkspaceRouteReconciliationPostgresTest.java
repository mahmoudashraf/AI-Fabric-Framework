package com.ai.fabric.platform.backend.aiworkspace.service;

import com.ai.fabric.platform.backend.aiworkspace.entity.AIWorkspaceDomainEntity;
import com.ai.fabric.platform.backend.aiworkspace.entity.AIWorkspaceInstallationEntity;
import com.ai.fabric.platform.backend.aiworkspace.model.*;
import com.ai.fabric.platform.backend.aiworkspace.repository.AIWorkspaceDomainRepository;
import com.ai.fabric.platform.backend.aiworkspace.repository.AIWorkspaceInstallationRepository;
import com.ai.fabric.platform.backend.aiworkspace.web.PlatformAIWorkspaceController;
import com.ai.fabric.platform.backend.aiworkspace.web.AIWorkspaceRouteController;
import com.ai.fabric.platform.backend.audit.entity.PlatformAuditEventEntity;
import com.ai.fabric.platform.backend.audit.repository.PlatformAuditEventRepository;
import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.PlatformAIWorkspaceProperties;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Authorization;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerSelfServiceOperationBoundary;
import com.ai.fabric.platform.backend.deployment.authority.DeploymentRuntimeAuthorityService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.service.*;
import com.ai.fabric.platform.backend.productservice.entity.PlatformManagedProductServiceEntity;
import com.ai.fabric.platform.backend.productservice.repository.PlatformManagedProductServiceRepository;
import com.ai.fabric.platform.backend.productservice.service.PlatformManagedProductServiceService;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.service.PlatformCustomerAccessService;
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
import com.ai.fabric.platform.backend.tenant.repository.PlatformCustomerRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServer;
import org.springframework.context.annotation.Configuration;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real V153/V158/V189/V206, JPA transactions, loopback HTTP; only external systems are stubbed. */
@Testcontainers(disabledWithoutDocker = true)
class AIWorkspaceRouteReconciliationPostgresTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final MutableClock clock = new MutableClock();
    private JdbcTemplate jdbc;
    private LocalContainerEntityManagerFactoryBean factory;
    private JpaTransactionManager transactions;
    private TransactionTemplate tx;
    private AIWorkspaceInstallationRepository installations;
    private AIWorkspaceDomainRepository domainRepository;
    private AIWorkspaceDomainService domains;
    private AIWorkspaceRouteStore store;
    private AIWorkspaceDomainRouteService worker;
    private AIWorkspaceInstallationService installationService;
    private AIWorkspaceDomainVerifier verifier;
    private CoolifyApiClient coolify;
    private PlatformManagedProductServiceEntity managedService;
    private PlatformManagedProductServiceRepository managedServices;
    private DeploymentTargetProfileRepository profiles;
    private DeploymentTargetProfileEntity profile;
    private CoolifyTargetProfileResolver resolver;
    private CoolifyConnection connection;
    private PlatformAuditService audit;
    private AIWorkspaceDeploymentSecurityProjectionService securityProjection;
    private AtomicReference<String> providerDomains;
    private WebServer server;
    private AnnotationConfigWebApplicationContext webContext;
    private volatile PlatformRole httpRole = PlatformRole.CUSTOMER_ADMIN;
    private final PlatformAIWorkspaceProperties properties = new PlatformAIWorkspaceProperties(Path.of("."), false,
        120, Duration.ofSeconds(60), null, null, "apps.test", "https://shell.test");

    @BeforeEach void setUp() {
        var ds = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("DROP SCHEMA public CASCADE"); jdbc.execute("CREATE SCHEMA public");
        jdbc.execute("CREATE TABLE platform_customers (id varchar(64) PRIMARY KEY)");
        jdbc.execute("CREATE TABLE platform_consumers (id varchar(64) PRIMARY KEY)");
        for (String migration : List.of("V2__platform_audit_events.sql", "V153__ai_workspace_installations.sql",
            "V158__standalone_ai_workspace_delivery.sql", "V189__ai_workspace_create_receipts.sql",
            "V206__ai_workspace_durable_domain_routes.sql")) {
            new ResourceDatabasePopulator(new ClassPathResource("db/migration/" + migration)).execute(ds);
        }
        jdbc.update("INSERT INTO platform_customers VALUES ('customer-1')");
        jdbc.update("INSERT INTO platform_consumers VALUES ('consumer-1')");
        factory = new LocalContainerEntityManagerFactoryBean(); factory.setDataSource(ds);
        factory.setManagedTypes(PersistenceManagedTypes.of(AIWorkspaceInstallationEntity.class.getName(),
            AIWorkspaceDomainEntity.class.getName(), PlatformAuditEventEntity.class.getName()));
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "none", "hibernate.physical_naming_strategy",
            "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));
        factory.afterPropertiesSet();
        var repositories = new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(factory.getObject()));
        installations = repositories.getRepository(AIWorkspaceInstallationRepository.class);
        domainRepository = repositories.getRepository(AIWorkspaceDomainRepository.class);
        transactions = new JpaTransactionManager(factory.getObject()); transactions.setDataSource(ds);
        tx = new TransactionTemplate(transactions);
        audit = proxy(new PlatformAuditService(repositories.getRepository(PlatformAuditEventRepository.class), mapper));
        store = new AIWorkspaceRouteStore(jdbc, mapper, transactions, audit, clock);
        managedServices = mock(PlatformManagedProductServiceRepository.class);
        managedService = new PlatformManagedProductServiceEntity(); managedService.setId("shared-service");
        managedService.setServiceKind(PlatformManagedProductServiceService.STANDALONE_AI_WORKSPACE_SERVICE);
        managedService.setStatus("ACTIVE"); managedService.setBaseUrl("https://shell.test");
        managedService.setDetailsJson("{\"targetProfileId\":\"profile-1\",\"coolifyApplicationUuid\":\"native-app\"}");
        when(managedServices.findAllByOrderByDisplayNameAsc()).thenReturn(List.of(managedService));
        profiles = mock(DeploymentTargetProfileRepository.class); resolver = mock(CoolifyTargetProfileResolver.class);
        profile = new DeploymentTargetProfileEntity(); profile.setId("profile-1");
        profile.setProviderType(com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType.COOLIFY);
        profile.setActive(true); profile.setPlatformServicesAllowed(true); profile.setEnvironmentName("staging");
        profile.setSourceStrategy("PREBUILT_IMAGE"); profile.setCredentialRefId("credential-1");
        profile.setProviderConfigJson("{\"baseUrl\":\"https://coolify.test\",\"serverUuid\":\"server-1\"}");
        profile.setNetworkPolicyJson("{}"); profile.setResourceDefaultsJson("{}");
        when(profiles.findById("profile-1")).thenReturn(Optional.of(profile));
        connection = mock(CoolifyConnection.class); when(resolver.requireConnection(profile)).thenReturn(connection);
        coolify = mock(CoolifyApiClient.class); providerDomains = new AtomicReference<>("https://shell.test");
        when(coolify.getApplication(connection, "native-app")).thenAnswer(call -> {
            assertNoTransaction(); return Optional.of(application());
        });
        doAnswer(call -> {
            assertNoTransaction(); providerDomains.set(call.getArgument(2)); return null;
        }).when(coolify).updateApplicationDomains(any(), any(), any());
        when(coolify.start(connection, "native-app", false, true)).thenAnswer(call -> {
            assertNoTransaction(); return new CoolifyActionResponse("accepted", "native-job", null);
        });
        when(coolify.getDeployment(connection, "native-job")).thenAnswer(call -> {
            assertNoTransaction(); return Optional.of(deployment("finished", "native-app"));
        });
        worker = newWorker(); verifier = mock(AIWorkspaceDomainVerifier.class);
        domains = proxy(new AIWorkspaceDomainService(domainRepository, properties, verifier, worker));
        var consumers = mock(PlatformConsumerRepository.class);
        var consumer = new PlatformConsumerEntity(); consumer.setId("consumer-1"); consumer.setCustomerId("customer-1");
        consumer.setConsumerId("web"); consumer.setStatus("ACTIVE"); consumer.setBoundDeploymentId("deployment-1");
        when(consumers.findBindingForUpdate("customer-1", "web")).thenReturn(Optional.of(consumer));
        when(consumers.findByCustomerIdAndConsumerIdIgnoreCase("customer-1", "web")).thenReturn(Optional.of(consumer));
        when(consumers.findById("consumer-1")).thenReturn(Optional.of(consumer));
        when(consumers.findAllByIdForUpdate(any())).thenReturn(List.of(consumer));
        var customers = mock(PlatformCustomerRepository.class); when(customers.existsById("customer-1")).thenReturn(true);
        var packs = mock(AIWorkspaceExperiencePackRegistry.class);
        var connections = mock(AIWorkspaceConnectionProfileRegistry.class);
        when(connections.resolve(any(), any(), any(), any())).thenReturn(new AIWorkspaceConnectionProfileRegistry.ConnectionProfile(
            "runtime-anonymous-direct", "1.0.0", "Anonymous", AIWorkspaceConnectionMode.PUBLIC_RUNTIME_ANONYMOUS,
            "direct-public-runtime", "loomai-runtime-anonymous-v1", true, null, null, Map.of()));
        var assets = mock(AIWorkspaceAssetCatalogService.class);
        when(assets.health()).thenReturn(new AIWorkspaceAssetCatalogService.CatalogHealth(false, "NOT_READY", "1"));
        var readiness = new AIWorkspaceReadinessService(consumers, mock(AIWorkspaceAssignmentService.class), assets,
            packs, connections, new AIWorkspaceAuthenticationProfileRegistry(mapper), domains,
            mock(PlatformSecretService.class), mock(DeploymentRuntimeAuthorityService.class), properties, mapper);
        var offers = mock(CustomerSelfServiceOperationBoundary.class);
        when(offers.requireConsumer(any(), any(), any(), any())).thenReturn(new Authorization(null, null));
        when(offers.profileId(any(), any())).thenAnswer(call -> call.getArgument(0) + ":profile");
        securityProjection = mock(AIWorkspaceDeploymentSecurityProjectionService.class);
        installationService = proxy(new AIWorkspaceInstallationService(installations, customers, consumers,
            mock(PlatformCustomerAccessService.class), packs, connections, new AIWorkspaceAuthenticationProfileRegistry(mapper),
            new AIWorkspaceConfigurationValidator(properties), domains, readiness, assets, audit, mapper, offers,
            securityProjection, proxy(new AIWorkspaceCreateReceiptService(jdbc, mapper))));
        authenticate();
    }

    @AfterEach void tearDown() {
        if (server != null) server.stop();
        if (webContext != null) webContext.close();
        SecurityContextHolder.clearContext(); if (factory != null) factory.destroy();
    }

    @Test void realHttpCommitsReceiptAuditAndUpdatesWhileProviderIsBlocked() throws Exception {
        create("first");
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        when(coolify.getApplication(connection, "native-app")).thenAnswer(call -> {
            assertNoTransaction(); entered.countDown(); assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            return Optional.of(application());
        });
        try (var executor = Executors.newSingleThreadExecutor()) {
            var running = executor.submit(worker::runNext);
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            startHttp();
            try {
                var created = http("POST", "", request("second"), "http-create");
                assertThat(created.statusCode()).isEqualTo(201);
                var body = mapper.readTree(created.body());
                assertThat(body.path("status").asText()).isEqualTo("DRAFT");
                assertThat(body.path("ready").asBoolean()).isFalse();
                assertThat(count("ai_workspace_create_receipts")).isEqualTo(2);
                assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_audit_events WHERE action='AI_WORKSPACE_INSTALLATION_CREATED'", Long.class)).isEqualTo(2);
                assertThat(jdbc.queryForObject("SELECT completed_at IS NOT NULL FROM ai_workspace_create_receipts WHERE operation_key='http-create'", Boolean.class)).isTrue();
                String id = body.path("installationId").asText();
                var changed = mapper.valueToTree(request("renamed"));
                ((com.fasterxml.jackson.databind.node.ObjectNode) changed).put("rowVersion", body.path("rowVersion").asLong());
                assertThat(http("PUT", "/" + id, changed, "http-update").statusCode()).isEqualTo(200);
                assertThat(http("DELETE", "/" + id, null, "http-delete").statusCode()).isEqualTo(204);
                verifyNoInteractions(verifier);
                verify(coolify, never()).updateApplicationDomains(any(), any(), any());
                verify(coolify, never()).start(any(), any(), anyBoolean(), anyBoolean());
            } finally { release.countDown(); }
            running.get(5, TimeUnit.SECONDS);
        }
    }

    @Test void rollbackLeavesNoActionableDesiredStateAuditOrReceipt() {
        doThrow(new IllegalStateException("local failure")).when(securityProjection).synchronizeForConsumer(any());
        assertThatThrownBy(() -> create("rolled-back")).hasMessageContaining("local failure");
        assertThat(count("ai_workspace_installations")).isZero(); assertThat(count("ai_workspace_domains")).isZero();
        assertThat(count("ai_workspace_create_receipts")).isZero(); assertThat(count("platform_audit_events")).isZero();
        worker.runNext();
        verify(coolify, never()).updateApplicationDomains(any(), any(), any());
        verify(coolify, never()).start(any(), any(), anyBoolean(), anyBoolean());
    }

    @Test void twoWorkersAndConcurrentAddRemoveFenceStalePlanWithoutCustomerBlocking() throws Exception {
        var first = create("first");
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        when(coolify.getApplication(connection, "native-app")).thenAnswer(call -> {
            entered.countDown(); assertThat(release.await(10, TimeUnit.SECONDS)).isTrue(); return Optional.of(application());
        });
        try (var executor = Executors.newSingleThreadExecutor()) {
            var old = executor.submit(worker::runNext); assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            newWorker().runNext();
            create("second"); installationService.deleteDraft("customer-1", first.installationId());
            release.countDown(); old.get(5, TimeUnit.SECONDS);
        }
        verify(coolify, never()).updateApplicationDomains(any(), any(), any());
        worker.runNext();
        assertThat(providerDomains.get()).isEqualTo("https://shell.test,https://second.apps.test");
        verify(coolify, times(1)).updateApplicationDomains(any(), any(), any());
    }

    @Test void restartAfterSuccessfulPatchStillStartsOldSnapshotThenReconcilesDeletion() {
        var original = create("first"); worker.runNext();
        assertThat(phase()).isEqualTo("PATCHED");
        installationService.deleteDraft("customer-1", original.installationId());
        worker = newWorker(); worker.runNext();
        assertThat(phase()).isEqualTo("POLLING");
        tick(); assertThat(phase()).isEqualTo("IDLE");
        assertThat(store.isApplied(binding(), "first.apps.test")).isFalse();
        tick(); assertThat(providerDomains.get()).isEqualTo("https://shell.test");
        assertThat(phase()).isEqualTo("PATCHED");
    }

    @Test void concurrentRemovalDuringProviderPatchKeepsCleanupOwnershipAndLatestDesiredGeneration() throws Exception {
        var original = create("first");
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        doAnswer(call -> {
            assertNoTransaction(); providerDomains.set(call.getArgument(2)); entered.countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue(); return null;
        }).when(coolify).updateApplicationDomains(any(), any(), any());
        try (var executor = Executors.newSingleThreadExecutor()) {
            var old = executor.submit(worker::runNext); assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(phase()).isEqualTo("PATCHING");
            assertThat(jdbc.queryForObject("SELECT inflight_domains FROM ai_workspace_route_reconciliations", String.class))
                .contains("first.apps.test");
            installationService.deleteDraft("customer-1", original.installationId()); create("second");
            newWorker().runNext();
            release.countDown(); old.get(5, TimeUnit.SECONDS);
        } finally { release.countDown(); }
        worker.runNext(); tick();
        assertThat(store.isApplied(binding(), "second.apps.test")).isFalse();
        tick();
        assertThat(providerDomains.get()).isEqualTo("https://shell.test,https://second.apps.test");
        verify(coolify, times(2)).updateApplicationDomains(any(), any(), any());
        verify(coolify, times(1)).start(any(), any(), anyBoolean(), anyBoolean());
    }

    @Test void recreatedSameHostnameCannotLoseNativeDeploymentRequirement() {
        var original = create("same"); worker.runNext();
        installationService.deleteDraft("customer-1", original.installationId()); create("same");
        worker.runNext();
        verify(coolify).start(connection, "native-app", false, true);
        assertThat(phase()).isEqualTo("POLLING");
        tick();
        assertThat(jdbc.queryForObject("SELECT applied_generation < generation FROM ai_workspace_route_reconciliations", Boolean.class)).isTrue();
        assertThat(store.isApplied(binding(), "same.apps.test")).isFalse();
        tick(); assertThat(store.isApplied(binding(), "same.apps.test")).isTrue();
    }

    @Test void changedProviderBindingNeverUsesOldAppliedAuthority() {
        create("first"); worker.runNext(); worker.runNext(); tick();
        assertThat(worker.isApplied("first.apps.test")).isTrue();
        managedService.setDetailsJson("{\"targetProfileId\":\"profile-1\",\"coolifyApplicationUuid\":\"new-app\"}");
        assertThat(worker.isApplied("first.apps.test")).isFalse();
        assertThatThrownBy(worker::runNext).hasMessageContaining("binding changed");
        verify(coolify, never()).getApplication(connection, "new-app");
    }

    @ParameterizedTest @ValueSource(strings = {"baseUrl", "serverUuid", "active", "allowed", "credential"})
    void mutableSameIdProfileCannotReusePinnedRouteAuthority(String change) {
        create("first"); worker.runNext(); worker.runNext(); tick();
        assertThat(worker.isApplied("first.apps.test")).isTrue();
        switch (change) {
            case "active" -> profile.setActive(false);
            case "allowed" -> profile.setPlatformServicesAllowed(false);
            case "credential" -> profile.setCredentialRefId("other-credential");
            default -> profile.setProviderConfigJson(change.equals("baseUrl")
                ? "{\"baseUrl\":\"https://foreign.test\",\"serverUuid\":\"server-1\"}"
                : "{\"baseUrl\":\"https://coolify.test\",\"serverUuid\":\"other-server\"}");
        }
        clearInvocations(coolify);
        if (change.equals("active") || change.equals("allowed")) {
            assertThatThrownBy(() -> worker.isApplied("first.apps.test")).hasMessageContaining("not available");
        } else assertThat(worker.isApplied("first.apps.test")).isFalse();
        assertThatThrownBy(worker::runNext).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(coolify);
    }

    @Test void blockedObservationIsStaffOnlyStrictAndDurablyReplaysWithoutProviderIo() throws Exception {
        create("first"); worker.runNext();
        when(coolify.start(connection, "native-app", false, true)).thenThrow(new IllegalStateException("SECRET native body"));
        worker.runNext(); assertThat(phase()).isEqualTo("UNKNOWN");
        var status = store.statuses().getFirst();
        var command = new AIWorkspaceRouteStore.ObserveDeployment("review-native-job", status.generation(),
            status.desiredFingerprint(), status.inflightFingerprint(), "native-job", "review:exact-native-job-receipt");
        when(coolify.getDeployment(connection, "native-job")).thenAnswer(call -> {
            assertNoTransaction();
            return Optional.of(new CoolifyDeploymentSummary("native-job", "Shared", "native-app", "finished", null,
                null, Instant.now().plusSeconds(1).toString(), null, null, null));
        });
        startHttp();
        String path = "/api/platform/ai-workspaces/routes/" + status.routeKey() + "/observe-deployment";
        assertThat(httpPath("POST", path, mapper.writeValueAsString(command)).statusCode()).isEqualTo(403);
        httpRole = null;
        assertThat(httpPath("GET", "/api/platform/ai-workspaces/routes", null).statusCode()).isEqualTo(401);
        httpRole = PlatformRole.PLATFORM_ADMIN;
        assertThat(httpPath("GET", "/api/platform/ai-workspaces/routes", null).statusCode()).isEqualTo(200);
        assertThat(httpPath("POST", path, mapper.writeValueAsString(command)).statusCode()).isEqualTo(200);
        assertThat(phase()).isEqualTo("POLLING");
        clearInvocations(coolify);
        assertThat(httpPath("POST", path, mapper.writeValueAsString(command)).statusCode()).isEqualTo(200);
        verifyNoInteractions(coolify);
        var changed = mapper.valueToTree(command);
        ((com.fasterxml.jackson.databind.node.ObjectNode) changed).put("operatorEvidenceRef", "changed-review");
        assertThat(httpPath("POST", path, mapper.writeValueAsString(changed)).statusCode()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_audit_events WHERE action='AI_WORKSPACE_ROUTE_OBSERVATION_ATTACHED'", Long.class)).isEqualTo(1);
        for (String bad : List.of(
            mapper.writeValueAsString(command).replace("\"expectedGeneration\":" + status.generation(), "\"expectedGeneration\":\"" + status.generation() + "\""),
            mapper.writeValueAsString(command).replace("\"expectedGeneration\":" + status.generation(), "\"expectedGeneration\":1.0"),
            mapper.writeValueAsString(command).replaceFirst("\\{", "{\"extra\":true,"),
            mapper.writeValueAsString(command).replaceFirst("\\{", "{\"deploymentUuid\":\"other\","))) {
            assertThat(httpPath("POST", path, bad).statusCode()).isEqualTo(400);
        }
        verifyNoInteractions(coolify);
        tick(); assertThat(phase()).isEqualTo("IDLE");
        assertThat(worker.isApplied("first.apps.test")).isTrue();
    }

    @ParameterizedTest @ValueSource(strings = {"foreign", "old", "missing", "unknown-status", "fqdn-drift"})
    void observationCannotAdoptForeignOldAbsentOrUnmatchedNativeProof(String failure) {
        create("first"); worker.runNext();
        when(coolify.start(connection, "native-app", false, true)).thenThrow(new IllegalStateException());
        worker.runNext();
        var status = store.statuses().getFirst();
        var command = new AIWorkspaceRouteStore.ObserveDeployment("attach", status.generation(), status.desiredFingerprint(),
            status.inflightFingerprint(), "native-job", "review:job");
        authenticate(PlatformRole.PLATFORM_ADMIN);
        var deployment = new CoolifyDeploymentSummary("native-job", "Shared", failure.equals("foreign") ? "foreign-app" : "native-app",
            failure.equals("unknown-status") ? "finished-with-guessed-meaning" : "finished", null, null,
            failure.equals("old") ? "2000-01-01T00:00:00Z" : Instant.now().plusSeconds(1).toString(), null, null, null);
        when(coolify.getDeployment(connection, "native-job")).thenReturn(failure.equals("missing") ? Optional.empty() : Optional.of(deployment));
        if (failure.equals("fqdn-drift")) providerDomains.set("https://foreign.test");
        assertThatThrownBy(() -> worker.observeDeployment(status.routeKey(), command))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class).hasMessageContaining("could not be proven")
            .hasNoCause();
        assertThat(phase()).isEqualTo("UNKNOWN"); assertThat(applied()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_audit_events WHERE action='AI_WORKSPACE_ROUTE_OBSERVATION_ATTACHED'", Long.class)).isZero();
        verify(coolify, times(1)).start(any(), any(), anyBoolean(), anyBoolean());
    }

    @ParameterizedTest @ValueSource(strings = {"PATCHING", "STARTING"})
    void expiredEffectLeaseAndLateCompletionCannotRedispatch(String phase) {
        create("first"); var old = store.claim(binding());
        assertThat(store.preparePatch(old, List.of("https://shell.test", "https://first.apps.test"))).isTrue();
        if (phase.equals("STARTING")) {
            store.patched(old); old = store.claim(binding()); assertThat(store.prepareStart(old)).isTrue();
        }
        clock.advance(Duration.ofMinutes(2));
        worker = newWorker(); worker.runNext();
        store.patched(old); store.started(old, "late-job");
        assertThat(phase()).isEqualTo("UNKNOWN"); assertThat(applied()).isZero();
        tick(); worker.runNext();
        verifyNoInteractions(coolify);
    }

    @Test void lostStartResponseNeverCausesDuplicateStartAcrossRestart() {
        create("first"); worker.runNext();
        when(coolify.start(connection, "native-app", false, true)).thenThrow(new IllegalStateException("SECRET provider body"));
        worker.runNext(); assertThat(phase()).isEqualTo("UNKNOWN");
        clock.advance(Duration.ofMinutes(2)); newWorker().runNext(); tick();
        verify(coolify, times(1)).start(any(), any(), anyBoolean(), anyBoolean());
        assertThat(jdbc.queryForObject("SELECT last_error FROM ai_workspace_route_reconciliations", String.class))
            .isEqualTo("PROVIDER_OUTCOME_UNAVAILABLE");
        assertThat(jdbc.queryForList("SELECT details_json FROM platform_audit_events").toString()).doesNotContain("SECRET");
    }

    @Test void absentStartReceiptDoesNotTreatRunningApplicationAsDeploymentProof() {
        create("first"); worker.runNext();
        when(coolify.start(connection, "native-app", false, true)).thenReturn(new CoolifyActionResponse("accepted", null, null));
        worker.runNext(); clock.advance(Duration.ofMinutes(2)); newWorker().runNext();
        assertThat(phase()).isEqualTo("UNKNOWN"); assertThat(applied()).isZero();
        assertThat(worker.isApplied("first.apps.test")).isFalse();
        verify(coolify, times(1)).start(any(), any(), anyBoolean(), anyBoolean());
    }

    @Test void customOwnershipIsExplicitAndUnmanagedDomainsSurviveRemoval() {
        var created = installationService.create("customer-1", request("first", List.of("custom.test")), "custom");
        providerDomains.set("https://shell.test,http://unmanaged.apps.test");
        worker.runNext(); assertThat(providerDomains.get()).doesNotContain("custom.test");
        worker.runNext(); tick();
        when(verifier.verifyOwnership("custom.test", token("custom.test")))
            .thenReturn(new AIWorkspaceDomainVerifier.Verification(true, null));
        assertThat(installationService.verifyDomain("customer-1", created.installationId(), "custom.test").status())
            .isEqualTo("ROUTING_PENDING");
        verify(verifier, never()).verifyRoute("custom.test");
        tick(); worker.runNext(); tick();
        assertThat(providerDomains.get()).contains("https://custom.test");
        when(verifier.verifyRoute("custom.test")).thenReturn(new AIWorkspaceDomainVerifier.Verification(true, null));
        assertThat(installationService.verifyDomain("customer-1", created.installationId(), "custom.test").status())
            .isEqualTo("VERIFIED");
        installationService.deleteDraft("customer-1", created.installationId());
        tick(); assertThat(providerDomains.get()).isEqualTo("https://shell.test,http://unmanaged.apps.test");
    }

    @Test void disableImmediatelyRevokesServingAndQueuesPhysicalRemovalWithoutDns() {
        var created = create("first"); worker.runNext(); worker.runNext(); tick();
        installationService.disable("customer-1", created.installationId());
        verifyNoInteractions(verifier);
        assertThat(worker.isApplied("first.apps.test")).isFalse();
        tick(); assertThat(providerDomains.get()).isEqualTo("https://shell.test");
    }

    @ParameterizedTest @ValueSource(strings = {"failed", "finished-but-not-a-status", "queued"})
    void onlyExactFinishedNativeJobCanAdvanceAppliedGeneration(String status) {
        create("first"); worker.runNext(); worker.runNext();
        when(coolify.getDeployment(connection, "native-job")).thenReturn(Optional.of(deployment(status, "native-app")));
        tick(); assertThat(applied()).isZero();
        assertThat(worker.isApplied("first.apps.test")).isFalse();
    }

    @Test void foreignNativeJobCannotBeApplied() {
        create("first"); worker.runNext(); worker.runNext();
        when(coolify.getDeployment(connection, "native-job")).thenReturn(Optional.of(deployment("finished", "foreign-app")));
        tick(); assertThat(applied()).isZero();
        assertThat(jdbc.queryForObject("SELECT last_error FROM ai_workspace_route_reconciliations", String.class))
            .isEqualTo("DEPLOYMENT_IDENTITY_MISMATCH");
    }

    @Test void migrationAndFirstClaimDoNotInventHistoricDeploymentProof() {
        create("first");
        assertThat(count("ai_workspace_route_reconciliations")).isZero();
        managedService.setDetailsJson("{\"targetProfileId\":\"profile-1\",\"coolifyApplicationUuid\":\"native-app\",\"aiWorkspaceManagedDomains\":[\"old.test\"]}");
        var claimed = store.claim(new AIWorkspaceRouteStore.Binding("shared-service", "profile-1", binding().profileFingerprint(), "native-app", List.of("old.test")));
        assertThat(claimed.managed()).containsExactly("old.test"); assertThat(applied()).isZero();
        assertThat(claimed.deploymentUuid()).isNull(); assertThat(claimed.phase()).isEqualTo("IDLE");
        assertThat(domainRepository.findAll()).allMatch(d -> d.getStatus() == AIWorkspaceDomainStatus.PENDING);
    }

    private AIWorkspaceInstallationSummary create(String slug) {
        return installationService.create("customer-1", request(slug), "create-" + slug + "-" + UUID.randomUUID());
    }
    private CreateAIWorkspaceInstallationRequest request(String slug) { return request(slug, List.of()); }
    private CreateAIWorkspaceInstallationRequest request(String slug, List<String> custom) {
        return new CreateAIWorkspaceInstallationRequest("web", "Workspace", "generic", "1.0.0", "public-runtime-anonymous",
            "runtime-anonymous-direct", "1.0.0", mapper.createObjectNode(), List.of(), mapper.createObjectNode(),
            "standalone", slug, custom, "public", "none", "1.0.0", mapper.createObjectNode());
    }
    private AIWorkspaceDomainRouteService newWorker() {
        return new AIWorkspaceDomainRouteService(store, managedServices, profiles, resolver, coolify, properties, mapper);
    }
    private AIWorkspaceRouteStore.Binding binding() {
        return new AIWorkspaceRouteStore.Binding("shared-service", "profile-1", AIWorkspaceRouteStore.fingerprint(List.of(
            DeploymentTargetProfileIdentity.fingerprint(mapper, profile), Boolean.toString(profile.isActive()),
            Boolean.toString(profile.isPlatformServicesAllowed()))), "native-app", List.of());
    }
    private void tick() { clock.advance(Duration.ofSeconds(31)); worker.runNext(); }
    private String phase() { return jdbc.queryForObject("SELECT phase FROM ai_workspace_route_reconciliations", String.class); }
    private long applied() { return jdbc.queryForObject("SELECT applied_generation FROM ai_workspace_route_reconciliations", Long.class); }
    private long count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class); }
    private String token(String hostname) { return jdbc.queryForObject("SELECT verification_token FROM ai_workspace_domains WHERE hostname=?", String.class, hostname); }
    private CoolifyApplicationSummary application() {
        return new CoolifyApplicationSummary("native-app", "Shared", providerDomains.get(), "running", null, null, null);
    }
    private CoolifyDeploymentSummary deployment(String status, String app) {
        return new CoolifyDeploymentSummary("native-job", "Shared", app, status, null, null, null, null, null, null);
    }
    private void assertNoTransaction() { assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse(); }
    private void authenticate() {
        authenticate(PlatformRole.CUSTOMER_ADMIN);
    }
    private void authenticate(PlatformRole role) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
            new PlatformPrincipal("owner@test", role, "owner@test", "SESSION"), "unused", List.of()));
    }
    @SuppressWarnings("unchecked") private <T> T proxy(T target) {
        var proxy = new ProxyFactory(target);
        proxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        return (T) proxy.getProxy();
    }
    @Configuration @EnableWebMvc static class HttpConfiguration { }
    private void startHttp() {
        webContext = new AnnotationConfigWebApplicationContext(); webContext.register(HttpConfiguration.class);
        var controller = new PlatformAIWorkspaceController(installationService, mock(AIWorkspaceConnectionService.class));
        webContext.addBeanFactoryPostProcessor(beans -> {
            beans.registerSingleton("workspaceController", controller);
            beans.registerSingleton("routeController", new AIWorkspaceRouteController(worker));
        });
        server = new TomcatServletWebServerFactory(0).getWebServer(context -> {
            webContext.setServletContext(context); webContext.refresh();
            context.addServlet("dispatcher", new DispatcherServlet(webContext)).addMapping("/");
            context.addFilter("fixture-auth", (Filter) (request, response, chain) -> {
                if (httpRole != null) authenticate(httpRole);
                try { chain.doFilter(request, response); }
                finally { SecurityContextHolder.clearContext(); }
            }).addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), false, "/*");
        });
        server.start();
    }
    private HttpResponse<String> http(String method, String suffix, Object body, String key) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + server.getPort()
            + "/api/platform/customers/customer-1/ai-workspace-installations" + suffix))
            .timeout(Duration.ofSeconds(4)).header("Content-Type", "application/json").header("Idempotency-Key", key)
            .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> httpPath(String method, String path, String body) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:" + server.getPort() + path))
            .timeout(Duration.ofSeconds(4)).header("Content-Type", "application/json")
            .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(),
            HttpResponse.BodyHandlers.ofString());
    }
    private static class MutableClock extends Clock {
        private Instant now = Instant.now().plusSeconds(1);
        void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
