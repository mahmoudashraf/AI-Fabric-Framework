package com.ai.fabric.platform.backend.support;

import com.ai.fabric.platform.backend.aiworkspace.repository.AIWorkspaceInstallationRepository;
import com.ai.fabric.platform.backend.aiworkspace.service.AIWorkspaceReadinessService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.repository.*;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.security.*;
import com.ai.fabric.platform.backend.security.entity.*;
import com.ai.fabric.platform.backend.security.repository.*;
import com.ai.fabric.platform.backend.security.service.PlatformCustomerAccessService;
import com.ai.fabric.platform.backend.tenant.entity.PlatformCustomerEntity;
import com.ai.fabric.platform.backend.tenant.repository.*;
import com.ai.fabric.platform.backend.vectorization.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.*;

import static com.ai.fabric.platform.backend.support.SupportContracts.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SupportFixture {
    final JdbcTemplate jdbc;
    final DataSource dataSource;
    final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    final PlatformUserRepository users = mock(PlatformUserRepository.class);
    final PlatformCustomerRepository customers = mock(PlatformCustomerRepository.class);
    final PlatformCustomerIdentityLinkRepository links = mock(PlatformCustomerIdentityLinkRepository.class);
    final DeploymentRepository deployments = mock(DeploymentRepository.class);
    final DeploymentTargetProfileRepository targets = mock(DeploymentTargetProfileRepository.class);
    final DeploymentReleaseRepository releases = mock(DeploymentReleaseRepository.class);
    final AIWorkspaceInstallationRepository apps = mock(AIWorkspaceInstallationRepository.class);
    final PlatformConsumerRepository consumers = mock(PlatformConsumerRepository.class);
    final AIWorkspaceReadinessService readiness = mock(AIWorkspaceReadinessService.class);
    final VectorizationSourceConnectionRepository connections = mock(VectorizationSourceConnectionRepository.class);
    final VectorizationPlanRepository plans = mock(VectorizationPlanRepository.class);
    final VectorizationRunRepository runs = mock(VectorizationRunRepository.class);
    final PlatformSecretService secrets = mock(PlatformSecretService.class);
    final Map<String, PlatformUserEntity> directory = new HashMap<>();
    final Map<String, PlatformCustomerIdentityLinkEntity> identityLinks = new HashMap<>();
    final MockEnvironment env = new MockEnvironment().withProperty("platform.support.enabled", "true")
        .withProperty("platform.support.customer-id", "internal-owner").withProperty("platform.support.tenant-id", "internal-tenant")
        .withProperty("platform.support.deployment-id", "support-runtime").withProperty("platform.support.target-profile-id", "staging-target")
        .withProperty("platform.support.assertion-issuer", "loomai-support").withProperty("platform.support.assertion-audience", "support-runtime")
        .withProperty("platform.support.diagnostics.service-key-secret-name", "PLATFORM_MANAGED_SUPPORT_DIAGNOSTICS");
    final SupportSettings settings;
    final SupportRequesterService requesters;
    final SupportResourceService resources;
    final SupportCaseStore store;
    final SupportCaseService service;
    final SupportDiagnosticAuthentication authentication;
    final SupportDiagnosticService diagnostics;

    SupportFixture() { this(new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "")); }
    SupportFixture(DataSource source) {
        dataSource = source; jdbc = new JdbcTemplate(source);
        jdbc.execute("create table platform_customers(id varchar(64) primary key)");
        jdbc.execute("create table platform_users(id varchar(64) primary key)");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V218__internal_support_case_registry.sql")).execute(source);
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V219__support_desk_context_receipts.sql")).execute(source);
        when(users.findById(anyString())).thenAnswer(a -> Optional.ofNullable(directory.get(a.getArgument(0))));
        when(links.findByPlatformUserId(anyString())).thenAnswer(a -> Optional.ofNullable(identityLinks.get(a.getArgument(0))));
        addUser("alice", "customer-a"); addUser("bob", "customer-a"); addUser("eve", "customer-b");
        addLink("alice"); addLink("bob"); addLink("eve");
        var internal = deployment("support-runtime", "internal-owner", "internal-tenant");
        when(deployments.findById(internal.getId())).thenReturn(Optional.of(internal));
        when(targets.existsById("staging-target")).thenReturn(true);
        settings = new SupportSettings(env, deployments, targets);
        requesters = new SupportRequesterService(users, links, new PlatformCustomerAccessService(users, customers));
        resources = new SupportResourceService(deployments, releases, apps, consumers, readiness, connections, plans, runs);
        store = new SupportCaseStore(jdbc, json);
        service = transactional(new SupportCaseService(store, requesters, resources, settings));
        authentication = new SupportDiagnosticAuthentication(settings, secrets);
        diagnostics = transactional(new SupportDiagnosticService(authentication, store, requesters, resources));
        when(secrets.resolveSecret("PLATFORM_MANAGED_SUPPORT_DIAGNOSTICS")).thenReturn("dedicated-diagnostic-test-key");
        login("alice");
    }
    void addUser(String id, String customerId) {
        if (jdbc.queryForObject("select count(*) from platform_customers where id=?", Integer.class, customerId) == 0) {
            jdbc.update("insert into platform_customers(id) values (?)", customerId);
        }
        jdbc.update("insert into platform_users(id) values (?)", id);
        var user = new PlatformUserEntity();
        user.setId(id); user.setCustomerId(customerId); user.setEmail(id + "@example.test");
        user.setRole("CUSTOMER_ADMIN"); user.setStatus("ACTIVE"); user.setDisplayName(id);
        directory.put(id, user);
        var customer = new PlatformCustomerEntity(); customer.setId(customerId); customer.setName(customerId); customer.setStatus("ACTIVE");
        when(customers.findById(customerId)).thenReturn(Optional.of(customer));
    }
    void addLink(String id) {
        var link = new PlatformCustomerIdentityLinkEntity(); link.setId("link-" + id); link.setPlatformUserId(id);
        link.setIssuer("https://identity.example.test/auth/v1"); link.setSubject("subject-" + id);
        identityLinks.put(id, link);
    }
    void login(String userId) {
        var user = directory.get(userId);
        var principal = new PlatformPrincipal(user.getEmail(), PlatformRole.CUSTOMER_ADMIN, userId, "SUPABASE", userId);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }
    CaseView create(boolean consent, ResourceRef... refs) {
        return service.create(new CreateCase("Help with setup", "The app is not ready.", "setup", List.of(refs), consent), UUID.randomUUID().toString());
    }
    org.springframework.http.HttpHeaders headers(OwnedCase c) {
        var headers = new org.springframework.http.HttpHeaders();
        headers.set(SupportDiagnosticAuthentication.SERVICE_KEY_HEADER, "dedicated-diagnostic-test-key");
        headers.set("X-AIFABRIC-AUTH-SUBJECT-TYPE", "END_USER");
        headers.set("X-AIFABRIC-AUTH-MODE", "PRIVATE_RUNTIME_BACKEND_MEDIATED");
        headers.set("X-AIFABRIC-AUTH-CALLER-TYPE", "TRUSTED_BACKEND");
        headers.set("X-AIFABRIC-AUTH-DEPLOYMENT-ID", c.deploymentId());
        headers.set("X-AIFABRIC-AUTH-CUSTOMER-ID", c.customerId());
        headers.set("X-AIFABRIC-AUTH-TENANT-ID", c.tenantId());
        headers.set("X-AIFABRIC-AUTH-ISSUER", c.assertionIssuer());
        headers.set("X-AIFABRIC-AUTH-AUDIENCES", c.assertionAudience());
        headers.set("X-AIFABRIC-AUTH-SCOPES", "chat:messages," + SupportDiagnosticAuthentication.DIAGNOSTIC_SCOPE);
        headers.set("X-AIFABRIC-AUTH-EXPIRES-AT", Instant.now().plusSeconds(180).toString());
        headers.set("X-AIFABRIC-AUTH-SUBJECT-ID", c.principalId());
        headers.set("X-AIFABRIC-CONVERSATION-ID", c.conversationId());
        return headers;
    }
    static DeploymentEntity deployment(String id, String customer, String tenant) {
        var d = new DeploymentEntity(); d.setId(id); d.setCustomerId(customer); d.setTenantId(tenant); d.setStatus("ACTIVE");
        return d;
    }
    @SuppressWarnings("unchecked")
    <T> T transactional(T target) {
        var factory = new ProxyFactory(target); factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(dataSource), new AnnotationTransactionAttributeSource()));
        return (T) factory.getProxy();
    }
}
