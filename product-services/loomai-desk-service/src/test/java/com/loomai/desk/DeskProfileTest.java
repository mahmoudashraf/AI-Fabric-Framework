package com.loomai.desk;

import static com.loomai.desk.DeskIsolationTest.*;
import static com.loomai.desk.model.DeskContracts.*;
import static org.assertj.core.api.Assertions.*;

import com.loomai.desk.service.DeskDirectory;
import com.loomai.desk.service.DeskProfilePhotos;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Set;
import javax.imageio.ImageIO;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

class DeskProfileTest {
  static String photo(int width, int height) throws Exception {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    var graphics = image.createGraphics();
    graphics.setColor(Color.BLUE);
    graphics.fillRect(0, 0, width, height);
    graphics.dispose();
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ImageIO.write(image, "PNG", bytes);
    return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes.toByteArray());
  }

  @ParameterizedTest
  @ValueSource(strings = {"h2", "postgres"})
  void selfServiceProfilePersistsAcrossLoginAndDoesNotChangeAnyPermissions(String db) throws Exception {
    try (DeskTestDatabase database = new DeskTestDatabase(db)) {
      JdbcTemplate jdbc = new JdbcTemplate(database.dataSource);
      DeskDirectory directory = new DeskDirectory(jdbc, properties());
      workspace(directory, "a", "owner-a");
      directory.putBinding("a", binding("a", "http://127.0.0.1:9999/a"));
      Operator owner = directory.register(identity("owner-a"));
      Operator agent = directory.register(identity("agent"));
      directory.assign("a", owner, new AssignmentInput(null, agent.id(), Set.of(Role.SUPPORT)));
      Operator updated = directory.updateProfile(agent, new ProfileInput("  Sam Support  ", photo(160, 80), false));
      assertThat(updated.id()).isEqualTo(agent.id());
      assertThat(updated.email()).isEqualTo(agent.email());
      assertThat(updated.displayName()).isEqualTo("Sam Support");
      assertThat(updated.avatarUrl()).startsWith("/api/desk/v1/agents/" + agent.id() + "/photo?v=");
      assertThat(directory.register(identity("agent")).displayName()).isEqualTo("Sam Support");
      assertThat(directory.roles(directory.binding("a"), agent.id())).containsExactly(Role.SUPPORT);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM desk_owner WHERE operator_id=?", Long.class, agent.id())).isZero();
      assertThat(directory.agents("a").getFirst().avatarUrl()).isEqualTo(updated.avatarUrl());
      assertThat(directory.profiles("a", agent)).containsExactly(new AgentProfile(agent.id(), "Sam Support", updated.avatarUrl()));
      byte[] saved = directory.photo(owner, agent.id());
      BufferedImage normalized = ImageIO.read(new ByteArrayInputStream(saved));
      assertThat(normalized.getWidth()).isEqualTo(256);
      assertThat(normalized.getHeight()).isEqualTo(256);
      assertThat(saved[0]).isEqualTo((byte) 0xff);
      assertThat(saved[1]).isEqualTo((byte) 0xd8);
      String revision = updated.avatarUrl();
      updated = directory.updateProfile(updated, new ProfileInput("Sam Renamed", null, false));
      assertThat(updated.avatarUrl()).isEqualTo(revision);
      assertThat(directory.photo(agent, agent.id())).containsExactly(saved);
      updated = directory.updateProfile(updated, new ProfileInput("Sam Renamed", null, true));
      assertThat(updated.avatarUrl()).isNull();
      assertThatThrownBy(() -> directory.photo(agent, agent.id())).isInstanceOf(ResponseStatusException.class);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM desk_audit WHERE operation='AGENT_PROFILE_UPDATE' AND actor_id=?", Long.class, agent.id())).isEqualTo(3);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"h2", "postgres"})
  void photosAndProfileDirectoryRequireCurrentSharedDeploymentAccess(String db) throws Exception {
    try (DeskTestDatabase database = new DeskTestDatabase(db)) {
      DeskDirectory directory = new DeskDirectory(new JdbcTemplate(database.dataSource), properties());
      workspace(directory, "a", "owner-a");
      workspace(directory, "b", "owner-b");
      directory.putBinding("a", binding("a", "http://127.0.0.1:9999/a"));
      directory.putBinding("b", binding("b", "http://127.0.0.1:9999/b"));
      Operator a = directory.register(identity("owner-a"));
      Operator b = directory.register(identity("owner-b"));
      Operator agent = directory.register(identity("agent"));
      directory.updateProfile(agent, new ProfileInput("Sam", photo(80, 80), false));
      directory.assign("a", a, new AssignmentInput(null, agent.id(), Set.of(Role.SUPPORT)));
      assertThat(directory.photo(agent, agent.id())).isNotEmpty();
      assertThat(directory.photo(a, agent.id())).isNotEmpty();
      assertThatThrownBy(() -> directory.photo(b, agent.id())).isInstanceOf(ResponseStatusException.class);
      assertThatThrownBy(() -> directory.profiles("a", b)).isInstanceOf(ResponseStatusException.class);
      directory.revoke("a", a, agent.id());
      assertThatThrownBy(() -> directory.photo(a, agent.id())).isInstanceOf(ResponseStatusException.class);
      assertThat(directory.photo(agent, agent.id())).isNotEmpty();
      assertThat(directory.profiles("a", a)).isEmpty();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"h2", "postgres"})
  void additiveMigrationPreservesExistingAgentsNamesAndAssignments(String db) throws Exception {
    try (DeskTestDatabase database = new DeskTestDatabase(db, "2")) {
      JdbcTemplate jdbc = new JdbcTemplate(database.dataSource);
      String legacyId = "agent-" + java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
          .digest((ISSUER + "\nlegacy").getBytes(java.nio.charset.StandardCharsets.UTF_8))).substring(0, 40);
      jdbc.update("INSERT INTO desk_operator(operator_id,issuer,subject,email,display_name,registered,created_at) VALUES(?,?,'legacy','legacy@example.test','Existing Agent',TRUE,CURRENT_TIMESTAMP)", legacyId, ISSUER);
      jdbc.update("INSERT INTO desk_workspace(customer_id,name) VALUES('legacy-customer','Existing workspace')");
      jdbc.update("INSERT INTO desk_owner(customer_id,operator_id) VALUES('legacy-customer',?)", legacyId);
      Flyway.configure().dataSource(database.dataSource).locations("classpath:db/desk-migration").load().migrate();
      DeskDirectory directory = new DeskDirectory(jdbc, properties());
      Operator existing = directory.register(identity("legacy"));
      assertThat(existing.id()).isEqualTo(legacyId);
      assertThat(existing.displayName()).isEqualTo("Existing Agent");
      assertThat(existing.avatarUrl()).isNull();
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM desk_owner WHERE customer_id='legacy-customer' AND operator_id=?", Long.class, legacyId)).isEqualTo(1);
    }
  }

  @Test
  void rejectsActiveContentExternalUrlsInvalidImagesAndUnboundedDecode() throws Exception {
    for (String invalid : new String[] {
        "https://example.test/photo.jpg", "data:image/svg+xml;base64,PHN2Zy8+",
        "data:image/jpeg;base64,invalid", "data:image/png;base64,AAAA", photo(1025, 2),
        "data:image/png;base64," + "A".repeat(400000) }) {
      assertThatThrownBy(() -> DeskProfilePhotos.normalize(invalid)).isInstanceOf(ResponseStatusException.class);
    }
  }
}
