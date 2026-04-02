package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.model.DeploymentHostedVerificationDiagnosticsSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentHostedVerificationStepSummary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class DeploymentHostedVerificationLogParser {

    private static final Pattern EXCEPTION_LINE =
        Pattern.compile("^(?:Caused by:\\s+)?[A-Za-z_$][A-Za-z0-9_.$]*(?:Error|Exception)(?::.*)?$");

    public DeploymentHostedVerificationDiagnosticsSummary parse(String output) {
        List<DeploymentHostedVerificationStepSummary> steps = new ArrayList<>();
        String currentSection = null;
        String lastPassMessage = null;
        String lastWarningMessage = null;
        String lastFailureMessage = null;
        int passCount = 0;
        int warningCount = 0;
        int failCount = 0;
        boolean tracebackSeen = false;

        for (String line : normalizeOutput(output).split("\\R")) {
            String normalized = line == null ? "" : line.trim();
            if (normalized.isEmpty()) {
                continue;
            }
            if (normalized.startsWith("==") && normalized.endsWith("==") && normalized.length() > 4) {
                currentSection = normalized.substring(2, normalized.length() - 2).trim();
                continue;
            }
            if (normalized.startsWith("PASS:")) {
                String message = normalized.substring("PASS:".length()).trim();
                passCount += 1;
                lastPassMessage = message;
                steps.add(new DeploymentHostedVerificationStepSummary("PASSED", currentSection, message, normalized));
                continue;
            }
            if (normalized.startsWith("WARN:") || normalized.startsWith("WARNING:")) {
                String prefix = normalized.startsWith("WARN:") ? "WARN:" : "WARNING:";
                String message = normalized.substring(prefix.length()).trim();
                warningCount += 1;
                lastWarningMessage = message;
                steps.add(new DeploymentHostedVerificationStepSummary("WARNING", currentSection, message, normalized));
                continue;
            }
            if (normalized.startsWith("FAIL:")) {
                String message = normalized.substring("FAIL:".length()).trim();
                failCount += 1;
                lastFailureMessage = message;
                steps.add(new DeploymentHostedVerificationStepSummary("FAILED", currentSection, message, normalized));
                tracebackSeen = false;
                continue;
            }
            if (normalized.startsWith("Traceback (most recent call last):")) {
                tracebackSeen = true;
                continue;
            }
            if (tracebackSeen && looksLikeExceptionLine(normalized)) {
                failCount += 1;
                lastFailureMessage = normalized;
                steps.add(new DeploymentHostedVerificationStepSummary("FAILED", currentSection, normalized, normalized));
                tracebackSeen = false;
                continue;
            }
            if (looksLikeExceptionLine(normalized)) {
                failCount += 1;
                lastFailureMessage = normalized;
                steps.add(new DeploymentHostedVerificationStepSummary("FAILED", currentSection, normalized, normalized));
                tracebackSeen = false;
            }
        }

        return new DeploymentHostedVerificationDiagnosticsSummary(
            buildHeadline(passCount, warningCount, failCount, lastPassMessage, lastWarningMessage, lastFailureMessage),
            passCount,
            warningCount,
            failCount,
            lastPassMessage,
            lastWarningMessage,
            lastFailureMessage,
            steps
        );
    }

    public String summarize(String status, String output, int exitCode) {
        DeploymentHostedVerificationDiagnosticsSummary diagnostics = parse(output);
        if ("PASSED".equalsIgnoreCase(status) && diagnostics.lastPassMessage() != null) {
            return formatSummaryLine("PASS", diagnostics.lastPassMessage(), diagnostics.passCount(), diagnostics.warningCount());
        }
        if ("FAILED".equalsIgnoreCase(status) && diagnostics.lastFailureMessage() != null) {
            return formatSummaryLine("FAIL", diagnostics.lastFailureMessage(), diagnostics.passCount(), diagnostics.warningCount());
        }
        if ("TIMED_OUT".equalsIgnoreCase(status)) {
            return "FAIL: Hosted verification timed out before completion.";
        }
        return "Hosted verification " + status.toLowerCase(Locale.ROOT) + " with exit code " + exitCode + ".";
    }

    private String buildHeadline(int passCount,
                                 int warningCount,
                                 int failCount,
                                 String lastPassMessage,
                                 String lastWarningMessage,
                                 String lastFailureMessage) {
        if (failCount > 0 && lastFailureMessage != null) {
            return "Failed at " + lastFailureMessage + ".";
        }
        if (warningCount > 0 && lastWarningMessage != null) {
            return "Completed with warnings. Latest warning: " + lastWarningMessage + ".";
        }
        if (passCount > 0 && lastPassMessage != null) {
            return "Passed. Latest checkpoint: " + lastPassMessage + ".";
        }
        return "No structured PASS/WARN/FAIL markers were recorded.";
    }

    private String formatSummaryLine(String prefix,
                                     String message,
                                     int passCount,
                                     int warningCount) {
        StringBuilder builder = new StringBuilder(prefix).append(": ").append(message);
        List<String> counters = new ArrayList<>();
        if (passCount > 0) {
            counters.add(passCount + " pass" + (passCount == 1 ? "" : "es"));
        }
        if (warningCount > 0) {
            counters.add(warningCount + " warning" + (warningCount == 1 ? "" : "s"));
        }
        if (!counters.isEmpty()) {
            builder.append(" (").append(String.join(", ", counters)).append(")");
        }
        return builder.toString();
    }

    private String normalizeOutput(String output) {
        return output == null ? "" : output;
    }

    private boolean looksLikeExceptionLine(String normalized) {
        if (normalized == null || normalized.isBlank()) {
            return false;
        }
        if ("AssertionError".equals(normalized) || normalized.startsWith("AssertionError:")) {
            return true;
        }
        return EXCEPTION_LINE.matcher(normalized).matches();
    }
}
