package com.ai.fabric.runtime.handover;

import ai.fabric.chat.control.ConversationControlState;
import ai.fabric.chat.control.HandoverIntakeSchema;
import com.ai.fabric.runtime.web.dto.ChatQueryResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class ConversationHandoverContracts {

    private ConversationHandoverContracts() {}

    public record HandoverRequest(
        String reasonCode,
        boolean continueAnonymously,
        Map<String, String> intake,
        List<String> consentedFields
    ) {}

    public record HandoverAdmission(
        String outcome,
        ConversationView conversation,
        HandoverIntakeSchema intakeSchema,
        String code,
        String message
    ) {}

    public record ConversationView(
        String conversationId,
        ConversationControlState controlState,
        long controlVersion,
        String handoverId,
        String channelBindingId,
        String handoverStatus,
        String safeHumanDisplayName,
        List<PublicConversationMessage> messages,
        List<ConversationEventView> events,
        Instant updatedAt
    ) {}

    public record PublicConversationMessage(
        String messageId,
        long sequence,
        String role,
        String participantDisplayName,
        String content,
        String clientMessageId,
        Map<String, Object> metadata,
        Instant createdAt
    ) {}

    public record CanonicalConversationSummary(
        String conversationId,
        ConversationControlState controlState,
        Instant createdAt,
        Instant lastInteractionAt,
        int userMessageCount
    ) {}

    public record ConversationEventView(
        String eventId,
        String type,
        Map<String, Object> payload,
        Instant createdAt
    ) {}

    public record HumanMessageAcceptance(
        String outcome,
        String messageId,
        ConversationView conversation
    ) {}

    public record AiInteractionAdmission(
        String outcome,
        Integer responseHttpStatus,
        ChatQueryResponse response,
        String failureCode
    ) {}

    public record HandoverMaintenanceResult(
        int queueExpired,
        int retentionClosed,
        int intakeValuesPurged,
        int replayRecordsPurged
    ) {}

    public record DispatchClaimRequest(String workerId, long leaseSeconds) {}

    public record DispatchClaim(
        String dispatchId,
        int attempt,
        Object dispatch
    ) {}

    public record DispatchOutcome(
        boolean accepted,
        String externalConversationRef,
        String providerMessageRef,
        String code,
        String message,
        boolean retryable
    ) {}

    public record HandoverOperationsOverview(
        boolean enabled,
        String channelBindingId,
        Map<String, Long> handoverCounts,
        Map<String, Long> dispatchCounts,
        Instant oldestPendingDispatchAt,
        List<DispatchOperationView> recentDispatches
    ) {}

    public record DispatchOperationView(
        String dispatchId,
        String handoverId,
        String dispatchType,
        String status,
        int attemptCount,
        String failureCode,
        Instant createdAt,
        Instant updatedAt,
        boolean retryEligible
    ) {}
}
