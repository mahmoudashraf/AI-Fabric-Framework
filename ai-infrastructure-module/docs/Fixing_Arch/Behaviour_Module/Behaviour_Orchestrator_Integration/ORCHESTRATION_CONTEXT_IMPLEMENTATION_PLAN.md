# Orchestration Context Implementation Plan

## 📋 Executive Summary

**Goal:** Refactor `RAGOrchestrator.orchestrate()` to accept a rich `OrchestrationContext` object instead of just `userId`, enabling support for both authenticated and anonymous users while preparing for behavior insights integration.

**Current Signature:**
```java
public OrchestrationResult orchestrate(String query, String userId)
```

**New Signature:**
```java
public OrchestrationResult orchestrate(String query, OrchestrationContext context)
```

**Key Benefits:**
- ✅ Support anonymous/public use cases (e-commerce, docs, catalogs)
- ✅ Support authenticated/private use cases (SaaS, enterprise)
- ✅ Richer context for behavior insights (session, device, locale)
- ✅ Future-proof API (easy to add new context fields)
- ✅ Session tracking for anonymous users
- ✅ Better personalization capabilities

---

## 🎯 Design Principles

1. **Flexible Authentication** - Support both authenticated and anonymous users
2. **Required Identifier** - Either `userId` OR `sessionId` must be present
3. **Progressive Enhancement** - Start simple, add context as needed
4. **Backward Compatible** - Provide clear migration path
5. **Security First** - Context enables better security decisions
6. **Behavior Ready** - Designed for behavior insights integration

---

## 📦 Phase 1: Create OrchestrationContext

### 1.1 Create OrchestrationContext Class

**Location:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/intent/orchestration/OrchestrationContext.java`

```java
package com.ai.infrastructure.intent.orchestration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Context for orchestration requests.
 * Supports both authenticated users (userId) and anonymous users (sessionId).
 * At least one identifier (userId or sessionId) must be present.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrchestrationContext {
    
    /**
     * User ID for authenticated users.
     * Optional - null for anonymous requests.
     */
    private String userId;
    
    /**
     * Session ID for tracking.
     * Required for anonymous users, optional for authenticated.
     */
    private String sessionId;
    
    /**
     * Request ID for tracing.
     * Auto-generated if not provided.
     */
    private String requestId;
    
    /**
     * Client IP address.
     * Used for rate limiting, security, and analytics.
     */
    private String ipAddress;
    
    /**
     * User agent string.
     * Used for device detection and behavior analysis.
     */
    private String userAgent;
    
    /**
     * User's locale for i18n responses.
     */
    private Locale locale;
    
    /**
     * Additional metadata.
     * Can include: subscription tier, device type, referrer, etc.
     */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
    
    // ========== Helper Methods ==========
    
    /**
     * Check if this is an authenticated request.
     */
    public boolean isAuthenticated() {
        return userId != null && !userId.isBlank();
    }
    
    /**
     * Check if this is an anonymous request.
     */
    public boolean isAnonymous() {
        return !isAuthenticated();
    }
    
    /**
     * Get the primary identifier (userId for authenticated, sessionId for anonymous).
     */
    public String getIdentifier() {
        return isAuthenticated() ? userId : sessionId;
    }
    
    /**
     * Get or generate request ID.
     */
    public String getOrGenerateRequestId() {
        if (requestId == null || requestId.isBlank()) {
            requestId = "rag-" + UUID.randomUUID();
        }
        return requestId;
    }
    
    /**
     * Validate that context has required fields.
     * @throws IllegalArgumentException if invalid
     */
    public void validate() {
        if (!isAuthenticated() && (sessionId == null || sessionId.isBlank())) {
            throw new IllegalArgumentException(
                "OrchestrationContext must have either userId (for authenticated) or sessionId (for anonymous)"
            );
        }
    }
    
    // ========== Factory Methods ==========
    
    /**
     * Create context for authenticated user.
     */
    public static OrchestrationContext forUser(String userId) {
        return OrchestrationContext.builder()
            .userId(userId)
            .build();
    }
    
    /**
     * Create context for anonymous session.
     */
    public static OrchestrationContext forSession(String sessionId) {
        return OrchestrationContext.builder()
            .sessionId(sessionId)
            .build();
    }
    
    /**
     * Create anonymous context with auto-generated session ID.
     */
    public static OrchestrationContext anonymous() {
        return OrchestrationContext.builder()
            .sessionId("anon-" + UUID.randomUUID())
            .build();
    }
    
    /**
     * Create context for testing.
     */
    public static OrchestrationContext forTest() {
        return OrchestrationContext.builder()
            .userId("test-user-" + UUID.randomUUID())
            .sessionId("test-session-" + UUID.randomUUID())
            .build();
    }
}
```

### 1.2 Add Unit Tests

**Location:** `ai-infrastructure-core/src/test/java/com/ai/infrastructure/intent/orchestration/OrchestrationContextTest.java`

```java
package com.ai.infrastructure.intent.orchestration;

