package com.loomai.desk;

import static com.loomai.desk.ticket.TicketContracts.*;
import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.loomai.desk.service.DeskDirectory;
import com.loomai.desk.ticket.SupportSpaces;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

class SupportTicketHttpTest {
  static final String PRIVATE_KEY = "private-host-server-key-12345678901234567890";
  static final String CONNECTOR = "private-connector-key-12345678901234567890";
  static final List<String> OPS =
      List.of(
          "SESSION",
          "TEAM",
          "CREATE",
          "GET",
          "LIST",
          "START",
          "LINK_CONVERSATION",
          "RESOLVE",
          "CANCEL",
          "REOPEN",
          "SEEN",
          "ACTIVITY",
          "TICKET_COMMAND");

  @ParameterizedTest
  @CsvSource({
    "private,,",
    "direct,,",
    "private,thinker,",
    "direct,thinker,",
    "private,support_assistant,support",
    "direct,support_assistant,support",
    "private,future-support.v2,future_help-v2",
    "direct,future-support.v2,future_help-v2"
  })
  void openingUsesOnlyRegisteredConversationSettingsAndKeepsIdentityAndIdempotency(
      String profile, String configuredMode, String configuredPosition) throws Exception {
    try (var h = new Harness("h2")) {
      boolean privateHost = profile.equals("private");
      var input =
          DeskIsolationTest.JSON.valueToTree(
              h.input(
                  profile,
                  privateHost ? IdentityMode.TRUSTED_PRIVATE_HOST : IdentityMode.SUPABASE));
      var body = (com.fasterxml.jackson.databind.node.ObjectNode) input;
      body.put("expectedVersion", "0");
      body.remove("conversation");
      if (configuredMode != null) {
        var settings = body.putObject("conversation").put("mode", configuredMode);
        if (configuredPosition != null) settings.put("position", configuredPosition);
      }
      String path = "/api/desk/admin/v1/bindings/" + (privateHost ? "a" : "b") + "/support-space";
      var registered =
          h.raw(
              "PUT",
              path,
              null,
              body,
              null,
              Map.of("X-LoomAI-Desk-Service-Key", DeskHttpSecurityTest.ADMIN));
      assertThat(registered.statusCode()).withFailMessage(registered.body()).isEqualTo(200);
      var conversation = DeskIsolationTest.JSON.readTree(registered.body()).path("conversation");
      assertThat(conversation.path("mode").asText())
          .isEqualTo(configuredMode == null ? "executor" : configuredMode);
      assertThat(conversation.has("position")).isEqualTo(configuredPosition != null);
      assertThat(registered.body()).doesNotContain(PRIVATE_KEY, CONNECTOR);
      String token = h.token("user", profile, privateHost, OPS, 300, "requesters");
      var created =
          h.request(
              "POST",
              profile + "/requests",
              token,
              new CreateInput("Opening settings", "Original details", null, null),
              "create-settings",
              privateHost ? h.privateHeaders() : null);
      assertThat(created.statusCode()).withFailMessage(created.body()).isEqualTo(201);
      String id = DeskIsolationTest.JSON.readTree(created.body()).path("id").asText();
      h.runtimeSubject = privateHost ? "support-ticket:" + id : "user";
      h.runtimeBinding = privateHost ? "a" : "b";
      var proof =
          privateHost
              ? h.runtimeHeaders()
              : Map.of("X-LoomAI-Runtime-Authorization", "Bearer direct-runtime-test");
      var started =
          h.request(
              "POST",
              profile + "/requests/" + id + "/start",
              token,
              new VersionInput("0"),
              "start-settings",
              proof);
      assertThat(started.statusCode()).withFailMessage(started.body()).isEqualTo(200);
      assertThat(h.messageBodies).hasSize(1);
      var expected = conversation.deepCopy();
      ((com.fasterxml.jackson.databind.node.ObjectNode) expected)
          .put("query", "Opening settings\n\nOriginal details");
      assertThat(h.messageBodies.getFirst()).isEqualTo(expected);
      assertThat(
              h.request(
                      "POST",
                      profile + "/requests/" + id + "/start",
                      token,
                      new VersionInput("0"),
                      "start-settings",
                      proof)
                  .statusCode())
          .isEqualTo(200);
      assertThat(h.messageKeys).hasSize(1);
      assertThat(h.runtimeHeadersSeen)
          .allSatisfy(
              headers -> {
                assertThat(headers.get("public"))
                    .isEqualTo(privateHost ? "" : "Bearer direct-runtime-test");
                assertThat(headers.get("private"))
                    .isEqualTo(privateHost ? "Bearer rpa1.private-test" : "");
                assertThat(headers.get("key"))
                    .isEqualTo(privateHost ? "runtime-backend-key-12345678901234567890" : "");
              });
    }
  }

  @org.junit.jupiter.api.Test
  void openingInferenceMayOutlastShortReadDeadlineWithoutAutomaticReplay() throws Exception {
    try (var h = new Harness("h2")) {
      String token = h.token("user", "private", true, OPS, 300, "requesters");
      var created =
          h.request(
              "POST",
              "private/requests",
              token,
              new CreateInput("Slow inference", "Original input", null, null),
              "create-slow",
              h.privateHeaders());
      String id = DeskIsolationTest.JSON.readTree(created.body()).path("id").asText();
      h.runtimeSubject = "support-ticket:" + id;
      h.messageDelayMillis = 2200;
      var started =
          h.request(
              "POST",
              "private/requests/" + id + "/start",
              token,
              new VersionInput("0"),
              "slow-start",
              h.runtimeHeaders());
      assertThat(started.statusCode()).withFailMessage(started.body()).isEqualTo(200);
      assertThat(
              DeskIsolationTest.JSON
                  .readTree(started.body())
                  .path("conversation")
                  .path("delivery")
                  .asText())
          .isEqualTo("DELIVERED");
      assertThat(h.messageKeys).hasSize(1);
      String conversation =
          DeskIsolationTest.JSON
              .readTree(created.body())
              .path("conversation")
              .path("conversationId")
              .asText();
      assertThat(
              h.runtimePaths.stream()
                  .filter(path -> path.equals("/api/chat/me/conversations/" + conversation))
                  .count())
          .isEqualTo(1);
    }
  }

