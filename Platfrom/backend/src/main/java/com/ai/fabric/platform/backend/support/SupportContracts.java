package com.ai.fabric.platform.backend.support;

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
    public record ConsentChange(boolean granted, @PositiveOrZero long expectedVersion) { }

    /** Immutable identity, never inferred from an email, form field or model parameter. */
    public record Requester(String customerId, String platformUserId, String issuer, String subject) { }
    public record RuntimeBinding(String customerId, String tenantId, String deploymentId, String targetProfileId,
                                 String issuer, String audience) { }
    public record Consent(boolean granted, long version, Instant changedAt, Instant revokedAt) { }
    /** Frozen server-only private-adapter contract. customerId is the fixed support deployment owner. */
    public record OwnedCase(String caseId, String reference, String deploymentId, String targetProfileId,
                             String customerId, String tenantId, String principalId, String conversationId,
                             String episodeId, int episodeNumber, boolean conversationTerminal,
                             String initialMessageKey, String initialMessageBody, DeliveryStatus initialMessageDelivery,
                             CaseStatus status, String assertionIssuer, String assertionAudience) { }
    public record ResourceDiagnostic(ResourceRef resource, Availability availability, Map<String, Object> facts) { }
    public record CaseDiagnostic(String reference, String summary, String topic, CaseStatus status,
                                 Consent consent, List<ResourceRef> resources) { }
    public record DiagnosticResult(String action, Instant observedAt, Availability availability,
                                    CaseDiagnostic supportCase, List<ResourceDiagnostic> resources) { }
}
