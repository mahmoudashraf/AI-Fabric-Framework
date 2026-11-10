package com.ai.fabric.platform.backend.secret.model;

import java.util.List;

/** Write-only credential inputs and value-free customer summaries. */
public final class DeploymentSecretContracts {
    private DeploymentSecretContracts() { }

    public record Create(String name, String purpose, String value, String secondaryValue) { }
    public record Rotate(String value) { }
    public record Purpose(String id, String label, boolean paired) { }
    public record Secret(String name, String label, String purpose, boolean present,
                         int bindingCount, String updatedAt) { }
    public record Catalogue(String deploymentId, List<Purpose> purposes, List<Secret> secrets) { }
}
