# Component Guide — What Exists vs. What You Build

The previous version of this guide described six tightly coupled “layers” (PII detection, intent extraction, orchestrated actions, sanitisation, history, smart suggestions). Only a subset of that stack was ever written. This document now maps the actual library code to the responsibilities you still need to cover in your product.

---

## High-Level Architecture in the Codebase

```
┌─────────────────────────────┐
│  Your Application Layer     │
│  ├─ PII / compliance checks │
│  ├─ Intent classification   │
│  ├─ Domain actions / UI     │
└──────────────┬──────────────┘
               │
┌──────────────▼──────────────┐
│  ai-infrastructure-core     │
│  ├─ AICoreService           │  → OpenAI chat + embeddings
│  ├─ AIEmbeddingService      │  → Embedding helpers
│  ├─ AISearchService         │  → Semantic search
│  ├─ RAGService              │  → Index + retrieve content
│  └─ AdvancedRAGService      │  → Query expansion, re-ranking
└──────────────┬──────────────┘
               │
┌──────────────▼──────────────┐
│  Vector store + LLM APIs    │
└─────────────────────────────┘
```

All higher-level behaviours (approval flows, conversational memory, multi-intent routing) are up to you to compose around these services.

---

## What the Library Delivers

| Area | What Works | Key Classes | Notes |
|------|------------|-------------|-------|
| Provider configuration | Bindings for OpenAI, Pinecone, ONNX, REST embeddings | `AIProviderConfig` | Uses `ai.providers.*` prefix |
| Embeddings | Generate vectors via OpenAI or ONNX | `AIEmbeddingService` | Reused by search & RAG |
| Semantic search | Vector similarity with Lucene/Pinecone/in-memory adapters | `AISearchService`, `VectorDatabaseService` | Threshold, limit, filters supported |
| Baseline RAG | Index + retrieve context, simple response scaffold | `RAGService` | Methods: `indexContent`, `performRag`, `performRAGQuery` |
| Advanced RAG | Query expansion, multi-strategy search, re-ranking, context optimisation | `AdvancedRAGService`, `AdvancedRAGController` | Accessible over REST at `/api/ai/advanced-rag/search` |

Anything else that was previously described as “Layer 1–6” is not implemented.

---

## Minimal Integration Path

1. **Configure providers**  
   Populate `ai.providers` properties in your Spring configuration (API key, model names, vector DB choice, thresholds).

2. **Ingest knowledge**  
   - Prepare documents and metadata.  
   - Call `RAGService#indexContent(entityType, entityId, content, metadata)` as part of your ingestion pipeline.

3. **Serve retrieval**  
   - For straight semantic search, use `RAGService#performRag(RAGRequest)`.  
   - For richer behaviour (query expansion, hybrid scoring), call `AdvancedRAGService#performAdvancedRAG` or hit the REST endpoint.

4. **Generate responses**  
   Feed the aggregated context into `AICoreService#generateContent` (or your own prompt templates) to produce user-facing output.

5. **Add application logic**  
   Wrap the library calls with your PII filters, intent classifier, domain-specific action execution, and analytics.

---

## Suggested Timeline (Single Developer)

| Day | Focus | Deliverable |
|-----|-------|-------------|
| 1 | Configuration + ingestion prototype | Provider config in place, first content indexed |
| 2 | Retrieval APIs wired | `RAGService` / `AdvancedRAGService` integrated, endpoint exposed |
| 3 | Prompting & response formatting | Baseline answer generation, logging |
| 4 | Surround with application logic | PII guard, intent classifier, business rules |
| 5 | Testing & hardening | Load tests, vector index tuning, fallback handling |

Parallel teams can split ingestion, retrieval tuning, and UI integration once the configuration scaffolding is complete.

---

## Current Gaps You Must Fill

- **PII Detection & Redaction:** No service or configuration exists. Add middleware or filters before calling the AI services.
- **Intent Extraction & Action Routing:** The library does not parse intents or execute domain actions. Plug in your own NLP model/classifier and orchestrate downstream logic.
- **Response Sanitisation & History:** There is no persistence of user interactions or automatic masking in responses. Implement this in your controllers/services.
- **Smart Suggestions:** No integrated recommendation layer is present. Use `AICoreService#generateRecommendations` or custom prompts to build this yourself.

Each of the focused documents (01–04) explains how to build these additions using the foundation that *is* present.

---

## Checklist Before Shipping

- [ ] `ai.providers.*` properties supplied (API keys, model names, vector DB settings)
- [ ] Vector database reachable and warmed with initial content
- [ ] Retrieval endpoints secured and monitored
- [ ] PII guardrails in place (before both indexing and querying)
- [ ] Intent classification or routing logic implemented externally
- [ ] Domain actions triggered and audited within your application code
- [ ] Responses formatted, logged, and optionally persisted under your governance

Treat this library as the retrieval and LLM access layer. Everything else described in earlier drafts should be implemented explicitly on top of it.
