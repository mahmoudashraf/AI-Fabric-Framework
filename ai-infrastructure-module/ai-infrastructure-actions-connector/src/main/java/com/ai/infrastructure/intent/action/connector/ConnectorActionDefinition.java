package com.ai.infrastructure.intent.action.connector;

import com.ai.infrastructure.intent.action.ActionAccessMode;

import java.util.List;

/**
 * Connector action definition loaded from a file-based contract.
 *
 * <p>This describes the action contract (metadata + parameter schema + confirmation rules),
 * not the execution routing. Execution is performed via the Customer Connector API.</p>
 */
public record ConnectorActionDefinition(
    String name,
    String description,
    String category,
    ActionAccessMode accessMode,
    boolean requiresConfirmation,
    String confirmationMessage,
    List<ConnectorActionParamDefinition> params,
    boolean anonymousAllowed
) {
    public ConnectorActionDefinition {
        params = params != null ? List.copyOf(params) : List.of();
    }
}
