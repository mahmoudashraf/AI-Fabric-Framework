package com.loomai.desk;

import static com.loomai.desk.DeskIsolationTest.*;
import static com.loomai.desk.model.DeskContracts.*;
import static org.assertj.core.api.Assertions.*;

import com.loomai.desk.service.DeskDirectory;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

class DeskBindingCapabilitiesTest {
  private void configureSpace(JdbcTemplate jdbc, String binding) {
    jdbc.update("""
        INSERT INTO desk_support_space(space_id,binding_id,name,enabled,human_enabled,
          provider_id,provider_binding_id,runtime_base_url,runtime_trusted_key_header,
          protected_identity,protected_connector_key,origins_json,topics_json,retention_days,version)
        VALUES(?,?,'Support',TRUE,FALSE,'desk','provider-fixture','https://runtime.example',
          'X-API-KEY','unused-fixture-identity','unused-fixture-key','[]','[]',30,0)
        """, "space-" + binding, binding);
  }

  @Test
  void ticketingMetadataIsBindingScopedReadOnlyAndDoesNotCreateSpaces() {
    try (var db = new DeskTestDatabase("h2")) {
      var jdbc = new JdbcTemplate(db.dataSource);
      var directory = new DeskDirectory(jdbc, properties());
      workspace(directory, "a", "owner-a");
      workspace(directory, "b", "owner-b");
      directory.putBinding("a", binding("a", "https://runtime.example/callback"));
      directory.putBinding("b", binding("b", "https://runtime.example/callback"));
      var input = binding("a", "https://runtime.example/callback");
      directory.putBinding("a-chat", new BindingInput(input.customerId(), input.tenantId(),
          input.deploymentId(), "staging", "Conversation only", "STAGING", input.callbackUrl(),
          "chat-provider-separate-12345678901234567890", input.callbackSigningSecret(), null, null, true));
      var owner = directory.register(identity("owner-a"));
      configureSpace(jdbc, "b");
      assertThat(directory.bindings(owner)).hasSize(2)
          .allSatisfy(view -> assertThat(view.ticketingConfigured()).isFalse());
      configureSpace(jdbc, "a");
      long auditBefore = jdbc.queryForObject("SELECT COUNT(*) FROM desk_audit", Long.class);
      for (int i = 0; i < 2; i++) {
        assertThat(directory.bindings(owner)).filteredOn(v -> v.bindingId().equals("a"))
            .singleElement().satisfies(v -> assertThat(v.ticketingConfigured()).isTrue());
        assertThat(directory.bindings(owner)).filteredOn(v -> v.bindingId().equals("a-chat"))
            .singleElement().satisfies(v -> assertThat(v.ticketingConfigured()).isFalse());
      }
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM desk_support_space", Long.class)).isEqualTo(2);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM desk_audit", Long.class)).isEqualTo(auditBefore);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM desk_assignment", Long.class)).isZero();
      jdbc.update("UPDATE desk_support_space SET enabled=FALSE WHERE binding_id='a'");
      assertThat(directory.view(directory.binding("a"), java.util.List.of()).ticketingConfigured()).isTrue();
      jdbc.update("DELETE FROM desk_support_space WHERE binding_id='a'");
      assertThat(directory.bindings(owner)).allSatisfy(v -> assertThat(v.ticketingConfigured()).isFalse());
    }
  }

  @Test
  void ticketingConfigurationDoesNotGrantOwnerReviewerOrForeignSupportAccess() {
    try (var db = new DeskTestDatabase("h2")) {
      var jdbc = new JdbcTemplate(db.dataSource);
      var directory = new DeskDirectory(jdbc, properties());
      workspace(directory, "a", "owner-a");
      workspace(directory, "b", "owner-b");
      directory.putBinding("a", binding("a", "https://runtime.example/callback"));
      directory.putBinding("b", binding("b", "https://runtime.example/callback"));
      configureSpace(jdbc, "a");
      var owner = directory.register(identity("owner-a"));
      var foreign = directory.register(identity("owner-b"));
      var reviewer = directory.register(identity("reviewer"));
      var support = directory.register(identity("support"));
      directory.assign("a", owner, new AssignmentInput(null, reviewer.agentReference(), Set.of(Role.REVIEWER)));
      directory.assign("a", owner, new AssignmentInput(null, support.agentReference(), Set.of(Role.SUPPORT)));
      assertThat(directory.bindings(owner)).singleElement().satisfies(v -> {
        assertThat(v.ticketingConfigured()).isTrue();
        assertThat(v.roles()).containsExactly(Role.OWNER);
      });
      assertThat(directory.bindings(reviewer)).singleElement().satisfies(v -> {
        assertThat(v.ticketingConfigured()).isTrue();
        assertThat(v.roles()).containsExactly(Role.REVIEWER);
      });
      assertThat(directory.bindings(support)).singleElement().satisfies(v -> {
        assertThat(v.bindingId()).isEqualTo("a");
        assertThat(v.roles()).containsExactly(Role.SUPPORT);
      });
      assertThat(directory.bindings(foreign)).singleElement().satisfies(v -> {
        assertThat(v.bindingId()).isEqualTo("b");
        assertThat(v.ticketingConfigured()).isFalse();
      });
      for (var denied : java.util.List.of(owner, reviewer, foreign)) {
        assertThatThrownBy(() -> directory.authorize("a", denied, Role.SUPPORT))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(403));
      }
      assertThat(directory.authorize("a", support, Role.SUPPORT).bindingId()).isEqualTo("a");
      assertThatThrownBy(() -> directory.authorize("b", support, Role.SUPPORT))
          .isInstanceOf(ResponseStatusException.class);
    }
  }
}
