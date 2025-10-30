package com.easyluxury.ai.service;

import org.springframework.stereotype.Service;

/**
 * SimpleAIService
 * 
 * Simple placeholder AI service for compilation purposes.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Service
public class SimpleAIService {
    
    public String generateContent(String prompt) {
        return "AI analysis placeholder for: " + prompt;
    }
    
    public String generateContent(String prompt, String entityType, String purpose) {
        return "AI analysis placeholder for: " + prompt + " (type: " + entityType + ", purpose: " + purpose + ")";
    }
}