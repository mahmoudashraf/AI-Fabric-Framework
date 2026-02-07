package com.ai.infrastructure.intent.action;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Describes an action exposed to the RAG orchestrator.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AIActionMetaData {

    private String name;

    private String description;

    private String category;

    /**
     * Side-effect semantics for this action.
     */
    private ActionAccessMode accessMode;

    @Builder.Default
    private Map<String, String> parameters = Collections.emptyMap();

    /**
     * Structured parameter schemas for actionParams.
     *
     * <p>This is the contract the LLM should follow when populating actionParams, including
     * array/object shapes for batch-capable actions.</p>
     */
    @Builder.Default
    private Map<String, AIActionParamSchema> parameterSchemas = Collections.emptyMap();

    /**
     * Parameter names that must be provided for the action to execute safely.
     *
     * <p>This is used for deterministic validation and provider-agnostic intent extraction fallbacks.
     * It should not be inferred from human-readable parameter descriptions.</p>
     */
    @Builder.Default
    private Set<String> requiredParameters = Collections.emptySet();

    public void setParameters(Map<String, String> parameters) {
        this.parameters = parameters == null ? Collections.emptyMap() : Map.copyOf(parameters);
    }

    public void setParameterSchemas(Map<String, AIActionParamSchema> parameterSchemas) {
        this.parameterSchemas = parameterSchemas == null ? Collections.emptyMap() : Map.copyOf(parameterSchemas);
    }

    public void setRequiredParameters(Set<String> requiredParameters) {
        this.requiredParameters = requiredParameters == null ? Collections.emptySet() : Set.copyOf(requiredParameters);
    }
}
