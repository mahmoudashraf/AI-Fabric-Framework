package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.config.PlatformVerificationProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVerificationRunEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.UUID;

@Service
public class DeploymentReleaseVerificationService {

    private final ObjectMapper objectMapper;
    private final PlatformVerificationProperties verificationProperties;
    private final HttpClient httpClient;

    public DeploymentReleaseVerificationService(ObjectMapper objectMapper,
                                                PlatformVerificationProperties verificationProperties) {
        this.objectMapper = objectMapper;
        this.verificationProperties = verificationProperties;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(verificationProperties.timeout())
            .build();
    }

    public DeploymentVerificationRunEntity verify(DeploymentEntity deployment,
                                                  DeploymentVersionEntity version,
                                                  DeploymentReleaseEntity release,
                                                  String verificationType) {
        Instant now = Instant.now();
        ArrayNode checks = objectMapper.createArrayNode();

        addBooleanCheck(
            checks,
            "active_version_matches_release",
            version.getId().equals(deployment.getActiveVersionId()),
            "Deployment active version matches the applied release version."
        );
        addBooleanCheck(
            checks,
            "runtime_base_url_present",
            hasText(deployment.getRuntimeBaseUrl()),
            "Runtime base URL is populated."
        );
        addBooleanCheck(
            checks,
            "connector_base_url_present",
            hasText(deployment.getConnectorBaseUrl()),
            "Connector base URL is populated."
        );
        addBooleanCheck(
            checks,
            "provisioning_details_present",
            hasText(release.getProvisioningDetailsJson()),
            "Provisioning details were captured for this release."
        );
        addBooleanCheck(
            checks,
            "version_manifest_present",
            hasText(version.getManifestJson()),
            "Compiled manifest exists for the release version."
        );
        verifyLiveEndpoints(checks, deployment, release);

        int passed = 0;
        int failed = 0;
        int skipped = 0;
        for (JsonNode check : checks) {
            String status = check.path("status").asText();
            if ("PASSED".equals(status)) {
                passed += 1;
            } else if ("FAILED".equals(status)) {
                failed += 1;
            } else if ("SKIPPED".equals(status)) {
                skipped += 1;
            }
        }

        DeploymentVerificationRunEntity run = new DeploymentVerificationRunEntity();
        run.setId(generateId("vrf"));
        run.setDeploymentId(deployment.getId());
        run.setReleaseId(release.getId());
        run.setDeploymentVersionId(version.getId());
        run.setVerificationType(verificationType);
        run.setStatus(failed == 0 ? "PASSED" : "FAILED");
        run.setSummaryMessage(passed + " passed, " + failed + " failed, " + skipped + " skipped");
        run.setChecksJson(checks.toPrettyString());
        run.setCreatedAt(now);
        run.setCompletedAt(now);
        return run;
    }

    private void verifyLiveEndpoints(ArrayNode checks,
                                     DeploymentEntity deployment,
                                     DeploymentReleaseEntity release) {
        if ("RAILWAY_STUB".equalsIgnoreCase(release.getProvisioningTarget())) {
            addSkippedCheck(
                checks,
                "runtime_health_http_probe",
                "Live runtime probe skipped because the deployment is still using stub provisioning."
            );
            addSkippedCheck(
                checks,
                "connector_health_http_probe",
                "Live connector probe skipped because the deployment is still using stub provisioning."
            );
            return;
        }

        addHttpProbe(
            checks,
            "runtime_health_http_probe",
            deployment.getRuntimeBaseUrl(),
            verificationProperties.runtimeHealthPath(),
            "Runtime"
        );
        addHttpProbe(
            checks,
            "connector_health_http_probe",
            deployment.getConnectorBaseUrl(),
            verificationProperties.connectorHealthPath(),
            "Connector"
        );
    }

    private void addHttpProbe(ArrayNode checks,
                              String name,
                              String baseUrl,
                              String path,
                              String label) {
        if (!hasText(baseUrl)) {
            addCheck(checks, name, "FAILED", label + " base URL is missing; cannot run live probe.", null);
            return;
        }

        URI uri;
        try {
            uri = buildProbeUri(baseUrl, path);
        } catch (IllegalArgumentException ex) {
            ObjectNode details = objectMapper.createObjectNode();
            details.put("baseUrl", baseUrl);
            details.put("path", path);
            addCheck(checks, name, "FAILED", label + " health URI is invalid: " + ex.getMessage(), details);
            return;
        }

        long startedAt = System.nanoTime();
        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(verificationProperties.timeout())
            .GET()
            .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            ObjectNode details = objectMapper.createObjectNode();
            details.put("url", uri.toString());
            details.put("httpStatus", response.statusCode());
            details.put("durationMs", durationMs);

            boolean healthy = isHealthyResponse(response, details);
            String status = healthy ? "PASSED" : "FAILED";
            String message = healthy
                ? label + " health endpoint responded successfully."
                : label + " health endpoint did not report a healthy state.";
            addCheck(checks, name, status, message, details);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            ObjectNode details = objectMapper.createObjectNode();
            details.put("url", uri.toString());
            addCheck(checks, name, "FAILED", label + " health probe was interrupted.", details);
        } catch (Exception ex) {
            ObjectNode details = objectMapper.createObjectNode();
            details.put("url", uri.toString());
            details.put("error", ex.getClass().getSimpleName());
            addCheck(checks, name, "FAILED", label + " health probe failed: " + ex.getMessage(), details);
        }
    }

    private boolean isHealthyResponse(HttpResponse<String> response, ObjectNode details) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return false;
        }

        String body = response.body();
        if (!hasText(body)) {
            return true;
        }

        try {
            JsonNode payload = objectMapper.readTree(body);
            JsonNode status = payload.path("status");
            if (status.isTextual()) {
                details.put("bodyStatus", status.asText());
                return "UP".equalsIgnoreCase(status.asText());
            }
        } catch (Exception ex) {
            details.put("bodySnippet", abbreviate(body));
        }

        return true;
    }

    private URI buildProbeUri(String baseUrl, String path) {
        String normalizedBaseUrl = baseUrl.endsWith("/")
            ? baseUrl.substring(0, baseUrl.length() - 1)
            : baseUrl;
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return URI.create(normalizedBaseUrl + normalizedPath);
    }

    private void addBooleanCheck(ArrayNode checks, String name, boolean passed, String message) {
        addCheck(checks, name, passed ? "PASSED" : "FAILED", message, null);
    }

    private void addSkippedCheck(ArrayNode checks, String name, String message) {
        addCheck(checks, name, "SKIPPED", message, null);
    }

    private void addCheck(ArrayNode checks,
                          String name,
                          String status,
                          String message,
                          ObjectNode details) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("name", name);
        node.put("status", status);
        node.put("message", message);
        if (details != null && !details.isEmpty()) {
            node.set("details", details);
        }
        checks.add(node);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String abbreviate(String value) {
        if (!hasText(value)) {
            return "";
        }
        return value.length() <= 160 ? value : value.substring(0, 157) + "...";
    }

    private String generateId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
