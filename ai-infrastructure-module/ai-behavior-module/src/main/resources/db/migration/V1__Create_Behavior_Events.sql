CREATE TABLE IF NOT EXISTS behavior_events (
    id UUID PRIMARY KEY,
    user_id UUID NULL,
    event_type VARCHAR(50) NOT NULL,
    entity_type VARCHAR(50),
    entity_id VARCHAR(255),
    session_id VARCHAR(255),
    timestamp TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    metadata JSONB,
    ingested_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_behavior_user_time ON behavior_events (user_id, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_behavior_entity ON behavior_events (entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_behavior_type ON behavior_events (event_type);
CREATE INDEX IF NOT EXISTS idx_behavior_session ON behavior_events (session_id);
CREATE INDEX IF NOT EXISTS idx_behavior_timestamp ON behavior_events (timestamp DESC);
