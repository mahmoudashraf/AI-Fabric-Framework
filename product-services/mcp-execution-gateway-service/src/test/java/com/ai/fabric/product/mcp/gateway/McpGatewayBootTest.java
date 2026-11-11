package com.ai.fabric.product.mcp.gateway;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.Map;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "mcp.gateway.internal-api-key=fixture-internal-key")
class McpGatewayBootTest {
    @Autowired
    private TestRestTemplate http;
    @Autowired
    private RestClient.Builder credentialRequests;

    @Test
    void bootsWithSdkAndKeepsExistingInternalAuthentication() {
        assertThat(http.getForEntity("/actuator/health", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(http.getForEntity("/api/admin/overview", String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-MCP-GATEWAY-API-KEY", "fixture-internal-key");
        var response = http.exchange("/api/admin/overview", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("READY", "mcp.initialize");
    }

    @Test
    void trustedInternalCallStillCannotDiscoverPrivateInfrastructure() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-MCP-GATEWAY-API-KEY", "fixture-internal-key");
        var response = http.postForEntity("/api/internal/mcp/import/discover", new HttpEntity<>(Map.of(
            "serverRef", "fixture", "server", Map.of("endpointUrl", "https://169.254.169.254/latest", "auth", Map.of("mode", "NONE"))
        ), headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"ready\":false", "MCP_DISCOVERY_FAILED");
        assertThat(response.getBody()).doesNotContain("fixture-internal-key");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://127.0.0.1:9/token", "https://[::1]:9/token", "https://169.254.169.254/token", "http://example.com/token"})
    void productionCredentialRequestFactoryRejectsPrivateOrNonHttpsTargets(String endpoint) {
        assertThatThrownBy(() -> credentialRequests.clone().build().post().uri(endpoint)
            .body(Map.of("client_secret", "fixture-never-dispatched")).retrieve().body(String.class))
            .hasMessageContaining("MCP credential destination must be public HTTPS");
    }
}
