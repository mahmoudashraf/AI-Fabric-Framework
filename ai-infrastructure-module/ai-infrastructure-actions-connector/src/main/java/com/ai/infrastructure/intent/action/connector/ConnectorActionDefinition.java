package com.ai.infrastructure.intent.action.connector;

import com.ai.infrastructure.intent.action.ActionAccessMode;
import com.ai.infrastructure.intent.action.ActionResultPresentationHint;
import com.ai.infrastructure.intent.action.AIContributionProvenance;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    List<ConnectorActionPostPolicyDefinition> postPolicies,
    ConnectorActionLlmFactsDefinition llmFacts,
    String adapterType,
    Map<String, Object> execution,
    Map<String, Object> mcpServers
) {
    public ConnectorActionDefinition {
        params = params != null ? List.copyOf(params) : List.of();
        postPolicies = postPolicies != null ? List.copyOf(postPolicies) : List.of();
        adapterType = adapterType != null && !adapterType.isBlank() ? adapterType.trim() : null;
        execution = execution != null && !execution.isEmpty() ? Map.copyOf(execution) : Map.of();
        mcpServers = mcpServers != null && !mcpServers.isEmpty() ? Map.copyOf(mcpServers) : Map.of();
    }

    public ConnectorActionDefinition(
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
        List<ConnectorActionPostPolicyDefinition> postPolicies,
        ConnectorActionLlmFactsDefinition llmFacts
    ) {
        this(
            name,
            displayName,
            description,
            category,
            accessMode,
            requiresConfirmation,
            confirmationMessage,
            params,
            anonymousAllowed,
            groundingEligible,
            readActionResolutionEligible,
            resultPresentationHint,
            builtInModuleId,
            builtInCardId,
            provenance,
            postPolicies,
            llmFacts,
            null,
            Map.of(),
            Map.of()
        );
    }

    public Map<String, Object> runtimeActionConfig() {
        Map<String, Object> out = new LinkedHashMap<>();
        if (adapterType != null && !adapterType.isBlank()) {
            out.put("adapterType", adapterType);
        }
        if (execution != null && !execution.isEmpty()) {
            out.put("execution", execution);
        }
        if (mcpServers != null && !mcpServers.isEmpty()) {
            out.put("mcpServers", mcpServers);
        }
        return out.isEmpty() ? Map.of() : Map.copyOf(out);
    }
}
