package com.ai.infrastructure.rag;

import com.ai.infrastructure.dto.AIEmbeddingRequest;
import com.ai.infrastructure.dto.AdvancedRAGRequest;
import com.ai.infrastructure.dto.AdvancedRAGResponse;
import com.ai.infrastructure.dto.RAGRequest;
import com.ai.infrastructure.dto.RAGResponse;
import com.ai.infrastructure.core.AISearchService;
import com.ai.infrastructure.core.AIEmbeddingService;
import com.ai.infrastructure.core.AICoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Advanced RAG Service with query expansion, re-ranking, and context optimization
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdvancedRAGService {

    private final AISearchService aiSearchService;
    private final AIEmbeddingService aiEmbeddingService;
    private final AICoreService aiCoreService;
    private final RAGService ragService;

    /**
     * Perform advanced RAG with query expansion and re-ranking
     */
    public AdvancedRAGResponse performAdvancedRAG(AdvancedRAGRequest request) {
        log.info("Performing advanced RAG for query: {}", request.getQuery());
        
        try {
            long startTime = System.currentTimeMillis();
            
            // Step 1: Query expansion
            List<String> expandedQueries = expandQuery(request.getQuery(), request.getExpansionLevel());
            log.debug("Expanded queries: {}", expandedQueries);
            
            // Step 2: Perform multiple searches with different strategies
            List<RAGResponse> searchResults = performMultiStrategySearch(expandedQueries, request);
            
            // Step 3: Re-rank results
            List<RAGResponse.RAGDocument> rerankedDocuments = rerankDocuments(
                searchResults, request.getQuery(), request.getRerankingStrategy()
            );
            
            // Step 4: Context optimization
            String optimizedContext = optimizeContext(rerankedDocuments, request.getContextOptimizationLevel());
            
            // Step 5: Generate response with optimized context
            String generatedResponse = generateResponse(request.getQuery(), optimizedContext, request);
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            // Convert RAGResponse.RAGDocument to AdvancedRAGResponse.RAGDocument
            List<AdvancedRAGResponse.RAGDocument> convertedDocuments = rerankedDocuments.stream()
                .map(doc -> AdvancedRAGResponse.RAGDocument.builder()
                    .id(doc.getId())
                    .content(doc.getContent())
                    .title(doc.getTitle())
                    .type(doc.getType())
                    .score(doc.getScore())
                    .similarity(doc.getSimilarity())
                    .metadata(doc.getMetadata())
                    .source(doc.getSource())
                    .createdAt(doc.getCreatedAt())
                    .author(doc.getAuthor())
                    .tags(doc.getTags())
                    .category(doc.getType()) // Use type as category
                    .wordCount(doc.getWordCount())
                    .language(doc.getLanguage())
                    .build())
                .collect(Collectors.toList());
            
            return AdvancedRAGResponse.builder()
                .query(request.getQuery())
                .expandedQueries(expandedQueries)
                .response(generatedResponse)
                .context(optimizedContext)
                .documents(convertedDocuments)
                .totalDocuments(convertedDocuments.size())
                .usedDocuments(Math.min(convertedDocuments.size(), request.getMaxDocuments()))
                .relevanceScores(extractRelevanceScores(rerankedDocuments))
                .confidenceScore(calculateConfidence(rerankedDocuments))
                .processingTimeMs(processingTime)
                .success(true)
                .rerankingStrategy(request.getRerankingStrategy())
                .expansionLevel(request.getExpansionLevel())
                .contextOptimizationLevel(request.getContextOptimizationLevel())
                .metadata(createMetadata(request, processingTime))
                .build();
                
        } catch (Exception e) {
            log.error("Error performing advanced RAG", e);
            return AdvancedRAGResponse.builder()
                .query(request.getQuery())
                .response("Error processing request: " + e.getMessage())
                .success(false)
                .errorMessage(e.getMessage())
                .build();
        }
    }

    /**
     * Expand query using AI to generate related queries
     */
    private List<String> expandQuery(String originalQuery, int expansionLevel) {
        try {
            String expansionPrompt = String.format(
                "Generate %d related queries for: '%s'. " +
                "Include synonyms, alternative phrasings, and related concepts. " +
                "Return only the queries, one per line.",
                expansionLevel, originalQuery
            );
            
            String response = aiCoreService.generateText(expansionPrompt);
            List<String> expandedQueries = Arrays.stream(response.split("\n"))
                .map(String::trim)
                .filter(q -> !q.isEmpty() && !q.equals(originalQuery))
                .limit(expansionLevel)
                .collect(Collectors.toList());
            
            // Always include original query
            expandedQueries.add(0, originalQuery);
            return expandedQueries;
            
        } catch (Exception e) {
            log.warn("Query expansion failed, using original query only", e);
            return Collections.singletonList(originalQuery);
        }
    }

    /**
     * Perform search with multiple strategies
     */
    private List<RAGResponse> performMultiStrategySearch(List<String> queries, AdvancedRAGRequest request) {
        List<CompletableFuture<RAGResponse>> futures = queries.stream()
            .map(query -> CompletableFuture.supplyAsync(() -> {
                try {
                    RAGRequest.RAGRequestBuilder ragRequestBuilder = RAGRequest.builder()
                        .query(query)
                        .limit(request.getMaxResults())
                        .enableHybridSearch(request.getEnableHybridSearch())
                        .enableContextualSearch(request.getEnableContextualSearch())
                        .categories(request.getCategories())
                        .filters(request.getFilters());

                    if (request.getEntityType() != null && !request.getEntityType().isBlank()) {
                        ragRequestBuilder.entityType(request.getEntityType());
                    }

                    if (request.getSimilarityThreshold() != null) {
                        ragRequestBuilder.threshold(request.getSimilarityThreshold());
                    }

                    if (request.getContext() != null && !request.getContext().isBlank()) {
                        ragRequestBuilder.context(Map.of("userContext", request.getContext()));
                    }

                    if (request.getMetadata() != null && !request.getMetadata().isEmpty()) {
                        ragRequestBuilder.metadata(request.getMetadata());
                    }

                    RAGRequest ragRequest = ragRequestBuilder.build();
                    
                    return ragService.performRag(ragRequest);
                } catch (Exception e) {
                    log.warn("Search failed for query: {}", query, e);
                    return RAGResponse.builder()
                        .originalQuery(query)
                        .success(false)
                        .errorMessage(e.getMessage())
                        .build();
                }
            }))
            .collect(Collectors.toList());
        
        return futures.stream()
            .map(CompletableFuture::join)
            .filter(RAGResponse::getSuccess)
            .collect(Collectors.toList());
    }

    /**
     * Re-rank documents based on strategy
     */
    private List<RAGResponse.RAGDocument> rerankDocuments(
        List<RAGResponse> searchResults, String originalQuery, String strategy) {
        
        List<RAGResponse.RAGDocument> allDocuments = searchResults.stream()
            .flatMap(response -> response.getDocuments().stream())
            .distinct()
            .collect(Collectors.toList());
        
        switch (strategy.toLowerCase()) {
            case "semantic":
                return rerankBySemanticSimilarity(allDocuments, originalQuery);
            case "hybrid":
                return rerankByHybridScore(allDocuments, originalQuery);
            case "diversity":
                return rerankByDiversity(allDocuments);
            default:
                return rerankByScore(allDocuments);
        }
    }

    /**
     * Re-rank by semantic similarity
     */
    private List<RAGResponse.RAGDocument> rerankBySemanticSimilarity(
        List<RAGResponse.RAGDocument> documents, String query) {
        
        try {
            // Get query embedding
            AIEmbeddingRequest queryRequest = AIEmbeddingRequest.builder()
                .text(query)
                .build();
            List<Double> queryEmbedding = aiEmbeddingService.generateEmbedding(queryRequest).getEmbedding();
            
            // Calculate semantic similarity for each document
            return documents.stream()
                .map(doc -> {
                    try {
                        AIEmbeddingRequest docRequest = AIEmbeddingRequest.builder()
                            .text(doc.getContent())
                            .build();
                        List<Double> docEmbedding = aiEmbeddingService.generateEmbedding(docRequest).getEmbedding();
                        double similarity = calculateCosineSimilarity(queryEmbedding, docEmbedding);
                        doc.setSimilarity(similarity);
                        return doc;
                    } catch (Exception e) {
                        log.warn("Failed to calculate similarity for document: {}", doc.getId(), e);
                        return doc;
                    }
                })
                .sorted((d1, d2) -> Double.compare(d2.getSimilarity(), d1.getSimilarity()))
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.warn("Semantic re-ranking failed, using original order", e);
            return documents;
        }
    }

    /**
     * Re-rank by hybrid score (combining multiple factors)
     */
    private List<RAGResponse.RAGDocument> rerankByHybridScore(
        List<RAGResponse.RAGDocument> documents, String query) {
        
        return documents.stream()
            .map(doc -> {
                double hybridScore = calculateHybridScore(doc, query);
                doc.setSimilarity(hybridScore);
                return doc;
            })
            .sorted((d1, d2) -> Double.compare(d2.getSimilarity(), d1.getSimilarity()))
            .collect(Collectors.toList());
    }

    /**
     * Re-rank by diversity to ensure varied results
     */
    private List<RAGResponse.RAGDocument> rerankByDiversity(List<RAGResponse.RAGDocument> documents) {
        List<RAGResponse.RAGDocument> diverse = new ArrayList<>();
        Set<String> usedTypes = new HashSet<>();
        
        // First pass: select diverse types
        for (RAGResponse.RAGDocument doc : documents) {
            if (!usedTypes.contains(doc.getType())) {
                diverse.add(doc);
                usedTypes.add(doc.getType());
            }
        }
        
        // Second pass: add remaining documents
        for (RAGResponse.RAGDocument doc : documents) {
            if (!diverse.contains(doc)) {
                diverse.add(doc);
            }
        }
        
        return diverse;
    }

    /**
     * Re-rank by original score
     */
    private List<RAGResponse.RAGDocument> rerankByScore(List<RAGResponse.RAGDocument> documents) {
        return documents.stream()
            .sorted((d1, d2) -> Double.compare(d2.getScore(), d1.getScore()))
            .collect(Collectors.toList());
    }

    /**
     * Optimize context for better generation
     */
    private String optimizeContext(List<RAGResponse.RAGDocument> documents, String level) {
        if (documents.isEmpty()) {
            return "";
        }
        
        switch (level.toLowerCase()) {
            case "high":
                return optimizeContextHigh(documents);
            case "medium":
                return optimizeContextMedium(documents);
            case "low":
                return optimizeContextLow(documents);
            default:
                return documents.stream()
                    .map(RAGResponse.RAGDocument::getContent)
                    .collect(Collectors.joining("\n\n"));
        }
    }

    /**
     * High-level context optimization
     */
    private String optimizeContextHigh(List<RAGResponse.RAGDocument> documents) {
        try {
            String context = documents.stream()
                .map(RAGResponse.RAGDocument::getContent)
                .collect(Collectors.joining("\n\n"));
            
            String optimizationPrompt = String.format(
                "Optimize this context for better AI generation. " +
                "Remove redundancy, improve clarity, and maintain key information:\n\n%s",
                context
            );
            
            return aiCoreService.generateText(optimizationPrompt);
            
        } catch (Exception e) {
            log.warn("High-level context optimization failed", e);
            return optimizeContextMedium(documents);
        }
    }

    /**
     * Medium-level context optimization
     */
    private String optimizeContextMedium(List<RAGResponse.RAGDocument> documents) {
        return documents.stream()
            .sorted((d1, d2) -> Double.compare(d2.getScore(), d1.getScore()))
            .limit(5) // Top 5 documents
            .map(RAGResponse.RAGDocument::getContent)
            .collect(Collectors.joining("\n\n"));
    }

    /**
     * Low-level context optimization
     */
    private String optimizeContextLow(List<RAGResponse.RAGDocument> documents) {
        return documents.stream()
            .map(RAGResponse.RAGDocument::getContent)
            .collect(Collectors.joining("\n\n"));
    }

    /**
     * Generate response using optimized context
     */
    private String generateResponse(String query, String context, AdvancedRAGRequest request) {
        try {
            String prompt = String.format(
                "Based on the following context, answer the question: %s\n\n" +
                "Context:\n%s\n\n" +
                "Provide a comprehensive, accurate answer based on the context provided.",
                query, context
            );
            
            return aiCoreService.generateText(prompt);
            
        } catch (Exception e) {
            log.error("Response generation failed", e);
            return "Unable to generate response at this time.";
        }
    }

    /**
     * Calculate cosine similarity between two vectors
     */
    private double calculateCosineSimilarity(List<Double> vector1, List<Double> vector2) {
        if (vector1.size() != vector2.size()) {
            return 0.0;
        }
        
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        
        for (int i = 0; i < vector1.size(); i++) {
            dotProduct += vector1.get(i) * vector2.get(i);
            norm1 += vector1.get(i) * vector1.get(i);
            norm2 += vector2.get(i) * vector2.get(i);
        }
        
        if (norm1 == 0.0 || norm2 == 0.0) {
            return 0.0;
        }
        
        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    /**
     * Calculate hybrid score combining multiple factors
     */
    private double calculateHybridScore(RAGResponse.RAGDocument doc, String query) {
        double score = doc.getScore();
        double similarity = doc.getSimilarity();
        
        // Combine original score with similarity
        return (score * 0.6) + (similarity * 0.4);
    }

    /**
     * Extract relevance scores from documents
     */
    private List<Double> extractRelevanceScores(List<RAGResponse.RAGDocument> documents) {
        return documents.stream()
            .map(RAGResponse.RAGDocument::getSimilarity)
            .collect(Collectors.toList());
    }

    /**
     * Calculate overall confidence score
     */
    private double calculateConfidence(List<RAGResponse.RAGDocument> documents) {
        if (documents.isEmpty()) {
            return 0.0;
        }
        
        double avgScore = documents.stream()
            .mapToDouble(RAGResponse.RAGDocument::getScore)
            .average()
            .orElse(0.0);
        
        double avgSimilarity = documents.stream()
            .mapToDouble(RAGResponse.RAGDocument::getSimilarity)
            .average()
            .orElse(0.0);
        
        return (avgScore + avgSimilarity) / 2.0;
    }

    /**
     * Create metadata for the response
     */
    private Map<String, Object> createMetadata(AdvancedRAGRequest request, long processingTime) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("timestamp", System.currentTimeMillis());
        metadata.put("processingTimeMs", processingTime);
        metadata.put("expansionLevel", request.getExpansionLevel());
        metadata.put("rerankingStrategy", request.getRerankingStrategy());
        metadata.put("contextOptimizationLevel", request.getContextOptimizationLevel());
        metadata.put("maxDocuments", request.getMaxDocuments());
        metadata.put("enableHybridSearch", request.getEnableHybridSearch());
        metadata.put("enableContextualSearch", request.getEnableContextualSearch());
        return metadata;
    }
}