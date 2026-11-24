package com.ai.fabric.platform.backend.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** The AI editor's writable-field contract, independent of runtime authority. */
final class SpecialistAuthoringPolicy {
    private static final Set<String> ROOT = Set.of("enabled", "areas", "resolutions", "limits");
    private static final Set<String> AREA = Set.of("id", "name", "purpose", "instructions", "vectorSpaces", "readActions");
    private static final Set<String> PLAN = Set.of("id", "name", "objective", "instructions", "areaIds", "allowParallel");
    private static final Set<String> LIMITS = Set.of("maxDecisions", "maxWorkerInvocations", "maxParallelWorkers", "deadlineSeconds");

    private SpecialistAuthoringPolicy() {}

    static void requireSupportedEdits(JsonNode before, JsonNode after) {
        var current = before.at("/behaviorConfig/specialistAssistance");
        var candidate = after.at("/behaviorConfig/specialistAssistance");
        supported(current, candidate, ROOT);
        supported(current.path("limits"), candidate.path("limits"), LIMITS);
        items(current.path("areas"), candidate.path("areas"), AREA);
        items(current.path("resolutions"), candidate.path("resolutions"), PLAN);
    }

    private static void items(JsonNode before, JsonNode after, Set<String> fields) {
        if (!after.isArray()) return;
        for (var item : after) {
            JsonNode previous = null;
            if (before.isArray() && item.path("id").isTextual()) {
                for (var old : before) if (item.path("id").equals(old.path("id"))) { previous = old; break; }
            }
            supported(previous, item, fields);
        }
    }

    private static void supported(JsonNode before, JsonNode after, Set<String> fields) {
        if (!after.isObject()) return;
        var names = new java.util.HashSet<String>();
        after.fieldNames().forEachRemaining(names::add);
        if (before != null && before.isObject()) before.fieldNames().forEachRemaining(names::add);
        for (String name : names) {
            if (!fields.contains(name) && !java.util.Objects.equals(before == null ? null : before.get(name), after.get(name))) {
                // Unknown/server-owned fields cannot become invented writable settings, even while disabled.
                throw new AuthoringContractException("SPECIALIST_AUTHORING_FIELD_UNSUPPORTED",
                    "SPECIALIST_AUTHORING_FIELD_UNSUPPORTED: Use only the documented specialist, area, resolution and limit fields. "
                        + "Preserve unknown or compiled metadata from the saved draft unchanged. Explicitly REMOVE unsupported additions from previous proposals; SET omission does not delete them. "
                        + "Manager instructions belong in the resolution's instructions field.");
            }
        }
    }
}
