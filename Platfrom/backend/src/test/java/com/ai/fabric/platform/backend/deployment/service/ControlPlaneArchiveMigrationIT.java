package com.ai.fabric.platform.backend.deployment.service;

import org.flywaydb.core.Flyway;
import org.testcontainers.DockerClientFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Explicit local rehearsal only. Never starts Spring, workers, or a hosted connection. */
class ControlPlaneArchiveMigrationIT {
    private static final DockerImageName IMAGE = DockerImageName.parse(
        "postgres@sha256:77f585114c32fbca283dc835b0596f4e52b51b4c6662d7810b2f4084f60a1873");
    private static final Set<PosixFilePermission> PRIVATE_FILE = Set.of(
        PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
    private static final Set<PosixFilePermission> PRIVATE_DIRECTORY = Set.of(
        PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE);
    private static final String[] PRESERVED_TABLES = {
        "platform_customers", "platform_customer_tenants", "platform_deployments",
        "platform_deployment_drafts", "platform_deployment_versions", "platform_deployment_releases",
        "platform_users", "platform_user_sessions", "platform_secrets", "deployment_target_profiles",
        "deployment_provider_credentials", "deployment_capacity_reservations",
        "platform_tenant_scoped_vector_resources", "ai_workspace_installations",
        "deployment_provider_resource_handles", "platform_deployment_managed_vector_resources",
        "deployment_managed_dependency_graphs", "platform_managed_product_services",
        "platform_customer_offers", "platform_customer_offer_entitlements", "platform_customer_offer_deployments"
    };

    @ParameterizedTest
    @ValueSource(strings = {"staging", "production"})
    void migratesActualPrivateArchiveWithoutChangingExistingCustomerState(String environment) throws Exception {
        assertEquals("true", System.getenv("RUN_01044_ARCHIVE_MIGRATION"), "Explicit local rehearsal opt-in required");
        String directory = System.getenv("CONTROL_PLANE_ARCHIVE_DIRECTORY");
        assertTrue(directory != null && !directory.isBlank(), "Explicit private archive directory required");
        Path root = Path.of(directory);
        assertTrue(root.isAbsolute() && root.normalize().equals(root) && root.toRealPath().equals(root));
        assertEquals(PRIVATE_DIRECTORY, Files.getPosixFilePermissions(root));
        Path archive = root.resolve(environment + ".dump");
        assertTrue(Files.isRegularFile(archive, LinkOption.NOFOLLOW_LINKS));
        assertEquals(archive, archive.toRealPath());
        assertEquals(PRIVATE_FILE, Files.getPosixFilePermissions(archive));
        try (var input = Files.newInputStream(archive)) {
            assertEquals("PGDMP", new String(input.readNBytes(5), java.nio.charset.StandardCharsets.US_ASCII));
        }
        String configuredHost = System.getenv("DOCKER_HOST");
        assertTrue(configuredHost == null || configuredHost.startsWith("unix:///"), "Remote Docker is forbidden");
        var dockerHost = DockerClientFactory.instance().getTransportConfig().getDockerHost();
        assertEquals("unix", dockerHost.getScheme(), "Only the local Docker Unix socket is allowed");
        assertTrue(dockerHost.getPath() != null && Path.of(dockerHost.getPath()).isAbsolute());
        assertTrue(Set.of("localhost", "127.0.0.1").contains(DockerClientFactory.instance().dockerHostIpAddress()));

        // Docker internal networks suppress published ports. Use a fresh private
        // bridge with a loopback-only binding so the local Flyway JVM can connect.
        try (Network network = Network.newNetwork();
             PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(IMAGE)
                 .withImagePullPolicy(image -> false)
                 .withNetwork(network)
                 .withDatabaseName("archive_rehearsal")
                 .withUsername("local_rehearsal")
                 .withPassword(java.util.UUID.randomUUID().toString())
                 .withCreateContainerCmdModifier(command -> command.getHostConfig()
                     .withPortBindings(new PortBinding(Ports.Binding.bindIp("127.0.0.1"), ExposedPort.tcp(5432)))
                     .withMemory(768L * 1024 * 1024).withNanoCPUs(1_000_000_000L))) {
            postgres.start();
            Process restore = new ProcessBuilder("docker", "--host", dockerHost.toString(), "exec", "--interactive", postgres.getContainerId(),
                "pg_restore", "--exit-on-error", "--single-transaction", "--no-owner", "--no-privileges",
                "--no-tablespaces", "--no-security-labels", "--no-subscriptions", "--no-password",
                "--username", postgres.getUsername(), "--dbname", postgres.getDatabaseName())
                .redirectInput(archive.toFile()).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            if (!restore.waitFor(120, TimeUnit.SECONDS)) {
                restore.destroyForcibly();
                throw new AssertionError("Private archive restore timed out; diagnostics withheld");
            }
            assertEquals(0, restore.exitValue(), "Private archive restore failed; diagnostics withheld");

            // Only aggregates/hashes leave the isolated database, never rows or stored credentials.
            try (Connection connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
                assertEquals("18", scalar(connection, "SELECT current_setting('server_version_num')::int / 10000"));
                assertEquals("195", scalar(connection, "SELECT max(version::int) FROM flyway_schema_history WHERE success"));
                Map<String, String> before = preserved(connection);
                Flyway flyway = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration").load();
                var validation = flyway.validateWithResult();
                if (!validation.validationSuccessful) {
                    Set<String> codes = validation.invalidMigrations.stream().map(value ->
                        (value.version != null && value.version.matches("[0-9.]{1,20}") ? value.version : "unversioned")
                            + ":" + value.errorDetails.errorCode).collect(java.util.stream.Collectors.toSet());
                    assertEquals(Set.of(
                        "196:RESOLVED_VERSIONED_MIGRATION_NOT_APPLIED", "197:RESOLVED_VERSIONED_MIGRATION_NOT_APPLIED",
                        "198:RESOLVED_VERSIONED_MIGRATION_NOT_APPLIED", "199:RESOLVED_VERSIONED_MIGRATION_NOT_APPLIED",
                        "200:RESOLVED_VERSIONED_MIGRATION_NOT_APPLIED", "201:RESOLVED_VERSIONED_MIGRATION_NOT_APPLIED"), codes,
                        "Only the exact six pending migrations may precede the normal migrate validation");
                }
                try {
                    assertEquals(6, flyway.migrate().migrationsExecuted);
                    flyway.validate();
                } catch (RuntimeException failure) {
                    throw new AssertionError("Archive migration/validation failed; private diagnostics withheld");
                }
                assertEquals(before, preserved(connection), "Existing customer/credential/resource state must remain unchanged");
                assertEquals("201", scalar(connection, "SELECT max(version::int) FROM flyway_schema_history WHERE success"));
                assertEquals("201", scalar(connection, "SELECT count(*) FROM flyway_schema_history WHERE success"));
                assertEquals("5", scalar(connection, "SELECT count(*) FROM platform_marketplace_plugins WHERE id IN "
                    + "('mkp-data-dealership-external-stock-v1','mkp-action-dealership-external-discovery-v1',"
                    + "'mkp-action-dealership-external-engagement-v1','mkp-template-dealership-external-systems-v1',"
                    + "'mkp-template-dealership-external-systems-managed-inbox-v1')"));
                assertEquals("0", scalar(connection, "SELECT count(*) FROM platform_managed_resource_ownership"));
                assertEquals("0", scalar(connection, "SELECT count(*) FROM platform_managed_service_ownership"));
                assertEquals("0", scalar(connection, "SELECT count(*) FROM platform_deployment_releases "
                    + "WHERE ai_state_storage_snapshot_json IS NOT NULL OR ai_state_storage_fingerprint IS NOT NULL"));
            } catch (java.sql.SQLException failure) {
                throw new AssertionError("Archive integrity query failed; private diagnostics withheld");
            }
        }
    }

    private Map<String, String> preserved(Connection connection) throws java.sql.SQLException {
        Map<String, String> result = new LinkedHashMap<>();
        for (String table : PRESERVED_TABLES) {
            // V198 adds nullable columns, not historical binding ownership. Compare
            // every original field and separately require both new fields to stay null.
            String row = "platform_deployment_releases".equals(table)
                ? "(to_jsonb(t)-'ai_state_storage_snapshot_json'-'ai_state_storage_fingerprint')"
                : "to_jsonb(t)";
            result.put(table, scalar(connection, "SELECT count(*)::text || ':' || encode(sha256(convert_to("
                + "coalesce(string_agg(" + row + "::text,E'\\n' ORDER BY " + row + "::text),''),'UTF8')),'hex') FROM "
                + table + " t"));
        }
        return result;
    }

    private String scalar(Connection connection, String sql) throws java.sql.SQLException {
        try (var statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            assertTrue(rows.next());
            return rows.getString(1);
        }
    }
}
