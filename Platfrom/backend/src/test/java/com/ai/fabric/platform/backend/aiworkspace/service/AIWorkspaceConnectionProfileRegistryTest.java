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
}
