package com.ai.fabric.platform.backend.marketplace.service;

import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class MarketplaceShellModuleRegistry {

    private static final Set<String> ALLOWED_MODULE_IDS = Set.of(
        "docs",
        "products",
        "ai-search",
        "actions"
    );

    public List<String> sanitize(List<String> requestedModuleIds) {
        if (requestedModuleIds == null || requestedModuleIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> sanitized = new LinkedHashSet<>();
        requestedModuleIds.stream()
            .filter(moduleId -> moduleId != null && !moduleId.isBlank())
            .map(String::trim)
            .filter(ALLOWED_MODULE_IDS::contains)
            .forEach(sanitized::add);
        return List.copyOf(sanitized);
    }

    public Set<String> allowedModuleIds() {
        return ALLOWED_MODULE_IDS;
    }
}
