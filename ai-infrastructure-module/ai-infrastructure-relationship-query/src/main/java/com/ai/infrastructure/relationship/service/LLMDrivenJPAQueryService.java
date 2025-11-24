package com.ai.infrastructure.relationship.service;

import com.ai.infrastructure.core.AIEmbeddingService;
import com.ai.infrastructure.dto.AIEmbeddingRequest;
import com.ai.infrastructure.dto.AIEmbeddingResponse;
import com.ai.infrastructure.dto.AISearchRequest;
import com.ai.infrastructure.dto.AISearchResponse;
import com.ai.infrastructure.dto.RAGResponse;
import com.ai.infrastructure.relationship.cache.QueryCache;
import com.ai.infrastructure.relationship.config.RelationshipModuleMetadata;
import com.ai.infrastructure.relationship.config.RelationshipQueryProperties;
import com.ai.infrastructure.relationship.dto.JpqlQuery;
import com.ai.infrastructure.relationship.dto.RelationshipQueryPlan;
import com.ai.infrastructure.relationship.model.QueryMode;
import com.ai.infrastructure.relationship.model.QueryOptions;
import com.ai.infrastructure.relationship.model.ReturnMode;
import com.ai.infrastructure.relationship.validation.RelationshipQueryValidator;
import com.ai.infrastructure.relationship.metrics.QueryMetrics;
import com.ai.infrastructure.rag.VectorDatabaseService;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Primary entrypoint that orchestrates LLM planning, JPQL execution, and optional vector reranking.
 */
@Slf4j
public class LLMDrivenJPAQueryService {
    private static final ObjectMapper METADATA_MAPPER = new ObjectMapper();

    private final RelationshipQueryPlanner planner;
    private final DynamicJPAQueryBuilder queryBuilder;
    private final RelationshipQueryValidator validator;
    private final RelationshipQueryProperties properties;
    private final RelationshipModuleMetadata moduleMetadata;
    private final RelationshipTraversalService jpaTraversalService;
    private final RelationshipTraversalService metadataTraversalService;
    private final AISearchableEntityRepository entityRepository;
    private final VectorDatabaseService vectorDatabaseService;
    private final AIEmbeddingService embeddingService;
    private final QueryCache queryCache;
    private final QueryMetrics queryMetrics;

    public LLMDrivenJPAQueryService(RelationshipQueryPlanner planner,
                                    DynamicJPAQueryBuilder queryBuilder,
                                    RelationshipQueryValidator validator,
                                    RelationshipQueryProperties properties,
                                    RelationshipModuleMetadata moduleMetadata,
                                    RelationshipTraversalService jpaTraversalService,
                                      RelationshipTraversalService metadataTraversalService,
                                    AISearchableEntityRepository entityRepository,
                                    VectorDatabaseService vectorDatabaseService,
                                      AIEmbeddingService embeddingService,
                                      QueryCache queryCache,
                                      QueryMetrics queryMetrics) {
        this.planner = planner;
        this.queryBuilder = queryBuilder;
        this.validator = validator;
        this.properties = properties;
        this.moduleMetadata = moduleMetadata;
        this.jpaTraversalService = jpaTraversalService;
        this.metadataTraversalService = metadataTraversalService;
        this.entityRepository = entityRepository;
        this.vectorDatabaseService = vectorDatabaseService;
        this.embeddingService = embeddingService;
        this.queryCache = queryCache;
        this.queryMetrics = queryMetrics;
    }

    public RAGResponse executeRelationshipQuery(String query, List<String> entityTypes) {
        return executeRelationshipQuery(query, entityTypes, QueryOptions.defaults());
    }

