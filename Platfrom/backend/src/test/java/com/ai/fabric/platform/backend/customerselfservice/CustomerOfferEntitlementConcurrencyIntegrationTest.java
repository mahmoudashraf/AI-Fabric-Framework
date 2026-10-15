package com.ai.fabric.platform.backend.customerselfservice;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferEntity;
import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerPlacementEvidenceEntity;
import com.ai.fabric.platform.backend.customerselfservice.config.CustomerSelfServiceProperties;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException;
import com.ai.fabric.platform.backend.customerselfservice.repository.*;
import com.ai.fabric.platform.backend.customerselfservice.service.*;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.entity.PlatformUserEntity;
import com.ai.fabric.platform.backend.security.repository.PlatformUserRepository;
import com.ai.fabric.platform.backend.security.service.PlatformCustomerAccessService;
import com.ai.fabric.platform.backend.tenant.entity.PlatformCustomerEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformCustomerRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"}, showSql = false)
@Import({CustomerOfferEntitlementService.class, PlatformCustomerAccessService.class, CustomerOfferCodec.class,
    CustomerOfferEntitlementConcurrencyIntegrationTest.JsonConfiguration.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CustomerOfferEntitlementConcurrencyIntegrationTest {
    @Autowired CustomerOfferEntitlementService service;
    @Autowired CustomerOfferRepository offers;
    @Autowired CustomerOfferEntitlementRepository grants;
    @Autowired CustomerEntitlementCommandRepository commands;
    @Autowired PlatformUserRepository users;
    @Autowired PlatformCustomerRepository customers;
    @Autowired CustomerOfferCodec codec;
    @Autowired PlatformTransactionManager transactionManager;
    @MockBean CustomerReviewedCompositionService composition;
    @MockBean CustomerTargetPlacementService placement;
    @MockBean PlatformAuditService audit;
    private String customerId;
    private String ownerId;
    private String email;
    private String offerId;

    @BeforeEach void setup() {
        String suffix = UUID.randomUUID().toString();
        customerId = "customer-" + suffix; ownerId = "owner-" + suffix; email = suffix + "@test.invalid"; offerId = "offer-" + suffix;
        Instant now = Instant.now();
        var customer = new PlatformCustomerEntity(); customer.setId(customerId); customer.setName("Test customer");
        customer.setSlug(suffix); customer.setStatus("ACTIVE"); customer.setCreatedAt(now); customer.setUpdatedAt(now);
        customers.saveAndFlush(customer);
        var owner = new PlatformUserEntity(); owner.setId(ownerId); owner.setEmail(email); owner.setDisplayName("Test owner");
        owner.setPasswordHash("test-not-a-real-password"); owner.setRole("CUSTOMER_ADMIN"); owner.setStatus("ACTIVE");
        owner.setCustomerId(customerId); owner.setCreatedAt(now); owner.setUpdatedAt(now); users.saveAndFlush(owner);
        var definition = CustomerOfferTestFixtures.definition();
        offers.saveAndFlush(new CustomerOfferEntity(offerId, codec.write(definition), codec.hash(definition), "test-review", "admin", now));
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void concurrentDifferentKeysCannotAcceptTheSameRevisionTwice() throws Exception {
        var invitation = invite();
        var results = concurrentAccept(invitation.id(), "first-key", "second-key");
        assertThat(results).filteredOn(result -> result instanceof EntitlementSummary).hasSize(1);
        assertThat(results).filteredOn(result -> result instanceof CustomerSelfServiceException).hasSize(1);
        var grant = grants.findById(invitation.id()).orElseThrow();
        assertThat(grant.getRevision()).isEqualTo(2);
        assertThat(grant.getStatus()).isEqualTo(EntitlementStatus.ACTIVE);
        assertThat(commands.findAll().stream().filter(command -> invitation.id().equals(command.getEntitlementId()))).hasSize(2);
    }

    @Test void concurrentSameKeyReplaysOnePersistedResultAcrossTransactions() throws Exception {
        var invitation = invite();
        var results = concurrentAccept(invitation.id(), "same-key", "same-key");
        assertThat(results).allMatch(result -> result instanceof EntitlementSummary);
        assertThat(results.get(0)).isEqualTo(results.get(1));
        assertThat(grants.findById(invitation.id()).orElseThrow().getRevision()).isEqualTo(2);
        assertThat(commands.findAll().stream().filter(command -> invitation.id().equals(command.getEntitlementId()))).hasSize(2);
    }

    @Test void placementProbeRejectsAnAmbientEntitlementRowLockButCachedValidationIsAllowed() {
        var invitation = invite();
        var resolver = mock(com.ai.fabric.platform.backend.deployment.service.CoolifyTargetProfileResolver.class);
        var client = mock(com.ai.fabric.platform.backend.deployment.service.CoolifyApiClient.class);
        var evidenceRepository = mock(CustomerPlacementEvidenceRepository.class);
        var profile = CustomerOfferTestFixtures.profile("staging-target", "stage-server");
        when(composition.profileFingerprint(profile)).thenReturn("profile-hash");
        var proof = new CustomerPlacementEvidenceEntity("proof", "staging-target", TargetIntent.CUSTOMER_STAGING,
            "profile-hash", "stage-server", "46.224.145.148", "destination", Instant.now().minusSeconds(1), Instant.now().plusSeconds(59));
        when(evidenceRepository.findFirstByTargetProfileIdAndIntentAndProfileFingerprintOrderByCheckedAtDesc(
            "staging-target", TargetIntent.CUSTOMER_STAGING, "profile-hash")).thenReturn(java.util.Optional.of(proof));
        var properties = new CustomerSelfServiceProperties(java.util.Map.of(
            TargetIntent.CUSTOMER_STAGING, new CustomerSelfServiceProperties.TrustedServer(
                "stage-server", "46.224.145.148", "stage.internal"),
            TargetIntent.CUSTOMER_PRODUCTION, new CustomerSelfServiceProperties.TrustedServer(
                "prod-server", "46.225.162.106", "prod.internal")), 60);
        var target = new CustomerTargetPlacementService(properties, resolver, client, composition, codec, evidenceRepository);
        var factory = new org.springframework.aop.framework.ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(transactionManager,
            new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
        var proxy = (CustomerTargetPlacementService) factory.getProxy();
        var transaction = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            grants.findByIdForUpdate(invitation.id()).orElseThrow();
            proxy.probeAndRecord(profile, TargetIntent.CUSTOMER_STAGING);
        })).isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        transaction.executeWithoutResult(status -> {
            grants.findByIdForUpdate(invitation.id()).orElseThrow();
            assertThat(proxy.requireFreshEvidence(profile, TargetIntent.CUSTOMER_STAGING)).isEqualTo(proof);
        });
        verifyNoInteractions(resolver, client);
        verify(evidenceRepository, never()).saveAndFlush(any());
    }

    private EntitlementSummary invite() {
        CustomerOfferTestFixtures.authenticate("admin-key", PlatformRole.PLATFORM_ADMIN, "API_KEY");
        return service.invite(offerId, new InviteRequest(customerId, ownerId, "test-invitation", Instant.now().plusSeconds(3600), 0, "invite", "test grant"));
    }

    private java.util.List<Object> concurrentAccept(String entitlementId, String firstKey, String secondKey) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Object> first = executor.submit(() -> accept(start, entitlementId, firstKey));
            Future<Object> second = executor.submit(() -> accept(start, entitlementId, secondKey));
            start.countDown();
            return java.util.List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
        }
    }

    private Object accept(CountDownLatch start, String entitlementId, String key) throws InterruptedException {
        start.await();
        CustomerOfferTestFixtures.authenticate(email, PlatformRole.CUSTOMER_ADMIN, "SESSION");
        try { return service.accept(entitlementId, new EntitlementCommand(1, key, "test acceptance")); }
        catch (CustomerSelfServiceException exception) { return exception; }
        finally { SecurityContextHolder.clearContext(); }
    }

    @org.springframework.boot.test.context.TestConfiguration
    static class JsonConfiguration {
        @org.springframework.context.annotation.Bean
        com.fasterxml.jackson.databind.ObjectMapper objectMapper() {
            return new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        }
    }
}
