package com.ai.fabric.runtime.search;

import ai.fabric.core.AISearchService;
import ai.fabric.dto.AISearchRequest;
import ai.fabric.dto.AISearchResponse;
import ai.fabric.dto.RAGRequest;
import ai.fabric.rag.VectorDatabaseService;
import ai.fabric.rag.RetrievalCapabilities;
import ai.fabric.rag.source.ResolvedKnowledgeSource;
import ai.fabric.rag.source.SearchSource;
import com.ai.fabric.runtime.documents.DocumentActiveVersionFilter;

import java.util.List;

final class SharedIndexSearchSource implements SearchSource {

    private final ResolvedKnowledgeSource source;
    private final AISearchService searchService;
    private final VectorDatabaseService vectorDatabaseService;
    private final DocumentActiveVersionFilter activeVersionFilter;

    SharedIndexSearchSource(ResolvedKnowledgeSource source,
                            AISearchService searchService,
                            VectorDatabaseService vectorDatabaseService) {
        this(source, searchService, vectorDatabaseService, null);
    }

    SharedIndexSearchSource(ResolvedKnowledgeSource source,
                            AISearchService searchService,
                            VectorDatabaseService vectorDatabaseService,
                            DocumentActiveVersionFilter activeVersionFilter) {
        this.source = source;
        this.searchService = searchService;
        this.vectorDatabaseService = vectorDatabaseService;
        this.activeVersionFilter = activeVersionFilter;
    }

    @Override
    public ResolvedKnowledgeSource source() {
        return source;
    }

    @Override
    public boolean isEligible(RAGRequest request) {
        if (!source.isEnabled() || !SearchSourceResultSupport.matchesRequestedDocumentSelection(source, request)) {
            return false;
        }
        if (source.getAuthModes().isEmpty()) {
            return true;
        }
        String authMode = request != null && request.getAuthContext() != null ? request.getAuthContext().getAuthMode() : null;
        return authMode != null && source.getAuthModes().stream().anyMatch(candidate -> candidate.equalsIgnoreCase(authMode));
    }

    @Override
    public AISearchResponse search(List<Double> queryVector, RAGRequest ragRequest, AISearchRequest baseSearchRequest) {
        AISearchRequest scopedRequest = SearchSourceResultSupport.scopedRequest(baseSearchRequest, source,
            ragRequest.getRetrievalProfile() == null ? 4 : 1);
        if (scopedRequest.getEntityType() == null || scopedRequest.getEntityType().isBlank()) {
            return AISearchResponse.builder()
                .results(List.of())
                .totalResults(0)
                .processingTimeMs(0L)
                .query(baseSearchRequest.getQuery())
                .build();
        }
        AISearchResponse response = executeSearch(queryVector, ragRequest, scopedRequest);
        return SearchSourceResultSupport.filterAndDecorate(response, source,
            ragRequest.getRetrievalProfile() == null ? source.getFilters() : scopedRequest.getMetadata(),
            activeVersionFilter == null ? ignored -> true : metadata -> activeVersionFilter.accepts(scopedRequest.getEntityType(), metadata));
    }

    @Override
    public RetrievalCapabilities retrievalCapabilities(RAGRequest request) {
        return vectorDatabaseService.retrievalCapabilities(source.getEntityType());
    }

    @Override
    public AISearchResponse keywordSearch(RAGRequest request, AISearchRequest baseSearchRequest) {
        AISearchRequest scoped = SearchSourceResultSupport.scopedRequest(baseSearchRequest, source, 1);
        AISearchResponse response = vectorDatabaseService.keywordSearch(request.getQuery(), scoped);
        return SearchSourceResultSupport.filterAndDecorate(response, source, scoped.getMetadata(),
            activeVersionFilter == null ? ignored -> true : metadata -> activeVersionFilter.accepts(scoped.getEntityType(), metadata));
    }

    private AISearchResponse executeSearch(List<Double> queryVector, RAGRequest ragRequest, AISearchRequest scopedRequest) {
        if (Boolean.TRUE.equals(ragRequest.getEnableHybridSearch())) {
            return vectorDatabaseService.hybridSearch(queryVector, ragRequest.getQuery(), scopedRequest);
        }
        if (Boolean.TRUE.equals(ragRequest.getEnableContextualSearch())) {
            AISearchRequest contextualRequest = AISearchRequest.builder()
                .query(scopedRequest.getQuery())
                .entityType(scopedRequest.getEntityType())
                .limit(scopedRequest.getLimit())
                .threshold(scopedRequest.getThreshold())
                .filters(scopedRequest.getFilters())
                .sortBy(scopedRequest.getSortBy())
                .context(ragRequest.getContext() != null ? ragRequest.getContext().toString() : null)
                .metadata(scopedRequest.getMetadata())
                .build();
            return vectorDatabaseService.search(queryVector, contextualRequest);
        }
        return searchService.search(queryVector, scopedRequest);
    }
}
