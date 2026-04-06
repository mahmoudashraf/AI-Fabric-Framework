package com.ai.fabric.runtime.web.dto;

import com.ai.infrastructure.intent.orchestration.attachment.OrchestrationAttachment;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.util.List;

@Data
public class SuggestionsRequest {
    private String content;

    @Deprecated
    @Schema(
        deprecated = true,
        description = "Legacy compatibility only. Production callers should convey identity through verified runtime auth context headers instead of request body userId."
    )
    private String userId;
    private List<OrchestrationAttachment> attachments;

    @Min(1)
    @Max(10)
    private Integer maxSuggestions = 5;
}
