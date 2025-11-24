package com.ai.infrastructure.relationship.service;

import com.ai.infrastructure.core.AIEmbeddingService;
import com.ai.infrastructure.dto.AIEmbeddingRequest;
import com.ai.infrastructure.dto.AIEmbeddingResponse;
import com.ai.infrastructure.dto.AISearchResponse;
import com.ai.infrastructure.dto.RAGResponse;
import com.ai.infrastructure.entity.AISearchableEntity;
import com.ai.infrastructure.relationship.cache.QueryCache;
import com.ai.infrastructure.relationship.config.RelationshipModuleMetadata;
import com.ai.infrastructure.relationship.config.RelationshipQueryProperties;
import com.ai.infrastructure.relationship.dto.JpqlQuery;
import com.ai.infrastructure.relationship.dto.RelationshipQueryPlan;
import com.ai.infrastructure.relationship.exception.FallbackExhaustedException;
import com.ai.infrastructure.relationship.exception.QueryPlanningException;
import com.ai.infrastructure.relationship.exception.RelationshipQueryErrorContext;
import com.ai.infrastructure.relationship.exception.RelationshipTraversalException;
import com.ai.infrastructure.relationship.exception.VectorSearchException;
import com.ai.infrastructure.relationship.metrics.QueryMetrics;
import com.ai.infrastructure.relationship.model.QueryOptions;
import com.ai.infrastructure.relationship.model.ReturnMode;
import com.ai.infrastructure.relationship.validation.RelationshipQueryValidator;
import com.ai.infrastructure.rag.VectorDatabaseService;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Wraps {@link LLMDrivenJPAQueryService} with a multi-level fallback chain that
 * attempts metadata traversal, vector search, and simple repository lookups.
 */
@Slf4j
public class ReliableRelationshipQueryService {

    private final LLMDrivenJPAQueryService primaryService;
    private final RelationshipQueryPlanner planner;
    private final RelationshipTraversalService metadataTraversalService;
    private final VectorDatabaseService vectorDatabaseService;
    private final AIEmbeddingService embeddingService;
    private final AISearchableEntityRepository entityRepository;
    private final RelationshipQueryValidator validator;
    private final RelationshipQueryProperties properties;
    private final RelationshipModuleMetadata moduleMetadata;
    private final QueryCache queryCache;
    private final QueryMetrics queryMetrics;

    public ReliableRelationshipQueryService(LLMDrivenJPAQueryService primaryService,
                                            RelationshipQueryPlanner planner,
                                            RelationshipTraversalService metadataTraversalService,
                                            VectorDatabaseService vectorDatabaseService,
                                            AIEmbeddingService embeddingService,
                                            AISearchableEntityRepository entityRepository,
                                            RelationshipQueryValidator validator,
                                            RelationshipQueryProperties properties,
                                            RelationshipModuleMetadata moduleMetadata,
                                            QueryCache queryCache,
                                            QueryMetrics queryMetrics) {
        this.primaryService = Objects.requireNonNull(primaryService);
        this.planner = Objects.requireNonNull(planner);
        this.metadataTraversalService = metadataTraversalService;
        this.vectorDatabaseService = vectorDatabaseService;
        this.embeddingService = embeddingService;
        this.entityRepository = entityRepository;
        this.validator = validator;
        this.properties = properties;
        this.moduleMetadata = moduleMetadata;
        this.queryCache = queryCache;
        this.queryMetrics = queryMetrics;
    }

    public RAGResponse execute(String query, List<String> entityTypes, @Nullable QueryOptions options) {
        QueryOptions effectiveOptions = options != null ? options : QueryOptions.defaults();
        RAGResponse primary = tryPrimary(query, entityTypes, effectiveOptions);
        if (hasDocuments(primary)) {
            annotate(primary, "LLM_PRIMARY", false);
            return primary;
        }

        RelationshipQueryPlan plan = safePlan(query, entityTypes);

        RAGResponse metadataResponse = tryMetadataFallback(query, plan, effectiveOptions);
        if (hasDocuments(metadataResponse)) {
            return metadataResponse;
        }

        RAGResponse vectorResponse = tryVectorFallback(query, plan, effectiveOptions);
        if (hasDocuments(vectorResponse)) {
            return vectorResponse;
        }

        RAGResponse simpleResponse = trySimpleFallback(query, plan, effectiveOptions);
        if (hasDocuments(simpleResponse)) {
            return simpleResponse;
        }

        throw new FallbackExhaustedException(
            "All fallback strategies failed for query: " + query,
            errorContext(query, plan, "FALLBACK_CHAIN_EXHAUSTED", true)
        );
    }

    private RAGResponse tryPrimary(String query, List<String> entityTypes, QueryOptions options) {
        try {
            return primaryService.executeRelationshipQuery(query, entityTypes, options);
        } catch (Exception ex) {
            log.warn("Primary LLM relationship query failed, attempting fallback chain", ex);
            return RAGResponse.builder()
                .originalQuery(query)
                .entityType(entityTypes != null && !entityTypes.isEmpty() ? entityTypes.get(0) : null)
                .documents(List.of())
                .warnings(List.of("LLM plan execution failed; attempting fallback chain"))
                .build();
        }
    }

