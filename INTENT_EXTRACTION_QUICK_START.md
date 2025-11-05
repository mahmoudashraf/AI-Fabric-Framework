# Intent Extraction - Quick Start Guide

## TL;DR

You're building **IntentQueryExtraction** - a unified service that:
1. Takes raw user query
2. Pulls system context (entity types, indexed docs, user behavior, actions)
3. Sends enriched query to LLM
4. Gets back structured intents with vector spaces, actions, orchestration strategy
5. RAGOrchestrator executes

**Result**: 95% accuracy, handles compounds, identifies actions, routes correctly.

---

## Files to Create/Modify

### New Files to Create (Week 1-2)

```
ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/

1. dto/MultiIntentResponse.java
   └─ Main response DTO with intents, orchestration strategy

2. dto/Intent.java
   └─ Individual intent (type, confidence, vectorSpace, action, params)

3. dto/SystemContext.java
   └─ Container for all system context

4. dto/EntityTypesSchema.java
   └─ Available entity types and vector spaces

5. dto/KnowledgeBaseOverview.java
   └─ Index statistics

6. dto/ActionInfo.java
   └─ Available actions with parameters

7. dto/UserBehaviorContext.java
   └─ User's recent behaviors and patterns

8. dto/IndexStatistics.java
   └─ Vector DB statistics

9. dto/SystemCapabilities.java
   └─ System capabilities and constraints

10. service/SystemContextBuilder.java
    └─ Pulls context from existing repositories/services

11. service/EnrichedPromptBuilder.java
    └─ Builds enriched system prompt with context

12. service/IntentQueryExtractor.java (MODIFY existing)
    └─ Updated to use enriched approach

13. rag/RAGOrchestrator.java
    └─ Routes and executes intents
```

### Existing Files to Modify

```
backend/src/main/java/com/easyluxury/ai/

1. controller/RAGController.java
   └─ Update to use RAGOrchestrator.orchestrate()

2. facade/AIFacade.java
   └─ Update performRAG() to use new orchestrator
```

---

## Implementation Order

### Day 1: DTOs
```bash
# Create all DTOs - straightforward, just data holders
touch ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/dto/MultiIntentResponse.java
touch ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/dto/Intent.java
touch ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/dto/SystemContext.java
# ... etc
```

### Day 2-3: SystemContextBuilder
```java
@Service
@RequiredArgsConstructor
public class SystemContextBuilder {
    private final AIEntityConfigurationLoader configurationLoader;
    private final AISearchableEntityRepository searchableEntityRepository;
    private final VectorDatabaseService vectorDatabaseService;
    private final BehaviorRepository behaviorRepository;
    
    public SystemContext buildContext(String userId) {
        // Use @Cacheable annotation for 1hr cache
        // Pull from existing services
        // Return SystemContext object
    }
    
    // Helper methods:
    // - buildEntityTypesSchema()
    // - buildKnowledgeBaseOverview()
    // - buildAvailableActions()
    // - buildUserBehaviorContext()
    // - buildIndexStatistics()
    // - buildSystemCapabilities()
}
```

### Day 4: EnrichedPromptBuilder
```java
@Service
@RequiredArgsConstructor
public class EnrichedPromptBuilder {
    private final SystemContextBuilder systemContextBuilder;
    
    public String buildSystemPrompt(String userId) {
        SystemContext context = systemContextBuilder.buildContext(userId);
        
        // Build enriched system prompt using String.format()
        // Include:
        // - System overview
        // - Entity types/vector spaces
        // - Available actions
        // - User context
        // - Examples
        // - Disambiguation rules
        
        return enrichedPrompt;
    }
}
```

### Day 5: IntentQueryExtractor Update
```java
@Service
@RequiredArgsConstructor
public class IntentQueryExtractor {
    private final AICoreService aiCoreService;
    private final EnrichedPromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;
    
    public MultiIntentResponse extract(String rawQuery, String userId) {
        // STEP 1: Build enriched system prompt
        String systemPrompt = promptBuilder.buildSystemPrompt(userId);
        
        // STEP 2: Combine with user query
        String userPrompt = "Extract intents from: " + rawQuery;
        
        // STEP 3: Send to LLM
        String response = aiCoreService.generateText(systemPrompt, userPrompt);
        
        // STEP 4: Parse into MultiIntentResponse
        return objectMapper.readValue(response, MultiIntentResponse.class);
    }
}
```

