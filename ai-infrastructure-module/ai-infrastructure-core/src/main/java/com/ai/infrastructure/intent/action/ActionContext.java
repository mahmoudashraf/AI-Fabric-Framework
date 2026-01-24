package com.ai.infrastructure.intent.action;

import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineContext;

import java.util.Map;

/**
 * Execution context passed to action methods.
 */
public record ActionContext(OrchestrationContext orchestrationContext, PipelineContext pipelineContext) {

    public String userId() {
        return orchestrationContext != null ? orchestrationContext.getUserId() : null;
    }

    public String sessionId() {
        return orchestrationContext != null ? orchestrationContext.getSessionId() : null;
    }

    public String identifier() {
        return orchestrationContext != null ? orchestrationContext.getIdentifier() : null;
    }

    public String conversationId() {
        return orchestrationContext != null ? orchestrationContext.getConversationId() : null;
    }

    public String requestId() {
        return orchestrationContext != null ? orchestrationContext.getRequestId() : null;
    }

    public boolean hasConversation() {
        return orchestrationContext != null && orchestrationContext.hasConversation();
    }

    public Map<String, Object> metadata() {
        return orchestrationContext != null ? orchestrationContext.getMetadata() : Map.of();
    }
}

