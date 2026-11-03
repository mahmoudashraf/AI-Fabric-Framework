package com.loomai.desk;

import static org.assertj.core.api.Assertions.*;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;

class SupportTicketMigrationTest {
  @ParameterizedTest
  @ValueSource(strings = {"h2", "postgres"})
  void upgradesRealV3SchemaWithoutChangingExistingBindingIdentity(String engine) throws Exception {
    try (var db = new DeskTestDatabase(engine, "3")) {
      var jdbc = new JdbcTemplate(db.dataSource);
      var directory =
          new com.loomai.desk.service.DeskDirectory(jdbc, DeskIsolationTest.properties());
      DeskIsolationTest.workspace(directory, "preserved", "owner");
      directory.putBinding(
          "preserved", DeskIsolationTest.binding("preserved", "https://runtime.example/callback"));
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
}
