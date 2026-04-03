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

    @Test
    void parseTreatsTracebackAssertionAsFailure() {
        var diagnostics = parser.parse("""
            == Runtime Proxy Checks (via REST connector if enabled) ==
            Traceback (most recent call last):
              File "<stdin>", line 11, in <module>
              File "<string>", line 3, in <module>
            AssertionError
            """);

        assertThat(diagnostics.failCount()).isEqualTo(1);
        assertThat(diagnostics.lastFailureMessage()).isEqualTo("AssertionError");
        assertThat(diagnostics.headline()).contains("Failed");
        assertThat(diagnostics.steps())
            .extracting(step -> step.message())
            .contains("AssertionError");
    }

    @Test
    void summarizeFallsBackToTracebackFailureWhenNoFailMarkerExists() {
        String summary = parser.summarize(
            "FAILED",
            """
                == Runtime Proxy Checks ==
                Traceback (most recent call last):
                  File "<stdin>", line 11, in <module>
                AssertionError
                """,
            1
        );

        assertThat(summary).contains("FAIL: AssertionError");
    }
}
