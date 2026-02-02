package com.ai.infrastructure.rag.scope;

import com.ai.infrastructure.dto.RAGScope;
import com.ai.infrastructure.intent.orchestration.targets.ResolvedTarget;
import com.ai.infrastructure.intent.orchestration.targets.ResolvedTargetSource;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds a bounded {@link RAGScope} from authoritative pinned targets.
 */
public final class RAGScopeFactory {

    private static final int DEFAULT_MAX_TARGETS = 5;
    private static final int DEFAULT_MAX_METADATA_KEYS = 12;
    private static final int DEFAULT_MAX_METADATA_VALUE_CHARS = 120;

    private RAGScopeFactory() {
    }

    public static RAGScope build(List<ResolvedTarget> resolvedTargets, List<String> activeTargetIds) {
        return build(resolvedTargets, activeTargetIds, DEFAULT_MAX_TARGETS, DEFAULT_MAX_METADATA_KEYS, DEFAULT_MAX_METADATA_VALUE_CHARS);
    }

    public static RAGScope build(List<ResolvedTarget> resolvedTargets,
                                 List<String> activeTargetIds,
                                 int maxTargets,
                                 int maxMetadataKeys,
                                 int maxMetadataValueChars) {
        int safeMaxTargets = Math.max(1, maxTargets);
        int safeMaxMetadataKeys = Math.max(0, maxMetadataKeys);
        int safeMaxValueChars = Math.max(0, maxMetadataValueChars);

        boolean truncated = false;

        List<RAGScope.Target> targets = new ArrayList<>();
        Set<ResolvedTargetSource> sources = new LinkedHashSet<>();

        if (resolvedTargets != null) {
            for (ResolvedTarget target : resolvedTargets) {
                if (target == null || !StringUtils.hasText(target.getId())) {
                    continue;
                }
                if (targets.size() >= safeMaxTargets) {
                    truncated = true;
                    break;
                }

                String id = target.getId().trim();
                if (!StringUtils.hasText(id)) {
                    continue;
                }

                sources.add(target.getSource());

                String vectorSpace = StringUtils.hasText(target.getVectorSpace()) ? target.getVectorSpace().trim() : null;

                Map<String, String> metadata = new LinkedHashMap<>();
                Map<String, String> raw = target.getMetadata();
                if (raw != null && !raw.isEmpty() && safeMaxMetadataKeys > 0) {
                    for (Map.Entry<String, String> entry : raw.entrySet()) {
                        if (entry == null || !StringUtils.hasText(entry.getKey()) || !StringUtils.hasText(entry.getValue())) {
                            continue;
                        }
                        if (metadata.size() >= safeMaxMetadataKeys) {
                            truncated = true;
                            break;
                        }
                        String key = entry.getKey().trim();
                        if (!StringUtils.hasText(key)) {
                            continue;
                        }
                        String value = entry.getValue().trim();
                        if (!StringUtils.hasText(value)) {
                            continue;
                        }
                        if (safeMaxValueChars > 0 && value.length() > safeMaxValueChars) {
                            value = value.substring(0, safeMaxValueChars);
                            truncated = true;
                        }
                        metadata.put(key, value);
                    }
                }

                targets.add(RAGScope.Target.builder()
                    .id(id)
                    .vectorSpace(vectorSpace)
                    .metadata(Map.copyOf(metadata))
                    .build());
            }
        }

        List<String> activeIds = normalizeActiveIds(activeTargetIds, safeMaxTargets);

        RAGScope.Source source = determineSource(sources, activeIds);
        return RAGScope.builder()
            .targets(List.copyOf(targets))
            .activeTargetIds(activeIds)
            .source(source)
            .truncated(truncated)
            .build();
    }

    private static List<String> normalizeActiveIds(List<String> raw, int max) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        Set<String> ids = new LinkedHashSet<>();
        for (String value : raw) {
            if (!StringUtils.hasText(value)) {
                continue;
            }
            if (ids.size() >= max) {
                break;
            }
            ids.add(value.trim());
        }
        return List.copyOf(ids);
    }

    private static RAGScope.Source determineSource(Set<ResolvedTargetSource> sources, List<String> activeIds) {
        boolean hasActiveIds = activeIds != null && !activeIds.isEmpty();
        if (sources == null || sources.isEmpty()) {
            return hasActiveIds ? RAGScope.Source.ACTIVE_ATTACHMENTS : null;
        }
        if (sources.size() > 1) {
            return RAGScope.Source.MIXED;
        }

        ResolvedTargetSource single = sources.iterator().next();
        if (single == ResolvedTargetSource.ACTIVE_ATTACHMENTS || hasActiveIds) {
            return RAGScope.Source.ACTIVE_ATTACHMENTS;
        }
        return RAGScope.Source.STORED_PINNED_TARGETS;
    }
}

