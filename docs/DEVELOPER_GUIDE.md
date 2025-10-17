# Developer Guide - Easy Luxury AI Integration
## Complete Development Workflow & Best Practices

---

## 🚀 **Quick Start Guide**

### **Prerequisites**
- Java 21+
- Maven 3.8+
- Node.js 18+
- Git

### **Setup Instructions**
```bash
# 1. Clone repository
git clone <repository-url>
cd easy-luxury

# 2. Switch to AI branch
git checkout AI-Enablement-Phase1-Batch1-Clean

# 3. Build project
mvn clean install

# 4. Run tests
mvn test

# 5. Start backend
cd backend
mvn spring-boot:run

# 6. Start frontend (new terminal)
cd frontend
npm install
npm run dev
```

---

## 🏗️ **Architecture Overview**

### **Project Structure**
```
easy-luxury/
├── ai-infrastructure-module/          # AI Infrastructure Module
│   ├── src/main/java/com/ai/infrastructure/
│   │   ├── core/                      # Core AI Services
│   │   ├── config/                    # Configuration
│   │   ├── dto/                       # Data Transfer Objects
│   │   └── exception/                 # Custom Exceptions
│   └── pom.xml
├── backend/                           # Spring Boot Application
│   ├── src/main/java/com/easyluxury/
│   │   ├── ai/                        # AI Integration Layer
│   │   │   ├── facade/                # AI Facades
│   │   │   ├── service/               # AI Services
│   │   │   ├── controller/            # AI Controllers
│   │   │   └── config/                # AI Configuration
│   │   ├── entity/                    # JPA Entities
│   │   ├── repository/                # Data Repositories
│   │   └── controller/                # REST Controllers
│   └── src/test/java/                 # Test Classes
├── frontend/                          # Next.js Application
└── docs/                              # Documentation
```

### **AI Service Architecture**
```
┌─────────────────────────────────────────────────────────────┐
│                    AI Infrastructure Module                 │
│  ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐ │
│  │   AICoreService │ │ AIEmbeddingService│ │  AISearchService│ │
│  └─────────────────┘ └─────────────────┘ └─────────────────┘ │
│  ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐ │
│  │   RAGService    │ │VectorDatabaseSvc│ │ AIHealthService │ │
│  └─────────────────┘ └─────────────────┘ └─────────────────┘ │
└─────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────┐
│              Easy Luxury AI Integration Layer              │
│  ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐ │
│  │  ProductAIFacade│ │   UserAIFacade  │ │  OrderAIFacade  │ │
│  └─────────────────┘ └─────────────────┘ └─────────────────┘ │
│  ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐ │
│  │  AIHelperService│ │BehaviorTracking │ │SmartValidation  │ │
│  └─────────────────┘ └─────────────────┘ └─────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

---

## 🔧 **Development Workflow**

### **1. Feature Development**
```bash
# Create feature branch
git checkout -b feature/ai-enhancement

# Make changes
# ... implement feature ...

# Run tests
mvn test

# Commit changes
git add .
git commit -m "feat: add AI enhancement feature (Sequence X)"

# Push branch
git push origin feature/ai-enhancement

# Create pull request
```

### **2. Testing Workflow**
```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=AIServiceIntegrationTest

# Run integration tests only
mvn test -Dtest="*IntegrationTest"

# Run with specific profile
mvn test -Dspring.profiles.active=test
```

### **3. Code Quality Checks**
```bash
# Check compilation
mvn compile

# Run linting
mvn checkstyle:check

# Run security checks
mvn dependency:check

# Generate test coverage report
mvn jacoco:report
```

---

## 🧪 **Testing Guidelines**

### **Test Structure**
```java
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "ai.providers.openai-api-key=test-key",
    "ai.providers.openai-model=gpt-4o-mini",
    "ai.service.enabled=true"
})
class AIServiceIntegrationTest {
    
    @Autowired
    private AICoreService aiCoreService;
    
    @Test
    void testAIContentGeneration() {
        // Given
        AIGenerationRequest request = AIGenerationRequest.builder()
            .prompt("Test prompt")
            .model("gpt-4o-mini")
            .maxTokens(200)
            .temperature(0.7)
            .build();
        
        // When
        AIGenerationResponse response = aiCoreService.generateContent(request);
        
        // Then
        assertNotNull(response);
        assertNotNull(response.getContent());
        assertFalse(response.getContent().isEmpty());
    }
}
```

### **Test Categories**
- **Unit Tests**: Individual component testing
- **Integration Tests**: Full Spring context testing
- **Controller Tests**: REST API endpoint testing
- **Service Tests**: Business logic testing

### **Test Data Management**
```java
// Use @BeforeEach for test data setup
@BeforeEach
void setUp() {
    testUser = createTestUser();
    testProduct = createTestProduct();
    testOrder = createTestOrder();
}

