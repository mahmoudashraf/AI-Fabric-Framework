package com.ai.fabric.platform.backend.deployment.dependency;

import com.ai.fabric.platform.backend.deployment.service.DeploymentRuntimeJsonTransport;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
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
                return response("READY", "init-generation-1", 0);
            });
        stubCurrent("init-generation-1");

        verifier.initializeAndVerify(scope, "scoped-seed-sync-canary/v1", binding, "init-generation-1");

        verify(transport).send(eq("https://receiver.example"), eq("POST"),
            eq(HttpManagedDependencyInitializationVerifier.PATH),
            eq(Map.of(HttpManagedDependencyInitializationVerifier.HEADER, "s".repeat(32))),
            any(), eq("init-generation-1"));
        verify(transport).send(eq(binding.baseUrl()), eq("GET"), startsWith(HttpManagedDependencyInitializationVerifier.STATUS_PATH),
            eq(Map.of(HttpManagedDependencyInitializationVerifier.HEADER, "s".repeat(32), "Cache-Control", "no-cache")),
            isNull(), isNull());
    }

    @Test void missingManagementSecretAndForeignReadbackFailClosed() {
        when(secrets.resolveSecret("managed-secret-ref")).thenReturn(null);
        assertThatThrownBy(() -> verifier.initializeAndVerify(scope,
            "scoped-seed-sync-canary/v1", binding, "init-generation-1"))
            .hasMessageContaining("DEPENDENCY_MANAGEMENT_SECRET_MISSING");

        when(secrets.resolveSecret("managed-secret-ref")).thenReturn("s".repeat(32));
        when(transport.send(anyString(), anyString(), anyString(), anyMap(), any(), anyString()))
            .thenReturn(response("READY", "foreign", 0));
        assertThatThrownBy(() -> verifier.initializeAndVerify(scope,
            "scoped-seed-sync-canary/v1", binding, "init-generation-1"))
            .hasMessageContaining("DEPENDENCY_INITIALIZATION_READBACK_INVALID");
    }

    @Test void unusedRequiresZeroAuthoritativeActiveWork() {
        when(secrets.resolveSecret("managed-secret-ref")).thenReturn("s".repeat(32));
        when(transport.send(anyString(), anyString(), anyString(), anyMap(), any(), anyString()))
            .thenReturn(response("UNUSED", "retire:receiver", 1));

        assertThatThrownBy(() -> verifier.requireUnused(scope, "scoped-seed-sync-canary/v1",
            binding, "retire")).hasMessageContaining("DEPENDENCY_INITIALIZATION_READBACK_INVALID");
    }

    @Test void operatorRemovalRequiresTheOldCredentialToBeRejectedByTheLiveInbox() {
        when(secrets.resolveSecret("managed-secret-ref")).thenReturn("s".repeat(32));
        when(secrets.resolveSecret("RETIRED_OPERATOR")).thenReturn("r".repeat(43));
        when(transport.send(eq(binding.baseUrl()), eq("POST"), anyString(), anyMap(), any(), anyString()))
            .thenReturn(response("READY", "revoke:lifecycle", 0));
        when(transport.send(eq(binding.baseUrl()), eq("GET"), eq("/api/operator/me"), anyMap(), isNull(), isNull()))
            .thenReturn(new DeploymentRuntimeJsonTransport.Response(200, mapper.createObjectNode().put("operatorId", "old")))
            .thenReturn(new DeploymentRuntimeJsonTransport.Response(401, mapper.createObjectNode().put("code", "OPERATOR_AUTH_INVALID")));

        assertThatThrownBy(() -> verifier.verifyOperatorRevocation(scope, "inbox-callback-canary/v1", binding,
            java.util.List.of("RETIRED_OPERATOR"), "revoke", false)).hasMessageContaining("INBOX_OPERATOR_REVOCATION_UNCONFIRMED");
        verifier.verifyOperatorRevocation(scope, "inbox-callback-canary/v1", binding,
            java.util.List.of("RETIRED_OPERATOR"), "revoke", false);
        verify(transport, times(2)).send(eq(binding.baseUrl()), eq("GET"), eq("/api/operator/me"),
            eq(Map.of("X-LoomAI-Operator-Key", "r".repeat(43))), isNull(), isNull());
        verify(transport, never()).send(anyString(), eq("POST"), anyString(), anyMap(), any(), any());
    }

    @Test void lastOperatorRemovalRequiresAnExactDisabledLifecycleReadback() {
        when(secrets.resolveSecret("managed-secret-ref")).thenReturn("s".repeat(32));
        when(transport.send(eq(binding.baseUrl()), eq("POST"), anyString(), anyMap(), any(), anyString()))
            .thenReturn(response("DISABLED", "revoke-last:lifecycle", 0));
        stubCurrent("DISABLED", "revoke-last:lifecycle", 0);
        verifier.verifyOperatorRevocation(scope, "inbox-callback-canary/v1", binding,
            java.util.List.of("RETIRED_OPERATOR"), "revoke-last", true);
        verify(secrets, never()).clearManagedSecret(anyString(), anyMap());
        verify(transport).send(eq(binding.baseUrl()), eq("GET"), startsWith(HttpManagedDependencyInitializationVerifier.STATUS_PATH),
            anyMap(), isNull(), isNull());
        verify(transport, never()).send(anyString(), eq("GET"), eq("/api/operator/me"), anyMap(), any(), any());
    }

    @Test void callerSuppliedGenerationKeyMakesInboxInitializationFreshAndReplayable() {
        when(secrets.resolveSecret("managed-secret-ref")).thenReturn("s".repeat(32));
        var operationKeys = new java.util.ArrayList<String>();
        var generation = new AtomicReference<String>();
        when(transport.send(eq(binding.baseUrl()), eq("POST"), anyString(), anyMap(), any(), anyString()))
            .thenAnswer(call -> {
                String key = call.getArgument(5); operationKeys.add(key); generation.set(key);
                return response("READY", key, 0);
            });
        var challenges = new java.util.ArrayList<String>();
        when(transport.send(eq(binding.baseUrl()), eq("GET"), startsWith(HttpManagedDependencyInitializationVerifier.STATUS_PATH),
            anyMap(), isNull(), isNull())).thenAnswer(call -> {
                String challenge = challenge(call.getArgument(2)); challenges.add(challenge);
                return current(response("READY", generation.get(), 0).body(), challenge);
            });
        verifier.initializeAndVerify(scope, "inbox-callback-canary/v1", binding, "init-generation-1");
        verifier.initializeAndVerify(scope, "inbox-callback-canary/v1", binding, "init-generation-1");
        verifier.initializeAndVerify(scope, "inbox-callback-canary/v1", binding, "init-generation-2");
        org.assertj.core.api.Assertions.assertThat(operationKeys)
            .containsExactly("init-generation-1", "init-generation-1", "init-generation-2");
        assertThat(challenges).hasSize(3).doesNotHaveDuplicates();
    }

    @Test void oldInitializationReceiptRemainsReplayableWhenCurrentGenerationIsFreshAndReady() {
        when(secrets.resolveSecret("managed-secret-ref")).thenReturn("s".repeat(32));
        var receipt = response("READY", "init", 0);
        ((com.fasterxml.jackson.databind.node.ObjectNode) receipt.body()).put("replay", true)
            .put("observedAt", Instant.now().minusSeconds(3600).toString());
        when(transport.send(eq(binding.baseUrl()), eq("POST"), anyString(), anyMap(), any(), anyString()))
            .thenReturn(receipt);
        stubCurrent("init");

        verifier.initializeAndVerify(scope, "inbox-callback-canary/v1", binding, "init");

        verify(transport).send(eq(binding.baseUrl()), eq("GET"), startsWith(HttpManagedDependencyInitializationVerifier.STATUS_PATH),
            anyMap(), isNull(), isNull());
    }

    @Test void cachedReadyReceiptCannotHideCurrentlyDisabledDependency() {
        when(secrets.resolveSecret("managed-secret-ref")).thenReturn("s".repeat(32));
        var receipt = response("READY", "init", 0);
        ((com.fasterxml.jackson.databind.node.ObjectNode) receipt.body()).put("replay", true)
            .put("observedAt", Instant.now().minusSeconds(3600).toString());
        when(transport.send(eq(binding.baseUrl()), eq("POST"), anyString(), anyMap(), any(), anyString()))
            .thenReturn(receipt);
        when(transport.send(eq(binding.baseUrl()), eq("GET"), startsWith(HttpManagedDependencyInitializationVerifier.STATUS_PATH),
            anyMap(), isNull(), isNull())).thenAnswer(call ->
                current(response("DISABLED", "disable", 0).body(), challenge(call.getArgument(2))));

        assertThatThrownBy(() -> verifier.initializeAndVerify(scope, "inbox-callback-canary/v1", binding, "init"))
            .hasMessageContaining("DEPENDENCY_CURRENT_READBACK_INVALID");
    }

    @Test void cachedDisableReceiptCannotHideAReenabledDependency() {
        when(secrets.resolveSecret("managed-secret-ref")).thenReturn("s".repeat(32));
        var receipt = response("DISABLED", "graph:receiver:disable", 0);
        ((com.fasterxml.jackson.databind.node.ObjectNode) receipt.body()).put("replay", true)
            .put("observedAt", Instant.now().minusSeconds(3600).toString());
        when(transport.send(eq(binding.baseUrl()), eq("POST"), anyString(), anyMap(), any(), anyString()))
            .thenReturn(receipt);
        stubCurrent("READY", "new-initialization", 0);

        assertThatThrownBy(() -> verifier.disableAndVerify(scope, "scoped-seed-sync-canary/v1", binding))
            .hasMessageContaining("DEPENDENCY_CURRENT_READBACK_INVALID");
    }

    @Test void cachedUnusedReceiptCannotHideNewActiveWork() {
        when(secrets.resolveSecret("managed-secret-ref")).thenReturn("s".repeat(32));
        var receipt = response("UNUSED", "retire:receiver", 0);
        ((com.fasterxml.jackson.databind.node.ObjectNode) receipt.body()).put("replay", true)
            .put("observedAt", Instant.now().minusSeconds(3600).toString());
        when(transport.send(eq(binding.baseUrl()), eq("POST"), anyString(), anyMap(), any(), anyString()))
            .thenReturn(receipt);
        stubCurrent("UNUSED", "retire:receiver", 1);

        assertThatThrownBy(() -> verifier.requireUnused(scope, "scoped-seed-sync-canary/v1", binding, "retire"))
            .hasMessageContaining("DEPENDENCY_CURRENT_READBACK_INVALID");
    }

    @Test void disableAndUnusedRequireFreshCurrentStateForTheirExactOperationKey() {
        when(secrets.resolveSecret("managed-secret-ref")).thenReturn("s".repeat(32));
        when(transport.send(eq(binding.baseUrl()), eq("POST"), anyString(), anyMap(), any(), anyString()))
            .thenReturn(response("DISABLED", "graph:receiver:disable", 0));
        stubCurrent("DISABLED", "graph:receiver:disable", 0);
        verifier.disableAndVerify(scope, "scoped-seed-sync-canary/v1", binding);

        when(transport.send(eq(binding.baseUrl()), eq("POST"), anyString(), anyMap(), any(), anyString()))
            .thenReturn(response("UNUSED", "retire:receiver", 0));
        stubCurrent("UNUSED", "retire:receiver", 0);
        verifier.requireUnused(scope, "scoped-seed-sync-canary/v1", binding, "retire");

        verify(transport, times(2)).send(eq(binding.baseUrl()), eq("GET"),
            startsWith(HttpManagedDependencyInitializationVerifier.STATUS_PATH), anyMap(), isNull(), isNull());
    }

    @Test void unusedRequiresExplicitTypedCurrentWorkAndReplayFields() {
        when(secrets.resolveSecret("managed-secret-ref")).thenReturn("s".repeat(32));
        when(transport.send(eq(binding.baseUrl()), eq("POST"), anyString(), anyMap(), any(), anyString()))
            .thenReturn(response("UNUSED", "retire:receiver", 0));
        for (String missing : java.util.List.of("activeWork", "replay")) {
            when(transport.send(eq(binding.baseUrl()), eq("GET"), startsWith(HttpManagedDependencyInitializationVerifier.STATUS_PATH),
                anyMap(), isNull(), isNull())).thenAnswer(call -> {
                    var body = (com.fasterxml.jackson.databind.node.ObjectNode) response("UNUSED", "retire:receiver", 0).body();
                    body.remove(missing);
                    return current(body, challenge(call.getArgument(2)));
                });
            assertThatThrownBy(() -> verifier.requireUnused(scope, "scoped-seed-sync-canary/v1", binding, "retire"))
                .as("missing %s", missing).hasMessageContaining("DEPENDENCY_CURRENT_READBACK_INVALID");
        }
        when(transport.send(eq(binding.baseUrl()), eq("GET"), startsWith(HttpManagedDependencyInitializationVerifier.STATUS_PATH),
            anyMap(), isNull(), isNull())).thenAnswer(call -> {
                var body = (com.fasterxml.jackson.databind.node.ObjectNode) response("UNUSED", "retire:receiver", 0).body();
                body.put("activeWork", "0");
                return current(body, challenge(call.getArgument(2)));
            });
        assertThatThrownBy(() -> verifier.requireUnused(scope, "scoped-seed-sync-canary/v1", binding, "retire"))
            .hasMessageContaining("DEPENDENCY_CURRENT_READBACK_INVALID");
    }

    @Test void currentReadbackRejectsChangedBindingGenerationStaleFutureAndReplayResponses() {
        when(secrets.resolveSecret("managed-secret-ref")).thenReturn("s".repeat(32));
        when(transport.send(eq(binding.baseUrl()), eq("POST"), anyString(), anyMap(), any(), anyString()))
            .thenAnswer(call -> response("READY", "init", 0));
        for (var invalid : Map.<String, Object>of(
            "tenantId", "foreign-tenant", "bindingId", "foreign-binding", "operationKey", "other-generation",
            "replay", true, "observedAt", Instant.now().minusSeconds(31).toString()).entrySet()) {
            when(transport.send(eq(binding.baseUrl()), eq("GET"), startsWith(HttpManagedDependencyInitializationVerifier.STATUS_PATH),
                anyMap(), isNull(), isNull())).thenAnswer(call -> {
                    var body = (com.fasterxml.jackson.databind.node.ObjectNode) response("READY", "init", 0).body();
                    body.set(invalid.getKey(), mapper.valueToTree(invalid.getValue()));
                    return current(body, challenge(call.getArgument(2)));
                });
            assertThatThrownBy(() -> verifier.initializeAndVerify(scope, "inbox-callback-canary/v1", binding, "init"))
                .as(invalid.getKey()).hasMessageContaining("DEPENDENCY_CURRENT_READBACK_INVALID");
        }
        when(transport.send(eq(binding.baseUrl()), eq("GET"), startsWith(HttpManagedDependencyInitializationVerifier.STATUS_PATH),
            anyMap(), isNull(), isNull())).thenAnswer(call -> {
                var body = (com.fasterxml.jackson.databind.node.ObjectNode) response("READY", "init", 0).body();
                body.put("observedAt", Instant.now().plusSeconds(60).toString());
                return current(body, challenge(call.getArgument(2)));
            });
        assertThatThrownBy(() -> verifier.initializeAndVerify(scope, "inbox-callback-canary/v1", binding, "init"))
            .hasMessageContaining("DEPENDENCY_CURRENT_READBACK_INVALID");
    }

    @Test void currentReadbackRequiresTheFreshChallengeAndSuccessfulAuthentication() {
        when(secrets.resolveSecret("managed-secret-ref")).thenReturn("s".repeat(32));
        when(transport.send(eq(binding.baseUrl()), eq("POST"), anyString(), anyMap(), any(), anyString()))
            .thenAnswer(call -> response("READY", "init", 0));
        when(transport.send(eq(binding.baseUrl()), eq("GET"), startsWith(HttpManagedDependencyInitializationVerifier.STATUS_PATH),
            anyMap(), isNull(), isNull())).thenAnswer(call -> current(response("READY", "init", 0).body(), "old-challenge"));
        assertThatThrownBy(() -> verifier.initializeAndVerify(scope, "inbox-callback-canary/v1", binding, "init"))
            .hasMessageContaining("DEPENDENCY_CURRENT_READBACK_INVALID");
        when(transport.send(eq(binding.baseUrl()), eq("GET"), startsWith(HttpManagedDependencyInitializationVerifier.STATUS_PATH),
            anyMap(), isNull(), isNull())).thenReturn(new DeploymentRuntimeJsonTransport.Response(401, mapper.createObjectNode()));
        assertThatThrownBy(() -> verifier.initializeAndVerify(scope, "inbox-callback-canary/v1", binding, "init"))
            .hasMessageContaining("DEPENDENCY_CURRENT_READBACK_REJECTED");
    }

    private void stubCurrent(String operationKey) {
        stubCurrent("READY", operationKey, 0);
    }

    private void stubCurrent(String state, String operationKey, long activeWork) {
        when(transport.send(eq(binding.baseUrl()), eq("GET"), startsWith(HttpManagedDependencyInitializationVerifier.STATUS_PATH),
            anyMap(), isNull(), isNull())).thenAnswer(call ->
                current(response(state, operationKey, activeWork).body(), challenge(call.getArgument(2))));
    }

    private String challenge(String path) {
        return path.substring((HttpManagedDependencyInitializationVerifier.STATUS_PATH + "?challenge=").length());
    }

    private DeploymentRuntimeJsonTransport.Response current(com.fasterxml.jackson.databind.JsonNode body, String challenge) {
        var result = mapper.createObjectNode().put("challenge", challenge);
        result.set("current", body);
        return new DeploymentRuntimeJsonTransport.Response(200, result);
    }

    private DeploymentRuntimeJsonTransport.Response response(String state, String operationKey, long activeWork) {
        var body = mapper.valueToTree(new HttpManagedDependencyInitializationVerifier.ManagedReadback(
            HttpManagedDependencyInitializationVerifier.VERSION, state, "customer", "tenant", "deployment",
            "staging", "version", "receiver", "service-ref", "account", "binding", operationKey,
            activeWork, Instant.now(), "dependency-evidence", false));
        return new DeploymentRuntimeJsonTransport.Response(200, body);
    }
}
