package com.ai.fabric.runtime.workflow;

import ai.fabric.execution.specialist.SpecialistId;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Immutable published profiles and task bindings; never selected from arbitrary request configuration. */
@Component
public final class WorkflowAssistanceBundle {
    public static final String CONTRACT = "LOOMAI_WORKFLOW_ASSISTANCE_V1";
    private final JsonNode content;
    private final String hash;
    private final Map<String, Profile> profiles;
    private final Map<String, String> bindings;
    private final String defaultProfile;
    public record Profile(String id, String name, SpecialistId specialist, Set<String> requiredScopes) { }

    public WorkflowAssistanceBundle(ObjectMapper mapper,
        @Value("${LOOMAI_WORKFLOW_ASSISTANCE_BUNDLE_BASE64:}") String encoded,
        @Value("${LOOMAI_WORKFLOW_ASSISTANCE_BUNDLE_HASH:}") String expectedHash) {
        if (encoded == null || encoded.isBlank()) {
            if (expectedHash != null && !expectedHash.isBlank()) throw invalid();
            content = mapper.createObjectNode(); hash = null; profiles = Map.of(); bindings = Map.of(); defaultProfile = null; return;
        }
        try {
            if (encoded.length() > 87384) throw invalid();
            byte[] bytes = Base64.getDecoder().decode(encoded);
            hash = "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if (bytes.length > 65536 || !hash.equals(expectedHash)) throw invalid();
            content = mapper.copy().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).readTree(bytes);
            if (!CONTRACT.equals(content.path("contractVersion").asText()) || !content.path("resources").isArray()
                || content.path("resources").isEmpty() || content.path("resources").size() > 32
                || !content.path("profiles").isArray() || content.path("profiles").isEmpty() || content.path("profiles").size() > 9
                || !content.path("taskBindings").isArray() || content.path("taskBindings").size() > 32) throw invalid();
            var parsed = new LinkedHashMap<String, Profile>();
            for (var item : content.path("profiles")) {
                String id = text(item, "id", 48); String name = text(item, "name", 120);
                if (!id.matches("[a-z][a-z0-9]*(?:-[a-z0-9]+)*")) throw invalid();
                var specialist = SpecialistId.parse(text(item, "specialistRef", 128));
                var scopes = new LinkedHashSet<String>();
                if (!item.path("requiredScopes").isArray() || item.path("requiredScopes").size() > 64) throw invalid();
                for (var scope : item.path("requiredScopes")) {
                    if (!scope.isTextual() || !scope.textValue().matches("[A-Za-z][A-Za-z0-9._@:/-]{0,199}") || !scopes.add(scope.textValue())) throw invalid();
                }
                if (!scopes.contains("specialist:" + specialist) || parsed.putIfAbsent(id, new Profile(id, name, specialist, Set.copyOf(scopes))) != null) throw invalid();
            }
            profiles = Map.copyOf(parsed);
            defaultProfile = text(content, "defaultProfileId", 48);
            if (!profiles.containsKey(defaultProfile)) throw invalid();
            var routes = new LinkedHashMap<String, String>();
            for (var route : content.path("taskBindings")) {
                String task = text(route, "task", 64); String profile = text(route, "profileId", 48);
                if (!task.matches("[A-Z][A-Z0-9_]{1,63}") || !profiles.containsKey(profile) || routes.putIfAbsent(task, profile) != null) throw invalid();
            }
            bindings = Map.copyOf(routes);
        } catch (Exception failure) { throw invalid(); }
    }
    public boolean enabled() { return hash != null; }
    public String contentHash() { return hash; }
    public JsonNode content() { return content.deepCopy(); }
    public Profile select(String task, String requestedProfile) {
        if (!enabled() || task == null || !task.matches("[A-Z][A-Z0-9_]{1,63}")
            || !bindings.containsKey(task) && !"CONFIGURATION".equals(task)) throw new IllegalArgumentException("WORKFLOW_TASK_UNAVAILABLE");
        String configured = bindings.getOrDefault(task, defaultProfile);
        if (requestedProfile != null && !requestedProfile.isBlank() && !configured.equals(requestedProfile))
            throw new IllegalArgumentException("WORKFLOW_PROFILE_NOT_ALLOWED_FOR_TASK");
        return profiles.get(configured);
    }
    private static String text(JsonNode node, String field, int max) {
        var value = node.path(field);
        if (!value.isTextual() || value.textValue().isBlank() || value.textValue().length() > max) throw invalid();
        return value.textValue();
    }
    private static IllegalStateException invalid() { return new IllegalStateException("Workflow assistance requires a bounded hash-verified published bundle."); }
}
