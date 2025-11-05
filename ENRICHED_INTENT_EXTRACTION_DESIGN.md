# Enriched Intent Extraction Design

## Overview

Instead of sending just a raw query to the LLM, we can **enrich the prompt with system context** using existing AI infrastructure:

1. **Available Entity Types** - What can be searched/indexed
2. **Knowledge Base Snapshot** - What documents/content spaces exist
3. **System Actions** - What functions can be executed
4. **User Behavior Context** - Recent user activity and patterns
5. **Index Statistics** - What's indexed, how much content
6. **Historical Patterns** - Similar past queries

This makes the LLM **data-aware** and **system-aware**, leading to better intent extraction.

---

## Architecture Overview

```
Raw User Query
    ↓
IntentQueryExtractor
    ├→ SystemContextBuilder (gathers enrichment data)
    │   ├→ AIEntityConfigurationLoader (entity types)
    │   ├→ AISearchableEntityRepository (indexed content stats)
    │   ├→ VectorDatabaseService (index metadata)
    │   ├→ BehaviorRepository (user patterns)
    │   └→ AICapabilityService (available actions)
    │
    ├→ PromptBuilder (constructs enriched prompt)
    │   └→ SystemContext → Prompt with examples
    │
    ├→ AICoreService (generateText with context)
    │
    └→ MultiIntentResponse
        ├→ Structured intents
        ├→ Correct vector spaces
        ├→ Available actions
        └→ Function parameters
```

---

