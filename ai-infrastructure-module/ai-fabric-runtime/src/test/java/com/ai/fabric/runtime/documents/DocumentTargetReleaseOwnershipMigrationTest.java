package com.ai.fabric.runtime.documents;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentTargetReleaseOwnershipMigrationTest {

    @Test
    void legacyRowsRemainUnownedAndCannotClaimTheNewRuntimeGeneration() {
        var source = database();
        apply(source, "V9__loomai_document_ingestion.sql");
        var jdbc = new JdbcTemplate(source);
        insertLegacySource(jdbc, "legacy");
        jdbc.update("""
            INSERT INTO loomai_document_command
                (id, tenant_id, deployment_id, operation, resource_key, idempotency_key, request_hash, created_at)
            VALUES ('legacy-command', 'tenant', 'deployment', 'REGISTER', 'legacy', 'same-key', 'hash', CURRENT_TIMESTAMP)
            """);

        apply(source, "V17__document_target_release_ownership.sql");

        assertThat(jdbc.queryForObject("SELECT target_profile_id FROM loomai_document_source WHERE id = 'legacy'", String.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT release_id FROM loomai_document_source WHERE id = 'legacy'", String.class)).isNull();
        assertThat(jdbc.queryForObject("""
            SELECT COUNT(*) FROM loomai_document_source WHERE tenant_id = 'tenant' AND customer_id = 'customer'
                AND deployment_id = 'deployment' AND target_profile_id = 'staging' AND release_id = 'release-a'
            """, Long.class)).isZero();
        insertCommand(jdbc, "new-command", "staging", "release-a");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM loomai_document_command", Long.class)).isEqualTo(2);
    }

    @Test
    void commandUniquenessAndManifestForeignKeysIncludeBothTargetAndRelease() {
        var source = database();
        apply(source, "V9__loomai_document_ingestion.sql");
        apply(source, "V17__document_target_release_ownership.sql");
        var jdbc = new JdbcTemplate(source);
        insertOwnedSource(jdbc, "source-a", "staging", "release-a");
        insertOwnedSource(jdbc, "source-b", "staging", "release-b");
        insertOwnedSource(jdbc, "source-production", "production", "release-a");

        insertCommand(jdbc, "command-a", "staging", "release-a");
        insertCommand(jdbc, "command-b", "staging", "release-b");
        insertCommand(jdbc, "command-production", "production", "release-a");
        assertThatThrownBy(() -> insertCommand(jdbc, "duplicate-a", "staging", "release-a"))
            .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> insertManifest(jdbc, "foreign-release", "source-a", "staging", "release-b"))
            .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("FK_DOCUMENT_MANIFEST_OWNER");
        assertThatThrownBy(() -> insertManifest(jdbc, "foreign-target", "source-b", "production", "release-b"))
            .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("FK_DOCUMENT_MANIFEST_OWNER");
        insertManifest(jdbc, "manifest-a", "source-a", "staging", "release-a");
        insertManifest(jdbc, "manifest-b", "source-b", "staging", "release-b");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM loomai_document_manifest", Long.class)).isEqualTo(2);
    }

    @Test
    void partiallySpecifiedGenerationOwnershipIsRejected() {
        var source = database();
        apply(source, "V9__loomai_document_ingestion.sql");
        apply(source, "V17__document_target_release_ownership.sql");
        var jdbc = new JdbcTemplate(source);

        assertThatThrownBy(() -> insertOwnedSource(jdbc, "missing-release", "staging", null))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertCommand(jdbc, "missing-target", null, "release-a"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    private DriverManagerDataSource database() {
        return new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
    }

    private void apply(DriverManagerDataSource source, String migration) {
        new ResourceDatabasePopulator(new ClassPathResource("db/runtime-migration/" + migration)).execute(source);
    }

    private void insertLegacySource(JdbcTemplate jdbc, String id) {
        jdbc.update("""
            INSERT INTO loomai_document_source
                (id, logical_source_key, dataset_id, tenant_id, customer_id, deployment_id, connector_type,
                 object_locator, object_locator_digest, display_name, provider_revision_fingerprint, content_length,
                 source_version, visibility, metadata_json, lifecycle_status, created_at, updated_at)
            VALUES (?, ?, 'documents', 'tenant', 'customer', 'deployment', 'MOUNTED_FOLDER', 'policy.txt', 'digest',
                'Policy', 'revision', 100, 1, 'tenant', '{}', 'REGISTERED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """, id, id);
    }

    private void insertOwnedSource(JdbcTemplate jdbc, String id, String target, String release) {
        // Supply ownership atomically, as the runtime registration path does.
        jdbc.update("""
            INSERT INTO loomai_document_source
                (id, logical_source_key, dataset_id, tenant_id, customer_id, deployment_id, target_profile_id, release_id,
                 connector_type, object_locator, object_locator_digest, display_name, provider_revision_fingerprint,
                 content_length, source_version, visibility, metadata_json, lifecycle_status, created_at, updated_at)
            VALUES (?, ?, 'documents', 'tenant', 'customer', 'deployment', ?, ?, 'MOUNTED_FOLDER', 'policy.txt', 'digest',
                'Policy', 'revision', 100, 1, 'tenant', '{}', 'REGISTERED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """, id, id, target, release);
    }

    private void insertCommand(JdbcTemplate jdbc, String id, String target, String release) {
        jdbc.update("""
            INSERT INTO loomai_document_command
                (id, tenant_id, customer_id, deployment_id, target_profile_id, release_id, operation, resource_key,
                 idempotency_key, request_hash, created_at)
            VALUES (?, 'tenant', 'customer', 'deployment', ?, ?, 'REGISTER', 'policy.txt', 'same-key', 'hash', CURRENT_TIMESTAMP)
            """, id, target, release);
    }

    private void insertManifest(JdbcTemplate jdbc, String id, String sourceId, String target, String release) {
        jdbc.update("""
            INSERT INTO loomai_document_manifest
                (manifest_id, manifest_schema_version, plan_id, source_id, source_version, provider_revision_fingerprint,
                 source_name, entity_type, tenant_id, customer_id, deployment_id, target_profile_id, release_id,
                 visibility, lifecycle_state, chunk_count, warnings_json, created_at, updated_at)
            VALUES (?, 1, ?, ?, 1, 'revision', 'Policy', 'document', 'tenant', 'customer', 'deployment', ?, ?,
                'tenant', 'DRAFT', 1, '[]', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """, id, id, sourceId, target, release);
    }
}
