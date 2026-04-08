package com.ai.fabric.platform.backend.deployment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ManagedDeploymentProfileCatalogTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void defaultPrivateRuntimeAcceptedIssuersCoverCurrentFirstPartyRuntimeCallers() throws Exception {
        String issuers = ManagedDeploymentProfileCatalog.effectivePrivateRuntimeAcceptedIssuers(
            objectMapper.readTree("{}")
        );

        assertThat(issuers).isEqualTo(
            "platform-runtime:SESSION,platform-runtime:API_KEY,platform-poc:SESSION,platform-poc:API_KEY,platform-poc:SYSTEM,platform-release-verification,platform-vectorization-verification,platform-runtime-coverage"
        );
    }

    @Test
    void configuredPrivateRuntimeAcceptedIssuersOverridePlatformDefaults() throws Exception {
        String issuers = ManagedDeploymentProfileCatalog.effectivePrivateRuntimeAcceptedIssuers(
            objectMapper.readTree("""
                {
                  "privateRuntimeAcceptedIssuers": "trusted-backend-app,shopify-app"
                }
                """)
        );

        assertThat(issuers).isEqualTo("trusted-backend-app,shopify-app");
    }
}
