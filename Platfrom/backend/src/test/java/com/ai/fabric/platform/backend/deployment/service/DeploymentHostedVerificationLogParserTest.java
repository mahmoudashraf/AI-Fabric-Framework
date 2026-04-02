package com.ai.fabric.platform.backend.deployment.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeploymentHostedVerificationLogParserTest {

    private final DeploymentHostedVerificationLogParser parser = new DeploymentHostedVerificationLogParser();

    @Test
    void parseCapturesStructuredMarkersAndHeadline() {
        var diagnostics = parser.parse("""
            == Runner Context ==
            Deployment: dep-123

            == Platform Health ==
            PASS: platform /actuator/health
            WARN: platform overview is protected and required session login
            FAIL: platform overview (expected HTTP 200)
            """);

        assertThat(diagnostics.passCount()).isEqualTo(1);
        assertThat(diagnostics.warningCount()).isEqualTo(1);
        assertThat(diagnostics.failCount()).isEqualTo(1);
        assertThat(diagnostics.lastFailureMessage()).isEqualTo("platform overview (expected HTTP 200)");
        assertThat(diagnostics.headline()).contains("Failed at platform overview");
        assertThat(diagnostics.steps()).hasSize(3);
        assertThat(diagnostics.steps().get(0).section()).isEqualTo("Platform Health");
    }

    @Test
    void summarizePrefersStructuredFailureAndCounters() {
        String summary = parser.summarize(
            "FAILED",
            """
                == Checks ==
                PASS: health
                FAIL: platform overview (expected HTTP 200)
                """,
            1
        );

        assertThat(summary).contains("FAIL: platform overview (expected HTTP 200)");
        assertThat(summary).contains("1 pass");
    }
}
