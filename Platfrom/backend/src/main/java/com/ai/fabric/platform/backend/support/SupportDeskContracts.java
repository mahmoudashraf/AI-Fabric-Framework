package com.ai.fabric.platform.backend.support;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

/** Wire DTOs for Desk's EXTERNAL_CONVERSATION_CONTEXT.md, not a new case protocol. */
public final class SupportDeskContracts {
    private SupportDeskContracts() { }
    public enum Operation { LOOKUP, RESOLVE }
    public enum State { NOT_LINKED, AVAILABLE }
    public record Scope(@NotBlank @Size(max = 64) String bindingId,
                         @NotBlank @Size(max = 64) String customerId,
                         @NotBlank @Size(max = 64) String tenantId,
                         @NotBlank @Size(max = 64) String deploymentId,
                         @NotBlank @Size(max = 128) String targetProfileId,
                         @NotBlank @Size(max = 128) String handoverId,
                         @NotBlank @Size(max = 96) String conversationId,
                         @NotBlank @Size(max = 96) String ownerRef,
                         @NotBlank @Size(max = 160) String externalConversationRef) { }
    public record Actor(@NotBlank @Size(max = 512) String issuer,
                         @NotBlank @Size(max = 255) String subject,
                         @NotBlank @Size(max = 128) String agentId) { }
    public record Command(@NotBlank @Size(max = 128) String idempotencyKey,
                           @NotNull @PositiveOrZero Long expectedVersion) { }
    public record Request(@Min(1) @Max(1) int schemaVersion, @NotNull Operation operation,
                           @NotNull @Valid Scope scope, @NotNull @Valid Actor actor, @Valid Command command) { }
    public record Consent(String status, String summary, Instant updatedAt) { }
    public record Observation(String label, String status, String summary, Instant observedAt) { }
    public record ResolveAction(String label, String confirmation) { }
    public record Context(String reference, String title, String status, Long version, String accountLabel,
                           List<String> resourceLabels, Consent consent, List<Observation> observations,
                           ResolveAction resolveAction) { }
    public record Response(int schemaVersion, Scope scope, State state, Context context) { }
    public record StaffIntake(String caseId, String reference, String summary, String details, String status,
                               long version, String accountLabel, List<String> resourceLabels, Consent consent,
                               String initialMessageDelivery, Instant createdAt, Instant updatedAt) { }
    public record VerifiedAgent(String issuer, String subject, String agentId) { }
}
