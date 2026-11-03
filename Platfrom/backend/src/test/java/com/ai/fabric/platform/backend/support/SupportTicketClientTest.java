package com.ai.fabric.platform.backend.support;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.desk.DeskServiceClient;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

class SupportTicketClientTest {
  @AfterEach
  void logout() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void privateRuntimeProofUsesActualAuthorityHeaderWithoutReplacingRequesterAuthorization()
      throws Exception {
    var f = new SupportFixture();
    var owner = f.requesters.current();
    var assertions = mock(SupportRequesterAssertionService.class);
    var http = mock(HttpClient.class);
    var client = client(f, assertions, http);
    when(assertions.issue(owner, "support-binding", "session-principal", "session-conversation"))
        .thenReturn("signed-requester-jwt");
    respond(http, 200, "{\"id\":\"request-1\",\"version\":\"vendor-etag\"}");
    var result =
        client.request(
            owner,
            "POST",
            "/requests",
            new SupportTicketService.Create(
                "Help", "Original details", null, "session-conversation"),
            "command-1",
            Map.of(
                "X-AIFABRIC-RUNTIME-AUTHORIZATION",
                "Bearer rpa1.private-proof",
                "X-AIFABRIC-RUNTIME-API-KEY",
                "registered-private-key"),
            "session-principal",
            "session-conversation");
    assertThat(result.path("version").asText()).isEqualTo("vendor-etag");
    var sent = ArgumentCaptor.forClass(HttpRequest.class);
    verify(http).send(sent.capture(), any());
    var request = sent.getValue();
    assertThat(request.uri().toString())
        .isEqualTo(
            "https://desk.example.test/api/desk/requester/v1/spaces/support-binding/requests");
    assertThat(request.headers().firstValue("Authorization"))
        .contains("Bearer signed-requester-jwt");
    assertThat(request.headers().firstValue("X-LoomAI-Requester-Server-Key"))
        .contains("registered-host-key");
    assertThat(request.headers().firstValue("X-LoomAI-Runtime-Authorization"))
        .contains("Bearer rpa1.private-proof");
    assertThat(request.headers().firstValue("X-LoomAI-Runtime-Api-Key"))
        .contains("registered-private-key");
    assertThat(request.headers().firstValue("Idempotency-Key")).contains("command-1");
    assertThat(request.headers().firstValue("X-API-KEY")).isEmpty();
    assertThat(request.headers().firstValue("X-AIFABRIC-RUNTIME-API-KEY")).isEmpty();
    assertThat(request.headers().map().toString()).doesNotContain("admin-service-key");
  }

  @Test
  void publicAuthorizationOrGenericApiKeyCannotSubstituteForPrivateRuntimeAuthority() {
    var f = new SupportFixture();
    var http = mock(HttpClient.class);
    var client = client(f, mock(SupportRequesterAssertionService.class), http);
    for (var headers :
        java.util.List.of(
            Map.of("Authorization", "Bearer public-token", "X-API-KEY", "generic-key"),
            Map.of(
                "X-AIFABRIC-RUNTIME-AUTHORIZATION",
                "Bearer rpa1.proof",
                "X-API-KEY",
                "wrong-header"),
            Map.of("X-AIFABRIC-RUNTIME-API-KEY", "private-key"))) {
      assertThatThrownBy(
              () ->
                  client.request(
                      f.requesters.current(),
                      "POST",
                      "/requests/request-1/start",
                      new SupportTicketService.Version("etag"),
                      "start",
                      headers))
          .hasMessageContaining("SUPPORT_NOT_CONFIGURED");
    }
    verifyNoInteractions(http);
  }

  @Test
  void providerErrorsStayExplicitWithoutLeakingProviderTextOrAcceptingRedirects() throws Exception {
    var f = new SupportFixture();
    var http = mock(HttpClient.class);
    var client = client(f, mock(SupportRequesterAssertionService.class), http);
    respond(
        http,
        409,
        "{\"code\":\"EXPECTED_VERSION_CONFLICT\",\"message\":\"secret-provider-detail\"}");
    assertThatThrownBy(
            () ->
                client.request(
                    f.requesters.current(),
                    "POST",
                    "/requests/request-1/resolve",
                    new SupportTicketService.Version("etag"),
                    "resolve"))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            error -> {
              assertThat(error.getStatusCode().value()).isEqualTo(409);
              assertThat(error.getReason()).isEqualTo("EXPECTED_VERSION_CONFLICT");
              assertThat(error.getMessage()).doesNotContain("secret-provider-detail");
            });
    respond(http, 302, "{}");
    assertThatThrownBy(
            () -> client.request(f.requesters.current(), "GET", "/requests/request-1", null, null))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            error -> assertThat(error.getStatusCode().value()).isEqualTo(503));
  }

  @Test
  void invalidRoutesKeysAndOversizedResponsesCannotBecomeProviderCallsOrUnboundedPayloads()
      throws Exception {
    var f = new SupportFixture();
    var http = mock(HttpClient.class);
    var client = client(f, mock(SupportRequesterAssertionService.class), http);
    for (String path :
        java.util.List.of("https://attacker.test/requests", "/../admin", "/requests#fragment"))
      assertThatThrownBy(() -> client.request(f.requesters.current(), "GET", path, null, null))
          .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                client.request(f.requesters.current(), "POST", "/requests", Map.of(), "bad key\n"))
        .hasMessageContaining("SUPPORT_IDEMPOTENCY_KEY_INVALID");
    verifyNoInteractions(http);
    respond(http, 200, "x".repeat(1_048_577));
    assertThatThrownBy(
            () -> client.request(f.requesters.current(), "GET", "/requests/request-1", null, null))
        .hasMessageContaining("SUPPORT_NOT_CONFIGURED");
  }

  private SupportTicketClient client(
      SupportFixture f, SupportRequesterAssertionService assertions, HttpClient http) {
    var target = new DeploymentTargetProfileEntity();
    target.setActive(true);
    target.setEnvironmentName("staging");
    when(f.targets.findById("staging-target")).thenReturn(Optional.of(target));
    var desk = mock(DeskServiceClient.class);
    when(desk.connection("staging"))
        .thenReturn(
            new DeskServiceClient.Connection("https://desk.example.test", "admin-service-key"));
    when(assertions.serverKey()).thenReturn("registered-host-key");
    return new SupportTicketClient(f.settings, f.targets, desk, assertions, f.json, http);
  }

  @SuppressWarnings("unchecked")
  private void respond(HttpClient http, int status, String body) throws Exception {
    HttpResponse<InputStream> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(status);
    when(response.body())
        .thenAnswer(ignored -> new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
    when(http.send(
            any(HttpRequest.class),
            org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<InputStream>>any()))
        .thenReturn(response);
  }
}
