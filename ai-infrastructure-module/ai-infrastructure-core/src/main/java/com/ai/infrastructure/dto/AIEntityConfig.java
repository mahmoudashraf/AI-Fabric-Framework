package com.ai.infrastructure.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * AI Entity Configuration
 * 
 * Represents the configuration for an AI-capable entity.
 * Contains all AI processing settings and field configurations.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Data
@Builder
public class AIEntityConfig {
    
    private String entityType;
    private List<String> features;
    private boolean autoProcess;
    private boolean enableSearch;
    private boolean enableRecommendations;
    private boolean autoEmbedding;
    private boolean indexable;
    
    @Builder.Default
    private Map<String, SearchableFieldConfig> searchableFields = Collections.emptyMap();
    private List<AIEmbeddableField> embeddableFields;
    private List<AIMetadataField> metadataFields;
    private Map<String, AICrudOperation> crudOperations;

    public Map<String, SearchableFieldConfig> getSearchableFields() {
        if (searchableFields == null) {
            return Collections.emptyMap();
        }
        return searchableFields;
    }

    /**
     * Convenience accessor for a single searchable field config by name.
     */
    public SearchableFieldConfig getSearchableField(String fieldName) {
        if (fieldName == null || searchableFields == null) {
            return null;
        }
        return searchableFields.get(fieldName);
    }

    /**
     * Safe copy of searchable field configs as a list.
     */
    public List<SearchableFieldConfig> getSearchableFieldList() {
        if (searchableFields == null || searchableFields.isEmpty()) {
            return List.of();
        }
        return List.copyOf(searchableFields.values());
    }

    public void setSearchableFields(Map<String, SearchableFieldConfig> fields) {
        if (fields == null) {
            this.searchableFields = new LinkedHashMap<>();
            return;
        }
        this.searchableFields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }

    /**
     * Backward compatibility: accept legacy AISearchableField list.
     */
    public void setSearchableFields(List<AISearchableField> fields) {
        if (fields == null) {
            this.searchableFields = new LinkedHashMap<>();
            return;
        }
        Map<String, SearchableFieldConfig> converted = fields.stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toMap(
                AISearchableField::getName,
                this::toSearchableFieldConfig,
                (first, second) -> first,
                LinkedHashMap::new
            ));
        this.searchableFields = Collections.unmodifiableMap(converted);
    }

    private SearchableFieldConfig toSearchableFieldConfig(AISearchableField field) {
        return SearchableFieldConfig.builder()
            .name(field.getName())
            .includeInRag(field.isIncludeInRAG())
            .enableSemanticSearch(field.isEnableSemanticSearch())
            .weight(field.getWeight())
            .build();
    }
}
