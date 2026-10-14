package com.ai.fabric.platform.backend.deployment.authority;

import com.ai.fabric.platform.backend.deployment.service.DeploymentExecutionSecretService;

import java.util.List;

/** Reviewed secret slots, not arbitrary environment overrides or provider/source credentials. */
public enum RuntimeAuthorityPurpose {
    TRUSTED_BACKEND_API_KEY(null, List.of("AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY"),
        List.of("REST_CONNECTOR_RUNTIME_PROXY_API_KEY")),
    PRIVATE_ASSERTION_SIGNING_KEY(null, List.of("AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY"), List.of()),
    PUBLIC_TOKEN_SIGNING_KEY(null, List.of("AI_FABRIC_RUNTIME_PUBLIC_TOKEN_SIGNING_KEY"), List.of()),
    CONNECTOR_ADMIN_API_KEY("CONNECTOR_ADMIN_API_KEY", List.of("AI_ACTIONS_CONNECTOR_ADMIN_API_KEY"), List.of("APP_ADMIN_API_KEY")),
    CONNECTOR_INVOCATION_API_KEY("CONNECTOR_INVOCATION_API_KEY", List.of("ACTIONS_CONNECTOR_API_KEY"), List.of("CONNECTOR_API_KEY")),
    SPECIALIST_CHAIN_ENCRYPTION("SPECIALIST_CHAIN_ENCRYPTION", List.of("AI_SPECIALIST_CHAIN_ENCRYPTION_SECRET"), List.of()),
    SPECIALIST_CHAIN_FINGERPRINT("SPECIALIST_CHAIN_FINGERPRINT", List.of("AI_SPECIALIST_CHAIN_FINGERPRINT_SECRET"), List.of()),
    SMART_BRAIN_JOB_ENCRYPTION("SMART_BRAIN_JOB_ENCRYPTION", List.of("AI_EXECUTION_ASYNC_ENCRYPTION_SECRET"), List.of()),
    SMART_BRAIN_JOB_FINGERPRINT("SMART_BRAIN_JOB_FINGERPRINT", List.of("AI_EXECUTION_ASYNC_FINGERPRINT_SECRET"), List.of()),
    SMART_BRAIN_ENCRYPTION("SMART_BRAIN_ENCRYPTION", List.of("LOOMAI_SMART_BRAIN_ENCRYPTION_SECRET"), List.of()),
    SMART_BRAIN_FINGERPRINT("SMART_BRAIN_FINGERPRINT", List.of("LOOMAI_SMART_BRAIN_FINGERPRINT_SECRET"), List.of()),
    SMART_BRAIN_DELIVERY_SIGNING("SMART_BRAIN_DELIVERY_SIGNING", List.of("LOOMAI_SMART_BRAIN_DELIVERY_SIGNING_SECRET"), List.of()),
    ACTION_RECEIPT_ENCRYPTION("ACTION_RECEIPT_ENCRYPTION", List.of("AI_ACTION_RECEIPT_ENCRYPTION_SECRET"), List.of()),
    ACTION_RECEIPT_FINGERPRINT("ACTION_RECEIPT_FINGERPRINT", List.of("AI_ACTION_RECEIPT_FINGERPRINT_SECRET"), List.of()),
    REVIEW_ENCRYPTION("REVIEW_ENCRYPTION", List.of("AI_REVIEW_ENCRYPTION_SECRET"), List.of()),
    REVIEW_FINGERPRINT("REVIEW_FINGERPRINT", List.of("AI_REVIEW_FINGERPRINT_SECRET"), List.of()),
    CONVERSATION_HANDOVER_ENCRYPTION("CONVERSATION_HANDOVER_ENCRYPTION", List.of("LOOMAI_CONVERSATION_HANDOVER_ENCRYPTION_SECRET"), List.of()),
    CONVERSATION_HANDOVER_CALLBACK_SIGNING(null, List.of("LOOMAI_CONVERSATION_HANDOVER_CALLBACK_SIGNING_SECRET"),
        List.of("REST_CONNECTOR_CONVERSATION_CHANNEL_RUNTIME_CALLBACK_SIGNING_SECRET")),
    INTEGRATION_SERVICE_API_KEY("INTEGRATION_SERVICE_API_KEY", List.of("AI_FABRIC_RUNTIME_AUTH_INGRESS_INTEGRATION_SERVICE_API_KEY_VALUE"),
        List.of("AI_FABRIC_RUNTIME_INTEGRATION_SERVICE_API_KEY", "REST_CONNECTOR_CONVERSATION_CHANNEL_RUNTIME_API_KEY")),
    INTEGRATION_CONNECTOR_DATABASE_PASSWORD("INTEGRATION_CONNECTOR_DATABASE_PASSWORD", List.of(), List.of("REST_CONNECTOR_JDBC_PASSWORD"));

    private final String legacyPurpose;
    private final List<String> runtimeEnvironmentNames;
    private final List<String> connectorEnvironmentNames;

    RuntimeAuthorityPurpose(String legacyPurpose, List<String> runtimeNames, List<String> connectorNames) {
        this.legacyPurpose = legacyPurpose;
        this.runtimeEnvironmentNames = runtimeNames;
        this.connectorEnvironmentNames = connectorNames;
    }

    public List<String> runtimeEnvironmentNames() { return runtimeEnvironmentNames; }
    public List<String> connectorEnvironmentNames() { return connectorEnvironmentNames; }

    String legacyName(String deploymentId) {
        if (legacyPurpose == null) return null;
        // Use the established purpose API to discover required composition slots only.
        return DeploymentExecutionSecretService.deploymentManagedSecretNames(deploymentId).stream()
            .filter(name -> name.startsWith("MANAGED_" + legacyPurpose + "_SECRET_DEP_"))
            .findFirst().orElseThrow();
    }
}
