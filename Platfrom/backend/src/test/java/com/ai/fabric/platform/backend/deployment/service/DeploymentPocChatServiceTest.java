package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPocChatQueryRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPocChatQueryResponse;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPocChatSuggestionsRequest;
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

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
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
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            server.createContext("/api/chat/query", exchange -> {
                capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                capturedAdminKey.set(exchange.getRequestHeaders().getFirst("X-ADMIN-API-KEY"));
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

            DeploymentPocChatService service = serviceFor(server, null, null);
            authenticateOperator();

            DeploymentPocChatQueryResponse response = service.query(
                "dep-123",
                new DeploymentPocChatQueryRequest("What can you do?", null, null, null, null)
            );

            JsonNode requestBody = objectMapper.readTree(capturedBody.get());
            assertThat(requestBody.path("query").asText()).isEqualTo("What can you do?");
            assertThat(requestBody.path("userId").asText()).startsWith("platform-poc-dep-123-");
            assertThat(requestBody.path("sessionId").asText()).startsWith("poc-session-platform-poc-dep-123-");
            assertThat(requestBody.path("promptPreview").isMissingNode()).isTrue();
            assertThat(capturedAdminKey.get()).isNull();
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
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            server.createContext("/api/chat/query", exchange -> {
                capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                capturedAdminKey.set(exchange.getRequestHeaders().getFirst("X-ADMIN-API-KEY"));
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

            DeploymentPocChatService service = serviceFor(server, "preview-admin-key", null);
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
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            server.createContext("/api/chat/suggestions", exchange -> writeJson(
                exchange,
                200,
                """
                    {
                      "success": true,
                      "suggestions": ["Summarize catalog", "Explain refund policy"],
                      "raw": null
                    }
                    """
            ));
            server.createContext("/api/chat/conversations/chat-555", exchange -> {
                if ("DELETE".equalsIgnoreCase(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(204, -1);
                    exchange.close();
                    return;
                }
                writeJson(
                    exchange,
                    200,
                    """
                        {
                          "id": "chat-555",
                          "ownerId": "owner-1",
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

            DeploymentPocChatService service = serviceFor(server, null, null);
            authenticateOperator();

            var suggestions = service.suggestions("dep-123", new DeploymentPocChatSuggestionsRequest("catalog", 2));
            assertThat(suggestions.suggestions()).containsExactly("Summarize catalog", "Explain refund policy");

            var conversation = service.getConversation("dep-123", "chat-555");
            assertThat(conversation.id()).isEqualTo("chat-555");
            assertThat(conversation.turns()).hasSize(1);
            assertThat(conversation.turns().get(0).aiResponse()).isEqualTo("Here are the products");

            service.deleteConversation("dep-123", "chat-555");
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
            server.createContext("/api/chat/query", exchange -> {
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

            DeploymentPocChatService service = serviceFor(server, "preview-admin-key", sessionPreview);
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

    private DeploymentPocChatService serviceFor(HttpServer server, String adminApiKey, JsonNode sessionPromptPreview) {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentAccessService deploymentAccessService = mock(DeploymentAccessService.class);
        DeploymentPocPromptSessionService deploymentPocPromptSessionService = mock(DeploymentPocPromptSessionService.class);
        PlatformAuditService platformAuditService = mock(PlatformAuditService.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);

        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-123");
        deployment.setRuntimeBaseUrl("http://localhost:" + server.getAddress().getPort());

        when(deploymentRepository.findById("dep-123")).thenReturn(Optional.of(deployment));
        when(deploymentAccessService.requireDeploymentAccess(deployment)).thenReturn(deployment);
        when(platformSecretService.resolveSecret("APP_ADMIN_API_KEY")).thenReturn(adminApiKey);
        when(deploymentPocPromptSessionService.effectivePromptPreview("dep-123"))
            .thenReturn(sessionPromptPreview == null ? null : (ObjectNode) sessionPromptPreview);

        return new DeploymentPocChatService(
            deploymentRepository,
            deploymentAccessService,
            deploymentPocPromptSessionService,
            platformAuditService,
            platformSecretService,
            objectMapper
        );
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
