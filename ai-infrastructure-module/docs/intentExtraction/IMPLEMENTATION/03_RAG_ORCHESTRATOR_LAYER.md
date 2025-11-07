# RAG Services — How Orchestration Works in the Actual Code

There is no `ActionHandlerRegistry`, no per-action handler interfaces, and no built-in intent router. The orchestration layer in this repository consists of two services:

- `RAGService` — baseline indexing and retrieval.
- `AdvancedRAGService` — query expansion, multi-strategy search, re-ranking, and response generation.

This guide shows how to use them as shipped.

---

## Core Responsibilities

```35:72:ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/rag/RAGService.java
public class RAGService {
    ...
    public void indexContent(String entityType, String entityId, String content, Map<String, Object> metadata) {
        ...
        vectorDatabaseService.storeVector(entityType, entityId, content,
            embeddingResponse.getEmbedding(), metadata);
    }
}
```

`RAGService` also exposes:
- `performRag(RAGRequest request)` — semantic search + response payload for standard flows.
- `performRAGQuery(RAGRequest request)` (note the capitalisation) — advanced response that includes confidence scores and metadata.
- `performRAGQuery(String query, String entityType, int limit)` — simplified overload for quick lookups.

```25:63:ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/rag/AdvancedRAGService.java
public class AdvancedRAGService {
    ...
    public AdvancedRAGResponse performAdvancedRAG(AdvancedRAGRequest request) {
        List<String> expandedQueries = expandQuery(request.getQuery(), request.getExpansionLevel());
        List<RAGResponse> searchResults = performMultiStrategySearch(expandedQueries, request);
        List<RAGResponse.RAGDocument> rerankedDocuments = rerankDocuments(searchResults, request.getQuery(), request.getRerankingStrategy());
        String optimizedContext = optimizeContext(rerankedDocuments, request.getContextOptimizationLevel());
        String generatedResponse = generateResponse(request.getQuery(), optimizedContext, request);
        ...
    }
}
```

The advanced service layers query expansion, optional hybrid/contextual search, and response synthesis on top of the baseline `RAGService`.

---

## REST Entry Point

`AdvancedRAGController` publishes `POST /api/ai/advanced-rag/search`, which simply forwards requests to `AdvancedRAGService#performAdvancedRAG`. Use this endpoint if you want a ready-made REST API; otherwise inject the service directly.

---

## Using the Services

```java
@Service
public class KnowledgeBaseFacade {

    private final RAGService ragService;

    public KnowledgeBaseFacade(RAGService ragService) {
        this.ragService = ragService;
    }

    public void indexArticle(KbArticle article) {
        ragService.indexContent(
            "knowledge_base",
            article.getId().toString(),
            article.getBody(),
            Map.of("category", article.getCategory(), "locale", article.getLocale()));
    }

    public RAGResponse ask(String query) {
        return ragService.performRag(
            RAGRequest.builder()
                .query(query)
                .entityType("knowledge_base")
                .limit(5)
                .threshold(0.7)
                .build());
    }
}
```

For more sophisticated searches:

```java
AdvancedRAGResponse response = advancedRAGService.performAdvancedRAG(
    AdvancedRAGRequest.builder()
        .query(userQuery)
        .entityType("knowledge_base")
        .maxResults(8)
        .maxDocuments(5)
        .expansionLevel(3)
        .rerankingStrategy("semantic")
        .contextOptimizationLevel("medium")
        .enableHybridSearch(true)
        .similarityThreshold(0.68)
        .build());
```

---

## Configuration Touchpoints

Everything is wired through `AIProviderConfig` (`ai.providers.*`):
- Choose the embedding model (`openai-embedding-model`) or ONNX/REST provider.
- Pick vector database implementation (`vector-db-type`: `lucene`, `pinecone`, or `memory`).
- Control similarity thresholds and result limits (`vector-db-similarity-threshold`, `vector-db-max-results`).

Ensure the relevant beans for your chosen vector store (Lucene, Pinecone) are enabled in your Spring context.

---

## What You Still Need to Own

- Intent classification and routing (decide when to call RAG vs. execute domain logic).
- Permission checks, confirmation flows, and action execution.
- Response formatting and post-processing (including PII scrubbing).
- Analytics, rate limiting, and error handling wrapped around these services.

Think of `RAGService` and `AdvancedRAGService` as retrieval engines. Your application provides the orchestration, decision-making, and business rules referenced in the earlier documentation.
