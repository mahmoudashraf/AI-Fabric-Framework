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

## 🔗 Phase 5: Behavior Insights Integration (Future)

### 5.1 Update SystemContextBuilder for Behavior Insights

```java
@Service
public class SystemContextBuilder {
    
    private final AvailableActionsRegistry availableActionsRegistry;
    private final KnowledgeBaseOverviewService knowledgeBaseOverviewService;
    private final Optional<BehaviorInsightsService> behaviorInsightsService;  // NEW
    private final Clock clock;
    
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
        
        // NEW: Add behavior insights if available
        if (behaviorInsightsService.isPresent()) {
            if (orchestrationContext.isAuthenticated()) {
                // Fetch user behavior insights
                behaviorInsightsService.get()
                    .getInsights(orchestrationContext.getUserId())
                    .ifPresent(builder::behaviorInsights);
            } else if (orchestrationContext.getSessionId() != null) {
                // Fetch session behavior insights
                behaviorInsightsService.get()
                    .getSessionInsights(orchestrationContext.getSessionId())
                    .ifPresent(builder::sessionBehavior);
            }
        }
        
        return builder.build();
    }
}
```

### 5.2 Update EnrichedPromptBuilder

Update to include behavior context in LLM prompts when available.

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

### Phase 6: Future - Behavior Integration
- [ ] Add `BehaviorInsightsService` dependency to `SystemContextBuilder`
- [ ] Fetch insights for authenticated users
- [ ] Fetch session behavior for anonymous users
- [ ] Update `EnrichedPromptBuilder` to include behavior context
- [ ] Add behavior fields to `SystemContext`

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

---

## 📈 Success Criteria

✅ All existing tests pass (backward compatibility)
✅ New context-based tests pass
✅ Anonymous user flows work
✅ Authenticated user flows work
✅ Metadata properly propagated through pipeline
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

## 📞 Questions & Clarifications

- ❓ Should we require sessionId for all anonymous requests?
- ❓ What's the timeline for removing deprecated methods?
- ❓ Should we add rate limiting based on session/IP?
- ❓ How should behavior insights work for anonymous users?

---

**Document Version:** 1.0  
**Created:** 2025-12-29  
**Status:** Ready for Implementation  
**Owner:** AI Infrastructure Team

