package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.config.PlatformDeliveryProperties;
import com.ai.fabric.platform.backend.config.PlatformProvisioningProperties;
import com.ai.fabric.platform.backend.deployment.model.RailwayPreflightCheckSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayPreflightSummary;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class RailwayPreflightService {

    private final PlatformProvisioningProperties provisioningProperties;
    private final PlatformDeliveryProperties deliveryProperties;
    private final RailwayGraphqlClient railwayGraphqlClient;
    private final PlatformSecretService platformSecretService;
    private final HttpClient httpClient;

    public RailwayPreflightService(PlatformProvisioningProperties provisioningProperties,
                                   PlatformDeliveryProperties deliveryProperties,
                                   RailwayGraphqlClient railwayGraphqlClient,
                                   PlatformSecretService platformSecretService) {
        this(
            provisioningProperties,
            deliveryProperties,
            railwayGraphqlClient,
            platformSecretService,
            HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build()
        );
    }

    RailwayPreflightService(PlatformProvisioningProperties provisioningProperties,
                            PlatformDeliveryProperties deliveryProperties,
                            RailwayGraphqlClient railwayGraphqlClient,
                            PlatformSecretService platformSecretService,
                            HttpClient httpClient) {
        this.provisioningProperties = provisioningProperties;
        this.deliveryProperties = deliveryProperties;
        this.railwayGraphqlClient = railwayGraphqlClient;
        this.platformSecretService = platformSecretService;
        this.httpClient = httpClient;
    }

    public RailwayPreflightSummary run() {
        List<RailwayPreflightCheckSummary> checks = new ArrayList<>();

        checkProvisioningMode(checks);
        checkRepository(checks);
        checkSecrets(checks);
        checkPublicBaseUrl(checks);

        String workspaceName = null;
        boolean canCheckRailway = checkRailwayTokenAndWorkspaceConfig(checks);
        if (canCheckRailway) {
            workspaceName = checkRailwayWorkspaceAccess(checks);
        }

        boolean ready = checks.stream().noneMatch(check -> "FAILED".equals(check.status()));
        return new RailwayPreflightSummary(
            provisioningProperties.mode(),
            ready,
            Instant.now().toString(),
            deliveryProperties.publicBaseUrl(),
            blankToNull(provisioningProperties.workspaceId()),
            workspaceName,
            provisioningProperties.repository(),
            provisioningProperties.branch(),
            List.copyOf(checks)
        );
    }

    private void checkProvisioningMode(List<RailwayPreflightCheckSummary> checks) {
        if ("RAILWAY_API".equalsIgnoreCase(provisioningProperties.mode())) {
            checks.add(pass(
                "provisioning_mode",
                "Platform is configured for live Railway API provisioning.",
                provisioningProperties.mode()
            ));
            return;
        }
        checks.add(fail(
            "provisioning_mode",
            "Platform provisioning mode is not RAILWAY_API, so live Railway apply is disabled.",
            provisioningProperties.mode()
        ));
    }

    private void checkRepository(List<RailwayPreflightCheckSummary> checks) {
        String repository = provisioningProperties.repository();
        if (repository != null && repository.contains("/")) {
            checks.add(pass(
                "deployment_repository",
                "Deployment repository is a valid GitHub slug.",
                repository + "@" + provisioningProperties.branch()
            ));
            return;
        }
        checks.add(fail(
            "deployment_repository",
            "PLATFORM_DEPLOY_REPOSITORY must be set to a GitHub slug like owner/repo.",
            repository
        ));
    }

    private void checkSecrets(List<RailwayPreflightCheckSummary> checks) {
        List<String> requiredSecrets = deliveryProperties.signedArtifactsEnabled()
            ? List.of("PLATFORM_ARTIFACT_SIGNING_KEY")
            : List.of();
        if (requiredSecrets.isEmpty()) {
            checks.add(pass(
                "platform_secrets",
                "No platform-operational secrets are required for the current artifact delivery mode.",
                "unsigned-artifacts"
            ));
            return;
        }
        List<String> missing = requiredSecrets.stream()
            .filter(name -> !platformSecretService.isSecretPresent(name))
            .toList();
        if (missing.isEmpty()) {
            checks.add(pass(
                "platform_secrets",
                "Required platform secrets are available for Railway provisioning.",
                String.join(", ", requiredSecrets)
            ));
            return;
        }
        checks.add(fail(
            "platform_secrets",
            "Some required platform secrets are missing.",
            String.join(", ", missing)
        ));
    }

    private void checkPublicBaseUrl(List<RailwayPreflightCheckSummary> checks) {
        String value = deliveryProperties.publicBaseUrl();
        if (value == null || value.isBlank()) {
            checks.add(fail(
                "public_base_url",
                "PLATFORM_PUBLIC_BASE_URL is blank, so Railway services will not be able to fetch config artifacts.",
                null
            ));
            return;
        }

        URI uri;
        try {
            uri = URI.create(value);
        } catch (Exception ex) {
            checks.add(fail(
                "public_base_url",
                "PLATFORM_PUBLIC_BASE_URL is not a valid URI.",
                value
            ));
            return;
        }

        String host = uri.getHost();
        if (isLocalHost(host)) {
            checks.add(fail(
                "public_base_url",
                "PLATFORM_PUBLIC_BASE_URL points to localhost/private host, which Railway services cannot reach.",
                value
            ));
            return;
        }

        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            checks.add(warn(
                "public_base_url",
                "PLATFORM_PUBLIC_BASE_URL is public but not HTTPS. Railway can still reach it if exposed, but HTTPS is preferred.",
                value
            ));
        }

        String probeTarget = value + "/api/platform/overview";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(probeTarget))
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                checks.add(pass(
                    "public_base_url_probe",
                    "Platform public base URL responded successfully to an overview probe.",
                    probeTarget
                ));
            } else if (response.statusCode() == 401 || response.statusCode() == 403) {
                checks.add(pass(
                    "public_base_url_probe",
                    "Platform public base URL is reachable and overview access is protected by authentication.",
                    probeTarget + " -> HTTP " + response.statusCode()
                ));
            } else {
                checks.add(warn(
                    "public_base_url_probe",
                    "Platform public base URL did not return a success status for the overview probe.",
                    probeTarget + " -> HTTP " + response.statusCode()
                ));
            }
        } catch (Exception ex) {
            checks.add(warn(
                "public_base_url_probe",
                "Platform public base URL could not be probed from the control plane runtime.",
                probeTarget + " -> " + ex.getClass().getSimpleName()
            ));
        }
    }

    private boolean checkRailwayTokenAndWorkspaceConfig(List<RailwayPreflightCheckSummary> checks) {
        boolean ready = true;
        if (provisioningProperties.railwayApiToken().isBlank()) {
            checks.add(fail(
                "railway_api_token",
                "RAILWAY_API_TOKEN is missing.",
                null
            ));
            ready = false;
        } else {
            checks.add(pass(
                "railway_api_token",
                "Railway API token is configured.",
                "configured"
            ));
        }

        if (provisioningProperties.workspaceId().isBlank()) {
            checks.add(fail(
                "railway_workspace_id",
                "RAILWAY_WORKSPACE_ID is missing.",
                null
            ));
            ready = false;
        }
        return ready;
    }

    private String checkRailwayWorkspaceAccess(List<RailwayPreflightCheckSummary> checks) {
        try {
            List<RailwayGraphqlClient.RailwayWorkspaceSummary> workspaces = railwayGraphqlClient.listAccessibleWorkspaces();
            RailwayGraphqlClient.RailwayWorkspaceSummary workspace = workspaces.stream()
                .filter(item -> provisioningProperties.workspaceId().equals(item.id()))
                .findFirst()
                .orElse(null);
            if (workspace == null) {
                checks.add(fail(
                    "railway_workspace_access",
                    "Configured Railway workspace was not returned by the current API token.",
                    provisioningProperties.workspaceId()
                ));
                return null;
            }
            checks.add(pass(
                "railway_workspace_access",
                "Railway API token can access the configured workspace.",
                workspace.name() + " (" + workspace.id() + ")"
            ));
            return workspace.name();
        } catch (Exception ex) {
            checks.add(fail(
                "railway_workspace_access",
                "Failed to query Railway API using the configured token.",
                ex.getMessage()
            ));
            return null;
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private boolean isLocalHost(String host) {
        if (host == null || host.isBlank()) {
            return true;
        }
        String normalized = host.trim().toLowerCase();
        if ("localhost".equals(normalized)
            || "127.0.0.1".equals(normalized)
            || "0.0.0.0".equals(normalized)
            || "::1".equals(normalized)
            || normalized.endsWith(".local")) {
            return true;
        }
        try {
            InetAddress address = InetAddress.getByName(host);
            return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isSiteLocalAddress();
        } catch (Exception ignored) {
            return false;
        }
    }

    private RailwayPreflightCheckSummary pass(String key, String message, String details) {
        return new RailwayPreflightCheckSummary(key, "PASSED", message, details);
    }

    private RailwayPreflightCheckSummary warn(String key, String message, String details) {
        return new RailwayPreflightCheckSummary(key, "WARNING", message, details);
    }

    private RailwayPreflightCheckSummary fail(String key, String message, String details) {
        return new RailwayPreflightCheckSummary(key, "FAILED", message, details);
    }
}
