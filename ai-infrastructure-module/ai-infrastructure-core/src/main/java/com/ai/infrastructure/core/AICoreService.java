package com.ai.infrastructure.core;

import com.ai.infrastructure.dto.AIGenerationRequest;
import com.ai.infrastructure.dto.AIGenerationResponse;
import com.ai.infrastructure.dto.AIEmbeddingRequest;
import com.ai.infrastructure.dto.AIEmbeddingResponse;
import com.ai.infrastructure.dto.AISearchRequest;
import com.ai.infrastructure.dto.AISearchResponse;
import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.exception.AIServiceException;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.embedding.EmbeddingRequest;
import com.theokanning.openai.embedding.EmbeddingResult;
import com.theokanning.openai.service.OpenAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Core AI service providing generic AI capabilities
 * 
 * This service provides the foundation for all AI features including:
 * - Text generation using OpenAI GPT models
 * - Embedding generation for vector search
 * - Semantic search capabilities
 * - AI-powered content generation
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AICoreService {
    
    private final AIProviderConfig aiProviderConfig;
    private final AIEmbeddingService embeddingService;
    private final AISearchService searchService;
    
    private OpenAiService openAiService;

    private boolean isMockEnabled() {
        return Boolean.TRUE.equals(aiProviderConfig.getOpenaiMockEnabled());
    }
    
    /**
     * Initialize OpenAI service with configuration
     */
    private void initializeOpenAI() {
        if (isMockEnabled()) {
            log.debug("OpenAI mock mode enabled - skipping real service initialization");
            return;
        }

        if (openAiService == null) {
            openAiService = new OpenAiService(
                aiProviderConfig.getOpenaiApiKey(),
                Duration.ofSeconds(aiProviderConfig.getOpenaiTimeout())
            );
        }
    }
    
    /**
     * Generate AI content based on prompt
     * 
     * @param request the generation request
     * @return generated content response
     */
    public AIGenerationResponse generateContent(AIGenerationRequest request) {
        try {
            if (isMockEnabled()) {
                return generateMockContent(request);
            }

            initializeOpenAI();
            
            log.debug("Generating AI content for prompt: {}", request.getPrompt());
            
            ChatCompletionRequest chatRequest = ChatCompletionRequest.builder()
                .model(aiProviderConfig.getOpenaiModel())
                .messages(List.of(
                    new ChatMessage(ChatMessageRole.SYSTEM.value(), request.getSystemPrompt()),
                    new ChatMessage(ChatMessageRole.USER.value(), request.getPrompt())
                ))
                .maxTokens(aiProviderConfig.getOpenaiMaxTokens())
                .temperature(aiProviderConfig.getOpenaiTemperature())
                .build();
            
            var completion = openAiService.createChatCompletion(chatRequest);
            var message = completion.getChoices().get(0).getMessage();
            
            log.debug("Successfully generated AI content");
            
            return AIGenerationResponse.builder()
                .content(message.getContent())
                .model(aiProviderConfig.getOpenaiModel())
                .usage(completion.getUsage())
                .build();
                
        } catch (Exception e) {
            log.error("Error generating AI content", e);
            throw new AIServiceException("Failed to generate AI content", e);
        }
    }
    
    /**
     * Generate embeddings for text content
     * 
     * @param request the embedding request
     * @return embedding response with vector data
     */
    public AIEmbeddingResponse generateEmbedding(AIEmbeddingRequest request) {
        try {
            if (isMockEnabled()) {
                log.debug("OpenAI mock mode enabled - delegating embedding generation to AIEmbeddingService");
                return embeddingService.generateEmbedding(request);
            }

            initializeOpenAI();
            
            log.debug("Generating embedding for text: {}", request.getText());
            
            EmbeddingRequest embeddingRequest = EmbeddingRequest.builder()
                .model(aiProviderConfig.getOpenaiEmbeddingModel())
                .input(List.of(request.getText()))
                .build();
            
            EmbeddingResult result = openAiService.createEmbeddings(embeddingRequest);
            var embedding = result.getData().get(0).getEmbedding();
            
            log.debug("Successfully generated embedding with {} dimensions", embedding.size());
            
            return AIEmbeddingResponse.builder()
                .embedding(embedding)
                .model(aiProviderConfig.getOpenaiEmbeddingModel())
                .dimensions(embedding.size())
                .build();
                
        } catch (Exception e) {
            log.error("Error generating embedding", e);
            throw new AIServiceException("Failed to generate embedding", e);
        }
    }
    
    /**
     * Perform semantic search across indexed content
     * 
     * @param request the search request
     * @return search results with relevance scores
     */
    public AISearchResponse performSearch(AISearchRequest request) {
        try {
            log.debug("Performing semantic search for query: {}", request.getQuery());
            
            // Generate embedding for search query
            AIEmbeddingRequest embeddingRequest = AIEmbeddingRequest.builder()
                .text(request.getQuery())
                .build();
            
            AIEmbeddingResponse embedding = generateEmbedding(embeddingRequest);
            
            // Perform vector search
            return searchService.search(embedding.getEmbedding(), request);
            
        } catch (Exception e) {
            log.error("Error performing semantic search", e);
            throw new AIServiceException("Failed to perform semantic search", e);
        }
    }
    
    /**
     * Generate AI recommendations based on context
     * 
     * @param entityType the type of entity to recommend
     * @param context the context for recommendations
     * @param limit maximum number of recommendations
     * @return list of recommended entities
     */
    public List<Map<String, Object>> generateRecommendations(String entityType, String context, int limit) {
        try {
            log.debug("Generating recommendations for entity type: {} with context: {}", entityType, context);
            
            // Generate embedding for context
            AIEmbeddingRequest embeddingRequest = AIEmbeddingRequest.builder()
                .text(context)
                .build();
            
            AIEmbeddingResponse embedding = generateEmbedding(embeddingRequest);
            
            // Find similar entities
            AISearchRequest searchRequest = AISearchRequest.builder()
                .query(context)
                .entityType(entityType)
                .limit(limit)
                .build();
            
            AISearchResponse searchResponse = searchService.search(embedding.getEmbedding(), searchRequest);
            
            log.debug("Generated {} recommendations", searchResponse.getResults().size());
            
            return searchResponse.getResults();
            
        } catch (Exception e) {
            log.error("Error generating recommendations", e);
            throw new AIServiceException("Failed to generate recommendations", e);
        }
    }
    
    /**
     * Validate content using AI
     * 
     * @param content the content to validate
     * @param validationRules the validation rules to apply
     * @return validation result with suggestions
     */
    public Map<String, Object> validateContent(String content, Map<String, Object> validationRules) {
        try {
            log.debug("Validating content using AI");
            
            String prompt = buildValidationPrompt(content, validationRules);
            
            AIGenerationRequest request = AIGenerationRequest.builder()
                .prompt(prompt)
                .systemPrompt("You are an AI content validator. Analyze the content and provide validation results with suggestions for improvement.")
                .build();
            
            AIGenerationResponse response = generateContent(request);
            
            // Parse validation result from AI response
            return parseValidationResult(response.getContent());
            
        } catch (Exception e) {
            log.error("Error validating content", e);
            throw new AIServiceException("Failed to validate content", e);
        }
    }
    
    /**
     * Build validation prompt for AI
     */
    private String buildValidationPrompt(String content, Map<String, Object> validationRules) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Validate the following content:\n\n");
        prompt.append("Content: ").append(content).append("\n\n");
        prompt.append("Validation Rules:\n");
        validationRules.forEach((key, value) -> 
            prompt.append("- ").append(key).append(": ").append(value).append("\n"));
        prompt.append("\nProvide validation results in JSON format with 'valid', 'errors', and 'suggestions' fields.");
        
        return prompt.toString();
    }
    
    /**
     * Parse validation result from AI response
     */
    private Map<String, Object> parseValidationResult(String aiResponse) {
        // Simple JSON parsing - in production, use proper JSON parser
        try {
            // This is a simplified implementation
            // In production, use Jackson or Gson for proper JSON parsing
            return Map.of(
                "valid", aiResponse.contains("\"valid\": true"),
                "errors", List.of(),
                "suggestions", List.of(aiResponse)
            );
        } catch (Exception e) {
            log.warn("Failed to parse AI validation result", e);
            return Map.of(
                "valid", false,
                "errors", List.of("Failed to parse validation result"),
                "suggestions", List.of("Please check the content manually")
            );
        }
    }
    
    /**
     * Generate text using AI with simple string input
     */
    public String generateText(String prompt) {
        try {
            if (isMockEnabled()) {
                return generateMockText(prompt);
            }

            initializeOpenAI();
            
            if (openAiService == null) {
                throw new AIServiceException("OpenAI service not available");
            }
            
            ChatCompletionRequest chatRequest = ChatCompletionRequest.builder()
                .model(aiProviderConfig.getOpenaiModel())
                .messages(List.of(new ChatMessage(ChatMessageRole.USER.value(), prompt)))
                .maxTokens(1000)
                .temperature(0.7)
                .build();
            
            var completion = openAiService.createChatCompletion(chatRequest);
            var message = completion.getChoices().get(0).getMessage();
            
            return message.getContent();
                
        } catch (Exception e) {
            log.error("Error generating text: {}", e.getMessage(), e);
            throw new AIServiceException("Failed to generate text: " + e.getMessage(), e);
        }
    }
}