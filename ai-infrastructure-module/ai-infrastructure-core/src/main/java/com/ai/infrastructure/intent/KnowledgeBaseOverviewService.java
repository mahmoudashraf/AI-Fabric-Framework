package com.ai.infrastructure.intent;

import com.ai.infrastructure.entity.AISearchableEntity;
import com.ai.infrastructure.rag.VectorDatabaseService;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import lombok.extern.slf4j.Slf4j;
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
public class KnowledgeBaseOverviewService {

    private final VectorDatabaseService vectorDatabaseService;
    private final AISearchableEntityRepository searchableEntityRepository;

    public KnowledgeBaseOverviewService(VectorDatabaseService vectorDatabaseService,
                                        AISearchableEntityRepository searchableEntityRepository) {
        this.vectorDatabaseService = vectorDatabaseService;
        this.searchableEntityRepository = searchableEntityRepository;
    }

    public KnowledgeBaseOverview getOverview() {
        Map<String, Object> stats = safeStatistics();
        Map<String, Long> documentsByType = deriveDocumentsByType(stats);
        if (documentsByType.isEmpty()) {
            documentsByType = fallbackDocumentsByType();
        }

        long totalDocuments = deriveTotalDocuments(stats, documentsByType);
        LocalDateTime lastUpdated = searchableEntityRepository
            .findFirstByVectorUpdatedAtIsNotNullOrderByVectorUpdatedAtDesc()
            .map(AISearchableEntity::getVectorUpdatedAt)
            .orElse(null);

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

    private Map<String, Long> fallbackDocumentsByType() {
        List<AISearchableEntity> entities = searchableEntityRepository.findByVectorIdIsNotNull();
        if (entities.isEmpty()) {
            return Collections.emptyMap();
        }
        return entities.stream()
            .filter(entity -> entity.getEntityType() != null)
            .collect(Collectors.toMap(
                entity -> entity.getEntityType().toLowerCase(Locale.ROOT),
                entity -> 1L,
                Long::sum,
                LinkedHashMap::new
            ));
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

        return searchableEntityRepository.countByVectorIdIsNotNull();
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
