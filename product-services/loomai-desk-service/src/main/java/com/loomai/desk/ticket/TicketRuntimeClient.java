package com.loomai.desk.ticket;

import static com.loomai.desk.ticket.TicketContracts.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loomai.desk.config.DeskProperties;
import com.loomai.desk.service.DeskDirectory;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

@Component
public class TicketRuntimeClient {
  private final TicketEndpointPolicy endpoints;
  private final DeskDirectory directory;
  private final ObjectMapper json;
  private final CloseableHttpClient http;

  public TicketRuntimeClient(
      TicketEndpointPolicy endpoints,
      DeskDirectory directory,
      DeskProperties properties,
      ObjectMapper json) {
    this.endpoints = endpoints;
    this.directory = directory;
    this.json = json;
    Timeout timeout = Timeout.ofMilliseconds(properties.getRequestTimeout().toMillis());
    var pool =
        PoolingHttpClientConnectionManagerBuilder.create()
            .setMaxConnTotal(16)
            .setMaxConnPerRoute(4)
            .setDnsResolver(
                new DnsResolver() {
                  public InetAddress[] resolve(String host) throws UnknownHostException {
                    return endpoints.resolve(host);
                  }

                  public String resolveCanonicalHostname(String host) {
                    return host;
                  }
                })
            .setDefaultConnectionConfig(
                ConnectionConfig.custom()
                    .setConnectTimeout(timeout)
                    .setSocketTimeout(timeout)
                    .build())
            .build();
    http =
        HttpClients.custom()
            .setConnectionManager(pool)
            .disableRedirectHandling()
            .disableAutomaticRetries()
            .disableCookieManagement()
            .disableContentCompression()
            .setDefaultRequestConfig(
                RequestConfig.custom()
                    .setConnectionRequestTimeout(timeout)
                    .setResponseTimeout(timeout)
                    .build())
            .build();
  }

  RestOperations jwks(String uri) {
    endpoints.requireEndpoint(uri);
    return new RestTemplate(new HttpComponentsClientHttpRequestFactory(http));
  }

  public void verify(
      Space space,
      Requester requester,
      RuntimeCredential credential,
      String expectedRuntimePrincipal,
      String conversationId) {
    JsonNode auth = call(space, credential, "/api/chat/me/auth-context", null, null);
    var b = directory.binding(space.view().bindingId());
    if (expectedRuntimePrincipal == null || expectedRuntimePrincipal.isBlank())
      throw TicketException.invalid("runtime principal mapping");
    if (!expectedRuntimePrincipal.equals(auth.path("subjectId").asText())
        || space.identity().mode() == IdentityMode.SUPABASE
            && !requester.subject().equals(expectedRuntimePrincipal)
        || !b.customerId().equals(auth.path("customerId").asText())
        || !b.tenantId().equals(auth.path("tenantId").asText())
        || !b.deploymentId().equals(auth.path("deploymentId").asText())
        || !"END_USER".equals(auth.path("subjectType").asText()))
      throw new TicketException(
          403,
          "RUNTIME_IDENTITY_MISMATCH",
          "Runtime identity does not match this requester and support space.");
    if (conversationId != null) {
      id(conversationId);
      JsonNode conversation =
          call(space, credential, "/api/chat/me/conversations/" + conversationId, null, null);
      if (!conversationId.equals(conversation.path("id").asText()))
        throw new TicketException(
            503, "RUNTIME_RESPONSE_INVALID", "The runtime returned an invalid conversation.");
    }
  }

  public Delivery start(
      Space space,
      Requester requester,
      RuntimeCredential credential,
      String expectedRuntimePrincipal,
      String conversationId,
      String key,
      String opening) {
    verify(space, requester, credential, expectedRuntimePrincipal, null);
    id(conversationId);
    try {
      if (observed(space, credential, conversationId, key)) return Delivery.DELIVERED;
    } catch (TicketException e) {
      if (e.status() != 404) throw e;
    }
    try {
      JsonNode response =
          call(
              space,
              credential,
              "/api/chat/me/conversations/" + conversationId + "/messages",
              Map.of("query", opening),
              key);
      if (conversationId.equals(response.path("conversationId").asText())
          && response.path("success").asBoolean(false)) return Delivery.DELIVERED;
    } catch (TicketException e) {
      // Observe the canonical saved user message; an AI failure is not a failed ticket save.
      try {
        if (observed(space, credential, conversationId, key)) return Delivery.DELIVERED;
      } catch (TicketException ignored) {
      }
      throw e;
    }
    return observed(space, credential, conversationId, key) ? Delivery.DELIVERED : Delivery.UNKNOWN;
  }

  private boolean observed(Space s, RuntimeCredential credential, String conversation, String key) {
    JsonNode saved = call(s, credential, "/api/chat/me/conversations/" + conversation, null, null);
    if (!conversation.equals(saved.path("id").asText()))
      throw new TicketException(
          503, "RUNTIME_RESPONSE_INVALID", "The runtime returned an invalid conversation.");
    for (JsonNode message : saved.path("participantMessages"))
      if (key.equals(message.path("clientMessageId").asText())
          && "USER".equals(message.path("role").asText())) return true;
    return false;
  }

