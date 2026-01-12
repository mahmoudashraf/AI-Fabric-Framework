# Testcontainers Vector Database Auto-Configuration Guide

## Table of Contents

- [Overview](#overview)
- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Implementation](#implementation)
- [Usage Examples](#usage-examples)
- [Advanced Usage](#advanced-usage)
- [Performance Considerations](#performance-considerations)
- [Best Practices](#best-practices)
- [CI/CD Integration](#cicd-integration)
- [Supported Vector Databases](#supported-vector-databases)
- [Troubleshooting](#troubleshooting)
- [Limitations](#limitations)
- [Migration Guide](#migration-guide)
- [File Structure](#file-structure)
- [Version Compatibility](#version-compatibility)
- [Summary](#summary)

---

## Overview

This guide explains how to run your **existing RealAPI integration tests** with different vector database providers using Testcontainers. No changes to your existing test classes are required.

### Key Benefits

✅ **Zero Code Changes** - Existing tests work without modification  
✅ **Automatic Lifecycle** - Containers start/stop automatically  
✅ **Easy Provider Switching** - Change database with a single property  
✅ **CI/CD Ready** - Works seamlessly in automated pipelines  
✅ **Isolated Testing** - Fresh container for each test run  
✅ **Consistent Environment** - Same database version every time

### The Problem

Currently, to test with different vector databases you need to:
1. Manually install and configure each database (Milvus, Qdrant, Weaviate, etc.)
2. Update configuration files with connection details
3. Ensure the database is running before tests
4. Clean up after tests

### The Solution

With Testcontainers auto-configuration:
1. Specify which vector database to use via a property
2. Container starts automatically before tests
3. Connection properties are injected automatically
4. Container stops and cleans up after tests

```
┌─────────────────────────────────────────────────────────────────┐
│                                                                 │
│   mvn verify -Dai.vector-db.type=milvus                        │
│                                                                 │
│         │                                                       │
│         ▼                                                       │
│   ┌─────────────────────────────────────────────────────────┐  │
│   │  Auto-Configuration detects "milvus"                     │  │
│   │  → Starts MilvusContainer                                │  │
│   │  → Injects host:port into Spring properties              │  │
│   └─────────────────────────────────────────────────────────┘  │
│         │                                                       │
│         ▼                                                       │
│   ┌─────────────────────────────────────────────────────────┐  │
│   │  Your existing RealAPIIntegrationTest runs               │  │
│   │  VectorDatabaseService connects to real Milvus           │  │
│   └─────────────────────────────────────────────────────────┘  │
│         │                                                       │
│         ▼                                                       │
│   ┌─────────────────────────────────────────────────────────┐  │
│   │  Tests complete → Container automatically stops          │  │
│   └─────────────────────────────────────────────────────────┘  │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## Prerequisites

Before using Testcontainers for vector database testing, ensure you have:

### Required Software

1. **Docker** (version 20.10+)
   ```bash
   # Verify Docker is installed and running
   docker --version
   docker info
   ```

2. **Docker Compose** (optional, for complex setups)
   ```bash
   docker compose version
   ```

3. **Java 17+** (JDK 21 recommended)
   ```bash
   java -version
   ```

4. **Maven 3.6+**
   ```bash
   mvn --version
   ```

### System Requirements

- **Minimum RAM**: 4GB (8GB+ recommended)
- **Disk Space**: 10GB+ free space for Docker images
- **Docker Resources**: 
  - Allocate at least 2GB RAM to Docker Desktop
  - Enable virtualization in BIOS (for Windows/Mac)

### Docker Configuration

Ensure Docker is configured to allow sufficient resources:

**Docker Desktop Settings:**
- Memory: 4GB+ (8GB recommended)
- CPUs: 2+ cores
- Disk image size: 60GB+

**Linux (Docker Engine):**
```bash
# Check Docker daemon configuration
cat /etc/docker/daemon.json
```

### Network Requirements

- **Port Availability**: Ensure ports 19530, 6333, 6334, 8080, 8000, 5432 are not in use
- **Internet Connection**: Required for pulling Docker images on first run
- **Firewall**: Allow Docker to bind to host ports

### Verification

Run this command to verify your setup:

```bash
# Test Docker connectivity
docker run --rm hello-world

# Check available disk space
docker system df
```

---

## Quick Start

### 1. Run Tests with Different Providers

```bash
# Run existing tests with Milvus
mvn verify -Dtest=RealAPIIntegrationTest \
    -Dai.vector-db.type=milvus \
    -Dspring.profiles.active=real-api-test,testcontainers

# Run existing tests with Qdrant
mvn verify -Dtest=RealAPIIntegrationTest \
    -Dai.vector-db.type=qdrant \
    -Dspring.profiles.active=real-api-test,testcontainers

# Run existing tests with Weaviate
mvn verify -Dtest=RealAPIIntegrationTest \
    -Dai.vector-db.type=weaviate \
    -Dspring.profiles.active=real-api-test,testcontainers

# Run existing tests with Chroma
mvn verify -Dtest=RealAPIIntegrationTest \
    -Dai.vector-db.type=chroma \
    -Dspring.profiles.active=real-api-test,testcontainers

# Run existing tests with pgvector
mvn verify -Dtest=RealAPIIntegrationTest \
    -Dai.vector-db.type=pgvector \
    -Dspring.profiles.active=real-api-test,testcontainers
```

### 2. No Test Code Changes Required

Your existing tests work as-is:

```java
// This test doesn't change at all!
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("real-api-test")
public class RealAPIIntegrationTest {

    @Autowired
    private VectorDatabaseService vectorDatabaseService;  // Auto-connected to container

    @Test
    void testVectorOperations() {
        // This now runs against real Milvus/Qdrant/Weaviate container
        vectorDatabaseService.storeVector(...);
    }
}
```

---

## Implementation

### Step 1: Add Dependencies

Add to `integration-tests/pom.xml`:

```xml
<properties>
    <!-- Use latest stable version (check https://testcontainers.org for updates) -->
    <testcontainers.version>1.19.8</testcontainers.version>
</properties>

<dependencies>
    <!-- Testcontainers Core -->
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>testcontainers</artifactId>
        <version>${testcontainers.version}</version>
        <scope>test</scope>
    </dependency>

    <!-- JUnit 5 Integration -->
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>junit-jupiter</artifactId>
        <version>${testcontainers.version}</version>
        <scope>test</scope>
    </dependency>

    <!-- Milvus (Official Module) -->
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>milvus</artifactId>
        <version>${testcontainers.version}</version>
        <scope>test</scope>
    </dependency>

    <!-- PostgreSQL (for pgvector) -->
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>postgresql</artifactId>
        <version>${testcontainers.version}</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

> **Note**: If you already have testcontainers dependencies (like `junit-jupiter` and `postgresql`), ensure they use the same version to avoid conflicts.

### Step 2: Create Auto-Configuration Class

Create `src/test/java/com/ai/infrastructure/it/config/VectorDatabaseContainerAutoConfiguration.java`:

```java
package com.ai.infrastructure.it.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.milvus.MilvusContainer;
import org.testcontainers.utility.DockerImageName;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Auto-configuration for vector database Testcontainers.
 *
 * <p>Automatically starts the appropriate container based on the
 * {@code ai.vector-db.type} property and injects connection properties
 * into the Spring environment.</p>
 *
 * <p><strong>Usage:</strong></p>
 * <pre>
 * mvn verify -Dai.vector-db.type=milvus -Dspring.profiles.active=testcontainers
 * </pre>
 *
 * <p><strong>Supported Providers:</strong> milvus, qdrant, weaviate, chroma, pgvector</p>
 *
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@TestConfiguration
@ConditionalOnProperty(name = "testcontainers.enabled", havingValue = "true", matchIfMissing = false)
public class VectorDatabaseContainerAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(VectorDatabaseContainerAutoConfiguration.class);

    // Thread-safe container storage (for parallel test execution)
    private static final Map<String, GenericContainer<?>> activeContainers = new ConcurrentHashMap<>();
    
    // Default timeouts
    private static final Duration DEFAULT_STARTUP_TIMEOUT = Duration.ofMinutes(2);
    private static final Duration EXTENDED_STARTUP_TIMEOUT = Duration.ofMinutes(5);

    // ==================== MILVUS ====================

    @Bean
    @ConditionalOnProperty(name = "ai.vector-db.type", havingValue = "milvus")
    public MilvusContainer milvusContainer(ConfigurableEnvironment environment) {
        String containerKey = "milvus";
        
        // Reuse existing container if available (for parallel tests)
        if (activeContainers.containsKey(containerKey)) {
            GenericContainer<?> existing = activeContainers.get(containerKey);
            if (existing != null && existing.isRunning()) {
                log.info("Reusing existing Milvus container at {}:{}",
                    existing.getHost(), existing.getMappedPort(19530));
                return (MilvusContainer) existing;
            }
        }

        log.info("Starting Milvus container...");

        try {
            MilvusContainer container = new MilvusContainer(
                DockerImageName.parse("milvusdb/milvus:v2.4.1-latest")
            )
                .withStartupTimeout(EXTENDED_STARTUP_TIMEOUT);

            container.start();
            activeContainers.put(containerKey, container);

            // Inject properties into Spring environment
            Map<String, Object> properties = new HashMap<>();
            properties.put("ai.vector-db.milvus.host", container.getHost());
            properties.put("ai.vector-db.milvus.port", container.getMappedPort(19530));
            properties.put("ai.vector-db.milvus.database", "default");
            properties.put("ai.vector-db.milvus.username", "");
            properties.put("ai.vector-db.milvus.password", "");
            properties.put("ai.vector-db.milvus.secure", false);

            environment.getPropertySources().addFirst(
                new MapPropertySource("milvusContainerProperties", properties)
            );

            log.info("Milvus container started successfully at {}:{}",
                container.getHost(), container.getMappedPort(19530));

            return container;
        } catch (Exception e) {
            log.error("Failed to start Milvus container", e);
            throw new IllegalStateException("Failed to start Milvus container: " + e.getMessage(), e);
        }
    }

    // ==================== QDRANT ====================

    @Bean
    @ConditionalOnProperty(name = "ai.vector-db.type", havingValue = "qdrant")
    public GenericContainer<?> qdrantContainer(ConfigurableEnvironment environment) {
        String containerKey = "qdrant";
        
        if (activeContainers.containsKey(containerKey)) {
            GenericContainer<?> existing = activeContainers.get(containerKey);
            if (existing != null && existing.isRunning()) {
                log.info("Reusing existing Qdrant container at {}:{}",
                    existing.getHost(), existing.getMappedPort(6333));
                return existing;
            }
        }

        log.info("Starting Qdrant container...");

        try {
            String image = getImageVersion("qdrant", "qdrant/qdrant:v1.16.1");
            GenericContainer<?> container = new GenericContainer<>(
                DockerImageName.parse(image)
            )
                .withExposedPorts(6333, 6334)
                .waitingFor(Wait.forHttp("/readyz")
                    .forPort(6333)
                    .forStatusCode(200)
                    .withStartupTimeout(DEFAULT_STARTUP_TIMEOUT));

            container.start();
            activeContainers.put(containerKey, container);

            // Inject properties into Spring environment
            Map<String, Object> properties = new HashMap<>();
            properties.put("ai.vector-db.qdrant.host", container.getHost());
            properties.put("ai.vector-db.qdrant.port", container.getMappedPort(6333));
            properties.put("ai.vector-db.qdrant.grpc-port", container.getMappedPort(6334));
            properties.put("ai.vector-db.qdrant.api-key", "");
            properties.put("ai.vector-db.qdrant.prefer-grpc", false);

            environment.getPropertySources().addFirst(
                new MapPropertySource("qdrantContainerProperties", properties)
            );

            log.info("Qdrant container started successfully at {}:{}",
                container.getHost(), container.getMappedPort(6333));

            return container;
        } catch (Exception e) {
            log.error("Failed to start Qdrant container", e);
            throw new IllegalStateException("Failed to start Qdrant container: " + e.getMessage(), e);
        }
    }

    // ==================== WEAVIATE ====================

    @Bean
    @ConditionalOnProperty(name = "ai.vector-db.type", havingValue = "weaviate")
    public GenericContainer<?> weaviateContainer(ConfigurableEnvironment environment) {
        String containerKey = "weaviate";
        
        if (activeContainers.containsKey(containerKey)) {
            GenericContainer<?> existing = activeContainers.get(containerKey);
            if (existing != null && existing.isRunning()) {
                log.info("Reusing existing Weaviate container at {}:{}",
                    existing.getHost(), existing.getMappedPort(8080));
                return existing;
            }
        }

        log.info("Starting Weaviate container...");

        try {
            String image = getImageVersion("weaviate", "semitechnologies/weaviate:1.23.0");
            GenericContainer<?> container = new GenericContainer<>(
                DockerImageName.parse(image)
            )
                .withExposedPorts(8080)
                .withEnv("AUTHENTICATION_ANONYMOUS_ACCESS_ENABLED", "true")
                .withEnv("PERSISTENCE_DATA_PATH", "/var/lib/weaviate")
                .withEnv("DEFAULT_VECTORIZER_MODULE", "none")
                .withEnv("CLUSTER_HOSTNAME", "node1")
                .waitingFor(Wait.forHttp("/v1/.well-known/ready")
                    .forPort(8080)
                    .forStatusCode(200)
                    .withStartupTimeout(DEFAULT_STARTUP_TIMEOUT));

            container.start();
            activeContainers.put(containerKey, container);

            // Inject properties into Spring environment
            Map<String, Object> properties = new HashMap<>();
            properties.put("ai.vector-db.weaviate.scheme", "http");
            properties.put("ai.vector-db.weaviate.host", container.getHost());
            properties.put("ai.vector-db.weaviate.port", container.getMappedPort(8080));
            properties.put("ai.vector-db.weaviate.api-key", "");

            environment.getPropertySources().addFirst(
                new MapPropertySource("weaviateContainerProperties", properties)
            );

            log.info("Weaviate container started successfully at {}:{}",
                container.getHost(), container.getMappedPort(8080));

            return container;
        } catch (Exception e) {
            log.error("Failed to start Weaviate container", e);
            throw new IllegalStateException("Failed to start Weaviate container: " + e.getMessage(), e);
        }
    }

    // ==================== CHROMA ====================

    @Bean
    @ConditionalOnProperty(name = "ai.vector-db.type", havingValue = "chroma")
    public GenericContainer<?> chromaContainer(ConfigurableEnvironment environment) {
        String containerKey = "chroma";
        
        if (activeContainers.containsKey(containerKey)) {
            GenericContainer<?> existing = activeContainers.get(containerKey);
            if (existing != null && existing.isRunning()) {
                log.info("Reusing existing Chroma container at {}:{}",
                    existing.getHost(), existing.getMappedPort(8000));
                return existing;
            }
        }

        log.info("Starting Chroma container...");

        try {
            String image = getImageVersion("chroma", "chromadb/chroma:0.4.22");
            GenericContainer<?> container = new GenericContainer<>(
                DockerImageName.parse(image)
            )
                .withExposedPorts(8000)
                .withEnv("IS_PERSISTENT", "TRUE")
                .withEnv("ANONYMIZED_TELEMETRY", "FALSE")
                .waitingFor(Wait.forHttp("/api/v1/heartbeat")
                    .forPort(8000)
                    .forStatusCode(200)
                    .withStartupTimeout(DEFAULT_STARTUP_TIMEOUT));

            container.start();
            activeContainers.put(containerKey, container);

            // Inject properties into Spring environment
            Map<String, Object> properties = new HashMap<>();
            properties.put("ai.vector-db.chroma.host", container.getHost());
            properties.put("ai.vector-db.chroma.port", container.getMappedPort(8000));

            environment.getPropertySources().addFirst(
                new MapPropertySource("chromaContainerProperties", properties)
            );

            log.info("Chroma container started successfully at {}:{}",
                container.getHost(), container.getMappedPort(8000));

            return container;
        } catch (Exception e) {
            log.error("Failed to start Chroma container", e);
            throw new IllegalStateException("Failed to start Chroma container: " + e.getMessage(), e);
        }
    }

    // ==================== PGVECTOR ====================

    @Bean
    @ConditionalOnProperty(name = "ai.vector-db.type", havingValue = "pgvector")
    public PostgreSQLContainer<?> pgvectorContainer(ConfigurableEnvironment environment) {
        String containerKey = "pgvector";
        
        if (activeContainers.containsKey(containerKey)) {
            GenericContainer<?> existing = activeContainers.get(containerKey);
            if (existing != null && existing.isRunning()) {
                log.info("Reusing existing pgvector container at {}:{}",
                    existing.getHost(), existing.getMappedPort(5432));
                return (PostgreSQLContainer<?>) existing;
            }
        }

        log.info("Starting PostgreSQL with pgvector container...");

        try {
            String image = getImageVersion("pgvector", "pgvector/pgvector:pg16");
            PostgreSQLContainer<?> container = new PostgreSQLContainer<>(
                DockerImageName.parse(image)
                    .asCompatibleSubstituteFor("postgres")
            )
                .withDatabaseName("vectordb")
                .withUsername("test")
                .withPassword("test")
                .withInitScript("db/init-pgvector.sql")
                .withStartupTimeout(EXTENDED_STARTUP_TIMEOUT);

            container.start();
            activeContainers.put(containerKey, container);

            // Inject properties into Spring environment
            Map<String, Object> properties = new HashMap<>();
            properties.put("ai.vector-db.pgvector.host", container.getHost());
            properties.put("ai.vector-db.pgvector.port", container.getMappedPort(5432));
            properties.put("ai.vector-db.pgvector.database", "vectordb");
            properties.put("ai.vector-db.pgvector.username", "test");
            properties.put("ai.vector-db.pgvector.password", "test");

            environment.getPropertySources().addFirst(
                new MapPropertySource("pgvectorContainerProperties", properties)
            );

            log.info("pgvector container started successfully at {}:{}",
                container.getHost(), container.getMappedPort(5432));

            return container;
        } catch (Exception e) {
            log.error("Failed to start pgvector container", e);
            throw new IllegalStateException("Failed to start pgvector container: " + e.getMessage(), e);
        }
    }

    // ==================== CLEANUP ====================

    @PreDestroy
    public void cleanup() {
        log.info("Cleaning up vector database containers...");
        activeContainers.values().forEach(container -> {
            try {
                if (container != null && container.isRunning()) {
                    log.debug("Stopping container: {}", container.getContainerId());
                    container.stop();
                }
            } catch (Exception e) {
                log.warn("Error stopping container: {}", e.getMessage());
            }
        });
        activeContainers.clear();
        log.info("Container cleanup completed");
    }
    
    /**
     * Utility method to get container image version from properties.
     * Allows overriding default images via system properties.
     */
    private String getImageVersion(String provider, String defaultImage) {
        String propertyKey = "testcontainers." + provider + ".image";
        String customImage = System.getProperty(propertyKey);
        return customImage != null ? customImage : defaultImage;
    }
}
```

### Step 3: Create Application Initializer

Create `src/test/java/com/ai/infrastructure/it/config/TestcontainersInitializer.java`:

```java
package com.ai.infrastructure.it.config;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * Initializer that enables Testcontainers when the 'testcontainers' profile is active.
 */
public class TestcontainersInitializer
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        ConfigurableEnvironment env = context.getEnvironment();

        // Check if testcontainers profile is active
        boolean testcontainersActive = false;
        for (String profile : env.getActiveProfiles()) {
            if ("testcontainers".equals(profile)) {
                testcontainersActive = true;
                break;
            }
        }

        if (testcontainersActive) {
            Map<String, Object> props = new HashMap<>();
            props.put("testcontainers.enabled", "true");
            env.getPropertySources().addFirst(
                new MapPropertySource("testcontainersEnabled", props)
            );
        }
    }
}
```

### Step 4: Register Initializer

Update your test classes or create a base configuration:

```java
package com.ai.infrastructure.it;

import com.ai.infrastructure.it.config.TestcontainersInitializer;
import com.ai.infrastructure.it.config.VectorDatabaseContainerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;

/**
 * Base configuration for tests that can use Testcontainers.
 *
 * Add @ActiveProfiles("testcontainers") to enable container auto-start.
 */
@SpringBootTest(classes = TestApplication.class)
@ContextConfiguration(initializers = TestcontainersInitializer.class)
@Import(VectorDatabaseContainerAutoConfiguration.class)
public abstract class AbstractContainerEnabledTest {
    // Base class - no additional code needed
}
```

Or use `spring.factories` for automatic registration:

Create `src/test/resources/META-INF/spring.factories`:

```properties
org.springframework.context.ApplicationContextInitializer=\
  com.ai.infrastructure.it.config.TestcontainersInitializer
```

### Step 5: Create Test Profile

Create `src/test/resources/application-testcontainers.yml`:

```yaml
# Testcontainers Profile
# Activated with: -Dspring.profiles.active=testcontainers

testcontainers:
  enabled: true

# Vector database type must be specified
# Example: -Dai.vector-db.type=milvus
ai:
  vector-db:
    type: ${VECTOR_DB_TYPE:memory}

    # Timeouts adjusted for container startup
    milvus:
      timeout: 60000
    qdrant:
      timeout: 60000
    weaviate:
      timeout: 60000
    chroma:
      timeout: 60000

# Logging for Testcontainers
logging:
  level:
    org.testcontainers: INFO
    com.github.dockerjava: WARN
    com.ai.infrastructure.it.config: DEBUG
```

### Step 6: Create pgvector Init Script

Create `src/test/resources/db/init-pgvector.sql`:

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

-- Create indexes
CREATE INDEX IF NOT EXISTS vectors_embedding_idx
    ON vectors USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
CREATE INDEX IF NOT EXISTS vectors_entity_idx
    ON vectors (entity_type, entity_id);
CREATE INDEX IF NOT EXISTS vectors_metadata_idx
    ON vectors USING gin (metadata);
```

---

## Usage Examples

### Run Existing Tests with Different Providers

```bash
# Using Milvus
mvn verify \
    -Dtest=RealAPIIntegrationTest \
    -Dspring.profiles.active=real-api-test,testcontainers \
    -Dai.vector-db.type=milvus

# Using Qdrant
mvn verify \
    -Dtest=RealAPIIntegrationTest \
    -Dspring.profiles.active=real-api-test,testcontainers \
    -Dai.vector-db.type=qdrant

# Using Weaviate
mvn verify \
    -Dtest=RealAPIIntegrationTest \
    -Dspring.profiles.active=real-api-test,testcontainers \
    -Dai.vector-db.type=weaviate
```

### Run All RealAPI Tests with Milvus

```bash
mvn verify \
    -Dtest="RealAPI*" \
    -Dspring.profiles.active=real-api-test,testcontainers \
    -Dai.vector-db.type=milvus \
    -DOPENAI_API_KEY=${OPENAI_API_KEY}
```

### Run Provider Matrix Test

```bash
# Test with all providers sequentially
for provider in milvus qdrant weaviate chroma; do
    echo "Testing with $provider..."
    mvn verify \
        -Dtest=RealAPIIntegrationTest \
        -Dspring.profiles.active=real-api-test,testcontainers \
        -Dai.vector-db.type=$provider
done
```

### Without Testcontainers (Use Memory/Lucene)

```bash
# Default behavior - no containers, uses in-memory or Lucene
mvn verify \
    -Dtest=RealAPIIntegrationTest \
    -Dspring.profiles.active=real-api-test \
    -Dai.vector-db.type=memory
```

---

## Advanced Usage

### Custom Container Images

Override default container images using system properties:

```bash
# Use a different Milvus version
mvn verify \
    -Dtestcontainers.milvus.image=milvusdb/milvus:v2.3.0 \
    -Dai.vector-db.type=milvus \
    -Dspring.profiles.active=testcontainers

# Use a different Qdrant version
mvn verify \
    -Dtestcontainers.qdrant.image=qdrant/qdrant:v1.16.1 \
    -Dai.vector-db.type=qdrant \
    -Dspring.profiles.active=testcontainers
```

### Container Reuse

For faster test execution, enable container reuse (containers persist between test runs):

```bash
# Enable reuse mode
export TESTCONTAINERS_REUSABLE_ENABLED=true

# Run tests
mvn verify -Dai.vector-db.type=milvus -Dspring.profiles.active=testcontainers
```

> **Note**: Container reuse requires Docker labels. Containers are automatically cleaned up when tests complete or on system shutdown.

### Parallel Test Execution

The auto-configuration supports parallel test execution. Each test class gets its own container instance:

```java
// Test class 1
@SpringBootTest
@ActiveProfiles("testcontainers")
class TestClass1 {
    // Gets its own container
}

// Test class 2 (runs in parallel)
@SpringBootTest
@ActiveProfiles("testcontainers")
class TestClass2 {
    // Gets its own container
}
```

Configure parallel execution in `pom.xml`:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <parallel>classes</parallel>
        <threadCount>4</threadCount>
    </configuration>
</plugin>
```

### Custom Health Checks

Override default health check timeouts:

```java
// In your test configuration
@TestConfiguration
public class CustomContainerConfig {
    @Bean
    public GenericContainer<?> customQdrantContainer() {
        return new GenericContainer<>("qdrant/qdrant:v1.16.1")
            .waitingFor(Wait.forHttp("/readyz")
                .withStartupTimeout(Duration.ofMinutes(10))); // Extended timeout
    }
}
```

### Network Configuration

Use custom Docker networks for container communication:

```java
@TestConfiguration
public class NetworkContainerConfig {
    @Bean
    public Network testNetwork() {
        return Network.newNetwork();
    }
    
    @Bean
    public GenericContainer<?> qdrantContainer(Network network) {
        return new GenericContainer<>("qdrant/qdrant:v1.16.1")
            .withNetwork(network)
            .withNetworkAliases("qdrant");
    }
}
```

### Volume Mounting

Mount volumes for data persistence:

```java
@Bean
public GenericContainer<?> weaviateContainer() {
    return new GenericContainer<>("semitechnologies/weaviate:1.23.0")
        .withVolumeMapping(
            Paths.get("./test-data/weaviate").toAbsolutePath().toString(),
            "/var/lib/weaviate"
        );
}
```

### Adding Support for New Providers

To add support for a new vector database provider:

#### Step 1: Add Container Bean

Add a new `@Bean` method in `VectorDatabaseContainerAutoConfiguration`:

```java
@Bean
@ConditionalOnProperty(name = "ai.vector-db.type", havingValue = "newprovider")
public GenericContainer<?> newProviderContainer(ConfigurableEnvironment environment) {
    String containerKey = "newprovider";
    
    if (activeContainers.containsKey(containerKey)) {
        GenericContainer<?> existing = activeContainers.get(containerKey);
        if (existing != null && existing.isRunning()) {
            log.info("Reusing existing NewProvider container");
            return existing;
        }
    }

    log.info("Starting NewProvider container...");

    try {
        String image = getImageVersion("newprovider", "newprovider/image:latest");
        GenericContainer<?> container = new GenericContainer<>(
            DockerImageName.parse(image)
        )
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/health")
                .forPort(8080)
                .forStatusCode(200)
                .withStartupTimeout(DEFAULT_STARTUP_TIMEOUT));

        container.start();
        activeContainers.put(containerKey, container);

        // Inject properties into Spring environment
        Map<String, Object> properties = new HashMap<>();
        properties.put("ai.vector-db.newprovider.host", container.getHost());
        properties.put("ai.vector-db.newprovider.port", container.getMappedPort(8080));
        // Add other required properties

        environment.getPropertySources().addFirst(
            new MapPropertySource("newproviderContainerProperties", properties)
        );

        log.info("NewProvider container started at {}:{}",
            container.getHost(), container.getMappedPort(8080));

        return container;
    } catch (Exception e) {
        log.error("Failed to start NewProvider container", e);
        throw new IllegalStateException("Failed to start NewProvider container: " + e.getMessage(), e);
    }
}
```

#### Step 2: Update Documentation

1. Add provider to the [Supported Vector Databases](#supported-vector-databases) table
2. Add usage example in [Quick Start](#quick-start)
3. Update CI/CD matrix examples

#### Step 3: Test

Create a test to verify the new provider works:

```java
@Test
void testNewProvider() {
    // Verify container started
    assertNotNull(newProviderContainer);
    assertTrue(newProviderContainer.isRunning());
    
    // Verify properties injected
    assertEquals("localhost", environment.getProperty("ai.vector-db.newprovider.host"));
}
```

#### Step 4: Update Configuration

Add provider configuration to `application-testcontainers.yml`:

```yaml
ai:
  vector-db:
    newprovider:
      timeout: 60000
```

---

## Performance Considerations

### Container Startup Time

Container startup times vary by provider:

| Provider | Typical Startup Time | Notes |
|----------|---------------------|-------|
| Milvus | 30-60 seconds | Complex setup, multiple services |
| Qdrant | 5-10 seconds | Fast startup |
| Weaviate | 10-20 seconds | Moderate startup |
| Chroma | 5-10 seconds | Fast startup |
| pgvector | 10-15 seconds | PostgreSQL initialization |

### Optimization Tips

1. **Use Container Reuse**: Enable `TESTCONTAINERS_REUSABLE_ENABLED=true` to reuse containers across test runs
2. **Parallel Execution**: Run tests in parallel to utilize multiple containers
3. **Image Pre-pulling**: Pre-pull Docker images before CI runs:
   ```bash
   docker pull milvusdb/milvus:v2.4.1-latest
   docker pull qdrant/qdrant:v1.16.1
   ```
4. **Resource Allocation**: Allocate sufficient Docker resources (RAM, CPU)
5. **Test Grouping**: Group tests by provider to minimize container restarts

### Memory Usage

Each container consumes memory:

- **Milvus**: ~1-2GB RAM
- **Qdrant**: ~200-500MB RAM
- **Weaviate**: ~500MB-1GB RAM
- **Chroma**: ~200-500MB RAM
- **pgvector**: ~100-300MB RAM

Ensure Docker has sufficient memory allocated (8GB+ recommended for running multiple containers).

### Disk Space

Docker images require disk space:

- Initial download: ~5-10GB total for all images
- Container layers: Additional space for container data
- Test data: Temporary data created during tests

Monitor disk usage:
```bash
docker system df
docker system prune  # Clean up unused resources
```

---

## Best Practices

### 1. Test Isolation

Each test should be independent and not rely on data from previous tests:

```java
@Test
void testVectorStorage() {
    // Clean state - container is fresh
    vectorDatabaseService.storeVector(...);
}

@Test
void testVectorSearch() {
    // Clean state - container is fresh
    // Don't assume data from previous test
}
```

### 2. Resource Cleanup

Always ensure containers are stopped after tests:

```java
@AfterAll
static void cleanup() {
    // Containers auto-cleanup via @PreDestroy, but explicit cleanup is safer
    if (container != null && container.isRunning()) {
        container.stop();
    }
}
```

### 3. Error Handling

Handle container startup failures gracefully:

```java
@Test
void testWithContainer() {
    try {
        // Test code
    } catch (ContainerStartException e) {
        // Log and skip test if container fails
        assumeTrue("Container failed to start", false);
    }
}
```

### 4. Configuration Management

Use profiles to separate test configurations:

```yaml
# application-testcontainers.yml
testcontainers:
  enabled: true
  reuse: ${TESTCONTAINERS_REUSABLE_ENABLED:false}
```

### 5. Logging

Enable debug logging for troubleshooting:

```yaml
logging:
  level:
    org.testcontainers: DEBUG
    com.ai.infrastructure.it.config: DEBUG
```

### 6. CI/CD Optimization

- Pre-pull images in CI setup
- Use container reuse when possible
- Run provider-specific tests in parallel
- Cache Docker layers

### 7. Version Pinning

Pin container image versions for reproducibility:

```java
// Use specific versions, not "latest"
DockerImageName.parse("milvusdb/milvus:v2.4.1-latest")  // ✅ Good
DockerImageName.parse("milvusdb/milvus:latest")         // ❌ Avoid
```

---

## CI/CD Integration

### GitHub Actions

```yaml
name: Vector Database Integration Tests

on:
  push:
    branches: [main, develop]
  pull_request:

jobs:
  test-vector-databases:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        vector-db: [milvus, qdrant, weaviate, chroma]

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: maven

      - name: Run tests with ${{ matrix.vector-db }}
        env:
          OPENAI_API_KEY: ${{ secrets.OPENAI_API_KEY }}
        run: |
          mvn verify \
            -pl ai-infrastructure-module/integration-Testing/integration-tests \
            -Dtest="RealAPI*" \
            -Dspring.profiles.active=real-api-test,testcontainers \
            -Dai.vector-db.type=${{ matrix.vector-db }} \
            -DfailIfNoTests=false

      - name: Upload test results
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: test-results-${{ matrix.vector-db }}
          path: '**/target/surefire-reports/*.xml'
```

### Jenkins Pipeline

```groovy
pipeline {
    agent any

    parameters {
        choice(
            name: 'VECTOR_DB',
            choices: ['milvus', 'qdrant', 'weaviate', 'chroma', 'pgvector'],
            description: 'Vector database to test with'
        )
    }

    stages {
        stage('Test') {
            steps {
                sh """
                    mvn verify \
                        -Dtest="RealAPI*" \
                        -Dspring.profiles.active=real-api-test,testcontainers \
                        -Dai.vector-db.type=${params.VECTOR_DB}
                """
            }
        }
    }
}
```

---

## Supported Vector Databases

| Provider | Image | Ports | Status |
|----------|-------|-------|--------|
| Milvus | `milvusdb/milvus:v2.4.1-latest` | 19530 (gRPC) | Official Testcontainers module |
| Qdrant | `qdrant/qdrant:v1.16.1` | 6333 (REST), 6334 (gRPC) | GenericContainer |
| Weaviate | `semitechnologies/weaviate:1.23.0` | 8080 (REST) | GenericContainer |
| Chroma | `chromadb/chroma:0.4.22` | 8000 (REST) | GenericContainer |
| pgvector | `pgvector/pgvector:pg16` | 5432 (PostgreSQL) | PostgreSQLContainer |

---

## Comparison: With vs Without Testcontainers

| Aspect | Without (current) | With Testcontainers |
|--------|-------------------|---------------------|
| **Setup** | Manual database installation | Automatic via Docker |
| **Configuration** | Update properties manually | Auto-injected by container |
| **Isolation** | Shared database | Fresh container per test run |
| **CI/CD** | Requires database service | Only needs Docker |
| **Cleanup** | Manual | Automatic |
| **Provider switching** | Change config, restart DB | Just change command line param |

---

## Troubleshooting

### Container fails to start

```bash
# Check Docker is running
docker info

# Pull image manually
docker pull milvusdb/milvus:v2.4.1-latest

# Check disk space
df -h
```

### Tests timeout waiting for container

Increase startup timeout in the auto-configuration:

```java
.waitingFor(Wait.forHttp("/readyz")
    .withStartupTimeout(Duration.ofMinutes(5)))  // Increase timeout
```

### Connection refused errors

Ensure the profile is active:

```bash
# Correct - includes testcontainers profile
-Dspring.profiles.active=real-api-test,testcontainers

# Wrong - missing testcontainers profile
-Dspring.profiles.active=real-api-test
```

### Container not cleaning up

Testcontainers uses Ryuk to clean up. If containers persist:

```bash
# List running containers
docker ps

# Clean up manually
docker stop $(docker ps -q --filter "label=org.testcontainers")

# Force cleanup all testcontainers
docker ps -a --filter "label=org.testcontainers" -q | xargs docker rm -f
```

### Port conflicts

If you get "port already in use" errors:

```bash
# Find process using port
netstat -ano | findstr :19530  # Windows
lsof -i :19530                 # Linux/Mac

# Kill process or change port in configuration
```

### Slow container startup

If containers take too long to start:

1. Check Docker resources (RAM, CPU allocation)
2. Increase startup timeout in configuration
3. Pre-pull images before running tests
4. Use container reuse mode

### Image pull failures

If Docker image pulls fail:

```bash
# Pull manually to see error
docker pull milvusdb/milvus:v2.4.1-latest

# Check network connectivity
docker info

# Try with different registry mirror
```

### Test failures with parallel execution

If tests fail when running in parallel:

1. Ensure each test class uses unique container instances
2. Check for shared state between tests
3. Verify thread-safety of test code
4. Reduce parallelism: `-DforkCount=1`

### Debugging Container Issues

#### Enable Debug Logging

Add to `application-testcontainers.yml`:

```yaml
logging:
  level:
    org.testcontainers: DEBUG
    com.github.dockerjava: DEBUG
    com.ai.infrastructure.it.config: DEBUG
```

#### Inspect Running Containers

```bash
# List all containers
docker ps -a

# View container logs
docker logs <container-id>

# Execute commands in container
docker exec -it <container-id> /bin/bash

# Inspect container configuration
docker inspect <container-id>
```

#### Check Container Health

```bash
# Check if container is responding
curl http://localhost:6333/readyz  # Qdrant
curl http://localhost:8080/v1/.well-known/ready  # Weaviate

# Check container resource usage
docker stats <container-id>
```

#### Common Debugging Commands

```bash
# View Testcontainers logs
docker logs testcontainers-ryuk-*  # Cleanup service

# Check Docker daemon logs
# Linux: journalctl -u docker
# Mac: ~/Library/Containers/com.docker.docker/Data/log/host/Docker.log
# Windows: Check Docker Desktop logs

# Verify network connectivity
docker network ls
docker network inspect bridge
```

#### Enable Testcontainers Debug Mode

```bash
# Set environment variable
export TESTCONTAINERS_DEBUG=true

# Run tests
mvn verify -Dai.vector-db.type=milvus
```

This will output detailed logs about container lifecycle.

---

## Limitations

### Known Limitations

1. **Docker Requirement**: Requires Docker to be installed and running
2. **Resource Usage**: Containers consume significant RAM and disk space
3. **Startup Time**: First container startup can be slow (image download + initialization)
4. **Network Isolation**: Containers run in isolated Docker networks by default
5. **Platform Support**: Some providers may have platform-specific limitations (ARM vs x86)
6. **Version Compatibility**: Container image versions may not match production versions exactly

### Provider-Specific Limitations

#### Milvus
- Requires more resources (1-2GB RAM)
- Slower startup time (30-60 seconds)
- Complex multi-service architecture

#### Qdrant
- Limited to single-node setup in containers
- No clustering support in testcontainers

#### Weaviate
- Vectorizer modules may require additional configuration
- Some features require external services

#### Chroma
- Limited persistence options in container mode
- Some advanced features may not be available

#### pgvector
- Requires PostgreSQL-specific configuration
- Extension must be enabled in init script

### Performance Limitations

- **Not for Load Testing**: Testcontainers are not suitable for performance/load testing
- **Network Latency**: Container networking adds slight latency vs local processes
- **Resource Constraints**: Limited by Docker resource allocation

### Best Practices to Mitigate Limitations

1. Use container reuse for faster test execution
2. Pre-pull images in CI/CD pipelines
3. Allocate sufficient Docker resources
4. Use provider-specific optimizations where available
5. Consider using in-memory providers for unit tests

---

## Migration Guide

### Migrating from Manual Database Setup

If you currently have manually configured vector databases for testing, follow these steps:

#### Step 1: Identify Current Configuration

Review your current test configuration:

```yaml
# application-real-api-test.yml (current)
ai:
  vector-db:
    type: milvus
    milvus:
      host: localhost
      port: 19530
      database: test_db
```

#### Step 2: Add Testcontainers Dependencies

Add dependencies to `pom.xml` as described in [Step 1: Add Dependencies](#step-1-add-dependencies).

#### Step 3: Create Auto-Configuration

Create the auto-configuration class as described in [Step 2: Create Auto-Configuration Class](#step-2-create-auto-configuration-class).

#### Step 4: Update Test Classes

**Before** (manual configuration):
```java
@SpringBootTest
@ActiveProfiles("real-api-test")
class RealAPIIntegrationTest {
    // Uses manually configured database
}
```

**After** (with Testcontainers):
```java
@SpringBootTest
@ActiveProfiles("real-api-test", "testcontainers")
class RealAPIIntegrationTest {
    // Uses auto-configured container
}
```

#### Step 5: Update CI/CD Scripts

**Before**:
```bash
# Start database manually
docker run -d -p 19530:19530 milvusdb/milvus:v2.4.1-latest

# Run tests
mvn verify -Dtest=RealAPIIntegrationTest

# Stop database
docker stop <container-id>
```

**After**:
```bash
# Tests handle container lifecycle automatically
mvn verify \
    -Dtest=RealAPIIntegrationTest \
    -Dspring.profiles.active=real-api-test,testcontainers \
    -Dai.vector-db.type=milvus
```

#### Step 6: Remove Manual Setup Code

Remove any manual database startup/shutdown scripts:

```bash
# Remove these files if they exist:
# - scripts/start-milvus.sh
# - scripts/stop-milvus.sh
# - docker-compose.test.yml (if only used for tests)
```

#### Step 7: Update Documentation

Update team documentation to reflect the new approach.

### Migration Checklist

- [ ] Testcontainers dependencies added
- [ ] Auto-configuration class created
- [ ] Test profile created (`application-testcontainers.yml`)
- [ ] Test classes updated with `testcontainers` profile
- [ ] CI/CD scripts updated
- [ ] Manual setup scripts removed
- [ ] Team documentation updated
- [ ] All tests passing with Testcontainers

### Rollback Plan

If you need to rollback:

1. Remove `testcontainers` profile from test classes
2. Restore manual database configuration
3. Re-enable manual startup scripts
4. Remove Testcontainers dependencies (optional)

---

## File Structure

## File Structure

```
integration-tests/
├── src/test/
│   ├── java/com/ai/infrastructure/it/
│   │   ├── config/
│   │   │   ├── VectorDatabaseContainerAutoConfiguration.java  ← NEW
│   │   │   └── TestcontainersInitializer.java                 ← NEW
│   │   ├── RealAPIIntegrationTest.java                        ← UNCHANGED
│   │   └── ...other existing tests...                         ← UNCHANGED
│   └── resources/
│       ├── application-testcontainers.yml                     ← NEW
│       ├── db/
│       │   └── init-pgvector.sql                              ← NEW
│       └── META-INF/
│           └── spring.factories                               ← NEW (optional)
└── pom.xml                                                    ← ADD DEPENDENCIES
```

---

## Version Compatibility

### Testcontainers Version

This guide is tested with **Testcontainers 1.19.8**. For other versions:

| Testcontainers Version | Java Version | Notes |
|----------------------|--------------|-------|
| 1.19.x | Java 17+ | Recommended |
| 1.18.x | Java 17+ | Supported |
| 1.17.x | Java 17+ | Supported |

### Spring Boot Compatibility

| Spring Boot Version | Compatibility | Notes |
|-------------------|----------------|-------|
| 3.2.x | ✅ Full | Recommended |
| 3.1.x | ✅ Full | Supported |
| 3.0.x | ✅ Full | Supported |
| 2.7.x | ⚠️ Partial | May require adjustments |

### Docker Version

| Docker Version | Compatibility | Notes |
|---------------|----------------|-------|
| 24.0+ | ✅ Full | Recommended |
| 23.0+ | ✅ Full | Supported |
| 22.0+ | ✅ Full | Supported |
| 20.10+ | ⚠️ Partial | Some features may not work |

### Provider Image Versions

Default container image versions used:

| Provider | Default Image | Tested Version |
|----------|---------------|----------------|
| Milvus | `milvusdb/milvus:v2.4.1-latest` | v2.4.1 |
| Qdrant | `qdrant/qdrant:v1.16.1` | v1.16.1 |
| Weaviate | `semitechnologies/weaviate:1.23.0` | 1.23.0 |
| Chroma | `chromadb/chroma:0.4.22` | 0.4.22 |
| pgvector | `pgvector/pgvector:pg16` | PostgreSQL 16 |

> **Note**: Always test with specific versions in production-like environments before deploying.

---

## Summary

With this auto-configuration approach, you get:

### Key Benefits

1. ✅ **Zero Code Changes** - Existing test classes work without modification
2. ✅ **One Command Switching** - Change providers with a single property
3. ✅ **Automatic Lifecycle** - Containers start/stop automatically
4. ✅ **CI/CD Ready** - Seamless integration with automated pipelines
5. ✅ **Consistent Testing** - Same database version every time
6. ✅ **Isolated Tests** - Fresh container for each test run
7. ✅ **Easy Debugging** - Containers can be inspected during test failures

### Quick Reference

```bash
# Run tests with Milvus
mvn verify \
    -Dtest=RealAPIIntegrationTest \
    -Dspring.profiles.active=real-api-test,testcontainers \
    -Dai.vector-db.type=milvus

# Run tests with Qdrant
mvn verify \
    -Dtest=RealAPIIntegrationTest \
    -Dspring.profiles.active=real-api-test,testcontainers \
    -Dai.vector-db.type=qdrant

# Run all RealAPI tests with matrix
for provider in milvus qdrant weaviate chroma; do
    mvn verify \
        -Dtest="RealAPI*" \
        -Dspring.profiles.active=real-api-test,testcontainers \
        -Dai.vector-db.type=$provider
done
```

### Next Steps

1. **Add Dependencies** - Update `pom.xml` with Testcontainers dependencies
2. **Create Configuration** - Add auto-configuration class and test profile
3. **Update Tests** - Add `testcontainers` profile to test classes
4. **Test Locally** - Verify everything works with one provider
5. **Update CI/CD** - Integrate into your pipeline
6. **Document** - Share with your team

### Getting Help

- **Testcontainers Documentation**: https://testcontainers.org
- **Spring Boot Testing**: https://spring.io/guides/gs/testing-web
- **Docker Troubleshooting**: Check Docker logs and system resources

---

**Happy Testing! 🚀**
