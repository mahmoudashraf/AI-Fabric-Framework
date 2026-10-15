package com.ai.fabric.platform.backend.deployment.dependency;

import com.ai.fabric.platform.backend.deployment.service.DeploymentRuntimeJsonTransport;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HttpManagedDependencyInitializationVerifierTest {
    private final DeploymentRuntimeJsonTransport transport = mock(DeploymentRuntimeJsonTransport.class);
    private final PlatformSecretService secrets = mock(PlatformSecretService.class);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final HttpManagedDependencyInitializationVerifier verifier =
        new HttpManagedDependencyInitializationVerifier(transport, secrets, mapper);
    private final ManagedDependencyPrerequisites.Scope scope = new ManagedDependencyPrerequisites.Scope(
        "graph", "customer", "tenant", "deployment", "staging", "version",
        "a".repeat(64), "b".repeat(64));
    private final ManagedDependencyPrerequisites.Binding binding = new ManagedDependencyPrerequisites.Binding(
        "receiver", "service-ref", "https://receiver.example", "account", "binding",
        Map.of("management", "managed-secret-ref"));

    @Test void exactReadyReadbackPassesWithoutExposingTheSecretInThePayload() {
        when(secrets.resolveSecret("managed-secret-ref")).thenReturn("s".repeat(32));
        when(transport.send(eq("https://receiver.example"), eq("POST"), anyString(), anyMap(), any(), anyString()))
            .thenAnswer(call -> {
                var body = call.getArgument(4, com.fasterxml.jackson.databind.JsonNode.class);
                org.assertj.core.api.Assertions.assertThat(body.toString()).doesNotContain("s".repeat(32));
                return response("READY", "graph:receiver:initialize", 0);
            });

        verifier.initializeAndVerify(scope, "scoped-seed-sync-canary/v1", binding);

        verify(transport).send(eq("https://receiver.example"), eq("POST"),
            eq(HttpManagedDependencyInitializationVerifier.PATH),
            eq(Map.of(HttpManagedDependencyInitializationVerifier.HEADER, "s".repeat(32))),
            any(), eq("graph:receiver:initialize"));
    }

    @Test void missingManagementSecretAndForeignReadbackFailClosed() {
        when(secrets.resolveSecret("managed-secret-ref")).thenReturn(null);
        assertThatThrownBy(() -> verifier.initializeAndVerify(scope,
            "scoped-seed-sync-canary/v1", binding)).hasMessageContaining("DEPENDENCY_MANAGEMENT_SECRET_MISSING");

        when(secrets.resolveSecret("managed-secret-ref")).thenReturn("s".repeat(32));
        when(transport.send(anyString(), anyString(), anyString(), anyMap(), any(), anyString()))
            .thenReturn(response("READY", "foreign", 0));
        assertThatThrownBy(() -> verifier.initializeAndVerify(scope,
            "scoped-seed-sync-canary/v1", binding)).hasMessageContaining("DEPENDENCY_INITIALIZATION_READBACK_INVALID");
    }

    @Test void unusedRequiresZeroAuthoritativeActiveWork() {
        when(secrets.resolveSecret("managed-secret-ref")).thenReturn("s".repeat(32));
        when(transport.send(anyString(), anyString(), anyString(), anyMap(), any(), anyString()))
            .thenReturn(response("UNUSED", "retire:receiver", 1));

        assertThatThrownBy(() -> verifier.requireUnused(scope, "scoped-seed-sync-canary/v1",
            binding, "retire")).hasMessageContaining("DEPENDENCY_INITIALIZATION_READBACK_INVALID");
    }

    private DeploymentRuntimeJsonTransport.Response response(String state, String operationKey, long activeWork) {
        var body = mapper.valueToTree(new HttpManagedDependencyInitializationVerifier.ManagedReadback(
            HttpManagedDependencyInitializationVerifier.VERSION, state, "customer", "tenant", "deployment",
            "staging", "version", "receiver", "service-ref", "account", "binding", operationKey,
            activeWork, Instant.now(), "dependency-evidence", false));
        return new DeploymentRuntimeJsonTransport.Response(200, body);
    }
}
