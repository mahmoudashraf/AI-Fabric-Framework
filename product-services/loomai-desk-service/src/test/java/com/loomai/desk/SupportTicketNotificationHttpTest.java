package com.loomai.desk;

import static com.loomai.desk.ticket.TicketContracts.*;
import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.loomai.desk.config.DeskProperties;
import com.loomai.desk.service.DeskDirectory;
import com.loomai.desk.ticket.SupportSpaces;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;

class SupportTicketNotificationHttpTest {
  static final List<String> OPS =
      java.util.stream.Stream.concat(
              SupportTicketHttpTest.OPS.stream(), java.util.stream.Stream.of("NOTIFICATIONS"))
          .toList();

  static Map<String, String> settings(int port) {
    return Map.of(
        "loomai.desk.email.enabled",
        "true",
        "loomai.desk.email.from",
        "support@loomai.pro",
        "spring.mail.host",
        "127.0.0.1",
        "spring.mail.port",
        Integer.toString(port),
        "DESK_EMAIL_POLL_MS",
        "3600000");
  }

  static String token(
      SupportTicketHttpTest.Harness h, String space, boolean privateHost, Map<String, Object> extra)
      throws Exception {
    SignedJWT base = SignedJWT.parse(h.token("user", space, privateHost, OPS, 300, "requesters"));
    var claims = new JWTClaimsSet.Builder(base.getJWTClaimsSet());
    extra.forEach(claims::claim);
    var jwt = new SignedJWT(base.getHeader(), claims.build());
    jwt.sign(new RSASSASigner(h.rsa));
    return jwt.serialize();
  }