## 1. System Context Builder Service

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class SystemContextBuilder {
    
    private final AIEntityConfigurationLoader configurationLoader;
    private final AISearchableEntityRepository searchableEntityRepository;
    private final VectorDatabaseService vectorDatabaseService;
    private final BehaviorRepository behaviorRepository;
    private final AICapabilityService aiCapabilityService;
    private final Cache cache;  // Cache context for 1 hour
    
    /**
     * Build rich system context for intent extraction
     */
    public SystemContext buildContext(String userId) {
        // Check cache first (system context doesn't change often)
        String cacheKey = "system_context_" + userId;
        SystemContext cached = cache.get(cacheKey, SystemContext.class);
        if (cached != null) {
            return cached;
        }
        
        SystemContext context = SystemContext.builder()
            .entityTypesSchema(buildEntityTypesSchema())
            .knowledgeBaseOverview(buildKnowledgeBaseOverview())
            .availableActions(buildAvailableActions())
            .userBehaviorContext(buildUserBehaviorContext(userId))
            .indexStatistics(buildIndexStatistics())
            .similarPastQueries(buildSimilarPastQueries(userId))
            .systemCapabilities(buildSystemCapabilities())
            .build();
        
        // Cache for 1 hour
        cache.put(cacheKey, context, Duration.ofHours(1));
        
        return context;
    }
    
    /**
     * What entity types can be searched/retrieved?
     * Maps to vector spaces
     */
    private EntityTypesSchema buildEntityTypesSchema() {
        Set<String> supportedTypes = configurationLoader.getSupportedEntityTypes();
        
        return EntityTypesSchema.builder()
            .entityTypes(supportedTypes.stream()
                .map(type -> {
                    AIEntityConfig config = configurationLoader.getEntityConfig(type);
                    return EntityTypeInfo.builder()
                        .type(type)
                        .description(type + " related documents and information")
                        .searchable(config != null && config.isEnableSearch())
                        .embeddable(config != null && config.isAutoEmbedding())
                        .searchableFields(config != null ? 
                            config.getSearchableFields().stream()
                                .map(AISearchableField::getFieldName)
                                .collect(Collectors.toList()) 
                            : List.of())
                        .build();
                })
                .collect(Collectors.toList()))
            .vectorSpaces(List.of(
                new VectorSpaceInfo("policies", "Company policies, terms, conditions"),
                new VectorSpaceInfo("products", "Product catalog, specifications, features"),
                new VectorSpaceInfo("support", "Support documentation, troubleshooting guides"),
                new VectorSpaceInfo("faq", "Frequently asked questions"),
                new VectorSpaceInfo("general", "General information across all spaces")
            ))
            .build();
    }
    
    /**
     * What content is indexed? Statistics overview
     */
    private KnowledgeBaseOverview buildKnowledgeBaseOverview() {
        // Get statistics from searchable entity repository
        Map<String, Long> contentCountByType = new HashMap<>();
        
        for (String entityType : configurationLoader.getSupportedEntityTypes()) {
            long count = searchableEntityRepository.countByEntityType(entityType);
            contentCountByType.put(entityType, count);
        }
        
        long totalIndexed = contentCountByType.values().stream()
            .mapToLong(Long::longValue)
            .sum();
        
        return KnowledgeBaseOverview.builder()
            .totalIndexedDocuments(totalIndexed)
            .documentsByType(contentCountByType)
            .lastIndexUpdateTime(findLastIndexUpdate())
            .indexHealth("healthy")  // Can be monitored
            .coverage(calculateCoverage(contentCountByType))  // Coverage metrics
            .build();
    }
    
    /**
     * What functions/actions can the system execute?
     */
    private List<ActionInfo> buildAvailableActions() {
        return List.of(
            // E-commerce actions
            ActionInfo.builder()
                .action("cancel_subscription")
                .description("Cancel user's subscription")
                .requiredParams(List.of("subscriptionId", "reason"))
                .confirmationRequired(true)
                .examples(List.of("Cancel my subscription", "I want to stop my membership"))
                .build(),
            
            ActionInfo.builder()
                .action("update_shipping_address")
                .description("Update user's shipping address")
                .requiredParams(List.of("newAddress", "city", "state", "zip"))
                .confirmationRequired(true)
                .examples(List.of("Change my shipping address", "Update my delivery address"))
                .build(),
            
            ActionInfo.builder()
                .action("request_refund")
                .description("Submit refund request")
                .requiredParams(List.of("orderId", "reason"))
                .confirmationRequired(true)
                .examples(List.of("Can I get a refund?", "Return my order"))
                .build(),
            
            ActionInfo.builder()
                .action("update_payment_method")
                .description("Update payment method")
                .requiredParams(List.of("paymentType"))
                .confirmationRequired(true)
                .examples(List.of("Change my payment method", "Update my card"))
                .build()
        );
    }
    
    /**
     * What is this specific user's behavior pattern?
     */
    private UserBehaviorContext buildUserBehaviorContext(String userId) {
        if (userId == null) {
            return UserBehaviorContext.builder()
                .userId(null)
                .isKnownUser(false)
                .build();
        }
        
        // Get user's recent behaviors
        List<Behavior> recentBehaviors = behaviorRepository
            .findByUserIdOrderByCreatedAtDesc(UUID.fromString(userId))
            .stream()
            .limit(20)
            .collect(Collectors.toList());
        
        if (recentBehaviors.isEmpty()) {
            return UserBehaviorContext.builder()
                .userId(userId)
                .isKnownUser(false)
                .build();
        }
        
        // Extract patterns
        Map<String, Long> behaviorCounts = recentBehaviors.stream()
            .collect(Collectors.groupingBy(
                b -> b.getBehaviorType().toString(),
                Collectors.counting()
            ));
        
        String mostCommonBehavior = behaviorCounts.entrySet().stream()
            .max(Comparator.comparing(Map.Entry::getValue))
            .map(Map.Entry::getKey)
            .orElse("unknown");
        
        // Get user's search history
        List<String> recentSearches = recentBehaviors.stream()
            .filter(b -> "SEARCH_QUERY".equals(b.getBehaviorType().toString()))
            .map(Behavior::getContext)
            .limit(5)
            .collect(Collectors.toList());
        
        return UserBehaviorContext.builder()
            .userId(userId)
            .isKnownUser(true)
            .recentBehaviorCount(recentBehaviors.size())
            .behaviorPatterns(behaviorCounts)
            .mostCommonBehavior(mostCommonBehavior)
            .recentSearchQueries(recentSearches)
            .hasViewedProducts(behaviorCounts.containsKey("PRODUCT_VIEW"))
            .hasMadePurchases(behaviorCounts.containsKey("PURCHASE"))
            .hasSubmittedSupport(behaviorCounts.containsKey("CUSTOMER_SUPPORT"))
            .build();
    }
    
    /**
     * Index statistics - how much content in each space?
     */
    private IndexStatistics buildIndexStatistics() {
        // Get stats from vector database
        long totalVectors = searchableEntityRepository.count();
        
        Map<String, Long> vectorCountByType = new HashMap<>();
        for (String entityType : configurationLoader.getSupportedEntityTypes()) {
            long count = searchableEntityRepository.countByEntityType(entityType);
            vectorCountByType.put(entityType, count);
        }
        
        return IndexStatistics.builder()
            .totalIndexedVectors(totalVectors)
            .vectorsByEntityType(vectorCountByType)
            .averageContentLength(estimateAverageContentLength())
            .lastUpdated(LocalDateTime.now())
            .health("operational")
            .build();
    }
    
    /**
     * Similar past queries (for pattern matching)
     */
    private List<PastQueryPattern> buildSimilarPastQueries(String userId) {
        // This could search through:
        // - User's own recent queries (from behavior logs)
        // - Popular queries across system
        // - Queries that had good/bad outcomes
        
        return List.of();  // Placeholder - would be populated from logs
    }
    
    /**
     * System capabilities summary
     */
    private SystemCapabilities buildSystemCapabilities() {
        return SystemCapabilities.builder()
            .supportsHybridSearch(true)
            .supportsContextualSearch(true)
            .supportsCompoundQueries(true)
            .supportsActionExecution(true)
            .supportsFunctionCalling(true)
            .maxContextLength(4000)
            .maxDocumentsPerQuery(10)
            .averageQueryResponseTimeMs(300)
            .build();
    }
    
    private LocalDateTime findLastIndexUpdate() {
        return searchableEntityRepository.findMaxUpdatedAt()
            .orElse(LocalDateTime.now());
    }
    
    private Map<String, Double> calculateCoverage(Map<String, Long> contentCount) {
        // Calculate coverage metrics per entity type
        return contentCount.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> Math.min(1.0, e.getValue() / 1000.0)  // Normalized to 0-1
            ));
    }
    
    private Double estimateAverageContentLength() {
        return searchableEntityRepository.findAverageContentLength()
            .orElse(500.0);
    }
}
```

---

## 2. System Context DTOs

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemContext {
    
    /**
     * What entity types/vector spaces are available?
     */
    private EntityTypesSchema entityTypesSchema;
    
    /**
     * What content is indexed?
     */
    private KnowledgeBaseOverview knowledgeBaseOverview;
    
    /**
     * What actions can be executed?
     */
    private List<ActionInfo> availableActions;
    
    /**
     * User's behavior patterns and history
     */
    private UserBehaviorContext userBehaviorContext;
    
    /**
     * Statistics about the index
     */
    private IndexStatistics indexStatistics;
    
    /**
     * Similar past queries (for pattern learning)
     */
    private List<PastQueryPattern> similarPastQueries;
    
    /**
     * System capabilities
     */
    private SystemCapabilities systemCapabilities;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntityTypesSchema {
    
    /**
     * Available entity types for retrieval
     */
    private List<EntityTypeInfo> entityTypes;
    
    /**
     * Named vector spaces (logical grouping of entity types)
     */
    private List<VectorSpaceInfo> vectorSpaces;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntityTypeInfo {
    private String type;
    private String description;
    private Boolean searchable;
    private Boolean embeddable;
    private List<String> searchableFields;
}

@Data
@AllArgsConstructor
@NoArgsConstructor
public class VectorSpaceInfo {
    private String name;
    private String description;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeBaseOverview {
    private Long totalIndexedDocuments;
    private Map<String, Long> documentsByType;
    private LocalDateTime lastIndexUpdateTime;
    private String indexHealth;
    private Map<String, Double> coverage;  // Coverage per type
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionInfo {
    private String action;
    private String description;
    private List<String> requiredParams;
    private Boolean confirmationRequired;
    private List<String> examples;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBehaviorContext {
    private String userId;
    private Boolean isKnownUser;
    private Integer recentBehaviorCount;
    private Map<String, Long> behaviorPatterns;
    private String mostCommonBehavior;
    private List<String> recentSearchQueries;
    private Boolean hasViewedProducts;
    private Boolean hasMadePurchases;
    private Boolean hasSubmittedSupport;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndexStatistics {
    private Long totalIndexedVectors;
    private Map<String, Long> vectorsByEntityType;
    private Double averageContentLength;
    private LocalDateTime lastUpdated;
    private String health;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemCapabilities {
    private Boolean supportsHybridSearch;
    private Boolean supportsContextualSearch;
    private Boolean supportsCompoundQueries;
    private Boolean supportsActionExecution;
    private Boolean supportsFunctionCalling;
    private Integer maxContextLength;
    private Integer maxDocumentsPerQuery;
    private Long averageQueryResponseTimeMs;
}

@Data
public class PastQueryPattern {
    private String query;
    private String outcome;
    private IntentType intent;
}
```

