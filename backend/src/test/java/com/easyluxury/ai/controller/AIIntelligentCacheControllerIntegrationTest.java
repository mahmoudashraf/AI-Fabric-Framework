package com.easyluxury.ai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AI Intelligent Cache Controller Integration Test
 * 
 * Comprehensive integration tests for AI intelligent caching endpoints
 * including cache statistics, cache management, and cache operations.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@SpringBootTest(classes = {com.easyluxury.EasyLuxuryApplication.class, com.ai.infrastructure.config.AIInfrastructureAutoConfiguration.class})
@AutoConfigureWebMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "ai.providers.openai-api-key=test-key",
    "ai.providers.openai-model=gpt-4o-mini",
    "ai.service.enabled=true"
})
class AIIntelligentCacheControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    private void setupMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void testGetCacheStatistics() throws Exception {
        setupMockMvc();
        
        mockMvc.perform(get("/api/v1/ai/cache/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cacheHits").exists())
                .andExpect(jsonPath("$.cacheMisses").exists())
                .andExpect(jsonPath("$.cacheEvictions").exists())
                .andExpect(jsonPath("$.hitRate").exists())
                .andExpect(jsonPath("$.contentGenerationCacheSize").exists())
                .andExpect(jsonPath("$.embeddingCacheSize").exists())
                .andExpect(jsonPath("$.searchCacheSize").exists())
                .andExpect(jsonPath("$.totalCacheSize").exists());
    }

    @Test
    void testClearAllCaches() throws Exception {
        setupMockMvc();
        
        mockMvc.perform(post("/api/v1/ai/cache/clear"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("All AI caches cleared successfully"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void testGetCacheHealth() throws Exception {
        setupMockMvc();
        
        mockMvc.perform(get("/api/v1/ai/cache/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.hitRate").exists())
                .andExpect(jsonPath("$.totalRequests").exists())
                .andExpect(jsonPath("$.cacheSize").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void testGetCacheConfiguration() throws Exception {
        setupMockMvc();
        
        mockMvc.perform(get("/api/v1/ai/cache/configuration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxCacheSize").value(1000))
                .andExpect(jsonPath("$.cacheTtlMs").value(3600000))
                .andExpect(jsonPath("$.similarityThreshold").value(0.95))
                .andExpect(jsonPath("$.cacheTypes").exists())
                .andExpect(jsonPath("$.evictionPolicy").value("LRU"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void testWarmUpCache() throws Exception {
        setupMockMvc();
        
        mockMvc.perform(post("/api/v1/ai/cache/warmup"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Cache warm-up initiated successfully"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void testCacheStatisticsAfterClear() throws Exception {
        setupMockMvc();
        
        // Clear caches first
        mockMvc.perform(post("/api/v1/ai/cache/clear"))
                .andExpect(status().isOk());
        
        // Check statistics after clear
        mockMvc.perform(get("/api/v1/ai/cache/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cacheHits").value(0))
                .andExpect(jsonPath("$.cacheMisses").value(0))
                .andExpect(jsonPath("$.cacheEvictions").value(0))
                .andExpect(jsonPath("$.hitRate").value(0.0))
                .andExpect(jsonPath("$.totalCacheSize").value(0));
    }

    @Test
    void testCacheHealthStatusValues() throws Exception {
        setupMockMvc();
        
        mockMvc.perform(get("/api/v1/ai/cache/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(org.hamcrest.Matchers.oneOf("HEALTHY", "WARNING", "CRITICAL")))
                .andExpect(jsonPath("$.hitRate").isNumber())
                .andExpect(jsonPath("$.totalRequests").isNumber())
                .andExpect(jsonPath("$.cacheSize").isNumber());
    }

    @Test
    void testCacheConfigurationStructure() throws Exception {
        setupMockMvc();
        
        mockMvc.perform(get("/api/v1/ai/cache/configuration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cacheTypes").isArray())
                .andExpect(jsonPath("$.cacheTypes[0]").value("content-generation"))
                .andExpect(jsonPath("$.cacheTypes[1]").value("embedding-generation"))
                .andExpect(jsonPath("$.cacheTypes[2]").value("semantic-search"));
    }
}