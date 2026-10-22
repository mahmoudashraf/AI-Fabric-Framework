package com.ai.fabric.runtime;

import ai.fabric.chat.control.ConversationScope;
import ai.fabric.execution.context.ExecutionPrincipalType;
import ai.fabric.execution.context.ExecutionSource;
import ai.fabric.execution.context.TrustedExecutionContext;
import com.ai.fabric.runtime.auth.RuntimeAuthCallerType;
import com.ai.fabric.runtime.auth.RuntimeAuthContext;
import com.ai.fabric.runtime.auth.RuntimeAuthMode;
import com.ai.fabric.runtime.auth.RuntimeAuthSubjectType;
import com.ai.fabric.runtime.auth.RuntimeResolvedIdentity;
import com.ai.fabric.runtime.handover.RuntimeConversationTurn;
import com.ai.fabric.runtime.web.ChatRuntimeController;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

class ChatRuntimeControllerCanonicalPrincipalTest {
    private final ChatRuntimeController controller = mock(ChatRuntimeController.class, CALLS_REAL_METHODS);

    @Test
    void verifiedAnonymousOwnerRemainsAnonymousInCanonicalExecution() {
        var trusted = context(identity(RuntimeAuthSubjectType.ANONYMOUS_SESSION, RuntimeAuthCallerType.PUBLIC_BROWSER),
            turn("tenant-1", "dep-1", "session-1"));

        assertThat(trusted.initiator().principalType()).isEqualTo(ExecutionPrincipalType.ANONYMOUS_SESSION);
        assertThat(trusted.initiator().principalId()).isEqualTo("session-1");
        assertThat(trusted.isAuthenticated()).isFalse();
        assertThat(trusted.source()).isEqualTo(ExecutionSource.INTERACTIVE);
        assertThat(trusted.subject().subjectType()).isEqualTo("conversation");
        assertThat(trusted.subject().subjectId()).isEqualTo("chat-1");
        assertThat(trusted.tenantId()).isEqualTo("tenant-1");
        assertThat(trusted.deploymentId()).isEqualTo("dep-1");
        assertThat(trusted.grantedScopes()).containsExactly("chat:query");
    }

    @Test
    void signedInBrowserRetainsEndUserPrincipal() {
        var trusted = context(identity(RuntimeAuthSubjectType.END_USER, RuntimeAuthCallerType.PUBLIC_BROWSER),
            turn("tenant-1", "dep-1", "session-1"));

        assertThat(trusted.initiator().principalType()).isEqualTo(ExecutionPrincipalType.END_USER);
        assertThat(trusted.isAuthenticated()).isTrue();
    }

    @Test
    void verifiedBackendRetainsServicePrincipal() {
        var trusted = context(identity(RuntimeAuthSubjectType.END_USER, RuntimeAuthCallerType.TRUSTED_BACKEND),
            turn("tenant-1", "dep-1", "session-1"));

        assertThat(trusted.initiator().principalType()).isEqualTo(ExecutionPrincipalType.SERVICE);
        assertThat(trusted.source()).isEqualTo(ExecutionSource.APPLICATION);
        assertThat(trusted.isAuthenticated()).isTrue();
    }

    @Test
    void anonymousSubjectCannotAcquireMachineAuthority() {
        assertThatThrownBy(() -> context(
            identity(RuntimeAuthSubjectType.ANONYMOUS_SESSION, RuntimeAuthCallerType.TRUSTED_BACKEND),
            turn("tenant-1", "dep-1", "session-1")))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403 FORBIDDEN");
    }

    @Test
    void canonicalPrincipalStillRequiresExactConversationOwnershipAndScope() {
        var identity = identity(RuntimeAuthSubjectType.ANONYMOUS_SESSION, RuntimeAuthCallerType.PUBLIC_BROWSER);
        for (var turn : List.of(turn("other-tenant", "dep-1", "session-1"),
            turn("tenant-1", "other-deployment", "session-1"), turn("tenant-1", "dep-1", "other-owner"))) {
            assertThatThrownBy(() -> context(identity, turn))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403 FORBIDDEN");
        }
    }

    @Test
    void missingCanonicalTurnCannotFallBackToLegacyAuthority() {
        assertThatThrownBy(() -> context(
            identity(RuntimeAuthSubjectType.ANONYMOUS_SESSION, RuntimeAuthCallerType.PUBLIC_BROWSER), null))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403 FORBIDDEN");
    }

    private TrustedExecutionContext context(RuntimeResolvedIdentity identity, RuntimeConversationTurn turn) {
        return ReflectionTestUtils.invokeMethod(controller, "trustedConversationContext", identity, turn);
    }

    private RuntimeResolvedIdentity identity(RuntimeAuthSubjectType type, RuntimeAuthCallerType caller) {
        return RuntimeResolvedIdentity.builder().authContext(RuntimeAuthContext.builder()
            .subjectId("session-1").sessionId("session-1").subjectType(type).callerType(caller)
            .authMode(type == RuntimeAuthSubjectType.ANONYMOUS_SESSION
                ? RuntimeAuthMode.PUBLIC_RUNTIME_ANONYMOUS : RuntimeAuthMode.PUBLIC_RUNTIME_AUTHENTICATED)
            .tenantId("tenant-1").deploymentId("dep-1").grantedScopes(List.of("chat:query")).build()).build();
    }

    private RuntimeConversationTurn turn(String tenant, String deployment, String owner) {
        return new RuntimeConversationTurn(new ConversationScope(tenant, deployment, "chat-1", owner), null, null, null);
    }
}
