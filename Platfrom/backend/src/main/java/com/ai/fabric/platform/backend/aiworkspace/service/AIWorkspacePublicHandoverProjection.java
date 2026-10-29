package com.ai.fabric.platform.backend.aiworkspace.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Shared browser-safe handover contract; never projects channel, action binding or credentials. */
final class AIWorkspacePublicHandoverProjection {
    private AIWorkspacePublicHandoverProjection() { }

    static ObjectNode project(ObjectMapper objectMapper, JsonNode behaviorConfiguration) {
        ObjectNode result = objectMapper.createObjectNode();
        JsonNode configured = behaviorConfiguration.path("conversationHandover");
        boolean extensionSelected = false;
        JsonNode extensions = behaviorConfiguration.path("executionExtensions");
        if (extensions.isArray()) {
            for (JsonNode extension : extensions) {
                if ("CONVERSATION_HANDOVER".equals(extension.asText(""))) {
                    extensionSelected = true;
                    break;
                }
            }
        }
        boolean enabled = extensionSelected && configured.path("enabled").asBoolean(false);
        result.put("enabled", enabled);
        if (!enabled) {
            return result;
        }
        result.put("contractVersion", configured.path("contractVersion").asText(""));
        result.put("modelInitiatedPolicy", configured.path("modelInitiatedPolicy").asText(""));
        result.put("userCanCancelPending", configured.path("userCanCancelPending").asBoolean(false));
        result.put("userCanResumeAi", configured.path("userCanResumeAi").asBoolean(false));
        result.put("queueTimeoutSeconds", configured.path("queueTimeoutSeconds").asInt(0));
        JsonNode intake = configured.path("intake");
        ObjectNode publicIntake = result.putObject("intake");
        publicIntake.put("anonymousAllowed", intake.path("anonymousAllowed").asBoolean(true));
        publicIntake.set(
            "fields",
            intake.path("fields").isArray()
                ? intake.path("fields").deepCopy()
                : objectMapper.createArrayNode()
        );
        return result;
    }
}
