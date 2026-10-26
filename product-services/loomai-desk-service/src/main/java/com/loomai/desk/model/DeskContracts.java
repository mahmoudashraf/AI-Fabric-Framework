package com.loomai.desk.model;

import java.util.List;
import java.util.Set;

public final class DeskContracts {
  private DeskContracts() {}

  public enum Role {
    OWNER,
    SUPPORT,
    REVIEWER
  }

  public record VerifiedIdentity(
      String issuer,
      String subject,
      String email,
      String displayName,
      boolean emailVerified,
      String accessToken) {
    @Override
    public String toString() {
      return "VerifiedIdentity[issuer=" + issuer + ",subject=" + subject + "]";
    }
  }

  public record Operator(String id, String displayName, String email, String agentReference) {}

  public record RegistrationInput(String displayName) {}

  public record OwnerInput(String issuer, String subject, String email, String displayName) {}

  public record WorkspaceInput(String name, List<OwnerInput> owners) {}

  public record BindingInput(
      String customerId,
      String tenantId,
      String deploymentId,
      String targetProfileId,
      String deploymentName,
      String environment,
      String callbackUrl,
      String providerApiKey,
      String callbackSigningSecret,
      String reviewBridgeUrl,
      String reviewBridgeKey,
      boolean enabled) {
    @Override
    public String toString() {
      return "BindingInput[customerId=" + customerId + ",deploymentId=" + deploymentId + "]";
    }
  }

  public record BindingView(
      String bindingId,
      String customerId,
      String deploymentId,
      String targetProfileId,
      String deploymentName,
      String environment,
      List<Role> roles) {}

  public record Binding(
      String bindingId,
      String customerId,
      String tenantId,
      String deploymentId,
      String targetProfileId,
      String deploymentName,
      String environment,
      String callbackUrl,
      String providerApiKey,
      String callbackSigningSecret,
      String reviewBridgeUrl,
      String reviewBridgeKey,
      boolean enabled) {
    @Override
    public String toString() {
      return "Binding[bindingId=" + bindingId + ",customerId=" + customerId + "]";
    }

    public BindingView view(List<Role> roles) {
      return new BindingView(
          bindingId, customerId, deploymentId, targetProfileId, deploymentName, environment, roles);
    }
  }

  public record AssignmentInput(String email, String agentReference, Set<Role> roles) {}

  public record AgentView(
      String agentId, String agentReference, String displayName, String email, List<Role> roles) {}

  public record SessionView(
      boolean authenticated,
      Operator operator,
      List<BindingView> bindings,
      String csrfToken,
      String csrfHeaderName) {}

  public record DecisionInput(String decisionId, String decision, long expectedVersion) {}
}
