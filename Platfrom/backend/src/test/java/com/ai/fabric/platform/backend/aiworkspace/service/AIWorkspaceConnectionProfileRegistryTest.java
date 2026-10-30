package com.ai.fabric.platform.backend.aiworkspace.service;

import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceConnectionMode;
import com.ai.fabric.platform.backend.config.PlatformAIWorkspaceProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.*;

class AIWorkspaceConnectionProfileRegistryTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final AIWorkspaceConnectionProfileRegistry registry = new AIWorkspaceConnectionProfileRegistry(
        new PlatformAIWorkspaceProperties(null, false, 0, null, null, null, null, null));

    @Test void anonymousAndAuthenticatedConnectionsAcceptNoCustomerSuppliedCredentialsOrEndpoints() {
        for (var mode : java.util.List.of(AIWorkspaceConnectionMode.PUBLIC_RUNTIME_ANONYMOUS,
            AIWorkspaceConnectionMode.PUBLIC_RUNTIME_AUTHENTICATED)) {
            String code = mode == AIWorkspaceConnectionMode.PUBLIC_RUNTIME_ANONYMOUS
                ? AIWorkspaceConnectionProfileRegistry.ANONYMOUS : AIWorkspaceConnectionProfileRegistry.AUTHENTICATED;
            assertThat(registry.resolve(code, "1.0.0", mode, mapper.createObjectNode()).publicConfiguration()).isEmpty();
            for (String field : java.util.List.of("accessToken", "refreshToken", "endpoint", "shopDomain")) {
                assertThatThrownBy(() -> registry.resolve(code, "1.0.0", mode,
                    mapper.createObjectNode().put(field, "must-not-be-public")))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Unknown connection configuration field");
            }
        }
    }

    @Test void shopifyConfigurationRejectsUnrecognizedFieldsBeforeResolvingTheBridge() {
        assertThatThrownBy(() -> registry.resolve(AIWorkspaceConnectionProfileRegistry.SHOPIFY, "1.0.0",
            AIWorkspaceConnectionMode.BACKEND_MEDIATED_PRIVATE_RUNTIME,
            mapper.createObjectNode().put("shopDomain", "test.myshopify.com").put("accessToken", "private-token")))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Unknown connection configuration field");
    }

    @Test void supportCaseHostIsExplicitInternalAndNeverOfferedAsAPublicScriptProfile() {
        var enabled = new AIWorkspaceConnectionProfileRegistry(
            new PlatformAIWorkspaceProperties(null, false, 0, null, null, null, null, null),
            new org.springframework.mock.env.MockEnvironment().withProperty("platform.support.enabled", "true")
                .withProperty("platform.support.customer-id", "internal-owner"));
        var profile = enabled.resolve(AIWorkspaceConnectionProfileRegistry.SUPPORT_CASE_HOST, "1.0.0",
            AIWorkspaceConnectionMode.BACKEND_MEDIATED_PRIVATE_RUNTIME, mapper.createObjectNode());
        assertThat(profile.handler()).isEqualTo("host-controlled-private-runtime");
        assertThat(profile.endpoint()).isNull();
        assertThat(enabled.summaries()).noneMatch(item -> AIWorkspaceConnectionProfileRegistry.SUPPORT_CASE_HOST.equals(item.code()));
        enabled.requireCustomer(profile.code(), "internal-owner");
        assertThatThrownBy(() -> enabled.requireCustomer(profile.code(), "another-customer"))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("internal owner");
        assertThatThrownBy(() -> registry.resolve(profile.code(), "1.0.0", profile.mode(), mapper.createObjectNode()))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("not configured");
        assertThatThrownBy(() -> enabled.resolve(profile.code(), "1.0.0", profile.mode(), mapper.createObjectNode().put("caseId", "sup-1")))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Unknown connection configuration field");
    }
}
