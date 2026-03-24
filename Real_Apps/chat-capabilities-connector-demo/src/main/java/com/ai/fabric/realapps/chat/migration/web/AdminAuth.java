package com.ai.fabric.realapps.chat.migration.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

/**
 * Minimal optional API-key guard for destructive admin endpoints.
 *
 * <p>If {@code connector.auth.api-key} is empty, requests are allowed (dev-friendly).
 * If set, caller must provide the matching value in {@code connector.auth.api-key-header}.</p>
 */
final class AdminAuth {

    private AdminAuth() {
    }

    static boolean isAuthorized(String configuredApiKey,
                                String configuredHeaderName,
                                HttpServletRequest request) {
        if (!StringUtils.hasText(configuredApiKey)) {
            return true;
        }
        String headerName = StringUtils.hasText(configuredHeaderName) ? configuredHeaderName : "X-AIFABRIC-API-KEY";
        String provided = request != null ? request.getHeader(headerName) : null;
        return configuredApiKey.equals(provided);
    }
}

