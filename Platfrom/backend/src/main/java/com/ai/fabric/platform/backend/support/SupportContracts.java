package com.ai.fabric.platform.backend.support;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class SupportContracts {
    private SupportContracts() { }

    public enum CaseStatus { OPEN, RESOLVED, CANCELLED }
    public enum ResourceType { DEPLOYMENT, ENGAGE_APP, CONNECTION }
    public enum DeliveryStatus { PENDING, UNKNOWN, USER_MESSAGE_PERSISTED, COMPLETED, FAILED }
    public enum Availability { CURRENT, INCOMPLETE, UNAVAILABLE, NOT_APPLICABLE }

    public record ResourceRef(@NotNull ResourceType type, @NotBlank @Size(max = 128) String id) { }
    public record CreateCase(@NotBlank @Size(max = 240) String summary,
                             @Size(max = 12000) String details, @Size(max = 80) String topic,
                             @Size(max = 8) List<@Valid ResourceRef> resources, boolean diagnosticConsent) { }
    public record UpdateCase(@Size(max = 80) String topic,
                             @NotNull @Size(max = 8) List<@Valid ResourceRef> resources,
                             @PositiveOrZero long expectedRevision) { }
    public record ConsentChange(boolean granted, @PositiveOrZero long expectedVersion) { }
    public record StatusChange(@PositiveOrZero long expectedRevision) { }

    /** Immutable identity, never inferred from an email, form field or model parameter. */
    public record Requester(String customerId, String platformUserId, String issuer, String subject) { }
    public record RuntimeBinding(String customerId, String tenantId, String deploymentId, String targetProfileId,
                                 String issuer, String audience) { }
    public record Consent(boolean granted, long version, Instant changedAt, Instant revokedAt) { }
    public record Episode(String id, String caseId, int number, RuntimeBinding binding, String conversationId,
                          boolean terminal, Instant createdAt) { }
    public record CaseRecord(String id, String reference, Requester requester, String runtimePrincipal,
                             String submissionKey, String submissionHash, String summary, String details, String topic,
                             List<ResourceRef> resources, String initialMessage, String initialMessageKey,
                             DeliveryStatus deliveryStatus, Instant deliveryObservedAt, CaseStatus status,
                             Consent consent, long revision, Instant createdAt, Instant updatedAt, Instant closedAt) { }
    public record EpisodeSummary(String id, int number, String conversationId, boolean terminal, Instant createdAt) { }
    public record CaseView(String id, String reference, String summary, String details, String topic,
                           List<ResourceRef> resources, CaseStatus status, Consent consent, long revision,
                           DeliveryStatus initialMessageDelivery, Instant deliveryObservedAt,
                           List<EpisodeSummary> episodes, Instant createdAt, Instant updatedAt, Instant closedAt) { }
    /** Server-only adapter contract; this record must not be serialized into a workspace manifest. */
    public record ConversationAccess(CaseRecord supportCase, Episode episode) { }
    /** Frozen server-only private-adapter contract. customerId is the fixed support deployment owner. */
    public record OwnedCase(String caseId, String reference, String deploymentId, String targetProfileId,
                             String customerId, String tenantId, String principalId, String conversationId,
                             String episodeId, int episodeNumber, boolean conversationTerminal,
                             String initialMessageKey, String initialMessageBody, DeliveryStatus initialMessageDelivery,
                             CaseStatus status, String assertionIssuer, String assertionAudience) { }
    public record InitialMessage(String caseId, String episodeId, String conversationId, String body,
                                 String idempotencyKey, DeliveryStatus observation) { }
    public record ResourceDiagnostic(ResourceRef resource, Availability availability, Map<String, Object> facts) { }
    public record CaseDiagnostic(String reference, String summary, String topic, CaseStatus status,
                                 Consent consent, List<ResourceRef> resources) { }
    public record DiagnosticResult(String action, Instant observedAt, Availability availability,
                                    CaseDiagnostic supportCase, List<ResourceDiagnostic> resources) { }
}
