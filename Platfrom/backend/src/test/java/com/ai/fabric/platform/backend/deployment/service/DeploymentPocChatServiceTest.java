package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPocChatQueryRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPocChatQueryResponse;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPocChatSuggestionsRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPocRuntimeAuthContextSummary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeploymentPocChatServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void queryUsesDeploymentScopedOwnerAndParsesRuntimeResponse() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        AtomicReference<String> capturedAdminKey = new AtomicReference<>();
        AtomicReference<String> capturedTrustedBackendKey = new AtomicReference<>();
        AtomicReference<String> capturedPrivateAuthorization = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            server.createContext("/api/chat/me/query", exchange -> {
                capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                capturedAdminKey.set(exchange.getRequestHeaders().getFirst("X-ADMIN-API-KEY"));
                capturedTrustedBackendKey.set(exchange.getRequestHeaders().getFirst("X-AIFABRIC-RUNTIME-API-KEY"));
                capturedPrivateAuthorization.set(exchange.getRequestHeaders().getFirst("X-AIFABRIC-RUNTIME-AUTHORIZATION"));
                writeJson(
                    exchange,
                    200,
                    """
                        {
                          "success": true,
                          "conversationId": "chat-123",
                          "sessionId": "runtime-session-1",
                          "result": {
                            "type": "COMPOUND_HANDLED",
                            "success": true,
                            "message": "Grounded response",
                            "children": [
                              {
                                "type": "ACTION_EXECUTED",
                                "success": true,
                                "message": "Listed products",
                                "data": {
                                  "action": "list_products",
                                  "summary": "Returned five products."
                                },
                                "metadata": {
                                  "actionParamValidation": {
                                    "missing": [],
                                    "provenanceMissing": [],
                                    "sourcesUsed": {
                                      "user": true,
                                      "history": false,
                                      "pinned": false
                                    }
                                  }
                                }
                              },
                              {
                                "type": "INFORMATION_PROVIDED",
                                "success": true,
                                "message": "Grounded response",
                                "data": {
                                  "answer": "Grounded response",
                                  "routingStrategy": "FAN_OUT",
                                  "candidateVectorSpaces": ["product", "policy"],
                                  "documents": [
                                    {
                                      "id": "doc-1",
                                      "title": "Catalog",
                                      "score": 0.91,
                                      "source": "runtime",
                                      "url": "https://example.com/doc-1",
                                      "metadata": {
                                        "vectorSpace": "product"
                                      }
                                    }
                                  ]
                                }
                              }
                            ]
                          }
                        }
                        """
                );
            });
            server.start();

            DeploymentPocChatService service = serviceFor(server, null, null, "trusted-backend-key");
            authenticateOperator();

            DeploymentPocChatQueryResponse response = service.query(
                "dep-123",
                new DeploymentPocChatQueryRequest("What can you do?", null, null, null, null)
            );

            JsonNode requestBody = objectMapper.readTree(capturedBody.get());
            assertThat(requestBody.path("query").asText()).isEqualTo("What can you do?");
            assertThat(requestBody.has("userId")).isFalse();
            assertThat(requestBody.has("sessionId")).isFalse();
            assertThat(requestBody.path("promptPreview").isMissingNode()).isTrue();
            assertThat(capturedAdminKey.get()).isNull();
            assertThat(capturedTrustedBackendKey.get()).isEqualTo("trusted-backend-key");
            Map<String, Object> assertion = decodeAssertionPayload(capturedPrivateAuthorization.get());
            assertThat(assertion).containsEntry("sub", "operator@example.com");
            assertThat(assertion).containsEntry("subjectType", "INTERNAL_PLATFORM_USER");
            assertThat(assertion).containsEntry("authMode", "PLATFORM_PROXY_SESSION");
            assertThat(assertion).containsEntry("callerType", "PLATFORM_PROXY");
            assertThat(assertion).containsEntry("deploymentId", "dep-123");
            assertThat(assertion).containsEntry("customerId", "cus-123");
            assertThat(assertion).containsEntry("tenantId", "ten-123");
            assertThat(assertion).containsEntry("iss", "platform-poc:SESSION");
            assertThat(assertion).containsEntry("aud", "dep-123");
            assertThat(assertion.get("sessionId")).asString().startsWith("platform-poc-dep-123-");
            assertThat(assertion.get("scopes")).isEqualTo(List.of("poc:chat", "poc:conversation"));
            assertThat(assertion).containsKey("exp");
            assertThat(response.success()).isTrue();
            assertThat(response.conversationId()).isEqualTo("chat-123");
            assertThat(response.result().path("message").asText()).isEqualTo("Grounded response");
            assertThat(response.traceSummary()).isNotNull();
            assertThat(response.traceSummary().executedAction()).isEqualTo("list_products");
            assertThat(response.traceSummary().answer()).isEqualTo("Grounded response");
            assertThat(response.traceSummary().routingStrategy()).isEqualTo("FAN_OUT");
            assertThat(response.traceSummary().vectorSpaces()).containsExactly("product");
            assertThat(response.traceSummary().candidateVectorSpaces()).containsExactly("product", "policy");
            assertThat(response.traceSummary().documentCount()).isEqualTo(1);
            assertThat(response.traceSummary().documents()).singleElement().satisfies(document -> {
                assertThat(document.title()).isEqualTo("Catalog");
                assertThat(document.vectorSpace()).isEqualTo("product");
            });
            assertThat(response.traceSummary().actionValidation()).isNotNull();
            assertThat(response.traceSummary().actionValidation().path("sourcesUsed").path("user").asBoolean()).isTrue();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void queryWithPromptPreviewAddsAdminHeaderAndSanitizesOverlay() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        AtomicReference<String> capturedAdminKey = new AtomicReference<>();
        AtomicReference<String> capturedTrustedBackendKey = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            server.createContext("/api/chat/me/query", exchange -> {
                capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                capturedAdminKey.set(exchange.getRequestHeaders().getFirst("X-ADMIN-API-KEY"));
                capturedTrustedBackendKey.set(exchange.getRequestHeaders().getFirst("X-AIFABRIC-RUNTIME-API-KEY"));
                writeJson(
                    exchange,
                    200,
                    """
                        {
                          "success": true,
                          "conversationId": "chat-preview",
                          "sessionId": "runtime-session-preview",
                          "result": {
                            "type": "INFORMATION_PROVIDED",
                            "success": true,
                            "message": "Preview answer",
                            "data": {
                              "answer": "Preview answer"
                            }
                          }
                        }
                        """
                );
            });
            server.start();

            DeploymentPocChatService service = serviceFor(server, "preview-admin-key", null, "trusted-backend-key");
            authenticateOperator();

            ObjectNode preview = objectMapper.createObjectNode();
            preview.put("systemPrompt", "Use a direct tone.");
            preview.put("answerGenerationPrompt", "Answer in two bullets.");
            preview.put("ignored", "should-not-pass-through");

            DeploymentPocChatQueryResponse response = service.query(
                "dep-123",
                new DeploymentPocChatQueryRequest("Preview this response", null, null, null, preview)
            );

            JsonNode requestBody = objectMapper.readTree(capturedBody.get());
            assertThat(capturedAdminKey.get()).isEqualTo("preview-admin-key");
            assertThat(capturedTrustedBackendKey.get()).isEqualTo("trusted-backend-key");
            assertThat(requestBody.path("promptPreview").isObject()).isTrue();
            assertThat(requestBody.path("promptPreview").path("systemPrompt").asText()).isEqualTo("Use a direct tone.");
            assertThat(requestBody.path("promptPreview").path("answerGenerationPrompt").asText())
                .isEqualTo("Answer in two bullets.");
            assertThat(requestBody.path("promptPreview").has("ignored")).isFalse();
            assertThat(response.success()).isTrue();
            assertThat(response.conversationId()).isEqualTo("chat-preview");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void conversationAndSuggestionsAreProxiedThroughRuntime() throws Exception {
        AtomicReference<String> suggestionsBody = new AtomicReference<>();
        AtomicReference<String> suggestionsTrustedBackendKey = new AtomicReference<>();
        AtomicReference<String> conversationQuery = new AtomicReference<>();
        AtomicReference<String> deleteConversationQuery = new AtomicReference<>();
        AtomicReference<String> conversationTrustedBackendKey = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            server.createContext("/api/chat/me/suggestions", exchange -> {
                suggestionsBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                suggestionsTrustedBackendKey.set(exchange.getRequestHeaders().getFirst("X-AIFABRIC-RUNTIME-API-KEY"));
                writeJson(
                    exchange,
                    200,
                    """
                        {
                          "success": true,
                          "suggestions": ["Summarize catalog", "Explain refund policy"],
                          "raw": null
                        }
                        """
                );
            });
            server.createContext("/api/chat/me/conversations/chat-555", exchange -> {
                conversationTrustedBackendKey.set(exchange.getRequestHeaders().getFirst("X-AIFABRIC-RUNTIME-API-KEY"));
                if ("DELETE".equalsIgnoreCase(exchange.getRequestMethod())) {
                    deleteConversationQuery.set(exchange.getRequestURI().getQuery());
                    exchange.sendResponseHeaders(204, -1);
                    exchange.close();
                    return;
                }
                conversationQuery.set(exchange.getRequestURI().getQuery());
                writeJson(
                    exchange,
                    200,
                    """
                        {
                          "id": "chat-555",
                          "authContext": {
                            "subjectId": "operator@example.com"
                          },
                          "status": "ACTIVE",
                          "createdAt": "2026-03-31T03:00:00",
                          "lastInteractionAt": "2026-03-31T03:01:00",
                          "turns": [
                            {
                              "timestamp": "2026-03-31T03:01:00",
                              "userQuery": "Show me products",
                              "aiResponse": "Here are the products"
                            }
                          ]
                        }
                        """
                );
            });
            server.start();

            DeploymentPocChatService service = serviceFor(server, null, null, "trusted-backend-key");
            authenticateOperator();

            var suggestions = service.suggestions("dep-123", new DeploymentPocChatSuggestionsRequest("catalog", 2));
            JsonNode suggestionsRequestBody = objectMapper.readTree(suggestionsBody.get());
            assertThat(suggestionsRequestBody.path("content").asText()).isEqualTo("catalog");
            assertThat(suggestionsRequestBody.has("userId")).isFalse();
            assertThat(suggestionsTrustedBackendKey.get()).isEqualTo("trusted-backend-key");
            assertThat(suggestions.suggestions()).containsExactly("Summarize catalog", "Explain refund policy");

            var conversation = service.getConversation("dep-123", "chat-555");
            assertThat(conversationQuery.get()).isNull();
            assertThat(conversationTrustedBackendKey.get()).isEqualTo("trusted-backend-key");
            assertThat(conversation.id()).isEqualTo("chat-555");
            assertThat(conversation.subjectId()).isEqualTo("operator@example.com");
            assertThat(conversation.turns()).hasSize(1);
            assertThat(conversation.turns().get(0).aiResponse()).isEqualTo("Here are the products");

            service.deleteConversation("dep-123", "chat-555");
            assertThat(deleteConversationQuery.get()).isNull();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void queryUsesActivePromptSessionWhenRequestPreviewIsAbsent() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        AtomicReference<String> capturedAdminKey = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            server.createContext("/api/chat/me/query", exchange -> {
                capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                capturedAdminKey.set(exchange.getRequestHeaders().getFirst("X-ADMIN-API-KEY"));
                writeJson(
                    exchange,
                    200,
                    """
                        {
                          "success": true,
                          "conversationId": "chat-session",
                          "sessionId": "runtime-session-session",
                          "result": {
                            "type": "INFORMATION_PROVIDED",
                            "success": true,
                            "message": "Session answer",
                            "data": {
                              "answer": "Session answer"
                            }
                          }
                        }
                        """
                );
            });
            server.start();

            ObjectNode sessionPreview = objectMapper.createObjectNode();
            sessionPreview.put("systemPrompt", "Session prompt");
            sessionPreview.put("answerGenerationPrompt", "Keep answers concise.");

            DeploymentPocChatService service = serviceFor(server, "preview-admin-key", sessionPreview, "trusted-backend-key");
            authenticateOperator();

            DeploymentPocChatQueryResponse response = service.query(
                "dep-123",
                new DeploymentPocChatQueryRequest("Use the active session", null, null, null, null)
            );

            JsonNode requestBody = objectMapper.readTree(capturedBody.get());
            assertThat(capturedAdminKey.get()).isEqualTo("preview-admin-key");
            assertThat(requestBody.path("promptPreview").path("systemPrompt").asText()).isEqualTo("Session prompt");
            assertThat(requestBody.path("promptPreview").path("answerGenerationPrompt").asText())
                .isEqualTo("Keep answers concise.");
            assertThat(response.success()).isTrue();
            assertThat(response.conversationId()).isEqualTo("chat-session");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void queryFailsClosedWhenVerifiedRuntimeRouteIsMissing() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        AtomicReference<String> firstTrustedBackendKey = new AtomicReference<>();
        AtomicReference<String> firstBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            server.createContext("/api/chat/me/query", exchange -> {
                int attempt = requestCount.incrementAndGet();
                String trustedBackendKey = exchange.getRequestHeaders().getFirst("X-AIFABRIC-RUNTIME-API-KEY");
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                firstTrustedBackendKey.set(trustedBackendKey);
                firstBody.set(body);
                assertThat(attempt).isEqualTo(1);
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
            });
            server.start();

            DeploymentPocChatService service = serviceFor(server, null, null, "trusted-backend-key");
            authenticateOperator();

            assertThatThrownBy(() -> service.query(
                "dep-123",
                new DeploymentPocChatQueryRequest("Fail closed", null, null, null, null)
            ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("does not expose the verified runtime route '/api/chat/me/query'");
            assertThat(requestCount.get()).isEqualTo(1);
            assertThat(firstTrustedBackendKey.get()).isEqualTo("trusted-backend-key");
            JsonNode firstRequestBody = objectMapper.readTree(firstBody.get());
            assertThat(firstRequestBody.has("userId")).isFalse();
            assertThat(firstRequestBody.has("sessionId")).isFalse();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void queryDoesNotFallBackToLegacyIdentityWhenVerifiedAuthFails() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            server.createContext("/api/chat/me/query", exchange -> {
                requestCount.incrementAndGet();
                exchange.sendResponseHeaders(401, -1);
                exchange.close();
            });
            server.createContext("/api/chat/query", exchange -> {
                fail("Legacy query fallback should not run when verified runtime auth fails.");
            });
            server.start();

            DeploymentPocChatService service = serviceFor(server, null, null, "trusted-backend-key");
            authenticateOperator();

            assertThatThrownBy(() -> service.query(
                "dep-123",
                new DeploymentPocChatQueryRequest("Do not downgrade", null, null, null, null)
            ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Runtime POC chat request failed with HTTP 401.");
            assertThat(requestCount.get()).isEqualTo(1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void authContextUsesVerifiedRuntimeIdentity() throws Exception {
        AtomicReference<String> firstTrustedBackendKey = new AtomicReference<>();
        AtomicReference<String> firstPrivateAuthorization = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            server.createContext("/api/chat/me/auth-context", exchange -> {
                firstTrustedBackendKey.set(exchange.getRequestHeaders().getFirst("X-AIFABRIC-RUNTIME-API-KEY"));
                firstPrivateAuthorization.set(exchange.getRequestHeaders().getFirst("X-AIFABRIC-RUNTIME-AUTHORIZATION"));
                writeJson(
                    exchange,
                    200,
                    """
                        {
                          "subjectId": "operator@example.com",
                          "subjectType": "INTERNAL_PLATFORM_USER",
                          "authMode": "PLATFORM_PROXY_SESSION",
                          "callerType": "PLATFORM_PROXY",
                          "sessionId": "platform-poc-dep-123-session",
                          "deploymentId": "dep-123",
                          "customerId": "cus-123",
                          "tenantId": "ten-123",
                          "issuer": "platform-poc:SESSION",
                          "expiresAt": "2026-04-07T12:15:00Z",
                          "grantedScopes": ["poc:chat", "poc:conversation"],
                          "warnings": []
                        }
                        """
                );
            });
            server.start();

            DeploymentPocChatService service = serviceFor(server, null, null, "trusted-backend-key");
            authenticateOperator();

            DeploymentPocRuntimeAuthContextSummary response = service.getRuntimeAuthContext("dep-123");

            assertThat(firstTrustedBackendKey.get()).isEqualTo("trusted-backend-key");
            Map<String, Object> assertion = decodeAssertionPayload(firstPrivateAuthorization.get());
            assertThat(assertion).containsEntry("sub", "operator@example.com");
            assertThat(assertion).containsEntry("subjectType", "INTERNAL_PLATFORM_USER");
            assertThat(assertion).containsEntry("authMode", "PLATFORM_PROXY_SESSION");
            assertThat(assertion).containsEntry("callerType", "PLATFORM_PROXY");
            assertThat(assertion).containsEntry("deploymentId", "dep-123");
            assertThat(assertion).containsEntry("customerId", "cus-123");
            assertThat(assertion).containsEntry("tenantId", "ten-123");
            assertThat(response.subjectId()).isEqualTo("operator@example.com");
            assertThat(response.subjectType()).isEqualTo("INTERNAL_PLATFORM_USER");
            assertThat(response.authMode()).isEqualTo("PLATFORM_PROXY_SESSION");
            assertThat(response.grantedScopes()).containsExactly("poc:chat", "poc:conversation");
            assertThat(response.warnings()).isEmpty();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void authContextDoesNotFallBackWhenVerifiedAuthFails() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            server.createContext("/api/chat/me/auth-context", exchange -> {
                requestCount.incrementAndGet();
                exchange.sendResponseHeaders(401, -1);
                exchange.close();
            });
            server.createContext("/api/chat/auth-context", exchange -> {
                fail("Legacy auth-context fallback should not run when verified runtime auth fails.");
            });
            server.start();

            DeploymentPocChatService service = serviceFor(server, null, null, "trusted-backend-key");
            authenticateOperator();

            assertThatThrownBy(() -> service.getRuntimeAuthContext("dep-123"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Runtime POC auth context request failed with HTTP 401.");
            assertThat(requestCount.get()).isEqualTo(1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void queryFailsClosedWhenTrustedBackendAuthIsMissing() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            server.start();

            DeploymentPocChatService service = serviceFor(server, null, null, null);
            authenticateOperator();

            assertThatThrownBy(() -> service.query(
                "dep-123",
                new DeploymentPocChatQueryRequest("Fail closed", null, null, null, null)
            ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Secure POC runtime auth is not configured for deployment 'dep-123'");
        } finally {
            server.stop(0);
        }
    }

    private DeploymentPocChatService serviceFor(HttpServer server,
                                                String adminApiKey,
                                                JsonNode sessionPromptPreview,
                                                String runtimeTrustedBackendApiKey) {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentAccessService deploymentAccessService = mock(DeploymentAccessService.class);
        DeploymentPocPromptSessionService deploymentPocPromptSessionService = mock(DeploymentPocPromptSessionService.class);
        PlatformAuditService platformAuditService = mock(PlatformAuditService.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);

        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-123");
        deployment.setCustomerId("cus-123");
        deployment.setTenantId("ten-123");
        deployment.setRuntimeBaseUrl("http://localhost:" + server.getAddress().getPort());

        when(deploymentRepository.findById("dep-123")).thenReturn(Optional.of(deployment));
        when(deploymentAccessService.requireDeploymentOperatorAccess(deployment)).thenReturn(deployment);
        when(platformSecretService.resolveSecret("APP_ADMIN_API_KEY")).thenReturn(adminApiKey);
        when(platformSecretService.resolveSecret("AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY"))
            .thenReturn(runtimeTrustedBackendApiKey);
        when(platformSecretService.resolveSecret("AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY"))
            .thenReturn(runtimeTrustedBackendApiKey == null ? null : "private-assertion-key");
        when(deploymentPocPromptSessionService.effectivePromptPreview("dep-123"))
            .thenReturn(sessionPromptPreview == null ? null : (ObjectNode) sessionPromptPreview);

        return new DeploymentPocChatService(
            deploymentRepository,
            deploymentAccessService,
            deploymentPocPromptSessionService,
            platformAuditService,
            platformSecretService,
            new com.ai.fabric.platform.backend.security.RuntimePrivateAssertionSigningService(
                platformSecretService,
                objectMapper
            ),
            objectMapper
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> decodeAssertionPayload(String authorizationHeader) throws IOException {
        assertThat(authorizationHeader).startsWith("Bearer rpa1.");
        String token = authorizationHeader.substring("Bearer ".length());
        String[] parts = token.split("\\.");
        assertThat(parts).hasSize(3);
        byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
        return objectMapper.readValue(payload, Map.class);
    }

    private void authenticateOperator() {
        PlatformPrincipal principal = new PlatformPrincipal(
            "operator@example.com",
            PlatformRole.PLATFORM_OPERATOR,
            "Operator",
            "SESSION"
        );
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            principal,
            null,
            List.of(new SimpleGrantedAuthority(principal.role().authority()))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static void writeJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }
}
