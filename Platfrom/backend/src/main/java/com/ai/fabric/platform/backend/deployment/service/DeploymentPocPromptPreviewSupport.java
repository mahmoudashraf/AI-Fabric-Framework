package com.ai.fabric.platform.backend.deployment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

final class DeploymentPocPromptPreviewSupport {

    private static final int MAX_PROMPT_LENGTH = 12_000;

    static final List<String> ALLOWED_KEYS = List.of(
        "systemPrompt",
        "intentExtractionPrompt",
        "actionSelectionPrompt",
        "clarificationPrompt",
        "answerGenerationPrompt",
        "retrievalPrompt",
        "assistantUiPrompt"
    );

    private DeploymentPocPromptPreviewSupport() {
    }

    static ObjectNode sanitizePromptPreview(ObjectMapper objectMapper, JsonNode promptPreviewNode) {
        if (promptPreviewNode == null || promptPreviewNode.isNull() || !promptPreviewNode.isObject()) {
            return null;
        }
        ObjectNode sanitized = objectMapper.createObjectNode();
        for (String key : ALLOWED_KEYS) {
            JsonNode candidate = promptPreviewNode.path(key);
            if (!candidate.isTextual()) {
                continue;
            }
            String text = candidate.asText();
            if (!StringUtils.hasText(text)) {
                continue;
            }
            String trimmed = text.trim();
            if (trimmed.length() > MAX_PROMPT_LENGTH) {
                throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Prompt preview value for " + key + " exceeds " + MAX_PROMPT_LENGTH + " characters."
                );
            }
            sanitized.put(key, trimmed);
        }
        return sanitized.isEmpty() ? null : sanitized;
    }

    static List<String> promptKeys(ObjectNode promptPreview) {
        if (promptPreview == null || promptPreview.isEmpty()) {
            return List.of();
        }
        return ALLOWED_KEYS.stream()
            .filter(promptPreview::has)
            .toList();
    }
}
