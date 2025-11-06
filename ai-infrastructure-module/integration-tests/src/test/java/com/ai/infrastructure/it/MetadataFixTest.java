package com.ai.infrastructure.it;

import com.ai.infrastructure.service.AICapabilityService;
import com.ai.infrastructure.config.AIEntityConfigurationLoader;
import com.ai.infrastructure.dto.AIEntityConfig;
import com.ai.infrastructure.it.entity.TestProduct;
import com.ai.infrastructure.it.repository.TestProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
@Transactional
public class MetadataFixTest {
    
    @Autowired
    private AICapabilityService capabilityService;
    
    @Autowired
    private AIEntityConfigurationLoader configurationLoader;
    
    @Autowired
    private TestProductRepository productRepository;
    
    @BeforeEach
    public void setUp() {
        // Clean up before each test
        productRepository.deleteAll();
    }
    
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
        // Create and save a test product entity
        TestProduct savedProduct = productRepository.save(TestProduct.builder()
            .name("Test Product")
            .description("Test Description")
            .category("Electronics")
            .price(new BigDecimal("99.99"))
            .brand("TestBrand")
            .active(true)
            .build());
        
        // This should not throw a NullPointerException or transaction rollback exception
        final TestProduct product = savedProduct;
        assertDoesNotThrow(() -> {
            capabilityService.processEntityForAI(product, "test-product");
        }, "processEntityForAI should not throw NullPointerException for metadata fields");
    }
}
