package com.ai.infrastructure.vector.milvus;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.dto.AISearchRequest;
import com.ai.infrastructure.dto.AISearchResponse;
import com.ai.infrastructure.dto.VectorRecord;
import com.ai.infrastructure.exception.AIServiceException;
import com.ai.infrastructure.rag.VectorDatabaseService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.milvus.exception.IllegalResponseException;
import io.milvus.exception.ParamException;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.MutationResult;
import io.milvus.grpc.QueryResults;
import io.milvus.grpc.SearchResults;
import io.milvus.grpc.CollectionSchema;
import io.milvus.grpc.FieldSchema;
import io.milvus.grpc.KeyValuePair;
import io.milvus.param.ConnectParam;
import io.milvus.param.R;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.DescribeCollectionParam;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.QueryParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.dml.SearchParam;
import io.milvus.grpc.DataType;
import io.milvus.response.FieldDataWrapper;
import io.milvus.response.QueryResultsWrapper;
import io.milvus.response.SearchResultsWrapper;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * Milvus-backed implementation of {@link VectorDatabaseService}.
 */
@Slf4j
public class MilvusVectorDatabaseService implements VectorDatabaseService, AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String FIELD_VECTOR_ID = "vector_id";
    private static final String FIELD_ENTITY_ID = "entity_id";
    private static final String FIELD_CONTENT = "content";
    private static final String FIELD_METADATA = "metadata";
    private static final String FIELD_VECTOR = "embedding";

    private final AIProviderConfig.MilvusConfig config;
    private final MilvusServiceClient client;
    private final ConcurrentMap<String, Integer> collectionDimensions = new ConcurrentHashMap<>();

    public MilvusVectorDatabaseService(AIProviderConfig providerConfig) {
        this.config = Objects.requireNonNull(providerConfig.getMilvus(), "Milvus configuration must be present");
        if (!config.isEnabled()) {
            throw new AIServiceException("Milvus vector provider is disabled");
        }

        try {
            this.client = new MilvusServiceClient(buildConnectParam());
            log.info("Connected to Milvus at {}:{}", config.getHost(), config.getPort());
        } catch (Exception ex) {
            throw new AIServiceException("Failed to initialise Milvus client: " + ex.getMessage(), ex);
        }

        String requestedDatabase = Optional.ofNullable(config.getDatabaseName()).orElse("default");
        if (!"default".equalsIgnoreCase(requestedDatabase)) {
            log.info("Milvus SDK 2.4.x uses the active server database; requested '{}'", requestedDatabase);
        }
    }

    private ConnectParam buildConnectParam() {
        ConnectParam.Builder builder = ConnectParam.newBuilder()
            .withHost(Optional.ofNullable(config.getHost()).orElse("localhost"))
            .withPort(Optional.ofNullable(config.getPort()).orElse(19530));

        if (Boolean.TRUE.equals(config.getSecure())) {
            builder.withSecure(true);
        }
        if (config.getUsername() != null && !config.getUsername().isBlank()) {
            builder.withAuthorization(config.getUsername(), Optional.ofNullable(config.getPassword()).orElse(""));
        }
        if (config.getTimeout() != null && config.getTimeout() > 0) {
            builder.withConnectTimeout(config.getTimeout().longValue(), TimeUnit.SECONDS);
        }
        return builder.build();
    }

    @Override
    public String storeVector(String entityType, String entityId, String content,
                              List<Double> embedding, Map<String, Object> metadata) {
        Objects.requireNonNull(entityType, "entityType must not be null");
        Objects.requireNonNull(entityId, "entityId must not be null");
        if (embedding == null || embedding.isEmpty()) {
            throw new AIServiceException("Embedding vector must not be empty");
        }

        String collection = entityType.toLowerCase();
        ensureCollection(collection, embedding.size());

        String vectorId = buildVectorId(entityType, entityId);
        removeVectorById(vectorId);

        List<InsertParam.Field> fields = new ArrayList<>();
        fields.add(new InsertParam.Field(FIELD_VECTOR_ID, Collections.singletonList(vectorId)));
        fields.add(new InsertParam.Field(FIELD_ENTITY_ID, Collections.singletonList(entityId)));
        fields.add(new InsertParam.Field(FIELD_CONTENT, Collections.singletonList(content != null ? content : "")));
        fields.add(new InsertParam.Field(FIELD_METADATA, Collections.singletonList(metadataToJson(metadata))));
        fields.add(new InsertParam.Field(FIELD_VECTOR, Collections.singletonList(toFloatList(embedding))));

        InsertParam insertParam = InsertParam.newBuilder()
            .withCollectionName(collection)
            .withFields(fields)
            .build();

        R<MutationResult> response = client.insert(insertParam);
        verifySuccess(response, "insert vector into Milvus");
        return vectorId;
    }

    @Override
    public boolean updateVector(String vectorId, String entityType, String entityId,
                                String content, List<Double> embedding, Map<String, Object> metadata) {
        storeVector(entityType, entityId, content, embedding, metadata);
        return true;
    }

    @Override
    public Optional<VectorRecord> getVector(String vectorId) {
        Objects.requireNonNull(vectorId, "vectorId must not be null");
        String collection = extractEntityType(vectorId);
        ensureCollectionLoaded(collection);

        QueryParam queryParam = QueryParam.newBuilder()
            .withCollectionName(collection)
            .withExpr(String.format("%s == \"%s\"", FIELD_VECTOR_ID, vectorId))
            .addOutField(FIELD_VECTOR_ID)
            .addOutField(FIELD_ENTITY_ID)
            .addOutField(FIELD_CONTENT)
            .addOutField(FIELD_METADATA)
            .addOutField(FIELD_VECTOR)
            .build();

        R<QueryResults> response = client.query(queryParam);
        verifySuccess(response, "query vector by id");
        QueryResultsWrapper wrapper = new QueryResultsWrapper(response.getData());
        if (wrapper.getRowCount() == 0) {
            return Optional.empty();
        }
        return Optional.of(toVectorRecord(collection, wrapper, 0));
    }

    @Override
    public Optional<VectorRecord> getVectorByEntity(String entityType, String entityId) {
        Objects.requireNonNull(entityType, "entityType must not be null");
        Objects.requireNonNull(entityId, "entityId must not be null");
        String collection = entityType.toLowerCase();
        ensureCollectionLoaded(collection);

        QueryParam queryParam = QueryParam.newBuilder()
            .withCollectionName(collection)
            .withExpr(String.format("%s == \"%s\"", FIELD_ENTITY_ID, entityId))
            .addOutField(FIELD_VECTOR_ID)
            .addOutField(FIELD_ENTITY_ID)
            .addOutField(FIELD_CONTENT)
            .addOutField(FIELD_METADATA)
            .addOutField(FIELD_VECTOR)
            .build();

        R<QueryResults> response = client.query(queryParam);
        verifySuccess(response, "query vector by entity id");
        QueryResultsWrapper wrapper = new QueryResultsWrapper(response.getData());
        if (wrapper.getRowCount() == 0) {
            return Optional.empty();
        }
        return Optional.of(toVectorRecord(collection, wrapper, 0));
    }

    @Override
    public AISearchResponse search(List<Double> queryVector, AISearchRequest request) {
        if (request == null || request.getEntityType() == null) {
            throw new AIServiceException("Milvus search requires request.entityType to be specified");
        }
        if (queryVector == null || queryVector.isEmpty()) {
            throw new AIServiceException("Query vector must not be empty");
        }

        String collection = request.getEntityType().toLowerCase();
        ensureCollection(collection, queryVector.size());
        ensureCollectionLoaded(collection);

        int topK = Optional.ofNullable(request.getLimit()).orElse(10);
        double threshold = Optional.ofNullable(request.getThreshold()).orElse(0.0);

        SearchParam searchParam = SearchParam.newBuilder()
            .withCollectionName(collection)
            .withVectorFieldName(FIELD_VECTOR)
            .withTopK(topK)
            .withMetricType(MetricType.IP)
            .withParams("{\"nprobe\":16}")
            .withVectors(Collections.singletonList(toFloatList(queryVector)))
            .addOutField(FIELD_VECTOR_ID)
            .addOutField(FIELD_ENTITY_ID)
            .addOutField(FIELD_CONTENT)
            .addOutField(FIELD_METADATA)
            .build();

        R<SearchResults> response = client.search(searchParam);
        verifySuccess(response, "execute search");
        SearchResultsWrapper wrapper = new SearchResultsWrapper(response.getData().getResults());
        List<SearchResultsWrapper.IDScore> scores = wrapper.getIDScore(0);

        List<?> vectorIds;
        List<?> entityIds;
        List<?> contents;
        List<?> metadata;
        try {
            vectorIds = wrapper.getFieldData(FIELD_VECTOR_ID, 0);
            entityIds = wrapper.getFieldData(FIELD_ENTITY_ID, 0);
            contents = wrapper.getFieldData(FIELD_CONTENT, 0);
            metadata = wrapper.getFieldData(FIELD_METADATA, 0);
        } catch (ParamException ex) {
            throw new AIServiceException("Failed to parse Milvus search response", ex);
        }

        List<Map<String, Object>> results = new ArrayList<>();
        for (int i = 0; i < scores.size(); i++) {
            SearchResultsWrapper.IDScore score = scores.get(i);
            double similarity = score.getScore();
            if (similarity < threshold) {
                continue;
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("vectorId", vectorIds != null && i < vectorIds.size() ? String.valueOf(vectorIds.get(i)) : null);
            row.put("entityId", entityIds != null && i < entityIds.size() ? String.valueOf(entityIds.get(i)) : null);
            row.put("entityType", collection);
            row.put("content", contents != null && i < contents.size() ? Objects.toString(contents.get(i), null) : null);
            Object metadataRaw = metadata != null && i < metadata.size() ? metadata.get(i) : null;
            row.put("metadata", parseMetadata(metadataRaw));
            row.put("score", similarity);
            results.add(row);
        }

        return AISearchResponse.builder()
            .results(results)
            .totalResults(results.size())
            .maxScore(results.stream().map(row -> (Double) row.get("score")).max(Double::compareTo).orElse(0.0))
            .query(request.getQuery())
            .model(collection)
            .build();
    }

    @Override
    public AISearchResponse searchByEntityType(List<Double> queryVector, String entityType, int limit, double threshold) {
        AISearchRequest request = AISearchRequest.builder()
            .entityType(entityType)
            .limit(limit)
            .threshold(threshold)
            .build();
        return search(queryVector, request);
    }

    @Override
    public boolean removeVector(String entityType, String entityId) {
        if (entityType == null || entityId == null) {
            return false;
        }
        String collection = entityType.toLowerCase();
        ensureCollectionLoaded(collection);
        DeleteParam deleteParam = DeleteParam.newBuilder()
            .withCollectionName(collection)
            .withExpr(String.format("%s == \"%s\"", FIELD_ENTITY_ID, entityId))
            .build();
        R<MutationResult> response = client.delete(deleteParam);
        verifySuccess(response, "delete vector by entity id");
        return response.getData().getDeleteCnt() > 0;
    }

    @Override
    public boolean removeVectorById(String vectorId) {
        if (vectorId == null) {
            return false;
        }
        String collection = extractEntityType(vectorId);
        ensureCollectionLoaded(collection);
        DeleteParam deleteParam = DeleteParam.newBuilder()
            .withCollectionName(collection)
            .withExpr(String.format("%s == \"%s\"", FIELD_VECTOR_ID, vectorId))
            .build();
        R<MutationResult> response = client.delete(deleteParam);
        verifySuccess(response, "delete vector by id");
        return response.getData().getDeleteCnt() > 0;
    }

    @Override
    public List<String> batchStoreVectors(List<VectorRecord> vectors) {
        if (vectors == null || vectors.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> ids = new ArrayList<>(vectors.size());
        for (VectorRecord record : vectors) {
            ids.add(storeVector(record.getEntityType(), record.getEntityId(), record.getContent(),
                record.getEmbedding(), record.getMetadata()));
        }
        return ids;
    }

    @Override
    public int batchUpdateVectors(List<VectorRecord> vectors) {
        if (vectors == null || vectors.isEmpty()) {
            return 0;
        }
        batchStoreVectors(vectors);
        return vectors.size();
    }

    @Override
    public int batchRemoveVectors(List<String> vectorIds) {
        if (vectorIds == null || vectorIds.isEmpty()) {
            return 0;
        }
        int removed = 0;
        for (String id : vectorIds) {
            if (removeVectorById(id)) {
                removed++;
            }
        }
        return removed;
    }

    @Override
    public List<VectorRecord> getVectorsByEntityType(String entityType) {
        if (entityType == null) {
            return Collections.emptyList();
        }
        String collection = entityType.toLowerCase();
        ensureCollectionLoaded(collection);

        QueryParam queryParam = QueryParam.newBuilder()
            .withCollectionName(collection)
            .withExpr(String.format("%s != \"\"", FIELD_VECTOR_ID))
            .addOutField(FIELD_VECTOR_ID)
            .addOutField(FIELD_ENTITY_ID)
            .addOutField(FIELD_CONTENT)
            .addOutField(FIELD_METADATA)
            .addOutField(FIELD_VECTOR)
            .build();

        R<QueryResults> response = client.query(queryParam);
        verifySuccess(response, "query all vectors by entity type");
        QueryResultsWrapper wrapper = new QueryResultsWrapper(response.getData());
        List<VectorRecord> records = new ArrayList<>((int) wrapper.getRowCount());
        for (int i = 0; i < wrapper.getRowCount(); i++) {
            records.add(toVectorRecord(collection, wrapper, i));
        }
        return records;
    }

    @Override
    public long getVectorCountByEntityType(String entityType) {
        if (entityType == null) {
            return 0;
        }
        return getVectorsByEntityType(entityType).size();
    }

    @Override
    public boolean vectorExists(String entityType, String entityId) {
        return getVectorByEntity(entityType, entityId).isPresent();
    }

    @Override
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("configuredCollections", collectionDimensions.keySet());
        stats.put("dimensions", new HashMap<>(collectionDimensions));
        return stats;
    }

    @Override
    public long clearVectors() {
        long removed = 0;
        for (String collection : collectionDimensions.keySet()) {
            removed += clearVectorsByEntityType(collection);
        }
        return removed;
    }

    @Override
    public long clearVectorsByEntityType(String entityType) {
        if (entityType == null) {
            return 0;
        }
        List<VectorRecord> records = getVectorsByEntityType(entityType);
        List<String> ids = new ArrayList<>(records.size());
        for (VectorRecord record : records) {
            ids.add(record.getVectorId());
        }
        return batchRemoveVectors(ids);
    }

    @Override
    public void close() {
        if (client != null) {
            try {
                client.close();
            } catch (Exception ex) {
                log.warn("Error closing Milvus client: {}", ex.getMessage());
            }
        }
    }

    private void ensureCollection(String collection, int dimension) {
        Integer existing = collectionDimensions.get(collection);
        if (existing != null) {
            if (!existing.equals(dimension)) {
                throw new AIServiceException(String.format(
                    "Milvus collection '%s' was created with dimension %d but %d was provided",
                    collection, existing, dimension));
            }
            return;
        }
        synchronized (collectionDimensions) {
            existing = collectionDimensions.get(collection);
            if (existing != null) {
                if (!existing.equals(dimension)) {
                    throw new AIServiceException(String.format(
                        "Milvus collection '%s' was created with dimension %d but %d was provided",
                        collection, existing, dimension));
                }
                return;
            }
            createCollectionIfNeeded(collection, dimension);
            collectionDimensions.put(collection, dimension);
        }
    }

    private void createCollectionIfNeeded(String collection, int dimension) {
        R<Boolean> hasCollection = client.hasCollection(HasCollectionParam.newBuilder()
            .withCollectionName(collection)
            .build());
        verifySuccess(hasCollection, "check collection existence");
        if (Boolean.TRUE.equals(hasCollection.getData())) {
            int existingDimension = resolveCollectionDimension(collection);
            collectionDimensions.put(collection, existingDimension);
            return;
        }

        FieldType vectorIdField = FieldType.newBuilder()
            .withName(FIELD_VECTOR_ID)
            .withDataType(DataType.VarChar)
            .withMaxLength(128)
            .withPrimaryKey(true)
            .withAutoID(false)
            .build();

        FieldType entityIdField = FieldType.newBuilder()
            .withName(FIELD_ENTITY_ID)
            .withDataType(DataType.VarChar)
            .withMaxLength(128)
            .build();

        FieldType contentField = FieldType.newBuilder()
            .withName(FIELD_CONTENT)
            .withDataType(DataType.VarChar)
            .withMaxLength(4096)
            .build();

        FieldType metadataField = FieldType.newBuilder()
            .withName(FIELD_METADATA)
            .withDataType(DataType.VarChar)
            .withMaxLength(4096)
            .build();

        FieldType vectorField = FieldType.newBuilder()
            .withName(FIELD_VECTOR)
            .withDataType(DataType.FloatVector)
            .withDimension(dimension)
            .build();

        CreateCollectionParam createParam = CreateCollectionParam.newBuilder()
            .withCollectionName(collection)
            .withDescription("AI Infrastructure vector collection")
            .withShardsNum(2)
            .addFieldType(vectorIdField)
            .addFieldType(entityIdField)
            .addFieldType(contentField)
            .addFieldType(metadataField)
            .addFieldType(vectorField)
            .build();

        verifySuccess(client.createCollection(createParam), "create collection " + collection);
        verifySuccess(client.createIndex(CreateIndexParam.newBuilder()
            .withCollectionName(collection)
            .withFieldName(FIELD_VECTOR)
            .withIndexName(collection + "_embedding_idx")
            .withMetricType(MetricType.IP)
            .withIndexType(IndexType.IVF_FLAT)
            .withExtraParam("{\"nlist\":128}")
            .build()), "create index for collection " + collection);
        verifySuccess(client.loadCollection(LoadCollectionParam.newBuilder()
            .withCollectionName(collection)
            .build()), "load collection " + collection);
    }

    private int resolveCollectionDimension(String collection) {
        R<io.milvus.grpc.DescribeCollectionResponse> response = client.describeCollection(
            DescribeCollectionParam.newBuilder().withCollectionName(collection).build());
        verifySuccess(response, "describe collection " + collection);
        CollectionSchema schema = response.getData().getSchema();
        for (FieldSchema field : schema.getFieldsList()) {
            if (FIELD_VECTOR.equals(field.getName())) {
                for (KeyValuePair param : field.getTypeParamsList()) {
                    if ("dim".equals(param.getKey())) {
                        return Integer.parseInt(param.getValue());
                    }
                }
                throw new AIServiceException("Milvus embedding field is missing dimension metadata");
            }
        }
        throw new AIServiceException("Milvus collection is missing embedding field");
    }

    private void ensureCollectionLoaded(String collection) {
        try {
            client.loadCollection(LoadCollectionParam.newBuilder().withCollectionName(collection).build());
        } catch (Exception ex) {
            throw new AIServiceException("Failed to load Milvus collection '" + collection + "': " + ex.getMessage(), ex);
        }
    }

    private VectorRecord toVectorRecord(String collection, QueryResultsWrapper wrapper, int index) {
        try {
            FieldDataWrapper idWrapper = wrapper.getFieldWrapper(FIELD_VECTOR_ID);
            FieldDataWrapper entityWrapper = wrapper.getFieldWrapper(FIELD_ENTITY_ID);
            FieldDataWrapper vectorWrapper = wrapper.getFieldWrapper(FIELD_VECTOR);

            FieldDataWrapper contentWrapper;
            FieldDataWrapper metadataWrapper;
            try {
                contentWrapper = wrapper.getFieldWrapper(FIELD_CONTENT);
            } catch (ParamException ex) {
                contentWrapper = null;
            }
            try {
                metadataWrapper = wrapper.getFieldWrapper(FIELD_METADATA);
            } catch (ParamException ex) {
                metadataWrapper = null;
            }

            String vectorId = idWrapper.getAsString(index, FIELD_VECTOR_ID);
            String entityId = entityWrapper.getAsString(index, FIELD_ENTITY_ID);

            String content = null;
            if (contentWrapper != null) {
                Object contentValue = contentWrapper.get(index, FIELD_CONTENT);
                if (contentValue != null) {
                    content = contentValue.toString();
                }
            }

            Object metadataRaw = metadataWrapper != null ? metadataWrapper.get(index, FIELD_METADATA) : null;
            Map<String, Object> metadata = parseMetadata(metadataRaw);

            Object vectorValue = vectorWrapper.get(index, FIELD_VECTOR);
            if (!(vectorValue instanceof List<?> floatsRaw)) {
                throw new AIServiceException("Unexpected Milvus embedding payload type: " + vectorValue);
            }
            List<Double> embedding = new ArrayList<>(floatsRaw.size());
            for (Object entry : floatsRaw) {
                embedding.add(((Number) entry).doubleValue());
            }

            return VectorRecord.builder()
                .vectorId(vectorId)
                .entityType(collection)
                .entityId(entityId)
                .content(content)
                .embedding(embedding)
                .metadata(metadata)
                .build();
        } catch (ParamException | IllegalResponseException ex) {
            throw new AIServiceException("Failed to parse Milvus query response", ex);
        }
    }

    private List<Float> toFloatList(List<Double> values) {
        List<Float> floats = new ArrayList<>(values.size());
        for (Double value : values) {
            floats.add(value.floatValue());
        }
        return floats;
    }

    private String metadataToJson(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "{}";
        }
        try {
            return MAPPER.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            throw new AIServiceException("Failed to serialize metadata", ex);
        }
    }

    private Map<String, Object> parseMetadata(Object value) {
        if (value == null) {
            return Collections.emptyMap();
        }
        String json = value.toString();
        if (json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return MAPPER.readValue(json, Map.class);
        } catch (Exception ex) {
            log.warn("Failed to parse metadata JSON: {}", json, ex);
            return Collections.emptyMap();
        }
    }

    private void verifySuccess(R<?> response, String action) {
        if (response == null || response.getStatus() != R.Status.Success.getCode()) {
            String message = response != null ? response.getMessage() : "unknown error";
            throw new AIServiceException("Milvus operation failed for " + action + ": " + message);
        }
    }

    private String buildVectorId(String entityType, String entityId) {
        return entityType.toLowerCase() + "::" + entityId;
    }

    private String extractEntityType(String vectorId) {
        int idx = vectorId.indexOf("::");
        if (idx <= 0) {
            throw new AIServiceException("Invalid Milvus vector identifier: " + vectorId);
        }
        return vectorId.substring(0, idx);
    }
}
