package com.ai.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Versioning configuration for multi-step intent extraction prompts.
 */
@Data
@Validated
@ConfigurationProperties(prefix = "ai.prompts.intent-extraction.multi-step")
public class MultiStepIntentExtractionPromptTemplatesProperties {

    /**
     * Template version folder under {@code prompts/intent-extraction/multi-step/}.
     */
    @NotBlank
    private String version = "v1";
}

