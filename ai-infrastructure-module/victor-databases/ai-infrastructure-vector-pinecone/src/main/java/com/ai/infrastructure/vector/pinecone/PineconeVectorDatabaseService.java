package com.ai.infrastructure.vector.pinecone;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.dto.AISearchRequest;
import com.ai.infrastructure.dto.AISearchResponse;
import com.ai.infrastructure.dto.VectorRecord;
import com.ai.infrastructure.rag.VectorDatabaseService;
import com.ai.infrastructure.exception.AIServiceException;
import com.ai.infrastructure.util.MetadataJsonSerializer;
import com.google.protobuf.ListValue;
import com.google.protobuf.NullValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import io.pinecone.clients.Index;
import io.pinecone.configs.PineconeConfig;
import io.pinecone.configs.PineconeConnection;
import io.pinecone.proto.DescribeIndexStatsResponse;
import io.pinecone.proto.FetchResponse;
import io.pinecone.proto.ListItem;
import io.pinecone.proto.ListResponse;
import io.pinecone.proto.NamespaceSummary;
import io.pinecone.proto.SparseValues;
import io.pinecone.proto.UpsertRequest;
import io.pinecone.unsigned_indices_model.QueryResponseWithUnsignedIndices;
import io.pinecone.unsigned_indices_model.ScoredVectorWithUnsignedIndices;
import io.pinecone.unsigned_indices_model.SparseValuesWithUnsignedIndices;
import io.pinecone.unsigned_indices_model.VectorWithUnsignedIndices;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import jakarta.annotation.PreDestroy;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Pinecone Vector Database Service
 * 
 * This service provides vector database operations using Pinecone for production
 * environments where high-scale vector search is required.
 * 
 * @author AI Infrastructure Team
 * @version 2.0.0
 */
@Slf4j
public class PineconeVectorDatabaseService implements VectorDatabaseService, AutoCloseable {

    private static final String DEFAULT_NAMESPACE = "default";
    private static final String EMBEDDING_BASE64_FIELD = "embeddingBase64";
    private static final String EMBEDDING_DIM_FIELD = "embeddingDim";
    private static final Set<String> RESERVED_METADATA_FIELDS =
        Set.of("entityType", "entityId", "content", EMBEDDING_BASE64_FIELD, EMBEDDING_DIM_FIELD);

    private final AIProviderConfig.PineconeConfig config;
    private final String indexName;
    private final PineconeConnection connection;
    private final Index index;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile Boolean sparseIndex;

    public PineconeVectorDatabaseService(AIProviderConfig providerConfig) {
        this(providerConfig, Index::new);
    }

    public PineconeVectorDatabaseService(AIProviderConfig providerConfig, PineconeIndexFactory indexFactory) {
        this.config = Objects.requireNonNull(providerConfig.getPinecone(), "Pinecone configuration must be present");
        this.indexName = resolveIndexName(this.config);
        this.connection = buildConnection(this.config, this.indexName);
        this.index = Objects.requireNonNull(indexFactory, "Pinecone indexFactory must be provided")
            .create(connection, indexName);
        log.info("Pinecone client configured for index '{}'", indexName);
    }

    @Override
    public String storeVector(String entityType, String entityId, String content, 
                           List<Double> embedding, Map<String, Object> metadata) {
        ensureEnabled();
        String vectorId = buildVectorId(entityType, entityId);
        upsertVector(vectorId, namespace(entityType), entityType, entityId, content, embedding, metadata);
        return vectorId;
    }
    
    @Override
    public boolean updateVector(String vectorId, String entityType, String entityId, String content, 
                              List<Double> embedding, Map<String, Object> metadata) {
        ensureEnabled();
        if (!StringUtils.hasText(vectorId)) {
            return false;
        }
        String namespace = extractNamespace(vectorId);
        upsertVector(vectorId, namespace, entityType, entityId, content, embedding, metadata);
        return true;
    }
    
    @Override
    public Optional<VectorRecord> getVector(String vectorId) {
        ensureEnabled();
        if (!StringUtils.hasText(vectorId)) {
            return Optional.empty();
        }

        String namespace = extractNamespace(vectorId);
        FetchResponse response = index.fetch(List.of(vectorId), namespace);
        if (response == null || !response.containsVectors(vectorId)) {
            return Optional.empty();
        }
        return Optional.of(toVectorRecord(response.getVectorsOrThrow(vectorId), namespace));
    }

