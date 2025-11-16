CREATE TABLE IF NOT EXISTS behavior_events (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(64),
    user_id UUID,
    session_id VARCHAR(128),
    event_type VARCHAR(40) NOT NULL,
    entity_type VARCHAR(50),
    entity_id VARCHAR(255),
    source VARCHAR(50),
    channel VARCHAR(50),
    timestamp TIMESTAMP NOT NULL,
    ingested_at TIMESTAMP NOT NULL DEFAULT NOW(),
    metadata JSONB
) PARTITION BY RANGE (timestamp);

CREATE INDEX IF NOT EXISTS idx_behavior_user_time ON behavior_events (user_id, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_behavior_session_time ON behavior_events (session_id, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_behavior_entity ON behavior_events (entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_behavior_event_type ON behavior_events (event_type);

DO $$
DECLARE
    start_month DATE := date_trunc('month', CURRENT_DATE);
    month_offset INTEGER := 0;
BEGIN
    WHILE month_offset < 3 LOOP
        EXECUTE format(
            'CREATE TABLE IF NOT EXISTS behavior_events_%s PARTITION OF behavior_events
             FOR VALUES FROM (%L) TO (%L);',
            to_char(start_month + (month_offset || ' month')::interval, 'YYYY_MM'),
            start_month + (month_offset || ' month')::interval,
            start_month + ((month_offset + 1) || ' month')::interval
        );
        month_offset := month_offset + 1;
    END LOOP;
END $$;
