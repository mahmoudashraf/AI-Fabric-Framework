package com.ai.fabric.runtime.web.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ConversationSummaryResponse {
    private String id;
    private String status;
    private String controlState;
    private LocalDateTime createdAt;
    private LocalDateTime lastInteractionAt;
    private int turnsCount;
    private RuntimeAuthContextResponse authContext;
}
