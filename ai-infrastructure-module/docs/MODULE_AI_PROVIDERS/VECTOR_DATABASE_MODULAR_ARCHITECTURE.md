# Vector Database Modular Architecture Plan

## Overview

Vector databases follow the same modular pattern as AI providers - each vector database implementation is in its own module with auto-discovery.

## Module Structure

```
ai-infrastructure-module/
├── ai-infrastructure-vector-lucene/      # Lucene (default, dev/test)
├── ai-infrastructure-vector-pinecone/    # Pinecone (production)
├── ai-infrastructure-vector-weaviate/   # Weaviate (self-hosted)
├── ai-infrastructure-vector-qdrant/      # Qdrant (self-hosted)
├── ai-infrastructure-vector-milvus/      # Milvus (self-hosted)
└── ai-infrastructure-vector-memory/     # InMemory (testing)
```

## Current State

**Current**: Vector database implementations now reside in dedicated modules:
- `ai-infrastructure-vector-lucene` (Lucene)
- `ai-infrastructure-vector-pinecone` (Pinecone)
- `ai-infrastructure-vector-memory` (In-Memory test support)

**Next**: Add remaining optional modules (Weaviate, Qdrant, Milvus) following the same pattern.

## Vector Database Module Template

### Example: Weaviate Vector Database Module

**File**: `ai-infrastructure-module/ai-infrastructure-vector-weaviate/pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.ai.infrastructure</groupId>
        <artifactId>ai-infrastructure-spring-boot-starter</artifactId>
        <version>1.0.0</version>
    </parent>

    <artifactId>ai-infrastructure-vector-weaviate</artifactId>
    <packaging>jar</packaging>

    <name>AI Infrastructure Weaviate Vector Database</name>
    <description>Weaviate vector database implementation for AI Infrastructure</description>

    <dependencies>
        <!-- Core dependency -->
        <dependency>
            <groupId>com.ai.infrastructure</groupId>
            <artifactId>ai-infrastructure-core</artifactId>
        </dependency>

        <!-- Weaviate Java client -->
        <dependency>
            <groupId>io.weaviate</groupId>
            <artifactId>client</artifactId>
            <version>4.0.0</version>
        </dependency>

        <!-- Spring Boot -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
    </dependencies>
</project>
```

**File**: `ai-infrastructure-module/ai-infrastructure-vector-weaviate/src/main/java/com/ai/infrastructure/vector/weaviate/WeaviateVectorDatabaseAutoConfiguration.java`

```java
package com.ai.infrastructure.vector.weaviate;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.rag.VectorDatabaseService;
import com.ai.infrastructure.rag.SearchableEntityVectorDatabaseService;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import com.ai.infrastructure.config.AIEntityConfigurationLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Auto-configuration for Weaviate vector database
 * 
 * This configuration is automatically loaded when:
 * 1. Weaviate client is on classpath (ConditionalOnClass)
 * 2. ai.vector-db.type=weaviate
 */
@Slf4j
@Configuration
@ConditionalOnClass(name = "io.weaviate.client.WeaviateClient")
@ConditionalOnProperty(
    prefix = "ai.vector-db",
    name = "type",
    havingValue = "weaviate"
)
@EnableConfigurationProperties(AIProviderConfig.class)
public class WeaviateVectorDatabaseAutoConfiguration {

    /**
     * Create WeaviateVectorDatabaseService delegate
     */
    @Bean
    public WeaviateVectorDatabaseService weaviateVectorDatabaseDelegate(AIProviderConfig config) {
        log.info("Creating Weaviate vector database delegate");
        return new WeaviateVectorDatabaseService(config);
    }

    /**
     * Create primary VectorDatabaseService bean
     * This wraps the delegate with SearchableEntityVectorDatabaseService
     */
    @Bean
    @Primary
    public VectorDatabaseService weaviateVectorDatabaseService(
            WeaviateVectorDatabaseService delegate,
            AISearchableEntityRepository searchableEntityRepository,
            AIEntityConfigurationLoader configurationLoader) {
        log.info("Creating Weaviate vector database service");
        return new SearchableEntityVectorDatabaseService(delegate, searchableEntityRepository, configurationLoader);
    }
}
```