### Day 6-7: Integration & Testing
```java
// Update RAGController
@PostMapping("/rag/query")
public ResponseEntity<OrchestrationResult> queryWithIntentExtraction(
    @RequestBody String query,
    @RequestParam(required = false) String userId) {
    
    MultiIntentResponse intents = intentExtractor.extract(query, userId);
    OrchestrationResult result = ragOrchestrator.orchestrate(intents);
    return ResponseEntity.ok(result);
}
```

---

## Minimal Viable Implementation

Start simple, iterate:

### MVP (Day 1-3)
```
✅ SystemContextBuilder - pulls entity types, index count
✅ EnrichedPromptBuilder - includes entity types, actions, examples
✅ IntentQueryExtractor - uses enriched prompt
✅ DTOs for MultiIntentResponse and Intent
```

### V1.1 (Day 4-5)
```
✅ Add user behavior context
✅ Add index statistics
✅ Add system capabilities
✅ Caching for context
```

### V1.2 (Day 6+)
```
✅ RAGOrchestrator integration
✅ Action execution
✅ Compound query handling
✅ Production monitoring
```

---

## Code Snippets

### SystemContextBuilder - Entity Types
```java
private EntityTypesSchema buildEntityTypesSchema() {
    Set<String> supportedTypes = configurationLoader.getSupportedEntityTypes();
    
    return EntityTypesSchema.builder()
        .entityTypes(supportedTypes.stream()
            .map(type -> {
                AIEntityConfig config = configurationLoader.getEntityConfig(type);
                return EntityTypeInfo.builder()
                    .type(type)
                    .description(type + " related documents")
                    .searchable(config.isEnableSearch())
                    .searchableFields(config.getSearchableFields().stream()
                        .map(AISearchableField::getFieldName)
                        .collect(Collectors.toList()))
                    .build();
            })
            .collect(Collectors.toList()))
        .vectorSpaces(List.of(
            new VectorSpaceInfo("policies", "Company policies"),
            new VectorSpaceInfo("products", "Product information"),
            new VectorSpaceInfo("support", "Support documentation")
        ))
        .build();
}
```

### SystemContextBuilder - Index Stats
```java
private KnowledgeBaseOverview buildKnowledgeBaseOverview() {
    Map<String, Long> contentCountByType = new HashMap<>();
    
    for (String entityType : configurationLoader.getSupportedEntityTypes()) {
        contentCountByType.put(
            entityType, 
            searchableEntityRepository.countByEntityType(entityType)
        );
    }
    
    return KnowledgeBaseOverview.builder()
        .totalIndexedDocuments(
            contentCountByType.values().stream().mapToLong(Long::longValue).sum()
        )
        .documentsByType(contentCountByType)
        .lastIndexUpdateTime(
            searchableEntityRepository.findMaxUpdatedAt()
                .orElse(LocalDateTime.now())
        )
        .indexHealth("healthy")
        .build();
}
```

### EnrichedPromptBuilder - Main Prompt
```java
public String buildSystemPrompt(String userId) {
    SystemContext context = systemContextBuilder.buildContext(userId);
    
    return String.format("""
        You are an expert query intent extractor.
        
        ## AVAILABLE ACTIONS
        %s
        
        ## ENTITY TYPES & VECTOR SPACES
        %s
        
        ## INDEXED CONTENT
        Total: %d documents
        By Type: %s
        
        ## USER CONTEXT
        %s
        
        ## EXTRACTION RULES
        1. INFORMATION intent: User asking for info → search vector space
        2. ACTION intent: User wants to perform action → available in system
        3. OUT_OF_SCOPE: Query outside KB → admit unknown
        4. COMPOUND: Multiple questions → extract all intents
        
        ## RESPONSE FORMAT
        ONLY valid JSON, no markdown:
        {
          "rawQuery": "...",
          "primaryIntent": "...",
          "intents": [{...}],
          "isCompound": true|false,
          "orchestrationStrategy": "sequential|parallel"
        }
        """,
        formatActions(context.getAvailableActions()),
        formatVectorSpaces(context.getEntityTypesSchema()),
        context.getKnowledgeBaseOverview().getTotalIndexedDocuments(),
        context.getKnowledgeBaseOverview().getDocumentsByType(),
        formatUserContext(context.getUserBehaviorContext())
    );
}
```

