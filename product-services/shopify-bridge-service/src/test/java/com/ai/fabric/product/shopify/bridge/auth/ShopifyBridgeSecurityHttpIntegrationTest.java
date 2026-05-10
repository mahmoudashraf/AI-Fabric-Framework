package com.ai.fabric.product.shopify.bridge.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "shopify.bridge.admin-api-key=test-admin-key",
        "shopify.bridge.shopify-api-key=test-shopify-api-key",
        "shopify.bridge.shopify-api-secret=test-shopify-secret",
        "shopify.bridge.webhook-shared-secret=super-secret"
    }
)
class ShopifyBridgeSecurityHttpIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int port;

    @Test
    void protectedRoutesPreserveUnauthorizedStatusAfterServletErrorDispatch() {
        ResponseEntity<String> adminResponse = restTemplate.getForEntity("/api/admin/overview", String.class);
        ResponseEntity<String> merchantResponse = restTemplate.getForEntity("/api/app/session", String.class);

        assertThat(adminResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(merchantResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void invalidWebhookHmacPreservesUnauthorizedStatusAfterServletErrorDispatch() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/webhooks/shopify"))
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .header("X-Shopify-Hmac-Sha256", "bad-hmac")
            .header("X-Shopify-Topic", "app/uninstalled")
            .header("X-Shopify-Shop-Domain", "alpha.myshopify.com")
            .POST(HttpRequest.BodyPublishers.ofString("{\"myshopify_domain\":\"alpha.myshopify.com\"}"))
            .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
            .send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void preflightRequestsDoNotRequireRouteAuthentication() throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> adminResponse = client.send(
            optionsRequest("/api/admin/overview"),
            HttpResponse.BodyHandlers.ofString()
        );
        HttpResponse<String> merchantResponse = client.send(
            optionsRequest("/api/app/session"),
            HttpResponse.BodyHandlers.ofString()
        );

        assertThat(adminResponse.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(merchantResponse.statusCode()).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    void unmappedApiRoutesRemainForbidden() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/internal/unknown", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void customerAccountAuthRoutesArePublicButStillValidatedByController() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/customer-auth/session", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private HttpRequest optionsRequest(String path) {
        return HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + path))
            .method(HttpMethod.OPTIONS.name(), HttpRequest.BodyPublishers.noBody())
            .build();
    }
}
