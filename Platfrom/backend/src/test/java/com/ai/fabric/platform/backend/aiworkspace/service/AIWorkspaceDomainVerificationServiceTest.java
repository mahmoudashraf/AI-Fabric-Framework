package com.ai.fabric.platform.backend.aiworkspace.service;

import com.ai.fabric.platform.backend.aiworkspace.entity.AIWorkspaceDomainEntity;
import com.ai.fabric.platform.backend.aiworkspace.entity.AIWorkspaceInstallationEntity;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceConnectionMode;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceDeliveryMode;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceDomainStatus;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceDomainType;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceInstallationStatus;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceInteractionPolicy;
import com.ai.fabric.platform.backend.aiworkspace.repository.AIWorkspaceDomainRepository;
import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.PlatformAIWorkspaceProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AIWorkspaceDomainVerificationServiceTest {
    private static final String HOST = "assistant.customer.example";
    private static final Instant CREATED = Instant.parse("2026-10-30T00:00:00Z");
    private LocalContainerEntityManagerFactoryBean factory;
    private JpaTransactionManager transactions;
    private TransactionTemplate transaction;
    private JdbcTemplate jdbc;
    private EntityManager entities;
    private AIWorkspaceDomainRepository domains;
    private AIWorkspaceDomainVerifier verifier;
    private AIWorkspaceDomainRouteService routes;
    private PlatformAuditService audit;
    private AIWorkspaceDomainVerificationService worker;

    @BeforeAll
    void createJpaFixture() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:workspace-domain-completion-" + UUID.randomUUID()
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
        entities = SharedEntityManagerCreator.createSharedEntityManager(factory.getObject());
        domains = new JpaRepositoryFactory(entities).getRepository(AIWorkspaceDomainRepository.class);
    }

    @BeforeEach
    void setUp() {
        verifier = mock(AIWorkspaceDomainVerifier.class);
        routes = mock(AIWorkspaceDomainRouteService.class);
        audit = mock(PlatformAuditService.class);
        worker = new AIWorkspaceDomainVerificationService(domains, entities, verifier, routes, audit, transactions);
        transaction.executeWithoutResult(ignored -> {
            domains.deleteAllInBatch();
            jdbc.update("DELETE FROM ai_workspace_installations");
            entities.persist(installation());
            domains.saveAndFlush(domain("awd-1", HOST, AIWorkspaceDomainStatus.ROUTING_PENDING));
        });
        when(routes.isApplied(HOST)).thenReturn(true);
        when(verifier.verifyOwnership(HOST, "token")).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return verified();
        });
        when(verifier.verifyRoute(HOST)).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return verified();
        });
    }

    @AfterEach
    void stopWorker() { worker.close(); }

    @AfterAll
    void destroyJpaFixture() {
        factory.destroy();
        jdbc.execute("SHUTDOWN");
    }

    @Test
    void explicitVerifyThenAppliedRouteCompletesAutomaticallyWithoutActivatingApp() {
        changeDomain(domain -> domain.setStatus(AIWorkspaceDomainStatus.PENDING));
        when(verifier.verifyOwnership(HOST, "token")).thenReturn(verified());
        when(routes.isApplied(HOST)).thenReturn(false);
        var explicit = new AIWorkspaceDomainService(domains, new PlatformAIWorkspaceProperties(
            Path.of("."), false, 120, Duration.ofSeconds(60), null, null, "apps.loomai.test", "https://engage.loomai.test"),
            verifier, routes);
        var summary = transaction.execute(ignored -> explicit.verify("awi-1", HOST));
        assertThat(summary.status()).isEqualTo("ROUTING_PENDING");
        verify(verifier, never()).verifyRoute(any());

        when(routes.isApplied(HOST)).thenReturn(true);
        when(verifier.verifyOwnership(HOST, "token")).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return verified();
        });
        worker.runNext();

        assertThat(currentDomain().getStatus()).isEqualTo(AIWorkspaceDomainStatus.VERIFIED);
        assertThat(currentDomain().getVerifiedAt()).isNotNull();
        assertThat(currentDomain().getLastVerificationError()).isNull();
        assertThat(currentInstallation().getStatus()).isEqualTo(AIWorkspaceInstallationStatus.DRAFT);
        assertThat(currentInstallation().getActivatedAt()).isNull();
        verify(verifier, times(3)).verifyOwnership(HOST, "token");
        verify(audit).record(eq("AI_WORKSPACE_DOMAIN_VERIFICATION_COMPLETED"), eq("AI_WORKSPACE_INSTALLATION"),
            eq("awi-1"), eq(Map.of("hostname", HOST, "status", "VERIFIED")));
        worker.runNext();
        verify(verifier, times(1)).verifyRoute(HOST);
        verifyNoMoreInteractions(audit);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "VERIFIED", "DISABLED"})
    void doesNotInitiateOrReverifyDomainsWithoutRoutingPendingIntent(String state) {
        changeDomain(domain -> domain.setStatus(AIWorkspaceDomainStatus.valueOf(state)));
        worker.runNext();
        assertThat(currentDomain().getStatus().name()).isEqualTo(state);
        verifyNoInteractions(routes, verifier, audit);
    }

    @Test
    void missingOwnershipOnExplicitVerifyDoesNotAuthorizeAutomaticRetry() {
        changeDomain(domain -> domain.setStatus(AIWorkspaceDomainStatus.PENDING));
        when(verifier.verifyOwnership(HOST, "token")).thenReturn(notVerified("TXT missing"));
        var explicit = new AIWorkspaceDomainService(domains, new PlatformAIWorkspaceProperties(
            Path.of("."), false, 120, Duration.ofSeconds(60), null, null, "apps.loomai.test", "https://engage.loomai.test"),
            verifier, routes);
        transaction.execute(ignored -> explicit.verify("awi-1", HOST));
        clearInvocations(verifier);
        worker.runNext();
        assertThat(currentDomain().getStatus()).isEqualTo(AIWorkspaceDomainStatus.PENDING);
        verifyNoInteractions(verifier, routes, audit);
    }

    @Test
    void unappliedRouteNeverTriggersNetworkProbeAndAttemptsStayFair() {
        transaction.executeWithoutResult(ignored -> domains.saveAndFlush(domain("awd-2", "other.customer.example",
            AIWorkspaceDomainStatus.ROUTING_PENDING)));
        when(routes.isApplied(HOST)).thenReturn(false);
        worker.runNext();
        assertThat(currentDomain().getStatus()).isEqualTo(AIWorkspaceDomainStatus.ROUTING_PENDING);
        assertThat(currentDomain().getVerifiedAt()).isNull();
        var next = transaction.execute(ignored -> domains.findVerificationCandidates(AIWorkspaceDomainStatus.ROUTING_PENDING,
            AIWorkspaceDeliveryMode.STANDALONE, AIWorkspaceInstallationStatus.DISABLED, PageRequest.of(0, 1)));
        assertThat(next).extracting(AIWorkspaceDomainEntity::getId).containsExactly("awd-2");
        verifyNoInteractions(verifier, audit);
        verify(routes, times(1)).isApplied(HOST);
    }

    @Test
    void platformDefaultCompletionDoesNotRequireCustomerTxt() {
        changeDomain(domain -> domain.setDomainType(AIWorkspaceDomainType.PLATFORM_DEFAULT));
        worker.runNext();
        assertThat(currentDomain().getStatus()).isEqualTo(AIWorkspaceDomainStatus.VERIFIED);
        verify(verifier, never()).verifyOwnership(any(), any());
        verify(verifier).verifyRoute(HOST);
    }

    @Test
    void ownershipLossRevokesRoutingAuthorizationWithoutRouteProbe() {
        when(verifier.verifyOwnership(HOST, "token")).thenReturn(notVerified("TXT no longer matches"));
        worker.runNext();
        assertThat(currentDomain().getStatus()).isEqualTo(AIWorkspaceDomainStatus.PENDING);
        assertThat(currentDomain().getVerifiedAt()).isNull();
        verify(verifier, never()).verifyRoute(any());
        verifyNoInteractions(audit);
        worker.runNext();
        verify(verifier, times(1)).verifyOwnership(HOST, "token");
    }

    @Test
    void ownershipIsRecheckedAfterTlsBeforeCompleting() {
        when(verifier.verifyOwnership(HOST, "token")).thenReturn(verified(), notVerified("TXT removed during probe"));
        worker.runNext();
        assertThat(currentDomain().getStatus()).isEqualTo(AIWorkspaceDomainStatus.PENDING);
        assertThat(currentDomain().getLastVerificationError()).isEqualTo("TXT removed during probe");
        verify(verifier).verifyRoute(HOST);
        verifyNoInteractions(audit);
    }

    @ParameterizedTest
    @ValueSource(strings = {"The hostname must resolve only to public addresses.",
        "Every resolved address must serve a direct healthy TLS workspace route."})
    void verifierProtectionFailuresStayPendingAndRetryWithoutAuditNoise(String error) {
        when(verifier.verifyRoute(HOST)).thenReturn(notVerified(error), verified());
        worker.runNext();
        assertThat(currentDomain().getStatus()).isEqualTo(AIWorkspaceDomainStatus.ROUTING_PENDING);
        assertThat(currentDomain().getLastVerificationError()).isEqualTo(error);
        verifyNoInteractions(audit);
        worker.runNext();
        assertThat(currentDomain().getStatus()).isEqualTo(AIWorkspaceDomainStatus.VERIFIED);
        verify(audit, times(1)).record(any(), any(), any(), any());
    }

    @Test
    void routeBindingChangeDuringProbeCannotMarkVerified() {
        when(routes.isApplied(HOST)).thenReturn(true, false);
        worker.runNext();
        assertThat(currentDomain().getStatus()).isEqualTo(AIWorkspaceDomainStatus.ROUTING_PENDING);
        assertThat(currentDomain().getVerifiedAt()).isNull();
        verifyNoInteractions(audit);
    }

    @ParameterizedTest
    @ValueSource(strings = {"disabled", "deleted", "embedded"})
    void unavailableInstallationsAreExcludedBeforeProbing(String change) {
        mutateInstallation(change);
        worker.runNext();
        verifyNoInteractions(routes, verifier, audit);
    }

    @ParameterizedTest
    @ValueSource(strings = {"disabled", "deleted", "embedded", "edited"})
    void installationChangeDuringProbeDiscardsLateSuccess(String change) {
        when(verifier.verifyRoute(HOST)).thenAnswer(call -> {
            mutateInstallation(change);
            return verified();
        });
        worker.runNext();
        assertThat(currentDomain().getStatus()).isEqualTo(AIWorkspaceDomainStatus.ROUTING_PENDING);
        assertThat(currentDomain().getVerifiedAt()).isNull();
        verifyNoInteractions(audit);
    }

    @ParameterizedTest
    @ValueSource(strings = {"disabled", "pending", "deleted", "token", "hostname", "type", "edited"})
    void domainChangeDuringProbeIsNotOverwritten(String change) {
        when(verifier.verifyRoute(HOST)).thenAnswer(call -> {
            transaction.executeWithoutResult(ignored -> {
                var domain = domains.findById("awd-1").orElseThrow();
                switch (change) {
                    case "disabled" -> domain.setStatus(AIWorkspaceDomainStatus.DISABLED);
                    case "pending" -> domain.setStatus(AIWorkspaceDomainStatus.PENDING);
                    case "deleted" -> domains.delete(domain);
                    case "token" -> domain.setVerificationToken("new-token");
                    case "hostname" -> domain.setHostname("replacement.customer.example");
                    case "type" -> domain.setDomainType(AIWorkspaceDomainType.PLATFORM_DEFAULT);
                    case "edited" -> domain.setUpdatedAt(CREATED.plusSeconds(1));
                    default -> throw new AssertionError(change);
                }
                domains.flush();
            });
            return verified();
        });
        worker.runNext();
        if (change.equals("deleted")) assertThat(domains.findById("awd-1")).isEmpty();
        else assertThat(domains.findById("awd-1")).hasValueSatisfying(domain -> {
                assertThat(domain.getStatus()).isNotEqualTo(AIWorkspaceDomainStatus.VERIFIED);
                assertThat(domain.getVerifiedAt()).isNull();
            });
        verifyNoInteractions(audit);
    }

    @Test
    void unexpectedProbeFailureDoesNotPersistItsExceptionOrBlockNextAttempt() {
        when(verifier.verifyRoute(HOST)).thenThrow(new IllegalStateException("SECRET"));
        worker.runNext();
        assertThat(currentDomain().getLastVerificationError()).doesNotContain("SECRET").contains("retried");
        assertThat(currentDomain().getStatus()).isEqualTo(AIWorkspaceDomainStatus.ROUTING_PENDING);
        verifyNoInteractions(audit);
        doReturn(verified()).when(verifier).verifyRoute(HOST);
        worker.runNext();
        assertThat(currentDomain().getStatus()).isEqualTo(AIWorkspaceDomainStatus.VERIFIED);
    }

    @Test
    void probeDeadlineDoesNotQueueOrAcceptLateSuccessfulResult() throws Exception {
        worker.close();
        var probes = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new SynchronousQueue<>());
        worker = new AIWorkspaceDomainVerificationService(domains, entities, verifier, routes, audit, transactions,
            probes, Duration.ofMillis(50));
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var finished = new CountDownLatch(1);
        when(verifier.verifyRoute(HOST)).thenAnswer(call -> {
            entered.countDown();
            boolean released = false;
            while (!released) {
                try { released = release.await(5, TimeUnit.SECONDS); }
                catch (InterruptedException ignored) { /* Simulate DNS ignoring interruption. */ }
            }
            finished.countDown();
            return verified();
        });
        try {
            worker.runNext();
            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(currentDomain().getLastVerificationError()).contains("timed out");
            worker.runNext();
            assertThat(currentDomain().getStatus()).isEqualTo(AIWorkspaceDomainStatus.ROUTING_PENDING);
            assertThat(probes.getQueue()).isEmpty();
            verify(verifier, times(1)).verifyRoute(HOST);
        } finally {
            release.countDown();
            assertThat(finished.await(1, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(currentDomain().getStatus()).isEqualTo(AIWorkspaceDomainStatus.ROUTING_PENDING);
        verifyNoInteractions(audit);
    }

    @Test
    void ambientDatabaseTransactionCannotContainNetworkProbes() {
        assertThatThrownBy(() -> transaction.executeWithoutResult(ignored -> worker.runNext()))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("cannot run in a database transaction");
        verifyNoInteractions(routes, verifier, audit);
    }

    private AIWorkspaceDomainVerifier.Verification verified() {
        return new AIWorkspaceDomainVerifier.Verification(true, null);
    }

    private AIWorkspaceDomainVerifier.Verification notVerified(String error) {
        return new AIWorkspaceDomainVerifier.Verification(false, error);
    }

    private AIWorkspaceDomainEntity currentDomain() { return domains.findById("awd-1").orElseThrow(); }

    private AIWorkspaceInstallationEntity currentInstallation() {
        return transaction.execute(ignored -> entities.find(AIWorkspaceInstallationEntity.class, "awi-1"));
    }

    private void changeDomain(java.util.function.Consumer<AIWorkspaceDomainEntity> change) {
        transaction.executeWithoutResult(ignored -> {
            var domain = domains.findById("awd-1").orElseThrow();
            change.accept(domain);
            domains.saveAndFlush(domain);
        });
    }

    private void mutateInstallation(String change) {
        transaction.executeWithoutResult(ignored -> {
            var installation = entities.find(AIWorkspaceInstallationEntity.class, "awi-1");
            switch (change) {
                case "disabled" -> installation.setStatus(AIWorkspaceInstallationStatus.DISABLED);
                case "deleted" -> entities.remove(installation);
                case "embedded" -> installation.setDeliveryMode(AIWorkspaceDeliveryMode.EMBEDDED);
                case "edited" -> installation.setDisplayName("Edited during probe");
                default -> throw new AssertionError(change);
            }
            entities.flush();
        });
    }

    private AIWorkspaceDomainEntity domain(String id, String host, AIWorkspaceDomainStatus status) {
        var domain = new AIWorkspaceDomainEntity();
        domain.setId(id);
        domain.setInstallationEntityId("awi-1");
        domain.setHostname(host);
        domain.setDomainType(AIWorkspaceDomainType.CUSTOMER_CUSTOM);
        domain.setStatus(status);
        domain.setVerificationToken("token");
        domain.setCreatedAt(CREATED);
        domain.setUpdatedAt(CREATED);
        return domain;
    }

    private AIWorkspaceInstallationEntity installation() {
        var installation = new AIWorkspaceInstallationEntity();
        installation.setId("awi-1");
        installation.setInstallationId("awi_pub_fixture");
        installation.setCustomerId("customer-1");
        installation.setConsumerEntityId("consumer-1");
        installation.setDisplayName("Customer workspace");
        installation.setStatus(AIWorkspaceInstallationStatus.DRAFT);
        installation.setExperiencePackCode("generic");
        installation.setExperiencePackVersion("1.0.0");
        installation.setConnectionMode(AIWorkspaceConnectionMode.PUBLIC_RUNTIME_ANONYMOUS);
        installation.setConnectionProfileCode("public-runtime-anonymous");
        installation.setConnectionProfileVersion("1.0.0");
        installation.setDeliveryMode(AIWorkspaceDeliveryMode.STANDALONE);
        installation.setAppSlug("fixture");
        installation.setInteractionPolicy(AIWorkspaceInteractionPolicy.PUBLIC);
        installation.setAuthProfileCode("none");
        installation.setAuthProfileVersion("1.0.0");
        installation.setAuthConfigurationJson("{}");
        installation.setConnectionConfigurationJson("{}");
        installation.setAllowedOriginsJson("[]");
        installation.setConfigurationJson("{}");
        installation.setCreatedAt(CREATED);
        installation.setUpdatedAt(CREATED);
        return installation;
    }
}
