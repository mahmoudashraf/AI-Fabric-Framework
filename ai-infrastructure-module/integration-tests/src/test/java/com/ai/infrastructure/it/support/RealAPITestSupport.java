package com.ai.infrastructure.it.support;

import org.springframework.util.StringUtils;

/**
 * Utility methods shared across Real API integration tests.
 *
 * Resolves provider credentials strictly from environment variables and/or JVM system properties.
 *
 * NOTE: This test support intentionally does not read .env/env.dev files from disk. CI and local
 * development should provide credentials via environment variables or JVM -D properties.
 */
public final class RealAPITestSupport {

    private static final String OPENAI_KEY_PROPERTY = "OPENAI_API_KEY";

    private static volatile boolean configured = false;

    private RealAPITestSupport() {
    }

    /**
     * Ensure the OpenAI API key is available via {@link System#getProperty(String)}.
     */
    public static synchronized void ensureOpenAIConfigured() {
        if (configured) {
            return;
        }

        // Prefer explicit JVM system property (e.g., mvn test -DOPENAI_API_KEY=...)
        String apiKey = System.getProperty(OPENAI_KEY_PROPERTY);
        // Fall back to environment variable (e.g., CI secret)
        if (!StringUtils.hasText(apiKey)) {
            apiKey = System.getenv(OPENAI_KEY_PROPERTY);
        }

        // Set as system property if found
        if (StringUtils.hasText(apiKey)) {
            System.setProperty(OPENAI_KEY_PROPERTY, apiKey);
            System.setProperty("ai.providers.openai.api-key", apiKey);
            configured = true;
        }
    }
}