  public boolean terminal(
      Space space,
      Requester requester,
      RuntimeCredential credential,
      String expectedRuntimePrincipal,
      String conversationId) {
    verify(space, requester, credential, expectedRuntimePrincipal, conversationId);
    JsonNode control =
        call(
            space,
            credential,
            "/api/chat/me/conversations/" + conversationId + "/control",
            null,
            null);
    return "CLOSED".equals(control.path("controlState").asText());
  }

  public void initialize(
      Space space,
      Requester requester,
      RuntimeCredential credential,
      String expectedRuntimePrincipal,
      String conversationId) {
    verify(space, requester, credential, expectedRuntimePrincipal, null);
    id(conversationId);
    JsonNode control =
        call(
            space,
            credential,
            "/api/chat/me/conversations/" + conversationId + "/control",
            null,
            null);
    if (!conversationId.equals(control.path("conversationId").asText())
        || !"AI_ACTIVE".equals(control.path("controlState").asText()))
      throw new TicketException(
          503, "RUNTIME_RESPONSE_INVALID", "The runtime did not initialize an empty conversation.");
    verify(space, requester, credential, expectedRuntimePrincipal, conversationId);
  }

  public java.time.Instant activity(
      Space space,
      Requester requester,
      RuntimeCredential credential,
      String expectedRuntimePrincipal,
      String conversationId) {
    verify(space, requester, credential, expectedRuntimePrincipal, conversationId);
    JsonNode saved =
        call(space, credential, "/api/chat/me/conversations/" + conversationId, null, null);
    try {
      // The current canonical ConversationResponse serializes this UTC field as LocalDateTime.
      return java.time.LocalDateTime.parse(saved.path("lastInteractionAt").asText())
          .toInstant(java.time.ZoneOffset.UTC);
    } catch (RuntimeException e) {
      throw new TicketException(
          503, "RUNTIME_RESPONSE_INVALID", "The runtime returned an invalid activity timestamp.");
    }
  }

  private JsonNode call(
      Space space, RuntimeCredential credential, String path, Object body, String key) {
    if (credential == null
        || credential.authorization() == null
        || !credential.authorization().startsWith("Bearer ")
        || credential.authorization().length() > 32768)
      throw new TicketException(
          401, "RUNTIME_AUTH_REQUIRED", "A matching runtime credential is required.");
    var uri = endpoints.requireEndpoint(space.runtimeBaseUrl() + path);
    HttpUriRequestBase request = body == null ? new HttpGet(uri) : new HttpPost(uri);
    if (body != null && path.endsWith("/messages"))
      request.setConfig(RequestConfig.custom().setResponseTimeout(Timeout.ofSeconds(120)).build());
    if (space.identity().mode() == IdentityMode.TRUSTED_PRIVATE_HOST) {
      if (credential.apiKey() == null
          || credential.apiKey().length() < 32
          || credential.apiKey().length() > 4096)
        throw new TicketException(
            401, "RUNTIME_AUTH_REQUIRED", "Private runtime authentication is required.");
      request.setHeader("X-AIFABRIC-RUNTIME-AUTHORIZATION", credential.authorization());
      request.setHeader(space.runtimeTrustedKeyHeader(), credential.apiKey());
    } else {
      if (credential.apiKey() != null)
        throw TicketException.invalid("private runtime credential for direct profile");
      request.setHeader("Authorization", credential.authorization());
    }
    if (key != null) request.setHeader("Idempotency-Key", key);
    return execute(request, body, 1048576);
  }

  private JsonNode execute(HttpUriRequestBase request, Object body, int limit) {
    request.setHeader("Accept", "application/json");
    try {
      endpoints.resolve(request.getUri().getHost());
      if (body != null)
        ((HttpPost) request)
            .setEntity(
                new StringEntity(json.writeValueAsString(body), ContentType.APPLICATION_JSON));
      return http.execute(
          request,
          response -> {
            int status = response.getCode();
            if (status < 200 || status >= 300) {
              request.cancel();
              if (status == 401 || status == 403 || status == 404 || status == 409)
                throw new TicketException(
                    status, "RUNTIME_REQUEST_REJECTED", "The runtime rejected this operation.");
              throw unavailable();
            }
            var entity = response.getEntity();
            if (entity == null || entity.getContentLength() > limit) {
              request.cancel();
              throw unavailable();
            }
            byte[] bytes;
            try (var stream = entity.getContent()) {
              bytes = stream.readNBytes(limit + 1);
            }
            if (bytes.length > limit) {
              request.cancel();
              throw unavailable();
            }
            return json.readTree(bytes);
          });
    } catch (TicketException e) {
      throw e;
    } catch (Exception e) {
      request.cancel();
      throw unavailable();
    }
  }

  private void id(String id) {
    if (id == null || !id.matches("[A-Za-z0-9_-]{1,160}"))
      throw TicketException.invalid("conversationId");
  }

  private TicketException unavailable() {
    return new TicketException(
        503,
        "RUNTIME_UNAVAILABLE",
        "Conversation authority or delivery is unavailable or uncertain.");
  }

  @PreDestroy
  public void close() throws IOException {
    http.close();
  }
}
