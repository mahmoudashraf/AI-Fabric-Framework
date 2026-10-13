package com.ai.fabric.runtime.web.dto;

import java.time.Instant;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConversationParticipantMessageResponse {
    private String messageId;
    private long sequence;
    private String role;
    private String participantDisplayName;
    private String content;
    private String clientMessageId;
    private Map<String, Object> metadata;
    private Instant createdAt;
}
