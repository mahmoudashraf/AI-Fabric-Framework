package com.loomai.desk;

import static com.loomai.desk.model.DeskContracts.*;
import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.loomai.desk.config.DeskProperties;
import com.loomai.desk.service.DeskChannelService;
import com.loomai.desk.service.DeskDirectory;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;

class DeskConversationContextTest {
  static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
  static final String KEY = "distinct-context-service-key-1234567890-abcdefgh";
  static final String ROOT = "/api/desk/v1/bindings/a/conversations/same-handover/context";

  @ParameterizedTest
  @ValueSource(strings = {"h2", "postgres"})
  void contextIsOptionalScopedProjectedAndResolutionNeverChangesConversationControl(String db)
      throws Exception {
    try (var h = new DeskHttpSecurityTest.Harness(db)) {
      h.provision();
      String owner = h.token("owner", h.issuer, "authenticated", true, 600);
      String agent = h.token("agent", h.issuer, "authenticated", true, 600);
      String reviewer = h.token("reviewer", h.issuer, "authenticated", true, 600);
      h.request("POST", "/api/desk/v1/agents/register", owner, "{}");
      String agentId =
          JSON.readTree(h.request("POST", "/api/desk/v1/agents/register", agent, "{}").body())
              .path("id")
              .asText();
      String reviewerId =
          JSON.readTree(h.request("POST", "/api/desk/v1/agents/register", reviewer, "{}").body())
              .path("id")
              .asText();
      assign(h, owner, agentId, Role.SUPPORT);
      assign(h, owner, reviewerId, Role.REVIEWER);
      var channel = h.context.getBean(DeskChannelService.class);
      var directory = h.context.getBean(DeskDirectory.class);
      ObjectNode dispatch = JSON.valueToTree(DeskIsolationTest.open("a", "a", "Original request"));
      ((ObjectNode) dispatch.path("scope")).put("deploymentId", "dep-a");
      channel.dispatch(directory.binding("a").providerApiKey(), JSON.writeValueAsString(dispatch));
      assertThat(h.request("GET", ROOT, agent, null).body()).contains("NOT_CONFIGURED");
      assertThat(
              h.request(
                      "POST",
                      ROOT + "/resolve",
                      agent,
                      "{\"idempotencyKey\":\"resolve\",\"expectedVersion\":3}")
                  .statusCode())
          .isEqualTo(409);
      List<JsonNode> requests = new CopyOnWriteArrayList<>();
      List<String> bearers = new CopyOnWriteArrayList<>();
      AtomicReference<String> mode = new AtomicReference<>("NORMAL");
      AtomicInteger mutations = new AtomicInteger();
      AtomicReference<JsonNode> receipt = new AtomicReference<>();
      AtomicInteger trapCalls = new AtomicInteger();
      HttpServer provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      provider.createContext(
          "/trap",
          exchange -> {
            trapCalls.incrementAndGet();
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
          });
      provider.createContext(
          "/context",
          exchange -> {
            JsonNode payload = JSON.readTree(exchange.getRequestBody());
            requests.add(payload);
            bearers.add(exchange.getRequestHeaders().getFirst("Authorization"));
            assertThat(exchange.getRequestHeaders().getFirst("X-LoomAI-Desk-Service-Key"))
                .isEqualTo(KEY);
            assertThat(exchange.getRequestHeaders().getFirst("X-LoomAI-Desk-Binding-Id"))
                .isEqualTo("a");
            int status = 200;
            ObjectNode result = response(payload);
            switch (mode.get()) {
              case "MISMATCH" ->
                  ((ObjectNode) result.path("scope")).put("ownerRef", "foreign-owner");
              case "OVERSIZE" ->
                  ((ObjectNode) result.path("context")).put("title", "x".repeat(33000));
              case "INVALID" -> ((ObjectNode) result.path("context")).put("title", "x".repeat(241));
              case "MISSING_VERSION" -> ((ObjectNode) result.path("context")).remove("version");
              case "RESOURCE_LIMIT" -> {
                for (int i = 0; i < 12; i++)
                  ((ObjectNode) result.path("context"))
                      .withArray("resourceLabels")
                      .add("Extra resource");
              }
              case "DOWN" -> {
                status = 503;
                result.put("secret", "provider-private-error");
              }
              case "REDIRECT" -> {
                status = 302;
                exchange.getResponseHeaders().set("Location", "/trap");
              }
              case "UNLINKED" -> {
                result.put("state", "NOT_LINKED");
                result.putNull("context");
              }
              case "REVOKE" ->
                  directory.revoke(
                      "a",
                      directory.find(
                          new VerifiedIdentity(
                              h.issuer, "owner", "owner@example.test", "owner", true, owner)),
                      agentId);
              default -> {}
            }
            if ("RESOLVE".equals(payload.path("operation").asText())) {
              if (receipt.get() == null) {
                mutations.incrementAndGet();
                ObjectNode context = (ObjectNode) result.path("context");
                context.put("version", 4).put("status", "RESOLVED").putNull("resolveAction");
                receipt.set(result.deepCopy());
              } else result = (ObjectNode) receipt.get();
            }
            byte[] bytes = JSON.writeValueAsBytes(result);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
          });
      provider.start();
      try {
        String base = "http://127.0.0.1:" + provider.getAddress().getPort();
        DeskProperties properties = h.context.getBean(DeskProperties.class);
        properties.setConversationContextAllowedOrigins(List.of(base));
        properties.setConversationContextAllowInsecureLoopback(true);
        String config =
            JSON.writeValueAsString(
                Map.of(
                    "providerUrl", base + "/context", "serviceKey", KEY, "resolveEnabled", true));
        assertThat(
                h.request(
                        "PUT", "/api/desk/admin/v1/bindings/a/conversation-context", owner, config)
                    .statusCode())
            .isEqualTo(401);
        var configured = h.admin("/bindings/a/conversation-context", config);
        assertThat(configured.statusCode()).withFailMessage(configured.body()).isEqualTo(200);
        assertThat(configured.body()).doesNotContain(KEY, "serviceKey");
        var jdbc = new JdbcTemplate(h.db.dataSource);
        assertThat(
                jdbc.queryForObject(
                    "SELECT protected_service_key FROM desk_context_provider", String.class))
            .doesNotContain(KEY);
        assertThat(h.adminGet("/bindings/a/conversation-context").body()).doesNotContain(KEY);
        assertThat(h.request("GET", ROOT, owner, null).statusCode()).isEqualTo(403);
        assertThat(h.request("GET", ROOT, reviewer, null).statusCode()).isEqualTo(403);
        assertThat(
                h.request("GET", ROOT.replace("/bindings/a/", "/bindings/b/"), agent, null)
                    .statusCode())
            .isEqualTo(403);
        assertThat(
                h.request("GET", ROOT.replace("same-handover", "unknown"), agent, null)
                    .statusCode())
            .isEqualTo(404);
        assertThat(
                h.request(
                        "GET", ROOT, h.token("agent", h.issuer, "authenticated", true, -300), null)
                    .statusCode())
            .isEqualTo(401);
        assertThat(requests).isEmpty();
        var loaded = h.request("GET", ROOT, agent, null);
        assertThat(loaded.statusCode()).withFailMessage(loaded.body()).isEqualTo(200);
        assertThat(loaded.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(loaded.body())
            .contains(
                "SUP-1042", "Selected workspace", "GIVEN", "Routing incomplete", "Resolve case")
            .doesNotContain(KEY, "privateNotes", "secret-payload", "same-owner", "providerUrl");
        JsonNode sent = requests.getFirst();
        assertThat(sent.path("scope").path("ownerRef").asText()).isEqualTo("same-owner");
        assertThat(sent.path("scope").path("conversationId").asText())
            .isEqualTo("same-conversation");
        assertThat(sent.path("actor").path("subject").asText()).isEqualTo("agent");
        assertThat(sent.path("actor").path("agentId").asText()).isEqualTo(agentId);
        assertThat(bearers).containsExactly("Bearer " + agent);
        assertThat(
                h.request(
                        "POST",
                        ROOT + "/resolve",
                        agent,
                        "{\"idempotencyKey\":\"missing-version\"}")
                    .statusCode())
            .isEqualTo(400);
        assertThat(requests).hasSize(1);
        String resolve = "{\"idempotencyKey\":\"resolve-stable\",\"expectedVersion\":3}";
        var completed = h.request("POST", ROOT + "/resolve", agent, resolve);
        assertThat(completed.statusCode()).withFailMessage(completed.body()).isEqualTo(200);
        assertThat(completed.body()).contains("RESOLVED", "\"version\":4");
        assertThat(h.request("POST", ROOT + "/resolve", agent, resolve).body())
            .isEqualTo(completed.body());
        assertThat(mutations).hasValue(1);
        assertThat(requests.getLast().path("command").path("expectedVersion").asInt()).isEqualTo(3);
        assertThat(
                channel
                    .engine(directory.binding("a"))
                    .repository()
                    .requireConversation("same-handover")
                    .status())
            .isEqualTo("WAITING");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inbox_callback_outbox", Long.class))
            .isZero();
        h.admin(
            "/bindings/a/conversation-context",
            config.replace("\"resolveEnabled\":true", "\"resolveEnabled\":false"));
        assertThat(
                JSON.readTree(h.request("GET", ROOT, agent, null).body())
                    .path("context")
                    .hasNonNull("resolveAction"))
            .isFalse();
        int before = requests.size();
        assertThat(h.request("POST", ROOT + "/resolve", agent, resolve).statusCode())
            .isEqualTo(403);
        assertThat(requests).hasSize(before);
        for (String failure :
            List.of(
                "MISMATCH",
                "OVERSIZE",
                "INVALID",
                "MISSING_VERSION",
                "RESOURCE_LIMIT",
                "DOWN",
                "REDIRECT")) {
          mode.set(failure);
          var result = h.request("GET", ROOT, agent, null);
          assertThat(result.statusCode()).as(failure + " " + result.body()).isEqualTo(502);
          assertThat(result.body())
              .doesNotContain(KEY, "privateNotes", "provider-private-error", "SUP-1042");
        }
        assertThat(trapCalls).hasValue(0);
        mode.set("UNLINKED");
        assertThat(h.request("GET", ROOT, agent, null).body())
            .contains("NOT_LINKED")
            .doesNotContain("SUP-1042");
        mode.set("REVOKE");
        var revoked = h.request("GET", ROOT, agent, null);
        assertThat(revoked.statusCode()).isEqualTo(403);
        assertThat(revoked.body()).doesNotContain("SUP-1042");
        before = requests.size();
        assertThat(h.request("POST", ROOT + "/resolve", agent, resolve).statusCode())
            .isEqualTo(403);
        assertThat(requests).hasSize(before);
        assign(h, owner, agentId, Role.SUPPORT);
        assertThat(
                h.http
                    .send(
                        HttpRequest.newBuilder(
                                URI.create(
                                    h.base + "/api/desk/admin/v1/bindings/a/conversation-context"))
                            .header("X-LoomAI-Desk-Service-Key", DeskHttpSecurityTest.ADMIN)
                            .DELETE()
                            .build(),
                        HttpResponse.BodyHandlers.ofString())
                    .statusCode())
            .isEqualTo(204);
        assertThat(h.request("GET", ROOT, agent, null).body()).contains("NOT_CONFIGURED");
        assertThat(requests).hasSize(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM desk_context_provider", Long.class))
            .isZero();
        assertThat(h.request("GET", "/api/desk/v1/bindings/a/conversations", agent, null).body())
            .contains("same-handover");
      } finally {
        provider.stop(0);
      }
    }
  }

  private void assign(DeskHttpSecurityTest.Harness h, String owner, String agent, Role role)
      throws Exception {
    assertThat(
            h.request(
                    "POST",
                    "/api/desk/v1/bindings/a/agents",
                    owner,
                    JSON.writeValueAsString(new AssignmentInput(null, agent, Set.of(role))))
                .statusCode())
        .isEqualTo(200);
  }

  private static ObjectNode response(JsonNode request) {
    ObjectNode result = JSON.createObjectNode().put("schemaVersion", 1).put("state", "AVAILABLE");
    result.set("scope", request.path("scope").deepCopy());
    ObjectNode c =
        result
            .putObject("context")
            .put("reference", "SUP-1042")
            .put("title", "Workspace setup")
            .put("status", "OPEN")
            .put("version", 3)
            .put("accountLabel", "Example company")
            .put("privateNotes", "secret-payload");
    c.putArray("resourceLabels").add("Selected workspace");
    c.putObject("consent")
        .put("status", "GIVEN")
        .put("summary", "Selected status only")
        .put("updatedAt", "2026-10-30T10:00:00Z");
    c.putArray("observations")
        .addObject()
        .put("label", "Routing")
        .put("status", "INCOMPLETE")
        .put("summary", "Routing incomplete")
        .put("observedAt", "2026-10-30T10:02:00Z");
    c.putObject("resolveAction")
        .put("label", "Resolve case")
        .put("confirmation", "Resolve the external case only?");
    return result;
  }
}
