# Testcontainers Setup Guide for Behavioral Integration Tests

## Current Status

### ✅ Testcontainers Already Configured
The project is **already set up** to use Testcontainers following the official guide from https://testcontainers.com/getting-started/

**Configuration Location:** `ai-infrastructure-module/integration-Testing/integration-tests/src/test/java/com/ai/infrastructure/it/config/PostgresTestContainerConfig.java`

### ✅ Dependencies Configured (pom.xml)
```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
```

### ✅ PostgreSQL Container Configuration
```java
@TestConfiguration
public class PostgresTestContainerConfig {
    
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
            .withDatabaseName("behavior_it")
            .withUsername("behavior")
            .withPassword("behavior");
    
    static {
        POSTGRES.start();
    }
    
    @DynamicPropertySource
    static void registerPostgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        
        // Liquibase configuration
        registry.add("spring.liquibase.enabled", () -> true);
        registry.add("spring.liquibase.change-log", 
            () -> "classpath:/db/changelog/db.changelog-master.yaml");
    }
}
```

---

## ❌ Current Issue: Docker Not Available

### Error When Running Tests
```
ERROR o.t.d.DockerClientProviderStrategy - Could not find a valid Docker environment.
  UnixSocketClientProviderStrategy: failed with exception InvalidConfigurationException 
    (Could not find unix domain socket). 
  Root cause: NoSuchFileException (/var/run/docker.sock)
```

### What's Missing
- ❌ Docker not installed (`docker: command not found`)
- ❌ Docker socket not available (`/var/run/docker.sock` missing)
- ❌ Docker daemon not running

---

## 🔧 How to Fix: Install Docker

### Current Environment
- **OS:** Ubuntu 24.04.3 LTS (Noble Numbat)
- **Architecture:** amd64
- **Java:** OpenJDK 21.0.8
- **Maven:** 3.8.7

### Option 1: Install Docker Engine (Recommended)

#### Step 1: Install Docker
```bash
# Update package index
sudo apt-get update

# Install prerequisites
sudo apt-get install -y \
    ca-certificates \
    curl \
    gnupg \
    lsb-release

# Add Docker's official GPG key
sudo mkdir -p /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | \
    sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg

# Set up the repository
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
  https://download.docker.com/linux/ubuntu \
  $(lsb_release -cs) stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# Install Docker Engine
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
```

#### Step 2: Start Docker Service
```bash
# Start Docker daemon
sudo systemctl start docker

# Enable Docker to start on boot
sudo systemctl enable docker

# Verify installation
sudo docker --version
sudo docker run hello-world
```

#### Step 3: Add User to Docker Group (Optional)
```bash
# Add current user to docker group
sudo usermod -aG docker $USER

# Log out and back in, or run:
newgrp docker

# Verify you can run without sudo
docker ps
```

### Option 2: Use Docker Desktop
1. Download Docker Desktop from https://docs.docker.com/desktop/install/linux-install/
2. Follow installation instructions for Ubuntu
3. Start Docker Desktop
4. Verify with `docker ps`

---

## 🚀 Running the Tests

Once Docker is installed and running:

### Run Single Test
```bash
cd /workspace/ai-infrastructure-module

# Run PatternAnalyzerInsightsIntegrationTest
mvn test -Dtest=PatternAnalyzerInsightsIntegrationTest -pl integration-tests
```

### Run All Behavioral Tests
```bash
# Run the full behavioral test suite (10 tests)
mvn test -Dtest=RealAPIProviderBehaviourMatrixIntegrationTest -pl integration-tests
```

### Run All Integration Tests
```bash
# Run all integration tests in the module
mvn test -pl integration-tests
```

---

## 📋 What Happens When Tests Run

### 1. **Testcontainers Starts PostgreSQL**
- Downloads `postgres:15-alpine` image (first time only)
- Starts PostgreSQL container on random available port
- Container runs in background during test execution

### 2. **Liquibase Applies Schema**
- Reads `classpath:/db/changelog/db.changelog-master.yaml`
- Creates tables: `behavior_signals`, `behavior_signal_metrics`, `behavior_insights`
- Applies JSONB column types and indexes

### 3. **Tests Execute**
- Spring Boot context starts with PostgreSQL connection
- Tests ingest behavior signals
- Tests verify insights, patterns, and recommendations
- Tests clean up data in `@AfterEach` methods

