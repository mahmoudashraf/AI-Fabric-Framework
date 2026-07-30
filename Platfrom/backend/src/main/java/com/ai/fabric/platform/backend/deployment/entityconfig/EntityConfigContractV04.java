package com.ai.fabric.platform.backend.deployment.entityconfig;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record EntityConfigContractV04(
    int vectorDimensions,
    Map<String, EntityProjectionConfig> entities
) {

    public EntityConfigContractV04 {
        entities = Collections.unmodifiableMap(new LinkedHashMap<>(entities));
    }

    public record EntityProjectionConfig(
        IndexingConfig indexing,
        AnalysisConfig analysis,
        List<SearchableFieldConfig> searchableFields,
        List<MetadataFieldConfig> metadataFields,
        MarketplaceProvenance marketplaceProvenance
    ) {

        public EntityProjectionConfig {
            searchableFields = List.copyOf(searchableFields);
            metadataFields = List.copyOf(metadataFields);
        }
    }

    public record IndexingConfig(
        boolean enabled,
        int maxCharacters
    ) {
    }

    public record AnalysisConfig(
        boolean enabled,
        List<AnalysisOperation> after
    ) {

        public AnalysisConfig {
            after = List.copyOf(after);
        }
    }

    public record SearchableFieldConfig(
        String name,
        List<SearchDestination> destinations,
        SearchPreprocessing preprocessing,
        int maxLength,
        int priority,
        boolean required
    ) {

        public SearchableFieldConfig {
            destinations = List.copyOf(destinations);
        }
    }

    public record MetadataFieldConfig(
        String name,
        MetadataDataType dataType,
        String format,
        String description,
        List<MetadataDestination> destinations,
        int priority,
        boolean required,
        boolean sanitizePii
    ) {

        public MetadataFieldConfig {
            destinations = List.copyOf(destinations);
        }
    }

    public record MarketplaceProvenance(
        boolean managed,
        String pluginId,
        String installId,
        String pluginVersion
    ) {
    }

    public enum AnalysisOperation {
        CREATE,
        UPDATE,
        DELETE
    }

    public enum SearchDestination {
        SEMANTIC_SEARCH,
        RAG_CONTEXT
    }

    public enum SearchPreprocessing {
        NONE,
        NORMALIZE,
        CLEAN,
        SANITIZE
    }

    public enum MetadataDataType {
        AUTO,
        STRING,
        NUMBER,
        BOOLEAN,
        DATE,
        ENUM,
        ID,
        JSON
    }

    public enum MetadataDestination {
        VECTOR_METADATA,
        LLM_CONTEXT,
        API_RESPONSE
    }
}
