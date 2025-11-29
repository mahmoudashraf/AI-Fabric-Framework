# Relationship Query Integration Tests - RealAPI Separation

## Overview

Similar to the **ai-infrastructure-module/integration-tests**, the **relationship-query-integration-tests** module now separates:

- ✅ **Non-RealAPI Tests** - Run during standard `mvn verify` (no API keys needed)
- 🔑 **RealAPI Tests** - Run separately with OpenAI API key via manual trigger

---

## Test Structure

### Directory Layout

```
relationship-query-integration-tests/
├── src/test/java/com/ai/infrastructure/relationship/it/
│   ├── RelationshipQueryBasicIntegrationTest.java        (✅ Non-RealAPI)
│   ├── config/
│   │   └── BackendEnvTestConfiguration.java
│   └── realapi/
│       ├── LawFirmRealApiIntegrationTest.java            (🔑 RealAPI)
│       ├── FinancialFraudRealApiIntegrationTest.java     (🔑 RealAPI)
│       └── ECommerceRealApiIntegrationTest.java          (🔑 RealAPI)
└── run-relationship-query-realapi-tests.sh
```

---

## Test Categories

### ✅ Non-RealAPI Tests (Run in `mvn verify`)

**Class:** `RelationshipQueryBasicIntegrationTest.java`
- **Profile:** `@ActiveProfiles("test")` (no real API calls)
- **Uses:** Mocked AI services
- **Database:** H2 in-memory
- **No Dependencies:** Works without OPENAI_API_KEY
- **Tests:**
  - `shouldCreateProductsSuccessfully()`
  - `shouldRetrieveProductsByName()`
  - `shouldFilterProductsByPrice()`
  - `shouldHandleProductUpdates()`
  - `shouldHandleProductDeletion()`

### 🔑 RealAPI Tests (Excluded from `mvn verify`)

**Classes:**
- `LawFirmRealApiIntegrationTest.java` - Legal contract relationships
- `FinancialFraudRealApiIntegrationTest.java` - Transaction fraud detection
- `ECommerceRealApiIntegrationTest.java` - Product relationships

**Profile:** `@ActiveProfiles("realapi")`
- **Requires:** OPENAI_API_KEY environment variable
- **Uses:** Real OpenAI API (GPT-4o-mini)
- **Database:** H2 in-memory
- **Callable via:** Manual scripts or CI/CD with API key

---

## Maven Configuration Changes

### pom.xml Updates

#### 1. Maven Surefire Plugin (Unit Tests)
```xml
<excludes>
    <exclude>**/*IntegrationTest.java</exclude>
    <exclude>**/*IT.java</exclude>
    <exclude>**/*RealApiIntegrationTest.java</exclude>
</excludes>
```

#### 2. Maven Failsafe Plugin (Integration Tests)
```xml
<includes>
    <include>**/*IT.java</include>
    <include>**/*IntegrationTest.java</include>
</includes>
<excludes>
    <!-- Exclude RealAPI tests - they require OpenAI API key -->
    <exclude>**/realapi/*RealApiIntegrationTest.java</exclude>
</excludes>
```

#### 3. RealAPI Tests Profile
```xml
<profile>
    <id>realapi-tests</id>
    <build>
        <plugins>
            <plugin>
                <artifactId>maven-failsafe-plugin</artifactId>
                <includes>
                    <include>**/realapi/*RealApiIntegrationTest.java</include>
                </includes>
            </plugin>
        </plugins>
    </build>
</profile>
```

---

## How to Run Tests

### 1. Standard Verify (No API Key Needed)
```bash
cd ai-infrastructure-module/relationship-query-integration-tests
mvn clean verify
```
✅ Runs: `RelationshipQueryBasicIntegrationTest`
❌ Skips: RealAPI tests

### 2. Run RealAPI Tests (Requires API Key)
```bash
cd ai-infrastructure-module/relationship-query-integration-tests
export OPENAI_API_KEY='sk-proj-...'
bash run-relationship-query-realapi-tests.sh
```
✅ Runs: All `*RealApiIntegrationTest.java` tests
- LawFirmRealApiIntegrationTest
- FinancialFraudRealApiIntegrationTest
- ECommerceRealApiIntegrationTest

### 3. Parent Module Verify
```bash
cd ai-infrastructure-module
mvn clean verify
```
✅ Runs: All modules including relationship-query integration tests (non-RealAPI only)

---

## CI/CD Integration

### GitHub Actions: integration-tests-manual.yml

The manual workflow includes a step to run RealAPI tests:

```yaml
- name: Run Relationship Query Integration Tests
  run: |
    cd ai-infrastructure-module/relationship-query-integration-tests
    bash run-relationship-query-realapi-tests.sh
  env:
    OPENAI_API_KEY: ${{ github.event.inputs.openai_api_key }}
```

### GitHub Actions: parent-verify.yml

The automatic workflow runs standard verify (excludes RealAPI tests):

```yaml
- name: Run Maven Verify on AI Infrastructure Module
  run: |
    cd ai-infrastructure-module
    mvn clean verify -B -V
```
✅ Includes: Non-RealAPI tests
❌ Excludes: RealAPI tests

---

## Test Execution Flow

### Maven Standard Build (`mvn clean verify`)
```
┌─────────────────────────────────────────────┐
│ ai-infrastructure-module                    │
└─────────────────────────────────────────────┘
        ↓
        ├─→ ai-infrastructure-core
        │   ├─ Unit Tests: ✅
        │   ├─ Integration Tests: ✅
        │   └─ RealAPI Tests: ❌ (excluded)
        │
        ├─→ ai-infrastructure-behavior
        │   ├─ Unit Tests: ✅
        │   └─ RealAPI Tests: ❌ (excluded)
        │
        ├─→ ai-infrastructure-behavior-integration-tests
        │   ├─ Integration Tests: ✅
        │   └─ RealAPI Tests: ❌ (excluded, needs PostgreSQL)
        │
        ├─→ integration-tests
        │   ├─ Integration Tests: ✅
        │   └─ RealAPI Tests: ❌ (excluded)
        │
        └─→ relationship-query-integration-tests
            ├─ RelationshipQueryBasicIntegrationTest: ✅
            └─ RealAPI Tests: ❌ (excluded)
```

### RealAPI Execution (Manual)
```
bash run-relationship-query-realapi-tests.sh (with OPENAI_API_KEY)
        ↓
mvn test -Preal-api-test
        ↓
    Runs:
    ├─ LawFirmRealApiIntegrationTest
    ├─ FinancialFraudRealApiIntegrationTest
    └─ ECommerceRealApiIntegrationTest
```

---

## Summary

| Aspect | Details |
|--------|---------|
| **Standard Verify** | `mvn clean verify` |
| **Runs in Verify** | RelationshipQueryBasicIntegrationTest |
| **Excluded from Verify** | All *RealApiIntegrationTest.java |
| **RealAPI Script** | `run-relationship-query-realapi-tests.sh` |
| **Required for RealAPI** | OPENAI_API_KEY environment variable |
| **CI/CD Workflows** | Both manual and automatic configured |

---

## Benefits

✅ **Fast standard builds** - No API keys needed  
✅ **Comprehensive testing** - All functionality covered  
✅ **Clear separation** - RealAPI tests explicitly excluded  
✅ **Easy maintenance** - Scripts and profiles handle everything  
✅ **CI/CD ready** - Automated and manual workflows included  
✅ **Cost efficient** - API calls only when intentionally triggered  

