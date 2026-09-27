CREATE TABLE IF NOT EXISTS integration_sync_state (
    source_id VARCHAR(160) PRIMARY KEY,
    status VARCHAR(40) NOT NULL,
    cursor_value VARCHAR(1000),
    source_version VARCHAR(160),
    provider_correlation_header VARCHAR(160),
    provider_correlation_value VARCHAR(256),
    source_count INTEGER NOT NULL DEFAULT 0,
    normalized_count INTEGER NOT NULL DEFAULT 0,
    indexed_count INTEGER NOT NULL DEFAULT 0,
    deleted_count INTEGER NOT NULL DEFAULT 0,
    accepted_work_count INTEGER NOT NULL DEFAULT 0,
    completed_work_count INTEGER NOT NULL DEFAULT 0,
    failed_work_count INTEGER NOT NULL DEFAULT 0,
    last_started_at TIMESTAMP WITH TIME ZONE,
    last_success_at TIMESTAMP WITH TIME ZONE,
    last_error_at TIMESTAMP WITH TIME ZONE,
    error_class VARCHAR(80),
    error_message VARCHAR(1000),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS integration_source_record (
    source_id VARCHAR(160) NOT NULL,
    record_id VARCHAR(500) NOT NULL,
    fingerprint VARCHAR(128) NOT NULL,
    last_seen_run VARCHAR(80) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (source_id, record_id)
);

CREATE INDEX IF NOT EXISTS idx_integration_source_record_active
    ON integration_source_record (source_id, active);

CREATE TABLE IF NOT EXISTS integration_index_work (
    work_id VARCHAR(160) PRIMARY KEY,
    source_id VARCHAR(160) NOT NULL,
    record_id VARCHAR(500) NOT NULL,
    operation VARCHAR(20) NOT NULL,
    status VARCHAR(40) NOT NULL,
    error_code VARCHAR(120),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_integration_index_work_source
    ON integration_index_work (source_id, created_at);

CREATE TABLE IF NOT EXISTS integration_webhook_event (
    source_id VARCHAR(160) NOT NULL,
    event_id VARCHAR(240) NOT NULL,
    event_type VARCHAR(160) NOT NULL,
    resource_fingerprint VARCHAR(128),
    payload_sha256 VARCHAR(128) NOT NULL,
    status VARCHAR(40) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    duplicate_count INTEGER NOT NULL DEFAULT 0,
    replay_count INTEGER NOT NULL DEFAULT 0,
    error_class VARCHAR(80),
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (source_id, event_id)
);

CREATE INDEX IF NOT EXISTS idx_integration_webhook_event_recent
    ON integration_webhook_event (source_id, received_at);

CREATE TABLE IF NOT EXISTS integration_webhook_rejection (
    source_id VARCHAR(160) NOT NULL,
    error_class VARCHAR(80) NOT NULL,
    rejection_count BIGINT NOT NULL DEFAULT 0,
    last_rejected_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (source_id, error_class)
);
