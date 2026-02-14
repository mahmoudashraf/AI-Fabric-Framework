package com.ai.fabric.runtime.web.dto;

import com.ai.infrastructure.intent.orchestration.attachment.OrchestrationAttachment;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.util.List;

@Data
public class SuggestionsRequest {
    private String content;
    private String userId;
    private List<OrchestrationAttachment> attachments;

    @Min(1)
    @Max(10)
    private Integer maxSuggestions = 5;
}

