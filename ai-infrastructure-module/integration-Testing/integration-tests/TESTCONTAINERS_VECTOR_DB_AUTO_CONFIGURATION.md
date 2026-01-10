# Testcontainers Vector Database Auto-Configuration Guide

## Overview

This guide explains how to run your **existing RealAPI integration tests** with different vector database providers using Testcontainers. No changes to your existing test classes are required.

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

/**
 * Auto-configuration for vector database Testcontainers.
 *
 * Automatically starts the appropriate container based on the
 * ai.vector-db.type property and injects connection properties.
 *
 * Usage:
 *   mvn verify -Dai.vector-db.type=milvus -Dspring.profiles.active=testcontainers
 */
@TestConfiguration
@ConditionalOnProperty(name = "testcontainers.enabled", havingValue = "true", matchIfMissing = false)
public class VectorDatabaseContainerAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(VectorDatabaseContainerAutoConfiguration.class);

    // Container references for cleanup
    private static GenericContainer<?> activeContainer;

    // ==================== MILVUS ====================

    @Bean
    @ConditionalOnProperty(name = "ai.vector-db.type", havingValue = "milvus")
    public MilvusContainer milvusContainer(ConfigurableEnvironment environment) {
        log.info("Starting Milvus container...");

        MilvusContainer container = new MilvusContainer(
            DockerImageName.parse("milvusdb/milvus:v2.4.1-latest")
        );

        container.start();
        activeContainer = container;

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

        log.info("Milvus container started at {}:{}",
            container.getHost(), container.getMappedPort(19530));

        return container;
    }

    // ==================== QDRANT ====================

    @Bean
    @ConditionalOnProperty(name = "ai.vector-db.type", havingValue = "qdrant")
    public GenericContainer<?> qdrantContainer(ConfigurableEnvironment environment) {
        log.info("Starting Qdrant container...");

        GenericContainer<?> container = new GenericContainer<>(
            DockerImageName.parse("qdrant/qdrant:v1.7.4")
        )
            .withExposedPorts(6333, 6334)
            .waitingFor(Wait.forHttp("/readyz")
                .forPort(6333)
                .forStatusCode(200)
                .withStartupTimeout(Duration.ofMinutes(2)));

        container.start();
        activeContainer = container;

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

        log.info("Qdrant container started at {}:{}",
            container.getHost(), container.getMappedPort(6333));

        return container;
    }

    // ==================== WEAVIATE ====================

    @Bean
    @ConditionalOnProperty(name = "ai.vector-db.type", havingValue = "weaviate")
    public GenericContainer<?> weaviateContainer(ConfigurableEnvironment environment) {
        log.info("Starting Weaviate container...");

        GenericContainer<?> container = new GenericContainer<>(
            DockerImageName.parse("semitechnologies/weaviate:1.23.0")
        )
            .withExposedPorts(8080)
            .withEnv("AUTHENTICATION_ANONYMOUS_ACCESS_ENABLED", "true")
            .withEnv("PERSISTENCE_DATA_PATH", "/var/lib/weaviate")
            .withEnv("DEFAULT_VECTORIZER_MODULE", "none")
            .withEnv("CLUSTER_HOSTNAME", "node1")
            .waitingFor(Wait.forHttp("/v1/.well-known/ready")
                .forPort(8080)
                .forStatusCode(200)
                .withStartupTimeout(Duration.ofMinutes(2)));

        container.start();
        activeContainer = container;

        // Inject properties into Spring environment
        Map<String, Object> properties = new HashMap<>();
        properties.put("ai.vector-db.weaviate.scheme", "http");
        properties.put("ai.vector-db.weaviate.host", container.getHost());
        properties.put("ai.vector-db.weaviate.port", container.getMappedPort(8080));
        properties.put("ai.vector-db.weaviate.api-key", "");

        environment.getPropertySources().addFirst(
            new MapPropertySource("weaviateContainerProperties", properties)
        );

        log.info("Weaviate container started at {}:{}",
            container.getHost(), container.getMappedPort(8080));

        return container;
    }

    // ==================== CHROMA ====================

    @Bean
    @ConditionalOnProperty(name = "ai.vector-db.type", havingValue = "chroma")
    public GenericContainer<?> chromaContainer(ConfigurableEnvironment environment) {
        log.info("Starting Chroma container...");

        GenericContainer<?> container = new GenericContainer<>(
            DockerImageName.parse("chromadb/chroma:0.4.22")
        )
            .withExposedPorts(8000)
            .withEnv("IS_PERSISTENT", "TRUE")
            .withEnv("ANONYMIZED_TELEMETRY", "FALSE")
            .waitingFor(Wait.forHttp("/api/v1/heartbeat")
                .forPort(8000)
                .forStatusCode(200)
                .withStartupTimeout(Duration.ofMinutes(2)));

        container.start();
        activeContainer = container;

        // Inject properties into Spring environment
        Map<String, Object> properties = new HashMap<>();
        properties.put("ai.vector-db.chroma.host", container.getHost());
        properties.put("ai.vector-db.chroma.port", container.getMappedPort(8000));

        environment.getPropertySources().addFirst(
            new MapPropertySource("chromaContainerProperties", properties)
        );

        log.info("Chroma container started at {}:{}",
            container.getHost(), container.getMappedPort(8000));

        return container;
    }

    // ==================== PGVECTOR ====================

    @Bean
    @ConditionalOnProperty(name = "ai.vector-db.type", havingValue = "pgvector")
    public PostgreSQLContainer<?> pgvectorContainer(ConfigurableEnvironment environment) {
        log.info("Starting PostgreSQL with pgvector container...");

        PostgreSQLContainer<?> container = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16")
                .asCompatibleSubstituteFor("postgres")
        )
            .withDatabaseName("vectordb")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("db/init-pgvector.sql");

        container.start();
        activeContainer = container;

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

        log.info("pgvector container started at {}:{}",
            container.getHost(), container.getMappedPort(5432));

        return container;
    }

    // ==================== CLEANUP ====================

    @PreDestroy
    public void cleanup() {
        if (activeContainer != null && activeContainer.isRunning()) {
            log.info("Stopping vector database container...");
            activeContainer.stop();
        }
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
| Qdrant | `qdrant/qdrant:v1.7.4` | 6333 (REST), 6334 (gRPC) | GenericContainer |
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
```

---

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

## Summary

With this auto-configuration approach:

1. **Zero changes** to existing test classes
2. **One command** to switch vector database providers
3. **Automatic** container lifecycle management
4. **Easy CI/CD** integration with matrix testing
5. **Consistent** testing across all providers

```bash
# That's it! Run your existing tests with any provider:
mvn verify -Dai.vector-db.type=milvus -Dspring.profiles.active=testcontainers
```
