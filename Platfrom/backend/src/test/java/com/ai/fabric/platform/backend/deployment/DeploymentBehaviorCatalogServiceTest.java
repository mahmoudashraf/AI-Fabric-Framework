package com.ai.fabric.platform.backend.deployment;

import com.ai.fabric.platform.backend.deployment.behavior.DeploymentBehaviorCatalogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeploymentBehaviorCatalogServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final DeploymentBehaviorCatalogService service = new DeploymentBehaviorCatalogService(mapper);

    @Test
    void conversationHandoverRequiresCanonicalOrchestrationState() {
        var requirements = service.releaseRequirements(handoverConfig());

        assertThat(requirements.capabilities()).contains(
            "conversation-control",
            "canonical-conversation-orchestration-state"
        );
        assertThat(requirements.migrationIds()).contains(
            "loomai-conversation-handover-v1",
            "loomai-conversation-orchestration-state-v1"
        );
    }

    @Test
    void acceptsReviewedManagedBindingReferenceBeforeTargetProvisioningResolvesIt() {
        ObjectNode behavior = handoverConfig();
        ObjectNode handover = behavior.putObject("conversationHandover");
        handover.put("contractVersion", "LOOMAI_CONVERSATION_HANDOVER_CONFIG_V1");
        handover.put("enabled", true);
        handover.put("actionId", "request_conversation_handover");
        handover.put("channelBindingId", "${MANAGED_INBOX_BINDING_ID}");
        ObjectNode action = handover.putObject("actionBinding");
        action.put("actionId", "request_conversation_handover");
        action.put("operation", "REQUEST_HANDOVER");
        action.put("implementation", "SERVER_OWNED_RUNTIME_ACTION");
        action.put("marketplaceManaged", true);
        action.put("marketplacePluginId", "mkp-action-conversation-handover");
        action.put("marketplaceInstallId", "install-action");
        action.put("marketplacePluginVersion", "1.0.0");
        handover.put("modelInitiatedPolicy", "REQUIRE_USER_CONFIRMATION");
        handover.put("userCanCancelPending", true);
        handover.put("userCanResumeAi", true);
        handover.put("activeIdleAutoResume", false);
        handover.put("queueTimeoutOutcome", "EXPIRED_REQUIRES_EXPLICIT_RECOVERY");
        handover.put("transcriptProjection", "SAFE_TEXT_AND_ATTACHMENT_REFS");
        handover.put("queueTimeoutSeconds", 300);
        handover.putObject("intake").putArray("fields");
        ObjectNode channel = handover.putObject("channel");
        channel.put("adapterImplementationId", "basic-loomai-inbox");
        channel.put("adapterVersion", "1.1.0");
        channel.put("bindingId", "${MANAGED_INBOX_BINDING_ID}");
        channel.put("providerBaseUrl", "${MANAGED_INBOX_BASE_URL}");
        channel.put("providerApiKeySecretRef", "MANAGED_INBOX_API_KEY");
        channel.put("runtimeCallbackSigningSecretRef", "MANAGED_INBOX_CALLBACK_SIGNING_SECRET");
        channel.put("providerWebhookSigningSecretRef", "MANAGED_INBOX_PROVIDER_WEBHOOK_SIGNING_SECRET");

        assertThat(service.validate(behavior, "CONVERSATIONAL").valid()).isTrue();
    }

    private ObjectNode handoverConfig() {
        ObjectNode behavior = (ObjectNode) service.defaultConfig("CONVERSATIONAL");
        behavior.withArray("executionExtensions").add("CONVERSATION_HANDOVER");
        return behavior;
    }

    @Test
    void rejectsUnreviewedBindingPlaceholders() {
        ObjectNode behavior = (ObjectNode) service.defaultConfig("CONVERSATIONAL");
        behavior.withArray("executionExtensions").add("CONVERSATION_HANDOVER");
        behavior.putObject("conversationHandover")
            .put("contractVersion", "LOOMAI_CONVERSATION_HANDOVER_CONFIG_V1")
            .put("enabled", true)
            .put("actionId", "request_conversation_handover")
            .put("channelBindingId", "${customer_binding}");

        var validation = service.validate(behavior, "CONVERSATIONAL");
        assertThat(validation.valid()).isFalse();
        assertThat(validation.code()).isEqualTo("CONVERSATION_HANDOVER_CONFIG_INVALID");
    }
}
