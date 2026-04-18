package com.ai.fabric.platform.backend.deployment.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RailwayApiProvisioningProviderServiceBaseUrlTest {

    @Test
    void resolveServiceBaseUrlUsesConnectorPublicUrlForRuntimeActionsConnector() {
        assertThat(RailwayApiProvisioningProvider.resolveServiceBaseUrl(
            "ACTIONS_CONNECTOR_BASE_URL",
            "https://runtime.example",
            "https://connector.example"
        )).isEqualTo("https://connector.example");
    }

    @Test
    void resolveServiceBaseUrlUsesConnectorPublicUrlForRuntimeAuthzBaseUrl() {
        assertThat(RailwayApiProvisioningProvider.resolveServiceBaseUrl(
            "AUTHZ_BASE_URL",
            "https://runtime.example",
            "https://connector.example"
        )).isEqualTo("https://connector.example");
    }

    @Test
    void resolveServiceBaseUrlUsesRuntimePublicUrlForConnectorRuntimeProxy() {
        assertThat(RailwayApiProvisioningProvider.resolveServiceBaseUrl(
            "REST_CONNECTOR_RUNTIME_PROXY_BASE_URL",
            "https://runtime.example",
            "https://connector.example"
        )).isEqualTo("https://runtime.example");
    }

    @Test
    void resolveServiceBaseUrlReturnsNullForUnrelatedEnvKeys() {
        assertThat(RailwayApiProvisioningProvider.resolveServiceBaseUrl(
            "OPENAI_API_KEY",
            "https://runtime.example",
            "https://connector.example"
        )).isNull();
    }
}