---

## 3. Enriched Prompt Builder

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class EnrichedPromptBuilder {
    
    private final SystemContextBuilder systemContextBuilder;
    
    /**
     * Build enriched system prompt with system context
     */
    public String buildSystemPrompt(String userId) {
        SystemContext context = systemContextBuilder.buildContext(userId);
        
        return String.format("""
            You are an expert query intent extractor with deep knowledge of this system.
            
            ## SYSTEM OVERVIEW
            
            ### Available Vector Spaces (Knowledge Bases)
            %s
            
            ### Indexed Content
            Total Documents: %d
            By Type: %s
            Index Health: %s
            
            ### Available Actions (Functions)
            %s
            
            ### User Context
            %s
            
            ### System Capabilities
            - Supports hybrid search: %s
            - Supports compound queries: %s
            - Supports action execution: %s
            - Max documents per query: %d
            
            ## YOUR TASK
            
            Extract structured intent from user queries. Respond ONLY with valid JSON (no markdown).
            
            ## JSON Schema
            {
              "rawQuery": "original user query",
              "primaryIntent": "dominant intent type",
              "intents": [
                {
                  "type": "INFORMATION|ACTION|OUT_OF_SCOPE|COMPOUND",
                  "intent": "specific intent name",
                  "confidence": 0.0-1.0,
                  "entities": ["entity1"],
                  "normalizedQuery": "cleaned query for retrieval",
                  "vectorSpace": "which space to search",
                  "action": "if ACTION type, which action",
                  "actionParams": {optional parameters},
                  "requiresRetrieval": true|false
                }
              ],
              "isCompound": true|false,
              "compoundParts": ["part1", "part2"],
              "orchestrationStrategy": "sequential|parallel|merged"
            }
            
            ## VECTOR SPACES
            %s
            
            ## AVAILABLE ACTIONS
            %s
            
            ## DISAMBIGUATION RULES
            1. Map user terminology to standard terms (see examples)
            2. If user mentions action from available actions list → type=ACTION
            3. If query matches entity type in knowledge base → route to correct vectorSpace
            4. If multiple questions → isCompound=true, split into parts
            5. If query is math/general knowledge not in KB → type=OUT_OF_SCOPE
            
            ## EXAMPLES
            
            ### Example 1: Action Intent
            User: "Cancel my subscription"
            Response: {
              "rawQuery": "Cancel my subscription",
              "primaryIntent": "cancel_subscription",
              "intents": [{
                "type": "ACTION",
                "intent": "cancel_subscription",
                "confidence": 0.99,
                "action": "cancel_subscription",
                "actionParams": {"reason": null, "confirmationRequired": true},
                "requiresRetrieval": false
              }],
              "isCompound": false
            }
            
            ### Example 2: Information Intent - Misspelled/Informal
            User: "can i get money back if thing broken???"
            Response: {
              "rawQuery": "can i get money back if thing broken???",
              "primaryIntent": "refund_eligibility",
              "intents": [{
                "type": "INFORMATION",
                "intent": "refund_eligibility_damaged",
                "confidence": 0.95,
                "entities": ["refund", "damaged_product"],
                "normalizedQuery": "Can I get a refund for a damaged product?",
                "vectorSpace": "policies"
              }]
            }
            
            ### Example 3: Compound Intent
            User: "What's your return policy and can I change my shipping?"
            Response: {
              "rawQuery": "What's your return policy and can I change my shipping?",
              "primaryIntent": "compound_query",
              "intents": [
                {
                  "type": "INFORMATION",
                  "intent": "return_policy",
                  "confidence": 0.98,
                  "normalizedQuery": "What is your return policy?",
                  "vectorSpace": "policies"
                },
                {
                  "type": "ACTION",
                  "intent": "update_shipping_address",
                  "confidence": 0.92,
                  "action": "update_shipping_address",
                  "actionParams": {"newAddress": null}
                }
              ],
              "isCompound": true,
              "compoundParts": ["return_policy", "shipping_address"],
              "orchestrationStrategy": "parallel"
            }
            """,
            // Format arguments
            formatVectorSpaceList(context.getEntityTypesSchema()),
            context.getKnowledgeBaseOverview().getTotalIndexedDocuments(),
            context.getKnowledgeBaseOverview().getDocumentsByType(),
            context.getKnowledgeBaseOverview().getIndexHealth(),
            formatActionList(context.getAvailableActions()),
            formatUserContext(context.getUserBehaviorContext()),
            context.getSystemCapabilities().getSupportsHybridSearch(),
            context.getSystemCapabilities().getSupportsCompoundQueries(),
            context.getSystemCapabilities().getSupportsActionExecution(),
            context.getSystemCapabilities().getMaxDocumentsPerQuery(),
            formatDetailedVectorSpaces(context.getEntityTypesSchema()),
            formatDetailedActions(context.getAvailableActions())
        );
    }
    
    private String formatVectorSpaceList(EntityTypesSchema schema) {
        return schema.getVectorSpaces().stream()
            .map(v -> "- " + v.getName() + ": " + v.getDescription())
            .collect(Collectors.joining("\n"));
    }
    
    private String formatActionList(List<ActionInfo> actions) {
        return actions.stream()
            .map(a -> String.format("- %s: %s (requires: %s)",
                a.getAction(), a.getDescription(), String.join(", ", a.getRequiredParams())))
            .collect(Collectors.joining("\n"));
    }
    
    private String formatUserContext(UserBehaviorContext context) {
        if (!context.getIsKnownUser()) {
            return "Unknown user - no history available";
        }
        
        return String.format("""
            User is known with %d recent behaviors
            Most common action: %s
            Recent searches: %s
            Has made purchases: %s""",
            context.getRecentBehaviorCount(),
            context.getMostCommonBehavior(),
            String.join(", ", context.getRecentSearchQueries()),
            context.getHasMadePurchases()
        );
    }
    
    private String formatDetailedVectorSpaces(EntityTypesSchema schema) {
        return schema.getVectorSpaces().stream()
            .map(v -> v.getName() + ": " + v.getDescription())
            .collect(Collectors.joining("\n"));
    }
    
    private String formatDetailedActions(List<ActionInfo> actions) {
        return actions.stream()
            .map(a -> String.format("%s: %s\n  Examples: %s",
                a.getAction(), a.getDescription(),
                String.join(", ", a.getExamples())))
            .collect(Collectors.joining("\n\n"));
    }
}
```

---

## 4. Updated IntentQueryExtractor

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class IntentQueryExtractor {
    
    private final AICoreService aiCoreService;
    private final EnrichedPromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;
    
    /**
     * Extract intents WITH system context enrichment
     */
    public MultiIntentResponse extract(String rawQuery, String userId) {
        try {
            // STEP 1: Build enriched system prompt with context
            String systemPrompt = promptBuilder.buildSystemPrompt(userId);
            
            // STEP 2: Send to LLM with enriched context
            String userPrompt = String.format(
                "Extract structured intents from this query: \"%s\"",
                rawQuery
            );
            
            String jsonResponse = aiCoreService.generateText(systemPrompt, userPrompt);
            
            // STEP 3: Parse response
            MultiIntentResponse response = objectMapper.readValue(
                jsonResponse,
                MultiIntentResponse.class
            );
            
            log.info("Intent extraction for query '{}': primary intent '{}', {} intents extracted",
                rawQuery, response.getPrimaryIntent(), response.getIntents().size());
            
            return response;
            
        } catch (Exception e) {
            log.error("Intent extraction failed for query: {}", rawQuery, e);
            return buildFallbackResponse(rawQuery);
        }
    }
    
    private MultiIntentResponse buildFallbackResponse(String rawQuery) {
        return MultiIntentResponse.builder()
            .rawQuery(rawQuery)
            .primaryIntent("unknown")
            .intent(Intent.builder()
                .type(IntentType.INFORMATION)
                .intent("generic_query")
                .confidence(0.5)
                .normalizedQuery(rawQuery)
                .vectorSpace("general")
                .requiresRetrieval(true)
                .build())
            .isCompound(false)
            .requiresOrchestration(false)
            .build();
    }
}
```

