# Phase 1, Batch 1: Create Maven Module Structure

## Overview
Create the foundational Maven module structure for `ai-infrastructure-spring-boot-starter` with proper dependencies, configuration, and build setup.

## Tickets
- **P1.1-A**: Create Maven Module Structure
- **P1.1-B**: Implement AICoreService with OpenAI Integration

## Backend Changes

### New Maven Module Structure
```
ai-infrastructure-spring-boot-starter/
├── pom.xml
├── src/main/java/com/ai/infrastructure/
│   ├── annotation/
│   ├── core/
│   ├── config/
│   ├── dto/
│   └── exception/
└── src/main/resources/
    ├── META-INF/spring.factories
    └── application-ai.yml
```

### Dependencies
```xml
<dependencies>
    <!-- Spring Boot Starters -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    
    <!-- AI Providers -->
    <dependency>
        <groupId>com.theokanning.openai-gpt3-java</groupId>
        <artifactId>service</artifactId>
        <version>0.18.2</version>
    </dependency>
    
    <!-- Utilities -->
    <dependency>
        <groupId>org.mapstruct</groupId>
        <artifactId>mapstruct</artifactId>
        <version>1.5.5.Final</version>
    </dependency>
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
    </dependency>
</dependencies>
```

### Core Services

#### AICoreService
```java
@Service
@RequiredArgsConstructor
public class AICoreService {
    private final AIProviderConfig aiProviderConfig;
    private final AIEmbeddingService embeddingService;
    private final AISearchService searchService;
    
    public AIGenerationResponse generateContent(AIGenerationRequest request) {
        // OpenAI integration for content generation
    }
    
    public AIEmbeddingResponse generateEmbedding(AIEmbeddingRequest request) {
        // Embedding generation using OpenAI
    }
    
    public AISearchResponse performSearch(AISearchRequest request) {
        // Semantic search functionality
    }
}
```

#### AIProviderConfig
```java
@ConfigurationProperties(prefix = "ai.providers")
public class AIProviderConfig {
    private String openaiApiKey;
    private String openaiModel = "gpt-4o-mini";
    private String openaiEmbeddingModel = "text-embedding-3-small";
    private Integer openaiMaxTokens = 2000;
    private Double openaiTemperature = 0.3;
    private Integer openaiTimeout = 60;
}
```

### Auto-Configuration
```java
@Configuration
@EnableConfigurationProperties(AIProviderConfig.class)
@ConditionalOnProperty(prefix = "ai", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AIInfrastructureAutoConfiguration {
    
    @Bean
    public AICoreService aiCoreService(AIProviderConfig config) {
        return new AICoreService(config, aiEmbeddingService(config), aiSearchService(config));
    }
    
    @Bean
    public AIEmbeddingService aiEmbeddingService(AIProviderConfig config) {
        return new AIEmbeddingService(config);
    }
    
    @Bean
    public AISearchService aiSearchService(AIProviderConfig config) {
        return new AISearchService(config);
    }
}
```

### DTOs

#### AIGenerationRequest
```java
@Data
@Builder
public class AIGenerationRequest {
    @NotBlank(message = "Prompt cannot be blank")
    @Size(max = 4000, message = "Prompt cannot exceed 4000 characters")
    private String prompt;
    
    private String systemPrompt;
    private String model;
    private Integer maxTokens;
    private Double temperature;
    private String context;
    private String entityType;
    private String purpose;
}
```

#### AIGenerationResponse
```java
@Data
@Builder
public class AIGenerationResponse {
    private String content;
    private String model;
    private Usage usage;
    private Long processingTimeMs;
    private String requestId;
}
```

### Configuration Files

#### META-INF/spring.factories
```
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
com.ai.infrastructure.config.AIInfrastructureAutoConfiguration
```

#### application-ai.yml
```yaml
ai:
  enabled: true
  providers:
    openai:
      api-key: ${OPENAI_API_KEY:}
      model: gpt-4o-mini
      embedding-model: text-embedding-3-small
      max-tokens: 2000
      temperature: 0.3
      timeout: 60
  features:
    rag:
      enabled: true
      auto-index: true
    behavioral:
      enabled: true
      tracking: true
    validation:
      enabled: true
      smart-validation: true
```

## Testing

### Unit Tests
- AICoreServiceTest
- AIProviderConfigTest
- AIGenerationRequestTest
- AIGenerationResponseTest

### Integration Tests
- AIIntegrationTest with Testcontainers
- OpenAI API integration tests
- Configuration validation tests

### Test Coverage
- Target: 90%+ unit test coverage
- All public methods tested
- Error scenarios covered
- Configuration scenarios tested

## Acceptance Criteria

### P1.1-A: Create Maven Module Structure
- [ ] Maven module structure is created
- [ ] Dependencies are properly configured
- [ ] Spring Boot starter auto-configuration works
- [ ] Module can be built and packaged
- [ ] Basic package structure is established

### P1.1-B: Implement AICoreService with OpenAI Integration
- [ ] AICoreService generates content successfully
- [ ] Embeddings are created and stored
- [ ] Semantic search returns relevant results
- [ ] Error handling and retry logic works
- [ ] Rate limiting prevents API abuse

## Risks and Mitigation

### Risks
1. **OpenAI API Rate Limits**: Implement proper rate limiting and retry logic
2. **Configuration Complexity**: Use Spring Boot configuration properties
3. **Dependency Management**: Use Spring Boot dependency management
4. **Testing Challenges**: Use Testcontainers for integration tests

### Mitigation
1. **Rate Limiting**: Implement exponential backoff and circuit breaker
2. **Configuration**: Use @ConfigurationProperties for type-safe configuration
3. **Dependencies**: Use Spring Boot BOM for dependency management
4. **Testing**: Use Testcontainers and mock services for testing

## Deliverables

### Code
- Complete Maven module structure
- AICoreService implementation
- AIProviderConfig configuration
- Auto-configuration setup
- DTOs and request/response models

### Documentation
- Module README
- Configuration guide
- Usage examples
- API documentation

### Testing
- Unit tests (90%+ coverage)
- Integration tests
- Configuration tests
- Error handling tests

## Timeline
- **Duration**: 1 week
- **Start**: Week 1 of Phase 1
- **End**: End of Week 1

## Dependencies
- OpenAI API key
- Spring Boot 3.x
- Java 21
- Maven 3.8+
