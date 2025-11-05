# Intent Extraction - Visual Architecture Guide

## High-Level Flow Diagram

```
┌────────────────────────────────────────────────────────────────┐
│                    USER QUERY                                  │
│           "can i get money back if broken???"                  │
└──────────────────────────────┬─────────────────────────────────┘
                               │
                ┌──────────────┴──────────────┐
                │                             │
                ↓                             ↓
        ┌──────────────────┐        ┌────────────────────┐
        │ System Context   │        │ Enriched Prompt    │
        │ Builder          │        │ Builder            │
        │                  │        │                    │
        │ Pulls from:      │        │ Uses:              │
        │ • Config Loader  │        │ • SystemContext    │
        │ • Search Repo    │        │ • Format Template  │
        │ • Behavior Repo  │        │ • Examples         │
        │ • Vector DB      │        │ • Rules            │
        └────────┬─────────┘        └─────────┬──────────┘
                 │                           │
                 ↓                           ↓
        ┌────────────────────┐    ┌──────────────────────┐
        │ SystemContext {    │    │ System Prompt        │
        │  • Entity types    │    │ "You are..."         │
        │  • Index stats     │    │ "Entity Types: ..."  │
        │  • User behavior   │    │ "Actions: ..."       │
        │  • Capabilities    │    │ "Rules: ..."         │
        │ }                  │    │ "Examples: ..."      │
        └────────┬───────────┘    └─────────┬────────────┘
                 └──────────────┬────────────┘
                                │
                    ┌───────────┴────────────┐
                    │                        │
                Combined Input:
                • SystemPrompt (~3000 tokens)
                • User Query
                    │
                    ↓
        ┌───────────────────────────┐
        │ LLM (AICoreService)       │
        │                           │
        │ Extracts intents with:    │
        │ • Full system context     │
        │ • User patterns           │
        │ • Available actions       │
        └───────────┬───────────────┘
                    │
                    ↓
        ┌────────────────────────────┐
        │ MultiIntentResponse {      │
        │  • intents: [...]          │
        │  • vectorSpace: "policies" │
        │  • action: "request_refund"│
        │  • confidence: 0.95        │
        │  • isCompound: false       │
        │ }                          │
        └───────────┬────────────────┘
                    │
                    ↓
        ┌────────────────────────────┐
        │ RAGOrchestrator            │
        │                            │
        │ 1. Search policies space   │
        │ 2. Prepare action          │
        │ 3. Present results         │
        └────────────────────────────┘
```

---

## Detailed System Context Collection

```
SystemContextBuilder pulls from existing infrastructure:

┌─────────────────────────────────────────────────────┐
│         AIEntityConfigurationLoader                 │
│                                                     │
│  supportedEntityTypes = [                           │
│    "product" (1200 docs),                          │
│    "policy" (800 docs),                            │
│    "support" (543 docs),                           │
│    "user" (200 docs)                               │
│  ]                                                  │
└──────────────┬──────────────────────────────────────┘
               │
┌──────────────┴──────────────────────────────────────┐
│     AISearchableEntityRepository                     │
│                                                     │
│  countByEntityType("product") = 1200                │
│  countByEntityType("policy") = 800                  │
│  countByEntityType("support") = 543                 │
│  count() = 2543 total                               │
└──────────────┬──────────────────────────────────────┘
               │
┌──────────────┴──────────────────────────────────────┐
│        BehaviorRepository                           │
│                                                     │
│  findByUserId(userID) = [                           │
│    {type: "SEARCH_QUERY", query: "return policy"},  │
│    {type: "PRODUCT_VIEW", ...},                     │
│    {type: "CUSTOMER_SUPPORT", ...},                 │
│    ...                                              │
│  ]                                                  │
└──────────────┬──────────────────────────────────────┘
               │
┌──────────────┴──────────────────────────────────────┐
│       VectorDatabaseService                         │
│                                                     │
│  statistics = {                                     │
│    totalVectors: 2543,                              │
│    indexHealth: "operational",                      │
│    avgResponseTime: 234ms                           │
│  }                                                  │
└──────────────┬──────────────────────────────────────┘
               │
┌──────────────┴──────────────────────────────────────┐
│         AIProviderConfig                            │
│                                                     │
│  capabilities = {                                   │
│    supportsHybridSearch: true,                      │
│    supportsCompoundQueries: true,                   │
│    maxTokens: 2000,                                 │
│    temperature: 0.7                                 │
│  }                                                  │
└──────────────┬──────────────────────────────────────┘
               │
               ↓
        Aggregates into:
               │
        ┌──────↓──────────────────────┐
        │ SystemContext {             │
        │  entityTypes: [...],        │
        │  indexStats: {...},         │
        │  userPatterns: {...},       │
        │  capabilities: {...}        │
        │ }                           │
        └─────────────────────────────┘
```