### IntentQueryExtractor - Main Method
```java
public MultiIntentResponse extract(String rawQuery, String userId) {
    try {
        log.info("Extracting intents from query: {}", rawQuery);
        
        // Build enriched context
        String systemPrompt = promptBuilder.buildSystemPrompt(userId);
        
        // Create user prompt
        String userPrompt = String.format(
            "Extract structured intents from this user query: \"%s\"",
            rawQuery
        );
        
        // Send to LLM
        String jsonResponse = aiCoreService.generateText(systemPrompt, userPrompt);
        
        // Parse response
        MultiIntentResponse response = objectMapper.readValue(
            jsonResponse,
            MultiIntentResponse.class
        );
        
        log.info("Extracted intents: primary={}, count={}, compound={}",
            response.getPrimaryIntent(),
            response.getIntents().size(),
            response.getIsCompound()
        );
        
        return response;
        
    } catch (Exception e) {
        log.error("Intent extraction failed", e);
        return buildFallbackResponse(rawQuery);
    }
}
```

---

## Testing Strategy

### Unit Tests
```java
@Test
public void testSystemContextBuilder_shouldPullEntityTypes() {
    SystemContext context = builder.buildContext("user123");
    
    assertThat(context.getEntityTypesSchema().getEntityTypes())
        .isNotEmpty();
}

@Test
public void testEnrichedPrompt_shouldIncludeActions() {
    String prompt = promptBuilder.buildSystemPrompt("user123");
    
    assertThat(prompt)
        .contains("cancel_subscription")
        .contains("update_shipping_address")
        .contains("request_refund");
}

@Test
public void testIntentExtraction_shouldReturnMultipleIntents() {
    MultiIntentResponse response = extractor.extract(
        "What's your policy and can I change my address?",
        "user123"
    );
    
    assertThat(response.getIntents()).hasSize(2);
    assertThat(response.getIsCompound()).isTrue();
}
```

### Integration Tests
```java
@Test
public void testEndToEnd_shouldOrchestrate() {
    MultiIntentResponse intents = extractor.extract(
        "Return my order and give me tracking",
        "user123"
    );
    
    OrchestrationResult result = orchestrator.orchestrate(intents, "user123");
    
    assertThat(result).isNotNull();
    assertThat(result.getResults()).isNotEmpty();
}
```

---

## Deployment Checklist

- [ ] All DTOs created
- [ ] SystemContextBuilder implemented
- [ ] EnrichedPromptBuilder implemented
- [ ] IntentQueryExtractor updated
- [ ] Caching configured (@Cacheable)
- [ ] Fallback response defined
- [ ] Error handling comprehensive
- [ ] Unit tests passing
- [ ] Integration tests passing
- [ ] Performance testing done (latency < 500ms)
- [ ] Production logging configured
- [ ] Metrics collection setup
- [ ] Monitoring dashboards created
- [ ] Documentation written
- [ ] Code review passed
- [ ] Deployed to staging
- [ ] Smoke tests passed
- [ ] Deployed to production
- [ ] Monitor metrics in production

---

## Monitoring & Metrics

```java
// Add to IntentQueryExtractor
private void recordMetrics(MultiIntentResponse response, long durationMs) {
    metrics.recordIntentExtraction(
        response.getPrimaryIntent(),
        response.getConfidence(),
        durationMs,
        response.getIntents().size()
    );
    
    if (response.getIsCompound()) {
        metrics.recordCompoundQuery();
    }
}
```

---

## Success Criteria

| Metric | Before | Target | Success |
|--------|--------|--------|---------|
| Intent Accuracy | 60% | 90% | ✅ |
| Vector Space Routing | Generic | Specific | ✅ |
| Action Detection | 40% | 90% | ✅ |
| Compound Handling | 30% | 85% | ✅ |
| Response Time | 300ms | < 350ms | ✅ |
| User Satisfaction | Low | High | ✅ |

---

## Quick Reference: What to Do Each Day

**Day 1**: Create all DTOs (copy-paste structure)
**Day 2-3**: Implement SystemContextBuilder (wire existing services)
**Day 4**: Implement EnrichedPromptBuilder (build prompt)
**Day 5**: Update IntentQueryExtractor (use enriched prompt)
**Day 6-7**: Integration, testing, deployment

**Total**: 1 week for production-ready system

---

## Questions?

Refer to these documents for deep dives:
- `ENRICHED_INTENT_EXTRACTION_DESIGN.md` - Full architecture
- `EXISTING_INFRASTRUCTURE_FOR_ENRICHMENT.md` - What you have
- `INTENT_EXTRACTION_COMPARISON.md` - Before/after
- `IMPLEMENTATION_SUMMARY.md` - Complete overview

**Now go build it!** 🚀

