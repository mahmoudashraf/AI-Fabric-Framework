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

    @Builder.Default
    private Map<String, String> parameters = Collections.emptyMap();

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

    public void setRequiredParameters(Set<String> requiredParameters) {
        this.requiredParameters = requiredParameters == null ? Collections.emptySet() : Set.copyOf(requiredParameters);
    }
}
