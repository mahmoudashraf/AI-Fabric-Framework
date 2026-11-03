package com.ai.fabric.platform.backend.support;

import static com.ai.fabric.platform.backend.support.SupportContracts.*;
import static org.assertj.core.api.Assertions.*;

import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class SupportContextPostgresTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @AfterEach
  void logout() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void concurrentProviderReplayCreatesOneMappingAndConsentRevocationWaitsForAuthorityLock()
      throws Exception {
    String schema = "support_" + UUID.randomUUID().toString().replace("-", "");
    var admin =
        new JdbcTemplate(
            new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    admin.execute("create schema " + schema);
    var data =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl() + "&currentSchema=" + schema,
            POSTGRES.getUsername(),
            POSTGRES.getPassword());
    var pool = Executors.newFixedThreadPool(2);
    var release = new CountDownLatch(1);
    try {
      var f = new SupportFixture(data);
      var start = new CountDownLatch(1);
      var input =
          new SupportTicketService.Create(
              "Concurrent provider request", "Not stored in Console", null, null);
      Callable<String> submit =
          () -> {
            f.login("alice");
            try {
              start.await();
              return f.service.create(input, "same-command").path("id").asText();
            } finally {
              SecurityContextHolder.clearContext();
            }
          };
      var one = pool.submit(submit);
      var two = pool.submit(submit);
      start.countDown();
      String id = one.get(15, TimeUnit.SECONDS);
      assertThat(two.get(15, TimeUnit.SECONDS)).isEqualTo(id);
      assertThat(
              f.jdbc.queryForObject(
                  "select count(*) from platform_support_integration_context", Integer.class))
          .isEqualTo(1);
      f.login("alice");
      var granted = f.service.consent(id, new ConsentChange(true, 0));
      var transaction = new TransactionTemplate(new DataSourceTransactionManager(data));
      var locked = new CountDownLatch(1);
      var holder =
          pool.submit(
              () ->
                  transaction.execute(
                      status -> {
                        f.contextStore
                            .byPrincipal("support-binding", "support-ticket:" + id, true)
                            .orElseThrow();
                        locked.countDown();
                        try {
                          assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
                        } catch (InterruptedException error) {
                          Thread.currentThread().interrupt();
                          throw new IllegalStateException(error);
                        }
                        return null;
                      }));
      assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
      var revoking = new CountDownLatch(1);
      var revoke =
          pool.submit(
              () -> {
                f.login("alice");
                revoking.countDown();
                try {
                  return f.service.consent(
                      id, new ConsentChange(false, granted.consent().version()));
                } finally {
                  SecurityContextHolder.clearContext();
                }
              });
      assertThat(revoking.await(5, TimeUnit.SECONDS)).isTrue();
      assertThatThrownBy(() -> revoke.get(200, TimeUnit.MILLISECONDS))
          .isInstanceOf(TimeoutException.class);
      release.countDown();
      holder.get(5, TimeUnit.SECONDS);
      assertThat(revoke.get(5, TimeUnit.SECONDS).consent().granted()).isFalse();
      assertThat(f.contextStore.byPrincipal("other-space", "support-ticket:" + id, false))
          .isEmpty();
    } finally {
      release.countDown();
      pool.shutdownNow();
      assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
      admin.execute("drop schema " + schema + " cascade");
    }
  }

  @Test
  void retirementDropsOnlyDisposableTicketTablesPreservingContextDocumentsAndAiState() {
    String schema = "support_" + UUID.randomUUID().toString().replace("-", "");
    var admin =
        new JdbcTemplate(
            new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    admin.execute("create schema " + schema);
    var data =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl() + "&currentSchema=" + schema,
            POSTGRES.getUsername(),
            POSTGRES.getPassword());
    try {
      var f = new SupportFixture(data);
      var saved = f.create(true);
      var scope = f.conversations.create();
      new ResourceDatabasePopulator(
              new ClassPathResource("db/migration/V218__internal_support_case_registry.sql"),
              new ClassPathResource("db/migration/V219__support_desk_context_receipts.sql"))
          .execute(data);
      f.jdbc.execute(
          "create table platform_vectorization_source_connections(id varchar(64) primary key,"
              + " secret_ref text)");
      f.jdbc.update(
          "insert into platform_vectorization_source_connections values"
              + " ('s3-documents','managed-s3-credential')");
      f.jdbc.execute("create table ai_conversation_state(id varchar(64) primary key, state text)");
      f.jdbc.update(
          "insert into ai_conversation_state values ('canonical-conversation','preserved')");
      new ResourceDatabasePopulator(
              new ClassPathResource("db/migration/V227__retire_console_support_ticket_storage.sql"))
          .execute(data);
      for (String table :
          java.util.List.of(
              "platform_support_cases",
              "platform_support_case_events",
              "platform_support_case_episodes",
              "platform_support_desk_operations"))
        assertThat(f.jdbc.queryForObject("select to_regclass(?)::text", String.class, table))
            .isNull();
      assertThat(f.service.contextView(saved.id()).consent().granted()).isTrue();
      assertThat(f.conversations.current(scope.conversationId())).isEqualTo(scope);
      assertThat(
              f.jdbc.queryForObject(
                  "select secret_ref from platform_vectorization_source_connections", String.class))
          .isEqualTo("managed-s3-credential");
      assertThat(f.jdbc.queryForObject("select state from ai_conversation_state", String.class))
          .isEqualTo("preserved");
      assertThat(
              f.jdbc.queryForList(
                  "select column_name from information_schema.columns where table_schema=? and"
                      + " table_name='platform_support_integration_context'",
                  String.class,
                  schema))
          .doesNotContain(
              "summary", "details", "status", "initial_message", "transcript", "terminal");
    } finally {
      admin.execute("drop schema " + schema + " cascade");
    }
  }
}
