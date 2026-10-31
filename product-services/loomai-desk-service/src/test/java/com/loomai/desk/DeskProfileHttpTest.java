package com.loomai.desk;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DeskProfileHttpTest {
  private static final ObjectMapper JSON = new ObjectMapper();

  @ParameterizedTest
  @ValueSource(strings = {"h2", "postgres"})
  void profileIsSelfOwnedAndPhotosArePrivateAndRoleIndependent(String db) throws Exception {
    try (var h = new DeskHttpSecurityTest.Harness(db)) {
      h.provision();
      String owner = h.token("owner", h.issuer, "authenticated", true, 600);
      String agent = h.token("agent", h.issuer, "authenticated", true, 600);
      String other = h.token("other-owner", h.issuer, "authenticated", true, 600);
      assertThat(h.request("PUT", "/api/desk/v1/profile", null, "{}").statusCode()).isEqualTo(403);
      assertThat(h.request("PUT", "/api/desk/v1/profile", agent, "{\"displayName\":\"Unregistered\"}").statusCode()).isEqualTo(403);
      assertThat(h.request("POST", "/api/desk/v1/agents/register", agent, "{}").statusCode()).isEqualTo(200);
      String body = JSON.writeValueAsString(Map.of("displayName", "Sam Support", "photoDataUrl", DeskProfileTest.photo(120, 80), "roles", new String[] {"OWNER"}));
      var update = h.request("PUT", "/api/desk/v1/profile", agent, body);
      assertThat(update.statusCode()).isEqualTo(200);
      var profile = JSON.readTree(update.body());
      assertThat(profile.path("email").asText()).isEqualTo("agent@example.test");
      String photoUrl = profile.path("avatarUrl").asText();
      assertThat(photoUrl).startsWith("/api/desk/v1/agents/");
      assertThat(h.request("GET", photoUrl, null, null).statusCode()).isEqualTo(401);
      assertThat(h.request("GET", photoUrl, other, null).statusCode()).isEqualTo(404);
      var photo = h.request("GET", photoUrl, agent, null);
      assertThat(photo.statusCode()).isEqualTo(200);
      assertThat(photo.headers().firstValue("Content-Type").orElse("")).isEqualTo("image/jpeg");
      assertThat(photo.headers().firstValue("Cache-Control").orElse("")).isEqualTo("private, no-store");
      assertThat(photo.headers().firstValue("X-Content-Type-Options").orElse("")).isEqualTo("nosniff");
      assertThat(JSON.readTree(h.request("GET", "/api/desk/v1/session", agent, null).body()).path("bindings")).isEmpty();
      assertThat(h.request("GET", "/api/desk/v1/bindings/a/agent-profiles", agent, null).statusCode()).isEqualTo(403);
      h.request("GET", "/api/desk/v1/session", owner, null);
      String assignment = JSON.writeValueAsString(Map.of("agentReference", profile.path("agentReference").asText(), "roles", new String[] {"SUPPORT"}));
      assertThat(h.request("POST", "/api/desk/v1/bindings/a/agents", owner, assignment).statusCode()).isEqualTo(200);
      assertThat(h.request("GET", photoUrl, owner, null).statusCode()).isEqualTo(200);
      var summaries = h.request("GET", "/api/desk/v1/bindings/a/agent-profiles", agent, null);
      assertThat(summaries.statusCode()).isEqualTo(200);
      assertThat(summaries.body()).contains("Sam Support", "avatarUrl").doesNotContain("email", "roles", "subject");
      assertThat(h.request("PUT", "/api/desk/v1/profile", agent, "{\"displayName\":\"\"}").statusCode()).isEqualTo(400);
      assertThat(h.request("PUT", "/api/desk/v1/profile", agent, "{\"displayName\":\"Sam\",\"photoDataUrl\":\"https://example.test/photo.jpg\"}").statusCode()).isEqualTo(400);
      var session = JSON.readTree(h.request("GET", "/api/desk/v1/session", agent, null).body());
      assertThat(session.path("operator").path("displayName").asText()).isEqualTo("Sam Support");
      assertThat(session.path("bindings").get(0).path("roles").toString()).isEqualTo("[\"SUPPORT\"]");
      assertThat(h.request("PUT", "/api/desk/v1/profile", agent, "{\"displayName\":\"Sam\",\"removePhoto\":true}").statusCode()).isEqualTo(200);
      assertThat(h.request("GET", photoUrl, agent, null).statusCode()).isEqualTo(404);
    }
  }
}