  @ParameterizedTest
  @ValueSource(strings = {"h2", "postgres"})
  void privateNotificationOptInUsesOnlyVerifiedSignedClaimsAndExplicitOperation(String engine)
      throws Exception {
    try (var smtp = new SupportTicketNotificationSmtpTest.Capture();
        var h = new SupportTicketHttpTest.Harness(engine, settings(smtp.port()))) {
      var spaces = h.app.getBean(SupportSpaces.class);
      var input =
          (ObjectNode)
              DeskIsolationTest.JSON.valueToTree(
                  h.input("private", IdentityMode.TRUSTED_PRIVATE_HOST));
      input.put("expectedVersion", "0");
      input.putArray("allowedOrigins").add("https://page.example");
      spaces.put("a", DeskIsolationTest.JSON.treeToValue(input, SupportSpaceInput.class));
      String regular = h.token("user", "private", true, OPS, 300, "requesters");
      var enabled = Map.of("enabled", true, "returnUrl", "https://page.example/support");
      String path = "private/notifications";
      assertThat(h.request("GET", path, regular, null, null, h.privateHeaders()).statusCode())
          .isEqualTo(200);
      assertThat(h.request("PUT", path, regular, enabled, null, h.privateHeaders()).statusCode())
          .isEqualTo(403);
      String metadata =
          token(
              h,
              "private",
              true,
              Map.of(
                  "user_metadata", Map.of("email_verified", true, "email", "forged@example.com")));
      assertThat(h.request("PUT", path, metadata, enabled, null, h.privateHeaders()).statusCode())
          .isEqualTo(403);
      String verified =
          token(
              h, "private", true, Map.of("email_verified", true, "email", "verified@example.com"));
      var recipient = new java.util.HashMap<String, Object>(enabled);
      recipient.put("recipient", "arbitrary@example.com");
      assertThat(h.request("PUT", path, verified, recipient, null, h.privateHeaders()).statusCode())
          .isEqualTo(400);
      var response = h.request("PUT", path, verified, enabled, null, h.privateHeaders());
      assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(200);
      assertThat(response.body()).doesNotContain("verified@example.com", "recipient", "protected_");
      assertThat(DeskIsolationTest.JSON.readTree(response.body()).path("enabled").asBoolean())
          .isTrue();
      String denied = h.token("user", "private", true, List.of("GET"), 300, "requesters");
      assertThat(h.request("GET", path, denied, null, null, h.privateHeaders()).statusCode())
          .isEqualTo(403);
      assertThat(
              h.request("PUT", path, regular, Map.of("enabled", false), null, h.privateHeaders())
                  .statusCode())
          .isEqualTo(200);
      String other = h.token("other", "private", true, OPS, 300, "requesters");
      assertThat(
              DeskIsolationTest.JSON
                  .readTree(h.request("GET", path, other, null, null, h.privateHeaders()).body())
                  .path("enabled")
                  .asBoolean())
          .isFalse();
      assertThat(smtp.messages).isEmpty();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"h2", "postgres"})
  void directEmailVerificationRequiresExactConfiguredSupabaseUserAndNeverMetadata(String engine)
      throws Exception {
    try (var smtp = new SupportTicketNotificationSmtpTest.Capture();
        var h = new SupportTicketHttpTest.Harness(engine, settings(smtp.port()))) {
      var directory = h.app.getBean(DeskDirectory.class);
      DeskIsolationTest.workspace(directory, "c", "owner-c");
      directory.putBinding("c", DeskIsolationTest.binding("c", h.origin + "/callback"));
      var p = h.app.getBean(DeskProperties.class);
      p.setIssuer(h.origin + "/auth/v1");
      p.setSupabasePublishableKey("test-publishable-key");
      var user =
          new AtomicReference<>(
              DeskIsolationTest.JSON
                  .createObjectNode()
                  .put("id", "user")
                  .put("email", "verified@example.com")
                  .put("email_confirmed_at", "")
                  .put("is_anonymous", false));
      h.upstream.createContext(
          "/auth/v1/user",
          exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("apikey"))
                .isEqualTo("test-publishable-key");
            assertThat(exchange.getRequestHeaders().getFirst("Authorization"))
                .startsWith("Bearer ");
            byte[] bytes = user.get().toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
          });
      var configured =
          (ObjectNode)
              DeskIsolationTest.JSON.valueToTree(h.input("verified", IdentityMode.SUPABASE));
      ((ObjectNode) configured.path("identity")).put("issuer", p.getIssuer());
      configured.putArray("allowedOrigins").add("https://page.example");
      h.app
          .getBean(SupportSpaces.class)
          .put("c", DeskIsolationTest.JSON.treeToValue(configured, SupportSpaceInput.class));
      String jwt =
          token(
              h,
              "verified",
              false,
              Map.of(
                  "iss",
                  p.getIssuer(),
                  "email_verified",
                  true,
                  "email",
                  "jwt-claim-not-proof@example.com",
                  "user_metadata",
                  Map.of("email_verified", true)));
      var enabled = Map.of("enabled", true, "returnUrl", "https://page.example/support");
      String path = "verified/notifications";
      assertThat(h.request("PUT", path, jwt, enabled, null, null).statusCode()).isEqualTo(403);
      user.get().put("email_confirmed_at", "2026-11-04T00:00:00Z");
      var response = h.request("PUT", path, jwt, enabled, null, null);
      assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(200);
      assertThat(response.body()).doesNotContain("verified@example.com", "jwt-claim-not-proof");
      var jdbc = new JdbcTemplate(h.db.dataSource);
      String encrypted =
          jdbc.queryForObject(
              "SELECT protected_recipient FROM desk_support_notification_subscription",
              String.class);
      assertThat(encrypted).doesNotContain("verified@example.com");
      user.get().put("is_anonymous", true);
      assertThat(h.request("PUT", path, jwt, enabled, null, null).statusCode()).isEqualTo(403);
      user.get().put("is_anonymous", false).put("id", "other");
      assertThat(h.request("PUT", path, jwt, enabled, null, null).statusCode()).isEqualTo(403);
      var unconfigured = h.token("user", "direct", false, OPS, 300, "requesters");
      assertThat(
              h.request("PUT", "direct/notifications", unconfigured, enabled, null, null)
                  .statusCode())
          .isEqualTo(422);
      assertThat(h.request("GET", "direct/session", unconfigured, null, null, null).statusCode())
          .isEqualTo(200);
      assertThat(
              DeskIsolationTest.JSON
                  .readTree(
                      h.request("GET", "direct/notifications", unconfigured, null, null, null)
                          .body())
                  .path("available")
                  .asBoolean())
          .isFalse();
    }
  }
}
