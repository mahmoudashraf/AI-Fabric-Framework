package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.config.PlatformHostedVerificationProperties;
import com.ai.fabric.platform.backend.config.PlatformVerificationSuiteProperties;
import com.ai.fabric.platform.backend.deployment.model.PlatformVerificationScriptContextSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformVerificationScriptRunnerServiceTest {

    @TempDir
    Path scriptRoot;

    @Test
    void passesManagedPartnerJwtAsSecretFile() throws Exception {
        Path script = scriptRoot.resolve("assert-partner-secret-file.sh");
        Files.writeString(script, """
            #!/usr/bin/env bash
            set -euo pipefail
            if [[ -n "${PARTNER_SUPABASE_JWT:-}" ]]; then
              echo "direct partner JWT env must not be present"
              exit 1
            fi
            if [[ -z "${PARTNER_SUPABASE_JWT_FILE:-}" ]]; then
              echo "partner JWT file env is missing"
              exit 1
            fi
            if [[ "$(cat "${PARTNER_SUPABASE_JWT_FILE}")" != "partner-jwt" ]]; then
              echo "partner JWT file value mismatch"
              exit 1
            fi
            echo "PASS: partner JWT secret file present"
            """);

        PlatformVerificationScriptRunnerService service = new PlatformVerificationScriptRunnerService(
            new PlatformHostedVerificationProperties(scriptRoot.toString(), Duration.ofSeconds(10), 12_000),
            new PlatformVerificationSuiteProperties(
                Duration.ofMinutes(60),
                Duration.ofMinutes(12),
                Duration.ofSeconds(10),
                Duration.ofMinutes(75),
                Duration.ofHours(12),
                Duration.ofSeconds(1),
                20,
                12_000,
                80_000,
                "https://platform-ui.example.test",
                "weaviate.example.test",
                "https://bridge.example.test",
                "shop.example.test",
                "shopify-bridge-prod",
                null,
                "https://partner-ui.example.test"
            )
        );

        PlatformVerificationScriptRunnerService.ScriptRunResult result = service.run(
            new PlatformVerificationScriptContextSummary(
                script.getFileName().toString(),
                Map.of(),
                Map.of("PARTNER_SUPABASE_JWT", "partner-jwt")
            )
        );

        assertThat(result.status()).isEqualTo("PASSED");
        assertThat(result.output()).contains("PASS: partner JWT secret file present");
    }
}
