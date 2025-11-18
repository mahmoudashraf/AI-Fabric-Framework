ALTER TABLE behavior_events
    ADD COLUMN IF NOT EXISTS schema_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS signal_key VARCHAR(128),
    ADD COLUMN IF NOT EXISTS version VARCHAR(16) DEFAULT '1.0';

CREATE INDEX IF NOT EXISTS idx_behavior_schema_time
    ON behavior_events (schema_id, timestamp DESC);

CREATE UNIQUE INDEX IF NOT EXISTS idx_behavior_signal_key
    ON behavior_events (schema_id, signal_key)
    WHERE signal_key IS NOT NULL;