  @ParameterizedTest
  @CsvSource({"h2,private", "postgres,private", "h2,direct", "postgres,direct"})
  void continuationInitializesEmptyCanonicalConversationWithoutRepeatingIntake(
      String engine, String profile) throws Exception {
    try (var h = new Harness(engine)) {
      boolean privateHost = profile.equals("private");
      String token = h.token("user", profile, privateHost, OPS, 300, "requesters");
      Map<String, String> runtimeProof =
          privateHost
              ? h.runtimeHeaders()
              : Map.of("X-LoomAI-Runtime-Authorization", "Bearer direct-runtime-test");
      var created =
          h.request(
              "POST",
              profile + "/requests",
              token,
              new CreateInput("Do not replay", "Original input", null, null),
              "create-continuation",
              privateHost ? h.privateHeaders() : null);
      assertThat(created.statusCode()).withFailMessage(created.body()).isEqualTo(201);
      var first = DeskIsolationTest.JSON.readTree(created.body());
      String id = first.path("id").asText();
      String previous = first.path("conversation").path("conversationId").asText();
      h.runtimeSubject = privateHost ? "support-ticket:" + id : "user";
      h.runtimeBinding = privateHost ? "a" : "b";
      h.requireInitialization = true;
      h.controlConversations.add(previous);
      h.closedConversations.add(previous);
      var result =
          h.request(
              "POST",
              profile + "/requests/" + id + "/episodes",
              token,
              new VersionInput("0"),
              "continue",
              runtimeProof);
      assertThat(result.statusCode()).withFailMessage(result.body()).isEqualTo(200);
      var next = DeskIsolationTest.JSON.readTree(result.body());
      assertThat(next.path("episodes")).hasSize(2);
      assertThat(next.path("episodes").get(0)).isEqualTo(first.path("episodes").get(0));
      assertThat(next.path("openingMessageRequired").asBoolean()).isFalse();
      assertThat(next.path("conversation").path("delivery").asText()).isEqualTo("NOT_REQUIRED");
      assertThat(h.controlConversations)
          .contains(next.path("conversation").path("conversationId").asText());
      String canonical =
          "/api/chat/me/conversations/" + next.path("conversation").path("conversationId").asText();
      assertThat(h.runtimePaths.indexOf(canonical))
          .isGreaterThan(h.runtimePaths.indexOf(canonical + "/control"));
      assertThat(h.runtimePaths).contains(canonical + "/control", canonical);
      int reads = h.runtimePaths.size();
      assertThat(
              h.request(
                      "POST",
                      profile + "/requests/" + id + "/start",
                      token,
                      new VersionInput(next.path("version").asText()),
                      "not-intake",
                      runtimeProof)
                  .statusCode())
          .isEqualTo(200);
      assertThat(h.messageKeys).isEmpty();
      assertThat(h.canonicalOpenings).isEmpty();
      assertThat(h.runtimePaths).hasSize(reads);
      assertThat(h.runtimeHeadersSeen)
          .allSatisfy(
              headers -> {
                assertThat(headers.get("public"))
                    .isEqualTo(privateHost ? "" : "Bearer direct-runtime-test");
                assertThat(headers.get("private"))
                    .isEqualTo(privateHost ? "Bearer rpa1.private-test" : "");
                assertThat(headers.get("key"))
                    .isEqualTo(privateHost ? "runtime-backend-key-12345678901234567890" : "");
              });
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"h2", "postgres"})
  void directRequesterPhotosAreAuthenticatedBindingScopedAndRevocable(String engine)
      throws Exception {
    try (var h = new Harness(engine)) {
      var directory = h.app.getBean(DeskDirectory.class);
      var owner = directory.register(DeskIsolationTest.identity("owner-b"));
      var agent = directory.register(DeskIsolationTest.identity("photo-agent"));
      directory.assign(
          "b",
          owner,
          new com.loomai.desk.model.DeskContracts.AssignmentInput(
              agent.email(),
              null,
              java.util.Set.of(com.loomai.desk.model.DeskContracts.Role.SUPPORT)));
      byte[] photo = new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xd9};
      var jdbc = new JdbcTemplate(h.db.dataSource);
      jdbc.update("UPDATE desk_operator SET avatar_bytes=? WHERE operator_id=?", photo, agent.id());
      jdbc.update("UPDATE desk_support_space SET enabled=FALSE WHERE space_id='direct'");
      String token = h.token("ordinary-requester", "direct", false, OPS, 300, "requesters");
      var session = h.request("GET", "direct/session", token, null, null, null);
      assertThat(session.statusCode()).withFailMessage(session.body()).isEqualTo(200);
      var state = DeskIsolationTest.JSON.readTree(session.body());
      assertThat(state.path("space").path("enabled").asBoolean()).isFalse();
      assertThat(state.path("space").path("humanHandoverEnabled").asBoolean()).isTrue();
      assertThat(state.path("capabilities")).isEmpty();
      assertThat(state.path("space").path("capabilities")).isEmpty();
      assertThat(
              h.request(
                      "POST",
                      "direct/requests",
                      token,
                      new CreateInput("Disabled", "No ticket writes", null, null),
                      "disabled",
                      null)
                  .statusCode())
          .isEqualTo(409);
      assertThat(h.request("GET", "direct/requests", token, null, null, null).statusCode())
          .isEqualTo(409);
      assertThat(
              h.request("GET", "direct/requests/not-enabled", token, null, null, null).statusCode())
          .isEqualTo(409);
      var team = h.request("GET", "direct/team", token, null, null, null);
      assertThat(team.statusCode()).withFailMessage(team.body()).isEqualTo(200);
      var profile = DeskIsolationTest.JSON.readTree(team.body()).get(0);
      String ref = profile.path("profileRef").asText();
      assertThat(profile.path("photoAvailable").asBoolean()).isTrue();
      assertThat(team.body()).doesNotContain(agent.email(), agent.id());
      assertThat(
              h.request("GET", "direct/team/" + ref + "/photo", null, null, null, null)
                  .statusCode())
          .isEqualTo(401);
      var result =
          h.http.send(
              HttpRequest.newBuilder(
                      URI.create(
                          h.base + "/api/desk/requester/v1/spaces/direct/team/" + ref + "/photo"))
                  .header("Authorization", "Bearer " + token)
                  .GET()
                  .build(),
              HttpResponse.BodyHandlers.ofByteArray());
      assertThat(result.statusCode()).isEqualTo(200);
      assertThat(result.body()).containsExactly(photo);
      assertThat(result.headers().firstValue("Content-Type")).contains("image/jpeg");
      assertThat(result.headers().firstValue("Cache-Control")).contains("private, no-store");
      assertThat(result.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
      jdbc.update("UPDATE desk_support_space SET human_enabled=FALSE WHERE space_id='direct'");
      assertThat(
              DeskIsolationTest.JSON.readTree(
                  h.request("GET", "direct/team", token, null, null, null).body()))
          .isEmpty();
      assertThat(
              h.request("GET", "direct/team/" + ref + "/photo", token, null, null, null)
                  .statusCode())
          .isEqualTo(404);
      jdbc.update("UPDATE desk_support_space SET human_enabled=TRUE WHERE space_id='direct'");
      String privateToken = h.token("ordinary-requester", "private", true, OPS, 300, "requesters");
      assertThat(
              h.request(
                      "GET",
                      "private/team/" + ref + "/photo",
                      privateToken,
                      null,
                      null,
                      h.privateHeaders())
                  .statusCode())
          .isEqualTo(404);
      directory.revoke("b", owner, agent.id());
      assertThat(
              h.request("GET", "direct/team/" + ref + "/photo", token, null, null, null)
                  .statusCode())
          .isEqualTo(404);
    }
  }

