package com.ai.fabric.platform.backend.assistant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ResponseStatusException;

class AuthoringToolAuthenticationTest {
    final AuthoringProperties properties = new AuthoringProperties();
    final PlatformSecretService secrets = mock(PlatformSecretService.class);
    final AuthoringBrainBindingService bindings = mock(AuthoringBrainBindingService.class);
    final AuthoringToolAuthentication authentication = new AuthoringToolAuthentication(properties, secrets, bindings);
    AuthoringToolAuthenticationTest() {
        properties.setEnabled(true); properties.setConfiguredBrainEnabled(true);
        when(secrets.resolveSecret(properties.getApiKeySecretRef())).thenReturn("local-tool-test-secret");
        when(bindings.identity("release")).thenReturn(new com.ai.fabric.platform.backend.deployment.authority.RuntimeAuthorityBinding(
            properties.getCustomerId(), properties.getTenantId(), properties.getDeploymentId(), "target", "version", "hash", 1,
            "frozen-issuer", "frozen-audience", "public-issuer", "public-audience", false, false, List.of(), java.util.Map.of()));
    }
    HttpHeaders headers() {
        var headers = new HttpHeaders(); headers.set(AuthoringToolAuthentication.KEY_HEADER, "local-tool-test-secret");
        headers.set(AuthoringToolAuthentication.RELEASE_HEADER, "release");
        headers.set("X-AIFABRIC-AUTH-SUBJECT-ID", "authoring:session");
        headers.set("X-AIFABRIC-AUTH-SUBJECT-TYPE", "END_USER");
        headers.set("X-AIFABRIC-AUTH-MODE", "PRIVATE_RUNTIME_BACKEND_MEDIATED");
        headers.set("X-AIFABRIC-AUTH-CALLER-TYPE", "TRUSTED_BACKEND");
        headers.set("X-AIFABRIC-AUTH-DEPLOYMENT-ID", properties.getDeploymentId());
        headers.set("X-AIFABRIC-AUTH-CUSTOMER-ID", properties.getCustomerId());
        headers.set("X-AIFABRIC-AUTH-TENANT-ID", properties.getTenantId());
        headers.set("X-AIFABRIC-AUTH-ISSUER", "frozen-issuer");
        headers.set("X-AIFABRIC-AUTH-AUDIENCES", "frozen-audience");
        headers.set("X-AIFABRIC-AUTH-SCOPES", "configuration:author,chat:query");
        headers.set("X-AIFABRIC-AUTH-EXPIRES-AT", Instant.now().plusSeconds(60).toString());
        return headers;
    }
    @Test void exactDedicatedIdentityCreatesOnlyTheBoundedCallerReference() {
        assertThat(authentication.verify(headers(), "session", "turn"))
            .isEqualTo(new AuthoringDraftOperations.Caller("session", "turn", properties.getDeploymentId(), "release"));
    }
    @Test void customerOrAdminCookiesWithoutServiceProofCannotGrantToolAccess() {
        assertThatThrownBy(() -> authentication.verify(new HttpHeaders(), "session", "turn"))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("ACCESS_DENIED");
    }
    @Test void everyIdentityBoundaryIsRequiredAndCannotBeSwapped() {
        for (String name : List.of("X-AIFABRIC-AUTH-SUBJECT-ID", "X-AIFABRIC-AUTH-SUBJECT-TYPE", "X-AIFABRIC-AUTH-MODE",
            "X-AIFABRIC-AUTH-CALLER-TYPE", "X-AIFABRIC-AUTH-DEPLOYMENT-ID", "X-AIFABRIC-AUTH-CUSTOMER-ID",
            "X-AIFABRIC-AUTH-TENANT-ID", "X-AIFABRIC-AUTH-ISSUER", "X-AIFABRIC-AUTH-AUDIENCES", "X-AIFABRIC-AUTH-SCOPES")) {
            var headers = headers(); headers.set(name, "other");
            assertThatThrownBy(() -> authentication.verify(headers, "session", "turn")).hasMessageContaining("ACCESS_DENIED");
        }
    }
    @Test void duplicateKeysAndExpiredAssertionsFailClosed() {
        var duplicate = headers(); duplicate.add(AuthoringToolAuthentication.KEY_HEADER, "local-tool-test-secret");
        assertThatThrownBy(() -> authentication.verify(duplicate, "session", "turn")).hasMessageContaining("ACCESS_DENIED");
        var expired = headers(); expired.set("X-AIFABRIC-AUTH-EXPIRES-AT", Instant.now().minusSeconds(1).toString());
        assertThatThrownBy(() -> authentication.verify(expired, "session", "turn")).hasMessageContaining("ACCESS_DENIED");
    }
    @Test void featureRemainsUnavailableUntilExplicitlyActivated() {
        properties.setConfiguredBrainEnabled(false);
        assertThatThrownBy(() -> authentication.verify(headers(), "session", "turn")).satisfies(failure ->
            assertThat(((ResponseStatusException) failure).getStatusCode().value()).isEqualTo(404));
        verify(secrets, never()).resolveSecret(anyString());
    }
    @Test void authoringScopeDoesNotImplyAnIndividualToolPermission() {
        assertThatThrownBy(() -> authentication.verify(headers(), "session", "turn", "authoring_update_draft"))
            .hasMessageContaining("ACCESS_DENIED");
        var authorized = headers(); authorized.set("X-AIFABRIC-AUTH-SCOPES", "configuration:author,action:authoring_get_context");
        assertThat(authentication.verify(authorized, "session", "turn", "authoring_get_context").action()).isEqualTo("authoring_get_context");
        assertThatThrownBy(() -> authentication.verify(authorized, "session", "turn", "authoring_update_draft"))
            .hasMessageContaining("ACCESS_DENIED");
    }
    @Test void contextSourcePreviewsRequireTheSeparateSelectedSourceReadScope() {
        var scoped = headers(); scoped.set("X-AIFABRIC-AUTH-SCOPES", "configuration:author,action:authoring_get_context");
        assertThat(authentication.verify(scoped, "session", "turn", "authoring_get_context").selectedSourceReadAllowed()).isFalse();
        scoped.set("X-AIFABRIC-AUTH-SCOPES", "configuration:author,action:authoring_get_context,action:authoring_read_selected_sources");
        assertThat(authentication.verify(scoped, "session", "turn", "authoring_get_context").selectedSourceReadAllowed()).isTrue();
    }
    @Test void revokedPublishedAuthorityHasNoLegacyIdentityFallback() {
        when(bindings.identity("release")).thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "AUTHORING_MANAGED_BRAIN_UNAVAILABLE"));
        assertThatThrownBy(() -> authentication.verify(headers(), "session", "turn")).hasMessageContaining("MANAGED_BRAIN_UNAVAILABLE");
    }
}
