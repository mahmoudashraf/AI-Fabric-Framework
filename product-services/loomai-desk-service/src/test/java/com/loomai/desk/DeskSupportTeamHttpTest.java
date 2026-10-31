package com.loomai.desk;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;

class DeskSupportTeamHttpTest {
  private static final ObjectMapper JSON = new ObjectMapper();

  @ParameterizedTest
  @ValueSource(strings = {"h2", "postgres"})
  void rosterAndJpegRequireServiceAuthAndCurrentEnabledSupportAssignment(String db) throws Exception {
    try (var h = new DeskHttpSecurityTest.Harness(db)) {
      h.provision();
      String owner = h.token("owner", h.issuer, "authenticated", true, 600);
      String agent = h.token("agent", h.issuer, "authenticated", true, 600);
      String rosterPath = "/api/desk/admin/v1/bindings/a/support-team";
      assertThat(h.request("GET", rosterPath, null, null).statusCode()).isEqualTo(401);
      assertThat(h.request("GET", rosterPath, agent, null).statusCode()).isEqualTo(401);
      var wrongKey = HttpRequest.newBuilder(URI.create(h.base + rosterPath))
          .header("X-LoomAI-Desk-Service-Key", "incorrect-service-key").GET().build();
      assertThat(h.http.send(wrongKey, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
      assertThat(h.adminGet("/bindings/a/support-team").body()).isEqualTo("[]");
      h.request("GET", "/api/desk/v1/session", owner, null);
      var registration = JSON.readTree(h.request("POST", "/api/desk/v1/agents/register", agent,
          "{\"displayName\":\"Sam Support\"}").body());
      assertThat(h.request("PUT", "/api/desk/v1/profile", agent, JSON.writeValueAsString(Map.of(
          "displayName", "Sam Support", "photoDataUrl", DeskProfileTest.photo(80, 80)))).statusCode()).isEqualTo(200);
      assertThat(h.request("POST", "/api/desk/v1/bindings/a/agents", owner, JSON.writeValueAsString(Map.of(
          "agentReference", registration.path("agentReference").asText(), "roles", new String[] {"SUPPORT"}))).statusCode()).isEqualTo(200);
      var response = h.adminGet("/bindings/a/support-team");
      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.headers().firstValue("Cache-Control").orElse("")).isEqualTo("private, no-store");
      assertThat(response.body()).contains("Sam Support", "\"photoAvailable\":true")
          .doesNotContain("agentId", "email", "subject", "issuer", "roles", "avatarUrl", "online", registration.path("id").asText());
      String reference = JSON.readTree(response.body()).get(0).path("profileRef").asText();
      String photoPath = rosterPath + "/" + reference + "/photo";
      assertThat(h.request("GET", photoPath, null, null).statusCode()).isEqualTo(401);
      assertThat(h.request("GET", photoPath, agent, null).statusCode()).isEqualTo(401);
      var photo = h.http.send(HttpRequest.newBuilder(URI.create(h.base + photoPath))
          .header("X-LoomAI-Desk-Service-Key", DeskHttpSecurityTest.ADMIN).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
      assertThat(photo.statusCode()).isEqualTo(200);
      assertThat(photo.headers().firstValue("Content-Type").orElse("")).isEqualTo("image/jpeg");
      assertThat(photo.headers().firstValue("Cache-Control").orElse("")).isEqualTo("private, no-store");
      assertThat(photo.headers().firstValue("X-Content-Type-Options").orElse("")).isEqualTo("nosniff");
      assertThat(photo.body()[0]).isEqualTo((byte) 0xff);
      assertThat(h.adminGet("/bindings/b/support-team/" + reference + "/photo").statusCode()).isEqualTo(404);
      assertThat(h.request("DELETE", "/api/desk/v1/bindings/a/agents/" + registration.path("id").asText(), owner, null).statusCode()).isEqualTo(204);
      assertThat(h.adminGet("/bindings/a/support-team").body()).isEqualTo("[]");
      assertThat(h.adminGet("/bindings/a/support-team/" + reference + "/photo").statusCode()).isEqualTo(404);
      new JdbcTemplate(h.db.dataSource).update("UPDATE desk_binding SET enabled=FALSE WHERE binding_id='a'");
      assertThat(h.adminGet("/bindings/a/support-team").statusCode()).isEqualTo(404);
    }
  }
}
