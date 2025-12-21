package com.ai.infrastructure.vector.pinecone;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.dto.AISearchRequest;
import com.ai.infrastructure.dto.AISearchResponse;
import com.ai.infrastructure.dto.VectorRecord;
import com.ai.infrastructure.rag.VectorDatabaseService;
import com.ai.infrastructure.exception.AIServiceException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.annotation.PostConstruct;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;

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
@RequiredArgsConstructor
public class PineconeVectorDatabaseService implements VectorDatabaseService {
    
    private final AIProviderConfig config;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String API_KEY_HEADER = "Api-Key";
    private static final String DEFAULT_NAMESPACE = "default";

    private URI baseUri;

    @PostConstruct
    void initializeClient() {
        this.baseUri = URI.create(resolveBaseUrl());
        log.info("Pinecone client configured for index '{}' at {}", config.getPinecone().getIndexName(), baseUri);
    }
    
    @Override
    public String storeVector(String entityType, String entityId, String content, 
                           List<Double> embedding, Map<String, Object> metadata) {
        return upsertSingleVector(entityType, entityId, content, embedding, metadata);
    }
    
    @Override
    public boolean updateVector(String vectorId, String entityType, String entityId, String content, 
                              List<Double> embedding, Map<String, Object> metadata) {
        String resolvedId = upsertSingleVector(entityType, entityId, content, embedding, metadata, vectorId);
        return Objects.equals(resolvedId, vectorId);
    }
    
    @Override
    public Optional<VectorRecord> getVector(String vectorId) {
        try {
            String namespace = extractNamespace(vectorId);
            URI uri = UriComponentsBuilder.fromUri(baseUri.resolve("/vectors/fetch"))
                .queryParam("ids", vectorId)
                .queryParam("namespace", namespace)
                .build(true)
                .toUri();

            Map<String, Object> response = exchange(uri, HttpMethod.GET, null);
            Map<String, Object> vectors = (Map<String, Object>) response.get("vectors");
            if (vectors == null || !vectors.containsKey(vectorId)) {
                return Optional.empty();
            }

            Map<String, Object> vectorPayload = (Map<String, Object>) vectors.get(vectorId);
            return Optional.of(mapToVectorRecord(vectorPayload, namespace));

        } catch (Exception ex) {
            throw new AIServiceException("Failed to fetch vector from Pinecone", ex);
        }
    }
    
    @Override
    public Optional<VectorRecord> getVectorByEntity(String entityType, String entityId) {
        String vectorId = buildVectorId(entityType, entityId);
        return getVector(vectorId);
    }
    
