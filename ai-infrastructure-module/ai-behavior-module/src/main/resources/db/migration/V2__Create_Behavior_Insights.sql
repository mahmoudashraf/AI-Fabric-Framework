CREATE TABLE IF NOT EXISTS behavior_insights (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    patterns JSONB,
    scores JSONB,
    segment VARCHAR(100),
    preferences JSONB,
    recommendations JSONB,
    analyzed_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    valid_until TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    analysis_version VARCHAR(50)
);

CREATE INDEX IF NOT EXISTS idx_behavior_insights_user ON behavior_insights (user_id);
CREATE INDEX IF NOT EXISTS idx_behavior_insights_valid ON behavior_insights (valid_until);
