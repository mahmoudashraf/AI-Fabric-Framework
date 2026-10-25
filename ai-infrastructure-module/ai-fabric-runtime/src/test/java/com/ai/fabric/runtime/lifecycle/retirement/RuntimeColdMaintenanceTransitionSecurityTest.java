package com.ai.fabric.runtime.lifecycle.retirement;

import static com.ai.fabric.runtime.lifecycle.retirement.RuntimeTargetRetirementContracts.*;
import static com.ai.fabric.runtime.lifecycle.retirement.RuntimeRetirementTestFixture.TARGET;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.ai.fabric.runtime.auth.*;
import com.ai.fabric.runtime.config.RuntimeAuthProperties;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

class RuntimeColdMaintenanceTransitionSecurityTest {
    private final RuntimeAuthProperties properties = properties();
    private final RuntimeTargetRetirementService service = mock(RuntimeTargetRetirementService.class);
    private final RuntimeTargetRetirementController controller = new RuntimeTargetRetirementController(
        new RuntimeRequestAuthResolver(properties),
        new RuntimeTargetRetirementIdentity(true, TARGET.deploymentId(), TARGET.targetProfileId(), TARGET.releaseId(), "tenant-1", true),
        service, mock(RuntimeTargetIndexRetirementService.class));
    private final ColdMaintenanceTransition command = new ColdMaintenanceTransition(null, "old", "a".repeat(64), "review:1", true);

    @Test
    void authorizedObsoleteColdMaintenanceIsGoneAndErrorRemainsNoStore() {
        var retired = new ResponseStatusException(HttpStatus.GONE, "RUNTIME_GENERATION_TRANSITION_RETIRED_RELEASE_SCOPED_GATES");
        when(service.coldMaintenanceTransition(command)).thenThrow(retired);
        assertThatThrownBy(() -> controller.coldMaintenanceTransition(command, request(RuntimeAuthSubjectType.SYSTEM_PROCESS,
            RuntimeAuthCallerType.TRUSTED_BACKEND, "dep-123", "tenant-1", RuntimeTargetRetirementController.COLD_MAINTENANCE_WRITE_SCOPE)))
            .isSameAs(retired);
        var response = controller.lifecycleError(retired);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        verify(service).coldMaintenanceTransition(command);
    }

    @Test
    void ordinaryWriteEndUserAndTrustedBackendSubjectCannotInvokeColdMaintenance() {
        assertForbidden(request(RuntimeAuthSubjectType.SYSTEM_PROCESS, RuntimeAuthCallerType.TRUSTED_BACKEND,
            "dep-123", "tenant-1", RuntimeTargetRetirementController.WRITE_SCOPE));
        assertForbidden(request(RuntimeAuthSubjectType.END_USER, RuntimeAuthCallerType.TRUSTED_BACKEND,
            "dep-123", "tenant-1", RuntimeTargetRetirementController.COLD_MAINTENANCE_WRITE_SCOPE));
        assertForbidden(request(RuntimeAuthSubjectType.TRUSTED_BACKEND, RuntimeAuthCallerType.TRUSTED_BACKEND,
            "dep-123", "tenant-1", RuntimeTargetRetirementController.COLD_MAINTENANCE_WRITE_SCOPE));
        verifyNoInteractions(service);
    }

    @Test
    void publicAndUnverifiedRequestsAreForbiddenBeforeService() {
        var publicRequest = new MockHttpServletRequest();
        publicRequest.addHeader("Authorization", "Bearer public-token");
        assertForbidden(publicRequest);
        var keyOnly = new MockHttpServletRequest();
        keyOnly.addHeader("X-AIFABRIC-RUNTIME-API-KEY", "runtime-secret");
        assertForbidden(keyOnly);
        verifyNoInteractions(service);
    }

