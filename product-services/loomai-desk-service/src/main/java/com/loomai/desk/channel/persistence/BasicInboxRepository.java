package com.loomai.desk.channel.persistence;

import static com.loomai.desk.channel.model.InboxContracts.*;

import ai.fabric.chat.channel.ConversationChannelDispatch;
import ai.fabric.chat.channel.ConversationChannelMessage;
import ai.fabric.chat.control.ConversationParticipantRole;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loomai.desk.channel.service.InboxSecurityService;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Basic Inbox persistence adapted to an immutable trusted binding, never a browser-selected tenant.
 */
public class BasicInboxRepository {
  private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};
  private static final TypeReference<List<String>> LIST = new TypeReference<>() {};
  private final String bindingId;
  private final JdbcTemplate jdbc;
  private final TransactionTemplate transactions;
  private final ObjectMapper mapper;
  private final InboxSecurityService security;

  public BasicInboxRepository(
      DataSource ds, ObjectMapper mapper, InboxSecurityService security, String bindingId) {
    this.bindingId = Objects.requireNonNull(bindingId);
    this.jdbc = new JdbcTemplate(ds);
    this.transactions = new TransactionTemplate(new DataSourceTransactionManager(ds));
    this.mapper = mapper;
    this.security = security;
  }

  public <T> T transactional(Supplier<T> operation) {
    return transactions.execute(s -> operation.get());
  }

  // Lock the trusted binding before dispatch receipt admission; PostgreSQL uniqueness errors must
  // not poison replay transactions.
  public void lockBinding() {
    if (jdbc.query(
            "SELECT binding_id FROM desk_binding WHERE binding_id = ? AND enabled=TRUE FOR UPDATE",
            (rs, n) -> rs.getString(1),
            bindingId)
        .isEmpty()) {
      throw conflict("BINDING_DISABLED", "This Desk binding is disabled.");
    }
  }

  public Optional<DispatchResponse> dispatchReceipt(String dispatchId, String hash) {
    return jdbc
        .query(
            "SELECT payload_sha256,response_json FROM inbox_dispatch_receipt WHERE binding_id=? AND dispatch_id=?",
            (rs, n) -> {
              if (!hash.equals(rs.getString(1)))
                throw conflict(
                    "DISPATCH_ID_PAYLOAD_CONFLICT",
                    "Dispatch identity was reused with different data.");
              return read(rs.getString(2), DispatchResponse.class);
            },
            bindingId,
            dispatchId)
        .stream()
        .findFirst();
  }

  public void saveDispatchReceipt(String id, String hash, DispatchResponse response) {
    jdbc.update(
        "INSERT INTO inbox_dispatch_receipt(binding_id,dispatch_id,payload_sha256,response_json,received_at) VALUES(?,?,?,?,?)",
        bindingId,
        id,
        hash,
        write(response),
        now());
  }

  public ConversationRecord open(ConversationChannelDispatch dispatch, String externalRef) {
    Instant now = Instant.now();
    jdbc.update(
        """
            INSERT INTO inbox_conversation(binding_id,handover_id,external_conversation_ref,tenant_id,deployment_id,
                conversation_id,owner_ref,status,expected_control_version,protected_intake,unread_count,created_at,updated_at,version)
            SELECT ?,?,?,?,?,?,?,'WAITING',?,?,0,?,?,0 WHERE NOT EXISTS
                (SELECT 1 FROM inbox_conversation WHERE binding_id=? AND handover_id=?)
            """,
        bindingId,
        dispatch.handoverId(),
        externalRef,
        dispatch.scope().tenantId(),
        dispatch.scope().deploymentId(),
        dispatch.scope().conversationId(),
        dispatch.scope().ownerId(),
        dispatch.expectedControlVersion(),
        dispatch.consentedIntake().isEmpty()
            ? null
            : security.encrypt(write(dispatch.consentedIntake())),
        Timestamp.from(now),
        Timestamp.from(now),
        bindingId,
        dispatch.handoverId());
    ConversationRecord record = requireConversation(dispatch.handoverId());
    requireExternalScope(record, dispatch);
    if (!externalRef.equals(record.externalConversationRef())
        || record.expectedControlVersion() != dispatch.expectedControlVersion()
        || !intake(record).equals(dispatch.consentedIntake())) {
      throw conflict(
          "HANDOVER_ID_PAYLOAD_CONFLICT", "Handover identity was reused with different data.");
    }
    dispatch.transcript().forEach(message -> storeProjectedMessage(dispatch.handoverId(), message));
    return record;
  }

  public void acceptUserMessage(ConversationChannelDispatch dispatch) {
    ConversationRecord record = requireConversation(dispatch.handoverId());
    requireExternalScope(record, dispatch);
    if (!List.of("WAITING", "CLAIM_PENDING", "ACTIVE").contains(record.status())) {
      throw conflict("CONVERSATION_NOT_OPEN", "Conversation no longer accepts user messages.");
    }
    boolean inserted =
        storeProjectedMessage(
            dispatch.handoverId(),
            new ConversationChannelMessage(
                dispatch.messageId() == null ? dispatch.dispatchId() : dispatch.messageId(),
                ConversationParticipantRole.USER,
                "conversation-user",
                null,
                dispatch.text() == null ? "[Attachment shared]" : dispatch.text(),
                dispatch.attachmentRefs(),
                dispatch.createdAt()));
    if (inserted)
      jdbc.update(
          "UPDATE inbox_conversation SET unread_count=unread_count+1,expected_control_version=?,updated_at=?,version=version+1 WHERE binding_id=? AND handover_id=?",
          dispatch.expectedControlVersion(),
          now(),
          bindingId,
          dispatch.handoverId());
  }

  private boolean storeProjectedMessage(String handoverId, ConversationChannelMessage message) {
    int inserted =
        jdbc.update(
            """
            INSERT INTO inbox_message(binding_id,handover_id,message_id,source_message_id,participant_role,actor_ref,safe_actor_display_name,content,attachment_refs_json,created_at)
            SELECT ?,?,?,?,?,?,?,?,?,? WHERE NOT EXISTS
              (SELECT 1 FROM inbox_message WHERE binding_id=? AND handover_id=? AND source_message_id=?)
            """,
            bindingId,
            handoverId,
            storageMessageId(handoverId, message.messageId()),
            message.messageId(),
            message.role().name(),
            message.actorRef(),
            message.safeActorDisplayName(),
            message.text(),
            write(message.attachmentRefs()),
            Timestamp.from(message.createdAt()),
            bindingId,
            handoverId,
            message.messageId());
    if (inserted == 1) return true;
    List<Boolean> matches =
        jdbc.query(
            "SELECT * FROM inbox_message WHERE binding_id=? AND handover_id=? AND source_message_id=?",
            (rs, n) ->
                message.role().name().equals(rs.getString("participant_role"))
                    && Objects.equals(message.actorRef(), rs.getString("actor_ref"))
                    && Objects.equals(
                        message.safeActorDisplayName(), rs.getString("safe_actor_display_name"))
                    && message.text().equals(rs.getString("content"))
                    && message
                        .attachmentRefs()
                        .equals(read(rs.getString("attachment_refs_json"), LIST)),
            bindingId,
            handoverId,
            message.messageId());
    if (matches.size() != 1 || !matches.getFirst())
      throw conflict(
          "MESSAGE_ID_PAYLOAD_CONFLICT", "Message identity was reused with different data.");
    return false;
  }

  public void applyControlDispatch(ConversationChannelDispatch dispatch) {
    requireExternalScope(requireConversation(dispatch.handoverId()), dispatch);
    String next =
        switch (dispatch.type()) {
          case CANCEL_HANDOVER -> "CANCELLED";
          case RETURN_TO_AI -> "RETURNED_TO_AI";
          case CLOSE -> "CLOSED";
          default -> throw conflict("DISPATCH_TYPE_INVALID", "Unsupported control dispatch.");
        };
    int changed =
        jdbc.update(
            "UPDATE inbox_conversation SET status=?,expected_control_version=?,closed_at=?,updated_at=?,version=version+1 WHERE binding_id=? AND handover_id=? AND status IN ('WAITING','CLAIM_PENDING','ACTIVE','RETURN_PENDING','CLOSE_PENDING')",
            next,
            dispatch.expectedControlVersion(),
            now(),
            now(),
            bindingId,
            dispatch.handoverId());
    if (changed != 1)
      throw conflict("CONTROL_DISPATCH_NOT_ALLOWED", "Conversation cannot accept this transition.");
  }

  public boolean hasIncompleteCallbacks(String handoverId) {
    return jdbc.queryForObject(
            "SELECT COUNT(*) FROM inbox_callback_outbox WHERE binding_id=? AND handover_id=? AND status<>'COMPLETED'",
            Long.class,
            bindingId,
            handoverId)
        > 0;
  }

  public List<ConversationSummary> listQueue(String binding, String status, int limit) {
    requireBinding(binding);
    String normalized =
        status == null || status.isBlank()
            ? null
            : status.trim().toUpperCase(java.util.Locale.ROOT);
    return normalized == null
        ? jdbc.query(
            "SELECT * FROM inbox_conversation WHERE binding_id=? ORDER BY updated_at DESC,handover_id LIMIT ?",
            (rs, n) -> summary(map(rs)),
            bindingId,
            bound(limit))
        : jdbc.query(
            "SELECT * FROM inbox_conversation WHERE binding_id=? AND status=? ORDER BY updated_at DESC,handover_id LIMIT ?",
            (rs, n) -> summary(map(rs)),
            bindingId,
            normalized,
            bound(limit));
  }

  public ConversationDetail detail(String handoverId) {
    ConversationRecord record = requireConversation(handoverId);
    List<MessageView> messages =
        jdbc.query(
            "SELECT * FROM inbox_message WHERE binding_id=? AND handover_id=? ORDER BY created_at,source_message_id",
            (rs, n) ->
                new MessageView(
                    rs.getString("source_message_id"),
                    rs.getString("participant_role"),
                    rs.getString("actor_ref"),
                    rs.getString("safe_actor_display_name"),
                    rs.getString("content"),
                    read(rs.getString("attachment_refs_json"), LIST),
                    instant(rs, "created_at")),
            bindingId,
            handoverId);
    return new ConversationDetail(summary(record), intake(record), messages);
  }

  public ConversationRecord requireConversation(String handoverId) {
    return jdbc
        .query(
            "SELECT * FROM inbox_conversation WHERE binding_id=? AND handover_id=?",
            (rs, n) -> map(rs),
            bindingId,
            handoverId)
        .stream()
        .findFirst()
        .orElseThrow(
            () ->
                new InboxNotFoundException(
                    "INBOX_CONVERSATION_NOT_FOUND", "Conversation is not available."));
  }

  public void lockConversation(String handoverId) {
    if (jdbc.query(
            "SELECT handover_id FROM inbox_conversation WHERE binding_id=? AND handover_id=? FOR UPDATE",
            (rs, n) -> rs.getString(1),
            bindingId,
            handoverId)
        .isEmpty())
      throw new InboxNotFoundException(
          "INBOX_CONVERSATION_NOT_FOUND", "Conversation is not available.");
  }

  public boolean claim(String handoverId, String operatorId, String displayName) {
    return jdbc.update(
            "UPDATE inbox_conversation SET status='CLAIM_PENDING',claimed_by=?,claimed_display_name=?,unread_count=0,updated_at=?,version=version+1 WHERE binding_id=? AND handover_id=? AND (status='WAITING' OR (status='CLAIM_PENDING' AND claimed_by=?))",
            operatorId,
            displayName,
            now(),
            bindingId,
            handoverId,
            operatorId)
        == 1;
  }

  public String addHumanMessage(
      String handoverId, String messageId, String operatorId, String displayName, String content) {
    ConversationRecord record = requireConversation(handoverId);
    if (!"ACTIVE".equals(record.status()) || !operatorId.equals(record.claimedBy()))
      throw conflict("INBOX_CONVERSATION_NOT_CLAIMED", "Only the active operator may reply.");
    storeProjectedMessage(
        handoverId,
        new ConversationChannelMessage(
            messageId,
            ConversationParticipantRole.HUMAN,
            operatorId,
            displayName,
            content,
            List.of(),
            Instant.now()));
    return messageId;
  }

  public boolean humanMessageExists(
      String messageId, String handoverId, String operatorId, String displayName, String content) {
    List<Boolean> matches =
        jdbc.query(
            "SELECT * FROM inbox_message WHERE binding_id=? AND handover_id=? AND source_message_id=?",
            (rs, n) -> {
              if (!"HUMAN".equals(rs.getString("participant_role"))
                  || !operatorId.equals(rs.getString("actor_ref"))
                  || !Objects.equals(displayName, rs.getString("safe_actor_display_name"))
                  || !content.equals(rs.getString("content")))
                throw conflict(
                    "IDEMPOTENCY_KEY_PAYLOAD_CONFLICT",
                    "Reply key was reused with different data.");
              return true;
            },
            bindingId,
            handoverId,
            messageId);
    return !matches.isEmpty();
  }

  private String storageMessageId(String handoverId, String id) {
    return "inbox-message-" + security.sha256(bindingId + "\n" + handoverId + "\n" + id);
  }

  public void markTransitionPending(String handoverId, String operatorId, String status) {
    if (jdbc.update(
            "UPDATE inbox_conversation SET status=?,updated_at=?,version=version+1 WHERE binding_id=? AND handover_id=? AND status='ACTIVE' AND claimed_by=?",
            status,
            now(),
            bindingId,
            handoverId,
            operatorId)
        != 1)
      throw conflict(
          "INBOX_CONVERSATION_NOT_CLAIMED",
          "Only the active operator may change this conversation.");
  }

  public boolean callbackExists(String callbackId, String handoverId, String eventType) {
    List<Boolean> matches =
        jdbc.query(
            "SELECT handover_id,event_type FROM inbox_callback_outbox WHERE binding_id=? AND callback_id=?",
            (rs, n) -> {
              if (!handoverId.equals(rs.getString(1)) || !eventType.equals(rs.getString(2)))
                throw conflict(
                    "IDEMPOTENCY_KEY_PAYLOAD_CONFLICT",
                    "Callback key was reused with different data.");
              return true;
            },
            bindingId,
            callbackId);
    return !matches.isEmpty();
  }

  public void enqueueCallback(String id, String handoverId, String type, String payload) {
    lockConversation(handoverId);
    if (callbackExists(id, handoverId, type)) return;
    long sequence =
        jdbc.queryForObject(
            "SELECT COALESCE(MAX(callback_sequence),0)+1 FROM inbox_callback_outbox WHERE binding_id=? AND handover_id=?",
            Long.class,
            bindingId,
            handoverId);
    jdbc.update(
        "INSERT INTO inbox_callback_outbox(binding_id,callback_id,handover_id,callback_sequence,event_type,protected_payload,status,attempt_count,next_attempt_at,created_at,updated_at) VALUES(?,?,?,?,?,?,'PENDING',0,?,?,?)",
        bindingId,
        id,
        handoverId,
        sequence,
        type,
        security.encrypt(payload),
        now(),
        now(),
        now());
  }

  public Optional<CallbackClaim> claimCallback(Duration lease) {
    return transactional(
        () -> {
          List<String> ids =
              jdbc.query(
                  """
                SELECT c.callback_id FROM inbox_callback_outbox c WHERE c.binding_id=?
                AND ((c.status IN ('PENDING','RETRY') AND c.next_attempt_at<=?) OR (c.status='LEASED' AND c.lease_until<?))
                AND NOT EXISTS(SELECT 1 FROM inbox_callback_outbox earlier WHERE earlier.binding_id=c.binding_id
                  AND earlier.handover_id=c.handover_id AND earlier.callback_sequence<c.callback_sequence AND earlier.status<>'COMPLETED')
                ORDER BY c.created_at,c.handover_id,c.callback_sequence LIMIT 1
                """,
                  (rs, n) -> rs.getString(1),
                  bindingId,
                  now(),
                  now());
          if (ids.isEmpty()) return Optional.empty();
          String owner = UUID.randomUUID().toString();
          int changed =
              jdbc.update(
                  "UPDATE inbox_callback_outbox SET status='LEASED',lease_owner=?,lease_until=?,attempt_count=attempt_count+1,updated_at=? WHERE binding_id=? AND callback_id=? AND ((status IN ('PENDING','RETRY') AND next_attempt_at<=?) OR (status='LEASED' AND lease_until<?))",
                  owner,
                  Timestamp.from(Instant.now().plus(lease)),
                  now(),
                  bindingId,
                  ids.getFirst(),
                  now(),
                  now());
          if (changed != 1) return Optional.empty();
          return jdbc
              .query(
                  "SELECT * FROM inbox_callback_outbox WHERE binding_id=? AND callback_id=? AND lease_owner=?",
                  (rs, n) ->
                      new CallbackClaim(
                          rs.getString("callback_id"),
                          rs.getString("handover_id"),
                          rs.getString("event_type"),
                          security.decrypt(rs.getString("protected_payload")),
                          rs.getInt("attempt_count"),
                          owner),
                  bindingId,
                  ids.getFirst(),
                  owner)
              .stream()
              .findFirst();
        });
  }

  public boolean completeCallbackAndUpdateConversation(
      String id, String handoverId, int attempt, String owner, String status, long version) {
    return transactional(
        () -> {
          lockConversation(handoverId);
          if (jdbc.update(
                  "UPDATE inbox_callback_outbox SET status='COMPLETED',lease_owner=NULL,lease_until=NULL,completed_at=?,updated_at=? WHERE binding_id=? AND callback_id=? AND handover_id=? AND status='LEASED' AND attempt_count=? AND lease_owner=?",
                  now(),
                  now(),
                  bindingId,
                  id,
                  handoverId,
                  attempt,
                  owner)
              != 1) return false;
          // A late callback cannot reopen a runtime-cancelled or closed handover.
          jdbc.update(
              "UPDATE inbox_conversation SET status=?,expected_control_version=?,closed_at=CASE WHEN ? IN ('RETURNED_TO_AI','CLOSED') THEN ? ELSE closed_at END,updated_at=?,version=version+1 WHERE binding_id=? AND handover_id=? AND status NOT IN ('CANCELLED','RETURNED_TO_AI','CLOSED')",
              status,
              version,
              status,
              now(),
              now(),
              bindingId,
              handoverId);
          return true;
        });
  }

  public boolean failCallback(
      String id,
      int attempt,
      String owner,
      boolean retryable,
      int maxAttempts,
      Duration delay,
      String code) {
    boolean retry = retryable && attempt < maxAttempts;
    return jdbc.update(
            "UPDATE inbox_callback_outbox SET status=?,next_attempt_at=?,lease_owner=NULL,lease_until=NULL,failure_code=?,updated_at=? WHERE binding_id=? AND callback_id=? AND status='LEASED' AND attempt_count=? AND lease_owner=?",
            retry ? "RETRY" : "DEAD",
            Timestamp.from(
                Instant.now()
                    .plus(retry ? delay.multipliedBy(Math.max(1, attempt)) : Duration.ZERO)),
            code,
            now(),
            bindingId,
            id,
            attempt,
            owner)
        == 1;
  }

  public List<CallbackOperationView> callbackOperations(String binding, int limit) {
    requireBinding(binding);
    return jdbc.query(
        "SELECT * FROM inbox_callback_outbox WHERE binding_id=? ORDER BY updated_at DESC,callback_id LIMIT ?",
        (rs, n) ->
            new CallbackOperationView(
                rs.getString("callback_id"),
                rs.getString("handover_id"),
                rs.getString("event_type"),
                rs.getString("status"),
                rs.getInt("attempt_count"),
                rs.getString("failure_code"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"),
                "DEAD".equals(rs.getString("status"))),
        bindingId,
        bound(limit));
  }

  public boolean retryDeadCallback(String binding, String id) {
    requireBinding(binding);
    return jdbc.update(
            "UPDATE inbox_callback_outbox SET status='PENDING',next_attempt_at=?,lease_owner=NULL,lease_until=NULL,failure_code=NULL,completed_at=NULL,updated_at=? WHERE binding_id=? AND callback_id=? AND status='DEAD'",
            now(),
            now(),
            bindingId,
            id)
        == 1;
  }

  public boolean operatorOperationExists(
      String id, String binding, String operator, String type, String target) {
    requireBinding(binding);
    List<Boolean> matches =
        jdbc.query(
            "SELECT operator_id,operation_type,target_id FROM inbox_operator_operation WHERE binding_id=? AND operation_id=?",
            (rs, n) ->
                operator.equals(rs.getString(1))
                    && type.equals(rs.getString(2))
                    && target.equals(rs.getString(3)),
            bindingId,
            id);
    if (!matches.isEmpty() && !matches.getFirst())
      throw conflict(
          "IDEMPOTENCY_KEY_PAYLOAD_CONFLICT", "Operation key was reused with different data.");
    return !matches.isEmpty();
  }

  public void recordOperatorOperation(
      String id, String binding, String operator, String type, String target) {
    requireBinding(binding);
    jdbc.update(
        "INSERT INTO inbox_operator_operation(binding_id,operation_id,operator_id,operation_type,target_id,created_at) VALUES(?,?,?,?,?,?)",
        bindingId,
        id,
        operator,
        type,
        target,
        now());
  }

  public void purgeBefore(Instant cutoff) {
    transactional(
        () -> {
          Timestamp threshold = Timestamp.from(cutoff);
          jdbc.update(
              "UPDATE inbox_conversation SET protected_intake=NULL WHERE binding_id=? AND created_at<?",
              bindingId,
              threshold);
          jdbc.update(
              "DELETE FROM inbox_operator_operation WHERE binding_id=? AND created_at<?",
              bindingId,
              threshold);
          jdbc.update(
              "DELETE FROM inbox_callback_outbox WHERE binding_id=? AND completed_at IS NOT NULL AND completed_at<?",
              bindingId,
              threshold);
          jdbc.update(
              "DELETE FROM inbox_message WHERE binding_id=? AND handover_id IN (SELECT handover_id FROM inbox_conversation WHERE binding_id=? AND closed_at<?)",
              bindingId,
              bindingId,
              threshold);
          jdbc.update(
              "DELETE FROM inbox_callback_outbox WHERE binding_id=? AND handover_id IN (SELECT handover_id FROM inbox_conversation WHERE binding_id=? AND closed_at<?)",
              bindingId,
              bindingId,
              threshold);
          jdbc.update(
              "DELETE FROM inbox_conversation WHERE binding_id=? AND closed_at<?",
              bindingId,
              threshold);
          jdbc.update(
              "DELETE FROM inbox_dispatch_receipt WHERE binding_id=? AND received_at<?",
              bindingId,
              threshold);
          return true;
        });
  }

  private void requireBinding(String id) {
    if (!bindingId.equals(id))
      throw conflict("BINDING_MISMATCH", "Repository scope cannot change.");
  }

  private void requireExternalScope(ConversationRecord c, ConversationChannelDispatch d) {
    if (!bindingId.equals(d.channelBindingId())
        || !c.tenantId().equals(d.scope().tenantId())
        || !c.deploymentId().equals(d.scope().deploymentId())
        || !c.conversationId().equals(d.scope().conversationId())
        || !c.ownerRef().equals(d.scope().ownerId()))
      throw conflict(
          "DISPATCH_SCOPE_MISMATCH", "Dispatch does not match established conversation scope.");
  }

  private Map<String, Object> intake(ConversationRecord c) {
    return c.protectedIntake() == null
        ? Map.of()
        : read(security.decrypt(c.protectedIntake()), MAP);
  }

  private ConversationSummary summary(ConversationRecord c) {
    return new ConversationSummary(
        c.handoverId(),
        c.externalConversationRef(),
        c.status(),
        String.valueOf(intake(c).getOrDefault("name", "Anonymous visitor")),
        c.claimedBy(),
        c.claimedDisplayName(),
        c.unreadCount(),
        c.createdAt(),
        c.updatedAt());
  }

  private ConversationRecord map(ResultSet rs) throws SQLException {
    return new ConversationRecord(
        rs.getString("handover_id"),
        rs.getString("binding_id"),
        rs.getString("external_conversation_ref"),
        rs.getString("tenant_id"),
        rs.getString("deployment_id"),
        rs.getString("conversation_id"),
        rs.getString("owner_ref"),
        rs.getString("status"),
        rs.getLong("expected_control_version"),
        rs.getString("claimed_by"),
        rs.getString("claimed_display_name"),
        rs.getString("protected_intake"),
        rs.getInt("unread_count"),
        instant(rs, "created_at"),
        instant(rs, "updated_at"),
        rs.getTimestamp("closed_at") == null ? null : instant(rs, "closed_at"),
        rs.getLong("version"));
  }

  private Instant instant(ResultSet rs, String field) throws SQLException {
    return rs.getTimestamp(field).toInstant();
  }

  private Timestamp now() {
    return Timestamp.from(Instant.now());
  }

  private int bound(int limit) {
    return Math.max(1, Math.min(200, limit));
  }

  private String write(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException("Desk JSON serialization failed.", e);
    }
  }

  private <T> T read(String value, Class<T> type) {
    try {
      return mapper.readValue(value, type);
    } catch (Exception e) {
      throw new IllegalStateException("Desk JSON invalid.", e);
    }
  }

  private <T> T read(String value, TypeReference<T> type) {
    try {
      return mapper.readValue(value, type);
    } catch (Exception e) {
      throw new IllegalStateException("Desk JSON invalid.", e);
    }
  }

  private InboxConflictException conflict(String code, String message) {
    return new InboxConflictException(code, message);
  }

  public record ConversationRecord(
      String handoverId,
      String bindingId,
      String externalConversationRef,
      String tenantId,
      String deploymentId,
      String conversationId,
      String ownerRef,
      String status,
      long expectedControlVersion,
      String claimedBy,
      String claimedDisplayName,
      String protectedIntake,
      int unreadCount,
      Instant createdAt,
      Instant updatedAt,
      Instant closedAt,
      long version) {}

  public static class InboxConflictException extends RuntimeException {
    private final String code;

    public InboxConflictException(String code, String message) {
      super(message);
      this.code = code;
    }

    public String code() {
      return code;
    }
  }

  public static class InboxNotFoundException extends RuntimeException {
    private final String code;

    public InboxNotFoundException(String code, String message) {
      super(message);
      this.code = code;
    }

    public String code() {
      return code;
    }
  }
}
