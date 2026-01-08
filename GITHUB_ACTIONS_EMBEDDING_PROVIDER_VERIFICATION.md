# GitHub Actions Embedding Provider Selection Verification

## Question
**Will the embedding provider selected in the GitHub Actions UI actually be used in the tests?**

## Analysis Flow

### 1. AI Infrastructure Tests (`run-provider-matrix-tests.sh`)

#### Flow:
1. **GitHub Actions UI** → User selects `embedding_provider: openai`
2. **Workflow** → Passes as: `"${{ github.event.inputs.llm_provider }}:${{ github.event.inputs.embedding_provider }}:${{ github.event.inputs.vector_database }}:${{ github.event.inputs.storage_strategy }}"`
   - Example: `"openai:openai:lucene:SINGLE_TABLE"`
3. **Script** → Receives as `MATRIX_SPEC="${1:-openai:onnx}"`
4. **Script** → Passes to Maven as: `-Dai.providers.real-api.matrix='$MATRIX_SPEC'`
5. **Test Class** (`AbstractProviderMatrixIntegrationTest`) → Reads via:
   ```java
   String matrixSpec = System.getProperty("ai.providers.real-api.matrix");
   ```
6. **Test Class** → Parses matrix spec and extracts embedding provider (2nd field)
7. **Test Class** → Sets system property:
   ```java
   System.setProperty("ai.providers.embedding-provider", combination.embeddingProvider());
   ```
8. **Spring Boot** → Reads from system property: `ai.providers.embedding-provider`

#### ✅ **VERDICT: YES, IT WILL BE USED**
- The embedding provider from the UI is correctly passed through the matrix spec
- The test class explicitly sets it as a system property
- Spring Boot reads it from the system property

---

### 2. Relationship Query Tests (`run-relationship-query-realapi-tests.sh`)

#### Flow:
1. **GitHub Actions UI** → User selects `embedding_provider: openai`
2. **Workflow** → Passes as: `"${{ github.event.inputs.llm_provider }}:${{ github.event.inputs.embedding_provider }}:${{ github.event.inputs.vector_database }}"`
   - Example: `"openai:openai:lucene"`
3. **Script** → Parses matrix spec:
   ```bash
   if [[ "$MATRIX_SPEC" =~ ^([^:]+):([^:]+):(.+)$ ]]; then
       EMBEDDING_PROVIDER="${BASH_REMATCH[2]}"  # Extracts "openai"
   fi
   ```
4. **Script** → Exports as environment variable:
   ```bash
   export AI_INFRASTRUCTURE_EMBEDDING_PROVIDER="${AI_INFRASTRUCTURE_EMBEDDING_PROVIDER:-$EMBEDDING_PROVIDER}"
   ```
5. **Script** → Runs Maven: `mvn -Prealapi failsafe:integration-test failsafe:verify`
6. **Maven Failsafe** → Should pass environment variables to test JVM
7. **Spring Boot** → Reads from `application-realapi.yml`:
   ```yaml
   embedding-provider: ${AI_INFRASTRUCTURE_EMBEDDING_PROVIDER:${EMBEDDING_PROVIDER:onnx}}
   ```

#### ⚠️ **POTENTIAL ISSUE: Environment Variable Passing**
- The script exports `AI_INFRASTRUCTURE_EMBEDDING_PROVIDER` as an environment variable
- Maven's Failsafe plugin should pass environment variables to the test JVM
- **However**, there's no explicit guarantee that Failsafe passes all environment variables
- Spring Boot should read it, but we should verify

#### ✅ **VERDICT: PROBABLY YES, BUT NEEDS VERIFICATION**
- The flow looks correct, but relies on Maven Failsafe passing environment variables
- Should work, but could fail if Failsafe doesn't pass env vars

---

### 3. Behavior Tests (`run-behavior-realapi-tests.sh`)

#### Flow:
1. **GitHub Actions UI** → User selects `embedding_provider: openai`
2. **Workflow** → Passes as: `"${{ github.event.inputs.llm_provider }}:${{ github.event.inputs.embedding_provider }}:${{ github.event.inputs.vector_database }}"`
   - Example: `"openai:openai:lucene"`
3. **Script** → Same parsing and export logic as relationship-query tests
4. **Script** → Exports: `export AI_INFRASTRUCTURE_EMBEDDING_PROVIDER="${AI_INFRASTRUCTURE_EMBEDDING_PROVIDER:-$EMBEDDING_PROVIDER}"`
5. **Maven Failsafe** → Should pass environment variables to test JVM
6. **Spring Boot** → Reads from application config (needs to check behavior config file)

#### ⚠️ **VERDICT: SAME AS RELATIONSHIP-QUERY**
- Same potential issue with environment variable passing

---

## Recommendations

### For Relationship Query and Behavior Tests

**Option 1: Convert to System Properties (Recommended)**
Update the scripts to pass embedding provider as a system property instead of relying on environment variables:

```bash
# In run-relationship-query-realapi-tests.sh
MAVEN_COMMAND="mvn -P${MAVEN_PROFILE}"
if [ -n "$AI_INFRASTRUCTURE_EMBEDDING_PROVIDER" ]; then
    MAVEN_COMMAND="$MAVEN_COMMAND -Dai.providers.embedding-provider=$AI_INFRASTRUCTURE_EMBEDDING_PROVIDER"
fi
```

**Option 2: Verify Environment Variable Passing**
Add explicit environment variable passing to Maven Failsafe configuration in `pom.xml`:
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-failsafe-plugin</artifactId>
    <configuration>
        <environmentVariables>
            <AI_INFRASTRUCTURE_EMBEDDING_PROVIDER>${env.AI_INFRASTRUCTURE_EMBEDDING_PROVIDER}</AI_INFRASTRUCTURE_EMBEDDING_PROVIDER>
        </environmentVariables>
    </configuration>
</plugin>
```

**Option 3: Use Maven Properties**
Pass as Maven system property and let Spring Boot read it:
```bash
MAVEN_COMMAND="$MAVEN_COMMAND -Dai.providers.embedding-provider=$EMBEDDING_PROVIDER"
```

---

## Current Status Summary

| Test Module | Method | Status | Confidence |
|-------------|--------|--------|------------|
| AI Infrastructure | System Property via Matrix | ✅ **WORKS** | High |
| Relationship Query | Environment Variable | ⚠️ **PROBABLY WORKS** | Medium |
| Behavior | Environment Variable | ⚠️ **PROBABLY WORKS** | Medium |

---

## Action Items

1. ✅ **AI Infrastructure Tests**: Already working correctly via matrix spec
2. ⚠️ **Relationship Query Tests**: Should verify or convert to system properties
3. ⚠️ **Behavior Tests**: Should verify or convert to system properties
4. 🔍 **Verification**: Add logging to confirm which embedding provider is actually used
