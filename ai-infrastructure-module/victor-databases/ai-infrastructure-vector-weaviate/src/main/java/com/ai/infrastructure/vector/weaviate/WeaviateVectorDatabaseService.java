package com.ai.infrastructure.vector.weaviate;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.dto.AISearchRequest;
import com.ai.infrastructure.dto.AISearchResponse;
import com.ai.infrastructure.dto.VectorRecord;
import com.ai.infrastructure.exception.AIServiceException;
import com.ai.infrastructure.rag.VectorDatabaseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.ai.infrastructure.util.MetadataJsonSerializer;
import io.weaviate.client.Config;
import io.weaviate.client.WeaviateClient;
import io.weaviate.client.base.Result;
import io.weaviate.client.base.WeaviateError;
import io.weaviate.client.v1.data.model.WeaviateObject;
import io.weaviate.client.v1.graphql.model.GraphQLResponse;
import io.weaviate.client.v1.graphql.query.argument.NearVectorArgument;
import io.weaviate.client.v1.graphql.query.fields.Field;
import io.weaviate.client.v1.misc.model.Meta;
import io.weaviate.client.v1.schema.model.DataType;
import io.weaviate.client.v1.schema.model.Property;
import io.weaviate.client.v1.schema.model.WeaviateClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

/**
 * Vector database implementation backed by Weaviate using the official Java client.
 */
