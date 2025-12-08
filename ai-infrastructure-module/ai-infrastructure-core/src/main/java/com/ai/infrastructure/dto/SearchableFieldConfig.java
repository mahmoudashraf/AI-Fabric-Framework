package com.ai.infrastructure.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Searchable field configuration with include-in-rag control.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchableFieldConfig {

    private String name;
    @Builder.Default
    private boolean includeInRag = true;
    @Builder.Default
    private boolean enableSemanticSearch = true;
    @Builder.Default
    private double weight = 1.0;
}
