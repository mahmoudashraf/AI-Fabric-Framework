package com.ai.fabric.runtime.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ConversationResponse {
    private String id;

    @Deprecated
    @Schema(
        deprecated = true,
        description = "Legacy compatibility only. Prefer authContext.subjectId for verified caller identity."
    )
    private String ownerId;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime lastInteractionAt;
    private RuntimeAuthContextResponse authContext;
    private List<TurnResponse> turns;
}
