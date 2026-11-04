package com.loomai.desk;

import static com.loomai.desk.ticket.TicketContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.loomai.desk.model.DeskContracts.Role;
import com.loomai.desk.ticket.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

class SupportTicketNotificationsTest {
  static class Fixture implements AutoCloseable {
    final SupportTicketsTest.Fixture f;
    final TicketNotificationEmail mail = mock(TicketNotificationEmail.class);
    final RequesterIdentityService identities = mock(RequesterIdentityService.class);
    final TicketNotifications notifications;

    Fixture(String engine) {
      f = new SupportTicketsTest.Fixture(engine);
      when(mail.available()).thenReturn(true);
      when(identities.emailVerificationAvailable(any())).thenReturn(true);
      notifications =
          new TicketNotifications(
              f.spaces, identities, mail, new DataSourceTransactionManager(f.db.dataSource));
    }

    void subscribe() {
      notifications.put(
          f.space, f.user, true, "https://page.example/support", "verified@example.com");
    }

    int count(String where) {
      return f.jdbc.queryForObject(
          "SELECT COUNT(*) FROM desk_support_notification_outbox " + where, Integer.class);
    }

    void due() {
      f.jdbc.update(
          "UPDATE desk_support_notification_outbox SET next_attempt_at=?",
          Timestamp.from(Instant.now().minusSeconds(1)));
    }

    Ticket linked(String key) {
      return f.tickets.create(
          f.space,
          f.user,
          new CreateInput(
              "Secret subject", "Never email intake or auth tokens", null, "chat-" + key),
          key,
          null);
    }

