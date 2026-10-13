package com.loomai.inbox.persistence;

import static com.loomai.inbox.model.InboxContracts.*;

import ai.fabric.chat.channel.ConversationChannelDispatch;
import ai.fabric.chat.channel.ConversationChannelMessage;
import ai.fabric.chat.control.ConversationParticipantRole;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loomai.inbox.service.InboxSecurityService;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class BasicInboxRepository {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<String>> LIST_TYPE = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ObjectMapper objectMapper;
    private final InboxSecurityService security;

    public BasicInboxRepository(
        DataSource dataSource,
        ObjectMapper objectMapper,
        InboxSecurityService security
    ) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        this.objectMapper = objectMapper;
        this.security = security;
    }

    public <T> T transactional(Supplier<T> operation) {
        return transactions.execute(status -> operation.get());
    }

    public Optional<DispatchResponse> dispatchReceipt(String dispatchId, String payloadSha256) {
        return jdbc.query(
            "SELECT payload_sha256, response_json FROM inbox_dispatch_receipt WHERE dispatch_id = ?",
            (rs, row) -> {
                if (!payloadSha256.equals(rs.getString("payload_sha256"))) {
                    throw new InboxConflictException(
                        "DISPATCH_ID_PAYLOAD_CONFLICT",
                        "A dispatch identifier was reused with a different payload."
                    );
                }
                return read(rs.getString("response_json"), DispatchResponse.class);
            },
            dispatchId
        ).stream().findFirst();
    }

    public void saveDispatchReceipt(String dispatchId, String payloadSha256, DispatchResponse response) {
        try {
            jdbc.update(
                "INSERT INTO inbox_dispatch_receipt (dispatch_id, payload_sha256, response_json, received_at) VALUES (?, ?, ?, ?)",
                dispatchId,
                payloadSha256,
                write(response),
                Timestamp.from(Instant.now())
            );
        } catch (DuplicateKeyException exception) {
            dispatchReceipt(dispatchId, payloadSha256);
        }
    }

    public ConversationRecord open(ConversationChannelDispatch dispatch, String externalRef) {
        Instant now = Instant.now();
        String protectedIntake = dispatch.consentedIntake().isEmpty()
            ? null
            : security.encrypt(write(dispatch.consentedIntake()));
        try {
            jdbc.update("""
                INSERT INTO inbox_conversation
                    (handover_id, binding_id, external_conversation_ref, tenant_id, deployment_id,
                     conversation_id, owner_ref, status, expected_control_version, claimed_by,
                     claimed_display_name, protected_intake, unread_count, created_at, updated_at,
                     closed_at, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'WAITING', ?, NULL, NULL, ?, 0, ?, ?, NULL, 0)
                """,
                dispatch.handoverId(),
                dispatch.channelBindingId(),
                externalRef,
                dispatch.scope().tenantId(),
                dispatch.scope().deploymentId(),
                dispatch.scope().conversationId(),
                dispatch.scope().ownerId(),
                dispatch.expectedControlVersion(),
                protectedIntake,
                Timestamp.from(now),
                Timestamp.from(now)
            );
        } catch (DuplicateKeyException ignored) {
            // OPEN_HANDOVER is idempotent at the handover boundary.
        }
        ConversationRecord conversation = requireConversation(dispatch.handoverId());
        requireExternalScope(conversation, dispatch);
        if (!externalRef.equals(conversation.externalConversationRef())
            || conversation.expectedControlVersion() != dispatch.expectedControlVersion()
            || !intake(conversation).equals(dispatch.consentedIntake())) {
            throw new InboxConflictException(
                "HANDOVER_ID_PAYLOAD_CONFLICT",
                "The handover identity was reused with different channel data."
            );
        }
        for (ConversationChannelMessage message : dispatch.transcript()) {
            storeProjectedMessage(dispatch.handoverId(), message);
        }
        return conversation;
    }

    public void acceptUserMessage(ConversationChannelDispatch dispatch) {
        ConversationRecord conversation = requireConversation(dispatch.handoverId());
        requireExternalScope(conversation, dispatch);
        String messageId = dispatch.messageId() == null ? dispatch.dispatchId() : dispatch.messageId();
        boolean inserted = storeProjectedMessage(
            dispatch.handoverId(),
            new ConversationChannelMessage(
                messageId,
                ConversationParticipantRole.USER,
                "conversation-user",
                null,
                dispatch.text() == null ? "[Attachment shared]" : dispatch.text(),
                dispatch.attachmentRefs(),
                dispatch.createdAt()
            )
        );
        if (inserted) {
            jdbc.update("""
                UPDATE inbox_conversation
                   SET unread_count = unread_count + 1,
                       expected_control_version = ?, updated_at = ?, version = version + 1
                 WHERE handover_id = ? AND status IN ('WAITING', 'ACTIVE')
                """,
                dispatch.expectedControlVersion(),
                Timestamp.from(Instant.now()),
                dispatch.handoverId()
            );
        }
    }

    private boolean storeProjectedMessage(String handoverId, ConversationChannelMessage message) {
        try {
            jdbc.update("""
                INSERT INTO inbox_message
                    (message_id, handover_id, participant_role, actor_ref, safe_actor_display_name,
                     content, attachment_refs_json, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                message.messageId(),
                handoverId,
                message.role().name(),
                message.actorRef(),
                message.safeActorDisplayName(),
                message.text(),
                write(message.attachmentRefs()),
                Timestamp.from(message.createdAt())
            );
            return true;
        } catch (DuplicateKeyException ignored) {
            verifyProjectedMessageReplay(handoverId, message);
            return false;
        }
    }

    private void verifyProjectedMessageReplay(
        String handoverId,
        ConversationChannelMessage message
    ) {
        List<Boolean> matches = jdbc.query(
            """
            SELECT handover_id, participant_role, actor_ref, safe_actor_display_name,
                   content, attachment_refs_json, created_at
              FROM inbox_message
             WHERE message_id = ?
            """,
            (rs, row) -> handoverId.equals(rs.getString("handover_id"))
                && message.role().name().equals(rs.getString("participant_role"))
                && java.util.Objects.equals(message.actorRef(), rs.getString("actor_ref"))
                && java.util.Objects.equals(
                    message.safeActorDisplayName(),
                    rs.getString("safe_actor_display_name")
                )
                && message.text().equals(rs.getString("content"))
                && message.attachmentRefs().equals(
                    read(rs.getString("attachment_refs_json"), LIST_TYPE)
                ),
            message.messageId()
        );
        if (matches.size() != 1 || !matches.getFirst()) {
            throw new InboxConflictException(
                "MESSAGE_ID_PAYLOAD_CONFLICT",
                "A projected message identifier was reused with different data."
            );
        }
    }

    public void applyControlDispatch(ConversationChannelDispatch dispatch) {
        ConversationRecord conversation = requireConversation(dispatch.handoverId());
        requireExternalScope(conversation, dispatch);
        String nextStatus = switch (dispatch.type()) {
            case CANCEL_HANDOVER -> "CANCELLED";
            case RETURN_TO_AI -> "RETURNED_TO_AI";
            case CLOSE -> "CLOSED";
            default -> throw new InboxConflictException("DISPATCH_TYPE_INVALID", "Unsupported control dispatch.");
        };
        int updated = jdbc.update("""
            UPDATE inbox_conversation
               SET status = ?, expected_control_version = ?, closed_at = ?, updated_at = ?, version = version + 1
             WHERE handover_id = ? AND status IN ('WAITING', 'CLAIM_PENDING', 'ACTIVE', 'RETURN_PENDING', 'CLOSE_PENDING')
            """,
            nextStatus,
            dispatch.expectedControlVersion(),
            Timestamp.from(Instant.now()),
            Timestamp.from(Instant.now()),
            dispatch.handoverId()
        );
        if (updated != 1) {
            throw new InboxConflictException(
                "CONTROL_DISPATCH_NOT_ALLOWED",
                "The conversation cannot accept this control dispatch in its current state."
            );
        }
    }

    public boolean hasIncompleteCallbacks(String handoverId) {
        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM inbox_callback_outbox WHERE handover_id = ? AND status <> 'COMPLETED'",
            Long.class,
            handoverId
        );
        return count != null && count > 0;
    }

    public List<ConversationSummary> listQueue(String bindingId, String status, int limit) {
        String normalizedStatus = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        String sql = normalizedStatus == null
            ? "SELECT * FROM inbox_conversation WHERE binding_id = ? ORDER BY updated_at DESC LIMIT ?"
            : "SELECT * FROM inbox_conversation WHERE binding_id = ? AND status = ? ORDER BY updated_at DESC LIMIT ?";
        Object[] args = normalizedStatus == null
            ? new Object[] { bindingId, boundedLimit(limit) }
            : new Object[] { bindingId, normalizedStatus, boundedLimit(limit) };
        return jdbc.query(sql, (rs, row) -> summary(mapConversation(rs)), args);
    }

    public ConversationDetail detail(String handoverId) {
        ConversationRecord conversation = requireConversation(handoverId);
        List<MessageView> messages = jdbc.query("""
            SELECT * FROM inbox_message WHERE handover_id = ? ORDER BY created_at, message_id
            """, (rs, row) -> new MessageView(
            rs.getString("message_id"),
            rs.getString("participant_role"),
            rs.getString("actor_ref"),
            rs.getString("safe_actor_display_name"),
            rs.getString("content"),
            read(rs.getString("attachment_refs_json"), LIST_TYPE),
            instant(rs, "created_at")
        ), handoverId);
        return new ConversationDetail(summary(conversation), intake(conversation), messages);
    }

    public ConversationRecord requireConversation(String handoverId) {
        return jdbc.query(
            "SELECT * FROM inbox_conversation WHERE handover_id = ?",
            (rs, row) -> mapConversation(rs),
            handoverId
        ).stream().findFirst().orElseThrow(() -> new InboxNotFoundException(
            "INBOX_CONVERSATION_NOT_FOUND",
            "The inbox conversation is not available."
        ));
    }

    public boolean claim(String handoverId, String operatorId, String displayName) {
        Instant now = Instant.now();
        int updated = jdbc.update("""
            UPDATE inbox_conversation
               SET status = 'CLAIM_PENDING', claimed_by = ?, claimed_display_name = ?,
                   unread_count = 0, updated_at = ?, version = version + 1
             WHERE handover_id = ?
               AND binding_id = ?
               AND (status = 'WAITING' OR (status = 'CLAIM_PENDING' AND claimed_by = ?))
            """,
            operatorId,
            displayName,
            Timestamp.from(now),
            handoverId,
            requireConversation(handoverId).bindingId(),
            operatorId
        );
        return updated == 1;
    }

    public void markActive(String handoverId, long nextControlVersion) {
        updateAfterCallback(handoverId, "ACTIVE", nextControlVersion);
    }

    public String addHumanMessage(
        String handoverId,
        String messageId,
        String operatorId,
        String displayName,
        String content
    ) {
        ConversationRecord conversation = requireConversation(handoverId);
        if (!"ACTIVE".equals(conversation.status()) || !operatorId.equals(conversation.claimedBy())) {
            throw new InboxConflictException(
                "INBOX_CONVERSATION_NOT_CLAIMED",
                "Only the active operator may reply to this conversation."
            );
        }
        try {
            jdbc.update("""
                INSERT INTO inbox_message
                    (message_id, handover_id, participant_role, actor_ref, safe_actor_display_name,
                     content, attachment_refs_json, created_at)
                VALUES (?, ?, 'HUMAN', ?, ?, ?, '[]', ?)
                """,
                messageId,
                handoverId,
                operatorId,
                displayName,
                content,
                Timestamp.from(Instant.now())
            );
        } catch (DuplicateKeyException ignored) {
            requireHumanMessageReplay(messageId, handoverId, operatorId, displayName, content);
        }
        return messageId;
    }

    public boolean humanMessageExists(
        String messageId,
        String handoverId,
        String operatorId,
        String displayName,
        String content
    ) {
        List<Boolean> matches = jdbc.query(
            "SELECT handover_id, participant_role, actor_ref, safe_actor_display_name, content"
                + " FROM inbox_message WHERE message_id = ?",
            (rs, row) -> {
                if (!handoverId.equals(rs.getString("handover_id"))
                    || !"HUMAN".equals(rs.getString("participant_role"))
                    || !operatorId.equals(rs.getString("actor_ref"))
                    || !java.util.Objects.equals(displayName, rs.getString("safe_actor_display_name"))
                    || !content.equals(rs.getString("content"))) {
                    throw new InboxConflictException(
                        "IDEMPOTENCY_KEY_PAYLOAD_CONFLICT",
                        "The operator idempotency key was reused with different reply data."
                    );
                }
                return Boolean.TRUE;
            },
            messageId
        );
        return !matches.isEmpty();
    }

    private void requireHumanMessageReplay(
        String messageId,
        String handoverId,
        String operatorId,
        String displayName,
        String content
    ) {
        if (!humanMessageExists(messageId, handoverId, operatorId, displayName, content)) {
            throw new InboxConflictException(
                "IDEMPOTENCY_REPLAY_NOT_FOUND",
                "The existing operator reply could not be verified."
            );
        }
    }

    public void markTransitionPending(String handoverId, String operatorId, String nextStatus) {
        int updated = jdbc.update("""
            UPDATE inbox_conversation
               SET status = ?, updated_at = ?, version = version + 1
             WHERE handover_id = ? AND status = 'ACTIVE' AND claimed_by = ?
            """,
            nextStatus,
            Timestamp.from(Instant.now()),
            handoverId,
            operatorId
        );
        if (updated != 1) {
            throw new InboxConflictException(
                "INBOX_CONVERSATION_NOT_CLAIMED",
                "Only the active operator may change this conversation."
            );
        }
    }

    public boolean callbackExists(String callbackId, String handoverId, String eventType) {
        List<Boolean> matches = jdbc.query(
            "SELECT handover_id, event_type FROM inbox_callback_outbox WHERE callback_id = ?",
            (rs, row) -> {
                if (!handoverId.equals(rs.getString("handover_id"))
                    || !eventType.equals(rs.getString("event_type"))) {
                    throw new InboxConflictException(
                        "IDEMPOTENCY_KEY_PAYLOAD_CONFLICT",
                        "The operator idempotency key was reused for a different callback command."
                    );
                }
                return Boolean.TRUE;
            },
            callbackId
        );
        return !matches.isEmpty();
    }

    public void updateAfterCallback(String handoverId, String status, long nextControlVersion) {
        jdbc.update("""
            UPDATE inbox_conversation
               SET status = ?, expected_control_version = ?,
                   closed_at = CASE WHEN ? IN ('RETURNED_TO_AI', 'CLOSED') THEN ? ELSE closed_at END,
                   updated_at = ?, version = version + 1
             WHERE handover_id = ?
            """,
            status,
            nextControlVersion,
            status,
            Timestamp.from(Instant.now()),
            Timestamp.from(Instant.now()),
            handoverId
        );
    }

    public void enqueueCallback(String callbackId, String handoverId, String eventType, String payload) {
        transactional(() -> {
            Instant now = Instant.now();
            jdbc.queryForObject(
                "SELECT handover_id FROM inbox_conversation WHERE handover_id = ? FOR UPDATE",
                String.class,
                handoverId
            );
            Long maximum = jdbc.queryForObject(
                "SELECT COALESCE(MAX(callback_sequence), 0)"
                    + " FROM inbox_callback_outbox WHERE handover_id = ?",
                Long.class,
                handoverId
            );
            long sequence = (maximum == null ? 0 : maximum) + 1;
            try {
                jdbc.update("""
                    INSERT INTO inbox_callback_outbox
                        (callback_id, handover_id, callback_sequence, event_type, protected_payload,
                         status, attempt_count, next_attempt_at, lease_owner, lease_until,
                         failure_code, created_at, completed_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, 'PENDING', 0, ?, NULL, NULL, NULL, ?, NULL, ?)
                    """,
                    callbackId,
                    handoverId,
                    sequence,
                    eventType,
                    security.encrypt(payload),
                    Timestamp.from(now),
                    Timestamp.from(now),
                    Timestamp.from(now)
                );
            } catch (DuplicateKeyException ignored) {
                callbackExists(callbackId, handoverId, eventType);
            }
            return Boolean.TRUE;
        });
    }

    public Optional<CallbackClaim> claimCallback(Duration lease) {
        String leaseOwner = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant leaseUntil = now.plus(lease);
        int updated = jdbc.update("""
            UPDATE inbox_callback_outbox
               SET status = 'LEASED', lease_owner = ?, lease_until = ?,
                   attempt_count = attempt_count + 1, updated_at = ?
             WHERE callback_id = (
               SELECT candidate.callback_id
                 FROM inbox_callback_outbox candidate
                WHERE ((candidate.status IN ('PENDING', 'RETRY')
                         AND candidate.next_attempt_at <= ?)
                       OR (candidate.status = 'LEASED' AND candidate.lease_until < ?))
                  AND NOT EXISTS (
                    SELECT 1
                      FROM inbox_callback_outbox earlier
                     WHERE earlier.handover_id = candidate.handover_id
                       AND earlier.callback_sequence < candidate.callback_sequence
                       AND earlier.status <> 'COMPLETED'
                  )
                ORDER BY candidate.created_at, candidate.handover_id, candidate.callback_sequence
                LIMIT 1
             )
               AND ((status IN ('PENDING', 'RETRY') AND next_attempt_at <= ?)
                 OR (status = 'LEASED' AND lease_until < ?))
            """,
            leaseOwner,
            Timestamp.from(leaseUntil),
            Timestamp.from(now),
            Timestamp.from(now),
            Timestamp.from(now),
            Timestamp.from(now),
            Timestamp.from(now)
        );
        if (updated != 1) {
            return Optional.empty();
        }
        return jdbc.query("""
            SELECT callback_id, handover_id, event_type, protected_payload, attempt_count
              FROM inbox_callback_outbox WHERE lease_owner = ? AND status = 'LEASED'
            """, (rs, row) -> new CallbackClaim(
            rs.getString("callback_id"),
            rs.getString("handover_id"),
            rs.getString("event_type"),
            security.decrypt(rs.getString("protected_payload")),
            rs.getInt("attempt_count"),
            leaseOwner
        ), leaseOwner).stream().findFirst();
    }

    public boolean completeCallback(String callbackId, int attempt, String leaseOwner) {
        Instant now = Instant.now();
        return jdbc.update("""
            UPDATE inbox_callback_outbox
               SET status = 'COMPLETED', lease_owner = NULL, lease_until = NULL,
                   completed_at = ?, updated_at = ?
             WHERE callback_id = ? AND status = 'LEASED'
               AND attempt_count = ? AND lease_owner = ?
            """, Timestamp.from(now), Timestamp.from(now), callbackId, attempt, leaseOwner) == 1;
    }

    public boolean completeCallbackAndUpdateConversation(
        String callbackId,
        String handoverId,
        int attempt,
        String leaseOwner,
        String status,
        long nextControlVersion
    ) {
        Boolean completed = transactions.execute(transaction -> {
            Instant now = Instant.now();
            int claimed = jdbc.update("""
                UPDATE inbox_callback_outbox
                   SET status = 'COMPLETED', lease_owner = NULL, lease_until = NULL,
                       completed_at = ?, updated_at = ?
                 WHERE callback_id = ? AND handover_id = ? AND status = 'LEASED'
                   AND attempt_count = ? AND lease_owner = ?
                """,
                Timestamp.from(now), Timestamp.from(now), callbackId, handoverId, attempt, leaseOwner
            );
            if (claimed != 1) {
                return false;
            }
            int updated = jdbc.update("""
                UPDATE inbox_conversation
                   SET status = ?, expected_control_version = ?,
                       closed_at = CASE WHEN ? IN ('RETURNED_TO_AI', 'CLOSED') THEN ? ELSE closed_at END,
                       updated_at = ?, version = version + 1
                 WHERE handover_id = ?
                """,
                status,
                nextControlVersion,
                status,
                Timestamp.from(now),
                Timestamp.from(now),
                handoverId
            );
            if (updated != 1) {
                throw new InboxConflictException(
                    "CALLBACK_CONVERSATION_MISSING",
                    "The callback conversation could not be updated."
                );
            }
            return true;
        });
        return Boolean.TRUE.equals(completed);
    }

    public boolean failCallback(
        String callbackId,
        int attempt,
        String leaseOwner,
        boolean retryable,
        int maxAttempts,
        Duration retryDelay,
        String code
    ) {
        boolean retry = retryable && attempt < maxAttempts;
        Instant now = Instant.now();
        return jdbc.update("""
            UPDATE inbox_callback_outbox
               SET status = ?, next_attempt_at = ?, lease_owner = NULL, lease_until = NULL,
                   failure_code = ?, updated_at = ?
             WHERE callback_id = ? AND status = 'LEASED'
               AND attempt_count = ? AND lease_owner = ?
            """,
            retry ? "RETRY" : "DEAD",
            Timestamp.from(retry ? now.plus(retryDelay.multipliedBy(Math.max(1, attempt))) : now),
            code,
            Timestamp.from(now),
            callbackId,
            attempt,
            leaseOwner
        ) == 1;
    }

    public List<CallbackOperationView> callbackOperations(String bindingId, int limit) {
        return jdbc.query("""
            SELECT callback.callback_id, callback.handover_id, callback.event_type,
                   callback.status, callback.attempt_count, callback.failure_code,
                   callback.created_at, callback.updated_at
              FROM inbox_callback_outbox callback
              JOIN inbox_conversation conversation
                ON conversation.handover_id = callback.handover_id
             WHERE conversation.binding_id = ?
             ORDER BY callback.updated_at DESC, callback.callback_id DESC
             LIMIT ?
            """, (rs, row) -> new CallbackOperationView(
            rs.getString("callback_id"),
            rs.getString("handover_id"),
            rs.getString("event_type"),
            rs.getString("status"),
            rs.getInt("attempt_count"),
            rs.getString("failure_code"),
            instant(rs, "created_at"),
            instant(rs, "updated_at"),
            "DEAD".equals(rs.getString("status"))
        ), bindingId, boundedLimit(limit));
    }

    public boolean retryDeadCallback(String bindingId, String callbackId) {
        Instant now = Instant.now();
        return jdbc.update("""
            UPDATE inbox_callback_outbox callback
               SET status = 'PENDING', next_attempt_at = ?, lease_owner = NULL,
                   lease_until = NULL, failure_code = NULL, completed_at = NULL,
                   updated_at = ?
             WHERE callback.callback_id = ? AND callback.status = 'DEAD'
               AND EXISTS (
                 SELECT 1
                   FROM inbox_conversation conversation
                  WHERE conversation.handover_id = callback.handover_id
                    AND conversation.binding_id = ?
               )
            """, Timestamp.from(now), Timestamp.from(now), callbackId, bindingId) == 1;
    }

    public boolean operatorOperationExists(
        String operationId,
        String bindingId,
        String operatorId,
        String operationType,
        String targetId
    ) {
        List<Boolean> matches = jdbc.query("""
            SELECT binding_id, operator_id, operation_type, target_id
              FROM inbox_operator_operation
             WHERE operation_id = ?
            """, (rs, row) -> bindingId.equals(rs.getString("binding_id"))
            && operatorId.equals(rs.getString("operator_id"))
            && operationType.equals(rs.getString("operation_type"))
            && targetId.equals(rs.getString("target_id")), operationId);
        if (!matches.isEmpty() && !matches.getFirst()) {
            throw new InboxConflictException(
                "IDEMPOTENCY_KEY_PAYLOAD_CONFLICT",
                "The operator idempotency key was reused for a different operation."
            );
        }
        return !matches.isEmpty();
    }

    public void recordOperatorOperation(
        String operationId,
        String bindingId,
        String operatorId,
        String operationType,
        String targetId
    ) {
        try {
            jdbc.update("""
                INSERT INTO inbox_operator_operation (
                  operation_id, binding_id, operator_id, operation_type, target_id, created_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """, operationId, bindingId, operatorId, operationType, targetId,
                Timestamp.from(Instant.now()));
        } catch (DuplicateKeyException ignored) {
            operatorOperationExists(operationId, bindingId, operatorId, operationType, targetId);
        }
    }

    public void purgeBefore(Instant cutoff) {
        transactional(() -> {
            Timestamp threshold = Timestamp.from(cutoff);
            jdbc.update(
                "UPDATE inbox_conversation SET protected_intake = NULL"
                    + " WHERE protected_intake IS NOT NULL AND created_at < ?",
                threshold
            );
            jdbc.update(
                "DELETE FROM inbox_operator_operation WHERE created_at < ?",
                threshold
            );
            jdbc.update(
                "DELETE FROM inbox_callback_outbox WHERE completed_at IS NOT NULL AND completed_at < ?",
                threshold
            );
            jdbc.update(
                "DELETE FROM inbox_message WHERE handover_id IN ("
                    + "SELECT handover_id FROM inbox_conversation WHERE closed_at IS NOT NULL AND closed_at < ?)",
                threshold
            );
            jdbc.update(
                "DELETE FROM inbox_callback_outbox WHERE handover_id IN ("
                    + "SELECT handover_id FROM inbox_conversation WHERE closed_at IS NOT NULL AND closed_at < ?)",
                threshold
            );
            jdbc.update(
                "DELETE FROM inbox_conversation WHERE closed_at IS NOT NULL AND closed_at < ?",
                threshold
            );
            jdbc.update("DELETE FROM inbox_dispatch_receipt WHERE received_at < ?", threshold);
            return Boolean.TRUE;
        });
    }

    private void requireExternalScope(ConversationRecord conversation, ConversationChannelDispatch dispatch) {
        if (!conversation.bindingId().equals(dispatch.channelBindingId())
            || !conversation.tenantId().equals(dispatch.scope().tenantId())
            || !conversation.deploymentId().equals(dispatch.scope().deploymentId())
            || !conversation.conversationId().equals(dispatch.scope().conversationId())
            || !conversation.ownerRef().equals(dispatch.scope().ownerId())) {
            throw new InboxConflictException(
                "DISPATCH_SCOPE_MISMATCH",
                "The dispatch does not match the established conversation scope."
            );
        }
    }

    private Map<String, Object> intake(ConversationRecord conversation) {
        if (conversation.protectedIntake() == null) {
            return Map.of();
        }
        return read(security.decrypt(conversation.protectedIntake()), MAP_TYPE);
    }

    private ConversationSummary summary(ConversationRecord record) {
        Map<String, Object> intake = intake(record);
        String label = String.valueOf(intake.getOrDefault("name", "Anonymous visitor"));
        return new ConversationSummary(
            record.handoverId(), record.externalConversationRef(), record.status(), label,
            record.claimedBy(), record.claimedDisplayName(), record.unreadCount(),
            record.createdAt(), record.updatedAt()
        );
    }

    private ConversationRecord mapConversation(ResultSet rs) throws SQLException {
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
            nullableInstant(rs, "closed_at"),
            rs.getLong("version")
        );
    }

    private Instant instant(ResultSet rs, String field) throws SQLException {
        return rs.getTimestamp(field).toInstant();
    }

    private Instant nullableInstant(ResultSet rs, String field) throws SQLException {
        Timestamp value = rs.getTimestamp(field);
        return value == null ? null : value.toInstant();
    }

    private int boundedLimit(int limit) {
        return Math.max(1, Math.min(200, limit));
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Inbox JSON serialization failed.", exception);
        }
    }

    private <T> T read(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (Exception exception) {
            throw new IllegalStateException("Inbox JSON is invalid.", exception);
        }
    }

    private <T> T read(String value, TypeReference<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (Exception exception) {
            throw new IllegalStateException("Inbox JSON is invalid.", exception);
        }
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
        long version
    ) {}

    public static class InboxConflictException extends RuntimeException {
        private final String code;
        public InboxConflictException(String code, String message) { super(message); this.code = code; }
        public String code() { return code; }
    }

    public static class InboxNotFoundException extends RuntimeException {
        private final String code;
        public InboxNotFoundException(String code, String message) { super(message); this.code = code; }
        public String code() { return code; }
    }
}
