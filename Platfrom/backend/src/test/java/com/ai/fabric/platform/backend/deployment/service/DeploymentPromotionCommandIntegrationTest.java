package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.model.DeploymentPracticalPromotionSummary;
import com.ai.fabric.platform.backend.security.PlatformTestSecurity;
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import com.ai.fabric.platform.backend.tenant.model.CreatePlatformConsumerRequest;
import com.ai.fabric.platform.backend.tenant.model.CreatePlatformCustomerRequest;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
import com.ai.fabric.platform.backend.tenant.service.PlatformCustomerConsumerService;
import com.ai.fabric.platform.backend.tenant.service.PlatformCustomerTenantService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class DeploymentPromotionCommandIntegrationTest {
    @Autowired private DeploymentPromotionCommandService commands;
    @Autowired private PlatformCustomerTenantService customers;
    @Autowired private PlatformCustomerConsumerService consumers;
    @Autowired private PlatformConsumerRepository repository;
    private String customerId;
    private String consumerId;
    private final AtomicInteger effects = new AtomicInteger();

    @BeforeEach void setUp() {
        PlatformTestSecurity.authenticateAsPlatformAdmin();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        customerId = customers.createCustomer(new CreatePlatformCustomerRequest("Promotion " + suffix, null)).id();
        consumerId = "promotion-" + suffix;
        consumers.createConsumer(customerId, new CreatePlatformConsumerRequest(consumerId, "Promotion consumer", null, null, null));
    }
    @AfterEach void clear() { PlatformTestSecurity.clearAuthentication(); }

    @Test void replayReturnsOriginalWholeResultWithoutRepeatingLifecycleEffects() {
        var first = execute("first", 0L, "release-a");
        execute("later", 1L, "release-b");
        assertThat(execute("first", 0L, "release-a")).isEqualTo(first);
        assertThat(effects).hasValue(2);
        assertThat(current().getBindingRevision()).isEqualTo(2);
    }

    @Test void changedPayloadOrStaleRevisionCannotRepeatEffects() {
        execute("same-key", 0L, "release-a");
        assertThatThrownBy(() -> execute("same-key", 0L, "release-b")).hasMessageContaining("another command");
        assertThatThrownBy(() -> execute("stale", 0L, "release-a")).hasMessageContaining("changed");
        assertThat(effects).hasValue(1);
    }

    @Test void distinctOperationKindsShareOneProtectedKeyNamespace() {
        execute("same-key", 0L, "release-a");
        assertThatThrownBy(() -> commands.execute(customerId, "dep-test", consumerId, "ROLLBACK", "same-key", 0L,
            Map.of("releaseId", "release-a"), consumer -> result("unexpected"))).hasMessageContaining("another command");
        assertThat(effects).hasValue(1);
    }

    @Test void interruptedTransactionDoesNotPersistHalfACommand() {
        assertThatThrownBy(() -> commands.execute(customerId, "dep-test", consumerId, "ACTIVATE", "retry", 0L,
            Map.of("releaseId", "release-a"), consumer -> {
                consumer.setBindingRevision(1); repository.saveAndFlush(consumer);
                throw new IllegalStateException("Interrupted before acceptance");
            })).hasMessageContaining("Interrupted");
        assertThat(current().getBindingRevision()).isZero();
        assertThat(execute("retry", 0L, "release-a").productionReleaseId()).isEqualTo("release-a");
    }

    @Test void duplicateConcurrentSubmitMutatesOnlyOnce() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var attempts = List.of(1, 2).stream().map(i -> executor.submit(() -> {
                PlatformTestSecurity.authenticateAsPlatformAdmin();
                try {
                    if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
                    return execute("concurrent", 0L, "release-a");
                } finally { PlatformTestSecurity.clearAuthentication(); }
            })).toList();
            start.countDown();
            assertThat(attempts.get(0).get(20, TimeUnit.SECONDS)).isEqualTo(attempts.get(1).get(20, TimeUnit.SECONDS));
        }
        assertThat(effects).hasValue(1);
        assertThat(current().getBindingRevision()).isEqualTo(1);
    }

    private DeploymentPracticalPromotionSummary execute(String key, Long revision, String releaseId) {
        return commands.execute(customerId, "dep-test", consumerId, "ACTIVATE", key, revision,
            Map.of("releaseId", releaseId), consumer -> {
                effects.incrementAndGet(); consumer.setBindingRevision(consumer.getBindingRevision() + 1);
                repository.saveAndFlush(consumer); return result(releaseId);
            });
    }
    private PlatformConsumerEntity current() { return repository.findByCustomerIdAndConsumerIdIgnoreCase(customerId, consumerId).orElseThrow(); }
    private DeploymentPracticalPromotionSummary result(String releaseId) {
        return new DeploymentPracticalPromotionSummary("TEST_COMMAND_ACCEPTED", "Command primitive verification", "dep-test",
            "version-test", "target-staging", "target-production", "release-staging", "APPLIED_VERIFIED",
            releaseId, "APPLIED_VERIFIED", null, List.of(), "test-candidate");
    }
}
