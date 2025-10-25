package com.ai.infrastructure.it;

import com.ai.infrastructure.service.AICapabilityService;
import com.ai.infrastructure.config.AIEntityConfigurationLoader;
import com.ai.infrastructure.dto.AIEntityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("real-api-test")
public class MetadataFixTest {
    
    @Autowired
    private AICapabilityService capabilityService;
    
    @Autowired
    private AIEntityConfigurationLoader configurationLoader;
    
    @Test
    public void testConfigurationLoaderHasMetadataFields() {
        // Test that configuration loader properly loads metadata fields
        AIEntityConfig config = configurationLoader.getEntityConfig("test-product");
        assertNotNull(config, "Configuration should not be null");
        assertNotNull(config.getMetadataFields(), "Metadata fields should not be null");
        assertFalse(config.getMetadataFields().isEmpty(), "Metadata fields should not be empty");
        
        System.out.println("Metadata fields count: " + config.getMetadataFields().size());
        config.getMetadataFields().forEach(field ->
            System.out.println("Field: " + field.getName() + " Type: " + field.getType())
        );
    }
    
    @Test
    public void testProcessEntityForAIWithMetadata() {
        // Create a simple test entity
        TestProduct product = new TestProduct();
        product.setId("test-1");
        product.setName("Test Product");
        product.setDescription("Test Description");
        product.setCategory("Electronics");
        product.setPrice(99.99);
        product.setBrand("TestBrand");
        
        // This should not throw a NullPointerException
        assertDoesNotThrow(() -> {
            capabilityService.processEntityForAI(product, "test-product");
        }, "processEntityForAI should not throw NullPointerException for metadata fields");
    }
    
    // Simple test entity class
    public static class TestProduct {
        private String id;
        private String name;
        private String description;
        private String category;
        private Double price;
        private String brand;
        
        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        
        public Double getPrice() { return price; }
        public void setPrice(Double price) { this.price = price; }
        
        public String getBrand() { return brand; }
        public void setBrand(String brand) { this.brand = brand; }
    }
}
