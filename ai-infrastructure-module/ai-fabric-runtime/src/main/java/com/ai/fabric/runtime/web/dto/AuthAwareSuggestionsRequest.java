package com.ai.fabric.runtime.web.dto;

import com.ai.infrastructure.intent.orchestration.attachment.OrchestrationAttachment;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class AuthAwareSuggestionsRequest {
    private String content;
    private List<OrchestrationAttachment> attachments;

    @Min(1)
    @Max(10)
    private Integer maxSuggestions = 5;

    @JsonIgnore
    private final Map<String, Object> unexpectedFields = new LinkedHashMap<>();

    @JsonAnySetter
    void captureUnexpectedField(String name, Object value) {
        unexpectedFields.put(name, value);
    }

    @JsonIgnore
    public SuggestionsRequest toSuggestionsRequest() {
        SuggestionsRequest request = new SuggestionsRequest();
        request.setContent(content);
        request.setAttachments(attachments);
        request.setMaxSuggestions(maxSuggestions);
        return request;
    }
}
