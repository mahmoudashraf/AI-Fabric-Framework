# Implementation Sequence Guide

## 🎯 Overview

This document provides the optimal sequence to implement the 6-layer RAG system, including dependencies, timing, and resource allocation.

---

## 📊 Complete Timeline

```
┌─────────────────────────────────────────────────────────────┐
│  WEEK 1: Core Foundation                                    │
├─────────────────────────────────────────────────────────────┤
│  Layer 1: PII Detection (Optional)              2-3 hours   │
│  Layer 2: Intent Extraction                     4-5 hours   │
│  Total: ~7 hours (can be done in 1-2 days)                 │
└─────────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────────┐
│  WEEK 1-2: Action Handling                                   │
├─────────────────────────────────────────────────────────────┤
│  Layer 3: RAG Orchestrator                      3-4 hours   │
│  + Implement ActionHandler per service          4-6 hours   │
│  Total: ~8 hours (2-3 days depending on # of actions)      │
└─────────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────────┐
│  WEEK 2: Intelligence Layer                                  │
├─────────────────────────────────────────────────────────────┤
│  Layer 4: Smart Suggestions                     2-3 hours   │
│  Testing & Validation                           2-3 hours   │
│  Total: ~5 hours (1-2 days)                                 │
└─────────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────────┐
│  WEEK 3: Polish & Storage                                    │
├─────────────────────────────────────────────────────────────┤
│  Layer 5: Response Sanitization                 1-2 hours   │
│  Layer 6: Intent History (Optional)             2-3 hours   │
│  Total: ~4 hours (1 day)                                    │
└─────────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────────┐
│  WEEK 3: Final Testing & Deployment                          │
├─────────────────────────────────────────────────────────────┤
│  Integration Testing                            3-4 hours   │
│  Performance Testing                            2-3 hours   │
│  Deployment                                     1-2 hours   │
│  Total: ~6 hours (1-2 days)                                 │
└─────────────────────────────────────────────────────────────┘

TOTAL IMPLEMENTATION TIME: 20-30 hours (3-4 weeks)
```

---

## 🔄 Detailed Sequence (Step-by-Step)

### PHASE 1: Setup & Foundation (Days 1-2)

#### Day 1 Morning (4 hours): Layer 1 - PII Detection

**Goal:** Optional PII layer ready (can skip if not needed initially)

**Tasks:**
1. Create DTOs
   - [ ] PIIDetectionResult
   - [ ] PIIDetection
   - [ ] PIIMode enum
   
2. Implement PIIDetectionService
   - [ ] Pattern detection (email, phone, CC, SSN)
   - [ ] Redaction logic
   - [ ] Configuration support
   
3. Integrate into RAGService
   - [ ] Wire PIIDetectionService
   - [ ] Call before intent extraction
   - [ ] Pass redacted query forward

4. Configure application.yml
   ```yaml
   ai:
     pii-detection:
       enabled: false  # Start disabled
       mode: PASS_THROUGH
   ```

5. Test
   - [ ] Test detection with sample PII
   - [ ] Test redaction
   - [ ] Test with disabled mode

**Estimated Time:** 2-3 hours
**Complexity:** Low
**Dependencies:** None

---

#### Day 1 Afternoon (4-5 hours): Layer 2 - Intent Extraction

**Goal:** LLM-based intent extraction working with next-step recommendations

**Tasks:**
1. Create DTOs
   - [ ] Intent
   - [ ] MultiIntentResponse
   - [ ] IntentType enum
   - [ ] NextStepRecommendation (NEW!)
   
2. Implement SystemContextBuilder
   - [ ] Wire AvailableActionsRegistry
   - [ ] Wire KnowledgeBaseOverviewService
   - [ ] Gather system context
   
3. Implement EnrichedPromptBuilder
   - [ ] Build system prompt with available actions
   - [ ] Add KB overview section
   - [ ] Add next-step recommendation guidance (NEW!)
   - [ ] Format extraction rules clearly
   
4. Implement IntentQueryExtractor
   - [ ] Call LLM with enriched prompt
   - [ ] Parse JSON response
   - [ ] Validate confidence scores
   - [ ] Handle next-step recommendations
   
5. Test
   - [ ] Test with ACTION intent
   - [ ] Test with INFORMATION intent
   - [ ] Test with OUT_OF_SCOPE
   - [ ] Test next-step generation

**Estimated Time:** 4-5 hours
**Complexity:** Medium
**Dependencies:** AICoreService, existing AvailableActionsRegistry, KnowledgeBaseOverviewService

