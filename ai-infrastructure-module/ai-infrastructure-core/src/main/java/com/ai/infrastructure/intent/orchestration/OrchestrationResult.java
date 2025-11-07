package com.ai.infrastructure.intent.orchestration;

import com.ai.infrastructure.dto.NextStepRecommendation;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrchestrationResult {

    private OrchestrationResultType type;

    private boolean success;

    private String message;

    @Builder.Default
    private Map<String, Object> data = Collections.emptyMap();

    @Builder.Default
    private List<NextStepRecommendation> nextSteps = List.of();

    @Builder.Default
    private List<OrchestrationResult> children = List.of();

    @Builder.Default
    private Map<String, Object> metadata = Collections.emptyMap();

    @Builder.Default
    private Map<String, Object> smartSuggestion = Collections.emptyMap();

    public static OrchestrationResult error(String message) {
        return OrchestrationResult.builder()
            .type(OrchestrationResultType.ERROR)
            .success(false)
            .message(message)
            .build();
    }

    public OrchestrationResult withAdditionalData(Map<String, Object> additional) {
        if (additional == null || additional.isEmpty()) {
            return this;
        }
        Map<String, Object> merged = new java.util.LinkedHashMap<>(data);
        merged.putAll(additional);
        setData(Collections.unmodifiableMap(merged));
        return this;
    }
}
