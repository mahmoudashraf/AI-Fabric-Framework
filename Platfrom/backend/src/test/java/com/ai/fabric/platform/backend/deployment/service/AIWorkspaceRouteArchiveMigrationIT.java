package com.ai.fabric.platform.backend.deployment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Opt-in local archive rehearsal; no Spring context, workers, or hosted clients. */
class AIWorkspaceRouteArchiveMigrationIT {
    private static final DockerImageName IMAGE = DockerImageName.parse(
        "postgres@sha256:77f585114c32fbca283dc835b0596f4e52b51b4c6662d7810b2f4084f60a1873");
    private static final String ROUTE_TABLE = "ai_workspace_route_reconciliations";
    private static final Set<PosixFilePermission> PRIVATE_FILE = Set.of(
        PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
    private static final Set<PosixFilePermission> PRIVATE_DIRECTORY = Set.of(
        PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE);
    private static final Map<String, ReviewedArchive> ARCHIVES = Map.of(
        "staging", new ReviewedArchive("m58iwvqdkfie8tykohmhyj7t", 8784710,
            "0c719e73c45848bf3d47d4db79738d20745102bcc2ce4ed6ac7c4ab7602204e7"),
        "production", new ReviewedArchive("nkti6x5r7ovw1xx8q0ykhweq", 7733936,
            "280b565d79c892e141d0507ade48daa0b93d55f699ec89faf637e8922548f6e7"));

    @ParameterizedTest
    @ValueSource(strings = {"staging", "production"})
    void migratesActual205ArchiveWithoutChangingExistingState(String environment) throws Exception {
        assertEquals("true", System.getenv("RUN_01044_ROUTE_ARCHIVE_MIGRATION"), "Explicit local opt-in required");
        String directory = System.getenv("CONTROL_PLANE_ROUTE_ARCHIVE_DIRECTORY");
        assertTrue(directory != null && !directory.isBlank(), "Private archive directory required");
        Path root = Path.of(directory);
        assertTrue(root.isAbsolute() && root.normalize().equals(root) && root.toRealPath().equals(root));
        assertEquals(PRIVATE_DIRECTORY, Files.getPosixFilePermissions(root));
        Path archive = reviewedArchive(root, environment);
        String configuredHost = System.getenv("DOCKER_HOST");
        assertTrue(configuredHost == null || configuredHost.startsWith("unix:///"), "Remote Docker is forbidden");
        var dockerHost = DockerClientFactory.instance().getTransportConfig().getDockerHost();
        assertEquals("unix", dockerHost.getScheme());
        assertTrue(dockerHost.getPath() != null && Path.of(dockerHost.getPath()).isAbsolute());
        assertTrue(Set.of("localhost", "127.0.0.1").contains(DockerClientFactory.instance().dockerHostIpAddress()));

        try (Network network = Network.newNetwork();
             PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(IMAGE)
                 .withImagePullPolicy(image -> false).withNetwork(network)
                 .withDatabaseName("route_archive_rehearsal").withUsername("local_rehearsal")
                 .withPassword(java.util.UUID.randomUUID().toString())
                 .withCreateContainerCmdModifier(command -> command.getHostConfig()
                     .withPortBindings(new PortBinding(Ports.Binding.bindIp("127.0.0.1"), ExposedPort.tcp(5432)))
                     .withMemory(768L * 1024 * 1024).withNanoCPUs(1_000_000_000L))) {
            postgres.start();
            Process restore = new ProcessBuilder("docker", "--host", dockerHost.toString(), "exec", "--interactive",
                postgres.getContainerId(), "pg_restore", "--exit-on-error", "--single-transaction", "--no-owner",
                "--no-privileges", "--no-tablespaces", "--no-security-labels", "--no-subscriptions", "--no-password",
                "--username", postgres.getUsername(), "--dbname", postgres.getDatabaseName())
                .redirectInput(archive.toFile()).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            if (!restore.waitFor(120, TimeUnit.SECONDS)) {
                restore.destroyForcibly();
                throw new AssertionError("Archive restore timed out; diagnostics withheld");
            }
            assertEquals(0, restore.exitValue(), "Archive restore failed; diagnostics withheld");
            try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
                assertEquals("18", scalar(connection, "SELECT current_setting('server_version_num')::int / 10000"));
                assertEquals("205", scalar(connection, "SELECT max(version::int) FROM flyway_schema_history WHERE success"));
                assertEquals("0", scalar(connection, "SELECT count(*) FROM flyway_schema_history WHERE NOT success"));
                Set<String> originalTables = tableNames(connection);
                assertEquals(137, originalTables.size());
                assertTrue(!originalTables.contains(ROUTE_TABLE));
                Map<String, TableSnapshot> before = snapshot(connection, originalTables);
                Fingerprint history = fingerprint(connection, "SELECT to_jsonb(h) AS entry FROM flyway_schema_history h");
                Flyway flyway = Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration").target("206").load();
                try {
                    assertEquals(1, flyway.migrate().migrationsExecuted);
                    flyway.validate();
                    assertPreserved(connection, originalTables, before, history);
                    assertEquals(0, flyway.migrate().migrationsExecuted);
                    flyway.validate();
                    assertPreserved(connection, originalTables, before, history);
                } catch (RuntimeException failure) {
                    throw new AssertionError("Route archive migration failed; diagnostics withheld");
                }
                System.out.println("ARCHIVE_205_TO_206_PASS " + new ObjectMapper().writeValueAsString(Map.of(
                    "environment", environment, "archiveSha256", ARCHIVES.get(environment).sha256(),
                    "from", "205", "to", "206", "migrationsExecuted", 1, "replayMigrationsExecuted", 0,
                    "preservedTableCount", before.size(), "originalTables", before, "originalHistory", history)));
            } catch (java.sql.SQLException failure) {
                throw new AssertionError("Archive integrity query failed; diagnostics withheld");
            }
        }
    }

