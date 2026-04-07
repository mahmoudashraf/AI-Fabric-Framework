package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.PlatformAuthProperties;
import com.ai.fabric.platform.backend.config.PlatformHostedVerificationProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentHostedVerificationRunEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentHostedVerificationContextSummary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentHostedVerificationRunRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeploymentHostedVerificationExecutionServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void executeRunsHostedScriptAndMarksRunPassed() throws Exception {
        Path script = tempDir.resolve("verify-vector-deployment.sh");
        Files.writeString(
            script,
            "#!/usr/bin/env bash\n" +
                "set -euo pipefail\n" +
                "[[ \"${VERIFY_WRITE:-}\" == \"false\" ]]\n" +
                "[[ -z \"${API_KEY:-}\" ]]\n" +
                "[[ -z \"${API_KEY_FILE:-}\" ]]\n" +
                "[[ -z \"${RUNTIME_TRUSTED_BACKEND_API_KEY:-}\" ]]\n" +
                "[[ -f \"${RUNTIME_TRUSTED_BACKEND_API_KEY_FILE:-}\" ]]\n" +
                "[[ \"$(cat \"${RUNTIME_TRUSTED_BACKEND_API_KEY_FILE}\")\" == \"runtime-secret\" ]]\n" +
                "[[ -z \"${CONNECTOR_ADMIN_API_KEY:-}\" ]]\n" +
                "[[ -z \"${CONNECTOR_ADMIN_API_KEY_FILE:-}\" ]]\n" +
                "[[ -z \"${PLATFORM_LOGIN_PASSWORD:-}\" ]]\n" +
                "[[ -f \"${PLATFORM_LOGIN_PASSWORD_FILE:-}\" ]]\n" +
                "[[ \"$(cat \"${PLATFORM_LOGIN_PASSWORD_FILE}\")\" == \"bootstrap-password\" ]]\n" +
                "echo \"PASS: hosted runner stub passed\"\n"
        );

        DeploymentHostedVerificationRunRepository runRepository = mock(DeploymentHostedVerificationRunRepository.class);
        DeploymentHostedVerificationContextService contextService = mock(DeploymentHostedVerificationContextService.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        PlatformAuditService auditService = mock(PlatformAuditService.class);

        DeploymentHostedVerificationRunEntity run = new DeploymentHostedVerificationRunEntity();
        run.setId("hvr-123");
        run.setDeploymentId("dep-123");
        run.setReleaseId("rel-123");
        run.setDeploymentVersionId("ver-123");
        run.setVerificationProfile("vector");
        run.setRunnerType("PLATFORM_HOSTED");
        run.setScriptPath("scripts/verify-vector-deployment.sh");
        run.setStatus("QUEUED");
        run.setVerifyWrite(false);
        run.setSummaryMessage("queued");
        run.setLogOutput("");
        run.setCreatedAt(Instant.now());

        when(runRepository.findById("hvr-123")).thenReturn(Optional.of(run));
        when(runRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(platformSecretService.resolveSecret("CONNECTOR_API_KEY")).thenReturn("connector-secret");
        when(platformSecretService.resolveSecret("AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY")).thenReturn("runtime-secret");
        when(contextService.buildContextForRun(run)).thenReturn(
            new DeploymentHostedVerificationContextSummary(
                "vector",
                "scripts/verify-vector-deployment.sh",
                "dep-123",
                "rel-123",
                "ver-123",
                false,
                Map.of("RUNTIME_BASE_URL", "https://runtime.example.com")
            )
        );

        DeploymentHostedVerificationExecutionService service = new DeploymentHostedVerificationExecutionService(
            runRepository,
            contextService,
            platformSecretService,
            new PlatformAuthProperties(true, "X-PLATFORM-API-KEY", false, true, "platform_session", Duration.ofHours(12), true, "Strict", null, null, true, "admin@example.com", "bootstrap-password", null),
            new PlatformHostedVerificationProperties(tempDir.toString(), Duration.ofSeconds(10), 10_000),
            auditService,
            new DeploymentHostedVerificationLogParser()
        );

        service.execute("hvr-123");

        assertThat(run.getStatus()).isEqualTo("PASSED");
        assertThat(run.getExitCode()).isZero();
        assertThat(run.getSummaryMessage()).contains("PASS:");
        assertThat(run.getSummaryMessage()).contains("1 pass");
        assertThat(run.getLogOutput()).contains("== Runner Context ==");
        assertThat(run.getLogOutput()).contains("hosted runner stub passed");
        assertThat(run.getCompletedAt()).isNotNull();
    }

    @Test
    void executeDoesNotInjectDirectConnectorSecretsEvenIfLegacyContextFieldsAppear() throws Exception {
        Path script = tempDir.resolve("verify-ecommerce-deployment.sh");
        Files.writeString(
            script,
            "#!/usr/bin/env bash\n" +
                "set -euo pipefail\n" +
                "[[ \"${VERIFY_WRITE:-}\" == \"true\" ]]\n" +
                "[[ -z \"${API_KEY:-}\" ]]\n" +
                "[[ -z \"${API_KEY_FILE:-}\" ]]\n" +
                "[[ -z \"${RUNTIME_TRUSTED_BACKEND_API_KEY:-}\" ]]\n" +
                "[[ -f \"${RUNTIME_TRUSTED_BACKEND_API_KEY_FILE:-}\" ]]\n" +
                "[[ \"$(cat \"${RUNTIME_TRUSTED_BACKEND_API_KEY_FILE}\")\" == \"runtime-secret\" ]]\n" +
                "[[ -z \"${RUNTIME_ADMIN_API_KEY:-}\" ]]\n" +
                "[[ -f \"${RUNTIME_ADMIN_API_KEY_FILE:-}\" ]]\n" +
                "[[ \"$(cat \"${RUNTIME_ADMIN_API_KEY_FILE}\")\" == \"admin-secret\" ]]\n" +
                "[[ -z \"${CONNECTOR_ADMIN_API_KEY:-}\" ]]\n" +
                "[[ -z \"${CONNECTOR_ADMIN_API_KEY_FILE:-}\" ]]\n" +
                "echo \"PASS: hosted verification stays runtime-only\"\n"
        );

        DeploymentHostedVerificationRunRepository runRepository = mock(DeploymentHostedVerificationRunRepository.class);
        DeploymentHostedVerificationContextService contextService = mock(DeploymentHostedVerificationContextService.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        PlatformAuditService auditService = mock(PlatformAuditService.class);

        DeploymentHostedVerificationRunEntity run = new DeploymentHostedVerificationRunEntity();
        run.setId("hvr-234");
        run.setDeploymentId("dep-234");
        run.setReleaseId("rel-234");
        run.setDeploymentVersionId("ver-234");
        run.setVerificationProfile("ecommerce");
        run.setRunnerType("PLATFORM_HOSTED");
        run.setScriptPath("scripts/verify-ecommerce-deployment.sh");
        run.setStatus("QUEUED");
        run.setVerifyWrite(true);
        run.setSummaryMessage("queued");
        run.setLogOutput("");
        run.setCreatedAt(Instant.now());

        when(runRepository.findById("hvr-234")).thenReturn(Optional.of(run));
        when(runRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(platformSecretService.resolveSecret("AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY")).thenReturn("runtime-secret");
        when(platformSecretService.resolveSecret("APP_ADMIN_API_KEY")).thenReturn("admin-secret");
        when(contextService.buildContextForRun(run)).thenReturn(
            new DeploymentHostedVerificationContextSummary(
                "ecommerce",
                "scripts/verify-ecommerce-deployment.sh",
                "dep-234",
                "rel-234",
                "ver-234",
                true,
                Map.of(
                    "RUNTIME_BASE_URL", "https://runtime.example.com",
                    "REST_CONNECTOR_BASE_URL", "https://connector.example.com"
                )
            )
        );

        DeploymentHostedVerificationExecutionService service = new DeploymentHostedVerificationExecutionService(
            runRepository,
            contextService,
            platformSecretService,
            new PlatformAuthProperties(true, "X-PLATFORM-API-KEY", false, true, "platform_session", Duration.ofHours(12), true, "Strict", null, null, true, "admin@example.com", "bootstrap-password", null),
            new PlatformHostedVerificationProperties(tempDir.toString(), Duration.ofSeconds(10), 10_000),
            auditService,
            new DeploymentHostedVerificationLogParser()
        );

        service.execute("hvr-234");

        assertThat(run.getStatus()).isEqualTo("PASSED");
        assertThat(run.getSummaryMessage()).contains("PASS:");
        assertThat(run.getLogOutput()).contains("hosted verification stays runtime-only");
    }

    @Test
    void executePrefersAdminApiKeyForHostedVerificationAutomation() throws Exception {
        Path script = tempDir.resolve("verify-vector-deployment.sh");
        Files.writeString(
            script,
            "#!/usr/bin/env bash\n" +
                "set -euo pipefail\n" +
                "[[ -z \"${PLATFORM_API_KEY:-}\" ]]\n" +
                "[[ -f \"${PLATFORM_API_KEY_FILE:-}\" ]]\n" +
                "[[ \"$(cat \"${PLATFORM_API_KEY_FILE}\")\" == \"admin-secret\" ]]\n" +
                "echo \"PASS: admin api key selected\"\n"
        );

        DeploymentHostedVerificationRunRepository runRepository = mock(DeploymentHostedVerificationRunRepository.class);
        DeploymentHostedVerificationContextService contextService = mock(DeploymentHostedVerificationContextService.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        PlatformAuditService auditService = mock(PlatformAuditService.class);

        DeploymentHostedVerificationRunEntity run = new DeploymentHostedVerificationRunEntity();
        run.setId("hvr-456");
        run.setDeploymentId("dep-456");
        run.setReleaseId("rel-456");
        run.setDeploymentVersionId("ver-456");
        run.setVerificationProfile("vector");
        run.setRunnerType("PLATFORM_HOSTED");
        run.setScriptPath("scripts/verify-vector-deployment.sh");
        run.setStatus("QUEUED");
        run.setVerifyWrite(false);
        run.setSummaryMessage("queued");
        run.setLogOutput("");
        run.setCreatedAt(Instant.now());

        when(runRepository.findById("hvr-456")).thenReturn(Optional.of(run));
        when(runRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(platformSecretService.resolveSecret("PLATFORM_OPERATOR_API_KEY")).thenReturn("operator-secret");
        when(platformSecretService.resolveSecret("PLATFORM_ADMIN_API_KEY")).thenReturn("admin-secret");
        when(contextService.buildContextForRun(run)).thenReturn(
            new DeploymentHostedVerificationContextSummary(
                "vector",
                "scripts/verify-vector-deployment.sh",
                "dep-456",
                "rel-456",
                "ver-456",
                false,
                Map.of("PLATFORM_BASE_URL", "https://platform.example")
            )
        );

        DeploymentHostedVerificationExecutionService service = new DeploymentHostedVerificationExecutionService(
            runRepository,
            contextService,
            platformSecretService,
            new PlatformAuthProperties(true, "X-PLATFORM-API-KEY", true, false, "platform_session", Duration.ofHours(12), true, "Strict", null, null, false, null, null, null),
            new PlatformHostedVerificationProperties(tempDir.toString(), Duration.ofSeconds(10), 10_000),
            auditService,
            new DeploymentHostedVerificationLogParser()
        );

        service.execute("hvr-456");

        assertThat(run.getStatus()).isEqualTo("PASSED");
        assertThat(run.getSummaryMessage()).contains("PASS:");
        assertThat(run.getLogOutput()).contains("admin api key selected");
        assertThat(run.getLogOutput()).contains("Platform auth mode: platform-api-key");
    }
}
