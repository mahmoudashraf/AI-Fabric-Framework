package com.loomai.desk.ticket;

import static com.loomai.desk.ticket.TicketContracts.*;

import com.loomai.desk.model.DeskContracts.Operator;
import com.loomai.desk.model.DeskContracts.Role;
import com.loomai.desk.model.DeskContracts.VerifiedIdentity;
import com.loomai.desk.service.DeskDirectory;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class SupportTickets {
  private final SupportSpaces spaces;
  private final TicketRuntimeClient runtime;
  private final DeskDirectory directory;
  private final TransactionTemplate tx;

  public SupportTickets(
      SupportSpaces spaces,
      TicketRuntimeClient runtime,
      DeskDirectory directory,
      PlatformTransactionManager manager) {
    this.spaces = spaces;
    this.runtime = runtime;
    this.directory = directory;
    tx = new TransactionTemplate(manager);
  }

  private record CreateCommand(
      CreateInput input, String runtimePrincipal, String runtimeConversationId) {}

  public Ticket create(
      Space s, Requester r, CreateInput in, String key, RuntimeCredential credential) {
    return create(s, r, in, key, credential, null);
  }

  Ticket createFromRuntimeTrace(
      Space s, Requester r, CreateInput in, String key, VerifiedRuntimeTrace proof) {
    if (proof == null
        || s.serverConnector() == null
        || s.identity().mode() != IdentityMode.SUPABASE
        || !s.view().spaceId().equals(proof.spaceId())
        || !r.id().equals(proof.requesterId())
        || !r.subject().equals(proof.subjectId())
        || proof.conversationId() == null
        || !proof.conversationId().equals(in.existingConversationId()))
      throw new TicketException(
          403, "RUNTIME_TRACE_INVALID", "A verified current runtime conversation is required.");
    return create(s, r, in, key, null, proof);
  }

  private Ticket create(
      Space s,
      Requester r,
      CreateInput in,
      String key,
      RuntimeCredential credential,
      VerifiedRuntimeTrace trace) {
    CreateCommand command = new CreateCommand(in, r.runtimePrincipal(), r.runtimeConversationId());
    return transaction(
        s,
        () -> {
          enabled(s);
          Ticket replay = replay(s, r.id(), key, "CREATE", command, Ticket.class);
          if (replay != null) return replay;
          validate(in, s);
          spaces.register(s, r);
          TicketProvider p = spaces.provider(s.view().providerId());
          capability(p, Capability.CREATE);
          boolean privateHost = s.identity().mode() == IdentityMode.TRUSTED_PRIVATE_HOST;
          String adoptedPrincipal = null;
          if (in.existingConversationId() != null) {
            capability(p, Capability.LINK_CONVERSATION);
            adoptedPrincipal = privateHost ? r.runtimePrincipal() : r.subject();
            if (privateHost) {
              if (adoptedPrincipal == null)
                throw new TicketException(
                    403,
                    "HOST_RUNTIME_BINDING_REQUIRED",
                    "The host must sign the existing runtime principal and conversation binding.");
              SupportSpaces.text(adoptedPrincipal, 256, "signed runtimePrincipal");
              if (!in.existingConversationId().equals(r.runtimeConversationId()))
                throw new TicketException(
                    403,
                    "RUNTIME_CONVERSATION_ASSERTION_MISMATCH",
                    "The host assertion must bind this existing conversation.");
            }
            // Only the explicit service-authenticated connector route can supply this typed trace.
            if (trace == null)
              runtime.verify(s, r, credential, adoptedPrincipal, in.existingConversationId());
            if (spaces
                    .jdbc()
                    .queryForObject(
                        "SELECT COUNT(*) FROM desk_support_ticket_episode WHERE space_id=? AND"
                            + " conversation_id=?",
                        Long.class,
                        s.view().spaceId(),
                        in.existingConversationId())
                > 0)
              throw new TicketException(
                  409,
                  "CONVERSATION_ALREADY_LINKED",
                  "This conversation is already linked to a request.");
            if (privateHost
                && spaces
                        .jdbc()
                        .queryForObject(
                            "SELECT COUNT(*) FROM desk_support_request WHERE space_id=? AND"
                                + " private_runtime_principal=?",
                            Long.class,
                            s.view().spaceId(),
                            adoptedPrincipal)
                    > 0)
              throw new TicketException(
                  409,
                  "RUNTIME_PRINCIPAL_ALREADY_BOUND",
                  "This private runtime principal is already bound to a request.");
          } else if (r.runtimePrincipal() != null || r.runtimeConversationId() != null) {
            throw TicketException.invalid("runtime binding claims without existingConversationId");
          }
          Scope scope = new Scope(s.view().spaceId(), s.view().providerBindingId(), r.id());
          TicketData data = p.createRequest(scope, in, key);
          SupportSpaces.text(data.id(), 160, "provider requestId");
          if (!scope.providerBindingId().equals(data.providerBindingId()))
            throw new TicketException(
                503, "TICKET_PROVIDER_RESPONSE_INVALID", "Provider returned an invalid binding.");
          String principal =
              adoptedPrincipal != null
                  ? adoptedPrincipal
                  : privateHost ? "support-ticket:" + data.id() : r.subject();
          spaces
              .jdbc()
              .update(
                  "INSERT INTO"
                      + " desk_support_request(space_id,request_id,requester_id,provider_id,provider_binding_id,runtime_principal,private_runtime_principal)"
                      + " VALUES(?,?,?,?,?,?,?)",
                  scope.spaceId(),
                  data.id(),
                  r.id(),
                  p.id(),
                  scope.providerBindingId(),
                  principal,
                  privateHost ? principal : null);
          boolean linked = in.existingConversationId() != null;
          episode(s, data.id(), linked ? in.existingConversationId() : newId("support"), !linked);
          event(s, data.id(), r.id(), linked ? "CONVERSATION_LINKED" : "CREATED");
          return receipt(s, r.id(), key, "CREATE", command, data.id(), project(s, data, p));
        });
  }

  public Ticket get(Space s, Requester r, String id) {
    Mapping m = own(s, r, id);
    var p = spaces.provider(m.provider());
    return project(s, p.getOwnRequest(m.scope(s), id), p);
  }

  public Page list(Space s, Requester r, Status status, String cursor, int limit) {
    return list(s, r, status, cursor, limit, false);
  }

  private record ListCursor(
      String space,
      String requester,
      String status,
      String configurationVersion,
      int providerIndex,
      String upstreamCursor) {}

  private Page list(Space s, Requester r, Status status, String cursor, int limit, boolean agent) {
    enabled(s);
    if (limit < 1 || limit > 50 || cursor != null && cursor.length() > 16384)
      throw TicketException.invalid("pagination");
    String query =
        "SELECT DISTINCT provider_id,provider_binding_id FROM desk_support_request WHERE space_id=?"
            + (agent ? "" : " AND requester_id=?");
    Object[] args =
        agent ? new Object[] {s.view().spaceId()} : new Object[] {s.view().spaceId(), r.id()};
    var bindings =
        new ArrayList<>(
            spaces.jdbc().query(query, (rs, n) -> List.of(rs.getString(1), rs.getString(2)), args));
    var selected = List.of(s.view().providerId(), s.view().providerBindingId());
    if (!bindings.contains(selected)) bindings.add(selected);
    bindings.sort(Comparator.comparing(v -> v.getFirst() + ":" + v.get(1)));
    String requester = agent ? "agent" : r.id();
    String filter = status == null ? "" : status.name();
    ListCursor position =
        new ListCursor(s.view().spaceId(), requester, filter, s.view().version(), 0, null);
    if (cursor != null) {
      try {
        String encrypted =
            new String(
                java.util.Base64.getUrlDecoder().decode(cursor),
                java.nio.charset.StandardCharsets.UTF_8);
        position = spaces.readJson(spaces.secrets().decrypt(encrypted), ListCursor.class);
        if (!position.space().equals(s.view().spaceId())
            || !position.requester().equals(requester)
            || !position.status().equals(filter)
            || !position.configurationVersion().equals(s.view().version())
            || position.providerIndex() < 0
            || position.providerIndex() >= bindings.size())
          throw TicketException.invalid("cursor scope");
      } catch (Exception e) {
        throw TicketException.invalid("cursor");
      }
    }
    List<Ticket> all = new ArrayList<>();
    int index = position.providerIndex();
    String upstreamCursor = position.upstreamCursor();
    while (index < bindings.size() && all.size() < limit) {
      var binding = bindings.get(index);
      var p = spaces.provider(binding.getFirst());
      capability(p, agent ? Capability.AGENT_LIST : Capability.LIST);
      Scope scope = new Scope(s.view().spaceId(), binding.get(1), agent ? null : r.id());
      int remaining = limit - all.size();
      DataPage page =
          agent
              ? p.listAgentRequests(scope, status, upstreamCursor, remaining)
              : p.listOwnRequests(scope, status, upstreamCursor, remaining);
      if (page.items().size() > remaining)
        throw new TicketException(
            503, "TICKET_PROVIDER_RESPONSE_INVALID", "Provider returned an invalid page.");
      for (TicketData data : page.items()) all.add(project(s, data, p));
      if (page.nextCursor() != null) {
        upstreamCursor = page.nextCursor();
        break;
      }
      index++;
      upstreamCursor = null;
    }
    String next =
        index >= bindings.size()
            ? null
            : java.util.Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                    spaces
                        .secrets()
                        .encrypt(
                            spaces.write(
                                new ListCursor(
                                    s.view().spaceId(),
                                    requester,
                                    filter,
                                    s.view().version(),
                                    index,
                                    upstreamCursor)))
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    return new Page(List.copyOf(all), next);
  }

  public Ticket change(
      Space s, Requester r, String id, String action, VersionInput in, String key) {
    return transaction(
        s,
        () -> {
          Mapping m = own(s, r, id);
          var p = spaces.provider(m.provider());
          Ticket replay = replay(s, r.id(), key, action, List.of(id, in), Ticket.class);
          if (replay != null) return replay;
          expected(in.expectedVersion());
          Capability cap =
              switch (action) {
                case "RESOLVE" -> Capability.RESOLVE;
                case "CANCEL" -> Capability.CANCEL;
                case "REOPEN" -> Capability.REOPEN;
                default -> throw TicketException.invalid("action");
              };
          capability(p, cap);
          TicketData result =
              switch (action) {
                case "RESOLVE" -> p.resolveRequest(m.scope(s), id, in.expectedVersion(), key);
                case "CANCEL" -> p.cancelRequest(m.scope(s), id, in.expectedVersion(), key);
                default -> p.reopenRequest(m.scope(s), id, in.expectedVersion(), key);
              };
          event(s, id, r.id(), action);
          return receipt(s, r.id(), key, action, List.of(id, in), id, project(s, result, p));
        });
  }

  public Ticket link(
      Space s, Requester r, String id, LinkInput in, String key, RuntimeCredential credential) {
    return transaction(
        s,
        () -> {
          Mapping m = own(s, r, id);
          var p = spaces.provider(m.provider());
          capability(p, Capability.LINK_CONVERSATION);
          boundConversation(r, in.conversationId());
          Ticket replay = replay(s, r.id(), key, "LINK", List.of(id, in), Ticket.class);
          if (replay != null) return replay;
          expected(in.expectedVersion());
          runtime.verify(s, r, credential, m.runtimePrincipal(), in.conversationId());
          var data =
              p.linkConversation(m.scope(s), id, in.expectedVersion(), in.conversationId(), key);
          episode(s, id, in.conversationId(), false);
          event(s, id, r.id(), "CONVERSATION_LINKED");
          return receipt(s, r.id(), key, "LINK", List.of(id, in), id, project(s, data, p));
        });
  }

  public Ticket seen(Space s, Requester r, String id, SeenInput in, String key) {
    return transaction(
        s,
        () -> {
          Mapping m = own(s, r, id);
          var p = spaces.provider(m.provider());
          capability(p, Capability.SEEN);
          Ticket replay = replay(s, r.id(), key, "SEEN", List.of(id, in), Ticket.class);
          if (replay != null) return replay;
          expected(in.expectedVersion());
          return receipt(
              s,
              r.id(),
              key,
              "SEEN",
              List.of(id, in),
              id,
              project(s, p.seen(m.scope(s), id, in.expectedVersion(), in.seenThrough(), key), p));
        });
  }

  public Ticket activity(
      Space s, Requester r, String id, VersionInput in, String key, RuntimeCredential credential) {
    return transaction(
        s,
        () -> {
          Mapping m = own(s, r, id);
          var p = spaces.provider(m.provider());
          capability(p, Capability.OBSERVE_ACTIVITY);
          Ticket replay = replay(s, r.id(), key, "ACTIVITY", List.of(id, in), Ticket.class);
          if (replay != null) return replay;
          Ticket current = get(s, r, id);
          expected(in.expectedVersion());
          Episode latest = current.episodes().getLast();
          Space episodeSpace =
              new Space(
                  s.view(),
                  latest.runtimeBaseUrl(),
                  s.runtimeTrustedKeyHeader(),
                  s.identity(),
                  s.connectorKey(),
                  s.serverConnector());
          Instant observed =
              runtime.activity(
                  episodeSpace, r, credential, m.runtimePrincipal(), latest.conversationId());
          TicketData data = p.observeActivity(m.scope(s), id, in.expectedVersion(), observed, key);
          return receipt(s, r.id(), key, "ACTIVITY", List.of(id, in), id, project(s, data, p));
        });
  }

  private record Start(
      Ticket done,
      String episodeId,
      String conversationId,
      String messageKey,
      String opening,
      String runtimeBaseUrl) {}

  public Ticket start(
      Space s, Requester r, String id, VersionInput in, String key, RuntimeCredential credential) {
    Start work =
        transaction(
            s,
            () -> {
              Mapping m = own(s, r, id);
              var p = spaces.provider(m.provider());
              Ticket current = project(s, p.getOwnRequest(m.scope(s), id), p);
              boundConversation(r, current.conversation().conversationId());
              Ticket replay = replay(s, r.id(), key, "START", List.of(id, in), Ticket.class);
              if (replay != null) return new Start(replay, null, null, null, null, null);
              TicketData data = p.getOwnRequest(m.scope(s), id);
              expected(in.expectedVersion());
              Episode e = current.episodes().getLast();
              version(data, in.expectedVersion());
              if (!e.openingMessageRequired() || e.delivery() == Delivery.DELIVERED)
                return new Start(
                    receipt(s, r.id(), key, "START", List.of(id, in), id, current),
                    null,
                    null,
                    null,
                    null,
                    null);
              if (data.status() != Status.OPEN)
                throw new TicketException(
                    409,
                    "TICKET_CLOSED",
                    "Reopen the request before starting its conversation.",
                    current);
              Timestamp attempt =
                  spaces
                      .jdbc()
                      .queryForObject(
                          "SELECT attempt_at FROM desk_support_ticket_episode WHERE space_id=? AND"
                              + " episode_id=?",
                          Timestamp.class,
                          s.view().spaceId(),
                          e.episodeId());
              if (e.delivery() == Delivery.IN_FLIGHT
                  && attempt != null
                  && attempt.toInstant().isAfter(Instant.now().minusSeconds(120)))
                throw new TicketException(
                    409,
                    "TICKET_DELIVERY_IN_PROGRESS",
                    "Opening message delivery is still being observed.",
                    current);
              spaces
                  .jdbc()
                  .update(
                      "UPDATE desk_support_ticket_episode SET delivery='IN_FLIGHT',attempt_at=?"
                          + " WHERE space_id=? AND episode_id=?",
                      Timestamp.from(Instant.now()),
                      s.view().spaceId(),
                      e.episodeId());
              String messageKey =
                  spaces
                      .jdbc()
                      .queryForObject(
                          "SELECT message_key FROM desk_support_ticket_episode WHERE space_id=? AND"
                              + " episode_id=?",
                          String.class,
                          s.view().spaceId(),
                          e.episodeId());
              return new Start(
                  null,
                  e.episodeId(),
                  e.conversationId(),
                  messageKey,
                  data.subject() + "\n\n" + data.details(),
                  e.runtimeBaseUrl());
            });
    if (work.done() != null) return work.done();
    Delivery outcome;
    Space episodeSpace =
        new Space(
            s.view(),
            work.runtimeBaseUrl(),
            s.runtimeTrustedKeyHeader(),
            s.identity(),
            s.connectorKey(),
            s.serverConnector());
    try {
      outcome =
          runtime.start(
              episodeSpace,
              r,
              credential,
              mapping(s, id).runtimePrincipal(),
              work.conversationId(),
              work.messageKey(),
              work.opening());
    } catch (RuntimeException error) {
      Delivery failed =
          error instanceof TicketException e && Set.of(400, 401, 403, 404).contains(e.status())
              ? Delivery.PENDING
              : Delivery.UNKNOWN;
      transaction(
          s,
          () -> {
            delivery(s, id, r.id(), work.episodeId(), failed);
            return null;
          });
      throw error;
    }
    Delivery result = outcome;
    Ticket completed =
        transaction(
            s,
            () -> {
              delivery(s, id, r.id(), work.episodeId(), result);
              Ticket ticket = get(s, r, id);
              if (result != Delivery.DELIVERED) return ticket;
              return receipt(s, r.id(), key, "START", List.of(id, in), id, ticket);
            });
    if (result != Delivery.DELIVERED)
      throw new TicketException(
          503,
          "TICKET_DELIVERY_UNCERTAIN",
          "Request saved; opening message delivery is uncertain.",
          completed);
    return completed;
  }

  /**
   * Reopen stays a local status change; this explicit operation creates a fresh terminal episode.
   */
  public Ticket newEpisode(
      Space s, Requester r, String id, VersionInput in, String key, RuntimeCredential credential) {
    return transaction(
        s,
        () -> {
          Mapping m = own(s, r, id);
          var p = spaces.provider(m.provider());
          capability(p, Capability.LINK_CONVERSATION);
          Ticket replay = replay(s, r.id(), key, "NEW_EPISODE", List.of(id, in), Ticket.class);
          if (replay != null) return replay;
          Ticket current = get(s, r, id);
          version(p.getOwnRequest(m.scope(s), id), in.expectedVersion());
          Episode previous = current.episodes().getLast();
          Space previousSpace =
              new Space(
                  s.view(),
                  previous.runtimeBaseUrl(),
                  s.runtimeTrustedKeyHeader(),
                  s.identity(),
                  s.connectorKey(),
                  s.serverConnector());
          if (current.status() != Status.OPEN
              || !runtime.terminal(
                  previousSpace,
                  r,
                  credential,
                  m.runtimePrincipal(),
                  current.conversation().conversationId()))
            throw new TicketException(
                409,
                "CONVERSATION_NOT_TERMINAL",
                "Only an open request with a terminal conversation needs a new episode.");
          String conversation = newId("support");
          runtime.initialize(s, r, credential, m.runtimePrincipal(), conversation);
          TicketData data =
              p.linkConversation(m.scope(s), id, in.expectedVersion(), conversation, key);
          episode(s, id, conversation, false);
          event(s, id, r.id(), "EPISODE_CREATED");
          return receipt(s, r.id(), key, "NEW_EPISODE", List.of(id, in), id, project(s, data, p));
        });
  }

  public Page agentList(
      String binding, Operator operator, Status status, String cursor, int limit) {
    directory.authorize(binding, operator, Role.SUPPORT);
    return list(spaces.forBinding(binding), null, status, cursor, limit, true);
  }

  public Ticket agentGet(String binding, Operator operator, String id) {
    directory.authorize(binding, operator, Role.SUPPORT);
    Space s = spaces.forBinding(binding);
    enabled(s);
    Mapping m = mapping(s, id);
    var p = spaces.provider(m.provider());
    capability(p, Capability.AGENT_GET);
    return project(s, p.getAgentRequest(m.scope(s), id), p);
  }

  public Ticket agentResolve(
      String binding, Operator operator, String id, VersionInput in, String key) {
    directory.authorize(binding, operator, Role.SUPPORT);
    Space s = spaces.forBinding(binding);
    enabled(s);
    return transaction(
        s,
        () -> {
          directory.authorize(binding, operator, Role.SUPPORT);
          Mapping m = mapping(s, id);
          var p = spaces.provider(m.provider());
          capability(p, Capability.AGENT_RESOLVE);
          Ticket replay =
              replay(s, operator.id(), key, "AGENT_RESOLVE", List.of(id, in), Ticket.class);
          if (replay != null) return replay;
          expected(in.expectedVersion());
          var data = p.resolveAgentRequest(m.scope(s), id, in.expectedVersion(), key);
          event(s, id, operator.id(), "AGENT_RESOLVE");
          return receipt(
              s, operator.id(), key, "AGENT_RESOLVE", List.of(id, in), id, project(s, data, p));
        });
  }

  public PurgeReceipt purge(String binding, String id, PurgeInput in, String key) {
    Space s = spaces.forBinding(binding);
    return transaction(
        s,
        () -> {
          Operator owner =
              directory.find(
                  new VerifiedIdentity(
                      in.ownerIssuer(), in.ownerSubject(), null, null, false, null));
          directory.authorize(binding, owner, Role.OWNER);
          SupportSpaces.text(in.reason(), 600, "purge reason");
          String actor = owner.id();
          PurgeReceipt replay = replay(s, actor, key, "PURGE", List.of(id, in), PurgeReceipt.class);
          if (replay != null) return replay;
          Mapping m = mapping(s, id);
          var p = spaces.provider(m.provider());
          capability(p, Capability.PURGE);
          if (spaces
                  .jdbc()
                  .queryForObject(
                      "SELECT COUNT(*) FROM desk_support_ticket_episode WHERE space_id=? AND"
                          + " request_id=? AND delivery IN ('PENDING','IN_FLIGHT','UNKNOWN')",
                      Long.class,
                      s.view().spaceId(),
                      id)
              > 0)
            throw new TicketException(
                409,
                "TICKET_DELIVERY_PENDING",
                "Observe pending delivery before purging this request.");
          expected(in.expectedVersion());
          p.purge(
              m.scope(s),
              id,
              in.expectedVersion(),
              in.testRecord(),
              s.view().closedRetentionDays());
          for (String table :
              List.of(
                  "desk_support_ticket_operation",
                  "desk_support_ticket_event",
                  "desk_support_ticket_episode",
                  "desk_support_request"))
            spaces
                .jdbc()
                .update(
                    "DELETE FROM " + table + " WHERE space_id=? AND request_id=?",
                    s.view().spaceId(),
                    id);
          spaces
              .jdbc()
              .update(
                  "DELETE FROM desk_support_requester WHERE space_id=? AND requester_id=? AND NOT"
                      + " EXISTS(SELECT 1 FROM desk_support_request WHERE space_id=? AND"
                      + " requester_id=?)",
                  s.view().spaceId(),
                  m.requester(),
                  s.view().spaceId(),
                  m.requester());
          spaces
              .jdbc()
              .update(
                  "INSERT INTO desk_support_ticket_tombstone(space_id,request_id,purged_at)"
                      + " VALUES(?,?,?)",
                  s.view().spaceId(),
                  id,
                  Timestamp.from(Instant.now()));
          directory.audit(
              binding, directory.binding(binding).customerId(), actor, "SUPPORT_TICKET_PURGE", id);
          return receipt(s, actor, key, "PURGE", List.of(id, in), id, new PurgeReceipt(id, true));
        });
  }

  private record Mapping(
      String requester, String provider, String binding, String runtimePrincipal) {
    Scope scope(Space s) {
      return new Scope(s.view().spaceId(), binding, requester);
    }
  }

  private Mapping mapping(Space s, String id) {
    return spaces
        .jdbc()
        .query(
            "SELECT requester_id,provider_id,provider_binding_id,runtime_principal FROM"
                + " desk_support_request WHERE space_id=? AND request_id=?",
            (rs, n) ->
                new Mapping(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)),
            s.view().spaceId(),
            id)
        .stream()
        .findFirst()
        .orElseThrow(TicketException::missing);
  }

  private Mapping own(Space s, Requester r, String id) {
    enabled(s);
    Mapping m = mapping(s, id);
    if (!m.requester().equals(r.id())) throw TicketException.missing();
    if (r.runtimePrincipal() != null && !r.runtimePrincipal().equals(m.runtimePrincipal()))
      throw new TicketException(
          403,
          "RUNTIME_PRINCIPAL_ASSERTION_MISMATCH",
          "The host assertion does not match the request's immutable runtime principal.");
    return m;
  }

  private void boundConversation(Requester r, String conversation) {
    if (r.runtimeConversationId() != null && !r.runtimeConversationId().equals(conversation))
      throw new TicketException(
          403,
          "RUNTIME_CONVERSATION_ASSERTION_MISMATCH",
          "The host assertion does not match this conversation.");
  }

  private Ticket project(Space s, TicketData t, TicketProvider p) {
    var episodes =
        spaces
            .jdbc()
            .query(
                "SELECT * FROM desk_support_ticket_episode WHERE space_id=? AND request_id=? ORDER"
                    + " BY episode_number",
                (rs, n) ->
                    new Episode(
                        rs.getString("episode_id"),
                        rs.getString("conversation_id"),
                        rs.getInt("episode_number"),
                        rs.getString("runtime_base_url"),
                        rs.getBoolean("opening_required"),
                        Delivery.valueOf(rs.getString("delivery")),
                        rs.getTimestamp("created_at").toInstant()),
                s.view().spaceId(),
                t.id());
    var events =
        spaces
            .jdbc()
            .query(
                "SELECT event_type,actor_id,created_at FROM desk_support_ticket_event WHERE"
                    + " space_id=? AND request_id=? ORDER BY created_at,event_id LIMIT 100",
                (rs, n) ->
                    new Event(rs.getString(1), rs.getString(2), rs.getTimestamp(3).toInstant()),
                s.view().spaceId(),
                t.id());
    Episode latest = episodes.isEmpty() ? null : episodes.getLast();
    return new Ticket(
        t.id(),
        t.reference(),
        t.providerBindingId(),
        mapping(s, t.id()).runtimePrincipal(),
        t.subject(),
        t.details(),
        t.topic(),
        t.status(),
        t.version(),
        t.createdAt(),
        t.updatedAt(),
        t.upstreamStatus(),
        t.unread(),
        latest != null && latest.openingMessageRequired(),
        latest == null
            ? null
            : new Conversation(latest.episodeId(), latest.conversationId(), latest.delivery()),
        episodes,
        events,
        p.capabilities());
  }

  private void episode(Space s, String id, String conversation, boolean opening) {
    SupportSpaces.text(conversation, 160, "conversationId");
    int number =
        spaces
                .jdbc()
                .queryForObject(
                    "SELECT COUNT(*) FROM desk_support_ticket_episode WHERE space_id=? AND"
                        + " request_id=?",
                    Integer.class,
                    s.view().spaceId(),
                    id)
            + 1;
    spaces
        .jdbc()
        .update(
            "INSERT INTO"
                + " desk_support_ticket_episode(space_id,request_id,episode_id,conversation_id,episode_number,runtime_base_url,opening_required,message_key,delivery,created_at)"
                + " VALUES(?,?,?,?,?,?,?,?,?,?)",
            s.view().spaceId(),
            id,
            newId("episode"),
            conversation,
            number,
            s.runtimeBaseUrl(),
            opening,
            newId("opening"),
            opening ? "PENDING" : "NOT_REQUIRED",
            Timestamp.from(Instant.now()));
  }

  private void delivery(Space s, String id, String actor, String episode, Delivery delivery) {
    spaces
        .jdbc()
        .update(
            "UPDATE desk_support_ticket_episode SET delivery=? WHERE space_id=? AND episode_id=?",
            delivery.name(),
            s.view().spaceId(),
            episode);
    event(s, id, actor, "DELIVERY_" + delivery.name());
  }

  private void event(Space s, String id, String actor, String type) {
    spaces
        .jdbc()
        .update(
            "INSERT INTO"
                + " desk_support_ticket_event(space_id,request_id,event_id,event_type,actor_id,created_at)"
                + " VALUES(?,?,?,?,?,?)",
            s.view().spaceId(),
            id,
            newId("event"),
            type,
            actor,
            Timestamp.from(Instant.now()));
  }

  private void validate(CreateInput in, Space s) {
    SupportSpaces.text(in.subject(), 240, "subject");
    SupportSpaces.text(in.details(), 12000, "details");
    if (in.topic() != null && !s.view().topics().contains(in.topic()))
      throw TicketException.invalid("configured topic");
  }

  private void enabled(Space s) {
    if (!s.view().enabled())
      throw new TicketException(409, "SUPPORT_SPACE_DISABLED", "Ticket operations are disabled.");
  }

  private void capability(TicketProvider p, Capability c) {
    if (!p.capabilities().contains(c)) throw TicketException.unsupported();
  }

  private void expected(String v) {
    SupportSpaces.text(v, 512, "expectedVersion");
  }

  private void version(TicketData t, String v) {
    expected(v);
    if (!t.version().equals(v))
      throw new TicketException(
          409, "TICKET_VERSION_CONFLICT", "Refresh this request before changing it.", t);
  }

  private String newId(String prefix) {
    return prefix + "-" + UUID.randomUUID();
  }

  private <T> T transaction(Space s, Supplier<T> action) {
    return tx.execute(
        status -> {
          spaces.lock(s.view().spaceId());
          Space current = spaces.available(s.view().spaceId());
          if (!current.view().version().equals(s.view().version()))
            throw new TicketException(
                409, "SPACE_VERSION_CONFLICT", "Support configuration changed; refresh and retry.");
          return action.get();
        });
  }

  private String fingerprint(String type, Object input) {
    return spaces.secrets().sha256(type + "\n" + spaces.write(input));
  }

  private <T> T replay(
      Space s, String actor, String key, String type, Object input, Class<T> result) {
    SupportSpaces.text(key, 180, "Idempotency-Key");
    var found =
        spaces
            .jdbc()
            .query(
                "SELECT fingerprint,protected_result FROM desk_support_ticket_operation WHERE"
                    + " space_id=? AND actor_id=? AND command_key=?",
                (rs, n) -> List.of(rs.getString(1), rs.getString(2)),
                s.view().spaceId(),
                actor,
                key);
    if (found.isEmpty()) return null;
    if (!found.getFirst().getFirst().equals(fingerprint(type, input)))
      throw new TicketException(
          409, "IDEMPOTENCY_CONFLICT", "Command key was used with different input.");
    return spaces.readJson(spaces.secrets().decrypt(found.getFirst().get(1)), result);
  }

  private <T> T receipt(
      Space s, String actor, String key, String type, Object input, String id, T result) {
    spaces
        .jdbc()
        .update(
            "INSERT INTO"
                + " desk_support_ticket_operation(space_id,actor_id,command_key,fingerprint,request_id,protected_result,created_at)"
                + " VALUES(?,?,?,?,?,?,?)",
            s.view().spaceId(),
            actor,
            key,
            fingerprint(type, input),
            id,
            spaces.secrets().encrypt(spaces.write(result)),
            Timestamp.from(Instant.now()));
    return result;
  }
}