---

### PHASE 2: Action Handling (Days 3-4)

#### Day 3 All Day (4-5 hours): Layer 3 - RAG Orchestrator + ActionHandlers

**Goal:** Complete action orchestration with user handlers implemented

**Subtasks 1 (2-3 hours): Implement RAGOrchestrator**
- [ ] Create ActionHandlerRegistry
  - [ ] Auto-discover all ActionHandler implementations
  - [ ] Build action name → handler map
  - [ ] Provide getAllAvailableActions()
  
- [ ] Implement RAGOrchestrator
  - [ ] Handle single intents
  - [ ] Handle compound intents
  - [ ] Handle out-of-scope
  - [ ] Call action handlers
  - [ ] Retrieve next-step info (Layer 4)
  - [ ] Error handling & logging

**Subtasks 2 (2-3 hours per service): Implement ActionHandlers**

For EACH service with actions (e.g., SubscriptionService):

**Example: CancelSubscriptionHandler**
```
Tasks:
- [ ] Create CancelSubscriptionHandler class
- [ ] Implement getActionMetadata()
- [ ] Implement validateActionAllowed()
- [ ] Implement executeAction() (YOUR BUSINESS LOGIC)
- [ ] Implement handleError()
- [ ] Configure confirmation message in application.yml
- [ ] Test handler locally
- [ ] Test via orchestrator
```

**Repeat for:**
- [ ] UpgradeSubscriptionHandler
- [ ] PauseSubscriptionHandler
- [ ] UpdatePaymentMethodHandler
- [ ] RequestRefundHandler
- [ ] Other business actions...

**Configuration (application.yml):**
```yaml
ai:
  actions:
    subscription:
      cancel:
        confirm-message: "Cancel your subscription?"
        success-message: "Subscription cancelled"
    payment:
      update:
        confirm-message: "Update payment method?"
    # ... more actions ...
```

**Testing:**
- [ ] Test each action independently
- [ ] Test orchestrator routing
- [ ] Test error scenarios
- [ ] Test confirmation messages

