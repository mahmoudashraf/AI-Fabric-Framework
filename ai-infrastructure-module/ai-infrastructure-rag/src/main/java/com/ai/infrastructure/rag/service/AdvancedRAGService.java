package com.ai.infrastructure.rag.service;

import com.ai.infrastructure.dto.AIEmbeddingRequest;
import com.ai.infrastructure.dto.AdvancedRAGRequest;
import com.ai.infrastructure.dto.AdvancedRAGResponse;
import com.ai.infrastructure.rag.dto.RAGRequest;
import com.ai.infrastructure.rag.dto.RAGResponse;
import com.ai.infrastructure.core.AISearchService;
import com.ai.infrastructure.core.AIEmbeddingService;
import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.rag.spi.RAGProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Advanced RAG Service with query expansion, re-ranking, and context optimization.
 * 
 * <p>This service provides advanced RAG capabilities beyond basic retrieval:</p>
 * <ul>
 *   <li><strong>Query Expansion:</strong> Generates related queries using AI to improve recall</li>
 *   <li><strong>Multi-Strategy Search:</strong> Executes searches with different strategies in parallel</li>
 *   <li><strong>Re-ranking:</strong> Re-ranks results using semantic, hybrid, or diversity strategies</li>
 *   <li><strong>Context Optimization:</strong> Optimizes context for better generation quality</li>
 * </ul>
 * 
 * <p><strong>Usage:</strong></p>
 * <pre>{@code
 * AdvancedRAGRequest request = AdvancedRAGRequest.builder()
 *     .query("What are the best practices for microservices?")
 *     .expansionLevel(3)
 *     .rerankingStrategy("semantic")
 *     .contextOptimizationLevel("high")
 *     .build();
 *     
 * AdvancedRAGResponse response = advancedRAGService.performAdvancedRAG(request);
 * }</pre>
 * 
 * <p><strong>Thread Safety:</strong> This service is thread-safe and uses parallel
 * processing for improved performance.</p>
 * 
 * @author AI Infrastructure Team
 * @version 2.0.0
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdvancedRAGService {

    // =========================================================================
    // Constants
    // =========================================================================
    
    // Re-ranking strategies
    private static final String STRATEGY_SEMANTIC = "semantic";
    private static final String STRATEGY_HYBRID = "hybrid";
    private static final String STRATEGY_DIVERSITY = "diversity";
    
    // Context optimization levels
    private static final String LEVEL_HIGH = "high";
    private static final String LEVEL_MEDIUM = "medium";
    private static final String LEVEL_LOW = "low";
    
    // Defaults
    private static final int TOP_DOCUMENTS_FOR_MEDIUM_OPTIMIZATION = 5;
    private static final int MAX_CONTEXT_LENGTH = 500;
    
    // Scoring weights
    private static final double SCORE_WEIGHT = 0.6;
    private static final double SIMILARITY_WEIGHT = 0.4;
    
    // Prompt templates
    private static final String EXPANSION_PROMPT_TEMPLATE = 
        "Generate %d related queries for: '%s'. " +
        "Include synonyms, alternative phrasings, and related concepts. " +
        "Return only the queries, one per line.";
    
    private static final String OPTIMIZATION_PROMPT_TEMPLATE = 
        "Optimize this context for better AI generation. " +
        "Remove redundancy, improve clarity, and maintain key information:\n\n%s";
    
    private static final String RESPONSE_GENERATION_PROMPT_TEMPLATE = 
        "Based on the following context, answer the question: %s\n\n" +
        "Context:\n%s\n\n" +
        "Provide a comprehensive, accurate answer based on the context provided.";
    
    // Messages
    private static final String ERROR_MESSAGE_TEMPLATE = "Error processing request: %s";
    private static final String UNABLE_TO_GENERATE_MESSAGE = "Unable to generate response at this time.";
    
    // Metadata keys
    private static final String METADATA_KEY_TIMESTAMP = "timestamp";
    private static final String METADATA_KEY_PROCESSING_TIME_MS = "processingTimeMs";
    private static final String METADATA_KEY_EXPANSION_LEVEL = "expansionLevel";
    private static final String METADATA_KEY_RERANKING_STRATEGY = "rerankingStrategy";
    private static final String METADATA_KEY_CONTEXT_OPTIMIZATION_LEVEL = "contextOptimizationLevel";
    private static final String METADATA_KEY_MAX_DOCUMENTS = "maxDocuments";
    private static final String METADATA_KEY_ENABLE_HYBRID_SEARCH = "enableHybridSearch";
    private static final String METADATA_KEY_ENABLE_CONTEXTUAL_SEARCH = "enableContextualSearch";
    private static final String METADATA_KEY_USER_CONTEXT = "userContext";

    // =========================================================================
    // Dependencies
    // =========================================================================

    private final AISearchService aiSearchService;
    private final AIEmbeddingService aiEmbeddingService;
    private final AICoreService aiCoreService;
    private final RAGProvider ragProvider;

    // =========================================================================
    // Public Methods
    // =========================================================================

    /**
     * Perform advanced RAG with query expansion and re-ranking.
     * 
     * <p>This method executes a multi-stage RAG process:</p>
     * <ol>
     *   <li>Expand the query into multiple related queries</li>
     *   <li>Execute parallel searches with different strategies</li>
     *   <li>Re-rank results using the specified strategy</li>
     *   <li>Optimize context for generation</li>
     *   <li>Generate the final response</li>
     * </ol>
     * 
     * @param request the advanced RAG request with all configuration options
     * @return AdvancedRAGResponse with expanded queries, re-ranked documents, and generated response
     */
    public AdvancedRAGResponse performAdvancedRAG(AdvancedRAGRequest request) {
        log.info("Performing advanced RAG for query: {}", request.getQuery());
        
        try {
            long startTime = System.currentTimeMillis();
            
            List<String> expandedQueries = expandQuery(request.getQuery(), request.getExpansionLevel());
            log.debug("Expanded queries: {}", expandedQueries);
            
            List<RAGResponse> searchResults = performMultiStrategySearch(expandedQueries, request);
            
            List<RAGResponse.RetrievedDocument> rerankedDocuments = rerankDocuments(
                searchResults, request.getQuery(), request.getRerankingStrategy()
            );
            
            String optimizedContext = optimizeContext(
                rerankedDocuments, request.getContextOptimizationLevel());
            
            String generatedResponse = generateResponse(
                request.getQuery(), optimizedContext, request);
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            List<AdvancedRAGResponse.RAGDocument> convertedDocuments = rerankedDocuments.stream()
                .map(this::convertToAdvancedDocument)
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
                .response(String.format(ERROR_MESSAGE_TEMPLATE, e.getMessage()))
                .success(false)
                .errorMessage(e.getMessage())
                .build();
        }
    }

    // =========================================================================
    // Private Methods - Query Expansion
    // =========================================================================

    private List<String> expandQuery(String originalQuery, int expansionLevel) {
        try {
            String expansionPrompt = String.format(EXPANSION_PROMPT_TEMPLATE, 
                expansionLevel, originalQuery);
            
            String response = aiCoreService.generateText(expansionPrompt);
            List<String> expandedQueries = Arrays.stream(response.split("\n"))
                .map(String::trim)
                .filter(q -> !q.isEmpty() && !q.equals(originalQuery))
                .limit(expansionLevel)
                .collect(Collectors.toList());
            
            expandedQueries.add(0, originalQuery);
            return expandedQueries;
            
        } catch (Exception e) {
            log.warn("Query expansion failed, using original query only", e);
            return Collections.singletonList(originalQuery);
        }
    }

    // =========================================================================
    // Private Methods - Search
    // =========================================================================

    private List<RAGResponse> performMultiStrategySearch(List<String> queries, 
            AdvancedRAGRequest request) {
        List<CompletableFuture<RAGResponse>> futures = queries.stream()
            .map(query -> CompletableFuture.supplyAsync(() -> 
                executeSearch(query, request)))
            .collect(Collectors.toList());
        
        return futures.stream()
            .map(CompletableFuture::join)
            .filter(response -> Boolean.TRUE.equals(response.getSuccess()))
            .collect(Collectors.toList());
    }
    
    private RAGResponse executeSearch(String query, AdvancedRAGRequest request) {
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
                ragRequestBuilder.context(Map.of(METADATA_KEY_USER_CONTEXT, request.getContext()));
            }

            if (request.getMetadata() != null && !request.getMetadata().isEmpty()) {
                ragRequestBuilder.metadata(request.getMetadata());
            }

            RAGRequest ragRequest = ragRequestBuilder.build();
            
            return ragProvider.retrieve(ragRequest);
        } catch (Exception e) {
            log.warn("Search failed for query: {}", query, e);
            return RAGResponse.builder()
                .originalQuery(query)
                .success(false)
                .errorMessage(e.getMessage())
                .build();
        }
    }

    // =========================================================================
    // Private Methods - Re-ranking
    // =========================================================================

    private List<RAGResponse.RetrievedDocument> rerankDocuments(
            List<RAGResponse> searchResults, String originalQuery, String strategy) {
        
        List<RAGResponse.RetrievedDocument> allDocuments = searchResults.stream()
            .flatMap(response -> response.getDocuments().stream())
            .distinct()
            .collect(Collectors.toList());
        
        return switch (strategy.toLowerCase()) {
            case STRATEGY_SEMANTIC -> rerankBySemanticSimilarity(allDocuments, originalQuery);
            case STRATEGY_HYBRID -> rerankByHybridScore(allDocuments, originalQuery);
            case STRATEGY_DIVERSITY -> rerankByDiversity(allDocuments);
            default -> rerankByScore(allDocuments);
        };
    }

    private List<RAGResponse.RetrievedDocument> rerankBySemanticSimilarity(
            List<RAGResponse.RetrievedDocument> documents, String query) {
        
        try {
            AIEmbeddingRequest queryRequest = AIEmbeddingRequest.builder()
                .text(query)
                .build();
            List<Double> queryEmbedding = aiEmbeddingService.generateEmbedding(queryRequest)
                .getEmbedding();
            
            return documents.stream()
                .map(doc -> {
                    try {
                        AIEmbeddingRequest docRequest = AIEmbeddingRequest.builder()
                            .text(doc.getContent())
                            .build();
                        List<Double> docEmbedding = aiEmbeddingService
                            .generateEmbedding(docRequest).getEmbedding();
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

    private List<RAGResponse.RetrievedDocument> rerankByHybridScore(
            List<RAGResponse.RetrievedDocument> documents, String query) {
        
        return documents.stream()
            .map(doc -> {
                double hybridScore = calculateHybridScore(doc);
                doc.setSimilarity(hybridScore);
                return doc;
            })
            .sorted((d1, d2) -> Double.compare(d2.getSimilarity(), d1.getSimilarity()))
            .collect(Collectors.toList());
    }

    private List<RAGResponse.RetrievedDocument> rerankByDiversity(
            List<RAGResponse.RetrievedDocument> documents) {
        List<RAGResponse.RetrievedDocument> diverse = new ArrayList<>();
        Set<String> usedTypes = new HashSet<>();
        
        for (RAGResponse.RetrievedDocument doc : documents) {
            if (!usedTypes.contains(doc.getType())) {
                diverse.add(doc);
                usedTypes.add(doc.getType());
            }
        }
        
        for (RAGResponse.RetrievedDocument doc : documents) {
            if (!diverse.contains(doc)) {
                diverse.add(doc);
            }
        }
        
        return diverse;
    }

    private List<RAGResponse.RetrievedDocument> rerankByScore(
            List<RAGResponse.RetrievedDocument> documents) {
        return documents.stream()
            .sorted((d1, d2) -> Double.compare(d2.getScore(), d1.getScore()))
            .collect(Collectors.toList());
    }

    // =========================================================================
    // Private Methods - Context Optimization
    // =========================================================================

    private String optimizeContext(List<RAGResponse.RetrievedDocument> documents, String level) {
        if (documents.isEmpty()) {
            return "";
        }
        
        return switch (level.toLowerCase()) {
            case LEVEL_HIGH -> optimizeContextHigh(documents);
            case LEVEL_MEDIUM -> optimizeContextMedium(documents);
            case LEVEL_LOW -> optimizeContextLow(documents);
            default -> documents.stream()
                .map(RAGResponse.RetrievedDocument::getContent)
                .collect(Collectors.joining("\n\n"));
        };
    }

    private String optimizeContextHigh(List<RAGResponse.RetrievedDocument> documents) {
        try {
            String context = documents.stream()
                .map(RAGResponse.RetrievedDocument::getContent)
                .collect(Collectors.joining("\n\n"));
            
            String optimizationPrompt = String.format(OPTIMIZATION_PROMPT_TEMPLATE, context);
            
            return aiCoreService.generateText(optimizationPrompt);
            
        } catch (Exception e) {
            log.warn("High-level context optimization failed", e);
            return optimizeContextMedium(documents);
        }
    }

    private String optimizeContextMedium(List<RAGResponse.RetrievedDocument> documents) {
        return documents.stream()
            .sorted((d1, d2) -> Double.compare(d2.getScore(), d1.getScore()))
            .limit(TOP_DOCUMENTS_FOR_MEDIUM_OPTIMIZATION)
            .map(RAGResponse.RetrievedDocument::getContent)
            .collect(Collectors.joining("\n\n"));
    }

    private String optimizeContextLow(List<RAGResponse.RetrievedDocument> documents) {
        return documents.stream()
            .map(RAGResponse.RetrievedDocument::getContent)
            .collect(Collectors.joining("\n\n"));
    }

    // =========================================================================
    // Private Methods - Response Generation
    // =========================================================================

    private String generateResponse(String query, String context, AdvancedRAGRequest request) {
        try {
            String prompt = String.format(RESPONSE_GENERATION_PROMPT_TEMPLATE, query, context);
            
            return aiCoreService.generateText(prompt);
            
        } catch (Exception e) {
            log.error("Response generation failed", e);
            return UNABLE_TO_GENERATE_MESSAGE;
        }
    }

    // =========================================================================
    // Private Methods - Utility
    // =========================================================================

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

    private double calculateHybridScore(RAGResponse.RetrievedDocument doc) {
        double score = doc.getScore();
        double similarity = doc.getSimilarity();
        
        return (score * SCORE_WEIGHT) + (similarity * SIMILARITY_WEIGHT);
    }

    private List<Double> extractRelevanceScores(List<RAGResponse.RetrievedDocument> documents) {
        return documents.stream()
            .map(RAGResponse.RetrievedDocument::getSimilarity)
            .collect(Collectors.toList());
    }

    private double calculateConfidence(List<RAGResponse.RetrievedDocument> documents) {
        if (documents.isEmpty()) {
            return 0.0;
        }
        
        double avgScore = documents.stream()
            .mapToDouble(RAGResponse.RetrievedDocument::getScore)
            .average()
            .orElse(0.0);
        
        double avgSimilarity = documents.stream()
            .mapToDouble(RAGResponse.RetrievedDocument::getSimilarity)
            .average()
            .orElse(0.0);
        
        return (avgScore + avgSimilarity) / 2.0;
    }

    private Map<String, Object> createMetadata(AdvancedRAGRequest request, long processingTime) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(METADATA_KEY_TIMESTAMP, System.currentTimeMillis());
        metadata.put(METADATA_KEY_PROCESSING_TIME_MS, processingTime);
        metadata.put(METADATA_KEY_EXPANSION_LEVEL, request.getExpansionLevel());
        metadata.put(METADATA_KEY_RERANKING_STRATEGY, request.getRerankingStrategy());
        metadata.put(METADATA_KEY_CONTEXT_OPTIMIZATION_LEVEL, request.getContextOptimizationLevel());
        metadata.put(METADATA_KEY_MAX_DOCUMENTS, request.getMaxDocuments());
        metadata.put(METADATA_KEY_ENABLE_HYBRID_SEARCH, request.getEnableHybridSearch());
        metadata.put(METADATA_KEY_ENABLE_CONTEXTUAL_SEARCH, request.getEnableContextualSearch());
        return metadata;
    }
    
    private AdvancedRAGResponse.RAGDocument convertToAdvancedDocument(RAGResponse.RetrievedDocument doc) {
        return AdvancedRAGResponse.RAGDocument.builder()
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
            .category(doc.getType())
            .wordCount(doc.getWordCount())
            .language(doc.getLanguage())
            .build();
    }
}
