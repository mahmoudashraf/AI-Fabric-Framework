package com.ai.infrastructure.intent.action.connector;

import com.ai.infrastructure.intent.action.ActionAccessMode;
import com.ai.infrastructure.intent.action.ActionResultPresentationHint;
import com.ai.infrastructure.intent.action.AIContributionProvenance;

import java.util.List;

/**
 * Connector action definition loaded from a file-based contract.
 *
 * <p>This describes the action contract (metadata + parameter schema + confirmation rules),
 * not the execution routing. Execution is performed via the Customer Connector API.</p>
 */
public record ConnectorActionDefinition(
    String name,
    String displayName,
    String description,
    String category,
    ActionAccessMode accessMode,
    boolean requiresConfirmation,
    String confirmationMessage,
    List<ConnectorActionParamDefinition> params,
    boolean anonymousAllowed,
    boolean groundingEligible,
    boolean readActionResolutionEligible,
    ActionResultPresentationHint resultPresentationHint,
    String builtInModuleId,
    String builtInCardId,
    AIContributionProvenance provenance,
    List<ConnectorActionPostPolicyDefinition> postPolicies
) {
    public ConnectorActionDefinition {
        params = params != null ? List.copyOf(params) : List.of();
        postPolicies = postPolicies != null ? List.copyOf(postPolicies) : List.of();
    }
}
