package com.easyluxury.service.impl;

import com.easyluxury.dto.AIProfileDto;
import com.easyluxury.entity.AIProfile;
import com.easyluxury.service.AIService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAIServiceImpl implements AIService {

    private final OpenAiService openAiService;
    private final ObjectMapper objectMapper;

    @Value("${openai.model:gpt-4o-mini}")
    private String modelName;

    @Value("${openai.max-tokens:2000}")
    private int maxTokens;

    @Value("${openai.temperature:0.3}")
    private double temperature;

    @Override
    public AIProfileDto generateProfileFromCV(String cvContent) {
        if (!isAvailable()) {
            log.warn("OpenAI service not available, falling back to mock data");
            return generateMockProfile(cvContent);
        }

        try {
            log.info("Generating AI profile from CV content using OpenAI");
            
            String prompt = buildPrompt(cvContent);
            
            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model(modelName)
                    .messages(Arrays.asList(
                            new ChatMessage(ChatMessageRole.SYSTEM.value(), getSystemPrompt()),
                            new ChatMessage(ChatMessageRole.USER.value(), prompt)
                    ))
                    .maxTokens(maxTokens)
                    .temperature(temperature)
                    .build();

            String response = openAiService.createChatCompletion(request)
                    .getChoices()
                    .get(0)
                    .getMessage()
                    .getContent();

            log.info("OpenAI response received, parsing JSON");
            return parseAIResponse(response);

        } catch (Exception e) {
            log.error("Failed to generate AI profile with OpenAI: {}", e.getMessage(), e);
            log.info("Falling back to mock data due to AI service error");
            return generateMockProfile(cvContent);
        }
    }

    @Override
    public boolean isAvailable() {
        return openAiService != null;
    }

    @Override
    public String getModelName() {
        return modelName;
    }

    private String buildPrompt(String cvContent) {
        return String.format("""
            Please analyze the following CV content and extract professional profile information.
            
            CV Content:
            %s
            
            Extract the following information and return it as a JSON object with the exact structure specified in the system prompt.
            """, cvContent);
    }

    private String getSystemPrompt() {
        return """
            You are an AI assistant specialized in extracting professional profile information from CVs.
            
            Analyze the provided CV content and extract the following information in the exact JSON format below:
            
            {
                "name": "Full Name",
                "jobTitle": "Current or Most Recent Job Title",
                "companies": [
                    {
                        "name": "Company Name",
                        "icon": "https://logo.clearbit.com/company.com",
                        "position": "Job Title at Company",
                        "duration": "Start Year - End Year or Present"
                    }
                ],
                "profileSummary": "Professional summary (max 500 characters)",
                "skills": ["Skill1", "Skill2", "Skill3"],
                "experience": 5,
                "photos": {
                    "profilePhoto": "placeholder://profile-photo",
                    "coverPhoto": "placeholder://cover-photo",
                    "professional": ["placeholder://professional-1", "placeholder://professional-2"],
                    "team": ["placeholder://team-1"],
                    "project": ["placeholder://project-1"]
                },
                "photoSuggestions": {
                    "profilePhoto": {
                        "required": true,
                        "count": 1,
                        "suggestions": ["Professional headshot for LinkedIn profile"],
                        "description": "A clear, professional headshot that represents your personal brand"
                    },
                    "professional": {
                        "required": false,
                        "count": 2,
                        "suggestions": ["Team meeting photo", "Conference presentation", "Working at desk"],
                        "description": "Photos showcasing your professional work environment"
                    },
                    "team": {
                        "required": false,
                        "count": 1,
                        "suggestions": ["Team collaboration photo", "Group project meeting"],
                        "description": "Photos highlighting your teamwork and collaboration skills"
                    },
                    "project": {
                        "required": false,
                        "count": 1,
                        "suggestions": ["Project showcase", "Award ceremony", "Product launch"],
                        "description": "Photos demonstrating your achievements and project outcomes"
                    }
                }
            }
            
            Guidelines:
            - Extract only information that is clearly stated in the CV
            - If information is missing, use reasonable defaults or omit the field
            - Keep profileSummary under 500 characters
            - Generate 3-5 relevant skills based on the CV content
            - Calculate experience years based on work history
            - Use placeholder:// URLs for all photos (these will be replaced with actual photos later)
            - Make photo suggestions relevant to the person's profession and experience
            - Return ONLY the JSON object, no additional text or formatting
            """;
    }

    private AIProfileDto parseAIResponse(String response) {
        try {
            // Clean the response to extract JSON
            String jsonResponse = response.trim();
            if (jsonResponse.startsWith("```json")) {
                jsonResponse = jsonResponse.substring(7);
            }
            if (jsonResponse.endsWith("```")) {
                jsonResponse = jsonResponse.substring(0, jsonResponse.length() - 3);
            }
            jsonResponse = jsonResponse.trim();

            // Validate JSON structure
            JsonNode jsonNode = objectMapper.readTree(jsonResponse);
            
            // Create AIProfileDto
            return AIProfileDto.builder()
                    .id(UUID.randomUUID())
                    .aiAttributes(jsonResponse)
                    .status(AIProfile.AIProfileStatus.DRAFT)
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse AI response as JSON: {}", e.getMessage());
            log.debug("Raw AI response: {}", response);
            throw new RuntimeException("Invalid AI response format", e);
        }
    }

    private AIProfileDto generateMockProfile(String cvContent) {
        // Fallback mock profile generation
        String mockProfile = """
            {
                "name": "John Doe",
                "jobTitle": "Senior Software Engineer",
                "companies": [
                    {
                        "name": "Google",
                        "icon": "https://logo.clearbit.com/google.com",
                        "position": "Senior Software Engineer",
                        "duration": "2020-2024"
                    }
                ],
                "profileSummary": "Experienced software engineer with 5+ years of experience in full-stack development, specializing in React, Node.js, and cloud technologies.",
                "skills": ["React", "Node.js", "Python", "AWS", "Docker"],
                "experience": 5,
                "photos": {
                    "profilePhoto": "placeholder://profile-photo",
                    "coverPhoto": "placeholder://cover-photo",
                    "professional": ["placeholder://professional-1", "placeholder://professional-2"],
                    "team": ["placeholder://team-1"],
                    "project": ["placeholder://project-1"]
                },
                "photoSuggestions": {
                    "profilePhoto": {
                        "required": true,
                        "count": 1,
                        "suggestions": ["Professional headshot for LinkedIn profile"],
                        "description": "A clear, professional headshot that represents your personal brand"
                    },
                    "professional": {
                        "required": false,
                        "count": 2,
                        "suggestions": ["Team meeting photo", "Conference presentation"],
                        "description": "Photos showcasing your professional work environment"
                    },
                    "team": {
                        "required": false,
                        "count": 1,
                        "suggestions": ["Team collaboration photo"],
                        "description": "Photos highlighting your teamwork and collaboration skills"
                    },
                    "project": {
                        "required": false,
                        "count": 1,
                        "suggestions": ["Project showcase or award ceremony"],
                        "description": "Photos demonstrating your achievements and project outcomes"
                    }
                }
            }
            """;

        return AIProfileDto.builder()
                .id(UUID.randomUUID())
                .aiAttributes(mockProfile)
                .status(AIProfile.AIProfileStatus.DRAFT)
                .build();
    }
}