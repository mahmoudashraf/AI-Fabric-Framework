package com.ai.fabric.platform.backend.deployment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/** Shared bootstrap settings; templates and later explicit edits remain authoritative. */
public final class ManagedBrainQualityDefaults {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final JsonNode POLICY = load();

    private ManagedBrainQualityDefaults() { }

    public static String openAiModel() {
        return POLICY.path("openaiModel").asText();
    }

    public static int openAiMaxTokens() {
        return POLICY.path("openaiMaxTokens").asInt();
    }

    public static Map<String, String> behaviourSelections() {
        Map<String, String> selections = new LinkedHashMap<>();
        POLICY.path("behaviourSelections").fields().forEachRemaining(entry ->
            selections.put(entry.getKey(), entry.getValue().asText()));
        return Map.copyOf(selections);
    }

    public static ObjectNode promptConfig(JsonNode curatedPreset) {
        ObjectNode result = DeploymentAiBehaviourProfiles.select(curatedPreset, MAPPER.createObjectNode());
        POLICY.path("promptBudgets").fields().forEachRemaining(entry -> {
            int configured = result.path(entry.getKey()).asInt(0);
            result.put(entry.getKey(), Math.max(configured, entry.getValue().asInt()));
        });
        String instructions = result.path("intentExtractionPrompt").asText("");
        result.put("intentExtractionPrompt", instructions + "\n\n" + POLICY.path("intentGuidance").asText());
        return result;
    }

    public static void seedProviderConfig(ObjectNode provider) {
        if ("openai".equals(provider.path("llmProvider").asText()) && !provider.has("openaiMaxTokens")) {
            provider.put("openaiMaxTokens", openAiMaxTokens());
        }
        ObjectNode modes = child(provider, "orchestrationModeOverrides");
        ObjectNode executor = child(modes, "executor");
        if (!executor.has("knowledgeBaseOverviewEnabled")) {
            executor.set("knowledgeBaseOverviewEnabled", POLICY.path("knowledgeBaseOverviewEnabled").deepCopy());
        }
        ObjectNode reads = child(executor, "readActionResolution");
        POLICY.path("readActionResolution").fields().forEachRemaining(entry -> {
            if (!reads.has(entry.getKey())) reads.set(entry.getKey(), entry.getValue().deepCopy());
        });
    }

    private static ObjectNode child(ObjectNode parent, String key) {
        JsonNode existing = parent.get(key);
        if (existing == null) return parent.putObject(key);
        if (existing instanceof ObjectNode object) return object;
        throw new IllegalArgumentException("Bootstrap configuration '" + key + "' must be an object.");
    }

    private static JsonNode load() {
        try (InputStream stream = ManagedBrainQualityDefaults.class.getResourceAsStream("/bootstrap/brain-quality-defaults.json")) {
            if (stream == null) throw new IllegalStateException("Managed Brain bootstrap defaults are missing.");
            return MAPPER.readTree(stream);
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot load managed Brain bootstrap defaults.", failure);
        }
    }
}
