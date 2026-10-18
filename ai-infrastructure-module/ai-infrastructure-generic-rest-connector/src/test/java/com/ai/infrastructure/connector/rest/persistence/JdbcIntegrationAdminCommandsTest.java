package com.ai.infrastructure.connector.rest.persistence;

import com.ai.infrastructure.connector.rest.config.RestConnectorServiceProperties;
import com.ai.infrastructure.connector.rest.config.RestRoutingConfig;
import com.ai.infrastructure.connector.rest.controller.IntegrationOperationsController;
import com.ai.infrastructure.connector.rest.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers(disabledWithoutDocker = true)
class JdbcIntegrationAdminCommandsTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;
    private String schema;

    @BeforeEach
    void database() {
        schema = "integration_command_" + UUID.randomUUID().toString().replace("-", "");
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .schemas(schema).defaultSchema(schema).locations("classpath:db/migration/integration").load().migrate();
        dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl()
            + (POSTGRES.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=" + schema,
            POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE admin_test_effect (value INTEGER NOT NULL)");
    }

    @AfterEach
    void cleanup() { jdbc.execute("DROP SCHEMA " + schema + " CASCADE"); }

    @Test
    void webhookReplayIsDeduplicatedAfterConnectorRestartIncludingPersistentReplayCount() {
        var config = new RestRoutingConfig();
        var source = new RestRoutingConfig.WebhookSource();
        source.setEnabled(true);
        source.setManualReplayEnabled(true);
        source.setReconcileDataSourceRef("source-1");
        config.getWebhooks().put("hook-1", source);
        var repository = repository();
        Instant now = Instant.parse("2026-10-16T12:00:00Z");
        assertThat(repository.registerEvent(new IntegrationStateRepository.WebhookEvent("hook-1", "event-1", "changed",
            null, "resource-fingerprint", "payload-fingerprint", "COMPLETED", 0, 0, 0, null, now, now))).isTrue();
        var sync = mock(HttpDataSyncService.class);
        when(sync.reconcileAsync("source-1")).thenReturn(CompletableFuture.completedFuture(state()));
        var firstWebhook = webhook(config, repository, sync);
        var first = controller(config, repository, sync, firstWebhook).replay("hook-1", "event-1", "replay-1");
        var restarted = repository();
        var second = controller(config, restarted, sync, webhook(config, restarted, sync)).replay("hook-1", "event-1", "replay-1");

        assertThat(first.getStatusCode().value()).isEqualTo(202);
        assertThat(second.getBody()).isEqualTo(first.getBody());
        assertThat(restarted.event("hook-1", "event-1")).get().satisfies(event -> {
            assertThat(event.replayCount()).isEqualTo(1);
            assertThat(event.attemptCount()).isEqualTo(1);
        });
        verify(sync, times(1)).reconcileAsync("source-1");
    }

    @Test
    void sourceReconcileReturnsItsOriginalReceiptAfterRestartAndRejectsConflictingReuse() {
        var config = new RestRoutingConfig();
        var sync = mock(HttpDataSyncService.class);
        var webhook = mock(ProviderWebhookService.class);
        when(sync.reconcile("source-1")).thenReturn(state());
        var first = controller(config, repository(), sync, webhook).reconcile("source-1", "reconcile-1");
        var restarted = controller(config, repository(), sync, webhook);
        assertThat(restarted.reconcile("source-1", "reconcile-1").getBody()).isEqualTo(first.getBody());
        assertThatThrownBy(() -> restarted.reconcile("other-source", "reconcile-1"))
            .isInstanceOf(ProviderCallException.class).hasMessageContaining("IDEMPOTENCY_KEY_REUSED");
        assertThatThrownBy(() -> restarted.replay("hook-1", "event-1", "reconcile-1"))
            .hasMessageContaining("IDEMPOTENCY_KEY_REUSED");
        verify(sync, times(1)).reconcile("source-1");
        verifyNoInteractions(webhook);
    }

    @Test
    void uncertainOutcomeNeverRunsAgainEvenWhenItsSideEffectCommitted() {
        assertThatThrownBy(() -> repository().executeAdminCommand("unknown-1", "RECONCILE", "source-1", String.class, () -> {
            jdbc.update("INSERT INTO admin_test_effect VALUES (1)");
            throw new IllegalStateException("Connection interrupted after acceptance.");
        })).hasMessageContaining("outcome is unconfirmed");
        assertThat(jdbc.queryForObject("SELECT state FROM integration_admin_command", String.class)).isEqualTo("OUTCOME_UNKNOWN");
        assertThatThrownBy(() -> repository().executeAdminCommand("unknown-1", "RECONCILE", "source-1", String.class,
            () -> { throw new AssertionError("Uncertain work was replayed."); })).hasMessageContaining("OUTCOME_UNCONFIRMED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_test_effect", Long.class)).isEqualTo(1);
    }

    @Test
    void interruptedProcessKeepsItsClaimAcrossRestart() {
        assertThatThrownBy(() -> repository().executeAdminCommand("crash-1", "RECONCILE", "source-1", String.class,
            () -> { throw new AssertionError("Simulated termination."); })).isInstanceOf(AssertionError.class);
        assertThat(jdbc.queryForObject("SELECT state FROM integration_admin_command", String.class)).isEqualTo("STARTED");
        assertThatThrownBy(() -> repository().executeAdminCommand("crash-1", "RECONCILE", "source-1", String.class,
            () -> { throw new AssertionError("Interrupted work was replayed."); })).hasMessageContaining("OUTCOME_UNCONFIRMED");
    }

    @Test
    void simultaneousConnectorInstancesExecuteOnlyOneMutation() throws Exception {
        var entered = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        try (var pool = Executors.newSingleThreadExecutor()) {
            var first = pool.submit(() -> repository().executeAdminCommand("race-1", "RECONCILE", "source-1", String.class, () -> {
                calls.incrementAndGet(); entered.countDown(); await(finish); return "accepted";
            }));
            try {
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> repository().executeAdminCommand("race-1", "RECONCILE", "source-1", String.class,
                    () -> { throw new AssertionError("Concurrent work ran twice."); })).hasMessageContaining("OUTCOME_UNCONFIRMED");
            } finally { finish.countDown(); }
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo("accepted");
        }
        assertThat(calls).hasValue(1);
    }

    @Test
    void disabledOrUnavailablePersistenceNeverExecutesAnAdminMutation() {
        assertThatThrownBy(() -> new InMemoryIntegrationStateRepository().executeAdminCommand("key-1", "RECONCILE", "source-1",
            String.class, () -> { throw new AssertionError("Non-durable command executed."); }))
            .hasMessageContaining("DURABLE_INTEGRATION_COMMAND_STORE_REQUIRED");
        jdbc.execute("DROP TABLE integration_admin_command");
        assertThatThrownBy(() -> repository().executeAdminCommand("key-1", "RECONCILE", "source-1", String.class,
            () -> { throw new AssertionError("Command executed without its store."); })).hasMessageContaining("store is unavailable");
    }

    @Test
    void invalidKeysDoNotLeaveReceiptsOrExecuteMutations() {
        for (String key : new String[] {null, "", " ", " key", "key ", "key\n", "x".repeat(129)}) {
            assertThatThrownBy(() -> repository().executeAdminCommand(key, "RECONCILE", "source-1", String.class,
                () -> { throw new AssertionError("Invalid command executed."); })).hasMessageContaining("Idempotency-Key");
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM integration_admin_command", Long.class)).isZero();
    }

    private JdbcIntegrationStateRepository repository() { return new JdbcIntegrationStateRepository(jdbc); }

    private ProviderWebhookService webhook(RestRoutingConfig config, IntegrationStateRepository repository, HttpDataSyncService sync) {
        return new ProviderWebhookService(config, mock(WebhookVerificationService.class), mock(ProtectedResourceService.class),
            repository, sync, new ObjectMapper(), Clock.systemUTC());
    }

    private IntegrationOperationsController controller(RestRoutingConfig config, IntegrationStateRepository repository,
                                                       HttpDataSyncService sync, ProviderWebhookService webhook) {
        return new IntegrationOperationsController(config, new RestConnectorServiceProperties(), sync, webhook,
            mock(ProviderTokenService.class), mock(ProtectedResourceService.class), repository);
    }

    private IntegrationStateRepository.SyncState state() {
        Instant now = Instant.parse("2026-10-16T12:00:00Z");
        return new IntegrationStateRepository.SyncState("source-1", "COMPLETED", "cursor-1", "v1", null, null,
            new IntegrationStateRepository.SyncCounts(1, 1, 1, 0, 1, 1, 0), now, now, null, null, null, now);
    }

    private void await(CountDownLatch latch) {
        try { if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out waiting for test."); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
    }
}
