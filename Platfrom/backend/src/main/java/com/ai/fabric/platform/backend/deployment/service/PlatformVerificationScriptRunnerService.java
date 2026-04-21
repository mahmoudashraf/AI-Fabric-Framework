package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.config.PlatformHostedVerificationProperties;
import com.ai.fabric.platform.backend.config.PlatformVerificationSuiteProperties;
import com.ai.fabric.platform.backend.deployment.model.PlatformVerificationScriptContextSummary;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class PlatformVerificationScriptRunnerService {

    private static final List<String> MANAGED_ENVIRONMENT_KEYS = List.of(
        "PLATFORM_API_KEY",
        "PLATFORM_API_KEY_FILE",
        "PLATFORM_LOGIN_EMAIL",
        "PLATFORM_LOGIN_EMAIL_FILE",
        "PLATFORM_LOGIN_PASSWORD",
        "PLATFORM_LOGIN_PASSWORD_FILE",
        "PINECONE_API_KEY",
        "PINECONE_API_KEY_FILE",
        "QDRANT_CLOUD_MANAGEMENT_API_KEY",
        "QDRANT_CLOUD_MANAGEMENT_API_KEY_FILE",
        "QDRANT_API_KEY",
        "QDRANT_API_KEY_FILE",
        "ZILLIZ_CLOUD_API_KEY",
        "ZILLIZ_CLOUD_API_KEY_FILE",
        "WEAVIATE_API_KEY",
        "WEAVIATE_API_KEY_FILE",
        "SHOPIFY_BRIDGE_ADMIN_API_KEY",
        "SHOPIFY_BRIDGE_ADMIN_API_KEY_FILE",
        "SHOPIFY_ADMIN_ACCESS_TOKEN",
        "SHOPIFY_ADMIN_ACCESS_TOKEN_FILE",
        "SHOPIFY_MERCHANT_AUTHORIZATION",
        "SHOPIFY_MERCHANT_AUTHORIZATION_FILE"
    );

    private final PlatformHostedVerificationProperties hostedVerificationProperties;
    private final PlatformVerificationSuiteProperties suiteProperties;

    public PlatformVerificationScriptRunnerService(PlatformHostedVerificationProperties hostedVerificationProperties,
                                                   PlatformVerificationSuiteProperties suiteProperties) {
        this.hostedVerificationProperties = hostedVerificationProperties;
        this.suiteProperties = suiteProperties;
    }

    public ScriptRunResult run(PlatformVerificationScriptContextSummary context) throws IOException, InterruptedException {
        Path scriptPath = resolveScriptPath(context.scriptPath());
        Path executionDir = Files.createTempDirectory("platform-verification-suite-script-");
        Path outputFile = executionDir.resolve("run.log");
        Process process = null;
        try {
            Map<String, String> env = buildEnvironment(context, executionDir);
            ProcessBuilder builder = new ProcessBuilder("bash", scriptPath.toString());
            builder.directory(scriptPath.getParent().getParent().toFile());
            builder.redirectErrorStream(true);
            builder.redirectOutput(outputFile.toFile());
            MANAGED_ENVIRONMENT_KEYS.forEach(key -> builder.environment().remove(key));
            builder.environment().putAll(env);

            process = builder.start();
            boolean finished = process.waitFor(suiteProperties.scriptStageTimeout().toMillis(), TimeUnit.MILLISECONDS);
            String output = Files.exists(outputFile) ? Files.readString(outputFile) : "";
            if (!finished) {
                process.destroyForcibly();
                return new ScriptRunResult("TIMED_OUT", null, trimOutput(output));
            }
            int exitCode = process.exitValue();
            return new ScriptRunResult(exitCode == 0 ? "PASSED" : "FAILED", exitCode, trimOutput(output));
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            deleteQuietly(executionDir);
        }
    }

    private Map<String, String> buildEnvironment(PlatformVerificationScriptContextSummary context,
                                                 Path executionDir) throws IOException {
        Map<String, String> env = new LinkedHashMap<>(context.environment());
        for (Map.Entry<String, String> entry : context.secretEnvironment().entrySet()) {
            putSecretIfPresent(env, entry.getKey(), entry.getValue(), executionDir);
        }
        return env;
    }

    private void putSecretIfPresent(Map<String, String> env,
                                    String key,
                                    String value,
                                    Path executionDir) throws IOException {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return;
        }
        Path secretFile = writeSecretFile(executionDir, key, normalized);
        env.put(key + "_FILE", secretFile.toString());
    }

    private Path writeSecretFile(Path executionDir,
                                 String key,
                                 String value) throws IOException {
        Files.createDirectories(executionDir);
        Path secretFile = executionDir.resolve(key.toLowerCase() + ".secret");
        Files.writeString(secretFile, value);
        secretFile.toFile().setReadable(false, false);
        secretFile.toFile().setReadable(true, true);
        secretFile.toFile().setWritable(false, false);
        secretFile.toFile().setWritable(true, true);
        secretFile.toFile().setExecutable(false, false);
        return secretFile;
    }

    private Path resolveScriptPath(String configuredScript) throws IOException {
        String scriptName = Paths.get(configuredScript).getFileName().toString();
        List<Path> candidates = List.of(
            Paths.get(hostedVerificationProperties.scriptRoot(), scriptName),
            Paths.get(System.getProperty("user.dir"), hostedVerificationProperties.scriptRoot(), scriptName),
            Paths.get(System.getProperty("user.dir"), "..", "..", hostedVerificationProperties.scriptRoot(), scriptName)
        );
        for (Path candidate : candidates) {
            Path normalized = candidate.normalize();
            if (Files.exists(normalized)) {
                return normalized.toAbsolutePath();
            }
        }
        throw new IOException("Verification script not found: " + configuredScript);
    }

    private String trimOutput(String output) {
        String normalized = output == null ? "" : output.strip();
        if (normalized.length() <= suiteProperties.maxStageLogCharacters()) {
            return normalized;
        }
        return "[truncated]\n" + normalized.substring(normalized.length() - suiteProperties.maxStageLogCharacters());
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private void deleteQuietly(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var stream = Files.walk(root)) {
            stream.sorted((left, right) -> right.getNameCount() - left.getNameCount())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                });
        } catch (IOException ignored) {
        }
    }

    public record ScriptRunResult(
        String status,
        Integer exitCode,
        String output
    ) {
        public boolean passed() {
            return "PASSED".equalsIgnoreCase(status);
        }

        public boolean timedOut() {
            return "TIMED_OUT".equalsIgnoreCase(status);
        }
    }
}