    private RAGResponse tryMetadataFallback(String query, RelationshipQueryPlan plan, QueryOptions options) {
        if (!properties.isFallbackToMetadata() || metadataTraversalService == null) {
            return emptyResponse(query, plan, "METADATA_DISABLED");
        }
        try {
            List<String> entityIds = metadataTraversalService.traverse(plan, (JpqlQuery) null);
            return buildResponseFromIds(query, plan, entityIds, options, "FALLBACK_METADATA");
        } catch (Exception ex) {
            log.warn("Metadata fallback traversal failed", ex);
            RelationshipTraversalException rte = new RelationshipTraversalException(
                "Metadata traversal failed",
                errorContext(query, plan, "FALLBACK_METADATA", true),
                ex
            );
            log.debug("Metadata traversal exception detail", rte);
            recordFallbackStage("FALLBACK_METADATA", false, 0);
            return emptyResponse(query, plan, "METADATA_ERROR");
        }
    }

    private RAGResponse tryVectorFallback(String query, RelationshipQueryPlan plan, QueryOptions options) {
        if (!properties.isFallbackToVectorSearch() || !vectorDependenciesPresent()) {
            return emptyResponse(query, plan, "VECTOR_DISABLED");
        }
        try {
            List<RAGResponse.RAGDocument> documents = vectorSearch(plan, options);
            recordFallbackStage("FALLBACK_VECTOR", !documents.isEmpty(), documents.size());
            return documents.isEmpty()
                ? emptyResponse(query, plan, "FALLBACK_VECTOR_EMPTY")
                : buildResponse(query, plan, documents, "FALLBACK_VECTOR");
        } catch (Exception ex) {
            log.warn("Vector fallback failed", ex);
            VectorSearchException vse = new VectorSearchException(
                "Vector fallback failed",
                errorContext(query, plan, "FALLBACK_VECTOR", true),
                ex
            );
            log.debug("Vector search exception detail", vse);
            recordFallbackStage("FALLBACK_VECTOR", false, 0);
            return emptyResponse(query, plan, "VECTOR_ERROR");
        }
    }

    private RAGResponse trySimpleFallback(String query, RelationshipQueryPlan plan, QueryOptions options) {
        if (!properties.isFallbackToSimpleSearch()) {
            return emptyResponse(query, plan, "SIMPLE_DISABLED");
        }
        try {
            List<AISearchableEntity> entities = entityRepository.findByEntityType(plan.getPrimaryEntityType());
            int limit = options.getLimit() != null ? options.getLimit() : 20;
            List<RAGResponse.RAGDocument> documents = new ArrayList<>();
            for (int i = 0; i < entities.size() && i < limit; i++) {
                AISearchableEntity entity = entities.get(i);
                documents.add(RAGResponse.RAGDocument.builder()
                    .id(entity.getEntityId())
                    .content(entity.getSearchableContent())
                    .metadata(Map.of("source", "simple-fallback"))
                    .build());
            }
            recordFallbackStage("FALLBACK_SIMPLE", !documents.isEmpty(), documents.size());
            return documents.isEmpty()
                ? emptyResponse(query, plan, "FALLBACK_SIMPLE_EMPTY")
                : buildResponse(query, plan, documents, "FALLBACK_SIMPLE");
        } catch (Exception ex) {
            log.error("Simple repository fallback failed", ex);
            recordFallbackStage("FALLBACK_SIMPLE", false, 0);
            return emptyResponse(query, plan, "SIMPLE_ERROR");
        }
    }

    private RelationshipQueryPlan safePlan(String query, List<String> entityTypes) {
        try {
            RelationshipQueryPlan plan = planner.planQuery(query, entityTypes);
            validator.validate(plan);
            return plan;
        } catch (Exception ex) {
            String entityType = (entityTypes != null && !entityTypes.isEmpty()) ? entityTypes.get(0) : "document";
            RelationshipQueryErrorContext context = RelationshipQueryErrorContext.builder()
                .originalQuery(query)
                .executionStage("PLAN_FALLBACK")
                .primaryEntityType(entityType)
                .candidateEntityTypes(entityTypes != null ? entityTypes : List.of(entityType))
                .fallbackUsed(true)
                .attributes(Map.of("reason", ex.getMessage()))
                .build();
            QueryPlanningException qpe = new QueryPlanningException(
                "Planner failed during fallback preparation; synthesizing plan",
                context,
                ex
            );
            log.warn("Planner failure captured with context {}", context.getExecutionStage(), qpe);
            return RelationshipQueryPlan.builder()
                .originalQuery(query)
                .semanticQuery(query)
                .primaryEntityType(entityType)
                .candidateEntityTypes(entityTypes != null ? entityTypes : List.of(entityType))
                .build();
        }
    }

