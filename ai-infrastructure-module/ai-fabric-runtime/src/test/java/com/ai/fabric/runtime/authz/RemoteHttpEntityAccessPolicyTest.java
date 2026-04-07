package com.ai.fabric.runtime.authz;

import com.ai.fabric.runtime.config.RuntimeAuthzProperties;
import com.ai.infrastructure.intent.orchestration.OrchestrationContextMetadataKeys;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RemoteHttpEntityAccessPolicyTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void forwardsVerifiedAuthContextFieldsToRemoteAuthzEndpoint() throws Exception {
        AtomicReference<JsonNode> observedRequest = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/authz/check", exchange -> {
            observedRequest.set(OBJECT_MAPPER.readTree(exchange.getRequestBody()));
            writeJson(exchange, 200, "{\"granted\":true}");
        });
        server.start();

        RemoteHttpEntityAccessPolicy policy = policy();

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("sessionId", "platform-session-1");
        metadata.put(OrchestrationContextMetadataKeys.SUBJECT_ID, "platform-user-1");
        metadata.put(OrchestrationContextMetadataKeys.SUBJECT_TYPE, "INTERNAL_PLATFORM_USER");
        metadata.put(OrchestrationContextMetadataKeys.AUTH_MODE, "PLATFORM_PROXY_SESSION");
        metadata.put(OrchestrationContextMetadataKeys.CALLER_TYPE, "PLATFORM_PROXY");
        metadata.put(OrchestrationContextMetadataKeys.AUTH_ISSUER, "platform-ui");
        metadata.put(OrchestrationContextMetadataKeys.AUTH_AUDIENCES, List.of("runtime-public", "runtime-admin"));
        metadata.put(OrchestrationContextMetadataKeys.AUTH_EXPIRES_AT, "2026-04-07T12:00:00Z");
        metadata.put(OrchestrationContextMetadataKeys.DEPLOYMENT_ID, "dep-123");
        metadata.put(OrchestrationContextMetadataKeys.CUSTOMER_ID, "cus-123");
        metadata.put(OrchestrationContextMetadataKeys.TENANT_ID, "ten-123");
        metadata.put(OrchestrationContextMetadataKeys.GRANTED_SCOPES, List.of("chat:read", "chat:write"));
        metadata.put(OrchestrationContextMetadataKeys.REQUESTED_SCOPES, List.of("chat:query"));
        metadata.put("entryPoint", "RAG_ORCHESTRATOR");
        metadata.put("authenticated", true);
        metadata.put("ipAddress", "203.0.113.10");

        Map<String, Object> entity = new LinkedHashMap<>();
        entity.put("requestId", "req-1");
        entity.put("resourceId", "rag:intent");
        entity.put("operationType", "READ");
        entity.put("context", "customer asks for an order update");
        entity.put("purpose", "chat-orchestration");
        entity.put("timestamp", "2026-04-07T12:05:00Z");
        entity.put("metadata", metadata);

        boolean granted = policy.canUserAccessEntity("platform-user-1", entity);

        assertThat(granted).isTrue();
        JsonNode request = observedRequest.get();
        assertThat(request).isNotNull();
        assertThat(request.path("contractVersion").asText()).isEqualTo("AUTH_CONTEXT_V1");
        assertThat(request.path("userId").asText()).isEqualTo("platform-user-1");
        assertThat(request.path("subjectId").asText()).isEqualTo("platform-user-1");
        assertThat(request.path("subjectType").asText()).isEqualTo("INTERNAL_PLATFORM_USER");
        assertThat(request.path("authMode").asText()).isEqualTo("PLATFORM_PROXY_SESSION");
        assertThat(request.path("callerType").asText()).isEqualTo("PLATFORM_PROXY");
        assertThat(request.path("deploymentId").asText()).isEqualTo("dep-123");
        assertThat(request.path("customerId").asText()).isEqualTo("cus-123");
        assertThat(request.path("tenantId").asText()).isEqualTo("ten-123");
        assertThat(request.path("issuer").asText()).isEqualTo("platform-ui");
        assertThat(request.path("grantedScopes")).hasSize(2);
        assertThat(request.path("requestedScopes")).hasSize(1);
        assertThat(request.path("requestedScopes").get(0).asText()).isEqualTo("chat:query");
        assertThat(request.path("requestContext").path("context").asText()).isEqualTo("customer asks for an order update");
        assertThat(request.path("requestContext").path("purpose").asText()).isEqualTo("chat-orchestration");
        assertThat(request.path("requestContext").path("timestamp").asText()).isEqualTo("2026-04-07T12:05:00Z");
        assertThat(request.path("requestContext").path("entryPoint").asText()).isEqualTo("RAG_ORCHESTRATOR");
        assertThat(request.path("requestContext").path("authenticated").asBoolean()).isTrue();
        assertThat(request.path("requestContext").path("ipAddress").asText()).isEqualTo("203.0.113.10");
        assertThat(request.path("compatibilityAliases").path("userId").asText()).isEqualTo("platform-user-1");
        assertThat(request.path("compatibilityAliases").path("sessionId").asText()).isEqualTo("platform-session-1");
        assertThat(request.path("metadata").path("subjectId").asText()).isEqualTo("platform-user-1");
        assertThat(request.path("authContext").path("subjectId").asText()).isEqualTo("platform-user-1");
        assertThat(request.path("authContext").path("subjectType").asText()).isEqualTo("INTERNAL_PLATFORM_USER");
        assertThat(request.path("authContext").path("authMode").asText()).isEqualTo("PLATFORM_PROXY_SESSION");
        assertThat(request.path("authContext").path("callerType").asText()).isEqualTo("PLATFORM_PROXY");
        assertThat(request.path("authContext").path("sessionId").asText()).isEqualTo("platform-session-1");
        assertThat(request.path("authContext").path("deploymentId").asText()).isEqualTo("dep-123");
        assertThat(request.path("authContext").path("customerId").asText()).isEqualTo("cus-123");
        assertThat(request.path("authContext").path("tenantId").asText()).isEqualTo("ten-123");
        assertThat(request.path("authContext").path("issuer").asText()).isEqualTo("platform-ui");
        assertThat(request.path("authContext").path("grantedScopes")).hasSize(2);
        assertThat(request.path("authContext").path("audiences")).hasSize(2);
        assertThat(request.path("authContext").path("expiresAt").asText()).isEqualTo("2026-04-07T12:00:00Z");
    }

    @Test
    void fallsBackToVerifiedSubjectIdWhenAnonymousCallerHasNoUserId() throws Exception {
        AtomicReference<JsonNode> observedRequest = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/authz/check", exchange -> {
            observedRequest.set(OBJECT_MAPPER.readTree(exchange.getRequestBody()));
            writeJson(exchange, 200, "{\"granted\":true}");
        });
        server.start();

        RemoteHttpEntityAccessPolicy policy = policy();

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("sessionId", "anon-public-session");
        metadata.put(OrchestrationContextMetadataKeys.SUBJECT_ID, "anon-public-session");
        metadata.put(OrchestrationContextMetadataKeys.SUBJECT_TYPE, "ANONYMOUS_SESSION");
        metadata.put(OrchestrationContextMetadataKeys.AUTH_MODE, "PUBLIC_RUNTIME_ANONYMOUS");
        metadata.put(OrchestrationContextMetadataKeys.REQUESTED_SCOPES, List.of("chat:query"));

        Map<String, Object> entity = new LinkedHashMap<>();
        entity.put("requestId", "req-2");
        entity.put("resourceId", "rag:intent");
        entity.put("operationType", "READ");
        entity.put("metadata", metadata);

        boolean granted = policy.canUserAccessEntity(null, entity);

        assertThat(granted).isTrue();
        JsonNode request = observedRequest.get();
        assertThat(request).isNotNull();
        assertThat(request.path("contractVersion").asText()).isEqualTo("AUTH_CONTEXT_V1");
        assertThat(request.path("userId").asText()).isEqualTo("anon-public-session");
        assertThat(request.path("subjectId").asText()).isEqualTo("anon-public-session");
        assertThat(request.path("subjectType").asText()).isEqualTo("ANONYMOUS_SESSION");
        assertThat(request.path("authMode").asText()).isEqualTo("PUBLIC_RUNTIME_ANONYMOUS");
        assertThat(request.path("sessionId").asText()).isEqualTo("anon-public-session");
        assertThat(request.path("requestedScopes")).hasSize(1);
        assertThat(request.path("requestedScopes").get(0).asText()).isEqualTo("chat:query");
        assertThat(request.path("compatibilityAliases").path("userId").asText()).isEqualTo("anon-public-session");
        assertThat(request.path("compatibilityAliases").path("sessionId").asText()).isEqualTo("anon-public-session");
        assertThat(request.path("authContext").path("subjectId").asText()).isEqualTo("anon-public-session");
        assertThat(request.path("authContext").path("sessionId").asText()).isEqualTo("anon-public-session");
        assertThat(request.path("authContext").path("subjectType").asText()).isEqualTo("ANONYMOUS_SESSION");
        assertThat(request.path("authContext").path("authMode").asText()).isEqualTo("PUBLIC_RUNTIME_ANONYMOUS");
    }

    private RuntimeAuthzProperties authzProperties() {
        RuntimeAuthzProperties properties = new RuntimeAuthzProperties();
        properties.getRemote().setBaseUrl("http://localhost:" + server.getAddress().getPort());
        return properties;
    }

    private RemoteHttpEntityAccessPolicy policy() {
        return new RemoteHttpEntityAccessPolicy(authzProperties(), OBJECT_MAPPER);
    }

    private void writeJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }
}
