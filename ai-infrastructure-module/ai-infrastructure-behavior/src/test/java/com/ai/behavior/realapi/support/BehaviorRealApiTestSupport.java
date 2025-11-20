package com.ai.behavior.realapi.support;

import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public final class BehaviorRealApiTestSupport {

    private static final String OPENAI_KEY_PROPERTY = "OPENAI_API_KEY";
    private static final Path[] CANDIDATE_ENV_PATHS = new Path[] {
        Paths.get("/workspace/env.dev"),
        Paths.get("/workspace/backend/env.dev"),
        Paths.get("../env.dev"),
        Paths.get("../backend/env.dev")
    };

    private static volatile boolean configured;

    private BehaviorRealApiTestSupport() {
    }

    public static synchronized void ensureProvidersConfigured() {
        if (configured) {
            return;
        }

        String apiKey = System.getenv(OPENAI_KEY_PROPERTY);
        if (!StringUtils.hasText(apiKey)) {
            apiKey = System.getProperty(OPENAI_KEY_PROPERTY);
        }
        if (!StringUtils.hasText(apiKey)) {
            apiKey = locateKeyFromEnvFiles();
        }

        if (StringUtils.hasText(apiKey)) {
            System.setProperty(OPENAI_KEY_PROPERTY, apiKey);
            System.setProperty("ai.providers.openai.api-key", apiKey);
        }

        System.setProperty("LLM_PROVIDER", System.getProperty("LLM_PROVIDER", "openai"));
        System.setProperty("ai.providers.llm-provider", System.getProperty("ai.providers.llm-provider", "openai"));
        System.setProperty("EMBEDDING_PROVIDER", System.getProperty("EMBEDDING_PROVIDER", "onnx"));
        System.setProperty("ai.providers.embedding-provider", System.getProperty("ai.providers.embedding-provider", "onnx"));

        configured = true;
    }

    public static boolean hasOpenAIKey() {
        return StringUtils.hasText(System.getenv(OPENAI_KEY_PROPERTY))
            || StringUtils.hasText(System.getProperty(OPENAI_KEY_PROPERTY));
    }

    private static String locateKeyFromEnvFiles() {
        for (Path path : CANDIDATE_ENV_PATHS) {
            if (!Files.exists(path) || !Files.isRegularFile(path)) {
                continue;
            }
            try (Stream<String> lines = Files.lines(path, StandardCharsets.UTF_8)) {
                return lines
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#") && line.contains("="))
                    .map(line -> line.split("=", 2))
                    .filter(parts -> parts.length == 2 && OPENAI_KEY_PROPERTY.equals(parts[0].trim()))
                    .map(parts -> parts[1].trim())
                    .findFirst()
                    .orElse(null);
            } catch (IOException ignored) {
            }
        }
        return null;
    }
}
