package com.loomai.inbox.service;

import static com.loomai.inbox.model.InboxContracts.*;

import ai.fabric.chat.channel.ConversationChannelDispatch;
import ai.fabric.chat.channel.ConversationChannelDispatchType;
import ai.fabric.chat.channel.ConversationChannelEvent;
import ai.fabric.chat.channel.ConversationChannelEventType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loomai.inbox.config.BasicInboxProperties;
import com.loomai.inbox.persistence.BasicInboxRepository;
import com.loomai.inbox.persistence.BasicInboxRepository.ConversationRecord;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class BasicInboxService {

    private final BasicInboxProperties properties;
    private final BasicInboxRepository repository;
    private final InboxSecurityService security;
    private final ObjectMapper objectMapper;

    public BasicInboxService(
        BasicInboxProperties properties,
        BasicInboxRepository repository,
        InboxSecurityService security,
        ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.repository = repository;
        this.security = security;
        this.objectMapper = objectMapper;
    }

    public DispatchResponse dispatch(ConversationChannelDispatch dispatch, String rawBody) {
        if (dispatch == null || !properties.getBindingId().equals(dispatch.channelBindingId())) {
            throw new BasicInboxRepository.InboxConflictException(
                "CHANNEL_BINDING_MISMATCH",
                "Dispatch does not target this inbox binding."
            );
        }
        if (!properties.getTenantId().equals(dispatch.scope().tenantId())
            || !properties.getDeploymentId().equals(dispatch.scope().deploymentId())) {
            throw new BasicInboxRepository.InboxConflictException(
                "DEPLOYMENT_SCOPE_MISMATCH",
                "Dispatch does not target the tenant and deployment assigned to this inbox instance."
            );
        }
        String payloadHash = security.sha256(rawBody);
        return repository.transactional(() -> repository.dispatchReceipt(dispatch.dispatchId(), payloadHash)
            .orElseGet(() -> {
                DispatchResponse response = applyDispatch(dispatch);
                if (!response.retryable()) {
                    repository.saveDispatchReceipt(dispatch.dispatchId(), payloadHash, response);
                }
                return response;
            }));
    }

    public List<ConversationSummary> queue(OperatorIdentity operator, String status, int limit) {
        security.requireBinding(operator, properties.getBindingId());
        return repository.listQueue(properties.getBindingId(), status, limit);
    }

    public ConversationDetail detail(OperatorIdentity operator, String handoverId) {
        ConversationRecord conversation = repository.requireConversation(handoverId);
        security.requireBinding(operator, conversation.bindingId());
        return repository.detail(handoverId);
    }

    public OperatorCommandResult claim(
        OperatorIdentity operator,
        String handoverId,
        String idempotencyKey
    ) {
        requireKey(idempotencyKey);
        return repository.transactional(() -> {
            ConversationRecord before = repository.requireConversation(handoverId);
            security.requireBinding(operator, before.bindingId());
            String callbackId = id(
                "claim",
                handoverId,
                operator.operatorId() + "|" + idempotencyKey.trim()
            );
            if (repository.callbackExists(callbackId, handoverId, ConversationChannelEventType.HANDOVER_CLAIMED.name())) {
                return new OperatorCommandResult(true, "CLAIM_REPLAYED", repository.detail(handoverId));
            }
            if ("ACTIVE".equals(before.status()) && operator.operatorId().equals(before.claimedBy())) {
                return new OperatorCommandResult(true, "ALREADY_CLAIMED", repository.detail(handoverId));
            }
            if ("CLAIM_PENDING".equals(before.status())
                && operator.operatorId().equals(before.claimedBy())) {
                return new OperatorCommandResult(
                    true,
                    "CLAIM_ALREADY_PENDING",
                    repository.detail(handoverId)
                );
            }
            if (!repository.claim(handoverId, operator.operatorId(), operator.displayName())) {
                throw new BasicInboxRepository.InboxConflictException(
                    "INBOX_ALREADY_CLAIMED",
                    "This conversation is already claimed or no longer waiting."
                );
            }
            ConversationRecord claimed = repository.requireConversation(handoverId);
            enqueueEvent(
                claimed,
                callbackId,
                ConversationChannelEventType.HANDOVER_CLAIMED,
                operator,
                null,
                null
            );
            return new OperatorCommandResult(true, "CLAIM_QUEUED", repository.detail(handoverId));
        });
    }

    public OperatorCommandResult reply(
        OperatorIdentity operator,
        String handoverId,
        ReplyRequest request
    ) {
        if (request == null || !StringUtils.hasText(request.text()) || request.text().trim().length() > 20_000) {
            throw new BasicInboxRepository.InboxConflictException(
                "HUMAN_MESSAGE_INVALID",
                "Reply text must contain between 1 and 20000 characters."
            );
        }
        String key = requireKey(request.idempotencyKey());
        return repository.transactional(() -> {
            ConversationRecord conversation = repository.requireConversation(handoverId);
            security.requireBinding(operator, conversation.bindingId());
            String actorKey = operator.operatorId() + "|" + key;
            String messageId = id("human-message", handoverId, actorKey);
            String callbackId = id("callback-message", handoverId, actorKey);
            if (repository.humanMessageExists(
                messageId,
                handoverId,
                operator.operatorId(),
                operator.displayName(),
                request.text().trim()
            )) {
                repository.callbackExists(
                    callbackId,
                    handoverId,
                    ConversationChannelEventType.HUMAN_MESSAGE.name()
                );
                return new OperatorCommandResult(true, "MESSAGE_REPLAYED", repository.detail(handoverId));
            }
            repository.addHumanMessage(
                handoverId,
                messageId,
                operator.operatorId(),
                operator.displayName(),
                request.text().trim()
            );
            enqueueEvent(
                repository.requireConversation(handoverId),
                callbackId,
                ConversationChannelEventType.HUMAN_MESSAGE,
                operator,
                messageId,
                request.text().trim()
            );
            return new OperatorCommandResult(true, "MESSAGE_QUEUED", repository.detail(handoverId));
        });
    }

    public OperatorCommandResult returnToAi(
        OperatorIdentity operator,
        String handoverId,
        String idempotencyKey
    ) {
        return transition(operator, handoverId, idempotencyKey, "RETURN_PENDING", ConversationChannelEventType.HANDOVER_RELEASED);
    }

    public OperatorCommandResult close(
        OperatorIdentity operator,
        String handoverId,
        String idempotencyKey
    ) {
        return transition(operator, handoverId, idempotencyKey, "CLOSE_PENDING", ConversationChannelEventType.HANDOVER_CLOSED);
    }

    public List<CallbackOperationView> callbackOperations(
        OperatorIdentity operator,
        int limit
    ) {
        security.requireBinding(operator, properties.getBindingId());
        return repository.callbackOperations(properties.getBindingId(), limit);
    }

    public CallbackRecoveryResult retryCallback(
        OperatorIdentity operator,
        String callbackId,
        String idempotencyKey
    ) {
        security.requireBinding(operator, properties.getBindingId());
        String key = requireKey(idempotencyKey);
        String operationId = id(
            "retry-callback",
            callbackId,
            operator.operatorId() + "|" + key
        );
        return repository.transactional(() -> {
            if (repository.operatorOperationExists(
                operationId,
                properties.getBindingId(),
                operator.operatorId(),
                "RETRY_CALLBACK",
                callbackId
            )) {
                return new CallbackRecoveryResult(
                    true,
                    "CALLBACK_RETRY_REPLAYED",
                    repository.callbackOperations(properties.getBindingId(), 100)
                );
            }
            if (!repository.retryDeadCallback(properties.getBindingId(), callbackId)) {
                throw new BasicInboxRepository.InboxConflictException(
                    "CALLBACK_RETRY_NOT_ALLOWED",
                    "Only a dead callback for this inbox binding can be retried."
                );
            }
            repository.recordOperatorOperation(
                operationId,
                properties.getBindingId(),
                operator.operatorId(),
                "RETRY_CALLBACK",
                callbackId
            );
            return new CallbackRecoveryResult(
                true,
                "CALLBACK_RETRY_QUEUED",
                repository.callbackOperations(properties.getBindingId(), 100)
            );
        });
    }

    private OperatorCommandResult transition(
        OperatorIdentity operator,
        String handoverId,
        String idempotencyKey,
        String pendingState,
        ConversationChannelEventType type
    ) {
        String key = requireKey(idempotencyKey);
        return repository.transactional(() -> {
            ConversationRecord conversation = repository.requireConversation(handoverId);
            security.requireBinding(operator, conversation.bindingId());
            String callbackId = id(
                type.name().toLowerCase(),
                handoverId,
                operator.operatorId() + "|" + key
            );
            if (repository.callbackExists(callbackId, handoverId, type.name())) {
                return new OperatorCommandResult(true, type.name() + "_REPLAYED", repository.detail(handoverId));
            }
            repository.markTransitionPending(handoverId, operator.operatorId(), pendingState);
            enqueueEvent(
                repository.requireConversation(handoverId),
                callbackId,
                type,
                operator,
                null,
                null
            );
            return new OperatorCommandResult(true, type.name() + "_QUEUED", repository.detail(handoverId));
        });
    }

    private DispatchResponse applyDispatch(ConversationChannelDispatch dispatch) {
        String externalRef = "loomai-inbox:" + dispatch.handoverId();
        switch (dispatch.type()) {
            case OPEN_HANDOVER -> repository.open(dispatch, externalRef);
            case USER_MESSAGE -> repository.acceptUserMessage(dispatch);
            case CANCEL_HANDOVER -> repository.applyControlDispatch(dispatch);
            case RETURN_TO_AI, CLOSE -> {
                if (repository.hasIncompleteCallbacks(dispatch.handoverId())) {
                    return new DispatchResponse(
                        false,
                        externalRef,
                        dispatch.messageId(),
                        "PROVIDER_CALLBACKS_PENDING",
                        "Earlier human-channel messages are still being delivered.",
                        true
                    );
                }
                repository.applyControlDispatch(dispatch);
            }
        }
        return new DispatchResponse(
            true,
            externalRef,
            dispatch.messageId(),
            "ACCEPTED",
            "Dispatch accepted by Basic LoomAI Inbox.",
            false
        );
    }

    private void enqueueEvent(
        ConversationRecord conversation,
        String eventId,
        ConversationChannelEventType type,
        OperatorIdentity operator,
        String messageId,
        String text
    ) {
        ConversationChannelEvent event = new ConversationChannelEvent(
            eventId,
            type,
            conversation.bindingId(),
            conversation.handoverId(),
            conversation.externalConversationRef(),
            operator.operatorId(),
            operator.displayName(),
            messageId,
            text,
            conversation.expectedControlVersion(),
            Map.of("provider", "basic-loomai-inbox"),
            Instant.now()
        );
        repository.enqueueCallback(eventId, conversation.handoverId(), type.name(), write(event));
    }

    private String requireKey(String value) {
        if (!StringUtils.hasText(value) || value.trim().length() > 200) {
            throw new BasicInboxRepository.InboxConflictException(
                "IDEMPOTENCY_KEY_REQUIRED",
                "A bounded idempotency key is required."
            );
        }
        return value.trim();
    }

    private String id(String prefix, String handoverId, String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest((prefix + "|" + handoverId + "|" + key).getBytes(StandardCharsets.UTF_8));
            return prefix + "-" + HexFormat.of().formatHex(digest).substring(0, 32);
        } catch (Exception exception) {
            throw new IllegalStateException("Inbox identifier generation failed.", exception);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Inbox event serialization failed.", exception);
        }
    }
}
