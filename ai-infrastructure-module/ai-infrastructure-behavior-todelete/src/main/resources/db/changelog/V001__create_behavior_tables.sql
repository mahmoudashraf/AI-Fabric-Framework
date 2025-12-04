--liquibase formatted sql

--changeset ai-behavior:pgcrypto
CREATE EXTENSION IF NOT EXISTS pgcrypto;

--changeset ai-behavior:events-temp
CREATE TABLE IF NOT EXISTS ai_behavior_events_temp (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    event_data JSONB NOT NULL,
    source VARCHAR(100),
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    processed BOOLEAN DEFAULT FALSE NOT NULL,
    processing_status VARCHAR(50),
    retry_count INT DEFAULT 0 NOT NULL,
    expires_at TIMESTAMPTZ,
    last_error TEXT
);

CREATE INDEX IF NOT EXISTS idx_behavior_events_temp_user_created
    ON ai_behavior_events_temp (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_behavior_events_temp_processed_expires
    ON ai_behavior_events_temp (processed, expires_at);

CREATE INDEX IF NOT EXISTS idx_behavior_events_temp_expires
    ON ai_behavior_events_temp (expires_at)
    WHERE expires_at IS NOT NULL;

--changeset ai-behavior:events-failed
CREATE TABLE IF NOT EXISTS ai_behavior_events_failed (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    event_data JSONB NOT NULL,
    error_reason TEXT,
    retry_count INT DEFAULT 0 NOT NULL,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    manual_review_required BOOLEAN DEFAULT FALSE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_behavior_events_failed_user_created
    ON ai_behavior_events_failed (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_behavior_events_failed_review
    ON ai_behavior_events_failed (manual_review_required);
