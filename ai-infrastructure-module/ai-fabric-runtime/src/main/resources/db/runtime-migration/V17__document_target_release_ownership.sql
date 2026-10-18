-- Legacy rows remain unowned. A runtime must register and index its own generation.
ALTER TABLE loomai_document_source ADD COLUMN target_profile_id VARCHAR(128);
ALTER TABLE loomai_document_source ADD COLUMN release_id VARCHAR(128);
ALTER TABLE loomai_document_manifest ADD COLUMN target_profile_id VARCHAR(128);
ALTER TABLE loomai_document_manifest ADD COLUMN release_id VARCHAR(128);
ALTER TABLE loomai_document_command ADD COLUMN customer_id VARCHAR(128);
ALTER TABLE loomai_document_command ADD COLUMN target_profile_id VARCHAR(128);
ALTER TABLE loomai_document_command ADD COLUMN release_id VARCHAR(128);

ALTER TABLE loomai_document_source ADD CONSTRAINT ck_document_source_generation CHECK (
    (target_profile_id IS NULL AND release_id IS NULL)
    OR (target_profile_id IS NOT NULL AND LENGTH(TRIM(target_profile_id)) > 0
        AND release_id IS NOT NULL AND LENGTH(TRIM(release_id)) > 0)
);
ALTER TABLE loomai_document_manifest ADD CONSTRAINT ck_document_manifest_generation CHECK (
    (target_profile_id IS NULL AND release_id IS NULL)
    OR (target_profile_id IS NOT NULL AND LENGTH(TRIM(target_profile_id)) > 0
        AND release_id IS NOT NULL AND LENGTH(TRIM(release_id)) > 0)
);
ALTER TABLE loomai_document_command ADD CONSTRAINT ck_document_command_generation CHECK (
    (target_profile_id IS NULL AND release_id IS NULL AND customer_id IS NULL)
    OR (target_profile_id IS NOT NULL AND LENGTH(TRIM(target_profile_id)) > 0
        AND release_id IS NOT NULL AND LENGTH(TRIM(release_id)) > 0
        AND customer_id IS NOT NULL AND LENGTH(TRIM(customer_id)) > 0)
);

ALTER TABLE loomai_document_source ADD CONSTRAINT uk_document_source_owner
    UNIQUE (id, tenant_id, customer_id, deployment_id, target_profile_id, release_id);
ALTER TABLE loomai_document_manifest ADD CONSTRAINT fk_document_manifest_owner
    FOREIGN KEY (source_id, tenant_id, customer_id, deployment_id, target_profile_id, release_id)
    REFERENCES loomai_document_source (id, tenant_id, customer_id, deployment_id, target_profile_id, release_id);

ALTER TABLE loomai_document_command DROP CONSTRAINT uk_loomai_document_command_idempotency;
ALTER TABLE loomai_document_command ADD CONSTRAINT uk_loomai_document_command_idempotency
    UNIQUE (tenant_id, customer_id, deployment_id, target_profile_id, release_id, operation, idempotency_key);

DROP INDEX IF EXISTS idx_loomai_document_source_boundary;
CREATE INDEX idx_loomai_document_source_boundary
    ON loomai_document_source (tenant_id, customer_id, deployment_id, target_profile_id, release_id, lifecycle_status);
DROP INDEX IF EXISTS idx_loomai_document_manifest_boundary;
CREATE INDEX idx_loomai_document_manifest_boundary
    ON loomai_document_manifest (tenant_id, customer_id, deployment_id, target_profile_id, release_id, lifecycle_state);
