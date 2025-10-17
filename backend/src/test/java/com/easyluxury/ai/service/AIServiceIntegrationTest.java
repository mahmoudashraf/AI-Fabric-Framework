package com.easyluxury.ai.service;

import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.dto.AIGenerationRequest;
import com.ai.infrastructure.dto.AIGenerationResponse;
import com.easyluxury.ai.facade.AIFacade;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = {com.easyluxury.EasyLuxuryApplication.class, com.ai.infrastructure.config.AIInfrastructureAutoConfiguration.class})
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "ai.providers.openai-api-key=test-key",
    "ai.providers.openai-model=gpt-4o-mini",
    "ai.providers.openai-embedding-model=text-embedding-3-small",
    "ai.service.enabled=true"
})
class AIServiceIntegrationTest {

    @Autowired
    private AICoreService aiCoreService;

    @Autowired
    private AIFacade aiFacade;

    @Test
    void testAICoreServiceContentGeneration() {
        AIGenerationRequest request = AIGenerationRequest.builder()
            .prompt("Generate a product description for a luxury watch")
            .model("gpt-4o-mini")
            .maxTokens(200)
            .temperature(0.7)
            .build();

        AIGenerationResponse response = aiCoreService.generateContent(request);

        assertNotNull(response);
        assertNotNull(response.getContent());
        assertFalse(response.getContent().isEmpty());
        assertEquals("gpt-4o-mini", response.getModel());
        assertNotNull(response.getRequestId());
        assertTrue(response.getProcessingTimeMs() > 0);
    }

    @Test
    void testAIFacadeContentGeneration() {
        // Test AI facade content generation using the correct DTO
        com.easyluxury.ai.dto.AIGenerationRequest request = com.easyluxury.ai.dto.AIGenerationRequest.builder()
            .prompt("Generate a marketing tagline for luxury products")
            .model("gpt-4o-mini")
            .maxTokens(200)
            .temperature(0.7)
            .build();

        com.easyluxury.ai.dto.AIGenerationResponse response = aiFacade.generateContent(request);

        assertNotNull(response);
        assertNotNull(response.getContent());
        assertFalse(response.getContent().isEmpty());
        assertTrue(response.getContent().length() > 10);
    }
}