    @Override
    public AISearchResponse search(List<Double> queryVector, AISearchRequest request) {
        if (CollectionUtils.isEmpty(queryVector)) {
            throw new AIServiceException("Query vector is required for Pinecone search");
        }

        long start = System.currentTimeMillis();

        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("namespace", namespace(request.getEntityType()));
            payload.put("vector", queryVector);
            payload.put("topK", request.getLimit() != null ? request.getLimit() : 10);
            payload.put("includeMetadata", true);
            payload.put("includeValues", false);

            if (request.getMetadata() != null && !request.getMetadata().isEmpty()) {
                payload.put("filter", request.getMetadata());
            }

            Map<String, Object> response = post("/query", payload);
            List<Map<String, Object>> matches = (List<Map<String, Object>>) response.getOrDefault("matches", List.of());
            double threshold = request.getThreshold() != null ? request.getThreshold() : 0.0;

            List<Map<String, Object>> results = new ArrayList<>();
            double maxScore = 0.0;
            for (Map<String, Object> match : matches) {
                Double score = toDouble(match.get("score"));
                if (score == null || score < threshold) {
                    continue;
                }
                maxScore = Math.max(maxScore, score);
                results.add(convertMatchToResult(match));
            }

            return AISearchResponse.builder()
                .results(results)
                .totalResults(results.size())
                .maxScore(maxScore)
                .processingTimeMs(System.currentTimeMillis() - start)
                .requestId(UUID.randomUUID().toString())
                .query(request.getQuery())
                .model(config.resolveEmbeddingDefaults().model())
                .build();

        } catch (Exception ex) {
            throw new AIServiceException("Failed to query Pinecone", ex);
        }
    }
    
    @Override
    public AISearchResponse searchByEntityType(List<Double> queryVector, String entityType, int limit, double threshold) {
        AISearchRequest request = AISearchRequest.builder()
            .entityType(entityType)
            .limit(limit)
            .threshold(threshold)
            .query("")
            .metadata(Map.of("entityType", entityType))
            .build();
        return search(queryVector, request);
    }
    
    @Override
    public boolean removeVector(String entityType, String entityId) {
        return removeVectorById(buildVectorId(entityType, entityId));
    }
    
    @Override
    public boolean removeVectorById(String vectorId) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("ids", List.of(vectorId));
            payload.put("namespace", extractNamespace(vectorId));
            post("/vectors/delete", payload);
            return true;
        } catch (Exception ex) {
            throw new AIServiceException("Failed to remove vector from Pinecone", ex);
        }
    }
    
    @Override
    public List<String> batchStoreVectors(List<VectorRecord> vectors) {
        if (vectors == null || vectors.isEmpty()) {
            return List.of();
        }

        Map<String, List<VectorRecord>> grouped = vectors.stream().collect(
            java.util.stream.Collectors.groupingBy(VectorRecord::getEntityType)
        );

        List<String> storedIds = new ArrayList<>();
        for (Map.Entry<String, List<VectorRecord>> entry : grouped.entrySet()) {
            storedIds.addAll(upsertBatch(entry.getKey(), entry.getValue()));
        }
        return storedIds;
    }
    
    @Override
    public int batchUpdateVectors(List<VectorRecord> vectors) {
        return batchStoreVectors(vectors).size();
    }
    
    @Override
    public int batchRemoveVectors(List<String> vectorIds) {
        if (vectorIds == null || vectorIds.isEmpty()) {
            return 0;
        }

        Map<String, List<String>> grouped = vectorIds.stream().collect(
            java.util.stream.Collectors.groupingBy(this::extractNamespace)
        );

        int removed = 0;
        for (Map.Entry<String, List<String>> entry : grouped.entrySet()) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("ids", entry.getValue());
            payload.put("namespace", entry.getKey());
            post("/vectors/delete", payload);
            removed += entry.getValue().size();
        }
        return removed;
    }
    
    @Override
    public List<VectorRecord> getVectorsByEntityType(String entityType) {
        // Best effort: query with zero vector to retrieve metadata-rich matches.
        List<Double> zeroVector = Collections.nCopies(config.getPinecone().getDimensions(), 0.0);
        AISearchRequest request = AISearchRequest.builder()
            .entityType(entityType)
            .limit(100)
            .threshold(-1.0)
            .metadata(Map.of("entityType", entityType))
            .query("entityType:" + entityType)
            .build();

        AISearchResponse response = search(zeroVector, request);
        List<VectorRecord> records = new ArrayList<>();
        for (Map<String, Object> result : response.getResults()) {
            records.add(mapResultToVectorRecord(result));
        }
        return records;
    }
    
    @Override
    public long getVectorCountByEntityType(String entityType) {
        Map<String, Object> stats = describeIndexStats();
        Map<String, Object> namespaces = (Map<String, Object>) stats.getOrDefault("namespaces", Map.of());
        Map<String, Object> namespaceStats = (Map<String, Object>) namespaces.getOrDefault(namespace(entityType), Map.of());
        Object vectorCount = namespaceStats.get("vectorCount");
        return vectorCount instanceof Number ? ((Number) vectorCount).longValue() : 0L;
    }
    
    @Override
    public boolean vectorExists(String entityType, String entityId) {
        return getVectorByEntity(entityType, entityId).isPresent();
    }
    
    @Override
    public long clearVectors() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("deleteAll", true);
        post("/vectors/delete", payload);
        return -1; // Pinecone does not return count
    }
    
    @Override
    public long clearVectorsByEntityType(String entityType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("namespace", namespace(entityType));
        payload.put("deleteAll", true);
        post("/vectors/delete", payload);
        return -1;
    }
    
    @Override
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = describeIndexStats();
        stats.put("type", "pinecone");
        return stats;
    }

    private Map<String, Object> describeIndexStats() {
        return post("/describe_index_stats", Collections.emptyMap());
    }

    private String upsertSingleVector(String entityType,
                                      String entityId,
                                      String content,
                                      List<Double> embedding,
                                      Map<String, Object> metadata) {
        return upsertSingleVector(entityType, entityId, content, embedding, metadata, null);
    }

    private String upsertSingleVector(String entityType,
                                      String entityId,
                                      String content,
                                      List<Double> embedding,
                                      Map<String, Object> metadata,
                                      String existingVectorId) {
        try {
            String vectorId = existingVectorId != null ? existingVectorId : buildVectorId(entityType, entityId);
            Map<String, Object> vector = buildVectorPayload(vectorId, entityType, entityId, content, embedding, metadata);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("namespace", namespace(entityType));
            payload.put("vectors", List.of(vector));

            post("/vectors/upsert", payload);
            return vectorId;
        } catch (Exception ex) {
            throw new AIServiceException("Failed to upsert vector into Pinecone", ex);
        }
    }

    private List<String> upsertBatch(String entityType, List<VectorRecord> records) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> vectors = new ArrayList<>();
        List<String> ids = new ArrayList<>();

        for (VectorRecord record : records) {
            String vectorId = Optional.ofNullable(record.getVectorId())
                .orElseGet(() -> buildVectorId(entityType, record.getEntityId()));
            ids.add(vectorId);
            vectors.add(buildVectorPayload(vectorId, entityType, record.getEntityId(), record.getContent(), record.getEmbedding(), record.getMetadata()));
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("namespace", namespace(entityType));
        payload.put("vectors", vectors);

        post("/vectors/upsert", payload);
        return ids;
    }

    private Map<String, Object> buildVectorPayload(String vectorId,
                                                   String entityType,
                                                   String entityId,
                                                   String content,
                                                   List<Double> embedding,
                                                   Map<String, Object> metadata) {
        Map<String, Object> meta = new LinkedHashMap<>();
        if (metadata != null) {
            meta.putAll(metadata);
        }
        meta.put("entityType", entityType);
        meta.put("entityId", entityId);
        if (StringUtils.hasText(content)) {
            meta.put("content", content);
        }

        Map<String, Object> vector = new LinkedHashMap<>();
        vector.put("id", vectorId);
        vector.put("metadata", meta);
        if (embedding != null) {
            vector.put("values", embedding);
        }
        return vector;
    }

    private Map<String, Object> convertMatchToResult(Map<String, Object> match) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.putAll(match);
        Map<String, Object> metadata = (Map<String, Object>) match.get("metadata");
        if (metadata != null) {
            result.put("metadata", metadata);
        }
        return result;
    }

    private VectorRecord mapToVectorRecord(Map<String, Object> vectorPayload, String namespace) {
        String vectorId = (String) vectorPayload.get("id");
        Map<String, Object> metadata = (Map<String, Object>) vectorPayload.getOrDefault("metadata", Map.of());
        List<Double> values = convertValues(vectorPayload.get("values"));
        String entityType = (String) metadata.getOrDefault("entityType", namespace);
        String entityId = (String) metadata.get("entityId");
        String content = (String) metadata.get("content");

        return VectorRecord.builder()
            .vectorId(vectorId)
            .entityType(entityType)
            .entityId(entityId)
            .content(content)
            .embedding(values)
            .metadata(metadata)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .vectorMetadata(vectorPayload)
            .build();
    }

    private VectorRecord mapResultToVectorRecord(Map<String, Object> result) {
        Map<String, Object> metadata = (Map<String, Object>) result.getOrDefault("metadata", Map.of());
        String vectorId = (String) result.get("id");
        Double score = toDouble(result.get("score"));

        return VectorRecord.builder()
            .vectorId(vectorId)
            .entityType((String) metadata.getOrDefault("entityType", DEFAULT_NAMESPACE))
            .entityId((String) metadata.get("entityId"))
            .content((String) metadata.get("content"))
            .metadata(metadata)
            .similarityScore(score)
            .vectorMetadata(result)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }

    private List<Double> convertValues(Object values) {
        if (values == null) {
            return List.of();
        }
        if (values instanceof List<?>) {
            List<?> raw = (List<?>) values;
            List<Double> converted = new ArrayList<>(raw.size());
            for (Object o : raw) {
                Double v = toDouble(o);
                if (v != null) {
                    converted.add(v);
                }
            }
            return converted;
        }
        return List.of();
    }

    private Map<String, Object> post(String path, Object payload) {
        try {
            String body = payload == null ? "{}" : objectMapper.writeValueAsString(payload);
            HttpEntity<String> entity = new HttpEntity<>(body, defaultHeaders(MediaType.APPLICATION_JSON));
            ResponseEntity<String> response = restTemplate.exchange(baseUri.resolve(path), HttpMethod.POST, entity, String.class);
            return parseResponse(response);
        } catch (Exception ex) {
            throw new AIServiceException("Failed to call Pinecone endpoint: " + path, ex);
        }
    }

    private Map<String, Object> exchange(URI uri, HttpMethod method, Object payload) throws Exception {
        HttpEntity<String> entity;
        if (payload != null) {
            entity = new HttpEntity<>(objectMapper.writeValueAsString(payload), defaultHeaders(MediaType.APPLICATION_JSON));
        } else {
            entity = new HttpEntity<>(defaultHeaders(MediaType.APPLICATION_JSON));
        }

        ResponseEntity<String> response = restTemplate.exchange(uri, method, entity, String.class);
        return parseResponse(response);
    }

    private Map<String, Object> parseResponse(ResponseEntity<String> response) throws Exception {
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new AIServiceException("Pinecone API returned status " + response.getStatusCode());
        }
        String body = response.getBody();
        if (!StringUtils.hasText(body)) {
            return Map.of();
        }
        return objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {});
    }

    private HttpHeaders defaultHeaders(MediaType mediaType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set(API_KEY_HEADER, config.getPinecone().getApiKey());
        return headers;
    }

    private HttpHeaders defaultHeaders() {
        return defaultHeaders(MediaType.APPLICATION_JSON);
    }

    private String resolveBaseUrl() {
        if (StringUtils.hasText(config.getPinecone().getApiHost())) {
            String host = config.getPinecone().getApiHost().trim();
            if (!host.startsWith("http")) {
                host = "https://" + host;
            }
            return host;
        }

        if (StringUtils.hasText(config.getPinecone().getProjectId())) {
            return String.format("https://%s-%s.svc.%s.pinecone.io", config.getPinecone().getIndexName(),
                config.getPinecone().getProjectId(), config.getPinecone().getEnvironment());
        }
        return String.format("https://%s.svc.%s.pinecone.io", config.getPinecone().getIndexName(), config.getPinecone().getEnvironment());
    }

    private String buildVectorId(String entityType, String entityId) {
        return String.format("%s::%s", namespace(entityType), entityId);
    }

    private String namespace(String entityType) {
        return StringUtils.hasText(entityType) ? entityType : DEFAULT_NAMESPACE;
    }

    private String extractNamespace(String vectorId) {
        if (!StringUtils.hasText(vectorId) || !vectorId.contains("::")) {
            return DEFAULT_NAMESPACE;
        }
        return vectorId.substring(0, vectorId.indexOf("::"));
    }

    private Double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String str) {
            try {
                return Double.parseDouble(str);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}