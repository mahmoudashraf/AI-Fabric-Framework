CREATE TABLE IF NOT EXISTS behavior_embeddings (
    id UUID PRIMARY KEY,
    behavior_event_id UUID NOT NULL,
    embedding_type VARCHAR(40) NOT NULL,
    original_text TEXT,
    embedding JSONB,
    model VARCHAR(100),
    detected_category VARCHAR(100),
    sentiment_score DOUBLE PRECISION,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_behavior_embedding_event ON behavior_embeddings (behavior_event_id);
CREATE INDEX IF NOT EXISTS idx_behavior_embedding_type ON behavior_embeddings (embedding_type);
