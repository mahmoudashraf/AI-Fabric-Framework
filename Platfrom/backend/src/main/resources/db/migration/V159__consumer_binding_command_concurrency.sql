ALTER TABLE platform_consumers ADD COLUMN binding_revision BIGINT NOT NULL DEFAULT 0;
ALTER TABLE platform_consumers ADD COLUMN row_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE platform_consumer_binding_history ADD COLUMN from_release_id VARCHAR(64);
ALTER TABLE platform_consumer_binding_history ADD COLUMN to_release_id VARCHAR(64);
ALTER TABLE platform_consumer_binding_history ADD COLUMN from_target_profile_id VARCHAR(64);
ALTER TABLE platform_consumer_binding_history ADD COLUMN to_target_profile_id VARCHAR(64);
ALTER TABLE platform_consumer_binding_history ADD COLUMN from_binding_revision BIGINT;
ALTER TABLE platform_consumer_binding_history ADD COLUMN to_binding_revision BIGINT;
ALTER TABLE platform_consumer_binding_history ADD COLUMN operation_key VARCHAR(128);
ALTER TABLE platform_consumer_binding_history ADD COLUMN request_fingerprint VARCHAR(64);
ALTER TABLE platform_consumer_binding_history ADD COLUMN result_json TEXT;
CREATE UNIQUE INDEX ux_consumer_binding_history_operation
    ON platform_consumer_binding_history (consumer_entity_id, operation_key);
