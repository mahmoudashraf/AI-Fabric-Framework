package com.ai.fabric.platform.backend.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.*;

import static com.ai.fabric.platform.backend.support.SupportDeskContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Testcontainers
class SupportDeskPostgresTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @AfterEach void logout() { SecurityContextHolder.clearContext(); }

    @Test void concurrentResolvePersistsOneReceiptAndActualAgentAuditWithoutAnyConversationMutation() throws Exception {
        var source = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var f = new SupportDeskFixture(source); var c = f.create(true); var lookup = f.lookup(c.id());
        var request = f.resolve(lookup, c.revision(), "concurrent-resolve");
        var start = new CountDownLatch(1); var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Response> operation = () -> { start.await(); return f.contexts.handle(request, f.deskHeaders()); };
            var one = pool.submit(operation); var two = pool.submit(operation); start.countDown();
            var a = one.get(15, TimeUnit.SECONDS); var b = two.get(15, TimeUnit.SECONDS);
            assertThat(a).isEqualTo(b); assertThat(a.context().status()).isEqualTo("RESOLVED");
            assertThat(f.jdbc.queryForObject("select count(*) from platform_support_desk_operations where operation='RESOLVE'", Integer.class)).isEqualTo(1);
            assertThat(f.jdbc.queryForObject("select actor_subject from platform_support_desk_operations", String.class)).isEqualTo(SupportDeskFixture.SUBJECT);
            assertThat(f.jdbc.queryForObject("select count(*) from platform_support_case_events where event_type='DESK_RESOLVED'", Integer.class)).isEqualTo(1);
            assertThat(f.store.episodes(c.id())).hasSize(1);
            assertThat(f.store.episodes(c.id()).getFirst().terminal()).isFalse();
            assertThat(f.store.find(c.id(), false).orElseThrow().deliveryStatus().name()).isEqualTo("PENDING");
            verify(f.desk, never()).put(any(), anyString(), any());
        } finally { pool.shutdownNow(); assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue(); }
    }
}
