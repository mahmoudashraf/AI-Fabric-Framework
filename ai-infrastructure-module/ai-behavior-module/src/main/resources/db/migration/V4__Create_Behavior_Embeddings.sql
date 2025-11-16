CREATE TABLE IF NOT EXISTS behavior_embeddings (
    id UUID PRIMARY KEY,
    behavior_event_id UUID NOT NULL,
    embedding_type VARCHAR(50) NOT NULL,
    original_text TEXT,
    embedding FLOAT4[],
    model VARCHAR(100),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    detected_category VARCHAR(100),
    sentiment_score DOUBLE PRECISION
);

CREATE INDEX IF NOT EXISTS idx_behavior_embedding_event ON behavior_embeddings (behavior_event_id);
CREATE INDEX IF NOT EXISTS idx_behavior_embedding_type ON behavior_embeddings (embedding_type);