    public void close() {
      notifications.stop();
      f.close();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"h2", "postgres"})
  void explicitConsentIsEncryptedFutureOnlyScopedIdempotentAndDisableCancels(String engine)
      throws Exception {
    try (var n = new Fixture(engine)) {
      var f = n.f;
      Ticket before = n.linked("before");
      f.tickets.change(
          f.space, f.user, before.id(), "RESOLVE", new VersionInput("0"), "old-resolve");
      n.notifications.collect();
      assertThat(n.count("")).isZero();
      n.subscribe();
      var view = n.notifications.view(f.space, f.user);
      assertThat(view.enabled()).isTrue();
      assertThat(view.deliveryStatus()).isEqualTo("IDLE");
      var stored = f.jdbc.queryForMap("SELECT * FROM desk_support_notification_subscription");
      assertThat(stored.get("protected_recipient").toString())
          .doesNotContain("verified@example.com");
      n.subscribe();
      assertThat(f.jdbc.queryForMap("SELECT * FROM desk_support_notification_subscription"))
          .isEqualTo(stored);
      Ticket t = n.linked("future");
      Ticket closed =
          f.tickets.change(
              f.space, f.user, t.id(), "RESOLVE", new VersionInput("0"), "new-resolve");
      f.tickets.change(f.space, f.user, t.id(), "RESOLVE", new VersionInput("0"), "new-resolve");
      n.notifications.collect();
      n.notifications.collect();
      assertThat(n.count("")).isEqualTo(1);
      assertThat(n.notifications.view(f.space, f.other).enabled()).isFalse();
      assertThat(n.notifications.view(f.spaces.get("space-b"), f.user).enabled()).isFalse();
      n.notifications.put(f.space, f.user, false, null, null);
      assertThat(n.count("WHERE status='CANCELLED'")).isEqualTo(1);
      assertThat(n.notifications.deliverNext()).isFalse();
      assertThat(
              f.jdbc.queryForObject(
                  "SELECT protected_recipient FROM desk_support_notification_subscription",
                  String.class))
          .isNull();
      n.subscribe();
      n.notifications.collect();
      assertThat(n.count("")).isEqualTo(1);
      f.tickets.change(
          f.space, f.user, closed.id(), "REOPEN", new VersionInput(closed.version()), "reopen");
      n.notifications.collect();
      assertThat(n.count("")).isEqualTo(2);
      assertThat(n.notifications.deliverNext()).isTrue();
      verify(n.mail)
          .send(
              anyString(),
              eq("verified@example.com"),
              eq(t.reference()),
              eq("https://page.example/support"));
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"h2", "postgres"})
  void statusChangesAndAcknowledgedHumanCallbacksOnlyWithExactBindingAndOwner(String engine)
      throws Exception {
    try (var n = new Fixture(engine)) {
      n.subscribe();
      var f = n.f;
      Ticket t = n.linked("human");
      var owner = f.directory.register(DeskIsolationTest.identity("owner-a"));
      var agent = f.directory.register(DeskIsolationTest.identity("support"));
      f.directory.assign(
          "a",
          owner,
          new com.loomai.desk.model.DeskContracts.AssignmentInput(
              null, agent.id(), Set.of(Role.SUPPORT)));
      Ticket resolved =
          f.tickets.agentResolve("a", agent, t.id(), new VersionInput("0"), "agent-resolve");
      Ticket open =
          f.tickets.change(
              f.space, f.user, t.id(), "REOPEN", new VersionInput(resolved.version()), "reopen");
      f.tickets.change(
          f.space, f.user, t.id(), "CANCEL", new VersionInput(open.version()), "cancel");
      n.notifications.collect();
      assertThat(n.count("")).isEqualTo(3);
      callback(
          n,
          "pending",
          "HUMAN_MESSAGE",
          "PENDING",
          "a",
          t.runtimePrincipal(),
          t.conversation().conversationId());
      callback(
          n,
          "dead",
          "HUMAN_MESSAGE",
          "DEAD",
          "a",
          t.runtimePrincipal(),
          t.conversation().conversationId());
      callback(
          n,
          "accepted",
          "HANDOVER_ACCEPTED",
          "COMPLETED",
          "a",
          t.runtimePrincipal(),
          t.conversation().conversationId());
      callback(
          n,
          "foreign-binding",
          "HUMAN_MESSAGE",
          "COMPLETED",
          "b",
          t.runtimePrincipal(),
          t.conversation().conversationId());
      callback(
          n,
          "foreign-owner",
          "HUMAN_MESSAGE",
          "COMPLETED",
          "a",
          "foreign",
          t.conversation().conversationId());
      n.notifications.collect();
      assertThat(n.count("")).isEqualTo(3);
      f.jdbc.update(
          "UPDATE inbox_callback_outbox SET status='COMPLETED',completed_at=? WHERE"
              + " callback_id='pending'",
          Timestamp.from(Instant.now()));
      n.notifications.collect();
      n.notifications.collect();
      assertThat(n.count("")).isEqualTo(4);
      assertThat(n.count("WHERE event_id='human:pending'")).isEqualTo(1);
      assertThat(
              f.jdbc.queryForObject(
                  "SELECT reference FROM desk_support_notification_outbox WHERE"
                      + " event_id='human:pending'",
                  String.class))
          .isEqualTo(t.reference());
      for (int i = 0; i < 4; i++) assertThat(n.notifications.deliverNext()).isTrue();
      verify(n.mail, times(4))
          .send(anyString(), eq("verified@example.com"), eq(t.reference()), anyString());
    }
  }

  static void callback(
      Fixture n,
      String id,
      String type,
      String status,
      String binding,
      String principal,
      String conversation) {
    var now = Timestamp.from(Instant.now());
    n.f.jdbc.update(
        "INSERT INTO"
            + " inbox_conversation(binding_id,handover_id,external_conversation_ref,tenant_id,deployment_id,conversation_id,owner_ref,status,expected_control_version,unread_count,created_at,updated_at,version)"
            + " VALUES(?,?,?,?,?,?,?,'ACTIVE',1,0,?,?,0)",
        binding,
        id,
        "external-" + id,
        "tenant-" + binding,
        "deployment-" + binding,
        conversation,
        principal,
        now,
        now);
    n.f.jdbc.update(
        "INSERT INTO"
            + " inbox_callback_outbox(binding_id,callback_id,handover_id,callback_sequence,event_type,protected_payload,status,attempt_count,next_attempt_at,created_at,completed_at,updated_at)"
            + " VALUES(?,?,?,1,?,'do-not-read-transcript',?,1,?,?,?,?)",
        binding,
        id,
        id,
        type,
        status,
        now,
        now,
        status.equals("COMPLETED") ? now : null,
        now);
  }

