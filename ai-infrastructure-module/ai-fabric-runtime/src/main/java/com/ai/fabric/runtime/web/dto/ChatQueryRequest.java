package com.ai.fabric.runtime.web.dto;

import com.ai.infrastructure.intent.orchestration.attachment.OrchestrationAttachment;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ChatQueryRequest {

    @NotBlank
    private String query;

    private String userId;
    private String sessionId;
    private String conversationId;
    private String position;
    private String mode;
    private List<OrchestrationAttachment> attachments;
    private Map<String, String> promptPreview;
}
