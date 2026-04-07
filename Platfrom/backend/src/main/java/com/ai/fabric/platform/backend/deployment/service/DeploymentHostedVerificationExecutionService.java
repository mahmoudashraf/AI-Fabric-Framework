package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.PlatformAuthProperties;
import com.ai.fabric.platform.backend.config.PlatformHostedVerificationProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentHostedVerificationRunEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentHostedVerificationContextSummary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentHostedVerificationRunRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
public class DeploymentHostedVerificationExecutionService {

    private static final String RUNTIME_TRUSTED_BACKEND_SECRET_NAME = "AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY";
    private static final String APP_ADMIN_API_KEY_SECRET_NAME = "APP_ADMIN_API_KEY";
    private static final String PLATFORM_OPERATOR_API_KEY_SECRET_NAME = "PLATFORM_OPERATOR_API_KEY";
    private static final String PLATFORM_ADMIN_API_KEY_SECRET_NAME = "PLATFORM_ADMIN_API_KEY";
    private static final List<String> MANAGED_ENVIRONMENT_KEYS = List.of(
        "RUNTIME_TRUSTED_BACKEND_API_KEY",
        "RUNTIME_TRUSTED_BACKEND_API_KEY_FILE",
        "RUNTIME_ADMIN_API_KEY",
        "RUNTIME_ADMIN_API_KEY_FILE",
        "PLATFORM_API_KEY",
        "PLATFORM_API_KEY_FILE",
        "PLATFORM_API_KEY_HEADER",
        "PLATFORM_LOGIN_EMAIL",
        "PLATFORM_LOGIN_EMAIL_FILE",
        "PLATFORM_LOGIN_PASSWORD",
        "PLATFORM_LOGIN_PASSWORD_FILE",
        "VERIFY_WRITE"
    );

    private final DeploymentHostedVerificationRunRepository runRepository;
    private final DeploymentHostedVerificationContextService contextService;
    private final PlatformSecretService platformSecretService;
    private final PlatformAuthProperties platformAuthProperties;
    private final PlatformHostedVerificationProperties hostedVerificationProperties;
    private final PlatformAuditService platformAuditService;
    private final DeploymentHostedVerificationLogParser logParser;

    public DeploymentHostedVerificationExecutionService(DeploymentHostedVerificationRunRepository runRepository,
                                                        DeploymentHostedVerificationContextService contextService,
                                                        PlatformSecretService platformSecretService,
                                                        PlatformAuthProperties platformAuthProperties,
                                                        PlatformHostedVerificationProperties hostedVerificationProperties,
                                                        PlatformAuditService platformAuditService,
                                                        DeploymentHostedVerificationLogParser logParser) {
        this.runRepository = runRepository;
        this.contextService = contextService;
        this.platformSecretService = platformSecretService;
        this.platformAuthProperties = platformAuthProperties;
        this.hostedVerificationProperties = hostedVerificationProperties;
        this.platformAuditService = platformAuditService;
        this.logParser = logParser;
    }

    @Async("hostedVerificationExecutor")
    public void execute(String runId) {
        Optional<DeploymentHostedVerificationRunEntity> optionalRun = runRepository.findById(runId);
        if (optionalRun.isEmpty()) {
            return;
        }
        DeploymentHostedVerificationRunEntity run = optionalRun.get();
        Instant startedAt = Instant.now();
        run.setStatus("RUNNING");
        run.setStartedAt(startedAt);
        run.setSummaryMessage("Hosted verification is running on the platform deployment.");
        runRepository.save(run);

        Process process = null;
        Path executionDir = null;
        try {
            DeploymentHostedVerificationContextSummary context = contextService.buildContextForRun(run);
            Path scriptPath = resolveScriptPath(context.script());
            executionDir = Files.createTempDirectory("hosted-verification-");
            Path outputFile = executionDir.resolve("run.log");
            ExecutionEnvironment executionEnvironment = buildExecutionEnvironment(context, executionDir);
            Map<String, String> env = executionEnvironment.variables();

            ProcessBuilder builder = new ProcessBuilder("bash", scriptPath.toString());
            builder.directory(scriptPath.getParent().getParent().toFile());
            builder.redirectErrorStream(true);
            builder.redirectOutput(outputFile.toFile());
            MANAGED_ENVIRONMENT_KEYS.forEach(key -> builder.environment().remove(key));
            builder.environment().putAll(env);

            process = builder.start();
            boolean finished = process.waitFor(hostedVerificationProperties.timeout().toMillis(), TimeUnit.MILLISECONDS);
            String output = withRunnerContext(context, executionEnvironment.authMode(), Files.exists(outputFile) ? Files.readString(outputFile) : "");
            if (!finished) {
                process.destroyForcibly();
                complete(run, "TIMED_OUT", null, "Hosted verification timed out after " + hostedVerificationProperties.timeout() + ".", output);
                return;
            }

            int exitCode = process.exitValue();
            String status = exitCode == 0 ? "PASSED" : "FAILED";
            String summary = logParser.summarize(status, output, exitCode);
            complete(run, status, exitCode, summary, output);
        } catch (Exception ex) {
            String output = "Hosted verification runner failed before the script could complete.\n" + ex.getMessage();
            complete(run, "FAILED", null, "Hosted verification failed before the script could complete.", output);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            if (executionDir != null) {
                deleteQuietly(executionDir);
            }
        }
    }

