package com.easyluxury.ai.facade;

import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.dto.AIGenerationRequest;
import com.ai.infrastructure.dto.AIGenerationResponse;
import com.ai.infrastructure.dto.AISearchRequest;
import com.ai.infrastructure.dto.AISearchResponse;
import com.easyluxury.ai.config.EasyLuxuryAIConfig.EasyLuxuryAISettings;
import com.easyluxury.entity.User;
import com.easyluxury.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AIFacadeTest {

    @Mock
    private AICoreService aiCoreService;

    @Mock
    private EasyLuxuryAISettings aiSettings;

    @Mock
    private UserMapper userMapper;

    private AIFacade aiFacade;

    @BeforeEach
    void setUp() {
        aiFacade = new AIFacade(aiCoreService, aiSettings, userMapper);
    }

    @Test
    void testSearchProducts() {
        // Given
        String query = "luxury watch";
        int limit = 10;
        
        AISearchResponse mockResponse = AISearchResponse.builder()
            .results(List.of(
                Map.of("name", "Luxury Watch 1", "description", "Premium watch", "price", 1000),
                Map.of("name", "Luxury Watch 2", "description", "Exclusive watch", "price", 2000)
            ))
            .build();
        
        when(aiCoreService.performSearch(any(AISearchRequest.class))).thenReturn(mockResponse);

        // When
        List<Map<String, Object>> results = aiFacade.searchProducts(query, limit);

        // Then
        assertNotNull(results);
        assertEquals(2, results.size());
        assertEquals("Luxury Watch 1", results.get(0).get("name"));
        assertEquals("Luxury Watch 2", results.get(1).get("name"));
        
        verify(aiCoreService).performSearch(any(AISearchRequest.class));
    }

    @Test
    void testGetProductRecommendations() {
        // Given
        User user = new User();
        user.setId(java.util.UUID.randomUUID());
        user.setFirstName("John");
        user.setLastName("Doe");
        user.setEmail("john.doe@example.com");
        
        int limit = 5;
        List<Map<String, Object>> mockRecommendations = List.of(
            Map.of("name", "Recommended Product 1", "description", "AI recommended product"),
            Map.of("name", "Recommended Product 2", "description", "Another AI recommended product")
        );
        
        when(aiCoreService.generateRecommendations(anyString(), anyString(), anyInt()))
            .thenReturn(mockRecommendations);

        // When
        List<Map<String, Object>> recommendations = aiFacade.getProductRecommendations(user, limit);

        // Then
        assertNotNull(recommendations);
        assertEquals(2, recommendations.size());
        assertEquals("Recommended Product 1", recommendations.get(0).get("name"));
        assertEquals("Recommended Product 2", recommendations.get(1).get("name"));
        
        verify(aiCoreService).generateRecommendations("Product", "User: John Doe\nEmail: john.doe@example.com\n", limit);
    }

    @Test
    void testGenerateProductDescription() {
        // Given
        Map<String, Object> productData = Map.of(
            "name", "Luxury Watch",
            "description", "A premium timepiece",
            "price", 1500
        );
        
        AIGenerationResponse mockResponse = AIGenerationResponse.builder()
            .content("This exquisite luxury watch represents the pinnacle of craftsmanship and elegance...")
            .build();
        
        when(aiCoreService.generateContent(any(AIGenerationRequest.class))).thenReturn(mockResponse);

        // When
        String description = aiFacade.generateProductDescription(productData);

        // Then
        assertNotNull(description);
        assertTrue(description.contains("luxury watch"));
        assertTrue(description.contains("craftsmanship"));
        
        verify(aiCoreService).generateContent(any(AIGenerationRequest.class));
    }

    @Test
    void testValidateProduct() {
        // Given
        Map<String, Object> productData = Map.of(
            "name", "Test Product",
            "description", "A test product description",
            "price", 100
        );
        
        Map<String, Object> mockValidation = Map.of(
            "valid", true,
            "suggestions", List.of("Consider adding more luxury keywords"),
            "score", 0.85
        );
        
        when(aiCoreService.validateContent(anyString(), any(Map.class))).thenReturn(mockValidation);

        // When
        Map<String, Object> validation = aiFacade.validateProduct(productData);

        // Then
        assertNotNull(validation);
        assertTrue((Boolean) validation.get("valid"));
        assertNotNull(validation.get("suggestions"));
        assertEquals(0.85, validation.get("score"));
        
        verify(aiCoreService).validateContent(anyString(), any(Map.class));
    }

    @Test
    void testGenerateUserInsights() {
        // Given
        User user = new User();
        user.setId(java.util.UUID.randomUUID());
        user.setFirstName("Jane");
        user.setLastName("Smith");
        user.setEmail("jane.smith@example.com");
        
        AIGenerationResponse mockResponse = AIGenerationResponse.builder()
            .content("User shows strong preference for luxury items and high-end products...")
            .build();
        
        when(aiCoreService.generateContent(any(AIGenerationRequest.class))).thenReturn(mockResponse);

        // When
        Map<String, Object> insights = aiFacade.generateUserInsights(user);

        // Then
        assertNotNull(insights);
        assertTrue(insights.containsKey("insights"));
        assertTrue(insights.containsKey("generatedAt"));
        assertTrue(insights.containsKey("userId"));
        assertTrue(((String) insights.get("insights")).contains("luxury items"));
        assertNotNull(insights.get("userId"));
        
        verify(aiCoreService).generateContent(any(AIGenerationRequest.class));
    }

    @Test
    void testBuildUserContext() {
        // Given
        User user = new User();
        user.setFirstName("John");
        user.setLastName("Doe");
        user.setEmail("john.doe@example.com");

        // When
        String context = aiFacade.buildUserContext(user);

        // Then
        assertNotNull(context);
        assertTrue(context.contains("John Doe"));
        assertTrue(context.contains("john.doe@example.com"));
    }

    @Test
    void testBuildProductDescriptionPrompt() {
        // Given
        Map<String, Object> productData = Map.of(
            "name", "Luxury Watch",
            "description", "A premium timepiece",
            "price", 1500
        );

        // When
        String prompt = aiFacade.buildProductDescriptionPrompt(productData);

        // Then
        assertNotNull(prompt);
        assertTrue(prompt.contains("Luxury Watch"));
        assertTrue(prompt.contains("A premium timepiece"));
        assertTrue(prompt.contains("1500"));
    }

    @Test
    void testBuildProductValidationContent() {
        // Given
        Map<String, Object> productData = Map.of(
            "name", "Test Product",
            "description", "Test description",
            "price", 100
        );

        // When
        String content = aiFacade.buildProductValidationContent(productData);

        // Then
        assertNotNull(content);
        assertTrue(content.contains("Test Product"));
        assertTrue(content.contains("Test description"));
        assertTrue(content.contains("100"));
    }

    @Test
    void testBuildProductValidationRules() {
        // When
        Map<String, Object> rules = aiFacade.buildProductValidationRules();

        // Then
        assertNotNull(rules);
        assertTrue((Boolean) rules.get("nameRequired"));
        assertEquals(10, rules.get("descriptionMinLength"));
        assertTrue((Boolean) rules.get("pricePositive"));
        assertTrue(rules.containsKey("luxuryKeywords"));
    }

    @Test
    void testBuildUserInsightsPrompt() {
        // Given
        User user = new User();
        user.setFirstName("Jane");
        user.setLastName("Smith");
        user.setEmail("jane.smith@example.com");

        // When
        String prompt = aiFacade.buildUserInsightsPrompt(user);

        // Then
        assertNotNull(prompt);
        assertTrue(prompt.contains("Jane Smith"));
        assertTrue(prompt.contains("jane.smith@example.com"));
    }
}