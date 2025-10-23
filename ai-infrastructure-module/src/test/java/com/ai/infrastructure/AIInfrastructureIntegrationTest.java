package com.ai.infrastructure;

import com.ai.infrastructure.annotation.AICapable;
import com.ai.infrastructure.annotation.AIProcess;
import com.ai.infrastructure.config.AIEntityConfigurationLoader;
import com.ai.infrastructure.service.AICapabilityService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AI Infrastructure Integration Test
 * 
 * Tests the complete AI infrastructure functionality.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
class AIInfrastructureIntegrationTest {
    
    @Test
    void testConfigurationLoading() {
        // Test configuration loading
        AIEntityConfigurationLoader loader = new AIEntityConfigurationLoader(null);
        assertNotNull(loader);
    }
    
    @Test
    void testAICapabilityService() {
        // Test AI capability service
        AICapabilityService service = new AICapabilityService(null, null, null);
        assertNotNull(service);
    }
    
    @Test
    void testAICapableAnnotation() {
        // Test @AICapable annotation
        AICapable annotation = TestEntity.class.getAnnotation(AICapable.class);
        assertNotNull(annotation);
        assertEquals("test", annotation.entityType());
    }
    
    @Test
    void testAIProcessAnnotation() throws NoSuchMethodException {
        // Test @AIProcess annotation
        AIProcess annotation = TestService.class.getMethod("testMethod").getAnnotation(AIProcess.class);
        assertNotNull(annotation);
        assertEquals("create", annotation.processType());
    }
    
    @AICapable(entityType = "test")
    static class TestEntity {
        private String id;
        private String name;
    }
    
    static class TestService {
        @AIProcess(processType = "create")
        public void testMethod() {
            // Test method
        }
    }
}