import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class OrchestrationContextTest {
    
    @Test
    void authenticatedContext_shouldHaveUserId() {
        OrchestrationContext context = OrchestrationContext.forUser("user-123");
        
        assertThat(context.isAuthenticated()).isTrue();
        assertThat(context.isAnonymous()).isFalse();
        assertThat(context.getUserId()).isEqualTo("user-123");
        assertThat(context.getIdentifier()).isEqualTo("user-123");
    }
    
    @Test
    void anonymousContext_shouldHaveSessionId() {
        OrchestrationContext context = OrchestrationContext.forSession("sess-456");
        
        assertThat(context.isAuthenticated()).isFalse();
        assertThat(context.isAnonymous()).isTrue();
        assertThat(context.getSessionId()).isEqualTo("sess-456");
        assertThat(context.getIdentifier()).isEqualTo("sess-456");
    }
    
    @Test
    void validate_withUserId_shouldPass() {
        OrchestrationContext context = OrchestrationContext.forUser("user-123");
        
        assertThatCode(() -> context.validate()).doesNotThrowAnyException();
    }
    
    @Test
    void validate_withSessionId_shouldPass() {
        OrchestrationContext context = OrchestrationContext.forSession("sess-456");
        
        assertThatCode(() -> context.validate()).doesNotThrowAnyException();
    }
    
    @Test
    void validate_withoutIdentifier_shouldFail() {
        OrchestrationContext context = OrchestrationContext.builder().build();
        
        assertThatThrownBy(() -> context.validate())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("userId")
            .hasMessageContaining("sessionId");
    }
    
    @Test
    void getOrGenerateRequestId_shouldGenerateIfMissing() {
        OrchestrationContext context = OrchestrationContext.forUser("user-123");
        
        String requestId = context.getOrGenerateRequestId();
        
        assertThat(requestId).isNotNull().startsWith("rag-");
        assertThat(context.getRequestId()).isEqualTo(requestId);
    }
    
    @Test
    void getOrGenerateRequestId_shouldReturnExisting() {
        OrchestrationContext context = OrchestrationContext.builder()
            .userId("user-123")
            .requestId("custom-123")
            .build();
        
        assertThat(context.getOrGenerateRequestId()).isEqualTo("custom-123");
    }
    
    @Test
    void builder_shouldSupportFullContext() {
        OrchestrationContext context = OrchestrationContext.builder()
            .userId("user-123")
            .sessionId("sess-456")
            .requestId("req-789")
            .ipAddress("192.168.1.1")
            .userAgent("Mozilla/5.0")
            .locale(Locale.US)
            .metadata(Map.of("tier", "premium"))
            .build();
        
        assertThat(context.getUserId()).isEqualTo("user-123");
        assertThat(context.getSessionId()).isEqualTo("sess-456");
        assertThat(context.getRequestId()).isEqualTo("req-789");
        assertThat(context.getIpAddress()).isEqualTo("192.168.1.1");
        assertThat(context.getUserAgent()).isEqualTo("Mozilla/5.0");
        assertThat(context.getLocale()).isEqualTo(Locale.US);
        assertThat(context.getMetadata()).containsEntry("tier", "premium");
    }
    
    @Test
    void anonymous_shouldGenerateSessionId() {
        OrchestrationContext context = OrchestrationContext.anonymous();
        
        assertThat(context.isAnonymous()).isTrue();
        assertThat(context.getSessionId()).isNotNull().startsWith("anon-");
    }
}
```

---

## 🔄 Phase 2: Update RAGOrchestrator

### 2.1 Update Orchestrate Method Signature

**Location:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/intent/orchestration/RAGOrchestrator.java`

**Changes:**

