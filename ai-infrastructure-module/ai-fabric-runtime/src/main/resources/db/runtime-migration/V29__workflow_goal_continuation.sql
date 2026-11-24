ALTER TABLE ai_specialist_execution
    ADD COLUMN IF NOT EXISTS goal_authority_reference VARCHAR(512);
ALTER TABLE ai_specialist_execution
    ADD COLUMN IF NOT EXISTS next_eligible_at TIMESTAMP;
ALTER TABLE ai_specialist_execution
    ADD COLUMN IF NOT EXISTS goal_notification_pending BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_ai_execution_goal_continuation
    ON ai_specialist_execution (status, next_eligible_at);
