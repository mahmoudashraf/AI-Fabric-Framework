package com.ai.fabric.runtime.handover;

import ai.fabric.chat.channel.ConversationChannelDeliveryResult;
import ai.fabric.chat.channel.ConversationChannelDispatch;
import ai.fabric.chat.channel.ConversationChannelDispatchType;
import ai.fabric.chat.control.ConversationScope;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

final class RuntimeConversationHandoverRepository {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ObjectMapper objectMapper;
    private final ConversationHandoverSecurity security;
    private final Clock clock;

    RuntimeConversationHandoverRepository(
        DataSource dataSource,
        PlatformTransactionManager transactionManager,
        ObjectMapper objectMapper,
        ConversationHandoverSecurity security,
        Clock clock
    ) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.transactions = new TransactionTemplate(transactionManager);
        this.objectMapper = objectMapper.copy();
        this.security = security;
        this.clock = clock;
    }

    <T> T transactional(Supplier<T> work) {
        T result = transactions.execute(status -> work.get());
        return java.util.Objects.requireNonNull(result, "conversation handover transaction returned no result");
    }

    HandoverRecord createHandoverIfAbsent(
        String handoverId,
        ConversationScope scope,
        String channelBindingId,
        String idempotencyKey,
        String reasonCode,
        Instant expiresAt
    ) {
        Instant now = clock.instant();
        try {
            jdbc.update(
                """
                INSERT INTO ai_conversation_handover (
                  handover_id, tenant_id, deployment_id, conversation_id, owner_id,
                  channel_binding_id, request_idempotency_key, reason_code, status,
                  created_at, updated_at, expires_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, ?, ?, 0)
                """,
                handoverId, scope.tenantId(), scope.deploymentId(), scope.conversationId(), scope.ownerId(),
                channelBindingId, idempotencyKey, trim(reasonCode), timestamp(now), timestamp(now), timestamp(expiresAt)
            );
        } catch (DuplicateKeyException ignored) {
            // Idempotent retries return the previously created handover below.
        }
        return findByRequest(scope, idempotencyKey).orElseThrow(() ->
            new IllegalStateException("Conversation handover could not be created."));
    }

    Optional<HandoverRecord> findByRequest(ConversationScope scope, String idempotencyKey) {
        return jdbc.query(
            handoverSelect() + " WHERE tenant_id = ? AND deployment_id = ? AND conversation_id = ?"
                + " AND owner_id = ? AND request_idempotency_key = ?",
            this::mapHandover,
            scope.tenantId(), scope.deploymentId(), scope.conversationId(), scope.ownerId(), idempotencyKey
        ).stream().findFirst();
    }

    Optional<HandoverRecord> findForOwner(ConversationScope scope, String handoverId) {
        return jdbc.query(
            handoverSelect() + " WHERE handover_id = ? AND tenant_id = ? AND deployment_id = ?"
                + " AND conversation_id = ? AND owner_id = ?",
            this::mapHandover,
            handoverId, scope.tenantId(), scope.deploymentId(), scope.conversationId(), scope.ownerId()
        ).stream().findFirst();
    }

    Optional<HandoverRecord> findForBinding(String channelBindingId, String handoverId) {
        return jdbc.query(
            handoverSelect() + " WHERE handover_id = ? AND channel_binding_id = ?",
            this::mapHandover,
            handoverId, channelBindingId
        ).stream().findFirst();
    }

    Optional<HandoverRecord> findById(String handoverId) {
        return jdbc.query(
            handoverSelect() + " WHERE handover_id = ?",
            this::mapHandover,
            handoverId
        ).stream().findFirst();
    }

    Optional<CanonicalConversationRecord> findCanonicalConversation(ConversationScope scope) {
        return canonicalConversationQuery(
            " AND c.conversation_id = ?"
                + " GROUP BY c.conversation_id, c.control_state, c.updated_at",
            scope.tenantId(), scope.deploymentId(), scope.ownerId(), scope.conversationId()
        ).stream().findFirst();
    }

    List<CanonicalConversationRecord> listCanonicalConversations(
        String tenantId,
        String deploymentId,
        String ownerId,
        int limit
    ) {
        int boundedLimit = Math.max(1, Math.min(limit, 100));
        return List.copyOf(canonicalConversationQuery(
            " GROUP BY c.conversation_id, c.control_state, c.updated_at"
                + " HAVING COUNT(m.message_id) > 0"
                + " ORDER BY COALESCE(MAX(m.created_at), c.updated_at) DESC LIMIT ?",
            tenantId, deploymentId, ownerId, boundedLimit
        ));
    }

    private List<CanonicalConversationRecord> canonicalConversationQuery(String suffix, Object... arguments) {
        return jdbc.query(
            """
            SELECT c.conversation_id, c.control_state, c.updated_at,
                   MIN(m.created_at) AS created_at,
                   MAX(m.created_at) AS last_message_at,
                   SUM(CASE WHEN m.participant_role = 'USER' THEN 1 ELSE 0 END) AS user_message_count
              FROM ai_conversation_control c
              LEFT JOIN ai_conversation_message m
                ON m.tenant_id = c.tenant_id
               AND m.deployment_id = c.deployment_id
               AND m.conversation_id = c.conversation_id
               AND m.owner_id = c.owner_id
             WHERE c.tenant_id = ? AND c.deployment_id = ? AND c.owner_id = ?
            """ + suffix,
            (rs, row) -> {
                Instant controlUpdatedAt = instant(rs, "updated_at");
                Instant createdAt = nullableInstant(rs, "created_at");
                Instant lastMessageAt = nullableInstant(rs, "last_message_at");
                Instant lastInteractionAt = lastMessageAt == null || controlUpdatedAt.isAfter(lastMessageAt)
                    ? controlUpdatedAt
                    : lastMessageAt;
                return new CanonicalConversationRecord(
                    rs.getString("conversation_id"),
                    rs.getString("control_state"),
                    createdAt == null ? controlUpdatedAt : createdAt,
                    lastInteractionAt,
                    rs.getInt("user_message_count")
                );
            },
            arguments
        );
    }

    InteractionClaim claimInteraction(
        ConversationScope scope,
        String idempotencyKey,
        String userMessageId,
        String requestFingerprint
    ) {
        return transactional(() -> {
            List<String> owners = jdbc.query(
                "SELECT owner_id FROM ai_conversation_control"
                    + " WHERE tenant_id = ? AND deployment_id = ? AND conversation_id = ? FOR UPDATE",
                (rs, row) -> rs.getString("owner_id"),
                scope.tenantId(), scope.deploymentId(), scope.conversationId()
            );
            if (owners.size() != 1 || !scope.ownerId().equals(owners.getFirst())) {
                throw new ConversationHandoverException(
                    "CONVERSATION_NOT_AVAILABLE",
                    "Conversation is not available.",
                    org.springframework.http.HttpStatus.NOT_FOUND
                );
            }

            Optional<InteractionRecord> existing = findInteraction(scope, idempotencyKey);
            if (existing.isPresent()) {
                return new InteractionClaim(
                    verifyInteractionReplay(existing.get(), userMessageId, requestFingerprint),
                    false
                );
            }

            Instant now = clock.instant();
            try {
                jdbc.update(
                    """
                    INSERT INTO ai_conversation_ai_interaction (
                      tenant_id, deployment_id, conversation_id, owner_id,
                      idempotency_key, user_message_id, request_fingerprint, status,
                      created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, 'IN_PROGRESS', ?, ?)
                    """,
                    scope.tenantId(), scope.deploymentId(), scope.conversationId(), scope.ownerId(),
                    idempotencyKey, userMessageId, requestFingerprint, timestamp(now), timestamp(now)
                );
            } catch (DuplicateKeyException ignored) {
                return new InteractionClaim(
                    verifyInteractionReplay(
                        findInteraction(scope, idempotencyKey).orElseThrow(() ->
                            new IllegalStateException("Concurrent AI interaction receipt is unavailable.")),
                        userMessageId,
                        requestFingerprint
                    ),
                    false
                );
            }
            return new InteractionClaim(
                findInteraction(scope, idempotencyKey).orElseThrow(() ->
                    new IllegalStateException("AI interaction receipt was not stored.")),
                true
            );
        });
    }

    InteractionRecord completeInteraction(
        ConversationScope scope,
        String idempotencyKey,
        int responseHttpStatus,
        String responseJson
    ) {
        Instant now = clock.instant();
        int updated = jdbc.update(
            """
            UPDATE ai_conversation_ai_interaction
               SET status = 'COMPLETED', response_http_status = ?, protected_response = ?,
                   failure_code = NULL, updated_at = ?
             WHERE tenant_id = ? AND deployment_id = ? AND conversation_id = ?
               AND owner_id = ? AND idempotency_key = ? AND status = 'IN_PROGRESS'
            """,
            responseHttpStatus, security.encrypt(responseJson), timestamp(now),
            scope.tenantId(), scope.deploymentId(), scope.conversationId(), scope.ownerId(), idempotencyKey
        );
        InteractionRecord record = findInteraction(scope, idempotencyKey).orElseThrow(() ->
            new IllegalStateException("AI interaction receipt is unavailable."));
        if (updated == 1) {
            return record;
        }
        if (!"COMPLETED".equals(record.status())
            || !java.util.Objects.equals(record.responseHttpStatus(), responseHttpStatus)
            || !java.util.Objects.equals(record.responseJson(), responseJson)) {
            throw new ConversationHandoverException(
                "AI_INTERACTION_IDEMPOTENCY_CONFLICT",
                "The AI interaction was already finalized with a different result.",
                org.springframework.http.HttpStatus.CONFLICT
            );
        }
        return record;
    }

    void failInteraction(ConversationScope scope, String idempotencyKey, String failureCode) {
        jdbc.update(
            """
            UPDATE ai_conversation_ai_interaction
               SET status = 'FAILED', failure_code = ?, updated_at = ?
             WHERE tenant_id = ? AND deployment_id = ? AND conversation_id = ?
               AND owner_id = ? AND idempotency_key = ? AND status = 'IN_PROGRESS'
            """,
            bounded(failureCode, 100), timestamp(clock.instant()),
            scope.tenantId(), scope.deploymentId(), scope.conversationId(), scope.ownerId(), idempotencyKey
        );
    }

    List<CompletedPresentationRecord> completedPresentations(ConversationScope scope) {
        return jdbc.query("""
            SELECT i.user_message_id, m.message_id AS ai_message_id, i.protected_response, i.updated_at
              FROM ai_conversation_ai_interaction i
              JOIN ai_conversation_message m
                ON m.tenant_id = i.tenant_id AND m.deployment_id = i.deployment_id
               AND m.conversation_id = i.conversation_id AND m.owner_id = i.owner_id
               AND m.client_message_id = i.idempotency_key || ':ai' AND m.participant_role = 'AI'
             WHERE i.tenant_id = ? AND i.deployment_id = ? AND i.conversation_id = ?
               AND i.owner_id = ? AND i.status = 'COMPLETED'
             ORDER BY i.updated_at DESC LIMIT 20
            """, (rows, index) -> new CompletedPresentationRecord(
                rows.getString("user_message_id"), rows.getString("ai_message_id"), security.decrypt(rows.getString("protected_response")),
                rows.getTimestamp("updated_at").toInstant()),
            scope.tenantId(), scope.deploymentId(), scope.conversationId(), scope.ownerId());
    }

    record CompletedPresentationRecord(String userMessageId, String aiMessageId, String responseJson, Instant completedAt) { }

    private Optional<InteractionRecord> findInteraction(
        ConversationScope scope,
        String idempotencyKey
    ) {
        return jdbc.query(
            """
            SELECT user_message_id, request_fingerprint, status, response_http_status,
                   protected_response, failure_code, created_at, updated_at
              FROM ai_conversation_ai_interaction
             WHERE tenant_id = ? AND deployment_id = ? AND conversation_id = ?
               AND owner_id = ? AND idempotency_key = ?
            """,
            (rs, row) -> new InteractionRecord(
                rs.getString("user_message_id"),
                rs.getString("request_fingerprint"),
                rs.getString("status"),
                (Integer) rs.getObject("response_http_status"),
                rs.getString("protected_response") == null
                    ? null
                    : security.decrypt(rs.getString("protected_response")),
                rs.getString("failure_code"),
                instant(rs, "created_at"),
                instant(rs, "updated_at")
            ),
            scope.tenantId(), scope.deploymentId(), scope.conversationId(), scope.ownerId(), idempotencyKey
        ).stream().findFirst();
    }

    private InteractionRecord verifyInteractionReplay(
        InteractionRecord existing,
        String userMessageId,
        String requestFingerprint
    ) {
        if (!existing.userMessageId().equals(userMessageId)
            || !existing.requestFingerprint().equals(requestFingerprint)) {
            throw new ConversationHandoverException(
                "AI_INTERACTION_IDEMPOTENCY_CONFLICT",
                "The AI interaction idempotency key was reused with different request data.",
                org.springframework.http.HttpStatus.CONFLICT
            );
        }
        return existing;
    }

    Optional<String> findOperationHandover(
        ConversationScope scope,
        String operationType,
        String idempotencyKey
    ) {
        return jdbc.query(
            """
            SELECT handover_id
              FROM ai_conversation_handover_operation
             WHERE tenant_id = ? AND deployment_id = ? AND conversation_id = ?
               AND owner_id = ? AND operation_type = ? AND idempotency_key = ?
            """,
            (rs, row) -> rs.getString("handover_id"),
            scope.tenantId(), scope.deploymentId(), scope.conversationId(), scope.ownerId(),
            operationType, idempotencyKey
        ).stream().findFirst();
    }

    void recordOperation(
        ConversationScope scope,
        String operationType,
        String idempotencyKey,
        String handoverId
    ) {
        try {
            jdbc.update(
                """
                INSERT INTO ai_conversation_handover_operation (
                  tenant_id, deployment_id, conversation_id, owner_id,
                  operation_type, idempotency_key, handover_id, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                scope.tenantId(), scope.deploymentId(), scope.conversationId(), scope.ownerId(),
                operationType, idempotencyKey, handoverId, timestamp(clock.instant())
            );
        } catch (DuplicateKeyException ignored) {
            String existing = findOperationHandover(scope, operationType, idempotencyKey)
                .orElseThrow(() -> new IllegalStateException("Conversation operation receipt was not stored."));
            if (!existing.equals(handoverId)) {
                throw new IllegalStateException("Conversation operation idempotency key was reused.");
            }
        }
    }

    void saveIntake(String handoverId, List<IntakeValue> values, Instant expiresAt) {
        Instant now = clock.instant();
        for (IntakeValue value : values) {
            jdbc.update(
                """
                INSERT INTO ai_conversation_handover_intake (
                  handover_id, field_key, protected_value, purpose, consented,
                  verified, created_at, expires_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                handoverId, value.key(), security.encrypt(value.value()), value.purpose(),
                value.consented(), value.verified(), timestamp(now), timestamp(expiresAt)
            );
        }
    }

    Map<String, Object> consentedIntake(String handoverId) {
        Map<String, Object> values = new LinkedHashMap<>();
        jdbc.query(
            """
            SELECT field_key, protected_value
              FROM ai_conversation_handover_intake
             WHERE handover_id = ? AND consented = TRUE AND expires_at > ?
             ORDER BY field_key
            """,
            (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                values.put(rs.getString("field_key"), security.decrypt(rs.getString("protected_value"))),
            handoverId, timestamp(clock.instant())
        );
        return Map.copyOf(values);
    }

    boolean addDispatch(ConversationChannelDispatch dispatch, String idempotencyKey) {
        return transactional(() -> {
            Instant now = clock.instant();
            jdbc.queryForObject(
                "SELECT handover_id FROM ai_conversation_handover WHERE handover_id = ? FOR UPDATE",
                String.class,
                dispatch.handoverId()
            );
            if (dispatchReplayExists(dispatch.dispatchId(), idempotencyKey)) {
                verifyDispatchReplay(dispatch, idempotencyKey);
                return Boolean.FALSE;
            }
            Long maximum = jdbc.queryForObject(
                "SELECT COALESCE(MAX(dispatch_sequence), 0)"
                    + " FROM ai_conversation_handover_dispatch WHERE handover_id = ?",
                Long.class,
                dispatch.handoverId()
            );
            long sequence = (maximum == null ? 0 : maximum) + 1;
            jdbc.update(
                """
                INSERT INTO ai_conversation_handover_dispatch (
                  dispatch_id, handover_id, dispatch_sequence, dispatch_type, idempotency_key,
                  protected_payload, status, attempt_count, next_attempt_at,
                  created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, 'PENDING', 0, ?, ?, ?, 0)
                """,
                dispatch.dispatchId(), dispatch.handoverId(), sequence, dispatch.type().name(),
                idempotencyKey, security.encrypt(writeJson(dispatch)), timestamp(now),
                timestamp(now), timestamp(now)
            );
            return Boolean.TRUE;
        });
    }

    private boolean dispatchReplayExists(String dispatchId, String idempotencyKey) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM ai_conversation_handover_dispatch"
                + " WHERE dispatch_id = ? OR idempotency_key = ?",
            Integer.class,
            dispatchId,
            idempotencyKey
        );
        return count != null && count > 0;
    }

    Optional<ClaimedDispatch> claimNextDispatch(String workerId, Duration leaseDuration) {
        return transactional(() -> {
            Instant now = clock.instant();
            List<DispatchRow> candidates = jdbc.query(
                """
                SELECT candidate.dispatch_id, candidate.handover_id, candidate.dispatch_type,
                       candidate.protected_payload, candidate.attempt_count, candidate.version
                  FROM ai_conversation_handover_dispatch candidate
                 WHERE (candidate.status = 'PENDING'
                        OR (candidate.status = 'PROCESSING' AND candidate.lease_until < ?))
                   AND candidate.next_attempt_at <= ?
                   AND NOT EXISTS (
                     SELECT 1
                       FROM ai_conversation_handover_dispatch earlier
                      WHERE earlier.handover_id = candidate.handover_id
                        AND earlier.dispatch_sequence < candidate.dispatch_sequence
                        AND earlier.status NOT IN ('DELIVERED', 'SKIPPED')
                   )
                 ORDER BY candidate.created_at, candidate.handover_id, candidate.dispatch_sequence
                 LIMIT 1 FOR UPDATE
                """,
                this::mapDispatchRow,
                timestamp(now), timestamp(now)
            );
            if (candidates.isEmpty()) {
                return Optional.empty();
            }
            DispatchRow row = candidates.getFirst();
            int updated = jdbc.update(
                """
                UPDATE ai_conversation_handover_dispatch
                   SET status = 'PROCESSING', lease_owner = ?, lease_until = ?,
                       attempt_count = attempt_count + 1, updated_at = ?, version = version + 1
                 WHERE dispatch_id = ? AND version = ?
                """,
                workerId, timestamp(now.plus(leaseDuration)), timestamp(now), row.dispatchId(), row.version()
            );
            if (updated != 1) {
                return Optional.empty();
            }
            ConversationChannelDispatch dispatch = readJson(
                security.decrypt(row.protectedPayload()),
                ConversationChannelDispatch.class
            );
            return Optional.of(new ClaimedDispatch(dispatch, row.attemptCount() + 1));
        });
    }

    boolean completeDispatch(
        String dispatchId,
        ConversationChannelDeliveryResult result,
        int attempt,
        int maxAttempts
    ) {
        Instant now = clock.instant();
        if (result.accepted()) {
            int updated = jdbc.update(
                """
                UPDATE ai_conversation_handover_dispatch
                   SET status = 'DELIVERED', lease_owner = NULL, lease_until = NULL,
                       external_conversation_ref = ?, provider_message_ref = ?,
                       failure_code = NULL, failure_message = NULL,
                       completed_at = ?, updated_at = ?, version = version + 1
                 WHERE dispatch_id = ? AND status = 'PROCESSING' AND attempt_count = ?
                """,
                trim(result.externalConversationRef()), trim(result.providerMessageRef()),
                timestamp(now), timestamp(now), dispatchId, attempt
            );
            if (updated != 1) {
                return false;
            }
            if (StringUtils.hasText(result.externalConversationRef())) {
                jdbc.update(
                    """
                    UPDATE ai_conversation_handover
                       SET external_conversation_ref = COALESCE(external_conversation_ref, ?),
                           updated_at = ?, version = version + 1
                     WHERE handover_id = (
                       SELECT handover_id FROM ai_conversation_handover_dispatch WHERE dispatch_id = ?
                     )
                    """,
                    result.externalConversationRef().trim(), timestamp(now), dispatchId
                );
            }
            return true;
        }
        boolean retry = result.retryable() && attempt < maxAttempts;
        long delaySeconds = Math.min(300L, 1L << Math.min(attempt, 8));
        int updated = jdbc.update(
            """
            UPDATE ai_conversation_handover_dispatch
               SET status = ?, lease_owner = NULL, lease_until = NULL,
                   failure_code = ?, failure_message = ?, next_attempt_at = ?,
                   completed_at = ?, updated_at = ?, version = version + 1
             WHERE dispatch_id = ? AND status = 'PROCESSING' AND attempt_count = ?
            """,
            retry ? "PENDING" : "DEAD", trim(result.code()), bounded(result.message(), 1000),
            timestamp(now.plusSeconds(delaySeconds)), retry ? null : timestamp(now), timestamp(now),
            dispatchId, attempt
        );
        return updated == 1;
    }

    boolean skipProcessingDispatch(
        String dispatchId,
        int attempt,
        String reasonCode,
        String reasonMessage
    ) {
        Instant now = clock.instant();
        return jdbc.update(
            """
            UPDATE ai_conversation_handover_dispatch
               SET status = 'SKIPPED', lease_owner = NULL, lease_until = NULL,
                   failure_code = ?, failure_message = ?, completed_at = ?,
                   updated_at = ?, version = version + 1
             WHERE dispatch_id = ? AND status = 'PROCESSING' AND attempt_count = ?
            """,
            trim(reasonCode), bounded(reasonMessage, 1000), timestamp(now), timestamp(now),
            dispatchId, attempt
        ) == 1;
    }

    int skipDeadLifecycleStaleDispatches() {
        Instant now = clock.instant();
        return jdbc.update(
            """
            UPDATE ai_conversation_handover_dispatch
               SET status = 'SKIPPED', completed_at = COALESCE(completed_at, ?),
                   updated_at = ?, version = version + 1
             WHERE status = 'DEAD' AND failure_code = 'HANDOVER_LIFECYCLE_STALE'
            """,
            timestamp(now), timestamp(now)
        );
    }

    Map<String, Long> handoverStatusCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        jdbc.query(
            "SELECT status, COUNT(*) AS status_count FROM ai_conversation_handover GROUP BY status",
            (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                counts.put(rs.getString("status"), rs.getLong("status_count"))
        );
        return Map.copyOf(counts);
    }

    Map<String, Long> dispatchStatusCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        jdbc.query(
            "SELECT status, COUNT(*) AS status_count FROM ai_conversation_handover_dispatch GROUP BY status",
            (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                counts.put(rs.getString("status"), rs.getLong("status_count"))
        );
        return Map.copyOf(counts);
    }

    Instant oldestPendingDispatchAt() {
        List<Instant> rows = jdbc.query(
            "SELECT MIN(created_at) AS oldest_at FROM ai_conversation_handover_dispatch"
                + " WHERE status IN ('PENDING', 'PROCESSING')",
            (rs, row) -> nullableInstant(rs, "oldest_at")
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    List<DispatchAdminRecord> recentDispatches(int limit) {
        return jdbc.query(
            """
            SELECT dispatch_id, handover_id, dispatch_type, status,
                   attempt_count, failure_code, created_at, updated_at
              FROM ai_conversation_handover_dispatch
             ORDER BY updated_at DESC, dispatch_id DESC
             LIMIT ?
            """,
            (rs, row) -> new DispatchAdminRecord(
                rs.getString("dispatch_id"),
                rs.getString("handover_id"),
                rs.getString("dispatch_type"),
                rs.getString("status"),
                rs.getInt("attempt_count"),
                rs.getString("failure_code"),
                instant(rs, "created_at"),
                instant(rs, "updated_at")
            ),
            Math.max(1, Math.min(limit, 100))
        );
    }

    Optional<DispatchAdminRecord> findDispatch(String dispatchId) {
        return jdbc.query(
            """
            SELECT dispatch_id, handover_id, dispatch_type, status,
                   attempt_count, failure_code, created_at, updated_at
              FROM ai_conversation_handover_dispatch
             WHERE dispatch_id = ?
            """,
            (rs, row) -> new DispatchAdminRecord(
                rs.getString("dispatch_id"),
                rs.getString("handover_id"),
                rs.getString("dispatch_type"),
                rs.getString("status"),
                rs.getInt("attempt_count"),
                rs.getString("failure_code"),
                instant(rs, "created_at"),
                instant(rs, "updated_at")
            ),
            dispatchId
        ).stream().findFirst();
    }

    Optional<ConversationChannelDispatch> findDispatchPayload(String dispatchId) {
        return jdbc.query(
            "SELECT protected_payload FROM ai_conversation_handover_dispatch WHERE dispatch_id = ?",
            (rs, row) -> readJson(
                security.decrypt(rs.getString("protected_payload")),
                ConversationChannelDispatch.class
            ),
            dispatchId
        ).stream().findFirst();
    }

    boolean retryDeadDispatch(String dispatchId) {
        Instant now = clock.instant();
        return jdbc.update(
            """
            UPDATE ai_conversation_handover_dispatch
               SET status = 'PENDING', next_attempt_at = ?, lease_owner = NULL,
                   lease_until = NULL, failure_code = NULL, failure_message = NULL,
                   completed_at = NULL, updated_at = ?, version = version + 1
             WHERE dispatch_id = ? AND status = 'DEAD'
            """,
            timestamp(now), timestamp(now), dispatchId
        ) == 1;
    }

    boolean reactivateOpenDispatch(String handoverId) {
        Integer active = jdbc.queryForObject(
            "SELECT COUNT(*) FROM ai_conversation_handover_dispatch"
                + " WHERE handover_id = ? AND dispatch_type = 'OPEN_HANDOVER'"
                + " AND status IN ('PENDING', 'PROCESSING')",
            Integer.class,
            handoverId
        );
        if (active != null && active > 0) {
            return true;
        }
        List<String> dead = jdbc.query(
            "SELECT dispatch_id FROM ai_conversation_handover_dispatch"
                + " WHERE handover_id = ? AND dispatch_type = 'OPEN_HANDOVER' AND status = 'DEAD'"
                + " ORDER BY dispatch_sequence LIMIT 1 FOR UPDATE",
            (rs, row) -> rs.getString("dispatch_id"),
            handoverId
        );
        return !dead.isEmpty() && retryDeadDispatch(dead.getFirst());
    }

    boolean hasProcessingDispatch(String handoverId) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM ai_conversation_handover_dispatch"
                + " WHERE handover_id = ? AND status = 'PROCESSING'",
            Integer.class,
            handoverId
        );
        return count != null && count > 0;
    }

    List<HandoverRecord> pendingHandoversBefore(Instant cutoff, int limit) {
        return jdbc.query(
            handoverSelect()
                + " WHERE status = 'PENDING' AND updated_at <= ?"
                + " ORDER BY updated_at, handover_id LIMIT ?",
            this::mapHandover,
            timestamp(cutoff), Math.max(1, Math.min(limit, 200))
        );
    }

    boolean expirePendingHandover(String handoverId, Instant cutoff) {
        Instant now = clock.instant();
        return jdbc.update(
            """
            UPDATE ai_conversation_handover
               SET status = 'EXPIRED', updated_at = ?, version = version + 1
             WHERE handover_id = ? AND status = 'PENDING' AND updated_at <= ?
            """,
            timestamp(now), handoverId, timestamp(cutoff)
        ) == 1;
    }

    boolean reactivateExpiredHandover(String handoverId) {
        Instant now = clock.instant();
        return jdbc.update(
            """
            UPDATE ai_conversation_handover
               SET status = 'PENDING', updated_at = ?, version = version + 1
             WHERE handover_id = ? AND status = 'EXPIRED'
            """,
            timestamp(now), handoverId
        ) == 1;
    }

    List<HandoverRecord> retentionDueHandovers(Instant now, int limit) {
        return jdbc.query(
            handoverSelect()
                + " WHERE status NOT IN ('CANCELLED', 'CLOSED', 'RETURNED_TO_AI')"
                + " AND expires_at <= ? ORDER BY expires_at, handover_id LIMIT ?",
            this::mapHandover,
            timestamp(now), Math.max(1, Math.min(limit, 200))
        );
    }

    int purgeExpiredIntake(Instant now) {
        return jdbc.update(
            "DELETE FROM ai_conversation_handover_intake WHERE expires_at <= ?",
            timestamp(now)
        );
    }

    int purgeReplayRecordsBefore(Instant cutoff) {
        int nonces = jdbc.update(
            "DELETE FROM ai_conversation_channel_callback_nonce WHERE created_at < ?",
            timestamp(cutoff)
        );
        int receipts = jdbc.update(
            "DELETE FROM ai_conversation_channel_event_receipt WHERE created_at < ?",
            timestamp(cutoff)
        );
        return nonces + receipts;
    }

    void updateHandover(
        String handoverId,
        String status,
        String externalConversationRef,
        String externalActorRef,
        String safeActorDisplayName,
        boolean terminal
    ) {
        Instant now = clock.instant();
        jdbc.update(
            """
            UPDATE ai_conversation_handover
               SET status = ?,
                   external_conversation_ref = COALESCE(?, external_conversation_ref),
                   external_actor_ref = COALESCE(?, external_actor_ref),
                   safe_actor_display_name = COALESCE(?, safe_actor_display_name),
                   activated_at = CASE WHEN ? = 'ACTIVE' AND activated_at IS NULL THEN ? ELSE activated_at END,
                   completed_at = CASE WHEN ? THEN ? ELSE completed_at END,
                   updated_at = ?, version = version + 1
             WHERE handover_id = ?
            """,
            status, trim(externalConversationRef), trim(externalActorRef), bounded(safeActorDisplayName, 160),
            status, timestamp(now), terminal, timestamp(now), timestamp(now), handoverId
        );
    }

    boolean recordChannelEventReceipt(
        String eventId,
        String channelBindingId,
        String handoverId,
        String nonce,
        String payloadFingerprint,
        String outcome
    ) {
        Optional<ChannelEventReceipt> existing = findChannelEventReceipt(eventId);
        if (existing.isPresent()) {
            verifyChannelEventReplay(
                existing.get(), channelBindingId, handoverId, payloadFingerprint
            );
            reserveCallbackNonce(
                nonce, eventId, channelBindingId, handoverId, payloadFingerprint
            );
            return false;
        }

        reserveCallbackNonce(
            nonce, eventId, channelBindingId, handoverId, payloadFingerprint
        );
        try {
            jdbc.update(
                """
                INSERT INTO ai_conversation_channel_event_receipt (
                  event_id, channel_binding_id, handover_id,
                  payload_fingerprint, outcome, created_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
                eventId, channelBindingId, handoverId, payloadFingerprint, outcome,
                timestamp(clock.instant())
            );
            return true;
        } catch (DuplicateKeyException ignored) {
            ChannelEventReceipt concurrent = findChannelEventReceipt(eventId)
                .orElseThrow(() -> new IllegalStateException(
                    "The concurrent channel event receipt could not be loaded."
                ));
            verifyChannelEventReplay(
                concurrent, channelBindingId, handoverId, payloadFingerprint
            );
            return false;
        }
    }

    private Optional<ChannelEventReceipt> findChannelEventReceipt(String eventId) {
        return jdbc.query(
            """
            SELECT event_id, channel_binding_id, handover_id, payload_fingerprint
              FROM ai_conversation_channel_event_receipt
             WHERE event_id = ?
            """,
            (rs, row) -> new ChannelEventReceipt(
                rs.getString("event_id"),
                rs.getString("channel_binding_id"),
                rs.getString("handover_id"),
                rs.getString("payload_fingerprint")
            ),
            eventId
        ).stream().findFirst();
    }

    private void verifyChannelEventReplay(
        ChannelEventReceipt existing,
        String channelBindingId,
        String handoverId,
        String payloadFingerprint
    ) {
        if (!channelBindingId.equals(existing.channelBindingId())
            || !handoverId.equals(existing.handoverId())
            || !payloadFingerprint.equals(existing.payloadFingerprint())) {
            throw new ConversationHandoverException(
                "CHANNEL_EVENT_IDEMPOTENCY_CONFLICT",
                "The channel event identity was reused with different callback data.",
                org.springframework.http.HttpStatus.CONFLICT
            );
        }
    }

    private void reserveCallbackNonce(
        String nonce,
        String eventId,
        String channelBindingId,
        String handoverId,
        String payloadFingerprint
    ) {
        try {
            jdbc.update(
                """
                INSERT INTO ai_conversation_channel_callback_nonce (
                  nonce, event_id, channel_binding_id, handover_id,
                  payload_fingerprint, created_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
                nonce, eventId, channelBindingId, handoverId, payloadFingerprint,
                timestamp(clock.instant())
            );
        } catch (DuplicateKeyException ignored) {
            List<Boolean> exactReplays = jdbc.query(
                """
                SELECT event_id, channel_binding_id, handover_id, payload_fingerprint
                  FROM ai_conversation_channel_callback_nonce
                 WHERE nonce = ?
                """,
                (rs, row) -> eventId.equals(rs.getString("event_id"))
                    && channelBindingId.equals(rs.getString("channel_binding_id"))
                    && handoverId.equals(rs.getString("handover_id"))
                    && payloadFingerprint.equals(rs.getString("payload_fingerprint")),
                nonce
            );
            if (exactReplays.size() != 1 || !exactReplays.getFirst()) {
                throw new ConversationHandoverException(
                    "CHANNEL_CALLBACK_NONCE_REUSED",
                    "The channel callback nonce was already used by another event.",
                    org.springframework.http.HttpStatus.CONFLICT
                );
            }
        }
    }

    ConversationEventRecord appendEvent(
        ConversationScope scope,
        String eventId,
        String eventType,
        Map<String, Object> payload
    ) {
        Instant now = clock.instant();
        try {
            jdbc.update(
                """
                INSERT INTO ai_conversation_event (
                  event_id, tenant_id, deployment_id, conversation_id, owner_id,
                  event_type, protected_payload, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                eventId, scope.tenantId(), scope.deploymentId(), scope.conversationId(), scope.ownerId(),
                eventType, security.encrypt(writeJson(payload)), timestamp(now)
            );
        } catch (DuplicateKeyException ignored) {
            // Event IDs are idempotent across retries.
        }
        return findEvent(scope, eventId).orElseThrow(() -> new IllegalStateException("Conversation event was not stored."));
    }

    List<ConversationEventRecord> events(ConversationScope scope, String afterEventId, int limit) {
        Instant afterTime = null;
        if (StringUtils.hasText(afterEventId)) {
            ConversationEventRecord cursor = findEvent(scope, afterEventId).orElseThrow(() ->
                new IllegalArgumentException("Conversation event cursor is not available."));
            afterTime = cursor.createdAt();
        }
        String sql = "SELECT event_id, event_type, protected_payload, created_at FROM ai_conversation_event"
            + " WHERE tenant_id = ? AND deployment_id = ? AND conversation_id = ? AND owner_id = ?"
            + (afterTime == null ? "" : " AND (created_at > ? OR (created_at = ? AND event_id > ?))")
            + " ORDER BY created_at, event_id LIMIT ?";
        List<Object> args = new ArrayList<>(List.of(
            scope.tenantId(), scope.deploymentId(), scope.conversationId(), scope.ownerId()
        ));
        if (afterTime != null) {
            args.add(timestamp(afterTime));
            args.add(timestamp(afterTime));
            args.add(afterEventId);
        }
        args.add(Math.max(1, Math.min(limit, 200)));
        return List.copyOf(jdbc.query(sql, this::mapEvent, args.toArray()));
    }

    private Optional<ConversationEventRecord> findEvent(ConversationScope scope, String eventId) {
        return jdbc.query(
            """
            SELECT event_id, event_type, protected_payload, created_at
              FROM ai_conversation_event
             WHERE event_id = ? AND tenant_id = ? AND deployment_id = ?
               AND conversation_id = ? AND owner_id = ?
            """,
            this::mapEvent,
            eventId, scope.tenantId(), scope.deploymentId(), scope.conversationId(), scope.ownerId()
        ).stream().findFirst();
    }

    private void verifyDispatchReplay(
        ConversationChannelDispatch dispatch,
        String idempotencyKey
    ) {
        List<Boolean> matches = jdbc.query(
            """
            SELECT dispatch_id, handover_id, idempotency_key, protected_payload
              FROM ai_conversation_handover_dispatch
             WHERE dispatch_id = ? OR idempotency_key = ?
            """,
            (rs, row) -> {
                ConversationChannelDispatch stored = readJson(
                    security.decrypt(rs.getString("protected_payload")),
                    ConversationChannelDispatch.class
                );
                if (!dispatch.dispatchId().equals(rs.getString("dispatch_id"))
                    || !dispatch.handoverId().equals(rs.getString("handover_id"))
                    || !idempotencyKey.equals(rs.getString("idempotency_key"))
                    || !sameDispatchPayload(dispatch, stored)) {
                    throw new ConversationHandoverException(
                        "DISPATCH_IDEMPOTENCY_CONFLICT",
                        "The handover dispatch identity was reused with different data.",
                        org.springframework.http.HttpStatus.CONFLICT
                    );
                }
                return Boolean.TRUE;
            },
            dispatch.dispatchId(), idempotencyKey
        );
        if (matches.size() != 1) {
            throw new ConversationHandoverException(
                "DISPATCH_IDEMPOTENCY_CONFLICT",
                "The existing handover dispatch could not be verified.",
                org.springframework.http.HttpStatus.CONFLICT
            );
        }
    }

    private static boolean sameDispatchPayload(
        ConversationChannelDispatch expected,
        ConversationChannelDispatch stored
    ) {
        return expected.dispatchId().equals(stored.dispatchId())
            && expected.type() == stored.type()
            && expected.scope().equals(stored.scope())
            && expected.handoverId().equals(stored.handoverId())
            && expected.channelBindingId().equals(stored.channelBindingId())
            && expected.expectedControlVersion() == stored.expectedControlVersion()
            && java.util.Objects.equals(expected.messageId(), stored.messageId())
            && java.util.Objects.equals(expected.text(), stored.text())
            && expected.attachmentRefs().equals(stored.attachmentRefs())
            && expected.transcript().equals(stored.transcript())
            && expected.consentedIntake().equals(stored.consentedIntake());
    }

    private String handoverSelect() {
        return "SELECT handover_id, tenant_id, deployment_id, conversation_id, owner_id,"
            + " channel_binding_id, request_idempotency_key, reason_code, status,"
            + " external_conversation_ref, external_actor_ref, safe_actor_display_name,"
            + " created_at, activated_at, completed_at, updated_at, expires_at, version"
            + " FROM ai_conversation_handover";
    }

    private HandoverRecord mapHandover(ResultSet rs, int row) throws SQLException {
        ConversationScope scope = new ConversationScope(
            rs.getString("tenant_id"), rs.getString("deployment_id"),
            rs.getString("conversation_id"), rs.getString("owner_id")
        );
        return new HandoverRecord(
            rs.getString("handover_id"), scope, rs.getString("channel_binding_id"),
            rs.getString("request_idempotency_key"), rs.getString("reason_code"), rs.getString("status"),
            rs.getString("external_conversation_ref"), rs.getString("external_actor_ref"),
            rs.getString("safe_actor_display_name"), instant(rs, "created_at"),
            nullableInstant(rs, "activated_at"), nullableInstant(rs, "completed_at"),
            instant(rs, "updated_at"), instant(rs, "expires_at"), rs.getLong("version")
        );
    }

    private DispatchRow mapDispatchRow(ResultSet rs, int row) throws SQLException {
        return new DispatchRow(
            rs.getString("dispatch_id"), rs.getString("handover_id"),
            ConversationChannelDispatchType.valueOf(rs.getString("dispatch_type")),
            rs.getString("protected_payload"), rs.getInt("attempt_count"), rs.getLong("version")
        );
    }

    private ConversationEventRecord mapEvent(ResultSet rs, int row) throws SQLException {
        return new ConversationEventRecord(
            rs.getString("event_id"), rs.getString("event_type"),
            readJson(security.decrypt(rs.getString("protected_payload")), new TypeReference<Map<String, Object>>() {}),
            instant(rs, "created_at")
        );
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Conversation handover payload is not JSON serializable.", exception);
        }
    }

    private <T> T readJson(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored conversation handover payload is invalid.", exception);
        }
    }

    private <T> T readJson(String value, TypeReference<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored conversation handover payload is invalid.", exception);
        }
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? Instant.EPOCH : value.toInstant();
    }

    private static Instant nullableInstant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String bounded(String value, int max) {
        String normalized = trim(value);
        return normalized == null || normalized.length() <= max ? normalized : normalized.substring(0, max);
    }

    record HandoverRecord(
        String handoverId,
        ConversationScope scope,
        String channelBindingId,
        String requestIdempotencyKey,
        String reasonCode,
        String status,
        String externalConversationRef,
        String externalActorRef,
        String safeActorDisplayName,
        Instant createdAt,
        Instant activatedAt,
        Instant completedAt,
        Instant updatedAt,
        Instant expiresAt,
        long version
    ) {}

    record IntakeValue(
        String key,
        String value,
        String purpose,
        boolean consented,
        boolean verified
    ) {}

    record ConversationEventRecord(
        String eventId,
        String type,
        Map<String, Object> payload,
        Instant createdAt
    ) {}

    private record ChannelEventReceipt(
        String eventId,
        String channelBindingId,
        String handoverId,
        String payloadFingerprint
    ) {}

    record ClaimedDispatch(ConversationChannelDispatch dispatch, int attempt) {}

    record DispatchAdminRecord(
        String dispatchId,
        String handoverId,
        String dispatchType,
        String status,
        int attemptCount,
        String failureCode,
        Instant createdAt,
        Instant updatedAt
    ) {}

    record InteractionRecord(
        String userMessageId,
        String requestFingerprint,
        String status,
        Integer responseHttpStatus,
        String responseJson,
        String failureCode,
        Instant createdAt,
        Instant updatedAt
    ) {}

    record InteractionClaim(InteractionRecord record, boolean claimed) {}

    record CanonicalConversationRecord(
        String conversationId,
        String controlState,
        Instant createdAt,
        Instant lastInteractionAt,
        int userMessageCount
    ) {}

    private record DispatchRow(
        String dispatchId,
        String handoverId,
        ConversationChannelDispatchType type,
        String protectedPayload,
        int attemptCount,
        long version
    ) {}
}
