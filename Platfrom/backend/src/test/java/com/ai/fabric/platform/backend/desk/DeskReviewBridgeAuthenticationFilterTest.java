package com.ai.fabric.platform.backend.desk;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.security.service.CustomerSupabaseIdentityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

class DeskReviewBridgeAuthenticationFilterTest {
    final DeskActivationService activations = mock(DeskActivationService.class);
    final CustomerSupabaseIdentityService identities = mock(CustomerSupabaseIdentityService.class);
    final DeskServiceClient desk = mock(DeskServiceClient.class);
    final DeploymentTargetProfileRepository targets = mock(DeploymentTargetProfileRepository.class);
    final ObjectMapper mapper = new ObjectMapper();
    final DeskReviewBridgeAuthenticationFilter filter = new DeskReviewBridgeAuthenticationFilter(activations, identities, desk, targets, mapper);
    final DeskServiceClient.Connection connection = new DeskServiceClient.Connection("https://desk.example", "service-key-never-for-browser");

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void verifiesTheActualReviewerAndCurrentAssignmentWithoutGrantingConsoleAdministration() throws Exception {
        scope(true);
        var response = new MockHttpServletResponse();
        final boolean[] called = {false};
        filter.doFilter(request(), response, (a, b) -> called[0] = true);
        assertThat(called[0]).isTrue();
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication.getPrincipal()).isEqualTo(new DeskReviewPrincipal("desk-a", "https://auth.example/auth/v1", "registered-agent"));
        assertThat(authentication.getAuthorities()).extracting(Object::toString).containsExactly("DESK_REVIEW_INTEGRATION");
        verify(identities).verifyIdentity("actual-supabase-user-token");
        verify(desk).get(eq(connection), contains("permission=REVIEWER"));
    }

    @Test void aRevokedOrSupportOnlyAssignmentCannotReadOrDecideReviews() throws Exception {
        scope(false);
        var response = new MockHttpServletResponse();
        filter.doFilter(request(), response, (a, b) -> fail("No request may pass without a reviewer assignment"));
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test void bindingCredentialCannotBeReusedAgainstAnotherBinding() throws Exception {
        when(activations.requireBridgeBinding("desk-a", "binding-key")).thenThrow(new ResponseStatusException(UNAUTHORIZED));
        var response = new MockHttpServletResponse();
        filter.doFilter(request(), response, (a, b) -> fail("Wrong binding must stop before reviewer resolution"));
        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(identities, desk);
    }

    @Test void aServiceCredentialAloneIsNotAReviewerIdentity() throws Exception {
        scope(true);
        var request = request(); request.removeHeader("Authorization");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (a, b) -> fail("A person must authenticate"));
        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(identities);
    }

    @Test void plainConsoleAuthenticationCannotGrantTheBridgeRole() throws Exception {
        var request = request(); request.removeHeader("X-LoomAI-Desk-Service-Key");
        when(activations.requireBridgeBinding("desk-a", null)).thenThrow(new ResponseStatusException(UNAUTHORIZED));
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (a, b) -> fail("Desk's integration credential is required too"));
        assertThat(response.getStatus()).isEqualTo(401);
    }

    private MockHttpServletRequest request() {
        var request = new MockHttpServletRequest("POST", "/api/desk/bridge/v1/bindings/desk-a/reviews/task-a/decisions");
        request.addHeader("Authorization", "Bearer actual-supabase-user-token");
        request.addHeader("X-LoomAI-Desk-Service-Key", "binding-key");
        return request;
    }

    private void scope(boolean allowed) {
        var binding = new DeskBindingEntity(); binding.setId("desk-a"); binding.setTargetProfileId("target-a");
        when(activations.requireBridgeBinding("desk-a", "binding-key")).thenReturn(binding);
        var target = new DeploymentTargetProfileEntity(); target.setActive(true); target.setEnvironmentName("production");
        when(targets.findById("target-a")).thenReturn(Optional.of(target));
        when(identities.verifyIdentity(anyString())).thenReturn(Jwt.withTokenValue("token").header("alg", "ES256")
            .subject("registered-agent").issuer("https://auth.example/auth/v1").issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300)).build());
        when(desk.connection("production")).thenReturn(connection);
        when(desk.get(eq(connection), anyString())).thenReturn(mapper.createObjectNode().put("authorized", allowed));
    }
}