1. **Add new method with OrchestrationContext**
2. **Keep old method temporarily** (deprecate it)
3. **Update internal logic to use context**

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class RAGOrchestrator {
    
    // ... existing fields ...
    
    /**
     * NEW: Orchestrate with rich context.
     * Supports both authenticated and anonymous users.
     */
    public OrchestrationResult orchestrate(String query, OrchestrationContext context) {
        Objects.requireNonNull(query, "query must not be null");
        Objects.requireNonNull(context, "context must not be null");
        context.validate();
        
        String identifier = context.getIdentifier();
        String requestId = context.getOrGenerateRequestId();
        LocalDateTime requestTimestamp = LocalDateTime.now(clock);
        
        log.info("Orchestrating query for identifier: {} (authenticated: {})", 
            identifier, context.isAuthenticated());
        
        // STEP 1: Security check
        AISecurityResponse securityResponse = securityService.analyzeRequest(
            AISecurityRequest.builder()
                .requestId(requestId)
                .userId(identifier)
                .content(query)
                .operationType("INTENT_QUERY")
                .timestamp(requestTimestamp)
                .metadata(buildSecurityMetadata(context))
                .build()
        );
        
        if (Boolean.TRUE.equals(securityResponse.getShouldBlock())) {
            return OrchestrationResult.error("Request blocked by security controls.");
        }
        
        // STEP 2: Access control
        AIAccessControlResponse accessResponse = accessControlService.checkAccess(
            AIAccessControlRequest.builder()
                .requestId(requestId)
                .userId(identifier)
                .resourceId("rag:intent")
                .operationType("READ")
                .context(query)
                .metadata(buildAccessControlMetadata(context))
                .timestamp(requestTimestamp)
                .build()
        );
        
        if (!Boolean.TRUE.equals(accessResponse.getAccessGranted())) {
            return OrchestrationResult.error("Access denied by policy.");
        }
        
        // STEP 3: PII Detection
        List<String> detectedPiiTypes = new ArrayList<>();
        String processedQuery = query;
        
        PIIDetectionProperties.PIIDetectionDirection detectionDirection =
            piiDetectionProperties.getDetectionDirection();
        
        boolean detectInput = piiDetectionProperties.isEnabled() &&
            (detectionDirection == PIIDetectionProperties.PIIDetectionDirection.INPUT ||
             detectionDirection == PIIDetectionProperties.PIIDetectionDirection.INPUT_OUTPUT);
        
        if (detectInput) {
            PIIDetectionResult queryPiiAnalysis = piiDetectionService.analyze(query);
            if (queryPiiAnalysis.isPiiDetected()) {
                detectedPiiTypes = queryPiiAnalysis.getDetections().stream()
                    .map(PIIDetection::getType)
                    .filter(t -> t != null && !t.isBlank())
                    .distinct()
                    .collect(Collectors.toList());
                log.info("PII detected in user query - types: {}", detectedPiiTypes);
            }
            processedQuery = queryPiiAnalysis.getProcessedQuery();
        }
        
        // STEP 4: Compliance check
        AIComplianceResponse complianceResponse = complianceService.checkCompliance(
            AIComplianceRequest.builder()
                .requestId(requestId)
                .userId(identifier)
                .content(processedQuery)
                .timestamp(requestTimestamp)
                .build()
        );
        
        if (Boolean.FALSE.equals(complianceResponse.getOverallCompliant())) {
            return OrchestrationResult.error("Request failed compliance validation.");
        }
        
        // STEP 5: Intent extraction with context
        MultiIntentResponse multiIntentResponse = intentQueryExtractor.extract(
            processedQuery, 
            context  // Pass full context instead of just userId
        );
        
        if (!multiIntentResponse.hasIntents()) {
            return OrchestrationResult.error("No intents extracted from query.");
        }
        
        // STEP 6: Handle intents
        OrchestrationResult result = handleIntents(multiIntentResponse, context);
        
        return result;
    }
    
    /**
     * DEPRECATED: Use orchestrate(String query, OrchestrationContext context) instead.
     * This method will be removed in future versions.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public OrchestrationResult orchestrate(String query, String userId) {
        log.warn("Using deprecated orchestrate(query, userId). Migrate to orchestrate(query, context)");
        return orchestrate(query, OrchestrationContext.forUser(userId));
    }
    
    // ========== Helper Methods ==========
    
    private Map<String, Object> buildSecurityMetadata(OrchestrationContext context) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("authenticated", context.isAuthenticated());
        if (context.getIpAddress() != null) {
            metadata.put("ipAddress", context.getIpAddress());
        }
        if (context.getUserAgent() != null) {
            metadata.put("userAgent", context.getUserAgent());
        }
        if (context.getMetadata() != null && !context.getMetadata().isEmpty()) {
            metadata.putAll(context.getMetadata());
        }
        return metadata;
    }
    
    private Map<String, Object> buildAccessControlMetadata(OrchestrationContext context) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("entryPoint", "RAG_ORCHESTRATOR");
        metadata.put("authenticated", context.isAuthenticated());
        metadata.put("sessionId", context.getSessionId());
        if (context.getIpAddress() != null) {
            metadata.put("ipAddress", context.getIpAddress());
        }
        return metadata;
    }
    
    private OrchestrationResult handleIntents(
        MultiIntentResponse multiIntentResponse, 
        OrchestrationContext context
    ) {
        if (multiIntentResponse.getIntents().size() == 1) {
            return handleSingleIntent(multiIntentResponse.getIntents().get(0), context);
        } else {
            return handleMultipleIntents(multiIntentResponse, context);
        }
    }
    
    private OrchestrationResult handleSingleIntent(Intent intent, OrchestrationContext context) {
        return switch (intent.getType()) {
            case ACTION -> handleAction(intent, context);
            case INFORMATION -> handleInformation(intent, context);
            case OUT_OF_SCOPE -> handleOutOfScope(intent);
            case COMPOUND -> handleSyntheticCompound(intent, context);
            default -> OrchestrationResult.error("Unknown intent type: " + intent.getType());
        };
    }
    
    private OrchestrationResult handleAction(Intent intent, OrchestrationContext context) {
        String actionName = StringUtils.hasText(intent.getAction()) 
            ? intent.getAction() 
            : intent.getIntent();
            
        if (!StringUtils.hasText(actionName)) {
            return OrchestrationResult.error("Intent is missing an action name.");
        }
        
        Optional<ActionHandler> maybeHandler = actionHandlerRegistry.findHandler(actionName);
        if (maybeHandler.isEmpty()) {
            return OrchestrationResult.error("No action handler registered for action '" + actionName + "'");
        }
        
        ActionHandler handler = maybeHandler.get();
        Map<String, Object> params = intent.getActionParams();
        
        // Use identifier instead of userId
        String identifier = context.getIdentifier();
        if (!handler.validateActionAllowed(identifier)) {
            AIActionMetaData metadata = metadataForAction(actionName);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("action", actionName);
            if (metadata != null) {
                data.put("metadata", metadata);
            }
            return OrchestrationResult.builder()
                .type(OrchestrationResultType.ACTION_DENIED)
                .success(false)
                .message("Action not permitted for this user.")
                .data(Collections.unmodifiableMap(data))
                .nextSteps(extractNextSteps(intent))
                .build();
        }
        
        String confirmationMessage = handler.getConfirmationMessage(params);
        try {
            ActionResult actionResult = handler.execute(identifier, params);
            
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("action", actionName);
            data.put("result", actionResult);
            
            return OrchestrationResult.builder()
                .type(OrchestrationResultType.ACTION_EXECUTED)
                .success(actionResult.isSuccess())
                .message(actionResult.getMessage())
                .data(Collections.unmodifiableMap(data))
                .nextSteps(extractNextSteps(intent))
                .build();
        } catch (Exception e) {
            log.error("Action execution failed: {}", actionName, e);
            return OrchestrationResult.error("Action execution failed: " + e.getMessage());
        }
    }
    
    private OrchestrationResult handleInformation(Intent intent, OrchestrationContext context) {
        boolean needsGeneration = intent.requiresGenerationOrDefault(false);
        String optimizedQuery = StringUtils.hasText(intent.getOptimizedQuery()) 
            ? intent.getOptimizedQuery() 
            : null;
        String query = StringUtils.hasText(optimizedQuery) 
            ? optimizedQuery 
            : intent.getIntentOrAction();
        
        String identifier = context.getIdentifier();
        
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", "orchestrator");
        metadata.put("userId", identifier);
        metadata.put("authenticated", context.isAuthenticated());
        metadata.put("sessionId", context.getSessionId());
        metadata.put("requiresGeneration", needsGeneration);
        if (optimizedQuery != null) {
            metadata.put("optimizedQuery", optimizedQuery);
        }
        
        RAGRequest ragRequest = RAGRequest.builder()
            .query(query)
            .entityType(intent.getVectorSpace())
            .limit(DEFAULT_RAG_LIMIT)
            .threshold(DEFAULT_RAG_THRESHOLD)
            .metadata(Collections.unmodifiableMap(metadata))
            .userId(identifier)
            .build();
        
        RAGResponse ragResponse = needsGeneration
            ? ragService.performRAGQuery(ragRequest)
            : ragService.performRag(ragRequest);
        
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("answer", ragResponse.getResponse());
        data.put("documents", ragResponse.getDocuments());
        data.put("ragResponse", ragResponse);
        data.put("requiresGeneration", needsGeneration);
        
        String message = StringUtils.hasText(ragResponse.getResponse()) 
            ? ragResponse.getResponse() 
            : "Search completed.";
        
        return OrchestrationResult.builder()
            .type(OrchestrationResultType.INFORMATION_PROVIDED)
            .success(Boolean.TRUE.equals(ragResponse.getSuccess()) || ragResponse.getSuccess() == null)
            .message(message)
            .data(Collections.unmodifiableMap(data))
            .nextSteps(extractNextSteps(intent))
            .build();
    }
    
    // ... rest of methods updated similarly ...
}
```

---

## 🔌 Phase 3: Update IntentQueryExtractor

### 3.1 Update Extract Method

**Location:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/intent/IntentQueryExtractor.java`

Update to accept `OrchestrationContext` instead of just `userId`:

```java
/**
 * NEW: Extract intents with full orchestration context.
 */
public MultiIntentResponse extract(String query, OrchestrationContext context) {
    // Pass context to SystemContextBuilder
    SystemContext systemContext = systemContextBuilder.buildContext(context);
    
    // ... rest of logic
}

/**
 * DEPRECATED: Use extract(String query, OrchestrationContext context) instead.
 */
@Deprecated(since = "2.0", forRemoval = true)
public MultiIntentResponse extract(String query, String userId) {
    return extract(query, OrchestrationContext.forUser(userId));
}
```

### 3.2 Update SystemContextBuilder

**Location:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/intent/SystemContextBuilder.java`

```java
@Service
public class SystemContextBuilder {
    
    private final AvailableActionsRegistry availableActionsRegistry;
    private final KnowledgeBaseOverviewService knowledgeBaseOverviewService;
    private final Clock clock;
    
    /**
     * NEW: Build context from OrchestrationContext.
     */
    public SystemContext buildContext(OrchestrationContext orchestrationContext) {
        List<ActionInfo> actions = availableActionsRegistry.getAllAvailableActions();
        KnowledgeBaseOverview overview = knowledgeBaseOverviewService.getOverview();
        
        return SystemContext.builder()
            .availableActions(actions)
            .knowledgeBaseOverview(overview)
            .userId(orchestrationContext.getUserId())  // May be null for anonymous
            .sessionId(orchestrationContext.getSessionId())
            .authenticated(orchestrationContext.isAuthenticated())
            .locale(orchestrationContext.getLocale())
            .metadata(orchestrationContext.getMetadata())
            .timestamp(LocalDateTime.now(clock))
            .build();
    }
    
    /**
     * DEPRECATED: Use buildContext(OrchestrationContext) instead.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public SystemContext buildContext(String userId) {
        return buildContext(OrchestrationContext.forUser(userId));
    }
}
```

### 3.3 Update SystemContext

**Location:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/intent/SystemContext.java`

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SystemContext {
    
    @Builder.Default
    private List<ActionInfo> availableActions = List.of();
    
    private KnowledgeBaseOverview knowledgeBaseOverview;
    
    // May be null for anonymous users
    private String userId;
    
    // Session ID for anonymous tracking
    private String sessionId;
    
    // Authentication status
    private Boolean authenticated;
    
    // User locale
    private Locale locale;
    
    // Additional metadata
    private Map<String, Object> metadata;
    
    private LocalDateTime timestamp;
    
    // Helper methods
    public boolean isAuthenticated() {
        return Boolean.TRUE.equals(authenticated);
    }
    
    public String getIdentifier() {
        return isAuthenticated() ? userId : sessionId;
    }
}
```

---

## 🧪 Phase 4: Update Tests

### 4.1 Update RAGOrchestratorTest

**Location:** `ai-infrastructure-core/src/test/java/com/ai/infrastructure/intent/orchestration/RAGOrchestratorTest.java`

```java
// Update all test methods to use new signature
@Test
void shouldHandleActionIntent() {
    // Arrange
    OrchestrationContext context = OrchestrationContext.forUser("user-1");
    
    // ... rest of test
    
    // Act
    OrchestrationResult result = orchestrator.orchestrate("Cancel my plan", context);
    
    // Assert
    assertThat(result).isNotNull();
}

@Test
void shouldHandleAnonymousQuery() {
    // Arrange
    OrchestrationContext context = OrchestrationContext.forSession("sess-123");
    
    // Mock appropriate responses for anonymous
    // ...
    
    // Act
    OrchestrationResult result = orchestrator.orchestrate("Find products", context);
    
    // Assert
    assertThat(result).isNotNull();
    assertThat(result.isSuccess()).isTrue();
}

@Test
void shouldEnrichContextWithMetadata() {
    // Arrange
    OrchestrationContext context = OrchestrationContext.builder()
        .userId("user-1")
        .sessionId("sess-123")
        .ipAddress("192.168.1.1")
        .userAgent("Mozilla/5.0")
        .metadata(Map.of("tier", "premium"))
        .build();
    
    // Act
    OrchestrationResult result = orchestrator.orchestrate("Search", context);
    
    // Assert - verify metadata was used in security/access control
    // ...
}
```

### 4.2 Update Integration Tests

Update all integration tests in `integration-tests/` module to use new context-based signature.

---

## 🔗 Phase 5: Behavior Insights Integration (SPI Pattern)

**CRITICAL:** Avoid circular dependency by using Service Provider Interface (SPI) pattern.

### 5.1 Define SPI in Core Module (No Circular Dependency!)

**Problem:** If core depends on behavior module, and behavior already depends on core → circular dependency ❌

**Solution:** Core defines interface, behavior implements it ✅

#### Create BehaviorContextProvider Interface

**Location:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/spi/BehaviorContextProvider.java`

```java
package com.ai.infrastructure.spi;

import com.ai.infrastructure.intent.orchestration.OrchestrationContext;

import java.util.Optional;

/**
 * SPI for providing behavior context during orchestration.
 * Implementations are provided by the behavior module if available.
 * 
 * This interface allows core orchestration to enrich context with behavior
 * insights WITHOUT creating a compile-time dependency on the behavior module.
 * 
 * @see BehaviorContext
 */
public interface BehaviorContextProvider {
    
    /**
     * Get behavior context for the given orchestration context.
     * 
     * @param context The orchestration context (contains userId or sessionId)
     * @return Behavior context if available, empty otherwise
     */
    Optional<BehaviorContext> getBehaviorContext(OrchestrationContext context);
}
```

#### Create BehaviorContext DTO

**Location:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/spi/BehaviorContext.java`

```java
package com.ai.infrastructure.spi;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Behavior context for orchestration.
 * This is a minimal DTO that doesn't depend on behavior module entities.
 * It carries behavior insights from the behavior module to the core orchestrator.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BehaviorContext {
    
    // User/Session identification
    private String userId;
    private String sessionId;
    
    // Sentiment
    private String sentimentLabel;      // e.g., "SATISFIED", "FRUSTRATED"
    private Double sentimentScore;      // 0.0 to 1.0
    
    // Churn prediction
    private Double churnRisk;           // 0.0 to 1.0
    private String churnReason;
    
    // Behavior patterns
    private String segment;             // e.g., "Pro", "Free", "Enterprise"
    private String trend;               // e.g., "IMPROVING", "DECLINING"
    private List<String> patterns;      // e.g., ["power_user", "mobile_preference"]
    private List<String> recommendations; // e.g., ["upsell", "retention_offer"]
    
    // Analysis metadata
    private Double confidence;
    private LocalDateTime analyzedAt;
    private Map<String, Object> insights;
    
    /**
     * Format behavior context for LLM prompt injection.
     * @return Formatted string ready for prompt inclusion
     */
    public String toPromptString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[BEHAVIOR CONTEXT]\n");
        if (segment != null) {
            sb.append("segment: ").append(segment).append(" | ");
        }
        if (sentimentLabel != null && sentimentScore != null) {
            sb.append("sentiment: ").append(sentimentLabel)
              .append(" (").append(String.format("%.2f", sentimentScore)).append(") | ");
        }
        if (churnRisk != null) {
            sb.append("churn: ").append(String.format("%.2f", churnRisk));
            if (churnReason != null) {
                sb.append(" (").append(churnReason).append(")");
            }
        }
        sb.append("\n");
        if (trend != null) {
            sb.append("trend: ").append(trend).append(" | ");
        }
        if (confidence != null) {
            sb.append("confidence: ").append(String.format("%.2f", confidence));
        }
        sb.append("\n");
        if (patterns != null && !patterns.isEmpty()) {
            sb.append("patterns: ").append(String.join(", ", patterns)).append("\n");
        }
        if (recommendations != null && !recommendations.isEmpty()) {
            sb.append("recommendations: ").append(String.join(", ", recommendations)).append("\n");
        }
        if (analyzedAt != null) {
            sb.append("analyzedAt: ").append(analyzedAt).append("\n");
        }
        return sb.toString();
    }
}
```

### 5.2 Update SystemContextBuilder (Core) to Use Optional Provider

**Location:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/intent/SystemContextBuilder.java`

```java
package com.ai.infrastructure.intent;

import com.ai.infrastructure.intent.action.ActionInfo;
import com.ai.infrastructure.intent.action.AvailableActionsRegistry;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.spi.BehaviorContextProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Builds the aggregated context object consumed by the prompt builder.
 * Optionally enriches with behavior insights if BehaviorContextProvider is available.
 */
@Slf4j
@Service
public class SystemContextBuilder {
    
    private final AvailableActionsRegistry availableActionsRegistry;
    private final KnowledgeBaseOverviewService knowledgeBaseOverviewService;
    private final Optional<BehaviorContextProvider> behaviorContextProvider;
    private final Clock clock;
    
    public SystemContextBuilder(
        AvailableActionsRegistry availableActionsRegistry,
        KnowledgeBaseOverviewService knowledgeBaseOverviewService,
        ObjectProvider<BehaviorContextProvider> behaviorContextProviderProvider,
        ObjectProvider<Clock> clockProvider
    ) {
        this.availableActionsRegistry = availableActionsRegistry;
        this.knowledgeBaseOverviewService = knowledgeBaseOverviewService;
        this.behaviorContextProvider = Optional.ofNullable(
            behaviorContextProviderProvider.getIfAvailable()
        );
        this.clock = clockProvider.getIfAvailable(Clock::systemUTC);
        
        if (behaviorContextProvider.isPresent()) {
            log.info("BehaviorContextProvider available - behavior insights enabled");
        } else {
            log.info("BehaviorContextProvider not available - behavior insights disabled");
        }
    }
    
    public SystemContext buildContext(OrchestrationContext orchestrationContext) {
        List<ActionInfo> actions = availableActionsRegistry.getAllAvailableActions();
        KnowledgeBaseOverview overview = knowledgeBaseOverviewService.getOverview();
        
        SystemContext.SystemContextBuilder builder = SystemContext.builder()
            .availableActions(actions)
            .knowledgeBaseOverview(overview)
            .userId(orchestrationContext.getUserId())
            .sessionId(orchestrationContext.getSessionId())
            .authenticated(orchestrationContext.isAuthenticated())
            .locale(orchestrationContext.getLocale())
            .metadata(orchestrationContext.getMetadata())
            .timestamp(LocalDateTime.now(clock));
        
        // Optional: Add behavior context if provider is available
        behaviorContextProvider.ifPresent(provider -> {
            provider.getBehaviorContext(orchestrationContext)
                .ifPresent(behaviorContext -> {
                    log.debug("Enriching system context with behavior insights for identifier: {}", 
                        orchestrationContext.getIdentifier());
                    builder.behaviorContext(behaviorContext);
                });
        });
        
        return builder.build();
    }
    
    /**
     * DEPRECATED: Use buildContext(OrchestrationContext) instead.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public SystemContext buildContext(String userId) {
        return buildContext(OrchestrationContext.forUser(userId));
    }
}
```

### 5.3 Update SystemContext (Core)

**Location:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/intent/SystemContext.java`

```java
package com.ai.infrastructure.intent;

import com.ai.infrastructure.intent.action.ActionInfo;
import com.ai.infrastructure.spi.BehaviorContext;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Bundles the contextual information passed to the intent extraction prompt.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SystemContext {
    
    @Builder.Default
    private List<ActionInfo> availableActions = List.of();
    
    private KnowledgeBaseOverview knowledgeBaseOverview;
    
    // May be null for anonymous users
    private String userId;
    
    // Session ID for anonymous tracking
    private String sessionId;
    
    // Authentication status
    private Boolean authenticated;
    
    // User locale
    private Locale locale;
    
    // Additional metadata
    private Map<String, Object> metadata;
    
    private LocalDateTime timestamp;
    
    // NEW: Optional behavior context (null if behavior module not present)
    private BehaviorContext behaviorContext;
    
    // Helper methods
    public boolean isAuthenticated() {
        return Boolean.TRUE.equals(authenticated);
    }
    
    public String getIdentifier() {
        return isAuthenticated() ? userId : sessionId;
    }
    
    public boolean hasBehaviorContext() {
        return behaviorContext != null;
    }
    
    public void setAvailableActions(List<ActionInfo> availableActions) {
        this.availableActions = availableActions == null ? List.of() : List.copyOf(availableActions);
    }
    
    public void setKnowledgeBaseOverview(KnowledgeBaseOverview knowledgeBaseOverview) {
        this.knowledgeBaseOverview = knowledgeBaseOverview;
    }
}
```

### 5.4 Implement Provider in Behavior Module

**Location:** `ai-infrastructure-behavior/src/main/java/com/ai/infrastructure/behavior/integration/BehaviorContextProviderImpl.java`

```java
package com.ai.infrastructure.behavior.integration;

import com.ai.infrastructure.behavior.entity.BehaviorInsights;
import com.ai.infrastructure.behavior.service.BehaviorStorageAdapter;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.spi.BehaviorContext;
import com.ai.infrastructure.spi.BehaviorContextProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of BehaviorContextProvider.
 * Bridges behavior module entities to core SPI contract.
 * 
 * This component will be automatically discovered by Spring and injected
 * into SystemContextBuilder (core module) if behavior module is present.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BehaviorContextProviderImpl implements BehaviorContextProvider {
    
    private final BehaviorStorageAdapter storageAdapter;
    
    // Maximum age for insights (24 hours)
    private static final Duration MAX_INSIGHT_AGE = Duration.ofHours(24);
    
    @Override
    public Optional<BehaviorContext> getBehaviorContext(OrchestrationContext context) {
        // Only fetch for authenticated users
        if (!context.isAuthenticated()) {
            log.trace("Anonymous user - no behavior insights available");
            return Optional.empty();
        }
        
        try {
            UUID userId = UUID.fromString(context.getUserId());
            Optional<BehaviorInsights> insights = storageAdapter.findByUserId(userId);
            
            if (insights.isEmpty()) {
                log.debug("No behavior insights found for user: {}", userId);
                return Optional.empty();
            }
            
            BehaviorInsights insight = insights.get();
            
            // Check freshness
            if (isStale(insight)) {
                log.info("Behavior insights stale for user: {} (age: {})", 
                    userId, Duration.between(insight.getUpdatedAt(), LocalDateTime.now()));
                // Could trigger async re-analysis here
                return Optional.empty();
            }
            
            // Convert to DTO
            BehaviorContext behaviorContext = toBehaviorContext(insight);
            log.debug("Behavior context provided for user: {} (segment: {}, sentiment: {})", 
                userId, behaviorContext.getSegment(), behaviorContext.getSentimentLabel());
            
            return Optional.of(behaviorContext);
            
        } catch (IllegalArgumentException e) {
            log.warn("Invalid userId format: {}", context.getUserId());
            return Optional.empty();
        } catch (Exception e) {
            log.error("Error fetching behavior context for user: {}", context.getUserId(), e);
            return Optional.empty();
        }
    }
    
    private boolean isStale(BehaviorInsights insights) {
        LocalDateTime analyzedAt = insights.getUpdatedAt();
        if (analyzedAt == null) {
            return true;
        }
        Duration age = Duration.between(analyzedAt, LocalDateTime.now());
        return age.compareTo(MAX_INSIGHT_AGE) > 0;
    }
    
    private BehaviorContext toBehaviorContext(BehaviorInsights insights) {
        return BehaviorContext.builder()
            .userId(insights.getUserId().toString())
            .segment(insights.getSegment())
            .sentimentLabel(insights.getSentimentLabel() != null 
                ? insights.getSentimentLabel().name() 
                : null)
            .sentimentScore(insights.getSentimentScore())
            .churnRisk(insights.getChurnRisk())
            .churnReason(insights.getChurnReason())
            .trend(insights.getTrend() != null 
                ? insights.getTrend().name() 
                : null)
            .patterns(insights.getPatterns())
            .recommendations(insights.getRecommendations())
            .confidence(insights.getConfidence())
            .analyzedAt(insights.getUpdatedAt())
            .insights(insights.getInsights())
            .build();
    }
}
```

### 5.5 Update EnrichedPromptBuilder to Use Behavior Context

**Location:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/intent/EnrichedPromptBuilder.java`

```java
public String buildSystemPrompt(SystemContext context) {
    StringBuilder prompt = new StringBuilder(1024);
    prompt.append("You are the intent extraction engine powering our Retrieval-Augmented Generation (RAG) assistant.\n");
    prompt.append("Analyse the user message and respond with a JSON payload that follows the schema provided below.\n");
    prompt.append("Use one call to capture intent, generation need, and optimized query (no extra services).\n\n");
    
    // NEW: Include behavior context if available
    if (context.hasBehaviorContext()) {
        prompt.append("## USER BEHAVIOR CONTEXT\n");
        prompt.append(context.getBehaviorContext().toPromptString());
        prompt.append("\n");
        prompt.append("Use this context to tailor your response tone and recommendations.\n");
        prompt.append("For frustrated users, be empathetic. For at-risk users, be proactive.\n\n");
    }
    
    appendAvailableActions(prompt, context);
    appendKnowledgeBaseSummary(prompt, context);
    appendExtractionRules(prompt);
    appendNextStepGuidance(prompt);
    appendOutputFormat(prompt);
    
    return prompt.toString();
}
```

### 5.6 Architecture Diagram

```
┌─────────────────────────────────────────────┐
│   ai-infrastructure-core                    │
│                                             │
│   ┌─────────────────────────────────────┐  │
│   │ RAGOrchestrator                     │  │
│   │   ↓                                 │  │
│   │ SystemContextBuilder                │  │
│   │   ↓                                 │  │
│   │ Optional<BehaviorContextProvider> ◄─┼──┼── SPI Interface
│   └─────────────────────────────────────┘  │
│                                             │
│   ┌─────────────────────────────────────┐  │
│   │ SPI Package                         │  │
│   │ - BehaviorContextProvider (interface)│ │
│   │ - BehaviorContext (DTO)             │  │
│   └─────────────────────────────────────┘  │
└─────────────────────────────────────────────┘
                    ▲
                    │ implements (no circular dependency!)
                    │
┌─────────────────────────────────────────────┐
│   ai-infrastructure-behavior                │
│                                             │
│   ┌─────────────────────────────────────┐  │
│   │ BehaviorContextProviderImpl         │  │
│   │ (implements BehaviorContextProvider)│  │
│   │   ↓                                 │  │
│   │ BehaviorStorageAdapter              │  │
│   │   ↓                                 │  │
│   │ BehaviorInsights (Entity)           │  │
│   └─────────────────────────────────────┘  │
└─────────────────────────────────────────────┘
```

**Key Points:**
- ✅ No circular dependency
- ✅ Core works without behavior module
- ✅ Behavior module plugs in via SPI
- ✅ Loose coupling via interface
- ✅ Easy to test with mocks

---

## 📚 Phase 6: Documentation Updates

### 6.1 Update README Examples

Add examples showing:
- Authenticated user context
- Anonymous session context
- Full context with metadata
- Factory method usage

### 6.2 Create Migration Guide

Document how existing consumers should migrate from old signature to new.

### 6.3 Update Integration Guide

Update `BEHAVIOR_ORCHESTRATOR_INTEGRATION_GUIDE.md` to reflect context-based approach.

---

## 🚦 Implementation Checklist

### Phase 1: Core Components ✓
- [ ] Create `OrchestrationContext` class with all fields
- [ ] Add validation logic
- [ ] Add factory methods (`forUser`, `forSession`, `anonymous`, `forTest`)
- [ ] Write unit tests for `OrchestrationContext`

### Phase 2: RAGOrchestrator Updates
- [ ] Add new `orchestrate(String query, OrchestrationContext context)` method
- [ ] Update internal logic to use context
- [ ] Deprecate old `orchestrate(String query, String userId)` method
- [ ] Update `handleAction`, `handleInformation` to use context
- [ ] Add metadata builders for security/access control

### Phase 3: Supporting Components
- [ ] Update `IntentQueryExtractor.extract()` to accept context
- [ ] Update `SystemContextBuilder.buildContext()` to accept context
- [ ] Update `SystemContext` with new fields (sessionId, authenticated, locale, metadata)
- [ ] Deprecate old methods

### Phase 4: Testing
- [ ] Update all unit tests in `RAGOrchestratorTest`
- [ ] Update all integration tests
- [ ] Add tests for anonymous users
- [ ] Add tests for rich context scenarios
- [ ] Verify deprecated methods still work

### Phase 5: Documentation
- [ ] Update README with new examples
- [ ] Create migration guide
- [ ] Update behavior integration guide
- [ ] Add JavaDoc to all new methods
- [ ] Document factory patterns

### Phase 5: Behavior Integration (SPI Pattern)
- [ ] Create `BehaviorContextProvider` interface in core module (SPI)
- [ ] Create `BehaviorContext` DTO in core module
- [ ] Update `SystemContextBuilder` to use `Optional<BehaviorContextProvider>`
- [ ] Add `behaviorContext` field to `SystemContext`
- [ ] Create `BehaviorContextProviderImpl` in behavior module
- [ ] Update `EnrichedPromptBuilder` to include behavior context in prompts
- [ ] Add tests for behavior integration
- [ ] Verify no circular dependency exists

### Phase 6: Documentation
- [ ] Document SPI pattern and architecture
- [ ] Add behavior integration examples
- [ ] Explain optional nature of behavior module

---

## 🎯 Usage Examples

### Example 1: Simple Authenticated User
```java
OrchestrationContext context = OrchestrationContext.forUser("user-123");
OrchestrationResult result = orchestrator.orchestrate("Find products", context);
```

### Example 2: Anonymous Session
```java
OrchestrationContext context = OrchestrationContext.forSession(httpSession.getId());
OrchestrationResult result = orchestrator.orchestrate("Browse catalog", context);
```

### Example 3: Rich Context (E-commerce)
```java
OrchestrationContext context = OrchestrationContext.builder()
    .sessionId(request.getSession().getId())
    .ipAddress(request.getRemoteAddr())
    .userAgent(request.getHeader("User-Agent"))
    .locale(request.getLocale())
    .metadata(Map.of(
        "device", "mobile",
        "referrer", "google"
    ))
    .build();

OrchestrationResult result = orchestrator.orchestrate("wireless headphones under $100", context);
```

### Example 4: Authenticated SaaS with Metadata
```java
User user = getCurrentUser();

OrchestrationContext context = OrchestrationContext.builder()
    .userId(user.getId())
    .sessionId(request.getSession().getId())
    .locale(user.getPreferredLocale())
    .metadata(Map.of(
        "subscriptionTier", user.getTier(),
        "accountAge", user.getAccountAgeDays(),
        "features", user.getEnabledFeatures()
    ))
    .build();

OrchestrationResult result = orchestrator.orchestrate("analyze my data", context);
```

### Example 5: Testing
```java
@Test
void testOrchestration() {
    OrchestrationContext context = OrchestrationContext.forTest();
    OrchestrationResult result = orchestrator.orchestrate("test query", context);
    assertThat(result).isNotNull();
}
```

### Example 6: Testing with Mock Behavior Provider
```java
@Test
void testOrchestrationWithBehaviorContext() {
    // Mock behavior provider
    BehaviorContextProvider mockProvider = mock(BehaviorContextProvider.class);
    BehaviorContext behaviorContext = BehaviorContext.builder()
        .userId("test-user")
        .segment("Premium")
        .sentimentLabel("SATISFIED")
        .sentimentScore(0.8)
        .build();
    
    when(mockProvider.getBehaviorContext(any()))
        .thenReturn(Optional.of(behaviorContext));
    
    // Inject into SystemContextBuilder
    SystemContextBuilder builder = new SystemContextBuilder(
        actionsRegistry,
        knowledgeBaseService,
        ObjectProvider.of(mockProvider),  // Provide mock
        ObjectProvider.of(Clock.systemUTC())
    );
    
    // Build context
    OrchestrationContext orchContext = OrchestrationContext.forUser("test-user");
    SystemContext systemContext = builder.buildContext(orchContext);
    
    // Verify behavior context is present
    assertThat(systemContext.hasBehaviorContext()).isTrue();
    assertThat(systemContext.getBehaviorContext().getSegment()).isEqualTo("Premium");
}

@Test
void testOrchestrationWithoutBehaviorModule() {
    // Create builder with no behavior provider
    SystemContextBuilder builder = new SystemContextBuilder(
        actionsRegistry,
        knowledgeBaseService,
        ObjectProvider.empty(),  // No provider
        ObjectProvider.of(Clock.systemUTC())
    );
    
    OrchestrationContext orchContext = OrchestrationContext.forUser("test-user");
    SystemContext systemContext = builder.buildContext(orchContext);
    
    // Should work fine without behavior context
    assertThat(systemContext.hasBehaviorContext()).isFalse();
    assertThat(systemContext.getUserId()).isEqualTo("test-user");
}
```

---

## 🔌 SPI Pattern: Avoiding Circular Dependencies

### The Problem
Without SPI pattern:
```
ai-infrastructure-core → ai-infrastructure-behavior (to get insights)
         ↑                           ↓
         └───────────────────────────┘
              CIRCULAR DEPENDENCY! ❌
```

### The Solution
With SPI pattern:
```
ai-infrastructure-core (defines interface)
         ↑
         │ implements
         │
ai-infrastructure-behavior (implements interface)
         ↓
ai-infrastructure-core (depends on)

NO CIRCULAR DEPENDENCY! ✅
```

### How It Works

1. **Core defines the contract** (interface + DTO):
   - `BehaviorContextProvider` (interface)
   - `BehaviorContext` (DTO with no behavior module dependencies)

2. **Behavior implements the contract**:
   - `BehaviorContextProviderImpl implements BehaviorContextProvider`
   - Converts `BehaviorInsights` entity to `BehaviorContext` DTO

3. **Core uses Optional injection**:
   - `Optional<BehaviorContextProvider> behaviorContextProvider`
   - Works if behavior module present (Optional has value)
   - Works if behavior module absent (Optional is empty)

4. **Spring autowires automatically**:
   - If behavior module on classpath → implementation found
   - If behavior module absent → Optional stays empty

### Benefits

✅ **No Circular Dependency** - Core doesn't compile-depend on behavior  
✅ **Optional Behavior Module** - Core works standalone  
✅ **Loose Coupling** - Core knows interface, not implementation  
✅ **Plugin Architecture** - Easy to swap implementations  
✅ **Testable** - Can mock the provider  
✅ **Framework-Friendly** - Users can implement custom providers  

### Verification Steps

```bash
# Verify core module has no dependency on behavior module
cd ai-infrastructure-core
mvn dependency:tree | grep behavior
# Should return NOTHING

# Verify behavior module depends on core (one-way only)
cd ../ai-infrastructure-behavior
mvn dependency:tree | grep core
# Should show dependency on core (OK - one direction)
```

---

## ⚠️ Breaking Changes & Migration

### Deprecated (Still Works)
```java
// This will work but log deprecation warning
orchestrator.orchestrate("query", "user-123");
```

### New Recommended Approach
```java
// Migrate to this
orchestrator.orchestrate("query", OrchestrationContext.forUser("user-123"));
```

### Migration Timeline
- **v2.0** - Introduce new signature, deprecate old
- **v2.1** - Warn consumers to migrate
- **v3.0** - Remove deprecated methods (breaking)

---

## 🔍 Testing Strategy

### Unit Tests
- Context validation
- Factory methods
- Authenticated vs anonymous logic
- Metadata propagation

### Integration Tests
- End-to-end with authenticated user
- End-to-end with anonymous session
- Security checks with context
- Access control with context
- RAG with context metadata

### Backward Compatibility Tests
- Verify deprecated methods still work
- Verify old tests still pass

### SPI Integration Tests
- Core works without behavior module (Optional is empty)
- Core works with behavior module (Optional has implementation)
- Behavior context correctly converted from entity to DTO
- Stale insights are filtered out
- Errors in provider don't break orchestration
- Custom implementations can be plugged in

---

## 📈 Success Criteria

✅ All existing tests pass (backward compatibility)  
✅ New context-based tests pass  
✅ Anonymous user flows work  
✅ Authenticated user flows work  
✅ Metadata properly propagated through pipeline  
✅ **No circular dependency** (verified via `mvn dependency:tree`)  
✅ Core module works without behavior module present  
✅ Behavior context enriches orchestration when behavior module present  
✅ SPI pattern correctly implemented  
✅ Documentation complete and clear  
✅ Zero regression in functionality  
✅ Ready for behavior insights integration  

---

## 🚀 Next Steps After Implementation

1. **Announce Change** - Update changelog, notify consumers
2. **Monitor Usage** - Track adoption of new signature
3. **Gather Feedback** - Adjust based on real-world usage
4. **Plan Behavior Integration** - Phase 5/6 implementation
5. **Consider Removal Timeline** - Plan deprecation cycle for old signature

---

## 🎨 Custom Behavior Provider (For Framework Users)

Framework consumers can implement their own behavior providers without using the built-in behavior module:

```java
@Component
public class CustomBehaviorProvider implements BehaviorContextProvider {
    
    @Autowired
    private MyCustomAnalyticsService analytics;
    
    @Override
    public Optional<BehaviorContext> getBehaviorContext(OrchestrationContext context) {
        if (!context.isAuthenticated()) {
            return Optional.empty();
        }
        
        // Fetch from your own analytics system
        MyUserProfile profile = analytics.getUserProfile(context.getUserId());
        
        // Convert to framework's BehaviorContext
        return Optional.of(BehaviorContext.builder()
            .userId(context.getUserId())
            .segment(profile.getSegment())
            .sentimentLabel(profile.getMood())
            .sentimentScore(profile.getSatisfactionScore())
            .patterns(profile.getBehaviorTags())
            .build());
    }
}
```

This allows framework users to:
- ✅ Integrate their own analytics systems
- ✅ Use custom behavior tracking
- ✅ Not depend on built-in behavior module
- ✅ Still get behavior-aware orchestration

---

## 📞 Questions & Clarifications

- ❓ Should we require sessionId for all anonymous requests?
- ❓ What's the timeline for removing deprecated methods?
- ❓ Should we add rate limiting based on session/IP?
- ❓ How should behavior insights work for anonymous users?
- ✅ **RESOLVED:** Circular dependency avoided via SPI pattern

---

## 🎯 Key Architectural Decisions Summary

### 1. Context Object Pattern
- **Decision:** `orchestrate(query, context)` signature
- **Rationale:** Clear separation of "what" (query) and "who/where" (context)
- **Benefit:** Extensible without breaking API

### 2. Optional UserId + Required Identifier
- **Decision:** UserId optional, but either userId OR sessionId required
- **Rationale:** Support both authenticated and anonymous users
- **Benefit:** Framework works for e-commerce, docs, SaaS

### 3. SPI Pattern for Behavior Integration
- **Decision:** Core defines interface, behavior implements it
- **Rationale:** Avoid circular dependency between core ↔ behavior
- **Benefit:** Loose coupling, optional behavior module, plugin architecture

### 4. Factory Methods Over Overloads
- **Decision:** Single orchestrate method + context factories
- **Rationale:** One clear API pattern, no confusion
- **Benefit:** Consistent usage, encourages rich context

### 5. Graceful Degradation
- **Decision:** Core works without behavior module
- **Rationale:** Not all users need behavior insights
- **Benefit:** Minimal setup, progressive enhancement

---

## 📐 Dependency Graph (Final)

```
┌─────────────────────────────┐
│  ai-infrastructure-core     │
│  (defines SPI interfaces)   │
└─────────────┬───────────────┘
              │
              │ implements (one-way)
              ▼
┌─────────────────────────────┐
│  ai-infrastructure-behavior │
│  (implements SPI)           │
└─────────────────────────────┘

✅ No circular dependency
✅ Core is independent
✅ Behavior plugs in optionally
```

---

**Document Version:** 1.1  
**Created:** 2025-12-30  
**Updated:** 2025-12-30 (Added SPI pattern for behavior integration)  
**Status:** Ready for Implementation  
**Owner:** AI Infrastructure Team  

**Key Change:** Added SPI pattern (Phase 5) to avoid circular dependency between core and behavior modules.


