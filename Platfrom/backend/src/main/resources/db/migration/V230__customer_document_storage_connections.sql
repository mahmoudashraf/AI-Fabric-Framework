CREATE TABLE platform_document_storage_connections (
    id varchar(64) PRIMARY KEY,
    customer_id varchar(64) NOT NULL,
    tenant_id varchar(128) NOT NULL,
    name varchar(160) NOT NULL,
    mode varchar(48) NOT NULL,
    bucket varchar(255) NOT NULL,
    region varchar(64) NOT NULL,
    root_prefix varchar(512) NOT NULL,
    status varchar(32) NOT NULL,
    operation_key varchar(160) NOT NULL,
    last_error varchar(512),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    revision bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_document_storage_create UNIQUE(customer_id, tenant_id, operation_key),
    CONSTRAINT uq_document_storage_bucket UNIQUE(bucket)
);
CREATE INDEX idx_document_storage_owner ON platform_document_storage_connections(customer_id, tenant_id);

CREATE TABLE platform_document_storage_uploads (
    id varchar(64) PRIMARY KEY,
    connection_id varchar(64) NOT NULL REFERENCES platform_document_storage_connections(id),
    filename varchar(180) NOT NULL,
    object_key varchar(1024) NOT NULL,
    prefix varchar(512) NOT NULL,
    size_bytes bigint NOT NULL,
    content_type varchar(128) NOT NULL,
    sha256 varchar(64) NOT NULL,
    etag varchar(255),
    provider_version_id varchar(1024),
    status varchar(32) NOT NULL,
    operation_key varchar(160) NOT NULL,
    last_error varchar(512),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    revision bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_document_upload_operation UNIQUE(connection_id, operation_key),
    CONSTRAINT uq_document_upload_key UNIQUE(connection_id, object_key)
);

CREATE TABLE platform_document_storage_selections (
    id varchar(64) PRIMARY KEY,
    upload_id varchar(64) NOT NULL REFERENCES platform_document_storage_uploads(id),
    deployment_id varchar(64) NOT NULL REFERENCES platform_deployments(id) ON DELETE CASCADE,
    target_profile_id varchar(64) NOT NULL,
    release_id varchar(64) NOT NULL,
    dataset_id varchar(160) NOT NULL,
    public_approved boolean NOT NULL,
    source_id varchar(128),
    status varchar(48) NOT NULL,
    last_error varchar(512),
    operation_key varchar(160) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    revision bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_document_storage_selection UNIQUE(upload_id, deployment_id, target_profile_id, release_id)
);
