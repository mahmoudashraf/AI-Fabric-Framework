package com.ai.fabric.platform.backend.aiworkspace.service;

import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceInteractionPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AIWorkspaceAuthenticationProfileRegistryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AIWorkspaceAuthenticationProfileRegistry registry =
        new AIWorkspaceAuthenticationProfileRegistry(objectMapper);

    @Test
    void exposesOnlyReviewedPublicSupabaseConfiguration() throws Exception {
        var profile = registry.resolve(
            "supabase",
            "1.0.0",
            AIWorkspaceInteractionPolicy.PUBLIC_WITH_PROTECTED_CAPABILITIES,
            objectMapper.readTree("""
                {
                  "projectUrl":"https://Example.Supabase.co/",
                  "publishableKey":"sb_publishable_fixture",
                  "methods":["email-password","magic-link"]
                }
                """)
        );

        assertThat(profile.code()).isEqualTo("supabase");
        assertThat(profile.publicConfiguration().path("projectUrl").asText())
            .isEqualTo("https://example.supabase.co");
        assertThat(profile.publicConfiguration().path("methods").toString())
            .isEqualTo("[\"email-password\",\"magic-link\"]");
        assertThat(profile.publicConfiguration().has("serviceRoleKey")).isFalse();
    }

    @Test
    void rejectsMissingProviderForAuthenticatedPoliciesAndProviderForPublicOnly() throws Exception {
        assertThatThrownBy(() -> registry.resolve(
            "none", "1.0.0", AIWorkspaceInteractionPolicy.LOGIN_REQUIRED,
            objectMapper.createObjectNode()))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("requires an authentication profile");

        assertThatThrownBy(() -> registry.resolve(
            "supabase", "1.0.0", AIWorkspaceInteractionPolicy.PUBLIC,
            objectMapper.readTree("""
                {"projectUrl":"https://example.supabase.co","publishableKey":"sb_publishable_fixture","methods":["magic-link"]}
                """)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Public-only");
    }

    @Test
    void rejectsSecretsUnknownFieldsAndUnsafeProviderUrls() throws Exception {
        assertThatThrownBy(() -> registry.resolve(
            "supabase", "1.0.0", AIWorkspaceInteractionPolicy.LOGIN_REQUIRED,
            objectMapper.readTree("""
                {
                  "projectUrl":"http://example.supabase.co",
                  "publishableKey":"sb_publishable_fixture",
                  "methods":["email-password"],
                  "serviceRoleKey":"must-not-be-accepted"
                }
                """)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Unknown authentication configuration field");
    }

    @Test
    void rejectsSecretAndServiceRoleKeysButAcceptsLegacyAnonKeys() throws Exception {
        var configuration = objectMapper.createObjectNode();
        configuration.put("projectUrl", "https://example.supabase.co");
        configuration.putArray("methods").add("email-password");
        for (String key : java.util.List.of("sb_secret_fixture", jwt("service_role"), jwt("authenticated"), "unknown-key")) {
            configuration.put("publishableKey", key);
            assertThatThrownBy(() -> registry.resolve("supabase", "1.0.0",
                AIWorkspaceInteractionPolicy.LOGIN_REQUIRED, configuration))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("legacy anon key");
        }
        configuration.put("publishableKey", jwt("anon"));
        assertThat(registry.resolve("supabase", "1.0.0", AIWorkspaceInteractionPolicy.LOGIN_REQUIRED, configuration)
            .publicConfiguration().path("publishableKey").asText()).isEqualTo(jwt("anon"));
    }

    private String jwt(String role) {
        return "eyJhbGciOiJIUzI1NiJ9." + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
            ("{\"role\":\"" + role + "\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8)) + ".c2lnbmF0dXJl";
    }
}