    private List<RAGResponse.RAGDocument> vectorSearch(RelationshipQueryPlan plan, QueryOptions options) {
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
            Optional.ofNullable(options.getSimilarityThreshold()).orElse(moduleMetadata.similarityThreshold())
        );
        if (response == null || CollectionUtils.isEmpty(response.getResults())) {
            return List.of();
        }
        List<RAGResponse.RAGDocument> documents = new ArrayList<>();
        for (Map<String, Object> result : response.getResults()) {
            if (result == null) {
                continue;
            }
            Object id = result.getOrDefault("entityId", result.get("id"));
            documents.add(RAGResponse.RAGDocument.builder()
                .id(id != null ? id.toString() : null)
                .score((Double) result.getOrDefault("score", 0.0d))
                .metadata(new LinkedHashMap<>(result))
                .source((String) result.getOrDefault("source", "vector-fallback"))
                .build());
        }
        return documents;
    }

    private RAGResponse buildResponseFromIds(String query,
                                             RelationshipQueryPlan plan,
                                             List<String> entityIds,
                                             QueryOptions options,
                                             String stage) {
        List<String> limited = limitIds(plan, entityIds, options);
        recordFallbackStage(stage, !limited.isEmpty(), limited.size());
        if (limited.isEmpty()) {
            return emptyResponse(query, plan, stage + "_EMPTY");
        }
        List<RAGResponse.RAGDocument> documents = new ArrayList<>();
        ReturnMode returnMode = options.getReturnMode() != null ? options.getReturnMode() : properties.getDefaultReturnMode();
        for (String id : limited) {
            if (returnMode == ReturnMode.IDS) {
                documents.add(RAGResponse.RAGDocument.builder()
                    .id(id)
                    .source(stage.toLowerCase())
                    .build());
            } else {
                entityRepository.findByEntityTypeAndEntityId(plan.getPrimaryEntityType(), id)
                    .ifPresent(entity -> documents.add(
                        RAGResponse.RAGDocument.builder()
                            .id(id)
                            .content(entity.getSearchableContent())
                            .metadata(Map.of("source", stage.toLowerCase()))
                            .build()
                    ));
            }
        }
        return buildResponse(query, plan, documents, stage);
    }

    private RAGResponse buildResponse(String query,
                                      RelationshipQueryPlan plan,
                                      List<RAGResponse.RAGDocument> documents,
                                      String stage) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("plan", plan);
        metadata.put("executionStage", stage);
        metadata.put("timestamp", Instant.now().toString());
        return RAGResponse.builder()
            .originalQuery(query)
            .entityType(plan.getPrimaryEntityType())
            .documents(documents)
            .totalResults(documents.size())
            .returnedResults(documents.size())
            .metadata(metadata)
            .warnings(documents.isEmpty()
                ? List.of("Fallback stage %s produced no results".formatted(stage))
                : List.of("Results returned from fallback stage %s".formatted(stage)))
            .build();
    }

    private RAGResponse emptyResponse(String query, RelationshipQueryPlan plan, String stage) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("plan", plan);
        metadata.put("executionStage", stage);
        metadata.put("timestamp", Instant.now().toString());
        return RAGResponse.builder()
            .originalQuery(query)
            .entityType(plan != null ? plan.getPrimaryEntityType() : null)
            .documents(List.of())
            .metadata(metadata)
            .warnings(List.of("Stage %s returned no results".formatted(stage)))
            .build();
    }

    private void annotate(RAGResponse response, String stage, boolean fallback) {
        if (response == null) {
            return;
        }
        Map<String, Object> metadata = response.getMetadata() != null
            ? new LinkedHashMap<>(response.getMetadata())
            : new LinkedHashMap<>();
        metadata.put("executionStage", stage);
        metadata.put("fallbackUsed", fallback);
        metadata.put("timestamp", Instant.now().toString());
        response.setMetadata(metadata);
    }

    private boolean hasDocuments(@Nullable RAGResponse response) {
        return response != null
            && response.getDocuments() != null
            && !response.getDocuments().isEmpty();
    }

    private List<String> limitIds(RelationshipQueryPlan plan, List<String> entityIds, QueryOptions options) {
        if (entityIds == null || entityIds.isEmpty()) {
            return List.of();
        }
        int limit = options.getLimit() != null ? options.getLimit() : Optional.ofNullable(plan.getLimit()).orElse(50);
        return entityIds.stream().limit(limit).toList();
    }

    private boolean vectorDependenciesPresent() {
        return vectorDatabaseService != null && embeddingService != null;
    }

    private RelationshipQueryErrorContext errorContext(String query,
                                                       RelationshipQueryPlan plan,
                                                       String stage,
                                                       boolean fallbackUsed) {
        return RelationshipQueryErrorContext.builder()
            .originalQuery(query)
            .executionStage(stage)
            .primaryEntityType(plan != null ? plan.getPrimaryEntityType() : null)
            .candidateEntityTypes(plan != null ? plan.getCandidateEntityTypes() : null)
            .fallbackUsed(fallbackUsed)
            .attributes(Map.of("timestamp", Instant.now().toString()))
            .build();
    }

    private void recordFallbackStage(String stage, boolean success, int producedResults) {
        if (queryMetrics != null && queryMetrics.isEnabled()) {
            queryMetrics.recordFallbackStage(stage, success, producedResults);
        }
    }
}
