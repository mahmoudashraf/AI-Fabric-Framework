package com.ai.infrastructure.storage.strategy.impl;

import com.ai.infrastructure.config.AIEntityConfigurationLoader;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.util.StringUtils;

@RequiredArgsConstructor
public class PerTypeRepositoryFactory {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final AIEntityConfigurationLoader configurationLoader;
    private final Map<String, PerTypeRepository> repositories = new ConcurrentHashMap<>();

    public PerTypeRepository getRepositoryForType(String entityType) {
        String normalizedType = normalizeEntityType(entityType);
        return repositories.computeIfAbsent(normalizedType,
            type -> new PerTypeRepository(jdbcTemplate, normalizedType, toTableName(normalizedType)));
    }

    public Map<String, PerTypeRepository> getAllRepositories() {
        initializeConfiguredRepositories();
        return Collections.unmodifiableMap(repositories);
    }

    public List<String> getKnownEntityTypes() {
        initializeConfiguredRepositories();
        return repositories.keySet().stream().sorted().toList();
    }

    private void initializeConfiguredRepositories() {
        Set<String> configuredTypes = configurationLoader != null
            ? configurationLoader.getSupportedEntityTypes()
            : Set.of();
        for (String type : configuredTypes) {
            getRepositoryForType(type);
        }
    }

    public static String toTableName(String entityType) {
        return "ai_searchable_" + normalizeEntityType(entityType);
    }

    private static String normalizeEntityType(String entityType) {
        if (!StringUtils.hasText(entityType)) {
            return "generic";
        }
        String cleaned = entityType
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "_")
            .replaceAll("_+", "_");
        if (cleaned.startsWith("_")) {
            cleaned = cleaned.substring(1);
        }
        if (cleaned.endsWith("_")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        return cleaned.isBlank() ? "generic" : cleaned;
    }
}
