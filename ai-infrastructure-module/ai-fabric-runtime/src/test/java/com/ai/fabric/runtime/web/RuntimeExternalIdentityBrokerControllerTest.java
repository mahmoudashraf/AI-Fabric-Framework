package com.ai.fabric.runtime.web;

import com.ai.fabric.runtime.auth.RuntimeAuthCallerType;
import com.ai.fabric.runtime.auth.RuntimeAuthContext;
import com.ai.fabric.runtime.auth.RuntimeAuthMode;
import com.ai.fabric.runtime.auth.RuntimeAuthSubjectType;
import com.ai.fabric.runtime.auth.RuntimeExternalIdentityBrokerService;
import com.ai.fabric.runtime.auth.RuntimePublicTokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RuntimeExternalIdentityBrokerControllerTest {

    @Test
    void returnsNoStoreAuthenticatedRuntimeTokenContract() {
        RuntimeExternalIdentityBrokerService broker = mock(RuntimeExternalIdentityBrokerService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        Instant expiresAt = Instant.parse("2030-01-01T00:00:00Z");
        RuntimeAuthContext context = RuntimeAuthContext.builder()
            .subjectId("user-1")
            .subjectType(RuntimeAuthSubjectType.END_USER)
            .authMode(RuntimeAuthMode.PUBLIC_RUNTIME_AUTHENTICATED)
            .callerType(RuntimeAuthCallerType.PUBLIC_BROWSER)
            .expiresAt(expiresAt)
            .grantedScopes(List.of("chat:query"))
            .build();
        when(broker.authenticate(request, "Bearer provider-token")).thenReturn(
            new RuntimeExternalIdentityBrokerService.BrokeredIdentity(
                new RuntimePublicTokenService.IssuedPublicRuntimeToken("runtime-token-value", context),
                "member@example.test"));

        var response = new RuntimeExternalIdentityBrokerController(broker).runtimeToken(
            "Bearer provider-token",
            new RuntimeExternalIdentityBrokerController.RuntimeTokenRequest(
                "loomai-workspace-runtime-token-request-v1"),
            request);

        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().schemaVersion()).isEqualTo("loomai-workspace-runtime-token-v1");
        assertThat(response.getBody().accessToken()).isEqualTo("runtime-token-value");
        assertThat(response.getBody().subjectId()).isEqualTo("user-1");
        assertThat(response.getBody().email()).isEqualTo("member@example.test");
    }

    @Test
    void rejectsUnknownRequestSchemaBeforeCallingBroker() {
        RuntimeExternalIdentityBrokerService broker = mock(RuntimeExternalIdentityBrokerService.class);
        RuntimeExternalIdentityBrokerController controller = new RuntimeExternalIdentityBrokerController(broker);

        assertThatThrownBy(() -> controller.runtimeToken(
            "Bearer provider-token",
            new RuntimeExternalIdentityBrokerController.RuntimeTokenRequest("unsupported"),
            mock(HttpServletRequest.class)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("invalid");
    }
}
