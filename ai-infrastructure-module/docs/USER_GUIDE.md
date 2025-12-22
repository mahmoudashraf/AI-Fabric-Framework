# AI Fabric Framework - User Guide

## Overview

The AI Fabric Framework is a comprehensive, high-performance library that weaves AI capabilities into any Spring Boot application. It offers annotation-driven AI processing, configuration-based entity management, and security-first operations. One of its core strengths is **Automatic Vector Synchronization**, ensuring that your vector indices always stay in lock-step with your database changes.

## Table of Contents

1. [Quick Start](#quick-start)
2. [Configuration](#configuration)
3. [Annotations](#annotations)
4. [Services](#services)
5. [Performance Optimization](#performance-optimization)
6. [Testing](#testing)
7. [Troubleshooting](#troubleshooting)
8. [Best Practices](#best-practices)

## Quick Start

### 1. Add Dependency

Add the AI Fabric Framework to your `pom.xml`:

```xml
<dependency>
    <groupId>com.ai.fabric</groupId>
    <artifactId>ai-fabric-core</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. Enable Auto-Configuration

Add the following to your `application.yml`:

```yaml
ai:
  config:
    default-file: ai-entity-config.yml
  openai:
    api-key: ${OPENAI_API_KEY}
    base-url: https://api.openai.com/v1
```

### 3. Create Entity Configuration

Create `ai-entity-config.yml` in your `src/main/resources`:

```yaml
ai-entities:
  product:
    entity-type: "product"
    features: ["embedding", "search", "rag", "recommendation"]
    auto-process: true
    enable-search: true
    enable-recommendations: true
    auto-embedding: true
    indexable: true
    
    searchable-fields:
      - name: "name"
        include-in-rag: true
        enable-semantic-search: true
        weight: 1.0
      - name: "description"
        include-in-rag: true
        enable-semantic-search: true
        weight: 0.8
    
    embeddable-fields:
      - name: "description"
        model: "text-embedding-3-small"
        auto-generate: true
        include-in-similarity: true
    
    metadata-fields:
      - name: "category"
        type: "TEXT"
        include-in-search: true
      - name: "price"
        type: "NUMERIC"
        include-in-search: false
```

### 4. Annotate Your Entities

```java
@Entity
@AICapable(entityType = "product")
public class Product {
    @Id
    private UUID id;
    
    private String name;
    private String description;
    private String category;
    private BigDecimal price;
    
    @AIProcess(operation = "CREATE")
    public void save() {
        // Your save logic
    }
}
```

## Configuration

### Entity Configuration Schema

The `ai-entity-config.yml` file defines how entities should be processed for AI capabilities:

```yaml
ai-entities:
  <entity-type>:
    entity-type: "string"           # Entity type identifier
    features: ["embedding", "search", "rag", "recommendation"]
    auto-process: true              # Enable automatic processing
    enable-search: true             # Enable search indexing
    enable-recommendations: true    # Enable recommendation generation
    auto-embedding: true            # Enable automatic embedding generation
    indexable: true                 # Enable search indexing
    
    # Searchable fields configuration
    searchable-fields:
      - name: "fieldName"
        include-in-rag: true
        enable-semantic-search: true
        weight: 1.0
    
    # Embeddable fields configuration
    embeddable-fields:
      - name: "fieldName"
        model: "text-embedding-3-small"
        auto-generate: true
        include-in-similarity: true
    
    # Metadata fields configuration
    metadata-fields:
      - name: "fieldName"
        type: "TEXT|NUMERIC|DATE"
        include-in-search: true
```

### Global Configuration

```yaml
ai:
  config:
    default-file: ai-entity-config.yml
    hot-reload: true
    reload-interval: 300
  
  openai:
    api-key: ${OPENAI_API_KEY}
    base-url: https://api.openai.com/v1
    timeout: 30000
    max-retries: 3
  
  performance:
    cache-enabled: true
    cache-ttl: 3600
    batch-size: 100
    thread-pool-size: 10
```

## Annotations

### @AICapable

Marks an entity as AI-capable:

```java
@AICapable(entityType = "product")
public class Product {
    // Entity fields
}
```

**Parameters:**
- `entityType`: The entity type identifier (must match configuration)

### @AIProcess

Marks methods that should trigger AI processing:

```java
@AIProcess(operation = "CREATE")
public void save() {
    // Save logic
}

@AIProcess(operation = "UPDATE")
public void update() {
    // Update logic
}

@AIProcess(operation = "DELETE")
public void delete() {
    // Delete logic
}
```

**Parameters:**
- `operation`: The CRUD operation (CREATE, READ, UPDATE, DELETE)

## Services

### AICapabilityService

Core service for AI processing:

```java
@Autowired
private AICapabilityService aiCapabilityService;

// Process entity for AI
aiCapabilityService.processEntityForAI(entity, "product");

// Generate embeddings
aiCapabilityService.generateEmbeddings(entity, config);

// Index for search
aiCapabilityService.indexForSearch(entity, config);

// Analyze entity
aiCapabilityService.analyzeEntity(entity, config);
```

### AIPerformanceService

Performance optimization service:

```java
@Autowired
private AIPerformanceService performanceService;

// Generate embedding with caching
List<Double> embedding = performanceService.generateEmbeddingWithCache(request);

// Generate embedding asynchronously
CompletableFuture<List<Double>> future = performanceService.generateEmbeddingAsync(request);

// Batch processing
List<List<Double>> embeddings = performanceService.generateEmbeddingsBatch(requests);

// Performance metrics
AIPerformanceResult result = performanceService.generateEmbeddingWithMetrics(request);
```

### AIEmbeddingService

Embedding generation service:

```java
@Autowired
private AIEmbeddingService embeddingService;

AIEmbeddingRequest request = AIEmbeddingRequest.builder()
    .text("Sample text for embedding")
    .model("text-embedding-3-small")
    .build();

AIEmbeddingResponse response = embeddingService.generateEmbedding(request);
List<Double> embedding = response.getEmbedding();
```

## Performance Optimization

### Caching

The module provides built-in caching for embedding generation:

```yaml
ai:
  performance:
    cache-enabled: true
    cache-ttl: 3600  # Cache TTL in seconds
```

### Batch Processing

Process multiple entities efficiently:

```java
List<AIEmbeddingRequest> requests = entities.stream()
    .map(entity -> AIEmbeddingRequest.builder()
        .text(extractText(entity))
        .model("text-embedding-3-small")
        .build())
    .toList();

List<List<Double>> embeddings = performanceService.generateEmbeddingsBatch(requests);
```

### Async Processing

Use async processing for non-blocking operations:

```java
@Async
public CompletableFuture<Void> processEntityAsync(Entity entity) {
    return CompletableFuture.runAsync(() -> {
        aiCapabilityService.processEntityForAI(entity, "product");
    });
}
```

## Testing

### Unit Tests

```java
@SpringBootTest
@ActiveProfiles("test")
class AITest {
    
    @Autowired
    private AICapabilityService aiCapabilityService;
    
    @Test
    void testEntityProcessing() {
        Product product = createTestProduct();
        
        assertDoesNotThrow(() -> {
            aiCapabilityService.processEntityForAI(product, "product");
        });
    }
}
```

### Integration Tests

```java
@SpringBootTest
@ActiveProfiles("real-api-test")
class AIIntegrationTest {
    
    @Test
    void testRealAPIIntegration() {
        // Test with real OpenAI API
        Product product = createTestProduct();
        aiCapabilityService.processEntityForAI(product, "product");
        
        // Verify results
        assertNotNull(product.getEmbeddings());
    }
}
```

## Troubleshooting

### Common Issues

1. **Configuration not loaded**
   - Check `ai-entity-config.yml` syntax
   - Verify file location in `src/main/resources`
   - Check Spring profile configuration

2. **Metadata fields null**
   - Ensure configuration has `metadata-fields` section
   - Check entity type matches configuration
   - Verify configuration loader is working

3. **Embedding generation fails**
   - Check OpenAI API key configuration
   - Verify network connectivity
   - Check API rate limits

4. **Performance issues**
   - Enable caching
   - Use batch processing
   - Check thread pool configuration

### Debug Logging

Enable debug logging:

```yaml
logging:
  level:
    com.ai.infrastructure: DEBUG
```

### Health Checks

The module provides health check endpoints:

```bash
curl http://localhost:8080/actuator/health/ai
```

## Best Practices

### 1. Entity Design

- Use meaningful entity types
- Include relevant searchable fields
- Keep metadata fields minimal
- Use appropriate field weights

### 2. Configuration

- Use environment-specific configurations
- Enable hot-reload for development
- Monitor configuration changes
- Validate configuration on startup

### 3. Performance

- Enable caching for production
- Use batch processing for bulk operations
- Monitor cache hit rates
- Set appropriate TTL values

### 4. Error Handling

- Implement proper exception handling
- Use circuit breakers for external APIs
- Log errors appropriately
- Provide fallback mechanisms

### 5. Security

- Secure API keys
- Use environment variables
- Implement rate limiting
- Monitor API usage

## Examples

### Complete Product Entity

```java
@Entity
@AICapable(entityType = "product")
public class Product {
    @Id
    private UUID id;
    
    private String name;
    private String description;
    private String category;
    private BigDecimal price;
    private String brand;
    
    @AIProcess(operation = "CREATE")
    public void save() {
        // Save logic
    }
    
    @AIProcess(operation = "UPDATE")
    public void update() {
        // Update logic
    }
    
    @AIProcess(operation = "DELETE")
    public void delete() {
        // Delete logic
    }
}
```

### Service Integration

```java
@Service
public class ProductService {
    
    @Autowired
    private AICapabilityService aiCapabilityService;
    
    @Autowired
    private AIPerformanceService performanceService;
    
    public Product saveProduct(Product product) {
        // Save product
        product = productRepository.save(product);
        
        // Process for AI
        aiCapabilityService.processEntityForAI(product, "product");
        
        return product;
    }
    
    public List<Product> searchProducts(String query) {
        // Use AI search capabilities
        return aiCapabilityService.searchEntities(query, "product");
    }
}
```

## Support

For issues and questions:

1. Check the troubleshooting section
2. Review the configuration examples
3. Enable debug logging
4. Check the health endpoints

## Version History

- **1.0.0**: Initial release with core AI capabilities
- **1.1.0**: Added performance optimizations
- **1.2.0**: Enhanced configuration management
