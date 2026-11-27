# RAG Module Migration Plan

## Executive Summary

This document describes the migration strategy for extracting all RAG (Retrieval-Augmented Generation) related code and functionality from `ai-infrastructure-core` into a new dedicated `ai-infrastructure-rag` module. This separation will improve modularity, enable independent versioning, reduce coupling, and allow teams to use RAG functionality without pulling in unrelated dependencies.

---

## Table of Contents

1. [Current State Analysis](#1-current-state-analysis)
2. [Migration Goals](#2-migration-goals)
3. [New Module Architecture](#3-new-module-architecture)
4. [Component Migration Inventory](#4-component-migration-inventory)
5. [Interface Design](#5-interface-design)
6. [Migration Steps](#6-migration-steps)
7. [Dependency Management](#7-dependency-management)
8. [Configuration Strategy](#8-configuration-strategy)
9. [Testing Strategy](#9-testing-strategy)
10. [Backward Compatibility](#10-backward-compatibility)
11. [Rollout Plan](#11-rollout-plan)
12. [Risk Assessment](#12-risk-assessment)

---

## 1. Current State Analysis

### 1.1 Current Module Structure

```
ai-infrastructure-module/
├── ai-infrastructure-core/          # Contains RAG + everything else
├── ai-infrastructure-behavior/      # Behavior analysis
├── ai-infrastructure-migration/     # Data migration tools
├── ai-infrastructure-relationship-query/  # NL to SQL
├── ai-infrastructure-web/           # REST controllers
├── providers/                       # LLM/Embedding providers
│   ├── ai-infrastructure-onnx-starter/
│   ├── ai-infrastructure-provider-openai/
│   ├── ai-infrastructure-provider-azure/
│   ├── ai-infrastructure-provider-anthropic/
│   ├── ai-infrastructure-provider-cohere/
│   └── ai-infrastructure-provider-rest/
└── victor-databases/                # Vector database implementations
    ├── ai-infrastructure-vector-lucene/
    ├── ai-infrastructure-vector-pinecone/
    ├── ai-infrastructure-vector-qdrant/
    ├── ai-infrastructure-vector-milvus/
    ├── ai-infrastructure-vector-weaviate/
    └── ai-infrastructure-vector-memory/
```

### 1.2 RAG Components Currently in Core Module

The following RAG-related components are currently embedded in `ai-infrastructure-core`:

| Package | Components | Purpose |
|---------|------------|---------|
| `com.ai.infrastructure.rag` | `RAGService`, `AdvancedRAGService`, `VectorDatabaseService`, `SearchableEntityVectorDatabaseService` | Core RAG operations |
| `com.ai.infrastructure.search` | `VectorSearchService` | Vector similarity search |
| `com.ai.infrastructure.vector` | `VectorDatabase`, `VectorDatabaseServiceAdapter` | Vector DB abstraction |
| `com.ai.infrastructure.intent` | `RAGOrchestrator`, `IntentQueryExtractor`, `EnrichedPromptBuilder`, `SystemContextBuilder`, `KnowledgeBaseOverviewService` | Intent extraction & orchestration |
| `com.ai.infrastructure.intent.orchestration` | `OrchestrationContext`, `OrchestrationResult`, `OrchestrationResultType` | Orchestration framework |
| `com.ai.infrastructure.intent.action` | `ActionHandler`, `ActionHandlerRegistry`, `ActionResult`, action handlers | Action execution |
| `com.ai.infrastructure.intent.history` | `IntentHistoryService` | History tracking |
| `com.ai.infrastructure.indexing` | `IndexingCoordinator`, `IndexingQueueService`, workers | Async indexing |
| `com.ai.infrastructure.dto` | `RAGRequest`, `RAGResponse`, `AdvancedRAGRequest`, `AdvancedRAGResponse`, `VectorRecord`, `Intent`, `MultiIntentResponse` | Data transfer objects |
| `com.ai.infrastructure.core` | `AISearchService` | Search service facade |
| `com.ai.infrastructure.storage` | Storage strategies | Searchable entity persistence |

### 1.3 Cross-Cutting Dependencies

RAG components depend on these core services:
- `AIEmbeddingService` - Embedding generation
- `AICoreService` - LLM text generation
- `PIIDetectionService` - Privacy protection
- `AISecurityService` - Security checks
- `AIAccessControlService` - Authorization
- `AIComplianceService` - Compliance validation

---

## 2. Migration Goals

### 2.1 Primary Goals

1. **Modularity**: Enable teams to include only RAG functionality without unrelated dependencies
2. **Separation of Concerns**: Clear boundaries between RAG, embedding, search, and orchestration
3. **Independent Versioning**: Allow RAG module to evolve separately from core
4. **Testability**: Smaller, focused modules are easier to test
5. **Maintainability**: Clearer ownership and reduced cognitive load

### 2.2 Secondary Goals

1. **Performance**: Optimize RAG-specific configurations independently
2. **Extensibility**: Easier to add new RAG features without affecting core
3. **Documentation**: Better organization of RAG-specific documentation
4. **Onboarding**: Simpler understanding for new team members

---

## 3. New Module Architecture

### 3.1 Proposed Module Structure

```
ai-infrastructure-module/
├── ai-infrastructure-core/              # Minimal core (annotations, entities, DTOs)
├── ai-infrastructure-rag/               # NEW: RAG functionality
│   ├── ai-infrastructure-rag-api/       # Interfaces & DTOs
│   └── ai-infrastructure-rag-core/      # Implementation
├── ai-infrastructure-embedding/         # NEW: Embedding abstractions
├── ai-infrastructure-search/            # NEW: Search functionality
├── ai-infrastructure-intent/            # NEW: Intent orchestration
├── ai-infrastructure-behavior/          # (unchanged)
├── ai-infrastructure-migration/         # (unchanged)
├── ai-infrastructure-relationship-query/# (unchanged)
├── ai-infrastructure-web/               # (unchanged)
├── providers/                           # (unchanged)
└── victor-databases/                    # (unchanged)
```

### 3.2 Package Structure for New RAG Module

```
ai-infrastructure-rag/
├── pom.xml
├── README.md
├── RAG_MODULE_USER_GUIDE.md
└── src/
    ├── main/
    │   ├── java/
    │   │   └── com/ai/infrastructure/rag/
    │   │       ├── api/                          # Public interfaces
    │   │       │   ├── RAGService.java
    │   │       │   ├── AdvancedRAGService.java
    │   │       │   └── VectorDatabaseService.java
    │   │       ├── config/                       # Auto-configuration
    │   │       │   ├── RAGAutoConfiguration.java
    │   │       │   └── RAGProperties.java
    │   │       ├── core/                         # Core implementations
    │   │       │   ├── DefaultRAGService.java
    │   │       │   ├── DefaultAdvancedRAGService.java
    │   │       │   └── SearchableEntityVectorDatabaseService.java
    │   │       ├── dto/                          # Data transfer objects
    │   │       │   ├── RAGRequest.java
    │   │       │   ├── RAGResponse.java
    │   │       │   ├── AdvancedRAGRequest.java
    │   │       │   ├── AdvancedRAGResponse.java
    │   │       │   └── VectorRecord.java
    │   │       ├── search/                       # Vector search
    │   │       │   └── VectorSearchService.java
    │   │       ├── context/                      # Context building
    │   │       │   ├── ContextBuilder.java
    │   │       │   └── ContextOptimizer.java
    │   │       └── exception/                    # RAG-specific exceptions
    │   │           └── RAGException.java
    │   └── resources/
    │       └── META-INF/
    │           └── spring/
    │               └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
    └── test/
        └── java/
            └── com/ai/infrastructure/rag/
                ├── RAGServiceTest.java
                ├── AdvancedRAGServiceTest.java
                └── VectorSearchServiceTest.java
```

### 3.3 Package Structure for Intent Module

```
ai-infrastructure-intent/
├── pom.xml
├── README.md
└── src/
    └── main/
        └── java/
            └── com/ai/infrastructure/intent/
                ├── api/                              # Public interfaces
                │   ├── IntentExtractor.java
                │   ├── Orchestrator.java
                │   └── ActionHandler.java
                ├── config/                           # Auto-configuration
                │   ├── IntentAutoConfiguration.java
                │   └── IntentProperties.java
                ├── core/                             # Implementations
                │   ├── IntentQueryExtractor.java
                │   ├── RAGOrchestrator.java
                │   └── EnrichedPromptBuilder.java
                ├── dto/                              # Intent DTOs
                │   ├── Intent.java
                │   ├── IntentType.java
                │   ├── MultiIntentResponse.java
                │   └── NextStepRecommendation.java
                ├── orchestration/                    # Orchestration framework
                │   ├── OrchestrationContext.java
                │   ├── OrchestrationResult.java
                │   └── OrchestrationResultType.java
                ├── action/                           # Action handling
                │   ├── ActionHandlerRegistry.java
                │   ├── ActionResult.java
                │   └── handlers/
                └── history/                          # History tracking
                    └── IntentHistoryService.java
```

---

## 4. Component Migration Inventory

### 4.1 Components to Move to `ai-infrastructure-rag`

| Current Location | Component | New Location |
|------------------|-----------|--------------|
| `rag/RAGService.java` | RAG Service | `rag/core/DefaultRAGService.java` |
| `rag/AdvancedRAGService.java` | Advanced RAG | `rag/core/DefaultAdvancedRAGService.java` |
| `rag/VectorDatabaseService.java` | Vector DB Interface | `rag/api/VectorDatabaseService.java` |
| `rag/SearchableEntityVectorDatabaseService.java` | Decorator | `rag/core/SearchableEntityVectorDatabaseService.java` |
| `search/VectorSearchService.java` | Vector Search | `rag/search/VectorSearchService.java` |
| `vector/VectorDatabase.java` | Vector DB Abstraction | `rag/api/VectorDatabase.java` |
| `vector/VectorDatabaseServiceAdapter.java` | Adapter | `rag/core/VectorDatabaseServiceAdapter.java` |
| `dto/RAGRequest.java` | Request DTO | `rag/dto/RAGRequest.java` |
| `dto/RAGResponse.java` | Response DTO | `rag/dto/RAGResponse.java` |
| `dto/AdvancedRAGRequest.java` | Advanced Request | `rag/dto/AdvancedRAGRequest.java` |
| `dto/AdvancedRAGResponse.java` | Advanced Response | `rag/dto/AdvancedRAGResponse.java` |
| `dto/VectorRecord.java` | Vector Record | `rag/dto/VectorRecord.java` |
| `core/AISearchService.java` | Search Facade | `rag/search/AISearchService.java` |

### 4.2 Components to Move to `ai-infrastructure-intent`

| Current Location | Component | New Location |
|------------------|-----------|--------------|
| `intent/IntentQueryExtractor.java` | Intent Extraction | `intent/core/IntentQueryExtractor.java` |
| `intent/EnrichedPromptBuilder.java` | Prompt Builder | `intent/core/EnrichedPromptBuilder.java` |
| `intent/SystemContextBuilder.java` | Context Builder | `intent/core/SystemContextBuilder.java` |
| `intent/KnowledgeBaseOverviewService.java` | KB Overview | `intent/core/KnowledgeBaseOverviewService.java` |
| `intent/orchestration/RAGOrchestrator.java` | Orchestrator | `intent/core/RAGOrchestrator.java` |
| `intent/orchestration/OrchestrationContext.java` | Context | `intent/orchestration/OrchestrationContext.java` |
| `intent/orchestration/OrchestrationResult.java` | Result | `intent/orchestration/OrchestrationResult.java` |
| `intent/action/*` | Action Framework | `intent/action/*` |
| `intent/history/*` | History Service | `intent/history/*` |
| `dto/Intent.java` | Intent DTO | `intent/dto/Intent.java` |
| `dto/IntentType.java` | Intent Type | `intent/dto/IntentType.java` |
| `dto/MultiIntentResponse.java` | Multi-Intent | `intent/dto/MultiIntentResponse.java` |
| `dto/NextStepRecommendation.java` | Recommendations | `intent/dto/NextStepRecommendation.java` |

### 4.3 Components Remaining in Core

| Component | Reason |
|-----------|--------|
| `@AICapable`, `@AIEmbedding`, `@AIKnowledge` | Core annotations used everywhere |
| `AIEmbeddingService` | Fundamental service, used by many modules |
| `AICoreService` | LLM abstraction, cross-cutting concern |
| `AIAccessControlService` | Security, cross-cutting concern |
| `AISecurityService` | Security, cross-cutting concern |
| `AIComplianceService` | Compliance, cross-cutting concern |
| `PIIDetectionService` | Privacy, cross-cutting concern |
| `AICapableAspect` | AOP for entity processing |
| `AISearchableEntity` | Core entity type |
| Indexing components | Shared by multiple modules |

---

## 5. Interface Design

### 5.1 Core RAG API Interface

```java
// ai-infrastructure-rag/src/main/java/com/ai/infrastructure/rag/api/RAGService.java
package com.ai.infrastructure.rag.api;

/**
 * Core RAG Service Interface
 * 
 * Defines the contract for Retrieval-Augmented Generation operations.
 * Implementations handle query processing, vector search, context building,
 * and response generation.
 */
public interface RAGService {
    
    /**
     * Perform RAG operation with full request configuration
     */
    RAGResponse performRag(RAGRequest request);
    
    /**
     * Perform RAG query with LLM generation
     */
    RAGResponse performRAGQuery(RAGRequest request);
    
    /**
     * Simple RAG query
     */
    AISearchResponse performRAGQuery(String query, String entityType, int limit);
    
    /**
     * Index content for RAG retrieval
     */
    void indexContent(String entityType, String entityId, String content, Map<String, Object> metadata);
    
    /**
     * Remove content from RAG index
     */
    void removeContent(String entityType, String entityId);
    
    /**
     * Build context from search results
     */
    String buildContext(AISearchResponse searchResponse);
    
    /**
     * Get RAG statistics
     */
    Map<String, Object> getStatistics();
}
```

### 5.2 Vector Database Service Interface

```java
// ai-infrastructure-rag/src/main/java/com/ai/infrastructure/rag/api/VectorDatabaseService.java
package com.ai.infrastructure.rag.api;

/**
 * Vector Database Service Interface
 * 
 * Defines the contract for vector storage and retrieval operations.
 * Implementations can be backed by Lucene, Pinecone, Qdrant, Milvus, etc.
 */
public interface VectorDatabaseService {
    
    // Store operations
    String storeVector(String entityType, String entityId, String content, 
                       List<Double> embedding, Map<String, Object> metadata);
    
    boolean updateVector(String vectorId, String entityType, String entityId, 
                         String content, List<Double> embedding, Map<String, Object> metadata);
    
    // Retrieval operations
    Optional<VectorRecord> getVector(String vectorId);
    Optional<VectorRecord> getVectorByEntity(String entityType, String entityId);
    List<VectorRecord> getVectorsByEntityType(String entityType);
    
    // Search operations
    AISearchResponse search(List<Double> queryVector, AISearchRequest request);
    AISearchResponse searchByEntityType(List<Double> queryVector, String entityType, 
                                        int limit, double threshold);
    
    // Remove operations
    boolean removeVector(String entityType, String entityId);
    boolean removeVectorById(String vectorId);
    
    // Batch operations
    List<String> batchStoreVectors(List<VectorRecord> vectors);
    int batchUpdateVectors(List<VectorRecord> vectors);
    int batchRemoveVectors(List<String> vectorIds);
    
    // Management operations
    long getVectorCountByEntityType(String entityType);
    boolean vectorExists(String entityType, String entityId);
    Map<String, Object> getStatistics();
    long clearVectors();
    long clearVectorsByEntityType(String entityType);
}
```

### 5.3 Intent Orchestrator Interface

```java
// ai-infrastructure-intent/src/main/java/com/ai/infrastructure/intent/api/Orchestrator.java
package com.ai.infrastructure.intent.api;

/**
 * Intent Orchestrator Interface
 * 
 * Defines the contract for processing user queries through intent extraction,
 * RAG retrieval, action execution, and response generation.
 */
public interface Orchestrator {
    
    /**
     * Orchestrate a user query with full context
     */
    OrchestrationResult orchestrate(String query, OrchestrationContext context);
    
    /**
     * Orchestrate with minimal context (creates anonymous context)
     */
    default OrchestrationResult orchestrate(String query) {
        return orchestrate(query, OrchestrationContext.anonymous());
    }
}
```

### 5.4 Service Provider Interfaces (SPI)

```java
// ai-infrastructure-rag/src/main/java/com/ai/infrastructure/rag/spi/EmbeddingProvider.java
package com.ai.infrastructure.rag.spi;

/**
 * SPI for embedding generation - allows RAG module to work with any embedding provider
 */
public interface EmbeddingServiceProvider {
    AIEmbeddingResponse generateEmbedding(AIEmbeddingRequest request);
    List<AIEmbeddingResponse> generateEmbeddings(List<String> texts, String entityType);
    boolean isAvailable();
}

// ai-infrastructure-rag/src/main/java/com/ai/infrastructure/rag/spi/LLMProvider.java
package com.ai.infrastructure.rag.spi;

/**
 * SPI for LLM generation - allows RAG module to work with any LLM provider
 */
public interface LLMServiceProvider {
    AIGenerationResponse generateContent(AIGenerationRequest request);
    String generateText(String prompt);
}
```

---

## 6. Migration Steps

### Phase 1: Preparation (Week 1-2)

#### Step 1.1: Create New Module Structure
```bash
# Create new module directories
mkdir -p ai-infrastructure-rag/src/main/java/com/ai/infrastructure/rag/{api,config,core,dto,search,exception}
mkdir -p ai-infrastructure-rag/src/main/resources/META-INF/spring
mkdir -p ai-infrastructure-rag/src/test/java/com/ai/infrastructure/rag

mkdir -p ai-infrastructure-intent/src/main/java/com/ai/infrastructure/intent/{api,config,core,dto,orchestration,action,history}
mkdir -p ai-infrastructure-intent/src/main/resources/META-INF/spring
mkdir -p ai-infrastructure-intent/src/test/java/com/ai/infrastructure/intent
```

#### Step 1.2: Create Module POMs

**ai-infrastructure-rag/pom.xml**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    
    <parent>
        <groupId>com.ai.fabric</groupId>
        <artifactId>ai-fabric-spring-boot-starter</artifactId>
        <version>1.0.0</version>
    </parent>
    
    <artifactId>ai-infrastructure-rag</artifactId>
    <packaging>jar</packaging>
    
    <name>AI Infrastructure RAG Module</name>
    <description>Retrieval-Augmented Generation functionality</description>
    
    <dependencies>
        <!-- Core module for shared entities and interfaces -->
        <dependency>
            <groupId>com.ai.fabric</groupId>
            <artifactId>ai-fabric-core</artifactId>
            <version>${project.version}</version>
        </dependency>
        
        <!-- Spring Boot -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-cache</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-configuration-processor</artifactId>
            <optional>true</optional>
        </dependency>
        
        <!-- Caching -->
        <dependency>
            <groupId>com.github.ben-manes.caffeine</groupId>
            <artifactId>caffeine</artifactId>
        </dependency>
        
        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        
        <!-- Testing -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

#### Step 1.3: Define SPI Interfaces
Create service provider interfaces to decouple from core services.

### Phase 2: Extract Interfaces (Week 2-3)

#### Step 2.1: Move Interface Definitions
1. Copy `VectorDatabaseService` interface to new module
2. Create `RAGService` interface based on existing implementation
3. Create `Orchestrator` interface

#### Step 2.2: Create DTOs
1. Move RAG-specific DTOs to new module
2. Update package references
3. Ensure backward compatibility with `@Deprecated` annotations on old locations

### Phase 3: Migrate Implementations (Week 3-5)

#### Step 3.1: Migrate RAG Core Services
```java
// Move and refactor RAGService
// 1. Copy to new location
// 2. Update imports
// 3. Inject dependencies via SPI
// 4. Update configuration
```

#### Step 3.2: Migrate Vector Search Service
```java
// Move VectorSearchService
// 1. Copy to new location
// 2. Update to use VectorDatabaseService interface
// 3. Update caching configuration
```

#### Step 3.3: Migrate Intent Components
1. Move IntentQueryExtractor
2. Move RAGOrchestrator
3. Move action handlers
4. Move orchestration framework

### Phase 4: Update Configurations (Week 5-6)

#### Step 4.1: Create RAG Auto-Configuration

```java
// ai-infrastructure-rag/src/main/java/com/ai/infrastructure/rag/config/RAGAutoConfiguration.java
@Configuration
@EnableConfigurationProperties(RAGProperties.class)
@ConditionalOnClass(RAGService.class)
@AutoConfigureAfter(AIInfrastructureAutoConfiguration.class)
public class RAGAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    public RAGService ragService(
            AIProviderConfig config,
            EmbeddingServiceProvider embeddingService,
            VectorDatabaseService vectorDatabaseService,
            VectorDatabase vectorDatabase,
            AISearchService searchService,
            PIIDetectionService piiDetectionService) {
        return new DefaultRAGService(
            config, embeddingService, vectorDatabaseService, 
            vectorDatabase, searchService, piiDetectionService
        );
    }
    
    @Bean
    @ConditionalOnMissingBean
    public AdvancedRAGService advancedRAGService(
            AISearchService aiSearchService,
            EmbeddingServiceProvider embeddingService,
            LLMServiceProvider llmService,
            RAGService ragService) {
        return new DefaultAdvancedRAGService(
            aiSearchService, embeddingService, llmService, ragService
        );
    }
    
    @Bean
    @ConditionalOnMissingBean
    public VectorSearchService vectorSearchService(
            AIProviderConfig config,
            VectorDatabaseService vectorDatabaseService,
            CacheManager cacheManager) {
        return new VectorSearchService(config, vectorDatabaseService, cacheManager);
    }
}
```

#### Step 4.2: Register Auto-Configuration
```
# ai-infrastructure-rag/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
com.ai.infrastructure.rag.config.RAGAutoConfiguration
```

### Phase 5: Update Dependencies (Week 6-7)

#### Step 5.1: Update Parent POM
```xml
<!-- Add new modules to parent -->
<modules>
    <module>ai-infrastructure-core</module>
    <module>ai-infrastructure-rag</module>
    <module>ai-infrastructure-intent</module>
    <!-- ... other modules ... -->
</modules>
```

#### Step 5.2: Update Dependent Modules
Update modules that depend on RAG:
- `ai-infrastructure-web` - Add RAG dependency
- `ai-infrastructure-relationship-query` - Add intent dependency
- `integration-Testing` - Add both dependencies

### Phase 6: Backward Compatibility Layer (Week 7-8)

#### Step 6.1: Create Deprecation Bridges

```java
// ai-infrastructure-core: Deprecated bridge for backward compatibility
package com.ai.infrastructure.rag;

/**
 * @deprecated Use {@link com.ai.infrastructure.rag.api.RAGService} from ai-infrastructure-rag module
 */
@Deprecated(forRemoval = true, since = "2.0.0")
public class RAGService {
    private final com.ai.infrastructure.rag.api.RAGService delegate;
    
    // Delegate all methods to new implementation
}
```

#### Step 6.2: Migration Documentation
Create migration guide for existing users.

---

## 7. Dependency Management

### 7.1 New Dependency Graph

```
ai-infrastructure-rag
├── ai-infrastructure-core (shared entities, annotations, base services)
├── spring-boot-starter
├── spring-boot-starter-cache
└── caffeine

ai-infrastructure-intent
├── ai-infrastructure-core
├── ai-infrastructure-rag
├── spring-boot-starter
└── jackson-databind

ai-infrastructure-web
├── ai-infrastructure-core
├── ai-infrastructure-rag
├── ai-infrastructure-intent
└── spring-boot-starter-web
```

### 7.2 Circular Dependency Prevention

To prevent circular dependencies:

1. **Core exports interfaces only** - No implementations
2. **RAG depends on Core** - Uses embedding/LLM via SPI
3. **Intent depends on RAG** - Uses RAG service interface
4. **Vector DBs depend on RAG interfaces** - Implement VectorDatabaseService

### 7.3 Optional Dependencies

```xml
<!-- In RAG module: Optional integration with specific vector databases -->
<dependency>
    <groupId>com.ai.fabric</groupId>
    <artifactId>ai-infrastructure-vector-lucene</artifactId>
    <optional>true</optional>
</dependency>
```

---

## 8. Configuration Strategy

### 8.1 RAG Configuration Properties

```yaml
# application.yml
ai:
  rag:
    enabled: true
    default-threshold: 0.7
    default-limit: 10
    context:
      max-documents: 5
      optimization-level: medium  # low, medium, high
    cache:
      enabled: true
      ttl-seconds: 3600
    search:
      hybrid-enabled: true
      contextual-enabled: true
```

### 8.2 Configuration Classes

```java
@ConfigurationProperties(prefix = "ai.rag")
public class RAGProperties {
    private boolean enabled = true;
    private double defaultThreshold = 0.7;
    private int defaultLimit = 10;
    private ContextProperties context = new ContextProperties();
    private CacheProperties cache = new CacheProperties();
    private SearchProperties search = new SearchProperties();
    
    @Data
    public static class ContextProperties {
        private int maxDocuments = 5;
        private String optimizationLevel = "medium";
    }
    
    @Data
    public static class CacheProperties {
        private boolean enabled = true;
        private long ttlSeconds = 3600;
    }
    
    @Data
    public static class SearchProperties {
        private boolean hybridEnabled = true;
        private boolean contextualEnabled = true;
    }
}
```

---

## 9. Testing Strategy

### 9.1 Unit Tests

Each migrated component needs unit tests in the new module:

```java
@ExtendWith(MockitoExtension.class)
class DefaultRAGServiceTest {
    
    @Mock
    private EmbeddingServiceProvider embeddingService;
    
    @Mock
    private VectorDatabaseService vectorDatabaseService;
    
    @InjectMocks
    private DefaultRAGService ragService;
    
    @Test
    void shouldPerformRAGQuery() {
        // Given
        RAGRequest request = RAGRequest.builder()
            .query("test query")
            .entityType("product")
            .limit(5)
            .build();
        
        // When
        RAGResponse response = ragService.performRag(request);
        
        // Then
        assertThat(response.getSuccess()).isTrue();
    }
}
```

### 9.2 Integration Tests

```java
@SpringBootTest
@Import(RAGTestConfiguration.class)
class RAGIntegrationTest {
    
    @Autowired
    private RAGService ragService;
    
    @Test
    void shouldIndexAndRetrieveContent() {
        // Index content
        ragService.indexContent("product", "123", "Test product description", Map.of());
        
        // Search
        RAGRequest request = RAGRequest.builder()
            .query("product")
            .entityType("product")
            .build();
        
        RAGResponse response = ragService.performRag(request);
        
        assertThat(response.getDocuments()).isNotEmpty();
    }
}
```

### 9.3 Test Migration Checklist

- [ ] Migrate unit tests for RAGService
- [ ] Migrate unit tests for AdvancedRAGService
- [ ] Migrate unit tests for VectorSearchService
- [ ] Migrate integration tests (RAGIntegrationFlowTest)
- [ ] Create new module-specific integration tests
- [ ] Update test configurations
- [ ] Verify test coverage maintains ≥80%

---

## 10. Backward Compatibility

### 10.1 Deprecation Strategy

1. **Phase 1 (v2.0)**: Add `@Deprecated` annotations to old locations
2. **Phase 2 (v2.x)**: Log warnings when deprecated classes are used
3. **Phase 3 (v3.0)**: Remove deprecated classes

### 10.2 Bridge Classes

```java
// Bridge in core module for backward compatibility
@Deprecated(forRemoval = true, since = "2.0.0")
@Service("legacyRagService")
public class LegacyRAGServiceBridge implements com.ai.infrastructure.rag.RAGService {
    
    private final com.ai.infrastructure.rag.api.RAGService delegate;
    
    public LegacyRAGServiceBridge(
            @Autowired(required = false) 
            com.ai.infrastructure.rag.api.RAGService ragService) {
        this.delegate = ragService;
    }
    
    @Override
    public RAGResponse performRag(RAGRequest request) {
        if (delegate == null) {
            throw new IllegalStateException(
                "RAG module not found. Add ai-infrastructure-rag dependency.");
        }
        return delegate.performRag(request);
    }
}
```

### 10.3 Migration Guide for Users

```markdown
## Migrating to RAG Module (v2.0)

### Step 1: Add New Dependency

```xml
<dependency>
    <groupId>com.ai.fabric</groupId>
    <artifactId>ai-infrastructure-rag</artifactId>
    <version>2.0.0</version>
</dependency>
```

### Step 2: Update Imports

```java
// Before
import com.ai.infrastructure.rag.RAGService;

// After
import com.ai.infrastructure.rag.api.RAGService;
```

### Step 3: Update Configuration (Optional)

```yaml
# New configuration namespace
ai:
  rag:
    enabled: true
    # ... RAG-specific settings
```
```

---

## 11. Rollout Plan

### 11.1 Timeline

| Week | Phase | Activities |
|------|-------|------------|
| 1-2 | Preparation | Create module structure, POMs, interfaces |
| 2-3 | Extract Interfaces | Define APIs, create DTOs |
| 3-5 | Migrate Implementations | Move services, update dependencies |
| 5-6 | Update Configurations | Create auto-configuration, properties |
| 6-7 | Update Dependencies | Update dependent modules |
| 7-8 | Backward Compatibility | Create bridges, deprecations |
| 8-9 | Testing | Comprehensive testing, fix issues |
| 9-10 | Documentation | User guides, migration docs |
| 10+ | Release | Alpha, beta, GA releases |

### 11.2 Release Strategy

1. **Alpha (Internal)**: Test with internal projects
2. **Beta**: Limited external testing with selected teams
3. **RC**: Release candidate with all features complete
4. **GA**: General availability

### 11.3 Feature Flags

```yaml
ai:
  modules:
    rag:
      use-new-module: false  # Toggle between old and new
```

---

## 12. Risk Assessment

### 12.1 Identified Risks

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| Breaking changes for existing users | High | Medium | Comprehensive backward compatibility layer |
| Performance regression | Medium | Low | Performance testing before release |
| Circular dependencies | High | Medium | Careful interface design, SPI pattern |
| Missing functionality in migration | Medium | Medium | Complete component inventory, testing |
| Configuration incompatibility | Medium | Low | Clear migration documentation |

### 12.2 Rollback Plan

If critical issues are discovered:

1. Revert to previous release
2. Fix issues in new module
3. Re-release with fixes
4. Update migration documentation

### 12.3 Success Criteria

- [ ] All existing tests pass
- [ ] No breaking changes for users who don't upgrade
- [ ] Performance is equal or better than before
- [ ] Documentation is complete
- [ ] At least 2 internal projects successfully migrate

---

## Appendices

### Appendix A: Full Component List

<details>
<summary>Click to expand full component list</summary>

#### Files to Move to ai-infrastructure-rag

```
ai-infrastructure-core/src/main/java/com/ai/infrastructure/
├── rag/
│   ├── RAGService.java
│   ├── AdvancedRAGService.java
│   ├── VectorDatabaseService.java
│   └── SearchableEntityVectorDatabaseService.java
├── search/
│   └── VectorSearchService.java
├── vector/
│   ├── VectorDatabase.java
│   └── VectorDatabaseServiceAdapter.java
├── dto/
│   ├── RAGRequest.java
│   ├── RAGResponse.java
│   ├── AdvancedRAGRequest.java
│   ├── AdvancedRAGResponse.java
│   └── VectorRecord.java
└── core/
    └── AISearchService.java
```

#### Files to Move to ai-infrastructure-intent

```
ai-infrastructure-core/src/main/java/com/ai/infrastructure/
├── intent/
│   ├── IntentQueryExtractor.java
│   ├── EnrichedPromptBuilder.java
│   ├── SystemContextBuilder.java
│   ├── SystemContext.java
│   ├── KnowledgeBaseOverview.java
│   ├── KnowledgeBaseOverviewService.java
│   ├── orchestration/
│   │   ├── RAGOrchestrator.java
│   │   ├── OrchestrationContext.java
│   │   ├── OrchestrationResult.java
│   │   └── OrchestrationResultType.java
│   ├── action/
│   │   ├── ActionHandler.java
│   │   ├── ActionHandlerRegistry.java
│   │   ├── ActionInfo.java
│   │   ├── ActionResult.java
│   │   ├── AIActionMetaData.java
│   │   ├── AIActionProvider.java
│   │   ├── AvailableActionsRegistry.java
│   │   └── handlers/
│   │       ├── ClearVectorIndexActionHandler.java
│   │       └── RemoveVectorActionHandler.java
│   └── history/
│       └── IntentHistoryService.java
└── dto/
    ├── Intent.java
    ├── IntentType.java
    ├── MultiIntentResponse.java
    └── NextStepRecommendation.java
```

</details>

### Appendix B: Configuration Reference

<details>
<summary>Click to expand configuration reference</summary>

```yaml
# Complete RAG module configuration
ai:
  rag:
    # General settings
    enabled: true
    
    # Search defaults
    default-threshold: 0.7
    default-limit: 10
    
    # Context building
    context:
      max-documents: 5
      optimization-level: medium  # low, medium, high
      include-metadata: true
    
    # Caching
    cache:
      enabled: true
      ttl-seconds: 3600
      max-entries: 10000
    
    # Search features
    search:
      hybrid-enabled: true
      contextual-enabled: true
      
    # Performance
    performance:
      parallel-search: true
      batch-size: 50
      
  intent:
    # Intent module settings
    enabled: true
    
    # Smart suggestions
    smart-suggestions:
      enabled: true
      min-confidence: 0.7
      retrieval-limit: 3
      
    # History
    history:
      enabled: true
      retention-days: 30
```

</details>

### Appendix C: API Changes Summary

<details>
<summary>Click to expand API changes</summary>

| Old API | New API | Notes |
|---------|---------|-------|
| `com.ai.infrastructure.rag.RAGService` | `com.ai.infrastructure.rag.api.RAGService` | Interface extracted |
| `com.ai.infrastructure.rag.VectorDatabaseService` | `com.ai.infrastructure.rag.api.VectorDatabaseService` | Interface extracted |
| `com.ai.infrastructure.dto.RAGRequest` | `com.ai.infrastructure.rag.dto.RAGRequest` | Package moved |
| `com.ai.infrastructure.dto.RAGResponse` | `com.ai.infrastructure.rag.dto.RAGResponse` | Package moved |
| `com.ai.infrastructure.intent.orchestration.RAGOrchestrator` | `com.ai.infrastructure.intent.api.Orchestrator` | Interface extracted |
| `com.ai.infrastructure.dto.Intent` | `com.ai.infrastructure.intent.dto.Intent` | Package moved |

</details>

---

## Document History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-01-05 | AI Infrastructure Team | Initial version |

---

## References

1. [AI Infrastructure Core README](../ai-infrastructure-core/README.md)
2. [Module Architecture Documentation](./MODULE_AI_PROVIDERS/AI_PROVIDER_MODULAR_ARCHITECTURE_PLAN.md)
3. [Spring Boot Auto-configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.developing-auto-configuration)
4. [Maven Multi-module Projects](https://maven.apache.org/guides/mini/guide-multiple-modules.html)
