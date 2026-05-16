package com.ai.infrastructure.intent.action.connector;

import com.ai.infrastructure.intent.action.AIActionParamType;

import java.util.List;
import java.util.Map;

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
    Object defaultValue,
    boolean sensitive,
    ConnectorActionParamDefinition items,
    Map<String, ConnectorActionParamDefinition> properties,
    List<String> requiredProperties
) {
    public ConnectorActionParamDefinition {
        allowedValues = allowedValues != null ? List.copyOf(allowedValues) : List.of();
        properties = properties != null ? Map.copyOf(properties) : Map.of();
        requiredProperties = requiredProperties != null ? List.copyOf(requiredProperties) : List.of();
    }
}
