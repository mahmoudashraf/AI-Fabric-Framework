package com.loomai.desk;

import static org.assertj.core.api.Assertions.*;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;

class SupportTicketMigrationTest {
  @ParameterizedTest
  @ValueSource(strings = {"h2", "postgres"})
  void addsSpaceConversationDefaultWithoutRewritingExistingTicket(String engine) throws Exception {
    try (var db = new DeskTestDatabase(engine, "4")) {
      var jdbc = new JdbcTemplate(db.dataSource);
      var directory =
          new com.loomai.desk.service.DeskDirectory(jdbc, DeskIsolationTest.properties());
      DeskIsolationTest.workspace(directory, "preserved", "owner");
      directory.putBinding(
          "preserved", DeskIsolationTest.binding("preserved", "https://runtime.example/callback"));
      jdbc.update(
          """
          INSERT INTO desk_support_space(space_id,binding_id,name,enabled,human_enabled,provider_id,
            provider_binding_id,runtime_base_url,runtime_trusted_key_header,protected_identity,
            protected_connector_key,origins_json,topics_json,retention_days,version)
          VALUES('space','preserved','Support',TRUE,FALSE,'desk','provider','https://runtime.example',
            'X-API-KEY','encrypted-identity','encrypted-key','[]','[]',30,7)
          """);
      jdbc.update(
          "INSERT INTO desk_support_requester(space_id,requester_id) VALUES('space','owner')");
      jdbc.update(
          """
          INSERT INTO desk_support_ticket(space_id,request_id,requester_id,provider_binding_id,
            reference,protected_intake,status,version,created_at,updated_at)
          VALUES('space','request','owner','provider','REF-1','encrypted-intake','OPEN',3,
            CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
          """);
      var before = jdbc.queryForMap("SELECT * FROM desk_support_ticket WHERE request_id='request'");
      Flyway.configure()
          .dataSource(db.dataSource)
          .locations("classpath:db/desk-migration")
          .load()
          .migrate();
      assertThat(jdbc.queryForMap("SELECT * FROM desk_support_ticket WHERE request_id='request'"))
          .isEqualTo(before);
      assertThat(
              jdbc.queryForObject(
                  "SELECT conversation_json FROM desk_support_space WHERE space_id='space'",
                  String.class))
          .isEqualTo("{\"mode\":\"executor\"}");
      assertThat(
              jdbc.queryForObject(
                  "SELECT version FROM desk_support_space WHERE space_id='space'", Long.class))
          .isEqualTo(7);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"h2", "postgres"})
  void upgradesRealV3SchemaWithoutChangingExistingBindingIdentity(String engine) throws Exception {
    try (var db = new DeskTestDatabase(engine, "3")) {
      var jdbc = new JdbcTemplate(db.dataSource);
      var directory =
          new com.loomai.desk.service.DeskDirectory(jdbc, DeskIsolationTest.properties());
      DeskIsolationTest.workspace(directory, "preserved", "owner");
      seedV3Binding(jdbc);
      var before = directory.binding("preserved");
      Flyway.configure()
          .dataSource(db.dataSource)
          .locations("classpath:db/desk-migration")
          .load()
          .migrate();
      assertThat(directory.binding("preserved")).isEqualTo(before);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM desk_support_ticket", Long.class))
          .isZero();
      assertThat(
              jdbc.queryForObject("SELECT COUNT(*) FROM desk_support_ticket_tombstone", Long.class))
          .isZero();
      assertThat(
              jdbc.queryForObject(
                  "SELECT COUNT(*) FROM flyway_schema_history WHERE version='4' AND success=TRUE",
                  Long.class))
          .isEqualTo(1);
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      "INSERT INTO desk_support_requester(space_id,requester_id)"
                          + " VALUES('missing-space','requester')"))
          .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
  }

  private void seedV3Binding(JdbcTemplate jdbc) {
    // Seed the historical schema without running the current ticket-capability projection.
    var input = DeskIsolationTest.binding("preserved", "https://runtime.example/callback");
    var properties = new com.loomai.desk.channel.config.BasicInboxProperties();
    properties.setDataEncryptionSecret(DeskIsolationTest.properties().getDataEncryptionSecret());
    var secrets = new com.loomai.desk.channel.service.InboxSecurityService(properties);
    jdbc.update("""
        INSERT INTO desk_binding(binding_id,customer_id,tenant_id,deployment_id,target_profile_id,
          deployment_name,environment,callback_url,provider_key_hash,protected_provider_key,
          protected_signing_secret,review_bridge_url,protected_review_bridge_key,enabled)
        VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        """, "preserved", input.customerId(), input.tenantId(), input.deploymentId(),
        input.targetProfileId(), input.deploymentName(), input.environment(), input.callbackUrl(),
        secrets.sha256(input.providerApiKey()), secrets.encrypt(input.providerApiKey()),
        secrets.encrypt(input.callbackSigningSecret()), input.reviewBridgeUrl(),
        secrets.encrypt(input.reviewBridgeKey()), input.enabled());
  }
}
