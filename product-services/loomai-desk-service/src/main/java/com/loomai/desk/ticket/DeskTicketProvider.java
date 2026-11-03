package com.loomai.desk.ticket;

import static com.loomai.desk.ticket.TicketContracts.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loomai.desk.channel.config.BasicInboxProperties;
import com.loomai.desk.channel.service.InboxSecurityService;
import com.loomai.desk.config.DeskProperties;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DeskTicketProvider implements TicketProvider {
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;
  private final InboxSecurityService secrets;

  public DeskTicketProvider(JdbcTemplate jdbc, ObjectMapper json, DeskProperties properties) {
    this.jdbc = jdbc;
    this.json = json;
    BasicInboxProperties p = new BasicInboxProperties();
    p.setDataEncryptionSecret(properties.getDataEncryptionSecret());
    secrets = new InboxSecurityService(p);
  }

  @Override
  public String id() {
    return "desk";
  }

  @Override
  public Set<Capability> capabilities() {
    return Set.of(Capability.values());
  }

  @Override
  public TicketData createRequest(Scope s, CreateInput input, String key) {
    String id =
        "req-" + secrets.sha256(s.spaceId() + "\n" + s.requesterId() + "\n" + key).substring(0, 48);
    var existing =
        jdbc.query(
            "SELECT * FROM desk_support_ticket WHERE space_id=? AND request_id=?",
            (r, n) -> read(r),
            s.spaceId(),
            id);
    if (!existing.isEmpty()) {
      TicketData t = getOwnRequest(s, id);
      if (!t.subject().equals(input.subject())
          || !t.details().equals(input.details())
          || !java.util.Objects.equals(t.topic(), input.topic()))
        throw new TicketException(
            409, "IDEMPOTENCY_CONFLICT", "Command key was used with different input.");
      return t;
    }
    Timestamp now = Timestamp.from(Instant.now());
    jdbc.update(
        "INSERT INTO"
            + " desk_support_ticket(space_id,request_id,requester_id,provider_binding_id,reference,protected_intake,status,version,created_at,updated_at)"
            + " VALUES(?,?,?,?,?,?,'OPEN',0,?,?)",
        s.spaceId(),
        id,
        s.requesterId(),
        s.providerBindingId(),
        "SUP-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(java.util.Locale.ROOT),
        encrypt(input),
        now,
        now);
    return getOwnRequest(s, id);
  }

  @Override
  public DataPage listOwnRequests(Scope s, Status status, String cursor, int limit) {
    return list(s, status, cursor, limit, false);
  }

  @Override
  public DataPage listAgentRequests(Scope s, Status status, String cursor, int limit) {
    return list(s, status, cursor, limit, true);
  }

  private DataPage list(Scope s, Status status, String cursor, int limit, boolean agent) {
    String sql =
        "SELECT * FROM desk_support_ticket WHERE space_id=? AND provider_binding_id=? AND"
            + " request_id>?";
    java.util.List<Object> args =
        new java.util.ArrayList<>(
            java.util.List.of(s.spaceId(), s.providerBindingId(), cursor == null ? "" : cursor));
    if (!agent) {
      sql += " AND requester_id=?";
      args.add(s.requesterId());
    }
    if (status != null) {
      sql += " AND status=?";
      args.add(status.name());
    }
    sql += " ORDER BY request_id LIMIT ?";
    args.add(limit + 1);
    var found = jdbc.query(sql, (r, n) -> read(r), args.toArray());
    boolean more = found.size() > limit;
    var items = more ? found.subList(0, limit) : found;
    return new DataPage(java.util.List.copyOf(items), more ? items.getLast().id() : null);
  }

  @Override
  public TicketData getOwnRequest(Scope s, String id) {
    return get(s, id, false);
  }

  @Override
  public TicketData getAgentRequest(Scope s, String id) {
    return get(s, id, true);
  }

  private TicketData get(Scope s, String id, boolean agent) {
    String sql =
        "SELECT * FROM desk_support_ticket WHERE space_id=? AND provider_binding_id=? AND"
            + " request_id=?"
            + (agent ? "" : " AND requester_id=?");
    Object[] args =
        agent
            ? new Object[] {s.spaceId(), s.providerBindingId(), id}
            : new Object[] {s.spaceId(), s.providerBindingId(), id, s.requesterId()};
    return jdbc.query(sql, (r, n) -> read(r), args).stream()
        .findFirst()
        .orElseThrow(TicketException::missing);
  }

  @Override
  public TicketData resolveRequest(Scope s, String id, String v, String key) {
    return transition(s, id, v, Status.RESOLVED, false);
  }

  @Override
  public TicketData resolveAgentRequest(Scope s, String id, String v, String key) {
    return transition(s, id, v, Status.RESOLVED, true);
  }

  @Override
  public TicketData cancelRequest(Scope s, String id, String v, String key) {
    return transition(s, id, v, Status.CANCELLED, false);
  }

  @Override
  public TicketData reopenRequest(Scope s, String id, String v, String key) {
    return transition(s, id, v, Status.OPEN, false);
  }

  private TicketData transition(Scope s, String id, String v, Status target, boolean agent) {
    TicketData t = get(s, id, agent);
    version(t, v);
    if (target == Status.OPEN ? t.status() == Status.OPEN : t.status() != Status.OPEN)
      throw new TicketException(
          409, "TICKET_STATUS_CONFLICT", "This status transition is not available.", t);
    Instant now = Instant.now();
    update(
        s,
        id,
        v,
        "status=?,closed_at=?,updated_at=?,",
        target.name(),
        target == Status.OPEN ? null : Timestamp.from(now),
        Timestamp.from(now));
    return get(s, id, agent);
  }

  @Override
  public TicketData linkConversation(
      Scope s, String id, String v, String conversation, String key) {
    TicketData t = getOwnRequest(s, id);
    version(t, v);
    update(s, id, v, "updated_at=?,", Timestamp.from(Instant.now()));
    return getOwnRequest(s, id);
  }

  @Override
  public TicketData seen(Scope s, String id, String v, Instant through, String key) {
    TicketData t = getOwnRequest(s, id);
    version(t, v);
    if (through == null || through.isAfter(t.updatedAt()))
      throw TicketException.invalid("seenThrough");
    update(
        s,
        id,
        v,
        "seen_at=CASE WHEN seen_at IS NULL OR seen_at<? THEN ? ELSE seen_at END,",
        Timestamp.from(through),
        Timestamp.from(through));
    return getOwnRequest(s, id);
  }

  @Override
  public void purge(Scope s, String id, String v, boolean test, int retention) {
    TicketData t = getAgentRequest(s, id);
    version(t, v);
    Timestamp closedAt =
        jdbc.queryForObject(
            "SELECT closed_at FROM desk_support_ticket WHERE space_id=? AND request_id=?",
            Timestamp.class,
            s.spaceId(),
            id);
    Instant closed = closedAt == null ? null : closedAt.toInstant();
    if (t.status() == Status.OPEN
        || closed == null
        || !test && closed.isAfter(Instant.now().minus(retention, ChronoUnit.DAYS)))
      throw new TicketException(
          409,
          "TICKET_PURGE_INELIGIBLE",
          "Only closed test or retention-eligible requests may be purged.");
    jdbc.update(
        "DELETE FROM desk_support_ticket WHERE space_id=? AND request_id=?", s.spaceId(), id);
  }

  @Override
  public TicketData observeActivity(Scope s, String id, String v, Instant activity, String key) {
    TicketData current = getOwnRequest(s, id);
    version(current, v);
    if (activity == null || activity.isAfter(Instant.now().plusSeconds(30)))
      throw TicketException.invalid("runtime activity");
    if (!activity.isAfter(current.updatedAt())) return current;
    update(s, id, v, "updated_at=?,", Timestamp.from(activity));
    return getOwnRequest(s, id);
  }

  private void update(Scope s, String id, String v, String changes, Object... values) {
    var args = new java.util.ArrayList<>(java.util.Arrays.asList(values));
    args.add(s.spaceId());
    args.add(id);
    args.add(Long.parseLong(v));
    if (jdbc.update(
            "UPDATE desk_support_ticket SET "
                + changes
                + "version=version+1 WHERE space_id=? AND request_id=? AND version=?",
            args.toArray())
        != 1)
      throw new TicketException(
          409, "TICKET_VERSION_CONFLICT", "Refresh this request before changing it.");
  }

  private void version(TicketData t, String v) {
    if (!t.version().equals(v))
      throw new TicketException(
          409, "TICKET_VERSION_CONFLICT", "Refresh this request before changing it.", t);
  }

  private String encrypt(CreateInput input) {
    try {
      return secrets.encrypt(
          json.writeValueAsString(
              new CreateInput(input.subject(), input.details(), input.topic(), null)));
    } catch (Exception e) {
      throw new IllegalStateException("Ticket serialization failed.");
    }
  }

  private TicketData read(ResultSet r) throws SQLException {
    CreateInput i;
    try {
      i = json.readValue(secrets.decrypt(r.getString("protected_intake")), CreateInput.class);
    } catch (Exception e) {
      throw new IllegalStateException("Ticket storage is invalid.");
    }
    Instant updated = r.getTimestamp("updated_at").toInstant();
    Timestamp seen = r.getTimestamp("seen_at");
    return new TicketData(
        r.getString("request_id"),
        r.getString("reference"),
        r.getString("provider_binding_id"),
        i.subject(),
        i.details(),
        i.topic(),
        Status.valueOf(r.getString("status")),
        Long.toString(r.getLong("version")),
        r.getTimestamp("created_at").toInstant(),
        updated,
        null,
        seen == null || seen.toInstant().isBefore(updated));
  }
}
