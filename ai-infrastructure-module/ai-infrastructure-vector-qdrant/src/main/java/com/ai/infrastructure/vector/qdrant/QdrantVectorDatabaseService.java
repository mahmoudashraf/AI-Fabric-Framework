package com.ai.infrastructure.vector.qdrant;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.dto.AISearchRequest;
import com.ai.infrastructure.dto.AISearchResponse;
import com.ai.infrastructure.dto.VectorRecord;
import com.ai.infrastructure.exception.AIServiceException;
import com.ai.infrastructure.rag.VectorDatabaseService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * Vector database service backed by Qdrant's REST API.
 */
@Slf4j
public class QdrantVectorDatabaseService implements VectorDatabaseService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AIProviderConfig.QdrantConfig config;
    private final RestTemplate restTemplate;
    private final ConcurrentMap<String, Boolean> collectionCache = new ConcurrentHashMap<>();

    public QdrantVectorDatabaseService(AIProviderConfig providerConfig) {
        this.config = Objects.requireNonNull(providerConfig.getQdrant(), "Qdrant configuration must be present");
        this.restTemplate = buildRestTemplate(config);
    }

    @Override
    public String storeVector(String entityType, String entityId, String content, List<Double> embedding, Map<String, Object> metadata) {
        ensureEnabled();
        ensureCollection(entityType, embedding.size());
        String vectorId = buildVectorId(entityType, entityId);

        ObjectNode payload = MAPPER.createObjectNode();
        ArrayNode points = payload.putArray("points");
        ObjectNode point = points.addObject();
        point.put("id", vectorId);

        ArrayNode vectorArray = point.putArray("vector");
        embedding.forEach(vectorArray::add);

        ObjectNode payloadNode = point.putObject("payload");
        payloadNode.put("entityId", entityId);
        if (content != null) {
            payloadNode.put("content", content);
        }
        if (metadata != null) {
            metadata.forEach((key, value) -> payloadNode.set(key, MAPPER.valueToTree(value)));
        }

        execute(HttpMethod.PUT, collectionPath(entityType, "/points"), payload, JsonNode.class);
        return vectorId;
    }

    @Override
    public boolean updateVector(String vectorId, String entityType, String entityId, String content, List<Double> embedding, Map<String, Object> metadata) {
        ensureEnabled();
        ensureCollection(entityType, embedding.size());
        return storeVector(entityType, entityId, content, embedding, metadata) != null;
    }

    @Override
    public Optional<VectorRecord> getVector(String vectorId) {
        ensureEnabled();
        String[] parts = parseVectorId(vectorId);
        String entityType = parts[0];
        ensureCollection(entityType, null);
        ObjectNode payload = MAPPER.createObjectNode();
        payload.putArray("ids").add(vectorId);
        JsonNode response = execute(HttpMethod.POST, collectionPath(entityType, "/points/scroll"), payload, JsonNode.class);
        JsonNode points = response.path("result").path("points");
        if (!points.isArray() || points.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(toVectorRecord(entityType, points.get(0)));
    }

    @Override
    public Optional<VectorRecord> getVectorByEntity(String entityType, String entityId) {
        String vectorId = buildVectorId(entityType, entityId);
        return getVector(vectorId);
    }

    @Override
    public AISearchResponse search(List<Double> queryVector, AISearchRequest request) {
        ensureEnabled();
        String entityType = Optional.ofNullable(request.getEntityType()).orElseThrow(() ->
            new AIServiceException("Qdrant search requires request.entityType"));
        ensureCollection(entityType, queryVector.size());

        int limit = Optional.ofNullable(request.getLimit()).orElse(10);
        double threshold = Optional.ofNullable(request.getThreshold()).orElse(0.0);

        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("limit", limit);
        ArrayNode vectorArray = payload.putArray("vector");
        queryVector.forEach(vectorArray::add);

        JsonNode filterNode = buildFilterNode(request.getFilters(), request.getMetadata());
        if (filterNode != null) {
            payload.set("filter", filterNode);
        }

        JsonNode response = execute(HttpMethod.POST, collectionPath(entityType, "/points/search"), payload, JsonNode.class);
        List<VectorRecord> results = parseSearchResults(entityType, response.path("result"), threshold);

        return AISearchResponse.builder()
            .query(request.getQuery())
            .results(results.stream()
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
                .collect(Collectors.toList()))
            .totalResults(results.size())
            .model(entityType)
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
        String vectorId = buildVectorId(entityType, entityId);
        return removeVectorById(vectorId);
    }

    @Override
    public boolean removeVectorById(String vectorId) {
        ensureEnabled();
        String[] parts = parseVectorId(vectorId);
        String entityType = parts[0];
        ensureCollection(entityType, null);

        ObjectNode payload = MAPPER.createObjectNode();
        payload.putArray("points").add(vectorId);
        execute(HttpMethod.POST, collectionPath(entityType, "/points/delete"), payload, JsonNode.class);
        return true;
    }

    @Override
    public List<String> batchStoreVectors(List<VectorRecord> vectors) {
        if (CollectionUtils.isEmpty(vectors)) {
            return Collections.emptyList();
        }
        vectors.forEach(record -> storeVector(record.getEntityType(), record.getEntityId(), record.getContent(),
            record.getEmbedding(), record.getMetadata()));
        return vectors.stream()
            .map(record -> buildVectorId(record.getEntityType(), record.getEntityId()))
            .collect(Collectors.toList());
    }

    @Override
    public int batchUpdateVectors(List<VectorRecord> vectors) {
        if (CollectionUtils.isEmpty(vectors)) {
            return 0;
        }
        batchStoreVectors(vectors);
        return vectors.size();
    }

    @Override
    public int batchRemoveVectors(List<String> vectorIds) {
        if (CollectionUtils.isEmpty(vectorIds)) {
            return 0;
        }
        ObjectNode payload = MAPPER.createObjectNode();
        ArrayNode ids = payload.putArray("points");
        vectorIds.forEach(ids::add);
        String entityType = parseVectorId(vectorIds.get(0))[0];
        ensureCollection(entityType, null);
        execute(HttpMethod.POST, collectionPath(entityType, "/points/delete"), payload, JsonNode.class);
        return vectorIds.size();
    }

    @Override
    public List<VectorRecord> getVectorsByEntityType(String entityType) {
        ensureEnabled();
        ensureCollection(entityType, null);
        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("limit", 1000);
        JsonNode response = execute(HttpMethod.POST, collectionPath(entityType, "/points/scroll"), payload, JsonNode.class);
        JsonNode points = response.path("result").path("points");
        if (!points.isArray()) {
            return Collections.emptyList();
        }
        List<VectorRecord> records = new ArrayList<>();
        points.forEach(point -> records.add(toVectorRecord(entityType, point)));
        return records;
    }

    @Override
    public long getVectorCountByEntityType(String entityType) {
        ensureEnabled();
        JsonNode response = execute(HttpMethod.GET, collectionPath(entityType, ""), null, JsonNode.class);
        return response.path("result").path("points_count").asLong(0);
    }

    @Override
    public boolean vectorExists(String entityType, String entityId) {
        return getVectorByEntity(entityType, entityId).isPresent();
    }

    @Override
    public Map<String, Object> getStatistics() {
        ensureEnabled();
        JsonNode response = execute(HttpMethod.GET, "/collections", null, JsonNode.class);
        return response == null ? Collections.emptyMap() : MAPPER.convertValue(response, Map.class);
    }

    @Override
    public long clearVectors() {
        ensureEnabled();
        JsonNode response = execute(HttpMethod.GET, "/collections", null, JsonNode.class);
        JsonNode collections = response.path("result");
        if (!collections.isArray()) {
            return 0;
        }
        long removed = 0;
        for (JsonNode collection : collections) {
            String name = collection.path("name").asText(null);
            if (name != null) {
                removed += clearVectorsByEntityType(name);
            }
        }
        return removed;
    }

    @Override
    public long clearVectorsByEntityType(String entityType) {
        ensureEnabled();
        ensureCollection(entityType, null);
        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("filter", MAPPER.createObjectNode());
        execute(HttpMethod.POST, collectionPath(entityType, "/points/delete"), payload, JsonNode.class);
        return 0; // Qdrant does not return count for delete requests
    }

    private void ensureEnabled() {
        if (!config.isEnabled()) {
            throw new AIServiceException("Qdrant vector provider is disabled");
        }
    }

    private void ensureCollection(String collection, Integer vectorSize) {
        if (collectionCache.containsKey(collection)) {
            return;
        }
        synchronized (collectionCache) {
            if (collectionCache.containsKey(collection)) {
                return;
            }
            try {
                execute(HttpMethod.GET, collectionPath(collection, ""), null, JsonNode.class);
            } catch (AIServiceException ex) {
                ObjectNode payload = MAPPER.createObjectNode();
                payload.put("name", collection);
                if (vectorSize != null && vectorSize > 0) {
                    payload.putObject("vectors").put("size", vectorSize).put("distance", "Cosine");
                }
                execute(HttpMethod.PUT, "/collections/" + collection, payload, JsonNode.class);
            }
            collectionCache.put(collection, Boolean.TRUE);
        }
    }

    private JsonNode buildFilterNode(String rawFilter, Map<String, Object> metadataFilters) {
        if (hasText(rawFilter)) {
            try {
                return MAPPER.readTree(rawFilter);
            } catch (Exception ex) {
                throw new AIServiceException("Invalid Qdrant filter expression", ex);
            }
        }
        if (metadataFilters != null && !metadataFilters.isEmpty()) {
            ObjectNode filter = MAPPER.createObjectNode();
            ArrayNode must = filter.putObject("must").putArray("must");
            metadataFilters.forEach((key, value) -> {
                ObjectNode condition = must.addObject();
                condition.put("key", key);
                condition.putObject("match").putPOJO("value", value);
            });
            return filter;
        }
        return null;
    }

    private List<VectorRecord> parseSearchResults(String entityType, JsonNode results, double threshold) {
        if (!results.isArray()) {
            return Collections.emptyList();
        }
        List<VectorRecord> list = new ArrayList<>();
        for (JsonNode node : results) {
            double score = node.path("score").asDouble(0.0);
            if (score < threshold) {
                continue;
            }
            list.add(toVectorRecord(entityType, node));
        }
        return list;
    }

    private VectorRecord toVectorRecord(String entityType, JsonNode node) {
        String id = node.path("id").asText(null);
        JsonNode payload = node.path("payload");
        String entityId = payload.path("entityId").asText(null);
        String content = payload.path("content").asText(null);
        List<Double> vector = new ArrayList<>();
        JsonNode vectorNode = node.path("vector");
        if (vectorNode.isArray()) {
            vectorNode.forEach(v -> vector.add(v.asDouble()));
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        payload.fields().forEachRemaining(entry -> {
            if (!List.of("entityId", "content").contains(entry.getKey())) {
                metadata.put(entry.getKey(), MAPPER.convertValue(entry.getValue(), Object.class));
            }
        });
        return VectorRecord.builder()
            .vectorId(id)
            .entityType(entityType)
            .entityId(entityId)
            .content(content)
            .embedding(vector)
            .metadata(metadata)
            .similarityScore(node.path("score").asDouble(0.0))
            .build();
    }

    private RestTemplate buildRestTemplate(AIProviderConfig.QdrantConfig config) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int timeout = Optional.ofNullable(config.getTimeout()).orElse(30) * 1000;
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return new RestTemplate(factory);
    }

    private <T> T execute(HttpMethod method, String path, Object body, Class<T> responseType) {
        try {
            URI uri = buildUri(path);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
                headers.set("api-key", config.getApiKey());
                headers.set("Authorization", "Bearer " + config.getApiKey());
            }
            HttpEntity<Object> entity = new HttpEntity<>(body, headers);
            ResponseEntity<T> response = restTemplate.exchange(uri, method, entity, responseType);
            return response.getBody();
        } catch (HttpStatusCodeException ex) {
            String message = ex.getResponseBodyAsString();
            log.error("Qdrant request failed: {}", message);
            throw new AIServiceException("Qdrant request failed: " + message, ex);
        } catch (Exception ex) {
            throw new AIServiceException("Qdrant request failed: " + ex.getMessage(), ex);
        }
    }

    private URI buildUri(String path) throws URISyntaxException {
        String host = Optional.ofNullable(config.getHost()).orElse("localhost");
        int port = Optional.ofNullable(config.getPort()).orElse(6333);
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return new URI("http", null, host, port, normalizedPath, null, null);
    }

    private String buildVectorId(String entityType, String entityId) {
        return entityType + "::" + entityId;
    }

    private String[] parseVectorId(String vectorId) {
        String[] parts = vectorId.split("::", 2);
        if (parts.length != 2) {
            throw new AIServiceException("Invalid vector ID: " + vectorId);
        }
        return parts;
    }

    private String collectionPath(String collection, String suffix) {
        String normalizedSuffix = suffix.startsWith("/") ? suffix : "/" + suffix;
        return "/collections/" + collection + normalizedSuffix;
    }
    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
