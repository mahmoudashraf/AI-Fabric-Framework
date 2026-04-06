package com.ai.fabric.platform.backend.secret.model;

public record UpsertDeploymentProviderSecretBindingRequest(
    String secretPurpose,
    String bindingMode,
    String secretName,
    String secondarySecretName
) {
}
