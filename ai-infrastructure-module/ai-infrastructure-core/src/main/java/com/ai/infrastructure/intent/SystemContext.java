package com.ai.infrastructure.intent;

import com.ai.infrastructure.intent.action.ActionInfo;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.List;
import java.util.Map;

/**
 * Bundles the contextual information passed to the intent extraction prompt.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SystemContext {

    @Builder.Default
    private List<ActionInfo> availableActions = List.of();

    private KnowledgeBaseOverview knowledgeBaseOverview;

    private String userId;

    private String sessionId;

    private Boolean authenticated;

    private Locale locale;

    private Map<String, Object> metadata;

    private LocalDateTime timestamp;

    public void setAvailableActions(List<ActionInfo> availableActions) {
        this.availableActions = availableActions == null ? List.of() : List.copyOf(availableActions);
    }

    public void setKnowledgeBaseOverview(KnowledgeBaseOverview knowledgeBaseOverview) {
        this.knowledgeBaseOverview = knowledgeBaseOverview;
    }

    public boolean isAuthenticated() {
        return Boolean.TRUE.equals(authenticated);
    }

    public String getIdentifier() {
        return isAuthenticated() ? userId : sessionId;
    }
}
