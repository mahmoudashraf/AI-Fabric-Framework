package com.ai.fabric.runtime.web.dto;

import com.ai.infrastructure.intent.orchestration.OrchestrationResult;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChatQueryResponse {
    private boolean success;
    private String message;
    private String conversationId;
    private String userId;
    private String sessionId;
    private OrchestrationResult result;
}

