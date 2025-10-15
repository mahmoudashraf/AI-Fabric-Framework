package com.easyluxury.config;

import com.theokanning.openai.service.OpenAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class OpenAIConfig {

    @Value("${openai.api-key:}")
    private String apiKey;

    @Value("${openai.timeout:60}")
    private int timeoutSeconds;

    @Bean
    public OpenAiService openAiService() {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.warn("OpenAI API key not configured. AI features will use mock data.");
            return null;
        }

        try {
            return new OpenAiService(apiKey, Duration.ofSeconds(timeoutSeconds));
        } catch (Exception e) {
            log.error("Failed to initialize OpenAI service: {}", e.getMessage());
            return null;
        }
    }
}