package com.ai.fabric.platform.backend.deployment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.springframework.http.HttpStatus.CONFLICT;

/** Only these provider-owned source attestations change with the reviewed image. */
public final class RuntimeArtifactRepairProvenance {
    private RuntimeArtifactRepairProvenance() { }
    private static final Set<String> KEYS = Set.of("APP_BUILD_COMMIT", "PLATFORM_SOURCE_COMMIT");
    public record Inventory(ArrayNode stable, String commit, List<JsonNode> sourceRecords) { }

    public static Inventory inspect(JsonNode raw) {
        if (raw == null || !raw.isArray() || raw.isEmpty()) fail();
        List<JsonNode> rows = new ArrayList<>(); raw.forEach(rows::add);
        rows.sort(Comparator.comparing(row -> row.path("uuid").asText()));
        var ids = new HashSet<String>(); var identities = new HashSet<String>();
        List<JsonNode> source = new ArrayList<>();
        ArrayNode stable = ((ArrayNode) raw).arrayNode(); String commit = null;
        for (var row : rows) {
            if (!row.isObject() || row.path("uuid").asText().isBlank() || !ids.add(row.path("uuid").asText())) fail();
            var projected = (ObjectNode) row.deepCopy();
            if (KEYS.contains(row.path("key").asText())) {
                String value = row.path("value").asText();
                if (!value.matches("[a-f0-9]{40}") || !row.path("real_value").isTextual()
                    || !(value.equals(row.path("real_value").textValue()) || row.path("value").toString().equals(row.path("real_value").textValue())
                        || ("'" + value + "'").equals(row.path("real_value").textValue()))
                    || commit != null && !commit.equals(value)
                    || !row.path("is_preview").isBoolean() || !row.path("is_runtime").isBoolean() || !row.path("is_runtime").booleanValue()
                    || !row.path("is_buildtime").isBoolean() || row.path("is_buildtime").booleanValue()
                    || !row.path("is_literal").isBoolean() || !row.path("is_literal").booleanValue()
                    || row.path("is_multiline").asBoolean(true) || row.path("is_shared").asBoolean(true)
                    || row.path("is_shown_once").asBoolean(true)
                    || !identities.add(row.path("key").asText() + ":" + row.path("is_preview").booleanValue())) fail();
                commit = value; source.add(row.deepCopy());
                projected.put("value", "REVIEWED_SOURCE_COMMIT").put("real_value",
                    value.equals(row.path("real_value").textValue()) ? "REVIEWED_SOURCE_COMMIT"
                        : row.path("real_value").textValue().startsWith("'") ? "'REVIEWED_SOURCE_COMMIT'" : "\"REVIEWED_SOURCE_COMMIT\"");
                projected.remove("updated_at");
            }
            stable.add(projected);
        }
        if (source.size() != 4 || identities.size() != 4) fail();
        return new Inventory(stable, commit, List.copyOf(source));
    }
    private static void fail() {
        throw new org.springframework.web.server.ResponseStatusException(CONFLICT, "RUNTIME_ARTIFACT_REPAIR_SOURCE_PROVENANCE_UNPROVEN");
    }
}
