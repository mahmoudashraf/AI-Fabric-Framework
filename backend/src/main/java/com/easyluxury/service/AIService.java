package com.easyluxury.service;

import com.easyluxury.dto.AIProfileDto;
import com.easyluxury.entity.AIProfile;

public interface AIService {
    
    /**
     * Generate AI profile from CV content using OpenAI GPT
     * @param cvContent The CV text content
     * @return AIProfileDto with generated profile data
     */
    AIProfileDto generateProfileFromCV(String cvContent);
    
    /**
     * Check if AI service is available
     * @return true if AI service is configured and available
     */
    boolean isAvailable();
    
    /**
     * Get the AI model being used
     * @return model name
     */
    String getModelName();
}