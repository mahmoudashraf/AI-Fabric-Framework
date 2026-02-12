package com.ai.infrastructure.intent.action.connector;

import com.ai.infrastructure.intent.action.AIActionParamType;

import java.util.List;

/**
 * Connector action parameter definition loaded from a file-based contract.
 */
public record ConnectorActionParamDefinition(
    String name,
    String description,
    AIActionParamType type,
    boolean required,
    boolean batchTargets,
    String pattern,
    List<String> allowedValues,
    Long min,
    Long max,
    boolean sensitive
) {
    public ConnectorActionParamDefinition {
        allowedValues = allowedValues != null ? List.copyOf(allowedValues) : List.of();
    }
}