    private void complete(DeploymentHostedVerificationRunEntity run,
                          String status,
                          Integer exitCode,
                          String summary,
                          String output) {
        run.setStatus(status);
        run.setExitCode(exitCode);
        run.setSummaryMessage(summary);
        run.setLogOutput(trimOutput(output));
        run.setCompletedAt(Instant.now());
        runRepository.save(run);
        platformAuditService.record(
            "HOSTED_VERIFICATION_COMPLETED",
            "DEPLOYMENT",
            run.getDeploymentId(),
            Map.of(
                "runId", run.getId(),
                "status", status,
                "releaseId", run.getReleaseId(),
                "profile", run.getVerificationProfile()
            )
        );
    }

    private ExecutionEnvironment buildExecutionEnvironment(DeploymentHostedVerificationContextSummary context,
                                                           Path executionDir) throws IOException {
        Map<String, String> env = new LinkedHashMap<>(context.env());
        boolean hasRuntimeSurface = hasServiceBaseUrl(context, "RUNTIME_BASE_URL");
        if (hasRuntimeSurface) {
            putSecretIfPresent(env, "RUNTIME_TRUSTED_BACKEND_API_KEY", platformSecretService.resolveSecret(RUNTIME_TRUSTED_BACKEND_SECRET_NAME), executionDir);
        }
        String adminApiKey = trimToNull(platformSecretService.resolveSecret(APP_ADMIN_API_KEY_SECRET_NAME));
        if (hasRuntimeSurface) {
            putSecretIfPresent(env, "RUNTIME_ADMIN_API_KEY", adminApiKey, executionDir);
        }
        String authMode = "platform-auth-disabled";

        if (platformAuthProperties.enabled()) {
            String automationApiKey = resolveAutomationApiKey();
            if (automationApiKey != null && platformAuthProperties.apiKeyEnabled()) {
                env.put("PLATFORM_API_KEY_HEADER", platformAuthProperties.headerName());
                putSecretIfPresent(env, "PLATFORM_API_KEY", automationApiKey, executionDir);
                authMode = "platform-api-key";
            } else if (platformAuthProperties.bootstrapAdminEnabled()
                && trimToNull(platformAuthProperties.bootstrapAdminEmail()) != null
                && trimToNull(platformAuthProperties.bootstrapAdminPassword()) != null) {
                putSecretIfPresent(env, "PLATFORM_LOGIN_EMAIL", platformAuthProperties.bootstrapAdminEmail().trim(), executionDir);
                putSecretIfPresent(env, "PLATFORM_LOGIN_PASSWORD", platformAuthProperties.bootstrapAdminPassword().trim(), executionDir);
                authMode = "platform-session-login";
            } else {
                stripPlatformChecks(env);
                env.put("VERIFY_RUNNER_NOTE", "platform-auth-skipped");
                authMode = "platform-auth-skipped";
            }
        }
        env.put("VERIFY_WRITE", Boolean.toString(context.verifyWrite()));
        return new ExecutionEnvironment(env, authMode);
    }

    private boolean hasServiceBaseUrl(DeploymentHostedVerificationContextSummary context, String key) {
        return trimToNull(context.env().get(key)) != null;
    }

    private void stripPlatformChecks(Map<String, String> env) {
        List<String> keysToRemove = env.keySet().stream()
            .filter(key -> key.startsWith("PLATFORM_"))
            .toList();
        keysToRemove.forEach(env::remove);
    }

    private String resolveAutomationApiKey() {
        String admin = trimToNull(platformAuthProperties.adminApiKey());
        if (admin != null) {
            return admin;
        }
        admin = trimToNull(platformSecretService.resolveSecret(PLATFORM_ADMIN_API_KEY_SECRET_NAME));
        if (admin != null) {
            return admin;
        }
        String operator = trimToNull(platformAuthProperties.operatorApiKey());
        if (operator != null) {
            return operator;
        }
        return trimToNull(platformSecretService.resolveSecret(PLATFORM_OPERATOR_API_KEY_SECRET_NAME));
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
        if (normalized.length() <= hostedVerificationProperties.maxOutputCharacters()) {
            return normalized;
        }
        return "[truncated]\n" + normalized.substring(normalized.length() - hostedVerificationProperties.maxOutputCharacters());
    }

    private String withRunnerContext(DeploymentHostedVerificationContextSummary context,
                                     String authMode,
                                     String output) {
        StringBuilder builder = new StringBuilder();
        builder.append("== Runner Context ==\n");
        builder.append("Deployment: ").append(context.deploymentId()).append('\n');
        builder.append("Release: ").append(context.releaseId()).append('\n');
        builder.append("Version: ").append(context.deploymentVersionId()).append('\n');
        builder.append("Profile: ").append(context.profile()).append('\n');
        builder.append("Script: ").append(context.script()).append('\n');
        builder.append("Verify write: ").append(context.verifyWrite()).append('\n');
        builder.append("Platform auth mode: ").append(authMode).append('\n');
        builder.append("Platform checks: ").append(context.env().containsKey("PLATFORM_BASE_URL") ? "enabled" : "disabled").append('\n');
        builder.append('\n');
        builder.append(output == null ? "" : output);
        return builder.toString();
    }

    private void putIfPresent(Map<String, String> env, String key, String value) {
        String normalized = trimToNull(value);
        if (normalized != null) {
            env.put(key, normalized);
        }
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

    private record ExecutionEnvironment(
        Map<String, String> variables,
        String authMode
    ) {
    }
}
