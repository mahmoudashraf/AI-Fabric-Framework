package com.ai.fabric.runtime.authz;

import ai.fabric.dto.AIAccessSubjectContext;
import ai.fabric.execution.context.ExecutionPrincipal;
import ai.fabric.execution.context.ExecutionPrincipalType;
import ai.fabric.execution.context.ExecutionSource;
import ai.fabric.execution.context.ExecutionSubjectRef;
import ai.fabric.execution.context.TrustedExecutionContext;
import ai.fabric.intent.orchestration.OrchestrationAuthContextResolver;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AllowVerifiedEntityAccessPolicyTest {

    private final AllowVerifiedEntityAccessPolicy policy = new AllowVerifiedEntityAccessPolicy();

    @Test
    void grantsVerifiedNonAnonymousSubject() {
        AIAccessSubjectContext authContext = AIAccessSubjectContext.builder()
            .subjectId("user-1")
            .subjectType("END_USER")
            .authMode("PLATFORM_PROXY_SESSION")
            .build();

        assertThat(policy.canAccess(authContext, Map.of("resourceId", "rag:intent"))).isTrue();
    }

    @Test
    void deniesAnonymousSubject() {
        AIAccessSubjectContext authContext = verifiedAnonymousSubject();

        assertThat(policy.canAccess(authContext, Map.of("resourceId", "rag:intent"))).isFalse();
    }

    @Test
    void grantsExplicitlyEnabledVerifiedAnonymousSubject() {
        AllowVerifiedEntityAccessPolicy anonymousPolicy = new AllowVerifiedEntityAccessPolicy(true);

        assertThat(anonymousPolicy.canAccess(
            verifiedAnonymousSubject(),
            Map.of("resourceId", "rag:intent")
        )).isTrue();
    }

    @Test
    void deniesEnabledAnonymousSubjectWithoutQueryScope() {
        AllowVerifiedEntityAccessPolicy anonymousPolicy = new AllowVerifiedEntityAccessPolicy(true);
        AIAccessSubjectContext authContext = verifiedAnonymousSubject();
        authContext.setGrantedScopes(List.of("chat:conversations"));

        assertThat(anonymousPolicy.canAccess(authContext, Map.of("resourceId", "rag:intent"))).isFalse();
    }

    @Test
    void deniesEnabledAnonymousSubjectWithIncompleteOrExpiredContract() {
        AllowVerifiedEntityAccessPolicy anonymousPolicy = new AllowVerifiedEntityAccessPolicy(true);
        AIAccessSubjectContext missingTenant = verifiedAnonymousSubject();
        missingTenant.setTenantId(null);
        AIAccessSubjectContext wrongCaller = verifiedAnonymousSubject();
        wrongCaller.setCallerType("TRUSTED_BACKEND");
        AIAccessSubjectContext mismatchedSession = verifiedAnonymousSubject();
        mismatchedSession.setSessionId("session-2");
        AIAccessSubjectContext expired = verifiedAnonymousSubject();
        expired.setExpiresAt(Instant.now().minusSeconds(60).toString());

        assertThat(anonymousPolicy.canAccess(missingTenant, Map.of("resourceId", "rag:intent"))).isFalse();
        assertThat(anonymousPolicy.canAccess(wrongCaller, Map.of("resourceId", "rag:intent"))).isFalse();
        assertThat(anonymousPolicy.canAccess(mismatchedSession, Map.of("resourceId", "rag:intent"))).isFalse();
        assertThat(anonymousPolicy.canAccess(expired, Map.of("resourceId", "rag:intent"))).isFalse();
    }

    @Test
    void deniesMissingVerifiedContextMetadata() {
        AIAccessSubjectContext authContext = AIAccessSubjectContext.builder()
            .subjectId("user-1")
            .subjectType("END_USER")
            .build();

        assertThat(policy.canAccess(authContext, Map.of("resourceId", "rag:intent"))).isFalse();
    }

    @Test
    void canonicalAnonymousOwnerStillRequiresDeploymentOptIn() {
        var auth = canonicalAnonymousSubject();

        assertThat(policy.canAccess(auth, Map.of("resourceId", "rag:intent"))).isFalse();
        assertThat(new AllowVerifiedEntityAccessPolicy(true)
            .canAccess(auth, Map.of("resourceId", "rag:intent"))).isTrue();
        assertThat(auth.getSubjectId()).isEqualTo("session-1");
        assertThat(auth.getIssuer()).isNull();
        assertThat(auth.getExpiresAt()).isNull();
    }

    @Test
    void canonicalAnonymousContextRequiresExactSessionScopeAndQueryPermission() {
        var anonymousPolicy = new AllowVerifiedEntityAccessPolicy(true);
        var missingTenant = canonicalAnonymousSubject(); missingTenant.setTenantId(null);
        var missingDeployment = canonicalAnonymousSubject(); missingDeployment.setDeploymentId(null);
        var wrongSession = canonicalAnonymousSubject(); wrongSession.setSessionId("other-session");
        var wrongCaller = canonicalAnonymousSubject(); wrongCaller.setCallerType("PUBLIC_BROWSER");
        var missingScope = canonicalAnonymousSubject(); missingScope.setGrantedScopes(List.of("chat:conversations"));
        var machine = canonicalAnonymousSubject(); machine.setAuthMode("TRUSTED_APPLICATION");

        for (var invalid : List.of(missingTenant, missingDeployment, wrongSession, wrongCaller, missingScope, machine)) {
            assertThat(anonymousPolicy.canAccess(invalid, Map.of("resourceId", "rag:intent"))).isFalse();
        }
    }

    private AIAccessSubjectContext canonicalAnonymousSubject() {
        return OrchestrationAuthContextResolver.from(new TrustedExecutionContext(
            new ExecutionPrincipal("session-1", ExecutionPrincipalType.ANONYMOUS_SESSION),
            new ExecutionSubjectRef("conversation", "conversation-1"), ExecutionSource.INTERACTIVE,
            "tenant-1", "dep-1", Set.of("chat:query"), null, Instant.now()
        ));
    }

    private AIAccessSubjectContext verifiedAnonymousSubject() {
        return AIAccessSubjectContext.builder()
            .subjectId("session-1")
            .sessionId("session-1")
            .subjectType("ANONYMOUS_SESSION")
            .authMode("PUBLIC_RUNTIME_ANONYMOUS")
            .callerType("PUBLIC_BROWSER")
            .deploymentId("dep-1")
            .tenantId("tenant-1")
            .issuer("runtime-public-bootstrap")
            .audiences(List.of("dep-1"))
            .grantedScopes(List.of("chat:query", "chat:conversations"))
            .expiresAt(Instant.now().plusSeconds(300).toString())
            .build();
    }
}
