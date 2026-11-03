package com.loomai.desk.ticket;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class TicketContracts {
  private TicketContracts() {}

  public enum IdentityMode {
    SUPABASE,
    TRUSTED_PRIVATE_HOST
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record ConversationSettings(String mode, String position) {
    public ConversationSettings {
      if (mode == null) mode = "executor";
      // Bound wire identifiers only; the runtime owns its active mode/position catalogue.
      code(mode, "conversation.mode");
      if (position != null) code(position, "conversation.position");
    }

    private static void code(String value, String field) {
      if (value.length() > 64 || !value.matches("[A-Za-z0-9][A-Za-z0-9._-]*"))
        throw TicketException.invalid(field);
    }
  }

  public enum Status {
    OPEN,
    RESOLVED,
    CANCELLED
  }

  public enum Delivery {
    PENDING,
    IN_FLIGHT,
    DELIVERED,
    UNKNOWN,
    NOT_REQUIRED
  }

  public enum Capability {
    CREATE,
    LIST,
    GET,
    RESOLVE,
    CANCEL,
    REOPEN,
    SEEN,
    LINK_CONVERSATION,
    OBSERVE_ACTIVITY,
    AGENT_LIST,
    AGENT_GET,
    AGENT_RESOLVE,
    PURGE
  }

  public record IdentityInput(
      IdentityMode mode,
      String issuer,
      String jwksUri,
      String audience,
      String requesterNamespace,
      String privateServerKey) {
    @Override
    public String toString() {
      return "IdentityInput[mode=" + mode + "]";
    }
  }

  public record SupportSpaceInput(
      String spaceId,
      String expectedVersion,
      String name,
      boolean enabled,
      boolean humanHandoverEnabled,
      String providerId,
      String providerBindingId,
      String runtimeBaseUrl,
      String runtimeTrustedKeyHeader,
      IdentityInput identity,
      String connectorKey,
      List<String> allowedOrigins,
      List<String> topics,
      int closedRetentionDays,
      ServerConnectorInput serverConnector,
      ConversationSettings conversation) {
    public SupportSpaceInput {
      if (closedRetentionDays == 0) closedRetentionDays = 30;
      if (runtimeTrustedKeyHeader == null) runtimeTrustedKeyHeader = "X-AIFABRIC-RUNTIME-API-KEY";
      if (conversation == null) conversation = new ConversationSettings(null, null);
    }

    @Override
    public String toString() {
      return "SupportSpaceInput[spaceId=" + spaceId + "]";
    }
  }

  public record ServerConnectorInput(
      String authMode,
      String callerType,
      String issuer,
      String audience,
      List<String> requiredScopes) {
    @Override
    public String toString() {
      return "ServerConnectorInput[redacted]";
    }
  }

  record VerifiedRuntimeTrace(
      String spaceId, String requesterId, String subjectId, String conversationId) {}

  public record IdentityView(
      IdentityMode mode, String issuer, String audience, String requesterNamespace) {}

  public record SpaceView(
      String spaceId,
      String bindingId,
      String name,
      boolean enabled,
      boolean humanHandoverEnabled,
      String providerId,
      String providerBindingId,
      String version,
      IdentityView identity,
      List<String> allowedOrigins,
      List<String> topics,
      int closedRetentionDays,
      Set<Capability> capabilities,
      ConversationSettings conversation) {
    public SpaceView {
      if (conversation == null) conversation = new ConversationSettings(null, null);
    }
  }

  public record Space(
      SpaceView view,
      String runtimeBaseUrl,
      String runtimeTrustedKeyHeader,
      IdentityInput identity,
      String connectorKey,
      ServerConnectorInput serverConnector) {
    @Override
    public String toString() {
      return "Space[spaceId=" + view.spaceId() + "]";
    }
  }

  public record Requester(
      String id,
      String subject,
      Set<String> operations,
      String runtimePrincipal,
      String runtimeConversationId) {}

  public record Session(
      boolean authenticated, String requesterId, SpaceView space, Set<Capability> capabilities) {}

  public record CreateInput(
      String subject, String details, String topic, String existingConversationId) {}

  public record VersionInput(String expectedVersion) {}

  public record LinkInput(String expectedVersion, String conversationId) {}

  public record SeenInput(String expectedVersion, Instant seenThrough) {}

  public record PurgeInput(
      String expectedVersion,
      String ownerIssuer,
      String ownerSubject,
      boolean testRecord,
      String reason) {}

  public record PurgeReceipt(String id, boolean purged) {}

  public record CommandInput(
      String operation,
      String requestId,
      String expectedVersion,
      String subject,
      String details,
      String topic,
      String conversationId) {}

  public record Conversation(String episodeId, String conversationId, Delivery delivery) {}

  public record Episode(
      String episodeId,
      String conversationId,
      int number,
      String runtimeBaseUrl,
      boolean openingMessageRequired,
      Delivery delivery,
      Instant createdAt) {}

  public record Event(String type, String actorId, Instant createdAt) {}

  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record Ticket(
      String id,
      String reference,
      String providerBindingId,
      String runtimePrincipal,
      String subject,
      String details,
      String topic,
      Status status,
      String version,
      Instant createdAt,
      Instant updatedAt,
      String upstreamStatus,
      boolean unread,
      boolean openingMessageRequired,
      Conversation conversation,
      List<Episode> episodes,
      List<Event> events,
      Set<Capability> capabilities) {}

  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record Page(List<Ticket> items, String nextCursor) {}

  public record Scope(String spaceId, String providerBindingId, String requesterId) {}

  public record TicketData(
      String id,
      String reference,
      String providerBindingId,
      String subject,
      String details,
      String topic,
      Status status,
      String version,
      Instant createdAt,
      Instant updatedAt,
      String upstreamStatus,
      boolean unread) {}

  public record DataPage(List<TicketData> items, String nextCursor) {}

  public record RuntimeCredential(String authorization, String apiKey) {

    @Override
    public String toString() {
      return "RuntimeCredential[redacted]";
    }
  }
}
