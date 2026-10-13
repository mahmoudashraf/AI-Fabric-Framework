package com.loomai.inbox.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class InboxContracts {

    private InboxContracts() {}

    public record DispatchResponse(
        boolean accepted,
        String externalConversationRef,
        String providerMessageRef,
        String code,
        String message,
        boolean retryable
    ) {}

    public record OperatorIdentity(
        String operatorId,
        String displayName,
        List<String> bindingIds
    ) {}

    public record ConversationSummary(
        String handoverId,
        String externalConversationRef,
        String status,
        String safeCustomerLabel,
        String claimedBy,
        String claimedDisplayName,
        int unreadCount,
        Instant createdAt,
        Instant updatedAt
    ) {}

    public record ConversationDetail(
        ConversationSummary conversation,
        Map<String, Object> consentedIntake,
        List<MessageView> messages
    ) {}

    public record MessageView(
        String messageId,
        String participantRole,
        String actorRef,
        String safeActorDisplayName,
        String content,
        List<String> attachmentRefs,
        Instant createdAt
    ) {}

    public record ReplyRequest(String text, String idempotencyKey) {}

    public record OperatorCommandRequest(String idempotencyKey) {}

    public record OperatorCommandResult(
        boolean accepted,
        String outcome,
        ConversationDetail conversation
    ) {}

    public record CallbackClaim(
        String callbackId,
        String handoverId,
        String eventType,
        String payload,
        int attempt,
        String leaseOwner
    ) {}

    public record CallbackOperationView(
        String callbackId,
        String handoverId,
        String eventType,
        String status,
        int attemptCount,
        String failureCode,
        Instant createdAt,
        Instant updatedAt,
        boolean retryEligible
    ) {}

    public record CallbackRecoveryResult(
        boolean accepted,
        String outcome,
        List<CallbackOperationView> callbacks
    ) {}
}
