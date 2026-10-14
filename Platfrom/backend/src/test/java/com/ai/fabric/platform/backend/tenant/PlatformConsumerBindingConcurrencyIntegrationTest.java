package com.ai.fabric.platform.backend.tenant;

import com.ai.fabric.platform.backend.deployment.model.CreateDeploymentRequest;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.ai.fabric.platform.backend.security.PlatformTestSecurity;
import com.ai.fabric.platform.backend.tenant.model.CreatePlatformConsumerRequest;
import com.ai.fabric.platform.backend.tenant.model.CreatePlatformCustomerRequest;
import com.ai.fabric.platform.backend.tenant.model.PlatformConsumerSummary;
import com.ai.fabric.platform.backend.tenant.model.UpdatePlatformConsumerBindingRequest;
import com.ai.fabric.platform.backend.tenant.service.PlatformCustomerConsumerService;
import com.ai.fabric.platform.backend.tenant.service.PlatformCustomerTenantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class PlatformConsumerBindingConcurrencyIntegrationTest {
    @Autowired private PlatformCustomerTenantService customers;
    @Autowired private PlatformCustomerConsumerService consumers;
    @Autowired private DeploymentService deployments;
    private String customerId;
    private String consumerId;
    private String firstDeploymentId;
    private String secondDeploymentId;

    @BeforeEach
    void setUp() {
        PlatformTestSecurity.authenticateAsPlatformAdmin();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        customerId = customers.createCustomer(new CreatePlatformCustomerRequest("Binding " + suffix, "Concurrency verification")).id();
        consumerId = "binding-" + suffix;
        consumers.createConsumer(customerId, new CreatePlatformConsumerRequest(consumerId, "Binding consumer", null, null, null));
        firstDeploymentId = deployment("First " + suffix);
        secondDeploymentId = deployment("Second " + suffix);
    }

    @AfterEach
    void clearAuthentication() { PlatformTestSecurity.clearAuthentication(); }

    @Test
    void missingRevisionOrOperationKeyIsRejected() {
        assertThatThrownBy(() -> bind(firstDeploymentId, null, "missing-revision"))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("expectedBindingRevision");
        assertThatThrownBy(() -> bind(firstDeploymentId, 0L, null))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("operationKey");
    }

    @Test
    void staleBindingCannotOverwriteNewAssignment() {
        assertThat(bind(firstDeploymentId, 0L, "first").bindingRevision()).isEqualTo(1);
        assertThatThrownBy(() -> bind(secondDeploymentId, 0L, "stale"))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("revision changed");
        assertThat(current().boundDeploymentId()).isEqualTo(firstDeploymentId);
    }

    @Test
    void duplicateCommandReturnsPersistedResultEvenAfterAnotherBindingChange() {
        var first = bind(firstDeploymentId, 0L, "first");
        bind(null, 1L, "unbind");
        var replay = bind(firstDeploymentId, 0L, "first");
        assertThat(replay).isEqualTo(first);
        assertThat(current().bindingRevision()).isEqualTo(2);
        assertThat(current().boundDeploymentId()).isNull();
        assertThat(consumers.listBindingHistory(customerId, consumerId)).hasSize(2);
    }

    @Test
    void sameKeyDifferentPayloadCannotMutateAssignment() {
        bind(firstDeploymentId, 0L, "same-key");
        assertThatThrownBy(() -> bind(secondDeploymentId, 0L, "same-key"))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("different binding command");
        assertThat(current().boundDeploymentId()).isEqualTo(firstDeploymentId);
    }

    @Test
    void noChangeCommandIsStillDurablyIdempotent() {
        var result = bind(null, 0L, "no-change");
        assertThat(result.bindingRevision()).isZero();
        assertThat(bind(null, 0L, "no-change")).isEqualTo(result);
        assertThat(consumers.listBindingHistory(customerId, consumerId)).hasSize(1);
    }

    @Test
    void historyRecordsCompleteBeforeAndAfterIdentity() {
        bind(firstDeploymentId, 0L, "first");
        bind(secondDeploymentId, 1L, "second");
        assertThat(consumers.listBindingHistory(customerId, consumerId))
            .anySatisfy(history -> {
                assertThat(history.operationKey()).isEqualTo("second");
                assertThat(history.fromDeploymentId()).isEqualTo(firstDeploymentId);
                assertThat(history.toDeploymentId()).isEqualTo(secondDeploymentId);
                assertThat(history.fromBindingRevision()).isEqualTo(1);
                assertThat(history.toBindingRevision()).isEqualTo(2);
            });
    }

    @Test
    void concurrentRebindsHaveExactlyOneWinner() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var attempts = List.of(firstDeploymentId, secondDeploymentId).stream()
                .map(deployment -> executor.submit(() -> {
                    PlatformTestSecurity.authenticateAsPlatformAdmin();
                    try {
                        if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Start latch timed out.");
                        bind(deployment, 0L, "concurrent-" + deployment);
                        return "ACCEPTED";
                    } catch (ResponseStatusException error) {
                        assertThat(error.getStatusCode().value()).isEqualTo(409);
                        return "CONFLICT";
                    } finally {
                        PlatformTestSecurity.clearAuthentication();
                    }
                })).toList();
            start.countDown();
            assertThat(List.of(attempts.get(0).get(15, TimeUnit.SECONDS), attempts.get(1).get(15, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder("ACCEPTED", "CONFLICT");
        }
        assertThat(current().bindingRevision()).isEqualTo(1);
        assertThat(consumers.listBindingHistory(customerId, consumerId)).hasSize(1);
    }

    private PlatformConsumerSummary current() {
        return consumers.listConsumers(customerId).getFirst();
    }

    private PlatformConsumerSummary bind(String deploymentId, Long revision, String key) {
        return consumers.updateBinding(customerId, consumerId,
            new UpdatePlatformConsumerBindingRequest(deploymentId, null, null, "Binding verification", revision, key));
    }

    private String deployment(String name) {
        return deployments.createDeployment(new CreateDeploymentRequest(name, "dev", "dev-openai-lucene", "default", null, customerId, null)).id();
    }
}
