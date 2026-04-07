package com.ai.fabric.platform.backend.deployment.model;

/**
 * Public provisioning credentials response.
 *
 * <p>The connector remains internal-only. Public consumers should use the
 * integration summary to determine whether runtime access is backend-mediated
 * or browser-direct. Customer-facing business CRUD routes such as cart or
 * order APIs are not implied by this response unless an explicit
 * runtime-backed CRUD surface is published later.</p>
 */
public record PublicDeploymentCredentialsResponse(
    String clientId,
    String externalDeploymentKey,
    String deploymentId,
    String runtimeBaseUrl,
    PublicDeploymentAccessSummary access,
    PublicDeploymentIntegrationSummary integration
) {
}
