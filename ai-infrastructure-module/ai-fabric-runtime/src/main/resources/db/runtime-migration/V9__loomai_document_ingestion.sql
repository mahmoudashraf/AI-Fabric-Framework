CREATE TABLE IF NOT EXISTS loomai_document_source (
    id VARCHAR(64) PRIMARY KEY,
    logical_source_key VARCHAR(64) NOT NULL,
    dataset_id VARCHAR(128) NOT NULL,
    tenant_id VARCHAR(128) NOT NULL,
    customer_id VARCHAR(128) NOT NULL,
    deployment_id VARCHAR(128) NOT NULL,
    connector_type VARCHAR(64) NOT NULL,
    connector_binding_ref VARCHAR(128),
    object_locator VARCHAR(1024) NOT NULL,
    object_locator_digest VARCHAR(64) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    media_type VARCHAR(128),
    provider_version_id VARCHAR(512),
    provider_etag VARCHAR(256),
    provider_revision_fingerprint VARCHAR(64) NOT NULL,
    content_fingerprint VARCHAR(64),
    content_length BIGINT NOT NULL,
    provider_last_modified TIMESTAMP WITH TIME ZONE,
    source_version BIGINT NOT NULL,
    active_source_version BIGINT,
    visibility VARCHAR(64) NOT NULL,
    metadata_json TEXT NOT NULL,
    lifecycle_status VARCHAR(32) NOT NULL,
    last_failure_code VARCHAR(128),
    last_failure_message VARCHAR(512),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    deleted_at TIMESTAMP WITH TIME ZONE,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_loomai_document_source_logical_key UNIQUE (logical_source_key)
);

CREATE INDEX IF NOT EXISTS idx_loomai_document_source_boundary
    ON loomai_document_source (tenant_id, deployment_id, lifecycle_status);

CREATE TABLE IF NOT EXISTS loomai_document_manifest (
    manifest_id VARCHAR(64) PRIMARY KEY,
    manifest_schema_version INTEGER NOT NULL,
    plan_id VARCHAR(64) NOT NULL,
    source_id VARCHAR(64) NOT NULL,
    source_version BIGINT NOT NULL,
    provider_revision_fingerprint VARCHAR(64) NOT NULL,
    source_name VARCHAR(512) NOT NULL,
    entity_type VARCHAR(128) NOT NULL,
    tenant_id VARCHAR(128) NOT NULL,
    customer_id VARCHAR(128) NOT NULL,
    deployment_id VARCHAR(128) NOT NULL,
    visibility VARCHAR(64) NOT NULL,
    lifecycle_state VARCHAR(32) NOT NULL,
    chunk_count INTEGER NOT NULL,
    accepted_index_work_count INTEGER NOT NULL DEFAULT 0,
    accepted_delete_work_count INTEGER NOT NULL DEFAULT 0,
    index_submission_attempt INTEGER NOT NULL DEFAULT 0,
    delete_submission_attempt INTEGER NOT NULL DEFAULT 0,
    delete_purpose VARCHAR(32),
    warnings_json TEXT NOT NULL,
    failure_code VARCHAR(128),
    failure_message VARCHAR(512),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    activated_at TIMESTAMP WITH TIME ZONE,
    superseded_at TIMESTAMP WITH TIME ZONE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_loomai_document_manifest_source_version UNIQUE (source_id, source_version),
    CONSTRAINT fk_loomai_document_manifest_source FOREIGN KEY (source_id)
        REFERENCES loomai_document_source (id)
);

CREATE INDEX IF NOT EXISTS idx_loomai_document_manifest_source_state
    ON loomai_document_manifest (source_id, lifecycle_state);
CREATE INDEX IF NOT EXISTS idx_loomai_document_manifest_boundary
    ON loomai_document_manifest (tenant_id, deployment_id, lifecycle_state);

CREATE TABLE IF NOT EXISTS loomai_document_manifest_chunk (
    id VARCHAR(64) PRIMARY KEY,
    manifest_id VARCHAR(64) NOT NULL,
    source_document_id VARCHAR(256) NOT NULL,
    chunk_id VARCHAR(64) NOT NULL,
    chunk_index INTEGER NOT NULL,
    entity_id VARCHAR(512) NOT NULL,
    content_fingerprint VARCHAR(64) NOT NULL,
    CONSTRAINT uk_loomai_document_manifest_chunk_entity UNIQUE (manifest_id, entity_id),
    CONSTRAINT fk_loomai_document_chunk_manifest FOREIGN KEY (manifest_id)
        REFERENCES loomai_document_manifest (manifest_id)
);

CREATE INDEX IF NOT EXISTS idx_loomai_document_chunk_manifest
    ON loomai_document_manifest_chunk (manifest_id, chunk_index);

CREATE TABLE IF NOT EXISTS loomai_document_work (
    id VARCHAR(64) PRIMARY KEY,
    manifest_id VARCHAR(64) NOT NULL,
    operation VARCHAR(16) NOT NULL,
    framework_work_id VARCHAR(64) NOT NULL,
    submission_attempt INTEGER NOT NULL DEFAULT 0,
    last_work_state VARCHAR(32) NOT NULL,
    terminal BOOLEAN NOT NULL DEFAULT FALSE,
    successful BOOLEAN NOT NULL DEFAULT FALSE,
    failure_code VARCHAR(128),
    failure_message VARCHAR(512),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_observed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_loomai_document_work_framework UNIQUE (manifest_id, operation, framework_work_id),
    CONSTRAINT fk_loomai_document_work_manifest FOREIGN KEY (manifest_id)
        REFERENCES loomai_document_manifest (manifest_id)
);

CREATE INDEX IF NOT EXISTS idx_loomai_document_work_manifest
    ON loomai_document_work (manifest_id, operation);

CREATE TABLE IF NOT EXISTS loomai_document_command (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
    deployment_id VARCHAR(128) NOT NULL,
    operation VARCHAR(64) NOT NULL,
    resource_key VARCHAR(256) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_loomai_document_command_idempotency
        UNIQUE (tenant_id, deployment_id, operation, idempotency_key)
);
