CREATE TABLE platform_deployment_promotion_commands (
    id VARCHAR(64) PRIMARY KEY,
    customer_id VARCHAR(64) NOT NULL,
    consumer_entity_id VARCHAR(64) NOT NULL REFERENCES platform_consumers(id),
    operation_key VARCHAR(128) NOT NULL,
    command_type VARCHAR(32) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    result_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (consumer_entity_id, operation_key)
);
ALTER TABLE platform_deployment_releases ADD COLUMN promotion_candidate_json TEXT;
ALTER TABLE platform_deployment_releases ADD COLUMN promotion_candidate_fingerprint VARCHAR(64);