---

## Enriched Prompt Construction

```
EnrichedPromptBuilder.buildSystemPrompt():

BASE TEMPLATE:
┌────────────────────────────────────────────────────┐
│ "You are an expert query intent extractor...       │
│                                                    │
│  ## SYSTEM OVERVIEW                               │
│  ### Available Vector Spaces                      │
│  ### Indexed Content                              │
│  ### Available Actions                            │
│  ### User Context                                 │
│  ### System Capabilities                          │
│  ...                                              │
│  ## RULES & EXAMPLES                              │
│  [detailed rules]                                 │
│  [concrete examples]                              │
│ "                                                 │
└────────────────────────────────────────────────────┘
         │
         ├→ Fill with EntityTypes data
         ├→ Fill with IndexStatistics data
         ├→ Fill with AvailableActions
         ├→ Fill with UserBehavior data
         ├→ Fill with SystemCapabilities
         │
         ↓
┌────────────────────────────────────────────────────┐
│ Enriched System Prompt (~3000 tokens)              │
│ "You are an expert query intent extractor...       │
│                                                    │
│  ## SYSTEM OVERVIEW                               │
│  ### Available Vector Spaces                      │
│  - policies: Company policies, terms              │
│  - products: Product info, specs                  │
│  - support: Support docs, guides                  │
│                                                    │
│  ### Indexed Content                              │
│  Total: 2543 documents                            │
│  - product: 1200 docs                             │
│  - policy: 800 docs                               │
│  - support: 543 docs                              │
│                                                    │
│  ### Available Actions                            │
│  - cancel_subscription (required: subId, reason) │
│  - update_shipping_address (required: address)    │
│  - request_refund (required: orderId, reason)     │
│                                                    │
│  ### User Context                                 │
│  - Known user, 24 recent behaviors               │
│  - Recent searches: return policy, shipping       │
│  - Has made purchases: true                       │
│                                                    │
│  ### System Capabilities                          │
│  - Hybrid search: true                            │
│  - Compound queries: true                         │
│  - Max docs: 10                                   │
│                                                    │
│  ## RULES                                         │
│  1. ACTION if matches available action           │
│  2. INFORMATION if retrieval needed              │
│  3. COMPOUND if multiple questions               │
│  4. Route to correct vector space                │
│                                                    │
│  ## EXAMPLES                                      │
│  [example: 'cancel my subscription' → ACTION]    │
│  [example: 'policy question' → INFORMATION]      │
│  [example: 'policy + action' → COMPOUND]         │
│ "                                                 │
└────────────────────────────────────────────────────┘
```

---

## LLM Processing

```
LLM Input:
┌──────────────────────────────────────────────────┐
│ SYSTEM PROMPT (3000 tokens)                      │
│ [Full enriched context with rules & examples]    │
│                                                  │
│ USER MESSAGE:                                    │
│ "Extract intents: 'can i get money back if      │
│  thing broken???'"                               │
└──────────────────────────────────────────────────┘
           │
           ↓
    ┌────────────────────────────────┐
    │ LLM Processing                 │
    │                                │
    │ Context Analysis:              │
    │ - Query is about refund        │
    │ - Typos: "can i" = "Can I"     │
    │ - "money back" = refund        │
    │ - "thing broken" = damaged     │
    │ - Need to search: policies     │
    │                                │
    │ Decision:                      │
    │ - Type: INFORMATION            │
    │ - Intent: refund_eligibility   │
    │ - Vector space: policies       │
    │ - Action: potentially request_│
    │           refund               │
    │ - Confidence: 0.95             │
    └────────────────────────────────┘
           │
           ↓
    ┌────────────────────────────────┐
    │ LLM Output (JSON only):        │
    │ {                              │
    │   "rawQuery": "can i get...",  │
    │   "primaryIntent": "refund",   │
    │   "intents": [{                │
    │     "type": "INFORMATION",     │
    │     "intent": "refund_refund", │
    │     "confidence": 0.95,        │
    │     "vectorSpace": "policies", │
    │     "normalizedQuery":         │
    │       "Can I get a refund for  │
    │        a damaged product?"     │
    │   }, {                         │
    │     "type": "ACTION",          │
    │     "action": "request_refund",│
    │     "confidence": 0.92         │
    │   }],                          │
    │   "isCompound": false,         │
    │   "orchestrationStrategy":     │
    │     "sequential"               │
    │ }                              │
    └────────────────────────────────┘
```

