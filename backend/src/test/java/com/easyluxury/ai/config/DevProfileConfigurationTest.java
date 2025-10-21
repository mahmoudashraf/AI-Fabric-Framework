package com.easyluxury.ai.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Test to verify dev profile configuration works correctly
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@SpringBootTest
@ActiveProfiles("dev")
class DevProfileConfigurationTest {
    
    @Test
    void contextLoads() {
        // This test will pass if the Spring context loads successfully with dev profile
        // If there are configuration issues, the context will fail to load
    }
}
