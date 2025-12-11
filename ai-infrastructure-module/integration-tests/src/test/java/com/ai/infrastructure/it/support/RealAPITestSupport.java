package com.ai.infrastructure.it.support;

import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Utility methods shared across Real API integration tests.
 *
 * Loads OpenAI API key from env.dev file (line 29).
 */
public final class RealAPITestSupport {

    private static final String OPENAI_KEY_PROPERTY = "OPENAI_API_KEY";
    private static final Path ENV_DEV_PATH = Paths.get("/workspace/env.dev");
    private static final Path BACKEND_ENV_PATH = Paths.get("/workspace/backend/.env");
    private static final Path BACKEND_ENV_DEV_PATH = Paths.get("/workspace/backend/.env.dev");
    private static final Path BACKEND_ENV_DEVELOPMENT_PATH = Paths.get("/workspace/backend/.env.development");

    private static volatile boolean configured = false;

    private RealAPITestSupport() {
    }

    /**
     * Ensure the OpenAI API key is available via {@link System#getProperty(String)}.
     * 
     * Reads from env.dev line 29: OPENAI_API_KEY=sk-...
     */
    public static synchronized void ensureOpenAIConfigured() {
        if (configured) {
            return;
        }

        // Prefer environment variable if already set
        String apiKey = System.getenv(OPENAI_KEY_PROPERTY);
        
        // Fall back to reading from env.dev file
        if (!StringUtils.hasText(apiKey)) {
            apiKey = readOpenAIKeyFromEnvFiles();
        }

        // Set as system property if found
        if (StringUtils.hasText(apiKey)) {
            System.setProperty(OPENAI_KEY_PROPERTY, apiKey);
            System.setProperty("ai.providers.openai.api-key", apiKey);
            configured = true;
        }
    }

    /**
     * Read OpenAI API key directly from env.dev file.
     * Line 29 format: OPENAI_API_KEY=sk-proj-...
     */
    private static String readOpenAIKeyFromEnvFiles() {
        List<Path> candidates = List.of(
            ENV_DEV_PATH,
            BACKEND_ENV_PATH,
            BACKEND_ENV_DEV_PATH,
            BACKEND_ENV_DEVELOPMENT_PATH
        );

        for (Path path : candidates) {
            if (!Files.exists(path) || !Files.isRegularFile(path)) {
                continue;
            }
            try {
                String key = Files.readAllLines(path, StandardCharsets.UTF_8)
                    .stream()
                    .map(String::trim)
                    .filter(line -> line.startsWith(OPENAI_KEY_PROPERTY + "="))
                    .map(line -> line.substring((OPENAI_KEY_PROPERTY + "=").length()))
                    .findFirst()
                    .orElse(null);
                if (StringUtils.hasText(key)) {
                    return key;
                }
            } catch (IOException ex) {
                System.err.printf("Unable to read OPENAI_API_KEY from %s: %s%n", path, ex.getMessage());
            }
        }
        return null;
    }
}