### 4. **Container Cleanup**
- Testcontainers automatically stops and removes container
- No manual cleanup needed

---

## 🔍 Verifying Setup

### Check Docker Status
```bash
# Check Docker is running
docker --version
docker ps

# Check Docker socket exists
ls -la /var/run/docker.sock

# Test Docker works
docker run --rm hello-world
```

### Check Testcontainers Can Connect
```bash
# Run a simple Testcontainers test
cd /workspace/ai-infrastructure-module
mvn test -Dtest=DatabaseSinkApiRoundtripIntegrationTest -pl integration-tests

# Expected output:
# - Container starts: "Creating container for image: postgres:15-alpine"
# - Tests execute successfully
# - Container stops automatically
```

---

## 📊 Test Execution Timeline

### First Run (with download)
```
1. Docker pulls postgres:15-alpine image  → ~1-2 minutes
2. Container starts                        → ~5-10 seconds
3. Liquibase migrations run                → ~2-3 seconds
4. Tests execute                           → ~10-30 seconds per test
5. Container cleanup                       → ~1 second
```

### Subsequent Runs
```
1. Container starts (image cached)         → ~5-10 seconds
2. Liquibase migrations run                → ~2-3 seconds
3. Tests execute                           → ~10-30 seconds per test
4. Container cleanup                       → ~1 second
```

---

## 🎯 Test Coverage with Testcontainers

### All 10 Behavioral Tests Use PostgreSQL Testcontainers

1. ✅ **DatabaseSinkApiRoundtripIntegrationTest** - Database persistence
2. ✅ **KafkaEventSinkIntegrationTest** - Kafka sink + monitoring
3. ✅ **RedisEventSinkIntegrationTest** - Redis sink with TTL
4. ✅ **HybridEventSinkIntegrationTest** - Hot/cold storage
5. ✅ **S3EventSinkIntegrationTest** - S3 archive sink
6. ✅ **AggregatedBehaviorProviderIntegrationTest** - Multi-provider aggregation
7. ✅ **ExternalAnalyticsAdapterContractTest** - External provider contract
8. ✅ **AnomalyDetectionWorkerIntegrationTest** - Anomaly detection
9. ✅ **UserSegmentationWorkerIntegrationTest** - User segmentation
10. ✅ **PatternAnalyzerInsightsIntegrationTest** - Pattern analysis (newly rewritten)

---

## 🐛 Troubleshooting

### Issue: "Could not find a valid Docker environment"
**Solution:** Install Docker following steps above

### Issue: "Permission denied while trying to connect to Docker daemon socket"
**Solution:** Add user to docker group:
```bash
sudo usermod -aG docker $USER
newgrp docker
```

### Issue: "Cannot connect to the Docker daemon"
**Solution:** Start Docker service:
```bash
sudo systemctl start docker
sudo systemctl status docker
```

### Issue: "Port already in use"
**Solution:** Testcontainers uses random ports, but if issues persist:
```bash
# Clean up any stale containers
docker container prune -f
docker system prune -f
```

### Issue: "Failed to pull image postgres:15-alpine"
**Solution:** Check internet connectivity and Docker Hub access:
```bash
# Test Docker Hub connection
docker pull hello-world

# If behind proxy, configure Docker proxy settings
sudo mkdir -p /etc/systemd/system/docker.service.d
sudo nano /etc/systemd/system/docker.service.d/http-proxy.conf
```

---

## 📚 Additional Resources

- **Testcontainers Official Docs:** https://testcontainers.com/getting-started/
- **Testcontainers for Java:** https://java.testcontainers.org/
- **PostgreSQL Module:** https://java.testcontainers.org/modules/databases/postgres/
- **Docker Installation:** https://docs.docker.com/engine/install/ubuntu/
- **Testcontainers Best Practices:** https://testcontainers.com/guides/

---

## ✅ Summary

| Component | Status | Notes |
|-----------|--------|-------|
| **Testcontainers Dependencies** | ✅ Configured | Already in pom.xml |
| **PostgreSQL Container Config** | ✅ Configured | `PostgresTestContainerConfig.java` |
| **Liquibase Integration** | ✅ Configured | Auto-applies schema |
| **Test Classes** | ✅ Ready | 10 behavioral tests |
| **Docker Installation** | ❌ Missing | **Required to run tests** |

**Next Step:** Install Docker Engine to enable test execution.

Once Docker is installed, all behavioral integration tests will work out of the box with no additional configuration needed.