    public RAGResponse executeRelationshipQuery(String query,
                                                List<String> entityTypes,
                                                QueryOptions options) {
        long startNano = System.nanoTime();
        try {
            if (log.isInfoEnabled()) {
                log.info("[RelationshipQuery] Executing query='{}' entityTypes={}", query, entityTypes);
            }
            RelationshipQueryPlan plan = planner.planQuery(query, entityTypes);
            validator.validate(plan);
            if (log.isInfoEnabled()) {
                log.info("[RelationshipQuery] Planner output:\n{}", toJson(plan));
            }

            JpqlQuery jpqlQuery = queryBuilder.buildQuery(plan);
            if (jpqlQuery != null && log.isInfoEnabled()) {
                log.info("[RelationshipQuery] JPQL statement: {}\nParameters: {}", jpqlQuery.getJpql(), jpqlQuery.getParameters());
            }
            List<String> entityIds = executeTraversal(plan, jpqlQuery);
            QueryMode mode = resolveMode(plan, options);
            if (mode == QueryMode.ENHANCED) {
                entityIds = rerankWithVectors(plan, entityIds, options);
            }

            ReturnMode returnMode = resolveReturnMode(options);
            List<RAGResponse.RAGDocument> documents = materializeDocuments(plan, entityIds, returnMode, options);
            long duration = Math.max(0, (System.nanoTime() - startNano) / 1_000_000);

            recordExecutionMetrics(mode, duration, documents.size(), plan.isNeedsSemanticSearch() || mode == QueryMode.ENHANCED);
            updateCacheMetrics();

            RAGResponse response = RAGResponse.builder()
                .originalQuery(query)
                .entityType(plan.getPrimaryEntityType())
                .documents(documents)
                .totalResults(entityIds.size())
                .returnedResults(documents.size())
                .hybridSearchUsed(mode == QueryMode.ENHANCED)
                .success(!documents.isEmpty())
                .processingTimeMs(duration)
                .confidenceScore(plan.getConfidenceScore())
                .warnings(Collections.emptyList())
                .metadata(Map.of(
                    "plan", plan,
                    "mode", mode,
                    "timestamp", Instant.now().toString()
                ))
                .build();
            logResultArtifacts(query, entityTypes, plan, jpqlQuery, response);
            return response;
        } catch (RuntimeException ex) {
            long duration = Math.max(0, (System.nanoTime() - startNano) / 1_000_000);
            if (queryMetrics != null && queryMetrics.isEnabled()) {
                queryMetrics.recordExecutionFailure(duration);
            }
            throw ex;
        }
    }

    private List<String> executeTraversal(RelationshipQueryPlan plan, JpqlQuery query) {
        List<String> entityIds = executeJpaTraversal(plan, query);
        if (!entityIds.isEmpty() || !properties.isFallbackToMetadata()) {
            return entityIds;
        }
        return metadataTraversalService.traverse(plan, query);
    }

    private QueryMode resolveMode(RelationshipQueryPlan plan, QueryOptions options) {
        if (options != null && options.getForceMode() != null) {
            return options.getForceMode();
        }
        if (plan.isNeedsSemanticSearch() && moduleMetadata.vectorSearchEnabled() && vectorDependenciesPresent()) {
            return QueryMode.ENHANCED;
        }
        return properties.getDefaultQueryMode();
    }

    private boolean vectorDependenciesPresent() {
        return vectorDatabaseService != null && embeddingService != null;
    }

    private ReturnMode resolveReturnMode(QueryOptions options) {
        if (options != null && options.getReturnMode() != null) {
            return options.getReturnMode();
        }
        return properties.getDefaultReturnMode();
    }

    private List<RAGResponse.RAGDocument> materializeDocuments(RelationshipQueryPlan plan,
                                                               List<String> entityIds,
                                                               ReturnMode returnMode,
                                                               QueryOptions options) {
        List<String> limited = applyLimit(plan, entityIds, options);
        if (returnMode == ReturnMode.IDS) {
            return limited.stream()
                .map(id -> RAGResponse.RAGDocument.builder().id(id).build())
                .collect(Collectors.toList());
        }

        List<RAGResponse.RAGDocument> documents = new ArrayList<>();
        for (String entityId : limited) {
            entityRepository.findByEntityTypeAndEntityId(plan.getPrimaryEntityType(), entityId)
                .ifPresent(entity -> documents.add(RAGResponse.RAGDocument.builder()
                    .id(entityId)
                    .content(entity.getSearchableContent())
                    .metadata(parseMetadata(entity.getMetadata()))
                    .build()));
        }
        return documents;
    }

