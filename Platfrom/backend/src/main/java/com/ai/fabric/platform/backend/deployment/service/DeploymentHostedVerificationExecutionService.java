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

    private static final String CONNECTOR_API_KEY_SECRET_NAME = "CONNECTOR_API_KEY";
    private static final String APP_ADMIN_API_KEY_SECRET_NAME = "APP_ADMIN_API_KEY";
    private static final String PLATFORM_OPERATOR_API_KEY_SECRET_NAME = "PLATFORM_OPERATOR_API_KEY";
    private static final String PLATFORM_ADMIN_API_KEY_SECRET_NAME = "PLATFORM_ADMIN_API_KEY";

    private final DeploymentHostedVerificationRunRepository runRepository;
    private final DeploymentHostedVerificationContextService contextService;
    private final PlatformSecretService platformSecretService;
    private final PlatformAuthProperties platformAuthProperties;
    private final PlatformHostedVerificationProperties hostedVerificationProperties;
    private final PlatformAuditService platformAuditService;

    public DeploymentHostedVerificationExecutionService(DeploymentHostedVerificationRunRepository runRepository,
                                                        DeploymentHostedVerificationContextService contextService,
                                                        PlatformSecretService platformSecretService,
                                                        PlatformAuthProperties platformAuthProperties,
                                                        PlatformHostedVerificationProperties hostedVerificationProperties,
                                                        PlatformAuditService platformAuditService) {
        this.runRepository = runRepository;
        this.contextService = contextService;
        this.platformSecretService = platformSecretService;
        this.platformAuthProperties = platformAuthProperties;
        this.hostedVerificationProperties = hostedVerificationProperties;
        this.platformAuditService = platformAuditService;
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
        try {
            DeploymentHostedVerificationContextSummary context = contextService.buildContextForRun(run);
            Path scriptPath = resolveScriptPath(context.script());
            Map<String, String> env = buildExecutionEnvironment(context.env());
            Path outputFile = Files.createTempFile("hosted-verification-", ".log");

            ProcessBuilder builder = new ProcessBuilder("bash", scriptPath.toString());
            builder.directory(scriptPath.getParent().getParent().toFile());
            builder.redirectErrorStream(true);
            builder.redirectOutput(outputFile.toFile());
            builder.environment().putAll(env);

            process = builder.start();
            boolean finished = process.waitFor(hostedVerificationProperties.timeout().toMillis(), TimeUnit.MILLISECONDS);
            String output = Files.exists(outputFile) ? Files.readString(outputFile) : "";
            if (!finished) {
                process.destroyForcibly();
                complete(run, "TIMED_OUT", null, "Hosted verification timed out after " + hostedVerificationProperties.timeout() + ".", output);
                return;
            }

            int exitCode = process.exitValue();
            String status = exitCode == 0 ? "PASSED" : "FAILED";
            String summary = summarize(status, output, exitCode);
            complete(run, status, exitCode, summary, output);
        } catch (Exception ex) {
            String output = "Hosted verification runner failed before the script could complete.\n" + ex.getMessage();
            complete(run, "FAILED", null, "Hosted verification failed before the script could complete.", output);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
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

    private Map<String, String> buildExecutionEnvironment(Map<String, String> contextEnv) {
        Map<String, String> env = new LinkedHashMap<>(contextEnv);
        putIfPresent(env, "API_KEY", platformSecretService.resolveSecret(CONNECTOR_API_KEY_SECRET_NAME));
        String adminApiKey = trimToNull(platformSecretService.resolveSecret(APP_ADMIN_API_KEY_SECRET_NAME));
        putIfPresent(env, "RUNTIME_ADMIN_API_KEY", adminApiKey);
        putIfPresent(env, "CONNECTOR_ADMIN_API_KEY", adminApiKey);

        if (platformAuthProperties.enabled()) {
            String automationApiKey = resolveAutomationApiKey();
            if (automationApiKey != null && platformAuthProperties.apiKeyEnabled()) {
                env.put("PLATFORM_API_KEY_HEADER", platformAuthProperties.headerName());
                env.put("PLATFORM_API_KEY", automationApiKey);
            } else if (platformAuthProperties.bootstrapAdminEnabled()
                && trimToNull(platformAuthProperties.bootstrapAdminEmail()) != null
                && trimToNull(platformAuthProperties.bootstrapAdminPassword()) != null) {
                env.put("PLATFORM_LOGIN_EMAIL", platformAuthProperties.bootstrapAdminEmail().trim());
                env.put("PLATFORM_LOGIN_PASSWORD", platformAuthProperties.bootstrapAdminPassword().trim());
            } else {
                stripPlatformChecks(env);
                env.put("VERIFY_RUNNER_NOTE", "platform-auth-skipped");
            }
        }
        env.put("VERIFY_WRITE", "false");
        return env;
    }

    private void stripPlatformChecks(Map<String, String> env) {
        List<String> keysToRemove = env.keySet().stream()
            .filter(key -> key.startsWith("PLATFORM_"))
            .toList();
        keysToRemove.forEach(env::remove);
    }

    private String resolveAutomationApiKey() {
        String operator = trimToNull(platformAuthProperties.operatorApiKey());
        if (operator != null) {
            return operator;
        }
        operator = trimToNull(platformSecretService.resolveSecret(PLATFORM_OPERATOR_API_KEY_SECRET_NAME));
        if (operator != null) {
            return operator;
        }
        String admin = trimToNull(platformAuthProperties.adminApiKey());
        if (admin != null) {
            return admin;
        }
        return trimToNull(platformSecretService.resolveSecret(PLATFORM_ADMIN_API_KEY_SECRET_NAME));
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

    private String summarize(String status, String output, int exitCode) {
        String marker = "PASSED".equals(status) ? "PASS:" : "FAIL:";
        String summary = output.lines()
            .filter(line -> line.startsWith(marker))
            .reduce((first, second) -> second)
            .orElse(null);
        if (summary != null) {
            return summary;
        }
        return "Hosted verification " + status.toLowerCase() + " with exit code " + exitCode + ".";
    }

    private String trimOutput(String output) {
        String normalized = output == null ? "" : output.strip();
        if (normalized.length() <= hostedVerificationProperties.maxOutputCharacters()) {
            return normalized;
        }
        return "[truncated]\n" + normalized.substring(normalized.length() - hostedVerificationProperties.maxOutputCharacters());
    }

    private void putIfPresent(Map<String, String> env, String key, String value) {
        String normalized = trimToNull(value);
        if (normalized != null) {
            env.put(key, normalized);
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