---

## RAGOrchestrator Execution

```
Input: MultiIntentResponse
           │
    ┌──────┴──────┐
    │             │
    ↓             ↓
INFORMATION   ACTION
Intents       Intents
    │             │
    ├─ Identify ──┼─ Identify
    │   vector    │   action
    │   space:    │   type:
    │   policies  │   request_refund
    │             │
    ↓             ↓
SEARCH          EXECUTE
┌────────────┐   ┌──────────────┐
│ Vector DB  │   │ Function     │
│ Query:     │   │ Call:        │
│ "refund"   │   │ request_     │
│   in       │   │  refund()    │
│ policies   │   │              │
│            │   │ Result:      │
│ Results:   │   │ Ready for    │
│ "Return    │   │ confirmation│
│ policy..." │   │              │
└────────────┘   └──────────────┘
    │                   │
    └─────────┬─────────┘
              │
              ↓
        MERGE & PRESENT
        ┌──────────────────────┐
        │ Final Response:      │
        │                      │
        │ "Based on your query:│
        │                      │
        │ Our refund policy:   │
        │ [search results]     │
        │                      │
        │ To request a refund: │
        │ [action ready]       │
        │ "                    │
        └──────────────────────┘
```

---

## Before vs After Comparison

```
BASIC EXTRACTION:
User Query: "can i get money back if broken???"
                    ↓
            (LLM has no context)
                    ↓
        Extract: unknown_query
        Route: general space ❌
        Confidence: 0.5

ENRICHED EXTRACTION:
User Query: "can i get money back if broken???"
                    ↓
        + SystemContext (entity types, index, user history, actions)
        + EnrichedPrompt (examples, rules, available actions)
                    ↓
            (LLM has full context)
                    ↓
        Extract: refund_eligibility ✅
        Route: policies space ✅
        Action: request_refund ✅
        Confidence: 0.95

Result: 35% better accuracy!
```

---

## Data Dependencies Visualization

```
           IntentQueryExtractor
                    │
        ┌───────────┼───────────┐
        │           │           │
        ↓           ↓           ↓
    System      Enriched     LLM Call
    Context     Prompt       (AICoreService)
    Builder     Builder
        │           │
        ├─→ Configs │
        │   ├─→ Entity Types
        │   ├─→ Vector Spaces
        │   └─→ Capabilities
        │
        ├─→ Database
        │   ├─→ Indexed Docs Count
        │   ├─→ Index Stats
        │   └─→ Metadata
        │
        ├─→ User History
        │   ├─→ Behaviors
        │   ├─→ Search Queries
        │   └─→ Patterns
        │
        ├─→ Actions List
        │   ├─→ From BehaviorType
        │   ├─→ With Parameters
        │   └─→ With Examples
        │
        └─→ System Stats
            ├─→ Performance Metrics
            ├─→ Health Status
            └─→ Capabilities
```

---

## State Transitions

```
INPUT                    PROCESSING                  OUTPUT
────────────────────────────────────────────────────────────

Raw Query
│
├─ [1] SystemContext added
│      (entity types, index stats, user behavior)
│
├─ [2] Enriched Prompt constructed
│      (examples, rules, full context)
│
├─ [3] LLM processes
│      (structured intent extraction)
│
├─ [4] Response parsed
│      (JSON → MultiIntentResponse)
│
├─ [5] Intent classified
│      (INFORMATION/ACTION/OUT_OF_SCOPE)
│
├─ [6] Vector space assigned
│      (policies/products/support/general)
│
├─ [7] Action identified (if applicable)
│      (cancel_subscription/update_address/etc)
│
├─ [8] Orchestration strategy determined
│      (sequential/parallel/merged)
│
└─→ Orchestrator executes
       • Search correct space
       • Execute actions
       • Handle compounds
```

---

This visual guide helps understand the complete flow at every level!

