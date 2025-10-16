package com.easyluxury.service;

import com.easyluxury.dto.AIProfileDataDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
public class AIService {
    
    @Value("${openai.api-key:}")
    private String apiKey;
    
    @Value("${openai.model:gpt-4o-mini}")
    private String model;
    
    @Value("${openai.max-tokens:2000}")
    private Integer maxTokens;
    
    @Value("${openai.temperature:0.3}")
    private Double temperature;
    
    @Value("${openai.timeout:60}")
    private Integer timeout;
    
    private final ObjectMapper objectMapper;
    
    public AIService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    /**
     * Generates AI profile data from CV content using OpenAI GPT
     */
    public AIProfileDataDto generateProfileFromCV(String cvContent) {
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("sk-your-openai-api-key")) {
            log.warn("OpenAI API key not configured, using mock data");
            return generateMockProfile(cvContent);
        }
        
        try {
            OpenAiService service = new OpenAiService(apiKey, Duration.ofSeconds(timeout));
            
            List<ChatMessage> messages = Arrays.asList(
                new ChatMessage(ChatMessageRole.SYSTEM.value(), getSystemPrompt()),
                new ChatMessage(ChatMessageRole.USER.value(), cvContent)
            );
            
            ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model(model)
                .messages(messages)
                .maxTokens(maxTokens)
                .temperature(temperature)
                .build();
            
            String response = service.createChatCompletion(request)
                .getChoices()
                .get(0)
                .getMessage()
                .getContent();
            
            log.debug("OpenAI response: {}", response);
            
            // Parse JSON response
            return objectMapper.readValue(response, AIProfileDataDto.class);
            
        } catch (Exception e) {
            log.error("Error calling OpenAI API", e);
            return generateMockProfile(cvContent);
        }
    }
    
    private String getSystemPrompt() {
        return """
            You are an AI assistant specialized in extracting professional profile information from CVs.
            
            Analyze the provided CV content and extract the following information in the exact JSON format:
            {
                "name": "Full Name",
                "jobTitle": "Current Job Title",
                "companies": [
                    {
                        "name": "Company Name",
                        "icon": "https://logo.clearbit.com/company.com",
                        "position": "Job Title",
                        "duration": "2020-2024"
                    }
                ],
                "profileSummary": "Professional summary (max 500 characters)",
                "skills": ["Skill1", "Skill2", "Skill3"],
                "experience": 5,
                "photos": {
                    "profilePhoto": "placeholder://profile-photo",
                    "coverPhoto": "placeholder://cover-photo",
                    "professional": ["placeholder://professional-1"],
                    "team": ["placeholder://team-1"],
                    "project": ["placeholder://project-1"]
                },
                "photoSuggestions": {
                    "profilePhoto": {
                        "required": true,
                        "count": 1,
                        "suggestions": ["Professional headshot with clean background"],
                        "description": "Clear professional headshot photo"
                    },
                    "coverPhoto": {
                        "required": false,
                        "count": 1,
                        "suggestions": ["Professional workspace or city skyline"],
                        "description": "Cover photo for profile banner"
                    },
                    "professional": {
                        "required": false,
                        "count": 3,
                        "suggestions": ["Working at desk", "Presenting to team", "In meeting"],
                        "description": "Professional work environment photos"
                    }
                }
            }
            
            Guidelines:
            - Extract only information clearly stated in the CV
            - Keep profileSummary under 500 characters
            - Generate 3-7 relevant skills based on the CV
            - Calculate experience years from work history
            - Use placeholder:// URLs for photos
            - Return ONLY the JSON object, no additional text
            - If information is not available, use reasonable defaults
            """;
    }
    
    /**
     * Generates mock profile data for development/testing
     */
    private AIProfileDataDto generateMockProfile(String cvContent) {
        return AIProfileDataDto.builder()
            .name("John Doe")
            .jobTitle("Senior Software Engineer")
            .companies(Arrays.asList(
                AIProfileDataDto.CompanyDto.builder()
                    .name("Tech Corp")
                    .icon("https://logo.clearbit.com/techcorp.com")
                    .position("Senior Software Engineer")
                    .duration("2020-2024")
                    .build(),
                AIProfileDataDto.CompanyDto.builder()
                    .name("StartupXYZ")
                    .icon("https://logo.clearbit.com/startupxyz.com")
                    .position("Software Engineer")
                    .duration("2018-2020")
                    .build()
            ))
            .profileSummary("Experienced software engineer with 6+ years building scalable web applications. Specialized in Java, Spring Boot, and React. Passionate about clean code and best practices.")
            .skills(Arrays.asList("Java", "Spring Boot", "React", "TypeScript", "PostgreSQL", "AWS", "Docker"))
            .experience(6)
            .photos(null)
            .photoSuggestions(null)
            .build();
    }
}
