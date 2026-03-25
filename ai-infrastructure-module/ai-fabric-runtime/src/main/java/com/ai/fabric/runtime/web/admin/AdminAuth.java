package com.ai.fabric.runtime.web.admin;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;

/**
 * Minimal optional API-key guard for public demo deployments.
 *
 * <p>Dev/test default: if {@code app.admin.api-key} is empty, requests are allowed.
 * Production deployments should set {@code app.admin.api-key} and require callers to provide the matching
 * value in {@code app.admin.api-key-header}.</p>
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
        String headerName = StringUtils.hasText(configuredHeaderName) ? configuredHeaderName : "X-ADMIN-API-KEY";
        String provided = request != null ? request.getHeader(headerName) : null;
        if (!StringUtils.hasText(provided)) {
            return false;
        }
        return constantTimeEquals(configuredApiKey.trim(), provided.trim());
    }

    // Avoid leaking API keys via timing side channels.
    private static boolean constantTimeEquals(String expected, String actual) {
        if (!StringUtils.hasText(expected) || !StringUtils.hasText(actual)) {
            return false;
        }
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = actual.getBytes(StandardCharsets.UTF_8);

        // Keep behavior stable: if lengths differ, treat as not equal.
        if (a.length != b.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }
}
