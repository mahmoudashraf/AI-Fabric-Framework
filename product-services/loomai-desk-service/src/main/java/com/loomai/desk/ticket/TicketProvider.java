package com.loomai.desk.ticket;

import static com.loomai.desk.ticket.TicketContracts.*;

import java.time.Instant;
import java.util.Set;

/** Adapters enforce ownership and upstream conditional writes; no fallback or mirrored payloads. */
public interface TicketProvider {
  String id();

  Set<Capability> capabilities();

  TicketData createRequest(Scope scope, CreateInput input, String commandKey);

  DataPage listOwnRequests(Scope scope, Status status, String cursor, int limit);

  TicketData getOwnRequest(Scope scope, String id);

  TicketData linkConversation(
      Scope scope, String id, String expectedVersion, String conversationId, String commandKey);

  default TicketData resolveRequest(Scope s, String id, String v, String key) {
    throw TicketException.unsupported();
  }

  default TicketData cancelRequest(Scope s, String id, String v, String key) {
    throw TicketException.unsupported();
  }

  default TicketData reopenRequest(Scope s, String id, String v, String key) {
    throw TicketException.unsupported();
  }

  default TicketData seen(Scope s, String id, String v, Instant through, String key) {
    throw TicketException.unsupported();
  }

  default TicketData observeActivity(Scope s, String id, String v, Instant activity, String key) {
    throw TicketException.unsupported();
  }

  default DataPage listAgentRequests(Scope s, Status status, String cursor, int limit) {
    throw TicketException.unsupported();
  }

  default TicketData getAgentRequest(Scope s, String id) {
    throw TicketException.unsupported();
  }

  default TicketData resolveAgentRequest(Scope s, String id, String v, String key) {
    throw TicketException.unsupported();
  }

  default void purge(Scope s, String id, String v, boolean testRecord, int retentionDays) {
    throw TicketException.unsupported();
  }
}
