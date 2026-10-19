package com.ai.fabric.platform.backend.deployment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
    private static final Map<String, ReviewedArchive> REVIEWED_ARCHIVES = Map.of(
        "staging", new ReviewedArchive("m58iwvqdkfie8tykohmhyj7t", 8752658,
            "c0b8b78b8b23e24c22faf9c06d3c073bdde585bc0347fe17bf95e89f1b937e35"),
        "production", new ReviewedArchive("nkti6x5r7ovw1xx8q0ykhweq", 7683556,
            "cdbd6da49b136740b0ff42f73c4a0566813f9b0841e9e00bb79a150eda33ccb8"));
    private static final Set<String> NEW_TABLES = Set.of(
        "deployment_external_index_retention_receipts", "provider_usage_grant_void_receipts");
    private static final Set<String> REQUIRED_ORIGINAL_TABLES = Set.of(
        "deployment_unused_vector_handoffs", "deployment_admission_unused_cancellations",
        "deployment_source_prerequisite_evidence", "deployment_provider_resource_handles",
        "platform_deployment_versions", "platform_deployment_releases", "deployment_capacity_reservations",
        "platform_audit_events", "provider_usage_grants", "provider_usage_allocations", "provider_usage_allowance_events");

    @ParameterizedTest
    @ValueSource(strings = {"staging", "production"})
    void migratesActualPrivateArchiveWithoutChangingExistingCustomerState(String environment) throws Exception {
        assertEquals("true", System.getenv("RUN_01044_ARCHIVE_MIGRATION"), "Explicit local rehearsal opt-in required");
        String directory = System.getenv("CONTROL_PLANE_ARCHIVE_DIRECTORY");
        assertTrue(directory != null && !directory.isBlank(), "Explicit private archive directory required");
        Path root = Path.of(directory);
        assertTrue(root.isAbsolute() && root.normalize().equals(root) && root.toRealPath().equals(root));
        assertEquals(PRIVATE_DIRECTORY, Files.getPosixFilePermissions(root));
        Path archive = reviewedArchive(root, environment);
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
                assertEquals("202", scalar(connection, "SELECT max(version::int) FROM flyway_schema_history WHERE success"));
                assertEquals("0", scalar(connection, "SELECT count(*) FROM flyway_schema_history WHERE NOT success"));
                Set<String> originalTables = tableNames(connection);
                assertTrue(originalTables.containsAll(REQUIRED_ORIGINAL_TABLES), "Required original ledgers must exist");
                assertTrue(originalTables.stream().noneMatch(NEW_TABLES::contains), "Only a CP202 archive is accepted");
                assertEquals("0", scalar(connection, """
                    SELECT count(*) FROM information_schema.columns WHERE table_schema='public'
                    AND ((table_name='provider_usage_grants' AND column_name='lifecycle_status')
                      OR (table_name='provider_usage_allowance_events' AND column_name='grant_id'))
                    """));
                Map<String, TableSnapshot> before = preserved(connection, originalTables);
                Fingerprint originalHistory = history(connection);
                Fingerprint expectedAttribution = expectedAttribution(connection);
                Flyway flyway = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration").target("204").load();
                var validation = flyway.validateWithResult();
                if (!validation.validationSuccessful) {
                    Set<String> codes = validation.invalidMigrations.stream().map(value ->
                        (value.version != null && value.version.matches("[0-9.]{1,20}") ? value.version : "unversioned")
                            + ":" + value.errorDetails.errorCode).collect(java.util.stream.Collectors.toSet());
                    assertEquals(Set.of("203:RESOLVED_VERSIONED_MIGRATION_NOT_APPLIED",
                        "204:RESOLVED_VERSIONED_MIGRATION_NOT_APPLIED"), codes,
                        "Only V203 and V204 may be pending before normal migration validation");
                }
                try {
                    assertEquals(2, flyway.migrate().migrationsExecuted);
                    flyway.validate();
                    assertPreserved(connection, originalTables, before, originalHistory, expectedAttribution);
                    assertEquals(0, flyway.migrate().migrationsExecuted, "Exact migration replay must be a no-op");
                    flyway.validate();
                    assertPreserved(connection, originalTables, before, originalHistory, expectedAttribution);
                } catch (RuntimeException failure) {
                    throw new AssertionError("Archive migration/validation failed; private diagnostics withheld");
                }
                System.out.println("ARCHIVE_202_TO_204_PASS " + new ObjectMapper().writeValueAsString(Map.of(
                    "environment", environment, "archiveSha256", REVIEWED_ARCHIVES.get(environment).sha256(),
                    "from", "202", "to", "204", "migrationsExecuted", 2, "replayMigrationsExecuted", 0,
                    "preservedTableCount", before.size(), "originalTables", before,
                    "originalHistory", originalHistory, "verifiedEventAttribution", expectedAttribution)));
            } catch (java.sql.SQLException failure) {
                throw new AssertionError("Archive integrity query failed; private diagnostics withheld");
            }
        }
    }

    private Path reviewedArchive(Path root, String environment) throws Exception {
        Path manifest = root.resolve("manifest.json");
        privateFile(manifest);
        var resources = new ObjectMapper().readTree(manifest.toFile()).path("resources");
        assertTrue(resources.isArray() && resources.size() == REVIEWED_ARCHIVES.size(), "Exact archive manifest required");
        var reviewed = REVIEWED_ARCHIVES.get(environment);
        Path archive = root.resolve(environment + ".dump");
        privateFile(archive);
        int matches = 0;
        for (var resource : resources) {
            if (!environment.equals(resource.path("environment").asText())) continue;
            matches++;
            assertTrue(reviewed.databaseUuid().equals(resource.path("databaseUuid").asText()), "Reviewed CP database required");
            assertTrue(archive.toString().equals(resource.path("path").asText()), "Archive path must match its manifest");
            assertTrue(reviewed.sha256().equals(resource.path("sha256").asText()), "Reviewed archive SHA256 required");
            assertEquals(reviewed.bytes(), resource.path("bytes").asLong());
        }
        assertEquals(1, matches, "Exactly one environment receipt required");
        assertEquals(reviewed.bytes(), Files.size(archive));
        try (var input = Files.newInputStream(archive)) {
            assertEquals("PGDMP", new String(input.readNBytes(5), java.nio.charset.StandardCharsets.US_ASCII));
        }
        var digest = MessageDigest.getInstance("SHA-256");
        try (var input = new DigestInputStream(Files.newInputStream(archive), digest)) {
            input.transferTo(java.io.OutputStream.nullOutputStream());
        }
        assertEquals(reviewed.sha256(), HexFormat.of().formatHex(digest.digest()), "Archive bytes must match reviewed backup");
        return archive;
    }

    private void privateFile(Path file) throws java.io.IOException {
        assertTrue(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS));
        assertEquals(file, file.toRealPath());
        assertEquals(PRIVATE_FILE, Files.getPosixFilePermissions(file));
    }

    private Set<String> tableNames(Connection connection) throws java.sql.SQLException {
        Set<String> result = new LinkedHashSet<>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(
            "SELECT tablename FROM pg_catalog.pg_tables WHERE schemaname='public' AND tablename<>'flyway_schema_history' ORDER BY tablename")) {
            while (rows.next()) result.add(rows.getString(1));
        }
        return result;
    }

    private Map<String, TableSnapshot> preserved(Connection connection, Set<String> tables) throws java.sql.SQLException {
        Map<String, TableSnapshot> result = new LinkedHashMap<>();
        for (String table : tables) {
            // Only V204's two new financial columns are excluded. Every preexisting
            // field, including AI-state snapshots, is hashed exactly as restored.
            String row = switch (table) {
                case "provider_usage_grants" -> "to_jsonb(t)-'lifecycle_status'";
                case "provider_usage_allowance_events" -> "to_jsonb(t)-'grant_id'";
                default -> "to_jsonb(t)";
            };
            var data = fingerprint(connection, "SELECT " + row + " AS entry FROM public." + quoted(table) + " t");
            var columns = fingerprint(connection, """
                SELECT to_jsonb(c) AS entry FROM information_schema.columns c
                WHERE table_schema='public' AND table_name=?
                AND NOT ((table_name='provider_usage_grants' AND column_name='lifecycle_status')
                  OR (table_name='provider_usage_allowance_events' AND column_name='grant_id'))
                """, table);
            result.put(table, new TableSnapshot(data, columns));
        }
        return result;
    }

    private Fingerprint history(Connection connection) throws java.sql.SQLException {
        return fingerprint(connection, "SELECT to_jsonb(h) AS entry FROM public.flyway_schema_history h "
            + "WHERE version IS NULL OR version NOT IN ('203','204')");
    }

    private Fingerprint expectedAttribution(Connection connection) throws java.sql.SQLException {
        // V204 may attribute only known grant approvals and these exact allocation
        // events. Unresolved or other historical events must remain unattributed.
        return fingerprint(connection, """
            SELECT jsonb_build_object('id',e.id,'grant_id',CASE
              WHEN e.event_type='GRANT_APPROVED' THEN g.id
              WHEN e.event_type IN ('ALLOWANCE_ALLOCATED','ALLOWANCE_INITIALIZED','ALLOWANCE_ACTIVE') THEN a.grant_id
              ELSE NULL END) AS entry
            FROM public.provider_usage_allowance_events e
            LEFT JOIN public.provider_usage_grants g ON g.id=e.subject_id
            LEFT JOIN public.provider_usage_allocations a ON a.approval_id=e.subject_id
            """);
    }

    private void assertPreserved(Connection connection, Set<String> originalTables, Map<String, TableSnapshot> before,
                                 Fingerprint originalHistory, Fingerprint expectedAttribution) throws java.sql.SQLException {
        Set<String> expectedTables = new LinkedHashSet<>(originalTables);
        expectedTables.addAll(NEW_TABLES);
        assertEquals(expectedTables, tableNames(connection), "Only the two approved receipt tables may be added");
        assertEquals(before, preserved(connection, originalTables), "All original table counts, columns and canonical hashes must be unchanged");
        assertEquals(originalHistory, history(connection), "Original Flyway history must be unchanged");
        assertEquals("204", scalar(connection, "SELECT max(version::int) FROM flyway_schema_history WHERE success"));
        assertEquals("0", scalar(connection, "SELECT count(*) FROM flyway_schema_history WHERE NOT success"));
        assertEquals("203,204", scalar(connection, "SELECT string_agg(version,',' ORDER BY installed_rank) "
            + "FROM flyway_schema_history WHERE version IN ('203','204') AND success"));
        for (String table : NEW_TABLES) assertEquals("0", scalar(connection, "SELECT count(*) FROM public." + quoted(table)),
            "Migration must not seed retention or grant-void receipts");
        assertEquals("0", scalar(connection, "SELECT count(*) FROM provider_usage_grants WHERE lifecycle_status IS DISTINCT FROM 'ACTIVE'"));
        assertEquals(expectedAttribution, fingerprint(connection,
            "SELECT jsonb_build_object('id',id,'grant_id',grant_id) AS entry FROM public.provider_usage_allowance_events"),
            "Only the documented financial history attribution is permitted");
    }

    private Fingerprint fingerprint(Connection connection, String entries, String... parameters) throws java.sql.SQLException {
        String sql = "SELECT count(*) AS row_count,encode(sha256(convert_to("
            + "coalesce(string_agg(entry::text,E'\\n' ORDER BY entry::text),''),'UTF8')),'hex') AS sha256 FROM (" + entries + ") entries";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(30);
            for (int index = 0; index < parameters.length; index++) statement.setString(index + 1, parameters[index]);
            try (var rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return new Fingerprint(rows.getLong("row_count"), rows.getString("sha256"));
            }
        }
    }

    private String quoted(String identifier) {
        return '"' + identifier.replace("\"", "\"\"") + '"';
    }

    private String scalar(Connection connection, String sql) throws java.sql.SQLException {
        try (var statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            assertTrue(rows.next());
            return rows.getString(1);
        }
    }

    private record ReviewedArchive(String databaseUuid, long bytes, String sha256) { }
    private record Fingerprint(long rows, String sha256) { }
    private record TableSnapshot(Fingerprint data, Fingerprint columns) { }
}