---

## 5. Integration with RAGOrchestrator

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class RAGOrchestrator {
    
    private final IntentQueryExtractor intentExtractor;
    private final RAGService ragService;
    private final FunctionExecutionService functionService;
    
    /**
     * Main entry point with enriched intent extraction
     */
    public OrchestrationResult orchestrate(String rawQuery, String userId) {
        // STEP 1: Extract intents with SYSTEM CONTEXT
        MultiIntentResponse intents = intentExtractor.extract(rawQuery, userId);
        
        log.info("Orchestrating query: {}. Primary: {}. Compound: {}. Strategy: {}",
            rawQuery, intents.getPrimaryIntent(), intents.getIsCompound(),
            intents.getOrchestrationStrategy());
        
        // STEP 2: Route based on extracted intents
        if (!intents.getIsCompound()) {
            return handleSingleIntent(intents.getIntent(), userId);
        }
        
        return handleCompoundIntents(intents, userId);
    }
    
    // ... rest of orchestration logic
}
```

---

## Benefits of Enriched Intent Extraction

### 1. Better Intent Classification
- LLM knows available actions → correctly identifies ACTION vs INFORMATION
- LLM knows vector spaces → routes to correct knowledge base
- LLM knows user history → can personalize intent understanding

### 2. More Accurate Normalization
- LLM sees entity types in system → better terminology mapping
- LLM sees past queries → recognizes similar patterns
- LLM knows what's indexed → better query normalization

### 3. Smarter Compound Question Handling
- LLM knows available actions → can split "policy question + action" correctly
- LLM knows system capabilities → knows what can be parallelized
- LLM has full context → makes better orchestration decisions

### 4. Function Parameter Extraction
- LLM knows required params for each action → can extract from context
- LLM knows confirmation requirements → can set flags appropriately

### 5. System-Aware Responses
- LLM won't attempt out-of-scope queries (knows KB content)
- LLM won't suggest unavailable actions
- LLM respects system constraints (max docs, response time, etc.)

---

## Implementation Steps

### Phase 1: Context Building (Week 1)
1. Implement `SystemContextBuilder` service
2. Create DTOs: `SystemContext`, `EntityTypesSchema`, etc.
3. Wire in existing repositories and services
4. Add caching layer

### Phase 2: Prompt Enrichment (Week 1)
1. Implement `EnrichedPromptBuilder`
2. Build system prompt templates
3. Test with sample queries
4. Tune prompt for better results

### Phase 3: Integration (Week 2)
1. Update `IntentQueryExtractor` to use enriched prompt
2. Update `RAGOrchestrator` to pass userId
3. Test end-to-end flow
4. Monitor LLM response quality

### Phase 4: Optimization (Ongoing)
1. Monitor cache effectiveness
2. Tune context refresh intervals
3. Add more behavioral patterns
4. Improve prompt based on feedback

---

## Example Enriched Prompt Output

```
## SYSTEM OVERVIEW

