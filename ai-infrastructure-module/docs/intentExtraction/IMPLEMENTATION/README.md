# Implementation Guide — Current Capabilities

This folder now documents the features that actually ship in `ai-infrastructure-core`. Earlier drafts promised multi-layer intent extraction, dynamic action handlers, and automated PII tooling, but those components were never implemented. The goal of this revision is to give you a practical map from the code that exists today to the work you need to do in your application.

---

## What Ships in the Library Today

- **AI provider configuration** via `ai.providers.*` properties (`AIProviderConfig`)
- **Core foundation** for calling OpenAI models (`AICoreService`)
- **Embedding & semantic search helpers** (`AIEmbeddingService`, `AISearchService`)
- **Baseline retrieval-augmented generation** (`RAGService`)
- **Advanced RAG orchestration** with query expansion and re-ranking (`AdvancedRAGService`)
- **REST endpoint** for advanced RAG at `/api/ai/advanced-rag/search` (`AdvancedRAGController`)

Everything else (intent classification, domain action execution, PII scrubbing, smart follow-up UX) must be provided by your application or built as future work.

---

## Quick Start

1. **Configure providers**
   ```yaml
   ai:
     providers:
       openai-api-key: ${OPENAI_API_KEY}
       openai-model: gpt-4o-mini
       openai-embedding-model: text-embedding-3-small
       vector-db-type: lucene
       vector-db-index-path: ./data/lucene-vector-index
   ```

2. **Index content**  
   Call `RAGService#indexContent` with your entity type, identifier, and text payload.

3. **Run retrieval**  
   - For simple semantic search, use `RAGService#performRag`.
   - For query expansion + re-ranking, send a POST request to `/api/ai/advanced-rag/search` with `AdvancedRAGRequest`.

4. **Generate content**  
   Use `AICoreService#generateContent` to produce responses against the retrieved context.

---

## Component Overview

| Component | Purpose | Key Entry Points |
|-----------|---------|------------------|
| Configuration | Centralizes provider settings | `AIProviderConfig` |
| Core AI Access | Wraps OpenAI chat & embedding APIs | `AICoreService` |
| Embedding/Search | Generates vectors and performs semantic search | `AIEmbeddingService`, `AISearchService` |
| RAG Basics | Stores vectors and performs combined retrieval | `RAGService` |
| Advanced RAG | Adds query expansion, re-ranking, context optimisation | `AdvancedRAGService`, `/api/ai/advanced-rag/search` |

---

## Implementation Checklist

- [ ] Supply `ai.providers.*` properties (API keys, vector DB choice, thresholds)
- [ ] Load or create vector store (Lucene by default; Pinecone and in-memory adapters are available)
- [ ] Build ingestion pipelines that invoke `RAGService#indexContent`
- [ ] Wire the REST endpoint or call `AdvancedRAGService` directly for complex retrieval flows
- [ ] Layer your own business logic (intent routing, action execution, PII filtering, auditing) before/after the library calls

---

## Upstream Responsibilities

The library deliberately stays focused on retrieval and model access. You are expected to implement the following in your application:

- Detecting and masking PII before passing user content to the library
- Classifying user requests or mapping them to business actions
- Executing domain actions, validations, and confirmation flows
- Persisting conversation or intent history
- Presenting any “smart suggestions” in your product UI

Each document in this folder now explains how to accomplish those responsibilities with the primitives that exist in the codebase.

---

## Where to Go Next

- `COMPLETE_LAYER_GUIDE.md` – walks through each shipped component and how they fit together
- `01_PII_DETECTION_LAYER.md` – explains how to add PII protection on top of the library
- `02_INTENT_EXTRACTION_LAYER.md` – offers patterns for plugging in your own intent classifier
- `03_RAG_ORCHESTRATOR_LAYER.md` – details the actual orchestration services in the code
- `04_SMART_SUGGESTIONS_LAYER.md` – shows how to build follow-up suggestions using existing APIs

Use these guides as a starting point for building the missing layers inside your own services.

