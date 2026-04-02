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
            new PlatformAuthProperties(false, "X-PLATFORM-API-KEY", false, true, "platform_session", Duration.ofHours(12), true, "Strict", null, null, false, null, null, null),
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
}
