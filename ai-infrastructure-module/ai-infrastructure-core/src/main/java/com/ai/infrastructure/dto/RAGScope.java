package com.ai.infrastructure.dto;

import lombok.Builder;
import lombok.Value;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Structured scope/hints derived from authoritative pinned targets (active attachments / stored pinned targets).
 *
 * <p>Used to influence retrieval and deep-mode query expansion without concatenating raw attachment content into
 * the retrieval query string.</p>
 */
@Value
@Builder(toBuilder = true)
public class RAGScope {

    public enum Source {
        ACTIVE_ATTACHMENTS,
        STORED_PINNED_TARGETS,
        MIXED
    }

    @Value
    @Builder(toBuilder = true)
    public static class Target {
        String id;
        String vectorSpace;

        @Builder.Default
        Map<String, String> metadata = Map.of();

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            if (StringUtils.hasText(id)) {
                map.put("id", id);
            }
            if (StringUtils.hasText(vectorSpace)) {
                map.put("vectorSpace", vectorSpace);
            }
            if (metadata != null && !metadata.isEmpty()) {
                map.put("metadata", metadata);
            }
            return Collections.unmodifiableMap(map);
        }
    }

    @Builder.Default
    List<Target> targets = List.of();

    @Builder.Default
    List<String> activeTargetIds = List.of();

    Source source;

    @Builder.Default
    boolean truncated = false;

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        List<Map<String, Object>> targetsOut = targets != null
            ? targets.stream().map(Target::toMap).toList()
            : List.of();
        List<String> activeOut = activeTargetIds != null ? List.copyOf(activeTargetIds) : List.of();

        map.put("provided", !targetsOut.isEmpty() || !activeOut.isEmpty());
        map.put("targetCount", targetsOut.size());
        map.put("targets", targetsOut);
        map.put("activeTargetIds", activeOut);
        if (source != null) {
            map.put("source", source.name());
        }
        map.put("truncated", truncated);
        return Collections.unmodifiableMap(map);
    }
}
