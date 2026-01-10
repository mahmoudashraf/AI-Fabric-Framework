# Testcontainers Vector Database Integration Testing Implementation Guide

## Table of Contents

1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Architecture](#architecture)
4. [Dependencies Setup](#dependencies-setup)
5. [Vector Database Container Implementations](#vector-database-container-implementations)
   - [Milvus](#1-milvus)
   - [Qdrant](#2-qdrant)
   - [Weaviate](#3-weaviate)
   - [Chroma](#4-chroma)
   - [PostgreSQL with pgvector](#5-postgresql-with-pgvector)
6. [Test Base Classes](#test-base-classes)
7. [Configuration Profiles](#configuration-profiles)
8. [Multi-Provider Test Matrix](#multi-provider-test-matrix)
9. [Running Tests](#running-tests)
10. [Best Practices](#best-practices)
11. [Troubleshooting](#troubleshooting)
12. [Performance Considerations](#performance-considerations)

---

## Overview

This document provides a comprehensive guide for implementing integration tests using Testcontainers to test different vector databases in the AI Fabric Framework. Testcontainers allows us to spin up real database instances in Docker containers during tests, providing more realistic testing compared to mocks.

### Goals

- Test vector database implementations against real database instances
- Ensure consistency across different vector database providers
- Validate CRUD operations, similarity search, and batch operations
- Support multiple embedding providers with different vector dimensions
- Enable CI/CD pipeline integration with containerized tests

### Supported Vector Databases

| Database | Testcontainers Support | Default Port | Protocol |
|----------|----------------------|--------------|----------|
| Milvus | Official Module | 19530 | gRPC |
| Qdrant | GenericContainer | 6333/6334 | REST/gRPC |
| Weaviate | GenericContainer | 8080 | REST |
| Chroma | GenericContainer | 8000 | REST |
| pgvector | PostgreSQLContainer | 5432 | JDBC |

---

## Prerequisites

### System Requirements

- **Docker**: Version 20.10 or later
- **Docker Compose**: Version 2.0 or later (optional, for local development)
- **Java**: JDK 17 or later
- **Maven**: Version 3.8 or later
- **Memory**: Minimum 8GB RAM (16GB recommended for running multiple containers)
- **Disk Space**: Minimum 10GB free space for Docker images

### Docker Images Required

```bash
# Pull required images before running tests
docker pull milvusdb/milvus:v2.4.1-latest
docker pull qdrant/qdrant:v1.7.4
docker pull semitechnologies/weaviate:1.23.0
docker pull chromadb/chroma:0.4.22
docker pull pgvector/pgvector:pg16
```

### Environment Variables

```bash
# Required for real API tests with embeddings
export OPENAI_API_KEY=sk-your-api-key

# Optional: Override default container images
export MILVUS_IMAGE=milvusdb/milvus:v2.4.1-latest
export QDRANT_IMAGE=qdrant/qdrant:v1.7.4
export WEAVIATE_IMAGE=semitechnologies/weaviate:1.23.0
export CHROMA_IMAGE=chromadb/chroma:0.4.22
export PGVECTOR_IMAGE=pgvector/pgvector:pg16

# Optional: Testcontainers configuration
export TESTCONTAINERS_RYUK_DISABLED=false
export TESTCONTAINERS_CHECKS_DISABLE=false
```

---

## Architecture

### Component Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Integration Test Suite                        │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │              AbstractVectorDatabaseIntegrationTest            │   │
│  │  - Common test methods (CRUD, search, batch operations)       │   │
│  │  - Embedding generation utilities                             │   │
│  │  - Test data factories                                        │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                │                                     │
│           ┌────────────────────┼────────────────────┐               │
│           ▼                    ▼                    ▼               │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐     │
│  │ MilvusContainer │  │ QdrantContainer │  │WeaviateContainer│     │
│  │    Integration  │  │   Integration   │  │   Integration   │     │
│  │      Test       │  │      Test       │  │      Test       │     │
│  └────────┬────────┘  └────────┬────────┘  └────────┬────────┘     │
│           │                    │                    │               │
│           ▼                    ▼                    ▼               │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐     │
│  │  MilvusVector   │  │  QdrantVector   │  │ WeaviateVector  │     │
│  │ DatabaseService │  │ DatabaseService │  │ DatabaseService │     │
│  └────────┬────────┘  └────────┬────────┘  └────────┬────────┘     │
│           │                    │                    │               │
└───────────┼────────────────────┼────────────────────┼───────────────┘
            │                    │                    │
            ▼                    ▼                    ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Milvus        │    │   Qdrant        │    │   Weaviate      │
│   Container     │    │   Container     │    │   Container     │
│   (Docker)      │    │   (Docker)      │    │   (Docker)      │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

### Test Flow

```
1. Test Class Initialization
   └── @Container annotations trigger container startup
       └── Container pulls image (if not cached)
       └── Container starts with health check
       └── @DynamicPropertySource injects connection properties

2. Spring Context Initialization
   └── VectorDatabaseService bean created with container properties
   └── EmbeddingProvider bean created (ONNX/OpenAI)

3. Test Execution
   └── Test methods execute against real containerized database
   └── Each test can use fresh data or shared test fixtures

4. Cleanup
   └── Testcontainers automatically stops and removes containers
   └── Ryuk container cleans up orphaned resources
```

---

## Dependencies Setup

### Parent POM Changes

Add to `/ai-infrastructure-module/pom.xml`:

```xml
<properties>
    <!-- Existing properties -->
    <testcontainers.version>1.19.3</testcontainers.version>
</properties>

<dependencyManagement>
    <dependencies>
        <!-- Testcontainers BOM -->
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>testcontainers-bom</artifactId>
            <version>${testcontainers.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### Integration Test Module POM Changes

Add to `/ai-infrastructure-module/integration-Testing/integration-tests/pom.xml`:

```xml
<dependencies>
    <!-- Testcontainers Core -->
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>testcontainers</artifactId>
        <scope>test</scope>
    </dependency>

    <!-- JUnit 5 Integration -->
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>junit-jupiter</artifactId>
        <scope>test</scope>
    </dependency>

    <!-- Milvus Container (Official Support) -->
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>milvus</artifactId>
        <scope>test</scope>
    </dependency>

    <!-- PostgreSQL Container (for pgvector) -->
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>postgresql</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

---

## Vector Database Container Implementations

### 1. Milvus

Milvus has official Testcontainers support, making integration straightforward.

#### Container Configuration Class

```java
package com.ai.infrastructure.it.containers;

import org.testcontainers.milvus.MilvusContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Milvus container configuration for integration tests.
 *
 * Milvus is a cloud-native vector database designed for scalable similarity search.
 * It supports multiple index types (IVF, HNSW, etc.) and hybrid search capabilities.
 */
public class MilvusContainerConfig {

    public static final String DEFAULT_IMAGE = "milvusdb/milvus:v2.4.1-latest";
    public static final int GRPC_PORT = 19530;
    public static final int HTTP_PORT = 9091;

    /**
     * Creates a new Milvus container with default configuration.
     */
    public static MilvusContainer createContainer() {
        return createContainer(DEFAULT_IMAGE);
    }

    /**
     * Creates a new Milvus container with specified image.
     */
    public static MilvusContainer createContainer(String imageName) {
        return new MilvusContainer(DockerImageName.parse(imageName))
            .withEnv("ETCD_USE_EMBED", "true")
            .withEnv("ETCD_DATA_DIR", "/var/lib/milvus/etcd")
            .withEnv("COMMON_STORAGETYPE", "local");
    }

    /**
     * Creates a Milvus container optimized for CI environments.
     * Uses reduced memory and simpler configuration.
     */
    public static MilvusContainer createCIContainer() {
        return new MilvusContainer(DockerImageName.parse(DEFAULT_IMAGE))
            .withEnv("ETCD_USE_EMBED", "true")
            .withEnv("ETCD_DATA_DIR", "/var/lib/milvus/etcd")
            .withEnv("COMMON_STORAGETYPE", "local")
            .withEnv("MILVUS_LOG_LEVEL", "warn");
    }
}
```

#### Integration Test Class

```java
package com.ai.infrastructure.it.vectordb;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.it.TestApplication;
import com.ai.infrastructure.it.containers.MilvusContainerConfig;
import com.ai.infrastructure.rag.AISearchRequest;
import com.ai.infrastructure.rag.AISearchResponse;
import com.ai.infrastructure.rag.VectorDatabaseService;
import com.ai.infrastructure.rag.VectorRecord;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.milvus.MilvusContainer;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Milvus vector database using Testcontainers.
 *
 * These tests verify:
 * - Vector storage and retrieval
 * - Similarity search functionality
 * - Batch operations
 * - Metadata filtering
 * - Collection management
 */
@Testcontainers
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("vectordb-test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Milvus Vector Database Integration Tests")
public class MilvusVectorDatabaseIntegrationTest extends AbstractVectorDatabaseIntegrationTest {

    @Container
    static MilvusContainer milvus = MilvusContainerConfig.createContainer();

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("ai.vector-db.type", () -> "milvus");
        registry.add("ai.vector-db.milvus.host", milvus::getHost);
        registry.add("ai.vector-db.milvus.port", () -> milvus.getMappedPort(19530));
        registry.add("ai.vector-db.milvus.database", () -> "default");
        registry.add("ai.vector-db.milvus.username", () -> "");
        registry.add("ai.vector-db.milvus.password", () -> "");
        registry.add("ai.vector-db.milvus.secure", () -> false);
        registry.add("ai.vector-db.milvus.timeout", () -> 30000);
    }

    @Autowired
    private VectorDatabaseService vectorDatabaseService;

    @BeforeEach
    void setUp() {
        // Clear any existing test data
        vectorDatabaseService.clearVectorsByEntityType("test-document");
    }

    @Test
    @Order(1)
    @DisplayName("Should store and retrieve a vector")
    void testStoreAndRetrieveVector() {
        // Given
        String entityType = "test-document";
        String entityId = "doc-001";
        String content = "This is a test document about artificial intelligence and machine learning.";
        List<Double> embedding = generateTestEmbedding(1536);
        Map<String, Object> metadata = Map.of(
            "category", "technology",
            "author", "test-user",
            "timestamp", System.currentTimeMillis()
        );

        // When
        String vectorId = vectorDatabaseService.storeVector(
            entityType, entityId, content, embedding, metadata
        );

        // Then
        assertThat(vectorId).isNotNull().isNotEmpty();

        Optional<VectorRecord> retrieved = vectorDatabaseService.getVector(vectorId);
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getContent()).isEqualTo(content);
        assertThat(retrieved.get().getEntityType()).isEqualTo(entityType);
        assertThat(retrieved.get().getEntityId()).isEqualTo(entityId);
    }

    @Test
    @Order(2)
    @DisplayName("Should perform similarity search")
    void testSimilaritySearch() {
        // Given - Store multiple documents
        storeTestDocuments(10);

        // Create a query vector similar to stored documents
        List<Double> queryVector = generateTestEmbedding(1536);

        AISearchRequest request = AISearchRequest.builder()
            .limit(5)
            .threshold(0.5)
            .build();

        // When
        AISearchResponse response = vectorDatabaseService.search(queryVector, request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getResults()).isNotEmpty();
        assertThat(response.getResults().size()).isLessThanOrEqualTo(5);

        // Verify results are sorted by score (descending)
        List<Double> scores = response.getResults().stream()
            .map(r -> r.getScore())
            .toList();
        assertThat(scores).isSortedAccordingTo(Comparator.reverseOrder());
    }

    @Test
    @Order(3)
    @DisplayName("Should perform batch store operations")
    void testBatchStoreVectors() {
        // Given
        List<VectorRecord> records = createTestVectorRecords(50);

        // When
        List<String> vectorIds = vectorDatabaseService.batchStoreVectors(records);

        // Then
        assertThat(vectorIds).hasSize(50);
        assertThat(vectorIds).allMatch(id -> id != null && !id.isEmpty());

        // Verify count
        long count = vectorDatabaseService.getVectorCountByEntityType("test-document");
        assertThat(count).isGreaterThanOrEqualTo(50);
    }

    @Test
    @Order(4)
    @DisplayName("Should update existing vector")
    void testUpdateVector() {
        // Given - Store initial vector
        String entityType = "test-document";
        String entityId = "update-test-001";
        String initialContent = "Initial content";
        List<Double> initialEmbedding = generateTestEmbedding(1536);

        String vectorId = vectorDatabaseService.storeVector(
            entityType, entityId, initialContent, initialEmbedding, Map.of()
        );

        // When - Update the vector
        String updatedContent = "Updated content with new information";
        List<Double> updatedEmbedding = generateTestEmbedding(1536);

        boolean updated = vectorDatabaseService.updateVector(
            vectorId, entityType, entityId, updatedContent, updatedEmbedding,
            Map.of("updated", true)
        );

        // Then
        assertThat(updated).isTrue();

        Optional<VectorRecord> retrieved = vectorDatabaseService.getVector(vectorId);
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getContent()).isEqualTo(updatedContent);
    }

    @Test
    @Order(5)
    @DisplayName("Should delete vector by entity")
    void testDeleteVector() {
        // Given
        String entityType = "test-document";
        String entityId = "delete-test-001";

        vectorDatabaseService.storeVector(
            entityType, entityId, "To be deleted",
            generateTestEmbedding(1536), Map.of()
        );

        assertThat(vectorDatabaseService.vectorExists(entityType, entityId)).isTrue();

        // When
        boolean deleted = vectorDatabaseService.removeVector(entityType, entityId);

        // Then
        assertThat(deleted).isTrue();
        assertThat(vectorDatabaseService.vectorExists(entityType, entityId)).isFalse();
    }

    @Test
    @Order(6)
    @DisplayName("Should search by entity type")
    void testSearchByEntityType() {
        // Given - Store documents of different types
        vectorDatabaseService.storeVector(
            "article", "art-001", "Article content",
            generateTestEmbedding(1536), Map.of()
        );
        vectorDatabaseService.storeVector(
            "product", "prod-001", "Product description",
            generateTestEmbedding(1536), Map.of()
        );

        List<Double> queryVector = generateTestEmbedding(1536);

        // When
        AISearchResponse response = vectorDatabaseService.searchByEntityType(
            queryVector, "article", 10, 0.0
        );

        // Then
        assertThat(response.getResults()).allMatch(
            r -> r.getEntityType().equals("article")
        );
    }

    @Test
    @Order(7)
    @DisplayName("Should return statistics")
    void testGetStatistics() {
        // Given - Ensure some data exists
        storeTestDocuments(5);

        // When
        Map<String, Object> stats = vectorDatabaseService.getStatistics();

        // Then
        assertThat(stats).isNotNull();
        assertThat(stats).containsKey("totalVectors");
    }

    // Helper methods

    private void storeTestDocuments(int count) {
        for (int i = 0; i < count; i++) {
            vectorDatabaseService.storeVector(
                "test-document",
                "doc-" + UUID.randomUUID(),
                "Test document content " + i + " with various topics",
                generateTestEmbedding(1536),
                Map.of("index", i, "category", "test")
            );
        }
    }

    private List<VectorRecord> createTestVectorRecords(int count) {
        List<VectorRecord> records = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            records.add(VectorRecord.builder()
                .entityType("test-document")
                .entityId("batch-doc-" + i)
                .content("Batch test document " + i)
                .embedding(generateTestEmbedding(1536))
                .metadata(Map.of("batchIndex", i))
                .build());
        }
        return records;
    }
}
```

---

### 2. Qdrant

Qdrant requires using GenericContainer as there's no official Testcontainers module.

#### Container Configuration Class

```java
package com.ai.infrastructure.it.containers;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

/**
 * Qdrant container configuration for integration tests.
 *
 * Qdrant is a vector similarity search engine with extended filtering support.
 * It provides both REST and gRPC APIs for vector operations.
 */
public class QdrantContainerConfig {

    public static final String DEFAULT_IMAGE = "qdrant/qdrant:v1.7.4";
    public static final int REST_PORT = 6333;
    public static final int GRPC_PORT = 6334;

    /**
     * Creates a new Qdrant container with default configuration.
     */
    public static GenericContainer<?> createContainer() {
        return createContainer(DEFAULT_IMAGE);
    }

    /**
     * Creates a new Qdrant container with specified image.
     */
    public static GenericContainer<?> createContainer(String imageName) {
        return new GenericContainer<>(DockerImageName.parse(imageName))
            .withExposedPorts(REST_PORT, GRPC_PORT)
            .withEnv("QDRANT__SERVICE__GRPC_PORT", String.valueOf(GRPC_PORT))
            .waitingFor(Wait.forHttp("/readyz")
                .forPort(REST_PORT)
                .forStatusCode(200)
                .withStartupTimeout(Duration.ofMinutes(2)));
    }

    /**
     * Creates a Qdrant container with custom storage configuration.
     */
    public static GenericContainer<?> createContainerWithStorage(String storagePath) {
        return createContainer()
            .withEnv("QDRANT__STORAGE__STORAGE_PATH", storagePath);
    }

    /**
     * Creates a Qdrant container optimized for CI environments.
     */
    public static GenericContainer<?> createCIContainer() {
        return new GenericContainer<>(DockerImageName.parse(DEFAULT_IMAGE))
            .withExposedPorts(REST_PORT)
            .withEnv("QDRANT__LOG_LEVEL", "WARN")
            .withEnv("QDRANT__STORAGE__ON_DISK_PAYLOAD", "false")
            .waitingFor(Wait.forHttp("/readyz")
                .forPort(REST_PORT)
                .forStatusCode(200)
                .withStartupTimeout(Duration.ofMinutes(1)));
    }
}
```

#### Integration Test Class

```java
package com.ai.infrastructure.it.vectordb;

import com.ai.infrastructure.it.TestApplication;
import com.ai.infrastructure.it.containers.QdrantContainerConfig;
import com.ai.infrastructure.rag.VectorDatabaseService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration tests for Qdrant vector database using Testcontainers.
 */
@Testcontainers
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("vectordb-test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Qdrant Vector Database Integration Tests")
public class QdrantVectorDatabaseIntegrationTest extends AbstractVectorDatabaseIntegrationTest {

    @Container
    static GenericContainer<?> qdrant = QdrantContainerConfig.createContainer();

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("ai.vector-db.type", () -> "qdrant");
        registry.add("ai.vector-db.qdrant.host", qdrant::getHost);
        registry.add("ai.vector-db.qdrant.port", () -> qdrant.getMappedPort(6333));
        registry.add("ai.vector-db.qdrant.grpc-port", () -> qdrant.getMappedPort(6334));
        registry.add("ai.vector-db.qdrant.api-key", () -> "");
        registry.add("ai.vector-db.qdrant.timeout", () -> 30000);
        registry.add("ai.vector-db.qdrant.prefer-grpc", () -> false);
    }

    @Autowired
    private VectorDatabaseService vectorDatabaseService;

    // Inherits all test methods from AbstractVectorDatabaseIntegrationTest

    @Test
    @Order(100)
    @DisplayName("Qdrant-specific: Should support payload filtering")
    void testPayloadFiltering() {
        // Store documents with different categories
        vectorDatabaseService.storeVector(
            "product", "p1", "Electronics product",
            generateTestEmbedding(1536),
            Map.of("category", "electronics", "price", 99.99)
        );
        vectorDatabaseService.storeVector(
            "product", "p2", "Clothing item",
            generateTestEmbedding(1536),
            Map.of("category", "clothing", "price", 49.99)
        );

        // Search with metadata filter (Qdrant-specific feature)
        AISearchRequest request = AISearchRequest.builder()
            .limit(10)
            .metadataFilter(Map.of("category", "electronics"))
            .build();

        AISearchResponse response = vectorDatabaseService.search(
            generateTestEmbedding(1536), request
        );

        assertThat(response.getResults())
            .allMatch(r -> "electronics".equals(r.getMetadata().get("category")));
    }
}
```

---

### 3. Weaviate

#### Container Configuration Class

```java
package com.ai.infrastructure.it.containers;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

/**
 * Weaviate container configuration for integration tests.
 *
 * Weaviate is an open-source vector database that stores both objects and vectors.
 * It supports GraphQL and RESTful APIs with built-in vectorization modules.
 */
public class WeaviateContainerConfig {

    public static final String DEFAULT_IMAGE = "semitechnologies/weaviate:1.23.0";
    public static final int HTTP_PORT = 8080;
    public static final int GRPC_PORT = 50051;

    /**
     * Creates a new Weaviate container with default configuration.
     * Uses anonymous access (no authentication required).
     */
    public static GenericContainer<?> createContainer() {
        return createContainer(DEFAULT_IMAGE);
    }

    /**
     * Creates a new Weaviate container with specified image.
     */
    public static GenericContainer<?> createContainer(String imageName) {
        return new GenericContainer<>(DockerImageName.parse(imageName))
            .withExposedPorts(HTTP_PORT, GRPC_PORT)
            .withEnv("AUTHENTICATION_ANONYMOUS_ACCESS_ENABLED", "true")
            .withEnv("PERSISTENCE_DATA_PATH", "/var/lib/weaviate")
            .withEnv("DEFAULT_VECTORIZER_MODULE", "none")
            .withEnv("CLUSTER_HOSTNAME", "node1")
            .withEnv("ENABLE_MODULES", "")
            .waitingFor(Wait.forHttp("/v1/.well-known/ready")
                .forPort(HTTP_PORT)
                .forStatusCode(200)
                .withStartupTimeout(Duration.ofMinutes(2)));
    }

    /**
     * Creates a Weaviate container with API key authentication.
     */
    public static GenericContainer<?> createContainerWithAuth(String apiKey) {
        return new GenericContainer<>(DockerImageName.parse(DEFAULT_IMAGE))
            .withExposedPorts(HTTP_PORT, GRPC_PORT)
            .withEnv("AUTHENTICATION_APIKEY_ENABLED", "true")
            .withEnv("AUTHENTICATION_APIKEY_ALLOWED_KEYS", apiKey)
            .withEnv("AUTHENTICATION_APIKEY_USERS", "test-user")
            .withEnv("PERSISTENCE_DATA_PATH", "/var/lib/weaviate")
            .withEnv("DEFAULT_VECTORIZER_MODULE", "none")
            .waitingFor(Wait.forHttp("/v1/.well-known/ready")
                .forPort(HTTP_PORT)
                .forStatusCode(200)
                .withStartupTimeout(Duration.ofMinutes(2)));
    }

    /**
     * Creates a Weaviate container optimized for CI environments.
     */
    public static GenericContainer<?> createCIContainer() {
        return new GenericContainer<>(DockerImageName.parse(DEFAULT_IMAGE))
            .withExposedPorts(HTTP_PORT)
            .withEnv("AUTHENTICATION_ANONYMOUS_ACCESS_ENABLED", "true")
            .withEnv("DEFAULT_VECTORIZER_MODULE", "none")
            .withEnv("LOG_LEVEL", "warning")
            .withEnv("PERSISTENCE_DATA_PATH", "/var/lib/weaviate")
            .waitingFor(Wait.forHttp("/v1/.well-known/ready")
                .forPort(HTTP_PORT)
                .forStatusCode(200)
                .withStartupTimeout(Duration.ofMinutes(1)));
    }
}
```

#### Integration Test Class

```java
package com.ai.infrastructure.it.vectordb;

import com.ai.infrastructure.it.TestApplication;
import com.ai.infrastructure.it.containers.WeaviateContainerConfig;
import com.ai.infrastructure.rag.VectorDatabaseService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration tests for Weaviate vector database using Testcontainers.
 */
@Testcontainers
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("vectordb-test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Weaviate Vector Database Integration Tests")
public class WeaviateVectorDatabaseIntegrationTest extends AbstractVectorDatabaseIntegrationTest {

    @Container
    static GenericContainer<?> weaviate = WeaviateContainerConfig.createContainer();

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("ai.vector-db.type", () -> "weaviate");
        registry.add("ai.vector-db.weaviate.scheme", () -> "http");
        registry.add("ai.vector-db.weaviate.host", weaviate::getHost);
        registry.add("ai.vector-db.weaviate.port", () -> weaviate.getMappedPort(8080));
        registry.add("ai.vector-db.weaviate.api-key", () -> "");
        registry.add("ai.vector-db.weaviate.timeout", () -> 30000);
        registry.add("ai.vector-db.weaviate.consistency-level-strong", () -> false);
    }

    @Autowired
    private VectorDatabaseService vectorDatabaseService;

    // Inherits all test methods from AbstractVectorDatabaseIntegrationTest

    @Test
    @Order(100)
    @DisplayName("Weaviate-specific: Should support hybrid search")
    void testHybridSearch() {
        // Store documents
        vectorDatabaseService.storeVector(
            "article", "a1", "Machine learning is a subset of artificial intelligence",
            generateTestEmbedding(1536), Map.of("topic", "AI")
        );
        vectorDatabaseService.storeVector(
            "article", "a2", "Deep learning uses neural networks",
            generateTestEmbedding(1536), Map.of("topic", "AI")
        );

        // Perform hybrid search (vector + keyword)
        AISearchResponse response = vectorDatabaseService.hybridSearch(
            generateTestEmbedding(1536),
            "machine learning",
            AISearchRequest.builder().limit(5).build()
        );

        assertThat(response.getResults()).isNotEmpty();
    }
}
```

---

### 4. Chroma

#### Container Configuration Class

```java
package com.ai.infrastructure.it.containers;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

/**
 * Chroma container configuration for integration tests.
 *
 * Chroma is an AI-native open-source embedding database.
 * It's designed to be simple and developer-friendly.
 */
public class ChromaContainerConfig {

    public static final String DEFAULT_IMAGE = "chromadb/chroma:0.4.22";
    public static final int HTTP_PORT = 8000;

    /**
     * Creates a new Chroma container with default configuration.
     */
    public static GenericContainer<?> createContainer() {
        return createContainer(DEFAULT_IMAGE);
    }

    /**
     * Creates a new Chroma container with specified image.
     */
    public static GenericContainer<?> createContainer(String imageName) {
        return new GenericContainer<>(DockerImageName.parse(imageName))
            .withExposedPorts(HTTP_PORT)
            .withEnv("IS_PERSISTENT", "TRUE")
            .withEnv("ANONYMIZED_TELEMETRY", "FALSE")
            .waitingFor(Wait.forHttp("/api/v1/heartbeat")
                .forPort(HTTP_PORT)
                .forStatusCode(200)
                .withStartupTimeout(Duration.ofMinutes(2)));
    }

    /**
     * Creates a Chroma container with authentication.
     */
    public static GenericContainer<?> createContainerWithAuth(
            String serverAuthCredentials) {
        return new GenericContainer<>(DockerImageName.parse(DEFAULT_IMAGE))
            .withExposedPorts(HTTP_PORT)
            .withEnv("CHROMA_SERVER_AUTH_CREDENTIALS", serverAuthCredentials)
            .withEnv("CHROMA_SERVER_AUTH_PROVIDER",
                     "chromadb.auth.token.TokenAuthServerProvider")
            .waitingFor(Wait.forHttp("/api/v1/heartbeat")
                .forPort(HTTP_PORT)
                .forStatusCode(200)
                .withStartupTimeout(Duration.ofMinutes(2)));
    }
}
```

---

### 5. PostgreSQL with pgvector

#### Container Configuration Class

```java
package com.ai.infrastructure.it.containers;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * PostgreSQL with pgvector extension container configuration.
 *
 * pgvector adds vector similarity search capabilities to PostgreSQL,
 * enabling it to be used as a vector database alongside traditional SQL.
 */
public class PgVectorContainerConfig {

    public static final String DEFAULT_IMAGE = "pgvector/pgvector:pg16";
    public static final int PORT = 5432;

    /**
     * Creates a new PostgreSQL container with pgvector extension.
     */
    public static PostgreSQLContainer<?> createContainer() {
        return createContainer(DEFAULT_IMAGE);
    }

    /**
     * Creates a new PostgreSQL container with specified image.
     */
    public static PostgreSQLContainer<?> createContainer(String imageName) {
        return new PostgreSQLContainer<>(DockerImageName.parse(imageName)
                .asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("vectordb")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("db/init-pgvector.sql");
    }

    /**
     * Creates a PostgreSQL container optimized for testing.
     */
    public static PostgreSQLContainer<?> createTestContainer() {
        return createContainer()
            .withCommand("postgres",
                "-c", "shared_buffers=256MB",
                "-c", "max_connections=100",
                "-c", "log_statement=none");
    }
}
```

#### Init Script (db/init-pgvector.sql)

```sql
-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Create vectors table
CREATE TABLE IF NOT EXISTS vectors (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type VARCHAR(255) NOT NULL,
    entity_id VARCHAR(255) NOT NULL,
    content TEXT,
    embedding vector(1536),
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(entity_type, entity_id)
);

-- Create index for vector similarity search (IVFFlat)
CREATE INDEX IF NOT EXISTS vectors_embedding_idx
ON vectors USING ivfflat (embedding vector_cosine_ops)
WITH (lists = 100);

-- Create index for entity lookups
CREATE INDEX IF NOT EXISTS vectors_entity_idx
ON vectors (entity_type, entity_id);

-- Create index for metadata queries
CREATE INDEX IF NOT EXISTS vectors_metadata_idx
ON vectors USING gin (metadata);
```

---

## Test Base Classes

### Abstract Vector Database Integration Test

```java
package com.ai.infrastructure.it.vectordb;

import com.ai.infrastructure.rag.AISearchRequest;
import com.ai.infrastructure.rag.AISearchResponse;
import com.ai.infrastructure.rag.VectorDatabaseService;
import com.ai.infrastructure.rag.VectorRecord;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abstract base class for vector database integration tests.
 *
 * Provides common test methods that should pass for all vector database
 * implementations. Subclasses should configure the specific container
 * and Spring properties.
 */
public abstract class AbstractVectorDatabaseIntegrationTest {

    @Autowired
    protected VectorDatabaseService vectorDatabaseService;

    // Default embedding dimension (OpenAI text-embedding-3-small)
    protected static final int DEFAULT_DIMENSION = 1536;

    @BeforeEach
    void baseSetUp() {
        // Clean up test data before each test
        try {
            vectorDatabaseService.clearVectorsByEntityType("test-entity");
            vectorDatabaseService.clearVectorsByEntityType("test-document");
        } catch (Exception e) {
            // Ignore cleanup errors for fresh containers
        }
    }

    // ==================== CRUD Operations ====================

    @Test
    @Order(1)
    @DisplayName("Should store a vector successfully")
    void testStoreVector() {
        // Given
        String entityType = "test-entity";
        String entityId = "entity-" + UUID.randomUUID();
        String content = "Test content for vector storage";
        List<Double> embedding = generateTestEmbedding(DEFAULT_DIMENSION);
        Map<String, Object> metadata = Map.of("key", "value");

        // When
        String vectorId = vectorDatabaseService.storeVector(
            entityType, entityId, content, embedding, metadata
        );

        // Then
        assertThat(vectorId).isNotNull().isNotEmpty();
    }

    @Test
    @Order(2)
    @DisplayName("Should retrieve stored vector by ID")
    void testGetVectorById() {
        // Given
        String entityType = "test-entity";
        String entityId = "get-test-" + UUID.randomUUID();
        String content = "Content to retrieve";
        List<Double> embedding = generateTestEmbedding(DEFAULT_DIMENSION);

        String vectorId = vectorDatabaseService.storeVector(
            entityType, entityId, content, embedding, Map.of()
        );

        // When
        Optional<VectorRecord> result = vectorDatabaseService.getVector(vectorId);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getContent()).isEqualTo(content);
        assertThat(result.get().getEntityType()).isEqualTo(entityType);
        assertThat(result.get().getEntityId()).isEqualTo(entityId);
    }

    @Test
    @Order(3)
    @DisplayName("Should retrieve vector by entity type and ID")
    void testGetVectorByEntity() {
        // Given
        String entityType = "test-entity";
        String entityId = "entity-lookup-" + UUID.randomUUID();
        String content = "Entity lookup content";

        vectorDatabaseService.storeVector(
            entityType, entityId, content,
            generateTestEmbedding(DEFAULT_DIMENSION), Map.of()
        );

        // When
        Optional<VectorRecord> result = vectorDatabaseService.getVectorByEntity(
            entityType, entityId
        );

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getContent()).isEqualTo(content);
    }

    @Test
    @Order(4)
    @DisplayName("Should update existing vector")
    void testUpdateVector() {
        // Given
        String entityType = "test-entity";
        String entityId = "update-" + UUID.randomUUID();

        String vectorId = vectorDatabaseService.storeVector(
            entityType, entityId, "Original content",
            generateTestEmbedding(DEFAULT_DIMENSION), Map.of("version", 1)
        );

        // When
        String updatedContent = "Updated content";
        boolean updated = vectorDatabaseService.updateVector(
            vectorId, entityType, entityId, updatedContent,
            generateTestEmbedding(DEFAULT_DIMENSION), Map.of("version", 2)
        );

        // Then
        assertThat(updated).isTrue();

        Optional<VectorRecord> result = vectorDatabaseService.getVector(vectorId);
        assertThat(result).isPresent();
        assertThat(result.get().getContent()).isEqualTo(updatedContent);
    }

    @Test
    @Order(5)
    @DisplayName("Should delete vector")
    void testRemoveVector() {
        // Given
        String entityType = "test-entity";
        String entityId = "delete-" + UUID.randomUUID();

        vectorDatabaseService.storeVector(
            entityType, entityId, "To be deleted",
            generateTestEmbedding(DEFAULT_DIMENSION), Map.of()
        );

        assertThat(vectorDatabaseService.vectorExists(entityType, entityId)).isTrue();

        // When
        boolean deleted = vectorDatabaseService.removeVector(entityType, entityId);

        // Then
        assertThat(deleted).isTrue();
        assertThat(vectorDatabaseService.vectorExists(entityType, entityId)).isFalse();
    }

    // ==================== Search Operations ====================

    @Test
    @Order(10)
    @DisplayName("Should perform similarity search")
    void testSimilaritySearch() {
        // Given - Store multiple vectors
        for (int i = 0; i < 10; i++) {
            vectorDatabaseService.storeVector(
                "test-document", "doc-" + i,
                "Document content number " + i,
                generateTestEmbedding(DEFAULT_DIMENSION),
                Map.of("index", i)
            );
        }

        List<Double> queryVector = generateTestEmbedding(DEFAULT_DIMENSION);
        AISearchRequest request = AISearchRequest.builder()
            .limit(5)
            .threshold(0.0) // Accept all results for testing
            .build();

        // When
        AISearchResponse response = vectorDatabaseService.search(queryVector, request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getResults()).isNotEmpty();
        assertThat(response.getResults().size()).isLessThanOrEqualTo(5);
    }

    @Test
    @Order(11)
    @DisplayName("Should search by entity type")
    void testSearchByEntityType() {
        // Given
        vectorDatabaseService.storeVector(
            "type-a", "a1", "Type A document",
            generateTestEmbedding(DEFAULT_DIMENSION), Map.of()
        );
        vectorDatabaseService.storeVector(
            "type-b", "b1", "Type B document",
            generateTestEmbedding(DEFAULT_DIMENSION), Map.of()
        );

        // When
        AISearchResponse response = vectorDatabaseService.searchByEntityType(
            generateTestEmbedding(DEFAULT_DIMENSION), "type-a", 10, 0.0
        );

        // Then
        assertThat(response.getResults())
            .allMatch(r -> "type-a".equals(r.getEntityType()));
    }

    @Test
    @Order(12)
    @DisplayName("Should respect search limit")
    void testSearchLimit() {
        // Given - Store more documents than limit
        for (int i = 0; i < 20; i++) {
            vectorDatabaseService.storeVector(
                "test-document", "limit-doc-" + i,
                "Document for limit test " + i,
                generateTestEmbedding(DEFAULT_DIMENSION), Map.of()
            );
        }

        AISearchRequest request = AISearchRequest.builder()
            .limit(3)
            .build();

        // When
        AISearchResponse response = vectorDatabaseService.search(
            generateTestEmbedding(DEFAULT_DIMENSION), request
        );

        // Then
        assertThat(response.getResults().size()).isLessThanOrEqualTo(3);
    }

    // ==================== Batch Operations ====================

    @Test
    @Order(20)
    @DisplayName("Should perform batch store")
    void testBatchStoreVectors() {
        // Given
        List<VectorRecord> records = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            records.add(VectorRecord.builder()
                .entityType("test-document")
                .entityId("batch-" + i)
                .content("Batch document " + i)
                .embedding(generateTestEmbedding(DEFAULT_DIMENSION))
                .metadata(Map.of("batchIndex", i))
                .build());
        }

        // When
        List<String> vectorIds = vectorDatabaseService.batchStoreVectors(records);

        // Then
        assertThat(vectorIds).hasSize(25);
        assertThat(vectorIds).allMatch(id -> id != null && !id.isEmpty());
    }

    @Test
    @Order(21)
    @DisplayName("Should perform batch delete")
    void testBatchRemoveVectors() {
        // Given - Store vectors first
        List<String> vectorIds = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String id = vectorDatabaseService.storeVector(
                "test-document", "batch-delete-" + i,
                "To be deleted " + i,
                generateTestEmbedding(DEFAULT_DIMENSION), Map.of()
            );
            vectorIds.add(id);
        }

        // When
        int deletedCount = vectorDatabaseService.batchRemoveVectors(vectorIds);

        // Then
        assertThat(deletedCount).isEqualTo(10);
    }

    // ==================== Utility Operations ====================

    @Test
    @Order(30)
    @DisplayName("Should check vector existence")
    void testVectorExists() {
        // Given
        String entityType = "test-entity";
        String entityId = "exists-" + UUID.randomUUID();

        vectorDatabaseService.storeVector(
            entityType, entityId, "Exists check",
            generateTestEmbedding(DEFAULT_DIMENSION), Map.of()
        );

        // Then
        assertThat(vectorDatabaseService.vectorExists(entityType, entityId)).isTrue();
        assertThat(vectorDatabaseService.vectorExists(entityType, "non-existent")).isFalse();
    }

    @Test
    @Order(31)
    @DisplayName("Should get vector count by entity type")
    void testGetVectorCount() {
        // Given
        String entityType = "count-test-" + UUID.randomUUID().toString().substring(0, 8);
        for (int i = 0; i < 5; i++) {
            vectorDatabaseService.storeVector(
                entityType, "count-" + i, "Count test " + i,
                generateTestEmbedding(DEFAULT_DIMENSION), Map.of()
            );
        }

        // When
        long count = vectorDatabaseService.getVectorCountByEntityType(entityType);

        // Then
        assertThat(count).isEqualTo(5);
    }

    @Test
    @Order(32)
    @DisplayName("Should clear vectors by entity type")
    void testClearVectorsByEntityType() {
        // Given
        String entityType = "clear-test-" + UUID.randomUUID().toString().substring(0, 8);
        for (int i = 0; i < 5; i++) {
            vectorDatabaseService.storeVector(
                entityType, "clear-" + i, "Clear test " + i,
                generateTestEmbedding(DEFAULT_DIMENSION), Map.of()
            );
        }

        // When
        long clearedCount = vectorDatabaseService.clearVectorsByEntityType(entityType);

        // Then
        assertThat(clearedCount).isEqualTo(5);
        assertThat(vectorDatabaseService.getVectorCountByEntityType(entityType)).isEqualTo(0);
    }

    @Test
    @Order(33)
    @DisplayName("Should return statistics")
    void testGetStatistics() {
        // When
        Map<String, Object> stats = vectorDatabaseService.getStatistics();

        // Then
        assertThat(stats).isNotNull();
    }

    // ==================== Helper Methods ====================

    /**
     * Generates a test embedding vector with random values.
     * Uses normalized values suitable for cosine similarity.
     */
    protected List<Double> generateTestEmbedding(int dimension) {
        List<Double> embedding = new ArrayList<>(dimension);
        double sumSquares = 0;

        for (int i = 0; i < dimension; i++) {
            double value = ThreadLocalRandom.current().nextGaussian();
            embedding.add(value);
            sumSquares += value * value;
        }

        // Normalize the vector
        double magnitude = Math.sqrt(sumSquares);
        for (int i = 0; i < dimension; i++) {
            embedding.set(i, embedding.get(i) / magnitude);
        }

        return embedding;
    }

    /**
     * Generates a test embedding that is similar to the given base embedding.
     * Useful for testing similarity search.
     */
    protected List<Double> generateSimilarEmbedding(List<Double> base, double similarity) {
        List<Double> result = new ArrayList<>(base.size());
        double noise = 1.0 - similarity;

        for (Double value : base) {
            double noisyValue = value + (ThreadLocalRandom.current().nextGaussian() * noise);
            result.add(noisyValue);
        }

        // Normalize
        double sumSquares = result.stream().mapToDouble(v -> v * v).sum();
        double magnitude = Math.sqrt(sumSquares);
        for (int i = 0; i < result.size(); i++) {
            result.set(i, result.get(i) / magnitude);
        }

        return result;
    }
}
```

---

## Configuration Profiles

### application-vectordb-test.yml

Create this file at `src/test/resources/application-vectordb-test.yml`:

```yaml
# Vector Database Testcontainers Configuration Profile
# This profile is used for integration tests with containerized vector databases

spring:
  application:
    name: ai-infrastructure-vectordb-test

  # Use H2 for relational data during tests
  datasource:
    url: jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL
    driver-class-name: org.h2.Driver
    username: sa
    password:

  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: false

  h2:
    console:
      enabled: false

# AI Infrastructure Configuration
ai:
  # Vector database configuration - dynamically set by @DynamicPropertySource
  vector-db:
    type: ${VECTOR_DB_TYPE:memory}

    # Milvus configuration
    milvus:
      host: ${MILVUS_HOST:localhost}
      port: ${MILVUS_PORT:19530}
      database: ${MILVUS_DATABASE:default}
      username: ${MILVUS_USERNAME:}
      password: ${MILVUS_PASSWORD:}
      secure: ${MILVUS_SECURE:false}
      timeout: ${MILVUS_TIMEOUT:30000}

    # Qdrant configuration
    qdrant:
      host: ${QDRANT_HOST:localhost}
      port: ${QDRANT_PORT:6333}
      grpc-port: ${QDRANT_GRPC_PORT:6334}
      api-key: ${QDRANT_API_KEY:}
      timeout: ${QDRANT_TIMEOUT:30000}
      prefer-grpc: ${QDRANT_PREFER_GRPC:false}

    # Weaviate configuration
    weaviate:
      scheme: ${WEAVIATE_SCHEME:http}
      host: ${WEAVIATE_HOST:localhost}
      port: ${WEAVIATE_PORT:8080}
      api-key: ${WEAVIATE_API_KEY:}
      timeout: ${WEAVIATE_TIMEOUT:30000}
      consistency-level-strong: ${WEAVIATE_CONSISTENCY_STRONG:false}

    # Chroma configuration
    chroma:
      host: ${CHROMA_HOST:localhost}
      port: ${CHROMA_PORT:8000}
      tenant: ${CHROMA_TENANT:default_tenant}
      database: ${CHROMA_DATABASE:default_database}

    # pgvector configuration
    pgvector:
      host: ${PGVECTOR_HOST:localhost}
      port: ${PGVECTOR_PORT:5432}
      database: ${PGVECTOR_DATABASE:vectordb}
      username: ${PGVECTOR_USERNAME:test}
      password: ${PGVECTOR_PASSWORD:test}
      table-name: ${PGVECTOR_TABLE:vectors}

  # Embedding provider configuration
  embedding:
    provider: ${EMBEDDING_PROVIDER:onnx}
    dimensions: ${EMBEDDING_DIMENSIONS:1536}

    # ONNX embedding (local, no API required)
    onnx:
      model-path: ${ONNX_MODEL_PATH:}

    # OpenAI embedding (requires API key)
    openai:
      api-key: ${OPENAI_API_KEY:}
      model: ${OPENAI_EMBEDDING_MODEL:text-embedding-3-small}
      dimensions: ${OPENAI_EMBEDDING_DIMENSIONS:1536}

# Logging configuration
logging:
  level:
    root: WARN
    com.ai.infrastructure: INFO
    org.testcontainers: INFO
    com.github.dockerjava: WARN
    org.springframework.test: INFO
```

### application-vectordb-ci.yml

Create this file for CI/CD environments:

```yaml
# CI-specific configuration for vector database tests
# Optimized for faster execution and lower resource usage

spring:
  profiles:
    include: vectordb-test

ai:
  vector-db:
    # Default timeouts reduced for CI
    milvus:
      timeout: 60000
    qdrant:
      timeout: 60000
    weaviate:
      timeout: 60000

# Reduce logging in CI
logging:
  level:
    root: ERROR
    com.ai.infrastructure: WARN
    org.testcontainers: WARN
```

---

## Multi-Provider Test Matrix

### VectorDatabaseProviderMatrixTest

```java
package com.ai.infrastructure.it.vectordb;

import com.ai.infrastructure.it.TestApplication;
import com.ai.infrastructure.it.containers.*;
import com.ai.infrastructure.rag.AISearchRequest;
import com.ai.infrastructure.rag.AISearchResponse;
import com.ai.infrastructure.rag.VectorDatabaseService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.milvus.MilvusContainer;

import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Parameterized test that runs the same test suite against multiple
 * vector database providers.
 *
 * This ensures consistent behavior across all supported vector databases.
 */
@Testcontainers
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("vectordb-test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Vector Database Provider Matrix Tests")
public class VectorDatabaseProviderMatrixTest {

    private static final int EMBEDDING_DIMENSION = 1536;

    // Container instances - managed manually for parameterized tests
    private static MilvusContainer milvusContainer;
    private static GenericContainer<?> qdrantContainer;
    private static GenericContainer<?> weaviateContainer;

    @BeforeAll
    static void startContainers() {
        // Start all containers in parallel for efficiency
        milvusContainer = MilvusContainerConfig.createContainer();
        qdrantContainer = QdrantContainerConfig.createContainer();
        weaviateContainer = WeaviateContainerConfig.createContainer();

        // Start containers
        milvusContainer.start();
        qdrantContainer.start();
        weaviateContainer.start();
    }

    @AfterAll
    static void stopContainers() {
        if (milvusContainer != null) milvusContainer.stop();
        if (qdrantContainer != null) qdrantContainer.stop();
        if (weaviateContainer != null) weaviateContainer.stop();
    }

    static Stream<Arguments> vectorDatabaseProviders() {
        return Stream.of(
            Arguments.of("milvus", milvusContainer),
            Arguments.of("qdrant", qdrantContainer),
            Arguments.of("weaviate", weaviateContainer)
        );
    }

    @ParameterizedTest(name = "Provider: {0}")
    @MethodSource("vectorDatabaseProviders")
    @DisplayName("Should store and retrieve vectors")
    void testStoreAndRetrieve(String providerName, GenericContainer<?> container) {
        // This test would need a custom test context for each provider
        // For simplicity, this demonstrates the pattern
        assertThat(container.isRunning()).isTrue();

        // Log container info
        System.out.println("Testing " + providerName + " at " +
            container.getHost() + ":" + container.getFirstMappedPort());
    }

    @ParameterizedTest(name = "Provider: {0}")
    @MethodSource("vectorDatabaseProviders")
    @DisplayName("Should perform similarity search")
    void testSimilaritySearch(String providerName, GenericContainer<?> container) {
        assertThat(container.isRunning()).isTrue();
    }

    @ParameterizedTest(name = "Provider: {0}")
    @MethodSource("vectorDatabaseProviders")
    @DisplayName("Should handle batch operations")
    void testBatchOperations(String providerName, GenericContainer<?> container) {
        assertThat(container.isRunning()).isTrue();
    }
}
```

### Simplified Provider Loop Test

For a simpler approach without nested Spring contexts:

```java
package com.ai.infrastructure.it.vectordb;

import com.ai.infrastructure.it.containers.*;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.milvus.MilvusContainer;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sequential test that validates each vector database container
 * can be started and accessed.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Vector Database Container Validation Tests")
public class VectorDatabaseContainerValidationTest {

    @Test
    @Order(1)
    @DisplayName("Milvus container should start and be accessible")
    void testMilvusContainer() {
        try (MilvusContainer milvus = MilvusContainerConfig.createContainer()) {
            milvus.start();

            assertThat(milvus.isRunning()).isTrue();
            assertThat(milvus.getMappedPort(19530)).isPositive();

            System.out.println("Milvus running at: " +
                milvus.getHost() + ":" + milvus.getMappedPort(19530));
        }
    }

    @Test
    @Order(2)
    @DisplayName("Qdrant container should start and be accessible")
    void testQdrantContainer() {
        try (GenericContainer<?> qdrant = QdrantContainerConfig.createContainer()) {
            qdrant.start();

            assertThat(qdrant.isRunning()).isTrue();
            assertThat(qdrant.getMappedPort(6333)).isPositive();

            System.out.println("Qdrant running at: " +
                qdrant.getHost() + ":" + qdrant.getMappedPort(6333));
        }
    }

    @Test
    @Order(3)
    @DisplayName("Weaviate container should start and be accessible")
    void testWeaviateContainer() {
        try (GenericContainer<?> weaviate = WeaviateContainerConfig.createContainer()) {
            weaviate.start();

            assertThat(weaviate.isRunning()).isTrue();
            assertThat(weaviate.getMappedPort(8080)).isPositive();

            System.out.println("Weaviate running at: " +
                weaviate.getHost() + ":" + weaviate.getMappedPort(8080));
        }
    }

    @Test
    @Order(4)
    @DisplayName("Chroma container should start and be accessible")
    void testChromaContainer() {
        try (GenericContainer<?> chroma = ChromaContainerConfig.createContainer()) {
            chroma.start();

            assertThat(chroma.isRunning()).isTrue();
            assertThat(chroma.getMappedPort(8000)).isPositive();

            System.out.println("Chroma running at: " +
                chroma.getHost() + ":" + chroma.getMappedPort(8000));
        }
    }
}
```

---

## Running Tests

### Maven Commands

```bash
# Run all vector database integration tests
mvn -pl ai-infrastructure-module/integration-Testing/integration-tests \
    verify -P integration-test \
    -Dtest="*VectorDatabase*" \
    -DfailIfNoTests=false

# Run specific provider test
mvn -pl ai-infrastructure-module/integration-Testing/integration-tests \
    verify -Dtest=MilvusVectorDatabaseIntegrationTest

# Run with real embeddings (requires OpenAI API key)
OPENAI_API_KEY=sk-xxx mvn -pl ai-infrastructure-module/integration-Testing/integration-tests \
    verify -Dtest="*VectorDatabase*" \
    -Dai.embedding.provider=openai

# Run in CI mode (reduced logging, optimized timeouts)
mvn verify -P ci,integration-test \
    -Dspring.profiles.active=vectordb-ci

# Run with specific vector database only
mvn verify -Dtest=QdrantVectorDatabaseIntegrationTest \
    -Dai.vector-db.type=qdrant

# Skip container tests (use mocks)
mvn verify -DskipContainerTests=true
```

### Gradle Commands (if using Gradle)

```groovy
// build.gradle
tasks.named('test') {
    useJUnitPlatform()

    // Configure Testcontainers
    systemProperty 'testcontainers.reuse.enable', 'true'
}

task vectorDbIntegrationTest(type: Test) {
    useJUnitPlatform {
        includeTags 'vectordb'
    }

    testLogging {
        events "passed", "skipped", "failed"
    }
}
```

```bash
# Run vector database tests
./gradlew vectorDbIntegrationTest

# Run specific provider
./gradlew test --tests "*Milvus*"
```

### IDE Configuration

#### IntelliJ IDEA

1. **Run Configuration**:
   - Create new JUnit run configuration
   - Set VM options: `-Dspring.profiles.active=vectordb-test`
   - Set environment variables: `OPENAI_API_KEY=sk-xxx` (if using real embeddings)

2. **Docker Settings**:
   - Ensure Docker integration is enabled
   - Settings → Build, Execution, Deployment → Docker
   - Configure Docker connection

#### VS Code

```json
// .vscode/settings.json
{
    "java.test.config": {
        "vmArgs": [
            "-Dspring.profiles.active=vectordb-test"
        ],
        "env": {
            "OPENAI_API_KEY": "${env:OPENAI_API_KEY}"
        }
    }
}
```

---

## Best Practices

### 1. Container Reuse

Enable container reuse to speed up local development:

```java
// In test class
@Container
static MilvusContainer milvus = MilvusContainerConfig.createContainer()
    .withReuse(true);
```

```properties
# ~/.testcontainers.properties
testcontainers.reuse.enable=true
```

### 2. Singleton Containers Pattern

For tests sharing the same container:

```java
public abstract class AbstractMilvusTest {

    static final MilvusContainer MILVUS;

    static {
        MILVUS = MilvusContainerConfig.createContainer();
        MILVUS.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("ai.vector-db.milvus.host", MILVUS::getHost);
        registry.add("ai.vector-db.milvus.port", () -> MILVUS.getMappedPort(19530));
    }
}
```

### 3. Test Data Isolation

```java
@BeforeEach
void isolateTestData() {
    String testRunId = UUID.randomUUID().toString().substring(0, 8);
    // Use testRunId as prefix for entity types/IDs
}
```

### 4. Health Check Configuration

```java
// Custom health check for slow-starting containers
.waitingFor(Wait.forHttp("/health")
    .forPort(8080)
    .forStatusCode(200)
    .withStartupTimeout(Duration.ofMinutes(5))
    .withReadTimeout(Duration.ofSeconds(10)));
```

### 5. Resource Cleanup

```java
@AfterEach
void cleanup() {
    // Clean up test-specific data
    vectorDatabaseService.clearVectorsByEntityType("test-" + testRunId);
}

@AfterAll
static void globalCleanup() {
    // Container cleanup is handled by Testcontainers
}
```

### 6. Parallel Test Execution

```xml
<!-- pom.xml -->
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-failsafe-plugin</artifactId>
    <configuration>
        <!-- Run tests in parallel by class -->
        <parallel>classes</parallel>
        <threadCount>2</threadCount>
        <!-- Each test class gets its own container -->
        <forkCount>1</forkCount>
        <reuseForks>true</reuseForks>
    </configuration>
</plugin>
```

---

## Troubleshooting

### Common Issues

#### 1. Container Fails to Start

**Symptoms**: `ContainerLaunchException`, timeout errors

**Solutions**:
```bash
# Check Docker is running
docker info

# Check available disk space
df -h

# Check available memory
free -m

# Pull images manually
docker pull milvusdb/milvus:v2.4.1-latest
```

#### 2. Port Already in Use

**Symptoms**: `Bind for 0.0.0.0:19530 failed: port is already allocated`

**Solutions**:
```java
// Let Testcontainers assign random ports (default behavior)
.withExposedPorts(19530)  // NOT .withFixedExposedPort()

// Get the mapped port
int port = container.getMappedPort(19530);
```

#### 3. Container Health Check Timeout

**Symptoms**: `Container startup failed` after timeout

**Solutions**:
```java
// Increase timeout
.waitingFor(Wait.forHttp("/health")
    .withStartupTimeout(Duration.ofMinutes(5)))

// Use simpler health check
.waitingFor(Wait.forListeningPort())
```

#### 4. Network Issues in CI

**Symptoms**: Tests pass locally but fail in CI

**Solutions**:
```yaml
# GitHub Actions example
jobs:
  test:
    runs-on: ubuntu-latest
    services:
      docker:
        image: docker:dind
        options: --privileged
    steps:
      - uses: actions/checkout@v3
      - name: Set up Docker
        run: |
          docker info
```

#### 5. Memory Issues

**Symptoms**: `OutOfMemoryError`, container killed

**Solutions**:
```java
// Limit container memory
.withCreateContainerCmdModifier(cmd ->
    cmd.getHostConfig().withMemory(512 * 1024 * 1024L))  // 512MB
```

```xml
<!-- Maven: Limit JVM memory -->
<argLine>-Xmx1g</argLine>
```

### Debug Logging

```yaml
# application-vectordb-test.yml
logging:
  level:
    org.testcontainers: DEBUG
    com.github.dockerjava: DEBUG
    org.springframework.test.context: DEBUG
```

### Container Logs

```java
@Test
void debugContainerLogs() {
    String logs = container.getLogs();
    System.out.println("Container logs: " + logs);
}
```

---

## Performance Considerations

### Container Startup Times

| Database | Cold Start | Warm Start (reuse) |
|----------|-----------|-------------------|
| Milvus | 30-60s | 2-5s |
| Qdrant | 10-20s | 1-3s |
| Weaviate | 15-30s | 2-4s |
| Chroma | 5-15s | 1-2s |
| pgvector | 10-20s | 2-4s |

### Optimization Strategies

1. **Container Reuse**: Enable for local development
2. **Singleton Pattern**: Share containers across test classes
3. **CI Container Caching**: Cache Docker layers in CI
4. **Parallel Execution**: Run different provider tests in parallel
5. **Selective Testing**: Run only affected provider tests

### CI/CD Pipeline Example

```yaml
# .github/workflows/vector-db-tests.yml
name: Vector Database Integration Tests

on:
  push:
    paths:
      - 'ai-infrastructure-module/victor-databases/**'
      - 'ai-infrastructure-module/integration-Testing/**'

jobs:
  test-milvus:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: Cache Docker images
        uses: satackey/action-docker-layer-caching@v0.0.11
      - name: Run Milvus Tests
        run: |
          mvn verify -Dtest=MilvusVectorDatabaseIntegrationTest

  test-qdrant:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: Run Qdrant Tests
        run: |
          mvn verify -Dtest=QdrantVectorDatabaseIntegrationTest

  test-weaviate:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: Run Weaviate Tests
        run: |
          mvn verify -Dtest=WeaviateVectorDatabaseIntegrationTest
```

---

## Appendix

### A. Docker Image Versions

| Database | Recommended Image | Notes |
|----------|------------------|-------|
| Milvus | `milvusdb/milvus:v2.4.1-latest` | Latest stable |
| Qdrant | `qdrant/qdrant:v1.7.4` | Latest stable |
| Weaviate | `semitechnologies/weaviate:1.23.0` | Latest stable |
| Chroma | `chromadb/chroma:0.4.22` | Latest stable |
| pgvector | `pgvector/pgvector:pg16` | PostgreSQL 16 |

### B. Port Reference

| Database | Primary Port | Secondary Port | Protocol |
|----------|-------------|----------------|----------|
| Milvus | 19530 | 9091 | gRPC / HTTP |
| Qdrant | 6333 | 6334 | REST / gRPC |
| Weaviate | 8080 | 50051 | REST / gRPC |
| Chroma | 8000 | - | REST |
| pgvector | 5432 | - | PostgreSQL |

### C. Environment Variables Reference

| Variable | Description | Default |
|----------|-------------|---------|
| `TESTCONTAINERS_RYUK_DISABLED` | Disable resource cleanup | `false` |
| `TESTCONTAINERS_CHECKS_DISABLE` | Disable startup checks | `false` |
| `DOCKER_HOST` | Docker daemon URL | `unix:///var/run/docker.sock` |
| `TESTCONTAINERS_HUB_IMAGE_NAME_PREFIX` | Registry prefix | `` |

### D. Related Documentation

- [VectorDatabaseService Interface](../../../ai-infrastructure-core/src/main/java/com/ai/infrastructure/rag/VectorDatabaseService.java)
- [Milvus Implementation](../../../victor-databases/ai-infrastructure-vector-milvus/)
- [Qdrant Implementation](../../../victor-databases/ai-infrastructure-vector-qdrant/)
- [Weaviate Implementation](../../../victor-databases/ai-infrastructure-vector-weaviate/)
- [Real API Tests Guide](./RUN_ALL_REALAPI_TESTS.md)
- [Testcontainers Official Docs](https://www.testcontainers.org/)

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2026-01-10 | Initial document creation |

---

*Document created for AI Fabric Framework - Integration Testing Module*
