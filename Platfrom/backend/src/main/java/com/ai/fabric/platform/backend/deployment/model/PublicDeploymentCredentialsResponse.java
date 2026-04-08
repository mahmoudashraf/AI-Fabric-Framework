package com.ai.fabric.platform.backend.deployment.model;

/**
 * Public provisioning credentials response.
 *
 * <p>The connector remains internal-only. Public consumers should use the
 * integration summary to determine whether runtime access is backend-mediated
 * or browser-direct, and should treat {@code connectorBaseUrl} as withheld.</p>
 */
public record PublicDeploymentCredentialsResponse(
    String clientId,
    String externalDeploymentKey,
    String deploymentId,
    String runtimeBaseUrl,
    String connectorBaseUrl,
    PublicDeploymentAccessSummary access,
    PublicDeploymentIntegrationSummary integration
) {
}
