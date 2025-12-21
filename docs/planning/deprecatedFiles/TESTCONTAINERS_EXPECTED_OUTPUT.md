# Expected Test Output with Testcontainers

## What to Expect When Running Tests

### Initial Container Startup (First Time)

```
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------

  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::                (v3.2.0)

[INFO] o.testcontainers.images.PullPolicy - Image pull policy will be performed by: DefaultPullPolicy()
[INFO] o.t.utility.ImageNameSubstitutor - Image name substitution will be performed by: DefaultImageNameSubstitutor
[INFO] o.t.d.DockerClientProviderStrategy - Loaded org.testcontainers.dockerclient.UnixSocketClientProviderStrategy
[INFO] o.t.d.DockerClientProviderStrategy - Found Docker environment with local Unix socket
[INFO] org.testcontainers.DockerClientFactory - Docker host IP address is localhost
[INFO] org.testcontainers.DockerClientFactory - Connected to docker: 
  Server Version: 24.0.7
  API Version: 1.43
  Operating System: Ubuntu 24.04.3 LTS
  Total Memory: 7944 MB

[INFO] tc.postgres:15-alpine - Creating container for image: postgres:15-alpine
[INFO] tc.postgres:15-alpine - Container postgres:15-alpine is starting: 94a5e6f2c3b1
[INFO] tc.postgres:15-alpine - Container postgres:15-alpine started in PT5.234S
[INFO] tc.postgres:15-alpine - Container is ready: 
  - JDBC URL: jdbc:postgresql://localhost:32768/behavior_it
  - Username: behavior
  - Database: behavior_it

[INFO] o.s.b.t.c.SpringBootTestContextBootstrapper - Using TestExecutionListeners: 
[INFO] liquibase.database - Set default schema name to public
[INFO] liquibase.lockservice - Successfully acquired change log lock
[INFO] liquibase.changelog - Creating database history table with name: public.databasechangelog
[INFO] liquibase.changelog - Reading from public.databasechangelog
[INFO] liquibase.changelog - classpath:/db/changelog/db.changelog-master.yaml: 
  Running Changeset: db/changelog/001-create-behavior-signals-table.yaml::1::ai-infrastructure
[INFO] liquibase.changelog - Table behavior_signals created
[INFO] liquibase.changelog - 
  Running Changeset: db/changelog/002-create-behavior-metrics-table.yaml::1::ai-infrastructure
[INFO] liquibase.changelog - Table behavior_signal_metrics created
[INFO] liquibase.changelog - 
  Running Changeset: db/changelog/003-create-behavior-insights-table.yaml::1::ai-infrastructure
[INFO] liquibase.changelog - Table behavior_insights created
[INFO] liquibase.lockservice - Successfully released change log lock

[INFO] Starting PatternAnalyzerInsightsIntegrationTest using Java 21.0.8
[INFO] No active profile set, falling back to 1 default profile: "default"
[INFO] Started PatternAnalyzerInsightsIntegrationTest in 3.245 seconds
```

### Test Execution

```
[INFO] Running com.ai.infrastructure.it.BehaviouralTests.PatternAnalyzerInsightsIntegrationTest

[INFO] c.a.b.ingestion.BehaviorIngestionService - Ingesting behavior signal: 
  schema=engagement.view, user=a1b2c3d4-e5f6-7890-abcd-ef1234567890
[INFO] c.a.b.ingestion.BehaviorIngestionService - Successfully ingested signal: id=f1e2d3c4...
[INFO] c.a.b.ingestion.BehaviorIngestionService - Ingesting behavior signal: 
  schema=engagement.add_to_cart, user=a1b2c3d4-e5f6-7890-abcd-ef1234567890
[INFO] c.a.b.service.BehaviorAnalysisService - Analyzing behavior for user: a1b2c3d4...
[INFO] c.a.b.processing.analyzer.PatternAnalyzer - Analyzing 9 signals for user a1b2c3d4...
[INFO] c.a.b.processing.analyzer.PatternAnalyzer - Identified patterns: 
  [high_engagement, luxury_preference, cart_abandonment_risk]
[INFO] c.a.b.processing.analyzer.PatternAnalyzer - User segment: needs_nurturing

[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.456 s 
  - in PatternAnalyzerInsightsIntegrationTest
```

### Container Cleanup

```
[INFO] tc.postgres:15-alpine - Stopping container: 94a5e6f2c3b1
[INFO] tc.postgres:15-alpine - Container stopped: postgres:15-alpine
[INFO] tc.postgres:15-alpine - Removing container: 94a5e6f2c3b1
[INFO] tc.postgres:15-alpine - Container removed: postgres:15-alpine

[INFO] Results:
[INFO] 
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  12.345 s
[INFO] Finished at: 2025-11-19T17:30:00Z
[INFO] ------------------------------------------------------------------------
```

---

## Expected Timing

### Single Test Class (3 tests)
```
Container Startup:   ~5-10 seconds
Liquibase Migration: ~2-3 seconds
Test Execution:      ~8-15 seconds
Container Cleanup:   ~1 second
─────────────────────────────────────
Total:              ~16-29 seconds
```

### Full Behavioral Suite (10 test classes, ~25 tests)
```
Container Startup:   ~5-10 seconds (shared container)
Liquibase Migration: ~2-3 seconds (once)
Test Execution:      ~60-120 seconds (all tests)
Container Cleanup:   ~1 second
─────────────────────────────────────
Total:              ~68-136 seconds (~1-2 minutes)
```

