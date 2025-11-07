# KnowledgeBaseOverview - Implementation Guide

## What to Build

```java
public class KnowledgeBaseOverview {
    private Long totalIndexedDocuments;
    private Map<String, Long> documentsByType;
    private LocalDateTime lastIndexUpdateTime;
    private String indexHealth;
    private Map<String, Double> coverage;  // Coverage per type
}
```

---

## Step 1: Create the DTO (15 min)

**File:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/dto/KnowledgeBaseOverview.java`

```java
package com.ai.infrastructure.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Overview of the knowledge base - tells LLM what documents are indexed
 * 
 * Critical for intent extraction - without this, LLM doesn't know:
 * - What types of documents exist
 * - How many documents are indexed
 * - If data is fresh
 * - Whether information exists for a query
 * 
 * Example:
 * {
 *   "totalIndexedDocuments": 5000,
 *   "documentsByType": {
 *     "policies": 800,
 *     "products": 5000,
 *     "support": 543
 *   },
 *   "lastIndexUpdateTime": "2025-01-15T10:30:00",
 *   "indexHealth": "HEALTHY",
 *   "coverage": {
 *     "policy_coverage": 0.95,
 *     "product_coverage": 0.75,
 *     "support_coverage": 0.88
 *   }
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KnowledgeBaseOverview {
    
    /** Total number of documents indexed */
    private Long totalIndexedDocuments;
    
    /** Document count by type (e.g., "policies": 800, "products": 5000) */
    @Builder.Default
    private Map<String, Long> documentsByType = new HashMap<>();
    
    /** Last time the index was updated */
    private LocalDateTime lastIndexUpdateTime;
    
    /** Health status: HEALTHY, DEGRADED, REBUILDING, FAILED */
    private String indexHealth;
    
    /** Coverage percentage for each type (0.0 - 1.0) */
    @Builder.Default
    private Map<String, Double> coverage = new HashMap<>();
    
    // ==================== UTILITY METHODS ====================
    
    /**
     * Get human-readable status
     */
    public String getStatus() {
        if (indexHealth == null) {
            return "UNKNOWN";
        }
        
        StringBuilder status = new StringBuilder();
        status.append(indexHealth).append(" - ");
        status.append(totalIndexedDocuments).append(" total docs");
        
        if (lastIndexUpdateTime != null) {
            status.append(", updated ").append(getTimeSinceUpdate());
        }
        
        return status.toString();
    }
    
    /**
     * Get time since last update (human-readable)
     */
    public String getTimeSinceUpdate() {
        if (lastIndexUpdateTime == null) {
            return "unknown time";
        }
        
        long minutesAgo = java.time.temporal.ChronoUnit.MINUTES
            .between(lastIndexUpdateTime, LocalDateTime.now());
        
        if (minutesAgo < 1) return "just now";
        if (minutesAgo < 60) return minutesAgo + " minutes ago";
        
        long hoursAgo = minutesAgo / 60;
        if (hoursAgo < 24) return hoursAgo + " hours ago";
        
        long daysAgo = hoursAgo / 24;
        return daysAgo + " days ago";
    }
    
    /**
     * Check if data is fresh (less than 24 hours old)
     */
    public boolean isFresh() {
        if (lastIndexUpdateTime == null) {
            return false;
        }
        
        long hoursAgo = java.time.temporal.ChronoUnit.HOURS
            .between(lastIndexUpdateTime, LocalDateTime.now());
        
        return hoursAgo < 24;
    }
    
    /**
     * Check if index is healthy
     */
    public boolean isHealthy() {
        return "HEALTHY".equals(indexHealth);
    }
    
    /**
     * Get coverage for a specific type (default 0.0 if not found)
     */
    public Double getCoverageForType(String type) {
        return coverage.getOrDefault(type, 0.0);
    }
}
```

---

## Step 2: Create Builder Service (30 min)

**File:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/context/KnowledgeBaseOverviewBuilder.java`

```java
package com.ai.infrastructure.context;

import com.ai.infrastructure.dto.KnowledgeBaseOverview;
import com.ai.infrastructure.service.VectorDatabaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Builds KnowledgeBaseOverview by querying the vector database
 * 
 * This provides the LLM with critical context about:
 * - What documents are indexed
 * - How many of each type
 * - When was it last updated
 * - Is the index healthy
 * - Coverage percentage for each type
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseOverviewBuilder {
    
    private final VectorDatabaseService vectorDatabaseService;
    
    /**
     * Build complete knowledge base overview
     */
    public KnowledgeBaseOverview build() {
        log.debug("Building knowledge base overview");
        
        try {
            return KnowledgeBaseOverview.builder()
                .totalIndexedDocuments(getTotalDocumentCount())
                .documentsByType(getDocumentCountByType())
                .lastIndexUpdateTime(getLastUpdateTime())
                .indexHealth(getIndexHealth())
                .coverage(calculateCoverage())
                .build();
                
        } catch (Exception e) {
            log.error("Error building knowledge base overview", e);
            
            // Return degraded overview on error
            return buildDegradedOverview();
        }
    }
    
    /**
     * Get total count of indexed documents
     */
    private Long getTotalDocumentCount() {
        try {
            return vectorDatabaseService.countAllDocuments();
        } catch (Exception e) {
            log.error("Error getting total document count", e);
            return 0L;
        }
    }
    
    /**
     * Get document count by type
     */
    private Map<String, Long> getDocumentCountByType() {
        try {
            return vectorDatabaseService.countDocumentsByType();
        } catch (Exception e) {
            log.error("Error getting document counts by type", e);
            return new HashMap<>();
        }
    }
    
    /**
     * Get last update time of the index
     */
    private LocalDateTime getLastUpdateTime() {
        try {
            return vectorDatabaseService.getLastIndexUpdateTime();
        } catch (Exception e) {
            log.error("Error getting last update time", e);
            return null;
        }
    }
    
    /**
     * Get health status of the index
     */
    private String getIndexHealth() {
        try {
            String health = vectorDatabaseService.getIndexHealth();
            return health != null ? health : "UNKNOWN";
        } catch (Exception e) {
            log.error("Error getting index health", e);
            return "DEGRADED";
        }
    }
    
    /**
     * Calculate coverage percentage for each document type
     */
    private Map<String, Double> calculateCoverage() {
        Map<String, Double> coverage = new HashMap<>();
        
        try {
            Long total = getTotalDocumentCount();
            if (total == 0) {
                return coverage;
            }
            
            Map<String, Long> byType = getDocumentCountByType();
            
            for (Map.Entry<String, Long> entry : byType.entrySet()) {
                // Calculate percentage coverage
                double coverageRatio = (double) entry.getValue() / total;
                coverage.put(entry.getKey() + "_coverage", coverageRatio);
            }
            
            // Also calculate if we have external expectations
            addExpectedCoverage(coverage);
            
            return coverage;
            
        } catch (Exception e) {
            log.error("Error calculating coverage", e);
            return coverage;
        }
    }
    
    /**
     * Add expected coverage metrics from configuration
     */
    private void addExpectedCoverage(Map<String, Double> coverage) {
        // Query AI infrastructure for expected types
        try {
            Map<String, Double> expectedCoverage = 
                vectorDatabaseService.getExpectedCoverageMetrics();
            
            if (expectedCoverage != null) {
                coverage.putAll(expectedCoverage);
            }
        } catch (Exception e) {
            log.debug("No expected coverage metrics available");
        }
    }
    
    /**
     * Build degraded overview when errors occur
     */
    private KnowledgeBaseOverview buildDegradedOverview() {
        return KnowledgeBaseOverview.builder()
            .totalIndexedDocuments(0L)
            .documentsByType(new HashMap<>())
            .lastIndexUpdateTime(null)
            .indexHealth("DEGRADED")
            .coverage(new HashMap<>())
            .build();
    }
}
```

---

## Step 3: Update SystemContextBuilder (20 min)

**File:** Update `SystemContextBuilder.java`

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class SystemContextBuilder {
    
    private final KnowledgeBaseOverviewBuilder knowledgeBaseBuilder;
    private final AvailableActionsRegistry actionsRegistry;
    // ... other dependencies ...
    
    public SystemContext buildContext(String userId) {
        log.debug("Building system context for user: {}", userId);
        
        return SystemContext.builder()
            .userId(userId)
            // ← ADD THIS: Knowledge base overview
            .knowledgeBaseOverview(knowledgeBaseBuilder.build())
            .availableActions(actionsRegistry.getAllAvailableActions())
            .entityTypesSchema(buildEntityTypesSchema())
            .userBehaviorContext(buildUserBehaviorContext(userId))
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    private Object buildEntityTypesSchema() {
        // Your existing implementation
        return null;
    }
    
    private Object buildUserBehaviorContext(String userId) {
        // Your existing implementation
        return null;
    }
}
```

---

## Step 4: Update VectorDatabaseService (45 min)

**File:** Add methods to your `VectorDatabaseService` or similar

```java
@Service
public class VectorDatabaseService {
    
    // ... existing code ...
    
    /**
     * Count total documents in vector database
     */
    public Long countAllDocuments() {
        try {
            // Implementation depends on your vector DB (Pinecone, Weaviate, etc.)
            // Example for generic implementation:
            return vectorClient.count();
        } catch (Exception e) {
            log.error("Error counting documents", e);
            return 0L;
        }
    }
    
    /**
     * Count documents by type (entity type, namespace, etc.)
     */
    public Map<String, Long> countDocumentsByType() {
        Map<String, Long> counts = new HashMap<>();
        
        try {
            // Get all configured entity types
            List<String> entityTypes = getConfiguredEntityTypes();
            
            for (String type : entityTypes) {
                Long count = countDocumentsByType(type);
                counts.put(type, count);
                log.debug("Documents of type {}: {}", type, count);
            }
            
            return counts;
            
        } catch (Exception e) {
            log.error("Error counting documents by type", e);
            return new HashMap<>();
        }
    }
    
    /**
     * Count documents for a specific type
     */
    private Long countDocumentsByType(String type) {
        try {
            // Implementation depends on your vector DB
            // Example for Pinecone:
            // return vectorClient.describeIndexStats(type).getNamespaceSummaries()
            //     .getOrDefault(type, 0L);
            return 0L;
        } catch (Exception e) {
            log.error("Error counting documents for type: {}", type, e);
            return 0L;
        }
    }
    
    /**
     * Get last time index was updated
     */
    public LocalDateTime getLastIndexUpdateTime() {
        try {
            // Query metadata or use a stored timestamp
            // Example from database:
            return vectorDatabaseMetadataRepository
                .findLatestIndexUpdate()
                .orElse(LocalDateTime.now().minusHours(1));
                
        } catch (Exception e) {
            log.error("Error getting last update time", e);
            return null;
        }
    }
    
    /**
     * Get health status of the vector index
     */
    public String getIndexHealth() {
        try {
            // Implementation depends on your vector DB
            
            Long totalDocs = countAllDocuments();
            
            if (totalDocs == 0) {
                return "EMPTY";
            }
            
            if (isRebuildingInProgress()) {
                return "REBUILDING";
            }
            
            if (hasErrors()) {
                return "DEGRADED";
            }
            
            return "HEALTHY";
            
        } catch (Exception e) {
            log.error("Error getting index health", e);
            return "FAILED";
        }
    }
    
    /**
     * Get expected coverage metrics
     */
    public Map<String, Double> getExpectedCoverageMetrics() {
        Map<String, Double> coverage = new HashMap<>();
        
        try {
            // Get from configuration or database
            // Example:
            coverage.put("policy_coverage", 0.95);  // We expect 95% coverage
            coverage.put("product_coverage", 0.85);
            coverage.put("support_coverage", 0.88);
            
            return coverage;
            
        } catch (Exception e) {
            log.error("Error getting expected coverage", e);
            return new HashMap<>();
        }
    }
    
    private List<String> getConfiguredEntityTypes() {
        // Get from your entity configuration
        // Example:
        return List.of("policies", "products", "support", "faq", "guides");
    }
    
    private boolean isRebuildingInProgress() {
        // Check if rebuilding
        return false;
    }
    
    private boolean hasErrors() {
        // Check for errors
        return false;
    }
}
```

---

## Step 5: Update IntentQueryExtractor (20 min)

**File:** Update `IntentQueryExtractor.java`

```java
@Service
@Slf4j
public class IntentQueryExtractor {
    
    private final SystemContextBuilder contextBuilder;
    private final AICoreService aiCoreService;
    
    public MultiIntentResponse extract(String rawQuery, String userId) {
        try {
            // Get system context (now includes KB overview)
            SystemContext context = contextBuilder.buildContext(userId);
            
            // Build enriched prompt
            String systemPrompt = buildEnrichedSystemPrompt(context);
            
            String userPrompt = String.format(
                "Extract intents from this query: \"%s\"",
                rawQuery
            );
            
            log.debug("Extracting intents with KB context for query: {}", rawQuery);
            
            String jsonResponse = aiCoreService.generateText(
                systemPrompt,
                userPrompt
            );
            
            MultiIntentResponse response = parseResponse(jsonResponse, rawQuery);
            
            log.debug("Successfully extracted intents: {}", response.getIntents());
            
            return response;
            
        } catch (Exception e) {
            log.error("Intent extraction failed for query: {}", rawQuery, e);
            return buildFallbackResponse(rawQuery);
        }
    }
    
    private String buildEnrichedSystemPrompt(SystemContext context) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("You are an expert at understanding user intent and query structure.\n\n");
        
        // ← ADD THIS: Include KB overview in prompt
        prompt.append("==== KNOWLEDGE BASE STATUS ====\n");
        KnowledgeBaseOverview kb = context.getKnowledgeBaseOverview();
        if (kb != null) {
            prompt.append("Total indexed documents: ").append(kb.getTotalIndexedDocuments()).append("\n");
            prompt.append("Document types available:\n");
            
            for (Map.Entry<String, Long> entry : kb.getDocumentsByType().entrySet()) {
                Double cov = kb.getCoverageForType(entry.getKey() + "_coverage");
                int coveragePercent = (int) (cov * 100);
                prompt.append(String.format("  - %s: %d documents (%d%% coverage)\n",
                    entry.getKey(), entry.getValue(), coveragePercent));
            }
            
            prompt.append("Index health: ").append(kb.getIndexHealth()).append("\n");
            prompt.append("Last updated: ").append(kb.getTimeSinceUpdate()).append("\n\n");
        }
        
        prompt.append("==== INSTRUCTIONS ====\n");
        prompt.append("When analyzing the query:\n");
        prompt.append("1. Check if query relates to available document types\n");
        prompt.append("2. If YES → Set intent type to INFORMATION\n");
        prompt.append("3. If NO (not in KB) → Set intent type to OUT_OF_SCOPE\n");
        prompt.append("4. Use coverage percentages to set confidence level\n");
        prompt.append("5. If coverage <50% for type → Lower confidence, suggest alternative\n\n");
        
        prompt.append("==== RESPONSE FORMAT ====\n");
        prompt.append("Respond ONLY with valid JSON:\n");
        prompt.append("{\n");
        prompt.append("  \"rawQuery\": \"original query\",\n");
        prompt.append("  \"type\": \"INFORMATION|ACTION|OUT_OF_SCOPE\",\n");
        prompt.append("  \"confidence\": 0.0-1.0,\n");
        prompt.append("  \"vectorSpace\": \"which space to search\",\n");
        prompt.append("  \"reason\": \"why this type was chosen\"\n");
        prompt.append("}\n");
        
        return prompt.toString();
    }
    
    private MultiIntentResponse parseResponse(String json, String rawQuery) {
        // Your existing implementation
        return null;
    }
    
    private MultiIntentResponse buildFallbackResponse(String rawQuery) {
        // Your existing implementation
        return null;
    }
}
```

---

## Step 6: Create Tests (20 min)

**File:** `ai-infrastructure-core/src/test/java/com/ai/infrastructure/context/KnowledgeBaseOverviewBuilderTest.java`

```java
package com.ai.infrastructure.context;

import com.ai.infrastructure.dto.KnowledgeBaseOverview;
import com.ai.infrastructure.service.VectorDatabaseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class KnowledgeBaseOverviewBuilderTest {
    
    @Autowired
    private KnowledgeBaseOverviewBuilder builder;
    
    @MockBean
    private VectorDatabaseService vectorDatabaseService;
    
    @Test
    void shouldBuildKnowledgeBaseOverview() {
        // Setup
        when(vectorDatabaseService.countAllDocuments()).thenReturn(5000L);
        when(vectorDatabaseService.countDocumentsByType()).thenReturn(Map.of(
            "policies", 800L,
            "products", 5000L,
            "support", 543L
        ));
        when(vectorDatabaseService.getLastIndexUpdateTime())
            .thenReturn(LocalDateTime.now());
        when(vectorDatabaseService.getIndexHealth()).thenReturn("HEALTHY");
        
        // Execute
        KnowledgeBaseOverview overview = builder.build();
        
        // Verify
        assertThat(overview)
            .isNotNull()
            .satisfies(o -> {
                assertThat(o.getTotalIndexedDocuments()).isEqualTo(5000L);
                assertThat(o.getIndexHealth()).isEqualTo("HEALTHY");
                assertThat(o.isFresh()).isTrue();
                assertThat(o.isHealthy()).isTrue();
            });
    }
    
    @Test
    void shouldHandleDegradedHealth() {
        // Setup
        when(vectorDatabaseService.getIndexHealth()).thenReturn("DEGRADED");
        when(vectorDatabaseService.countAllDocuments()).thenReturn(5000L);
        when(vectorDatabaseService.countDocumentsByType()).thenReturn(Map.of());
        
        // Execute
        KnowledgeBaseOverview overview = builder.build();
        
        // Verify
        assertThat(overview.isHealthy()).isFalse();
        assertThat(overview.getIndexHealth()).isEqualTo("DEGRADED");
    }
    
    @Test
    void shouldProvideHumanReadableStatus() {
        // Setup
        when(vectorDatabaseService.countAllDocuments()).thenReturn(5000L);
        when(vectorDatabaseService.countDocumentsByType()).thenReturn(Map.of());
        when(vectorDatabaseService.getLastIndexUpdateTime())
            .thenReturn(LocalDateTime.now().minusHours(2));
        when(vectorDatabaseService.getIndexHealth()).thenReturn("HEALTHY");
        
        // Execute
        KnowledgeBaseOverview overview = builder.build();
        
        // Verify
        String status = overview.getStatus();
        assertThat(status)
            .contains("HEALTHY")
            .contains("5000")
            .contains("ago");
    }
}
```

---

## Configuration

**File:** `application.yml`

```yaml
ai:
  knowledge-base:
    # KB overview settings
    enabled: true
    refresh-interval-minutes: 5
    
    # Expected coverage targets
    expected-coverage:
      policies: 0.95
      products: 0.85
      support: 0.88
      faq: 0.90
    
    # Health check settings
    health-check:
      enabled: true
      interval-minutes: 1
      failure-threshold: 3
```

---

## Usage in LLM Prompt

```
KNOWLEDGE BASE STATUS:
═══════════════════════════════════════

Total indexed: 5,000 documents

Available document types:
  • Policies: 800 docs (95% coverage)
  • Products: 5,000 docs (85% coverage)
  • Support: 543 docs (88% coverage)
  • FAQ: 312 docs (90% coverage)

Index Health: HEALTHY
Last Updated: 2 hours ago

═══════════════════════════════════════

INTENT EXTRACTION RULES:
1. If query matches available type → INFORMATION
2. If query NOT in KB → OUT_OF_SCOPE
3. Set confidence based on coverage %
4. If coverage <50% → LOWER confidence
```

---

## Expected Output Example

```json
{
  "totalIndexedDocuments": 5000,
  "documentsByType": {
    "policies": 800,
    "products": 5000,
    "support": 543,
    "faq": 312
  },
  "lastIndexUpdateTime": "2025-01-15T10:30:45Z",
  "indexHealth": "HEALTHY",
  "coverage": {
    "policies_coverage": 0.95,
    "products_coverage": 0.85,
    "support_coverage": 0.88,
    "faq_coverage": 0.90
  }
}
```

---

## Benefits

✅ **LLM knows what's available** - Can decide intelligently
✅ **Better routing** - Searches right space first
✅ **Higher confidence** - Uses coverage metrics
✅ **Reduced hallucination** - Honest about gaps
✅ **Better user experience** - Knows when to say "I don't know"

---

## Implementation Time

- Step 1 (DTO): 15 min
- Step 2 (Builder): 30 min
- Step 3 (Update SystemContextBuilder): 20 min
- Step 4 (VectorDB methods): 45 min
- Step 5 (Update IntentQueryExtractor): 20 min
- Step 6 (Tests): 20 min

**Total: 2.5 hours**

---

## Next Steps

1. ✅ Create KnowledgeBaseOverview DTO
2. ✅ Create KnowledgeBaseOverviewBuilder
3. ✅ Update SystemContextBuilder to use it
4. ✅ Add methods to VectorDatabaseService
5. ✅ Update IntentQueryExtractor to include KB info in prompt
6. ✅ Write tests
7. ✅ Deploy and monitor

**Start today! 🚀**

