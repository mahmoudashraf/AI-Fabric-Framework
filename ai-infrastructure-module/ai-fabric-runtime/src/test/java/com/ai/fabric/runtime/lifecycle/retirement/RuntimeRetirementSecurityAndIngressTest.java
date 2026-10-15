package com.ai.fabric.runtime.lifecycle.retirement;

import static com.ai.fabric.runtime.lifecycle.retirement.RuntimeTargetRetirementContracts.*;
import static com.ai.fabric.runtime.lifecycle.retirement.RuntimeRetirementTestFixture.*;
import static org.assertj.core.api.Assertions.*;

import com.ai.fabric.runtime.auth.*;
import com.ai.fabric.runtime.config.RuntimeAuthProperties;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

class RuntimeRetirementSecurityAndIngressTest {
    @Test
    void requiresApiKeyPlusSignedPrivateServerAssertionExactScopesAndMatchingTenant() {
        var f = new RuntimeRetirementTestFixture();
        var properties = properties();
        var controller = new RuntimeTargetRetirementController(new RuntimeRequestAuthResolver(properties),
            f.identity, f.service, org.mockito.Mockito.mock(RuntimeTargetIndexRetirementService.class));
        var missing = new MockHttpServletRequest();
        missing.addHeader("X-AIFABRIC-RUNTIME-API-KEY", "runtime-secret");
        assertThatThrownBy(() -> controller.status(TARGET, missing)).hasMessageContaining("Private runtime auth context is required");
        var publicOnly = new MockHttpServletRequest();
        publicOnly.addHeader("Authorization", "Bearer public-token");
        assertThatThrownBy(() -> controller.status(TARGET, publicOnly)).hasMessageContaining("Private runtime auth context is required");
        assertThatThrownBy(() -> controller.status(TARGET, request(properties, RuntimeAuthSubjectType.END_USER, "tenant-1", RuntimeTargetRetirementController.READ_SCOPE)))
            .hasMessageContaining("PRIVATE_RETIREMENT_SERVER_SCOPE_REQUIRED");
        assertThatThrownBy(() -> controller.status(TARGET, request(properties, RuntimeAuthSubjectType.TRUSTED_BACKEND, "other", RuntimeTargetRetirementController.READ_SCOPE)))
            .hasMessageContaining("PRIVATE_RETIREMENT_SERVER_SCOPE_REQUIRED");
        assertThatThrownBy(() -> controller.command(f.command(1, 0, CommandType.BEGIN_DRAIN),
            request(properties, RuntimeAuthSubjectType.TRUSTED_BACKEND, "tenant-1", RuntimeTargetRetirementController.READ_SCOPE)))
            .hasMessageContaining("missing required scope");
        assertThat(controller.command(f.command(1, 0, CommandType.BEGIN_DRAIN),
            request(properties, RuntimeAuthSubjectType.SYSTEM_PROCESS, "tenant-1", RuntimeTargetRetirementController.WRITE_SCOPE)).getBody().current().state())
            .isEqualTo(State.DRAINING);
    }

    @Test
    void ingressRejectsEveryUnlistedMutationButPermitsReadbackAndDeclaredContinuations() {
        var f = new RuntimeRetirementTestFixture();
        f.service.command(f.command(1, 0, CommandType.BEGIN_DRAIN));
        var gate = new RuntimeRetirementIngressConfiguration.DrainIngress(f.service);
        var response = new MockHttpServletResponse();
        for (String route : List.of("/api/documents/sources", "/api/documents/sources/{sourceId}/index",
            "/api/internal/integrations/data-sync/batch", "/api/private/ingestion/data-sync/batch",
            "/api/admin/connector/integrations/sources/{sourceId}/reconcile",
            "/api/admin/connector/integrations/webhooks/{sourceId}/events/{eventId}/replay",
            "/api/public/chat/session", "/api/agentic/v1/executions", "/api/unknown/new-work")) {
            assertThatThrownBy(() -> gate.preHandle(route("POST", route), response, new Object())).hasMessageContaining("NEW_WORK_BLOCKED");
        }
        for (String route : List.of("/api/reviews/v1/tasks/{taskId}/decisions", "/api/public/chat/session/renew",
            "/api/private/conversation-channel-dispatches/{dispatchId}/outcomes/{attempt}")) {
            var request = route("POST", route);
            assertThat(gate.preHandle(request, response, new Object())).isTrue();
            gate.afterCompletion(request, response, new Object(), null);
        }
        assertThat(gate.preHandle(route("POST", "/api/private/runtime-target-retirement/snapshots"), response, new Object())).isTrue();
        assertThat(gate.preHandle(route("GET", "/api/documents/sources"), response, new Object())).isTrue();
        assertThat(f.service.snapshot(f.binding(1)).inFlightRequests()).isZero();
    }

    @Test
    void matchingConfigAutomaticallyEnablesFeatureAndRejectsPartialIdentity() {
        assertThat(new RuntimeTargetRetirementIdentity(null, "dep-123", "staging", "release-1", "tenant-1", true).enabled()).isTrue();
        assertThatThrownBy(() -> new RuntimeTargetRetirementIdentity(null, "dep-123", "staging", "", "tenant-1", true))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void springResolvesExactProviderEnvironmentPropertyNames() {
        try (var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("provider",
                java.util.Map.of("AI_FABRIC_RUNTIME_DEPLOYMENT_ID", "dep-123", "AI_FABRIC_RUNTIME_TENANT_ID", "tenant-1",
                    "LOOMAI_RUNTIME_TARGET_PROFILE_ID", "staging", "LOOMAI_RUNTIME_RELEASE_ID", "release-1")));
            context.register(RuntimeTargetRetirementIdentity.class);
            context.refresh();
            assertThat(context.getBean(RuntimeTargetRetirementIdentity.class).target()).isEqualTo(TARGET);
            assertThat(context.getBean(RuntimeTargetRetirementIdentity.class).enabled()).isTrue();
        }
    }

    private RuntimeAuthProperties properties() {
        var properties = new RuntimeAuthProperties();
        properties.getIngress().getTrustedBackend().setApiKeyValue("runtime-secret");
        properties.getIngress().getPrivateAssertions().setSigningKey("retirement-private-signing-secret");
        properties.getIngress().setAcceptedIssuers(List.of("platform-retirement"));
        properties.getIngress().setAcceptedAudiences(List.of("dep-123"));
        return properties;
    }
    private MockHttpServletRequest request(RuntimeAuthProperties properties, RuntimeAuthSubjectType type, String tenant, String scope) {
        RuntimeAuthContext context = RuntimeAuthContext.builder().subjectId("platform-retirement-worker").subjectType(type)
            .callerType(RuntimeAuthCallerType.TRUSTED_BACKEND).authMode(RuntimeAuthMode.PRIVATE_RUNTIME_BACKEND_MEDIATED)
            .deploymentId("dep-123").tenantId(tenant).issuer("platform-retirement").audiences(List.of("dep-123"))
            .grantedScopes(List.of(scope)).expiresAt(Instant.now().plusSeconds(60)).build();
        var request = new MockHttpServletRequest();
        request.addHeader("X-AIFABRIC-RUNTIME-API-KEY", "runtime-secret");
        request.addHeader("X-AIFABRIC-RUNTIME-AUTHORIZATION", "Bearer " + new RuntimePrivateAssertionService(properties).issueAssertion(context));
        return request;
    }
    private MockHttpServletRequest route(String method, String path) {
        var request = new MockHttpServletRequest(method, path);
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, path);
        return request;
    }
}