**File**: `ai-infrastructure-module/ai-infrastructure-vector-weaviate/src/main/java/com/ai/infrastructure/vector/weaviate/WeaviateVectorDatabaseService.java`

```java
package com.ai.infrastructure.vector.weaviate;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.rag.VectorDatabaseService;
import com.ai.infrastructure.dto.AISearchRequest;
import com.ai.infrastructure.dto.AISearchResponse;
import com.ai.infrastructure.dto.VectorRecord;
// ... imports

/**
 * Weaviate Vector Database Service Implementation
 */
@Slf4j
public class WeaviateVectorDatabaseService implements VectorDatabaseService {
    
    private final AIProviderConfig config;
    private final io.weaviate.client.WeaviateClient client;
    
    public WeaviateVectorDatabaseService(AIProviderConfig config) {
        this.config = config;
        // Initialize Weaviate client
        this.client = initializeWeaviateClient();
    }
    
    // Implement all VectorDatabaseService methods
    @Override
    public String storeVector(String entityType, String entityId, String content, 
                             List<Double> embedding, Map<String, Object> metadata) {
        // Weaviate implementation
    }
    
    // ... other methods
}
```

## Configuration

**application.yml**:
```yaml
ai:
  vector-db:
    type: weaviate  # Options: lucene, pinecone, weaviate, qdrant, milvus, memory
    
    weaviate:
      url: http://localhost:8080
      api-key: ${WEAVIATE_API_KEY}
      scheme: http  # or https
      host: localhost
      port: 8080
```

## Migration Steps

1. [x] **Move existing implementations** from core to modules
2. [x] **Create auto-configuration** for each vector database
3. [x] **Update parent POM** to include new modules
4. [x] **Update tests** to use new modules
5. [ ] **Implement additional providers** (Weaviate, Qdrant, Milvus)

## Benefits

- ✅ Only include vector databases you need
- ✅ Independent versioning
- ✅ Smaller artifacts
- ✅ Easy to add new vector databases


## Roadmap: Additional Provider Modules

The next vector integrations share the same packaging conventions used for Lucene, Pinecone, and the in-memory adapter. Each module will expose a Spring Boot auto-configuration and delegate that implements `VectorDatabaseService`.

| Module | Primary Dependency | Notes | Target Stories |
| --- | --- | --- | --- |
| `ai-infrastructure-vector-weaviate` | `io.weaviate:client` (gRPC/REST) | Requires schema bootstrap helper and API key support. | Service delegate, auto-config, smoke test against mock server. |
| `ai-infrastructure-vector-qdrant` | `io.qdrant:client` | Supports both gRPC and REST; decide default transport. | Batch upsert + payload filtering, configuration samples. |
| `ai-infrastructure-vector-milvus` | `io.milvus:milvus-sdk-java` | Needs connection pooling and health check due to gRPC streaming. | Delegate implementation, metrics wiring, profile for local Milvus container. |

### Implementation Checklist (per module)

1. Create Maven module (`ai-infrastructure-vector-<provider>`), add to parent `<modules>` list and dependency management.
2. Implement `<Provider>VectorDatabaseService` with full CRUD/search parity.
3. Add `<Provider>VectorAutoConfiguration` that exposes delegate plus `SearchableEntityVectorDatabaseService` wrapper.
4. Provide configuration section in `CONFIGURATION_REFERENCE.md` and sample YAML in `integration-tests` resources.
5. Extend integration test matrix with provider-specific profile and smoke test (can be profile-disabled until CI infra is available).
6. Document environment prerequisites (container image, env vars) in this roadmap and `INTEGRATION_TEST_CHANGES.md`.

### Open Questions
- **Testing Strategy:** Decide whether to spin up containers in CI (Testcontainers) or rely on contract tests against vendor mocks.
- **Schema Management:** Determine common abstraction for schema bootstrapping (e.g., vector dimension, metadata fields) to avoid duplication across modules.
- **Resilience:** Evaluate retry/backoff defaults per provider; some SDKs already expose resilient clients.

Capturing this roadmap here keeps the remaining work visible until the modules are implemented. Each bullet can be promoted to a tracked story as prioritization solidifies.
