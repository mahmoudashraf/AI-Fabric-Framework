package com.ai.infrastructure.integration;

import com.ai.infrastructure.annotation.AICapable;
import com.ai.infrastructure.annotation.AIProcess;
import com.ai.infrastructure.config.AIEntityConfigurationLoader;
import com.ai.infrastructure.config.AIInfrastructureAutoConfiguration;
import com.ai.infrastructure.dto.AIEntityConfig;
import com.ai.infrastructure.service.AICapabilityService;
import com.ai.infrastructure.aspect.AICapableAspect;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Spring Boot Integration Tests for AI Infrastructure Module
 * Tests the full Spring context loading and bean configuration
 */
@SpringBootTest(classes = {AIInfrastructureAutoConfiguration.class})
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false",
    "logging.level.com.ai.infrastructure=DEBUG"
})
@SpringJUnitConfig
public class AIInfrastructureSpringBootIntegrationTest {

    @Autowired(required = false)
    private AIEntityConfigurationLoader configLoader;

    @Autowired(required = false)
    private AICapabilityService capabilityService;

    @Autowired(required = false)
    private AICapableAspect aspect;

    @Test
    public void testSpringContextLoads() {
        assertNotNull(configLoader, "AIEntityConfigurationLoader should be loaded");
        assertNotNull(capabilityService, "AICapabilityService should be loaded");
        assertNotNull(aspect, "AICapableAspect should be loaded");
    }

    @Test
    public void testConfigurationLoaderIntegration() {
        assertNotNull(configLoader);
        
        AIEntityConfig productConfig = configLoader.getEntityConfig("product");
        assertNotNull(productConfig, "Product configuration should be loaded");
        assertEquals("product", productConfig.getEntityType());
        assertTrue(productConfig.getSearchableFields().size() > 0, "Product should have searchable fields");
    }

    @Test
    public void testAICapabilityServiceIntegration() {
        assertNotNull(capabilityService);
        
        // Test that the service can process entities
        assertTrue(capabilityService.getSupportedEntityTypes().contains("product"));
        assertTrue(capabilityService.getSupportedEntityTypes().contains("user"));
        assertTrue(capabilityService.getSupportedEntityTypes().contains("order"));
    }

    @Test
    public void testAspectIntegration() {
        assertNotNull(aspect);
        // Aspect should be properly configured for AOP
        assertTrue(aspect.getClass().getSimpleName().contains("Aspect"));
    }
}