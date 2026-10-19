package com.loomai.demo.dealership.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loomai.demo.dealership.lead.LeadService;
import com.loomai.demo.dealership.provider.ProviderStockClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiExceptionHandlerHttpTest {
    private static final String INTERNAL_KEY = "method-regression-internal-key";
    private static final String ACTION_PATH = "/api/internal/actions/execute";
    private static final String DATABASE = "jdbc:h2:mem:method-regression-" + UUID.randomUUID()
        + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DATABASE);
        registry.add("dealership.internal.api-key", () -> INTERNAL_KEY);
        registry.add("dealership.privacy.encryption-key-base64", () -> Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)));
        registry.add("dealership.runtime.enabled", () -> "false");
        registry.add("dealership.provider-simulator.enabled", () -> "false");
    }

    @LocalServerPort int port;
    @Autowired ObjectMapper mapper;
    @MockBean LeadService leads;
    @MockBean ProviderStockClient provider;

    private final HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();

    @AfterEach
    void noActionOrProviderReadWasExecuted() {
        verifyNoInteractions(leads, provider);
    }

    @Test
    void authenticatedGetReturns405AndPostAllowanceWithSafeStructuredError() throws Exception {
        var response = request("GET", ACTION_PATH, INTERNAL_KEY);

        assertMethodNotAllowed(response);
        assertThat(mapper.readTree(response.body()).path("message").asText())
            .isEqualTo("This HTTP method is not supported for the requested resource.");
        assertThat(response.body()).doesNotContain(INTERNAL_KEY, "Exception", "stackTrace", "trace");
    }

    @ParameterizedTest
    @ValueSource(strings = {"HEAD", "PUT", "PATCH", "DELETE"})
    void unsupportedVerbsStayUnsupportedAndNeverExecuteTheAction(String method) throws Exception {
        var response = request(method, ACTION_PATH, INTERNAL_KEY);

        assertThat(response.statusCode()).isEqualTo(405);
        assertThat(response.headers().allValues("Allow")).containsExactly("POST");
        if ("HEAD".equals(method)) assertThat(response.body()).isEmpty();
        else assertMethodNotAllowed(response);
    }

    @ParameterizedTest
    @ValueSource(strings = {ACTION_PATH, "/api/internal/authz/check"})
    void otherPostOnlyInternalRoutesPreserveTheirDeclaredMethod(String path) throws Exception {
        assertMethodNotAllowed(request("GET", path, INTERNAL_KEY));
    }

    @ParameterizedTest
    @CsvSource({"GET, missing", "GET, incorrect", "HEAD, missing", "HEAD, incorrect",
        "PUT, missing", "PUT, incorrect"})
    void unauthenticatedRequestsAreRejectedBeforeMethodDiscovery(String method, String credential) throws Exception {
        var response = request(method, ACTION_PATH, "missing".equals(credential) ? null : "incorrect-key");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().allValues("Allow")).isEmpty();
        if ("HEAD".equals(method)) assertThat(response.body()).isEmpty();
        else {
            JsonNode error = mapper.readTree(response.body());
            assertThat(error.path("success").booleanValue()).isFalse();
            assertThat(error.path("errorCode").asText()).isEqualTo("INTERNAL_AUTH_REQUIRED");
        }
    }

    @Test
    void unknownPublicResourcesRetainTheExistingStructured404() throws Exception {
        var response = request("GET", "/api/public/no-method-regression-resource", null);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.headers().allValues("Allow")).isEmpty();
        JsonNode error = mapper.readTree(response.body());
        assertThat(error.path("success").booleanValue()).isFalse();
        assertThat(error.path("errorCode").asText()).isEqualTo("NOT_FOUND");
    }

    @Test
    void absentFrameworkMethodMetadataDoesNotInventAnAllowHeader() {
        var response = new ApiExceptionHandler().methodNotAllowed(new HttpRequestMethodNotSupportedException("GET"));

        assertThat(response.getStatusCode().value()).isEqualTo(405);
        assertThat(response.getHeaders().getAllow()).isEmpty();
        JsonNode error = mapper.valueToTree(response.getBody());
        assertThat(error.path("success").booleanValue()).isFalse();
        assertThat(error.path("errorCode").asText()).isEqualTo("METHOD_NOT_ALLOWED");
    }

    private HttpResponse<String> request(String method, String path, String credential) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .timeout(Duration.ofSeconds(10)).header("Accept", "application/json")
            .method(method, HttpRequest.BodyPublishers.noBody());
        if (credential != null) request.header("X-DEALERSHIP-INTERNAL-KEY", credential);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private void assertMethodNotAllowed(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).isEqualTo(405);
        assertThat(response.headers().allValues("Allow")).containsExactly("POST");
        JsonNode error = mapper.readTree(response.body());
        assertThat(error.path("success").booleanValue()).isFalse();
        assertThat(error.path("errorCode").asText()).isEqualTo("METHOD_NOT_ALLOWED");
        assertThat(error.size()).isEqualTo(3);
    }
}
