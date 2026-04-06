package com.ai.fabric.runtime.authz;

import com.ai.fabric.runtime.config.RuntimeAuthzProperties;
import com.ai.infrastructure.intent.action.connector.AIActionConnectorProperties;
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

        RemoteHttpEntityAccessPolicy policy = new RemoteHttpEntityAccessPolicy(
            authzProperties(),
            connectorProperties(),
            OBJECT_MAPPER
        );

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("sessionId", "platform-session-1");
        metadata.put(OrchestrationContextMetadataKeys.SUBJECT_ID, "platform-user-1");
        metadata.put(OrchestrationContextMetadataKeys.SUBJECT_TYPE, "INTERNAL_PLATFORM_USER");
        metadata.put(OrchestrationContextMetadataKeys.AUTH_MODE, "PLATFORM_PROXY_SESSION");
        metadata.put(OrchestrationContextMetadataKeys.CALLER_TYPE, "PLATFORM_PROXY");
        metadata.put(OrchestrationContextMetadataKeys.AUTH_ISSUER, "platform-ui");
        metadata.put(OrchestrationContextMetadataKeys.DEPLOYMENT_ID, "dep-123");
        metadata.put(OrchestrationContextMetadataKeys.CUSTOMER_ID, "cus-123");
        metadata.put(OrchestrationContextMetadataKeys.TENANT_ID, "ten-123");
        metadata.put(OrchestrationContextMetadataKeys.GRANTED_SCOPES, List.of("chat:read", "chat:write"));

        Map<String, Object> entity = new LinkedHashMap<>();
        entity.put("requestId", "req-1");
        entity.put("resourceId", "rag:intent");
        entity.put("operationType", "READ");
        entity.put("metadata", metadata);

        boolean granted = policy.canUserAccessEntity("platform-user-1", entity);

        assertThat(granted).isTrue();
        JsonNode request = observedRequest.get();
        assertThat(request).isNotNull();
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
        assertThat(request.path("metadata").path("subjectId").asText()).isEqualTo("platform-user-1");
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

        RemoteHttpEntityAccessPolicy policy = new RemoteHttpEntityAccessPolicy(
            authzProperties(),
            connectorProperties(),
            OBJECT_MAPPER
        );

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("sessionId", "anon-public-session");
        metadata.put(OrchestrationContextMetadataKeys.SUBJECT_ID, "anon-public-session");
        metadata.put(OrchestrationContextMetadataKeys.SUBJECT_TYPE, "ANONYMOUS_SESSION");
        metadata.put(OrchestrationContextMetadataKeys.AUTH_MODE, "PUBLIC_RUNTIME_ANONYMOUS");

        Map<String, Object> entity = new LinkedHashMap<>();
        entity.put("requestId", "req-2");
        entity.put("resourceId", "rag:intent");
        entity.put("operationType", "READ");
        entity.put("metadata", metadata);

        boolean granted = policy.canUserAccessEntity(null, entity);

        assertThat(granted).isTrue();
        JsonNode request = observedRequest.get();
        assertThat(request).isNotNull();
        assertThat(request.path("userId").asText()).isEqualTo("anon-public-session");
        assertThat(request.path("subjectId").asText()).isEqualTo("anon-public-session");
        assertThat(request.path("subjectType").asText()).isEqualTo("ANONYMOUS_SESSION");
        assertThat(request.path("authMode").asText()).isEqualTo("PUBLIC_RUNTIME_ANONYMOUS");
        assertThat(request.path("sessionId").asText()).isEqualTo("anon-public-session");
    }

    private RuntimeAuthzProperties authzProperties() {
        RuntimeAuthzProperties properties = new RuntimeAuthzProperties();
        properties.getRemote().setBaseUrl("http://localhost:" + server.getAddress().getPort());
        return properties;
    }

    private AIActionConnectorProperties connectorProperties() {
        return new AIActionConnectorProperties();
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
