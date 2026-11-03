package com.loomai.desk;

import static com.loomai.desk.SupportTicketsTest.rejected;
import static com.loomai.desk.ticket.TicketContracts.*;
import static org.assertj.core.api.Assertions.*;

import com.loomai.desk.ticket.*;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class TicketProviderContractTest {
  @ParameterizedTest
  @CsvSource({"h2,desk", "postgres,desk", "h2,upstream", "postgres,upstream"})
  void sameFacadeEnforcesOwnershipConditionalVersionsAndOriginalReceipts(
      String engine, String provider) throws Exception {
    FakeUpstream upstream = new FakeUpstream();
    try (var f =
        new SupportTicketsTest.Fixture(
            engine, provider.equals("desk") ? null : new FakeAdapter(upstream))) {
      Ticket a = f.create("create");
      assertThat(f.create("create")).isEqualTo(a);
      assertThat(f.tickets.list(f.space, f.user, Status.OPEN, null, 20).items()).hasSize(1);
      assertThat(f.tickets.list(f.space, f.other, null, null, 20).items()).isEmpty();
      rejected(404, () -> f.tickets.get(f.space, f.other, a.id()));
      rejected(
          409,
          () ->
              f.tickets.change(
                  f.space,
                  f.user,
                  a.id(),
                  "RESOLVE",
                  new VersionInput("not-the-current-version"),
                  "stale"));
      Ticket resolved =
          f.tickets.change(
              f.space, f.user, a.id(), "RESOLVE", new VersionInput(a.version()), "resolve");
      assertThat(resolved.status()).isEqualTo(Status.RESOLVED);
      assertThat(resolved.version()).isNotEqualTo(a.version());
      assertThat(
              f.tickets.change(
                  f.space, f.user, a.id(), "RESOLVE", new VersionInput(a.version()), "resolve"))
          .isEqualTo(resolved);
      if (provider.equals("upstream")) {
        assertThat(a.version()).startsWith("W/\"");
        rejected(
            422,
            () ->
                f.tickets.change(
                    f.space,
                    f.user,
                    a.id(),
                    "CANCEL",
                    new VersionInput(resolved.version()),
                    "cancel"));
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM desk_support_ticket", Long.class))
            .isZero();
        assertThat(upstream.writes).isEqualTo(2);
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM desk_support_request", Long.class))
            .isEqualTo(1);
      }
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"h2", "postgres"})
  void upstreamOutageNeverFallsBackAndChangingSelectionPreservesProvenance(String engine)
      throws Exception {
    FakeUpstream upstream = new FakeUpstream();
    FakeAdapter external = new FakeAdapter(upstream);
    try (var f = new SupportTicketsTest.Fixture(engine, external)) {
      Ticket old = f.create("old");
      upstream.unavailable = true;
      rejected(503, () -> f.create("failed"));
      assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM desk_support_ticket", Long.class))
          .isZero();
      assertThat(upstream.writes).isEqualTo(1);
      upstream.unavailable = false;
      f.tx.execute(status -> f.spaces.put("a", f.input("space-a", "0", "desk", true)));
      Space current = f.spaces.get("space-a");
      Ticket nativeTicket =
          f.tickets.create(
              current, f.user, new CreateInput("Native", "details", null, null), "native", null);
      assertThat(nativeTicket.providerBindingId()).isEqualTo("tpb-desk");
      assertThat(f.tickets.get(current, f.user, old.id()).providerBindingId())
          .isEqualTo("tpb-fake-upstream");
      assertThat(f.tickets.list(current, f.user, null, null, 20).items()).hasSize(2);
      assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM desk_support_ticket", Long.class))
          .isEqualTo(1);
    }
  }

  /** Independent upstream authority; no JDBC, native ticket repository or copied payload. */
  static final class FakeUpstream {
    final Map<String, Stored> data = new HashMap<>();
    int writes;
    boolean unavailable;

    void available() {
      if (unavailable)
        throw new TicketException(503, "UPSTREAM_UNAVAILABLE", "Fake upstream unavailable.");
    }
  }

  record Stored(Scope owner, TicketData data) {}

  static final class FakeAdapter implements TicketProvider {
    final FakeUpstream upstream;

    FakeAdapter(FakeUpstream upstream) {
      this.upstream = upstream;
    }

    public String id() {
      return "fake-upstream";
    }

    public Set<Capability> capabilities() {
      return Set.of(
          Capability.CREATE,
          Capability.LIST,
          Capability.GET,
          Capability.LINK_CONVERSATION,
          Capability.RESOLVE);
    }

    public TicketData createRequest(Scope scope, CreateInput in, String key) {
      upstream.available();
      Instant now = Instant.now();
      String id = "external-" + UUID.randomUUID();
      TicketData t =
          new TicketData(
              id,
              "EXT-42",
              scope.providerBindingId(),
              in.subject(),
              in.details(),
              in.topic(),
              Status.OPEN,
              "W/\"etag-a\"",
              now,
              now,
              "New",
              true);
      upstream.data.put(id, new Stored(scope, t));
      upstream.writes++;
      return t;
    }

    public DataPage listOwnRequests(Scope scope, Status status, String cursor, int limit) {
      upstream.available();
      var found =
          upstream.data.values().stream()
              .filter(s -> s.owner().equals(scope))
              .map(Stored::data)
              .filter(t -> status == null || t.status() == status)
              .filter(t -> cursor == null || t.id().compareTo(cursor) > 0)
              .sorted(Comparator.comparing(TicketData::id))
              .limit(limit + 1)
              .toList();
      return new DataPage(
          found.size() > limit ? found.subList(0, limit) : found,
          found.size() > limit ? found.get(limit - 1).id() : null);
    }

    public TicketData getOwnRequest(Scope scope, String id) {
      upstream.available();
      Stored s = upstream.data.get(id);
      if (s == null || !s.owner().equals(scope)) throw TicketException.missing();
      return s.data();
    }

    public TicketData linkConversation(
        Scope scope, String id, String version, String conversation, String key) {
      return mutate(scope, id, version, null);
    }

    public TicketData resolveRequest(Scope scope, String id, String version, String key) {
      return mutate(scope, id, version, Status.RESOLVED);
    }

    private TicketData mutate(Scope s, String id, String version, Status target) {
      TicketData old = getOwnRequest(s, id);
      if (!old.version().equals(version))
        throw new TicketException(409, "UPSTREAM_ETAG_CONFLICT", "Upstream version changed.", old);
      TicketData t =
          new TicketData(
              old.id(),
              old.reference(),
              old.providerBindingId(),
              old.subject(),
              old.details(),
              old.topic(),
              target == null ? old.status() : target,
              "W/\"" + UUID.randomUUID() + "\"",
              old.createdAt(),
              Instant.now(),
              target == null ? old.upstreamStatus() : "Done",
              true);
      upstream.data.put(id, new Stored(s, t));
      upstream.writes++;
      return t;
    }
  }
}