    private Path reviewedArchive(Path root, String environment) throws Exception {
        Path manifest = root.resolve("manifest.json");
        privateFile(manifest);
        var resources = new ObjectMapper().readTree(manifest.toFile()).path("resources");
        assertTrue(resources.isArray() && resources.size() == ARCHIVES.size());
        ReviewedArchive reviewed = ARCHIVES.get(environment);
        Path archive = root.resolve(environment + ".dump");
        privateFile(archive);
        int matches = 0;
        for (var resource : resources) {
            if (!environment.equals(resource.path("environment").asText())) continue;
            matches++;
            assertEquals(reviewed.databaseUuid(), resource.path("databaseUuid").asText());
            assertEquals(archive.toString(), resource.path("path").asText());
            assertEquals(reviewed.sha256(), resource.path("sha256").asText());
            assertEquals(reviewed.bytes(), resource.path("bytes").asLong());
        }
        assertEquals(1, matches);
        assertEquals(reviewed.bytes(), Files.size(archive));
        try (var input = Files.newInputStream(archive)) {
            assertEquals("PGDMP", new String(input.readNBytes(5), java.nio.charset.StandardCharsets.US_ASCII));
        }
        var digest = MessageDigest.getInstance("SHA-256");
        try (var input = new DigestInputStream(Files.newInputStream(archive), digest)) {
            input.transferTo(java.io.OutputStream.nullOutputStream());
        }
        assertEquals(reviewed.sha256(), HexFormat.of().formatHex(digest.digest()));
        return archive;
    }

    private void privateFile(Path path) throws java.io.IOException {
        assertTrue(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS));
        assertEquals(path, path.toRealPath());
        assertEquals(PRIVATE_FILE, Files.getPosixFilePermissions(path));
    }

    private Set<String> tableNames(Connection connection) throws java.sql.SQLException {
        Set<String> result = new LinkedHashSet<>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(
            "SELECT tablename FROM pg_catalog.pg_tables WHERE schemaname='public' "
                + "AND tablename<>'flyway_schema_history' ORDER BY tablename")) {
            while (rows.next()) result.add(rows.getString(1));
        }
        return result;
    }

    private Map<String, TableSnapshot> snapshot(Connection connection, Set<String> tables) throws java.sql.SQLException {
        Map<String, TableSnapshot> result = new LinkedHashMap<>();
        for (String table : tables) {
            String quoted = '"' + table.replace("\"", "\"\"") + '"';
            Fingerprint data = fingerprint(connection, "SELECT to_jsonb(t) AS entry FROM public." + quoted + " t");
            Fingerprint columns = fingerprint(connection, "SELECT to_jsonb(c) AS entry FROM information_schema.columns c "
                + "WHERE table_schema='public' AND table_name=?", table);
            result.put(table, new TableSnapshot(data, columns));
        }
        return result;
    }

    private void assertPreserved(Connection connection, Set<String> originalTables, Map<String, TableSnapshot> before,
                                 Fingerprint history) throws java.sql.SQLException {
        Set<String> expected = new LinkedHashSet<>(originalTables);
        expected.add(ROUTE_TABLE);
        assertEquals(expected, tableNames(connection), "Only the routing-state table may be added");
        assertEquals(before, snapshot(connection, originalTables), "All original rows and columns must remain unchanged");
        assertEquals(history, fingerprint(connection,
            "SELECT to_jsonb(h) AS entry FROM flyway_schema_history h WHERE version IS DISTINCT FROM '206'"));
        assertEquals("206", scalar(connection, "SELECT max(version::int) FROM flyway_schema_history WHERE success"));
        assertEquals("0", scalar(connection, "SELECT count(*) FROM flyway_schema_history WHERE NOT success"));
        assertEquals("0", scalar(connection, "SELECT count(*) FROM " + ROUTE_TABLE), "Migration must not claim routing work or success");
    }

    private Fingerprint fingerprint(Connection connection, String entries, String... parameters) throws java.sql.SQLException {
        String query = "SELECT count(*) AS row_count, encode(sha256(convert_to(coalesce("
            + "string_agg(entry::text,E'\\n' ORDER BY entry::text COLLATE \"C\"),''),'UTF8')),'hex') AS sha256 "
            + "FROM (" + entries + ") entries";
        try (var statement = connection.prepareStatement(query)) {
            statement.setQueryTimeout(30);
            for (int index = 0; index < parameters.length; index++) statement.setString(index + 1, parameters[index]);
            try (var rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return new Fingerprint(rows.getLong("row_count"), rows.getString("sha256"));
            }
        }
    }

    private String scalar(Connection connection, String query) throws java.sql.SQLException {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(query)) {
            assertTrue(rows.next());
            return rows.getString(1);
        }
    }

    private record ReviewedArchive(String databaseUuid, long bytes, String sha256) { }
    private record Fingerprint(long rows, String sha256) { }
    private record TableSnapshot(Fingerprint data, Fingerprint columns) { }
}
