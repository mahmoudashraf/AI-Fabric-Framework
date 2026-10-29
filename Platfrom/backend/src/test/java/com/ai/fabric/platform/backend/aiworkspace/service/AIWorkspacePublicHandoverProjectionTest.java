package com.ai.fabric.platform.backend.aiworkspace.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class AIWorkspacePublicHandoverProjectionTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void sharedPublicProjectionPreservesIntakeAndControlPolicyWithoutPrivateBindings() throws Exception {
        var behavior = mapper.readTree("""
            {"executionExtensions":["CONVERSATION_HANDOVER"],"secret":"private-root",
             "conversationHandover":{"enabled":true,"contractVersion":"v1","modelInitiatedPolicy":"REQUIRE_USER_CONFIRMATION",
               "userCanCancelPending":true,"userCanResumeAi":false,"queueTimeoutSeconds":900,
               "intake":{"anonymousAllowed":false,"credentials":"private-intake",
                 "fields":[{"id":"email","label":"Email","type":"email","required":true,"maxLength":254,
                   "purpose":"Follow-up","consentRequired":true}]},
               "channel":{"token":"private-channel"},"actionBinding":{"id":"private-action"}}}
            """);
        var before = behavior.deepCopy();
        var projected = AIWorkspacePublicHandoverProjection.project(mapper, behavior);
        assertThat(projected.path("enabled").asBoolean()).isTrue();
        assertThat(projected.path("userCanCancelPending").asBoolean()).isTrue();
        assertThat(projected.path("userCanResumeAi").asBoolean()).isFalse();
        assertThat(projected.path("intake").path("anonymousAllowed").asBoolean()).isFalse();
        assertThat(projected.path("intake").path("fields")).isEqualTo(behavior.path("conversationHandover").path("intake").path("fields"));
        assertThat(projected.toString()).doesNotContain("private-", "channel", "actionBinding", "credentials");
        projected.withObject("/intake").withArray("fields").removeAll();
        assertThat(behavior).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"conversationHandover\":{\"enabled\":true}}",
        "{\"executionExtensions\":[\"CONVERSATION_HANDOVER\"],\"conversationHandover\":{\"enabled\":false}}"})
    void enabledRequiresBothTheSelectedExtensionAndConfiguredFlag(String raw) throws Exception {
        assertThat(AIWorkspacePublicHandoverProjection.project(mapper, mapper.readTree(raw)))
            .isEqualTo(mapper.readTree("{\"enabled\":false}"));
    }
}
