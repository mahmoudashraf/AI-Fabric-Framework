package com.ai.infrastructure.api;

import com.ai.infrastructure.config.AIServiceConfig;
import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.dto.AIGenerationRequest;
import com.ai.infrastructure.dto.AIGenerationResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Default implementation of {@link AIAutoGeneratorService} that keeps all generated metadata
 * in-memory and relies on {@link AICoreService} for optional descriptive content.
 */
@Slf4j
@RequiredArgsConstructor
public class DefaultAIAutoGeneratorService implements AIAutoGeneratorService {

    private static final List<String> BASE_OPERATIONS = List.of("list", "get", "create", "update", "delete");

    private final AICoreService aiCoreService;
    private final AIServiceConfig serviceConfig;

    private final ConcurrentMap<String, APIEndpointDefinition> endpoints = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Set<String>> endpointsByEntity = new ConcurrentHashMap<>();

    private final AtomicLong generatedCount = new AtomicLong();
    private final AtomicLong refreshedCount = new AtomicLong();
    private final AtomicLong registeredCount = new AtomicLong();
    private final AtomicLong updatedCount = new AtomicLong();

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Override
    public List<APIEndpointDefinition> generateEndpoints(String entityType) {
        String normalized = normalizeEntityType(entityType);
        String basePath = defaultBasePath(normalized);

        List<APIEndpointDefinition> generated = new ArrayList<>();

        generated.add(createEndpoint(normalized, "list", "GET", basePath, false));
        generated.add(createEndpoint(normalized, "get", "GET", basePath + "/{id}", false));
        generated.add(createEndpoint(normalized, "create", "POST", basePath, false));
        generated.add(createEndpoint(normalized, "update", "PUT", basePath + "/{id}", false));
        generated.add(createEndpoint(normalized, "delete", "DELETE", basePath + "/{id}", false));

        generated.forEach(this::registerEndpointInternal);
        generatedCount.addAndGet(generated.size());

        return Collections.unmodifiableList(generated);
    }

    @Override
    public APISpecification generateSpecification(String entityType) {
        String normalized = normalizeEntityType(entityType);
        List<APIEndpointDefinition> entityEndpoints = getEndpointsByEntityType(normalized);
        if (entityEndpoints.isEmpty()) {
            entityEndpoints = generateEndpoints(normalized);
        }

        return buildSpecification(normalized, entityEndpoints);
    }

    @Override
    public void registerEndpoint(APIEndpointDefinition endpoint) {
        registerEndpointInternal(sanitizeEndpoint(endpoint));
        registeredCount.incrementAndGet();
    }

    @Override
    public void unregisterEndpoint(String endpointId) {
        if (endpointId == null) {
            return;
        }
        APIEndpointDefinition removed = endpoints.remove(endpointId);
        if (removed != null) {
            String entityType = resolveEntityType(removed);
            endpointsByEntity.computeIfPresent(entityType, (key, ids) -> {
                ids.remove(endpointId);
                return ids.isEmpty() ? null : ids;
            });
        }
    }

    @Override
    public List<APIEndpointDefinition> getRegisteredEndpoints() {
        List<APIEndpointDefinition> all = new ArrayList<>(endpoints.values());
        all.sort(Comparator.comparing(APIEndpointDefinition::getId));
        return Collections.unmodifiableList(all);
    }

    @Override
    public List<APIEndpointDefinition> getEndpointsByEntityType(String entityType) {
        String normalized = normalizeEntityType(entityType);
        Set<String> ids = endpointsByEntity.getOrDefault(normalized, Set.of());
        List<APIEndpointDefinition> result = new ArrayList<>(ids.size());
        for (String id : ids) {
            APIEndpointDefinition definition = endpoints.get(id);
            if (definition != null) {
                result.add(definition);
            }
        }
        result.sort(Comparator.comparing(APIEndpointDefinition::getId));
        return Collections.unmodifiableList(result);
    }