    private Map<String, Object> parseMetadata(String metadataJson) {
        if (!StringUtils.hasText(metadataJson)) {
            return Collections.emptyMap();
        }
        try {
            return METADATA_MAPPER.readValue(metadataJson, Map.class);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private List<String> applyLimit(RelationshipQueryPlan plan, List<String> entityIds, QueryOptions options) {
        if (entityIds.isEmpty()) {
            return entityIds;
        }
        int limit = Optional.ofNullable(options.getLimit())
            .or(() -> Optional.ofNullable(plan.getLimit()))
            .orElse(50);
        return entityIds.stream().limit(limit).collect(Collectors.toList());
    }

    private List<String> rerankWithVectors(RelationshipQueryPlan plan,
                                           List<String> entityIds,
                                           QueryOptions options) {
        if (!vectorDependenciesPresent() || !StringUtils.hasText(plan.getSemanticQuery())) {
            return entityIds;
        }
        try {
            String embeddingKey = QueryCache.hash(plan.getSemanticQuery());
            AIEmbeddingResponse embedding = null;
            if (queryCache.isEnabled()) {
                embedding = queryCache.getEmbedding(embeddingKey).orElse(null);
            }
            if (embedding == null) {
                embedding = embeddingService.generateEmbedding(
                    AIEmbeddingRequest.builder()
                        .text(plan.getSemanticQuery())
                        .entityType(plan.getPrimaryEntityType())
                        .build()
                );
                if (queryCache.isEnabled()) {
                    queryCache.putEmbedding(embeddingKey, embedding);
                }
            }
            AISearchResponse response = vectorDatabaseService.searchByEntityType(
                embedding.getEmbedding(),
                plan.getPrimaryEntityType(),
                Optional.ofNullable(options.getLimit()).orElse(25),
                Optional.ofNullable(options.getSimilarityThreshold())
                    .orElse(moduleMetadata.similarityThreshold())
            );
            if (response == null || CollectionUtils.isEmpty(response.getResults())) {
                return entityIds;
            }
            List<String> reranked = response.getResults().stream()
                .map(result -> {
                    Object entityId = result.get("entityId");
                    if (entityId == null) {
                        entityId = result.get("id");
                    }
                    return entityId != null ? entityId.toString() : null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
            if (reranked.isEmpty()) {
                return entityIds;
            }
            Set<String> unique = new LinkedHashSet<>(reranked);
            unique.addAll(entityIds);
            return new ArrayList<>(unique);
        } catch (Exception ex) {
            log.warn("Vector reranking failed: {}", ex.getMessage());
            return entityIds;
        }
    }

    private List<String> executeJpaTraversal(RelationshipQueryPlan plan, JpqlQuery query) {
        if (query == null || !StringUtils.hasText(query.getJpql())) {
            return Collections.emptyList();
        }
        String cacheKey = QueryCache.hash(query);
        if (queryCache.isEnabled()) {
            Optional<List<String>> cached = queryCache.getQueryResult(cacheKey);
            if (cached.isPresent()) {
                return cached.get();
            }
        }
        List<String> entityIds = jpaTraversalService.traverse(plan, query);
        if (queryCache.isEnabled()) {
            queryCache.putQueryResult(cacheKey, entityIds);
        }
        return entityIds;
    }

    private void recordExecutionMetrics(QueryMode mode, long latencyMs, int resultCount, boolean fallbackUsed) {
        if (queryMetrics != null && queryMetrics.isEnabled()) {
            queryMetrics.recordExecution(mode != null ? mode : QueryMode.STANDALONE, latencyMs, resultCount, fallbackUsed);
        }
    }

    private void updateCacheMetrics() {
        if (queryMetrics != null && queryMetrics.isEnabled()) {
            queryMetrics.updateCacheStats(
                queryCache.getPlanStats(),
                queryCache.getEmbeddingStats(),
                queryCache.getResultStats()
            );
        }
    }

    private void logResultArtifacts(String query,
                                    List<String> entityTypes,
                                    RelationshipQueryPlan plan,
                                    JpqlQuery jpqlQuery,
                                    RAGResponse response) {
        if (!log.isInfoEnabled() || response == null) {
            return;
        }
        log.info("[RelationshipQuery] Result summary -> query='{}', entityTypes={}, totalCandidates={}, returned={}, hybridMode={}",
            query,
            entityTypes,
            response.getTotalResults(),
            response.getReturnedResults(),
            Boolean.TRUE.equals(response.getHybridSearchUsed()));
        log.info("[RelationshipQuery] Document IDs: {}", response.getDocuments() == null ? List.of() : previewDocumentIds(response.getDocuments()));
        if (jpqlQuery != null) {
            log.info("[RelationshipQuery] JPQL (repeated for reference): {}\nParameters: {}", jpqlQuery.getJpql(), jpqlQuery.getParameters());
        }
        log.info("[RelationshipQuery] Serialized response:\n{}", toJson(response));
    }

    private List<String> previewDocumentIds(List<RAGResponse.RAGDocument> documents) {
        if (CollectionUtils.isEmpty(documents)) {
            return List.of();
        }
        return documents.stream()
            .map(RAGResponse.RAGDocument::getId)
            .filter(Objects::nonNull)
            .limit(5)
            .collect(Collectors.toList());
    }

    private String toJson(Object value) {
        if (value == null) {
            return "null";
        }
        try {
            return METADATA_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (Exception ex) {
            return value.toString();
        }
    }
}
