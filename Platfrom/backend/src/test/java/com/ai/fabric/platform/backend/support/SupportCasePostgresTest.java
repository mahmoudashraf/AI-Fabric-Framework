package com.ai.fabric.platform.backend.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.concurrent.*;

import static com.ai.fabric.platform.backend.support.SupportContracts.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class SupportCasePostgresTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @AfterEach void logout() { SecurityContextHolder.clearContext(); }

    @Test void migrationAndConcurrentIntakeKeepOneDurablePrincipalEpisodeAndMessageKey() throws Exception {
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var f = new SupportFixture(dataSource);
        var input = new CreateCase("Concurrent intake", "Exact original form", null, List.of(), false);
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<CaseView> submit = () -> {
                f.login("alice");
                try { start.await(); return f.service.create(input, "one-operation"); }
                finally { SecurityContextHolder.clearContext(); }
            };
            var one = pool.submit(submit); var two = pool.submit(submit); start.countDown();
            var a = one.get(15, TimeUnit.SECONDS); var b = two.get(15, TimeUnit.SECONDS);
            assertThat(a.id()).isEqualTo(b.id());
            assertThat(a.episodes()).isEqualTo(b.episodes());
            assertThat(f.jdbc.queryForObject("select count(*) from platform_support_cases", Integer.class)).isEqualTo(1);
            assertThat(f.jdbc.queryForObject("select count(*) from platform_support_case_events", Integer.class)).isEqualTo(1);
            var access = f.service.requireOwnedCase(a.id());
            assertThat(access.initialMessageBody()).isEqualTo("Concurrent intake\n\nExact original form");

            // Registry serialization is the consent boundary, not an unlocked pre/post permission check.
            var transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
            var locked = new CountDownLatch(1); var release = new CountDownLatch(1);
            var holder = pool.submit(() -> transaction.execute(status -> {
                f.store.byPrincipal(access.principalId()).orElseThrow(); locked.countDown();
                try { release.await(10, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return null;
            }));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            var revoking = new CountDownLatch(1);
            var revoke = pool.submit(() -> {
                f.login("alice"); revoking.countDown();
                try { return f.service.consent(a.id(), new ConsentChange(false, a.consent().version())); }
                finally { SecurityContextHolder.clearContext(); }
            });
            assertThat(revoking.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> revoke.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown(); holder.get(5, TimeUnit.SECONDS);
            assertThat(revoke.get(5, TimeUnit.SECONDS).consent().granted()).isFalse();
        } finally { pool.shutdownNow(); assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue(); }
    }
}
