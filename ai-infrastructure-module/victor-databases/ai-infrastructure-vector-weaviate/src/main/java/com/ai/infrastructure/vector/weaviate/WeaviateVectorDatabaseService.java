package com.ai.infrastructure.vector.weaviate;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.dto.AISearchRequest;
import com.ai.infrastructure.dto.AISearchResponse;
import com.ai.infrastructure.dto.VectorRecord;
import com.ai.infrastructure.exception.AIServiceException;
import com.ai.infrastructure.rag.VectorDatabaseService;
import com.fasterxml.jackson.core.JsonProcessingException;
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
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Vector database implementation backed by Weaviate's REST API.
 *
 * <p>The service focuses on the common CRUD/search operations required by the
 * AI infrastructure module. Additional methods fall back to simple iteration
 * strategies when the Weaviate API does not expose a dedicated endpoint.</p>
 */
@Slf4j
public class WeaviateVectorDatabaseService implements VectorDatabaseService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AIProviderConfig.WeaviateConfig config;
    private final RestTemplate restTemplate;
    private final Set<String> knownClasses = ConcurrentHashMap.newKeySet();

    public WeaviateVectorDatabaseService(AIProviderConfig providerConfig) {
        this.config = Objects.requireNonNull(providerConfig.getWeaviate(), "Weaviate configuration must be present");
        this.restTemplate = buildRestTemplate(config);
    }

    @Override
    public String storeVector(String entityType, String entityId, String content,
                              List<Double> embedding, Map<String, Object> metadata) {
        ensureEnabled();
        ensureClassExists(entityType);
        String vectorId = buildVectorId(entityType, entityId);

        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("id", vectorId);
        payload.put("class", entityType);

        ObjectNode properties = MAPPER.createObjectNode();
        properties.put("entityId", entityId);
        if (content != null) {
            properties.put("content", content);
        }
        if (metadata != null) {
            metadata.forEach((key, value) -> properties.set(key, MAPPER.valueToTree(value)));
        }
        payload.set("properties", properties);

        if (!CollectionUtils.isEmpty(embedding)) {
            ArrayNode vector = payload.putArray("vector");
            embedding.forEach(vector::add);
        }

        execute(HttpMethod.POST, "/v1/objects", payload, JsonNode.class);
        return vectorId;
    }

    @Override
    public boolean updateVector(String vectorId, String entityType, String entityId, String content,
                                List<Double> embedding, Map<String, Object> metadata) {
        ensureEnabled();
        ensureClassExists(entityType);

        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("id", vectorId);
        payload.put("class", entityType);

        ObjectNode properties = MAPPER.createObjectNode();
        properties.put("entityId", entityId);
        if (content != null) {
            properties.put("content", content);
        }
        if (metadata != null) {
            metadata.forEach((key, value) -> properties.set(key, MAPPER.valueToTree(value)));
        }
        payload.set("properties", properties);

        if (!CollectionUtils.isEmpty(embedding)) {
            ArrayNode vector = payload.putArray("vector");
            embedding.forEach(vector::add);
        }

        try {
            execute(HttpMethod.PUT, String.format("/v1/objects/%s", vectorId), payload, JsonNode.class);
            return true;
        } catch (AIServiceException ex) {
            log.warn("Failed to update vector {}: {}", vectorId, ex.getMessage());
            return false;
        }
    }

    @Override
    public Optional<VectorRecord> getVector(String vectorId) {
        ensureEnabled();
        try {
            JsonNode response = execute(HttpMethod.GET, String.format("/v1/objects/%s", vectorId), null, JsonNode.class);
            return Optional.ofNullable(response).map(this::toVectorRecord);
        } catch (AIServiceException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("not found")) {
                return Optional.empty();
            }
            throw ex;
        }
    }

    @Override
    public Optional<VectorRecord> getVectorByEntity(String entityType, String entityId) {
        String vectorId = buildVectorId(entityType, entityId);
        return getVector(vectorId);
    }

    @Override
    public AISearchResponse search(List<Double> queryVector, AISearchRequest request) {
        return executeSearch(queryVector, request, null);
    }

    @Override
    public AISearchResponse searchByEntityType(List<Double> queryVector, String entityType, int limit, double threshold) {
        AISearchRequest searchRequest = AISearchRequest.builder()
            .entityType(entityType)
            .limit(limit)
            .threshold(threshold)
            .build();
        return executeSearch(queryVector, searchRequest, entityType);
    }

    @Override
    public boolean removeVector(String entityType, String entityId) {
        ensureEnabled();
        ensureClassExists(entityType);
        String vectorId = buildVectorId(entityType, entityId);
        return removeVectorById(vectorId);
    }

    @Override
    public boolean removeVectorById(String vectorId) {
        ensureEnabled();
        try {
            execute(HttpMethod.DELETE, String.format("/v1/objects/%s", vectorId), null, Void.class);
            return true;
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
        ensureClassExists(entityType);
        String query = "{ Get { " + entityType + "(limit: 1000) { _additional { id vector } entityId content } } }";
        JsonNode result = executeGraphQL(query);
        return parseSearchResults(entityType, result, 0.0);
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
        JsonNode stats = execute(HttpMethod.GET, "/v1/meta", null, JsonNode.class);
        return stats == null ? Collections.emptyMap() : MAPPER.convertValue(stats, Map.class);
    }

    @Override
    public long clearVectors() {
        ensureEnabled();
        long removed = 0;
        for (String className : new HashSet<>(knownClasses)) {
            removed += clearVectorsByEntityType(className);
        }
        return removed;
    }

    @Override
    public long clearVectorsByEntityType(String entityType) {
        ensureEnabled();
        ensureClassExists(entityType);
        List<VectorRecord> records = getVectorsByEntityType(entityType);
        records.forEach(record -> removeVectorById(record.getVectorId()));
        return records.size();
    }

    private AISearchResponse executeSearch(List<Double> queryVector, AISearchRequest request, String explicitClass) {
        ensureEnabled();
        String entityType = explicitClass != null ? explicitClass : request.getEntityType();
        ensureClassExists(entityType);

        int limit = request.getLimit() != null ? request.getLimit() : 10;
        double threshold = request.getThreshold() != null ? request.getThreshold() : 0.0;

        String query = buildNearVectorQuery(entityType, queryVector, limit, request.getFilters());
        JsonNode response = executeGraphQL(query);
        List<VectorRecord> results = parseSearchResults(entityType, response, threshold);

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

    private List<VectorRecord> parseSearchResults(String entityType, JsonNode response, double threshold) {
        if (response == null) {
            return Collections.emptyList();
        }
        JsonNode data = response.path("data");
        if (data.isMissingNode()) {
            return Collections.emptyList();
        }
        JsonNode getNode = data.path("Get");
        if (getNode.isMissingNode()) {
            return Collections.emptyList();
        }
        JsonNode classNode = getNode.path(entityType);
        if (!classNode.isArray()) {
            return Collections.emptyList();
        }

        List<VectorRecord> results = new ArrayList<>();
        for (JsonNode row : classNode) {
            JsonNode additional = row.path("_additional");
            String id = additional.path("id").asText(null);
            double distance = additional.path("distance").asDouble(Double.NaN);
            double score = Double.isNaN(distance) ? 0.0 : 1.0 - distance;
            if (score < threshold) {
                continue;
            }

            JsonNode vectorNode = additional.path("vector");
            List<Double> vector = new ArrayList<>();
            if (vectorNode.isArray()) {
                vectorNode.forEach(v -> vector.add(v.asDouble()));
            }

            String entityId = row.path("entityId").asText(null);
            String content = row.path("content").asText(null);

            Map<String, Object> metadata = new LinkedHashMap<>();
            row.fields().forEachRemaining(entry -> {
                if (!List.of("_additional", "entityId", "content").contains(entry.getKey())) {
                    metadata.put(entry.getKey(), MAPPER.convertValue(entry.getValue(), Object.class));
                }
            });

            results.add(VectorRecord.builder()
                .vectorId(id)
                .entityType(entityType)
                .entityId(entityId)
                .content(content)
                .embedding(vector)
                .metadata(metadata)
                .similarityScore(score)
                .build());
        }
        return results;
    }

    private JsonNode executeGraphQL(String query) {
        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("query", query);
        return execute(HttpMethod.POST, "/v1/graphql", payload, JsonNode.class);
    }

    private <T> T execute(HttpMethod method, String path, Object payload, Class<T> responseType) {
        try {
            URI uri = buildUri(path);
            HttpHeaders headers = buildHeaders();
            if (payload != null) {
                headers.setContentType(MediaType.APPLICATION_JSON);
            }
            HttpEntity<Object> entity = new HttpEntity<>(payload, headers);
            ResponseEntity<T> response = restTemplate.exchange(uri, method, entity, responseType);
            return response.getBody();
        } catch (HttpStatusCodeException ex) {
            String message = ex.getResponseBodyAsString();
            log.error("Weaviate request failed: {}", message);
            throw new AIServiceException("Weaviate request failed: " + message, ex);
        } catch (Exception ex) {
            throw new AIServiceException("Weaviate request failed: " + ex.getMessage(), ex);
        }
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        if (hasText(config.getApiKey())) {
            headers.set("Authorization", "Bearer " + config.getApiKey());
            headers.set("X-API-Key", config.getApiKey());
        }
        return headers;
    }

    private URI buildUri(String path) throws URISyntaxException {
        String scheme = hasText(config.getScheme()) ? config.getScheme() : "https";
        String host = Objects.requireNonNull(config.getHost(), "Weaviate host must be configured");
        int port = config.getPort() != null ? config.getPort() : ("https".equalsIgnoreCase(scheme) ? 443 : 80);
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return new URI(scheme, null, host, port, normalizedPath, null, null);
    }

    private RestTemplate buildRestTemplate(AIProviderConfig.WeaviateConfig config) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int timeout = Optional.ofNullable(config.getTimeout()).orElse(30) * 1000;
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return new RestTemplate(factory);
    }

    private void ensureClassExists(String className) {
        if (knownClasses.contains(className)) {
            return;
        }
        synchronized (knownClasses) {
            if (knownClasses.contains(className)) {
                return;
            }
            try {
                execute(HttpMethod.GET, "/v1/schema", null, JsonNode.class);
                knownClasses.add(className);
            } catch (AIServiceException ex) {
                log.debug("Schema lookup failed: {}", ex.getMessage());
            }
        }
    }

    private void ensureEnabled() {
        if (!config.isEnabled()) {
            throw new AIServiceException("Weaviate vector provider is disabled");
        }
    }

    private String buildVectorId(String entityType, String entityId) {
        return entityType + "::" + entityId;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String buildNearVectorQuery(String entityType, List<Double> vector, int limit, String filterExpression) {
        StringBuilder builder = new StringBuilder();
        builder.append("{ Get { ").append(entityType).append("(");
        builder.append("limit: ").append(limit);
        builder.append(", nearVector: { vector: [");
        StringJoiner joiner = new StringJoiner(", ");
        vector.forEach(v -> joiner.add(Double.toString(v)));
        builder.append(joiner.toString()).append("] }");

        if (hasText(filterExpression)) {
            builder.append(", where: ").append(filterExpression);
        }

        builder.append(") { _additional { id distance vector } entityId content }");
        builder.append(" } } }");
        return builder.toString();
    }


    private VectorRecord toVectorRecord(JsonNode node) {
        if (node == null) {
            return null;
        }
        String id = node.path("id").asText(null);
        String entityType = node.path("class").asText(null);
        JsonNode properties = node.path("properties");
        String entityId = properties.path("entityId").asText(null);
        String content = properties.path("content").asText(null);
        Map<String, Object> metadata = new LinkedHashMap<>();
        properties.fields().forEachRemaining(entry -> {
            if (!List.of("entityId", "content").contains(entry.getKey())) {
                metadata.put(entry.getKey(), MAPPER.convertValue(entry.getValue(), Object.class));
            }
        });

        JsonNode vectorNode = node.path("vector");
        List<Double> embedding = new ArrayList<>();
        if (vectorNode.isArray()) {
            vectorNode.forEach(v -> embedding.add(v.asDouble()));
        }

        return VectorRecord.builder()
            .vectorId(id)
            .entityType(entityType)
            .entityId(entityId)
            .content(content)
            .embedding(embedding)
            .metadata(metadata)
            .build();
    }
}
