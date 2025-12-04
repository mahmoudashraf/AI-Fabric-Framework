CREATE TABLE IF NOT EXISTS behaviors (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    behavior_type VARCHAR(128) NOT NULL,
    entity_type VARCHAR(255),
    entity_id VARCHAR(255),
    action VARCHAR(255),
    context TEXT,
    metadata TEXT,
    session_id VARCHAR(255),
    device_info VARCHAR(255),
    location_info VARCHAR(255),
    duration_seconds BIGINT,
    behavior_value VARCHAR(255),
    search_vector TEXT,
    ai_analysis TEXT,
    ai_insights TEXT,
    behavior_score DOUBLE PRECISION,
    significance_score DOUBLE PRECISION,
    pattern_flags TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_behaviors_user ON behaviors(user_id);
CREATE INDEX IF NOT EXISTS idx_behaviors_type ON behaviors(behavior_type);
