package com.ai.fabric.platform.backend.deployment.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/** Real PostgreSQL, real TLS and real runtime/connector migrations. Missing Docker is a failure, never a skip. */
@Testcontainers
class DeploymentAiStateStorageProbePostgresTest {
    private static final Path TLS = certificate();
    @Container private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withCopyFileToContainer(MountableFile.forHostPath(TLS.resolve("server.key"), 0644), "/tmp/tls-source/server.key")
        .withCopyFileToContainer(MountableFile.forHostPath(TLS.resolve("server.crt"), 0644), "/tmp/tls-source/server.crt")
        .withCreateContainerCmdModifier(command -> command.withEntrypoint("/bin/sh", "-ec"))
        .withCommand(new String[]{"cp /tmp/tls-source/server.key /tmp/server.key; chown postgres:postgres /tmp/server.key; "
            + "chmod 600 /tmp/server.key; exec docker-entrypoint.sh postgres -c ssl=on "
            + "-c ssl_cert_file=/tmp/tls-source/server.crt -c ssl_key_file=/tmp/server.key"});
    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static SSLContext priorContext;
    private final DeploymentAiStateStorageProbe probe = new DeploymentAiStateStorageProbe();
    private String runtimeSchema, connectorSchema, businessSchema, role, inheritedRole;