    @Test
    void foreignDeploymentOrTenantCannotInvokeColdMaintenance() {
        assertForbidden(request(RuntimeAuthSubjectType.SYSTEM_PROCESS, RuntimeAuthCallerType.TRUSTED_BACKEND,
            "other", "tenant-1", RuntimeTargetRetirementController.COLD_MAINTENANCE_WRITE_SCOPE));
        assertForbidden(request(RuntimeAuthSubjectType.SYSTEM_PROCESS, RuntimeAuthCallerType.TRUSTED_BACKEND,
            "dep-123", "other", RuntimeTargetRetirementController.COLD_MAINTENANCE_WRITE_SCOPE));
        verifyNoInteractions(service);
    }

    @Test
    void obsoleteNormalGenerationTransitionStillRequiresWriteAuthorityButIsGone() {
        var transition = new GenerationTransition(TARGET, new Target("dep-123", "staging", "release-2"),
            "tenant-1", 1, "a".repeat(64), "b".repeat(64), "generation-2", "c".repeat(64), "stop:1");
        when(service.transition(transition)).thenThrow(new ResponseStatusException(HttpStatus.GONE, "RUNTIME_GENERATION_TRANSITION_RETIRED_RELEASE_SCOPED_GATES"));
        assertThatThrownBy(() -> controller.transition(transition, request(RuntimeAuthSubjectType.TRUSTED_BACKEND, RuntimeAuthCallerType.TRUSTED_BACKEND,
            "dep-123", "tenant-1", RuntimeTargetRetirementController.WRITE_SCOPE))).isInstanceOfSatisfying(ResponseStatusException.class,
                failure -> assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.GONE));
        verify(service).transition(transition);
        verify(service, never()).coldMaintenanceTransition(any());
    }

    @Test
    void namedDrainErrorsAreStructuredAndUnsafeDetailsAreNotExposed() {
        var pending = controller.lifecycleError(new ResponseStatusException(HttpStatus.CONFLICT, "DRAIN_NOT_PROVED_COMPLETE"));
        assertThat(pending.getBody().code()).isEqualTo("DRAIN_NOT_PROVED_COMPLETE");
        var unsafe = controller.lifecycleError(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unexpected secret: token-value"));
        assertThat(unsafe.getBody().code()).isEqualTo("RUNTIME_LIFECYCLE_REQUEST_REJECTED");
    }

    private void assertForbidden(MockHttpServletRequest request) {
        assertThatThrownBy(() -> controller.coldMaintenanceTransition(command, request))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    private RuntimeAuthProperties properties() {
        var p = new RuntimeAuthProperties();
        p.getIngress().getTrustedBackend().setApiKeyValue("runtime-secret");
        p.getIngress().getPrivateAssertions().setSigningKey("cold-maintenance-private-signing-test-secret");
        p.getIngress().setAcceptedIssuers(List.of("platform-cold-maintenance"));
        return p;
    }

    private MockHttpServletRequest request(RuntimeAuthSubjectType subject, RuntimeAuthCallerType caller,
        String deploymentId, String tenantId, String scope) {
        RuntimeAuthContext context = RuntimeAuthContext.builder().subjectId("claimed-admin-maintenance-worker")
            .subjectType(subject).callerType(caller).authMode(RuntimeAuthMode.PRIVATE_RUNTIME_BACKEND_MEDIATED)
            .deploymentId(deploymentId).tenantId(tenantId).issuer("platform-cold-maintenance")
            .audiences(List.of(deploymentId)).grantedScopes(List.of(scope)).expiresAt(Instant.now().plusSeconds(60)).build();
        var request = new MockHttpServletRequest("POST", "/api/private/runtime-target-retirement/cold-maintenance-generation-transitions");
        request.addHeader("X-AIFABRIC-RUNTIME-API-KEY", "runtime-secret");
        request.addHeader("X-AIFABRIC-RUNTIME-AUTHORIZATION", "Bearer " + new RuntimePrivateAssertionService(properties).issueAssertion(context));
        return request;
    }
}