    @Override
    public Optional<VectorRecord> getVectorByEntity(String entityType, String entityId) {
        String vectorId = buildVectorId(entityType, entityId);
        return getVector(vectorId);
    }

    @Override
    public AISearchResponse search(List<Double> queryVector, AISearchRequest request) {
        ensureEnabled();
        if (CollectionUtils.isEmpty(queryVector)) {
            throw new AIServiceException("Query vector is required for Pinecone search");
        }
        if (request == null) {
            throw new AIServiceException("Pinecone search requires a request");
        }

        long start = System.currentTimeMillis();

        String namespace = namespace(request.getEntityType());
        int topK = Optional.ofNullable(request.getLimit()).orElse(10);
        double threshold = Optional.ofNullable(request.getThreshold()).orElse(0.0);

        Struct filter = null;
        if (request.getMetadata() != null && !request.getMetadata().isEmpty()) {
            filter = toFilterStruct(request.getMetadata());
        }

        QueryResponseWithUnsignedIndices response;
        boolean preferSparse = isSparseIndex();
        try {
            if (preferSparse) {
                response = querySparse(topK, queryVector, namespace, filter);
            } else {
                response = index.queryByVector(topK, toFloatList(queryVector), namespace, filter, false, true);
            }
        } catch (Exception ex) {
            if (!preferSparse && shouldRetryAsSparse(ex)) {
                sparseIndex = true;
                response = querySparse(topK, queryVector, namespace, filter);
            } else {
                throw new AIServiceException("Pinecone search failed", ex);
            }
        }

        List<ScoredVectorWithUnsignedIndices> matches = response != null ? response.getMatchesList() : List.of();

        List<Map<String, Object>> results = new ArrayList<>();
        double maxScore = 0.0;

        for (ScoredVectorWithUnsignedIndices match : matches) {
            double score = match.getScore();
            if (score < threshold) {
                continue;
            }
            maxScore = Math.max(maxScore, score);

            Map<String, Object> metadata = fromStruct(match.getMetadata());
            String content = metadata != null ? Objects.toString(metadata.get("content"), null) : null;
            String entityId = metadata != null ? Objects.toString(metadata.get("entityId"), null) : null;
            String entityType = metadata != null ? Objects.toString(metadata.get("entityType"), request.getEntityType()) : request.getEntityType();
            if (!StringUtils.hasText(entityId)) {
                entityId = extractEntityId(match.getId());
            }
            if (!StringUtils.hasText(entityType)) {
                entityType = extractNamespace(match.getId());
            }

            Map<String, Object> customMetadata = new LinkedHashMap<>();
            if (metadata != null) {
                metadata.forEach((key, value) -> {
                    if (!RESERVED_METADATA_FIELDS.contains(key)) {
                        customMetadata.put(key, value);
                    }
                });
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("vectorId", match.getId());
            row.put("entityId", entityId);
            row.put("entityType", entityType);
            row.put("content", content);
            row.put("metadata", customMetadata);
            row.put("score", score);
            results.add(row);
        }

        return AISearchResponse.builder()
            .results(results)
            .totalResults(results.size())
            .maxScore(maxScore)
            .processingTimeMs(System.currentTimeMillis() - start)
            .requestId(UUID.randomUUID().toString())
            .query(request.getQuery())
            .model(namespace)
            .build();
    }

    @Override
    public AISearchResponse searchByEntityType(List<Double> queryVector, String entityType, int limit, double threshold) {
        AISearchRequest request = AISearchRequest.builder()
            .query("")
            .entityType(entityType)
            .limit(limit)
            .threshold(threshold)
            .build();
        return search(queryVector, request);
    }

    @Override
    public boolean removeVector(String entityType, String entityId) {
        return removeVectorById(buildVectorId(entityType, entityId));
    }

    @Override
    public boolean removeVectorById(String vectorId) {
        ensureEnabled();
        if (!StringUtils.hasText(vectorId)) {
            return false;
        }

        String namespace = extractNamespace(vectorId);
        index.deleteByIds(List.of(vectorId), namespace);
        return true;
    }

    @Override
    public List<String> batchStoreVectors(List<VectorRecord> vectors) {
        if (CollectionUtils.isEmpty(vectors)) {
            return List.of();
        }

        List<String> ids = new ArrayList<>(vectors.size());
        for (VectorRecord record : vectors) {
            ids.add(storeVector(record.getEntityType(), record.getEntityId(), record.getContent(), record.getEmbedding(), record.getMetadata()));
        }
        return ids;
    }

    @Override
    public int batchUpdateVectors(List<VectorRecord> vectors) {
        if (CollectionUtils.isEmpty(vectors)) {
            return 0;
        }
        int updated = 0;
        for (VectorRecord record : vectors) {
            if (record.getVectorId() == null) {
                continue;
            }
            if (updateVector(record.getVectorId(), record.getEntityType(), record.getEntityId(), record.getContent(), record.getEmbedding(), record.getMetadata())) {
                updated++;
            }
        }
        return updated;
    }

    @Override
    public int batchRemoveVectors(List<String> vectorIds) {
        if (CollectionUtils.isEmpty(vectorIds)) {
            return 0;
        }

        Map<String, List<String>> grouped = vectorIds.stream().filter(StringUtils::hasText).collect(Collectors.groupingBy(this::extractNamespace));
        int removed = 0;
        for (Map.Entry<String, List<String>> entry : grouped.entrySet()) {
            index.deleteByIds(entry.getValue(), entry.getKey());
            removed += entry.getValue().size();
        }
        return removed;
    }
    
    @Override
    public List<VectorRecord> getVectorsByEntityType(String entityType) {
        ensureEnabled();
        String namespace = namespace(entityType);

        List<String> ids = new ArrayList<>();
        String token = null;
        int pageSize = 100;

        do {
            ListResponse response = token == null
                ? index.list(namespace, pageSize)
                : index.list(namespace, pageSize, token);

            if (response == null) {
                break;
            }

            for (ListItem item : response.getVectorsList()) {
                if (StringUtils.hasText(item.getId())) {
                    ids.add(item.getId());
                }
            }

            token = response.hasPagination() ? response.getPagination().getNext() : null;
        } while (StringUtils.hasText(token));

        if (ids.isEmpty()) {
            return List.of();
        }

        List<VectorRecord> records = new ArrayList<>(ids.size());
        for (List<String> chunk : chunk(ids, 100)) {
            FetchResponse fetch = index.fetch(chunk, namespace);
            if (fetch == null) {
                continue;
            }
            fetch.getVectorsMap().values().forEach(vector -> records.add(toVectorRecord(vector, namespace)));
        }

        return records;
    }
    
    @Override
    public long getVectorCountByEntityType(String entityType) {
        ensureEnabled();
        String namespace = namespace(entityType);
        DescribeIndexStatsResponse stats = index.describeIndexStats();
        if (stats == null) {
            return 0L;
        }
        NamespaceSummary summary = stats.getNamespacesOrDefault(namespace, NamespaceSummary.getDefaultInstance());
        return summary.getVectorCount();
    }
    
    @Override
    public boolean vectorExists(String entityType, String entityId) {
        return getVectorByEntity(entityType, entityId).isPresent();
    }
    
    @Override
    public long clearVectors() {
        ensureEnabled();
        DescribeIndexStatsResponse stats = index.describeIndexStats();
        if (stats == null) {
            return 0L;
        }

        long cleared = 0;
        for (Map.Entry<String, NamespaceSummary> entry : stats.getNamespacesMap().entrySet()) {
            int count = entry.getValue() != null ? entry.getValue().getVectorCount() : 0;
            cleared += count;
            try {
                index.deleteAll(entry.getKey());
            } catch (Exception ex) {
                // Pinecone can return NOT_FOUND when a namespace doesn't exist (race/empty namespace).
                // Clearing is best-effort; ignore "namespace not found" and continue.
                if (!isNamespaceNotFound(ex)) {
                    throw new AIServiceException("Failed to clear all vectors", ex);
                }
            }
        }
        return cleared;
    }
    
    @Override
    public long clearVectorsByEntityType(String entityType) {
        ensureEnabled();
        String namespace = namespace(entityType);
        long count = 0L;
        try {
            DescribeIndexStatsResponse stats = index.describeIndexStats();
            if (stats != null) {
                NamespaceSummary summary = stats.getNamespacesOrDefault(namespace, NamespaceSummary.getDefaultInstance());
                count = summary.getVectorCount();
                if (count <= 0) {
                    return 0L;
                }
            }
        } catch (Exception ex) {
            // best-effort; still attempt deletion
        }

        try {
            index.deleteAll(namespace);
        } catch (Exception ex) {
            if (!isNamespaceNotFound(ex)) {
                throw new AIServiceException("Failed to clear vectors for entity type", ex);
            }
        }
        return count;
    }
    
    @Override
    public Map<String, Object> getStatistics() {
        ensureEnabled();
        DescribeIndexStatsResponse stats = index.describeIndexStats();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("type", "pinecone");
        response.put("indexName", indexName);
        response.put("host", config.getApiHost());
        response.put("dimension", stats != null ? stats.getDimension() : null);
        response.put("totalVectorCount", stats != null ? stats.getTotalVectorCount() : null);
        response.put("namespaces", stats != null ? stats.getNamespacesMap().keySet() : Set.of());
        return response;
    }

    private void ensureEnabled() {
        if (!config.isEnabled()) {
            throw new AIServiceException("Pinecone vector provider is disabled");
        }
        if (!StringUtils.hasText(config.getApiKey())) {
            throw new AIServiceException("Pinecone apiKey must be configured");
        }
        if (!StringUtils.hasText(indexName)) {
            throw new AIServiceException("Pinecone indexName must be configured");
        }
    }

    private PineconeConnection buildConnection(AIProviderConfig.PineconeConfig config, String indexName) {
        if (config == null || !config.isEnabled()) {
            throw new AIServiceException("Pinecone vector provider is disabled");
        }
        if (!StringUtils.hasText(config.getApiKey())) {
            throw new AIServiceException("Pinecone apiKey must be configured");
        }
        if (!StringUtils.hasText(indexName)) {
            throw new AIServiceException("Pinecone indexName must be configured");
        }

        String host = resolveIndexHost(config);
        PineconeConfig pineconeConfig = new PineconeConfig(config.getApiKey());
        pineconeConfig.setHost(host);
        return new PineconeConnection(pineconeConfig);
    }

    private String resolveIndexName(AIProviderConfig.PineconeConfig config) {
        if (config == null) {
            throw new AIServiceException("Pinecone configuration must be present");
        }
        if (StringUtils.hasText(config.getIndexName())) {
            return config.getIndexName().trim();
        }
        if (StringUtils.hasText(config.getApiHost())) {
            String host = config.getApiHost().trim();
            try {
                URI uri = host.startsWith("http://") || host.startsWith("https://")
                    ? URI.create(host)
                    : URI.create("https://" + host);
                String hostname = uri.getHost();
                if (!StringUtils.hasText(hostname)) {
                    hostname = uri.getAuthority();
                }
                if (!StringUtils.hasText(hostname)) {
                    throw new AIServiceException("Unable to derive Pinecone indexName from apiHost");
                }
                int dot = hostname.indexOf('.');
                return dot > 0 ? hostname.substring(0, dot) : hostname;
            } catch (IllegalArgumentException ex) {
                throw new AIServiceException("Unable to derive Pinecone indexName from apiHost", ex);
            }
        }
        throw new AIServiceException("Pinecone indexName must be configured");
    }

    private String resolveIndexHost(AIProviderConfig.PineconeConfig config) {
        if (StringUtils.hasText(config.getApiHost())) {
            return config.getApiHost().trim();
        }
        if (StringUtils.hasText(config.getProjectId())) {
            return String.format("https://%s-%s.svc.%s.pinecone.io",
                config.getIndexName(), config.getProjectId(), config.getEnvironment());
        }
        return String.format("https://%s.svc.%s.pinecone.io",
            config.getIndexName(), config.getEnvironment());
    }

    private void upsertVector(String vectorId,
                              String namespace,
                              String entityType,
                              String entityId,
                              String content,
                              List<Double> embedding,
                              Map<String, Object> metadata) {
        if (!StringUtils.hasText(vectorId)) {
            throw new AIServiceException("Pinecone vectorId is required");
        }
        if (CollectionUtils.isEmpty(embedding)) {
            throw new AIServiceException("Pinecone upsert requires a non-empty embedding vector");
        }

        Struct metadataStruct = buildMetadataStruct(entityType, entityId, content, embedding, metadata);

        boolean preferSparse = isSparseIndex();
        try {
            withPineconeRetry("upsert", () -> {
                if (preferSparse) {
                    upsertSparse(vectorId, namespace, embedding, metadataStruct, true);
                } else {
                    upsertDense(vectorId, namespace, embedding, metadataStruct);
                }
                return null;
            });
        } catch (Exception ex) {
            if (!preferSparse && shouldRetryAsSparse(ex)) {
                sparseIndex = true;
                withPineconeRetry("upsertSparse", () -> {
                    upsertSparse(vectorId, namespace, embedding, metadataStruct, true);
                    return null;
                });
                return;
            }
            throw new AIServiceException("Failed to upsert vector into Pinecone", ex);
        }
    }

    private <T> T withPineconeRetry(String operation, Supplier<T> supplier) {
        int maxAttempts = 6;
        long backoffMs = 250;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return supplier.get();
            } catch (Exception ex) {
                if (attempt >= maxAttempts || !isRetryablePineconeException(ex)) {
                    throw ex;
                }
                long sleepMs = backoffMs + ThreadLocalRandom.current().nextLong(0, 200);
                log.warn(
                    "Pinecone {} failed due to rate/availability (attempt {}/{}). Retrying after {}ms. Cause: {}",
                    operation,
                    attempt,
                    maxAttempts,
                    sleepMs,
                    ex.getMessage()
                );
                sleepQuietly(sleepMs);
                backoffMs = Math.min(5000, backoffMs * 2);
            }
        }

