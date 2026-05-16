package com.ai.infrastructure.intent.action;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Parameter schema contract for LLM-populated actionParams.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AIActionParamSchema {

    private String name;

    private String description;

    private AIActionParamType type;

    private Boolean required;

    /**
     * When true, this parameter can be populated by batching pinned targets.
     */
    private Boolean batchTargets;

    /**
     * Item schema for {@link AIActionParamType#ARRAY} parameters.
     */
    private AIActionParamSchema items;

    /**
     * Property schemas for {@link AIActionParamType#OBJECT} parameters.
     */
    @Builder.Default
    private Map<String, AIActionParamSchema> properties = Collections.emptyMap();

    /**
     * Required property names for {@link AIActionParamType#OBJECT} parameters.
     */
    @Builder.Default
    private List<String> requiredProperties = List.of();

    private String pattern;

    @Builder.Default
    private List<String> allowedValues = List.of();

    private Long min;

    private Long max;

    private Object defaultValue;

    public void setProperties(Map<String, AIActionParamSchema> properties) {
        this.properties = properties == null ? Collections.emptyMap() : Map.copyOf(properties);
    }

    public void setRequiredProperties(List<String> requiredProperties) {
        this.requiredProperties = requiredProperties == null ? List.of() : List.copyOf(requiredProperties);
    }

    public void setAllowedValues(List<String> allowedValues) {
        this.allowedValues = allowedValues == null ? List.of() : List.copyOf(allowedValues);
    }
}