**Estimated Time:** 4-5 hours (depending on # of actions)
**Complexity:** Medium
**Dependencies:** RAGService, user's business logic

---

### PHASE 3: Intelligence & Polish (Days 5-6)

#### Day 5 Morning (2-3 hours): Layer 4 - Smart Suggestions

**Goal:** Intelligent next-step recommendations fully operational

**Tasks:**
1. Verify NextStepRecommendation is in Intent DTO
   - [ ] Already added in Layer 2
   
2. Verify LLM prompt includes next-step guidance
   - [ ] Already added in Layer 2
   
3. Implement Smart Suggestions in RAGOrchestrator
   - [ ] Check if nextStepRecommended exists
   - [ ] Validate confidence threshold (0.70+)
   - [ ] Call RAGService for next-step query
   - [ ] Add to response with formatting
   
4. Update response DTOs
   - [ ] Add nextStepInfo to OrchestrationResult
   
5. Test
   - [ ] Test that next-step is retrieved
   - [ ] Test confidence thresholds
   - [ ] Test with different action types
   - [ ] Verify response formatting

**Estimated Time:** 2-3 hours
**Complexity:** Low (mostly already in Layer 2)
**Dependencies:** Layer 3, RAGService

---

#### Day 5 Afternoon (2-3 hours): Layer 5 - Response Sanitization

**Goal:** Clean responses and format for user presentation

**Tasks:**
1. Implement ResponseSanitizer
   - [ ] Remove internal fields
   - [ ] Redact PII from response
   - [ ] Format for presentation
   - [ ] Add suggestions section
   
2. Update RAGOrchestrator
   - [ ] Call ResponseSanitizer before returning
   
3. Test
   - [ ] Test PII removal from response
   - [ ] Test formatting
   - [ ] Test with different response types

**Estimated Time:** 1-2 hours
**Complexity:** Low
**Dependencies:** Layer 3, Layer 4

---

#### Day 6 Morning (2-3 hours): Layer 6 - Intent History (Optional)

**Goal:** Store structured intents for analytics and compliance

**Tasks:**
1. Create Entities & Repositories
   - [ ] IntentHistory entity
   - [ ] IntentHistoryRepository
   
2. Implement IntentHistoryService
   - [ ] Save intent (not query)
   - [ ] Retrieve history
   - [ ] Search by intent
   - [ ] Cleanup expired data
   
3. Integrate into RAGOrchestrator
   - [ ] After action execution
   - [ ] Extract structured intent
   - [ ] Store with result
   
4. Configure
   ```yaml
   ai:
     intent-history:
       enabled: true
       ttl-days: 90
       storage-type: DATABASE
   ```
   
5. Test
   - [ ] Test intent storage
   - [ ] Test retrieval
   - [ ] Test TTL cleanup

**Estimated Time:** 2-3 hours
**Complexity:** Low-Medium
**Dependencies:** Layer 3

---

### PHASE 4: Testing & Deployment (Day 7)

#### Day 7 Morning (3-4 hours): Integration Testing

**Tasks:**
- [ ] End-to-end flow test (query → action → suggestion → history)
- [ ] Test all action types
- [ ] Test error scenarios
- [ ] Test PII handling
- [ ] Load testing (concurrent users)
- [ ] Performance benchmarking

**Test Scenarios:**
```
1. Action Flow
   Query: "Cancel my order"
   Expected:
   ├─ Intent extracted (ACTION: cancel_order)
   ├─ Action executed via handler
   ├─ Smart suggestion retrieved (refund info)
   └─ History stored (structured intent)

2. Information Flow
   Query: "What's your refund policy?"
   Expected:
   ├─ Intent extracted (INFORMATION: show_refund_policy)
   ├─ Retrieval from RAG
   ├─ Smart suggestion (refund process)
   └─ History stored

3. Compound Flow
   Query: "Pause my sub and show discounts"
   Expected:
   ├─ Two intents extracted (ACTION + INFORMATION)
   ├─ Both handled sequentially
   ├─ Two next-steps recommended
   └─ Both in history

4. Out-of-Scope Flow
   Query: "Build me a spaceship"
   Expected:
   ├─ Intent: OUT_OF_SCOPE
   ├─ Honest response
   └─ Stored in history
```

**Estimated Time:** 3-4 hours
**Complexity:** Medium

---

#### Day 7 Afternoon (1-2 hours): Deployment

**Tasks:**
- [ ] Code review
- [ ] Security audit
- [ ] Performance verification
- [ ] Documentation review
- [ ] Deploy to staging
- [ ] Final smoke tests
- [ ] Deploy to production

---

## 📋 Implementation Checklist by Phase

### Phase 1: Foundation
- [ ] PII Detection Service
- [ ] Intent Extraction Service
- [ ] SystemContextBuilder
- [ ] EnrichedPromptBuilder
- [ ] NextStepRecommendation DTO
- [ ] Configuration setup

### Phase 2: Actions
- [ ] ActionHandlerRegistry
- [ ] RAGOrchestrator
- [ ] ActionHandler implementations (per service)
- [ ] Configuration (confirmation messages)
- [ ] Testing (each action)

### Phase 3: Intelligence
- [ ] Smart suggestions retrieval
- [ ] Response sanitization
- [ ] Intent history storage
- [ ] Configuration (TTL, storage type)

### Phase 4: Deployment
- [ ] Integration tests
- [ ] Performance tests
- [ ] Security audit
- [ ] Documentation
- [ ] Deployment

---

## 🔑 Critical Dependencies

```
Layer 1 (PII)
├─ Depends on: Nothing
└─ Can start immediately

Layer 2 (Intent Extraction)
├─ Depends on: Layer 1 (optional)
├─ Requires: AICoreService
├─ Requires: AvailableActionsRegistry (from earlier)
└─ Can start immediately

Layer 3 (Orchestrator)
├─ Depends on: Layer 2
├─ Requires: User ActionHandler implementations
└─ Blocked until Layer 2 complete

Layer 4 (Smart Suggestions)
├─ Depends on: Layer 2 + Layer 3
├─ Requires: RAGService
└─ Blocked until Layer 3 complete

Layer 5 (Sanitization)
├─ Depends on: Layer 3 + Layer 4
└─ Blocked until Layer 4 complete

Layer 6 (History)
├─ Depends on: Layer 3
├─ Can start in parallel with Layer 4-5
└─ Can start after Layer 3 complete
```

---

## ⚡ Parallel Work Opportunities

**Team of 1 developer:**
- Sequential is required (dependencies)
- 3-4 weeks total

**Team of 2 developers:**
```
Dev 1: Layers 1-3 (weeks 1-2)
Dev 2: 
  ├─ Week 1: Implement ActionHandlers for own services
  ├─ Week 2: Layer 4 (Smart Suggestions) + Layer 6 (History)
  └─ Week 3: Integration testing
```

**Team of 3+ developers:**
```
Dev 1: Layers 1-2 (Foundation)
Dev 2: Layer 3 + ActionHandlers (Actions)
Dev 3: Layer 4-6 (Intelligence + Storage)
→ All work in parallel after Layer 2 complete
```

---

## 🎯 Milestones

### Milestone 1: Basic Intent Extraction (End of Day 2)
- Layerss 1-2 complete
- LLM extracts structured intents
- Next-steps generated (not yet used)
- **Status:** Ready for testing

### Milestone 2: Action Execution (End of Day 4)
- Layers 1-3 complete
- Actions execute via handlers
- Smart suggestions retrieved
- **Status:** Core system working

### Milestone 3: Full System (End of Day 6)
- All 6 layers complete
- End-to-end flow operational
- **Status:** Ready for testing

### Milestone 4: Production Ready (End of Day 7)
- All tests passing
- Performance verified
- Documentation complete
- **Status:** Deploy to production

---

## 📊 Resource Allocation

### Time per Layer (1 Developer)

```
Layer 1 (PII): 2-3 hours
  ├─ Code: 1.5 hours
  ├─ Testing: 0.5 hours
  └─ Setup: 0.5 hours

Layer 2 (Intent Extraction): 4-5 hours
  ├─ Code: 3 hours
  ├─ LLM prompt tuning: 1 hour
  └─ Testing: 1 hour

Layer 3 (Orchestrator): 3-4 hours (library) + 4-6 hours (handlers)
  ├─ Code (library): 2 hours
  ├─ Testing: 1-2 hours
  ├─ Code (handlers per service): 1-2 hours each
  └─ Testing (handlers): 1-2 hours each

Layer 4 (Smart Suggestions): 2-3 hours
  ├─ Code: 1 hour
  ├─ LLM prompt: 0.5 hours
  └─ Testing: 1-1.5 hours

Layer 5 (Sanitization): 1-2 hours
  ├─ Code: 1 hour
  └─ Testing: 0.5-1 hour

Layer 6 (History): 2-3 hours
  ├─ Code: 1.5 hours
  ├─ Configuration: 0.5 hours
  └─ Testing: 0.5-1 hour

Testing & Deployment: 4-6 hours
  ├─ Integration: 2-3 hours
  ├─ Performance: 1-2 hours
  └─ Deployment: 1-2 hours

TOTAL: 20-30 hours (3-4 weeks)
```

---

## ✅ Pre-Implementation Checklist

Before starting, ensure you have:

- [ ] AICoreService working
- [ ] AvailableActionsRegistry implemented
- [ ] KnowledgeBaseOverviewService ready
- [ ] RAGService working
- [ ] Database setup (for Layer 6)
- [ ] OpenAI API access
- [ ] application.yml ready for modifications
- [ ] Team understood requirements
- [ ] Environment setup complete

---

## 🚀 Getting Started

1. **Day 1 Morning:**
   - Read: `01_PII_DETECTION_LAYER.md` (30 min)
   - Implement: Layer 1 (2.5 hours)
   - Test: PII detection (0.5 hours)

2. **Day 1 Afternoon:**
   - Read: `02_INTENT_EXTRACTION_LAYER.md` (45 min)
   - Implement: Layer 2 (4 hours)
   - Test: Intent extraction (0.5 hours)

3. **Day 3-4:**
   - Read: `03_RAG_ORCHESTRATOR_LAYER.md` (30 min)
   - Implement: Layer 3 (3 hours)
   - Implement: ActionHandlers (2-3 hours per service)

4. **Day 5:**
   - Read: `04_SMART_SUGGESTIONS_LAYER.md` (30 min)
   - Implement: Layers 4-6 (6-7 hours)

5. **Day 7:**
   - Integration testing (3-4 hours)
   - Deployment (1-2 hours)

---

## 📞 Estimated Effort Summary

| Layer | Time | Complexity | Dependencies |
|-------|------|-----------|--------------|
| 1 (PII) | 2-3h | Low | None |
| 2 (Intent) | 4-5h | Medium | None |
| 3 (Orchestrator) | 7-10h | Medium | Layer 2 |
| 4 (Suggestions) | 2-3h | Low | Layer 3 |
| 5 (Sanitization) | 1-2h | Low | Layer 4 |
| 6 (History) | 2-3h | Low | Layer 3 |
| Testing & Deploy | 4-6h | Medium | All layers |
| **TOTAL** | **22-32h** | **Medium** | **Sequential** |

---

**Ready to start? Follow this sequence and you'll have a production-ready RAG system in 3-4 weeks!** 🎉

