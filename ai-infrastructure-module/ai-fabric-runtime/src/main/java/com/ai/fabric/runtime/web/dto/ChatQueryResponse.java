package com.ai.fabric.runtime.web.dto;

import com.ai.infrastructure.intent.orchestration.OrchestrationResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChatQueryResponse {
    private boolean success;
    private String message;
    private String conversationId;

    @Deprecated
    @Schema(
        hidden = true,
        deprecated = true,
        description = "Legacy compatibility only. Prefer authContext.subjectId for verified caller identity."
    )
    private String userId;

    @Schema(description = "Resolved conversation session id. Prefer authContext.sessionId for verified auth-aware integrations.")
    private String sessionId;
    private RuntimeAuthContextResponse authContext;
    private OrchestrationResult result;
}
