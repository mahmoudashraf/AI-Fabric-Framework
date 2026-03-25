package com.ai.fabric.runtime.web.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ConversationResponse {
    private String id;
    private String ownerId;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime lastInteractionAt;
    private List<TurnResponse> turns;
}