  @ParameterizedTest
  @ValueSource(strings = {"h2", "postgres"})
  void workerRetryIsBoundedAndFailureDoesNotRollbackTicketAndLeasesAreFenced(String engine)
      throws Exception {
    try (var n = new Fixture(engine)) {
      n.subscribe();
      Ticket t = n.linked("failure");
      var closed =
          n.f.tickets.change(
              n.f.space, n.f.user, t.id(), "RESOLVE", new VersionInput("0"), "resolve");
      n.notifications.collect();
      doThrow(new RuntimeException("sensitive@example.com token-smtp-secret"))
          .when(n.mail)
          .send(anyString(), anyString(), anyString(), anyString());
      for (int i = 0; i < 4; i++) {
        n.due();
        assertThat(n.notifications.deliverNext()).isTrue();
      }
      var view = n.notifications.view(n.f.space, n.f.user);
      assertThat(view.deliveryStatus()).isEqualTo("FAILED");
      assertThat(view.lastFailureCode()).isEqualTo("EMAIL_DELIVERY_FAILED");
      assertThat(view.toString()).doesNotContain("sensitive", "token", "verified@example");
      n.due();
      assertThat(n.notifications.deliverNext()).isFalse();
      verify(n.mail, times(4)).send(anyString(), anyString(), anyString(), anyString());
      assertThat(n.f.tickets.get(n.f.space, n.f.user, t.id()).status()).isEqualTo(Status.RESOLVED);
      n.f.jdbc.update(
          "UPDATE desk_support_notification_outbox SET"
              + " status='LEASED',attempt_count=4,lease_until=?,lease_owner='dead-worker'",
          Timestamp.from(Instant.now().minusSeconds(5)));
      assertThat(n.notifications.deliverNext()).isFalse();
      assertThat(n.notifications.view(n.f.space, n.f.user).lastFailureCode())
          .isEqualTo("EMAIL_DELIVERY_UNCERTAIN");
      var purge =
          new PurgeInput(
              closed.version(),
              DeskIsolationTest.ISSUER,
              "owner-a",
              true,
              "Disposable notification test");
      assertThat(n.f.tickets.purge("a", t.id(), purge, "purge").purged()).isTrue();
      assertThat(n.count("")).isZero();
      assertThat(
              n.f.jdbc.queryForObject(
                  "SELECT COUNT(*) FROM desk_support_notification_subscription", Integer.class))
          .isZero();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"h2", "postgres"})
  void concurrentCollectionAndDeliveryAreSingleEffectAndCurrentDisablePreventsSending(String engine)
      throws Exception {
    try (var n = new Fixture(engine);
        var pool = Executors.newFixedThreadPool(4)) {
      n.subscribe();
      Ticket t = n.linked("concurrent");
      n.f.tickets.change(n.f.space, n.f.user, t.id(), "RESOLVE", new VersionInput("0"), "resolve");
      for (var result :
          pool.invokeAll(
              java.util.stream.IntStream.range(0, 4)
                  .mapToObj(
                      i ->
                          (java.util.concurrent.Callable<Void>)
                              () -> {
                                n.notifications.collect();
                                return null;
                              })
                  .toList())) result.get();
      assertThat(n.count("")).isEqualTo(1);
      for (var result :
          pool.invokeAll(
              java.util.stream.IntStream.range(0, 4)
                  .mapToObj(
                      i -> (java.util.concurrent.Callable<Boolean>) n.notifications::deliverNext)
                  .toList())) result.get();
      verify(n.mail, times(1)).send(anyString(), anyString(), anyString(), anyString());
      n.f.tickets.change(n.f.space, n.f.user, t.id(), "REOPEN", new VersionInput("1"), "reopen");
      n.notifications.collect();
      var disabled =
          (com.fasterxml.jackson.databind.node.ObjectNode)
              DeskIsolationTest.JSON.valueToTree(
                  DeskIsolationTest.binding("a", "https://runtime.example/callback"));
      disabled.put("enabled", false);
      n.f.directory.putBinding(
          "a",
          DeskIsolationTest.JSON.treeToValue(
              disabled, com.loomai.desk.model.DeskContracts.BindingInput.class));
      assertThat(n.notifications.deliverNext()).isTrue();
      assertThat(n.count("WHERE status='CANCELLED'")).isEqualTo(1);
      verify(n.mail, times(1)).send(anyString(), anyString(), anyString(), anyString());
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"h2", "postgres"})
  void backlogAndCollectorBatchAreBoundedAndInvalidLinksNeverEnroll(String engine)
      throws Exception {
    try (var n = new Fixture(engine)) {
      for (String url :
          List.of(
              "http://page.example/support",
              "https://evil.example/support",
              "https://page.example/support?token=secret",
              "https://user:pass@page.example/support",
              "https://page.example/#access_token=x",
              "https://page.example/a/../support"))
        SupportTicketsTest.rejected(
            400, () -> n.notifications.put(n.f.space, n.f.user, true, url, "verified@example.com"));
      assertThat(
              n.f.jdbc.queryForObject(
                  "SELECT COUNT(*) FROM desk_support_notification_subscription", Integer.class))
          .isZero();
      n.subscribe();
      var t = n.linked("backlog");
      var now = Timestamp.from(Instant.now());
      for (int i = 0; i < 60; i++)
        n.f.jdbc.update(
            "INSERT INTO"
                + " desk_support_ticket_event(space_id,request_id,event_id,event_type,actor_id,created_at)"
                + " VALUES(?,?,?,'REOPEN',?,?)",
            "space-a",
            t.id(),
            "bulk-" + i,
            n.f.user.id(),
            now);
      n.notifications.collect();
      assertThat(n.count("")).isEqualTo(25);
      n.notifications.collect();
      n.notifications.collect();
      assertThat(n.count("WHERE status='PENDING'")).isEqualTo(50);
      assertThat(n.count("WHERE status='FAILED' AND failure_code='NOTIFICATION_BACKLOG_LIMIT'"))
          .isEqualTo(10);
      n.f.jdbc.update(
          "UPDATE desk_support_notification_outbox SET updated_at=?,status='SENT' WHERE"
              + " notification_id=(SELECT MIN(notification_id) FROM"
              + " desk_support_notification_outbox WHERE status='PENDING')",
          Timestamp.from(Instant.now().plusSeconds(1)));
      assertThat(n.notifications.view(n.f.space, n.f.user).deliveryStatus()).isEqualTo("FAILED");
      assertThat(n.notifications.view(n.f.space, n.f.user).lastFailureCode())
          .isEqualTo("NOTIFICATION_BACKLOG_LIMIT");
      when(n.mail.available()).thenReturn(false);
      assertThat(n.notifications.view(n.f.space, n.f.user).available()).isFalse();
      assertThat(n.notifications.deliverNext()).isFalse();
      verify(n.mail, never()).send(anyString(), anyString(), anyString(), anyString());
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"h2", "postgres"})
  void normalCallbackMustBeAcknowledgedBeforeHumanEmailIsQueued(String engine) throws Exception {
    try (var n = new Fixture(engine)) {
      n.subscribe();
      var ticket = n.linked("acknowledged");
      callback(
          n,
          "acknowledged",
          "HUMAN_MESSAGE",
          "PENDING",
          "a",
          ticket.runtimePrincipal(),
          ticket.conversation().conversationId());
      var p = new com.loomai.desk.channel.config.BasicInboxProperties();
      p.setBindingId("a");
      p.setDataEncryptionSecret(DeskIsolationTest.properties().getDataEncryptionSecret());
      p.setCallbackSigningSecret("signing-secret-123456789012345678901234567890");
      var security = new com.loomai.desk.channel.service.InboxSecurityService(p);
      n.f.jdbc.update(
          "UPDATE inbox_callback_outbox SET protected_payload=? WHERE callback_id='acknowledged'",
          security.encrypt("{}"));
      var accepted = new java.util.concurrent.atomic.AtomicBoolean(false);
      var callbackServer =
          com.sun.net.httpserver.HttpServer.create(
              new java.net.InetSocketAddress("127.0.0.1", 0), 0);
      callbackServer.createContext(
          "/callback",
          exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("X-LoomAI-Provider-Signature"))
                .isNotBlank();
            exchange.getRequestBody().readAllBytes();
            byte[] body =
                "{\"controlVersion\":2}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(accepted.get() ? 200 : 503, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
          });
      callbackServer.start();
      try {
        p.setConnectorCallbackUrl(
            "http://127.0.0.1:" + callbackServer.getAddress().getPort() + "/callback");
        var repository =
            new com.loomai.desk.channel.persistence.BasicInboxRepository(
                n.f.db.dataSource, DeskIsolationTest.JSON, security, "a");
        var worker =
            new com.loomai.desk.channel.service.InboxCallbackWorker(
                p, repository, security, DeskIsolationTest.JSON);
        worker.deliverNext();
        n.notifications.collect();
        assertThat(n.count("")).isZero();
        accepted.set(true);
        n.f.jdbc.update(
            "UPDATE inbox_callback_outbox SET next_attempt_at=? WHERE callback_id='acknowledged'",
            Timestamp.from(Instant.now().minusSeconds(1)));
        worker.deliverNext();
        assertThat(
                n.f.jdbc.queryForObject(
                    "SELECT status FROM inbox_callback_outbox WHERE callback_id='acknowledged'",
                    String.class))
            .isEqualTo("COMPLETED");
        n.notifications.collect();
        assertThat(n.count("WHERE event_id='human:acknowledged'")).isEqualTo(1);
        assertThat(n.notifications.deliverNext()).isTrue();
        verify(n.mail)
            .send(
                anyString(),
                eq("verified@example.com"),
                eq(ticket.reference()),
                eq("https://page.example/support"));
      } finally {
        callbackServer.stop(0);
      }
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"h2", "postgres"})
  void disabledSpaceAndRemovedOriginAreRecheckedBeforeSend(String engine) throws Exception {
    try (var n = new Fixture(engine)) {
      n.subscribe();
      var ticket = n.linked("disable");
      n.f.tickets.change(
          n.f.space, n.f.user, ticket.id(), "RESOLVE", new VersionInput("0"), "resolve");
      n.notifications.collect();
      n.f.tx.execute(ignored -> n.f.spaces.put("a", n.f.input("space-a", "0", "desk", false)));
      assertThat(n.notifications.deliverNext()).isTrue();
      assertThat(n.count("WHERE status='CANCELLED'")).isEqualTo(1);
      n.f.tx.execute(ignored -> n.f.spaces.put("a", n.f.input("space-a", "1", "desk", true)));
      Space current = n.f.spaces.get("space-a");
      n.f.tickets.change(current, n.f.user, ticket.id(), "REOPEN", new VersionInput("1"), "reopen");
      n.notifications.collect();
      var noOrigin =
          (com.fasterxml.jackson.databind.node.ObjectNode)
              DeskIsolationTest.JSON.valueToTree(n.f.input("space-a", "2", "desk", true));
      noOrigin.putArray("allowedOrigins");
      n.f.tx.execute(
          ignored -> {
            try {
              return n.f.spaces.put(
                  "a", DeskIsolationTest.JSON.treeToValue(noOrigin, SupportSpaceInput.class));
            } catch (Exception failure) {
              throw new IllegalStateException(failure);
            }
          });
      assertThat(n.notifications.deliverNext()).isTrue();
      assertThat(n.notifications.view(n.f.spaces.get("space-a"), n.f.user).lastFailureCode())
          .isEqualTo("EMAIL_DELIVERY_FAILED");
      verify(n.mail, never()).send(anyString(), anyString(), anyString(), anyString());
    }
  }

  @org.junit.jupiter.api.Test
  void slowSmtpDoesNotBlockCallbackSchedulerOrQueueOverlappingMailJobs() throws Exception {
    try (var n = new Fixture("h2")) {
      n.subscribe();
      var ticket = n.linked("slow-smtp");
      n.f.tickets.change(
          n.f.space, n.f.user, ticket.id(), "RESOLVE", new VersionInput("0"), "resolve");
      var entered = new java.util.concurrent.CountDownLatch(1);
      var release = new java.util.concurrent.CountDownLatch(1);
      var thread = new java.util.concurrent.atomic.AtomicReference<String>();
      doAnswer(
              call -> {
                thread.set(Thread.currentThread().getName());
                entered.countDown();
                if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS))
                  throw new IllegalStateException("Test timed out");
                return null;
              })
          .when(n.mail)
          .send(anyString(), anyString(), anyString(), anyString());
      try {
        n.notifications.poll();
        assertThat(entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(
            java.time.Duration.ofSeconds(1),
            () -> {
              for (int i = 0; i < 100; i++) n.notifications.poll();
            });
      } finally {
        release.countDown();
        n.notifications.stop();
      }
      assertThat(thread.get()).startsWith("desk-ticket-email-");
      assertThat(n.count("WHERE status='SENT'")).isEqualTo(1);
      verify(n.mail, times(1)).send(anyString(), anyString(), anyString(), anyString());
    }
  }
}