  @org.junit.jupiter.api.Test
  void registeredUrnIssuerRequiresTheSameExactVerifiedTraceIssuer() throws Exception {
    try (var h = new Harness("h2")) {
      String issuer = "urn:loomai:runtime:deployment-b:1";
      var body =
          (com.fasterxml.jackson.databind.node.ObjectNode)
              DeskIsolationTest.JSON.valueToTree(h.input("direct", IdentityMode.SUPABASE));
      body.put("expectedVersion", "0");
      ((com.fasterxml.jackson.databind.node.ObjectNode) body.path("serverConnector"))
          .put("issuer", issuer);
      var registered =
          h.raw(
              "PUT",
              "/api/desk/admin/v1/bindings/b/support-space",
              null,
              body,
              null,
              Map.of("X-LoomAI-Desk-Service-Key", DeskHttpSecurityTest.ADMIN));
      assertThat(registered.statusCode()).withFailMessage(registered.body()).isEqualTo(200);
      String path = "/api/desk/integrations/v1/spaces/direct/server-ticket-commands";
      var list = new CommandInput("LIST", null, null, null, null, null, null);
      var headers = h.connectorHeaders("user", "persisted-chat");
      assertThat(h.raw("POST", path, null, list, "wrong-issuer", headers).statusCode())
          .isEqualTo(401);
      headers.put("X-AIFABRIC-AUTH-ISSUER", issuer);
      var own = h.raw("POST", path, null, list, "registered-urn", headers);
      assertThat(own.statusCode()).withFailMessage(own.body()).isEqualTo(200);
      assertThat(DeskIsolationTest.JSON.readTree(own.body()).path("items")).isEmpty();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"h2", "postgres"})
  void registeredServerConnectorUsesOnlyVerifiedTraceAndOriginalSupabaseOwner(String engine)
      throws Exception {
    try (var h = new Harness(engine)) {
      String path = "/api/desk/integrations/v1/spaces/direct/server-ticket-commands";
      var input =
          new CommandInput(
              "CREATE",
              null,
              null,
              "Action request",
              "Existing chat",
              null,
              "untrusted-model-conversation");
      var headers = h.connectorHeaders("user", "persisted-chat");
      for (String name : headers.keySet()) {
        var missing = new java.util.HashMap<>(headers);
        missing.remove(name);
        assertThat(h.raw("POST", path, null, input, "missing-" + name, missing).statusCode())
            .as(name)
            .isIn(401, 403);
        if (!java.util.Set.of("X-AIFABRIC-AUTH-SUBJECT-ID", "X-AIFABRIC-CONVERSATION-ID")
            .contains(name)) {
          var forged = new java.util.HashMap<>(headers);
          forged.put(name, "forged");
          assertThat(h.raw("POST", path, null, input, "forged-" + name, forged).statusCode())
              .as(name)
              .isIn(401, 403);
        }
      }
      var duplicate =
          HttpRequest.newBuilder(URI.create(h.base + path))
              .header("Content-Type", "application/json")
              .header("Idempotency-Key", "duplicate");
      headers.forEach(duplicate::header);
      duplicate.header("X-AIFABRIC-AUTH-SUBJECT-ID", "other");
      assertThat(
              h.http
                  .send(
                      duplicate
                          .POST(
                              HttpRequest.BodyPublishers.ofString(
                                  DeskIsolationTest.JSON.writeValueAsString(input)))
                          .build(),
                      HttpResponse.BodyHandlers.ofString())
                  .statusCode())
          .isEqualTo(401);
      var expired = new java.util.HashMap<>(headers);
      expired.put("X-AIFABRIC-AUTH-EXPIRES-AT", Instant.now().minusSeconds(1).toString());
      assertThat(h.raw("POST", path, null, input, "expired", expired).statusCode()).isEqualTo(401);
      var absent = new java.util.HashMap<>(headers);
      absent.remove("X-AIFABRIC-AUTH-ISSUER");
      assertThat(h.raw("POST", path, null, input, "missing", absent).statusCode()).isEqualTo(401);
      var browser = new java.util.HashMap<>(headers);
      browser.put("Origin", h.origin);
      assertThat(h.raw("POST", path, null, input, "browser", browser).statusCode()).isEqualTo(403);
      var foreignBinding = new java.util.HashMap<>(headers);
      foreignBinding.put("X-AIFABRIC-AUTH-DEPLOYMENT-ID", "foreign");
      assertThat(h.raw("POST", path, null, input, "foreign", foreignBinding).statusCode())
          .isEqualTo(401);
      var anonymous = new java.util.HashMap<>(headers);
      anonymous.put("X-AIFABRIC-AUTH-SUBJECT-TYPE", "ANONYMOUS_SESSION");
      assertThat(h.raw("POST", path, null, input, "anonymous", anonymous).statusCode())
          .isEqualTo(401);
      assertThat(
              new JdbcTemplate(h.db.dataSource)
                  .queryForObject("SELECT COUNT(*) FROM desk_support_request", Integer.class))
          .isZero();
      h.canonicalDenied = false;
      var created = h.raw("POST", path, null, input, "adopt", headers);
      assertThat(created.statusCode()).withFailMessage(created.body()).isEqualTo(200);
      var ticket = DeskIsolationTest.JSON.readTree(created.body());
      assertThat(ticket.path("runtimePrincipal").asText()).isEqualTo("user");
      assertThat(ticket.path("openingMessageRequired").asBoolean()).isFalse();
      assertThat(ticket.path("conversation").path("delivery").asText()).isEqualTo("NOT_REQUIRED");
      assertThat(ticket.path("conversation").path("conversationId").asText())
          .isEqualTo("persisted-chat");
      var replay = h.raw("POST", path, null, input, "adopt", headers);
      assertThat(DeskIsolationTest.JSON.readValue(replay.body(), Ticket.class))
          .isEqualTo(DeskIsolationTest.JSON.readValue(created.body(), Ticket.class));
      assertThat(
              h.raw(
                      "POST",
                      path,
                      null,
                      input,
                      "adopt",
                      h.connectorHeaders("user", "different-chat"))
                  .statusCode())
          .isEqualTo(409);
      String id = ticket.path("id").asText();
      String direct = h.token("user", "direct", false, OPS, 300, "requesters");
      assertThat(h.request("GET", "direct/requests/" + id, direct, null, null, null).statusCode())
          .isEqualTo(200);
      var get = new CommandInput("GET", id, null, null, null, null, null);
      assertThat(h.raw("POST", path, null, get, "get", headers).statusCode()).isEqualTo(200);
      assertThat(
              h.raw("POST", path, null, get, "other", h.connectorHeaders("other", "persisted-chat"))
                  .statusCode())
          .isEqualTo(404);
      assertThat(h.messageKeys).isEmpty();
      assertThat(h.runtimeHeadersSeen).isEmpty();
      assertThat(h.runtimePaths).isEmpty();
      assertThat(
              new JdbcTemplate(h.db.dataSource)
                  .queryForObject(
                      "SELECT protected_server_connector FROM desk_support_space WHERE"
                          + " space_id='direct'",
                      String.class))
          .doesNotContain("authMode", "requiredScopes");
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"h2", "postgres"})
  void privateAndDirectAdmissionIsolationRuntimeMappingAndConnectorProof(String engine)
      throws Exception {
    try (var h = new Harness(engine)) {
      String direct = h.token("user", "direct", false, OPS, 300, "requesters");
      String privateUser = h.token("user", "private", true, OPS, 300, "requesters");
      String other = h.token("other", "private", true, OPS, 300, "requesters");
      assertThat(
              h.request(
                      "POST",
                      "private/requests/not-owned/activity",
                      h.token("user", "private", true, List.of("GET"), 300, "requesters"),
                      new VersionInput("0"),
                      "read-only-activity",
                      h.privateHeaders())
                  .statusCode())
          .isEqualTo(403);
      assertThat(h.request("GET", "direct/session", null, null, null, null).statusCode())
          .isEqualTo(401);
      assertThat(h.request("GET", "direct/session", direct, null, null, null).statusCode())
          .isEqualTo(200);
      assertThat(h.request("GET", "private/session", privateUser, null, null, null).statusCode())
          .isEqualTo(401);
      assertThat(
              h.request(
                      "GET",
                      "private/session",
                      privateUser,
                      null,
                      null,
                      Map.of("X-LoomAI-Requester-Server-Key", PRIVATE_KEY))
                  .statusCode())
          .isEqualTo(200);
      assertThat(
              h.request(
                      "GET",
                      "private/session",
                      h.token("user", "foreign", true, OPS, 300, "requesters"),
                      null,
                      null,
                      h.privateHeaders())
                  .statusCode())
          .isEqualTo(401);
      assertThat(
              h.request(
                      "GET",
                      "private/session",
                      h.token("user", "private", true, OPS, 301, "requesters"),
                      null,
                      null,
                      h.privateHeaders())
                  .statusCode())
          .isEqualTo(401);
      assertThat(
              h.request(
                      "GET",
                      "private/session",
                      h.token("user", "private", true, OPS, -1, "requesters"),
                      null,
                      null,
                      h.privateHeaders())
                  .statusCode())
          .isEqualTo(401);
      assertThat(
              h.request(
                      "GET",
                      "private/session",
                      h.token("user", "private", true, OPS, 300, "wrong-audience"),
                      null,
                      null,
                      h.privateHeaders())
                  .statusCode())
          .isEqualTo(401);
      assertThat(
              h.request(
                      "GET",
                      "private/session",
                      h.token("user", "private", true, List.of("LIST"), 300, "requesters"),
                      null,
                      null,
                      h.privateHeaders())
                  .statusCode())
          .isEqualTo(403);
      assertThat(
              h.request(
                      "GET",
                      "private/session",
                      privateUser,
                      null,
                      null,
                      Map.of("X-LoomAI-Requester-Server-Key", PRIVATE_KEY, "Origin", h.origin))
                  .statusCode())
          .isEqualTo(403);
      assertThat(
              h.request(
                      "GET",
                      "direct/session",
                      direct,
                      null,
                      null,
                      Map.of("Origin", "https://evil.example"))
                  .statusCode())
          .isEqualTo(403);
      var saved =
          h.request(
              "POST",
              "private/requests",
              privateUser,
              new CreateInput("Support issue", "Private details", null, null),
              "create",
              h.privateHeaders());
      assertThat(saved.statusCode()).withFailMessage(saved.body()).isEqualTo(201);
      JsonNode ticket = DeskIsolationTest.JSON.readTree(saved.body());
      String id = ticket.path("id").asText();
      assertThat(
              h.request("GET", "private/requests/" + id, other, null, null, h.privateHeaders())
                  .statusCode())
          .isEqualTo(404);
      assertThat(h.request("GET", "direct/requests/" + id, direct, null, null, null).statusCode())
          .isEqualTo(404);
      assertThat(
              h.request(
                      "POST",
                      "private/requests/" + id + "/start",
                      other,
                      new VersionInput("0"),
                      "foreign",
                      h.runtimeHeaders())
                  .statusCode())
          .isEqualTo(404);
      h.runtimeSubject = "wrong-ticket";
      assertThat(
              h.request(
                      "POST",
                      "private/requests/" + id + "/start",
                      privateUser,
                      new VersionInput("0"),
                      "start-wrong",
                      h.runtimeHeaders())
                  .statusCode())
          .isEqualTo(403);
      assertThat(h.messageKeys).isEmpty();
      h.runtimeSubject = "support-ticket:" + id;
      var started =
          h.request(
              "POST",
              "private/requests/" + id + "/start",
              privateUser,
              new VersionInput("0"),
              "start",
              h.runtimeHeaders());
      assertThat(started.statusCode()).withFailMessage(started.body()).isEqualTo(200);
      assertThat(
              DeskIsolationTest.JSON
                  .readTree(started.body())
                  .path("conversation")
                  .path("delivery")
                  .asText())
          .isEqualTo("DELIVERED");
      assertThat(h.messageKeys).hasSize(1);
      assertThat(
              h.request(
                      "POST",
                      "private/requests/" + id + "/start",
                      privateUser,
                      new VersionInput("0"),
                      "start-again",
                      h.runtimeHeaders())
                  .statusCode())
          .isEqualTo(200);
      assertThat(h.messageKeys).hasSize(1);
      assertThat(h.runtimeHeadersSeen)
          .allSatisfy(
              headers -> {
                assertThat(headers.get("private")).isEqualTo("Bearer rpa1.private-test");
                assertThat(headers.get("key"))
                    .isEqualTo("runtime-backend-key-12345678901234567890");
                assertThat(headers.get("public")).isEqualTo("");
              });
      var failedAiSave =
          h.request(
              "POST",
              "private/requests",
              privateUser,
              new CreateInput("AI unavailable", "Still save the opening", null, null),
              "create-ai-fail",
              h.privateHeaders());
      String failedAiId = DeskIsolationTest.JSON.readTree(failedAiSave.body()).path("id").asText();
      h.runtimeSubject = "support-ticket:" + failedAiId;
      h.failMessageResponse = true;
      var observed =
          h.request(
              "POST",
              "private/requests/" + failedAiId + "/start",
              privateUser,
              new VersionInput("0"),
              "start-ai-fail",
              h.runtimeHeaders());
      assertThat(observed.statusCode()).withFailMessage(observed.body()).isEqualTo(200);
      assertThat(
              DeskIsolationTest.JSON
                  .readTree(observed.body())
                  .path("conversation")
                  .path("delivery")
                  .asText())
          .isEqualTo("DELIVERED");
      assertThat(h.messageKeys).hasSize(2);
      String endpoint = "/api/desk/integrations/v1/spaces/private/ticket-commands";
      var command = new CommandInput("GET", id, null, null, null, null, null);
      assertThat(
              h.raw(
                      "POST",
                      endpoint,
                      null,
                      command,
                      "command",
                      Map.of("X-LoomAI-Ticket-Connector-Key", CONNECTOR))
                  .statusCode())
          .isEqualTo(401);
      assertThat(
              h.raw("POST", endpoint, privateUser, command, "command", h.privateHeaders())
                  .statusCode())
          .isEqualTo(401);
      assertThat(
              h.raw(
                      "POST",
                      endpoint,
                      privateUser,
                      command,
                      "command",
                      Map.of(
                          "X-LoomAI-Ticket-Connector-Key",
                          CONNECTOR,
                          "X-LoomAI-Requester-Server-Key",
                          PRIVATE_KEY))
                  .statusCode())
          .isEqualTo(200);
      assertThat(
              h.raw(
                      "POST",
                      endpoint,
                      other,
                      command,
                      "command",
                      Map.of(
                          "X-LoomAI-Ticket-Connector-Key",
                          CONNECTOR,
                          "X-LoomAI-Requester-Server-Key",
                          PRIVATE_KEY,
                          "X-Trace-Auth-Context",
                          "user"))
                  .statusCode())
          .isEqualTo(404);
      assertThat(
              new JdbcTemplate(h.db.dataSource)
                  .queryForObject(
                      "SELECT COUNT(*) FROM desk_operator WHERE subject='user'", Long.class))
          .isZero();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"h2", "postgres"})
  void privateActionCreateAdoptsOnlySignedOwnedPrincipalAndNeverSendsAgain(String engine)
      throws Exception {
    try (var h = new Harness(engine)) {
      String principal = "support-console:unique-chat-42";
      String conversation = "chat-before-ticket";
      String bound = h.boundToken("user", principal, conversation);
      h.runtimeSubject = principal;
      var headers = new java.util.HashMap<>(h.runtimeHeaders());
      headers.put("X-LoomAI-Ticket-Connector-Key", CONNECTOR);
      String endpoint = "/api/desk/integrations/v1/spaces/private/ticket-commands";
      var input =
          new CommandInput(
              "CREATE", null, null, "Existing chat", "Already discussed", null, conversation);
      assertThat(
              h.raw(
                      "POST",
                      endpoint,
                      h.token("user", "private", true, OPS, 300, "requesters"),
                      input,
                      "missing-binding",
                      headers)
                  .statusCode())
          .isEqualTo(403);
      var created = h.raw("POST", endpoint, bound, input, "action-create", headers);
      assertThat(created.statusCode()).withFailMessage(created.body()).isEqualTo(200);
      JsonNode ticket = DeskIsolationTest.JSON.readTree(created.body());
      String id = ticket.path("id").asText();
      assertThat(ticket.path("runtimePrincipal").asText()).isEqualTo(principal);
      assertThat(ticket.path("openingMessageRequired").asBoolean()).isFalse();
      assertThat(ticket.path("conversation").path("delivery").asText()).isEqualTo("NOT_REQUIRED");
      assertThat(
              DeskIsolationTest.JSON.readValue(
                  h.raw("POST", endpoint, bound, input, "action-create", headers).body(),
                  Ticket.class))
          .isEqualTo(DeskIsolationTest.JSON.readValue(created.body(), Ticket.class));
      assertThat(
              h.raw(
                      "POST",
                      endpoint,
                      h.boundToken("user", "different-principal", conversation),
                      input,
                      "action-create",
                      headers)
                  .statusCode())
          .isEqualTo(409);
      assertThat(
              h.raw("POST", endpoint, bound, input, "second-ticket-same-principal", headers)
                  .statusCode())
          .isEqualTo(409);
      assertThat(
              h.raw(
                      "POST",
                      endpoint,
                      h.boundToken("user", principal, "different-conversation"),
                      input,
                      "wrong-conversation",
                      headers)
                  .statusCode())
          .isEqualTo(403);
      h.runtimeSubject = "different-actual-owner";
      assertThat(
              h.raw(
                      "POST",
                      endpoint,
                      h.boundToken("user", "fresh-principal", "fresh-chat"),
                      new CommandInput(
                          "CREATE", null, null, "Wrong owner", "details", null, "fresh-chat"),
                      "wrong-runtime",
                      headers)
                  .statusCode())
          .isEqualTo(403);
      h.runtimeSubject = principal;
      assertThat(
              h.request(
                      "POST",
                      "private/requests/" + id + "/start",
                      bound,
                      new VersionInput("0"),
                      "linked-start",
                      h.runtimeHeaders())
                  .statusCode())
          .isEqualTo(200);
      assertThat(
              h.request(
                      "POST",
                      "private/requests/" + id + "/start",
                      bound,
                      new VersionInput("0"),
                      "linked-start-again",
                      h.runtimeHeaders())
                  .statusCode())
          .isEqualTo(200);
      assertThat(h.messageKeys).isEmpty();
      String other = h.boundToken("other", principal, conversation);
      assertThat(
              h.request("GET", "private/requests/" + id, other, null, null, h.privateHeaders())
                  .statusCode())
          .isEqualTo(404);
      assertThat(
              h.request(
                      "GET",
                      "private/requests/" + id,
                      h.boundToken("user", "different-principal", conversation),
                      null,
                      null,
                      h.privateHeaders())
                  .statusCode())
          .isEqualTo(403);
      assertThat(
              new JdbcTemplate(h.db.dataSource)
                  .queryForObject("SELECT COUNT(*) FROM desk_support_request", Long.class))
          .isEqualTo(1);
    }
  }

