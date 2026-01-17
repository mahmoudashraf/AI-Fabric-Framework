package com.ai.infrastructure.intent;

import com.ai.infrastructure.config.AIEntityConfigurationLoader;
import com.ai.infrastructure.rag.VectorDatabaseService;
import lombok.extern.slf4j.Slf4j;
import com.ai.infrastructure.config.condition.VectorDbConfiguredCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Provides lightweight statistics about the indexed knowledge base to enrich prompts.
 */
@Slf4j
@Service
@Conditional(VectorDbConfiguredCondition.class)
public class KnowledgeBaseOverviewService {

    private final VectorDatabaseService vectorDatabaseService;
    private final AIEntityConfigurationLoader configurationLoader;

    public KnowledgeBaseOverviewService(VectorDatabaseService vectorDatabaseService,
                                        AIEntityConfigurationLoader configurationLoader) {
        this.vectorDatabaseService = vectorDatabaseService;
        this.configurationLoader = configurationLoader;
    }

    public KnowledgeBaseOverview getOverview() {
        Map<String, Object> stats = safeStatistics();
        Map<String, Long> documentsByType = deriveDocumentsByType(stats);
        if (documentsByType.isEmpty()) {
            documentsByType = deriveDocumentsByTypeFromProvider();
        }

        long totalDocuments = deriveTotalDocuments(stats, documentsByType);
        LocalDateTime lastUpdated = deriveLastUpdated(documentsByType.keySet().stream().toList());

        KnowledgeBaseOverview overview = KnowledgeBaseOverview.builder()
            .totalIndexedDocuments(totalDocuments)
            .documentsByType(documentsByType)
            .entityTypes(documentsByType.keySet().stream().toList())
            .lastIndexUpdateTime(lastUpdated)
            .rawStatistics(Collections.unmodifiableMap(new LinkedHashMap<>(stats)))
            .build();

        log.debug("Knowledge base overview computed: {}", overview);
        return overview;
    }

    private Map<String, Object> safeStatistics() {
        try {
            Map<String, Object> stats = vectorDatabaseService.getStatistics();
            return stats == null ? Collections.emptyMap() : stats;
        } catch (Exception ex) {
            log.warn("Unable to fetch vector database statistics, continuing with fallbacks", ex);
            return Collections.emptyMap();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Long> deriveDocumentsByType(Map<String, Object> stats) {
        Object entityTypeCounts = stats.get("entityTypeCounts");
        if (entityTypeCounts instanceof Map<?, ?> rawMap) {
            return rawMap.entrySet().stream()
                .filter(entry -> entry.getKey() != null)
                .collect(Collectors.toMap(
                    entry -> entry.getKey().toString(),
                    entry -> entry.getValue() instanceof Number number ? number.longValue() : 0L,
                    Long::sum,
                    LinkedHashMap::new
                ));
        }
        return Collections.emptyMap();
    }

    private Map<String, Long> deriveDocumentsByTypeFromProvider() {
        if (configurationLoader == null || configurationLoader.getSupportedEntityTypes().isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Long> counts = new LinkedHashMap<>();
        for (String entityType : configurationLoader.getSupportedEntityTypes()) {
            if (entityType == null || entityType.isBlank()) {
                continue;
            }
            try {
                long count = vectorDatabaseService.getVectorCountByEntityType(entityType);
                if (count > 0) {
                    counts.put(entityType.toLowerCase(Locale.ROOT), count);
                }
            } catch (Exception ex) {
                log.debug("Failed to compute vector count for entity type {}", entityType, ex);
            }
        }
        return counts;
    }

    private long deriveTotalDocuments(Map<String, Object> stats, Map<String, Long> documentsByType) {
        List<String> keys = List.of("totalVectors", "totalIndexed", "totalIndexedDocuments");
        for (String key : keys) {
            Optional<Long> value = extractLong(stats.get(key));
            if (value.isPresent()) {
                return value.get();
            }
        }

        if (!documentsByType.isEmpty()) {
            return documentsByType.values().stream().mapToLong(Long::longValue).sum();
        }

        return 0L;
    }

    private LocalDateTime deriveLastUpdated(List<String> entityTypes) {
        if (entityTypes == null || entityTypes.isEmpty()) {
            return null;
        }

        LocalDateTime latest = null;
        for (String entityType : entityTypes) {
            if (entityType == null || entityType.isBlank()) {
                continue;
            }
            try {
                List<com.ai.infrastructure.dto.VectorRecord> vectors = vectorDatabaseService.getVectorsByEntityType(entityType);
                if (vectors == null || vectors.isEmpty()) {
                    continue;
                }
                for (com.ai.infrastructure.dto.VectorRecord record : vectors) {
                    if (record == null) {
                        continue;
                    }
                    LocalDateTime timestamp = record.getUpdatedAt() != null ? record.getUpdatedAt() : record.getCreatedAt();
                    if (timestamp == null) {
                        continue;
                    }
                    if (latest == null || timestamp.isAfter(latest)) {
                        latest = timestamp;
                    }
                }
            } catch (Exception ex) {
                log.debug("Failed to compute lastUpdated for entity type {}", entityType, ex);
            }
        }
        return latest;
    }

    private Optional<Long> extractLong(Object value) {
        if (value instanceof Number number) {
            return Optional.of(number.longValue());
        }
        if (value instanceof String string && !string.isBlank()) {
            try {
                return Optional.of(Long.parseLong(string.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return Optional.empty();
    }
}
