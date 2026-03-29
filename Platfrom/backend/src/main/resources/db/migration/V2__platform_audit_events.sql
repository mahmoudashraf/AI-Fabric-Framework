CREATE TABLE platform_audit_events (
    id VARCHAR(64) PRIMARY KEY,
    actor_id VARCHAR(128) NOT NULL,
    actor_role VARCHAR(64) NOT NULL,
    action VARCHAR(128) NOT NULL,
    target_type VARCHAR(128) NOT NULL,
    target_id VARCHAR(128) NOT NULL,
    details_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_platform_audit_events_created
    ON platform_audit_events (created_at);
