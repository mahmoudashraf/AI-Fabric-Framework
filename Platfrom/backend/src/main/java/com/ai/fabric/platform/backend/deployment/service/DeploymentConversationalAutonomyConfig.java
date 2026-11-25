package com.ai.fabric.platform.backend.deployment.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Published execution policy. It grants no action, source, authoring or infrastructure permission. */
public final class DeploymentConversationalAutonomyConfig {
    public static final String FIELD = "conversationalAutonomy";
    public static final String CAPABILITY = "conversational-autonomy-v1";
    public static final String KNOWLEDGE_ACTION = "ai_search_knowledge";
    private static final Map<String, int[]> LIMITS = Map.of(
        "maxElapsedSeconds", new int[]{5, 300, 120}, "segmentSeconds", new int[]{5, 120, 30},
        "maxActionsPerSegment", new int[]{1, 64, 8}, "maxWritesPerSegment", new int[]{0, 64, 3},
        "maxModelCallsPerSegment", new int[]{1, 64, 12}, "maxCorrectionsPerSegment", new int[]{0, 8, 3},
        "maxConsecutiveNoProgress", new int[]{1, 100, 8}, "maxConsecutivePlannerFailures", new int[]{1, 20, 4});
    private DeploymentConversationalAutonomyConfig() { }
    public static boolean enabled(JsonNode provider) {
        return provider != null && provider.path(FIELD).path("enabled").asBoolean(false);
    }
    public static void validate(JsonNode provider) {
        if (provider == null || !provider.hasNonNull(FIELD)) return;
        var policy = provider.path(FIELD);
        if (!policy.isObject()) throw new IllegalArgumentException("Conversation autonomy must be a configuration object.");
        for (String key : new String[]{"enabled", "defaultEnabled", "replanNonAuthorityReadFailures"})
            if (policy.hasNonNull(key) && !policy.path(key).isBoolean()) throw new IllegalArgumentException(key + " must be true or false.");
        for (var entry : LIMITS.entrySet()) {
            var value = policy.path(entry.getKey()); var bounds = entry.getValue();
            if (!value.isMissingNode() && !value.isNull() && (!value.isIntegralNumber() || !value.canConvertToInt()
                || value.intValue() < bounds[0] || value.intValue() > bounds[1]))
                throw new IllegalArgumentException(entry.getKey() + " must be between " + bounds[0] + " and " + bounds[1] + ".");
        }
        if (policy.path("maxWritesPerSegment").asInt(3) > policy.path("maxActionsPerSegment").asInt(8))
            throw new IllegalArgumentException("The write limit cannot exceed the action limit.");
        if (policy.hasNonNull("instructions") && (!policy.path("instructions").isTextual()
            || policy.path("instructions").textValue().length() > 8000))
            throw new IllegalArgumentException("Autonomy instructions must be text up to 8000 characters.");
    }
    public static Map<String, String> environment(JsonNode provider) {
        validate(provider);
        var result = new LinkedHashMap<String, String>();
        result.put("AI_ORCHESTRATION_AUTONOMY_ENABLED", Boolean.toString(enabled(provider)));
        if (!enabled(provider)) return result;
        var policy = provider.path(FIELD);
        result.put("AI_ORCHESTRATION_AUTONOMY_DEFAULT_ENABLED", Boolean.toString(policy.path("defaultEnabled").asBoolean(true)));
        result.put("AI_ORCHESTRATION_AUTONOMY_REPLAN_NON_AUTHORITY_READ_FAILURES", Boolean.toString(policy.path("replanNonAuthorityReadFailures").asBoolean(true)));
        LIMITS.forEach((field, bounds) -> result.put("AI_ORCHESTRATION_AUTONOMY_" + field.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase(java.util.Locale.ROOT),
            Integer.toString(policy.path(field).asInt(bounds[2]))));
        if (policy.path("instructions").isTextual() && !policy.path("instructions").textValue().isBlank())
            result.put("AI_ORCHESTRATION_AUTONOMY_INSTRUCTIONS", policy.path("instructions").textValue());
        return result;
    }
    public static void requireSupport(JsonNode manifest) {
        if (manifest != null) for (var capability : manifest.path("capabilities"))
            if (CAPABILITY.equals(capability.asText())) return;
        throw new ResponseStatusException(HttpStatus.CONFLICT,
            "The selected runtime does not support autonomous conversations. Choose a compatible runtime update.");
    }
    public static boolean statusMatches(JsonNode provider, JsonNode actual) {
        if (!enabled(provider)) return actual == null || !actual.path("enabled").asBoolean(false);
        if (actual == null || !actual.path("enabled").asBoolean(false) || !"ACTIVE_REQUEST".equals(actual.path("execution").asText())) return false;
        var policy = provider.path(FIELD);
        for (var entry : LIMITS.entrySet())
            if (actual.path(entry.getKey()).asInt(-1) != policy.path(entry.getKey()).asInt(entry.getValue()[2])) return false;
        return actual.path("defaultEnabled").asBoolean(false) == policy.path("defaultEnabled").asBoolean(true)
            && actual.path("replanNonAuthorityReadFailures").asBoolean(false) == policy.path("replanNonAuthorityReadFailures").asBoolean(true);
    }
}
