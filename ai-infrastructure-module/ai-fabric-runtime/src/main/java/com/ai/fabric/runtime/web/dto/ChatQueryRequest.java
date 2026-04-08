package com.ai.fabric.runtime.web.dto;

import com.ai.infrastructure.intent.orchestration.attachment.OrchestrationAttachment;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ChatQueryRequest {

    @NotBlank
    private String query;

    @Deprecated
    @Schema(
        hidden = true,
        deprecated = true,
        description = "Legacy compatibility only. Production callers should convey identity through verified runtime auth context headers instead of request body userId."
    )
    private String userId;

    @Deprecated
    @Schema(
        hidden = true,
        deprecated = true,
        description = "Legacy compatibility only. Production callers should convey session identity through verified runtime auth context headers instead of request body sessionId."
    )
    private String sessionId;
    private String conversationId;
    private String position;
    private String mode;
    private List<OrchestrationAttachment> attachments;
    private Map<String, String> promptPreview;
}