private User createTestUser() {
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setEmail("test@example.com");
    user.setFirstName("Test");
    user.setLastName("User");
    user.setRole(User.Role.USER);
    return userRepository.save(user);
}
```

---

## 🔌 **AI Service Usage**

### **Content Generation**
```java
@Service
public class ProductAIService {
    
    @Autowired
    private AICoreService aiCoreService;
    
    public String generateProductDescription(Product product) {
        AIGenerationRequest request = AIGenerationRequest.builder()
            .prompt("Generate a product description for: " + product.getName())
            .model("gpt-4o-mini")
            .maxTokens(200)
            .temperature(0.7)
            .build();
        
        AIGenerationResponse response = aiCoreService.generateContent(request);
        return response.getContent();
    }
}
```

### **Embedding Generation**
```java
public List<Double> generateProductEmbedding(Product product) {
    AIEmbeddingRequest request = AIEmbeddingRequest.builder()
        .text(product.getName() + " " + product.getDescription())
        .model("text-embedding-3-small")
        .build();
    
    AIEmbeddingResponse response = aiCoreService.generateEmbedding(request);
    return response.getEmbedding();
}
```

### **Semantic Search**
```java
public List<Product> searchProducts(String query, int limit) {
    // Generate query embedding
    List<Double> queryVector = generateQueryEmbedding(query);
    
    // Create search request
    AISearchRequest request = AISearchRequest.builder()
        .query(query)
        .entityType("products")
        .limit(limit)
        .threshold(0.7)
        .build();
    
    // Perform search
    AISearchResponse response = aiCoreService.search(queryVector, request);
    
    // Convert results to products
    return convertSearchResultsToProducts(response.getResults());
}
```

---

## ⚙️ **Configuration Management**

### **Profile-based Configuration**
```yaml
# application-test.yml
spring:
  profiles:
    active: test
  datasource:
    url: jdbc:h2:mem:testdb
    driver-class-name: org.h2.Driver

ai:
  providers:
    openai-api-key: test-key
    openai-model: gpt-4o-mini
    openai-embedding-model: text-embedding-3-small
  service:
    enabled: true
    auto-configuration: true
```

### **Environment-specific Settings**
```yaml
# application-dev.yml
ai:
  providers:
    openai-api-key: ${OPENAI_API_KEY:dev-key}
    mock-responses: true

# application-prod.yml
ai:
  providers:
    openai-api-key: ${OPENAI_API_KEY}
    mock-responses: false
```

### **AI Service Configuration**
```java
@Configuration
public class AIProfileConfiguration {
    
    @Bean
    @Primary
    @Profile({"test", "dev"})
    @ConditionalOnProperty(name = "ai.provider.openai.mock-responses", havingValue = "true", matchIfMissing = true)
    public AICoreService mockAIService() {
        return new MockAIService();
    }
    
    @Bean
    @Primary
    @Profile({"prod", "production"})
    @ConditionalOnProperty(name = "ai.provider.openai.mock-responses", havingValue = "false")
    public AICoreService productionAIService() {
        return new ProductionAIService();
    }
}
```

---

## 🐛 **Debugging & Troubleshooting**

### **Common Issues**

#### **1. Bean Definition Conflicts**
```
Error: Bean definition conflicts
Solution: Use @Primary and @Profile annotations
```

#### **2. Configuration Not Loading**
```
Error: Properties not loaded in tests
Solution: Use @TestPropertySource annotation
```

#### **3. Database Schema Issues**
```
Error: Reserved keyword conflicts
Solution: Use @Column(name = "custom_name") annotation
```

#### **4. DTO Method Signature Mismatches**
```
Error: Cannot find symbol method
Solution: Check DTO structure and method signatures
```

### **Debugging Tools**
```java
// Enable debug logging
logging:
  level:
    com.easyluxury.ai: DEBUG
    com.ai.infrastructure: DEBUG

// Add debug statements
log.debug("Processing AI request: {}", request);
log.debug("AI response: {}", response);
```

### **Test Debugging**
```java
// Use @DirtiesContext for test isolation
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AIServiceIntegrationTest {
    // Test methods
}

// Use @Transactional for database rollback
@Transactional
class AIServiceIntegrationTest {
    // Test methods
}
```

---

## 📊 **Performance Optimization**

### **Caching Strategy**
```java
@Cacheable("ai-responses")
public AIGenerationResponse generateContent(AIGenerationRequest request) {
    // AI content generation
}