---

## Success Indicators

### ✅ Successful Test Run Signs

1. **Container Starts Successfully**
   ```
   [INFO] tc.postgres:15-alpine - Container postgres:15-alpine started in PT5.234S
   ```

2. **Liquibase Applies Schema**
   ```
   [INFO] liquibase.changelog - Table behavior_signals created
   [INFO] liquibase.changelog - Table behavior_signal_metrics created
   [INFO] liquibase.changelog - Table behavior_insights created
   ```

3. **Tests Pass**
   ```
   [INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
   ```

4. **Container Cleans Up**
   ```
   [INFO] tc.postgres:15-alpine - Container removed: postgres:15-alpine
   ```

5. **Build Succeeds**
   ```
   [INFO] BUILD SUCCESS
   ```

---

## Test Output Breakdown by Test Class

### 1. PatternAnalyzerInsightsIntegrationTest (3 tests)
```
✅ analyzerBuildsSegmentedInsights
   - Ingests 9 signals (6 views, 2 carts, 1 purchase)
   - Generates insights with patterns, segment, recommendations
   - Validates preference extraction
   - Duration: ~3-5 seconds

✅ analyzerHandlesEmptySignals
   - Tests edge case with no signals
   - Returns empty insights gracefully
   - Duration: ~0.5-1 second

✅ analyzerDetectsHighValuePatterns
   - Ingests 5 high-value purchases
   - Validates engagement score calculation
   - Duration: ~2-3 seconds
```

### 2. DatabaseSinkApiRoundtripIntegrationTest (1 test)
```
✅ databaseSinkPersistsAndQueryReturnsEvents
   - Ingests 2 signals via REST API
   - Queries via REST API
   - Validates persistence and ordering
   - Duration: ~2-4 seconds
```

### 3. KafkaEventSinkIntegrationTest (1 test)
```
✅ kafkaSinkPublishesEventsAndMonitoringReflectsActivity
   - Starts embedded Kafka broker
   - Ingests 5 signals
   - Verifies Kafka messages published
   - Checks monitoring metrics
   - Duration: ~4-6 seconds
```

### ... (continues for all 10 test classes)

---

## Verification Commands

### Check Container is Running During Tests
```bash
# In another terminal while tests run
docker ps

# Expected output:
CONTAINER ID   IMAGE                  STATUS        PORTS
94a5e6f2c3b1   postgres:15-alpine    Up 5 seconds  0.0.0.0:32768->5432/tcp
```

### View Container Logs
```bash
# Get container ID during test execution
docker ps

# View logs
docker logs <container-id>

# Expected: PostgreSQL startup logs
```

### Check Images Downloaded
```bash
docker images | grep postgres

# Expected output:
postgres    15-alpine    sha256:abc123...    50MB
```

---

## Common Success Patterns in Logs

### Pattern 1: Container Lifecycle
```
Creating container → Starting → Ready → Stopping → Removing
```

### Pattern 2: Database Schema
```
Acquiring lock → Creating tables → Releasing lock
```

### Pattern 3: Test Execution
```
Starting test → Ingesting signals → Analyzing → Asserting → Cleaning up
```

---

## What Success Looks Like

### Terminal Output Summary
```bash
$ mvn test -Dtest=PatternAnalyzerInsightsIntegrationTest -pl integration-tests

[INFO] Scanning for projects...
[INFO] Building AI Infrastructure Integration Tests 1.0.0
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running PatternAnalyzerInsightsIntegrationTest
[INFO] tc.postgres:15-alpine - Container started ✓
[INFO] liquibase.changelog - Schema applied ✓
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 ✓
[INFO] BUILD SUCCESS ✓
[INFO] Total time: 18.234 s
```

### Test Report File
**Location:** `integration-tests/target/surefire-reports/PatternAnalyzerInsightsIntegrationTest.txt`

```
-------------------------------------------------------------------------------
Test set: PatternAnalyzerInsightsIntegrationTest
-------------------------------------------------------------------------------
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 8.456 s

✓ analyzerBuildsSegmentedInsights           Time elapsed: 3.234 s
✓ analyzerHandlesEmptySignals              Time elapsed: 0.891 s
✓ analyzerDetectsHighValuePatterns         Time elapsed: 2.567 s
```

---

## 🎯 Next Steps After Successful Run

1. **Check test reports:**
   ```bash
   cat ai-infrastructure-module/integration-Testing/integration-tests/target/surefire-reports/*.txt
   ```

2. **Run full behavioral suite:**
   ```bash
   mvn test -Dtest=RealAPIProviderBehaviourMatrixIntegrationTest -pl integration-tests
   ```

3. **View detailed XML reports:**
   ```bash
   cat ai-infrastructure-module/integration-Testing/integration-tests/target/surefire-reports/*.xml
   ```

4. **Check for any warnings:**
   ```bash
   grep "WARN" ai-infrastructure-module/integration-Testing/integration-tests/target/surefire-reports/*.txt
   ```

---

## 🎉 Congratulations!

If you see `BUILD SUCCESS` and `Tests run: X, Failures: 0, Errors: 0`, your Testcontainers setup is working perfectly!

All behavioral integration tests are now running against a real PostgreSQL 15 database with proper JSONB support, exactly as they would in production.