    @Override
    public APIEndpointDefinition getEndpoint(String endpointId) {
        return endpoints.get(endpointId);
    }

    @Override
    public boolean isEndpointRegistered(String endpointId) {
        return endpoints.containsKey(endpointId);
    }

    @Override
    public void updateEndpoint(APIEndpointDefinition endpoint) {
        if (endpoint == null || endpoint.getId() == null) {
            return;
        }
        APIEndpointDefinition sanitized = sanitizeEndpoint(endpoint);
        APIEndpointDefinition existing = endpoints.put(sanitized.getId(), sanitized);
        if (existing != null) {
            String oldEntity = resolveEntityType(existing);
            endpointsByEntity.computeIfPresent(oldEntity, (key, ids) -> {
                ids.remove(existing.getId());
                return ids.isEmpty() ? null : ids;
            });
        }
        registerEndpointByEntity(sanitized);
        updatedCount.incrementAndGet();
    }

    @Override
    public void enableEndpoint(String endpointId) {
        toggleEndpoint(endpointId, true);
    }

    @Override
    public void disableEndpoint(String endpointId) {
        toggleEndpoint(endpointId, false);
    }

    @Override
    public Map<String, Object> getEndpointStatistics() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalEndpoints", endpoints.size());
        stats.put("entityTypes", endpointsByEntity.keySet());
        Map<String, Integer> counts = new LinkedHashMap<>();
        endpointsByEntity.forEach((entity, ids) -> counts.put(entity, ids.size()));
        stats.put("countsByEntity", counts);
        return stats;
    }

    @Override
    public boolean validateEndpoint(APIEndpointDefinition endpoint) {
        if (endpoint == null) {
            return false;
        }
        return isNotBlank(endpoint.getId())
            && isNotBlank(endpoint.getPath())
            && isNotBlank(endpoint.getMethod());
    }

    @Override
    public String generateOpenAPISpecification() {
        return toJson(generateCompleteAPISpecification());
    }

    @Override
    public String generateOpenAPISpecification(String entityType) {
        return toJson(generateSpecification(entityType));
    }

    @Override
    public String generateAPIDocumentation() {
        StringBuilder builder = new StringBuilder();
        builder.append("<html><body><h1>API Documentation</h1>");
        endpointsByEntity.keySet().stream()
            .sorted()
            .forEach(entity -> builder.append(generateDocumentationForEntity(entity)));
        builder.append("</body></html>");
        return builder.toString();
    }

    @Override
    public String generateAPIDocumentation(String entityType) {
        return "<html><body>" + generateDocumentationForEntity(normalizeEntityType(entityType)) + "</body></html>";
    }

    @Override
    public void refreshEndpoints() {
        endpoints.clear();
        endpointsByEntity.clear();
        refreshedCount.incrementAndGet();
    }

    @Override
    public void refreshEndpoints(String entityType) {
        String normalized = normalizeEntityType(entityType);
        getEndpointsByEntityType(normalized).forEach(endpoint -> endpoints.remove(endpoint.getId()));
        endpointsByEntity.remove(normalized);
        refreshedCount.incrementAndGet();
        generateEndpoints(normalized);
    }

    @Override
    public void clearEndpoints() {
        endpoints.clear();
        endpointsByEntity.clear();
    }

    @Override
    public Map<String, Object> getHealthStatus() {
        return Map.of(
            "status", "UP",
            "totalEndpoints", endpoints.size(),
            "entityTypes", endpointsByEntity.keySet()
        );
    }

    @Override
    public Map<String, Object> getMetrics() {
        return Map.of(
            "totalEndpoints", endpoints.size(),
            "entityCount", endpointsByEntity.size(),
            "generatedCount", generatedCount.get(),
            "refreshedCount", refreshedCount.get(),
            "registeredCount", registeredCount.get(),
            "updatedCount", updatedCount.get()
        );
    }

    @Override
    public APIEndpointDefinition generateAPIEndpoint(String entityType, Map<String, String> parameters) {
        String normalized = normalizeEntityType(entityType);
        String operation = parameters.getOrDefault("operation", "custom");
        String id = parameters.getOrDefault("id", normalized + ":" + operation.toLowerCase(Locale.ROOT));
        String method = parameters.getOrDefault("method", "POST");
        String path = parameters.getOrDefault("path", defaultBasePath(normalized));

        APIEndpointDefinition endpoint = APIEndpointDefinition.builder()
            .id(id)
            .method(method)
            .path(path)
            .operationId(parameters.getOrDefault("operationId", operationId(normalized, operation)))
            .summary(parameters.getOrDefault("summary", defaultSummary(normalized, operation)))
            .description(parameters.getOrDefault("description", defaultDescription(normalized, operation)))
            .enabled(true)
            .metadata(metadataWithEntity(normalized, operation))
            .build();

        registerEndpoint(endpoint);
        return endpoint;
    }

    @Override
    public APISpecification generateCompleteAPISpecification() {
        List<APIEndpointDefinition> all = getRegisteredEndpoints();
        return buildSpecification("all", all);
    }

    @Override
    public String generateClientSDK(String language, APISpecification specification) {
        String safeLanguage = language == null ? "unknown" : language;
        String specTitle = specification != null && specification.getTitle() != null
            ? specification.getTitle()
            : "AI Infrastructure API";
        return String.format("// Auto-generated %s SDK for %s%n", safeLanguage, specTitle);
    }



    private APISpecification buildSpecification(String entityType, List<APIEndpointDefinition> endpoints) {
        LocalDateTime now = LocalDateTime.now();
        String title = String.format("%s API", entityType.replace('-', ' ').toUpperCase(Locale.ROOT));

        return APISpecification.builder()
            .title(title)
            .version("1.0.0")
            .description(generateSpecificationDescription(entityType))
            .baseUrl(defaultBasePath(entityType))
            .host("localhost")
            .schemes(List.of("https"))
            .consumes(List.of("application/json"))
            .produces(List.of("application/json"))
            .tags(List.of(entityType))
            .endpoints(endpoints)
            .generatedAt(now)
            .lastUpdated(now)
            .valid(true)
            .metadata(Map.of("entityType", entityType))
            .build();
    }

    private APIEndpointDefinition createEndpoint(String entityType, String operation, String method, String path, boolean idExplicit) {
        String operationUpper = operation.toUpperCase(Locale.ROOT);
        String endpointId = entityType + ":" + operation;
        String summary = generateText(entityType, operation, () -> defaultSummary(entityType, operation));
        String description = generateText(entityType, operation + " description", () -> defaultDescription(entityType, operation));

        APIEndpointDefinition endpoint = APIEndpointDefinition.builder()
            .id(endpointId)
            .path(path)
            .method(method)
            .operationId(operationId(entityType, operationUpper))
            .summary(summary)
            .description(description)
            .category(entityType)
            .tags(List.of(entityType))
            .priority(5)
            .enabled(true)
            .deprecated(false)
            .metadata(metadataWithEntity(entityType, operation))
            .build();

        return endpoint;
    }

    private Map<String, Object> metadataWithEntity(String entityType, String operation) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("entityType", entityType);
        metadata.put("operation", operation);
        metadata.put("generated", true);
        return metadata;
    }

    private void registerEndpointInternal(APIEndpointDefinition endpoint) {
        APIEndpointDefinition sanitized = sanitizeEndpoint(endpoint);
        endpoints.put(sanitized.getId(), sanitized);
        registerEndpointByEntity(sanitized);
    }

    private void registerEndpointByEntity(APIEndpointDefinition endpoint) {
        String entityType = resolveEntityType(endpoint);
        endpointsByEntity.computeIfAbsent(entityType, key -> new CopyOnWriteArraySet<>()).add(endpoint.getId());
    }

    private APIEndpointDefinition sanitizeEndpoint(APIEndpointDefinition endpoint) {
        if (endpoint.getMetadata() == null) {
            endpoint.setMetadata(metadataWithEntity(resolveEntityType(endpoint), "custom"));
        }
        if (endpoint.getEnabled() == null) {
            endpoint.setEnabled(true);
        }
        return endpoint;
    }

    private String resolveEntityType(APIEndpointDefinition endpoint) {
        if (endpoint.getMetadata() != null && endpoint.getMetadata().get("entityType") != null) {
            return normalizeEntityType(endpoint.getMetadata().get("entityType").toString());
        }
        if (endpoint.getCategory() != null) {
            return normalizeEntityType(endpoint.getCategory());
        }
        if (endpoint.getPath() != null) {
            String[] segments = endpoint.getPath().split("/");
            if (segments.length > 1) {
                return normalizeEntityType(segments[segments.length - 1].replace("{", "").replace("}", ""));
            }
        }
        return "generic";
    }

    private String defaultBasePath(String entityType) {
        String suffix = entityType.endsWith("s") ? entityType : entityType + "s";
        return "/api/" + suffix;
    }

    private String generateText(String entityType, String operation, TextSupplier fallback) {
        try {
            AIGenerationResponse response = aiCoreService.generateContent(AIGenerationRequest.builder()
                .entityId(entityType + "-" + operation)
                .entityType(entityType)
                .generationType("api_generation")
                .prompt(String.format("Provide a concise %s for the %s endpoint handling %s entities.", operation.contains("description") ? "description" : "summary", operation, entityType))
                .build());
            if (response != null && response.getContent() != null && !response.getContent().isBlank()) {
                return response.getContent().trim();
            }
        } catch (Exception ex) {
            log.debug("AI content generation failed, using fallback: {}", ex.getMessage());
        }
        return fallback.get();
    }

    private String defaultSummary(String entityType, String operation) {
        return String.format("%s %s", capitalize(operation), entityType.replace('-', ' '));
    }

    private String defaultDescription(String entityType, String operation) {
        return String.format("Endpoint to %s %s entities", operation, entityType.replace('-', ' '));
    }

    private String generateSpecificationDescription(String entityType) {
        return generateText(entityType, "specification", () -> String.format("Auto-generated API specification for %s entities", entityType));
    }

    private void toggleEndpoint(String endpointId, boolean enabled) {
        APIEndpointDefinition endpoint = endpoints.get(endpointId);
        if (endpoint != null) {
            endpoint.setEnabled(enabled);
        }
    }

    private String operationId(String entityType, String operation) {
        return (entityType + "_" + operation).replace('-', '_');
    }

    private String toJson(APISpecification specification) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(specification);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise OpenAPI specification", e);
        }
    }

    private String generateDocumentationForEntity(String entityType) {
        List<APIEndpointDefinition> entityEndpoints = getEndpointsByEntityType(entityType);
        StringBuilder builder = new StringBuilder();
        builder.append(String.format("<h2>%s</h2><ul>", entityType));
        for (APIEndpointDefinition endpoint : entityEndpoints) {
            builder.append("<li><strong>")
                .append(endpoint.getMethod())
                .append(" ")
                .append(endpoint.getPath())
                .append("</strong> - ")
                .append(Optional.ofNullable(endpoint.getSummary()).orElse("No summary"))
                .append("</li>");
        }
        builder.append("</ul>");
        return builder.toString();
    }

    private String normalizeEntityType(String entityType) {
        if (entityType == null || entityType.isBlank()) {
            return "entity";
        }
        return entityType.trim().toLowerCase(Locale.ROOT).replace(' ', '-');
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }

    @FunctionalInterface
    private interface TextSupplier {
        String get();
    }
}
