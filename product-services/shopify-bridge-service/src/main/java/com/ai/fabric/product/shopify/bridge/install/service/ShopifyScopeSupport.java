package com.ai.fabric.product.shopify.bridge.install.service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ShopifyScopeSupport {

    private ShopifyScopeSupport() {
    }

    public static List<String> parseScopes(String scopesText) {
        if (scopesText == null || scopesText.isBlank()) {
            return List.of();
        }
        Set<String> scopes = new LinkedHashSet<>();
        for (String rawScope : scopesText.split(",")) {
            String normalized = normalizeScope(rawScope);
            if (normalized != null) {
                scopes.add(normalized);
            }
        }
        return List.copyOf(scopes);
    }

    public static List<String> normalizeScopes(Collection<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return List.of();
        }
        Set<String> normalizedScopes = new LinkedHashSet<>();
        for (String rawScope : scopes) {
            String normalized = normalizeScope(rawScope);
            if (normalized != null) {
                normalizedScopes.add(normalized);
            }
        }
        return List.copyOf(normalizedScopes);
    }

    public static String joinScopes(Collection<String> scopes) {
        List<String> normalized = normalizeScopes(scopes);
        return normalized.isEmpty() ? null : String.join(",", normalized);
    }

    public static boolean hasScope(String scopesText, String requiredScope) {
        return hasScope(parseScopes(scopesText), requiredScope);
    }

    public static boolean hasScope(Collection<String> scopes, String requiredScope) {
        String normalizedRequiredScope = normalizeScope(requiredScope);
        if (normalizedRequiredScope == null || scopes == null || scopes.isEmpty()) {
            return false;
        }
        for (String scope : scopes) {
            if (normalizedRequiredScope.equals(normalizeScope(scope))) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeScope(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
