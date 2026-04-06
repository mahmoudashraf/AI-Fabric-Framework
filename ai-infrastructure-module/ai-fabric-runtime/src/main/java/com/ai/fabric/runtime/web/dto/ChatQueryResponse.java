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
        deprecated = true,
        description = "Legacy compatibility only. Prefer authContext.subjectId for verified caller identity."
    )
    private String userId;
    private String sessionId;
    private RuntimeAuthContextResponse authContext;
    private OrchestrationResult result;
}