@CacheEvict("ai-responses")
public void clearCache() {
    // Clear cache
}
```

### **Async Processing**
```java
@Async
public CompletableFuture<AIGenerationResponse> generateContentAsync(AIGenerationRequest request) {
    return CompletableFuture.completedFuture(aiCoreService.generateContent(request));
}
```

### **Rate Limiting**
```java
@RateLimiter(name = "ai-service", fallbackMethod = "fallbackResponse")
public AIGenerationResponse generateContent(AIGenerationRequest request) {
    return aiCoreService.generateContent(request);
}

public AIGenerationResponse fallbackResponse(AIGenerationRequest request, Exception ex) {
    return AIGenerationResponse.builder()
        .content("Service temporarily unavailable")
        .model("fallback")
        .build();
}
```

---

## 🔒 **Security Best Practices**

### **API Key Management**
```yaml
# Use environment variables
ai:
  providers:
    openai-api-key: ${OPENAI_API_KEY}

# Never commit API keys to repository
# Use .env files for local development
```

### **Input Validation**
```java
@Valid
public class AIGenerationRequest {
    @NotBlank
    @Size(max = 1000)
    private String prompt;
    
    @NotNull
    @Positive
    private Integer maxTokens;
    
    @DecimalMin("0.0")
    @DecimalMax("2.0")
    private Double temperature;
}
```

### **Error Handling**
```java
@ControllerAdvice
public class AIExceptionHandler {
    
    @ExceptionHandler(AIServiceException.class)
    public ResponseEntity<ErrorResponse> handleAIServiceException(AIServiceException ex) {
        ErrorResponse error = ErrorResponse.builder()
            .message("AI service error")
            .details(ex.getMessage())
            .timestamp(LocalDateTime.now())
            .build();
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
```

---

## 📚 **Code Style & Conventions**

### **Naming Conventions**
- **Classes**: PascalCase (`ProductAIService`)
- **Methods**: camelCase (`generateProductDescription`)
- **Variables**: camelCase (`productId`)
- **Constants**: UPPER_SNAKE_CASE (`MAX_RETRY_ATTEMPTS`)

### **Package Structure**
```
com.easyluxury.ai/
├── config/          # Configuration classes
├── controller/      # REST controllers
├── facade/          # Business facades
├── service/         # Business services
├── dto/            # Data transfer objects
└── exception/      # Custom exceptions
```

### **Documentation Standards**
```java
/**
 * Service for AI-powered product operations
 * 
 * This service provides AI capabilities for product management including
 * content generation, recommendations, and semantic search.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Service
public class ProductAIService {
    
    /**
     * Generate AI-powered product description
     * 
     * @param product the product to generate description for
     * @return generated product description
     * @throws AIServiceException if AI service fails
     */
    public String generateProductDescription(Product product) {
        // Implementation
    }
}
```

---

## 🚀 **Deployment Guide**

### **Local Development**
```bash
# Backend
cd backend
mvn spring-boot:run

# Frontend
cd frontend
npm run dev
```

### **Docker Deployment**
```dockerfile
# Dockerfile
FROM openjdk:21-jdk-slim
COPY target/easy-luxury-backend.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

### **Environment Variables**
```bash
# Required environment variables
export OPENAI_API_KEY=your-api-key
export SPRING_PROFILES_ACTIVE=prod
export DATABASE_URL=jdbc:postgresql://localhost:5432/easyluxury
```

---

## 📖 **Additional Resources**

### **Documentation**
- [AI Integration Complete Summary](AI_INTEGRATION_COMPLETE_SUMMARY.md)
- [AI Architecture Solution](AI_ARCHITECTURE_SOLUTION.md)
- [Sequence 10 Completion Status](SEQUENCE_10_COMPLETION_STATUS.md)
- [Optimal Execution Order](planning/OPTIMAL_EXECUTION_ORDER.md)

### **Useful Commands**
```bash
# Check test coverage
mvn jacoco:report

# Run specific test profile
mvn test -Dspring.profiles.active=test

# Build without tests
mvn clean install -DskipTests

# Check dependencies
mvn dependency:tree

# Clean and rebuild
mvn clean install -U
```

### **IDE Configuration**
- **IntelliJ IDEA**: Import as Maven project, enable annotation processing
- **VS Code**: Install Java Extension Pack, Spring Boot Extension Pack
- **Eclipse**: Import as Maven project, install Spring Tools

---

**This developer guide provides comprehensive information for working with the Easy Luxury AI integration project. Follow these guidelines to ensure consistent, high-quality development practices.**