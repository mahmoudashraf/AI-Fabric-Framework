package com.ai.infrastructure.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a single intent extracted from a user query.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Intent {

    private IntentType type;

    /**
     * Canonical name of the detected intent (e.g. {@code cancel_subscription}).
     */
    private String intent;

    /**
     * Confidence score (0.0 - 1.0) supplied by the intent extractor.
     */
    private Double confidence;

    /**
     * Name of the action to execute when the intent type is {@link IntentType#ACTION}.
     */
    private String action;

    /**
     * Action parameters to be forwarded when executing the action.
     */
    @Builder.Default
    private Map<String, Object> actionParams = Collections.emptyMap();

    /**
     * Logical vector space or index that should be queried to fulfil the intent.
     */
    private String vectorSpace;

    /**
     * Whether this intent requires document retrieval before responding.
     */
    @JsonAlias({"requires_retrieval"})
    private Boolean requiresRetrieval;

    /**
     * Optional recommendation that should be surfaced after handling this intent.
     */
    private NextStepRecommendation nextStepRecommended;

    public void setActionParams(Map<String, Object> params) {
        this.actionParams = params == null ? Collections.emptyMap() : Map.copyOf(params);
    }

    /**
     * Returns {@code true} whenever the intent type represents an actionable operation.
     */
    public boolean isActionable() {
        return type == IntentType.ACTION;
    }

    public double confidenceOrDefault(double fallback) {
        return confidence != null ? confidence : fallback;
    }

    public boolean requiresRetrievalOrDefault(boolean fallback) {
        return requiresRetrieval != null ? requiresRetrieval : fallback;
    }

    public String getIntentOrAction() {
        if (intent != null && !intent.isBlank()) {
            return intent;
        }
        return action;
    }

    public void normalize() {
        if (confidence != null) {
            confidence = Math.max(0.0d, Math.min(1.0d, confidence));
        }
        if (requiresRetrieval == null) {
            requiresRetrieval = type == IntentType.INFORMATION || type == IntentType.COMPOUND;
        }
        if (actionParams == null) {
            actionParams = Collections.emptyMap();
        } else {
            actionParams = Map.copyOf(actionParams);
        }
        if (nextStepRecommended != null && nextStepRecommended.getConfidence() != null) {
            double value = Math.max(0.0d, Math.min(1.0d, nextStepRecommended.getConfidence()));
            nextStepRecommended.setConfidence(value);
        }
    }

    public boolean hasValidType() {
        return type != null;
    }

    public boolean hasMeaningfulName() {
        return Objects.nonNull(getIntentOrAction()) && !getIntentOrAction().isBlank();
    }
}