  static final class Harness implements AutoCloseable {
    final DeskTestDatabase db;
    final RSAKey rsa;
    final HttpServer upstream;
    final ConfigurableApplicationContext app;
    final String origin;
    final String base;
    final HttpClient http = HttpClient.newHttpClient();
    final List<String> messageKeys = new CopyOnWriteArrayList<>();
    final List<JsonNode> messageBodies = new CopyOnWriteArrayList<>();
    final List<Map<String, String>> runtimeHeadersSeen = new CopyOnWriteArrayList<>();
    final List<String> runtimePaths = new CopyOnWriteArrayList<>();
    volatile String runtimeSubject;
    volatile String runtimeBinding = "a";
    volatile boolean canonicalDenied;
    volatile long messageDelayMillis;
    volatile boolean requireInitialization;
    final java.util.Set<String> controlConversations =
        java.util.concurrent.ConcurrentHashMap.newKeySet();
    final java.util.Set<String> closedConversations =
        java.util.concurrent.ConcurrentHashMap.newKeySet();
    volatile boolean failMessageResponse;
    final Map<String, String> canonicalOpenings = new java.util.concurrent.ConcurrentHashMap<>();

    Harness(String engine) throws Exception {
      db = new DeskTestDatabase(engine);
      rsa = new RSAKeyGenerator(2048).keyID("requester-test").generate();
      upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      origin = "http://127.0.0.1:" + upstream.getAddress().getPort();
      upstream.createContext(
          "/jwks",
          exchange -> {
            byte[] bytes =
                new JWKSet(rsa.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
          });
      upstream.createContext(
          "/api/chat/me/",
          exchange -> {
            var headers = exchange.getRequestHeaders();
            runtimeHeadersSeen.add(
                Map.of(
                    "private",
                    value(headers.getFirst("X-AIFABRIC-RUNTIME-AUTHORIZATION")),
                    "key",
                    value(
                        headers.getFirst("X-AIFABRIC-RUNTIME-API-KEY") == null
                            ? headers.getFirst("X-API-KEY")
                            : headers.getFirst("X-AIFABRIC-RUNTIME-API-KEY")),
                    "public",
                    value(headers.getFirst("Authorization"))));
            String path = exchange.getRequestURI().getPath();
            runtimePaths.add(path);
            Object response;
            if (path.endsWith("auth-context"))
              response =
                  DeskIsolationTest.JSON
                      .createObjectNode()
                      .put("subjectId", value(runtimeSubject))
                      .put("subjectType", "END_USER")
                      .put("customerId", runtimeBinding)
                      .put("tenantId", "tenant-" + runtimeBinding)
                      .put("deploymentId", "deployment-" + runtimeBinding)
                      .put(
                          "authMode",
                          headers.getFirst("Authorization") == null
                              ? "PRIVATE_RUNTIME_BACKEND_MEDIATED"
                              : "PUBLIC_RUNTIME_AUTHENTICATED")
                      .put(
                          "callerType",
                          headers.getFirst("Authorization") == null
                              ? "TRUSTED_BACKEND"
                              : "PUBLIC_BROWSER")
                      .put("expiresAt", Instant.now().plusSeconds(120).toString())
                      .set(
                          "grantedScopes",
                          DeskIsolationTest.JSON.valueToTree(List.of("chat:conversations")));
            else {
              String conversation = path.split("/")[5];
              if (path.endsWith("messages")) {
                messageBodies.add(DeskIsolationTest.JSON.readTree(exchange.getRequestBody()));
                messageKeys.add(headers.getFirst("Idempotency-Key"));
                canonicalOpenings.put(conversation, headers.getFirst("Idempotency-Key"));
                try {
                  Thread.sleep(messageDelayMillis);
                } catch (InterruptedException interrupted) {
                  Thread.currentThread().interrupt();
                }
              }
              if (path.endsWith("control")) controlConversations.add(conversation);
              response =
                  path.endsWith("control")
                      ? Map.of(
                          "conversationId",
                          conversation,
                          "controlState",
                          closedConversations.contains(conversation) ? "CLOSED" : "AI_ACTIVE")
                      : path.endsWith("messages")
                          ? Map.of("success", true, "conversationId", conversation)
                          : Map.of(
                              "id",
                              conversation,
                              "participantMessages",
                              canonicalOpenings.containsKey(conversation)
                                  ? List.of(
                                      Map.of(
                                          "role",
                                          "USER",
                                          "clientMessageId",
                                          canonicalOpenings.get(conversation)))
                                  : List.of());
            }
            byte[] bytes = DeskIsolationTest.JSON.writeValueAsBytes(response);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(
                canonicalDenied && !path.endsWith("auth-context")
                        || requireInitialization
                            && !path.endsWith("auth-context")
                            && !path.endsWith("control")
                            && !controlConversations.contains(path.split("/")[5])
                    ? 404
                    : failMessageResponse && path.endsWith("messages") ? 503 : 200,
                bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
          });
      upstream.start();
      app =
          new SpringApplicationBuilder(LoomAiDeskApplication.class)
              .run(
                  "--server.port=0",
                  "--spring.datasource.url=" + db.url,
                  "--spring.datasource.username=" + db.username,
                  "--spring.datasource.password=" + db.password,
                  "--loomai.desk.service-key=" + DeskHttpSecurityTest.ADMIN,
                  "--loomai.desk.data-encryption-secret=data-secret-separate-12345678901234567890",
                  "--loomai.desk.supabase-url=" + origin,
                  "--loomai.desk.issuer=" + DeskIsolationTest.ISSUER,
                  "--loomai.desk.jwks-uri=" + origin + "/jwks",
                  "--loomai.desk.conversation-context-allowed-origins=" + origin,
                  "--loomai.desk.conversation-context-allow-insecure-loopback=true",
                  "--loomai.desk.request-timeout=PT2S",
                  "--DESK_CALLBACK_POLL_MS=3600000",
                  "--spring.main.banner-mode=off",
                  "--logging.level.root=WARN");
      base = "http://127.0.0.1:" + app.getEnvironment().getProperty("local.server.port");
      var directory = app.getBean(DeskDirectory.class);
      DeskIsolationTest.workspace(directory, "a", "owner-a");
      DeskIsolationTest.workspace(directory, "b", "owner-b");
      directory.putBinding("a", DeskIsolationTest.binding("a", origin + "/callback"));
      directory.putBinding("b", DeskIsolationTest.binding("b", origin + "/callback"));
      var spaces = app.getBean(SupportSpaces.class);
      spaces.put("a", input("private", IdentityMode.TRUSTED_PRIVATE_HOST));
      spaces.put("b", input("direct", IdentityMode.SUPABASE));
    }

    SupportSpaceInput input(String id, IdentityMode mode) {
      return new SupportSpaceInput(
          id,
          null,
          "Support",
          true,
          true,
          "desk",
          "tpb-" + id,
          origin,
          mode == IdentityMode.SUPABASE ? "X-AIFABRIC-RUNTIME-API-KEY" : "X-API-KEY",
          new IdentityInput(
              mode,
              origin,
              origin + "/jwks",
              "requesters",
              "users",
              mode == IdentityMode.TRUSTED_PRIVATE_HOST ? PRIVATE_KEY : null),
          CONNECTOR,
          List.of(origin),
          List.of(),
          30,
          mode == IdentityMode.SUPABASE
              ? new ServerConnectorInput(
                  "PUBLIC_RUNTIME_AUTHENTICATED",
                  "PUBLIC_BROWSER",
                  origin,
                  "public-runtime",
                  List.of("chat:conversations"))
              : null,
          null);
    }

    Map<String, String> connectorHeaders(String subject, String conversation) {
      var h = new java.util.HashMap<String, String>();
      h.put("X-LoomAI-Ticket-Connector-Key", CONNECTOR);
      h.put("X-AIFABRIC-AUTH-SUBJECT-TYPE", "END_USER");
      h.put("X-AIFABRIC-AUTH-MODE", "PUBLIC_RUNTIME_AUTHENTICATED");
      h.put("X-AIFABRIC-AUTH-CALLER-TYPE", "PUBLIC_BROWSER");
      h.put("X-AIFABRIC-AUTH-ISSUER", origin);
      h.put("X-AIFABRIC-AUTH-AUDIENCES", "public-runtime");
      h.put("X-AIFABRIC-AUTH-SCOPES", "chat:conversations");
      h.put("X-AIFABRIC-AUTH-EXPIRES-AT", Instant.now().plusSeconds(120).toString());
      h.put("X-AIFABRIC-AUTH-CUSTOMER-ID", "b");
      h.put("X-AIFABRIC-AUTH-TENANT-ID", "tenant-b");
      h.put("X-AIFABRIC-AUTH-DEPLOYMENT-ID", "deployment-b");
      h.put("X-AIFABRIC-AUTH-SUBJECT-ID", subject);
      h.put("X-AIFABRIC-CONVERSATION-ID", conversation);
      return h;
    }

    String token(
        String subject,
        String space,
        boolean privateHost,
        List<String> operations,
        int seconds,
        String audience)
        throws Exception {
      Instant now = Instant.now();
      var claims =
          new JWTClaimsSet.Builder()
              .issuer(origin)
              .audience(audience)
              .subject(subject)
              .issueTime(Date.from(now))
              .expirationTime(Date.from(now.plusSeconds(seconds)))
              .jwtID(UUID.randomUUID().toString());
      if (privateHost) claims.claim("spaceId", space).claim("operations", operations);
      SignedJWT jwt =
          new SignedJWT(
              new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsa.getKeyID()).build(),
              claims.build());
      jwt.sign(new RSASSASigner(rsa));
      return jwt.serialize();
    }

    String boundToken(String subject, String principal, String conversation) throws Exception {
      SignedJWT original = SignedJWT.parse(token(subject, "private", true, OPS, 300, "requesters"));
      var claims =
          new JWTClaimsSet.Builder(original.getJWTClaimsSet())
              .claim("runtimePrincipal", principal)
              .claim("runtimeConversationId", conversation)
              .build();
      SignedJWT bound = new SignedJWT(original.getHeader(), claims);
      bound.sign(new RSASSASigner(rsa));
      return bound.serialize();
    }

    Map<String, String> privateHeaders() {
      return Map.of("X-LoomAI-Requester-Server-Key", PRIVATE_KEY);
    }

    Map<String, String> runtimeHeaders() {
      return Map.of(
          "X-LoomAI-Requester-Server-Key",
          PRIVATE_KEY,
          "X-LoomAI-Runtime-Authorization",
          "Bearer rpa1.private-test",
          "X-LoomAI-Runtime-Api-Key",
          "runtime-backend-key-12345678901234567890");
    }

    HttpResponse<String> request(
        String method,
        String path,
        String token,
        Object body,
        String key,
        Map<String, String> headers)
        throws Exception {
      return raw(method, "/api/desk/requester/v1/spaces/" + path, token, body, key, headers);
    }

    HttpResponse<String> raw(
        String method,
        String path,
        String token,
        Object body,
        String key,
        Map<String, String> headers)
        throws Exception {
      var r = HttpRequest.newBuilder(URI.create(base + path));
      if (token != null) r.header("Authorization", "Bearer " + token);
      if (key != null) r.header("Idempotency-Key", key);
      if (headers != null) headers.forEach(r::header);
      if (body != null) r.header("Content-Type", "application/json");
      return http.send(
          r.method(
                  method,
                  body == null
                      ? HttpRequest.BodyPublishers.noBody()
                      : HttpRequest.BodyPublishers.ofString(
                          DeskIsolationTest.JSON.writeValueAsString(body)))
              .build(),
          HttpResponse.BodyHandlers.ofString());
    }

    static String value(String s) {
      return s == null ? "" : s;
    }

    public void close() {
      app.close();
      upstream.stop(0);
      db.close();
    }
  }
}
