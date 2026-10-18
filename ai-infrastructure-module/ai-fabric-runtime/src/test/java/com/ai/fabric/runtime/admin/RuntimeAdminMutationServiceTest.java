package com.ai.fabric.runtime.admin;

import com.ai.fabric.runtime.auth.RuntimeAuthContext;
import com.ai.fabric.runtime.auth.RuntimeResolvedIdentity;
import com.ai.fabric.runtime.auth.RuntimeRequestAuthResolver;
import com.ai.fabric.runtime.documents.DocumentKnowledgeService;
import com.ai.fabric.runtime.handover.ConversationHandoverContracts.HandoverOperationsOverview;
import com.ai.fabric.runtime.handover.RuntimeConversationHandoverService;
import com.ai.fabric.runtime.web.DocumentKnowledgeController;
import com.ai.fabric.runtime.web.admin.ConversationHandoverAdminController;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Testcontainers(disabledWithoutDocker = true)
class RuntimeAdminMutationServiceTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;
    private String schema;

    @BeforeEach
    void database() {
        schema = "admin_command_" + UUID.randomUUID().toString().replace("-", "");
        var admin = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        admin.execute("CREATE SCHEMA " + schema);
        dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl()
            + (POSTGRES.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=" + schema,
            POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        new ResourceDatabasePopulator(new ClassPathResource("db/runtime-migration/V18__runtime_admin_mutation_receipts.sql"))
            .execute(dataSource);
        jdbc.execute("CREATE TABLE admin_test_effect (value INTEGER NOT NULL)");
    }

    @AfterEach
    void cleanup() {
        jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
    }

    @Test
    void completedReceiptSurvivesServiceRestartAndRejectsKeyReuseForDifferentInput() {
        Receipt expected = new Receipt("accepted", Instant.parse("2026-10-16T12:00:00Z"));
        assertThat(service("target-1", "release-1").execute(identity(), "RECOVER", "resource-1", "key-1", Receipt.class,
            () -> { jdbc.update("INSERT INTO admin_test_effect VALUES (1)"); return expected; })).isEqualTo(expected);

        var restarted = service("target-1", "release-1");
        assertThat(restarted.execute(identity(), "RECOVER", "resource-1", "key-1", Receipt.class,
            () -> { throw new AssertionError("A replay executed again."); })).isEqualTo(expected);
        assertThatThrownBy(() -> restarted.execute(identity(), "RECOVER", "resource-2", "key-1", Receipt.class,
            () -> expected)).hasMessageContaining("IDEMPOTENCY_KEY_REUSED");
        assertThatThrownBy(() -> restarted.execute(identity(), "OTHER_OPERATION", "resource-1", "key-1", Receipt.class,
            () -> expected)).hasMessageContaining("IDEMPOTENCY_KEY_REUSED");
        assertThat(effects()).isEqualTo(1);
    }

    @Test
    void uncertainSideEffectsAreNeverExecutedAgainAfterRestart() {
        var service = service("target-1", "release-1");
        assertThatThrownBy(() -> service.execute(identity(), "RECOVER", "resource-1", "unknown-1", Receipt.class, () -> {
            jdbc.update("INSERT INTO admin_test_effect VALUES (1)");
            throw new IllegalStateException("Provider response was lost.");
        })).hasMessageContaining("Provider response was lost");
        assertThat(jdbc.queryForObject("SELECT state FROM loomai_runtime_admin_command", String.class)).isEqualTo("OUTCOME_UNKNOWN");
        assertThatThrownBy(() -> service("target-1", "release-1").execute(identity(), "RECOVER", "resource-1", "unknown-1",
            Receipt.class, () -> { throw new AssertionError("Uncertain work was repeated."); }))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("OUTCOME_UNCONFIRMED");
        assertThat(effects()).isEqualTo(1);
    }

    @Test
    void interruptedProcessLeavesADurableNonReplayableClaim() {
        assertThatThrownBy(() -> service("target-1", "release-1").execute(identity(), "RECOVER", "resource-1", "crash-1",
            Receipt.class, () -> { throw new AssertionError("Simulated process interruption."); })).isInstanceOf(AssertionError.class);
        assertThat(jdbc.queryForObject("SELECT state FROM loomai_runtime_admin_command", String.class)).isEqualTo("STARTED");
        assertThatThrownBy(() -> service("target-1", "release-1").execute(identity(), "RECOVER", "resource-1", "crash-1",
            Receipt.class, () -> { throw new AssertionError("Interrupted work was repeated."); })).hasMessageContaining("OUTCOME_UNCONFIRMED");
    }

    @Test
    void concurrentInstancesCannotExecuteTheSameClaimTwice() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        Receipt expected = new Receipt("accepted", Instant.parse("2026-10-16T12:00:00Z"));
        try (var pool = Executors.newSingleThreadExecutor()) {
            var first = pool.submit(() -> service("target-1", "release-1").execute(identity(), "RECOVER", "resource-1", "race-1",
                Receipt.class, () -> {
                    calls.incrementAndGet();
                    entered.countDown();
                    await(finish);
                    return expected;
                }));
            try {
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> service("target-1", "release-1").execute(identity(), "RECOVER", "resource-1", "race-1",
                    Receipt.class, () -> { throw new AssertionError("Concurrent work ran twice."); })).hasMessageContaining("OUTCOME_UNCONFIRMED");
            } finally { finish.countDown(); }
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(expected);
        }
        assertThat(calls).hasValue(1);
    }

    @Test
    void receiptScopeSeparatesTargetAndReleaseAndRejectsWrongVerifiedIdentity() {
        var expected = new Receipt("accepted", Instant.parse("2026-10-16T12:00:00Z"));
        for (String[] target : new String[][] {{"target-1", "release-1"}, {"target-2", "release-1"}, {"target-1", "release-2"}}) {
            service(target[0], target[1]).execute(identity(), "RECOVER", "resource-1", "same-key", Receipt.class,
                () -> { jdbc.update("INSERT INTO admin_test_effect VALUES (1)"); return expected; });
        }
        assertThat(effects()).isEqualTo(3);
        var wrong = RuntimeResolvedIdentity.builder().authContext(identity().getAuthContext().toBuilder().tenantId("other-tenant").build()).build();
        assertThatThrownBy(() -> service("target-1", "release-1").execute(wrong, "RECOVER", "resource-1", "wrong-1", Receipt.class,
            () -> expected)).hasMessageContaining("SCOPE_MISMATCH");
    }

    @Test
    void missingKeysOrConfiguredTargetIdentityFailBeforeAnySideEffect() {
        for (String key : new String[] {null, "", " ", "key\n", "x".repeat(129)}) {
            assertThatThrownBy(() -> service("target-1", "release-1").execute(identity(), "RECOVER", "resource-1", key,
                Receipt.class, () -> { throw new AssertionError("Invalid command was executed."); })).hasMessageContaining("Idempotency-Key");
        }
        assertThatThrownBy(() -> service("", "").execute(identity(), "RECOVER", "resource-1", "key-1", Receipt.class,
            () -> { throw new AssertionError("An unbound command was executed."); })).hasMessageContaining("TARGET_IDENTITY_REQUIRED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM loomai_runtime_admin_command", Long.class)).isZero();
    }

    @Test
    void handoverRecoveryAndDocumentCleanupUseDurableReceiptsAtTheirHttpBoundaries() {
        var auth = mock(RuntimeRequestAuthResolver.class);
        var handover = mock(RuntimeConversationHandoverService.class);
        var documents = mock(DocumentKnowledgeService.class);
        var request = new MockHttpServletRequest();
        var identity = identity();
        when(auth.resolveVerifiedPrivateContext(any(), any())).thenReturn(identity);
        var overview = new HandoverOperationsOverview(true, "channel-1", Map.of("PENDING", 1L), Map.of(),
            Instant.parse("2026-10-16T12:00:00Z"), List.of());
        when(handover.retryDispatch("deployment-1", "dispatch-1")).thenReturn(overview);
        when(handover.expirePendingHandover("deployment-1", "handover-1")).thenReturn(overview);
        var cleanup = new DocumentKnowledgeService.RetentionCleanupResult("deployment", Instant.EPOCH, Instant.EPOCH,
            2, 3, 4, 5, Instant.parse("2026-10-16T12:00:00Z"), false);
        when(documents.cleanupRetention(identity)).thenReturn(cleanup);
        var first = new ConversationHandoverAdminController(auth, handover, service("target-1", "release-1"));
        var restarted = new ConversationHandoverAdminController(auth, handover, service("target-1", "release-1"));
        assertThat(first.retryDispatch("dispatch-1", "retry-1", request)).isEqualTo(overview);
        assertThat(restarted.retryDispatch("dispatch-1", "retry-1", request)).isEqualTo(overview);
        assertThat(first.expirePending("handover-1", "expire-1", request)).isEqualTo(overview);
        assertThat(restarted.expirePending("handover-1", "expire-1", request)).isEqualTo(overview);
        var documentController = new DocumentKnowledgeController(auth, documents, service("target-1", "release-1"));
        assertThat(documentController.cleanupRetention("cleanup-1", request)).isEqualTo(cleanup);
        assertThat(new DocumentKnowledgeController(auth, documents, service("target-1", "release-1"))
            .cleanupRetention("cleanup-1", request)).isEqualTo(cleanup);
        verify(handover, times(1)).retryDispatch("deployment-1", "dispatch-1");
        verify(handover, times(1)).expirePendingHandover("deployment-1", "handover-1");
        verify(documents, times(1)).cleanupRetention(identity);
    }

    private long effects() { return jdbc.queryForObject("SELECT COUNT(*) FROM admin_test_effect", Long.class); }

    private RuntimeAdminMutationService service(String target, String release) {
        return new RuntimeAdminMutationService(dataSource, new ObjectMapper(), "deployment-1", target, release, "tenant-1");
    }

    private RuntimeResolvedIdentity identity() {
        return RuntimeResolvedIdentity.builder().authContext(RuntimeAuthContext.builder()
            .customerId("customer-1").deploymentId("deployment-1").tenantId("tenant-1").build()).build();
    }

    private void await(CountDownLatch latch) {
        try { if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out waiting for test."); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
    }

    public record Receipt(String status, Instant acceptedAt) { }
}