### Available Vector Spaces (Knowledge Bases)
- policies: Company policies, terms, conditions
- products: Product catalog, specifications, features
- support: Support documentation, troubleshooting guides
- faq: Frequently asked questions

### Indexed Content
Total Documents: 2,543
By Type: {product: 1200, policy: 800, support: 543}
Index Health: operational

### Available Actions (Functions)
- cancel_subscription: Cancel user's subscription (requires: subscriptionId, reason)
- update_shipping_address: Update user's shipping address (requires: newAddress, city, state, zip)
- request_refund: Submit refund request (requires: orderId, reason)

### User Context
User is known with 24 recent behaviors
Most common action: PRODUCT_VIEW
Recent searches: return policy, shipping time, product reviews
Has made purchases: true

### System Capabilities
- Supports hybrid search: true
- Supports compound queries: true
- Supports action execution: true
- Max documents per query: 10
```

This is much richer than just the raw query!

---

## Backward Compatibility

The system remains backward compatible:
- If userId is null → system context is generic/empty
- If repos unavailable → graceful fallback to basic context
- If cache unavailable → fetch fresh context each time
- If LLM fails → fallback response generated

---

## Performance Considerations

### Caching Strategy
- System context cached for 1 hour (doesn't change often)
- Per-user cache keys (different contexts per user)
- Cache invalidation on entity type changes

### Query Building Cost
- One-time per request (not per intent)
- Reuse same context for all intents in compound query
- Prompt size acceptable (~4000 tokens with context)

### LLM Call Optimization
- Single LLM call extracts all intents
- Enriched context improves accuracy → fewer re-prompts
- Better structured responses → no parsing errors

---

## Conclusion

By enriching the intent extraction prompt with **system context**, we leverage the existing AI infrastructure to make the LLM:

1. ✅ **System-aware** (knows what actions/vector spaces exist)
2. ✅ **Data-aware** (knows what's indexed, statistics)
3. ✅ **User-aware** (knows user's behavior patterns)
4. ✅ **Capability-aware** (knows system constraints)
5. ✅ **Context-aware** (makes decisions with full picture)

This results in **better intent extraction, accurate routing, and fewer failures** in production.

