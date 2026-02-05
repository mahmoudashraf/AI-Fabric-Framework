package com.ai.infrastructure.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * AIGenerationRequest
 * 
 * Request DTO for AI content generation operations.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIGenerationRequest {
    
    @NotNull(message = "Entity ID is required")
    private String entityId;
    
    @NotNull(message = "Entity type is required")
    private String entityType;
    
    @NotNull(message = "Generation type is required")
    private String generationType;
    
    private String prompt;
    
    private String context;
    
    private String systemPrompt;

    /**
     * Optional multi-message chat history to send to chat-capable LLM providers.
     *
     * <p>When present, providers MUST send these as native chat messages (role/user/assistant),
     * and MUST NOT pack them into the {@link #prompt} string.</p>
     */
    private List<AIChatMessage> messages;
    
    private String purpose;
    
    private Map<String, Object> parameters;
    
    private String model;
    
    private Integer maxTokens;
    
    private Double temperature;
    
    private String userId;
}
