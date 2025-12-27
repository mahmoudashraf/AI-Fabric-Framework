-- Example schema for PostgreSQL
-- Users should adapt this to their database and migration tooling.

CREATE TABLE ai_behavior_insights (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    segment VARCHAR(100),
    patterns JSONB,
    recommendations JSONB,
    insights JSONB,
    analyzed_at TIMESTAMP NOT NULL,
    confidence DOUBLE PRECISION,
    ai_model_used VARCHAR(50),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_behavior_insights_user UNIQUE (user_id)
);

CREATE INDEX idx_behavior_insights_user_id ON ai_behavior_insights(user_id);
CREATE INDEX idx_behavior_insights_analyzed_at ON ai_behavior_insights(analyzed_at DESC);
CREATE INDEX idx_behavior_insights_segment ON ai_behavior_insights(segment);
