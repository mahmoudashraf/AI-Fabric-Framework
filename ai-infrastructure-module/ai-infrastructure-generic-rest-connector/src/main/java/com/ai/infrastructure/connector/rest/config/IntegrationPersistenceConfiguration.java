package com.ai.infrastructure.connector.rest.config;

import com.ai.infrastructure.connector.rest.persistence.InMemoryIntegrationStateRepository;
import com.ai.infrastructure.connector.rest.persistence.IntegrationStateRepository;
import com.ai.infrastructure.connector.rest.persistence.JdbcIntegrationStateRepository;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

@Configuration
public class IntegrationPersistenceConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "rest-connector.persistence", name = "enabled", havingValue = "true")
    public HikariDataSource connectorDataSource(RestConnectorServiceProperties properties) {
        RestConnectorServiceProperties.Persistence persistence = properties.getPersistence();
        if (!StringUtils.hasText(persistence.getJdbcUrl())
            || !StringUtils.hasText(persistence.getUsername())
            || !StringUtils.hasText(persistence.getPassword())) {
            throw new IllegalStateException(
                "Connector persistence is enabled but JDBC URL, username, or password is missing."
            );
        }
        bootstrapRestrictedRole(persistence);
        String schema = requireSafeSchema(persistence.getSchema());
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(persistence.getJdbcUrl().trim());
        config.setUsername(persistence.getUsername().trim());
        config.setPassword(persistence.getPassword());
        config.setSchema(schema);
        config.setMaximumPoolSize(Math.max(1, Math.min(20, persistence.getMaximumPoolSize())));
        config.setPoolName("integration-connector");
        return new HikariDataSource(config);
    }

    private void bootstrapRestrictedRole(RestConnectorServiceProperties.Persistence persistence) {
        boolean bootstrapConfigured = StringUtils.hasText(persistence.getBootstrapJdbcUrl())
            || StringUtils.hasText(persistence.getBootstrapUsername())
            || StringUtils.hasText(persistence.getBootstrapPassword())
            || StringUtils.hasText(persistence.getRoleName());
        if (!bootstrapConfigured) {
            return;
        }
        if (!StringUtils.hasText(persistence.getBootstrapJdbcUrl())
            || !StringUtils.hasText(persistence.getBootstrapUsername())
            || !StringUtils.hasText(persistence.getBootstrapPassword())
            || !StringUtils.hasText(persistence.getRoleName())) {
            throw new IllegalStateException("Connector database bootstrap configuration is incomplete.");
        }
        String role = requireSafeIdentifier(persistence.getRoleName(), "role");
        String schema = requireSafeSchema(persistence.getSchema());
        try (Connection connection = DriverManager.getConnection(
            persistence.getBootstrapJdbcUrl().trim(),
            persistence.getBootstrapUsername().trim(),
            persistence.getBootstrapPassword()
        )) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.execute("DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '"
                    + sqlLiteral(role) + "') THEN CREATE ROLE " + quoteIdentifier(role)
                    + " LOGIN PASSWORD '" + sqlLiteral(persistence.getPassword()) + "'; ELSE ALTER ROLE "
                    + quoteIdentifier(role) + " LOGIN PASSWORD '" + sqlLiteral(persistence.getPassword())
                    + "'; END IF; END $$");
                statement.execute("CREATE SCHEMA IF NOT EXISTS " + quoteIdentifier(schema)
                    + " AUTHORIZATION " + quoteIdentifier(role));
                statement.execute("ALTER SCHEMA " + quoteIdentifier(schema)
                    + " OWNER TO " + quoteIdentifier(role));
                statement.execute("REVOKE ALL ON SCHEMA " + quoteIdentifier(schema) + " FROM PUBLIC");
                statement.execute("GRANT USAGE, CREATE ON SCHEMA " + quoteIdentifier(schema)
                    + " TO " + quoteIdentifier(role));
            }
            connection.commit();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to bootstrap the connector-owned database role and schema.", exception);
        }
    }

    @Bean
    public IntegrationStateRepository integrationStateRepository(
        RestConnectorServiceProperties properties,
        ObjectProvider<DataSource> connectorDataSourceProvider
    ) {
        RestConnectorServiceProperties.Persistence persistence = properties.getPersistence();
        if (persistence == null || !persistence.isEnabled()) {
            return new InMemoryIntegrationStateRepository();
        }
        DataSource connectorDataSource = connectorDataSourceProvider.getIfAvailable();
        if (connectorDataSource == null) {
            throw new IllegalStateException("Connector persistence is enabled but its DataSource was not created.");
        }
        String schema = requireSafeSchema(persistence.getSchema());
        Flyway.configure()
            .dataSource(connectorDataSource)
            .schemas(schema)
            .defaultSchema(schema)
            .createSchemas(true)
            .locations("classpath:db/migration/integration")
            .load()
            .migrate();
        return new JdbcIntegrationStateRepository(new JdbcTemplate(connectorDataSource));
    }

    private String requireSafeSchema(String value) {
        String schema = StringUtils.hasText(value) ? value.trim() : "integration_connector";
        if (!schema.matches("[a-z][a-z0-9_]{0,62}")) {
            throw new IllegalStateException("Connector persistence schema must be a safe PostgreSQL identifier.");
        }
        return schema;
    }

    private String requireSafeIdentifier(String value, String label) {
        String identifier = value.trim();
        if (!identifier.matches("[a-z][a-z0-9_]{0,62}")) {
            throw new IllegalStateException("Connector persistence " + label + " must be a safe PostgreSQL identifier.");
        }
        return identifier;
    }

    private String quoteIdentifier(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private String sqlLiteral(String value) {
        return value.replace("'", "''");
    }
}
