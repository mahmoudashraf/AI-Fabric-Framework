CREATE TABLE IF NOT EXISTS behavior_insights (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    patterns JSONB,
    scores JSONB,
    segment VARCHAR(50),
    preferences JSONB,
    recommendations JSONB,
    analyzed_at TIMESTAMP NOT NULL,
    valid_until TIMESTAMP NOT NULL,
    analysis_version VARCHAR(32)
);

CREATE INDEX IF NOT EXISTS idx_behavior_insights_user ON behavior_insights (user_id);
CREATE INDEX IF NOT EXISTS idx_behavior_insights_valid_until ON behavior_insights (valid_until);
