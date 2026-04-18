package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.model.PublicConsumerDeploymentCredentialsResponse;
import com.ai.fabric.platform.backend.deployment.model.PublicDeploymentAccessSummary;
import com.ai.fabric.platform.backend.deployment.model.PublicDeploymentIntegrationSummary;
import com.ai.fabric.platform.backend.deployment.model.PublicIntegrationPathSummary;
import com.ai.fabric.platform.backend.deployment.model.PublicRuntimeEndpointsSummary;
import com.ai.fabric.platform.backend.deployment.model.PublicRuntimePostureSummary;
import com.ai.fabric.platform.backend.deployment.model.PublicRuntimeTokenSummary;
import com.ai.fabric.platform.backend.deployment.model.PublicTrustedBackendAccessSummary;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.security.RuntimePrivateAssertionSigningService;
import com.ai.fabric.platform.backend.security.RuntimePublicTokenSigningService;
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import com.ai.fabric.platform.backend.tenant.service.PlatformCustomerConsumerService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicConsumerBridgeChatServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void queryPrefersPrivateRuntimeHeadersForBridgeTraffic() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        AtomicReference<String> capturedTrustedBackend = new AtomicReference<>();
        AtomicReference<String> capturedPrivateAuthorization = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            server.createContext("/api/chat/me/query", exchange -> {
                capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                capturedTrustedBackend.set(exchange.getRequestHeaders().getFirst("X-AIFABRIC-RUNTIME-API-KEY"));
                capturedPrivateAuthorization.set(exchange.getRequestHeaders().getFirst("X-AIFABRIC-RUNTIME-AUTHORIZATION"));
                writeJson(exchange, 200, """
                    {"success":true,"conversationId":"conv-1","result":{"message":"Hello from runtime"}}
                    """);
            });
            server.start();

            PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
            when(platformSecretService.resolveSecret("AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY")).thenReturn("trusted-backend-key");
            when(platformSecretService.resolveSecret(RuntimePrivateAssertionSigningService.SECRET_NAME)).thenReturn("private-signing-key");
            when(platformSecretService.resolveSecret(RuntimePublicTokenSigningService.SECRET_NAME)).thenReturn("public-signing-key");

            PublicConsumerBridgeChatService service = new PublicConsumerBridgeChatService(
                consumerService(server),
                credentialsService(privateAccess(server)),
                platformSecretService,
                new RuntimePrivateAssertionSigningService(platformSecretService, objectMapper),
                new RuntimePublicTokenSigningService(platformSecretService, objectMapper),
                objectMapper
            );

            JsonNode response = service.query(
                "consumer-alpha",
                objectMapper.readTree("""
                    {"query":"Find laptop bags","conversationId":"conv-1","promptPreview":{"systemPrompt":"skip"}}
                    """),
                "shopper-session-alpha"
            );

            assertThat(response.path("conversationId").asText()).isEqualTo("conv-1");
            assertThat(capturedTrustedBackend.get()).isEqualTo("trusted-backend-key");
            assertThat(capturedPrivateAuthorization.get()).startsWith("Bearer ");
            JsonNode requestBody = objectMapper.readTree(capturedBody.get());
            assertThat(requestBody.path("query").asText()).isEqualTo("Find laptop bags");
            assertThat(requestBody.path("conversationId").asText()).isEqualTo("conv-1");
            assertThat(requestBody.has("promptPreview")).isFalse();
            Map<String, Object> assertion = decodeTokenPayload(capturedPrivateAuthorization.get());
            assertThat(assertion).containsEntry("authMode", "PRIVATE_RUNTIME_BACKEND_MEDIATED");
            assertThat(assertion).containsEntry("callerType", "SHOPIFY_BRIDGE_SERVICE");
            assertThat(assertion).containsEntry("deploymentId", "dep-1");
            assertThat(assertion).containsEntry("iss", "platform-consumer-bridge");
            assertThat(assertion.get("sessionId")).isEqualTo("shopper-session-alpha");
            assertThat(assertion.get("scopes")).isEqualTo(List.of("chat:query"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void suggestionsFallsBackToSignedPublicTokenWhenPrivateRuntimeIsUnavailable() throws Exception {
        AtomicReference<String> capturedAuthorization = new AtomicReference<>();
        AtomicReference<String> capturedBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            server.createContext("/api/chat/me/suggestions", exchange -> {
                capturedAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                writeJson(exchange, 200, """
                    {"success":true,"suggestions":["Show me backpacks","What are your return options?"]}
                    """);
            });
            server.start();

            PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
            when(platformSecretService.resolveSecret("AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY")).thenReturn(null);
            when(platformSecretService.resolveSecret(RuntimePrivateAssertionSigningService.SECRET_NAME)).thenReturn("private-signing-key");
            when(platformSecretService.resolveSecret(RuntimePublicTokenSigningService.SECRET_NAME)).thenReturn("public-signing-key");

            PublicConsumerBridgeChatService service = new PublicConsumerBridgeChatService(
                consumerService(server),
                credentialsService(publicAccess(server)),
                platformSecretService,
                new RuntimePrivateAssertionSigningService(platformSecretService, objectMapper),
                new RuntimePublicTokenSigningService(platformSecretService, objectMapper),
                objectMapper
            );

            JsonNode response = service.suggestions("consumer-alpha", objectMapper.createObjectNode(), "shopper-session-beta");

            assertThat(response.path("suggestions")).hasSize(2);
            JsonNode requestBody = objectMapper.readTree(capturedBody.get());
            assertThat(requestBody.path("content").asText()).isEmpty();
            assertThat(requestBody.path("maxSuggestions").asInt()).isEqualTo(4);
            Map<String, Object> token = decodeTokenPayload(capturedAuthorization.get());
            assertThat(token).containsEntry("authMode", "PUBLIC_RUNTIME_BRIDGE_TOKEN");
            assertThat(token).containsEntry("callerType", "SHOPIFY_BRIDGE_SERVICE");
            assertThat(token).containsEntry("iss", "shopify-bridge");
            assertThat(token).containsEntry("aud", "storefront-chat");
            assertThat(token.get("sessionId")).isEqualTo("shopper-session-beta");
            assertThat(token.get("scopes")).isEqualTo(List.of("chat:suggestions"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void queryFailsClosedWhenNoSupportedRuntimeAuthIsConfigured() {
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        when(platformSecretService.resolveSecret("AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY")).thenReturn(null);
        when(platformSecretService.resolveSecret(RuntimePrivateAssertionSigningService.SECRET_NAME)).thenReturn(null);
        when(platformSecretService.resolveSecret(RuntimePublicTokenSigningService.SECRET_NAME)).thenReturn(null);

        PublicConsumerBridgeChatService service = new PublicConsumerBridgeChatService(
            consumerService(null),
            credentialsService(noAuthAccess()),
            platformSecretService,
            new RuntimePrivateAssertionSigningService(platformSecretService, objectMapper),
            new RuntimePublicTokenSigningService(platformSecretService, objectMapper),
            objectMapper
        );

        assertThatThrownBy(() -> service.query("consumer-alpha", objectMapper.createObjectNode().put("query", "Hi"), null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Route customer traffic through your host");
    }

    private PlatformCustomerConsumerService consumerService(HttpServer server) {
        PlatformCustomerConsumerService service = mock(PlatformCustomerConsumerService.class);
        PlatformConsumerEntity consumer = new PlatformConsumerEntity();
        consumer.setConsumerId("consumer-alpha");
        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-1");
        deployment.setCustomerId("cust-1");
        deployment.setTenantId("ten-1");
        deployment.setRuntimeBaseUrl(server == null ? "https://runtime.example.com" : "http://localhost:" + server.getAddress().getPort());
        when(service.resolvePublicConsumer("consumer-alpha"))
            .thenReturn(new PlatformCustomerConsumerService.ResolvedPublicConsumer(consumer, deployment));
        return service;
    }

    private PublicProvisioningApiService credentialsService(PublicDeploymentAccessSummary access) {
        PublicProvisioningApiService service = mock(PublicProvisioningApiService.class);
        when(service.getConsumerDeploymentCredentials("consumer-alpha")).thenReturn(
            new PublicConsumerDeploymentCredentialsResponse(
                "consumer-alpha",
                "dep-1",
                access.runtime().chatBaseUrl(),
                access,
                new PublicDeploymentIntegrationSummary(
                    "BACKEND_MEDIATED_PRIVATE_RUNTIME",
                    access.posture(),
                    access.runtime(),
                    access.trustedBackend(),
                    access.publicRuntime(),
                    new PublicIntegrationPathSummary(true, false, null, null, access.runtime().chatBaseUrl()),
                    access.guidance()
                )
            )
        );
        return service;
    }

    private PublicDeploymentAccessSummary privateAccess(HttpServer server) {
        String baseUrl = "http://localhost:" + server.getAddress().getPort();
        return new PublicDeploymentAccessSummary(
            "RUNTIME_ENTRYPOINT",
            "PRIVATE_INTERNAL_SERVICE",
            new PublicRuntimePostureSummary("PRIVATE_RUNTIME_SIGNED_ASSERTION", true, true, false),
            new PublicRuntimeEndpointsSummary(
                baseUrl,
                null,
                baseUrl + "/api/chat/me/query",
                baseUrl + "/api/chat/me/suggestions",
                baseUrl + "/api/chat/me/conversations",
                baseUrl + "/api/chat/me/conversations/{conversationId}",
                baseUrl,
                baseUrl + "/api/chat/me/auth-context",
                baseUrl + "/api/admin/auth/overview"
            ),
            new PublicTrustedBackendAccessSummary(true, "X-AIFABRIC-RUNTIME-API-KEY", true, "X-AIFABRIC-RUNTIME-AUTHORIZATION", "Bearer", false, false, true, false),
            new PublicRuntimeTokenSummary(false, false, null, null, null, false, false, null, null),
            "Route customer traffic through your host or storefront backend."
        );
    }

    private PublicDeploymentAccessSummary publicAccess(HttpServer server) {
        String baseUrl = "http://localhost:" + server.getAddress().getPort();
        return new PublicDeploymentAccessSummary(
            "RUNTIME_ENTRYPOINT",
            "PRIVATE_INTERNAL_SERVICE",
            new PublicRuntimePostureSummary("PUBLIC_RUNTIME_SIGNED_TOKEN", true, false, false),
            new PublicRuntimeEndpointsSummary(
                baseUrl,
                null,
                baseUrl + "/api/chat/me/query",
                baseUrl + "/api/chat/me/suggestions",
                baseUrl + "/api/chat/me/conversations",
                baseUrl + "/api/chat/me/conversations/{conversationId}",
                baseUrl,
                baseUrl + "/api/chat/me/auth-context",
                baseUrl + "/api/admin/auth/overview"
            ),
            new PublicTrustedBackendAccessSummary(false, null, false, null, null, false, false, false, false),
            new PublicRuntimeTokenSummary(true, true, baseUrl + "/api/public/chat/session", "Authorization", "Bearer", true, true, "shopify-bridge", "storefront-chat"),
            "Runtime can validate signed public bearer tokens."
        );
    }

    private PublicDeploymentAccessSummary noAuthAccess() {
        return new PublicDeploymentAccessSummary(
            "RUNTIME_ENTRYPOINT",
            "PRIVATE_INTERNAL_SERVICE",
            new PublicRuntimePostureSummary("AUTH_CONFIGURATION_REQUIRED", true, false, false),
            new PublicRuntimeEndpointsSummary(
                "https://runtime.example.com",
                null,
                "https://runtime.example.com/api/chat/me/query",
                "https://runtime.example.com/api/chat/me/suggestions",
                "https://runtime.example.com/api/chat/me/conversations",
                "https://runtime.example.com/api/chat/me/conversations/{conversationId}",
                "https://runtime.example.com",
                "https://runtime.example.com/api/chat/me/auth-context",
                "https://runtime.example.com/api/admin/auth/overview"
            ),
            new PublicTrustedBackendAccessSummary(false, null, false, null, null, false, false, false, false),
            new PublicRuntimeTokenSummary(false, false, null, null, null, false, false, null, null),
            "Route customer traffic through your host or storefront backend."
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> decodeTokenPayload(String authorization) throws Exception {
        String token = authorization.replaceFirst("^Bearer\\s+", "");
        String[] parts = token.split("\\.");
        return objectMapper.readValue(Base64.getUrlDecoder().decode(parts[1]), Map.class);
    }

    private void writeJson(HttpExchange exchange, int status, String json) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }
}
