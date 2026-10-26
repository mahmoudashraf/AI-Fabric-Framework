package com.loomai.desk;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

final class DeskTestDatabase implements AutoCloseable {
  final DataSource dataSource;
  final String schema;
  final DriverManagerDataSource root;
  final String url;
  final String username;
  final String password;

  DeskTestDatabase(String engine) {
    schema = "desk_" + UUID.randomUUID().toString().replace("-", "");
    if (engine.equals("postgres")) {
      String source =
          System.getProperty("desk.test.postgres.url", System.getenv("DESK_TEST_POSTGRES_URL"));
      if (Boolean.getBoolean("desk.test.postgres.required") && (source == null || source.isBlank()))
        throw new IllegalStateException("PostgreSQL test URL required.");
      Assumptions.assumeTrue(
          source != null && !source.isBlank(),
          "Set desk.test.postgres.url to run real PostgreSQL cases.");
      root =
          new DriverManagerDataSource(
              source,
              System.getProperty("desk.test.postgres.user", "desk"),
              System.getProperty("desk.test.postgres.password", "desk-tests-local-only"));
      new JdbcTemplate(root).execute("CREATE SCHEMA " + schema);
      url = source + (source.contains("?") ? "&" : "?") + "currentSchema=" + schema;
      username = root.getUsername();
      password = root.getPassword();
      dataSource = new DriverManagerDataSource(url, username, password);
    } else {
      root = null;
      url = "jdbc:h2:mem:" + schema + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
      username = "sa";
      password = "";
      // Match production pooling: H2 2.4 check-expression sessions cannot be physically closed
      // after migration.
      HikariConfig config = new HikariConfig();
      config.setJdbcUrl(url);
      config.setUsername(username);
      config.setPassword(password);
      config.setMaximumPoolSize(4);
      dataSource = new HikariDataSource(config);
    }
    Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/desk-migration")
        .load()
        .migrate();
  }

  @Override
  public void close() {
    if (root != null) new JdbcTemplate(root).execute("DROP SCHEMA " + schema + " CASCADE");
    else {
      new JdbcTemplate(dataSource).execute("DROP ALL OBJECTS");
      ((HikariDataSource) dataSource).close();
    }
  }
}
