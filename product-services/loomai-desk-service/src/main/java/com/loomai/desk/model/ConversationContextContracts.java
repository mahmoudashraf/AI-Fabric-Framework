package com.loomai.desk.model;

import java.time.Instant;
import java.util.List;

/** Typed external projections only; Desk never stores the external case or its transcript. */
public final class ConversationContextContracts {
  private ConversationContextContracts() {}

  public enum Operation {
    LOOKUP,
    RESOLVE
  }

  public enum State {
    NOT_CONFIGURED,
    NOT_LINKED,
    AVAILABLE
  }

  public enum ConsentStatus {
    GIVEN,
    DECLINED,
    REVOKED,
    NOT_REQUESTED
  }

  public record ProviderInput(String providerUrl, String serviceKey, boolean resolveEnabled) {
    @Override
    public String toString() {
      return "ProviderInput[providerUrl=" + providerUrl + "]";
    }
  }

  public record ProviderView(boolean configured, String providerUrl, boolean resolveEnabled) {}

  public record Provider(String providerUrl, String serviceKey, boolean resolveEnabled) {
    @Override
    public String toString() {
      return "Provider[providerUrl=" + providerUrl + "]";
    }
  }

  public record Scope(
      String bindingId,
      String customerId,
      String tenantId,
      String deploymentId,
      String targetProfileId,
      String handoverId,
      String conversationId,
      String ownerRef,
      String externalConversationRef) {}

  public record Actor(String issuer, String subject, String agentId) {}

  public record ResolveInput(String idempotencyKey, Long expectedVersion) {}

  public record ProviderRequest(
      int schemaVersion, Operation operation, Scope scope, Actor actor, ResolveInput command) {}

  public record Consent(ConsentStatus status, String summary, Instant updatedAt) {}

  public record Observation(String label, String status, String summary, Instant observedAt) {}

  public record ResolveAction(String label, String confirmation) {}

  public record ExternalContext(
      String reference,
      String title,
      String status,
      Long version,
      String accountLabel,
      List<String> resourceLabels,
      Consent consent,
      List<Observation> observations,
      ResolveAction resolveAction) {}

  public record ProviderResponse(
      int schemaVersion, Scope scope, State state, ExternalContext context) {}

  public record ContextView(State state, ExternalContext context) {}
}