    @BeforeAll static void trustFixtureCertificateOnly() throws Exception {
        assertThat(POSTGRES.getHost()).isIn("localhost", "127.0.0.1");
        priorContext = SSLContext.getDefault();
        var store = KeyStore.getInstance(KeyStore.getDefaultType()); store.load(null);
        try (var input = Files.newInputStream(TLS.resolve("server.crt"))) {
            store.setCertificateEntry("local-fixture", CertificateFactory.getInstance("X.509").generateCertificate(input));
        }
        var trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()); trust.init(store);
        var context = SSLContext.getInstance("TLS"); context.init(null, trust.getTrustManagers(), null); SSLContext.setDefault(context);
    }

    @AfterAll static void restoreTrustAndRemoveFixtureKeys() throws Exception {
        if (priorContext != null) SSLContext.setDefault(priorContext);
        try (var paths = Files.walk(TLS)) {
            for (var path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    @BeforeEach void setup() throws Exception {
        int n = SEQUENCE.incrementAndGet(); runtimeSchema = "ai_runtime_" + n; connectorSchema = "ai_connector_" + n;
        businessSchema = "business_" + n; role = "ai_role_" + n; inheritedRole = "owner_role_" + n;
        execute("REVOKE ALL ON SCHEMA public FROM PUBLIC");
        execute("CREATE ROLE " + role + " LOGIN NOINHERIT PASSWORD 'fixture-only-password'");
        execute("CREATE SCHEMA " + runtimeSchema + " AUTHORIZATION " + role);
        execute("CREATE SCHEMA " + connectorSchema + " AUTHORIZATION " + role);
        execute("CREATE SCHEMA " + businessSchema);
        execute("CREATE TABLE " + businessSchema + ".inventory(id integer primary key, description text)");
        execute("INSERT INTO " + businessSchema + ".inventory VALUES (1,'Customer-owned stock must remain unchanged')");
    }

    @AfterEach void cleanup() throws Exception {
        execute("DROP SCHEMA " + runtimeSchema + " CASCADE");
        execute("DROP SCHEMA " + connectorSchema + " CASCADE");
        execute("DROP SCHEMA " + businessSchema + " CASCADE");
        execute("DROP OWNED BY " + role);
        execute("DROP ROLE " + role);
        execute("DROP ROLE IF EXISTS " + inheritedRole);
    }

    @Test void verifiedTlsAndDedicatedSchemasPassWithoutMutatingBusinessData() throws Exception {
        probe.verify(connection("localhost"));
        try (var connection = DriverManager.getConnection(connection("localhost").runtimeJdbcUrl(), role, "fixture-only-password");
             var statement = connection.createStatement(); var rows = statement.executeQuery("select ssl from pg_stat_ssl where pid=pg_backend_pid()")) {
            assertThat(rows.next()).isTrue(); assertThat(rows.getBoolean(1)).isTrue();
            assertThatThrownBy(() -> connection.createStatement().executeQuery("select * from " + businessSchema + ".inventory"))
                .hasMessageContaining("permission denied");
            assertThatThrownBy(() -> connection.createStatement().execute("create schema forbidden_business"))
                .hasMessageContaining("permission denied");
        }
        assertBusinessUnchanged();
    }

    @Test void wrongTlsHostnameAndWrongCredentialsFailWithRedactedErrors() {
        assertThatThrownBy(() -> probe.verify(connection("127.0.0.1")))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("CONNECTION_VERIFICATION_FAILED")
            .hasMessageNotContaining("fixture-only-password").hasMessageNotContaining("127.0.0.1");
        var expected = connection("localhost");
        assertThatThrownBy(() -> probe.verify(new DeploymentAiStateStorageConnection(expected.host(), expected.port(),
            expected.databaseName(), runtimeSchema, connectorSchema, role, "wrong-fixture-password")))
            .hasMessageContaining("CONNECTION_VERIFICATION_FAILED").hasMessageNotContaining("wrong-fixture-password");
    }

    @ParameterizedTest @ValueSource(strings = {"SUPERUSER", "CREATEDB", "CREATEROLE", "REPLICATION", "BYPASSRLS",
        "GLOBAL_READ", "GLOBAL_WRITE", "DATABASE_CREATE", "BUSINESS_SELECT", "BUSINESS_WRITE", "NOINHERIT_ROLE", "SECURITY_DEFINER"})
    void businessAndEscalationAuthorityIsRejectedEvenWhenSchemasAreDedicated(String privilege) throws Exception {
        switch (privilege) {
            case "SUPERUSER", "CREATEDB", "CREATEROLE", "REPLICATION", "BYPASSRLS" -> execute("ALTER ROLE " + role + " " + privilege);
            case "GLOBAL_READ" -> execute("GRANT pg_read_all_data TO " + role);
            case "GLOBAL_WRITE" -> execute("GRANT pg_write_all_data TO " + role);
            case "DATABASE_CREATE" -> execute("GRANT CREATE ON DATABASE " + POSTGRES.getDatabaseName() + " TO " + role);
            case "BUSINESS_SELECT", "BUSINESS_WRITE" -> {
                execute("GRANT USAGE ON SCHEMA " + businessSchema + " TO " + role);
                execute("GRANT " + (privilege.equals("BUSINESS_SELECT") ? "SELECT" : "INSERT,UPDATE,DELETE")
                    + " ON " + businessSchema + ".inventory TO " + role);
            }
            case "NOINHERIT_ROLE" -> {
                execute("CREATE ROLE " + inheritedRole);
                execute("GRANT " + inheritedRole + " TO " + role);
                execute("GRANT USAGE ON SCHEMA " + businessSchema + " TO " + inheritedRole);
                execute("GRANT SELECT,UPDATE ON " + businessSchema + ".inventory TO " + inheritedRole);
            }
            case "SECURITY_DEFINER" -> {
                execute("CREATE FUNCTION " + businessSchema + ".write_business() RETURNS void LANGUAGE sql SECURITY DEFINER AS $$ "
                    + "UPDATE " + businessSchema + ".inventory SET description='forbidden' WHERE id=1 $$");
                execute("GRANT USAGE ON SCHEMA " + businessSchema + " TO " + role);
            }
        }
        assertThatThrownBy(() -> probe.verify(connection("localhost")))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("AI_STATE_STORAGE_");
        assertBusinessUnchanged();
    }

    @Test void actualRuntimeAndConnectorFlywayMigrationsStayInSeparateAiSchemasAcrossRestart() throws Exception {
        var expected = connection("localhost"); probe.verify(expected);
        String root = System.getProperty("repoRoot", Path.of("../..").toAbsolutePath().normalize().toString());
        var runtime = migrate(expected.runtimeJdbcUrl(), runtimeSchema,
            "filesystem:" + root + "/ai-infrastructure-module/ai-fabric-runtime/src/main/resources/db/runtime-migration");
        var connector = migrate(expected.connectorJdbcUrl(), connectorSchema,
            "filesystem:" + root + "/ai-infrastructure-module/ai-infrastructure-generic-rest-connector/src/main/resources/db/migration/integration");
        assertThat(runtime.migrationsExecuted).isPositive(); assertThat(connector.migrationsExecuted).isEqualTo(4);
        assertThat(migrate(expected.runtimeJdbcUrl(), runtimeSchema,
            "filesystem:" + root + "/ai-infrastructure-module/ai-fabric-runtime/src/main/resources/db/runtime-migration").migrationsExecuted).isZero();
        assertThat(migrate(expected.connectorJdbcUrl(), connectorSchema,
            "filesystem:" + root + "/ai-infrastructure-module/ai-infrastructure-generic-rest-connector/src/main/resources/db/migration/integration").migrationsExecuted).isZero();
        assertThat(tableSchema("ai_conversation_control")).isEqualTo(runtimeSchema);
        assertThat(tableSchema("integration_sync_state")).isEqualTo(connectorSchema);
        probe.verify(expected);
        assertBusinessUnchanged();
    }

    @Test void platformMigrationChainIncludesRetainedBindingsAndFrozenReleaseIdentity() throws Exception {
        String root = System.getProperty("repoRoot", Path.of("../..").toAbsolutePath().normalize().toString());
        var migrations = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .schemas(runtimeSchema).defaultSchema(runtimeSchema).createSchemas(false)
            .locations("filesystem:" + root + "/Platfrom/backend/src/main/resources/db/migration").load();
        migrations.migrate();
        assertThat(migrations.info().applied()).extracting(value -> value.getVersion().getVersion()).contains("198", "200");
        assertThat(migrations.info().pending()).isEmpty();
        try (var connection = admin(); var statement = connection.prepareStatement(
            "select column_name from information_schema.columns where table_schema=? and table_name='platform_deployment_releases' "
                + "and column_name in ('ai_state_storage_snapshot_json','ai_state_storage_fingerprint')")) {
            statement.setString(1, runtimeSchema);
            try (var rows = statement.executeQuery()) { int count=0; while (rows.next()) count++; assertThat(count).isEqualTo(2); }
        }
        String claims = runtimeSchema + ".deployment_ai_state_schema_claims";
        execute("INSERT INTO " + claims + "(schema_identity,binding_id,customer_id,tenant_id,deployment_id,target_profile_id)"
            + " VALUES ('historical-schema','deleted-binding','original-customer','original-tenant','deleted-deployment','staging')");
        assertThatThrownBy(() -> execute("INSERT INTO " + claims
            + "(schema_identity,binding_id,customer_id,tenant_id,deployment_id,target_profile_id)"
            + " VALUES ('historical-schema','new-binding','foreign-customer','foreign-tenant','new-deployment','production')"))
            .isInstanceOf(java.sql.SQLException.class).hasMessageContaining("duplicate key");
        try (var connection = admin(); var statement = connection.createStatement(); var rows = statement.executeQuery(
            "SELECT customer_id FROM " + claims + " WHERE schema_identity='historical-schema'")) {
            assertThat(rows.next()).isTrue(); assertThat(rows.getString(1)).isEqualTo("original-customer");
        }
        assertBusinessUnchanged();
    }

    @Test void customerCanSupplyANewRestrictedLoginForTheSameExistingAiSchemasWithoutChangingTheOldLogin() throws Exception {
        var old = connection("localhost");
        String root = System.getProperty("repoRoot", Path.of("../..").toAbsolutePath().normalize().toString());
        String runtimeLocation = "filesystem:" + root + "/ai-infrastructure-module/ai-fabric-runtime/src/main/resources/db/runtime-migration";
        String connectorLocation = "filesystem:" + root + "/ai-infrastructure-module/ai-infrastructure-generic-rest-connector/src/main/resources/db/migration/integration";
        migrate(old.runtimeJdbcUrl(), runtimeSchema, runtimeLocation);
        migrate(old.connectorJdbcUrl(), connectorSchema, connectorLocation);
        // The customer prepares a second restricted login; Platform never issues these role commands.
        execute("CREATE ROLE " + inheritedRole + " LOGIN INHERIT PASSWORD 'new-fixture-password'");
        execute("GRANT " + role + " TO " + inheritedRole);
        var replacement = new DeploymentAiStateStorageConnection(old.host(), old.port(), old.databaseName(), runtimeSchema,
            connectorSchema, inheritedRole, "new-fixture-password");
        probe.verify(old);
        probe.verify(replacement);
        assertThat(Flyway.configure().dataSource(replacement.runtimeJdbcUrl(), inheritedRole, "new-fixture-password")
            .schemas(runtimeSchema).defaultSchema(runtimeSchema).createSchemas(false).locations(runtimeLocation).load().migrate().migrationsExecuted).isZero();
        assertThat(Flyway.configure().dataSource(replacement.connectorJdbcUrl(), inheritedRole, "new-fixture-password")
            .schemas(connectorSchema).defaultSchema(connectorSchema).createSchemas(false).locations(connectorLocation).load().migrate().migrationsExecuted).isZero();
        probe.verify(old);
        assertBusinessUnchanged();
    }

    private org.flywaydb.core.api.output.MigrateResult migrate(String url, String schema, String location) {
        return Flyway.configure().dataSource(url, role, "fixture-only-password").schemas(schema).defaultSchema(schema)
            .createSchemas(false).locations(location).load().migrate();
    }
    private String tableSchema(String table) throws Exception {
        try (var connection = admin(); var statement = connection.prepareStatement(
            "select table_schema from information_schema.tables where table_name=? and table_schema in (?,?)")) {
            statement.setString(1, table); statement.setString(2, runtimeSchema); statement.setString(3, connectorSchema);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue(); String schema = result.getString(1); assertThat(result.next()).isFalse(); return schema;
            }
        }
    }
    private void assertBusinessUnchanged() throws Exception {
        try (var connection = admin(); var statement = connection.createStatement(); var result = statement.executeQuery(
            "select description from " + businessSchema + ".inventory where id=1")) {
            assertThat(result.next()).isTrue(); assertThat(result.getString(1)).isEqualTo("Customer-owned stock must remain unchanged");
        }
    }
    private DeploymentAiStateStorageConnection connection(String host) {
        return new DeploymentAiStateStorageConnection(host, POSTGRES.getMappedPort(5432), POSTGRES.getDatabaseName(),
            runtimeSchema, connectorSchema, role, "fixture-only-password");
    }
    private Connection admin() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
    private void execute(String sql) throws Exception {
        try (var connection = admin(); var statement = connection.createStatement()) { statement.execute(sql); }
    }
    private static Path certificate() {
        try {
            Path directory = Files.createTempDirectory("loomai-sql-tls-");
            var process = new ProcessBuilder("openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes", "-days", "1",
                "-subj", "/CN=localhost", "-keyout", directory.resolve("server.key").toString(), "-out", directory.resolve("server.crt").toString())
                .redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            if (process.waitFor() != 0) throw new IllegalStateException("Cannot create local TLS fixture: " + output);
            return directory;
        } catch (Exception error) { throw new IllegalStateException("Cannot initialize PostgreSQL TLS fixture", error); }
    }
}
