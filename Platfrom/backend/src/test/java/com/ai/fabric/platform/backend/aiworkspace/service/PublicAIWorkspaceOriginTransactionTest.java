package com.ai.fabric.platform.backend.aiworkspace.service;

import com.ai.fabric.platform.backend.aiworkspace.entity.AIWorkspaceDomainEntity;
import com.ai.fabric.platform.backend.aiworkspace.entity.AIWorkspaceInstallationEntity;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceAssetDescriptor;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceConnectionMode;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceDeliveryMode;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceDomainStatus;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceDomainType;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceInstallationStatus;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceInteractionPolicy;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceReadinessSummary;
import com.ai.fabric.platform.backend.aiworkspace.repository.AIWorkspaceDomainRepository;
import com.ai.fabric.platform.backend.aiworkspace.repository.AIWorkspaceInstallationRepository;
import com.ai.fabric.platform.backend.aiworkspace.web.AIWorkspacePublicBoundaryFilter;
import com.ai.fabric.platform.backend.aiworkspace.web.PublicAIWorkspaceController;
import com.ai.fabric.platform.backend.config.PlatformAIWorkspaceProperties;
import com.ai.fabric.platform.backend.config.PlatformDeliveryProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
import com.ai.fabric.platform.backend.tenant.service.PlatformCustomerConsumerService;
import com.ai.fabric.platform.backend.web.PlatformApiExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PublicAIWorkspaceOriginTransactionTest {

    private static final String INSTALLATION_ID = "awi_pub_0123456789abcdef0123456789abcdef";
    private static final String ORIGIN = "https://northfield.apps.loomai.test";
    private static final String APP_PATH = "/api/public/ai-workspace/apps/bootstrap";
    private static final String MANIFEST_PATH = "/api/public/ai-workspace/installations/" + INSTALLATION_ID + "/manifest";
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private LocalContainerEntityManagerFactoryBean factory;
    private JpaTransactionManager transactions;
    private TransactionTemplate transaction;
    private JdbcTemplate jdbc;
    private AIWorkspaceDomainRepository domainRepository;
    private AIWorkspaceInstallationRepository installationRepository;
    private AIWorkspaceDomainService domains;
    private PublicAIWorkspaceManifestService manifests;
    private AIWorkspacePublicBoundaryFilter filter;
    private MockMvc mvc;
    private RuntimeException domainReadFailure;
    private RuntimeException installationReadFailure;

    @BeforeAll
    void createJpaFixture() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:workspace-origin-" + UUID.randomUUID()
            + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(source);
        factory.setManagedTypes(PersistenceManagedTypes.of(
            AIWorkspaceDomainEntity.class.getName(), AIWorkspaceInstallationEntity.class.getName()));
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop",
            "hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));
        factory.afterPropertiesSet();
        transactions = new JpaTransactionManager(factory.getObject());
        transactions.setDataSource(source);
        transaction = new TransactionTemplate(transactions);
        jdbc = new JdbcTemplate(source);
        var repositories = new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(factory.getObject()));
        domainRepository = withReadFailure(repositories.getRepository(AIWorkspaceDomainRepository.class), () -> domainReadFailure);
        installationRepository = withReadFailure(repositories.getRepository(AIWorkspaceInstallationRepository.class), () -> installationReadFailure);
    }

    @BeforeEach
    void setUp() {
        domainReadFailure = null;
        installationReadFailure = null;
        transaction.executeWithoutResult(ignored -> {
            domainRepository.deleteAllInBatch();
            installationRepository.deleteAllInBatch();
            installationRepository.saveAndFlush(installation());
            domainRepository.saveAndFlush(domain());
        });
        var properties = new PlatformAIWorkspaceProperties(Path.of("."), false, 120, Duration.ofSeconds(60),
            null, null, "apps.loomai.test", "https://workspace.loomai.test");
        domains = transactional(new AIWorkspaceDomainService(domainRepository, properties,
            mock(AIWorkspaceDomainVerifier.class), mock(AIWorkspaceDomainRouteService.class)));
        var consumers = mock(PlatformConsumerRepository.class);
        var consumerService = mock(PlatformCustomerConsumerService.class);
        var assignments = mock(AIWorkspaceAssignmentService.class);
        var readiness = mock(AIWorkspaceReadinessService.class);
        var assets = mock(AIWorkspaceAssetCatalogService.class);
        var packs = mock(AIWorkspaceExperiencePackRegistry.class);
        var consumer = new PlatformConsumerEntity();
        consumer.setId("consumer-entity-1");
        consumer.setConsumerId("northfield-web");
        var deployment = new DeploymentEntity();
        deployment.setId("dep-1");
        var release = new DeploymentReleaseEntity();
        release.setId("rel-1");
        var version = new DeploymentVersionEntity();
        version.setBehaviorConfigJson("{}");
        var assignment = new AIWorkspaceAssignmentService.Assignment(consumer, deployment, release, version,
            mapper.createObjectNode(), "https://dep-example.loomai.test", "sha256:" + "a".repeat(64));
        when(consumers.findById(consumer.getId())).thenReturn(java.util.Optional.of(consumer));
        when(consumerService.resolvePublicConsumer(consumer.getConsumerId()))
            .thenReturn(new PlatformCustomerConsumerService.ResolvedPublicConsumer(consumer, deployment, release));
        when(assignments.resolveCurrent(consumer)).thenReturn(assignment);
        when(readiness.evaluate(any())).thenReturn(new AIWorkspaceReadinessSummary(true, INSTALLATION_ID,
            "public-runtime-anonymous", "northfield-web", "dep-1", "rel-1", "https://dep-example.loomai.test",
            assignment.assignmentRevision(), List.of()));
        when(assets.installer()).thenReturn(asset("installer"));
        when(assets.workspace()).thenReturn(asset("workspace"));
        when(packs.resolve("dealership", "1.1.0")).thenReturn(new AIWorkspaceExperiencePackRegistry.ExperiencePack(
            "dealership", "1.1.0", "Dealership", "loomai-dealership-experience-config-v1", true, asset("pack")));
        manifests = transactional(new PublicAIWorkspaceManifestService(installationRepository, consumers, consumerService,
            assignments, readiness, assets, packs, new AIWorkspaceConnectionProfileRegistry(properties),
            new AIWorkspaceAuthenticationProfileRegistry(mapper), domains,
            new PlatformDeliveryProperties("https://api.loomai.test", true, Duration.ofMinutes(10)), properties, mapper));
        var rateLimiter = mock(AIWorkspaceManifestRateLimiter.class);
        when(rateLimiter.allow(anyString())).thenReturn(true);
        filter = new AIWorkspacePublicBoundaryFilter(manifests, rateLimiter,
            new AIWorkspaceObservability(new SimpleMeterRegistry()));
        mvc = MockMvcBuilders.standaloneSetup(new PublicAIWorkspaceController(assets, manifests))
            .setControllerAdvice(new PlatformApiExceptionHandler())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
            .addFilters(filter).build();
    }

    @AfterAll
    void destroyJpaFixture() {
        if (factory != null) factory.destroy();
        if (jdbc != null) jdbc.execute("SHUTDOWN");
    }

    @ParameterizedTest
    @ValueSource(strings = {APP_PATH, MANIFEST_PATH})
    void realTransactionalServicesPreserveSuccessfulGetRevalidationAndPreflight(String path) throws Exception {
        assertThat(AopUtils.isAopProxy(domains)).isTrue();
        assertThat(AopUtils.isAopProxy(manifests)).isTrue();
        assertThat(domains.findVerifiedOrigin(ORIGIN)).isPresent();
        var response = mvc.perform(withOrigin(get(path), ORIGIN))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN))
            .andExpect(header().string(HttpHeaders.VARY, HttpHeaders.ORIGIN))
            .andReturn().getResponse();
        assertThat(response.getHeader(HttpHeaders.ETAG)).isNotBlank();
        mvc.perform(withOrigin(get(path), ORIGIN).header(HttpHeaders.IF_NONE_MATCH, response.getHeader(HttpHeaders.ETAG)))
            .andExpect(status().isNotModified())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN));
        mvc.perform(withOrigin(options(path), ORIGIN))
            .andExpect(status().isNoContent())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN));
    }

    @ParameterizedTest
    @MethodSource("deniedOrigins")
    void unknownAndInvalidOriginsReturn404WithoutCorsOnBothRoutes(String path, String origin) throws Exception {
        assertDenied(path, origin);
    }

    static Stream<Arguments> deniedOrigins() {
        return Stream.of(APP_PATH, MANIFEST_PATH).flatMap(path -> Stream.of(null, "", "null", "not an origin",
            "https://unknown.apps.loomai.test", "http://northfield.apps.loomai.test", ORIGIN + ":443", ORIGIN + "/path",
            ORIGIN + "?query=1", ORIGIN + "#fragment", "https://user@northfield.apps.loomai.test",
            ORIGIN + ".attacker.example").map(origin -> Arguments.of(path, origin)));
    }

    @ParameterizedTest
    @EnumSource(value = AIWorkspaceDomainStatus.class, names = {"PENDING", "ROUTING_PENDING", "DISABLED"})
    void unverifiedOrDisabledDomainIsAnExpectedAppDenial(AIWorkspaceDomainStatus state) throws Exception {
        transaction.executeWithoutResult(ignored -> {
            var domain = domainRepository.findById("awd-1").orElseThrow();
            domain.setStatus(state);
            domainRepository.saveAndFlush(domain);
        });
        assertThat(domains.findVerifiedOrigin(ORIGIN)).isEmpty();
        assertThat(manifests.isAppOriginAllowed(ORIGIN)).isFalse();
        assertDenied(APP_PATH, ORIGIN);
    }

    @ParameterizedTest
    @ValueSource(strings = {APP_PATH, MANIFEST_PATH})
    void missingInstallationIsAnExpectedDenial(String path) throws Exception {
        transaction.executeWithoutResult(ignored -> installationRepository.deleteAllInBatch());
        assertDenied(path, ORIGIN);
    }

    @ParameterizedTest
    @EnumSource(value = AIWorkspaceInstallationStatus.class, names = {"DRAFT", "DISABLED"})
    void inactiveInstallationsAreDeniedOnBothRoutes(AIWorkspaceInstallationStatus state) throws Exception {
        transaction.executeWithoutResult(ignored -> {
            var installation = installationRepository.findById("awi-1").orElseThrow();
            installation.setStatus(state);
            installationRepository.saveAndFlush(installation);
        });
        assertDenied(APP_PATH, ORIGIN);
        assertDenied(MANIFEST_PATH, ORIGIN);
    }

    @Test
    void embeddedInstallationIsDeniedAsAnAppButRetainsItsManifestContract() throws Exception {
        transaction.executeWithoutResult(ignored -> {
            var installation = installationRepository.findById("awi-1").orElseThrow();
            installation.setDeliveryMode(AIWorkspaceDeliveryMode.EMBEDDED);
            installationRepository.saveAndFlush(installation);
        });
        assertDenied(APP_PATH, ORIGIN);
        assertThat(manifests.isOriginAllowed(INSTALLATION_ID, ORIGIN)).isTrue();
        mvc.perform(withOrigin(get(MANIFEST_PATH), ORIGIN)).andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN));
    }

    @ParameterizedTest
    @ValueSource(strings = {APP_PATH, MANIFEST_PATH})
    void verifiedDomainCannotOverrideTheInstallationsExactOriginAllowlist(String path) throws Exception {
        transaction.executeWithoutResult(ignored -> {
            var installation = installationRepository.findById("awi-1").orElseThrow();
            installation.setAllowedOriginsJson("[\"https://other.apps.loomai.test\"]");
            installationRepository.saveAndFlush(installation);
        });
        assertDenied(path, ORIGIN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://unknown.apps.loomai.test", "http://northfield.apps.loomai.test",
        "https://northfield.apps.loomai.test:443", "not an origin"})
    void expectedDenialDoesNotPoisonAnAmbientWriteTransaction(String origin) {
        transaction.executeWithoutResult(status -> {
            assertThat(manifests.isAppOriginAllowed(origin)).isFalse();
            assertThat(manifests.isOriginAllowed(INSTALLATION_ID, origin)).isFalse();
            assertThat(status.isRollbackOnly()).isFalse();
            var installation = installationRepository.findById("awi-1").orElseThrow();
            installation.setDisplayName("committed after denial");
            installationRepository.saveAndFlush(installation);
        });
        assertThat(jdbc.queryForObject("SELECT display_name FROM ai_workspace_installations WHERE id='awi-1'", String.class))
            .isEqualTo("committed after denial");
    }

    @Test
    void missingInstallationLookupDoesNotPoisonAnAmbientTransaction() {
        transaction.executeWithoutResult(status -> {
            installationRepository.deleteAllInBatch();
            assertThat(manifests.isAppOriginAllowed(ORIGIN)).isFalse();
            assertThat(manifests.isOriginAllowed(INSTALLATION_ID, ORIGIN)).isFalse();
            assertThat(status.isRollbackOnly()).isFalse();
        });
        assertThat(installationRepository.count()).isZero();
    }

    @Test
    void strictResolversRetain404ForUnknownDomainsAndInstallations() {
        assert404(() -> domains.resolveVerifiedOrigin("https://unknown.apps.loomai.test"));
        assert404(() -> manifests.resolveApp("https://unknown.apps.loomai.test"));
        assert404(() -> manifests.resolve("awi_pub_" + "a".repeat(32), ORIGIN));
        assert404(() -> manifests.resolve("invalid", ORIGIN));
    }

    @Test
    void domainRepositoryFailurePropagatesUnchangedThroughRealServiceProxiesAndBoundary() {
        domainReadFailure = new DataAccessResourceFailureException("fixture domain database unavailable");
        assertThatThrownBy(() -> manifests.isAppOriginAllowed(ORIGIN)).isSameAs(domainReadFailure);
        assertThatThrownBy(() -> domains.findVerifiedOrigin(ORIGIN)).isSameAs(domainReadFailure);
        assertThatThrownBy(() -> domains.resolveVerifiedOrigin(ORIGIN)).isSameAs(domainReadFailure);
        assertBoundaryFailure(APP_PATH, domainReadFailure);
    }

    @ParameterizedTest
    @ValueSource(strings = {APP_PATH, MANIFEST_PATH})
    void installationRepositoryFailurePropagatesUnchangedForBothRoutes(String path) {
        installationReadFailure = new DataAccessResourceFailureException("fixture installation database unavailable");
        if (APP_PATH.equals(path)) {
            assertThatThrownBy(() -> manifests.isAppOriginAllowed(ORIGIN)).isSameAs(installationReadFailure);
            assertThatThrownBy(() -> manifests.resolveApp(ORIGIN)).isSameAs(installationReadFailure);
        } else {
            assertThatThrownBy(() -> manifests.isOriginAllowed(INSTALLATION_ID, ORIGIN)).isSameAs(installationReadFailure);
            assertThatThrownBy(() -> manifests.resolve(INSTALLATION_ID, ORIGIN)).isSameAs(installationReadFailure);
        }
        assertBoundaryFailure(path, installationReadFailure);
    }

    private void assertDenied(String path, String origin) throws Exception {
        if (APP_PATH.equals(path)) assertThat(manifests.isAppOriginAllowed(origin)).isFalse();
        else assertThat(manifests.isOriginAllowed(INSTALLATION_ID, origin)).isFalse();
        mvc.perform(withOrigin(get(path), origin)).andExpect(status().isNotFound())
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        mvc.perform(withOrigin(options(path), origin)).andExpect(status().isNotFound())
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    private void assertBoundaryFailure(String path, RuntimeException failure) {
        for (String method : List.of("GET", "OPTIONS")) {
            var request = new MockHttpServletRequest(method, path);
            request.addHeader(HttpHeaders.ORIGIN, ORIGIN);
            var response = new MockHttpServletResponse();
            assertThatThrownBy(() -> filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
                throw new AssertionError("A failed boundary must not invoke the controller.");
            })).isSameAs(failure);
            assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
        }
    }

    private static void assert404(Runnable call) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ResponseStatusException.class,
            ex -> assertThat(ex.getStatusCode().value()).isEqualTo(NOT_FOUND.value()));
    }

    private static MockHttpServletRequestBuilder withOrigin(MockHttpServletRequestBuilder request, String origin) {
        return origin == null ? request : request.header(HttpHeaders.ORIGIN, origin);
    }

    @SuppressWarnings("unchecked")
    private <T> T transactional(T target) {
        var proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        return (T) proxy.getProxy();
    }

    @SuppressWarnings("unchecked")
    private static <T> T withReadFailure(T repository, Supplier<RuntimeException> failure) {
        var proxy = new ProxyFactory(repository);
        proxy.addAdvice((MethodInterceptor) call -> {
            if (call.getMethod().getName().startsWith("find") && failure.get() != null) throw failure.get();
            return call.proceed();
        });
        return (T) proxy.getProxy();
    }

    private AIWorkspaceInstallationEntity installation() {
        var installation = new AIWorkspaceInstallationEntity();
        installation.setId("awi-1");
        installation.setInstallationId(INSTALLATION_ID);
        installation.setCustomerId("customer-1");
        installation.setConsumerEntityId("consumer-entity-1");
        installation.setDisplayName("Northfield");
        installation.setStatus(AIWorkspaceInstallationStatus.ACTIVE);
        installation.setExperiencePackCode("dealership");
        installation.setExperiencePackVersion("1.1.0");
        installation.setConnectionMode(AIWorkspaceConnectionMode.PUBLIC_RUNTIME_ANONYMOUS);
        installation.setConnectionProfileCode(AIWorkspaceConnectionProfileRegistry.ANONYMOUS);
        installation.setConnectionProfileVersion("1.0.0");
        installation.setDeliveryMode(AIWorkspaceDeliveryMode.STANDALONE);
        installation.setAppSlug("northfield");
        installation.setInteractionPolicy(AIWorkspaceInteractionPolicy.PUBLIC);
        installation.setAuthProfileCode(AIWorkspaceAuthenticationProfileRegistry.NONE);
        installation.setAuthProfileVersion(AIWorkspaceAuthenticationProfileRegistry.VERSION);
        installation.setAuthConfigurationJson("{}");
        installation.setConnectionConfigurationJson("{}");
        installation.setAllowedOriginsJson("[\"" + ORIGIN + "\"]");
        installation.setConfigurationJson("{}");
        installation.setCreatedAt(Instant.parse("2026-10-09T00:00:00Z"));
        installation.setUpdatedAt(installation.getCreatedAt());
        return installation;
    }

    private AIWorkspaceDomainEntity domain() {
        var domain = new AIWorkspaceDomainEntity();
        domain.setId("awd-1");
        domain.setInstallationEntityId("awi-1");
        domain.setHostname("northfield.apps.loomai.test");
        domain.setDomainType(AIWorkspaceDomainType.PLATFORM_DEFAULT);
        domain.setStatus(AIWorkspaceDomainStatus.VERIFIED);
        domain.setVerificationToken("fixture-token");
        domain.setCreatedAt(Instant.parse("2026-10-09T00:00:00Z"));
        domain.setUpdatedAt(domain.getCreatedAt());
        domain.setVerifiedAt(domain.getCreatedAt());
        return domain;
    }

    private AIWorkspaceAssetDescriptor asset(String code) {
        return new AIWorkspaceAssetDescriptor(code, code, "1.0.0", "/api/public/ai-workspace/assets/" + code + ".js",
            code + ".js", "b".repeat(64), "sha384-AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", 10);
    }
}
