-- Additional helper indexes for frequent access patterns
CREATE INDEX IF NOT EXISTS idx_behavior_events_user_desc ON behavior_events (user_id DESC NULLS LAST, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_behavior_events_entity_desc ON behavior_events (entity_type, entity_id, timestamp DESC);

-- Supporting retention and cleanup operations
CREATE INDEX IF NOT EXISTS idx_behavior_insights_valid_until ON behavior_insights (valid_until);

-- Lightweight materialized view placeholder for hot metrics (drop/create to avoid failures)
DROP MATERIALIZED VIEW IF EXISTS mv_behavior_daily_counts;
CREATE MATERIALIZED VIEW mv_behavior_daily_counts AS
SELECT
    date_trunc('day', timestamp) AS event_day,
    event_type,
    count(*) AS total_events
FROM behavior_events
GROUP BY event_day, event_type;

CREATE INDEX IF NOT EXISTS idx_mv_behavior_daily_counts ON mv_behavior_daily_counts (event_day, event_type);