        // unreachable
        return supplier.get();
    }

    private boolean isRetryablePineconeException(Throwable ex) {
        Throwable cursor = ex;
        while (cursor != null) {
            if (cursor instanceof StatusRuntimeException statusEx) {
                Status status = statusEx.getStatus();
                Status.Code code = status != null ? status.getCode() : null;
                if (code == Status.Code.UNAVAILABLE || code == Status.Code.RESOURCE_EXHAUSTED || code == Status.Code.DEADLINE_EXCEEDED) {
                    return true;
                }
            }

            String message = cursor.getMessage();
            if (StringUtils.hasText(message)) {
                String lower = message.toLowerCase(Locale.ROOT);
                if (lower.contains("too many requests") || lower.contains("rate limit") || lower.contains("throttl")) {
                    return true;
                }
            }

            cursor = cursor.getCause();
        }

        return false;
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private void upsertDense(String vectorId, String namespace, List<Double> embedding, Struct metadataStruct) {
        List<Float> values = toFloatList(embedding);
        index.upsert(vectorId, values, null, null, metadataStruct, namespace);
    }

    private void upsertSparse(String vectorId,
                              String namespace,
                              List<Double> embedding,
                              Struct metadataStruct,
                              boolean sparseOnly) {
        List<Long> sparseIndices = new ArrayList<>(embedding.size());
        List<Float> sparseValues = new ArrayList<>(embedding.size());
        for (int i = 0; i < embedding.size(); i++) {
            sparseIndices.add((long) i);
            Double value = embedding.get(i);
            sparseValues.add(value != null ? value.floatValue() : 0.0f);
        }

        if (sparseOnly) {
            SparseValuesWithUnsignedIndices sparse = new SparseValuesWithUnsignedIndices(sparseIndices, sparseValues);
            VectorWithUnsignedIndices vector = new VectorWithUnsignedIndices(vectorId, null, metadataStruct, sparse);
            index.upsert(List.of(vector), namespace);
            return;
        }

        List<Float> denseValues = toFloatList(embedding);
        index.upsert(vectorId, denseValues, sparseIndices, sparseValues, metadataStruct, namespace);
    }

    private boolean isSparseIndex() {
        Boolean cached = sparseIndex;
        if (cached != null) {
            return cached;
        }

        try {
            DescribeIndexStatsResponse stats = index.describeIndexStats();
            cached = stats != null && stats.getDimension() == 0;
        } catch (Exception ex) {
            cached = false;
        }

        sparseIndex = cached;
        return cached;
    }

    private boolean shouldRetryAsSparse(Exception ex) {
        try {
            DescribeIndexStatsResponse stats = index.describeIndexStats();
            if (stats != null && stats.getDimension() == 0) {
                return true;
            }
        } catch (Exception ignored) {
            // ignore
        }

        String message = ex != null ? ex.getMessage() : null;
        if (!StringUtils.hasText(message)) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("sparse");
    }

    private boolean isNamespaceNotFound(Throwable ex) {
        if (ex == null) {
            return false;
        }

        Throwable cursor = ex;
        while (cursor != null) {
            if (cursor instanceof StatusRuntimeException statusEx) {
                Status status = statusEx.getStatus();
                if (status != null && status.getCode() == Status.Code.NOT_FOUND) {
                    return true;
                }
            }

            String message = cursor.getMessage();
            if (StringUtils.hasText(message) && message.toLowerCase(Locale.ROOT).contains("namespace not found")) {
                return true;
            }

            cursor = cursor.getCause();
        }

        return false;
    }

    private QueryResponseWithUnsignedIndices querySparse(int topK, List<Double> queryVector, String namespace, Struct filter) {
        List<Long> indices = new ArrayList<>(queryVector.size());
        List<Float> values = new ArrayList<>(queryVector.size());
        for (int i = 0; i < queryVector.size(); i++) {
            indices.add((long) i);
            values.add(queryVector.get(i).floatValue());
        }
        return index.query(topK, null, indices, values, null, namespace, filter, false, true);
    }

    private String extractEntityId(String vectorId) {
        if (!StringUtils.hasText(vectorId)) {
            return null;
        }
        int idx = vectorId.indexOf("::");
        if (idx < 0) {
            return vectorId;
        }
        return vectorId.substring(idx + 2);
    }

    private Struct buildMetadataStruct(String entityType,
                                      String entityId,
                                      String content,
                                      List<Double> embedding,
                                      Map<String, Object> metadata) {
        Map<String, Object> safeMetadata = metadata == null ? Map.of() : metadata;

        Map<String, Value> fields = new LinkedHashMap<>();
        fields.put("entityType", Value.newBuilder().setStringValue(namespace(entityType)).build());
        fields.put("entityId", Value.newBuilder().setStringValue(entityId).build());
        if (content != null) {
            fields.put("content", Value.newBuilder().setStringValue(content).build());
        }

        fields.put(EMBEDDING_DIM_FIELD, Value.newBuilder().setNumberValue(embedding.size()).build());
        fields.put(EMBEDDING_BASE64_FIELD, Value.newBuilder().setStringValue(encodeEmbeddingBase64(embedding)).build());

        String raw = MetadataJsonSerializer.serialize(stripRaw(safeMetadata));
        fields.put("raw", Value.newBuilder().setStringValue(raw).build());

        safeMetadata.forEach((key, value) -> {
            if (!"raw".equals(key)) {
                fields.put(key, toValue(value));
            }
        });

        return Struct.newBuilder().putAllFields(fields).build();
    }

    private Map<String, Object> stripRaw(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty() || !metadata.containsKey("raw")) {
            return metadata == null ? Map.of() : metadata;
        }
        Map<String, Object> copy = new LinkedHashMap<>(metadata);
        copy.remove("raw");
        return copy;
    }

    private Value toValue(Object obj) {
        if (obj == null) {
            return Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build();
        }
        if (obj instanceof String value) {
            return Value.newBuilder().setStringValue(value).build();
        }
        if (obj instanceof Boolean value) {
            return Value.newBuilder().setBoolValue(value).build();
        }
        if (obj instanceof Number number) {
            return Value.newBuilder().setNumberValue(number.doubleValue()).build();
        }
        if (obj instanceof List<?> list) {
            List<Value> values = list.stream().map(this::toValue).toList();
            return Value.newBuilder()
                .setListValue(ListValue.newBuilder().addAllValues(values))
                .build();
        }
        if (obj instanceof Map<?, ?> map) {
            Map<String, Value> nested = new LinkedHashMap<>();
            map.forEach((key, value) -> {
                if (key != null) {
                    nested.put(String.valueOf(key), toValue(value));
                }
            });
            return Value.newBuilder()
                .setStructValue(Struct.newBuilder().putAllFields(nested))
                .build();
        }
        return Value.newBuilder().setStringValue(obj.toString()).build();
    }

    private Struct toStruct(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return Struct.getDefaultInstance();
        }
        Map<String, Value> fields = new LinkedHashMap<>();
        values.forEach((key, value) -> fields.put(key, toValue(value)));
        return Struct.newBuilder().putAllFields(fields).build();
    }

    /**
     * Build a Pinecone metadata filter struct.
     *
     * Pinecone expects filter values to be "operator objects" (e.g. {"field": {"$eq": "x"}}).
     * Lists/collections must be represented via operators such as "$in", not raw arrays.
     * Null/blank values are skipped to avoid INVALID_ARGUMENT errors.
     */
    private Struct toFilterStruct(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }

        Map<String, Value> fields = new LinkedHashMap<>();
        metadata.forEach((key, value) -> {
            if (!StringUtils.hasText(key)) {
                return;
            }
            Value condition = toFilterCondition(value);
            if (condition != null) {
                fields.put(key, condition);
            }
        });

        if (fields.isEmpty()) {
            return null;
        }
        return Struct.newBuilder().putAllFields(fields).build();
    }

    private Value toFilterCondition(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String str && !StringUtils.hasText(str)) {
            return null;
        }

        // If caller already provided a Pinecone-style operator map, keep it (but skip nulls).
        if (value instanceof Map<?, ?> map) {
            Struct struct = toFilterOperatorStruct(map);
            if (struct == null || struct.getFieldsCount() == 0) {
                return null;
            }
            return Value.newBuilder().setStructValue(struct).build();
        }

        // Collections/lists must use operators like "$in" instead of a raw ListValue.
        if (value instanceof Iterable<?> iterable) {
            List<Value> items = new ArrayList<>();
            for (Object item : iterable) {
                Value scalar = toFilterScalarValue(item);
                if (scalar != null) {
                    items.add(scalar);
                }
            }
            if (items.isEmpty()) {
                return null;
            }
            Struct condition = Struct.newBuilder()
                .putFields("$in", Value.newBuilder()
                    .setListValue(ListValue.newBuilder().addAllValues(items))
                    .build())
                .build();
            return Value.newBuilder().setStructValue(condition).build();
        }

        Value scalar = toFilterScalarValue(value);
        if (scalar == null) {
            return null;
        }
        Struct condition = Struct.newBuilder()
            .putFields("$eq", scalar)
            .build();
        return Value.newBuilder().setStructValue(condition).build();
    }

    private Struct toFilterOperatorStruct(Map<?, ?> map) {
        if (map == null || map.isEmpty()) {
            return Struct.getDefaultInstance();
        }

        Map<String, Value> fields = new LinkedHashMap<>();
        map.forEach((rawKey, rawValue) -> {
            if (rawKey == null || rawValue == null) {
                return;
            }
            String key = String.valueOf(rawKey);
            if (!StringUtils.hasText(key)) {
                return;
            }

            // For "$in", ensure we emit a ListValue of scalars and skip nulls/blanks.
            if ("$in".equals(key) && rawValue instanceof Iterable<?> iterable) {
                List<Value> items = new ArrayList<>();
                for (Object item : iterable) {
                    Value scalar = toFilterScalarValue(item);
                    if (scalar != null) {
                        items.add(scalar);
                    }
                }
                if (!items.isEmpty()) {
                    fields.put(key, Value.newBuilder()
                        .setListValue(ListValue.newBuilder().addAllValues(items))
                        .build());
                }
                return;
            }

            // Default: allow scalar or nested structures, but avoid embedding nulls.
            Value scalar = toFilterScalarValue(rawValue);
            if (scalar != null) {
                fields.put(key, scalar);
            }
        });

        return Struct.newBuilder().putAllFields(fields).build();
    }

    private Value toFilterScalarValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String str) {
            return StringUtils.hasText(str)
                ? Value.newBuilder().setStringValue(str).build()
                : null;
        }
        if (value instanceof Boolean bool) {
            return Value.newBuilder().setBoolValue(bool).build();
        }
        if (value instanceof Number number) {
            return Value.newBuilder().setNumberValue(number.doubleValue()).build();
        }
        // As a fallback, treat as a keyword string value.
        String text = String.valueOf(value);
        return StringUtils.hasText(text) ? Value.newBuilder().setStringValue(text).build() : null;
    }

    private Object fromValue(Value value) {
        if (value == null) {
            return null;
        }
        return switch (value.getKindCase()) {
            case NULL_VALUE -> null;
            case STRING_VALUE -> value.getStringValue();
            case BOOL_VALUE -> value.getBoolValue();
            case NUMBER_VALUE -> value.getNumberValue();
            case STRUCT_VALUE -> fromStruct(value.getStructValue());
            case LIST_VALUE -> value.getListValue().getValuesList().stream()
                .map(this::fromValue)
                .toList();
            case KIND_NOT_SET -> null;
        };
    }

    private Map<String, Object> fromStruct(Struct struct) {
        if (struct == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> map = new LinkedHashMap<>();
        struct.getFieldsMap().forEach((k, v) -> map.put(k, fromValue(v)));
        if (!map.containsKey("raw") && !map.isEmpty()) {
            map.put("raw", MetadataJsonSerializer.serialize(map));
        }
        return map;
    }

    private VectorRecord toVectorRecord(io.pinecone.proto.Vector vector, String namespace) {
        Map<String, Object> allMetadata = vector.hasMetadata() ? fromStruct(vector.getMetadata()) : Collections.emptyMap();

        String entityType = Objects.toString(allMetadata.getOrDefault("entityType", namespace), namespace);
        String entityId = Objects.toString(allMetadata.get("entityId"), null);
        String content = Objects.toString(allMetadata.get("content"), null);

        Map<String, Object> metadata = new LinkedHashMap<>();
        allMetadata.forEach((key, value) -> {
            if (!RESERVED_METADATA_FIELDS.contains(key)) {
                metadata.put(key, value);
            }
        });

        List<Double> embedding = extractEmbedding(vector, allMetadata);

        return VectorRecord.builder()
            .vectorId(vector.getId())
            .entityType(entityType)
            .entityId(entityId)
            .content(content)
            .embedding(embedding)
            .metadata(metadata)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }

    private List<Double> extractEmbedding(io.pinecone.proto.Vector vector, Map<String, Object> allMetadata) {
        Object encoded = allMetadata != null ? allMetadata.get(EMBEDDING_BASE64_FIELD) : null;
        if (encoded instanceof String base64 && StringUtils.hasText(base64)) {
            List<Double> decoded = decodeEmbeddingBase64(base64);
            if (!decoded.isEmpty()) {
                return decoded;
            }
        }

        if (vector != null && !vector.getValuesList().isEmpty()) {
            return vector.getValuesList().stream()
                .map(Float::doubleValue)
                .toList();
        }

        if (vector != null && vector.hasSparseValues()) {
            int dimension = resolveEmbeddingDimension(allMetadata);
            if (dimension > 0) {
                double[] embedding = new double[dimension];
                List<Integer> indices = vector.getSparseValues().getIndicesList();
                List<Float> values = vector.getSparseValues().getValuesList();
                for (int i = 0; i < indices.size() && i < values.size(); i++) {
                    int idx = indices.get(i);
                    if (idx >= 0 && idx < embedding.length) {
                        embedding[idx] = values.get(i);
                    }
                }
                List<Double> result = new ArrayList<>(embedding.length);
                for (double value : embedding) {
                    result.add(value);
                }
                return result;
            }
        }

        return List.of();
    }

    private int resolveEmbeddingDimension(Map<String, Object> allMetadata) {
        if (allMetadata == null) {
            return -1;
        }
        Object dim = allMetadata.get(EMBEDDING_DIM_FIELD);
        if (dim instanceof Number number) {
            return number.intValue();
        }
        if (dim instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        return -1;
    }

    private String encodeEmbeddingBase64(List<Double> embedding) {
        if (CollectionUtils.isEmpty(embedding)) {
            return "";
        }
        ByteBuffer buffer = ByteBuffer.allocate(embedding.size() * Double.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (Double value : embedding) {
            buffer.putDouble(value != null ? value : 0.0d);
        }
        return Base64.getEncoder().encodeToString(buffer.array());
    }

    private List<Double> decodeEmbeddingBase64(String base64) {
        if (!StringUtils.hasText(base64)) {
            return List.of();
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            if (bytes.length == 0) {
                return List.of();
            }

            if (bytes.length % Double.BYTES == 0) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
                int count = bytes.length / Double.BYTES;
                List<Double> values = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    values.add(buffer.getDouble());
                }
                return values;
            }

            if (bytes.length % Float.BYTES == 0) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
                int count = bytes.length / Float.BYTES;
                List<Double> values = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    values.add((double) buffer.getFloat());
                }
                return values;
            }
        } catch (Exception ignored) {
            // ignore
        }
        return List.of();
    }

    private List<Float> toFloatList(List<Double> embedding) {
        List<Float> floats = new ArrayList<>(embedding.size());
        for (Double value : embedding) {
            floats.add(value.floatValue());
        }
        return floats;
    }

    private String buildVectorId(String entityType, String entityId) {
        return namespace(entityType) + "::" + entityId;
    }

    private String namespace(String entityType) {
        return StringUtils.hasText(entityType) ? entityType : DEFAULT_NAMESPACE;
    }

    private String extractNamespace(String vectorId) {
        if (!StringUtils.hasText(vectorId)) {
            return DEFAULT_NAMESPACE;
        }
        int idx = vectorId.indexOf("::");
        if (idx <= 0) {
            return DEFAULT_NAMESPACE;
        }
        return vectorId.substring(0, idx);
    }

    private List<List<String>> chunk(List<String> ids, int chunkSize) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        if (chunkSize <= 0 || ids.size() <= chunkSize) {
            return List.of(ids);
        }
        List<List<String>> chunks = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += chunkSize) {
            chunks.add(ids.subList(i, Math.min(i + chunkSize, ids.size())));
        }
        return chunks;
    }

    @PreDestroy
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            try {
                index.close();
            } catch (Exception ex) {
                log.debug("Failed to close Pinecone index connection", ex);
            }
        }
    }
}