@Slf4j
public class WeaviateVectorDatabaseService implements VectorDatabaseService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> METADATA_TYPE = new TypeReference<>() {
    };

    private static final String MODEL_NAME = "weaviate";
    private static final String PROPERTY_ENTITY_TYPE = "entityType";
    private static final String PROPERTY_ENTITY_ID = "entityId";
    private static final String PROPERTY_CONTENT = "content";
    private static final String PROPERTY_RAW = "raw";

    private final AIProviderConfig.WeaviateConfig config;
    private final WeaviateClient client;
    private final Set<String> knownClasses = ConcurrentHashMap.newKeySet(); // cache of class names
    private final Set<String> knownEntityTypes = ConcurrentHashMap.newKeySet(); // cache of original entity types

    public WeaviateVectorDatabaseService(AIProviderConfig providerConfig) {
        this.config = Objects.requireNonNull(providerConfig.getWeaviate(), "Weaviate configuration must be present");
        this.client = buildClient(config);
    }

    @Override
    public String storeVector(String entityType, String entityId, String content,
                              List<Double> embedding, Map<String, Object> metadata) {
        ensureEnabled();
        requireText(entityType, "entityType");
        requireText(entityId, "entityId");
        if (CollectionUtils.isEmpty(embedding)) {
            throw new AIServiceException("Weaviate storeVector requires a non-empty embedding vector");
        }

        String className = toClassName(entityType);
        ensureClassExists(entityType, className);
        knownEntityTypes.add(entityType);

        String vectorId = buildVectorId(entityType, entityId);
        upsertObject(className, vectorId, buildProperties(entityType, entityId, content, metadata), toFloatArray(embedding));
        return vectorId;
    }

    @Override
    public boolean updateVector(String vectorId, String entityType, String entityId, String content,
                                List<Double> embedding, Map<String, Object> metadata) {
        ensureEnabled();
        if (!hasText(vectorId) || !hasText(entityType) || !hasText(entityId)) {
            return false;
        }
        if (CollectionUtils.isEmpty(embedding)) {
            throw new AIServiceException("Weaviate updateVector requires a non-empty embedding vector");
        }

        try {
            String className = toClassName(entityType);
            ensureClassExists(entityType, className);
            knownEntityTypes.add(entityType);
            upsertObject(className, vectorId, buildProperties(entityType, entityId, content, metadata), toFloatArray(embedding));
            return true;
        } catch (AIServiceException ex) {
            log.warn("Failed to update vector {}: {}", vectorId, ex.getMessage());
            return false;
        }
    }

    @Override
    public Optional<VectorRecord> getVector(String vectorId) {
        ensureEnabled();
        if (!hasText(vectorId)) {
            return Optional.empty();
        }

        Result<List<WeaviateObject>> result = client.data()
            .objectsGetter()
            .withID(vectorId)
            .withVector()
            .run();

        if (result.hasErrors()) {
            if (isNotFound(result.getError())) {
                return Optional.empty();
            }
            throw new AIServiceException("Weaviate getVector failed: " + errorMessages(result.getError()));
        }

        if (CollectionUtils.isEmpty(result.getResult())) {
            return Optional.empty();
        }

        return Optional.ofNullable(toVectorRecord(result.getResult().getFirst(), null));
    }

    @Override
    public Optional<VectorRecord> getVectorByEntity(String entityType, String entityId) {
        ensureEnabled();
        if (!hasText(entityType) || !hasText(entityId)) {
            return Optional.empty();
        }

        String className = toClassName(entityType);
        if (!classExists(className)) {
            return Optional.empty();
        }

        String vectorId = buildVectorId(entityType, entityId);
        Result<List<WeaviateObject>> result = client.data()
            .objectsGetter()
            .withClassName(className)
            .withID(vectorId)
            .withVector()
            .run();

        if (result.hasErrors()) {
            if (isNotFound(result.getError())) {
                return Optional.empty();
            }
            throw new AIServiceException("Weaviate getVectorByEntity failed: " + errorMessages(result.getError()));
        }

        if (CollectionUtils.isEmpty(result.getResult())) {
            return Optional.empty();
        }

        return Optional.ofNullable(toVectorRecord(result.getResult().getFirst(), null));
    }

    @Override
    public AISearchResponse search(List<Double> queryVector, AISearchRequest request) {
        ensureEnabled();
        if (request == null) {
            throw new AIServiceException("Weaviate search requires a request");
        }
        if (CollectionUtils.isEmpty(queryVector)) {
            throw new AIServiceException("Weaviate search requires a non-empty query vector");
        }

        int limit = Optional.ofNullable(request.getLimit()).orElse(10);
        double threshold = Optional.ofNullable(request.getThreshold()).orElse(0.0);

        List<String> entityTypes = request.getEntityType() != null
            ? List.of(request.getEntityType())
            : new ArrayList<>(knownEntityTypes);

        if (entityTypes.isEmpty()) {
            return AISearchResponse.builder()
                .query(request.getQuery())
                .results(List.of())
                .totalResults(0)
                .model(MODEL_NAME)
                .build();
        }

        Float[] queryFloatVector = toFloatArray(queryVector);

        List<VectorRecord> allResults = new ArrayList<>();
        for (String entityType : entityTypes) {
            if (!hasText(entityType)) {
                continue;
            }
            String className = toClassName(entityType);
            if (!classExists(className)) {
                continue;
            }
            allResults.addAll(searchClass(className, queryFloatVector, limit, threshold));
        }

        List<Map<String, Object>> mapped = allResults.stream()
            .sorted((left, right) -> Double.compare(
                Optional.ofNullable(right.getSimilarityScore()).orElse(0.0),
                Optional.ofNullable(left.getSimilarityScore()).orElse(0.0)))
            .limit(limit)
            .map(record -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("vectorId", record.getVectorId());
                row.put("entityId", record.getEntityId());
                row.put("entityType", record.getEntityType());
                row.put("content", record.getContent());
                row.put("metadata", record.getMetadata());
                row.put("score", record.getSimilarityScore());
                return row;
            })
            .toList();

        return AISearchResponse.builder()
            .query(request.getQuery())
            .results(mapped)
            .totalResults(mapped.size())
            .model(MODEL_NAME)
            .build();
    }

    @Override
    public AISearchResponse searchByEntityType(List<Double> queryVector, String entityType, int limit, double threshold) {
        return search(queryVector, AISearchRequest.builder()
            .entityType(entityType)
            .limit(limit)
            .threshold(threshold)
            .build());
    }

    @Override
    public boolean removeVector(String entityType, String entityId) {
        ensureEnabled();
        if (!hasText(entityType) || !hasText(entityId)) {
            return false;
        }

        String className = toClassName(entityType);
        if (!classExists(className)) {
            return false;
        }

        return deleteById(className, buildVectorId(entityType, entityId));
    }

    @Override
    public boolean removeVectorById(String vectorId) {
        ensureEnabled();
        if (!hasText(vectorId)) {
            return false;
        }
        try {
            Result<Boolean> result = client.data()
                .deleter()
                .withID(vectorId)
                .run();

            if (result.hasErrors()) {
                if (isNotFound(result.getError())) {
                    return false;
                }
                log.warn("Weaviate delete failed for {}: {}", vectorId, errorMessages(result.getError()));
                return false;
            }

            return Boolean.TRUE.equals(result.getResult());
        } catch (AIServiceException ex) {
            log.warn("Failed to remove vector {}: {}", vectorId, ex.getMessage());
            return false;
        }
    }

    @Override
    public List<String> batchStoreVectors(List<VectorRecord> vectors) {
        if (CollectionUtils.isEmpty(vectors)) {
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
        if (CollectionUtils.isEmpty(vectors)) {
            return 0;
        }
        int updated = 0;
        for (VectorRecord record : vectors) {
            String vectorId = buildVectorId(record.getEntityType(), record.getEntityId());
            if (updateVector(vectorId, record.getEntityType(), record.getEntityId(), record.getContent(),
                record.getEmbedding(), record.getMetadata())) {
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
        ensureEnabled();
        if (!hasText(entityType)) {
            return List.of();
        }

        String className = toClassName(entityType);
        if (!classExists(className)) {
            return List.of();
        }

        Result<List<WeaviateObject>> result = client.data()
            .objectsGetter()
            .withClassName(className)
            .withVector()
            .withLimit(1000)
            .run();

        if (result.hasErrors()) {
            if (isNotFound(result.getError())) {
                return List.of();
            }
            throw new AIServiceException("Weaviate list vectors failed: " + errorMessages(result.getError()));
        }

        if (CollectionUtils.isEmpty(result.getResult())) {
            return List.of();
        }

        return result.getResult().stream()
            .map(obj -> toVectorRecord(obj, null))
            .filter(Objects::nonNull)
            .toList();
    }

    @Override
    public long getVectorCountByEntityType(String entityType) {
        return getVectorsByEntityType(entityType).size();
    }

    @Override
    public boolean vectorExists(String entityType, String entityId) {
        return getVectorByEntity(entityType, entityId).isPresent();
    }

    @Override
    public Map<String, Object> getStatistics() {
        ensureEnabled();
        Result<Meta> metaResult = client.misc().metaGetter().run();
        if (metaResult.hasErrors() || metaResult.getResult() == null) {
            return Map.of(
                "knownEntityTypes", new ArrayList<>(knownEntityTypes),
                "meta", Collections.emptyMap()
            );
        }

        Map<String, Object> meta = OBJECT_MAPPER.convertValue(metaResult.getResult(), Map.class);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("knownEntityTypes", new ArrayList<>(knownEntityTypes));
        response.put("knownClasses", new ArrayList<>(knownClasses));
        response.put("meta", meta);
        return response;
    }

    @Override
    public long clearVectors() {
        ensureEnabled();
        long removed = 0;
        for (String entityType : Set.copyOf(knownEntityTypes)) {
            removed += clearVectorsByEntityType(entityType);
        }
        return removed;
    }

    @Override
    public long clearVectorsByEntityType(String entityType) {
        ensureEnabled();
        if (!hasText(entityType)) {
            return 0;
        }
        List<VectorRecord> records = getVectorsByEntityType(entityType);
        records.forEach(record -> removeVectorById(record.getVectorId()));
        return records.size();
    }

    private void ensureEnabled() {
        if (!config.isEnabled()) {
            throw new AIServiceException("Weaviate vector provider is disabled");
        }
    }

    private String buildVectorId(String entityType, String entityId) {
        return UUID.nameUUIDFromBytes((entityType + "::" + entityId).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private WeaviateClient buildClient(AIProviderConfig.WeaviateConfig config) {
        String scheme = hasText(config.getScheme()) ? config.getScheme().trim() : "https";
        String host = Objects.requireNonNull(config.getHost(), "Weaviate host must be configured");
        int port = config.getPort() != null ? config.getPort() : ("https".equalsIgnoreCase(scheme) ? 443 : 80);
        int timeoutSeconds = Optional.ofNullable(config.getTimeout()).orElse(30);

        Map<String, String> headers = new HashMap<>();
        if (hasText(config.getApiKey())) {
            headers.put("Authorization", "Bearer " + config.getApiKey());
            headers.put("X-API-Key", config.getApiKey());
        }

        Config clientConfig = new Config(scheme, host + ":" + port, headers, timeoutSeconds);
        log.info("Weaviate client initialized: {}://{}:{}", scheme, host, port);
        return new WeaviateClient(clientConfig);
    }

    private void ensureClassExists(String entityType, String className) {
        if (knownClasses.contains(className)) {
            return;
        }

        synchronized (knownClasses) {
            if (knownClasses.contains(className)) {
                return;
            }

            if (!classExists(className)) {
                createClass(entityType, className);
            }

            knownClasses.add(className);
        }
    }

    private boolean classExists(String className) {
        Result<Boolean> result = client.schema().exists().withClassName(className).run();
        if (!result.hasErrors()) {
            return Boolean.TRUE.equals(result.getResult());
        }
        if (isNotFound(result.getError())) {
            return false;
        }
        throw new AIServiceException("Weaviate class existence check failed: " + errorMessages(result.getError()));
    }

    private void createClass(String entityType, String className) {
        List<Property> properties = List.of(
            Property.builder()
                .name(PROPERTY_ENTITY_TYPE)
                .dataType(List.of(DataType.TEXT))
                .description("Original entity type")
                .build(),
            Property.builder()
                .name(PROPERTY_ENTITY_ID)
                .dataType(List.of(DataType.TEXT))
                .description("Entity identifier")
                .build(),
            Property.builder()
                .name(PROPERTY_CONTENT)
                .dataType(List.of(DataType.TEXT))
                .description("Entity content")
                .build(),
            Property.builder()
                .name(PROPERTY_RAW)
                .dataType(List.of(DataType.TEXT))
                .description("Metadata JSON")
                .build()
        );

        WeaviateClass weaviateClass = WeaviateClass.builder()
            .className(className)
            .description("AI-Fabric class for entityType: " + entityType)
            .vectorizer("none")
            .properties(properties)
            .build();

        Result<Boolean> result = client.schema()
            .classCreator()
            .withClass(weaviateClass)
            .run();

        if (result.hasErrors()) {
            throw new AIServiceException("Failed to create Weaviate class '" + className + "': " + errorMessages(result.getError()));
        }

        log.info("Created Weaviate class {} for entityType {}", className, entityType);
    }

    private String toClassName(String entityType) {
        String normalized = entityType == null ? "" : entityType.trim();
        if (normalized.isEmpty()) {
            return "Entity_" + shortHash("");
        }

        StringBuilder base = new StringBuilder();
        boolean upperNext = true;
        for (int i = 0; i < normalized.length(); i++) {
            char current = normalized.charAt(i);
            if (Character.isLetterOrDigit(current)) {
                base.append(upperNext ? Character.toUpperCase(current) : current);
                upperNext = false;
            } else {
                upperNext = true;
            }
        }

        if (base.isEmpty()) {
            base.append("Entity");
        }

        if (!Character.isLetter(base.charAt(0))) {
            base.insert(0, "Entity");
        }

        if (!Character.isUpperCase(base.charAt(0))) {
            base.setCharAt(0, Character.toUpperCase(base.charAt(0)));
        }

        return base + "_" + shortHash(normalized);
    }

    private String shortHash(String value) {
        String compact = UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8))
            .toString()
            .replace("-", "");
        return compact.substring(0, 8);
    }

    private Float[] toFloatArray(List<Double> vector) {
        Float[] values = new Float[vector.size()];
        for (int i = 0; i < vector.size(); i++) {
            values[i] = vector.get(i).floatValue();
        }
        return values;
    }

    private List<Double> toDoubleList(Float[] vector) {
        if (vector == null || vector.length == 0) {
            return List.of();
        }
        List<Double> values = new ArrayList<>(vector.length);
        for (Float value : vector) {
            values.add(value == null ? 0.0 : value.doubleValue());
        }
        return values;
    }

    private Map<String, Object> buildProperties(String entityType, String entityId, String content, Map<String, Object> metadata) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put(PROPERTY_ENTITY_TYPE, entityType);
        props.put(PROPERTY_ENTITY_ID, entityId);
        if (content != null) {
            props.put(PROPERTY_CONTENT, content);
        }

        Map<String, Object> safeMetadata = metadata == null ? Collections.emptyMap() : metadata;
        props.put(PROPERTY_RAW, MetadataJsonSerializer.serialize(stripRaw(safeMetadata)));
        return props;
    }

    private Map<String, Object> stripRaw(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty() || !metadata.containsKey(PROPERTY_RAW)) {
            return metadata == null ? Collections.emptyMap() : metadata;
        }
        Map<String, Object> copy = new LinkedHashMap<>(metadata);
        copy.remove(PROPERTY_RAW);
        return copy;
    }

    private void upsertObject(String className, String vectorId, Map<String, Object> properties, Float[] vector) {
        client.data()
            .deleter()
            .withClassName(className)
            .withID(vectorId)
            .run();

        Result<WeaviateObject> result = client.data()
            .creator()
            .withClassName(className)
            .withID(vectorId)
            .withProperties(properties)
            .withVector(vector)
            .run();

        if (result.hasErrors()) {
            throw new AIServiceException("Failed to upsert Weaviate object " + vectorId + ": " + errorMessages(result.getError()));
        }
    }

    private boolean deleteById(String className, String vectorId) {
        Result<Boolean> result = client.data()
            .deleter()
            .withClassName(className)
            .withID(vectorId)
            .run();

        if (result.hasErrors()) {
            if (isNotFound(result.getError())) {
                return false;
            }
            log.warn("Weaviate delete failed for {}: {}", vectorId, errorMessages(result.getError()));
            return false;
        }
        return Boolean.TRUE.equals(result.getResult());
    }

    private List<VectorRecord> searchClass(String className, Float[] queryVector, int limit, double threshold) {
        NearVectorArgument nearVector = NearVectorArgument.builder()
            .vector(queryVector)
            .build();

        Field[] fields = new Field[]{
            Field.builder().name(PROPERTY_ENTITY_TYPE).build(),
            Field.builder().name(PROPERTY_ENTITY_ID).build(),
            Field.builder().name(PROPERTY_CONTENT).build(),
            Field.builder().name(PROPERTY_RAW).build(),
            Field.builder().name("_additional").fields(
                Field.builder().name("id").build(),
                Field.builder().name("certainty").build(),
                Field.builder().name("distance").build(),
                Field.builder().name("vector").build()
            ).build()
        };

        Result<GraphQLResponse> result = client.graphQL()
            .get()
            .withClassName(className)
            .withNearVector(nearVector)
            .withLimit(limit)
            .withFields(fields)
            .run();

        if (result.hasErrors()) {
            throw new AIServiceException("Weaviate search failed for class " + className + ": " + errorMessages(result.getError()));
        }

        return parseGraphQLRecords(className, result.getResult(), threshold);
    }

    private List<VectorRecord> parseGraphQLRecords(String className, GraphQLResponse response, double threshold) {
        if (response == null || response.getData() == null) {
            return List.of();
        }

        if (!(response.getData() instanceof Map<?, ?> dataMap)) {
            return List.of();
        }
        Object getObject = dataMap.get("Get");
        if (!(getObject instanceof Map<?, ?> getMap)) {
            return List.of();
        }
        Object classResults = getMap.get(className);
        if (!(classResults instanceof List<?> results)) {
            return List.of();
        }

        List<VectorRecord> records = new ArrayList<>(results.size());
        for (Object entry : results) {
            if (!(entry instanceof Map<?, ?> row)) {
                continue;
            }

            Object additionalObject = row.get("_additional");
            if (!(additionalObject instanceof Map<?, ?> additional)) {
                continue;
            }

            String vectorId = String.valueOf(additional.get("id"));

            Double certainty = toDouble(additional.get("certainty"));
            Double distance = toDouble(additional.get("distance"));
            double score = certainty != null ? certainty : (distance != null ? Math.max(0.0, 1.0 - distance) : 0.0);

            if (score < threshold) {
                continue;
            }

            List<Double> embedding = toDoubleList(extractVector(additional.get("vector")));

            String entityId = row.get(PROPERTY_ENTITY_ID) != null ? String.valueOf(row.get(PROPERTY_ENTITY_ID)) : null;
            String entityType = row.get(PROPERTY_ENTITY_TYPE) != null ? String.valueOf(row.get(PROPERTY_ENTITY_TYPE)) : null;
            String content = row.get(PROPERTY_CONTENT) != null ? String.valueOf(row.get(PROPERTY_CONTENT)) : null;
            String raw = row.get(PROPERTY_RAW) != null ? String.valueOf(row.get(PROPERTY_RAW)) : "{}";

            Map<String, Object> metadata = parseRawMetadata(raw);

            records.add(VectorRecord.builder()
                .vectorId(vectorId)
                .entityType(entityType)
                .entityId(entityId)
                .content(content)
                .embedding(embedding)
                .metadata(metadata)
                .similarityScore(score)
                .build());
        }

        return records;
    }

    private Float[] extractVector(Object vectorValue) {
        if (!(vectorValue instanceof List<?> list) || list.isEmpty()) {
            return new Float[0];
        }

        Float[] vector = new Float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            Object element = list.get(i);
            if (element instanceof Number number) {
                vector[i] = number.floatValue();
            } else if (element instanceof String str) {
                try {
                    vector[i] = Float.parseFloat(str);
                } catch (NumberFormatException ignored) {
                    vector[i] = 0.0f;
                }
            } else {
                vector[i] = 0.0f;
            }
        }
        return vector;
    }

    private Double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String string && hasText(string)) {
            try {
                return Double.parseDouble(string);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Map<String, Object> parseRawMetadata(String raw) {
        if (!hasText(raw) || "{}".equals(raw.trim())) {
            return Map.of(PROPERTY_RAW, "{}");
        }
        try {
            Map<String, Object> parsed = OBJECT_MAPPER.readValue(raw, METADATA_TYPE);
            Map<String, Object> metadata = parsed == null ? new LinkedHashMap<>() : new LinkedHashMap<>(parsed);
            metadata.put(PROPERTY_RAW, raw);
            return metadata;
        } catch (Exception ex) {
            return Map.of(PROPERTY_RAW, raw);
        }
    }

    private VectorRecord toVectorRecord(WeaviateObject object, Double similarityScore) {
        if (object == null) {
            return null;
        }

        Map<String, Object> props = object.getProperties() != null ? object.getProperties() : Collections.emptyMap();
        String entityType = props.get(PROPERTY_ENTITY_TYPE) != null ? String.valueOf(props.get(PROPERTY_ENTITY_TYPE)) : null;
        String entityId = props.get(PROPERTY_ENTITY_ID) != null ? String.valueOf(props.get(PROPERTY_ENTITY_ID)) : null;
        String content = props.get(PROPERTY_CONTENT) != null ? String.valueOf(props.get(PROPERTY_CONTENT)) : null;
        String raw = props.get(PROPERTY_RAW) != null ? String.valueOf(props.get(PROPERTY_RAW)) : "{}";

        return VectorRecord.builder()
            .vectorId(object.getId())
            .entityType(entityType)
            .entityId(entityId)
            .content(content)
            .embedding(toDoubleList(object.getVector()))
            .metadata(parseRawMetadata(raw))
            .similarityScore(similarityScore)
            .build();
    }

    private boolean isNotFound(WeaviateError error) {
        return error != null && error.getStatusCode() == 404;
    }

    private String errorMessages(WeaviateError error) {
        if (error == null || error.getMessages() == null || error.getMessages().isEmpty()) {
            return "unknown error";
        }
        return error.getMessages().toString();
    }

    private void requireText(String value, String field) {
        if (!hasText(value)) {
            throw new AIServiceException(field + " must not be blank");
        }
    }
}
