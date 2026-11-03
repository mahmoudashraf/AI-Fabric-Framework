package com.loomai.desk;

import static com.loomai.desk.DeskIsolationTest.*;
import static com.loomai.desk.ticket.TicketContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.loomai.desk.model.DeskContracts.*;
import com.loomai.desk.service.DeskDirectory;
import com.loomai.desk.ticket.*;
import com.loomai.desk.ticket.TicketEndpointPolicy;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class SupportTicketsTest {
  static class Fixture implements AutoCloseable {
    final DeskTestDatabase db;
    final JdbcTemplate jdbc;
    final DeskDirectory directory;
    final DeskTicketProvider provider;
    final TicketRuntimeClient runtime = mock(TicketRuntimeClient.class);
    final SupportSpaces spaces;
    final SupportTickets tickets;
    final TransactionTemplate tx;
    final Space space;
    final Requester user;
    final Requester other;

    Fixture(String engine) {
      this(engine, null);
    }

    Fixture(String engine, TicketProvider external) {
      db = new DeskTestDatabase(engine);
      jdbc = new JdbcTemplate(db.dataSource);
      var p = properties();
      p.setConversationContextAllowedOrigins(
          List.of("https://runtime.example", "https://identity.example", "https://page.example"));
      directory = new DeskDirectory(jdbc, p);
      workspace(directory, "a", "owner-a");
      workspace(directory, "b", "owner-b");
      directory.putBinding("a", binding("a", "https://runtime.example/callback"));
      directory.putBinding("b", binding("b", "https://runtime.example/callback"));
      provider = new DeskTicketProvider(jdbc, JSON, p);
      spaces =
          new SupportSpaces(
              jdbc,
              JSON,
              p,
              directory,
              new TicketEndpointPolicy(p),
              external == null ? List.of(provider) : List.of(provider, external));
      var manager = new DataSourceTransactionManager(db.dataSource);
      tx = new TransactionTemplate(manager);
      tickets = new SupportTickets(spaces, runtime, directory, manager);
      tx.execute(
          s ->
              spaces.put(
                  "a", input("space-a", null, external == null ? "desk" : external.id(), true)));
      tx.execute(s -> spaces.put("b", input("space-b", null, "desk", true)));
      space = spaces.get("space-a");
      user = spaces.requester(space, "user", Set.of());
      other = spaces.requester(space, "other", Set.of());
    }

    SupportSpaceInput input(String id, String version, String provider, boolean enabled) {
      return new SupportSpaceInput(
          id,
          version,
          "Support",
          enabled,
          true,
          provider,
          "tpb-" + provider,
          "https://runtime.example",
          "X-API-KEY",
          new IdentityInput(
              IdentityMode.SUPABASE,
              "https://identity.example",
              "https://identity.example/jwks",
              "authenticated",
              "users",
              null),
          "connector-secret-12345678901234567890",
          List.of("https://page.example"),
          List.of("knowledge"),
          30,
          null);
    }

    Ticket create(String key) {
      return tickets.create(
          space,
          user,
          new CreateInput("Guide issue", "Private issue details", "knowledge", null),
          key,
          null);
    }

    @Override
    public void close() {
      db.close();
    }
  }

  static void rejected(int status, org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
    assertThatThrownBy(action)
        .isInstanceOfSatisfying(
            TicketException.class, e -> assertThat(e.status()).isEqualTo(status));
  }

  @ParameterizedTest
  @ValueSource(strings = {"h2", "postgres"})
  void saveOwnIsolationOpaqueRevisionStatusAndSeenWithoutRuntime(String engine) throws Exception {
    try (var f = new Fixture(engine)) {
      Ticket t = f.create("create");
      assertThat(t.version()).isEqualTo("0");
      assertThat(t.status()).isEqualTo(Status.OPEN);
      assertThat(t.conversation().delivery()).isEqualTo(Delivery.PENDING);
      assertThat(f.create("create")).isEqualTo(t);
      rejected(
          409,
          () ->
              f.tickets.create(
                  f.space,
                  f.user,
                  new CreateInput("changed", "details", null, null),
                  "create",
                  null));
      rejected(404, () -> f.tickets.get(f.space, f.other, t.id()));
      var b = f.spaces.get("space-b");
      var userB = f.spaces.requester(b, "user", Set.of());
      rejected(404, () -> f.tickets.get(b, userB, t.id()));
      assertThat(f.tickets.list(f.space, f.other, null, null, 20).items()).isEmpty();
      assertThat(f.tickets.list(f.space, f.user, Status.OPEN, null, 20).items()).hasSize(1);
      rejected(400, () -> f.tickets.list(f.space, f.user, null, null, 51));
      Ticket resolved =
          f.tickets.change(f.space, f.user, t.id(), "RESOLVE", new VersionInput("0"), "resolve");
      assertThat(resolved.version()).isEqualTo("1");
      assertThat(resolved.status()).isEqualTo(Status.RESOLVED);
      assertThat(
              f.tickets.change(
                  f.space, f.user, t.id(), "RESOLVE", new VersionInput("0"), "resolve"))
          .isEqualTo(resolved);
      rejected(
          409,
          () ->
              f.tickets.change(f.space, f.user, t.id(), "REOPEN", new VersionInput("0"), "stale"));
      Ticket reopened =
          f.tickets.change(f.space, f.user, t.id(), "REOPEN", new VersionInput("1"), "reopen");
      Ticket seen =
          f.tickets.seen(
              f.space,
              f.user,
              t.id(),
              new SeenInput(reopened.version(), reopened.updatedAt()),
              "seen");
      assertThat(seen.unread()).isFalse();
      assertThat(seen.status()).isEqualTo(Status.OPEN);
      rejected(
          400,
          () ->
              f.tickets.seen(
                  f.space,
                  f.user,
                  t.id(),
                  new SeenInput(seen.version(), Instant.now().plusSeconds(3600)),
                  "future"));
      String encrypted =
          f.jdbc.queryForObject(
              "SELECT protected_intake FROM desk_support_ticket WHERE request_id=?",
              String.class,
              t.id());
      assertThat(encrypted).doesNotContain("Private issue details", "Guide issue");
      verifyNoInteractions(f.runtime);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"h2", "postgres"})
  void startsOneSavedOpeningKeyAndUncertaintySurvivesRetry(String engine) throws Exception {
    try (var f = new Fixture(engine)) {
      Ticket t = f.create("start-create");
      RuntimeCredential credential = new RuntimeCredential("Bearer runtime-token", null);
      when(f.runtime.start(
              eq(f.space),
              eq(f.user),
              eq(credential),
              eq(t.runtimePrincipal()),
              anyString(),
              anyString(),
              anyString()))
          .thenThrow(new TicketException(503, "RUNTIME_UNAVAILABLE", "Unavailable"))
          .thenReturn(Delivery.DELIVERED);
      rejected(
          503,
          () ->
              f.tickets.start(f.space, f.user, t.id(), new VersionInput("0"), "start", credential));
      assertThat(f.tickets.get(f.space, f.user, t.id()).conversation().delivery())
          .isEqualTo(Delivery.UNKNOWN);
      Ticket delivered =
          f.tickets.start(f.space, f.user, t.id(), new VersionInput("0"), "retry", credential);
      assertThat(delivered.conversation().delivery()).isEqualTo(Delivery.DELIVERED);
      f.tickets.start(f.space, f.user, t.id(), new VersionInput("0"), "again", credential);
      var keys = org.mockito.ArgumentCaptor.forClass(String.class);
      verify(f.runtime, times(2))
          .start(
              eq(f.space),
              eq(f.user),
              eq(credential),
              eq(t.runtimePrincipal()),
              eq(t.conversation().conversationId()),
              keys.capture(),
              eq("Guide issue\n\nPrivate issue details"));
      assertThat(keys.getAllValues().getFirst()).isEqualTo(keys.getAllValues().getLast());
      rejected(
          404,
          () ->
              f.tickets.start(
                  f.space, f.other, t.id(), new VersionInput("0"), "foreign", credential));
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"h2", "postgres"})
  void chatAndActionOriginsNeverDuplicateOpeningAndTerminalEpisodeIsExplicit(String engine)
      throws Exception {
    try (var f = new Fixture(engine)) {
      var credential = new RuntimeCredential("Bearer runtime-token", null);
      Ticket linked =
          f.tickets.create(
              f.space,
              f.user,
              new CreateInput("Existing issue", "Already sent", null, "existing-conversation"),
              "link-create",
              credential);
      assertThat(linked.openingMessageRequired()).isFalse();
      assertThat(linked.conversation().delivery()).isEqualTo(Delivery.NOT_REQUIRED);
      f.tickets.start(f.space, f.user, linked.id(), new VersionInput("0"), "start", credential);
      f.tickets.start(
          f.space, f.user, linked.id(), new VersionInput("0"), "start-new-key", credential);
      verify(f.runtime, never()).start(any(), any(), any(), any(), any(), any(), any());
      Ticket resolved =
          f.tickets.change(
              f.space, f.user, linked.id(), "RESOLVE", new VersionInput("0"), "resolve");
      Ticket open =
          f.tickets.change(
              f.space,
              f.user,
              linked.id(),
              "REOPEN",
              new VersionInput(resolved.version()),
              "reopen");
      when(f.runtime.terminal(
              f.space, f.user, credential, linked.runtimePrincipal(), "existing-conversation"))
          .thenReturn(true);
      Ticket next =
          f.tickets.newEpisode(
              f.space,
              f.user,
              linked.id(),
              new VersionInput(open.version()),
              "episode",
              credential);
      assertThat(next.episodes()).hasSize(2);
      assertThat(next.openingMessageRequired()).isFalse();
      assertThat(next.conversation().delivery()).isEqualTo(Delivery.NOT_REQUIRED);
      assertThat(next.conversation().conversationId()).isNotEqualTo("existing-conversation");
      assertThat(next.episodes().getFirst()).isEqualTo(linked.episodes().getFirst());
      verify(f.runtime)
          .initialize(
              f.space,
              f.user,
              credential,
              linked.runtimePrincipal(),
              next.conversation().conversationId());
      f.tickets.start(
          f.space,
          f.user,
          linked.id(),
          new VersionInput(next.version()),
          "continuation-start",
          credential);
      verify(f.runtime, never()).start(any(), any(), any(), any(), any(), any(), any());
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"h2", "postgres"})
  void agentsUseCurrentSupportNotOwnerOrReviewerAndPurgeIsExact(String engine) throws Exception {
    try (var f = new Fixture(engine)) {
      var owner = f.directory.register(identity("owner-a"));
      var support = f.directory.register(identity("support"));
      var reviewer = f.directory.register(identity("reviewer"));
      f.directory.assign("a", owner, new AssignmentInput(null, support.id(), Set.of(Role.SUPPORT)));
      f.directory.assign(
          "a", owner, new AssignmentInput(null, reviewer.id(), Set.of(Role.REVIEWER)));
      Ticket t =
          f.tickets.create(
              f.space, f.user, new CreateInput("Linked", "details", null, "saved"), "create", null);
      assertThatThrownBy(() -> f.tickets.agentGet("a", owner, t.id()))
          .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
      assertThatThrownBy(() -> f.tickets.agentGet("a", reviewer, t.id()))
          .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
      assertThat(f.tickets.agentList("a", support, null, null, 20).items()).hasSize(1);
      Ticket closed =
          f.tickets.agentResolve("a", support, t.id(), new VersionInput("0"), "resolve");
      assertThat(closed.status()).isEqualTo(Status.RESOLVED);
      f.directory.revoke("a", owner, support.id());
      assertThatThrownBy(() -> f.tickets.agentGet("a", support, t.id()))
          .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
      rejected(
          409,
          () ->
              f.tickets.purge(
                  "a",
                  t.id(),
                  new PurgeInput(closed.version(), ISSUER, "owner-a", false, "Retention"),
                  "too-soon"));
      Ticket other = f.create("another");
      Ticket otherClosed =
          f.tickets.change(
              f.space, f.user, other.id(), "RESOLVE", new VersionInput("0"), "other-close");
      rejected(
          409,
          () ->
              f.tickets.purge(
                  "a",
                  other.id(),
                  new PurgeInput(otherClosed.version(), ISSUER, "owner-a", true, "Test"),
                  "pending"));
      var input =
          new PurgeInput(closed.version(), ISSUER, "owner-a", true, "Disposable test record");
      assertThat(f.tickets.purge("a", t.id(), input, "purge").purged()).isTrue();
      assertThat(f.tickets.purge("a", t.id(), input, "purge").purged()).isTrue();
      rejected(404, () -> f.tickets.get(f.space, f.user, t.id()));
      assertThat(f.tickets.get(f.space, f.user, other.id()).id()).isEqualTo(other.id());
      assertThat(
              f.jdbc.queryForObject(
                  "SELECT COUNT(*) FROM desk_support_ticket_tombstone", Long.class))
          .isEqualTo(1);
      assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM desk_binding", Long.class))
          .isEqualTo(2);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"h2", "postgres"})
  void opaquePaginationAndObservedActivityUseVerifiedScope(String engine) throws Exception {
    try (var f = new Fixture(engine)) {
      Ticket first = f.create("a");
      f.create("b");
      f.create("c");
      Page one = f.tickets.list(f.space, f.user, null, null, 1);
      assertThat(one.items()).hasSize(1);
      assertThat(one.nextCursor()).isNotNull();
      Page two = f.tickets.list(f.space, f.user, null, one.nextCursor(), 1);
      assertThat(two.items().getFirst().id()).isNotEqualTo(one.items().getFirst().id());
      rejected(400, () -> f.tickets.list(f.space, f.other, null, one.nextCursor(), 1));
      rejected(400, () -> f.tickets.list(f.space, f.user, Status.RESOLVED, one.nextCursor(), 1));
      rejected(400, () -> f.tickets.list(f.space, f.user, null, "untrusted", 1));
      Ticket seen =
          f.tickets.seen(
              f.space,
              f.user,
              first.id(),
              new SeenInput(first.version(), first.updatedAt()),
              "seen");
      assertThat(seen.unread()).isFalse();
      RuntimeCredential credential = new RuntimeCredential("Bearer runtime", null);
      when(f.runtime.activity(
              f.space,
              f.user,
              credential,
              first.runtimePrincipal(),
              first.conversation().conversationId()))
          .thenReturn(first.updatedAt().plusSeconds(1));
      Ticket active =
          f.tickets.activity(
              f.space,
              f.user,
              first.id(),
              new VersionInput(seen.version()),
              "activity",
              credential);
      assertThat(active.unread()).isTrue();
      assertThat(active.status()).isEqualTo(Status.OPEN);
      assertThat(active.updatedAt()).isEqualTo(first.updatedAt().plusSeconds(1));
      rejected(
          404,
          () ->
              f.tickets.activity(
                  f.space,
                  f.other,
                  first.id(),
                  new VersionInput(active.version()),
                  "foreign",
                  credential));
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"h2", "postgres"})
  void concurrentCreateReplayIsSingleTicketAndDisableGatesApisWithoutDeletingData(String engine)
      throws Exception {
    try (var f = new Fixture(engine);
        var pool = Executors.newFixedThreadPool(4)) {
      var tasks =
          java.util.stream.IntStream.range(0, 4)
              .mapToObj(i -> (java.util.concurrent.Callable<Ticket>) () -> f.create("same-key"))
              .toList();
      var results = pool.invokeAll(tasks);
      String id = results.getFirst().get().id();
      for (var result : results) assertThat(result.get().id()).isEqualTo(id);
      assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM desk_support_ticket", Long.class))
          .isEqualTo(1);
      f.tx.execute(s -> f.spaces.put("a", f.input("space-a", "0", "desk", false)));
      Space disabled = f.spaces.get("space-a");
      assertThat(disabled.view().capabilities()).isEmpty();
      rejected(409, () -> f.tickets.get(disabled, f.user, id));
      rejected(409, () -> f.tickets.list(disabled, f.user, null, null, 20));
      rejected(
          409,
          () ->
              f.tickets.create(
                  disabled, f.user, new CreateInput("new", "details", null, null), "new", null));
      rejected(409, () -> f.create("stale-config"));
      assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM desk_support_ticket", Long.class))
          .isEqualTo(1);
      f.tx.execute(s -> f.spaces.put("a", f.input("space-a", "1", "desk", true)));
      assertThat(f.tickets.get(f.spaces.get("space-a"), f.user, id).id()).isEqualTo(id);
      verifyNoInteractions(f.runtime);
    }
  }
}
